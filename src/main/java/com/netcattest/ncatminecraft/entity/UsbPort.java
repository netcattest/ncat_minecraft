package com.netcattest.ncatminecraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.UUID;

public final class UsbPort {
    public final UUID id;
    public final boolean automatic;
    public BlockPos block;
    public Direction face;
    public float x;
    public float y;
    public float z;
    public BlockPos keyboard;
    public final ArrayList<Vec3> waypoints = new ArrayList<>();

    public UsbPort(UUID id, boolean automatic) {
        this.id = id;
        this.automatic = automatic;
    }

    public static UsbPort automatic() {
        return new UsbPort(UUID.randomUUID(), true);
    }

    public boolean connected() {
        return keyboard != null;
    }

    public void disconnect() {
        keyboard = null;
        waypoints.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putBoolean("Automatic", automatic);
        if (block != null && face != null) {
            tag.putLong("Block", block.asLong());
            tag.putByte("Face", (byte) face.ordinal());
            tag.putFloat("X", x);
            tag.putFloat("Y", y);
            tag.putFloat("Z", z);
        }
        if (keyboard != null) tag.putLong("Keyboard", keyboard.asLong());
        ListTag list = new ListTag();
        for (Vec3 point : waypoints) {
            CompoundTag entry = new CompoundTag();
            entry.putDouble("X", point.x);
            entry.putDouble("Y", point.y);
            entry.putDouble("Z", point.z);
            list.add(entry);
        }
        tag.put("Waypoints", list);
        return tag;
    }

    public static UsbPort load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("Id")) return null;
        UsbPort port = new UsbPort(tag.getUUID("Id"), tag.getBoolean("Automatic"));
        if (tag.contains("Block") && tag.contains("Face")) {
            int face = tag.getByte("Face");
            if (face >= 0 && face < Direction.values().length) {
                port.block = BlockPos.of(tag.getLong("Block"));
                port.face = Direction.values()[face];
                port.x = tag.getFloat("X");
                port.y = tag.getFloat("Y");
                port.z = tag.getFloat("Z");
            }
        }
        if (tag.contains("Keyboard")) port.keyboard = BlockPos.of(tag.getLong("Keyboard"));
        ListTag points = tag.getList("Waypoints", 10);
        for (int i = 0; i < Math.min(12, points.size()); i++) {
            CompoundTag point = points.getCompound(i);
            double x = point.getDouble("X"), y = point.getDouble("Y"), z = point.getDouble("Z");
            if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) port.waypoints.add(new Vec3(x, y, z));
        }
        return port;
    }
}
