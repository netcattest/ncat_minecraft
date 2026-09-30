/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.entity;

import com.cinemamod.mcef.MCEFBrowser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.block.ScreenBlock;
import com.netcattest.ncatminecraft.client.ClientProxy;
import com.netcattest.ncatminecraft.client.workstation.WorkstationClientView;
import com.netcattest.ncatminecraft.config.CommonConfig;
import com.netcattest.ncatminecraft.controls.builtin.ClickControl;
import com.netcattest.ncatminecraft.core.DefaultUpgrade;
import com.netcattest.ncatminecraft.core.IUpgrade;
import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.data.ScreenConfigData;
import com.netcattest.ncatminecraft.miniserv.SyncPlugin;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.net.client_bound.S2CMessageAddScreen;
import com.netcattest.ncatminecraft.net.client_bound.S2CMessageScreenUpdate;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.registry.ItemRegistry;
import com.netcattest.ncatminecraft.registry.TileRegistry;
import com.netcattest.ncatminecraft.utilities.Log;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.utilities.Multiblock;
import com.netcattest.ncatminecraft.utilities.ScreenIterator;
import com.netcattest.ncatminecraft.utilities.VideoType;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.data.Rotation;
import com.netcattest.ncatminecraft.utilities.math.MutableAABB;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;
import com.netcattest.ncatminecraft.utilities.math.Vector3f;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;
import com.netcattest.ncatminecraft.utilities.serialization.NameUUIDPair;
import com.netcattest.ncatminecraft.utilities.serialization.TypeData;
import org.cef.browser.CefBrowser;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static com.netcattest.ncatminecraft.block.PeripheralBlock.point;

public class ScreenBlockEntity extends BlockEntity {
    public ScreenBlockEntity(BlockPos arg2, BlockState arg3) {
        super(TileRegistry.SCREEN_BLOCK_ENTITY.get(), arg2, arg3);
    }

    public void forEachScreenBlocks(BlockSide side, Consumer<BlockPos> func) {
        ScreenData scr = getScreen(side);

        if (scr != null) {
            ScreenIterator it = new ScreenIterator(getBlockPos(), side, scr.size);

            while (it.hasNext())
                func.accept(it.next());
        }
    }

    private final ArrayList<ScreenData> screens = new ArrayList<>();
    private net.minecraft.world.phys.AABB renderBB = new net.minecraft.world.phys.AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);
    private boolean loaded = true;
    public float ytVolume = Float.POSITIVE_INFINITY;

    public boolean isLoaded() {
        return loaded;
    }

    public void load() {
        loaded = true;
    }

    public void unload() {
        for (ScreenData scr : screens) {
            if (scr.browser != null) {
                scr.closeBrowsers();
            }
        }
        screens.clear();

        loaded = false;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        ListTag list = tag.getList("WDScreens", Tag.TAG_COMPOUND);
        if (list.isEmpty())
            return;

        for (ScreenData screen : screens) {
            if (screen.browser != null) {
                screen.closeBrowsers();
            }
        }

        screens.clear();
        for (int i = 0; i < list.size(); i++)
            screens.add(ScreenData.deserialize(list.getCompound(i)));
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
        String page = BlockRegistry.screenPage(getBlockState().getBlock());
        for (ScreenData screen : screens) {
            if (screen.browser != null) continue;
            screen.createBrowser(this, false);
            screen.syncedUrl = page != null ? page : screen.url;
        }
        updateAABB();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        ListTag list = new ListTag();
        for (ScreenData scr : screens)
            list.add(scr.serialize());

        tag.put("WDScreens", list);
    }

    public ScreenData addScreen(BlockSide side, Vector2i size, @Nullable Vector2i resolution, @Nullable Player owner, boolean sendUpdate) {
        for (ScreenData scr : screens) {
            if (scr.side == side)
                return scr;
        }

        ScreenData ret = new ScreenData();
        ret.side = side;
        ret.size = size;
        String page = BlockRegistry.screenPage(getBlockState().getBlock());
        ret.url = page != null ? page : CommonConfig.Browser.homepage;
        ret.friends = new ArrayList<>();
        ret.friendRights = ScreenRights.DEFAULTS;
        ret.otherRights = ScreenRights.DEFAULTS;
        ret.upgrades = new ArrayList<>();

        if (owner != null) {
            ret.owner = new NameUUIDPair(owner.getGameProfile());

            if (side == BlockSide.TOP || side == BlockSide.BOTTOM) {
                int rot = (int) Math.floor(((double) (owner.getYRot() * 4.0f / 360.0f)) + 2.5) & 3;

                if (side == BlockSide.TOP) {
                    if (rot == 1)
                        rot = 3;
                    else if (rot == 3)
                        rot = 1;
                }

                ret.rotation = Rotation.values()[rot];
            }
        }

        if (resolution == null || resolution.x < 1 || resolution.y < 1) {
            float psx = ((float) size.x) * 16.f - 4.f;
            float psy = ((float) size.y) * 16.f - 4.f;
            psx *= 8.f;
            psy *= 8.f;

            ret.resolution = new Vector2i((int) psx, (int) psy);
        } else
            ret.resolution = resolution;

        ret.clampResolution();

        if (!level.isClientSide) {
            ret.setupRedstoneStatus(level, getBlockPos());

            if (sendUpdate)
                WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() -> point(level, getBlockPos())), new S2CMessageAddScreen(this, ret));
        }

        screens.add(ret);

        if (level.isClientSide)
            updateAABB();
        else
            setChanged();


        return ret;
    }

    public ScreenData getScreen(BlockSide side) {
        for (ScreenData scr : screens) {
            if (scr.side == side)
                return scr;
        }

        return null;
    }

    public int screenCount() {
        return screens.size();
    }

    public ScreenData getScreen(int idx) {
        return screens.get(idx);
    }

    public void clear() {
        for (ScreenData screen : screens)
            if (screen.browser != null) {
                screen.closeBrowsers();
            }
        screens.clear();

        if (!level.isClientSide)
            setChanged();
    }

    public static String url(String url) throws IOException {
        Log.info("URL received: " + url);
        if (!(NcatMinecraft.PROXY instanceof ClientProxy)) {
            List<ServerPlayer> serverPlayers = NcatMinecraft.PROXY.getServer().getPlayerList().getPlayers();
            SyncPlugin.syncPlayers(serverPlayers);
            for (ServerPlayer serverPlayer : serverPlayers) {
                SyncPlugin.setPlayerString(serverPlayer, url);
            }
            return url;
        } else {
            return url;
        }
    }

    public void setScreenURL(BlockSide side, String url) throws IOException {
        if (BlockRegistry.isLocalScreen(getBlockState().getBlock()))
            return;
        ScreenData scr = getScreen(side);
        if (scr == null) {
            Log.error("Attempt to change URL of non-existing screen on side %s", side.toString());
            return;
        }

        String weburl = url(url);

        weburl = NcatMinecraft.applyBlacklist(weburl);
        scr.url = weburl;
        scr.videoType = VideoType.getTypeFromURL(weburl);

        if (level.isClientSide) {
            if (scr.browser != null)
                scr.browser.loadURL(weburl);
        } else {
            WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() -> point(level, getBlockPos())), S2CMessageScreenUpdate.setURL(this, side, weburl));
            setChanged();
        }
    }

    public void removeScreen(BlockSide side) {
        int idx = -1;
        for (int i = 0; i < screens.size(); i++) {
            if (screens.get(i).side == side) {
                idx = i;
                break;
            }
        }

        if (idx < 0) {
            Log.error("Tried to delete non-existing screen on side %s", side.toString());
            return;
        }

        if (level.isClientSide) {
            if (screens.get(idx).browser != null) {
                screens.get(idx).closeBrowsers();
            }
        } else
            WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() -> point(level, getBlockPos())), new S2CMessageScreenUpdate(this.getBlockPos(), side));

        screens.remove(idx);

        if (!level.isClientSide) {
            if (screens.isEmpty())
                level.setBlockAndUpdate(getBlockPos(), getBlockState().setValue(ScreenBlock.hasTE, false));
            else
                setChanged();
        }
    }

    public void setResolution(BlockSide side, Vector2i res) {
        if (res.x < 1 || res.y < 1) {
            Log.warning("Call to TileEntityScreen.setResolution(%s) with suspicious values X=%d and Y=%d", side.toString(), res.x, res.y);
            return;
        }

        ScreenData scr = getScreen(side);
        if (scr == null) {
            Log.error("Tried to change resolution of non-existing screen on side %s", side.toString());
            return;
        }

        scr.resolution = res;
        scr.clampResolution();

        if (level.isClientSide) {
            NcatMinecraft.PROXY.screenUpdateResolutionInGui(new Vector3i(getBlockPos()), side, res);

            if (scr.browser != null) {
                scr.closeBrowsers();
            }
        } else {
            WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() -> point(level, getBlockPos())), S2CMessageScreenUpdate.setResolution(this, side, res));
            setChanged();
        }
    }

    private static Player getLaserUser(ScreenData scr) {
        if (scr.laserUser != null) {
            if (scr.laserUser.isRemoved() || !scr.laserUser.getItemInHand(InteractionHand.MAIN_HAND).getItem().equals(ItemRegistry.LASER_POINTER.get()))
                scr.laserUser = null;
        }

        return scr.laserUser;
    }

    private static void checkLaserUserRights(ScreenData scr) {
        if (scr.laserUser != null && (scr.rightsFor(scr.laserUser) & ScreenRights.INTERACT) == 0)
            scr.laserUser = null;
    }

    public void clearLaserUser(BlockSide side) {
        ScreenData scr = getScreen(side);

        if (scr != null)
            scr.laserUser = null;
    }

    public void click(BlockSide side, Vector2i vec) {
        if (BlockRegistry.isLocalScreen(getBlockState().getBlock()))
            return;
        ScreenData scr = getScreen(side);
        if (scr == null) {
            Log.error("Attempt click non-existing screen of side %s", side.toString());
            return;
        }

        if (level.isClientSide)
            Log.warning("TileEntityScreen.click() from client side is useless...");
        else if (getLaserUser(scr) == null)
            WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() -> point(level, getBlockPos())), S2CMessageScreenUpdate.click(this, side, ClickControl.ControlType.CLICK, vec));
    }

    public void handleMouseEvent(BlockSide side, ClickControl.ControlType event, @Nullable Vector2i vec, int button) {
        if (button > 1) return;

        ScreenData scr = getScreen(side);
        if (scr == null) {
            Log.error("Attempt inject mouse events on non-existing screen of side %s", side.toString());
            return;
        }

        if (level != null && level.isClientSide && BlockRegistry.isWorkstationScreen(getBlockState().getBlock())) {
            WorkstationClientView.handleMouse(this, side, event, vec, button);
            return;
        }

        if (scr.browser instanceof MCEFBrowser mcefBrowser) {
            if (event == ClickControl.ControlType.CLICK) {
                mcefBrowser.sendMouseMove(vec.x, vec.y);
                mcefBrowser.sendMousePress(vec.x, vec.y, button);
                mcefBrowser.sendMouseRelease(vec.x, vec.y, button);
            } else if (event == ClickControl.ControlType.DOWN) {
                mcefBrowser.sendMouseMove(vec.x, vec.y);
                mcefBrowser.sendMousePress(vec.x, vec.y, button);
            } else if (event == ClickControl.ControlType.MOVE)
                mcefBrowser.sendMouseMove(vec.x, vec.y);
            else if (event == ClickControl.ControlType.UP)
                mcefBrowser.sendMouseRelease(scr.lastMousePos.x, scr.lastMousePos.y, button);

            mcefBrowser.setFocus(true);

            if (vec != null) {
                scr.lastMousePos.x = vec.x;
                scr.lastMousePos.y = vec.y;
            }
        }
    }


    @Override
    public void onLoad() {
        if (level.isClientSide) {
            NcatMinecraft.PROXY.trackScreen(this, true);
        }
    }

    @Override
    public void onChunkUnloaded() {
        if (level.isClientSide) {
            NcatMinecraft.PROXY.trackScreen(this, false);

            for (ScreenData scr : screens) {
                if (scr.browser != null) {
                    scr.closeBrowsers();
                }
            }
        }
    }

    public void updateAABB() {
        Vector3i origin = new Vector3i(getBlockPos());
        MutableAABB box = null;

        for (ScreenData scr : screens) {
            Vector3i f = scr.side.forward;

            int fx = Math.max(f.x, 0);
            int fy = Math.max(f.y, 0);
            int fz = Math.max(f.z, 0);
            int ox = 0;
            if (scr.side.equals(BlockSide.NORTH)) ox = 1;
            int oz = 0;
            if (
                    scr.side.equals(BlockSide.EAST) ||
                            scr.side.equals(BlockSide.TOP) ||
                            scr.side.equals(BlockSide.BOTTOM)
            ) oz = 1;

            if (box == null) {
                box = new MutableAABB(
                        origin.x + fx + ox,
                        origin.y + fy,
                        origin.z + fz + oz,

                        origin.x + ox + scr.side.right.x * scr.size.x + fx + scr.side.up.x * scr.size.y,
                        origin.y + scr.side.right.y * scr.size.x + fy + scr.side.up.y * scr.size.y,
                        origin.z + oz + scr.side.right.z * scr.size.x + fz + scr.side.up.z * scr.size.y
                );
            } else {
                box.expand(
                        origin.x + fx + ox,
                        origin.y + fy,
                        origin.z + fz + oz,

                        origin.x + ox + scr.side.right.x * scr.size.x + fx + scr.side.up.x * scr.size.y,
                        origin.y + scr.side.right.y * scr.size.x + fy + scr.side.up.y * scr.size.y,
                        origin.z + oz + scr.side.right.z * scr.size.x + fz + scr.side.up.z * scr.size.y
                );
            }
        }

        if (box == null) renderBB = new AABB(worldPosition);
        else renderBB = box.toMc();
    }

    @Override
    @Nonnull
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        double minX = renderBB.minX;
        double minY = renderBB.minY;
        double minZ = renderBB.minZ;
        double maxX = renderBB.maxX;
        double maxY = renderBB.maxY;
        double maxZ = renderBB.maxZ;
        for (ScreenData screen : screens) {
              for (DataPort port : screen.dataPorts) {
                if (!port.connected() || getBlockPos().distSqr(port.linkedPos) > 96.0 * 96.0) continue;
                minX = Math.min(minX, port.linkedPos.getX());
                minY = Math.min(minY, port.linkedPos.getY());
                minZ = Math.min(minZ, port.linkedPos.getZ());
                maxX = Math.max(maxX, port.linkedPos.getX() + 1);
                maxY = Math.max(maxY, port.linkedPos.getY() + 1);
                maxZ = Math.max(maxZ, port.linkedPos.getZ() + 1);
                for (Vec3 waypoint : port.waypoints) {
                    minX = Math.min(minX, waypoint.x);
                    minY = Math.min(minY, waypoint.y);
                    minZ = Math.min(minZ, waypoint.z);
                    maxX = Math.max(maxX, waypoint.x);
                    maxY = Math.max(maxY, waypoint.y);
                    maxZ = Math.max(maxZ, waypoint.z);
                }
            }
              for (UsbPort port : screen.usbPorts) {
                  if (port.keyboard == null || getBlockPos().distSqr(port.keyboard) > 96.0 * 96.0) continue;
                  minX = Math.min(minX, port.keyboard.getX());
                  minY = Math.min(minY, port.keyboard.getY());
                  minZ = Math.min(minZ, port.keyboard.getZ());
                  maxX = Math.max(maxX, port.keyboard.getX() + 1);
                  maxY = Math.max(maxY, port.keyboard.getY() + 1);
                  maxZ = Math.max(maxZ, port.keyboard.getZ() + 1);
                  for (Vec3 waypoint : port.waypoints) {
                      minX = Math.min(minX, waypoint.x);
                      minY = Math.min(minY, waypoint.y);
                      minZ = Math.min(minZ, waypoint.z);
                      maxX = Math.max(maxX, waypoint.x);
                      maxY = Math.max(maxY, waypoint.y);
                      maxZ = Math.max(maxZ, waypoint.z);
                  }
              }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(0.5);
    }


    public void updateClientSideURL(CefBrowser target, String url) {
        for (ScreenData scr : screens) {
            if (scr.browser == target) {
                String webUrl;
                try {
                    webUrl = ScreenBlockEntity.url(url);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                boolean blacklisted = NcatMinecraft.isSiteBlacklisted(url);
                scr.url = blacklisted ? NcatMinecraft.BLACKLIST_URL : url;
                scr.videoType = VideoType.getTypeFromURL(scr.url);
                ytVolume = Float.POSITIVE_INFINITY;

                if (blacklisted && scr.browser != null)
                    scr.browser.loadURL(NcatMinecraft.BLACKLIST_URL);

                break;
            }
        }
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();

        if (level.isClientSide)
            onChunkUnloaded();
    }

    public void addFriend(ServerPlayer ply, BlockSide side, NameUUIDPair pair) {
        if (!level.isClientSide) {
            ScreenData scr = getScreen(side);
            if (scr == null) {
                Log.error("Tried to add friend to invalid screen side %s", side.toString());
                return;
            }

            if (!scr.friends.contains(pair)) {
                scr.friends.add(pair);
                (new ScreenConfigData(new Vector3i(getBlockPos()), side, scr)).updateOnly().sendTo(point(level, getBlockPos()));
                DataCableService.sync(this, scr);
                setChanged();
            }
        }
    }

    public void removeFriend(ServerPlayer ply, BlockSide side, NameUUIDPair pair) {
        if (!level.isClientSide) {
            ScreenData scr = getScreen(side);
            if (scr == null) {
                Log.error("Tried to remove friend from invalid screen side %s", side.toString());
                return;
            }

            if (scr.friends.remove(pair)) {
                checkLaserUserRights(scr);
                (new ScreenConfigData(new Vector3i(getBlockPos()), side, scr)).updateOnly().sendTo(point(level, getBlockPos()));
                DataCableService.sync(this, scr);
                setChanged();
            }
        }
    }

    public void setRights(ServerPlayer ply, BlockSide side, int fr, int or) {
        if (!level.isClientSide) {
            ScreenData scr = getScreen(side);
            if (scr == null) {
                Log.error("Tried to change rights of invalid screen on side %s", side.toString());
                return;
            }

            scr.friendRights = fr;
            scr.otherRights = or;

            checkLaserUserRights(scr);
            (new ScreenConfigData(new Vector3i(getBlockPos()), side, scr)).updateOnly().sendTo(point(level, getBlockPos()));
            DataCableService.sync(this, scr);
            setChanged();
        }
    }

    public void type(BlockSide side, String text, BlockPos soundPos) {
        type(side, text, soundPos, null);
    }

    public void type(BlockSide side, String text, BlockPos soundPos, @Nullable ServerPlayer sender) {
        if (!level.isClientSide && BlockRegistry.isLocalScreen(getBlockState().getBlock()))
            return;
        ScreenData scr = getScreen(side);
        if (scr == null) {
            Log.error("Tried to type on invalid screen on side %s", side.toString());
            return;
        }

        if (level.isClientSide) {
            CefBrowser inputBrowser = BlockRegistry.isWorkstationScreen(getBlockState().getBlock()) ?
                    WorkstationClientView.browserForInput(this, side) : scr.browser;
            if (inputBrowser instanceof MCEFBrowser mcefBrowser) {
                try {
                    if (text.startsWith("t")) {
                        for (int i = 1; i < text.length(); i++) {
                            char chr = text.charAt(i);
                            if (chr == 1)
                                break;

                            mcefBrowser.sendKeyTyped(chr, 0);
                        }
                    } else {
                        TypeData[] data = NcatMinecraft.GSON.fromJson(text, TypeData[].class);

                        for (TypeData ev : data) {
                            if (ev.getKeyCode() == 257) {
                                ev = new TypeData(
                                        ev.getAction(),
                                        10, ev.getModifier(),
                                        ev.getScanCode()
                                );
                            }

                            switch (ev.getAction()) {
                                case PRESS -> {
                                    mcefBrowser.sendKeyPress(ev.getKeyCode(), ev.getScanCode(), ev.getModifier());
                                    if (ev.getKeyCode() == 10)
                                        mcefBrowser.sendKeyTyped('\r', ev.getModifier());
                                }
                                case RELEASE ->
                                        mcefBrowser.sendKeyRelease(ev.getKeyCode(), ev.getScanCode(), ev.getModifier());
                                case TYPE ->
                                        mcefBrowser.sendKeyTyped((char) ev.getKeyCode(), ev.getModifier());

                                default -> throw new RuntimeException("Invalid type action '" + ev.getAction() + '\'');
                            }
                        }
                    }
                } catch (Throwable t) {
                    Log.warningEx("Suspicious keyboard type packet received...", t);
                }
            }
        } else {
            WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(
                    sender != null ?
                            () -> point(sender, level, getBlockPos()) :
                            () -> point(level, getBlockPos())
            ), S2CMessageScreenUpdate.type(this, side, text));

            if (soundPos != null)
                playSoundAt(NcatMinecraft.INSTANCE.soundTyping, soundPos, 0.25f, 1.f);
        }
    }

    private void playSoundAt(SoundEvent snd, BlockPos at, float vol, float pitch) {
        double x = at.getX();
        double y = at.getY();
        double z = at.getZ();

        level.playSound(null, x + 0.5, y + 0.5, z + 0.5, snd, SoundSource.BLOCKS, vol, pitch);
    }


    private static String safeName(ItemStack is) {
        return is.getItem().getName(is).getString();
    }

    public boolean addUpgrade(BlockSide side, ItemStack is, @Nullable Player player, boolean abortIfExisting) {
        if (level.isClientSide) {
            IUpgrade itemAsUpgrade = (IUpgrade) is.getItem();
            ScreenData scr = getScreen(side);
            ItemStack isCopy = is.copy();
            scr.upgrades.add(isCopy);
            itemAsUpgrade.onInstall(this, side, player, isCopy);
            return false;
        }

        ScreenData scr = getScreen(side);
        if (scr == null) {
            Log.error("Tried to add an upgrade on invalid screen on side %s", side.toString());
            return false;
        }

        if (!(is.getItem() instanceof IUpgrade)) {
            Log.error("Tried to add a non-upgrade item %s to screen (%s does not implement IUpgrade)", safeName(is), is.getItem().getClass().getCanonicalName());
            return false;
        }

        if (scr.upgrades.size() >= 16) {
            Log.error("Can't insert upgrade %s in screen %s at %s: too many upgrades already!", safeName(is), side.toString(), getBlockPos().toString());
            return false;
        }

        IUpgrade itemAsUpgrade = (IUpgrade) is.getItem();
        if (abortIfExisting && scr.upgrades.stream().anyMatch(otherStack -> itemAsUpgrade.isSameUpgrade(is, otherStack)))
            return false;

        ItemStack isCopy = is.copy();
        isCopy.setCount(1);

        scr.upgrades.add(isCopy);
        if (player != null && !player.level().isClientSide) {
            WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() -> point(level, getBlockPos())), S2CMessageScreenUpdate.upgrade(this, side, true, is));
            itemAsUpgrade.onInstall(this, side, player, isCopy);
            playSoundAt(NcatMinecraft.INSTANCE.soundUpgradeAdd, getBlockPos(), 1.0f, 1.0f);
        }
        setChanged();
        return true;
    }

    public boolean hasUpgrade(BlockSide side, ItemStack is) {
        ScreenData scr = getScreen(side);
        if (scr == null)
            return false;

        if (!(is.getItem() instanceof IUpgrade))
            return false;

        IUpgrade itemAsUpgrade = (IUpgrade) is.getItem();
        return scr.upgrades.stream().anyMatch(otherStack -> itemAsUpgrade.isSameUpgrade(is, otherStack));
    }

    public boolean hasUpgrade(BlockSide side, DefaultUpgrade du) {
        ScreenData scr = getScreen(side);
        if (du == DefaultUpgrade.LASERMOUSE) {
            return scr != null && scr.upgrades.stream().anyMatch(du::matchesLaserMouse);
        } else if (du == DefaultUpgrade.REDINPUT) {
            return scr != null && scr.upgrades.stream().anyMatch(du::matchesRedInput);
        } else if (du == DefaultUpgrade.GPS) {
            return scr != null && scr.upgrades.stream().anyMatch(du::matchesGps);
        } else if (du == DefaultUpgrade.REDOUTPUT) {
            return scr != null && scr.upgrades.stream().anyMatch(du::matchesRedOutput);
        } else {
            return false;
        }
    }

    public void removeUpgrade(BlockSide side, ItemStack is, @Nullable Player player) {
        if (level.isClientSide)
            return;

        ScreenData scr = getScreen(side);
        if (scr == null) {
            Log.error("Tried to remove an upgrade on invalid screen on side %s", side.toString());
            return;
        }

        if (!(is.getItem() instanceof IUpgrade)) {
            Log.error("Tried to remove a non-upgrade item %s to screen (%s does not implement IUpgrade)", safeName(is), is.getItem().getClass().getCanonicalName());
            return;
        }

        int idxToRemove = -1;
        IUpgrade itemAsUpgrade = (IUpgrade) is.getItem();

        for (int i = 0; i < scr.upgrades.size(); i++) {
            if (itemAsUpgrade.isSameUpgrade(is, scr.upgrades.get(i))) {
                idxToRemove = i;
                break;
            }
        }

        if (idxToRemove >= 0) {
            dropUpgrade(scr.upgrades.get(idxToRemove), side, player);
            scr.upgrades.remove(idxToRemove);
            if (player != null && !player.level().isClientSide) {
                WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() -> point(level, getBlockPos())), S2CMessageScreenUpdate.upgrade(this, side, false, is));
                playSoundAt(NcatMinecraft.INSTANCE.soundUpgradeDel, getBlockPos(), 1.0f, 1.0f);
            }
            setChanged();
        } else
            Log.warning("Tried to remove non-existing upgrade %s to screen %s at %s", safeName(is), side.toString(), getBlockPos().toString());
    }

    private void dropUpgrade(ItemStack is, BlockSide side, @Nullable Player ply) {
        if (!((IUpgrade) is.getItem()).onRemove(this, side, ply, is)) {
            boolean spawnDrop = true;

            if (ply != null) {
                if (ply.isCreative() || ply.addItem(is))
                    spawnDrop = false;
            }

            if (spawnDrop) {
                Vector3f pos = new Vector3f((float) this.getBlockPos().getX(), (float) this.getBlockPos().getY(), (float) this.getBlockPos().getZ());
                pos.addMul(side.backward.toFloat(), 1.5f);

                if (level != null) {
                    level.addFreshEntity(new ItemEntity(level, pos.x, pos.y, pos.z, is));
                }
            }
        }
    }

    private ScreenData getScreenForLaserOp(BlockSide side, Player ply) {
        if (level.isClientSide || ply == null ||
                ply.getItemInHand(InteractionHand.MAIN_HAND).getItem() != ItemRegistry.LASER_POINTER.get() ||
                ply.distanceToSqr(getBlockPos().getX() + 0.5, getBlockPos().getY() + 0.5,
                        getBlockPos().getZ() + 0.5) > 72 * 72)
            return null;

        ScreenData scr = getScreen(side);
        if (scr == null) {
            Log.error("Called laser operation on invalid screen on side %s", side.toString());
            return null;
        }

        if ((scr.rightsFor(ply) & ScreenRights.INTERACT) == 0)
            return null;

        return scr;
    }

    public void laserDownMove(BlockSide side, Player ply, Vector2i pos, boolean down, int button) {
        ScreenData scr = getScreenForLaserOp(side, ply);

        if (scr != null) {
			if (pos == null || pos.x < 0 || pos.y < 0 || pos.x >= scr.resolution.x || pos.y >= scr.resolution.y)
				return;
			scr.lastMousePos.x = pos.x;
			scr.lastMousePos.y = pos.y;
			if (down)
				scr.laserUser = ply;
            if (button == -1)
                WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() -> point(ply, level, getBlockPos())), S2CMessageScreenUpdate.click(this, side, ClickControl.ControlType.MOVE, pos));
            else if (down)
                WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() -> point(ply, level, getBlockPos())), S2CMessageScreenUpdate.click(this, side, ClickControl.ControlType.DOWN, pos, button));
            else
                WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() -> point(ply, level, getBlockPos())), S2CMessageScreenUpdate.click(this, side, ClickControl.ControlType.UP, pos, button));
        }
    }

    public void laserUp(BlockSide side, Player ply, int button) {
        ScreenData scr = getScreen(side);

        if (scr != null && ply != null && !level.isClientSide && button >= 0 && button <= 1) {
            if (scr.laserUser == ply) {
                scr.laserUser = null;
                WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() -> point(ply, level, getBlockPos())),
                        S2CMessageScreenUpdate.click(this, side, ClickControl.ControlType.UP,
                                new Vector2i(scr.lastMousePos.x, scr.lastMousePos.y), button));
            }
        }
    }

    public void onDestroy(@Nullable Player ply) {
        for (ScreenData scr : screens) {
            scr.upgrades.forEach(is -> dropUpgrade(is, scr.side, ply));
            scr.upgrades.clear();
        }

        WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() -> point(level, getBlockPos())), S2CMessageScreenUpdate.turnOff(getBlockPos(), null));
    }

    public void disableScreen(BlockSide side) {
        ScreenData remove = null;
        for (ScreenData screen : screens) {
            if (screen.side == side) {
                remove = screen;
                break;
            }
        }

        if (remove == null) return;

        if (level != null && !level.isClientSide) {
            final ScreenData scrn = remove;
            remove.upgrades.forEach(is -> dropUpgrade(is, scrn.side, null));
        }

        remove.upgrades.clear();
        if (remove.browser != null)
            remove.closeBrowsers();
        screens.remove(remove);
    }

    public void setOwner(BlockSide side, Player newOwner) {
        if (level.isClientSide) {
            Log.error("Called TileEntityScreen.setOwner() on client...");
            return;
        }

        if (newOwner == null) {
            Log.error("Called TileEntityScreen.setOwner() with null owner");
            return;
        }

        ScreenData scr = getScreen(side);
        if (scr == null) {
            Log.error("Called TileEntityScreen.setOwner() on invalid screen on side %s", side.toString());
            return;
        }

        scr.owner = new NameUUIDPair(newOwner.getGameProfile());
        WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() -> point(level, getBlockPos())), S2CMessageScreenUpdate.owner(this, side, scr.owner));
        checkLaserUserRights(scr);
        setChanged();
    }

    public void setRotation(BlockSide side, Rotation rot) {
        ScreenData scr = getScreen(side);
        if (scr == null) {
            Log.error("Trying to change rotation of invalid screen on side %s", side.toString());
            return;
        }

        if (level.isClientSide) {
            boolean oldWasVertical = scr.rotation.isVertical;
            scr.rotation = rot;

            NcatMinecraft.PROXY.screenUpdateRotationInGui(new Vector3i(getBlockPos()), side, rot);

            if (scr.browser != null && oldWasVertical != rot.isVertical) {
                scr.closeBrowsers();
            }
        } else {
            scr.rotation = rot;
            WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() -> point(level, getBlockPos())), S2CMessageScreenUpdate.rotation(this, side, rot));
            setChanged();
        }
    }


    public void setAutoVolume(BlockSide side, boolean av) {
        ScreenData scr = getScreen(side);
        if (scr == null) {
            Log.error("Trying to toggle auto-volume on invalid screen (side %s)", side.toString());
            return;
        }

        scr.autoVolume = av;

        if (level.isClientSide)
            NcatMinecraft.PROXY.screenUpdateAutoVolumeInGui(new Vector3i(getBlockPos()), side, av);
        else {
            WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() -> point(level, getBlockPos())), S2CMessageScreenUpdate.autoVolume(this, side, av));
            setChanged();
        }
    }

    public void deactivate() {
        for (ScreenData screen : screens) {
            if (screen.browser != null) {
                screen.closeBrowsers();
            }
        }
    }

    public void activate() {
        for (ScreenData screen : screens) {
            if (screen.browser == null)
                screen.createBrowser(this, false);
        }
    }

    public void interact(BlockHitResult result, Consumer<Vector2i> func) {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof ScreenBlock) {
            Vector3i pos = new Vector3i(result.getBlockPos());
            BlockSide side = BlockSide.values()[result.getDirection().ordinal()];

            Multiblock.findOrigin(Minecraft.getInstance().level, pos, side, null);

            ScreenData scr = this.getScreen(side);

            if (scr.browser != null) {
                float hitX = ((float) result.getLocation().x) - (float) pos.x;
                float hitY = ((float) result.getLocation().y) - (float) pos.y;
                float hitZ = ((float) result.getLocation().z) - (float) pos.z;
                Vector2i tmp = new Vector2i();

                if (ScreenBlock.hit2pixels(side, result.getBlockPos(), new Vector3i(result.getBlockPos()), scr, hitX, hitY, hitZ, tmp)) {
                    func.accept(tmp);
                }
            }
        }
    }

    public BlockHitResult trace(BlockSide side, Vec3 start, Vec3 look) {
        AABB box = renderBB;
        double pHitDistance = box.distanceToSqr(start) + 2;

        Vec3 vec32 = start.add(look.x * pHitDistance, look.y * pHitDistance, look.z * pHitDistance);

        box = box.move(
                -getBlockPos().getX(),
                -getBlockPos().getY(),
                -getBlockPos().getZ()
        );

        BlockHitResult bhr = AABB.clip(Arrays.asList(box), start, vec32, getBlockPos());
        if (bhr == null || bhr.getType() != HitResult.Type.BLOCK || bhr.getDirection().ordinal() != side.ordinal()) {
            bhr = AABB.clip(Arrays.asList(box), vec32, start, getBlockPos());
            if (bhr == null || bhr.getType() != HitResult.Type.BLOCK || bhr.getDirection().ordinal() != side.ordinal()) {
                return BlockHitResult.miss(
                        vec32,
                        bhr == null ? Direction.getNearest(look.x, look.y, look.z).getOpposite() : bhr.getDirection(),
                        getBlockPos()
                );
            }
        }

        return bhr;
    }

}
