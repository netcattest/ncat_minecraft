package com.netcattest.ncatminecraft.client.proxy;

import com.google.gson.JsonObject;
import com.netcattest.ncatminecraft.config.ClientConfig;
import org.cef.browser.CefBrowser;
import org.cef.handler.CefResourceHandler;
import org.cef.network.CefRequest;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ProxyService {
    public static final String PAGE = "mod://ncat_minecraft/proxy.html";

    private static final int MAX_HELD = 32;
    private static final Set<String> PRIMARY = Set.of("RT_MAIN_FRAME", "RT_SUB_FRAME", "RT_XHR");

    private static final Map<CefBrowser, ProxySession> SESSIONS = new ConcurrentHashMap<>();
    private static volatile Map<CefBrowser, ProxySession> bySource = Map.of();

    private ProxyService() {
    }

    public static CefResourceHandler handler(CefBrowser source, CefRequest request) {
        if (!ClientConfig.proxyEnabled || request == null) return null;
        ProxySession session = bySource.get(source);
        if (session == null || session.mode == ProxySession.Mode.PASSIVE) return null;
        String url = request.getURL();
        if (!isHttp(url)) return null;
        String type = String.valueOf(request.getResourceType());
        if (!session.matches(url, type)) return null;
        if (session.mode == ProxySession.Mode.INTERCEPT && session.heldCount() >= MAX_HELD) return null;
        return new ProxyExchange(session, source);
    }

    public static void observe(CefBrowser source, long identifier, JsonObject captured) {
        ProxySession session = bySource.get(source);
        if (session == null || captured == null) return;
        ProxyExchange exchange = session.inFlight.remove(identifier);
        JsonObject detail = captured.deepCopy();
        JsonObject entry = new JsonObject();
        entry.addProperty("time", captured.has("time") ? captured.get("time").getAsLong() : System.currentTimeMillis());
        entry.addProperty("method", string(captured, "method"));
        entry.addProperty("url", string(captured, "url"));
        entry.addProperty("status", captured.has("status") ? captured.get("status").getAsInt() : 0);
        entry.addProperty("bytes", captured.has("bytes") ? captured.get("bytes").getAsLong() : 0L);
        entry.addProperty("handled", exchange != null);
        entry.addProperty("edited", exchange != null && exchange.edited());
        entry.addProperty("dropped", exchange != null && exchange.stage() == ProxyExchange.Stage.DROPPED);
        if (exchange != null) {
            JsonObject own = exchange.requestDetail();
            for (String key : own.keySet()) detail.add(key, own.get(key));
            entry.addProperty("method", exchange.method());
            entry.addProperty("url", exchange.url());
            if (exchange.stage() == ProxyExchange.Stage.READY) {
                entry.addProperty("status", exchange.responseStatus());
                entry.addProperty("bytes", exchange.responseBytes());
                detail.addProperty("status", exchange.responseStatus());
                detail.addProperty("bytes", exchange.responseBytes());
            }
        } else {
            detail.addProperty("note", "Observed without proxying. Enable the active mode to capture response bodies.");
        }
        detail.addProperty("handled", entry.get("handled").getAsBoolean());
        detail.addProperty("edited", entry.get("edited").getAsBoolean());
        detail.addProperty("dropped", entry.get("dropped").getAsBoolean());
        session.record(entry, detail);
    }

    public static void sync(Map<CefBrowser, CefBrowser> links, Set<CefBrowser> displays) {
        List<ProxySession> retired = new ArrayList<>();
        SESSIONS.entrySet().removeIf(entry -> {
            if (displays.contains(entry.getKey())) return false;
            retired.add(entry.getValue());
            return true;
        });
        for (ProxySession session : retired)
            for (ProxyExchange exchange : session.drainHeld()) exchange.release();

        Map<CefBrowser, ProxySession> mapped = new HashMap<>();
        for (CefBrowser display : displays) {
            ProxySession session = SESSIONS.computeIfAbsent(display, ignored -> new ProxySession());
            CefBrowser source = links.get(display);
            session.source = source;
            if (source != null) mapped.putIfAbsent(source, session);
        }
        bySource = Map.copyOf(mapped);

        long now = System.currentTimeMillis();
        long deadline = now - Math.max(5, ClientConfig.proxyHoldSeconds) * 1000L;
        for (ProxySession session : SESSIONS.values()) {
            if (session.source == null) {
                for (ProxyExchange exchange : session.drainHeld()) exchange.release();
            } else {
                for (ProxyExchange exchange : session.expired(deadline)) exchange.release();
            }
            session.inFlight.values().removeIf(exchange -> now - exchange.startedAt() > 300000L);
        }
    }

    public static JsonObject poll(CefBrowser display, long after) {
        ProxySession session = SESSIONS.computeIfAbsent(display, ignored -> new ProxySession());
        JsonObject result = new JsonObject();
        CefBrowser source = session.source;
        result.addProperty("enabled", ClientConfig.proxyEnabled);
        result.addProperty("linked", source != null);
        result.addProperty("url", source == null ? "" : trim(source.getURL(), 500));
        result.addProperty("mode", session.mode.id());
        result.addProperty("scope", session.scope.id());
        result.addProperty("filter", session.filter);
        result.addProperty("holdSeconds", Math.max(5, ClientConfig.proxyHoldSeconds));
        result.add("held", session.heldJson());
        result.add("entries", session.since(Math.max(0, after)));
        return result;
    }

    public static boolean setMode(CefBrowser display, String value) {
        if (!ClientConfig.proxyEnabled) return false;
        ProxySession session = SESSIONS.computeIfAbsent(display, ignored -> new ProxySession());
        ProxySession.Mode mode = ProxySession.Mode.parse(value);
        session.mode = mode;
        if (mode != ProxySession.Mode.INTERCEPT)
            for (ProxyExchange exchange : session.drainHeld()) exchange.release();
        return true;
    }

    public static boolean setScope(CefBrowser display, String value) {
        SESSIONS.computeIfAbsent(display, ignored -> new ProxySession()).scope = ProxySession.Scope.parse(value);
        return true;
    }

    public static boolean setFilter(CefBrowser display, String value) {
        String filter = value == null ? "" : value.trim();
        if (filter.length() > 200) filter = filter.substring(0, 200);
        SESSIONS.computeIfAbsent(display, ignored -> new ProxySession()).filter = filter;
        return true;
    }

    public static boolean forward(CefBrowser display, long id, JsonObject edit) {
        ProxySession session = SESSIONS.get(display);
        if (session == null) return false;
        ProxyExchange exchange = session.takeHeld(id);
        if (exchange == null) return false;
        exchange.applyEdit(edit);
        exchange.release();
        return true;
    }

    public static boolean drop(CefBrowser display, long id) {
        ProxySession session = SESSIONS.get(display);
        if (session == null) return false;
        ProxyExchange exchange = session.takeHeld(id);
        if (exchange == null) return false;
        exchange.drop();
        return true;
    }

    public static int releaseAll(CefBrowser display, boolean dropped) {
        ProxySession session = SESSIONS.get(display);
        if (session == null) return 0;
        List<ProxyExchange> all = session.drainHeld();
        for (ProxyExchange exchange : all) {
            if (dropped) exchange.drop();
            else exchange.release();
        }
        return all.size();
    }

    public static JsonObject detail(CefBrowser display, long id) {
        ProxySession session = SESSIONS.get(display);
        return session == null ? null : session.detail(id);
    }

    public static boolean clearHistory(CefBrowser display) {
        ProxySession session = SESSIONS.get(display);
        if (session != null) session.clearHistory();
        return true;
    }

    public static void clear() {
        for (ProxySession session : SESSIONS.values())
            for (ProxyExchange exchange : session.drainHeld()) exchange.drop();
        SESSIONS.clear();
        bySource = Map.of();
    }

    static boolean isPrimaryResource(String resourceType) {
        return PRIMARY.contains(resourceType);
    }

    static boolean isHttp(String address) {
        if (address == null || address.length() < 8) return false;
        try {
            URI uri = URI.create(address);
            String scheme = uri.getScheme();
            return uri.getHost() != null && scheme != null &&
                    (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"));
        } catch (RuntimeException error) {
            return false;
        }
    }

    static int maxBodyBytes() {
        return Math.max(16, Math.min(8192, ClientConfig.proxyMaxBodyKiB)) * 1024;
    }

    static String trim(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit) + "…";
    }

    static JsonObject headersJson(Map<String, String> values) {
        JsonObject result = new JsonObject();
        int count = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (++count > 80) break;
            result.addProperty(trim(entry.getKey(), 128), trim(entry.getValue(), 2048));
        }
        return result;
    }

    private static String string(JsonObject source, String key) {
        return source.has(key) && source.get(key).isJsonPrimitive() ? source.get(key).getAsString() : "";
    }
}
