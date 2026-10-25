import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Объёмные модели оружия мода. В руках, на земле и в рамке показывается модель из кубоидов,
 * в инвентаре — плоская HD-иконка (загрузчик neoforge:separate_transforms).
 *
 * Детали задаются в «своих» координатах оружия: x — поперёк, y — вдоль (к острию/вперёд),
 * z — толщина; центр модели в нуле. Поворот всей модели к диагонали иконки сделан в display:
 * у ручного оружия z-угол сдвинут на −45°, у луков и арбалетов — на +45°.
 *
 * Запуск: java tools/Weapon3DGen.java src/main/resources
 */
public class Weapon3DGen {
    static final String NS = "heroicpaths";
    static Path res;

    public static void main(String[] args) throws IOException {
        res = Path.of(args.length > 0 ? args[0] : "src/main/resources");
        makeTextures();
        int n = makeModels();
        System.out.println("models: " + n);
    }

    // =====================================================================================
    // Текстуры материалов 64×64: поперёк (u) — объёмная заливка, вдоль (v) — узор.
    // =====================================================================================

    static final String[] IRON = {"24262E", "5A5F6C", "969CA8", "D0D5DE", "FFFFFF"};
    static final String[] DIAMOND = {"08383A", "168884", "3CC8C0", "9AF2EC", "F2FFFF"};
    static final String[] STEEL = {"161E2C", "3C4A64", "6E86A8", "B2C4DE", "F2F8FF"};
    static final String[] DAMASCUS = {"0E101A", "2C3042", "565C72", "8E96AC", "D2DAEA"};
    static final String[] FACETED = {"0A2448", "1E5A9A", "4AA0E0", "B4E4FF", "FFFFFF"};
    static final String[] BRILLIANT = {"3A3A52", "8A8EB0", "D0D6F0", "F4F6FF", "FFFFFF"};
    static final String[] SILVER = {"2A2E36", "6A707C", "AEB4C0", "E2E6EE", "FFFFFF"};
    static final String[] DARK = {"101218", "262A36", "444A5C", "6E7690", "A8B2CC"};
    static final String[] GOLD = {"3A2606", "7A5414", "C08A2C", "ECC462", "FFF2B8"};
    static final String[] BRASS = {"3A2A0E", "6E5222", "A8823E", "D8B46A", "F8E6B0"};
    static final String[] LEATHER = {"1C1008", "3C2414", "664028", "8C6040"};
    static final String[] BLACK_LEATHER = {"0C0808", "1E1618", "3A2C2E", "584448"};
    static final String[] GREEN_LEATHER = {"0E1A08", "24401A", "3C6428", "5E8C40"};
    static final String[] WOOD = {"28180A", "4C2E16", "784C2C", "A2743E"};
    static final String[] DARK_WOOD = {"100804", "28180E", "442C1A", "684A2E"};
    static final String[] RANGER_WOOD = {"2A1E0A", "5A4420", "8C6E34", "C4A260"};
    static final String[] BLACK_WOOD = {"06060A", "16141E", "2A2636", "4A4458"};
    static final String[] SHAFT_WOOD = {"3A2410", "8A6436", "B08850", "D0A868"};
    static final Map<String, String[]> GEMS = new LinkedHashMap<>();

    static {
        GEMS.put("amber", new String[]{"5A2A04", "C87A1A", "FFD27A"});
        GEMS.put("aqua", new String[]{"0A4A56", "1AA8C0", "B4FFFF"});
        GEMS.put("sapphire", new String[]{"0C1C5A", "2A5AD8", "A8C4FF"});
        GEMS.put("ruby", new String[]{"4A0610", "C01A2C", "FF8A94"});
        GEMS.put("amethyst", new String[]{"2A0E4A", "8A4AD8", "E2C8FF"});
        GEMS.put("emerald", new String[]{"0A3A14", "2AA84A", "B8FFC8"});
        GEMS.put("fire", new String[]{"5A1004", "F0501A", "FFE08A"});
        GEMS.put("frost", new String[]{"0A3A6A", "3AB8F0", "E8FCFF"});
        GEMS.put("earth", new String[]{"243A0A", "7AB83A", "E4F8A8"});
        GEMS.put("storm", new String[]{"2A1A5A", "8A6AF0", "FFF6A8"});
        GEMS.put("prism", new String[]{"3A2A6A", "B8A8FF", "FFFFFF"});
    }

    /** Размер текстур материалов: 128×128, 8 пикселей на единицу модели вдоль детали. */
    static final int N = 128;
    /** Масштаб относительно исходной сетки 64×64: узоры задаются в её координатах. */
    static final double S = N / 64.0;
    /** Ореол — мягкий градиент без деталей, ему хватает 64×64. */
    static final int GN = 64;

    static void makeTextures() throws IOException {
        save(metal(IRON, false), "metal_iron");
        save(metal(DIAMOND, false), "metal_diamond");
        save(metal(STEEL, false), "metal_steel");
        save(metal(DAMASCUS, true), "metal_damascus");
        save(metal(FACETED, false), "metal_faceted");
        save(metal(BRILLIANT, false), "metal_brilliant");
        save(metal(SILVER, false), "metal_silver");
        save(metal(DARK, false), "metal_dark");
        save(metal(GOLD, false), "metal_gold");
        save(metal(BRASS, false), "metal_brass");
        save(grip(LEATHER, "A47C4A"), "grip_iron");
        save(grip(LEATHER, "E2B654"), "grip_diamond");
        save(grip(BLACK_LEATHER, "8E9AB2"), "grip_steel");
        save(grip(BLACK_LEATHER, "A41C2E"), "grip_damascus");
        save(grip(BLACK_LEATHER, "C8D4E8"), "grip_faceted");
        save(grip(BLACK_LEATHER, "F0D070"), "grip_brilliant");
        save(grip(GREEN_LEATHER, "9AC870"), "grip_ranger");
        save(grip(LEATHER, "B08A50"), "grip_bow");
        save(grip(LEATHER, "C8A050"), "grip_focus");
        save(wood(WOOD, null), "wood");
        save(wood(DARK_WOOD, null), "dark_wood");
        save(wood(RANGER_WOOD, null), "ranger_wood");
        save(wood(SHAFT_WOOD, null), "shaft_wood");
        for (String el : new String[]{"fire", "frost", "earth", "storm"}) {
            save(wood(DARK_WOOD, GEMS.get(el)), "rune_wood_" + el);
            save(wood(BLACK_WOOD, GEMS.get(el)), "arch_wood_" + el);
        }
        for (Map.Entry<String, String[]> e : GEMS.entrySet()) {
            save(gem(e.getValue()), "gem_" + e.getKey());
            if (!List.of("amethyst", "fire", "frost", "earth", "storm").contains(e.getKey())) continue;
            save(glow(e.getValue()), "glow_" + e.getKey());
            Files.writeString(res.resolve("assets/" + NS + "/textures/item/3d/glow_" + e.getKey() + ".png.mcmeta"),
                    "{\n  \"animation\": { \"frametime\": 4, \"interpolate\": true }\n}\n", StandardCharsets.UTF_8);
        }
        save(plain("6A6458", "E8E4D8", "FFFFF4", 0), "string");
        save(plain("8A6A20", "F0D890", "FFF4C8", 0), "string_gold");
        save(plain("6A5A3A", "D8C49C", "F4E8CC", 0), "bone");
        save(plain("2E4A1A", "7AA84E", "B4DC86", 0), "belly_green");
        save(plain("5A0A0A", "C01E1E", "F05A4A", 2), "paper_red");
        save(feather(), "feather");
        for (int g = 1; g <= 6; g++) save(shieldFace(g), "shield_face_" + g);
    }

    /**
     * Лицевая сторона щита по грейду (рисунок натягивается на весь щит):
     * I — дубовые доски на гвоздях, II — лазурь с серебряным шевроном, III — чёрно-червлёное поле с золотым солнцем.
     */
    static BufferedImage shieldFace(int grade) {
        BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(grade * 977L);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (grade == 1) {
            int boards = 6;
            double bw = (double) N / boards;
            double[] grain = smoothNoise(r, N, 0.08, 0.5);
            for (int v = 0; v < N; v++)
                for (int u = 0; u < N; u++) {
                    int b = (int) (u / bw);
                    double t = (u - b * bw) / bw;
                    Color base = mix(c(WOOD[2]), c(WOOD[3]), 0.25 + 0.2 * ((b * 37) % 5) / 4.0);
                    double k = 1 + grain[u] + 0.06 * Math.sin(v * 0.05 + b * 1.7 + 2 * Math.sin(u * 0.2));
                    Color col = shade(base, k * (1 + (r.nextDouble() - 0.5) * 0.04));
                    if (t < 0.035 || t > 0.965) col = mix(col, c(WOOD[0]), 0.7);
                    img.setRGB(u, v, col.getRGB());
                }
            g2.setColor(new Color(0x3A3A40));
            for (int b = 0; b < boards; b++)
                for (double y : new double[]{0.18, 0.5, 0.82}) {
                    int cx = (int) ((b + 0.5) * bw), cy = (int) (y * N);
                    g2.fillOval(cx - 4, cy - 4, 8, 8);
                    g2.setColor(new Color(0x9A9AA4));
                    g2.fillOval(cx - 2, cy - 3, 3, 3);
                    g2.setColor(new Color(0x3A3A40));
                }
        } else if (grade == 2) {
            for (int v = 0; v < N; v++)
                for (int u = 0; u < N; u++) {
                    double dx = (u - N / 2.0) / N, dy = (v - N / 2.0) / N;
                    Color col = shade(new Color(0x2A52A8), 1.05 - Math.sqrt(dx * dx + dy * dy) * 0.5 + (r.nextDouble() - 0.5) * 0.05);
                    img.setRGB(u, v, col.getRGB());
                }
            java.awt.Polygon outer = new java.awt.Polygon(new int[]{-10, N / 2, N + 10}, new int[]{N + 10, (int) (N * 0.34), N + 10}, 3);
            java.awt.Polygon inner = new java.awt.Polygon(new int[]{40, N / 2, N - 40}, new int[]{N + 10, (int) (N * 0.62), N + 10}, 3);
            g2.setColor(new Color(0x2A2E3A));
            g2.setStroke(new BasicStroke(6f));
            g2.drawPolygon(outer);
            g2.setColor(new Color(0xE8ECF2));
            g2.fillPolygon(outer);
            g2.setColor(new Color(0x2A2E3A));
            g2.drawPolygon(inner);
            g2.setColor(new Color(0x2A52A8));
            g2.fillPolygon(inner);
            for (int i = 0; i < 260; i++) {
                int u = r.nextInt(N), v = r.nextInt(N);
                blend(img, u, v, Color.BLACK, 0.12);
            }
        } else if (grade >= 4) {
            // IV–VI: поле цвета камня и огранённый ромб посередине — алмаз, гранёный алмаз, бриллиант.
            Color field = grade == 4 ? new Color(0x0E4A4A) : grade == 5 ? new Color(0x10284E) : new Color(0xE8E8F2);
            Color stone = grade == 4 ? new Color(0x5CE0D8) : grade == 5 ? new Color(0x7AC4FF) : new Color(0xC8D0FF);
            Color rim = grade == 6 ? new Color(0xD8A830) : new Color(0xC8D0DC);
            for (int v = 0; v < N; v++)
                for (int u = 0; u < N; u++) {
                    double dx = (u - N / 2.0) / N, dy = (v - N / 2.0) / N;
                    img.setRGB(u, v, shade(field, 1.05 - Math.sqrt(dx * dx + dy * dy) * 0.5 + (r.nextDouble() - 0.5) * 0.05).getRGB());
                }
            int cx = N / 2, cy = (int) (N * 0.45), hw = 30, hh = 44;
            java.awt.Polygon gemShape = new java.awt.Polygon(new int[]{cx, cx + hw, cx, cx - hw}, new int[]{cy - hh, cy, cy + hh, cy}, 4);
            g2.setColor(rim);
            g2.setStroke(new BasicStroke(7f));
            g2.drawPolygon(gemShape);
            g2.setColor(stone);
            g2.fillPolygon(gemShape);
            g2.setColor(stone.brighter());
            g2.fillPolygon(new int[]{cx, cx + hw, cx}, new int[]{cy - hh, cy, cy}, 3);
            g2.setColor(shade(stone, 0.7));
            g2.fillPolygon(new int[]{cx, cx - hw, cx}, new int[]{cy + hh, cy, cy}, 3);
            g2.setColor(new Color(255, 255, 255, 200));
            g2.fillOval(cx - 12, cy - 22, 9, 9);
            if (grade >= 5) {
                g2.setColor(rim);
                g2.setStroke(new BasicStroke(4f));
                g2.drawRect(6, 6, N - 12, N - 12);
            }
        } else {
            for (int v = 0; v < N; v++)
                for (int u = 0; u < N; u++) {
                    Color base = u < N / 2 ? new Color(0x16121C) : new Color(0x7A0E18);
                    img.setRGB(u, v, shade(base, 1 + (r.nextDouble() - 0.5) * 0.06).getRGB());
                }
            double cx = N / 2.0, cy = N * 0.45;
            Color gold = new Color(0xE8B840), dark = new Color(0x6A4A10);
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2 * i / 16, w = Math.PI / 16 * 0.7, r1 = 40, r2 = i % 2 == 0 ? 80 : 64;
                java.awt.Polygon ray = new java.awt.Polygon(
                        new int[]{(int) (cx + r1 * Math.cos(a - w)), (int) (cx + r2 * Math.cos(a)), (int) (cx + r1 * Math.cos(a + w))},
                        new int[]{(int) (cy + r1 * Math.sin(a - w)), (int) (cy + r2 * Math.sin(a)), (int) (cy + r1 * Math.sin(a + w))}, 3);
                g2.setColor(gold);
                g2.fillPolygon(ray);
                g2.setColor(dark);
                g2.setStroke(new BasicStroke(2f));
                g2.drawPolygon(ray);
            }
            g2.setColor(dark);
            g2.fillOval((int) cx - 44, (int) cy - 44, 88, 88);
            g2.setColor(gold);
            g2.fillOval((int) cx - 40, (int) cy - 40, 80, 80);
            g2.setColor(new Color(0xFFF0B0));
            g2.fillOval((int) cx - 22, (int) cy - 26, 26, 22);
        }
        g2.dispose();
        return img;
    }

    static Color c(String hex) {
        return new Color(Integer.parseInt(hex, 16));
    }

    static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static Color shade(Color a, double k) {
        return new Color(clamp(a.getRed() * k), clamp(a.getGreen() * k), clamp(a.getBlue() * k));
    }

    static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    static Color ramp(double t, double[] stops, Color[] cols) {
        for (int i = 1; i < stops.length; i++)
            if (t <= stops[i]) return mix(cols[i - 1], cols[i], (t - stops[i - 1]) / (stops[i] - stops[i - 1]));
        return cols[cols.length - 1];
    }

    /** Сглаженный шум вдоль ряда: соседние значения близки (продольная шлифовка, волокна). */
    static double[] smoothNoise(Random r, int n, double amp, double keep) {
        double[] a = new double[n];
        double prev = 0;
        for (int i = 0; i < n; i++) {
            prev = prev * keep + (r.nextDouble() - 0.5) * (1 - keep);
            a[i] = prev;
        }
        double max = 1e-9;
        for (double x : a) max = Math.max(max, Math.abs(x));
        for (int i = 0; i < n; i++) a[i] = a[i] / max * amp + (r.nextDouble() - 0.5) * amp * 0.5;
        return a;
    }

    /** Тёмный контур по краям поперёк и полированная фаска у одной кромки. */
    static Color edges(Color col, int u, Color outline, Color shine) {
        double x = u / S;
        if (x >= 2 && x < 4) col = mix(col, shine, 0.55 * (1 - (x - 2) / 2) + 0.15);
        if (x >= 60.5 && x < 62) col = mix(col, outline, 0.25);
        if (x < 1 || x >= 63) col = mix(col, outline, 0.6);
        return col;
    }

    /** Металл: блик ближе к левому краю, продольная шлифовка, царапины; у дамаска — волнистые слои стали. */
    static BufferedImage metal(String[] p, boolean damascus) {
        BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        Color[] cols = {c(p[1]), c(p[2]), c(p[3]), c(p[4]), c(p[3]), c(p[2]), c(p[1])};
        double[] st = {0, 0.14, 0.34, 0.44, 0.56, 0.8, 1};
        Random r = new Random(p[2].hashCode());
        double[] streak = smoothNoise(r, N, 0.06, 0.55);
        double[] sheen = smoothNoise(r, N, 0.025, 0.97);
        for (int v = 0; v < N; v++)
            for (int u = 0; u < N; u++) {
                Color base = ramp((u + 0.5) / N, st, cols);
                double k = 1 + streak[u] + sheen[v] + (r.nextDouble() - 0.5) * 0.025;
                if (damascus) {
                    double x = u / S, y = v / S;
                    double w = Math.sin(y * 0.45 + 2.8 * Math.sin(x * 0.19) + 1.4 * Math.sin(y * 0.09 + x * 0.05));
                    double fine = Math.sin(y * 1.9 + 3.2 * Math.sin(x * 0.23 + y * 0.04));
                    k *= 1 + 0.26 * smooth(0.55, 0.85, w) - 0.22 * smooth(0.65, 0.92, -w) + 0.06 * fine;
                }
                img.setRGB(u, v, edges(shade(base, k), u, c(p[0]), c(p[4])).getRGB());
            }
        // Царапины: короткие штрихи почти вдоль детали, светлые и тёмные.
        for (int i = 0; i < 70; i++) {
            double x = 6 * S + r.nextDouble() * (N - 12 * S), y = r.nextDouble() * N;
            double len = 6 + r.nextDouble() * 28, ang = Math.toRadians(80 + r.nextDouble() * 20);
            boolean light = r.nextBoolean();
            for (int s = 0; s < len; s++) {
                int px = (int) Math.round(x + Math.cos(ang) * s), py = (int) Math.round(y + Math.sin(ang) * s) % N;
                if (px < 0 || px >= N || py < 0) continue;
                blend(img, px, py, light ? c(p[4]) : c(p[0]), light ? 0.22 : 0.16);
            }
        }
        return img;
    }

    /** Кожаная рукоять с косой обмоткой шнуром: одна полоса на единицу длины, шнур круглый и витой. */
    static BufferedImage grip(String[] leather, String wrap) {
        BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        Color[] lc = {c(leather[1]), c(leather[3]), c(leather[2]), c(leather[1])};
        Color w = c(wrap);
        Color[] wc = {shade(w, 0.55), shade(w, 1.15), w, shade(w, 0.5)};
        double[] st = {0, 0.32, 0.6, 1};
        Random r = new Random(wrap.hashCode());
        for (int v = 0; v < N; v++)
            for (int u = 0; u < N; u++) {
                double t = (u + 0.5) / N, x = u / S, y = v / S;
                double phase = ((y + x * 0.18) % 4 + 4) % 4;
                Color col;
                if (phase < 2.6) {
                    // Шнур: светлее посередине полосы, тоньше к краям, косые волокна.
                    double round = Math.sin(Math.PI * phase / 2.6);
                    double twist = 0.94 + 0.06 * Math.sin((x * 1.7 - y * 3.1) * 1.3);
                    col = shade(ramp(t, st, wc), (0.72 + 0.34 * round) * twist);
                } else if (phase < 3.1) {
                    col = mix(c(leather[0]), c(leather[1]), smooth(2.6, 3.1, phase) * 0.6);
                } else {
                    col = shade(ramp(t, st, lc), 0.92 + 0.1 * Math.sin(Math.PI * (phase - 3.1) / 0.9));
                }
                col = shade(col, 1 + (r.nextDouble() - 0.5) * 0.05);
                if (u < S || u >= N - S) col = mix(col, c(leather[0]), 0.6);
                img.setRGB(u, v, col.getRGB());
            }
        return img;
    }

    /** Дерево: волокна вдоль, редкие сучки; если задан цвет стихии — светящиеся руны через каждые 4 единицы. */
    static BufferedImage wood(String[] p, String[] rune) {
        BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        Color[] cols = {c(p[1]), c(p[3]), c(p[2]), c(p[1])};
        double[] st = {0, 0.3, 0.62, 1};
        Random r = new Random(p[2].hashCode() * 31L);
        double[] fiber = smoothNoise(r, N, 0.07, 0.35);
        double kx = 0.25 + r.nextDouble() * 0.5, ky = 0.2 + r.nextDouble() * 0.6;
        for (int v = 0; v < N; v++)
            for (int u = 0; u < N; u++) {
                double x = u / S, y = v / S;
                Color col = ramp((u + 0.5) / N, st, cols);
                double g = Math.sin(x * 0.85 + 1.8 * Math.sin(y * 0.07 + x * 0.31));
                double k = 1 - 0.18 * smooth(0.55, 0.8, g) + 0.08 * smooth(0.8, 0.95, -g) + fiber[u];
                // Сучок: кольца вокруг точки.
                double dx = (x - 64 * kx) / 3.0, dy = (y - 64 * ky) / 6.0, d = Math.sqrt(dx * dx + dy * dy);
                if (d < 1.6) k *= 0.86 + 0.1 * Math.cos(d * 9);
                col = shade(col, k * (1 + (r.nextDouble() - 0.5) * 0.04));
                if (u < S || u >= N - S) col = mix(col, c(p[0]), 0.6);
                img.setRGB(u, v, col.getRGB());
            }
        if (rune != null) {
            // Руны: мягкое свечение в несколько проходов, затем яркий штрих и светлая сердцевина.
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            Color glow = c(rune[2]), mid = c(rune[1]);
            double[][] glyphs = {{0, 0, 0, 8, 0, 2, 5, 5}, {0, 0, 5, 8, 5, 0, 0, 8}, {2, 0, 2, 8, 2, 4, 6, 1}, {0, 2, 6, 2, 3, 2, 3, 8}};
            double[][] passes = {{2.6, 40}, {1.7, 80}, {1.0, 255}, {0.45, 255}};
            for (int q = 0; q < 4; q++) {
                double ox = 29 * S, oy = (q * 16 + 4) * S;
                double[] gl = glyphs[q];
                for (int pass = 0; pass < passes.length; pass++) {
                    Color col = pass == 3 ? mix(glow, Color.WHITE, 0.6) : pass == 2 ? glow : mid;
                    g2.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), (int) passes[pass][1]));
                    g2.setStroke(new BasicStroke((float) (passes[pass][0] * S), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    for (int s = 0; s < gl.length; s += 4)
                        g2.draw(new Line2D.Double(ox + gl[s] * S, oy + gl[s + 1] * S, ox + gl[s + 2] * S, oy + gl[s + 3] * S));
                }
            }
            g2.dispose();
        }
        return img;
    }

    /**
     * Огранённый самоцвет: светлая площадка в середине, четыре скоса разной яркости (свет сверху слева),
     * тонкие светлые рёбра, мелкие искорки в глубине и звезда блика.
     */
    static BufferedImage gem(String[] g) {
        BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        Color d = c(g[0]), m = c(g[1]), l = c(g[2]);
        double a = 18 * S, b = N - 1 - 18 * S;
        Random r = new Random(g[1].hashCode());
        for (int v = 0; v < N; v++)
            for (int u = 0; u < N; u++) {
                Color col;
                if (u >= a && u <= b && v >= a && v <= b) {
                    double t = ((u - a) + (v - a)) / (2.0 * (b - a));
                    col = mix(mix(l, m, 0.25), m, t);
                    // Мягкое внутреннее мерцание.
                    double cx = (u - N / 2.0) / (b - a), cy = (v - N / 2.0) / (b - a);
                    col = mix(col, l, 0.25 * Math.max(0, 1 - Math.sqrt(cx * cx + cy * cy) * 2.2));
                } else {
                    int top = v, left = u, bottom = N - 1 - v, right = N - 1 - u;
                    int min = Math.min(Math.min(top, left), Math.min(bottom, right));
                    double depth = min / a;
                    if (min == top) col = mix(l, m, 0.1 + 0.15 * depth);
                    else if (min == left) col = mix(m, l, 0.4 - 0.15 * depth);
                    else if (min == right) col = mix(m, d, 0.5 - 0.15 * depth);
                    else col = mix(m, d, 0.75 - 0.2 * depth);
                }
                double rw = 0.6 * S;
                boolean diag = (Math.abs(u - v) <= rw || Math.abs(u + v - (N - 1)) <= rw) && (u < a || u > b);
                boolean frame = (Math.abs(u - a) <= rw || Math.abs(u - b) <= rw) && v >= a && v <= b
                        || (Math.abs(v - a) <= rw || Math.abs(v - b) <= rw) && u >= a && u <= b;
                if (diag || frame) col = mix(col, Color.WHITE, 0.3);
                if (u < 2 * S || v < 2 * S || u >= N - 2 * S || v >= N - 2 * S) col = mix(col, d, 0.6);
                img.setRGB(u, v, col.getRGB());
            }
        for (int i = 0; i < 26; i++) {
            int x = (int) (a + r.nextDouble() * (b - a)), y = (int) (a + r.nextDouble() * (b - a));
            blend(img, x, y, Color.WHITE, 0.5);
            blend(img, x + 1, y, Color.WHITE, 0.25);
            blend(img, x, y + 1, Color.WHITE, 0.25);
        }
        // Звезда блика.
        int sx = (int) (24 * S), sy = (int) (24 * S), len = (int) (6 * S);
        for (int k = -len; k <= len; k++) {
            double t = 1 - Math.abs(k) / (double) len;
            for (int wdt = -1; wdt <= 1; wdt++) {
                double a2 = t * (wdt == 0 ? 0.95 : 0.4);
                blend(img, sx + k, sy + wdt, Color.WHITE, a2);
                blend(img, sx + wdt, sy + k, Color.WHITE, a2);
            }
            blend(img, sx + k / 2, sy + k / 2, Color.WHITE, t * 0.35);
            blend(img, sx + k / 2, sy - k / 2, Color.WHITE, t * 0.35);
        }
        return img;
    }

    static void blend(BufferedImage img, int x, int y, Color col, double t) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return;
        img.setRGB(x, y, mix(new Color(img.getRGB(x, y)), col, t).getRGB());
    }

    static final int GLOW_FRAMES = 12;

    /**
     * Ореол кристалла: полупрозрачная мягкая заливка цвета стихии, гаснущая к краям грани.
     * Кадры анимации — медленная пульсация яркости.
     */
    static BufferedImage glow(String[] g) {
        BufferedImage img = new BufferedImage(GN, GN * GLOW_FRAMES, BufferedImage.TYPE_INT_ARGB);
        Color col = mix(c(g[1]), c(g[2]), 0.55);
        for (int f = 0; f < GLOW_FRAMES; f++) {
            double pulse = 0.68 + 0.32 * Math.sin(2 * Math.PI * f / GLOW_FRAMES);
            for (int v = 0; v < GN; v++)
                for (int u = 0; u < GN; u++) {
                    double su = Math.sin(Math.PI * (u + 0.5) / GN), sv = Math.sin(Math.PI * (v + 0.5) / GN);
                    double alpha = 112 * pulse * Math.pow(su * sv, 1.3);
                    int a = (int) Math.round(alpha);
                    img.setRGB(u, f * GN + v, (a << 24) | (col.getRGB() & 0xFFFFFF));
                }
        }
        return img;
    }

    /** Однотонный материал с объёмной заливкой и волокнами; bands > 0 — светлые кольца поперёк (бумага ракеты). */
    static BufferedImage plain(String dark, String mid, String light, int bands) {
        BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        Color[] cols = {c(dark), c(light), c(mid), c(dark)};
        double[] st = {0, 0.32, 0.62, 1};
        Random r = new Random(mid.hashCode());
        double[] fiber = smoothNoise(r, N, 0.05, 0.4);
        for (int v = 0; v < N; v++)
            for (int u = 0; u < N; u++) {
                Color col = shade(ramp((u + 0.5) / N, st, cols), 1 + fiber[u] + (r.nextDouble() - 0.5) * 0.04);
                double y = (v / S) % 32;
                if (bands > 0 && y >= 12 && y < 18) col = mix(col, new Color(0xF0E6C8), 0.85 * smooth(11.5, 12.5, y) * (1 - smooth(17.5, 18, y)));
                img.setRGB(u, v, col.getRGB());
            }
        return img;
    }

    /** Перо: светлое опахало с бородками и стержень посередине. */
    static BufferedImage feather() {
        BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        for (int v = 0; v < N; v++)
            for (int u = 0; u < N; u++) {
                double t = Math.abs((u + 0.5) / N - 0.5) * 2;
                Color col = mix(new Color(0xF6F6F2), new Color(0xA8A8A0), t * t);
                double side = u < N / 2 ? 1 : -1;
                if (((u * side * 0.6 + v) % (2.2 * S) + 2.2 * S) % (2.2 * S) < 0.6 * S) col = shade(col, 0.88);
                if (Math.abs(u - N / 2.0) <= S) col = new Color(0xDCD4BC);
                img.setRGB(u, v, col.getRGB());
            }
        return img;
    }
    static void save(BufferedImage img, String name) throws IOException {
        Path p = res.resolve("assets/" + NS + "/textures/item/3d/" + name + ".png");
        Files.createDirectories(p.getParent());
        ImageIO.write(img, "png", p.toFile());
    }

    // =====================================================================================
    // Сборка моделей
    // =====================================================================================

    enum Kind {
        HANDHELD, TWO_HANDED, REAL, POLEARM, STAFF, MUSKET, SHIELD, BOW, CROSSBOW
    }

    static final class M {
        final List<String> els = new ArrayList<>();
        final Map<String, String> tex = new LinkedHashMap<>();
        /** Сдвиг своих координат в координаты модели: 8 — центр блока; у щитов 0 (система ванильного щита). */
        double shift = 8;
        /** Где рука держит оружие (y своей модели) — по нему ставится хват от первого лица. */
        double grip = -6.5;
        /** Двуручное: от первого лица хват и удар ставит код мода (WeaponPoses), модель только приводит рукоять в начало координат. */
        boolean twoHanded;
        /** Длинный меч, парный кинжал и посох: хват и удары от первого и третьего лица ставит код мода (WeaponMoves), как у двуручного. */
        boolean heldByCode;
        /** Высота модели по y — для подгонки иконки и рамки. */
        double minY = 1e9, maxY = -1e9;
    }

    static String f(double v) {
        double r = Math.round(v * 1000) / 1000.0;
        if (r == Math.rint(r)) return String.valueOf((long) r);
        return String.format(Locale.ROOT, "%s", r);
    }

    /** UV по стилю: full — весь объём поперёк, light — полоса блика, dark — тёмная кромка, mid — середина. */
    static String uv(double h, String style) {
        double u1, u2;
        if (style.startsWith("c")) {
            double k = Math.max(0.06, Math.min(1, Double.parseDouble(style.substring(1))));
            return range(8 - 8 * k, 8 + 8 * k, h);
        }
        if (style.startsWith("r")) {
            String[] p = style.substring(1).split(":");
            return "[" + p[0] + ", " + p[1] + ", " + p[2] + ", " + p[3] + "]";
        }
        if (style.startsWith("u")) {
            String[] p = style.substring(1).split(":");
            return range(Double.parseDouble(p[0]), Double.parseDouble(p[1]), h);
        }
        switch (style) {
            case "light" -> { u1 = 6; u2 = 9; }
            case "dark" -> { u1 = 0.5; u2 = 2.5; }
            case "mid" -> { u1 = 10; u2 = 13; }
            default -> { u1 = 0; u2 = 16; }
        }
        return range(u1, u2, h);
    }

    static String range(double u1, double u2, double h) {
        double v2 = Math.max(0.5, Math.min(16, h));
        return "[" + f(Math.max(0, u1)) + ", 0, " + f(Math.min(16, u2)) + ", " + f(v2) + "]";
    }

    /** Кубоид в своих координатах (центр модели — ноль), с поворотом вокруг центра кубоида. */
    static void el(M m, double x1, double y1, double z1, double x2, double y2, double z2, String t, String style,
                   char axis, double angle, boolean glow) {
        m.tex.put(t, NS + ":item/3d/" + t);
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1, s = m.shift;
        m.minY = Math.min(m.minY, y1);
        m.maxY = Math.max(m.maxY, y2);
        StringBuilder sb = new StringBuilder();
        sb.append("{\"from\": [").append(f(x1 + s)).append(", ").append(f(y1 + s)).append(", ").append(f(z1 + s))
                .append("], \"to\": [").append(f(x2 + s)).append(", ").append(f(y2 + s)).append(", ").append(f(z2 + s)).append("]");
        if (angle != 0) {
            if (Math.abs(angle) > 45.001 || Math.abs(angle / 22.5 - Math.rint(angle / 22.5)) > 1e-6)
                throw new IllegalArgumentException("bad angle " + angle);
            sb.append(", \"rotation\": {\"angle\": ").append(f(angle)).append(", \"axis\": \"").append(axis)
                    .append("\", \"origin\": [").append(f((x1 + x2) / 2 + s)).append(", ").append(f((y1 + y2) / 2 + s))
                    .append(", ").append(f((z1 + z2) / 2 + s)).append("]}");
        }
        if (glow) sb.append(", \"neoforge_data\": {\"block_light\": 15, \"sky_light\": 15}");
        String ref = "\"texture\": \"#" + t + "\"";
        // Камни берут всю текстуру с бликом, остальное — полосу по стилю и длину вдоль.
        boolean gemTex = t.startsWith("gem_") || t.startsWith("glow_");
        boolean whole = gemTex && style.equals("full");
        String side = whole ? "[0, 0, 16, 16]" : uv(gemTex ? 16 : dy, style), cap = whole ? "[0, 0, 16, 16]" : uv(gemTex ? 16 : dz, style);
        sb.append(", \"faces\": {")
                .append("\"north\": {\"uv\": ").append(side).append(", ").append(ref).append("}, ")
                .append("\"south\": {\"uv\": ").append(side).append(", ").append(ref).append("}, ")
                .append("\"east\": {\"uv\": ").append(side).append(", ").append(ref).append("}, ")
                .append("\"west\": {\"uv\": ").append(side).append(", ").append(ref).append("}, ")
                .append("\"up\": {\"uv\": ").append(cap).append(", ").append(ref).append("}, ")
                .append("\"down\": {\"uv\": ").append(cap).append(", ").append(ref).append("}}}");
        if (Math.min(dx, Math.min(dy, dz)) <= 0) throw new IllegalArgumentException("empty box " + sb);
        m.els.add(sb.toString());
    }

    static void box(M m, double x1, double y1, double z1, double x2, double y2, double z2, String t, String style) {
        el(m, x1, y1, z1, x2, y2, z2, t, style, 'z', 0, false);
    }

    /** Кубоид по центру и размерам с поворотом вокруг своей оси. */
    static void boxC(M m, double cx, double cy, double cz, double sx, double sy, double sz, String t, String style,
                     char axis, double angle, boolean glow) {
        el(m, cx - sx / 2, cy - sy / 2, cz - sz / 2, cx + sx / 2, cy + sy / 2, cz + sz / 2, t, style, axis, angle, glow);
    }

    /**
     * Круглый стержень вдоль y: правильный многоугольник (16 граней, у тонких — 8) как объединение
     * прямоугольников, каждый из которых даёт пару противоположных граней. Торцы слегка разнесены по высоте,
     * чтобы не мерцали.
     */
    static void rod(M m, double x, double z, double y1, double y2, double r, String t) {
        boolean fine = r >= 0.38;
        double hw = r * Math.tan(Math.PI / (fine ? 16 : 8));
        double[] angles = fine ? new double[]{0, 22.5, 45, -22.5} : new double[]{0, 45};
        double step = Math.min(0.003, (y2 - y1) / 40);
        int k = 0;
        for (double a : angles) {
            double i1 = step * k++, i2 = step * k++;
            el(m, x - r, y1 + i1, z - hw, x + r, y2 - i1, z + hw, t, "c0.3", 'y', a, false);
            el(m, x - hw, y1 + i2, z - r, x + hw, y2 - i2, z + r, t, "c0.3", 'y', a, false);
        }
    }

    /** Шар из трёх ярусов круглых стержней: шишечки гард и навершия. */
    static void ball(M m, double x, double y, double z, double r, String t) {
        rod(m, x, z, y - 0.42 * r, y + 0.42 * r, r, t);
        rod(m, x, z, y - 0.86 * r, y - 0.4 * r, 0.74 * r, t);
        rod(m, x, z, y + 0.4 * r, y + 0.86 * r, 0.74 * r, t);
    }

    /**
     * Скруглённый сустав изогнутой детали: короткий восьмигранный цилиндр поперёк плоскости изгиба
     * (plane "xy" — вдоль z, "zy" — вдоль x), чуть тоньше самой детали, чтобы не мерцал с её гранями.
     */
    static void pin(M m, String plane, double a, double y, double c, double r, double th, String t, String style) {
        double hw = r * Math.tan(Math.PI / 8);
        int k = 0;
        for (double ang : new double[]{0, 45}) {
            double d1 = th / 2 - 0.006 - 0.003 * k++, d2 = th / 2 - 0.006 - 0.003 * k++;
            if (plane.equals("xy")) {
                el(m, a - r, y - hw, c - d1, a + r, y + hw, c + d1, t, style, 'z', ang, false);
                el(m, a - hw, y - r, c - d2, a + hw, y + r, c + d2, t, style, 'z', ang, false);
            } else {
                el(m, c - d1, y - hw, a - r, c + d1, y + hw, a + r, t, style, 'x', ang, false);
                el(m, c - d2, y - r, a - hw, c + d2, y + r, a + hw, t, style, 'x', ang, false);
            }
        }
    }

    static void rod(M m, double y1, double y2, double r, String t) {
        rod(m, 0, 0, y1, y2, r, t);
    }

    /** Ромб в плоскости xy (кубоид, повёрнутый на 45° вокруг z): острия клинков и камни. */
    static void diamond(M m, double cx, double cy, double half, double hz, String t, String style, boolean glow) {
        double s = half * Math.sqrt(2);
        boxC(m, cx, cy, 0, s, s, 2 * hz, t, style, 'z', 45, glow);
    }

    /**
     * Огранённый кристалл вдоль y: призма с ромбовидным сечением и плавно сужающиеся острия. Светится сам,
     * а вокруг — полупрозрачный пульсирующий ореол из двух оболочек.
     */
    static void crystal(M m, double cy, double half, double r, String gem, boolean shards) {
        String t = "gem_" + gem;
        double body = half;
        boxC(m, 0, cy, 0, 2 * r, body, 2 * r, t, "full", 'y', 45, true);
        // Огранённое острие из двух уступов и короткий низ.
        double top = cy + body / 2, bottom = cy - body / 2;
        double[] tw = {1.62, 1.38, 1.14, 0.9, 0.68, 0.48, 0.3, 0.16};
        for (int i = 0; i < tw.length; i++)
            boxC(m, 0, top + 0.065 + i * 0.13 - 0.002 * i, 0, tw[i] * r, 0.13, tw[i] * r, t, "c" + f(tw[i] / 2), 'y', 45, true);
        double[] bw = {1.45, 1.1, 0.78, 0.46, 0.22};
        for (int i = 0; i < bw.length; i++)
            boxC(m, 0, bottom - 0.06 - i * 0.12 + 0.002 * i, 0, bw[i] * r, 0.12, bw[i] * r, t, "c" + f(bw[i] / 2), 'y', 45, true);
        // Осколки у основания, наклонённые наружу.
        if (shards) {
            double s = Math.max(0.24, r * 0.5), h = body * 0.55, y = bottom + h / 2 - 0.05, off = r * 0.95 + s / 2;
            boxC(m, off, y, 0, s, h, s, t, "c0.7", 'z', -22.5, true);
            boxC(m, -off, y, 0, s, h, s, t, "c0.7", 'z', 22.5, true);
            boxC(m, 0, y, off, s, h, s, t, "c0.7", 'x', 22.5, true);
            boxC(m, 0, y, -off, s, h, s, t, "c0.7", 'x', -22.5, true);
        }
        // Ореол: внутренняя оболочка повторяет призму, внешняя — шире и короче, обе мягко гаснут к краям.
        double y0 = bottom - 0.6 - 0.25, y1 = top + 1.04 + 0.3;
        double s1 = 2 * r + 0.5;
        el(m, -s1 / 2, y0, -s1 / 2, s1 / 2, y1, s1 / 2, "glow_" + gem, "full", 'y', 45, true);
        double s2 = 2.83 * r + 0.45;
        el(m, -s2 / 2, y0 + 0.3, -s2 / 2, s2 / 2, y1 - 0.3, s2 / 2, "glow_" + gem, "full", 'y', 0, true);
    }
    static double norm(double a) {
        a = ((a % 180) + 180) % 180;
        return a > 90 ? a - 180 : a;
    }

    /**
     * Отрезок изогнутой детали (плечи лука, оправа посоха): от точки (a, y) в направлении ang
     * (градусы от оси «поперёк» к +y, кратно 22.5°). plane "xy" — поворот вокруг z, "zy" — вокруг x.
     * c — координата по третьей оси. Возвращает конец отрезка.
     */
    static double[] seg(M m, String plane, double a0, double y0, double ang, double len, double w, double th,
                        double c, String t, String style) {
        return seg(m, plane, a0, y0, ang, len, w, th, c, t, style, w * 0.7);
    }

    static double[] seg(M m, String plane, double a0, double y0, double ang, double len, double w, double th,
                        double c, String t, String style, double overlap) {
        double rad = Math.toRadians(ang);
        double a1 = a0 + Math.cos(rad) * len, y1 = y0 + Math.sin(rad) * len;
        double ca = (a0 + a1) / 2, cy = (y0 + y1) / 2;
        double n = norm(ang);
        boolean across = Math.abs(n) <= 45.001;
        double L = len + overlap;
        if (plane.equals("xy")) {
            double phi = across ? n : (n > 0 ? n - 90 : n + 90);
            boxC(m, ca, cy, c, across ? L : w, across ? w : L, th, t, style, 'z', phi, false);
        } else {
            double phi = across ? -n : (n > 0 ? 90 - n : -90 - n);
            boxC(m, c, cy, ca, th, across ? w : L, across ? L : w, t, style, 'x', phi, false);
        }
        return new double[]{a1, y1};
    }

    /** Цепочка отрезков: пары (угол, длина). Возвращает конечную точку. */
    static double[] chain(M m, String plane, double a, double y, double[] path, double w, double th, double c,
                          String t, String style, boolean mirror) {
        double[] p = {a, y};
        for (int i = 0; i < path.length; i += 2) {
            double ang = mirror ? 180 - path[i] : path[i];
            p = seg(m, plane, p[0], p[1], ang, path[i + 1], w, th + ((i / 2) % 2 == 0 ? 0 : 0.012), c, t, style, 0.04);
            // Стык со следующим отрезком скругляем суставом.
            if (i + 2 < path.length) pin(m, plane, p[0], p[1], c, w / 2, th, t, style);
        }
        return p;
    }

    // ---------------------------------------------------------------- стиль материала

    record S(String metal, String guard, String gem, String grip, String wood) {
    }

    static S style(String mat) {
        return switch (mat) {
            case "iron" -> new S("metal_iron", "metal_brass", "amber", "grip_iron", "wood");
            case "diamond" -> new S("metal_diamond", "metal_gold", "aqua", "grip_diamond", "wood");
            case "steel" -> new S("metal_steel", "metal_dark", "sapphire", "grip_steel", "wood");
            case "damascus" -> new S("metal_damascus", "metal_gold", "ruby", "grip_damascus", "dark_wood");
            case "faceted" -> new S("metal_faceted", "metal_silver", "sapphire", "grip_faceted", "dark_wood");
            case "brilliant" -> new S("metal_brilliant", "metal_gold", "prism", "grip_brilliant", "dark_wood");
            default -> throw new IllegalArgumentException(mat);
        };
    }

    // ---------------------------------------------------------------- ближний бой

    /**
     * Гарда: утолщённая середина, плечи с концами, загнутыми к клинку, и шариками на концах;
     * над ней — язычок на клинке, в середине — камень в оправе.
     */
    static void crossGuard(M m, S s, double y, double halfW, double h, double hz, boolean gem, double langetHz) {
        double cw = Math.max(0.62, halfW * 0.26);
        box(m, -cw, y - h * 0.9, -hz * 1.3, cw, y + h * 0.9, hz * 1.3, s.guard, "full");
        for (int k = -1; k <= 1; k += 2) {
            double x0 = k * cw, x1 = k * halfW * 0.62;
            box(m, Math.min(x0, x1), y - h / 2, -hz, Math.max(x0, x1), y + h / 2, hz, s.guard, "full");
            double[] e = seg(m, "xy", x1, y, k > 0 ? 22.5 : 157.5, halfW * 0.38, h * 0.86, 2 * hz * 0.9, 0, s.guard, "full", 0.04);
            pin(m, "xy", x1, y, 0, h * 0.45, 2 * hz * 0.9, s.guard, "full");
            ball(m, e[0], e[1], 0, h * 0.62, s.guard);
        }
        if (langetHz > 0)
            box(m, -cw * 0.55, y + h * 0.88, -langetHz, cw * 0.55, y + h * 0.9 + 0.6, langetHz, s.guard, "light");
        if (gem) {
            diamond(m, 0, y, h * 0.8, hz * 1.4, s.guard, "light", false);
            diamond(m, 0, y, h * 0.62, hz * 1.55, "gem_" + s.gem, "full", true);
        }
    }

    /** Рукоять в обмотке с металлическими кольцами на концах. */
    static void grip(M m, S s, double y1, double y2, double r) {
        rod(m, y1, y2, r, s.grip);
        rod(m, y2 - 0.22, y2, r + 0.08, s.guard);
        rod(m, y1, y1 + 0.22, r + 0.08, s.guard);
    }

    /** Навершие: шейка, восьмигранная головка с камнем в оправе, колпачок с острием. */
    static void pommel(M m, S s, double y1, double y2, double r, boolean gem) {
        rod(m, y2 - 0.22, y2, r * 0.6, s.guard);
        double hh = y2 - 0.2 - y1;
        rod(m, y1 + 0.2 * hh, y2 - 0.2 - 0.2 * hh, r, s.guard);
        rod(m, y1, y1 + 0.22 * hh, r * 0.8, s.guard);
        rod(m, y2 - 0.2 - 0.22 * hh, y2 - 0.2, r * 0.8, s.guard);
        rod(m, y1 - 0.2, y1 + 0.02, r * 0.52, s.guard);
        rod(m, y1 - 0.36, y1 - 0.18, r * 0.26, s.guard);
        if (gem) {
            double cy = (y1 + y2 - 0.2) / 2;
            diamond(m, 0, cy, r * 0.56, r * 1.06, s.guard, "light", false);
            diamond(m, 0, cy, r * 0.42, r * 1.14, "gem_" + s.gem, "full", true);
        }
    }

    /**
     * Острие с углом 45°: светлые подточенные кромки под 22.5° к оси сходятся в точку,
     * полотно внутри — ступенями. c — полуширина по середине кромок у плеча. Возвращает y острия.
     */
    static double point(M m, String met, double yS, double c, double edge, double hz) {
        double L = c / Math.tan(Math.toRadians(22.5));
        int n = 12;
        double core = c - edge / 2;
        for (int i = 0; i < n; i++) {
            double w = c * (1 - (i + 1.0) / n) - edge * 0.15;
            if (w < 0.07) break;
            box(m, -w, yS + i * L / n + 0.02, -hz, w, yS + (i + 1) * L / n + 0.02, hz, met, "c" + f(w / core));
        }
        double len = Math.hypot(c, L);
        for (int s = -1; s <= 1; s += 2)
            seg(m, "xy", s * c, yS, s > 0 ? 112.5 : 67.5, len - 0.35 * edge, edge, 0.9 * hz, 0, met, "light");
        diamond(m, 0, yS + L - 0.14, 0.13, hz * 0.45, met, "light", false);
        return yS + L;
    }

    /**
     * Прямой клинок: пятка (незаточенная часть у гарды), сердцевина с долом, подточенные кромки,
     * острие 45° и рёбрышко к острию.
     */
    static void straightBlade(M m, String met, double y1, double tipY, double hw, double edge, double hz,
                              double fullerTo, double fullerW) {
        double c = hw + edge / 2, yS = tipY - c / Math.tan(Math.toRadians(22.5));
        double f = Math.min(fullerTo, yS - 0.3), b = Math.min(0.17, hw * 0.16), in = hw - b;
        box(m, -in, y1, -hz, -fullerW, f, hz, met, "full");
        box(m, fullerW, y1, -hz, in, f, hz, met, "full");
        box(m, -fullerW, y1, -hz * 0.55, fullerW, f, hz * 0.55, met, "dark");
        box(m, -in, f, -hz, in, yS + 0.02, hz, met, "c" + f(in / hw));
        // Фаски: полотно плавно переходит в тонкую кромку.
        box(m, -hw, y1 + 0.45, -hz * 0.72, -in, yS + 0.04, hz * 0.72, met, "mid");
        box(m, in, y1 + 0.45, -hz * 0.72, hw, yS + 0.04, hz * 0.72, met, "mid");
        box(m, -hw - edge, y1 + 0.5, -hz * 0.45, -hw, yS + 0.05, hz * 0.45, met, "light");
        box(m, hw, y1 + 0.5, -hz * 0.45, hw + edge, yS + 0.05, hz * 0.45, met, "light");
        box(m, -hw - edge, y1, -hz * 1.05, hw + edge, y1 + 0.55, hz * 1.05, met, "mid");
        box(m, -0.1, f, -hz * 1.18, 0.1, tipY - 0.75, hz * 1.18, met, "light");
        point(m, met, yS, c, edge, hz);
    }

    // ---------------------------------------------------------------- реальные пропорции
    // Длины в единицах модели ≈ пикселях игрока (персонаж — 32): рукоять там же, где была в руке (около y −6),
    // клинок растёт от неё вверх. Двуручник — почти в рост, копьё — больше двух блоков.

    /** Круглый диск в плоскости xy (16 граней) толщиной по z; лицевые грани чуть разнесены, чтобы не мерцали. */
    static void wheel(M m, double cx, double cy, double z1, double z2, double r, String t, String style) {
        double hw = r * Math.tan(Math.PI / 16);
        int k = 0;
        for (double a : new double[]{0, 22.5, 45, -22.5}) {
            double d1 = 0.003 * k++, d2 = 0.003 * k++;
            el(m, cx - r, cy - hw, z1 + d1, cx + r, cy + hw, z2 - d1, t, style, 'z', a, false);
            el(m, cx - hw, cy - r, z1 + d2, cx + hw, cy + r, z2 - d2, t, style, 'z', a, false);
        }
    }

    /** Дисковое навершие меча: колесо с выпуклой серединой и камнем. */
    static void wheelPommel(M m, S s, double y, double r, double th, boolean gem) {
        wheel(m, 0, y, -th / 2, th / 2, r, s.guard, "c0.45");
        wheel(m, 0, y, -th / 2 - 0.14, th / 2 + 0.14, r * 0.5, s.guard, "light");
        rod(m, y + r - 0.1, y + r + 0.35, 0.36, s.guard);
        if (gem) diamond(m, 0, y, r * 0.32, th / 2 + 0.22, "gem_" + s.gem, "full", true);
    }

    /**
     * Длинный клинок: ширина плавно сходит от hw0 у гарды до hw1 у начала острия (пять ступеней),
     * с долом, фасками и подточенными кромками; дальше — острие 45°.
     */
    static double taperedBlade(M m, String met, double y0, double yS, double hw0, double hw1, double hz, double edge,
                               double fullerTo, double fullerW) {
        int n = 5;
        for (int i = 0; i < n; i++) {
            double a = y0 + (yS - y0) * i / n, b = y0 + (yS - y0) * (i + 1) / n + (i + 1 < n ? 0 : 0.02);
            double hw = hw0 + (hw1 - hw0) * (i + 0.5) / n, bev = Math.min(0.12, hw * 0.2), in = hw - bev;
            if (fullerW > 0 && fullerTo > a + 0.05) {
                double fb = Math.min(b, fullerTo);
                box(m, -in, a, -hz, -fullerW, fb, hz, met, "full");
                box(m, fullerW, a, -hz, in, fb, hz, met, "full");
                box(m, -fullerW, a, -hz * 0.5, fullerW, fb, hz * 0.5, met, "dark");
                if (fb < b - 0.01) box(m, -in, fb, -hz, in, b, hz, met, "c" + f(in / hw0));
            } else box(m, -in, a, -hz, in, b, hz, met, "c" + f(in / hw0));
            box(m, -hw, a, -hz * 0.7, -in, b, hz * 0.7, met, "mid");
            box(m, in, a, -hz * 0.7, hw, b, hz * 0.7, met, "mid");
            box(m, -hw - edge, a, -hz * 0.4, -hw, b, hz * 0.4, met, "light");
            box(m, hw, a, -hz * 0.4, hw + edge, b, hz * 0.4, met, "light");
        }
        return point(m, met, yS, hw1 + edge / 2, edge, hz);
    }

    static void dagger(M m, S s) {
        m.grip = -4.8;
        m.heldByCode = true;
        wheelPommel(m, s, -7.05, 0.72, 0.85, true);
        grip(m, s, -6.4, -3.2, 0.45);
        crossGuard(m, s, -2.95, 1.75, 0.45, 0.34, false, 0);
        taperedBlade(m, s.metal, -2.65, 4.2, 0.62, 0.42, 0.2, 0.16, 1.4, 0.11);
    }

    /** Длинный одноручный меч: дисковое навершие, рукоять на ладонь, узкая прямая гарда, клинок ×12 своей ширины. */
    static void sword(M m, S s) {
        m.grip = -6.4;
        m.heldByCode = true;
        wheelPommel(m, s, -9.75, 0.95, 1.0, true);
        grip(m, s, -8.85, -4.0, 0.5);
        crossGuard(m, s, -3.72, 3.3, 0.5, 0.4, true, 0.26);
        taperedBlade(m, s.metal, -3.4, 12.4, 0.78, 0.5, 0.2, 0.18, 6.5, 0.15);
    }

    /** Двуручник: длинная рукоять с кольцом, широкая гарда, обмотанная пятка с усами, клинок почти в рост. */
    static void greatsword(M m, S s) {
        m.grip = -8.2;
        m.twoHanded = true;
        pommel(m, s, -13.4, -12.0, 1.0, true);
        grip(m, s, -12.0, -4.45, 0.55);
        rod(m, -8.4, -8.1, 0.66, s.guard);
        crossGuard(m, s, -4.1, 5.0, 0.6, 0.45, true, 0.32);
        box(m, -0.6, -3.72, -0.3, 0.6, -0.62, 0.3, s.grip, "full");
        box(m, -0.66, -3.75, -0.2, 0.66, -0.6, 0.2, s.metal, "mid");
        for (int k = -1; k <= 1; k += 2) {
            seg(m, "xy", k * 0.6, -0.55, k > 0 ? 22.5 : 157.5, 0.9, 0.34, 0.3, 0, s.guard, "full");
            ball(m, k * 1.48, -0.2, 0, 0.24, s.guard);
        }
        taperedBlade(m, s.metal, -0.6, 21.6, 0.98, 0.66, 0.24, 0.2, 10.5, 0.19);
    }

    static void rapier(M m, S s) {
        m.grip = -5.2;
        String met = s.metal;
        taperedBlade(m, met, -2.0, 16.2, 0.34, 0.22, 0.16, 0.07, 3.0, 0.07);
        // Чашка, перекладина и дужка к навершию.
        rod(m, -2.75, -1.95, 1.3, s.guard);
        rod(m, -2.82, -2.7, 1.42, s.guard);
        box(m, -3.6, -2.62, -0.2, 3.6, -2.22, 0.2, s.guard, "full");
        ball(m, -3.75, -2.42, 0, 0.3, s.guard);
        ball(m, 3.75, -2.42, 0, 0.3, s.guard);
        box(m, 3.2, -6.4, -0.18, 3.48, -2.5, 0.18, s.guard, "full");
        seg(m, "xy", 3.34, -6.4, 225, 3.6, 0.28, 0.36, 0, s.guard, "full", 0.04);
        pin(m, "xy", 3.34, -6.4, 0, 0.14, 0.36, s.guard, "full");
        diamond(m, 0, -2.35, 0.5, 1.42, s.guard, "light", false);
        diamond(m, 0, -2.35, 0.36, 1.5, "gem_" + s.gem, "full", true);
        grip(m, s, -7.7, -2.85, 0.48);
        ball(m, 0, -8.35, 0, 0.72, s.guard);
        rod(m, -9.15, -8.95, 0.2, s.guard);
    }

    /** Копьё больше двух блоков: длинное древко, обмотка под обе руки, втулка и узкий листовидный наконечник. */
    static void spear(M m, S s) {
        m.twoHanded = true;
        String met = s.metal;
        rod(m, -22.0, 18.6, 0.42, s.wood);
        grip(m, s, -9.6, -3.4, 0.5);
        rod(m, -22.6, -21.8, 0.48, met);
        rod(m, -23.3, -22.6, 0.24, met);
        rod(m, 18.0, 20.0, 0.48, met);
        rod(m, 18.0, 18.35, 0.6, met);
        rod(m, 19.5, 19.7, 0.54, met);
        // Наконечник: расширение под 45°, ровная часть, острие.
        box(m, -0.5, 19.95, -0.22, 0.5, 20.35, 0.22, met, "full");
        for (int k = -1; k <= 1; k += 2)
            seg(m, "xy", k * 0.45, 19.95, k > 0 ? 45 : 135, 0.75, 0.2, 0.18, 0, met, "light");
        box(m, -0.78, 20.3, -0.22, 0.78, 20.62, 0.22, met, "full");
        taperedBlade(m, met, 20.6, 21.7, 0.86, 0.8, 0.22, 0.18, 0, 0);
        box(m, -0.16, 19.95, -0.32, 0.16, 23.6, 0.32, met, "light");
    }

    /** Шестопёр: рукоять, древко и головка с восемью перьями на конце. */
    static void flangedMace(M m, S s) {
        m.grip = -7.2;
        String met = s.metal;
        rod(m, -9.6, 7.0, 0.42, s.wood);
        grip(m, s, -9.6, -4.8, 0.52);
        rod(m, -10.4, -9.6, 0.62, met);
        rod(m, -10.7, -10.4, 0.32, met);
        rod(m, 6.55, 7.0, 0.6, met);
        rod(m, 7.0, 10.6, 0.62, met);
        for (double ang : new double[]{0, 45}) {
            el(m, -1.25, 7.2, -0.14, 1.25, 10.4, 0.14, met, "full", 'y', ang, false);
            el(m, -1.62, 7.8, -0.11, 1.62, 9.9, 0.11, met, "light", 'y', ang, false);
            el(m, -0.14, 7.2, -1.25, 0.14, 10.4, 1.25, met, "full", 'y', ang, false);
            el(m, -0.11, 7.8, -1.62, 0.11, 9.9, 1.62, met, "light", 'y', ang, false);
        }
        rod(m, 10.6, 11.2, 0.38, met);
        rod(m, 11.2, 11.7, 0.18, met);
    }

    /** Боевой молот: древко до пола с лангетами, небольшая головка с бойком, клювом и шипом у самого верха. */
    static void warhammer(M m, S s, String mat) {
        m.twoHanded = true;
        String met = s.metal;
        double h = 3.2;
        rod(m, -22.0, 15.0 + h, 0.42, s.wood);
        for (int k = -1; k <= 1; k += 2) {
            box(m, k > 0 ? 0.38 : -0.54, 9.4 + h, -0.2, k > 0 ? 0.54 : -0.38, 15.0 + h, 0.2, met, "light");
            for (double y : new double[]{10.0, 12.0, 14.0})
                box(m, k > 0 ? 0.52 : -0.62, y + h - 0.1, -0.1, k > 0 ? 0.62 : -0.52, y + h + 0.1, 0.1, met, "mid");
        }
        grip(m, s, -10.2, -3.0, 0.52);
        rod(m, -22.8, -22.0, 0.5, met);
        rod(m, -23.6, -22.8, 0.22, met);
        box(m, -0.65, 14.8 + h, -0.62, 0.65, 17.6 + h, 0.62, met, "full");
        box(m, -0.72, 14.8 + h, -0.69, 0.72, 15.05 + h, 0.69, met, "light");
        box(m, -2.3, 15.6 + h, -0.62, -0.65, 16.9 + h, 0.62, met, "full");
        box(m, -2.6, 15.45 + h, -0.78, -2.3, 17.05 + h, 0.78, met, "light");
        box(m, 0.65, 15.7 + h, -0.45, 1.8, 16.8 + h, 0.45, met, "full");
        box(m, 1.8, 15.85 + h, -0.3, 2.8, 16.6 + h, 0.3, met, "full");
        seg(m, "xy", 2.65, 16.2 + h, -22.5, 1.1, 0.42, 0.34, 0, met, "light");
        rod(m, 17.6 + h, 18.8 + h, 0.42, met);
        rod(m, 18.8 + h, 19.9 + h, 0.26, met);
        rod(m, 19.9 + h, 20.7 + h, 0.12, met);
        if (!mat.equals("iron")) {
            diamond(m, 0, 16.2 + h, 0.42, 0.7, s.guard, "light", false);
            diamond(m, 0, 16.2 + h, 0.3, 0.76, "gem_" + s.gem, "full", true);
        }
    }
    /** Бородовидный боевой топор: длинное топорище, небольшое лезвие с вытянутой вниз бородой. */
    static void axe(M m, S s) {
        m.grip = -7.9;
        String met = s.metal;
        rod(m, -10.2, 11.2, 0.42, s.wood);
        grip(m, s, -10.2, -5.6, 0.52);
        rod(m, -10.7, -10.2, 0.5, met);
        box(m, -0.6, 7.8, -0.6, 0.6, 10.8, 0.6, met, "full");
        for (double y : new double[]{8.4, 10.2}) box(m, -0.18, y - 0.18, -0.67, 0.18, y + 0.18, 0.67, met, "light");
        // Лезвие: верх почти прямой, борода тянется вниз, кромка — светлая полоса.
        double x0 = -0.6, xe = -3.6;
        double[] topEnd = seg(m, "xy", x0, 10.35, 157.5, 3.2, 0.28, 0.5, 0, met, "full");
        double[] b1 = seg(m, "xy", x0, 8.2, 225, 1.9, 0.28, 0.5, 0, met, "full");
        double[] b2 = seg(m, "xy", b1[0], b1[1], 202.5, 1.45, 0.28, 0.5, 0, met, "full");
        pin(m, "xy", b1[0], b1[1], 0, 0.14, 0.5, met, "full");
        double[] e1 = seg(m, "xy", b2[0], b2[1], 112.5, 0.9, 0.34, 0.22, 0, met, "light");
        pin(m, "xy", e1[0], e1[1], 0, 0.17, 0.22, met, "light");
        seg(m, "xy", e1[0], e1[1], 90, topEnd[1] - e1[1], 0.34, 0.22, 0, met, "light");
        int n = 8;
        for (int i = 0; i < n; i++) {
            double xa = x0 + (e1[0] + 0.17 - x0) * i / n, xb = x0 + (e1[0] + 0.17 - x0) * (i + 1) / n;
            double top = 10.35 + (x0 - xa) * Math.tan(Math.toRadians(22.5));
            double bot = xa > b1[0] ? 8.2 - (x0 - xa) : b1[1] - (b1[0] - xa) * Math.tan(Math.toRadians(22.5));
            double u1 = 16 * (xb - xe) / (x0 - xe), u2 = 16 * (xa - xe) / (x0 - xe);
            box(m, xb, bot, -0.22, xa, top, 0.22, met, "u" + f(Math.max(0, u1)) + ":" + f(Math.min(16, u2)));
        }
        box(m, -1.3, 8.3, -0.34, -0.6, 10.3, 0.34, met, "full");
        // Обух.
        box(m, 0.6, 8.6, -0.45, 1.3, 10.2, 0.45, met, "full");
        box(m, 1.3, 8.7, -0.4, 1.55, 10.1, 0.4, met, "light");
    }
    /** Ствол и замок — металл грейда, накладки — его отделка, ложе — дерево (у дамаска и выше — тёмное). */
    static void musket(M m, String mat) {
        String iron = switch (mat) {
            case "iron" -> "metal_iron";
            default -> "metal_" + mat;
        };
        String brass = switch (mat) {
            case "iron" -> "metal_brass";
            case "steel" -> "metal_dark";
            case "faceted" -> "metal_silver";
            default -> "metal_gold";
        };
        String wood = mat.equals("iron") || mat.equals("steel") ? "wood" : "dark_wood";
        rod(m, -2.2, 15.4, 0.4, iron);
        rod(m, 15.0, 15.55, 0.5, iron);
        box(m, -0.75, 14.2, -0.1, -0.38, 14.6, 0.1, brass, "full");
        box(m, 0.25, -2.2, -0.48, 1.05, 11.0, 0.48, wood, "full");
        for (double y : new double[]{10.2, 6.0, 2.0}) box(m, -0.52, y - 0.2, -0.56, 1.12, y + 0.2, 0.56, brass, "full");
        // Приклад длинный, до плеча, расширяется к затыльнику.
        box(m, -0.55, -4.5, -0.52, 1.05, -2.0, 0.52, wood, "full");
        box(m, -0.65, -6.2, -0.55, 1.05, -4.5, 0.55, wood, "full");
        box(m, -0.7, -8.4, -0.6, 1.6, -6.2, 0.6, wood, "full");
        box(m, -0.8, -10.4, -0.66, 2.1, -8.4, 0.66, wood, "full");
        box(m, -0.85, -12.0, -0.7, 2.4, -10.4, 0.7, wood, "full");
        box(m, -0.95, -12.35, -0.75, 2.5, -12.0, 0.75, iron, "full");
        // Замок, курок и полка.
        box(m, -0.45, -4.0, -0.6, 0.85, -2.1, 0.6, iron, "full");
        box(m, -1.3, -3.2, 0.3, -0.4, -2.75, 0.68, "metal_dark", "full");
        box(m, -0.7, -3.85, 0.45, -0.4, -3.5, 0.7, brass, "light");
        // Спусковая скоба и крючок.
        box(m, 1.05, -4.45, -0.12, 1.9, -4.3, 0.12, brass, "full");
        box(m, 1.75, -5.6, -0.12, 1.9, -4.3, 0.12, brass, "full");
        box(m, 1.05, -5.6, -0.12, 1.9, -5.45, 0.12, brass, "full");
        box(m, 1.05, -5.0, -0.08, 1.45, -4.85, 0.08, "metal_dark", "full");
        // Шомпол под цевьём и антабка.
        box(m, 1.05, 0.6, -0.1, 1.25, 14.4, 0.1, "metal_dark", "full");
        box(m, 1.25, 7.9, -0.06, 1.6, 8.0, 0.06, brass, "full");
        box(m, 1.5, 7.4, -0.06, 1.6, 8.0, 0.06, brass, "full");
        box(m, 1.25, 7.4, -0.06, 1.6, 7.5, 0.06, brass, "full");
    }

    // ---------------------------------------------------------------- щиты
    // Система координат ванильного щита: пластина по центру x −6..6, y −11..11, лицо смотрит в +z (z ≈ 2),
    // ремни и рукоять — сзади (z < 1). Тогда ванильные положения в руке и при блоке подходят как есть.

    record SH(String rim, String face, String stud, String back) {
    }

    static SH shieldStyle(int grade) {
        return switch (grade) {
            case 1 -> new SH("metal_iron", "shield_face_1", "metal_iron", "wood");
            case 2 -> new SH("metal_steel", "shield_face_2", "metal_steel", "wood");
            case 3 -> new SH("metal_damascus", "shield_face_3", "metal_gold", "dark_wood");
            case 4 -> new SH("metal_diamond", "shield_face_4", "metal_gold", "dark_wood");
            case 5 -> new SH("metal_faceted", "shield_face_5", "metal_silver", "dark_wood");
            default -> new SH("metal_brilliant", "shield_face_6", "metal_gold", "dark_wood");
        };
    }

    /** Полуширина контура на высоте y по правой стороне (точки сверху вниз). */
    static double polyHalf(double[][] pts, double y) {
        for (int i = 0; i + 1 < pts.length; i++) {
            double[] a = pts[i], b = pts[i + 1];
            if (y <= a[1] + 1e-9 && y >= b[1] - 1e-9) {
                double t = (a[1] - y) / Math.max(1e-9, a[1] - b[1]);
                return a[0] + (b[0] - a[0]) * t;
            }
        }
        return 0;
    }

    /**
     * Полотно щита ломтиками по высоте: деревянная основа и лицевой слой с рисунком,
     * натянутым на весь щит (каждый ломтик берёт свой кусок рисунка).
     */
    static void shieldBody(M m, SH sh, double[][] right, double inset, double step) {
        double yTop = right[0][1], yBot = right[right.length - 1][1], maxX = 0;
        for (double[] p : right) maxX = Math.max(maxX, p[0]);
        double H = yTop - yBot, W = 2 * maxX;
        List<Double> ys = new ArrayList<>();
        for (int i = 0; i + 1 < right.length; i++) {
            double[] a = right[i], b = right[i + 1];
            int n = Math.abs(a[0] - b[0]) < 1e-6 ? 1 : Math.max(1, (int) Math.ceil((a[1] - b[1]) / step));
            for (int k = 0; k < n; k++) ys.add(a[1] - (a[1] - b[1]) * k / n);
        }
        ys.add(yBot);
        for (int i = 0; i + 1 < ys.size(); i++) {
            double top = ys.get(i), bot = ys.get(i + 1);
            double w = Math.min(polyHalf(right, top), polyHalf(right, bot)) - inset;
            if (w < 0.12 || top - bot < 0.01) continue;
            box(m, -w, bot, 1.0, w, top, 1.85, sh.back(), "c0.5");
            double u1 = 16 * (maxX - w) / W, u2 = 16 * (maxX + w) / W, v1 = 16 * (yTop - top) / H, v2 = 16 * (yTop - bot) / H;
            box(m, -w, bot, 1.85, w, top, 2.1, sh.face(), "r" + f(u1) + ":" + f(v1) + ":" + f(u2) + ":" + f(v2));
        }
    }

    /** Ремни и рукоять на обороте. */
    static void shieldStraps(M m, SH sh) {
        box(m, -1.0, 1.6, -0.9, 1.0, 2.7, 1.0, "grip_bow", "full");
        box(m, -1.0, -2.7, -0.9, 1.0, -1.6, 1.0, "grip_bow", "full");
        box(m, -0.42, -2.9, -1.5, 0.42, 2.9, -0.9, sh.back(), "full");
    }

    /** Заклёпки на ободе. */
    static void studs(M m, SH sh, double[][] at) {
        for (double[] p : at) boxC(m, p[0], p[1], 2.38, 0.42, 0.42, 0.2, sh.stud(), "light", 'z', 45, false);
    }

    /** Треугольный (рыцарский) щит: ровный верх, прямые бока и скруглённый низ, сходящийся в точку. */
    static void heaterShield(M m, int grade) {
        SH sh = shieldStyle(grade);
        double x = 5.825, w = 0.75, th = 1.4, zc = 1.6;
        box(m, -6.2, 10.25, 0.9, 6.2, 11.0, 2.3, sh.rim(), "full");
        for (int k = -1; k <= 1; k += 2)
            box(m, k > 0 ? x - w / 2 : -x - w / 2, -0.95, 0.9 + 0.006, k > 0 ? x + w / 2 : -x + w / 2, 10.3, 2.3 - 0.006, sh.rim(), "full");
        double[] path = {-112.5, 3.0, -135, (x + 3.0 * Math.cos(Math.toRadians(-112.5))) / Math.cos(Math.toRadians(45))};
        double[] end = chain(m, "xy", x, -0.95, path, w, th, zc, sh.rim(), "full", false);
        chain(m, "xy", -x, -0.95, path, w, th, zc, sh.rim(), "full", true);
        for (int k = -1; k <= 1; k += 2) pin(m, "xy", k * x, -0.95, zc, w / 2, th, sh.rim(), "full");
        pin(m, "xy", 0, end[1], zc, w / 2, th, sh.rim(), "full");
        double x1 = x + 3.0 * Math.cos(Math.toRadians(-112.5)), y1 = -0.95 + 3.0 * Math.sin(Math.toRadians(-112.5));
        shieldBody(m, sh, new double[][]{{x, 10.3}, {x, -0.95}, {x1, y1}, {0, end[1]}}, w * 0.3, 0.45);
        studs(m, sh, new double[][]{{-x, 9.0}, {x, 9.0}, {-x, 4.0}, {x, 4.0}, {-x, -0.2}, {x, -0.2}, {0, 10.62}});
        shieldStraps(m, sh);
    }

    /** Каплевидный щит: скруглённый верх и длинный клин вниз. */
    static void kiteShield(M m, int grade) {
        SH sh = shieldStyle(grade);
        double r = 6, cy = 6, w = 0.75, th = 1.4, zc = 1.6;
        double vx = r * Math.cos(Math.toRadians(11.25)), vy = cy + r * Math.sin(Math.toRadians(11.25)), edge = 2 * r * Math.sin(Math.toRadians(11.25));
        double[] arc = {67.5, edge, 45, edge, 22.5, edge, 0, edge, -22.5, edge, -45, edge, -67.5, edge};
        chain(m, "xy", -vx, vy, arc, w, th, zc, sh.rim(), "full", false);
        double sideBot = 3.5, taper = vx / Math.cos(Math.toRadians(67.5));
        for (int k = -1; k <= 1; k += 2) {
            box(m, k * vx - w / 2, sideBot, zc - th / 2 + 0.006, k * vx + w / 2, vy, zc + th / 2 - 0.006, sh.rim(), "full");
            pin(m, "xy", k * vx, vy, zc, w / 2, th, sh.rim(), "full");
            pin(m, "xy", k * vx, sideBot, zc, w / 2, th, sh.rim(), "full");
        }
        double[] end = chain(m, "xy", vx, sideBot, new double[]{-112.5, taper}, w, th, zc, sh.rim(), "full", false);
        chain(m, "xy", -vx, sideBot, new double[]{-112.5, taper}, w, th, zc, sh.rim(), "full", true);
        pin(m, "xy", 0, end[1], zc, w / 2, th, sh.rim(), "full");
        List<double[]> right = new ArrayList<>();
        for (double a : new double[]{78.75, 56.25, 33.75, 11.25})
            right.add(new double[]{r * Math.cos(Math.toRadians(a)), cy + r * Math.sin(Math.toRadians(a))});
        right.add(new double[]{vx, sideBot});
        right.add(new double[]{0, end[1]});
        shieldBody(m, sh, right.toArray(new double[0][]), w * 0.3, 0.45);
        studs(m, sh, new double[][]{{0, cy + r}, {-vx, 5.2}, {vx, 5.2}, {-3.0, -3.2}, {3.0, -3.2}});
        shieldStraps(m, sh);
    }

    /** Баклер: маленький круглый металлический щит с умбоном и рукоятью посередине. */
    static void buckler(M m, int grade) {
        SH sh = shieldStyle(grade);
        double R = 5.0;
        wheel(m, 0, 0, 1.0, 1.9, R, sh.rim(), "c0.6");
        double[] ring = new double[32];
        for (int k = 0; k < 16; k++) {
            ring[2 * k] = 90 + 22.5 * k;
            ring[2 * k + 1] = 2 * R * Math.sin(Math.toRadians(11.25));
        }
        double vx = R * Math.cos(Math.toRadians(-11.25)), vy = R * Math.sin(Math.toRadians(-11.25));
        chain(m, "xy", vx, vy, ring, 0.65, 1.4, 1.6, sh.rim(), "light", false);
        pin(m, "xy", vx, vy, 1.6, 0.325, 1.4, sh.rim(), "light");
        String boss = grade >= 3 ? "metal_gold" : sh.rim();
        wheel(m, 0, 0, 1.9, 2.45, 2.0, boss, "c0.5");
        wheel(m, 0, 0, 2.45, 2.9, 1.4, boss, "c0.4");
        wheel(m, 0, 0, 2.9, 3.2, 0.8, boss, "light");
        if (grade >= 3) boxC(m, 0, 0, 3.3, 0.54, 0.54, 0.3, "gem_ruby", "full", 'z', 45, true);
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(22.5 + 45 * k);
            boxC(m, 3.4 * Math.cos(a), 3.4 * Math.sin(a), 2.0, 0.42, 0.42, 0.24, sh.stud(), "light", 'z', 45, false);
        }
        box(m, -1.7, -0.45, -1.0, 1.7, 0.45, 1.0, "grip_bow", "full");
    }
    // ---------------------------------------------------------------- луки

    /** Плечи луков по кадрам: пары (угол, длина) от рукояти к концу правого плеча. */
    static final double[][] COMPOSITE = {
            {0, 2.1, -22.5, 2.3, -45, 2.5, -67.5, 1.7, -22.5, 1.1},
            {0, 1.9, -22.5, 2.3, -45, 2.5, -67.5, 1.9, -45, 1.0},
            {0, 1.0, -22.5, 2.0, -45, 2.4, -67.5, 2.6, -90, 1.6, -67.5, 0.9},
            {0, 0.8, -22.5, 1.9, -45, 2.4, -67.5, 2.6, -90, 1.4, -67.5, 0.8}};
    static final double[][] LONGBOW = {
            {0, 2.8, -22.5, 2.7, -45, 2.6, -67.5, 2.2},
            {0, 2.5, -22.5, 2.7, -45, 2.7, -67.5, 2.3},
            {0, 1.4, -22.5, 2.6, -45, 2.7, -67.5, 2.7, -90, 1.0},
            {0, 0.9, -22.5, 2.4, -45, 2.7, -67.5, 2.9, -90, 1.6}};
    /** Угол тетивы от горизонтали по кадрам: прямая, слабое, среднее и полное натяжение. */
    static final double[] STRING_ANGLE = {0, 22.5, 22.5, 45};

    static void bow(M m, boolean composite, int frame) {
        if (composite) bow(m, true, frame, "dark_wood", "bone", "grip_bow", "metal_gold", "metal_gold");
        else bow(m, false, frame, "ranger_wood", "belly_green", "grip_ranger", "metal_iron", null);
    }

    /** Луки IV–VI: изгиб составного лука, тёмное дерево, накладки и кольца из металла грейда. */
    static void grandBow(M m, String mat, int frame) {
        S s = style(mat);
        bow(m, true, frame, "dark_wood", s.metal(), s.grip(), s.guard(), s.metal());
    }

    static void bow(M m, boolean composite, int frame, String wood, String belly, String grip, String tip, String rings) {
        double[] path = (composite ? COMPOSITE : LONGBOW)[frame];
        double w = composite ? 0.62 : 0.6, th = composite ? 0.62 : 0.56;
        double[] end = null;
        for (int side = -1; side <= 1; side += 2) {
            boolean mir = side < 0;
            double sx = side * 0.95;
            end = chain(m, "xy", sx, 5.7, path, w, th, 0, wood, "full", mir);
            // Накладка на внутренней стороне плеч: та же цепочка, сдвинутая к тетиве.
            double[] p = {sx, 5.7};
            for (int i = 0; i < path.length; i += 2) {
                double ang = mir ? 180 - path[i] : path[i];
                double rad = Math.toRadians(ang);
                double nx = Math.sin(rad) * (mir ? -1 : 1), ny = -Math.cos(rad) * (mir ? -1 : 1);
                double off = w / 2 + 0.08;
                double[] q2 = seg(m, "xy", p[0] + nx * off, p[1] + ny * off, ang, path[i + 1], 0.18, th * 0.8, 0, belly, "full", 0.03);
                if (i + 2 < path.length) pin(m, "xy", q2[0], q2[1], 0, 0.09, th * 0.8, belly, "full");
                p = new double[]{p[0] + Math.cos(rad) * path[i + 1], p[1] + Math.sin(rad) * path[i + 1]};
            }
            if (rings != null) {
                // Кольца на середине плеч.
                double[] q = {sx, 5.7};
                for (int i = 0; i < 4; i += 2) {
                    double ang = mir ? 180 - path[i] : path[i];
                    double rad = Math.toRadians(ang);
                    q = new double[]{q[0] + Math.cos(rad) * path[i + 1], q[1] + Math.sin(rad) * path[i + 1]};
                }
                boxC(m, q[0], q[1], 0, 0.85, 0.85, th + 0.14, rings, "light", 'z', 0, false);
            }
            boxC(m, end[0], end[1], 0, 0.5, 0.5, th + 0.1, tip, "light", 'y', 45, false);
            boxC(m, end[0], end[1], 0, 0.26, 0.26, th + 0.26, tip, "light", 'y', 45, false);
            // Окантовка у корня плеча.
            boxC(m, side * 1.12, 5.7, 0, 0.3, w + 0.2, th + 0.14, tip, "light", 'z', 0, false);
        }
        // Рукоять с кольцами по краям и полочкой для стрелы.
        box(m, -1.0, 4.85, -0.42, 1.0, 6.55, 0.42, grip, "full");
        boxC(m, 0, 5.7, 0, 1.9, 1.6, 0.9, grip, "full", 'y', 45, false);
        box(m, -1.04, 4.8, -0.47, 1.04, 4.98, 0.47, tip, "full");
        box(m, -1.04, 6.42, -0.47, 1.04, 6.6, 0.47, tip, "full");
        box(m, -0.3, 6.55, 0.3, 0.3, 6.75, 0.62, tip, "light");
        // Тетива.
        double tx = end[0], ty = end[1];
        double a = STRING_ANGLE[frame];
        double nock;
        if (a == 0) {
            box(m, -tx, ty - 0.08, -0.08, tx, ty + 0.08, 0.08, "string", "full");
            nock = ty;
        } else {
            nock = ty - tx * Math.tan(Math.toRadians(a));
            double len = tx / Math.cos(Math.toRadians(a));
            seg(m, "xy", tx, ty, 180 + a, len, 0.16, 0.16, 0, "string", "full");
            seg(m, "xy", -tx, ty, -a, len, 0.16, 0.16, 0, "string", "full");
        }
        if (frame > 0) arrow(m, nock, nock + 14.5, 0);
    }

    /** Стрела вдоль y: оперение у хвоста, наконечник-ромб. */
    static void arrow(M m, double tail, double head, double z) {
        box(m, -0.12, tail - 0.2, z - 0.12, 0.12, head - 0.5, z + 0.12, "shaft_wood", "full");
        box(m, -0.5, tail + 0.15, z - 0.03, 0.5, tail + 1.7, z + 0.03, "feather", "full");
        box(m, -0.03, tail + 0.15, z - 0.5, 0.03, tail + 1.7, z + 0.5, "feather", "full");
        boxC(m, 0, head - 0.55, z, 0.62, 0.62, 0.14, "metal_iron", "light", 'z', 45, false);
        box(m, -0.16, head - 1.0, z - 0.16, 0.16, head - 0.6, z + 0.16, "metal_iron", "full");
    }

    // ---------------------------------------------------------------- ручные арбалеты

    static final double[] PROD_RELAXED = {0, 2.0, -22.5, 2.0, -45, 2.2, -67.5, 1.0};
    static final double[] PROD_BENT = {0, 1.6, -22.5, 2.0, -45, 2.2, -67.5, 1.4, -90, 0.6};

    /** frame: standby, pulling_0, pulling_1, pulling_2, arrow, firework. */
    static void crossbow(M m, String mat, String frame) {
        String met = "metal_" + mat;
        String trim = switch (mat) {
            case "iron" -> "metal_brass";
            case "steel" -> "metal_dark";
            case "faceted" -> "metal_silver";
            default -> "metal_gold";
        };
        boolean fine = !mat.equals("iron") && !mat.equals("steel");
        String wood = fine ? "dark_wood" : "wood";
        String string = fine ? "string_gold" : "string";
        // Ложе, направляющая и приклад со спуском снизу (низ — −z).
        box(m, -0.6, -3.0, -0.45, 0.6, 6.2, 0.45, wood, "full");
        box(m, -0.28, -4.8, 0.45, 0.28, 6.4, 0.68, met, "full");
        box(m, -0.7, -6.5, -0.55, 0.7, -3.0, 0.5, wood, "full");
        box(m, -0.75, -10.0, -1.1, 0.75, -6.5, 0.45, wood, "full");
        box(m, -0.8, -10.3, -1.15, 0.8, -10.0, 0.5, trim, "full");
        box(m, -0.1, -4.1, -1.2, 0.1, -3.5, -0.45, "metal_dark", "full");
        box(m, -0.12, -5.0, -1.45, 0.12, -2.9, -1.3, trim, "full");
        box(m, -0.12, -3.05, -1.45, 0.12, -2.9, -0.45, trim, "full");
        box(m, -0.42, -4.6, 0.45, 0.42, -3.9, 0.95, trim, "full");
        // Накладки с заклёпками по бокам ложа, кольцо на прикладе и мушка.
        for (int k = -1; k <= 1; k += 2) {
            box(m, k > 0 ? 0.6 : -0.69, -4.7, -0.36, k > 0 ? 0.69 : -0.6, -2.5, 0.36, trim, "full");
            for (double y : new double[]{-4.4, -2.8})
                box(m, k > 0 ? 0.68 : -0.73, y - 0.08, -0.08, k > 0 ? 0.73 : -0.68, y + 0.08, 0.08, trim, "light");
        }
        box(m, -0.79, -6.62, -1.14, 0.79, -6.4, 0.54, trim, "full");
        box(m, -0.08, 6.0, 0.68, 0.08, 6.22, 0.98, met, "light");
        // Крепление плеч и стремя.
        box(m, -0.95, 5.7, -0.15, 0.95, 7.0, 0.85, trim, "full");
        box(m, 0.6, 6.8, 0.2, 0.78, 8.1, 0.38, met, "full");
        box(m, -0.78, 6.8, 0.2, -0.6, 8.1, 0.38, met, "full");
        box(m, -0.78, 7.95, 0.2, 0.78, 8.13, 0.38, met, "full");
        // Плечи.
        boolean bent = !frame.equals("standby") && !frame.equals("pulling_0");
        double[] path = bent ? PROD_BENT : PROD_RELAXED;
        double[] end = null;
        for (int side = -1; side <= 1; side += 2) {
            end = chain(m, "xy", side * 0.9, 6.4, path, 0.55, 0.56, 0.35, met, "full", side < 0);
            boxC(m, end[0], end[1], 0.35, 0.5, 0.5, 0.72, trim, "light", 'y', 45, false);
        }
        double tx = end[0], ty = end[1];
        double a = switch (frame) {
            case "standby" -> 0;
            case "pulling_0", "pulling_1" -> 22.5;
            default -> 45;
        };
        double sz = 0.62, nock;
        if (a == 0) {
            box(m, -tx, ty - 0.08, sz - 0.07, tx, ty + 0.08, sz + 0.07, string, "full");
            nock = ty;
        } else {
            nock = ty - tx * Math.tan(Math.toRadians(a));
            double len = tx / Math.cos(Math.toRadians(a));
            seg(m, "xy", tx, ty, 180 + a, len, 0.16, 0.14, sz, string, "full");
            seg(m, "xy", -tx, ty, -a, len, 0.16, 0.14, sz, string, "full");
        }
        if (frame.equals("arrow")) {
            box(m, -0.1, nock, 0.68, 0.1, 6.6, 0.88, "shaft_wood", "full");
            box(m, -0.4, nock + 0.2, 0.76, 0.4, nock + 1.4, 0.8, "feather", "full");
            box(m, -0.03, nock + 0.2, 0.88, 0.03, nock + 1.4, 1.2, "feather", "full");
            boxC(m, 0, 6.9, 0.78, 0.55, 0.55, 0.16, "metal_iron", "light", 'z', 45, false);
        }
        if (frame.equals("firework")) {
            rod(m, 0, 1.06, nock + 0.1, 2.6, 0.38, "paper_red");
            rod(m, 0, 1.06, 2.6, 3.2, 0.24, "metal_iron");
            rod(m, 0, 1.06, 3.2, 3.6, 0.11, "metal_iron");
        }
    }

    // ---------------------------------------------------------------- жезлы и посохи

    record Focus(String shaft, String trim, boolean ornate) {
    }

    static Focus focus(String tier, String el) {
        return switch (tier) {
            case "apprentice" -> new Focus("wood", "metal_brass", false);
            case "rune" -> new Focus("rune_wood_" + el, "metal_gold", false);
            case "diamond" -> new Focus("arch_wood_" + el, "metal_diamond", true);
            case "faceted" -> new Focus("arch_wood_" + el, "metal_silver", true);
            default -> new Focus("arch_wood_" + el, "metal_gold", true);
        };
    }

    static void wand(M m, Focus fc, String gem) {
        rod(m, -9.3, 4.3, 0.36, fc.shaft());
        rod(m, -9.3, -5.6, 0.46, "grip_focus");
        rod(m, -9.85, -9.3, 0.5, fc.trim());
        rod(m, -10.05, -9.85, 0.3, fc.trim());
        rod(m, -5.75, -5.35, 0.5, fc.trim());
        rod(m, 3.9, 4.4, 0.52, fc.trim());
        rod(m, 4.3, 4.8, 0.42, fc.trim());
        // Четыре зубца оправы вокруг кристалла.
        double[] prong = {67.5, 1.3, 90, 0.9, 112.5, 0.5};
        for (int side = -1; side <= 1; side += 2) {
            chain(m, "xy", side * 0.36, 4.6, prong, 0.17, 0.17, 0, fc.trim(), "light", side < 0);
            chain(m, "zy", side * 0.36, 4.6, prong, 0.17, 0.17, 0, fc.trim(), "light", side < 0);
        }
        crystal(m, 6.3, fc.ornate() ? 2.0 : 1.7, fc.ornate() ? 0.5 : 0.42, gem, fc.ornate());
        if (fc.ornate()) for (int side = -1; side <= 1; side += 2) {
            boxC(m, side * 0.72, 4.15, 0, 0.3, 0.3, 0.3, "gem_" + gem, "full", 'y', 45, true);
            boxC(m, 0, 4.15, side * 0.72, 0.3, 0.3, 0.3, "gem_" + gem, "full", 'y', 45, true);
        }
    }

    static void staff(M m, Focus fc, String gem) {
        m.grip = -3.9;
        m.heldByCode = true;
        rod(m, -20.0, 5.3, 0.46, fc.shaft());
        rod(m, -5.8, -2.0, 0.56, "grip_focus");
        for (double y : new double[]{5.05, -1.75, -6.05, -10.1, -15.2}) rod(m, y - 0.22, y + 0.22, 0.6, fc.trim());
        rod(m, -20.6, -20.0, 0.42, fc.trim());
        rod(m, -20.9, -20.6, 0.22, fc.trim());
        rod(m, 5.2, 5.8, 0.48, fc.trim());
        // Оправа-полумесяц: у архимага клетка из четырёх дуг.
        double[] arc = {22.5, 1.0, 67.5, 1.0, 90, 1.3, 112.5, 1.0, 135, 0.75};
        for (int side = -1; side <= 1; side += 2) {
            chain(m, "xy", side * 0.42, 5.3, arc, 0.26, 0.26, 0, fc.trim(), "full", side < 0);
            if (fc.ornate()) chain(m, "zy", side * 0.42, 5.3, arc, 0.26, 0.26, 0, fc.trim(), "full", side < 0);
        }
        crystal(m, 7.2, fc.ornate() ? 2.0 : 1.7, fc.ornate() ? 0.58 : 0.5, gem, true);
        if (fc.ornate()) {
            for (int side = -1; side <= 1; side += 2) {
                boxC(m, side * 1.85, 7.2, 0, 0.36, 0.36, 0.36, "gem_" + gem, "full", 'y', 45, true);
                boxC(m, 0, 7.2, side * 1.85, 0.36, 0.36, 0.36, "gem_" + gem, "full", 'y', 45, true);
            }
            diamond(m, 0, 4.5, 0.3, 0.55, "gem_" + gem, "full", true);
        }
    }

    // =====================================================================================
    // Запись файлов
    // =====================================================================================

    static final String HANDS = """
                        "thirdperson_righthand": {"rotation": [0, -90, 10], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
                        "thirdperson_lefthand": {"rotation": [0, 90, -10], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
                        "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
                        "firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},""";

    /** Положения в руках как у ванильного щита (поднятый щит — отдельная модель). */
    static final String SHIELD_DISPLAY = """
                    {
                        "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [1, 1, 1]},
                        "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [1, 1, 1]},
                        "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 2, -10], "scale": [1.25, 1.25, 1.25]},
                        "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.25, 1.25, 1.25]},
                        "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.6, 0.6, 0.6]},
                        "fixed": {"rotation": [0, 180, 0], "translation": [-4.5, 4.5, -5], "scale": [0.55, 0.55, 0.55]},
                        "ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]}
                      }""";
    static final String SHIELD_BLOCKING = """
                    {
                        "thirdperson_righthand": {"rotation": [45, 155, 0], "translation": [-3.49, 11, -2], "scale": [1, 1, 1]},
                        "thirdperson_lefthand": {"rotation": [45, 155, 0], "translation": [11.51, 7, 2.5], "scale": [1, 1, 1]},
                        "firstperson_righthand": {"rotation": [0, 180, -5], "translation": [-15, 5, -11], "scale": [1.25, 1.25, 1.25]},
                        "firstperson_lefthand": {"rotation": [0, 180, -5], "translation": [5, 5, -11], "scale": [1.25, 1.25, 1.25]}
                      }""";

    /**
     * От первого лица: длинное оружие смотрит вверх, чуть от камеры и к центру экрана (ванильный наклон к камере
     * уводит клинок в рост за край экрана), рукоять — точно в кулаке.
     */
    static String firstPerson(M m) {
        if (m.twoHanded || m.heldByCode) {
            String t = "\"rotation\": [0, 0, 0], \"translation\": [0, " + f(-m.grip) + ", 0], \"scale\": [1, 1, 1]";
            return "                        \"firstperson_righthand\": {" + t + "},\n"
                    + "                        \"firstperson_lefthand\": {" + t + "},";
        }
        double yg = m.grip, s = 0.68, dx = -0.11, dy = 0.906, dz = -0.408, k = -yg * s;
        String t = "[" + f(0.9 + k * dx) + ", " + f(2.6 + k * dy) + ", " + f(0.4 + k * dz) + "]";
        return "                        \"firstperson_righthand\": {\"rotation\": [0, -75, 25], \"translation\": " + t + ", \"scale\": [0.68, 0.68, 0.68]},\n"
                + "                        \"firstperson_lefthand\": {\"rotation\": [0, 75, -25], \"translation\": " + t + ", \"scale\": [0.68, 0.68, 0.68]},";
    }

    /** Ручное оружие: как ванильный меч от третьего лица, свой хват от первого. */
    static String hands(M m) {
        // Двуручное оружие держат обе руки (позы в WeaponPoses и PolearmModels) — его положение не трогаем.
        if (m.twoHanded) return "                        \"thirdperson_righthand\": {\"rotation\": [0, -90, 10], \"translation\": [0, 4, 0.5], \"scale\": [0.85, 0.85, 0.85]},\n"
                + "                        \"thirdperson_lefthand\": {\"rotation\": [0, 90, -10], \"translation\": [0, 4, 0.5], \"scale\": [0.85, 0.85, 0.85]},\n"
                + firstPerson(m);
        // Модель стоит вертикально, а ванильный спрайт меча нарисован по диагонали: наклон на −45° и сдвиг, чтобы рукоять
        // (m.grip) попала в кулак — туда, где кулак держит рукоять ванильного меча (≈ (−4.5, −4.5) от центра спрайта).
        double s = 0.85, g = m.grip * 0.7071, dx = -4.5 - g, dy = -4.5 - g;
        double c = Math.cos(Math.toRadians(10)), sn = Math.sin(Math.toRadians(10));
        // Поворот ванильного положения Ry(−90)·Rz(10) переводит (x, y, 0) в (0, x·sin + y·cos, x·cos − y·sin).
        String t = "[0, " + f(4 + s * (dx * sn + dy * c)) + ", " + f(0.5 + s * (dx * c - dy * sn)) + "]";
        return "                        \"thirdperson_righthand\": {\"rotation\": [0, -90, -35], \"translation\": " + t + ", \"scale\": [0.85, 0.85, 0.85]},\n"
                + "                        \"thirdperson_lefthand\": {\"rotation\": [0, 90, 35], \"translation\": " + t + ", \"scale\": [0.85, 0.85, 0.85]},\n"
                + firstPerson(m);
    }

    /**
     * Стойка с древком: оружие стоит вертикально, нижний конец на полу. Рука чуть вынесена вперёд (поза в моде),
     * поворот 70° это компенсирует. yh — где на модели рука, s — масштаб.
     */
    static String verticalHands(M m, double s) {
        double yh = m.grip;
        String t = "[0, " + f(-0.342 * yh * s) + ", " + f(-0.940 * yh * s) + "]", sc = "[" + f(s) + ", " + f(s) + ", " + f(s) + "]";
        return "                        \"thirdperson_righthand\": {\"rotation\": [70, 0, 0], \"translation\": " + t + ", \"scale\": " + sc + "},\n"
                + "                        \"thirdperson_lefthand\": {\"rotation\": [70, 0, 0], \"translation\": " + t + ", \"scale\": " + sc + "},\n"
                + firstPerson(m);
    }

    /** Выпад копьём: древко вдоль вытянутой руки, остриё вперёд; от первого лица — копьё смотрит в прицел. */
    static String thrustDisplay(M m) {
        double yh = m.grip, s = 0.85;
        String fp = firstPerson(m);
        return "{\n"
                + "                        \"thirdperson_righthand\": {\"rotation\": [-90, 0, 0], \"translation\": [-1.5, 0, " + f(yh * s) + "], \"scale\": [0.85, 0.85, 0.85]},\n"
                + "                        \"thirdperson_lefthand\": {\"rotation\": [-90, 0, 0], \"translation\": [-1.5, 0, " + f(yh * s) + "], \"scale\": [0.85, 0.85, 0.85]},\n"
                + fp.substring(0, fp.length() - 1) + "\n"
                + "                      }";
    }

    /** Иконка, рамка и земля для вертикальной модели: по диагонали, вписаны по длине. */
    static String fitted(M m) {
        double len = m.maxY - m.minY, c = (m.maxY + m.minY) / 2, k = 0.7071 * c;
        double gui = Math.min(1.0, 21.0 / len), frame = Math.min(1.0, 18.0 / len), ground = 0.5;
        return "                        \"gui\": {\"rotation\": [0, 0, -45], \"translation\": [" + f(-k * gui) + ", " + f(-k * gui) + ", 0], \"scale\": [" + f(gui) + ", " + f(gui) + ", " + f(gui) + "]},\n"
                + "                        \"fixed\": {\"rotation\": [0, 180, -45], \"translation\": [" + f(k * frame) + ", " + f(-k * frame) + ", 0], \"scale\": [" + f(frame) + ", " + f(frame) + ", " + f(frame) + "]},\n"
                + "                        \"ground\": {\"rotation\": [0, 0, -45], \"translation\": [" + f(-k * ground) + ", " + f(2 - k * ground) + ", 0], \"scale\": [0.5, 0.5, 0.5]},\n"
                + "                        \"head\": {\"rotation\": [0, 180, -45], \"translation\": [0, 13, 7], \"scale\": [1, 1, 1]}\n"
                + "                      }";
    }

    /** Мушкет как ружьё: вдоль поднятой руки, приклад к плечу, прицел сверху. */
    static final String MUSKET_DISPLAY = """
                    {
                        "thirdperson_righthand": {"rotation": [-90, 90, 0], "translation": [0, 1, -4.6], "scale": [1, 1, 1]},
                        "thirdperson_lefthand": {"rotation": [-90, -90, 0], "translation": [0, 1, -4.6], "scale": [1, 1, 1]},
                        "firstperson_righthand": {"rotation": [-90, 90, 0], "translation": [1.6, 2.4, -2.3], "scale": [0.75, 0.75, 0.75]},
                        "firstperson_lefthand": {"rotation": [-90, -90, 0], "translation": [1.6, 2.4, -2.3], "scale": [0.75, 0.75, 0.75]},
                        "ground": {"rotation": [0, 0, 45], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
                        "head": {"rotation": [0, 180, 45], "translation": [0, 13, 7], "scale": [1, 1, 1]},
                        "fixed": {"rotation": [0, 180, 45], "scale": [0.7, 0.7, 0.7]}
                      }""";

    static String display(Kind kind, M m) {
        if (kind == Kind.POLEARM) return "{\n" + verticalHands(m, 0.85) + "\n" + fitted(m);
        if (kind == Kind.STAFF) return "{\n" + verticalHands(m, 0.85) + "\n"
                + "                        \"ground\": {\"rotation\": [0, 0, -45], \"translation\": [0, 2, 0], \"scale\": [0.5, 0.5, 0.5]},\n"
                + "                        \"head\": {\"rotation\": [0, 180, -45], \"translation\": [0, 13, 7], \"scale\": [1, 1, 1]},\n"
                + "                        \"fixed\": {\"rotation\": [0, 180, -45], \"scale\": [0.75, 0.75, 0.75]}\n"
                + "                      }";
        if (kind == Kind.MUSKET) return MUSKET_DISPLAY;
        if (kind == Kind.SHIELD) {
            // Иконка щита по его высоте: ванильный щит (22) — 0.65, маленький баклер — крупнее.
            double gui = Math.min(0.95, 0.65 * 22 / (m.maxY - m.minY)), cy = (m.maxY + m.minY) / 2;
            return SHIELD_DISPLAY.replace("\"translation\": [2, 3, 0], \"scale\": [0.6, 0.6, 0.6]",
                    "\"translation\": [2, " + f(3 - cy * gui) + ", 0], \"scale\": [" + f(gui) + ", " + f(gui) + ", " + f(gui) + "]");
        }
        if (kind == Kind.REAL) {
            // Модель стоит вертикально; иконка и рамка — по диагонали, вписаны по длине и отцентрованы.
            double len = m.maxY - m.minY, c = (m.maxY + m.minY) / 2, k = 0.7071 * c;
            double gui = Math.min(1.0, 21.0 / len), frame = Math.min(1.0, 18.0 / len), ground = 0.5;
            return "{\n" + hands(m) + "\n"
                    + "                        \"gui\": {\"rotation\": [0, 0, -45], \"translation\": [" + f(-k * gui) + ", " + f(-k * gui) + ", 0], \"scale\": [" + f(gui) + ", " + f(gui) + ", " + f(gui) + "]},\n"
                    + "                        \"fixed\": {\"rotation\": [0, 180, -45], \"translation\": [" + f(k * frame) + ", " + f(-k * frame) + ", 0], \"scale\": [" + f(frame) + ", " + f(frame) + ", " + f(frame) + "]},\n"
                    + "                        \"ground\": {\"rotation\": [0, 0, -45], \"translation\": [" + f(-k * ground) + ", " + f(2 - k * ground) + ", 0], \"scale\": [0.5, 0.5, 0.5]},\n"
                    + "                        \"head\": {\"rotation\": [0, 180, -45], \"translation\": [0, 13, 7], \"scale\": [1, 1, 1]}\n"
                    + "                      }";
        }
        return switch (kind) {
            case HANDHELD -> """
                    {
                        "thirdperson_righthand": {"rotation": [0, -90, 10], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
                        "thirdperson_lefthand": {"rotation": [0, 90, -10], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
                        "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
                        "firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
                        "ground": {"rotation": [0, 0, -45], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
                        "head": {"rotation": [0, 180, -45], "translation": [0, 13, 7], "scale": [1, 1, 1]},
                        "fixed": {"rotation": [0, 180, -45], "scale": [1, 1, 1]}
                      }""";
            // Двуручное оружие крупнее: в руках, на земле и в рамке.
            case TWO_HANDED -> """
                    {
                        "thirdperson_righthand": {"rotation": [0, -90, 10], "translation": [0, 5, 0.5], "scale": [1.12, 1.12, 1.12]},
                        "thirdperson_lefthand": {"rotation": [0, 90, -10], "translation": [0, 5, 0.5], "scale": [1.12, 1.12, 1.12]},
                        "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 3.6, 1.13], "scale": [0.86, 0.86, 0.86]},
                        "firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 3.6, 1.13], "scale": [0.86, 0.86, 0.86]},
                        "ground": {"rotation": [0, 0, -45], "translation": [0, 2, 0], "scale": [0.65, 0.65, 0.65]},
                        "head": {"rotation": [0, 180, -45], "translation": [0, 13, 7], "scale": [1, 1, 1]},
                        "fixed": {"rotation": [0, 180, -45], "scale": [1.2, 1.2, 1.2]}
                      }""";
            case BOW -> """
                    {
                        "thirdperson_righthand": {"rotation": [-80, 260, 5], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
                        "thirdperson_lefthand": {"rotation": [-80, -280, -5], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
                        "firstperson_righthand": {"rotation": [0, -90, 70], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
                        "firstperson_lefthand": {"rotation": [0, 90, -70], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
                        "ground": {"rotation": [0, 0, 45], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
                        "head": {"rotation": [0, 180, 45], "translation": [0, 13, 7], "scale": [1, 1, 1]},
                        "fixed": {"rotation": [0, 180, 45], "scale": [1, 1, 1]}
                      }""";
            case REAL, SHIELD, POLEARM, STAFF, MUSKET -> "{}";
            case CROSSBOW -> """
                    {
                        "thirdperson_righthand": {"rotation": [-90, 0, -15], "translation": [1.5, 0.5, -2.5], "scale": [0.95, 0.95, 0.95]},
                        "thirdperson_lefthand": {"rotation": [-90, 0, -15], "translation": [1.5, 0.5, -2.5], "scale": [0.95, 0.95, 0.95]},
                        "firstperson_righthand": {"rotation": [-90, 0, -10], "translation": [1.13, 3.2, 0.5], "scale": [0.72, 0.72, 0.72]},
                        "firstperson_lefthand": {"rotation": [-90, 0, -10], "translation": [1.13, 3.2, 0.5], "scale": [0.72, 0.72, 0.72]},
                        "ground": {"rotation": [0, 0, 45], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
                        "head": {"rotation": [0, 180, 45], "translation": [0, 13, 7], "scale": [1, 1, 1]},
                        "fixed": {"rotation": [0, 180, 45], "scale": [1, 1, 1]}
                      }""";
        };
    }

    static int written;

    /** 3D-модель в models/item/3d/ и обёртка models/item/<name>.json с иконкой для инвентаря. */
    static void write(String name, Kind kind, M m, String overrides) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"gui_light\": \"front\",\n  \"textures\": {\n");
        for (Map.Entry<String, String> e : m.tex.entrySet())
            sb.append("    \"").append(e.getKey()).append("\": \"").append(e.getValue()).append("\",\n");
        String particle = kind == Kind.SHIELD ? m.tex.values().iterator().next() : NS + ":item/" + name;
        sb.append("    \"particle\": \"").append(particle).append("\"\n  },\n");
        sb.append("  \"elements\": [\n");
        for (int i = 0; i < m.els.size(); i++)
            sb.append("    ").append(m.els.get(i)).append(i + 1 < m.els.size() ? ",\n" : "\n");
        sb.append("  ],\n  \"display\": ").append(display(kind, m)).append("\n}\n");
        Path model = res.resolve("assets/" + NS + "/models/item/3d/" + name + ".json");
        Files.createDirectories(model.getParent());
        Files.writeString(model, sb.toString(), StandardCharsets.UTF_8);

        Path item = res.resolve("assets/" + NS + "/models/item/" + name + ".json");
        String parent = "  \"parent\": \"" + NS + ":item/3d/" + name + "\"";
        if (kind == Kind.REAL) {
            // Иконка — та же объёмная модель, повёрнутая по диагонали.
            Files.writeString(item, "{\n" + parent + "\n}\n", StandardCharsets.UTF_8);
            written++;
            return;
        }
        if (kind == Kind.POLEARM) {
            // В стойке оружие вертикально; положение для удара — в <имя>_attack, мод плавно смешивает их по ходу удара (PolearmModels).
            Files.writeString(item, "{\n" + parent + "\n}\n", StandardCharsets.UTF_8);
            String h = hands(m);
            String attack = name.endsWith("spear") ? thrustDisplay(m) : "{\n" + h.substring(0, h.length() - 1) + "\n                      }";
            Files.writeString(res.resolve("assets/" + NS + "/models/item/" + name + "_attack.json"),
                    "{\n" + parent + ",\n  \"display\": " + attack + "\n}\n", StandardCharsets.UTF_8);
            written++;
            return;
        }
        if (kind == Kind.SHIELD) {
            Files.writeString(item, "{\n" + parent + ",\n  \"overrides\": [\n" + ov("\"blocking\": 1", name + "_blocking") + "\n  ]\n}\n", StandardCharsets.UTF_8);
            Files.writeString(res.resolve("assets/" + NS + "/models/item/" + name + "_blocking.json"),
                    "{\n" + parent + ",\n  \"display\": " + SHIELD_BLOCKING + "\n}\n", StandardCharsets.UTF_8);
            written++;
            return;
        }
        String wrapper = "{\n"
                + "  \"loader\": \"neoforge:separate_transforms\",\n"
                + "  \"gui_light\": \"front\",\n"
                + "  \"textures\": { \"particle\": \"" + NS + ":item/" + name + "\" },\n"
                + "  \"base\": { \"parent\": \"" + NS + ":item/3d/" + name + "\" },\n"
                + "  \"perspectives\": {\n"
                + "    \"gui\": { \"parent\": \"minecraft:item/generated\", \"textures\": { \"layer0\": \"" + NS + ":item/" + name + "\" } }\n"
                + "  }" + (overrides == null ? "" : ",\n  \"overrides\": [\n" + overrides + "\n  ]") + "\n}\n";
        Files.writeString(res.resolve("assets/" + NS + "/models/item/" + name + ".json"), wrapper, StandardCharsets.UTF_8);
        written++;
    }

    interface Builder {
        void build(M m);
    }

    static void one(String name, Kind kind, Builder b) throws IOException {
        one(name, kind, b, null);
    }

    static void one(String name, Kind kind, Builder b, String overrides) throws IOException {
        M m = new M();
        b.build(m);
        write(name, kind, m, overrides);
    }

    static String ov(String predicate, String model) {
        return "    { \"predicate\": { " + predicate + " }, \"model\": \"" + NS + ":item/" + model + "\" }";
    }

    /** Материалы грейдов I–VI. */
    static final String[] GRADES = {"iron", "steel", "damascus", "diamond", "faceted", "brilliant"};

    static int makeModels() throws IOException {
        for (String mat : GRADES) {
            S s = style(mat);
            one(mat + "_dagger", Kind.REAL, m -> dagger(m, s));
            one(mat + "_greatsword", Kind.REAL, m -> greatsword(m, s));
            one(mat + "_sword", Kind.REAL, m -> sword(m, s));
            if (!mat.equals("iron")) one(mat + "_axe", Kind.REAL, m -> axe(m, s));
            one(mat + "_rapier", Kind.REAL, m -> rapier(m, s));
            one(mat + "_spear", Kind.POLEARM, m -> spear(m, s));
            one(mat + "_flanged_mace", Kind.REAL, m -> flangedMace(m, s));
            one(mat + "_warhammer", Kind.POLEARM, m -> warhammer(m, s, mat));
            one(mat.equals("iron") ? "musket" : mat + "_musket", Kind.MUSKET, m -> musket(m, mat));
        }

        String[] grades = GRADES;
        for (int g = 1; g <= 6; g++) {
            int grade = g;
            String mat = grades[g - 1];
            one(mat + "_buckler", Kind.SHIELD, m -> { m.shift = 0; buckler(m, grade); });
            one(mat + "_heater_shield", Kind.SHIELD, m -> { m.shift = 0; heaterShield(m, grade); });
            one(mat + "_kite_shield", Kind.SHIELD, m -> { m.shift = 0; kiteShield(m, grade); });
        }

        for (String bowName : new String[]{"composite_bow", "ranger_longbow"}) {
            boolean comp = bowName.startsWith("composite");
            String ovs = String.join(",\n",
                    ov("\"pulling\": 1", bowName + "_pulling_0"),
                    ov("\"pulling\": 1, \"pull\": 0.65", bowName + "_pulling_1"),
                    ov("\"pulling\": 1, \"pull\": 0.9", bowName + "_pulling_2"));
            one(bowName, Kind.BOW, m -> bow(m, comp, 0), ovs);
            for (int f = 0; f < 3; f++) {
                int frame = f + 1;
                one(bowName + "_pulling_" + f, Kind.BOW, m -> bow(m, comp, frame));
            }
        }
        for (String mat : new String[]{"diamond", "faceted", "brilliant"}) {
            String bowName = mat + "_longbow";
            String ovs = String.join(",\n",
                    ov("\"pulling\": 1", bowName + "_pulling_0"),
                    ov("\"pulling\": 1, \"pull\": 0.65", bowName + "_pulling_1"),
                    ov("\"pulling\": 1, \"pull\": 0.9", bowName + "_pulling_2"));
            one(bowName, Kind.BOW, m -> grandBow(m, mat, 0), ovs);
            for (int f = 0; f < 3; f++) {
                int frame = f + 1;
                one(bowName + "_pulling_" + f, Kind.BOW, m -> grandBow(m, mat, frame));
            }
        }

        for (String mat : GRADES) {
            String base = mat.equals("iron") ? "hand_crossbow" : mat + "_hand_crossbow";
            String ovs = String.join(",\n",
                    ov("\"pulling\": 1", base + "_pulling_0"),
                    ov("\"pulling\": 1, \"pull\": 0.58", base + "_pulling_1"),
                    ov("\"pulling\": 1, \"pull\": 1.0", base + "_pulling_2"),
                    ov("\"charged\": 1", base + "_arrow"),
                    ov("\"charged\": 1, \"firework\": 1", base + "_firework"));
            one(base, Kind.CROSSBOW, m -> crossbow(m, mat, "standby"), ovs);
            for (String fr : new String[]{"pulling_0", "pulling_1", "pulling_2", "arrow", "firework"})
                one(base + "_" + fr, Kind.CROSSBOW, m -> crossbow(m, mat, fr));
        }

        one("apprentice_wand", Kind.HANDHELD, m -> wand(m, focus("apprentice", null), "amethyst"));
        one("apprentice_staff", Kind.STAFF, m -> staff(m, focus("apprentice", null), "amethyst"));
        for (String el : new String[]{"fire", "frost", "earth", "storm"}) {
            for (String tier : new String[]{"rune", "archmage", "diamond", "faceted", "brilliant"}) {
                Focus fc = focus(tier, el);
                one(el + "_" + tier + "_wand", Kind.HANDHELD, m -> wand(m, fc, el));
                one(el + "_" + tier + "_staff", Kind.STAFF, m -> staff(m, fc, el));
            }
        }
        return written;
    }
}
