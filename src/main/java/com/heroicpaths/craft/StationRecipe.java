package com.heroicpaths.craft;

import com.heroicpaths.registry.ModRecipes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.List;

/**
 * Рецепт станка: набор ингредиентов с количеством, которые берутся прямо из инвентаря, и результат.
 * В сетке крафта не участвует — его выбирают в окне станка.
 */
public record StationRecipe(Station station, List<SizedIngredient> ingredients, ItemStack result) implements Recipe<RecipeInput> {
    public static final MapCodec<StationRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Station.CODEC.fieldOf("station").forGetter(StationRecipe::station),
            SizedIngredient.FLAT_CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(StationRecipe::ingredients),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(StationRecipe::result)
    ).apply(instance, StationRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StationRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Station.values()[i], Station::ordinal), StationRecipe::station,
            SizedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list()), StationRecipe::ingredients,
            ItemStack.STREAM_CODEC, StationRecipe::result,
            StationRecipe::new);

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        for (SizedIngredient ingredient : ingredients) list.add(ingredient.ingredient());
        return list;
    }

    /** Книга рецептов о станках не знает. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.STATION.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.STATION_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<StationRecipe> {
        @Override
        public MapCodec<StationRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, StationRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
