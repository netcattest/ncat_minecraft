/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.net.client_bound;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.controls.ScreenControl;
import com.netcattest.ncatminecraft.controls.ScreenControlRegistry;
import com.netcattest.ncatminecraft.controls.builtin.*;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.net.BufferUtils;
import com.netcattest.ncatminecraft.net.Packet;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.data.Rotation;
import com.netcattest.ncatminecraft.utilities.serialization.NameUUIDPair;

public class S2CMessageScreenUpdate extends Packet  {
    ScreenControl control;
    BlockPos pos;
    BlockSide side;
    
    public S2CMessageScreenUpdate(BlockPos blockPos, BlockSide side) {
        this.pos = blockPos;
        this.side = side;
    }
    
    public S2CMessageScreenUpdate(FriendlyByteBuf buf) {
        super(buf);
    
        pos = buf.readBlockPos();
        side = (BlockSide) BufferUtils.readEnum(buf, (i) -> BlockSide.values()[i], (byte) 1);
    
        this.control = ScreenControlRegistry.parse(buf);
    }
    
    public static S2CMessageScreenUpdate setURL(ScreenBlockEntity screen, BlockSide side, String weburl) {
        S2CMessageScreenUpdate screenUpdate = new S2CMessageScreenUpdate(screen.getBlockPos(), side);
        screenUpdate.control = new SetURLControl(weburl, new Vector3i(screenUpdate.pos));
        return screenUpdate;
    }
    
    public static S2CMessageScreenUpdate setResolution(ScreenBlockEntity screen, BlockSide side, Vector2i res) {
        S2CMessageScreenUpdate screenUpdate = new S2CMessageScreenUpdate(screen.getBlockPos(), side);
        screenUpdate.control = new ScreenModifyControl(res);
        return screenUpdate;
    }
    
    public static S2CMessageScreenUpdate rotation(ScreenBlockEntity screen, BlockSide side, Rotation rot) {
        S2CMessageScreenUpdate screenUpdate = new S2CMessageScreenUpdate(screen.getBlockPos(), side);
        screenUpdate.control = new ScreenModifyControl(rot);
        return screenUpdate;
    }
    
    public static S2CMessageScreenUpdate upgrade(ScreenBlockEntity screen, BlockSide side, boolean adding, ItemStack stack) {
        S2CMessageScreenUpdate screenUpdate = new S2CMessageScreenUpdate(screen.getBlockPos(), side);
        screenUpdate.control = new ManageRightsAndUpdgradesControl(adding, stack);
        return screenUpdate;
    }
    
    public static S2CMessageScreenUpdate click(ScreenBlockEntity screen, BlockSide side, ClickControl.ControlType mouseMove, Vector2i pos) {
        return click(screen, side, mouseMove, pos, mouseMove == ClickControl.ControlType.MOVE ? -1 : 0);
    }

    public static S2CMessageScreenUpdate click(ScreenBlockEntity screen, BlockSide side, ClickControl.ControlType mouseMove, Vector2i pos, int button) {
        S2CMessageScreenUpdate screenUpdate = new S2CMessageScreenUpdate(screen.getBlockPos(), side);
        screenUpdate.control = new ClickControl(mouseMove, pos, button);
        return screenUpdate;
    }
    
    public static S2CMessageScreenUpdate type(ScreenBlockEntity screen, BlockSide side, String text) {
        S2CMessageScreenUpdate screenUpdate = new S2CMessageScreenUpdate(screen.getBlockPos(), side);
        screenUpdate.control = new KeyTypedControl(text, screenUpdate.pos);
        return screenUpdate;
    }
    
    public static S2CMessageScreenUpdate autoVolume(ScreenBlockEntity screen, BlockSide side, boolean av) {
        S2CMessageScreenUpdate screenUpdate = new S2CMessageScreenUpdate(screen.getBlockPos(), side);
        screenUpdate.control = new AutoVolumeControl(av);
        return screenUpdate;
    }
    
    public static S2CMessageScreenUpdate owner(ScreenBlockEntity screen, BlockSide side, NameUUIDPair owner) {
        S2CMessageScreenUpdate screenUpdate = new S2CMessageScreenUpdate(screen.getBlockPos(), side);
        screenUpdate.control = new OwnerControl(owner);
        return screenUpdate;
    }

    public static S2CMessageScreenUpdate turnOff(BlockPos blockPos, BlockSide side) {
        S2CMessageScreenUpdate screenUpdate = new S2CMessageScreenUpdate(blockPos, side);
        screenUpdate.control = TurnOffControl.INSTANCE;
        return screenUpdate;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        BufferUtils.writeEnum(buf, side, (byte) 1);
    
        buf.writeUtf(control.getId().toString());
        control.write(buf);
    }
    
    public void handle(NetworkEvent.Context ctx) {
        if (checkClient(ctx)) {
            ctx.enqueueWork(() -> {
                Level level = (Level) NcatMinecraft.PROXY.getWorld(ctx);
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof ScreenBlockEntity tes) {
                    if (BlockRegistry.isLocalScreen(tes.getBlockState().getBlock()) &&
                            !BlockRegistry.isWorkstationScreen(tes.getBlockState().getBlock()) &&
                            (control instanceof KeyTypedControl || control instanceof ClickControl || control instanceof LaserControl))
                        return;
                    control.handleClient(pos, side, tes, ctx);
                }
            });
            ctx.setPacketHandled(true);
            
        }
    }
}
