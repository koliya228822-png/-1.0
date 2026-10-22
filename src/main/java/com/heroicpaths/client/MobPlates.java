package com.heroicpaths.client;

import com.heroicpaths.combat.MobLevels;
import com.heroicpaths.rpg.RpgManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Маленькая плашка над головой моба: уровень (цвет — насколько моб опасен для игрока), значок сопротивления и имя.
 * Без подложки, буквы с тёмной обводкой; видна, только когда игрок подошёл к мобу примерно на 3 блока.
 */
public final class MobPlates {
    private MobPlates() {}

    /** От игрока до края моба. */
    private static final float SHOW_DISTANCE = 3f;
    private static final float SCALE = 0.014f;
    private static final int OUTLINE = 0xFF141018;

    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity mob = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mob == player || mc.options.hideGui) return;
        int level = MobLevels.level(mob);
        if (level <= 0 || !mob.isAlive() || mob.isInvisibleTo(player)) return;
        float gap = (float) Math.sqrt(player.distanceToSqr(mob)) - mob.getBbWidth() / 2f;
        if (gap > SHOW_DISTANCE) return;

        MutableComponent plate = Component.empty().append(Component.translatable("hud.heroicpaths.mob_level", level)
                .withColor(MobInfo.levelColor(level - RpgManager.get(player).getLevel())));
        MobLevels.Resist resist = MobLevels.resist(mob);
        if (resist != null) plate.append(Component.literal(" " + MobInfo.resistIcon(resist)).withColor(MobInfo.resistColor(resist)));
        plate.append(Component.literal(" ").append(mob.getDisplayName()).withColor(0xD8D2C8));

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        float lift = mob.shouldShowName() ? 0.7f : 0.35f; // над ванильной табличкой с именем, если она есть
        pose.translate(0, mob.getBbHeight() + lift, 0);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(SCALE, -SCALE, SCALE);
        Font font = mc.font;
        font.drawInBatch8xOutline(plate.getVisualOrderText(), -font.width(plate) / 2f, 0, 0xFFFFFFFF, OUTLINE,
                pose.last().pose(), event.getMultiBufferSource(), LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
