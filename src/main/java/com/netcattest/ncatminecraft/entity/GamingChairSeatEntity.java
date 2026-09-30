package com.netcattest.ncatminecraft.entity;

import com.netcattest.ncatminecraft.block.GamingChairBlock;
import com.netcattest.ncatminecraft.block.ToiletBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.network.NetworkHooks;

public final class GamingChairSeatEntity extends Entity {
    public GamingChairSeatEntity(EntityType<? extends GamingChairSeatEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 2) {
            BlockState chair = level().getBlockState(blockPosition());
            if (!isSeat(chair) || getPassengers().isEmpty())
                discard();
        }
    }

    private static boolean isSeat(BlockState state) {
        if (state.getBlock() instanceof GamingChairBlock)
            return state.getValue(GamingChairBlock.HALF) == DoubleBlockHalf.LOWER;
        if (state.getBlock() instanceof ToiletBlock)
            return state.getValue(ToiletBlock.HALF) == DoubleBlockHalf.LOWER;
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
