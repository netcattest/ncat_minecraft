package com.netcattest.ncatminecraft.client.ssh;

import com.google.gson.JsonObject;
import com.netcattest.ncatminecraft.utilities.browser.handlers.js.JSQueryHandler;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;

public final class LocaleQuery extends JSQueryHandler {
    public LocaleQuery() {
        super("Locale");
    }

    @Override
    public boolean handle(CefBrowser browser, CefFrame frame, JsonObject data, boolean persistent, CefQueryCallback callback) {
        if (frame == null || !frame.isMain() || !frame.getURL().startsWith("mod://ncat_minecraft/")) {
            callback.failure(403, "denied");
            return true;
        }
        Minecraft.getInstance().execute(() -> {
            String selected = Minecraft.getInstance().getLanguageManager().getSelected();
            callback.success(selected.startsWith("pt_") ? "pt_br" : "en_us");
        });
        return true;
    }
}
