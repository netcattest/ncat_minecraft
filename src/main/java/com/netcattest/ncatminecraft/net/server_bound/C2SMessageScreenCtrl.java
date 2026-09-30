/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.net.server_bound;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.controls.ScreenControl;
import com.netcattest.ncatminecraft.controls.ScreenControlRegistry;
import com.netcattest.ncatminecraft.controls.builtin.*;
import com.netcattest.ncatminecraft.core.JSServerRequest;
import com.netcattest.ncatminecraft.core.MissingPermissionException;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.net.BufferUtils;
import com.netcattest.ncatminecraft.net.Packet;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.data.Rotation;
import com.netcattest.ncatminecraft.utilities.serialization.NameUUIDPair;

public class C2SMessageScreenCtrl extends Packet {
	@Deprecated(forRemoval = true)
	public static final int CTRL_LASER_MOVE = 0;
	@Deprecated(forRemoval = true)
	public static final int CTRL_LASER_UP = 0;
	@Deprecated(forRemoval = true)
	public static final int CTRL_LASER_DOWN = 0;
	@Deprecated(forRemoval = true)
	public static final int CTRL_SET_RESOLUTION = 0;
	
	ScreenControl control;
	BlockPos pos;
	BlockSide side;
	
	public C2SMessageScreenCtrl() {
	}
	
	public C2SMessageScreenCtrl(ScreenBlockEntity screen, BlockSide side, ScreenControl control) {
		this.pos = screen.getBlockPos();
		this.side = side;
		this.control = control;
	}
	
	protected static C2SMessageScreenCtrl base(ScreenBlockEntity screen, BlockSide side) {
		C2SMessageScreenCtrl packet = new C2SMessageScreenCtrl();
		packet.pos = screen.getBlockPos();
		packet.side = side;
		return packet;
	}
	
	@Deprecated(forRemoval = true)
	public static C2SMessageScreenCtrl setURL(ScreenBlockEntity tes, BlockSide side, String url, Vector3i remoteLocation) {
		C2SMessageScreenCtrl ret = base(tes, side);
		ret.control = new SetURLControl(url, remoteLocation);
		return ret;
	}
	
	@Deprecated(forRemoval = true)
	public C2SMessageScreenCtrl(ScreenBlockEntity tes, BlockSide side, NameUUIDPair friend, boolean del) {
		this(tes, side, new ModifyFriendListControl(friend, !del));
	}
	
	@Deprecated(forRemoval = true)
	public C2SMessageScreenCtrl(ScreenBlockEntity tes, BlockSide side, int fr, int or) {
		this(tes, side, new ManageRightsAndUpdgradesControl(fr, or));
	}
	
	@Deprecated(forRemoval = true)
	public C2SMessageScreenCtrl(ScreenBlockEntity tes, BlockSide side, ItemStack toRem) {
		this(tes, side, new ManageRightsAndUpdgradesControl(false, toRem));
	}
	
	@Deprecated(forRemoval = true)
	public C2SMessageScreenCtrl(ScreenBlockEntity tes, BlockSide side, Rotation rot) {
		this(tes, side, new ScreenModifyControl(rot));
	}
	
	@Deprecated(forRemoval = true)
	public static C2SMessageScreenCtrl vec2(ScreenBlockEntity tes, BlockSide side, int ctrl, Vector2i vec) {
		throw new RuntimeException("Moved: look into ScreenControlRegistry");
	}
	
	@Deprecated(forRemoval = true)
	public static C2SMessageScreenCtrl resolution(ScreenBlockEntity tes, BlockSide side, Vector2i vec) {
		C2SMessageScreenCtrl ret = base(tes, side);
		ret.control = new ScreenModifyControl(vec);
		return ret;
	}
	
	@Deprecated(forRemoval = true)
	public static C2SMessageScreenCtrl type(ScreenBlockEntity tes, BlockSide side, String text, BlockPos soundPos) {
		C2SMessageScreenCtrl ret = base(tes, side);
		ret.control = new KeyTypedControl(text, soundPos);
		return ret;
	}
	
	public static C2SMessageScreenCtrl laserMove(ScreenBlockEntity tes, BlockSide side, Vector2i vec) {
		C2SMessageScreenCtrl ret = base(tes, side);
		ret.control = new LaserControl(LaserControl.ControlType.MOVE, vec);
		return ret;
	}
	
	public static C2SMessageScreenCtrl laserDown(ScreenBlockEntity tes, BlockSide side, Vector2i vec, int button) {
		C2SMessageScreenCtrl ret = base(tes, side);
		ret.control = new LaserControl(LaserControl.ControlType.DOWN, vec, button);
		return ret;
	}
	
	@Deprecated(forRemoval = true)
	public static C2SMessageScreenCtrl laserUp(ScreenBlockEntity tes, BlockSide side, int button) {
		C2SMessageScreenCtrl ret = base(tes, side);
		ret.control = new LaserControl(LaserControl.ControlType.UP, null, button);
		return ret;
	}
	
	@Deprecated(forRemoval = true)
	public static C2SMessageScreenCtrl jsRequest(ScreenBlockEntity tes, BlockSide side, int reqId, JSServerRequest reqType, Object... data) {
		C2SMessageScreenCtrl ret = base(tes, side);
		ret.control = new JSRequestControl(reqId, reqType, data);
		return ret;
	}
	
	@Deprecated(forRemoval = true)
	public static C2SMessageScreenCtrl autoVol(ScreenBlockEntity tes, BlockSide side, boolean av) {
		C2SMessageScreenCtrl ret = base(tes, side);
		ret.control = new AutoVolumeControl(av);
		return ret;
	}
	
	public C2SMessageScreenCtrl(FriendlyByteBuf buf) {
		super(buf);
		
		pos = buf.readBlockPos();
		side = (BlockSide) BufferUtils.readEnum(buf, (i) -> BlockSide.values()[i], (byte) 1);
		
		this.control = ScreenControlRegistry.parse(buf);
	}
	
	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeBlockPos(pos);
		BufferUtils.writeEnum(buf, side, (byte) 1);
		
		buf.writeUtf(control.getId().toString());
		control.write(buf);
	}
	
	public void checkPermission(ServerPlayer sender, ScreenBlockEntity scr, int right) throws MissingPermissionException {
		int prights = scr.getScreen(side).rightsFor(sender);
		if ((prights & right) == 0)
			throw new MissingPermissionException(right, sender);
	}
	
	public void handle(NetworkEvent.Context ctx) {
		if (checkServer(ctx)) {
			ctx.enqueueWork(() -> {
				try {
					Level level = (Level) NcatMinecraft.PROXY.getWorld(ctx);
					BlockEntity be = level.getBlockEntity(pos);
					if (be instanceof ScreenBlockEntity tes) {
						if (BlockRegistry.isLocalScreen(tes.getBlockState().getBlock()) &&
								!BlockRegistry.isWorkstationScreen(tes.getBlockState().getBlock()) &&
								(control instanceof KeyTypedControl || control instanceof ClickControl || control instanceof LaserControl))
							return;
						control.handleServer(pos, side, tes, ctx, (perm) -> {
							try {
								checkPermission(ctx.getSender(), tes, perm);
								return true;
							} catch (Throwable ignored) {
								return false;
							}
						});
					}
				} catch (MissingPermissionException e) {
					e.printStackTrace();
				} catch (Throwable ignored) {
				}
			});
			ctx.setPacketHandled(true);
		}
	}
}
