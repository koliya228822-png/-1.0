package com.heroicpaths.client.spider;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.SpiderEggSac;
import com.heroicpaths.entity.SpiderQueen;
import com.heroicpaths.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Отрисовка Матери выводка, её пауков и кладок. */
public final class SpiderRenderers {
    public static final ModelLayerLocation BROODMOTHER = layer("broodmother");
    public static final ModelLayerLocation BROOD_SPIDER = layer("brood_spider");
    public static final ModelLayerLocation VENOM_SPITTER = layer("venom_spitter");
    public static final ModelLayerLocation BROOD_TANK = layer("brood_tank");
    public static final ModelLayerLocation EGG_SAC = layer("spider_egg_sac");

    private SpiderRenderers() {}

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(HeroicPaths.id(name), "main");
    }

    private static ResourceLocation texture(String name) {
        return HeroicPaths.id("textures/entity/spider/" + name + ".png");
    }

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(BROODMOTHER, SpiderModels::broodmother);
        event.registerLayerDefinition(BROOD_SPIDER, SpiderModels::broodSpider);
        event.registerLayerDefinition(VENOM_SPITTER, SpiderModels::venomSpitter);
        event.registerLayerDefinition(BROOD_TANK, SpiderModels::broodTank);
        event.registerLayerDefinition(EGG_SAC, SpiderModels::spiderEggSac);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SPIDER_QUEEN.get(), c -> new Renderer<SpiderQueen>(c, BROODMOTHER, "broodmother", SpiderQueen.MODEL_SCALE, 1.6f, 0.45f));
        event.registerEntityRenderer(ModEntities.BROOD_SPIDER.get(), c -> new Renderer<>(c, BROOD_SPIDER, "brood_spider", 1.0f, 0.6f, 0.9f));
        event.registerEntityRenderer(ModEntities.VENOM_SPITTER.get(), c -> new Renderer<>(c, VENOM_SPITTER, "venom_spitter", 1.0f, 0.6f, 0.9f));
        event.registerEntityRenderer(ModEntities.BROOD_TANK.get(), c -> new Renderer<>(c, BROOD_TANK, "brood_tank", 1.1f, 1.0f, 0.6f));
        event.registerEntityRenderer(ModEntities.SPIDER_EGG_SAC.get(), EggSacRenderer::new);
    }

    /** Светящийся слой: глаза, рога, кромка кокона, яйца и паучата светятся в темноте. */
    static <T extends LivingEntity, M extends EntityModel<T>> EyesLayer<T, M> glow(RenderLayerParent<T, M> parent, String name) {
        RenderType type = RenderType.eyes(texture(name + "_glow"));
        return new EyesLayer<>(parent) {
            @Override
            public RenderType renderType() {
                return type;
            }
        };
    }

    public static class Renderer<T extends Mob> extends MobRenderer<T, SpiderModel<T>> {
        private final ResourceLocation texture;
        private final float scale;

        public Renderer(EntityRendererProvider.Context context, ModelLayerLocation layer, String name, float scale, float shadow, float stride) {
            super(context, new SpiderModel<>(context.bakeLayer(layer), stride), shadow * scale);
            this.texture = texture(name);
            this.scale = scale;
            addLayer(glow(this, name));
        }

        @Override
        protected void scale(T entity, PoseStack poseStack, float partialTick) {
            poseStack.scale(scale, scale, scale);
        }

        /** Пауки умирают, переворачиваясь лапами кверху. */
        @Override
        protected float getFlipDegrees(T entity) {
            return 180f;
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return texture;
        }
    }

    /** Кладка: яйца покачиваются всё сильнее, чем ближе вылупление. */
    public static class EggSacRenderer extends MobRenderer<SpiderEggSac, EggSacModel> {
        private static final ResourceLocation TEXTURE = texture("spider_egg_sac");

        public EggSacRenderer(EntityRendererProvider.Context context) {
            super(context, new EggSacModel(context.bakeLayer(EGG_SAC)), 0.4f);
            addLayer(glow(this, "spider_egg_sac"));
        }

        @Override
        public ResourceLocation getTextureLocation(SpiderEggSac entity) {
            return TEXTURE;
        }
    }

    public static class EggSacModel extends HierarchicalModel<SpiderEggSac> {
        private final ModelPart root, body;

        public EggSacModel(ModelPart root) {
            super(RenderType::entityCutoutNoCull);
            this.root = root;
            this.body = root.getChild("body");
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(SpiderEggSac entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            body.resetPose();
            float progress = Math.min(1f, ageInTicks / SpiderEggSac.HATCH_TICKS);
            float wobble = Mth.sin(ageInTicks * (0.3f + progress * 0.9f)) * (0.02f + progress * progress * 0.12f);
            body.zRot = wobble;
            body.xRot = wobble * 0.6f;
            float swell = 1 + progress * 0.12f + Math.max(0, Mth.sin(ageInTicks * 0.8f)) * progress * 0.05f;
            body.xScale = body.zScale = swell;
            body.yScale = swell;
        }
    }
}
