package com.netcattest.ncatminecraft.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.netcattest.ncatminecraft.block.KeyboardBlockLeft;
import com.netcattest.ncatminecraft.block.KeyboardBlockRight;
import com.netcattest.ncatminecraft.block.PeripheralBlock;
import com.netcattest.ncatminecraft.block.ScreenBlock;
import com.netcattest.ncatminecraft.block.GamingChairBlock;
import com.netcattest.ncatminecraft.block.ToiletBlock;
import com.netcattest.ncatminecraft.block.DigitalClockBlock;
import com.netcattest.ncatminecraft.block.AltaWifiBlock;
import com.netcattest.ncatminecraft.block.RubberDuckBlock;
import com.netcattest.ncatminecraft.block.WaterCoolerBlock;
import com.netcattest.ncatminecraft.block.NetworkSwitchBlock;
import com.netcattest.ncatminecraft.block.ManagedSwitchBlock;
import com.netcattest.ncatminecraft.block.ManagedTabletBlock;
import com.netcattest.ncatminecraft.block.RackFrameBlock;
import com.netcattest.ncatminecraft.core.DefaultPeripheral;

public class BlockRegistry {
    public static void init(IEventBus bus) {
        BLOCKS.register(bus);
    }

    public static DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "ncat_minecraft");

    public static final RegistryObject<ScreenBlock> SCREEN_BLOCk = BLOCKS.register("screen", () -> new ScreenBlock(BlockBehaviour.Properties.copy(Blocks.STONE)));
    public static final RegistryObject<ScreenBlock> SSH_SCREEN_BLOCK = BLOCKS.register("ssh_screen", () -> new ScreenBlock(BlockBehaviour.Properties.copy(Blocks.STONE)));
    public static final RegistryObject<ScreenBlock> SFTP_SCREEN_BLOCK = BLOCKS.register("sftp_screen", () -> new ScreenBlock(BlockBehaviour.Properties.copy(Blocks.STONE)));
    public static final RegistryObject<ScreenBlock> LOG_SCREEN_BLOCK = BLOCKS.register("log_screen", () -> new ScreenBlock(BlockBehaviour.Properties.copy(Blocks.STONE)));
    public static final RegistryObject<ScreenBlock> DEVTOOLS_SCREEN_BLOCK = BLOCKS.register("devtools_screen", () -> new ScreenBlock(BlockBehaviour.Properties.copy(Blocks.STONE)));
    public static final RegistryObject<ScreenBlock> WORKSTATION_SCREEN_BLOCK = BLOCKS.register("workstation_screen", () -> new ScreenBlock(BlockBehaviour.Properties.copy(Blocks.STONE)));
    public static final RegistryObject<ScreenBlock> TERMINAL_SCREEN_BLOCK = BLOCKS.register("terminal_screen", () -> new ScreenBlock(BlockBehaviour.Properties.copy(Blocks.STONE)));
    public static final RegistryObject<ScreenBlock> REMOTE_SCREEN_BLOCK = BLOCKS.register("remote_screen", () -> new ScreenBlock(BlockBehaviour.Properties.copy(Blocks.STONE)));
    public static final RegistryObject<ScreenBlock> PROXY_SCREEN_BLOCK = BLOCKS.register("proxy_screen", () -> new ScreenBlock(BlockBehaviour.Properties.copy(Blocks.STONE)));
    public static final RegistryObject<NetworkSwitchBlock> NETWORK_SWITCH = BLOCKS.register("network_switch", NetworkSwitchBlock::new);
    public static final RegistryObject<ManagedSwitchBlock> MANAGED_SWITCH = BLOCKS.register("managed_switch", ManagedSwitchBlock::new);
    public static final RegistryObject<ManagedTabletBlock> MANAGED_TABLET = BLOCKS.register("managed_tablet", ManagedTabletBlock::new);
    public static final RegistryObject<RackFrameBlock> RACK_FRAME_12U = BLOCKS.register("rack_frame_12u", () -> new RackFrameBlock(18, RackFrameBlock.Role.BASE));
    public static final RegistryObject<RackFrameBlock> RACK_FRAME_18U = BLOCKS.register("rack_frame_18u", () -> new RackFrameBlock(24, RackFrameBlock.Role.EXTENSION));
    public static final RegistryObject<RackFrameBlock> RACK_WALL_6U = BLOCKS.register("rack_wall_6u", () -> new RackFrameBlock(6, RackFrameBlock.Role.STANDALONE));
    public static final RegistryObject<GamingChairBlock> GAMING_CHAIR = BLOCKS.register("gaming_chair", GamingChairBlock::new);
    public static final RegistryObject<ToiletBlock> TOILET = BLOCKS.register("toilet", ToiletBlock::new);
    public static final RegistryObject<WaterCoolerBlock> WATER_COOLER = BLOCKS.register("water_cooler", WaterCoolerBlock::new);
    public static final RegistryObject<DigitalClockBlock> DIGITAL_CLOCK = BLOCKS.register("digital_clock", () -> new DigitalClockBlock(false));
    public static final RegistryObject<DigitalClockBlock> DIGITAL_CLOCK_WHITE = BLOCKS.register("digital_clock_white", () -> new DigitalClockBlock(true));
    public static final RegistryObject<RubberDuckBlock> RUBBER_DUCK = BLOCKS.register("rubber_duck", RubberDuckBlock::new);
    public static final RegistryObject<AltaWifiBlock> ALTA_WIFI = BLOCKS.register("alta_wifi", AltaWifiBlock::new);
    public static boolean isSshScreen(Block block) {
        return block == SSH_SCREEN_BLOCK.get();
    }

    public static boolean isSftpScreen(Block block) {
        return block == SFTP_SCREEN_BLOCK.get();
    }

    public static boolean isLogScreen(Block block) {
        return block == LOG_SCREEN_BLOCK.get();
    }

    public static boolean isDevToolsScreen(Block block) {
        return block == DEVTOOLS_SCREEN_BLOCK.get();
    }

    public static boolean isBrowserScreen(Block block) {
        return block == SCREEN_BLOCk.get();
    }

    public static boolean isWorkstationScreen(Block block) {
        return block == WORKSTATION_SCREEN_BLOCK.get();
    }

    public static boolean isTerminalScreen(Block block) {
        return block == TERMINAL_SCREEN_BLOCK.get();
    }

    public static boolean isRemoteScreen(Block block) {
        return block == REMOTE_SCREEN_BLOCK.get();
    }

    public static boolean isProxyScreen(Block block) {
        return block == PROXY_SCREEN_BLOCK.get();
    }

    public static boolean isLocalScreen(Block block) {
        return isSshScreen(block) || isSftpScreen(block) || isLogScreen(block) || isDevToolsScreen(block)
                || isWorkstationScreen(block) || isTerminalScreen(block) || isRemoteScreen(block)
                || isProxyScreen(block);
    }

    public static String screenPage(Block block) {
        if (isSshScreen(block)) return "mod://ncat_minecraft/ssh.html";
        if (isSftpScreen(block)) return "mod://ncat_minecraft/sftp.html";
        if (isLogScreen(block)) return "mod://ncat_minecraft/log.html";
        if (isDevToolsScreen(block)) return "mod://ncat_minecraft/devtools.html";
        if (isWorkstationScreen(block)) return "mod://ncat_minecraft/workstation.html";
        if (isTerminalScreen(block)) return "mod://ncat_minecraft/terminal.html";
        if (isRemoteScreen(block)) return "mod://ncat_minecraft/remote.html";
        if (isProxyScreen(block)) return "mod://ncat_minecraft/proxy.html";
        return null;
    }

    public static final RegistryObject<KeyboardBlockLeft> KEYBOARD_BLOCK = BlockRegistry.BLOCKS.register("kb_left", KeyboardBlockLeft::new);
    public static final RegistryObject<KeyboardBlockRight> blockKbRight = BLOCKS.register("kb_right", KeyboardBlockRight::new);
    public static final RegistryObject<KeyboardBlockLeft> SSH_KEYBOARD_BLOCK = BLOCKS.register("ssh_kb_left", KeyboardBlockLeft::new);
    public static final RegistryObject<KeyboardBlockRight> SSH_KEYBOARD_RIGHT = BLOCKS.register("ssh_kb_right", KeyboardBlockRight::new);
    public static final RegistryObject<KeyboardBlockLeft> SFTP_KEYBOARD_BLOCK = BLOCKS.register("sftp_kb_left", KeyboardBlockLeft::new);
    public static final RegistryObject<KeyboardBlockRight> SFTP_KEYBOARD_RIGHT = BLOCKS.register("sftp_kb_right", KeyboardBlockRight::new);
    public static final RegistryObject<KeyboardBlockLeft> DEVTOOLS_KEYBOARD_BLOCK = BLOCKS.register("devtools_kb_left", KeyboardBlockLeft::new);
    public static final RegistryObject<KeyboardBlockRight> DEVTOOLS_KEYBOARD_RIGHT = BLOCKS.register("devtools_kb_right", KeyboardBlockRight::new);
    public static final RegistryObject<KeyboardBlockLeft> WORKSTATION_KEYBOARD_BLOCK = BLOCKS.register("workstation_kb_left", KeyboardBlockLeft::new);
    public static final RegistryObject<KeyboardBlockLeft> TERMINAL_KEYBOARD_BLOCK = BLOCKS.register("terminal_kb_left", KeyboardBlockLeft::new);
    public static final RegistryObject<KeyboardBlockRight> WORKSTATION_KEYBOARD_RIGHT = BLOCKS.register("workstation_kb_right", KeyboardBlockRight::new);
    public static final RegistryObject<KeyboardBlockRight> TERMINAL_KEYBOARD_RIGHT = BLOCKS.register("terminal_kb_right", KeyboardBlockRight::new);
    public static final RegistryObject<KeyboardBlockLeft> PROXY_KEYBOARD_BLOCK = BLOCKS.register("proxy_kb_left", KeyboardBlockLeft::new);
    public static final RegistryObject<KeyboardBlockRight> PROXY_KEYBOARD_RIGHT = BLOCKS.register("proxy_kb_right", KeyboardBlockRight::new);

    public static boolean matchesKeyboardHalves(Block left, Block right) {
        return left == KEYBOARD_BLOCK.get() && right == blockKbRight.get()
                || left == SSH_KEYBOARD_BLOCK.get() && right == SSH_KEYBOARD_RIGHT.get()
                || left == SFTP_KEYBOARD_BLOCK.get() && right == SFTP_KEYBOARD_RIGHT.get()
                || left == DEVTOOLS_KEYBOARD_BLOCK.get() && right == DEVTOOLS_KEYBOARD_RIGHT.get()
                || left == WORKSTATION_KEYBOARD_BLOCK.get() && right == WORKSTATION_KEYBOARD_RIGHT.get()
                || left == TERMINAL_KEYBOARD_BLOCK.get() && right == TERMINAL_KEYBOARD_RIGHT.get()
                || left == PROXY_KEYBOARD_BLOCK.get() && right == PROXY_KEYBOARD_RIGHT.get();
    }

    public static final RegistryObject<PeripheralBlock> REDSTONE_CONTROL_BLOCK = BlockRegistry.BLOCKS.register("redctrl", () -> new PeripheralBlock(DefaultPeripheral.REDSTONE_CONTROLLER));
    public static final RegistryObject<PeripheralBlock> REMOTE_CONTROLLER_BLOCK = BlockRegistry.BLOCKS.register("rctrl", () -> new PeripheralBlock(DefaultPeripheral.REMOTE_CONTROLLER));
    public static final RegistryObject<PeripheralBlock> SERVER_BLOCK = BlockRegistry.BLOCKS.register("server", () -> new PeripheralBlock(DefaultPeripheral.SERVER));
}
