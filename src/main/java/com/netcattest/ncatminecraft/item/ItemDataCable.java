package com.netcattest.ncatminecraft.item;

import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.client.rack.ClientRackAccess;
import com.netcattest.ncatminecraft.entity.RackBlockEntity;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.List;

public final class ItemDataCable extends Item {
    public ItemDataCable(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ncat_minecraft.data_cable.help").withStyle(ChatFormatting.GRAY));
        if (DataCableSelection.active(stack))
            tooltip.add(Component.translatable("item.ncat_minecraft.data_cable.pending").withStyle(ChatFormatting.AQUA));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (player.isShiftKeyDown() && RackBlockEntity.at(context.getLevel(), context.getClickedPos()) != null) {
            if (context.getLevel().isClientSide)
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRackAccess.open(context.getClickedPos()));
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        DataCableService.CableEnd target = DataCableService.hitAnyPort(context.getLevel(),
                new net.minecraft.world.phys.BlockHitResult(context.getClickLocation(), context.getClickedFace(),
                        context.getClickedPos(), false));
        if (target == null) {
            Util.toast(player, "cableAimPort");
            return InteractionResult.SUCCESS;
        }
        return selectOrConnect(context.getLevel(), player, stack, target);
    }

    public static InteractionResult selectOrConnect(Level level, Player player, ItemStack stack,
                                                     DataCableService.CableEnd target) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (target == null || !(stack.getItem() instanceof ItemDataCable)) return InteractionResult.PASS;
        if (!DataCableService.canManage(target, player)) {
            Util.toast(player, "restrictions");
            return InteractionResult.SUCCESS;
        }
        if (!DataCableSelection.active(stack)) {
            if (!DataCableService.available(level, target)) {
                Util.toast(player, "cablePortBusy");
                return InteractionResult.SUCCESS;
            }
            DataCableSelection.set(stack, level, target);
            Util.toast(player, ChatFormatting.AQUA, "cableFirst");
            return InteractionResult.SUCCESS;
        }
        DataCableService.CableEnd start = DataCableSelection.get(stack, level);
        if (start == null) {
            DataCableSelection.clear(stack);
            Util.toast(player, "cableFirstExpired");
            return InteractionResult.SUCCESS;
        }
        if (!DataCableService.canManage(start, player)) {
            DataCableSelection.clear(stack);
            Util.toast(player, "restrictions");
            return InteractionResult.SUCCESS;
        }
        if (DataCableService.connect(level, start, target)) {
            DataCableSelection.clear(stack);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            Util.toast(player, ChatFormatting.AQUA, "cableConnected");
        } else {
            Util.toast(player, "cableFailed");
        }
        return InteractionResult.SUCCESS;
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
