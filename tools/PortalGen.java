import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Текстура и модели портала данжа: одна воронка на весь проём 3×4 (каждый блок берёт свою клетку текстуры)
 * и отдельная повторяющаяся плитка для одиночных блоков.
 *
 * Запуск из корня проекта (нужна JDK 21):
 *   java tools/PortalGen.java src/main/resources
 */
public class PortalGen {
    static final String MODID = "heroicpaths";
    /** Пикселей на блок. */
    static final int CELL = 64;
    static final int W = 3, H = 4;
    static final int FRAMES = 32;

    public static void main(String[] args) throws IOException {
        Path resources = Path.of(args.length > 0 ? args[0] : "src/main/resources");
        Path textures = resources.resolve("assets/" + MODID + "/textures/block");
        Path models = resources.resolve("assets/" + MODID + "/models/block");
        Path states = resources.resolve("assets/" + MODID + "/blockstates");

        writeStrip(textures.resolve("dungeon_portal_gate.png"), W * CELL, H * CELL, false);
        writeText(textures.resolve("dungeon_portal_gate.png.mcmeta"), mcmeta(W * CELL, H * CELL));
        writeStrip(textures.resolve("dungeon_portal.png"), CELL, CELL, true);
        writeText(textures.resolve("dungeon_portal.png.mcmeta"), mcmeta(CELL, CELL));

        StringBuilder variants = new StringBuilder("{\n  \"variants\": {\n");
        for (String axis : new String[]{"x", "z"}) {
            for (int part = 0; part <= W * H; part++) {
                String name = "dungeon_portal_" + axis + "_" + part;
                writeText(models.resolve(name + ".json"), model(axis.equals("x"), part));
                variants.append("    \"axis=").append(axis).append(",part=").append(part).append("\": { \"model\": \"")
                        .append(MODID).append(":block/").append(name).append("\" }");
                variants.append(axis.equals("z") && part == W * H ? "\n" : ",\n");
            }
        }
        variants.append("  }\n}\n");
        writeText(states.resolve("dungeon_portal.json"), variants.toString());
        Files.deleteIfExists(models.resolve("dungeon_portal_ns.json"));
        Files.deleteIfExists(models.resolve("dungeon_portal_ew.json"));
        System.out.println("Portal textures and " + 2 * (W * H + 1) + " models written");
    }

    static String mcmeta(int w, int h) {
        return "{\n  \"animation\": {\n    \"frametime\": 2,\n    \"interpolate\": true,\n    \"width\": " + w + ",\n    \"height\": " + h + "\n  }\n}\n";
    }

    /**
     * Плоскость в середине блока, видна с обеих сторон, светится сама. Клетка текстуры: столбец part % 3, ряд part / 3
     * снизу. С одной из сторон левый край зрителя — это последний столбец, поэтому там столбец зеркалится,
     * и узор сходится с обеих сторон проёма.
     */
    static String model(boolean alongX, int part) {
        boolean lone = part == W * H;
        String texture = lone ? "dungeon_portal" : "dungeon_portal_gate";
        double cw = 16.0 / W, ch = 16.0 / H;
        int col = part % W, row = part / W;
        double v1 = lone ? 0 : (H - 1 - row) * ch, v2 = lone ? 16 : v1 + ch;
        // Вдоль X: юг смотрит на север, левый край — запад (столбец 0); с севера левый край — восток.
        // Вдоль Z: запад смотрит на восток, левый край — север (столбец 0); с востока левый край — юг.
        String front = uv(lone, col, false, cw, v1, v2), back = uv(lone, col, true, cw, v1, v2);
        String faces = alongX
                ? "\"south\": { " + front + ", \"texture\": \"#portal\" },\n        \"north\": { " + back + ", \"texture\": \"#portal\" }"
                : "\"east\": { " + back + ", \"texture\": \"#portal\" },\n        \"west\": { " + front + ", \"texture\": \"#portal\" }";
        String from = alongX ? "[0, 0, 7.5]" : "[7.5, 0, 0]", to = alongX ? "[16, 16, 8.5]" : "[8.5, 16, 16]";
        return """
                {
                  "render_type": "minecraft:translucent",
                  "ambientocclusion": false,
                  "textures": {
                    "particle": "%s:block/%s",
                    "portal": "%s:block/%s"
                  },
                  "elements": [
                    {
                      "from": %s,
                      "to": %s,
                      "shade": false,
                      "neoforge_data": { "block_light": 15, "sky_light": 15 },
                      "faces": {
                        %s
                      }
                    }
                  ]
                }
                """.formatted(MODID, texture, MODID, texture, from, to, faces);
    }

    static String uv(boolean lone, int col, boolean mirrored, double cw, double v1, double v2) {
        if (lone) return "\"uv\": [0, 0, 16, 16]";
        int c = mirrored ? W - 1 - col : col;
        return "\"uv\": [" + fmt(c * cw) + ", " + fmt(v1) + ", " + fmt((c + 1) * cw) + ", " + fmt(v2) + "]";
    }

    static String fmt(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.4f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    // ---------------------------------------------------------------- рисунок

    record Star(double u, double v, double phase, double size) {}

    static void writeStrip(Path path, int w, int h, boolean tile) throws IOException {
        Random random = new Random(tile ? 7 : 42);
        Star[] stars = new Star[tile ? 10 : 70];
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new Star(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble(), 0.6 + random.nextDouble() * 1.4);
        }
        BufferedImage image = new BufferedImage(w, h * FRAMES, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < FRAMES; f++) {
            double t = (double) f / FRAMES;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    double u = (x + 0.5) / w * 2 - 1, v = (y + 0.5) / h * 2 - 1;
                    image.setRGB(x, f * h + y, tile ? tilePixel(u, v, t, stars, w) : gatePixel(u, v, t, stars, w, h));
                }
            }
        }
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());
    }

    /** Воронка проёма: тёмная бездна, три закрученных бирюзово-фиолетовых рукава, светлое ядро и золотой огненный край. */
    static int gatePixel(double u, double v, double t, Star[] stars, int w, int h) {
        double p = 5;
        double edge = Math.pow(Math.pow(Math.abs(u), p) + Math.pow(Math.abs(v), p), 1 / p); // 1 на краю проёма
        double ru = u * w / (double) h, rv = v; // круглые рукава при вытянутом проёме
        double r = Math.sqrt(ru * ru + rv * rv);
        double theta = Math.atan2(rv, ru);
        double[] c = swirl(r, theta, t);

        // Звёзды-искры, мерцают и медленно втягиваются к центру.
        for (Star s : stars) {
            double pull = (t + s.phase) % 1.0;
            double su = s.u * (1 - pull * 0.6), sv = s.v * (1 - pull * 0.6);
            double du = (u - su) * w / 2, dv = (v - sv) * h / 2;
            double d = Math.sqrt(du * du + dv * dv);
            double twinkle = Math.pow(Math.max(0, Math.sin(Math.PI * 2 * (t + s.phase))), 3);
            double glow = Math.max(0, 1 - d / (s.size * 1.6)) * twinkle * (1 - pull);
            add(c, 0.85 * glow, 0.95 * glow, 1.0 * glow);
        }

        // Огненный край: золото у рамки, переходит в багрянец, языки пламени бегут по кругу.
        double flame = 0.5 + 0.5 * Math.sin(theta * 9 + t * Math.PI * 2 * 2) * Math.sin(theta * 4 - t * Math.PI * 2);
        double inner = smooth(0.62, 0.88, edge);
        mix(c, 0.3, 0.08, 0.5, inner * 0.45);
        // Узкое золотое кольцо с языками пламени, тонкая раскалённая нить по нему, за ним — тлеющие угли до рамки.
        double rim = smooth(0.84 - flame * 0.07, 0.93, edge) * (1 - smooth(0.98, 1.06, edge));
        mix(c, 1.0, 0.62 + 0.25 * flame, 0.18, rim);
        double hot = Math.max(0, 1 - Math.abs(edge - 0.95) / 0.025);
        mix(c, 1.0, 0.95, 0.7, hot * 0.75);
        double ember = smooth(0.98, 1.08, edge);
        double glowEmber = 0.5 + 0.5 * Math.sin(theta * 13 - t * Math.PI * 2 * 3);
        mix(c, 0.45 + 0.25 * glowEmber, 0.1 + 0.06 * glowEmber, 0.06, ember);
        double alpha = 0.84 + 0.16 * Math.max(Math.max(rim, ember), smooth(0, 0.2, 1 - r));
        return argb(alpha, c);
    }

    /** Одиночный блок: маленький водоворот, сходящийся на краях, чтобы плитка повторялась без швов. */
    static int tilePixel(double u, double v, double t, Star[] stars, int w) {
        double r = Math.sqrt(u * u + v * v);
        double theta = Math.atan2(v, u);
        double[] c = swirl(r * 1.1, theta, t);
        double fade = smooth(0.75, 1.15, Math.max(Math.abs(u), Math.abs(v)));
        mix(c, 0.22, 0.06, 0.42, fade);
        for (Star s : stars) {
            double du = (u - s.u) * w / 2, dv = (v - s.v) * w / 2;
            double twinkle = Math.pow(Math.max(0, Math.sin(Math.PI * 2 * (t + s.phase))), 3);
            double glow = Math.max(0, 1 - Math.sqrt(du * du + dv * dv) / (s.size * 1.4)) * twinkle;
            add(c, 0.8 * glow, 0.9 * glow, glow);
        }
        return argb(0.85, c);
    }

    /** Цвет воронки на радиусе r (0 — центр) и угле theta в момент t ∈ [0, 1). */
    static double[] swirl(double r, double theta, double t) {
        double logR = Math.log(r + 0.04);
        double turn = t * Math.PI * 2;
        double arms = Math.sin(3 * (theta + 1.9 * logR) + turn * 3);
        double fine = Math.sin(7 * (theta + 2.6 * logR) + turn * 7 + 1.3);
        double arm = Math.pow(0.5 + 0.5 * arms, 2.2);
        double thread = Math.pow(0.5 + 0.5 * fine, 6) * 0.45;
        // Бездна: почти чёрный индиго, к краю — пурпур.
        double[] c = {0.05 + 0.18 * r, 0.02 + 0.03 * r, 0.12 + 0.3 * r};
        double depth = Math.max(0, 1 - Math.abs(r - 0.45) * 1.6);
        add(c, 0.15 * arm * depth, 0.75 * arm * depth, 0.85 * arm * depth);       // бирюзовые рукава
        add(c, 0.55 * arm * (1 - depth), 0.2 * arm * (1 - depth), 0.85 * arm * (1 - depth)); // фиолетовые снаружи
        add(c, 0.6 * thread, 0.85 * thread, 1.0 * thread);
        // Ядро: белое свечение с бирюзой, пульсирует.
        double pulse = 0.85 + 0.15 * Math.sin(turn * 2);
        double core = Math.pow(Math.max(0, 1 - r / 0.32), 2) * pulse;
        add(c, 0.8 * core, 1.0 * core, 0.95 * core);
        return c;
    }

    static double smooth(double a, double b, double x) {
        double k = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return k * k * (3 - 2 * k);
    }

    static void add(double[] c, double r, double g, double b) {
        c[0] += r;
        c[1] += g;
        c[2] += b;
    }

    static void mix(double[] c, double r, double g, double b, double k) {
        c[0] += (r - c[0]) * k;
        c[1] += (g - c[1]) * k;
        c[2] += (b - c[2]) * k;
    }

    static int argb(double alpha, double[] c) {
        int a = (int) Math.round(Math.max(0, Math.min(1, alpha)) * 255);
        int r = (int) Math.round(Math.max(0, Math.min(1, c[0])) * 255);
        int g = (int) Math.round(Math.max(0, Math.min(1, c[1])) * 255);
        int b = (int) Math.round(Math.max(0, Math.min(1, c[2])) * 255);
        return a << 24 | r << 16 | g << 8 | b;
    }

    static void writeText(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }
}
