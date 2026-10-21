package com.heroicpaths.entity;

import com.heroicpaths.fx.Palette;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Метеор мага — только зрелище: раскалённый камень падает с неба по прямой от from к to за flight тиков
 * (положение считают и сервер, и клиент — полёт плавный). Размер — ранг умения: 1, 2 или 3 блока.
 * Урон и взрыв делает умение (AbilityLogic.meteor) по схеме меток Zones, ровно к приземлению камня.
 */
public class MeteorEntity extends Entity {
    private static final EntityDataAccessor<Integer> SIZE = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FLIGHT = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> FROM = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> TO = SynchedEntityData.defineId(MeteorEntity.class, EntityDataSerializers.VECTOR3);

    public MeteorEntity(EntityType<? extends MeteorEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public MeteorEntity(Level level, Vec3 from, Vec3 to, int size, int flightTicks) {
        this(ModEntities.METEOR.get(), level);
        entityData.set(SIZE, size);
        entityData.set(FLIGHT, flightTicks);
        entityData.set(FROM, from.toVector3f());
        entityData.set(TO, to.toVector3f());
        setPos(from);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SIZE, 1);
        builder.define(FLIGHT, 30);
        builder.define(FROM, new Vector3f());
        builder.define(TO, new Vector3f());
    }

    public int size() {
        return Math.max(1, entityData.get(SIZE));
    }

    /** Где камень на тике t: падает с ускорением. */
    private Vec3 at(float t) {
        float k = Math.min(1, t / Math.max(1, entityData.get(FLIGHT)));
        k = k * (0.45f + 0.55f * k);
        Vector3f from = entityData.get(FROM), to = entityData.get(TO);
        return new Vec3(from.x + (to.x - from.x) * k, from.y + (to.y - from.y) * k, from.z + (to.z - from.z) * k);
    }

    @Override
    public void tick() {
        super.tick();
        setPos(at(tickCount));
        if (level().isClientSide) trail();
        else if (tickCount > entityData.get(FLIGHT)) discard();
    }

    /** Шлейф: клубы огня по всему пути за тик, искры, густой дым, капли лавы; гул пламени. */
    private void trail() {
        int s = size();
        Vec3 now = position(), before = new Vec3(xo, yo, zo);
        for (int i = 0; i < 3 + s * 3; i++) {
            Vec3 p = before.lerp(now, random.nextDouble());
            double spread = 0.35 * s;
            level().addParticle(ModParticles.wisp(Palette.FIRE), p.x + jitter(spread), p.y + jitter(spread), p.z + jitter(spread), 0, 0.02, 0);
        }
        for (int i = 0; i < 2 + s * 2; i++)
            level().addParticle(ModParticles.ember(Palette.FIRE), now.x + jitter(0.5 * s), now.y + jitter(0.5 * s), now.z + jitter(0.5 * s),
                    jitter(0.15), jitter(0.15) + 0.05, jitter(0.15));
        for (int i = 0; i < s; i++)
            level().addParticle(ParticleTypes.LARGE_SMOKE, before.x + jitter(0.4 * s), before.y + jitter(0.4 * s), before.z + jitter(0.4 * s), 0, 0.03, 0);
        if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.LAVA, now.x, now.y, now.z, 0, 0, 0);
        if (tickCount % 5 == 0)
            level().playLocalSound(now.x, now.y, now.z, SoundEvents.FIRE_AMBIENT, SoundSource.NEUTRAL, 1.5f + s * 0.5f, 0.6f, false);
    }

    private double jitter(double spread) {
        return (random.nextDouble() * 2 - 1) * spread;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
