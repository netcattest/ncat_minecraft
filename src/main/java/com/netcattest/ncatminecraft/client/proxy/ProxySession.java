package com.netcattest.ncatminecraft.client.proxy;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.cef.browser.CefBrowser;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class ProxySession {
    enum Mode {
        PASSIVE, ACTIVE, INTERCEPT;

        static Mode parse(String value) {
            if (value == null) return PASSIVE;
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "active" -> ACTIVE;
                case "intercept" -> INTERCEPT;
                default -> PASSIVE;
            };
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    enum Scope {
        MAIN, ALL;

        static Scope parse(String value) {
            return value != null && value.equalsIgnoreCase("all") ? ALL : MAIN;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final int HISTORY_LIMIT = 250;

    volatile Mode mode = Mode.PASSIVE;
    volatile Scope scope = Scope.MAIN;
    volatile String filter = "";
    volatile CefBrowser source;

    final Map<Long, ProxyExchange> held = new LinkedHashMap<>();
    final Map<Long, ProxyExchange> inFlight = new ConcurrentHashMap<>();

    private final ArrayDeque<JsonObject> history = new ArrayDeque<>();
    private final Map<Long, JsonObject> details = new HashMap<>();
    private long sequence;
    private final Map<String, long[]> redirects = new HashMap<>();

    synchronized long record(JsonObject entry, JsonObject detail) {
        long id = ++sequence;
        entry.addProperty("id", id);
        history.addLast(entry);
        if (detail != null) {
            detail.addProperty("id", id);
            details.put(id, detail);
        }
        while (history.size() > HISTORY_LIMIT)
            details.remove(history.removeFirst().get("id").getAsLong());
        return id;
    }

    synchronized JsonArray since(long after) {
        JsonArray result = new JsonArray();
        for (JsonObject entry : history) {
            if (entry.get("id").getAsLong() > after) result.add(entry.deepCopy());
            if (result.size() >= 80) break;
        }
        return result;
    }

    synchronized JsonObject detail(long id) {
        JsonObject found = details.get(id);
        return found == null ? null : found.deepCopy();
    }

    synchronized void clearHistory() {
        history.clear();
        details.clear();
    }

    synchronized ProxyExchange takeHeld(long id) {
        return held.remove(id);
    }

    synchronized void addHeld(ProxyExchange exchange) {
        held.put(exchange.id(), exchange);
    }

    synchronized java.util.List<ProxyExchange> drainHeld() {
        java.util.List<ProxyExchange> all = new java.util.ArrayList<>(held.values());
        held.clear();
        return all;
    }

    synchronized boolean allowRedirect(String url) {
        long now = System.currentTimeMillis();
        if (redirects.size() > 64) redirects.clear();
        long[] entry = redirects.computeIfAbsent(url, ignored -> new long[]{0L, now});
        if (now - entry[1] > 10000L) entry[0] = 0L;
        entry[1] = now;
        return ++entry[0] <= 5L;
    }

    synchronized int heldCount() {
        return held.size();
    }

    synchronized java.util.List<ProxyExchange> expired(long deadline) {
        java.util.List<ProxyExchange> stale = new java.util.ArrayList<>();
        for (ProxyExchange exchange : held.values())
            if (exchange.heldAt() < deadline) stale.add(exchange);
        for (ProxyExchange exchange : stale) held.remove(exchange.id());
        return stale;
    }

    synchronized JsonArray heldJson() {
        JsonArray result = new JsonArray();
        for (ProxyExchange exchange : held.values()) result.add(exchange.describe());
        return result;
    }

    boolean matches(String url, String resourceType) {
        if (scope == Scope.MAIN && !ProxyService.isPrimaryResource(resourceType)) return false;
        String needle = filter;
        return needle.isEmpty() || url.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    static LinkedHashMap<String, String> emptyHeaders() {
        return new LinkedHashMap<>();
    }
}
