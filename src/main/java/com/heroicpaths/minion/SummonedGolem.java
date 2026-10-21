package com.heroicpaths.minion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.Level;

/** Каменный страж призывателя: железный голем, который служит хозяину, а не деревне. */
public class SummonedGolem extends IronGolem implements Minion {
    private final MinionData data = new MinionData();

    public SummonedGolem(EntityType<? extends IronGolem> type, Level level) {
        super(type, level);
        setPlayerCreated(true);
    }

    @Override
    public MinionData minionData() {
        return data;
    }

    @Override
    protected void registerGoals() {
        MinionGoals.install(this, 0.9, true);
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
