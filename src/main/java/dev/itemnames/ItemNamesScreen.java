package dev.itemnames;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class ItemNamesScreen extends Screen {
    private static final int ROWS = 6, MAX_STOPS = 6;
    /** Suchtexte (Name in der gewaehlten Minecraft-Sprache + ID), neu aufgebaut wenn die Sprache wechselt. */
    private static Map<Item, String> labels;
    private static String labelsLang;
    private final Screen parent;
    private final List<Item> filtered = new ArrayList<>();
    private final List<Button> rows = new ArrayList<>();
    private final List<Button> stopBtns = new ArrayList<>();
    private final List<Integer> stops = new ArrayList<>();
    private EditBox search, name;
    private Button boldBtn, italicBtn, colorBtn;
    private Item selected;
    private boolean bold, italic, loading;
    private String query = "";
    private int page, si, rx, pw;

    public ItemNamesScreen(Screen parent) {
        super(Component.translatable("itemnames.title"));
        this.parent = parent;
    }

    private static Component tr(String key) { return Component.translatable(key); }

    @Override
    protected void init() {
        String lang = minecraft.getLanguageManager().getSelected();
        if (labels == null || !lang.equals(labelsLang)) {
            labels = new LinkedHashMap<>();
            for (Item i : BuiltInRegistries.ITEM) {
                ItemStack s = i.getDefaultInstance();
                labels.put(i, (i.getName(s).getString() + " " + ItemNames.key(i)).toLowerCase());
            }
            labelsLang = lang;
        }
        rx = 190;
        pw = Math.min(210, width - rx - 10);
        int keepSi = si;

        search = new EditBox(font, 20, 28, 150, 16, tr("itemnames.search"));
        search.setHint(tr("itemnames.search.hint"));
        search.setValue(query);
        search.setResponder(s -> { query = s; page = 0; refilter(s); rebuildRows(); });
        addRenderableWidget(search);

        name = new EditBox(font, rx, 44, pw, 18, tr("itemnames.name"));
        name.setMaxLength(64);
        name.setHint(tr("itemnames.name.hint"));
        name.setResponder(s -> apply());
        addRenderableWidget(name);

        stopBtns.clear();
        for (int i = 0; i < MAX_STOPS; i++) {
            final int idx = i;
            stopBtns.add(addRenderableWidget(Button.builder(Component.empty(), b -> {
                        if (si == idx && idx < stops.size()) openPicker();
                        else { si = idx; syncStops(); }
                    })
                    .bounds(rx + i * 26, 92, 22, 20).build()));
        }
        colorBtn = addRenderableWidget(Button.builder(tr("itemnames.pick"), b -> openPicker())
                .bounds(rx, 118, pw, 20).build());

        boldBtn = addRenderableWidget(Button.builder(tr("itemnames.bold"), b -> { bold = !bold; apply(); updateBtns(); })
                .bounds(rx, 176, 46, 20).build());
        italicBtn = addRenderableWidget(Button.builder(tr("itemnames.italic"), b -> { italic = !italic; apply(); updateBtns(); })
                .bounds(rx + 50, 176, 50, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> addStop()).bounds(rx + 104, 176, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-"), b -> removeStop()).bounds(rx + 132, 176, 24, 20).build());
        addRenderableWidget(Button.builder(tr("itemnames.reset"), b -> reset()).bounds(rx + 160, 176, 50, 20).build());

        addRenderableWidget(Button.builder(Component.literal("<"), b -> { if (page > 0) { page--; rebuildRows(); } })
                .bounds(20, 54 + ROWS * 22, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> { if ((page + 1) * ROWS < filtered.size()) { page++; rebuildRows(); } })
                .bounds(100, 54 + ROWS * 22, 70, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 50, height - 26, 100, 20).build());

        refilter(query);
        page = Math.max(0, Math.min(page, Math.max(0, (filtered.size() - 1) / ROWS)));
        rebuildRows();
        if (selected != null) {
            select(selected);
            si = Math.max(0, Math.min(keepSi, stops.size() - 1));
        }
        updateBtns();
        syncStops();
    }

    private void openPicker() {
        if (selected == null || stops.isEmpty()) return;
        final int idx = si;
        minecraft.setScreen(new ColorPickerScreen(this, stops.get(idx), c -> {
            if (idx < stops.size()) { stops.set(idx, c); apply(); }
        }));
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
        syncStops();
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

    private void addStop() {
        if (selected == null || stops.size() >= MAX_STOPS) return;
        stops.add(stops.isEmpty() ? 0xFF5EC8 : stops.get(stops.size() - 1));
        si = stops.size() - 1;
        syncStops();
        apply();
    }

    private void removeStop() {
        if (selected == null || stops.isEmpty()) return;
        stops.remove(si);
        si = Math.max(0, Math.min(si, stops.size() - 1));
        syncStops();
        apply();
    }

    private void syncStops() {
        colorBtn.active = !stops.isEmpty();
        for (int i = 0; i < stopBtns.size(); i++) stopBtns.get(i).visible = i < stops.size();
    }

    private void reset() {
        if (selected == null) return;
        ItemNames.entries.remove(ItemNames.key(selected));
        select(selected);
    }

    private void updateBtns() {
        boldBtn.setMessage(bold ? Component.literal(tr("itemnames.bold").getString() + "*") : tr("itemnames.bold"));
        italicBtn.setMessage(italic ? Component.literal(tr("itemnames.italic").getString() + "*") : tr("itemnames.italic"));
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
        g.drawString(font, tr("itemnames.name"), rx, 32, 0xFFAAAAAA);
        g.drawString(font, tr("itemnames.gradient"), rx, 68, 0xFFAAAAAA);
        if (stops.isEmpty()) {
            g.fill(rx, 80, rx + pw, 88, 0xFF555555);
            g.drawString(font, tr("itemnames.gradient.none"), rx, 98, 0xFF888888);
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
            // Vorschau im Tooltip-Stil
            g.drawString(font, tr("itemnames.preview"), rx, 144, 0xFFAAAAAA);
            g.fill(rx, 154, rx + pw, 172, 0xF0100010);
            g.renderOutline(rx, 154, pw, 18, 0xFF3A0E6B);
            g.drawString(font, ItemNames.build(text, spec(), bold, italic), rx + 6, 159, 0xFFFFFFFF);
        } else {
            g.drawString(font, tr("itemnames.select"), rx, 13, 0xFF888888);
        }
    }

    @Override
    public void onClose() {
        ItemNames.save();
        minecraft.setScreen(parent);
    }
}
