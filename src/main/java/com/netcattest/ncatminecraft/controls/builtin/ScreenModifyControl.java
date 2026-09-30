package com.netcattest.ncatminecraft.controls.builtin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import com.netcattest.ncatminecraft.controls.ScreenControl;
import com.netcattest.ncatminecraft.core.MissingPermissionException;
import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.data.Rotation;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;

import java.util.function.Function;

public class ScreenModifyControl extends ScreenControl {
	public static final ResourceLocation id = new ResourceLocation("ncat_minecraft:mod_screen");
	
	public enum ControlType {
		RESOLUTION, ROTATION
	}
	
	ControlType type;
	Vector2i res;
	Rotation rotation;
	
	public ScreenModifyControl(Vector2i res) {
		super(id);
		this.type = ControlType.RESOLUTION;
		this.res = res;
	}
	
	public ScreenModifyControl(Rotation rotation) {
		super(id);
		this.type = ControlType.ROTATION;
		this.rotation = rotation;
	}
	
	public ScreenModifyControl(FriendlyByteBuf buf) {
		super(id);
		type = ControlType.values()[buf.readByte()];
		if (type.equals(ControlType.RESOLUTION))
			res = new Vector2i(buf);
		else rotation = Rotation.values()[buf.readByte()];
	}
	
	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeByte(type.ordinal());
		if (res != null) res.writeTo(buf);
		else if (rotation != null) buf.writeByte(rotation.ordinal());
	}
	
	@Override
	public void handleServer(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx, Function<Integer, Boolean> permissionChecker) throws MissingPermissionException {
		checkPerms(ScreenRights.MODIFY_SCREEN, permissionChecker, ctx.getSender());
		switch (type) {
			case RESOLUTION -> tes.setResolution(side, res);
			case ROTATION -> tes.setRotation(side, rotation);
		}
	}
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public void handleClient(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx) {
		switch (type) {
			case RESOLUTION -> tes.setResolution(side, res);
			case ROTATION -> tes.setRotation(side, rotation);
		}
	}
}
