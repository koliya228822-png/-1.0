package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.craft.Station;
import com.heroicpaths.craft.StationCrafting;
import com.heroicpaths.craft.StationRecipe;
import com.heroicpaths.gear.GearRules;
import com.heroicpaths.item.GradedBowItem;
import com.heroicpaths.item.Grades;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModComponents;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.ability.AbilityLogic;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import com.heroicpaths.rpg.stat.Stat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Игровые тесты этапа B: руды, станки ремесла, грейды снаряжения, руны и фокусы стихий. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class MaterialGameTests {
    private static final String ARENA = "gametest_arena";

    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    private static boolean biomeHas(ServerLevel level, ResourceKey<Biome> biome, String feature) {
        Biome value = level.registryAccess().registryOrThrow(Registries.BIOME).getOrThrow(biome);
        return value.getGenerationSettings().features().stream()
                .anyMatch(step -> step.stream().anyMatch((Holder<PlacedFeature> holder) -> holder.is(HeroicPaths.id(feature))));
    }

    @GameTest(template = ARENA)
    public static void oresGenerateInTheOverworld(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        for (String ore : List.of("fire_crystal", "frost_crystal", "earth_crystal", "storm_crystal", "topaz", "sapphire", "ruby")) {
            helper.assertTrue(placed.containsKey(HeroicPaths.id("ore_" + ore)), "missing placed feature ore_" + ore);
            helper.assertTrue(biomeHas(level, Biomes.PLAINS, "ore_" + ore), "plains must generate ore_" + ore);
        }
        // Стихийные кристаллы чаще встречаются в своих краях.
        helper.assertTrue(biomeHas(level, Biomes.SNOWY_PLAINS, "ore_frost_crystal_extra"), "cold biomes need extra frost crystals");
        helper.assertTrue(biomeHas(level, Biomes.DESERT, "ore_fire_crystal_extra"), "hot biomes need extra fire crystals");
        helper.assertTrue(!biomeHas(level, Biomes.PLAINS, "ore_frost_crystal_extra"), "plains must not get extra frost crystals");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void oresDropCrystalsAndGems(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState ruby = ModBlocks.RUBY_ORE.get().defaultBlockState();
        BlockState crystal = ModBlocks.DEEPSLATE_FIRE_CRYSTAL_ORE.get().defaultBlockState();

        helper.assertTrue(ruby.is(BlockTags.NEEDS_IRON_TOOL) && ruby.is(BlockTags.MINEABLE_WITH_PICKAXE), "gem ores need an iron pickaxe");
        helper.assertTrue(crystal.is(BlockTags.NEEDS_STONE_TOOL), "crystal ores need a stone pickaxe");
        helper.assertTrue(!new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(ruby), "a stone pickaxe must not mine rubies");
        helper.assertTrue(new ItemStack(Items.STONE_PICKAXE).isCorrectToolForDrops(crystal), "a stone pickaxe must mine crystals");

        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        List<ItemStack> gemDrops = Block.getDrops(ruby, level, pos, null, null, pickaxe);
        helper.assertTrue(gemDrops.size() == 1 && gemDrops.get(0).is(ModItems.RUBY.get()), "ruby ore must drop a ruby: " + gemDrops);
        List<ItemStack> crystalDrops = Block.getDrops(crystal, level, pos, null, null, pickaxe);
        helper.assertTrue(!crystalDrops.isEmpty() && crystalDrops.get(0).is(ModItems.FIRE_CRYSTAL.get()) && crystalDrops.get(0).getCount() <= 2,
                "fire crystal ore must drop 1-2 fire crystals: " + crystalDrops);

        ItemStack silk = new ItemStack(Items.IRON_PICKAXE);
        silk.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        List<ItemStack> silkDrops = Block.getDrops(ruby, level, pos, null, null, silk);
        helper.assertTrue(silkDrops.size() == 1 && silkDrops.get(0).is(ModItems.RUBY_ORE.get()), "silk touch must keep the ore: " + silkDrops);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void stationsCraftFromTheInventory(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos forge = player.blockPosition().east(2);
        BlockPos loom = player.blockPosition().west(1).north(1);
        level.setBlockAndUpdate(forge, ModBlocks.SMITHING_FORGE.get().defaultBlockState());
        level.setBlockAndUpdate(loom, ModBlocks.WEAVING_LOOM.get().defaultBlockState());

        // У каждого станка свои рецепты; сначала материалы, потом снаряжение по грейдам.
        var manager = level.getRecipeManager();
        for (Station station : Station.values()) {
            List<RecipeHolder<StationRecipe>> recipes = StationCrafting.recipes(manager, station);
            helper.assertTrue(recipes.size() >= 10, station + " has only " + recipes.size() + " recipes");
            int lastGrade = 0;
            for (RecipeHolder<StationRecipe> holder : recipes) {
                int grade = Grades.of(holder.value().result());
                helper.assertTrue(grade >= lastGrade, station + " recipes are not sorted by grade");
                lastGrade = grade;
            }
        }
        helper.assertTrue(StationCrafting.recipes(manager, Station.FORGE).get(0).value().result().is(ModItems.STEEL_PLATE.get()),
                "steel must be the first forge recipe");

        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 4));
        player.getInventory().add(new ItemStack(Items.COAL, 1));
        player.getInventory().add(new ItemStack(Items.CHARCOAL, 1));
        // Сталь и дамаск требуют ступеней кузнеца (подмастерье и мастер-ремесленник).
        com.heroicpaths.rpg.RpgManager.get(player).professions().setRank(com.heroicpaths.craft.Profession.BLACKSMITH, 3);
        helper.assertTrue(StationCrafting.craft(player, HeroicPaths.id("station/forge/steel_plate"), loom, false) == 0,
                "forge recipes must not work at the loom");
        helper.assertTrue(StationCrafting.craft(player, HeroicPaths.id("station/forge/steel_plate"), forge, false) == 1, "steel was not forged");
        helper.assertTrue(player.getInventory().countItem(ModItems.STEEL_PLATE.get()) == 1 && player.getInventory().countItem(Items.IRON_INGOT) == 2,
                "forging steel must take 2 iron and give 1 plate");
        helper.assertTrue(StationCrafting.craft(player, HeroicPaths.id("station/forge/steel_plate"), forge, true) == 1,
                "craft-all must stop when the iron runs out");
        helper.assertTrue(StationCrafting.craft(player, HeroicPaths.id("station/forge/steel_plate"), forge, true) == 0, "no iron left");
        helper.assertTrue(player.getInventory().countItem(ModItems.STEEL_PLATE.get()) == 2, "expected 2 steel plates");

        // Снаряжение со станка получает качество, как при обычном крафте.
        player.getInventory().add(new ItemStack(ModItems.DAMASCUS_INGOT.get(), 8));
        player.getInventory().add(new ItemStack(ModItems.HARDENED_LEATHER.get(), 1));
        helper.assertTrue(StationCrafting.craft(player, HeroicPaths.id("station/forge/warrior_damascus_chestplate"), forge, false) == 1,
                "damascus cuirass was not forged");
        ItemStack cuirass = player.getInventory().items.stream().filter(s -> s.is(ModItems.WARRIOR_DAMASCUS_CHESTPLATE.get())).findFirst().orElse(ItemStack.EMPTY);
        helper.assertTrue(cuirass.has(ModComponents.RARITY), "station gear must roll rarity");
        helper.assertTrue(Grades.of(cuirass) == 3, "damascus armor must be grade III");

        // Слишком далеко от станка — ничего не выходит.
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 2));
        player.moveTo(player.getX() + 20, player.getY(), player.getZ());
        helper.assertTrue(StationCrafting.craft(player, HeroicPaths.id("station/forge/steel_plate"), forge, false) == 0,
                "crafting must need the player next to the station");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void armorGradesScaleClassBonuses(GameTestHelper helper) {
        ServerPlayer mage = survivor(helper);
        RpgManager.chooseClass(mage, PlayerClass.MAGE);
        mage.getInventory().clearContent();
        float base = PlayerStats.get(mage).get(Stat.SPELL_DAMAGE);
        float wool = setDelta(mage, base, ModItems.MAGE_WOOL_HELMET.get(), ModItems.MAGE_WOOL_CHESTPLATE.get(), ModItems.MAGE_WOOL_LEGGINGS.get(), ModItems.MAGE_WOOL_BOOTS.get());
        float arcane = setDelta(mage, base, ModItems.MAGE_HELMET.get(), ModItems.MAGE_CHESTPLATE.get(), ModItems.MAGE_LEGGINGS.get(), ModItems.MAGE_BOOTS.get());
        float astral = setDelta(mage, base, ModItems.MAGE_ASTRAL_HELMET.get(), ModItems.MAGE_ASTRAL_CHESTPLATE.get(), ModItems.MAGE_ASTRAL_LEGGINGS.get(), ModItems.MAGE_ASTRAL_BOOTS.get());
        helper.assertTrue(Math.abs(wool - 15.6f) < 0.01f, "wool set (grade I) must give 0.6 x 26 = 15.6% spell damage, got " + wool);
        helper.assertTrue(Math.abs(arcane - 26f) < 0.01f, "arcane set (grade II) must give 26% spell damage, got " + arcane);
        helper.assertTrue(Math.abs(astral - 39f) < 0.01f, "astral set (grade III) must give 1.5 x 26 = 39% spell damage, got " + astral);
        // Смешанный комплект: бонус комплекта по худшему грейду.
        float mixed = setDelta(mage, base, ModItems.MAGE_ASTRAL_HELMET.get(), ModItems.MAGE_ASTRAL_CHESTPLATE.get(), ModItems.MAGE_ASTRAL_LEGGINGS.get(), ModItems.MAGE_WOOL_BOOTS.get());
        helper.assertTrue(Math.abs(mixed - (18f + 2.4f + 6f)) < 0.01f, "mixed set must use the lowest grade for the set bonus, got " + mixed);
        helper.assertTrue(Grades.of(new ItemStack(ModItems.MAGE_WOOL_HELMET.get())) == 1 && Grades.of(new ItemStack(ModItems.WARRIOR_HELMET.get())) == 2,
                "wrong armor grades");
        helper.succeed();
    }

    private static float setDelta(ServerPlayer player, float base, Item head, Item chest, Item legs, Item feet) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(head));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chest));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(legs));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(feet));
        RpgManager.applyPassives(player);
        return PlayerStats.get(player).get(Stat.SPELL_DAMAGE) - base;
    }

    @GameTest(template = ARENA)
    public static void runeFociBoostTheirElement(GameTestHelper helper) {
        ServerPlayer mage = survivor(helper);
        RpgManager.chooseClass(mage, PlayerClass.MAGE);
        AbilityDef fireball = AbilityRegistry.get("fireball");
        AbilityDef iceLance = AbilityRegistry.get("ice_lance");

        mage.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FIRE_RUNE_WAND.get()));
        float ice = AbilityLogic.damageFactor(mage, iceLance);
        float fire = AbilityLogic.damageFactor(mage, fireball);
        helper.assertTrue(Math.abs(ice - 0.55f) < 1e-4, "rune wand (power 5.5) must give 0.55 to other elements, got " + ice);
        helper.assertTrue(Math.abs(fire - 0.55f * 1.15f) < 1e-4, "fire rune wand must add 15% to fire spells, got " + fire);

        mage.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FROST_ARCHMAGE_STAFF.get()));
        helper.assertTrue(Math.abs(AbilityLogic.damageFactor(mage, iceLance) - 1.3f) < 1e-4, "archmage frost staff: 0.5 x 10/5 x 1.3 = 1.3");
        helper.assertTrue(Math.abs(AbilityLogic.damageFactor(mage, fireball) - 1.0f) < 1e-4, "archmage frost staff must not boost fire");

        ServerPlayer warrior = survivor(helper);
        RpgManager.chooseClass(warrior, PlayerClass.WARRIOR);
        warrior.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FIRE_RUNE_WAND.get()));
        helper.assertTrue(AbilityLogic.damageFactor(warrior, fireball) == 0.5f, "foci must not change warrior damage");

        ItemStack wand = new ItemStack(ModItems.STORM_RUNE_WAND.get());
        helper.assertTrue(ItemRarity.canHaveRarity(wand) && GearRules.isGear(wand), "foci must roll rarity and be kept on death");
        helper.assertTrue(Grades.of(wand) == 2 && Grades.of(new ItemStack(ModItems.EARTH_ARCHMAGE_WAND.get())) == 3
                && Grades.of(new ItemStack(ModItems.APPRENTICE_STAFF.get())) == 1, "wrong focus grades");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void gradedWeaponsHitHarder(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ItemStack longbow = new ItemStack(ModItems.RANGER_LONGBOW.get());
        Arrow arrow = new Arrow(helper.getLevel(), player, new ItemStack(Items.ARROW), longbow);
        double base = arrow.getBaseDamage();
        ((GradedBowItem) longbow.getItem()).customArrow(arrow, new ItemStack(Items.ARROW), longbow);
        helper.assertTrue(Math.abs(arrow.getBaseDamage() - base * 1.5) < 1e-6, "ranger longbow must add 50% arrow damage");

        double iron = attackDamage(new ItemStack(Items.IRON_SWORD));
        double steel = attackDamage(new ItemStack(ModItems.STEEL_SWORD.get()));
        double damascus = attackDamage(new ItemStack(ModItems.DAMASCUS_SWORD.get()));
        helper.assertTrue(iron < steel && steel < damascus, "swords must grow iron < steel < damascus: " + iron + " " + steel + " " + damascus);
        helper.assertTrue(Grades.of(new ItemStack(Items.IRON_SWORD)) == 1 && Grades.of(new ItemStack(ModItems.STEEL_DAGGER.get())) == 2
                && Grades.of(new ItemStack(ModItems.DAMASCUS_GREATSWORD.get())) == 3 && Grades.of(new ItemStack(Items.DIAMOND_SWORD)) == 0
                && Grades.of(new ItemStack(Items.BOW)) == 1 && Grades.of(longbow) == 3, "wrong weapon grades");
        helper.succeed();
    }

    private static double attackDamage(ItemStack stack) {
        double[] total = {1.0};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) total[0] += modifier.amount();
        });
        return total[0];
    }
}
