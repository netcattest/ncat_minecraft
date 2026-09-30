package com.netcattest.ncatminecraft.item;

import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.registry.ItemRegistry;
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
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ItemUsbCableAdjuster extends Item {
    public ItemUsbCableAdjuster(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ncat_minecraft.usb_cable_adjuster.help").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), false);
        UsbCableService.Endpoint clicked = UsbCableService.hitPort(level, hit);
        if (clicked != null) {
            if ((clicked.screen().rightsFor(player) & ScreenRights.MANAGE_UPGRADES) == 0) Util.toast(player, "restrictions");
            else if (!clicked.port().connected()) Util.toast(player, "usbPortFree");
            else if (player.isShiftKeyDown()) {
                if (UsbCableService.disconnect(level, clicked)) {
                    UsbSelection.clear(stack);
                    if (!player.getAbilities().instabuild && !player.getInventory().add(new ItemStack(ItemRegistry.USB_CABLE.get())))
                        player.drop(new ItemStack(ItemRegistry.USB_CABLE.get()), false);
                    Util.toast(player, ChatFormatting.AQUA, "usbDisconnected");
                }
            } else {
                UsbSelection.screen(stack, level, clicked);
                Util.toast(player, ChatFormatting.AQUA, "usbSelected");
            }
            return InteractionResult.SUCCESS;
        }
        UsbCableService.Endpoint selected = UsbSelection.screen(stack, level);
        if (selected == null || !selected.port().connected()) {
            UsbSelection.clear(stack);
            Util.toast(player, "usbSelectFirst");
            return InteractionResult.SUCCESS;
        }
        if ((selected.screen().rightsFor(player) & ScreenRights.MANAGE_UPGRADES) == 0) {
            UsbSelection.clear(stack);
            Util.toast(player, "restrictions");
            return InteractionResult.SUCCESS;
        }
        var face = context.getClickedFace();
        Vec3 point = context.getClickLocation().add(face.getStepX() * .08, face.getStepY() * .08, face.getStepZ() * .08);
        if (UsbCableService.waypoint(level, selected, point, player.isShiftKeyDown()))
            Util.toast(player, ChatFormatting.AQUA, player.isShiftKeyDown() ? "usbWaypointRemoved" : "usbWaypoint");
        else Util.toast(player, "usbWaypointFailed");
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
