package com.heroicpaths.gear;

import com.heroicpaths.registry.ModRecipes;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.level.Level;

/**
 * Ванильная починка в сетке крафта (два повреждённых предмета → один), но не для оружия и брони.
 * Заменяет рецепт {@code minecraft:repair_item} через data/minecraft/recipe/repair_item.json.
 */
public class GearlessRepairRecipe extends RepairItemRecipe {
    public GearlessRepairRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        for (int i = 0; i < input.size(); i++) {
            if (GearRules.isGear(input.getItem(i))) return false;
        }
        return super.matches(input, level);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.GEARLESS_REPAIR.get();
    }
}
