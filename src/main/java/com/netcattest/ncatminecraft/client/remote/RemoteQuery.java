package com.netcattest.ncatminecraft.client.remote;

import com.google.gson.JsonObject;
import com.netcattest.ncatminecraft.utilities.browser.handlers.js.JSQueryHandler;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;

public final class RemoteQuery extends JSQueryHandler {
    public RemoteQuery() {
        super("Remote");
    }

    @Override
    public boolean handle(CefBrowser browser, CefFrame frame, JsonObject data, boolean persistent, CefQueryCallback callback) {
        if (!RemoteClientSessions.accepts(browser, frame)) {
            callback.failure(403, "denied");
            return true;
        }
        try {
            if (data == null || !data.has("action")) throw new IllegalArgumentException("invalidData");
            String action = data.get("action").getAsString();
            switch (action) {
                case "connect" -> callback.success(RemoteClientSessions.connect(browser, data).toString());
                case "poll" -> callback.success(RemoteClientSessions.poll(browser).toString());
                case "disconnect" -> {
                    RemoteClientSessions.disconnect(browser);
                    callback.success("disconnected");
                }
                case "vncStatus" -> {
                    RemoteClientSessions.vncStatus(browser, data.get("state").getAsString());
                    callback.success("OK");
                }
                case "mouse" -> respond(callback, RemoteClientSessions.mouse(browser,
                        data.get("x").getAsInt(), data.get("y").getAsInt(), data.get("buttons").getAsInt()));
                case "wheel" -> respond(callback, RemoteClientSessions.wheel(browser, data.get("delta").getAsInt()));
                case "key" -> respond(callback, RemoteClientSessions.key(browser,
                        data.get("code").getAsString(), data.get("key").getAsString(), data.get("down").getAsBoolean(),
                        data.has("ctrl") && data.get("ctrl").getAsBoolean(),
                        data.has("alt") && data.get("alt").getAsBoolean(),
                        data.has("meta") && data.get("meta").getAsBoolean()));
                case "resize" -> respond(callback, RemoteClientSessions.resize(browser,
                        data.get("width").getAsInt(), data.get("height").getAsInt()));
                case "trust" -> respond(callback, RemoteClientSessions.trust(browser,
                        data.get("accepted").getAsBoolean()));
                default -> callback.failure(404, "unknownAction");
            }
        } catch (IllegalArgumentException invalid) {
            callback.failure(400, invalid.getMessage() == null ? "invalidData" : invalid.getMessage());
        } catch (IllegalStateException unavailable) {
            callback.failure(500, unavailable.getMessage() == null ? "connectFailed" : unavailable.getMessage());
        } catch (RuntimeException failure) {
            callback.failure(500, "connectFailed");
        }
        return true;
    }

    private static void respond(CefQueryCallback callback, boolean accepted) {
        if (accepted) callback.success("OK");
        else callback.failure(409, "notConnected");
    }
}
