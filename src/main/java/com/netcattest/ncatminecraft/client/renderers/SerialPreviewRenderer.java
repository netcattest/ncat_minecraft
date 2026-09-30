package com.netcattest.ncatminecraft.client.renderers;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.netcattest.ncatminecraft.entity.ManagedSwitchBlockEntity;
import com.netcattest.ncatminecraft.entity.ManagedTabletBlockEntity;
import com.netcattest.ncatminecraft.item.ItemSerialCable;
import com.netcattest.ncatminecraft.utilities.SerialCableService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.List;

@Mod.EventBusSubscriber(modid = "ncat_minecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SerialPreviewRenderer {
    private static final double HAND_REACH = 1.9D;
    private static final double RADIUS = .016D;

    private static final MultiBufferSource.BufferSource GHOST =
            MultiBufferSource.immediate(new BufferBuilder(2048));

    private SerialPreviewRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)
            return;
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        Level level = client.level;
        if (player == null || level == null)
            return;
        ItemStack stack = held(player);
        if (stack == null)
            return;
        BlockPos pending = ItemSerialCable.pendingPos(stack);
        if (pending == null || !level.hasChunkAt(pending))
            return;
        Vec3 socket = socket(level, pending, ItemSerialCable.pendingIsTablet(stack));
        if (socket == null)
            return;
        float partial = event.getPartialTick();
        Vec3 hand = player.getEyePosition(partial)
                .add(player.getViewVector(partial).scale(HAND_REACH))
                .subtract(0.0D, .22D, 0.0D);
        double span = socket.distanceTo(hand);
        boolean reach = span <= SerialCableService.MAX_LENGTH;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        List<Vec3> path = CablePhysics.drape(level, socket, hand, List.of(), null, null, 0);
        VertexConsumer consumer = GHOST.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = pose.last().pose();
        for (int i = 1; i < path.size(); i++)
            tube(consumer, matrix, path.get(i - 1), path.get(i), RADIUS,
                    reach ? 58 : 216, reach ? 178 : 74, reach ? 206 : 70);
        pose.popPose();
        GHOST.endBatch();
    }

    private static ItemStack held(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof ItemSerialCable)
                return stack;
        }
        return null;
    }

    private static Vec3 socket(Level level, BlockPos pos, boolean tablet) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (tablet && entity instanceof ManagedTabletBlockEntity managed)
            return managed.serialPortPosition();
        if (!tablet && entity instanceof ManagedSwitchBlockEntity managed)
            return SerialCableService.switchPortPosition(managed);
        return null;
    }

    private static void tube(VertexConsumer consumer, Matrix4f matrix, Vec3 a, Vec3 b, double radius,
                             int red, int green, int blue) {
        Vec3 delta = b.subtract(a);
        if (delta.lengthSqr() < 1.0E-6D)
            return;
        Vec3 tangent = delta.normalize();
        Vec3 side = tangent.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1.0E-4D)
            side = tangent.cross(new Vec3(1, 0, 0));
        side = side.normalize().scale(radius);
        Vec3 up = tangent.cross(side).normalize().scale(radius);
        Vec3[] near = {a.add(side).add(up), a.add(side).subtract(up),
                a.subtract(side).subtract(up), a.subtract(side).add(up)};
        Vec3[] far = {b.add(side).add(up), b.add(side).subtract(up),
                b.subtract(side).subtract(up), b.subtract(side).add(up)};
        for (int i = 0; i < 4; i++) {
            int next = (i + 1) & 3;
            vertex(consumer, matrix, near[i], red, green, blue);
            vertex(consumer, matrix, near[next], red, green, blue);
            vertex(consumer, matrix, far[next], red, green, blue);
            vertex(consumer, matrix, far[i], red, green, blue);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 point,
                               int red, int green, int blue) {
        consumer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(red, green, blue, 235).endVertex();
    }
}
