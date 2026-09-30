package com.netcattest.ncatminecraft.utilities;

import net.minecraft.client.Minecraft;
import com.netcattest.ncatminecraft.client.ClientProxy;

public class DistSafety {
    public static ClientProxy createProxy() {
        return new ClientProxy();
    }

    public static boolean isConnected() {
        if (Minecraft.getInstance().getConnection() == null) return false;
        if (Minecraft.getInstance().getConnection().getConnection().isConnecting()) return false;
        return Minecraft.getInstance().getConnection().getConnection().isConnected();
    }
}
