package com.netcattest.ncatminecraft.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ItemPaperCup extends Item {
    private final boolean filled;

    public ItemPaperCup(boolean filled) {
        super(new Properties().stacksTo(filled ? 16 : 64));
        this.filled = filled;
    }

    public boolean filled() {
        return filled;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!filled)
            return InteractionResultHolder.pass(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return filled ? 32 : 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!filled)
            return stack;
        if (entity instanceof Player player && !player.getAbilities().instabuild)
            stack.shrink(1);
        entity.clearFire();
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 0.8F, 1.0F);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (filled) {
            tooltip.add(Component.translatable("item.ncat_minecraft.paper_cup_water.hint").withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("item.ncat_minecraft.paper_cup.hint").withStyle(ChatFormatting.GRAY));
        }
    }
}
