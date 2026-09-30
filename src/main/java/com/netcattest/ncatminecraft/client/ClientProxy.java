/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.client;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.network.NetworkEvent;
import com.netcattest.ncatminecraft.SharedProxy;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.block.ScreenBlock;
import com.netcattest.ncatminecraft.client.gui.*;
import com.netcattest.ncatminecraft.client.workstation.WorkstationClientView;
import com.netcattest.ncatminecraft.client.remote.RemoteClientSessions;
import com.netcattest.ncatminecraft.client.rack.RackClientApps;
import com.netcattest.ncatminecraft.controls.builtin.ClickControl;
import com.netcattest.ncatminecraft.client.gui.loading.GuiLoader;
import com.netcattest.ncatminecraft.client.renderers.*;
import com.netcattest.ncatminecraft.client.ssh.SshClientSessions;
import com.netcattest.ncatminecraft.client.sftp.SftpClientSessions;
import com.netcattest.ncatminecraft.client.log.NetworkLogService;
import com.netcattest.ncatminecraft.core.HasAdvancement;
import com.netcattest.ncatminecraft.data.GuiData;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.item.ItemLaserPointer;
import com.netcattest.ncatminecraft.item.ItemMinePad2;
import com.netcattest.ncatminecraft.item.WDItem;
import com.netcattest.ncatminecraft.miniserv.client.Client;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.registry.ItemRegistry;
import com.netcattest.ncatminecraft.registry.TileRegistry;
import com.netcattest.ncatminecraft.registry.FurnitureEntityRegistry;
import com.netcattest.ncatminecraft.utilities.Log;
import com.netcattest.ncatminecraft.utilities.Multiblock;
import com.netcattest.ncatminecraft.utilities.browser.WDBrowser;
import com.netcattest.ncatminecraft.utilities.browser.handlers.DisplayHandler;
import com.netcattest.ncatminecraft.utilities.browser.handlers.WDRouter;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.data.Rotation;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;
import com.netcattest.ncatminecraft.utilities.math.Vector3i;
import com.netcattest.ncatminecraft.utilities.serialization.NameUUIDPair;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefMessageRouter;
import org.cef.misc.CefCursorType;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;

@Mod.EventBusSubscriber(modid = "ncat_minecraft", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientProxy extends SharedProxy implements ResourceManagerReloadListener {
	
	private static ClientProxy INSTANCE;
	
	public ClientProxy() {
		INSTANCE = this;
	}
	
	public static void renderCrosshair(Options options, int screenWidth, int screenHeight, int offset, GuiGraphics poseStack, CallbackInfo ci) {
		ItemStack stack = Minecraft.getInstance().player.getItemInHand(InteractionHand.MAIN_HAND);
		ItemStack stack1 = Minecraft.getInstance().player.getItemInHand(InteractionHand.OFF_HAND);
		
		if (stack.getItem() instanceof ItemMinePad2) {
			float sign = 1;
			if (Minecraft.getInstance().player.getMainArm() == HumanoidArm.LEFT) sign = -1;
			if (!MinePadRenderer.renderAtSide(sign)) {
				ci.cancel();
				return;
			}
		} else {
			if (stack1.getItem() instanceof ItemMinePad2) {
				float sign = -1;
				if (Minecraft.getInstance().player.getMainArm() == HumanoidArm.LEFT) sign = 1;
				if (!MinePadRenderer.renderAtSide(sign)) {
					ci.cancel();
					return;
				}
			}
		}
		
		if (!(stack.getItem() instanceof ItemLaserPointer || stack.getItem() == ItemRegistry.LOG_INSPECTOR.get() ||
				stack1.getItem() instanceof ItemLaserPointer))
			return;
		
		if (!LaserPointerRenderer.isOn()) {
			RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

			poseStack.blit(new ResourceLocation(
					"ncat_minecraft:textures/gui/cursors.png"
			), (screenWidth - 15) / 2, (screenHeight - 15) / 2, offset, 240, 240, 15, 15, 256, 256);
			ci.cancel();
			return;
		}
		
		Minecraft mc = Minecraft.getInstance();
		
		BlockHitResult result = raycast(64.0);
		
		BlockPos bpos = result.getBlockPos();
		
		if (result.getType() != HitResult.Type.BLOCK || !(mc.level.getBlockState(bpos).getBlock() instanceof ScreenBlock)) {
			RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

			poseStack.blit(new ResourceLocation(
					"ncat_minecraft:textures/gui/cursors.png"
			), (screenWidth - 15) / 2, (screenHeight - 15) / 2, offset, 240, 240, 15, 15, 256, 256);
			ci.cancel();
			return;
		}

		Vector3i pos = new Vector3i(result.getBlockPos());
		BlockSide side = BlockSide.values()[result.getDirection().ordinal()];
		
		Multiblock.findOrigin(mc.level, pos, side, null);
		ScreenBlockEntity te = (ScreenBlockEntity) mc.level.getBlockEntity(pos.toBlock());

		ScreenData sc = te.getScreen(side);
		
		if (sc == null) return;

		int coordX = sc.mouseType * 15;
		int coordY = coordX / 255;
		coordX -= coordY * 255;
		coordY *= 15;
		if (sc.mouseType >= CefCursorType.NOT_ALLOWED.ordinal()) coordX -= 15;
		
		RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

		poseStack.blit(new ResourceLocation(
				"ncat_minecraft:textures/gui/cursors.png"
		), (screenWidth - 15) / 2, (screenHeight - 15) / 2, offset, coordX, coordY, 15, 15, 256, 256);

		ci.cancel();
	}

	public List<ScreenBlockEntity> getScreens() {
		return screenTracking;
	}

	@Override
	public void interactSshScreen(BlockPos position, BlockHitResult hit, boolean keyboard) {
		if (mc.level == null)
			return;
		BlockSide side = BlockSide.values()[hit.getDirection().ordinal()];
		Vector3i origin = new Vector3i(position);
		Multiblock.findOrigin(mc.level, origin, side, null);
		if (!(mc.level.getBlockEntity(origin.toBlock()) instanceof ScreenBlockEntity screen))
			return;
		ScreenData data = screen.getScreen(side);
		if (data == null || data.browser == null)
			return;
		if (keyboard) {
			mc.setScreen(new GuiKeyboard(screen, side, position));
			return;
		}
		Vector2i pixels = new Vector2i();
		float x = (float) hit.getLocation().x - screen.getBlockPos().getX();
		float y = (float) hit.getLocation().y - screen.getBlockPos().getY();
		float z = (float) hit.getLocation().z - screen.getBlockPos().getZ();
		if (ScreenBlock.hit2pixels(side, position, new Vector3i(position), data, x, y, z, pixels)) {
			if (BlockRegistry.isWorkstationScreen(screen.getBlockState().getBlock()))
				WorkstationClientView.sendRemoteMouse(screen, side, ClickControl.ControlType.CLICK, pixels, 0);
			screen.handleMouseEvent(side, ClickControl.ControlType.CLICK, pixels, 0);
		}
	}

	public List<PadData> getPads() {
		return padList;
	}

	public class PadData {
		
		public CefBrowser view;
		public final UUID id;
		private boolean isInHotbar;
		private long lastURLSent;
		
		public int activeCursor;
		
		private PadData(String url, UUID id) {
			String webUrl;
			try {
				webUrl = ScreenBlockEntity.url(url);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			view = WDBrowser.createBrowser(NcatMinecraft.applyBlacklist(webUrl), false);
			if (view instanceof MCEFBrowser browser) {
				browser.resize((int) NcatMinecraft.INSTANCE.padResX, (int) NcatMinecraft.INSTANCE.padResY);
				browser.setCursorChangeListener((cursor) -> {
					activeCursor = cursor;
				});
			}
			isInHotbar = true;
			this.id = id;
		}

		public void updateTime() {
			lastURLSent = System.currentTimeMillis();
		}

		public long lastSent() {
			return lastURLSent;
		}
	}
	
	private Minecraft mc;
	private MinePadRenderer minePadRenderer;
	private LaserPointerRenderer laserPointerRenderer;
	private Screen nextScreen;
	private boolean isF1Down;
	
	private int miniservPort;
	private boolean msClientStarted;
	
	private final Field advancementToProgressField = findAdvancementToProgressField();
	private ClientAdvancements lastAdvMgr;
	private Map advancementToProgress;
	
	private final ArrayList<ScreenBlockEntity> screenTracking = new ArrayList<>();
	private int lastTracked = 0;
	
	private final HashMap<UUID, PadData> padMap = new HashMap<>();
	private final ArrayList<PadData> padList = new ArrayList<>();
	private int minePadTickCounter = 0;
	
	@SubscribeEvent
	public static void onClientSetup(FMLClientSetupEvent event) {
		BlockEntityRenderers.register(TileRegistry.SCREEN_BLOCK_ENTITY.get(), new ScreenRenderer.ScreenRendererProvider());
		BlockEntityRenderers.register((net.minecraft.world.level.block.entity.BlockEntityType<com.netcattest.ncatminecraft.entity.KeyboardBlockEntity>) (net.minecraft.world.level.block.entity.BlockEntityType<?>) TileRegistry.KEYBOARD.get(),
				context -> (keyboard, partialTick, pose, buffers, light, overlay) -> UsbCableRenderer.renderKeyboard(keyboard, pose, buffers));
		BlockEntityRenderers.register(TileRegistry.WATER_COOLER.get(), WaterCoolerRenderer::new);
		BlockEntityRenderers.register(TileRegistry.DIGITAL_CLOCK.get(), DigitalClockRenderer::new);
		BlockEntityRenderers.register(TileRegistry.NETWORK_SWITCH.get(), NetworkSwitchRenderer::new);
		BlockEntityRenderers.register(TileRegistry.MANAGED_SWITCH.get(), NetworkSwitchRenderer::new);
		BlockEntityRenderers.register(TileRegistry.MANAGED_TABLET.get(), ManagedTabletRenderer::new);
		BlockEntityRenderers.register(TileRegistry.RACK_FRAME.get(), com.netcattest.ncatminecraft.client.renderers.RackRenderer::new);
		EntityRenderers.register(FurnitureEntityRegistry.CHAIR_SEAT.get(), GamingChairSeatRenderer::new);
	}

	@SubscribeEvent
	public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
		event.register(WaterCoolerRenderer.LEVER_RED);
		event.register(WaterCoolerRenderer.LEVER_BLUE);
		event.register(WaterCoolerRenderer.CUBE);
		event.register(WaterCoolerRenderer.PAPER_CUP);
		event.register(RackRenderer.DOOR_12U);
		event.register(RackRenderer.DOOR_18U);
		event.register(RackRenderer.DOOR_6U);
	}
	
	@SubscribeEvent
	public static void onModelRegistryEvent(ModelEvent.RegisterGeometryLoaders event) {
		event.register(ScreenModelLoader.SCREEN_LOADER.getPath(), new ScreenModelLoader());
	}
	
	@Override
	public void preInit() {
		super.preInit();
		mc = Minecraft.getInstance();
		MinecraftForge.EVENT_BUS.register(this);
		MinecraftForge.EVENT_BUS.register(ClientSerialAnimation.class);
	}
	
	@Override
	public void onCefInit() {
		minePadRenderer = new MinePadRenderer();
		laserPointerRenderer = new LaserPointerRenderer();

		if (!MCEF.isInitialized()) return;

		MCEF.getApp().getHandle().registerSchemeHandlerFactory(
				"ncat_minecraft", "",
				(browser, frame, url, request) -> {
					return new WDScheme(request.getURL());
				}
		);

		MCEF.getClient().addDisplayHandler(DisplayHandler.INSTANCE);
		MCEF.getClient().getHandle().addRequestHandler(NetworkLogService.REQUEST_HANDLER);
		MCEF.getClient().getHandle().addMessageRouter(CefMessageRouter.create(WDRouter.INSTANCE));

		findAdvancementToProgressField();
	}
	
	@Override
	public void postInit() {
		((ReloadableResourceManager) mc.getResourceManager()).registerReloadListener(this);
	}
	
	@Override
	public Level getWorld(ResourceKey<Level> dim) {
		Level ret = mc.level;
		if (ret != null) {
			if (!ret.dimension().equals(dim))
				throw new RuntimeException("Can't get non-current dimension " + dim + " from client.");
			return ret;
		} else {
			throw new RuntimeException("Level on client is null");
		}
	}
	
	@Override
	public void enqueue(Runnable r) {
		mc.submit(r);
	}
	
	@Override
	public void displayGui(GuiData data) {
		Screen gui = data.createGui(mc.screen, mc.level);
		if (gui != null)
			mc.setScreen(gui);
	}
	
	@Override
	public void trackScreen(ScreenBlockEntity tes, boolean track) {
		int idx = -1;
		for (int i = 0; i < screenTracking.size(); i++) {
			if (screenTracking.get(i) == tes) {
				idx = i;
				break;
			}
		}
		
		if (track) {
			if (idx < 0)
				screenTracking.add(tes);
		} else if (idx >= 0)
			screenTracking.remove(idx);
	}
	
	@Override
	public void onAutocompleteResult(NameUUIDPair[] pairs) {
		if (mc.screen != null && mc.screen instanceof WDScreen screen) {
			if (pairs.length == 0)
				(screen).onAutocompleteFailure();
			else
				(screen).onAutocompleteResult(pairs);
		}
	}
	
	@Override
	public GameProfile[] getOnlineGameProfiles() {
		return new GameProfile[]{mc.player.getGameProfile()};
	}
	
	@Override
	public void screenUpdateResolutionInGui(Vector3i pos, BlockSide side, Vector2i res) {
		if (mc.screen != null && mc.screen instanceof GuiScreenConfig gsc) {
			if (gsc.isForBlock(pos.toBlock(), side))
				gsc.updateResolution(res);
		}
	}
	
	@Override
	public void screenUpdateRotationInGui(Vector3i pos, BlockSide side, Rotation rot) {
		if (mc.screen != null && mc.screen instanceof GuiScreenConfig gsc) {
			if (gsc.isForBlock(pos.toBlock(), side))
				gsc.updateRotation(rot);
		}
	}
	
	@Override
	public void screenUpdateAutoVolumeInGui(Vector3i pos, BlockSide side, boolean av) {
		if (mc.screen != null && mc.screen instanceof GuiScreenConfig gsc) {
			if (gsc.isForBlock(pos.toBlock(), side))
				gsc.updateAutoVolume(av);
		}
	}
	
	@Override
	public void displaySetPadURLGui(ItemStack is, String padURL) {
		mc.setScreen(new GuiSetURL2(is, padURL));
	}
	
	@Override
	public void openMinePadGui(UUID padId) {
		PadData pd = padMap.get(padId);
		
		if (pd != null && pd.view != null)
			mc.setScreen(new GuiMinePad(pd));
	}
	
	@Override
	@Nonnull
	public HasAdvancement hasClientPlayerAdvancement(@Nonnull ResourceLocation rl) {
		if (advancementToProgressField != null && mc.player != null && mc.player.connection != null) {
			ClientAdvancements cam = mc.player.connection.getAdvancements();
			Advancement adv = cam.getAdvancements().get(rl);
			
			if (adv == null)
				return HasAdvancement.DONT_KNOW;
			
			if (lastAdvMgr != cam) {
				lastAdvMgr = cam;
				
				try {
					advancementToProgress = (Map) advancementToProgressField.get(cam);
				} catch (Throwable t) {
					Log.warningEx("Could not get ClientAdvancementManager.advancementToProgress field", t);
					advancementToProgress = null;
					return HasAdvancement.DONT_KNOW;
				}
			}
			
			if (advancementToProgress == null)
				return HasAdvancement.DONT_KNOW;
			
			Object progress = advancementToProgress.get(adv);
			if (progress == null)
				return HasAdvancement.NO;
			
			if (!(progress instanceof AdvancementProgress)) {
				Log.warning("The ClientAdvancementManager.advancementToProgress map does not contain AdvancementProgress instances");
				advancementToProgress = null;
				return HasAdvancement.DONT_KNOW;
			}
			
			return ((AdvancementProgress) progress).isDone() ? HasAdvancement.YES : HasAdvancement.NO;
		}
		
		return HasAdvancement.DONT_KNOW;
	}
	
	@Override
	public MinecraftServer getServer() {
		return mc.getSingleplayerServer();
	}
	
	
	@Override
	public void setMiniservClientPort(int port) {
		miniservPort = port;
	}
	
	@Override
	public void startMiniservClient() {
		if (miniservPort <= 0) {
			Log.warning("Can't start miniserv client: miniserv is disabled");
			return;
		}
		
		if (mc.player == null) {
			Log.warning("Can't start miniserv client: player is null");
			return;
		}
		
		SocketAddress saddr = mc.player.connection.getConnection().channel().remoteAddress();
		if (saddr == null || !(saddr instanceof InetSocketAddress)) {
			Log.warning("Miniserv client: remote address is not inet, assuming local address");
			saddr = new InetSocketAddress("127.0.0.1", 1234);
		}
		
		InetSocketAddress msAddr = new InetSocketAddress(((InetSocketAddress) saddr).getAddress(), miniservPort);
		Client.getInstance().start(msAddr);
		msClientStarted = true;
	}
	
	@Override
	public boolean isMiniservDisabled() {
		return miniservPort <= 0;
	}
	
	@Override
	public void closeGui(BlockPos bp, BlockSide bs) {
		if (mc.screen instanceof WDScreen) {
			WDScreen scr = (WDScreen) mc.screen;
			
			if (scr.isForBlock(bp, bs))
				mc.setScreen(null);
		}
	}
	
	@Override
	public void renderRecipes() {
		nextScreen = new RenderRecipe();
	}
	
	@Override
	public boolean isShiftDown() {
		return Screen.hasShiftDown();
	}
	
	
	
	@Override
	public void onResourceManagerReload(ResourceManager resourceManager) {
		Log.info("Resource manager reload: clearing GUI cache...");
		GuiLoader.clearCache();
	}
	
	


	@SubscribeEvent
	public void onLevelTick(TickEvent.LevelTickEvent ev) {
		if (!ev.side.equals(LogicalSide.CLIENT)) return;
		if (ev.phase != TickEvent.Phase.END) return;
		
		if (mc.player == null || screenTracking.isEmpty())
			return;
		
		int id = lastTracked % screenTracking.size();
		
		ScreenBlockEntity tes = screenTracking.get(id);
		
		if (!tes.getLevel().equals(ev.level))
			return;
		
		lastTracked++;
		if (tes.getLevel() != mc.player.level()) {
			if (!tes.isLoaded())
				tes.load();
		} else {
			Camera camera = mc.getEntityRenderDispatcher().camera;
			Entity entity = null;


			if (camera != null) entity = camera.getEntity();
			if (entity == null) entity = mc.player;
			if (entity != null) {
				double dist = distanceTo(tes, entity.getPosition(0));

				if (tes.isLoaded()) {
					if (dist > NcatMinecraft.INSTANCE.unloadDistance2 * 16)
						tes.deactivate();
				} else if (dist <= NcatMinecraft.INSTANCE.loadDistance2 * 16)
					tes.activate();
			}
		}
	}
	
	@SubscribeEvent
	public void onTick(TickEvent.ClientTickEvent ev) {
		if (ev.phase != TickEvent.Phase.END) return;
		RackClientApps.tick();
		SshClientSessions.tick();
		RemoteClientSessions.tick();
		com.netcattest.ncatminecraft.client.terminal.LocalTerminalSessions.tick();
		SftpClientSessions.tick();
		NetworkLogService.tick(this);
		
		if (InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_F1)) {
			if (!isF1Down) {
				isF1Down = true;
				
				String wikiName = null;
				if (mc.screen instanceof WDScreen)
					wikiName = ((WDScreen) mc.screen).getWikiPageName();
				else if (mc.screen instanceof AbstractContainerScreen) {
					Slot slot = ((AbstractContainerScreen) mc.screen).getSlotUnderMouse();
					
					if (slot != null && slot.hasItem() && slot.getItem().getItem() instanceof WDItem)
						wikiName = ((WDItem) slot.getItem().getItem()).getWikiName(slot.getItem());
				}
				
			}
		} else if (isF1Down)
			isF1Down = false;
		
		if (nextScreen != null && mc.screen == null) {
			mc.setScreen(nextScreen);
			nextScreen = null;
		}
		
		boolean holdingLaser = Minecraft.getInstance().player != null &&
				Minecraft.getInstance().player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof ItemLaserPointer;
		if (holdingLaser && !wasHoldingLaser)
			mouseOn = true;
		if (!holdingLaser)
			mouseOn = false;
		wasHoldingLaser = holdingLaser;

		if (KEY_MOUSE.isDown()) {
			if (!rDown) {
				rDown = true;
				mouseOn = !mouseOn;
			}
		} else rDown = false;
		
		if (++minePadTickCounter >= 10) {
			minePadTickCounter = 0;
			Player ep = mc.player;
			
			for (PadData pd : padList)
				pd.isInHotbar = false;
			
			if (ep != null) {
				updateInventory(ep.getInventory().items, ep.getItemInHand(InteractionHand.MAIN_HAND), 9);
				updateInventory(ep.getInventory().offhand, ep.getItemInHand(InteractionHand.OFF_HAND), 1);
			}
			
			
			for (int i = padList.size() - 1; i >= 0; i--) {
				PadData pd = padList.get(i);
				
				if (!pd.isInHotbar) {
					pd.view.close(true);
					pd.view = null;
					padList.remove(i);
					padMap.remove(pd.id);
				}
			}
		}
		
		if (LaserPointerRenderer.isOn()) {
			ItemLaserPointer.tick(mc);
		} else {
			ItemLaserPointer.deselect(mc);
		}
		
		if (msClientStarted && mc.player == null) {
			msClientStarted = false;
			Client.getInstance().stop();
		}
	}
	
	@SubscribeEvent
	public void onRenderPlayerHand(RenderHandEvent ev) {
		Item item = ev.getItemStack().getItem();
		IItemRenderer renderer;
		
		if (ItemRegistry.MINEPAD.isPresent() && ItemRegistry.LASER_POINTER.isPresent()) {
			if (item == ItemRegistry.MINEPAD.get())
				renderer = minePadRenderer;
			else if (item == ItemRegistry.LASER_POINTER.get() || item == ItemRegistry.LOG_INSPECTOR.get())
				renderer = laserPointerRenderer;
			else
				return;
			HumanoidArm handSide = mc.player.getMainArm();
			if (ev.getHand() == InteractionHand.OFF_HAND)
				handSide = handSide.getOpposite();
			
			if (renderer.render(ev.getPoseStack(), ev.getItemStack(), (handSide == HumanoidArm.RIGHT) ? 1.0f : -1.0f, ev.getSwingProgress(), ev.getEquipProgress(), ev.getMultiBufferSource(), ev.getPackedLight())) {
				ev.setCanceled(true);
			}
		}
	}
	
	@SubscribeEvent
	public void onWorldUnload(LevelEvent.Unload ev) {
		SshClientSessions.closeAll();
		com.netcattest.ncatminecraft.client.terminal.LocalTerminalSessions.closeAll();
		RemoteClientSessions.closeAll();
		SftpClientSessions.closeAll();
		RackClientApps.clear();
		NetworkLogService.clear();
		Log.info("World unloaded; killing screens...");
		if (ev.getLevel() instanceof Level level) {
			ResourceLocation dim = level.dimension().location();
			for (int i = screenTracking.size() - 1; i >= 0; i--) {
				if (screenTracking.get(i).getLevel().dimension().location().equals(dim))
					screenTracking.remove(i).unload();
			}
		}
	}
	
	public static BlockHitResult raycast(double dist) {
		Minecraft mc = Minecraft.getInstance();
		
		Vec3 start = mc.player.getEyePosition(1.0f);
		Vec3 lookVec = mc.player.getLookAngle();
		Vec3 end = start.add(lookVec.x * dist, lookVec.y * dist, lookVec.z * dist);
		
		return mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, null));
	}
	
	private void updateInventory(NonNullList<ItemStack> inv, ItemStack heldStack, int cnt) {
		for (int i = 0; i < cnt; i++) {
			ItemStack item = inv.get(i);
			
			if (ItemRegistry.MINEPAD.isPresent()) {
				if (item.getItem() == ItemRegistry.MINEPAD.get()) {
					CompoundTag tag = item.getTag();
					
					if (tag != null && tag.contains("PadID"))
						updatePad(tag.getUUID("PadID"), tag, item == heldStack);
				}
			}
		}
	}
	
	private void updatePad(UUID id, CompoundTag tag, boolean isSelected) {
		PadData pd = padMap.get(id);
		
		if (pd != null)
			pd.isInHotbar = true;
		else if (isSelected && tag.contains("PadURL")) {
			pd = new PadData(tag.getString("PadURL"), id);
			padMap.put(id, pd);
			padList.add(pd);
		}
	}
	
	public MinePadRenderer getMinePadRenderer() {
		return minePadRenderer;
	}
	
	public PadData getPadByID(UUID id) {
		return padMap.get(id);
	}
	
	public static final class ScreenSidePair {
		
		public ScreenBlockEntity tes;
		public BlockSide side;
		
	}
	
	public boolean findScreenFromBrowser(CefBrowser browser, ScreenSidePair pair) {
		for (ScreenBlockEntity tes : screenTracking) {
			for (int i = 0; i < tes.screenCount(); i++) {
				ScreenData scr = tes.getScreen(i);
				
				if (scr.browser == browser) {
					pair.tes = tes;
					pair.side = scr.side;
					return true;
				}
			}
		}
		
		return false;
	}
	
	private static Field findAdvancementToProgressField() {
		Field[] fields = ClientAdvancements.class.getDeclaredFields();
		Optional<Field> result = Arrays.stream(fields).filter(f -> f.getType() == Map.class).findAny();
		
		if (result.isPresent()) {
			try {
				Field ret = result.get();
				ret.setAccessible(true);
				return ret;
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
		
		Log.warning("ClientAdvancementManager.advancementToProgress field could not be found");
		return null;
	}
	
	@Override
	public BlockGetter getWorld(NetworkEvent.Context context) {
		BlockGetter senderLevel = super.getWorld(context);
		if (senderLevel == null) return Minecraft.getInstance().level;
		return senderLevel;
	}
	
	public static void onDrawSelection(RenderHighlightEvent event) {
		if (event.getTarget() instanceof BlockHitResult bhr) {
			BlockState state = Minecraft.getInstance().level.getBlockState(bhr.getBlockPos());
			if (state.getBlock() instanceof ScreenBlock screen) {
				Vector3i vec = new Vector3i(bhr.getBlockPos().getX(), bhr.getBlockPos().getY(), bhr.getBlockPos().getZ());
				BlockSide side = BlockSide.fromInt(bhr.getDirection().ordinal());
				Multiblock.findOrigin(
						Minecraft.getInstance().level, vec,
						side, null
				);
				
				BlockPos pos = new BlockPos(vec.x, vec.y, vec.z);
				BlockEntity be = Minecraft.getInstance().level.getBlockEntity(
						pos
				);
				if (be instanceof ScreenBlockEntity tes) {
					if (tes.getScreen(side) != null) {
						event.setCanceled(true);
					}
				}
			}
		}
	}
	
	public static final KeyMapping KEY_MOUSE = new KeyMapping("ncat_minecraft.key.toggle_mouse", GLFW.GLFW_KEY_R, "key.categories.misc");
	static boolean rDown = false;
	public static boolean mouseOn = false;
	private boolean wasHoldingLaser;
	
	public static void onKeybindRegistry(RegisterKeyMappingsEvent event) {
		event.register(KEY_MOUSE);
	}
}
