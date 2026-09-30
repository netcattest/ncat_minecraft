package com.netcattest.ncatminecraft.client.sftp;

import com.google.gson.JsonObject;
import com.netcattest.ncatminecraft.utilities.browser.handlers.js.JSQueryHandler;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;

public final class SftpQuery extends JSQueryHandler {
    public SftpQuery() { super("Sftp"); }

    @Override
    public boolean handle(CefBrowser browser, CefFrame frame, JsonObject data, boolean persistent, CefQueryCallback callback) {
        Minecraft.getInstance().execute(() -> {
            if (!SftpClientSessions.accepts(browser, frame)) callback.failure(403, "denied");
            else SftpClientSessions.handle(browser, data, callback);
        });
        return true;
    }
}
