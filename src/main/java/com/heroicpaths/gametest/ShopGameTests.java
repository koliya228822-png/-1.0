package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.craft.Profession;
import com.heroicpaths.craft.Professions;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.item.ProfessionManualItem;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.registry.ModVillagers;
import com.heroicpaths.accessory.AccessoryItem;
import com.heroicpaths.village.Bookseller;
import com.heroicpaths.village.GemCutter;
import com.heroicpaths.village.Jeweler;
import com.heroicpaths.village.MerchantPosts;
import com.heroicpaths.village.Pawnbroker;
import com.heroicpaths.village.TradeReroll;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Книжник, ювелир, огранщик, скупщик и реролл товаров жителей. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class ShopGameTests {
    private static final String ARENA = "gametest_arena";

    private static Villager villager(GameTestHelper helper, VillagerProfession profession, int level) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
        villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(level));
        villager.setVillagerXp(1);
        return villager;
    }

    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    @GameTest(template = ARENA)
    public static void booksellerSellsEveryManualDearerByRank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Весь список трактатов: каждое ремесло, каждая ступень, старше — дороже.
        List<MerchantOffer> all = Bookseller.offers();
        int ranks = Professions.MAX_RANK - Professions.MIN_RANK;
        helper.assertTrue(all.size() == Profession.values().length * ranks, "bookseller knows " + all.size() + " manuals");
        for (Profession craft : Profession.values()) {
            int last = 0;
            for (int rank = Professions.MIN_RANK + 1; rank <= Professions.MAX_RANK; rank++) {
                int r = rank;
                MerchantOffer offer = all.stream().filter(o -> o.getResult().is(craft.manual()) && ProfessionManualItem.rank(o.getResult()) == r)
                        .findFirst().orElse(null);
                helper.assertTrue(offer != null, "bookseller does not sell " + craft + " manual " + rank);
                helper.assertTrue(offer.getBaseCostA().is(Items.EMERALD), "manual is not sold for emeralds");
                int price = MerchantPosts.emeralds(offer);
                helper.assertTrue(price > last, craft + " manual " + rank + " costs " + price + ", not more than " + last);
                last = price;
            }
        }
        // На прилавке — дневной набор, реролл раскладывает другой.
        Villager seller = villager(helper, ModVillagers.BOOKSELLER.get(), 1);
        MerchantPosts.stock(level, seller);
        List<MerchantOffer> offers = seller.getOffers();
        helper.assertTrue(offers.size() == Bookseller.SHELF, "bookseller has " + offers.size() + " offers");
        List<String> before = offers.stream().map(o -> o.getResult().getItem() + "/" + ProfessionManualItem.rank(o.getResult())).toList();
        helper.assertTrue(TradeReroll.canReroll(seller), "bookseller cannot be rerolled");
        boolean changed = false;
        for (int i = 0; i < 5 && !changed; i++) {
            TradeReroll.reroll(seller, level.random);
            changed = !seller.getOffers().stream().map(o -> o.getResult().getItem() + "/" + ProfessionManualItem.rank(o.getResult())).toList().equals(before);
        }
        helper.assertTrue(changed, "bookseller reroll did not change the shelf");
        // Распроданное возвращается.
        seller.getOffers().get(0).setToOutOfStock();
        MerchantPosts.stock(level, seller);
        helper.assertFalse(seller.getOffers().get(0).isOutOfStock(), "bookseller ran out of manuals");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void jewelerAndGemCutterSellByLevelAndReroll(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager jeweler = villager(helper, ModVillagers.JEWELER.get(), 1);
        helper.assertTrue(MerchantPosts.isPost(jeweler) && TradeReroll.canReroll(jeweler), "jeweler is not a rerollable shopkeeper");
        MerchantPosts.stock(level, jeweler);
        helper.assertTrue(jeweler.getOffers().size() == Jeweler.OFFERS, "jeweler has " + jeweler.getOffers().size() + " offers");
        for (MerchantOffer offer : jeweler.getOffers()) {
            helper.assertTrue(offer.getResult().getItem() instanceof AccessoryItem, "jeweler sells " + offer.getResult());
            helper.assertTrue(AccessoryItem.grade(offer.getResult()) == 1, "novice jeweler sells grade " + AccessoryItem.grade(offer.getResult()));
        }
        // Купленное не возвращается до реролла; реролл раскладывает всё заново.
        jeweler.getOffers().get(0).setToOutOfStock();
        jeweler.getPersistentData().putInt(HeroicPaths.MODID + ".sold", 1);
        MerchantPosts.stock(level, jeweler);
        helper.assertTrue(jeweler.getOffers().get(0).isOutOfStock(), "a sold jewel came back");
        TradeReroll.reroll(jeweler, level.random);
        helper.assertTrue(jeweler.getOffers().stream().noneMatch(MerchantOffer::isOutOfStock), "reroll left sold-out jewels");

        Villager cutter = villager(helper, ModVillagers.GEM_CUTTER.get(), 1);
        helper.assertTrue(TradeReroll.canReroll(cutter), "gem cutter cannot be rerolled");
        MerchantPosts.stock(level, cutter);
        helper.assertFalse(cutter.getOffers().isEmpty(), "gem cutter has nothing");
        helper.assertTrue(cutter.getOffers().stream().noneMatch(o -> o.getResult().is(Items.DIAMOND) || o.getResult().is(ModItems.BRILLIANT.get())),
                "novice gem cutter sells diamonds");
        Villager master = villager(helper, ModVillagers.GEM_CUTTER.get(), 5);
        MerchantPosts.stock(level, master);
        helper.assertTrue(master.getOffers().stream().anyMatch(o -> o.getResult().is(ModItems.BRILLIANT.get())), "master gem cutter has no brilliant");
        helper.assertTrue(master.getOffers().size() <= GemCutter.OFFERS, "master gem cutter has " + master.getOffers().size() + " offers");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void pawnbrokerPaysAFifthAndBetterGearMore(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ItemStack common = new ItemStack(ModItems.STEEL_SWORD.get());
        ItemRarity.apply(common, ItemRarity.Tier.COMMON, helper.getLevel().random, helper.getLevel().registryAccess());
        ItemStack epic = new ItemStack(ModItems.STEEL_SWORD.get());
        ItemRarity.apply(epic, ItemRarity.Tier.EPIC, helper.getLevel().random, helper.getLevel().registryAccess());
        ItemStack damascus = new ItemStack(ModItems.DAMASCUS_SWORD.get());
        ItemRarity.apply(damascus, ItemRarity.Tier.COMMON, helper.getLevel().random, helper.getLevel().registryAccess());
        int commonPay = Pawnbroker.payment(common, 1), epicPay = Pawnbroker.payment(epic, 1), damascusPay = Pawnbroker.payment(damascus, 1);
        helper.assertTrue(epicPay > commonPay, "epic sword " + epicPay + " vs common " + commonPay);
        helper.assertTrue(damascusPay > commonPay, "damascus sword " + damascusPay + " vs steel " + commonPay);
        helper.assertTrue(commonPay == Math.max(1, (int) Math.floor(Pawnbroker.marketValue(common) / Pawnbroker.DISCOUNT)),
                "pawnbroker pays " + commonPay + " for a sword worth " + Pawnbroker.marketValue(common));
        // Дешёвое — не меньше изумруда; хлам он не берёт, изумруды тоже.
        helper.assertTrue(Pawnbroker.payment(new ItemStack(Items.IRON_INGOT), Pawnbroker.lot(new ItemStack(Items.IRON_INGOT))) >= 1, "iron lot pays nothing");
        helper.assertTrue(Pawnbroker.marketValue(new ItemStack(Items.DIRT)) == 0, "pawnbroker buys dirt");
        helper.assertTrue(Pawnbroker.marketValue(new ItemStack(Items.EMERALD)) == 0, "pawnbroker buys emeralds");

        player.getInventory().add(common.copy());
        player.getInventory().add(epic.copy());
        player.getInventory().add(new ItemStack(Items.DIRT, 32));
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 20));
        List<MerchantOffer> offers = Pawnbroker.offersFor(player);
        helper.assertTrue(offers.size() == 3, "pawnbroker made " + offers.size() + " offers");
        MerchantOffer epicOffer = offers.get(0);
        helper.assertTrue(epicOffer.satisfiedBy(epic, ItemStack.EMPTY), "the epic offer does not take the epic sword");
        helper.assertFalse(epicOffer.satisfiedBy(common, ItemStack.EMPTY), "the epic offer takes a common sword");
        helper.assertTrue(Pawnbroker.paid(epicOffer) == epicPay, "epic offer pays " + Pawnbroker.paid(epicOffer));

        Villager broker = villager(helper, ModVillagers.PAWNBROKER.get(), 1);
        helper.assertTrue(MerchantPosts.isPost(broker), "pawnbroker does not keep his post");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void rerollRefreshesTradesOfEveryLevelAndGetsDearer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long dayTime = level.getServer().overworld().getDayTime();
        try {
            ServerPlayer player = survivor(helper);
            Villager librarian = villager(helper, VillagerProfession.LIBRARIAN, 3);
            helper.assertTrue(TradeReroll.canReroll(librarian), "librarian cannot be rerolled");
            helper.assertFalse(TradeReroll.canReroll(villager(helper, ModVillagers.BANKER.get(), 1)), "banker can be rerolled");
            helper.assertFalse(TradeReroll.canReroll(villager(helper, ModVillagers.PAWNBROKER.get(), 1)), "pawnbroker can be rerolled");
            helper.assertTrue(TradeReroll.canReroll(villager(helper, ModVillagers.ARMS_DEALER.get(), 1)), "arms dealer cannot be rerolled");
            helper.assertFalse(TradeReroll.canReroll(villager(helper, VillagerProfession.NITWIT, 1)), "nitwit can be rerolled");

            TradeReroll.reroll(librarian, level.random);
            int count = librarian.getOffers().size();
            helper.assertTrue(count > 2 && count <= 6, "level 3 librarian got " + count + " trades");

            player.getInventory().add(new ItemStack(Items.EMERALD, 10));
            librarian.setTradingPlayer(player);
            player.containerMenu = new MerchantMenu(1, player.getInventory(), librarian);
            helper.assertTrue(TradeReroll.price(librarian) == TradeReroll.BASE_PRICE, "first reroll costs " + TradeReroll.price(librarian));
            int xp = librarian.getVillagerXp();
            TradeReroll.reroll(player);
            helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 7, "first reroll took " + (10 - player.getInventory().countItem(Items.EMERALD)));
            helper.assertTrue(librarian.getVillagerXp() == xp + TradeReroll.VILLAGER_XP, "reroll gave the trader " + (librarian.getVillagerXp() - xp) + " xp");
            helper.assertTrue(TradeReroll.price(librarian) == TradeReroll.BASE_PRICE + 1, "second reroll costs " + TradeReroll.price(librarian));
            TradeReroll.reroll(player);
            helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 3, "second reroll left " + player.getInventory().countItem(Items.EMERALD));
            // Не хватает изумрудов — ничего не происходит.
            TradeReroll.reroll(player);
            helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 3 && TradeReroll.price(librarian) == 5, "a reroll went through without money");

            // Утром цена снова 3.
            level.getServer().overworld().setDayTime((dayTime / 24000L + 1) * 24000L);
            helper.assertTrue(TradeReroll.price(librarian) == TradeReroll.BASE_PRICE, "next morning a reroll costs " + TradeReroll.price(librarian));
            librarian.setTradingPlayer(null);
            player.containerMenu = player.inventoryMenu;
        } finally {
            level.getServer().overworld().setDayTime(dayTime);
        }
        helper.succeed();
    }
}
