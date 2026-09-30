package com.netcattest.ncatminecraft.client.rack;

import com.netcattest.ncatminecraft.entity.RackBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class ClientRackAccess {
    private ClientRackAccess() { }

    public static void open(BlockPos position) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || position == null) return;
        RackBlockEntity rack = RackBlockEntity.at(client.level, position);
        if (rack == null || !rack.canConfigure(client.player)) return;
        client.setScreen(new GuiRack(RackBlockEntity.basePos(client.level, position)));
    }
}
