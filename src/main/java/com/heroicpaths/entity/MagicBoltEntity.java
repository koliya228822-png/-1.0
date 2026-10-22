package com.heroicpaths.entity;

import com.heroicpaths.fx.Fx;
import com.heroicpaths.fx.Palette;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModParticles;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.ability.AbilityLogic;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Магическая стрела жезла или посоха. Летит медленно (5 блоков в секунду) и доворачивает к ближайшему врагу;
 * сквозь своих (группу, своих слуг, спутников и питомцев) пролетает, мирных существ на пути задевает. Стихия — стихия фокуса:
 * огонь поджигает, лёд замедляет, земля отбрасывает, гроза перескакивает на соседа, тайная магия просто бьёт.
 */
public class MagicBoltEntity extends ThrowableProjectile {
    /** Скорость: 5 блоков в секунду. */
    public static final float SPEED = 0.25f;
    private static final int LIFETIME = 120;
    private static final double HOMING_RADIUS = 16;
    /** Насколько сильно стрела доворачивает к цели за тик. */
    private static final double TURN = 0.2;
    private static final EntityDataAccessor<Integer> PALETTE = SynchedEntityData.defineId(MagicBoltEntity.class, EntityDataSerializers.INT);

    private float damage;
    @Nullable
    private LivingEntity target;

    public MagicBoltEntity(EntityType<? extends MagicBoltEntity> type, Level level) {
        super(type, level);
    }

    public MagicBoltEntity(Level level, LivingEntity owner, float damage, Palette palette) {
        super(ModEntities.MAGIC_BOLT.get(), owner, level);
        this.damage = damage;
        entityData.set(PALETTE, palette.ordinal());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(PALETTE, Palette.ARCANE.ordinal());
    }

    public Palette palette() {
        Palette[] all = Palette.values();
        return all[Mth.clamp(entityData.get(PALETTE), 0, all.length - 1)];
    }

    public float damage() {
        return damage;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            if (tickCount > LIFETIME) {
                fade();
                return;
            }
            steer();
        }
        super.tick();
        if (level().isClientSide) trail();
    }

    /** Цель, выбранная при выстреле (враг, на которого смотрел маг); пока она жива и видна, стрела летит к ней. */
    public void lockOn(@Nullable LivingEntity target) {
        this.target = target;
    }

    @Nullable
    public LivingEntity target() {
        return target;
    }

    /** Довернуть к цели (только если её видно — сквозь стены стрела не наводится) и держать скорость 5 блоков в секунду. */
    private void steer() {
        Vec3 motion = getDeltaMovement();
        Vec3 dir = motion.lengthSqr() > 1.0E-6 ? motion.normalize() : getLookAngle();
        if (getOwner() instanceof ServerPlayer owner) {
            if (target == null || !target.isAlive() || target.distanceToSqr(this) > 24 * 24 || !AbilityLogic.isEnemy(owner, target))
                target = nearest(owner);
            if (target != null && visible(position(), target)) {
                Vec3 to = center(target).subtract(position());
                if (to.lengthSqr() > 1.0E-4) dir = dir.lerp(to.normalize(), TURN).normalize();
            }
        }
        setDeltaMovement(dir.scale(SPEED));
    }

    private static Vec3 center(LivingEntity entity) {
        return entity.position().add(0, entity.getBbHeight() * 0.55, 0);
    }

    /** Между точкой и существом нет блоков. */
    private boolean visible(Vec3 from, LivingEntity entity) {
        return level().clip(new ClipContext(from, center(entity), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /** Ближайший к стреле враг, которого с неё видно. */
    @Nullable
    private LivingEntity nearest(ServerPlayer owner) {
        LivingEntity best = null;
        double bestDistance = HOMING_RADIUS * HOMING_RADIUS;
        for (LivingEntity candidate : level().getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(HOMING_RADIUS),
                entity -> AbilityLogic.isEnemy(owner, entity))) {
            double distance = candidate.distanceToSqr(this);
            if (distance < bestDistance && visible(position(), candidate)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Насколько далеко и под каким углом от прицела ищется враг, на которого смотрит маг. */
    private static final double AIM_RANGE = 24, AIM_CONE_DEGREES = 30;

    /**
     * Враг, на которого смотрит маг: видимый (без стен между ними), в конусе вокруг прицела; из таких — ближе всего
     * к прицелу, при равном угле — ближайший. Null, если в той стороне видимых врагов нет.
     */
    @Nullable
    public static LivingEntity aimedTarget(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        double cos = Math.cos(Math.toRadians(AIM_CONE_DEGREES));
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(AIM_RANGE),
                entity -> AbilityLogic.isEnemy(player, entity))) {
            Vec3 to = center(candidate).subtract(eye);
            double distance = to.length();
            if (distance > AIM_RANGE || distance < 1.0E-3) continue;
            double dot = to.scale(1 / distance).dot(look);
            if (dot < cos || !player.hasLineOfSight(candidate)) continue;
            // Угол от прицела важнее расстояния: 1° ≈ 1 блок.
            double score = Math.toDegrees(Math.acos(Math.min(1, dot))) + distance;
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    /** Своих (группу, своих слуг и питомцев) стрела не задевает; мирных существ на пути — задевает, но наводится только на врагов. */
    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity)) return false;
        if (getOwner() instanceof ServerPlayer owner) return entity instanceof LivingEntity living && AbilityLogic.canHarm(owner, living);
        return !(getOwner() instanceof net.minecraft.world.entity.player.Player);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!(level() instanceof ServerLevel level) || !(result.getEntity() instanceof LivingEntity victim)) return;
        Entity owner = getOwner();
        float amount = damage;
        if (owner instanceof ServerPlayer player && player.getRandom().nextFloat() * 100f < PlayerStats.critChance(player)) {
            amount = PlayerStats.applyCrit(player, amount);
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
        }
        victim.hurt(damageSources().indirectMagic(this, owner), amount);
        element(level, victim, owner, amount);
    }

    /** Действие стихии при попадании. */
    private void element(ServerLevel level, LivingEntity victim, @Nullable Entity owner, float amount) {
        switch (palette()) {
            case FIRE -> victim.igniteForSeconds(3);
            case FROST -> {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                victim.setTicksFrozen(Math.min(victim.getTicksRequiredToFreeze(), victim.getTicksFrozen() + 40));
            }
            case AMBER -> {
                Vec3 push = getDeltaMovement();
                victim.knockback(0.5, -push.x, -push.z);
                Fx.debris(level, victim.position(), 12, 0.3, 0.15);
            }
            case STORM -> {
                // Разряд перескакивает на ближайшего соседа цели за половину урона.
                if (!(owner instanceof ServerPlayer player)) return;
                LivingEntity next = null;
                double best = 16;
                for (LivingEntity other : AbilityLogic.enemiesInRadius(player, victim.position(), 4)) {
                    double d = other.distanceToSqr(victim);
                    if (other != victim && d < best) {
                        best = d;
                        next = other;
                    }
                }
                if (next != null) {
                    Fx.zigzag(level, Palette.STORM, victim.position().add(0, victim.getBbHeight() / 2, 0), next.position().add(0, next.getBbHeight() / 2, 0), random);
                    next.hurt(damageSources().indirectMagic(this, owner), amount * 0.5f);
                }
            }
            default -> { }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel level) {
            Vec3 at = position().add(0, getBbHeight() / 2, 0);
            Palette palette = palette();
            Fx.flash(level, palette, at, 1);
            Fx.wisps(level, palette, at, 14, 0.15, 0.08);
            Fx.embers(level, palette, at, 20, 0.08, 0.22);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.7f, 1.4f);
            discard();
        }
    }

    /** Стрела выдохлась: тихо рассыпается искрами. */
    private void fade() {
        if (level() instanceof ServerLevel level) Fx.embers(level, palette(), position(), 10, 0.1, 0.08);
        discard();
    }

    /** Шлейф (на клиенте): облачка вдоль пути, две спирали искр, частицы стихии. */
    private void trail() {
        Palette palette = palette();
        double half = getBbHeight() / 2;
        Vec3 now = position().add(0, half, 0), before = new Vec3(xo, yo + half, zo), motion = getDeltaMovement();
        Vec3 dir = motion.lengthSqr() > 1.0E-6 ? motion.normalize() : new Vec3(0, 0, 1);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        Level level = level();
        for (int i = 0; i < 2; i++) {
            Vec3 p = before.lerp(now, (i + random.nextDouble()) / 2);
            if (tickCount > 6) level.addParticle(ModParticles.wisp(palette), p.x, p.y, p.z, -dir.x * 0.01, -dir.y * 0.01, -dir.z * 0.01);
            double angle = (tickCount + i * 0.5) * 1.3;
            for (int s = 0; s < 2; s++) {
                double a = angle + s * Math.PI;
                Vec3 off = side.scale(Math.cos(a) * 0.18).add(up.scale(Math.sin(a) * 0.18));
                level.addParticle(ModParticles.ember(palette), p.x + off.x, p.y + off.y, p.z + off.z, off.x * 0.05, off.y * 0.05, off.z * 0.05);
            }
        }
        ParticleOptions extra = switch (palette) {
            case FIRE -> ParticleTypes.SMALL_FLAME;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case STORM -> ParticleTypes.ELECTRIC_SPARK;
            case AMBER -> ModParticles.wisp(Palette.EARTH);
            default -> ParticleTypes.ENCHANT;
        };
        // Первые тики стрела у самой камеры стрелка: крупные частицы стихии там только мешали бы.
        if (tickCount > 8 && random.nextInt(2) == 0)
            level.addParticle(extra, now.x, now.y, now.z, (random.nextDouble() - 0.5) * 0.05, 0.01, (random.nextDouble() - 0.5) * 0.05);
        if (tickCount % 10 == 0)
            level.playLocalSound(now.x, now.y, now.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.35f, 1.6f + random.nextFloat() * 0.3f, false);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("damage", damage);
        tag.putInt("palette", entityData.get(PALETTE));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("damage");
        entityData.set(PALETTE, tag.getInt("palette"));
    }
}
