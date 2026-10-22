import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Наряд глашатая гильдии (слой профессии жителя 64×64): алая котта с золотой каймой и черепом-гербом на груди,
 * пояс с пряжкой, алые рукава с золотыми манжетами и алый берет с белым пером.
 *
 * Запуск из корня проекта: java tools/HeraldGen.java src/main/resources
 */
public class HeraldGen {
    static final int RED = 0xFF9B1B1B, RED_DARK = 0xFF6E1010, GOLD = 0xFFE0B03A, GOLD_DARK = 0xFFA87A1E,
            BELT = 0xFF4A2E1A, WHITE = 0xFFEEEEEE, GREY = 0xFFB8B8B8, BLACK = 0xFF222222;

    public static void main(String[] args) throws IOException {
        Path resources = Path.of(args.length > 0 ? args[0] : "src/main/resources");
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

        // Котта поверх тела (рамка 8×20×6 с 0,38): все боковые грани алые, складки потемнее, кайма по низу и плечам.
        box(img, 0, 38, 8, 20, 6, RED);
        for (int v = 44; v < 64; v++) for (int u = 0; u < 28; u++) if ((u + v / 3) % 5 == 0) img.setRGB(u, v, RED_DARK);
        for (int u = 0; u < 28; u++) {
            img.setRGB(u, 62, GOLD);
            img.setRGB(u, 63, GOLD_DARK);
            img.setRGB(u, 44, GOLD);
        }
        // Спереди: золотые борта, пояс с пряжкой, череп-герб.
        for (int v = 44; v < 62; v++) {
            img.setRGB(6, v, GOLD_DARK);
            img.setRGB(13, v, GOLD_DARK);
        }
        for (int u = 0; u < 28; u++) img.setRGB(u, 53, BELT);
        img.setRGB(9, 53, GOLD);
        img.setRGB(10, 53, GOLD);
        int[][] skull = {
                {0, 1, 1, 0},
                {1, 2, 1, 2},
                {1, 1, 1, 1},
                {0, 1, 0, 1}};
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (skull[y][x] == 1) img.setRGB(8 + x, 47 + y, WHITE);
                if (skull[y][x] == 2) img.setRGB(8 + x, 47 + y, BLACK);
            }
        }
        for (int u = 7; u <= 12; u++) img.setRGB(u, 46, GOLD);

        // Тело (8×12×6 с 16,20) под коттой — тоже алое.
        box(img, 16, 20, 8, 12, 6, RED);
        // Скрещённые руки: рукава алые, на концах золотые манжеты.
        box(img, 44, 22, 4, 8, 4, RED);
        for (int u = 44; u < 60; u++) img.setRGB(u, 33, GOLD);
        box(img, 40, 38, 8, 4, 4, RED);
        for (int v = 42; v < 46; v++) {
            img.setRGB(40, v, GOLD);
            img.setRGB(52, v, GOLD);
        }

        // Берет (слой шапки 8×10×8 с 32,0): алый верх, околыш с золотой лентой, белое перо набок.
        for (int u = 40; u < 48; u++) for (int v = 0; v < 8; v++) img.setRGB(u, v, (u + v) % 4 == 0 ? RED_DARK : RED);
        for (int u = 32; u < 64; u++) {
            img.setRGB(u, 8, RED);
            img.setRGB(u, 9, RED_DARK);
            img.setRGB(u, 10, GOLD);
        }
        int[][] feather = {{41, 6}, {42, 5}, {42, 4}, {43, 3}, {44, 2}, {44, 1}, {45, 0}};
        for (int[] p : feather) img.setRGB(p[0], p[1], WHITE);
        img.setRGB(43, 4, GREY);
        img.setRGB(45, 1, GREY);
        for (int v = 8; v <= 10; v++) img.setRGB(33, v, WHITE);
        img.setRGB(34, 9, GREY);

        Path out = resources.resolve("assets/heroicpaths/textures/entity/villager/profession/bounty_herald.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(img, "png", out.toFile());
        System.out.println("Wrote " + out);
        writeBoard(resources.resolve("assets/heroicpaths/textures/block/bounty_board_front.png"));
    }

    /** Лицо доски наказов 16×16: тёмная рама, два листа «разыскивается» с черепом и красной печатью. */
    static void writeBoard(Path out) throws IOException {
        int frame = 0xFF3B2614, frameLight = 0xFF5A3C20, cork = 0xFF6B4A2B, paper = 0xFFE8D9B0, paperDark = 0xFFC9B68A,
                ink = 0xFF3A2A1E, seal = 0xFFB21E1E;
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                boolean edge = x == 0 || y == 0 || x == 15 || y == 15;
                img.setRGB(x, y, edge ? ((x + y) % 3 == 0 ? frameLight : frame) : ((x * 7 + y * 3) % 5 == 0 ? frame : cork));
            }
        }
        // Большой лист с черепом.
        for (int x = 2; x <= 9; x++) for (int y = 2; y <= 12; y++) img.setRGB(x, y, (x + y) % 6 == 0 ? paperDark : paper);
        int[][] skull = {{4, 4}, {5, 4}, {6, 4}, {7, 4}, {4, 5}, {7, 5}, {4, 6}, {5, 6}, {6, 6}, {7, 6}, {5, 7}, {7, 7}};
        for (int[] p : skull) img.setRGB(p[0], p[1], ink);
        img.setRGB(5, 5, ink);
        img.setRGB(6, 5, paper);
        for (int x = 3; x <= 8; x++) img.setRGB(x, 9, x % 2 == 0 ? ink : paperDark);
        for (int x = 3; x <= 7; x++) img.setRGB(x, 11, x % 3 == 0 ? paperDark : ink);
        img.setRGB(8, 11, seal);
        img.setRGB(8, 12, seal);
        // Малый лист справа с печатью.
        for (int x = 10; x <= 13; x++) for (int y = 5; y <= 11; y++) img.setRGB(x, y, paper);
        for (int y = 6; y <= 9; y += 1) img.setRGB(11, y, y % 2 == 0 ? ink : paperDark);
        img.setRGB(12, 7, ink);
        img.setRGB(12, 10, seal);
        img.setRGB(11, 10, seal);
        // Гвозди.
        img.setRGB(5, 2, 0xFF9A9A9A);
        img.setRGB(11, 5, 0xFF9A9A9A);
        ImageIO.write(img, "png", out.toFile());
        System.out.println("Wrote " + out);
    }

    /** Все шесть граней коробки с развёрткой Minecraft (u, v — левый верхний угол; w, h, d — размеры). */
    static void box(BufferedImage img, int u, int v, int w, int h, int d, int color) {
        for (int x = u + d; x < u + d + 2 * w; x++) for (int y = v; y < v + d; y++) img.setRGB(x, y, color);
        for (int x = u; x < u + 2 * (w + d); x++) for (int y = v + d; y < v + d + h; y++) img.setRGB(x, y, color);
    }
}
