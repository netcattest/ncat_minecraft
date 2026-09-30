/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.data;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.netcattest.ncatminecraft.client.gui.GuiServer;
import com.netcattest.ncatminecraft.net.BufferUtils;
import com.netcattest.ncatminecraft.utilities.serialization.NameUUIDPair;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;

public class ServerData extends GuiData {
    public Vector3i pos;
    public NameUUIDPair owner;

    public ServerData() {
    }

    public ServerData(BlockPos bp, NameUUIDPair owner) {
        pos = new Vector3i(bp);
        this.owner = owner;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public Screen createGui(Screen old, Level world) {
        return new GuiServer(pos, owner);
    }

    @Override
    public String getName() {
        return "Server";
    }

    @Override
    public void serialize(FriendlyByteBuf buf) {
        BufferUtils.writeVec3i(buf, pos);
        owner.writeTo(buf);
    }

    @Override
    public void deserialize(FriendlyByteBuf buf) {
        pos = BufferUtils.readVec3i(buf);
        owner = new NameUUIDPair(buf);
    }
}
