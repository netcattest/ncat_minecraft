package com.netcattest.ncatminecraft.item;

import com.netcattest.ncatminecraft.entity.ManagedSwitchBlockEntity;
import com.netcattest.ncatminecraft.entity.ManagedTabletBlockEntity;
import com.netcattest.ncatminecraft.utilities.SerialCableService;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.net.client_bound.S2CMessageSerialAnimation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public final class ItemSerialCable extends Item {
    private static final String START_POS = "SerialStartPos";
    private static final String START_TABLET = "SerialStartTablet";
    private static final String START_DIMENSION = "SerialStartDimension";

    public ItemSerialCable(Properties properties) { super(properties); }

    public static BlockPos pendingPos(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(START_POS) ? BlockPos.of(tag.getLong(START_POS)) : null;
    }

    public static boolean pendingIsTablet(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(START_TABLET);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ncat_minecraft.serial_cable.help").withStyle(ChatFormatting.GRAY));
        if (pending(stack)) tooltip.add(Component.translatable("ncat_minecraft.message.serialFirst")
                .withStyle(ChatFormatting.AQUA));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(),
                context.getClickedPos(), false);
        SerialCableService.Endpoint target = SerialCableService.hit(level, hit);
        if (target == null) {
            notify(player, "serialUnavailable");
            return InteractionResult.SUCCESS;
        }
        if (target instanceof SerialCableService.SwitchEnd end && !end.entity().canConfigure(player)) {
            notify(player, "serialUnavailable");
            return InteractionResult.SUCCESS;
        }
        SerialCableService.repairStale(level, target);
        if (player.isShiftKeyDown()) {
            ManagedTabletBlockEntity tablet = target instanceof SerialCableService.TabletEnd end ? end.entity() :
                    SerialCableService.connectedTablet(level, ((SerialCableService.SwitchEnd) target).entity());
            if (tablet != null && SerialCableService.disconnect(level, tablet, player, true)) {
                clear(stack);
                player.swing(context.getHand(), true);
                notify(player, "serialDisconnected");
            } else notify(player, "serialUnavailable");
            return InteractionResult.SUCCESS;
        }
        if (!pending(stack)) {
            if (target instanceof SerialCableService.TabletEnd tabletEnd && tabletEnd.entity().linkedSwitchPos() != null ||
                    target instanceof SerialCableService.SwitchEnd switchEnd && switchEnd.entity().serialTabletPos() != null) {
                notify(player, "serialBusy");
                return InteractionResult.SUCCESS;
            }
            CompoundTag tag = stack.getOrCreateTag();
            tag.putLong(START_POS, target.pos().asLong());
            tag.putBoolean(START_TABLET, target instanceof SerialCableService.TabletEnd);
            tag.putString(START_DIMENSION, level.dimension().location().toString());
            player.swing(context.getHand(), true);
            animate(player, 0);
            level.playSound(null, target.pos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, .35F, 1.2F);
            notify(player, "serialFirst");
            return InteractionResult.SUCCESS;
        }
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getString(START_DIMENSION).equals(level.dimension().location().toString()) ||
                tag.getBoolean(START_TABLET) == (target instanceof SerialCableService.TabletEnd) ||
                !level.hasChunkAt(BlockPos.of(tag.getLong(START_POS)))) {
            clear(stack);
            notify(player, "serialUnavailable");
            return InteractionResult.SUCCESS;
        }
        BlockEntity first = level.getBlockEntity(BlockPos.of(tag.getLong(START_POS)));
        ManagedTabletBlockEntity tablet = first instanceof ManagedTabletBlockEntity t ? t :
                target instanceof SerialCableService.TabletEnd t ? t.entity() : null;
        ManagedSwitchBlockEntity networkSwitch = first instanceof ManagedSwitchBlockEntity s ? s :
                target instanceof SerialCableService.SwitchEnd s ? s.entity() : null;
        clear(stack);
        if (SerialCableService.connect(level, tablet, networkSwitch, player)) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            player.swing(context.getHand(), true);
            animate(player, 1);
            level.playSound(null, target.pos(), SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, .45F, 1.45F);
            notify(player, "serialConnected");
        } else notify(player, "serialUnavailable");
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && pending(stack)) {
            clear(stack);
            notify(player, "serialUnavailable");
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static boolean pending(ItemStack stack) {
        return stack.getTag() != null && stack.getTag().contains(START_POS);
    }

    private static void clear(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(START_POS);
        tag.remove(START_TABLET);
        tag.remove(START_DIMENSION);
        if (tag.isEmpty()) stack.setTag(null);
    }

    private static void notify(Player player, String id) {
        player.displayClientMessage(Component.translatable("ncat_minecraft.message." + id), true);
    }

    private static void animate(Player player, int phase) {
        if (player instanceof ServerPlayer serverPlayer)
            WDNetworkRegistry.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new S2CMessageSerialAnimation(phase));
    }
}
