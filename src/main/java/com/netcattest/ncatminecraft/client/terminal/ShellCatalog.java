package com.netcattest.ncatminecraft.client.terminal;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ShellCatalog {
    public record Shell(String id, String label, String kind, List<String> command) {
    }

    private static final AtomicReference<List<Shell>> CACHE = new AtomicReference<>();
    private static final List<Runnable> WAITING = new ArrayList<>();
    private static boolean scanning;

    private ShellCatalog() {
    }

    public static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public static List<Shell> cached() {
        List<Shell> shells = CACHE.get();
        return shells == null ? List.of() : shells;
    }

    public static boolean ready() {
        return CACHE.get() != null;
    }

    public static void invalidate() {
        CACHE.set(null);
    }

    public static void scan(Runnable done) {
        synchronized (WAITING) {
            if (CACHE.get() != null) {
                if (done != null)
                    done.run();
                return;
            }
            if (done != null)
                WAITING.add(done);
            if (scanning)
                return;
            scanning = true;
        }
        Thread worker = new Thread(() -> {
            List<Shell> found;
            try {
                found = detect();
            } catch (RuntimeException error) {
                found = List.of();
            }
            CACHE.set(found);
            List<Runnable> pending;
            synchronized (WAITING) {
                scanning = false;
                pending = List.copyOf(WAITING);
                WAITING.clear();
            }
            for (Runnable callback : pending)
                callback.run();
        }, "NCAT-Terminal-Scan");
        worker.setDaemon(true);
        worker.start();
    }

    public static Shell byId(String id) {
        for (Shell shell : cached())
            if (shell.id().equals(id))
                return shell;
        return null;
    }

    public static JsonArray toJson() {
        JsonArray array = new JsonArray();
        for (Shell shell : cached()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", shell.id());
            entry.addProperty("label", shell.label());
            entry.addProperty("kind", shell.kind());
            array.add(entry);
        }
        return array;
    }

    private static List<Shell> detect() {
        List<Shell> shells = new ArrayList<>();
        if (windows()) {
            String comspec = System.getenv("ComSpec");
            Path cmd = comspec == null || comspec.isBlank()
                    ? Paths.get("C:\\Windows\\System32\\cmd.exe") : Paths.get(comspec);
            if (Files.isRegularFile(cmd))
                shells.add(new Shell("cmd", "Prompt de Comando", "cmd",
                        List.of(cmd.toString(), "/Q", "/K", "prompt $P$G")));
            Path powershell = Paths.get("C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe");
            if (Files.isRegularFile(powershell))
                shells.add(new Shell("powershell", "Windows PowerShell", "powershell",
                        List.of(powershell.toString(), "-NoLogo", "-NoProfile", "-NoExit", "-Command", "-")));
            Path pwsh = onPath("pwsh.exe");
            if (pwsh != null)
                shells.add(new Shell("pwsh", "PowerShell 7", "powershell",
                        List.of(pwsh.toString(), "-NoLogo", "-NoProfile", "-NoExit", "-Command", "-")));
            for (String distro : wslDistros())
                shells.add(new Shell("wsl:" + distro, "WSL · " + distro, "wsl",
                        List.of("wsl.exe", "-d", distro, "--", "bash", "-i")));
        } else {
            String preferred = System.getenv("SHELL");
            if (preferred != null && Files.isExecutable(Paths.get(preferred)))
                shells.add(new Shell("login", preferred, "unix", List.of(preferred, "-i")));
            for (String candidate : new String[] {"/bin/bash", "/usr/bin/zsh", "/bin/zsh", "/bin/sh"}) {
                Path path = Paths.get(candidate);
                if (!Files.isExecutable(path))
                    continue;
                String id = path.getFileName().toString();
                if (shells.stream().anyMatch(shell -> shell.label().equals(candidate)))
                    continue;
                shells.add(new Shell(id, candidate, "unix", List.of(candidate, "-i")));
            }
        }
        return List.copyOf(shells);
    }

    private static Path onPath(String executable) {
        String path = System.getenv("PATH");
        if (path == null)
            return null;
        for (String entry : path.split(File.pathSeparator)) {
            if (entry.isBlank())
                continue;
            try {
                Path candidate = Paths.get(entry, executable);
                if (Files.isRegularFile(candidate))
                    return candidate;
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<String> wslDistros() {
        List<String> distros = new ArrayList<>();
        try {
            ProcessBuilder builder = new ProcessBuilder("wsl.exe", "--list", "--quiet");
            builder.redirectErrorStream(false);
            Process process = builder.start();
            byte[] raw;
            try (InputStream stream = process.getInputStream()) {
                raw = stream.readAllBytes();
            }
            if (!process.waitFor(6, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return distros;
            }
            String text = new String(raw, StandardCharsets.UTF_16LE);
            if (text.indexOf('\0') >= 0)
                text = new String(raw, StandardCharsets.UTF_8);
            for (String line : text.split("\\R")) {
                String name = line.replace("\uFEFF", "").replace("\0", "").trim();
                if (name.isEmpty() || name.length() > 64 || !name.matches("[A-Za-z0-9._\\-]+"))
                    continue;
                if (!distros.contains(name))
                    distros.add(name);
            }
        } catch (Exception ignored) {
            return distros;
        }
        return distros;
    }
}
