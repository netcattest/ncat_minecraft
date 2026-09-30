/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.item;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import com.netcattest.ncatminecraft.block.ScreenBlock;
import com.netcattest.ncatminecraft.client.ClientProxy;
import com.netcattest.ncatminecraft.client.workstation.WorkstationClientView;
import com.netcattest.ncatminecraft.controls.builtin.ClickControl;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.net.server_bound.C2SMessageScreenCtrl;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.registry.ItemRegistry;
import com.netcattest.ncatminecraft.utilities.Multiblock;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ItemLaserPointer extends Item implements WDItem {
	
	public ItemLaserPointer(Properties properties) {
		super(properties
				.stacksTo(1)
		);
	}
	
	private static ScreenBlockEntity pointedScreen;
	private static BlockSide pointedScreenSide;
	private static long lastPointPacket;
	
	private static final Pressed[] pressed = new Pressed[2];
	
	public static void tick(Minecraft mc) {
		Target target = target(mc);
		if (target == null) {
			deselectScreen();
			return;
		}
		target.screen.handleMouseEvent(target.side, ClickControl.ControlType.MOVE, target.pixels, -1);
		if (pointedScreen != target.screen || pointedScreenSide != target.side) {
			pointedScreen = target.screen;
			pointedScreenSide = target.side;
			lastPointPacket = 0;
		}
		long now = System.currentTimeMillis();
		if (BlockRegistry.isWorkstationScreen(target.screen.getBlockState().getBlock()) && now - lastPointPacket >= 100) {
			lastPointPacket = now;
			WorkstationClientView.sendRemoteMouse(target.screen, target.side, ClickControl.ControlType.MOVE, target.pixels, -1);
		} else if (BlockRegistry.isBrowserScreen(target.screen.getBlockState().getBlock()) && now - lastPointPacket >= 100) {
			lastPointPacket = now;
			WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserMove(target.screen, target.side, target.pixels));
		}
	}
	
	public static void deselect(Minecraft mc) {
		for (int button = 0; button < pressed.length; button++)
			press(false, button);
		deselectScreen();
	}
	
	private static void deselectScreen() {
		pointedScreen = null;
		pointedScreenSide = null;
	}
	
	public static boolean press(boolean down, int rawButton) {
		if (rawButton < 0 || rawButton > 1)
			return false;
		if (!down) {
			Pressed previous = pressed[rawButton];
			if (previous == null)
				return false;
			if (BlockRegistry.isWorkstationScreen(previous.screen.getBlockState().getBlock()))
				WorkstationClientView.sendRemoteMouse(previous.screen, previous.side, ClickControl.ControlType.UP, null, previous.button);
			previous.screen.handleMouseEvent(previous.side, ClickControl.ControlType.UP, previous.pixels, previous.button);
			if (BlockRegistry.isBrowserScreen(previous.screen.getBlockState().getBlock()))
				WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserUp(previous.screen, previous.side, previous.button));
			pressed[rawButton] = null;
			return true;
		}
		if (pressed[rawButton] != null)
			return true;
		Target target = target(Minecraft.getInstance());
		if (target == null)
			return false;
		int button = rawButton;
		target.screen.handleMouseEvent(target.side, ClickControl.ControlType.MOVE, target.pixels, -1);
		if (BlockRegistry.isWorkstationScreen(target.screen.getBlockState().getBlock()))
			WorkstationClientView.sendRemoteMouse(target.screen, target.side, ClickControl.ControlType.DOWN, target.pixels, button);
		target.screen.handleMouseEvent(target.side, ClickControl.ControlType.DOWN, target.pixels, button);
		pressed[rawButton] = new Pressed(target.screen, target.side, target.pixels, button);
		if (BlockRegistry.isBrowserScreen(target.screen.getBlockState().getBlock()))
			WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserDown(target.screen, target.side, target.pixels, button));
		return true;
	}
	
	public static boolean isOn() {
		return pressed[0] != null || pressed[1] != null;
	}

	private static Target target(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.screen != null)
            return null;
        boolean inspector = mc.player.getMainHandItem().getItem() == ItemRegistry.LOG_INSPECTOR.get();
        if (!inspector && mc.player.getMainHandItem().getItem() != ItemRegistry.LASER_POINTER.get()) return null;
        BlockHitResult result = ClientProxy.raycast(64.0);
        if (result.getType() != HitResult.Type.BLOCK || !(mc.level.getBlockState(result.getBlockPos()).getBlock() instanceof ScreenBlock))
            return null;
        if (inspector && !BlockRegistry.isLogScreen(mc.level.getBlockState(result.getBlockPos()).getBlock())) return null;
		Vector3i pos = new Vector3i(result.getBlockPos());
		BlockSide side = BlockSide.values()[result.getDirection().ordinal()];
		Multiblock.findOrigin(mc.level, pos, side, null);
		if (!(mc.level.getBlockEntity(pos.toBlock()) instanceof ScreenBlockEntity screen))
			return null;
		ScreenData data = screen.getScreen(side);
		if (data == null || data.browser == null)
			return null;
		Vector2i pixels = new Vector2i();
		float x = (float) result.getLocation().x - pos.x;
		float y = (float) result.getLocation().y - pos.y;
		float z = (float) result.getLocation().z - pos.z;
		return ScreenBlock.hit2pixels(side, result.getBlockPos(), new Vector3i(result.getBlockPos()), data, x, y, z, pixels) ?
				new Target(screen, side, pixels) : null;
	}

	private record Target(ScreenBlockEntity screen, BlockSide side, Vector2i pixels) {
	}

	private record Pressed(ScreenBlockEntity screen, BlockSide side, Vector2i pixels, int button) {
	}
	
	@Nullable
	@Override
	public String getWikiName(@Nonnull ItemStack is) {
		return is.getItem().getName(is).getString();
	}
}
