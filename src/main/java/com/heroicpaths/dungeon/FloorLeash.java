package com.heroicpaths.dungeon;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Монстры данжа держатся своего этажа: пока их не ранили, они не сходят на лестницу между этажами — ни вниз
 * с зала спуска, ни вверх с площадки, будто там невидимая стена. Раненый монстр свободен и может гнаться за
 * игроком куда угодно. Этаж запоминается при постройке данжа (высота, на которой монстр появился).
 */
public final class FloorLeash {
    /** Высота ног монстра при постройке данжа; снимается при первом уроне. */
    public static final String TAG = "heroicpaths.floor_y";
    /** Последняя позиция в пределах этажа, куда монстра возвращаем (не сохраняется). */
    private static final Map<Mob, Vec3> LAST_VALID = new WeakHashMap<>();

    private FloorLeash() {}

    public static void bind(Mob mob) {
        if (!mob.getPersistentData().contains(TAG)) mob.getPersistentData().putInt(TAG, mob.getBlockY());
    }

    public static boolean isBound(Mob mob) {
        return mob.getPersistentData().contains(TAG);
    }

    /** Может ли привязанный монстр стоять на этой высоте: шаг на первую ступеньку можно, дальше — нет. */
    public static boolean withinFloor(Mob mob, double y) {
        int home = mob.getPersistentData().getInt(TAG);
        boolean flying = mob.getNavigation() instanceof FlyingPathNavigation;
        return y >= home - (flying ? 1.0 : 0.6) && y <= home + (flying ? 5.0 : 1.3);
    }

    @SubscribeEvent
    public static void onMobTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || !isBound(mob)) return;
        if (!DungeonManager.isDungeonLevel(level) || !mob.isAlive()) return;
        if (withinFloor(mob, mob.getY())) {
            LAST_VALID.put(mob, mob.position());
            return;
        }
        mob.getNavigation().stop();
        Vec3 back = LAST_VALID.get(mob);
        if (back == null) return;
        mob.setPos(back.x, back.y, back.z);
        mob.setDeltaMovement(Vec3.ZERO);
    }

    /** Получив урон, монстр больше не привязан к этажу. */
    @SubscribeEvent
    public static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Mob mob && isBound(mob)) {
            mob.getPersistentData().remove(TAG);
            LAST_VALID.remove(mob);
        }
    }
}
