/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.block.ScreenBlock;
import com.netcattest.ncatminecraft.core.IPeripheral;
import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.net.client_bound.S2CMessageAddScreen;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.Multiblock;
import com.netcattest.ncatminecraft.utilities.serialization.Util;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ItemLinker extends Item implements WDItem {
    public ItemLinker(Properties properties) {
        super(properties
                        .stacksTo(1)
        );
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide())
            return InteractionResult.SUCCESS;

        ItemStack stack = context.getPlayer().getItemInHand(context.getHand());
        CompoundTag tag = stack.getTag();

        if (tag != null) {
            if (tag.contains("ScreenX") && tag.contains("ScreenY") && tag.contains("ScreenZ") && tag.contains("ScreenSide")) {
                BlockState state = context.getLevel().getBlockState(context.getClickedPos());
                if (BlockRegistry.isLogScreen(state.getBlock()) || BlockRegistry.isDevToolsScreen(state.getBlock()) ||
                        BlockRegistry.isProxyScreen(state.getBlock()) || BlockRegistry.isSftpScreen(state.getBlock())) {
                    boolean sftpTarget = BlockRegistry.isSftpScreen(state.getBlock());
                    Vector3i sourcePos = new Vector3i(tag.getInt("ScreenX"), tag.getInt("ScreenY"), tag.getInt("ScreenZ"));
                    BlockSide sourceSide = BlockSide.fromInt(tag.getByte("ScreenSide"));
                    Vector3i logPos = new Vector3i(context.getClickedPos());
                    BlockSide logSide = BlockSide.fromInt(context.getClickedFace().ordinal());
                    Multiblock.findOrigin(context.getLevel(), logPos, logSide, null);
                    BlockEntity sourceEntity = context.getLevel().getBlockEntity(sourcePos.toBlock());
                    BlockEntity logEntity = context.getLevel().getBlockEntity(logPos.toBlock());
                    if (sourceSide != null && sourceEntity instanceof ScreenBlockEntity source &&
                            (sftpTarget ? BlockRegistry.isSshScreen(source.getBlockState().getBlock()) : BlockRegistry.isBrowserScreen(source.getBlockState().getBlock())) && source.getScreen(sourceSide) != null &&
                            (source.getScreen(sourceSide).rightsFor(context.getPlayer()) & ScreenRights.MANAGE_UPGRADES) != 0 &&
                            logEntity instanceof ScreenBlockEntity log && log.getScreen(logSide) != null &&
                            (log.getScreen(logSide).rightsFor(context.getPlayer()) & ScreenRights.MANAGE_UPGRADES) != 0) {
                        ScreenData logScreen = log.getScreen(logSide);
                        logScreen.logSourcePos = sourcePos;
                        logScreen.logSourceSide = sourceSide;
                        logScreen.logSourceViaCable = false;
                        log.setChanged();
                        WDNetworkRegistry.INSTANCE.send(PacketDistributor.NEAR.with(() ->
                                new PacketDistributor.TargetPoint(logPos.x, logPos.y, logPos.z, 64.0, context.getLevel().dimension())),
                                new S2CMessageAddScreen(log, logScreen));
                        Util.toast(context.getPlayer(), ChatFormatting.AQUA, "linked");
                    } else {
                        Util.toast(context.getPlayer(), sftpTarget ? "sftpLinkError" : "logLinkError");
                    }
                    stack.setTag(null);
                    return InteractionResult.SUCCESS;
                }
                IPeripheral target;

                if (state.getBlock() instanceof IPeripheral)
                    target = (IPeripheral) state.getBlock();
                else {
                    BlockEntity te = context.getLevel().getBlockEntity(context.getClickedPos());
                    if (te == null || !(te instanceof IPeripheral)) {
                        if (context.getPlayer().isShiftKeyDown()) {
                            Util.toast(context.getPlayer(), ChatFormatting.GOLD, "linkAbort");
                            stack.setTag(null);
                        } else
                            Util.toast(context.getPlayer(), "peripheral");

                        return InteractionResult.SUCCESS;
                    }

                    target = (IPeripheral) te;
                }

                Vector3i tePos = new Vector3i(tag.getInt("ScreenX"), tag.getInt("ScreenY"), tag.getInt("ScreenZ"));
                BlockSide scrSide = BlockSide.values()[tag.getByte("ScreenSide")];

                if (target.connect(context.getLevel(), context.getClickedPos(), state, tePos, scrSide)) {
                    Util.toast(context.getPlayer(), ChatFormatting.AQUA, "linked");

                    if (context.getPlayer() instanceof ServerPlayer)
                        NcatMinecraft.INSTANCE.criterionLinkPeripheral.trigger(((ServerPlayer) context.getPlayer()).getAdvancements());
                } else
                    Util.toast(context.getPlayer(), "linkError");

                stack.setTag(null);
                return InteractionResult.SUCCESS;
            }
        }

        if (!(context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof ScreenBlock)) {
            Util.toast(context.getPlayer(), "notAScreen");
            return InteractionResult.SUCCESS;
        }
        BlockState selectedState = context.getLevel().getBlockState(context.getClickedPos());
        if (BlockRegistry.isLogScreen(selectedState.getBlock()) || BlockRegistry.isDevToolsScreen(selectedState.getBlock()) ||
                BlockRegistry.isProxyScreen(selectedState.getBlock())) {
            Util.toast(context.getPlayer(), "logLinkError");
            return InteractionResult.SUCCESS;
        }

        Vector3i pos = new Vector3i(context.getClickedPos());
        BlockSide side = BlockSide.values()[context.getClickedFace().ordinal()];
        Multiblock.findOrigin(context.getLevel(), pos, side, null);

        BlockEntity te = context.getLevel().getBlockEntity(pos.toBlock());
        if (te == null || !(te instanceof ScreenBlockEntity)) {
            Util.toast(context.getPlayer(), "turnOn");
            return InteractionResult.SUCCESS;
        }

        ScreenData scr = ((ScreenBlockEntity) te).getScreen(side);
        if(scr == null)
            Util.toast(context.getPlayer(), "turnOn");
        else if ((scr.rightsFor(context.getPlayer()) & ScreenRights.MANAGE_UPGRADES) == 0)
            Util.toast(context.getPlayer(), "restrictions");
        else {
            tag = new CompoundTag();
            tag.putInt("ScreenX", pos.x);
            tag.putInt("ScreenY", pos.y);
            tag.putInt("ScreenZ", pos.z);
            tag.putByte("ScreenSide", (byte) side.ordinal());

            stack.setTag(tag);
            Util.toast(context.getPlayer(), ChatFormatting.AQUA,
                    BlockRegistry.isBrowserScreen(context.getLevel().getBlockState(pos.toBlock()).getBlock()) ?
                            "browserScreenSelected" : BlockRegistry.isSshScreen(context.getLevel().getBlockState(pos.toBlock()).getBlock()) ?
                            "sshScreenSelected" : "screenSet2");
        }

        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public String getWikiName(@Nonnull ItemStack is) {
        return is.getItem().getName(is).getString();
    }
}
