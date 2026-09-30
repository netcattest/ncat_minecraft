package com.netcattest.ncatminecraft.item;

import com.netcattest.ncatminecraft.registry.ItemRegistry;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.utilities.serialization.Util;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
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

public final class ItemCableAdjuster extends Item {
    public ItemCableAdjuster(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ncat_minecraft.cable_adjuster.help").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.ncat_minecraft.cable_adjuster.help2").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        DataCableService.CableEnd clicked = DataCableService.hitAnyPort(context.getLevel(),
                new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), false));
        if (clicked != null) {
            if (!DataCableService.canManage(clicked, player)) {
                Util.toast(player, "restrictions");
            } else if (!clicked.port().connected()) {
                Util.toast(player, "cablePortFree");
            } else if (!canManageOther(context.getLevel(), player, clicked)) {
                Util.toast(player, "cableOtherUnavailable");
            } else if (player.isShiftKeyDown()) {
                if (DataCableService.disconnect(context.getLevel(), clicked)) {
                    DataCableSelection.clear(stack);
                    if (!player.getAbilities().instabuild && !player.getInventory().add(new ItemStack(ItemRegistry.DATA_CABLE.get())))
                        player.drop(new ItemStack(ItemRegistry.DATA_CABLE.get()), false);
                    Util.toast(player, ChatFormatting.AQUA, "cableDisconnected");
                }
            } else {
                DataCableSelection.set(stack, context.getLevel(), clicked);
                Util.toast(player, ChatFormatting.AQUA, "cableSelected");
            }
            return InteractionResult.SUCCESS;
        }
        DataCableService.CableEnd selected = DataCableSelection.get(stack, context.getLevel());
        if (selected == null) {
            DataCableSelection.clear(stack);
            Util.toast(player, "cableSelectFirst");
            return InteractionResult.SUCCESS;
        }
        if (!DataCableService.canManage(selected, player)) {
            DataCableSelection.clear(stack);
            Util.toast(player, "restrictions");
            return InteractionResult.SUCCESS;
        }
        if (!canManageOther(context.getLevel(), player, selected)) {
            Util.toast(player, "cableOtherUnavailable");
            return InteractionResult.SUCCESS;
        }
        Direction face = context.getClickedFace();
        Vec3 point = context.getClickLocation().add(face.getStepX() * .07, face.getStepY() * .07, face.getStepZ() * .07);
        if (DataCableService.waypoint(context.getLevel(), selected, point, player.isShiftKeyDown()))
            Util.toast(player, ChatFormatting.AQUA, player.isShiftKeyDown() ? "cableWaypointRemoved" : "cableWaypoint");
        else Util.toast(player, "cableWaypointFailed");
        return InteractionResult.SUCCESS;
    }

    private static boolean canManageOther(Level level, Player player, DataCableService.CableEnd endpoint) {
        DataCableService.CableEnd other = DataCableService.other(level, endpoint);
        return other != null && DataCableService.canManage(other, player);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && DataCableSelection.active(stack)) {
            DataCableSelection.clear(stack);
            Util.toast(player, ChatFormatting.GOLD, "cableCancelled");
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
