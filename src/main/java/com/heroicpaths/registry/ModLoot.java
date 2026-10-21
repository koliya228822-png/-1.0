package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.item.RandomAbilityBookFunction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Функции таблиц добычи мода. */
public final class ModLoot {
    public static final DeferredRegister<LootItemFunctionType<?>> FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, HeroicPaths.MODID);

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<RandomAbilityBookFunction>> RANDOM_ABILITY_BOOK =
            FUNCTIONS.register("random_ability_book", () -> new LootItemFunctionType<>(RandomAbilityBookFunction.CODEC));

    private ModLoot() {}
}
