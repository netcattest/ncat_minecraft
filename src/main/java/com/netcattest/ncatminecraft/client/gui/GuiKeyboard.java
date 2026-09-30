/*
 * Copyright (C) 2019 BARBOTIN Nicolas
 */

package com.netcattest.ncatminecraft.client.gui;

import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import com.netcattest.ncatminecraft.NcatMinecraft;
import com.netcattest.ncatminecraft.client.gui.camera.KeyboardCamera;
import com.netcattest.ncatminecraft.client.workstation.WorkstationClientView;
import com.netcattest.ncatminecraft.client.rack.RackClientApps;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.client.gui.controls.Button;
import com.netcattest.ncatminecraft.client.gui.controls.Control;
import com.netcattest.ncatminecraft.client.gui.controls.Label;
import com.netcattest.ncatminecraft.client.gui.loading.FillControl;
import com.netcattest.ncatminecraft.controls.builtin.ClickControl;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.net.server_bound.C2SMessageScreenCtrl;
import com.netcattest.ncatminecraft.net.server_bound.C2SMessageWorkstationInput;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.utilities.Log;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;
import com.netcattest.ncatminecraft.utilities.serialization.TypeData;
import com.netcattest.ncatminecraft.utilities.serialization.Util;
import org.cef.browser.CefBrowser;
import org.cef.misc.CefCursorType;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;
import org.vivecraft.client_vr.gameplay.VRPlayer;
import org.vivecraft.client_vr.gameplay.screenhandlers.KeyboardHandler;

import java.io.*;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class GuiKeyboard extends WDScreen {

    private static final String WARNING_FNAME = "wd_keyboard_warning.txt";

    private ScreenBlockEntity tes;
    private BlockSide side;
    private ScreenData data;
    private CefBrowser cursorBrowser;
    private final ArrayList<TypeData> evStack = new ArrayList<>();
    private DataCableService.ScreenEndpoint queuedWorkstationTarget;
    private BlockPos kbPos;
    private boolean showWarning = true;
    private boolean browserFullscreen;
    private boolean addressEditing;
    private final StringBuilder addressInput = new StringBuilder();

    @FillControl
    private Label lblInfo;

    @FillControl
    private Button btnOk;

    public GuiKeyboard() {
        super(Component.nullToEmpty(null));
    }

    public GuiKeyboard(ScreenBlockEntity tes, BlockSide side, BlockPos kbPos) {
        this();
        this.tes = tes;
        this.side = side;
        this.kbPos = kbPos;
    }

    @Override
    protected void addLoadCustomVariables(Map<String, Double> vars) {
        vars.put("showWarning", showWarning ? 1.0 : 0.0);
    }

    private static final boolean vivecraftPresent;

    static {
        boolean vivePres = false;
        if (ModList.get().isLoaded("vivecraft")) vivePres = true;
        else {
            try {
                Class<?> clazz = Class.forName("org.vivecraft.gameplay.screenhandlers.KeyboardHandler");
                if (clazz == null) vivePres = false;
                else {
                    Method m = clazz.getMethod("setOverlayShowing", boolean.class);
                    vivePres = m != null;
                }
            } catch (Throwable ignored) {
                vivePres = false;
            }
        }
        vivecraftPresent = vivePres;
    }

    @Override
    public void init() {
        super.init();

        if (minecraft.getSingleplayerServer() != null && !minecraft.getSingleplayerServer().isPublished())
            showWarning = false;
        else
            showWarning = !hasUserReadWarning();
        if (isLocalScreen())
            showWarning = false;

        loadFrom(new ResourceLocation("ncat_minecraft", "gui/kb_right.json"));

        if (showWarning) {
            int maxLabelW = 0;
            int totalH = 0;

            for (Control ctrl : controls) {
                if (ctrl != lblInfo && ctrl instanceof Label) {
                    if (ctrl.getWidth() > maxLabelW)
                        maxLabelW = ctrl.getWidth();

                    totalH += ctrl.getHeight();
                    ctrl.setPos((width - ctrl.getWidth()) / 2, 0);
                }
            }

            btnOk.setWidth(maxLabelW);
            btnOk.setPos((width - maxLabelW) / 2, 0);
            totalH += btnOk.getHeight();

            int y = (height - totalH) / 2;
            for (Control ctrl : controls) {
                if (ctrl != lblInfo) {
                    ctrl.setPos(ctrl.getX(), y);
                    y += ctrl.getHeight();
                }
            }
        } else {
            if (!minecraft.isWindowActive()) {
                minecraft.setWindowActive(true);
                minecraft.mouseHandler.grabMouse();
            }
        }

        defaultBackground = showWarning;
        syncTicks = 5;

        if (vivecraftPresent)
            if (VRPlayer.get() != null)
                KeyboardHandler.setOverlayShowing(true);

        KeyboardCamera.focus(tes, side);

        data = tes.getScreen(side);
        syncCursorBrowser();
    }

    private void syncCursorBrowser() {
        CefBrowser next = BlockRegistry.isWorkstationScreen(tes.getBlockState().getBlock()) ?
                WorkstationClientView.canInteract(tes, side) ? WorkstationClientView.browserForDisplay(tes, side) : null :
                data.browser;
        if (next == cursorBrowser) return;
        if (cursorBrowser instanceof MCEFBrowser previous) {
            previous.setCursor(CefCursorType.POINTER);
            previous.setCursorChangeListener(cursor -> data.mouseType = cursor);
        }
        cursorBrowser = next;
        if (next instanceof MCEFBrowser active) {
            active.setCursor(CefCursorType.fromId(data.mouseType));
            active.setCursorChangeListener(id -> {
                data.mouseType = id;
                active.setCursor(CefCursorType.fromId(id));
            });
        }
    }

    @Override
    public void removed() {
        flushQueuedKeys();
        super.removed();
        if (vivecraftPresent)
            if (VRPlayer.get() != null)
                KeyboardHandler.setOverlayShowing(false);
        KeyboardCamera.focus(null, null);
        if (cursorBrowser instanceof MCEFBrowser mcef) {
            mcef.setCursor(CefCursorType.POINTER);
            mcef.setCursorChangeListener((cursor) -> data.mouseType = cursor);
        }
        cursorBrowser = null;
    }

    @Override
    public void onClose() {
        removed();
        super.onClose();
        this.minecraft.popGuiLayer();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        ScreenData browserData = activeBrowserData();
        ScreenBlockEntity browserEntity = activeBrowserEntity();
        CefBrowser rackBrowser = rackBrowser();
        CefBrowser activeBrowser = rackBrowser != null ? rackBrowser : browserData == null ? null : browserData.browser;
        if (addressEditing) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { addressEditing = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && addressInput.length() > 0) { addressInput.deleteCharAt(addressInput.length() - 1); return true; }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                String value = addressInput.toString().trim();
                if (!value.isEmpty()) {
                    String destination = value.matches("(?i)^https?://.*") ? value :
                            value.matches("[^ ]+\\.[^ ]+") ? "https://" + value :
                            "https://www.google.com/search?q=" + java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
                    if (activeBrowser != null) activeBrowser.loadURL(NcatMinecraft.applyBlacklist(destination));
                }
                addressEditing = false;
                return true;
            }
            return true;
        }
        if (activeBrowser != null) {
            boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
            boolean alt = (modifiers & GLFW.GLFW_MOD_ALT) != 0;
            if (keyCode == GLFW.GLFW_KEY_F11) {
                browserFullscreen = !browserFullscreen;
                return true;
            }
            if (browserFullscreen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
                browserFullscreen = false;
                return true;
            }
            if (alt && keyCode == GLFW.GLFW_KEY_LEFT && activeBrowser.canGoBack()) { activeBrowser.goBack(); return true; }
            if (alt && keyCode == GLFW.GLFW_KEY_RIGHT && activeBrowser.canGoForward()) { activeBrowser.goForward(); return true; }
            if (alt && keyCode == GLFW.GLFW_KEY_HOME) { activeBrowser.loadURL(com.netcattest.ncatminecraft.config.CommonConfig.Browser.homepage); return true; }
            if (keyCode == GLFW.GLFW_KEY_F5 || ctrl && keyCode == GLFW.GLFW_KEY_R) { activeBrowser.reload(); return true; }
            if (ctrl && keyCode == GLFW.GLFW_KEY_T) { if (rackBrowser != null) RackClientApps.newTab(rackBrowser); else browserData.newTab(browserEntity); return true; }
            if (ctrl && keyCode == GLFW.GLFW_KEY_L) {
                addressInput.setLength(0);
                addressInput.append(activeBrowser.getURL());
                addressEditing = true;
                return true;
            }
            if (ctrl && keyCode == GLFW.GLFW_KEY_W) { if (rackBrowser != null) RackClientApps.closeTab(rackBrowser); else browserData.closeTab(); return true; }
            if (ctrl && keyCode == GLFW.GLFW_KEY_TAB) {
                int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
                if (rackBrowser != null) RackClientApps.switchTab(rackBrowser,
                        (RackClientApps.activeTab(rackBrowser) + step + RackClientApps.tabCount(rackBrowser)) % RackClientApps.tabCount(rackBrowser));
                else browserData.switchTab((browserData.activeTab() + step + browserData.tabCount()) % browserData.tabCount());
                return true;
            }
            if (ctrl && keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_8) {
                if (rackBrowser != null) RackClientApps.switchTab(rackBrowser, keyCode - GLFW.GLFW_KEY_1);
                else browserData.switchTab(keyCode - GLFW.GLFW_KEY_1);
                return true;
            }
            if (ctrl && (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD)) { activeBrowser.setZoomLevel(Math.min(3, activeBrowser.getZoomLevel() + .25)); return true; }
            if (ctrl && (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT)) { activeBrowser.setZoomLevel(Math.max(-3, activeBrowser.getZoomLevel() - .25)); return true; }
            if (ctrl && keyCode == GLFW.GLFW_KEY_0) { activeBrowser.setZoomLevel(0); return true; }
        }
        if (quitOnEscape && keyCode == GLFW.GLFW_KEY_ESCAPE && !isRemoteInputSelected()) {
            onClose();
            return true;
        }
        addKey(new TypeData(TypeData.Action.PRESS, keyCode, modifiers, scanCode));
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean isBrowserScreen() {
        ScreenData browserData = activeBrowserData();
        return browserData != null && browserData.browser != null || rackBrowser() != null;
    }

    private CefBrowser rackBrowser() {
        if (!isWorkstation()) return null;
        DataCableService.RackModuleEndpoint selected = WorkstationClientView.selectedRackTarget(tes, side);
        if (selected == null || selected.module().type() != RackModuleType.BROWSER) return null;
        return WorkstationClientView.browserForDisplay(tes, side);
    }

    private boolean isWorkstation() {
        return tes != null && BlockRegistry.isWorkstationScreen(tes.getBlockState().getBlock());
    }

    private DataCableService.ScreenEndpoint selectedBrowserTarget() {
        if (!isWorkstation()) return null;
        DataCableService.ScreenEndpoint selected = WorkstationClientView.selectedTarget(tes, side);
        return selected != null && BlockRegistry.isBrowserScreen(selected.entity().getBlockState().getBlock()) ? selected : null;
    }

    private boolean isRemoteInputSelected() {
        if (tes == null) return false;
        if (BlockRegistry.isRemoteScreen(tes.getBlockState().getBlock())) return true;
        DataCableService.ScreenEndpoint selected = selectedBrowserTarget();
        return selected != null && BlockRegistry.isRemoteScreen(selected.entity().getBlockState().getBlock());
    }

    private ScreenData activeBrowserData() {
        if (tes == null || data == null) return null;
        if (!isWorkstation())
            return BlockRegistry.isBrowserScreen(tes.getBlockState().getBlock()) ? data : null;
        DataCableService.ScreenEndpoint selected = selectedBrowserTarget();
        return selected == null ? null : selected.entity().getScreen(selected.side());
    }

    private ScreenBlockEntity activeBrowserEntity() {
        if (!isWorkstation()) return tes;
        DataCableService.ScreenEndpoint selected = selectedBrowserTarget();
        return selected == null ? null : selected.entity();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ScreenData browserData = activeBrowserData();
        CefBrowser selectedBrowser = rackBrowser() != null ? rackBrowser() : browserData == null ? null : browserData.browser;
        if (!browserFullscreen || !(selectedBrowser instanceof MCEFBrowser activeBrowser)) {
            if (lblInfo != null) {
                String hint = Component.translatable(isRemoteInputSelected() ?
                        "ncat_minecraft.gui.keyboard.remoteHooked" : "ncat_minecraft.gui.keyboard.hooked").getString();
                if (!hint.equals(lblInfo.getLabel())) lblInfo.setLabel(hint);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
            if (addressEditing) renderAddress(graphics);
            if (isRemoteInputSelected()) {
                graphics.fill(width - 105, 3, width - 4, 23, 0xFF154B35);
                graphics.fill(width - 103, 5, width - 6, 21, 0xFF236746);
                graphics.drawCenteredString(font,
                        Component.translatable("ncat_minecraft.gui.keyboard.close"),
                        width - 54, 9, 0xFFFFFFFF);
            }
            return;
        }
        graphics.fill(0, 0, width, height, 0xFF101419);
        int top = 24;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem._setShaderTexture(0, activeBrowser.getRenderer().getTextureID());
        RenderSystem.setShaderColor(1, 1, 1, 1);
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder builder = tessellator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        builder.vertex(graphics.pose().last().pose(), 0, height, 0).uv(0, 1).color(1, 1, 1, 1).endVertex();
        builder.vertex(graphics.pose().last().pose(), width, height, 0).uv(1, 1).color(1, 1, 1, 1).endVertex();
        builder.vertex(graphics.pose().last().pose(), width, top, 0).uv(1, 0).color(1, 1, 1, 1).endVertex();
        builder.vertex(graphics.pose().last().pose(), 0, top, 0).uv(0, 0).color(1, 1, 1, 1).endVertex();
        tessellator.end();
        graphics.fill(0, 0, width, top, 0xFF1A2027);
        graphics.drawString(font, "<", 10, 8, 0xFFE8EDF2, false);
        graphics.drawString(font, ">", 34, 8, 0xFFE8EDF2, false);
        graphics.drawString(font, "R", 58, 8, 0xFFE8EDF2, false);
        graphics.drawString(font, "H", 82, 8, 0xFFE8EDF2, false);
        graphics.drawString(font, "+", 106, 8, 0xFFE8EDF2, false);
        int activeTab = browserData == null ? RackClientApps.activeTab(selectedBrowser) : browserData.activeTab();
        int tabCount = browserData == null ? RackClientApps.tabCount(selectedBrowser) : browserData.tabCount();
        graphics.drawString(font, (activeTab + 1) + "/" + tabCount, 130, 8, 0xFFE8EDF2, false);
        String address = selectedBrowser.getURL();
        while (font.width(address) > width - 165 && address.length() > 0) address = address.substring(0, address.length() - 1);
        graphics.drawString(font, address, 160, 8, 0xFFAFC4D8, false);
        if (addressEditing) renderAddress(graphics);
    }

    private void renderAddress(GuiGraphics graphics) {
        int left = Math.max(8, width / 8);
        int right = width - left;
        graphics.fill(left, 4, right, 25, 0xFF18232E);
        graphics.fill(left, 24, right, 25, 0xFF62B6FF);
        String value = addressInput.toString();
        while (font.width(value) > right - left - 20 && value.length() > 0) value = value.substring(1);
        graphics.drawString(font, value + "_", left + 8, 10, 0xFFFFFFFF, false);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (addressEditing) {
            if (codePoint >= 32 && codePoint != 127 && addressInput.length() < 2048) addressInput.append(codePoint);
            return true;
        }
        addKey(new TypeData(TypeData.Action.TYPE, codePoint, modifiers, 0));
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (addressEditing) return true;
        addKey(new TypeData(TypeData.Action.RELEASE, keyCode, modifiers, scanCode));
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    void addKey(TypeData data) {
        if (isWorkstation()) {
            DataCableService.ScreenEndpoint selected = selectedBrowserTarget();
            if (queuedWorkstationTarget != null && !sameTarget(queuedWorkstationTarget, selected))
                flushQueuedKeys();
            tes.type(side, "[" + NcatMinecraft.GSON.toJson(data) + "]", kbPos);
            if (selected == null || BlockRegistry.isRemoteScreen(selected.entity().getBlockState().getBlock())) return;
            queuedWorkstationTarget = selected;
            evStack.add(data);
            if (evStack.size() >= 96 || NcatMinecraft.GSON.toJson(evStack).length() >= 8192)
                flushQueuedKeys();
            else if (!syncRequested())
                requestSync();
            return;
        }
        tes.type(side, "[" + NcatMinecraft.GSON.toJson(data) + "]", kbPos);

        if (isLocalScreen())
            return;

        evStack.add(data);
        if (!evStack.isEmpty() && !syncRequested())
            requestSync();
    }

    @Override
    protected void sync() {
        flushQueuedKeys();
    }

    private static boolean sameTarget(DataCableService.ScreenEndpoint a, DataCableService.ScreenEndpoint b) {
        return a != null && b != null && a.side() == b.side() &&
                a.entity().getBlockPos().equals(b.entity().getBlockPos());
    }

    private void flushQueuedKeys() {
        if (evStack.isEmpty()) return;
        if (isWorkstation()) {
            DataCableService.ScreenEndpoint target = queuedWorkstationTarget;
            if (target != null)
                WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageWorkstationInput.keys(
                        tes, side, target.entity(), target.side(), NcatMinecraft.GSON.toJson(evStack)));
            queuedWorkstationTarget = null;
            evStack.clear();
            return;
        }
        if (!isLocalScreen()) {
            WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.type(tes, side, NcatMinecraft.GSON.toJson(evStack), kbPos));
        }
        evStack.clear();
    }

    @GuiSubscribe
    public void onClick(Button.ClickEvent ev) {
        if(showWarning && ev.getSource() == btnOk) {
            writeUserAcknowledge();

            for(Control ctrl: controls) {
                if(ctrl instanceof Label) {
                    Label lbl = (Label) ctrl;
                    lbl.setVisible(!lbl.isVisible());
                }
            }

            btnOk.setDisabled(true);
            btnOk.setVisible(false);
            showWarning = false;
            defaultBackground = false;
            minecraft.setWindowActive(true);
            minecraft.mouseHandler.grabMouse();
        }
    }

    private boolean hasUserReadWarning() {
        try {
            File f = new File(FMLPaths.GAMEDIR.get().toString(), WARNING_FNAME);

            if(f.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(f));
                String str = br.readLine();
                Util.silentClose(br);

                return str != null && str.trim().equalsIgnoreCase("read");
            }
        } catch(Throwable t) {
            Log.warningEx("Can't know if user has already read the warning", t);
        }

        return false;
    }

    private void writeUserAcknowledge() {
        try {
            File f = new File(FMLPaths.GAMEDIR.get().toString(), WARNING_FNAME);

            BufferedWriter bw = new BufferedWriter(new FileWriter(f));
            bw.write("read\n");
            Util.silentClose(bw);
        } catch(Throwable t) {
            Log.warningEx("Can't write that the user read the warning", t);
        }
    }

    @Override
    public boolean isForBlock(BlockPos bp, BlockSide side) {
        return bp.equals(kbPos) || (bp.equals(tes.getBlockPos()) && side == this.side);
    }

    protected void mouse(double mouseX, double mouseY, Consumer<Vector2i> func) {
        if (browserFullscreen && isBrowserScreen()) {
            if (mouseY >= 24 && mouseY < height && mouseX >= 0 && mouseX < width) {
                int screenWidth = data.rotation.isVertical ? data.resolution.y : data.resolution.x;
                int screenHeight = data.rotation.isVertical ? data.resolution.x : data.resolution.y;
                int contentHeight = isWorkstation() ?
                        Math.max(1, screenHeight - WorkstationClientView.footerPixels(data)) : screenHeight;
                func.accept(new Vector2i((int) (mouseX * screenWidth / width),
                        (int) ((mouseY - 24) * contentHeight / (height - 24))));
            }
            return;
        }
        float pct = Minecraft.getInstance().getPartialTick();

        double fov = Minecraft.getInstance().gameRenderer.getFov(
                Minecraft.getInstance().getEntityRenderDispatcher().camera,
                pct, true
        );

        mouseX /= width;
        mouseY /= height;

        mouseX -= 0.5;
        mouseY -= 0.5;
        mouseY = -mouseY;

        Matrix4f proj = Minecraft.getInstance().gameRenderer.getProjectionMatrix(fov);

        Entity e = Minecraft.getInstance().getEntityRenderDispatcher().camera.getEntity();

        PoseStack camera = new PoseStack();
        float[] angle = KeyboardCamera.getAngle(e, pct);
        camera.mulPose(Axis.XP.rotationDegrees(angle[0]));
        camera.mulPose(Axis.YP.rotationDegrees(angle[1] + 180.0F));

        Vector4f coord = new Vector4f(2f * (float) mouseX, 2 * (float) mouseY, 0, 1f);
        coord.add(proj.invert().transform(coord));
        coord = camera.last().pose().invert().transform(coord);

        Vec3 vec3 = e.getEyePosition(pct);
        Vec3 vec31 = new Vec3(coord.x, coord.y, coord.z).normalize();

        BlockHitResult result = tes.trace(side, vec3, vec31);
        if (result.getType() != HitResult.Type.MISS) {
            tes.interact(result, func);
        }
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (isRemoteInputSelected() && remoteCloseButtonAt(mouseX, mouseY)) return;
        mouse(mouseX, mouseY, (hit) -> {
            if (isWorkstation())
                WorkstationClientView.sendRemoteMouse(tes, side, ClickControl.ControlType.MOVE, hit, -1);
            tes.handleMouseEvent(side, ClickControl.ControlType.MOVE, hit, -1);
            if (!isLocalScreen())
                WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserMove(tes, side, hit));
        });

        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isRemoteInputSelected() && remoteCloseButtonAt(mouseX, mouseY)) {
            onClose();
            return true;
        }
        ScreenData browserData = activeBrowserData();
        ScreenBlockEntity browserEntity = activeBrowserEntity();
        CefBrowser rackBrowser = rackBrowser();
        CefBrowser activeBrowser = rackBrowser != null ? rackBrowser : browserData == null ? null : browserData.browser;
        if (browserFullscreen && activeBrowser != null && mouseY < 24 && button == 0) {
            if (mouseX < 28 && activeBrowser.canGoBack()) activeBrowser.goBack();
            else if (mouseX < 52 && activeBrowser.canGoForward()) activeBrowser.goForward();
            else if (mouseX < 76) activeBrowser.reload();
            else if (mouseX < 100) activeBrowser.loadURL(com.netcattest.ncatminecraft.config.CommonConfig.Browser.homepage);
            else if (mouseX < 124) { if (rackBrowser != null) RackClientApps.newTab(rackBrowser); else browserData.newTab(browserEntity); }
            else if (mouseX < 160) {
                if (rackBrowser != null) RackClientApps.switchTab(rackBrowser,
                        (RackClientApps.activeTab(rackBrowser) + 1) % RackClientApps.tabCount(rackBrowser));
                else browserData.switchTab((browserData.activeTab() + 1) % browserData.tabCount());
            }
            else {
                addressInput.setLength(0);
                addressInput.append(activeBrowser.getURL());
                addressEditing = true;
            }
            return true;
        }
        mouse(mouseX, mouseY, (hit) -> {
            if (isWorkstation())
                WorkstationClientView.sendRemoteMouse(tes, side, ClickControl.ControlType.MOVE, hit, -1);
            tes.handleMouseEvent(side, ClickControl.ControlType.MOVE, hit, -1);
            if (isWorkstation())
                WorkstationClientView.sendRemoteMouse(tes, side, ClickControl.ControlType.DOWN, hit, button);
            tes.handleMouseEvent(side, ClickControl.ControlType.DOWN, hit, button);
            if (!isLocalScreen())
                WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserDown(tes, side, hit, button));
        });

        KeyboardCamera.setMouse(button, true);

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean remoteCloseButtonAt(double mouseX, double mouseY) {
        return mouseX >= width - 105 && mouseX < width - 4 && mouseY >= 3 && mouseY < 23;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouse(mouseX, mouseY, (hit) -> {
            if (isWorkstation())
                WorkstationClientView.sendRemoteMouse(tes, side, ClickControl.ControlType.MOVE, hit, -1);
            tes.handleMouseEvent(side, ClickControl.ControlType.MOVE, hit, -1);
            if (isWorkstation())
                WorkstationClientView.sendRemoteMouse(tes, side, ClickControl.ControlType.UP, hit, button);
            tes.handleMouseEvent(side, ClickControl.ControlType.UP, hit, button);
            if (!isLocalScreen())
                WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserUp(tes, side, button));
        });

        KeyboardCamera.setMouse(button, false);

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (BlockRegistry.isWorkstationScreen(tes.getBlockState().getBlock()))
            return WorkstationClientView.mouseWheel(tes, side, amount);
        ScreenData browserData = activeBrowserData();
        if (browserFullscreen && browserData != null && browserData.browser instanceof MCEFBrowser browser) {
            browser.sendMouseWheel(browserData.lastMousePos.x, browserData.lastMousePos.y, amount, 0);
            return true;
        }
        if (isLocalScreen() && data.browser instanceof MCEFBrowser browser) {
            browser.sendMouseWheel(data.lastMousePos.x, data.lastMousePos.y, amount, 0);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void tick() {
        if (data != null) syncCursorBrowser();
        double mouseX = Minecraft.getInstance().mouseHandler.xpos() / Minecraft.getInstance().getWindow().getWidth();
        double mouseY = Minecraft.getInstance().mouseHandler.ypos() / Minecraft.getInstance().getWindow().getHeight();

        mouse(mouseX * width, mouseY * height, (hit) -> {
            if (isWorkstation())
                WorkstationClientView.sendRemoteMouse(tes, side, ClickControl.ControlType.MOVE, hit, -1);
            tes.handleMouseEvent(side, ClickControl.ControlType.MOVE, hit, -1);
            if (!isLocalScreen())
                WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageScreenCtrl.laserMove(tes, side, hit));
        });

        super.tick();
    }

    private boolean isLocalScreen() {
        return BlockRegistry.isLocalScreen(tes.getBlockState().getBlock());
    }
}
