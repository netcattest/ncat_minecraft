package com.netcattest.ncatminecraft.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import com.netcattest.ncatminecraft.net.client_bound.*;
import com.netcattest.ncatminecraft.net.server_bound.*;

import java.util.ArrayList;

public class WDNetworkRegistry {
	public static final String networkingVersion = "12";
	public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
			new ResourceLocation("ncat_minecraft", "packetsystem"),
			() -> networkingVersion,
			(s) -> s.equals(networkingVersion),
			(s) -> s.equals(networkingVersion)
	);
	
	public static void sendToNearExcept() {
	
	}
	
	static {
		ArrayList<NetworkEntry<?>> entries = new ArrayList<>();
		
		entries.add(new NetworkEntry<>(S2CMessageServerInfo.class, S2CMessageServerInfo::new));
		entries.add(new NetworkEntry<>(C2SMessageMiniservConnect.class, C2SMessageMiniservConnect::new));
		entries.add(new NetworkEntry<>(S2CMessageMiniservKey.class, S2CMessageMiniservKey::new));
		
		entries.add(new NetworkEntry<>(S2CMessageCloseGui.class, S2CMessageCloseGui::new));
		entries.add(new NetworkEntry<>(S2CMessageOpenGui.class, S2CMessageOpenGui::new));
		
		entries.add(new NetworkEntry<>(S2CMessageAddScreen.class, S2CMessageAddScreen::new));
		
		entries.add(new NetworkEntry<>(C2SMessageScreenCtrl.class, C2SMessageScreenCtrl::new));
		entries.add(new NetworkEntry<>(C2SMessageDataActivity.class, C2SMessageDataActivity::new));
		entries.add(new NetworkEntry<>(C2SMessageWorkstationInput.class, C2SMessageWorkstationInput::new));
		entries.add(new NetworkEntry<>(C2SMessageManagedSwitchConfig.class, C2SMessageManagedSwitchConfig::new));
		entries.add(new NetworkEntry<>(C2SMessageRackAction.class, C2SMessageRackAction::new));
		entries.add(new NetworkEntry<>(C2SMessageRackActivity.class, C2SMessageRackActivity::new));
		entries.add(new NetworkEntry<>(C2SMessageRackFlow.class, C2SMessageRackFlow::new));
		entries.add(new NetworkEntry<>(S2CMessageSerialAnimation.class, S2CMessageSerialAnimation::new));
		entries.add(new NetworkEntry<>(S2CMessageScreenUpdate.class, S2CMessageScreenUpdate::new));
		
		entries.add(new NetworkEntry<>(C2SMessageRedstoneCtrl.class, C2SMessageRedstoneCtrl::new));
		
		entries.add(new NetworkEntry<>(C2SMessageACQuery.class, C2SMessageACQuery::new));
		entries.add(new NetworkEntry<>(S2CMessageACResult.class, S2CMessageACResult::new));
		
		entries.add(new NetworkEntry<>(S2CMessageJSResponse.class, S2CMessageJSResponse::new));
		
		entries.add(new NetworkEntry<>(C2SMessageMinepadUrl.class, C2SMessageMinepadUrl::new));
		entries.add(new NetworkEntry<>(C2SMessageLogTablet.class, C2SMessageLogTablet::new));
		
		for (int i = 0; i < entries.size(); i++) entries.get(i).register(i, INSTANCE);
	}
	
	public static void init() {
	}
}
