package dev.itemnames;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ItemNames {
    public static class Entry {
        public String name = "";
        public String colors = "";   // z.B. "#FF0000,#0000FF" -> Farbverlauf
        public boolean bold, italic;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("itemnames.json");
    public static Map<String, Entry> entries = new LinkedHashMap<>();

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Map<String, Entry> m = GSON.fromJson(Files.readString(FILE), new TypeToken<LinkedHashMap<String, Entry>>() {}.getType());
                if (m != null) entries = m;
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void save() {
        try { Files.writeString(FILE, GSON.toJson(entries)); } catch (Exception e) { e.printStackTrace(); }
    }

    public static String key(net.minecraft.world.item.Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public static Component nameFor(ItemStack stack) {
        if (entries.isEmpty()) return null;
        Entry e = entries.get(key(stack.getItem()));
        if (e == null) return null;
        String text = e.name == null || e.name.isBlank() ? stack.getItem().getName(stack).getString() : e.name;
        return build(text, e.colors, e.bold, e.italic);
    }

    public static MutableComponent build(String text, String colorSpec, boolean bold, boolean italic) {
        List<int[]> cols = parse(colorSpec);
        MutableComponent out = Component.empty();
        int[] cps = text.codePoints().toArray();
        int n = cps.length;
        for (int k = 0; k < n; k++) {
            float t = n <= 1 ? 0f : (float) k / (n - 1);
            Style st = Style.EMPTY.withBold(bold).withItalic(italic);
            if (!cols.isEmpty()) st = st.withColor(sample(cols, t)); // ohne Farbe bleibt die Seltenheitsfarbe des Items
            out.append(Component.literal(new String(Character.toChars(cps[k]))).withStyle(st));
        }
        return out;
    }

    private static List<int[]> parse(String spec) {
        List<int[]> l = new ArrayList<>();
        for (String s : (spec == null ? "" : spec).split("[,; ]+")) {
            s = s.replace("#", "").trim();
            if (s.length() != 6) continue;
            try {
                int v = Integer.parseInt(s, 16);
                l.add(new int[]{(v >> 16) & 255, (v >> 8) & 255, v & 255});
            } catch (NumberFormatException ignored) {}
        }
        return l;
    }

    private static int sample(List<int[]> c, float t) {
        if (c.size() == 1) return pack(c.get(0));
        float p = t * (c.size() - 1);
        int a = (int) Math.floor(p);
        if (a >= c.size() - 1) return pack(c.get(c.size() - 1));
        float f = p - a;
        int[] x = c.get(a), y = c.get(a + 1);
        return pack(new int[]{Math.round(x[0] + (y[0] - x[0]) * f), Math.round(x[1] + (y[1] - x[1]) * f), Math.round(x[2] + (y[2] - x[2]) * f)});
    }

    public static int colorAt(String spec, float t) {
        List<int[]> c = parse(spec);
        return c.isEmpty() ? 0xFFFFFF : sample(c, t);
    }

    private static int pack(int[] c) { return (c[0] << 16) | (c[1] << 8) | c[2]; }
}
