package com.heroicpaths.client;

import com.heroicpaths.item.FocusItem;
import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** Полоска каста под прицелом: название умения и сколько осталось; без каста — заряд стрелы жезла или посоха. */
public class CastBarLayer implements LayeredDraw.Layer {
    private static String ability;
    private static long startTick;
    private static int duration;

    /** Идёт ли сейчас каст (по последнему пакету с сервера). */
    public static boolean casting() {
        return ability != null;
    }

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
        if (mc.level == null || mc.player == null || mc.options.hideGui) return;
        if (ability == null) {
            renderFocusCharge(graphics, deltaTracker, mc);
            return;
        }
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

    /** Заряд магической стрелы жезла или посоха (ПКМ): сила выстрела, на полном заряде полоска светлеет. */
    private static void renderFocusCharge(GuiGraphics graphics, DeltaTracker deltaTracker, Minecraft mc) {
        if (!mc.player.isUsingItem() || !(mc.player.getUseItem().getItem() instanceof FocusItem)) return;
        float ticks = mc.player.getTicksUsingItem() + deltaTracker.getGameTimeDeltaPartialTick(false);
        ItemStack focus = mc.player.getUseItem();
        float k = FocusItem.charge(focus, (int) ticks);
        boolean full = ticks >= FocusItem.chargeTicks(focus);
        int w = 80, h = 5, x = (graphics.guiWidth() - w) / 2, y = graphics.guiHeight() / 2 + 14;
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
        graphics.fill(x, y, x + w, y + h, 0x80201830);
        int fill = Math.round(w * Mth.clamp(ticks / FocusItem.chargeTicks(focus), 0, 1));
        graphics.fill(x, y, x + fill, y + h, full ? 0xFFC8A0FF : 0xFF7850D0);
        graphics.fill(x, y, x + fill, y + 1, full ? 0xFFFFFFFF : 0xFFB8A0F0);
        Component label = full ? Component.translatable("hud.heroicpaths.charge_full")
                : Component.translatable("hud.heroicpaths.charge", Math.round(k * 100));
        graphics.drawCenteredString(mc.font, label, graphics.guiWidth() / 2, y + h + 3, full ? 0xFFE8D8FF : 0xFFB8A8E0);
    }
}
