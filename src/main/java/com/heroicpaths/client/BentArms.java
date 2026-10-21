package com.heroicpaths.client;

import com.heroicpaths.client.armor.ArmorModels;
import com.heroicpaths.item.ClassArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Руки, которые сгибаются в локте: пока у игрока в руке оружие с особыми ударами (WeaponMoves), обычные прямые руки
 * не рисуются, а вместо них — плечо и предплечье по отдельности. Кулак тянется к точке из положения удара, локоть
 * находится по длинам плеча и предплечья (отведён вниз, наружу и назад). Рукава классовой брони гнутся так же.
 */
public final class BentArms {
    private BentArms() {}

    /** От плеча (точки поворота руки) до локтя и от локтя до кулака, пикселей модели: вместе — длина прямой руки. */
    private static final float UPPER = 4, FORE = WeaponMoves.ARM_REACH - UPPER;

    public static boolean active(LivingEntity entity) {
        return entity instanceof AbstractClientPlayer && WeaponMoves.is(entity.getMainHandItem())
                && !WeaponMoves.channeling(entity, entity.getMainHandItem());
    }

    // ---------------------------------------------------------------- части рук из скина

    /** Плечо и предплечье каждой руки с рукавами; [0] — для широких рук, [1] — для тонких. */
    private static ModelPart[] roots;

    private static ModelPart root(boolean slim) {
        if (roots == null) roots = new ModelPart[]{bake(false), bake(true)};
        return roots[slim ? 1 : 0];
    }

    /**
     * Те же места текстуры скина, что у обычной руки: плечо — верхние 6 строк руки, предплечье — нижние 7 (на строку
     * заходит на плечо, чтобы на сгибе не было щели).
     */
    private static ModelPart bake(boolean slim) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        int w = slim ? 3 : 4;
        CubeDeformation sleeve = new CubeDeformation(0.25F);
        root.addOrReplaceChild("right_upper", CubeListBuilder.create().texOffs(40, 16).addBox(-3, -2, -2, w, 6, 4), PartPose.ZERO);
        root.addOrReplaceChild("right_fore", CubeListBuilder.create().texOffs(40, 21).addBox(-3, -1, -2, w, 7, 4), PartPose.ZERO);
        root.addOrReplaceChild("right_upper_sleeve", CubeListBuilder.create().texOffs(40, 32).addBox(-3, -2, -2, w, 6, 4, sleeve), PartPose.ZERO);
        root.addOrReplaceChild("right_fore_sleeve", CubeListBuilder.create().texOffs(40, 37).addBox(-3, -1, -2, w, 7, 4, sleeve), PartPose.ZERO);
        root.addOrReplaceChild("left_upper", CubeListBuilder.create().texOffs(32, 48).addBox(-1, -2, -2, w, 6, 4), PartPose.ZERO);
        root.addOrReplaceChild("left_fore", CubeListBuilder.create().texOffs(32, 53).addBox(-1, -1, -2, w, 7, 4), PartPose.ZERO);
        root.addOrReplaceChild("left_upper_sleeve", CubeListBuilder.create().texOffs(48, 48).addBox(-1, -2, -2, w, 6, 4, sleeve), PartPose.ZERO);
        root.addOrReplaceChild("left_fore_sleeve", CubeListBuilder.create().texOffs(48, 53).addBox(-1, -1, -2, w, 7, 4, sleeve), PartPose.ZERO);
        // Скругление локтя: короткий кусок руки, который рисуется на сгибе под промежуточными углами.
        root.addOrReplaceChild("right_elbow", CubeListBuilder.create().texOffs(40, 23).addBox(-3, -1, -2, w, 2, 4), PartPose.ZERO);
        root.addOrReplaceChild("right_elbow_sleeve", CubeListBuilder.create().texOffs(40, 39).addBox(-3, -1, -2, w, 2, 4, sleeve), PartPose.ZERO);
        root.addOrReplaceChild("left_elbow", CubeListBuilder.create().texOffs(32, 55).addBox(-1, -1, -2, w, 2, 4), PartPose.ZERO);
        root.addOrReplaceChild("left_elbow_sleeve", CubeListBuilder.create().texOffs(48, 55).addBox(-1, -1, -2, w, 2, 4, sleeve), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    // ---------------------------------------------------------------- локоть

    /** Локоть между плечом s и кулаком h: в плоскости, повёрнутой к pole. */
    static Vector3f elbow(Vector3f s, Vector3f h, Vector3f pole) {
        Vector3f d = new Vector3f(h).sub(s);
        float len = Math.max(Math.abs(FORE - UPPER) + 0.01F, Math.min(UPPER + FORE - 0.001F, d.length()));
        d.normalize();
        float a = (UPPER * UPPER + len * len - FORE * FORE) / (2 * len), k = (float) Math.sqrt(Math.max(0, UPPER * UPPER - a * a));
        Vector3f perp = new Vector3f(pole).sub(new Vector3f(d).mul(pole.dot(d)));
        if (perp.lengthSquared() < 1e-6F) perp.set(0, 0, 1);
        return new Vector3f(d).mul(a).add(perp.normalize().mul(k)).add(s);
    }

    // ---------------------------------------------------------------- отрисовка

    /** Обычные прямые руки и рукава прячутся, пока рисуются согнутые (на следующем кадре PlayerRenderer вернёт их сам). */
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!active(event.getEntity()) || !(event.getRenderer().getModel() instanceof PlayerModel<?> model)) return;
        model.rightArm.visible = false;
        model.leftArm.visible = false;
        model.rightSleeve.visible = false;
        model.leftSleeve.visible = false;
    }

    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins())
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new Layer(renderer));
    }

    static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!active(player) || player.isInvisible()) return;
            PlayerSkin skin = player.getSkin();
            ModelPart root = root(skin.model() == PlayerSkin.Model.SLIM);
            int overlay = LivingEntityRenderer.getOverlayCoords(player, 0);
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            for (boolean right : new boolean[]{true, false}) {
                ModelPart arm = right ? getParentModel().rightArm : getParentModel().leftArm;
                Vector3f s = WeaponMoves.pivot(arm);
                Vector3f target = WeaponMoves.handTarget(player, right);
                // Рука без цели висит как обычная: кулак на конце прямой руки.
                Vector3f h = target == null
                        ? new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).transform(new Vector3f(0, WeaponMoves.ARM_REACH, 0)).add(s)
                        : WeaponMoves.reach(s, target);
                Vector3f e = elbow(s, h, new Vector3f(right ? -1 : 1, 1, 0.6F));
                String side = right ? "right" : "left";
                ModelPart upper = root.getChild(side + "_upper"), fore = root.getChild(side + "_fore");
                ModelPart upperSleeve = root.getChild(side + "_upper_sleeve"), foreSleeve = root.getChild(side + "_fore_sleeve");
                place(upper, s, e);
                place(fore, e, h);
                place(upperSleeve, s, e);
                place(foreSleeve, e, h);
                ResourceLocation texture = skin.texture();
                VertexConsumer solid = buffers.getBuffer(RenderType.entitySolid(texture));
                upper.render(pose, solid, light, overlay);
                fore.render(pose, solid, light, overlay);
                // Локоть не угловатый: на сгибе ещё два куска руки под промежуточными углами.
                Quaternionf qu = rotation(upper), qf = rotation(fore);
                ModelPart joint = root.getChild(side + "_elbow");
                for (float k : new float[]{1 / 3F, 2 / 3F}) renderAt(pose, joint, e, new Quaternionf(qu).slerp(qf, k), solid, light, overlay);
                if (player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE)) {
                    VertexConsumer translucent = buffers.getBuffer(RenderType.entityTranslucent(texture));
                    upperSleeve.render(pose, translucent, light, overlay);
                    foreSleeve.render(pose, translucent, light, overlay);
                    ModelPart jointSleeve = root.getChild(side + "_elbow_sleeve");
                    for (float k : new float[]{1 / 3F, 2 / 3F}) renderAt(pose, jointSleeve, e, new Quaternionf(qu).slerp(qf, k), translucent, light, overlay);
                }
                if (chest.getItem() instanceof ClassArmorItem armor) renderArmor(pose, buffers, light, player, chest, armor, right, upper, fore, e);
            }
        }

        /** Детали рукава брони: что выше локтя — на плече, что ниже — на предплечье. */
        private static void renderArmor(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, ItemStack chest,
                                        ClassArmorItem armor, boolean right, ModelPart upper, ModelPart fore, Vector3f elbow) {
            ResourceLocation texture = ClientHooks.getArmorTexture(player, chest, armor.getMaterial().value().layers().get(0), false, EquipmentSlot.CHEST);
            VertexConsumer vc = buffers.getBuffer(RenderType.armorCutoutNoCull(texture));
            for (Map.Entry<ModelPart, Float> piece : ArmorModels.armPieces(armor, right)) {
                pose.pushPose();
                if (piece.getValue() < UPPER) {
                    upper.translateAndRotate(pose);
                } else {
                    fore.translateAndRotate(pose);
                    pose.translate(0, -UPPER / 16, 0);
                }
                piece.getKey().render(pose, vc, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
    }

    private static Quaternionf rotation(ModelPart part) {
        return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot);
    }

    /** Часть (с нулевой позой) в точке at с поворотом rot. */
    private static void renderAt(PoseStack pose, ModelPart part, Vector3f at, Quaternionf rot, VertexConsumer vc, int light, int overlay) {
        pose.pushPose();
        pose.translate(at.x / 16, at.y / 16, at.z / 16);
        pose.mulPose(rot);
        part.render(pose, vc, light, overlay);
        pose.popPose();
    }

    /** Часть руки из точки from, направленная на to. */
    private static void place(ModelPart part, Vector3f from, Vector3f to) {
        part.setPos(from.x, from.y, from.z);
        WeaponMoves.aim(part, from, to);
    }
}
