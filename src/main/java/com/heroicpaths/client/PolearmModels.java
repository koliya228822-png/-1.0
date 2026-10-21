package com.heroicpaths.client;

import com.heroicpaths.combat.WeaponType;
import com.heroicpaths.item.MeleeWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Копьё и боевой молот от третьего лица: в стойке древко стоит на полу, при ударе оружие в руках для выпада или замаха
 * (положения из моделей item/&lt;имя&gt; и item/&lt;имя&gt;_attack). Между ними оружие переходит плавно, по ходу удара,
 * а не скачком в начале и в конце: обёртка модели смешивает оба положения, и рукоять всё время остаётся в кулаке.
 */
public final class PolearmModels {
    private PolearmModels() {}

    private static List<ResourceLocation> polearms() {
        List<ResourceLocation> ids = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM)
            if (item instanceof MeleeWeaponItem weapon && weapon.isTwoHanded() && weapon.weaponType() != WeaponType.SLASHING)
                ids.add(BuiltInRegistries.ITEM.getKey(item));
        return ids;
    }

    private static ModelResourceLocation attackModel(ResourceLocation id) {
        return ModelResourceLocation.standalone(id.withPrefix("item/").withSuffix("_attack"));
    }

    public static void registerAdditional(ModelEvent.RegisterAdditional event) {
        for (ResourceLocation id : polearms()) event.register(attackModel(id));
    }

    public static void modifyBaking(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (ResourceLocation id : polearms()) {
            ModelResourceLocation key = ModelResourceLocation.inventory(id);
            BakedModel stance = models.get(key), attack = models.get(attackModel(id));
            if (stance != null && attack != null) models.put(key, new Smooth(stance, attack.getTransforms()));
        }
    }

    static final class Smooth extends BakedModelWrapper<BakedModel> {
        private final ItemTransforms attack;
        /** Насколько оружие сейчас в положении удара — запоминается при выборе модели, прямо перед отрисовкой. */
        private float weight;
        private final ItemOverrides overrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                weight = entity != null && entity.getMainHandItem() == stack
                        ? WeaponPoses.attackWeight(entity.getAttackAnim(Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false))) : 0;
                return Smooth.this;
            }
        };

        Smooth(BakedModel stance, ItemTransforms attack) {
            super(stance);
            this.attack = attack;
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            if (weight <= 0 || context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                return super.applyTransform(context, pose, leftHand);
            ItemTransforms own = originalModel.getTransforms();
            // От первого лица модель двуручного оружия лишь сдвигает точку хвата в начало координат — оттуда она и берётся.
            float grip = -own.firstPersonRightHand.translation.y() * 16;
            blend(own.getTransform(context), attack.getTransform(context), weight, grip, leftHand, pose);
            return this;
        }
    }

    private static Quaternionf rotation(ItemTransform t, boolean left) {
        float y = t.rotation.y(), z = t.rotation.z();
        if (left) {
            y = -y;
            z = -z;
        }
        return new Quaternionf().rotationXYZ(t.rotation.x() * Mth.DEG_TO_RAD, y * Mth.DEG_TO_RAD, z * Mth.DEG_TO_RAD);
    }

    /** Куда при этом положении попадает точка хвата. */
    private static Vector3f anchor(ItemTransform t, Quaternionf r, float grip, boolean left) {
        return r.transform(new Vector3f(0, grip * t.scale.y() / 16, 0)).add(left ? -t.translation.x() : t.translation.x(), t.translation.y(), t.translation.z());
    }

    /** Промежуточное положение: поворот и масштаб смешиваются, точка хвата идёт от одного положения кулака к другому. */
    private static void blend(ItemTransform a, ItemTransform b, float w, float grip, boolean left, PoseStack pose) {
        Quaternionf ra = rotation(a, left), rb = rotation(b, left), r = new Quaternionf(ra).slerp(rb, w);
        float s = Mth.lerp(w, a.scale.y(), b.scale.y());
        Vector3f g = anchor(a, ra, grip, left).lerp(anchor(b, rb, grip, left), w);
        Vector3f t = g.sub(r.transform(new Vector3f(0, grip * s / 16, 0)));
        pose.translate(t.x, t.y, t.z);
        pose.mulPose(r);
        pose.scale(s, s, s);
    }
}
