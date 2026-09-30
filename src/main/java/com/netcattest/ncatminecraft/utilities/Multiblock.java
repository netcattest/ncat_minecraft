/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.utilities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import com.netcattest.ncatminecraft.block.ScreenBlock;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;

public abstract class Multiblock {
    public enum OverrideAction {
        NONE,
        SIMULATE,
        IGNORE
    }

    public static class BlockOverride {
        final Vector3i pos;
        final OverrideAction action;

        public BlockOverride(Vector3i p, OverrideAction act) {
            pos = p;
            action = act;
        }

        public boolean apply(Vector3i bp, boolean originalResult) {
            if (action == OverrideAction.NONE || !bp.equals(pos))
                return originalResult;
            else if (action == OverrideAction.SIMULATE)
                return true;
            else
                return false;
        }

    }

    public static final BlockOverride NULL_OVERRIDE = new BlockOverride(null, OverrideAction.NONE);

    public static void findOrigin(LevelAccessor world, Vector3i pos, BlockSide side, BlockOverride override) {
        if (override == null)
            override = NULL_OVERRIDE;

        Block screenBlock = world.getBlockState(pos.toBlock()).getBlock();
        if (!(screenBlock instanceof ScreenBlock))
            return;
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();

        do {
            pos.add(side.left);
            pos.toBlock(bp);
        } while (override.apply(pos, world.getBlockState(bp).getBlock() == screenBlock));

        pos.add(side.right);

        do {
            pos.add(side.down);
            pos.toBlock(bp);
        } while (override.apply(pos, world.getBlockState(bp).getBlock() == screenBlock));

        pos.add(side.up);
    }

    public static Vector2i measure(LevelAccessor world, Vector3i origin, BlockSide side) {
        Block screenBlock = world.getBlockState(origin.toBlock()).getBlock();
        Vector2i ret = new Vector2i();
        if (!(screenBlock instanceof ScreenBlock))
            return ret;
        Vector3i pos = origin.clone();

        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        pos.toBlock(bp);

        do {
            pos.add(side.up);
            pos.toBlock(bp);
            ret.y++;
        } while (world.getBlockState(bp).getBlock() == screenBlock);

        pos.add(side.down);

        do {
            pos.add(side.right);
            pos.toBlock(bp);
            ret.x++;
        } while (world.getBlockState(bp).getBlock() == screenBlock);

        return ret;
    }

    public static Vector3i check(LevelAccessor world, Vector3i origin, Vector2i size, BlockSide side) {
        Block screenBlock = world.getBlockState(origin.toBlock()).getBlock();
        if (!(screenBlock instanceof ScreenBlock))
            return origin;
        Vector3i pos = origin.clone();
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();

        for (int y = 0; y < size.y; y++) {
            for (int x = 0; x < size.x; x++) {
                pos.toBlock(bp);
                if (!(world.getBlockState(bp).getBlock() == screenBlock))
                    return pos;

                pos.add(side.forward);
                pos.toBlock(bp);
                if (world.getBlockState(bp).getBlock() == screenBlock)
                    return pos;

                pos.addMul(side.backward, 2);
                pos.toBlock(bp);
                if (world.getBlockState(bp).getBlock() == screenBlock)
                    return pos;

                pos.add(side.forward);
                pos.add(side.right);
            }

            pos.addMul(side.left, size.x);
            pos.add(side.up);
        }

        pos.set(origin);
        pos.add(side.left);

        for (int y = 0; y < size.y; y++) {
            pos.toBlock(bp);
            if (world.getBlockState(bp).getBlock() == screenBlock)
                return pos;

            pos.add(side.up);
        }

        pos.set(origin);
        pos.addMul(side.right, size.x);

        for (int y = 0; y < size.y; y++) {
            pos.toBlock(bp);
            if (world.getBlockState(bp).getBlock() == screenBlock)
                return pos;

            pos.add(side.up);
        }

        pos.set(origin);
        pos.add(side.down);

        for (int x = 0; x < size.x; x++) {
            pos.toBlock(bp);
            if (world.getBlockState(bp).getBlock() == screenBlock)
                return pos;

            pos.add(side.right);
        }

        pos.set(origin);
        pos.addMul(side.up, size.y);

        for (int x = 0; x < size.x; x++) {
            pos.toBlock(bp);
            if (world.getBlockState(bp).getBlock() == screenBlock)
                return pos;

            pos.add(side.right);
        }

        return null;
    }
}
