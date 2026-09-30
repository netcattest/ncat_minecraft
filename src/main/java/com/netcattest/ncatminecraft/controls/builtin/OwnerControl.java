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
import com.netcattest.ncatminecraft.utilities.serialization.NameUUIDPair;

import java.util.function.Function;

public class OwnerControl extends ScreenControl {
	public static final ResourceLocation id = new ResourceLocation("ncat_minecraft:set_owner");
	
	NameUUIDPair owner;
	
	public OwnerControl(NameUUIDPair pair) {
		super(id);
		this.owner = pair;
	}
	
	public OwnerControl(FriendlyByteBuf buf) {
		super(id);
		owner = new NameUUIDPair(buf);
	}
	
	@Override
	public void write(FriendlyByteBuf buf) {
		owner.writeTo(buf);
	}
	
	@Override
	public void handleServer(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx, Function<Integer, Boolean> permissionChecker) throws MissingPermissionException {
		throw new RuntimeException("Cannot handle ownership theft packet from server");
	}
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public void handleClient(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx) {
		tes.getScreen(side).owner = owner;
	}
}
