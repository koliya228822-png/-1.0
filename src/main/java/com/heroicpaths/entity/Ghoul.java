package com.heroicpaths.entity;

import com.heroicpaths.registry.ModEffects;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * Вурдалак — быстрая нежить ближнего боя: бегает на четвереньках, часто полосует когтями (кровотечение)
 * и, убив кого-то, пожирает труп — восстанавливает здоровье.
 */
public class Ghoul extends ClayGolem {
    public Ghoul(EntityType<? extends Ghoul> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.ARMOR, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.34)
                .add(Attributes.FOLLOW_RANGE, 26.0);
    }

    @Override
    protected void registerAttackGoals() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false) {
            @Override
            protected int getAttackInterval() {
                return adjustedTickDelay(12);
            }
        });
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(ModEffects.BLEEDING, 60, 0), this);
            if (!living.isAlive()) heal(getMaxHealth() * 0.4f);
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.HUSK_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.HUSK_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.HUSK_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.3f;
    }
}
