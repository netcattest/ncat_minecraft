/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.data;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.netcattest.ncatminecraft.client.gui.GuiRedstoneCtrl;
import com.netcattest.ncatminecraft.net.BufferUtils;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;

public class RedstoneCtrlData extends GuiData {
    public ResourceLocation dimension;
    public Vector3i pos;
    public String risingEdgeURL;
    public String fallingEdgeURL;

    public RedstoneCtrlData() {
        super();
    }

    public RedstoneCtrlData(ResourceLocation d, BlockPos p, String r, String f) {
        dimension = d;
        pos = new Vector3i(p);
        risingEdgeURL = r;
        fallingEdgeURL = f;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public Screen createGui(Screen old, Level world) {
        return new GuiRedstoneCtrl(old.getTitle(), dimension, pos, risingEdgeURL, fallingEdgeURL);
    }

    @Override
    public String getName() {
        return "RedstoneCtrl";
    }

    @Override
    public void serialize(FriendlyByteBuf buf) {
        buf.writeUtf(dimension.toString());
        BufferUtils.writeVec3i(buf, pos);
        buf.writeUtf(risingEdgeURL);
        buf.writeUtf(fallingEdgeURL);
    }

    @Override
    public void deserialize(FriendlyByteBuf buf) {
        dimension = new ResourceLocation(buf.readUtf());
        pos = BufferUtils.readVec3i(buf);
        risingEdgeURL = buf.readUtf();
        fallingEdgeURL = buf.readUtf();
    }
}
