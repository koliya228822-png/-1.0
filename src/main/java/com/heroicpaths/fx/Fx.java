package com.heroicpaths.fx;

import com.heroicpaths.registry.ModParticles;
import com.heroicpaths.rpg.ability.DelayedTasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Набор зрелищных эффектов умений (на сервере, частицы рассылаются игрокам рядом): вспышки, кольца, ударные волны,
 * столбы света, лучи, молнии-зигзаги, спирали, ауры и обломки земли. Цвет — гамма стихии (Palette).
 */
public final class Fx {
    private Fx() {}

    // ---------------------------------------------------------------- базовые

    /** Облачка в точке: count штук разбросом spread, скорость speed во все стороны. */
    public static void wisps(ServerLevel level, Palette palette, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(ModParticles.wisp(palette), at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    public static void embers(ServerLevel level, Palette palette, Vec3 at, int count, double spread, double speed) {
        level.sendParticles(ModParticles.ember(palette), at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    public static void flash(ServerLevel level, Palette palette, Vec3 at, int count) {
        level.sendParticles(ModParticles.flash(palette), at.x, at.y, at.z, count, 0.05, 0.05, 0.05, 0);
    }

    /** Одна частица с точной скоростью (в пакете count = 0 означает «скорость = смещение × speed»). */
    public static void one(ServerLevel level, ParticleOptions particle, Vec3 at, Vec3 velocity) {
        level.sendParticles(particle, at.x, at.y, at.z, 0, velocity.x, velocity.y, velocity.z, 1);
    }

    /** Взрыв стихии: вспышка, облачка и искры во все стороны. scale — 1 для обычного удара. */
    public static void burst(ServerLevel level, Palette palette, Vec3 at, double scale) {
        flash(level, palette, at, Math.max(1, (int) Math.round(scale)));
        wisps(level, palette, at, (int) (18 * scale), 0.25 * scale, 0.1 * scale);
        embers(level, palette, at, (int) (24 * scale), 0.1 * scale, 0.25 * scale);
    }

    public static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    // ---------------------------------------------------------------- фигуры

    /** Горизонтальное кольцо: points частиц на радиусе, каждая летит наружу со скоростью outward (и вверх со скоростью up). */
    public static void ring(ServerLevel level, ParticleOptions particle, Vec3 center, double radius, int points, double outward, double up) {
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2 * i / points;
            double c = Math.cos(angle), s = Math.sin(angle);
            one(level, particle, center.add(c * radius, 0, s * radius), new Vec3(c * outward, up, s * outward));
        }
    }

    /** Ударная волна: кольцо облачков расходится от центра до радиуса за ticks тиков. */
    public static void shockwave(ServerLevel level, Palette palette, Vec3 center, double radius, int ticks) {
        int[] step = {0};
        DelayedTasks.repeat(0, 1, ticks, () -> {
            double r = radius * (++step[0]) / ticks;
            int points = Math.max(10, (int) (r * 7));
            ring(level, ModParticles.wisp(palette), center.add(0, 0.15, 0), r, points, 0.03, 0.01);
            ring(level, ModParticles.ember(palette), center.add(0, 0.25, 0), r, points / 2, 0.12, 0.08);
        });
    }

    /** Столб частиц от основания вверх. */
    public static void column(ServerLevel level, ParticleOptions particle, Vec3 base, double height, int count, double spread, double rise) {
        for (int i = 0; i < count; i++) {
            double y = height * i / Math.max(1, count - 1);
            level.sendParticles(particle, base.x, base.y + y, base.z, 1, spread, 0.05, spread, 0);
        }
        if (rise > 0) level.sendParticles(particle, base.x, base.y, base.z, count / 2, spread, 0.1, spread, 0);
    }

    /** Луч с неба в точку: столб света сверху вниз и вспышка у земли. */
    public static void skyBeam(ServerLevel level, Palette palette, Vec3 at, double height) {
        for (double y = 0; y <= height; y += 0.35) {
            Vec3 p = at.add(0, y, 0);
            one(level, ModParticles.mark(palette), p, Vec3.ZERO);
            if (((int) (y * 3)) % 3 == 0) level.sendParticles(ModParticles.wisp(palette), p.x, p.y, p.z, 1, 0.08, 0.1, 0.08, 0);
        }
        flash(level, palette, at.add(0, 0.3, 0), 1);
    }

    /** Ровная линия частиц от from до to (perBlock штук на блок). */
    public static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to, double perBlock) {
        Vec3 step = to.subtract(from);
        int points = (int) Math.max(2, step.length() * perBlock);
        for (int i = 0; i <= points; i++) one(level, particle, from.add(step.scale((double) i / points)), Vec3.ZERO);
    }

    /** Молния-зигзаг: ломаная из коротких отрезков со случайными изломами. */
    public static void zigzag(ServerLevel level, Palette palette, Vec3 from, Vec3 to, RandomSource random) {
        Vec3 dir = to.subtract(from);
        int segments = Math.max(3, (int) (dir.length() / 1.2));
        Vec3 previous = from;
        for (int i = 1; i <= segments; i++) {
            Vec3 point = from.add(dir.scale((double) i / segments));
            if (i < segments) point = point.add((random.nextDouble() - 0.5) * 0.9, (random.nextDouble() - 0.5) * 0.9, (random.nextDouble() - 0.5) * 0.9);
            line(level, ModParticles.mark(palette), previous, point, 6);
            previous = point;
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 8, 0.2, 0.2, 0.2, 0.3);
    }

    /** Спираль вверх вокруг вертикальной оси: points частиц, turns оборотов. */
    public static void spiral(ServerLevel level, ParticleOptions particle, Vec3 base, double radius, double height, double turns, int points, double phase) {
        for (int i = 0; i < points; i++) {
            double k = (double) i / points, angle = phase + k * turns * Math.PI * 2;
            one(level, particle, base.add(Math.cos(angle) * radius, k * height, Math.sin(angle) * radius), new Vec3(0, 0.02, 0));
        }
    }

    /** Сфера искр, разлетающихся во все стороны с одинаковой скоростью. */
    public static void nova(ServerLevel level, ParticleOptions particle, Vec3 center, int count, double speed, RandomSource random) {
        for (int i = 0; i < count; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian() * 0.6, random.nextGaussian()).normalize();
            one(level, particle, center, dir.scale(speed));
        }
    }

    /** Аура: ticks тиков вокруг сущности кружат искры гаммы (каждые interval тиков). */
    public static void aura(Entity entity, Palette palette, int ticks, int interval) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        DelayedTasks.repeat(0, interval, Math.max(1, ticks / interval), () -> {
            if (!entity.isAlive()) return;
            double phase = level.getGameTime() * 0.35, r = entity.getBbWidth() * 0.8 + 0.3;
            for (int i = 0; i < 3; i++) {
                double angle = phase + i * Math.PI * 2 / 3;
                Vec3 p = entity.position().add(Math.cos(angle) * r, 0.2 + (level.getGameTime() * 0.1 + i * 0.6) % entity.getBbHeight(), Math.sin(angle) * r);
                one(level, ModParticles.ember(palette), p, new Vec3(0, 0.03, 0));
            }
            if (level.random.nextInt(2) == 0) {
                Vec3 p = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
                level.sendParticles(ModParticles.wisp(palette), p.x, p.y, p.z, 1, r * 0.6, entity.getBbHeight() * 0.3, r * 0.6, 0.01);
            }
        });
    }

    /** Обломки земли: частицы блока под точкой, подброшенные вверх. */
    public static void debris(ServerLevel level, Vec3 at, int count, double spread, double speed) {
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.2, at.z));
        if (ground.isAir()) ground = level.getBlockState(BlockPos.containing(at.x, at.y - 1.2, at.z));
        if (ground.isAir()) return;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, count, spread, 0.1, spread, speed);
    }

    /** Точка на земле под (или у) точки at — для меток и ударов с неба. */
    public static Vec3 ground(ServerLevel level, Vec3 at) {
        BlockHitResult hit = level.clip(new ClipContext(at.add(0, 0.5, 0), at.add(0, -8, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? at : hit.getLocation();
    }
}
