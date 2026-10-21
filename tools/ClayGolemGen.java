import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Глиняные големы Глиняных копей: модели (кубоиды по частям тела), раскладка развёрток по текстуре и сами
 * текстуры в 4 раза подробнее развёртки, плюс светящийся слой (глаза, ядро колосса).
 *
 * Пишет textures/entity/clay/<модель>.png и <модель>_glow.png и client/clay/ClayModels.java (LayerDefinition
 * каждой модели). Координаты — как у моделей Minecraft: y вниз, земля на y = 24, лицо смотрит в −z.
 * Запуск из корня проекта: java tools/ClayGolemGen.java .
 */
public class ClayGolemGen {
    /** Во сколько раз текстура подробнее развёртки (1 — пиксель на единицу модели, как у ванильных мобов). */
    static final int R = 1;
    /** Во сколько раз шум и узоры мельче, чем на эталонной текстуре 4×: узоры заданы в её пикселях. */
    static final double K = 4.0 / R;

    record Box(double x, double y, double z, int w, int h, int d, double inflate, String mat, String feature) {}

    static final class Part {
        final String name, parent;
        final double px, py, pz, rx, ry, rz;
        final List<Box> boxes = new ArrayList<>();
        final List<int[]> uv = new ArrayList<>();

        Part(String name, String parent, double px, double py, double pz, double rx, double ry, double rz) {
            this.name = name;
            this.parent = parent;
            this.px = px;
            this.py = py;
            this.pz = pz;
            this.rx = rx;
            this.ry = ry;
            this.rz = rz;
        }

        Part box(double x, double y, double z, int w, int h, int d, String mat) {
            return box(x, y, z, w, h, d, 0, mat, "");
        }

        Part box(double x, double y, double z, int w, int h, int d, double inflate, String mat, String feature) {
            boxes.add(new Box(x, y, z, w, h, d, inflate, mat, feature));
            return this;
        }
    }

    static final class Model {
        final String name;
        final List<Part> parts = new ArrayList<>();
        int texW = 128, texH;
        /** Цвет глаз и светящихся трещин. */
        final int[] glow;
        /** Папка текстур в textures/entity: clay — Глиняные копи, frost — Ледяная цитадель. */
        final String dir;

        Model(String name, int[] glow) {
            this(name, glow, "clay");
        }

        Model(String name, int[] glow, String dir) {
            this.dir = dir;
            this.name = name;
            this.glow = glow;
        }

        Part part(String name, String parent, double px, double py, double pz) {
            return part(name, parent, px, py, pz, 0, 0, 0);
        }

        Part part(String name, String parent, double px, double py, double pz, double rx, double ry, double rz) {
            Part p = new Part(name, parent, px, py, pz, rx, ry, rz);
            parts.add(p);
            return p;
        }

        /** Полками раскладывает развёртки всех кубоидов (высокие первыми) по ширине texW. */
        void pack() {
            List<Object[]> all = new ArrayList<>();
            for (Part p : parts) for (int i = 0; i < p.boxes.size(); i++) {
                p.uv.add(new int[2]);
                all.add(new Object[]{p, i});
            }
            all.sort((a, b) -> {
                Box ba = ((Part) a[0]).boxes.get((int) a[1]), bb = ((Part) b[0]).boxes.get((int) b[1]);
                return Integer.compare(bb.d + bb.h, ba.d + ba.h);
            });
            int x = 0, y = 0, shelf = 0;
            for (Object[] o : all) {
                Part p = (Part) o[0];
                Box b = p.boxes.get((int) o[1]);
                int w = 2 * (b.w + b.d), h = b.d + b.h;
                if (x + w > texW) {
                    x = 0;
                    y += shelf;
                    shelf = 0;
                }
                p.uv.get((int) o[1])[0] = x;
                p.uv.get((int) o[1])[1] = y;
                x += w;
                shelf = Math.max(shelf, h);
            }
            int used = y + shelf;
            texH = 32;
            while (texH < used) texH *= 2;
        }
    }

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : ".");
        Path textures = root.resolve("src/main/resources/assets/heroicpaths/textures/entity");
        Files.createDirectories(textures);
        List<Model> models = List.of(colossus(), golem(), thrower(), brute(), frostLich(), iceGolem(), frozenArcher(), snowBrute(),
                undeadZombie(), ghoul(), banshee(), abomination());
        StringBuilder java = new StringBuilder();
        java.append("package com.heroicpaths.client.clay;\n\n")
                .append("import net.minecraft.client.model.geom.PartPose;\n")
                .append("import net.minecraft.client.model.geom.builders.CubeDeformation;\n")
                .append("import net.minecraft.client.model.geom.builders.CubeListBuilder;\n")
                .append("import net.minecraft.client.model.geom.builders.LayerDefinition;\n")
                .append("import net.minecraft.client.model.geom.builders.MeshDefinition;\n")
                .append("import net.minecraft.client.model.geom.builders.PartDefinition;\n\n")
                .append("import java.util.HashMap;\n")
                .append("import java.util.Map;\n\n")
                .append("// Сгенерировано tools/ClayGolemGen.java — не править вручную.\n")
                .append("public final class ClayModels {\n")
                .append("    private ClayModels() {}\n");
        for (Model m : models) {
            m.pack();
            BufferedImage[] img = paint(m);
            Path tex = textures.resolve(m.dir);
            Files.createDirectories(tex);
            ImageIO.write(img[0], "png", tex.resolve(m.name + ".png").toFile());
            ImageIO.write(img[1], "png", tex.resolve(m.name + "_glow.png").toFile());
            java.append("\n    public static LayerDefinition ").append(camel(m.name)).append("() {\n")
                    .append("        MeshDefinition mesh = new MeshDefinition();\n")
                    .append("        Map<String, PartDefinition> parts = new HashMap<>();\n")
                    .append("        parts.put(\"root\", mesh.getRoot());\n");
            for (Part p : m.parts) {
                StringBuilder cubes = new StringBuilder("CubeListBuilder.create()");
                for (int i = 0; i < p.boxes.size(); i++) {
                    Box b = p.boxes.get(i);
                    cubes.append(".texOffs(").append(p.uv.get(i)[0]).append(", ").append(p.uv.get(i)[1]).append(")")
                            .append(".addBox(").append(f(b.x)).append(", ").append(f(b.y)).append(", ").append(f(b.z)).append(", ")
                            .append(b.w).append(", ").append(b.h).append(", ").append(b.d)
                            .append(", new CubeDeformation(").append(f(b.inflate)).append("))");
                }
                java.append("        parts.put(\"").append(p.name).append("\", parts.get(\"").append(p.parent).append("\").addOrReplaceChild(\"")
                        .append(p.name).append("\", ").append(cubes).append(",\n                PartPose.offsetAndRotation(")
                        .append(f(p.px)).append(", ").append(f(p.py)).append(", ").append(f(p.pz)).append(", ")
                        .append(f(p.rx)).append(", ").append(f(p.ry)).append(", ").append(f(p.rz)).append(")));\n");
            }
            java.append("        return LayerDefinition.create(mesh, ").append(m.texW).append(", ").append(m.texH).append(");\n    }\n");
            System.out.println(m.name + ": " + m.parts.size() + " parts, texture " + m.texW + "x" + m.texH);
        }
        java.append("}\n");
        Path out = root.resolve("src/main/java/com/heroicpaths/client/clay/ClayModels.java");
        Files.createDirectories(out.getParent());
        Files.writeString(out, java.toString(), StandardCharsets.UTF_8);
    }

    // =====================================================================================
    // Модели
    // =====================================================================================

    static final int[] AMBER = {255, 176, 64}, CYAN = {120, 230, 255}, RED = {255, 80, 50};

    /**
     * Глиняный колосс: шесть рук (могучая верхняя пара, средняя и нижняя), обожжённые латы — нагрудник, наплечники,
     * пояс и шлем. В фазах отваливаются нижние руки с нагрудником и наплечниками, потом средние руки со шлемом и поясом.
     */
    static Model colossus() {
        Model m = new Model("clay_colossus", AMBER);
        m.part("body", "root", 0, 8, 0, 0.12, 0, 0)
                .box(-8, -16, -5, 16, 14, 10, 0, "clay", "core")
                .box(-6, -2, -4, 12, 5, 8, 0, "clay_dark", "")
                .box(-5, -14, 4.5, 10, 9, 2, 0, "clay_dark", "")
                // Горб из глиняных глыб и янтарные кристаллы вдоль хребта.
                .box(-7, -17, 2, 5, 4, 4, 0, "clay_dark", "")
                .box(2, -16, 2.5, 5, 3, 4, 0, "clay_dark", "")
                .box(-1, -21, 3, 2, 5, 2, 0, "crystal", "")
                .box(-4, -19, 4, 2, 3, 2, 0, "crystal", "")
                .box(2.5, -18, 4.5, 2, 3, 2, 0, "crystal", "")
                // Рёбра на боках.
                .box(-8.6, -12, -3, 1, 6, 6, 0, "clay_dark", "seams")
                .box(7.6, -12, -3, 1, 6, 6, 0, "clay_dark", "seams");
        m.part("chest_plate", "body", 0, 0, 0)
                .box(-8, -16, -5, 16, 9, 10, 0.8, "fired", "plate")
                .box(-3, -18, -5.5, 6, 3, 2, 0, "fired", "plate")
                .box(-5, -17, -6.6, 10, 2, 2, 0, "fired", "plate")
                .box(-2, -14, -6.6, 4, 4, 1, 0, "copper", "")
                .box(-7, -9, -6.2, 14, 1, 1, 0, "copper", "");
        m.part("belt_plate", "body", 0, 0, 0)
                .box(-6, -2, -4, 12, 4, 8, 0.7, "fired", "plate")
                .box(-4, 1, -5.2, 8, 5, 1, 0, "fired", "plate")
                .box(-7, 2, -5, 3, 5, 1, 0, "fired", "plate")
                .box(4, 2, -5, 3, 5, 1, 0, "fired", "plate")
                .box(-1.5, -1, -5.6, 3, 3, 1, 0, "copper", "");
        m.part("head", "body", 0, -16, -2)
                .box(-4, -7, -4, 8, 8, 8, 0, "clay", "face")
                .box(-3, -1, -4.6, 6, 2, 1, 0, "clay_dark", "")
                .box(-4.5, -5, -4.8, 9, 1, 2, 0, "clay_dark", "")
                .box(-6, -8, -1, 2, 4, 2, 0, "clay_dark", "")
                .box(4, -8, -1, 2, 4, 2, 0, "clay_dark", "")
                .box(-2, 1, -4.2, 4, 1, 3, 0, "clay_dark", "");
        m.part("helmet", "head", 0, 0, 0)
                .box(-4, -7, -4, 8, 4, 8, 0.8, "fired", "plate")
                .box(-1, -10, -3, 2, 3, 8, 0, "fired", "plate")
                .box(-5, -4, -4.4, 1, 4, 6, 0, "fired", "")
                .box(4, -4, -4.4, 1, 4, 6, 0, "fired", "")
                .box(-0.5, -4, -5.2, 1, 3, 1, 0, "copper", "");
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "right" : "left";
            double fx = s < 0 ? -6 : -2; // левый край кулака: левая рука — зеркало правой
            m.part("arm_" + side, "body", 10 * s, -13, 0)
                    .box(s < 0 ? -5 : -1, -2, -3, 6, 18, 6, 0, "clay", "seams")
                    .box(fx, 16, -4, 8, 6, 8, 0, "clay_dark", "fist")
                    .box(fx + 0.5, 9, -3.5, 7, 4, 7, 0, "copper", "band")
                    .box(fx, 22, -4, 2, 2, 2, 0, "clay_dark", "")
                    .box(fx + 3, 22, -4, 2, 2, 2, 0, "clay_dark", "")
                    .box(fx + 6, 22, -4, 2, 2, 2, 0, "clay_dark", "")
                    // Светящиеся наросты на внешней стороне руки и на плече (плечевые видны, когда спадут наплечники).
                    .box(s < 0 ? -6 : 4, 1, -1.5, 2, 3, 2, 0, "crystal", "")
                    .box(s < 0 ? -6.5 : 5.5, 5, 0, 1, 2, 1, 0, "crystal", "")
                    .box(s < 0 ? -6 : 4, 13, -1, 2, 2, 2, 0, "crystal", "")
                    .box(s < 0 ? -3 : 0, -4, -1, 2, 2, 2, 0, "crystal", "")
                    .box(s < 0 ? -4.5 : 2.5, -3.5, 1, 2, 2, 1, 0, "crystal", "");
            m.part("pauldron_" + side, "arm_" + side, 0, 0, 0)
                    .box(s < 0 ? -6 : -2, -4, -4, 8, 6, 8, 0.4, "fired", "plate")
                    .box(s < 0 ? -4 : 0, -6, -2, 4, 2, 4, 0, "fired", "plate")
                    .box(s < 0 ? -3 : 1, -9, -1, 2, 3, 2, 0, "copper", "")
                    .box(s < 0 ? -6.6 : 5.6, -3, -3, 1, 4, 6, 0, "fired", "");
            m.part("arm_mid_" + side, "body", 8 * s, -8, 1, 0, 0, -0.35 * s)
                    .box(-2, -1, -2, 4, 13, 4, "clay")
                    .box(-2.5, 12, -2.5, 5, 4, 5, 0, "clay_dark", "fist")
                    .box(-2.5, 5, -2.5, 5, 2, 5, 0, "copper", "band")
                    .box(-2, 16, -2.5, 1, 2, 1, 0, "clay_dark", "")
                    .box(1, 16, -2.5, 1, 2, 1, 0, "clay_dark", "");
            m.part("arm_lower_" + side, "body", 7 * s, -3, 1, 0, 0, -0.6 * s)
                    .box(-1.5, -1, -1.5, 3, 10, 3, "clay")
                    .box(-2, 9, -2, 4, 3, 4, 0, "clay_dark", "fist")
                    .box(-1, 12, -2, 1, 2, 1, 0, "clay_dark", "")
                    .box(0.5, 12, -2, 1, 2, 1, 0, "clay_dark", "");
            m.part("leg_" + side, "root", 4 * s, 10, 0)
                    .box(-3.5, 0, -3.5, 7, 14, 7, 0, "clay", "seams")
                    .box(-4, 11, -5, 8, 3, 9, 0, "clay_dark", "")
                    .box(-3, 3, -4.6, 6, 4, 2, 0, "fired", "plate")
                    .box(-4, 12, -6.5, 2, 2, 2, 0, "clay_dark", "")
                    .box(-1, 12, -6.5, 2, 2, 2, 0, "clay_dark", "")
                    .box(2, 12, -6.5, 2, 2, 2, 0, "clay_dark", "")
                    .box(-4, -1, -4, 8, 2, 8, 0, "clay_dark", "")
                    .box(s < 0 ? -4.5 : 2.5, 6, -1, 2, 3, 2, 0, "crystal", "")
                    .box(s < 0 ? -4.2 : 3.2, 9, 1, 1, 2, 1, 0, "crystal", "");
        }
        return m;
    }

    /** Маленький голем ближнего боя: коренастый, по пояс человеку, с рожками, поясом и янтарём в груди. */
    static Model golem() {
        Model m = new Model("clay_golem", AMBER);
        m.part("body", "root", 0, 12, 0)
                .box(-4, -8, -3, 8, 8, 6, 0, "clay", "seams")
                .box(-4.5, -2, -3.5, 9, 2, 7, 0, "leather", "")
                .box(2, -2.5, -4.3, 2, 2, 1, 0, "leather", "")
                .box(-1, -6, -3.6, 2, 2, 1, 0, "crystal", "")
                .box(-3, -9, 1, 6, 2, 3, 0, "clay_dark", "")
                // Светящиеся наросты на спине.
                .box(-2, -11, 1.5, 2, 3, 2, 0, "crystal", "")
                .box(1, -10, 2, 1, 2, 1, 0, "crystal", "")
                .box(-3.5, -6, 3, 1, 2, 1, 0, "crystal", "")
                .box(1.5, -5, 3, 2, 2, 1, 0, "crystal", "");
        m.part("head", "body", 0, -8, -1)
                .box(-3, -5, -3, 6, 5, 6, 0, "clay", "face")
                .box(-3.5, -4, -3.6, 7, 1, 1, 0, "clay_dark", "")
                .box(-4, -7, -1, 2, 2, 2, 0, "clay_dark", "")
                .box(2, -7, -1, 2, 2, 2, 0, "clay_dark", "")
                .box(-2, 0, -3.4, 4, 1, 3, 0, "clay_dark", "");
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "right" : "left";
            m.part("arm_" + side, "root", 5.5 * s, 5, 0)
                    .box(-2, -1, -2, 4, 9, 4, "clay")
                    .box(-2.5, 7, -2.5, 5, 4, 5, 0, "clay_dark", "fist")
                    .box(-2.5, -2, -2.5, 5, 3, 5, 0, "clay_dark", "")
                    .box(-2, 11, -2.5, 1, 1, 1, 0, "clay_dark", "")
                    .box(1, 11, -2.5, 1, 1, 1, 0, "clay_dark", "")
                    .box(-1, -4, -0.5, 2, 2, 2, 0, "crystal", "")
                    .box(s < 0 ? -2.6 : 1.6, 3, 0, 1, 2, 1, 0, "crystal", "");
            m.part("leg_" + side, "root", 2 * s, 12, 0)
                    .box(-2, 0, -2, 4, 12, 4, "clay")
                    .box(-2.5, 10, -3, 5, 2, 5, 0, "clay_dark", "")
                    .box(-2.3, 4, -2.3, 4, 2, 4, 0, "leather", "band");
        }
        return m;
    }

    /** Голем-метатель: светлая глина, капюшон, сума с глиняными ядрами и праща в бросковой руке. */
    static Model thrower() {
        Model m = new Model("clay_thrower", CYAN);
        m.part("body", "root", 0, 12, 0)
                .box(-3.5, -9, -2.5, 7, 9, 5, 0, "pale", "seams")
                .box(3, -7, -3, 3, 5, 6, 0, "leather", "")
                .box(3.5, -8, -2, 1, 1, 1, 0, "clayball", "")
                .box(4.5, -8, 0, 1, 1, 1, 0, "clayball", "")
                .box(3.5, -8, 1.5, 1, 1, 1, 0, "clayball", "")
                .box(-3.8, -9, -2.8, 2, 9, 1, 0, "leather", "")
                .box(-3.8, -9, 1.8, 2, 9, 1, 0, "leather", "")
                .box(-4, -1.5, -3, 8, 2, 6, 0, "leather", "")
                .box(-3, -10, 0.5, 6, 3, 2, 0, "leather", "")
                .box(-1.5, -12, 1, 2, 3, 2, 0, "crystal", "")
                .box(1, -11, 1.5, 1, 2, 1, 0, "crystal", "")
                .box(-3, -5, 2.6, 1, 2, 1, 0, "crystal", "");
        m.part("head", "body", 0, -9, -1)
                .box(-3, -6, -3, 6, 6, 6, 0, "pale", "face")
                .box(-3.5, -7, -3.5, 7, 2, 7, 0, "leather", "")
                .box(-3.5, -5, -4, 7, 1, 1, 0, "leather", "")
                .box(-3.6, -5, -3, 1, 4, 6, 0, "leather", "")
                .box(2.6, -5, -3, 1, 4, 6, 0, "leather", "")
                .box(-2, -8, -1, 4, 1, 4, 0, "leather", "")
                // Светящиеся наросты пробиваются сквозь капюшон.
                .box(-1, -10, 0, 1, 2, 1, 0, "crystal", "")
                .box(0.5, -9.5, 1.5, 1, 2, 1, 0, "crystal", "");
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "right" : "left";
            Part arm = m.part("arm_" + side, "root", 5 * s, 4, 0)
                    .box(-1.5, -1, -1.5, 3, s < 0 ? 12 : 10, 3, "pale")
                    .box(-2, s < 0 ? 10 : 8, -2, 4, 3, 4, 0, "pale_dark", "fist")
                    .box(-2, s < 0 ? 6 : 4, -2, 4, 3, 4, 0, "leather", "band")
                    .box(-2, -2, -2, 4, 2, 4, 0, "pale_dark", "");
            if (s < 0) arm.box(-0.5, 13, -0.5, 1, 3, 1, 0, "leather", "")
                    .box(-1.5, 15.5, -1.5, 3, 2, 3, 0, "clayball", "");
            m.part("leg_" + side, "root", 2 * s, 12, 0)
                    .box(-1.5, 0, -1.5, 3, 12, 3, "pale")
                    .box(-2, 3, -2, 4, 3, 4, 0, "leather", "band")
                    .box(-2, 10, -2.5, 4, 2, 4, 0, "pale_dark", "");
        }
        return m;
    }

    /** Голем-танк: массивный, из тёмной обожжённой глины, под толстыми плитами, с шипами, бивнями и валунами на спине. */
    static Model brute() {
        Model m = new Model("clay_brute", RED);
        m.part("body", "root", 0, 12, 0, 0.15, 0, 0)
                .box(-7, -11, -4.5, 14, 11, 9, 0, "dark", "seams")
                .box(-7, -11, -4.5, 14, 7, 9, 0.6, "fired", "plate")
                .box(-7.5, -1.5, -5, 15, 2, 10, 0, "leather", "")
                .box(-1.5, -2, -5.8, 3, 3, 1, 0, "copper", "")
                .box(-6, -14, 2, 5, 4, 4, 0, "clay_dark", "")
                .box(1, -13, 2.5, 5, 3, 4, 0, "clay_dark", "")
                .box(-2, -16, 3, 2, 3, 2, 0, "fired", "")
                .box(2, -15, 3.5, 2, 2, 2, 0, "fired", "")
                // Светящиеся наросты между валунами и на боках.
                .box(-1, -18, 2, 2, 4, 2, 0, "crystal", "")
                .box(-5, -15, 4, 1, 2, 1, 0, "crystal", "")
                .box(4, -14, 4, 2, 3, 2, 0, "crystal", "")
                .box(-7.6, -5, -1, 1, 2, 2, 0, "crystal", "")
                .box(6.6, -6, 0, 1, 3, 2, 0, "crystal", "");
        m.part("head", "body", 0, -10, -3)
                .box(-3, -4, -3.5, 6, 5, 6, 0, "dark", "face")
                .box(-3.5, -3, -4.2, 7, 1, 1, 0, "fired", "")
                .box(-2.5, 0, -4, 5, 2, 2, 0, "dark", "")
                .box(-2.5, -1, -4.5, 1, 2, 1, 0, "bone", "")
                .box(1.5, -1, -4.5, 1, 2, 1, 0, "bone", "");
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "right" : "left";
            double x0 = s < 0 ? -4.5 : -1.5;
            m.part("arm_" + side, "root", 8.5 * s, 3, 0)
                    .box(s < 0 ? -3.5 : -1.5, -2, -2.5, 5, 13, 5, "dark")
                    .box(x0, 10, -3.5, 6, 6, 7, 0, "fired", "fist")
                    .box(x0, -4, -3.5, 6, 5, 7, 0.3, "fired", "plate")
                    .box(x0 + 1, -7, -2, 2, 3, 2, 0, "fired", "")
                    .box(x0 + 3, -6, 0.5, 2, 2, 2, 0, "fired", "")
                    .box(x0 + 0.5, 12, -4.5, 1, 1, 1, 0, "bone", "")
                    .box(x0 + 2.5, 12, -4.5, 1, 1, 1, 0, "bone", "")
                    .box(x0 + 4.5, 12, -4.5, 1, 1, 1, 0, "bone", "")
                    .box(x0, 5, -3, 6, 2, 6, 0, "copper", "band")
                    .box(s < 0 ? x0 - 0.6 : x0 + 5.6, 1, -1, 1, 3, 2, 0, "crystal", "");
            m.part("leg_" + side, "root", 3.5 * s, 12, 0)
                    .box(-2.5, 0, -2.5, 5, 12, 5, "dark")
                    .box(-3, 2, -3.6, 6, 4, 2, 0, "fired", "plate")
                    .box(-3, 10, -4, 6, 2, 6, 0, "clay_dark", "");
        }
        return m;
    }

    // ---------------------------------------------------------------- Ледяная цитадель

    static final int[] ICE_BLUE = {110, 220, 255}, LICH_RED = {255, 50, 40};

    /**
     * Лич в стиле Аинза Оал Гоуна из «Повелителя» (Overlord): череп с красными огоньками глаз, чёрная мантия
     * с золотой каймой и фиолетовой подкладкой, высокий золотой воротник-веер за головой, золотые наплечники
     * с самоцветами, красная сфера в рёбрах, кольца на костяных пальцах и золотой посох со змеями и камнями.
     * Вокруг кружат тёмно-красные сферы. Во второй фазе спадают нагрудная накидка, наплечники и воротник —
     * открываются рёбра с красной сферой; в третьей падает золотой пояс.
     */
    static Model frostLich() {
        Model m = new Model("frost_lich", LICH_RED, "frost");
        m.part("body", "root", 0, 2, 0, 0.12, 0, 0)
                .box(-5, -12, -3, 10, 12, 6, 0, "frost_bone", "ribs")
                .box(-1, -12, 2.6, 2, 12, 1, 0, "frost_bone", "")
                .box(-2, -9, -2, 4, 4, 4, 0, "crystal", "");
        m.part("chest_armor", "body", 0, 0, 0)
                .box(-5.5, -12.5, -3.5, 11, 9, 7, 0.4, "robe_black", "")
                .box(-5.8, -12.8, -3.9, 2, 9, 1, 0, "gold", "")
                .box(3.8, -12.8, -3.9, 2, 9, 1, 0, "gold", "")
                .box(-1, -11, -4.3, 2, 2, 1, 0, "crystal", "");
        m.part("belt", "body", 0, 0, 0)
                .box(-5.5, -1.5, -3.5, 11, 2, 7, 0.1, "gold", "band")
                .box(-1.5, -2, -4.2, 3, 3, 1, 0, "crystal", "");
        m.part("robe", "body", 0, 0, 0)
                .box(-5, 0, -3.5, 10, 6, 7, 0, "robe_black", "")
                .box(-6, 6, -4, 12, 6, 8, 0, "robe_black", "")
                .box(-7, 12, -4.5, 14, 6, 9, 0, "robe_black", "")
                .box(-1.5, 0, -4.2, 3, 18, 1, 0, "robe_purple", "")
                .box(-2.5, 0, -4.4, 1, 18, 1, 0, "gold", "")
                .box(1.5, 0, -4.4, 1, 18, 1, 0, "gold", "")
                .box(-7.5, 17, -5, 15, 1, 10, 0, "gold", "")
                .box(-6, -12, 3, 12, 30, 1, 0, "robe_purple", "")
                .box(-6.5, -12.5, 3.5, 13, 31, 1, 0, "robe_black", "");
        m.part("head", "body", 0, -12, -1.5)
                .box(-4, -8, -4.5, 8, 8, 8, 0, "frost_bone", "face")
                .box(-3, -1, -4.8, 6, 2, 5, 0, "frost_bone", "")
                .box(-3.5, 0, -2.5, 7, 2, 4, 0, "robe_black", "");
        m.part("crown", "head", 0, 0, 0)
                .box(-6, -6, 2, 12, 9, 1, 0, "robe_purple", "")
                .box(-6.5, -6.5, 2.5, 13, 10, 1, 0, "gold", "")
                .box(-1, -17, 2.5, 2, 11, 1, 0, "gold", "")
                .box(-5, -14, 2.5, 2, 8, 1, 0, "gold", "")
                .box(3, -14, 2.5, 2, 8, 1, 0, "gold", "")
                .box(-8, -10, 2.5, 2, 6, 1, 0, "gold", "")
                .box(6, -10, 2.5, 2, 6, 1, 0, "gold", "")
                .box(-0.5, -19, 2.5, 1, 2, 1, 0, "crystal", "")
                .box(-4.5, -15.5, 2.5, 1, 2, 1, 0, "crystal", "")
                .box(3.5, -15.5, 2.5, 1, 2, 1, 0, "crystal", "");
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "right" : "left";
            Part arm = m.part("arm_" + side, "body", 6.5 * s, -10, 0)
                    .box(-1.5, 0, -1.5, 3, 11, 3, "frost_bone")
                    .box(-3, -1, -3, 6, 9, 6, 0, "robe_black", "")
                    .box(-3.3, 7, -3.3, 6, 1, 6, 0.3, "gold", "")
                    .box(-2, 10, -2, 4, 2, 4, 0, "frost_bone", "")
                    .box(-2, 12, -2.5, 1, 4, 1, 0, "frost_bone", "")
                    .box(-0.5, 12, -2.5, 1, 5, 1, 0, "frost_bone", "")
                    .box(1, 12, -2.5, 1, 4, 1, 0, "frost_bone", "")
                    .box(-0.7, 13, -2.7, 1, 1, 1, 0.15, "gold", "");
            if (s < 0) {
                // Посох Аинза: золотое древко и навершие — змеи держат самоцветы.
                arm.box(-0.5, -8, -4, 1, 32, 1, 0, "gold", "")
                        .box(-2.5, -13, -6, 5, 5, 5, 0, "gold", "")
                        .box(-1, -12, -4.5, 2, 2, 2, 0, "crystal", "")
                        .box(-4, -15, -4.5, 2, 2, 2, 0, "crystal", "")
                        .box(2, -15, -4.5, 2, 2, 2, 0, "crystal", "")
                        .box(-1, -17, -4.5, 2, 2, 2, 0, "crystal", "")
                        .box(-3.5, -16, -4, 1, 5, 1, 0, "gold", "")
                        .box(2.5, -16, -4, 1, 5, 1, 0, "gold", "")
                        .box(-0.5, -18, -4, 1, 2, 1, 0, "gold", "");
            }
            m.part("pauldron_" + side, "arm_" + side, 0, 0, 0)
                    .box(-4, -3, -3.5, 8, 4, 7, 0.2, "gold", "plate")
                    .box(s < 0 ? -4.5 : 2.5, -7, -1, 2, 4, 2, 0, "gold", "")
                    .box(-1, -4, -4, 2, 2, 1, 0, "crystal", "")
                    .box(-4.5, 0, -4, 9, 1, 8, 0, "robe_purple", "");
        }
        for (int i = 1; i <= 4; i++) {
            m.part("shard_" + i, "root", 0, 0, 0)
                    .box(-1.5, -1.5, -1.5, 3, 3, 3, 0, "crystal", "");
        }
        return m;
    }

    /**
     * Ледяной голем: сгорбленная глыба из кристаллов без шеи, один светящийся глаз, длинные руки с сосульками-когтями
     * и ледяные шипы, растущие из спины и плеч.
     */
    static Model iceGolem() {
        Model m = new Model("ice_golem", ICE_BLUE, "frost");
        m.part("body", "root", 0, 12, 1, 0.4, 0, 0)
                .box(-5, -10, -4, 10, 10, 8, 0, "ice", "facets")
                .box(-3, -8, -4.8, 6, 5, 1, 0, "ice_dark", "")
                .box(-1, -7, -5.2, 2, 2, 1, 0, "crystal", "")
                .box(-4, -14, 0, 2, 5, 2, 0, "crystal", "")
                .box(0, -16, 1, 2, 7, 2, 0, "crystal", "")
                .box(3, -13, 0, 2, 4, 2, 0, "crystal", "")
                .box(-2, -12, 3, 2, 3, 2, 0, "ice", "")
                .box(-5.5, -3, -4.5, 11, 3, 9, 0, "ice_dark", "facets");
        m.part("head", "body", 0, -10, -3)
                .box(-2.5, -3, -2.5, 5, 3, 4, 0, "ice_dark", "cyclops");
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "right" : "left";
            m.part("arm_" + side, "root", 6.5 * s, 4, -1)
                    .box(-2, -2, -2, 4, 12, 4, 0, "ice", "facets")
                    .box(-2.5, -3, -2.5, 5, 4, 5, 0, "ice_dark", "")
                    .box(s < 0 ? -2 : 0, -6, -1, 2, 4, 2, 0, "crystal", "")
                    .box(-2, 10, -2, 1, 4, 1, 0, "ice_light", "")
                    .box(-0.5, 10, -2, 1, 5, 1, 0, "ice_light", "")
                    .box(1, 10, -2, 1, 4, 1, 0, "ice_light", "")
                    .box(-0.5, 10, 1, 1, 3, 1, 0, "ice_light", "");
            m.part("leg_" + side, "root", 2.5 * s, 14, 0)
                    .box(-2, 0, -2, 4, 10, 4, 0, "ice_dark", "facets")
                    .box(-2.5, 8, -3, 5, 2, 5, 0, "ice", "");
        }
        return m;
    }

    /** Замороженный лучник: скелет во льду — ледяная корка на черепе, сосульки-борода, ледяной нагрудник и наручи. */
    static Model frozenArcher() {
        Model m = new Model("frozen_archer", ICE_BLUE, "frost");
        m.part("head", "root", 0, 0, 0)
                .box(-4, -8, -4, 8, 8, 8, 0, "frost_bone", "face")
                .box(-4.5, -9, -4.5, 9, 3, 9, 0, "ice", "facets")
                .box(-3, -11, -2, 2, 3, 2, 0, "crystal", "")
                .box(1, -12, 0, 2, 4, 2, 0, "crystal", "")
                .box(-2, 0, -4.2, 1, 2, 1, 0, "ice", "")
                .box(1, 0, -4.2, 1, 3, 1, 0, "ice", "");
        m.part("hat", "root", 0, 0, 0);
        m.part("body", "root", 0, 0, 0)
                .box(-4, 0, -2, 8, 12, 4, 0, "frost_bone", "ribs")
                .box(-4.5, 0, -2.5, 9, 6, 5, 0, "ice", "plate")
                .box(-4.5, 9, -2.5, 9, 3, 5, 0, "robe", "tatter")
                .box(-3, 0, 2.2, 3, 9, 2, 0, "leather", "");
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "right" : "left";
            m.part(side + "_arm", "root", 5 * s, 2, 0)
                    .box(-1, -2, -1, 2, 12, 2, "frost_bone")
                    .box(-1.5, 4, -1.5, 3, 4, 3, 0, "ice", "band")
                    .box(s < 0 ? -2 : -1, -3, -2, 3, 3, 4, 0, "ice", "facets");
            m.part(side + "_leg", "root", 2 * s, 12, 0)
                    .box(-1, 0, -1, 2, 12, 2, "frost_bone")
                    .box(-1.5, 8, -1.5, 3, 4, 3, 0, "ice", "facets");
        }
        return m;
    }

    /**
     * Снеговик-громила: три снежных кома, обмотанных ледяными плитами, угольные пуговицы и сосулька вместо носа,
     * руки-брёвна, вмёрзшие в ледяные кулаки, и ледяной венец.
     */
    static Model snowBrute() {
        Model m = new Model("snow_brute", ICE_BLUE, "frost");
        m.part("body", "root", 0, 20, 0)
                .box(-7, -10, -6, 14, 10, 12, 0, "snow", "")
                .box(-6, -19, -5, 12, 9, 10, 0, "snow", "")
                .box(-6.5, -19, -5.5, 13, 4, 1, 0, "ice", "plate")
                .box(-7.5, -6, -6.5, 15, 2, 13, 0, "ice", "band")
                .box(-1, -14, -5.6, 2, 2, 1, 0, "coal", "")
                .box(-1, -10.5, -6.6, 2, 2, 1, 0, "coal", "")
                .box(-1, -6.5, -6.6, 2, 2, 1, 0, "coal", "")
                .box(-5, -21, -1, 3, 3, 3, 0, "crystal", "")
                .box(3, -21, 0, 2, 4, 2, 0, "crystal", "");
        m.part("head", "body", 0, -19, -1)
                .box(-4.5, -9, -4.5, 9, 9, 9, 0, "snow", "face")
                .box(-0.5, -5, -7, 1, 1, 3, 0, "ice_light", "")
                .box(-5, -10, -5, 10, 2, 10, 0, "ice", "band")
                .box(-1, -14, -1, 2, 4, 2, 0, "crystal", "")
                .box(-4, -12, -1, 2, 2, 2, 0, "ice", "")
                .box(2, -12, -1, 2, 2, 2, 0, "ice", "");
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "right" : "left";
            m.part("arm_" + side, "root", 8.5 * s, 5, 0)
                    .box(-1.5, -1, -1.5, 3, 13, 3, "wood")
                    .box(s < 0 ? -3 : 1, 3, -1, 2, 1, 2, 0, "wood", "")
                    .box(-3, 11, -3, 6, 6, 6, 0, "ice", "facets")
                    .box(-2, 16, -3.5, 1, 2, 1, 0, "ice_light", "")
                    .box(1, 16, -3.5, 1, 2, 1, 0, "ice_light", "");
            m.part("leg_" + side, "root", 3.5 * s, 20, 0)
                    .box(-2.5, 0, -2.5, 5, 4, 5, 0, "ice_dark", "facets");
        }
        return m;
    }

    // ---------------------------------------------------------------- Нежить Ледяной цитадели (по мотивам Плети из Warcraft)

    static final int[] PLAGUE_GREEN = {150, 255, 90}, GHOST_WHITE = {200, 245, 255};

    /** Чумной зомби: гниющая зелёная кожа, отвисшая челюсть, рваная рубаха, рёбра наружу, кость из руки. */
    static Model undeadZombie() {
        Model m = new Model("undead_zombie", PLAGUE_GREEN, "undead");
        m.part("head", "root", 0, 0, 0)
                .box(-4, -8, -4, 8, 8, 8, 0, "rotten", "face")
                .box(-3, -1, -4.5, 6, 2, 4, 0, "rotten_dark", "")
                .box(-4.5, -8.5, -2, 3, 2, 3, 0, "cloth", "")
                .box(1, -9, 0, 2, 2, 2, 0, "rotten_dark", "");
        m.part("hat", "root", 0, 0, 0);
        m.part("body", "root", 0, 0, 0)
                .box(-4, 0, -2, 8, 12, 4, 0, "cloth", "tatter")
                .box(-3, 2, -2.6, 5, 5, 1, 0, "frost_bone", "ribs")
                .box(-4.3, 7, -2.3, 3, 4, 1, 0, "rotten", "")
                .box(-4.5, 9, -2.5, 9, 2, 5, 0, "rope", "band");
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "right" : "left";
            Part arm = m.part(side + "_arm", "root", 5 * s, 2, 0)
                    .box(s < 0 ? -3 : -1, -2, -2, 4, 12, 4, 0, "rotten", "")
                    .box(s < 0 ? -3.3 : -1.3, -2.3, -2.3, 4, 5, 4, 0, "cloth", "tatter");
            if (s > 0) arm.box(0, 6, -2.6, 1, 4, 1, 0, "frost_bone", "");
            m.part(side + "_leg", "root", 1.9 * s, 12, 0)
                    .box(-2, 0, -2, 4, 12, 4, 0, "cloth", "tatter")
                    .box(-2, 9, -2, 4, 3, 4, 0, "rotten_dark", "");
        }
        return m;
    }

    /** Вурдалак: сгорбленный, тощий, серо-лиловый, огромная пасть с клыками, длинные руки с когтями. */
    static Model ghoul() {
        Model m = new Model("ghoul", RED, "undead");
        m.part("body", "root", 0, 12, 2, 0.9, 0, 0)
                .box(-4, -10, -3, 8, 10, 6, 0, "ghoul", "ribs")
                .box(-1, -11, 2.5, 2, 9, 2, 0, "frost_bone", "")
                .box(-3.5, -1, -3.5, 7, 3, 7, 0, "cloth", "tatter");
        m.part("head", "body", 0, -10, -1, -0.7, 0, 0)
                .box(-3.5, -6, -6, 7, 6, 7, 0, "ghoul", "face")
                .box(-3, 0, -6.5, 6, 2, 5, 0, "ghoul_dark", "")
                .box(-2.5, -0.5, -6.8, 1, 2, 1, 0, "frost_bone", "")
                .box(1.5, -0.5, -6.8, 1, 2, 1, 0, "frost_bone", "")
                .box(-5, -5, -2, 2, 3, 1, 0, "ghoul_dark", "")
                .box(3, -5, -2, 2, 3, 1, 0, "ghoul_dark", "");
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "right" : "left";
            m.part("arm_" + side, "root", 5 * s, 5, -3)
                    .box(-1.5, -1, -1.5, 3, 13, 3, "ghoul")
                    .box(-2, 11, -2, 4, 2, 4, 0, "ghoul_dark", "")
                    .box(-2, 13, -2.5, 1, 4, 1, 0, "frost_bone", "")
                    .box(-0.5, 13, -2.5, 1, 5, 1, 0, "frost_bone", "")
                    .box(1, 13, -2.5, 1, 4, 1, 0, "frost_bone", "");
            m.part("leg_" + side, "root", 2.5 * s, 13, 1)
                    .box(-1.5, 0, -1.5, 3, 11, 3, "ghoul")
                    .box(-2, 9, -3.5, 4, 2, 5, 0, "ghoul_dark", "");
        }
        return m;
    }

    /** Банши: полупрозрачный призрак эльфийки — длинные белые волосы, тающая книзу мантия, тонкие руки. */
    static Model banshee() {
        Model m = new Model("banshee", GHOST_WHITE, "undead");
        m.part("body", "root", 0, 7, 0)
                .box(-3.5, -9, -2, 7, 9, 4, 0, "ghost", "")
                .box(-4, 0, -2.5, 8, 6, 5, 0, "ghost", "")
                .box(-3, 6, -2, 6, 5, 4, 0, "ghost", "tatter")
                .box(-2, 11, -1.5, 4, 4, 3, 0, "ghost_dark", "tatter");
        m.part("head", "body", 0, -9, 0)
                .box(-3.5, -7, -3.5, 7, 7, 7, 0, "ghost", "face")
                .box(-4, -8, 0.5, 8, 12, 3, 0, "ghost_hair", "tatter")
                .box(-4, -8, -4, 8, 2, 5, 0, "ghost_hair", "")
                .box(-4.2, -6, -3, 1, 8, 3, 0, "ghost_hair", "tatter")
                .box(3.2, -6, -3, 1, 8, 3, 0, "ghost_hair", "tatter");
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "right" : "left";
            m.part("arm_" + side, "root", 4.5 * s, -1, 0)
                    .box(-1, -1, -1, 2, 13, 2, "ghost")
                    .box(-1.5, 0, -1.5, 3, 6, 3, 0, "ghost_dark", "tatter");
            m.part("leg_" + side, "root", 1.5 * s, 21, 0)
                    .box(-1, 0, -1, 2, 2, 2, 0, "ghost_dark", "");
        }
        return m;
    }

    /** Лоскут плоти: огромное сшитое брюхо с вывалившимися кишками, крюки в спине, рука-тесак и рука-крюк. */
    static Model abomination() {
        Model m = new Model("abomination", PLAGUE_GREEN, "undead");
        m.part("body", "root", 0, 13, 0, 0.1, 0, 0)
                .box(-8, -12, -6, 16, 13, 12, 0, "flesh", "stitches")
                .box(-7, -18, -5, 14, 6, 10, 0, "flesh", "stitches")
                .box(-3, -5, -6.6, 6, 4, 1, 0, "guts", "")
                .box(-2, -1, -6.4, 2, 5, 1, 0, "guts", "")
                .box(-5, -22, 2, 2, 5, 2, 0, "metal", "")
                .box(3, -21, 2, 2, 4, 2, 0, "metal", "")
                .box(-1, -20, 4, 2, 4, 2, 0, "frost_bone", "")
                .box(-8.5, -3, -6.5, 17, 2, 13, 0, "rope", "band");
        m.part("head", "body", 0, -18, -4)
                .box(-3, -5, -3, 6, 5, 5, 0, "flesh", "face")
                .box(-2.5, 0, -3.3, 5, 2, 3, 0, "flesh_dark", "")
                .box(-3.5, -4, -2, 1, 3, 2, 0, "metal", "");
        for (int s = -1; s <= 1; s += 2) {
            String side = s < 0 ? "right" : "left";
            Part arm = m.part("arm_" + side, "root", 9.5 * s, 0, 0)
                    .box(-2.5, -2, -2.5, 5, 14, 5, 0, "flesh", "stitches")
                    .box(-3, -3, -3, 6, 4, 6, 0, "flesh_dark", "");
            if (s < 0) arm.box(-0.5, 10, -9, 1, 9, 12, 0, "metal", "")
                    .box(-1.5, 10, -1.5, 3, 3, 3, 0, "rope", "");
            else arm.box(-0.5, 12, -0.5, 1, 6, 1, 0, "metal", "")
                    .box(-0.5, 17, -3, 1, 1, 3, 0, "metal", "")
                    .box(-0.5, 15, -3, 1, 2, 1, 0, "metal", "");
            m.part("leg_" + side, "root", 4 * s, 13, 0)
                    .box(-3, 0, -3, 6, 11, 6, 0, "flesh", "stitches")
                    .box(-3.5, 9, -4, 7, 2, 7, 0, "flesh_dark", "");
        }
        return m;
    }

    // =====================================================================================
    // Текстуры
    // =====================================================================================

    static int[][] glowPalette(int[] c) {
        return new int[][]{c, scale(c, 0.65), {Math.min(255, c[0] + 60), Math.min(255, c[1] + 60), Math.min(255, c[2] + 60)}};
    }

    /** Основной цвет, тёмный и светлый оттенки материала. */
    static int[][] palette(String mat) {
        return switch (mat) {
            case "clay" -> new int[][]{{176, 108, 70}, {112, 62, 40}, {214, 150, 104}};
            case "clay_dark" -> new int[][]{{140, 82, 54}, {86, 46, 30}, {180, 118, 80}};
            case "pale" -> new int[][]{{170, 156, 140}, {108, 96, 86}, {206, 196, 180}};
            case "pale_dark" -> new int[][]{{132, 118, 104}, {80, 70, 62}, {170, 158, 142}};
            case "dark" -> new int[][]{{110, 70, 52}, {62, 36, 26}, {150, 100, 76}};
            case "leather" -> new int[][]{{110, 74, 44}, {64, 40, 22}, {150, 106, 66}};
            case "copper" -> new int[][]{{184, 104, 56}, {104, 52, 26}, {238, 170, 110}};
            case "ice" -> new int[][]{{146, 204, 236}, {62, 118, 172}, {226, 246, 255}};
            case "ice_dark" -> new int[][]{{92, 150, 200}, {36, 74, 124}, {160, 210, 240}};
            case "ice_light" -> new int[][]{{206, 238, 255}, {140, 196, 232}, {250, 254, 255}};
            case "snow" -> new int[][]{{232, 238, 244}, {170, 186, 206}, {255, 255, 255}};
            case "frost_bone" -> new int[][]{{196, 204, 212}, {120, 132, 150}, {232, 238, 244}};
            case "robe" -> new int[][]{{58, 84, 140}, {26, 38, 76}, {98, 132, 196}};
            case "robe_dark" -> new int[][]{{40, 56, 100}, {16, 22, 48}, {70, 96, 150}};
            case "robe_trim" -> new int[][]{{150, 200, 230}, {80, 120, 170}, {210, 236, 250}};
            case "coal" -> new int[][]{{44, 44, 50}, {16, 16, 20}, {84, 84, 96}};
            case "wood" -> new int[][]{{110, 80, 50}, {64, 44, 26}, {150, 112, 74}};
            case "robe_black" -> new int[][]{{34, 28, 40}, {12, 9, 16}, {64, 54, 76}};
            case "robe_purple" -> new int[][]{{84, 42, 118}, {42, 18, 64}, {130, 76, 170}};
            case "gold" -> new int[][]{{214, 168, 58}, {128, 88, 20}, {252, 224, 124}};
            case "rotten" -> new int[][]{{112, 138, 96}, {58, 80, 52}, {152, 172, 126}};
            case "rotten_dark" -> new int[][]{{84, 104, 70}, {40, 56, 36}, {120, 140, 98}};
            case "cloth" -> new int[][]{{96, 76, 60}, {48, 36, 28}, {132, 106, 84}};
            case "rope" -> new int[][]{{120, 96, 64}, {66, 50, 32}, {160, 132, 92}};
            case "ghoul" -> new int[][]{{128, 118, 140}, {68, 60, 80}, {168, 158, 180}};
            case "ghoul_dark" -> new int[][]{{94, 84, 106}, {48, 40, 58}, {130, 120, 142}};
            case "ghost" -> new int[][]{{176, 228, 240}, {104, 164, 196}, {228, 250, 255}};
            case "ghost_dark" -> new int[][]{{120, 180, 210}, {64, 112, 150}, {176, 222, 240}};
            case "ghost_hair" -> new int[][]{{226, 240, 248}, {160, 190, 210}, {255, 255, 255}};
            case "flesh" -> new int[][]{{188, 122, 116}, {118, 62, 60}, {224, 162, 150}};
            case "flesh_dark" -> new int[][]{{150, 92, 90}, {90, 44, 44}, {190, 128, 120}};
            case "guts" -> new int[][]{{156, 44, 54}, {84, 16, 26}, {204, 86, 96}};
            case "metal" -> new int[][]{{150, 156, 166}, {78, 84, 96}, {212, 216, 226}};
            case "crystal" -> new int[][]{{255, 170, 60}, {200, 100, 20}, {255, 230, 150}};
            case "bone" -> new int[][]{{214, 204, 178}, {150, 138, 112}, {240, 234, 214}};
            case "clayball" -> new int[][]{{196, 130, 88}, {140, 84, 54}, {226, 170, 128}};
            default -> new int[][]{{132, 56, 34}, {74, 28, 16}, {184, 92, 54}}; // fired — обожжённая терракота лат
        };
    }

    static BufferedImage[] paint(Model m) {
        int w = m.texW * R, h = m.texH * R;
        BufferedImage base = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        BufferedImage glow = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(m.name.hashCode());
        for (Part p : m.parts) {
            for (int i = 0; i < p.boxes.size(); i++) {
                Box b = p.boxes.get(i);
                int u = p.uv.get(i)[0], v = p.uv.get(i)[1];
                // Грани: верх, низ, правая (запад), перед (север), левая (восток), зад (юг).
                face(base, glow, m, b, "top", u + b.d, v, b.w, b.d, rnd);
                face(base, glow, m, b, "bottom", u + b.d + b.w, v, b.w, b.d, rnd);
                face(base, glow, m, b, "side", u, v + b.d, b.d, b.h, rnd);
                face(base, glow, m, b, "front", u + b.d, v + b.d, b.w, b.h, rnd);
                face(base, glow, m, b, "side", u + b.d + b.w, v + b.d, b.d, b.h, rnd);
                face(base, glow, m, b, "back", u + 2 * b.d + b.w, v + b.d, b.w, b.h, rnd);
            }
        }
        return new BufferedImage[]{base, glow};
    }

    /** Одна грань кубоида: материал с шумом и разводами, затенение сверху вниз, скошенные края и особенности. */
    static void face(BufferedImage base, BufferedImage glow, Model m, Box b, String kind, int u, int v, int fw, int fh, Random rnd) {
        int x0 = u * R, y0 = v * R, pw = fw * R, ph = fh * R;
        if (pw <= 0 || ph <= 0) return;
        // Светящиеся наросты окрашены в цвет глаз модели.
        int[][] pal = b.mat.equals("crystal") ? glowPalette(m.glow) : palette(b.mat);
        boolean fired = b.mat.equals("fired");
        long seed = rnd.nextLong();
        double[][] tone = new double[pw][ph];
        for (int x = 0; x < pw; x++) {
            for (int y = 0; y < ph; y++) {
                double n = noise(seed, x * K / 9.0, y * K / 9.0) * 0.55 + noise(seed + 1, x * K / 3.5, y * K / 3.5) * 0.3 + noise(seed + 2, x * K, y * K) * 0.15;
                // Разводы пальцев по сырой глине (у лат — ровные полосы обжига).
                double smear = fired ? Math.sin(y * K * 0.9 + noise(seed + 3, x * K / 14.0, 0) * 3) * 0.04
                        : Math.sin((x * K * 0.35 + y * K * 0.12) + noise(seed + 4, x * K / 7.0, y * K / 7.0) * 4) * 0.06;
                double shade = switch (kind) {
                    case "top" -> 1.12;
                    case "bottom" -> 0.68;
                    default -> 1.08 - 0.25 * y / (double) ph;
                };
                tone[x][y] = (0.82 + 0.36 * n + smear) * shade;
            }
        }
        // Трещины: случайные блуждания, тёмные с светлой кромкой.
        int cracks = (pw * ph < 12 ? 0 : fired ? 1 : 2) + (int) (pw * ph * K * K / 900);
        for (int c = 0; c < cracks; c++) crack(tone, rnd, pw, ph, 0.55);
        // Скошенные края грани.
        for (int x = 0; x < pw; x++) {
            for (int y = 0; y < ph; y++) {
                int edge = Math.min(Math.min(x, pw - 1 - x), Math.min(y, ph - 1 - y));
                if (edge == 0) tone[x][y] *= 0.72;
                else if (edge == 1 && R > 1) tone[x][y] *= fired ? 1.18 : 0.88;
                else if (edge == 2 && R > 1 && fired) tone[x][y] *= 1.1;
            }
        }
        for (int x = 0; x < pw; x++) for (int y = 0; y < ph; y++) put(base, x0 + x, y0 + y, mix(pal, tone[x][y]));

        if (fired && b.feature.equals("plate")) plate(base, x0, y0, pw, ph, kind);
        if (b.feature.equals("seams") && !kind.equals("bottom")) seams(base, x0, y0, pw, ph, pal);
        if (b.feature.equals("facets")) {
            // Лёд: косые светлые грани и редкие искры.
            for (int x = 0; x < pw; x++) for (int y = 0; y < ph; y++) {
                if ((x + y * 2) % 5 == 0) put(base, x0 + x, y0 + y, mix(pal, 1.18));
                else if ((x * 3 + y) % 11 == 0) put(base, x0 + x, y0 + y, pal[2]);
            }
        }
        if (b.feature.equals("ribs") && !kind.equals("top") && !kind.equals("bottom")) {
            // Рёбра: тёмные щели между костями.
            for (int y = 1; y < ph - 1; y += 2) for (int x = 1; x < pw - 1; x++) put(base, x0 + x, y0 + y, scale(pal[1], 0.55));
        }
        if (b.feature.equals("tatter") && !kind.equals("top") && !kind.equals("bottom")) {
            // Рваный подол: снизу клочья, сквозь которые видно насквозь.
            Random tear = new Random((long) x0 * 31 + y0);
            for (int x = 0; x < pw; x++) {
                int cut = tear.nextInt(Math.max(1, Math.min(3, ph / 2)) + 1);
                for (int y = ph - cut; y < ph; y++) base.setRGB(x0 + x, y0 + y, 0);
            }
        }
        if (b.feature.equals("stitches") && !kind.equals("bottom")) {
            // Грубые швы: тёмная линия поперёк грани с поперечными стежками.
            int sy = ph / 2, sx = pw / 3;
            for (int x = 1; x < pw - 1; x++) put(base, x0 + x, y0 + sy, scale(pal[1], 0.6));
            for (int x = 1; x < pw - 1; x += 2) {
                put(base, x0 + x, y0 + sy - 1, scale(pal[1], 0.8));
                put(base, x0 + x, y0 + sy + 1, scale(pal[1], 0.8));
            }
            for (int y = 1; y < ph - 1; y++) if (sx > 1 && pw > 6) put(base, x0 + sx, y0 + y, scale(pal[1], 0.6));
        }
        if (b.mat.startsWith("ghost")) {
            // Призрак полупрозрачен.
            for (int x = 0; x < pw; x++) for (int y = 0; y < ph; y++) {
                int c = base.getRGB(x0 + x, y0 + y);
                if ((c >>> 24) != 0) base.setRGB(x0 + x, y0 + y, (b.mat.equals("ghost_hair") ? 0xC8000000 : 0xA0000000) | (c & 0xFFFFFF));
            }
        }
        if (kind.equals("front") && b.feature.equals("cyclops")) {
            int cx = pw / 2 - 1;
            for (int x = cx; x <= cx + 1; x++) {
                put(base, x0 + x, y0 + ph / 2, m.glow);
                put(glow, x0 + x, y0 + ph / 2, m.glow);
            }
        }
        if (kind.equals("front") && b.feature.equals("face")) eyes(base, glow, m.glow, x0, y0, pw, ph, b.mat);
        if (kind.equals("front") && b.feature.equals("core")) core(base, glow, m.glow, x0, y0, pw, ph, rnd);
        if (b.feature.equals("fist") && (kind.equals("front") || kind.equals("bottom"))) knuckles(base, x0, y0, pw, ph, pal);
        if (b.feature.equals("band") && !kind.equals("top") && !kind.equals("bottom")) {
            // Обмотка или медный обруч: светлая кромка сверху, тёмная снизу.
            for (int x = 0; x < pw; x++) {
                put(base, x0 + x, y0, pal[2]);
                put(base, x0 + x, y0 + ph - 1, pal[1]);
            }
        }
        if (b.mat.equals("crystal")) {
            // Кристаллы светятся целиком: грань с бликом в верхнем левом углу.
            for (int x = 0; x < pw; x++) for (int y = 0; y < ph; y++) {
                int[] c = (x + y < 2) ? pal[2] : get(base, x0 + x, y0 + y);
                put(base, x0 + x, y0 + y, c);
                put(glow, x0 + x, y0 + y, c);
            }
        }
    }

    /** Латы: заклёпки по углам, гравированные полосы и медный кант. */
    static void plate(BufferedImage img, int x0, int y0, int pw, int ph, String kind) {
        int[] copper = {214, 136, 72}, copperDark = {120, 64, 30};
        if (R == 1) {
            // Пиксельные латы: медные заклёпки в углах и бороздки через 3 пикселя.
            if (!kind.equals("top") && !kind.equals("bottom") && ph >= 5) {
                for (int y = 2; y < ph - 1; y += 3) for (int x = 1; x < pw - 1; x++) put(img, x0 + x, y0 + y, scale(get(img, x0 + x, y0 + y), 0.78));
            }
            if (pw >= 4 && ph >= 4) {
                for (int[] p : new int[][]{{1, 1}, {pw - 2, 1}, {1, ph - 2}, {pw - 2, ph - 2}}) put(img, x0 + p[0], y0 + p[1], copper);
            }
            return;
        }
        for (int x = 3; x < pw - 3; x++) {
            put(img, x0 + x, y0 + 3, copperDark);
            put(img, x0 + x, y0 + ph - 4, copperDark);
        }
        if (!kind.equals("top") && !kind.equals("bottom") && ph > 20) {
            for (int y = 8; y < ph - 8; y += 8) for (int x = 6; x < pw - 6; x++) {
                int[] c = get(img, x0 + x, y0 + y);
                put(img, x0 + x, y0 + y, scale(c, 0.7));
                put(img, x0 + x, y0 + y + 1, scale(c, 1.15));
            }
        }
        int r = 2;
        for (int[] p : new int[][]{{6, 6}, {pw - 7, 6}, {6, ph - 7}, {pw - 7, ph - 7}}) {
            if (pw < 16 || ph < 16) break;
            for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) {
                if (dx * dx + dy * dy > r * r + 1) continue;
                boolean light = dx + dy < 0;
                put(img, x0 + p[0] + dx, y0 + p[1] + dy, light ? copper : copperDark);
            }
        }
    }

    /** Швы между пластами глины — как будто голема лепили слоями. */
    static void seams(BufferedImage img, int x0, int y0, int pw, int ph, int[][] pal) {
        int step = Math.max(4, 11 * R / 4);
        for (int y = step - 1; y < ph - 2; y += step) {
            for (int x = 1; x < pw - 1; x++) {
                int dy = R > 1 ? (int) Math.round(Math.sin(x * 0.4 + y) * 1.2) : 0;
                put(img, x0 + x, y0 + y + dy, scale(pal[1], 0.9));
                put(img, x0 + x, y0 + y + dy + 1, pal[2]);
            }
        }
    }

    /** Костяшки кулака: тёмные ложбинки между пальцами. */
    static void knuckles(BufferedImage img, int x0, int y0, int pw, int ph, int[][] pal) {
        int fingers = 4;
        for (int f = 1; f < fingers; f++) {
            int x = pw * f / fingers;
            for (int y = 2; y < ph - 2; y++) {
                put(img, x0 + x, y0 + y, scale(pal[1], 0.8));
                put(img, x0 + x + 1, y0 + y, pal[2]);
            }
        }
    }

    /** Глаза: две светящиеся прорези с ореолом на основном слое и чистым светом на светящемся. */
    static void eyes(BufferedImage base, BufferedImage glow, int[] color, int x0, int y0, int pw, int ph, String mat) {
        int ew = R > 1 ? Math.max(4, pw / 5) : Math.max(1, pw / 4), eh = Math.max(R > 1 ? 3 : 1, ph / 7);
        int ring = R > 1 ? 2 : 1;
        int ey = ph * 2 / 5;
        for (int e = 0; e < 2; e++) {
            int ex = e == 0 ? pw / 4 - ew / 2 + 1 : pw * 3 / 4 - ew / 2 - 1;
            for (int x = -ring; x < ew + ring; x++) {
                for (int y = -ring; y < eh + ring; y++) {
                    boolean inside = x >= 0 && x < ew && y >= 0 && y < eh;
                    // Глубокая глазница вокруг.
                    if (!inside) {
                        put(base, x0 + ex + x, y0 + ey + y, scale(get(base, x0 + ex + x, y0 + ey + y), 0.45));
                        continue;
                    }
                    double hot = 1 - Math.abs(y - (eh - 1) / 2.0) / eh;
                    int[] c = {Math.min(255, (int) (color[0] * (0.7 + 0.5 * hot))), Math.min(255, (int) (color[1] * (0.7 + 0.5 * hot))),
                            Math.min(255, (int) (color[2] * (0.7 + 0.5 * hot)))};
                    put(base, x0 + ex + x, y0 + ey + y, c);
                    put(glow, x0 + ex + x, y0 + ey + y, c);
                }
            }
        }
        // Рот — тёмная щель.
        for (int x = pw / 3; x < pw * 2 / 3; x++) put(base, x0 + x, y0 + ph * 3 / 4, scale(palette(mat)[1], 0.6));
    }

    /** Ядро колосса: руна-круг в груди и расходящиеся светящиеся трещины (видны, когда нагрудник отвалится). */
    static void core(BufferedImage base, BufferedImage glow, int[] color, int x0, int y0, int pw, int ph, Random rnd) {
        int cx = pw / 2, cy = ph * 2 / 5, r = Math.min(pw, ph) / 6;
        for (int x = -r - 2; x <= r + 2; x++) {
            for (int y = -r - 2; y <= r + 2; y++) {
                double d = Math.sqrt(x * x + y * y);
                if (d > r + 1.5) continue;
                double heat = d < r * 0.55 ? 1 : (d > r - 1 && d < r + 1) ? 0.9 : 0.35;
                int[] c = {Math.min(255, (int) (color[0] * (0.5 + 0.6 * heat))), Math.min(255, (int) (color[1] * (0.4 + 0.6 * heat))),
                        Math.min(255, (int) (color[2] * (0.3 + 0.6 * heat)))};
                put(base, x0 + cx + x, y0 + cy + y, c);
                if (heat > 0.5) put(glow, x0 + cx + x, y0 + cy + y, c);
            }
        }
        for (int i = 0; i < 9; i++) {
            double a = Math.PI * 2 * i / 9 + rnd.nextDouble() * 0.4;
            double x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
            for (int step = 0; step < r * 3; step++) {
                a += (rnd.nextDouble() - 0.5) * 0.6;
                x += Math.cos(a);
                y += Math.sin(a);
                if (x < 2 || y < 2 || x >= pw - 2 || y >= ph - 2) break;
                double fade = 1 - step / (r * 3.0);
                int[] c = {(int) (color[0] * (0.5 + 0.5 * fade)), (int) (color[1] * (0.4 + 0.5 * fade)), (int) (color[2] * (0.3 + 0.4 * fade))};
                put(base, x0 + (int) x, y0 + (int) y, c);
                put(glow, x0 + (int) x, y0 + (int) y, c);
            }
        }
    }

    static void crack(double[][] tone, Random rnd, int pw, int ph, double depth) {
        double x = rnd.nextInt(pw), y = rnd.nextInt(ph), a = rnd.nextDouble() * Math.PI * 2;
        int length = (int) Math.max(2, (6 + rnd.nextInt(Math.max(4, Math.min(pw, ph) / 2))) / Math.max(1, K / 2));
        for (int i = 0; i < length; i++) {
            a += (rnd.nextDouble() - 0.5) * 0.9;
            x += Math.cos(a);
            y += Math.sin(a);
            int ix = (int) x, iy = (int) y;
            if (ix < 1 || iy < 1 || ix >= pw - 1 || iy >= ph - 1) return;
            tone[ix][iy] *= depth;
            tone[ix][iy - 1] *= 1.12;
        }
    }

    // ---------------------------------------------------------------- шум и цвет

    static double noise(long seed, double x, double y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(seed, ix, iy), b = hash(seed, ix + 1, iy), c = hash(seed, ix, iy + 1), d = hash(seed, ix + 1, iy + 1);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    static double hash(long seed, int x, int y) {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL);
        h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }

    /** Тон 1 — основной цвет; меньше — к тёмному оттенку, больше — к светлому. */
    static int[] mix(int[][] pal, double t) {
        int[] from, to;
        double k;
        if (t < 1) {
            from = pal[1];
            to = pal[0];
            k = Math.max(0, (t - 0.45) / 0.55);
        } else {
            from = pal[0];
            to = pal[2];
            k = Math.min(1, (t - 1) / 0.3);
        }
        return new int[]{(int) (from[0] + (to[0] - from[0]) * k), (int) (from[1] + (to[1] - from[1]) * k), (int) (from[2] + (to[2] - from[2]) * k)};
    }

    static int[] scale(int[] c, double k) {
        return new int[]{clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k)};
    }

    static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, Math.round(v)));
    }

    static void put(BufferedImage img, int x, int y, int[] c) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return;
        img.setRGB(x, y, 0xFF000000 | (c[0] << 16) | (c[1] << 8) | c[2]);
    }

    static int[] get(BufferedImage img, int x, int y) {
        if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) return new int[]{0, 0, 0};
        int rgb = img.getRGB(x, y);
        return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
    }

    static String f(double v) {
        return String.format(Locale.ROOT, "%.2ff", v);
    }

    static String camel(String name) {
        StringBuilder out = new StringBuilder();
        boolean up = false;
        for (char c : name.toCharArray()) {
            if (c == '_') up = true;
            else {
                out.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return out.toString();
    }
}
