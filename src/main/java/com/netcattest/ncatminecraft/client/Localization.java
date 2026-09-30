package com.netcattest.ncatminecraft.client;

import net.minecraft.client.Minecraft;

public final class Localization {
    private Localization() {
    }

    public static boolean portuguese() {
        try {
            return Minecraft.getInstance().getLanguageManager().getSelected()
                    .toLowerCase(java.util.Locale.ROOT).startsWith("pt");
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static String t(String pt, String en) {
        return portuguese() ? pt : en;
    }
}
