package com.netcattest.ncatminecraft.block;

import com.netcattest.ncatminecraft.entity.GamingChairSeatEntity;
import com.netcattest.ncatminecraft.registry.FurnitureEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class ToiletBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = EnumProperty.create("half", DoubleBlockHalf.class);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty FLUSHING = BooleanProperty.create("flushing");
    private static final int FLUSH_TICKS = 45;
    private static final double SEAT_HEIGHT = 0.58D;

    private static final VoxelShape LOWER_NORTH = Shapes.or(
            box(0.7, 0, 0.5, 15.3, 14.6, 12.4),
            box(0.6, 10.4, 9.2, 15.4, 16, 16));
    private static final VoxelShape UPPER_NORTH = Shapes.or(
            box(0.6, 0, 9.0, 15.4, 7.6, 16),
            box(2.0, 0, 6.6, 14.0, 7.4, 9.8));
    private static final VoxelShape[] LOWER_SHAPES = rotations(LOWER_NORTH);
    private static final VoxelShape[] UPPER_SHAPES = rotations(UPPER_NORTH);

    public ToiletBlock() {
        super(Properties.of().strength(2.0F).sound(SoundType.CALCITE).noOcclusion());
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(OPEN, false)
                .setValue(FLUSHING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, OPEN, FLUSHING);
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
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
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
        if (hand != InteractionHand.MAIN_HAND || player.isPassenger())
            return InteractionResult.PASS;
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockState lowerState = level.getBlockState(lower);
        if (!lowerState.is(this))
            return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (hitsFlush(state, pos, hit))
                flush(lowerState, (ServerLevel) level, lower);
            else if (player.isShiftKeyDown())
                toggleLid(lowerState, level, lower);
            else
                sit(level, lower, lowerState, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER)
            return;
        BlockState current = level.getBlockState(pos);
        if (!current.is(this) || !current.getValue(FLUSHING))
            return;
        BlockState cleared = current.setValue(FLUSHING, false);
        level.setBlock(pos, cleared, Block.UPDATE_ALL);
        BlockPos above = pos.above();
        if (level.getBlockState(above).is(this))
            level.setBlock(above, cleared.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.4F, 1.4F);
        Vec3 splash = bowlCenter(pos, current.getValue(FACING));
        level.sendParticles(ParticleTypes.SPLASH, splash.x, splash.y, splash.z, 10, 0.14, 0.05, 0.1, 0.02);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int rotation = rotationIndex(state.getValue(FACING));
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES[rotation] : UPPER_SHAPES[rotation];
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    private void sit(Level level, BlockPos lower, BlockState state, Player player) {
        AABB area = new AABB(lower).inflate(0.3, 1.2, 0.3);
        var seats = level.getEntitiesOfClass(GamingChairSeatEntity.class, area);
        GamingChairSeatEntity seat = seats.isEmpty() ? FurnitureEntityRegistry.CHAIR_SEAT.get().create(level) : seats.get(0);
        if (seat == null || !seat.getPassengers().isEmpty())
            return;
        Vec3 mount = seatPos(lower, state.getValue(FACING));
        seat.setPos(mount.x, mount.y, mount.z);
        if (seats.isEmpty())
            level.addFreshEntity(seat);
        if (!player.startRiding(seat, true) && seats.isEmpty())
            seat.discard();
    }

    private void toggleLid(BlockState state, Level level, BlockPos lower) {
        boolean open = !state.getValue(OPEN);
        BlockState updated = state.setValue(OPEN, open).setValue(HALF, DoubleBlockHalf.LOWER);
        level.setBlock(lower, updated, Block.UPDATE_ALL);
        BlockPos above = lower.above();
        if (level.getBlockState(above).is(this))
            level.setBlock(above, updated.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        level.playSound(null, lower, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE,
                SoundSource.BLOCKS, 0.45F, open ? 1.4F : 1.2F);
    }

    private void flush(BlockState state, ServerLevel level, BlockPos lower) {
        if (state.getValue(FLUSHING))
            return;
        BlockState flushing = state.setValue(FLUSHING, true).setValue(HALF, DoubleBlockHalf.LOWER);
        level.setBlock(lower, flushing, Block.UPDATE_ALL);
        BlockPos above = lower.above();
        if (level.getBlockState(above).is(this))
            level.setBlock(above, flushing.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        level.scheduleTick(lower, this, FLUSH_TICKS);
        level.playSound(null, lower, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.65F, 1.2F);
        Vec3 splash = bowlCenter(lower, state.getValue(FACING));
        level.sendParticles(ParticleTypes.SPLASH, splash.x, splash.y, splash.z, 18, 0.16, 0.08, 0.12, 0.04);
    }

    private static boolean hitsFlush(BlockState state, BlockPos pos, BlockHitResult hit) {
        double[] local = toModel(state.getValue(FACING), hit.getLocation().x - pos.getX(), hit.getLocation().z - pos.getZ());
        double px = local[0] * 16.0;
        double py = (hit.getLocation().y - pos.getY()) * 16.0;
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER)
            py += 16.0;
        double pz = local[1] * 16.0;
        boolean handle = px <= 2.5 && py >= 15.2 && py <= 19.0 && pz >= 11.4 && pz <= 14.8;
        boolean tank = py >= 14.6 && pz >= 9.3 && px >= 0.5 && px <= 15.5;
        return handle || tank;
    }

    private static Vec3 bowlCenter(BlockPos pos, Direction facing) {
        return seatPos(pos, facing).add(0, 0.18, 0);
    }

    private static Vec3 seatPos(BlockPos pos, Direction facing) {
        double rz = -0.22D;
        double wx;
        double wz;
        switch (facing) {
            case EAST -> {
                wx = -rz;
                wz = 0;
            }
            case SOUTH -> {
                wx = 0;
                wz = -rz;
            }
            case WEST -> {
                wx = rz;
                wz = 0;
            }
            default -> {
                wx = 0;
                wz = rz;
            }
        }
        return new Vec3(pos.getX() + 0.5 + wx, pos.getY() + SEAT_HEIGHT, pos.getZ() + 0.5 + wz);
    }

    private static double[] toModel(Direction facing, double x, double z) {
        return switch (facing) {
            case EAST -> new double[]{z, 1 - x};
            case SOUTH -> new double[]{1 - x, 1 - z};
            case WEST -> new double[]{1 - z, x};
            default -> new double[]{x, z};
        };
    }

    private static int rotationIndex(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
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
