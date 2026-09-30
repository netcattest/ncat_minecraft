package com.netcattest.ncatminecraft.item;

import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.registry.ItemRegistry;
import com.netcattest.ncatminecraft.utilities.UsbCableService;
import com.netcattest.ncatminecraft.utilities.serialization.Util;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

public final class ItemUsbPortTool extends Item {
    public ItemUsbPortTool(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ncat_minecraft.usb_port_tool.help").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), false);
        UsbCableService.Endpoint target = UsbCableService.hitScreen(level, hit);
        if (target == null) {
            Util.toast(player, "usbNoScreen");
            return InteractionResult.SUCCESS;
        }
        if ((target.screen().rightsFor(player) & ScreenRights.MANAGE_UPGRADES) == 0) {
            Util.toast(player, "restrictions");
            return InteractionResult.SUCCESS;
        }
        ItemStack supply = ItemStack.EMPTY;
        if (!player.getAbilities().instabuild) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack candidate = player.getInventory().getItem(i);
                if (candidate.is(ItemRegistry.USB_PORT.get())) { supply = candidate; break; }
            }
            if (supply.isEmpty()) {
                Util.toast(player, "usbPortMissing");
                return InteractionResult.SUCCESS;
            }
        }
        if (UsbCableService.addPort(level, target, hit)) {
            if (!player.getAbilities().instabuild) supply.shrink(1);
            Util.toast(player, ChatFormatting.AQUA, "usbPortAdded");
        } else Util.toast(player, "usbPortFailed");
        return InteractionResult.SUCCESS;
    }
}
