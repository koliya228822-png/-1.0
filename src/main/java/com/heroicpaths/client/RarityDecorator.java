package com.heroicpaths.client;

import com.heroicpaths.item.ItemRarity;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

/**
 * Рамка редкости на иконке предмета в инвентаре и на панели: у мусора — трещины и потемнение,
 * у качественной — точка, у редкой и выше — уголки, у легендарной — золотая рамка, у реликвии — мерцающая.
 */
public final class RarityDecorator implements IItemDecorator {
    public static final RarityDecorator INSTANCE = new RarityDecorator();

    /** Пиксели трещин мусорной вещи (x, y внутри иконки 16×16). */
    private static final int[][] CRACKS = {
            {13, 1}, {12, 2}, {12, 3}, {11, 4}, {10, 5}, {10, 6}, {11, 7}, {12, 8},
            {9, 7}, {8, 8},
            {2, 14}, {3, 13}, {3, 12}, {4, 11}, {5, 11}
    };

    private RarityDecorator() {}

    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        ItemRarity.Tier tier = ItemRarity.get(stack);
        if (tier == null || tier == ItemRarity.Tier.COMMON) return false;
        int rgb = tier.rgb();
        switch (tier) {
            case JUNK -> {
                fill(graphics, x, y, 16, 16, 0x38000000);
                for (int[] pixel : CRACKS) fill(graphics, x + pixel[0], y + pixel[1], 1, 1, 0xC0302A24);
            }
            case FINE -> fill(graphics, x, y, 2, 2, 0xFF000000 | rgb);
            case RARE -> corner(graphics, x, y, 0, 0, rgb, false);
            case UNIQUE -> {
                corner(graphics, x, y, 0, 0, rgb, false);
                corner(graphics, x, y, 1, 1, rgb, false);
            }
            case EPIC -> {
                for (int cx = 0; cx < 2; cx++) for (int cy = 0; cy < 2; cy++) corner(graphics, x, y, cx, cy, rgb, true);
            }
            case LEGENDARY -> frame(graphics, x, y, rgb, 0x50);
            case RELIC -> {
                // Пульсирует между алым и золотым.
                float pulse = 0.5f + 0.5f * Mth.sin((Util.getMillis() % 1600L) / 1600f * Mth.TWO_PI);
                frame(graphics, x, y, lerp(0xFF2A2A, 0xFFC33A, pulse), 0x40 + (int) (pulse * 0x40));
                // Тёмно-алые камни в углах отличают реликвию от легендарной вещи.
                for (int cx = 0; cx < 2; cx++) for (int cy = 0; cy < 2; cy++) fill(graphics, x + 1 + cx * 12, y + 1 + cy * 12, 2, 2, 0xFFB3122E);
            }
            default -> {}
        }
        return true;
    }

    /** Уголок рамки: cx, cy — 0 слева/сверху, 1 справа/снизу. */
    private static void corner(GuiGraphics graphics, int x, int y, int cx, int cy, int rgb, boolean glow) {
        int left = x + cx * 12;
        int top = y + cy * 15;
        fill(graphics, left, top, 4, 1, 0xFF000000 | rgb);
        int column = x + cx * 15;
        int row = y + cy * 12;
        fill(graphics, column, row, 1, 4, 0xFF000000 | rgb);
        if (glow) {
            fill(graphics, x + 1 + cx * 11, y + (cy == 0 ? 1 : 14), 3, 1, 0x60000000 | rgb);
            fill(graphics, x + (cx == 0 ? 1 : 14), y + 1 + cy * 11, 1, 3, 0x60000000 | rgb);
        }
    }

    /** Сплошная рамка в край иконки и полупрозрачное свечение внутри. */
    private static void frame(GuiGraphics graphics, int x, int y, int rgb, int glowAlpha) {
        int solid = 0xFF000000 | rgb;
        fill(graphics, x, y, 16, 1, solid);
        fill(graphics, x, y + 15, 16, 1, solid);
        fill(graphics, x, y + 1, 1, 14, solid);
        fill(graphics, x + 15, y + 1, 1, 14, solid);
        int glow = (glowAlpha << 24) | rgb;
        fill(graphics, x + 1, y + 1, 14, 1, glow);
        fill(graphics, x + 1, y + 14, 14, 1, glow);
        fill(graphics, x + 1, y + 2, 1, 12, glow);
        fill(graphics, x + 14, y + 2, 1, 12, glow);
    }

    private static void fill(GuiGraphics graphics, int x, int y, int width, int height, int argb) {
        graphics.fill(RenderType.guiOverlay(), x, y, x + width, y + height, argb);
    }

    private static int lerp(int from, int to, float t) {
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return (r << 16) | (g << 8) | b;
    }
}
