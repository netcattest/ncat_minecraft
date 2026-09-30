package com.netcattest.ncatminecraft.client.renderers;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class CableMesh {
    private static final int SIDES = 8;
    private static final Vec3 LIGHT = new Vec3(.38D, 1.0D, .52D).normalize();
    private static final double AMBIENT = .46D;
    private static final double DIFFUSE = .56D;
    private static final double SHINE = .28D;
    private static final double MIN_STEP = 1.0E-4D;

    private CableMesh() {
    }

    public static float brightness(Level level, Vec3 point) {
        if (level == null) return 1.0F;
        BlockPos pos = BlockPos.containing(point);
        if (!level.hasChunkAt(pos)) return 1.0F;
        int raw = level.getMaxLocalRawBrightness(pos);
        return .22F + .78F * Math.max(0, Math.min(15, raw)) / 15.0F;
    }

    public static float[] brightness(Level level, List<Vec3> points) {
        float[] result = new float[points.size()];
        float last = 1.0F;
        for (int i = 0; i < points.size(); i++) {
            if (i % 6 == 0 || i == points.size() - 1) last = brightness(level, points.get(i));
            result[i] = last;
        }
        return result;
    }

    public static List<Vec3> clean(List<Vec3> points) {
        List<Vec3> result = new ArrayList<>(points.size());
        for (Vec3 point : points)
            if (result.isEmpty() || result.get(result.size() - 1).distanceToSqr(point) > MIN_STEP * MIN_STEP)
                result.add(point);
        return result;
    }

    public static List<Vec3> rounded(List<Vec3> corners, double radius, int steps) {
        List<Vec3> points = clean(corners);
        if (points.size() < 3 || radius <= 0) return points;
        List<Vec3> result = new ArrayList<>();
        result.add(points.get(0));
        for (int i = 1; i < points.size() - 1; i++) {
            Vec3 previous = points.get(i - 1);
            Vec3 corner = points.get(i);
            Vec3 next = points.get(i + 1);
            Vec3 in = previous.subtract(corner);
            Vec3 out = next.subtract(corner);
            double r = Math.min(radius, Math.min(in.length(), out.length()) * .45D);
            if (r < 1.0E-3D || in.normalize().dot(out.normalize()) < -.999D) {
                result.add(corner);
                continue;
            }
            Vec3 from = corner.add(in.normalize().scale(r));
            Vec3 to = corner.add(out.normalize().scale(r));
            for (int step = 0; step <= steps; step++) {
                double t = (double) step / steps;
                double u = 1.0D - t;
                result.add(from.scale(u * u).add(corner.scale(2 * u * t)).add(to.scale(t * t)));
            }
        }
        result.add(points.get(points.size() - 1));
        return clean(result);
    }

    public static void tube(VertexConsumer consumer, Matrix4f matrix, List<Vec3> path, Vec3 origin,
                            double radius, int red, int green, int blue, float[] light) {
        double[] radii = new double[path.size()];
        java.util.Arrays.fill(radii, radius);
        tube(consumer, matrix, path, origin, radii, red, green, blue, light, true);
    }

    public static void tube(VertexConsumer consumer, Matrix4f matrix, List<Vec3> path, Vec3 origin,
                            double[] radii, int red, int green, int blue, float[] light, boolean caps) {
        int count = path.size();
        if (count < 2) return;
        Vec3[] tangents = new Vec3[count];
        for (int i = 0; i < count; i++) {
            Vec3 before = path.get(Math.max(0, i - 1));
            Vec3 after = path.get(Math.min(count - 1, i + 1));
            Vec3 direction = after.subtract(before);
            tangents[i] = direction.lengthSqr() < 1.0E-12D ? new Vec3(0, 0, 1) : direction.normalize();
        }
        Vec3 normal = perpendicular(tangents[0], new Vec3(0, 1, 0));
        Vec3[][] rings = new Vec3[count][SIDES];
        Vec3[][] normals = new Vec3[count][SIDES];
        for (int i = 0; i < count; i++) {
            Vec3 tangent = tangents[i];
            normal = perpendicular(tangent, normal);
            Vec3 binormal = tangent.cross(normal).normalize();
            Vec3 center = path.get(i).subtract(origin);
            double radius = radii[Math.min(i, radii.length - 1)];
            for (int side = 0; side < SIDES; side++) {
                double angle = Math.PI * 2 * side / SIDES;
                Vec3 offset = normal.scale(Math.cos(angle)).add(binormal.scale(Math.sin(angle)));
                normals[i][side] = offset;
                rings[i][side] = center.add(offset.scale(radius));
            }
        }
        for (int i = 0; i < count - 1; i++) {
            float lightA = light == null ? 1.0F : light[Math.min(i, light.length - 1)];
            float lightB = light == null ? 1.0F : light[Math.min(i + 1, light.length - 1)];
            for (int side = 0; side < SIDES; side++) {
                int next = (side + 1) % SIDES;
                vertex(consumer, matrix, rings[i][side], normals[i][side], red, green, blue, lightA);
                vertex(consumer, matrix, rings[i][next], normals[i][next], red, green, blue, lightA);
                vertex(consumer, matrix, rings[i + 1][next], normals[i + 1][next], red, green, blue, lightB);
                vertex(consumer, matrix, rings[i + 1][side], normals[i + 1][side], red, green, blue, lightB);
            }
        }
        if (caps) {
            cap(consumer, matrix, rings[0], tangents[0].scale(-1), red, green, blue,
                    light == null ? 1.0F : light[0]);
            cap(consumer, matrix, rings[count - 1], tangents[count - 1], red, green, blue,
                    light == null ? 1.0F : light[Math.min(count - 1, light.length - 1)]);
        }
    }

    public static void box(VertexConsumer consumer, Matrix4f matrix, Vec3 center, Vec3 right, Vec3 up,
                           Vec3 forward, double width, double height, double depth,
                           int red, int green, int blue, float light) {
        Vec3 r = right.scale(width);
        Vec3 u = up.scale(height);
        Vec3 f = forward.scale(depth);
        Vec3 a = center.subtract(r).subtract(u).subtract(f);
        Vec3 b = center.add(r).subtract(u).subtract(f);
        Vec3 c = center.add(r).add(u).subtract(f);
        Vec3 d = center.subtract(r).add(u).subtract(f);
        Vec3 e = center.subtract(r).subtract(u).add(f);
        Vec3 g = center.add(r).subtract(u).add(f);
        Vec3 h = center.add(r).add(u).add(f);
        Vec3 i = center.subtract(r).add(u).add(f);
        face(consumer, matrix, e, g, h, i, forward, red, green, blue, light);
        face(consumer, matrix, b, a, d, c, forward.scale(-1), red, green, blue, light);
        face(consumer, matrix, a, e, i, d, right.scale(-1), red, green, blue, light);
        face(consumer, matrix, g, b, c, h, right, red, green, blue, light);
        face(consumer, matrix, d, i, h, c, up, red, green, blue, light);
        face(consumer, matrix, a, b, g, e, up.scale(-1), red, green, blue, light);
    }

    private static void face(VertexConsumer consumer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 c, Vec3 d,
                             Vec3 normal, int red, int green, int blue, float light) {
        vertex(consumer, matrix, a, normal, red, green, blue, light);
        vertex(consumer, matrix, b, normal, red, green, blue, light);
        vertex(consumer, matrix, c, normal, red, green, blue, light);
        vertex(consumer, matrix, d, normal, red, green, blue, light);
    }

    private static void cap(VertexConsumer consumer, Matrix4f matrix, Vec3[] ring, Vec3 normal,
                            int red, int green, int blue, float light) {
        for (int start = 1; start + 2 < SIDES + 1; start += 2) {
            int second = start + 1;
            int third = Math.min(start + 2, SIDES - 1);
            vertex(consumer, matrix, ring[0], normal, red, green, blue, light);
            vertex(consumer, matrix, ring[start], normal, red, green, blue, light);
            vertex(consumer, matrix, ring[second], normal, red, green, blue, light);
            vertex(consumer, matrix, ring[third], normal, red, green, blue, light);
        }
    }

    private static Vec3 perpendicular(Vec3 tangent, Vec3 hint) {
        Vec3 projected = hint.subtract(tangent.scale(hint.dot(tangent)));
        if (projected.lengthSqr() > 1.0E-8D) return projected.normalize();
        Vec3 fallback = Math.abs(tangent.y) < .9D ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return fallback.subtract(tangent.scale(fallback.dot(tangent))).normalize();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, Vec3 point, Vec3 normal,
                               int red, int green, int blue, float light) {
        double facing = normal.dot(LIGHT);
        double lit = AMBIENT + DIFFUSE * Math.max(0, facing) + SHINE * Math.pow(Math.max(0, facing), 10);
        double shade = Math.min(1.25D, lit) * light;
        consumer.vertex(matrix, (float) point.x, (float) point.y, (float) point.z)
                .color(channel(red, shade), channel(green, shade), channel(blue, shade), 255).endVertex();
    }

    private static int channel(int value, double shade) {
        return (int) Math.max(0, Math.min(255, Math.round(value * shade)));
    }
}
