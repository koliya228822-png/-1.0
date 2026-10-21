package com.heroicpaths.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Королева пауков — босс Паучьего логова. Паук вдвое больше обычного: зовёт пещерных пауков
 * (в ярости — больших), опутывает ядовитой паутиной вблизи и плюётся ядом издалека.
 * В отличие от обычных пауков нападает и на свету.
 */
public class SpiderQueen extends Spider implements DungeonBoss, BossController.Abilities {
    private final BossController controller = new BossController(this, "spider_queen", BossEvent.BossBarColor.PURPLE);

    public SpiderQueen(EntityType<? extends SpiderQueen> type, Level level) {
        super(type, level);
        this.xpReward = 120;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 260.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    public BossController controller() {
        return controller;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(3, new LeapAtTargetGoal(this, 0.45f));
        goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12f));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        controller.tick(this);
    }

    @Override
    public void summon() {
        if (controller.isEnraged()) controller.summon(EntityType.SPIDER, 2, 5, mob -> {});
        else controller.summon(EntityType.CAVE_SPIDER, 3, 7, mob -> {});
    }

    /** Ядовитая паутина: урон, яд и сильное замедление всем рядом. */
    @Override
    public void nova() {
        for (Player player : controller.playersInRadius(6)) {
            player.hurt(damageSources().indirectMagic(this, this), 6f);
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
        }
        if (level() instanceof ServerLevel level) {
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2 * i / 24;
                level.sendParticles(ParticleTypes.SNEEZE, getX() + Math.cos(angle) * 4, getY() + 0.3, getZ() + Math.sin(angle) * 4,
                        3, 0.3, 0.1, 0.3, 0.01);
            }
        }
        playSound(SoundEvents.SPIDER_AMBIENT, 2f, 0.5f);
    }

    /** Веер из трёх ядовитых плевков. */
    @Override
    public void bolt(LivingEntity target) {
        for (int i = -1; i <= 1; i++) {
            SpellProjectile spit = new SpellProjectile(level(), this, 5f, SpellProjectile.Kind.POISON);
            Vec3 dir = new Vec3(target.getX() - getX(), target.getY(0.5) - spit.getY(), target.getZ() - getZ())
                    .yRot((float) Math.toRadians(i * 12));
            spit.shoot(dir.x, dir.y, dir.z, 1.2f, 1f);
            level().addFreshEntity(spit);
        }
        playSound(SoundEvents.LLAMA_SPIT, 1.5f, 0.6f);
    }

    /** Свои пауки не ранят королеву. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (DungeonBoss.ignoresDamage(source)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide) controller.onDeath();
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        controller.bossEvent().removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        controller.bossEvent().removeAllPlayers();
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        controller.bossEvent().setName(getDisplayName());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        controller.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        controller.load(tag);
    }
}
