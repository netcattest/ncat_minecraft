package com.netcattest.ncatminecraft.item;

import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.registry.ItemRegistry;
import com.netcattest.ncatminecraft.utilities.DataCableService;
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

public final class ItemDataPortTool extends Item {
    public ItemDataPortTool(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.ncat_minecraft.data_port_tool.help").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        DataCableService.Endpoint endpoint = DataCableService.hit(context.getLevel(),
                new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), false));
        if (endpoint == null) {
            Util.toast(player, "cableNoScreen");
            return InteractionResult.SUCCESS;
        }
        if ((endpoint.screen().rightsFor(player) & ScreenRights.MANAGE_UPGRADES) == 0) {
            Util.toast(player, "restrictions");
            return InteractionResult.SUCCESS;
        }
        ItemStack supply = ItemStack.EMPTY;
        if (!player.getAbilities().instabuild) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack candidate = player.getInventory().getItem(i);
                if (candidate.is(ItemRegistry.DATA_PORT.get())) {
                    supply = candidate;
                    break;
                }
            }
            if (supply.isEmpty()) {
                Util.toast(player, "portMissing");
                return InteractionResult.SUCCESS;
            }
        }
        if (DataCableService.addPort(endpoint,
                new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), false))) {
            if (!player.getAbilities().instabuild) supply.shrink(1);
            Util.toast(player, ChatFormatting.AQUA, "portAdded");
        } else {
            Util.toast(player, "portFailed");
        }
        return InteractionResult.SUCCESS;
    }
}
