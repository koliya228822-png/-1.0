package com.heroicpaths.entity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.level.Level;

/**
 * Замороженный лучник Ледяной цитадели: скелет, вмёрзший в лёд. Стреляет из лука замедляющими стрелами,
 * как зимогор, но крепче; своих не ранит.
 */
public class FrozenArcher extends Stray {
    public FrozenArcher(EntityType<? extends FrozenArcher> type, Level level) {
        super(type, level);
        this.xpReward = 9;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSkeleton.createAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.ARMOR, 3.0)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (DungeonBoss.ignoresDamage(source)) return false;
        return super.hurt(source, amount);
    }
}
