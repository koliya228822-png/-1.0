package com.heroicpaths.entity;

import com.heroicpaths.fx.Fx;
import com.heroicpaths.fx.Palette;
import com.heroicpaths.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Кладка Матери выводка: три яйца на подстилке из паутины. Не двигается; если не разбить её за 5 секунд,
 * из неё вылупляется паук выводка (каждый третий раз — плевальщик).
 */
public class SpiderEggSac extends Monster {
    public static final int HATCH_TICKS = 100;
    private int age;

    public SpiderEggSac(EntityType<? extends SpiderEggSac> type, Level level) {
        super(type, level);
        this.xpReward = 1;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void registerGoals() {}

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        if (!(level() instanceof ServerLevel level)) return;
        age++;
        if (age > HATCH_TICKS - 30 && age % 6 == 0) {
            Fx.embers(level, Palette.POISON, position().add(0, 0.4, 0), 3, 0.3, 0.02);
        }
        if (age >= HATCH_TICKS) hatch(level);
    }

    private void hatch(ServerLevel level) {
        EntityType<? extends Mob> type = (getId() % 3 == 0 ? ModEntities.VENOM_SPITTER : ModEntities.BROOD_SPIDER).get();
        Mob spider = type.create(level);
        if (spider != null) {
            spider.moveTo(getX(), getY(), getZ(), random.nextFloat() * 360f, 0);
            spider.finalizeSpawn(level, level.getCurrentDifficultyAt(blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            spider.addTag(BossController.MINION_TAG);
            for (EquipmentSlot slot : EquipmentSlot.values()) spider.setDropChance(slot, 0f);
            Player near = level.getNearestPlayer(this, 24);
            if (near != null && !near.isCreative() && !near.isSpectator()) spider.setTarget(near);
            level.addFreshEntity(spider);
        }
        Fx.burst(level, Palette.POISON, position().add(0, 0.4, 0), 0.6);
        level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.3, getZ(), 20, 0.3, 0.2, 0.3, 0.1);
        playSound(SoundEvents.TURTLE_EGG_HATCH, 1.2f, 0.8f);
        discard();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (DungeonBoss.ignoresDamage(source)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 0.3, getZ(), 25, 0.3, 0.2, 0.3, 0.15);
            Fx.burst(level, Palette.POISON, position().add(0, 0.3, 0), 0.4);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {}

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.TURTLE_EGG_CRACK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.TURTLE_EGG_BREAK;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("HatchAge", age);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        age = tag.getInt("HatchAge");
    }
}
