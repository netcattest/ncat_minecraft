/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemStack;

public interface IItemRenderer {
	
	boolean render(PoseStack pose, ItemStack stack, float handSideSign, float swingProgress, float equipProgress, MultiBufferSource multiBufferSource, int packedLight);
	
}
