package com.netcattest.ncatminecraft.block;

import com.netcattest.ncatminecraft.entity.ManagedTabletBlockEntity;
import com.netcattest.ncatminecraft.entity.ManagedSwitchBlockEntity;
import com.netcattest.ncatminecraft.item.ManagedTabletItem;
import com.netcattest.ncatminecraft.item.ItemSerialCable;
import com.netcattest.ncatminecraft.registry.ItemRegistry;
import com.netcattest.ncatminecraft.utilities.SerialCableService;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public final class ManagedTabletBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final BooleanProperty DOCKED = BooleanProperty.create("docked");
    private static final VoxelShape DOCKED_SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 7.0D, 15.0D);
    private static final VoxelShape EMPTY_DOCK_SHAPE = Block.box(1.0D, 0.0D, 2.0D, 15.0D, 4.5D, 14.0D);

    public ManagedTabletBlock() {
        super(Properties.of().strength(1.8F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DOCKED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DOCKED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManagedTabletBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.getItemInHand(hand).getItem() instanceof ItemSerialCable)
            return InteractionResult.PASS;
        if (player.getItemInHand(hand).getItem() instanceof ManagedTabletItem) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof ManagedTabletBlockEntity tablet))
            return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (!player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
            if (state.getValue(DOCKED) && !level.isClientSide) {
                if (tablet.linkedSwitchPos() != null) {
                    ManagedSwitchBlockEntity networkSwitch = SerialCableService.connectedSwitch(level, tablet);
                    if (networkSwitch == null ? !player.hasPermissions(2) :
                            !networkSwitch.canConfigure(player)) {
                        player.displayClientMessage(Component.translatable(
                                "ncat_minecraft.message.tabletLiftDenied"), true);
                        return InteractionResult.SUCCESS;
                    }
                }
                tablet.setDockToken(UUID.randomUUID());
                ItemStack handheld = new ItemStack(ItemRegistry.MANAGED_TABLET.get());
                ManagedTabletItem.bind(handheld, level, tablet);
                level.setBlock(pos, state.setValue(DOCKED, false), Block.UPDATE_ALL);
                if (!player.getInventory().add(handheld)) player.drop(handheld, false);
                player.displayClientMessage(Component.translatable("ncat_minecraft.message.tabletLifted"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!state.getValue(DOCKED)) {
            if (!level.isClientSide) player.displayClientMessage(
                    Component.translatable("ncat_minecraft.message.tabletUndocked"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            BlockPos switchPos = tablet.linkedSwitchPos() == null ? pos : tablet.linkedSwitchPos();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.netcattest.ncatminecraft.client.ClientTabletAccess.open(pos, switchPos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (state.getBlock() != replacement.getBlock()) SerialCableService.detachOnRemoval(level, pos);
        super.onRemove(state, level, pos, replacement, moving);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(DOCKED) ? DOCKED_SHAPE : EMPTY_DOCK_SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
