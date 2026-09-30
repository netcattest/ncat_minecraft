package com.netcattest.ncatminecraft.net.server_bound;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.netcattest.ncatminecraft.net.Packet;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.RackBlockEntity;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import java.util.UUID;

public final class C2SMessageLogTablet extends Packet {
    private final BlockPos position;
    private final String detail;
    private final UUID rackModule;

    public C2SMessageLogTablet(BlockPos position, String detail) {
        this(position, null, detail);
    }

    public C2SMessageLogTablet(BlockPos position, UUID rackModule, String detail) {
        this.position = position;
        this.rackModule = rackModule;
        this.detail = detail;
    }

    public C2SMessageLogTablet(FriendlyByteBuf buffer) {
        position = buffer.readBlockPos();
        rackModule = buffer.readBoolean() ? buffer.readUUID() : null;
        detail = buffer.readUtf(131072);
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(position);
        buffer.writeBoolean(rackModule != null);
        if (rackModule != null) buffer.writeUUID(rackModule);
        buffer.writeUtf(detail, 131072);
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        if (!checkServer(context)) return;
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || detail.length() > 131072 ||
                    player.getMainHandItem().getItem() != ItemRegistry.LOG_INSPECTOR.get() ||
                    player.distanceToSqr(position.getX() + .5, position.getY() + .5, position.getZ() + .5) > 64 * 64)
                return;
            if (!player.level().hasChunkAt(position)) return;
            if (rackModule == null) {
                BlockEntity entity = player.level().getBlockEntity(position);
                if (!(entity instanceof ScreenBlockEntity) || !BlockRegistry.isLogScreen(entity.getBlockState().getBlock())) return;
            } else {
                RackBlockEntity rack = RackBlockEntity.at(player.level(), position);
                if (rack == null || !rack.canConfigure(player) || rack.getModule(rackModule) == null ||
                        rack.getModule(rackModule).type() != RackModuleType.LOG ||
                        !rack.getModule(rackModule).powered()) return;
            }
            try {
                JsonObject parsed = JsonParser.parseString(detail).getAsJsonObject();
                if (!parsed.has("id") || !parsed.has("method") || !parsed.has("fullUrl") || !parsed.has("requestHeaders"))
                    return;
                ItemStack tablet = new ItemStack(ItemRegistry.LOG_TABLET.get());
                tablet.getOrCreateTag().putString("NcatLogDetail", detail);
                if (!player.getInventory().add(tablet)) player.drop(tablet, false);
            } catch (RuntimeException ignored) { }
        });
    }
}
