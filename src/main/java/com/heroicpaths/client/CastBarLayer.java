package com.heroicpaths.client;

import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Полоска каста под прицелом: название умения и сколько осталось. */
public class CastBarLayer implements LayeredDraw.Layer {
    private static String ability;
    private static long startTick;
    private static int duration;

    /** Пакет с сервера: каст начался (ticks > 0) или закончился (0). */
    public static void set(String id, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (ticks <= 0 || mc.level == null) {
            ability = null;
            return;
        }
        ability = id;
        duration = ticks;
        startTick = mc.level.getGameTime();
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (ability == null || mc.level == null || mc.player == null || mc.options.hideGui) return;
        float elapsed = mc.level.getGameTime() - startTick + deltaTracker.getGameTimeDeltaPartialTick(false);
        if (elapsed > duration + 10) {
            ability = null;
            return;
        }
        float k = Mth.clamp(elapsed / duration, 0, 1);
        AbilityDef def = AbilityRegistry.get(ability);
        int w = 120, h = 7, x = (graphics.guiWidth() - w) / 2, y = graphics.guiHeight() / 2 + 22;
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
        graphics.fill(x, y, x + w, y + h, 0x80302010);
        int fill = Math.round(w * k);
        graphics.fill(x, y, x + fill, y + h, 0xFFE8A030);
        graphics.fill(x, y, x + fill, y + 2, 0xFFFFE0A0);
        Component name = def == null ? Component.literal(ability) : def.name();
        String left = String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, (duration - elapsed) / 20f));
        graphics.drawCenteredString(mc.font, Component.translatable("hud.heroicpaths.casting", name, left), graphics.guiWidth() / 2, y + h + 3, 0xFFF0D8A0);
    }
}
