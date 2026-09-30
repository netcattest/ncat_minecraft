package com.netcattest.ncatminecraft.client.devtools;

import com.google.gson.JsonObject;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.client.rack.RackClientApps;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import com.netcattest.ncatminecraft.utilities.browser.WDBrowser;
import com.netcattest.ncatminecraft.utilities.browser.handlers.js.JSQueryHandler;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;

public final class DevResultQuery extends JSQueryHandler {
    public DevResultQuery() {
        super("DevResult");
    }

    @Override
    public boolean handle(CefBrowser browser, CefFrame frame, JsonObject data, boolean persistent, CefQueryCallback callback) {
        boolean physical = browser instanceof WDBrowser view && view.getBe() != null &&
                BlockRegistry.isBrowserScreen(view.getBe().getBlockState().getBlock());
        if ((!physical && !RackClientApps.accepts(browser, RackModuleType.BROWSER)) ||
                frame == null || !frame.isMain() || !DevToolsService.complete(browser, data)) {
            callback.failure(403, "denied");
            return true;
        }
        callback.success("ok");
        return true;
    }
}
