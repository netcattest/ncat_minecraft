package com.netcattest.ncatminecraft.client.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class GuiLogTablet extends Screen {
    private final ItemStack tablet;
    private final Player holder;
    private final JsonObject detail;
    private final List<Line> lines = new ArrayList<>();
    private final List<Fragment> wrappedLines = new ArrayList<>();
    private int scroll;
    private float zoom = 1;
    private boolean portuguese;
    private int cachedWidth = -1;
    private float cachedZoom;

    public GuiLogTablet(ItemStack tablet, Player holder) {
        super(Component.literal("NCAT // LOG"));
        this.tablet = tablet;
        this.holder = holder;
        JsonObject parsed;
        try { parsed = JsonParser.parseString(tablet.getTag().getString("NcatLogDetail")).getAsJsonObject(); }
        catch (RuntimeException error) { parsed = new JsonObject(); }
        detail = parsed;
    }

    private static String value(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString();
    }

    private void buildLines() {
        lines.clear();
        cachedWidth = -1;
        portuguese = minecraft != null && minecraft.getLanguageManager().getSelected().toLowerCase().startsWith("pt");
        section(portuguese ? "REQUISIÇÃO" : "REQUEST");
        add(value(detail, "method") + "  " + value(detail, "fullUrl"));
        add((portuguese ? "Protocolo: " : "Protocol: ") + value(detail, "protocol"));
        add((portuguese ? "Tipo: " : "Type: ") + value(detail, "resourceType"));
        add((portuguese ? "Origem: " : "Referrer: ") + value(detail, "referrer"));
        section(portuguese ? "CABEÇALHOS DA REQUISIÇÃO" : "REQUEST HEADERS");
        add(portuguese ? "Este tablet pode conter cookies e credenciais. Compartilhe-o com cuidado." :
                "This tablet may contain cookies and credentials. Share it carefully.");
        headers("requestHeaders");
        section(portuguese ? "CORPO ENVIADO" : "REQUEST BODY");
        String requestBody = value(detail, "requestBody");
        if (portuguese) {
            requestBody = requestBody.replace("[truncated at 32 KiB]", "[truncado em 32 KiB]")
                    .replace("[request body unavailable]", "[corpo da requisição indisponível]")
                    .replace("[uploaded file: ", "[arquivo enviado: ")
                    .replace("; content unavailable]", "; conteúdo indisponível]");
        }
        add(requestBody.isEmpty() ? "—" : requestBody);
        section(portuguese ? "RESPOSTA" : "RESPONSE");
        add(value(detail, "status") + "  " + value(detail, "statusText"));
        add((portuguese ? "Transferência: " : "Transfer: ") + value(detail, "loadStatus"));
        add((portuguese ? "Recebido: " : "Received: ") + value(detail, "bytes") + " bytes");
        add("MIME: " + value(detail, "mimeType"));
        section(portuguese ? "CABEÇALHOS DA RESPOSTA" : "RESPONSE HEADERS");
        headers("responseHeaders");
        section(portuguese ? "CORPO DA RESPOSTA" : "RESPONSE BODY");
        add(portuguese ? "O MCEF não disponibiliza o corpo da resposta neste ponto de captura." :
                "MCEF does not expose the response body at this capture point.");
    }

    private void section(String heading) { lines.add(new Line(heading, 0xFFB77AFF)); }
    private void add(String content) {
        for (String part : content.split("\\R", -1))
            lines.add(new Line(part.isEmpty() ? " " : part, 0xFFE9EDF4));
    }

    private void headers(String key) {
        JsonElement element = detail.get(key);
        if (element == null || !element.isJsonObject() || element.getAsJsonObject().size() == 0) { add("—"); return; }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
            add("_capture_note".equals(entry.getKey()) && portuguese ?
                    "Cabeçalhos adicionais foram truncados em 16 KiB." :
                    entry.getKey() + ": " + entry.getValue().getAsString());
    }

    @Override
    protected void init() { buildLines(); }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int left = Math.max(8, width / 10);
        int right = width - left;
        int top = Math.max(8, height / 12);
        int bottom = height - top;
        graphics.fill(0, 0, width, height, 0xB8000000);
        graphics.fill(left - 8, top - 8, right + 8, bottom + 8, 0xFF10131D);
        graphics.fill(left - 5, top - 5, right + 5, bottom + 5, 0xFF343B52);
        graphics.fill(left, top, right, bottom, 0xFF171D29);
        graphics.fill(left, top, right, top + 3, 0xFF9653E7);
        graphics.drawString(font, "NCAT // LOG  ·  #" + value(detail, "id"), left + 12, top + 12, 0xFFFFFFFF, false);
        graphics.drawString(font, portuguese ? "- / +  zoom     ↑ / ↓  rolar     Esc  fechar" :
                "- / +  zoom     ↑ / ↓  scroll     Esc  close", left + 12, bottom - 18, 0xFF97A5B8, false);
        int textWidth = (int) ((right - left - 26) / zoom);
        int lineHeight = Math.max(10, (int) (12 * zoom));
        int y = top + 34;
        int visibleBottom = bottom - 30;
        if (cachedWidth != textWidth || cachedZoom != zoom) {
            wrappedLines.clear();
            for (Line line : lines) {
                List<FormattedCharSequence> parts = font.split(Component.literal(line.text), Math.max(80, textWidth));
                for (int i = 0; i < parts.size(); i++)
                    wrappedLines.add(new Fragment(parts.get(i), line.color, line.color == 0xFFB77AFF && i == parts.size() - 1));
            }
            cachedWidth = textWidth;
            cachedZoom = zoom;
        }
        scroll = Math.min(scroll, Math.max(0, wrappedLines.size() - Math.max(1, (visibleBottom - y) / lineHeight)));
        int index = 0;
        for (Fragment fragment : wrappedLines) {
            if (index++ < scroll) continue;
            if (y + lineHeight > visibleBottom) break;
            graphics.pose().pushPose();
            graphics.pose().translate(left + 13, y, 0);
            graphics.pose().scale(zoom, zoom, 1);
            graphics.drawString(font, fragment.text, 0, 0, fragment.color, false);
            graphics.pose().popPose();
            y += lineHeight;
            if (fragment.spacerAfter) y += 4;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll = Math.max(0, scroll - (int) Math.signum(amount) * 3);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_PAGE_UP) { scroll = Math.max(0, scroll - (keyCode == GLFW.GLFW_KEY_UP ? 1 : 12)); return true; }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) { scroll += keyCode == GLFW.GLFW_KEY_DOWN ? 1 : 12; return true; }
        if (keyCode == GLFW.GLFW_KEY_EQUAL || keyCode == GLFW.GLFW_KEY_KP_ADD) { zoom = Math.min(2, zoom + .25f); return true; }
        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) { zoom = Math.max(.75f, zoom - .25f); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void removed() { holder.stopUsingItem(); }

    @Override
    public boolean isPauseScreen() { return false; }

    private record Line(String text, int color) { }
    private record Fragment(FormattedCharSequence text, int color, boolean spacerAfter) { }
}
