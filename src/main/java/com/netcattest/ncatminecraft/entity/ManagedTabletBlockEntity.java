package com.netcattest.ncatminecraft.entity;

import com.netcattest.ncatminecraft.block.ManagedTabletBlock;
import com.netcattest.ncatminecraft.registry.TileRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import java.util.UUID;

public final class ManagedTabletBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos linkedSwitchPos;
    private String activePanel = "overview";
    private int selectedPort;
    @Nullable
    private UUID dockToken;

    public ManagedTabletBlockEntity(BlockPos pos, BlockState state) {
        super(TileRegistry.MANAGED_TABLET.get(), pos, state);
    }

    @Nullable
    public BlockPos linkedSwitchPos() {
        return linkedSwitchPos;
    }

    public void setLinkedSwitchPos(@Nullable BlockPos pos) {
        if (pos == null ? linkedSwitchPos == null : pos.equals(linkedSwitchPos)) return;
        linkedSwitchPos = pos == null ? null : pos.immutable();
        sync();
    }

    public String activePanel() {
        return activePanel;
    }

    public int selectedPort() { return selectedPort; }

    @Nullable
    public UUID dockToken() { return dockToken; }

    public UUID ensureDockToken() {
        if (dockToken == null) {
            dockToken = UUID.randomUUID();
            sync();
        }
        return dockToken;
    }

    public void setDockToken(UUID token) {
        if (token.equals(dockToken)) return;
        dockToken = token;
        sync();
    }

    public void setSelectedPort(int port) {
        if (port < 0 || port > 7 || selectedPort == port) return;
        selectedPort = port;
        sync();
    }

    public void setActivePanel(String panel) {
        if (!"overview".equals(panel) && !"ports".equals(panel) && !"policies".equals(panel)
                && !"diagnostics".equals(panel)) return;
        if (activePanel.equals(panel)) return;
        activePanel = panel;
        sync();
    }

    public Vec3 serialPortPosition() {
        double x = 15.40D / 16.0D - 0.5D;
        double z = 8.0D / 16.0D - 0.5D;
        double rotatedX;
        double rotatedZ;
        switch (getBlockState().getValue(ManagedTabletBlock.FACING)) {
            case EAST -> {
                rotatedX = -z;
                rotatedZ = x;
            }
            case SOUTH -> {
                rotatedX = -x;
                rotatedZ = -z;
            }
            case WEST -> {
                rotatedX = z;
                rotatedZ = -x;
            }
            default -> {
                rotatedX = x;
                rotatedZ = z;
            }
        }
        return new Vec3(worldPosition.getX() + 0.5D + rotatedX,
                worldPosition.getY() + 2.10D / 16.0D,
                worldPosition.getZ() + 0.5D + rotatedZ);
    }

    @Override
    public AABB getRenderBoundingBox() {
        AABB bounds = new AABB(worldPosition);
        if (linkedSwitchPos == null || worldPosition.distSqr(linkedSwitchPos) > 18D * 18D)
            return bounds;
        return bounds.minmax(new AABB(linkedSwitchPos)).inflate(2D);
    }

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        linkedSwitchPos = tag.contains("LinkedSwitch") ? BlockPos.of(tag.getLong("LinkedSwitch")) : null;
        String savedPanel = tag.getString("ActivePanel");
        activePanel = "ports".equals(savedPanel) || "policies".equals(savedPanel)
                || "diagnostics".equals(savedPanel) ? savedPanel : "overview";
        selectedPort = Math.max(0, Math.min(7, tag.getInt("SelectedPort")));
        dockToken = tag.hasUUID("DockToken") ? tag.getUUID("DockToken") : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (linkedSwitchPos != null) tag.putLong("LinkedSwitch", linkedSwitchPos.asLong());
        tag.putString("ActivePanel", activePanel);
        tag.putInt("SelectedPort", selectedPort);
        if (dockToken != null) tag.putUUID("DockToken", dockToken);
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
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) load(packet.getTag());
    }
}
