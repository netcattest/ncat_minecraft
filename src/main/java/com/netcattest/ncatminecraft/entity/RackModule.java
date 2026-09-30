package com.netcattest.ncatminecraft.entity;

import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class RackModule {
    private final UUID id;
    private final RackModuleType type;
    private final int startU;
    private final ArrayList<DataPort> ports;
    private final List<DataPort> readonlyPorts;
    private final boolean[] enabled;
    private final boolean[] trunk;
    private final boolean[] isolated;
    private final int[] vlan;
    private final int[] deniedPairs;
    private final int[] dress;
    private boolean powered;

    public RackModule(RackModuleType type, int startU) {
        this(UUID.randomUUID(), type, startU);
    }

    private RackModule(UUID id, RackModuleType type, int startU) {
        this.id = id;
        this.type = type;
        this.startU = startU;
        this.ports = new ArrayList<>(type.portCount());
        this.readonlyPorts = Collections.unmodifiableList(ports);
        this.enabled = new boolean[16];
        this.trunk = new boolean[16];
        this.isolated = new boolean[16];
        this.vlan = new int[16];
        this.deniedPairs = new int[16];
        this.dress = new int[16];
        Arrays.fill(enabled, true);
        Arrays.fill(vlan, 1);
        for (int i = 0; i < type.portCount(); i++) {
            DataPort port = new DataPort(UUID.randomUUID(), (float) ((i + 1D) / (type.portCount() + 1D)), .5F, false);
            port.mountFace = BlockSide.NORTH;
            ports.add(port);
        }
    }

    public UUID id() { return id; }
    public RackModuleType type() { return type; }
    public int startU() { return startU; }
    public int heightU() { return type.heightU(); }
    public boolean powered() { return powered; }
    public List<DataPort> ports() { return readonlyPorts; }
    public DataPort port(int index) { return index >= 0 && index < ports.size() ? ports.get(index) : null; }
    public int portCount() { return ports.size(); }
    public int maxPorts() { return type.isSwitch() ? 16 : 4; }
    public boolean addPort() {
        if (ports.size() >= maxPorts()) return false;
        DataPort port = new DataPort(UUID.randomUUID(), .5F, .5F, false);
        port.mountFace = ports.get(0).mountFace;
        ports.add(port);
        for (int i = 0; i < ports.size(); i++) ports.get(i).u = (float) ((i + 1D) / (ports.size() + 1D));
        return true;
    }
    public boolean containsU(int unit) { return unit >= startU && unit < startU + heightU(); }
    public int connectedCount() {
        int count = 0;
        for (DataPort port : ports) if (port.connected()) count++;
        return count;
    }

    public boolean portTraffic(int index, long gameTime) {
        DataPort port = port(index);
        return powered && port != null && port.trafficLight(gameTime);
    }

    public void setPowered(boolean value) { powered = value; }

    public boolean portEnabled(int index) { return valid(index) && enabled[index]; }
    public boolean portTrunk(int index) { return valid(index) && trunk[index]; }
    public boolean portIsolated(int index) { return valid(index) && isolated[index]; }
    public int portVlan(int index) { return valid(index) ? vlan[index] : 0; }
    public int portDress(int index) {
        return valid(index) ? dress[index] : 0;
    }

    public boolean cyclePortDress(int index) {
        if (!valid(index)) return false;
        dress[index] = (dress[index] + 1) % 3;
        return true;
    }

    public boolean portPairAllowed(int first, int second) {
        return valid(first) && valid(second) && first != second &&
                (deniedPairs[first] & (1 << second)) == 0 &&
                (deniedPairs[second] & (1 << first)) == 0;
    }

    public int acceptIngressVlan(int ingress, int incomingVlan) {
        if (!powered || !type.isSwitch() || !portEnabled(ingress) || incomingVlan < 0 || incomingVlan > 4094)
            return -1;
        if (type == RackModuleType.SWITCH) return incomingVlan;
        if (incomingVlan == 0) return portVlan(ingress);
        return portTrunk(ingress) || portVlan(ingress) == incomingVlan ? incomingVlan : -1;
    }

    public boolean allowsForward(int ingress, int egress, int trafficVlan) {
        if (!powered || !type.isSwitch() || !valid(ingress) || !valid(egress) || ingress == egress)
            return false;
        if (type == RackModuleType.SWITCH) return true;
        return portEnabled(ingress) && portEnabled(egress) && portPairAllowed(ingress, egress) &&
                !(portIsolated(ingress) && portIsolated(egress)) &&
                trafficVlan >= 1 && trafficVlan <= 4094 &&
                (portTrunk(ingress) || portVlan(ingress) == trafficVlan) &&
                (portTrunk(egress) || portVlan(egress) == trafficVlan);
    }

    public boolean setPortEnabled(int index, boolean value) {
        if (!managed(index) || enabled[index] == value) return false;
        enabled[index] = value;
        return true;
    }

    public boolean setPortTrunk(int index, boolean value) {
        if (!managed(index) || trunk[index] == value) return false;
        trunk[index] = value;
        return true;
    }

    public boolean setPortIsolated(int index, boolean value) {
        if (!managed(index) || isolated[index] == value) return false;
        isolated[index] = value;
        return true;
    }

    public boolean setPortVlan(int index, int value) {
        if (!managed(index) || value < 1 || value > 4094 || vlan[index] == value) return false;
        vlan[index] = value;
        return true;
    }

    public boolean setPortPairAllowed(int first, int second, boolean allowed) {
        if (!managed(first) || !valid(second) || first == second) return false;
        int bitFirst = 1 << second;
        int bitSecond = 1 << first;
        boolean current = portPairAllowed(first, second);
        if (current == allowed) return false;
        if (allowed) {
            deniedPairs[first] &= ~bitFirst;
            deniedPairs[second] &= ~bitSecond;
        } else {
            deniedPairs[first] |= bitFirst;
            deniedPairs[second] |= bitSecond;
        }
        return true;
    }

    private boolean valid(int index) { return index >= 0 && index < ports.size(); }
    private boolean managed(int index) { return type == RackModuleType.MANAGED_SWITCH && valid(index); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Type", type.name());
        tag.putInt("StartU", startU);
        tag.putBoolean("Powered", powered);
        ListTag savedPorts = new ListTag();
        for (DataPort port : ports) savedPorts.add(port.save());
        tag.put("Ports", savedPorts);
        for (int i = 0; i < ports.size(); i++) {
            CompoundTag settings = new CompoundTag();
            settings.putBoolean("Enabled", enabled[i]);
            settings.putBoolean("Trunk", trunk[i]);
            settings.putBoolean("Isolated", isolated[i]);
            settings.putInt("Vlan", vlan[i]);
            settings.putInt("DeniedPairs", deniedPairs[i]);
            settings.putInt("Dress", dress[i]);
            tag.put("Settings" + i, settings);
        }
        return tag;
    }

    public static RackModule load(CompoundTag tag) {
        if (tag == null || !tag.hasUUID("Id")) return null;
        RackModuleType type;
        try { type = RackModuleType.valueOf(tag.getString("Type")); }
        catch (IllegalArgumentException exception) { return null; }
        int startU = tag.getInt("StartU");
        if (startU < 0 || startU > 95) return null;
        RackModule module = new RackModule(tag.getUUID("Id"), type, startU);
        module.powered = tag.getBoolean("Powered");
        ListTag savedPorts = tag.getList("Ports", 10);
        int count = Math.max(type.portCount(), Math.min(module.maxPorts(), savedPorts.size()));
        while (module.portCount() < count) module.addPort();
        Set<UUID> used = new HashSet<>();
        for (int i = 0; i < module.portCount() && i < savedPorts.size(); i++) {
            DataPort port = DataPort.load(savedPorts.getCompound(i));
            if (port != null && used.add(port.id)) module.ports.set(i, port);
        }
        for (int i = 0; i < module.portCount(); i++) module.ports.get(i).u =
                (float) ((i + 1D) / (module.portCount() + 1D));
        for (int i = 0; i < module.portCount(); i++) {
            String key = "Settings" + i;
            if (!tag.contains(key, 10)) continue;
            CompoundTag settings = tag.getCompound(key);
            module.enabled[i] = settings.getBoolean("Enabled");
            module.trunk[i] = settings.getBoolean("Trunk");
            module.isolated[i] = settings.getBoolean("Isolated");
            int savedVlan = settings.getInt("Vlan");
            module.vlan[i] = savedVlan >= 1 && savedVlan <= 4094 ? savedVlan : 1;
            module.deniedPairs[i] = settings.getInt("DeniedPairs") & ((1 << module.portCount()) - 1);
            int savedDress = settings.getInt("Dress");
            module.dress[i] = savedDress >= 0 && savedDress <= 2 ? savedDress : 0;
        }
        return module;
    }
}
