package com.netcattest.ncatminecraft.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.netcattest.ncatminecraft.block.DigitalClockBlock;
import com.netcattest.ncatminecraft.entity.DigitalClockBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

import java.time.LocalTime;

public final class DigitalClockRenderer implements BlockEntityRenderer<DigitalClockBlockEntity> {
    private static final ResourceLocation LED = new ResourceLocation("ncat_minecraft", "textures/block/clock/led.png");
    private static final int[] GLYPHS = {
            0b1110111, 0b0010010, 0b1011101, 0b1011011, 0b0111010,
            0b1101011, 0b1101111, 0b1010010, 0b1111111, 0b1111011
    };
    private static final float DIGIT_W = 1.82F;
    private static final float DIGIT_H = 3.05F * 1.34F;
    private static final float Z = 3.18F / 16F;
    private static final float DEPTH = 0.14F / 16F;

    public DigitalClockRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(DigitalClockBlockEntity clock, float partial, PoseStack pose, MultiBufferSource buffer,
                       int light, int overlay) {
        if (!(clock.getBlockState().getBlock() instanceof DigitalClockBlock block))
            return;
        int yRot = switch (clock.getBlockState().getValue(DigitalClockBlock.FACING)) {
            case EAST -> 270;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
        int hours;
        int minutes;
        boolean colon;
        if (block.realTime()) {
            LocalTime now = LocalTime.now();
            hours = now.getHour();
            minutes = now.getMinute();
            colon = (System.currentTimeMillis() / 500L) % 2L == 0L;
        } else {
            Level level = clock.getLevel();
            long ticks = level == null ? 6000L : Math.floorMod(level.getDayTime(), 24000L);
            long shifted = Math.floorMod(ticks + 6000L, 24000L);
            hours = (int) (shifted / 1000L);
            minutes = (int) ((shifted % 1000L) * 60L / 1000L);
            colon = (ticks / 8L) % 2L == 0L;
        }
        pose.pushPose();
        pose.translate(0.5D, 0.0D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(yRot));
        pose.translate(-0.5D, 0.0D, -0.5D);
        VertexConsumer consumer = buffer.getBuffer(RenderType.eyes(LED));
        PoseStack.Pose entry = pose.last();
        float y = 2.28F * 1.34F / 16F;
        drawDigit(consumer, entry, light, 10.48F / 16F, y, hours / 10, GLYPHS);
        drawDigit(consumer, entry, light, 8.35F / 16F, y, hours % 10, GLYPHS);
        if (colon)
            drawColon(consumer, entry, light, 7.55F / 16F, y);
        drawDigit(consumer, entry, light, 5.55F / 16F, y, minutes / 10, GLYPHS);
        drawDigit(consumer, entry, light, 3.42F / 16F, y, minutes % 10, GLYPHS);
        pose.popPose();
    }

    private static void drawDigit(VertexConsumer consumer, PoseStack.Pose pose, int light, float x, float y, int digit, int[] glyphs) {
        int original = glyphs[Math.max(0, Math.min(9, digit))];
        int mask = original & 0b1001001;
        mask |= (original & 0b0100000) >> 1;
        mask |= (original & 0b0010000) << 1;
        mask |= (original & 0b0000100) >> 1;
        mask |= (original & 0b0000010) << 1;
        float w = DIGIT_W / 16F;
        float h = DIGIT_H / 16F;
        float t = 0.30F / 16F;
        if ((mask & 0b1000000) != 0)
            box(consumer, pose, x + 0.22F / 16F, y + h - t, Z, x + w - 0.22F / 16F, y + h, Z + DEPTH, light);
        if ((mask & 0b0100000) != 0)
            box(consumer, pose, x, y + h * 0.52F, Z, x + t, y + h - 0.08F / 16F, Z + DEPTH, light);
        if ((mask & 0b0010000) != 0)
            box(consumer, pose, x + w - t, y + h * 0.52F, Z, x + w, y + h - 0.08F / 16F, Z + DEPTH, light);
        if ((mask & 0b0001000) != 0)
            box(consumer, pose, x + 0.22F / 16F, y + h * 0.5F - t * 0.5F, Z, x + w - 0.22F / 16F, y + h * 0.5F + t * 0.5F, Z + DEPTH, light);
        if ((mask & 0b0000100) != 0)
            box(consumer, pose, x, y + 0.08F / 16F, Z, x + t, y + h * 0.48F, Z + DEPTH, light);
        if ((mask & 0b0000010) != 0)
            box(consumer, pose, x + w - t, y + 0.08F / 16F, Z, x + w, y + h * 0.48F, Z + DEPTH, light);
        if ((mask & 0b0000001) != 0)
            box(consumer, pose, x + 0.22F / 16F, y, Z, x + w - 0.22F / 16F, y + t, Z + DEPTH, light);
    }

    private static void drawColon(VertexConsumer consumer, PoseStack.Pose pose, int light, float x, float y) {
        float s = 0.28F / 16F;
        float h = DIGIT_H / 16F;
        box(consumer, pose, x, y + h * 0.62F, Z, x + s, y + h * 0.62F + s, Z + DEPTH, light);
        box(consumer, pose, x, y + h * 0.28F, Z, x + s, y + h * 0.28F + s, Z + DEPTH, light);
    }

    private static void box(VertexConsumer consumer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1, int light) {
        quad(consumer, pose, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0F, 0F, -1F);
        quad(consumer, pose, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0F, 0F, 1F);
        quad(consumer, pose, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1F, 0F, 0F);
        quad(consumer, pose, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1F, 0F, 0F);
        quad(consumer, pose, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0F, 1F, 0F);
        quad(consumer, pose, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0F, -1F, 0F);
    }

    private static void quad(VertexConsumer consumer, PoseStack.Pose pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        Matrix4f matrix = pose.pose();
        vertex(consumer, matrix, x0, y0, z0, nx, ny, nz);
        vertex(consumer, matrix, x1, y1, z1, nx, ny, nz);
        vertex(consumer, matrix, x2, y2, z2, nx, ny, nz);
        vertex(consumer, matrix, x3, y3, z3, nx, ny, nz);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float nx, float ny, float nz) {
        consumer.vertex(matrix, x, y, z).color(255, 42, 32, 255).uv(0.5F, 0.5F)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(nx, ny, nz).endVertex();
    }
}
