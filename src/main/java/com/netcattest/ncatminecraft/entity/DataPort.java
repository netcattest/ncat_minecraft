package com.netcattest.ncatminecraft.entity;

import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.UUID;

public final class DataPort {
    public final UUID id;
    public float u;
    public float v;
    public final boolean automatic;
    public BlockSide mountFace;
    public float depth;
    public BlockPos linkedPos;
    public BlockSide linkedSide;
    public UUID linkedPort;
    public boolean linkedSwitch;
    public long lastTrafficTick = -100;
    public final ArrayList<Vec3> waypoints = new ArrayList<>();

    public DataPort(UUID id, float u, float v, boolean automatic) {
        this.id = id;
        this.u = Math.max(0f, Math.min(1f, u));
        this.v = Math.max(0f, Math.min(1f, v));
        this.automatic = automatic;
        this.depth = .85f;
    }

    public static DataPort automatic() {
        return new DataPort(UUID.randomUUID(), 1f, .14f, true);
    }

    public boolean connected() {
        return linkedPos != null && linkedPort != null && (linkedSwitch || linkedSide != null);
    }

    public void recordTraffic(long tick) {
        lastTrafficTick = tick;
    }

    public boolean trafficLight(long tick) {
        long age = tick - lastTrafficTick;
        return connected() && age >= 0 && age <= 9 && ((age / 2) & 1) == 0;
    }

    public void disconnect() {
        linkedPos = null;
        linkedSide = null;
        linkedPort = null;
        linkedSwitch = false;
        waypoints.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putFloat("U", u);
        tag.putFloat("V", v);
        tag.putBoolean("Automatic", automatic);
        if (mountFace != null) tag.putByte("MountFace", (byte) mountFace.ordinal());
        tag.putFloat("Depth", depth);
        if (lastTrafficTick >= 0) tag.putLong("LastTrafficTick", lastTrafficTick);
        if (connected()) {
            tag.putLong("LinkedPos", linkedPos.asLong());
            if (linkedSide != null) tag.putByte("LinkedSide", (byte) linkedSide.ordinal());
            tag.putUUID("LinkedPort", linkedPort);
            tag.putBoolean("LinkedSwitch", linkedSwitch);
        }
        ListTag points = new ListTag();
        for (Vec3 point : waypoints) {
            CompoundTag entry = new CompoundTag();
            entry.putDouble("X", point.x);
            entry.putDouble("Y", point.y);
            entry.putDouble("Z", point.z);
            points.add(entry);
        }
        tag.put("Waypoints", points);
        return tag;
    }

    public static DataPort load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("Id")) return null;
        float u = tag.getFloat("U");
        float v = tag.getFloat("V");
        if (!Float.isFinite(u) || !Float.isFinite(v)) return null;
        DataPort port = new DataPort(tag.getUUID("Id"), u, v, tag.getBoolean("Automatic"));
        if (tag.contains("MountFace")) port.mountFace = BlockSide.fromInt(tag.getByte("MountFace"));
        float depth = tag.contains("Depth") ? tag.getFloat("Depth") : .85f;
        if (Float.isFinite(depth)) port.depth = Math.max(0f, Math.min(1f, depth));
        if (tag.contains("LastTrafficTick")) port.lastTrafficTick = tag.getLong("LastTrafficTick");
        if (tag.hasUUID("LinkedPort")) {
            boolean switchLink = tag.getBoolean("LinkedSwitch");
            BlockSide side = tag.contains("LinkedSide") ? BlockSide.fromInt(tag.getByte("LinkedSide")) : null;
            if (side != null || switchLink) {
                port.linkedPos = BlockPos.of(tag.getLong("LinkedPos"));
                port.linkedSide = side;
                port.linkedPort = tag.getUUID("LinkedPort");
                port.linkedSwitch = switchLink;
            }
        }
        ListTag points = tag.getList("Waypoints", 10);
        for (int i = 0; i < Math.min(12, points.size()); i++) {
            CompoundTag entry = points.getCompound(i);
            double x = entry.getDouble("X"), y = entry.getDouble("Y"), z = entry.getDouble("Z");
            if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z))
                port.waypoints.add(new Vec3(x, y, z));
        }
        return port;
    }
}
