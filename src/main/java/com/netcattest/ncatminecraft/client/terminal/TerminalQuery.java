package com.netcattest.ncatminecraft.client.terminal;

import com.google.gson.JsonObject;
import com.netcattest.ncatminecraft.utilities.browser.handlers.js.JSQueryHandler;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;

public final class TerminalQuery extends JSQueryHandler {
    public static final String[] NAMES = {"TerminalList", "TerminalStart", "TerminalInput",
            "TerminalResize", "TerminalStop", "TerminalClipboard"};

    public TerminalQuery(String name) {
        super(name);
    }

    @Override
    public boolean handle(CefBrowser browser, CefFrame frame, JsonObject data, boolean persistent,
                          CefQueryCallback callback) {
        if (!LocalTerminalSessions.accepts(browser, frame)) {
            callback.failure(403, "denied");
            return true;
        }
        try {
            switch (name) {
                case "TerminalList" -> {
                    boolean refresh = data != null && data.has("refresh")
                            && data.get("refresh").getAsBoolean();
                    LocalTerminalSessions.requestCatalog(browser, refresh);
                    callback.success("OK");
                }
                case "TerminalStart" -> {
                    if (data == null || !data.has("shell")) {
                        callback.failure(400, "invalidData");
                        break;
                    }
                    int columns = data.has("cols") ? data.get("cols").getAsInt() : 80;
                    int rows = data.has("rows") ? data.get("rows").getAsInt() : 24;
                    String error = LocalTerminalSessions.start(browser,
                            data.get("shell").getAsString(), columns, rows);
                    if (error != null)
                        callback.failure(400, error);
                    else
                        callback.success("starting");
                }
                case "TerminalInput" -> {
                    if (data == null || !data.has("text")
                            || !LocalTerminalSessions.input(browser, data.get("text").getAsString()))
                        callback.failure(409, "notRunning");
                    else
                        callback.success("OK");
                }
                case "TerminalResize" -> {
                    if (data != null && data.has("cols") && data.has("rows"))
                        LocalTerminalSessions.resize(browser, data.get("cols").getAsInt(),
                                data.get("rows").getAsInt());
                    callback.success("OK");
                }
                case "TerminalStop" -> {
                    LocalTerminalSessions.stop(browser);
                    callback.success("stopped");
                }
                case "TerminalClipboard" -> {
                    if (data == null || !data.has("mode")) {
                        callback.failure(400, "invalidData");
                        break;
                    }
                    String mode = data.get("mode").getAsString();
                    String value = "write".equals(mode) && data.has("text")
                            ? data.get("text").getAsString() : "";
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
