package com.netcattest.ncatminecraft.client.rack;

import com.cinemamod.mcef.MCEFBrowser;
import com.netcattest.ncatminecraft.client.devtools.DevResultQuery;
import com.netcattest.ncatminecraft.client.devtools.DevToolsQuery;
import com.netcattest.ncatminecraft.client.log.LogInspectQuery;
import com.netcattest.ncatminecraft.client.log.LogQuery;
import com.netcattest.ncatminecraft.client.log.NetworkLogService;
import com.netcattest.ncatminecraft.client.sftp.SftpQuery;
import com.netcattest.ncatminecraft.client.ssh.LocaleQuery;
import com.netcattest.ncatminecraft.client.ssh.SshQuery;
import com.netcattest.ncatminecraft.client.proxy.ProxyQuery;
import com.netcattest.ncatminecraft.client.remote.RemoteQuery;
import com.netcattest.ncatminecraft.client.terminal.TerminalQuery;
import com.netcattest.ncatminecraft.entity.RackBlockEntity;
import com.netcattest.ncatminecraft.entity.RackModule;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.config.CommonConfig;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.utilities.browser.WDBrowser;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.net.server_bound.C2SMessageRackFlow;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.cef.browser.CefBrowser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class RackClientApps {
    private record Key(Level level, BlockPos pos, UUID moduleId) { }
    public record Ref(RackBlockEntity rack, RackModule module) { }

    private static final Map<Key, Entry> APPS = new ConcurrentHashMap<>();
    private static final Map<CefBrowser, Entry> BY_BROWSER = new ConcurrentHashMap<>();

    private static final class Entry {
        final Key key;
        final RackModuleType type;
        final CopyOnWriteArrayList<CefBrowser> tabs = new CopyOnWriteArrayList<>();
        volatile int activeTab;
        int width;
        int height;

        Entry(Key key, RackModuleType type, CefBrowser browser) {
            this.key = key;
            this.type = type;
            this.tabs.add(browser);
        }

        CefBrowser current() { return tabs.get(Math.min(activeTab, tabs.size() - 1)); }
    }

    private RackClientApps() { }

    public static CefBrowser browser(DataCableService.RackModuleEndpoint target, int width, int height) {
        RackBlockEntity rack = target.rack();
        RackModule module = target.module();
        if (rack == null || module == null || rack.getLevel() == null || !module.powered() || module.type().isSwitch()) return null;
        Minecraft client = Minecraft.getInstance();
        if (client.level != rack.getLevel() || client.player == null || !rack.canConfigure(client.player)) return null;
        Key key = new Key(rack.getLevel(), rack.getBlockPos(), module.id());
        Entry entry = APPS.get(key);
        if (entry == null) {
            CefBrowser created = WDBrowser.createBrowser(initialUrl(module.type()), false);
            if (!(created instanceof WDBrowser view)) {
                created.close(true);
                return null;
            }
            attachQueries(view, module.type());
            entry = new Entry(key, module.type(), created);
            APPS.put(key, entry);
            BY_BROWSER.put(created, entry);
            if (module.type() == RackModuleType.BROWSER) NetworkLogService.trackRackSource(created, key);
        }
        int resolvedWidth = Math.max(320, width);
        int resolvedHeight = Math.max(180, height);
        if (entry.width != resolvedWidth || entry.height != resolvedHeight) {
            entry.width = resolvedWidth;
            entry.height = resolvedHeight;
            for (CefBrowser tab : entry.tabs)
                if (tab instanceof MCEFBrowser mcef) mcef.resize(resolvedWidth, resolvedHeight);
        }
        return entry.current();
    }

    private static String initialUrl(RackModuleType type) {
        return switch (type) {
            case BROWSER -> "mod://ncat_minecraft/main.html";
            case SSH -> "mod://ncat_minecraft/ssh.html";
            case SFTP -> "mod://ncat_minecraft/sftp.html";
            case LOG -> "mod://ncat_minecraft/log.html";
            case DEVTOOLS -> "mod://ncat_minecraft/devtools.html";
            case TERMINAL -> "mod://ncat_minecraft/terminal.html";
            case REMOTE -> "mod://ncat_minecraft/remote.html";
            case PROXY -> "mod://ncat_minecraft/proxy.html";
            default -> "mod://ncat_minecraft/main.html";
        };
    }

    private static void attachQueries(WDBrowser browser, RackModuleType type) {
        LocaleQuery locale = new LocaleQuery();
        browser.queryHandlers().put(locale.getName(), locale);
        switch (type) {
            case BROWSER -> {
                DevResultQuery result = new DevResultQuery();
                browser.queryHandlers().put(result.getName(), result);
            }
            case SSH -> {
                for (String name : new String[]{"SshConnect", "SshInput", "SshResize", "SshTrust", "SshDisconnect", "SshClipboard"})
                    browser.queryHandlers().put(name, new SshQuery(name));
            }
            case SFTP -> {
                SftpQuery sftp = new SftpQuery();
                browser.queryHandlers().put(sftp.getName(), sftp);
            }
            case LOG -> {
                LogQuery log = new LogQuery();
                LogInspectQuery inspect = new LogInspectQuery();
                browser.queryHandlers().put(log.getName(), log);
                browser.queryHandlers().put(inspect.getName(), inspect);
            }
            case DEVTOOLS -> {
                DevToolsQuery devtools = new DevToolsQuery();
                browser.queryHandlers().put(devtools.getName(), devtools);
            }
            case TERMINAL -> {
                for (String name : TerminalQuery.NAMES)
                    browser.queryHandlers().put(name, new TerminalQuery(name));
            }
            case REMOTE -> {
                RemoteQuery remote = new RemoteQuery();
                browser.queryHandlers().put(remote.getName(), remote);
            }
            case PROXY -> {
                ProxyQuery proxy = new ProxyQuery();
                browser.queryHandlers().put(proxy.getName(), proxy);
            }
            default -> { }
        }
    }

    public static Ref ref(CefBrowser browser) {
        if (browser == null) return null;
        Entry entry = BY_BROWSER.get(browser);
        if (entry == null) return null;
        Level level = entry.key.level();
        if (Minecraft.getInstance().level != level || !level.hasChunkAt(entry.key.pos())) return null;
        RackBlockEntity rack = RackBlockEntity.at(level, entry.key.pos());
        if (rack == null) return null;
        RackModule module = rack.getModule(entry.key.moduleId());
        return module != null && module.powered() && module.type() == entry.type ? new Ref(rack, module) : null;
    }

    public static boolean accepts(CefBrowser browser, RackModuleType type) {
        Ref ref = ref(browser);
        Minecraft client = Minecraft.getInstance();
        return ref != null && ref.module().type() == type && client.player != null && ref.rack().canConfigure(client.player);
    }

    public static CefBrowser linkedBrowser(CefBrowser display, RackModuleType sourceType) {
        Ref from = ref(display);
        if (from == null) return null;
        List<DataCableService.RackModuleEndpoint> reachable = DataCableService.connectedRackModules(
                from.rack().getLevel(), from.rack(), from.module().id());
        for (DataCableService.RackModuleEndpoint target : reachable) {
            if (target.module().type() != sourceType || !target.module().powered()) continue;
            Key key = new Key(target.rack().getLevel(), target.rack().getBlockPos(), target.module().id());
            Entry entry = APPS.get(key);
            CefBrowser browser = entry == null ? browser(target, 1280, 720) : entry.current();
            if (browser != null && ref(browser) != null) return browser;
        }
        for (DataCableService.ScreenEndpoint target : DataCableService.connectedScreens(
                from.rack().getLevel(), from.rack(), from.module().id())) {
            boolean correctType = sourceType == RackModuleType.BROWSER ?
                    BlockRegistry.isBrowserScreen(target.entity().getBlockState().getBlock()) :
                    sourceType == RackModuleType.SSH && BlockRegistry.isSshScreen(target.entity().getBlockState().getBlock());
            if (!correctType) continue;
            ScreenData data = target.entity().getScreen(target.side());
            if (data == null || data.owner == null || Minecraft.getInstance().player == null ||
                    (data.rightsFor(Minecraft.getInstance().player) & ScreenRights.INTERACT) == 0) continue;
            if (data.browser == null) data.createBrowser(target.entity(), false);
            if (data.browser != null) return data.browser;
        }
        return null;
    }

    public static Collection<CefBrowser> activeBrowsers(RackModuleType type) {
        List<CefBrowser> result = new ArrayList<>();
        for (Entry entry : APPS.values())
            if (entry.type == type && ref(entry.current()) != null) result.addAll(entry.tabs);
        return result;
    }

    private record FlowEnd(BlockPos pos, UUID module, BlockSide side) { }

    private static FlowEnd flowEnd(CefBrowser browser) {
        if (browser == null) return null;
        Ref rack = ref(browser);
        if (rack != null) return new FlowEnd(rack.rack().getBlockPos(), rack.module().id(), null);
        if (browser instanceof WDBrowser view && view.getBe() != null && view.getSide() != null)
            return new FlowEnd(view.getBe().getBlockPos(), null, view.getSide());
        return null;
    }

    public static void sendFlow(CefBrowser display, CefBrowser source) {
        FlowEnd from = flowEnd(display);
        FlowEnd to = flowEnd(source);
        if (from != null && to != null && (from.module() != null || to.module() != null))
            WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageRackFlow(from.pos(), from.module(), from.side(),
                    to.pos(), to.module(), to.side()));
    }

    public static Object groupKey(CefBrowser browser) {
        Entry entry = BY_BROWSER.get(browser);
        return entry == null ? null : entry.key;
    }

    public static int tabCount(CefBrowser browser) {
        Entry entry = BY_BROWSER.get(browser);
        return entry == null || entry.type != RackModuleType.BROWSER ? 0 : entry.tabs.size();
    }

    public static int activeTab(CefBrowser browser) {
        Entry entry = BY_BROWSER.get(browser);
        return entry == null || entry.type != RackModuleType.BROWSER ? 0 : entry.activeTab;
    }

    public static void newTab(CefBrowser browser) {
        Entry entry = BY_BROWSER.get(browser);
        if (entry == null || entry.type != RackModuleType.BROWSER || entry.tabs.size() >= 8 || ref(browser) == null) return;
        CefBrowser created = WDBrowser.createBrowser(CommonConfig.Browser.homepage, false);
        if (!(created instanceof WDBrowser view)) { created.close(true); return; }
        attachQueries(view, RackModuleType.BROWSER);
        if (created instanceof MCEFBrowser mcef && entry.width > 0 && entry.height > 0)
            mcef.resize(entry.width, entry.height);
        entry.tabs.add(created);
        entry.activeTab = entry.tabs.size() - 1;
        BY_BROWSER.put(created, entry);
        NetworkLogService.trackRackSource(created, entry.key);
    }

    public static void switchTab(CefBrowser browser, int index) {
        Entry entry = BY_BROWSER.get(browser);
        if (entry != null && entry.type == RackModuleType.BROWSER && index >= 0 && index < entry.tabs.size())
            entry.activeTab = index;
    }

    public static void closeTab(CefBrowser browser) {
        Entry entry = BY_BROWSER.get(browser);
        if (entry == null || entry.type != RackModuleType.BROWSER || entry.tabs.size() < 2) return;
        CefBrowser old = entry.tabs.remove(entry.activeTab);
        BY_BROWSER.remove(old);
        old.close(true);
        entry.activeTab = Math.min(entry.activeTab, entry.tabs.size() - 1);
    }

    public static void tick() {
        List<Entry> invalid = new ArrayList<>();
        for (Entry entry : APPS.values()) if (ref(entry.current()) == null) invalid.add(entry);
        for (Entry entry : invalid) {
            APPS.remove(entry.key);
            for (CefBrowser tab : entry.tabs) {
                BY_BROWSER.remove(tab);
                tab.close(true);
            }
        }
    }

    public static void clear() {
        for (Entry entry : APPS.values()) for (CefBrowser tab : entry.tabs) tab.close(true);
        APPS.clear();
        BY_BROWSER.clear();
    }
}
