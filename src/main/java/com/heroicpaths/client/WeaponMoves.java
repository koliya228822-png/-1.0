package com.heroicpaths.client;

import com.heroicpaths.item.DaggerItem;
import com.heroicpaths.item.FocusItem;
import com.heroicpaths.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
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
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Удары длинным мечом и парными кинжалами: в покое — своя стойка, каждый удар — случайный из своих (у меча три, у кинжалов четыре)
 * (тот же два раза подряд не выпадает).
 * <ul>
 *   <li>Длинный меч (стальной и дамасский) — в одной руке клинком вверх. Удары: вторая рука ложится на рукоять и широкий
 *       горизонтальный взмах справа налево; меч обеими руками над головой и удар сверху вниз; обычный косой удар одной рукой.</li>
 *   <li>Кинжалы парные: взятый в основную руку кинжал держат вместе со вторым таким же (вторая рука закрыта, второй кинжал
 *       рисуется здесь). Удары: укол одним кинжалом; оба кинжала над головой и удар сверху вниз; серия «одной рукой, другой, обеими»; удар с разворотом
 *       на месте — разворот виден от третьего лица, от первого лица это широкий взмах.</li>
 *   <li>Посох — в покое стоит вертикально, удары одной рукой размашисто: из-за спины снизу вперёд, выпад вперёд, сильный
 *       удар из-за левого плеча. Пока посох заряжает стрелу (ПКМ), он в позе заряда из WeaponPoses.</li>
 * </ul>
 * Выбор удара каждый клиент делает сам по взмахам (их рассылает игра) с зерном из существа и игрового времени,
 * поэтому другие игроки обычно видят тот же удар без своих пакетов.
 * <p>
 * Положения задаются в пространстве модели игрока (x — влево от персонажа, y — вниз, z — назад) для правши, левше — зеркально.
 * От третьего лица обёртка модели оружия ставит клинок так, чтобы при любом положении руки он смотрел куда нужно,
 * а рукоять была в кулаке. От первого лица то же движение переводится в пространство камеры.
 */
public final class WeaponMoves {
    private WeaponMoves() {}

    /** Длительность одного удара (тиков). */
    static final float DURATION = 10;
    /** Серия кинжалами — три укола подряд, поэтому дольше. */
    static final int FLURRY = 3;
    static final float FLURRY_DURATION = 18;
    /** Новый удар засчитывается, только когда предыдущий почти закончен — иначе удержание кнопки при копании дёргает удары. */
    static final float RESTART_AT = 0.7F;

    enum Style {
        LONGSWORD, DAGGERS, STAFF
    }

    @Nullable
    static Style style(ItemStack stack) {
        if (stack.is(ModItems.STEEL_SWORD.get()) || stack.is(ModItems.DAMASCUS_SWORD.get())) return Style.LONGSWORD;
        if (stack.getItem() instanceof FocusItem focus && focus.isStaff()) return Style.STAFF;
        return stack.getItem() instanceof DaggerItem ? Style.DAGGERS : null;
    }

    public static boolean is(ItemStack stack) {
        return style(stack) != null;
    }

    static Item[] items() {
        return BuiltInRegistries.ITEM.stream().filter(item -> is(new ItemStack(item))).toArray(Item[]::new);
    }

    /** Посох сейчас заряжает магическую стрелу (ПКМ) — тогда он в позе заряда, а не в стойке для ударов. */
    static boolean channeling(LivingEntity entity, ItemStack stack) {
        return entity.isUsingItem() && entity.getUseItem() == stack;
    }

    // ---------------------------------------------------------------- выбор удара

    static final class State {
        int step = -1, start = -1000;
        float duration = DURATION;
        /** Откуда начался текущий удар: прошлый удар и его ход в тот момент (fromP < 0 — из стойки). */
        int fromStep;
        float fromP = -1;
    }

    private static final Map<LivingEntity, State> STATES = new WeakHashMap<>();

    /** Каждый тик: начался ли у существа новый взмах основной рукой. */
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
        Style style = style(entity.getMainHandItem());
        int moves = moves(style), last = s.step < moves ? s.step : -1;
        int roll = (int) Math.floorMod(seed ^ (seed >>> 29), last >= 0 ? moves - 1L : moves);
        s.step = last >= 0 ? (last + 1 + roll) % moves : roll;
        s.start = entity.tickCount;
        s.duration = duration(style, s.step);
    }

    /** Сколько видов ударов: у кинжалов ещё серия «одной рукой, другой, обеими». */
    static int moves(Style style) {
        return style == Style.DAGGERS ? 4 : 3;
    }

    static float duration(Style style, int step) {
        return style == Style.DAGGERS && step == FLURRY ? FLURRY_DURATION : DURATION;
    }

    private static float progress(State s, int tick, float partial) {
        return (tick - s.start + partial) / s.duration;
    }

    private static float partial() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    // ---------------------------------------------------------------- положения

    private static final Vector3f UP = new Vector3f(0, -1, 0), FWD = new Vector3f(0, 0, -1), LEFT = new Vector3f(1, 0, 0);
    /** Плечи правой и левой руки (в пикселях модели игрока). */
    private static final Vector3f R_SHOULDER = new Vector3f(-5, 2, 0), L_SHOULDER = new Vector3f(5, 2, 0);

    /**
     * Положение: куда смотрит клинок в основной руке и во второй (+y модели оружия — к острию, +z — плашмя), где кулаки,
     * насколько вторая рука участвует (0 — висит как обычно) и на сколько градусов развёрнут весь персонаж.
     */
    record Pose(Quaternionf blade, Quaternionf offBlade, Vector3f main, Vector3f off, float offW, float spin, float turn) {
        Pose lerp(Pose o, float k) {
            return new Pose(new Quaternionf(blade).slerp(o.blade, k), new Quaternionf(offBlade).slerp(o.offBlade, k),
                    new Vector3f(main).lerp(o.main, k), new Vector3f(off).lerp(o.off, k), Mth.lerp(k, offW, o.offW), Mth.lerp(k, spin, o.spin),
                    Mth.lerp(k, turn, o.turn));
        }
    }

    private static Vector3f n(float x, float y, float z) {
        return new Vector3f(x, y, z).normalize();
    }

    /** Поворот модели оружия: остриё в dir, плоскость клинка перпендикулярна flat. */
    static Quaternionf blade(Vector3f dir, Vector3f flat) {
        Vector3f y = new Vector3f(dir).normalize(), z = new Vector3f(flat).sub(new Vector3f(y).mul(flat.dot(y))).normalize();
        Vector3f x = new Vector3f(y).cross(z);
        return new Quaternionf().setFromNormalized(new Matrix3f(x, y, z));
    }

    private static Vector3f arm(Vector3f shoulder, Vector3f dir, float length) {
        return new Vector3f(shoulder).add(new Vector3f(dir).normalize().mul(length));
    }

    static Pose idle(Style style) {
        if (style == Style.DAGGERS) {
            // Оба кинжала перед собой остриём вперёд-вверх, руки чуть вперёд.
            Quaternionf b = blade(n(0, -0.55F, -0.85F), LEFT);
            return new Pose(b, new Quaternionf(b), arm(R_SHOULDER, n(0.1F, 0.9F, -0.45F), 10), arm(L_SHOULDER, n(-0.1F, 0.9F, -0.45F), 10), 1, 0, 0);
        }
        // Меч в правой руке клинком вверх, чуть вперёд; лезвия смотрят вперёд и назад. Посох стоит вертикально.
        Vector3f main = arm(R_SHOULDER, n(0, 0.94F, -0.34F), 10);
        Quaternionf b = blade(style == Style.STAFF ? n(0, -1, 0) : n(0, -1, -0.15F), LEFT);
        return new Pose(b, new Quaternionf(b), main, new Vector3f(main), 0, 0, 0);
    }

    /** Взмах: начальное направление клинка, ось поворота (по правилу правой руки) и угол в градусах. */
    private record Swing(Vector3f start, Vector3f axis, float degrees) {
        Vector3f dir(float t) {
            return new Quaternionf().rotationAxis(degrees * t * Mth.DEG_TO_RAD, axis).transform(new Vector3f(start));
        }
    }

    private static final Swing LONG_HORIZONTAL = new Swing(n(-0.95F, -0.1F, 0.3F), new Vector3f(UP), 195);
    private static final Swing OVERHEAD = new Swing(n(0, -0.77F, 0.64F), new Vector3f(LEFT), 150);
    private static final Swing DIAGONAL = diagonal(n(-0.5F, -0.8F, 0.25F), n(0.6F, 0.45F, -0.65F), 12);

    private static Swing diagonal(Vector3f a, Vector3f b, float extra) {
        Vector3f axis = new Vector3f(a).cross(b).normalize();
        return new Swing(a, axis, (float) Math.toDegrees(Math.acos(Mth.clamp(a.dot(b), -1, 1))) + extra);
    }

    /** Положение посреди взмаха: t от 0 (замах) до 1 (конец удара). */
    static Pose swing(Style style, int step, float t) {
        return switch (style) {
            case DAGGERS -> daggerSwing(step, t);
            case STAFF -> staffSwing(step, t);
            case LONGSWORD -> longswordSwing(step, t);
        };
    }

    // Посох — размашисто одной рукой: из-за спины снизу вперёд-вверх, сильный удар из-за левого плеча.
    private static final Swing STAFF_UNDERHAND = new Swing(n(0, 0.35F, 0.95F), new Vector3f(-1, 0, 0), 165);
    private static final Swing STAFF_LEFT_SHOULDER = diagonal(n(0.55F, -0.75F, 0.35F), n(-0.6F, 0.5F, -0.6F), 15);

    private static Pose staffSwing(int step, float t) {
        if (step == 1) {
            // Выпад: рука отводится назад и выбрасывается вперёд, посох навершием вперёд.
            Vector3f u = new Vector3f(n(0, 0.9F, 0.45F)).lerp(n(0.1F, 0.05F, -1), t);
            Quaternionf q = blade(new Vector3f(n(0, -0.3F, -0.95F)).lerp(n(0, 0.02F, -1), t), LEFT);
            Vector3f main = arm(R_SHOULDER, u, 10);
            return new Pose(q, q, main, new Vector3f(main), 0, 0, 0);
        }
        // Рука из-за спины вперёд или из-за левого плеча наискось вниз — рука идёт вслед за посохом.
        Swing sw = step == 0 ? STAFF_UNDERHAND : STAFF_LEFT_SHOULDER;
        Vector3f dir = sw.dir(t);
        Quaternionf q = blade(dir, sw.axis);
        Vector3f main = arm(R_SHOULDER, new Vector3f(dir).mul(step == 0 ? 0.75F : 0.6F).add(new Vector3f(FWD).mul(step == 0 ? 0.25F : 0.8F)), 10);
        return new Pose(q, q, main, new Vector3f(main), 0, 0, 0);
    }

    private static Pose longswordSwing(int step, float t) {
        if (step == 2) {
            // Косой удар справа сверху налево вниз одной рукой.
            Vector3f dir = DIAGONAL.dir(t);
            Quaternionf q = blade(dir, DIAGONAL.axis);
            Vector3f main = arm(R_SHOULDER, new Vector3f(dir).mul(0.6F).add(new Vector3f(FWD).mul(0.8F)), 10);
            return new Pose(q, q, main, new Vector3f(main), 0, 0, 0);
        }
        // Обеими руками: горизонтально справа-сзади через перед налево или из-за головы вперёд-вниз.
        Swing sw = step == 0 ? LONG_HORIZONTAL : OVERHEAD;
        Vector3f dir = sw.dir(t);
        Quaternionf q = blade(dir, sw.axis);
        // Кулаки перед грудью, сдвинуты в сторону клинка; вторая рука ниже по рукояти, ближе к навершию.
        Vector3f center = step == 0 ? new Vector3f(0, 7, -7) : new Vector3f(0, 4, -6);
        Vector3f main = center.add(new Vector3f(dir).mul(step == 0 ? 3 : 5));
        Vector3f off = new Vector3f(main).sub(new Vector3f(dir).mul(2.5F));
        return new Pose(q, q, main, off, 1, 0, 0);
    }

    private static Pose daggerSwing(int step, float t) {
        Pose idle = idle(Style.DAGGERS);
        if (step == 0) {
            // Укол одним кинжалом: рука отводится назад-вниз и выбрасывается вперёд, вторая остаётся в стойке.
            Vector3f u = new Vector3f(n(0, 0.95F, 0.35F)).lerp(n(0.15F, 0.05F, -1), t);
            Quaternionf q = blade(new Vector3f(n(0, -0.4F, -1)).lerp(n(0, 0.05F, -1), t), LEFT);
            return new Pose(q, idle.offBlade, arm(R_SHOULDER, u, 10), idle.off, 1, 0, 0);
        }
        if (step == 1) {
            // Оба кинжала над головой, удар сверху вниз обеими руками.
            Vector3f dir = OVERHEAD.dir(t);
            Quaternionf q = blade(dir, LEFT);
            Vector3f main = new Vector3f(-3, 4, -5).add(new Vector3f(dir).mul(5)), off = new Vector3f(3, 4, -5).add(new Vector3f(dir).mul(5));
            return new Pose(q, new Quaternionf(q), main, off, 1, 0, 0);
        }
        // Удар с разворотом: руки в стороны, кинжалы плашмя к земле, персонаж поворачивается кругом.
        Quaternionf q = blade(n(-0.25F, 0, -1), UP), qo = blade(n(0.25F, 0, 1), UP);
        return new Pose(q, qo, arm(R_SHOULDER, n(-1, 0.25F, -0.35F), 10), arm(L_SHOULDER, n(1, 0.25F, 0.35F), 10), 1, 360 * t, -20 + 198 * t);
    }

    /** Ход удара p (0..1): замах из прежнего положения, сам взмах, возврат в стойку. */
    static Pose at(Style style, int step, float p, @Nullable Pose from) {
        Pose idle = idle(style), start = from != null ? from : idle;
        if (step < 0 || p >= 1) return idle;
        if (style == Style.DAGGERS && step == FLURRY) return flurry(p, start);
        if (p < 0.3F) return start.lerp(swing(style, step, 0), ease(p / 0.3F));
        if (p < 0.62F) return swing(style, step, ease((p - 0.3F) / 0.32F));
        Pose end = swing(style, step, 1);
        // После разворота персонаж уже стоит как раньше (360°) — к стойке возвращаются только руки.
        if (end.spin != 0) end = new Pose(end.blade, end.offBlade, end.main, end.off, end.offW, 0, end.turn);
        return end.lerp(idle, ease((p - 0.62F) / 0.38F));
    }

    /** Серия кинжалами: укол основной рукой, потом второй, потом обеими сразу. */
    private static Pose flurry(float p, Pose start) {
        Pose idle = idle(Style.DAGGERS);
        if (p < 0.1F) start = start.lerp(idle, ease(p / 0.1F));
        else start = idle;
        // Каждый укол — треть серии: рука выбрасывается вперёд и возвращается.
        int hit = Math.min(2, (int) (p / 0.32F));
        float k = Mth.sin(Mth.clamp((p - hit * 0.32F) / 0.32F, 0, 1) * Mth.PI);
        boolean main = hit != 1, off = hit != 0;
        Quaternionf q = blade(n(0, 0.05F, -1), LEFT);
        Vector3f mainOut = arm(R_SHOULDER, n(0.15F, 0.05F, -1), 10), offOut = arm(L_SHOULDER, n(-0.15F, 0.05F, -1), 10);
        return new Pose(main ? new Quaternionf(start.blade).slerp(q, k) : start.blade, off ? new Quaternionf(start.offBlade).slerp(q, k) : start.offBlade,
                main ? new Vector3f(start.main).lerp(mainOut, k) : start.main, off ? new Vector3f(start.off).lerp(offOut, k) : start.off, 1, 0, 0);
    }

    private static float ease(float k) {
        k = Mth.clamp(k, 0, 1);
        return k * k * (3 - 2 * k);
    }

    /** Текущее положение для существа (для правши; левше — зеркально). */
    static Pose pose(LivingEntity entity, float partial) {
        Style style = style(entity.getMainHandItem());
        if (style == null) style = Style.LONGSWORD;
        State s = STATES.get(entity);
        if (s == null || s.step < 0) return idle(style);
        float p = progress(s, entity.tickCount, partial);
        Pose from = s.fromP >= 0 ? at(style, s.fromStep, s.fromP, null) : null;
        return at(style, s.step, p, from);
    }

    private static Vector3f mirror(Vector3f v, boolean left) {
        return left ? new Vector3f(-v.x, v.y, v.z) : v;
    }

    static Quaternionf mirror(Quaternionf q, boolean left) {
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

    /**
     * От первого лица разворот не виден (камера крутилась бы вместе с персонажем): вместо него рука с кинжалом
     * проходит широким взмахом справа налево.
     */
    private static Pose firstPerson(Pose p) {
        if (p.turn == 0) return p;
        Quaternionf r = new Quaternionf().rotationAxis(p.turn * Mth.DEG_TO_RAD, UP);
        Vector3f main = r.transform(new Vector3f(p.main).sub(0, 4, 0)).add(0, 4, 0);
        // Вторая рука, отведённая в сторону для разворота, от первого лица остаётся в стойке.
        Pose idle = idle(Style.DAGGERS);
        float k = Math.min(1, Math.abs(p.turn) / 5);
        return new Pose(new Quaternionf(r).mul(p.blade), new Quaternionf(p.offBlade).slerp(idle.offBlade, k), main,
                new Vector3f(p.off).lerp(idle.off, k), p.offW, 0, 0);
    }

    /** То же движение от первого лица для основной руки; второй кулак — в Hold.offAt. */
    static WeaponPoses.Hold hold(Pose p, Style style, float dip, int side) {
        p = firstPerson(p);
        WeaponPoses.Hold h = hold(p.main, idle(style).main, FP_IDLE, p.blade, dip, side);
        h.mainAt = style == Style.DAGGERS ? ONE_HAND_AT : Mth.lerp(p.offW, ONE_HAND_AT, MAIN_AT);
        h.offAt = OFF_AT;
        return h;
    }

    /** Второй кинжал от первого лица: в другой руке, зеркально основной. */
    static WeaponPoses.Hold offHold(Pose p, float dip, int side) {
        p = firstPerson(p);
        Vector3f base = new Vector3f(-FP_IDLE.x, FP_IDLE.y, FP_IDLE.z);
        WeaponPoses.Hold h = hold(p.off, idle(Style.DAGGERS).off, base, p.offBlade, dip, side);
        h.mainAt = ONE_HAND_AT;
        return h;
    }

    private static WeaponPoses.Hold hold(Vector3f point, Vector3f idlePoint, Vector3f base, Quaternionf blade, float dip, int side) {
        WeaponPoses.Hold h = new WeaponPoses.Hold();
        h.scale = 0.85F;
        // Вверх кулаки поднимаются меньше, чем у модели игрока, чтобы не закрывать полэкрана.
        Vector3f d = view(new Vector3f(point).sub(idlePoint)).mul(1.1F / 16, 0.35F / 16, 1F / 16);
        h.grip.set(base).add(d);
        h.grip.y -= dip;
        h.rot = new Quaternionf().rotationZ(Mth.PI).mul(blade);
        // Клинок, направленный от камеры, виден как точка: от первого лица он развёрнут сильнее поперёк взгляда
        // (кроме удара вниз — его и так видно над кулаками).
        Vector3f dir = h.rot.transform(new Vector3f(0, 1, 0)), flat = new Vector3f(dir.x, dir.y, dir.z * Mth.lerp(Mth.clamp(-dir.y / 0.5F, 0, 1), 0.45F, 1)).normalize();
        h.rot = new Quaternionf().rotationTo(dir, flat).mul(h.rot);
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

    private static final Map<LivingEntity, Boolean> SPINNING = new WeakHashMap<>();

    /** Разворот кинжалами: весь персонаж поворачивается кругом на месте (поворот снимается в onRenderPost). */
    public static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (style(entity.getMainHandItem()) != Style.DAGGERS) return;
        float spin = pose(entity, event.getPartialTick()).spin;
        if (spin == 0) return;
        event.getPoseStack().pushPose();
        // Правая рука идёт вперёд — правша крутится влево, левша — вправо.
        event.getPoseStack().mulPose(Axis.YP.rotationDegrees(entity.getMainArm() == HumanoidArm.RIGHT ? spin : -spin));
        SPINNING.put(entity, Boolean.TRUE);
    }

    public static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        if (SPINNING.remove(event.getEntity()) != null) event.getPoseStack().popPose();
    }

    public static void modifyBaking(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (Item item : items()) {
            ModelResourceLocation key = ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(item));
            BakedModel model = models.get(key);
            if (model != null) models.put(key, new Held(model));
        }
    }

    /** Второй кинжал от третьего лица — в другой руке игрока, как обычный предмет в руке. */
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins())
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new SecondDagger(renderer));
    }

    static final class SecondDagger extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        SecondDagger(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ItemStack stack = player.getMainHandItem();
            if (style(stack) != Style.DAGGERS || !player.getOffhandItem().isEmpty()) return;
            HumanoidArm arm = player.getMainArm().getOpposite();
            boolean left = arm == HumanoidArm.LEFT;
            pose.pushPose();
            getParentModel().translateToHand(arm, pose);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.translate((left ? -1 : 1) / 16F, 0.125F, -0.625F);
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, stack,
                    left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, left, pose, buffers, light);
            pose.popPose();
        }
    }

    /** Модель оружия, которая от третьего лица ставит клинок по положению удара, а рукоять — в кулак. */
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
            if (entity == null || channeling(entity, entity.getMainHandItem())
                    || context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                    || !(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) instanceof LivingEntityRenderer<?, ?> renderer)
                    || !(renderer.getModel() instanceof HumanoidModel<?> model))
                return super.applyTransform(context, pose, leftHand);
            ModelPart arm = leftHand ? model.leftArm : model.rightArm;
            // Слой предмета в руке уже повернул всё по руке и ещё на Rx(−90)·Ry(180) — снимаем это и ставим клинок как в позе.
            Quaternionf hand = new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).rotateX(-Mth.HALF_PI).rotateY(Mth.PI);
            Pose p = pose(entity, partial());
            boolean leftHanded = entity.getMainArm() == HumanoidArm.LEFT, mainArm = leftHand == leftHanded;
            Quaternionf want = mirror(mainArm ? p.blade : p.offBlade, leftHanded);
            pose.mulPose(hand.conjugate().mul(want));
            float s = 0.85F, grip = -originalModel.getTransforms().firstPersonRightHand.translation.y() * 16;
            pose.scale(s, s, s);
            pose.translate(0, -grip / 16, 0);
            return this;
        }
    }
}
