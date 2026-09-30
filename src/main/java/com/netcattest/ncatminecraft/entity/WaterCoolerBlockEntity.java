package com.netcattest.ncatminecraft.entity;

import com.netcattest.ncatminecraft.block.WaterCoolerBlock;
import com.netcattest.ncatminecraft.item.ItemGallon;
import com.netcattest.ncatminecraft.registry.ItemRegistry;
import com.netcattest.ncatminecraft.registry.TileRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class WaterCoolerBlockEntity extends BlockEntity {
    public static final int DRIP_WATER = 16;
    public static final int CUP_NONE = 0;
    public static final int CUP_EMPTY = 1;
    public static final int CUP_FULL = 2;
    public static final int FILL_TIME = 32;
    public static final int CUP_COST = 8;
    private static final int STREAM_INTERVAL = 10;
    private static final int DRIP_INTERVAL = 18;

    private boolean gallon;
    private int water;
    private boolean hot;
    private boolean cold;
    private int cup = CUP_NONE;
    private int cupSide = -1;
    private boolean filling;
    private boolean fillHot;
    private int fillTicks;
    private int flowTicks;
    private boolean clientReady;
    private float hotAngle;
    private float prevHotAngle;
    private float coldAngle;
    private float prevColdAngle;
    private float shown = 1F;
    private float prevShown = 1F;
    private float cupShown;
    private float prevCupShown;

    public WaterCoolerBlockEntity(BlockPos pos, BlockState state) {
        super(TileRegistry.WATER_COOLER.get(), pos, state);
    }

    public boolean hasGallon() {
        return gallon;
    }

    public int water() {
        return water;
    }

    public boolean hotOpen() {
        return hot;
    }

    public boolean coldOpen() {
        return cold;
    }

    public void install(int amount) {
        gallon = true;
        water = Mth.clamp(amount, 0, ItemGallon.MAX_WATER);
        hot = false;
        cold = false;
        filling = false;
        fillTicks = 0;
        flowTicks = 0;
        sync();
    }

    public ItemStack removeGallon() {
        ItemStack stack = ItemGallon.withWater(water);
        gallon = false;
        water = 0;
        hot = false;
        cold = false;
        filling = false;
        fillTicks = 0;
        flowTicks = 0;
        sync();
        return stack;
    }

    public void refill() {
        water = ItemGallon.MAX_WATER;
        sync();
    }

    public void toggleHot() {
        hot = !hot;
        sync();
    }

    public void toggleCold() {
        cold = !cold;
        sync();
    }

    public int cup() {
        return cup;
    }

    public int cupSide() {
        return cupSide;
    }

    public double cupX() {
        return cupSide > 0 ? 9.9D : 6.1D;
    }

    public boolean hasCup() {
        return cup != CUP_NONE;
    }

    public boolean filling() {
        return filling;
    }

    public boolean fillHot() {
        return fillHot;
    }

    public float cupLevel(float partial) {
        return Mth.lerp(partial, prevCupShown, cupShown);
    }

    public void placeCup(int side) {
        cup = CUP_EMPTY;
        cupSide = side > 0 ? 1 : -1;
        filling = false;
        fillTicks = 0;
        if (cupSide > 0)
            hot = false;
        else
            cold = false;
        sync();
    }

    public boolean startFill(boolean hotTap) {
        if (cup != CUP_EMPTY || !gallon || water < CUP_COST || filling)
            return false;
        filling = true;
        fillHot = hotTap;
        fillTicks = 0;
        if (hotTap)
            hot = true;
        else
            cold = true;
        if (hotTap)
            cold = false;
        else
            hot = false;
        sync();
        return true;
    }

    public ItemStack takeCup() {
        ItemStack stack = ItemStack.EMPTY;
        if (cup == CUP_FULL)
            stack = new ItemStack(ItemRegistry.PAPER_CUP_WATER.get());
        else if (cup == CUP_EMPTY)
            stack = new ItemStack(ItemRegistry.PAPER_CUP.get());
        cup = CUP_NONE;
        filling = false;
        fillTicks = 0;
        sync();
        return stack;
    }

    public float hotAngle(float partial) {
        return Mth.lerp(partial, prevHotAngle, hotAngle);
    }

    public float coldAngle(float partial) {
        return Mth.lerp(partial, prevColdAngle, coldAngle);
    }

    public float shownFill(float partial) {
        return Mth.lerp(partial, prevShown, shown);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WaterCoolerBlockEntity be) {
        BlockPos above = pos.above();
        if (level.getBlockState(above).canBeReplaced())
            level.setBlock(above, state.setValue(WaterCoolerBlock.HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        if (be.filling) {
            be.tickFill(level, pos, state);
            return;
        }
        if (be.cup != CUP_NONE && !(be.hot && be.cupSide < 0) && !(be.cold && be.cupSide > 0))
            return;
        int taps = (be.hot ? 1 : 0) + (be.cold ? 1 : 0);
        if (!be.gallon || taps == 0 || be.water <= 0)
            return;
        be.flowTicks++;
        boolean dripping = be.water <= DRIP_WATER;
        int interval = dripping ? DRIP_INTERVAL : STREAM_INTERVAL;
        if (be.flowTicks < interval)
            return;
        be.flowTicks = 0;
        int drain = dripping ? 1 : Math.min(be.water, taps);
        be.water = Math.max(0, be.water - drain);
        be.sync();
        if (!(level instanceof net.minecraft.server.level.ServerLevel server))
            return;
        Direction facing = state.getValue(WaterCoolerBlock.FACING);
        if (be.hot)
            be.pour(server, pos, facing, 9.9D, dripping);
        if (be.cold)
            be.pour(server, pos, facing, 6.1D, dripping);
        if (be.water == 0)
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.STONE_BUTTON_CLICK_OFF,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.35F, 1.4F);
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, WaterCoolerBlockEntity be) {
        if (!be.clientReady) {
            be.snap();
            be.clientReady = true;
            return;
        }
        be.prevHotAngle = be.hotAngle;
        be.prevColdAngle = be.coldAngle;
        be.prevShown = be.shown;
        be.prevCupShown = be.cupShown;
        be.hotAngle = approach(be.hotAngle, be.hot ? -48F : 0F);
        be.coldAngle = approach(be.coldAngle, be.cold ? -48F : 0F);
        float cupTarget = be.cup == CUP_FULL ? 1F : be.filling ? be.fillTicks / (float) FILL_TIME : 0F;
        be.cupShown += (cupTarget - be.cupShown) * 0.35F;
        float target = be.gallon ? be.water / (float) ItemGallon.MAX_WATER : 0F;
        be.shown += (target - be.shown) * 0.18F;
        if (Math.abs(target - be.shown) < 0.004F)
            be.shown = target;
    }

    private void tickFill(Level level, BlockPos pos, BlockState state) {
        fillTicks++;
        boolean done = fillTicks >= FILL_TIME || water <= 0;
        if (fillTicks % 4 == 0 && water > 0) {
            water--;
            sync();
        }
        if (fillTicks % 2 == 0 && level instanceof net.minecraft.server.level.ServerLevel server) {
            Direction facing = state.getValue(WaterCoolerBlock.FACING);
            pour(server, pos, facing, fillHot ? 9.9D : 6.1D, water <= DRIP_WATER);
        }
        if (!done)
            return;
        filling = false;
        hot = false;
        cold = false;
        cup = fillTicks > 4 ? CUP_FULL : CUP_EMPTY;
        sync();
    }

    private void pour(net.minecraft.server.level.ServerLevel level, BlockPos pos, Direction facing, double x, boolean dripping) {
        Vec3 spout = modelToWorld(pos, facing, x, 9.08D, 3.15D);
        Vec3 tray = modelToWorld(pos, facing, x,
                hasCup() && (cupSide > 0 ? x > 8.0D : x < 8.0D) ? 8.25D : 5.95D, 3.2D);
        if (dripping) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.DRIPPING_WATER, spout.x, spout.y, spout.z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.POINTED_DRIPSTONE_DRIP_WATER,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.35F, 1.15F);
            return;
        }
        for (int i = 0; i < 4; i++) {
            double t = i / 3.0D;
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.FALLING_WATER,
                    Mth.lerp(t, spout.x, tray.x), Mth.lerp(t, spout.y, tray.y), Mth.lerp(t, spout.z, tray.z),
                    1, 0.01D, 0.02D, 0.01D, 0.0D);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, tray.x, tray.y, tray.z, 2, 0.04D, 0.01D, 0.04D, 0.0D);
        if (level.getGameTime() % 24L == 0L)
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.WATER_AMBIENT,
                    net.minecraft.sounds.SoundSource.BLOCKS, 0.22F, 1.35F);
    }

    public static Vec3 modelToWorld(BlockPos pos, Direction facing, double mx, double my, double mz) {
        double rx = mx / 16.0D - 0.5D;
        double rz = mz / 16.0D - 0.5D;
        double wx;
        double wz;
        switch (facing) {
            case EAST -> {
                wx = -rz;
                wz = rx;
            }
            case SOUTH -> {
                wx = -rx;
                wz = -rz;
            }
            case WEST -> {
                wx = rz;
                wz = -rx;
            }
            default -> {
                wx = rx;
                wz = rz;
            }
        }
        return new Vec3(pos.getX() + 0.5D + wx, pos.getY() + my / 16.0D, pos.getZ() + 0.5D + wz);
    }

    private void snap() {
        hotAngle = prevHotAngle = hot ? -48F : 0F;
        coldAngle = prevColdAngle = cold ? -48F : 0F;
        shown = prevShown = gallon ? water / (float) ItemGallon.MAX_WATER : 0F;
        cupShown = prevCupShown = cup == CUP_FULL ? 1F : 0F;
    }

    private static float approach(float current, float target) {
        float next = current + (target - current) * 0.38F;
        return Math.abs(target - next) < 0.4F ? target : next;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        gallon = tag.getBoolean("Gallon");
        water = tag.getInt("Water");
        hot = tag.getBoolean("Hot");
        cold = tag.getBoolean("Cold");
        cup = tag.getInt("Cup");
        cupSide = tag.getInt("CupSide") > 0 ? 1 : -1;
        filling = tag.getBoolean("Filling");
        fillHot = tag.getBoolean("FillHot");
        fillTicks = tag.getInt("Fill");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("Gallon", gallon);
        tag.putInt("Water", water);
        tag.putBoolean("Hot", hot);
        tag.putBoolean("Cold", cold);
        tag.putInt("Cup", cup);
        tag.putInt("CupSide", cupSide);
        tag.putBoolean("Filling", filling);
        tag.putBoolean("FillHot", fillHot);
        tag.putInt("Fill", fillTicks);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null)
            load(packet.getTag());
    }
}
