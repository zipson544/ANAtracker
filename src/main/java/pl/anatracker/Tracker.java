package pl.anatracker;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tracker {
    static final MinecraftClient mc = MinecraftClient.getInstance();

    static long tick = 0;
    static Vec3d prevPos;
    static String prevDim = "";
    static long lastRequestTick = -99999;
    static long lastSendMs = 0;

    public static String lastAccepter = null;
    static long lastAccepterTick = -99999;
    public static String lastTp = "-";
    public static String status = "Czekam na wiadomości na czacie";

    static long scanAt = -1;
    static String scanTarget = null;

    static final Map<String, Long> requested = new HashMap<>();
    static Pattern chatP, acceptP;
    static String chatSrc = "", acceptSrc = "";

    static Pattern chatPattern() {
        if (chatP == null || !chatSrc.equals(Cfg.I.chatRegex)) {
            chatSrc = Cfg.I.chatRegex;
            try { chatP = Pattern.compile(chatSrc); } catch (Exception e) { chatP = Pattern.compile("^$"); }
        }
        return chatP;
    }

    static Pattern acceptPattern() {
        if (acceptP == null || !acceptSrc.equals(Cfg.I.acceptRegex)) {
            acceptSrc = Cfg.I.acceptRegex;
            try { acceptP = Pattern.compile(acceptSrc); } catch (Exception e) { acceptP = Pattern.compile("^$"); }
        }
        return acceptP;
    }

    static boolean online(String n) {
        return mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(n) != null;
    }

    static boolean isSelf(String n) {
        return mc.player != null && mc.player.getName().getString().equalsIgnoreCase(n);
    }

    /** Wywolywane dla kazdej wiadomosci z czatu / gry. */
    public static void onMessage(String text) {
        if (!Cfg.I.enabled || mc.player == null || mc.getNetworkHandler() == null) return;

        // 1) akceptacja tpa - zapamietujemy OSTATNIEGO, ktory zaakceptowal
        if (tick - lastRequestTick < 1200) {
            Matcher a = acceptPattern().matcher(text);
            if (a.find()) {
                String n = a.group(1);
                if (online(n) && !isSelf(n)) {
                    lastAccepter = n;
                    lastAccepterTick = tick;
                    status = "Zaakceptował: " + n + " (odliczanie od nowa)";
                    return;
                }
            }
        }

        // 2) ktos pisze na czacie -> tpa
        Matcher m = chatPattern().matcher(text);
        if (!m.find()) return;
        String nick = m.group(1);
        if (!online(nick) || isSelf(nick)) return;
        String key = nick.toLowerCase();
        if (Cfg.I.visited.contains(key) || Cfg.I.banned.contains(key)) return;
        long now = System.currentTimeMillis();
        Long prev = requested.get(key);
        if (prev != null && now - prev < 10 * 60 * 1000L) return;
        if (now - lastSendMs < Cfg.I.requestIntervalMs) return;

        requested.put(key, now);
        lastSendMs = now;
        lastRequestTick = tick;
        mc.getNetworkHandler().sendChatCommand(String.format(Cfg.I.tpaCommand, nick));
        status = "Wysłano tpa do: " + nick;
    }

    public static void tick() {
        tick++;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) {
            prevPos = null;
            return;
        }
        Vec3d pos = p.getPos();
        String dim = mc.world.getRegistryKey().getValue().toString();
        if (prevPos != null && Cfg.I.enabled && tick - lastRequestTick < 1200
                && (!dim.equals(prevDim) || pos.squaredDistanceTo(prevPos) > 64)) {
            onTeleport();
        }
        prevPos = pos;
        prevDim = dim;

        if (scanAt >= 0 && tick >= scanAt) {
            scanAt = -1;
            doScan();
        }
    }

    static void onTeleport() {
        // cel = ostatni, ktory zaakceptowal (poprzednie odliczania sie resetuja)
        String t = (lastAccepter != null && tick - lastAccepterTick < 400) ? lastAccepter : null;
        lastAccepter = null;
        scanTarget = t;
        scanAt = tick + Cfg.I.scanDelayTicks;
        lastRequestTick = -99999;
        status = "Teleport! Skanuję za chwilę...";
    }

    static void doScan() {
        if (mc.player == null || mc.world == null) return;
        ClientPlayerEntity me = mc.player;
        List<AbstractClientPlayerEntity> list = mc.world.getPlayers();

        String target = scanTarget;
        scanTarget = null;
        if (target == null) {
            double best = 64 * 64;
            for (AbstractClientPlayerEntity pl : list) {
                if (pl == me) continue;
                double d = pl.squaredDistanceTo(me);
                if (d < best) { best = d; target = pl.getName().getString(); }
            }
        }
        if (target != null) {
            Cfg.I.visited.add(target.toLowerCase());
            lastTp = target;
        }

        int count = 0;
        for (AbstractClientPlayerEntity pl : list) {
            if (pl == me) continue;
            String name = pl.getName().getString();
            if (Cfg.I.banned.contains(name.toLowerCase())) continue;
            List<String> got = scanPlayer(pl);
            if (!got.isEmpty()) {
                addFound(pl, name, got);
                count++;
            }
        }
        status = "Skan zakończony (" + (target == null ? "?" : target) + "), znaleziono: " + count;
        Cfg.save();
    }

    static List<String> scanPlayer(AbstractClientPlayerEntity pl) {
        Set<String> want = new HashSet<>(Cfg.I.items);
        List<String> out = new ArrayList<>();
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
        for (EquipmentSlot s : slots) {
            ItemStack st = pl.getEquippedStack(s);
            if (st.isEmpty()) continue;
            String id = Registries.ITEM.getId(st.getItem()).toString();
            if (want.contains(id)) out.add(id.replace("minecraft:", ""));
        }
        return out;
    }

    static void addFound(AbstractClientPlayerEntity pl, String name, List<String> items) {
        String key = name.toLowerCase();
        Cfg.Found f = Cfg.I.found.get(key);
        boolean isNew = f == null;
        if (isNew) { f = new Cfg.Found(); f.name = name; }
        int before = f.items.size();
        for (String i : items) if (!f.items.contains(i)) f.items.add(i);
        f.x = pl.getX(); f.y = pl.getY(); f.z = pl.getZ();
        f.dim = mc.world.getRegistryKey().getValue().toString().replace("minecraft:", "");
        f.time = System.currentTimeMillis();
        Cfg.I.found.remove(key);
        Cfg.I.found.put(key, f);
        if (isNew || f.items.size() > before) {
            mc.player.sendMessage(Text.literal("§e[AnaTracker] §fZnaleziono §a" + name + " §7" + f.items
                    + " §fx" + (int) f.x + " y" + (int) f.y + " z" + (int) f.z), false);
        }
    }

    public static void ban(String nick) {
        String k = nick.toLowerCase();
        Cfg.I.banned.add(k);
        Cfg.I.found.remove(k);
        Cfg.save();
    }
}
