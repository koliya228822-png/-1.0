import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * Текстуры камня перемещения и свитка перемещения (16×16).
 * Запуск: java tools/WaystoneGen.java src/main/resources
 */
public class WaystoneGen {
    public static void main(String[] args) throws Exception {
        File root = new File(args.length > 0 ? args[0] : "src/main/resources", "assets/heroicpaths/textures");
        ImageIO.write(side(), "png", new File(root, "block/waystone_side.png"));
        ImageIO.write(top(), "png", new File(root, "block/waystone_top.png"));
        ImageIO.write(bottom(), "png", new File(root, "block/waystone_bottom.png"));
        ImageIO.write(scroll(), "png", new File(root, "item/teleport_scroll.png"));
    }

    static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** Булыжная кладка: камни с тёмными швами. */
    static BufferedImage stone(long seed) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(seed);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int brick = (y / 4) % 2 == 0 ? x : (x + 4) % 16;
                boolean seam = y % 4 == 3 || brick % 8 == 7;
                int v = seam ? 62 : 118 + random.nextInt(22) - (y % 4 == 0 ? 0 : 8);
                img.setRGB(x, y, rgb(v, v, v + 4));
            }
        }
        return img;
    }

    static BufferedImage side() {
        BufferedImage img = stone(7);
        // Изумрудная руна по центру: вертикальная черта со стрелками-«крыльями».
        int[][] rune = {
                {7, 2}, {8, 2}, {6, 3}, {9, 3}, {7, 3}, {8, 3},
                {7, 4}, {8, 4}, {7, 5}, {8, 5}, {5, 6}, {10, 6}, {7, 6}, {8, 6},
                {6, 7}, {9, 7}, {7, 7}, {8, 7}, {7, 8}, {8, 8}, {7, 9}, {8, 9},
                {5, 10}, {10, 10}, {7, 10}, {8, 10}, {6, 11}, {9, 11}, {7, 11}, {8, 11}, {7, 12}, {8, 12}, {7, 13}, {8, 13}};
        for (int[] p : rune) {
            int glow = (p[0] == 7 || p[0] == 8) ? 255 : 200;
            img.setRGB(p[0], p[1], rgb(40, glow, 110));
        }
        // Оправа сверху и снизу.
        for (int x = 0; x < 16; x++) {
            img.setRGB(x, 0, rgb(70, 74, 80));
            img.setRGB(x, 15, rgb(50, 52, 58));
        }
        return img;
    }

    static BufferedImage top() {
        BufferedImage img = stone(11);
        // Изумрудная плита с кольцом в центре.
        for (int y = 4; y < 12; y++) {
            for (int x = 4; x < 12; x++) {
                double d = Math.hypot(x - 7.5, y - 7.5);
                if (d < 4.2) img.setRGB(x, y, d > 3 ? rgb(20, 120, 60) : rgb(50, 220 - (int) (d * 20), 120));
            }
        }
        img.setRGB(7, 7, rgb(200, 255, 220));
        img.setRGB(8, 8, rgb(160, 255, 200));
        return img;
    }

    static BufferedImage bottom() {
        return stone(13);
    }

    /** Свиток: свёрнутая бумага с изумрудной печатью, по диагонали. */
    static BufferedImage scroll() {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int d = x + y - 15, t = x - y;
                if (Math.abs(d) <= 2 && Math.abs(t) <= 10) {
                    int shade = 230 - Math.abs(d) * 18;
                    img.setRGB(x, y, rgb(shade, shade - 14, shade - 50));
                }
                // Валики на концах свитка.
                if (Math.abs(d) <= 3 && (t == 11 || t == 12 || t == -11 || t == -12)) img.setRGB(x, y, rgb(120, 80, 40));
            }
        }
        // Изумрудная печать и строки.
        int[][] seal = {{7, 8}, {8, 7}, {7, 7}, {8, 8}, {6, 8}, {8, 6}, {9, 7}, {7, 9}};
        for (int[] p : seal) img.setRGB(p[0], p[1], rgb(30, 200, 90));
        img.setRGB(7, 8, rgb(150, 255, 190));
        for (int k = 2; k <= 4; k++) {
            img.setRGB(4 + k, 11 - k - 2, rgb(120, 100, 70));
            img.setRGB(10 + k - 4, 5 + k - 2 + 4, rgb(120, 100, 70));
        }
        return img;
    }
}
