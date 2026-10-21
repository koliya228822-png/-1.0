package com.heroicpaths.client;

import com.heroicpaths.HeroicPaths;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.WitherSkeletonRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.WitherSkeleton;

/** Король Склепа: модель и текстура скелета-иссушителя, увеличенные в 1.8 раза, и светящиеся фиолетовые глаза. */
public class CryptKingRenderer extends WitherSkeletonRenderer {
    private static final RenderType EYES = RenderType.eyes(HeroicPaths.id("textures/entity/crypt_king_eyes.png"));

    public CryptKingRenderer(EntityRendererProvider.Context context) {
        super(context);
        addLayer(new Eyes(this));
    }

    @Override
    protected void scale(WitherSkeleton entity, PoseStack poseStack, float partialTick) {
        poseStack.scale(1.8f, 1.8f, 1.8f);
    }

    private static final class Eyes extends EyesLayer<WitherSkeleton, SkeletonModel<WitherSkeleton>> {
        Eyes(RenderLayerParent<WitherSkeleton, SkeletonModel<WitherSkeleton>> parent) {
            super(parent);
        }

        @Override
        public RenderType renderType() {
            return EYES;
        }
    }
}
