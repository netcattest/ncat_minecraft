package com.netcattest.ncatminecraft.client.renderers;

import com.netcattest.ncatminecraft.block.RackFrameBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class CablePhysics {
    public static final int MAX_POINTS = 240;

    private static final double ANCHOR_MARGIN = .55D;
    private static final double GROUND_CLEARANCE = .075D;
    private static final double MAX_DROOP = 3.2D;
    private static final double DROOP_RATIO = .34D;
    private static final double RELAX_STEP = .18D;
    private static final int RELAX_PASSES = 3;
    private static final int DETOUR_PROBES = 14;
    private static final double[] DETOUR_STEPS = {.5D, 1.0D, 1.5D, 2.0D};
    private static final double CLEAR_TOLERANCE = 1.0E-4D;
    private static final double CLIMB_MARGIN = .6D;

    private static final double CELL = .25D;
    private static final double STEP_UP = 1.05D;
    private static final double STEP_DOWN = 2.0D;
    private static final double DROP_LIMIT = 10.0D;
    private static final double MIN_LEAD = .12D;
    private static final double SAMPLE = .125D;
    private static final double VERTICAL_COST = 1.6D;
    private static final int SEARCH_BUDGET = 6000;
    private static final int SEARCH_MARGIN = 24;
    private static final long ROUTE_TTL = 3_000_000_000L;
    private static final int[][] MOVES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private static final Map<String, Remembered> ROUTES = new LinkedHashMap<>(128, .75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Remembered> eldest) {
            return size() > 160;
        }
    };

    private CablePhysics() {
    }

    public static List<Vec3> drape(Level level, Vec3 start, Vec3 end, List<Vec3> waypoints,
                                   Vec3 startNormal, Vec3 endNormal, double lead) {
        List<Vec3> hung = hang(level, start, end, waypoints, startNormal, endNormal, lead);
        if (waypoints != null && !waypoints.isEmpty()) return hung;
        if (clear(level, hung, start, end)) return hung;
        List<Vec3> routed = route(level, start, end, startNormal, endNormal, lead);
        return routed == null ? hung : routed;
    }

    private static List<Vec3> hang(Level level, Vec3 start, Vec3 end, List<Vec3> waypoints,
                                   Vec3 startNormal, Vec3 endNormal, double lead) {
        List<Vec3> controls = new ArrayList<>();
        controls.add(start);
        if (startNormal != null)
            controls.add(start.add(startNormal.scale(lead)));
        if (waypoints != null)
            for (Vec3 waypoint : waypoints) {
                if (controls.size() > 14)
                    break;
                controls.add(waypoint);
            }
        if (endNormal != null)
            controls.add(end.add(endNormal.scale(lead)));
        controls.add(end);

        boolean guided = waypoints != null && !waypoints.isEmpty();
        double total = 0;
        for (int i = 1; i < controls.size(); i++)
            total += controls.get(i - 1).distanceTo(controls.get(i));
        double droop = guided ? 0 : Math.min(MAX_DROOP, total * DROOP_RATIO);

        Vec3 side = lateral(start, end);
        double detour = guided ? 0 : detour(level, start, end, side);
        List<Vec3> points = new ArrayList<>();
        points.add(start);
        double density = Math.min(7.0D, (MAX_POINTS - 1.0D) / Math.max(1.0D, total));
        int budget = MAX_POINTS - 1;
        for (int segment = 1; segment < controls.size(); segment++) {
            Vec3 a = controls.get(segment - 1);
            Vec3 b = controls.get(segment);
            int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) * density));
            steps = Math.max(1, Math.min(steps, budget - (controls.size() - 1 - segment)));
            budget -= steps;
            for (int step = 1; step <= steps; step++) {
                double t = (double) step / steps;
                Vec3 sample = a.lerp(b, t);
                boolean last = segment == controls.size() - 1 && step == steps;
                if (!last && !anchored(sample, start, end)) {
                    double along = progress(sample, start, end);
                    double bump = detour * Math.sin(Math.PI * along);
                    double x = sample.x + side.x * bump;
                    double z = sample.z + side.z * bump;
                    double target = sample.y - droop * Math.sin(Math.PI * along);
                    if (level.hasChunkAt(BlockPos.containing(x, target, z)))
                        target = lift(level, x, target, z);
                    sample = new Vec3(x, target, z);
                }
                points.add(sample);
            }
        }
        relax(level, points);
        return points;
    }

    private static boolean clear(Level level, List<Vec3> points, Vec3 start, Vec3 end) {
        double ceiling = Math.max(start.y, end.y) + CLIMB_MARGIN;
        for (int i = 2; i < points.size() - 2; i++) {
            Vec3 point = points.get(i);
            if (point.y > ceiling || solid(level, point.x, point.y, point.z))
                return false;
        }
        return true;
    }

    private static List<Vec3> route(Level level, Vec3 start, Vec3 end, Vec3 startNormal, Vec3 endNormal,
                                    double lead) {
        String key = key(start, startNormal) + "|" + key(end, endNormal);
        long now = System.nanoTime();
        Remembered known = ROUTES.get(key);
        if (known != null && known.level() == level && now - known.time() < ROUTE_TTL)
            return known.points();
        List<Vec3> result = search(level, start, end, startNormal, endNormal, lead);
        ROUTES.put(key, new Remembered(level, now, result));
        return result;
    }

    private static String key(Vec3 point, Vec3 normal) {
        return Math.round(point.x * 10) + "," + Math.round(point.y * 10) + "," + Math.round(point.z * 10)
                + (normal == null ? "" : ":" + Math.round(normal.x) + Math.round(normal.y) + Math.round(normal.z));
    }

    private static List<Vec3> search(Level level, Vec3 start, Vec3 end, Vec3 startNormal, Vec3 endNormal,
                                     double lead) {
        double reach = Math.max(lead, MIN_LEAD);
        Vec3 a = startNormal == null ? start : start.add(startNormal.scale(reach));
        Vec3 b = endNormal == null ? end : end.add(endNormal.scale(reach));
        if (solid(level, a.x, a.y, a.z) || solid(level, b.x, b.y, b.z)) return null;
        double floorA = floorBelow(level, a.x, a.y, a.z, DROP_LIMIT);
        double floorB = floorBelow(level, b.x, b.y, b.z, DROP_LIMIT);
        if (Double.isNaN(floorA) || Double.isNaN(floorB)) return null;

        List<Vec3> ground = walk(level, a.x, floorA, a.z, b.x, floorB, b.z);
        if (ground == null) return null;

        List<Vec3> corners = new ArrayList<>();
        corners.add(start);
        corners.add(a);
        corners.addAll(ground);
        corners.add(b);
        corners.add(end);
        List<Vec3> points = CableMesh.rounded(corners, .09D, 4);
        relax(level, points);
        return points;
    }

    private static List<Vec3> walk(Level level, double ax, double floorA, double az,
                                   double bx, double floorB, double bz) {
        int si = cell(ax);
        int sj = cell(az);
        int gi = cell(bx);
        int gj = cell(bz);
        int minI = Math.min(si, gi) - SEARCH_MARGIN;
        int maxI = Math.max(si, gi) + SEARCH_MARGIN;
        int minJ = Math.min(sj, gj) - SEARCH_MARGIN;
        int maxJ = Math.max(sj, gj) + SEARCH_MARGIN;

        Map<Long, Node> best = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((x, y) -> Double.compare(x.estimate, y.estimate));
        Node first = new Node(si, sj, floorA, 0, null);
        first.estimate = heuristic(si, sj, gi, gj);
        open.add(first);
        best.put(pack(si, sj), first);
        Node goal = null;
        int expanded = 0;
        while (!open.isEmpty() && expanded < SEARCH_BUDGET) {
            Node node = open.poll();
            if (node.closed) continue;
            node.closed = true;
            expanded++;
            if (node.i == gi && node.j == gj) {
                goal = node;
                break;
            }
            for (int[] move : MOVES) {
                int ni = node.i + move[0];
                int nj = node.j + move[1];
                if (ni < minI || ni > maxI || nj < minJ || nj > maxJ) continue;
                double floor = step(level, node.floor, centre(ni), centre(nj));
                if (Double.isNaN(floor)) continue;
                if (move[0] != 0 && move[1] != 0 &&
                        (Double.isNaN(step(level, node.floor, centre(node.i + move[0]), centre(node.j))) ||
                                Double.isNaN(step(level, node.floor, centre(node.i), centre(node.j + move[1])))))
                    continue;
                boolean target = ni == gi && nj == gj;
                if (target && Math.abs(floor - floorB) > STEP_UP) continue;
                double cost = node.cost + Math.hypot(move[0], move[1]) * CELL
                        + Math.abs(floor - node.floor) * VERTICAL_COST;
                long key = pack(ni, nj);
                Node known = best.get(key);
                if (known != null && (known.closed || known.cost <= cost)) continue;
                Node next = new Node(ni, nj, floor, cost, node);
                next.estimate = cost + heuristic(ni, nj, gi, gj);
                best.put(key, next);
                open.add(next);
            }
        }
        if (goal == null) return null;

        List<Vec3> cells = new ArrayList<>();
        for (Node node = goal; node != null; node = node.parent)
            cells.add(new Vec3(centre(node.i), node.floor, centre(node.j)));
        Collections.reverse(cells);
        cells.set(0, new Vec3(ax, floorA, az));
        cells.set(cells.size() - 1, new Vec3(bx, floorB, bz));

        List<Vec3> pulled = new ArrayList<>();
        pulled.add(cells.get(0));
        int anchor = 0;
        while (anchor < cells.size() - 1) {
            int far = anchor + 1;
            for (int candidate = anchor + 2; candidate < cells.size(); candidate++) {
                if (!sight(level, cells.get(anchor), cells.get(candidate))) break;
                far = candidate;
            }
            pulled.add(cells.get(far));
            anchor = far;
        }

        List<Vec3> path = new ArrayList<>();
        for (int k = 0; k < pulled.size() - 1; k++) {
            Vec3 from = pulled.get(k);
            Vec3 to = pulled.get(k + 1);
            double length = Math.hypot(to.x - from.x, to.z - from.z);
            int steps = Math.max(1, (int) Math.ceil(length / SAMPLE));
            double height = from.y;
            for (int s = 0; s < steps; s++) {
                double t = (double) s / steps;
                double x = from.x + (to.x - from.x) * t;
                double z = from.z + (to.z - from.z) * t;
                double floor = s == 0 ? from.y : step(level, height, x, z);
                if (Double.isNaN(floor)) floor = height;
                height = floor;
                path.add(new Vec3(x, floor + GROUND_CLEARANCE, z));
            }
        }
        Vec3 last = pulled.get(pulled.size() - 1);
        path.add(new Vec3(last.x, last.y + GROUND_CLEARANCE, last.z));
        return path;
    }

    private static boolean sight(Level level, Vec3 from, Vec3 to) {
        double length = Math.hypot(to.x - from.x, to.z - from.z);
        int steps = Math.max(1, (int) Math.ceil(length / SAMPLE));
        double height = from.y;
        for (int s = 1; s <= steps; s++) {
            double t = (double) s / steps;
            double floor = step(level, height, from.x + (to.x - from.x) * t, from.z + (to.z - from.z) * t);
            if (Double.isNaN(floor)) return false;
            height = floor;
        }
        return Math.abs(height - to.y) <= STEP_UP;
    }

    private static double step(Level level, double floor, double x, double z) {
        double top = floor + STEP_UP;
        if (solid(level, x, top, z)) return Double.NaN;
        return floorBelow(level, x, top, z, STEP_UP + STEP_DOWN);
    }

    private static double floorBelow(Level level, double x, double y, double z, double maxDrop) {
        if (solid(level, x, y, z)) return Double.NaN;
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int blockY = (int) Math.floor(y);
        int lowest = (int) Math.floor(y - maxDrop);
        for (int by = blockY; by >= lowest; by--) {
            BlockPos pos = new BlockPos(bx, by, bz);
            if (!level.hasChunkAt(pos)) return Double.NaN;
            BlockState state = level.getBlockState(pos);
            double top = Double.NEGATIVE_INFINITY;
            if (state.getBlock() instanceof RackFrameBlock) {
                if (by + 1.0D <= y + 1.0E-6D) top = by + 1.0D;
            } else {
                VoxelShape shape = state.getCollisionShape(level, pos);
                double lx = x - bx;
                double lz = z - bz;
                for (AABB box : shape.toAabbs())
                    if (lx >= box.minX - .01D && lx <= box.maxX + .01D && lz >= box.minZ - .01D && lz <= box.maxZ + .01D) {
                        double candidate = by + box.maxY;
                        if (candidate <= y + 1.0E-6D) top = Math.max(top, candidate);
                    }
            }
            if (top != Double.NEGATIVE_INFINITY) return top;
        }
        return Double.NaN;
    }

    private static boolean solid(Level level, double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.hasChunkAt(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof RackFrameBlock) return true;
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        double lx = x - pos.getX();
        double ly = y - pos.getY();
        double lz = z - pos.getZ();
        for (AABB box : shape.toAabbs())
            if (lx > box.minX + .01D && lx < box.maxX - .01D && ly > box.minY + .01D && ly < box.maxY - .01D
                    && lz > box.minZ + .01D && lz < box.maxZ - .01D)
                return true;
        return false;
    }

    private static int cell(double value) {
        return (int) Math.floor(value / CELL);
    }

    private static double centre(int index) {
        return (index + .5D) * CELL;
    }

    private static long pack(int i, int j) {
        return ((long) i << 32) ^ (j & 0xffffffffL);
    }

    private static double heuristic(int i, int j, int gi, int gj) {
        return Math.hypot(i - gi, j - gj) * CELL;
    }

    private static Vec3 lateral(Vec3 start, Vec3 end) {
        double dx = end.x - start.x;
        double dz = end.z - start.z;
        double length = Math.sqrt(dx * dx + dz * dz);
        return length < 1.0E-3D ? Vec3.ZERO : new Vec3(-dz / length, 0, dx / length);
    }

    private static double detour(Level level, Vec3 start, Vec3 end, Vec3 side) {
        if (side.lengthSqr() < 1.0E-6D || !blocked(level, start, end, side, 0))
            return 0;
        for (double step : DETOUR_STEPS)
            for (int sign = 1; sign >= -1; sign -= 2)
                if (!blocked(level, start, end, side, step * sign))
                    return step * sign;
        return 0;
    }

    private static boolean blocked(Level level, Vec3 start, Vec3 end, Vec3 side, double push) {
        for (int probe = 1; probe < DETOUR_PROBES; probe++) {
            double t = (double) probe / DETOUR_PROBES;
            Vec3 sample = start.lerp(end, t);
            if (anchored(sample, start, end))
                continue;
            double bump = push * Math.sin(Math.PI * t);
            double x = sample.x + side.x * bump;
            double z = sample.z + side.z * bump;
            if (!level.hasChunkAt(BlockPos.containing(x, sample.y, z)))
                continue;
            if (lift(level, x, sample.y, z) > sample.y + CLEAR_TOLERANCE)
                return true;
        }
        return false;
    }

    private static boolean anchored(Vec3 sample, Vec3 start, Vec3 end) {
        return sample.distanceToSqr(start) < ANCHOR_MARGIN * ANCHOR_MARGIN
                || sample.distanceToSqr(end) < ANCHOR_MARGIN * ANCHOR_MARGIN;
    }

    private static double progress(Vec3 sample, Vec3 start, Vec3 end) {
        double span = start.distanceTo(end);
        if (span < 1.0E-4D)
            return 0;
        return Math.max(0, Math.min(1, sample.distanceTo(start) / span));
    }

    private static void relax(Level level, List<Vec3> points) {
        for (int pass = 0; pass < RELAX_PASSES; pass++) {
            for (int i = 1; i < points.size() - 1; i++)
                points.set(i, floorAt(level, points.get(i), points.get(i - 1).y - RELAX_STEP));
            for (int i = points.size() - 2; i > 0; i--)
                points.set(i, floorAt(level, points.get(i), points.get(i + 1).y - RELAX_STEP));
        }
    }

    private static Vec3 floorAt(Level level, Vec3 point, double minimum) {
        if (point.y >= minimum)
            return point;
        if (!level.hasChunkAt(BlockPos.containing(point.x, minimum, point.z)))
            return new Vec3(point.x, minimum, point.z);
        double free = lift(level, point.x, minimum, point.z);
        return new Vec3(point.x, free > minimum ? point.y : minimum, point.z);
    }

    public static double lift(Level level, double x, double y, double z) {
        double current = y;
        for (int attempt = 0; attempt < 24; attempt++) {
            BlockPos block = BlockPos.containing(x, current, z);
            if (!level.hasChunkAt(block))
                return current;
            BlockState state = level.getBlockState(block);
            if (state.getBlock() instanceof RackFrameBlock) {
                current = block.getY() + 1.0D + GROUND_CLEARANCE;
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level, block);
            if (shape.isEmpty())
                return current;
            double localX = x - block.getX();
            double localY = current - block.getY();
            double localZ = z - block.getZ();
            double top = Double.NEGATIVE_INFINITY;
            for (AABB box : shape.toAabbs())
                if (localX >= box.minX - .02D && localX <= box.maxX + .02D
                        && localZ >= box.minZ - .02D && localZ <= box.maxZ + .02D
                        && localY >= box.minY - .02D && localY <= box.maxY)
                    top = Math.max(top, box.maxY);
            if (top == Double.NEGATIVE_INFINITY)
                return current;
            current = block.getY() + top + GROUND_CLEARANCE;
        }
        return current;
    }

    private static final class Node {
        final int i;
        final int j;
        final double floor;
        final double cost;
        final Node parent;
        double estimate;
        boolean closed;

        Node(int i, int j, double floor, double cost, Node parent) {
            this.i = i;
            this.j = j;
            this.floor = floor;
            this.cost = cost;
            this.parent = parent;
        }
    }

    private record Remembered(Level level, long time, List<Vec3> points) {
    }
}
