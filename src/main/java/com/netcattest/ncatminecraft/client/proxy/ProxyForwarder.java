package com.netcattest.ncatminecraft.client.proxy;

import org.cef.network.CefCookie;
import org.cef.network.CefCookieManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class ProxyForwarder {
    private static final Set<String> BLOCKED_REQUEST_HEADERS = Set.of(
            "connection", "content-length", "expect", "host", "upgrade", "keep-alive",
            "proxy-connection", "proxy-authorization", "te", "trailer", "transfer-encoding",
            "accept-encoding", "if-none-match", "if-modified-since", "if-range");
    private static final Set<String> BLOCKED_RESPONSE_HEADERS = Set.of(
            "connection", "content-encoding", "content-length", "keep-alive",
            "transfer-encoding", "trailer", "upgrade", "set-cookie");
    private static final Set<String> BODYLESS = Set.of("GET", "HEAD", "DELETE", "OPTIONS", "TRACE");

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(6, task -> {
        Thread thread = new Thread(task, "ncat-proxy-" + COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private ProxyForwarder() {
    }

    static void submit(ProxyExchange exchange) {
        try {
            POOL.execute(() -> run(exchange));
        } catch (RejectedExecutionException error) {
            exchange.fail("The proxy worker pool is shutting down.");
        }
    }

    private static void run(ProxyExchange exchange) {
        try {
            HttpRequest request = build(exchange);
            HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            handle(exchange, response);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            exchange.fail("The request was interrupted.");
        } catch (Exception error) {
            String reason = error.getClass().getSimpleName() +
                    (error.getMessage() == null ? "" : ": " + error.getMessage());
            exchange.fail(ProxyService.trim(reason, 500));
        }
    }

    private static HttpRequest build(ProxyExchange exchange) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(exchange.url()))
                .timeout(Duration.ofSeconds(30));
        boolean hasCookie = false;
        for (Map.Entry<String, String> entry : exchange.headers().entrySet()) {
            String name = entry.getKey();
            if (BLOCKED_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) continue;
            if (name.equalsIgnoreCase("cookie")) hasCookie = true;
            try {
                builder.header(name, entry.getValue());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (!hasCookie) {
            String cookies = cookiesFor(exchange.url());
            if (!cookies.isEmpty()) builder.header("Cookie", cookies);
        }
        builder.header("Accept-Encoding", "identity");
        byte[] body = exchange.body();
        boolean sendBody = body.length > 0 || !BODYLESS.contains(exchange.method());
        builder.method(exchange.method(), sendBody
                ? HttpRequest.BodyPublishers.ofByteArray(body)
                : HttpRequest.BodyPublishers.noBody());
        return builder.build();
    }

    private static void handle(ProxyExchange exchange, HttpResponse<byte[]> response) {
        LinkedHashMap<String, String> headers = ProxySession.emptyHeaders();
        List<String> setCookies = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : response.headers().map().entrySet()) {
            String name = entry.getKey();
            if (name == null || name.isEmpty() || name.startsWith(":")) continue;
            if (name.equalsIgnoreCase("set-cookie")) {
                setCookies.addAll(entry.getValue());
                continue;
            }
            if (BLOCKED_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) continue;
            if (!entry.getValue().isEmpty()) headers.put(name, ProxyService.trim(entry.getValue().get(0), 8192));
        }
        for (String cookie : setCookies) store(exchange.url(), cookie);

        byte[] payload = response.body() == null ? new byte[0] : response.body();
        String note = "";
        int limit = ProxyService.maxBodyBytes();
        if (payload.length > limit) {
            payload = Arrays.copyOf(payload, limit);
            note = "Response truncated to " + limit + " bytes by the proxy.";
        }
        headers.put("Content-Length", Integer.toString(payload.length));

        String contentType = headers.getOrDefault("Content-Type", headers.getOrDefault("content-type", ""));
        int semicolon = contentType.indexOf(';');
        String mime = (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim();

        int status = response.statusCode();
        String location = headers.getOrDefault("Location", headers.getOrDefault("location", ""));
        String redirect = status >= 300 && status < 400 && !location.isEmpty()
                ? ProxyExchange.resolve(exchange.url(), location) : "";
        if (!redirect.isEmpty() && !exchange.session().allowRedirect(exchange.url())) {
            redirect = "";
            note = (note.isEmpty() ? "" : note + " ") +
                    "Redirect loop stopped by the proxy after 5 hops to the same address.";
        }

        exchange.deliver(status, reason(status), mime, headers, setCookies, payload, redirect, note);
    }

    private static String cookiesFor(String url) {
        CefCookieManager manager;
        try {
            manager = CefCookieManager.getGlobalManager();
        } catch (RuntimeException | UnsatisfiedLinkError error) {
            return "";
        }
        if (manager == null) return "";
        StringBuilder result = new StringBuilder();
        CountDownLatch done = new CountDownLatch(1);
        boolean started;
        try {
            started = manager.visitUrlCookies(url, true, (cookie, count, total, delete) -> {
                append(result, cookie);
                if (count + 1 >= total) done.countDown();
                return true;
            });
        } catch (RuntimeException error) {
            return "";
        }
        if (!started) return "";
        try {
            done.await(750, TimeUnit.MILLISECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        synchronized (result) {
            return result.toString();
        }
    }

    private static void append(StringBuilder target, CefCookie cookie) {
        if (cookie == null || cookie.name == null) return;
        synchronized (target) {
            if (target.length() > 0) target.append("; ");
            target.append(cookie.name).append('=').append(cookie.value == null ? "" : cookie.value);
        }
    }

    private static void store(String url, String header) {
        CefCookie cookie = parse(url, header);
        if (cookie == null) return;
        try {
            CefCookieManager manager = CefCookieManager.getGlobalManager();
            if (manager != null) manager.setCookie(url, cookie);
        } catch (RuntimeException | UnsatisfiedLinkError ignored) {
        }
    }

    private static CefCookie parse(String url, String header) {
        if (header == null || header.isBlank()) return null;
        String[] parts = header.split(";");
        int split = parts[0].indexOf('=');
        if (split <= 0) return null;
        String name = parts[0].substring(0, split).trim();
        String value = parts[0].substring(split + 1).trim();
        String host;
        try {
            host = URI.create(url).getHost();
        } catch (RuntimeException error) {
            return null;
        }
        if (host == null) return null;
        String domain = "";
        String path = "/";
        boolean secure = false;
        boolean httpOnly = false;
        Date expires = null;
        for (int i = 1; i < parts.length; i++) {
            String attribute = parts[i].trim();
            String lower = attribute.toLowerCase(Locale.ROOT);
            if (lower.startsWith("domain=")) domain = attribute.substring(7).trim();
            else if (lower.startsWith("path=")) path = attribute.substring(5).trim();
            else if (lower.equals("secure")) secure = true;
            else if (lower.equals("httponly")) httpOnly = true;
            else if (lower.startsWith("max-age=")) {
                try {
                    expires = new Date(System.currentTimeMillis() + Long.parseLong(attribute.substring(8).trim()) * 1000L);
                } catch (NumberFormatException ignored) {
                }
            } else if (lower.startsWith("expires=") && expires == null) {
                try {
                    expires = Date.from(java.time.ZonedDateTime.parse(attribute.substring(8).trim(),
                            java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant());
                } catch (RuntimeException ignored) {
                }
            }
        }
        Date now = new Date();
        return new CefCookie(name, value, domain.isEmpty() ? host : domain, path.isEmpty() ? "/" : path,
                secure, httpOnly, now, now, expires != null, expires);
    }

    private static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 303 -> "See Other";
            case 304 -> "Not Modified";
            case 307 -> "Temporary Redirect";
            case 308 -> "Permanent Redirect";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            case 504 -> "Gateway Timeout";
            default -> status >= 200 && status < 300 ? "OK" : "HTTP " + status;
        };
    }
}
