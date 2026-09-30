package com.netcattest.ncatminecraft.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.ViewportEvent;

public final class ClientSerialAnimation {
    private static long startedAt;
    private static int phase;

    private ClientSerialAnimation() { }

    public static void start(int step) {
        startedAt = System.nanoTime();
        phase = step;
    }

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (startedAt == 0L || Minecraft.getInstance().player == null ||
                !Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
        double elapsed = (System.nanoTime() - startedAt) / 1_000_000_000.0D;
        if (elapsed >= .7D) {
            startedAt = 0L;
            return;
        }
        double envelope = Math.sin(Math.PI * elapsed / .7D);
        double pulse = Math.sin(Math.PI * Math.min(1D, elapsed / .24D));
        float direction = phase == 0 ? -1F : 1F;
        event.setPitch((float) (event.getPitch() + 1.4D * envelope));
        event.setYaw((float) (event.getYaw() + direction * .9D * envelope));
        event.setRoll((float) (event.getRoll() + direction * 1.8D * envelope + .5D * pulse));
    }
}
