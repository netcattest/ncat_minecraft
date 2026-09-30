package com.netcattest.ncatminecraft.client.rack;

import com.netcattest.ncatminecraft.entity.RackBlockEntity;
import com.netcattest.ncatminecraft.entity.RackModule;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import com.netcattest.ncatminecraft.item.ItemDataCable;
import com.netcattest.ncatminecraft.item.RackModuleItem;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.net.server_bound.C2SMessageRackAction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public final class GuiRack extends Screen {
    private static final int BG = 0xFF07111C;
    private static final int PANEL = 0xFF101E2C;
    private static final int ROW = 0xFF16283A;
    private static final int EDGE = 0xFF3A5A70;
    private static final int TEXT = 0xFFE8F4FA;
    private static final int MUTED = 0xFF9DB5C4;
    private static final int CYAN = 0xFF55CBE7;
    private static final int GREEN = 0xFF58D99A;
    private static final int RED = 0xFFE36C78;

    private final BlockPos base;
    private UUID selected;
    private int firstU;
    private boolean portFocus;
    private boolean policyFocus;
    private boolean accessPolicy;
    private boolean vlanEditing;
    private final StringBuilder vlanDraft = new StringBuilder();
    private int selectedPort;
    private int portPage;
    private int accessPage;
    private long focusStarted;

    public GuiRack(BlockPos base) {
        super(Component.literal("NCAT // RACK"));
        this.base = base.immutable();
    }

    private RackBlockEntity rack() {
        if (minecraft == null || minecraft.level == null || !minecraft.level.hasChunkAt(base)) return null;
        return RackBlockEntity.at(minecraft.level, base);
    }

    private boolean usable(RackBlockEntity rack) {
        return rack != null && minecraft != null && minecraft.player != null &&
                minecraft.player.distanceToSqr(base.getX() + .5D, base.getY() + .5D, base.getZ() + .5D) <= 144D &&
                rack.canConfigure(minecraft.player);
    }

    private boolean pt() {
        return minecraft != null && minecraft.getLanguageManager().getSelected().toLowerCase().startsWith("pt");
    }

    private String t(String portuguese, String english) {
        return pt() ? portuguese : english;
    }

    private void send(C2SMessageRackAction.Action action, UUID module, int value) {
        WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageRackAction(base, action, module, value));
    }

    private RackModule selected(RackBlockEntity rack) {
        return rack == null || selected == null ? null : rack.getModule(selected);
    }

    private int top() { return 48; }
    private int bottom() { return height - 34; }
    private int rackLeft() { return 18; }
    private int rackRight() { return Math.min(width / 2 + 22, width - 140); }
    private int detailLeft() { return rackRight() + 10; }
    private int rowHeight() { return 18; }
    private int visibleU() { return Math.max(1, (bottom() - top() - 19) / rowHeight()); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BG);
        RackBlockEntity rack = rack();
        graphics.fill(10, 8, width - 10, 39, PANEL);
        graphics.fill(10, 8, width - 10, 11, CYAN);
        graphics.drawString(font, "NCAT // RACK", 20, 17, TEXT, false);
        if (!usable(rack)) {
            centered(graphics, t("Rack indisponível. Aproxime-se para configurar.", "Rack unavailable. Move closer to configure."),
                    width / 2, height / 2, MUTED);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        int capacity = rack.getCapacityU();
        firstU = Math.min(firstU, Math.max(0, capacity - visibleU()));
        String summary = capacity + "U  /  " + rack.getModules().size() + " " + t("módulos", "modules");
        graphics.drawString(font, summary, width - 20 - font.width(summary), 19, MUTED, false);
        if (portFocus && selected(rack) != null) renderPortFocus(graphics, rack, selected(rack), mouseX, mouseY);
        else if (policyFocus && selected(rack) != null && selected(rack).type() == RackModuleType.MANAGED_SWITCH)
            renderPolicy(graphics, selected(rack), mouseX, mouseY);
        else renderOverview(graphics, rack, mouseX, mouseY);
        graphics.fill(10, height - 28, width - 10, height - 10, PANEL);
        String hint = portFocus ? t("Clique na porta com o cabo na mão · Esc retorna", "Click a port while holding a cable · Esc returns") :
                policyFocus ? t("Políticas do switch · Esc retorna", "Switch policies · Esc returns") :
                t("Shift + clique com módulo ou cabo abre este painel · role para navegar", "Shift + click with a module or cable opens this panel · scroll to browse");
        ellipsis(graphics, hint, 18, height - 23, width - 35, MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderOverview(GuiGraphics graphics, RackBlockEntity rack, int mouseX, int mouseY) {
        int l = rackLeft(), r = rackRight(), top = top(), bottom = bottom();
        graphics.fill(l, top, r, bottom, PANEL);
        graphics.fill(l + 3, top + 3, r - 3, top + 17, ROW);
        graphics.drawString(font, t("GABINETE", "CABINET") + "  " + rack.getCapacityU() + "U", l + 10, top + 7, TEXT, false);
        graphics.drawString(font, rack.doorOpen() ? t("PORTA ABERTA", "DOOR OPEN") : t("VIDRO FECHADO", "GLASS CLOSED"),
                l + 10, bottom - 13, MUTED, false);
        int maxRows = Math.min(visibleU(), rack.getCapacityU() - firstU);
        for (int i = 0; i < maxRows; i++) {
            int u = firstU + i;
            int y = top + 19 + i * rowHeight();
            RackModule module = rack.moduleAtU(u);
            boolean firstRow = module != null && module.startU() == u;
            int color = module == null ? 0xFF10202E : module.powered() ? 0xFF183746 : 0xFF322333;
            graphics.fill(l + 5, y, r - 5, y + rowHeight() - 1, color);
            if (module != null && module.id().equals(selected)) graphics.fill(l + 5, y, l + 8, y + rowHeight() - 1, CYAN);
            String index = String.format("%02dU", u + 1);
            graphics.drawString(font, index, l + 11, y + 5, MUTED, false);
            if (module != null) {
                int led = module.powered() ? GREEN : RED;
                graphics.fill(r - 17, y + 6, r - 12, y + 11, led);
                if (firstRow) ellipsis(graphics, name(module), l + 49, y + 5, r - l - 72, TEXT);
            } else if (mouseX >= l + 5 && mouseX < r - 5 && mouseY >= y && mouseY < y + rowHeight()) {
                graphics.drawString(font, "+", r - 17, y + 4, CYAN, false);
            }
        }
        if (!rack.doorOpen()) graphics.fill(l + 5, top + 19, r - 5, Math.min(bottom - 17, top + 19 + maxRows * rowHeight()), 0x352B5D76);
        int dl = detailLeft(), dr = width - 18;
        graphics.fill(dl, top, dr, bottom, PANEL);
        RackModule module = selected(rack);
        graphics.drawString(font, t("MÓDULO", "MODULE"), dl + 10, top + 9, CYAN, false);
        if (module == null) {
            ellipsis(graphics, t("Escolha um equipamento à esquerda.", "Select a device on the left."), dl + 10, top + 31, dr - dl - 20, MUTED);
            ellipsis(graphics, t("Todos os cabos saem pelas laterais do rack.", "All cables leave through the rack sides."), dl + 10, top + 47, dr - dl - 20, MUTED);
            return;
        }
        ellipsis(graphics, name(module), dl + 10, top + 27, dr - dl - 20, TEXT);
        String location = "U" + (module.startU() + 1) + "  ·  " + module.heightU() + "U  ·  " +
                module.ports().size() + " " + t("portas", "ports");
        ellipsis(graphics, location, dl + 10, top + 43, dr - dl - 20, MUTED);
        int y = top + 64;
        int buttonWidth = (dr - dl - 23) / 2;
        int x1 = dl + 9, x2 = x1 + buttonWidth + 5;
        button(graphics, x1, y, x1 + buttonWidth, y + 18,
                module.powered() ? t("Desligar", "Power off") : t("Ligar", "Power on"),
                mouseX, mouseY, module.powered() ? RED : GREEN);
        button(graphics, x2, y, dr - 9, y + 18, t("Portas", "Ports"), mouseX, mouseY, CYAN);
        button(graphics, x1, y + 23, x1 + buttonWidth, y + 41,
                t("Retirar", "Remove"), mouseX, mouseY, RED);
        button(graphics, x2, y + 23, dr - 9, y + 41,
                rack.doorOpen() ? t("Fechar vidro", "Close glass") : t("Abrir vidro", "Open glass"),
                mouseX, mouseY, CYAN);
        if (module.type() == RackModuleType.MANAGED_SWITCH)
            button(graphics, x1, y + 46, dr - 9, y + 64,
                    t("Configurar switch", "Configure switch"), mouseX, mouseY, CYAN);
    }

    private void renderPortFocus(GuiGraphics graphics, RackBlockEntity rack, RackModule module, int mouseX, int mouseY) {
        int l = 18, r = width - 18, top = top(), bottom = bottom();
        graphics.fill(l, top, r, bottom, PANEL);
        graphics.fill(l + 4, top + 4, r - 4, top + 32, ROW);
        ellipsis(graphics, "←  " + name(module) + "   U" + (module.startU() + 1), l + 14, top + 14, r - l - 154, TEXT);
        if (module.portCount() < module.maxPorts())
            graphics.drawString(font, t("+ porta", "+ port"), r - 136, top + 14, GREEN, false);
        int pages = Math.max(1, (module.portCount() + 3) / 4);
        portPage = Math.min(portPage, pages - 1);
        graphics.drawString(font, "< " + (portPage + 1) + "/" + pages + " >", r - 64, top + 14, CYAN, false);
        int inset = Math.max(9, 30 - (int) ((System.currentTimeMillis() - focusStarted) / 160.0D * 21));
        int frontL = l + inset, frontR = r - inset, frontT = top + 41, frontB = Math.min(bottom - 59, frontT + 54);
        graphics.fill(frontL, frontT, frontR, frontB, 0xFF0A151E);
        graphics.fill(frontL, frontT, frontR, frontT + 2, EDGE);
        graphics.fill(frontL + 12, frontT + 15, frontL + 21, frontT + 24, module.powered() ? GREEN : RED);
        graphics.drawString(font, "NCAT", frontL + 30, frontT + 15, TEXT, false);
        String state = module.powered() ? t("LIGADO", "ONLINE") : t("DESLIGADO", "OFFLINE");
        graphics.drawString(font, state, frontL + 30, frontT + 31, module.powered() ? GREEN : RED, false);
        int n = module.ports().size();
        int portTop = frontB + 8;
        int available = r - l - 24;
        int pageCount = Math.min(4, n - portPage * 4);
        int portWidth = Math.max(24, Math.min(64, (available - 12) / Math.max(1, pageCount) - 4));
        int groupWidth = pageCount * (portWidth + 4) - 4;
        int portLeft = (l + r - groupWidth) / 2;
        for (int index = 0; index < pageCount; index++) {
            int i = portPage * 4 + index;
            int x = portLeft + index * (portWidth + 4);
            int y = portTop;
            boolean connected = module.ports().get(i).connected();
            graphics.fill(x, y, x + portWidth, y + 28, connected ? 0xFF19534F : 0xFF233C50);
            graphics.fill(x + 3, y + 3, x + portWidth - 3, y + 18, 0xFF07131D);
            graphics.fill(x + 7, y + 8, x + portWidth - 7, y + 13, connected ? GREEN : EDGE);
            centered(graphics, "eth" + i, x + portWidth / 2, y + 20, TEXT);
        }
        int dressY = portTop + 34;
        int dressL = portLeft;
        int dressR = portLeft + Math.max(120, groupWidth);
        int side = module.portDress(selectedPort);
        button(graphics, dressL, dressY, dressR, dressY + 18,
                t("Cabo da eth" + selectedPort + ": ", "eth" + selectedPort + " cable: ") + dressLabel(side),
                mouseX, mouseY, side == 0 ? MUTED : CYAN);
        String route = t("Clique numa porta para escolher o lado do cabo · segure o cabo para conectar",
                "Click a port to choose its cable side · hold a cable to connect");
        ellipsis(graphics, route, l + 13, bottom - 16, r - l - 26, MUTED);
    }

    private void renderPolicy(GuiGraphics graphics, RackModule module, int mouseX, int mouseY) {
        int l = 18, r = width - 18, top = top(), bottom = bottom();
        int split = l + Math.max(122, (r - l) * 44 / 100);
        graphics.fill(l, top, r, bottom, PANEL);
        graphics.fill(l + 4, top + 4, r - 4, top + 32, ROW);
        ellipsis(graphics, "←  " + t("SWITCH GERENCIÁVEL", "MANAGED SWITCH"), l + 12, top + 14,
                split - l - 20, TEXT);
        int tabsLeft = Math.max(split + 4, r - 111);
        button(graphics, tabsLeft, top + 8, tabsLeft + 50, top + 26,
                t("Portas", "Ports"), mouseX, mouseY, !accessPolicy ? CYAN : MUTED);
        button(graphics, tabsLeft + 54, top + 8, r - 7, top + 26,
                t("Acesso", "Access"), mouseX, mouseY, accessPolicy ? CYAN : MUTED);
        graphics.fill(l + 5, top + 37, split - 3, bottom - 5, 0xFF0B1926);
        graphics.fill(split + 3, top + 37, r - 5, bottom - 5, 0xFF0B1926);
        int first = portPage * 4;
        for (int row = 0; row < 4; row++) {
            int port = first + row;
            if (port >= module.portCount()) break;
            int y = top + 43 + row * 21;
            graphics.fill(l + 9, y, split - 7, y + 19, selectedPort == port ? 0xFF20516A : ROW);
            boolean linked = module.port(port) != null && module.port(port).connected();
            graphics.fill(l + 13, y + 7, l + 18, y + 12, linked && module.powered() && module.portEnabled(port) ? GREEN : MUTED);
            graphics.drawString(font, "eth" + port + "  VLAN " + module.portVlan(port), l + 24, y + 6, TEXT, false);
        }
        int navY = bottom - 21;
        graphics.drawString(font, "<  " + (portPage + 1) + "/" + Math.max(1, (module.portCount() + 3) / 4) + "  >",
                l + 14, navY, CYAN, false);
        int x = split + 10, xRight = r - 11;
        String heading = "eth" + selectedPort + (module.port(selectedPort) != null && module.port(selectedPort).connected() ?
                "  •  " + t("conectada", "connected") : "  •  " + t("livre", "free"));
        ellipsis(graphics, heading, x, top + 44, xRight - x, TEXT);
        if (accessPolicy) {
            int start = accessPage * 8;
            for (int i = start; i < Math.min(module.portCount(), start + 8); i++) {
                int col = (i - start) % 2, row = (i - start) / 2;
                int middle = x + (xRight - x) / 2;
                int x0 = col == 0 ? x : middle + 3;
                int x1 = col == 0 ? middle - 3 : xRight;
                int y = top + 61 + row * 22;
                boolean allowed = module.portPairAllowed(selectedPort, i);
                button(graphics, x0, y, x1, y + 18,
                        "eth" + i + " " + (i == selectedPort ? "—" : allowed ? "✓" : "×"),
                        mouseX, mouseY, i == selectedPort ? MUTED : allowed ? GREEN : RED);
            }
            if (module.portCount() > 8)
                graphics.drawString(font, "< " + (accessPage + 1) + "/" + ((module.portCount() + 7) / 8) + " >",
                        xRight - 46, bottom - 19, CYAN, false);
        } else {
            button(graphics, x, top + 61, xRight, top + 79,
                    module.portEnabled(selectedPort) ? t("Porta habilitada", "Port enabled") : t("Porta desabilitada", "Port disabled"),
                    mouseX, mouseY, module.portEnabled(selectedPort) ? GREEN : RED);
            button(graphics, x, top + 83, xRight, top + 101,
                    module.portTrunk(selectedPort) ? t("Trunk ativo", "Trunk on") : t("Trunk desativado", "Trunk off"),
                    mouseX, mouseY, module.portTrunk(selectedPort) ? GREEN : MUTED);
            button(graphics, x, top + 105, xRight, top + 123,
                    module.portIsolated(selectedPort) ? t("Isolada", "Isolated") : t("Compartilhada", "Shared"),
                    mouseX, mouseY, module.portIsolated(selectedPort) ? RED : GREEN);
            int vlanY = top + 127;
            int minus = Math.max(x + 35, xRight - 50);
            graphics.fill(x, vlanY, xRight, vlanY + 18, ROW);
            String vlanLabel = vlanEditing ? "VLAN " + vlanDraft + "_" : "VLAN " + module.portVlan(selectedPort);
            ellipsis(graphics, vlanLabel, x + 5, vlanY + 5, minus - x - 6, TEXT);
            button(graphics, minus, vlanY, minus + 22, vlanY + 18, "−", mouseX, mouseY, CYAN);
            button(graphics, minus + 25, vlanY, xRight, vlanY + 18, "+", mouseX, mouseY, CYAN);
        }
    }

    private String dressLabel(int side) {
        return switch (side) {
            case 1 -> t("esquerda", "left");
            case 2 -> t("direita", "right");
            default -> t("automático", "auto");
        };
    }

    private String name(RackModule module) {
        String id = module.type().name().toLowerCase();
        return switch (id) {
            case "browser" -> t("Navegador", "Browser");
            case "ssh" -> "SSH";
            case "log" -> t("Registro de rede", "Network log");
            case "devtools" -> t("Ferramentas de desenvolvimento", "Developer tools");
            case "sftp" -> "SFTP";
            case "terminal" -> t("Terminal local", "Local terminal");
            case "remote" -> t("Área de trabalho remota", "Remote desktop");
            case "proxy" -> t("Proxy de interceptação", "Intercepting proxy");
            case "switch" -> t("Switch não gerenciável", "Unmanaged switch");
            case "managed_switch" -> t("Switch gerenciável", "Managed switch");
            default -> module.type().name().replace('_', ' ');
        };
    }

    private void button(GuiGraphics graphics, int l, int t, int r, int b, String label,
                        int mouseX, int mouseY, int accent) {
        graphics.fill(l, t, r, b, mouseX >= l && mouseX < r && mouseY >= t && mouseY < b ? 0xFF264457 : ROW);
        graphics.fill(l, t, l + 3, b, accent);
        centered(graphics, font.plainSubstrByWidth(label, Math.max(1, r - l - 12)), (l + r) / 2, (t + b) / 2 - 4, TEXT);
    }

    private void centered(GuiGraphics graphics, String value, int x, int y, int color) {
        graphics.drawString(font, value, x - font.width(value) / 2, y, color, false);
    }

    private void ellipsis(GuiGraphics graphics, String value, int x, int y, int maxWidth, int color) {
        if (maxWidth < 5) return;
        String displayed = font.width(value) <= maxWidth ? value : font.plainSubstrByWidth(value, maxWidth - 10) + "…";
        graphics.drawString(font, displayed, x, y, color, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        RackBlockEntity rack = rack();
        if (button != 0 || !usable(rack)) return super.mouseClicked(mouseX, mouseY, button);
        RackModule module = selected(rack);
        if (portFocus) {
            if (module == null) { portFocus = false; return true; }
            if (mouseY < top() + 35) {
                if (mouseX >= width - 154 && mouseX < width - 84 && module.portCount() < module.maxPorts())
                    send(C2SMessageRackAction.Action.ADD_PORT, module.id(), 0);
                else if (mouseX >= width - 82) portPage = (portPage + 1) % Math.max(1, (module.portCount() + 3) / 4);
                else portFocus = false;
                return true;
            }
            int l = 18, r = width - 18;
            int frontB = Math.min(bottom() - 59, top() + 41 + 54);
            int portTop = frontB + 8;
            int available = r - l - 24;
            int pageCount = Math.min(4, module.portCount() - portPage * 4);
            int portWidth = Math.max(24, Math.min(64, (available - 12) / Math.max(1, pageCount) - 4));
            int groupWidth = pageCount * (portWidth + 4) - 4;
            int portLeft = (l + r - groupWidth) / 2;
            for (int index = 0; index < pageCount; index++) {
                int i = portPage * 4 + index;
                int x = portLeft + index * (portWidth + 4);
                if (mouseX >= x && mouseX < x + portWidth && mouseY >= portTop && mouseY < portTop + 28) {
                    ItemStack tool = minecraft.player.getMainHandItem();
                    if (tool.getItem() instanceof ItemDataCable)
                        send(C2SMessageRackAction.Action.CABLE, module.id(), i);
                    else {
                        selectedPort = i;
                        send(C2SMessageRackAction.Action.PORT_DRESS, module.id(), i);
                    }
                    return true;
                }
            }
            int dressY = portTop + 34;
            if (mouseY >= dressY && mouseY < dressY + 18) {
                send(C2SMessageRackAction.Action.PORT_DRESS, module.id(), selectedPort);
                return true;
            }
            return true;
        }
        if (policyFocus) {
            if (module == null || module.type() != RackModuleType.MANAGED_SWITCH) { policyFocus = false; return true; }
            int l = 18, r = width - 18, split = l + Math.max(122, (r - l) * 44 / 100);
            int top = top();
            int tabsLeft = Math.max(split + 4, r - 111);
            if (mouseY >= top + 8 && mouseY < top + 30) {
                if (mouseX < tabsLeft) policyFocus = false;
                else accessPolicy = mouseX >= tabsLeft + 52;
                vlanEditing = false;
                return true;
            }
            if (mouseX >= l + 9 && mouseX < split - 7) {
                if (mouseY >= top + 43 && mouseY < top + 43 + 4 * 21) {
                    int port = portPage * 4 + ((int) mouseY - top - 43) / 21;
                    if (port < module.portCount()) { selectedPort = port; vlanEditing = false; }
                } else if (mouseY >= bottom() - 25 && mouseY < bottom())
                    portPage = (portPage + 1) % Math.max(1, (module.portCount() + 3) / 4);
                return true;
            }
            int x = split + 10, xRight = r - 11;
            if (mouseX < x || mouseX >= xRight) return true;
            if (accessPolicy) {
                if (module.portCount() > 8 && mouseY >= bottom() - 24 && mouseY < bottom()) {
                    accessPage = (accessPage + 1) % Math.max(1, (module.portCount() + 7) / 8);
                    return true;
                }
                for (int port = accessPage * 8; port < Math.min(module.portCount(), accessPage * 8 + 8); port++) {
                    int middle = x + (xRight - x) / 2;
                    int x0 = (port - accessPage * 8) % 2 == 0 ? x : middle + 3;
                    int x1 = (port - accessPage * 8) % 2 == 0 ? middle - 3 : xRight;
                    int y = top + 61 + ((port - accessPage * 8) / 2) * 22;
                    if (port != selectedPort && mouseX >= x0 && mouseX < x1 && mouseY >= y && mouseY < y + 18) {
                        send(C2SMessageRackAction.Action.PORT_PAIR, module.id(),
                                (selectedPort << 5) | (port << 1) | (module.portPairAllowed(selectedPort, port) ? 0 : 1));
                        return true;
                    }
                }
            } else {
                if (mouseY >= top + 61 && mouseY < top + 79)
                    send(C2SMessageRackAction.Action.PORT_ENABLED, module.id(),
                            selectedPort * 2 + (module.portEnabled(selectedPort) ? 0 : 1));
                else if (mouseY >= top + 83 && mouseY < top + 101)
                    send(C2SMessageRackAction.Action.PORT_TRUNK, module.id(),
                            selectedPort * 2 + (module.portTrunk(selectedPort) ? 0 : 1));
                else if (mouseY >= top + 105 && mouseY < top + 123)
                    send(C2SMessageRackAction.Action.PORT_ISOLATED, module.id(),
                            selectedPort * 2 + (module.portIsolated(selectedPort) ? 0 : 1));
                else if (mouseY >= top + 127 && mouseY < top + 145) {
                    int minus = Math.max(x + 35, xRight - 50);
                    if (mouseX >= minus + 25) sendVlan(module, module.portVlan(selectedPort) + 1);
                    else if (mouseX >= minus) sendVlan(module, module.portVlan(selectedPort) - 1);
                    else {
                        vlanEditing = true;
                        vlanDraft.setLength(0);
                        vlanDraft.append(module.portVlan(selectedPort));
                    }
                }
            }
            return true;
        }
        int l = rackLeft(), r = rackRight();
        if (mouseX >= l && mouseX < r && mouseY >= top() + 19 && mouseY < bottom() - 17) {
            int u = firstU + ((int) mouseY - top() - 19) / rowHeight();
            if (u >= 0 && u < rack.getCapacityU()) {
                RackModule chosen = rack.moduleAtU(u);
                if (chosen != null) {
                    if (!chosen.id().equals(selected)) {
                        portPage = 0;
                        selectedPort = 0;
                    }
                    selected = chosen.id();
                }
                else {
                    ItemStack held = minecraft.player.getMainHandItem();
                    if (held.getItem() instanceof RackModuleItem) send(C2SMessageRackAction.Action.INSTALL, null, u);
                    selected = null;
                }
                return true;
            }
        }
        int dl = detailLeft(), dr = width - 18;
        if (mouseX >= dl + 9 && mouseX < dr - 9) {
            int y = top() + 64;
            int half = dl + 9 + (dr - dl - 23) / 2;
            if (module != null && mouseY >= y && mouseY < y + 18) {
                if (mouseX < half + 5) send(C2SMessageRackAction.Action.POWER, module.id(), module.powered() ? 0 : 1);
                else { portFocus = true; portPage = 0; focusStarted = System.currentTimeMillis(); }
                return true;
            }
            if (module != null && mouseY >= y + 23 && mouseY < y + 41) {
                if (mouseX < half + 5) {
                    send(C2SMessageRackAction.Action.REMOVE, module.id(), 0);
                    selected = null;
                } else send(C2SMessageRackAction.Action.DOOR, null, rack.doorOpen() ? 0 : 1);
                return true;
            }
            if (module != null && module.type() == RackModuleType.MANAGED_SWITCH &&
                    mouseY >= y + 46 && mouseY < y + 64) {
                policyFocus = true;
                portPage = 0;
                accessPage = 0;
                selectedPort = 0;
                return true;
            }
        }
        return true;
    }

    private void sendVlan(RackModule module, int vlan) {
        if (vlan >= 1 && vlan <= 4094)
            send(C2SMessageRackAction.Action.PORT_VLAN, module.id(), (selectedPort << 12) | vlan);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        RackBlockEntity rack = rack();
        if (rack == null) return super.mouseScrolled(mouseX, mouseY, delta);
        RackModule module = selected(rack);
        if ((portFocus || policyFocus) && module != null) {
            if (policyFocus && accessPolicy && mouseX > width / 2) {
                int pages = Math.max(1, (module.portCount() + 7) / 8);
                accessPage = Math.floorMod(accessPage - (int) Math.signum(delta), pages);
                return true;
            }
            int pages = Math.max(1, (module.portCount() + 3) / 4);
            portPage = Math.floorMod(portPage - (int) Math.signum(delta), pages);
            return true;
        }
        firstU = Math.max(0, Math.min(Math.max(0, rack.getCapacityU() - visibleU()), firstU - (int) Math.signum(delta) * 3));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (vlanEditing) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { vlanEditing = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!vlanDraft.isEmpty()) vlanDraft.deleteCharAt(vlanDraft.length() - 1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                try {
                    RackModule module = selected(rack());
                    if (module != null) sendVlan(module, Integer.parseInt(vlanDraft.toString()));
                } catch (NumberFormatException ignored) { }
                vlanEditing = false;
                return true;
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (portFocus || policyFocus)) {
            portFocus = false;
            policyFocus = false;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (vlanEditing) {
            if (codePoint >= '0' && codePoint <= '9' && vlanDraft.length() < 4) vlanDraft.append(codePoint);
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
