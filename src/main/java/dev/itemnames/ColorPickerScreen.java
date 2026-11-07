package dev.itemnames;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.awt.Color;
import java.util.function.IntConsumer;

/** Farbwaehler als eigenes Fenster: Flaeche fuer Saettigung/Helligkeit, Farbtonleiste, Hex-Feld und Minecraft-Farben. */
public class ColorPickerScreen extends Screen {
    private static final int WW = 230, WH = 182, SZ = 100, HW = 14;
    private static final int[] PALETTE = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};

    private final Screen parent;
    private final int original;
    private final IntConsumer onChange;
    private float h, s, v;
    private EditBox hex;
    private boolean syncing, dragSv, dragHue;
    private int wx, wy;

    public ColorPickerScreen(Screen parent, int color, IntConsumer onChange) {
        super(Component.translatable("itemnames.picker.title"));
        this.parent = parent;
        this.original = color & 0xFFFFFF;
        this.onChange = onChange;
        setHsb(original, true);
    }

    private void setHsb(int rgb, boolean keepHueIfGrey) {
        float[] a = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
        if (!(keepHueIfGrey && (a[1] == 0f || a[2] == 0f) && s + v > 0f)) h = a[0];
        s = a[1];
        v = a[2];
    }

    private int current() { return Color.HSBtoRGB(h, s, v) & 0xFFFFFF; }

    @Override
    protected void init() {
        wx = (width - WW) / 2;
        wy = (height - WH) / 2;

        hex = new EditBox(font, wx + 142, wy + 68, 74, 16, Component.translatable("itemnames.picker.hex"));
        hex.setMaxLength(6);
        hex.setFilter(t -> t.matches("[0-9a-fA-F]*"));
        hex.setValue(String.format("%06X", current()));
        hex.setResponder(t -> {
            if (syncing || t.length() != 6) return;
            setHsb(Integer.parseInt(t, 16), true);
            onChange.accept(current());
        });
        addRenderableWidget(hex);

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> minecraft.setScreen(parent))
                .bounds(wx + 10, wy + WH - 26, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(wx + WW - 110, wy + WH - 26, 100, 20).build());
    }

    private void changed() {
        syncing = true;
        hex.setValue(String.format("%06X", current()));
        syncing = false;
        onChange.accept(current());
    }

    private int sx() { return wx + 10; }
    private int sy() { return wy + 22; }
    private int hx() { return wx + 118; }

    private void pickSv(double mx, double my) {
        s = (float) Math.max(0, Math.min(1, (mx - sx()) / (SZ - 1)));
        v = (float) Math.max(0, Math.min(1, 1 - (my - sy()) / (SZ - 1)));
        changed();
    }

    private void pickHue(double my) {
        h = (float) Math.max(0, Math.min(1, (my - sy()) / (SZ - 1)));
        if (s == 0f) s = 1f;
        if (v == 0f) v = 1f;
        changed();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            double mx = event.x(), my = event.y();
            if (mx >= sx() && mx < sx() + SZ && my >= sy() && my < sy() + SZ) { dragSv = true; pickSv(mx, my); return true; }
            if (mx >= hx() && mx < hx() + HW && my >= sy() && my < sy() + SZ) { dragHue = true; pickHue(my); return true; }
            for (int i = 0; i < PALETTE.length; i++) {
                int px = wx + 142 + (i % 4) * 16, py = wy + 92 + (i / 4) * 14;
                if (mx >= px && mx < px + 14 && my >= py && my < py + 12) {
                    setHsb(PALETTE[i], false);
                    changed();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragSv) { pickSv(event.x(), event.y()); return true; }
        if (dragHue) { pickHue(event.y()); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragSv = dragHue = false;
        return super.mouseReleased(event);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        super.renderBackground(g, mx, my, delta);
        g.fill(wx, wy, wx + WW, wy + WH, 0xE0000000);
        g.renderOutline(wx, wy, WW, WH, 0xFF8A8A8A);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        g.drawString(font, title, wx + 10, wy + 8, 0xFFFFFFFF);

        // Flaeche: Saettigung nach rechts, Helligkeit nach unten
        for (int i = 0; i < SZ; i++) {
            int top = 0xFF000000 | (Color.HSBtoRGB(h, i / (float) (SZ - 1), 1f) & 0xFFFFFF);
            g.fillGradient(sx() + i, sy(), sx() + i + 1, sy() + SZ, top, 0xFF000000);
        }
        g.renderOutline(sx() - 1, sy() - 1, SZ + 2, SZ + 2, 0xFF8A8A8A);
        int mxp = sx() + Math.round(s * (SZ - 1)), myp = sy() + Math.round((1 - v) * (SZ - 1));
        g.renderOutline(mxp - 3, myp - 3, 7, 7, 0xFF000000);
        g.renderOutline(mxp - 2, myp - 2, 5, 5, 0xFFFFFFFF);

        // Farbtonleiste
        for (int j = 0; j < SZ; j++)
            g.fill(hx(), sy() + j, hx() + HW, sy() + j + 1, 0xFF000000 | (Color.HSBtoRGB(j / (float) (SZ - 1), 1f, 1f) & 0xFFFFFF));
        g.renderOutline(hx() - 1, sy() - 1, HW + 2, SZ + 2, 0xFF8A8A8A);
        int hy = sy() + Math.round(h * (SZ - 1));
        g.renderOutline(hx() - 2, hy - 2, HW + 4, 5, 0xFF000000);
        g.renderOutline(hx() - 1, hy - 1, HW + 2, 3, 0xFFFFFFFF);

        // Neu / Alt
        g.fill(wx + 142, wy + 22, wx + 178, wy + 42, 0xFF000000 | current());
        g.fill(wx + 180, wy + 22, wx + 216, wy + 42, 0xFF000000 | original);
        g.renderOutline(wx + 142, wy + 22, 36, 20, 0xFF8A8A8A);
        g.renderOutline(wx + 180, wy + 22, 36, 20, 0xFF8A8A8A);
        g.drawString(font, Component.translatable("itemnames.picker.new"), wx + 142, wy + 45, 0xFFAAAAAA);
        g.drawString(font, Component.translatable("itemnames.picker.old"), wx + 180, wy + 45, 0xFFAAAAAA);
        g.drawString(font, Component.translatable("itemnames.picker.hex"), wx + 142, wy + 57, 0xFFAAAAAA);

        // Minecraft-Farben
        for (int i = 0; i < PALETTE.length; i++) {
            int px = wx + 142 + (i % 4) * 16, py = wy + 92 + (i / 4) * 14;
            g.fill(px, py, px + 14, py + 12, 0xFF000000 | PALETTE[i]);
            g.renderOutline(px, py, 14, 12, (PALETTE[i] == current()) ? 0xFFFFFFFF : 0xFF3A3A3A);
        }
    }

    @Override
    public void onClose() {
        onChange.accept(original);
        minecraft.setScreen(parent);
    }
}
