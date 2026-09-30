/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.client.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.netcattest.ncatminecraft.client.gui.controls.Button;
import com.netcattest.ncatminecraft.client.gui.controls.TextField;
import com.netcattest.ncatminecraft.client.gui.loading.FillControl;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;

import javax.annotation.Nullable;

public class GuiRedstoneCtrl extends WDScreen {

    private ResourceLocation dimension;
    private Vector3i pos;
    private String risingEdgeURL;
    private String fallingEdgeURL;

    @FillControl
    private TextField tfRisingEdge;

    @FillControl
    private TextField tfFallingEdge;

    @FillControl
    private Button btnOk;

    public GuiRedstoneCtrl(Component component, ResourceLocation d, Vector3i p, String r, String f) {
        super(component);
        dimension = d;
        pos = p;
        risingEdgeURL = r;
        fallingEdgeURL = f;
    }

    @Override
    public void init() {
        super.init();
        loadFrom(new ResourceLocation("ncat_minecraft", "gui/redstonectrl.json"));
        tfRisingEdge.setText(risingEdgeURL);
        tfFallingEdge.setText(fallingEdgeURL);
    }


    @Override
    public boolean isForBlock(BlockPos bp, BlockSide side) {
        return pos.equalsBlockPos(bp);
    }

    @Nullable
    @Override
    public String getWikiPageName() {
        return "Redstone_Controller";
    }

}
