package com.heroicpaths.combat;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.dungeon.DungeonManager;
import com.heroicpaths.dungeon.DungeonType;
import com.heroicpaths.entity.DungeonBoss;
import com.heroicpaths.minion.Minion;
import com.heroicpaths.network.MobLevelPayload;
import com.heroicpaths.rpg.ability.AbilityLogic;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Уровни враждебных мобов (1–80). У каждого вида есть обычный уровень по его силе в ванилле; с шансом моб
 * появляется выше обычного, и чем больше разница, тем реже. Сила растёт пропорционально уровню:
 * зомби 10-го уровня (обычный 5-й) вдвое крепче и вдвое больнее бьёт, а ещё получает случайное сопротивление.
 * В данжах уровень задаёт сам данж и этаж, а силу — его собственное масштабирование.
 */
public final class MobLevels {
    private MobLevels() {}

    public static final int MAX_LEVEL = 80;
    /** Шанс появиться выше обычного уровня. */
    public static final float UPGRADE_CHANCE = 0.25f;
    /** Шанс подняться ещё на уровень после каждого шага: +1 — 25%, +5 — 10%, +10 — 3,4%, +20 — 0,4%. */
    public static final float CLIMB_CHANCE = 0.8f;
    /** Сопротивление при удвоенной силе; растёт с разницей уровней и не больше MAX_RESIST. */
    public static final float RESIST_PER_POWER = 0.25f, MAX_RESIST = 0.6f;
    /** Опыт за моба на 5+ уровней выше игрока и на 5+ уровней ниже. */
    public static final int LEVEL_GAP = 5;
    public static final float HIGHER_XP = 1.2f, LOWER_XP = 0.1f;

    private static final String LEVEL = "heroicpaths.mob_level", BASE = "heroicpaths.mob_base",
            POWER = "heroicpaths.mob_power", RESIST = "heroicpaths.mob_resist";
    private static final ResourceLocation HEALTH_LEVEL = HeroicPaths.id("mob_level");

    /** Сопротивление, которое моб получает, когда появился выше своего уровня. */
    public enum Resist {
        PHYSICAL, PROJECTILE, MAGIC, FIRE;

        public Component label() {
            return Component.translatable("hud.heroicpaths.resist." + name().toLowerCase(Locale.ROOT));
        }

        public boolean matches(DamageSource source) {
            boolean magic = AbilityLogic.isAbilityDamage() || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC);
            boolean fire = source.is(DamageTypeTags.IS_FIRE);
            boolean projectile = source.is(DamageTypeTags.IS_PROJECTILE);
            return switch (this) {
                case MAGIC -> magic;
                case FIRE -> fire && !magic;
                case PROJECTILE -> projectile && !magic && !fire;
                case PHYSICAL -> !magic && !fire && !projectile && source.getEntity() != null
                        && source.getDirectEntity() == source.getEntity();
            };
        }
    }

    /** Обычные уровни ванильных врагов по их силе. */
    private static final Map<EntityType<?>, Integer> BASE_LEVELS = new IdentityHashMap<>();

    static {
        base(1, EntityType.SILVERFISH);
        base(2, EntityType.ENDERMITE);
        base(3, EntityType.SLIME);
        base(4, EntityType.SPIDER);
        base(5, EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER);
        base(6, EntityType.SKELETON, EntityType.HUSK, EntityType.DROWNED, EntityType.CAVE_SPIDER);
        base(7, EntityType.CREEPER, EntityType.BOGGED);
        base(8, EntityType.STRAY, EntityType.PHANTOM, EntityType.MAGMA_CUBE);
        base(9, EntityType.PILLAGER);
        base(10, EntityType.WITCH, EntityType.VEX);
        base(12, EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN);
        base(14, EntityType.VINDICATOR);
        base(15, EntityType.GUARDIAN);
        base(16, EntityType.GHAST);
        base(18, EntityType.BLAZE, EntityType.HOGLIN, EntityType.EVOKER);
        base(20, EntityType.ENDERMAN, EntityType.ZOGLIN, EntityType.ILLUSIONER);
        base(22, EntityType.WITHER_SKELETON, EntityType.BREEZE);
        base(24, EntityType.PIGLIN_BRUTE);
        base(25, EntityType.RAVAGER);
        base(28, EntityType.SHULKER);
        base(30, EntityType.GIANT);
        base(35, EntityType.ELDER_GUARDIAN);
        base(60, EntityType.WARDEN);
        base(70, EntityType.WITHER);
        base(80, EntityType.ENDER_DRAGON);
    }

    private static void base(int level, EntityType<?>... types) {
        for (EntityType<?> type : types) BASE_LEVELS.put(type, level);
    }

    // ================================================================ чтение

    /** Уровень моба; 0 — у моба нет уровня (мирные, существа игроков, игроки). */
    public static int level(Entity entity) {
        return entity.getPersistentData().getInt(LEVEL);
    }

    public static int baseLevel(Entity entity) {
        return entity.getPersistentData().getInt(BASE);
    }

    /** Во сколько раз моб сильнее обычного для своего вида (1 — обычный). */
    public static float power(Entity entity) {
        CompoundTag tag = entity.getPersistentData();
        return tag.contains(POWER) ? tag.getFloat(POWER) : 1f;
    }

    public static Resist resist(Entity entity) {
        CompoundTag tag = entity.getPersistentData();
        if (!tag.contains(RESIST)) return null;
        int index = tag.getInt(RESIST);
        return index >= 0 && index < Resist.values().length ? Resist.values()[index] : null;
    }

    /** Доля урона, которую срезает сопротивление моба. */
    public static float resistAmount(float power) {
        return Math.min(MAX_RESIST, RESIST_PER_POWER * (power - 1));
    }

    /** Множитель опыта за убийство: +20% за моба на 5+ уровней выше игрока, 10% — за моба на 5+ уровней ниже. */
    public static float xpFactor(int playerLevel, int mobLevel) {
        if (mobLevel <= 0) return 1f;
        if (mobLevel - playerLevel >= LEVEL_GAP) return HIGHER_XP;
        if (playerLevel - mobLevel >= LEVEL_GAP) return LOWER_XP;
        return 1f;
    }

    public static boolean hasLevels(Entity entity) {
        if (!(entity instanceof Mob mob) || !(mob instanceof Enemy) || mob instanceof Minion) return false;
        return !(mob instanceof OwnableEntity ownable) || ownable.getOwnerUUID() == null;
    }

    private static boolean isBoss(Mob mob) {
        return mob instanceof DungeonBoss || mob instanceof WitherBoss || mob instanceof EnderDragon
                || mob instanceof ElderGuardian || mob instanceof Warden;
    }

    /** Обычный уровень вида; для монстров других модов — по здоровью и урону (зомби выходит 5-м). */
    public static int baseLevel(Mob mob) {
        Integer known = BASE_LEVELS.get(mob.getType());
        if (known != null) return known;
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        double strength = (health == null ? 20 : health.getBaseValue()) + 4 * (attack == null ? 2 : attack.getBaseValue());
        return Mth.clamp((int) Math.round(strength / 6), 1, MAX_LEVEL);
    }

    /** Уровень при появлении: обычный или выше, каждый следующий шаг вверх реже предыдущего. */
    public static int rollLevel(int base, RandomSource random) {
        int level = Math.min(base, MAX_LEVEL);
        if (level >= MAX_LEVEL || random.nextFloat() >= UPGRADE_CHANCE) return level;
        level++;
        while (level < MAX_LEVEL && random.nextFloat() < CLIMB_CHANCE) level++;
        return level;
    }

    // ================================================================ назначение

    /**
     * Ставит мобу уровень и сразу его силу: здоровье × (уровень / обычный), урон — в {@link #onIncomingDamage}.
     * В данже power = 1: силу там задаёт масштабирование данжа.
     */
    public static void assign(Mob mob, int base, int level, boolean scale) {
        level = Mth.clamp(level, 1, MAX_LEVEL);
        base = Mth.clamp(base, 1, level);
        float power = scale ? (float) level / base : 1f;
        CompoundTag tag = mob.getPersistentData();
        tag.putInt(LEVEL, level);
        tag.putInt(BASE, base);
        tag.putFloat(POWER, power);
        tag.remove(RESIST);
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.removeModifier(HEALTH_LEVEL);
            if (power > 1f) {
                health.addPermanentModifier(new AttributeModifier(HEALTH_LEVEL, power - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            mob.setHealth(mob.getMaxHealth());
        }
        if (power > 1f) tag.putInt(RESIST, mob.getRandom().nextInt(Resist.values().length));
        if (mob.level() instanceof ServerLevel server) {
            for (ServerPlayer player : server.getChunkSource().chunkMap.getPlayers(mob.chunkPosition(), false)) send(player, mob);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !hasLevels(event.getEntity())) return;
        Mob mob = (Mob) event.getEntity();
        if (mob.getPersistentData().contains(LEVEL)) return; // уже есть — моб загружен из сохранения
        if (DungeonManager.isDungeonLevel(level)) {
            DungeonType type = DungeonManager.typeAt(level, mob.blockPosition());
            if (type != null) {
                int dungeonLevel = dungeonLevel(type, mob.getBlockY(), isBoss(mob));
                assign(mob, dungeonLevel, dungeonLevel, false);
                return;
            }
        }
        int base = baseLevel(mob);
        // На тестовом сервере без случайности: остальные тесты рассчитывают на обычных мобов.
        boolean roll = !isBoss(mob) && !(level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer);
        assign(mob, base, roll ? rollLevel(base, mob.getRandom()) : base, true);
    }

    /** Уровень врагов в данже: уровень данжа + номер этажа − 1, на арене — после последнего этажа, у босса — ещё на 3 выше. */
    public static int dungeonLevel(DungeonType type, int y, boolean boss) {
        int floor = Mth.clamp(1 + Math.floorDiv(DungeonManager.TOP_Y + 8 - y, DungeonManager.FLOOR_STEP), 1, type.floors() + 1);
        int level = type.minLevel() + floor - 1;
        return Math.min(MAX_LEVEL, boss ? type.minLevel() + type.floors() + 3 : level);
    }

    /** Мобы с уровнем выше обычного бьют сильнее (любым способом: рукой, стрелой, взрывом, чарами) и держат удар лучше. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof Mob attacker && attacker != event.getEntity()) {
            float power = power(attacker);
            if (power > 1f) event.setAmount(event.getAmount() * power);
        }
        LivingEntity target = event.getEntity();
        Resist resist = resist(target);
        if (resist != null && resist.matches(source)) {
            event.setAmount(event.getAmount() * (1 - resistAmount(power(target))));
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && level(event.getTarget()) > 0) {
            send(player, event.getTarget());
        }
    }

    /** У фейковых игроков и клиентов без мода канала нет — отправка бросила бы исключение. */
    private static void send(ServerPlayer player, Entity entity) {
        if (player.connection == null || !player.connection.hasChannel(MobLevelPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player, payload(entity));
    }

    private static MobLevelPayload payload(Entity entity) {
        Resist resist = resist(entity);
        return new MobLevelPayload(entity.getId(), level(entity), baseLevel(entity), resist == null ? -1 : resist.ordinal());
    }

    /** Клиент: запоминает уровень в собственных данных сущности, их читает подсказка под прицелом. */
    public static void applyOnClient(Entity entity, MobLevelPayload payload) {
        CompoundTag tag = entity.getPersistentData();
        tag.putInt(LEVEL, payload.level());
        tag.putInt(BASE, payload.base());
        if (payload.resist() >= 0) tag.putInt(RESIST, payload.resist());
        else tag.remove(RESIST);
    }
}
