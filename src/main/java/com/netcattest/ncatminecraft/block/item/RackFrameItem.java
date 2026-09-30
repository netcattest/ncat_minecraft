package com.netcattest.ncatminecraft.block.item;

import com.netcattest.ncatminecraft.block.RackFrameBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RackFrameItem extends BlockItem {
    public RackFrameItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        if (!(getBlock() instanceof RackFrameBlock frame))
            return;
        tooltip.add(Component.translatable("block.ncat_minecraft.rack.units", frame.units())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(switch (frame.role()) {
            case BASE -> "block.ncat_minecraft.rack.role.base";
            case EXTENSION -> "block.ncat_minecraft.rack.role.extension";
            default -> "block.ncat_minecraft.rack.role.standalone";
        }).withStyle(ChatFormatting.GRAY));
        if (frame.role() != RackFrameBlock.Role.STANDALONE)
            tooltip.add(Component.translatable("block.ncat_minecraft.rack.role.limit",
                    RackFrameBlock.MAX_SECTIONS).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("block.ncat_minecraft.rack.role.help")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
