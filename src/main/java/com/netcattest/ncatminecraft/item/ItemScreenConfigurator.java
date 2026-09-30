/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.item;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.netcattest.ncatminecraft.block.ScreenBlock;
import com.netcattest.ncatminecraft.data.ScreenConfigData;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.Multiblock;
import com.netcattest.ncatminecraft.utilities.serialization.Util;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;
import org.jetbrains.annotations.NotNull;

public class ItemScreenConfigurator extends Item implements WDItem {
    public ItemScreenConfigurator(Properties properties) {
        super(properties
                        .stacksTo(1)
        );
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer().isShiftKeyDown() || !(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof ScreenBlock))
            return InteractionResult.PASS;

        if (context.getLevel().isClientSide)
            return InteractionResult.SUCCESS;

        Vector3i origin = new Vector3i(context.getClickedPos());
        BlockSide side = BlockSide.values()[context.getClickedFace().ordinal()];

        Multiblock.findOrigin(context.getLevel(), origin, side, null);
        BlockEntity te = context.getLevel().getBlockEntity(origin.toBlock());

        if (te == null || !(te instanceof ScreenBlockEntity)) {
            Util.toast(context.getPlayer(), "turnOn");
            return InteractionResult.SUCCESS;
        }

        ScreenData scr = ((ScreenBlockEntity) te).getScreen(side);
        if(scr == null)
            Util.toast(context.getPlayer(), "turnOn");
        else
            (new ScreenConfigData(origin, side, scr)).sendTo((ServerPlayer) context.getPlayer());

        return InteractionResult.SUCCESS;
    }

    @Override
    public String getWikiName(@NotNull ItemStack is) {
        return is.getItem().getName(is).getString();
    }
}
