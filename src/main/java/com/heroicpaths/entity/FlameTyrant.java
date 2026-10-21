package com.heroicpaths.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Пламенный Тиран — босс Огненной кузни. Огромный ифрит.
 * Призывает ифритов (в ярости — магмовые кубы), бьёт огненной волной вблизи и веером огненных шаров издалека.
 */
public class FlameTyrant extends Blaze implements DungeonBoss, BossController.Abilities {
    private final BossController controller = new BossController(this, "flame_tyrant", BossEvent.BossBarColor.RED);

    public FlameTyrant(EntityType<? extends FlameTyrant> type, Level level) {
        super(type, level);
        this.xpReward = 150;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    public BossController controller() {
        return controller;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        controller.tick(this);
    }

    @Override
    public void summon() {
        if (controller.isEnraged()) {
            controller.summon(EntityType.MAGMA_CUBE, 2, 5, mob -> ((MagmaCube) mob).setSize(2, true));
        } else {
            controller.summon(EntityType.BLAZE, 2, 5, mob -> {});
        }
    }

    @Override
    public void nova() {
        for (Player player : controller.playersInRadius(6)) {
            player.hurt(damageSources().indirectMagic(this, this), 9f);
            player.igniteForSeconds(5);
            Vec3 push = player.position().subtract(position()).normalize();
            player.knockback(1.0, -push.x, -push.z);
            player.hurtMarked = true;
        }
        if (level() instanceof ServerLevel level) {
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2 * i / 24;
                level.sendParticles(ParticleTypes.FLAME, getX() + Math.cos(angle) * 4, getY() + 0.5, getZ() + Math.sin(angle) * 4,
                        4, 0.3, 0.2, 0.3, 0.02);
            }
        }
        playSound(SoundEvents.BLAZE_SHOOT, 2f, 0.5f);
    }

    @Override
    public void bolt(LivingEntity target) {
        for (int i = -1; i <= 1; i++) {
            SpellProjectile fire = new SpellProjectile(level(), this, 7f, SpellProjectile.Kind.FIRE);
            Vec3 dir = new Vec3(target.getX() - getX(), target.getY(0.5) - fire.getY(), target.getZ() - getZ())
                    .yRot((float) Math.toRadians(i * 10));
            fire.shoot(dir.x, dir.y, dir.z, 1.3f, 1f);
            level().addFreshEntity(fire);
        }
        playSound(SoundEvents.BLAZE_SHOOT, 1.5f, 0.7f);
    }

    /** Свои ифриты и магмовые кубы не ранят тирана. */
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
