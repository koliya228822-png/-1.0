package com.heroicpaths.entity;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * Голем-танк: медленный, в толстых обожжённых плитах, втрое крепче обычного и вдобавок
 * гасит четверть любого урона.
 */
public class ClayBrute extends ClayGolem {
    public static final float ABSORB = 0.25f;

    public ClayBrute(EntityType<? extends ClayBrute> type, Level level) {
        super(type, level);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 48.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.2)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return super.hurt(source, amount * (1 - ABSORB));
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) playSound(SoundEvents.MUD_BRICKS_PLACE, 1.2f, 0.5f);
        return hit;
    }
}
