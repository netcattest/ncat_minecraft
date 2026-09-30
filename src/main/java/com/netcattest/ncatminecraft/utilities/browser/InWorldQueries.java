package com.netcattest.ncatminecraft.utilities.browser;

import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.client.ssh.SshQuery;
import com.netcattest.ncatminecraft.client.ssh.LocaleQuery;
import com.netcattest.ncatminecraft.client.sftp.SftpQuery;
import com.netcattest.ncatminecraft.client.log.LogQuery;
import com.netcattest.ncatminecraft.client.log.LogInspectQuery;
import com.netcattest.ncatminecraft.client.devtools.DevToolsQuery;
import com.netcattest.ncatminecraft.client.devtools.DevResultQuery;
import com.netcattest.ncatminecraft.client.terminal.TerminalQuery;
import com.netcattest.ncatminecraft.client.workstation.WorkstationQuery;
import com.netcattest.ncatminecraft.client.proxy.ProxyQuery;
import com.netcattest.ncatminecraft.client.remote.RemoteQuery;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.browser.handlers.js.queries.GetSizeQuery;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;

public class InWorldQueries {
    private static final GetSizeQuery getSize = new GetSizeQuery();
    private static final LocaleQuery locale = new LocaleQuery();

    public static void attach(ScreenBlockEntity blockEntity, BlockSide side, WDBrowser browser) {
        browser.setBe(blockEntity, side);
        browser.queryHandlers().put(getSize.getName(), getSize);
        browser.queryHandlers().put(locale.getName(), locale);
        if (BlockRegistry.isSshScreen(blockEntity.getBlockState().getBlock())) {
            for (String name : new String[]{"SshConnect", "SshInput", "SshResize", "SshTrust", "SshDisconnect", "SshClipboard"})
                browser.queryHandlers().put(name, new SshQuery(name));
        }
        if (BlockRegistry.isSftpScreen(blockEntity.getBlockState().getBlock())) {
            SftpQuery sftp = new SftpQuery();
            browser.queryHandlers().put(sftp.getName(), sftp);
        }
        if (BlockRegistry.isLogScreen(blockEntity.getBlockState().getBlock())) {
            LogQuery log = new LogQuery();
            browser.queryHandlers().put(log.getName(), log);
            LogInspectQuery inspect = new LogInspectQuery();
            browser.queryHandlers().put(inspect.getName(), inspect);
        }
        if (BlockRegistry.isDevToolsScreen(blockEntity.getBlockState().getBlock())) {
            DevToolsQuery tools = new DevToolsQuery();
            browser.queryHandlers().put(tools.getName(), tools);
        }
        if (BlockRegistry.isBrowserScreen(blockEntity.getBlockState().getBlock())) {
            DevResultQuery result = new DevResultQuery();
            browser.queryHandlers().put(result.getName(), result);
        }
        if (BlockRegistry.isTerminalScreen(blockEntity.getBlockState().getBlock())) {
            for (String name : TerminalQuery.NAMES)
                browser.queryHandlers().put(name, new TerminalQuery(name));
        }
        if (BlockRegistry.isWorkstationScreen(blockEntity.getBlockState().getBlock())) {
            WorkstationQuery workstation = new WorkstationQuery();
            browser.queryHandlers().put(workstation.getName(), workstation);
        }
        if (BlockRegistry.isRemoteScreen(blockEntity.getBlockState().getBlock())) {
            RemoteQuery remote = new RemoteQuery();
            browser.queryHandlers().put(remote.getName(), remote);
        }
        if (BlockRegistry.isProxyScreen(blockEntity.getBlockState().getBlock())) {
            ProxyQuery proxy = new ProxyQuery();
            browser.queryHandlers().put(proxy.getName(), proxy);
        }
    }
}
