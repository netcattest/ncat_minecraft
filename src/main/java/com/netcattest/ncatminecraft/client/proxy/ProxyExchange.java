package com.netcattest.ncatminecraft.client.proxy;

import com.google.gson.JsonObject;
import org.cef.browser.CefBrowser;
import org.cef.callback.CefCallback;
import org.cef.handler.CefResourceHandler;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefPostData;
import org.cef.network.CefPostDataElement;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;

public final class ProxyExchange implements CefResourceHandler {
    public enum Stage { NEW, HELD, FORWARDING, READY, DROPPED, ABORTED }

    private static final AtomicLong NEXT = new AtomicLong();
    private static final java.util.regex.Pattern TOKEN = java.util.regex.Pattern.compile("[A-Za-z0-9!#$%&'*+.^_`|~-]+");

    private final long id = NEXT.incrementAndGet();
    private final ProxySession session;
    private final CefBrowser source;

    private volatile long identifier;
    private volatile CefCallback callback;
    private volatile Stage stage = Stage.NEW;
    private volatile long heldAt;
    private volatile long startedAt;
    private volatile boolean edited;

    private volatile String originalMethod = "GET";
    private volatile String originalUrl = "";
    private volatile String resourceType = "";
    private volatile LinkedHashMap<String, String> originalHeaders = ProxySession.emptyHeaders();
    private volatile byte[] originalBody = new byte[0];

    private volatile String method = "GET";
    private volatile String url = "";
    private volatile LinkedHashMap<String, String> headers = ProxySession.emptyHeaders();
    private volatile byte[] body = new byte[0];

    private volatile int status;
    private volatile String statusText = "";
    private volatile String mime = "application/octet-stream";
    private volatile LinkedHashMap<String, String> responseHeaders = ProxySession.emptyHeaders();
    private volatile List<String> extraSetCookies = List.of();
    private volatile byte[] responseBody = new byte[0];
    private volatile String redirect = "";
    private volatile String note = "";
    private volatile int offset;

    ProxyExchange(ProxySession session, CefBrowser source) {
        this.session = session;
        this.source = source;
    }

    public long id() {
        return id;
    }

    long identifier() {
        return identifier;
    }

    ProxySession session() {
        return session;
    }

    CefBrowser source() {
        return source;
    }

    Stage stage() {
        return stage;
    }

    long heldAt() {
        return heldAt;
    }

    long startedAt() {
        return startedAt;
    }

    boolean edited() {
        return edited;
    }

    String method() {
        return method;
    }

    String url() {
        return url;
    }

    Map<String, String> headers() {
        return headers;
    }

    byte[] body() {
        return body;
    }

    @Override
    public boolean processRequest(CefRequest request, CefCallback handlerCallback) {
        callback = handlerCallback;
        identifier = request.getIdentifier();
        startedAt = System.currentTimeMillis();
        originalMethod = normalizeMethod(request.getMethod());
        originalUrl = ProxyService.trim(request.getURL(), 4096);
        resourceType = String.valueOf(request.getResourceType());
        originalHeaders = readHeaders(request);
        originalBody = readBody(request);
        method = originalMethod;
        url = originalUrl;
        headers = new LinkedHashMap<>(originalHeaders);
        body = originalBody;
        session.inFlight.put(identifier, this);
        if (session.mode == ProxySession.Mode.INTERCEPT) {
            stage = Stage.HELD;
            heldAt = System.currentTimeMillis();
            session.addHeld(this);
        } else {
            stage = Stage.FORWARDING;
            ProxyForwarder.submit(this);
        }
        return true;
    }

    @Override
    public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {
        response.setStatus(status);
        response.setStatusText(statusText);
        response.setMimeType(mime);
        response.setHeaderMap(responseHeaders);
        for (String cookie : extraSetCookies) response.setHeaderByName("Set-Cookie", cookie, false);
        if (!redirect.isEmpty()) redirectUrl.set(redirect);
        responseLength.set(responseBody.length);
    }

    @Override
    public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback readCallback) {
        byte[] payload = responseBody;
        if (offset >= payload.length || bytesToRead <= 0) {
            bytesRead.set(0);
            return false;
        }
        int count = Math.min(bytesToRead, payload.length - offset);
        System.arraycopy(payload, offset, dataOut, 0, count);
        offset += count;
        bytesRead.set(count);
        return true;
    }

    @Override
    public void cancel() {
        if (stage != Stage.READY && stage != Stage.DROPPED) stage = Stage.ABORTED;
        session.takeHeld(id);
        session.inFlight.remove(identifier, this);
    }

    void applyEdit(JsonObject edit) {
        if (edit == null) return;
        if (edit.has("method")) {
            String value = normalizeMethod(edit.get("method").getAsString());
            if (!value.equals(method)) {
                method = value;
                edited = true;
            }
        }
        if (edit.has("url")) {
            String value = edit.get("url").getAsString().trim();
            if (ProxyService.isHttp(value) && value.length() <= 4096 && !value.equals(url)) {
                url = value;
                edited = true;
            }
        }
        if (edit.has("headers")) {
            LinkedHashMap<String, String> parsed = parseHeaders(edit.get("headers").getAsString());
            if (!parsed.equals(headers)) {
                headers = parsed;
                edited = true;
            }
        }
        if (edit.has("body")) {
            byte[] value = edit.get("body").getAsString().getBytes(StandardCharsets.UTF_8);
            if (value.length <= ProxyService.maxBodyBytes() && !Arrays.equals(value, body)) {
                body = value;
                edited = true;
            }
        }
    }

    void release() {
        stage = Stage.FORWARDING;
        session.takeHeld(id);
        ProxyForwarder.submit(this);
    }

    void drop() {
        stage = Stage.DROPPED;
        session.takeHeld(id);
        CefCallback pending = callback;
        if (pending != null) pending.cancel();
    }

    void deliver(int responseStatus, String responseStatusText, String responseMime,
                 LinkedHashMap<String, String> headerMap, List<String> setCookies,
                 byte[] payload, String redirectTarget, String message) {
        status = responseStatus;
        statusText = responseStatusText == null ? "" : ProxyService.trim(responseStatusText, 200);
        mime = responseMime == null || responseMime.isBlank() ? "application/octet-stream" : responseMime;
        responseHeaders = headerMap == null ? ProxySession.emptyHeaders() : headerMap;
        extraSetCookies = setCookies == null ? List.of() : List.copyOf(setCookies);
        responseBody = payload == null ? new byte[0] : payload;
        redirect = redirectTarget == null ? "" : redirectTarget;
        note = message == null ? "" : message;
        stage = Stage.READY;
        CefCallback pending = callback;
        if (pending != null) pending.Continue();
    }

    void fail(String reason) {
        byte[] payload = ("NCAT Proxy could not forward this request.\n\n" + reason)
                .getBytes(StandardCharsets.UTF_8);
        LinkedHashMap<String, String> headerMap = ProxySession.emptyHeaders();
        headerMap.put("Content-Type", "text/plain; charset=utf-8");
        deliver(502, "Proxy Error", "text/plain", headerMap, List.of(), payload, "", reason);
    }

    JsonObject describe() {
        JsonObject result = new JsonObject();
        result.addProperty("id", id);
        result.addProperty("time", heldAt);
        result.addProperty("method", method);
        result.addProperty("url", url);
        result.addProperty("originalMethod", originalMethod);
        result.addProperty("originalUrl", originalUrl);
        result.addProperty("resourceType", shortType());
        result.addProperty("headers", formatHeaders(headers));
        result.addProperty("body", text(body));
        result.addProperty("binary", !isText(body));
        result.addProperty("edited", edited);
        return result;
    }

    JsonObject requestDetail() {
        JsonObject result = new JsonObject();
        result.addProperty("method", method);
        result.addProperty("url", url);
        result.addProperty("originalMethod", originalMethod);
        result.addProperty("originalUrl", originalUrl);
        result.add("requestHeaders", ProxyService.headersJson(headers));
        result.addProperty("requestBody", text(body));
        result.add("responseHeaders", ProxyService.headersJson(responseHeaders));
        result.addProperty("responseBody", text(responseBody));
        result.addProperty("statusText", statusText);
        result.addProperty("mimeType", mime);
        result.addProperty("resourceType", shortType());
        result.addProperty("note", note);
        return result;
    }

    String shortType() {
        String value = resourceType.startsWith("RT_") ? resourceType.substring(3) : resourceType;
        return value.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    int responseStatus() {
        return status;
    }

    int responseBytes() {
        return responseBody.length;
    }

    private static String normalizeMethod(String value) {
        if (value == null) return "GET";
        String trimmed = value.trim().toUpperCase(Locale.ROOT);
        return trimmed.matches("[A-Z]{1,16}") ? trimmed : "GET";
    }

    private static LinkedHashMap<String, String> readHeaders(CefRequest request) {
        LinkedHashMap<String, String> values = ProxySession.emptyHeaders();
        try {
            request.getHeaderMap(values);
        } catch (RuntimeException ignored) {
        }
        return values;
    }

    private static byte[] readBody(CefRequest request) {
        try {
            CefPostData post = request.getPostData();
            if (post == null) return new byte[0];
            Vector<CefPostDataElement> elements = new Vector<>();
            post.getElements(elements);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int limit = ProxyService.maxBodyBytes();
            for (CefPostDataElement element : elements) {
                if (buffer.size() >= limit) break;
                if (element.getType() != CefPostDataElement.Type.PDE_TYPE_BYTES) continue;
                int count = Math.min(element.getBytesCount(), limit - buffer.size());
                byte[] bytes = new byte[count];
                int actual = element.getBytes(count, bytes);
                buffer.write(bytes, 0, Math.max(0, actual));
            }
            return buffer.toByteArray();
        } catch (RuntimeException error) {
            return new byte[0];
        }
    }

    static LinkedHashMap<String, String> parseHeaders(String raw) {
        LinkedHashMap<String, String> result = ProxySession.emptyHeaders();
        if (raw == null) return result;
        for (String line : raw.split("\r?\n")) {
            int split = line.indexOf(':');
            if (split <= 0) continue;
            String name = line.substring(0, split).trim();
            String value = line.substring(split + 1).trim();
            if (name.isEmpty() || name.length() > 128 || value.length() > 8192) continue;
            if (!TOKEN.matcher(name).matches()) continue;
            result.put(name, value);
            if (result.size() >= 80) break;
        }
        return result;
    }

    static String formatHeaders(Map<String, String> values) {
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : values.entrySet())
            result.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        return result.toString();
    }

    static boolean isText(byte[] payload) {
        int checked = Math.min(payload.length, 2048);
        for (int i = 0; i < checked; i++) {
            int value = payload[i] & 0xff;
            if (value == 0) return false;
            if (value < 0x09 || (value > 0x0d && value < 0x20)) return false;
        }
        return true;
    }

    static String text(byte[] payload) {
        if (payload.length == 0) return "";
        int limit = Math.min(payload.length, ProxyService.maxBodyBytes());
        if (!isText(payload))
            return "[base64] " + Base64.getEncoder().encodeToString(Arrays.copyOf(payload, Math.min(limit, 65536)));
        String value = new String(payload, 0, limit, StandardCharsets.UTF_8);
        return limit < payload.length ? value + "\n[truncated]" : value;
    }

    static String resolve(String base, String target) {
        try {
            return URI.create(base).resolve(target).toString();
        } catch (RuntimeException error) {
            return target;
        }
    }
}
