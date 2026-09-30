/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.item;

import net.minecraft.world.item.ItemStack;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.core.CraftComponent;
import org.jetbrains.annotations.NotNull;

public class ItemCraftComponent extends ItemMulti implements WDItem {
    public ItemCraftComponent(Properties properties) {
        super(CraftComponent.class, properties
        );

        creativeTabItems.clear(CraftComponent.BADEXTCARD.ordinal());
    }

    @Override
    public String getWikiName(@NotNull ItemStack is) {
        return is.getItem().getName(is).getString();
    }
}
