package com.netcattest.ncatminecraft.client.workstation;

import com.google.gson.JsonObject;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.browser.WDBrowser;
import com.netcattest.ncatminecraft.utilities.browser.handlers.js.JSQueryHandler;
import net.minecraft.client.Minecraft;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;

public final class WorkstationQuery extends JSQueryHandler {
    private static final String PAGE = "mod://ncat_minecraft/workstation.html";

    public WorkstationQuery() {
        super("Workstation");
    }

    @Override
    public boolean handle(CefBrowser browser, CefFrame frame, JsonObject data, boolean persistent, CefQueryCallback callback) {
        if (!(browser instanceof WDBrowser view) || view.getBe() == null || view.getSide() == null ||
                !BlockRegistry.isWorkstationScreen(view.getBe().getBlockState().getBlock()) || frame == null ||
                !frame.isMain() || !PAGE.equals(frame.getURL()) || !PAGE.equals(browser.getURL())) {
            callback.failure(403, "denied");
            return true;
        }
        ScreenData screen = view.getBe().getScreen(view.getSide());
        if (screen == null || screen.browser != browser) {
            callback.failure(403, "denied");
            return true;
        }
        Minecraft.getInstance().execute(() -> {
            try {
                if (view.getBe().getLevel() == null || view.getBe().getScreen(view.getSide()) == null ||
                        view.getBe().getScreen(view.getSide()).browser != browser ||
                        !WorkstationClientView.canInteract(view.getBe(), view.getSide())) {
                    callback.failure(403, "denied");
                    return;
                }
                String action = data == null || !data.has("action") ? "status" : data.get("action").getAsString();
                if ("status".equals(action)) {
                    callback.success(WorkstationClientView.status(view.getBe(), view.getSide()).toString());
                } else if ("select".equals(action) && data.has("index")) {
                    boolean selected = WorkstationClientView.select(view.getBe(), view.getSide(), data.get("index").getAsInt());
                    if (selected) callback.success("selected");
                    else callback.failure(404, "disconnected");
                } else {
                    callback.failure(404, "unknownAction");
                }
            } catch (RuntimeException invalid) {
                callback.failure(400, "invalidData");
            }
        });
        return true;
    }
}
