package com.netcattest.ncatminecraft.block;

import com.netcattest.ncatminecraft.NcatMinecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class RubberDuckBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape NORTH = Shapes.or(
            box(3.1, 0, 3.6, 12.9, 5.8, 12.4),
            box(5.0, 5.2, 2.8, 11.0, 11.9, 8.1),
            box(6.1, 6.8, 0.5, 9.9, 8.8, 3.0),
            box(2.1, 2.3, 5.4, 4.0, 5.0, 10.1),
            box(12.0, 2.3, 5.4, 13.9, 5.0, 10.1),
            box(6.5, 3.5, 11.5, 9.5, 7.0, 14.0));
    private static final VoxelShape[] SHAPES = rotations(NORTH);

    public RubberDuckBlock() {
        super(Properties.of().strength(0.35F).sound(SoundType.WOOL).noOcclusion());
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (!canSurvive(defaultBlockState(), context.getLevel(), pos))
            return null;
        FluidState fluid = context.getLevel().getFluidState(pos);
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        return below.isFaceSturdy(level, pos.below(), Direction.UP) || below.getFluidState().is(Fluids.WATER);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED))
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (direction == Direction.DOWN && !canSurvive(state, level, pos))
            return Blocks.AIR.defaultBlockState();
        return state;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND)
            return InteractionResult.PASS;
        if (!level.isClientSide)
            level.playSound(null, pos, NcatMinecraft.INSTANCE.soundDuckSqueak, SoundSource.BLOCKS, 0.8F,
                    0.94F + level.random.nextFloat() * 0.12F);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[switch (state.getValue(FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        }];
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    private static VoxelShape[] rotations(VoxelShape original) {
        VoxelShape[] shapes = new VoxelShape[4];
        shapes[0] = original;
        for (int i = 1; i < shapes.length; i++) {
            VoxelShape source = shapes[i - 1];
            VoxelShape[] next = {Shapes.empty()};
            source.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                    next[0] = Shapes.or(next[0], Shapes.box(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX)));
            shapes[i] = next[0];
        }
        return shapes;
    }
}
