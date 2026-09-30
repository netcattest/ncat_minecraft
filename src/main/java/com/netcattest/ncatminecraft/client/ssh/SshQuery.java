package com.netcattest.ncatminecraft.client.ssh;

import com.google.gson.JsonObject;
import com.netcattest.ncatminecraft.utilities.browser.handlers.js.JSQueryHandler;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;

public final class SshQuery extends JSQueryHandler {
    public SshQuery(String name) {
        super(name);
    }

    @Override
    public boolean handle(CefBrowser browser, CefFrame frame, JsonObject data, boolean persistent, CefQueryCallback callback) {
        if (!SshClientSessions.accepts(browser, frame)) {
            callback.failure(403, "denied");
            return true;
        }
        try {
            switch (name) {
                case "SshConnect" -> {
                    String error = SshClientSessions.connect(browser, data);
                    if (error != null)
                        callback.failure(400, error);
                    else
                        callback.success("connecting");
                }
                case "SshInput" -> {
                    if (data == null || !data.has("text") || !SshClientSessions.input(browser, data.get("text").getAsString()))
                        callback.failure(409, "notConnected");
                    else
                        callback.success("OK");
                }
                case "SshResize" -> {
                    if (data != null && data.has("cols") && data.has("rows"))
                        SshClientSessions.resize(browser, data.get("cols").getAsInt(), data.get("rows").getAsInt());
                    callback.success("OK");
                }
                case "SshTrust" -> {
                    if (data != null && data.has("accepted"))
                        SshClientSessions.trust(browser, data.get("accepted").getAsBoolean());
                    callback.success("OK");
                }
                case "SshDisconnect" -> {
                    SshClientSessions.disconnect(browser);
                    callback.success("disconnected");
                }
                case "SshClipboard" -> {
                    if (data == null || !data.has("mode")) {
                        callback.failure(400, "invalidData");
                        break;
                    }
                    String mode = data.get("mode").getAsString();
                    String value = "write".equals(mode) && data.has("text") ? data.get("text").getAsString() : "";
                    if (!("read".equals(mode) || "write".equals(mode)) || value.length() > 16384) {
                        callback.failure(400, "invalidData");
                        break;
                    }
                    Minecraft.getInstance().execute(() -> {
                        try {
                            if ("read".equals(mode)) {
                                String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
                                if (clipboard.length() > 16384)
                                    callback.failure(413, "clipboardTooLarge");
                                else
                                    callback.success(clipboard);
                            } else {
                                Minecraft.getInstance().keyboardHandler.setClipboard(value);
                                callback.success("copied");
                            }
                        } catch (RuntimeException error) {
                            callback.failure(500, "clipboardFailed");
                        }
                    });
                }
                default -> callback.failure(404, "unknownCommand");
            }
        } catch (RuntimeException error) {
            callback.failure(400, "invalidData");
        }
        return true;
    }
}
