package com.netcattest.ncatminecraft.net.client_bound;

import com.netcattest.ncatminecraft.client.ClientSerialAnimation;
import com.netcattest.ncatminecraft.net.Packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public final class S2CMessageSerialAnimation extends Packet {
    private final int phase;

    public S2CMessageSerialAnimation(int phase) {
        this.phase = phase;
    }

    public S2CMessageSerialAnimation(FriendlyByteBuf buffer) {
        phase = buffer.readUnsignedByte();
    }

    @Override
    public void write(FriendlyByteBuf buffer) {
        buffer.writeByte(phase);
    }

    @Override
    public void handle(NetworkEvent.Context context) {
        if (!checkClient(context)) return;
        context.enqueueWork(() -> ClientSerialAnimation.start(phase));
        context.setPacketHandled(true);
    }
}
