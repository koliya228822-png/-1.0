package com.heroicpaths.rpg.ability;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.fx.Fx;
import com.heroicpaths.fx.Palette;
import com.heroicpaths.registry.ModParticles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Метки ударов по площади. Схема: место будущего удара отмечается кругом на 1 секунду — враги в нём замедлены на 80%;
 * за полсекунды до удара метка гаснет (успеть отскочить), затем удар. Кольцо внутри круга сжимается — видно, сколько осталось.
 */
public final class Zones {
    /** Насколько метка замедляет врагов. */
    public static final float SLOW = 0.8f;
    /** Сколько живёт метка и через сколько после её появления удар. */
    public static final int MARK_TICKS = 20, IMPACT_DELAY = 30;
    private static final ResourceLocation SLOW_ID = HeroicPaths.id("zone_slow");

    private record Zone(ServerLevel level, ServerPlayer owner, Vec3 center, double radius, long start, long end, Palette palette) {}

    private static final List<Zone> ZONES = new ArrayList<>();
    private static final Map<LivingEntity, Long> SLOWED = new HashMap<>();

    private Zones() {}

    /** Метка на ticks тиков: круг гаммы palette, враги внутри замедлены на 80%. */
    public static void mark(ServerPlayer owner, Vec3 center, double radius, Palette palette, int ticks) {
        ServerLevel level = owner.serverLevel();
        long now = level.getGameTime();
        ZONES.add(new Zone(level, owner, center, radius, now, now + ticks, palette));
    }

    /** Удар по схеме: метка на 1 с, пауза полсекунды, затем impact (через 1.5 с после вызова). */
    public static void strike(ServerPlayer owner, Vec3 center, double radius, Palette palette, Runnable impact) {
        mark(owner, center, radius, palette, MARK_TICKS);
        DelayedTasks.schedule(IMPACT_DELAY, impact);
    }

    public static boolean isSlowed(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.hasModifier(SLOW_ID);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Zone> zones = ZONES.iterator();
        while (zones.hasNext()) {
            Zone zone = zones.next();
            long now = zone.level.getGameTime();
            if (now >= zone.end) {
                zones.remove();
                continue;
            }
            double r2 = zone.radius * zone.radius;
            for (LivingEntity target : zone.level.getEntitiesOfClass(LivingEntity.class, new AABB(zone.center, zone.center).inflate(zone.radius, 3, zone.radius),
                    entity -> AbilityLogic.canHarm(zone.owner, entity) && entity.distanceToSqr(zone.center) <= r2 + 1)) {
                slow(target, target.level().getGameTime() + 2);
            }
            if ((now - zone.start) % 2 == 0) draw(zone, now);
        }
        Iterator<Map.Entry<LivingEntity, Long>> slowed = SLOWED.entrySet().iterator();
        while (slowed.hasNext()) {
            Map.Entry<LivingEntity, Long> entry = slowed.next();
            LivingEntity entity = entry.getKey();
            if (entity.isRemoved() || entity.level().getGameTime() > entry.getValue()) {
                unslow(entity);
                slowed.remove();
            }
        }
    }

    private static void draw(Zone zone, long now) {
        Vec3 c = zone.center.add(0, 0.12, 0);
        int points = Math.max(18, (int) (zone.radius * 10));
        Fx.ring(zone.level, ModParticles.mark(zone.palette), c, zone.radius, points, 0, 0);
        // Внутреннее кольцо сжимается к центру к моменту, когда метка гаснет.
        double left = 1 - (double) (now - zone.start) / (zone.end - zone.start);
        Fx.ring(zone.level, ModParticles.mark(zone.palette), c, Math.max(0.2, zone.radius * left), Math.max(8, (int) (points * left)), 0, 0);
        zone.level.sendParticles(ModParticles.wisp(zone.palette), c.x, c.y, c.z, (int) Math.max(2, zone.radius * 1.5),
                zone.radius * 0.45, 0.02, zone.radius * 0.45, 0.005);
    }

    private static void slow(LivingEntity entity, long until) {
        for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null && !instance.hasModifier(SLOW_ID))
                instance.addTransientModifier(new AttributeModifier(SLOW_ID, -SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        SLOWED.put(entity, until);
    }

    private static void unslow(LivingEntity entity) {
        for (var attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.FLYING_SPEED)) {
            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) instance.removeModifier(SLOW_ID);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ZONES.clear();
        SLOWED.clear();
    }
}
