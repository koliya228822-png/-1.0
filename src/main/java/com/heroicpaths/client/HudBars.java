package com.heroicpaths.client;

import com.heroicpaths.combat.Stamina;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Полоски над хотбаром вместо ванильных сердечек: слева — здоровье (с числом, золотым запасом поглощения
 * и светлым «следом» только что потерянного здоровья), справа над едой — стамина бойца (у воина это его выносливость).
 * Обе встают в общий счёт высоты ванильного интерфейса, поэтому броня, воздух и здоровье коня рисуются над ними.
 */
public final class HudBars {
    private static final int WIDTH = 81;
    private static final int HEIGHT = 9;
    private static final int BORDER = 0xFF0A0A0A;
    private static final int EMPTY = 0xFF2B1A1A;
    private static final int HEALTH = 0xFFC8262E;
    private static final int POISON = 0xFF6E9A1E;
    private static final int WITHER = 0xFF4A4A4A;
    private static final int FROZEN = 0xFF7EC0E6;
    private static final int ABSORPTION = 0xFFE8C030;
    private static final int TRAIL = 0xFFF0C8C8;
    private static final int STAMINA_EMPTY = 0xFF262214;
    private static final int STAMINA = 0xFFD8B83A;
    private static final int STAMINA_LOW = 0xFFC03A2A;
    /** Ниже этого запаса полоска стамины мигает. */
    private static final float STAMINA_LOW_LEVEL = 15f;

    /** Здоровье, которое показывает след: оно догоняет настоящее не сразу. */
    private static float trail = -1;
    private static long lastFrame;

    private HudBars() {}

    /** Слева над хотбаром, на месте сердечек. */
    public static void renderHealth(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()
                || !(mc.getCameraEntity() instanceof Player player)) return;
        Gui gui = mc.gui;
        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - gui.leftHeight;
        gui.leftHeight += HEIGHT + 1;

        float health = Math.max(0f, player.getHealth());
        float max = Math.max(player.getMaxHealth(), health);
        float absorption = player.getAbsorptionAmount();
        float total = max + absorption;

        long now = Util.getMillis();
        float seconds = lastFrame == 0 ? 0f : Math.min(0.25f, (now - lastFrame) / 1000f);
        lastFrame = now;
        if (trail < health || trail > max) trail = health;
        else trail = Math.max(health, trail - seconds * Math.max(2f, max * 0.4f));

        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, BORDER);
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, EMPTY);
        int healthEnd = x + Math.round(WIDTH * health / total);
        int trailEnd = x + Math.round(WIDTH * trail / total);
        if (trailEnd > healthEnd) graphics.fill(healthEnd, y, trailEnd, y + HEIGHT, TRAIL);
        int color = healthColor(player);
        graphics.fill(x, y, healthEnd, y + HEIGHT, color);
        graphics.fill(x, y, healthEnd, y + 2, lighter(color)); // блик сверху
        if (absorption > 0) {
            int absorptionEnd = x + Math.round(WIDTH * (max + absorption) / total);
            int absorptionStart = x + Math.round(WIDTH * max / total);
            graphics.fill(absorptionStart, y, absorptionEnd, y + HEIGHT, ABSORPTION);
        }

        String text = Mth.ceil(health) + (absorption > 0 ? " +" + Mth.ceil(absorption) : "") + " / " + Math.round(max);
        drawCentered(graphics, mc.font, text, x + WIDTH / 2, y + 1);
    }

    /** Справа над едой; только у бойцов (у мага стамины нет). */
    public static void renderStamina(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.gameMode == null || !mc.gameMode.canHurtPlayer()
                || !(mc.getCameraEntity() instanceof Player player) || !Stamina.uses(player)) return;
        Gui gui = mc.gui;
        int right = graphics.guiWidth() / 2 + 91;
        int x = right - WIDTH;
        int y = graphics.guiHeight() - gui.rightHeight;
        gui.rightHeight += HEIGHT + 1;

        float max = Math.max(1f, Stamina.max(player));
        float stamina = Mth.clamp(Stamina.current(player), 0f, max);
        boolean low = stamina < STAMINA_LOW_LEVEL;
        boolean blink = low && (Util.getMillis() / 250) % 2 == 0;
        graphics.fill(x - 1, y - 1, right + 1, y + HEIGHT + 1, BORDER);
        graphics.fill(x, y, right, y + HEIGHT, STAMINA_EMPTY);
        int end = x + Math.round(WIDTH * stamina / max);
        int color = blink ? STAMINA_LOW : STAMINA;
        graphics.fill(x, y, end, y + HEIGHT, color);
        graphics.fill(x, y, end, y + 2, lighter(color));
        drawCentered(graphics, mc.font, String.valueOf(Mth.floor(stamina)), x + WIDTH / 2, y + 1);
    }

    private static int healthColor(Player player) {
        if (player.hasEffect(MobEffects.WITHER)) return WITHER;
        if (player.hasEffect(MobEffects.POISON)) return POISON;
        if (player.isFullyFrozen()) return FROZEN;
        return HEALTH;
    }

    private static int lighter(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 50);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 50);
        int b = Math.min(255, (color & 0xFF) + 50);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static void drawCentered(GuiGraphics graphics, Font font, String text, int centerX, int y) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, 0xFFFFFFFF, true);
    }
}
