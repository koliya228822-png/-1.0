package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.accessory.AccessoryItem;
import com.heroicpaths.accessory.Accessories;
import com.heroicpaths.craft.GrandmasterForging;
import com.heroicpaths.craft.Profession;
import com.heroicpaths.craft.StationCrafting;
import com.heroicpaths.gear.GearRules;
import com.heroicpaths.gear.GearSet;
import com.heroicpaths.gear.GearSets;
import com.heroicpaths.item.EquipmentStats;
import com.heroicpaths.item.Grades;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.item.ItemRarity.Tier;
import com.heroicpaths.item.MusketItem;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModComponents;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.stat.Stat;
import com.heroicpaths.rpg.stat.StatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Алмазный тир, аксессуары, сеты и отсутствие износа. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class DiamondTierGameTests {
    private static final String ARENA = "gametest_arena";

    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    @GameTest(template = ARENA)
    public static void gearDoesNotWearButLosesFivePercentOnDeath(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ItemStack sword = new ItemStack(ModItems.STEEL_SWORD.get());
        sword.hurtAndBreak(10, player, EquipmentSlot.MAINHAND);
        helper.assertTrue(sword.getDamageValue() == 0, "mod gear must not wear in use");
        ItemStack vanilla = new ItemStack(Items.IRON_PICKAXE);
        vanilla.hurtAndBreak(3, player, EquipmentSlot.MAINHAND);
        helper.assertTrue(vanilla.getDamageValue() == 3, "vanilla tools must still wear");
        int expected = Math.round(sword.getMaxDamage() * GearRules.DEATH_DURABILITY_LOSS);
        helper.assertTrue(GearRules.DEATH_DURABILITY_LOSS == 0.05f && GearRules.deathLoss(sword) == expected,
                "death must cost 5% durability, got " + GearRules.deathLoss(sword) + " of " + sword.getMaxDamage());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void newRecipesExist(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        for (String id : new String[]{"gunpowder", "musket_ball", "gem_table", "jeweler_table", "diamond_helmet_blank",
                "brilliant_weapon_blank", "station/forge/iron_sword", "station/forge/steel_musket", "station/forge/damascus_musket",
                "station/gem_table/faceted_diamond", "station/gem_table/brilliant", "station/forge/plate_gloves_6",
                "station/jeweler_table/ring_6", "station/jeweler_table/necklace_1"}) {
            helper.assertTrue(recipes.byKey(HeroicPaths.id(id)).isPresent(), "missing recipe " + id);
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void musketGradesGetStronger(GameTestHelper helper) {
        Item[] muskets = {ModItems.MUSKET.get(), ModItems.STEEL_MUSKET.get(), ModItems.DAMASCUS_MUSKET.get(),
                ModItems.DIAMOND_MUSKET.get(), ModItems.FACETED_MUSKET.get(), ModItems.BRILLIANT_MUSKET.get()};
        for (int i = 0; i < muskets.length; i++) {
            MusketItem musket = (MusketItem) muskets[i];
            helper.assertTrue(Grades.of(new ItemStack(musket)) == i + 1, "musket " + i + " has wrong grade");
            if (i > 0) {
                MusketItem previous = (MusketItem) muskets[i - 1];
                helper.assertTrue(musket.damage() > previous.damage() && musket.reloadTicks() <= previous.reloadTicks(),
                        "musket grade " + (i + 1) + " must hit harder and reload no slower");
            }
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void everyDamascusItemUpgradesToBrilliant(GameTestHelper helper) {
        int chains = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(HeroicPaths.MODID)) continue;
            ItemStack stack = new ItemStack(item);
            if (Grades.of(stack) != 3 || !ItemRarity.canHaveRarity(stack) || item instanceof AccessoryItem) continue;
            Item current = item;
            for (int grade = 4; grade <= 6; grade++) {
                Item next = GrandmasterForging.next(current);
                helper.assertTrue(next != null, BuiltInRegistries.ITEM.getKey(current) + " has no grade " + grade + " upgrade");
                helper.assertTrue(Grades.of(new ItemStack(next)) == grade, BuiltInRegistries.ITEM.getKey(next) + " must be grade " + grade);
                current = next;
            }
            chains++;
        }
        helper.assertTrue(chains > 30, "too few upgradable damascus items: " + chains);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void anvilUpgradeKeepsRarityAndSet(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ItemStack chest = new ItemStack(ModItems.WARRIOR_DAMASCUS_CHESTPLATE.get());
        ItemRarity.apply(chest, Tier.EPIC, RandomSource.create(1), helper.getLevel().registryAccess());
        chest.set(ModComponents.GEAR_SET, GearSet.GLADIATOR.id());

        helper.assertTrue(GrandmasterForging.result(chest, new ItemStack(ModItems.DIAMOND_HELMET_BLANK.get())).isEmpty(),
                "a helmet blank must not fit a chestplate");
        helper.assertTrue(GrandmasterForging.result(chest, new ItemStack(ModItems.FACETED_CHESTPLATE_BLANK.get())).isEmpty(),
                "damascus must go to diamond first");

        ItemStack diamond = GrandmasterForging.result(chest, new ItemStack(ModItems.DIAMOND_CHESTPLATE_BLANK.get()));
        helper.assertTrue(BuiltInRegistries.ITEM.getKey(diamond.getItem()).getPath().equals("warrior_diamond_chestplate"),
                "wrong upgrade result " + diamond);
        helper.assertTrue(ItemRarity.get(diamond) == Tier.EPIC, "rarity must survive the upgrade");
        helper.assertTrue(GearSets.of(diamond) == GearSet.GLADIATOR, "set must survive the upgrade");
        helper.assertTrue(!GrandmasterForging.canForge(player, diamond), "a novice must not forge grade IV");
        RpgManager.get(player).professions().setRank(Profession.BLACKSMITH, GrandmasterForging.RANK);
        helper.assertTrue(GrandmasterForging.canForge(player, diamond), "a grandmaster smith must forge grade IV");

        ItemStack brilliant = GrandmasterForging.result(
                GrandmasterForging.result(diamond, new ItemStack(ModItems.FACETED_CHESTPLATE_BLANK.get())),
                new ItemStack(ModItems.BRILLIANT_CHESTPLATE_BLANK.get()));
        helper.assertTrue(Grades.of(brilliant) == 6 && ItemRarity.get(brilliant) == Tier.EPIC, "IV → V → VI chain failed");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void gemCuttingBreaksAboutHalf(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        BlockPos table = player.blockPosition().east(2);
        helper.getLevel().setBlockAndUpdate(table, ModBlocks.GEM_TABLE.get().defaultBlockState());
        RpgManager.get(player).professions().setRank(Profession.JEWELER, GrandmasterForging.RANK);
        int tries = 200;
        for (int i = 0; i < tries; i += 50) player.getInventory().add(new ItemStack(Items.DIAMOND, 50));
        int crafted = 0;
        for (int i = 0; i < tries; i++) crafted += StationCrafting.craft(player, HeroicPaths.id("station/gem_table/faceted_diamond"), table, false);
        helper.assertTrue(crafted == tries, "every cut must use a diamond, used " + crafted);
        int cut = player.getInventory().countItem(ModItems.FACETED_DIAMOND.get());
        helper.assertTrue(cut >= 65 && cut <= 135, "about half the stones must survive cutting, got " + cut + " of " + tries);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void accessoriesWorkForTheirArmorType(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        Accessories accessories = RpgManager.get(player).accessories();
        accessories.set(0, AccessoryItem.withGrade(ModItems.PLATE_GLOVES.get(), 3));
        accessories.set(6, AccessoryItem.withGrade(ModItems.RING.get(), 3));
        helper.assertTrue(!Accessories.fits(1, new ItemStack(ModItems.RING.get())), "a ring must not go into the belt slot");

        RpgManager.chooseClass(player, PlayerClass.MAGE);
        StatBlock mage = new StatBlock();
        EquipmentStats.collect(player, PlayerClass.MAGE, mage);
        helper.assertTrue(mage.get(Stat.MELEE_DAMAGE) == 0, "plate gloves must give a mage nothing");
        helper.assertTrue(mage.get(Stat.ABILITY_DAMAGE) > 0, "jewelry must work for every class");

        StatBlock warrior = new StatBlock();
        EquipmentStats.collect(player, PlayerClass.WARRIOR, warrior);
        helper.assertTrue(warrior.get(Stat.MELEE_DAMAGE) > 0, "plate gloves must work for a warrior");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void setsCountSevenPiecesWithoutJewelry(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        Accessories accessories = RpgManager.get(player).accessories();
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        Item[] armor = {ModItems.WARRIOR_DAMASCUS_HELMET.get(), ModItems.WARRIOR_DAMASCUS_CHESTPLATE.get(),
                ModItems.WARRIOR_DAMASCUS_LEGGINGS.get(), ModItems.WARRIOR_DAMASCUS_BOOTS.get()};
        for (int i = 0; i < 3; i++) player.setItemSlot(slots[i], gladiator(new ItemStack(armor[i])));
        // Украшение с чужим тегом сета в пороги не идёт.
        accessories.set(3, gladiator(AccessoryItem.withGrade(ModItems.NECKLACE.get(), 3)));
        helper.assertTrue(!GearSets.hasPerk(player, GearSet.Perk.RANGED_GUARD), "3 pieces + jewelry must not give the half bonus");

        player.setItemSlot(slots[3], gladiator(new ItemStack(armor[3])));
        helper.assertTrue(GearSets.hasPerk(player, GearSet.Perk.RANGED_GUARD), "4 pieces must give the half bonus");
        helper.assertTrue(!GearSets.hasPerk(player, GearSet.Perk.NO_SLOWNESS), "4 pieces must not give the full bonus");

        accessories.set(0, gladiator(AccessoryItem.withGrade(ModItems.PLATE_GLOVES.get(), 3)));
        accessories.set(1, gladiator(AccessoryItem.withGrade(ModItems.PLATE_BELT.get(), 3)));
        accessories.set(2, gladiator(AccessoryItem.withGrade(ModItems.PLATE_BRACERS.get(), 3)));
        helper.assertTrue(GearSets.count(player).get(GearSet.GLADIATOR) == 7, "gloves, belt and bracers must count as set pieces");
        helper.assertTrue(GearSets.hasPerk(player, GearSet.Perk.NO_SLOWNESS), "7 pieces must give the full bonus");

        helper.assertTrue(!GearSets.canHaveSet(new ItemStack(ModItems.WARRIOR_CHESTPLATE.get())), "grade II must not roll a set");
        helper.assertTrue(!GearSets.canHaveSet(AccessoryItem.withGrade(ModItems.RING.get(), 6)), "jewelry must not roll a set");
        helper.assertTrue(GearSets.canHaveSet(new ItemStack(ModItems.WARRIOR_DAMASCUS_CHESTPLATE.get())), "grade III must roll sets");
        for (PlayerClass type : new PlayerClass[]{PlayerClass.WARRIOR, PlayerClass.ARCHER, PlayerClass.MAGE}) {
            helper.assertTrue(GearSet.forArmor(type).size() == 4, type + " armor must have 4 sets");
        }
        helper.succeed();
    }

    private static ItemStack gladiator(ItemStack stack) {
        stack.set(ModComponents.GEAR_SET, GearSet.GLADIATOR.id());
        return stack;
    }
}
