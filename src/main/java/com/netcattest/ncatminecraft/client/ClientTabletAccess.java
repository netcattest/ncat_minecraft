package com.netcattest.ncatminecraft.client;

import com.netcattest.ncatminecraft.client.gui.GuiManagedSwitchTablet;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class ClientTabletAccess {
    private ClientTabletAccess() { }

    public static void open(BlockPos tabletPos, BlockPos switchPos) {
        open(tabletPos, switchPos, false);
    }

    public static void open(BlockPos tabletPos, BlockPos switchPos, boolean handheld) {
        Minecraft.getInstance().setScreen(new GuiManagedSwitchTablet(tabletPos, switchPos, handheld));
    }
}
