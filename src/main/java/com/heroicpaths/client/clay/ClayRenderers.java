package com.heroicpaths.client.clay;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.ClayColossus;
import com.heroicpaths.entity.ClayDebris;
import com.heroicpaths.entity.ClayGolem;
import com.heroicpaths.entity.FrostLich;
import com.heroicpaths.entity.FrozenArcher;
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
    public static final ModelLayerLocation LICH = layer("frost_lich");
    public static final ModelLayerLocation ICE_GOLEM = layer("ice_golem");
    public static final ModelLayerLocation ARCHER = layer("frozen_archer");
    public static final ModelLayerLocation SNOW_BRUTE = layer("snow_brute");

    private ClayRenderers() {}

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(HeroicPaths.id(name), "main");
    }

    private static ResourceLocation texture(String name) {
        // Без папки — текстура Глиняных копей; иначе «frost/ice_golem» и т. п.
        return HeroicPaths.id("textures/entity/" + (name.contains("/") ? name : "clay/" + name) + ".png");
    }

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(COLOSSUS, ClayModels::clayColossus);
        event.registerLayerDefinition(GOLEM, ClayModels::clayGolem);
        event.registerLayerDefinition(THROWER, ClayModels::clayThrower);
        event.registerLayerDefinition(BRUTE, ClayModels::clayBrute);
        event.registerLayerDefinition(LICH, ClayModels::frostLich);
        event.registerLayerDefinition(ICE_GOLEM, ClayModels::iceGolem);
        event.registerLayerDefinition(ARCHER, ClayModels::frozenArcher);
        event.registerLayerDefinition(SNOW_BRUTE, ClayModels::snowBrute);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CLAY_COLOSSUS.get(), ColossusRenderer::new);
        event.registerEntityRenderer(ModEntities.CLAY_GOLEM.get(), c -> new GolemRenderer<>(c, GOLEM, "clay_golem", 0.8f, 0.5f));
        event.registerEntityRenderer(ModEntities.CLAY_THROWER.get(), c -> new GolemRenderer<>(c, THROWER, "clay_thrower", 0.85f, 0.45f));
        event.registerEntityRenderer(ModEntities.CLAY_BRUTE.get(), c -> new GolemRenderer<>(c, BRUTE, "clay_brute", 1.3f, 0.9f));
        event.registerEntityRenderer(ModEntities.FROST_LICH.get(), LichRenderer::new);
        event.registerEntityRenderer(ModEntities.ICE_GOLEM.get(), c -> new GolemRenderer<>(c, ICE_GOLEM, "frost/ice_golem", 0.95f, 0.5f));
        event.registerEntityRenderer(ModEntities.SNOW_BRUTE.get(), c -> new GolemRenderer<>(c, SNOW_BRUTE, "frost/snow_brute", 1.1f, 0.9f));
        event.registerEntityRenderer(ModEntities.FROZEN_ARCHER.get(), ArcherRenderer::new);
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

    public static class LichRenderer extends MobRenderer<FrostLich, FrostLichModel> {
        private static final ResourceLocation TEXTURE = texture("frost/frost_lich");

        public LichRenderer(EntityRendererProvider.Context context) {
            super(context, new FrostLichModel(context.bakeLayer(LICH)), 0.9f);
            addLayer(glow(this, "frost/frost_lich"));
        }

        @Override
        protected void scale(FrostLich entity, PoseStack poseStack, float partialTick) {
            poseStack.scale(FrostLich.MODEL_SCALE, FrostLich.MODEL_SCALE, FrostLich.MODEL_SCALE);
        }

        @Override
        public ResourceLocation getTextureLocation(FrostLich entity) {
            return TEXTURE;
        }
    }

    /** Замороженный лучник: модель скелета с луком, но своя — вмёрзшая в лёд. */
    public static class ArcherRenderer extends net.minecraft.client.renderer.entity.HumanoidMobRenderer<FrozenArcher,
            net.minecraft.client.model.SkeletonModel<FrozenArcher>> {
        private static final ResourceLocation TEXTURE = texture("frost/frozen_archer");

        public ArcherRenderer(EntityRendererProvider.Context context) {
            super(context, new net.minecraft.client.model.SkeletonModel<>(context.bakeLayer(ARCHER)), 0.5f);
            addLayer(glow(this, "frost/frozen_archer"));
        }

        @Override
        public ResourceLocation getTextureLocation(FrozenArcher entity) {
            return TEXTURE;
        }

        @Override
        protected boolean isShaking(FrozenArcher entity) {
            return false;
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

    /** Отвалившаяся часть босса: та же деталь его модели в том же масштабе, кувыркается в полёте и рассыпается в конце. */
    public static class DebrisRenderer extends EntityRenderer<ClayDebris> {
        private static final ResourceLocation COLOSSUS_TEXTURE = texture("clay_colossus");
        private static final ResourceLocation LICH_TEXTURE = texture("frost/frost_lich");
        private final ClayColossusModel colossus;
        private final FrostLichModel lich;

        public DebrisRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.colossus = new ClayColossusModel(context.bakeLayer(COLOSSUS));
            this.lich = new FrostLichModel(context.bakeLayer(LICH));
            this.shadowRadius = 0.6f;
        }

        @Override
        public void render(ClayDebris entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
            boolean isLich = entity.model() == ClayDebris.LICH;
            ModelPart part = isLich ? lich.part(entity.part()) : colossus.part(entity.part());
            if (part == null) return;
            float age = entity.tickCount + partialTick;
            float fade = Math.max(0f, (age - (ClayDebris.LIFE - 30)) / 30f);
            part.resetPose();
            part.x = part.y = part.z = 0;
            part.xRot = part.yRot = part.zRot = 0;
            part.visible = true;
            // Границы детали в пикселях модели: её рисуем по центру, а не вокруг шарнира.
            float[] box = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            part.visit(new PoseStack(), (pose, path, index, cube) -> {
                box[0] = Math.min(box[0], cube.minX);
                box[1] = Math.min(box[1], cube.minY);
                box[2] = Math.min(box[2], cube.minZ);
                box[3] = Math.max(box[3], cube.maxX);
                box[4] = Math.max(box[4], cube.maxY);
                box[5] = Math.max(box[5], cube.maxZ);
            });
            float s = (isLich ? FrostLich.MODEL_SCALE : ClayColossus.MODEL_SCALE) * (1 - 0.3f * fade);
            // В полёте деталь кувыркается; упав, ложится плашмя: угол округляется до 90° и больше не меняется.
            boolean landed = entity.landedTick >= 0;
            float angle = (landed ? Math.min(entity.landedTick, 25) : Math.min(age, 25f)) * entity.spin();
            if (landed) angle = Math.round(angle / 90f) * 90f;
            boolean onSide = Math.floorMod(Math.round(angle / 90f), 2) == 1;
            float height = (onSide ? box[5] - box[2] : box[4] - box[1]) * s / 16f;
            poseStack.pushPose();
            poseStack.translate(0, height / 2 + 0.01 - (landed ? fade * height : 0), 0);
            poseStack.mulPose(Axis.YP.rotationDegrees(180 - entity.getYRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(angle));
            poseStack.scale(-s, -s, s);
            poseStack.translate(-(box[0] + box[3]) / 32f, -(box[1] + box[4]) / 32f, -(box[2] + box[5]) / 32f);
            part.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(isLich ? LICH_TEXTURE : COLOSSUS_TEXTURE)), light, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
            super.render(entity, yaw, partialTick, poseStack, buffers, light);
        }

        @Override
        public ResourceLocation getTextureLocation(ClayDebris entity) {
            return entity.model() == ClayDebris.LICH ? LICH_TEXTURE : COLOSSUS_TEXTURE;
        }
    }
}
