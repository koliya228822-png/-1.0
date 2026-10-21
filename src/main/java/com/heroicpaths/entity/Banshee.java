package com.heroicpaths.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Банши — призрачная нежить дальнего боя: парит над полом, бросает сгустки тьмы (иссушение) и время от времени
 * воет — все игроки рядом слабеют и замедляются.
 */
public class Banshee extends ClayThrower {
    private int wailCooldown = 100;

    public Banshee(EntityType<? extends Banshee> type, Level level) {
        super(type, level);
        this.xpReward = 9;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.ATTACK_DAMAGE, 4.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.FOLLOW_RANGE, 26.0);
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        SpellProjectile bolt = new SpellProjectile(level(), this, (float) getAttributeValue(Attributes.ATTACK_DAMAGE),
                SpellProjectile.Kind.SHADOW);
        bolt.setPos(getX(), getY() + getBbHeight() * 0.75, getZ());
        Vec3 dir = new Vec3(target.getX() - getX(), target.getY(0.5) - bolt.getY(), target.getZ() - getZ());
        bolt.shoot(dir.x, dir.y, dir.z, 1.1f, 4f);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        playSound(SoundEvents.VEX_CHARGE, 1f, 0.7f);
        level().addFreshEntity(bolt);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (getTarget() == null || --wailCooldown > 0) return;
        wailCooldown = 160 + random.nextInt(80);
        if (distanceToSqr(getTarget()) > 64) return;
        // Вой: слабость и замедление всем игрокам в 8 блоках.
        for (Player player : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(8),
                p -> !p.isSpectator() && !p.isCreative())) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0), this);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0), this);
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel level) {
            level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 1.5, getZ(), 20, 1.5, 0.8, 1.5, 0.02);
        }
        playSound(SoundEvents.GHAST_SCREAM, 1.5f, 1.6f);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VEX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {}
}
