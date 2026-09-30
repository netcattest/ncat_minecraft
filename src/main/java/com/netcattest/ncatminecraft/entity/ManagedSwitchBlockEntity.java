package com.netcattest.ncatminecraft.entity;

import com.netcattest.ncatminecraft.registry.TileRegistry;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;

public final class ManagedSwitchBlockEntity extends NetworkSwitchBlockEntity {
    public static final int DEFAULT_VLAN = 1;
    public static final int MAX_VLAN = 4094;
    public static final int MAX_LABEL_LENGTH = 24;
    private final boolean[] enabled = new boolean[PORT_COUNT];
    private final boolean[] trunk = new boolean[PORT_COUNT];
    private final boolean[] isolated = new boolean[PORT_COUNT];
    private final int[] vlan = new int[PORT_COUNT];
    private final int[] deniedPairs = new int[PORT_COUNT];
    private final String[] labels = new String[PORT_COUNT];
    private final long[] portEvents = new long[PORT_COUNT];
    private long configurationRevision;
    private UUID owner;
    private BlockPos serialTabletPos;

    public ManagedSwitchBlockEntity(BlockPos pos, BlockState state) {
        super(TileRegistry.MANAGED_SWITCH.get(), pos, state);
        Arrays.fill(enabled, true);
        Arrays.fill(vlan, DEFAULT_VLAN);
        Arrays.fill(labels, "");
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public void setOwner(UUID playerId) {
        if (owner == null && playerId != null && level != null && !level.isClientSide) {
            owner = playerId;
            configurationRevision++;
            sync();
        }
    }

    public boolean canConfigure(@Nullable Player player) {
        return player != null && (owner != null && owner.equals(player.getUUID()) ||
                player.hasPermissions(2));
    }

    @Nullable
    public BlockPos serialTabletPos() {
        return serialTabletPos;
    }

    public void setSerialTabletPos(@Nullable BlockPos pos) {
        if (level == null || level.isClientSide ||
                (serialTabletPos == null ? pos == null : serialTabletPos.equals(pos))) return;
        serialTabletPos = pos == null ? null : pos.immutable();
        configurationRevision++;
        sync();
    }

    public long configurationRevision() {
        return configurationRevision;
    }

    public long portEvents(int index) {
        return valid(index) ? portEvents[index] : 0L;
    }

    @Override
    public void recordTraffic(int index) {
        if (!valid(index) || !powered() || !enabled[index] || !activeLink(index) ||
                level == null || level.isClientSide) return;
        if (portEvents[index] < Long.MAX_VALUE) portEvents[index]++;
        setChanged();
        super.recordTraffic(index);
    }

    public boolean portEnabled(int index) {
        return valid(index) && enabled[index];
    }

    public boolean portTrunk(int index) {
        return valid(index) && trunk[index];
    }

    public boolean portIsolated(int index) {
        return valid(index) && isolated[index];
    }

    public int portVlan(int index) {
        return valid(index) ? vlan[index] : 0;
    }

    public String portLabel(int index) {
        return valid(index) ? labels[index] : "";
    }

    public boolean pairAllowed(int ingress, int egress) {
        return valid(ingress) && valid(egress) && ingress != egress &&
                (deniedPairs[ingress] & (1 << egress)) == 0;
    }

    public boolean setPortEnabled(int index, boolean value) {
        if (!valid(index)) return false;
        if (enabled[index] != value) {
            enabled[index] = value;
            changedTopology();
        }
        return true;
    }

    public boolean setPortTrunk(int index, boolean value) {
        if (!valid(index)) return false;
        if (trunk[index] != value) {
            trunk[index] = value;
            changedTopology();
        }
        return true;
    }

    public boolean setPortIsolation(int index, boolean value) {
        if (!valid(index)) return false;
        if (isolated[index] != value) {
            isolated[index] = value;
            changedTopology();
        }
        return true;
    }

    public boolean setPortVlan(int index, int value) {
        if (!valid(index) || value < 1 || value > MAX_VLAN) return false;
        if (vlan[index] != value) {
            vlan[index] = value;
            changedTopology();
        }
        return true;
    }

    public boolean setPortLabel(int index, String value) {
        if (!valid(index) || value == null) return false;
        if (value.length() > MAX_LABEL_LENGTH * 4) return false;
        String clean = value.replaceAll("[\\p{Cntrl}]", "").strip();
        if (clean.codePointCount(0, clean.length()) > MAX_LABEL_LENGTH) return false;
        if (!labels[index].equals(clean)) {
            labels[index] = clean;
            configurationRevision++;
            sync();
        }
        return true;
    }

    public boolean setPairAllowed(int ingress, int egress, boolean allowed) {
        if (!valid(ingress) || !valid(egress) || ingress == egress) return false;
        int bitA = 1 << egress;
        int bitB = 1 << ingress;
        int oldA = deniedPairs[ingress];
        int oldB = deniedPairs[egress];
        if (allowed) {
            deniedPairs[ingress] &= ~bitA;
            deniedPairs[egress] &= ~bitB;
        } else {
            deniedPairs[ingress] |= bitA;
            deniedPairs[egress] |= bitB;
        }
        if (oldA != deniedPairs[ingress] || oldB != deniedPairs[egress]) changedTopology();
        return true;
    }

    public int acceptIngressVlan(int ingress, int incomingVlan) {
        if (!powered() || !valid(ingress) || !enabled[ingress] ||
                incomingVlan < 0 || incomingVlan > MAX_VLAN) return -1;
        if (incomingVlan == 0) return vlan[ingress];
        return trunk[ingress] || vlan[ingress] == incomingVlan ? incomingVlan : -1;
    }

    public boolean allowsForward(int ingress, int egress, int trafficVlan) {
        return powered() && valid(ingress) && valid(egress) && ingress != egress &&
                enabled[ingress] && enabled[egress] && pairAllowed(ingress, egress) &&
                !(isolated[ingress] && isolated[egress]) &&
                trafficVlan >= 1 && trafficVlan <= MAX_VLAN &&
                (trunk[ingress] || vlan[ingress] == trafficVlan) &&
                (trunk[egress] || vlan[egress] == trafficVlan);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        serialTabletPos = tag.contains("SerialTabletPos") ? BlockPos.of(tag.getLong("SerialTabletPos")) : null;
        configurationRevision = Math.max(0L, tag.getLong("ConfigurationRevision"));
        int mask = tag.contains("EnabledMask") ? tag.getInt("EnabledMask") : 0xff;
        int trunks = tag.getInt("TrunkMask");
        int isolatedMask = tag.getInt("IsolatedMask");
        int[] savedVlans = tag.getIntArray("Vlans");
        int[] savedDenied = tag.getIntArray("DeniedPairs");
        long[] savedEvents = tag.getLongArray("PortEvents");
        ListTag savedLabels = tag.getList("PortLabels", 8);
        for (int i = 0; i < PORT_COUNT; i++) {
            enabled[i] = (mask & (1 << i)) != 0;
            trunk[i] = (trunks & (1 << i)) != 0;
            isolated[i] = (isolatedMask & (1 << i)) != 0;
            vlan[i] = i < savedVlans.length && savedVlans[i] >= 1 && savedVlans[i] <= MAX_VLAN
                    ? savedVlans[i] : DEFAULT_VLAN;
            deniedPairs[i] = i < savedDenied.length ? savedDenied[i] & 0xff & ~(1 << i) : 0;
            portEvents[i] = i < savedEvents.length ? Math.max(0L, savedEvents[i]) : 0L;
            String saved = i < savedLabels.size() ? savedLabels.getString(i) : "";
            labels[i] = saved.codePointCount(0, saved.length()) <= MAX_LABEL_LENGTH ? saved : "";
        }
        for (int i = 0; i < PORT_COUNT; i++)
            for (int j = i + 1; j < PORT_COUNT; j++)
                if ((deniedPairs[i] & (1 << j)) != 0 || (deniedPairs[j] & (1 << i)) != 0) {
                    deniedPairs[i] |= 1 << j;
                    deniedPairs[j] |= 1 << i;
                }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        if (serialTabletPos != null) tag.putLong("SerialTabletPos", serialTabletPos.asLong());
        tag.putLong("ConfigurationRevision", configurationRevision);
        int mask = 0, trunks = 0, isolatedMask = 0;
        for (int i = 0; i < PORT_COUNT; i++) {
            if (enabled[i]) mask |= 1 << i;
            if (trunk[i]) trunks |= 1 << i;
            if (isolated[i]) isolatedMask |= 1 << i;
        }
        tag.putInt("EnabledMask", mask);
        tag.putInt("TrunkMask", trunks);
        tag.putInt("IsolatedMask", isolatedMask);
        tag.putIntArray("Vlans", vlan);
        tag.putIntArray("DeniedPairs", deniedPairs);
        tag.putLongArray("PortEvents", portEvents);
        ListTag savedLabels = new ListTag();
        for (String label : labels) savedLabels.add(StringTag.valueOf(label));
        tag.put("PortLabels", savedLabels);
    }

    private void changedTopology() {
        configurationRevision++;
        sync();
        if (level != null && !level.isClientSide) DataCableService.refreshSwitch(level, this);
    }

    private static boolean valid(int index) {
        return index >= 0 && index < PORT_COUNT;
    }
}
