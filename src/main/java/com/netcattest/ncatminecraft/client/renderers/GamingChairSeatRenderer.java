package com.netcattest.ncatminecraft.client.renderers;

import com.netcattest.ncatminecraft.entity.GamingChairSeatEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class GamingChairSeatRenderer extends EntityRenderer<GamingChairSeatEntity> {
    public GamingChairSeatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(GamingChairSeatEntity entity) {
        return new ResourceLocation("ncat_minecraft", "textures/block/chair/leather_dark.png");
    }
}
