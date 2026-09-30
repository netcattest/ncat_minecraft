package com.netcattest.ncatminecraft.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.netcattest.ncatminecraft.block.ManagedTabletBlock;
import com.netcattest.ncatminecraft.block.NetworkSwitchBlock;
import com.netcattest.ncatminecraft.entity.ManagedSwitchBlockEntity;
import com.netcattest.ncatminecraft.entity.ManagedTabletBlockEntity;
import com.netcattest.ncatminecraft.utilities.SerialCableService;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class SerialCableRenderer {
    private SerialCableRenderer() { }

    public static void renderTablet(ManagedTabletBlockEntity tablet, PoseStack pose,
                                    MultiBufferSource buffers) {
        Level level = tablet.getLevel();
        ManagedSwitchBlockEntity networkSwitch = SerialCableService.connectedSwitch(level, tablet);
        if (networkSwitch == null) return;
        Direction tabletFace = tablet.getBlockState().getValue(ManagedTabletBlock.FACING).getClockWise();
        Direction switchFace = networkSwitch.getBlockState().getValue(NetworkSwitchBlock.FACING).getClockWise();
        Vec3 start = tablet.serialPortPosition();
        Vec3 end = SerialCableService.switchPortPosition(networkSwitch);
        Vec3 startOutside = start.add(Vec3.atLowerCornerOf(tabletFace.getNormal()).scale(.16D));
        Vec3 endOutside = end.add(Vec3.atLowerCornerOf(switchFace.getNormal()).scale(.16D));
        if (startOutside.distanceToSqr(endOutside) >
                (SerialCableService.MAX_LENGTH + .5D) * (SerialCableService.MAX_LENGTH + .5D)) return;
        List<Vec3> path = route(level, startOutside, endOutside);
        Vec3 origin = Vec3.atLowerCornerOf(tablet.getBlockPos());
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = pose.last().pose();
        tube(consumer, matrix, start.subtract(origin), startOutside.subtract(origin), .026D, 50, 68, 80);
        tube(consumer, matrix, endOutside.subtract(origin), end.subtract(origin), .026D, 50, 68, 80);
        for (int i = 1; i < path.size(); i++) {
            Vec3 a = path.get(i - 1).subtract(origin);
            Vec3 b = path.get(i).subtract(origin);
            tube(consumer, matrix, a, b, .016D, 31, 61, 78);
            if (i % 9 == 1) tube(consumer, matrix, a, b, .0168D, 50, 177, 205);
        }
    }

    private static List<Vec3> route(Level level, Vec3 start, Vec3 end) {
        return CablePhysics.drape(level, start, end, List.of(), null, null, 0);
    }

    private static double clearCollision(Level level, double x, double initialY, double z) {
        double y = initialY;
        for (int attempt = 0; attempt < 16; attempt++) {
            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level.hasChunkAt(pos)) break;
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (shape.isEmpty()) break;
            AABB bounds = shape.bounds();
            double localX = x - pos.getX();
            double localY = y - pos.getY();
            double localZ = z - pos.getZ();
            if (localX < bounds.minX - .04D || localX > bounds.maxX + .04D ||
                    localZ < bounds.minZ - .04D || localZ > bounds.maxZ + .04D ||
                    localY < bounds.minY - .04D || localY >= bounds.maxY + .08D) break;
            y = pos.getY() + bounds.maxY + .08D;
        }
        return y;
    }

    private static void tube(VertexConsumer consumer, Matrix4f matrix, Vec3 a, Vec3 b,
                             double radius, int red, int green, int blue) {
        Vec3 direction = b.subtract(a);
        if (direction.lengthSqr() < .000001D) return;
        Vec3 axis = direction.normalize();
        Vec3 right = axis.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < .0001D) right = axis.cross(new Vec3(1, 0, 0));
        right = right.normalize().scale(radius);
        Vec3 up = axis.cross(right).normalize().scale(radius);
        Vec3[] near = {a.add(right).add(up), a.add(right).subtract(up),
                a.subtract(right).subtract(up), a.subtract(right).add(up)};
        Vec3[] far = {b.add(right).add(up), b.add(right).subtract(up),
                b.subtract(right).subtract(up), b.subtract(right).add(up)};
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
                .color(red, green, blue, 255).endVertex();
    }
}
