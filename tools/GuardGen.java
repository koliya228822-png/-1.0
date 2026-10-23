import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * Текстуры стражи деревни (64x64, раскладка GuardRenderer.GuardModel): голова жителя с носом,
 * слой шлема/капюшона, тело, сюрко поверх, руки и ноги.
 *
 * Запуск из корня проекта: java tools/GuardGen.java src/main/resources
 */
public class GuardGen {
    interface Painter {
        int color(String face, int x, int y, int w, int h);
    }

    static BufferedImage img;
    static Random random;

    public static void main(String[] args) throws IOException {
        File dir = new File(args.length > 0 ? args[0] : "src/main/resources", "assets/heroicpaths/textures/entity/guard");
        dir.mkdirs();
        ImageIO.write(guard(false), "png", new File(dir, "guard_swordsman.png"));
        ImageIO.write(guard(true), "png", new File(dir, "guard_archer.png"));
        System.out.println("Wrote guard textures to " + dir.getAbsolutePath());
    }

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Цвет с лёгким шумом и затемнением. */
    static int shade(int color, double k) {
        int n = random.nextInt(13) - 6;
        int r = (int) (((color >> 16) & 255) * k) + n, g = (int) (((color >> 8) & 255) * k) + n, b = (int) ((color & 255) * k) + n;
        return rgb(r, g, b);
    }

    static void box(int u, int v, int w, int h, int d, Painter p) {
        face(u + d, v, w, d, "top", p);
        face(u + d + w, v, w, d, "bottom", p);
        face(u, v + d, d, h, "right", p);
        face(u + d, v + d, w, h, "front", p);
        face(u + d + w, v + d, d, h, "left", p);
        face(u + 2 * d + w, v + d, w, h, "back", p);
    }

    static void face(int u, int v, int w, int h, String name, Painter p) {
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int c = p.color(name, x, y, w, h);
                if (c != 0) img.setRGB(u + x, v + y, c);
            }
        }
    }

    static BufferedImage guard(boolean archer) {
        img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        random = new Random(archer ? 7 : 3);
        int skin = rgb(184, 132, 98), skinDark = rgb(150, 102, 74), hair = archer ? rgb(92, 62, 38) : rgb(58, 42, 30);
        int iron = rgb(168, 172, 180), ironDark = rgb(104, 108, 118);
        int mail = rgb(126, 130, 138);
        int cloth = archer ? rgb(62, 104, 50) : rgb(44, 70, 150), clothDark = archer ? rgb(40, 70, 34) : rgb(28, 44, 100);
        int trim = archer ? rgb(150, 112, 60) : rgb(214, 176, 62);
        int leather = rgb(112, 74, 44), leatherDark = rgb(78, 50, 30);
        int trousers = archer ? rgb(86, 70, 48) : rgb(60, 54, 70);

        // ---------------------------------------------------------------- голова жителя
        box(0, 0, 8, 10, 8, (f, x, y, w, h) -> {
            if (f.equals("top")) return shade(hair, 1.0);
            if (f.equals("bottom")) return shade(skinDark, 1.0);
            if (f.equals("back")) return y < 7 ? shade(hair, 0.95) : shade(skinDark, 1.0);
            if (!f.equals("front")) return y < 3 ? shade(hair, 0.95) : (y < 6 && (x == 0 || x == 7) ? shade(hair, 0.9) : shade(skin, 0.95));
            // Лицо: брови, глаза (белок + зрачок), щёки, рот.
            if (y < 2) return shade(hair, 1.0);
            if (y == 3 && (x == 1 || x == 2 || x == 5 || x == 6)) return shade(hair, 0.8);
            if (y == 4) {
                if (x == 1 || x == 6) return rgb(236, 236, 230);
                if (x == 2 || x == 5) return archer ? rgb(46, 96, 40) : rgb(40, 70, 120);
            }
            if (y == 8 && x >= 2 && x <= 5) return shade(skinDark, 0.8);
            if (y >= 7 && !archer && (x <= 1 || x >= 6)) return shade(hair, 0.85);
            return shade(skin, 1.0);
        });
        // Нос.
        box(24, 0, 2, 4, 2, (f, x, y, w, h) -> shade(skin, f.equals("front") ? 1.0 : 0.9));

        // ---------------------------------------------------------------- шлем или капюшон (внешний слой головы)
        box(32, 0, 8, 10, 8, (f, x, y, w, h) -> {
            if (archer) {
                // Капюшон: закрывает макушку, затылок и бока, лицо открыто.
                int c = shade(cloth, 0.95 - 0.02 * y);
                if (f.equals("top") || f.equals("back")) return c;
                if (f.equals("bottom")) return 0;
                if (f.equals("front")) return y < 2 || ((x == 0 || x == 7) && y < 9) ? shade(clothDark, 1.0) : 0;
                return y < 9 ? c : 0;
            }
            // Шлем-капеллина: железный купол до бровей, полоса-обод, наносник, бармица по бокам и сзади.
            if (f.equals("top")) return shade(iron, (x == 3 || x == 4) ? 1.08 : 1.0);
            if (f.equals("bottom")) return 0;
            if (f.equals("front")) {
                if (y < 2) return shade(iron, 1.0);
                if (y == 2) return shade(ironDark, 1.0);
                if ((x == 3 || x == 4) && y <= 5) return shade(iron, 0.92);
                return 0;
            }
            if (y < 2) return shade(iron, 1.0);
            if (y == 2) return shade(ironDark, 1.0);
            if (y <= 8) return shade(mail, (x + y) % 2 == 0 ? 1.0 : 0.82);
            return 0;
        });

        // ---------------------------------------------------------------- тело: кольчуга или кожаная куртка
        box(16, 20, 8, 12, 4, (f, x, y, w, h) -> {
            if (archer) return shade(y == 7 ? leatherDark : leather, (x + y) % 3 == 0 ? 0.9 : 1.0);
            return shade(mail, (x + y) % 2 == 0 ? 1.0 : 0.8);
        });

        // ---------------------------------------------------------------- сюрко с гербом / плащ-накидка
        box(16, 38, 8, 12, 4, (f, x, y, w, h) -> {
            if (f.equals("top")) return shade(cloth, 1.0);
            if (f.equals("bottom")) return 0;
            if (y == 7) return shade(leatherDark, 1.0); // пояс
            if (y == 7 && x == 3) return shade(trim, 1.0);
            if (archer) {
                if (f.equals("front")) {
                    // Перевязь колчана по диагонали и открытая грудь куртки.
                    if (x == y - 1 || x == y) return shade(leatherDark, 1.0);
                    if (y < 7 && (x == 3 || x == 4)) return 0;
                    if (y > 7 && (x == 3 || x == 4)) return 0;
                    return shade(cloth, 1.0 - 0.02 * y);
                }
                if (f.equals("back") && x >= 5 && x <= 6 && y < 9) return shade(leather, 0.85); // колчан
                return shade(cloth, 1.0 - 0.02 * y);
            }
            if (f.equals("right") || f.equals("left")) return y < 2 ? shade(cloth, 1.0) : 0;
            if (x == 0 || x == w - 1 || y == h - 1) return shade(trim, 1.0);
            if (f.equals("front")) {
                // Герб: золотой крест на синем поле.
                if ((x == 3 || x == 4) && y >= 1 && y <= 6) return shade(trim, 1.0);
                if (y == 3 && x >= 2 && x <= 5) return shade(trim, 1.0);
                if (y == 7 && (x == 3 || x == 4)) return shade(trim, 0.9); // пряжка
            }
            return shade(y > 7 ? clothDark : cloth, 1.0);
        });

        // ---------------------------------------------------------------- руки
        box(40, 20, 4, 12, 4, (f, x, y, w, h) -> {
            if (f.equals("bottom")) return shade(skin, 0.9);
            if (y >= 10) return shade(skin, 0.95);
            if (y >= 7) return shade(leather, 1.0); // перчатки-краги
            if (archer) return y < 4 ? shade(cloth, 1.0) : shade(leather, 0.92);
            return y < 4 ? shade(cloth, 1.0) : shade(mail, (x + y) % 2 == 0 ? 1.0 : 0.8);
        });

        // ---------------------------------------------------------------- ноги
        box(0, 20, 4, 12, 4, (f, x, y, w, h) -> {
            if (f.equals("bottom")) return shade(leatherDark, 0.8);
            if (y >= 8) return shade(y == 8 ? leather : leatherDark, 1.0); // сапоги
            if (!archer && f.equals("front") && y >= 3 && y <= 5) return shade(iron, y == 4 ? 1.05 : 0.95); // наколенник
            return shade(trousers, 1.0);
        });
        return img;
    }
}
