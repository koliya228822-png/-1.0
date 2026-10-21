package com.heroicpaths.entity;

import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Чумной зомби — рядовая нежить Ледяной цитадели (как зомби Плети в Warcraft): медленный, крепкий,
 * его укус заражает чумой — слабость и голод. Не бывает детёнышем и не зовёт подкрепление.
 */
public class UndeadZombie extends Zombie {
    public UndeadZombie(EntityType<? extends UndeadZombie> type, Level level) {
        super(type, level);
        this.xpReward = 7;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.21)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0)
                .add(Attributes.FOLLOW_RANGE, 22.0);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, spawnType, data);
        setBaby(false);
        if (isPassenger()) stopRiding();
        return result;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), this);
            living.addEffect(new MobEffectInstance(MobEffects.HUNGER, 100, 0), this);
        }
        return hit;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (DungeonBoss.ignoresDamage(source)) return false;
        return super.hurt(source, amount);
    }

    @Override
    protected boolean convertsInWater() {
        return false;
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }
}
