package com.netcattest.ncatminecraft.item;

import com.netcattest.ncatminecraft.block.ManagedTabletBlock;
import com.netcattest.ncatminecraft.entity.ManagedTabletBlockEntity;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ManagedTabletItem extends BlockItem {
    private static final String DOCK_POS = "NcatTabletDock";
    private static final String DOCK_DIMENSION = "NcatTabletDimension";
    private static final String DOCK_TOKEN = "NcatTabletToken";
    private static final double RANGE = 24.0D;

    public ManagedTabletItem(Properties properties) {
        super(BlockRegistry.MANAGED_TABLET.get(), properties.stacksTo(1));
    }

    public static void bind(ItemStack stack, Level level, ManagedTabletBlockEntity tablet) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(DOCK_POS, tablet.getBlockPos().asLong());
        tag.putString(DOCK_DIMENSION, level.dimension().location().toString());
        tag.putUUID(DOCK_TOKEN, tablet.ensureDockToken());
    }

    public static boolean matchesDock(ItemStack stack, Level level, ManagedTabletBlockEntity tablet) {
        CompoundTag tag = stack.getTag();
        return stack.getItem() instanceof ManagedTabletItem && tag != null && tag.contains(DOCK_POS)
                && tag.hasUUID(DOCK_TOKEN)
                && tag.getString(DOCK_DIMENSION).equals(level.dimension().location().toString())
                && BlockPos.of(tag.getLong(DOCK_POS)).equals(tablet.getBlockPos())
                && tag.getUUID(DOCK_TOKEN).equals(tablet.dockToken());
    }

    public static boolean isHeldBy(Player player, Level level, ManagedTabletBlockEntity tablet) {
        if (player == null || level == null || tablet == null ||
                tablet.getBlockState().getValue(ManagedTabletBlock.DOCKED)) return false;
        return matchesDock(player.getMainHandItem(), level, tablet)
                || matchesDock(player.getOffhandItem(), level, tablet);
    }

    @Nullable
    private static BlockPos boundDock(ItemStack stack, Level level) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(DOCK_POS) || !tag.hasUUID(DOCK_TOKEN) ||
                !tag.getString(DOCK_DIMENSION).equals(level.dimension().location().toString())) return null;
        return BlockPos.of(tag.getLong(DOCK_POS));
    }

    private static void clearBinding(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(DOCK_POS);
        tag.remove(DOCK_DIMENSION);
        tag.remove(DOCK_TOKEN);
        if (tag.isEmpty()) stack.setTag(null);
    }

    private static boolean boundToAnotherDimension(ItemStack stack, Level level) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(DOCK_POS) &&
                !tag.getString(DOCK_DIMENSION).equals(level.dimension().location().toString());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return super.useOn(context);
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        if (boundToAnotherDimension(stack, level)) {
            if (!level.isClientSide) player.displayClientMessage(
                    Component.translatable("ncat_minecraft.message.tabletWrongDimension"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos clicked = context.getClickedPos();
        BlockEntity block = level.getBlockEntity(clicked);
        if (block instanceof ManagedTabletBlockEntity tablet &&
                !tablet.getBlockState().getValue(ManagedTabletBlock.DOCKED)) {
            if (!level.isClientSide) {
                if (!matchesDock(stack, level, tablet)) {
                    player.displayClientMessage(Component.translatable("ncat_minecraft.message.tabletWrongDock"), true);
                    return InteractionResult.SUCCESS;
                }
                level.setBlock(clicked, tablet.getBlockState().setValue(ManagedTabletBlock.DOCKED, true), 3);
                stack.shrink(1);
                player.displayClientMessage(Component.translatable("ncat_minecraft.message.tabletDocked"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        BlockPos dock = boundDock(stack, level);
        if (dock != null) {
            if (level.hasChunkAt(dock)) {
                BlockEntity origin = level.getBlockEntity(dock);
                if (origin instanceof ManagedTabletBlockEntity tablet && matchesDock(stack, level, tablet) &&
                        !tablet.getBlockState().getValue(ManagedTabletBlock.DOCKED)) {
                    openHandheld(level, player, dock, tablet);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                clearBinding(stack);
            } else {
                if (!level.isClientSide) player.displayClientMessage(
                        Component.translatable("ncat_minecraft.message.tabletDockUnloaded"), true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (boundToAnotherDimension(stack, level)) {
            if (!level.isClientSide) player.displayClientMessage(
                    Component.translatable("ncat_minecraft.message.tabletWrongDimension"), true);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        BlockPos dock = boundDock(stack, level);
        if (dock != null && level.hasChunkAt(dock) &&
                level.getBlockEntity(dock) instanceof ManagedTabletBlockEntity tablet &&
                matchesDock(stack, level, tablet) && !tablet.getBlockState().getValue(ManagedTabletBlock.DOCKED)) {
            openHandheld(level, player, dock, tablet);
        } else {
            boolean stale = dock != null && level.hasChunkAt(dock);
            if (stale) clearBinding(stack);
            if (!level.isClientSide) player.displayClientMessage(Component.translatable(
                    dock != null && !stale ? "ncat_minecraft.message.tabletDockUnloaded" :
                            "ncat_minecraft.message.tabletPlaceFirst"), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void openHandheld(Level level, Player player, BlockPos dock,
                                     ManagedTabletBlockEntity tablet) {
        double distance = Math.sqrt(player.distanceToSqr(dock.getX() + .5D, dock.getY() + .5D,
                dock.getZ() + .5D));
        if (distance > RANGE) {
            if (!level.isClientSide) player.displayClientMessage(
                    Component.translatable("ncat_minecraft.message.tabletOutOfRange",
                            (int) distance, (int) RANGE).withStyle(ChatFormatting.RED), true);
            return;
        }
        if (level.isClientSide) {
            BlockPos switchPos = tablet.linkedSwitchPos() == null ? dock : tablet.linkedSwitchPos();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.netcattest.ncatminecraft.client.ClientTabletAccess.open(dock, switchPos, true));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.ncat_minecraft.managed_tablet.help")
                .withStyle(ChatFormatting.GRAY));
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(DOCK_POS)) {
            tooltip.add(Component.translatable("item.ncat_minecraft.managed_tablet.unpaired")
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        BlockPos dock = BlockPos.of(tag.getLong(DOCK_POS));
        tooltip.add(Component.translatable("item.ncat_minecraft.managed_tablet.paired")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.ncat_minecraft.managed_tablet.dock",
                dock.getX(), dock.getY(), dock.getZ()).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.ncat_minecraft.managed_tablet.range", (int) RANGE)
                .withStyle(ChatFormatting.DARK_GRAY));
        if (level == null || !level.hasChunkAt(dock))
            return;
        if (level.getBlockEntity(dock) instanceof ManagedTabletBlockEntity tablet
                && tablet.linkedSwitchPos() != null) {
            BlockPos linked = tablet.linkedSwitchPos();
            tooltip.add(Component.translatable("item.ncat_minecraft.managed_tablet.linked",
                    linked.getX(), linked.getY(), linked.getZ()).withStyle(ChatFormatting.GREEN));
        } else {
            tooltip.add(Component.translatable("item.ncat_minecraft.managed_tablet.unlinked")
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
