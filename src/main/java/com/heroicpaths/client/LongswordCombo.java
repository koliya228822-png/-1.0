package com.heroicpaths.client;

import com.heroicpaths.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Длинный меч (стальной и дамасский): в покое — в одной руке клинком вверх; каждый удар — случайный из трёх
 * (тот же два раза подряд не выпадает):
 * <ol>
 *   <li>вторая рука ложится на рукоять, широкий горизонтальный взмах справа налево;</li>
 *   <li>меч обеими руками над головой и удар сверху вниз;</li>
 *   <li>обычный косой удар одной рукой.</li>
 * </ol>
 * Выбор удара каждый клиент делает сам по взмахам (их рассылает игра) с зерном из существа и игрового времени,
 * поэтому другие игроки обычно видят тот же удар без своих пакетов.
 * <p>
 * Положение клинка задаётся в пространстве модели игрока (x — влево от персонажа, y — вниз, z — назад), и от третьего лица
 * обёртка модели меча ставит его так, чтобы при любом положении руки клинок смотрел куда нужно, а рукоять была в кулаке.
 * От первого лица то же движение переводится в пространство камеры (WeaponPoses.hold).
 */
public final class LongswordCombo {
    private LongswordCombo() {}

    /** Длительность одного удара (тиков). */
    static final float DURATION = 10;
    /** Новый удар засчитывается, только когда предыдущий почти закончен — иначе удержание кнопки при копании дёргает удары. */
    static final float RESTART_AT = 0.7F;

    public static boolean is(ItemStack stack) {
        return stack.is(ModItems.STEEL_SWORD.get()) || stack.is(ModItems.DAMASCUS_SWORD.get());
    }

    // ---------------------------------------------------------------- счёт ударов

    static final class State {
        int step = -1, start = -1000;
        /** Откуда начался текущий удар: прошлый удар и его ход в тот момент (fromP < 0 — из стойки). */
        int fromStep;
        float fromP = -1;
    }

    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();

    /** Каждый тик: начался ли у существа новый взмах основной рукой с длинным мечом. */
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide || !(event.getEntity() instanceof LivingEntity entity)) return;
        if (!entity.swinging || entity.swingTime != 0 || entity.swingingArm != InteractionHand.MAIN_HAND || !is(entity.getMainHandItem())) return;
        State s = STATES.computeIfAbsent(entity, e -> new State());
        float p = progress(s, entity.tickCount, 0);
        if (s.step >= 0 && p < RESTART_AT) return;
        s.fromStep = s.step;
        s.fromP = s.step >= 0 && p < 1 ? p : -1;
        // Случайный удар, но не тот же, что только что. Зерно — существо и игровое время, одинаковые у всех клиентов.
        long seed = entity.getUUID().getLeastSignificantBits() ^ entity.level().getGameTime() * 0x9E3779B97F4A7C15L;
        int roll = (int) Math.floorMod(seed ^ (seed >>> 29), s.step >= 0 ? 2L : 3L);
        s.step = s.step >= 0 ? (s.step + 1 + roll) % 3 : roll;
        s.start = entity.tickCount;
    }

    private static float progress(State s, int tick, float partial) {
        return (tick - s.start + partial) / DURATION;
    }

    private static float partial() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    // ---------------------------------------------------------------- положения

    private static final Vector3f UP = new Vector3f(0, -1, 0), FWD = new Vector3f(0, 0, -1), LEFT = new Vector3f(1, 0, 0);
    /** Плечи правой и левой руки, грудь (в пикселях модели игрока). */
    private static final Vector3f R_SHOULDER = new Vector3f(-5, 2, 0), L_SHOULDER = new Vector3f(5, 2, 0);

    /**
     * Положение для правши: куда смотрит клинок (blade: +y модели меча — к острию, +z — плашмя) и где кулаки.
     * off — насколько вторая рука на рукояти (0 — висит как обычно).
     */
    record Pose(Quaternionf blade, Vector3f main, Vector3f off, float offW) {
        Pose lerp(Pose o, float k) {
            return new Pose(new Quaternionf(blade).slerp(o.blade, k), new Vector3f(main).lerp(o.main, k),
                    new Vector3f(off).lerp(o.off, k), Mth.lerp(k, offW, o.offW));
        }
    }

    private static Vector3f n(float x, float y, float z) {
        return new Vector3f(x, y, z).normalize();
    }

    /** Поворот модели меча: остриё в dir, плоскость клинка перпендикулярна flat. */
    static Quaternionf blade(Vector3f dir, Vector3f flat) {
        Vector3f y = new Vector3f(dir).normalize(), z = new Vector3f(flat).sub(new Vector3f(y).mul(flat.dot(y))).normalize();
        Vector3f x = new Vector3f(y).cross(z);
        return new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
    }

    /** Стойка: меч в правой руке клинком вверх, чуть вперёд; лезвия смотрят вперёд и назад. */
    static Pose idle() {
        Vector3f arm = n(0, 0.94F, -0.34F);
        Vector3f main = new Vector3f(R_SHOULDER).add(arm.mul(10));
        return new Pose(blade(n(0, -1, -0.15F), LEFT), main, new Vector3f(main), 0);
    }

    /** Взмах: начальное направление клинка, ось поворота (по правилу правой руки) и угол в градусах. */
    private record Swing(Vector3f start, Vector3f axis, float degrees, boolean twoHands) {}

    private static final Swing[] SWINGS = {
            // 1: справа-сзади через перед налево, клинок плашмя к земле.
            new Swing(n(-0.95F, -0.1F, 0.3F), new Vector3f(UP), 195, true),
            // 2: из-за головы через верх вперёд-вниз.
            new Swing(n(0, -0.77F, 0.64F), new Vector3f(LEFT), 150, true),
            // 3: косой удар справа сверху налево вниз.
            diagonal(n(-0.5F, -0.8F, 0.25F), n(0.6F, 0.45F, -0.65F), 12)
    };

    private static Swing diagonal(Vector3f a, Vector3f b, float extra) {
        Vector3f axis = new Vector3f(a).cross(b).normalize();
        return new Swing(a, axis, (float) Math.toDegrees(Math.acos(Mth.clamp(a.dot(b), -1, 1))) + extra, false);
    }

    /** Положение посреди взмаха: t от 0 (замах) до 1 (конец удара). */
    static Pose swing(int step, float t) {
        Swing sw = SWINGS[step];
        Vector3f dir = new Quaternionf().rotationAxis(sw.degrees * t * Mth.DEG_TO_RAD, sw.axis).transform(new Vector3f(sw.start));
        Quaternionf q = blade(dir, sw.axis);
        if (sw.twoHands) {
            // Кулаки перед грудью, сдвинуты в сторону клинка; вторая рука ниже по рукояти, ближе к навершию.
            Vector3f center = step == 0 ? new Vector3f(0, 7, -7) : new Vector3f(0, 4, -6);
            Vector3f main = center.add(new Vector3f(dir).mul(step == 0 ? 3 : 5));
            Vector3f off = new Vector3f(main).sub(new Vector3f(dir).mul(2.5F));
            return new Pose(q, main, off, 1);
        }
        Vector3f arm = new Vector3f(dir).mul(0.6F).add(new Vector3f(FWD).mul(0.8F)).normalize().mul(10);
        Vector3f main = new Vector3f(R_SHOULDER).add(arm);
        return new Pose(q, main, new Vector3f(main), 0);
    }

    /** Ход удара p (0..1): замах из прежнего положения, сам взмах, возврат в стойку. */
    static Pose at(int step, float p, @Nullable Pose from) {
        Pose idle = idle(), start = from != null ? from : idle;
        if (step < 0 || p >= 1) return idle;
        if (p < 0.3F) return start.lerp(swing(step, 0), ease(p / 0.3F));
        if (p < 0.62F) return swing(step, ease((p - 0.3F) / 0.32F));
        return swing(step, 1).lerp(idle, ease((p - 0.62F) / 0.38F));
    }

    private static float ease(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    /** Текущее положение для существа (для правши; левше — зеркально). */
    static Pose pose(LivingEntity entity, float partial) {
        State s = STATES.get(entity);
        if (s == null || s.step < 0) return idle();
        float p = progress(s, entity.tickCount, partial);
        Pose from = s.fromP >= 0 ? at(s.fromStep, s.fromP, null) : null;
        return at(s.step, p, from);
    }

    private static Vector3f mirror(Vector3f v, boolean left) {
        return left ? new Vector3f(-v.x, v.y, v.z) : v;
    }

    private static Quaternionf mirror(Quaternionf q, boolean left) {
        return left ? new Quaternionf(q.x, -q.y, -q.z, q.w) : q;
    }

    /** Направить руку (вниз от плеча в покое) из плеча на точку. */
    private static void aim(ModelPart arm, Vector3f shoulder, Vector3f target) {
        Vector3f u = new Vector3f(target).sub(shoulder).normalize();
        float x = -(float) Math.acos(Mth.clamp(u.y, -1, 1)), sx = Mth.sin(x);
        arm.xRot = x;
        arm.yRot = Math.abs(sx) < 1e-3F ? 0 : (float) Mth.atan2(u.x / sx, u.z / sx);
        arm.zRot = 0;
    }

    // ---------------------------------------------------------------- первое лицо

    /** Где рукоять от первого лица в стойке (для правой руки), кулак движется так же, как у модели игрока. */
    private static final Vector3f FP_IDLE = new Vector3f(0.42F, -0.52F, -0.9F);
    /** Кулаки на рукояти в пикселях модели от точки хвата: одной рукой — посередине, двумя — у гарды и у навершия. */
    static final float ONE_HAND_AT = 0, MAIN_AT = 1.1F, OFF_AT = -1.7F;

    /** Модель игрока (x влево, y вниз) → камера (x вправо, y вверх); вперёд в обоих −z. */
    private static Vector3f view(Vector3f v) {
        return new Vector3f(-v.x, -v.y, v.z);
    }

    /** То же движение от первого лица; второй кулак — в Hold.offAt, насколько он на рукояти — в offW. */
    static WeaponPoses.Hold hold(Pose p, float dip, int side) {
        WeaponPoses.Hold h = new WeaponPoses.Hold();
        h.scale = 0.85F;
        // Вверх кулаки поднимаются меньше, чем у модели игрока, чтобы не закрывать полэкрана.
        Vector3f d = view(new Vector3f(p.main).sub(idle().main)).mul(1.1F / 16, 0.35F / 16, 1F / 16);
        h.grip.set(FP_IDLE).add(d);
        h.grip.y -= dip;
        h.rot = new Quaternionf().rotationZ(Mth.PI).mul(p.blade);
        // Клинок, направленный от камеры, виден как точка: от первого лица он развёрнут сильнее поперёк взгляда
        // (кроме удара вниз — его и так видно над кулаками).
        Vector3f dir = h.rot.transform(new Vector3f(0, 1, 0)), flat = new Vector3f(dir.x, dir.y, dir.z * Mth.lerp(Mth.clamp(-dir.y / 0.5F, 0, 1), 0.45F, 1)).normalize();
        h.rot = new Quaternionf().rotationTo(dir, flat).mul(h.rot);
        h.mainAt = Mth.lerp(p.offW, ONE_HAND_AT, MAIN_AT);
        h.offAt = OFF_AT;
        if (side < 0) {
            h.grip.x = -h.grip.x;
            h.rot = mirror(h.rot, true);
        }
        return h;
    }

    // ---------------------------------------------------------------- третье лицо

    /** Поза рук от третьего лица. */
    static void armPose(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        model.attackTime = 0;
        boolean left = arm == HumanoidArm.LEFT;
        ModelPart main = left ? model.leftArm : model.rightArm, off = left ? model.rightArm : model.leftArm;
        Pose p = pose(entity, partial());
        aim(main, mirror(R_SHOULDER, left), mirror(p.main, left));
        if (p.offW <= 0 || !entity.getOffhandItem().isEmpty()) return;
        float ox = off.xRot, oy = off.yRot, oz = off.zRot;
        aim(off, mirror(L_SHOULDER, left), mirror(p.off, left));
        off.xRot = Mth.lerp(p.offW, ox, off.xRot);
        off.yRot = Mth.lerp(p.offW, oy, off.yRot);
        off.zRot = Mth.lerp(p.offW, oz, off.zRot);
    }

    public static void modifyBaking(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (Item item : new Item[]{ModItems.STEEL_SWORD.get(), ModItems.DAMASCUS_SWORD.get()}) {
            ModelResourceLocation key = ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(item));
            BakedModel model = models.get(key);
            if (model != null) models.put(key, new Held(model));
        }
    }

    /** Модель меча, которая от третьего лица ставит клинок по положению из комбо, а рукоять — в кулак. */
    static final class Held extends BakedModelWrapper<BakedModel> {
        @Nullable
        private LivingEntity holder;
        private final ItemOverrides overrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                holder = entity != null && entity.getMainHandItem() == stack ? entity : null;
                return Held.this;
            }
        };

        Held(BakedModel model) {
            super(model);
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean leftHand) {
            LivingEntity entity = holder;
            if (entity == null || context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    || !(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer<?, ?> renderer)
                    || !(renderer.getModel() instanceof HumanoidModel<?> model))
                return super.applyTransform(context, pose, leftHand);
            ModelPart arm = leftHand ? model.leftArm : model.rightArm;
            // Слой предмета в руке уже повернул всё по руке и ещё на Rx(−90)·Ry(180) — снимаем это и ставим клинок как в позе.
            Quaternionf hand = new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).rotateX(-Mth.HALF_PI).rotateY(Mth.PI);
            Quaternionf want = mirror(pose(entity, partial()).blade, leftHand);
            pose.mulPose(hand.conjugate().mul(want));
            float s = 0.85F, grip = -originalModel.getTransforms().firstPersonRightHand.translation.y() * 16;
            pose.scale(s, s, s);
            pose.translate(0, -grip / 16, 0);
            return this;
        }
    }
}
