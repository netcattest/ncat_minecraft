package com.netcattest.ncatminecraft.net.server_bound;

import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.entity.RackBlockEntity;
import com.netcattest.ncatminecraft.entity.RackModule;
import com.netcattest.ncatminecraft.entity.RackModuleType;
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

public final class C2SMessageRackFlow extends Packet {
    private record End(BlockPos pos, UUID module, BlockSide side) { }
    private record Pair(End from, End to) { }
    private static final Map<ServerPlayer, Map<Pair, Long>> LAST = new WeakHashMap<>();
    private final End from;
    private final End to;

    private C2SMessageRackFlow(End from, End to) {
        this.from = from;
        this.to = to;
    }

    public C2SMessageRackFlow(BlockPos fromPos, UUID fromModule, BlockSide fromSide,
                              BlockPos toPos, UUID toModule, BlockSide toSide) {
        this(new End(fromPos, fromModule, fromSide), new End(toPos, toModule, toSide));
    }

    public C2SMessageRackFlow(FriendlyByteBuf buffer) {
        from = readEnd(buffer);
        to = readEnd(buffer);
    }

    private static End readEnd(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        UUID module = buffer.readBoolean() ? buffer.readUUID() : null;
        BlockSide side = module == null ? BlockSide.fromInt(buffer.readUnsignedByte()) : null;
        return new End(pos, module, side);
    }

    private static void writeEnd(FriendlyByteBuf buffer, End end) {
        buffer.writeBlockPos(end.pos());
        buffer.writeBoolean(end.module() != null);
        if (end.module() != null) buffer.writeUUID(end.module());
        else buffer.writeByte(end.side().ordinal());
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        writeEnd(buffer, from);
        writeEnd(buffer, to);
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        if (!checkServer(context)) return;
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || from.side() == null && from.module() == null ||
                    to.side() == null && to.module() == null) return;
            ServerLevel level = player.serverLevel();
            long tick = level.getGameTime();
            if (!level.hasChunkAt(from.pos()) || !level.hasChunkAt(to.pos()) ||
                    player.distanceToSqr(from.pos().getX() + .5D, from.pos().getY() + .5D,
                            from.pos().getZ() + .5D) > 64D * 64D &&
                    player.distanceToSqr(to.pos().getX() + .5D, to.pos().getY() + .5D,
                            to.pos().getZ() + .5D) > 64D * 64D) return;
            Map<Pair, Long> recent = LAST.computeIfAbsent(player, ignored -> new java.util.HashMap<>());
            Pair pair = new Pair(from, to);
            Long previous = recent.get(pair);
            if (previous != null && tick >= previous && tick - previous < 2L) return;
            recent.put(pair, tick);
            if (recent.size() > 128) recent.clear();
            RackBlockEntity fromRack = from.module() == null ? null : RackBlockEntity.at(level, from.pos());
            RackModule fromModule = fromRack == null ? null : fromRack.getModule(from.module());
            ScreenBlockEntity fromScreen = from.module() != null ||
                    !(level.getBlockEntity(from.pos()) instanceof ScreenBlockEntity screen) ? null : screen;
            RackBlockEntity toRack = to.module() == null ? null : RackBlockEntity.at(level, to.pos());
            RackModule toModule = toRack == null ? null : toRack.getModule(to.module());
            ScreenBlockEntity toScreen = to.module() != null ||
                    !(level.getBlockEntity(to.pos()) instanceof ScreenBlockEntity screen) ? null : screen;
            RackModuleType fromType = fromModule == null ? screenType(fromScreen) : fromModule.type();
            RackModuleType toType = toModule == null ? screenType(toScreen) : toModule.type();
            if (!(fromType == RackModuleType.LOG || fromType == RackModuleType.DEVTOOLS ||
                    fromType == RackModuleType.PROXY || fromType == RackModuleType.SFTP) ||
                    toType != (fromType == RackModuleType.SFTP ? RackModuleType.SSH : RackModuleType.BROWSER)) return;
            if (fromRack != null && (!fromRack.canConfigure(player) || !fromModule.powered()) ||
                    toRack != null && (!toRack.canConfigure(player) || !toModule.powered()) ||
                    fromScreen != null && !screenAllowed(fromScreen, from.side(), player) ||
                    toScreen != null && !screenAllowed(toScreen, to.side(), player)) return;
            if (fromRack != null && toRack != null)
                DataCableService.recordRackTraffic(level,
                        new DataCableService.RackModuleEndpoint(fromRack, fromModule),
                        new DataCableService.RackModuleEndpoint(toRack, toModule));
            else if (fromRack != null && toScreen != null)
                DataCableService.recordRackTraffic(level,
                        new DataCableService.RackModuleEndpoint(fromRack, fromModule), toScreen, to.side());
            else if (fromScreen != null && toRack != null)
                DataCableService.recordRackTraffic(level, fromScreen, from.side(),
                        new DataCableService.RackModuleEndpoint(toRack, toModule));
        });
        context.setPacketHandled(true);
    }

    private static RackModuleType screenType(ScreenBlockEntity screen) {
        if (screen == null) return null;
        var block = screen.getBlockState().getBlock();
        if (BlockRegistry.isLogScreen(block)) return RackModuleType.LOG;
        if (BlockRegistry.isDevToolsScreen(block)) return RackModuleType.DEVTOOLS;
        if (BlockRegistry.isProxyScreen(block)) return RackModuleType.PROXY;
        if (BlockRegistry.isSftpScreen(block)) return RackModuleType.SFTP;
        if (BlockRegistry.isBrowserScreen(block)) return RackModuleType.BROWSER;
        if (BlockRegistry.isSshScreen(block)) return RackModuleType.SSH;
        return null;
    }

    private static boolean screenAllowed(ScreenBlockEntity screen, BlockSide side, ServerPlayer player) {
        ScreenData data = side == null ? null : screen.getScreen(side);
        return data != null && data.owner != null && (data.rightsFor(player) & ScreenRights.INTERACT) != 0;
    }

}
