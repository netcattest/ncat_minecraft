package com.netcattest.ncatminecraft.client.workstation;

import com.cinemamod.mcef.MCEFBrowser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.netcattest.ncatminecraft.controls.builtin.ClickControl;
import com.netcattest.ncatminecraft.client.rack.RackClientApps;
import com.netcattest.ncatminecraft.client.remote.RemoteClientSessions;
import com.netcattest.ncatminecraft.entity.RackModuleType;
import com.netcattest.ncatminecraft.core.ScreenRights;
import com.netcattest.ncatminecraft.entity.ScreenBlockEntity;
import com.netcattest.ncatminecraft.entity.ScreenData;
import com.netcattest.ncatminecraft.net.server_bound.C2SMessageDataActivity;
import com.netcattest.ncatminecraft.net.server_bound.C2SMessageWorkstationInput;
import com.netcattest.ncatminecraft.net.server_bound.C2SMessageRackActivity;
import com.netcattest.ncatminecraft.net.WDNetworkRegistry;
import com.netcattest.ncatminecraft.registry.BlockRegistry;
import com.netcattest.ncatminecraft.utilities.DataCableService;
import com.netcattest.ncatminecraft.utilities.data.BlockSide;
import com.netcattest.ncatminecraft.utilities.math.Vector2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import org.cef.browser.CefBrowser;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.UUID;

public final class WorkstationClientView {
    private static final Map<ScreenBlockEntity, Map<BlockSide, State>> STATES = new WeakHashMap<>();
    private static final int FOOTER_PIXELS = 17;
    private static final float HOME_END = .16f;
    private static final float BACK_END = .24f;
    private static final float TABS_END = .92f;

    private WorkstationClientView() { }

    private record Key(BlockPos pos, BlockSide side) { }
    private record RackKey(BlockPos pos, UUID module) { }

    private static final class State {
        private long refreshedAt = Long.MIN_VALUE;
        private List<DataCableService.ScreenEndpoint> targets = List.of();
        private List<DataCableService.RackModuleEndpoint> rackTargets = List.of();
        private Key selected;
        private RackKey selectedRack;
        private int firstVisible;
        private int lastMouseX;
        private int lastMouseY;
        private MCEFBrowser pressedBrowser;
        private long lastTrafficTick = Long.MIN_VALUE;
        private Key lastTrafficTarget;
        private RackKey lastRackTrafficTarget;
        private DataCableService.ScreenEndpoint pressedRemoteTarget;
        private long lastRemoteMoveTick = Long.MIN_VALUE;
        private Key lastRemoteMoveTarget;
    }

    private static State state(ScreenBlockEntity workstation, BlockSide side) {
        return STATES.computeIfAbsent(workstation, ignored -> new HashMap<>())
                .computeIfAbsent(side, ignored -> new State());
    }

    public static boolean canInteract(ScreenBlockEntity workstation, BlockSide side) {
        ScreenData screen = workstation.getScreen(side);
        Player player = Minecraft.getInstance().player;
        return screen != null && screen.owner != null && player != null &&
                (screen.rightsFor(player) & ScreenRights.INTERACT) != 0;
    }

    private static List<DataCableService.ScreenEndpoint> targets(ScreenBlockEntity workstation, BlockSide side, State state) {
        if (workstation.getLevel() == null || !workstation.getLevel().isClientSide || !canInteract(workstation, side)) return List.of();
        long tick = workstation.getLevel().getGameTime();
        if (state.refreshedAt == Long.MIN_VALUE || tick < state.refreshedAt || tick - state.refreshedAt >= 5L) {
            state.refreshedAt = tick;
            Player player = Minecraft.getInstance().player;
            List<DataCableService.ScreenEndpoint> found = new ArrayList<>();
            Set<Key> seen = new HashSet<>();
            for (DataCableService.ScreenEndpoint target : DataCableService.connectedScreens(workstation.getLevel(), workstation, side)) {
                if (target.entity() == workstation || BlockRegistry.isWorkstationScreen(target.entity().getBlockState().getBlock())) continue;
                ScreenData remote = target.entity().getScreen(target.side());
                if (remote == null || remote.owner == null || player == null) continue;
                if ((remote.rightsFor(player) & ScreenRights.INTERACT) == 0) continue;
                if (!seen.add(key(target))) continue;
                found.add(target);
            }
            state.targets = List.copyOf(found);
            List<DataCableService.RackModuleEndpoint> rackFound = new ArrayList<>();
            Set<RackKey> rackSeen = new HashSet<>();
            for (DataCableService.RackModuleEndpoint target : DataCableService.connectedRackModules(
                    workstation.getLevel(), workstation, side)) {
                if (target.module().type().isSwitch() || !target.module().powered() ||
                        player == null || !target.rack().canConfigure(player) || !rackSeen.add(key(target))) continue;
                rackFound.add(target);
            }
            state.rackTargets = List.copyOf(rackFound);
            if (state.selected != null && state.targets.stream().noneMatch(target -> state.selected.equals(key(target))))
                state.selected = null;
            if (state.selectedRack != null && state.rackTargets.stream().noneMatch(target -> state.selectedRack.equals(key(target))))
                state.selectedRack = null;
            state.firstVisible = Math.min(state.firstVisible, Math.max(0, total(state) - 1));
        }
        return state.targets;
    }

    private static Key key(DataCableService.ScreenEndpoint target) {
        return new Key(target.entity().getBlockPos(), target.side());
    }

    private static RackKey key(DataCableService.RackModuleEndpoint target) {
        return new RackKey(target.rack().getBlockPos(), target.module().id());
    }

    private static int total(State state) { return state.targets.size() + state.rackTargets.size(); }

    private static void choose(State state, int index) {
        if (index < 0 || index >= total(state)) return;
        if (index < state.targets.size()) {
            state.selected = key(state.targets.get(index));
            state.selectedRack = null;
        } else {
            state.selectedRack = key(state.rackTargets.get(index - state.targets.size()));
            state.selected = null;
        }
    }

    public static DataCableService.RackModuleEndpoint selectedRackTarget(ScreenBlockEntity workstation, BlockSide side) {
        State state = state(workstation, side);
        targets(workstation, side, state);
        for (DataCableService.RackModuleEndpoint target : state.rackTargets)
            if (key(target).equals(state.selectedRack)) return target;
        return null;
    }

    public static DataCableService.ScreenEndpoint selectedTarget(ScreenBlockEntity workstation, BlockSide side) {
        State state = state(workstation, side);
        for (DataCableService.ScreenEndpoint target : targets(workstation, side, state))
            if (key(target).equals(state.selected)) return target;
        return null;
    }

    public static CefBrowser browserForDisplay(ScreenBlockEntity workstation, BlockSide side) {
        DataCableService.RackModuleEndpoint selectedRack = selectedRackTarget(workstation, side);
        if (selectedRack != null) {
            ScreenData home = workstation.getScreen(side);
            if (home != null) {
                CefBrowser browser = RackClientApps.browser(selectedRack, width(home), height(home) - footerPixels(home));
                if (browser != null) return browser;
            }
        }
        DataCableService.ScreenEndpoint selected = selectedTarget(workstation, side);
        if (selected != null) {
            ScreenData screen = selected.entity().getScreen(selected.side());
            if (screen != null) {
                if (screen.browser == null) screen.createBrowser(selected.entity(), false);
                if (screen.browser != null) return screen.browser;
            }
        }
        ScreenData home = workstation.getScreen(side);
        return home == null ? null : home.browser;
    }

    public static CefBrowser browserForInput(ScreenBlockEntity workstation, BlockSide side) {
        if (!canInteract(workstation, side)) return null;
        CefBrowser browser = browserForDisplay(workstation, side);
        if (selectedRackTarget(workstation, side) != null) signalTraffic(workstation, side);
        DataCableService.ScreenEndpoint selected = selectedTarget(workstation, side);
        if (selected != null && !BlockRegistry.isBrowserScreen(selected.entity().getBlockState().getBlock()))
            signalTraffic(workstation, side);
        if (browser instanceof MCEFBrowser mcef) mcef.setFocus(true);
        return browser;
    }

    private static void signalTraffic(ScreenBlockEntity workstation, BlockSide side) {
        if (workstation.getLevel() == null || !workstation.getLevel().isClientSide) return;
        State state = state(workstation, side);
        DataCableService.RackModuleEndpoint rackTarget = selectedRackTarget(workstation, side);
        if (rackTarget != null) {
            RackKey targetKey = key(rackTarget);
            long tick = workstation.getLevel().getGameTime();
            if (targetKey.equals(state.lastRackTrafficTarget) && state.lastTrafficTick != Long.MIN_VALUE &&
                    tick >= state.lastTrafficTick && tick - state.lastTrafficTick < 3L) return;
            state.lastTrafficTick = tick;
            state.lastRackTrafficTarget = targetKey;
            WDNetworkRegistry.INSTANCE.sendToServer(new C2SMessageRackActivity(workstation.getBlockPos(), side,
                    rackTarget.rack().getBlockPos(), rackTarget.module().id()));
            return;
        }
        DataCableService.ScreenEndpoint target = selectedTarget(workstation, side);
        if (target == null) return;
        Key targetKey = key(target);
        long tick = workstation.getLevel().getGameTime();
        if (targetKey.equals(state.lastTrafficTarget) && state.lastTrafficTick != Long.MIN_VALUE &&
                tick >= state.lastTrafficTick && tick - state.lastTrafficTick < 3L) return;
        state.lastTrafficTick = tick;
        state.lastTrafficTarget = targetKey;
        C2SMessageDataActivity.send(workstation, side, target.entity(), target.side());
    }

    public static void sendRemoteMouse(ScreenBlockEntity workstation, BlockSide side,
                                       ClickControl.ControlType event, Vector2i point, int button) {
        if (!canInteract(workstation, side) || workstation.getLevel() == null ||
                !workstation.getLevel().isClientSide || event == null) return;
        State state = state(workstation, side);
        if (selectedRackTarget(workstation, side) != null) return;
        DataCableService.ScreenEndpoint selected = selectedTarget(workstation, side);
        DataCableService.ScreenEndpoint target = event == ClickControl.ControlType.UP && state.pressedRemoteTarget != null
                ? state.pressedRemoteTarget : selected;
        if (target == null || !BlockRegistry.isBrowserScreen(target.entity().getBlockState().getBlock())) {
            if (event == ClickControl.ControlType.UP) state.pressedRemoteTarget = null;
            return;
        }
        Vector2i mapped = selected != null && key(target).equals(key(selected))
                ? mapToTarget(workstation, side, point) : null;
        if (mapped == null && event == ClickControl.ControlType.UP)
            mapped = new Vector2i(state.lastMouseX, state.lastMouseY);
        if (mapped == null) return;
        if (event == ClickControl.ControlType.MOVE) {
            long tick = workstation.getLevel().getGameTime();
            Key targetKey = key(target);
            if (targetKey.equals(state.lastRemoteMoveTarget) && state.lastRemoteMoveTick != Long.MIN_VALUE &&
                    tick >= state.lastRemoteMoveTick && tick - state.lastRemoteMoveTick < 2L) return;
            state.lastRemoteMoveTick = tick;
            state.lastRemoteMoveTarget = targetKey;
        }
        state.lastMouseX = mapped.x;
        state.lastMouseY = mapped.y;
        if (event == ClickControl.ControlType.DOWN) state.pressedRemoteTarget = target;
        if (event == ClickControl.ControlType.UP) state.pressedRemoteTarget = null;
        WDNetworkRegistry.INSTANCE.sendToServer(C2SMessageWorkstationInput.mouse(
                workstation, side, target.entity(), target.side(), event, mapped, button));
    }

    public static int footerPixels(ScreenData workstationScreen) {
        return Math.min(FOOTER_PIXELS, Math.max(12, height(workstationScreen) / 5));
    }

    private static int width(ScreenData screen) {
        return screen.rotation.isVertical ? screen.resolution.y : screen.resolution.x;
    }

    private static int height(ScreenData screen) {
        return screen.rotation.isVertical ? screen.resolution.x : screen.resolution.y;
    }

    public static float contentBottomY(ScreenData workstationScreen, float halfHeight) {
        return -halfHeight + 2f * halfHeight * footerPixels(workstationScreen) / Math.max(1, height(workstationScreen));
    }

    public static Vector2i mapToTarget(ScreenBlockEntity workstation, BlockSide side, Vector2i point) {
        ScreenData home = workstation.getScreen(side);
        if (home == null || point == null) return null;
        int bodyHeight = Math.max(1, height(home) - footerPixels(home));
        if (point.y < 0 || point.y >= bodyHeight || point.x < 0 || point.x >= width(home)) return null;
        DataCableService.ScreenEndpoint selected = selectedTarget(workstation, side);
        ScreenData active = selected == null ? home : selected.entity().getScreen(selected.side());
        if (active == null) return null;
        boolean rack = selectedRackTarget(workstation, side) != null;
        int targetWidth = rack ? width(home) : width(active);
        int targetHeight = rack ? bodyHeight : height(active);
        return new Vector2i(Math.min(targetWidth - 1, Math.max(0, point.x * targetWidth / width(home))),
                Math.min(targetHeight - 1, Math.max(0, point.y * targetHeight / bodyHeight)));
    }

    public static boolean handleMouse(ScreenBlockEntity workstation, BlockSide side, ClickControl.ControlType event, Vector2i point, int button) {
        if (!canInteract(workstation, side)) return true;
        ScreenData home = workstation.getScreen(side);
        if (home == null || home.browser == null) return true;
        State state = state(workstation, side);
        targets(workstation, side, state);
        if (event == ClickControl.ControlType.UP && state.pressedBrowser != null) {
            state.pressedBrowser.sendMouseRelease(state.lastMouseX, state.lastMouseY, button);
            state.pressedBrowser = null;
            return true;
        }
        if (point != null && point.y >= height(home) - footerPixels(home)) {
            if (button == 0 && (event == ClickControl.ControlType.DOWN || event == ClickControl.ControlType.CLICK)) {
                int pixelWidth = Math.max(1, width(home));
                float x = (float) point.x / pixelWidth;
                int visible = visibleCount(home);
                if (x < HOME_END) { state.selected = null; state.selectedRack = null; }
                else if (x < BACK_END) state.firstVisible = Math.max(0, state.firstVisible - visible);
                else if (x >= TABS_END) state.firstVisible = Math.min(Math.max(0, total(state) - visible), state.firstVisible + visible);
                else {
                    int slot = Math.min(visible - 1, (int) ((x - BACK_END) / (TABS_END - BACK_END) * visible));
                    int index = state.firstVisible + slot;
                    choose(state, index);
                }
                signalTraffic(workstation, side);
            }
            return true;
        }
        CefBrowser browser = browserForDisplay(workstation, side);
        if (!(browser instanceof MCEFBrowser mcef)) return true;
        Vector2i mapped = point == null ? null : mapToTarget(workstation, side, point);
        if (mapped != null) {
            state.lastMouseX = mapped.x;
            state.lastMouseY = mapped.y;
        }
        if (mapped != null) {
            DataCableService.ScreenEndpoint selected = selectedTarget(workstation, side);
            if (selectedRackTarget(workstation, side) != null ||
                    selected != null && !BlockRegistry.isBrowserScreen(selected.entity().getBlockState().getBlock()))
                signalTraffic(workstation, side);
            mcef.sendMouseMove(mapped.x, mapped.y);
            if (event == ClickControl.ControlType.DOWN || event == ClickControl.ControlType.CLICK) {
                mcef.sendMousePress(mapped.x, mapped.y, button);
                if (event == ClickControl.ControlType.DOWN) state.pressedBrowser = mcef;
            }
            if (event == ClickControl.ControlType.CLICK)
                mcef.sendMouseRelease(mapped.x, mapped.y, button);
        }
        mcef.setFocus(true);
        return true;
    }

    public static boolean mouseWheel(ScreenBlockEntity workstation, BlockSide side, double amount) {
        if (!canInteract(workstation, side)) return true;
        State state = state(workstation, side);
        CefBrowser browser = browserForDisplay(workstation, side);
        if (!(browser instanceof MCEFBrowser mcef)) return false;
        signalTraffic(workstation, side);
        mcef.sendMouseWheel(state.lastMouseX, state.lastMouseY, amount, 0);
        return true;
    }

    public static JsonObject status(ScreenBlockEntity workstation, BlockSide side) {
        State state = state(workstation, side);
        List<DataCableService.ScreenEndpoint> targets = targets(workstation, side, state);
        JsonObject result = new JsonObject();
        result.addProperty("count", total(state));
        JsonArray entries = new JsonArray();
        Map<String, Integer> counts = new HashMap<>();
        for (int index = 0; index < targets.size(); index++) {
            DataCableService.ScreenEndpoint target = targets.get(index);
            JsonObject entry = new JsonObject();
            String type = shortType(target);
            int number = counts.merge(type, 1, Integer::sum);
            entry.addProperty("index", index);
            entry.addProperty("label", type + " " + number);
            entry.addProperty("name", target.entity().getBlockState().getBlock().getName().getString());
            entry.addProperty("position", target.entity().getBlockPos().toShortString());
            entries.add(entry);
        }
        for (int i = 0; i < state.rackTargets.size(); i++) {
            DataCableService.RackModuleEndpoint target = state.rackTargets.get(i);
            JsonObject entry = new JsonObject();
            String type = shortType(target);
            int number = counts.merge(type, 1, Integer::sum);
            entry.addProperty("index", targets.size() + i);
            entry.addProperty("label", type + " " + number);
            entry.addProperty("name", "Rack " + type);
            entry.addProperty("position", target.rack().getBlockPos().toShortString() + " / U" +
                    (target.module().startU() + 1));
            entries.add(entry);
        }
        result.add("devices", entries);
        return result;
    }

    public static boolean select(ScreenBlockEntity workstation, BlockSide side, int index) {
        if (!canInteract(workstation, side)) return false;
        State state = state(workstation, side);
        targets(workstation, side, state);
        if (index < 0 || index >= total(state)) return false;
        choose(state, index);
        signalTraffic(workstation, side);
        int visible = visibleCount(workstation.getScreen(side));
        if (index < state.firstVisible || index >= state.firstVisible + visible)
            state.firstVisible = index / visible * visible;
        return true;
    }

    private static int visibleCount(ScreenData screen) {
        return Math.max(1, Math.min(6, (screen.rotation.isVertical ? screen.size.y : screen.size.x) * 3));
    }

    private static String shortType(DataCableService.ScreenEndpoint target) {
        var block = target.entity().getBlockState().getBlock();
        if (BlockRegistry.isSshScreen(block)) return "SSH";
        if (BlockRegistry.isSftpScreen(block)) return "SFTP";
        if (BlockRegistry.isLogScreen(block)) return "LOG";
        if (BlockRegistry.isDevToolsScreen(block)) return "DEV";
        if (BlockRegistry.isRemoteScreen(block)) {
            ScreenData screen = target.entity().getScreen(target.side());
            return screen == null || screen.browser == null ? "REM" : RemoteClientSessions.protocolLabel(screen.browser);
        }
        if (BlockRegistry.isTerminalScreen(block)) return "TERM";
        if (BlockRegistry.isProxyScreen(block)) return "PROXY";
        if (BlockRegistry.isBrowserScreen(block)) return "WEB";
        return "TELA";
    }

    private static String shortType(DataCableService.RackModuleEndpoint target) {
        return switch (target.module().type()) {
            case BROWSER -> "WEB";
            case SSH -> "SSH";
            case SFTP -> "SFTP";
            case LOG -> "LOG";
            case DEVTOOLS -> "DEV";
            case TERMINAL -> "TERM";
            case REMOTE -> "REM";
            case PROXY -> "PROXY";
            default -> "RACK";
        };
    }

    public static void renderFooter(ScreenBlockEntity workstation, ScreenData screen, PoseStack poseStack,
                                    MultiBufferSource bufferSource, float halfWidth, float halfHeight, int packedLight) {
        State state = state(workstation, screen.side);
        List<DataCableService.ScreenEndpoint> targets = targets(workstation, screen.side, state);
        float left = -halfWidth;
        float right = halfWidth;
        float bottom = -halfHeight;
        float top = contentBottomY(screen, halfHeight);
        float span = right - left;
        quad(poseStack, left, bottom, right, top, 0xFF101C2C);
        quad(poseStack, left, top - .008f, right, top, 0xFF3F98C8);
        float homeRight = left + span * HOME_END;
        if (state.selected == null && state.selectedRack == null) quad(poseStack, left, bottom, homeRight, top - .01f, 0xFF245071);
        drawLabel("NCAT", left, homeRight, top, bottom, poseStack, bufferSource, LightTexture.FULL_BRIGHT, 0xFFEAF7FF);
        float backRight = left + span * BACK_END;
        drawLabel("<", homeRight, backRight, top, bottom, poseStack, bufferSource, LightTexture.FULL_BRIGHT, 0xFF98BBD0);
        float tabsRight = left + span * TABS_END;
        int visible = visibleCount(screen);
        Map<String, Integer> numbers = new HashMap<>();
        for (int i = 0; i < total(state); i++) {
            boolean physical = i < targets.size();
            String type = physical ? shortType(targets.get(i)) : shortType(state.rackTargets.get(i - targets.size()));
            numbers.merge(type, 1, Integer::sum);
            if (i < state.firstVisible || i >= state.firstVisible + visible) continue;
            int slot = i - state.firstVisible;
            float tabLeft = backRight + (tabsRight - backRight) * slot / visible;
            float tabRight = backRight + (tabsRight - backRight) * (slot + 1) / visible;
            if (physical ? key(targets.get(i)).equals(state.selected) :
                    key(state.rackTargets.get(i - targets.size())).equals(state.selectedRack))
                quad(poseStack, tabLeft + .002f, bottom, tabRight - .002f, top - .01f, 0xFF245071);
            drawLabel(type + numbers.get(type), tabLeft, tabRight, top, bottom, poseStack,
                    bufferSource, LightTexture.FULL_BRIGHT, 0xFFEAF7FF);
        }
        drawLabel(">", tabsRight, right, top, bottom, poseStack, bufferSource, LightTexture.FULL_BRIGHT, 0xFF98BBD0);
    }

    private static void drawLabel(String value, float left, float right, float top, float bottom,
                                  PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int color) {
        Font font = Minecraft.getInstance().font;
        float maxWidth = Math.max(.01f, right - left - .01f);
        float scale = Math.min((top - bottom) / 13f, maxWidth / Math.max(1, font.width(value)));
        float textWidth = font.width(value) * scale;
        poseStack.pushPose();
        poseStack.translate((left + right - textWidth) * .5f, top - (top - bottom - 8f * scale) * .5f, .512f);
        poseStack.scale(scale, -scale, scale);
        font.drawInBatch(value, 0, 0, color, false, poseStack.last().pose(), bufferSource,
                Font.DisplayMode.NORMAL, 0, packedLight);
        poseStack.popPose();
    }

    private static void quad(PoseStack poseStack, float left, float bottom, float right, float top, int argb) {
        float alpha = ((argb >>> 24) & 255) / 255f;
        float red = ((argb >>> 16) & 255) / 255f;
        float green = ((argb >>> 8) & 255) / 255f;
        float blue = (argb & 255) / 255f;
        Matrix4f matrix = poseStack.last().pose();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        builder.vertex(matrix, left, bottom, .508f).color(red, green, blue, alpha).endVertex();
        builder.vertex(matrix, right, bottom, .508f).color(red, green, blue, alpha).endVertex();
        builder.vertex(matrix, right, top, .508f).color(red, green, blue, alpha).endVertex();
        builder.vertex(matrix, left, top, .508f).color(red, green, blue, alpha).endVertex();
        Tesselator.getInstance().end();
    }
}
