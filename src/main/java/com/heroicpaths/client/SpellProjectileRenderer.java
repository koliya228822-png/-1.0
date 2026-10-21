package com.heroicpaths.client;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.SpellProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Снаряды заклинаний. Огненный шар — живой светящийся шар: мягкий ореол, два слоя языков пламени
 * (кадры анимации из fireball_core.png), которые крутятся навстречу друг другу, и пульсирующее раскалённое ядро.
 * Остальные снаряды — предметом.
 */
public class SpellProjectileRenderer extends ThrownItemRenderer<SpellProjectile> {
    private static final ResourceLocation CORE = HeroicPaths.id("textures/entity/fireball_core.png");
    private static final ResourceLocation GLOW = HeroicPaths.id("textures/entity/fireball_glow.png");
    private static final ResourceLocation HOT = HeroicPaths.id("textures/entity/fireball_hot.png");
    private static final int FRAMES = 8;

    public SpellProjectileRenderer(EntityRendererProvider.Context context) {
        super(context, 1.0F, true);
    }

    @Override
    public void render(SpellProjectile entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!entity.isFireball()) {
            super.render(entity, yaw, partialTick, pose, buffers, light);
            return;
        }
        // Как у брошенных предметов: в первые тики не загораживать обзор тому, кто бросил.
        if (entity.tickCount < 2 && entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) < 12.25) return;
        float t = entity.tickCount + partialTick, pulse = 1 + 0.08F * Mth.sin(t * 0.9F);
        int frame = (int) (t / 1.5F + entity.getId()) % FRAMES;
        pose.pushPose();
        pose.translate(0, entity.getBbHeight() / 2, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        // Ореол — свечение, складывается со светом за ним: ночью заметен, днём почти не виден, как настоящий огонь.
        quad(pose, buffers.getBuffer(RenderType.eyes(GLOW)), 1.1F * pulse, t * 3, 0, 0, 1, 255);
        // Два слоя пламени почти одного размера: красные края совпадают, а не ложатся кольцом на раскалённую середину.
        VertexConsumer core = buffers.getBuffer(RenderType.entityTranslucentEmissive(CORE));
        quad(pose, core, 0.56F * pulse, t * 9, 0.01F, frame / (float) FRAMES, (frame + 1) / (float) FRAMES, 255);
        int inner = (frame + FRAMES / 2) % FRAMES;
        quad(pose, core, 0.5F * (2 - pulse), -t * 14, 0.02F, inner / (float) FRAMES, (inner + 1) / (float) FRAMES, 190);
        quad(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(HOT)), 0.3F * pulse, 0, 0.03F, 0, 1, 235);
        pose.popPose();
    }

    /** Квадрат лицом к камере, повёрнутый на spin градусов; v0..v1 — кадр в полосе кадров. */
    private static void quad(PoseStack pose, VertexConsumer out, float half, float spin, float z, float v0, float v1, int alpha) {
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(spin));
        PoseStack.Pose last = pose.last();
        vertex(out, last, -half, -half, z, 0, v1, alpha);
        vertex(out, last, half, -half, z, 1, v1, alpha);
        vertex(out, last, half, half, z, 1, v0, alpha);
        vertex(out, last, -half, half, z, 0, v0, alpha);
        pose.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, float u, float v, int alpha) {
        // Нормаль «вверх» в мире, без поворота квадрата: иначе освещение сущностей затеняет вращающиеся слои пятнами.
        out.addVertex(pose, x, y, z).setColor(255, 255, 255, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }
}
