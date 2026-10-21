package com.heroicpaths.minion;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.PathType;

import java.util.EnumSet;

/** Поведение существ игроков: держаться рядом с хозяином, защищать его и бить его цели. */
public final class MinionGoals {
    private MinionGoals() {}

    /** Полный набор целей для существа ближнего боя. */
    public static void install(PathfinderMob mob, double speed, boolean melee) {
        mob.goalSelector.addGoal(0, new FloatGoal(mob));
        if (melee) mob.goalSelector.addGoal(2, new MeleeAttackGoal(mob, speed * 1.2, true));
        mob.goalSelector.addGoal(4, new FollowOwnerGoal(mob, speed, 7f, 3f, 20f));
        mob.goalSelector.addGoal(8, new LookAtPlayerGoal(mob, Player.class, 8f));
        mob.goalSelector.addGoal(9, new RandomLookAroundGoal(mob));
        installTargets(mob);
    }

    public static void installTargets(PathfinderMob mob) {
        mob.targetSelector.addGoal(1, new OwnerHurtByGoal(mob));
        mob.targetSelector.addGoal(2, new OwnerHurtGoal(mob));
        // Сами существа выбирают только врагов, до которых можно добраться: не тех, что висят высоко в воздухе.
        boolean ranged = mob instanceof net.minecraft.world.entity.monster.RangedAttackMob;
        mob.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(mob, Mob.class, 10, true, false,
                target -> Minions.isValidTarget(mob, target) && target.distanceToSqr(mob) < 256
                        && (ranged || Math.abs(target.getY() - mob.getY()) < 3.5)));
    }

    /** Идёт за хозяином; если отстал слишком далеко, переносится к нему. */
    public static class FollowOwnerGoal extends Goal {
        private final PathfinderMob mob;
        private final double speed;
        private final float startDistance;
        private final float stopDistance;
        private final float teleportDistance;
        private ServerPlayer owner;
        private int recalc;

        public FollowOwnerGoal(PathfinderMob mob, double speed, float startDistance, float stopDistance, float teleportDistance) {
            this.mob = mob;
            this.speed = speed;
            this.startDistance = startDistance;
            this.stopDistance = stopDistance;
            this.teleportDistance = teleportDistance;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            ServerPlayer owner = Minions.owner(mob);
            if (owner == null || owner.isSpectator() || mob.getTarget() != null) return false;
            if (mob.distanceToSqr(owner) < startDistance * startDistance) return false;
            this.owner = owner;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && !mob.getNavigation().isDone() && mob.getTarget() == null
                    && mob.distanceToSqr(owner) > stopDistance * stopDistance;
        }

        @Override
        public void start() {
            recalc = 0;
            mob.setPathfindingMalus(PathType.WATER, 0f);
        }

        @Override
        public void stop() {
            owner = null;
            mob.getNavigation().stop();
        }

        @Override
        public void tick() {
            mob.getLookControl().setLookAt(owner, 10f, mob.getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = adjustedTickDelay(10);
            if (mob.distanceToSqr(owner) > teleportDistance * teleportDistance) {
                mob.moveTo(owner.getX(), owner.getY(), owner.getZ(), mob.getYRot(), mob.getXRot());
                mob.getNavigation().stop();
            } else {
                mob.getNavigation().moveTo(owner, speed);
            }
        }
    }

    /** Атакует того, кто ранил хозяина. */
    public static class OwnerHurtByGoal extends TargetGoal {
        private int timestamp;
        private LivingEntity attacker;

        public OwnerHurtByGoal(Mob mob) {
            super(mob, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            ServerPlayer owner = Minions.owner(mob);
            if (owner == null) return false;
            attacker = owner.getLastHurtByMob();
            return owner.getLastHurtByMobTimestamp() != timestamp && attacker != null && !Minions.isFriendly(mob, attacker)
                    && canAttack(attacker, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(attacker);
            ServerPlayer owner = Minions.owner(mob);
            if (owner != null) timestamp = owner.getLastHurtByMobTimestamp();
            super.start();
        }
    }

    /** Атакует того, кого ударил хозяин. */
    public static class OwnerHurtGoal extends TargetGoal {
        private int timestamp;
        private LivingEntity victim;

        public OwnerHurtGoal(Mob mob) {
            super(mob, false);
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            ServerPlayer owner = Minions.owner(mob);
            if (owner == null) return false;
            victim = owner.getLastHurtMob();
            return owner.getLastHurtMobTimestamp() != timestamp && victim != null && !Minions.isFriendly(mob, victim)
                    && canAttack(victim, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(victim);
            ServerPlayer owner = Minions.owner(mob);
            if (owner != null) timestamp = owner.getLastHurtMobTimestamp();
            super.start();
        }
    }
}
