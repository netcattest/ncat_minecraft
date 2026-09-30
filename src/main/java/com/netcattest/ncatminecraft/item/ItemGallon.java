package com.netcattest.ncatminecraft.item;

import com.netcattest.ncatminecraft.registry.ItemRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "ncat_minecraft")
public final class ItemGallon extends Item {
    public static final int MAX_WATER = 96;
    public static final String WATER = "Water";
    private static final UUID WEIGHT_MAIN = UUID.fromString("6f1c2a80-7a1e-4b5d-9c3a-1a0e5d6b7c11");
    private static final UUID WEIGHT_OFF = UUID.fromString("6f1c2a80-7a1e-4b5d-9c3a-1a0e5d6b7c12");

    public ItemGallon() {
        super(new Properties().stacksTo(4));
    }

    public static int waterOf(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(WATER))
            return MAX_WATER;
        return Math.max(0, Math.min(MAX_WATER, tag.getInt(WATER)));
    }

    public static ItemStack withWater(int amount) {
        ItemStack stack = new ItemStack(ItemRegistry.GALLON.get());
        stack.getOrCreateTag().putInt(WATER, Math.max(0, Math.min(MAX_WATER, amount)));
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        int water = waterOf(stack);
        String key;
        ChatFormatting color;
        if (water <= 0) {
            key = "item.ncat_minecraft.water_gallon.empty";
            color = ChatFormatting.DARK_GRAY;
        } else if (water <= MAX_WATER / 6) {
            key = "item.ncat_minecraft.water_gallon.drips";
            color = ChatFormatting.GRAY;
        } else if (water <= MAX_WATER / 2) {
            key = "item.ncat_minecraft.water_gallon.half";
            color = ChatFormatting.AQUA;
        } else {
            key = "item.ncat_minecraft.water_gallon.full";
            color = ChatFormatting.BLUE;
        }
        tooltip.add(Component.translatable(key).withStyle(color));
        if (water > 0)
            tooltip.add(Component.translatable("item.ncat_minecraft.water_gallon.weight").withStyle(ChatFormatting.DARK_AQUA));
    }

    @SubscribeEvent
    public static void onAttributes(ItemAttributeModifierEvent event) {
        if (!event.getItemStack().is(ItemRegistry.GALLON.get()))
            return;
        EquipmentSlot slot = event.getSlotType();
        if (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND)
            return;
        int water = waterOf(event.getItemStack());
        if (water <= 0)
            return;
        double slow = -0.12D * water / MAX_WATER;
        UUID id = slot == EquipmentSlot.MAINHAND ? WEIGHT_MAIN : WEIGHT_OFF;
        event.addModifier(Attributes.MOVEMENT_SPEED, new AttributeModifier(
                id, "ncat_gallon_weight", slow, AttributeModifier.Operation.MULTIPLY_BASE));
    }
}
