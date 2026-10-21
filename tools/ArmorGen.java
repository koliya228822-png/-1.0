import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Объёмная классовая броня: детали каждого комплекта (кубоиды на голове, теле, руках и ногах),
 * раскладка их развёрток по текстуре 128×128 и сама текстура в 4 раза подробнее (512×512).
 * Пишет текстуры в textures/models/armor3d/ и данные моделей в client/armor/ArmorShapes.java.
 *
 * Координаты — как у моделей Minecraft: y вниз, лицо смотрит в −z; детали рук и ног задаются для правой
 * стороны и зеркалятся на левую. Запуск: java tools/ArmorGen.java .
 */
public class ArmorGen {
    static final int R = 4, TW = 128, TH = 128;
    static final String[] BONES = {"head", "body", "right_arm", "left_arm", "right_leg", "left_leg"};
    static final String[] SLOTS = {"head", "chest", "legs", "feet"};
    static final String[] SETS = {"warrior_iron", "warrior", "warrior_damascus", "archer_leather", "archer",
            "archer_impregnated", "mage_wool", "mage", "mage_astral"};

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : ".");
        Path texDir = root.resolve("src/main/resources/assets/heroicpaths/textures/models/armor3d");
        Files.createDirectories(texDir);
        StringBuilder java = new StringBuilder();
        java.append("package com.heroicpaths.client.armor;\n\n")
                .append("// Сгенерировано tools/ArmorGen.java — не править вручную.\n")
                .append("// Строка детали: кость, родитель, смещение x y z, поворот x y z, развёртка u v,\n")
                .append("// кубоид x y z, размер w h d, раздувание, зеркальная развёртка.\n")
                .append("public final class ArmorShapes {\n")
                .append("    public static final int TEX_W = ").append(TW).append(", TEX_H = ").append(TH).append(";\n")
                .append("    public static final String[] SETS = {\"").append(String.join("\", \"", SETS)).append("\"};\n\n")
                .append("    private ArmorShapes() {}\n\n");
        StringBuilder sw = new StringBuilder("    public static float[][] get(String key) {\n        return switch (key) {\n");
        for (String set : SETS) {
            Builder b = new Builder(set);
            switch (b.cls) {
                case "warrior" -> warrior(b);
                case "archer" -> archer(b);
                default -> mage(b);
            }
            b.pack();
            ImageIO.write(b.paint(), "png", texDir.resolve(set + ".png").toFile());
            for (String slot : SLOTS) {
                String name = (set + "_" + slot).toUpperCase(Locale.ROOT);
                java.append("    private static final float[][] ").append(name).append(" = {\n");
                for (Cube c : b.slots.get(slot)) java.append("            ").append(c.row()).append(",\n");
                java.append("    };\n\n");
                sw.append("            case \"").append(set).append('/').append(slot).append("\" -> ").append(name).append(";\n");
            }
            System.out.println(set + ": " + b.blocks.size() + " blocks, uv height " + b.usedH);
            if (b.cls.equals("mage")) ImageIO.write(hatIcon(set, b.grade), "png",
                    root.resolve("src/main/resources/assets/heroicpaths/textures/item/" + set + "_helmet.png").toFile());
        }
        sw.append("            default -> null;\n        };\n    }\n");
        java.append(sw).append("}\n");
        Path out = root.resolve("src/main/java/com/heroicpaths/client/armor/ArmorShapes.java");
        Files.createDirectories(out.getParent());
        Files.writeString(out, java.toString(), StandardCharsets.UTF_8);
    }

    // =====================================================================================
    // Детали
    // =====================================================================================

    static final class Cube {
        int bone, parent = -1;
        double px, py, pz, rx, ry, rz, x, y, z, w, h, d, inflate;
        String mat;
        String[] deco;
        boolean mirror;
        int u, v;

        Cube copy() {
            Cube c = new Cube();
            c.bone = bone; c.parent = parent; c.px = px; c.py = py; c.pz = pz; c.rx = rx; c.ry = ry; c.rz = rz;
            c.x = x; c.y = y; c.z = z; c.w = w; c.h = h; c.d = d; c.inflate = inflate; c.mat = mat; c.deco = deco;
            c.mirror = mirror;
            return c;
        }

        String key() {
            return f(w) + "|" + f(h) + "|" + f(d) + "|" + mat + "|" + String.join(",", deco);
        }

        String row() {
            double[] a = {bone, parent, px, py, pz, rx, ry, rz, u, v, x, y, z, w, h, d, inflate, mirror ? 1 : 0};
            StringBuilder sb = new StringBuilder("{");
            for (int i = 0; i < a.length; i++) sb.append(i == 0 ? "" : ", ").append(f(a[i])).append(a[i] == Math.rint(a[i]) ? "" : "F");
            return sb.append('}').toString();
        }
    }

    static String f(double v) {
        double r = Math.round(v * 1000) / 1000.0;
        if (r == Math.rint(r)) return String.valueOf((long) r);
        return String.valueOf(r);
    }

    static final class Builder {
        final String set, cls;
        final int grade;
        final Map<String, List<Cube>> slots = new LinkedHashMap<>();
        List<Cube> cur;
        final Map<String, Cube> blocks = new LinkedHashMap<>();
        int usedH;

        Builder(String set) {
            this.set = set;
            this.cls = set.split("_")[0];
            this.grade = set.contains("_") ? (set.endsWith("iron") || set.endsWith("leather") || set.endsWith("wool") ? 1 : 3) : 2;
            for (String s : SLOTS) slots.put(s, new ArrayList<>());
        }

        void slot(String s) {
            cur = slots.get(s);
        }

        /** Деталь на кости или на другой детали (parent ≥ 0) со своей точкой поворота и поворотом в радианах. */
        int part(String bone, int parent, double px, double py, double pz, double rx, double ry, double rz,
                 double x, double y, double z, double w, double h, double d, double inflate, String mat, String... deco) {
            Cube c = new Cube();
            c.bone = Arrays.asList(BONES).indexOf(bone);
            c.parent = parent;
            c.px = px; c.py = py; c.pz = pz; c.rx = rx; c.ry = ry; c.rz = rz;
            c.x = x; c.y = y; c.z = z; c.w = w; c.h = h; c.d = d; c.inflate = inflate;
            c.mat = mat;
            c.deco = deco;
            cur.add(c);
            return cur.size() - 1;
        }

        int box(String bone, double x, double y, double z, double w, double h, double d, double inflate, String mat, String... deco) {
            return part(bone, -1, 0, 0, 0, 0, 0, 0, x, y, z, w, h, d, inflate, mat, deco);
        }

        /**
         * Зеркальные копии деталей: на противоположную руку/ногу (cross) или на ту же кость (голова, тело).
         * Цепочки родителей копируются вместе; у копии на другой конечности чуть больше раздувание,
         * чтобы совпадающие грани правой и левой ноги не мерцали.
         */
        void mirror(boolean cross, int... idx) {
            Map<Integer, Integer> map = new HashMap<>();
            for (int i : idx) {
                Cube c = cur.get(i).copy();
                if (cross) {
                    c.bone = c.bone == 2 ? 3 : c.bone == 4 ? 5 : c.bone;
                    c.inflate += 0.02;
                }
                c.parent = c.parent < 0 ? -1 : map.getOrDefault(c.parent, c.parent);
                c.px = -c.px;
                c.ry = -c.ry;
                c.rz = -c.rz;
                c.x = -(c.x + c.w);
                c.mirror = !c.mirror;
                map.put(i, cur.size());
                cur.add(c);
            }
        }

        /** Деталь правой конечности сразу с копией на левую. */
        int limb(String bone, double x, double y, double z, double w, double h, double d, double inflate, String mat, String... deco) {
            int i = box(bone, x, y, z, w, h, d, inflate, mat, deco);
            mirror(true, i);
            return i;
        }

        /** Раскладка развёрток: одинаковые детали (и зеркальные копии) делят один участок текстуры. */
        void pack() {
            for (List<Cube> list : slots.values())
                for (Cube c : list) blocks.putIfAbsent(c.key(), c);
            List<Cube> order = new ArrayList<>(blocks.values());
            order.sort((a, b) -> Double.compare(b.h + b.d, a.h + a.d));
            int x = 0, y = 0, rowH = 0;
            Map<String, int[]> pos = new HashMap<>();
            for (Cube c : order) {
                int bw = (int) Math.ceil(2 * (c.w + c.d)) + 1, bh = (int) Math.ceil(c.h + c.d) + 1;
                if (x + bw > TW) {
                    x = 0;
                    y += rowH;
                    rowH = 0;
                }
                if (bw > TW) throw new IllegalStateException("too wide " + c.key());
                pos.put(c.key(), new int[]{x, y});
                x += bw;
                rowH = Math.max(rowH, bh);
            }
            usedH = y + rowH;
            if (usedH > TH) throw new IllegalStateException(set + ": uv overflow " + usedH);
            for (List<Cube> list : slots.values())
                for (Cube c : list) {
                    int[] p = pos.get(c.key());
                    c.u = p[0];
                    c.v = p[1];
                }
        }

        BufferedImage paint() {
            BufferedImage img = new BufferedImage(TW * R, TH * R, BufferedImage.TYPE_INT_ARGB);
            Map<String, Pal> pal = palettes(set);
            for (Cube c : blocks.values()) {
                int[] uv = null;
                for (List<Cube> list : slots.values())
                    for (Cube k : list) if (k.key().equals(c.key())) uv = new int[]{k.u, k.v};
                Painter p = new Painter(img, pal, set, c, uv[0], uv[1]);
                p.all();
            }
            return img;
        }
    }

    // =====================================================================================
    // Комплекты
    // =====================================================================================

    static final double P = Math.PI / 180;

    /** Латы мечника: шлем с забралом и гребнем, наплечники, табар, набедренники и сабатоны. */
    static void warrior(Builder b) {
        int g = b.grade;
        b.slot("head");
        b.box("head", -4, -8, -4, 8, 8, 8, 1.0, "metal", "visor", "rivets", "bevel");
        b.box("head", -0.625, -10.25, -4.75, 1.25, 1.5, 9.5, 0, "trim", "bevel");
        b.box("head", -0.5, -7.5, -5.5, 1, 5.5, 0.6, 0, "metal", "ridge");
        b.box("head", -5.4, -0.75, -5.4, 10.8, 1.5, 10.8, 0, "metal2", "rivets", "bevel");
        int pl = b.part("head", -1, 0, -10, 0.5, -20 * P, 0, 0, -0.75, -3.5, -2.25, 1.5, 3.5, 4.5, 0, "plume");
        if (g >= 2) b.part("head", pl, 0, 0, 2.25, -45 * P, 0, 0, -0.6, -3.5, -0.75, 1.2, 3.5, 2, 0, "plume");
        if (g >= 3) {
            int h1 = b.part("head", -1, -5, -6, 0, 0, 0, 20 * P, -2.5, -0.75, -0.75, 2.5, 1.5, 1.5, 0, "trim", "bevel");
            int h2 = b.part("head", h1, -2.25, 0, 0, 0, 0, -45 * P, -0.6, -2.75, -0.6, 1.2, 3, 1.2, 0, "trim", "bevel");
            b.mirror(false, h1, h2);
        }

        b.slot("chest");
        b.box("body", -4, 0, -2, 8, 12, 4, 1.0, "metal", "ridge", "rivets", "bevel");
        b.box("body", -5.25, -1.5, -3.25, 10.5, 2, 6.5, 0, "metal2", "rivets", "bevel");
        b.box("body", -2.25, 1.5, -3.5, 4.5, 10.25, 0.5, 0, "cloth", "cross", g >= 2 ? "trim_b" : "hem");
        b.box("body", -5.25, 10.5, -3.25, 10.5, 2.25, 6.5, 0, "metal2", "rivets", "bevel");
        int[] pd = {
                b.box("right_arm", -4.5, -3.75, -3.5, 6, 2.5, 7, 0, "metal", "bevel", g >= 3 ? "trim_b" : "rivets"),
                b.box("right_arm", -4.75, -1.5, -3.6, 5.5, 1.75, 7.2, 0, "metal2", "rivets", "bevel"),
                b.box("right_arm", -5, 0, -3.75, 5.25, 1.75, 7.5, 0, "metal", "bevel", g >= 2 ? "trim_b" : "rivets")};
        b.mirror(true, pd);
        b.limb("right_arm", -3, -2, -2, 4, 12, 4, 1.0, "metal", "bevel");
        b.limb("right_arm", -4.4, 7.5, -3.4, 6.8, 2.5, 6.8, 0, "metal", "bevel", g >= 2 ? "trim_b" : "rivets");
        if (g >= 3) {
            int s1 = b.part("right_arm", -1, -3.5, -3.75, 0, 0, 0, -20 * P, -0.5, -2.25, -0.5, 1, 2.25, 1, 0, "trim", "bevel");
            int s2 = b.part("right_arm", -1, -3, -3.75, -2.25, 0, 0, -20 * P, -0.4, -1.75, -0.4, 0.8, 1.75, 0.8, 0, "trim", "bevel");
            int s3 = b.part("right_arm", -1, -3, -3.75, 2.25, 0, 0, -20 * P, -0.4, -1.75, -0.4, 0.8, 1.75, 0.8, 0, "trim", "bevel");
            b.mirror(true, s1, s2, s3);
        }

        b.slot("legs");
        b.box("body", -4.5, 9.5, -2.5, 9, 3, 5, 0, "metal2", "bevel");
        b.box("body", -4.75, 10, -2.75, 9.5, 1.5, 5.5, 0, "leather2", "buckle", "stitch");
        b.limb("right_leg", -2, 0, -2, 4, 12, 4, 0.5, "metal2", "bevel");
        b.limb("right_leg", -2.75, -0.25, -3.25, 5.5, 3.75, 0.75, 0, "metal", "rivets", "bevel");
        b.limb("right_leg", -3.25, 0, -2.5, 0.75, 3.5, 5, 0, "metal", "bevel");
        b.limb("right_leg", -2.5, 4.75, -3.4, 5, 2.25, 1, 0, "metal", "ridge", "bevel");
        b.limb("right_leg", -3, 5, -2.25, 0.6, 1.75, 3, 0, "metal", "bevel");

        b.slot("feet");
        b.limb("right_leg", -2.5, 7.75, -2.5, 5, 4.75, 5, 0.25, "metal", "rivets", "bevel");
        b.limb("right_leg", -2.25, 10.25, -4, 4.5, 2.25, 1.5, 0, "metal2", "bevel");
        b.limb("right_leg", -1.75, 11, -4.75, 3.5, 1.5, 1, 0, "metal", "bevel");
        b.limb("right_leg", -0.5, 7.5, -3.15, 1, 4.5, 0.5, 0, "trim", "bevel");
    }

    /** Лёгкая броня лучника: капюшон с маской, накидка, ремень через грудь, колчан, наручи и сапоги. */
    static void archer(Builder b) {
        int g = b.grade;
        b.slot("head");
        b.box("head", -4, -8, -4, 8, 8, 8, 0.75, "cloth", "opening", "stitch");
        b.box("head", -3.25, -9.75, -2.5, 6.5, 1.25, 6.5, 0, "cloth", "stitch");
        b.part("head", -1, 0, -8, 4.5, 35 * P, 0, 0, -1.75, -1.25, -0.5, 3.5, 2.5, 2.75, 0, "cloth");
        b.box("head", -5, -2.5, -1.5, 10, 2.75, 6.75, 0, "cloth", "jagged");
        if (g >= 2) b.box("head", -4.9, -3.75, -5, 9.8, 3.5, 1, 0, "cloth2", "folds_h");
        if (g >= 3) b.part("head", -1, 4.75, -8, 1, 0, 0, 25 * P, -0.25, -4.5, -0.75, 0.5, 4.5, 1.5, 0, "feather");

        b.slot("chest");
        b.box("body", -4, 0, -2, 8, 12, 4, 0.6, "leather", "lace", "stitch");
        b.box("body", -4.9, -0.75, -2.9, 9.8, 3.75, 5.8, 0, "cloth", "jagged");
        b.part("body", -1, 0, 5.5, -2.85, 0, 0, -36 * P, -0.7, -7.5, -0.3, 1.4, 15, 0.6, 0, "leather2", "stitch");
        b.part("body", -1, 0, 5.5, 2.85, 0, 0, -36 * P, -0.7, -7.5, -0.3, 1.4, 15, 0.6, 0, "leather2", "stitch");
        // Колчан за спиной: тёмная кожа с окантовкой, над правым плечом торчат стрелы с оперением.
        int q = b.part("body", -1, -0.75, 4, 3.9, 0, 0, -23 * P, -1.75, -4.75, -1.3, 3.5, 9.5, 2.6, 0, "leather2", "stitch", "trim_t", "trim_b");
        double[][] arrows = {{-1.2, -0.7, 3.8}, {-0.2, 0.2, 3.2}, {0.75, -0.45, 3.6}, {0.2, -0.95, 4.2}};
        for (double[] a : arrows) {
            b.part("body", q, 0, -4.75, 0, 0, 0, 0, a[0] - 0.15, -a[2], a[1] - 0.15, 0.3, a[2], 0.3, 0, "shaft");
            b.part("body", q, 0, -4.75, 0, 0, 0, 0, a[0] - 0.4, -a[2] - 0.2, a[1] - 0.1, 0.8, 1.6, 0.2, 0, "feather");
        }
        b.limb("right_arm", -3.75, -2.9, -2.9, 5.5, 2.75, 5.8, 0, "leather", "stitch", g >= 3 ? "trim_b" : "hem");
        b.limb("right_arm", -3, -2, -2, 4, 12, 4, 0.5, "cloth2");
        b.limb("right_arm", -3.6, 5.5, -2.6, 5.2, 4.75, 5.2, 0, "leather2", "lace", "stitch");

        b.slot("legs");
        b.box("body", -4.75, 10, -2.75, 9.5, 1.75, 5.5, 0, "leather2", "buckle", "stitch");
        b.box("body", 1.5, 10.5, -3.6, 2.25, 2.75, 1, 0, "leather", "stitch");
        b.box("body", -5.25, 10.25, -1.25, 0.75, 3, 2.5, 0, "leather", "stitch");
        b.limb("right_leg", -2, 0, -2, 4, 12, 4, 0.5, "cloth2", "folds");
        b.limb("right_leg", -2.4, 4.25, -3, 4.8, 2.5, 0.75, 0, "leather", "stitch");
        b.limb("right_leg", -2.65, 1.5, -2.65, 5.3, 0.75, 5.3, 0, "leather2");

        b.slot("feet");
        b.limb("right_leg", -2.25, 6.75, -2.25, 4.5, 5.5, 4.5, 0.4, "leather", "stitch");
        b.limb("right_leg", -2.9, 6, -2.9, 5.8, 1.5, 5.8, 0, "fur");
        b.limb("right_leg", -2.25, 10.25, -3.4, 4.5, 2, 1, 0, "leather");
        b.limb("right_leg", -2.6, 12, -3.6, 5.2, 0.75, 6.2, 0, "leather2");
    }

    /** Мантия мага: остроконечный колпак с полями и лентой, накидка, рукава-колокола, пояс, полы и туфли. */
    static void mage(Builder b) {
        int g = b.grade;
        b.slot("head");
        b.box("head", -4.75, -10.75, -4.75, 9.5, 3.25, 9.5, 0, "cloth", "folds");
        b.box("head", -7.5, -8, -7.5, 15, 0.75, 15, 0, "cloth", "rim");
        b.box("head", -5, -9, -5, 10, 1.25, 10, 0, "cloth2", g >= 2 ? "buckle" : "stitch");
        // Конус колпака: ярусы плавно сужаются и чуть уходят назад.
        double[][] tiers = {{8.25, 1}, {7.4, 1}, {6.55, 1}, {5.75, 1}, {4.95, 1}, {4.2, 0.95}, {3.5, 0.9}, {2.85, 0.85}};
        double ty = -10.75, tz = 0;
        for (double[] tr : tiers) {
            ty -= tr[1];
            tz += 0.2;
            b.box("head", -tr[0] / 2, ty, -tr[0] / 2 + tz, tr[0], tr[1], tr[0], 0, "cloth", "folds");
        }
        int t1 = b.part("head", -1, 0, ty, tz, -26 * P, 0, 0, -1.1, -2.0, -1.1, 2.2, 2.0, 2.2, 0, "cloth");
        int t2 = b.part("head", t1, 0, -2.25, 0, -32 * P, 0, 0, -0.75, -2, -0.75, 1.5, 2, 1.5, 0, "cloth");
        int t3 = b.part("head", t2, 0, -2, 0, -34 * P, 0, 0, -0.4, -1.6, -0.4, 0.8, 1.6, 0.8, 0, "cloth");
        if (g >= 3) b.part("head", t3, 0, -1.6, 0, 0, 45 * P, 0, -0.6, -1.2, -0.6, 1.2, 1.2, 1.2, 0, "star");
        if (g >= 2) b.box("head", -0.75, -9.25, -5.4, 1.5, 1.75, 0.6, 0, "gem");

        b.slot("chest");
        b.box("body", -4, 0, -2, 8, 12, 4, 0.6, "cloth", "robe", "folds");
        b.box("body", -4.9, -0.75, -2.9, 9.8, 3.75, 5.8, 0, "cloth2", "trim_b");
        if (g >= 2) b.box("body", -0.75, 1, -3.25, 1.5, 1.5, 0.6, 0, "gem");
        b.limb("right_arm", -3.9, -2.9, -2.9, 5.8, 2.5, 5.8, 0, "cloth2", "trim_b");
        b.limb("right_arm", -3, -2, -2, 4, 12, 4, 0.55, "cloth", "folds");
        b.limb("right_arm", -3.9, 6.25, -2.9, 5.8, 3, 5.8, 0, "cloth", "trim_b");
        b.limb("right_arm", -4.25, 8.75, -3.25, 6.5, 2.25, 6.5, 0, "cloth2", "trim_b");

        b.slot("legs");
        b.box("body", -4.75, 9.75, -2.75, 9.5, 2, 5.5, 0, "cloth2", "trim_t", "trim_b");
        b.box("body", -1, 9.5, -3.1, 2, 2.5, 0.5, 0, "trim", "bevel");
        b.box("body", 0.75, 11.5, -2.9, 1.5, 4, 0.4, 0, "cloth2", "trim_b");
        b.limb("right_leg", -2.25, -0.25, -2.25, 4.5, 9.5, 4.5, 0.5, "cloth", "folds");
        b.limb("right_leg", -3, 8.5, -3, 6, 2.75, 6, 0, "cloth", "trim_b", "folds");

        b.slot("feet");
        b.limb("right_leg", -2.25, 9.75, -2.5, 4.5, 2.5, 4.75, 0.3, "leather", "stitch");
        b.limb("right_leg", -2.75, 9.25, -2.75, 5.5, 0.75, 5.5, 0, "trim", "bevel");
        b.limb("right_leg", -1.5, 10.75, -4.25, 3, 1.75, 1.75, 0, "leather");
        b.limb("right_leg", -0.75, 10, -4.85, 1.5, 1.5, 1, 0, "leather");
    }

    // =====================================================================================
    // Палитры
    // =====================================================================================

    record Pal(Color o, Color d, Color m, Color l, Color h) {
        static Pal of(String o, String d, String m, String l, String h) {
            return new Pal(c(o), c(d), c(m), c(l), c(h));
        }
    }

    static Color c(String hex) {
        return new Color(Integer.parseInt(hex, 16));
    }

    static final Pal IRON = Pal.of("24262E", "5A5F6C", "969CA8", "D0D5DE", "F4F6FA");
    static final Pal IRON2 = Pal.of("1C1E24", "464A55", "707684", "A0A6B2", "C8CCD4");
    static final Pal STEEL = Pal.of("161E2C", "3C4A64", "6E86A8", "B2C4DE", "F2F8FF");
    static final Pal STEEL2 = Pal.of("101622", "2C3648", "4E5E78", "7A8CA8", "A8B8D0");
    static final Pal DAMASCUS = Pal.of("0E101A", "2C3042", "565C72", "8E96AC", "D2DAEA");
    static final Pal BLACK = Pal.of("08080C", "1A1A22", "2E2E3A", "4A4A5A", "70707E");
    static final Pal SILVER = Pal.of("2A2E36", "5C6470", "9AA2B0", "D0D6E0", "FFFFFF");
    static final Pal GOLD = Pal.of("3A2606", "7A5414", "C08A2C", "ECC462", "FFF2B8");
    static final Pal BRASS = Pal.of("3A2A0E", "6E5222", "A8823E", "D8B46A", "F8E6B0");
    static final Pal LEATHER = Pal.of("1C1008", "4A2C14", "7C4E24", "A87444", "D09C68");
    static final Pal LEATHER_DARK = Pal.of("120A06", "2A1A10", "40281A", "5A3C26", "7A5436");
    static final Pal LEATHER_BLACK = Pal.of("0C0806", "1E1610", "302418", "4A3828", "6A523C");
    static final Pal FUR = Pal.of("3A3022", "7A6A50", "B0A080", "D8CCAE", "F0E8D4");
    static final Pal FEATHER = Pal.of("6A6A64", "A8A8A0", "D8D8D0", "F2F2EC", "FFFFFF");

    static Map<String, Pal> palettes(String set) {
        Map<String, Pal> p = new HashMap<>();
        p.put("feather", FEATHER);
        p.put("shaft", Pal.of("3A2410", "6A4824", "9A7240", "C49A60", "E0C08A"));
        p.put("gem", Pal.of("2A0E4A", "5A2E9A", "8A4AD8", "C8A4F4", "F4ECFF"));
        p.put("star", Pal.of("5A4A10", "B09020", "F0D040", "FFF080", "FFFFE0"));
        switch (set) {
            case "warrior_iron" -> {
                p.put("metal", IRON); p.put("metal2", IRON2); p.put("trim", BRASS);
                p.put("cloth", Pal.of("0E1428", "1E2C5A", "30458A", "4E66B0", "7C92D0"));
                p.put("leather2", LEATHER_DARK); p.put("plume", Pal.of("0A1430", "1C3478", "3058C0", "5C84E0", "A0BEF4"));
            }
            case "warrior" -> {
                p.put("metal", STEEL); p.put("metal2", STEEL2); p.put("trim", SILVER);
                p.put("cloth", Pal.of("2A0408", "5A0E16", "8E1A24", "BC3440", "E07078"));
                p.put("leather2", LEATHER_DARK); p.put("plume", Pal.of("3A0408", "7A0E18", "B81E2A", "E04450", "FF8A94"));
            }
            case "warrior_damascus" -> {
                p.put("metal", DAMASCUS); p.put("metal2", BLACK); p.put("trim", GOLD);
                p.put("cloth", Pal.of("0A0204", "2A0608", "5A0E14", "8A1C24", "B8404A"));
                p.put("leather2", LEATHER_BLACK); p.put("plume", Pal.of("080406", "240A0E", "4A1018", "7A1C26", "A8303A"));
                p.put("gem", Pal.of("4A0610", "8A0E1E", "C01A2C", "FF8A94", "FFE0E4"));
            }
            case "archer_leather" -> {
                p.put("cloth", Pal.of("1E1208", "4A2E14", "7A5028", "A47444", "C89A68"));
                p.put("cloth2", Pal.of("2A2014", "5A4A30", "8A7450", "B49C72", "D8C49C"));
                p.put("leather", LEATHER); p.put("leather2", LEATHER_DARK); p.put("fur", FUR); p.put("trim", BRASS);
            }
            case "archer" -> {
                p.put("cloth", Pal.of("0E1A08", "24401A", "3C6428", "5E8C40", "88B464"));
                p.put("cloth2", Pal.of("101410", "283028", "40483C", "5C6656", "808A78"));
                p.put("leather", Pal.of("1C1008", "3C2414", "664028", "8C6040", "B08060"));
                p.put("leather2", LEATHER_DARK); p.put("fur", Pal.of("2A2418", "5A5038", "8A7C5C", "B0A482", "D0C6A8"));
                p.put("trim", BRASS);
            }
            case "archer_impregnated" -> {
                p.put("cloth", Pal.of("08080A", "18161C", "2A262E", "423C46", "625A66"));
                p.put("cloth2", Pal.of("0E0406", "2A0A0E", "4A1218", "6E1E26", "9A3440"));
                p.put("leather", LEATHER_BLACK); p.put("leather2", Pal.of("060404", "120C0A", "1E1612", "30241C", "48382C"));
                p.put("fur", Pal.of("0A0A0A", "1E1E20", "34343A", "505058", "74747E")); p.put("trim", GOLD);
                p.put("feather", Pal.of("0A0204", "2A0608", "5A0E14", "8A1C24", "B8404A"));
            }
            case "mage_wool" -> {
                p.put("cloth", Pal.of("3A342A", "8A8070", "C8C0AE", "E8E2D4", "FFFCF4"));
                p.put("cloth2", Pal.of("1E0C34", "3E1E6A", "5E34A0", "8A5ACC", "B48CEC"));
                p.put("leather", Pal.of("1E0C34", "3E1E6A", "5E34A0", "8A5ACC", "B48CEC")); p.put("trim", BRASS);
            }
            case "mage" -> {
                p.put("cloth", Pal.of("1A0A2E", "3A1A64", "5A2E96", "8250C4", "AC80E4"));
                p.put("cloth2", Pal.of("0C0A28", "1C1A54", "2E2C82", "4A48AE", "7472D4"));
                p.put("leather", Pal.of("0C0A28", "1C1A54", "2E2C82", "4A48AE", "7472D4")); p.put("trim", GOLD);
                p.put("gem", Pal.of("5A1004", "A02808", "F0501A", "FFB060", "FFF0C8"));
            }
            default -> {
                p.put("cloth", Pal.of("060A24", "10184E", "1E2C7E", "3448AE", "5C70D4"));
                p.put("cloth2", Pal.of("0E0620", "24104A", "3A1C72", "58349C", "8058C4"));
                p.put("leather", Pal.of("0E0620", "24104A", "3A1C72", "58349C", "8058C4")); p.put("trim", GOLD);
                p.put("gem", Pal.of("0A3A6A", "1A78B0", "3AB8F0", "A8E8FF", "F4FEFF"));
            }
        }
        return p;
    }

    // =====================================================================================
    // Роспись развёрток
    // =====================================================================================

    enum Face {TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK}

    static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color((int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    static Color shade(Color a, double k) {
        return new Color(cl(a.getRed() * k), cl(a.getGreen() * k), cl(a.getBlue() * k));
    }

    static int cl(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    static double hash(double x, double y, int seed) {
        long n = (long) Math.floor(x) * 73856093L ^ (long) Math.floor(y) * 19349663L ^ seed * 83492791L;
        n = (n ^ (n >>> 13)) * 0x5bd1e995L;
        return ((n ^ (n >>> 15)) & 0xFFFF) / 65535.0;
    }

    static final class Painter {
        final BufferedImage img;
        final Map<String, Pal> pal;
        final Cube c;
        final int u, v;
        final boolean astral, damascus;
        final List<String> deco;
        final Random rnd;

        Painter(BufferedImage img, Map<String, Pal> pal, String set, Cube c, int u, int v) {
            this.img = img;
            this.pal = pal;
            this.c = c;
            this.u = u;
            this.v = v;
            this.astral = set.equals("mage_astral");
            this.damascus = set.equals("warrior_damascus");
            this.deco = List.of(c.deco);
            this.rnd = new Random(c.key().hashCode());
        }

        void all() {
            double w = c.w, h = c.h, d = c.d;
            face(Face.TOP, d, 0, w, d);
            face(Face.BOTTOM, d + w, 0, w, d);
            face(Face.RIGHT, 0, d, d, h);
            face(Face.FRONT, d, d, w, h);
            face(Face.LEFT, d + w, d, d, h);
            face(Face.BACK, 2 * d + w, d, w, h);
        }

        void face(Face f, double fx0, double fy0, double fw, double fh) {
            int x0 = (int) Math.floor((u + fx0) * R), x1 = (int) Math.ceil((u + fx0 + fw) * R);
            int y0 = (int) Math.floor((v + fy0) * R), y1 = (int) Math.ceil((v + fy0 + fh) * R);
            for (int py = y0; py < y1; py++)
                for (int px = x0; px < x1; px++) {
                    if (px < 0 || py < 0 || px >= img.getWidth() || py >= img.getHeight()) continue;
                    double fx = (px + 0.5) / R - u - fx0, fy = (py + 0.5) / R - v - fy0;
                    if (fx < 0 || fy < 0 || fx > fw || fy > fh) continue;
                    Color col = pixel(f, fx, fy, fw, fh, px, py);
                    img.setRGB(px, py, col == null ? 0 : col.getRGB());
                }
        }

        boolean vertical(Face f) {
            return f != Face.TOP && f != Face.BOTTOM;
        }

        /** Цвет точки грани или null — прозрачно (вырез капюшона, рваный край). */
        Color pixel(Face f, double fx, double fy, double fw, double fh, int px, int py) {
            Pal p = pal.getOrDefault(c.mat, pal.get("cloth") != null ? pal.get("cloth") : IRON);
            String m = c.mat;
            // Вырез лица у капюшона.
            if (deco.contains("opening") && f == Face.FRONT && fx > 1 && fx < fw - 1 && fy > 1.75) return null;
            // Рваный нижний край накидки.
            if (deco.contains("jagged") && vertical(f) && fy > fh - 0.8) {
                double tooth = Math.abs((fx % 1.0) - 0.5) * 2;
                if (tooth < (fy - (fh - 0.8)) / 0.8) return null;
            }
            Color col = base(m, p, f, fx, fy, fw, fh, px, py);
            // Отделка: полосы, кайма, знаки.
            Pal trim = pal.getOrDefault("trim", GOLD);
            if (deco.contains("trim_b") && vertical(f) && fy > fh - 0.8) col = band(trim, fy - (fh - 0.8), 0.8);
            if (deco.contains("trim_t") && vertical(f) && fy < 0.8) col = band(trim, fy, 0.8);
            if (deco.contains("hem") && vertical(f) && fy > fh - 0.6) col = shade(col, 0.72);
            if (deco.contains("rim")) {
                if (vertical(f)) col = band(trim, fy, fh);
                else if (fx < 0.9 || fy < 0.9 || fx > fw - 0.9 || fy > fh - 0.9) col = mix(col, trim.m(), 0.85);
            }
            if (deco.contains("robe") && (f == Face.FRONT) && Math.abs(fx - fw / 2) < 0.6) col = band(trim, fx - (fw / 2 - 0.6), 1.2);
            if (deco.contains("cross") && f == Face.FRONT) {
                boolean vbar = Math.abs(fx - fw / 2) < 0.45 && fy > 1 && fy < fh - 1.6;
                boolean hbar = Math.abs(fy - fh * 0.32) < 0.45 && fx > 0.7 && fx < fw - 0.7;
                if (vbar || hbar) col = mix(trim.l(), trim.m(), (fx + fy) % 1.0 * 0.4);
            }
            if (deco.contains("ridge") && f == Face.FRONT && Math.abs(fx - fw / 2) < 0.4)
                col = fx < fw / 2 ? mix(col, p.h(), 0.45) : mix(col, p.d(), 0.35);
            if (deco.contains("visor") && f == Face.FRONT) {
                boolean slit = fy > 2.7 && fy < 3.55 && fx > 0.9 && fx < fw - 0.9 && Math.abs(fx - fw / 2) > 0.45;
                boolean holes = fy > 5.2 && fy < 6.6 && fx > fw / 2 + 0.9 && fx < fw - 1.2
                        && ((int) Math.floor(fx * 2.2)) % 2 == 0 && ((int) Math.floor(fy * 2.2)) % 2 == 0;
                if (slit || holes) col = mix(p.o(), Color.BLACK, 0.5);
                else if (fy > 2.45 && fy < 2.7 && fx > 0.9 && fx < fw - 0.9) col = mix(col, p.h(), 0.4);
            }
            if (deco.contains("lace") && f == Face.FRONT) {
                double cx = fw / 2, t = (fy % 1.25) / 1.25;
                double lx = cx - 0.7 + 1.4 * t, rx = cx + 0.7 - 1.4 * t;
                Pal lp = pal.getOrDefault("leather2", LEATHER_DARK);
                if (fy > 0.6 && fy < fh - 0.6 && (Math.abs(fx - lx) < 0.16 || Math.abs(fx - rx) < 0.16)) col = mix(lp.l(), lp.h(), 0.3);
                if (fy > 0.6 && fy < fh - 0.6 && Math.abs(Math.abs(fx - cx) - 0.75) < 0.14 && (fy % 1.25) < 0.3) col = trim.l();
            }
            if (deco.contains("buckle") && f == Face.FRONT) {
                double bx = Math.abs(fx - fw / 2), by = Math.abs(fy - fh / 2);
                double s = Math.min(fh * 0.42, 0.9);
                if (Math.max(bx, by) < s && Math.max(bx, by) > s - 0.3) col = mix(trim.l(), trim.h(), by / s * 0.5);
            }
            if (deco.contains("stitch") && vertical(f)) {
                double inset = 0.5;
                boolean onLine = (Math.abs(fy - inset) < 0.09 || Math.abs(fy - (fh - inset)) < 0.09) && fx > inset && fx < fw - inset
                        || (Math.abs(fx - inset) < 0.09 || Math.abs(fx - (fw - inset)) < 0.09) && fy > inset && fy < fh - inset;
                if (onLine && ((int) Math.floor((fx + fy) * 2.5)) % 2 == 0) col = mix(col, p.h(), 0.45);
            }
            if (deco.contains("rivets") && vertical(f)) {
                double ry = fy < fh / 2 ? 0.55 : fh - 0.55;
                double rxs = (fx - 1.0) % 2.0;
                if (fh > 1.6 && fw > 1.6 && Math.abs(fy - ry) < 0.22 && fx > 0.7 && fx < fw - 0.7 && rxs >= -0.22 && Math.abs(rxs) < 0.22)
                    col = fy - ry < 0 ? p.h() : p.d();
            }
            if (astral && (m.equals("cloth") || m.equals("cloth2"))) col = stars(col, fx, fy);
            // Объём: светлая фаска сверху-слева, тень снизу-справа, тёмный контур граней.
            if (deco.contains("bevel")) {
                if (fy < 0.55 || fx < 0.45) col = mix(col, p.h(), 0.28);
                else if (fy > fh - 0.5 || fx > fw - 0.45) col = mix(col, p.d(), 0.3);
            }
            if (fx < 0.25 || fy < 0.25 || fx > fw - 0.25 || fy > fh - 0.25) col = mix(col, p.o(), 0.5);
            double light = switch (f) {
                case TOP -> 1.1;
                case BOTTOM -> 0.75;
                case BACK -> 0.9;
                case RIGHT, LEFT -> 0.95;
                default -> 1.0;
            };
            return shade(col, light);
        }

        Color band(Pal t, double pos, double size) {
            double k = pos / Math.max(size, 0.01);
            return k < 0.25 ? mix(t.h(), t.l(), k * 4) : k > 0.8 ? mix(t.m(), t.d(), (k - 0.8) * 5) : mix(t.l(), t.m(), (k - 0.25) / 0.55);
        }

        Color stars(Color col, double fx, double fy) {
            double cx = Math.floor(fx / 1.6), cy = Math.floor(fy / 1.6);
            if (hash(cx, cy, 7) > 0.32) return col;
            double sx = cx * 1.6 + 0.4 + hash(cx, cy, 11) * 0.8, sy = cy * 1.6 + 0.4 + hash(cx, cy, 13) * 0.8;
            double dx = Math.abs(fx - sx), dy = Math.abs(fy - sy);
            Pal st = pal.get("star");
            if (dx < 0.13 && dy < 0.42 || dy < 0.13 && dx < 0.42) return mix(st.l(), st.h(), 1 - Math.max(dx, dy) / 0.42);
            return col;
        }

        Color base(String m, Pal p, Face f, double fx, double fy, double fw, double fh, int px, int py) {
            double n = rnd.nextDouble() - 0.5;
            switch (m) {
                case "metal", "metal2", "trim", "star" -> {
                    double t = vertical(f) ? fy / Math.max(fh, 0.01) : 0.3;
                    Color col = t < 0.4 ? mix(p.l(), p.m(), t / 0.4) : mix(p.m(), p.d(), (t - 0.4) / 0.6 * 0.7);
                    double k = 1 + n * 0.05 + (hash(0, py, 3) - 0.5) * 0.06;
                    if (damascus && m.equals("metal")) {
                        double w = Math.sin(py * 0.32 + 2.6 * Math.sin(px * 0.11) + 1.2 * Math.sin(py * 0.05 + px * 0.04));
                        if (w > 0.72) k *= 1.3;
                        else if (w < -0.8) k *= 0.78;
                    }
                    return shade(col, k);
                }
                case "cloth", "cloth2" -> {
                    double fold = deco.contains("folds") && vertical(f) ? Math.sin(fx * 2.1 + hash(0, 0, (int) (fw * 10)) * 6) * 0.09 : 0;
                    double foldH = deco.contains("folds_h") ? Math.sin(fy * 3.2) * 0.09 : 0;
                    double weave = (((px + py) & 1) == 0 ? 0.025 : -0.025) + (((px - py) % 6 + 6) % 6 == 0 ? -0.04 : 0);
                    Color col = vertical(f) ? mix(p.m(), p.d(), fy / Math.max(fh, 0.01) * 0.35) : mix(p.m(), p.l(), 0.25);
                    return shade(col, 1 + fold + foldH + weave + n * 0.03);
                }
                case "leather", "leather2" -> {
                    double blot = (hash(px / 3.0, py / 3.0, 5) - 0.5) * 0.14;
                    Color col = vertical(f) ? mix(p.l(), p.m(), Math.min(1, fy / Math.max(fh, 0.01) * 1.4)) : p.m();
                    return shade(col, 1 + blot + n * 0.05);
                }
                case "shaft" -> {
                    return shade(mix(p.l(), p.d(), fx / Math.max(fw, 0.01)), 1 + n * 0.05);
                }
                case "fur" -> {
                    double strand = hash(px, Math.floor(py / 3.0), 9);
                    return shade(mix(p.m(), p.l(), strand), 1 + n * 0.1);
                }
                case "plume" -> {
                    double strand = Math.sin(px * 1.3 + Math.sin(py * 0.2) * 2) * 0.12;
                    Color col = mix(p.l(), p.d(), fy / Math.max(fh, 0.01) * 0.8);
                    return shade(col, 1 + strand + n * 0.05);
                }
                case "feather" -> {
                    Color col = mix(p.l(), p.m(), Math.abs(fx / Math.max(fw, 0.01) - 0.5) * 2);
                    if (Math.abs(fx - fw / 2) < 0.08) col = p.d();
                    return shade(col, 1 + n * 0.04);
                }
                case "gem" -> {
                    double dx = fx / Math.max(fw, 0.01) - 0.4, dy = fy / Math.max(fh, 0.01) - 0.4;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    Color col = dist < 0.25 ? mix(p.h(), p.l(), dist * 4) : mix(p.l(), p.d(), (dist - 0.25) * 2);
                    return col;
                }
                default -> {
                    return p.m();
                }
            }
        }
    }

    // =====================================================================================
    // Иконка колпака для инвентаря (32×32, пиксельная)
    // =====================================================================================

    static BufferedImage hatIcon(String set, int grade) {
        Map<String, Pal> pal = palettes(set);
        Pal cloth = pal.get("cloth"), band = pal.get("cloth2"), trim = pal.get("trim"), star = pal.get("star");
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        // Конус с загнутым назад кончиком: светлая левая сторона, тёмная правая.
        int[] cx = {8, 23, 22, 21, 22, 24, 27, 28, 26, 22, 19, 16, 14, 11};
        int[] cy = {21, 21, 15, 11, 8, 5, 2, 3, 4, 6, 8, 11, 14, 18};
        g.setColor(cloth.m());
        g.fillPolygon(cx, cy, cx.length);
        // Загнутый кончик — толстой ломаной, чтобы не рвался на пиксели.
        g.setStroke(new java.awt.BasicStroke(2.6f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        g.drawPolyline(new int[]{20, 22, 25, 28}, new int[]{10, 6, 4, 3}, 4);
        g.setStroke(new java.awt.BasicStroke(1f));
        g.setColor(cloth.l());
        g.fillPolygon(new int[]{8, 13, 15, 17, 16, 14, 11}, new int[]{21, 21, 14, 10, 11, 14, 18}, 7);
        g.setColor(cloth.d());
        g.fillPolygon(new int[]{19, 23, 22, 21, 20}, new int[]{21, 21, 15, 11, 12}, 5);
        // Поля шляпы и лента.
        g.setColor(cloth.d());
        g.fillRect(3, 23, 26, 3);
        g.setColor(cloth.m());
        g.fillRect(4, 22, 24, 2);
        g.setColor(trim.l());
        g.fillRect(3, 25, 26, 1);
        g.setColor(band.m());
        g.fillRect(8, 19, 16, 3);
        g.setColor(band.l());
        g.fillRect(8, 19, 16, 1);
        if (grade >= 2) {
            g.setColor(trim.l());
            g.fillRect(14, 19, 4, 3);
            g.setColor(pal.get("gem").l());
            g.fillRect(15, 20, 2, 1);
        }
        if (grade >= 3) {
            g.setColor(star.l());
            for (int[] s : new int[][]{{11, 17}, {17, 14}, {14, 10}, {19, 17}}) {
                g.fillRect(s[0], s[1], 1, 1);
                g.setColor(star.h());
                g.fillRect(s[0] - 1, s[1], 1, 1);
                g.fillRect(s[0] + 1, s[1], 1, 1);
                g.setColor(star.l());
            }
            g.setColor(star.h());
            g.fillRect(26, 2, 2, 2);
        }
        g.dispose();
        // Тёмный контур по краю силуэта.
        BufferedImage out = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                int argb = img.getRGB(x, y);
                if ((argb >>> 24) == 0) continue;
                boolean edge = false;
                for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + dd[0], ny = y + dd[1];
                    if (nx < 0 || ny < 0 || nx >= 32 || ny >= 32 || (img.getRGB(nx, ny) >>> 24) == 0) edge = true;
                }
                out.setRGB(x, y, edge ? cloth.o().getRGB() : argb);
            }
        return out;
    }
}