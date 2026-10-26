package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.craft.CraftEvents;
import com.heroicpaths.craft.Profession;
import com.heroicpaths.craft.ProfessionProgress;
import com.heroicpaths.craft.Professions;
import com.heroicpaths.craft.RuneEnchanting;
import com.heroicpaths.craft.StationCrafting;
import com.heroicpaths.gear.GearEvents;
import com.heroicpaths.item.HandCrossbowItem;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.item.ItemRarity.Tier;
import com.heroicpaths.item.ProfessionManualItem;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.village.MentorTrades;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Игровые тесты ремёсел, рунных чар и ручных арбалетов. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class ProfessionGameTests {
    private static final String ARENA = "gametest_arena";

    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    @GameTest(template = ARENA)
    public static void manualsRaiseCraftRanks(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        Professions professions = RpgManager.get(player).professions();
        helper.assertTrue(professions.rank(Profession.BLACKSMITH) == 1, "crafts must start at novice");

        // Без опыта трактат не читается и не тратится.
        ItemStack apprentice = ProfessionManualItem.create(ModItems.BLACKSMITH_MANUAL.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, apprentice);
        apprentice.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(professions.rank(Profession.BLACKSMITH) == 1 && apprentice.getCount() == 1, "manual worked without craft XP");

        professions.addXp(Profession.BLACKSMITH, Professions.xpForRank(2));
        apprentice.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(professions.rank(Profession.BLACKSMITH) == 2 && apprentice.isEmpty(), "apprentice manual must raise the rank and be used up");

        // Ступени идут по порядку.
        professions.addXp(Profession.BLACKSMITH, 5000);
        helper.assertTrue(professions.check(Profession.BLACKSMITH, 4) == Professions.Check.NEED_PREVIOUS, "rank 4 must need rank 3 first");
        for (int rank = 3; rank <= Professions.MAX_RANK; rank++) {
            helper.assertTrue(ProfessionProgress.readManual(player, Profession.BLACKSMITH, rank), "blacksmith manual " + rank + " failed");
        }

        // Любое ремесло, не только первое, растёт до легендарного мастера.
        for (Profession craft : Profession.values()) {
            professions.addXp(craft, Professions.xpForRank(Professions.MAX_RANK));
            for (int rank = professions.rank(craft) + 1; rank <= Professions.MAX_RANK; rank++) {
                helper.assertTrue(ProfessionProgress.readManual(player, craft, rank), craft + " manual " + rank + " failed");
            }
            helper.assertTrue(professions.rank(craft) == Professions.MAX_RANK, craft + " stopped at rank " + professions.rank(craft));
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void stationRecipesNeedCraftRanks(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos forge = player.blockPosition().east(2);
        level.setBlockAndUpdate(forge, ModBlocks.SMITHING_FORGE.get().defaultBlockState());
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 16));
        player.getInventory().add(new ItemStack(Items.COAL, 8));
        player.getInventory().add(new ItemStack(Items.STICK, 4));
        player.getInventory().add(new ItemStack(Items.GOLD_NUGGET, 4));
        Professions professions = RpgManager.get(player).professions();

        helper.assertTrue(StationCrafting.craft(player, HeroicPaths.id("station/forge/steel_plate"), forge, false) == 0,
                "a novice must not forge steel");
        int xpBefore = professions.xp(Profession.BLACKSMITH);
        helper.assertTrue(StationCrafting.craft(player, HeroicPaths.id("station/forge/iron_rapier"), forge, false) == 1,
                "a novice must forge grade I weapons");
        helper.assertTrue(professions.xp(Profession.BLACKSMITH) - xpBefore == 10, "grade I gear must give 10 craft XP");

        professions.setRank(Profession.BLACKSMITH, 2);
        helper.assertTrue(StationCrafting.craft(player, HeroicPaths.id("station/forge/steel_plate"), forge, false) == 1,
                "an apprentice must forge steel");

        // Верстак: лук даёт опыт плотнику.
        ItemStack bow = new ItemStack(Items.BOW);
        player.containerMenu.setCarried(bow);
        ItemRarity.onItemCrafted(new PlayerEvent.ItemCraftedEvent(player, bow, new SimpleContainer(9)));
        helper.assertTrue(professions.xp(Profession.CARPENTER) == ItemRarity.WORKBENCH_CRAFT_XP, "workbench bows must train the carpenter");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void craftRankImprovesRarity(GameTestHelper helper) {
        float[] master = ItemRarity.chances(Professions.MAX_RANK);
        float[] novice = ItemRarity.chances(Professions.MIN_RANK);
        float total = 0;
        for (float chance : master) total += chance;
        helper.assertTrue(Math.abs(total - 100f) < 0.01f, "chances must sum to 100%");
        helper.assertTrue(master[Tier.JUNK.ordinal()] == 0f && novice[Tier.JUNK.ordinal()] == 10f, "masters make no junk");
        helper.assertTrue(Math.abs(master[Tier.LEGENDARY.ordinal()] - 2f) < 0.001f, "master legendary chance must be 2%");

        RandomSource random = RandomSource.create(17);
        int[] counts = new int[Tier.values().length];
        for (int i = 0; i < 40000; i++) counts[ItemRarity.roll(random, Professions.MAX_RANK).ordinal()]++;
        helper.assertTrue(counts[Tier.JUNK.ordinal()] == 0, "a master rolled junk");
        helper.assertTrue(counts[Tier.LEGENDARY.ordinal()] > 650 && counts[Tier.LEGENDARY.ordinal()] < 950,
                "master legendary should be ~2%: " + counts[Tier.LEGENDARY.ordinal()]);
        helper.assertTrue(counts[Tier.RELIC.ordinal()] == 0, "relics are never crafted");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void vanillaEnchantingSkipsGear(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        EnchantmentLevelSetEvent sword = new EnchantmentLevelSetEvent(level, pos, 2, 15, new ItemStack(Items.IRON_SWORD), 30);
        CraftEvents.onEnchantmentLevel(sword);
        helper.assertTrue(sword.getEnchantLevel() == 0, "the enchanting table must not offer anything for a sword");
        EnchantmentLevelSetEvent chestplate = new EnchantmentLevelSetEvent(level, pos, 2, 15, new ItemStack(Items.IRON_CHESTPLATE), 30);
        CraftEvents.onEnchantmentLevel(chestplate);
        helper.assertTrue(chestplate.getEnchantLevel() == 0, "the enchanting table must not offer anything for armor");
        EnchantmentLevelSetEvent pickaxe = new EnchantmentLevelSetEvent(level, pos, 2, 15, new ItemStack(Items.IRON_PICKAXE), 30);
        CraftEvents.onEnchantmentLevel(pickaxe);
        helper.assertTrue(pickaxe.getEnchantLevel() == 30, "tools keep the enchanting table");
        helper.assertTrue(GearEvents.isBlockedAnvilRepair(new ItemStack(Items.BOW), new ItemStack(Items.ENCHANTED_BOOK)),
                "books must not enchant bows on the anvil");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void runeEnchantingFollowsRankAndRarity(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos table = player.blockPosition().east(2);
        level.setBlockAndUpdate(table, ModBlocks.RUNE_TABLE.get().defaultBlockState());
        RpgManager.get(player).professions().setRank(Profession.RUNECRAFTER, 2);
        player.giveExperienceLevels(40);
        // Не в слот основной руки: туда кладётся зачаровываемая вещь.
        player.getInventory().setItem(10, new ItemStack(ModItems.FIRE_RUNE.get(), 8));
        player.getInventory().setItem(11, new ItemStack(ModItems.FROST_RUNE.get(), 8));
        ResourceLocation fireAspect = Enchantments.FIRE_ASPECT.location();
        ResourceLocation sharpness = Enchantments.SHARPNESS.location();

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemRarity.apply(sword, Tier.COMMON, RandomSource.create(1), null);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        helper.assertTrue(RuneEnchanting.slots(sword) == 1, "common items hold one enchantment");
        helper.assertTrue(RuneEnchanting.enchant(player, fireAspect, table), "fire aspect I was not inscribed");
        helper.assertTrue(level(player, sword, fireAspect) == 1, "the sword has no fire aspect");
        helper.assertTrue(player.getInventory().countItem(ModItems.FIRE_RUNE.get()) == 7 && player.experienceLevel == 38,
                "fire aspect I must cost 1 fire rune and 2 levels");
        helper.assertTrue(!RuneEnchanting.enchant(player, sharpness, table), "a common item must not take a second enchantment");
        helper.assertTrue(RuneEnchanting.enchant(player, fireAspect, table) && level(player, sword, fireAspect) == 2,
                "upgrading fire aspect to II must not need a free slot");
        helper.assertTrue(RpgManager.get(player).professions().xp(Profession.RUNECRAFTER) >= Professions.xpForRank(2) + 3 * RuneEnchanting.XP_PER_LEVEL,
                "rune enchanting must train the rune enchanter");

        // Редкая вещь держит две чары, но ступень подмастерья — только до II уровня.
        ItemStack rare = new ItemStack(Items.IRON_SWORD);
        ItemRarity.apply(rare, Tier.RARE, RandomSource.create(2), null);
        player.setItemInHand(InteractionHand.MAIN_HAND, rare);
        helper.assertTrue(RuneEnchanting.enchant(player, sharpness, table) && RuneEnchanting.enchant(player, sharpness, table),
                "sharpness I-II failed on a rare sword");
        helper.assertTrue(!RuneEnchanting.enchant(player, sharpness, table) && level(player, rare, sharpness) == 2,
                "an apprentice must not inscribe sharpness III");
        helper.assertTrue(RuneEnchanting.enchant(player, fireAspect, table), "a rare item must take a second enchantment");

        // Мусор рун не держит; далеко от стола ничего не выходит.
        ItemStack junk = new ItemStack(Items.IRON_SWORD);
        ItemRarity.apply(junk, Tier.JUNK, RandomSource.create(3), null);
        player.setItemInHand(InteractionHand.MAIN_HAND, junk);
        helper.assertTrue(!RuneEnchanting.enchant(player, fireAspect, table), "junk must not hold runes");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        player.moveTo(player.getX() + 20, player.getY(), player.getZ());
        helper.assertTrue(!RuneEnchanting.enchant(player, sharpness, table), "rune enchanting must need the rune table nearby");
        helper.succeed();
    }

    private static int level(ServerPlayer player, ItemStack stack, ResourceLocation enchantment) {
        var holder = player.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(
                net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, enchantment));
        return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder);
    }

    @GameTest(template = ARENA)
    public static void handCrossbowsFireVolleys(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        ItemStack main = new ItemStack(ModItems.STEEL_HAND_CROSSBOW.get());
        ItemStack off = new ItemStack(ModItems.HAND_CROSSBOW.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        player.getInventory().add(new ItemStack(Items.ARROW, 10));
        HandCrossbowItem crossbow = (HandCrossbowItem) main.getItem();
        helper.assertTrue(crossbow.chargeDuration(main, player) < 25, "hand crossbows must reload faster than a crossbow");

        // Отпустили раньше времени — не зарядился.
        crossbow.releaseUsing(main, level, player, crossbow.getUseDuration(main, player) - 2);
        helper.assertTrue(!CrossbowItem.isCharged(main), "a short pull must not load the crossbow");
        // Полная зарядка основной руки заряжает и вторую.
        crossbow.releaseUsing(main, level, player, crossbow.getUseDuration(main, player) - crossbow.chargeDuration(main, player));
        helper.assertTrue(CrossbowItem.isCharged(main) && CrossbowItem.isCharged(off), "both hand crossbows must load together");
        helper.assertTrue(player.getInventory().countItem(Items.ARROW) == 8, "loading two crossbows must take two arrows");

        int arrowsBefore = level.getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(8)).size();
        crossbow.use(level, player, InteractionHand.MAIN_HAND);
        int arrowsAfter = level.getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(8)).size();
        helper.assertTrue(!CrossbowItem.isCharged(main) && !CrossbowItem.isCharged(off), "the volley must fire both crossbows");
        helper.assertTrue(arrowsAfter - arrowsBefore == 2, "the volley must shoot two bolts, got " + (arrowsAfter - arrowsBefore));
        helper.assertTrue(ItemRarity.canHaveRarity(main) && com.heroicpaths.item.Grades.of(main) == 2, "steel hand crossbow must be grade II gear");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void villagersSellManualsAndUnenchantedGear(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager trader = new Villager(EntityType.VILLAGER, level);
        Int2ObjectOpenHashMap<List<VillagerTrades.ItemListing>> trades = new Int2ObjectOpenHashMap<>();
        for (int i = 1; i <= 5; i++) trades.put(i, new ArrayList<>());
        trades.get(5).add(new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_SWORD, 10, 3, 5));
        MentorTrades.onVillagerTrades(new VillagerTradesEvent(trades, VillagerProfession.WEAPONSMITH));

        RandomSource random = RandomSource.create(5);
        boolean manualFound = false;
        for (VillagerTrades.ItemListing listing : trades.get(3)) {
            MerchantOffer offer = listing.getOffer(trader, random);
            if (offer != null && offer.getResult().is(ModItems.BLACKSMITH_MANUAL.get()) && ProfessionManualItem.rank(offer.getResult()) == 3) {
                manualFound = true;
            }
        }
        helper.assertTrue(manualFound, "a journeyman weaponsmith must sell the artisan blacksmith treatise");
        for (VillagerTrades.ItemListing listing : trades.get(5)) {
            MerchantOffer offer = listing.getOffer(trader, random);
            if (offer == null || !offer.getResult().is(Items.DIAMOND_SWORD)) continue;
            helper.assertTrue(!offer.getResult().isEnchanted(), "villager swords must come without enchantments");
            Tier tier = ItemRarity.get(offer.getResult());
            helper.assertTrue(tier == Tier.FINE || tier == Tier.RARE, "villager gear must be fine or rare instead, got " + tier);
        }
        helper.succeed();
    }
}
