package com.netcattest.ncatminecraft.client.log;

import com.google.gson.JsonObject;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.net.server_bound.C2SMessageLogTablet;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.client.rack.RackClientApps;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import com.netcattest.ncatminecraft.utilities.browser.WDBrowser;
import com.netcattest.ncatminecraft.utilities.browser.handlers.js.JSQueryHandler;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;

public final class LogInspectQuery extends JSQueryHandler {
    public LogInspectQuery() { super("LogInspect"); }

    @Override
    public boolean handle(CefBrowser browser, CefFrame frame, JsonObject data, boolean persistent, CefQueryCallback callback) {
        boolean physical = browser instanceof WDBrowser view && view.getBe() != null && view.getSide() != null &&
                BlockRegistry.isLogScreen(view.getBe().getBlockState().getBlock()) &&
                view.getBe().getScreen(view.getSide()) != null && view.getBe().getScreen(view.getSide()).browser == browser;
        RackClientApps.Ref rack = RackClientApps.ref(browser);
        if ((!physical && (rack == null || !RackClientApps.accepts(browser, RackModuleType.LOG))) ||
                frame == null || !frame.isMain() || !"mod://ncat_minecraft/log.html".equals(frame.getURL()) ||
                data == null || !data.has("id")) {
            callback.failure(403, "denied");
            return true;
        }
        try {
            JsonObject detail = NetworkLogService.detail(browser, data.get("id").getAsLong());
            if (detail == null || detail.toString().length() > 131072) {
                callback.failure(404, "unavailable");
                return true;
            }
            Minecraft.getInstance().execute(() -> WDNetworkRegistry.INSTANCE.sendToServer(
                    physical ? new C2SMessageLogTablet(((WDBrowser) browser).getBe().getBlockPos(), detail.toString()) :
                            new C2SMessageLogTablet(rack.rack().getBlockPos(), rack.module().id(), detail.toString())));
            callback.success("ok");
        } catch (RuntimeException error) {
            callback.failure(400, "invalidData");
        }
        return true;
    }
}
