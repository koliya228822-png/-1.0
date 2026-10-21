package com.heroicpaths.rpg.stat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/** Ключевые камни дерева: меняют правила игры, а не просто прибавляют цифры. */
public enum Keystone {
    /** Способности тратят здоровье вместо ресурса (1 здоровья за 5 ресурса), +20% к урону способностей. */
    BLOOD_RAGE,
    /** Шанс крита из дерева удваивается, крит. урон −25%. */
    COLD_PRECISION,
    /** Все стихийные бонусы равны лучшему из четырёх. */
    ELEMENTAL_HARMONY,
    /** +15% к скорости атаки, −20% к максимуму ресурса. */
    BATTLE_TRANCE,
    /** +25% ко всему урону, −25% к максимальному здоровью. */
    GLASS_CANNON,
    /** Бонусы к урону заклинаний действуют и на атаки в ближнем бою. */
    IRON_WILL,
    /** Разбойник: +40% урона по целям с почти полным здоровьем, −10% к максимальному здоровью. */
    AMBUSH,
    /** Охотник: +40% к урону и здоровью спутников, −20% к собственному урону. */
    PACK_BOND,
    /** Мост лучника и охотника: каждое убийство лечит на 8% здоровья, −10% к урону. */
    VITAL_SURGE,
    /** Мост охотника и мага: 30% получаемого урона снимается с ресурса (2 ресурса за единицу урона). */
    ARCANE_SHIELD;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MutableComponent displayName() {
        return Component.translatable("keystone.heroicpaths." + id());
    }

    public MutableComponent description() {
        return Component.translatable("keystone.heroicpaths." + id() + ".desc");
    }
}
