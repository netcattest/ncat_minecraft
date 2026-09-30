package com.netcattest.ncatminecraft.controls;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;
import com.netcattest.ncatminecraft.core.MissingPermissionException;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;

import java.util.Objects;
import java.util.function.Function;

public abstract class ScreenControl {
	private final ResourceLocation id;
	
	public ScreenControl(ResourceLocation id) {
		this.id = id;
	}
	
	public abstract void write(FriendlyByteBuf buf);
	public abstract void handleServer(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx, Function<Integer, Boolean> permissionChecker) throws MissingPermissionException;
	@OnlyIn(Dist.CLIENT)
	public abstract void handleClient(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx);
	
	public void checkPerms(int perms, Function<Integer, Boolean> checker, ServerPlayer player) throws MissingPermissionException {
		if (!checker.apply(perms)) {
			throw new MissingPermissionException(perms, Objects.requireNonNull(player));
		}
	}
	
	public final ResourceLocation getId() {
		return id;
	}
}
