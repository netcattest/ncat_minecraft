package com.netcattest.ncatminecraft.item;

import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

final class DataCableSelection {
    private static final String POS = "CableStartPos";
    private static final String SIDE = "CableStartSide";
    private static final String PORT = "CableStartPort";
    private static final String DIMENSION = "CableStartDimension";
    private static final String SWITCH = "CableStartSwitch";

    private DataCableSelection() { }

    static void set(ItemStack stack, Level level, DataCableService.CableEnd endpoint) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(POS, endpoint.pos().asLong());
        if (endpoint instanceof DataCableService.Endpoint screen)
            tag.putByte(SIDE, (byte) screen.side().ordinal());
        else tag.remove(SIDE);
        tag.putBoolean(SWITCH, !(endpoint instanceof DataCableService.Endpoint));
        tag.putUUID(PORT, endpoint.port().id);
        tag.putString(DIMENSION, level.dimension().location().toString());
    }

    static boolean active(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(POS) && (tag.getBoolean(SWITCH) || tag.contains(SIDE)) &&
                tag.hasUUID(PORT) && tag.contains(DIMENSION);
    }

    static DataCableService.CableEnd get(ItemStack stack, Level level) {
        if (!active(stack)) return null;
        CompoundTag tag = stack.getTag();
        if (!level.dimension().location().toString().equals(tag.getString(DIMENSION))) return null;
        boolean switchPort = tag.getBoolean(SWITCH);
        BlockSide side = switchPort ? null : BlockSide.fromInt(tag.getByte(SIDE));
        UUID id = tag.getUUID(PORT);
        return !switchPort && side == null ? null :
                DataCableService.getAny(level, BlockPos.of(tag.getLong(POS)), side, id, switchPort);
    }

    static void clear(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(POS);
        tag.remove(SIDE);
        tag.remove(PORT);
        tag.remove(DIMENSION);
        tag.remove(SWITCH);
        if (tag.isEmpty()) stack.setTag(null);
    }
}
