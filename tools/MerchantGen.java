import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Наряды банкира и мастера-оружейника (слой профессии жителя 64×64) и их рабочие места 16×16:
 * конторка банкира (зелёное сукно, столбики монет, весы) и оружейный стенд (клинки и лук на досках);
 * наряды книжника и скупщика, конторка книжника (раскрытая книга, чернильница) и весы оценщика;
 * наряды ювелира и огранщика, витрина ювелира и круг огранщика.
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
        write(bookseller(), profession.resolve("bookseller.png"));
        write(pawnbroker(), profession.resolve("pawnbroker.png"));
        write(deskTop(), blocks.resolve("bookseller_desk_top.png"));
        write(deskSide(), blocks.resolve("bookseller_desk_side.png"));
        write(scalesTop(), blocks.resolve("appraisal_scales_top.png"));
        write(scalesSide(), blocks.resolve("appraisal_scales_side.png"));
        write(jeweler(), profession.resolve("jeweler.png"));
        write(gemCutter(), profession.resolve("gem_cutter.png"));
        write(showcaseTop(), blocks.resolve("jewelry_showcase_top.png"));
        write(showcaseSide(), blocks.resolve("jewelry_showcase_side.png"));
        write(wheelTop(), blocks.resolve("gem_wheel_top.png"));
        write(wheelSide(), blocks.resolve("gem_wheel_side.png"));
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

    /** Книжник: тёмно-синяя мантия с медными застёжками, сумка с книгами через плечо, бордовая шапочка с пером. */
    static BufferedImage bookseller() {
        int robe = 0xFF1E2E5A, robeDark = 0xFF152142, copper = 0xFFB8733A, strap = 0xFF5A3A20, cap = 0xFF6E1B2A, quill = 0xFFEDEDE0;
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        box(img, 0, 38, 8, 20, 6, robe);
        for (int v = 44; v < 64; v++) for (int u = 0; u < 28; u++) if ((u * 2 + v) % 9 == 0) img.setRGB(u, v, robeDark);
        for (int v = 46; v < 62; v += 3) img.setRGB(10, v, copper);
        // Ремень сумки наискось и сама сумка с корешками книг.
        for (int i = 0; i < 12; i++) img.setRGB(6 + i * 7 / 12, 44 + i, strap);
        for (int u = 11; u <= 14; u++) for (int v = 55; v <= 59; v++) img.setRGB(u, v, strap);
        img.setRGB(12, 55, 0xFF8A1C1C);
        img.setRGB(13, 55, 0xFF2A6A3A);
        img.setRGB(14, 55, 0xFFE0C060);
        for (int u = 0; u < 28; u++) img.setRGB(u, 63, robeDark);
        box(img, 16, 20, 8, 12, 6, robe);
        box(img, 44, 22, 4, 8, 4, robe);
        for (int u = 44; u < 60; u++) img.setRGB(u, 33, copper);
        box(img, 40, 38, 8, 4, 4, robe);
        // Шапочка с пером.
        for (int u = 40; u < 48; u++) for (int v = 0; v < 8; v++) img.setRGB(u, v, (u + v) % 5 == 0 ? 0xFF54141F : cap);
        for (int u = 32; u < 64; u++) {
            img.setRGB(u, 8, cap);
            img.setRGB(u, 9, 0xFF54141F);
        }
        for (int v = 2; v <= 7; v++) img.setRGB(46 - v / 3, v, quill);
        return img;
    }

    /** Скупщик: серый сюртук, коричневый жилет с цепочкой часов, кожаные нарукавники, тёмный цилиндр. */
    static BufferedImage pawnbroker() {
        int coat = 0xFF55585E, coatDark = 0xFF3E4146, vest = 0xFF6B4528, vestDark = 0xFF4A2E1A, hat = 0xFF222226;
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        box(img, 0, 38, 8, 20, 6, coat);
        for (int v = 44; v < 64; v++) for (int u = 0; u < 28; u++) if ((u + v * 3) % 8 == 0) img.setRGB(u, v, coatDark);
        // Жилет спереди с пуговицами и цепочкой часов.
        for (int u = 7; u <= 12; u++) for (int v = 44; v < 55; v++) img.setRGB(u, v, (u + v) % 6 == 0 ? vestDark : vest);
        for (int v = 46; v < 55; v += 2) img.setRGB(10, v, GOLD);
        int[][] chain = {{8, 50}, {9, 51}, {10, 51}, {11, 51}, {12, 50}};
        for (int[] p : chain) img.setRGB(p[0], p[1], GOLD_LIGHT);
        img.setRGB(12, 51, GOLD);
        for (int u = 0; u < 28; u++) img.setRGB(u, 55, coatDark);
        box(img, 16, 20, 8, 12, 6, coat);
        box(img, 44, 22, 4, 8, 4, coat);
        for (int u = 44; u < 60; u++) for (int v = 29; v < 34; v++) img.setRGB(u, v, vest);
        box(img, 40, 38, 8, 4, 4, coat);
        // Цилиндр.
        for (int u = 40; u < 48; u++) for (int v = 0; v < 8; v++) img.setRGB(u, v, hat);
        for (int u = 32; u < 64; u++) {
            img.setRGB(u, 8, hat);
            img.setRGB(u, 9, 0xFF8A1C1C);
            img.setRGB(u, 10, hat);
        }
        return img;
    }

    /** Верх конторки книжника: доски, раскрытая книга и чернильница с пером. */
    static BufferedImage deskTop() {
        int plank = 0xFF6B4A2E, plankDark = 0xFF52361F, page = 0xFFEDE3C6, pageDark = 0xFFC9BC98, ink = 0xFF1A1A2A;
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) img.setRGB(x, y, y % 5 == 4 ? plankDark : plank);
        for (int x = 2; x <= 11; x++) for (int y = 3; y <= 10; y++) img.setRGB(x, y, x == 6 || x == 7 ? pageDark : page);
        for (int y = 4; y <= 9; y += 2) {
            for (int x = 3; x <= 5; x++) img.setRGB(x, y, pageDark);
            for (int x = 8; x <= 10; x++) img.setRGB(x, y, pageDark);
        }
        for (int x = 12; x <= 13; x++) for (int y = 11; y <= 12; y++) img.setRGB(x, y, ink);
        for (int i = 0; i < 4; i++) img.setRGB(13 - i, 10 - i, 0xFFEDEDE0);
        return img;
    }

    /** Бок конторки книжника: корешки книг на полке. */
    static BufferedImage deskSide() {
        int frame = 0xFF52361F;
        int[] spines = {0xFF8A1C1C, 0xFF2A4A8A, 0xFF2A6A3A, 0xFF7A5A1A, 0xFF5A2A6A, 0xFF8A1C1C, 0xFF2A4A8A};
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                boolean edge = x == 0 || x == 15 || y <= 1 || y >= 14 || y == 7 || y == 8;
                img.setRGB(x, y, edge ? frame : spines[(x - 1) / 2 % spines.length]);
            }
        }
        for (int x = 1; x < 15; x += 2) {
            img.setRGB(x, 4, GOLD);
            img.setRGB(x, 11, GOLD);
        }
        return img;
    }

    /** Верх весов оценщика: тёмные доски, медные весы и лупа. */
    static BufferedImage scalesTop() {
        int wood = 0xFF3A2414, woodLight = 0xFF4A2E1A, copper = 0xFFB8733A, glass = 0xFFA8D8E8;
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) img.setRGB(x, y, (x * 3 + y) % 7 == 0 ? woodLight : wood);
        for (int x = 2; x <= 13; x++) img.setRGB(x, 5, copper);
        for (int y = 5; y <= 11; y++) img.setRGB(7, y, copper);
        for (int x = 2; x <= 4; x++) for (int y = 6; y <= 7; y++) img.setRGB(x, y, GOLD);
        for (int x = 11; x <= 13; x++) for (int y = 6; y <= 7; y++) img.setRGB(x, y, GOLD);
        int[][] lens = {{11, 10}, {12, 10}, {10, 11}, {13, 11}, {10, 12}, {13, 12}, {11, 13}, {12, 13}};
        for (int[] p : lens) img.setRGB(p[0], p[1], copper);
        img.setRGB(11, 11, glass);
        img.setRGB(12, 12, glass);
        img.setRGB(14, 14, woodLight);
        return img;
    }

    /** Бок весов оценщика: тёмные филёнки и медный замок. */
    static BufferedImage scalesSide() {
        int wood = 0xFF3A2414, woodLight = 0xFF4A2E1A, woodDark = 0xFF2A180C, copper = 0xFFB8733A;
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int c = (y * 5 + x) % 11 == 0 ? woodLight : wood;
                if (y <= 1) c = copper;
                else if (y >= 14 || x == 0 || x == 15) c = woodDark;
                img.setRGB(x, y, c);
            }
        }
        for (int x = 6; x <= 9; x++) for (int y = 6; y <= 9; y++) img.setRGB(x, y, x == 6 || x == 9 || y == 6 || y == 9 ? copper : woodDark);
        img.setRGB(7, 7, GOLD);
        img.setRGB(8, 8, GOLD);
        return img;
    }

    /** Ювелир: пурпурный бархатный камзол с золотой вышивкой, ожерелье с рубином, лупа на глазу, бархатный берет. */
    static BufferedImage jeweler() {
        int coat = 0xFF4A1E5A, coatDark = 0xFF341442, ruby = 0xFFC8203A, glass = 0xFFA8D8E8, cap = 0xFF341442;
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        box(img, 0, 38, 8, 20, 6, coat);
        for (int v = 44; v < 64; v++) for (int u = 0; u < 28; u++) if ((u * 3 + v) % 10 == 0) img.setRGB(u, v, coatDark);
        // Золотая вышивка по бортам и ожерелье с рубином.
        for (int v = 44; v < 63; v++) {
            img.setRGB(8, v, GOLD);
            img.setRGB(11, v, GOLD);
        }
        int[][] chain = {{7, 44}, {8, 45}, {9, 46}, {10, 46}, {11, 45}, {12, 44}};
        for (int[] p : chain) img.setRGB(p[0], p[1], GOLD_LIGHT);
        img.setRGB(9, 47, ruby);
        img.setRGB(10, 47, ruby);
        for (int u = 0; u < 28; u++) img.setRGB(u, 63, coatDark);
        box(img, 16, 20, 8, 12, 6, coat);
        box(img, 44, 22, 4, 8, 4, coat);
        for (int u = 44; u < 60; u++) img.setRGB(u, 33, GOLD);
        box(img, 40, 38, 8, 4, 4, coat);
        // Лупа на правом глазу (лицо головы — u 8…15, v 8…15).
        int[][] lens = {{9, 11}, {10, 11}, {9, 12}, {10, 12}};
        for (int[] p : lens) img.setRGB(p[0], p[1], glass);
        img.setRGB(8, 11, GOLD_DARK);
        img.setRGB(11, 12, GOLD_DARK);
        // Бархатный берет с золотой лентой.
        for (int u = 40; u < 48; u++) for (int v = 0; v < 8; v++) img.setRGB(u, v, (u + v) % 4 == 0 ? coat : cap);
        for (int u = 32; u < 64; u++) {
            img.setRGB(u, 8, cap);
            img.setRGB(u, 9, GOLD);
        }
        return img;
    }

    /** Огранщик: кожаный фартук поверх серо-голубой рубахи, защитные очки на лбу, мешочек с камнями, нарукавники. */
    static BufferedImage gemCutter() {
        int shirt = 0xFF4E6A7A, shirtDark = 0xFF3A5260, apron = 0xFF6B4528, apronDark = 0xFF4A2E1A, lens = 0xFF7FD8E8;
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        box(img, 0, 38, 8, 20, 6, shirt);
        for (int v = 44; v < 64; v++) for (int u = 0; u < 28; u++) if ((u + v * 2) % 9 == 0) img.setRGB(u, v, shirtDark);
        // Фартук спереди и каменная крошка на нём.
        for (int u = 6; u <= 13; u++) for (int v = 46; v < 63; v++) img.setRGB(u, v, (u * 2 + v) % 7 == 0 ? apronDark : apron);
        img.setRGB(8, 52, 0xFFC8203A);
        img.setRGB(11, 55, 0xFF3060D0);
        img.setRGB(9, 58, 0xFFE0B03A);
        // Мешочек с камнями на поясе.
        for (int u = 14; u <= 16; u++) for (int v = 54; v <= 57; v++) img.setRGB(u, v, apronDark);
        box(img, 16, 20, 8, 12, 6, shirt);
        box(img, 44, 22, 4, 8, 4, shirt);
        for (int u = 44; u < 60; u++) for (int v = 29; v < 34; v++) img.setRGB(u, v, apron);
        box(img, 40, 38, 8, 4, 4, shirt);
        // Защитные очки на лбу: ремешок вокруг головы и два стекла спереди.
        for (int u = 32; u < 64; u++) img.setRGB(u, 9, BLACK);
        for (int u : new int[]{9, 10, 13, 14}) {
            img.setRGB(u, 8, lens);
            img.setRGB(u, 9, lens);
        }
        return img;
    }

    /** Верх витрины ювелира: стекло в золотой раме, под ним на бархате кольца и ожерелье. */
    static BufferedImage showcaseTop() {
        int velvet = 0xFF3A1446, velvetLight = 0xFF4E1E5E, glint = 0xFFDDF4FF;
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                boolean frame = x == 0 || x == 15 || y == 0 || y == 15;
                img.setRGB(x, y, frame ? GOLD_DARK : (x + y) % 5 == 0 ? velvetLight : velvet);
            }
        }
        int[][] ring = {{3, 3}, {4, 3}, {2, 4}, {5, 4}, {3, 5}, {4, 5}};
        for (int[] p : ring) {
            img.setRGB(p[0], p[1], GOLD);
            img.setRGB(p[0] + 7, p[1] + 6, 0xFFC0C0C8);
        }
        img.setRGB(3, 2, 0xFFC8203A);
        img.setRGB(10, 8, 0xFF3060D0);
        for (int x = 3; x <= 12; x++) img.setRGB(x, 12 - Math.abs(x - 8) / 3, GOLD_LIGHT);
        img.setRGB(8, 13, 0xFF2AA050);
        for (int i = 0; i < 4; i++) img.setRGB(12 - i, 1 + i, glint);
        return img;
    }

    /** Бок витрины ювелира: тёмное дерево с золотыми накладками и стекло наверху. */
    static BufferedImage showcaseSide() {
        int wood = 0xFF2A180C, woodLight = 0xFF3A2414, glass = 0xFFB8DCE8, glassDark = 0xFF8AB4C4;
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int c = (x * 3 + y) % 8 == 0 ? woodLight : wood;
                if (y <= 5) c = x == 0 || x == 15 || y == 0 ? GOLD_DARK : (x + y) % 6 == 0 ? glassDark : glass;
                else if (y == 6 || y == 15) c = GOLD;
                img.setRGB(x, y, c);
            }
        }
        for (int x = 5; x <= 10; x++) for (int y = 9; y <= 12; y++) img.setRGB(x, y, x == 5 || x == 10 || y == 9 || y == 12 ? GOLD : wood);
        return img;
    }

    /** Верх круга огранщика: каменный точильный круг на станине, рядом россыпь камней. */
    static BufferedImage wheelTop() {
        int iron = 0xFF5A5E64, ironLight = 0xFF7A7E86, stone = 0xFF9A9A9A, stoneDark = 0xFF707070;
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) img.setRGB(x, y, (x + y * 2) % 7 == 0 ? ironLight : iron);
        for (int x = 2; x <= 13; x++) {
            for (int y = 2; y <= 13; y++) {
                double d = Math.hypot(x - 7.5, y - 7.5);
                if (d <= 5.6) img.setRGB(x, y, d < 1.5 ? ironLight : (int) (d * 3) % 2 == 0 ? stone : stoneDark);
            }
        }
        img.setRGB(1, 14, 0xFFC8203A);
        img.setRGB(2, 13, 0xFF3060D0);
        img.setRGB(14, 1, 0xFFE0B03A);
        img.setRGB(13, 14, 0xFF9AE8F0);
        return img;
    }

    /** Бок круга огранщика: станина с ручкой и ящик для камней. */
    static BufferedImage wheelSide() {
        int wood = 0xFF5A3A20, woodDark = 0xFF3E2814, iron = 0xFF5A5E64, stone = 0xFF9A9A9A;
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                int c = (y % 4 == 3) ? woodDark : wood;
                if (y <= 1 || x == 0 || x == 15) c = iron;
                img.setRGB(x, y, c);
            }
        }
        for (int x = 4; x <= 11; x++) for (int y = 2; y <= 4; y++) img.setRGB(x, y, stone);
        for (int x = 5; x <= 10; x++) for (int y = 9; y <= 12; y++) img.setRGB(x, y, x == 5 || x == 10 || y == 9 || y == 12 ? iron : woodDark);
        img.setRGB(7, 10, 0xFFC8203A);
        img.setRGB(8, 11, 0xFF9AE8F0);
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
