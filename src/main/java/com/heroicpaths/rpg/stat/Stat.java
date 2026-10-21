package com.heroicpaths.rpg.stat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/** Характеристики персонажа, которые дают узлы дерева, специализации и снаряжение. */
public enum Stat {
    MAX_HEALTH(false),
    MAX_HEALTH_PCT(true),
    ARMOR(false),
    DAMAGE_REDUCTION(true),
    MOVE_SPEED(true),
    ATTACK_SPEED(true),
    MELEE_DAMAGE(true),
    SWORD_DAMAGE(true),
    AXE_DAMAGE(true),
    BOW_DAMAGE(true),
    ABILITY_DAMAGE(true),
    SPELL_DAMAGE(true),
    FIRE_DAMAGE(true),
    WATER_DAMAGE(true),
    EARTH_DAMAGE(true),
    AIR_DAMAGE(true),
    HOLY_DAMAGE(true),
    POISON_DAMAGE(true),
    CRIT_CHANCE(true),
    CRIT_DAMAGE(true),
    LIFE_STEAL(true),
    MAX_RESOURCE(false),
    MAX_RESOURCE_PCT(true),
    RESOURCE_REGEN(false),
    COOLDOWN_REDUCTION(true),
    COST_REDUCTION(true),
    HEALING(true),
    HEALTH_REGEN(false),
    THORNS(false),
    XP_GAIN(true),
    MINION_DAMAGE(true),
    MINION_HEALTH(true),
    TRADE_DISCOUNT(true),
    // Этап D: характеристики, завязанные на типы оружия и стиль класса
    BLEED_CHANCE(true),
    STUN_CHANCE(true),
    ARMOR_PIERCE(true),
    DUAL_WIELD_DAMAGE(true),
    DODGE_CHANCE(true),
    // Числовые прибавки дерева (0.3–1 за узел): урон — к каждому удару своего типа, остальное — своим числом.
    FLAT_MELEE_DAMAGE(false),
    FLAT_SWORD_DAMAGE(false),
    FLAT_AXE_DAMAGE(false),
    FLAT_BOW_DAMAGE(false),
    FLAT_SPELL_DAMAGE(false),
    FLAT_ABILITY_DAMAGE(false),
    FLAT_FIRE_DAMAGE(false),
    FLAT_WATER_DAMAGE(false),
    FLAT_EARTH_DAMAGE(false),
    FLAT_AIR_DAMAGE(false),
    FLAT_POISON_DAMAGE(false),
    FLAT_MINION_DAMAGE(false),
    FLAT_MINION_HEALTH(false),
    FLAT_DUAL_WIELD_DAMAGE(false),
    FLAT_CRIT_DAMAGE(false),
    FLAT_LIFE_STEAL(false),
    FLAT_DAMAGE_REDUCTION(false),
    FLAT_HEALING(false),
    FLAT_COST_REDUCTION(false),
    FLAT_ARMOR_PIERCE(false),
    // Пункты шансов и скоростей из дерева: складываются с одноимёнными процентами (PlayerStats.compute).
    FLAT_CRIT_CHANCE(false),
    FLAT_DODGE_CHANCE(false),
    FLAT_BLEED_CHANCE(false),
    FLAT_STUN_CHANCE(false),
    FLAT_ATTACK_SPEED(false),
    FLAT_MOVE_SPEED(false),
    FLAT_COOLDOWN_REDUCTION(false);

    private final boolean percent;

    Stat(boolean percent) {
        this.percent = percent;
    }

    public boolean isPercent() {
        return percent;
    }

    public String translationKey() {
        return "stat.heroicpaths." + name().toLowerCase(Locale.ROOT);
    }

    /** «+12% к урону мечом» или «+10 к максимальному здоровью». */
    public MutableComponent describe(float value) {
        return Component.translatable(translationKey(), format(value));
    }

    public static String format(float value) {
        String number = value == Math.round(value)
                ? String.valueOf(Math.round(value))
                : new java.math.BigDecimal(String.format(Locale.ROOT, "%.2f", value)).stripTrailingZeros().toPlainString();
        return value >= 0 ? "+" + number : number;
    }
}
