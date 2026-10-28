package com.heroicpaths.client.spider;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import java.util.HashMap;
import java.util.Map;

// Сгенерировано tools/SpiderGen.java — не править вручную.
public final class SpiderModels {
    private SpiderModels() {}

    public static LayerDefinition broodmother() {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        parts.put("body", parts.get("root").addOrReplaceChild("body", CubeListBuilder.create().texOffs(36, 30).addBox(-6.00f, -3.00f, -8.00f, 12, 6, 12, new CubeDeformation(0.00f)).texOffs(46, 49).addBox(-5.00f, -4.40f, -7.00f, 10, 2, 10, new CubeDeformation(0.00f)).texOffs(106, 73).addBox(-3.00f, -5.20f, -6.00f, 6, 1, 7, new CubeDeformation(0.00f)).texOffs(190, 49).addBox(-5.00f, 2.60f, -7.00f, 10, 1, 10, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 15.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("head", parts.get("body").addOrReplaceChild("head", CubeListBuilder.create().texOffs(86, 49).addBox(-4.50f, -3.00f, -6.00f, 9, 6, 6, new CubeDeformation(0.00f)).texOffs(84, 91).addBox(-4.00f, -3.60f, -6.30f, 8, 2, 5, new CubeDeformation(0.00f)).texOffs(64, 105).addBox(-3.00f, 1.50f, -6.60f, 6, 2, 1, new CubeDeformation(0.00f)).texOffs(0, 105).addBox(-4.50f, -3.50f, -1.00f, 9, 4, 1, new CubeDeformation(0.20f)).texOffs(78, 105).addBox(-2.70f, -2.20f, -7.25f, 2, 2, 1, new CubeDeformation(-0.05f)).texOffs(84, 105).addBox(0.70f, -2.20f, -7.25f, 2, 2, 1, new CubeDeformation(-0.05f)).texOffs(160, 105).addBox(-0.50f, -1.70f, -7.00f, 1, 1, 1, new CubeDeformation(-0.05f)).texOffs(164, 105).addBox(-1.50f, -0.50f, -7.00f, 1, 1, 1, new CubeDeformation(-0.15f)).texOffs(168, 105).addBox(0.50f, -0.50f, -7.00f, 1, 1, 1, new CubeDeformation(-0.15f)),
                PartPose.offsetAndRotation(0.00f, -1.00f, -8.50f, 0.00f, 0.00f, 0.00f)));
        parts.put("horn_r", parts.get("head").addOrReplaceChild("horn_r", CubeListBuilder.create().texOffs(110, 91).addBox(-1.00f, -5.00f, -1.00f, 2, 5, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-2.20f, -3.20f, -4.00f, 0.25f, 0.00f, -0.55f)));
        parts.put("horn_mid_r", parts.get("horn_r").addOrReplaceChild("horn_mid_r", CubeListBuilder.create().texOffs(118, 91).addBox(-1.00f, -5.00f, -1.00f, 2, 5, 2, new CubeDeformation(-0.20f)),
                PartPose.offsetAndRotation(0.00f, -5.00f, 0.00f, 0.05f, 0.00f, 0.30f)));
        parts.put("horn_tip_r", parts.get("horn_mid_r").addOrReplaceChild("horn_tip_r", CubeListBuilder.create().texOffs(186, 91).addBox(-0.50f, -5.00f, -0.50f, 1, 5, 1, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, -5.00f, 0.00f, -0.20f, 0.00f, 0.45f)));
        parts.put("horn_small_r", parts.get("head").addOrReplaceChild("horn_small_r", CubeListBuilder.create().texOffs(40, 105).addBox(-0.50f, -3.00f, -0.50f, 1, 3, 1, new CubeDeformation(0.10f)).texOffs(90, 105).addBox(-0.50f, -5.00f, -0.50f, 1, 2, 1, new CubeDeformation(-0.10f)),
                PartPose.offsetAndRotation(-3.50f, -2.50f, -5.00f, 0.60f, 0.00f, -0.90f)));
        parts.put("fang_r", parts.get("head").addOrReplaceChild("fang_r", CubeListBuilder.create().texOffs(190, 91).addBox(-1.00f, 0.00f, -1.00f, 2, 4, 2, new CubeDeformation(0.10f)).texOffs(94, 105).addBox(-0.50f, 4.00f, -0.50f, 1, 2, 1, new CubeDeformation(0.10f)),
                PartPose.offsetAndRotation(-1.80f, 1.50f, -5.50f, -0.50f, 0.00f, -0.15f)));
        parts.put("palp_r", parts.get("head").addOrReplaceChild("palp_r", CubeListBuilder.create().texOffs(126, 91).addBox(-1.00f, 0.00f, -1.00f, 2, 5, 2, new CubeDeformation(0.10f)).texOffs(44, 105).addBox(-1.00f, 4.00f, -1.00f, 2, 2, 2, new CubeDeformation(0.20f)),
                PartPose.offsetAndRotation(-3.80f, 1.50f, -5.00f, -0.90f, 0.40f, 0.00f)));
        parts.put("horn_l", parts.get("head").addOrReplaceChild("horn_l", CubeListBuilder.create().texOffs(134, 91).addBox(-1.00f, -5.00f, -1.00f, 2, 5, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(2.20f, -3.20f, -4.00f, 0.25f, 0.00f, 0.55f)));
        parts.put("horn_mid_l", parts.get("horn_l").addOrReplaceChild("horn_mid_l", CubeListBuilder.create().texOffs(142, 91).addBox(-1.00f, -5.00f, -1.00f, 2, 5, 2, new CubeDeformation(-0.20f)),
                PartPose.offsetAndRotation(0.00f, -5.00f, 0.00f, 0.05f, 0.00f, -0.30f)));
        parts.put("horn_tip_l", parts.get("horn_mid_l").addOrReplaceChild("horn_tip_l", CubeListBuilder.create().texOffs(198, 91).addBox(-0.50f, -5.00f, -0.50f, 1, 5, 1, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, -5.00f, 0.00f, -0.20f, 0.00f, -0.45f)));
        parts.put("horn_small_l", parts.get("head").addOrReplaceChild("horn_small_l", CubeListBuilder.create().texOffs(52, 105).addBox(-0.50f, -3.00f, -0.50f, 1, 3, 1, new CubeDeformation(0.10f)).texOffs(98, 105).addBox(-0.50f, -5.00f, -0.50f, 1, 2, 1, new CubeDeformation(-0.10f)),
                PartPose.offsetAndRotation(3.50f, -2.50f, -5.00f, 0.60f, 0.00f, 0.90f)));
        parts.put("fang_l", parts.get("head").addOrReplaceChild("fang_l", CubeListBuilder.create().texOffs(202, 91).addBox(-1.00f, 0.00f, -1.00f, 2, 4, 2, new CubeDeformation(0.10f)).texOffs(102, 105).addBox(-0.50f, 4.00f, -0.50f, 1, 2, 1, new CubeDeformation(0.10f)),
                PartPose.offsetAndRotation(1.80f, 1.50f, -5.50f, -0.50f, 0.00f, 0.15f)));
        parts.put("palp_l", parts.get("head").addOrReplaceChild("palp_l", CubeListBuilder.create().texOffs(150, 91).addBox(-1.00f, 0.00f, -1.00f, 2, 5, 2, new CubeDeformation(0.10f)).texOffs(56, 105).addBox(-1.00f, 4.00f, -1.00f, 2, 2, 2, new CubeDeformation(0.20f)),
                PartPose.offsetAndRotation(3.80f, 1.50f, -5.00f, -0.90f, -0.40f, 0.00f)));
        parts.put("abdomen", parts.get("body").addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(0, 0).addBox(-8.00f, -6.00f, 0.00f, 16, 12, 18, new CubeDeformation(0.00f)).texOffs(138, 30).addBox(-7.00f, 5.60f, 1.00f, 14, 1, 16, new CubeDeformation(0.00f)).texOffs(20, 105).addBox(-2.00f, -1.00f, 17.50f, 4, 3, 2, new CubeDeformation(0.00f)).texOffs(106, 105).addBox(-1.00f, 0.00f, 19.00f, 2, 2, 1, new CubeDeformation(0.00f)).texOffs(116, 49).addBox(-7.00f, -5.00f, -1.50f, 14, 9, 3, new CubeDeformation(0.30f)),
                PartPose.offsetAndRotation(0.00f, -1.00f, 3.50f, 0.20f, 0.00f, 0.00f)));
        parts.put("shell", parts.get("abdomen").addOrReplaceChild("shell", CubeListBuilder.create().texOffs(68, 0).addBox(-8.50f, -3.00f, -9.00f, 17, 3, 18, new CubeDeformation(0.00f)).texOffs(200, 0).addBox(-9.00f, -1.50f, -8.50f, 1, 2, 17, new CubeDeformation(0.00f)).texOffs(0, 30).addBox(8.00f, -1.50f, -8.50f, 1, 2, 17, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, -6.00f, 9.00f, -0.35f, 0.00f, 0.00f)));
        parts.put("shell_top", parts.get("shell").addOrReplaceChild("shell_top", CubeListBuilder.create().texOffs(138, 0).addBox(-7.50f, -7.00f, -8.00f, 15, 4, 16, new CubeDeformation(0.00f)).texOffs(84, 30).addBox(-6.50f, -11.00f, -7.00f, 13, 4, 14, new CubeDeformation(0.00f)).texOffs(0, 49).addBox(-5.00f, -14.00f, -5.50f, 10, 3, 11, new CubeDeformation(0.00f)).texOffs(80, 73).addBox(-3.00f, -16.00f, -3.50f, 6, 2, 7, new CubeDeformation(0.00f)).texOffs(42, 49).addBox(-0.50f, -16.50f, -8.00f, 1, 13, 1, new CubeDeformation(0.00f)).texOffs(32, 105).addBox(-8.00f, -7.50f, -0.50f, 1, 4, 1, new CubeDeformation(0.00f)).texOffs(36, 105).addBox(7.00f, -7.50f, -0.50f, 1, 4, 1, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("core", parts.get("shell").addOrReplaceChild("core", CubeListBuilder.create().texOffs(198, 30).addBox(-7.00f, -5.00f, -7.00f, 14, 2, 14, new CubeDeformation(0.00f)).texOffs(150, 49).addBox(-5.00f, -7.00f, -5.00f, 10, 2, 10, new CubeDeformation(0.00f)).texOffs(158, 91).addBox(-6.00f, -8.00f, -6.00f, 2, 5, 2, new CubeDeformation(0.00f)).texOffs(166, 91).addBox(4.00f, -7.00f, -3.00f, 2, 4, 3, new CubeDeformation(0.00f)).texOffs(176, 91).addBox(-2.00f, -8.00f, 5.00f, 3, 5, 2, new CubeDeformation(0.00f)).texOffs(132, 73).addBox(1.50f, -9.00f, -6.50f, 2, 6, 2, new CubeDeformation(0.00f)).texOffs(210, 91).addBox(-7.00f, -6.00f, 2.00f, 2, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("eggs_a", parts.get("abdomen").addOrReplaceChild("eggs_a", CubeListBuilder.create().texOffs(140, 73).addBox(-12.00f, -11.00f, 1.00f, 4, 4, 4, new CubeDeformation(0.00f)).texOffs(156, 73).addBox(-12.50f, -15.00f, 5.00f, 4, 4, 4, new CubeDeformation(0.00f)).texOffs(172, 73).addBox(-12.00f, -10.50f, 7.00f, 4, 4, 4, new CubeDeformation(0.00f)).texOffs(220, 91).addBox(-11.00f, -13.50f, 11.00f, 3, 3, 3, new CubeDeformation(0.00f)).texOffs(232, 91).addBox(-9.50f, -18.50f, 2.50f, 3, 3, 3, new CubeDeformation(0.00f)).texOffs(188, 73).addBox(-5.00f, -25.00f, 5.00f, 4, 4, 4, new CubeDeformation(0.00f)).texOffs(244, 91).addBox(0.50f, -23.50f, 8.00f, 3, 3, 3, new CubeDeformation(0.00f)).texOffs(112, 105).addBox(-12.00f, -17.10f, 7.10f, 2, 1, 2, new CubeDeformation(0.05f)).texOffs(172, 105).addBox(-13.00f, -16.80f, 7.30f, 4, 1, 1, new CubeDeformation(-0.32f)).texOffs(120, 105).addBox(-4.00f, -27.10f, 7.10f, 2, 1, 2, new CubeDeformation(0.05f)).texOffs(182, 105).addBox(-5.00f, -26.80f, 7.30f, 4, 1, 1, new CubeDeformation(-0.32f)).texOffs(128, 105).addBox(-13.50f, -12.10f, 3.10f, 2, 1, 2, new CubeDeformation(0.05f)).texOffs(192, 105).addBox(-14.50f, -11.80f, 3.30f, 4, 1, 1, new CubeDeformation(-0.32f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("eggs_b", parts.get("abdomen").addOrReplaceChild("eggs_b", CubeListBuilder.create().texOffs(204, 73).addBox(8.00f, -11.00f, 2.00f, 4, 4, 4, new CubeDeformation(0.00f)).texOffs(0, 99).addBox(9.00f, -14.50f, 7.00f, 3, 3, 3, new CubeDeformation(0.00f)).texOffs(220, 73).addBox(7.50f, -10.50f, 9.00f, 4, 4, 4, new CubeDeformation(0.00f)).texOffs(12, 99).addBox(6.50f, -17.50f, 10.50f, 3, 3, 3, new CubeDeformation(0.00f)).texOffs(236, 73).addBox(-5.00f, -11.00f, 16.50f, 4, 4, 4, new CubeDeformation(0.00f)).texOffs(24, 99).addBox(1.50f, -11.50f, 16.50f, 3, 3, 3, new CubeDeformation(0.00f)).texOffs(36, 99).addBox(4.50f, -21.50f, 4.50f, 3, 3, 3, new CubeDeformation(0.00f)).texOffs(136, 105).addBox(10.50f, -13.10f, 4.10f, 2, 1, 2, new CubeDeformation(0.05f)).texOffs(202, 105).addBox(9.50f, -12.80f, 4.30f, 4, 1, 1, new CubeDeformation(-0.32f)).texOffs(144, 105).addBox(-4.00f, -13.10f, 18.60f, 2, 1, 2, new CubeDeformation(0.05f)).texOffs(212, 105).addBox(-5.00f, -12.80f, 18.80f, 4, 1, 1, new CubeDeformation(-0.32f)).texOffs(152, 105).addBox(7.50f, -20.10f, 12.10f, 2, 1, 2, new CubeDeformation(0.05f)).texOffs(222, 105).addBox(6.50f, -19.80f, 12.30f, 4, 1, 1, new CubeDeformation(-0.32f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("leg_r1", parts.get("body").addOrReplaceChild("leg_r1", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-5.50f, 0.50f, -6.50f, 0.00f, -1.00f, 0.00f)));
        parts.put("femur_r1", parts.get("leg_r1").addOrReplaceChild("femur_r1", CubeListBuilder.create().texOffs(0, 63).addBox(-15.00f, -2.50f, -2.50f, 15, 5, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.50f)));
        parts.put("tibia_r1", parts.get("femur_r1").addOrReplaceChild("tibia_r1", CubeListBuilder.create().texOffs(0, 83).addBox(-17.00f, -2.00f, -2.00f, 17, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-15.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.12f)));
        parts.put("tarsus_r1", parts.get("tibia_r1").addOrReplaceChild("tarsus_r1", CubeListBuilder.create().texOffs(48, 99).addBox(-4.00f, -1.50f, -1.50f, 4, 3, 3, new CubeDeformation(0.00f)).texOffs(62, 99).addBox(-6.00f, -1.50f, -1.50f, 2, 3, 3, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-17.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.73f)));
        parts.put("leg_l1", parts.get("body").addOrReplaceChild("leg_l1", CubeListBuilder.create(),
                PartPose.offsetAndRotation(5.50f, 0.50f, -6.50f, 0.00f, 1.00f, 0.00f)));
        parts.put("femur_l1", parts.get("leg_l1").addOrReplaceChild("femur_l1", CubeListBuilder.create().texOffs(40, 63).addBox(0.00f, -2.50f, -2.50f, 15, 5, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.50f)));
        parts.put("tibia_l1", parts.get("femur_l1").addOrReplaceChild("tibia_l1", CubeListBuilder.create().texOffs(42, 83).addBox(0.00f, -2.00f, -2.00f, 17, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(15.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.12f)));
        parts.put("tarsus_l1", parts.get("tibia_l1").addOrReplaceChild("tarsus_l1", CubeListBuilder.create().texOffs(72, 99).addBox(0.00f, -1.50f, -1.50f, 4, 3, 3, new CubeDeformation(0.00f)).texOffs(86, 99).addBox(4.00f, -1.50f, -1.50f, 2, 3, 3, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(17.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.73f)));
        parts.put("leg_r2", parts.get("body").addOrReplaceChild("leg_r2", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-5.50f, 0.50f, -3.50f, 0.00f, -0.35f, 0.00f)));
        parts.put("femur_r2", parts.get("leg_r2").addOrReplaceChild("femur_r2", CubeListBuilder.create().texOffs(80, 63).addBox(-14.00f, -2.50f, -2.50f, 14, 5, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.45f)));
        parts.put("tibia_r2", parts.get("femur_r2").addOrReplaceChild("tibia_r2", CubeListBuilder.create().texOffs(84, 83).addBox(-16.00f, -2.00f, -2.00f, 16, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-14.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.03f)));
        parts.put("tarsus_r2", parts.get("tibia_r2").addOrReplaceChild("tarsus_r2", CubeListBuilder.create().texOffs(96, 99).addBox(-4.00f, -1.50f, -1.50f, 4, 3, 3, new CubeDeformation(0.00f)).texOffs(110, 99).addBox(-6.00f, -1.50f, -1.50f, 2, 3, 3, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-16.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.77f)));
        parts.put("leg_l2", parts.get("body").addOrReplaceChild("leg_l2", CubeListBuilder.create(),
                PartPose.offsetAndRotation(5.50f, 0.50f, -3.50f, 0.00f, 0.35f, 0.00f)));
        parts.put("femur_l2", parts.get("leg_l2").addOrReplaceChild("femur_l2", CubeListBuilder.create().texOffs(118, 63).addBox(0.00f, -2.50f, -2.50f, 14, 5, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.45f)));
        parts.put("tibia_l2", parts.get("femur_l2").addOrReplaceChild("tibia_l2", CubeListBuilder.create().texOffs(124, 83).addBox(0.00f, -2.00f, -2.00f, 16, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(14.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.03f)));
        parts.put("tarsus_l2", parts.get("tibia_l2").addOrReplaceChild("tarsus_l2", CubeListBuilder.create().texOffs(120, 99).addBox(0.00f, -1.50f, -1.50f, 4, 3, 3, new CubeDeformation(0.00f)).texOffs(134, 99).addBox(4.00f, -1.50f, -1.50f, 2, 3, 3, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(16.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.77f)));
        parts.put("leg_r3", parts.get("body").addOrReplaceChild("leg_r3", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-5.50f, 0.50f, -0.50f, 0.00f, 0.30f, 0.00f)));
        parts.put("femur_r3", parts.get("leg_r3").addOrReplaceChild("femur_r3", CubeListBuilder.create().texOffs(156, 63).addBox(-14.00f, -2.50f, -2.50f, 14, 5, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.45f)));
        parts.put("tibia_r3", parts.get("femur_r3").addOrReplaceChild("tibia_r3", CubeListBuilder.create().texOffs(164, 83).addBox(-16.00f, -2.00f, -2.00f, 16, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-14.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.03f)));
        parts.put("tarsus_r3", parts.get("tibia_r3").addOrReplaceChild("tarsus_r3", CubeListBuilder.create().texOffs(144, 99).addBox(-4.00f, -1.50f, -1.50f, 4, 3, 3, new CubeDeformation(0.00f)).texOffs(158, 99).addBox(-6.00f, -1.50f, -1.50f, 2, 3, 3, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-16.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.77f)));
        parts.put("leg_l3", parts.get("body").addOrReplaceChild("leg_l3", CubeListBuilder.create(),
                PartPose.offsetAndRotation(5.50f, 0.50f, -0.50f, 0.00f, -0.30f, 0.00f)));
        parts.put("femur_l3", parts.get("leg_l3").addOrReplaceChild("femur_l3", CubeListBuilder.create().texOffs(194, 63).addBox(0.00f, -2.50f, -2.50f, 14, 5, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.45f)));
        parts.put("tibia_l3", parts.get("femur_l3").addOrReplaceChild("tibia_l3", CubeListBuilder.create().texOffs(204, 83).addBox(0.00f, -2.00f, -2.00f, 16, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(14.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.03f)));
        parts.put("tarsus_l3", parts.get("tibia_l3").addOrReplaceChild("tarsus_l3", CubeListBuilder.create().texOffs(168, 99).addBox(0.00f, -1.50f, -1.50f, 4, 3, 3, new CubeDeformation(0.00f)).texOffs(182, 99).addBox(4.00f, -1.50f, -1.50f, 2, 3, 3, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(16.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.77f)));
        parts.put("leg_r4", parts.get("body").addOrReplaceChild("leg_r4", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-5.50f, 0.50f, 2.50f, 0.00f, 0.95f, 0.00f)));
        parts.put("femur_r4", parts.get("leg_r4").addOrReplaceChild("femur_r4", CubeListBuilder.create().texOffs(0, 73).addBox(-15.00f, -2.50f, -2.50f, 15, 5, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.50f)));
        parts.put("tibia_r4", parts.get("femur_r4").addOrReplaceChild("tibia_r4", CubeListBuilder.create().texOffs(0, 91).addBox(-17.00f, -2.00f, -2.00f, 17, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-15.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.12f)));
        parts.put("tarsus_r4", parts.get("tibia_r4").addOrReplaceChild("tarsus_r4", CubeListBuilder.create().texOffs(192, 99).addBox(-4.00f, -1.50f, -1.50f, 4, 3, 3, new CubeDeformation(0.00f)).texOffs(206, 99).addBox(-6.00f, -1.50f, -1.50f, 2, 3, 3, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-17.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.73f)));
        parts.put("leg_l4", parts.get("body").addOrReplaceChild("leg_l4", CubeListBuilder.create(),
                PartPose.offsetAndRotation(5.50f, 0.50f, 2.50f, 0.00f, -0.95f, 0.00f)));
        parts.put("femur_l4", parts.get("leg_l4").addOrReplaceChild("femur_l4", CubeListBuilder.create().texOffs(40, 73).addBox(0.00f, -2.50f, -2.50f, 15, 5, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.50f)));
        parts.put("tibia_l4", parts.get("femur_l4").addOrReplaceChild("tibia_l4", CubeListBuilder.create().texOffs(42, 91).addBox(0.00f, -2.00f, -2.00f, 17, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(15.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.12f)));
        parts.put("tarsus_l4", parts.get("tibia_l4").addOrReplaceChild("tarsus_l4", CubeListBuilder.create().texOffs(216, 99).addBox(0.00f, -1.50f, -1.50f, 4, 3, 3, new CubeDeformation(0.00f)).texOffs(230, 99).addBox(4.00f, -1.50f, -1.50f, 2, 3, 3, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(17.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.73f)));
        return LayerDefinition.create(mesh, 256, 128);
    }

    public static LayerDefinition broodSpider() {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        parts.put("body", parts.get("root").addOrReplaceChild("body", CubeListBuilder.create().texOffs(34, 0).addBox(-3.00f, -2.00f, -4.00f, 6, 4, 6, new CubeDeformation(0.00f)).texOffs(18, 16).addBox(-2.50f, -2.70f, -3.50f, 5, 1, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 18.50f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("head", parts.get("body").addOrReplaceChild("head", CubeListBuilder.create().texOffs(58, 0).addBox(-2.50f, -2.00f, -4.00f, 5, 4, 4, new CubeDeformation(0.00f)).texOffs(76, 23).addBox(-2.00f, -2.50f, -4.20f, 4, 1, 3, new CubeDeformation(0.00f)).texOffs(36, 37).addBox(-1.50f, -0.60f, -4.95f, 1, 1, 1, new CubeDeformation(-0.05f)).texOffs(40, 37).addBox(0.50f, -0.60f, -4.95f, 1, 1, 1, new CubeDeformation(-0.05f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, -4.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("horn_r", parts.get("head").addOrReplaceChild("horn_r", CubeListBuilder.create().texOffs(90, 23).addBox(-0.50f, -3.00f, -0.50f, 1, 3, 1, new CubeDeformation(0.10f)).texOffs(16, 37).addBox(-0.50f, -4.50f, -0.50f, 1, 2, 1, new CubeDeformation(-0.15f)),
                PartPose.offsetAndRotation(-1.40f, -2.20f, -3.00f, 0.60f, 0.00f, -0.30f)));
        parts.put("fang_r", parts.get("head").addOrReplaceChild("fang_r", CubeListBuilder.create().texOffs(94, 23).addBox(-0.50f, 0.00f, -0.50f, 1, 3, 1, new CubeDeformation(0.10f)),
                PartPose.offsetAndRotation(-1.10f, 1.20f, -3.60f, -0.50f, 0.00f, -0.15f)));
        parts.put("horn_l", parts.get("head").addOrReplaceChild("horn_l", CubeListBuilder.create().texOffs(98, 23).addBox(-0.50f, -3.00f, -0.50f, 1, 3, 1, new CubeDeformation(0.10f)).texOffs(20, 37).addBox(-0.50f, -4.50f, -0.50f, 1, 2, 1, new CubeDeformation(-0.15f)),
                PartPose.offsetAndRotation(1.40f, -2.20f, -3.00f, 0.60f, 0.00f, 0.30f)));
        parts.put("fang_l", parts.get("head").addOrReplaceChild("fang_l", CubeListBuilder.create().texOffs(102, 23).addBox(-0.50f, 0.00f, -0.50f, 1, 3, 1, new CubeDeformation(0.10f)),
                PartPose.offsetAndRotation(1.10f, 1.20f, -3.60f, -0.50f, 0.00f, 0.15f)));
        parts.put("abdomen", parts.get("body").addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(0, 0).addBox(-4.00f, -3.50f, 0.00f, 8, 7, 9, new CubeDeformation(0.00f)).texOffs(76, 0).addBox(-1.50f, -4.60f, 1.00f, 3, 1, 7, new CubeDeformation(0.20f)).texOffs(24, 37).addBox(-3.00f, -3.00f, 8.60f, 2, 2, 1, new CubeDeformation(-0.20f)).texOffs(30, 37).addBox(1.00f, -3.00f, 8.60f, 2, 2, 1, new CubeDeformation(-0.20f)).texOffs(96, 0).addBox(-3.50f, 3.10f, 1.00f, 7, 1, 7, new CubeDeformation(0.00f)).texOffs(0, 16).addBox(-3.50f, -3.50f, -1.00f, 7, 5, 2, new CubeDeformation(0.20f)),
                PartPose.offsetAndRotation(0.00f, -0.50f, 2.00f, 0.30f, 0.00f, 0.00f)));
        parts.put("leg_r1", parts.get("body").addOrReplaceChild("leg_r1", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-2.60f, 0.00f, -3.50f, 0.00f, -1.00f, 0.00f)));
        parts.put("femur_r1", parts.get("leg_r1").addOrReplaceChild("femur_r1", CubeListBuilder.create().texOffs(38, 16).addBox(-7.00f, -1.50f, -1.50f, 7, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.50f)));
        parts.put("tibia_r1", parts.get("femur_r1").addOrReplaceChild("tibia_r1", CubeListBuilder.create().texOffs(106, 23).addBox(-8.00f, -1.00f, -1.00f, 8, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-7.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.17f)));
        parts.put("tarsus_r1", parts.get("tibia_r1").addOrReplaceChild("tarsus_r1", CubeListBuilder.create().texOffs(0, 29).addBox(-3.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(10, 29).addBox(-4.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-8.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.68f)));
        parts.put("leg_l1", parts.get("body").addOrReplaceChild("leg_l1", CubeListBuilder.create(),
                PartPose.offsetAndRotation(2.60f, 0.00f, -3.50f, 0.00f, 1.00f, 0.00f)));
        parts.put("femur_l1", parts.get("leg_l1").addOrReplaceChild("femur_l1", CubeListBuilder.create().texOffs(58, 16).addBox(0.00f, -1.50f, -1.50f, 7, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.50f)));
        parts.put("tibia_l1", parts.get("femur_l1").addOrReplaceChild("tibia_l1", CubeListBuilder.create().texOffs(16, 29).addBox(0.00f, -1.00f, -1.00f, 8, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(7.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.17f)));
        parts.put("tarsus_l1", parts.get("tibia_l1").addOrReplaceChild("tarsus_l1", CubeListBuilder.create().texOffs(36, 29).addBox(0.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(46, 29).addBox(3.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(8.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.68f)));
        parts.put("leg_r2", parts.get("body").addOrReplaceChild("leg_r2", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-2.60f, 0.00f, -2.00f, 0.00f, -0.35f, 0.00f)));
        parts.put("femur_r2", parts.get("leg_r2").addOrReplaceChild("femur_r2", CubeListBuilder.create().texOffs(78, 16).addBox(-6.00f, -1.50f, -1.50f, 6, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.45f)));
        parts.put("tibia_r2", parts.get("femur_r2").addOrReplaceChild("tibia_r2", CubeListBuilder.create().texOffs(52, 29).addBox(-8.00f, -1.00f, -1.00f, 8, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-6.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.00f)));
        parts.put("tarsus_r2", parts.get("tibia_r2").addOrReplaceChild("tarsus_r2", CubeListBuilder.create().texOffs(72, 29).addBox(-3.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(82, 29).addBox(-4.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-8.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.80f)));
        parts.put("leg_l2", parts.get("body").addOrReplaceChild("leg_l2", CubeListBuilder.create(),
                PartPose.offsetAndRotation(2.60f, 0.00f, -2.00f, 0.00f, 0.35f, 0.00f)));
        parts.put("femur_l2", parts.get("leg_l2").addOrReplaceChild("femur_l2", CubeListBuilder.create().texOffs(96, 16).addBox(0.00f, -1.50f, -1.50f, 6, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.45f)));
        parts.put("tibia_l2", parts.get("femur_l2").addOrReplaceChild("tibia_l2", CubeListBuilder.create().texOffs(88, 29).addBox(0.00f, -1.00f, -1.00f, 8, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(6.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.00f)));
        parts.put("tarsus_l2", parts.get("tibia_l2").addOrReplaceChild("tarsus_l2", CubeListBuilder.create().texOffs(108, 29).addBox(0.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(118, 29).addBox(3.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(8.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.80f)));
        parts.put("leg_r3", parts.get("body").addOrReplaceChild("leg_r3", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-2.60f, 0.00f, -0.50f, 0.00f, 0.30f, 0.00f)));
        parts.put("femur_r3", parts.get("leg_r3").addOrReplaceChild("femur_r3", CubeListBuilder.create().texOffs(0, 23).addBox(-6.00f, -1.50f, -1.50f, 6, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.45f)));
        parts.put("tibia_r3", parts.get("femur_r3").addOrReplaceChild("tibia_r3", CubeListBuilder.create().texOffs(0, 33).addBox(-8.00f, -1.00f, -1.00f, 8, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-6.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.00f)));
        parts.put("tarsus_r3", parts.get("tibia_r3").addOrReplaceChild("tarsus_r3", CubeListBuilder.create().texOffs(20, 33).addBox(-3.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(30, 33).addBox(-4.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-8.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.80f)));
        parts.put("leg_l3", parts.get("body").addOrReplaceChild("leg_l3", CubeListBuilder.create(),
                PartPose.offsetAndRotation(2.60f, 0.00f, -0.50f, 0.00f, -0.30f, 0.00f)));
        parts.put("femur_l3", parts.get("leg_l3").addOrReplaceChild("femur_l3", CubeListBuilder.create().texOffs(18, 23).addBox(0.00f, -1.50f, -1.50f, 6, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.45f)));
        parts.put("tibia_l3", parts.get("femur_l3").addOrReplaceChild("tibia_l3", CubeListBuilder.create().texOffs(36, 33).addBox(0.00f, -1.00f, -1.00f, 8, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(6.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.00f)));
        parts.put("tarsus_l3", parts.get("tibia_l3").addOrReplaceChild("tarsus_l3", CubeListBuilder.create().texOffs(56, 33).addBox(0.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(66, 33).addBox(3.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(8.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.80f)));
        parts.put("leg_r4", parts.get("body").addOrReplaceChild("leg_r4", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-2.60f, 0.00f, 1.00f, 0.00f, 0.95f, 0.00f)));
        parts.put("femur_r4", parts.get("leg_r4").addOrReplaceChild("femur_r4", CubeListBuilder.create().texOffs(36, 23).addBox(-7.00f, -1.50f, -1.50f, 7, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.50f)));
        parts.put("tibia_r4", parts.get("femur_r4").addOrReplaceChild("tibia_r4", CubeListBuilder.create().texOffs(72, 33).addBox(-8.00f, -1.00f, -1.00f, 8, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-7.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.17f)));
        parts.put("tarsus_r4", parts.get("tibia_r4").addOrReplaceChild("tarsus_r4", CubeListBuilder.create().texOffs(92, 33).addBox(-3.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(102, 33).addBox(-4.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-8.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.68f)));
        parts.put("leg_l4", parts.get("body").addOrReplaceChild("leg_l4", CubeListBuilder.create(),
                PartPose.offsetAndRotation(2.60f, 0.00f, 1.00f, 0.00f, -0.95f, 0.00f)));
        parts.put("femur_l4", parts.get("leg_l4").addOrReplaceChild("femur_l4", CubeListBuilder.create().texOffs(56, 23).addBox(0.00f, -1.50f, -1.50f, 7, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.50f)));
        parts.put("tibia_l4", parts.get("femur_l4").addOrReplaceChild("tibia_l4", CubeListBuilder.create().texOffs(108, 33).addBox(0.00f, -1.00f, -1.00f, 8, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(7.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.17f)));
        parts.put("tarsus_l4", parts.get("tibia_l4").addOrReplaceChild("tarsus_l4", CubeListBuilder.create().texOffs(0, 37).addBox(0.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(10, 37).addBox(3.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(8.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.68f)));
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition venomSpitter() {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        parts.put("body", parts.get("root").addOrReplaceChild("body", CubeListBuilder.create().texOffs(38, 0).addBox(-3.00f, -2.00f, -4.00f, 6, 4, 6, new CubeDeformation(0.00f)).texOffs(52, 19).addBox(-2.50f, -2.80f, -3.50f, 5, 1, 5, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 17.50f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("head", parts.get("body").addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 19).addBox(-2.50f, -2.00f, -4.00f, 5, 4, 4, new CubeDeformation(0.00f)).texOffs(112, 27).addBox(-2.00f, -2.60f, -4.20f, 4, 1, 3, new CubeDeformation(0.00f)).texOffs(0, 33).addBox(-3.50f, -3.00f, -1.50f, 7, 3, 1, new CubeDeformation(0.10f)).texOffs(32, 45).addBox(-1.00f, 0.80f, -4.40f, 2, 1, 1, new CubeDeformation(0.00f)).texOffs(38, 45).addBox(-1.50f, -0.70f, -4.95f, 1, 1, 1, new CubeDeformation(-0.05f)).texOffs(42, 45).addBox(0.50f, -0.70f, -4.95f, 1, 1, 1, new CubeDeformation(-0.05f)),
                PartPose.offsetAndRotation(0.00f, -0.30f, -4.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("horn_r", parts.get("head").addOrReplaceChild("horn_r", CubeListBuilder.create().texOffs(16, 33).addBox(-0.50f, -3.00f, -0.50f, 1, 3, 1, new CubeDeformation(0.05f)).texOffs(114, 41).addBox(-0.50f, -5.00f, -0.50f, 1, 2, 1, new CubeDeformation(-0.15f)),
                PartPose.offsetAndRotation(-1.60f, -2.30f, -3.00f, 0.20f, 0.00f, -0.60f)));
        parts.put("fang_r", parts.get("head").addOrReplaceChild("fang_r", CubeListBuilder.create().texOffs(20, 33).addBox(-0.50f, 0.00f, -0.50f, 1, 3, 1, new CubeDeformation(0.10f)),
                PartPose.offsetAndRotation(-1.10f, 1.20f, -3.60f, -0.70f, 0.00f, -0.25f)));
        parts.put("horn_l", parts.get("head").addOrReplaceChild("horn_l", CubeListBuilder.create().texOffs(24, 33).addBox(-0.50f, -3.00f, -0.50f, 1, 3, 1, new CubeDeformation(0.05f)).texOffs(118, 41).addBox(-0.50f, -5.00f, -0.50f, 1, 2, 1, new CubeDeformation(-0.15f)),
                PartPose.offsetAndRotation(1.60f, -2.30f, -3.00f, 0.20f, 0.00f, 0.60f)));
        parts.put("fang_l", parts.get("head").addOrReplaceChild("fang_l", CubeListBuilder.create().texOffs(28, 33).addBox(-0.50f, 0.00f, -0.50f, 1, 3, 1, new CubeDeformation(0.10f)),
                PartPose.offsetAndRotation(1.10f, 1.20f, -3.60f, -0.70f, 0.00f, 0.25f)));
        parts.put("abdomen", parts.get("body").addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(18, 19).addBox(-3.00f, -3.00f, -1.00f, 6, 5, 3, new CubeDeformation(0.20f)).texOffs(62, 0).addBox(-3.50f, -3.00f, 0.00f, 7, 6, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, -1.00f, 2.00f, 0.75f, 0.00f, 0.00f)));
        parts.put("sac", parts.get("abdomen").addOrReplaceChild("sac", CubeListBuilder.create().texOffs(0, 0).addBox(-4.50f, -4.50f, 0.00f, 9, 9, 10, new CubeDeformation(0.00f)).texOffs(84, 0).addBox(-3.50f, -5.50f, 1.00f, 7, 1, 8, new CubeDeformation(0.00f)).texOffs(36, 19).addBox(-3.50f, -3.50f, 10.00f, 7, 7, 1, new CubeDeformation(0.00f)).texOffs(46, 45).addBox(-0.50f, -0.50f, 11.00f, 1, 1, 1, new CubeDeformation(0.00f)).texOffs(122, 41).addBox(-3.00f, -6.50f, 2.00f, 1, 2, 1, new CubeDeformation(0.00f)).texOffs(0, 45).addBox(2.00f, -6.50f, 3.00f, 1, 2, 1, new CubeDeformation(0.00f)).texOffs(4, 45).addBox(-1.00f, -7.00f, 6.00f, 1, 2, 1, new CubeDeformation(0.00f)).texOffs(8, 45).addBox(2.50f, -6.00f, 7.50f, 1, 2, 1, new CubeDeformation(0.00f)).texOffs(12, 45).addBox(-3.50f, -6.00f, 8.00f, 1, 2, 1, new CubeDeformation(0.00f)).texOffs(16, 45).addBox(-5.50f, -3.00f, 4.00f, 1, 2, 1, new CubeDeformation(0.00f)).texOffs(20, 45).addBox(4.50f, -3.00f, 5.00f, 1, 2, 1, new CubeDeformation(0.00f)).texOffs(24, 45).addBox(-5.50f, -2.00f, 8.00f, 1, 2, 1, new CubeDeformation(0.00f)).texOffs(28, 45).addBox(4.50f, -1.00f, 8.50f, 1, 2, 1, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, -0.50f, 3.50f, 0.00f, 0.00f, 0.00f)));
        parts.put("leg_r1", parts.get("body").addOrReplaceChild("leg_r1", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-2.60f, 0.00f, -3.50f, 0.00f, -1.00f, 0.00f)));
        parts.put("femur_r1", parts.get("leg_r1").addOrReplaceChild("femur_r1", CubeListBuilder.create().texOffs(72, 19).addBox(-7.00f, -1.50f, -1.50f, 7, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.55f)));
        parts.put("tibia_r1", parts.get("femur_r1").addOrReplaceChild("tibia_r1", CubeListBuilder.create().texOffs(32, 33).addBox(-9.00f, -1.00f, -1.00f, 9, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-7.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.32f)));
        parts.put("tarsus_r1", parts.get("tibia_r1").addOrReplaceChild("tarsus_r1", CubeListBuilder.create().texOffs(54, 33).addBox(-3.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(64, 33).addBox(-4.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-9.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.58f)));
        parts.put("leg_l1", parts.get("body").addOrReplaceChild("leg_l1", CubeListBuilder.create(),
                PartPose.offsetAndRotation(2.60f, 0.00f, -3.50f, 0.00f, 1.00f, 0.00f)));
        parts.put("femur_l1", parts.get("leg_l1").addOrReplaceChild("femur_l1", CubeListBuilder.create().texOffs(92, 19).addBox(0.00f, -1.50f, -1.50f, 7, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.55f)));
        parts.put("tibia_l1", parts.get("femur_l1").addOrReplaceChild("tibia_l1", CubeListBuilder.create().texOffs(70, 33).addBox(0.00f, -1.00f, -1.00f, 9, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(7.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.32f)));
        parts.put("tarsus_l1", parts.get("tibia_l1").addOrReplaceChild("tarsus_l1", CubeListBuilder.create().texOffs(92, 33).addBox(0.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(102, 33).addBox(3.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(9.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.58f)));
        parts.put("leg_r2", parts.get("body").addOrReplaceChild("leg_r2", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-2.60f, 0.00f, -2.00f, 0.00f, -0.35f, 0.00f)));
        parts.put("femur_r2", parts.get("leg_r2").addOrReplaceChild("femur_r2", CubeListBuilder.create().texOffs(0, 27).addBox(-6.00f, -1.50f, -1.50f, 6, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.50f)));
        parts.put("tibia_r2", parts.get("femur_r2").addOrReplaceChild("tibia_r2", CubeListBuilder.create().texOffs(0, 37).addBox(-9.00f, -1.00f, -1.00f, 9, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-6.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.15f)));
        parts.put("tarsus_r2", parts.get("tibia_r2").addOrReplaceChild("tarsus_r2", CubeListBuilder.create().texOffs(22, 37).addBox(-3.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(32, 37).addBox(-4.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-9.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.70f)));
        parts.put("leg_l2", parts.get("body").addOrReplaceChild("leg_l2", CubeListBuilder.create(),
                PartPose.offsetAndRotation(2.60f, 0.00f, -2.00f, 0.00f, 0.35f, 0.00f)));
        parts.put("femur_l2", parts.get("leg_l2").addOrReplaceChild("femur_l2", CubeListBuilder.create().texOffs(18, 27).addBox(0.00f, -1.50f, -1.50f, 6, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.50f)));
        parts.put("tibia_l2", parts.get("femur_l2").addOrReplaceChild("tibia_l2", CubeListBuilder.create().texOffs(38, 37).addBox(0.00f, -1.00f, -1.00f, 9, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(6.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.15f)));
        parts.put("tarsus_l2", parts.get("tibia_l2").addOrReplaceChild("tarsus_l2", CubeListBuilder.create().texOffs(60, 37).addBox(0.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(70, 37).addBox(3.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(9.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.70f)));
        parts.put("leg_r3", parts.get("body").addOrReplaceChild("leg_r3", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-2.60f, 0.00f, -0.50f, 0.00f, 0.30f, 0.00f)));
        parts.put("femur_r3", parts.get("leg_r3").addOrReplaceChild("femur_r3", CubeListBuilder.create().texOffs(36, 27).addBox(-6.00f, -1.50f, -1.50f, 6, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.50f)));
        parts.put("tibia_r3", parts.get("femur_r3").addOrReplaceChild("tibia_r3", CubeListBuilder.create().texOffs(76, 37).addBox(-9.00f, -1.00f, -1.00f, 9, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-6.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.15f)));
        parts.put("tarsus_r3", parts.get("tibia_r3").addOrReplaceChild("tarsus_r3", CubeListBuilder.create().texOffs(98, 37).addBox(-3.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(108, 37).addBox(-4.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-9.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.70f)));
        parts.put("leg_l3", parts.get("body").addOrReplaceChild("leg_l3", CubeListBuilder.create(),
                PartPose.offsetAndRotation(2.60f, 0.00f, -0.50f, 0.00f, -0.30f, 0.00f)));
        parts.put("femur_l3", parts.get("leg_l3").addOrReplaceChild("femur_l3", CubeListBuilder.create().texOffs(54, 27).addBox(0.00f, -1.50f, -1.50f, 6, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.50f)));
        parts.put("tibia_l3", parts.get("femur_l3").addOrReplaceChild("tibia_l3", CubeListBuilder.create().texOffs(0, 41).addBox(0.00f, -1.00f, -1.00f, 9, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(6.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.15f)));
        parts.put("tarsus_l3", parts.get("tibia_l3").addOrReplaceChild("tarsus_l3", CubeListBuilder.create().texOffs(22, 41).addBox(0.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(32, 41).addBox(3.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(9.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.70f)));
        parts.put("leg_r4", parts.get("body").addOrReplaceChild("leg_r4", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-2.60f, 0.00f, 1.00f, 0.00f, 0.95f, 0.00f)));
        parts.put("femur_r4", parts.get("leg_r4").addOrReplaceChild("femur_r4", CubeListBuilder.create().texOffs(72, 27).addBox(-7.00f, -1.50f, -1.50f, 7, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.55f)));
        parts.put("tibia_r4", parts.get("femur_r4").addOrReplaceChild("tibia_r4", CubeListBuilder.create().texOffs(38, 41).addBox(-9.00f, -1.00f, -1.00f, 9, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-7.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.32f)));
        parts.put("tarsus_r4", parts.get("tibia_r4").addOrReplaceChild("tarsus_r4", CubeListBuilder.create().texOffs(60, 41).addBox(-3.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(70, 41).addBox(-4.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-9.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.58f)));
        parts.put("leg_l4", parts.get("body").addOrReplaceChild("leg_l4", CubeListBuilder.create(),
                PartPose.offsetAndRotation(2.60f, 0.00f, 1.00f, 0.00f, -0.95f, 0.00f)));
        parts.put("femur_l4", parts.get("leg_l4").addOrReplaceChild("femur_l4", CubeListBuilder.create().texOffs(92, 27).addBox(0.00f, -1.50f, -1.50f, 7, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.55f)));
        parts.put("tibia_l4", parts.get("femur_l4").addOrReplaceChild("tibia_l4", CubeListBuilder.create().texOffs(76, 41).addBox(0.00f, -1.00f, -1.00f, 9, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(7.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.32f)));
        parts.put("tarsus_l4", parts.get("tibia_l4").addOrReplaceChild("tarsus_l4", CubeListBuilder.create().texOffs(98, 41).addBox(0.00f, -1.00f, -1.00f, 3, 2, 2, new CubeDeformation(0.00f)).texOffs(108, 41).addBox(3.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(9.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.58f)));
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition broodTank() {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        parts.put("body", parts.get("root").addOrReplaceChild("body", CubeListBuilder.create().texOffs(116, 0).addBox(-5.00f, -3.00f, -6.00f, 10, 6, 9, new CubeDeformation(0.00f)).texOffs(0, 28).addBox(-4.50f, -4.20f, -5.50f, 9, 2, 8, new CubeDeformation(0.00f)).texOffs(58, 28).addBox(-4.50f, 2.50f, -5.50f, 9, 1, 8, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 15.50f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("head", parts.get("body").addOrReplaceChild("head", CubeListBuilder.create().texOffs(34, 28).addBox(-3.50f, -2.50f, -5.00f, 7, 5, 5, new CubeDeformation(0.00f)).texOffs(188, 38).addBox(-4.00f, -3.40f, -5.40f, 8, 2, 5, new CubeDeformation(0.00f)).texOffs(224, 52).addBox(-3.00f, 1.50f, -5.40f, 6, 1, 1, new CubeDeformation(0.00f)).texOffs(238, 52).addBox(-1.90f, -1.50f, -6.15f, 1, 1, 1, new CubeDeformation(-0.05f)).texOffs(242, 52).addBox(0.90f, -1.50f, -6.15f, 1, 1, 1, new CubeDeformation(-0.05f)),
                PartPose.offsetAndRotation(0.00f, 0.50f, -6.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("horn_r", parts.get("head").addOrReplaceChild("horn_r", CubeListBuilder.create().texOffs(196, 46).addBox(-1.00f, -3.00f, -1.00f, 2, 3, 2, new CubeDeformation(0.00f)).texOffs(144, 52).addBox(-0.50f, -5.00f, -0.50f, 1, 2, 1, new CubeDeformation(0.10f)),
                PartPose.offsetAndRotation(-2.80f, -3.00f, -3.50f, 0.20f, 0.00f, -0.90f)));
        parts.put("fang_r", parts.get("head").addOrReplaceChild("fang_r", CubeListBuilder.create().texOffs(204, 46).addBox(-1.00f, 0.00f, -1.00f, 2, 3, 2, new CubeDeformation(0.10f)).texOffs(148, 52).addBox(-0.50f, 3.00f, -0.50f, 1, 2, 1, new CubeDeformation(0.10f)),
                PartPose.offsetAndRotation(-1.70f, 1.60f, -4.50f, -0.45f, 0.00f, -0.10f)));
        parts.put("horn_l", parts.get("head").addOrReplaceChild("horn_l", CubeListBuilder.create().texOffs(212, 46).addBox(-1.00f, -3.00f, -1.00f, 2, 3, 2, new CubeDeformation(0.00f)).texOffs(152, 52).addBox(-0.50f, -5.00f, -0.50f, 1, 2, 1, new CubeDeformation(0.10f)),
                PartPose.offsetAndRotation(2.80f, -3.00f, -3.50f, 0.20f, 0.00f, 0.90f)));
        parts.put("fang_l", parts.get("head").addOrReplaceChild("fang_l", CubeListBuilder.create().texOffs(220, 46).addBox(-1.00f, 0.00f, -1.00f, 2, 3, 2, new CubeDeformation(0.10f)).texOffs(156, 52).addBox(-0.50f, 3.00f, -0.50f, 1, 2, 1, new CubeDeformation(0.10f)),
                PartPose.offsetAndRotation(1.70f, 1.60f, -4.50f, -0.45f, 0.00f, 0.10f)));
        parts.put("abdomen", parts.get("body").addOrReplaceChild("abdomen", CubeListBuilder.create().texOffs(210, 0).addBox(-6.00f, -5.00f, -1.50f, 12, 8, 3, new CubeDeformation(0.30f)).texOffs(0, 0).addBox(-8.00f, -6.00f, 0.00f, 16, 12, 16, new CubeDeformation(0.00f)).texOffs(154, 0).addBox(-7.00f, 5.40f, 1.00f, 14, 1, 14, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, -0.50f, 2.50f, 0.12f, 0.00f, 0.00f)));
        parts.put("plates", parts.get("abdomen").addOrReplaceChild("plates", CubeListBuilder.create().texOffs(92, 28).addBox(-8.50f, -7.50f, 0.50f, 17, 3, 5, new CubeDeformation(0.00f)).texOffs(136, 28).addBox(-8.50f, -7.80f, 5.50f, 17, 3, 5, new CubeDeformation(0.00f)).texOffs(180, 28).addBox(-8.00f, -7.50f, 10.50f, 16, 3, 5, new CubeDeformation(0.00f)).texOffs(160, 52).addBox(-7.00f, -8.60f, 4.50f, 14, 1, 2, new CubeDeformation(0.20f)).texOffs(192, 52).addBox(-7.00f, -8.60f, 9.50f, 14, 1, 2, new CubeDeformation(0.20f)).texOffs(64, 0).addBox(-9.20f, -5.00f, 2.00f, 1, 4, 12, new CubeDeformation(0.00f)).texOffs(90, 0).addBox(8.20f, -5.00f, 2.00f, 1, 4, 12, new CubeDeformation(0.00f)).texOffs(228, 46).addBox(-1.00f, -9.50f, 2.00f, 2, 2, 2, new CubeDeformation(0.00f)).texOffs(236, 46).addBox(-1.00f, -9.80f, 7.00f, 2, 2, 2, new CubeDeformation(0.00f)).texOffs(244, 46).addBox(-1.00f, -9.50f, 12.00f, 2, 2, 2, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        parts.put("leg_r1", parts.get("body").addOrReplaceChild("leg_r1", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-4.50f, 0.00f, -5.00f, 0.00f, -1.00f, 0.00f)));
        parts.put("femur_r1", parts.get("leg_r1").addOrReplaceChild("femur_r1", CubeListBuilder.create().texOffs(222, 28).addBox(-10.00f, -2.00f, -2.00f, 10, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.50f)));
        parts.put("tibia_r1", parts.get("femur_r1").addOrReplaceChild("tibia_r1", CubeListBuilder.create().texOffs(214, 38).addBox(-11.00f, -1.50f, -1.50f, 11, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-10.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.36f)));
        parts.put("tarsus_r1", parts.get("tibia_r1").addOrReplaceChild("tarsus_r1", CubeListBuilder.create().texOffs(0, 52).addBox(-4.00f, -1.00f, -1.00f, 4, 2, 2, new CubeDeformation(0.00f)).texOffs(12, 52).addBox(-5.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-11.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.54f)));
        parts.put("leg_l1", parts.get("body").addOrReplaceChild("leg_l1", CubeListBuilder.create(),
                PartPose.offsetAndRotation(4.50f, 0.00f, -5.00f, 0.00f, 1.00f, 0.00f)));
        parts.put("femur_l1", parts.get("leg_l1").addOrReplaceChild("femur_l1", CubeListBuilder.create().texOffs(0, 38).addBox(0.00f, -2.00f, -2.00f, 10, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.50f)));
        parts.put("tibia_l1", parts.get("femur_l1").addOrReplaceChild("tibia_l1", CubeListBuilder.create().texOffs(0, 46).addBox(0.00f, -1.50f, -1.50f, 11, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(10.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.36f)));
        parts.put("tarsus_l1", parts.get("tibia_l1").addOrReplaceChild("tarsus_l1", CubeListBuilder.create().texOffs(18, 52).addBox(0.00f, -1.00f, -1.00f, 4, 2, 2, new CubeDeformation(0.00f)).texOffs(30, 52).addBox(4.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(11.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.54f)));
        parts.put("leg_r2", parts.get("body").addOrReplaceChild("leg_r2", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-4.50f, 0.00f, -2.50f, 0.00f, -0.35f, 0.00f)));
        parts.put("femur_r2", parts.get("leg_r2").addOrReplaceChild("femur_r2", CubeListBuilder.create().texOffs(28, 38).addBox(-9.00f, -2.00f, -2.00f, 9, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.45f)));
        parts.put("tibia_r2", parts.get("femur_r2").addOrReplaceChild("tibia_r2", CubeListBuilder.create().texOffs(28, 46).addBox(-11.00f, -1.50f, -1.50f, 11, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-9.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.20f)));
        parts.put("tarsus_r2", parts.get("tibia_r2").addOrReplaceChild("tarsus_r2", CubeListBuilder.create().texOffs(36, 52).addBox(-4.00f, -1.00f, -1.00f, 4, 2, 2, new CubeDeformation(0.00f)).texOffs(48, 52).addBox(-5.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-11.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.65f)));
        parts.put("leg_l2", parts.get("body").addOrReplaceChild("leg_l2", CubeListBuilder.create(),
                PartPose.offsetAndRotation(4.50f, 0.00f, -2.50f, 0.00f, 0.35f, 0.00f)));
        parts.put("femur_l2", parts.get("leg_l2").addOrReplaceChild("femur_l2", CubeListBuilder.create().texOffs(54, 38).addBox(0.00f, -2.00f, -2.00f, 9, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.45f)));
        parts.put("tibia_l2", parts.get("femur_l2").addOrReplaceChild("tibia_l2", CubeListBuilder.create().texOffs(56, 46).addBox(0.00f, -1.50f, -1.50f, 11, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(9.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.20f)));
        parts.put("tarsus_l2", parts.get("tibia_l2").addOrReplaceChild("tarsus_l2", CubeListBuilder.create().texOffs(54, 52).addBox(0.00f, -1.00f, -1.00f, 4, 2, 2, new CubeDeformation(0.00f)).texOffs(66, 52).addBox(4.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(11.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.65f)));
        parts.put("leg_r3", parts.get("body").addOrReplaceChild("leg_r3", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-4.50f, 0.00f, 0.00f, 0.00f, 0.30f, 0.00f)));
        parts.put("femur_r3", parts.get("leg_r3").addOrReplaceChild("femur_r3", CubeListBuilder.create().texOffs(80, 38).addBox(-9.00f, -2.00f, -2.00f, 9, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.45f)));
        parts.put("tibia_r3", parts.get("femur_r3").addOrReplaceChild("tibia_r3", CubeListBuilder.create().texOffs(84, 46).addBox(-11.00f, -1.50f, -1.50f, 11, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-9.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.20f)));
        parts.put("tarsus_r3", parts.get("tibia_r3").addOrReplaceChild("tarsus_r3", CubeListBuilder.create().texOffs(72, 52).addBox(-4.00f, -1.00f, -1.00f, 4, 2, 2, new CubeDeformation(0.00f)).texOffs(84, 52).addBox(-5.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-11.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.65f)));
        parts.put("leg_l3", parts.get("body").addOrReplaceChild("leg_l3", CubeListBuilder.create(),
                PartPose.offsetAndRotation(4.50f, 0.00f, 0.00f, 0.00f, -0.30f, 0.00f)));
        parts.put("femur_l3", parts.get("leg_l3").addOrReplaceChild("femur_l3", CubeListBuilder.create().texOffs(106, 38).addBox(0.00f, -2.00f, -2.00f, 9, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.45f)));
        parts.put("tibia_l3", parts.get("femur_l3").addOrReplaceChild("tibia_l3", CubeListBuilder.create().texOffs(112, 46).addBox(0.00f, -1.50f, -1.50f, 11, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(9.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.20f)));
        parts.put("tarsus_l3", parts.get("tibia_l3").addOrReplaceChild("tarsus_l3", CubeListBuilder.create().texOffs(90, 52).addBox(0.00f, -1.00f, -1.00f, 4, 2, 2, new CubeDeformation(0.00f)).texOffs(102, 52).addBox(4.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(11.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.65f)));
        parts.put("leg_r4", parts.get("body").addOrReplaceChild("leg_r4", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-4.50f, 0.00f, 2.50f, 0.00f, 0.95f, 0.00f)));
        parts.put("femur_r4", parts.get("leg_r4").addOrReplaceChild("femur_r4", CubeListBuilder.create().texOffs(132, 38).addBox(-10.00f, -2.00f, -2.00f, 10, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.50f)));
        parts.put("tibia_r4", parts.get("femur_r4").addOrReplaceChild("tibia_r4", CubeListBuilder.create().texOffs(140, 46).addBox(-11.00f, -1.50f, -1.50f, 11, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(-10.00f, 0.00f, 0.00f, 0.00f, 0.00f, -1.36f)));
        parts.put("tarsus_r4", parts.get("tibia_r4").addOrReplaceChild("tarsus_r4", CubeListBuilder.create().texOffs(108, 52).addBox(-4.00f, -1.00f, -1.00f, 4, 2, 2, new CubeDeformation(0.00f)).texOffs(120, 52).addBox(-5.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(-11.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.54f)));
        parts.put("leg_l4", parts.get("body").addOrReplaceChild("leg_l4", CubeListBuilder.create(),
                PartPose.offsetAndRotation(4.50f, 0.00f, 2.50f, 0.00f, -0.95f, 0.00f)));
        parts.put("femur_l4", parts.get("leg_l4").addOrReplaceChild("femur_l4", CubeListBuilder.create().texOffs(160, 38).addBox(0.00f, -2.00f, -2.00f, 10, 4, 4, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(0.00f, 0.00f, 0.00f, 0.00f, 0.00f, -0.50f)));
        parts.put("tibia_l4", parts.get("femur_l4").addOrReplaceChild("tibia_l4", CubeListBuilder.create().texOffs(168, 46).addBox(0.00f, -1.50f, -1.50f, 11, 3, 3, new CubeDeformation(0.00f)),
                PartPose.offsetAndRotation(10.00f, 0.00f, 0.00f, 0.00f, 0.00f, 1.36f)));
        parts.put("tarsus_l4", parts.get("tibia_l4").addOrReplaceChild("tarsus_l4", CubeListBuilder.create().texOffs(126, 52).addBox(0.00f, -1.00f, -1.00f, 4, 2, 2, new CubeDeformation(0.00f)).texOffs(138, 52).addBox(4.00f, -1.00f, -1.00f, 1, 2, 2, new CubeDeformation(-0.12f)),
                PartPose.offsetAndRotation(11.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.54f)));
        return LayerDefinition.create(mesh, 256, 64);
    }

    public static LayerDefinition spiderEggSac() {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> parts = new HashMap<>();
        parts.put("root", mesh.getRoot());
        parts.put("body", parts.get("root").addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-6.00f, -1.00f, -6.00f, 12, 1, 12, new CubeDeformation(0.00f)).texOffs(48, 0).addBox(-4.50f, -6.00f, -3.50f, 4, 5, 4, new CubeDeformation(0.00f)).texOffs(0, 13).addBox(0.50f, -5.00f, -4.00f, 4, 4, 4, new CubeDeformation(0.00f)).texOffs(16, 13).addBox(-2.00f, -5.00f, 0.50f, 4, 4, 4, new CubeDeformation(0.00f)).texOffs(32, 13).addBox(-1.50f, -9.00f, -2.00f, 3, 3, 3, new CubeDeformation(0.00f)).texOffs(44, 13).addBox(-3.50f, -7.10f, -2.40f, 2, 1, 2, new CubeDeformation(0.05f)).texOffs(52, 13).addBox(-4.50f, -6.80f, -2.20f, 4, 1, 1, new CubeDeformation(-0.32f)),
                PartPose.offsetAndRotation(0.00f, 24.00f, 0.00f, 0.00f, 0.00f, 0.00f)));
        return LayerDefinition.create(mesh, 64, 32);
    }
}
