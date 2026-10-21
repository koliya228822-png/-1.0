package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.item.RandomAbilityBookFunction;
import com.heroicpaths.item.VanillaGear;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Функции таблиц добычи и глобальные модификаторы добычи мода. */
public final class ModLoot {
    public static final DeferredRegister<LootItemFunctionType<?>> FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, HeroicPaths.MODID);
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, HeroicPaths.MODID);

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<RandomAbilityBookFunction>> RANDOM_ABILITY_BOOK =
            FUNCTIONS.register("random_ability_book", () -> new LootItemFunctionType<>(RandomAbilityBookFunction.CODEC));

    /** Убирает ванильное оружие и броню из любой добычи. */
    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<VanillaGear.LootFilter>> STRIP_VANILLA_GEAR =
            LOOT_MODIFIERS.register("strip_vanilla_gear", () -> VanillaGear.LootFilter.CODEC);

    private ModLoot() {}
}
