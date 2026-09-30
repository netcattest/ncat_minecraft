package com.netcattest.ncatminecraft.net.server_bound;

import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.net.Packet;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;

public final class C2SMessageDataActivity extends Packet {
    private record Link(BlockPos sourcePos, BlockSide sourceSide, BlockPos targetPos, BlockSide targetSide) { }
    private static final Map<ServerPlayer, LinkedHashMap<Link, Long>> LAST_REQUEST = new WeakHashMap<>();

    private final BlockPos sourcePos;
    private final BlockSide sourceSide;
    private final BlockPos targetPos;
    private final BlockSide targetSide;

    public C2SMessageDataActivity(BlockPos sourcePos, BlockSide sourceSide,
                                  BlockPos targetPos, BlockSide targetSide) {
        this.sourcePos = sourcePos;
        this.sourceSide = sourceSide;
        this.targetPos = targetPos;
        this.targetSide = targetSide;
    }

    public C2SMessageDataActivity(FriendlyByteBuf buffer) {
        sourcePos = buffer.readBlockPos();
        sourceSide = BlockSide.fromInt(buffer.readByte());
        targetPos = buffer.readBlockPos();
        targetSide = BlockSide.fromInt(buffer.readByte());
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(sourcePos);
        buffer.writeByte(sourceSide.ordinal());
        buffer.writeBlockPos(targetPos);
        buffer.writeByte(targetSide.ordinal());
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        if (!checkServer(context)) return;
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || sourceSide == null || targetSide == null || sourcePos.equals(targetPos)) return;
            Level level = player.level();
            long now = level.getGameTime();
            LinkedHashMap<Link, Long> recent = LAST_REQUEST.computeIfAbsent(player, ignored -> new LinkedHashMap<>());
            Link link = new Link(sourcePos, sourceSide, targetPos, targetSide);
            Long last = recent.get(link);
            if (last != null && now >= last && now - last < 3L) return;
            recent.put(link, now);
            if (recent.size() > 128) recent.remove(recent.keySet().iterator().next());
            if (!level.hasChunkAt(sourcePos) || !level.hasChunkAt(targetPos)) return;
            double sourceDistance = player.distanceToSqr(sourcePos.getX() + .5D, sourcePos.getY() + .5D,
                    sourcePos.getZ() + .5D);
            double targetDistance = player.distanceToSqr(targetPos.getX() + .5D, targetPos.getY() + .5D,
                    targetPos.getZ() + .5D);
            if (sourceDistance > 64D * 64D && targetDistance > 64D * 64D) return;
            BlockEntity sourceBlock = level.getBlockEntity(sourcePos);
            BlockEntity targetBlock = level.getBlockEntity(targetPos);
            if (!(sourceBlock instanceof ScreenBlockEntity source) ||
                    !(targetBlock instanceof ScreenBlockEntity target)) return;
            ScreenData home = source.getScreen(sourceSide);
            ScreenData remote = target.getScreen(targetSide);
            if (home == null || remote == null || home.owner == null || remote.owner == null ||
                    (home.rightsFor(player) & ScreenRights.INTERACT) == 0 ||
                    (remote.rightsFor(player) & ScreenRights.INTERACT) == 0) return;
            DataCableService.recordTraffic(level, source, sourceSide, target, targetSide);
        });
        context.setPacketHandled(true);
    }

    public static void send(ScreenBlockEntity source, BlockSide sourceSide,
                            ScreenBlockEntity target, BlockSide targetSide) {
        if (source == null || target == null || sourceSide == null || targetSide == null ||
                source.getLevel() == null || !source.getLevel().isClientSide) return;
        WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageDataActivity(
                source.getBlockPos(), sourceSide, target.getBlockPos(), targetSide));
    }
}
