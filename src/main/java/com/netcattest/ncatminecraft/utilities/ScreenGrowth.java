package com.netcattest.ncatminecraft.utilities;

import com.netcattest.ncatminecraft.block.ScreenBlock;
import com.netcattest.ncatminecraft.config.CommonConfig;
import com.netcattest.ncatminecraft.entity.DataPort;
import com.netcattest.ncatminecraft.entity.NetworkSwitchBlockEntity;
import com.netcattest.ncatminecraft.entity.RackBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.entity.KeyboardBlockEntity;
import com.netcattest.ncatminecraft.entity.UsbPort;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ScreenGrowth {
    private ScreenGrowth() { }

    public static void onPlaced(Level level, BlockPos placed, BlockState state) {
        if (level.isClientSide || !(state.getBlock() instanceof ScreenBlock)) return;
        for (BlockSide side : BlockSide.values()) grow(level, placed, state, side);
    }

    private static void grow(Level level, BlockPos placed, BlockState state, BlockSide side) {
        Vector3i originVector = new Vector3i(placed);
        Multiblock.findOrigin(level, originVector, side, null);
        BlockPos origin = originVector.toBlock();
        if (level.getBlockState(origin).getBlock() != state.getBlock()) return;
        Vector2i size = Multiblock.measure(level, originVector, side);
        if (size.x < 1 || size.y < 1 ||
                size.x > CommonConfig.Screen.maxScreenSizeX || size.y > CommonConfig.Screen.maxScreenSizeY ||
                Multiblock.check(level, originVector, size, side) != null) return;

        ScreenBlockEntity existing = null;
        ScreenIterator iterator = new ScreenIterator(origin, side, size);
        while (iterator.hasNext()) {
            BlockPos member = iterator.next();
            if (!(level.getBlockEntity(member) instanceof ScreenBlockEntity entity) || entity.getScreen(side) == null)
                continue;
            if (existing != null && existing != entity) return;
            existing = entity;
        }
        if (existing == null) return;

        BlockPos oldOrigin = existing.getBlockPos();
        ScreenData screen = existing.getScreen(side);
        if (screen == null || screen.size == null ||
                !contains(origin, size, side, oldOrigin, screen.size) ||
                contains(oldOrigin, screen.size, side, placed, new Vector2i(1, 1)) ||
                size.x < screen.size.x || size.y < screen.size.y ||
                size.x == screen.size.x && size.y == screen.size.y) return;

        Map<DataPort, Vec3> originalPortPositions = new HashMap<>();
        for (DataPort port : DataCableService.ports(screen))
            if (!port.automatic)
                originalPortPositions.put(port, DataCableService.worldPosition(oldOrigin, screen, port));

        float oldPixelWidth = screen.size.x * 16.0f - 4.0f;
        float oldPixelHeight = screen.size.y * 16.0f - 4.0f;
        screen.resolution = new Vector2i(
                Math.max(1, Math.round(screen.resolution.x * (size.x * 16.0f - 4.0f) / oldPixelWidth)),
                Math.max(1, Math.round(screen.resolution.y * (size.y * 16.0f - 4.0f) / oldPixelHeight)));
        screen.size = size;
        screen.clampResolution();
        for (Map.Entry<DataPort, Vec3> entry : originalPortPositions.entrySet()) {
            float[] coordinate = DataCableService.uv(origin, screen, entry.getValue());
            DataPort port = entry.getKey();
            if (port.mountFace != null && port.mountFace != side) {
                int facingRight = port.mountFace.forward.x * side.right.x +
                        port.mountFace.forward.y * side.right.y + port.mountFace.forward.z * side.right.z;
                int facingUp = port.mountFace.forward.x * side.up.x +
                        port.mountFace.forward.y * side.up.y + port.mountFace.forward.z * side.up.z;
                port.u = facingRight < 0 ? 0f : facingRight > 0 ? 1f : coordinate[0];
                port.v = facingUp < 0 ? 0f : facingUp > 0 ? 1f : coordinate[1];
            } else {
                port.u = coordinate[0];
                port.v = coordinate[1];
            }
        }

        ScreenBlockEntity destination = existing;
        if (!origin.equals(oldOrigin)) {
            CompoundTag saved = screen.serialize();
            existing.removeScreen(side);
            if (!level.getBlockState(origin).getValue(ScreenBlock.hasTE))
                level.setBlockAndUpdate(origin, level.getBlockState(origin).setValue(ScreenBlock.hasTE, true));
            if (!(level.getBlockEntity(origin) instanceof ScreenBlockEntity moved)) return;
            destination = moved;
            CompoundTag tag = moved.getUpdateTag();
            ListTag list = tag.getList("WDScreens", 10);
            list.add(saved);
            tag.put("WDScreens", list);
            moved.load(tag);
            screen = moved.getScreen(side);
            if (screen == null) return;
        }

        screen.redstoneStatus = null;
        for (int i = 0; i < destination.screenCount(); i++) {
            ScreenData current = destination.getScreen(i);
            if (current.redstoneStatus == null) current.setupRedstoneStatus(level, destination.getBlockPos());
        }
        DataCableService.sync(destination, screen);
        updateCounterparts(level, oldOrigin, origin, side, screen);
    }

    private static void updateCounterparts(Level level, BlockPos oldOrigin, BlockPos newOrigin,
                                           BlockSide side, ScreenData screen) {
        ArrayList<DataCableService.CableEnd> toSync = new ArrayList<>();
        Set<NetworkSwitchBlockEntity> switchesToRefresh = new HashSet<>();
        Set<RackBlockEntity> racksToRefresh = new HashSet<>();
        for (DataPort port : DataCableService.ports(screen)) {
            if (!port.connected()) continue;
            DataCableService.CableEnd other = DataCableService.getAny(level, port.linkedPos,
                    port.linkedSide, port.linkedPort, port.linkedSwitch);
            if (other == null || !other.port().connected() ||
                    !oldOrigin.equals(other.port().linkedPos) || !port.id.equals(other.port().linkedPort) ||
                    other.port().linkedSwitch) continue;
            if (!newOrigin.equals(oldOrigin)) {
                other.port().linkedPos = newOrigin;
                other.port().linkedSide = side;
                other.port().linkedSwitch = false;
                if (other instanceof DataCableService.Endpoint remote &&
                        remote.screen().logSourceViaCable && remote.screen().logSourcePos != null &&
                        remote.screen().logSourcePos.equals(new Vector3i(oldOrigin)) &&
                        remote.screen().logSourceSide == side)
                    remote.screen().logSourcePos = new Vector3i(newOrigin);
            }
            toSync.add(other);
            if (!newOrigin.equals(oldOrigin) && other instanceof DataCableService.SwitchEndpoint switchPort)
                switchesToRefresh.add(switchPort.entity());
            if (!newOrigin.equals(oldOrigin) && other instanceof DataCableService.RackEndpoint rackPort)
                racksToRefresh.add(rackPort.rack());
        }
        for (DataCableService.CableEnd other : toSync)
            DataCableService.sync(other);
        for (NetworkSwitchBlockEntity networkSwitch : switchesToRefresh)
            DataCableService.refreshSwitch(level, networkSwitch);
        for (RackBlockEntity rack : racksToRefresh)
            DataCableService.refreshRack(level, rack);
        if (!newOrigin.equals(oldOrigin)) {
            for (UsbPort port : screen.usbPorts) {
                if (port.keyboard == null || !level.hasChunkAt(port.keyboard)) continue;
                KeyboardBlockEntity keyboard = UsbCableService.keyboard(level, port.keyboard);
                if (keyboard != null) keyboard.retargetUsb(newOrigin, side, port.id);
            }
        }
    }

    private static boolean contains(BlockPos outerOrigin, Vector2i outerSize, BlockSide side,
                                    BlockPos innerOrigin, Vector2i innerSize) {
        int dx = innerOrigin.getX() - outerOrigin.getX();
        int dy = innerOrigin.getY() - outerOrigin.getY();
        int dz = innerOrigin.getZ() - outerOrigin.getZ();
        int horizontal = dx * side.right.x + dy * side.right.y + dz * side.right.z;
        int vertical = dx * side.up.x + dy * side.up.y + dz * side.up.z;
        int normal = dx * side.forward.x + dy * side.forward.y + dz * side.forward.z;
        return normal == 0 && horizontal >= 0 && vertical >= 0 &&
                horizontal + innerSize.x <= outerSize.x && vertical + innerSize.y <= outerSize.y;
    }
}
