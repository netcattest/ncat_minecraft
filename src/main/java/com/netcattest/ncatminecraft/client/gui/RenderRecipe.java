/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraftforge.api.distmarker.OnlyIn;
import com.netcattest.ncatminecraft.utilities.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.stream.IntStream;

import static net.minecraftforge.api.distmarker.Dist.CLIENT;

@OnlyIn(CLIENT)
public class RenderRecipe extends Screen {
    public RenderRecipe() {
        super(Component.nullToEmpty(null));
    }

    private static class NameRecipePair {

        private final String name;
        private final ShapedRecipe recipe;

        private NameRecipePair(String n, ShapedRecipe r) {
            this.name = n;
            this.recipe = r;
        }

    }

    private static final ResourceLocation CRAFTING_TABLE_GUI_TEXTURES = new ResourceLocation("textures/gui/container/crafting_table.png");
    private static final int SIZE_X = 176;
    private static final int SIZE_Y = 166;
    private int x;
    private int y;
    private ItemRenderer renderItem;
    private final ItemStack[] recipe = new ItemStack[3 * 3];
    private ItemStack recipeResult;
    private String recipeName;
    private final ArrayList<NameRecipePair> recipes = new ArrayList<>();
    private ByteBuffer buffer;
    private int[] array;

    @Override
    public void init() {
        x = (width - SIZE_X) / 2;
        y = (height - SIZE_Y) / 2;
        renderItem = minecraft.getItemRenderer();

        for(Recipe recipe : minecraft.level.getRecipeManager().getRecipes()) {
            ResourceLocation regName = recipe.getId();

            if(regName != null && regName.getNamespace().equals("ncat_minecraft")) {
                if(recipe instanceof ShapedRecipe)
                    recipes.add(new NameRecipePair(regName.getPath(), (ShapedRecipe) recipe));
                else
                    Log.warning("Found non-shaped recipe %s", regName.toString());
            }
        }

        Log.info("Loaded %d recipes", recipes.size());
        nextRecipe();
    }
    
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float partialTick) {
        renderBackground(context);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, CRAFTING_TABLE_GUI_TEXTURES);

        Lighting.setupForFlatItems();

        for(int sy = 0; sy < 3; sy++) {
            for(int sx = 0; sx < 3; sx++) {
                ItemStack is = recipe[sy * 3 + sx];

                if(is != null) {
                    int x = this.x + 30 + sx * 18;
                    int y = this.y + 17 + sy * 18;

                    context.renderItem(is, x, y);
                    context.renderItemDecorations(font, is, x, y);
                }
            }
        }

        if(recipeResult != null) {
            context.renderItem(recipeResult, x, y);
            context.renderItemDecorations(font, recipeResult, x, y);
        }

        Lighting.setupFor3DItems();
    }

    private void setRecipe(ShapedRecipe recipe) {
        IntStream.range(0, this.recipe.length).forEach(i -> this.recipe[i] = null);
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        int pos = 0;

        for(int y = 0; y < recipe.getRecipeHeight(); y++) {
            for(int x = 0; x < recipe.getRecipeWidth(); x++) {
                ItemStack[] stacks = ingredients.get(pos++).getItems();

                if(stacks.length > 0)
                    this.recipe[y * 3 + x] = stacks[0];
            }
        }

    }

    private void nextRecipe() {
        if(recipes.isEmpty())
            minecraft.setScreen(null);
        else {
            NameRecipePair pair = recipes.remove(0);
            setRecipe(pair.recipe);
            recipeName = pair.name;
        }
    }

    private int screen2DisplayX(int x) {
        double ret = ((double) x) / ((double) width) * ((double) minecraft.getWindow().getWidth());
        return (int) ret;
    }

    private int screen2DisplayY(int y) {
        double ret = ((double) y) / ((double) height) * ((double) minecraft.getWindow().getHeight());
        return (int) ret;
    }

    private void takeScreenshot() throws Throwable {







    }

    @Override
    public void tick() {
        if(recipeName != null) {
            try {
                takeScreenshot();
                nextRecipe();
            } catch(Throwable t) {
                t.printStackTrace();
                minecraft.setScreen(null);
            }
        }
    }

}
