package com.heroicpaths.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Лоскут плоти — танк нежити (поганище Плети): огромный, сшитый из трупов, гасит четверть урона,
 * рубит тесаком и окружён чумным облаком — всех игроков вплотную раз в секунду отравляет.
 */
public class Abomination extends ClayBrute {
    public Abomination(EntityType<? extends Abomination> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.18)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0)
                .add(Attributes.FOLLOW_RANGE, 20.0);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 20 != 0 || !(level() instanceof ServerLevel level)) return;
        level.sendParticles(ParticleTypes.SNEEZE, getX(), getY() + 1.2, getZ(), 12, 1.2, 0.8, 1.2, 0.01);
        for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(2.5),
                p -> !p.isSpectator() && !p.isCreative())) {
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0), this);
        }
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ZOMBIE_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ZOMBIE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ZOMBIE_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.55f;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 0.6f, 0.8f);
    }
}
