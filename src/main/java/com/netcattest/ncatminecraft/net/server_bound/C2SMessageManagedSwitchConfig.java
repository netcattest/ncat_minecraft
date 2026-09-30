package com.netcattest.ncatminecraft.net.server_bound;

import com.netcattest.ncatminecraft.entity.ManagedSwitchBlockEntity;
import com.netcattest.ncatminecraft.entity.ManagedTabletBlockEntity;
import com.netcattest.ncatminecraft.block.ManagedTabletBlock;
import com.netcattest.ncatminecraft.item.ManagedTabletItem;
import com.netcattest.ncatminecraft.net.Packet;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.utilities.SerialCableService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.WeakHashMap;

public final class C2SMessageManagedSwitchConfig extends Packet {
    public enum Action {
        PORT_ENABLED, PORT_TRUNK, PORT_ISOLATION, PORT_VLAN,
        PORT_LABEL, PAIR_ALLOWED, PANEL, SELECT_PORT, POWER
    }

    private static final Map<ServerPlayer, Budget> BUDGETS = new WeakHashMap<>();

    private static final class Budget {
        long tick = Long.MIN_VALUE;
        int count;
    }

    private final BlockPos tabletPos;
    private final BlockPos switchPos;
    private final Action action;
    private final int port;
    private final int other;
    private final int value;
    private final String text;

    public C2SMessageManagedSwitchConfig(BlockPos tabletPos, BlockPos switchPos, Action action,
                                         int port, int other, int value, String text) {
        this.tabletPos = tabletPos;
        this.switchPos = switchPos;
        this.action = action;
        this.port = port;
        this.other = other;
        this.value = value;
        this.text = text == null ? "" : text;
    }

    public C2SMessageManagedSwitchConfig(FriendlyByteBuf buffer) {
        tabletPos = buffer.readBlockPos();
        switchPos = buffer.readBlockPos();
        int ordinal = buffer.readUnsignedByte();
        action = ordinal < Action.values().length ? Action.values()[ordinal] : null;
        port = buffer.readByte();
        other = buffer.readByte();
        value = buffer.readInt();
        text = buffer.readUtf(96);
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(tabletPos);
        buffer.writeBlockPos(switchPos);
        buffer.writeByte(action.ordinal());
        buffer.writeByte(port);
        buffer.writeByte(other);
        buffer.writeInt(value);
        buffer.writeUtf(text, 96);
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        if (!checkServer(context)) return;
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || action == null) return;
            ServerLevel level = player.serverLevel();
            Budget budget = BUDGETS.computeIfAbsent(player, ignored -> new Budget());
            if (budget.tick != level.getGameTime()) {
                budget.tick = level.getGameTime();
                budget.count = 0;
            }
            if (++budget.count > 8 || !level.hasChunkAt(tabletPos) || !level.hasChunkAt(switchPos) ||
                    player.distanceToSqr(tabletPos.getX() + .5D, tabletPos.getY() + .5D,
                            tabletPos.getZ() + .5D) > 12D * 12D) return;
            BlockEntity tabletBlock = level.getBlockEntity(tabletPos);
            BlockEntity switchBlock = level.getBlockEntity(switchPos);
            if (!(tabletBlock instanceof ManagedTabletBlockEntity tablet) ||
                    !(switchBlock instanceof ManagedSwitchBlockEntity networkSwitch) ||
                    (!tablet.getBlockState().getValue(ManagedTabletBlock.DOCKED) &&
                            !ManagedTabletItem.isHeldBy(player, level, tablet)) ||
                    SerialCableService.connectedSwitch(level, tablet) != networkSwitch ||
                    !networkSwitch.canConfigure(player)) return;
            switch (action) {
                case PORT_ENABLED -> {
                    if (booleanValue(value)) networkSwitch.setPortEnabled(port, value == 1);
                }
                case PORT_TRUNK -> {
                    if (booleanValue(value)) networkSwitch.setPortTrunk(port, value == 1);
                }
                case PORT_ISOLATION -> {
                    if (booleanValue(value)) networkSwitch.setPortIsolation(port, value == 1);
                }
                case PORT_VLAN -> networkSwitch.setPortVlan(port, value);
                case PORT_LABEL -> networkSwitch.setPortLabel(port, text);
                case PAIR_ALLOWED -> {
                    if (booleanValue(value)) networkSwitch.setPairAllowed(port, other, value == 1);
                }
                case PANEL -> tablet.setActivePanel(text);
                case SELECT_PORT -> tablet.setSelectedPort(port);
                case POWER -> {
                    if (booleanValue(value)) {
                        networkSwitch.setPowered(value == 1);
                        DataCableService.refreshSwitch(level, networkSwitch);
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }

    private static boolean booleanValue(int value) {
        return value == 0 || value == 1;
    }
}
