package com.heroicpaths.entity;

import com.heroicpaths.HeroicPaths;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Общая логика боссов данжей: полоса здоровья, ярость на половине здоровья,
 * таймеры призыва стражей, волны и снарядов.
 */
public final class BossController {
    public static final String MINION_TAG = HeroicPaths.MODID + ".boss_minion";
    private static final ResourceLocation ENRAGE_SPEED = HeroicPaths.id("boss_enrage");
    private static final double BAR_RANGE = 32;

    public interface Abilities {
        void summon();

        void nova();

        void bolt(LivingEntity target);
    }

    private final Mob boss;
    private final String id;
    private final ServerBossEvent bossEvent;
    private boolean enraged;
    private int summonCooldown = 100;
    private int novaCooldown = 120;
    private int boltCooldown = 60;

    public BossController(Mob boss, String id, BossEvent.BossBarColor color) {
        this.boss = boss;
        this.id = id;
        this.bossEvent = new ServerBossEvent(boss.getDisplayName(), color, BossEvent.BossBarOverlay.NOTCHED_10);
    }

    public ServerBossEvent bossEvent() {
        return bossEvent;
    }

    public boolean isEnraged() {
        return enraged;
    }

    /** Вызывается из customServerAiStep босса. */
    public void tick(Abilities abilities) {
        bossEvent.setProgress(boss.getHealth() / boss.getMaxHealth());
        if (boss.tickCount % 10 == 0) updatePlayers();
        updatePhase();
        LivingEntity target = boss.getTarget();
        if (target == null || !target.isAlive()) return;
        if (--summonCooldown <= 0) {
            abilities.summon();
            summonCooldown = enraged ? 160 : 240;
        }
        double distanceSqr = boss.distanceToSqr(target);
        if (--novaCooldown <= 0 && distanceSqr < 36) {
            abilities.nova();
            novaCooldown = enraged ? 120 : 200;
        }
        if (--boltCooldown <= 0 && distanceSqr > 16 && boss.hasLineOfSight(target)) {
            abilities.bolt(target);
            boltCooldown = enraged ? 40 : 80;
        }
    }

    public void updatePhase() {
        if (enraged || boss.getHealth() > boss.getMaxHealth() / 2) return;
        enraged = true;
        AttributeInstance speed = boss.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(ENRAGE_SPEED)) {
            speed.addPermanentModifier(new AttributeModifier(ENRAGE_SPEED, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        bossEvent.setColor(BossEvent.BossBarColor.RED);
        boss.playSound(SoundEvents.RAVAGER_ROAR, 2f, 0.6f);
        if (boss.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, boss.getX(), boss.getY() + 1.5, boss.getZ(), 60, 0.8, 1.2, 0.8, 0.05);
        }
        message("message.heroicpaths." + id + "_enraged", ChatFormatting.DARK_RED);
    }

    public void onDeath() {
        message("message.heroicpaths." + id + "_defeated", ChatFormatting.GOLD);
    }

    private void message(String key, ChatFormatting color) {
        for (ServerPlayer player : bossEvent.getPlayers()) {
            player.sendSystemMessage(Component.translatable(key).withStyle(color));
        }
    }

    private void updatePlayers() {
        if (!(boss.level() instanceof ServerLevel level)) return;
        Set<ServerPlayer> nearby = new HashSet<>(level.getPlayers(player ->
                player.distanceToSqr(boss) < BAR_RANGE * BAR_RANGE && Math.abs(player.getY() - boss.getY()) < 16));
        for (ServerPlayer player : List.copyOf(bossEvent.getPlayers())) {
            if (!nearby.contains(player)) bossEvent.removePlayer(player);
        }
        for (ServerPlayer player : nearby) bossEvent.addPlayer(player);
    }

    /** Призывает до max стражей вокруг босса. */
    public int summon(EntityType<? extends Mob> type, int count, int max, Consumer<Mob> setup) {
        if (!(boss.level() instanceof ServerLevel level)) return 0;
        int existing = level.getEntitiesOfClass(Mob.class, boss.getBoundingBox().inflate(20),
                mob -> mob.getTags().contains(MINION_TAG)).size();
        int spawned = 0;
        for (int i = 0; i < Math.min(count, max - existing); i++) {
            Mob minion = type.create(level);
            if (minion == null || !placeNearby(level, minion)) continue;
            minion.finalizeSpawn(level, level.getCurrentDifficultyAt(minion.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            minion.addTag(MINION_TAG);
            for (EquipmentSlot slot : EquipmentSlot.values()) minion.setDropChance(slot, 0f);
            setup.accept(minion);
            if (boss.getTarget() != null) minion.setTarget(boss.getTarget());
            level.addFreshEntity(minion);
            level.sendParticles(ParticleTypes.SOUL, minion.getX(), minion.getY() + 1, minion.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
            spawned++;
        }
        if (spawned > 0) boss.playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.5f, 0.7f);
        return spawned;
    }

    private boolean placeNearby(ServerLevel level, Mob mob) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = boss.getRandom().nextDouble() * Math.PI * 2;
            double distance = 2.5 + boss.getRandom().nextDouble();
            mob.moveTo(boss.getX() + Math.cos(angle) * distance, boss.getY(), boss.getZ() + Math.sin(angle) * distance,
                    boss.getRandom().nextFloat() * 360f, 0f);
            if (level.noCollision(mob)) return true;
        }
        return false;
    }

    /** Игроки в радиусе волны (без зрителей и творческого режима). */
    public List<Player> playersInRadius(double radius) {
        return boss.level().getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(radius),
                player -> !player.isSpectator() && !player.isCreative() && player.distanceToSqr(boss) < radius * radius);
    }

    public static void equip(Mob mob, EquipmentSlot slot, ItemStack stack) {
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, 0f);
    }

    public void save(CompoundTag tag) {
        tag.putBoolean("Enraged", enraged);
    }

    public void load(CompoundTag tag) {
        enraged = tag.getBoolean("Enraged");
        if (enraged) bossEvent.setColor(BossEvent.BossBarColor.RED);
        bossEvent.setName(boss.getDisplayName());
    }
}
