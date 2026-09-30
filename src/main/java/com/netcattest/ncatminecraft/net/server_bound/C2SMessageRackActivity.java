package com.netcattest.ncatminecraft.net.server_bound;

import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.entity.RackBlockEntity;
import com.netcattest.ncatminecraft.entity.RackModule;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.net.Packet;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class C2SMessageRackActivity extends Packet {
    private static final Map<ServerPlayer, Long> LAST = new WeakHashMap<>();
    private final BlockPos workstationPos;
    private final BlockSide side;
    private final BlockPos rackPos;
    private final UUID moduleId;

    public C2SMessageRackActivity(BlockPos workstationPos, BlockSide side, BlockPos rackPos, UUID moduleId) {
        this.workstationPos = workstationPos;
        this.side = side;
        this.rackPos = rackPos;
        this.moduleId = moduleId;
    }

    public C2SMessageRackActivity(FriendlyByteBuf buffer) {
        workstationPos = buffer.readBlockPos();
        side = BlockSide.fromInt(buffer.readUnsignedByte());
        rackPos = buffer.readBlockPos();
        moduleId = buffer.readUUID();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(workstationPos);
        buffer.writeByte(side.ordinal());
        buffer.writeBlockPos(rackPos);
        buffer.writeUUID(moduleId);
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        if (!checkServer(context)) return;
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || side == null) return;
            ServerLevel level = player.serverLevel();
            Long last = LAST.get(player);
            if (last != null && level.getGameTime() - last < 2L) return;
            LAST.put(player, level.getGameTime());
            if (!level.hasChunkAt(workstationPos) || !level.hasChunkAt(rackPos) ||
                    player.distanceToSqr(workstationPos.getX() + .5D, workstationPos.getY() + .5D,
                            workstationPos.getZ() + .5D) > 64D * 64D) return;
            if (!(level.getBlockEntity(workstationPos) instanceof ScreenBlockEntity workstation) ||
                    !BlockRegistry.isWorkstationScreen(workstation.getBlockState().getBlock())) return;
            ScreenData display = workstation.getScreen(side);
            RackBlockEntity rack = RackBlockEntity.at(level, rackPos);
            RackModule module = rack == null ? null : rack.getModule(moduleId);
            if (display == null || display.owner == null ||
                    (display.rightsFor(player) & ScreenRights.INTERACT) == 0 ||
                    rack == null || !rack.canConfigure(player) || module == null || !module.powered()) return;
            DataCableService.recordRackTraffic(level, workstation, side,
                    new DataCableService.RackModuleEndpoint(rack, module));
        });
        context.setPacketHandled(true);
    }
}
