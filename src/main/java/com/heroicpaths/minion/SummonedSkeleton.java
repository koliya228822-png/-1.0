package com.heroicpaths.minion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.level.Level;

/** Скелет-воин призывателя: служит хозяину и не горит на солнце. */
public class SummonedSkeleton extends Skeleton implements Minion {
    private final MinionData data = new MinionData();

    public SummonedSkeleton(EntityType<? extends Skeleton> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    @Override
    public MinionData minionData() {
        return data;
    }

    @Override
    protected void registerGoals() {
        // Оружейные цели (меч или лук) скелет добавляет сам, когда получает оружие.
        MinionGoals.install(this, 1.0, false);
    }

    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) Minions.tick(this);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity attacker && Minions.isFriendly(this, attacker)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !Minions.isFriendly(this, target) && super.canAttack(target);
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        data.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        data.load(tag);
    }
}
