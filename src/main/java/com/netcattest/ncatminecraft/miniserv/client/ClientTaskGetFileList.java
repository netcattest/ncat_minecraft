/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.miniserv.client;

import com.netcattest.ncatminecraft.miniserv.OutgoingPacket;
import com.netcattest.ncatminecraft.miniserv.PacketID;

import java.util.UUID;

public class ClientTaskGetFileList extends ClientTask<ClientTaskGetFileList> {

    private final UUID user;
    private String[] files;

    public ClientTaskGetFileList(UUID user) {
        this.user = user;
        runCallbackOnMcThread = true;
    }

    @Override
    public void start() {
        OutgoingPacket pkt = new OutgoingPacket();
        pkt.writeByte(PacketID.LIST.ordinal());
        pkt.writeLong(user.getMostSignificantBits());
        pkt.writeLong(user.getLeastSignificantBits());
        client.sendPacket(pkt);
    }

    @Override
    public void abort() {
    }

    public void onFileList(String[] files) {
        this.files = files;
        client.nextTask();
    }

    public String[] getFileList() {
        return files;
    }

}
