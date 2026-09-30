package com.netcattest.ncatminecraft.entity;

import com.netcattest.ncatminecraft.block.RackFrameBlock;
import com.netcattest.ncatminecraft.block.RackLayout;
import com.netcattest.ncatminecraft.registry.ItemRegistry;
import com.netcattest.ncatminecraft.registry.TileRegistry;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RackBlockEntity extends BlockEntity {
    private final ArrayList<RackModule> modules = new ArrayList<>();
    private final List<RackModule> readonlyModules = Collections.unmodifiableList(modules);
    private static final int MAX_SECTIONS = RackFrameBlock.MAX_SECTIONS;
    private static final int MAX_UNITS = 96;
    private static final float DOOR_SWING = 112.0F;

    private UUID owner;
    private boolean doorOpen;
    private float doorAngle;
    private float lastDoorAngle;

    public RackBlockEntity(BlockPos pos, BlockState state) {
        super(TileRegistry.RACK_FRAME.get(), pos, state);
    }

    @Nullable
    public static BlockPos basePos(Level level, BlockPos anySection) {
        BlockState here = level.getBlockState(anySection);
        if (!(here.getBlock() instanceof RackFrameBlock)) return null;
        if (RackFrameBlock.isStandalone(here)) return anySection;
        BlockPos candidate = anySection;
        for (int i = 0; i < RackFrameBlock.MAX_SECTIONS; i++) {
            BlockPos below = candidate.below();
            if (!(level.getBlockState(below).getBlock() instanceof RackFrameBlock)) break;
            candidate = below;
        }
        return RackFrameBlock.isBase(level.getBlockState(candidate)) ? candidate : null;
    }

    @Nullable
    public static RackBlockEntity at(Level level, BlockPos anySection) {
        BlockPos base = basePos(level, anySection);
        return base != null && level.getBlockEntity(base) instanceof RackBlockEntity rack ? rack : null;
    }

    public int sectionUnits(int section) {
        if (level == null || section < 0 || section >= MAX_SECTIONS) return 0;
        BlockState state = level.getBlockState(worldPosition.above(section));
        if (!(state.getBlock() instanceof RackFrameBlock)) return 0;
        if (section > 0 && state.getValue(RackFrameBlock.FACING) != getFacing()) return 0;
        if (section > 0 && RackFrameBlock.isStandalone(state)) return 0;
        return RackFrameBlock.unitsOf(state);
    }

    public int firstUnitOfSection(int section) {
        int total = 0;
        for (int i = 0; i < section; i++) total += sectionUnits(i);
        return total;
    }

    public int getCapacityU() {
        int first = sectionUnits(0);
        if (first <= 0) return 12;
        if (level != null && RackFrameBlock.isStandalone(level.getBlockState(worldPosition))) return first;
        int total = first;
        for (int section = 1; section < MAX_SECTIONS; section++) {
            int units = sectionUnits(section);
            if (units <= 0) break;
            total += units;
        }
        return total;
    }

    public int capacityU() { return getCapacityU(); }

    public int heightBlocks() {
        int sections = 0;
        for (int section = 0; section < MAX_SECTIONS; section++) {
            if (sectionUnits(section) <= 0) break;
            sections++;
        }
        return Math.max(1, sections);
    }

    public boolean fitsSegment(int startU, int heightU) {
        if (startU < 0 || heightU <= 0 || startU + heightU > getCapacityU()) return false;
        int consumed = 0;
        for (int section = 0; section < heightBlocks(); section++) {
            int units = sectionUnits(section);
            if (startU >= consumed && startU + heightU <= consumed + units) return true;
            consumed += units;
        }
        return false;
    }

    public double unitY(int unit) {
        int consumed = 0;
        for (int section = 0; section < heightBlocks(); section++) {
            int units = sectionUnits(section);
            if (units <= 0) break;
            if (unit <= consumed + units || section == heightBlocks() - 1)
                return section + (double) (unit - consumed) / Math.max(1, units);
            consumed += units;
        }
        return unit / 12D;
    }
    public Direction getFacing() {
        return getBlockState().getBlock() instanceof RackFrameBlock ?
                getBlockState().getValue(RackFrameBlock.FACING) : Direction.NORTH;
    }
    public List<RackModule> getModules() { return readonlyModules; }
    public List<RackModule> modules() { return readonlyModules; }
    public boolean doorOpen() { return doorOpen; }
    @Nullable public UUID owner() { return owner; }

    public void setOwner(UUID playerId) {
        if (owner == null && playerId != null && level != null && !level.isClientSide) {
            owner = playerId;
            sync();
        }
    }

    public boolean canConfigure(@Nullable Player player) {
        return player != null && (owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2));
    }

    public void setDoorOpen(boolean open) {
        if (level == null || level.isClientSide || doorOpen == open) return;
        doorOpen = open;
        level.playSound(null, worldPosition, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE,
                SoundSource.BLOCKS, .45F, open ? 1.25F : 1.05F);
        sync();
    }

    public float doorAngle(float partial) {
        return lastDoorAngle + (doorAngle - lastDoorAngle) * partial;
    }

    public void animateDoor() {
        lastDoorAngle = doorAngle;
        float target = doorOpen ? DOOR_SWING : 0.0F;
        doorAngle += (target - doorAngle) * .28F;
        if (Math.abs(target - doorAngle) < .4F) doorAngle = target;
    }

    @Nullable
    public RackModule getModule(UUID id) {
        if (id == null) return null;
        for (RackModule module : modules) if (id.equals(module.id())) return module;
        return null;
    }

    @Nullable
    public RackModule moduleAtU(int unit) {
        for (RackModule module : modules) if (module.containsU(unit)) return module;
        return null;
    }

    @Nullable
    public RackModule moduleByPort(UUID portId) {
        if (portId == null) return null;
        for (RackModule module : modules)
            for (DataPort port : module.ports())
                if (portId.equals(port.id)) return module;
        return null;
    }

    public boolean installModule(RackModuleType type, int startU, Player player) {
        if (level == null || level.isClientSide || type == null || !canConfigure(player) ||
                startU < 0 || startU + type.heightU() > getCapacityU() ||
                !fitsSegment(startU, type.heightU())) return false;
        for (int unit = startU; unit < startU + type.heightU(); unit++)
            if (moduleAtU(unit) != null) return false;
        if (owner == null) owner = player.getUUID();
        modules.add(new RackModule(type, startU));
        modules.sort((a, b) -> Integer.compare(a.startU(), b.startU()));
        updatePortFaces();
        sync();
        return true;
    }

    public ItemStack removeModule(UUID moduleId, Player player) {
        if (level == null || level.isClientSide || !canConfigure(player)) return ItemStack.EMPTY;
        RackModule module = getModule(moduleId);
        if (module == null) return ItemStack.EMPTY;
        DataCableService.detachRackModule(level, this, module);
        modules.remove(module);
        sync();
        return new ItemStack(ItemRegistry.rackModuleItem(module.type()));
    }

    public boolean setModulePowered(UUID moduleId, boolean powered, Player player) {
        if (level == null || level.isClientSide || !canConfigure(player)) return false;
        RackModule module = getModule(moduleId);
        if (module == null || module.powered() == powered) return false;
        module.setPowered(powered);
        sync();
        DataCableService.refreshRack(level, this);
        return true;
    }

    public boolean addModulePort(UUID moduleId, Player player) {
        if (level == null || level.isClientSide || !canConfigure(player)) return false;
        RackModule module = getModule(moduleId);
        if (module == null || module.portCount() >= module.maxPorts()) return false;
        ItemStack supply = ItemStack.EMPTY;
        if (!player.getAbilities().instabuild) {
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack candidate = player.getInventory().getItem(i);
                if (candidate.is(ItemRegistry.DATA_PORT.get())) { supply = candidate; break; }
            }
            if (supply.isEmpty()) return false;
        }
        if (!module.addPort()) return false;
        if (!player.getAbilities().instabuild) supply.shrink(1);
        updatePortFaces();
        sync();
        return true;
    }

    public int cycleModulePortDress(UUID moduleId, int portIndex, Player player) {
        if (level == null || level.isClientSide || !canConfigure(player)) return -1;
        RackModule module = getModule(moduleId);
        if (module == null || !module.cyclePortDress(portIndex)) return -1;
        sync();
        return module.portDress(portIndex);
    }

    public boolean moduleActive(UUID moduleId) {
        RackModule module = getModule(moduleId);
        return module != null && module.powered();
    }

    public boolean setModulePortEnabled(UUID moduleId, int portIndex, boolean value, Player player) {
        RackModule module = configurableManagedModule(moduleId, player);
        if (module == null || !module.setPortEnabled(portIndex, value)) return false;
        afterPolicyChange();
        return true;
    }

    public boolean setModulePortTrunk(UUID moduleId, int portIndex, boolean value, Player player) {
        RackModule module = configurableManagedModule(moduleId, player);
        if (module == null || !module.setPortTrunk(portIndex, value)) return false;
        afterPolicyChange();
        return true;
    }

    public boolean setModulePortIsolated(UUID moduleId, int portIndex, boolean value, Player player) {
        RackModule module = configurableManagedModule(moduleId, player);
        if (module == null || !module.setPortIsolated(portIndex, value)) return false;
        afterPolicyChange();
        return true;
    }

    public boolean setModulePortVlan(UUID moduleId, int portIndex, int value, Player player) {
        RackModule module = configurableManagedModule(moduleId, player);
        if (module == null || !module.setPortVlan(portIndex, value)) return false;
        afterPolicyChange();
        return true;
    }

    public boolean setModulePortPairAllowed(UUID moduleId, int first, int second, boolean allowed, Player player) {
        RackModule module = configurableManagedModule(moduleId, player);
        if (module == null || !module.setPortPairAllowed(first, second, allowed)) return false;
        afterPolicyChange();
        return true;
    }

    @Nullable
    private RackModule configurableManagedModule(UUID moduleId, Player player) {
        if (level == null || level.isClientSide || !canConfigure(player)) return null;
        RackModule module = getModule(moduleId);
        return module != null && module.type() == RackModuleType.MANAGED_SWITCH ? module : null;
    }

    private void afterPolicyChange() {
        sync();
        DataCableService.refreshRack(level, this);
    }

    @Nullable
    public Vec3 portWorldPosition(UUID moduleId, int portIndex) {
        RackModule module = getModule(moduleId);
        if (module == null || portIndex < 0 || portIndex >= module.portCount()) return null;
        double bottom = unitY(module.startU()) + .005D;
        double top = unitY(module.startU() + module.heightU()) - .005D;
        double y = RackLayout.portY(bottom, top);
        double lateral = RackLayout.portX(portIndex, module.portCount()) - .5D;
        double depth = .5D - RackLayout.socketDepth();
        Direction facing = getFacing();
        Direction right = facing.getClockWise();
        return new Vec3(worldPosition.getX() + .5D + facing.getStepX() * depth + right.getStepX() * lateral,
                worldPosition.getY() + y,
                worldPosition.getZ() + .5D + facing.getStepZ() * depth + right.getStepZ() * lateral);
    }

    public double unitCenterY(int startU, int heightU) {
        return (unitY(startU) + unitY(startU + heightU)) * .5D;
    }

    public static boolean slotFitsSegment(int startU, int heightU) {
        if (startU < 0 || heightU <= 0 || startU + heightU > MAX_UNITS) return false;
        int endU = startU + heightU - 1;
        return startU < 12 && endU < 12 ||
                startU >= 12 && startU < 24 && endU < 24 ||
                startU >= 24 && endU < 42;
    }

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public void dropAllModules() {
        if (level == null || level.isClientSide) return;
        for (RackModule module : List.copyOf(modules)) {
            DataCableService.detachRackModule(level, this, module);
            Block.popResource(level, worldPosition, new ItemStack(ItemRegistry.rackModuleItem(module.type())));
        }
        modules.clear();
        sync();
    }

    @Override
    public AABB getRenderBoundingBox() {
        AABB bounds = new AABB(worldPosition).expandTowards(0, heightBlocks() - 1D, 0);
        for (RackModule module : modules) {
            for (DataPort port : module.ports()) {
                if (!port.connected() || worldPosition.distSqr(port.linkedPos) > 96D * 96D) continue;
                bounds = bounds.minmax(new AABB(port.linkedPos));
                for (Vec3 waypoint : port.waypoints)
                    if (waypoint.distanceToSqr(Vec3.atCenterOf(worldPosition)) <= 96D * 96D)
                        bounds = bounds.minmax(new AABB(waypoint, waypoint));
            }
        }
        return bounds.inflate(.5D);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        doorOpen = tag.getBoolean("DoorOpen");
        modules.clear();
        ListTag saved = tag.getList("Modules", 10);
        Set<UUID> moduleIds = new HashSet<>();
        Set<UUID> portIds = new HashSet<>();
        boolean[] occupied = new boolean[MAX_UNITS];
        for (int i = 0; i < saved.size() && i < MAX_UNITS; i++) {
            RackModule module = RackModule.load(saved.getCompound(i));
            if (module == null || !slotFitsSegment(module.startU(), module.heightU()) ||
                    moduleIds.contains(module.id())) continue;
            boolean valid = true;
            for (int unit = module.startU(); unit < module.startU() + module.heightU(); unit++)
                if (occupied[unit]) valid = false;
            Set<UUID> modulePortIds = new HashSet<>();
            for (DataPort port : module.ports())
                if (portIds.contains(port.id) || !modulePortIds.add(port.id)) valid = false;
            if (!valid) continue;
            moduleIds.add(module.id());
            portIds.addAll(modulePortIds);
            for (int unit = module.startU(); unit < module.startU() + module.heightU(); unit++) occupied[unit] = true;
            modules.add(module);
        }
        modules.sort((a, b) -> Integer.compare(a.startU(), b.startU()));
        updatePortFaces();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putBoolean("DoorOpen", doorOpen);
        ListTag saved = new ListTag();
        for (RackModule module : modules) saved.add(module.save());
        tag.put("Modules", saved);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) { load(tag); }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) load(packet.getTag());
    }

    private void updatePortFaces() {
        BlockSide face = BlockSide.fromInt(getFacing().ordinal());
        for (RackModule module : modules) for (DataPort port : module.ports()) port.mountFace = face;
    }
}
