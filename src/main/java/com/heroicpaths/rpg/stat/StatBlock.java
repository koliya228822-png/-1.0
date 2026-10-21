package com.heroicpaths.rpg.stat;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Набор прибавок к характеристикам. */
public final class StatBlock {
    public static final StatBlock EMPTY = new StatBlock();

    private final EnumMap<Stat, Float> values = new EnumMap<>(Stat.class);

    /** StatBlock.of(Stat.MAX_HEALTH, 10, Stat.ARMOR, 2) */
    public static StatBlock of(Object... statValuePairs) {
        StatBlock block = new StatBlock();
        for (int i = 0; i < statValuePairs.length; i += 2) {
            block.add((Stat) statValuePairs[i], ((Number) statValuePairs[i + 1]).floatValue());
        }
        return block;
    }

    public StatBlock add(Stat stat, float value) {
        if (value != 0) values.merge(stat, value, Float::sum);
        return this;
    }

    public StatBlock addAll(StatBlock other) {
        other.values.forEach(this::add);
        return this;
    }

    /** Копия, где каждая прибавка умножена на factor. */
    public StatBlock scaled(float factor) {
        StatBlock block = new StatBlock();
        values.forEach((stat, value) -> block.add(stat, value * factor));
        return block;
    }

    public float get(Stat stat) {
        return values.getOrDefault(stat, 0f);
    }

    public void set(Stat stat, float value) {
        values.put(stat, value);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Map<Stat, Float> asMap() {
        return Collections.unmodifiableMap(values);
    }

    public List<Component> describe() {
        List<Component> lines = new ArrayList<>();
        values.forEach((stat, value) -> lines.add(stat.describe(value)));
        return lines;
    }
}
