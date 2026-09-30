package com.netcattest.ncatminecraft.recipe;

import com.netcattest.ncatminecraft.item.ItemGallon;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class GallonRecipe extends CustomRecipe {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, "ncat_minecraft");
    public static final RegistryObject<RecipeSerializer<GallonRecipe>> SERIALIZER = SERIALIZERS.register(
            "water_gallon", () -> new SimpleCraftingRecipeSerializer<>(GallonRecipe::new));

    public GallonRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    public static void init(IEventBus bus) {
        SERIALIZERS.register(bus);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        if (container.getWidth() < 3 || container.getHeight() < 3)
            return false;
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                ItemStack stack = container.getItem(x + y * container.getWidth());
                if (x == 1 && y == 1) {
                    if (!stack.is(Items.WATER_BUCKET))
                        return false;
                } else if (!stack.is(Items.GLASS)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess access) {
        return ItemGallon.withWater(ItemGallon.MAX_WATER);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }

    @Override
    public boolean isSpecial() {
        return false;
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer container) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < remaining.size(); i++) {
            if (container.getItem(i).is(Items.WATER_BUCKET))
                remaining.set(i, new ItemStack(Items.BUCKET));
        }
        return remaining;
    }
}
