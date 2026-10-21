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
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Король Склепа — босс данжа. Огромный скелет-иссушитель в золотой короне.
 * Фаза 1: ближний бой и призыв стражей-зомби.
 * Фаза 2 (меньше половины здоровья): ускоряется, чаще призывает, бьёт волной тьмы и стреляет огненными шарами.
 */
public class CryptKing extends WitherSkeleton implements DungeonBoss {
    public static final String MINION_TAG = HeroicPaths.MODID + ".crypt_minion";
    private static final ResourceLocation ENRAGE_SPEED = HeroicPaths.id("crypt_king_enrage");
    private static final int MAX_MINIONS = 6;
    private static final double BOSS_BAR_RANGE = 32;

    private final ServerBossEvent bossEvent =
            new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private boolean enraged;
    private int summonCooldown = 100;
    private int novaCooldown = 120;
    private int boltCooldown = 60;

    public CryptKing(EntityType<? extends CryptKing> type, Level level) {
        super(type, level);
        this.xpReward = 100;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
        for (EquipmentSlot slot : EquipmentSlot.values()) setDropChance(slot, 0f);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        @Nullable SpawnGroupData spawnGroupData) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
        // WitherSkeleton сбрасывает урон до 4 — возвращаем урон босса.
        getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(12.0);
        setCanPickUpLoot(false);
        restrictTo(blockPosition(), 16);
        return data;
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossEvent.setProgress(getHealth() / getMaxHealth());
        if (tickCount % 10 == 0) updateBossBarPlayers();
        updatePhase();

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        if (--summonCooldown <= 0) {
            summonMinions();
            summonCooldown = enraged ? 140 : 220;
        }
        if (!enraged) return;
        double distanceSqr = distanceToSqr(target);
        if (--novaCooldown <= 0 && distanceSqr < 36) {
            shadowNova();
            novaCooldown = 160;
        }
        if (--boltCooldown <= 0 && distanceSqr > 25 && hasLineOfSight(target)) {
            shootBolt(target);
            boltCooldown = 50;
        }
    }

    /** Переход во вторую фазу, когда здоровья меньше половины. */
    public void updatePhase() {
        if (enraged || getHealth() > getMaxHealth() / 2) return;
        enraged = true;
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(ENRAGE_SPEED)) {
            speed.addPermanentModifier(new AttributeModifier(ENRAGE_SPEED, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
        bossEvent.setColor(BossEvent.BossBarColor.RED);
        playSound(SoundEvents.RAVAGER_ROAR, 2f, 0.6f);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.5, getZ(), 60, 0.6, 1.0, 0.6, 0.05);
        }
        for (ServerPlayer player : bossEvent.getPlayers()) {
            player.sendSystemMessage(Component.translatable("message.heroicpaths.crypt_king_enraged").withStyle(ChatFormatting.DARK_PURPLE));
        }
    }

    public boolean isEnraged() {
        return enraged;
    }

    /** Призывает стражей склепа (зомби в кольчужных шлемах). Возвращает число призванных. */
    public int summonMinions() {
        if (!(level() instanceof ServerLevel level)) return 0;
        int existing = level.getEntitiesOfClass(Zombie.class, getBoundingBox().inflate(20),
                zombie -> zombie.getTags().contains(MINION_TAG)).size();
        int toSpawn = Math.min(enraged ? 3 : 2, MAX_MINIONS - existing);
        int spawned = 0;
        for (int i = 0; i < toSpawn; i++) {
            Zombie minion = EntityType.ZOMBIE.create(level);
            if (minion == null || !placeNearby(level, minion)) continue;
            minion.finalizeSpawn(level, level.getCurrentDifficultyAt(minion.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
            minion.setBaby(false);
            minion.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            minion.setDropChance(EquipmentSlot.HEAD, 0f);
            minion.setCustomName(Component.translatable("entity.heroicpaths.crypt_guard"));
            minion.addTag(MINION_TAG);
            if (getTarget() != null) minion.setTarget(getTarget());
            level.addFreshEntity(minion);
            level.sendParticles(ParticleTypes.SOUL, minion.getX(), minion.getY() + 1, minion.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
            spawned++;
        }
        if (spawned > 0) playSound(SoundEvents.EVOKER_PREPARE_SUMMON, 1.5f, 0.7f);
        return spawned;
    }

    /** Ищет свободное место в 2–3 блоках от короля (в логове мешают колонны). */
    private boolean placeNearby(ServerLevel level, Mob mob) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = getRandom().nextDouble() * Math.PI * 2;
            double distance = 2.0 + getRandom().nextDouble();
            mob.moveTo(getX() + Math.cos(angle) * distance, getY(), getZ() + Math.sin(angle) * distance,
                    getRandom().nextFloat() * 360f, 0f);
            if (level.noCollision(mob)) return true;
        }
        return false;
    }

    /** Волна тьмы: урон, замедление и отбрасывание всем игрокам в радиусе 6 блоков. */
    private void shadowNova() {
        if (!(level() instanceof ServerLevel level)) return;
        List<Player> players = level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(6),
                player -> !player.isSpectator() && !player.isCreative());
        for (Player player : players) {
            player.hurt(damageSources().indirectMagic(this, this), 8f);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
            Vec3 push = player.position().subtract(position()).normalize();
            player.knockback(1.2, -push.x, -push.z);
            player.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY() + 0.5, getZ(), 80, 3.0, 0.3, 3.0, 0.05);
        playSound(SoundEvents.EVOKER_CAST_SPELL, 2f, 0.5f);
    }

    /** Огненный шар в цель. Не взрывается и не ломает стены склепа. */
    private void shootBolt(LivingEntity target) {
        SpellProjectile bolt = new SpellProjectile(level(), this, 7f);
        Vec3 dir = new Vec3(target.getX() - getX(), target.getY(0.5) - bolt.getY(), target.getZ() - getZ());
        bolt.shoot(dir.x, dir.y, dir.z, 1.4f, 2f);
        level().addFreshEntity(bolt);
        playSound(SoundEvents.BLAZE_SHOOT, 1.5f, 0.6f);
    }

    /** Ни стражи, ни другие мобы не могут ранить короля — так они не дерутся между собой. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (DungeonBoss.ignoresDamage(source)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level().isClientSide) return;
        for (ServerPlayer player : bossEvent.getPlayers()) {
            player.sendSystemMessage(Component.translatable("message.heroicpaths.crypt_king_defeated").withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    private void updateBossBarPlayers() {
        if (!(level() instanceof ServerLevel level)) return;
        Set<ServerPlayer> nearby = new HashSet<>(level.getPlayers(player ->
                player.distanceToSqr(this) < BOSS_BAR_RANGE * BOSS_BAR_RANGE && Math.abs(player.getY() - getY()) < 16));
        for (ServerPlayer player : List.copyOf(bossEvent.getPlayers())) {
            if (!nearby.contains(player)) bossEvent.removePlayer(player);
        }
        for (ServerPlayer player : nearby) bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossEvent.removeAllPlayers();
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Enraged", enraged);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        enraged = tag.getBoolean("Enraged");
        if (enraged) bossEvent.setColor(BossEvent.BossBarColor.RED);
        if (hasCustomName()) bossEvent.setName(getDisplayName());
    }
}
