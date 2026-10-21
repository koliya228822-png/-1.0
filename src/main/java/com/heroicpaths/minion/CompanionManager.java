package com.heroicpaths.minion;

import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.WolfVariants;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Зверь охотника. Один на охотника; растёт вместе с ним: пёс → волк (15 ур.) → кабан (30) → медведь (50)
 * → совомедведь (75). Живёт, пока хозяин в сети, и переходит за ним между мирами.
 * Погибший зверь возвращается через минуту.
 */
public final class CompanionManager {
    public static final int DOWN_TICKS = 1200;

    public enum Stage {
        DOG(1, 20, 4, ModEntities.COMPANION_DOG::get),
        WOLF(15, 30, 6, ModEntities.COMPANION_WOLF::get),
        BOAR(30, 45, 8, ModEntities.COMPANION_BOAR::get),
        BEAR(50, 70, 11, ModEntities.COMPANION_BEAR::get),
        OWLBEAR(75, 110, 15, ModEntities.COMPANION_OWLBEAR::get);

        private final int minLevel;
        private final float health;
        private final float damage;
        private final Supplier<? extends EntityType<? extends Mob>> type;

        Stage(int minLevel, float health, float damage, Supplier<? extends EntityType<? extends Mob>> type) {
            this.minLevel = minLevel;
            this.health = health;
            this.damage = damage;
            this.type = type;
        }

        public int minLevel() {
            return minLevel;
        }

        public EntityType<? extends Mob> type() {
            return type.get();
        }

        public Component displayName() {
            return Component.translatable("companion.heroicpaths." + name().toLowerCase(Locale.ROOT));
        }

        public static Stage forLevel(int level) {
            Stage result = DOG;
            for (Stage stage : values()) if (level >= stage.minLevel) result = stage;
            return result;
        }

        @Nullable
        public static Stage of(Entity entity) {
            for (Stage stage : values()) if (entity.getType() == stage.type()) return stage;
            return null;
        }
    }

    private CompanionManager() {}

    @Nullable
    public static Mob find(ServerPlayer player) {
        RpgData data = RpgManager.get(player);
        if (data.getCompanion() == null) return null;
        Entity entity = player.serverLevel().getEntity(data.getCompanion());
        return entity instanceof Mob mob && mob.isAlive() && entity instanceof Minion ? mob : null;
    }

    /** Умение «Призвать зверя»: призывает зверя или зовёт уже призванного к себе и подлечивает. */
    public static boolean call(ServerPlayer player) {
        RpgData data = RpgManager.get(player);
        long now = player.serverLevel().getGameTime();
        if (now < data.getCompanionDownUntil()) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.companion_down",
                    (data.getCompanionDownUntil() - now + 19) / 20).withStyle(ChatFormatting.RED), true);
            return false;
        }
        Mob existing = find(player);
        if (existing != null) {
            existing.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0f);
            existing.getNavigation().stop();
            existing.heal(existing.getMaxHealth() * 0.3f);
            effects(existing);
            return true;
        }
        Mob spawned = spawn(player, Stage.forLevel(data.getLevel()), 1f);
        if (spawned == null) return false;
        player.sendSystemMessage(Component.translatable("message.heroicpaths.companion_called", Stage.forLevel(data.getLevel()).displayName())
                .withStyle(ChatFormatting.GREEN));
        return true;
    }

    @Nullable
    private static Mob spawn(ServerPlayer player, Stage stage, float healthFraction) {
        ServerLevel level = player.serverLevel();
        Mob mob = stage.type().create(level);
        if (!(mob instanceof Minion minion)) return null;
        minion.minionData().setOwner(player.getUUID());
        minion.minionData().setCompanion(true);
        mob.moveTo(player.getX() + 1, player.getY(), player.getZ(), player.getYRot(), 0f);
        mob.setPersistenceRequired();
        if (mob instanceof CompanionWolf wolf) {
            level.registryAccess().registryOrThrow(Registries.WOLF_VARIANT)
                    .getHolder(stage == Stage.WOLF ? WolfVariants.BLACK : WolfVariants.PALE).ifPresent(wolf::setVariant);
        }
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(stage == Stage.WOLF ? 1.3 : stage == Stage.OWLBEAR ? 1.25 : 1.0);
        Minions.applyStats(mob, player, stage.health, stage.damage);
        mob.setHealth(mob.getMaxHealth() * Math.max(0.1f, Math.min(1f, healthFraction)));
        level.addFreshEntity(mob);
        RpgManager.get(player).setCompanion(mob.getUUID());
        RpgManager.sync(player);
        effects(mob);
        return mob;
    }

    private static void effects(Mob mob) {
        if (mob.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, mob.getX(), mob.getY() + mob.getBbHeight() / 2, mob.getZ(), 15, 0.5, 0.5, 0.5, 0);
            level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WOLF_HOWL, SoundSource.NEUTRAL, 0.6f, 1.2f);
        }
    }

    /** Убирает зверя (сброс класса). */
    public static void dismiss(ServerPlayer player) {
        Mob mob = find(player);
        if (mob != null) Minions.vanish(mob);
        RpgManager.get(player).setCompanion(null);
    }

    /** Уровень или харизма хозяина изменились: зверь растёт до новой ступени или просто становится сильнее. */
    public static void onOwnerLevelChanged(ServerPlayer player) {
        RpgData data = RpgManager.get(player);
        Mob mob = find(player);
        if (mob == null || data.getPlayerClass() != PlayerClass.HUNTER) return;
        Stage desired = Stage.forLevel(data.getLevel());
        Stage current = Stage.of(mob);
        if (current != desired) {
            float fraction = mob.getHealth() / mob.getMaxHealth();
            mob.discard();
            if (spawn(player, desired, fraction) != null) {
                player.sendSystemMessage(Component.translatable("message.heroicpaths.companion_evolved", desired.displayName())
                        .withStyle(ChatFormatting.GOLD));
            }
        } else {
            Minions.applyStats(mob, player, current.health, current.damage);
        }
    }

    /** Зверь возвращается к хозяину после входа в игру, возрождения и перехода в другой мир. */
    private static void restore(ServerPlayer player) {
        RpgData data = RpgManager.get(player);
        if (data.getCompanion() == null || data.getPlayerClass() != PlayerClass.HUNTER) return;
        Mob existing = find(player);
        if (existing != null) {
            existing.moveTo(player.getX() + 1, player.getY(), player.getZ(), player.getYRot(), 0f);
            return;
        }
        spawn(player, Stage.forLevel(data.getLevel()), 1f);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restore(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restore(player);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restore(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Mob mob = find(player);
        if (mob != null) mob.discard();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Minion minion) || !minion.minionData().isCompanion()) return;
        ServerPlayer owner = Minions.owner(event.getEntity());
        if (owner == null) return;
        RpgData data = RpgManager.get(owner);
        if (!event.getEntity().getUUID().equals(data.getCompanion())) return;
        data.setCompanion(null);
        data.setCompanionDownUntil(owner.serverLevel().getGameTime() + DOWN_TICKS);
        RpgManager.sync(owner);
        owner.sendSystemMessage(Component.translatable("message.heroicpaths.companion_fell", DOWN_TICKS / 20).withStyle(ChatFormatting.RED));
    }
}
