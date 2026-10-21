package com.heroicpaths.registry;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.SimpleTier;

/** Ступени металла для оружия: железо (грейд I) → сталь (II) → дамаск (III). */
public final class ModTiers {
    public static final Tier STEEL = new SimpleTier(BlockTags.INCORRECT_FOR_IRON_TOOL,
            600, 7f, 2.5f, 12, () -> Ingredient.of(ModItems.STEEL_PLATE.get()));
    public static final Tier DAMASCUS = new SimpleTier(BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            1400, 8.5f, 3.5f, 16, () -> Ingredient.of(ModItems.DAMASCUS_INGOT.get()));

    private ModTiers() {}
}
