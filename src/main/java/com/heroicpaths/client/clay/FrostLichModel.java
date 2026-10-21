package com.heroicpaths.client.clay;

import com.heroicpaths.entity.FrostLich;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Модель Лича (в стиле Аинза из «Повелителя»): парит над полом, покачиваясь, мантия колышется, вокруг кружат тёмно-красные сферы
 * (два, три, четыре — по фазе). Сброшенные части скрыты. Колдуя, Лич вскидывает руки; на смене фазы раскидывает их.
 */
public class FrostLichModel extends HierarchicalModel<FrostLich> {
    private static final String[] NAMES = {"body", "chest_armor", "belt", "robe", "head", "crown", "arm_left", "arm_right",
            "pauldron_left", "pauldron_right", "shard_1", "shard_2", "shard_3", "shard_4"};
    private final ModelPart root;
    private final Map<String, ModelPart> parts = new HashMap<>();

    public FrostLichModel(ModelPart root) {
        this.root = root;
        collect(root);
    }

    private void collect(ModelPart part) {
        for (String name : NAMES) {
            if (part.hasChild(name)) {
                ModelPart child = part.getChild(name);
                parts.put(name, child);
                collect(child);
            }
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
    public void setupAnim(FrostLich entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        int phase = entity.phase();
        part("pauldron_left").visible = phase < 2;
        part("pauldron_right").visible = phase < 2;
        part("crown").visible = phase < 2;
        part("chest_armor").visible = phase < 2;
        part("belt").visible = phase < 3;

        ModelPart body = part("body"), head = part("head");
        body.y += Mth.sin(ageInTicks * 0.08f) * 1.2f;
        body.xRot += Math.min(limbSwingAmount, 1f) * 0.25f;
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        part("robe").xRot = Mth.sin(ageInTicks * 0.07f) * 0.06f - Math.min(limbSwingAmount, 1f) * 0.3f;
        ModelPart armL = part("arm_left"), armR = part("arm_right");
        armR.zRot = 0.25f + Mth.sin(ageInTicks * 0.09f) * 0.08f;
        armL.zRot = -0.25f - Mth.sin(ageInTicks * 0.09f) * 0.08f;
        armR.xRot = -0.3f + Mth.cos(ageInTicks * 0.07f) * 0.1f;
        armL.xRot = -0.3f - Mth.cos(ageInTicks * 0.07f) * 0.1f;

        float at = entity.clientActionTicks + ageInTicks - entity.tickCount;
        if (entity.action() == FrostLich.CAST) {
            float raise = Math.min(1f, at / (FrostLich.CAST_TICKS * 0.6f));
            armR.xRot = -2.5f * raise;
            armL.xRot = -2.5f * raise;
            armR.zRot = 0.25f - 0.35f * raise;
            armL.zRot = -0.25f + 0.35f * raise;
            head.xRot -= 0.3f * raise;
        } else if (entity.action() == FrostLich.SHATTER) {
            armR.zRot = 1.3f;
            armL.zRot = -1.3f;
            armR.xRot = -0.5f;
            armL.xRot = -0.5f;
            head.xRot = -0.6f;
            body.zRot = Mth.sin(ageInTicks * 2.1f) * 0.05f;
        } else if (attackTime > 0) {
            float swing = Mth.sin(attackTime * Mth.PI);
            armR.xRot = -1.8f * swing;
        }

        // Ледяные осколки кружат вокруг груди: два в первой фазе, три во второй, четыре в третьей.
        int shards = phase + 1;
        for (int i = 1; i <= 4; i++) {
            ModelPart shard = part("shard_" + i);
            shard.visible = i <= shards;
            float angle = ageInTicks * (0.06f + 0.02f * phase) + Mth.TWO_PI * i / shards;
            shard.x = Mth.cos(angle) * 15;
            shard.z = Mth.sin(angle) * 15;
            shard.y = -6 + Mth.sin(ageInTicks * 0.12f + i) * 2.5f + body.y - 2;
            shard.yRot = angle * 2;
            shard.zRot = 0.4f;
        }
    }
}
