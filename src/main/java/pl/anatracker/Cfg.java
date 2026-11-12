package pl.anatracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Cfg {
    public static class Found {
        public String name;
        public List<String> items = new ArrayList<>();
        public double x, y, z;
        public String dim = "";
        public long time;
    }

    public boolean enabled = true;
    public int lightning = 2;          // 0 WYL, 1 MALO, 2 SREDNIO, 3 BARDZO, 4 FULL
    public int alpha = 215;            // przezroczystosc panelu 40..255
    public int accent = 0xFFFFC400;
    public int scanDelayTicks = 30;
    public int requestIntervalMs = 2500;
    public String tpaCommand = "tpa %s";
    // grupa 1 = nick osoby piszacej na czacie
    public String chatRegex = "^(?:\\S+\\s)*?<?([A-Za-z0-9_]{3,16})>?\\s*(?:»|:|>)\\s";
    // grupa 1 = nick osoby, ktora zaakceptowala tpa
    public String acceptRegex = "(?i)([A-Za-z0-9_]{3,16})\\s+(?:zaakceptowa[łl]|accepted|przyj[ąa][łl])";
    public List<String> items = new ArrayList<>();
    public LinkedHashSet<String> visited = new LinkedHashSet<>();
    public LinkedHashSet<String> banned = new LinkedHashSet<>();
    public LinkedHashMap<String, Found> found = new LinkedHashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Cfg I = new Cfg();

    static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("anatracker.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Cfg c = GSON.fromJson(Files.readString(p), Cfg.class);
                if (c != null) I = c;
            }
        } catch (Exception ignored) {
        }
        I.fix();
        save();
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(I));
        } catch (Exception ignored) {
        }
    }

    void fix() {
        if (items == null || items.isEmpty()) {
            items = new ArrayList<>();
            for (String s : new String[]{"helmet", "chestplate", "leggings", "boots", "sword", "pickaxe", "axe", "shovel", "hoe"})
                items.add("minecraft:netherite_" + s);
            items.add("minecraft:netherite_ingot");
        }
        if (visited == null) visited = new LinkedHashSet<>();
        if (banned == null) banned = new LinkedHashSet<>();
        if (found == null) found = new LinkedHashMap<>();
        if (alpha < 40) alpha = 40;
        if (alpha > 255) alpha = 255;
        if (lightning < 0 || lightning > 4) lightning = 2;
        if (scanDelayTicks < 5) scanDelayTicks = 5;
    }
}
