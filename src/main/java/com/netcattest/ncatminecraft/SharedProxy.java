/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft;

import com.cinemamod.mcef.MCEF;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.netcattest.ncatminecraft.core.HasAdvancement;
import com.netcattest.ncatminecraft.core.JSServerRequest;
import com.netcattest.ncatminecraft.data.GuiData;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.utilities.*;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.data.Rotation;
import com.netcattest.ncatminecraft.utilities.serialization.NameUUIDPair;
import org.joml.Vector3d;

import javax.annotation.Nonnull;
import java.util.UUID;

public class SharedProxy {
    public void preInit() {
    }

    public void init() {
        MCEF.scheduleForInit((cef) -> onCefInit());
    }

    public void postInit() {
    }

    public void onCefInit() {
    }

    @Deprecated(forRemoval = true)
    public Level getWorld(ResourceKey<Level> dim) {
        return getServer().getLevel(dim);
    }

    public BlockGetter getWorld(NetworkEvent.Context context) {
        if (context.getSender() != null) return context.getSender().level();
        return null;
    }

    public void enqueue(Runnable r) {
        ServerLifecycleHooks.getCurrentServer().addTickable(r);
    }

    public void displayGui(GuiData data) {
        Log.error("Called SharedProxy.displayGui() on server side...");
    }

    public void trackScreen(ScreenBlockEntity tes, boolean track) {
    }

    public void interactSshScreen(BlockPos position, BlockHitResult hit, boolean keyboard) {
    }

    public void onAutocompleteResult(NameUUIDPair pairs[]) {
    }

    public GameProfile[] getOnlineGameProfiles() {
        return ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().stream().map(Player::getGameProfile).toArray(GameProfile[]::new);
    }

    public void screenUpdateResolutionInGui(Vector3i pos, BlockSide side, Vector2i res) {
    }

    public void screenUpdateRotationInGui(Vector3i pos, BlockSide side, Rotation rot) {
    }

    public void screenUpdateAutoVolumeInGui(Vector3i pos, BlockSide side, boolean av) {
    }

    public void displaySetPadURLGui(ItemStack is, String padURL) {
        Log.error("Called SharedProxy.displaySetPadURLGui() on server side...");
    }

    public void openMinePadGui(UUID padId) {
        Log.error("Called SharedProxy.openMinePadGui() on server side...");
    }

    public void handleJSResponseSuccess(int reqId, JSServerRequest type, byte[] data) {
        Log.error("Called SharedProxy.handleJSResponseSuccess() on server side...");
    }

    public void handleJSResponseError(int reqId, JSServerRequest type, int errCode, String err) {
        Log.error("Called SharedProxy.handleJSResponseError() on server side...");
    }

    @Nonnull
    public HasAdvancement hasClientPlayerAdvancement(@Nonnull ResourceLocation rl) {
        return HasAdvancement.DONT_KNOW;
    }

    public MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }

    public void setMiniservClientPort(int port) {
    }

    public void startMiniservClient() {
    }

    public boolean isMiniservDisabled() {
        return false;
    }

    public void closeGui(BlockPos bp, BlockSide bs) {
    }

    public void renderRecipes() {
    }

    public boolean isShiftDown() {
        return false;
    }

    public double distanceTo(ScreenBlockEntity tes, Vec3 position) {
        double dist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < tes.screenCount(); i++) {
            ScreenData scrn = tes.getScreen(i);

            Vector3d pos = new Vector3d(
                    scrn.side.right.x * scrn.size.x / 2d + scrn.size.y * scrn.side.up.x / 2d,
                    scrn.side.right.y * scrn.size.x / 2d + scrn.size.y * scrn.side.up.y / 2d,
                    scrn.side.right.z * scrn.size.x / 2d + scrn.size.y * scrn.side.up.z / 2d
            ).add(tes.getBlockPos().getX(), tes.getBlockPos().getY(), tes.getBlockPos().getZ());

            double dist2 = position.distanceToSqr(pos.x, pos.y, pos.z);
            dist = Math.min(dist, dist2);
        }
        return dist;
    }
}
