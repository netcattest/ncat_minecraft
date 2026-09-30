/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;

public interface IPeripheral {
    boolean connect(Level world, BlockPos blockPos, BlockState blockState, Vector3i screenPos, BlockSide screenSide);
}
