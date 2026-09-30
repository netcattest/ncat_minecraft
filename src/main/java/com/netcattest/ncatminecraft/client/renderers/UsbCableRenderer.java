package com.netcattest.ncatminecraft.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.netcattest.ncatminecraft.block.KeyboardBlockLeft;
import com.netcattest.ncatminecraft.block.ScreenBlock;
import com.netcattest.ncatminecraft.entity.KeyboardBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.entity.UsbPort;
import com.netcattest.ncatminecraft.registry.ItemRegistry;
import com.netcattest.ncatminecraft.utilities.UsbCableService;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UsbCableRenderer {
    private static final Map<UUID, CachedPath> PATHS = new HashMap<>();

    private UsbCableRenderer() { }

    public static void render(ScreenBlockEntity entity, PoseStack pose, MultiBufferSource buffers) {
        Level level = entity.getLevel();
        if (level == null) return;
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = pose.last().pose();
        BlockPos origin = entity.getBlockPos();
        Vec3 offset = Vec3.atLowerCornerOf(origin);
        ItemStack heldCable = selectedCable();
        for (int i = 0; i < entity.screenCount(); i++) {
            ScreenData screen = entity.getScreen(i);
            for (UsbPort port : UsbCableService.ports(screen)) {
                Vec3 socket = UsbCableService.worldPosition(origin, screen, port);
                Vec3 normal = normal(port.automatic ? UsbCableService.automaticFace(screen) : port.face);
                socket(vertices, matrix, socket.subtract(offset), normal, port.connected());
                if (!heldCable.isEmpty() && heldCable.getTag() != null &&
                        heldCable.getTag().hasUUID("UsbStartPort") &&
                        heldCable.getTag().getUUID("UsbStartPort").equals(port.id) &&
                        heldCable.getTag().getLong("UsbStartPos") == origin.asLong() &&
                        heldCable.getTag().getByte("UsbStartSide") == screen.side.ordinal() &&
                        heldCable.getTag().getString("UsbStartDimension").equals(level.dimension().location().toString()))
                    preview(vertices, matrix, socket.add(normal.scale(.11)), offset);
                if (!port.connected() || !level.hasChunkAt(port.keyboard)) continue;
                KeyboardBlockEntity keyboard = UsbCableService.keyboard(level, port.keyboard);
                if (keyboard == null || !port.id.equals(keyboard.usbPortId()) || !UsbCableService.valid(keyboard)) continue;
                Vec3 keySocket = UsbCableService.keyboardPosition(keyboard);
                if (socket.distanceToSqr(keySocket) > UsbCableService.MAX_LENGTH * UsbCableService.MAX_LENGTH) continue;
                Vec3 start = socket.add(normal.scale(.11));
                Vec3 end = keySocket;
                plug(vertices, matrix, socket.subtract(offset), normal);
                Vec3 keyboardNormal = normal(keyboard.getBlockState().getValue(KeyboardBlockLeft.FACING));
                plug(vertices, matrix, keySocket.subtract(offset), keyboardNormal);
                List<Vec3> route = path(level, port, start, end, normal, keyboardNormal);
                for (int segment = 1; segment < route.size(); segment++) {
                    Vec3 a = route.get(segment - 1).subtract(offset);
                    Vec3 b = route.get(segment).subtract(offset);
                    tube(vertices, matrix, a, b, .014, 35, 38, 47);
                    if (segment % 13 == 0) tube(vertices, matrix, a, b, .0148, 38, 126, 221);
                }
            }
        }
    }

    public static void renderKeyboard(KeyboardBlockEntity keyboard, PoseStack pose, MultiBufferSource buffers) {
        if (keyboard.getLevel() == null) return;
        VertexConsumer vertices = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = pose.last().pose();
        Vec3 center = UsbCableService.keyboardPosition(keyboard).subtract(Vec3.atLowerCornerOf(keyboard.getBlockPos()));
        Vec3 normal = normal(keyboard.getBlockState().getValue(KeyboardBlockLeft.FACING));
        socket(vertices, matrix, center, normal, keyboard.usbLinked());
        ItemStack heldCable = selectedCable();
        CompoundTag tag = heldCable.getTag();
        if (tag != null && tag.contains("UsbStartKeyboard") &&
                tag.getLong("UsbStartKeyboard") == keyboard.getBlockPos().asLong() &&
                tag.getString("UsbStartDimension").equals(keyboard.getLevel().dimension().location().toString()))
            preview(vertices, matrix, UsbCableService.keyboardPosition(keyboard).add(normal.scale(.11)),
                    Vec3.atLowerCornerOf(keyboard.getBlockPos()));
    }

    private static ItemStack selectedCable() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return ItemStack.EMPTY;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(ItemRegistry.USB_CABLE.get()) && stack.getTag() != null) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void preview(VertexConsumer vertices, Matrix4f matrix, Vec3 start, Vec3 origin) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        Vec3 look = client.player.getLookAngle();
        Vec3 hand = client.player.getEyePosition().add(look.scale(.5)).add(0, -.36, 0);
        if (start.distanceToSqr(hand) > UsbCableService.MAX_LENGTH * UsbCableService.MAX_LENGTH) return;
        List<Vec3> ghost = CablePhysics.drape(client.level, start, hand, List.of(), null, null, 0);
        for (int i = 1; i < ghost.size(); i++)
            tube(vertices, matrix, ghost.get(i - 1).subtract(origin), ghost.get(i).subtract(origin),
                    .013, 46, 132, 198);
    }

    private static List<Vec3> path(Level level, UsbPort port, Vec3 start, Vec3 end, Vec3 startNormal, Vec3 endNormal) {
        return CablePhysics.drape(level, start, end, port.waypoints, startNormal, endNormal, .11D);
    }

    private static boolean nearGround(Level level, Vec3 point) {
        BlockPos at = BlockPos.containing(point);
        if (!level.hasChunkAt(at)) return false;
        return Math.abs(surfaceHeight(level, point.x, point.z, new HashMap<>()) - point.y) < 2.5;
    }

    private static int surfaceHeight(Level level, double x, double z, Map<Long, Integer> cache) {
        int highest = level.getMinBuildHeight();
        int[] xs = {(int) Math.floor(x - .18), (int) Math.floor(x + .18)};
        int[] zs = {(int) Math.floor(z - .18), (int) Math.floor(z + .18)};
        for (int blockX : xs) for (int blockZ : zs) {
            BlockPos at = new BlockPos(blockX, 0, blockZ);
            if (!level.hasChunkAt(at)) continue;
            long key = ((long) blockX << 32) ^ (blockZ & 0xffffffffL);
            int height = cache.computeIfAbsent(key, ignored -> {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
                while (y > level.getMinBuildHeight()) {
                    BlockPos upper = new BlockPos(blockX, y - 1, blockZ);
                    if (!(level.getBlockState(upper).getBlock() instanceof ScreenBlock) &&
                            UsbCableService.keyboard(level, upper) == null) break;
                    y--;
                }
                return y;
            });
            highest = Math.max(highest, height);
        }
        return highest;
    }

    private static double clearCollision(Level level, double x, double y, double z) {
        double current = y;
        for (int attempt = 0; attempt < 16; attempt++) {
            BlockPos block = BlockPos.containing(x, current, z);
            if (!level.hasChunkAt(block)) break;
            if (level.getBlockState(block).getBlock() instanceof ScreenBlock || UsbCableService.keyboard(level, block) != null) break;
            VoxelShape shape = level.getBlockState(block).getCollisionShape(level, block);
            if (shape.isEmpty()) break;
            AABB bounds = shape.bounds();
            double lx = x - block.getX(), ly = current - block.getY(), lz = z - block.getZ();
            if (lx < bounds.minX - .035 || lx > bounds.maxX + .035 ||
                    lz < bounds.minZ - .035 || lz > bounds.maxZ + .035 ||
                    ly < bounds.minY - .035 || ly >= bounds.maxY + .075) break;
            double above = block.getY() + bounds.maxY + .08;
            if (above <= current) break;
            current = above;
        }
        return current;
    }

    private static void socket(VertexConsumer vertices, Matrix4f matrix, Vec3 center, Vec3 forward, boolean occupied) {
        Vec3 right = basis(forward);
        Vec3 up = forward.cross(right).normalize();
        box(vertices, matrix, center.subtract(forward.scale(.006)), right, up, forward,
                .082, .050, .026, 46, 52, 60);
        box(vertices, matrix, center.add(forward.scale(.012)), right, up, forward,
                .074, .042, .016, 168, 176, 188);
        box(vertices, matrix, center.add(forward.scale(.026)), right, up, forward,
                .070, .038, .004, 196, 203, 212);
        box(vertices, matrix, center.add(forward.scale(.029)), right, up, forward,
                .058, .028, .004, 14, 19, 26);
        for (int side = -1; side <= 1; side += 2)
            box(vertices, matrix, center.add(right.scale(side * .040)).add(forward.scale(.024)),
                    right, up, forward, .008, .012, .006, 132, 140, 152);
        Vec3 tongue = center.add(forward.scale(.030)).subtract(up.scale(.009));
        box(vertices, matrix, tongue, right, up, forward, .050, .011, .006,
                occupied ? 26 : 224, occupied ? 34 : 228, occupied ? 44 : 234);
        for (int pin = 0; pin < 4; pin++)
            box(vertices, matrix, tongue.add(right.scale((pin - 1.5) * .024)).add(forward.scale(.005)),
                    right, up, forward, .007, .008, .002, 206, 168, 82);
        box(vertices, matrix, center.add(up.scale(.036)).add(forward.scale(.020)),
                right, up, forward, .046, .004, .004, 120, 128, 140);
        box(vertices, matrix, center.add(right.scale(.062)).subtract(up.scale(.036)).add(forward.scale(.024)),
                right, up, forward, .009, .009, .004,
                occupied ? 74 : 62, occupied ? 220 : 70, occupied ? 255 : 80);
    }

    private static void plug(VertexConsumer vertices, Matrix4f matrix, Vec3 center, Vec3 forward) {
        Vec3 right = basis(forward);
        Vec3 up = forward.cross(right).normalize();
        box(vertices, matrix, center.add(forward.scale(.078)), right, up, forward, .046, .025, .045, 159, 169, 180);
        box(vertices, matrix, center.add(forward.scale(.122)), right, up, forward, .055, .033, .022, 28, 37, 47);
    }

    private static void tube(VertexConsumer vertices, Matrix4f matrix, Vec3 a, Vec3 b, double radius,
                             int red, int green, int blue) {
        Vec3 delta = b.subtract(a);
        if (delta.lengthSqr() < .00001) return;
        Vec3 tangent = delta.normalize();
        Vec3 right = basis(tangent).scale(radius);
        Vec3 up = tangent.cross(right).normalize().scale(radius);
        Vec3[] first = {a.add(right).add(up), a.add(right).subtract(up), a.subtract(right).subtract(up), a.subtract(right).add(up)};
        Vec3[] second = {b.add(right).add(up), b.add(right).subtract(up), b.subtract(right).subtract(up), b.subtract(right).add(up)};
        for (int i = 0; i < 4; i++) quad(vertices, matrix, first[i], first[(i + 1) & 3],
                second[(i + 1) & 3], second[i], red, green, blue);
    }

    private static void box(VertexConsumer vertices, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up,
                            Vec3 forward, double width, double height, double depth, int red, int green, int blue) {
        Vec3 r = right.scale(width), u = up.scale(height), f = forward.scale(depth);
        Vec3 a = center.subtract(r).subtract(u).subtract(f), b = center.add(r).subtract(u).subtract(f);
        Vec3 c = center.add(r).add(u).subtract(f), d = center.subtract(r).add(u).subtract(f);
        Vec3 e = center.subtract(r).subtract(u).add(f), g = center.add(r).subtract(u).add(f);
        Vec3 h = center.add(r).add(u).add(f), i = center.subtract(r).add(u).add(f);
        quad(vertices, matrix, e, g, h, i, red, green, blue);
        quad(vertices, matrix, b, a, d, c, red / 2, green / 2, blue / 2);
        quad(vertices, matrix, a, e, i, d, red * 3 / 4, green * 3 / 4, blue * 3 / 4);
        quad(vertices, matrix, g, b, c, h, red * 3 / 4, green * 3 / 4, blue * 3 / 4);
        quad(vertices, matrix, d, i, h, c, red, green, blue);
        quad(vertices, matrix, a, b, g, e, red / 2, green / 2, blue / 2);
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             int red, int green, int blue) {
        vertex(vertices, matrix, a, red, green, blue);
        vertex(vertices, matrix, b, red, green, blue);
        vertex(vertices, matrix, c, red, green, blue);
        vertex(vertices, matrix, d, red, green, blue);
    }

    private static void vertex(VertexConsumer vertices, Matrix4f matrix, Vec3 value, int red, int green, int blue) {
        vertices.vertex(matrix, (float) value.x, (float) value.y, (float) value.z)
                .color(red, green, blue, 255).endVertex();
    }

    private static Vec3 basis(Vec3 direction) {
        Vec3 basis = direction.cross(new Vec3(0, 1, 0));
        if (basis.lengthSqr() < .0001) basis = direction.cross(new Vec3(1, 0, 0));
        return basis.normalize();
    }

    private static Vec3 normal(Direction face) {
        return Vec3.atLowerCornerOf(face.getNormal());
    }

    private record CachedPath(Level level, int fingerprint, long updatedAt, List<Vec3> points) { }
}
