package com.netcattest.ncatminecraft.utilities.browser.handlers;

import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.client.ClientProxy;
import com.netcattest.ncatminecraft.client.ssh.SshClientSessions;
import com.netcattest.ncatminecraft.client.remote.RemoteClientSessions;
import com.netcattest.ncatminecraft.client.devtools.DevToolsService;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.browser.WDBrowser;
import com.netcattest.ncatminecraft.utilities.browser.handlers.js.Scripts;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.net.server_bound.C2SMessageMinepadUrl;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefDisplayHandler;

public class DisplayHandler implements CefDisplayHandler {


    public static final CefDisplayHandler INSTANCE = new DisplayHandler();

    @Override
    public void onAddressChange(CefBrowser browser, CefFrame cefFrame, String url) {
        if (browser instanceof WDBrowser view && view.getBe() != null) {
            if (BlockRegistry.isSshScreen(view.getBe().getBlockState().getBlock())) {
                if (!"mod://ncat_minecraft/ssh.html".equals(url)) {
                    SshClientSessions.disconnect(browser);
                    browser.loadURL("mod://ncat_minecraft/ssh.html");
                }
                return;
            }
            if (BlockRegistry.isSftpScreen(view.getBe().getBlockState().getBlock())) {
                if (!"mod://ncat_minecraft/sftp.html".equals(url)) {
                    browser.loadURL("mod://ncat_minecraft/sftp.html");
                }
                return;
            }
            if (BlockRegistry.isLogScreen(view.getBe().getBlockState().getBlock())) {
                if (!"mod://ncat_minecraft/log.html".equals(url))
                    browser.loadURL("mod://ncat_minecraft/log.html");
                return;
            }
            if (BlockRegistry.isDevToolsScreen(view.getBe().getBlockState().getBlock())) {
                if (!"mod://ncat_minecraft/devtools.html".equals(url))
                    browser.loadURL("mod://ncat_minecraft/devtools.html");
                return;
            }
            if (BlockRegistry.isProxyScreen(view.getBe().getBlockState().getBlock())) {
                if (!"mod://ncat_minecraft/proxy.html".equals(url))
                    browser.loadURL("mod://ncat_minecraft/proxy.html");
                return;
            }
            if (BlockRegistry.isRemoteScreen(view.getBe().getBlockState().getBlock())) {
                if (cefFrame != null && cefFrame.isMain() && !"mod://ncat_minecraft/remote.html".equals(url)) {
                    RemoteClientSessions.disconnect(browser);
                    browser.loadURL("mod://ncat_minecraft/remote.html");
                }
                return;
            }
        }
        ClientProxy proxy = ((ClientProxy) NcatMinecraft.PROXY);

        if (browser != null) {
            long t = System.currentTimeMillis();

            for (ClientProxy.PadData pd : proxy.getPads()) {
                if (pd.view == browser && t - pd.lastSent() >= 1000) {
                    if (NcatMinecraft.isSiteBlacklisted(url))
                        pd.view.loadURL(NcatMinecraft.BLACKLIST_URL);
                    else {
                        pd.updateTime();
                        WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageMinepadUrl(pd.id, url));
                    }

                    break;
                }
            }

            for (ScreenBlockEntity tes : proxy.getScreens())
                tes.updateClientSideURL(browser, url);
        }

        browser.executeJavaScript(Scripts.POINTER_LOCK, "NcatMinecraft", 0);
    }

    @Override
    public void onTitleChange(CefBrowser cefBrowser, String s) {
    }

    @Override
    public boolean onTooltip(CefBrowser cefBrowser, String s) {
        return false;
    }

    @Override
    public void onStatusMessage(CefBrowser cefBrowser, String s) {
    }

    @Override
    public boolean onConsoleMessage(CefBrowser cefBrowser, CefSettings.LogSeverity logSeverity, String s, String s1, int i) {
        DevToolsService.recordConsole(cefBrowser, logSeverity == null ? "info" : logSeverity.name(), s, s1, i);
        return false;
    }

    @Override
    public boolean onCursorChange(CefBrowser cefBrowser, int i) {
        return false;
    }
}
