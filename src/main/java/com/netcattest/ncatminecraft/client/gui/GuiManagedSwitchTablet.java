package com.netcattest.ncatminecraft.client.gui;

import com.netcattest.ncatminecraft.entity.ManagedSwitchBlockEntity;
import com.netcattest.ncatminecraft.entity.ManagedTabletBlockEntity;
import com.netcattest.ncatminecraft.entity.NetworkSwitchBlockEntity;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import com.netcattest.ncatminecraft.block.ManagedTabletBlock;
import com.netcattest.ncatminecraft.item.ManagedTabletItem;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.net.server_bound.C2SMessageManagedSwitchConfig;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.utilities.SerialCableService;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class GuiManagedSwitchTablet extends Screen {
    private static final int BACKGROUND = 0xFF07111E;
    private static final int SURFACE = 0xFF101E2D;
    private static final int SURFACE_LIGHT = 0xFF192D40;
    private static final int BORDER = 0xFF2E5068;
    private static final int TEXT = 0xFFE6F3FA;
    private static final int MUTED = 0xFF9CB8C7;
    private static final int ACCENT = 0xFF58D0EA;
    private static final int GREEN = 0xFF5FE1A8;
    private static final int RED = 0xFFFF647C;
    private static final String[] PANELS = {"overview", "ports", "policies", "diagnostics"};

    private final BlockPos tabletPos;
    private final BlockPos switchPos;
    private final boolean handheld;
    private int panel;
    private int selectedPort;
    private int listOffset;
    private int policyOffset;
    private Edit edit = Edit.NONE;
    private final StringBuilder draft = new StringBuilder();
    private long cachedTopologyTick = Long.MIN_VALUE;
    private int cachedDeviceCount;
    private final Map<Integer, String> cachedPeers = new HashMap<>();

    private enum Edit { NONE, LABEL, VLAN }

    public GuiManagedSwitchTablet(BlockPos tabletPos, BlockPos switchPos, boolean handheld) {
        super(Component.literal("NCAT // SWITCH"));
        this.tabletPos = tabletPos;
        this.switchPos = switchPos;
        this.handheld = handheld;
    }

    @Override
    protected void init() {
        super.init();
        ManagedTabletBlockEntity tablet = tablet();
        if (tablet != null) {
            for (int i = 0; i < PANELS.length; i++)
                if (PANELS[i].equals(tablet.activePanel())) panel = i;
            selectedPort = tablet.selectedPort();
            listOffset = Math.max(0, Math.min(8 - visibleRows(), selectedPort));
        }
    }

    private boolean pt() {
        return minecraft != null && minecraft.getLanguageManager().getSelected().toLowerCase().startsWith("pt");
    }

    private String t(String portuguese, String english) {
        return pt() ? portuguese : english;
    }

    private ManagedTabletBlockEntity tablet() {
        if (minecraft == null || minecraft.level == null || !minecraft.level.hasChunkAt(tabletPos)) return null;
        return minecraft.level.getBlockEntity(tabletPos) instanceof ManagedTabletBlockEntity tablet ? tablet : null;
    }

    private ManagedSwitchBlockEntity networkSwitch() {
        ManagedTabletBlockEntity tablet = tablet();
        if (tablet == null) return null;
        if (minecraft.player == null ||
                minecraft.player.distanceToSqr(tabletPos.getX() + .5D, tabletPos.getY() + .5D,
                        tabletPos.getZ() + .5D) > 12D * 12D) return null;
        boolean docked = tablet.getBlockState().getValue(ManagedTabletBlock.DOCKED);
        if (handheld ? docked || !ManagedTabletItem.isHeldBy(minecraft.player, minecraft.level, tablet) : !docked)
            return null;
        ManagedSwitchBlockEntity networkSwitch = SerialCableService.connectedSwitch(minecraft.level, tablet);
        return networkSwitch != null && networkSwitch.getBlockPos().equals(switchPos) ? networkSwitch : null;
    }

    private boolean editable(ManagedSwitchBlockEntity networkSwitch) {
        return networkSwitch != null && minecraft != null && minecraft.player != null &&
                networkSwitch.canConfigure(minecraft.player);
    }

    private int left() { return 10; }
    private int right() { return width - 10; }
    private int top() { return 10; }
    private int bottom() { return height - 10; }
    private int contentTop() { return 74; }
    private int contentBottom() { return height - 31; }
    private int sideWidth() { return Math.min(196, Math.max(99, (right() - left()) * 38 / 100)); }
    private int sideRight() { return left() + sideWidth(); }
    private int visibleRows() { return Math.max(1, Math.min(8, (contentBottom() - contentTop() - 29) / 23)); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(0, 0, width, height, BACKGROUND);
        int l = left(), r = right(), b = bottom();
        graphics.fill(l, top(), r, b, 0xFF0C1927);
        graphics.fill(l, top(), r, top() + 3, ACCENT);
        graphics.fill(l, 13, r, 48, SURFACE);
        graphics.drawString(font, "NCAT // SWITCH", l + 10, 22, TEXT, false);
        graphics.drawString(font, t("GERENCIAMENTO", "MANAGEMENT"), l + 10, 35, MUTED, false);
        ManagedSwitchBlockEntity networkSwitch = networkSwitch();
        boolean online = networkSwitch != null && networkSwitch.powered();
        graphics.fill(r - 13, 24, r - 6, 31, online ? GREEN : RED);
        String status = networkSwitch == null ? t("SEM CABO", "NO CABLE") : online ?
                t("ATIVO", "ACTIVE") : t("DESLIGADO", "OFFLINE");
        graphics.drawString(font, status, r - 20 - font.width(status), 23, online ? GREEN : MUTED, false);
        drawNavigation(graphics, mouseX, mouseY);
        if (networkSwitch == null) {
            card(graphics, l + 8, contentTop(), r - 8, contentBottom());
            centered(graphics, t("Conecte o cabo serial ao switch gerenciável.",
                    "Connect the serial cable to the managed switch."),
                    (l + r) / 2, (contentTop() + contentBottom()) / 2 - 5, TEXT);
        } else {
            switch (panel) {
                case 1 -> renderPorts(graphics, networkSwitch, mouseX, mouseY, false);
                case 2 -> renderPorts(graphics, networkSwitch, mouseX, mouseY, true);
                case 3 -> renderDiagnostics(graphics, networkSwitch);
                default -> renderOverview(graphics, networkSwitch, mouseX, mouseY);
            }
        }
        graphics.fill(l, height - 28, r, height - 10, SURFACE);
        String footer = networkSwitch == null ? t("Tablet desconectado", "Tablet disconnected") :
                t("Revisão ", "Revision ") + networkSwitch.configurationRevision() + "  •  " +
                (editable(networkSwitch) ? t("configuração autorizada", "configuration enabled") :
                        t("somente leitura", "read only"));
        drawEllipsized(graphics, footer, l + 8, height - 23, r - l - 16, MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawNavigation(GuiGraphics graphics, int mouseX, int mouseY) {
        String[] labels = pt() ? new String[] {"Visão geral", "Portas", "Políticas", "Diagnóstico"} :
                new String[] {"Overview", "Ports", "Policies", "Diagnostics"};
        int l = left() + 6;
        int total = right() - left() - 12;
        for (int i = 0; i < 4; i++) {
            int x0 = l + total * i / 4;
            int x1 = l + total * (i + 1) / 4 - 3;
            boolean active = panel == i;
            graphics.fill(x0, 53, x1, 68, active ? 0xFF17465A : SURFACE);
            if (active) graphics.fill(x0, 67, x1, 69, ACCENT);
            drawEllipsized(graphics, labels[i], x0 + 5, 57, x1 - x0 - 9, active ? TEXT : MUTED);
        }
    }

    private void renderOverview(GuiGraphics graphics, ManagedSwitchBlockEntity networkSwitch,
                                int mouseX, int mouseY) {
        int l = left() + 7, r = right() - 7, y = contentTop();
        int wide = r - l;
        int row = Math.min(54, Math.max(37, (contentBottom() - y) / 3));
        card(graphics, l, y, r, y + row);
        graphics.drawString(font, t("ESTADO DA REDE", "NETWORK STATE"), l + 9, y + 8, ACCENT, false);
        graphics.drawString(font, networkSwitch.powered() ? t("Switch ligado", "Switch online") :
                t("Switch desligado", "Switch offline"), l + 9, y + 23, TEXT, false);
        if (editable(networkSwitch)) {
            int bx0 = r - Math.min(91, wide / 3) - 8;
            button(graphics, bx0, y + 10, r - 8, y + 32,
                    networkSwitch.powered() ? t("Desligar", "Power off") : t("Ligar", "Power on"),
                    mouseX, mouseY, networkSwitch.powered() ? RED : GREEN);
        }
        y += row + 7;
        card(graphics, l, y, r, y + row);
        int connected = networkSwitch.connectedCount();
        graphics.drawString(font, t("PORTAS", "PORTS"), l + 9, y + 8, ACCENT, false);
        graphics.drawString(font, connected + " / 8 " + t("conectadas", "connected"), l + 9, y + 23, TEXT, false);
        String ports = "";
        for (int i = 0; i < 8; i++) if (networkSwitch.activeLink(i)) ports += "eth" + i + "  ";
        drawEllipsized(graphics, ports.isEmpty() ? t("Nenhum cabo de dados", "No data cables") : ports,
                l + Math.min(155, wide / 2), y + 23, Math.max(1, wide - Math.min(155, wide / 2) - 8), MUTED);
        y += row + 7;
        card(graphics, l, y, r, contentBottom());
        graphics.drawString(font, t("TOPOLOGIA", "TOPOLOGY"), l + 9, y + 8, ACCENT, false);
        updateTopologyCache(networkSwitch);
        graphics.drawString(font, cachedDeviceCount + " " + t("telas vistas nas portas", "screens seen across ports"),
                l + 9, y + 23, TEXT, false);
        drawEllipsized(graphics, t("Cadeias com switches não gerenciáveis aparecem nas portas de uplink.",
                "Unmanaged switch chains appear on uplink ports."), l + 9, y + 38, wide - 18, MUTED);
    }

    private void renderPorts(GuiGraphics graphics, ManagedSwitchBlockEntity networkSwitch,
                             int mouseX, int mouseY, boolean policy) {
        int l = left() + 7, r = right() - 7, top = contentTop(), bottom = contentBottom();
        int split = sideRight();
        card(graphics, l, top, split - 3, bottom);
        card(graphics, split + 4, top, r, bottom);
        graphics.drawString(font, policy ? t("PORTA DE ORIGEM", "SOURCE PORT") :
                t("PORTAS FÍSICAS", "PHYSICAL PORTS"), l + 7, top + 7, ACCENT, false);
        int rows = visibleRows();
        for (int row = 0; row < rows; row++) {
            int index = listOffset + row;
            if (index >= 8) break;
            int y = top + 22 + row * 23;
            graphics.fill(l + 5, y, split - 8, y + 21, selectedPort == index ? SURFACE_LIGHT : SURFACE);
            graphics.fill(l + 8, y + 7, l + 13, y + 12,
                    networkSwitch.portEnabled(index) && networkSwitch.activeLink(index) ? GREEN :
                            networkSwitch.activeLink(index) ? RED : MUTED);
            String label = NetworkSwitchBlockEntity.portName(index) +
                    (networkSwitch.portLabel(index).isEmpty() ? "" : "  " + networkSwitch.portLabel(index));
            drawEllipsized(graphics, label, l + 19, y + 7, split - l - 29, TEXT);
        }
        if (rows < 8) graphics.drawString(font, "" + (listOffset + 1) + "–" + Math.min(8, listOffset + rows) + "/8",
                l + 8, bottom - 11, MUTED, false);
        int x = split + 11;
        int innerWidth = r - x - 7;
        graphics.drawString(font, "eth" + selectedPort + "  " +
                (policy ? t("REGRAS DE ACESSO", "ACCESS RULES") : t("CONFIGURAÇÃO", "CONFIGURATION")),
                x, top + 7, ACCENT, false);
        if (policy) {
            renderPolicies(graphics, networkSwitch, x, top + 23, innerWidth, mouseX, mouseY);
            return;
        }
        String peer = peerDescription(networkSwitch, selectedPort);
        drawEllipsized(graphics, peer, x, top + 22, innerWidth, MUTED);
        int dy = top + 36;
        drawField(graphics, x, dy, innerWidth, t("Nome", "Name"),
                edit == Edit.LABEL ? draft.toString() + "_" : networkSwitch.portLabel(selectedPort), mouseX, mouseY);
        dy += 20;
        drawField(graphics, x, dy, innerWidth, "VLAN", edit == Edit.VLAN ? draft.toString() + "_" :
                String.valueOf(networkSwitch.portVlan(selectedPort)), mouseX, mouseY);
        dy += 21;
        drawToggle(graphics, x, dy, innerWidth, t("Porta ativa", "Port enabled"),
                networkSwitch.portEnabled(selectedPort), mouseX, mouseY);
        dy += 18;
        drawToggle(graphics, x, dy, innerWidth, t("Trunk VLAN", "VLAN trunk"),
                networkSwitch.portTrunk(selectedPort), mouseX, mouseY);
        dy += 18;
        drawToggle(graphics, x, dy, innerWidth, t("Isolar de outras portas isoladas", "Isolate from isolated ports"),
                networkSwitch.portIsolated(selectedPort), mouseX, mouseY);
    }

    private void renderPolicies(GuiGraphics graphics, ManagedSwitchBlockEntity networkSwitch,
                                int x, int y, int w, int mouseX, int mouseY) {
        drawEllipsized(graphics, t("Permissões entre eth" + selectedPort + " e cada porta:",
                "Traffic between eth" + selectedPort + " and each port:"), x, y, w, MUTED);
        int visible = visiblePolicies();
        for (int row = 0; row < visible; row++) {
            int ordinal = policyOffset + row;
            if (ordinal >= 7) break;
            int i = ordinal < selectedPort ? ordinal : ordinal + 1;
            int yy = y + 18 + row * 18;
            drawToggle(graphics, x, yy, w, "eth" + selectedPort + "  ⇄  eth" + i,
                    networkSwitch.pairAllowed(selectedPort, i), mouseX, mouseY);
        }
        if (visible < 7) drawEllipsized(graphics,
                (policyOffset + 1) + "–" + Math.min(7, policyOffset + visible) + "/7  " +
                        t("Role para ver mais regras", "Scroll for more rules"),
                x, contentBottom() - 11, w, MUTED);
    }

    private int visiblePolicies() {
        return Math.max(1, Math.min(7, (contentBottom() - contentTop() - 41) / 18));
    }

    private void renderDiagnostics(GuiGraphics graphics, ManagedSwitchBlockEntity networkSwitch) {
        int l = left() + 7, r = right() - 7, top = contentTop(), bottom = contentBottom();
        card(graphics, l, top, r, bottom);
        graphics.drawString(font, t("DIAGNÓSTICO DE PORTAS", "PORT DIAGNOSTICS"), l + 8, top + 8, ACCENT, false);
        int rows = Math.max(1, Math.min(8, (bottom - top - 52) / 16));
        for (int row = 0; row < rows; row++) {
            int port = listOffset + row;
            if (port >= 8) break;
            int y = top + 25 + row * 16;
            graphics.fill(l + 7, y + 2, l + 12, y + 7,
                    networkSwitch.portEnabled(port) && networkSwitch.activeLink(port) ? GREEN : RED);
            String line = "eth" + port + "  " + peerDescription(networkSwitch, port) + "  ·  " +
                    networkSwitch.portEvents(port) + " " + t("eventos", "events");
            drawEllipsized(graphics, line, l + 17, y + 1, r - l - 27, TEXT);
        }
        int noticeY = bottom - 23;
        drawEllipsized(graphics, t("Regras locais não controlam tráfego dentro de um switch não gerenciável.",
                "Local rules cannot control traffic within an unmanaged switch."),
                l + 8, noticeY, r - l - 16, MUTED);
    }

    private String peerDescription(ManagedSwitchBlockEntity networkSwitch, int port) {
        updateTopologyCache(networkSwitch);
        return cachedPeers.getOrDefault(port, "—");
    }

    private void updateTopologyCache(ManagedSwitchBlockEntity networkSwitch) {
        Level level = networkSwitch.getLevel();
        if (level == null) return;
        long tick = level.getGameTime();
        if (cachedTopologyTick != Long.MIN_VALUE && tick >= cachedTopologyTick &&
                tick - cachedTopologyTick < 20) return;
        cachedTopologyTick = tick;
        cachedDeviceCount = 0;
        cachedPeers.clear();
        Set<DataCableService.ScreenEndpoint> allScreens = new HashSet<>();
        Set<DataCableService.RackModuleEndpoint> allModules = new HashSet<>();
        for (int port = 0; port < 8; port++) {
            List<DataCableService.ScreenEndpoint> screens = DataCableService.physicalScreensBehindSwitchPort(level, networkSwitch, port);
            List<DataCableService.RackModuleEndpoint> modules = DataCableService.physicalRackModulesBehindSwitchPort(level, networkSwitch, port);
            int count = screens.size();
            int rackCount = modules.size();
            allScreens.addAll(screens);
            allModules.addAll(modules);
            if (networkSwitch.getPort(port) == null) continue;
            DataCableService.CableEnd remote = DataCableService.other(level,
                    new DataCableService.SwitchEndpoint(networkSwitch, port, networkSwitch.getPort(port)));
            String description;
            if (remote == null) description = t("Sem conexão", "Disconnected");
            else if (remote instanceof DataCableService.SwitchEndpoint switchEnd) {
                description = (switchEnd.entity() instanceof ManagedSwitchBlockEntity ?
                        t("Switch gerenciável", "Managed switch") : t("Switch não gerenciado", "Unmanaged switch")) +
                        " · eth" + switchEnd.index() + " · " + (count + rackCount) + " " + t("dispositivos", "devices");
            } else if (remote instanceof DataCableService.RackEndpoint rack) {
                if (rack.module() == null) description = t("Módulo de rack", "Rack module");
                else {
                    RackModuleType type = rack.module().type();
                    String name = switch (type) {
                        case BROWSER -> t("Navegador de rack", "Rack browser");
                        case SSH -> "SSH";
                        case LOG -> "Log";
                        case DEVTOOLS -> "DevTools";
                        case SFTP -> "SFTP";
                        case TERMINAL -> t("Terminal de rack", "Rack terminal");
                        case REMOTE -> t("Desktop remoto de rack", "Rack remote desktop");
                        case PROXY -> t("Proxy de rack", "Rack proxy");
                        case SWITCH -> t("Switch de rack", "Rack switch");
                        case MANAGED_SWITCH -> t("Switch gerenciável de rack", "Managed rack switch");
                    };
                    description = name + " · U" + (rack.module().startU() + 1) + " · eth" + rack.index() +
                            (type.isSwitch() ? " · " + (count + rackCount) + " " + t("dispositivos", "devices") : "");
                }
            } else if (remote instanceof DataCableService.Endpoint screen) {
                description = screen.entity().getBlockState().getBlock().getName().getString() + " · " +
                        screen.entity().getBlockPos().toShortString();
            } else description = t("Dispositivo conectado", "Connected device");
            cachedPeers.put(port, description);
        }
        cachedDeviceCount = allScreens.size() + allModules.size();
    }

    private void drawField(GuiGraphics graphics, int x, int y, int w, String title,
                           String value, int mouseX, int mouseY) {
        if (y + 19 > contentBottom()) return;
        graphics.fill(x, y, x + w, y + 19, SURFACE_LIGHT);
        graphics.fill(x, y + 18, x + w, y + 19, BORDER);
        graphics.drawString(font, title + ":", x + 4, y + 5, MUTED, false);
        drawEllipsized(graphics, value.isEmpty() ? "—" : value,
                x + Math.min(72, w / 2), y + 5, Math.max(1, w - Math.min(72, w / 2) - 6), TEXT);
    }

    private void drawToggle(GuiGraphics graphics, int x, int y, int w, String title,
                            boolean on, int mouseX, int mouseY) {
        if (y + 17 > contentBottom()) return;
        graphics.fill(x, y, x + w, y + 17, SURFACE_LIGHT);
        drawEllipsized(graphics, title, x + 5, y + 5, Math.max(1, w - 32), TEXT);
        graphics.fill(x + w - 25, y + 4, x + w - 5, y + 13, on ? 0xFF226A55 : 0xFF713244);
        graphics.fill(x + w - (on ? 13 : 24), y + 5, x + w - (on ? 6 : 17), y + 12, on ? GREEN : RED);
    }

    private void card(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        if (x1 <= x0 || y1 <= y0) return;
        graphics.fill(x0, y0, x1, y1, BORDER);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, SURFACE);
    }

    private void button(GuiGraphics graphics, int x0, int y0, int x1, int y1,
                        String title, int mouseX, int mouseY, int accent) {
        graphics.fill(x0, y0, x1, y1, accent);
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, SURFACE_LIGHT);
        centered(graphics, title, (x0 + x1) / 2, (y0 + y1) / 2 - 4, TEXT);
    }

    private void centered(GuiGraphics graphics, String value, int x, int y, int color) {
        graphics.drawString(font, value, x - font.width(value) / 2, y, color, false);
    }

    private void drawEllipsized(GuiGraphics graphics, String value, int x, int y, int available, int color) {
        if (available < 4) return;
        if (value == null) value = "";
        if (font.width(value) > available) {
            while (!value.isEmpty() && font.width(value + "…") > available)
                value = value.substring(0, value.length() - 1);
            value += "…";
        }
        graphics.drawString(font, value, x, y, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        int x = (int) mouseX, y = (int) mouseY;
        if (y >= 53 && y < 69 && x >= left() + 6 && x < right() - 6) {
            submitEdit();
            panel = Math.min(3, (x - left() - 6) * 4 / Math.max(1, right() - left() - 12));
            listOffset = 0;
            policyOffset = 0;
            send(C2SMessageManagedSwitchConfig.Action.PANEL, -1, -1, 0, PANELS[panel]);
            return true;
        }
        ManagedSwitchBlockEntity networkSwitch = networkSwitch();
        if (networkSwitch == null || !editable(networkSwitch) && panel == 0) return true;
        if (panel == 0) {
            int l = left() + 7, r = right() - 7, row = Math.min(54,
                    Math.max(37, (contentBottom() - contentTop()) / 3));
            int bx0 = r - Math.min(91, (r - l) / 3) - 8;
            if (inside(x, y, bx0, contentTop() + 10, r - 8, contentTop() + 32))
                send(C2SMessageManagedSwitchConfig.Action.POWER, -1, -1,
                        networkSwitch.powered() ? 0 : 1, "");
            return true;
        }
        if (panel == 3) return true;
        int l = left() + 7, split = sideRight(), top = contentTop();
        if (x >= l + 5 && x < split - 8 && y >= top + 22) {
            int row = (y - top - 22) / 23;
            if (row >= 0 && row < visibleRows() && listOffset + row < 8) {
                submitEdit();
                selectedPort = listOffset + row;
                send(C2SMessageManagedSwitchConfig.Action.SELECT_PORT, selectedPort, -1, 0, "");
                return true;
            }
        }
        if (!editable(networkSwitch)) return true;
        int detailX = split + 11, detailRight = right() - 14;
        if (x < detailX || x >= detailRight || y < top + 22) return true;
        if (panel == 2) {
            int row = (y - top - 41) / 18;
            if (y >= top + 41 && row >= 0 && row < visiblePolicies()) {
                int ordinal = policyOffset + row;
                int other = ordinal < selectedPort ? ordinal : ordinal + 1;
                send(C2SMessageManagedSwitchConfig.Action.PAIR_ALLOWED, selectedPort, other,
                        networkSwitch.pairAllowed(selectedPort, other) ? 0 : 1, "");
            }
            return true;
        }
        if (inside(x, y, detailX, top + 36, detailRight, top + 55)) {
            beginEdit(Edit.LABEL, networkSwitch.portLabel(selectedPort));
        } else if (inside(x, y, detailX, top + 56, detailRight, top + 75)) {
            beginEdit(Edit.VLAN, Integer.toString(networkSwitch.portVlan(selectedPort)));
        } else if (inside(x, y, detailX, top + 77, detailRight, top + 94)) {
            send(C2SMessageManagedSwitchConfig.Action.PORT_ENABLED, selectedPort, -1,
                    networkSwitch.portEnabled(selectedPort) ? 0 : 1, "");
        } else if (inside(x, y, detailX, top + 95, detailRight, top + 112)) {
            send(C2SMessageManagedSwitchConfig.Action.PORT_TRUNK, selectedPort, -1,
                    networkSwitch.portTrunk(selectedPort) ? 0 : 1, "");
        } else if (inside(x, y, detailX, top + 113, detailRight, top + 130)) {
            send(C2SMessageManagedSwitchConfig.Action.PORT_ISOLATION, selectedPort, -1,
                    networkSwitch.portIsolated(selectedPort) ? 0 : 1, "");
        }
        return true;
    }

    private static boolean inside(int x, int y, int x0, int y0, int x1, int y1) {
        return x >= x0 && x < x1 && y >= y0 && y < y1;
    }

    private void beginEdit(Edit target, String initial) {
        edit = target;
        draft.setLength(0);
        draft.append(initial);
    }

    private void submitEdit() {
        if (edit == Edit.LABEL) {
            send(C2SMessageManagedSwitchConfig.Action.PORT_LABEL, selectedPort, -1, 0, draft.toString());
        } else if (edit == Edit.VLAN) {
            try {
                int vlan = Integer.parseInt(draft.toString());
                if (vlan >= 1 && vlan <= 4094)
                    send(C2SMessageManagedSwitchConfig.Action.PORT_VLAN, selectedPort, -1, vlan, "");
            } catch (NumberFormatException ignored) { }
        }
        edit = Edit.NONE;
        draft.setLength(0);
    }

    private void send(C2SMessageManagedSwitchConfig.Action action, int port, int other, int value, String text) {
        ManagedSwitchBlockEntity networkSwitch = networkSwitch();
        if (networkSwitch == null || !editable(networkSwitch)) return;
        WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageManagedSwitchConfig(
                tabletPos, switchPos, action, port, other, value, text));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (edit != Edit.NONE) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                edit = Edit.NONE;
                draft.setLength(0);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                submitEdit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !draft.isEmpty()) {
                draft.deleteCharAt(draft.length() - 1);
                return true;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (edit == Edit.NONE) return super.charTyped(codePoint, modifiers);
        if (edit == Edit.LABEL && codePoint >= 32 && codePoint != 127 && draft.length() < 24)
            draft.append(codePoint);
        if (edit == Edit.VLAN && codePoint >= '0' && codePoint <= '9' && draft.length() < 4)
            draft.append(codePoint);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (panel == 2 && mouseX >= sideRight()) {
            policyOffset = Math.max(0, Math.min(7 - visiblePolicies(),
                    policyOffset - (amount > 0 ? 1 : -1)));
            return true;
        }
        if (panel == 1 || panel == 2 || panel == 3) {
            int rows = panel == 3 ? Math.max(1, Math.min(8,
                    (contentBottom() - contentTop() - 52) / 16)) : visibleRows();
            listOffset = Math.max(0, Math.min(8 - rows, listOffset - (amount > 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void onClose() {
        submitEdit();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
