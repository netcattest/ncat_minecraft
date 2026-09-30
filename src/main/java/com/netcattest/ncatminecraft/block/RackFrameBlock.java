package com.netcattest.ncatminecraft.block;

import com.netcattest.ncatminecraft.client.rack.ClientRackAccess;
import com.netcattest.ncatminecraft.entity.RackBlockEntity;
import com.netcattest.ncatminecraft.entity.RackModule;
import com.netcattest.ncatminecraft.item.RackModuleItem;
import com.netcattest.ncatminecraft.item.ItemDataCable;
import com.netcattest.ncatminecraft.item.ItemCableAdjuster;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

public final class RackFrameBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final ThreadLocal<Boolean> REMOVING_STACK = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FRAME = Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(0, 14, 0, 16, 16, 16),
            Block.box(0, 0, 0, 2, 16, 16),
            Block.box(14, 0, 0, 16, 16, 16),
            Block.box(2, 0, 14, 14, 16, 16));

    public enum Role { BASE, EXTENSION, STANDALONE }

    public static final int MAX_SECTIONS = 6;

    private final int units;
    private final Role role;

    public RackFrameBlock(int units, Role role) {
        super(Properties.of().strength(4F, 8F).sound(SoundType.METAL).noOcclusion());
        this.units = units;
        this.role = role;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public int units() { return units; }

    public Role role() { return role; }

    public static int unitsOf(BlockState state) {
        return state.getBlock() instanceof RackFrameBlock frame ? frame.units : 0;
    }

    public static boolean isBase(BlockState state) {
        return state.getBlock() instanceof RackFrameBlock frame && frame.role == Role.BASE;
    }

    public static boolean isStandalone(BlockState state) {
        return state.getBlock() instanceof RackFrameBlock frame && frame.role == Role.STANDALONE;
    }

    public static boolean isTwelveUnit(BlockState state) {
        return isBase(state);
    }

    public static boolean isEighteenUnit(BlockState state) {
        return state.getBlock() instanceof RackFrameBlock frame && frame.role == Role.EXTENSION;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState below = level.getBlockState(pos.below());
        Direction facing = context.getHorizontalDirection().getOpposite();
        if (role == Role.STANDALONE) {
            if (below.getBlock() instanceof RackFrameBlock
                    || level.getBlockState(pos.above()).getBlock() instanceof RackFrameBlock) return null;
            return defaultBlockState().setValue(FACING, facing);
        }
        if (!(below.getBlock() instanceof RackFrameBlock)) {
            if (role == Role.EXTENSION) return null;
            return defaultBlockState().setValue(FACING, facing);
        }
        if (isStandalone(below)) return null;
        int stacked = 0;
        for (int depth = 1; depth <= MAX_SECTIONS; depth++) {
            BlockState below_n = level.getBlockState(pos.below(depth));
            if (!(below_n.getBlock() instanceof RackFrameBlock) || isStandalone(below_n)) break;
            if (below_n.getValue(FACING) != below.getValue(FACING)) return null;
            stacked++;
        }
        if (stacked >= MAX_SECTIONS || !isBase(level.getBlockState(pos.below(stacked)))) return null;
        if (role == Role.BASE && stacked >= 2) return null;
        facing = below.getValue(FACING);
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) {
            RackBlockEntity rack = RackBlockEntity.at(level, pos);
            if (rack != null) rack.setOwner(player.getUUID());
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof ItemCableAdjuster) return InteractionResult.PASS;
        boolean guiTool = held.getItem() instanceof RackModuleItem || held.getItem() instanceof ItemDataCable;
        if (guiTool && !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!held.isEmpty() && !guiTool) return InteractionResult.PASS;
        RackBlockEntity rack = RackBlockEntity.at(level, pos);
        if (rack == null) return InteractionResult.PASS;
        if (held.isEmpty() && !player.isShiftKeyDown() && rack.doorOpen()) {
            RackModule frontButton = frontPowerButton(rack, pos, hit);
            if (frontButton != null) {
                if (!level.isClientSide && rack.canConfigure(player))
                    rack.setModulePowered(frontButton.id(), !frontButton.powered(), player);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        if (player.isShiftKeyDown() && !guiTool) {
            if (!level.isClientSide && rack.canConfigure(player)) rack.setDoorOpen(!rack.doorOpen());
        } else if (level.isClientSide) {
            BlockPos base = rack.getBlockPos();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRackAccess.open(base));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    private RackModule frontPowerButton(RackBlockEntity rack, BlockPos sectionPos, BlockHitResult hit) {
        if (hit.getDirection() != rack.getFacing()) return null;
        Direction right = rack.getFacing().getClockWise();
        double dx = hit.getLocation().x - sectionPos.getX() - .5D;
        double dz = hit.getLocation().z - sectionPos.getZ() - .5D;
        double localX = .5D + dx * right.getStepX() + dz * right.getStepZ();
        if (localX < RackLayout.STATUS_LEFT || localX > RackLayout.STATUS_RIGHT) return null;
        int section = sectionPos.getY() - rack.getBlockPos().getY();
        if (section < 0 || section >= rack.heightBlocks()) return null;
        int start = rack.firstUnitOfSection(section);
        int count = rack.sectionUnits(section);
        if (count <= 0) return null;
        double localY = Math.max(0D, Math.min(.9999D, hit.getLocation().y - sectionPos.getY()));
        return rack.moduleAtU(start + Math.min(count - 1, (int) (localY * count)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new RackBlockEntity(pos, state); }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (level instanceof Level world) {
            RackBlockEntity rack = RackBlockEntity.at(world, pos);
            if (rack != null && rack.doorOpen()) return FRAME;
        }
        return Shapes.block();
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) { return Shapes.empty(); }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (state.getBlock() != replacement.getBlock() && !level.isClientSide && !REMOVING_STACK.get()) {
            RackBlockEntity rack = RackBlockEntity.at(level, pos);
            if (rack != null) {
                BlockPos base = rack.getBlockPos();
                int height = rack.heightBlocks();
                REMOVING_STACK.set(true);
                try {
                    rack.dropAllModules();
                    for (int i = height - 1; i >= 0; i--) {
                        BlockPos section = base.above(i);
                        if (!section.equals(pos) && level.getBlockState(section).getBlock() instanceof RackFrameBlock)
                            level.destroyBlock(section, true);
                    }
                } finally {
                    REMOVING_STACK.set(false);
                }
            }
        }
        super.onRemove(state, level, pos, replacement, moving);
    }
}
