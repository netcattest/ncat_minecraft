/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.core;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public interface IUpgrade {
    void onInstall(@Nonnull ScreenBlockEntity tes, @Nonnull BlockSide screenSide, @Nullable Player player, @Nonnull ItemStack is);
    boolean onRemove(@Nonnull ScreenBlockEntity tes, @Nonnull BlockSide screenSide, @Nullable Player player, @Nonnull ItemStack is);
    boolean isSameUpgrade(@Nonnull ItemStack myStack, @Nonnull ItemStack otherStack);
    String getJSName(@Nonnull ItemStack is);
}
