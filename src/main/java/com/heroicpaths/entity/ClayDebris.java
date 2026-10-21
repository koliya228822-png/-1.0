package com.heroicpaths.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Отвалившаяся часть Глиняного колосса — рука или кусок лат. Только зрелище: падает, кувыркаясь,
 * лежит на полу арены и через несколько секунд рассыпается.
 */
public class ClayDebris extends Entity {
    /** Части колосса, которые могут отвалиться (имена частей модели). */
    public static final String[] PARTS = {"arm_lower_left", "arm_lower_right", "chest_plate", "pauldron_left", "pauldron_right",
            "arm_mid_left", "arm_mid_right", "helmet", "belt_plate"};
    public static final int LIFE = 160;
    private static final EntityDataAccessor<Integer> PART = SynchedEntityData.defineId(ClayDebris.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SPIN = SynchedEntityData.defineId(ClayDebris.class, EntityDataSerializers.FLOAT);

    public ClayDebris(EntityType<? extends ClayDebris> type, Level level) {
        super(type, level);
    }

    public static int partIndex(String name) {
        for (int i = 0; i < PARTS.length; i++) if (PARTS[i].equals(name)) return i;
        return 0;
    }

    public void setup(String part, float yaw, Vec3 velocity) {
        entityData.set(PART, partIndex(part));
        entityData.set(SPIN, (random.nextFloat() - 0.5f) * 30f);
        setYRot(yaw);
        setDeltaMovement(velocity);
    }

    public String part() {
        return PARTS[Math.max(0, Math.min(PARTS.length - 1, entityData.get(PART)))];
    }

    /** Скорость кувыркания в градусах за тик (замирает на земле). */
    public float spin() {
        return entityData.get(SPIN);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PART, 0);
        builder.define(SPIN, 0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!onGround()) setDeltaMovement(getDeltaMovement().add(0, -0.06, 0));
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().multiply(onGround() ? 0.6 : 0.98, 0.98, onGround() ? 0.6 : 0.98));
        if (!level().isClientSide && tickCount > LIFE) discard();
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
