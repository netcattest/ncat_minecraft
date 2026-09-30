package com.netcattest.ncatminecraft.client.remote;

import com.google.gson.JsonObject;
import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.client.rack.RackClientApps;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.browser.WDBrowser;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class RemoteClientSessions {
    private static final String PAGE = "mod://ncat_minecraft/remote.html";
    private static final int TILE = 256;
    private static final Map<CefBrowser, Connection> CONNECTIONS = new ConcurrentHashMap<>();
    private static final Map<String, String> ACCEPTED_CERTIFICATES = new ConcurrentHashMap<>();

    private RemoteClientSessions() {
    }

    public static boolean accepts(CefBrowser browser, CefFrame frame) {
        if (frame == null || !frame.isMain() || !PAGE.equals(frame.getURL()) || !PAGE.equals(browser.getURL()))
            return false;
        if (RackClientApps.accepts(browser, RackModuleType.REMOTE))
            return true;
        if (
                !(browser instanceof WDBrowser view) || view.getBe() == null || view.getSide() == null ||
                !BlockRegistry.isRemoteScreen(view.getBe().getBlockState().getBlock()) || view.getBe().getLevel() == null)
            return false;
        ScreenData screen = view.getBe().getScreen(view.getSide());
        return screen != null && screen.browser == browser && Minecraft.getInstance().player != null &&
                (screen.rightsFor(Minecraft.getInstance().player) & ScreenRights.INTERACT) != 0;
    }

    public static String protocolLabel(CefBrowser browser) {
        Connection connection = CONNECTIONS.get(browser);
        return connection == null ? "REM" : connection.protocol.toUpperCase();
    }

    public static synchronized JsonObject connect(CefBrowser browser, JsonObject data) {
        String protocol = required(data, "protocol", 3).toLowerCase();
        String host = required(data, "host", 253).trim();
        int port = number(data, "port", 1, 65535);
        if (!(protocol.equals("rdp") || protocol.equals("vnc")) || host.isEmpty() ||
                host.chars().anyMatch(ch -> Character.isISOControl(ch) || Character.isWhitespace(ch)) ||
                host.contains("/") || host.contains("\\"))
            throw new IllegalArgumentException("invalidConnection");
        disconnect(browser);
        Connection connection = new Connection(protocol, browser, host, port);
        CONNECTIONS.put(browser, connection);
        JsonObject response = new JsonObject();
        try {
            if (protocol.equals("vnc")) {
                response.addProperty("url", VncTunnelServer.open(browser, host, port));
            } else {
                String user = required(data, "user", 128).trim();
                String domain = optional(data, "domain", 128).trim();
                String password = optional(data, "password", 512);
                int width = number(data, "width", 320, 1920);
                int height = number(data, "height", 240, 1080);
                if (user.isEmpty()) throw new IllegalArgumentException("invalidConnection");
                char[] secret = password.toCharArray();
                try {
                    connection.resizeBuffer(width, height);
                    connection.rdp = RdpNative.Session.open(host, port, domain, user, secret, width, height, connection);
                } finally {
                    Arrays.fill(secret, '\0');
                }
            }
            response.addProperty("status", "connecting");
            return response;
        } catch (RuntimeException | LinkageError error) {
            if (CONNECTIONS.remove(browser, connection)) connection.close();
            throw error;
        }
    }

    public static JsonObject poll(CefBrowser browser) {
        Connection connection = CONNECTIONS.get(browser);
        if (connection == null || !connection.protocol.equals("rdp")) return message("error", "notConnected");
        JsonObject event = connection.events.poll();
        if (event != null) return event;
        return connection.nextTile();
    }

    public static boolean mouse(CefBrowser browser, int x, int y, int buttons) {
        Connection connection = rdp(browser);
        RdpNative.Session session = connection == null ? null : connection.rdp;
        if (session == null || x < 0 || y < 0 || buttons < 0 || buttons > 7) return false;
        session.mouse(x, y, buttons);
        return true;
    }

    public static boolean wheel(CefBrowser browser, int delta) {
        Connection connection = rdp(browser);
        RdpNative.Session session = connection == null ? null : connection.rdp;
        if (session == null || delta < -1 || delta > 1) return false;
        session.wheel(delta);
        return true;
    }

    public static boolean key(CefBrowser browser, String code, String key, boolean down, boolean control, boolean alt, boolean meta) {
        Connection connection = rdp(browser);
        RdpNative.Session session = connection == null ? null : connection.rdp;
        if (session == null || code == null || code.length() > 40 || key == null || key.length() > 40) return false;
        if (key.codePointCount(0, key.length()) == 1 && !control && !alt && !meta) {
            if (down) session.text(key.codePointAt(0));
            return true;
        }
        int scanCode = RemoteScanCodes.fromCode(code);
        if (scanCode == 0) return false;
        session.key(scanCode, down);
        return true;
    }

    public static boolean resize(CefBrowser browser, int width, int height) {
        Connection connection = rdp(browser);
        RdpNative.Session session = connection == null ? null : connection.rdp;
        if (session == null || width < 320 || width > 1920 || height < 240 || height > 1080) return false;
        session.resize(width, height);
        return true;
    }

    public static boolean trust(CefBrowser browser, boolean accepted) {
        Connection connection = rdp(browser);
        if (connection == null || connection.pendingTrust == null) return false;
        return connection.pendingTrust.complete(accepted);
    }

    public static void vncStatus(CefBrowser browser, String state) {
        Connection connection = CONNECTIONS.get(browser);
        if (connection != null && connection.protocol.equals("vnc") && "disconnected".equals(state)) disconnect(browser);
    }

    public static synchronized void disconnect(CefBrowser browser) {
        Connection connection = CONNECTIONS.remove(browser);
        if (connection != null) connection.close();
        VncTunnelServer.close(browser);
    }

    public static void tick() {
        for (Map.Entry<CefBrowser, Connection> entry : CONNECTIONS.entrySet()) {
            CefBrowser browser = entry.getKey();
            if (RackClientApps.accepts(browser, RackModuleType.REMOTE))
                continue;
            if (!(browser instanceof WDBrowser view) || view.getBe() == null || view.getSide() == null ||
                    view.getBe().getLevel() == null ||
                    !BlockRegistry.isRemoteScreen(view.getBe().getBlockState().getBlock()) ||
                    view.getBe().getScreen(view.getSide()) == null ||
                    view.getBe().getScreen(view.getSide()).browser != browser || !PAGE.equals(browser.getURL()) ||
                    Minecraft.getInstance().player == null ||
                    (view.getBe().getScreen(view.getSide()).rightsFor(Minecraft.getInstance().player) & ScreenRights.INTERACT) == 0)
                disconnect(browser);
        }
    }

    public static synchronized void closeAll() {
        for (CefBrowser browser : CONNECTIONS.keySet()) disconnect(browser);
        VncTunnelServer.closeAll();
        ACCEPTED_CERTIFICATES.clear();
    }

    private static Connection rdp(CefBrowser browser) {
        Connection connection = CONNECTIONS.get(browser);
        return connection == null || !connection.protocol.equals("rdp") || connection.rdp == null ? null : connection;
    }

    private static String required(JsonObject data, String key, int maxLength) {
        if (data == null || !data.has(key)) throw new IllegalArgumentException("invalidData");
        String value = data.get(key).getAsString();
        if (value.length() > maxLength) throw new IllegalArgumentException("invalidData");
        return value;
    }

    private static String optional(JsonObject data, String key, int maxLength) {
        return data == null || !data.has(key) ? "" : required(data, key, maxLength);
    }

    private static int number(JsonObject data, String key, int min, int max) {
        if (data == null || !data.has(key)) throw new IllegalArgumentException("invalidData");
        int value = data.get(key).getAsInt();
        if (value < min || value > max) throw new IllegalArgumentException("invalidData");
        return value;
    }

    private static JsonObject message(String type, String value) {
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        object.addProperty("message", value);
        return object;
    }

    private static final class Connection implements RdpNative.Listener {
        private final String protocol;
        private final CefBrowser browser;
        private final String serverKey;
        private final Object frameLock = new Object();
        private final ArrayBlockingQueue<JsonObject> events = new ArrayBlockingQueue<>(32);
        private volatile RdpNative.Session rdp;
        private volatile CompletableFuture<Boolean> pendingTrust;
        private volatile boolean closed;
        private BufferedImage frame;
        private boolean[][] dirty;

        private Connection(String protocol, CefBrowser browser, String host, int port) {
            this.protocol = protocol;
            this.browser = browser;
            this.serverKey = host.toLowerCase(Locale.ROOT) + ':' + port;
        }

        private void resizeBuffer(int width, int height) {
            synchronized (frameLock) {
                frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                dirty = new boolean[(height + TILE - 1) / TILE][(width + TILE - 1) / TILE];
            }
        }

        @Override
        public void onFrame(int x, int y, int width, int height, int[] argb) {
            if (closed || argb == null || width <= 0 || height <= 0 || argb.length != width * height) return;
            synchronized (frameLock) {
                if (frame == null || x < 0 || y < 0 || x + width > frame.getWidth() || y + height > frame.getHeight()) return;
                frame.setRGB(x, y, width, height, argb, 0, width);
                for (int row = y / TILE; row <= (y + height - 1) / TILE; row++)
                    for (int column = x / TILE; column <= (x + width - 1) / TILE; column++) dirty[row][column] = true;
            }
        }

        @Override
        public void onResize(int width, int height) {
            if (!closed && width >= 320 && width <= 1920 && height >= 240 && height <= 1080) resizeBuffer(width, height);
        }

        @Override
        public void onStatus(String code) {
            if (closed) return;
            if ("connected".equals(code) || "disconnected".equals(code)) offer(message(code, code));
            else offer(message("error", code == null ? "connectFailed" :
                    code.startsWith("error:") ? code.substring(6) : code));
        }

        @Override
        public boolean onCertificate(String fingerprint, boolean changed) {
            String known = ACCEPTED_CERTIFICATES.get(serverKey);
            if (closed || changed || fingerprint == null || fingerprint.isBlank() ||
                    known != null && !known.equals(fingerprint)) {
                offer(message("error", "certificateChanged"));
                return false;
            }
            if (known != null) return true;
            CompletableFuture<Boolean> decision = new CompletableFuture<>();
            pendingTrust = decision;
            JsonObject event = message("certificate", fingerprint == null ? "?" : fingerprint);
            event.addProperty("fingerprint", fingerprint == null ? "?" : fingerprint);
            offer(event);
            try {
                boolean accepted = decision.get(60, TimeUnit.SECONDS);
                return accepted && (ACCEPTED_CERTIFICATES.putIfAbsent(serverKey, fingerprint) == null ||
                        fingerprint.equals(ACCEPTED_CERTIFICATES.get(serverKey)));
            } catch (Exception ignored) {
                return false;
            } finally {
                pendingTrust = null;
            }
        }

        private void offer(JsonObject event) {
            if (!events.offer(event)) {
                events.poll();
                events.offer(event);
            }
        }

        private JsonObject nextTile() {
            BufferedImage tile = null;
            int tileX = 0;
            int tileY = 0;
            int fullWidth = 0;
            int fullHeight = 0;
            synchronized (frameLock) {
                if (frame != null) {
                    fullWidth = frame.getWidth();
                    fullHeight = frame.getHeight();
                    for (int row = 0; row < dirty.length && tile == null; row++) {
                        for (int column = 0; column < dirty[row].length; column++) {
                            if (!dirty[row][column]) continue;
                            dirty[row][column] = false;
                            tileX = column * TILE;
                            tileY = row * TILE;
                            int width = Math.min(TILE, fullWidth - tileX);
                            int height = Math.min(TILE, fullHeight - tileY);
                            tile = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                            tile.setRGB(0, 0, width, height, frame.getRGB(tileX, tileY, width, height, null, 0, width), 0, width);
                            break;
                        }
                    }
                }
            }
            if (tile == null) return message("idle", "");
            try {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(tile, "png", output);
                JsonObject result = message("frame", "");
                result.addProperty("x", tileX);
                result.addProperty("y", tileY);
                result.addProperty("width", fullWidth);
                result.addProperty("height", fullHeight);
                result.addProperty("png", Base64.getEncoder().encodeToString(output.toByteArray()));
                return result;
            } catch (Exception failure) {
                return message("error", "frameFailed");
            }
        }

        private void close() {
            closed = true;
            CompletableFuture<Boolean> decision = pendingTrust;
            if (decision != null) decision.complete(false);
            RdpNative.Session session = rdp;
            rdp = null;
            if (session != null) session.close();
            events.clear();
        }
    }
}
