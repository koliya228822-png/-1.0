package com.heroicpaths.accessory;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Locale;

/**
 * Вид аксессуара. Перчатки, пояс и наручи делаются под тип брони (латы, кожа, ткань) и входят в сет вместе с броней;
 * ожерелье, серьги и кольца — украшения ювелира для всех классов, в сет не входят.
 */
public enum AccessoryKind {
    GLOVES, BELT, BRACERS, NECKLACE, EARRING, RING;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Часть сета брони. */
    public boolean isSetPiece() {
        return this == GLOVES || this == BELT || this == BRACERS;
    }

    public MutableComponent displayName() {
        return Component.translatable("accessory.heroicpaths." + id());
    }
}
