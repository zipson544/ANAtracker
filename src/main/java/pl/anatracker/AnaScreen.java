package pl.anatracker;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;

public class AnaScreen extends Screen {
    static final int W = 430, H = 280;
    static final String[] TABS = {"GŁÓWNA", "ZNALEZIENI", "ODWIEDZENI", "ZBANOWANI", "ITEMY", "USTAWIENIA"};
    static final String[] LEVELS = {"WYŁ", "MAŁO", "ŚREDNIO", "BARDZO", "FULL"};
    static final float[] RATES = {0f, 0.5f, 1.6f, 4f, 10f};
    static final int[] COLORS = {0xFFFFC400, 0xFFFF4040, 0xFF40A0FF, 0xFF40E070, 0xFFB060FF, 0xFF40E0E0};
    static int tab = 0;

    record Btn(int x, int y, int w, int h, Runnable r) {}
    record Row(String l1, String l2, String[] labels, Runnable[] acts) {}

    static class Bolt { int[] xs, ys; long born; int life; }

    int px, py;
    String input = "";
    String msg = "";
    boolean focus = false, dragAlpha = false;
    int scroll = 0;
    int[] inputRect = null, sliderRect = null;
    final List<Btn> btns = new ArrayList<>();
    final List<Bolt> bolts = new ArrayList<>();
    final Random rnd = new Random();
    long lastFrame = System.currentTimeMillis();
    float flash = 0f;

    public AnaScreen() { super(Text.literal("AnaTracker")); }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;
    }

    @Override
    public boolean shouldPause() { return false; }

    // ---------- helpers ----------
    static int a(int rgb, int alpha) { return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF); }

    static int lighten(int c) {
        int al = c >>> 24, r = Math.min(255, ((c >> 16) & 255) + 24), g = Math.min(255, ((c >> 8) & 255) + 24), b = Math.min(255, (c & 255) + 24);
        return (al << 24) | (r << 16) | (g << 8) | b;
    }

    static void rr(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x + 2, y, x + w - 2, y + 1, col);
        c.fill(x + 1, y + 1, x + w - 1, y + 2, col);
        c.fill(x, y + 2, x + w, y + h - 2, col);
        c.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, col);
        c.fill(x + 2, y + h - 1, x + w - 2, y + h, col);
    }

    static void outline(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x, y, x + w, y + 1, col);
        c.fill(x, y + h - 1, x + w, y + h, col);
        c.fill(x, y + 1, x + 1, y + h - 1, col);
        c.fill(x + w - 1, y + 1, x + w, y + h - 1, col);
    }

    void btn(DrawContext c, int x, int y, int w, int h, String label, int bg, int fg, boolean border, int mx, int my, Runnable r) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        rr(c, x, y, w, h, hov ? lighten(bg) : bg);
        if (border) outline(c, x - 1, y - 1, w + 2, h + 2, Cfg.I.accent);
        int tw = textRenderer.getWidth(label);
        c.drawText(textRenderer, label, x + (w - tw) / 2, y + (h - 8) / 2, fg, false);
        btns.add(new Btn(x, y, w, h, r));
    }

    // ---------- lightning ----------
    void spawnBolt() {
        Bolt b = new Bolt();
        int n = H / 14 + 1;
        b.xs = new int[n + 1];
        b.ys = new int[n + 1];
        int x = px + 10 + rnd.nextInt(W - 20);
        for (int i = 0; i <= n; i++) {
            b.xs[i] = x;
            b.ys[i] = py + i * 14;
            x += rnd.nextInt(21) - 10;
        }
        b.born = System.currentTimeMillis();
        b.life = 200 + rnd.nextInt(Cfg.I.lightning >= 4 ? 300 : 180);
        bolts.add(b);
        if (Cfg.I.lightning >= 3) flash = 1f;
    }

    void lightning(DrawContext c) {
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        int lvl = Cfg.I.lightning;
        if (lvl == 0) { bolts.clear(); flash = 0; return; }
        if (rnd.nextFloat() < RATES[lvl] * dt) {
            spawnBolt();
            if (lvl >= 2 && rnd.nextFloat() < 0.3f) spawnBolt();
        }
        flash = Math.max(0f, flash - dt * 4f);
        c.enableScissor(px, py, px + W, py + H);
        if (lvl >= 3 && flash > 0f) c.fill(px, py, px + W, py + H, a(0xFFFFFF, (int) (flash * (lvl == 4 ? 70 : 35))));
        Iterator<Bolt> it = bolts.iterator();
        while (it.hasNext()) {
            Bolt b = it.next();
            long age = now - b.born;
            if (age > b.life) { it.remove(); continue; }
            float fade = 1f - (float) age / b.life;
            int core = a(Cfg.I.accent, (int) (255 * fade));
            int glow = a(Cfg.I.accent, (int) (60 * fade));
            for (int i = 0; i + 1 < b.xs.length; i++) {
                int y1 = b.ys[i], y2 = b.ys[i + 1];
                for (int yy = y1; yy < y2; yy++) {
                    double t = (yy - y1) / (double) (y2 - y1);
                    int xx = (int) (b.xs[i] + (b.xs[i + 1] - b.xs[i]) * t);
                    c.fill(xx - 2, yy, xx + 4, yy + 1, glow);
                    c.fill(xx, yy, xx + 2, yy + 1, core);
                }
            }
        }
        c.disableScissor();
    }

    // ---------- render ----------
    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        btns.clear();
        inputRect = null;
        sliderRect = null;
        int ac = Cfg.I.accent, al = Cfg.I.alpha;

        c.fill(0, 0, width, height, 0x40000000);
        c.fill(px, py, px + W, py + H, a(0x141922, al));
        outline(c, px - 1, py - 1, W + 2, H + 2, a(0x3A4356, 255));
        lightning(c);

        // logo
        MatrixStack m = c.getMatrices();
        m.push(); m.translate(px + 14, py + 12, 0); m.scale(2f, 2f, 1f);
        c.drawText(textRenderer, "ANA", 0, 0, 0xFFFFFFFF, true);
        m.pop();
        m.push(); m.translate(px + 14, py + 32, 0); m.scale(1.3f, 1.3f, 1f);
        c.drawText(textRenderer, "TRACKER", 0, 0, ac, true);
        m.pop();
        int bx = px + 104, by = py + 8;
        int[][] bolt = {{6, 0, 4, 4}, {4, 4, 4, 4}, {2, 8, 4, 4}, {0, 12, 8, 3}, {4, 15, 4, 4}, {2, 19, 4, 4}, {0, 23, 3, 4}};
        for (int[] r : bolt) c.fill(bx + r[0], by + r[1], bx + r[0] + r[2], by + r[1] + r[3], ac);

        // tabs (2 rzedy x 3)
        for (int i = 0; i < 6; i++) {
            int tx = px + 134 + (i % 3) * 96, ty = py + 10 + (i / 3) * 21;
            final int idx = i;
            btn(c, tx, ty, 92, 17, TABS[i], a(0x1E2430, 255), i == tab ? 0xFFFFFFFF : 0xFFB0B8C8, i == tab, mx, my, () -> {
                tab = idx; scroll = 0; input = ""; msg = ""; focus = (idx == 3 || idx == 4);
            });
        }

        int cx = px + 10, cy = py + 54, cw = W - 20, ch = H - 54 - 34;
        rr(c, cx, cy, cw, ch, a(0x0C1018, (int) (al * 0.75f)));

        switch (tab) {
            case 0 -> renderMain(c, cx, cy, cw, ch, mx, my);
            case 1, 2, 3, 4 -> renderList(c, cx, cy, cw, ch, mx, my);
            case 5 -> renderSettings(c, cx, cy, cw, ch, mx, my);
        }

        // pasek statusu
        int sy = py + H - 26;
        boolean on = Cfg.I.enabled;
        btn(c, px + 10, sy, 92, 16, "AUTO TP : " + (on ? "ON" : "OFF"), on ? 0xFF1E4A33 : 0xFF4A1E1E, 0xFFFFFFFF, false, mx, my, () -> {
            Cfg.I.enabled = !Cfg.I.enabled; Cfg.save();
        });
        c.fill(px + 14, sy + 6, px + 18, sy + 10, on ? 0xFF40E070 : 0xFFFF4040);
        c.drawText(textRenderer, "Znalezieni " + Cfg.I.found.size() + " | Odwiedzeni " + Cfg.I.visited.size()
                + " | Zbanowani " + Cfg.I.banned.size() + " | Ostatni TP: " + Tracker.lastTp, px + 110, sy + 4, 0xFFB0B8C8, false);
    }

    void renderMain(DrawContext c, int cx, int cy, int cw, int ch, int mx, int my) {
        int x = cx + 10, y = cy + 10;
        c.drawText(textRenderer, "Status moda: " + (Cfg.I.enabled ? "AKTYWNY" : "WYŁĄCZONY"), x, y, Cfg.I.enabled ? 0xFF40E070 : 0xFFFF5555, false);
        c.drawText(textRenderer, "Ostatnio zaakceptował: " + (Tracker.lastAccepter == null ? "-" : Tracker.lastAccepter), x, y + 14, 0xFFFFFFFF, false);
        c.drawText(textRenderer, "Ostatni TP: " + Tracker.lastTp, x, y + 28, 0xFFFFFFFF, false);
        c.drawText(textRenderer, "Szukam itemów: " + Cfg.I.items.size() + " (zakładka ITEMY)", x, y + 42, 0xFFFFFFFF, false);
        c.drawText(textRenderer, Tracker.status, x, y + 60, Cfg.I.accent, false);
        c.drawText(textRenderer, "Nikt z kolejki - tp tylko do osób piszących na czacie.", x, y + 84, 0xFF8890A0, false);
        c.drawText(textRenderer, "Ctrl+A: dodaj item  |  /anatracker on|off|ban|unban", x, y + 98, 0xFF8890A0, false);
        btn(c, x, cy + ch - 30, 130, 20, Cfg.I.enabled ? "MOD: ON  (kliknij = OFF)" : "MOD: OFF (kliknij = ON)",
                Cfg.I.enabled ? 0xFF1E4A33 : 0xFF4A1E1E, 0xFFFFFFFF, false, mx, my, () -> {
                    Cfg.I.enabled = !Cfg.I.enabled; Cfg.save();
                });
    }

    List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        switch (tab) {
            case 1 -> {
                List<Cfg.Found> l = new ArrayList<>(Cfg.I.found.values());
                Collections.reverse(l);
                for (Cfg.Found f : l) {
                    final String name = f.name;
                    rows.add(new Row(f.name + "   " + (int) f.x + " " + (int) f.y + " " + (int) f.z + "   [" + f.dim + "]",
                            String.join(", ", f.items), new String[]{"BAN", "X"},
                            new Runnable[]{() -> Tracker.ban(name), () -> { Cfg.I.found.remove(name.toLowerCase()); Cfg.save(); }}));
                }
            }
            case 2 -> {
                List<String> l = new ArrayList<>(Cfg.I.visited);
                Collections.reverse(l);
                for (String n : l)
                    rows.add(new Row(n, null, new String[]{"BAN", "X"},
                            new Runnable[]{() -> Tracker.ban(n), () -> { Cfg.I.visited.remove(n); Cfg.save(); }}));
            }
            case 3 -> {
                for (String n : new ArrayList<>(Cfg.I.banned))
                    rows.add(new Row(n, null, new String[]{"X"}, new Runnable[]{() -> { Cfg.I.banned.remove(n); Cfg.save(); }}));
            }
            case 4 -> {
                for (String n : new ArrayList<>(Cfg.I.items))
                    rows.add(new Row(n, null, new String[]{"X"}, new Runnable[]{() -> { Cfg.I.items.remove(n); Cfg.save(); }}));
            }
        }
        return rows;
    }

    void renderList(DrawContext c, int cx, int cy, int cw, int ch, int mx, int my) {
        boolean hasInput = tab == 3 || tab == 4;
        int ly = cy + (hasInput ? 26 : 6), lh = ch - (ly - cy) - 6;

        if (hasInput) {
            int ix = cx + 8, iy = cy + 5, iw = cw - 16 - 58;
            c.fill(ix, iy, ix + iw, iy + 16, 0xFF000000);
            outline(c, ix, iy, iw, 16, focus ? Cfg.I.accent : 0xFF3A4356);
            String shown = input.isEmpty() && !focus ? (tab == 3 ? "Wpisz nick + Enter" : "Wpisz id itemu + Enter (np. diamond_sword)") : input;
            boolean blink = focus && (System.currentTimeMillis() / 500) % 2 == 0;
            c.drawText(textRenderer, shown + (blink ? "_" : ""), ix + 4, iy + 4, input.isEmpty() && !focus ? 0xFF666E80 : 0xFFFFFFFF, false);
            inputRect = new int[]{ix, iy, iw, 16};
            btn(c, ix + iw + 6, iy, 46, 16, "Dodaj", 0xFF2FA866, 0xFFFFFFFF, false, mx, my, this::submit);
            if (!msg.isEmpty()) c.drawText(textRenderer, msg, cx + 8, cy + ch - 12, 0xFF8890A0, false);
            lh -= 12;
        }

        List<Row> rows = buildRows();
        int total = 0;
        for (Row r : rows) total += r.l2 == null ? 18 : 30;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, total - lh)));

        if (rows.isEmpty()) {
            c.drawText(textRenderer, "Brak wpisów.", cx + 10, ly + 4, 0xFF8890A0, false);
            return;
        }
        c.enableScissor(cx, ly, cx + cw, ly + lh);
        int ry = ly - scroll;
        for (Row r : rows) {
            int rh = r.l2 == null ? 18 : 30;
            if (ry + rh > ly && ry < ly + lh) {
                c.drawText(textRenderer, r.l1, cx + 8, ry + 4, r.l2 == null ? 0xFFFFFFFF : Cfg.I.accent, false);
                if (r.l2 != null) c.drawText(textRenderer, r.l2, cx + 8, ry + 16, 0xFFB0B8C8, false);
                int bxx = cx + cw - 8;
                int by2 = ry + (rh - 14) / 2;
                for (int i = r.labels.length - 1; i >= 0; i--) {
                    int bw = textRenderer.getWidth(r.labels[i]) + 10;
                    bxx -= bw;
                    boolean del = r.labels[i].equals("X");
                    btn(c, bxx, by2, bw, 14, r.labels[i], del ? 0xFF4A1E1E : 0xFF3A2A18, 0xFFFFFFFF, false, mx, my, r.acts[i]);
                    if (by2 < ly || by2 + 14 > ly + lh) btns.remove(btns.size() - 1);
                    bxx -= 4;
                }
                c.fill(cx + 6, ry + rh - 1, cx + cw - 6, ry + rh, 0x22FFFFFF);
            }
            ry += rh;
        }
        c.disableScissor();
    }

    void renderSettings(DrawContext c, int cx, int cy, int cw, int ch, int mx, int my) {
        int x = cx + 10, y = cy + 10;
        // pioruny
        c.drawText(textRenderer, "Pioruny w tle:", x, y + 4, 0xFFFFFFFF, false);
        btn(c, x + 140, y, 100, 16, LEVELS[Cfg.I.lightning], 0xFF1E2430, Cfg.I.lightning == 0 ? 0xFFFF7777 : Cfg.I.accent, false, mx, my, () -> {
            Cfg.I.lightning = (Cfg.I.lightning + 1) % 5; Cfg.save();
        });
        // przezroczystosc
        y += 28;
        c.drawText(textRenderer, "Przezroczystość GUI:", x, y + 2, 0xFFFFFFFF, false);
        int sx = x + 140, sw = 200;
        c.fill(sx, y + 3, sx + sw, y + 7, 0xFF2A3140);
        float t = (Cfg.I.alpha - 40) / 215f;
        c.fill(sx, y + 3, sx + (int) (sw * t), y + 7, Cfg.I.accent);
        c.fill(sx + (int) (sw * t) - 2, y, sx + (int) (sw * t) + 2, y + 10, 0xFFFFFFFF);
        c.drawText(textRenderer, (int) (Cfg.I.alpha * 100 / 255f) + "%", sx + sw + 8, y + 2, 0xFFB0B8C8, false);
        sliderRect = new int[]{sx, y - 2, sw, 14};
        // kolor
        y += 28;
        c.drawText(textRenderer, "Kolor akcentu:", x, y + 3, 0xFFFFFFFF, false);
        for (int i = 0; i < COLORS.length; i++) {
            final int col = COLORS[i];
            int sx2 = x + 140 + i * 26;
            c.fill(sx2, y, sx2 + 20, y + 14, col);
            if (col == Cfg.I.accent) outline(c, sx2 - 1, y - 1, 22, 16, 0xFFFFFFFF);
            btns.add(new Btn(sx2, y, 20, 14, () -> { Cfg.I.accent = col; Cfg.save(); }));
        }
        // opoznienie skanu
        y += 28;
        c.drawText(textRenderer, "Opóźnienie skanu (ticki):", x, y + 4, 0xFFFFFFFF, false);
        btn(c, x + 140, y, 18, 16, "-", 0xFF1E2430, 0xFFFFFFFF, false, mx, my, () -> { Cfg.I.scanDelayTicks = Math.max(5, Cfg.I.scanDelayTicks - 5); Cfg.save(); });
        c.drawText(textRenderer, String.valueOf(Cfg.I.scanDelayTicks), x + 166, y + 4, 0xFFFFFFFF, false);
        btn(c, x + 196, y, 18, 16, "+", 0xFF1E2430, 0xFFFFFFFF, false, mx, my, () -> { Cfg.I.scanDelayTicks = Math.min(200, Cfg.I.scanDelayTicks + 5); Cfg.save(); });
        // mod on/off
        y += 28;
        c.drawText(textRenderer, "Cały mod:", x, y + 4, 0xFFFFFFFF, false);
        btn(c, x + 140, y, 100, 16, Cfg.I.enabled ? "ON" : "OFF", Cfg.I.enabled ? 0xFF1E4A33 : 0xFF4A1E1E, 0xFFFFFFFF, false, mx, my, () -> {
            Cfg.I.enabled = !Cfg.I.enabled; Cfg.save();
        });
        c.drawText(textRenderer, "Regexy czatu i komenda tpa: config/anatracker.json", x, cy + ch - 16, 0xFF8890A0, false);
    }

    // ---------- input ----------
    void submit() {
        String v = input.trim();
        if (v.isEmpty()) return;
        if (tab == 3) {
            Tracker.ban(v);
            msg = "Zbanowano " + v;
        } else if (tab == 4) {
            String id = (v.contains(":") ? v : "minecraft:" + v).toLowerCase();
            Identifier i = Identifier.tryParse(id);
            if (i != null && Registries.ITEM.containsId(i)) {
                if (!Cfg.I.items.contains(i.toString())) Cfg.I.items.add(i.toString());
                msg = "Dodano " + i;
            } else msg = "Nieznany item: " + v;
        }
        input = "";
        Cfg.save();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (inputRect != null) {
            focus = mx >= inputRect[0] && mx < inputRect[0] + inputRect[2] && my >= inputRect[1] && my < inputRect[1] + inputRect[3];
        }
        if (sliderRect != null && mx >= sliderRect[0] - 4 && mx <= sliderRect[0] + sliderRect[2] + 4
                && my >= sliderRect[1] && my <= sliderRect[1] + sliderRect[3]) {
            dragAlpha = true;
            setAlpha(mx);
            return true;
        }
        for (int i = btns.size() - 1; i >= 0; i--) {
            Btn b = btns.get(i);
            if (mx >= b.x() && mx < b.x() + b.w() && my >= b.y() && my < b.y() + b.h()) {
                b.r().run();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    void setAlpha(double mx) {
        double t = (mx - sliderRect[0]) / sliderRect[2];
        t = Math.max(0, Math.min(1, t));
        Cfg.I.alpha = 40 + (int) (t * 215);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragAlpha && sliderRect != null) { setAlpha(mx); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragAlpha) { dragAlpha = false; Cfg.save(); }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        scroll -= (int) (vAmount * 14);
        if (scroll < 0) scroll = 0;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 65 && hasControlDown()) { // Ctrl+A -> dodaj item
            tab = 4; scroll = 0; focus = true; msg = "";
            ItemStack s = client != null && client.player != null ? client.player.getMainHandStack() : ItemStack.EMPTY;
            input = s.isEmpty() ? "" : Registries.ITEM.getId(s.getItem()).toString();
            return true;
        }
        if (focus) {
            if (keyCode == 259) { if (!input.isEmpty()) input = input.substring(0, input.length() - 1); return true; }
            if (keyCode == 257 || keyCode == 335) { submit(); return true; }
            if (keyCode == 256) { focus = false; return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (focus && chr >= 32 && chr != 127 && input.length() < 40) { input += chr; return true; }
        return super.charTyped(chr, modifiers);
    }
}
