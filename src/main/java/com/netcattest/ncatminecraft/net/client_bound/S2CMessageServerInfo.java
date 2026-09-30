/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.net.client_bound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.miniserv.client.Client;
import com.netcattest.ncatminecraft.net.Packet;
import com.netcattest.ncatminecraft.net.server_bound.C2SMessageMiniservConnect;

public class S2CMessageServerInfo extends Packet {
	
	private int miniservPort;
	
	public S2CMessageServerInfo(int msPort) {
		miniservPort = msPort;
	}
	
	public S2CMessageServerInfo(FriendlyByteBuf buf) {
		super(buf);
		miniservPort = buf.readShort();
	}
	
	@Override
	public void write(FriendlyByteBuf buf) {
		buf.writeShort(miniservPort);
	}
	
	@Override
	public void handle(NetworkEvent.Context ctx) {
		if (checkClient(ctx)) {
			try {
				NcatMinecraft.PROXY.setMiniservClientPort(miniservPort);
				C2SMessageMiniservConnect message = Client.getInstance().beginConnection();
				respond(ctx, message);
				ctx.setPacketHandled(true);
			} catch (Throwable err) {
				err.printStackTrace();
				throw new RuntimeException(err);
			}
		}
	}
}
