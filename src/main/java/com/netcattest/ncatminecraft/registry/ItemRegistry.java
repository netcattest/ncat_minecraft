package com.netcattest.ncatminecraft.registry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.netcattest.ncatminecraft.block.item.KeyboardItem;
import com.netcattest.ncatminecraft.core.CraftComponent;
import com.netcattest.ncatminecraft.core.DefaultUpgrade;
import com.netcattest.ncatminecraft.item.*;
import com.netcattest.ncatminecraft.entity.RackModuleType;

import java.util.List;
import java.util.Locale;

@SuppressWarnings({"unchecked", "unused"})
public class ItemRegistry {
    public static void init(IEventBus bus) {
        ITEMS.register(bus);
    }

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "ncat_minecraft");

    protected static final RegistryObject<Item>[] COMP_CRAFT_ITEMS = new RegistryObject[CraftComponent.values().length];
    protected static final RegistryObject<Item>[] UPGRADE_ITEMS = new RegistryObject[DefaultUpgrade.values().length];

    public static final RegistryObject<Item> CONFIGURATOR = ITEMS.register("screencfg", () -> new ItemScreenConfigurator(new Item.Properties()));
    public static final RegistryObject<Item> OWNERSHIP_THEIF = ITEMS.register("ownerthief", () -> new ItemOwnershipThief(new Item.Properties()));
    public static final RegistryObject<Item> LINKER = ITEMS.register("linker", () -> new ItemLinker(new Item.Properties()));
    public static final RegistryObject<Item> MINEPAD = ITEMS.register("minepad", () -> new ItemMinePad2(new Item.Properties()));
    public static final RegistryObject<Item> LASER_POINTER = ITEMS.register("laserpointer", () -> new ItemLaserPointer(new Item.Properties()));
    public static final RegistryObject<Item> DATA_CABLE = ITEMS.register("data_cable", () -> new ItemDataCable(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> DATA_PORT = ITEMS.register("data_port", () -> new ItemDataPort(new Item.Properties()));
    public static final RegistryObject<Item> DATA_PORT_TOOL = ITEMS.register("data_port_tool", () -> new ItemDataPortTool(new Item.Properties()));
    public static final RegistryObject<Item> CABLE_ADJUSTER = ITEMS.register("cable_adjuster", () -> new ItemCableAdjuster(new Item.Properties()));
    public static final RegistryObject<Item> USB_CABLE = ITEMS.register("usb_cable", () -> new ItemUsbCable(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> USB_PORT = ITEMS.register("usb_port", () -> new ItemUsbPort(new Item.Properties()));
    public static final RegistryObject<Item> USB_PORT_TOOL = ITEMS.register("usb_port_tool", () -> new ItemUsbPortTool(new Item.Properties()));
    public static final RegistryObject<Item> USB_CABLE_ADJUSTER = ITEMS.register("usb_cable_adjuster", () -> new ItemUsbCableAdjuster(new Item.Properties()));

    static {
        DefaultUpgrade[] defaultUpgrades = DefaultUpgrade.values();
        for (int i = 0; i < defaultUpgrades.length; i++) {
            DefaultUpgrade upgrade = defaultUpgrades[i];
            UPGRADE_ITEMS[i] = ITEMS.register("upgrade_" + upgrade.name().toLowerCase(Locale.ROOT), () -> new ItemUpgrade(upgrade));
        }

        CraftComponent[] components = CraftComponent.values();
        for (int i = 0; i < components.length; i++) {
            CraftComponent cc = components[i];
            COMP_CRAFT_ITEMS[i] = ITEMS.register("craftcomp_" + cc.name().toLowerCase(Locale.ROOT), () -> new ItemCraftComponent(new Item.Properties()));
        }
    }

    public static final RegistryObject<Item> SCREEN = ITEMS.register("screen", () -> new BlockItem(BlockRegistry.SCREEN_BLOCk.get(), new Item.Properties()));
    public static final RegistryObject<Item> SSH_SCREEN = ITEMS.register("ssh_screen", () -> new BlockItem(BlockRegistry.SSH_SCREEN_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> SFTP_SCREEN = ITEMS.register("sftp_screen", () -> new BlockItem(BlockRegistry.SFTP_SCREEN_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOG_SCREEN = ITEMS.register("log_screen", () -> new BlockItem(BlockRegistry.LOG_SCREEN_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> DEVTOOLS_SCREEN = ITEMS.register("devtools_screen", () -> new BlockItem(BlockRegistry.DEVTOOLS_SCREEN_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> WORKSTATION_SCREEN = ITEMS.register("workstation_screen", () -> new BlockItem(BlockRegistry.WORKSTATION_SCREEN_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> TERMINAL_SCREEN = ITEMS.register("terminal_screen", () -> new BlockItem(BlockRegistry.TERMINAL_SCREEN_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> REMOTE_SCREEN = ITEMS.register("remote_screen", () -> new BlockItem(BlockRegistry.REMOTE_SCREEN_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> PROXY_SCREEN = ITEMS.register("proxy_screen", () -> new BlockItem(BlockRegistry.PROXY_SCREEN_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> NETWORK_SWITCH = ITEMS.register("network_switch", () -> new BlockItem(BlockRegistry.NETWORK_SWITCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> MANAGED_SWITCH = ITEMS.register("managed_switch", () -> new BlockItem(BlockRegistry.MANAGED_SWITCH.get(), new Item.Properties()));
    public static final RegistryObject<Item> MANAGED_TABLET = ITEMS.register("managed_tablet", () -> new ManagedTabletItem(new Item.Properties()));
    public static final RegistryObject<Item> SERIAL_CABLE = ITEMS.register("serial_cable", () -> new com.netcattest.ncatminecraft.item.ItemSerialCable(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> RACK_FRAME_12U = ITEMS.register("rack_frame_12u", () -> new com.netcattest.ncatminecraft.block.item.RackFrameItem(BlockRegistry.RACK_FRAME_12U.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> RACK_FRAME_18U = ITEMS.register("rack_frame_18u", () -> new com.netcattest.ncatminecraft.block.item.RackFrameItem(BlockRegistry.RACK_FRAME_18U.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> RACK_WALL_6U = ITEMS.register("rack_wall_6u", () -> new com.netcattest.ncatminecraft.block.item.RackFrameItem(BlockRegistry.RACK_WALL_6U.get(), new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> RACK_MODULE_BROWSER = ITEMS.register("rack_module_browser", () -> new RackModuleItem(RackModuleType.BROWSER));
    public static final RegistryObject<Item> RACK_MODULE_SSH = ITEMS.register("rack_module_ssh", () -> new RackModuleItem(RackModuleType.SSH));
    public static final RegistryObject<Item> RACK_MODULE_LOG = ITEMS.register("rack_module_log", () -> new RackModuleItem(RackModuleType.LOG));
    public static final RegistryObject<Item> RACK_MODULE_DEVTOOLS = ITEMS.register("rack_module_devtools", () -> new RackModuleItem(RackModuleType.DEVTOOLS));
    public static final RegistryObject<Item> RACK_MODULE_SFTP = ITEMS.register("rack_module_sftp", () -> new RackModuleItem(RackModuleType.SFTP));
    public static final RegistryObject<Item> RACK_MODULE_TERMINAL = ITEMS.register("rack_module_terminal", () -> new RackModuleItem(RackModuleType.TERMINAL));
    public static final RegistryObject<Item> RACK_MODULE_REMOTE = ITEMS.register("rack_module_remote", () -> new RackModuleItem(RackModuleType.REMOTE));
    public static final RegistryObject<Item> RACK_MODULE_PROXY = ITEMS.register("rack_module_proxy", () -> new RackModuleItem(RackModuleType.PROXY));
    public static final RegistryObject<Item> RACK_MODULE_SWITCH = ITEMS.register("rack_module_switch", () -> new RackModuleItem(RackModuleType.SWITCH));
    public static final RegistryObject<Item> RACK_MODULE_MANAGED_SWITCH = ITEMS.register("rack_module_managed_switch", () -> new RackModuleItem(RackModuleType.MANAGED_SWITCH));
    public static final RegistryObject<Item> GAMING_CHAIR = ITEMS.register("gaming_chair", () -> new BlockItem(BlockRegistry.GAMING_CHAIR.get(), new Item.Properties()));
    public static final RegistryObject<Item> TOILET = ITEMS.register("toilet", () -> new BlockItem(BlockRegistry.TOILET.get(), new Item.Properties()) {
        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("item.ncat_minecraft.toilet.lid").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.ncat_minecraft.toilet.flush").withStyle(ChatFormatting.AQUA));
        }
    });
    public static final RegistryObject<Item> WATER_COOLER = ITEMS.register("water_cooler", () -> new BlockItem(BlockRegistry.WATER_COOLER.get(), new Item.Properties()) {
        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("block.ncat_minecraft.water_cooler.hint").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("block.ncat_minecraft.water_cooler.hint2").withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("block.ncat_minecraft.water_cooler.hint3").withStyle(ChatFormatting.DARK_AQUA));
        }
    });
    public static final RegistryObject<Item> GALLON = ITEMS.register("water_gallon", ItemGallon::new);
    public static final RegistryObject<Item> PAPER_CUP = ITEMS.register("paper_cup", () -> new ItemPaperCup(false));
    public static final RegistryObject<Item> PAPER_CUP_WATER = ITEMS.register("paper_cup_water", () -> new ItemPaperCup(true));
    public static final RegistryObject<Item> DIGITAL_CLOCK = ITEMS.register("digital_clock", () -> new BlockItem(BlockRegistry.DIGITAL_CLOCK.get(), new Item.Properties()) {
        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("block.ncat_minecraft.digital_clock.hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    });
    public static final RegistryObject<Item> RUBBER_DUCK = ITEMS.register("rubber_duck", () -> new BlockItem(BlockRegistry.RUBBER_DUCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> ALTA_WIFI = ITEMS.register("alta_wifi", () -> new BlockItem(BlockRegistry.ALTA_WIFI.get(), new Item.Properties()));
    public static final RegistryObject<Item> DIGITAL_CLOCK_WHITE = ITEMS.register("digital_clock_white", () -> new BlockItem(BlockRegistry.DIGITAL_CLOCK_WHITE.get(), new Item.Properties()) {
        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            tooltip.add(Component.translatable("block.ncat_minecraft.digital_clock_white.hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    });

    public static final RegistryObject<Item> KEYBOARD = ITEMS.register("keyboard", () -> new KeyboardItem(BlockRegistry.KEYBOARD_BLOCK.get(), BlockRegistry.blockKbRight.get(), new Item.Properties()));
    public static final RegistryObject<Item> SSH_KEYBOARD = ITEMS.register("ssh_keyboard", () -> new KeyboardItem(BlockRegistry.SSH_KEYBOARD_BLOCK.get(), BlockRegistry.SSH_KEYBOARD_RIGHT.get(), new Item.Properties()));
    public static final RegistryObject<Item> SFTP_KEYBOARD = ITEMS.register("sftp_keyboard", () -> new KeyboardItem(BlockRegistry.SFTP_KEYBOARD_BLOCK.get(), BlockRegistry.SFTP_KEYBOARD_RIGHT.get(), new Item.Properties()));
    public static final RegistryObject<Item> DEVTOOLS_KEYBOARD = ITEMS.register("devtools_keyboard", () -> new KeyboardItem(BlockRegistry.DEVTOOLS_KEYBOARD_BLOCK.get(), BlockRegistry.DEVTOOLS_KEYBOARD_RIGHT.get(), new Item.Properties()));
    public static final RegistryObject<Item> WORKSTATION_KEYBOARD = ITEMS.register("workstation_keyboard", () -> new KeyboardItem(BlockRegistry.WORKSTATION_KEYBOARD_BLOCK.get(), BlockRegistry.WORKSTATION_KEYBOARD_RIGHT.get(), new Item.Properties()));
    public static final RegistryObject<Item> TERMINAL_KEYBOARD = ITEMS.register("terminal_keyboard", () -> new KeyboardItem(BlockRegistry.TERMINAL_KEYBOARD_BLOCK.get(), BlockRegistry.TERMINAL_KEYBOARD_RIGHT.get(), new Item.Properties()));
    public static final RegistryObject<Item> PROXY_KEYBOARD = ITEMS.register("proxy_keyboard", () -> new KeyboardItem(BlockRegistry.PROXY_KEYBOARD_BLOCK.get(), BlockRegistry.PROXY_KEYBOARD_RIGHT.get(), new Item.Properties()));
    public static final RegistryObject<Item> LOG_INSPECTOR = ITEMS.register("log_inspector", () -> new ItemLogInspector(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> LOG_TABLET = ITEMS.register("log_tablet", () -> new ItemLogTablet(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> REDSTONE_CONTROLLER = ITEMS.register("redctrl", () -> new BlockItem(BlockRegistry.REDSTONE_CONTROL_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> REMOTE_CONTROLLER = ITEMS.register("rctrl", () -> new BlockItem(BlockRegistry.REMOTE_CONTROLLER_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> SERVER = ITEMS.register("server", () -> new BlockItem(BlockRegistry.SERVER_BLOCK.get(), new Item.Properties()));

    public static RegistryObject<Item> getComputerCraftItem(int index) {
        return COMP_CRAFT_ITEMS[index];
    }

    public static RegistryObject<Item> getUpgradeItem(int index) {
        return UPGRADE_ITEMS[index];
    }

    public static int countCompCraftItems() {
        return COMP_CRAFT_ITEMS.length;
    }

    public static int countUpgrades() {
        return UPGRADE_ITEMS.length;
    }

    public static boolean isCompCraftItem(Item item) {
        for (RegistryObject<Item> itemRegistryObject : COMP_CRAFT_ITEMS)
            if (item == itemRegistryObject.get())
                return true;
        return false;
    }

    public static Item rackModuleItem(RackModuleType type) {
        return switch (type) {
            case BROWSER -> RACK_MODULE_BROWSER.get();
            case SSH -> RACK_MODULE_SSH.get();
            case LOG -> RACK_MODULE_LOG.get();
            case DEVTOOLS -> RACK_MODULE_DEVTOOLS.get();
            case SFTP -> RACK_MODULE_SFTP.get();
            case TERMINAL -> RACK_MODULE_TERMINAL.get();
            case REMOTE -> RACK_MODULE_REMOTE.get();
            case PROXY -> RACK_MODULE_PROXY.get();
            case SWITCH -> RACK_MODULE_SWITCH.get();
            case MANAGED_SWITCH -> RACK_MODULE_MANAGED_SWITCH.get();
        };
    }
}
