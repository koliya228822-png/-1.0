package com.heroicpaths.client;

import com.heroicpaths.combat.MobLevels;
import com.heroicpaths.rpg.RpgManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

/**
 * Маленькая плашка над головой моба: уровень (цвет — насколько моб опасен для игрока), значок сопротивления и имя,
 * а под ними полоска здоровья с числом «текущее / максимум». Буквы с тёмной обводкой; видна, только когда игрок ближе к мобу, чем задано в настройках (по умолчанию 9 блоков).
 */
public final class MobPlates {
    private MobPlates() {}

    private static final float SCALE = 0.014f;
    private static final int OUTLINE = 0xFF141018;
    /** Полоска здоровья под надписью, в пикселях шрифта. */
    private static final int BAR_WIDTH = 60;
    private static final int BAR_HEIGHT = 9;

    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity mob = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mob == player || mc.options.hideGui) return;
        int level = MobLevels.level(mob);
        if (level <= 0 || !mob.isAlive() || mob.isInvisibleTo(player)) return;
        float gap = (float) Math.sqrt(player.distanceToSqr(mob)) - mob.getBbWidth() / 2f;
        int distance = ClientConfig.plateDistance(); // от игрока до края моба; 0 — плашки выключены
        if (distance <= 0 || gap > distance) return;

        MutableComponent plate = Component.empty().append(Component.translatable("hud.heroicpaths.mob_level", level)
                .withColor(MobInfo.levelColor(level - RpgManager.get(player).getLevel())));
        MobLevels.Resist resist = MobLevels.resist(mob);
        if (resist != null) plate.append(Component.literal(" " + MobInfo.resistIcon(resist)).withColor(MobInfo.resistColor(resist)));
        plate.append(Component.literal(" ").append(mob.getDisplayName()).withColor(0xD8D2C8));

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        float lift = mob.shouldShowName() ? 0.85f : 0.5f; // над ванильной табличкой с именем, если она есть
        pose.translate(0, mob.getBbHeight() + lift, 0);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(SCALE, -SCALE, SCALE);
        Font font = mc.font;
        Matrix4f matrix = pose.last().pose();
        font.drawInBatch8xOutline(plate.getVisualOrderText(), -font.width(plate) / 2f, 0, 0xFFFFFFFF, OUTLINE,
                matrix, event.getMultiBufferSource(), LightTexture.FULL_BRIGHT);
        renderHealthBar(mob, font, matrix, event.getMultiBufferSource(), 11);
        pose.popPose();
    }

    /** Полоска здоровья: зелёная, при половине — жёлтая, при четверти — красная; поверх — число. */
    private static void renderHealthBar(LivingEntity mob, Font font, Matrix4f matrix, MultiBufferSource buffers, int top) {
        float max = Math.max(1f, mob.getMaxHealth());
        float health = Mth.clamp(mob.getHealth(), 0f, max);
        float fraction = health / max;
        int color = fraction > 0.5f ? 0xFF4CC24C : fraction > 0.25f ? 0xFFE0C030 : 0xFFD23C3C;
        float left = -BAR_WIDTH / 2f;
        VertexConsumer quads = buffers.getBuffer(RenderType.textBackground());
        // Подложка дальше от камеры, заливка между ней и числом — как фон ванильной таблички с именем.
        quad(quads, matrix, left - 1, top - 1, left + BAR_WIDTH + 1, top + BAR_HEIGHT + 1, 0.02f, 0xC0141018);
        quad(quads, matrix, left, top, left + BAR_WIDTH * fraction, top + BAR_HEIGHT, 0.01f, color);
        String text = Mth.ceil(health) + " / " + Math.round(max);
        font.drawInBatch8xOutline(Component.literal(text).getVisualOrderText(), -font.width(text) / 2f, top + 1, 0xFFFFFFFF, OUTLINE,
                matrix, buffers, LightTexture.FULL_BRIGHT);
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, float x0, float y0, float x1, float y1, float z, int color) {
        if (x1 <= x0) return;
        consumer.addVertex(matrix, x0, y0, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(matrix, x0, y1, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(matrix, x1, y1, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
        consumer.addVertex(matrix, x1, y0, z).setColor(color).setLight(LightTexture.FULL_BRIGHT);
    }
}
