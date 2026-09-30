package com.netcattest.ncatminecraft.entity;

import com.netcattest.ncatminecraft.block.NetworkSwitchBlock;
import com.netcattest.ncatminecraft.block.SwitchShapes;
import com.netcattest.ncatminecraft.registry.TileRegistry;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class NetworkSwitchBlockEntity extends BlockEntity {
    public static final int PORT_COUNT = 8;
    private static final double[] PORT_X = SwitchShapes.PORT_X;
    private static final double[] PORT_Y = SwitchShapes.PORT_Y;
    private final ArrayList<DataPort> ports = new ArrayList<>(PORT_COUNT);
    private final List<DataPort> readonlyPorts = Collections.unmodifiableList(ports);
    private final long[] lastTraffic = new long[PORT_COUNT];
    private boolean powered;

    public NetworkSwitchBlockEntity(BlockPos pos, BlockState state) {
        this(TileRegistry.NETWORK_SWITCH.get(), pos, state);
    }

    protected NetworkSwitchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        Arrays.fill(lastTraffic, -100L);
        for (int i = 0; i < PORT_COUNT; i++) ports.add(newPort(i));
    }

    public List<DataPort> ports() {
        updatePortFaces();
        return readonlyPorts;
    }

    @Nullable
    public DataPort getPort(int index) {
        if (index < 0 || index >= PORT_COUNT) return null;
        updatePortFaces();
        return ports.get(index);
    }

    @Nullable
    public DataPort getPortById(UUID id) {
        int index = indexOfPort(id);
        return index < 0 ? null : getPort(index);
    }

    public int indexOfPort(UUID id) {
        if (id == null) return -1;
        for (int i = 0; i < PORT_COUNT; i++)
            if (id.equals(ports.get(i).id)) return i;
        return -1;
    }

    public static String portName(int index) {
        return index >= 0 && index < PORT_COUNT ? "eth" + index : "";
    }

    public int portAtHit(BlockHitResult hit) {
        if (!worldPosition.equals(hit.getBlockPos()) || hit.getDirection() != facing()) return -1;
        double x = hit.getLocation().x - worldPosition.getX();
        double z = hit.getLocation().z - worldPosition.getZ();
        double modelX = switch (facing()) {
            case EAST -> z;
            case SOUTH -> 1.0D - x;
            case WEST -> 1.0D - z;
            default -> x;
        } * 16.0D;
        double modelY = (hit.getLocation().y - worldPosition.getY()) * 16.0D;
        for (int i = 0; i < PORT_COUNT; i++) {
            if (Math.abs(modelX - PORT_X[i]) <= SwitchShapes.PORT_HALF_X + .05D
                    && Math.abs(modelY - PORT_Y[i]) <= SwitchShapes.PORT_HALF_Y + .1D)
                return i;
        }
        return -1;
    }

    @Nullable
    public Vec3 portWorldPosition(int index) {
        if (index < 0 || index >= PORT_COUNT) return null;
        double x = PORT_X[index] / 16.0D - 0.5D;
        double z = SwitchShapes.PORT_Z / 16.0D - 0.5D;
        double rotatedX;
        double rotatedZ;
        switch (facing()) {
            case EAST -> {
                rotatedX = -z;
                rotatedZ = x;
            }
            case SOUTH -> {
                rotatedX = -x;
                rotatedZ = -z;
            }
            case WEST -> {
                rotatedX = z;
                rotatedZ = -x;
            }
            default -> {
                rotatedX = x;
                rotatedZ = z;
            }
        }
        return new Vec3(worldPosition.getX() + 0.5D + rotatedX,
                worldPosition.getY() + PORT_Y[index] / 16.0D,
                worldPosition.getZ() + 0.5D + rotatedZ);
    }

    public boolean powered() {
        return powered;
    }

    public void setPowered(boolean on) {
        if (powered == on) return;
        powered = on;
        sync();
        if (level != null && !level.isClientSide) DataCableService.refreshSwitch(level, this);
    }

    public int connectedCount() {
        int count = 0;
        for (int i = 0; i < PORT_COUNT; i++) if (activeLink(i)) count++;
        return count;
    }

    public boolean activeLink(int index) {
        DataPort port = getPort(index);
        return level != null && port != null && port.connected() &&
                DataCableService.other(level, new DataCableService.SwitchEndpoint(this, index, port)) != null;
    }

    public void recordTraffic(int index) {
        if (index < 0 || index >= PORT_COUNT || !powered || !activeLink(index))
            return;
        long now = level.getGameTime();
        if (now - lastTraffic[index] < 3L) return;
        lastTraffic[index] = now;
        sync();
    }

    public boolean trafficLight(int index) {
        if (index < 0 || index >= PORT_COUNT || level == null || !powered || !activeLink(index)) return false;
        long age = level.getGameTime() - lastTraffic[index];
        return age >= 0L && age <= 9L && ((age / 2L) & 1L) == 0L;
    }

    @Override
    public AABB getRenderBoundingBox() {
        AABB bounds = new AABB(worldPosition);
        double minX = bounds.minX;
        double minY = bounds.minY;
        double minZ = bounds.minZ;
        double maxX = bounds.maxX;
        double maxY = bounds.maxY;
        double maxZ = bounds.maxZ;
        for (DataPort port : ports) {
            if (!port.connected() || worldPosition.distSqr(port.linkedPos) > 96.0D * 96.0D) continue;
            minX = Math.min(minX, port.linkedPos.getX());
            minY = Math.min(minY, port.linkedPos.getY());
            minZ = Math.min(minZ, port.linkedPos.getZ());
            maxX = Math.max(maxX, port.linkedPos.getX() + 1D);
            maxY = Math.max(maxY, port.linkedPos.getY() + 1D);
            maxZ = Math.max(maxZ, port.linkedPos.getZ() + 1D);
            for (Vec3 waypoint : port.waypoints) {
                if (waypoint.distanceToSqr(Vec3.atCenterOf(worldPosition)) > 96.0D * 96.0D) continue;
                minX = Math.min(minX, waypoint.x);
                minY = Math.min(minY, waypoint.y);
                minZ = Math.min(minZ, waypoint.z);
                maxX = Math.max(maxX, waypoint.x);
                maxY = Math.max(maxY, waypoint.y);
                maxZ = Math.max(maxZ, waypoint.z);
            }
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(.5D);
    }

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        powered = tag.getBoolean("Powered");
        ListTag saved = tag.getList("Ports", 10);
        Set<UUID> usedIds = new HashSet<>();
        ports.clear();
        for (int i = 0; i < PORT_COUNT; i++) {
            DataPort port = i < saved.size() ? DataPort.load(saved.getCompound(i)) : null;
            if (port == null || !usedIds.add(port.id)) {
                port = newPort(i);
                usedIds.add(port.id);
            }
            port.u = (float) (PORT_X[i] / 16.0D);
            port.v = (float) (PORT_Y[i] / 16.0D);
            ports.add(port);
        }
        updatePortFaces();
        if (level != null && level.isClientSide && tag.contains("PulseMask")) {
            int mask = tag.getInt("PulseMask");
            for (int i = 0; i < PORT_COUNT; i++)
                if ((mask & (1 << i)) != 0) lastTraffic[i] = level.getGameTime();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Powered", powered);
        ListTag saved = new ListTag();
        for (DataPort port : ports) saved.add(port.save());
        tag.put("Ports", saved);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        int mask = 0;
        if (level != null && powered) {
            long now = level.getGameTime();
            for (int i = 0; i < PORT_COUNT; i++)
                if (now - lastTraffic[i] >= 0L && now - lastTraffic[i] <= 3L)
                    mask |= 1 << i;
        }
        tag.putInt("PulseMask", mask);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) load(packet.getTag());
    }

    private Direction facing() {
        return getBlockState().getBlock() instanceof NetworkSwitchBlock
                ? getBlockState().getValue(NetworkSwitchBlock.FACING) : Direction.NORTH;
    }

    private void updatePortFaces() {
        BlockSide side = BlockSide.fromInt(facing().ordinal());
        for (DataPort port : ports) port.mountFace = side;
    }

    private static DataPort newPort(int index) {
        return new DataPort(UUID.randomUUID(), (float) (PORT_X[index] / 16.0D),
                (float) (PORT_Y[index] / 16.0D), false);
    }
}
