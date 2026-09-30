package com.netcattest.ncatminecraft.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.netcattest.ncatminecraft.block.ManagedTabletBlock;
import com.netcattest.ncatminecraft.entity.ManagedTabletBlockEntity;
import com.netcattest.ncatminecraft.entity.ManagedSwitchBlockEntity;
import com.netcattest.ncatminecraft.utilities.SerialCableService;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import java.util.Map;
import java.util.WeakHashMap;

public final class ManagedTabletRenderer implements BlockEntityRenderer<ManagedTabletBlockEntity> {
    private static final ResourceLocation DISPLAY = new ResourceLocation(
            "ncat_minecraft", "textures/block/managed_tablet/screen.png");
    private static final Map<ManagedSwitchBlockEntity, SeenCache> SEEN = new WeakHashMap<>();

    private record SeenCache(long tick, int count) { }

    public ManagedTabletRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(ManagedTabletBlockEntity tablet, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!tablet.getBlockState().getValue(ManagedTabletBlock.DOCKED)) {
            SerialCableRenderer.renderTablet(tablet, poseStack, buffers);
            return;
        }
        Direction facing = tablet.getBlockState().getValue(ManagedTabletBlock.FACING);
        poseStack.pushPose();
        poseStack.translate(.5D, 0D, .5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(switch (facing) {
            case EAST -> -90F;
            case SOUTH -> 180F;
            case WEST -> 90F;
            default -> 0F;
        }));
        poseStack.translate(-.5D, 0D, -.5D);
        poseStack.translate(.5D, .25D, .5D);
        poseStack.mulPose(Axis.XP.rotationDegrees(-22.5F));
        poseStack.translate(-.5D, -.25D, -.5D);

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer display = buffers.getBuffer(RenderType.entityCutout(DISPLAY));
        displayVertex(display, matrix, 2.35D, 4.083D, 2.5D, 1F, 1F);
        displayVertex(display, matrix, 2.35D, 4.083D, 13.5D, 1F, 0F);
        displayVertex(display, matrix, 13.65D, 4.083D, 13.5D, 0F, 0F);
        displayVertex(display, matrix, 13.65D, 4.083D, 2.5D, 0F, 1F);
        BlockPos switchPos = tablet.linkedSwitchPos();
        ManagedSwitchBlockEntity networkSwitch = SerialCableService.connectedSwitch(tablet.getLevel(), tablet);
        boolean linked = networkSwitch != null;
        topQuad(buffers, matrix, px(229D), 4.095D, py(20D), px(238D), py(29D),
                linked ? 58 : 233, linked ? 211 : 145, linked ? 219 : 67);

        int panel = switch (tablet.activePanel()) {
            case "ports" -> 1;
            case "policies" -> 2;
            case "diagnostics" -> 3;
            default -> 0;
        };
        double x0 = px(15D + panel * 59D);
        double x1 = px(61D + panel * 59D);
        double z0 = py(67D);
        topQuad(buffers, matrix, x0, 4.095D, z0, x1, z0 - .11D,
                72, 218, 245);
        boolean portuguese = Minecraft.getInstance().getLanguageManager().getSelected()
                .toLowerCase().startsWith("pt");
        label(poseStack, buffers, portuguese ? "// SWITCH GERENCIÁVEL" : "// MANAGED SWITCH",
                93, 21, 0xFF4CC9DF);
        String[] tabs = portuguese ? new String[] {"GERAL", "PORTAS", "REGRAS", "ESTADO"} :
                new String[] {"HOME", "PORTS", "RULES", "STATUS"};
        for (int i = 0; i < tabs.length; i++)
            label(poseStack, buffers, tabs[i], 15 + i * 59, 53, 0xFF91B5C6);
        renderLivePanel(tablet, networkSwitch, poseStack, buffers, matrix);
        if (networkSwitch != null) {
            for (int port = 0; port < 8; port++) {
                int column = port % 4;
                int row = port / 4;
                double pixelX = 57.0D + column * 59.0D;
                double pixelY = 129.0D + row * 44.0D;
                double markerX = px(pixelX);
                double markerZ = py(pixelY);
                boolean connected = networkSwitch.activeLink(port);
                boolean enabled = networkSwitch.portEnabled(port) && networkSwitch.powered();
                boolean traffic = enabled && networkSwitch.trafficLight(port);
                topQuad(buffers, matrix, markerX, 4.098D, markerZ,
                        markerX - .16D, markerZ - .16D,
                        traffic ? 91 : connected && enabled ? 65 : connected ? 217 : 77,
                        traffic ? 246 : connected && enabled ? 207 : connected ? 86 : 117,
                        traffic ? 190 : connected && enabled ? 151 : connected ? 101 : 135);
            }
        }
        poseStack.popPose();
        SerialCableRenderer.renderTablet(tablet, poseStack, buffers);
    }

    private static void renderLivePanel(ManagedTabletBlockEntity tablet,
                                        ManagedSwitchBlockEntity networkSwitch,
                                        PoseStack pose, MultiBufferSource buffers, Matrix4f matrix) {
        topQuad(buffers, matrix, px(15), 4.101D, py(76), px(241), py(215),
                8, 23, 37);
        boolean portuguese = Minecraft.getInstance().getLanguageManager().getSelected().toLowerCase().startsWith("pt");
        if (networkSwitch == null) {
            label(pose, buffers, portuguese ? "CABO SERIAL DESCONECTADO" : "SERIAL CABLE DISCONNECTED",
                    23, 94, 0xFF8DCBDD);
            label(pose, buffers, portuguese ? "Conecte as duas pontas." : "Connect both ends.",
                    23, 113, 0xFFC2D7E0);
            return;
        }
        String panel = tablet.activePanel();
        if ("ports".equals(panel)) {
            label(pose, buffers, portuguese ? "PORTAS ETHERNET" : "ETHERNET PORTS",
                    23, 83, 0xFF61DDF1);
            for (int i = 0; i < 8; i++) {
                int col = i % 4, row = i / 4;
                int x = 22 + col * 58, y = 111 + row * 44;
                topQuad(buffers, matrix, px(x - 4), 4.104D, py(y - 5),
                        px(x + 48), py(y + 32), 20, 48, 66);
                label(pose, buffers, "eth" + i, x, y, 0xFFECF6F9);
                label(pose, buffers, "V" + networkSwitch.portVlan(i) +
                        (networkSwitch.portTrunk(i) ? " T" : ""),
                        x, y + 14, networkSwitch.portEnabled(i) ? 0xFF8DCBDD : 0xFFFF697C);
            }
            return;
        }
        if ("policies".equals(panel)) {
            label(pose, buffers, portuguese ? "MATRIZ DE ACESSO" : "ACCESS MATRIX",
                    23, 83, 0xFF61DDF1);
            for (int i = 0; i < 8; i++) {
                int blocked = 0;
                for (int j = 0; j < 8; j++) if (i != j && !networkSwitch.pairAllowed(i, j)) blocked++;
                String line = "eth" + i + "  " + (portuguese ? "bloqueios: " : "blocked: ") + blocked +
                        (networkSwitch.portIsolated(i) ? "  ISO" : "");
                label(pose, buffers, line, 23 + (i / 4) * 111, 105 + (i % 4) * 23,
                        blocked > 0 ? 0xFFFFA6AD : 0xFFB4EBD3);
            }
            return;
        }
        if ("diagnostics".equals(panel)) {
            label(pose, buffers, portuguese ? "DIAGNÓSTICO" : "DIAGNOSTICS",
                    23, 83, 0xFF61DDF1);
            for (int i = 0; i < 8; i++) {
                String line = "eth" + i + "  " + (networkSwitch.activeLink(i) ? "LINK" : "----") +
                        "  " + networkSwitch.portEvents(i);
                label(pose, buffers, line, 23 + (i / 4) * 111, 105 + (i % 4) * 23,
                        networkSwitch.activeLink(i) && networkSwitch.portEnabled(i)
                                ? 0xFFB4EBD3 : 0xFF91A8B7);
            }
            return;
        }
        label(pose, buffers, portuguese ? "VISÃO GERAL" : "OVERVIEW",
                23, 83, 0xFF61DDF1);
        label(pose, buffers, networkSwitch.powered() ?
                        (portuguese ? "SISTEMA ATIVO" : "SYSTEM ACTIVE") :
                        (portuguese ? "SISTEMA DESLIGADO" : "SYSTEM OFFLINE"),
                23, 108, networkSwitch.powered() ? 0xFF80E8AE : 0xFFFF8295);
        label(pose, buffers, (portuguese ? "Portas: " : "Ports: ") +
                        networkSwitch.connectedCount() + "/8", 23, 130, 0xFFE5F3F7);
        label(pose, buffers, (portuguese ? "Revisão: " : "Revision: ") +
                        networkSwitch.configurationRevision(), 23, 152, 0xFFB1CBD8);
        int seen = screenCount(networkSwitch);
        label(pose, buffers, (portuguese ? "Telas vistas: " : "Screens seen: ") + seen,
                23, 174, 0xFFB1CBD8);
    }

    private static int screenCount(ManagedSwitchBlockEntity networkSwitch) {
        if (networkSwitch.getLevel() == null) return 0;
        long tick = networkSwitch.getLevel().getGameTime();
        SeenCache cached = SEEN.get(networkSwitch);
        if (cached != null && tick >= cached.tick() && tick - cached.tick() < 20) return cached.count();
        int count = 0;
        for (int i = 0; i < 8; i++)
            count += DataCableService.physicalScreensBehindSwitchPort(
                    networkSwitch.getLevel(), networkSwitch, i).size();
        SEEN.put(networkSwitch, new SeenCache(tick, count));
        return count;
    }

    private static double px(double pixel) { return 13.65D - pixel * 11.3D / 256D; }

    private static double py(double pixel) { return 13.5D - pixel * 11.0D / 256D; }

    private static void label(PoseStack pose, MultiBufferSource buffers, String value,
                              int x, int y, int color) {
        Font font = Minecraft.getInstance().font;
        pose.pushPose();
        pose.translate(px(x) / 16D, 4.122D / 16D, py(y) / 16D);
        pose.mulPose(Axis.XP.rotationDegrees(-90F));
        pose.scale(-.00275F, .00268F, .00275F);
        font.drawInBatch(value, 0F, 0F, color, false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    private static void displayVertex(VertexConsumer consumer, Matrix4f matrix,
                                      double x, double y, double z, float u, float v) {
        consumer.vertex(matrix, (float) (x / 16D), (float) (y / 16D), (float) (z / 16D))
                .color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(0F, 1F, 0F).endVertex();
    }

    private static void topQuad(MultiBufferSource buffers, Matrix4f matrix,
                                double x0, double y, double z0, double x1, double z1,
                                int red, int green, int blue) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        consumer.vertex(matrix, (float) (x0 / 16D), (float) (y / 16D), (float) (z0 / 16D))
                .color(red, green, blue, 255).endVertex();
        consumer.vertex(matrix, (float) (x0 / 16D), (float) (y / 16D), (float) (z1 / 16D))
                .color(red, green, blue, 255).endVertex();
        consumer.vertex(matrix, (float) (x1 / 16D), (float) (y / 16D), (float) (z1 / 16D))
                .color(red, green, blue, 255).endVertex();
        consumer.vertex(matrix, (float) (x1 / 16D), (float) (y / 16D), (float) (z0 / 16D))
                .color(red, green, blue, 255).endVertex();
    }
}
