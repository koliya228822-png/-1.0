package com.heroicpaths.dungeon;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Данжи, в которые ведут врата деревень. Сложность растёт от склепа к ледяным чертогам:
 * враги крепче и бьют сильнее, награда больше.
 */
public enum DungeonType implements net.minecraft.util.StringRepresentable {
    CRYPT(1, 1.0f, 1.0f, 300, 8, 1),
    SPIDER_LAIR(10, 1.4f, 1.2f, 600, 12, 1),
    FORGE(20, 1.8f, 1.4f, 900, 16, 2),
    SUNKEN_TEMPLE(30, 2.3f, 1.65f, 1400, 22, 2),
    FROST(40, 2.8f, 1.9f, 2000, 28, 3),
    NECROPOLIS(50, 3.4f, 2.2f, 3000, 36, 4);

    /** Прибавка к здоровью врагов за каждого игрока сверх первого. */
    public static final float HEALTH_PER_EXTRA_PLAYER = 0.3f;
    /** Прибавка к урону врагов за каждого игрока сверх первого. */
    public static final float DAMAGE_PER_EXTRA_PLAYER = 0.1f;
    public static final int MAX_PARTY = 6;
    /** С этого уровня данжа для входа нужен свиток, скрафченный из трофеев босса предыдущего данжа. */
    public static final int SCROLL_LEVEL = 20;

    private final int minLevel;
    private final float healthMultiplier;
    private final float damageMultiplier;
    private final int rewardXp;
    private final int rewardEmeralds;
    private final int rewardTomes;

    DungeonType(int minLevel, float healthMultiplier, float damageMultiplier, int rewardXp, int rewardEmeralds, int rewardTomes) {
        this.minLevel = minLevel;
        this.healthMultiplier = healthMultiplier;
        this.damageMultiplier = damageMultiplier;
        this.rewardXp = rewardXp;
        this.rewardEmeralds = rewardEmeralds;
        this.rewardTomes = rewardTomes;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Override
    public String getSerializedName() {
        return id();
    }


    /**
     * Папка шаблонов и пулов данжа. Первый данж за вратами — Глиняные копи (id остался «crypt», чтобы старые
     * сохранения, контракты и заказы гильдии не сломались); склеп в мире со своим Королём Склепа остался прежним.
     */
    /** Этажей с монстрами; под последним — арена босса. В Ледяной цитадели их шесть, в остальных данжах — пять. */
    public int floors() {
        return this == FROST ? 6 : 5;
    }

    public String theme() {
        return this == CRYPT ? "clay" : id();
    }

    /** Босс данжа (его id в шаблоне арены). */
    public String bossId() {
        return switch (this) {
            case CRYPT -> "clay_colossus";
            case SPIDER_LAIR -> "spider_queen";
            case FORGE -> "flame_tyrant";
            case SUNKEN_TEMPLE -> "drowned_prophet";
            case FROST -> "frost_lich";
            case NECROPOLIS -> "lich";
        };
    }

    public int minLevel() {
        return minLevel;
    }

    public float damageMultiplier() {
        return damageMultiplier;
    }

    public int rewardXp() {
        return rewardXp;
    }

    public int rewardEmeralds() {
        return rewardEmeralds;
    }

    public int rewardTomes() {
        return rewardTomes;
    }

    /** Во сколько раз здоровье врагов больше обычного: сложность данжа × размер группы. */
    public float healthMultiplier(int partySize) {
        return healthMultiplier * (1f + HEALTH_PER_EXTRA_PLAYER * (Math.max(1, partySize) - 1));
    }

    /** Во сколько раз урон врагов больше обычного: сложность данжа × размер группы. */
    public float damageMultiplier(int partySize) {
        return damageMultiplier * (1f + DAMAGE_PER_EXTRA_PLAYER * (Math.max(1, partySize) - 1));
    }

    /** Сколько монстров появляется на месте каждого спавнера первого этажа: 2 и ещё по одному за каждого игрока сверх первого. */
    public int mobsPerSpawner(int partySize) {
        return 2 + Math.max(1, partySize) - 1;
    }

    /** Чем глубже этаж, тем больше монстров: второй этаж вдвое людней первого, дальше ещё больше. */
    private static final float[] FLOOR_CROWD = {1f, 2f, 2.5f, 3f, 3.5f, 4f};

    public int mobsPerSpawner(int partySize, int floor) {
        return Math.round(mobsPerSpawner(partySize) * FLOOR_CROWD[Math.max(1, Math.min(FLOOR_CROWD.length, floor)) - 1]);
    }

    /**
     * Кто выходит из спавнера на этаже. В Глиняных копях состав зависит от этажа: на 1–2 только глиняные големы,
     * на 3-м половина — метатели, с 4-го каждый пятый — голем-танк, остальные поровну ближние и дальние.
     * Ледяную цитадель заселяет нежить ({@link #undeadFloorMob}). В других данжах — монстр, записанный в спавнере.
     * number — порядковый номер монстра на этаже.
     */
    public EntityType<?> floorMob(int floor, int number, EntityType<?> original) {
        return switch (this) {
            case CRYPT -> {
                if (floor >= 4 && number % 5 == 0) yield ModEntities.CLAY_BRUTE.get();
                if (floor >= 3 && number % 2 == 1) yield ModEntities.CLAY_THROWER.get();
                yield ModEntities.CLAY_GOLEM.get();
            }
            case FROST -> undeadFloorMob(floor, number);
            default -> original;
        };
    }

    /**
     * Нежить Ледяной цитадели (по мотивам Плети из Warcraft): с каждым этажом добавляется новый вид.
     * Этаж 1 — только чумные зомби; 2 — пополам зомби и вурдалаки; 3 — 40% зомби, 30% вурдалаков, 30% банши;
     * с 4-го — 20% лоскутов плоти, 20% зомби, 30% вурдалаков и 30% банши.
     */
    private static EntityType<?> undeadFloorMob(int floor, int number) {
        int slot = Math.floorMod(number, 10);
        if (floor <= 1) return ModEntities.UNDEAD_ZOMBIE.get();
        if (floor == 2) return slot % 2 == 0 ? ModEntities.UNDEAD_ZOMBIE.get() : ModEntities.GHOUL.get();
        if (floor == 3) return slot < 4 ? ModEntities.UNDEAD_ZOMBIE.get() : slot < 7 ? ModEntities.GHOUL.get() : ModEntities.BANSHEE.get();
        if (slot < 2) return ModEntities.ABOMINATION.get();
        return slot < 4 ? ModEntities.UNDEAD_ZOMBIE.get() : slot < 7 ? ModEntities.GHOUL.get() : ModEntities.BANSHEE.get();
    }

    public boolean requiresScroll() {
        return minLevel >= SCROLL_LEVEL;
    }

    /** Свиток доступа в данж или null, если он не нужен. */
    @Nullable
    public Item scroll() {
        return switch (this) {
            case CRYPT, SPIDER_LAIR -> null;
            case FORGE -> ModItems.FORGE_SCROLL.get();
            case SUNKEN_TEMPLE -> ModItems.TEMPLE_SCROLL.get();
            case FROST -> ModItems.FROST_SCROLL.get();
            case NECROPOLIS -> ModItems.NECROPOLIS_SCROLL.get();
        };
    }

    /**
     * Трофей босса: из трёх трофеев крафтится свиток данжа на ступень выше через один
     * (склеп → кузня, логово → храм, кузня → чертоги, храм → некрополь).
     */
    public Item trophy() {
        return switch (this) {
            case CRYPT -> ModItems.CRYPT_CROWN_SHARD.get();
            case SPIDER_LAIR -> ModItems.BROODMOTHER_FANG.get();
            case FORGE -> ModItems.TYRANT_EMBER_CORE.get();
            case SUNKEN_TEMPLE -> ModItems.TIDE_PEARL.get();
            case FROST -> ModItems.FROST_HEART.get();
            case NECROPOLIS -> ModItems.LICH_PHYLACTERY.get();
        };
    }

    /** Шанс, что босс оставит каждому вошедшему Искру реликвии: от 8% в склепе до 30% в некрополе. */
    public float relicSparkChance() {
        return switch (this) {
            case CRYPT -> 0.08f;
            case SPIDER_LAIR -> 0.10f;
            case FORGE -> 0.15f;
            case SUNKEN_TEMPLE -> 0.20f;
            case FROST -> 0.25f;
            case NECROPOLIS -> 0.30f;
        };
    }

    /** Пул стартовой комнаты первого этажа (зал прибытия с запечатанными вратами). */
    public ResourceLocation startPool() {
        return HeroicPaths.id(theme() + "/arrival");
    }

    /** Пул площадки спуска, с которой начинаются этажи 2–5. */
    public ResourceLocation landingPool() {
        return HeroicPaths.id(theme() + "/landing");
    }

    /** Пул арены босса — шестого этажа. */
    public ResourceLocation arenaPool() {
        return HeroicPaths.id(theme() + "/arena");
    }

    public MutableComponent displayName() {
        return Component.translatable("dungeon.heroicpaths." + id());
    }

    public MutableComponent description() {
        return Component.translatable("dungeon.heroicpaths." + id() + ".desc");
    }

    @Nullable
    public static DungeonType byId(String id) {
        for (DungeonType type : values()) {
            if (type.id().equals(id)) return type;
        }
        return null;
    }
}
