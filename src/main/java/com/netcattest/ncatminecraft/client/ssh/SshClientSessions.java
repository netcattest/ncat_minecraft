package com.netcattest.ncatminecraft.client.ssh;

import com.google.gson.JsonObject;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.client.rack.RackClientApps;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.browser.WDBrowser;
import net.minecraftforge.fml.loading.FMLPaths;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class SshClientSessions {
    private static final String PAGE = "mod://ncat_minecraft/ssh.html";
    private static final Map<CefBrowser, Connection> CONNECTIONS = new ConcurrentHashMap<>();

    private SshClientSessions() {
    }

    public static boolean accepts(CefBrowser browser, CefFrame frame) {
        if (frame == null || !frame.isMain() || !PAGE.equals(frame.getURL()) || !PAGE.equals(browser.getURL()))
            return false;
        if (RackClientApps.accepts(browser, RackModuleType.SSH)) return true;
        if (!(browser instanceof WDBrowser view) || view.getBe() == null || view.getSide() == null) return false;
        if (!BlockRegistry.isSshScreen(view.getBe().getBlockState().getBlock()))
            return false;
        ScreenData screen = view.getBe().getScreen(view.getSide());
        return screen != null && screen.browser == browser;
    }

    public static String connect(CefBrowser browser, JsonObject data) {
        if (data == null)
            return "invalidData";
        try {
            String host = data.get("host").getAsString().trim();
            String user = data.get("user").getAsString().trim();
            String password = data.get("password").getAsString();
            String portText = data.get("port").getAsString();
            if (!portText.matches("[0-9]{1,5}"))
                return "invalidConnection";
            int port = Integer.parseInt(portText);
            int columns = data.get("cols").getAsInt();
            int rows = data.get("rows").getAsInt();
            if (host.isEmpty() || host.length() > 253 || !host.matches("[A-Za-z0-9._:-]+") ||
                    user.isEmpty() || user.length() > 128 || user.chars().anyMatch(Character::isISOControl) ||
                    password.isEmpty() || password.length() > 512 ||
                    port < 1 || port > 65535 || columns < 20 || columns > 500 || rows < 5 || rows > 200)
                return "invalidConnection";
            Connection connection = new Connection(browser, host, user, port, columns, rows);
            Connection previous = CONNECTIONS.put(browser, connection);
            if (previous != null)
                previous.close();
            connection.start(password.getBytes(StandardCharsets.UTF_8));
            return null;
        } catch (RuntimeException error) {
            return "invalidConnection";
        }
    }

    public static boolean input(CefBrowser browser, String data) {
        Connection connection = CONNECTIONS.get(browser);
        return connection != null && connection.input(data);
    }

    public static void resize(CefBrowser browser, int columns, int rows) {
        Connection connection = CONNECTIONS.get(browser);
        if (connection != null)
            connection.resize(columns, rows);
    }

    public static void trust(CefBrowser browser, boolean accepted) {
        Connection connection = CONNECTIONS.get(browser);
        if (connection != null)
            connection.trust(accepted);
    }

    public static void disconnect(CefBrowser browser) {
        Connection connection = CONNECTIONS.remove(browser);
        if (connection != null)
            connection.close();
    }

    public static Session connectedSession(CefBrowser browser) {
        Connection connection = CONNECTIONS.get(browser);
        if (connection == null || connection.closed) return null;
        Session active = connection.session;
        ChannelShell shell = connection.channel;
        return active != null && active.isConnected() && shell != null && shell.isConnected() ? active : null;
    }

    public static void tick() {
        for (Map.Entry<CefBrowser, Connection> entry : CONNECTIONS.entrySet()) {
            CefBrowser browser = entry.getKey();
            Connection connection = entry.getValue();
            if (!RackClientApps.accepts(browser, RackModuleType.SSH) &&
                    (!(browser instanceof WDBrowser view) || view.getBe() == null || view.getSide() == null ||
                    view.getBe().getLevel() == null || view.getBe().getScreen(view.getSide()) == null ||
                    view.getBe().getScreen(view.getSide()).browser != browser)) {
                if (CONNECTIONS.remove(browser, connection))
                    connection.close();
                continue;
            }
            connection.flush();
        }
    }

    public static void closeAll() {
        for (CefBrowser browser : CONNECTIONS.keySet())
            disconnect(browser);
    }

    private static final class Connection implements UserInfo {
        private final CefBrowser browser;
        private final String host;
        private final String user;
        private final int port;
        private final ArrayBlockingQueue<String> events = new ArrayBlockingQueue<>(256);
        private volatile int columns;
        private volatile int rows;
        private volatile boolean closed;
        private volatile Session session;
        private volatile ChannelShell channel;
        private volatile OutputStream output;
        private volatile CompletableFuture<Boolean> pendingTrust;
        private Thread worker;

        private Connection(CefBrowser browser, String host, String user, int port, int columns, int rows) {
            this.browser = browser;
            this.host = host;
            this.user = user;
            this.port = port;
            this.columns = columns;
            this.rows = rows;
        }

        private void start(byte[] password) {
            worker = new Thread(() -> run(password), "NCAT-SSH-" + host + ":" + port);
            worker.setDaemon(true);
            worker.start();
        }

        private void run(byte[] password) {
            try {
                event("connecting", "connecting");
                Path knownHosts = FMLPaths.GAMEDIR.get().resolve("config/ncat_minecraft/known_hosts");
                Files.createDirectories(knownHosts.getParent());
                if (Files.notExists(knownHosts))
                    Files.createFile(knownHosts);
                JSch ssh = new JSch();
                ssh.setKnownHosts(knownHosts.toString());
                Session active = ssh.getSession(user, host, port);
                session = active;
                active.setUserInfo(this);
                active.setConfig("StrictHostKeyChecking", "ask");
                active.setConfig("FingerprintHash", "sha256");
                active.setConfig("PreferredAuthentications", "password");
                active.setPassword(password);
                Arrays.fill(password, (byte) 0);
                active.connect(10000);
                if (closed)
                    return;
                ChannelShell shell = (ChannelShell) active.openChannel("shell");
                channel = shell;
                shell.setPty(true);
                shell.setPtyType("xterm-256color");
                shell.setPtySize(columns, rows, 0, 0);
                OutputStream shellOutput = shell.getOutputStream();
                shell.connect(10000);
                output = shellOutput;
                event("connected", user + "@" + host + ":" + port);
                try (InputStreamReader input = new InputStreamReader(shell.getInputStream(), StandardCharsets.UTF_8)) {
                    char[] buffer = new char[4096];
                    int count;
                    while (!closed && (count = input.read(buffer)) >= 0) {
                        if (count > 0)
                            event("output", new String(buffer, 0, count));
                    }
                }
                if (!closed)
                    event("status", "connectionClosed");
            } catch (Exception error) {
                if (!closed)
                    event("status", "connectFailed");
            } finally {
                Arrays.fill(password, (byte) 0);
                closeTransport();
            }
        }

        private void event(String type, String value) {
            if (closed)
                return;
            JsonObject message = new JsonObject();
            message.addProperty("type", type);
            message.addProperty("value", value);
            try {
                events.put(message.toString());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private void flush() {
            for (int i = 0; i < 16; i++) {
                String message = events.poll();
                if (message == null)
                    break;
                browser.executeJavaScript("window.ncatSshEvent(" + message + ");", PAGE, 0);
            }
        }

        private boolean input(String text) {
            if (text == null || text.length() > 16384 || closed)
                return false;
            OutputStream target = output;
            if (target == null)
                return false;
            try {
                synchronized (target) {
                    target.write(text.getBytes(StandardCharsets.UTF_8));
                    target.flush();
                }
                return true;
            } catch (Exception error) {
                event("status", "sendFailed");
                return false;
            }
        }

        private void resize(int width, int height) {
            if (width < 20 || width > 500 || height < 5 || height > 200)
                return;
            columns = width;
            rows = height;
            ChannelShell active = channel;
            if (active != null && active.isConnected())
                active.setPtySize(width, height, 0, 0);
        }

        private void trust(boolean accepted) {
            CompletableFuture<Boolean> request = pendingTrust;
            if (request != null)
                request.complete(accepted);
        }

        private void close() {
            closed = true;
            trust(false);
            if (worker != null)
                worker.interrupt();
            closeTransport();
            events.clear();
        }

        private void closeTransport() {
            ChannelShell activeChannel = channel;
            channel = null;
            output = null;
            if (activeChannel != null)
                activeChannel.disconnect();
            Session activeSession = session;
            session = null;
            if (activeSession != null)
                activeSession.disconnect();
        }

        @Override
        public String getPassphrase() {
            return null;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public boolean promptPassword(String message) {
            return false;
        }

        @Override
        public boolean promptPassphrase(String message) {
            return false;
        }

        @Override
        public boolean promptYesNo(String message) {
            if (message.contains("HAS CHANGED")) {
                event("status", "hostKeyChanged");
                return false;
            }
            int start = message.indexOf("fingerprint is ");
            String fingerprint = start < 0 ? "?" : message.substring(start + 15).split("[.\\n]")[0].trim();
            CompletableFuture<Boolean> request = new CompletableFuture<>();
            pendingTrust = request;
            event("hostKey", host + ":" + port + " · " + fingerprint);
            try {
                return request.get(60, TimeUnit.SECONDS);
            } catch (Exception error) {
                return false;
            } finally {
                pendingTrust = null;
            }
        }

        @Override
        public void showMessage(String message) {
        }
    }
}
