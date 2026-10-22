package com.heroicpaths.guild;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Виды заказов гильдии искателей приключений. */
public enum OrderType {
    /** Убить N существ (вида или группы видов). */
    HUNT,
    /** Принести N предметов и сдать их на доске. */
    DELIVERY,
    /** Добыть N блоков (руды, брёвна). */
    GATHER,
    /** Зачистить данж (убить его босса). */
    DUNGEON,
    /** Наказ глашатая гильдии: убить босса данжа (засчитывается всем игрокам у босса и их группам). */
    BOSS;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MutableComponent displayName() {
        return Component.translatable("order_type.heroicpaths." + id());
    }

    @Nullable
    public static OrderType byId(String id) {
        for (OrderType type : values()) if (type.id().equals(id)) return type;
        return null;
    }
}
