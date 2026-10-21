package com.heroicpaths.guild;

import com.heroicpaths.item.AbilityBookItem;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.heroicpaths.guild.OrderType.DELIVERY;
import static com.heroicpaths.guild.OrderType.DUNGEON;
import static com.heroicpaths.guild.OrderType.GATHER;
import static com.heroicpaths.guild.OrderType.HUNT;

/**
 * Каталог заказов и ежедневные предложения досок. Предложения не хранятся: они однозначно получаются
 * из сида мира, номера игрового дня и места доски, поэтому у каждой доски свой набор на каждый день.
 */
public final class OrderCatalog {
    /** Сколько заказов висит на доске: по два каждой ступени. */
    public static final int OFFERS = 6;
    private static final int[] OFFER_TIERS = {0, 0, 1, 1, 2, 2};
    private static final int[] BASE_XP = {150, 500, 1200};
    private static final int[] BASE_EMERALDS = {4, 10, 20};

    private record Entry(OrderType type, String target, String targetKey, String icon, int min, int max) {}

    private static Entry hunt(String entity, int min, int max) {
        return new Entry(HUNT, "minecraft:" + entity, "entity.minecraft." + entity, "minecraft:" + entity + "_spawn_egg", min, max);
    }

    private static Entry huntGroup(String tag, String icon, int min, int max) {
        return new Entry(HUNT, "#minecraft:" + tag, "order.heroicpaths.target." + tag, "minecraft:" + icon + "_spawn_egg", min, max);
    }

    private static Entry deliver(String item, int min, int max) {
        String namespace = item.contains(":") ? item.substring(0, item.indexOf(':')) : "minecraft";
        String path = item.contains(":") ? item.substring(item.indexOf(':') + 1) : item;
        return new Entry(DELIVERY, namespace + ":" + path, "item." + namespace + "." + path, namespace + ":" + path, min, max);
    }

    private static Entry deliverGroup(String tag, String targetName, String icon, int min, int max) {
        return new Entry(DELIVERY, "#" + tag, "order.heroicpaths.target." + targetName, icon, min, max);
    }

    private static Entry gather(String tag, String targetName, String icon, int min, int max) {
        return new Entry(GATHER, "#" + tag, "order.heroicpaths.target." + targetName, icon, min, max);
    }

    private static Entry dungeon(String id, int amount) {
        return new Entry(DUNGEON, id, "dungeon.heroicpaths." + id, "heroicpaths:gate_stone", amount, amount);
    }

    private static final List<List<Entry>> BY_TIER = List.of(
            List.of(huntGroup("zombies", "zombie", 8, 14), huntGroup("skeletons", "skeleton", 6, 12),
                    hunt("spider", 6, 10), hunt("creeper", 4, 8),
                    deliver("wheat", 24, 40), deliver("leather", 6, 12), deliverGroup("minecraft:wool", "wool", "minecraft:white_wool", 12, 20),
                    deliver("iron_ingot", 6, 12), deliver("bread", 10, 20),
                    gather("minecraft:coal_ores", "coal_ores", "minecraft:coal_ore", 12, 24),
                    gather("minecraft:iron_ores", "iron_ores", "minecraft:iron_ore", 8, 16),
                    gather("minecraft:logs", "logs", "minecraft:oak_log", 24, 48),
                    dungeon("crypt", 1)),
            List.of(hunt("enderman", 4, 8), hunt("witch", 2, 4), huntGroup("raiders", "pillager", 4, 8),
                    hunt("cave_spider", 6, 10), hunt("blaze", 4, 6),
                    deliver("heroicpaths:steel_plate", 4, 8), deliver("heroicpaths:hardened_leather", 4, 8),
                    deliver("heroicpaths:arcane_cloth", 4, 6), deliverGroup("heroicpaths:crystals", "crystals", "heroicpaths:fire_crystal", 4, 8),
                    deliver("gold_ingot", 6, 12),
                    gather("heroicpaths:crystal_ores", "crystal_ores", "heroicpaths:fire_crystal_ore", 6, 12),
                    gather("minecraft:gold_ores", "gold_ores", "minecraft:gold_ore", 6, 10),
                    gather("minecraft:redstone_ores", "redstone_ores", "minecraft:redstone_ore", 8, 14),
                    dungeon("crypt", 2), dungeon("spider_lair", 1)),
            List.of(hunt("wither_skeleton", 4, 8), hunt("ravager", 1, 2), hunt("evoker", 1, 3), hunt("ghast", 2, 4), hunt("guardian", 4, 8),
                    deliver("heroicpaths:damascus_ingot", 2, 4), deliver("heroicpaths:impregnated_leather", 2, 4),
                    deliver("heroicpaths:astral_silk", 2, 4), deliver("heroicpaths:sapphire", 2, 3), deliver("heroicpaths:ruby", 1, 2),
                    deliver("diamond", 2, 4),
                    gather("heroicpaths:gem_ores", "gem_ores", "heroicpaths:ruby_ore", 4, 8),
                    gather("minecraft:diamond_ores", "diamond_ores", "minecraft:diamond_ore", 3, 5),
                    dungeon("forge", 1), dungeon("sunken_temple", 1), dungeon("frost", 1)));

    private OrderCatalog() {}

    /** Заказы доски на этот день. */
    public static List<Order> offers(long worldSeed, long day, BlockPos board) {
        RandomSource random = RandomSource.create(worldSeed ^ (day * 0x9E3779B97F4A7C15L) ^ (board.asLong() * 31L));
        List<Order> result = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (int i = 0; i < OFFERS; i++) {
            int tier = OFFER_TIERS[i];
            List<Entry> pool = BY_TIER.get(tier);
            Entry entry = pool.get(random.nextInt(pool.size()));
            for (int attempt = 0; attempt < 10 && used.contains(entry.target()); attempt++) entry = pool.get(random.nextInt(pool.size()));
            used.add(entry.target());
            int amount = entry.min() + random.nextInt(entry.max() - entry.min() + 1);
            float difficulty = 0.8f + 0.4f * (amount - entry.min()) / (entry.max() - entry.min() + 1f);
            if (entry.type() == DUNGEON) difficulty *= 1.5f;
            int xp = Math.round(BASE_XP[tier] * difficulty);
            int emeralds = Math.max(1, Math.round(BASE_EMERALDS[tier] * difficulty));
            String id = "d" + day + ":" + board.asLong() + ":" + i;
            result.add(new Order(id, entry.type(), entry.target(), entry.targetKey(), entry.icon(), amount, tier, xp, emeralds,
                    bonus(tier, random), 0));
        }
        return result;
    }

    /** Дополнительная награда: эликсир у новичков, кристаллы и топазы у опытных, том опыта или книга умения у ветеранов. */
    private static ItemStack bonus(int tier, RandomSource random) {
        return switch (tier) {
            case 0 -> random.nextFloat() < 0.3f ? new ItemStack(ModItems.SPIRIT_ELIXIR.get(), 2) : ItemStack.EMPTY;
            case 1 -> {
                if (random.nextBoolean()) yield new ItemStack(ModItems.TOPAZ.get(), 2);
                ItemStack[] crystals = {new ItemStack(ModItems.FIRE_CRYSTAL.get(), 4), new ItemStack(ModItems.FROST_CRYSTAL.get(), 4),
                        new ItemStack(ModItems.EARTH_CRYSTAL.get(), 4), new ItemStack(ModItems.STORM_CRYSTAL.get(), 4)};
                yield crystals[random.nextInt(crystals.length)];
            }
            default -> {
                List<AbilityDef> books = AbilityRegistry.bookAbilities(100);
                if (random.nextFloat() < 0.4f && !books.isEmpty()) {
                    yield AbilityBookItem.create(ModItems.ABILITY_BOOK.get(), books.get(random.nextInt(books.size())));
                }
                yield new ItemStack(ModItems.EXPERIENCE_TOME.get());
            }
        };
    }
}
