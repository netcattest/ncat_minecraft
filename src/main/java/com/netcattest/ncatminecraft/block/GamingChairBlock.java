package com.netcattest.ncatminecraft.block;

import com.netcattest.ncatminecraft.entity.GamingChairSeatEntity;
import com.netcattest.ncatminecraft.registry.FurnitureEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class GamingChairBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = EnumProperty.create("half", DoubleBlockHalf.class);
    private static final double SEAT_MOUNT_HEIGHT = 0.49D;

    private static final VoxelShape LOWER_NORTH = Shapes.or(
            box(1, 0, 7, 15, 2, 9), box(7, 0, 1, 9, 2, 15),
            box(6, 1, 6, 10, 9, 10), box(2, 9, 2, 14, 12, 14),
            box(1, 11, 4, 3, 15, 12), box(13, 11, 4, 15, 15, 12),
            box(2, 10, 13, 14, 16, 16));
    private static final VoxelShape UPPER_NORTH = Shapes.or(
            box(2, 0, 12, 14, 15, 16),
            box(1, 0, 10, 3, 14, 15), box(13, 0, 10, 15, 14, 15),
            box(4, 9, 8, 12, 14, 13));
    private static final VoxelShape[] LOWER_SHAPES = rotations(LOWER_NORTH);
    private static final VoxelShape[] UPPER_SHAPES = rotations(UPPER_NORTH);

    public GamingChairBlock() {
        super(Properties.of().strength(2.2F).noOcclusion());
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (pos.getY() >= context.getLevel().getMaxBuildHeight() - 1 ||
                !context.getLevel().getBlockState(pos.above()).canBeReplaced(context) ||
                !context.getLevel().getBlockState(pos.below()).isFaceSturdy(context.getLevel(), pos.below(), Direction.UP))
            return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER;
        }
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN))
            return neighbor.is(this) && neighbor.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        return half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !canSurvive(state, level, pos)
                ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lowerPos = pos.below();
            BlockState lower = level.getBlockState(lowerPos);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.levelEvent(player, 2001, lowerPos, Block.getId(lower));
                if (!player.isCreative())
                    Block.dropResources(lower, level, lowerPos, null, player, player.getMainHandItem());
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand == InteractionHand.OFF_HAND || player.isShiftKeyDown() || player.isPassenger())
            return InteractionResult.PASS;
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (!level.isClientSide) {
            AABB area = new AABB(lower).inflate(0.2, 0.8, 0.2);
            var seats = level.getEntitiesOfClass(GamingChairSeatEntity.class, area);
            GamingChairSeatEntity seat = seats.isEmpty() ? FurnitureEntityRegistry.CHAIR_SEAT.get().create(level) : seats.get(0);
            if (seat != null && seat.getPassengers().isEmpty()) {
                seat.setPos(lower.getX() + 0.5, lower.getY() + SEAT_MOUNT_HEIGHT, lower.getZ() + 0.5);
                if (seats.isEmpty()) {
                    level.addFreshEntity(seat);
                }
                if (!player.startRiding(seat, true) && seats.isEmpty())
                    seat.discard();
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int rotation = switch (state.getValue(FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES[rotation] : UPPER_SHAPES[rotation];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
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
