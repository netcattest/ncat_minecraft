package com.netcattest.ncatminecraft.controls.builtin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import com.netcattest.ncatminecraft.controls.ScreenControl;
import com.netcattest.ncatminecraft.core.MissingPermissionException;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;

import java.util.function.Function;

public class ClickControl extends ScreenControl {
	public static final ResourceLocation id = new ResourceLocation("ncat_minecraft:click");
	
	public enum ControlType {
		CLICK, MOVE, DOWN, UP
	}
	
	ControlType type;
	Vector2i coord;
	int button;
	
	public ClickControl(ControlType type, Vector2i coord) {
		this(type, coord, type == ControlType.MOVE ? -1 : 0);
	}
	
	public ClickControl(ControlType type, Vector2i coord, int button) {
		super(id);
		this.type = type;
		this.coord = coord;
		this.button = button;
	}
	
	public ClickControl(FriendlyByteBuf buf) {
		super(id);
		type = ControlType.values()[buf.readByte()];
		coord = new Vector2i(buf);
		button = buf.readByte();
	}
	
	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeByte(type.ordinal());
		coord.writeTo(buf);
		buf.writeByte(button);
	}
	
	@Override
	public void handleServer(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx, Function<Integer, Boolean> permissionChecker) throws MissingPermissionException {
		throw new RuntimeException("Cannot call click control on server");
	}
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public void handleClient(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx) {
		if (coord != null && type != ControlType.MOVE)
			tes.handleMouseEvent(side, ClickControl.ControlType.MOVE, coord, -1);
		
		tes.handleMouseEvent(side, type, coord, button);
	}
}
