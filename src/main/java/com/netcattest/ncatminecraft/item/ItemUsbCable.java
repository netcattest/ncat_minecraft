package com.netcattest.ncatminecraft.item;

import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.entity.KeyboardBlockEntity;
import com.netcattest.ncatminecraft.utilities.UsbCableService;
import com.netcattest.ncatminecraft.utilities.serialization.Util;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public final class ItemUsbCable extends Item {
    public ItemUsbCable(Properties properties) { super(properties); }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ncat_minecraft.usb_cable.help").withStyle(ChatFormatting.GRAY));
        if (UsbSelection.active(stack)) tooltip.add(Component.translatable("item.ncat_minecraft.usb_cable.pending").withStyle(ChatFormatting.AQUA));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), false);
        UsbCableService.Endpoint screen = UsbCableService.hitPort(level, hit);
        KeyboardBlockEntity keyboard = UsbCableService.keyboard(level, context.getClickedPos());
        if (screen == null && keyboard == null) {
            Util.toast(player, "usbAimPort");
            return InteractionResult.SUCCESS;
        }
        if (screen != null && (screen.screen().rightsFor(player) & ScreenRights.MANAGE_UPGRADES) == 0) {
            Util.toast(player, "restrictions");
            return InteractionResult.SUCCESS;
        }
        if (!UsbSelection.active(stack)) {
            if (screen != null && screen.port().connected() || keyboard != null && keyboard.usbLinked()) {
                Util.toast(player, "usbPortBusy");
                return InteractionResult.SUCCESS;
            }
            if (screen != null) UsbSelection.screen(stack, level, screen);
            else UsbSelection.keyboard(stack, level, keyboard.getBlockPos());
            Util.toast(player, ChatFormatting.AQUA, "usbFirst");
            return InteractionResult.SUCCESS;
        }
        UsbCableService.Endpoint selectedScreen = UsbSelection.screen(stack, level);
        var selectedKeyboardPos = UsbSelection.keyboard(stack, level);
        if (selectedScreen != null && keyboard == null || selectedKeyboardPos != null && screen == null) {
            Util.toast(player, "usbSecond");
            return InteractionResult.SUCCESS;
        }
        if (selectedScreen == null && selectedKeyboardPos == null) {
            UsbSelection.clear(stack);
            Util.toast(player, "usbExpired");
            return InteractionResult.SUCCESS;
        }
        if (selectedScreen != null && (selectedScreen.screen().rightsFor(player) & ScreenRights.MANAGE_UPGRADES) == 0) {
            UsbSelection.clear(stack);
            Util.toast(player, "restrictions");
            return InteractionResult.SUCCESS;
        }
        if (selectedKeyboardPos != null) keyboard = UsbCableService.keyboard(level, selectedKeyboardPos);
        if (UsbCableService.connect(level, selectedScreen == null ? screen : selectedScreen, keyboard)) {
            UsbSelection.clear(stack);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            Util.toast(player, ChatFormatting.AQUA, "usbConnected");
        } else Util.toast(player, "usbFailed");
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && UsbSelection.active(stack)) {
            UsbSelection.clear(stack);
            Util.toast(player, "usbCancelled");
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
