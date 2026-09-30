package com.netcattest.ncatminecraft.client.devtools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.netcattest.ncatminecraft.client.log.NetworkLogService;
import org.cef.browser.CefBrowser;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DevToolsService {
    private static final int CONSOLE_LIMIT = 250;
    private static final Map<CefBrowser, State> STATES = new ConcurrentHashMap<>();
    private static final Map<String, Pending> PENDING = new ConcurrentHashMap<>();

    private DevToolsService() {
    }

    public static void recordConsole(CefBrowser browser, String level, String message, String source, int line) {
        if (NetworkLogService.sourceId(browser) == 0)
            return;
        String severity = level == null ? "info" : level.toLowerCase(Locale.ROOT);
        if (severity.contains("error")) severity = "error";
        else if (severity.contains("warning")) severity = "warning";
        else severity = "info";
        state(browser).add(severity, message, source, line);
    }

    public static JsonObject snapshot(CefBrowser display, long consoleAfter, long networkAfter, long domRevision) {
        JsonObject result = new JsonObject();
        CefBrowser source = NetworkLogService.linkedSource(display);
        long sourceId = NetworkLogService.sourceId(source);
        result.addProperty("linked", sourceId != 0);
        result.addProperty("source", sourceId);
        if (sourceId == 0) {
            result.add("console", new JsonArray());
            result.add("network", new JsonArray());
            return result;
        }
        State state = state(source);
        requestDom(source, state, false);
        result.addProperty("url", trim(source.getURL(), 500));
        result.addProperty("revision", state.revision);
        if (state.revision != domRevision)
            result.addProperty("html", state.html);
        result.add("console", state.since(Math.max(0, consoleAfter)));
        result.add("network", NetworkLogService.snapshotJson(display, Math.max(0, networkAfter)).getAsJsonArray("entries"));
        return result;
    }

    public static boolean refresh(CefBrowser display) {
        CefBrowser source = NetworkLogService.linkedSource(display);
        if (NetworkLogService.sourceId(source) == 0)
            return false;
        requestDom(source, state(source), true);
        return true;
    }

    public static boolean evaluate(CefBrowser display, String expression) {
        CefBrowser source = NetworkLogService.linkedSource(display);
        if (NetworkLogService.sourceId(source) == 0 || expression == null || expression.isBlank() || expression.length() > 4096)
            return false;
        State state = state(source);
        state.add("input", "> " + expression, "", 0);
        String token = UUID.randomUUID().toString();
        PENDING.put(token, new Pending(source, "eval", System.currentTimeMillis() + 10000));
        String code = new JsonPrimitive(expression).toString();
        String script = "(function(){const token='" + token + "';" +
                "function done(value,failed){window.cefQuery({request:'NcatMinecraft_DevResult'+JSON.stringify({token:token,kind:'eval',value:String(value).slice(0,4000),failed:failed}),onSuccess:function(){},onFailure:function(){}});}" +
                "try{Promise.resolve((0,eval)(" + code + ")).then(function(value){done(value,false)},function(error){done(error,true)});}catch(error){done(error,true)}})();";
        source.executeJavaScript(script, source.getURL(), 0);
        return true;
    }

    public static boolean complete(CefBrowser browser, JsonObject data) {
        if (data == null || !data.has("token") || !data.has("kind") || !data.has("value"))
            return false;
        String token = data.get("token").getAsString();
        Pending pending = PENDING.get(token);
        if (pending == null || pending.browser != browser || !pending.kind.equals(data.get("kind").getAsString()))
            return false;
        PENDING.remove(token, pending);
        String value = data.get("value").getAsString();
        State state = state(browser);
        if ("dom".equals(pending.kind))
            state.updateHtml(trim(value, 60000));
        else
            state.add(data.has("failed") && data.get("failed").getAsBoolean() ? "error" : "result", trim(value, 4000), "", 0);
        return true;
    }

    public static void prune(Set<CefBrowser> active) {
        STATES.keySet().removeIf(browser -> !active.contains(browser));
        long now = System.currentTimeMillis();
        PENDING.entrySet().removeIf(entry -> {
            Pending pending = entry.getValue();
            if (!active.contains(pending.browser) || pending.expires < now) {
                if (active.contains(pending.browser) && "eval".equals(pending.kind))
                    state(pending.browser).add("error", "Timed out waiting for page response", "", 0);
                return true;
            }
            return false;
        });
    }

    public static void clear() {
        STATES.clear();
        PENDING.clear();
    }

    private static State state(CefBrowser browser) {
        return STATES.computeIfAbsent(browser, ignored -> new State());
    }

    private static void requestDom(CefBrowser source, State state, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - state.lastDomRequest < 3000)
            return;
        state.lastDomRequest = now;
        String token = UUID.randomUUID().toString();
        PENDING.put(token, new Pending(source, "dom", now + 10000));
        String script = "(function(){try{const html=document.documentElement?document.documentElement.outerHTML.slice(0,60000):'';" +
                "window.cefQuery({request:'NcatMinecraft_DevResult'+JSON.stringify({token:'" + token + "',kind:'dom',value:html}),onSuccess:function(){},onFailure:function(){}});}" +
                "catch(error){}})();";
        source.executeJavaScript(script, source.getURL(), 0);
    }

    private static String trim(String value, int limit) {
        if (value == null)
            return "";
        return value.length() > limit ? value.substring(0, limit) + "…" : value;
    }

    private record Pending(CefBrowser browser, String kind, long expires) {
    }

    private static final class State {
        private final ArrayDeque<JsonObject> console = new ArrayDeque<>();
        private long sequence;
        private volatile String html = "";
        private volatile long revision;
        private volatile long lastDomRequest;

        private synchronized void add(String level, String message, String source, int line) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", ++sequence);
            entry.addProperty("time", System.currentTimeMillis());
            entry.addProperty("level", trim(level, 30));
            entry.addProperty("message", trim(message, 4000));
            entry.addProperty("source", trim(source, 200));
            entry.addProperty("line", Math.max(0, line));
            console.addLast(entry);
            while (console.size() > CONSOLE_LIMIT)
                console.removeFirst();
        }

        private synchronized JsonArray since(long after) {
            JsonArray entries = new JsonArray();
            for (JsonObject entry : console) {
                if (entry.get("id").getAsLong() > after)
                    entries.add(entry);
                if (entries.size() >= 100)
                    break;
            }
            return entries;
        }

        private void updateHtml(String value) {
            if (!value.equals(html)) {
                html = value;
                revision++;
            }
        }
    }
}
