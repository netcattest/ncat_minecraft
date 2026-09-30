package com.netcattest.ncatminecraft.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.netcattest.ncatminecraft.block.NetworkSwitchBlock;
import com.netcattest.ncatminecraft.block.SwitchShapes;
import com.netcattest.ncatminecraft.entity.NetworkSwitchBlockEntity;
import com.netcattest.ncatminecraft.entity.ManagedSwitchBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

public final class NetworkSwitchRenderer implements BlockEntityRenderer<NetworkSwitchBlockEntity> {
    private static final double[] PORT_X = SwitchShapes.PORT_X;
    private static final double[] PORT_Y = SwitchShapes.PORT_Y;

    public NetworkSwitchRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NetworkSwitchBlockEntity networkSwitch, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Direction facing = networkSwitch.getBlockState().getValue(NetworkSwitchBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case EAST -> -90F;
            case SOUTH -> 180F;
            case WEST -> 90F;
            default -> 0F;
        }));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        boolean powered = networkSwitch.powered();

        lamp(consumer, matrix, SwitchShapes.POWER_LED[0], SwitchShapes.POWER_LED[1],
                SwitchShapes.LED_Z, 0.52D,
                powered ? 62 : 44, powered ? 226 : 48, powered ? 118 : 54);

        for (int i = 0; i < NetworkSwitchBlockEntity.PORT_COUNT; i++) {
            boolean linked = powered && networkSwitch.activeLink(i) &&
                    (!(networkSwitch instanceof ManagedSwitchBlockEntity managed) || managed.portEnabled(i));
            boolean active = linked && networkSwitch.trafficLight(i);
            lamp(consumer, matrix, PORT_X[i] - SwitchShapes.LED_DX, SwitchShapes.LED_Y,
                    SwitchShapes.LED_Z, 0.40D,
                    linked ? 74 : 26, linked ? 232 : 40, linked ? 124 : 48);
            lamp(consumer, matrix, PORT_X[i] + SwitchShapes.LED_DX, SwitchShapes.LED_Y,
                    SwitchShapes.LED_Z, 0.40D,
                    active ? 244 : 34, active ? 206 : 34, active ? 76 : 26);
        }
        poseStack.popPose();
        DataCableRenderer.renderSwitch(networkSwitch, poseStack, buffers);
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    private static void lamp(VertexConsumer consumer, Matrix4f matrix, double x, double y, double z,
                             double size, int red, int green, int blue) {
        double half = size / 32.0D;
        double centerX = x / 16.0D;
        double centerY = y / 16.0D;
        double depth = z / 16.0D;
        quad(consumer, matrix, centerX - half, centerY - half,
                centerX + half, centerY + half, depth,
                red / 3, green / 3, blue / 3);
        half *= 0.68D;
        quad(consumer, matrix, centerX - half, centerY - half,
                centerX + half, centerY + half, depth - 0.0004D,
                red, green, blue);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, double x0, double y0,
                             double x1, double y1, double z, int red, int green, int blue) {
        consumer.vertex(matrix, (float) x0, (float) y0, (float) z).color(red, green, blue, 255).endVertex();
        consumer.vertex(matrix, (float) x1, (float) y0, (float) z).color(red, green, blue, 255).endVertex();
        consumer.vertex(matrix, (float) x1, (float) y1, (float) z).color(red, green, blue, 255).endVertex();
        consumer.vertex(matrix, (float) x0, (float) y1, (float) z).color(red, green, blue, 255).endVertex();
    }
}
