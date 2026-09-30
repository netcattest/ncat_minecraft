package com.netcattest.ncatminecraft.client.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.netcattest.ncatminecraft.block.RackFrameBlock;
import com.netcattest.ncatminecraft.block.RackLayout;
import com.netcattest.ncatminecraft.entity.DataPort;
import com.netcattest.ncatminecraft.entity.NetworkSwitchBlockEntity;
import com.netcattest.ncatminecraft.entity.RackBlockEntity;
import com.netcattest.ncatminecraft.entity.RackModule;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DataCableRenderer {
    private static final Map<UUID, CachedPath> PATHS = new HashMap<>();
    private static final double MAX_DISTANCE_SQUARED = 48.0 * 48.0;

    private static final double CABLE_RADIUS = .017D;
    private static final double LEAD = .14D;
    private static final double BEND = .032D;
    private static final int BEND_STEPS = 5;

    private static final int[] JACKET = {52, 108, 168};
    private static final int[] GHOST = {70, 158, 196};
    private static final int[] BOOT = {38, 150, 178};
    private static final int[] PLUG = {198, 206, 214};
    private static final int[] LATCH = {150, 160, 170};

    private static final double PLUG_INSERT = .014D;
    private static final double PLUG_OUT = .012D;
    private static final double BOOT_LENGTH = .022D;

    private static final double GAP_LATERAL = .3594D;
    private static final double CHANNEL_LATERAL = .372D;
    private static final double CHANNEL_DEPTH = .16D;
    private static final double BEHIND_RAIL = .30D;
    private static final double OUTSIDE = .535D;
    private static final double RUN_DROP = .024D;
    private static final double FLOOR_TOP = .1D;
    private static final double WALL_FLOOR_TOP = .0875D;
    private static final double WALL_ROOF_TOP = .96875D;

    private DataCableRenderer() {
    }

    public static void render(ScreenBlockEntity entity, PoseStack poseStack, MultiBufferSource buffers,
                              int packedLight, int packedOverlay) {
        Level level = entity.getLevel();
        if (level == null || entity.screenCount() == 0) return;
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = poseStack.last().pose();
        ItemStack held = selectedCable();
        for (int screenIndex = 0; screenIndex < entity.screenCount(); screenIndex++) {
            ScreenData screen = entity.getScreen(screenIndex);
            for (DataPort port : DataCableService.ports(screen))
                renderEnd(level, entity.getBlockPos(), new DataCableService.Endpoint(entity, screen, port),
                        consumer, matrix, held, true);
        }
    }

    public static void renderSwitch(NetworkSwitchBlockEntity entity, PoseStack poseStack, MultiBufferSource buffers) {
        Level level = entity.getLevel();
        if (level == null) return;
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = poseStack.last().pose();
        ItemStack held = selectedCable();
        for (int i = 0; i < NetworkSwitchBlockEntity.PORT_COUNT; i++) {
            DataPort port = entity.getPort(i);
            if (port != null)
                renderEnd(level, entity.getBlockPos(), new DataCableService.SwitchEndpoint(entity, i, port),
                        consumer, matrix, held, false);
        }
    }

    public static void renderRack(RackBlockEntity rack, PoseStack poseStack, MultiBufferSource buffers) {
        Level level = rack.getLevel();
        if (level == null) return;
        VertexConsumer consumer = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = poseStack.last().pose();
        ItemStack held = selectedCable();
        for (RackModule module : rack.getModules())
            for (int index = 0; index < module.portCount(); index++) {
                DataCableService.RackEndpoint endpoint = DataCableService.rackEndpoint(rack, module.id(), index);
                if (endpoint != null)
                    renderEnd(level, rack.getBlockPos(), endpoint, consumer, matrix, held, false);
            }
    }

    private static void renderEnd(Level level, BlockPos origin, DataCableService.CableEnd local,
                                  VertexConsumer consumer, Matrix4f matrix, ItemStack held, boolean drawSocket) {
        Vec3 socket = DataCableService.worldPosition(local);
        BlockSide mount = DataCableService.mountFace(local);
        if (socket == null || mount == null) return;
        DataPort port = local.port();
        Vec3 frame = Vec3.atLowerCornerOf(origin);
        float light = CableMesh.brightness(level, socket);
        if (drawSocket)
            renderSocket(consumer, matrix, socket.subtract(frame), mount, port.connected(),
                    port.trafficLight(level.getGameTime()), light);
        if (!held.isEmpty())
            renderPreview(consumer, matrix, level, local, socket, held, frame);

        DataCableService.CableEnd remote = DataCableService.other(level, local);
        if (remote == null) return;
        renderPlug(consumer, matrix, local, socket, mount, frame, light);
        if (port.id.compareTo(remote.port().id) > 0) return;

        Vec3 remoteSocket = DataCableService.worldPosition(remote);
        BlockSide remoteMount = DataCableService.mountFace(remote);
        if (remoteSocket == null || remoteMount == null ||
                socket.distanceToSqr(remoteSocket) > MAX_DISTANCE_SQUARED) return;
        CachedPath path = path(level, local, socket, mount, remote, remoteSocket, remoteMount);
        CableMesh.tube(consumer, matrix, path.points(), frame, CABLE_RADIUS,
                JACKET[0], JACKET[1], JACKET[2], path.light());
    }

    private static CachedPath path(Level level, DataCableService.CableEnd local, Vec3 socket, BlockSide mount,
                                   DataCableService.CableEnd remote, Vec3 remoteSocket, BlockSide remoteMount) {
        DataPort port = local.port();
        int fingerprint = socket.hashCode() * 31 + remoteSocket.hashCode() * 17 + port.waypoints.hashCode()
                + mount.ordinal() * 7 + remoteMount.ordinal() * 3;
        CachedPath previous = PATHS.get(port.id);
        long tick = level.getGameTime();
        if (previous != null && previous.fingerprint() == fingerprint && previous.world() == level &&
                tick - previous.updatedAt() >= 0 && tick - previous.updatedAt() < 20) return previous;

        Route near = route(level, local, socket, mount, remoteSocket);
        Route far = route(level, remote, remoteSocket, remoteMount, socket);
        List<Vec3> drape = CablePhysics.drape(level, near.exit(), far.exit(), port.waypoints,
                near.normal(), far.normal(), LEAD);
        List<Vec3> points = new ArrayList<>(near.points());
        points.addAll(drape);
        List<Vec3> tail = new ArrayList<>(far.points());
        java.util.Collections.reverse(tail);
        points.addAll(tail);
        points = CableMesh.clean(points);
        CachedPath created = new CachedPath(level, fingerprint, tick, points, CableMesh.brightness(level, points));
        if (PATHS.size() > 512) PATHS.clear();
        PATHS.put(port.id, created);
        return created;
    }

    private static Route route(Level level, DataCableService.CableEnd end, Vec3 socket, BlockSide mount, Vec3 target) {
        if (end instanceof DataCableService.RackEndpoint rack) {
            Route inside = rackRoute(level, rack, socket, target);
            if (inside != null) return inside;
        }
        Vec3 normal = direction(mount);
        Vec3 mouth = socket.add(normal.scale(mouthOffset(end)));
        Vec3 bootEnd = mouth.add(normal.scale(PLUG_OUT + BOOT_LENGTH));
        return new Route(List.of(mouth, bootEnd), bootEnd, normal);
    }

    private static Route rackRoute(Level level, DataCableService.RackEndpoint endpoint, Vec3 socket, Vec3 target) {
        RackBlockEntity rack = endpoint.rack();
        Direction facing = rack.getFacing();
        Vec3 front = new Vec3(facing.getStepX(), 0, facing.getStepZ());
        Vec3 right = new Vec3(facing.getClockWise().getStepX(), 0, facing.getClockWise().getStepZ());
        BlockPos base = rack.getBlockPos();
        double cx = base.getX() + .5D;
        double cz = base.getZ() + .5D;
        Frame frame = new Frame(cx, cz, front, right);

        double socketLateral = (socket.x - cx) * right.x + (socket.z - cz) * right.z;
        double socketDepth = (socket.x - cx) * front.x + (socket.z - cz) * front.z;
        double bootDepth = socketDepth + PLUG_OUT + BOOT_LENGTH;

        BlockState baseState = level.getBlockState(base);
        boolean wall = RackFrameBlock.isStandalone(baseState);
        int sections = rack.heightBlocks();
        BlockState topState = level.getBlockState(base.above(sections - 1));
        boolean roof = wall || !RackFrameBlock.isBase(topState);
        double top = wall ? base.getY() + WALL_ROOF_TOP : base.getY() + sections;
        double floor = base.getY() + (wall ? WALL_FLOOR_TOP : FLOOR_TOP);

        int hash = endpoint.port().id.hashCode();
        double laneLateral = CHANNEL_LATERAL + (Math.floorMod(hash, 5) - 2) * .011D;
        double laneDepth = CHANNEL_DEPTH + (Math.floorMod(hash >> 3, 3) - 1) * .028D;
        double laneRise = Math.floorMod(hash >> 6, 4) * .011D;
        double runY = socket.y - RUN_DROP - Math.floorMod(endpoint.index(), 4) * .004D;
        double runDepth = bootDepth + (endpoint.index() / 4 % 2) * .004D;

        RackModule module = endpoint.module();
        int dressed = module == null ? 0 : module.portDress(endpoint.index());
        double towardTarget = (target.x - cx) * right.x + (target.z - cz) * right.z;
        boolean upward = roof && target.y > top - .3D;
        int side;
        if (dressed == 1) side = 1;
        else if (dressed == 2) side = -1;
        else if (!upward && Math.abs(towardTarget) > .35D) side = towardTarget >= 0 ? 1 : -1;
        else side = socketLateral >= 0 ? 1 : -1;

        String exit = upward ? "top" : "side";
        if (!upward) {
            if (!free(level, frame.at(side * OUTSIDE, laneDepth, floor + .035D + laneRise))) {
                if (dressed == 0 && free(level, frame.at(-side * OUTSIDE, laneDepth, floor + .035D + laneRise)))
                    side = -side;
                else if (!wall && free(level, frame.at(side * laneLateral, -OUTSIDE, floor + .035D + laneRise)))
                    exit = "back";
                else if (roof)
                    exit = "top";
            }
        }

        List<Vec3> corners = new ArrayList<>();
        corners.add(socket);
        corners.add(frame.at(socketLateral, bootDepth, socket.y));
        corners.add(frame.at(socketLateral, runDepth, runY));
        corners.add(frame.at(side * GAP_LATERAL, runDepth, runY));
        corners.add(frame.at(side * GAP_LATERAL, BEHIND_RAIL, runY));
        corners.add(frame.at(side * laneLateral, laneDepth, runY));
        Vec3 exitPoint;
        Vec3 normal;
        switch (exit) {
            case "top" -> {
                double y = top + .04D;
                corners.add(frame.at(side * laneLateral, laneDepth, top - .13D - laneRise));
                exitPoint = frame.at(side * laneLateral, laneDepth, y);
                normal = new Vec3(0, 1, 0);
            }
            case "back" -> {
                double y = floor + .035D + laneRise;
                corners.add(frame.at(side * laneLateral, laneDepth, y));
                exitPoint = frame.at(side * laneLateral, -OUTSIDE, y);
                normal = front.scale(-1);
            }
            default -> {
                double y = floor + .035D + laneRise;
                corners.add(frame.at(side * laneLateral, laneDepth, y));
                exitPoint = frame.at(side * OUTSIDE, laneDepth, y);
                normal = right.scale(side);
            }
        }
        corners.add(exitPoint);
        List<Vec3> points = CableMesh.rounded(corners, BEND, BEND_STEPS);
        return new Route(points, exitPoint, normal);
    }

    private static boolean free(Level level, Vec3 point) {
        BlockPos block = BlockPos.containing(point);
        if (!level.hasChunkAt(block)) return false;
        BlockState state = level.getBlockState(block);
        if (state.getBlock() instanceof RackFrameBlock) return false;
        VoxelShape shape = state.getCollisionShape(level, block);
        if (shape.isEmpty()) return true;
        double x = point.x - block.getX();
        double y = point.y - block.getY();
        double z = point.z - block.getZ();
        for (AABB box : shape.toAabbs())
            if (x >= box.minX && x <= box.maxX && y >= box.minY && y <= box.maxY && z >= box.minZ && z <= box.maxZ)
                return false;
        return true;
    }

    private static double mouthOffset(DataCableService.CableEnd end) {
        return end instanceof DataCableService.Endpoint ? .045D : 0D;
    }

    private static double[] plugSize(DataCableService.CableEnd end) {
        if (end instanceof DataCableService.Endpoint) return new double[] {.050D, .034D};
        if (end instanceof DataCableService.SwitchEndpoint) return new double[] {.034D, .026D};
        if (end instanceof DataCableService.RackEndpoint rack && rack.module() != null) {
            double half = RackLayout.portWidth(rack.module().portCount()) * .5D * .82D;
            return new double[] {Math.max(.016D, Math.min(.024D, half)), .018D};
        }
        return new double[] {.022D, .018D};
    }

    private static void renderPlug(VertexConsumer consumer, Matrix4f matrix, DataCableService.CableEnd end,
                                   Vec3 socket, BlockSide mount, Vec3 frame, float light) {
        Vec3 forward = direction(mount);
        Vec3 right = directionRight(mount);
        Vec3 up = directionUp(mount);
        double[] size = plugSize(end);
        Vec3 mouth = socket.add(forward.scale(mouthOffset(end))).subtract(frame);
        double bodyDepth = (PLUG_INSERT + PLUG_OUT) * .5D;
        Vec3 body = mouth.add(forward.scale((PLUG_OUT - PLUG_INSERT) * .5D));
        CableMesh.box(consumer, matrix, body, right, up, forward, size[0], size[1], bodyDepth,
                PLUG[0], PLUG[1], PLUG[2], light);
        CableMesh.box(consumer, matrix, mouth.add(forward.scale(PLUG_OUT * .45D)).add(up.scale(size[1] + .003D)),
                right, up, forward, size[0] * .42D, .003D, PLUG_OUT * .5D,
                LATCH[0], LATCH[1], LATCH[2], light);
        List<Vec3> boot = List.of(mouth.add(forward.scale(PLUG_OUT)),
                mouth.add(forward.scale(PLUG_OUT + BOOT_LENGTH * .5D)),
                mouth.add(forward.scale(PLUG_OUT + BOOT_LENGTH)));
        double outer = Math.max(CABLE_RADIUS + .004D, Math.min(size[0], size[1]) * 1.02D);
        CableMesh.tube(consumer, matrix, boot, Vec3.ZERO,
                new double[] {outer, (outer + CABLE_RADIUS) * .5D + .001D, CABLE_RADIUS + .0015D},
                BOOT[0], BOOT[1], BOOT[2], new float[] {light, light, light}, true);
    }

    private static void renderSocket(VertexConsumer consumer, Matrix4f matrix, Vec3 center, BlockSide facing,
                                     boolean occupied, boolean active, float light) {
        Vec3 right = directionRight(facing);
        Vec3 up = directionUp(facing);
        Vec3 forward = direction(facing);
        CableMesh.box(consumer, matrix, center, right, up, forward, .104D, .074D, .035D, 38, 43, 51, light);
        CableMesh.box(consumer, matrix, center.add(forward.scale(.039D)), right, up, forward,
                .079D, .051D, .004D, 7, 12, 18, light);
        CableMesh.box(consumer, matrix, center.add(forward.scale(.045D)).add(up.scale(-.032D)), right, up, forward,
                .052D, .005D, .003D, 193, 160, 85, light);
        for (int pin = 0; pin < 8; pin++) {
            double offset = (pin - 3.5D) * .014D;
            CableMesh.box(consumer, matrix, center.add(forward.scale(.046D)).add(right.scale(offset)).add(up.scale(.018D)),
                    right, up, forward, .004D, .013D, .003D, 210, 174, 94, light);
        }
        Vec3 led = center.add(right.scale(.083D)).add(up.scale(.056D)).add(forward.scale(.038D));
        float glow = active ? 1.0F : light;
        CableMesh.box(consumer, matrix, led, right, up, forward, .009D, .009D, .004D,
                active ? 79 : occupied ? 47 : 70,
                active ? 240 : occupied ? 111 : 80,
                active ? 205 : occupied ? 93 : 92, glow);
    }

    private static ItemStack selectedCable() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return ItemStack.EMPTY;
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.isEmpty() || stack.getTag() == null || !stack.getTag().hasUUID("CableStartPort")) continue;
            var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (key != null && "ncat_minecraft".equals(key.getNamespace()) && "data_cable".equals(key.getPath()))
                return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void renderPreview(VertexConsumer consumer, Matrix4f matrix, Level level,
                                      DataCableService.CableEnd endpoint, Vec3 socket, ItemStack held, Vec3 frame) {
        CompoundTag tag = held.getTag();
        Player player = Minecraft.getInstance().player;
        boolean switchPort = endpoint instanceof DataCableService.SwitchEndpoint ||
                endpoint instanceof DataCableService.RackEndpoint;
        if (tag == null || player == null || !tag.contains("CableStartPos") || !tag.hasUUID("CableStartPort") ||
                !tag.contains("CableStartDimension") ||
                tag.getLong("CableStartPos") != endpoint.pos().asLong() ||
                tag.getBoolean("CableStartSwitch") != switchPort ||
                !switchPort && (endpoint instanceof DataCableService.Endpoint screen &&
                        (!tag.contains("CableStartSide") || tag.getByte("CableStartSide") != screen.side().ordinal())) ||
                !tag.getUUID("CableStartPort").equals(endpoint.port().id) ||
                !tag.getString("CableStartDimension").equals(level.dimension().location().toString())) return;

        Vec3 look = player.getLookAngle();
        Vec3 horizontalRight = new Vec3(-look.z, 0, look.x).normalize();
        Vec3 hand = player.getEyePosition().add(look.scale(.54D)).add(horizontalRight.scale(.24D)).add(0, -.41D, 0);
        BlockSide mount = DataCableService.mountFace(endpoint);
        Route start = route(level, endpoint, socket, mount, hand);
        if (start.exit().distanceToSqr(hand) > MAX_DISTANCE_SQUARED) return;
        float light = CableMesh.brightness(level, socket);
        renderPlug(consumer, matrix, endpoint, socket, mount, frame, light);
        List<Vec3> points = new ArrayList<>(start.points());
        points.addAll(CablePhysics.drape(level, start.exit(), hand, List.of(), start.normal(), null, LEAD));
        points = CableMesh.clean(points);
        CableMesh.tube(consumer, matrix, points, frame, CABLE_RADIUS, GHOST[0], GHOST[1], GHOST[2],
                CableMesh.brightness(level, points));
    }

    private static Vec3 direction(BlockSide side) {
        return new Vec3(side.forward.x, side.forward.y, side.forward.z);
    }

    private static Vec3 directionRight(BlockSide side) {
        return new Vec3(side.right.x, side.right.y, side.right.z);
    }

    private static Vec3 directionUp(BlockSide side) {
        return new Vec3(side.up.x, side.up.y, side.up.z);
    }

    private record Frame(double cx, double cz, Vec3 front, Vec3 right) {
        Vec3 at(double lateral, double depth, double y) {
            return new Vec3(cx + right.x * lateral + front.x * depth, y, cz + right.z * lateral + front.z * depth);
        }
    }

    private record Route(List<Vec3> points, Vec3 exit, Vec3 normal) {
    }

    private record CachedPath(Level world, int fingerprint, long updatedAt, List<Vec3> points, float[] light) {
    }
}
