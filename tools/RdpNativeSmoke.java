package com.netcattest.ncatminecraft.client.remote;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class RdpNativeSmoke {
    public static void main(String[] args) throws Exception {
        if (!RdpNative.isAvailable()) {
            throw new IllegalStateException(RdpNative.unavailableReason());
        }
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        try (RdpNative.Session session = RdpNative.Session.open("127.0.0.1", 1, "", "smoke",
                new char[0], 400, 300, new RdpNative.Listener() {
                    @Override
                    public void onFrame(int x, int y, int width, int height, int[] argb) {
                    }

                    @Override
                    public void onResize(int width, int height) {
                    }

                    @Override
                    public void onStatus(String code) {
                        if (code.startsWith("error:")) {
                            result.set(code);
                            finished.countDown();
                        }
                    }

                    @Override
                    public boolean onCertificate(String fingerprint, boolean changed) {
                        return false;
                    }
                })) {
            if (!finished.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("RDP connection timeout");
            }
        }
        System.out.println("Native RDP loaded and connection attempt reached socket: " + result.get());
    }
}
