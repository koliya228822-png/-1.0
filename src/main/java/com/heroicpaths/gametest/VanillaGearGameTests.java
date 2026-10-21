package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.craft.Profession;
import com.heroicpaths.craft.Station;
import com.heroicpaths.craft.StationCrafting;
import com.heroicpaths.craft.StationRecipe;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModComponents;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Ванильное оружие, инструменты и броня делаются только на станках ремёсел. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class VanillaGearGameTests {
    private static final String ARENA = "gametest_arena";

    /** Все перенесённые рецепты и станки, на которых они теперь делаются. */
    static List<String[]> moved() {
        List<String[]> moved = new ArrayList<>();
        for (String material : List.of("wooden", "stone", "iron", "golden", "diamond")) {
            String station = material.equals("wooden") || material.equals("stone") ? "tannery" : "forge";
            for (String tool : List.of("sword", "axe", "pickaxe", "shovel", "hoe")) moved.add(new String[]{material + "_" + tool, station});
        }
        for (String material : List.of("leather", "iron", "golden", "diamond")) {
            String station = material.equals("leather") ? "tannery" : "forge";
            for (String piece : List.of("helmet", "chestplate", "leggings", "boots")) moved.add(new String[]{material + "_" + piece, station});
        }
        for (String name : List.of("turtle_helmet", "bow", "crossbow", "shield")) moved.add(new String[]{name, "tannery"});
        return moved;
    }

    @GameTest(template = ARENA)
    public static void vanillaGearRecipesMovedToStations(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        for (String[] entry : moved()) {
            helper.assertTrue(manager.byKey(ResourceLocation.withDefaultNamespace(entry[0])).isEmpty(),
                    "the crafting table still makes " + entry[0]);
            var station = manager.byKey(HeroicPaths.id("station/" + entry[1] + "/" + entry[0])).orElse(null);
            helper.assertTrue(station != null && station.value() instanceof StationRecipe recipe
                    && recipe.station() == Station.byId(entry[1])
                    && recipe.result().getItem().builtInRegistryHolder().key().location().getPath().equals(entry[0]),
                    entry[0] + " is not made at the " + entry[1]);
        }
        // Оружие мода тоже делается только на станках.
        for (String[] entry : List.of(new String[]{"iron_dagger", "forge"}, new String[]{"diamond_greatsword", "forge"},
                new String[]{"musket", "forge"}, new String[]{"apprentice_wand", "rune_table"}, new String[]{"apprentice_staff", "rune_table"})) {
            helper.assertTrue(manager.byKey(HeroicPaths.id(entry[0])).isEmpty(), "the crafting table still makes " + entry[0]);
            helper.assertTrue(manager.byKey(HeroicPaths.id("station/" + entry[1] + "/" + entry[0])).isPresent(), entry[0] + " is not made at the " + entry[1]);
        }
        // Остальные ванильные рецепты на месте.
        helper.assertTrue(manager.byKey(ResourceLocation.withDefaultNamespace("crafting_table")).isPresent()
                && manager.byKey(ResourceLocation.withDefaultNamespace("stick")).isPresent()
                && manager.byKey(ResourceLocation.withDefaultNamespace("netherite_sword_smithing")).isPresent(),
                "unrelated vanilla recipes must stay");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void vanillaGearIsForgedWithRarityAndCraftXp(GameTestHelper helper) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        ServerLevel level = helper.getLevel();
        BlockPos forge = player.blockPosition().east(2);
        BlockPos tannery = player.blockPosition().west(2);
        level.setBlockAndUpdate(forge, ModBlocks.SMITHING_FORGE.get().defaultBlockState());
        level.setBlockAndUpdate(tannery, ModBlocks.TANNING_VAT.get().defaultBlockState());
        player.getInventory().setItem(10, new ItemStack(Items.IRON_INGOT, 8));
        player.getInventory().setItem(11, new ItemStack(Items.STICK, 8));
        player.getInventory().setItem(12, new ItemStack(Items.OAK_PLANKS, 8));
        var professions = RpgManager.get(player).professions();

        helper.assertTrue(StationCrafting.craft(player, HeroicPaths.id("station/forge/iron_pickaxe"), forge, false) == 1,
                "a novice blacksmith must forge an iron pickaxe");
        ItemStack pickaxe = player.getInventory().items.stream().filter(s -> s.is(Items.IRON_PICKAXE)).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(pickaxe.has(ModComponents.RARITY), "forged vanilla tools must roll rarity");
        helper.assertTrue(professions.xp(Profession.BLACKSMITH) == 8, "an iron pickaxe must give 8 craft XP, got " + professions.xp(Profession.BLACKSMITH));
        helper.assertTrue(StationCrafting.craft(player, HeroicPaths.id("station/forge/iron_pickaxe"), tannery, false) == 0,
                "forge recipes must not work at the tannery");

        helper.assertTrue(StationCrafting.craft(player, HeroicPaths.id("station/tannery/wooden_sword"), tannery, false) == 1,
                "the tannery must make wooden swords");
        helper.assertTrue(professions.xp(Profession.CARPENTER) == 2, "a wooden sword must give only 2 craft XP");
        helper.assertTrue(StationCrafting.craft(player, HeroicPaths.id("station/tannery/shield"), tannery, false) == 1
                && player.getInventory().countItem(Items.SHIELD) == 1, "the tannery must make shields");

        // Станки выводят сначала материалы, потом снаряжение по грейдам.
        var recipes = StationCrafting.recipes(level.getRecipeManager(), Station.FORGE);
        helper.assertTrue(recipes.get(0).value().result().is(com.heroicpaths.registry.ModItems.STEEL_PLATE.get()),
                "forge materials must come first");
        helper.succeed();
    }
}
