package com.netcattest.ncatminecraft.utilities;

import com.netcattest.ncatminecraft.entity.DataPort;
import com.netcattest.ncatminecraft.entity.ManagedSwitchBlockEntity;
import com.netcattest.ncatminecraft.entity.NetworkSwitchBlockEntity;
import com.netcattest.ncatminecraft.entity.RackBlockEntity;
import com.netcattest.ncatminecraft.entity.RackModule;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.net.client_bound.S2CMessageAddScreen;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public final class DataCableService {
    public static final int MAX_PORTS = 8;
    public static final double MAX_LENGTH = 48;
    private static final Map<Level, Set<PendingPort>> PENDING_REMOVALS = new WeakHashMap<>();

    private record PendingPort(BlockPos pos, BlockSide side, UUID id, boolean switchPort) { }

    private DataCableService() { }

    public sealed interface CableEnd permits Endpoint, SwitchEndpoint, RackEndpoint {
        BlockPos pos();
        DataPort port();
    }

    public record Endpoint(ScreenBlockEntity entity, ScreenData screen, DataPort port) implements CableEnd {
        public BlockPos pos() { return entity.getBlockPos(); }
        public BlockSide side() { return screen.side; }
    }

    public record SwitchEndpoint(NetworkSwitchBlockEntity entity, int index, DataPort port) implements CableEnd {
        public BlockPos pos() { return entity.getBlockPos(); }
    }

    public record RackEndpoint(RackBlockEntity rack, UUID moduleId, int index, DataPort port) implements CableEnd {
        public BlockPos pos() { return rack.getBlockPos(); }
        public RackModule module() { return rack.getModule(moduleId); }
    }

    public record RackModuleEndpoint(RackBlockEntity rack, RackModule module) { }

    public static RackEndpoint rackEndpoint(RackBlockEntity rack, UUID moduleId, int portIndex) {
        if (rack == null || moduleId == null) return null;
        RackModule module = rack.getModule(moduleId);
        DataPort port = module == null ? null : module.port(portIndex);
        return port == null ? null : new RackEndpoint(rack, moduleId, portIndex, port);
    }

    public record ScreenEndpoint(ScreenBlockEntity entity, BlockSide side) { }

    public static List<DataPort> ports(ScreenData screen) {
        if (screen.dataPorts.stream().noneMatch(port -> port.automatic))
            screen.dataPorts.add(0, DataPort.automatic());
        return screen.dataPorts;
    }

    private static final double EDGE_PROTRUSION = .035D;

    public static Vec3 worldPosition(BlockPos origin, ScreenData screen, DataPort port) {
        Vector3i right = screen.side.right;
        Vector3i up = screen.side.up;
        Vector3i forward = screen.side.forward;
        BlockSide mount = mountFace(screen, port);
        double horizontal = (port.automatic ? screen.size.x + EDGE_PROTRUSION : port.u * screen.size.x) - .5;
        double vertical = (port.automatic ? .14 : port.v * screen.size.y) - .5;
        double depth = mount == screen.side ? .57 : port.depth - .5;
        return Vec3.atCenterOf(origin)
                .add(right.x * horizontal, right.y * horizontal, right.z * horizontal)
                .add(up.x * vertical, up.y * vertical, up.z * vertical)
                .add(forward.x * depth, forward.y * depth, forward.z * depth);
    }

    public static BlockSide mountFace(ScreenData screen, DataPort port) {
        if (port.mountFace != null) return port.mountFace;
        if (port.automatic) {
            for (BlockSide candidate : BlockSide.values()) {
                if (candidate.forward.x == screen.side.right.x &&
                        candidate.forward.y == screen.side.right.y &&
                        candidate.forward.z == screen.side.right.z) return candidate;
            }
        }
        return screen.side;
    }

    public static Endpoint hit(Level level, BlockHitResult hit) {
        if (!(level.getBlockState(hit.getBlockPos()).getBlock() instanceof com.netcattest.ncatminecraft.block.ScreenBlock)) return null;
        BlockSide face = BlockSide.fromInt(hit.getDirection().ordinal());
        if (face == null) return null;
        Endpoint direct = screenAt(level, hit.getBlockPos(), face, face);
        if (direct != null) return direct;
        Endpoint selected = null;
        double best = Double.POSITIVE_INFINITY;
        for (BlockSide side : BlockSide.values()) {
            if (!isBorderFace(side, face)) continue;
            Endpoint candidate = screenAt(level, hit.getBlockPos(), side, face);
            if (candidate == null) continue;
            double distance = worldPosition(candidate.pos(), candidate.screen(), candidate.port())
                    .distanceToSqr(hit.getLocation());
            if (distance < best) {
                best = distance;
                selected = candidate;
            }
        }
        return selected;
    }

    public static Endpoint hitPort(Level level, BlockHitResult hit) {
        Endpoint endpoint = hit(level, hit);
        if (endpoint == null) return null;
        BlockSide face = BlockSide.fromInt(hit.getDirection().ordinal());
        DataPort closest = null;
        double best = .36 * .36;
        for (DataPort port : ports(endpoint.screen())) {
            if (mountFace(endpoint.screen(), port) != face) continue;
            double distance = worldPosition(endpoint.pos(), endpoint.screen(), port).distanceToSqr(hit.getLocation());
            if (distance < best) {
                best = distance;
                closest = port;
            }
        }
        return closest == null ? null : new Endpoint(endpoint.entity(), endpoint.screen(), closest);
    }

    private static Endpoint screenAt(Level level, BlockPos clicked, BlockSide side, BlockSide face) {
        Vector3i origin = new Vector3i(clicked);
        Multiblock.findOrigin(level, origin, side, null);
        if (!(level.getBlockEntity(origin.toBlock()) instanceof ScreenBlockEntity entity)) return null;
        ScreenData screen = entity.getScreen(side);
        if (screen == null || side != face && !outerEdge(entity.getBlockPos(), clicked, screen, face)) return null;
        DataPort nearest = null;
        double best = Double.POSITIVE_INFINITY;
        Vec3 clickedCenter = Vec3.atCenterOf(clicked);
        for (DataPort port : ports(screen)) {
            double distance = worldPosition(entity.getBlockPos(), screen, port).distanceToSqr(clickedCenter);
            if (distance < best) {
                best = distance;
                nearest = port;
            }
        }
        return nearest == null ? null : new Endpoint(entity, screen, nearest);
    }

    private static boolean outerEdge(BlockPos origin, BlockPos clicked, ScreenData screen, BlockSide face) {
        int dx = clicked.getX() - origin.getX();
        int dy = clicked.getY() - origin.getY();
        int dz = clicked.getZ() - origin.getZ();
        int horizontal = dx * screen.side.right.x + dy * screen.side.right.y + dz * screen.side.right.z;
        int vertical = dx * screen.side.up.x + dy * screen.side.up.y + dz * screen.side.up.z;
        int normal = dx * screen.side.forward.x + dy * screen.side.forward.y + dz * screen.side.forward.z;
        int faceSide = face.forward.x * screen.side.right.x +
                face.forward.y * screen.side.right.y + face.forward.z * screen.side.right.z;
        int faceVertical = face.forward.x * screen.side.up.x +
                face.forward.y * screen.side.up.y + face.forward.z * screen.side.up.z;
        return normal == 0 && horizontal >= 0 && horizontal < screen.size.x &&
                vertical >= 0 && vertical < screen.size.y &&
                (faceSide < 0 && horizontal == 0 || faceSide > 0 && horizontal == screen.size.x - 1 ||
                        faceVertical < 0 && vertical == 0 || faceVertical > 0 && vertical == screen.size.y - 1);
    }

    private static boolean isBorderFace(BlockSide screen, BlockSide face) {
        int right = face.forward.x * screen.right.x + face.forward.y * screen.right.y + face.forward.z * screen.right.z;
        int up = face.forward.x * screen.up.x + face.forward.y * screen.up.y + face.forward.z * screen.up.z;
        return Math.abs(right) == 1 || Math.abs(up) == 1;
    }

    public static Endpoint get(Level level, BlockPos pos, BlockSide side, UUID portId) {
        if (pos == null || side == null || portId == null || !level.hasChunkAt(pos)) return null;
        BlockEntity block = level.getBlockEntity(pos);
        if (!(block instanceof ScreenBlockEntity entity)) return null;
        ScreenData screen = entity.getScreen(side);
        if (screen == null) return null;
        for (DataPort port : ports(screen))
            if (port.id.equals(portId)) return new Endpoint(entity, screen, port);
        return null;
    }

    public static CableEnd hitAnyPort(Level level, BlockHitResult hit) {
        if (level.getBlockEntity(hit.getBlockPos()) instanceof NetworkSwitchBlockEntity networkSwitch) {
            int index = networkSwitch.portAtHit(hit);
            DataPort port = networkSwitch.getPort(index);
            return port == null ? null : new SwitchEndpoint(networkSwitch, index, port);
        }
        RackBlockEntity rack = RackBlockEntity.at(level, hit.getBlockPos());
        if (rack != null && hit.getDirection() == rack.getFacing()) {
            RackEndpoint nearest = null;
            double best = .14D * .14D;
            for (RackModule module : rack.getModules()) {
                for (int i = 0; i < module.portCount(); i++) {
                    Vec3 position = rack.portWorldPosition(module.id(), i);
                    double distance = position == null ? Double.POSITIVE_INFINITY : position.distanceToSqr(hit.getLocation());
                    if (distance < best) {
                        best = distance;
                        nearest = rackEndpoint(rack, module.id(), i);
                    }
                }
            }
            return nearest;
        }
        return hitPort(level, hit);
    }

    public static CableEnd getAny(Level level, BlockPos pos, BlockSide side, UUID portId, boolean switchPort) {
        if (pos == null || portId == null || !level.hasChunkAt(pos)) return null;
        if (!switchPort) return get(level, pos, side, portId);
        BlockEntity block = level.getBlockEntity(pos);
        if (block instanceof NetworkSwitchBlockEntity entity) {
            int index = entity.indexOfPort(portId);
            DataPort port = entity.getPort(index);
            return port == null ? null : new SwitchEndpoint(entity, index, port);
        }
        if (block instanceof RackBlockEntity rack) {
            RackModule module = rack.moduleByPort(portId);
            if (module == null) return null;
            for (int i = 0; i < module.portCount(); i++)
                if (portId.equals(module.port(i).id)) return rackEndpoint(rack, module.id(), i);
        }
        return null;
    }

    public static CableEnd other(Level level, CableEnd endpoint) {
        if (endpoint == null || !endpoint.port().connected()) return null;
        DataPort link = endpoint.port();
        CableEnd result = getAny(level, link.linkedPos, link.linkedSide, link.linkedPort, link.linkedSwitch);
        if (result == null || !result.port().connected() ||
                !endpoint.pos().equals(result.port().linkedPos) || !endpoint.port().id.equals(result.port().linkedPort) ||
                (endpoint instanceof Endpoint) == result.port().linkedSwitch) return null;
        if (endpoint instanceof Endpoint screen && result.port().linkedSide != screen.side()) return null;
        return result;
    }

    public static Vec3 worldPosition(CableEnd endpoint) {
        if (endpoint instanceof Endpoint screen)
            return worldPosition(screen.pos(), screen.screen(), screen.port());
        if (endpoint instanceof SwitchEndpoint port)
            return port.entity().portWorldPosition(port.index());
        RackEndpoint port = (RackEndpoint) endpoint;
        return port.rack().portWorldPosition(port.moduleId(), port.index());
    }

    public static BlockSide mountFace(CableEnd endpoint) {
        return endpoint instanceof Endpoint screen ? mountFace(screen.screen(), screen.port()) : endpoint.port().mountFace;
    }

    public static boolean canManage(CableEnd endpoint, Player player) {
        return endpoint instanceof SwitchEndpoint networkSwitch &&
                        (!(networkSwitch.entity() instanceof ManagedSwitchBlockEntity managed) ||
                                managed.canConfigure(player)) ||
                endpoint instanceof RackEndpoint rack && rack.rack().canConfigure(player) ||
                endpoint instanceof Endpoint screen &&
                        (screen.screen().rightsFor(player) & com.netcattest.ncatminecraft.core.ScreenRights.MANAGE_UPGRADES) != 0;
    }

    public static void sync(CableEnd endpoint) {
        if (endpoint instanceof Endpoint screen) sync(screen.entity(), screen.screen());
        else if (endpoint instanceof SwitchEndpoint networkSwitch) networkSwitch.entity().sync();
        else ((RackEndpoint) endpoint).rack().sync();
    }

    public static float[] uv(BlockPos origin, ScreenData screen, Vec3 hit) {
        Vec3 relative = hit.subtract(Vec3.atCenterOf(origin));
        Vector3i right = screen.side.right, up = screen.side.up;
        double x = relative.x * right.x + relative.y * right.y + relative.z * right.z;
        double y = relative.x * up.x + relative.y * up.y + relative.z * up.z;
        float u = (float) ((x + .5) / Math.max(1, screen.size.x));
        float v = (float) ((y + .5) / Math.max(1, screen.size.y));
        return new float[]{Math.max(0f, Math.min(1f, u)), Math.max(0f, Math.min(1f, v))};
    }

    public static boolean addPort(Endpoint target, BlockHitResult hit) {
        ScreenData screen = target.screen;
        if (screen.dataPorts.size() >= MAX_PORTS) return false;
        BlockSide face = BlockSide.fromInt(hit.getDirection().ordinal());
        if (face == null) return false;
        float[] point = uv(target.pos(), screen, hit.getLocation());
        DataPort candidate;
        if (isBorderFace(screen.side, face) && outerEdge(target.pos(), hit.getBlockPos(), screen, face)) {
            int side = face.forward.x * screen.side.right.x + face.forward.y * screen.side.right.y +
                    face.forward.z * screen.side.right.z;
            int vertical = face.forward.x * screen.side.up.x + face.forward.y * screen.side.up.y +
                    face.forward.z * screen.side.up.z;
            float u = side < 0 ? 0f : side > 0 ? 1f : point[0];
            float v = vertical < 0 ? 0f : vertical > 0 ? 1f : clampedVertical(point[1], screen.size.y);
            candidate = new DataPort(UUID.randomUUID(), u, v, false);
            candidate.mountFace = face;
            Vec3 local = hit.getLocation().subtract(Vec3.atCenterOf(hit.getBlockPos()));
            double forward = local.x * screen.side.forward.x + local.y * screen.side.forward.y +
                    local.z * screen.side.forward.z;
            candidate.depth = (float) Math.max(.1, Math.min(.9, forward + .5));
        } else return false;
        for (DataPort current : screen.dataPorts)
            if (worldPosition(target.pos(), screen, current).distanceToSqr(worldPosition(target.pos(), screen, candidate)) < .16)
                return false;
        screen.dataPorts.add(candidate);
        sync(target.entity, screen);
        return true;
    }

    private static float clampedVertical(float v, int height) {
        float margin = .12f / Math.max(1, height);
        return Math.max(margin, Math.min(1f - margin, v));
    }

    public static boolean connect(Level level, Endpoint first, Endpoint second) {
        return connect(level, (CableEnd) first, second);
    }

    public static boolean connect(Level level, CableEnd first, CableEnd second) {
        if (first == null || second == null || first.port().id.equals(second.port().id) ||
                first.pos().equals(second.pos()) && !(first instanceof RackEndpoint && second instanceof RackEndpoint) ||
                first instanceof RackEndpoint rackA && second instanceof RackEndpoint rackB &&
                        rackA.rack() == rackB.rack() && rackA.moduleId().equals(rackB.moduleId()) ||
                !available(level, first) || !available(level, second)) return false;
        Vec3 start = worldPosition(first), end = worldPosition(second);
        if (start == null || end == null || start.distanceToSqr(end) > MAX_LENGTH * MAX_LENGTH) return false;
        if (first instanceof Endpoint a && second instanceof Endpoint b && !validDirectPair(a, b)) return false;
        link(first, second);
        link(second, first);
        sync(first);
        sync(second);
        refreshAround(level, first, second);
        return true;
    }

    public static boolean available(Level level, CableEnd endpoint) {
        if (level == null || endpoint == null) return false;
        if (!endpoint.port().connected()) return true;
        BlockPos remote = endpoint.port().linkedPos;
        if (remote == null || !level.hasChunkAt(remote) || other(level, endpoint) != null) return false;
        endpoint.port().disconnect();
        sync(endpoint);
        return true;
    }

    private static boolean validDirectPair(Endpoint first, Endpoint second) {
        if (BlockRegistry.isWorkstationScreen(first.entity().getBlockState().getBlock()) ||
                BlockRegistry.isWorkstationScreen(second.entity().getBlockState().getBlock())) return true;
        if (needsBrowser(first.entity()) && !BlockRegistry.isBrowserScreen(second.entity().getBlockState().getBlock()) ||
                needsBrowser(second.entity()) && !BlockRegistry.isBrowserScreen(first.entity().getBlockState().getBlock()) ||
                needsSsh(first.entity()) && !BlockRegistry.isSshScreen(second.entity().getBlockState().getBlock()) ||
                needsSsh(second.entity()) && !BlockRegistry.isSshScreen(first.entity().getBlockState().getBlock()))
            return false;
        boolean browserSsh = BlockRegistry.isBrowserScreen(first.entity().getBlockState().getBlock()) &&
                BlockRegistry.isSshScreen(second.entity().getBlockState().getBlock()) ||
                BlockRegistry.isSshScreen(first.entity().getBlockState().getBlock()) &&
                        BlockRegistry.isBrowserScreen(second.entity().getBlockState().getBlock());
        return !browserSsh;
    }

    private static void link(CableEnd source, CableEnd target) {
        DataPort port = source.port();
        port.linkedPos = target.pos();
        port.linkedPort = target.port().id;
        port.linkedSwitch = !(target instanceof Endpoint);
        port.linkedSide = target instanceof Endpoint screen ? screen.side() : null;
    }

    public static boolean disconnect(Level level, Endpoint selected) {
        return disconnect(level, (CableEnd) selected);
    }

    public static boolean disconnect(Level level, CableEnd selected) {
        if (selected == null || !selected.port().connected()) return false;
        CableEnd remote = other(level, selected);
        selected.port().disconnect();
        sync(selected);
        if (remote != null) {
            remote.port().disconnect();
            sync(remote);
        }
        refreshAround(level, selected, remote);
        return true;
    }

    public static boolean waypoint(Level level, Endpoint selected, Vec3 point, boolean removeLast) {
        return waypoint(level, (CableEnd) selected, point, removeLast);
    }

    public static boolean waypoint(Level level, CableEnd selected, Vec3 point, boolean removeLast) {
        CableEnd remote = other(level, selected);
        if (remote == null) return false;
        if (removeLast) {
            if (selected.port().waypoints.isEmpty()) return false;
            selected.port().waypoints.remove(selected.port().waypoints.size() - 1);
        } else {
            if (selected.port().waypoints.size() >= 12 ||
                    worldPosition(selected).distanceToSqr(point) > 64 * 64 ||
                    worldPosition(remote).distanceToSqr(point) > 64 * 64) return false;
            selected.port().waypoints.add(point);
            double length = 0;
            Vec3 previous = worldPosition(selected);
            for (Vec3 waypoint : selected.port().waypoints) {
                length += previous.distanceTo(waypoint);
                previous = waypoint;
            }
            length += previous.distanceTo(worldPosition(remote));
            if (length > MAX_LENGTH) {
                selected.port().waypoints.remove(selected.port().waypoints.size() - 1);
                return false;
            }
        }
        remote.port().waypoints.clear();
        for (int i = selected.port().waypoints.size() - 1; i >= 0; i--)
            remote.port().waypoints.add(selected.port().waypoints.get(i));
        sync(selected);
        sync(remote);
        return true;
    }

    public static void sync(ScreenBlockEntity entity, ScreenData screen) {
        entity.setChanged();
        Level level = entity.getLevel();
        if (level == null || level.isClientSide) return;
        BlockPos pos = entity.getBlockPos();
        WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() ->
                new PacketDistributor.TargetPoint(pos.getX(), pos.getY(), pos.getZ(), 112, level.dimension())),
                new S2CMessageAddScreen(entity, screen));
    }

    private static boolean needsBrowser(ScreenBlockEntity entity) {
        return BlockRegistry.isLogScreen(entity.getBlockState().getBlock()) ||
                BlockRegistry.isDevToolsScreen(entity.getBlockState().getBlock()) ||
                BlockRegistry.isProxyScreen(entity.getBlockState().getBlock());
    }

    private static boolean needsSsh(ScreenBlockEntity entity) {
        return BlockRegistry.isSftpScreen(entity.getBlockState().getBlock());
    }

    public static List<ScreenEndpoint> connectedScreens(Level level, ScreenBlockEntity root, BlockSide side) {
        if (level == null || root == null || side == null || root.getScreen(side) == null) return List.of();
        ScreenData screen = root.getScreen(side);
        ArrayList<CableEnd> starts = new ArrayList<>();
        for (DataPort port : ports(screen)) starts.add(new Endpoint(root, screen, port));
        return sortedScreens(discover(level, starts, new ScreenEndpoint(root, side), null).screens);
    }

    public static List<ScreenEndpoint> connectedScreens(Level level, RackBlockEntity rack, UUID moduleId) {
        RackModule module = rack == null ? null : rack.getModule(moduleId);
        if (level == null || module == null || !module.powered()) return List.of();
        ArrayList<CableEnd> starts = new ArrayList<>();
        for (int i = 0; i < module.portCount(); i++) starts.add(rackEndpoint(rack, moduleId, i));
        return sortedScreens(discover(level, starts, null, new RackModuleEndpoint(rack, module)).screens);
    }

    public static List<RackModuleEndpoint> connectedRackModules(Level level, ScreenBlockEntity root, BlockSide side) {
        if (level == null || root == null || side == null || root.getScreen(side) == null) return List.of();
        ScreenData screen = root.getScreen(side);
        ArrayList<CableEnd> starts = new ArrayList<>();
        for (DataPort port : ports(screen)) starts.add(new Endpoint(root, screen, port));
        return sortedRackModules(discover(level, starts, new ScreenEndpoint(root, side), null).modules);
    }

    public static List<RackModuleEndpoint> connectedRackModules(Level level, RackBlockEntity rack, UUID moduleId) {
        RackModule module = rack == null ? null : rack.getModule(moduleId);
        if (level == null || module == null || !module.powered()) return List.of();
        ArrayList<CableEnd> starts = new ArrayList<>();
        for (int i = 0; i < module.portCount(); i++) starts.add(rackEndpoint(rack, moduleId, i));
        return sortedRackModules(discover(level, starts, null, new RackModuleEndpoint(rack, module)).modules);
    }

    private static List<ScreenEndpoint> sortedScreens(Map<ScreenEndpoint, Integer> discovered) {
        return discovered.entrySet().stream()
                .sorted(Comparator.<Map.Entry<ScreenEndpoint, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparingInt(e -> e.getKey().entity().getBlockPos().getX())
                        .thenComparingInt(e -> e.getKey().entity().getBlockPos().getY())
                        .thenComparingInt(e -> e.getKey().entity().getBlockPos().getZ())
                        .thenComparingInt(e -> e.getKey().side().ordinal()))
                .map(Map.Entry::getKey).toList();
    }

    private static List<RackModuleEndpoint> sortedRackModules(Map<RackModuleEndpoint, Integer> discovered) {
        return discovered.entrySet().stream()
                .sorted(Comparator.<Map.Entry<RackModuleEndpoint, Integer>>comparingInt(Map.Entry::getValue)
                        .thenComparingInt(e -> e.getKey().rack().getBlockPos().getX())
                        .thenComparingInt(e -> e.getKey().rack().getBlockPos().getY())
                        .thenComparingInt(e -> e.getKey().rack().getBlockPos().getZ())
                        .thenComparingInt(e -> e.getKey().module().startU())
                        .thenComparing(e -> e.getKey().module().id()))
                .map(Map.Entry::getKey).toList();
    }

    private record SwitchState(BlockPos pos, UUID moduleId, int ingress, int vlan) { }
    private record SwitchNodeKey(BlockPos pos, UUID moduleId) { }

    private record SwitchHop(CableEnd ingress, int vlan, int distance) { }

    private static final class Discovery {
        final Map<ScreenEndpoint, Integer> screens = new HashMap<>();
        final Map<RackModuleEndpoint, Integer> modules = new HashMap<>();
        final Set<SwitchState> visited = new HashSet<>();
        final ArrayDeque<SwitchHop> queue = new ArrayDeque<>();
    }

    private static Discovery discover(Level level, List<CableEnd> starts,
                                      ScreenEndpoint rootScreen, RackModuleEndpoint rootModule) {
        Discovery result = new Discovery();
        for (CableEnd start : starts)
            visitRemote(other(level, start), 1, 0, rootScreen, rootModule, result);
        while (!result.queue.isEmpty()) {
            SwitchHop hop = result.queue.removeFirst();
            for (int i = 0; i < switchPortCount(hop.ingress()); i++) {
                int vlan = forwardVlan(hop.ingress(), i, hop.vlan());
                if (vlan < 0) continue;
                CableEnd exit = switchPort(hop.ingress(), i);
                if (exit != null)
                    visitRemote(other(level, exit), hop.distance() + 1, vlan,
                            rootScreen, rootModule, result);
            }
        }
        return result;
    }

    private static void visitRemote(CableEnd remote, int distance, int incomingVlan,
                                    ScreenEndpoint rootScreen, RackModuleEndpoint rootModule,
                                    Discovery result) {
        if (remote instanceof Endpoint endpoint) {
            ScreenEndpoint found = new ScreenEndpoint(endpoint.entity(), endpoint.side());
            if (!found.equals(rootScreen)) result.screens.merge(found, distance, Math::min);
        } else if (remote instanceof RackEndpoint endpoint && !isSwitch(endpoint)) {
            RackModule module = endpoint.module();
            if (module == null || !module.powered()) return;
            RackModuleEndpoint found = new RackModuleEndpoint(endpoint.rack(), module);
            if (!found.equals(rootModule)) result.modules.merge(found, distance, Math::min);
        } else if (isSwitch(remote)) {
            int vlan = ingressVlan(remote, incomingVlan);
            SwitchState state = switchState(remote, vlan);
            if (vlan >= 0 && result.visited.add(state))
                result.queue.addLast(new SwitchHop(remote, vlan, distance));
        }
    }

    private static boolean isSwitch(CableEnd endpoint) {
        if (endpoint instanceof SwitchEndpoint) return true;
        return endpoint instanceof RackEndpoint rack && rack.module() != null && rack.module().type().isSwitch();
    }

    private static SwitchState switchState(CableEnd endpoint, int vlan) {
        return new SwitchState(endpoint.pos(), endpoint instanceof RackEndpoint rack ? rack.moduleId() : null,
                endpoint instanceof SwitchEndpoint networkSwitch ? networkSwitch.index() : ((RackEndpoint) endpoint).index(),
                vlan);
    }

    private static SwitchNodeKey switchNode(CableEnd endpoint) {
        return new SwitchNodeKey(endpoint.pos(), endpoint instanceof RackEndpoint rack ? rack.moduleId() : null);
    }

    private static int switchPortCount(CableEnd endpoint) {
        if (endpoint instanceof SwitchEndpoint) return NetworkSwitchBlockEntity.PORT_COUNT;
        RackModule module = ((RackEndpoint) endpoint).module();
        return module == null ? 0 : module.portCount();
    }

    private static CableEnd switchPort(CableEnd ingress, int index) {
        if (ingress instanceof SwitchEndpoint networkSwitch) {
            DataPort port = networkSwitch.entity().getPort(index);
            return port == null ? null : new SwitchEndpoint(networkSwitch.entity(), index, port);
        }
        RackEndpoint rack = (RackEndpoint) ingress;
        return rackEndpoint(rack.rack(), rack.moduleId(), index);
    }

    private static int ingressVlan(CableEnd endpoint, int incomingVlan) {
        if (endpoint instanceof SwitchEndpoint networkSwitch) {
            NetworkSwitchBlockEntity entity = networkSwitch.entity();
            if (!entity.powered() || incomingVlan < 0) return -1;
            return entity instanceof ManagedSwitchBlockEntity managed
                    ? managed.acceptIngressVlan(networkSwitch.index(), incomingVlan) : incomingVlan;
        }
        if (!(endpoint instanceof RackEndpoint rack)) return -1;
        RackModule module = rack.module();
        return module == null ? -1 : module.acceptIngressVlan(rack.index(), incomingVlan);
    }

    private static int forwardVlan(CableEnd ingress, int egress, int vlan) {
        int ingressIndex = ingress instanceof SwitchEndpoint networkSwitch ? networkSwitch.index() :
                ((RackEndpoint) ingress).index();
        if (ingressIndex == egress || egress < 0 || egress >= switchPortCount(ingress)) return -1;
        if (ingress instanceof SwitchEndpoint networkSwitch) {
            NetworkSwitchBlockEntity entity = networkSwitch.entity();
            return entity.powered() && !(entity instanceof ManagedSwitchBlockEntity managed &&
                    !managed.allowsForward(ingressIndex, egress, vlan)) ? vlan : -1;
        }
        RackModule module = ((RackEndpoint) ingress).module();
        return module != null && module.allowsForward(ingressIndex, egress, vlan) ? vlan : -1;
    }

    public static boolean recordTraffic(Level level, ScreenBlockEntity from, BlockSide fromSide,
                                        ScreenBlockEntity to, BlockSide toSide) {
        if (level == null || level.isClientSide || from == null || to == null || fromSide == null || toSide == null ||
                from == to && fromSide == toSide) return false;
        ScreenData firstScreen = from.getScreen(fromSide);
        if (firstScreen == null || to.getScreen(toSide) == null) return false;
        ArrayList<CableEnd> starts = new ArrayList<>();
        for (DataPort port : ports(firstScreen)) starts.add(new Endpoint(from, firstScreen, port));
        return route(level, starts, endpoint -> endpoint instanceof Endpoint screen &&
                screen.entity() == to && screen.side() == toSide);
    }

    public static boolean recordRackTraffic(Level level, ScreenBlockEntity from, BlockSide fromSide,
                                            RackModuleEndpoint to) {
        if (level == null || level.isClientSide || from == null || fromSide == null || to == null ||
                from.getScreen(fromSide) == null || to.module() == null || !to.module().powered()) return false;
        ArrayList<CableEnd> starts = new ArrayList<>();
        ScreenData screen = from.getScreen(fromSide);
        for (DataPort port : ports(screen)) starts.add(new Endpoint(from, screen, port));
        return route(level, starts, endpoint -> endpoint instanceof RackEndpoint rack &&
                rack.rack() == to.rack() && rack.moduleId().equals(to.module().id()));
    }

    public static boolean recordRackTraffic(Level level, RackModuleEndpoint from,
                                            RackModuleEndpoint to) {
        if (level == null || level.isClientSide || from == null || to == null ||
                from.rack() == to.rack() && from.module().id().equals(to.module().id()) ||
                !from.module().powered() || !to.module().powered()) return false;
        ArrayList<CableEnd> starts = new ArrayList<>();
        for (int i = 0; i < from.module().portCount(); i++)
            starts.add(rackEndpoint(from.rack(), from.module().id(), i));
        return route(level, starts, endpoint -> endpoint instanceof RackEndpoint rack &&
                rack.rack() == to.rack() && rack.moduleId().equals(to.module().id()));
    }

    public static boolean recordRackTraffic(Level level, RackModuleEndpoint from,
                                            ScreenBlockEntity to, BlockSide toSide) {
        if (level == null || level.isClientSide || from == null || to == null || toSide == null ||
                !from.module().powered() || to.getScreen(toSide) == null) return false;
        ArrayList<CableEnd> starts = new ArrayList<>();
        for (int i = 0; i < from.module().portCount(); i++)
            starts.add(rackEndpoint(from.rack(), from.module().id(), i));
        return route(level, starts, endpoint -> endpoint instanceof Endpoint screen &&
                screen.entity() == to && screen.side() == toSide);
    }

    private record TrafficRoute(CableEnd ingress, int vlan, CableEnd source, List<CableEnd> path) { }

    private static boolean route(Level level, List<CableEnd> starts,
                                 java.util.function.Predicate<CableEnd> destination) {
        ArrayDeque<TrafficRoute> queue = new ArrayDeque<>();
        Set<SwitchState> visited = new HashSet<>();
        for (CableEnd source : starts) {
            CableEnd remote = other(level, source);
            if (destination.test(remote) && targetPowered(remote)) {
                pulse(level, source);
                pulse(level, remote);
                return true;
            }
            if (isSwitch(remote)) {
                int vlan = ingressVlan(remote, 0);
                if (vlan >= 0 && visited.add(switchState(remote, vlan)))
                    queue.addLast(new TrafficRoute(remote, vlan, source, List.of(remote)));
            }
        }
        while (!queue.isEmpty()) {
            TrafficRoute route = queue.removeFirst();
            for (int i = 0; i < switchPortCount(route.ingress()); i++) {
                int vlan = forwardVlan(route.ingress(), i, route.vlan());
                if (vlan < 0) continue;
                CableEnd exit = switchPort(route.ingress(), i);
                if (exit == null) continue;
                CableEnd remote = other(level, exit);
                if (destination.test(remote) && targetPowered(remote)) {
                    pulse(level, route.source());
                    for (CableEnd step : route.path()) pulse(level, step);
                    pulse(level, exit);
                    pulse(level, remote);
                    return true;
                }
                if (isSwitch(remote)) {
                    int nextVlan = ingressVlan(remote, vlan);
                    if (nextVlan < 0 || !visited.add(switchState(remote, nextVlan))) continue;
                    ArrayList<CableEnd> path = new ArrayList<>(route.path());
                    path.add(exit);
                    path.add(remote);
                    queue.addLast(new TrafficRoute(remote, nextVlan, route.source(), path));
                }
            }
        }
        return false;
    }

    private static boolean targetPowered(CableEnd endpoint) {
        return endpoint instanceof RackEndpoint rack ? rack.module() != null && rack.module().powered() : endpoint != null;
    }

    private static void pulse(Level level, CableEnd endpoint) {
        if (endpoint == null || level.getGameTime() - endpoint.port().lastTrafficTick < 3L) return;
        if (endpoint instanceof SwitchEndpoint networkSwitch) {
            networkSwitch.entity().recordTraffic(networkSwitch.index());
        } else {
            endpoint.port().recordTraffic(level.getGameTime());
            sync(endpoint);
        }
    }

    private static void refreshAround(Level level, CableEnd first, CableEnd second) {
        if (level == null || level.isClientSide) return;
        Set<ScreenEndpoint> affected = new HashSet<>();
        collectScreens(level, first, affected);
        collectScreens(level, second, affected);
        for (ScreenEndpoint endpoint : affected) refreshSource(level, endpoint);
    }

    private static void collectScreens(Level level, CableEnd start, Set<ScreenEndpoint> affected) {
        if (start == null) return;
        if (start instanceof Endpoint endpoint) {
            affected.add(new ScreenEndpoint(endpoint.entity(), endpoint.side()));
            for (DataPort port : ports(endpoint.screen())) {
                CableEnd remote = other(level, new Endpoint(endpoint.entity(), endpoint.screen(), port));
                if (remote instanceof Endpoint screen)
                    affected.add(new ScreenEndpoint(screen.entity(), screen.side()));
                else if (isSwitch(remote)) collectSwitchScreens(level, remote, affected);
            }
        } else if (isSwitch(start)) {
            collectSwitchScreens(level, start, affected);
        } else if (start instanceof RackEndpoint rack) {
            RackModule module = rack.module();
            if (module == null) return;
            for (int i = 0; i < module.portCount(); i++) {
                CableEnd remote = other(level, rackEndpoint(rack.rack(), rack.moduleId(), i));
                if (remote instanceof Endpoint screen)
                    affected.add(new ScreenEndpoint(screen.entity(), screen.side()));
                else if (isSwitch(remote)) collectSwitchScreens(level, remote, affected);
            }
        }
    }

    private static void collectSwitchScreens(Level level, CableEnd start, Set<ScreenEndpoint> affected) {
        Set<SwitchNodeKey> seen = new HashSet<>();
        ArrayDeque<CableEnd> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            CableEnd current = queue.removeFirst();
            if (!seen.add(switchNode(current))) continue;
            for (int i = 0; i < switchPortCount(current); i++) {
                CableEnd exit = switchPort(current, i);
                if (exit == null) continue;
                CableEnd remote = other(level, exit);
                if (remote instanceof Endpoint screen)
                    affected.add(new ScreenEndpoint(screen.entity(), screen.side()));
                else if (isSwitch(remote)) queue.addLast(remote);
            }
        }
    }

    public static List<ScreenEndpoint> physicalScreensBehindSwitchPort(Level level,
                                                                        NetworkSwitchBlockEntity networkSwitch,
                                                                        int index) {
        if (level == null || networkSwitch == null || index < 0 ||
                index >= NetworkSwitchBlockEntity.PORT_COUNT) return List.of();
        DataPort port = networkSwitch.getPort(index);
        if (port == null) return List.of();
        CableEnd first = other(level, new SwitchEndpoint(networkSwitch, index, port));
        if (first == null) return List.of();
        Set<ScreenEndpoint> found = new HashSet<>();
        Set<SwitchNodeKey> seen = new HashSet<>();
        seen.add(switchNode(new SwitchEndpoint(networkSwitch, index, port)));
        ArrayDeque<CableEnd> queue = new ArrayDeque<>();
        if (first instanceof Endpoint screen) found.add(new ScreenEndpoint(screen.entity(), screen.side()));
        else if (isSwitch(first)) queue.add(first);
        while (!queue.isEmpty()) {
            CableEnd current = queue.removeFirst();
            if (!seen.add(switchNode(current))) continue;
            for (int i = 0; i < switchPortCount(current); i++) {
                CableEnd exit = switchPort(current, i);
                if (exit == null) continue;
                CableEnd remote = other(level, exit);
                if (remote instanceof Endpoint screen)
                    found.add(new ScreenEndpoint(screen.entity(), screen.side()));
                else if (isSwitch(remote)) queue.addLast(remote);
            }
        }
        return found.stream().sorted(Comparator.comparingInt((ScreenEndpoint endpoint) -> endpoint.entity().getBlockPos().getX())
                .thenComparingInt(endpoint -> endpoint.entity().getBlockPos().getY())
                .thenComparingInt(endpoint -> endpoint.entity().getBlockPos().getZ())
                .thenComparingInt(endpoint -> endpoint.side().ordinal())).toList();
    }

    public static List<RackModuleEndpoint> physicalRackModulesBehindSwitchPort(Level level,
                                                                                NetworkSwitchBlockEntity networkSwitch,
                                                                                int index) {
        if (level == null || networkSwitch == null || index < 0 ||
                index >= NetworkSwitchBlockEntity.PORT_COUNT) return List.of();
        DataPort port = networkSwitch.getPort(index);
        if (port == null) return List.of();
        CableEnd first = other(level, new SwitchEndpoint(networkSwitch, index, port));
        if (first == null) return List.of();
        Set<RackModuleEndpoint> found = new HashSet<>();
        Set<SwitchNodeKey> seen = new HashSet<>();
        seen.add(switchNode(new SwitchEndpoint(networkSwitch, index, port)));
        ArrayDeque<CableEnd> queue = new ArrayDeque<>();
        if (first instanceof RackEndpoint rack && !isSwitch(rack) && rack.module() != null)
            found.add(new RackModuleEndpoint(rack.rack(), rack.module()));
        else if (isSwitch(first)) queue.add(first);
        while (!queue.isEmpty()) {
            CableEnd current = queue.removeFirst();
            if (!seen.add(switchNode(current))) continue;
            for (int i = 0; i < switchPortCount(current); i++) {
                CableEnd exit = switchPort(current, i);
                if (exit == null) continue;
                CableEnd remote = other(level, exit);
                if (remote instanceof RackEndpoint rack && !isSwitch(rack) && rack.module() != null)
                    found.add(new RackModuleEndpoint(rack.rack(), rack.module()));
                else if (isSwitch(remote)) queue.addLast(remote);
            }
        }
        return found.stream().sorted(Comparator.comparingInt((RackModuleEndpoint endpoint) -> endpoint.rack().getBlockPos().getX())
                .thenComparingInt(endpoint -> endpoint.rack().getBlockPos().getY())
                .thenComparingInt(endpoint -> endpoint.rack().getBlockPos().getZ())
                .thenComparingInt(endpoint -> endpoint.module().startU())).toList();
    }

    public static void refreshSwitch(Level level, NetworkSwitchBlockEntity networkSwitch) {
        if (level == null || level.isClientSide || networkSwitch == null) return;
        Set<ScreenEndpoint> affected = new HashSet<>();
        for (int i = 0; i < NetworkSwitchBlockEntity.PORT_COUNT; i++) {
            DataPort port = networkSwitch.getPort(i);
            if (port != null) collectSwitchScreens(level, new SwitchEndpoint(networkSwitch, i, port), affected);
        }
        for (ScreenEndpoint endpoint : affected) refreshSource(level, endpoint);
    }

    public static void refreshRack(Level level, RackBlockEntity rack) {
        if (level == null || level.isClientSide || rack == null) return;
        Set<ScreenEndpoint> affected = new HashSet<>();
        for (RackModule module : rack.getModules())
            for (int i = 0; i < module.portCount(); i++)
                collectScreens(level, rackEndpoint(rack, module.id(), i), affected);
        for (ScreenEndpoint endpoint : affected) refreshSource(level, endpoint);
    }

    public static void detachRackModule(Level level, RackBlockEntity rack, RackModule module) {
        if (level == null || level.isClientSide || rack == null || module == null) return;
        for (int i = 0; i < module.portCount(); i++) {
            RackEndpoint endpoint = rackEndpoint(rack, module.id(), i);
            if (endpoint != null && endpoint.port().connected()) disconnect(level, endpoint);
        }
        refreshRack(level, rack);
    }

    public static void detachSwitch(Level level, NetworkSwitchBlockEntity networkSwitch) {
        if (level == null || level.isClientSide || networkSwitch == null) return;
        for (int i = 0; i < NetworkSwitchBlockEntity.PORT_COUNT; i++) {
            DataPort port = networkSwitch.getPort(i);
            if (port != null && port.connected()) disconnect(level, new SwitchEndpoint(networkSwitch, i, port));
        }
    }

    public static void queueScreenRemoval(Level level, BlockPos removed) {
        if (level == null || level.isClientSide) return;
        Set<PendingPort> pending = PENDING_REMOVALS.computeIfAbsent(level, ignored -> new HashSet<>());
        for (BlockSide side : BlockSide.values()) {
            Vector3i origin = new Vector3i(removed);
            Multiblock.findOrigin(level, origin, side, null);
            if (!(level.getBlockEntity(origin.toBlock()) instanceof ScreenBlockEntity entity)) continue;
            ScreenData screen = entity.getScreen(side);
            if (screen == null) continue;
            for (DataPort port : ports(screen)) {
                if (!port.connected()) continue;
                pending.add(new PendingPort(port.linkedPos, port.linkedSide, port.linkedPort, port.linkedSwitch));
            }
        }
    }

    public static void processPendingRemovals(Level level) {
        if (level == null || level.isClientSide) return;
        Set<PendingPort> pending = PENDING_REMOVALS.remove(level);
        if (pending == null || pending.isEmpty()) return;
        Set<ScreenEndpoint> affected = new HashSet<>();
        for (PendingPort port : pending)
            collectScreens(level, getAny(level, port.pos(), port.side(), port.id(), port.switchPort()), affected);
        for (ScreenEndpoint endpoint : affected) refreshSource(level, endpoint);
    }

    public static void refreshRestoredScreen(Level level, ScreenBlockEntity entity, BlockSide side) {
        if (level == null || level.isClientSide || entity == null || side == null) return;
        ScreenData screen = entity.getScreen(side);
        if (screen == null) return;
        for (DataPort port : ports(screen))
            refreshAround(level, new Endpoint(entity, screen, port), other(level, new Endpoint(entity, screen, port)));
    }

    private static void refreshSource(Level level, ScreenEndpoint dependent) {
        ScreenBlockEntity entity = dependent.entity();
        ScreenData screen = entity.getScreen(dependent.side());
        if (screen == null) return;
        boolean browser = needsBrowser(entity);
        boolean ssh = needsSsh(entity);
        if (!browser && !ssh) return;
        if (screen.logSourcePos != null && !screen.logSourceViaCable) return;
        List<ScreenEndpoint> candidates = connectedScreens(level, entity, dependent.side()).stream()
                .filter(endpoint -> browser ? BlockRegistry.isBrowserScreen(endpoint.entity().getBlockState().getBlock())
                        : BlockRegistry.isSshScreen(endpoint.entity().getBlockState().getBlock()))
                .toList();
        ScreenEndpoint chosen = candidates.stream().filter(endpoint -> screen.logSourcePos != null &&
                endpoint.entity().getBlockPos().equals(screen.logSourcePos.toBlock()) &&
                endpoint.side() == screen.logSourceSide).findFirst().orElse(candidates.isEmpty() ? null : candidates.get(0));
        if (chosen == null && !screen.logSourceViaCable) return;
        if (chosen == null) {
            screen.logSourcePos = null;
            screen.logSourceSide = null;
            screen.logSourceViaCable = false;
        } else if (screen.logSourcePos == null || !chosen.entity().getBlockPos().equals(screen.logSourcePos.toBlock()) ||
                chosen.side() != screen.logSourceSide || !screen.logSourceViaCable) {
            screen.logSourcePos = new Vector3i(chosen.entity().getBlockPos());
            screen.logSourceSide = chosen.side();
            screen.logSourceViaCable = true;
        } else return;
        sync(entity, screen);
    }
}
