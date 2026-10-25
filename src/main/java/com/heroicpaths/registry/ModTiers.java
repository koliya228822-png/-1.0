package com.heroicpaths.registry;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

/**
 * Ступени металла для оружия: железо (грейд I) → сталь (II) → дамаск (III) →
 * алмаз (IV) → гранёный алмаз (V) → бриллиант (VI).
 */
public final class ModTiers {
    public static final Tier STEEL = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL,
            600, 7f, 2.5f, 12, () -> Ingredient.of(ModItems.STEEL_PLATE.get()));
    public static final Tier DAMASCUS = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            1400, 8.5f, 3.5f, 16, () -> Ingredient.of(ModItems.DAMASCUS_INGOT.get()));

    public static final Tier DIAMOND = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            2000, 9f, 4.5f, 18, () -> Ingredient.of(Items.DIAMOND));
    public static final Tier FACETED = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            2600, 9.5f, 5.5f, 20, () -> Ingredient.of(ModItems.FACETED_DIAMOND.get()));
    public static final Tier BRILLIANT = new SimpleTier(BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            3400, 10f, 6.5f, 22, () -> Ingredient.of(ModItems.BRILLIANT.get()));

    private ModTiers() {}
}
