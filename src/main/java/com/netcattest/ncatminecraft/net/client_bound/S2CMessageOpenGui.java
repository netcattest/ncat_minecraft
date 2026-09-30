/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.net.client_bound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.data.GuiData;
import com.netcattest.ncatminecraft.net.Packet;
import com.netcattest.ncatminecraft.utilities.Log;

public class S2CMessageOpenGui extends Packet {
	private GuiData data;
	
	public S2CMessageOpenGui(GuiData data) {
		this.data = data;
	}
	
	public S2CMessageOpenGui(FriendlyByteBuf buf) {
		super(buf);
		
		String name = buf.readUtf();
		data = GuiData.read(name, buf);
		Class<? extends GuiData> cls = GuiData.classOf(name);
		
		if (cls == null) {
			Log.error("Could not create GuiData of type %s because it doesn't exist!", name);
		}
	}
	
	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeUtf(data.getName());
		data.serialize(buf);
	}
	
	public void handle(NetworkEvent.Context context) {
		if (checkClient(context)) {
			context.enqueueWork(() -> NcatMinecraft.PROXY.displayGui(data));
			context.setPacketHandled(true);
		}
	}
}
