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
import com.netcattest.ncatminecraft.utilities.math.Vector3i;

import java.util.function.Function;

public class SetURLControl extends ScreenControl {
	public static final ResourceLocation id = new ResourceLocation("ncat_minecraft:set_url");
	
	String url;
	Vector3i remoteLocation;
	
	public SetURLControl(String url, Vector3i remoteLocation) {
		super(id);
		this.url = url;
		this.remoteLocation = remoteLocation;
	}
	
	public SetURLControl(FriendlyByteBuf buf) {
		super(id);
		url = buf.readUtf();
		if (buf.readBoolean()) remoteLocation = new Vector3i(buf);
	}
	
	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeUtf(url);
		buf.writeBoolean(remoteLocation != null);
		if (remoteLocation != null) remoteLocation.writeTo(buf);
	}
	
	@Override
	public void handleServer(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx, Function<Integer, Boolean> permissionChecker) throws MissingPermissionException {
		checkPerms(ScreenRights.CHANGE_URL, permissionChecker, ctx.getSender());
		try {
			tes.setScreenURL(side, url);
		} catch (Throwable err) {
			err.printStackTrace();
		}
	}
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public void handleClient(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx) {
		try {
			tes.setScreenURL(side, url);
		} catch (Throwable err) {
			err.printStackTrace();
		}
	}
}
