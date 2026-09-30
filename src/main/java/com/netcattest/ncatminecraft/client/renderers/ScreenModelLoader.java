package com.netcattest.ncatminecraft.client.renderers;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;

public class ScreenModelLoader implements IGeometryLoader<ScreenModelLoader.ScreenModelGeometry> {
    public static final ResourceLocation SCREEN_LOADER = new ResourceLocation("ncat_minecraft", "screen_loader");

    public static final ResourceLocation SCREEN_SIDE = new ResourceLocation("ncat_minecraft", "block/screen");

    private static final ResourceLocation[] SIDES = new ResourceLocation[16];
    public static final Material[] MATERIALS_SIDES = new Material[16];
    
    static {
        for (int i = 0; i < SIDES.length; i++) {
            SIDES[i] = new ResourceLocation(SCREEN_SIDE.getNamespace(), SCREEN_SIDE.getPath() + i);
            MATERIALS_SIDES[i] = ForgeHooksClient.getBlockMaterial(SIDES[i]);
        }
    }
    
    private static final java.util.Set<String> ACCENTS = java.util.Set.of(
            "red", "blue", "orange", "purple", "cyan", "green",
            "lime", "light_blue", "yellow", "magenta", "pink", "gray");

    @Override
    public ScreenModelGeometry read(JsonObject jsonObject, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        String accent = jsonObject.has("accent") ? jsonObject.get("accent").getAsString() : "red";
        if (!ACCENTS.contains(accent))
            throw new JsonParseException("Unknown screen accent: " + accent);
        return new ScreenModelGeometry(accent);
    }

    public static class ScreenModelGeometry implements IUnbakedGeometry<ScreenModelGeometry> {
        private final Material accentMaterial;

        public ScreenModelGeometry(String accent) {
            accentMaterial = ForgeHooksClient.getBlockMaterial(new ResourceLocation("minecraft", "block/" + accent + "_concrete"));
        }
        
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation) {
            return new ScreenBaker(modelState, spriteGetter, overrides, context.getTransforms(), accentMaterial);
        }
        
        

    }
}
