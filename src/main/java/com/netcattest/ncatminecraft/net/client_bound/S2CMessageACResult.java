/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.net.client_bound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.net.Packet;
import com.netcattest.ncatminecraft.utilities.serialization.NameUUIDPair;

public class S2CMessageACResult extends Packet {
    private static NameUUIDPair[] result;

    public S2CMessageACResult(NameUUIDPair[] pairs) {
        result = pairs;
    }
    
    public S2CMessageACResult(FriendlyByteBuf buf) {
        super(buf);
        
        int cnt = buf.readByte();
        result = new NameUUIDPair[cnt];

        for(int i = 0; i < cnt; i++)
            result[i] = new NameUUIDPair(buf);
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeByte(result.length);

        for(NameUUIDPair pair : result)
            pair.writeTo(buf);
    }

    public void handle(NetworkEvent.Context ctx) {
        if (checkClient(ctx)) {
            ctx.enqueueWork(() -> {
                NcatMinecraft.PROXY.onAutocompleteResult(result);
            });
            ctx.setPacketHandled(true);
        }
    }
}
