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

    /** Ванильные инструменты: делаются и на верстаке, и на станке ремёсел (там — с редкостью и опытом ремесла). */
    static List<String[]> tools() {
        List<String[]> tools = new ArrayList<>();
        for (String material : List.of("wooden", "stone", "iron", "golden", "diamond")) {
            String station = material.equals("wooden") || material.equals("stone") ? "tannery" : "forge";
            for (String tool : List.of("axe", "pickaxe", "shovel", "hoe")) tools.add(new String[]{material + "_" + tool, station});
        }
        return tools;
    }

    @GameTest(template = ARENA)
    public static void vanillaToolsOnBenchAndStations(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager();
        for (String[] entry : tools()) {
            helper.assertTrue(manager.byKey(ResourceLocation.withDefaultNamespace(entry[0])).isPresent(),
                    "the crafting table must make " + entry[0]);
            var station = manager.byKey(HeroicPaths.id("station/" + entry[1] + "/" + entry[0])).orElse(null);
            helper.assertTrue(station != null && station.value() instanceof StationRecipe recipe
                    && recipe.station() == Station.byId(entry[1])
                    && recipe.result().getItem().builtInRegistryHolder().key().location().getPath().equals(entry[0]),
                    entry[0] + " is not made at the " + entry[1]);
        }
        // Ванильный лук — лук I грейда — делается на дубильне.
        helper.assertTrue(manager.byKey(ResourceLocation.withDefaultNamespace("bow")).isEmpty()
                && manager.byKey(HeroicPaths.id("station/tannery/bow")).isPresent(), "the vanilla bow is made at the tannery");
        // Ванильное оружие и броня не делаются нигде: ни на верстаке, ни на станках, ни на кузнечном столе.
        for (var holder : manager.getRecipes()) {
            // Отделка брони ничего не создаёт: её «результат» — просто пример.
            if (holder.value() instanceof net.minecraft.world.item.crafting.SmithingTrimRecipe) continue;
            var result = holder.value().getResultItem(helper.getLevel().registryAccess());
            helper.assertTrue(!com.heroicpaths.item.VanillaGear.isRemoved(result), holder.id() + " still makes " + result);
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
                && manager.byKey(ResourceLocation.withDefaultNamespace("netherite_pickaxe_smithing")).isPresent(),
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

        helper.assertTrue(StationCrafting.craft(player, HeroicPaths.id("station/tannery/wooden_pickaxe"), tannery, false) == 1,
                "the tannery must make wooden pickaxes");
        helper.assertTrue(professions.xp(Profession.CARPENTER) == 2, "a wooden pickaxe must give only 2 craft XP");

        // Станки выводят сначала материалы, потом снаряжение по грейдам.
        var recipes = StationCrafting.recipes(level.getRecipeManager(), Station.FORGE);
        helper.assertTrue(recipes.get(0).value().result().is(com.heroicpaths.registry.ModItems.STEEL_PLATE.get()),
                "forge materials must come first");
        helper.succeed();
    }

    /** Ванильного оружия и брони нет ни у монстров, ни в добыче; слуги игрока вместо меча берут кинжал. */
    @GameTest(template = ARENA)
    public static void vanillaGearRemovedFromMobsAndLoot(GameTestHelper helper) {
        net.minecraft.world.entity.monster.Zombie zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
        zombie.moveTo(helper.absolutePos(new BlockPos(2, 2, 2)), 0f, 0f);
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        zombie.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH));
        helper.getLevel().addFreshEntity(zombie);
        helper.assertTrue(zombie.getMainHandItem().isEmpty() && zombie.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(),
                "monsters must not carry vanilla weapons or armor");
        helper.assertTrue(zombie.getOffhandItem().is(Items.TORCH), "other items stay");

        var loot = new it.unimi.dsi.fastutil.objects.ObjectArrayList<ItemStack>();
        loot.add(new ItemStack(Items.IRON_CHESTPLATE));
        loot.add(new ItemStack(Items.GOLDEN_SWORD));
        loot.add(new ItemStack(Items.TRIDENT));
        loot.add(new ItemStack(Items.IRON_PICKAXE));
        loot.add(new ItemStack(Items.BOW));
        loot.removeIf(com.heroicpaths.item.VanillaGear::isRemoved);
        helper.assertTrue(loot.size() == 2, "loot keeps tools and the bow but loses weapons and armor, got " + loot);
        zombie.discard();
        helper.succeed();
    }
}
