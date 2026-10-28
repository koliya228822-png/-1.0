package com.heroicpaths.client.spider;

import com.heroicpaths.entity.SpiderQueen;
import com.heroicpaths.entity.VenomSpitter;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

/**
 * Модель пауков логова (матерь, паук выводка, плевальщик, толстобрюх): восемь лап из трёх сегментов шагают
 * попеременно двумя четвёрками, брюшко дышит, жвалы шевелятся. Передние лапы вскидываются при ударе.
 * У матери вдобавок фазы (вылупившиеся яйца, расколотый кокон) и позы её приёмов.
 */
public class SpiderModel<T extends Mob> extends HierarchicalModel<T> {
    private static final String[] IDS = {"r1", "r2", "r3", "r4", "l1", "l2", "l3", "l4"};
    private final ModelPart root, body, head, abdomen;
    private final ModelPart[] leg = new ModelPart[8], femur = new ModelPart[8], tibia = new ModelPart[8];
    private final ModelPart fangR, fangL;
    @Nullable
    private final ModelPart eggsA, eggsB, shellTop, core, sac;
    /** Во сколько раз шаг чаще (мелкие пауки семенят, матерь ступает тяжело). */
    private final float stride;
    /** Все части модели: сбрасываются каждый кадр, поэтому список собран один раз. */
    private final ModelPart[] allParts;

    public SpiderModel(ModelPart root, float stride) {
        super(net.minecraft.client.renderer.RenderType::entityCutoutNoCull);
        this.root = root;
        this.stride = stride;
        this.allParts = root.getAllParts().toArray(ModelPart[]::new);
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.abdomen = body.getChild("abdomen");
        for (int i = 0; i < 8; i++) {
            leg[i] = body.getChild("leg_" + IDS[i]);
            femur[i] = leg[i].getChild("femur_" + IDS[i]);
            tibia[i] = femur[i].getChild("tibia_" + IDS[i]);
        }
        this.fangR = head.getChild("fang_r");
        this.fangL = head.getChild("fang_l");
        this.eggsA = child(abdomen, "eggs_a");
        this.eggsB = child(abdomen, "eggs_b");
        ModelPart shell = child(abdomen, "shell");
        this.shellTop = shell == null ? null : child(shell, "shell_top");
        this.core = shell == null ? null : child(shell, "core");
        this.sac = child(abdomen, "sac");
    }

    @Nullable
    private static ModelPart child(ModelPart parent, String name) {
        return parent.hasChild(name) ? parent.getChild(name) : null;
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        for (ModelPart part : allParts) part.resetPose();
        head.yRot = Mth.clamp(netHeadYaw, -40f, 40f) * Mth.DEG_TO_RAD * 0.6f;
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.4f;
        float walk = limbSwing * stride;
        float amount = Math.min(limbSwingAmount * 1.4f, 1f);
        for (int i = 0; i < 8; i++) {
            int s = i < 4 ? -1 : 1;
            // Две четвёрки: правые 1 и 3 с левыми 2 и 4 и наоборот.
            float phase = (i % 4 + (s > 0 ? 1 : 0)) % 2 == 0 ? 0 : Mth.PI;
            float swing = Mth.cos(walk + phase);
            float lift = Math.max(0, Mth.sin(walk + phase));
            leg[i].yRot += s * swing * 0.35f * amount;
            femur[i].zRot += -s * lift * 0.5f * amount;
            tibia[i].zRot += s * lift * 0.25f * amount;
            // В покое лапы чуть подрагивают.
            femur[i].zRot += -s * Mth.sin(ageInTicks * 0.09f + i * 1.3f) * 0.025f;
        }
        float breath = Mth.sin(ageInTicks * 0.07f);
        abdomen.xRot += breath * 0.035f;
        body.y += breath * 0.15f + Math.abs(Mth.sin(walk)) * -0.4f * amount;
        float chatter = Mth.sin(ageInTicks * 0.35f) * 0.08f;
        fangR.zRot -= chatter;
        fangL.zRot += chatter;
        if (sac != null) {
            // Мешок с ядом пульсирует.
            float pulse = 1 + Mth.sin(ageInTicks * 0.15f) * 0.04f;
            sac.xScale = sac.yScale = sac.zScale = pulse;
        }
        if (attackTime > 0) strike(Mth.sin(attackTime * Mth.PI), entity instanceof VenomSpitter);
        if (entity instanceof SpiderQueen queen) queen(queen, ageInTicks);
    }

    /** Удар: передние лапы вскинуты, голова с жвалами бросается вперёд (плевальщик — задирает голову и плюёт). */
    private void strike(float k, boolean spit) {
        if (spit) {
            head.xRot -= 0.5f * k;
            abdomen.xRot += 0.3f * k;
            return;
        }
        for (int i : new int[]{0, 4}) {
            int s = i < 4 ? -1 : 1;
            femur[i].zRot += -s * 0.9f * k;
            leg[i].yRot += s * 0.4f * k;
            tibia[i].zRot += s * 0.5f * k;
        }
        head.xRot += 0.35f * k;
        body.xRot += 0.12f * k;
        fangR.zRot += 0.4f * k;
        fangL.zRot -= 0.4f * k;
    }

    private void queen(SpiderQueen queen, float ageInTicks) {
        int phase = queen.phase();
        if (eggsA != null) eggsA.visible = phase < 2;
        if (eggsB != null) eggsB.visible = phase < 3;
        if (shellTop != null) shellTop.visible = phase < 3;
        if (core != null) core.visible = phase >= 3;
        float partial = ageInTicks - (int) ageInTicks;
        float t = queen.clientActionTicks + partial;
        if (queen.biteTicks > 0) strike(Mth.sin((queen.biteTicks - partial) / 10f * Mth.PI), false);
        switch (queen.action()) {
            case SpiderQueen.SHED -> {
                // Встаёт на дыбы и машет передними лапами.
                float k = Math.min(1, t / 8f) * Math.min(1, (SpiderQueen.SHED_TICKS - t) / 8f);
                body.xRot -= 0.45f * k;
                body.y -= 3 * k;
                for (int i : new int[]{0, 1, 4, 5}) {
                    int s = i < 4 ? -1 : 1;
                    femur[i].zRot += -s * (0.7f + 0.3f * Mth.sin(t * 0.6f + i)) * k;
                    tibia[i].zRot += s * 0.4f * k;
                }
                head.xRot -= 0.3f * k;
            }
            case SpiderQueen.NOVA -> {
                // Приседает, брюшко дрожит, перед выбросом паутины — рывок вверх.
                float k = Math.min(1, t / SpiderQueen.NOVA_WINDUP);
                body.y += 2.5f * k;
                abdomen.xRot += 0.15f * k + Mth.sin(t * 1.4f) * 0.04f * k;
                for (int i = 0; i < 8; i++) femur[i].zRot += (i < 4 ? 1 : -1) * 0.25f * k;
            }
            case SpiderQueen.SPIT, SpiderQueen.WEB -> {
                float wind = queen.action() == SpiderQueen.SPIT ? SpiderQueen.SPIT_WINDUP : SpiderQueen.WEB_WINDUP;
                float k = Math.min(1, t / wind);
                head.xRot -= 0.45f * k;
                body.xRot -= 0.15f * k;
                if (queen.action() == SpiderQueen.WEB) abdomen.xRot -= 0.5f * k;
            }
            case SpiderQueen.LAY -> {
                // Брюшко опускается к полу — откладывает кладку.
                float k = Math.min(1, t / 10f);
                abdomen.xRot -= 0.55f * k;
                body.y += 1.5f * k;
            }
            case SpiderQueen.LEAP -> {
                if (t < SpiderQueen.LEAP_WINDUP) {
                    float k = t / SpiderQueen.LEAP_WINDUP;
                    body.y += 3 * k;
                    for (int i = 0; i < 8; i++) femur[i].zRot += (i < 4 ? 1 : -1) * 0.3f * k;
                } else {
                    // В полёте лапы раскинуты вперёд.
                    for (int i = 0; i < 8; i++) {
                        int s = i < 4 ? -1 : 1;
                        femur[i].zRot += -s * 0.4f;
                        tibia[i].zRot += -s * 0.4f;
                    }
                    body.xRot -= 0.2f;
                }
            }
            default -> { }
        }
    }
}
