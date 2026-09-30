package com.netcattest.ncatminecraft.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.netcattest.ncatminecraft.block.WaterCoolerBlock;
import com.netcattest.ncatminecraft.entity.WaterCoolerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.math.Axis;

public final class WaterCoolerRenderer implements BlockEntityRenderer<WaterCoolerBlockEntity> {
    public static final ResourceLocation LEVER_RED = new ResourceLocation("ncat_minecraft", "block/water_cooler_lever_red");
    public static final ResourceLocation LEVER_BLUE = new ResourceLocation("ncat_minecraft", "block/water_cooler_lever_blue");
    public static final ResourceLocation CUBE = new ResourceLocation("ncat_minecraft", "block/water_cooler_cube");
    public static final ResourceLocation PAPER_CUP = new ResourceLocation("ncat_minecraft", "block/paper_cup");
    private static final Vec3 RED_PIVOT = new Vec3(9.9D / 16.0D, 13.23D / 16.0D, 4.05D / 16.0D);
    private static final Vec3 BLUE_PIVOT = new Vec3(6.1D / 16.0D, 13.23D / 16.0D, 4.05D / 16.0D);

    public WaterCoolerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(WaterCoolerBlockEntity cooler, float partial, PoseStack pose, MultiBufferSource buffer,
                       int light, int overlay) {
        BlockState state = cooler.getBlockState();
        if (!state.hasProperty(WaterCoolerBlock.FACING))
            return;
        int yRot = switch (state.getValue(WaterCoolerBlock.FACING)) {
            case EAST -> 270;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(yRot));
        pose.translate(-0.5D, 0.0D, -0.5D);
        renderLever(pose, buffer, light, overlay, state, LEVER_RED, RED_PIVOT, cooler.hotAngle(partial));
        renderLever(pose, buffer, light, overlay, state, LEVER_BLUE, BLUE_PIVOT, cooler.coldAngle(partial));
        if (cooler.hasGallon()) {
            int above = cooler.getLevel() == null ? light : LevelRenderer.getLightColor(cooler.getLevel(), cooler.getBlockPos().above());
            float fill = cooler.shownFill(partial);
            if (fill > 0.012F)
                renderScaled(pose, buffer, above, overlay, state, 4.85D, 21.95D, 4.5D, 6.3D, 8.05D * fill, 6.1D, 0.62F, 0.86F, 1.0F);
            long time = cooler.getLevel() == null ? 0L : cooler.getLevel().getGameTime();
            boolean drip = cooler.water() > 0 && cooler.water() <= WaterCoolerBlockEntity.DRIP_WATER;
            if (cooler.water() > WaterCoolerBlockEntity.DRIP_WATER) {
                float pulse = 0.82F + 0.18F * (float) Math.sin((time + partial) * 0.45F);
                if (cooler.hotOpen())
                    renderStream(pose, buffer, light, overlay, state, 9.9D, pulse,
                            cooler.hasCup() && cooler.cupSide() > 0, 0.72F, 0.88F, 1.0F);
                if (cooler.coldOpen())
                    renderStream(pose, buffer, light, overlay, state, 6.1D, pulse,
                            cooler.hasCup() && cooler.cupSide() < 0, 0.45F, 0.72F, 1.0F);
            } else if (drip && (time / 7L) % 2L == 0L) {
                if (cooler.hotOpen())
                    renderScaled(pose, buffer, light, overlay, state, 9.7D, 8.25D, 3.0D, 0.4D, 0.5D, 0.4D, 0.7F, 0.88F, 1.0F);
                if (cooler.coldOpen())
                    renderScaled(pose, buffer, light, overlay, state, 5.9D, 8.25D, 3.0D, 0.4D, 0.5D, 0.4D, 0.45F, 0.72F, 1.0F);
            }
        }
        if (cooler.hasCup()) {
            float scale = 0.29F;
            double centerX = cooler.cupX();
            pose.pushPose();
            pose.translate(centerX / 16.0D - scale * 0.5D, 5.65D / 16.0D,
                    3.15D / 16.0D - scale * 0.5D);
            pose.scale(scale, scale, scale);
            draw(pose, buffer, light, overlay, state, model(PAPER_CUP), 1F, 1F, 1F);
            pose.popPose();
            float cup = cooler.cupLevel(partial);
            if (cup > 0.03F)
                renderScaled(pose, buffer, light, overlay, state, centerX - 1.05D,
                        6.2D + 2.05D * cup, 2.1D, 2.1D, 0.13D, 2.1D, 0.55F, 0.82F, 1.0F);
        }
        pose.popPose();
    }

    public AABB getRenderBoundingBox(WaterCoolerBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).expandTowards(0.0D, 1.0D, 0.0D);
    }

    private void renderLever(PoseStack pose, MultiBufferSource buffer, int light, int overlay, BlockState state,
                             ResourceLocation modelId, Vec3 pivot, float angle) {
        pose.pushPose();
        pose.translate(pivot.x, pivot.y, pivot.z);
        pose.mulPose(Axis.XP.rotationDegrees(angle));
        pose.translate(-pivot.x, -pivot.y, -pivot.z);
        draw(pose, buffer, light, overlay, state, model(modelId), 1F, 1F, 1F);
        pose.popPose();
    }

    private void renderStream(PoseStack pose, MultiBufferSource buffer, int light, int overlay, BlockState state,
                              double x, float pulse, boolean intoCup, float red, float green, float blue) {
        double height = (intoCup ? 0.86D : 3.1D) * pulse;
        renderScaled(pose, buffer, light, overlay, state, x - 0.19D, 9.08D - height,
                2.96D, 0.38D, height, 0.38D, red, green, blue);
    }

    private void renderScaled(PoseStack pose, MultiBufferSource buffer, int light, int overlay, BlockState state,
                              double x, double y, double z, double width, double height, double depth,
                              float red, float green, float blue) {
        if (height < 0.04D)
            return;
        pose.pushPose();
        pose.translate(x / 16.0D, y / 16.0D, z / 16.0D);
        pose.scale((float) (width / 16.0D), (float) (height / 16.0D), (float) (depth / 16.0D));
        draw(pose, buffer, light, overlay, state, model(CUBE), red, green, blue);
        pose.popPose();
    }

    private void draw(PoseStack pose, MultiBufferSource buffer, int light, int overlay, BlockState state,
                      BakedModel model, float red, float green, float blue) {
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                pose.last(), buffer.getBuffer(RenderType.solid()), state, model, red, green, blue, light, overlay);
    }

    private static BakedModel model(ResourceLocation id) {
        return Minecraft.getInstance().getModelManager().getModel(id);
    }
}
