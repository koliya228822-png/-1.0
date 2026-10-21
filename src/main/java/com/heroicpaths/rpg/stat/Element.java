package com.heroicpaths.rpg.stat;

import org.jetbrains.annotations.Nullable;

/** Стихия урона способности. Определяет, какая характеристика его усиливает. */
public enum Element {
    PHYSICAL(null),
    FIRE(Stat.FIRE_DAMAGE),
    WATER(Stat.WATER_DAMAGE),
    EARTH(Stat.EARTH_DAMAGE),
    AIR(Stat.AIR_DAMAGE),
    HOLY(Stat.HOLY_DAMAGE),
    POISON(Stat.POISON_DAMAGE);

    private final Stat bonusStat;

    Element(@Nullable Stat bonusStat) {
        this.bonusStat = bonusStat;
    }

    @Nullable
    public Stat bonusStat() {
        return bonusStat;
    }

    public boolean isClassical() {
        return this == FIRE || this == WATER || this == EARTH || this == AIR;
    }
}
