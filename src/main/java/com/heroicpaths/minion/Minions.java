package com.heroicpaths.minion;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.party.PartyManager;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/** Общие правила существ игроков: хозяин, кого можно атаковать, сила и время жизни. */
public final class Minions {
    public static final ResourceLocation STATS = HeroicPaths.id("minion_stats");

    private Minions() {}

    @Nullable
    public static ServerPlayer owner(Entity minion) {
        if (!(minion instanceof Minion m) || m.minionData().owner() == null || !(minion.level() instanceof ServerLevel level)) return null;
        return level.getServer().getPlayerList().getPlayer(m.minionData().owner());
    }

    public static boolean isOwnedBy(Entity entity, Player player) {
        return entity instanceof Minion m && player.getUUID().equals(m.minionData().owner());
    }

    /** Существо и цель на одной стороне: хозяин, его группа и их существа. */
    public static boolean isFriendly(Entity minion, LivingEntity other) {
        UUID ownerId = minion instanceof Minion m ? m.minionData().owner() : null;
        if (ownerId == null || other == minion) return other == minion;
        Player owner = minion.level().getPlayerByUUID(ownerId);
        if (other.getUUID().equals(ownerId)) return true;
        if (other instanceof Player player) return owner != null && PartyManager.sameParty(owner, player);
        if (other instanceof Minion otherMinion && otherMinion.minionData().owner() != null) {
            UUID otherOwner = otherMinion.minionData().owner();
            if (otherOwner.equals(ownerId)) return true;
            Player otherPlayer = minion.level().getPlayerByUUID(otherOwner);
            return owner != null && otherPlayer != null && PartyManager.sameParty(owner, otherPlayer);
        }
        return false;
    }

    /** Цель, которую существо может атаковать само: враги и всё, что дерётся с хозяином. */
    public static boolean isValidTarget(Mob minion, @Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || isFriendly(minion, target)) return false;
        if (target instanceof Player) return false;
        return target instanceof Enemy || target instanceof Mob mob && mob.getTarget() != null && isFriendly(minion, mob.getTarget());
    }

    /**
     * Сила существа: базовые здоровье и урон × рост от уровня хозяина × бонусы спутников (харизма, подкласс, дерево).
     */
    public static void applyStats(Mob mob, ServerPlayer owner, float baseHealth, float baseDamage) {
        float levelScale = 1f + 0.015f * (RpgManager.get(owner).getLevel() - 1);
        float health = baseHealth * levelScale * PlayerStats.minionHealthFactor(owner) + PlayerStats.get(owner).get(com.heroicpaths.rpg.stat.Stat.FLAT_MINION_HEALTH);
        float damage = baseDamage * levelScale * PlayerStats.minionDamageFactor(owner) + PlayerStats.get(owner).get(com.heroicpaths.rpg.stat.Stat.FLAT_MINION_DAMAGE);
        float fraction = mob.getMaxHealth() > 0 ? mob.getHealth() / mob.getMaxHealth() : 1f;
        setBase(mob, Attributes.MAX_HEALTH, health);
        setBase(mob, Attributes.ATTACK_DAMAGE, damage);
        mob.setHealth(Math.max(1f, mob.getMaxHealth() * Math.min(1f, fraction)));
    }

    private static void setBase(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    /** Время жизни временных существ и уборка брошенных. Вызывается из тика существа на сервере. */
    public static void tick(Mob mob) {
        if (!(mob instanceof Minion minion) || !(mob.level() instanceof ServerLevel level)) return;
        Minion.MinionData data = minion.minionData();
        if (data.lifetime() > 0) {
            data.setLifetime(data.lifetime() - 1);
            if (data.lifetime() <= 0) {
                vanish(mob);
                return;
            }
        }
        if (mob.tickCount % 20 == 0) dropUnreachableTarget(mob);
        if (mob.tickCount % 40 != 0) return;
        ServerPlayer owner = owner(mob);
        // Без хозяина в сети существо исчезает; зверь охотника вернётся, когда хозяин его позовёт.
        if (owner == null || owner.level() != level) {
            vanish(mob);
            return;
        }
        if (data.isCompanion() && !mob.getUUID().equals(RpgManager.get(owner).getCompanion())) vanish(mob);
    }

    /**
     * Существо ближнего боя бросает цель, до которой не дойти (ифрит в воздухе, враг за стеной),
     * и выбирает другую, а не стоит на месте.
     */
    private static void dropUnreachableTarget(Mob mob) {
        LivingEntity target = mob.getTarget();
        if (target == null) return;
        if (!target.isAlive() || isFriendly(mob, target)) {
            mob.setTarget(null);
            return;
        }
        if (mob instanceof net.minecraft.world.entity.monster.RangedAttackMob) return;
        if (mob.isWithinMeleeAttackRange(target)) return;
        net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().createPath(target, 0);
        if (path == null || !path.canReach() && path.getEndNode() != null
                && path.getEndNode().distanceToSqr(target.blockPosition()) > 4) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }

    public static void vanish(Mob mob) {
        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
        }
        mob.discard();
    }

    /** Все существа игрока поблизости. */
    public static List<Mob> ownedNear(ServerPlayer owner, double radius) {
        return owner.serverLevel().getEntitiesOfClass(Mob.class, new AABB(owner.blockPosition()).inflate(radius),
                mob -> mob.isAlive() && isOwnedBy(mob, owner));
    }
}
