package com.netcattest.ncatminecraft.block;

import com.netcattest.ncatminecraft.entity.NetworkSwitchBlockEntity;
import com.netcattest.ncatminecraft.item.ItemCableAdjuster;
import com.netcattest.ncatminecraft.item.ItemDataCable;
import com.netcattest.ncatminecraft.item.ItemSerialCable;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class NetworkSwitchBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(0.4, 0, 2.6, 15.6, 6.2, 13.5);

    public NetworkSwitchBlock() {
        super(Properties.of().strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || player.getItemInHand(hand).getItem() instanceof ItemDataCable ||
                player.getItemInHand(hand).getItem() instanceof ItemSerialCable ||
                player.getItemInHand(hand).getItem() instanceof ItemCableAdjuster)
            return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof NetworkSwitchBlockEntity networkSwitch) ||
                !powerButtonAtHit(state, pos, hit))
            return InteractionResult.PASS;
        if (!level.isClientSide) {
            networkSwitch.setPowered(!networkSwitch.powered());
            DataCableService.refreshSwitch(level, networkSwitch);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS,
                    0.4F, networkSwitch.powered() ? 1.25F : 0.8F);
            player.displayClientMessage(Component.translatable(networkSwitch.powered()
                    ? "block.ncat_minecraft.network_switch.on"
                    : "block.ncat_minecraft.network_switch.off"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    protected static boolean powerButtonAtHit(BlockState state, BlockPos pos, BlockHitResult hit) {
        if (hit.getDirection() != state.getValue(FACING)) return false;
        double x = hit.getLocation().x - pos.getX();
        double z = hit.getLocation().z - pos.getZ();
        double modelX = switch (state.getValue(FACING)) {
            case EAST -> z;
            case SOUTH -> 1.0D - x;
            case WEST -> 1.0D - z;
            default -> x;
        };
        double modelY = hit.getLocation().y - pos.getY();
        double[] region = SwitchShapes.POWER_BUTTON;
        return modelX >= region[0] / 16.0D && modelX <= region[2] / 16.0D &&
                modelY >= region[1] / 16.0D && modelY <= region[3] / 16.0D;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkSwitchBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (state.getBlock() != replacement.getBlock() && !level.isClientSide &&
                level.getBlockEntity(pos) instanceof NetworkSwitchBlockEntity networkSwitch)
            DataCableService.detachSwitch(level, networkSwitch);
        super.onRemove(state, level, pos, replacement, moving);
    }
}
