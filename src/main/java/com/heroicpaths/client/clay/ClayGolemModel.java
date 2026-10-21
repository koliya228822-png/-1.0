package com.heroicpaths.client.clay;

import com.heroicpaths.entity.ClayGolem;
import com.heroicpaths.entity.ClayThrower;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Модель мобов данжей с телом, головой, двумя руками и ногами (големы, вурдалак, банши, лоскут плоти): походка и удар обеими руками. */
public class ClayGolemModel<T extends ClayGolem> extends HierarchicalModel<T> {
    private final ModelPart root, body, head, armLeft, armRight, legLeft, legRight;

    public ClayGolemModel(ModelPart root) {
        this(root, false);
    }

    /** translucent — рисовать полупрозрачным (призраки вроде банши). */
    public ClayGolemModel(ModelPart root, boolean translucent) {
        super(translucent ? net.minecraft.client.renderer.RenderType::entityTranslucent : net.minecraft.client.renderer.RenderType::entityCutoutNoCull);
        this.root = root;
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.armLeft = root.getChild("arm_left");
        this.armRight = root.getChild("arm_right");
        this.legLeft = root.getChild("leg_left");
        this.legRight = root.getChild("leg_right");
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        float walk = limbSwing * 0.8f;
        float amount = Math.min(limbSwingAmount, 1f);
        legRight.xRot = Mth.cos(walk) * 1.1f * amount;
        legLeft.xRot = Mth.cos(walk + Mth.PI) * 1.1f * amount;
        body.zRot = Mth.cos(walk) * 0.1f * amount;
        armRight.xRot = Mth.cos(walk + Mth.PI) * 0.8f * amount;
        armLeft.xRot = Mth.cos(walk) * 0.8f * amount;
        armRight.zRot = 0.1f + Mth.sin(ageInTicks * 0.08f) * 0.04f;
        armLeft.zRot = -0.1f - Mth.sin(ageInTicks * 0.08f) * 0.04f;
        if (attackTime > 0) {
            float swing = Mth.sin(attackTime * Mth.PI);
            if (entity instanceof ClayThrower) {
                // Бросок из-за плеча одной рукой.
                armRight.xRot = -2.6f * swing + 0.4f * Mth.sin(attackTime * Mth.TWO_PI);
            } else {
                armRight.xRot = -1.9f * swing;
                armLeft.xRot = -1.9f * swing;
                body.xRot = 0.2f * swing;
            }
        }
    }
}
