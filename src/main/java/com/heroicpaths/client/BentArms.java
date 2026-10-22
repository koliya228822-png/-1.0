package com.heroicpaths.client;

import com.heroicpaths.client.armor.ArmorModels;
import com.heroicpaths.item.ClassArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Руки, которые сгибаются в локте: пока у игрока в руке оружие с особыми ударами (WeaponMoves), обычные прямые руки
 * не рисуются, а вместо них — те же руки, согнутые в локте. Кулак тянется к точке из положения удара, локоть
 * находится по длинам плеча и предплечья (отведён вниз, наружу и назад).
 * <p>
 * Рука сгибается как гибкая трубка: каждая вершина прямой руки (вместе с рукавом скина, классовой и ванильной
 * бронёй) переносится по своей высоте — выше локтя вместе с плечом, ниже — с предплечьем, а на сгибе плавно
 * поворачивается между ними. Грани на сгибе режутся на тонкие полоски, поэтому локоть круглый, без ступенек.
 */
public final class BentArms {
    private BentArms() {}

    /** От плеча (точки поворота руки) до локтя и от локтя до кулака, пикселей модели: вместе — длина прямой руки. */
    private static final float UPPER = 4, FORE = WeaponMoves.ARM_REACH - UPPER;

    public static boolean active(LivingEntity entity) {
        return entity instanceof AbstractClientPlayer && WeaponMoves.is(entity.getMainHandItem())
                && !WeaponMoves.channeling(entity, entity.getMainHandItem());
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

    /** Модель внешнего слоя ванильной брони: её рукава рисуются здесь согнутыми. */
    private static HumanoidModel<LivingEntity> vanillaArmor;

    static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final Bend bend = new Bend();

        Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (!active(player) || player.isInvisible()) return;
            PlayerModel<AbstractClientPlayer> model = getParentModel();
            ResourceLocation skin = player.getSkin().texture();
            int overlay = LivingEntityRenderer.getOverlayCoords(player, 0);
            ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
            for (boolean right : new boolean[]{true, false}) {
                ModelPart arm = right ? model.rightArm : model.leftArm;
                Vector3f s = WeaponMoves.pivot(arm);
                Vector3f target = WeaponMoves.handTarget(player, right);
                // Рука без цели висит как обычная: кулак на конце прямой руки.
                Vector3f h = target == null
                        ? new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).transform(new Vector3f(0, WeaponMoves.ARM_REACH, 0)).add(s)
                        : WeaponMoves.reach(s, target);
                bend.set(s, elbow(s, h, new Vector3f(right ? -1 : 1, 1, 0.6F)), h, pose.last());
                bend.draw(arm, buffers.getBuffer(RenderType.entitySolid(skin)), light, overlay, -1);
                if (player.isModelPartShown(right ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE))
                    bend.draw(right ? model.rightSleeve : model.leftSleeve, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, overlay, -1);
                if (chest.getItem() instanceof ArmorItem armor) renderArmor(buffers, light, player, chest, armor, right);
            }
        }

        /** Рукав брони — та же кость руки модели брони, согнутая вместе с рукой. */
        private void renderArmor(MultiBufferSource buffers, int light, AbstractClientPlayer player, ItemStack chest, ArmorItem armor, boolean right) {
            int overlay = OverlayTexture.NO_OVERLAY;
            if (armor instanceof ClassArmorItem classArmor) {
                HumanoidModel<?> model = ArmorModels.chestModel(classArmor);
                if (model == null) return;
                ModelPart bone = right ? model.rightArm : model.leftArm;
                ResourceLocation texture = ClientHooks.getArmorTexture(player, chest, armor.getMaterial().value().layers().get(0), false, EquipmentSlot.CHEST);
                bend.draw(bone, buffers.getBuffer(RenderType.armorCutoutNoCull(texture)), light, overlay, -1);
                if (chest.hasFoil()) bend.draw(bone, buffers.getBuffer(RenderType.armorEntityGlint()), light, overlay, -1);
                return;
            }
            if (vanillaArmor == null)
                vanillaArmor = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
            ModelPart bone = right ? vanillaArmor.rightArm : vanillaArmor.leftArm;
            // Как в HumanoidArmorLayer: слои материала (крашеные — цветом краски), отделка и блеск чар.
            List<ArmorMaterial.Layer> layers = armor.getMaterial().value().layers();
            int dye = chest.is(ItemTags.DYEABLE) ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(chest, DyedItemColor.LEATHER_COLOR)) : -1;
            for (ArmorMaterial.Layer layer : layers) {
                ResourceLocation texture = ClientHooks.getArmorTexture(player, chest, layer, false, EquipmentSlot.CHEST);
                bend.draw(bone, buffers.getBuffer(RenderType.armorCutoutNoCull(texture)), light, overlay, layer.dyeable() ? dye : -1);
            }
            ArmorTrim trim = chest.get(DataComponents.TRIM);
            if (trim != null) {
                TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getAtlas(Sheets.ARMOR_TRIMS_SHEET)
                        .getSprite(trim.outerTexture(armor.getMaterial()));
                bend.draw(bone, sprite.wrap(buffers.getBuffer(Sheets.armorTrimsSheet(trim.pattern().value().decal()))), light, overlay, -1);
            }
            if (chest.hasFoil()) bend.draw(bone, buffers.getBuffer(RenderType.armorEntityGlint()), light, overlay, -1);
        }
    }

    /**
     * Сгибатель руки. Часть рисуется в своих координатах прямой руки (от точки поворота плеча, ось руки — +y), а этот
     * VertexConsumer переносит каждую вершину на согнутую руку и только потом — в пространство игрока.
     */
    static final class Bend implements VertexConsumer {
        /** Полуширина сгиба по длине руки и сколько полосок на нём: чем их больше, тем круглее локоть. */
        private static final float BAND = 2.5F;
        private static final int STRIPS = 24;
        private static final PoseStack STRAIGHT = new PoseStack();

        private final Vector3f elbow = new Vector3f();
        /** Поворот плеча и поворот в локте (от направления плеча к направлению предплечья). */
        private final Quaternionf upper = new Quaternionf(), hinge = new Quaternionf();
        private final ModelPart aim = new ModelPart(List.of(), java.util.Map.of());
        private Matrix4f matrix;
        private Matrix3f normalMatrix;
        private VertexConsumer out;
        private final float[][] quad = new float[4][8];
        private final float[] cuts = new float[STRIPS + 3];
        private final float[] a = new float[8], b = new float[8], c = new float[8], d = new float[8];
        private int n, color, overlay, light;
        private final Quaternionf rot = new Quaternionf();
        private final Vector3f tmp = new Vector3f(), nrm = new Vector3f();

        void set(Vector3f s, Vector3f e, Vector3f h, PoseStack.Pose pose) {
            elbow.set(e);
            WeaponMoves.aim(aim, s, e);
            upper.rotationZYX(aim.zRot, aim.yRot, aim.xRot);
            Vector3f du = new Vector3f(e).sub(s).normalize(), df = new Vector3f(h).sub(e);
            if (df.lengthSquared() < 1e-6F) df.set(du);
            hinge.rotationTo(du, df.normalize());
            matrix = pose.pose();
            normalMatrix = pose.normal();
        }

        /** Рисует часть (с потомками) согнутой; её собственная поза на время отрисовки обнуляется. */
        void draw(ModelPart part, VertexConsumer to, int light, int overlay, int color) {
            PartPose saved = part.storePose();
            boolean visible = part.visible;
            part.setPos(0, 0, 0);
            part.setRotation(0, 0, 0);
            part.visible = true;
            out = to;
            n = 0;
            part.render(STRAIGHT, this, light, overlay, color);
            part.loadPose(saved);
            part.visible = visible;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
            float[] q = quad[n++];
            q[0] = x * 16;
            q[1] = y * 16;
            q[2] = z * 16;
            q[3] = u;
            q[4] = v;
            q[5] = nx;
            q[6] = ny;
            q[7] = nz;
            this.color = color;
            this.overlay = overlay;
            this.light = light;
            if (n == 4) {
                n = 0;
                cut();
            }
        }

        /** Грань, которая заходит на сгиб, режется поперёк руки на полоски по уровням сгиба. */
        private void cut() {
            float[] q0 = quad[0], q1 = quad[1], q2 = quad[2], q3 = quad[3];
            // Вдоль какой пары рёбер грань тянется по руке: 0→3 и 1→2 или 0→1 и 3→2.
            float ya0 = (q0[1] + q1[1]) / 2, ya1 = (q3[1] + q2[1]) / 2, yb0 = (q0[1] + q3[1]) / 2, yb1 = (q1[1] + q2[1]) / 2;
            boolean alongA = Math.abs(ya1 - ya0) >= Math.abs(yb1 - yb0);
            float y0 = alongA ? ya0 : yb0, y1 = alongA ? ya1 : yb1;
            int count = 0;
            cuts[count++] = 0;
            if (Math.abs(y1 - y0) > 1e-4F)
                for (int k = 0; k <= STRIPS; k++) {
                    float t = (UPPER - BAND + k * 2 * BAND / STRIPS - y0) / (y1 - y0);
                    if (t > 1e-4F && t < 1 - 1e-4F) cuts[count++] = t;
                }
            cuts[count++] = 1;
            java.util.Arrays.sort(cuts, 0, count);
            for (int i = 0; i + 1 < count; i++) {
                float t0 = cuts[i], t1 = cuts[i + 1];
                if (alongA) {
                    lerp(a, q0, q3, t0);
                    lerp(b, q1, q2, t0);
                    lerp(c, q1, q2, t1);
                    lerp(d, q0, q3, t1);
                } else {
                    lerp(a, q0, q1, t0);
                    lerp(b, q0, q1, t1);
                    lerp(c, q3, q2, t1);
                    lerp(d, q3, q2, t0);
                }
                emit(a);
                emit(b);
                emit(c);
                emit(d);
            }
        }

        private static void lerp(float[] dst, float[] p, float[] q, float t) {
            for (int i = 0; i < 8; i++) dst[i] = p[i] + (q[i] - p[i]) * t;
        }

        /** Вершина прямой руки на согнутой: поворот плеча, на сгибе — плавно доворачивается до предплечья. */
        private void emit(float[] v) {
            float t = Mth.clamp((v[1] - (UPPER - BAND)) / (2 * BAND), 0, 1);
            rot.identity().slerp(hinge, t * t * (3 - 2 * t)).mul(upper);
            rot.transform(tmp.set(v[0], v[1] - UPPER, v[2])).add(elbow).div(16);
            matrix.transformPosition(tmp);
            normalMatrix.transform(rot.transform(nrm.set(v[5], v[6], v[7])));
            out.addVertex(tmp.x, tmp.y, tmp.z, color, v[3], v[4], overlay, light, nrm.x, nrm.y, nrm.z);
        }

        // Части модели передают вершины только целиком (addVertex выше), по отдельности их не бывает.
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            throw new UnsupportedOperationException();
        }
    }
}
