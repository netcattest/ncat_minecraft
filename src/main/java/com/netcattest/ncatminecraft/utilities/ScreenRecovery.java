package com.netcattest.ncatminecraft.utilities;

import com.netcattest.ncatminecraft.block.ScreenBlock;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.net.client_bound.S2CMessageAddScreen;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class ScreenRecovery {
    private static final long LIFETIME_MS = 600000;
    private static final Map<Level, List<Snapshot>> PENDING = new WeakHashMap<>();

    private ScreenRecovery() { }

    public static synchronized void remember(Level level, BlockPos removed) {
        if (level.isClientSide) return;
        List<Snapshot> list = PENDING.computeIfAbsent(level, ignored -> new ArrayList<>());
        list.removeIf(snapshot -> System.currentTimeMillis() - snapshot.time > LIFETIME_MS);
        Multiblock.BlockOverride override = new Multiblock.BlockOverride(
                new Vector3i(removed), Multiblock.OverrideAction.SIMULATE);
        for (BlockSide side : BlockSide.values()) {
            Vector3i origin = new Vector3i(removed);
            Multiblock.findOrigin(level, origin, side, override);
            BlockPos originPos = origin.toBlock();
            BlockEntity blockEntity = level.getBlockEntity(originPos);
            if (!(blockEntity instanceof ScreenBlockEntity entity)) continue;
            ScreenData screen = entity.getScreen(side);
            if (screen == null || !contains(originPos, screen, removed)) continue;
            Block block = level.getBlockState(originPos).getBlock();
            CompoundTag saved = screen.serialize();
            saved.remove("Upgrades");
            list.removeIf(snapshot -> snapshot.origin.equals(originPos) && snapshot.side == side);
            list.add(new Snapshot(originPos.immutable(), side, block, saved, System.currentTimeMillis()));
        }
    }

    public static synchronized void restore(Level level, BlockPos placed) {
        if (level.isClientSide) return;
        List<Snapshot> list = PENDING.get(level);
        if (list == null) return;
        long now = System.currentTimeMillis();
        list.removeIf(snapshot -> now - snapshot.time > LIFETIME_MS);
        List<Snapshot> ready = new ArrayList<>();
        for (Snapshot snapshot : list) {
            ScreenData data = ScreenData.deserialize(snapshot.data);
            if (contains(snapshot.origin, data, placed) && complete(level, snapshot, data)) ready.add(snapshot);
        }
        if (ready.isEmpty()) return;
        for (Snapshot snapshot : ready) {
            BlockPos origin = snapshot.origin;
            if (!(level.getBlockState(origin).getBlock() instanceof ScreenBlock)) continue;
            if (!level.getBlockState(origin).getValue(ScreenBlock.hasTE))
                level.setBlockAndUpdate(origin, level.getBlockState(origin).setValue(ScreenBlock.hasTE, true));
            if (!(level.getBlockEntity(origin) instanceof ScreenBlockEntity entity)) continue;
            ScreenData present = entity.getScreen(snapshot.side);
            if (present != null) {
                ScreenData saved = ScreenData.deserialize(snapshot.data);
                if (present.size != null && saved.size != null &&
                        present.size.x == saved.size.x && present.size.y == saved.size.y) {
                    entity.setChanged();
                    WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() ->
                            new PacketDistributor.TargetPoint(origin.getX(), origin.getY(), origin.getZ(), 64,
                                    level.dimension())), new S2CMessageAddScreen(entity, present));
                    DataCableService.refreshRestoredScreen(level, entity, snapshot.side);
                    list.remove(snapshot);
                    continue;
                }
                entity.removeScreen(snapshot.side);
            }
            CompoundTag tag = entity.getUpdateTag();
            ListTag screens = tag.getList("WDScreens", 10);
            screens.add(snapshot.data.copy());
            tag.put("WDScreens", screens);
            entity.load(tag);
            ScreenData recovered = entity.getScreen(snapshot.side);
            if (recovered == null) continue;
            for (int i = 0; i < entity.screenCount(); i++) {
                ScreenData screen = entity.getScreen(i);
                if (screen.redstoneStatus == null) screen.setupRedstoneStatus(level, origin);
            }
            entity.setChanged();
            WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() ->
                    new PacketDistributor.TargetPoint(origin.getX(), origin.getY(), origin.getZ(), 64, level.dimension())),
                    new S2CMessageAddScreen(entity, recovered));
            DataCableService.refreshRestoredScreen(level, entity, snapshot.side);
            list.remove(snapshot);
        }
    }

    private static boolean complete(Level level, Snapshot snapshot, ScreenData data) {
        ScreenIterator iterator = new ScreenIterator(snapshot.origin, snapshot.side, data.size);
        while (iterator.hasNext())
            if (level.getBlockState(iterator.next()).getBlock() != snapshot.block) return false;
        return true;
    }

    private static boolean contains(BlockPos origin, ScreenData data, BlockPos target) {
        ScreenIterator iterator = new ScreenIterator(origin, data.side, data.size);
        while (iterator.hasNext()) if (iterator.next().equals(target)) return true;
        return false;
    }

    private record Snapshot(BlockPos origin, BlockSide side, Block block, CompoundTag data, long time) { }
}
