package com.heroicpaths.rpg;

import com.heroicpaths.rpg.stat.Stat;
import com.heroicpaths.rpg.stat.StatBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * Основные характеристики персонажа в духе Baldur's Gate. На старте у каждой по {@link #BASE} очка,
 * каждый уровень даёт одно свободное очко. Каждое очко характеристики даёт небольшие бонусы.
 */
public enum CoreStat {
    /** Сила: урон в ближнем бою. */
    STRENGTH,
    /** Ловкость: урон стрелкового оружия, шанс крита, скорость атаки. */
    DEXTERITY,
    /** Телосложение: здоровье и снижение урона. */
    CONSTITUTION,
    /** Интеллект: урон заклинаний и запас ресурса. */
    INTELLIGENCE,
    /** Мудрость: лечение, восстановление ресурса, перезарядка. */
    WISDOM,
    /** Харизма: сила спутников и призванных существ, скидки у торговцев. */
    CHARISMA;

    public static final int BASE = 2;
    public static final int MAX = 50;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MutableComponent displayName() {
        return Component.translatable("core_stat.heroicpaths." + id());
    }

    public MutableComponent description() {
        return Component.translatable("core_stat.heroicpaths." + id() + ".desc");
    }

    /** Бонусы от значения характеристики. */
    public void apply(int value, StatBlock into) {
        switch (this) {
            case STRENGTH -> into.add(Stat.MELEE_DAMAGE, 2f * value);
            case DEXTERITY -> into.add(Stat.BOW_DAMAGE, 2f * value).add(Stat.CRIT_CHANCE, 0.3f * value).add(Stat.ATTACK_SPEED, 0.5f * value);
            case CONSTITUTION -> into.add(Stat.MAX_HEALTH, value).add(Stat.DAMAGE_REDUCTION, 0.2f * value);
            case INTELLIGENCE -> into.add(Stat.SPELL_DAMAGE, 2f * value).add(Stat.MAX_RESOURCE, 2f * value);
            case WISDOM -> into.add(Stat.HEALING, 2f * value).add(Stat.RESOURCE_REGEN, 0.1f * value).add(Stat.COOLDOWN_REDUCTION, 0.3f * value);
            case CHARISMA -> into.add(Stat.MINION_DAMAGE, 3f * value).add(Stat.MINION_HEALTH, 3f * value).add(Stat.TRADE_DISCOUNT, value);
        }
    }

    public static CoreStat byOrdinal(int ordinal) {
        CoreStat[] values = values();
        return values[Math.floorMod(ordinal, values.length)];
    }
}
