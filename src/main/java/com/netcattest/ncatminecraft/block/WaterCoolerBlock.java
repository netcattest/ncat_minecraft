package com.netcattest.ncatminecraft.block;

import com.netcattest.ncatminecraft.entity.WaterCoolerBlockEntity;
import com.netcattest.ncatminecraft.item.ItemGallon;
import com.netcattest.ncatminecraft.item.ItemPaperCup;
import com.netcattest.ncatminecraft.registry.ItemRegistry;
import com.netcattest.ncatminecraft.registry.TileRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public final class WaterCoolerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = EnumProperty.create("half", DoubleBlockHalf.class);
    public static final BooleanProperty GALLON = BooleanProperty.create("gallon");

    private static final VoxelShape BODY_NORTH = Shapes.or(
            box(1.5, 0, 4.9, 14.5, 16, 14.5),
            box(1.5, 0, 1.4, 3.8, 16, 4.9),
            box(12.2, 0, 1.4, 14.5, 16, 4.9),
            box(3.8, 0, 1.4, 12.2, 5.5, 4.9),
            box(3.8, 14.5, 1.4, 12.2, 16, 4.9),
            box(4.6, 8.7, 1.4, 7.6, 14.5, 5.0),
            box(8.4, 8.7, 1.4, 11.4, 14.5, 5.0));
    private static final VoxelShape BODY_TOP_NORTH = box(1.5, 0, 1.4, 14.5, 4.2, 14.5);
    private static final VoxelShape GALLON_NORTH = box(3.0, 4.1, 3.0, 13.0, 16, 13.0);
    private static final VoxelShape CUP_BLUE_NORTH = box(4.5, 5.55, 1.6, 7.7, 9.0, 4.8);
    private static final VoxelShape CUP_RED_NORTH = box(8.3, 5.55, 1.6, 11.5, 9.0, 4.8);
    private static final VoxelShape[] BODY = rotations(BODY_NORTH);
    private static final VoxelShape[] BODY_TOP = rotations(BODY_TOP_NORTH);
    private static final VoxelShape[] GALLON_SHAPE = rotations(GALLON_NORTH);
    private static final VoxelShape[] CUP_BLUE_SHAPE = rotations(CUP_BLUE_NORTH);
    private static final VoxelShape[] CUP_RED_SHAPE = rotations(CUP_RED_NORTH);

    public WaterCoolerBlock() {
        super(Properties.of().strength(2.4F).sound(SoundType.METAL).noOcclusion());
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(GALLON, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, GALLON);
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
        if (half == DoubleBlockHalf.UPPER && direction == Direction.DOWN && !canSurvive(state, level, pos))
            return Blocks.AIR.defaultBlockState();
        if (half == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            releaseGallon(level, pos, false);
            dropCup(level, pos, false);
            BlockPos above = pos.above();
            if (level.getBlockState(above).is(this))
                level.removeBlock(above, false);
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos lowerPos = pos.below();
            BlockState lower = level.getBlockState(lowerPos);
            if (!level.isClientSide && lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                releaseGallon(level, lowerPos, player.isCreative());
                dropCup(level, lowerPos, player.isCreative());
                level.setBlock(lowerPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.levelEvent(player, 2001, lowerPos, Block.getId(lower));
                if (!player.isCreative())
                    Block.dropResources(lower, level, lowerPos, null, player, player.getMainHandItem());
            }
        } else {
            releaseGallon(level, pos, player.isCreative());
            dropCup(level, pos, player.isCreative());
            BlockPos above = pos.above();
            if (level.getBlockState(above).is(this))
                level.removeBlock(above, false);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        releaseGallon(level, lower, false);
        dropCup(level, lower, false);
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            if (level.getBlockState(lower).is(this))
                level.destroyBlock(lower, true);
        } else if (level.getBlockState(pos.above()).is(this)) {
            level.removeBlock(pos.above(), false);
        }
        super.onBlockExploded(state, level, pos, explosion);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND)
            return InteractionResult.PASS;
        BlockPos lowerPos = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        BlockState lower = level.getBlockState(lowerPos);
        if (!lower.is(this) || lower.getValue(HALF) != DoubleBlockHalf.LOWER)
            return InteractionResult.PASS;
        BlockEntity entity = level.getBlockEntity(lowerPos);
        if (!(entity instanceof WaterCoolerBlockEntity cooler))
            return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        boolean insert = held.is(ItemRegistry.GALLON.get()) && !cooler.hasGallon();
        boolean refill = held.is(Items.WATER_BUCKET) && cooler.hasGallon() && cooler.water() < ItemGallon.MAX_WATER;
        int tap = state.getValue(HALF) == DoubleBlockHalf.LOWER ? tapSide(lower, lowerPos, hit) : 0;
        boolean takeCup = held.isEmpty() && cooler.hasCup() && !cooler.filling()
                && state.getValue(HALF) == DoubleBlockHalf.LOWER
                && ((tap == 0 && hitCup(lower, lowerPos, hit, cooler.cupSide()))
                || (tap == cooler.cupSide() && cooler.cup() == WaterCoolerBlockEntity.CUP_FULL));
        boolean remove = player.isShiftKeyDown() && held.isEmpty() && cooler.hasGallon() && !takeCup;
        boolean placeCup = held.getItem() instanceof ItemPaperCup cupItem && !cupItem.filled() && !cooler.hasCup();
        boolean fillCup = tap != 0 && tap == cooler.cupSide()
                && cooler.cup() == WaterCoolerBlockEntity.CUP_EMPTY && !cooler.filling();
        boolean cupReady = tap != 0 && tap == cooler.cupSide() && cooler.cup() == WaterCoolerBlockEntity.CUP_FULL;
        boolean toggle = tap != 0 && (!cooler.hasCup() || tap != cooler.cupSide());
        if (!insert && !refill && !remove && !placeCup && !takeCup && !fillCup && !cupReady && !toggle)
            return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (insert)
                insertGallon(level, lowerPos, lower, cooler, player, held);
            else if (refill)
                refill(level, lowerPos, cooler, player, hand);
            else if (remove)
                removeGallon(level, lowerPos, lower, cooler, player);
            else if (placeCup)
                placeCup(level, lowerPos, cooler, player, held, sideFromHit(lower, lowerPos, hit));
            else if (takeCup)
                takeCup(level, lowerPos, cooler, player);
            else if (fillCup)
                fillCup(level, lowerPos, cooler, player, tap > 0);
            else if (cupReady)
                player.displayClientMessage(Component.translatable("block.ncat_minecraft.water_cooler.cup_ready"), true);
            else
                toggleTap(lower, lowerPos, cooler, player, hit);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private InteractionResult insertGallon(Level level, BlockPos pos, BlockState state, WaterCoolerBlockEntity cooler,
                                           Player player, ItemStack held) {
        BlockPos above = pos.above();
        BlockState top = level.getBlockState(above);
        if (!top.is(this) || top.getValue(HALF) != DoubleBlockHalf.UPPER) {
            player.displayClientMessage(Component.translatable("block.ncat_minecraft.water_cooler.blocked"), true);
            return InteractionResult.CONSUME;
        }
        int amount = ItemGallon.waterOf(held);
        BlockState filled = state.setValue(GALLON, true);
        level.setBlock(above, top.setValue(GALLON, true), Block.UPDATE_ALL);
        level.setBlock(pos, filled.setValue(HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof WaterCoolerBlockEntity placed)
            placed.install(amount);
        else
            cooler.install(amount);
        if (!player.isCreative())
            held.shrink(1);
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.7F, 1.1F);
        return InteractionResult.CONSUME;
    }

    private InteractionResult refill(Level level, BlockPos pos, WaterCoolerBlockEntity cooler, Player player, InteractionHand hand) {
        cooler.refill();
        if (!player.isCreative())
            player.setItemInHand(hand, new ItemStack(Items.BUCKET));
        level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 1.0F);
        return InteractionResult.CONSUME;
    }

    private InteractionResult removeGallon(Level level, BlockPos pos, BlockState state, WaterCoolerBlockEntity cooler, Player player) {
        ItemStack gallon = cooler.removeGallon();
        level.setBlock(pos, state.setValue(GALLON, false), Block.UPDATE_ALL);
        BlockPos above = pos.above();
        if (level.getBlockState(above).is(this))
            level.setBlock(above, level.getBlockState(above).setValue(GALLON, false), Block.UPDATE_ALL);
        if (!player.getInventory().add(gallon))
            Block.popResource(level, pos, gallon);
        level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.6F, 0.9F);
        return InteractionResult.CONSUME;
    }

    private void placeCup(Level level, BlockPos pos, WaterCoolerBlockEntity cooler, Player player, ItemStack held, int side) {
        if (!cooler.hasGallon()) {
            player.displayClientMessage(Component.translatable("block.ncat_minecraft.water_cooler.needs_gallon"), true);
            return;
        }
        cooler.placeCup(side);
        if (!player.isCreative())
            held.shrink(1);
        player.displayClientMessage(Component.translatable(side > 0
                ? "block.ncat_minecraft.water_cooler.cup_placed_red"
                : "block.ncat_minecraft.water_cooler.cup_placed_blue"), true);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
    }

    private void takeCup(Level level, BlockPos pos, WaterCoolerBlockEntity cooler, Player player) {
        ItemStack cup = cooler.takeCup();
        if (!cup.isEmpty() && !player.getInventory().add(cup))
            Block.popResource(level, pos, cup);
        player.displayClientMessage(Component.translatable("block.ncat_minecraft.water_cooler.cup_taken"), true);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.35F, 1.1F);
    }

    private void fillCup(Level level, BlockPos pos, WaterCoolerBlockEntity cooler, Player player, boolean hot) {
        if (cooler.water() < WaterCoolerBlockEntity.CUP_COST) {
            player.displayClientMessage(Component.translatable("block.ncat_minecraft.water_cooler.not_enough_water"), true);
            return;
        }
        if (!cooler.startFill(hot))
            return;
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 0.8F);
    }

    private boolean hitCup(BlockState state, BlockPos pos, BlockHitResult hit, int side) {
        double[] local = toModel(state.getValue(FACING), hit.getLocation().x - pos.getX(), hit.getLocation().z - pos.getZ());
        double px = local[0] * 16.0;
        double py = (hit.getLocation().y - pos.getY()) * 16.0;
        double pz = local[1] * 16.0;
        double center = side > 0 ? 9.9 : 6.1;
        return Math.abs(px - center) <= 1.8 && py >= 5.3 && py <= 7.8 && pz <= 5.1;
    }

    private int sideFromHit(BlockState state, BlockPos pos, BlockHitResult hit) {
        double[] local = toModel(state.getValue(FACING), hit.getLocation().x - pos.getX(), hit.getLocation().z - pos.getZ());
        return local[0] >= 0.5D ? 1 : -1;
    }

    private int tapSide(BlockState state, BlockPos pos, BlockHitResult hit) {
        double[] local = toModel(state.getValue(FACING), hit.getLocation().x - pos.getX(), hit.getLocation().z - pos.getZ());
        double px = local[0] * 16.0;
        double py = (hit.getLocation().y - pos.getY()) * 16.0;
        double pz = local[1] * 16.0;
        if (py < 8.1 || py > 14.6 || pz > 5.3)
            return 0;
        if (px >= 8.2 && px <= 11.8)
            return 1;
        if (px >= 4.2 && px <= 7.8)
            return -1;
        return 0;
    }

    private void toggleTap(BlockState state, BlockPos pos, WaterCoolerBlockEntity cooler, Player player, BlockHitResult hit) {
        boolean red = tapSide(state, pos, hit) > 0;
        if (!cooler.hasGallon()) {
            player.displayClientMessage(Component.translatable("block.ncat_minecraft.water_cooler.needs_gallon"), true);
            return;
        }
        if (red)
            cooler.toggleHot();
        else
            cooler.toggleCold();
        boolean open = red ? cooler.hotOpen() : cooler.coldOpen();
        player.level().playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.55F, open ? 0.72F : 0.52F);
        if (open && cooler.water() <= 0)
            player.displayClientMessage(Component.translatable("block.ncat_minecraft.water_cooler.empty"), true);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER)
            return null;
        return new WaterCoolerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER || type != TileRegistry.WATER_COOLER.get())
            return null;
        return level.isClientSide ? cast(WaterCoolerBlockEntity::clientTick) : cast(WaterCoolerBlockEntity::serverTick);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int rotation = switch (state.getValue(FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER)
            return state.getValue(GALLON)
                    ? Shapes.or(BODY_TOP[rotation], GALLON_SHAPE[rotation]) : BODY_TOP[rotation];
        VoxelShape body = BODY[rotation];
        if (level.getBlockEntity(pos) instanceof WaterCoolerBlockEntity cooler && cooler.hasCup())
            return Shapes.or(body, cooler.cupSide() > 0 ? CUP_RED_SHAPE[rotation] : CUP_BLUE_SHAPE[rotation]);
        return body;
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    private static void releaseGallon(LevelAccessor level, BlockPos pos, boolean creative) {
        if (level.isClientSide())
            return;
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof WaterCoolerBlockEntity cooler) || !cooler.hasGallon())
            return;
        ItemStack stack = cooler.removeGallon();
        if (!creative && level instanceof Level real)
            Block.popResource(real, pos, stack);
    }

    private static void dropCup(LevelAccessor level, BlockPos pos, boolean creative) {
        if (level.isClientSide() || creative)
            return;
        if (!(level.getBlockEntity(pos) instanceof WaterCoolerBlockEntity cooler) || !cooler.hasCup())
            return;
        ItemStack stack = cooler.takeCup();
        if (!stack.isEmpty() && level instanceof Level real)
            Block.popResource(real, pos, stack);
    }

    private static double[] toModel(Direction facing, double x, double z) {
        return switch (facing) {
            case EAST -> new double[]{z, 1 - x};
            case SOUTH -> new double[]{1 - x, 1 - z};
            case WEST -> new double[]{1 - z, x};
            default -> new double[]{x, z};
        };
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> BlockEntityTicker<T> cast(BlockEntityTicker<WaterCoolerBlockEntity> ticker) {
        return (BlockEntityTicker<T>) ticker;
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
