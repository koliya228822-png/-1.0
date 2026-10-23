package com.heroicpaths.client;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.VillageGuard;
import com.heroicpaths.registry.ModEntities;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BowItem;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Стражник: тело человека, голова жителя с большим носом, шлем-капеллина (мечник) или зелёный капюшон (лучник)
 * поверх головы, сюрко с гербом поверх кольчуги. Текстуры рисует tools/GuardGen.java.
 */
public class GuardRenderer extends HumanoidMobRenderer<VillageGuard, GuardRenderer.GuardModel> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(HeroicPaths.id("village_guard"), "main");
    private static final ResourceLocation SWORDSMAN = HeroicPaths.id("textures/entity/guard/guard_swordsman.png");
    private static final ResourceLocation ARCHER = HeroicPaths.id("textures/entity/guard/guard_archer.png");

    public GuardRenderer(EntityRendererProvider.Context context) {
        super(context, new GuardModel(context.bakeLayer(LAYER)), 0.5f);
    }

    @Override
    public ResourceLocation getTextureLocation(VillageGuard guard) {
        return guard.isArcher() ? ARCHER : SWORDSMAN;
    }

    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LAYER, GuardModel::createLayer);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.GUARD_SWORDSMAN.get(), GuardRenderer::new);
        event.registerEntityRenderer(ModEntities.GUARD_ARCHER.get(), GuardRenderer::new);
    }

    public static class GuardModel extends HumanoidModel<VillageGuard> {
        public GuardModel(ModelPart root) {
            super(root);
        }

        public static LayerDefinition createLayer() {
            MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0f);
            PartDefinition root = mesh.getRoot();
            PartDefinition head = root.addOrReplaceChild("head",
                    CubeListBuilder.create().texOffs(0, 0).addBox(-4f, -10f, -4f, 8f, 10f, 8f), PartPose.ZERO);
            head.addOrReplaceChild("nose",
                    CubeListBuilder.create().texOffs(24, 0).addBox(-1f, -1f, -6f, 2f, 4f, 2f), PartPose.offset(0f, -2f, 0f));
            root.addOrReplaceChild("hat",
                    CubeListBuilder.create().texOffs(32, 0).addBox(-4f, -10f, -4f, 8f, 10f, 8f, new CubeDeformation(0.5f)), PartPose.ZERO);
            root.addOrReplaceChild("body", CubeListBuilder.create()
                    .texOffs(16, 20).addBox(-4f, 0f, -2f, 8f, 12f, 4f)
                    .texOffs(16, 38).addBox(-4f, 0f, -2f, 8f, 12f, 4f, new CubeDeformation(0.3f)), PartPose.ZERO);
            root.addOrReplaceChild("right_arm",
                    CubeListBuilder.create().texOffs(40, 20).addBox(-3f, -2f, -2f, 4f, 12f, 4f), PartPose.offset(-5f, 2f, 0f));
            root.addOrReplaceChild("left_arm",
                    CubeListBuilder.create().texOffs(40, 20).mirror().addBox(-1f, -2f, -2f, 4f, 12f, 4f), PartPose.offset(5f, 2f, 0f));
            root.addOrReplaceChild("right_leg",
                    CubeListBuilder.create().texOffs(0, 20).addBox(-2f, 0f, -2f, 4f, 12f, 4f), PartPose.offset(-1.9f, 12f, 0f));
            root.addOrReplaceChild("left_leg",
                    CubeListBuilder.create().texOffs(0, 20).mirror().addBox(-2f, 0f, -2f, 4f, 12f, 4f), PartPose.offset(1.9f, 12f, 0f));
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        public void prepareMobModel(VillageGuard guard, float limbSwing, float limbSwingAmount, float partialTick) {
            rightArmPose = ArmPose.EMPTY;
            leftArmPose = ArmPose.EMPTY;
            if (guard.getMainHandItem().getItem() instanceof BowItem && guard.isAggressive()) {
                if (guard.getMainArm() == HumanoidArm.RIGHT) rightArmPose = ArmPose.BOW_AND_ARROW;
                else leftArmPose = ArmPose.BOW_AND_ARROW;
            } else {
                if (!guard.getMainHandItem().isEmpty()) rightArmPose = ArmPose.ITEM;
                if (!guard.getOffhandItem().isEmpty()) leftArmPose = guard.isBlocking() ? ArmPose.BLOCK : ArmPose.ITEM;
            }
            super.prepareMobModel(guard, limbSwing, limbSwingAmount, partialTick);
        }
    }
}
