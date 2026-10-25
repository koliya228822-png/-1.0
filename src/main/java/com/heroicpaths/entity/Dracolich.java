package com.heroicpaths.entity;

import com.heroicpaths.minion.Minion;
import com.heroicpaths.minion.Minions;
import com.heroicpaths.rpg.ability.AbilityLogic;
import com.heroicpaths.rpg.ability.UniqueAbilities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Драколич призывателя («Призыв Драколича»): огромный костяной фантом. Кружит над целью хозяина,
 * дышит по площади, каждой третьей атакой бьёт крыльями вокруг себя, стягивает на себя врагов,
 * а убитые им встают скелетами хозяина. Живёт минуту; каждая атака продлевает жизнь.
 */
public class Dracolich extends Phantom implements Minion {
    public static final int SIZE = 14;
    private static final int ATTACK_INTERVAL = 30;
    private static final double SPLASH = 3.5, SWEEP = 7, AGGRO = 12, SEARCH = 24;

    private final MinionData data = new MinionData();
    private int attacks;
    private int nextAttack = ATTACK_INTERVAL;

    public Dracolich(EntityType<? extends Phantom> type, Level level) {
        super(type, level);
        xpReward = 0;
        moveControl = new MoveControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 120).add(Attributes.FOLLOW_RANGE, 32);
    }

    @Override
    public MinionData minionData() {
        return data;
    }

    @Override
    protected void registerGoals() {
        // Полётом и атаками управляет tick.
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) return;
        Minions.tick(this);
        if (isRemoved()) return;
        ServerPlayer owner = Minions.owner(this);
        if (owner == null) return;
        LivingEntity target = getTarget();
        if (tickCount % 10 == 0 && (target == null || !target.isAlive() || target.distanceToSqr(owner) > SEARCH * SEARCH * 2
                || !AbilityLogic.isEnemy(owner, target))) {
            target = nearestEnemy(owner);
            setTarget(target);
        }
        fly(owner, target);
        if (tickCount % 20 == 0) taunt(owner);
        if (target != null && --nextAttack <= 0 && distanceToSqr(target) < 14 * 14) {
            nextAttack = ATTACK_INTERVAL;
            attack(level, owner, target);
        }
    }

    @Nullable
    private LivingEntity nearestEnemy(ServerPlayer owner) {
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity enemy : AbilityLogic.enemiesInRadius(owner, owner.position(), SEARCH)) {
            if (enemy == this || enemy instanceof Player) continue;
            double distance = enemy.distanceToSqr(owner);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = enemy;
            }
        }
        return best;
    }

    /** Кружит над целью (или над хозяином), плавно разворачиваясь по ходу полёта. */
    private void fly(ServerPlayer owner, @Nullable LivingEntity target) {
        LivingEntity center = target != null ? target : owner;
        double angle = tickCount * 0.05;
        double radius = target != null ? 7 : 5;
        Vec3 point = center.position().add(Math.cos(angle) * radius, target != null ? 6 : 5, Math.sin(angle) * radius);
        if (owner.distanceToSqr(this) > 48 * 48) {
            teleportTo(owner.getX(), owner.getY() + 5, owner.getZ());
            return;
        }
        Vec3 delta = point.subtract(position());
        Vec3 velocity = getDeltaMovement().scale(0.8).add(delta.scale(0.03));
        double max = 0.7;
        if (velocity.length() > max) velocity = velocity.normalize().scale(max);
        setDeltaMovement(velocity);
        if (velocity.horizontalDistanceSqr() > 1e-4) {
            float yaw = (float) (Mth.atan2(velocity.z, velocity.x) * Mth.RAD_TO_DEG) - 90f;
            setYRot(Mth.approachDegrees(getYRot(), yaw, 10f));
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
        setXRot((float) Mth.clamp(-velocity.y * 40, -30, 30));
    }

    /** Аура злобы: враги вокруг бросаются на драколича, а не на хозяина. */
    private void taunt(ServerPlayer owner) {
        for (LivingEntity enemy : AbilityLogic.enemiesInRadius(owner, position(), AGGRO)) {
            if (enemy instanceof Mob mob && mob != this && !(enemy instanceof DungeonBoss)) mob.setTarget(this);
        }
    }

    private void attack(ServerLevel level, ServerPlayer owner, LivingEntity target) {
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        data.setLifetime(Math.max(data.lifetime(), UniqueAbilities.DRACOLICH_TICKS));
        attacks++;
        List<LivingEntity> victims;
        if (attacks % 3 == 0) {
            // Удар крыльями: всех вокруг себя.
            Vec3 ground = new Vec3(getX(), target.getY(), getZ());
            victims = AbilityLogic.enemiesInRadius(owner, ground, SWEEP);
            for (int i = 0; i < 48; i++) {
                double a = i * Math.PI * 2 / 48;
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, ground.x + Math.cos(a) * SWEEP * 0.7, ground.y + 0.5, ground.z + Math.sin(a) * SWEEP * 0.7, 1, 0, 0, 0, 0);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 2f, 0.7f);
        } else {
            // Дыхание: цель и всех рядом с ней.
            victims = AbilityLogic.enemiesInRadius(owner, target.position(), SPLASH);
            Vec3 from = position().add(0, getBbHeight() * 0.3, 0), to = target.position().add(0, target.getBbHeight() / 2, 0);
            Vec3 step = to.subtract(from).scale(1 / 12.0);
            for (int i = 0; i <= 12; i++) {
                Vec3 at = from.add(step.scale(i));
                level.sendParticles(ParticleTypes.DRAGON_BREATH, at.x, at.y, at.z, 3, 0.2, 0.2, 0.2, 0.01);
            }
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, to.x, to.y, to.z, 30, SPLASH * 0.4, 0.4, SPLASH * 0.4, 0.02);
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_DRAGON_SHOOT, SoundSource.HOSTILE, 1.5f, 0.8f);
        }
        if (!victims.contains(target)) victims = new java.util.ArrayList<>(victims);
        if (!victims.contains(target)) victims.add(target);
        for (LivingEntity victim : victims) {
            if (victim == this || victim instanceof Player) continue;
            victim.invulnerableTime = 0;
            victim.hurt(damageSources().mobAttack(this), damage);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity attacker
                && (Minions.isFriendly(this, attacker) || attacker instanceof IronGolem || attacker instanceof VillageGuard)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return !Minions.isFriendly(this, target) && super.canAttack(target);
    }

    @Override
    protected boolean isSunBurnTick() {
        return false;
    }

    @Override
    public boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        data.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        data.load(tag);
    }
}
