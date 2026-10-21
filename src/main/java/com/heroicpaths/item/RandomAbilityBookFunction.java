package com.heroicpaths.item;

import com.heroicpaths.registry.ModComponents;
import com.heroicpaths.registry.ModLoot;
import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.List;

/**
 * Функция добычи: превращает книгу умения в книгу случайного умения, которое можно выучить до уровня max_level.
 * Чем глубже данж, тем более сильные умения попадаются.
 */
public class RandomAbilityBookFunction extends LootItemConditionalFunction {
    public static final MapCodec<RandomAbilityBookFunction> CODEC = RecordCodecBuilder.mapCodec(instance -> commonFields(instance)
            .and(Codec.INT.optionalFieldOf("max_level", 100).forGetter(function -> function.maxLevel))
            .apply(instance, RandomAbilityBookFunction::new));

    private final int maxLevel;

    protected RandomAbilityBookFunction(List<LootItemCondition> conditions, int maxLevel) {
        super(conditions);
        this.maxLevel = maxLevel;
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        List<AbilityDef> pool = AbilityRegistry.bookAbilities(maxLevel);
        if (!pool.isEmpty()) stack.set(ModComponents.ABILITY.get(), pool.get(context.getRandom().nextInt(pool.size())).id());
        return stack;
    }

    @Override
    public LootItemFunctionType<RandomAbilityBookFunction> getType() {
        return ModLoot.RANDOM_ABILITY_BOOK.get();
    }
}
