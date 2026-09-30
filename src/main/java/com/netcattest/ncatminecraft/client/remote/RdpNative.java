package com.netcattest.ncatminecraft.client.remote;

import com.netcattest.ncatminecraft.client.Localization;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class RdpNative {
    private static final String REQUIRED_VERSION = "3.32.1";
    private static volatile boolean loadAttempted;
    private static volatile String loadError;

    private RdpNative() {
    }

    public interface Listener {
        void onFrame(int x, int y, int width, int height, int[] argb);

        void onResize(int width, int height);

        void onStatus(String code);

        boolean onCertificate(String fingerprint, boolean changed);
    }

    public static boolean isAvailable() {
        return load() == null;
    }

    public static String unavailableReason() {
        return load();
    }

    private static synchronized String load() {
        if (loadAttempted) {
            return loadError;
        }
        loadAttempted = true;
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!arch.equals("amd64") && !arch.equals("x86_64")) {
            loadError = "RDP requer arquitetura x64";
            return loadError;
        }
        String platform;
        String filename;
        if (os.contains("win")) {
            platform = "windows-x64";
            filename = "ncatrdp.dll";
        } else if (os.contains("linux")) {
            platform = "linux-x64";
            filename = "libncatrdp.so";
        } else {
            loadError = Localization.t("RDP disponível apenas em Windows e Linux x64",
                    "RDP is available only on Windows and Linux x64");
            return loadError;
        }

        String bundle = "/assets/ncat_minecraft/native/rdp/" + platform + ".zip";
        try (InputStream stream = RdpNative.class.getResourceAsStream(bundle)) {
            if (stream == null) {
                throw new IOException(Localization.t("pacote nativo RDP " + platform + " ausente",
                        "RDP native bundle " + platform + " is missing"));
            }
            Path directory = Files.createTempDirectory("ncat-rdp-").toAbsolutePath();
            directory.toFile().deleteOnExit();
            try (ZipInputStream zip = new ZipInputStream(stream)) {
                for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                    if (entry.isDirectory() || !entry.getName().matches("[A-Za-z0-9_.+-]+")) {
                        throw new IOException(Localization.t("Entrada inválida no pacote RDP",
                                "Invalid entry in the RDP bundle"));
                    }
                    Path target = directory.resolve(entry.getName());
                    try (OutputStream output = new FileOutputStream(target.toFile())) {
                        zip.transferTo(output);
                    }
                    target.toFile().deleteOnExit();
                    zip.closeEntry();
                }
            }
            Path target = directory.resolve(filename);
            if (!Files.isRegularFile(target)) {
                throw new IOException(Localization.t("Biblioteca RDP ausente no pacote",
                        "The RDP library is missing from the bundle"));
            }
            if (os.contains("win")) {
                Path loader = directory.resolve("ncatrdploader.dll");
                if (!Files.isRegularFile(loader)) {
                    throw new IOException(Localization.t("Carregador de bibliotecas RDP ausente",
                            "The RDP library loader is missing"));
                }
                System.load(loader.toString());
                if (!nativePrepareDirectory(directory.toString())) {
                    throw new IOException(Localization.t("Não foi possível preparar bibliotecas RDP",
                            "Could not prepare the RDP libraries"));
                }
            }
            System.load(target.toString());
            if (!nativeConfigureCrypto(directory.toString())) {
                throw new IOException(Localization.t("Provedor criptográfico RDP indisponível",
                        "The RDP cryptographic provider is unavailable"));
            }
            String version = nativeVersion();
            if (!REQUIRED_VERSION.equals(version)) {
                loadError = Localization.t("FreeRDP " + REQUIRED_VERSION + " necessário; encontrado " + version,
                        "FreeRDP " + REQUIRED_VERSION + " is required; found " + version);
            }
        } catch (IOException | LinkageError | SecurityException e) {
            loadError = Localization.t("Biblioteca RDP indisponível: ", "The RDP library is unavailable: ")
                    + e.getMessage();
        }
        return loadError;
    }

    public static final class Session implements AutoCloseable {
        private final Listener listener;
        private final Thread worker;
        private long handle;
        private boolean closed;

        private Session(String host, int port, String domain, String user, char[] password,
                        int width, int height, Listener listener) {
            this.listener = listener;
            this.handle = nativeCreate();
            if (handle == 0) {
                Arrays.fill(password, '\0');
                throw new IllegalStateException("Não foi possível criar a sessão RDP nativa");
            }
            long nativeHandle = this.handle;
            this.worker = new Thread(() -> {
                try {
                    nativeRun(nativeHandle, host, port, domain, user, password, width, height);
                } catch (Throwable error) {
                    status("error:" + error.getMessage());
                } finally {
                    Arrays.fill(password, '\0');
                    synchronized (Session.this) {
                        if (handle != 0) {
                            nativeDestroy(nativeHandle);
                            handle = 0;
                        }
                        closed = true;
                    }
                }
            }, "ncat-rdp-" + host);
            worker.setDaemon(true);
            worker.start();
        }

        public static Session open(String host, int port, String domain, String user,
                                   char[] password, int width, int height, Listener listener) {
            Objects.requireNonNull(host, "host");
            Objects.requireNonNull(user, "user");
            Objects.requireNonNull(password, "password");
            Objects.requireNonNull(listener, "listener");
            if (host.isBlank() || port < 1 || port > 65535 || width < 200 || width > 8192
                    || height < 200 || height > 8192) {
                throw new IllegalArgumentException(Localization.t("Host, porta ou dimensões RDP inválidos",
                        "Invalid RDP host, port or size"));
            }
            String error = load();
            if (error != null) {
                throw new IllegalStateException(error);
            }
            return new Session(host, port, domain == null ? "" : domain, user,
                    password.clone(), width, height, listener);
        }

        public synchronized void mouse(int x, int y, int buttons) {
            enqueue(1, x, y, buttons);
        }

        public synchronized void wheel(int delta) {
            enqueue(2, delta, 0, 0);
        }

        public synchronized void key(int scanCode, boolean down) {
            if (scanCode < 1 || scanCode > 0x1ff) {
                throw new IllegalArgumentException(Localization.t("Scan code RDP inválido",
                        "Invalid RDP scan code"));
            }
            enqueue(3, scanCode, down ? 1 : 0, 0);
        }

        public synchronized void text(int codePoint) {
            if (!Character.isValidCodePoint(codePoint)
                    || (codePoint >= 0xd800 && codePoint <= 0xdfff)) {
                throw new IllegalArgumentException(Localization.t("Unicode inválido", "Invalid Unicode"));
            }
            enqueue(4, codePoint, 0, 0);
        }

        public synchronized void resize(int width, int height) {
            if (width < 200 || width > 8192 || height < 200 || height > 8192) {
                throw new IllegalArgumentException(Localization.t("Dimensões RDP inválidas",
                        "Invalid RDP size"));
            }
            enqueue(5, width, height, 0);
        }

        private void enqueue(int kind, int a, int b, int c) {
            if (closed || handle == 0) {
                return;
            }
            if (!nativeQueue(handle, kind, a, b, c)) {
                throw new IllegalStateException("Fila de entrada RDP cheia");
            }
        }

        @Override
        public synchronized void close() {
            if (closed || handle == 0) {
                return;
            }
            closed = true;
            nativeStop(handle);
        }

        private void frame(int x, int y, int width, int height, int[] argb) {
            try {
                listener.onFrame(x, y, width, height, argb);
            } catch (RuntimeException ignored) {
            }
        }

        private void resized(int width, int height) {
            try {
                listener.onResize(width, height);
            } catch (RuntimeException ignored) {
            }
        }

        private void status(String code) {
            try {
                listener.onStatus(code);
            } catch (RuntimeException ignored) {
            }
        }

        private boolean certificate(String fingerprint, boolean changed) {
            try {
                return listener.onCertificate(fingerprint, changed);
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        private native long nativeCreate();

        private native void nativeRun(long handle, String host, int port, String domain,
                                      String user, char[] password, int width, int height);

        private native boolean nativeQueue(long handle, int kind, int a, int b, int c);

        private native void nativeStop(long handle);

        private native void nativeDestroy(long handle);
    }

    private static native String nativeVersion();

    private static native boolean nativePrepareDirectory(String path);

    private static native boolean nativeConfigureCrypto(String path);
}
