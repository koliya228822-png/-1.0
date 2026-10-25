package com.heroicpaths.client;

import com.heroicpaths.combat.PowerAttack;
import com.heroicpaths.network.CombatInputPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Боевой ввод на клиенте:
 * <ul>
 *     <li>силовая атака — зажать правую кнопку с оружием ближнего боя, отпустить, когда полоска под прицелом заполнится;</li>
 *     <li>двойное нажатие пробела во время каста отменяет его.</li>
 * </ul>
 */
public final class ClientCombat {
    /** Двойное нажатие пробела: второе нажатие не позже стольких тиков после первого. */
    private static final int DOUBLE_TAP_TICKS = 7;

    private static int heldTicks;
    private static boolean wasUseDown;
    private static boolean wasJumpDown;
    private static long lastJumpTick = -100;

    private ClientCombat() {}

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.screen != null) {
            heldTicks = 0;
            wasUseDown = false;
            return;
        }
        tickPowerAttack(minecraft, player);
        tickCancelCast(minecraft, player);
    }

    private static void tickPowerAttack(Minecraft minecraft, LocalPlayer player) {
        boolean down = minecraft.options.keyUse.isDown();
        boolean able = (PowerAttack.canPowerAttack(player.getMainHandItem()) || com.heroicpaths.rpg.ability.UniqueAbilities.werewolf(player))
                && !player.isUsingItem() && !interactsWithEntity(minecraft);
        if (down && able) {
            heldTicks++;
        } else if (wasUseDown && !down && heldTicks >= PowerAttack.CHARGE_TICKS) {
            PacketDistributor.sendToServer(new CombatInputPayload(CombatInputPayload.Kind.POWER_ATTACK, heldTicks));
            heldTicks = 0;
        } else {
            heldTicks = 0;
        }
        wasUseDown = down;
    }

    /** Под прицелом мирное существо (житель, ездовое животное): правая кнопка — это разговор, а не удар. */
    private static boolean interactsWithEntity(Minecraft minecraft) {
        HitResult hit = minecraft.hitResult;
        return hit != null && hit.getType() == HitResult.Type.ENTITY && !(((EntityHitResult) hit).getEntity() instanceof Enemy);
    }

    private static void tickCancelCast(Minecraft minecraft, LocalPlayer player) {
        boolean down = minecraft.options.keyJump.isDown();
        if (down && !wasJumpDown) {
            long now = player.level().getGameTime();
            if (CastBarLayer.casting() && now - lastJumpTick <= DOUBLE_TAP_TICKS) {
                PacketDistributor.sendToServer(new CombatInputPayload(CombatInputPayload.Kind.CANCEL_CAST, 0));
                lastJumpTick = -100;
            } else {
                lastJumpTick = now;
            }
        }
        wasJumpDown = down;
    }

    /** Полоска заряда силовой атаки под прицелом (и сколько осталось до готовности). */
    public static class Layer implements LayeredDraw.Layer {
        @Override
        public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.options.hideGui || heldTicks <= 2) return;
            long remaining = PowerAttack.remaining(mc.player);
            float k = Mth.clamp((float) heldTicks / PowerAttack.CHARGE_TICKS, 0, 1);
            int w = 60, h = 4, x = (graphics.guiWidth() - w) / 2, y = graphics.guiHeight() / 2 + 12;
            graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
            int color = remaining > 0 ? 0xFF707070 : k >= 1 ? 0xFFFF5030 : 0xFFE0A040;
            graphics.fill(x, y, x + Math.round(w * k), y + h, color);
            if (remaining > 0) {
                graphics.drawCenteredString(mc.font, Component.translatable("hud.heroicpaths.power_attack_wait",
                        String.format(java.util.Locale.ROOT, "%.1f", remaining / 20f)), graphics.guiWidth() / 2, y + h + 2, 0xFFB0B0B0);
            } else if (k >= 1) {
                graphics.drawCenteredString(mc.font, Component.translatable("hud.heroicpaths.power_attack_ready"),
                        graphics.guiWidth() / 2, y + h + 2, 0xFFFF8060);
            }
        }
    }
}
