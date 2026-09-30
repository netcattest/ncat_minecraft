/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.data.RedstoneCtrlData;
import com.netcattest.ncatminecraft.registry.TileRegistry;
import com.netcattest.ncatminecraft.utilities.serialization.Util;

import java.io.IOException;

public class RedstoneControlBlockEntity extends AbstractPeripheralBlockEntity {
    private String risingEdgeURL = "";
    private String fallingEdgeURL = "";
    private boolean state = false;

    public RedstoneControlBlockEntity(BlockPos arg2, BlockState arg3) {
        super(TileRegistry.REDSTONE_CONTROLLER.get(), arg2, arg3);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);

        risingEdgeURL = tag.getString("RisingEdgeURL");
        fallingEdgeURL = tag.getString("FallingEdgeURL");
        state = tag.getBoolean("Powered");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("RisingEdgeURL", risingEdgeURL);
        tag.putString("FallingEdgeURL", fallingEdgeURL);
        tag.putBoolean("Powered", state);
    }

    @Override
    public InteractionResult onRightClick(Player player, InteractionHand hand) {
        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        if (!isScreenChunkLoaded()) {
            Util.toast(player, "chunkUnloaded");
            return InteractionResult.SUCCESS;
        }

        ScreenBlockEntity tes = getConnectedScreen();
        if (tes == null) {
            Util.toast(player, "notLinked");
            return InteractionResult.SUCCESS;
        }

        ScreenData scr = tes.getScreen(screenSide);
        if((scr.rightsFor(player) & ScreenRights.CHANGE_URL) == 0) {
            Util.toast(player, "restrictions");
            return InteractionResult.SUCCESS;
        }

        (new RedstoneCtrlData(level.dimension().location(), getBlockPos(), risingEdgeURL, fallingEdgeURL)).sendTo((ServerPlayer) player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onNeighborChange(Block neighborType, BlockPos neighborPos) {
        boolean hasPower = (level.hasNeighborSignal(getBlockPos()) || level.hasNeighborSignal(getBlockPos().above()));

        if (hasPower != state) {
            state = hasPower;

            if (state)
                changeURL(risingEdgeURL);
            else
                changeURL(fallingEdgeURL);
        }
    }

    public void setURLs(String r, String f) {
        risingEdgeURL = r.trim();
        fallingEdgeURL = f.trim();
        setChanged();
    }

    private void changeURL(String url) {
        if (level.isClientSide || url.isEmpty())
            return;

        if (isScreenChunkLoaded()) {
            ScreenBlockEntity tes = getConnectedScreen();

            if (tes != null)
                try {
                    tes.setScreenURL(screenSide, url);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
        }
    }
}
