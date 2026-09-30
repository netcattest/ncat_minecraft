package com.netcattest.ncatminecraft.client.devtools;

import com.google.gson.JsonObject;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.client.rack.RackClientApps;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import com.netcattest.ncatminecraft.client.log.NetworkLogService;
import com.netcattest.ncatminecraft.utilities.browser.WDBrowser;
import com.netcattest.ncatminecraft.utilities.browser.handlers.js.JSQueryHandler;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;

public final class DevToolsQuery extends JSQueryHandler {
    private static final String PAGE = "mod://ncat_minecraft/devtools.html";

    public DevToolsQuery() {
        super("DevTools");
    }

    @Override
    public boolean handle(CefBrowser browser, CefFrame frame, JsonObject data, boolean persistent, CefQueryCallback callback) {
        boolean physical = browser instanceof WDBrowser view && view.getBe() != null && view.getSide() != null &&
                BlockRegistry.isDevToolsScreen(view.getBe().getBlockState().getBlock()) &&
                view.getBe().getScreen(view.getSide()) != null && view.getBe().getScreen(view.getSide()).browser == browser;
        if ((!physical && !RackClientApps.accepts(browser, RackModuleType.DEVTOOLS)) ||
                frame == null || !frame.isMain() || !PAGE.equals(frame.getURL()) || !PAGE.equals(browser.getURL())) {
            callback.failure(403, "denied");
            return true;
        }
        String action = data == null || !data.has("action") ? "poll" : data.get("action").getAsString();
        if ("poll".equals(action)) {
            long consoleAfter = number(data, "consoleAfter");
            long networkAfter = number(data, "networkAfter");
            long revision = number(data, "revision");
            callback.success(DevToolsService.snapshot(browser, consoleAfter, networkAfter, revision).toString());
        } else if ("refresh".equals(action)) {
            boolean refreshed = DevToolsService.refresh(browser);
            if (refreshed) NetworkLogService.pulseDisplay(browser);
            callback.success(Boolean.toString(refreshed));
        } else if ("eval".equals(action) && data != null && data.has("expression")) {
            boolean evaluated = DevToolsService.evaluate(browser, data.get("expression").getAsString());
            if (evaluated) NetworkLogService.pulseDisplay(browser);
            callback.success(Boolean.toString(evaluated));
        } else {
            callback.failure(400, "invalidAction");
        }
        return true;
    }

    private static long number(JsonObject data, String key) {
        if (data == null || !data.has(key))
            return 0;
        try {
            return Math.max(0, data.get(key).getAsLong());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
