/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.core;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.netcattest.ncatminecraft.entity.KeyboardBlockEntity;
import com.netcattest.ncatminecraft.entity.RemoteControlBlockEntity;
import com.netcattest.ncatminecraft.entity.RedstoneControlBlockEntity;
import com.netcattest.ncatminecraft.entity.ServerBlockEntity;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public enum DefaultPeripheral implements StringRepresentable {
    KEYBOARD("keyboard", "Keyboard", KeyboardBlockEntity::new, BlockRegistry.KEYBOARD_BLOCK),
    REMOTE_CONTROLLER("remotectrl", "Remote_Controller", RemoteControlBlockEntity::new , BlockRegistry.REMOTE_CONTROLLER_BLOCK),
    REDSTONE_CONTROLLER("redstonectrl", "Redstone_Controller", RedstoneControlBlockEntity::new , BlockRegistry.REDSTONE_CONTROL_BLOCK),
    SERVER("server", "Server", ServerBlockEntity::new, BlockRegistry.SERVER_BLOCK);

    private final String name;
    private final String wikiName;
    private final BlockEntityType.BlockEntitySupplier<? extends BlockEntity> teClass;
    private final Supplier<? extends Block> bClass;

    DefaultPeripheral(String name, String wname, BlockEntityType.BlockEntitySupplier<? extends BlockEntity> factory, Supplier<? extends Block> supplier) {
        this.name = name;
        wikiName = wname;
        teClass = factory;
        bClass = supplier;
    }

    public Supplier<? extends Block> getBlockClass() {
        return bClass;
    }

    public static DefaultPeripheral fromMetadata(int meta) {
        if((meta & 3) == 3)
            return values()[(((meta >> 2) & 3) | 4) - 1];
        else
            return values()[meta & 3];
    }

    public BlockEntityType.BlockEntitySupplier<? extends BlockEntity> getTEClass() {
        return teClass;
    }

    public boolean hasFacing() {
        return ordinal() < 3;
    }

    public int toMetadata(int facing) {
        int ret = ordinal();
        if(ret < 3)
            ret |= facing << 2;
        else
            ret = (((ret + 1) & 3) << 2) | 3;

        return ret;
    }

    @Override
    public @NotNull String getSerializedName() {
        return "default_peripheral_" + name;
    }
}
