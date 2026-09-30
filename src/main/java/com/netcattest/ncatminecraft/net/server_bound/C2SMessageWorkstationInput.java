package com.netcattest.ncatminecraft.net.server_bound;

import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.controls.builtin.ClickControl;
import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.net.Packet;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.net.client_bound.S2CMessageScreenUpdate;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;
import com.netcattest.ncatminecraft.utilities.serialization.TypeData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.WeakHashMap;

public final class C2SMessageWorkstationInput extends Packet {
    private static final int MAX_KEYS_LENGTH = 8192;
    private static final Map<ServerPlayer, Budget> BUDGETS = new WeakHashMap<>();

    private enum Kind { MOVE, DOWN, UP, CLICK, KEYS }

    private static final class Budget {
        private long tick = Long.MIN_VALUE;
        private int count;
        private long moveTick = Long.MIN_VALUE;
    }

    private final BlockPos workstationPos;
    private final BlockSide workstationSide;
    private final BlockPos targetPos;
    private final BlockSide targetSide;
    private final Kind kind;
    private final int x;
    private final int y;
    private final int button;
    private final String keys;

    private C2SMessageWorkstationInput(BlockPos workstationPos, BlockSide workstationSide,
                                       BlockPos targetPos, BlockSide targetSide, Kind kind,
                                       int x, int y, int button, String keys) {
        this.workstationPos = workstationPos;
        this.workstationSide = workstationSide;
        this.targetPos = targetPos;
        this.targetSide = targetSide;
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.button = button;
        this.keys = keys;
    }

    public static C2SMessageWorkstationInput mouse(ScreenBlockEntity workstation, BlockSide workstationSide,
                                                    ScreenBlockEntity target, BlockSide targetSide,
                                                    ClickControl.ControlType event, Vector2i position, int button) {
        Kind kind = switch (event) {
            case MOVE -> Kind.MOVE;
            case DOWN -> Kind.DOWN;
            case UP -> Kind.UP;
            case CLICK -> Kind.CLICK;
        };
        return new C2SMessageWorkstationInput(workstation.getBlockPos(), workstationSide,
                target.getBlockPos(), targetSide, kind, position.x, position.y, button, "");
    }

    public static C2SMessageWorkstationInput keys(ScreenBlockEntity workstation, BlockSide workstationSide,
                                                   ScreenBlockEntity target, BlockSide targetSide, String keys) {
        return new C2SMessageWorkstationInput(workstation.getBlockPos(), workstationSide,
                target.getBlockPos(), targetSide, Kind.KEYS, 0, 0, -1, keys);
    }

    public C2SMessageWorkstationInput(FriendlyByteBuf buffer) {
        workstationPos = buffer.readBlockPos();
        workstationSide = BlockSide.fromInt(buffer.readByte());
        targetPos = buffer.readBlockPos();
        targetSide = BlockSide.fromInt(buffer.readByte());
        int index = buffer.readUnsignedByte();
        kind = index < Kind.values().length ? Kind.values()[index] : null;
        if (kind == Kind.KEYS) {
            keys = buffer.readUtf(MAX_KEYS_LENGTH);
            x = 0;
            y = 0;
            button = -1;
        } else {
            x = buffer.readInt();
            y = buffer.readInt();
            button = buffer.readByte();
            keys = "";
        }
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(workstationPos);
        buffer.writeByte(workstationSide.ordinal());
        buffer.writeBlockPos(targetPos);
        buffer.writeByte(targetSide.ordinal());
        buffer.writeByte(kind.ordinal());
        if (kind == Kind.KEYS) buffer.writeUtf(keys, MAX_KEYS_LENGTH);
        else {
            buffer.writeInt(x);
            buffer.writeInt(y);
            buffer.writeByte(button);
        }
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        if (!checkServer(context)) return;
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || kind == null || workstationSide == null || targetSide == null ||
                    workstationPos.equals(targetPos)) return;
            ServerLevel level = player.serverLevel();
            if (!allow(player, level.getGameTime(), kind) || !level.hasChunkAt(workstationPos) ||
                    !level.hasChunkAt(targetPos) ||
                    player.distanceToSqr(workstationPos.getX() + .5D, workstationPos.getY() + .5D,
                            workstationPos.getZ() + .5D) > 64D * 64D) return;
            BlockEntity workstationBlock = level.getBlockEntity(workstationPos);
            BlockEntity targetBlock = level.getBlockEntity(targetPos);
            if (!(workstationBlock instanceof ScreenBlockEntity workstation) ||
                    !(targetBlock instanceof ScreenBlockEntity target) ||
                    !BlockRegistry.isWorkstationScreen(workstation.getBlockState().getBlock()) ||
                    !BlockRegistry.isBrowserScreen(target.getBlockState().getBlock())) return;
            ScreenData home = workstation.getScreen(workstationSide);
            ScreenData remote = target.getScreen(targetSide);
            if (home == null || remote == null || home.owner == null || remote.owner == null ||
                    (home.rightsFor(player) & ScreenRights.INTERACT) == 0 ||
                    (remote.rightsFor(player) & ScreenRights.INTERACT) == 0) return;

            if (kind == Kind.KEYS) {
                if (!validKeys(keys)) return;
            } else {
                int width = remote.rotation.isVertical ? remote.resolution.y : remote.resolution.x;
                int height = remote.rotation.isVertical ? remote.resolution.x : remote.resolution.y;
                if (x < 0 || y < 0 || x >= width || y >= height ||
                        (kind == Kind.MOVE ? button != -1 : button < 0 || button > 1)) return;
            }
            if (!DataCableService.recordTraffic(level, workstation, workstationSide, target, targetSide)) return;
            S2CMessageScreenUpdate update = kind == Kind.KEYS
                    ? S2CMessageScreenUpdate.type(target, targetSide, keys)
                    : S2CMessageScreenUpdate.click(target, targetSide,
                            ClickControl.ControlType.valueOf(kind.name()), new Vector2i(x, y), button);
            for (ServerPlayer observer : level.players()) {
                if (observer == player) continue;
                double targetDistance = observer.distanceToSqr(targetPos.getX() + .5D, targetPos.getY() + .5D,
                        targetPos.getZ() + .5D);
                double workstationDistance = observer.distanceToSqr(workstationPos.getX() + .5D,
                        workstationPos.getY() + .5D, workstationPos.getZ() + .5D);
                if (targetDistance > 64D * 64D && workstationDistance > 64D * 64D) continue;
                WDNetworkRegistry.INSTANCE.send(PacketDistributor.PLAYER.with(() -> observer), update);
            }
        });
        context.setPacketHandled(true);
    }

    private static boolean allow(ServerPlayer player, long now, Kind kind) {
        Budget budget = BUDGETS.computeIfAbsent(player, ignored -> new Budget());
        if (budget.tick != now) {
            budget.tick = now;
            budget.count = 0;
        }
        if (++budget.count > 24) return false;
        if (kind == Kind.MOVE) {
            if (budget.moveTick != Long.MIN_VALUE && now >= budget.moveTick && now - budget.moveTick < 2L) return false;
            budget.moveTick = now;
        }
        return true;
    }

    private static boolean validKeys(String text) {
        if (text == null || text.isEmpty() || text.length() > MAX_KEYS_LENGTH || !text.startsWith("[")) return false;
        try {
            TypeData[] events = NcatMinecraft.GSON.fromJson(text, TypeData[].class);
            if (events == null || events.length == 0 || events.length > 128) return false;
            for (TypeData event : events)
                if (event == null || event.getAction() == null || event.getAction() == TypeData.Action.INVALID ||
                        event.getKeyCode() < 0 || event.getKeyCode() > 65535 ||
                        event.getScanCode() < 0 || event.getScanCode() > 65535 ||
                        event.getModifier() < 0 || event.getModifier() > 255) return false;
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
