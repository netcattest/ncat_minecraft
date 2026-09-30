package com.netcattest.ncatminecraft.entity;

import com.netcattest.ncatminecraft.registry.TileRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class DigitalClockBlockEntity extends BlockEntity {
    public DigitalClockBlockEntity(BlockPos pos, BlockState state) {
        super(TileRegistry.DIGITAL_CLOCK.get(), pos, state);
    }
}
