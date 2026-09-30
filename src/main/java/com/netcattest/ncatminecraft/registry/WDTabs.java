/*
 * Copyright (C) 2018 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class WDTabs {
	public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "ncat_minecraft");
	
	public static final RegistryObject<CreativeModeTab> BROWSER_TAB = TABS.register("browser", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.ncat_minecraft.browser"))
			.icon(() -> new ItemStack(ItemRegistry.SCREEN.get()))
			.displayItems((params, output) -> {
				output.accept(ItemRegistry.SCREEN.get());
				output.accept(ItemRegistry.LOG_SCREEN.get());
				output.accept(ItemRegistry.DEVTOOLS_SCREEN.get());
				output.accept(ItemRegistry.DEVTOOLS_KEYBOARD.get());
				output.accept(ItemRegistry.LOG_INSPECTOR.get());
				output.accept(ItemRegistry.LOG_TABLET.get());
				output.accept(ItemRegistry.KEYBOARD.get());
				output.accept(ItemRegistry.LINKER.get());
				output.accept(ItemRegistry.CONFIGURATOR.get());
				output.accept(ItemRegistry.LASER_POINTER.get());
				output.accept(ItemRegistry.DATA_CABLE.get());
				output.accept(ItemRegistry.USB_CABLE.get());
			})
			.build()
	);

	public static final RegistryObject<CreativeModeTab> SSH_TAB = TABS.register("ssh", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.ncat_minecraft.ssh"))
			.icon(() -> new ItemStack(ItemRegistry.SSH_SCREEN.get()))
			.displayItems((params, output) -> {
				output.accept(ItemRegistry.SSH_SCREEN.get());
				output.accept(ItemRegistry.SFTP_SCREEN.get());
				output.accept(ItemRegistry.SSH_KEYBOARD.get());
				output.accept(ItemRegistry.SFTP_KEYBOARD.get());
				output.accept(ItemRegistry.LINKER.get());
				output.accept(ItemRegistry.CONFIGURATOR.get());
				output.accept(ItemRegistry.LASER_POINTER.get());
				output.accept(ItemRegistry.DATA_CABLE.get());
				output.accept(ItemRegistry.USB_CABLE.get());
			})
			.build()
	);

	public static final RegistryObject<CreativeModeTab> TERMINAL_TAB = TABS.register("terminal", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.ncat_minecraft.terminal"))
			.icon(() -> new ItemStack(ItemRegistry.TERMINAL_SCREEN.get()))
			.displayItems((params, output) -> {
				output.accept(ItemRegistry.TERMINAL_SCREEN.get());
				output.accept(ItemRegistry.TERMINAL_KEYBOARD.get());
				output.accept(ItemRegistry.RACK_MODULE_TERMINAL.get());
				output.accept(ItemRegistry.LINKER.get());
				output.accept(ItemRegistry.CONFIGURATOR.get());
				output.accept(ItemRegistry.DATA_CABLE.get());
				output.accept(ItemRegistry.DATA_PORT.get());
				output.accept(ItemRegistry.USB_CABLE.get());
				output.accept(ItemRegistry.USB_PORT.get());
			})
			.build()
	);

	public static final RegistryObject<CreativeModeTab> PROXY_TAB = TABS.register("proxy", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.ncat_minecraft.proxy"))
			.icon(() -> new ItemStack(ItemRegistry.PROXY_SCREEN.get()))
			.displayItems((params, output) -> {
				output.accept(ItemRegistry.PROXY_SCREEN.get());
				output.accept(ItemRegistry.PROXY_KEYBOARD.get());
				output.accept(ItemRegistry.RACK_MODULE_PROXY.get());
				output.accept(ItemRegistry.SCREEN.get());
				output.accept(ItemRegistry.LINKER.get());
				output.accept(ItemRegistry.CONFIGURATOR.get());
				output.accept(ItemRegistry.DATA_CABLE.get());
				output.accept(ItemRegistry.DATA_PORT.get());
				output.accept(ItemRegistry.USB_CABLE.get());
				output.accept(ItemRegistry.USB_PORT.get());
			})
			.build()
	);

	public static final RegistryObject<CreativeModeTab> REMOTE_CONNECTION_TAB = TABS.register("remote_connection", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.ncat_minecraft.remote_connection"))
			.icon(() -> new ItemStack(ItemRegistry.REMOTE_SCREEN.get()))
			.displayItems((params, output) -> {
				output.accept(ItemRegistry.REMOTE_SCREEN.get());
				output.accept(ItemRegistry.RACK_MODULE_REMOTE.get());
				output.accept(ItemRegistry.KEYBOARD.get());
				output.accept(ItemRegistry.LINKER.get());
				output.accept(ItemRegistry.CONFIGURATOR.get());
				output.accept(ItemRegistry.DATA_CABLE.get());
				output.accept(ItemRegistry.DATA_PORT.get());
				output.accept(ItemRegistry.USB_CABLE.get());
				output.accept(ItemRegistry.USB_PORT.get());
			})
			.build()
	);

	public static final RegistryObject<CreativeModeTab> CABLE_TAB = TABS.register("cables", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.ncat_minecraft.cables"))
			.icon(() -> new ItemStack(ItemRegistry.DATA_CABLE.get()))
			.displayItems((params, output) -> {
				output.accept(ItemRegistry.DATA_CABLE.get());
				output.accept(ItemRegistry.DATA_PORT.get());
				output.accept(ItemRegistry.DATA_PORT_TOOL.get());
				output.accept(ItemRegistry.CABLE_ADJUSTER.get());
				output.accept(ItemRegistry.USB_CABLE.get());
				output.accept(ItemRegistry.USB_PORT.get());
				output.accept(ItemRegistry.USB_PORT_TOOL.get());
				output.accept(ItemRegistry.USB_CABLE_ADJUSTER.get());
				output.accept(ItemRegistry.SERIAL_CABLE.get());
			})
			.build()
	);

	public static final RegistryObject<CreativeModeTab> NETWORK_TAB = TABS.register("network", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.ncat_minecraft.network_equipment"))
			.icon(() -> new ItemStack(ItemRegistry.NETWORK_SWITCH.get()))
			.displayItems((params, output) -> {
				output.accept(ItemRegistry.NETWORK_SWITCH.get());
				output.accept(ItemRegistry.MANAGED_SWITCH.get());
				output.accept(ItemRegistry.RACK_MODULE_SWITCH.get());
				output.accept(ItemRegistry.RACK_MODULE_MANAGED_SWITCH.get());
				output.accept(ItemRegistry.MANAGED_TABLET.get());
				output.accept(ItemRegistry.SERIAL_CABLE.get());
				output.accept(ItemRegistry.DATA_CABLE.get());
				output.accept(ItemRegistry.CABLE_ADJUSTER.get());
			})
			.build()
	);

	public static final RegistryObject<CreativeModeTab> WORKSTATION_TAB = TABS.register("workstation", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.ncat_minecraft.workstation"))
			.icon(() -> new ItemStack(ItemRegistry.WORKSTATION_SCREEN.get()))
			.displayItems((params, output) -> {
				output.accept(ItemRegistry.WORKSTATION_SCREEN.get());
				output.accept(ItemRegistry.WORKSTATION_KEYBOARD.get());
				output.accept(ItemRegistry.DATA_CABLE.get());
				output.accept(ItemRegistry.DATA_PORT.get());
				output.accept(ItemRegistry.DATA_PORT_TOOL.get());
			})
			.build()
	);

	public static final RegistryObject<CreativeModeTab> RACK_TAB = TABS.register("rack", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.ncat_minecraft.rack"))
			.icon(() -> new ItemStack(ItemRegistry.RACK_FRAME_12U.get()))
			.displayItems((params, output) -> {
				output.accept(ItemRegistry.RACK_FRAME_12U.get());
				output.accept(ItemRegistry.RACK_FRAME_18U.get());
				output.accept(ItemRegistry.RACK_WALL_6U.get());
				output.accept(ItemRegistry.RACK_MODULE_BROWSER.get());
				output.accept(ItemRegistry.RACK_MODULE_SSH.get());
				output.accept(ItemRegistry.RACK_MODULE_LOG.get());
				output.accept(ItemRegistry.RACK_MODULE_DEVTOOLS.get());
				output.accept(ItemRegistry.RACK_MODULE_SFTP.get());
				output.accept(ItemRegistry.RACK_MODULE_TERMINAL.get());
				output.accept(ItemRegistry.RACK_MODULE_REMOTE.get());
				output.accept(ItemRegistry.RACK_MODULE_PROXY.get());
				output.accept(ItemRegistry.RACK_MODULE_SWITCH.get());
				output.accept(ItemRegistry.RACK_MODULE_MANAGED_SWITCH.get());
				output.accept(ItemRegistry.DATA_CABLE.get());
			})
			.build()
	);

	public static final RegistryObject<CreativeModeTab> FURNITURE_TAB = TABS.register("furniture", () -> CreativeModeTab.builder()
			.title(Component.translatable("itemGroup.ncat_minecraft.furniture"))
			.icon(() -> new ItemStack(ItemRegistry.GAMING_CHAIR.get()))
			.displayItems((params, output) -> {
				output.accept(ItemRegistry.GAMING_CHAIR.get());
				output.accept(ItemRegistry.TOILET.get());
				output.accept(ItemRegistry.WATER_COOLER.get());
				output.accept(ItemRegistry.GALLON.get());
				output.accept(ItemRegistry.PAPER_CUP.get());
				output.accept(ItemRegistry.PAPER_CUP_WATER.get());
				output.accept(ItemRegistry.DIGITAL_CLOCK.get());
				output.accept(ItemRegistry.DIGITAL_CLOCK_WHITE.get());
				output.accept(ItemRegistry.RUBBER_DUCK.get());
				output.accept(ItemRegistry.ALTA_WIFI.get());
			})
			.build()
	);

	public static void init(IEventBus bus) {
		TABS.register(bus);
	}
}
