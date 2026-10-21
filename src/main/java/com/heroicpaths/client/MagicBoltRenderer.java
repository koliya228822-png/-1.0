package com.heroicpaths.client;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.MagicBoltEntity;
import com.heroicpaths.fx.Palette;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Магическая стрела: светящийся сгусток в цвет стихии фокуса — ореол, два слоя бурлящей силы навстречу друг другу
 * и яркое ядро (белые текстуры, окрашенные гаммой стихии).
 */
public class MagicBoltRenderer extends EntityRenderer<MagicBoltEntity> {
    private static final ResourceLocation CORE = HeroicPaths.id("textures/entity/magic_bolt_core.png");
    private static final ResourceLocation GLOW = HeroicPaths.id("textures/entity/magic_bolt_glow.png");
    private static final ResourceLocation HOT = HeroicPaths.id("textures/entity/fireball_hot.png");
    private static final int FRAMES = 8;

    public MagicBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(MagicBoltEntity entity) {
        return CORE;
    }

    @Override
    public void render(MagicBoltEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (entity.tickCount < 2 && entityRenderDispatcher.camera.getEntity().distanceToSqr(entity) < 4) return;
        Palette p = entity.palette();
        float t = entity.tickCount + partialTick, pulse = 1 + 0.1f * Mth.sin(t * 1.1f);
        // У самой камеры (сразу после выстрела) свечение приглушено, чтобы не заливать экран.
        double distance = entityRenderDispatcher.camera.getPosition().distanceTo(entity.getPosition(partialTick));
        float near = Mth.clamp((float) (distance - 0.8) / 2.5f, 0.12f, 1f);
        int frame = (int) (t / 1.5f + entity.getId()) % FRAMES, other = (frame + FRAMES / 2) % FRAMES;
        pose.pushPose();
        pose.translate(0, entity.getBbHeight() / 2, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        quad(pose, buffers.getBuffer(RenderType.eyes(GLOW)), 0.6f * pulse, t * 3, 0, 1, p.r1 * near, p.g1 * near, p.b1 * near, 255);
        VertexConsumer core = buffers.getBuffer(RenderType.entityTranslucentEmissive(CORE));
        float mr = (p.r0 + p.r1) / 2, mg = (p.g0 + p.g1) / 2, mb = (p.b0 + p.b1) / 2;
        quad(pose, core, 0.3f * pulse, t * 10, frame / (float) FRAMES, (frame + 1) / (float) FRAMES, mr, mg, mb, (int) (255 * near));
        quad(pose, core, 0.26f * (2 - pulse), -t * 15, other / (float) FRAMES, (other + 1) / (float) FRAMES, p.r0, p.g0, p.b0, (int) (200 * near));
        quad(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(HOT)), 0.14f * pulse, 0, 0, 1, 1, 1, 1, (int) (235 * near));
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void quad(PoseStack pose, VertexConsumer out, float half, float spin, float v0, float v1, float r, float g, float b, int alpha) {
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(spin));
        PoseStack.Pose last = pose.last();
        int ri = (int) (r * 255), gi = (int) (g * 255), bi = (int) (b * 255);
        vertex(out, last, -half, -half, 0, v1, ri, gi, bi, alpha);
        vertex(out, last, half, -half, 1, v1, ri, gi, bi, alpha);
        vertex(out, last, half, half, 1, v0, ri, gi, bi, alpha);
        vertex(out, last, -half, half, 0, v0, ri, gi, bi, alpha);
        pose.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v, int r, int g, int b, int alpha) {
        out.addVertex(pose, x, y, 0).setColor(r, g, b, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }
}
