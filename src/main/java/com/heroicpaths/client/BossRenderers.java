package com.heroicpaths.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.BlazeRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.StrayRenderer;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Stray;

/** Рендеры боссов данжей: ванильные модели и текстуры, увеличенные под размер хитбокса. */
public final class BossRenderers {
    private BossRenderers() {}

    public static class FlameTyrantRenderer extends BlazeRenderer {
        public FlameTyrantRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected void scale(Blaze entity, PoseStack poseStack, float partialTick) {
            poseStack.scale(2.2f, 2.2f, 2.2f);
        }
    }

    public static class SpiderQueenRenderer extends net.minecraft.client.renderer.entity.SpiderRenderer<com.heroicpaths.entity.SpiderQueen> {
        public SpiderQueenRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected void scale(com.heroicpaths.entity.SpiderQueen entity, PoseStack poseStack, float partialTick) {
            poseStack.scale(2.0f, 2.0f, 2.0f);
        }
    }

    public static class DrownedProphetRenderer extends net.minecraft.client.renderer.entity.DrownedRenderer {
        public DrownedProphetRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected void scale(net.minecraft.world.entity.monster.Drowned entity, PoseStack poseStack, float partialTick) {
            poseStack.scale(1.7f, 1.7f, 1.7f);
        }
    }

    public static class LichRenderer extends net.minecraft.client.renderer.entity.WitherSkeletonRenderer {
        public LichRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected void scale(net.minecraft.world.entity.monster.WitherSkeleton entity, PoseStack poseStack, float partialTick) {
            poseStack.scale(1.35f, 1.35f, 1.35f);
        }
    }

    public static class FrostLordRenderer extends StrayRenderer {
        public FrostLordRenderer(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected void scale(Stray entity, PoseStack poseStack, float partialTick) {
            poseStack.scale(1.8f, 1.8f, 1.8f);
        }
    }
}
