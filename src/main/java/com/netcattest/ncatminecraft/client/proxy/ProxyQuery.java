package com.netcattest.ncatminecraft.client.proxy;

import com.google.gson.JsonObject;
import com.netcattest.ncatminecraft.client.log.NetworkLogService;
import com.netcattest.ncatminecraft.client.rack.RackClientApps;
import com.netcattest.ncatminecraft.config.ClientConfig;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.browser.WDBrowser;
import com.netcattest.ncatminecraft.utilities.browser.handlers.js.JSQueryHandler;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;

public final class ProxyQuery extends JSQueryHandler {
    public ProxyQuery() {
        super("Proxy");
    }

    @Override
    public boolean handle(CefBrowser browser, CefFrame frame, JsonObject data, boolean persistent, CefQueryCallback callback) {
        boolean physical = browser instanceof WDBrowser view && view.getBe() != null && view.getSide() != null &&
                BlockRegistry.isProxyScreen(view.getBe().getBlockState().getBlock()) &&
                view.getBe().getScreen(view.getSide()) != null && view.getBe().getScreen(view.getSide()).browser == browser;
        if ((!physical && !RackClientApps.accepts(browser, RackModuleType.PROXY)) ||
                frame == null || !frame.isMain() ||
                !ProxyService.PAGE.equals(frame.getURL()) || !ProxyService.PAGE.equals(browser.getURL())) {
            callback.failure(403, "denied");
            return true;
        }
        String action = data == null || !data.has("action") ? "poll" : data.get("action").getAsString();
        try {
            switch (action) {
                case "poll" -> callback.success(ProxyService.poll(browser, number(data, "after")).toString());
                case "mode" -> {
                    if (!ClientConfig.proxyEnabled) {
                        callback.failure(403, "disabled");
                        return true;
                    }
                    ProxyService.setMode(browser, text(data, "value"));
                    NetworkLogService.pulseDisplay(browser);
                    callback.success("ok");
                }
                case "scope" -> {
                    ProxyService.setScope(browser, text(data, "value"));
                    callback.success("ok");
                }
                case "filter" -> {
                    ProxyService.setFilter(browser, text(data, "value"));
                    callback.success("ok");
                }
                case "forward" -> {
                    boolean done = ProxyService.forward(browser, number(data, "id"), data);
                    if (done) NetworkLogService.pulseDisplay(browser);
                    callback.success(Boolean.toString(done));
                }
                case "drop" -> callback.success(Boolean.toString(ProxyService.drop(browser, number(data, "id"))));
                case "forwardAll" -> callback.success(Integer.toString(ProxyService.releaseAll(browser, false)));
                case "dropAll" -> callback.success(Integer.toString(ProxyService.releaseAll(browser, true)));
                case "clear" -> callback.success(Boolean.toString(ProxyService.clearHistory(browser)));
                case "detail" -> {
                    JsonObject detail = ProxyService.detail(browser, number(data, "id"));
                    if (detail == null || detail.toString().length() > 1048576) callback.failure(404, "unavailable");
                    else callback.success(detail.toString());
                }
                default -> callback.failure(400, "invalidAction");
            }
        } catch (RuntimeException error) {
            callback.failure(400, "invalidData");
        }
        return true;
    }

    private static long number(JsonObject data, String key) {
        if (data == null || !data.has(key)) return 0;
        try {
            return Math.max(0, data.get(key).getAsLong());
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static String text(JsonObject data, String key) {
        return data == null || !data.has(key) ? "" : data.get(key).getAsString();
    }
}
