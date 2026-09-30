package com.netcattest.ncatminecraft.block;

import com.netcattest.ncatminecraft.entity.ManagedSwitchBlockEntity;
import com.netcattest.ncatminecraft.item.ItemSerialCable;
import com.netcattest.ncatminecraft.utilities.serialization.Util;
import com.netcattest.ncatminecraft.utilities.SerialCableService;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class ManagedSwitchBlock extends NetworkSwitchBlock {
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (player.getItemInHand(hand).getItem() instanceof ItemSerialCable) return InteractionResult.PASS;
        if (hand == InteractionHand.MAIN_HAND && powerButtonAtHit(state, pos, hit) &&
                level.getBlockEntity(pos) instanceof ManagedSwitchBlockEntity managedSwitch &&
                !managedSwitch.canConfigure(player)) {
            if (!level.isClientSide) Util.toast(player, "restrictions");
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManagedSwitchBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player &&
                level.getBlockEntity(pos) instanceof ManagedSwitchBlockEntity managedSwitch)
            managedSwitch.setOwner(player.getUUID());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (state.getBlock() != replacement.getBlock()) SerialCableService.detachOnRemoval(level, pos);
        super.onRemove(state, level, pos, replacement, moving);
    }
}
