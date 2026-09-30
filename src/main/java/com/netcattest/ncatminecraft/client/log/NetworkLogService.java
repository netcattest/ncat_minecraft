package com.netcattest.ncatminecraft.client.log;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.netcattest.ncatminecraft.client.ClientProxy;
import com.netcattest.ncatminecraft.client.devtools.DevToolsService;
import com.netcattest.ncatminecraft.client.proxy.ProxyService;
import com.netcattest.ncatminecraft.client.rack.RackClientApps;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.net.server_bound.C2SMessageDataActivity;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.browser.WDBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefRequestHandlerAdapter;
import org.cef.handler.CefResourceRequestHandler;
import org.cef.handler.CefResourceRequestHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.network.CefRequest;
import org.cef.network.CefPostData;
import org.cef.network.CefPostDataElement;
import org.cef.network.CefResponse;
import org.cef.network.CefURLRequest;

import java.net.URI;
import java.util.ArrayDeque;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class NetworkLogService {
    private static final int HISTORY_LIMIT = 300;
    private static final int BODY_LIMIT = 32768;
    private static final AtomicLong NEXT_SOURCE = new AtomicLong();
    private static final AtomicLong NEXT_BROWSER = new AtomicLong();
    private static final Map<CefBrowser, Source> SOURCES = new ConcurrentHashMap<>();
    private static final Map<Long, RequestDetails> PENDING = new ConcurrentHashMap<>();
    private static final Map<CefBrowser, Long> BROWSER_IDS = new ConcurrentHashMap<>();
    private static final Map<ScreenData, Source> GROUPS = new ConcurrentHashMap<>();
    private static final Map<Object, Source> RACK_GROUPS = new ConcurrentHashMap<>();
    private static final Map<CefBrowser, CefBrowser> LINKS = new ConcurrentHashMap<>();
    public static final CefRequestHandlerAdapter REQUEST_HANDLER = new CefRequestHandlerAdapter() {
        @Override
        public CefResourceRequestHandler getResourceRequestHandler(CefBrowser browser, CefFrame frame, CefRequest request,
                boolean navigation, boolean download, String initiator, BoolRef disableDefaultHandling) {
            return SOURCES.containsKey(browser) ? RESOURCE_HANDLER : null;
        }
    };
    private static final CefResourceRequestHandler RESOURCE_HANDLER = new CefResourceRequestHandlerAdapter() {
        @Override
        public org.cef.handler.CefResourceHandler getResourceHandler(CefBrowser browser, CefFrame frame, CefRequest request) {
            return SOURCES.containsKey(browser) ? ProxyService.handler(browser, request) : null;
        }

        @Override
        public boolean onBeforeResourceLoad(CefBrowser browser, CefFrame frame, CefRequest request) {
            if (SOURCES.containsKey(browser) && request != null && destination(request.getURL()) != null && request.getIdentifier() > 0)
                PENDING.put(request.getIdentifier(), RequestDetails.capture(request));
            return false;
        }

        @Override
        public void onResourceLoadComplete(CefBrowser browser, CefFrame frame, CefRequest request,
                CefResponse response, CefURLRequest.Status status, long bytesReceived) {
            RequestDetails captured = request == null ? null : PENDING.remove(request.getIdentifier());
            Source source = SOURCES.get(browser);
            if (source == null || request == null)
                return;
            String destination = destination(request.getURL());
            if (destination == null)
                return;
            String method = request.getMethod();
            if (method == null || !method.matches("[A-Z]{1,16}"))
                method = "?";
            int code = response == null ? 0 : response.getStatus();
            JsonObject detail = source.add(method, destination, request, captured, response, status, code, Math.max(0, bytesReceived));
            ProxyService.observe(browser, request.getIdentifier(), detail);
            long now = System.currentTimeMillis();
            long previous = source.lastActivityMillis.get();
            if (now - previous >= 150 && source.lastActivityMillis.compareAndSet(previous, now))
                Minecraft.getInstance().execute(() -> pulseLinked(browser));
        }
    };

    private NetworkLogService() {
    }

    public static void trackSource(CefBrowser browser, ScreenData data) {
        if (browser == null || data == null) return;
        SOURCES.putIfAbsent(browser, GROUPS.computeIfAbsent(data, ignored -> new Source()));
        BROWSER_IDS.computeIfAbsent(browser, ignored -> NEXT_BROWSER.incrementAndGet());
    }

    public static void trackRackSource(CefBrowser browser, Object group) {
        if (browser == null || group == null) return;
        SOURCES.putIfAbsent(browser, RACK_GROUPS.computeIfAbsent(group, ignored -> new Source()));
        BROWSER_IDS.computeIfAbsent(browser, ignored -> NEXT_BROWSER.incrementAndGet());
    }

    public static void tick(ClientProxy proxy) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null)
            return;
        Set<CefBrowser> activeSources = new HashSet<>();
        Set<CefBrowser> activeLogs = new HashSet<>();
        Set<CefBrowser> proxyDisplays = new HashSet<>();
        for (ScreenBlockEntity screen : proxy.getScreens()) {
            if (screen.getLevel() != minecraft.level)
                continue;
            if (BlockRegistry.isBrowserScreen(screen.getBlockState().getBlock())) {
                for (int i = 0; i < screen.screenCount(); i++) {
                    ScreenData data = screen.getScreen(i);
                    for (CefBrowser browser : data.browsers()) {
                        activeSources.add(browser);
                        trackSource(browser, data);
                    }
                }
            }
        }
        for (CefBrowser browser : RackClientApps.activeBrowsers(RackModuleType.BROWSER)) {
            activeSources.add(browser);
            trackRackSource(browser, RackClientApps.groupKey(browser));
        }
        for (ScreenBlockEntity screen : proxy.getScreens()) {
            boolean proxyScreen = BlockRegistry.isProxyScreen(screen.getBlockState().getBlock());
            if (screen.getLevel() != minecraft.level ||
                    !(BlockRegistry.isLogScreen(screen.getBlockState().getBlock()) ||
                            BlockRegistry.isDevToolsScreen(screen.getBlockState().getBlock()) || proxyScreen))
                continue;
            for (int i = 0; i < screen.screenCount(); i++) {
                ScreenData data = screen.getScreen(i);
                if (data.browser == null)
                    continue;
                activeLogs.add(data.browser);
                if (proxyScreen) proxyDisplays.add(data.browser);
                CefBrowser sourceBrowser = null;
                if (data.logSourcePos != null && data.logSourceSide != null) {
                    BlockEntity linked = minecraft.level.getBlockEntity(data.logSourcePos.toBlock());
                    if (linked instanceof ScreenBlockEntity source &&
                            BlockRegistry.isBrowserScreen(source.getBlockState().getBlock())) {
                        ScreenData linkedScreen = source.getScreen(data.logSourceSide);
                        if (linkedScreen != null && activeSources.contains(linkedScreen.browser))
                            sourceBrowser = linkedScreen.browser;
                    }
                }
                if (sourceBrowser == null) {
                    for (DataCableService.RackModuleEndpoint target : DataCableService.connectedRackModules(
                            minecraft.level, screen, data.side)) {
                        if (target.module().type() != RackModuleType.BROWSER || !target.module().powered()) continue;
                        sourceBrowser = RackClientApps.browser(target, data.resolution.x, data.resolution.y);
                        if (sourceBrowser != null) break;
                    }
                }
                if (sourceBrowser == null)
                    LINKS.remove(data.browser);
                else
                    LINKS.put(data.browser, sourceBrowser);
            }
        }
        for (CefBrowser display : RackClientApps.activeBrowsers(RackModuleType.LOG)) {
            activeLogs.add(display);
            CefBrowser source = RackClientApps.linkedBrowser(display, RackModuleType.BROWSER);
            if (source == null) LINKS.remove(display);
            else LINKS.put(display, source);
        }
        for (CefBrowser display : RackClientApps.activeBrowsers(RackModuleType.DEVTOOLS)) {
            activeLogs.add(display);
            CefBrowser source = RackClientApps.linkedBrowser(display, RackModuleType.BROWSER);
            if (source == null) LINKS.remove(display);
            else LINKS.put(display, source);
        }
        for (CefBrowser display : RackClientApps.activeBrowsers(RackModuleType.PROXY)) {
            activeLogs.add(display);
            proxyDisplays.add(display);
            CefBrowser source = RackClientApps.linkedBrowser(display, RackModuleType.BROWSER);
            if (source == null) LINKS.remove(display);
            else LINKS.put(display, source);
        }
        LINKS.keySet().removeIf(browser -> !activeLogs.contains(browser));
        ProxyService.sync(LINKS, proxyDisplays);
        SOURCES.keySet().removeIf(browser -> !activeSources.contains(browser));
        BROWSER_IDS.keySet().removeIf(browser -> !activeSources.contains(browser));
        GROUPS.keySet().removeIf(data -> data.browsers().stream().noneMatch(activeSources::contains));
        RACK_GROUPS.keySet().removeIf(key -> activeSources.stream().noneMatch(browser -> key.equals(RackClientApps.groupKey(browser))));
        PENDING.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().capturedAt > 60000);
        DevToolsService.prune(activeSources);
    }

    public static void clear() {
        ProxyService.clear();
        LINKS.clear();
        SOURCES.clear();
        BROWSER_IDS.clear();
        GROUPS.clear();
        RACK_GROUPS.clear();
        PENDING.clear();
        DevToolsService.clear();
    }

    public static String snapshot(CefBrowser logBrowser, long after) {
        return snapshotJson(logBrowser, after).toString();
    }

    public static CefBrowser linkedSource(CefBrowser displayBrowser) {
        return LINKS.get(displayBrowser);
    }

    public static void pulseDisplay(CefBrowser displayBrowser) {
        Minecraft.getInstance().execute(() -> {
            CefBrowser sourceBrowser = LINKS.get(displayBrowser);
            RackClientApps.sendFlow(displayBrowser, sourceBrowser);
            if (!(sourceBrowser instanceof WDBrowser source) || !(displayBrowser instanceof WDBrowser display) ||
                    source.getBe() == null || source.getSide() == null ||
                    display.getBe() == null || display.getSide() == null) return;
            C2SMessageDataActivity.send(display.getBe(), display.getSide(), source.getBe(), source.getSide());
        });
    }

    private static void pulseLinked(CefBrowser sourceBrowser) {
        for (Map.Entry<CefBrowser, CefBrowser> link : LINKS.entrySet()) {
            if (link.getValue() != sourceBrowser) continue;
            RackClientApps.sendFlow(link.getKey(), sourceBrowser);
            if (!(sourceBrowser instanceof WDBrowser source) || source.getBe() == null || source.getSide() == null ||
                    !(link.getKey() instanceof WDBrowser display) ||
                    display.getBe() == null || display.getSide() == null) continue;
            C2SMessageDataActivity.send(source.getBe(), source.getSide(), display.getBe(), display.getSide());
        }
    }

    public static long sourceId(CefBrowser browser) {
        if (browser == null)
            return 0;
        return BROWSER_IDS.getOrDefault(browser, 0L);
    }

    public static JsonObject snapshotJson(CefBrowser logBrowser, long after) {
        JsonObject result = new JsonObject();
        CefBrowser sourceBrowser = LINKS.get(logBrowser);
        Source source = sourceBrowser == null ? null : SOURCES.get(sourceBrowser);
        result.addProperty("linked", source != null);
        if (source == null) {
            result.addProperty("source", 0);
            result.add("entries", new JsonArray());
        } else {
            result.addProperty("source", source.id);
            result.add("entries", source.since(Math.max(0, after)));
        }
        return result;
    }

    public static JsonObject detail(CefBrowser logBrowser, long id) {
        CefBrowser sourceBrowser = LINKS.get(logBrowser);
        Source source = sourceBrowser == null ? null : SOURCES.get(sourceBrowser);
        return source == null ? null : source.detail(id);
    }

    private static String destination(String address) {
        try {
            URI uri = URI.create(address);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || uri.getHost() == null)
                return null;
            String path = uri.getRawPath();
            String destination = scheme.toLowerCase() + "://" + uri.getHost() +
                    (uri.getPort() < 0 ? "" : ":" + uri.getPort()) + (path == null || path.isEmpty() ? "/" : path);
            return destination.length() > 400 ? destination.substring(0, 400) + "…" : destination;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static final class Source {
        private final long id = NEXT_SOURCE.incrementAndGet();
        private final AtomicLong lastActivityMillis = new AtomicLong();
        private final ArrayDeque<JsonObject> entries = new ArrayDeque<>();
        private final Map<Long, JsonObject> details = new HashMap<>();
        private long sequence;

        private synchronized JsonObject add(String method, String url, CefRequest request, RequestDetails captured, CefResponse response,
                                      CefURLRequest.Status loadStatus, int status, long bytes) {
            JsonObject entry = new JsonObject();
            long id = ++sequence;
            entry.addProperty("id", id);
            entry.addProperty("time", System.currentTimeMillis());
            entry.addProperty("method", method);
            entry.addProperty("url", url);
            entry.addProperty("status", status);
            entry.addProperty("bytes", bytes);
            entries.addLast(entry);
            JsonObject detail = entry.deepCopy();
            RequestDetails requestData = captured == null ? RequestDetails.capture(request) : captured;
            detail.addProperty("fullUrl", requestData.fullUrl);
            detail.addProperty("protocol", requestData.protocol);
            detail.addProperty("resourceType", requestData.resourceType);
            detail.addProperty("referrer", requestData.referrer);
            detail.add("requestHeaders", requestData.requestHeaders);
            detail.add("responseHeaders", headers(response));
            detail.addProperty("requestBody", requestData.requestBody);
            detail.addProperty("statusText", response == null ? "" : trim(response.getStatusText(), 200));
            detail.addProperty("loadStatus", String.valueOf(loadStatus));
            detail.addProperty("mimeType", response == null ? "" : trim(response.getMimeType(), 200));
            detail.addProperty("responseBody", "Response body is not available through the MCEF request callback.");
            details.put(id, detail);
            while (entries.size() > HISTORY_LIMIT) {
                long oldest = entries.removeFirst().get("id").getAsLong();
                details.remove(oldest);
            }
            return detail;
        }

        private synchronized JsonObject detail(long id) {
            JsonObject result = details.get(id);
            return result == null ? null : result.deepCopy();
        }

        private synchronized JsonArray since(long after) {
            JsonArray result = new JsonArray();
            for (JsonObject entry : entries) {
                if (entry.get("id").getAsLong() > after)
                    result.add(entry);
                if (result.size() >= 100)
                    break;
            }
            return result;
        }
    }

    private static JsonObject headers(CefRequest request) {
        Map<String, String> values = new LinkedHashMap<>();
        try { request.getHeaderMap(values); } catch (RuntimeException ignored) { }
        return headers(values);
    }

    private static JsonObject headers(CefResponse response) {
        Map<String, String> values = new LinkedHashMap<>();
        if (response != null) try { response.getHeaderMap(values); } catch (RuntimeException ignored) { }
        return headers(values);
    }

    private static JsonObject headers(Map<String, String> values) {
        JsonObject result = new JsonObject();
        int budget = 16384;
        int count = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String key = trim(entry.getKey(), 128);
            String value = trim(entry.getValue(), 1024);
            if (++count > 80 || key.length() + value.length() > budget) {
                result.addProperty("_capture_note", "Additional headers were truncated at 16 KiB.");
                break;
            }
            result.addProperty(key, value);
            budget -= key.length() + value.length();
        }
        return result;
    }

    private static String body(CefRequest request) {
        try {
            CefPostData post = request.getPostData();
            if (post == null) return "";
            String contentType = request.getHeaderByName("Content-Type");
            String type = contentType == null ? "" : contentType.toLowerCase(java.util.Locale.ROOT);
            boolean text = type.isEmpty() || type.contains("text") || type.contains("json") ||
                    type.contains("xml") || type.contains("javascript") || type.contains("form-urlencoded");
            Vector<CefPostDataElement> elements = new Vector<>();
            post.getElements(elements);
            StringBuilder result = new StringBuilder();
            for (CefPostDataElement element : elements) {
                if (result.length() >= BODY_LIMIT) break;
                if (element.getType() == CefPostDataElement.Type.PDE_TYPE_FILE) {
                    String file = element.getFile();
                    String name = file == null ? "" : file.replace('\\', '/');
                    name = name.substring(name.lastIndexOf('/') + 1);
                    result.append("[uploaded file: ").append(trim(name, 128)).append("; content unavailable]");
                } else if (element.getType() == CefPostDataElement.Type.PDE_TYPE_BYTES) {
                    int count = Math.min(element.getBytesCount(), BODY_LIMIT - result.length());
                    byte[] bytes = new byte[count];
                    int actual = element.getBytes(count, bytes);
                    if (text) result.append(new String(bytes, 0, actual, StandardCharsets.UTF_8));
                    else result.append("[base64] ").append(Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(bytes, actual)));
                }
            }
            if (result.length() >= BODY_LIMIT) result.append("\n[truncated at 32 KiB]");
            return result.toString();
        } catch (RuntimeException error) {
            return "[request body unavailable]";
        }
    }

    private static String trim(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit) + "…";
    }

    private record RequestDetails(long capturedAt, String fullUrl, String protocol, String resourceType,
                                  String referrer, JsonObject requestHeaders, String requestBody) {
        private static RequestDetails capture(CefRequest request) {
            String address = request.getURL();
            return new RequestDetails(System.currentTimeMillis(), trim(address, 2048),
                    address != null && address.startsWith("https:") ? "HTTPS" : "HTTP",
                    String.valueOf(request.getResourceType()), trim(request.getReferrerURL(), 1024),
                    headers(request), body(request));
        }
    }
}
