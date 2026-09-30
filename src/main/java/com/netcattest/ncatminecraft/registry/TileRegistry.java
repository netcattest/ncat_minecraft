package com.netcattest.ncatminecraft.registry;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.netcattest.ncatminecraft.entity.*;

public class TileRegistry {
    public static final DeferredRegister<BlockEntityType<?>> TILE_TYPES = DeferredRegister
            .create(ForgeRegistries.BLOCK_ENTITY_TYPES, "ncat_minecraft");

    public static final RegistryObject<BlockEntityType<ScreenBlockEntity>> SCREEN_BLOCK_ENTITY = TILE_TYPES
            .register("screen", () -> BlockEntityType.Builder
                    .of(ScreenBlockEntity::new, BlockRegistry.SCREEN_BLOCk.get(), BlockRegistry.SSH_SCREEN_BLOCK.get(), BlockRegistry.SFTP_SCREEN_BLOCK.get(), BlockRegistry.LOG_SCREEN_BLOCK.get(), BlockRegistry.DEVTOOLS_SCREEN_BLOCK.get(), BlockRegistry.WORKSTATION_SCREEN_BLOCK.get(), BlockRegistry.TERMINAL_SCREEN_BLOCK.get(), BlockRegistry.REMOTE_SCREEN_BLOCK.get(), BlockRegistry.PROXY_SCREEN_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<?>> KEYBOARD = TILE_TYPES.register("kb_left", () -> BlockEntityType.Builder
            .of(KeyboardBlockEntity::new, BlockRegistry.KEYBOARD_BLOCK.get(), BlockRegistry.SSH_KEYBOARD_BLOCK.get(), BlockRegistry.SFTP_KEYBOARD_BLOCK.get(), BlockRegistry.DEVTOOLS_KEYBOARD_BLOCK.get(), BlockRegistry.WORKSTATION_KEYBOARD_BLOCK.get(), BlockRegistry.TERMINAL_KEYBOARD_BLOCK.get(), BlockRegistry.PROXY_KEYBOARD_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<NetworkSwitchBlockEntity>> NETWORK_SWITCH = TILE_TYPES.register("network_switch",
            () -> BlockEntityType.Builder.of(NetworkSwitchBlockEntity::new, BlockRegistry.NETWORK_SWITCH.get()).build(null));
    public static final RegistryObject<BlockEntityType<ManagedSwitchBlockEntity>> MANAGED_SWITCH = TILE_TYPES.register("managed_switch",
            () -> BlockEntityType.Builder.of(ManagedSwitchBlockEntity::new, BlockRegistry.MANAGED_SWITCH.get()).build(null));
    public static final RegistryObject<BlockEntityType<ManagedTabletBlockEntity>> MANAGED_TABLET = TILE_TYPES.register("managed_tablet",
            () -> BlockEntityType.Builder.of(ManagedTabletBlockEntity::new, BlockRegistry.MANAGED_TABLET.get()).build(null));
    public static final RegistryObject<BlockEntityType<RackBlockEntity>> RACK_FRAME = TILE_TYPES.register("rack_frame",
            () -> BlockEntityType.Builder.of(RackBlockEntity::new,
                    BlockRegistry.RACK_FRAME_12U.get(), BlockRegistry.RACK_FRAME_18U.get(),
                    BlockRegistry.RACK_WALL_6U.get()).build(null));

    public static final RegistryObject<BlockEntityType<?>> REMOTE_CONTROLLER = TILE_TYPES.register("rctrl",
            () -> BlockEntityType.Builder.of(RemoteControlBlockEntity::new, BlockRegistry.REMOTE_CONTROLLER_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<?>> REDSTONE_CONTROLLER = TILE_TYPES.register("redctrl",
            () -> BlockEntityType.Builder.of(RedstoneControlBlockEntity::new, BlockRegistry.REDSTONE_CONTROL_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<?>> SERVER = TILE_TYPES.register("server",
            () -> BlockEntityType.Builder.of(ServerBlockEntity::new, BlockRegistry.SERVER_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<WaterCoolerBlockEntity>> WATER_COOLER = TILE_TYPES.register("water_cooler",
            () -> BlockEntityType.Builder.of(WaterCoolerBlockEntity::new, BlockRegistry.WATER_COOLER.get()).build(null));

    public static final RegistryObject<BlockEntityType<DigitalClockBlockEntity>> DIGITAL_CLOCK = TILE_TYPES.register("digital_clock",
            () -> BlockEntityType.Builder.of(DigitalClockBlockEntity::new, BlockRegistry.DIGITAL_CLOCK.get(), BlockRegistry.DIGITAL_CLOCK_WHITE.get()).build(null));

    public static void init(IEventBus bus) {
        TILE_TYPES.register(bus);
    }
}
