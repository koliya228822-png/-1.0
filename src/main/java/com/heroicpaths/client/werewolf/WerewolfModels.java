package com.heroicpaths.client.werewolf;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.util.HashMap;
import java.util.Map;

// Сгенерировано tools/werewolf_gen.py — не править вручную.
public final class WerewolfModels {
    private WerewolfModels() {}

    public static LayerDefinition werewolf() {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        parts.put("hips", parts.get("root").addOrReplaceChild("hips", CubeListBuilder.create().texOffs(0, 35).addBox(-5.00f, -3.00f, -3.50f, 10, 5, 7, new CubeDeformation(0.00f)).texOffs(12, 66).addBox(-2.00f, 1.50f, -3.90f, 4, 5, 1, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 9.00f, 2.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("torso", parts.get("hips").addOrReplaceChild("torso", CubeListBuilder.create().texOffs(0, 0).addBox(-6.00f, -13.00f, -4.00f, 12, 13, 8, new CubeDeformation(0.00f)).texOffs(0, 21).addBox(-7.00f, -15.00f, -1.50f, 14, 6, 8, new CubeDeformation(0.25f)).texOffs(92, 47).addBox(-4.00f, -17.00f, 1.00f, 3, 4, 4, new CubeDeformation(0.00f)).texOffs(106, 47).addBox(1.00f, -16.50f, 2.00f, 3, 4, 4, new CubeDeformation(0.00f)).texOffs(116, 58).addBox(-1.50f, -13.00f, 5.50f, 3, 5, 2, new CubeDeformation(0.00f)).texOffs(0, 66).addBox(-2.00f, -7.00f, 3.60f, 4, 5, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, -2.00f, 0.00f, 0.50f, 0.00f, 0.00f)));
        parts.put("head", parts.get("torso").addOrReplaceChild("head", CubeListBuilder.create().texOffs(90, 35).addBox(-5.00f, -3.00f, -2.50f, 10, 6, 5, new CubeDeformation(0.20f)).texOffs(40, 0).addBox(-4.00f, -7.00f, -7.00f, 8, 8, 7, new CubeDeformation(0.00f)).texOffs(54, 66).addBox(-4.50f, -10.00f, -3.50f, 3, 3, 2, new CubeDeformation(0.00f)).texOffs(8, 73).addBox(-4.50f, -12.00f, -3.00f, 2, 2, 1, new CubeDeformation(0.00f)).texOffs(64, 66).addBox(1.50f, -10.00f, -3.50f, 3, 3, 2, new CubeDeformation(0.00f)).texOffs(14, 73).addBox(2.50f, -12.00f, -3.00f, 2, 2, 1, new CubeDeformation(0.00f)).texOffs(32, 47).addBox(-2.50f, -3.50f, -13.00f, 5, 4, 6, new CubeDeformation(0.00f)).texOffs(116, 66).addBox(-1.50f, -4.00f, -13.50f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(20, 73).addBox(-2.30f, -0.20f, -12.60f, 1, 2, 1, new CubeDeformation(0.00f)).texOffs(24, 73).addBox(1.30f, -0.20f, -12.60f, 1, 2, 1, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, -14.00f, -3.00f, -0.50f, 0.00f, 0.00f)));
        parts.put("jaw", parts.get("head").addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 58).addBox(-2.00f, 0.00f, -5.50f, 4, 2, 6, new CubeDeformation(0.00f)).texOffs(28, 73).addBox(-1.80f, -1.60f, -5.30f, 1, 2, 1, new CubeDeformation(0.00f)).texOffs(32, 73).addBox(0.80f, -1.60f, -5.30f, 1, 2, 1, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, -7.00f, 0.40f, 0.00f, 0.00f)));
        parts.put("arm_right", parts.get("torso").addOrReplaceChild("arm_right", CubeListBuilder.create().texOffs(34, 35).addBox(-3.50f, -2.50f, -3.50f, 7, 5, 7, new CubeDeformation(0.20f)).texOffs(44, 21).addBox(-2.50f, 0.00f, -2.50f, 5, 9, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-7.50f, -11.00f, -0.50f, -0.55f, 0.00f, -0.12f)));
        parts.put("forearm_right", parts.get("arm_right").addOrReplaceChild("forearm_right", CubeListBuilder.create().texOffs(84, 21).addBox(-2.50f, 0.00f, -2.50f, 5, 8, 5, new CubeDeformation(0.00f)).texOffs(22, 66).addBox(-3.00f, -0.50f, 1.00f, 6, 4, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 8.50f, 0.00f, -0.30f, 0.00f, 0.00f)));
        parts.put("hand_right", parts.get("forearm_right").addOrReplaceChild("hand_right", CubeListBuilder.create().texOffs(20, 58).addBox(-2.50f, 0.00f, -2.00f, 5, 4, 4, new CubeDeformation(0.00f)).texOffs(74, 66).addBox(-2.40f, 3.50f, -2.30f, 1, 4, 1, new CubeDeformation(0.00f)).texOffs(78, 66).addBox(-1.10f, 3.50f, -2.30f, 1, 4, 1, new CubeDeformation(0.00f)).texOffs(82, 66).addBox(0.20f, 3.50f, -2.30f, 1, 4, 1, new CubeDeformation(0.00f)).texOffs(86, 66).addBox(1.50f, 3.50f, -2.30f, 1, 4, 1, new CubeDeformation(0.00f)).texOffs(0, 73).addBox(-1.60f, 1.00f, -2.60f, 1, 3, 1, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 8.00f, 0.00f, -0.15f, 0.00f, 0.00f)));
        parts.put("arm_left", parts.get("torso").addOrReplaceChild("arm_left", CubeListBuilder.create().texOffs(62, 35).addBox(-3.50f, -2.50f, -3.50f, 7, 5, 7, new CubeDeformation(0.20f)).texOffs(64, 21).addBox(-2.50f, 0.00f, -2.50f, 5, 9, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(7.50f, -11.00f, -0.50f, -0.55f, 0.00f, 0.12f)));
        parts.put("forearm_left", parts.get("arm_left").addOrReplaceChild("forearm_left", CubeListBuilder.create().texOffs(104, 21).addBox(-2.50f, 0.00f, -2.50f, 5, 8, 5, new CubeDeformation(0.00f)).texOffs(38, 66).addBox(-3.00f, -0.50f, 1.00f, 6, 4, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 8.50f, 0.00f, -0.30f, 0.00f, 0.00f)));
        parts.put("hand_left", parts.get("forearm_left").addOrReplaceChild("hand_left", CubeListBuilder.create().texOffs(38, 58).addBox(-2.50f, 0.00f, -2.00f, 5, 4, 4, new CubeDeformation(0.00f)).texOffs(90, 66).addBox(-2.40f, 3.50f, -2.30f, 1, 4, 1, new CubeDeformation(0.00f)).texOffs(94, 66).addBox(-1.10f, 3.50f, -2.30f, 1, 4, 1, new CubeDeformation(0.00f)).texOffs(98, 66).addBox(0.20f, 3.50f, -2.30f, 1, 4, 1, new CubeDeformation(0.00f)).texOffs(102, 66).addBox(1.50f, 3.50f, -2.30f, 1, 4, 1, new CubeDeformation(0.00f)).texOffs(4, 73).addBox(1.60f, 1.00f, -2.60f, 1, 3, 1, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 8.00f, 0.00f, -0.15f, 0.00f, 0.00f)));
        parts.put("leg_right", parts.get("root").addOrReplaceChild("leg_right", CubeListBuilder.create().texOffs(70, 0).addBox(-2.50f, -1.00f, -3.00f, 5, 9, 6, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-3.20f, 9.00f, 2.00f, -0.35f, 0.00f, 0.00f)));
        parts.put("shin_right", parts.get("leg_right").addOrReplaceChild("shin_right", CubeListBuilder.create().texOffs(0, 47).addBox(-2.00f, -0.50f, -2.00f, 4, 7, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 7.50f, -0.50f, 0.75f, 0.00f, 0.00f)));
        parts.put("foot_right", parts.get("shin_right").addOrReplaceChild("foot_right", CubeListBuilder.create().texOffs(56, 58).addBox(-2.50f, 0.00f, -4.50f, 5, 2, 6, new CubeDeformation(0.00f)).texOffs(36, 73).addBox(-2.20f, 1.00f, -6.00f, 1, 1, 2, new CubeDeformation(0.00f)).texOffs(42, 73).addBox(-0.50f, 1.00f, -6.00f, 1, 1, 2, new CubeDeformation(0.00f)).texOffs(48, 73).addBox(1.20f, 1.00f, -6.00f, 1, 1, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 6.50f, 0.00f, -0.40f, 0.00f, 0.00f)));
        parts.put("leg_left", parts.get("root").addOrReplaceChild("leg_left", CubeListBuilder.create().texOffs(92, 0).addBox(-2.50f, -1.00f, -3.00f, 5, 9, 6, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(3.20f, 9.00f, 2.00f, -0.35f, 0.00f, 0.00f)));
        parts.put("shin_left", parts.get("leg_left").addOrReplaceChild("shin_left", CubeListBuilder.create().texOffs(16, 47).addBox(-2.00f, -0.50f, -2.00f, 4, 7, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 7.50f, -0.50f, 0.75f, 0.00f, 0.00f)));
        parts.put("foot_left", parts.get("shin_left").addOrReplaceChild("foot_left", CubeListBuilder.create().texOffs(78, 58).addBox(-2.50f, 0.00f, -4.50f, 5, 2, 6, new CubeDeformation(0.00f)).texOffs(54, 73).addBox(-2.20f, 1.00f, -6.00f, 1, 1, 2, new CubeDeformation(0.00f)).texOffs(60, 73).addBox(-0.50f, 1.00f, -6.00f, 1, 1, 2, new CubeDeformation(0.00f)).texOffs(66, 73).addBox(1.20f, 1.00f, -6.00f, 1, 1, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 6.50f, 0.00f, -0.40f, 0.00f, 0.00f)));
        parts.put("tail", parts.get("hips").addOrReplaceChild("tail", CubeListBuilder.create().texOffs(74, 47).addBox(-2.00f, -2.00f, 0.00f, 4, 4, 5, new CubeDeformation(0.20f)),
                PartPose.offsetAndRotation(0.00f, -1.00f, 3.50f, -0.70f, 0.00f, 0.00f)));
        parts.put("tail2", parts.get("tail").addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(54, 47).addBox(-2.50f, -2.50f, 0.00f, 5, 5, 5, new CubeDeformation(0.30f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 4.50f, -0.25f, 0.00f, 0.00f)));
        parts.put("tail3", parts.get("tail2").addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(100, 58).addBox(-2.00f, -2.00f, 0.00f, 4, 4, 4, new CubeDeformation(0.20f)).texOffs(106, 66).addBox(-1.00f, -1.00f, 3.50f, 2, 2, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 4.50f, -0.20f, 0.00f, 0.00f)));
        return LayerDefinition.create(mesh, 128, 128);
    }
}
