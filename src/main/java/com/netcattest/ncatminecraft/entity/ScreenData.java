package com.netcattest.ncatminecraft.entity;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.cinemamod.mcef.listeners.MCEFCursorChangeListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.client.ClientProxy;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.config.CommonConfig;
import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.utilities.*;
import com.netcattest.ncatminecraft.utilities.browser.InWorldQueries;
import com.netcattest.ncatminecraft.utilities.browser.WDBrowser;
import com.netcattest.ncatminecraft.client.log.NetworkLogService;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.data.Rotation;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;
import com.netcattest.ncatminecraft.utilities.serialization.NameUUIDPair;
import org.cef.browser.CefBrowser;

import java.util.ArrayList;
import java.util.UUID;

public class ScreenData {
    public BlockSide side;
    public Vector2i size;
    public Vector2i resolution;
    public Rotation rotation = Rotation.ROT_0;
    public String url;
    public String syncedUrl;
    public Vector3i logSourcePos;
    public BlockSide logSourceSide;
    public boolean logSourceViaCable;
    public final ArrayList<DataPort> dataPorts = new ArrayList<>();
    public final ArrayList<UsbPort> usbPorts = new ArrayList<>();
    protected VideoType videoType;
    public NameUUIDPair owner;
    public ArrayList<NameUUIDPair> friends;
    public int friendRights;
    public int otherRights;
    public CefBrowser browser;
    private final ArrayList<CefBrowser> tabs = new ArrayList<>();
    private int activeTab;
    public ArrayList<ItemStack> upgrades;
    public boolean doTurnOnAnim;
    public long turnOnTime;
    public Player laserUser;
    public final Vector2i lastMousePos = new Vector2i();
    public NibbleArray redstoneStatus;
    public boolean autoVolume = true;

    public int mouseType;

    public ScreenData() {
        dataPorts.add(DataPort.automatic());
        usbPorts.add(UsbPort.automatic());
    }

    public static ScreenData deserialize(CompoundTag tag) {
        ScreenData ret = new ScreenData();
        ret.side = BlockSide.values()[tag.getByte("Side")];
        ret.size = new Vector2i(tag.getInt("Width"), tag.getInt("Height"));
        ret.resolution = new Vector2i(tag.getInt("ResolutionX"), tag.getInt("ResolutionY"));
        ret.rotation = Rotation.values()[tag.getByte("Rotation")];
        ret.url = tag.getString("URL");
        if (tag.contains("LogSource", 10)) {
            CompoundTag source = tag.getCompound("LogSource");
            BlockSide linkedSide = BlockSide.fromInt(source.getByte("Side"));
            if (linkedSide != null) {
                ret.logSourcePos = new Vector3i(source.getInt("X"), source.getInt("Y"), source.getInt("Z"));
                ret.logSourceSide = linkedSide;
            }
        }
        ret.logSourceViaCable = tag.getBoolean("LogSourceViaCable");
        if (tag.contains("DataPorts", 9)) {
            ret.dataPorts.clear();
            ListTag ports = tag.getList("DataPorts", 10);
            for (int i = 0; i < Math.min(12, ports.size()); i++) {
                DataPort port = DataPort.load(ports.getCompound(i));
                if (port != null) ret.dataPorts.add(port);
            }
            if (ret.dataPorts.stream().noneMatch(port -> port.automatic))
                ret.dataPorts.add(0, DataPort.automatic());
        }
        if (tag.contains("UsbPorts", 9)) {
            ret.usbPorts.clear();
            ListTag ports = tag.getList("UsbPorts", 10);
            for (int i = 0; i < Math.min(8, ports.size()); i++) {
                UsbPort port = UsbPort.load(ports.getCompound(i));
                if (port != null) ret.usbPorts.add(port);
            }
            if (ret.usbPorts.stream().noneMatch(port -> port.automatic)) ret.usbPorts.add(0, UsbPort.automatic());
        }
        ret.videoType = VideoType.getTypeFromURL(ret.url);

        if (ret.resolution.x <= 0 || ret.resolution.y <= 0) {
            float psx = ((float) ret.size.x) * 16.f - 4.f;
            float psy = ((float) ret.size.y) * 16.f - 4.f;
            psx *= 8.f;
            psy *= 8.f;

            ret.resolution.x = (int) psx;
            ret.resolution.y = (int) psy;
        }

        if (tag.contains("OwnerName")) {
            String name = tag.getString("OwnerName");
            UUID uuid = tag.getUUID("OwnerUUID");
            ret.owner = new NameUUIDPair(name, uuid);
        }

        ListTag friends = tag.getList("Friends", 10);
        ret.friends = new ArrayList<>(friends.size());

        for (int i = 0; i < friends.size(); i++) {
            CompoundTag nf = friends.getCompound(i);
            NameUUIDPair pair = new NameUUIDPair(nf.getString("Name"), nf.getUUID("UUID"));
            ret.friends.add(pair);
        }

        ret.friendRights = tag.getByte("FriendRights");
        ret.otherRights = tag.getByte("OtherRights");

        ListTag upgrades = tag.getList("Upgrades", 10);
        ret.upgrades = new ArrayList<>();

        for (int i = 0; i < upgrades.size(); i++)
            ret.upgrades.add(ItemStack.of(upgrades.getCompound(i)));

        if (tag.contains("AutoVolume"))
            ret.autoVolume = tag.getBoolean("AutoVolume");

        return ret;
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("Side", (byte) side.ordinal());
        tag.putInt("Width", size.x);
        tag.putInt("Height", size.y);
        tag.putInt("ResolutionX", resolution.x);
        tag.putInt("ResolutionY", resolution.y);
        tag.putByte("Rotation", (byte) rotation.ordinal());
        tag.putString("URL", url);
        if (logSourcePos != null && logSourceSide != null) {
            CompoundTag source = new CompoundTag();
            source.putInt("X", logSourcePos.x);
            source.putInt("Y", logSourcePos.y);
            source.putInt("Z", logSourcePos.z);
            source.putByte("Side", (byte) logSourceSide.ordinal());
            tag.put("LogSource", source);
        }
        tag.putBoolean("LogSourceViaCable", logSourceViaCable);
        ListTag ports = new ListTag();
        for (DataPort port : dataPorts) ports.add(port.save());
        tag.put("DataPorts", ports);
        ListTag usb = new ListTag();
        for (UsbPort port : usbPorts) usb.add(port.save());
        tag.put("UsbPorts", usb);

        if (owner == null)
            Log.warning("Found TES with NO OWNER!!");
        else {
            tag.putString("OwnerName", owner.name);
            tag.putUUID("OwnerUUID", owner.uuid);
        }

        ListTag list = new ListTag();
        for (NameUUIDPair f : friends) {
            CompoundTag nf = new CompoundTag();
            nf.putString("Name", f.name);
            nf.putUUID("UUID", f.uuid);

            list.add(nf);
        }

        tag.put("Friends", list);
        tag.putByte("FriendRights", (byte) friendRights);
        tag.putByte("OtherRights", (byte) otherRights);

        list = new ListTag();
        for (ItemStack is : upgrades)
            list.add(is.save(new CompoundTag()));

        tag.put("Upgrades", list);
        tag.putBoolean("AutoVolume", autoVolume);
        return tag;
    }

    public int rightsFor(Player ply) {
        return rightsFor(ply.getGameProfile().getId());
    }

    public int rightsFor(UUID uuid) {
        if (owner.uuid.equals(uuid))
            return ScreenRights.ALL;

        return friends.stream().anyMatch(f -> f.uuid.equals(uuid)) ? friendRights : otherRights;
    }

    public void setupRedstoneStatus(Level world, BlockPos start) {
        if (world.isClientSide()) {
            Log.warning("Called Screen.setupRedstoneStatus() on client.");
            return;
        }

        if (redstoneStatus != null) {
            Log.warning("Called Screen.setupRedstoneStatus() on server, but redstone status is non-null");
            return;
        }

        Direction[] VALUES = Direction.values();
        redstoneStatus = new NibbleArray(size.x * size.y);
        final Direction facing = VALUES[side.reverse().ordinal()];
        final ScreenIterator it = new ScreenIterator(start, side, size);

        while (it.hasNext()) {
            int idx = it.getIndex();
            redstoneStatus.set(idx, world.getSignal(it.next(), facing));
        }
    }


    public void clampResolution() {
        if (resolution.x > CommonConfig.Screen.maxResolutionX) {
            float newY = ((float) resolution.y) * ((float) CommonConfig.Screen.maxResolutionX) / ((float) resolution.x);
            resolution.x = CommonConfig.Screen.maxResolutionX;
            resolution.y = (int) newY;
        }

        if (resolution.y > CommonConfig.Screen.maxResolutionY) {
            float newX = ((float) resolution.x) * ((float) CommonConfig.Screen.maxResolutionY) / ((float) resolution.y);
            resolution.x = (int) newX;
            resolution.y = CommonConfig.Screen.maxResolutionY;
        }
    }

    public void createBrowser(ScreenBlockEntity be, boolean doAnim) {
        if (NcatMinecraft.PROXY instanceof ClientProxy) {
            String page = BlockRegistry.screenPage(be.getBlockState().getBlock());
            String initialUrl = page != null ? page
                    : NcatMinecraft.applyBlacklist(url != null ? url : "https://www.google.com");
            browser = newBrowser(be, initialUrl);
            if (BlockRegistry.isBrowserScreen(be.getBlockState().getBlock()))
                tabs.add(browser);

            if (browser instanceof MCEFBrowser mcefBrowser) {
                if (rotation.isVertical)
                    mcefBrowser.resize(resolution.y, resolution.x);
                else
                    mcefBrowser.resize(resolution.x, resolution.y);

                mcefBrowser.setCursorChangeListener((type) -> mouseType = type);
            }

            doTurnOnAnim = doAnim;
            turnOnTime = System.currentTimeMillis();
        }
    }

    private CefBrowser newBrowser(ScreenBlockEntity be, String address) {
        CefBrowser created = WDBrowser.createBrowser(address, false);
        if (created instanceof MCEFBrowser mcefBrowser) {
            if (BlockRegistry.isRemoteScreen(be.getBlockState().getBlock()))
                mcefBrowser.useBrowserControls(false);
            if (rotation.isVertical)
                mcefBrowser.resize(resolution.y, resolution.x);
            else
                mcefBrowser.resize(resolution.x, resolution.y);
            mcefBrowser.setCursorChangeListener(type -> mouseType = type);
        }
        if (created instanceof WDBrowser wdBrowser)
            InWorldQueries.attach(be, side, wdBrowser);
        if (BlockRegistry.isBrowserScreen(be.getBlockState().getBlock()))
            NetworkLogService.trackSource(created, this);
        return created;
    }

    public java.util.List<CefBrowser> browsers() {
        return tabs.isEmpty() ? (browser == null ? java.util.List.of() : java.util.List.of(browser)) : java.util.List.copyOf(tabs);
    }

    public int tabCount() { return tabs.size(); }
    public int activeTab() { return activeTab; }

    public void newTab(ScreenBlockEntity be) {
        if (tabs.size() >= 8) return;
        CefBrowser created = newBrowser(be, CommonConfig.Browser.homepage);
        tabs.add(created);
        activeTab = tabs.size() - 1;
        browser = created;
    }

    public void switchTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        activeTab = index;
        browser = tabs.get(index);
    }

    public void closeTab() {
        if (tabs.size() <= 1) return;
        CefBrowser closing = tabs.remove(activeTab);
        closing.close(true);
        activeTab = Math.min(activeTab, tabs.size() - 1);
        browser = tabs.get(activeTab);
    }

    public void closeBrowsers() {
        if (tabs.isEmpty()) {
            if (browser != null) browser.close(true);
        } else {
            for (CefBrowser tab : tabs) tab.close(true);
            tabs.clear();
        }
        browser = null;
        activeTab = 0;
    }
}
