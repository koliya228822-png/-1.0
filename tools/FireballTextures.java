import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Текстуры огненного шара: кадры шара с языками пламени (зацикленная анимация), мягкий ореол и раскалённое ядро,
 * а также белые (их красит код) частицы шлейфа: мягкое облачко пламени и искры.
 * Запуск: java tools/FireballTextures.java src/main/resources
 */
public class FireballTextures {
    static final int N = 128, FRAMES = 8;

    public static void main(String[] args) throws IOException {
        Path dir = Path.of(args.length > 0 ? args[0] : "src/main/resources").resolve("assets/heroicpaths/textures/entity");
        Files.createDirectories(dir);
        BufferedImage core = new BufferedImage(N, N * FRAMES, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < FRAMES; f++) frame(core, f);
        ImageIO.write(core, "png", dir.resolve("fireball_core.png").toFile());
        ImageIO.write(glow(), "png", dir.resolve("fireball_glow.png").toFile());
        ImageIO.write(hot(), "png", dir.resolve("fireball_hot.png").toFile());
        ImageIO.write(meteor(), "png", dir.resolve("meteor.png").toFile());
        // Магическая стрела жезлов и посохов: те же кадры шара, но белые — цвет стихии задаёт рендер.
        BufferedImage bolt = new BufferedImage(N, N * FRAMES, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < FRAMES; f++) frame(bolt, f);
        whiten(bolt);
        ImageIO.write(bolt, "png", dir.resolve("magic_bolt_core.png").toFile());
        ImageIO.write(whiteGlow(), "png", dir.resolve("magic_bolt_glow.png").toFile());
        Path particles = dir.getParent().resolve("particle");
        Files.createDirectories(particles);
        ImageIO.write(wisp(), "png", particles.resolve("fire_wisp.png").toFile());
        for (int i = 0; i < 4; i++) ImageIO.write(ember(i), "png", particles.resolve("fire_ember_" + i + ".png").toFile());
        System.out.println("fireball textures: " + dir);
    }

    /** Один кадр: край шара рвётся языками пламени, внутри бурлит жар; кадры по кругу во времени — анимация без шва. */
    static void frame(BufferedImage img, int f) {
        double phase = 2 * Math.PI * f / FRAMES, tc = Math.cos(phase) * 1.1, ts = Math.sin(phase) * 1.1;
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                double dx = (x + 0.5) / N * 2 - 1, dy = (y + 0.5) / N * 2 - 1, r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
                double ca = Math.cos(a), sa = Math.sin(a);
                double tongues = 0.6 * fbm(ca * 1.8 + 3, sa * 1.8 + 7, tc, ts, 3) + 0.4 * fbm(ca * 5 + 11, sa * 5 + 2, tc * 1.6, ts * 1.6, 3);
                double radius = 0.5 + 0.42 * Math.pow(tongues, 1.6);
                double h = 1 - r / radius;
                double alpha = smooth(-0.05, 0.2, h);
                if (alpha <= 0) continue;
                double swirl = fbm(dx * 3.2 + 5, dy * 3.2 + 9, tc * 1.3, ts * 1.3, 4);
                double heat = Math.max(0, h) * (0.7 + 0.6 * swirl) + 0.15 * Math.max(0, 1 - r * 2.2);
                int[] c = ramp(Math.min(1, heat));
                img.setRGB(x, y + f * N, (int) Math.round(alpha * 255) << 24 | c[0] << 16 | c[1] << 8 | c[2]);
            }
    }

    /** Ореол для аддитивного свечения: цвет заранее умножен на прозрачность, чёрное по краю ничего не добавляет. */
    static BufferedImage glow() {
        BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                double dx = (x + 0.5) / N * 2 - 1, dy = (y + 0.5) / N * 2 - 1, r = Math.hypot(dx, dy);
                if (r >= 1) continue;
                double k = Math.pow(1 - r, 1.7), i = k * 0.7;
                int red = (int) Math.round(255 * i), g = (int) Math.round((110 + 90 * k) * i), b = (int) Math.round((20 + 30 * k) * i);
                img.setRGB(x, y, (int) Math.round(k * 255) << 24 | red << 16 | g << 8 | b);
            }
        return img;
    }

    /** Перевести кадры в оттенки серого: яркость по жару, прозрачность та же. */
    static void whiten(BufferedImage img) {
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) {
                int argb = img.getRGB(x, y), a = argb >>> 24;
                if (a == 0) continue;
                int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
                double lum = (0.3 * r + 0.5 * g + 0.2 * b) / 255;
                int v = (int) Math.round(255 * Math.min(1, 0.45 + 0.65 * lum));
                img.setRGB(x, y, a << 24 | v << 16 | v << 8 | v);
            }
    }

    /** Белый ореол для аддитивного свечения (цвет заранее умножен на прозрачность). */
    static BufferedImage whiteGlow() {
        BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                double dx = (x + 0.5) / N * 2 - 1, dy = (y + 0.5) / N * 2 - 1, r = Math.hypot(dx, dy);
                if (r >= 1) continue;
                double k = Math.pow(1 - r, 1.7);
                int v = (int) Math.round(255 * k * 0.75);
                img.setRGB(x, y, (int) Math.round(k * 255) << 24 | v << 16 | v << 8 | v);
            }
        return img;
    }

    /** Камень метеора: тёмный базальт, по нему сеть раскалённых трещин и пятна магмы. */
    static BufferedImage meteor() {
        int n = 128;
        java.util.Random random = new java.util.Random(7);
        double[][] cells = new double[18][2];
        for (double[] c : cells) {
            c[0] = random.nextDouble();
            c[1] = random.nextDouble();
        }
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                double u = (x + 0.5) / n, v = (y + 0.5) / n, d1 = 9, d2 = 9;
                for (double[] c : cells) {
                    double d = Math.hypot(u - c[0], v - c[1]);
                    if (d < d1) {
                        d2 = d1;
                        d1 = d;
                    } else if (d < d2) d2 = d;
                }
                double edge = d2 - d1, rock = fbm(u * 6, v * 6, 1.3, 0.7, 4), magma = fbm(u * 3 + 9, v * 3 + 4, 0.2, 2.1, 3);
                double glow = Math.exp(-edge * 40) * 0.95 + smooth(0.62, 0.8, magma) * 0.8;
                double base = 0.09 + 0.13 * rock;
                double r = base * 1.1, g = base * 0.95, b = base * 0.9;
                double hot = Math.min(1, glow), core = Math.exp(-edge * 120);
                r = r + (1.0 - r) * hot;
                g = g + (0.42 + 0.45 * core - g) * hot;
                b = b + (0.06 + 0.35 * core - b) * hot;
                int ri = (int) Math.round(Math.min(1, r) * 255), gi = (int) Math.round(Math.min(1, g) * 255), bi = (int) Math.round(Math.min(1, b) * 255);
                img.setRGB(x, y, 0xFF000000 | ri << 16 | gi << 8 | bi);
            }
        return img;
    }

    /** Раскалённое ядро: бело-жёлтая сердцевина без красного края. */
    static BufferedImage hot() {
        BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                double dx = (x + 0.5) / N * 2 - 1, dy = (y + 0.5) / N * 2 - 1, r = Math.hypot(dx, dy);
                if (r >= 1) continue;
                double k = smooth(0, 1, 1 - r);
                int g = (int) Math.round(200 + 55 * k), b = (int) Math.round(80 + 155 * k);
                img.setRGB(x, y, (int) Math.round(Math.pow(k, 1.3) * 255) << 24 | 255 << 16 | g << 8 | b);
            }
        return img;
    }

    /** Облачко пламени для шлейфа: мягкий белый круг, цвет задаёт частица. */
    static BufferedImage wisp() {
        int n = 32;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1, r = Math.hypot(dx, dy);
                if (r >= 1) continue;
                double k = Math.pow(1 - r, 1.2), w = 215 + 40 * k;
                int v = (int) Math.round(w);
                img.setRGB(x, y, (int) Math.round(k * 255) << 24 | v << 16 | v << 8 | v);
            }
        return img;
    }

    /** Искры: круглая, крестик-блёстка, вытянутая и мелкая — яркий центр и мягкий край. */
    static BufferedImage ember(int variant) {
        int n = 16;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                double dx = (x + 0.5) / n * 2 - 1, dy = (y + 0.5) / n * 2 - 1, r = Math.hypot(dx, dy);
                double k = switch (variant) {
                    case 0 -> Math.max(0, 1 - r / 0.75);
                    case 1 -> Math.max(Math.max(0, 1 - r / 0.4), Math.max(Math.max(0, 1 - Math.abs(dx) / 0.12) * Math.max(0, 1 - Math.abs(dy)),
                            Math.max(0, 1 - Math.abs(dy) / 0.12) * Math.max(0, 1 - Math.abs(dx))));
                    case 2 -> Math.max(0, 1 - Math.hypot(dx / 0.4, dy / 0.9));
                    default -> Math.max(0, 1 - r / 0.45);
                };
                if (k <= 0) continue;
                double a = Math.pow(k, 1.2);
                int v = (int) Math.round(210 + 45 * k);
                img.setRGB(x, y, (int) Math.round(Math.min(1, a * 1.3) * 255) << 24 | v << 16 | v << 8 | v);
            }
        return img;
    }

    static final double[][] STOPS = {
            {0.00, 110, 8, 0}, {0.12, 190, 35, 4}, {0.28, 240, 95, 12}, {0.45, 255, 160, 32},
            {0.65, 255, 215, 95}, {0.85, 255, 248, 200}, {1.00, 255, 255, 245}};

    static int[] ramp(double h) {
        for (int i = 1; i < STOPS.length; i++)
            if (h <= STOPS[i][0]) {
                double[] a = STOPS[i - 1], b = STOPS[i];
                double k = (h - a[0]) / (b[0] - a[0]);
                return new int[]{(int) Math.round(a[1] + (b[1] - a[1]) * k), (int) Math.round(a[2] + (b[2] - a[2]) * k), (int) Math.round(a[3] + (b[3] - a[3]) * k)};
            }
        double[] l = STOPS[STOPS.length - 1];
        return new int[]{(int) l[1], (int) l[2], (int) l[3]};
    }

    static double smooth(double e0, double e1, double x) {
        double k = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return k * k * (3 - 2 * k);
    }

    static double fbm(double x, double y, double z, double w, int octaves) {
        double sum = 0, amp = 0.5, norm = 0;
        for (int o = 0; o < octaves; o++) {
            sum += amp * noise(x, y, z, w);
            norm += amp;
            x *= 2;
            y *= 2;
            z *= 2;
            w *= 2;
            amp *= 0.5;
        }
        return sum / norm;
    }

    /** Значение-шум в 4D: гладкая интерполяция случайных значений в узлах решётки, от 0 до 1. */
    static double noise(double x, double y, double z, double w) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z), w0 = (int) Math.floor(w);
        double fx = fade(x - x0), fy = fade(y - y0), fz = fade(z - z0), fw = fade(w - w0);
        double result = 0;
        for (int c = 0; c < 16; c++) {
            int ix = c & 1, iy = c >> 1 & 1, iz = c >> 2 & 1, iw = c >> 3 & 1;
            double weight = (ix == 1 ? fx : 1 - fx) * (iy == 1 ? fy : 1 - fy) * (iz == 1 ? fz : 1 - fz) * (iw == 1 ? fw : 1 - fw);
            result += weight * hash(x0 + ix, y0 + iy, z0 + iz, w0 + iw);
        }
        return result;
    }

    static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    static double hash(int x, int y, int z, int w) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ w * 0xD6E8FEB86659FD93L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) / (double) (1L << 53);
    }
}
