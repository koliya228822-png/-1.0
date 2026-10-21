package com.heroicpaths.client.clay;

import com.heroicpaths.entity.ClayColossus;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Модель Глиняного колосса. Части, отвалившиеся в прошлых фазах, скрыты. Анимации: тяжёлая походка,
 * удар верхними руками (руки пониже машут следом), замах над головой перед ударом оземь, присед перед прыжком,
 * дрожь при сбросе лат.
 */
public class ClayColossusModel extends HierarchicalModel<ClayColossus> {
    private final ModelPart root;
    private final Map<String, ModelPart> parts = new HashMap<>();

    public ClayColossusModel(ModelPart root) {
        this.root = root;
        for (String name : new String[]{"body", "leg_left", "leg_right"}) collect(name, root.getChild(name));
    }

    private void collect(String name, ModelPart part) {
        parts.put(name, part);
        for (String child : new String[]{"chest_plate", "belt_plate", "head", "helmet", "arm_left", "arm_right", "pauldron_left",
                "pauldron_right", "arm_mid_left", "arm_mid_right", "arm_lower_left", "arm_lower_right"}) {
            if (part.hasChild(child)) collect(child, part.getChild(child));
        }
    }

    public ModelPart part(String name) {
        return parts.get(name);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(ClayColossus entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root().getAllParts().forEach(ModelPart::resetPose);
        int phase = entity.phase();
        part("chest_plate").visible = phase < 2;
        part("pauldron_left").visible = phase < 2;
        part("pauldron_right").visible = phase < 2;
        part("arm_lower_left").visible = phase < 2;
        part("arm_lower_right").visible = phase < 2;
        part("helmet").visible = phase < 3;
        part("belt_plate").visible = phase < 3;
        part("arm_mid_left").visible = phase < 3;
        part("arm_mid_right").visible = phase < 3;

        ModelPart body = part("body"), head = part("head");
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        // Походка: ноги шагают, тело раскачивается, большие руки маятником.
        float walk = limbSwing * 0.45f;
        float amount = Math.min(limbSwingAmount, 1f);
        part("leg_right").xRot = Mth.cos(walk) * 0.9f * amount;
        part("leg_left").xRot = Mth.cos(walk + Mth.PI) * 0.9f * amount;
        body.zRot = Mth.cos(walk) * 0.06f * amount;
        body.y += Math.abs(Mth.sin(walk)) * 0.8f * amount;
        float breathe = Mth.sin(ageInTicks * 0.06f) * 0.03f;
        body.xRot += breathe;
        ModelPart armL = part("arm_left"), armR = part("arm_right");
        armR.xRot = Mth.cos(walk + Mth.PI) * 0.5f * amount;
        armL.xRot = Mth.cos(walk) * 0.5f * amount;
        armR.zRot = 0.08f;
        armL.zRot = -0.08f;
        for (String side : new String[]{"left", "right"}) {
            float s = side.equals("left") ? -1 : 1;
            ModelPart mid = part("arm_mid_" + side), low = part("arm_lower_" + side);
            mid.xRot = Mth.sin(ageInTicks * 0.09f + s) * 0.2f - 0.2f;
            low.xRot = Mth.sin(ageInTicks * 0.11f + s * 2) * 0.25f - 0.3f;
        }

        float partial = ageInTicks - entity.tickCount;
        // Удар: верхние руки обрушиваются сверху, средние и нижние бьют следом.
        if (entity.attackAnimTicks > 0) {
            float t = (entity.attackAnimTicks - partial) / 10f;
            float swing = Mth.sin(t * Mth.PI);
            armR.xRot = -2.2f * swing;
            armL.xRot = -2.2f * swing;
            part("arm_mid_left").xRot -= 1.4f * swing;
            part("arm_mid_right").xRot -= 1.4f * swing;
            part("arm_lower_left").xRot -= 1.0f * swing;
            part("arm_lower_right").xRot -= 1.0f * swing;
            body.xRot += 0.25f * swing;
        }
        int action = entity.action();
        float at = entity.clientActionTicks + partial;
        if (action == ClayColossus.SLAM) {
            // Замах: все руки поднимаются над головой, в конце — резкий удар вниз.
            float raise = Math.min(1f, at / (ClayColossus.SLAM_WINDUP - 4f));
            float drop = at > ClayColossus.SLAM_WINDUP - 4 ? Math.min(1f, (at - ClayColossus.SLAM_WINDUP + 4) / 4f) : 0f;
            float up = raise * (1 - drop);
            armR.xRot = -3.0f * up + 0.4f * drop;
            armL.xRot = -3.0f * up + 0.4f * drop;
            armR.zRot = 0.08f - 0.3f * up;
            armL.zRot = -0.08f + 0.3f * up;
            part("arm_mid_left").xRot = -2.4f * up;
            part("arm_mid_right").xRot = -2.4f * up;
            part("arm_lower_left").xRot = -1.8f * up;
            part("arm_lower_right").xRot = -1.8f * up;
            body.xRot += -0.2f * up + 0.5f * drop;
        } else if (action == ClayColossus.THROW) {
            float raise = Math.min(1f, at / ClayColossus.THROW_WINDUP);
            armR.xRot = -2.8f * raise;
            armL.xRot = -2.8f * raise;
            body.xRot -= 0.15f * raise;
        } else if (action == ClayColossus.LEAP) {
            float crouch = at < ClayColossus.LEAP_WINDUP ? at / ClayColossus.LEAP_WINDUP : Math.max(0f, 1f - (at - ClayColossus.LEAP_WINDUP) / 5f);
            body.y += 2.5f * crouch;
            body.xRot += 0.35f * crouch;
            armR.xRot = 0.6f * crouch - 0.8f * (1 - crouch);
            armL.xRot = 0.6f * crouch - 0.8f * (1 - crouch);
            part("leg_left").xRot -= 0.4f * crouch;
            part("leg_right").xRot -= 0.4f * crouch;
        } else if (action == ClayColossus.SHED) {
            // Сброс лат: дрожь всем телом, руки раскинуты, голова запрокинута в рёве.
            float shake = Mth.sin(ageInTicks * 2.3f) * 0.06f;
            body.zRot += shake;
            body.xRot -= 0.15f;
            head.xRot = -0.5f;
            armR.zRot = 0.9f;
            armL.zRot = -0.9f;
            armR.xRot = -0.4f;
            armL.xRot = -0.4f;
        }
    }
}
