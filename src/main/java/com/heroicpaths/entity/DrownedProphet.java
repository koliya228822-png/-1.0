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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Утопший пророк — босс Затопленного храма. Огромный утопленник с трезубцем: зовёт утопленников,
 * сбивает с ног приливной волной вблизи и бросает сгустки воды издалека. Не горит на солнце и нападает днём.
 */
public class DrownedProphet extends Drowned implements DungeonBoss, BossController.Abilities {
    private final BossController controller = new BossController(this, "drowned_prophet", BossEvent.BossBarColor.BLUE);

    public DrownedProphet(EntityType<? extends DrownedProphet> type, Level level) {
        super(type, level);
        this.xpReward = 180;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 420.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ATTACK_DAMAGE, 11.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
    }

    public BossController controller() {
        return controller;
    }

    /** Утопленники нападают на сушу только ночью; пророк — всегда. */
    @Override
    protected void addBehaviourGoals() {
        super.addBehaviourGoals();
        targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }

    @Override
    public void setBaby(boolean baby) {
        super.setBaby(false);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // Пророк всегда с трезубцем, даже если появился из шаблона без снаряжения.
        if (!getMainHandItem().is(Items.TRIDENT)) BossController.equip(this, EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        controller.tick(this);
    }

    @Override
    public void summon() {
        controller.summon(EntityType.DROWNED, controller.isEnraged() ? 3 : 2, 6, mob -> {
            if (mob.getRandom().nextBoolean()) BossController.equip(mob, EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
        });
    }

    /** Приливная волна: урон, отбрасывание и замедление всем рядом. */
    @Override
    public void nova() {
        for (Player player : controller.playersInRadius(7)) {
            player.hurt(damageSources().indirectMagic(this, this), 8f);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            Vec3 push = player.position().subtract(position()).normalize();
            player.knockback(1.4, -push.x, -push.z);
            player.hurtMarked = true;
        }
        if (level() instanceof ServerLevel level) {
            for (int i = 0; i < 32; i++) {
                double angle = Math.PI * 2 * i / 32;
                level.sendParticles(ParticleTypes.SPLASH, getX() + Math.cos(angle) * 4.5, getY() + 0.4, getZ() + Math.sin(angle) * 4.5,
                        6, 0.3, 0.2, 0.3, 0.1);
            }
        }
        playSound(SoundEvents.GENERIC_SPLASH, 2f, 0.5f);
        playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 1f, 1.2f);
    }

    /** Три сгустка воды веером. */
    @Override
    public void bolt(LivingEntity target) {
        for (int i = -1; i <= 1; i++) {
            SpellProjectile water = new SpellProjectile(level(), this, 7f, SpellProjectile.Kind.WATER);
            Vec3 dir = new Vec3(target.getX() - getX(), target.getY(0.5) - water.getY(), target.getZ() - getZ())
                    .yRot((float) Math.toRadians(i * 10));
            water.shoot(dir.x, dir.y, dir.z, 1.3f, 1f);
            level().addFreshEntity(water);
        }
        playSound(SoundEvents.DROWNED_SHOOT, 1.5f, 0.6f);
    }

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
