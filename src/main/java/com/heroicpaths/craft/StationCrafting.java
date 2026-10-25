package com.heroicpaths.craft;

import com.heroicpaths.item.Grades;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.registry.ModRecipes;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Крафт на станках: материалы списываются прямо из инвентаря (и второй руки),
 * а снаряжение получает случайное качество, как при обычном крафте.
 */
public final class StationCrafting {
    /** Насколько далеко от станка можно стоять (квадрат расстояния). */
    public static final double MAX_DISTANCE_SQR = 8 * 8;

    private StationCrafting() {}

    /** Рецепты станка: сначала материалы, затем снаряжение по грейдам, внутри — в порядке регистрации предметов. */
    public static List<RecipeHolder<StationRecipe>> recipes(RecipeManager manager, Station station) {
        return manager.getAllRecipesFor(ModRecipes.STATION_TYPE.get()).stream()
                .filter(holder -> holder.value().station() == station)
                .sorted(Comparator.<RecipeHolder<StationRecipe>>comparingInt(holder -> ItemRarity.canHaveRarity(holder.value().result()) ? 1 : 0)
                        .thenComparingInt(holder -> Grades.of(holder.value().result()))
                        .thenComparingInt(holder -> BuiltInRegistries.ITEM.getId(holder.value().result().getItem()))
                        .thenComparing(holder -> holder.id().toString()))
                .toList();
    }

    /** Сколько предметов, подходящих ингредиенту, есть у игрока. */
    public static int available(Inventory inventory, SizedIngredient ingredient) {
        int total = 0;
        for (ItemStack stack : slots(inventory)) {
            if (!stack.isEmpty() && ingredient.ingredient().test(stack)) total += stack.getCount();
        }
        return total;
    }

    public static boolean canCraft(Inventory inventory, StationRecipe recipe) {
        return consume(inventory, recipe.ingredients(), true);
    }

    /**
     * Ступень ремесла, нужная для рецепта: по грейду вещи или ступени материала (I — новичок, II — подмастерье,
     * III — мастер-ремесленник, IV–VI — гроссмейстер).
     */
    public static int requiredRank(StationRecipe recipe) {
        int grade = Grades.of(recipe.result());
        if (grade == 0) grade = Grades.materialGrade(recipe.result());
        return Math.max(Professions.MIN_RANK, Math.min(GrandmasterForging.RANK, grade));
    }

    /** Шанс, что камень расколется при огранке (ингредиенты пропадают, результата нет). */
    public static final float CUT_BREAK_CHANCE = 0.5f;

    public static boolean isUnlocked(Player player, StationRecipe recipe) {
        return RpgManager.get(player).professions().rank(Profession.of(recipe.station())) >= requiredRank(recipe);
    }

    /**
     * Опыт ремесла за одну вещь: снаряжение с грейдом — 10 за грейд, ванильное снаряжение — по материалу
     * (см. {@link #vanillaGearXp}), материалы и руны — 3 за ступень.
     */
    public static int craftXp(StationRecipe recipe) {
        ItemStack result = recipe.result();
        int grade = Grades.of(result);
        if (grade > 0 && ItemRarity.canHaveRarity(result)) return 10 * grade;
        if (ItemRarity.canHaveRarity(result)) return vanillaGearXp(result);
        return 3 * Math.max(1, Grades.materialGrade(result));
    }

    /** Ванильное снаряжение без грейда: дерево и кожа — 2, камень — 3, золото — 4, железо и кольчуга — 8, алмаз — 15, луки и щиты — 5. */
    public static int vanillaGearXp(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof TieredItem tiered) {
            Tier tier = tiered.getTier();
            if (tier == Tiers.WOOD) return 2;
            if (tier == Tiers.STONE) return 3;
            if (tier == Tiers.GOLD) return 4;
            if (tier == Tiers.IRON) return 8;
            if (tier == Tiers.DIAMOND) return 15;
            if (tier == Tiers.NETHERITE) return 20;
        }
        if (item instanceof ArmorItem armor) {
            ArmorMaterial material = armor.getMaterial().value();
            if (material == ArmorMaterials.LEATHER.value()) return 2;
            if (material == ArmorMaterials.GOLD.value()) return 4;
            if (material == ArmorMaterials.IRON.value() || material == ArmorMaterials.CHAIN.value() || material == ArmorMaterials.TURTLE.value()) return 8;
            if (material == ArmorMaterials.DIAMOND.value()) return 15;
            if (material == ArmorMaterials.NETHERITE.value()) return 20;
        }
        return 5;
    }

    /**
     * Создаёт предмет по рецепту, если игрок стоит у нужного станка и у него хватает материалов.
     * all — повторять, пока хватает материалов (не больше стопки результата). Возвращает число созданий.
     */
    public static int craft(ServerPlayer player, ResourceLocation recipeId, BlockPos pos, boolean all) {
        RecipeHolder<?> holder = player.server.getRecipeManager().byKey(recipeId).orElse(null);
        if (holder == null || !(holder.value() instanceof StationRecipe recipe)) return 0;
        if (!(player.level().getBlockState(pos).getBlock() instanceof StationBlock block) || block.station() != recipe.station()) return 0;
        if (player.distanceToSqr(pos.getCenter()) > MAX_DISTANCE_SQR) return 0;
        Profession profession = Profession.of(recipe.station());
        if (!isUnlocked(player, recipe)) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.recipe_locked", profession.displayName(),
                    Professions.rankName(requiredRank(recipe))).withStyle(ChatFormatting.RED), true);
            return 0;
        }

        int limit = all ? Math.max(1, recipe.result().getMaxStackSize() / Math.max(1, recipe.result().getCount())) : 1;
        int crafted = 0, broken = 0;
        while (crafted < limit && consume(player.getInventory(), recipe.ingredients(), true)) {
            consume(player.getInventory(), recipe.ingredients(), false);
            crafted++;
            if (recipe.station() == Station.GEM_TABLE && player.getRandom().nextFloat() < CUT_BREAK_CHANCE) {
                broken++;
                continue;
            }
            ItemStack result = recipe.result().copy();
            if (ItemRarity.canHaveRarity(result)) ItemRarity.rollFor(player, result, profession);
            result.onCraftedBy(player.level(), player, result.getCount());
            if (!player.getInventory().add(result)) player.drop(result, false);
        }
        if (broken > 0) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.gem_broken", broken, crafted)
                    .withStyle(ChatFormatting.RED), true);
            player.serverLevel().playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1f, 0.8f);
        }
        if (crafted > 0) {
            ProfessionProgress.award(player, profession, craftXp(recipe) * crafted);
            player.serverLevel().playSound(null, pos, sound(recipe.station()), SoundSource.BLOCKS, 0.8f,
                    0.9f + player.getRandom().nextFloat() * 0.2f);
        }
        return crafted;
    }

    private static SoundEvent sound(Station station) {
        return switch (station) {
            case FORGE -> SoundEvents.ANVIL_USE;
            case TANNERY -> SoundEvents.ARMOR_EQUIP_LEATHER.value();
            case LOOM -> SoundEvents.UI_LOOM_TAKE_RESULT;
            case RUNE_TABLE -> SoundEvents.ENCHANTMENT_TABLE_USE;
            case GEM_TABLE -> SoundEvents.GRINDSTONE_USE;
            case JEWELER_TABLE -> SoundEvents.AMETHYST_BLOCK_CHIME;
        };
    }

    private static List<ItemStack> slots(Inventory inventory) {
        List<ItemStack> slots = new ArrayList<>(inventory.items);
        slots.addAll(inventory.offhand);
        return slots;
    }

    /** Списывает ингредиенты; при simulate только проверяет, что их хватает. */
    static boolean consume(Inventory inventory, List<SizedIngredient> ingredients, boolean simulate) {
        List<ItemStack> slots = slots(inventory);
        int[] taken = new int[slots.size()];
        for (SizedIngredient ingredient : ingredients) {
            int need = ingredient.count();
            for (int i = 0; i < slots.size() && need > 0; i++) {
                ItemStack stack = slots.get(i);
                if (stack.isEmpty() || !ingredient.ingredient().test(stack)) continue;
                int use = Math.min(stack.getCount() - taken[i], need);
                taken[i] += use;
                need -= use;
            }
            if (need > 0) return false;
        }
        if (!simulate) {
            for (int i = 0; i < slots.size(); i++) if (taken[i] > 0) slots.get(i).shrink(taken[i]);
        }
        return true;
    }
}
