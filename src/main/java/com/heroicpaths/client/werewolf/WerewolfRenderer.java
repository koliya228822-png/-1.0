package com.heroicpaths.client.werewolf;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.Werewolf;
import com.heroicpaths.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Отрисовка оборотня: своя модель и текстура, глаза светятся в темноте. */
public class WerewolfRenderer extends MobRenderer<Werewolf, WerewolfModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(HeroicPaths.id("werewolf"), "main");
    private static final ResourceLocation TEXTURE = HeroicPaths.id("textures/entity/werewolf/werewolf.png");
    private static final RenderType GLOW = RenderType.eyes(HeroicPaths.id("textures/entity/werewolf/werewolf_glow.png"));

    public WerewolfRenderer(EntityRendererProvider.Context context) {
        super(context, new WerewolfModel(context.bakeLayer(LAYER)), 0.7f);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return GLOW;
            }
        });
    }

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER, WerewolfModels::werewolf);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.WEREWOLF.get(), WerewolfRenderer::new);
    }

    @Override
    protected void scale(Werewolf entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(Werewolf.MODEL_SCALE, Werewolf.MODEL_SCALE, Werewolf.MODEL_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(Werewolf entity) {
        return TEXTURE;
    }
}
