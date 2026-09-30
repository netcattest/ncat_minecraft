/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.client.renderers;

import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.client.workstation.WorkstationClientView;
import com.netcattest.ncatminecraft.utilities.math.Vector3f;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;
import org.jetbrains.annotations.NotNull;
import org.cef.browser.CefBrowser;

import static com.mojang.math.Axis.*;

public class ScreenRenderer implements BlockEntityRenderer<ScreenBlockEntity> {
	public ScreenRenderer() {
	}

	@Override
	public int getViewDistance() {
		return 112;
	}
	
	public static class ScreenRendererProvider implements BlockEntityRendererProvider<ScreenBlockEntity> {
		@Override
		public @NotNull BlockEntityRenderer<ScreenBlockEntity> create(@NotNull Context arg) {
			return new ScreenRenderer();
		}
	}
	
	private final Vector3f mid = new Vector3f();
	private final Vector3i tmpi = new Vector3i();
	private final Vector3f tmpf = new Vector3f();
	
	@Override
	public void render(ScreenBlockEntity te, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		if (!te.isLoaded())
			return;

		RenderSystem.disableBlend();
		
		for (int i = 0; i < te.screenCount(); i++) {
			ScreenData scr = te.getScreen(i);
			if (scr.browser == null) {
				double dist = NcatMinecraft.PROXY.distanceTo(te, Minecraft.getInstance().getEntityRenderDispatcher().camera.getPosition());
				if (dist <= NcatMinecraft.INSTANCE.loadDistance2 * 16)
					scr.createBrowser(te, true);
				else continue;
			}
			
			
			tmpi.set(scr.side.right);
			tmpi.mul(scr.size.x);
			tmpi.addMul(scr.side.up, scr.size.y);
			tmpf.set(tmpi);
			mid.set(0.5, 0.5, 0.5);
			mid.addMul(tmpf, 0.5f);
			tmpf.set(scr.side.left);
			mid.addMul(tmpf, 0.5f);
			tmpf.set(scr.side.down);
			mid.addMul(tmpf, 0.5f);
			
			poseStack.pushPose();
			poseStack.translate(mid.x, mid.y, mid.z);
			
			switch (scr.side) {
				case BOTTOM:
					poseStack.mulPose(XP.rotation(90.f + 49.8f));
					break;
				
				case TOP:
					poseStack.mulPose(XN.rotation(90.f + 49.8f));
					break;
				
				case NORTH:
					poseStack.mulPose(YN.rotationDegrees(180.f));
					break;
				
				case SOUTH:
					break;
				
				case WEST:
					poseStack.mulPose(YN.rotationDegrees(90.f));
					break;
				
				case EAST:
					poseStack.mulPose(YP.rotationDegrees(90.f));
					break;
			}
			
			if (scr.doTurnOnAnim) {
				long lt = System.currentTimeMillis() - scr.turnOnTime;
				float ft = ((float) lt) / 100.0f;
				
				if (ft >= 1.0f) {
					ft = 1.0f;
					scr.doTurnOnAnim = false;
				}
				
				poseStack.scale(ft, ft, 1.0f);
			}
			
			if (!scr.rotation.isNull)
				poseStack.mulPose(ZP.rotationDegrees(scr.rotation.angle));
			
			float sw = ((float) scr.size.x) * 0.5f - 2.f / 16.f;
			float sh = ((float) scr.size.y) * 0.5f - 2.f / 16.f;
			
			if (scr.rotation.isVertical) {
				float tmp = sw;
				sw = sh;
				sh = tmp;
			}
			boolean workstation = com.netcattest.ncatminecraft.registry.BlockRegistry.isWorkstationScreen(te.getBlockState().getBlock());
			CefBrowser displayed = workstation ? WorkstationClientView.browserForDisplay(te, scr.side) : scr.browser;
			if (!(displayed instanceof MCEFBrowser displayedMcef)) {
				poseStack.popPose();
				continue;
			}
			float contentBottom = workstation ? WorkstationClientView.contentBottomY(scr, sh) : -sh;
			
			Tesselator tesselator = Tesselator.getInstance();
			BufferBuilder builder = tesselator.getBuilder();
			RenderSystem.enableDepthTest();
			RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
			RenderSystem._setShaderTexture(0, displayedMcef.getRenderer().getTextureID());
			RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
			builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
			builder.vertex(poseStack.last().pose(), -sw, contentBottom, 0.505f).uv(0.f, 1.f).color(1.f, 1.f, 1.f, 1.f).endVertex();
			builder.vertex(poseStack.last().pose(), sw, contentBottom, 0.505f).uv(1.f, 1.f).color(1.f, 1.f, 1.f, 1.f).endVertex();
			builder.vertex(poseStack.last().pose(), sw, sh, 0.505f).uv(1.f, 0.f).color(1.f, 1.f, 1.f, 1.f).endVertex();
			builder.vertex(poseStack.last().pose(), -sw, sh, 0.505f).uv(0.f, 0.f).color(1.f, 1.f, 1.f, 1.f).endVertex();
			tesselator.end();
			RenderSystem.disableDepthTest();
			if (workstation)
				WorkstationClientView.renderFooter(te, scr, poseStack, bufferSource, sw, sh, packedLight);
			
			
			poseStack.popPose();
		}

		DataCableRenderer.render(te, poseStack, bufferSource, packedLight, packedOverlay);
		UsbCableRenderer.render(te, poseStack, bufferSource);


		
	}
}
