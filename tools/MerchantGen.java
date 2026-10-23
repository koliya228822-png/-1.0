import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Наряды банкира и мастера-оружейника (слой профессии жителя 64×64) и их рабочие места 16×16:
 * конторка банкира (зелёное сукно, столбики монет, весы) и оружейный стенд (клинки и лук на досках).
 *
 * Запуск из корня проекта: java tools/MerchantGen.java src/main/resources
 */
public class MerchantGen {
    static final int GOLD = 0xFFE0B03A, GOLD_DARK = 0xFFA87A1E, GOLD_LIGHT = 0xFFF6DA7A, BLACK = 0xFF1E1C1C, WHITE = 0xFFEEEEEE;

    public static void main(String[] args) throws IOException {
        Path resources = Path.of(args.length > 0 ? args[0] : "src/main/resources");
        Path profession = resources.resolve("assets/heroicpaths/textures/entity/villager/profession");
        Path blocks = resources.resolve("assets/heroicpaths/textures/block");
        Files.createDirectories(profession);
        write(banker(), profession.resolve("banker.png"));
        write(armsDealer(), profession.resolve("arms_dealer.png"));
        write(counterTop(), blocks.resolve("bank_counter_top.png"));
        write(counterSide(), blocks.resolve("bank_counter_side.png"));
        write(displayTop(), blocks.resolve("arms_display_top.png"));
        write(displaySide(), blocks.resolve("arms_display_side.png"));
    }

    /** Банкир: тёмно-зелёный кафтан с золотыми пуговицами, золотая цепь, кошель на поясе, чёрный берет с золотой лентой. */
    static BufferedImage banker() {
        int coat = 0xFF1F4A33, coatDark = 0xFF153524, belt = 0xFF3A2414, purse = 0xFF6B3E1E;
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        box(img, 0, 38, 8, 20, 6, coat);
        for (int v = 44; v < 64; v++) for (int u = 0; u < 28; u++) if ((u + v / 4) % 6 == 0) img.setRGB(u, v, coatDark);
        for (int u = 0; u < 28; u++) {
            img.setRGB(u, 62, GOLD_DARK);
            img.setRGB(u, 63, coatDark);
        }
        // Борта с пуговицами, цепь от плеч к медальону, пояс с кошелём.
        for (int v = 44; v < 62; v++) {
            img.setRGB(9, v, coatDark);
            img.setRGB(10, v, coatDark);
            if (v % 3 == 0 && v > 50) img.setRGB(10, v, GOLD);
        }
        int[][] chain = {{6, 44}, {7, 45}, {8, 46}, {9, 47}, {10, 47}, {11, 46}, {12, 45}, {13, 44}};
        for (int[] p : chain) img.setRGB(p[0], p[1], (p[0] % 2 == 0) ? GOLD : GOLD_LIGHT);
        img.setRGB(9, 48, GOLD);
        img.setRGB(10, 48, GOLD_LIGHT);
        img.setRGB(9, 49, GOLD_DARK);
        img.setRGB(10, 49, GOLD);
        for (int u = 0; u < 28; u++) img.setRGB(u, 54, belt);
        img.setRGB(9, 54, GOLD);
        img.setRGB(10, 54, GOLD);
        for (int u = 11; u <= 13; u++) for (int v = 55; v <= 57; v++) img.setRGB(u, v, purse);
        img.setRGB(12, 55, GOLD);
        box(img, 16, 20, 8, 12, 6, coat);
        // Рукава с золотыми манжетами.
        box(img, 44, 22, 4, 8, 4, coat);
        for (int u = 44; u < 60; u++) img.setRGB(u, 33, GOLD);
        box(img, 40, 38, 8, 4, 4, coat);
        for (int v = 42; v < 46; v++) {
            img.setRGB(40, v, GOLD);
            img.setRGB(52, v, GOLD);
        }
        // Чёрный берет с золотой лентой.
        for (int u = 40; u < 48; u++) for (int v = 0; v < 8; v++) img.setRGB(u, v, (u * 3 + v) % 7 == 0 ? 0xFF2C2828 : BLACK);
        for (int u = 32; u < 64; u++) {
            img.setRGB(u, 8, BLACK);
            img.setRGB(u, 9, GOLD);
            img.setRGB(u, 10, BLACK);
        }
        return img;
    }

    /** Мастер-оружейник: кольчуга под кожаным фартуком, кожаные наручи, красная повязка на голове. */
    static BufferedImage armsDealer() {
        int mail = 0xFF8C8F94, mailDark = 0xFF5E6166, leather = 0xFF6B4528, leatherDark = 0xFF4A2E1A, red = 0xFF9B1B1B;
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        box(img, 0, 38, 8, 20, 6, mail);
        for (int v = 44; v < 64; v++) for (int u = 0; u < 28; u++) if ((u + v) % 2 == 0) img.setRGB(u, v, mailDark);
        // Фартук спереди (u 6..13), ремни на плечах, заклёпки.
        for (int u = 6; u <= 13; u++) for (int v = 47; v < 63; v++) img.setRGB(u, v, (u + v) % 5 == 0 ? leatherDark : leather);
        for (int v = 44; v < 47; v++) {
            img.setRGB(7, v, leatherDark);
            img.setRGB(12, v, leatherDark);
        }
        img.setRGB(7, 49, 0xFFC0C0C0);
        img.setRGB(12, 49, 0xFFC0C0C0);
        for (int u = 0; u < 28; u++) img.setRGB(u, 54, leatherDark);
        for (int u = 9; u <= 10; u++) for (int v = 56; v <= 59; v++) img.setRGB(u, v, 0xFFB0B4BA);
        box(img, 16, 20, 8, 12, 6, mail);
        box(img, 44, 22, 4, 8, 4, mail);
        for (int u = 44; u < 60; u++) for (int v = 30; v < 34; v++) img.setRGB(u, v, leather);
        box(img, 40, 38, 8, 4, 4, leather);
        // Красная повязка.
        for (int u = 32; u < 64; u++) {
            img.setRGB(u, 9, red);
            img.setRGB(u, 10, 0xFF6E1010);
        }
        return img;
    }

    /** Верх конторки: зелёное сукно в рамке, столбики монет и весы. */
    static BufferedImage counterTop() {
        int wood = 0xFF4A2E1A, woodLight = 0xFF6B4528, felt = 0xFF1F5A38, feltDark = 0xFF184A2E;
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                boolean edge = x <= 1 || y <= 1 || x >= 14 || y >= 14;
                img.setRGB(x, y, edge ? ((x + y) % 4 == 0 ? woodLight : wood) : ((x * 5 + y * 3) % 7 == 0 ? feltDark : felt));
            }
        }
        for (int[] c : new int[][]{{3, 3}, {5, 4}, {4, 6}}) {
            img.setRGB(c[0], c[1], GOLD_LIGHT);
            img.setRGB(c[0] + 1, c[1], GOLD);
            img.setRGB(c[0], c[1] + 1, GOLD);
            img.setRGB(c[0] + 1, c[1] + 1, GOLD_DARK);
        }
        // Весы: коромысло и две чаши.
        for (int x = 8; x <= 13; x++) img.setRGB(x, 10, GOLD_DARK);
        img.setRGB(10, 9, GOLD);
        img.setRGB(10, 11, GOLD);
        for (int x = 8; x <= 9; x++) for (int y = 11; y <= 12; y++) img.setRGB(x, y, GOLD);
        for (int x = 12; x <= 13; x++) for (int y = 11; y <= 12; y++) img.setRGB(x, y, GOLD);
        // Книга учёта.
        for (int x = 3; x <= 7; x++) for (int y = 9; y <= 12; y++) img.setRGB(x, y, x == 5 ? 0xFF8A1C1C : 0xFFE8D9B0);
        return img;
    }

    /** Бок конторки: тёмные филёнки с золотой каймой и монетой-гербом. */
    static BufferedImage counterSide() {
        int wood = 0xFF4A2E1A, woodLight = 0xFF6B4528, woodDark = 0xFF33200F;
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int c = (y * 3 + x / 5) % 9 == 0 ? woodLight : wood;
                if (y <= 1) c = y == 0 ? GOLD_DARK : GOLD;
                else if (y >= 14 || x == 0 || x == 15 || x == 7 || x == 8) c = woodDark;
                img.setRGB(x, y, c);
            }
        }
        for (int[] panel : new int[][]{{2, 5}, {10, 13}}) {
            for (int x = panel[0]; x <= panel[1]; x++) {
                img.setRGB(x, 3, woodLight);
                img.setRGB(x, 12, woodDark);
            }
        }
        int[][] coin = {{3, 6}, {4, 6}, {2, 7}, {5, 7}, {2, 8}, {5, 8}, {3, 9}, {4, 9}};
        for (int[] p : coin) {
            img.setRGB(p[0], p[1], GOLD);
            img.setRGB(p[0] + 8, p[1], GOLD);
        }
        img.setRGB(3, 7, GOLD_LIGHT);
        img.setRGB(4, 8, GOLD_DARK);
        img.setRGB(11, 7, GOLD_LIGHT);
        img.setRGB(12, 8, GOLD_DARK);
        return img;
    }

    /** Верх стенда: доски и два скрещённых клинка. */
    static BufferedImage displayTop() {
        int plank = 0xFF7A5230, plankDark = 0xFF5A3A20, steel = 0xFFC8CCD2, steelDark = 0xFF8A8E94, grip = 0xFF3A2414;
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) img.setRGB(x, y, y % 4 == 3 ? plankDark : ((x + y * 7) % 11 == 0 ? plankDark : plank));
        for (int i = 2; i <= 11; i++) {
            img.setRGB(i, i, steel);
            img.setRGB(i + 1, i, steelDark);
            img.setRGB(15 - i, i, steel);
            img.setRGB(14 - i, i, steelDark);
        }
        for (int i = 12; i <= 13; i++) {
            img.setRGB(i, i, grip);
            img.setRGB(15 - i, i, grip);
        }
        img.setRGB(11, 12, GOLD);
        img.setRGB(12, 11, GOLD);
        img.setRGB(4, 12, GOLD);
        img.setRGB(3, 11, GOLD);
        return img;
    }

    /** Бок стенда: тёмная рама, на крюках меч, лук и кинжал. */
    static BufferedImage displaySide() {
        int frame = 0xFF4A2E1A, back = 0xFF7A5230, backDark = 0xFF6A4628, steel = 0xFFC8CCD2, steelDark = 0xFF8A8E94,
                grip = 0xFF3A2414, bow = 0xFF8B5A2B, string = 0xFFDDDDDD;
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                boolean edge = x == 0 || y == 0 || x == 15 || y == 15;
                img.setRGB(x, y, edge ? frame : (x % 5 == 0 ? backDark : back));
            }
        }
        // Меч остриём вверх.
        for (int y = 2; y <= 10; y++) {
            img.setRGB(3, y, steel);
            img.setRGB(4, y, steelDark);
        }
        for (int x = 2; x <= 5; x++) img.setRGB(x, 11, GOLD_DARK);
        img.setRGB(3, 12, grip);
        img.setRGB(3, 13, grip);
        img.setRGB(3, 14, GOLD);
        // Лук.
        int[][] arc = {{8, 2}, {9, 3}, {10, 4}, {10, 5}, {11, 6}, {11, 7}, {11, 8}, {11, 9}, {10, 10}, {10, 11}, {9, 12}, {8, 13}};
        for (int[] p : arc) img.setRGB(p[0], p[1], bow);
        for (int y = 3; y <= 12; y++) img.setRGB(8, y, string);
        // Кинжал.
        for (int y = 4; y <= 9; y++) img.setRGB(13, y, steel);
        img.setRGB(12, 10, GOLD_DARK);
        img.setRGB(13, 10, GOLD_DARK);
        img.setRGB(14, 10, GOLD_DARK);
        img.setRGB(13, 11, grip);
        img.setRGB(13, 12, grip);
        return img;
    }

    static void write(BufferedImage img, Path out) throws IOException {
        ImageIO.write(img, "png", out.toFile());
        System.out.println("Wrote " + out);
    }

    /** Все шесть граней коробки с развёрткой Minecraft (u, v — левый верхний угол; w, h, d — размеры). */
    static void box(BufferedImage img, int u, int v, int w, int h, int d, int color) {
        for (int x = u + d; x < u + d + 2 * w; x++) for (int y = v; y < v + d; y++) img.setRGB(x, y, color);
        for (int x = u; x < u + 2 * (w + d); x++) for (int y = v + d; y < v + d + h; y++) img.setRGB(x, y, color);
    }
}
