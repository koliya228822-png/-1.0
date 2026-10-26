package com.heroicpaths.client.werewolf;

import com.heroicpaths.entity.Werewolf;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/** Оборотень: сгорбленная походка на полусогнутых, качающийся хвост, оскал; удар — взмах обеими лапами с раскрытой пастью. */
public class WerewolfModel extends HierarchicalModel<Werewolf> {
    private final ModelPart root, torso, head, jaw, armLeft, armRight, forearmLeft, forearmRight, legLeft, legRight, shinLeft, shinRight,
            tail, tail2, tail3;

    public WerewolfModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        ModelPart hips = root.getChild("hips");
        this.torso = hips.getChild("torso");
        this.head = torso.getChild("head");
        this.jaw = head.getChild("jaw");
        this.armLeft = torso.getChild("arm_left");
        this.armRight = torso.getChild("arm_right");
        this.forearmLeft = armLeft.getChild("forearm_left");
        this.forearmRight = armRight.getChild("forearm_right");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");
        this.shinLeft = legLeft.getChild("shin_left");
        this.shinRight = legRight.getChild("shin_right");
        this.tail = hips.getChild("tail");
        this.tail2 = tail.getChild("tail2");
        this.tail3 = tail2.getChild("tail3");
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Werewolf entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float walk = limbSwing * 0.7f;
        float amount = Math.min(limbSwingAmount, 1f);
        float breath = Mth.sin(ageInTicks * 0.09f);

        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.8f;
        head.xRot += headPitch * Mth.DEG_TO_RAD * 0.8f;
        jaw.xRot += 0.06f * Mth.sin(ageInTicks * 0.15f);
        torso.xRot += 0.03f * breath + 0.08f * amount;

        // Ноги: бедро вперёд-назад, голень подгибается, когда нога идёт назад.
        legRight.xRot += Mth.cos(walk) * 0.8f * amount;
        legLeft.xRot += Mth.cos(walk + Mth.PI) * 0.8f * amount;
        shinRight.xRot += Math.max(0f, Mth.sin(walk)) * 0.6f * amount;
        shinLeft.xRot += Math.max(0f, Mth.sin(walk + Mth.PI)) * 0.6f * amount;
        root.y = -Math.abs(Mth.cos(walk)) * 1.6f * amount;

        // Руки болтаются в такт шагам и чуть покачиваются на месте.
        armRight.xRot += Mth.cos(walk + Mth.PI) * 0.6f * amount + 0.03f * breath;
        armLeft.xRot += Mth.cos(walk) * 0.6f * amount + 0.03f * breath;
        armRight.zRot += 0.03f * breath;
        armLeft.zRot -= 0.03f * breath;

        // Хвост: ленивое покачивание, в беге — сильнее.
        float sway = Mth.sin(ageInTicks * 0.12f + walk * 0.5f) * (0.18f + 0.25f * amount);
        tail.yRot = sway;
        tail2.yRot = sway * 0.8f;
        tail3.yRot = sway * 0.6f;
        tail.xRot += 0.25f * amount;

        if (attackTime > 0) {
            // Взмах обеими лапами сверху вниз, корпус подаётся вперёд, пасть раскрывается.
            float swing = Mth.sin(attackTime * Mth.PI);
            float lift = Mth.sin(Mth.sqrt(attackTime) * Mth.PI);
            armRight.xRot -= 2.0f * lift - 0.6f * swing;
            armLeft.xRot -= 2.0f * lift - 0.6f * swing;
            forearmRight.xRot -= 0.5f * swing;
            forearmLeft.xRot -= 0.5f * swing;
            armRight.zRot -= 0.3f * swing;
            armLeft.zRot += 0.3f * swing;
            torso.xRot += 0.25f * swing;
            head.xRot -= 0.2f * swing;
            jaw.xRot += 0.45f * swing;
        }
    }
}
