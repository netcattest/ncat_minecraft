/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Ocelot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.data.KeyboardData;
import com.netcattest.ncatminecraft.registry.TileRegistry;
import com.netcattest.ncatminecraft.utilities.serialization.Util;
import com.netcattest.ncatminecraft.utilities.UsbCableService;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;

import java.util.UUID;

public class KeyboardBlockEntity extends AbstractPeripheralBlockEntity {
    private static final String RANDOM_CHARS = "AZERTYUIOPQSDFGHJKLMWXCVBNazertyuiopqsdfghjklmwxcvbn0123456789";
    private UUID usbPortId;

    public KeyboardBlockEntity(BlockPos arg2, BlockState arg3) {
        super(TileRegistry.KEYBOARD.get(), arg2, arg3);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        usbPortId = tag.hasUUID("UsbPortId") ? tag.getUUID("UsbPortId") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (usbPortId != null) tag.putUUID("UsbPortId", usbPortId);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) { load(tag); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) load(packet.getTag());
    }

    @Override
    public boolean connect(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, Vector3i screen, BlockSide side) {
        if (usbPortId != null) UsbCableService.disconnectKeyboard(level, this);
        return super.connect(level, pos, state, screen, side);
    }

    public boolean connectUsb(BlockPos screen, BlockSide side, UUID portId) {
        if (level == null || !super.connect(level, getBlockPos(), getBlockState(), new Vector3i(screen), side)) return false;
        usbPortId = portId;
        syncUsb();
        return true;
    }

    public void disconnectUsb() {
        usbPortId = null;
        screenPos = null;
        screenSide = null;
        syncUsb();
    }

    public void retargetUsb(BlockPos origin, BlockSide side, UUID portId) {
        if (usbPortId == null || !usbPortId.equals(portId)) return;
        screenPos = new Vector3i(origin);
        screenSide = side;
        syncUsb();
    }

    private void syncUsb() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    public boolean usbLinked() { return usbPortId != null; }
    public UUID usbPortId() { return usbPortId; }

    @Override
    public InteractionResult onRightClick(Player player, InteractionHand hand) {
        if(level.isClientSide)
            return InteractionResult.SUCCESS;

        if(!isScreenChunkLoaded()) {
            Util.toast(player, "chunkUnloaded");
            return InteractionResult.SUCCESS;
        }

        if (usbPortId != null && !UsbCableService.valid(this)) {
            Util.toast(player, "notLinked");
            return InteractionResult.SUCCESS;
        }

        ScreenBlockEntity tes = getConnectedScreen();
        if(tes == null) {
            Util.toast(player, "notLinked");
            return InteractionResult.SUCCESS;
        }

        ScreenData scr = tes.getScreen(screenSide);
        if((scr.rightsFor(player) & ScreenRights.INTERACT) == 0) {
            Util.toast(player, "restrictions");
            return InteractionResult.SUCCESS;
        }

        (new KeyboardData(tes, screenSide, getBlockPos())).sendTo((ServerPlayer) player);
        return InteractionResult.SUCCESS;
    }

    public void simulateCat(Entity ent) {
        if(!isScreenChunkLoaded())
            return;
        if (usbPortId != null && !UsbCableService.valid(this)) return;
        
        ScreenBlockEntity tes = getConnectedScreen();

        if(tes != null) {
            ScreenData scr = tes.getScreen(screenSide);
            boolean ok;

            if(ent instanceof Player)
                ok = (scr.rightsFor((Player) ent) & ScreenRights.INTERACT) != 0;
            else
                ok = (scr.otherRights & ScreenRights.INTERACT) != 0;

            if(ok) {
                char rnd = RANDOM_CHARS.charAt((int) (Math.random() * ((double) RANDOM_CHARS.length())));
                tes.type(screenSide, "t" + rnd, getBlockPos());

                Player owner = level.getPlayerByUUID(scr.owner.uuid);
                if(owner instanceof ServerPlayer && ent instanceof Ocelot)
                    NcatMinecraft.INSTANCE.criterionKeyboardCat.trigger(((ServerPlayer) owner).getAdvancements());
            }
        }
    }
}
