package com.heroicpaths.entity;

import com.heroicpaths.registry.ModItems;
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
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Лич — босс Некрополя. Огромный скелет-иссушитель с посохом архимага: поднимает скелетов (в ярости —
 * скелетов-иссушителей), вблизи похищает жизнь у всех вокруг и лечится ею, издалека бьёт тёмными стрелами.
 */
public class Lich extends WitherSkeleton implements DungeonBoss, BossController.Abilities {
    private final BossController controller = new BossController(this, "lich", BossEvent.BossBarColor.WHITE);

    public Lich(EntityType<? extends Lich> type, Level level) {
        super(type, level);
        this.xpReward = 250;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractSkeleton.createAttributes()
                .add(Attributes.MAX_HEALTH, 560.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    public BossController controller() {
        return controller;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!getMainHandItem().is(ModItems.STORM_ARCHMAGE_STAFF.get())) {
            BossController.equip(this, EquipmentSlot.MAINHAND, new ItemStack(ModItems.STORM_ARCHMAGE_STAFF.get()));
        }
        controller.tick(this);
    }

    @Override
    public void summon() {
        if (controller.isEnraged()) controller.summon(EntityType.WITHER_SKELETON, 2, 5, mob -> {});
        else controller.summon(EntityType.SKELETON, 3, 7, mob -> {});
    }

    /** Похищение жизни: урон и иссушение всем рядом, половина нанесённого урона лечит лича. */
    @Override
    public void nova() {
        float drained = 0;
        for (Player player : controller.playersInRadius(7)) {
            float before = player.getHealth();
            player.hurt(damageSources().indirectMagic(this, this), 8f);
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
            drained += Math.max(0, before - player.getHealth());
        }
        if (drained > 0) heal(drained * 0.5f);
        if (level() instanceof ServerLevel level) {
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2 * i / 24;
                level.sendParticles(ParticleTypes.SOUL, getX() + Math.cos(angle) * 4.5, getY() + 0.5, getZ() + Math.sin(angle) * 4.5,
                        3, 0.2, 0.3, 0.2, 0.02);
            }
            level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 2, getZ(), 20, 0.5, 0.8, 0.5, 0.02);
        }
        playSound(SoundEvents.WITHER_AMBIENT, 1.5f, 0.7f);
    }

    /** Три тёмные стрелы веером. */
    @Override
    public void bolt(LivingEntity target) {
        for (int i = -1; i <= 1; i++) {
            SpellProjectile shadow = new SpellProjectile(level(), this, 8f, SpellProjectile.Kind.SHADOW);
            Vec3 dir = new Vec3(target.getX() - getX(), target.getY(0.5) - shadow.getY(), target.getZ() - getZ())
                    .yRot((float) Math.toRadians(i * 10));
            shadow.shoot(dir.x, dir.y, dir.z, 1.3f, 1f);
            level().addFreshEntity(shadow);
        }
        playSound(SoundEvents.WITHER_SHOOT, 1.2f, 0.8f);
    }

    /** Свои скелеты не ранят лича. */
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
