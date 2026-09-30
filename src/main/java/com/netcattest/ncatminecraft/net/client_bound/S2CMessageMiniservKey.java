/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.net.client_bound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.client.ClientProxy;
import com.netcattest.ncatminecraft.miniserv.client.Client;
import com.netcattest.ncatminecraft.net.BufferUtils;
import com.netcattest.ncatminecraft.net.Packet;
import com.netcattest.ncatminecraft.utilities.Log;

public class S2CMessageMiniservKey extends Packet {
	private byte[] encryptedKey;
	
	public S2CMessageMiniservKey(byte[] key) {
		encryptedKey = key;
	}
	
	public S2CMessageMiniservKey(FriendlyByteBuf buf) {
		super(buf);
		encryptedKey = BufferUtils.readBytes(buf);
	}
	
	@Override
	public void write(FriendlyByteBuf buf) {
		BufferUtils.writeBytes(buf, encryptedKey);
	}
	
	@Override
	public void handle(NetworkEvent.Context ctx) {
		if (checkClient(ctx)) {
			if (Client.getInstance().decryptKey(encryptedKey)) {
				Log.info("Successfully received and decrypted key, starting miniserv client...");
				if (NcatMinecraft.PROXY instanceof ClientProxy proxy) {
					proxy.startMiniservClient();
				}
			}
			
			ctx.setPacketHandled(true);
		}
	}
}
