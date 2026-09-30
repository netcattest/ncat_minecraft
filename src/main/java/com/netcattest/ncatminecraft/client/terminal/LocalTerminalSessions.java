package com.netcattest.ncatminecraft.client.terminal;

import com.google.gson.JsonObject;
import com.netcattest.ncatminecraft.client.rack.RackClientApps;
import com.netcattest.ncatminecraft.config.ClientConfig;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.browser.WDBrowser;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public final class LocalTerminalSessions {
    public static final String PAGE = "mod://ncat_minecraft/terminal.html";
    private static final int MAX_INPUT = 8192;

    private static final Map<CefBrowser, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<CefBrowser, ArrayBlockingQueue<String>> PENDING = new ConcurrentHashMap<>();

    private LocalTerminalSessions() {
    }

    public static boolean accepts(CefBrowser browser, CefFrame frame) {
        if (frame == null || !frame.isMain() || !PAGE.equals(frame.getURL()) || !PAGE.equals(browser.getURL()))
            return false;
        if (RackClientApps.accepts(browser, RackModuleType.TERMINAL))
            return true;
        if (!(browser instanceof WDBrowser view) || view.getBe() == null || view.getSide() == null)
            return false;
        if (!BlockRegistry.isTerminalScreen(view.getBe().getBlockState().getBlock()))
            return false;
        ScreenData screen = view.getBe().getScreen(view.getSide());
        return screen != null && screen.browser == browser;
    }

    public static boolean enabled() {
        return ClientConfig.terminalEnabled;
    }

    public static void requestCatalog(CefBrowser browser, boolean refresh) {
        if (refresh)
            ShellCatalog.invalidate();
        if (ShellCatalog.ready())
            queueCatalog(browser);
        else
            ShellCatalog.scan(() -> queueCatalog(browser));
    }

    private static void queueCatalog(CefBrowser browser) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "catalog");
        message.addProperty("host", hostLabel());
        message.addProperty("enabled", enabled());
        message.add("shells", ShellCatalog.toJson());
        PENDING.computeIfAbsent(browser, ignored -> new ArrayBlockingQueue<>(8)).offer(message.toString());
    }

    private static String hostLabel() {
        String user = System.getProperty("user.name", "user");
        String os = System.getProperty("os.name", "host");
        return user + "@" + os;
    }

    public static String start(CefBrowser browser, String shellId, int columns, int rows) {
        if (!enabled())
            return "terminalDisabled";
        ShellCatalog.Shell shell = ShellCatalog.byId(shellId);
        if (shell == null)
            return "unknownShell";
        stop(browser);
        Session session = new Session(browser, shell, Math.max(20, Math.min(400, columns)),
                Math.max(5, Math.min(200, rows)));
        SESSIONS.put(browser, session);
        session.start();
        return null;
    }

    public static boolean input(CefBrowser browser, String text) {
        Session session = SESSIONS.get(browser);
        return session != null && session.input(text);
    }

    public static void resize(CefBrowser browser, int columns, int rows) {
        Session session = SESSIONS.get(browser);
        if (session != null)
            session.resize(columns, rows);
    }

    public static void stop(CefBrowser browser) {
        Session session = SESSIONS.remove(browser);
        if (session != null)
            session.close();
    }

    public static void tick() {
        for (Map.Entry<CefBrowser, ArrayBlockingQueue<String>> entry : PENDING.entrySet()) {
            ArrayBlockingQueue<String> queue = entry.getValue();
            String message;
            while ((message = queue.poll()) != null)
                entry.getKey().executeJavaScript("window.ncatTerminalEvent(" + message + ");", PAGE, 0);
        }
        for (Map.Entry<CefBrowser, Session> entry : SESSIONS.entrySet()) {
            CefBrowser browser = entry.getKey();
            Session session = entry.getValue();
            if (!RackClientApps.accepts(browser, RackModuleType.TERMINAL) &&
                    (!(browser instanceof WDBrowser view) || view.getBe() == null || view.getSide() == null ||
                            view.getBe().getLevel() == null || view.getBe().getScreen(view.getSide()) == null ||
                            view.getBe().getScreen(view.getSide()).browser != browser)) {
                if (SESSIONS.remove(browser, session))
                    session.close();
                PENDING.remove(browser);
                continue;
            }
            session.flush();
        }
    }

    public static void closeAll() {
        for (CefBrowser browser : new ArrayList<>(SESSIONS.keySet()))
            stop(browser);
        PENDING.clear();
    }

    private static final class Session {
        private final CefBrowser browser;
        private final ShellCatalog.Shell shell;
        private final ArrayBlockingQueue<String> events = new ArrayBlockingQueue<>(512);
        private volatile Process process;
        private volatile OutputStream output;
        private volatile boolean closed;
        private int columns;
        private int rows;

        Session(CefBrowser browser, ShellCatalog.Shell shell, int columns, int rows) {
            this.browser = browser;
            this.shell = shell;
            this.columns = columns;
            this.rows = rows;
        }

        void start() {
            Thread worker = new Thread(this::run, "NCAT-Terminal-" + shell.id());
            worker.setDaemon(true);
            worker.start();
        }

        private void run() {
            try {
                ProcessBuilder builder = new ProcessBuilder(shell.command());
                builder.redirectErrorStream(true);
                builder.directory(workingDirectory().toFile());
                builder.environment().put("TERM", "xterm-256color");
                builder.environment().put("COLUMNS", Integer.toString(columns));
                builder.environment().put("LINES", Integer.toString(rows));
                Process started = builder.start();
                process = started;
                output = started.getOutputStream();
                event("started", shell.label());
                try (InputStream stream = started.getInputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while (!closed && (count = stream.read(buffer)) >= 0)
                        if (count > 0)
                            event("output", new String(buffer, 0, count, StandardCharsets.UTF_8));
                }
                int code = started.waitFor();
                if (!closed)
                    event("exit", Integer.toString(code));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                if (!closed)
                    event("failed", error.getClass().getSimpleName());
            } finally {
                closeProcess();
            }
        }

        private Path workingDirectory() {
            String configured = ClientConfig.terminalDirectory;
            if (configured != null && !configured.isBlank()) {
                try {
                    Path path = Paths.get(configured);
                    if (Files.isDirectory(path))
                        return path;
                } catch (RuntimeException ignored) {
                    return Paths.get(System.getProperty("user.home", "."));
                }
            }
            return Paths.get(System.getProperty("user.home", "."));
        }

        boolean input(String text) {
            if (closed || text == null || text.length() > MAX_INPUT)
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
                return false;
            }
        }

        void resize(int width, int height) {
            columns = Math.max(20, Math.min(400, width));
            rows = Math.max(5, Math.min(200, height));
        }

        void flush() {
            for (int i = 0; i < 24; i++) {
                String message = events.poll();
                if (message == null)
                    break;
                browser.executeJavaScript("window.ncatTerminalEvent(" + message + ");", PAGE, 0);
            }
        }

        private void event(String type, String value) {
            if (closed)
                return;
            JsonObject message = new JsonObject();
            message.addProperty("type", type);
            message.addProperty("value", value);
            if (!events.offer(message.toString())) {
                events.poll();
                events.offer(message.toString());
            }
        }

        void close() {
            closed = true;
            closeProcess();
        }

        private void closeProcess() {
            Process running = process;
            if (running == null)
                return;
            process = null;
            output = null;
            running.destroy();
            try {
                if (!running.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS))
                    running.destroyForcibly();
            } catch (InterruptedException interrupted) {
                running.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }

    public static List<String> shellIds() {
        List<String> ids = new ArrayList<>();
        for (ShellCatalog.Shell shell : ShellCatalog.cached())
            ids.add(shell.id());
        return ids;
    }
}
