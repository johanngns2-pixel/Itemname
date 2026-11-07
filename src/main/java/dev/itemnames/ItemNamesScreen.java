package dev.itemnames;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.awt.Color;
import java.util.*;

public class ItemNamesScreen extends Screen {
    private static final int ROWS = 6, MAX_STOPS = 6;
    private static Map<Item, String> labels;
    private final Screen parent;
    private final List<Item> filtered = new ArrayList<>();
    private final List<Button> rows = new ArrayList<>();
    private final List<Button> stopBtns = new ArrayList<>();
    private final List<Integer> stops = new ArrayList<>();
    private EditBox search, name;
    private Button boldBtn, italicBtn;
    private Slider hue, sat, bri;
    private Item selected;
    private boolean bold, italic, loading;
    private int page, si, rx, pw;

    public ItemNamesScreen(Screen parent) {
        super(Component.literal("Item Names"));
        this.parent = parent;
    }

    /** Regler fuer das Farbauswahl-Menue (Farbton / Saettigung / Helligkeit). */
    private class Slider extends AbstractSliderButton {
        private final String label;
        private final int max;

        Slider(int x, int y, int w, String label, int max) {
            super(x, y, w, 18, Component.empty(), 0);
            this.label = label;
            this.max = max;
            updateMessage();
        }

        void set(int v) { value = v / (double) max; updateMessage(); }
        int get() { return (int) Math.round(value * max); }

        @Override protected void updateMessage() { setMessage(Component.literal(label + ": " + get())); }
        @Override protected void applyValue() { pick(); }
    }

    @Override
    protected void init() {
        if (labels == null) {
            labels = new LinkedHashMap<>();
            for (Item i : BuiltInRegistries.ITEM) {
                ItemStack s = i.getDefaultInstance();
                labels.put(i, (i.getName(s).getString() + " " + ItemNames.key(i)).toLowerCase());
            }
        }
        rx = 190;
        pw = Math.min(210, width - rx - 10);

        search = new EditBox(font, 20, 28, 150, 16, Component.literal("Suche"));
        search.setHint(Component.literal("Item suchen..."));
        search.setResponder(s -> { page = 0; refilter(s); rebuildRows(); });
        addRenderableWidget(search);

        name = new EditBox(font, rx, 44, pw, 18, Component.literal("Name"));
        name.setMaxLength(64);
        name.setHint(Component.literal("Neuer Name"));
        name.setResponder(s -> apply());
        addRenderableWidget(name);

        stopBtns.clear();
        for (int i = 0; i < MAX_STOPS; i++) {
            final int idx = i;
            stopBtns.add(addRenderableWidget(Button.builder(Component.empty(), b -> { si = idx; syncSliders(); })
                    .bounds(rx + i * 26, 92, 22, 20).build()));
        }
        hue = addRenderableWidget(new Slider(rx, 116, pw, "Farbton", 360));
        sat = addRenderableWidget(new Slider(rx, 135, pw, "Saettigung", 100));
        bri = addRenderableWidget(new Slider(rx, 154, pw, "Helligkeit", 100));

        boldBtn = addRenderableWidget(Button.builder(Component.literal("Fett"), b -> { bold = !bold; apply(); updateBtns(); })
                .bounds(rx, 176, 46, 20).build());
        italicBtn = addRenderableWidget(Button.builder(Component.literal("Kursiv"), b -> { italic = !italic; apply(); updateBtns(); })
                .bounds(rx + 50, 176, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> addStop()).bounds(rx + 104, 176, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> removeStop()).bounds(rx + 132, 176, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset"), b -> reset()).bounds(rx + 160, 176, 50, 20).build());

        addRenderableWidget(Button.builder(Component.literal("<"), b -> { if (page > 0) { page--; rebuildRows(); } })
                .bounds(20, 54 + ROWS * 22, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { if ((page + 1) * ROWS < filtered.size()) { page++; rebuildRows(); } })
                .bounds(100, 54 + ROWS * 22, 70, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 50, height - 26, 100, 20).build());
        refilter("");
        rebuildRows();
        updateBtns();
        syncSliders();
    }

    private void refilter(String q) {
        String s = q.toLowerCase().trim();
        filtered.clear();
        for (Map.Entry<Item, String> e : labels.entrySet())
            if (s.isEmpty() || e.getValue().contains(s)) filtered.add(e.getKey());
    }

    private void rebuildRows() {
        for (Button b : rows) removeWidget(b);
        rows.clear();
        for (int i = 0; i < ROWS; i++) {
            int idx = page * ROWS + i;
            if (idx >= filtered.size()) break;
            Item it = filtered.get(idx);
            Button b = Button.builder(it.getName(it.getDefaultInstance()), x -> select(it))
                    .bounds(40, 54 + i * 22, 130, 20).build();
            rows.add(addRenderableWidget(b));
        }
    }

    private void select(Item it) {
        selected = it;
        loading = true;
        ItemNames.Entry e = ItemNames.entries.get(ItemNames.key(it));
        name.setValue(e != null ? e.name : "");
        stops.clear();
        if (e != null) for (String s : e.colors.split("[,; ]+")) {
            s = s.replace("#", "").trim();
            if (s.length() != 6) continue;
            try { stops.add(Integer.parseInt(s, 16)); } catch (NumberFormatException ignored) {}
        }
        si = 0;
        bold = e != null && e.bold;
        italic = e != null && e.italic;
        loading = false;
        updateBtns();
        syncSliders();
    }

    private String spec() {
        StringBuilder sb = new StringBuilder();
        for (int c : stops) { if (sb.length() > 0) sb.append(','); sb.append(String.format("#%06X", c)); }
        return sb.toString();
    }

    private void apply() {
        if (loading || selected == null) return;
        String k = ItemNames.key(selected);
        if (name.getValue().isBlank() && stops.isEmpty() && !bold && !italic) {
            ItemNames.entries.remove(k);
            return;
        }
        ItemNames.Entry e = new ItemNames.Entry();
        e.name = name.getValue();
        e.colors = spec();
        e.bold = bold;
        e.italic = italic;
        ItemNames.entries.put(k, e);
    }

    private void pick() {
        if (loading || selected == null || stops.isEmpty()) return;
        stops.set(si, Color.HSBtoRGB(hue.get() / 360f, sat.get() / 100f, bri.get() / 100f) & 0xFFFFFF);
        apply();
    }

    private void addStop() {
        if (selected == null || stops.size() >= MAX_STOPS) return;
        stops.add(stops.isEmpty() ? 0xFF5EC8 : stops.get(stops.size() - 1));
        si = stops.size() - 1;
        syncSliders();
        apply();
    }

    private void removeStop() {
        if (selected == null || stops.isEmpty()) return;
        stops.remove(si);
        si = Math.max(0, Math.min(si, stops.size() - 1));
        syncSliders();
        apply();
    }

    private void syncSliders() {
        loading = true;
        boolean has = !stops.isEmpty();
        hue.active = sat.active = bri.active = has;
        if (has) {
            int c = stops.get(si);
            float[] hsb = Color.RGBtoHSB((c >> 16) & 255, (c >> 8) & 255, c & 255, null);
            hue.set(Math.round(hsb[0] * 360));
            sat.set(Math.round(hsb[1] * 100));
            bri.set(Math.round(hsb[2] * 100));
        }
        for (int i = 0; i < stopBtns.size(); i++) stopBtns.get(i).visible = i < stops.size();
        loading = false;
    }

    private void reset() {
        if (selected == null) return;
        ItemNames.entries.remove(ItemNames.key(selected));
        select(selected);
    }

    private void updateBtns() {
        boldBtn.setMessage(Component.literal(bold ? "Fett*" : "Fett"));
        italicBtn.setMessage(Component.literal(italic ? "Kursiv*" : "Kursiv"));
    }

    /** Vanilla-Optik: dunkle Panels mit hellem/dunklem Rand wie in den Minecraft-Menues. */
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float delta) {
        super.renderBackground(g, mx, my, delta);
        panel(g, 12, 6, 178, height - 32);
        panel(g, rx - 8, 6, Math.min(width - 6, rx + pw + 8), height - 32);
    }

    private static void panel(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fill(x1, y1, x2, y2, 0xB0000000);
        g.fill(x1, y1, x2, y1 + 1, 0xFF555555);
        g.fill(x1, y2 - 1, x2, y2, 0xFF1A1A1A);
        g.fill(x1, y1, x1 + 1, y2, 0xFF555555);
        g.fill(x2 - 1, y1, x2, y2, 0xFF1A1A1A);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float delta) {
        super.render(g, mx, my, delta);
        g.drawString(font, title, 20, 12, 0xFFFFFFFF);
        for (int i = 0; i < rows.size(); i++)
            g.renderItem(filtered.get(page * ROWS + i).getDefaultInstance(), 20, 54 + i * 22 + 2);
        g.drawString(font, Component.literal("Name"), rx, 32, 0xFFAAAAAA);
        g.drawString(font, Component.literal("Farbverlauf (Farbe waehlen, + fuegt eine hinzu)"), rx, 68, 0xFFAAAAAA);
        if (stops.isEmpty()) {
            g.fill(rx, 80, rx + pw, 88, 0xFF555555);
            g.drawString(font, Component.literal("Keine Farbe - mit + hinzufuegen"), rx, 98, 0xFF888888);
        } else {
            for (int px = 0; px < pw; px++)
                g.fill(rx + px, 80, rx + px + 1, 88, 0xFF000000 | ItemNames.colorAt(spec(), px / (float) Math.max(1, pw - 1)));
            for (int i = 0; i < stops.size(); i++) {
                int x = rx + i * 26;
                g.fill(x + 3, 95, x + 19, 109, 0xFF000000 | stops.get(i));
                if (i == si) g.renderOutline(x, 92, 22, 20, 0xFFFFFFFF);
            }
        }
        if (selected != null) {
            String text = name.getValue().isBlank() ? selected.getName(selected.getDefaultInstance()).getString() : name.getValue();
            g.renderItem(selected.getDefaultInstance(), rx, 8);
            g.drawString(font, ItemNames.build(text, spec(), bold, italic), rx + 22, 13, 0xFFFFFFFF);
        } else {
            g.drawString(font, Component.literal("Links ein Item auswaehlen"), rx, 13, 0xFF888888);
        }
    }

    @Override
    public void onClose() {
        ItemNames.save();
        minecraft.setScreen(parent);
    }
}
