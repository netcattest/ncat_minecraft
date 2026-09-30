package com.netcattest.ncatminecraft.block;

import com.netcattest.ncatminecraft.entity.DigitalClockBlockEntity;
import com.netcattest.ncatminecraft.registry.TileRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class DigitalClockBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape BODY_NORTH = Shapes.or(
            box(1.1, 1.0, 3.6, 14.9, 8.7, 12.4),
            box(1.1, 1.4, 2.8, 14.9, 8.3, 4.0),
            box(3.6, 8.4, 6.1, 12.4, 10.1, 8.8));
    private static final VoxelShape[] SHAPES = rotations(BODY_NORTH);
    private final boolean realTime;

    public DigitalClockBlock(boolean realTime) {
        super(Properties.of().strength(1.4F).sound(realTime ? SoundType.CALCITE : SoundType.STONE).noOcclusion().lightLevel(state -> 5));
        this.realTime = realTime;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public boolean realTime() {
        return realTime;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (!context.getLevel().getBlockState(pos.below()).isFaceSturdy(context.getLevel(), pos.below(), Direction.UP))
            return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos))
            return Blocks.AIR.defaultBlockState();
        return state;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DigitalClockBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return null;
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
