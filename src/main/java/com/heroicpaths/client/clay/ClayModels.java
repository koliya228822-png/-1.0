package com.heroicpaths.client.clay;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.util.HashMap;
import java.util.Map;

// Сгенерировано tools/ClayGolemGen.java — не править вручную.
public final class ClayModels {
    private ClayModels() {}

    public static LayerDefinition clayColossus() {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        parts.put("body", parts.get("root").addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-8.00f, -16.00f, -5.00f, 16, 14, 10, new CubeDeformation(0.00f)).texOffs(32, 61).addBox(-6.00f, -2.00f, -4.00f, 12, 5, 8, new CubeDeformation(0.00f)).texOffs(34, 87).addBox(-5.00f, -14.00f, 4.50f, 10, 9, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 8.00f, 0.00f, 0.12f, 0.00f, 0.00f)));
        parts.put("chest_plate", parts.get("body").addOrReplaceChild("chest_plate", CubeListBuilder.create().texOffs(28, 24).addBox(-8.00f, -16.00f, -5.00f, 16, 9, 10, new CubeDeformation(0.80f)).texOffs(82, 99).addBox(-3.00f, -18.00f, -5.50f, 6, 3, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("belt_plate", parts.get("body").addOrReplaceChild("belt_plate", CubeListBuilder.create().texOffs(0, 75).addBox(-6.00f, -2.00f, -4.00f, 12, 4, 8, new CubeDeformation(0.70f)).texOffs(32, 99).addBox(-4.00f, 1.00f, -5.20f, 8, 5, 1, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("head", parts.get("body").addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 45).addBox(-4.00f, -7.00f, -4.00f, 8, 8, 8, new CubeDeformation(0.00f)).texOffs(98, 99).addBox(-3.00f, -1.00f, -4.60f, 6, 2, 1, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, -16.00f, -2.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("helmet", parts.get("head").addOrReplaceChild("helmet", CubeListBuilder.create().texOffs(40, 75).addBox(-4.00f, -7.00f, -4.00f, 8, 4, 8, new CubeDeformation(0.80f)).texOffs(58, 87).addBox(-1.00f, -10.00f, -3.00f, 2, 3, 8, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("arm_right", parts.get("body").addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(52, 0).addBox(-5.00f, -2.00f, -3.00f, 6, 18, 6, new CubeDeformation(0.00f)).texOffs(32, 45).addBox(-6.00f, 16.00f, -4.00f, 8, 6, 8, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-10.00f, -13.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("pauldron_right", parts.get("arm_right").addOrReplaceChild("pauldron_right", CubeListBuilder.create().texOffs(64, 45).addBox(-6.00f, -4.00f, -4.00f, 8, 6, 8, new CubeDeformation(0.40f)).texOffs(50, 99).addBox(-4.00f, -6.00f, -2.00f, 4, 2, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("arm_mid_right", parts.get("body").addOrReplaceChild("arm_mid_right", CubeListBuilder.create().texOffs(80, 24).addBox(-2.00f, -1.00f, -2.00f, 4, 13, 4, new CubeDeformation(0.00f)).texOffs(78, 87).addBox(-2.50f, 12.00f, -2.50f, 5, 4, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-8.00f, -8.00f, 1.00f, 0.00f, 0.00f, 0.35f)));
        parts.put("arm_lower_right", parts.get("body").addOrReplaceChild("arm_lower_right", CubeListBuilder.create().texOffs(72, 61).addBox(-1.50f, -1.00f, -1.50f, 3, 10, 3, new CubeDeformation(0.00f)).texOffs(0, 99).addBox(-2.00f, 9.00f, -2.00f, 4, 3, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-7.00f, -3.00f, 1.00f, 0.00f, 0.00f, 0.60f)));
        parts.put("leg_right", parts.get("root").addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(100, 0).addBox(-3.50f, 0.00f, -3.50f, 7, 14, 7, new CubeDeformation(0.00f)).texOffs(72, 75).addBox(-4.00f, 11.00f, -5.00f, 8, 3, 9, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-4.00f, 10.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("arm_left", parts.get("body").addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(76, 0).addBox(-1.00f, -2.00f, -3.00f, 6, 18, 6, new CubeDeformation(0.00f)).texOffs(96, 45).addBox(-2.00f, 16.00f, -4.00f, 8, 6, 8, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(10.00f, -13.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("pauldron_left", parts.get("arm_left").addOrReplaceChild("pauldron_left", CubeListBuilder.create().texOffs(0, 61).addBox(-2.00f, -4.00f, -4.00f, 8, 6, 8, new CubeDeformation(0.40f)).texOffs(66, 99).addBox(0.00f, -6.00f, -2.00f, 4, 2, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("arm_mid_left", parts.get("body").addOrReplaceChild("arm_mid_left", CubeListBuilder.create().texOffs(96, 24).addBox(-2.00f, -1.00f, -2.00f, 4, 13, 4, new CubeDeformation(0.00f)).texOffs(98, 87).addBox(-2.50f, 12.00f, -2.50f, 5, 4, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(8.00f, -8.00f, 1.00f, 0.00f, 0.00f, -0.35f)));
        parts.put("arm_lower_left", parts.get("body").addOrReplaceChild("arm_lower_left", CubeListBuilder.create().texOffs(84, 61).addBox(-1.50f, -1.00f, -1.50f, 3, 10, 3, new CubeDeformation(0.00f)).texOffs(16, 99).addBox(-2.00f, 9.00f, -2.00f, 4, 3, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(7.00f, -3.00f, 1.00f, 0.00f, 0.00f, -0.60f)));
        parts.put("leg_left", parts.get("root").addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(0, 24).addBox(-3.50f, 0.00f, -3.50f, 7, 14, 7, new CubeDeformation(0.00f)).texOffs(0, 87).addBox(-4.00f, 11.00f, -5.00f, 8, 3, 9, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(4.00f, 10.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        return LayerDefinition.create(mesh, 128, 128);
    }

    public static LayerDefinition clayGolem() {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        parts.put("body", parts.get("root").addOrReplaceChild("body", CubeListBuilder.create().texOffs(32, 0).addBox(-4.00f, -8.00f, -3.00f, 8, 8, 6, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 12.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("head", parts.get("body").addOrReplaceChild("head", CubeListBuilder.create().texOffs(92, 0).addBox(-3.00f, -5.00f, -3.00f, 6, 5, 6, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, -8.00f, -1.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("arm_right", parts.get("root").addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(60, 0).addBox(-2.00f, -1.00f, -2.00f, 4, 9, 4, new CubeDeformation(0.00f)).texOffs(0, 16).addBox(-2.50f, 7.00f, -2.50f, 5, 4, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-5.50f, 5.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("leg_right", parts.get("root").addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(0, 0).addBox(-2.00f, 0.00f, -2.00f, 4, 12, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-2.00f, 12.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("arm_left", parts.get("root").addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(76, 0).addBox(-2.00f, -1.00f, -2.00f, 4, 9, 4, new CubeDeformation(0.00f)).texOffs(20, 16).addBox(-2.50f, 7.00f, -2.50f, 5, 4, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(5.50f, 5.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("leg_left", parts.get("root").addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(16, 0).addBox(-2.00f, 0.00f, -2.00f, 4, 12, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(2.00f, 12.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        return LayerDefinition.create(mesh, 128, 32);
    }

    public static LayerDefinition clayThrower() {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        parts.put("body", parts.get("root").addOrReplaceChild("body", CubeListBuilder.create().texOffs(36, 0).addBox(-3.50f, -9.00f, -2.50f, 7, 9, 5, new CubeDeformation(0.00f)).texOffs(96, 0).addBox(3.00f, -7.00f, -3.00f, 3, 5, 6, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 12.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("head", parts.get("body").addOrReplaceChild("head", CubeListBuilder.create().texOffs(72, 0).addBox(-3.00f, -6.00f, -3.00f, 6, 6, 6, new CubeDeformation(0.00f)).texOffs(0, 15).addBox(-3.50f, -7.00f, -3.50f, 7, 2, 7, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, -9.00f, -1.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("arm_right", parts.get("root").addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(0, 0).addBox(-1.50f, -1.00f, -1.50f, 3, 12, 3, new CubeDeformation(0.00f)).texOffs(28, 15).addBox(-2.00f, 10.00f, -2.00f, 4, 3, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-5.00f, 4.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("leg_right", parts.get("root").addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(12, 0).addBox(-1.50f, 0.00f, -1.50f, 3, 12, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-2.00f, 12.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("arm_left", parts.get("root").addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(60, 0).addBox(-1.50f, -1.00f, -1.50f, 3, 10, 3, new CubeDeformation(0.00f)).texOffs(44, 15).addBox(-2.00f, 8.00f, -2.00f, 4, 3, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(5.00f, 4.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("leg_left", parts.get("root").addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(24, 0).addBox(-1.50f, 0.00f, -1.50f, 3, 12, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(2.00f, 12.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        return LayerDefinition.create(mesh, 128, 32);
    }

    public static LayerDefinition clayBrute() {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        parts.put("body", parts.get("root").addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7.00f, -11.00f, -4.50f, 14, 11, 9, new CubeDeformation(0.00f)).texOffs(0, 20).addBox(-7.00f, -11.00f, -4.50f, 14, 7, 9, new CubeDeformation(0.60f)),
                PartPose.offsetAndRotation(0.00f, 12.00f, 0.00f, 0.15f, 0.00f, 0.00f)));
        parts.put("head", parts.get("body").addOrReplaceChild("head", CubeListBuilder.create().texOffs(26, 36).addBox(-3.00f, -4.00f, -3.50f, 6, 5, 6, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, -10.00f, -3.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("arm_right", parts.get("root").addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(46, 0).addBox(-3.50f, -2.00f, -2.50f, 5, 13, 5, new CubeDeformation(0.00f)).texOffs(46, 20).addBox(-4.50f, 10.00f, -3.50f, 6, 6, 7, new CubeDeformation(0.00f)).texOffs(98, 20).addBox(-4.50f, -4.00f, -3.50f, 6, 5, 7, new CubeDeformation(0.30f)),
                PartPose.offsetAndRotation(-8.50f, 3.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("leg_right", parts.get("root").addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(86, 0).addBox(-2.50f, 0.00f, -2.50f, 5, 12, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-3.50f, 12.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("arm_left", parts.get("root").addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(66, 0).addBox(-1.50f, -2.00f, -2.50f, 5, 13, 5, new CubeDeformation(0.00f)).texOffs(72, 20).addBox(-1.50f, 10.00f, -3.50f, 6, 6, 7, new CubeDeformation(0.00f)).texOffs(0, 36).addBox(-1.50f, -4.00f, -3.50f, 6, 5, 7, new CubeDeformation(0.30f)),
                PartPose.offsetAndRotation(8.50f, 3.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("leg_left", parts.get("root").addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(106, 0).addBox(-2.50f, 0.00f, -2.50f, 5, 12, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(3.50f, 12.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        return LayerDefinition.create(mesh, 128, 64);
    }
}
