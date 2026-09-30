package com.netcattest.ncatminecraft.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.netcattest.ncatminecraft.block.RackFrameBlock;
import com.netcattest.ncatminecraft.block.RackLayout;
import com.netcattest.ncatminecraft.block.RackShapes;
import com.netcattest.ncatminecraft.entity.DataPort;
import com.netcattest.ncatminecraft.entity.RackBlockEntity;
import com.netcattest.ncatminecraft.entity.RackModule;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.EnumMap;
import java.util.Map;

public final class RackRenderer implements BlockEntityRenderer<RackBlockEntity> {
    public static final ResourceLocation DOOR_12U = model("rack_frame_12u_door");
    public static final ResourceLocation DOOR_18U = model("rack_frame_18u_door");
    public static final ResourceLocation DOOR_6U = model("rack_wall_6u_door");
    private static final Map<RackModuleType, ResourceLocation> MODULE_TEXTURES = new EnumMap<>(RackModuleType.class);

    static {
        for (RackModuleType type : RackModuleType.values())
            MODULE_TEXTURES.put(type, texture("module_" + type.name().toLowerCase(java.util.Locale.ROOT)));
    }

    public RackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RackBlockEntity rack, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (rack.getLevel() == null || RackBlockEntity.at(rack.getLevel(), rack.getBlockPos()) != rack) return;
        Direction facing = rack.getFacing();
        pose.pushPose();
        pose.translate(.5D, 0D, .5D);
        pose.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case EAST -> -90F;
            case SOUTH -> 180F;
            case WEST -> 90F;
            default -> 0F;
        }));
        pose.translate(-.5D, 0D, -.5D);

        Matrix4f matrix = pose.last().pose();
        for (RackModule module : rack.getModules()) renderModule(rack, module, buffers, matrix, packedLight);
        rack.animateDoor();
        float angle = rack.doorAngle(partialTick);
        for (int section = 0; section < rack.heightBlocks(); section++)
            renderDoor(rack, section, angle, pose, buffers, packedLight, packedOverlay);
        pose.popPose();
        DataCableRenderer.renderRack(rack, pose, buffers);
    }

    @Override
    public int getViewDistance() {
        return 64;
    }

    private static ResourceLocation model(String name) {
        return new ResourceLocation("ncat_minecraft", "block/rack/" + name);
    }

    private static ResourceLocation texture(String name) {
        return new ResourceLocation("ncat_minecraft", "textures/block/rack/" + name + ".png");
    }



    private static void renderModule(RackBlockEntity rack, RackModule module,
                                     MultiBufferSource buffers, Matrix4f matrix, int packedLight) {
        if (module.startU() >= rack.getCapacityU()) return;
        double bottom = rack.unitY(module.startU()) + .005D;
        double top = rack.unitY(module.startU() + module.heightU()) - .005D;
        if (top <= bottom) return;
        int[] accent = accent(module.type());
        int count = module.portCount();
        double mid = (bottom + top) * .5D;
        double portY = RackLayout.portY(bottom, top);
        double portW = RackLayout.portWidth(count);
        double portH = RackLayout.portHeight(bottom, top);
        double ledY = RackLayout.ledY(bottom, top);
        double ledSize = Math.min(portW * .30D, (top - bottom) * .13D);
        long tick = rack.getLevel() != null ? rack.getLevel().getGameTime() : -100L;

        VertexConsumer solid = buffers.getBuffer(RenderType.debugQuads());
        cuboid(solid, matrix, RackLayout.MODULE_LEFT, bottom, RackLayout.FACE + .002D,
                RackLayout.MODULE_RIGHT, top, RackLayout.DEPTH, 20, 31, 41);
        face(solid, matrix, RackLayout.MODULE_LEFT, bottom, RackLayout.FACE - .001D,
                RackLayout.MODULE_RIGHT, bottom + .005D, accent[0], accent[1], accent[2]);
        face(solid, matrix, RackLayout.BAY_LEFT - .012D, portY - portH * .5D - .008D,
                RackLayout.FACE - .0015D, RackLayout.BAY_RIGHT + .012D,
                portY + portH * .5D + .008D, 12, 18, 25);

        boolean anyLink = false;
        boolean traffic = false;
        for (int index = 0; index < count; index++) {
            DataPort port = module.port(index);
            boolean enabled = module.powered() && module.portEnabled(index);
            boolean linked = enabled && port != null && port.connected();
            boolean moving = linked && port.trafficLight(tick);
            anyLink |= linked;
            traffic |= moving;
            double x = RackLayout.portX(index, count);
            socket(solid, matrix, x, portY, portW, portH, linked);
            lamp(solid, matrix, x, ledY, RackLayout.FACE - .002D, ledSize,
                    moving ? 84 : linked ? 40 : 30,
                    moving ? 240 : linked ? 190 : 42,
                    moving ? 150 : linked ? 108 : 50);
        }

        face(solid, matrix, RackLayout.STATUS_LEFT, mid - (top - bottom) * .30D,
                RackLayout.FACE - .0015D, RackLayout.STATUS_RIGHT,
                mid + (top - bottom) * .30D, 9, 20, 29);
        face(solid, matrix, RackLayout.STATUS_LEFT + .006D, mid - (top - bottom) * .24D,
                RackLayout.FACE - .002D, RackLayout.STATUS_RIGHT - .006D,
                mid + (top - bottom) * .24D, 44, 63, 76);
        lamp(solid, matrix, (RackLayout.STATUS_LEFT + RackLayout.STATUS_RIGHT) * .5D, mid,
                RackLayout.FACE - .0025D, ledSize * 1.25D,
                !module.powered() ? 218 : traffic ? 95 : anyLink ? 76 : 231,
                !module.powered() ? 50 : traffic ? 246 : anyLink ? 210 : 160,
                !module.powered() ? 60 : traffic ? 149 : anyLink ? 119 : 68);

        VertexConsumer panel = buffers.getBuffer(RenderType.entityCutoutNoCull(MODULE_TEXTURES.get(module.type())));
        texturedNorth(panel, matrix, RackLayout.BRAND_LEFT, bottom + .008D,
                RackLayout.FACE - .0018D, RackLayout.BRAND_RIGHT, top - .008D, packedLight);
    }

    private static void socket(VertexConsumer consumer, Matrix4f matrix, double x, double y,
                               double width, double height, boolean linked) {
        double hx = width * .5D;
        double hy = height * .5D;
        double shell = RackLayout.FACE - .004D;
        face(consumer, matrix, x - hx, y - hy, shell, x + hx, y + hy, 122, 132, 143);
        face(consumer, matrix, x - hx + .004D, y - hy + .004D, shell - .0012D,
                x + hx - .004D, y + hy - .004D, 74, 82, 92);
        double cx = hx - .009D;
        double cy = hy - .009D;
        face(consumer, matrix, x - cx, y - cy, shell - .0024D, x + cx, y + cy, 8, 11, 15);
        face(consumer, matrix, x - cx * .45D, y - cy, shell - .0034D,
                x + cx * .45D, y - cy + height * .22D, 16, 20, 26);
        face(consumer, matrix, x - cx * .72D, y + cy - height * .20D, shell - .0034D,
                x + cx * .72D, y + cy - height * .06D,
                linked ? 214 : 176, linked ? 172 : 140, linked ? 84 : 68);
    }

    private static int[] accent(RackModuleType type) {
        return switch (type) {
            case BROWSER -> new int[] {223, 58, 82};
            case SSH -> new int[] {61, 146, 238};
            case LOG -> new int[] {232, 156, 63};
            case DEVTOOLS -> new int[] {140, 99, 225};
            case SFTP -> new int[] {53, 184, 167};
            case TERMINAL -> new int[] {47, 174, 99};
            case REMOTE -> new int[] {198, 118, 62};
            case PROXY -> new int[] {206, 84, 196};
            case SWITCH -> new int[] {57, 184, 109};
            case MANAGED_SWITCH -> new int[] {57, 187, 211};
        };
    }

    private static void renderDoor(RackBlockEntity rack, int section, float angle, PoseStack pose,
                                   MultiBufferSource buffers, int packedLight, int packedOverlay) {
        ResourceLocation leaf = doorModel(rack, section);
        if (leaf == null) return;
        double[] hinge = RackShapes.RACK_FRAME_12U_HINGE;
        pose.pushPose();
        pose.translate(0D, section, 0D);
        pose.translate(hinge[0] / 16D, hinge[1] / 16D, hinge[2] / 16D);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-hinge[0] / 16D, -hinge[1] / 16D, -hinge[2] / 16D);
        Minecraft client = Minecraft.getInstance();
        BakedModel baked = client.getModelManager().getModel(leaf);
        for (RenderType type : new RenderType[] {RenderType.cutout(), RenderType.translucent()})
            client.getBlockRenderer().getModelRenderer().renderModel(pose.last(),
                    buffers.getBuffer(type), rack.getBlockState(), baked,
                    1F, 1F, 1F, packedLight, packedOverlay);
        pose.popPose();
    }

    private static ResourceLocation doorModel(RackBlockEntity rack, int section) {
        if (rack.getLevel() == null) return null;
        BlockState state = rack.getLevel().getBlockState(rack.getBlockPos().above(section));
        if (!(state.getBlock() instanceof RackFrameBlock frame)) return null;
        return switch (frame.role()) {
            case EXTENSION -> DOOR_18U;
            case STANDALONE -> DOOR_6U;
            default -> DOOR_12U;
        };
    }

    private static void texturedNorth(VertexConsumer consumer, Matrix4f matrix,
                                      double x0, double y0, double z,
                                      double x1, double y1, int packedLight) {
        texturedVertex(consumer, matrix, x0, y0, z, 1F, 1F, 0F, 0F, -1F, packedLight);
        texturedVertex(consumer, matrix, x0, y1, z, 1F, 0F, 0F, 0F, -1F, packedLight);
        texturedVertex(consumer, matrix, x1, y1, z, 0F, 0F, 0F, 0F, -1F, packedLight);
        texturedVertex(consumer, matrix, x1, y0, z, 0F, 1F, 0F, 0F, -1F, packedLight);
    }

    private static void texturedSide(VertexConsumer consumer, Matrix4f matrix,
                                     double x, double y0, double z0,
                                     double y1, double z1, int packedLight) {
        texturedVertex(consumer, matrix, x, y0, z0, 0F, 1F, -1F, 0F, 0F, packedLight);
        texturedVertex(consumer, matrix, x, y1, z0, 0F, 0F, -1F, 0F, 0F, packedLight);
        texturedVertex(consumer, matrix, x, y1, z1, 1F, 0F, -1F, 0F, 0F, packedLight);
        texturedVertex(consumer, matrix, x, y0, z1, 1F, 1F, -1F, 0F, 0F, packedLight);
    }

    private static void texturedVertex(VertexConsumer consumer, Matrix4f matrix,
                                       double x, double y, double z, float u, float v,
                                       float nx, float ny, float nz, int packedLight) {
        consumer.vertex(matrix, (float) x, (float) y, (float) z)
                .color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight).normal(nx, ny, nz).endVertex();
    }

    private static void lamp(VertexConsumer consumer, Matrix4f matrix,
                             double x, double y, double z, double radius,
                             int red, int green, int blue) {
        face(consumer, matrix, x - radius * 1.35D, y - radius * 1.35D, z + .0005D,
                x + radius * 1.35D, y + radius * 1.35D,
                red / 3, green / 3, blue / 3);
        face(consumer, matrix, x - radius, y - radius, z,
                x + radius, y + radius, red, green, blue);
    }

    private static void cuboid(VertexConsumer consumer, Matrix4f matrix,
                               double x0, double y0, double z0, double x1, double y1, double z1,
                               int red, int green, int blue) {
        vertex(consumer, matrix, x0, y0, z0, red, green, blue);
        vertex(consumer, matrix, x0, y1, z0, red, green, blue);
        vertex(consumer, matrix, x1, y1, z0, red, green, blue);
        vertex(consumer, matrix, x1, y0, z0, red, green, blue);
        vertex(consumer, matrix, x1, y0, z1, red / 2, green / 2, blue / 2);
        vertex(consumer, matrix, x1, y1, z1, red / 2, green / 2, blue / 2);
        vertex(consumer, matrix, x0, y1, z1, red / 2, green / 2, blue / 2);
        vertex(consumer, matrix, x0, y0, z1, red / 2, green / 2, blue / 2);
        vertex(consumer, matrix, x0, y1, z0, red + 8, green + 8, blue + 8);
        vertex(consumer, matrix, x0, y1, z1, red + 8, green + 8, blue + 8);
        vertex(consumer, matrix, x1, y1, z1, red + 8, green + 8, blue + 8);
        vertex(consumer, matrix, x1, y1, z0, red + 8, green + 8, blue + 8);
        vertex(consumer, matrix, x0, y0, z1, red / 2, green / 2, blue / 2);
        vertex(consumer, matrix, x0, y1, z1, red / 2, green / 2, blue / 2);
        vertex(consumer, matrix, x0, y1, z0, red / 2, green / 2, blue / 2);
        vertex(consumer, matrix, x0, y0, z0, red / 2, green / 2, blue / 2);
        vertex(consumer, matrix, x1, y0, z0, red / 2, green / 2, blue / 2);
        vertex(consumer, matrix, x1, y1, z0, red / 2, green / 2, blue / 2);
        vertex(consumer, matrix, x1, y1, z1, red / 2, green / 2, blue / 2);
        vertex(consumer, matrix, x1, y0, z1, red / 2, green / 2, blue / 2);
    }

    private static void face(VertexConsumer consumer, Matrix4f matrix,
                             double x0, double y0, double z,
                             double x1, double y1, int red, int green, int blue) {
        vertex(consumer, matrix, x0, y0, z, red, green, blue);
        vertex(consumer, matrix, x0, y1, z, red, green, blue);
        vertex(consumer, matrix, x1, y1, z, red, green, blue);
        vertex(consumer, matrix, x1, y0, z, red, green, blue);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix,
                               double x, double y, double z,
                               int red, int green, int blue) {
        consumer.vertex(matrix, (float) x, (float) y, (float) z).color(red, green, blue, 255).endVertex();
    }
}
