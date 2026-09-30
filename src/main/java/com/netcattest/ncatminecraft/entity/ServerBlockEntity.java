/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.data.ServerData;
import com.netcattest.ncatminecraft.registry.TileRegistry;
import com.netcattest.ncatminecraft.utilities.serialization.NameUUIDPair;
import com.netcattest.ncatminecraft.utilities.serialization.Util;

public class ServerBlockEntity extends BlockEntity {
    private NameUUIDPair owner;

    public ServerBlockEntity(BlockPos arg2, BlockState arg3) {
        super(TileRegistry.SERVER.get(), arg2, arg3);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = Util.readOwnerFromNBT(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        Util.writeOwnerToNBT(tag, owner);
    }

    public void setOwner(Player ep) {
        owner = new NameUUIDPair(ep.getGameProfile());
        setChanged();
    }

    public void onPlayerRightClick(Player ply) {
        if (level.isClientSide)
            return;

        if (NcatMinecraft.INSTANCE.miniservPort == 0)
            Util.toast(ply, "noMiniserv");
        else if (owner != null && ply instanceof ServerPlayer)
            (new ServerData(getBlockPos(), owner)).sendTo((ServerPlayer) ply);
    }
}
