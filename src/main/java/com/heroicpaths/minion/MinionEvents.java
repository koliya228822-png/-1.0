package com.heroicpaths.minion;

import com.heroicpaths.rpg.RpgEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/** Существа игроков не ранят своих и сами не страдают от своих; их добыча опыта идёт хозяину. */
public final class MinionEvents {
    private MinionEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Entity attacker = event.getSource().getEntity();
        if (attacker instanceof LivingEntity living && attacker != victim) {
            if (victim instanceof Minion && Minions.isFriendly(victim, living)) event.setCanceled(true);
            else if (attacker instanceof Minion && Minions.isFriendly(attacker, victim)) event.setCanceled(true);
        }
    }

    /** Убийство существом засчитывается хозяину, как если бы он убил сам. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Entity killer = event.getSource().getEntity();
        if (killer instanceof Minion) {
            ServerPlayer owner = Minions.owner(killer);
            if (owner != null) RpgEvents.awardKillXp(owner, event.getEntity());
        }
    }
}
