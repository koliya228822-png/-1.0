package com.heroicpaths.rpg;

import com.heroicpaths.item.EquipmentStats;
import com.heroicpaths.rpg.stat.Element;
import com.heroicpaths.rpg.stat.Keystone;
import com.heroicpaths.rpg.stat.Stat;
import com.heroicpaths.rpg.stat.StatBlock;
import com.heroicpaths.rpg.tree.PassiveTree;
import net.minecraft.world.entity.player.Player;

import java.util.Set;

/** Итоговые характеристики игрока: основные характеристики + подкласс + дерево + снаряжение + ключевые камни. */
public final class PlayerStats {
    /** Потолки, чтобы сборки не ломали игру. */
    public static final float MAX_DAMAGE_REDUCTION = 75f;
    public static final float MAX_COOLDOWN_REDUCTION = 40f;
    public static final float MAX_COST_REDUCTION = 50f;
    public static final float MAX_CRIT_CHANCE = 75f;
    public static final float MAX_TRADE_DISCOUNT = 30f;

    private PlayerStats() {}

    public static StatBlock get(Player player) {
        RpgData data = RpgManager.get(player);
        StatBlock cached = data.cachedStats();
        if (cached != null) return cached;
        StatBlock stats = compute(player, data);
        data.cacheStats(stats, PassiveTree.keystones(data.getAllocatedNodes()));
        return stats;
    }

    public static StatBlock compute(Player player, RpgData data) {
        StatBlock stats = new StatBlock();
        if (data.getPlayerClass() == PlayerClass.NONE) return stats;
        for (CoreStat core : CoreStat.values()) core.apply(data.getStat(core), stats);
        // Здоровье и ресурс растут с уровнем персонажа (в дереве узлов здоровья нет).
        int levels = data.getLevel() - 1;
        stats.add(Stat.MAX_HEALTH, data.getPlayerClass().healthPerLevel() * levels);
        stats.add(Stat.MAX_RESOURCE, data.getPlayerClass().resourcePerLevel() * levels);
        if (data.getSubclass() != null) stats.addAll(data.getSubclass().passive());
        PassiveTree.collectStats(data.getAllocatedNodes(), data.getPlayerClass(), stats);
        EquipmentStats.collect(player, data.getPlayerClass(), stats);
        EquipmentStats.collectTraits(player, stats);
        ArmorProficiency.collect(player, data, stats);
        // Пункты шансов и скоростей из дерева складываются с процентами тех же характеристик.
        fold(stats, Stat.FLAT_CRIT_CHANCE, Stat.CRIT_CHANCE);
        fold(stats, Stat.FLAT_DODGE_CHANCE, Stat.DODGE_CHANCE);
        fold(stats, Stat.FLAT_BLEED_CHANCE, Stat.BLEED_CHANCE);
        fold(stats, Stat.FLAT_STUN_CHANCE, Stat.STUN_CHANCE);
        fold(stats, Stat.FLAT_ATTACK_SPEED, Stat.ATTACK_SPEED);
        fold(stats, Stat.FLAT_MOVE_SPEED, Stat.MOVE_SPEED);
        fold(stats, Stat.FLAT_COOLDOWN_REDUCTION, Stat.COOLDOWN_REDUCTION);

        Set<Keystone> keystones = PassiveTree.keystones(data.getAllocatedNodes());
        if (keystones.contains(Keystone.GLASS_CANNON)) {
            stats.add(Stat.MELEE_DAMAGE, 25).add(Stat.BOW_DAMAGE, 25).add(Stat.ABILITY_DAMAGE, 25).add(Stat.MAX_HEALTH_PCT, -25);
        }
        if (keystones.contains(Keystone.BATTLE_TRANCE)) stats.add(Stat.ATTACK_SPEED, 15).add(Stat.MAX_RESOURCE_PCT, -20);
        if (keystones.contains(Keystone.BLOOD_RAGE)) stats.add(Stat.ABILITY_DAMAGE, 20);
        if (keystones.contains(Keystone.COLD_PRECISION)) {
            stats.set(Stat.CRIT_CHANCE, stats.get(Stat.CRIT_CHANCE) * 2);
            stats.add(Stat.CRIT_DAMAGE, -25);
        }
        if (keystones.contains(Keystone.AMBUSH)) stats.add(Stat.MAX_HEALTH_PCT, -10);
        if (keystones.contains(Keystone.PACK_BOND)) {
            stats.add(Stat.MINION_DAMAGE, 40).add(Stat.MINION_HEALTH, 40)
                    .add(Stat.MELEE_DAMAGE, -20).add(Stat.BOW_DAMAGE, -20).add(Stat.SPELL_DAMAGE, -20);
        }
        if (keystones.contains(Keystone.STEADY_HANDS)) stats.add(Stat.MOVE_SPEED, -10);
        if (keystones.contains(Keystone.VITAL_SURGE)) {
            stats.add(Stat.MELEE_DAMAGE, -10).add(Stat.BOW_DAMAGE, -10).add(Stat.SPELL_DAMAGE, -10).add(Stat.MINION_DAMAGE, -10);
        }
        return stats;
    }

    private static void fold(StatBlock stats, Stat from, Stat into) {
        float value = stats.get(from);
        if (value == 0) return;
        stats.add(into, value);
        stats.set(from, 0);
    }

    /** Числовая прибавка дерева к удару умения: общая, по роду урона и по стихии. */
    public static float flatAbilityDamage(Player player, com.heroicpaths.rpg.ability.AbilityDef def) {
        StatBlock stats = get(player);
        float flat = stats.get(Stat.FLAT_ABILITY_DAMAGE) + switch (def.kind()) {
            case SPELL -> stats.get(Stat.FLAT_SPELL_DAMAGE);
            case MELEE -> stats.get(Stat.FLAT_MELEE_DAMAGE);
            case RANGED -> stats.get(Stat.FLAT_BOW_DAMAGE);
            case MINION -> stats.get(Stat.FLAT_MINION_DAMAGE);
        };
        return flat + flatElementDamage(player, def.element());
    }

    /** Числовая прибавка стихии. С «Гармонией стихий» любая из четырёх стихий получает лучшую из их прибавок. */
    public static float flatElementDamage(Player player, Element element) {
        StatBlock stats = get(player);
        Stat stat = switch (element) {
            case FIRE -> Stat.FLAT_FIRE_DAMAGE;
            case WATER -> Stat.FLAT_WATER_DAMAGE;
            case EARTH -> Stat.FLAT_EARTH_DAMAGE;
            case AIR -> Stat.FLAT_AIR_DAMAGE;
            case POISON -> Stat.FLAT_POISON_DAMAGE;
            default -> null;
        };
        if (stat == null) return 0;
        if (element.isClassical() && has(player, Keystone.ELEMENTAL_HARMONY)) {
            return Math.max(Math.max(stats.get(Stat.FLAT_FIRE_DAMAGE), stats.get(Stat.FLAT_WATER_DAMAGE)),
                    Math.max(stats.get(Stat.FLAT_EARTH_DAMAGE), stats.get(Stat.FLAT_AIR_DAMAGE)));
        }
        return stats.get(stat);
    }

    /** Критический удар: урон × множитель крита плюс числовая прибавка дерева. */
    public static float applyCrit(Player player, float amount) {
        return amount * critMultiplier(player) + get(player).get(Stat.FLAT_CRIT_DAMAGE);
    }

    public static final float MAX_DODGE_CHANCE = 40f;

    /** Шанс полностью увернуться от удара или снаряда, в процентах. */
    public static float dodgeChance(Player player) {
        return Math.min(MAX_DODGE_CHANCE, Math.max(0, get(player).get(Stat.DODGE_CHANCE)));
    }

    public static boolean has(Player player, Keystone keystone) {
        return RpgManager.get(player).keystones().contains(keystone);
    }

    /** Умения стоят здоровья, а не ресурса: маг крови и ключевой камень «Кровавая ярость». */
    public static boolean paysWithHealth(Player player) {
        RpgData data = RpgManager.get(player);
        return (data.getSubclass() != null && data.getSubclass().usesHealth()) || has(player, Keystone.BLOOD_RAGE);
    }

    /** Сколько здоровья стоит умение, если платить кровью: 1 здоровья за 4 единицы ресурса. */
    public static float healthCost(int cost) {
        return cost / 4f;
    }

    /** Бонус стихии. С «Гармонией стихий» любая из четырёх стихий получает лучший из их бонусов. */
    public static float elementBonus(Player player, Element element) {
        StatBlock stats = get(player);
        if (element.bonusStat() == null) return 0;
        if (element.isClassical() && has(player, Keystone.ELEMENTAL_HARMONY)) {
            return Math.max(Math.max(stats.get(Stat.FIRE_DAMAGE), stats.get(Stat.WATER_DAMAGE)),
                    Math.max(stats.get(Stat.EARTH_DAMAGE), stats.get(Stat.AIR_DAMAGE)));
        }
        return stats.get(element.bonusStat());
    }

    public static float maxResource(Player player) {
        RpgData data = RpgManager.get(player);
        if (data.getPlayerClass() == PlayerClass.NONE) return 100f;
        StatBlock stats = get(player);
        return Math.max(10f, (100f + stats.get(Stat.MAX_RESOURCE)) * (1f + stats.get(Stat.MAX_RESOURCE_PCT) / 100f));
    }

    public static float resourceRegen(Player player) {
        RpgData data = RpgManager.get(player);
        return data.getPlayerClass().resourceRegen() + get(player).get(Stat.RESOURCE_REGEN);
    }

    public static float critChance(Player player) {
        return Math.min(MAX_CRIT_CHANCE, 5f + get(player).get(Stat.CRIT_CHANCE) + ClassTraits.critBonus(player));
    }

    public static float critMultiplier(Player player) {
        return Math.max(1.1f, 1.5f + get(player).get(Stat.CRIT_DAMAGE) / 100f);
    }

    public static float cooldownFactor(Player player) {
        return 1f - Math.min(MAX_COOLDOWN_REDUCTION, get(player).get(Stat.COOLDOWN_REDUCTION)) / 100f;
    }

    public static float costFactor(Player player) {
        return 1f - Math.min(MAX_COST_REDUCTION, get(player).get(Stat.COST_REDUCTION)) / 100f;
    }

    public static float damageTakenFactor(Player player) {
        return 1f - Math.min(MAX_DAMAGE_REDUCTION, Math.max(0, get(player).get(Stat.DAMAGE_REDUCTION))) / 100f;
    }

    public static float healingFactor(Player player) {
        return 1f + get(player).get(Stat.HEALING) / 100f;
    }

    /** Множитель силы спутников и призванных существ. */
    public static float minionDamageFactor(Player player) {
        return 1f + get(player).get(Stat.MINION_DAMAGE) / 100f;
    }

    public static float minionHealthFactor(Player player) {
        return 1f + get(player).get(Stat.MINION_HEALTH) / 100f;
    }

    /** Скидка у всех торговцев от харизмы, в процентах. */
    public static float tradeDiscount(Player player) {
        return Math.min(MAX_TRADE_DISCOUNT, Math.max(0, get(player).get(Stat.TRADE_DISCOUNT) - CoreStat.BASE));
    }
}
