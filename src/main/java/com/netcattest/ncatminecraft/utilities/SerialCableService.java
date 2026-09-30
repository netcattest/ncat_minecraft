package com.netcattest.ncatminecraft.utilities;

import com.netcattest.ncatminecraft.block.ManagedTabletBlock;
import com.netcattest.ncatminecraft.block.NetworkSwitchBlock;
import com.netcattest.ncatminecraft.entity.ManagedSwitchBlockEntity;
import com.netcattest.ncatminecraft.entity.ManagedTabletBlockEntity;
import com.netcattest.ncatminecraft.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class SerialCableService {
    public static final double MAX_LENGTH = 16.0D;
    private static final double HIT_RADIUS_SQUARED = .34D * .34D;

    public sealed interface Endpoint permits TabletEnd, SwitchEnd {
        BlockPos pos();
        Vec3 socket();
    }

    public record TabletEnd(ManagedTabletBlockEntity entity) implements Endpoint {
        public BlockPos pos() { return entity.getBlockPos(); }
        public Vec3 socket() { return entity.serialPortPosition(); }
    }

    public record SwitchEnd(ManagedSwitchBlockEntity entity) implements Endpoint {
        public BlockPos pos() { return entity.getBlockPos(); }
        public Vec3 socket() { return switchPortPosition(entity); }
    }

    private SerialCableService() { }

    @Nullable
    public static Endpoint hit(Level level, BlockHitResult hit) {
        BlockEntity block = level.getBlockEntity(hit.getBlockPos());
        Endpoint endpoint = block instanceof ManagedTabletBlockEntity tablet ? new TabletEnd(tablet) :
                block instanceof ManagedSwitchBlockEntity managedSwitch ? new SwitchEnd(managedSwitch) : null;
        if (endpoint == null) return null;
        Direction front = endpoint instanceof TabletEnd tablet
                ? tablet.entity().getBlockState().getValue(ManagedTabletBlock.FACING)
                : ((SwitchEnd) endpoint).entity().getBlockState().getValue(NetworkSwitchBlock.FACING);
        return hit.getDirection() == front.getClockWise() &&
                endpoint.socket().distanceToSqr(hit.getLocation()) <= HIT_RADIUS_SQUARED ? endpoint : null;
    }

    public static Vec3 switchPortPosition(ManagedSwitchBlockEntity networkSwitch) {
        double localX = 15.6D / 16.0D - .5D;
        Direction facing = networkSwitch.getBlockState().getValue(NetworkSwitchBlock.FACING);
        double x = switch (facing) {
            case SOUTH -> -localX;
            case EAST, WEST -> 0;
            default -> localX;
        };
        double z = switch (facing) {
            case EAST -> localX;
            case WEST -> -localX;
            default -> 0;
        };
        BlockPos pos = networkSwitch.getBlockPos();
        return new Vec3(pos.getX() + .5D + x, pos.getY() + 4.0D / 16.0D, pos.getZ() + .5D + z);
    }

    @Nullable
    public static ManagedSwitchBlockEntity connectedSwitch(Level level, ManagedTabletBlockEntity tablet) {
        if (level == null || tablet == null || tablet.linkedSwitchPos() == null ||
                !level.hasChunkAt(tablet.linkedSwitchPos())) return null;
        BlockEntity block = level.getBlockEntity(tablet.linkedSwitchPos());
        if (!(block instanceof ManagedSwitchBlockEntity managedSwitch) ||
                !tablet.getBlockPos().equals(managedSwitch.serialTabletPos()) ||
                tablet.serialPortPosition().distanceTo(switchPortPosition(managedSwitch)) > MAX_LENGTH)
            return null;
        return managedSwitch;
    }

    @Nullable
    public static ManagedTabletBlockEntity connectedTablet(Level level, ManagedSwitchBlockEntity managedSwitch) {
        if (level == null || managedSwitch == null || managedSwitch.serialTabletPos() == null ||
                !level.hasChunkAt(managedSwitch.serialTabletPos())) return null;
        BlockEntity block = level.getBlockEntity(managedSwitch.serialTabletPos());
        if (!(block instanceof ManagedTabletBlockEntity tablet) ||
                !managedSwitch.getBlockPos().equals(tablet.linkedSwitchPos()) ||
                tablet.serialPortPosition().distanceTo(switchPortPosition(managedSwitch)) > MAX_LENGTH)
            return null;
        return tablet;
    }

    public static boolean connect(Level level, ManagedTabletBlockEntity tablet,
                                  ManagedSwitchBlockEntity managedSwitch, Player player) {
        if (level == null || level.isClientSide || tablet == null || managedSwitch == null ||
                player == null || !managedSwitch.canConfigure(player) ||
                !level.hasChunkAt(tablet.getBlockPos()) || !level.hasChunkAt(managedSwitch.getBlockPos()) ||
                tablet.serialPortPosition().distanceTo(switchPortPosition(managedSwitch)) > MAX_LENGTH)
            return false;
        repairStale(level, new TabletEnd(tablet));
        repairStale(level, new SwitchEnd(managedSwitch));
        if (tablet.linkedSwitchPos() != null || managedSwitch.serialTabletPos() != null) {
            if (tablet.getBlockPos().equals(managedSwitch.serialTabletPos()) &&
                    managedSwitch.getBlockPos().equals(tablet.linkedSwitchPos())) return true;
            return false;
        }
        tablet.setLinkedSwitchPos(managedSwitch.getBlockPos());
        managedSwitch.setSerialTabletPos(tablet.getBlockPos());
        return true;
    }

    public static boolean disconnect(Level level, ManagedTabletBlockEntity tablet, Player player,
                                     boolean returnCable) {
        ManagedSwitchBlockEntity managedSwitch = connectedSwitch(level, tablet);
        if (level == null || level.isClientSide || managedSwitch == null ||
                player == null || !managedSwitch.canConfigure(player)) return false;
        tablet.setLinkedSwitchPos(null);
        managedSwitch.setSerialTabletPos(null);
        if (returnCable) {
            ItemStack cable = new ItemStack(ItemRegistry.SERIAL_CABLE.get());
            if (!player.getInventory().add(cable)) player.drop(cable, false);
        }
        return true;
    }

    public static void detachOnRemoval(Level level, BlockPos removed) {
        if (level == null || level.isClientSide) return;
        BlockEntity entity = level.getBlockEntity(removed);
        if (entity instanceof ManagedTabletBlockEntity tablet && tablet.linkedSwitchPos() != null) {
            BlockPos switchPos = tablet.linkedSwitchPos();
            boolean unloaded = !level.hasChunkAt(switchPos);
            BlockEntity remote = unloaded ? null : level.getBlockEntity(switchPos);
            boolean reciprocal = remote instanceof ManagedSwitchBlockEntity managedSwitch &&
                    removed.equals(managedSwitch.serialTabletPos());
            tablet.setLinkedSwitchPos(null);
            if (reciprocal) ((ManagedSwitchBlockEntity) remote).setSerialTabletPos(null);
            if (unloaded || reciprocal)
                Block.popResource(level, removed, new ItemStack(ItemRegistry.SERIAL_CABLE.get()));
        } else if (entity instanceof ManagedSwitchBlockEntity networkSwitch &&
                networkSwitch.serialTabletPos() != null) {
            BlockPos tabletPos = networkSwitch.serialTabletPos();
            boolean unloaded = !level.hasChunkAt(tabletPos);
            BlockEntity remote = unloaded ? null : level.getBlockEntity(tabletPos);
            boolean reciprocal = remote instanceof ManagedTabletBlockEntity tablet &&
                    removed.equals(tablet.linkedSwitchPos());
            networkSwitch.setSerialTabletPos(null);
            if (reciprocal) ((ManagedTabletBlockEntity) remote).setLinkedSwitchPos(null);
            if (unloaded || reciprocal)
                Block.popResource(level, removed, new ItemStack(ItemRegistry.SERIAL_CABLE.get()));
        }
    }

    public static void repairStale(Level level, Endpoint endpoint) {
        if (level == null || level.isClientSide || endpoint == null) return;
        if (endpoint instanceof TabletEnd tabletEnd) {
            ManagedTabletBlockEntity tablet = tabletEnd.entity();
            BlockPos linked = tablet.linkedSwitchPos();
            if (linked == null || !level.hasChunkAt(linked)) return;
            BlockEntity remote = level.getBlockEntity(linked);
            if (!(remote instanceof ManagedSwitchBlockEntity networkSwitch) ||
                    !tablet.getBlockPos().equals(networkSwitch.serialTabletPos()) ||
                    tablet.serialPortPosition().distanceTo(switchPortPosition(networkSwitch)) > MAX_LENGTH)
                tablet.setLinkedSwitchPos(null);
        } else if (endpoint instanceof SwitchEnd switchEnd) {
            ManagedSwitchBlockEntity networkSwitch = switchEnd.entity();
            BlockPos linked = networkSwitch.serialTabletPos();
            if (linked == null || !level.hasChunkAt(linked)) return;
            BlockEntity remote = level.getBlockEntity(linked);
            if (!(remote instanceof ManagedTabletBlockEntity tablet) ||
                    !networkSwitch.getBlockPos().equals(tablet.linkedSwitchPos()) ||
                    tablet.serialPortPosition().distanceTo(switchPortPosition(networkSwitch)) > MAX_LENGTH)
                networkSwitch.setSerialTabletPos(null);
        }
    }
}
