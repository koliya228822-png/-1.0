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
 * Пауки Паучьего логова в духе Матери выводка: мохнатые тёмно-синие лапы с бежевыми пушистыми манжетами
 * на суставах и красными переливчатыми кончиками, зелёные светящиеся глаза и рога, у матери — фиолетовый кокон
 * с белыми узорами и зелёной кромкой, гроздья чёрных и бирюзовых яиц с паучатами.
 *
 * Пишет textures/entity/spider/<модель>.png и <модель>_glow.png и client/spider/SpiderModels.java.
 * Координаты — как у моделей Minecraft: y вниз, земля на y = 24, морда смотрит в −z.
 * Лапы: leg_<id> (разворот по y) → femur_<id> → tibia_<id> → tarsus_<id> (изгибы по z), id = r1..r4, l1..l4, 1 — передняя.
 * Запуск из корня проекта: java tools/SpiderGen.java .
 */
public class SpiderGen {
    /** Пикселей текстуры на единицу модели. */
    static final int R = 2;

    record Box(double x, double y, double z, int w, int h, int d, double inflate, String mat) {}

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
            return box(x, y, z, w, h, d, 0, mat);
        }

        Part box(double x, double y, double z, int w, int h, int d, double inflate, String mat) {
            boxes.add(new Box(x, y, z, w, h, d, inflate, mat));
            return this;
        }

        /** Кубоид с мохнатой накидкой поверх: волоски торчат за края. */
        Part furry(double x, double y, double z, int w, int h, int d, String mat, double shag) {
            box(x, y, z, w, h, d, 0, mat);
            return box(x, y, z, w, h, d, shag, mat.equals("cuff") ? "cuff_shag" : "shag");
        }

        /** Кубоид с центром в точке. */
        Part centered(double cx, double cy, double cz, int w, int h, int d, double inflate, String mat) {
            return box(cx - w / 2.0, cy - h / 2.0, cz - d / 2.0, w, h, d, inflate, mat);
        }
    }

    static final class Model {
        final String name;
        final List<Part> parts = new ArrayList<>();
        int texW, texH;

        Model(String name, int texW) {
            this.name = name;
            this.texW = texW;
        }

        Part part(String name, String parent, double px, double py, double pz) {
            return part(name, parent, px, py, pz, 0, 0, 0);
        }

        Part part(String name, String parent, double px, double py, double pz, double rx, double ry, double rz) {
            Part p = new Part(name, parent, px, py, pz, rx, ry, rz);
            parts.add(p);
            return p;
        }

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
        Path textures = root.resolve("src/main/resources/assets/heroicpaths/textures/entity/spider");
        Files.createDirectories(textures);
        List<Model> models = List.of(broodmother(), broodSpider(), venomSpitter(), broodTank(), eggSac());
        StringBuilder java = new StringBuilder();
        java.append("package com.heroicpaths.client.spider;\n\n")
                .append("import net.minecraft.client.model.geom.PartPose;\n")
                .append("import net.minecraft.client.model.geom.builders.CubeDeformation;\n")
                .append("import net.minecraft.client.model.geom.builders.CubeListBuilder;\n")
                .append("import net.minecraft.client.model.geom.builders.LayerDefinition;\n")
                .append("import net.minecraft.client.model.geom.builders.MeshDefinition;\n")
                .append("import net.minecraft.client.model.geom.builders.PartDefinition;\n\n")
                .append("import java.util.HashMap;\n")
                .append("import java.util.Map;\n\n")
                .append("// Сгенерировано tools/SpiderGen.java — не править вручную.\n")
                .append("public final class SpiderModels {\n")
                .append("    private SpiderModels() {}\n");
        for (Model m : models) {
            m.pack();
            BufferedImage[] img = paint(m);
            ImageIO.write(img[0], "png", textures.resolve(m.name + ".png").toFile());
            ImageIO.write(img[1], "png", textures.resolve(m.name + "_glow.png").toFile());
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
        Path out = root.resolve("src/main/java/com/heroicpaths/client/spider/SpiderModels.java");
        Files.createDirectories(out.getParent());
        Files.writeString(out, java.toString(), StandardCharsets.UTF_8);
    }

    // =====================================================================================
    // Лапы
    // =====================================================================================

    /** Размеры одной пары лап. */
    record Leg(double z, double spread, double femur, double tibia, double tarsus, int thickF, int thickT, int thickTar,
               double lift, double tarsusDown, int cuff) {}

    /**
     * Пара лап: от бока тела (x = ±side, высота y) бедро уходит вверх под углом lift, голень — вниз так, чтобы
     * кончик лапки встал на землю (y = 24), лапка почти отвесна (tarsusDown).
     */
    static void legs(Model m, String parent, int index, double side, double y, double groundY, Leg l, boolean shaggy) {
        double kneeY = y - l.femur * Math.sin(l.lift);
        double need = groundY - kneeY - l.tarsus * Math.sin(l.tarsusDown);
        double beta = Math.asin(Math.max(-1, Math.min(1, need / l.tibia)));
        double tibiaRel = -(l.lift + beta);
        double tarsusRel = -(l.tarsusDown - beta);
        for (int s : new int[]{-1, 1}) {
            String id = (s < 0 ? "r" : "l") + index;
            m.part("leg_" + id, parent, s * side, y, l.z, 0, s * l.spread, 0)
                    .centered(0, 0, 0, l.cuff, l.cuff, l.cuff, 0.2, "cuff")
                    .centered(0, 0, 0, l.cuff, l.cuff, l.cuff, 0.7, "cuff_shag");
            // Бедро — золотисто-бежевое, лохматое; голень — тёмно-синий мех; лапка тёмная с красным когтем.
            Part femur = m.part("femur_" + id, "leg_" + id, 0, 0, 0, 0, 0, -s * l.lift);
            segment(femur, s, l.femur, l.thickF, "mane", shaggy ? 0.6 : 0.35);
            ring(femur, s, l.femur - 1, 3, l.thickF + 2, shaggy ? 0.6 : 0.35);
            Part tibia = m.part("tibia_" + id, "femur_" + id, s * l.femur, 0, 0, 0, 0, -s * tibiaRel);
            segment(tibia, s, l.tibia, l.thickT, shaggy ? "fur" : "fur_short", shaggy ? 0.55 : 0.3);
            ring(tibia, s, l.tibia - 0.5, 2, l.thickT + 1, shaggy ? 0.5 : 0.3);
            Part tarsus = m.part("tarsus_" + id, "tibia_" + id, s * l.tibia, 0, 0, 0, 0, -s * tarsusRel);
            int len = (int) Math.round(l.tarsus);
            int claw = Math.max(1, len / 3);
            double t = l.thickTar;
            tarsus.box(s < 0 ? -(len - claw) : 0, -t / 2, -t / 2, len - claw, l.thickTar, l.thickTar, 0, "fur_short");
            // Переливчатый красный коготь.
            tarsus.box(s < 0 ? -len : len - claw, -t / 2, -t / 2, claw, l.thickTar, l.thickTar, -0.12, "tip");
            tarsus.centered(s * (len + 0.3), 0.2, 0, 1, 1, 1, -0.2, "tip");
        }
    }

    /** Сегмент лапы вдоль ±x длиной len с квадратным сечением t и мохнатой накидкой. */
    static void segment(Part p, int s, double len, int t, String mat, double shag) {
        int n = (int) Math.round(len);
        p.box(s < 0 ? -n : 0, -t / 2.0, -t / 2.0, n, t, t, 0, mat)
                .box(s < 0 ? -n : 0, -t / 2.0, -t / 2.0, n, t, t, shag, mat.equals("mane") ? "cuff_shag" : "shag");
    }

    /** Пушистая бежевая манжета вокруг сустава: кольцо шириной w в точке at вдоль лапы. */
    static void ring(Part p, int s, double at, int w, int size, double shag) {
        p.centered(s * at, 0, 0, w, size, size, 0.1, "cuff").centered(s * at, 0, 0, w, size, size, shag + 0.1, "cuff_shag");
    }

    /** Круглое яйцо из трёх кубоидов (без острых углов). */
    static void egg(Part p, double cx, double cy, double cz, int size, String mat) {
        p.centered(cx, cy, cz, size, size - 2, size - 2, 0, mat)
                .centered(cx, cy, cz, size - 2, size, size - 2, 0, mat)
                .centered(cx, cy, cz, size - 2, size - 2, size, 0, mat)
                .centered(cx, cy, cz, size - 1, size - 1, size - 1, 0, mat);
    }

    /** Паучонок, сидящий на яйце (в точке x, y — низ брюшка, z). */
    static void spiderling(Part p, double x, double y, double z, double turn) {
        p.centered(x, y - 0.6, z + 0.6, 2, 1, 2, 0.05, "spiderling");
        p.centered(x, y - 0.6, z - 0.9, 1, 1, 1, 0.1, "spiderling");
        p.centered(x, y - 0.3, z - 0.2, 4, 1, 1, -0.32, "spiderling");
        p.centered(x, y - 0.3, z + 0.8, 4, 1, 1, -0.32, "spiderling");
        p.centered(x, y - 0.3, z + 1.7, 3, 1, 1, -0.34, "spiderling");
    }

    /** Глаза: два крупных спереди и россыпь мелких над ними. */
    static void eyes(Part head, double front, double top, int big, int small, double spread) {
        for (int s : new int[]{-1, 1}) {
            head.centered(s * spread, top + 2.2, front - 0.15, big, big, 1, -0.05, "eye");
            head.centered(s * (spread + big * 0.9), top + 1.0, front + 0.4, small, small, 1, -0.1, "eye");
            head.centered(s * spread * 0.45, top + 0.7, front + 0.2, small, small, 1, -0.1, "eye");
            head.centered(s * (spread + 0.6), top + 0.2, front + 1.6, small, small, 1, -0.15, "eye");
        }
    }

    // =====================================================================================
    // Модели
    // =====================================================================================

    /**
     * Матерь выводка: огромная паучиха. Головогрудь у земли, брюшко задрано и укрыто фиолетовым коконом,
     * вокруг кокона гроздья яиц (eggs_a вылупляются во второй фазе, eggs_b — в третьей), в третьей фазе
     * верх кокона (shell_top) раскалывается и открывает светящееся нутро (core).
     */
    static Model broodmother() {
        Model m = new Model("broodmother", 256);
        double y = 15;
        Part body = m.part("body", "root", 0, y, 0);
        body.box(-6, -3, -8, 12, 6, 12, 0, "fur").box(-6, -3, -8, 12, 6, 12, 0.7, "shag")
                .box(-5, -4.4, -7, 10, 2, 10, 0, "chitin")
                .box(-3, -5.2, -6, 6, 1, 7, 0, "chitin")
                .box(-5, 2.6, -7, 10, 1, 10, 0, "belly");
        // Голова чуть ниже и впереди груди, в бежевой гриве.
        Part head = m.part("head", "body", 0, -1, -8.5);
        head.box(-4.5, -3, -6, 9, 6, 6, 0, "fur").box(-4.5, -3, -6, 9, 6, 6, 0.5, "shag")
                .box(-4, -3.6, -6.3, 8, 2, 5, 0, "chitin")
                .box(-3, 1.5, -6.6, 6, 2, 1, 0, "chitin")
                .box(-4.5, -3.5, -1, 9, 4, 1, 0.2, "cuff").box(-4.5, -3.5, -1, 9, 4, 1, 0.6, "cuff_shag");
        eyes(head, -6.6, -3.4, 2, 1, 1.7);
        head.centered(0, -1.2, -6.5, 1, 1, 1, -0.05, "eye").centered(-1, 0, -6.5, 1, 1, 1, -0.15, "eye").centered(1, 0, -6.5, 1, 1, 1, -0.15, "eye");
        // Зелёные светящиеся рога: от лба вверх и в стороны, концы загибаются внутрь — как лира.
        for (int s : new int[]{-1, 1}) {
            String id = s < 0 ? "r" : "l";
            m.part("horn_" + id, "head", s * 2.2, -3.2, -4, 0.25, 0, s * 0.55).box(-1, -5, -1, 2, 5, 2, 0, "horn_base");
            m.part("horn_mid_" + id, "horn_" + id, 0, -5, 0, 0.05, 0, -s * 0.3).box(-1, -5, -1, 2, 5, 2, -0.2, "horn");
            m.part("horn_tip_" + id, "horn_mid_" + id, 0, -5, 0, -0.2, 0, -s * 0.45).box(-0.5, -5, -0.5, 1, 5, 1, 0, "horn");
            // Малые рожки ниже — пучок, как у референса.
            m.part("horn_small_" + id, "head", s * 3.5, -2.5, -5, 0.6, 0, s * 0.9).box(-0.5, -3, -0.5, 1, 3, 1, 0.1, "horn")
                    .box(-0.5, -5, -0.5, 1, 2, 1, -0.1, "horn");
            // Жвалы: толстые, с зелёными светящимися кончиками.
            m.part("fang_" + id, "head", s * 1.8, 1.5, -5.5, -0.5, 0, s * 0.15)
                    .box(-1, 0, -1, 2, 4, 2, 0.1, "fang")
                    .box(-0.5, 4, -0.5, 1, 2, 1, 0.1, "fang_tip");
            // Педипальпы — короткие мохнатые щупики по бокам морды.
            m.part("palp_" + id, "head", s * 3.8, 1.5, -5, -0.9, -s * 0.4, 0)
                    .box(-1, 0, -1, 2, 5, 2, 0.1, "fur_short").box(-1, 0, -1, 2, 5, 2, 0.45, "shag")
                    .centered(0, 5, 0, 2, 2, 2, 0.2, "cuff");
        }
        // Брюшко: тёмно-бордовое под мехом, чуть задрано; на нём высокий кокон.
        Part abdomen = m.part("abdomen", "body", 0, -1, 3.5, 0.2, 0, 0);
        abdomen.box(-8, -6, 0, 16, 12, 18, 0, "maroon").box(-8, -6, 0, 16, 12, 18, 0.7, "shag")
                .box(-7, 5.6, 1, 14, 1, 16, 0, "maroon")
                .box(-2, -1, 17.5, 4, 3, 2, 0, "chitin")
                .box(-1, 0, 19, 2, 2, 1, 0, "fang_tip");
        abdomen.box(-7, -5, -1.5, 14, 9, 3, 0.3, "cuff").box(-7, -5, -1.5, 14, 9, 3, 1.0, "cuff_shag");
        // Кокон стоит на брюшке, наклонён вперёд: панцирь из ярусов, зелёная кромка по краю.
        Part shell = m.part("shell", "abdomen", 0, -6, 9, -0.35, 0, 0);
        shell.box(-8.5, -3, -9, 17, 3, 18, 0, "shell_rim")
                .box(-9, -1.5, -8.5, 1, 2, 17, 0, "rim")
                .box(8, -1.5, -8.5, 1, 2, 17, 0, "rim");
        Part shellTop = m.part("shell_top", "shell", 0, 0, 0);
        shellTop.box(-7.5, -7, -8, 15, 4, 16, 0, "shell")
                .box(-6.5, -11, -7, 13, 4, 14, 0, "shell")
                .box(-5, -14, -5.5, 10, 3, 11, 0, "shell")
                .box(-3, -16, -3.5, 6, 2, 7, 0, "shell")
                .box(-0.5, -16.5, -8, 1, 13, 1, 0, "rim")
                .box(-8, -7.5, -0.5, 1, 4, 1, 0, "rim").box(7, -7.5, -0.5, 1, 4, 1, 0, "rim");
        // Под коконом — светящееся нутро (видно, когда кокон расколот).
        m.part("core", "shell", 0, 0, 0)
                .box(-7, -5, -7, 14, 2, 14, 0, "core")
                .box(-5, -7, -5, 10, 2, 10, 0, "core")
                .box(-6, -8, -6, 2, 5, 2, 0, "shard").box(4, -7, -3, 2, 4, 3, 0, "shard")
                .box(-2, -8, 5, 3, 5, 2, 0, "shard").box(1.5, -9, -6.5, 2, 6, 2, 0, "shard")
                .box(-7, -6, 2, 2, 3, 3, 0, "shard");
        // Яйца: гроздь на правом боку и макушке кокона вылупится во второй фазе, гроздь слева и сзади — в третьей.
        Part eggsA = m.part("eggs_a", "abdomen", 0, 0, 0);
        egg(eggsA, -10, -9, 3, 5, "egg_black");
        egg(eggsA, -10.5, -13, 7, 5, "egg_glow");
        egg(eggsA, -10, -8.5, 9, 5, "egg_black");
        egg(eggsA, -9.5, -12, 12.5, 4, "egg_black");
        egg(eggsA, -8, -17, 4, 4, "egg_glow");
        egg(eggsA, -3, -23, 7, 5, "egg_glow");
        egg(eggsA, 2, -22, 9.5, 4, "egg_black");
        spiderling(eggsA, -11, -16, 7.5, 0);
        spiderling(eggsA, -3, -26, 7.5, 0);
        spiderling(eggsA, -12.5, -11, 3.5, 0);
        Part eggsB = m.part("eggs_b", "abdomen", 0, 0, 0);
        egg(eggsB, 10, -9, 4, 5, "egg_glow");
        egg(eggsB, 10.5, -13, 8.5, 4, "egg_black");
        egg(eggsB, 9.5, -8.5, 11, 5, "egg_black");
        egg(eggsB, 8, -16, 12, 4, "egg_glow");
        egg(eggsB, -3, -9, 18.5, 5, "egg_glow");
        egg(eggsB, 3, -10, 18, 4, "egg_black");
        egg(eggsB, 6, -20, 6, 4, "egg_glow");
        spiderling(eggsB, 11.5, -12, 4.5, 0);
        spiderling(eggsB, -3, -12, 19, 0);
        spiderling(eggsB, 8.5, -19, 12.5, 0);
        Leg[] l = {
                new Leg(-6.5, 1.0, 15, 17, 6, 5, 4, 3, 0.5, 1.35, 4),
                new Leg(-3.5, 0.35, 14, 16, 6, 5, 4, 3, 0.45, 1.35, 4),
                new Leg(-0.5, -0.3, 14, 16, 6, 5, 4, 3, 0.45, 1.35, 4),
                new Leg(2.5, -0.95, 15, 17, 6, 5, 4, 3, 0.5, 1.35, 4),
        };
        for (int i = 0; i < 4; i++) legs(m, "body", i + 1, 5.5, 0.5, 24 - y, l[i], true);
        return m;
    }

    /** Паук выводка: маленькая копия матери — мех, бежевые пучки, зелёные рожки и глаза, красные кончики лап. */
    static Model broodSpider() {
        Model m = new Model("brood_spider", 128);
        double y = 18.5;
        Part body = m.part("body", "root", 0, y, 0);
        body.furry(-3, -2, -4, 6, 4, 6, "fur", 0.4).box(-2.5, -2.7, -3.5, 5, 1, 5, 0, "chitin");
        Part head = m.part("head", "body", 0, 0, -4);
        head.furry(-2.5, -2, -4, 5, 4, 4, "fur", 0.35).box(-2, -2.5, -4.2, 4, 1, 3, 0, "chitin");
        eyes(head, -4.3, -2.3, 1, 1, 1.0);
        for (int s : new int[]{-1, 1}) {
            String id = s < 0 ? "r" : "l";
            m.part("horn_" + id, "head", s * 1.4, -2.2, -3, 0.6, 0, s * 0.3).box(-0.5, -3, -0.5, 1, 3, 1, 0.1, "horn")
                    .box(-0.5, -4.5, -0.5, 1, 2, 1, -0.15, "horn");
            m.part("fang_" + id, "head", s * 1.1, 1.2, -3.6, -0.5, 0, s * 0.15).box(-0.5, 0, -0.5, 1, 3, 1, 0.1, "fang_tip");
        }
        Part abdomen = m.part("abdomen", "body", 0, -0.5, 2, 0.3, 0, 0);
        abdomen.furry(-4, -3.5, 0, 8, 7, 9, "fur", 0.5)
                .box(-1.5, -4.6, 1, 3, 1, 7, 0.2, "cuff").box(-1.5, -4.6, 1, 3, 1, 7, 0.7, "cuff_shag")
                .box(-3, -3, 8.6, 2, 2, 1, -0.2, "egg_glow").box(1, -3, 8.6, 2, 2, 1, -0.2, "egg_glow")
                .box(-3.5, 3.1, 1, 7, 1, 7, 0, "belly");
        abdomen.box(-3.5, -3.5, -1, 7, 5, 2, 0.2, "cuff").box(-3.5, -3.5, -1, 7, 5, 2, 0.6, "cuff_shag");
        Leg[] l = {
                new Leg(-3.5, 1.0, 7, 8, 4, 3, 2, 2, 0.5, 1.35, 3),
                new Leg(-2, 0.35, 6, 8, 4, 3, 2, 2, 0.45, 1.35, 3),
                new Leg(-0.5, -0.3, 6, 8, 4, 3, 2, 2, 0.45, 1.35, 3),
                new Leg(1, -0.95, 7, 8, 4, 3, 2, 2, 0.5, 1.35, 3),
        };
        for (int i = 0; i < 4; i++) legs(m, "body", i + 1, 2.6, 0, 24 - y, l[i], false);
        return m;
    }

    /** Паук-плевальщик: поджарый, брюшко — огромный светящийся мешок с ядом в шипах, высоко задран. */
    static Model venomSpitter() {
        Model m = new Model("venom_spitter", 128);
        double y = 17.5;
        Part body = m.part("body", "root", 0, y, 0);
        body.furry(-3, -2, -4, 6, 4, 6, "fur", 0.4).box(-2.5, -2.8, -3.5, 5, 1, 5, 0, "chitin");
        Part head = m.part("head", "body", 0, -0.3, -4);
        head.furry(-2.5, -2, -4, 5, 4, 4, "fur", 0.35).box(-2, -2.6, -4.2, 4, 1, 3, 0, "chitin")
                .box(-3.5, -3, -1.5, 7, 3, 1, 0.1, "cuff").box(-3.5, -3, -1.5, 7, 3, 1, 0.6, "cuff_shag")
                .box(-1, 0.8, -4.4, 2, 1, 1, 0, "sac");
        eyes(head, -4.3, -2.4, 1, 1, 1.0);
        for (int s : new int[]{-1, 1}) {
            String id = s < 0 ? "r" : "l";
            m.part("horn_" + id, "head", s * 1.6, -2.3, -3, 0.2, 0, s * 0.6).box(-0.5, -3, -0.5, 1, 3, 1, 0.05, "horn")
                    .box(-0.5, -5, -0.5, 1, 2, 1, -0.15, "horn");
            m.part("fang_" + id, "head", s * 1.1, 1.2, -3.6, -0.7, 0, s * 0.25).box(-0.5, 0, -0.5, 1, 3, 1, 0.1, "fang_tip");
        }
        Part abdomen = m.part("abdomen", "body", 0, -1, 2, 0.75, 0, 0);
        abdomen.box(-3, -3, -1, 6, 5, 3, 0.2, "cuff").box(-3, -3, -1, 6, 5, 3, 0.6, "cuff_shag")
                .furry(-3.5, -3, 0, 7, 6, 4, "fur", 0.4);
        Part sac = m.part("sac", "abdomen", 0, -0.5, 3.5);
        sac.box(-4.5, -4.5, 0, 9, 9, 10, 0, "sac")
                .box(-3.5, -5.5, 1, 7, 1, 8, 0, "sac")
                .box(-3.5, -3.5, 10, 7, 7, 1, 0, "sac")
                .box(-0.5, -0.5, 11, 1, 1, 1, 0, "fang_tip");
        for (double[] sp : new double[][]{{-3, -6.5, 2}, {2, -6.5, 3}, {-1, -7, 6}, {2.5, -6, 7.5}, {-3.5, -6, 8}, {-5.5, -3, 4}, {4.5, -3, 5}, {-5.5, -2, 8}, {4.5, -1, 8.5}}) {
            sac.box(sp[0], sp[1], sp[2], 1, 2, 1, 0, "spike");
        }
        Leg[] l = {
                new Leg(-3.5, 1.0, 7, 9, 4, 3, 2, 2, 0.55, 1.35, 3),
                new Leg(-2, 0.35, 6, 9, 4, 3, 2, 2, 0.5, 1.35, 3),
                new Leg(-0.5, -0.3, 6, 9, 4, 3, 2, 2, 0.5, 1.35, 3),
                new Leg(1, -0.95, 7, 9, 4, 3, 2, 2, 0.55, 1.35, 3),
        };
        for (int i = 0; i < 4; i++) legs(m, "body", i + 1, 2.6, 0, 24 - y, l[i], false);
        return m;
    }

    /** Паук-толстобрюх: широкий, низкий, брюшко втрое больше головогруди и укрыто тяжёлыми пластинами панциря. */
    static Model broodTank() {
        Model m = new Model("brood_tank", 256);
        double y = 15.5;
        Part body = m.part("body", "root", 0, y, 0);
        body.furry(-5, -3, -6, 10, 6, 9, "fur", 0.6)
                .box(-4.5, -4.2, -5.5, 9, 2, 8, 0, "plate")
                .box(-4.5, 2.5, -5.5, 9, 1, 8, 0, "belly");
        Part head = m.part("head", "body", 0, 0.5, -6);
        head.furry(-3.5, -2.5, -5, 7, 5, 5, "fur", 0.5).box(-4, -3.4, -5.4, 8, 2, 5, 0, "plate")
                .box(-3, 1.5, -5.4, 6, 1, 1, 0, "chitin");
        eyes(head, -5.5, -3.2, 1, 1, 1.4);
        for (int s : new int[]{-1, 1}) {
            String id = s < 0 ? "r" : "l";
            m.part("horn_" + id, "head", s * 2.8, -3, -3.5, 0.2, 0, s * 0.9).box(-1, -3, -1, 2, 3, 2, 0, "horn_base")
                    .box(-0.5, -5, -0.5, 1, 2, 1, 0.1, "horn");
            m.part("fang_" + id, "head", s * 1.7, 1.6, -4.5, -0.45, 0, s * 0.1)
                    .box(-1, 0, -1, 2, 3, 2, 0.1, "fang").box(-0.5, 3, -0.5, 1, 2, 1, 0.1, "fang_tip");
        }
        Part abdomen = m.part("abdomen", "body", 0, -0.5, 2.5, 0.12, 0, 0);
        abdomen.box(-6, -5, -1.5, 12, 8, 3, 0.3, "cuff").box(-6, -5, -1.5, 12, 8, 3, 0.9, "cuff_shag")
                .furry(-8, -6, 0, 16, 12, 16, "fur", 0.8)
                .box(-7, 5.4, 1, 14, 1, 14, 0, "belly");
        // Пластины панциря внахлёст, между ними бежевые гребни меха.
        Part plates = m.part("plates", "abdomen", 0, 0, 0);
        plates.box(-8.5, -7.5, 0.5, 17, 3, 5, 0, "plate")
                .box(-8.5, -7.8, 5.5, 17, 3, 5, 0, "plate")
                .box(-8, -7.5, 10.5, 16, 3, 5, 0, "plate")
                .box(-7, -8.6, 4.5, 14, 1, 2, 0.2, "cuff").box(-7, -8.6, 4.5, 14, 1, 2, 0.7, "cuff_shag")
                .box(-7, -8.6, 9.5, 14, 1, 2, 0.2, "cuff").box(-7, -8.6, 9.5, 14, 1, 2, 0.7, "cuff_shag")
                .box(-9.2, -5, 2, 1, 4, 12, 0, "plate").box(8.2, -5, 2, 1, 4, 12, 0, "plate")
                .box(-1, -9.5, 2, 2, 2, 2, 0, "spike").box(-1, -9.8, 7, 2, 2, 2, 0, "spike").box(-1, -9.5, 12, 2, 2, 2, 0, "spike");
        Leg[] l = {
                new Leg(-5, 1.0, 10, 11, 5, 4, 3, 2, 0.5, 1.4, 5),
                new Leg(-2.5, 0.35, 9, 11, 5, 4, 3, 2, 0.45, 1.4, 5),
                new Leg(0, -0.3, 9, 11, 5, 4, 3, 2, 0.45, 1.4, 5),
                new Leg(2.5, -0.95, 10, 11, 5, 4, 3, 2, 0.5, 1.4, 5),
        };
        for (int i = 0; i < 4; i++) legs(m, "body", i + 1, 4.5, 0, 24 - y, l[i], true);
        return m;
    }

    /** Кладка на полу: три яйца на подстилке из паутины, в одном шевелится паучонок. */
    static Model eggSac() {
        Model m = new Model("spider_egg_sac", 64);
        Part body = m.part("body", "root", 0, 24, 0);
        body.box(-6, -1, -6, 12, 1, 12, 0, "web")
                .box(-4.5, -6, -3.5, 4, 5, 4, 0, "egg_glow")
                .box(0.5, -5, -4, 4, 4, 4, 0, "egg_black")
                .box(-2, -5, 0.5, 4, 4, 4, 0, "egg_glow")
                .box(-1.5, -9, -2, 3, 3, 3, 0, "egg_black");
        spiderling(body, -2.5, -6, -2, 0);
        return m;
    }

    // =====================================================================================
    // Текстуры
    // =====================================================================================

    static BufferedImage[] paint(Model m) {
        int w = m.texW * R, h = m.texH * R;
        BufferedImage base = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        BufferedImage glow = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(m.name.hashCode());
        for (Part p : m.parts) {
            for (int i = 0; i < p.boxes.size(); i++) {
                Box b = p.boxes.get(i);
                int u = p.uv.get(i)[0], v = p.uv.get(i)[1];
                // Грани: верх, низ, правая, перед, левая, зад. Первая ось грани — её ширина, вторая — высота.
                face(base, glow, b, "top", u + b.d, v, b.w, b.d, b.w, b.d, rnd);
                face(base, glow, b, "bottom", u + b.d + b.w, v, b.w, b.d, b.w, b.d, rnd);
                face(base, glow, b, "side", u, v + b.d, b.d, b.h, b.d, b.h, rnd);
                face(base, glow, b, "front", u + b.d, v + b.d, b.w, b.h, b.w, b.h, rnd);
                face(base, glow, b, "side", u + b.d + b.w, v + b.d, b.d, b.h, b.d, b.h, rnd);
                face(base, glow, b, "back", u + 2 * b.d + b.w, v + b.d, b.w, b.h, b.w, b.h, rnd);
            }
        }
        return new BufferedImage[]{base, glow};
    }

    static final int[] FUR = {24, 34, 62}, FUR_DARK = {8, 11, 22}, FUR_SHEEN = {70, 98, 150};
    static final int[] CUFF = {200, 164, 106}, CUFF_LIGHT = {240, 220, 166}, CUFF_DARK = {128, 94, 52};
    static final int[] GREEN = {120, 255, 110}, GREEN_DEEP = {30, 150, 70}, TEAL = {70, 245, 205}, TEAL_DEEP = {16, 120, 110};

    /**
     * Одна грань: материал по пикселям. long — направление волосков вдоль большей стороны грани.
     * Пиксели без цвета остаются прозрачными (накидки меха), в светящийся слой пишется только свечение.
     */
    static void face(BufferedImage base, BufferedImage glow, Box b, String kind, int u, int v, int fw, int fh, int aw, int ah, Random rnd) {
        int x0 = u * R, y0 = v * R, pw = fw * R, ph = fh * R;
        if (pw <= 0 || ph <= 0) return;
        long seed = rnd.nextLong();
        boolean alongU = aw >= ah;
        for (int x = 0; x < pw; x++) {
            for (int y = 0; y < ph; y++) {
                double fu = (x + 0.5) / pw, fv = (y + 0.5) / ph;
                // Координата вдоль волоска и поперёк.
                double along = alongU ? x : y, across = alongU ? y : x;
                double shade = kind.equals("top") ? 1.12 : kind.equals("bottom") ? 0.6 : 1.05 - 0.35 * fv;
                int[] c = null;
                int[] g = null;
                switch (b.mat) {
                    case "fur", "fur_short" -> {
                        double strand = hash(seed, (int) across, 7);
                        double streak = noise(seed + 3, along * 0.35 + strand * 9, across * 1.0);
                        double t = 0.55 + streak * 0.55 + (strand - 0.5) * 0.25;
                        c = shadeFur(t * shade);
                    }
                    case "mane" -> {
                        // Золотисто-бежевая шерсть бёдер: пряди вдоль лапы, тёмные корни.
                        double strand = hash(seed, (int) across, 7);
                        double streak = noise(seed + 3, along * 0.3 + strand * 9, across * 0.9);
                        c = ramp(CUFF_DARK, CUFF, CUFF_LIGHT, (0.6 + streak * 0.6 + (strand - 0.5) * 0.3) * shade);
                    }
                    case "maroon" -> {
                        double strand = hash(seed, (int) across, 7);
                        double streak = noise(seed + 3, along * 0.35 + strand * 9, across);
                        c = ramp(new int[]{22, 6, 12}, new int[]{70, 16, 28}, new int[]{120, 40, 60}, (0.55 + streak * 0.55) * shade);
                    }
                    case "shag" -> {
                        // Пряди: у каждой своя длина и сдвиг; между ними просвет.
                        int lane = (int) across;
                        double len = 3 + hash(seed, lane, 1) * 6;
                        double off = hash(seed, lane, 2) * 10;
                        double pos = (along + off) % (len + 2);
                        boolean hair = pos < len && hash(seed, lane, 3) > 0.35;
                        boolean edge = (x == 0 || y == 0 || x == pw - 1 || y == ph - 1) && hash(seed, x * 31 + y, 5) > 0.45;
                        if (hair || edge) {
                            double t = 0.7 + hash(seed, lane, 4) * 0.6 - pos / (len + 2) * 0.3;
                            c = shadeFur(t * shade);
                        }
                    }
                    case "cuff" -> {
                        double n = noise(seed, x * 0.6, y * 0.6) * 0.7 + noise(seed + 1, x * 1.7, y * 1.7) * 0.3;
                        c = ramp(CUFF_DARK, CUFF, CUFF_LIGHT, n * shade);
                    }
                    case "cuff_shag" -> {
                        double n = noise(seed, x * 0.9, y * 0.9);
                        if (n > 0.42 || hash(seed, x, y) > 0.8) c = ramp(CUFF_DARK, CUFF, CUFF_LIGHT, (0.5 + n * 0.7) * shade);
                    }
                    case "tip" -> {
                        // Красный с переливом в малиновый и оранжевый, глянцевый блик вдоль лапки.
                        double t = (along / Math.max(1, alongU ? pw : ph));
                        int[] a = {150, 16, 28}, mid = {225, 46, 44}, hi = {255, 140, 70};
                        int[] irid = lerp(new int[]{190, 30, 120}, new int[]{255, 120, 40}, 0.5 + 0.5 * Math.sin(t * 9 + across));
                        c = lerp(ramp(a, mid, hi, (0.7 + 0.4 * (1 - fv)) * shade), irid, 0.3);
                        if (Math.abs(across - (alongU ? ph : pw) * 0.3) < 0.8) c = lerp(c, new int[]{255, 210, 200}, 0.45);
                    }
                    case "chitin" -> {
                        double n = noise(seed, x * 0.4, y * 0.4);
                        c = ramp(new int[]{12, 10, 20}, new int[]{32, 26, 48}, new int[]{86, 70, 120}, (0.7 + n * 0.5) * shade);
                    }
                    case "plate" -> {
                        double n = noise(seed, x * 0.3, y * 0.3);
                        c = ramp(new int[]{14, 11, 22}, new int[]{40, 30, 58}, new int[]{96, 80, 130}, (0.7 + n * 0.45) * shade);
                        boolean rim = x < R || y < R || x >= pw - R || y >= ph - R;
                        if (rim) c = ramp(CUFF_DARK, CUFF, CUFF_LIGHT, 0.9 * shade);
                        else if (hash(seed, x / 3, y / 3) > 0.93) c = lerp(c, new int[]{140, 120, 180}, 0.5);
                    }
                    case "belly" -> {
                        double n = noise(seed, x * 0.5, y * 0.5);
                        c = shadeFur((0.5 + n * 0.4) * shade);
                        if (((int) (across / R)) % 3 == 1 && n > 0.35) {
                            c = lerp(c, GREEN_DEEP, 0.6);
                            g = scale(GREEN_DEEP, 0.6);
                        }
                    }
                    case "shell", "shell_rim" -> {
                        double n = noise(seed, x * 0.25, y * 0.25);
                        c = ramp(new int[]{30, 12, 48}, new int[]{66, 30, 94}, new int[]{112, 62, 150}, (0.65 + n * 0.55) * shade);
                        // Белые завитки и точки.
                        double sx = x / (double) R, sy = y / (double) R;
                        double swirl = Math.sin(sx * 0.9 + Math.sin(sy * 0.55 + seed % 7) * 2.6) * Math.cos(sy * 0.7 - sx * 0.2);
                        if (Math.abs(swirl) < 0.09) c = lerp(c, new int[]{236, 230, 246}, 0.9);
                        else if (hash(seed, (int) sx, (int) sy) > 0.965) c = lerp(c, new int[]{220, 214, 236}, 0.8);
                        boolean edge = b.mat.equals("shell_rim") && (kind.equals("top") ? (x < R || y < R || x >= pw - R || y >= ph - R) : y >= ph - R);
                        if (edge) {
                            c = GREEN;
                            g = GREEN;
                        }
                    }
                    case "rim" -> {
                        c = lerp(GREEN_DEEP, GREEN, 0.6 + 0.4 * noise(seed, x * 0.5, y * 0.5));
                        g = c;
                    }
                    case "egg_black" -> {
                        double dx = fu - 0.35, dy = fv - 0.3;
                        double hi = Math.max(0, 1 - Math.sqrt(dx * dx + dy * dy) * 3.2);
                        c = lerp(new int[]{10, 10, 15}, new int[]{90, 100, 125}, hi * hi);
                        if (hash(seed, x, y) > 0.97) {
                            c = GREEN_DEEP;
                            g = scale(GREEN_DEEP, 0.7);
                        }
                    }
                    case "egg_glow" -> {
                        double cx = fu - 0.5, cy = fv - 0.5;
                        double r = Math.sqrt(cx * cx + cy * cy) * 2;
                        double vein = Math.abs(Math.sin((cx * 7 + Math.sin(cy * 9) * 0.6) * 2));
                        c = lerp(new int[]{200, 255, 240}, TEAL, Math.min(1, r * 1.1));
                        if (vein < 0.12) c = TEAL_DEEP;
                        g = vein < 0.12 ? scale(TEAL_DEEP, 0.6) : scale(c, 0.9);
                    }
                    case "spiderling" -> {
                        c = lerp(TEAL, new int[]{190, 255, 245}, hash(seed, x, y) * 0.4);
                        g = c;
                    }
                    case "eye" -> {
                        double cx = fu - 0.5, cy = fv - 0.5;
                        double r = Math.sqrt(cx * cx + cy * cy) * 2;
                        c = r < 0.35 ? new int[]{230, 255, 210} : lerp(GREEN, GREEN_DEEP, Math.max(0, r - 0.4));
                        g = c;
                    }
                    case "horn", "horn_base" -> {
                        double t = b.mat.equals("horn") ? 0.6 + 0.4 * (1 - fv) : 0.2 + 0.4 * (1 - fv);
                        c = lerp(GREEN_DEEP, GREEN, t);
                        if (hash(seed, x / 2, y / 2) > 0.85) c = lerp(c, new int[]{220, 255, 200}, 0.5);
                        g = scale(c, b.mat.equals("horn") ? 1.0 : 0.55);
                    }
                    case "fang" -> {
                        c = ramp(new int[]{10, 10, 18}, new int[]{30, 30, 44}, new int[]{70, 80, 100}, (0.7 + 0.5 * (1 - fv)) * shade);
                        if (fv > 0.75) {
                            c = lerp(c, GREEN, (fv - 0.75) * 4);
                            g = scale(GREEN, (fv - 0.75) * 4);
                        }
                    }
                    case "fang_tip" -> {
                        c = lerp(GREEN, new int[]{220, 255, 200}, fv * 0.6);
                        g = c;
                    }
                    case "core" -> {
                        double n = noise(seed, x * 0.35, y * 0.35);
                        c = lerp(new int[]{14, 50, 26}, new int[]{40, 120, 50}, n);
                        double vein = Math.abs(noise(seed + 9, x * 0.18, y * 0.18) - 0.5);
                        if (vein < 0.06) {
                            c = new int[]{170, 255, 150};
                            g = c;
                        } else g = scale(c, 0.5);
                    }
                    case "shard" -> {
                        c = ramp(new int[]{30, 12, 48}, new int[]{66, 30, 94}, new int[]{112, 62, 150}, 0.9 * shade);
                        if (fv > 0.7 || x < R) {
                            c = GREEN;
                            g = GREEN;
                        }
                    }
                    case "sac" -> {
                        double n = noise(seed, x * 0.3, y * 0.3);
                        c = lerp(new int[]{40, 150, 40}, new int[]{170, 255, 110}, n);
                        double vein = Math.abs(noise(seed + 4, x * 0.15, y * 0.15) - 0.5);
                        if (vein < 0.05) c = new int[]{18, 70, 20};
                        if (hash(seed, x / 2, y / 2) > 0.94) c = new int[]{230, 255, 200};
                        g = scale(c, vein < 0.05 ? 0.2 : 0.75);
                    }
                    case "spike" -> c = ramp(new int[]{10, 10, 18}, new int[]{36, 30, 50}, new int[]{90, 80, 110}, (0.6 + 0.6 * (1 - fv)) * shade);
                    case "web" -> {
                        double cx = fu - 0.5, cy = fv - 0.5;
                        double ang = Math.atan2(cy, cx), r = Math.sqrt(cx * cx + cy * cy);
                        boolean spoke = Math.abs(Math.sin(ang * 4)) < 0.08;
                        boolean ring = Math.abs(Math.sin(r * 40)) < 0.15 && r < 0.5;
                        if (kind.equals("top") && (spoke || ring) && r < 0.52) c = new int[]{226, 230, 236};
                        else if (!kind.equals("top")) c = null;
                    }
                    default -> c = new int[]{255, 0, 255};
                }
                if (c != null) put(base, x0 + x, y0 + y, c);
                if (g != null) put(glow, x0 + x, y0 + y, g);
                else if (c != null) put(glow, x0 + x, y0 + y, new int[]{0, 0, 0});
            }
        }
    }

    static int[] shadeFur(double t) {
        return ramp(FUR_DARK, FUR, FUR_SHEEN, t);
    }

    /** t: 0 — тёмный, 1 — основной, 1.4 и выше — светлый. */
    static int[] ramp(int[] dark, int[] mid, int[] light, double t) {
        if (t < 1) return lerp(dark, mid, Math.max(0, (t - 0.3) / 0.7));
        return lerp(mid, light, Math.min(1, (t - 1) / 0.4));
    }

    static int[] lerp(int[] a, int[] b, double k) {
        k = Math.max(0, Math.min(1, k));
        return new int[]{clamp(a[0] + (b[0] - a[0]) * k), clamp(a[1] + (b[1] - a[1]) * k), clamp(a[2] + (b[2] - a[2]) * k)};
    }

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
