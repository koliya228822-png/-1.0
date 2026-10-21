package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.craft.StationRecipe;
import com.heroicpaths.gear.GearlessRepairRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, HeroicPaths.MODID);
    public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, HeroicPaths.MODID);

    /** Починка в сетке крафта без оружия и брони. */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<GearlessRepairRecipe>> GEARLESS_REPAIR =
            SERIALIZERS.register("repair_item", () -> new SimpleCraftingRecipeSerializer<>(GearlessRepairRecipe::new));

    /** Рецепты станков ремесла: горна, дубильного чана, ткацкого станка и рунного стола. */
    public static final DeferredHolder<RecipeType<?>, RecipeType<StationRecipe>> STATION_TYPE =
            TYPES.register("station", () -> RecipeType.simple(HeroicPaths.id("station")));
    public static final DeferredHolder<RecipeSerializer<?>, StationRecipe.Serializer> STATION =
            SERIALIZERS.register("station", StationRecipe.Serializer::new);

    private ModRecipes() {}
}
