package com.netcattest.ncatminecraft.client.log;

import com.google.gson.JsonObject;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.client.rack.RackClientApps;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import com.netcattest.ncatminecraft.utilities.browser.WDBrowser;
import com.netcattest.ncatminecraft.utilities.browser.handlers.js.JSQueryHandler;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;

public final class LogQuery extends JSQueryHandler {
    private static final String PAGE = "mod://ncat_minecraft/log.html";

    public LogQuery() {
        super("LogPoll");
    }

    @Override
    public boolean handle(CefBrowser browser, CefFrame frame, JsonObject data, boolean persistent, CefQueryCallback callback) {
        boolean physical = browser instanceof WDBrowser view && view.getBe() != null && view.getSide() != null &&
                BlockRegistry.isLogScreen(view.getBe().getBlockState().getBlock()) &&
                view.getBe().getScreen(view.getSide()) != null && view.getBe().getScreen(view.getSide()).browser == browser;
        if ((!physical && !RackClientApps.accepts(browser, RackModuleType.LOG)) ||
                frame == null || !frame.isMain() || !PAGE.equals(frame.getURL()) || !PAGE.equals(browser.getURL())) {
            callback.failure(403, "denied");
            return true;
        }
        long after = 0;
        if (data != null && data.has("after")) {
            try {
                after = Math.max(0, data.get("after").getAsLong());
            } catch (RuntimeException ignored) {
                callback.failure(400, "invalidData");
                return true;
            }
        }
        callback.success(NetworkLogService.snapshot(browser, after));
        return true;
    }
}
