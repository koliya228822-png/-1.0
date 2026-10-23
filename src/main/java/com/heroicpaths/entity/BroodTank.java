package com.heroicpaths.entity;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * Паук-толстобрюх: огромное брюшко в пластинах панциря. Медленный, втрое крепче паука
 * выводка и гасит четверть любого урона; тяжёлый укус замедляет.
 */
public class BroodTank extends BroodSpider {
    public static final float ABSORB = 0.25f;

    public BroodTank(EntityType<? extends BroodTank> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 56.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.ATTACK_KNOCKBACK, 0.8)
                .add(Attributes.STEP_HEIGHT, 1.0)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected void registerAttackGoals() {
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, true));
    }

    @Override
    protected float poisonChance() {
        return 0.2f;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, amount * (1 - ABSORB));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            if (target instanceof LivingEntity living) living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
            playSound(SoundEvents.RAVAGER_ATTACK, 0.8f, 1.4f);
        }
        return hit;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 0.6f;
    }
}
