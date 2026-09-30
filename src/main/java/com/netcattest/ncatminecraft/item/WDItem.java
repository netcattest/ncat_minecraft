/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.ItemStack;
import com.netcattest.ncatminecraft.NcatMinecraft;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public interface WDItem {
    static void addInformation(@Nullable List<String> tt) {
        if (tt != null && NcatMinecraft.PROXY.isShiftDown())
            tt.add(ChatFormatting.GRAY + I18n.get("item.ncat_minecraft.wiki"));
    }

    String getWikiName(@Nonnull ItemStack is);
}
