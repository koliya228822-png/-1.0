package com.heroicpaths.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Отвалившаяся часть босса — рука или кусок лат Глиняного колосса, осколок ледяных доспехов Лича. Только зрелище:
 * падает, кувыркаясь, лежит на полу арены и через несколько секунд рассыпается.
 */
public class ClayDebris extends Entity {
    /** Чья это часть: модель колосса или Лича. */
    public static final int COLOSSUS = 0, LICH = 1;
    /** Части, которые могут отвалиться (имена частей модели), для каждой модели. */
    public static final String[][] PARTS = {
            {"arm_lower_left", "arm_lower_right", "chest_plate", "pauldron_left", "pauldron_right",
                    "arm_mid_left", "arm_mid_right", "helmet", "belt_plate"},
            {"pauldron_left", "pauldron_right", "crown", "chest_armor", "belt"}};
    public static final int LIFE = 160;
    private static final EntityDataAccessor<Integer> MODEL = SynchedEntityData.defineId(ClayDebris.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PART = SynchedEntityData.defineId(ClayDebris.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SPIN = SynchedEntityData.defineId(ClayDebris.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> LANDED = SynchedEntityData.defineId(ClayDebris.class, EntityDataSerializers.BOOLEAN);

    public ClayDebris(EntityType<? extends ClayDebris> type, Level level) {
        super(type, level);
    }

    public static int partIndex(int model, String name) {
        for (int i = 0; i < PARTS[model].length; i++) if (PARTS[model][i].equals(name)) return i;
        return 0;
    }

    public void setup(int model, String part, float yaw, Vec3 velocity) {
        entityData.set(MODEL, model);
        entityData.set(PART, partIndex(model, part));
        entityData.set(SPIN, (random.nextFloat() - 0.5f) * 30f);
        setYRot(yaw);
        setDeltaMovement(velocity);
    }

    public int model() {
        return Mth.clamp(entityData.get(MODEL), 0, PARTS.length - 1);
    }

    public String part() {
        String[] parts = PARTS[model()];
        return parts[Mth.clamp(entityData.get(PART), 0, parts.length - 1)];
    }

    /** Скорость кувыркания в градусах за тик (замирает на земле). */
    public float spin() {
        return entityData.get(SPIN);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MODEL, COLOSSUS);
        builder.define(PART, 0);
        builder.define(SPIN, 0f);
        builder.define(LANDED, false);
    }

    /** Только на клиенте: тик, когда обломок упал (−1 — ещё летит). Дальше он лежит неподвижно, без кувыркания. */
    public int landedTick = -1;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            // Падение считает сервер; клиент только плавно следует за ним, иначе обломок дёргается на полу.
            if (lerpSteps > 0) {
                double x = getX() + (lerpX - getX()) / lerpSteps, y = getY() + (lerpY - getY()) / lerpSteps, z = getZ() + (lerpZ - getZ()) / lerpSteps;
                lerpSteps--;
                setPos(x, y, z);
            }
            if (landedTick < 0 && onGroundSynced()) landedTick = tickCount;
            return;
        }
        if (!onGround()) setDeltaMovement(getDeltaMovement().add(0, -0.06, 0));
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().multiply(onGround() ? 0.6 : 0.98, 0.98, onGround() ? 0.6 : 0.98));
        entityData.set(LANDED, onGround() || entityData.get(LANDED));
        if (tickCount > LIFE) discard();
    }

    private int lerpSteps;
    private double lerpX, lerpY, lerpZ;

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpSteps = Math.max(1, steps);
    }

    private boolean onGroundSynced() {
        return entityData.get(LANDED);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
