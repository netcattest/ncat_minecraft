package com.netcattest.ncatminecraft.item;

import com.netcattest.ncatminecraft.utilities.UsbCableService;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

final class UsbSelection {
    private static final String POS = "UsbStartPos";
    private static final String SIDE = "UsbStartSide";
    private static final String PORT = "UsbStartPort";
    private static final String KEYBOARD = "UsbStartKeyboard";
    private static final String DIMENSION = "UsbStartDimension";

    private UsbSelection() { }

    static void screen(ItemStack stack, Level level, UsbCableService.Endpoint endpoint) {
        clear(stack);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(POS, endpoint.pos().asLong());
        tag.putByte(SIDE, (byte) endpoint.side().ordinal());
        tag.putUUID(PORT, endpoint.port().id);
        tag.putString(DIMENSION, level.dimension().location().toString());
    }

    static void keyboard(ItemStack stack, Level level, BlockPos pos) {
        clear(stack);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(KEYBOARD, pos.asLong());
        tag.putString(DIMENSION, level.dimension().location().toString());
    }

    static boolean active(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(DIMENSION) && (tag.hasUUID(PORT) || tag.contains(KEYBOARD));
    }

    static UsbCableService.Endpoint screen(ItemStack stack, Level level) {
        if (!dimension(stack, level)) return null;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID(PORT) || !tag.contains(POS) || !tag.contains(SIDE)) return null;
        BlockSide side = BlockSide.fromInt(tag.getByte(SIDE));
        return side == null ? null : UsbCableService.get(level, BlockPos.of(tag.getLong(POS)), side, tag.getUUID(PORT));
    }

    static BlockPos keyboard(ItemStack stack, Level level) {
        if (!dimension(stack, level)) return null;
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(KEYBOARD) ? BlockPos.of(tag.getLong(KEYBOARD)) : null;
    }

    private static boolean dimension(ItemStack stack, Level level) {
        return active(stack) && level.dimension().location().toString().equals(stack.getTag().getString(DIMENSION));
    }

    static void clear(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(POS);
        tag.remove(SIDE);
        tag.remove(PORT);
        tag.remove(KEYBOARD);
        tag.remove(DIMENSION);
        if (tag.isEmpty()) stack.setTag(null);
    }
}
