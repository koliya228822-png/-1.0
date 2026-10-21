package com.heroicpaths.client;

import com.heroicpaths.combat.MobLevels;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Над прицелом: имя моба, на которого смотрит игрок, и его уровень; цвет уровня — насколько моб опасен для игрока. */
public class MobInfoLayer implements LayeredDraw.Layer {
    /** Дальше этого расстояния мобов не подписываем. */
    private static final double RANGE = 32;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;
        LivingEntity target = target(mc, deltaTracker.getGameTimeDeltaPartialTick(true));
        if (target == null) return;
        int level = MobLevels.level(target);
        if (level <= 0) return;

        int diff = level - RpgManager.get(mc.player).getLevel();
        MutableComponent title = target.getDisplayName().copy().withStyle(ChatFormatting.WHITE).append("  ")
                .append(Component.translatable("hud.heroicpaths.mob_level", level).withColor(levelColor(diff)));
        int base = MobLevels.baseLevel(target);
        MobLevels.Resist resist = MobLevels.resist(target);
        MutableComponent details = null;
        if (base > 0 && level > base) {
            details = Component.translatable("hud.heroicpaths.mob_base", base);
            if (resist != null) details.append(" · ").append(Component.translatable("hud.heroicpaths.resist", resist.label()));
        }

        int center = graphics.guiWidth() / 2, y = graphics.guiHeight() / 2 - 34;
        int width = Math.max(mc.font.width(title), details == null ? 0 : mc.font.width(details)) + 8;
        int height = details == null ? 12 : 22;
        graphics.fill(center - width / 2, y - 2, center + width / 2, y - 2 + height, 0x90000000);
        graphics.drawCenteredString(mc.font, title, center, y, 0xFFFFFFFF);
        if (details != null) graphics.drawCenteredString(mc.font, details, center, y + 10, 0xFFB8B8B8);
    }

    /** Красный — моб на 5+ уровней выше, оранжевый — на 3–4, жёлтый — ровня, зелёный — слабее, серый — на 5+ ниже. */
    static int levelColor(int diff) {
        if (diff >= MobLevels.LEVEL_GAP) return 0xFF5050;
        if (diff >= 3) return 0xFFA040;
        if (diff > -3) return 0xFFE060;
        if (diff > -MobLevels.LEVEL_GAP) return 0x60E060;
        return 0xA0A0A0;
    }

    /** Живое существо под прицелом: сначала то, что выбрал ванильный прицел, иначе луч на RANGE блоков до первой стены. */
    private static LivingEntity target(Minecraft mc, float partialTick) {
        if (mc.crosshairPickEntity instanceof LivingEntity living) return living;
        Entity camera = mc.getCameraEntity();
        if (camera == null) return null;
        Vec3 eye = camera.getEyePosition(partialTick);
        Vec3 look = camera.getViewVector(partialTick);
        Vec3 end = eye.add(look.scale(RANGE));
        HitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, camera));
        if (block.getType() != HitResult.Type.MISS) end = block.getLocation();
        AABB box = camera.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(camera, eye, end, box,
                entity -> entity instanceof LivingEntity && entity.isPickable() && !entity.isSpectator() && !entity.isInvisibleTo(mc.player),
                eye.distanceToSqr(end));
        return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
    }
}
