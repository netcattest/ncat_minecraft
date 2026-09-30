package com.netcattest.ncatminecraft.item;

import com.netcattest.ncatminecraft.client.gui.GuiLogTablet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;
import javax.annotation.Nullable;

public final class ItemLogTablet extends Item {
    public ItemLogTablet(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.hasTag() || !stack.getTag().contains("NcatLogDetail"))
            return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        if (level.isClientSide)
            Minecraft.getInstance().setScreen(new GuiLogTablet(stack.copy(), player));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public int getUseDuration(ItemStack stack) { return 72000; }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) { return stack; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(stack.hasTag() && stack.getTag().contains("NcatLogDetail") ?
                "item.ncat_minecraft.log_tablet.ready" : "item.ncat_minecraft.log_tablet.empty"));
    }
}
