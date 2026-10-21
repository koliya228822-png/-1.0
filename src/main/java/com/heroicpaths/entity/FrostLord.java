package com.heroicpaths.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Ледяной Лорд — босс Ледяных чертогов. Огромный зимогор в алмазной короне.
 * Стреляет замедляющими стрелами, призывает зимогоров, бьёт ледяной волной и ледяными копьями.
 */
public class FrostLord extends Stray implements DungeonBoss, BossController.Abilities {
    private final BossController controller = new BossController(this, "frost_lord", BossEvent.BossBarColor.BLUE);

    public FrostLord(EntityType<? extends FrostLord> type, Level level) {
        super(type, level);
        this.xpReward = 200;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 500.0)
                .add(Attributes.ARMOR, 12.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    public BossController controller() {
        return controller;
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        BossController.equip(this, EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        BossController.equip(this, EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        controller.tick(this);
    }

    @Override
    public void summon() {
        controller.summon(EntityType.STRAY, controller.isEnraged() ? 3 : 2, 6, mob -> {});
    }

    @Override
    public void nova() {
        for (Player player : controller.playersInRadius(6)) {
            player.hurt(damageSources().indirectMagic(this, this), 8f);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
            player.setTicksFrozen(Math.max(player.getTicksFrozen(), player.getTicksRequiredToFreeze() + 40));
        }
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, getX(), getY() + 1, getZ(), 150, 3, 0.4, 3, 0.05);
        }
        playSound(SoundEvents.GLASS_BREAK, 2f, 0.5f);
    }

    @Override
    public void bolt(LivingEntity target) {
        for (int i = -1; i <= 1; i++) {
            SpellProjectile lance = new SpellProjectile(level(), this, 7f, SpellProjectile.Kind.ICE);
            Vec3 dir = new Vec3(target.getX() - getX(), target.getY(0.5) - lance.getY(), target.getZ() - getZ())
                    .yRot((float) Math.toRadians(i * 8));
            lance.shoot(dir.x, dir.y, dir.z, 1.8f, 1f);
            level().addFreshEntity(lance);
        }
        playSound(SoundEvents.PLAYER_HURT_FREEZE, 1.5f, 0.6f);
    }

    /** Свои зимогоры не ранят лорда. */
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
