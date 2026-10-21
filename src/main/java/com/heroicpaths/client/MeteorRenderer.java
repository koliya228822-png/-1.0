package com.heroicpaths.client;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.MeteorEntity;
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
import net.minecraft.util.RandomSource;

/**
 * Метеор: раскалённая глыба размером в ранг умения (1, 2 или 3 блока) — куб с наростами, кувыркается в полёте;
 * вокруг неё корона огня (те же слои, что у огненного шара) и свечение.
 */
public class MeteorRenderer extends EntityRenderer<MeteorEntity> {
    private static final ResourceLocation ROCK = HeroicPaths.id("textures/entity/meteor.png");
    private static final ResourceLocation CORE = HeroicPaths.id("textures/entity/fireball_core.png");
    private static final ResourceLocation GLOW = HeroicPaths.id("textures/entity/fireball_glow.png");
    private static final int FRAMES = 8;

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public ResourceLocation getTextureLocation(MeteorEntity entity) {
        return ROCK;
    }

    @Override
    public void render(MeteorEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        float s = entity.size(), t = entity.tickCount + partialTick;
        // Глыба: основной куб и несколько наростов (у каждого метеора свои).
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(t * 6));
        pose.mulPose(Axis.XP.rotationDegrees(t * 9));
        VertexConsumer rock = buffers.getBuffer(RenderType.entityCutoutNoCull(ROCK));
        PoseStack.Pose last = pose.last();
        float h = s / 2;
        cube(rock, last, -h, -h, -h, h, h, h);
        RandomSource random = RandomSource.create(entity.getId() * 31L);
        for (int i = 0; i < 6; i++) {
            float c = s * (0.28f + random.nextFloat() * 0.14f);
            float ox = (random.nextFloat() - 0.5f) * s * 0.9f, oy = (random.nextFloat() - 0.5f) * s * 0.9f, oz = (random.nextFloat() - 0.5f) * s * 0.9f;
            // Нарост выступает из грани: сдвиг к ближайшей грани.
            int axis = random.nextInt(3);
            float out = (random.nextBoolean() ? 1 : -1) * (h + c * 0.2f);
            if (axis == 0) ox = out;
            else if (axis == 1) oy = out;
            else oz = out;
            cube(rock, last, ox - c, oy - c, oz - c, ox + c, oy + c, oz + c);
        }
        pose.popPose();
        // Корона огня и свечение вокруг глыбы.
        pose.pushPose();
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        float pulse = 1 + 0.06f * Mth.sin(t * 0.8f);
        billboard(pose, buffers.getBuffer(RenderType.eyes(GLOW)), 1.6f * s * pulse, t * 2, 0, 1, 255);
        int frame = (int) (t / 1.5f + entity.getId()) % FRAMES;
        VertexConsumer core = buffers.getBuffer(RenderType.entityTranslucentEmissive(CORE));
        billboard(pose, core, 0.95f * s * pulse, t * 7, frame / (float) FRAMES, (frame + 1) / (float) FRAMES, 255);
        int other = (frame + FRAMES / 2) % FRAMES;
        billboard(pose, core, 0.85f * s * (2 - pulse), -t * 11, other / (float) FRAMES, (other + 1) / (float) FRAMES, 200);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    private static void cube(VertexConsumer out, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1) {
        face(out, pose, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0);
        face(out, pose, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0);
        face(out, pose, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1);
        face(out, pose, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1);
        face(out, pose, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0);
        face(out, pose, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0);
    }

    private static void face(VertexConsumer out, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz) {
        vertex(out, pose, ax, ay, az, 0, 1, nx, ny, nz);
        vertex(out, pose, bx, by, bz, 1, 1, nx, ny, nz);
        vertex(out, pose, cx, cy, cz, 1, 0, nx, ny, nz);
        vertex(out, pose, dx, dy, dz, 0, 0, nx, ny, nz);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        out.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, nx, ny, nz);
    }

    private static void billboard(PoseStack pose, VertexConsumer out, float half, float spin, float v0, float v1, int alpha) {
        pose.pushPose();
        pose.mulPose(Axis.ZP.rotationDegrees(spin));
        PoseStack.Pose last = pose.last();
        flat(out, last, -half, -half, 0, v1, alpha);
        flat(out, last, half, -half, 1, v1, alpha);
        flat(out, last, half, half, 1, v0, alpha);
        flat(out, last, -half, half, 0, v0, alpha);
        pose.popPose();
    }

    private static void flat(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v, int alpha) {
        out.addVertex(pose, x, y, 0).setColor(255, 255, 255, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }
}
