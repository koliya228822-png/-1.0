package com.heroicpaths.client.clay;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.ClayColossus;
import com.heroicpaths.entity.ClayDebris;
import com.heroicpaths.entity.ClayGolem;
import com.heroicpaths.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Отрисовка глиняных големов, колосса, его обломков и глиняных снарядов. */
public final class ClayRenderers {
    public static final ModelLayerLocation COLOSSUS = layer("clay_colossus");
    public static final ModelLayerLocation GOLEM = layer("clay_golem");
    public static final ModelLayerLocation THROWER = layer("clay_thrower");
    public static final ModelLayerLocation BRUTE = layer("clay_brute");

    private ClayRenderers() {}

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(HeroicPaths.id(name), "main");
    }

    private static ResourceLocation texture(String name) {
        return HeroicPaths.id("textures/entity/clay/" + name + ".png");
    }

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(COLOSSUS, ClayModels::clayColossus);
        event.registerLayerDefinition(GOLEM, ClayModels::clayGolem);
        event.registerLayerDefinition(THROWER, ClayModels::clayThrower);
        event.registerLayerDefinition(BRUTE, ClayModels::clayBrute);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CLAY_COLOSSUS.get(), ColossusRenderer::new);
        event.registerEntityRenderer(ModEntities.CLAY_GOLEM.get(), c -> new GolemRenderer<>(c, GOLEM, "clay_golem", 0.8f, 0.5f));
        event.registerEntityRenderer(ModEntities.CLAY_THROWER.get(), c -> new GolemRenderer<>(c, THROWER, "clay_thrower", 0.85f, 0.45f));
        event.registerEntityRenderer(ModEntities.CLAY_BRUTE.get(), c -> new GolemRenderer<>(c, BRUTE, "clay_brute", 1.3f, 0.9f));
        event.registerEntityRenderer(ModEntities.CLAY_DEBRIS.get(), DebrisRenderer::new);
        event.registerEntityRenderer(ModEntities.CLAY_SHOT.get(), c -> new ThrownItemRenderer<>(c, 1.0f, false));
        event.registerEntityRenderer(ModEntities.CLAY_BOULDER.get(), c -> new ThrownItemRenderer<>(c, 3.0f, false));
    }

    /** Светящийся слой: глаза и трещины ядра светятся в темноте. */
    static <T extends net.minecraft.world.entity.LivingEntity, M extends EntityModel<T>> EyesLayer<T, M> glow(RenderLayerParent<T, M> parent, String name) {
        RenderType type = RenderType.eyes(texture(name + "_glow"));
        return new EyesLayer<>(parent) {
            @Override
            public RenderType renderType() {
                return type;
            }
        };
    }

    public static class GolemRenderer<T extends ClayGolem> extends MobRenderer<T, ClayGolemModel<T>> {
        private final ResourceLocation texture;
        private final float scale;

        public GolemRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, String name, float scale, float shadow) {
            super(context, new ClayGolemModel<>(context.bakeLayer(layer)), shadow * scale);
            this.texture = texture(name);
            this.scale = scale;
            addLayer(glow(this, name));
        }

        @Override
        protected void scale(T entity, PoseStack poseStack, float partialTick) {
            poseStack.scale(scale, scale, scale);
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return texture;
        }
    }

    public static class ColossusRenderer extends MobRenderer<ClayColossus, ClayColossusModel> {
        private static final ResourceLocation TEXTURE = texture("clay_colossus");

        public ColossusRenderer(EntityRendererProvider.Context context) {
            super(context, new ClayColossusModel(context.bakeLayer(COLOSSUS)), 1.8f);
            addLayer(glow(this, "clay_colossus"));
        }

        @Override
        protected void scale(ClayColossus entity, PoseStack poseStack, float partialTick) {
            poseStack.scale(ClayColossus.MODEL_SCALE, ClayColossus.MODEL_SCALE, ClayColossus.MODEL_SCALE);
        }

        @Override
        public ResourceLocation getTextureLocation(ClayColossus entity) {
            return TEXTURE;
        }
    }

    /** Отвалившаяся часть колосса: та же деталь модели в том же масштабе, кувыркается в полёте и рассыпается в конце. */
    public static class DebrisRenderer extends EntityRenderer<ClayDebris> {
        private static final ResourceLocation TEXTURE = texture("clay_colossus");
        private final ClayColossusModel model;

        public DebrisRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.model = new ClayColossusModel(context.bakeLayer(COLOSSUS));
            this.shadowRadius = 0.6f;
        }

        @Override
        public void render(ClayDebris entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
            ModelPart part = model.part(entity.part());
            if (part == null) return;
            float age = entity.tickCount + partialTick;
            float spin = entity.onGround() ? 0 : entity.spin();
            float fade = Math.max(0f, (age - (ClayDebris.LIFE - 30)) / 30f);
            poseStack.pushPose();
            poseStack.translate(0, 0.3 - fade * 0.6, 0);
            poseStack.mulPose(Axis.YP.rotationDegrees(180 - entity.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(Math.min(age, 25f) * spin));
            float s = ClayColossus.MODEL_SCALE * (1 - 0.3f * fade);
            poseStack.scale(-s, -s, s);
            part.resetPose();
            part.x = part.y = part.z = 0;
            part.xRot = part.yRot = part.zRot = 0;
            part.visible = true;
            part.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
            super.render(entity, yaw, partialTick, poseStack, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(ClayDebris entity) {
            return TEXTURE;
        }
    }
}
