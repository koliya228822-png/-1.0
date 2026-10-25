package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.dungeon.GateKey;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Компоненты данных предметов мода. */
public final class ModComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, HeroicPaths.MODID);

    /** Старое качество (0 — обычное … 3 — легендарное): только для перевода старых вещей в редкость. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> QUALITY =
            COMPONENTS.registerComponentType("quality", builder -> builder
                    .persistent(Codec.intRange(0, 3))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Редкость предмета: 0 — мусор … 7 — реликвия (см. ItemRarity.Tier). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RARITY =
            COMPONENTS.registerComponentType("rarity", builder -> builder
                    .persistent(Codec.intRange(0, 7))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Уникальные бонусы предметов редкой редкости и выше. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.List<com.heroicpaths.item.ItemTrait.Roll>>> TRAITS =
            COMPONENTS.registerComponentType("traits", builder -> builder
                    .persistent(com.heroicpaths.item.ItemTrait.Roll.CODEC.listOf())
                    .networkSynchronized(com.heroicpaths.item.ItemTrait.Roll.STREAM_CODEC.apply(ByteBufCodecs.list())));

    /** Ключ Камня врат: данж и размер зарегистрированной группы. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GateKey>> GATE_KEY =
            COMPONENTS.registerComponentType("gate_key", builder -> builder
                    .persistent(GateKey.CODEC)
                    .networkSynchronized(GateKey.STREAM_CODEC));

    /** Умение, которому учит книга. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> ABILITY =
            COMPONENTS.registerComponentType("ability", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** Ступень ремесла, до которой поднимает трактат: 2 — подмастерье … 5 — мастер. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MANUAL_RANK =
            COMPONENTS.registerComponentType("manual_rank", builder -> builder
                    .persistent(Codec.intRange(2, 5))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Души в камне души. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SOULS =
            COMPONENTS.registerComponentType("souls", builder -> builder
                    .persistent(Codec.intRange(0, 100000))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Грейд аксессуара (перчатки, пояс, наручи, украшения): 1 — I … 6 — VI. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GEAR_GRADE =
            COMPONENTS.registerComponentType("gear_grade", builder -> builder
                    .persistent(Codec.intRange(1, 6))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Комплект (сет), к которому принадлежит вещь, — id из GearSet. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> GEAR_SET =
            COMPONENTS.registerComponentType("gear_set", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private ModComponents() {}
}
