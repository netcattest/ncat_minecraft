package com.netcattest.ncatminecraft.utilities;

import com.netcattest.ncatminecraft.block.KeyboardBlockLeft;
import com.netcattest.ncatminecraft.block.KeyboardBlockRight;
import com.netcattest.ncatminecraft.block.ScreenBlock;
import com.netcattest.ncatminecraft.entity.KeyboardBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.entity.UsbPort;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

public final class UsbCableService {
    public static final int MAX_PORTS = 8;
    public static final double MAX_LENGTH = 48;

    private UsbCableService() { }

    public record Endpoint(ScreenBlockEntity entity, ScreenData screen, UsbPort port) {
        public BlockPos pos() { return entity.getBlockPos(); }
        public BlockSide side() { return screen.side; }
    }

    public static List<UsbPort> ports(ScreenData screen) {
        if (screen.usbPorts.stream().noneMatch(port -> port.automatic))
            screen.usbPorts.add(0, UsbPort.automatic());
        return screen.usbPorts;
    }

    public static Vec3 worldPosition(BlockPos origin, ScreenData screen, UsbPort port) {
        if (!port.automatic && port.block != null && port.face != null) {
            Vec3 normal = Vec3.atLowerCornerOf(port.face.getNormal());
            return Vec3.atLowerCornerOf(port.block).add(port.x, port.y, port.z).add(normal.scale(.065));
        }
        Vector3i right = screen.side.right, up = screen.side.up, front = screen.side.forward;
        return Vec3.atCenterOf(origin)
                .add(right.x * -.57, right.y * -.57, right.z * -.57)
                .add(up.x * -.35, up.y * -.35, up.z * -.35)
                .add(front.x * .35, front.y * .35, front.z * .35);
    }

    public static Direction automaticFace(ScreenData screen) {
        Vector3i right = screen.side.right;
        for (Direction direction : Direction.values())
            if (direction.getStepX() == -right.x && direction.getStepY() == -right.y &&
                    direction.getStepZ() == -right.z) return direction;
        return Direction.NORTH;
    }

    private static final double KEYBOARD_EDGE = .3125D;

    public static Vec3 keyboardPosition(KeyboardBlockEntity keyboard) {
        BlockPos pos = keyboard.getBlockPos();
        Direction direction = keyboard.getBlockState().hasProperty(KeyboardBlockLeft.FACING)
                ? keyboard.getBlockState().getValue(KeyboardBlockLeft.FACING) : Direction.SOUTH;
        return Vec3.atLowerCornerOf(pos).add(.5, .075, .5)
                .add(Vec3.atLowerCornerOf(direction.getNormal()).scale(KEYBOARD_EDGE));
    }

    public static KeyboardBlockEntity keyboard(Level level, BlockPos clicked) {
        var state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof KeyboardBlockLeft || state.getBlock() instanceof KeyboardBlockRight)) return null;
        return KeyboardBlockLeft.getTileEntity(state, level, clicked);
    }

    public static Endpoint hitScreen(Level level, BlockHitResult hit) {
        BlockPos clicked = hit.getBlockPos();
        if (!(level.getBlockState(clicked).getBlock() instanceof ScreenBlock)) return null;
        Endpoint best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (BlockSide side : BlockSide.values()) {
            Vector3i origin = new Vector3i(clicked);
            Multiblock.findOrigin(level, origin, side, null);
            if (!(level.getBlockEntity(origin.toBlock()) instanceof ScreenBlockEntity entity)) continue;
            ScreenData screen = entity.getScreen(side);
            if (screen == null || !contains(origin.toBlock(), screen, clicked)) continue;
            for (UsbPort port : ports(screen)) {
                double next = worldPosition(entity.getBlockPos(), screen, port).distanceToSqr(hit.getLocation());
                if (next < distance) {
                    distance = next;
                    best = new Endpoint(entity, screen, port);
                }
            }
        }
        return best;
    }

    public static Endpoint hitPort(Level level, BlockHitResult hit) {
        BlockPos clicked = hit.getBlockPos();
        if (!(level.getBlockState(clicked).getBlock() instanceof ScreenBlock)) return null;
        Endpoint best = null;
        double distance = .42 * .42;
        for (BlockSide side : BlockSide.values()) {
            Vector3i origin = new Vector3i(clicked);
            Multiblock.findOrigin(level, origin, side, null);
            if (!(level.getBlockEntity(origin.toBlock()) instanceof ScreenBlockEntity entity)) continue;
            ScreenData screen = entity.getScreen(side);
            if (screen == null || !contains(origin.toBlock(), screen, clicked)) continue;
            for (UsbPort port : ports(screen)) {
                Direction face = port.automatic ? automaticFace(screen) : port.face;
                if (face != hit.getDirection()) continue;
                double next = worldPosition(entity.getBlockPos(), screen, port).distanceToSqr(hit.getLocation());
                if (next < distance) {
                    distance = next;
                    best = new Endpoint(entity, screen, port);
                }
            }
        }
        return best;
    }

    public static Endpoint get(Level level, BlockPos origin, BlockSide side, UUID portId) {
        if (origin == null || side == null || portId == null || !level.hasChunkAt(origin)) return null;
        if (!(level.getBlockEntity(origin) instanceof ScreenBlockEntity entity)) return null;
        ScreenData screen = entity.getScreen(side);
        if (screen == null) return null;
        for (UsbPort port : ports(screen))
            if (port.id.equals(portId)) return new Endpoint(entity, screen, port);
        return null;
    }

    public static boolean addPort(Level level, Endpoint target, BlockHitResult hit) {
        if (target == null || target.screen().usbPorts.size() >= MAX_PORTS) return false;
        BlockSide face = BlockSide.fromInt(hit.getDirection().ordinal());
        if (face == null || target.entity().getScreen(face) != null) return false;
        Vector3i normal = target.screen().side.forward;
        if (hit.getDirection().getStepX() * normal.x + hit.getDirection().getStepY() * normal.y +
                hit.getDirection().getStepZ() * normal.z != 0) return false;
        Vector3i faceOrigin = new Vector3i(hit.getBlockPos());
        Multiblock.findOrigin(level, faceOrigin, face, null);
        if (level.getBlockEntity(faceOrigin.toBlock()) instanceof ScreenBlockEntity facing &&
                facing.getScreen(face) != null) return false;
        if (level.getBlockState(hit.getBlockPos().relative(hit.getDirection())).getBlock() ==
                level.getBlockState(hit.getBlockPos()).getBlock()) return false;
        if (!contains(target.pos(), target.screen(), hit.getBlockPos())) return false;
        Vec3 relative = hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos()));
        UsbPort port = new UsbPort(UUID.randomUUID(), false);
        port.block = hit.getBlockPos();
        port.face = hit.getDirection();
        port.x = (float) Math.max(.10, Math.min(.90, relative.x));
        port.y = (float) Math.max(.10, Math.min(.90, relative.y));
        port.z = (float) Math.max(.10, Math.min(.90, relative.z));
        switch (port.face) {
            case EAST -> port.x = 1;
            case WEST -> port.x = 0;
            case UP -> port.y = 1;
            case DOWN -> port.y = 0;
            case SOUTH -> port.z = 1;
            case NORTH -> port.z = 0;
        }
        Vec3 position = worldPosition(target.pos(), target.screen(), port);
        for (UsbPort current : target.screen().usbPorts)
            if (worldPosition(target.pos(), target.screen(), current).distanceToSqr(position) < .17 * .17) return false;
        target.screen().usbPorts.add(port);
        DataCableService.sync(target.entity(), target.screen());
        return true;
    }

    public static boolean connect(Level level, Endpoint endpoint, KeyboardBlockEntity keyboard) {
        if (endpoint == null || keyboard == null || endpoint.port().connected() || keyboard.usbLinked()) return false;
        if (worldPosition(endpoint.pos(), endpoint.screen(), endpoint.port()).distanceToSqr(keyboardPosition(keyboard)) > MAX_LENGTH * MAX_LENGTH)
            return false;
        if (!keyboard.connectUsb(endpoint.pos(), endpoint.side(), endpoint.port().id)) return false;
        endpoint.port().keyboard = keyboard.getBlockPos();
        DataCableService.sync(endpoint.entity(), endpoint.screen());
        return true;
    }

    public static boolean valid(KeyboardBlockEntity keyboard) {
        if (!keyboard.usbLinked() || keyboard.getLevel() == null) return true;
        if (keyboard.getScreenPos() == null || keyboard.getScreenSide() == null) return false;
        Endpoint endpoint = get(keyboard.getLevel(), keyboard.getScreenPos().toBlock(), keyboard.getScreenSide(), keyboard.usbPortId());
        return endpoint != null && keyboard.getBlockPos().equals(endpoint.port().keyboard);
    }

    public static boolean disconnect(Level level, Endpoint endpoint) {
        if (endpoint == null || endpoint.port().keyboard == null) return false;
        KeyboardBlockEntity keyboard = keyboard(level, endpoint.port().keyboard);
        if (keyboard != null && endpoint.port().id.equals(keyboard.usbPortId())) keyboard.disconnectUsb();
        endpoint.port().disconnect();
        DataCableService.sync(endpoint.entity(), endpoint.screen());
        return true;
    }

    public static void disconnectKeyboard(Level level, KeyboardBlockEntity keyboard) {
        if (!keyboard.usbLinked() || keyboard.getScreenPos() == null || keyboard.getScreenSide() == null) return;
        Endpoint endpoint = get(level, keyboard.getScreenPos().toBlock(), keyboard.getScreenSide(), keyboard.usbPortId());
        if (endpoint != null && keyboard.getBlockPos().equals(endpoint.port().keyboard)) disconnect(level, endpoint);
        else keyboard.disconnectUsb();
    }

    public static boolean waypoint(Level level, Endpoint endpoint, Vec3 point, boolean removeLast) {
        if (endpoint == null || endpoint.port().keyboard == null || keyboard(level, endpoint.port().keyboard) == null) return false;
        List<Vec3> points = endpoint.port().waypoints;
        if (removeLast) {
            if (points.isEmpty()) return false;
            points.remove(points.size() - 1);
        } else {
            if (points.size() >= 12 || worldPosition(endpoint.pos(), endpoint.screen(), endpoint.port()).distanceToSqr(point) > 64 * 64)
                return false;
            points.add(point);
            double length = 0;
            Vec3 last = worldPosition(endpoint.pos(), endpoint.screen(), endpoint.port());
            for (Vec3 waypoint : points) { length += last.distanceTo(waypoint); last = waypoint; }
            length += last.distanceTo(keyboardPosition(keyboard(level, endpoint.port().keyboard)));
            if (length > MAX_LENGTH) { points.remove(points.size() - 1); return false; }
        }
        DataCableService.sync(endpoint.entity(), endpoint.screen());
        return true;
    }

    private static boolean contains(BlockPos origin, ScreenData screen, BlockPos clicked) {
        Vector3i relative = new Vector3i(clicked.getX() - origin.getX(), clicked.getY() - origin.getY(), clicked.getZ() - origin.getZ());
        int x = relative.dot(screen.side.right), y = relative.dot(screen.side.up);
        return x >= 0 && x < screen.size.x && y >= 0 && y < screen.size.y && relative.dot(screen.side.forward) == 0;
    }
}
