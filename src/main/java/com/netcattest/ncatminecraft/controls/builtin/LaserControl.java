package com.netcattest.ncatminecraft.controls.builtin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import com.netcattest.ncatminecraft.controls.ScreenControl;
import com.netcattest.ncatminecraft.core.MissingPermissionException;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;

import java.util.function.Function;

public class LaserControl extends ScreenControl {
	public static final ResourceLocation id = new ResourceLocation("ncat_minecraft:laser");
	
	public enum ControlType {
		MOVE, DOWN, UP
	}
	
	ControlType type;
	Vector2i coord;
	int button;
	
	public LaserControl(ControlType type, Vector2i coord) {
		this(type, coord, -1);
	}
	
	public LaserControl(ControlType type, Vector2i coord, int button) {
		super(id);
		this.type = type;
		this.coord = coord;
		this.button = button;
	}
	
	public LaserControl(FriendlyByteBuf buf) {
		super(id);
		type = ControlType.values()[buf.readByte()];
		if (!type.equals(ControlType.UP))
			coord = new Vector2i(buf);
		if (!type.equals(ControlType.MOVE))
			button = buf.readInt();
	}
	
	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeByte(type.ordinal());
		if (coord != null) coord.writeTo(buf);
		if (type != ControlType.MOVE) buf.writeInt(button);
	}
	
	@Override
	public void handleServer(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx, Function<Integer, Boolean> permissionChecker) throws MissingPermissionException {
		ServerPlayer sender = ctx.getSender();
		switch (type) {
			case UP -> tes.laserUp(side, sender, button);
			case DOWN -> tes.laserDownMove(side, sender, coord, true, button);
			case MOVE -> tes.laserDownMove(side, sender, coord, false, button);
		}
	}
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public void handleClient(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx) {
		if (coord != null)
			tes.handleMouseEvent(side, ClickControl.ControlType.MOVE, coord, -1);
		
		switch (type) {
			case UP -> tes.handleMouseEvent(side, ClickControl.ControlType.UP, coord, button);
			case DOWN -> tes.handleMouseEvent(side, ClickControl.ControlType.DOWN, coord, button);
		}
	}
}
