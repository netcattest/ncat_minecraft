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
import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.serialization.NameUUIDPair;

import java.util.function.Function;

public class ModifyFriendListControl extends ScreenControl {
	public static final ResourceLocation id = new ResourceLocation("ncat_minecraft:mod_friend_list");
	
	boolean adding;
	NameUUIDPair friend;
	
	public ModifyFriendListControl(NameUUIDPair pair, boolean adding) {
		super(id);
		this.adding = adding;
		this.friend = pair;
	}
	
	public ModifyFriendListControl(FriendlyByteBuf buf) {
		super(id);
		adding = buf.readBoolean();
		friend = new NameUUIDPair(buf);
	}
	
	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeBoolean(adding);
		friend.writeTo(buf);
	}
	
	@Override
	public void handleServer(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx, Function<Integer, Boolean> permissionChecker) throws MissingPermissionException {
		ServerPlayer player = ctx.getSender();
		checkPerms(ScreenRights.MANAGE_FRIEND_LIST, permissionChecker, ctx.getSender());
		if (adding) tes.addFriend(player, side, friend);
		else tes.removeFriend(player, side, friend);
	}
	
	@Override
	@OnlyIn(Dist.CLIENT)
	public void handleClient(BlockPos pos, BlockSide side, ScreenBlockEntity tes, NetworkEvent.Context ctx) {
	}
}
