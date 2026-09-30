package com.netcattest.ncatminecraft.net.server_bound;

import com.netcattest.ncatminecraft.entity.RackBlockEntity;
import com.netcattest.ncatminecraft.entity.RackModule;
import com.netcattest.ncatminecraft.item.ItemDataCable;
import com.netcattest.ncatminecraft.item.RackModuleItem;
import com.netcattest.ncatminecraft.net.Packet;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class C2SMessageRackAction extends Packet {
    public enum Action { INSTALL, REMOVE, POWER, DOOR, CABLE, ADD_PORT,
        PORT_ENABLED, PORT_TRUNK, PORT_ISOLATED, PORT_VLAN, PORT_PAIR, PORT_DRESS }

    private static final Map<ServerPlayer, Budget> BUDGETS = new WeakHashMap<>();

    private static final class Budget {
        long tick = Long.MIN_VALUE;
        int count;
    }

    private final BlockPos position;
    private final Action action;
    private final UUID module;
    private final int value;

    public C2SMessageRackAction(BlockPos position, Action action, UUID module, int value) {
        this.position = position;
        this.action = action;
        this.module = module;
        this.value = value;
    }

    public C2SMessageRackAction(FriendlyByteBuf buffer) {
        position = buffer.readBlockPos();
        int ordinal = buffer.readUnsignedByte();
        action = ordinal < Action.values().length ? Action.values()[ordinal] : null;
        module = buffer.readBoolean() ? buffer.readUUID() : null;
        value = buffer.readVarInt();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(position);
        buffer.writeByte(action.ordinal());
        buffer.writeBoolean(module != null);
        if (module != null) buffer.writeUUID(module);
        buffer.writeVarInt(value);
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        if (!checkServer(context)) return;
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || action == null) return;
            ServerLevel level = player.serverLevel();
            if (!level.hasChunkAt(position) || player.distanceToSqr(position.getX() + .5D,
                    position.getY() + .5D, position.getZ() + .5D) > 144D) return;
            Budget budget = BUDGETS.computeIfAbsent(player, ignored -> new Budget());
            if (budget.tick != level.getGameTime()) {
                budget.tick = level.getGameTime();
                budget.count = 0;
            }
            if (++budget.count > 12) return;
            RackBlockEntity rack = RackBlockEntity.at(level, position);
            if (rack == null || !rack.canConfigure(player)) return;
            if (action == Action.INSTALL) {
                ItemStack held = player.getMainHandItem();
                if (!(held.getItem() instanceof RackModuleItem item) || value < 0 || value >= rack.getCapacityU()) return;
                if (rack.installModule(item.type(), value, player) && !player.getAbilities().instabuild) held.shrink(1);
                return;
            }
            if (action == Action.DOOR) {
                if (value == 0 || value == 1) rack.setDoorOpen(value == 1);
                return;
            }
            if (module == null) return;
            RackModule target = rack.getModule(module);
            if (target == null) return;
            switch (action) {
                case REMOVE -> {
                    ItemStack removed = rack.removeModule(module, player);
                    if (!removed.isEmpty() && !player.getInventory().add(removed)) player.drop(removed, false);
                }
                case POWER -> {
                    if (value == 0 || value == 1) rack.setModulePowered(module, value == 1, player);
                }
                case CABLE -> {
                    ItemStack held = player.getMainHandItem();
                    if (!(held.getItem() instanceof ItemDataCable)) return;
                    DataCableService.RackEndpoint endpoint = DataCableService.rackEndpoint(rack, module, value);
                    if (endpoint != null) ItemDataCable.selectOrConnect(level, player, held, endpoint);
                }
                case ADD_PORT -> rack.addModulePort(module, player);
                case PORT_DRESS -> {
                    int side = rack.cycleModulePortDress(module, value, player);
                    if (side >= 0)
                        player.displayClientMessage(Component.translatable(
                                "block.ncat_minecraft.rack.dress." + side), true);
                }
                case PORT_ENABLED -> rack.setModulePortEnabled(module, value >>> 1, (value & 1) != 0, player);
                case PORT_TRUNK -> rack.setModulePortTrunk(module, value >>> 1, (value & 1) != 0, player);
                case PORT_ISOLATED -> rack.setModulePortIsolated(module, value >>> 1, (value & 1) != 0, player);
                case PORT_VLAN -> rack.setModulePortVlan(module, value >>> 12, value & 0xFFF, player);
                case PORT_PAIR -> rack.setModulePortPairAllowed(module, value >>> 5, (value >>> 1) & 0xF,
                        (value & 1) != 0, player);
                default -> { }
            }
        });
        context.setPacketHandled(true);
    }
}
