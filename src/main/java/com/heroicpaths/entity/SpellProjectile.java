package com.heroicpaths.entity;

import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.fx.Palette;
import com.heroicpaths.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Снаряд заклинания: летит прямо, блоки не поджигает и не ломает.
 * Огненный шар рисуется своим рендером (SpellProjectileRenderer), за ним тянется огненный шлейф, а в цель он бьёт взрывом пламени.
 */
public class SpellProjectile extends ThrowableItemProjectile {
    private static final int LIFETIME_TICKS = 80;

    public enum Kind {
        /** Огненный шар: поджигает цель. */
        FIRE(Items.FIRE_CHARGE, ParticleTypes.FLAME, SoundEvents.FIRECHARGE_USE, Palette.FIRE),
        /** Ледяное копьё: сильно замедляет цель. */
        ICE(Items.PACKED_ICE, ParticleTypes.SNOWFLAKE, SoundEvents.GLASS_BREAK, Palette.FROST),
        /** Ядовитый плевок Королевы пауков: отравляет и замедляет. */
        POISON(Items.FERMENTED_SPIDER_EYE, ParticleTypes.SNEEZE, SoundEvents.SPIDER_HURT, Palette.POISON),
        /** Водяной сгусток Утопшего пророка: отбрасывает и замедляет. */
        WATER(Items.PRISMARINE_CRYSTALS, ParticleTypes.SPLASH, SoundEvents.GENERIC_SPLASH, Palette.WATER),
        /** Тёмная стрела Лича: иссушает. */
        SHADOW(Items.WITHER_SKELETON_SKULL, ParticleTypes.SOUL, SoundEvents.WITHER_SHOOT, Palette.SHADOW);

        final Item item;
        final SimpleParticleType particle;
        final SoundEvent hitSound;
        /** Цвет шлейфа и вспышки при попадании. */
        final Palette palette;

        Kind(Item item, SimpleParticleType particle, SoundEvent hitSound, Palette palette) {
            this.item = item;
            this.particle = particle;
            this.hitSound = hitSound;
            this.palette = palette;
        }
    }

    private float damage = 6f;
    private Kind kind = Kind.FIRE;

    public SpellProjectile(EntityType<? extends SpellProjectile> type, Level level) {
        super(type, level);
    }

    public SpellProjectile(Level level, LivingEntity owner, float damage) {
        this(level, owner, damage, Kind.FIRE);
    }

    public SpellProjectile(Level level, LivingEntity owner, float damage, Kind kind) {
        super(ModEntities.SPELL_PROJECTILE.get(), owner, level);
        this.damage = damage;
        this.kind = kind;
        setItem(new ItemStack(kind.item));
    }

    @Override
    protected Item getDefaultItem() {
        return Items.FIRE_CHARGE;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    /** На клиенте вид снаряда определяется по синхронизированному предмету. */
    private Kind visualKind() {
        for (Kind candidate : Kind.values()) if (getItem().is(candidate.item)) return candidate;
        return Kind.FIRE;
    }

    public boolean isFireball() {
        return visualKind() == Kind.FIRE;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            trail(visualKind());
        } else if (tickCount > LIFETIME_TICKS) {
            discard();
        }
    }

    /**
     * Шлейф снаряда (только на клиенте), в цвет его стихии: светящиеся клубы вдоль всего пути за тик (хвост кометы),
     * две спирали искр вокруг траектории и разлетающиеся искры. У огня ещё язычки пламени, дым, капли лавы и потрескивание,
     * у льда — снежинки, у яда — капли, у воды — брызги, у тьмы — души.
     */
    private void trail(Kind look) {
        Palette palette = look.palette;
        Level level = level();
        double half = getBbHeight() / 2;
        Vec3 now = position().add(0, half, 0), before = new Vec3(xo, yo + half, zo), motion = getDeltaMovement();
        Vec3 dir = motion.lengthSqr() > 1.0E-4 ? motion.normalize() : new Vec3(0, 0, 1);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : side.normalize();
        Vec3 up = side.cross(dir).normalize();
        int steps = 4;
        for (int i = 0; i < steps; i++) {
            double k = (i + random.nextDouble()) / steps;
            Vec3 p = before.lerp(now, k);
            level.addParticle(ModParticles.wisp(palette), p.x + jitter(0.05), p.y + jitter(0.05), p.z + jitter(0.05),
                    -dir.x * 0.03 + jitter(0.01), -dir.y * 0.03 + jitter(0.01), -dir.z * 0.03 + jitter(0.01));
            double turn = (tickCount + k) * 1.7;
            for (int s = 0; s < 2; s++) {
                double angle = turn + s * Math.PI;
                Vec3 off = side.scale(Math.cos(angle) * 0.3).add(up.scale(Math.sin(angle) * 0.3));
                level.addParticle(ModParticles.ember(palette), p.x + off.x, p.y + off.y, p.z + off.z, off.x * 0.08, off.y * 0.08, off.z * 0.08);
            }
        }
        level.addParticle(ModParticles.ember(palette), now.x, now.y, now.z, jitter(0.12), jitter(0.12) + 0.03, jitter(0.12));
        Vec3 flame = before.lerp(now, random.nextDouble());
        if (look != Kind.FIRE) {
            level.addParticle(look.particle, flame.x + jitter(0.1), flame.y + jitter(0.1), flame.z + jitter(0.1), 0, 0, 0);
            return;
        }
        level.addParticle(ParticleTypes.FLAME, flame.x + jitter(0.1), flame.y + jitter(0.1), flame.z + jitter(0.1), 0, 0.01, 0);
        if (random.nextInt(3) == 0)
            level.addParticle(ParticleTypes.SMOKE, before.x + jitter(0.15), before.y + jitter(0.15), before.z + jitter(0.15), 0, 0.02, 0);
        if (random.nextInt(10) == 0)
            level.addParticle(ParticleTypes.LAVA, now.x, now.y, now.z, 0, 0, 0);
        if (tickCount % 4 == 0)
            level.playLocalSound(getX(), getY(), getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.NEUTRAL, 0.9f, 1.4f + random.nextFloat() * 0.3f, false);
    }

    private double jitter(double spread) {
        return (random.nextDouble() * 2 - 1) * spread;
    }

    /** Взрыв при попадании: вспышка, клубы и сноп искр в цвет стихии; у огня ещё пламя, лава и дым. */
    private void burst(ServerLevel level) {
        double x = getX(), y = getY() + getBbHeight() / 2, z = getZ();
        Palette palette = kind.palette;
        level.sendParticles(ModParticles.flash(palette), x, y + 0.3, z, 2, 0.05, 0.05, 0.05, 0);
        level.sendParticles(ModParticles.wisp(palette), x, y + 0.2, z, 40, 0.25, 0.2, 0.25, 0.14);
        level.sendParticles(ModParticles.ember(palette), x, y + 0.2, z, 50, 0.1, 0.1, 0.1, 0.35);
        level.sendParticles(kind.particle, x, y, z, 15, 0.2, 0.2, 0.2, 0.1);
        if (kind == Kind.FIRE) {
            level.sendParticles(ParticleTypes.LAVA, x, y, z, 3, 0.2, 0.2, 0.2, 0);
            level.sendParticles(ParticleTypes.SMOKE, x, y + 0.3, z, 8, 0.3, 0.3, 0.3, 0.02);
            level.playSound(null, x, y, z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.45f, 1.6f);
        }
        level.playSound(null, x, y, z, kind.hitSound, SoundSource.PLAYERS, 0.8f, 0.9f);
    }

    /** Заклинания игрока не задевают своих: снаряд пролетает сквозь группу, своих слуг, спутников и питомцев. */
    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity)) return false;
        if (getOwner() instanceof net.minecraft.server.level.ServerPlayer owner)
            return entity instanceof LivingEntity living && com.heroicpaths.rpg.ability.AbilityLogic.canHarm(owner, living);
        return !(getOwner() instanceof net.minecraft.world.entity.player.Player);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide) return;
        Entity target = result.getEntity();
        target.hurt(damageSources().indirectMagic(this, getOwner()), damage);
        if (kind == Kind.FIRE) {
            target.igniteForSeconds(4);
        } else if (target instanceof LivingEntity living) {
            switch (kind) {
                case ICE -> {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                    living.setTicksFrozen(Math.max(living.getTicksFrozen(), living.getTicksRequiredToFreeze()));
                }
                case POISON -> {
                    living.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                }
                case WATER -> {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                    living.knockback(0.6, getX() - living.getX(), getZ() - living.getZ());
                }
                case SHADOW -> living.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1));
                default -> { }
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel serverLevel) {
            burst(serverLevel);
            discard();
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("damage", damage);
        tag.putString("kind", kind.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("damage");
        try {
            kind = Kind.valueOf(tag.getString("kind"));
        } catch (IllegalArgumentException e) {
            kind = Kind.FIRE;
        }
    }
}
