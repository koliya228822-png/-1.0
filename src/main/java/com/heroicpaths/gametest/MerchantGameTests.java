package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.registry.ModVillagers;
import com.heroicpaths.village.MentorHomes;
import com.heroicpaths.village.MerchantPosts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Банкир и мастер-оружейник: товары, смена ассортимента по дням, пост за прилавком. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class MerchantGameTests {
    private static final String ARENA = "gametest_arena";

    private static Villager merchant(GameTestHelper helper, VillagerProfession profession, BlockPos at) {
        Villager villager = helper.spawn(EntityType.VILLAGER, at);
        villager.setVillagerData(villager.getVillagerData().setProfession(profession));
        villager.setVillagerXp(1);
        return villager;
    }

    @GameTest(template = ARENA)
    public static void bankerBuysDungeonLootAndNeverLeavesHisPost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager banker = merchant(helper, ModVillagers.BANKER.get(), new BlockPos(4, 2, 4));
        MerchantPosts.keepPost(level, banker);
        BlockPos post = MentorHomes.home(banker);
        helper.assertTrue(post != null && post.equals(banker.blockPosition()), "banker post is " + post);

        MerchantPosts.stock(level, banker);
        List<MerchantOffer> offers = banker.getOffers();
        helper.assertTrue(offers.size() >= 50, "banker has only " + offers.size() + " offers");
        for (MerchantOffer offer : offers) {
            helper.assertTrue(offer.getResult().is(Items.EMERALD), "banker pays with " + offer.getResult());
        }
        for (var loot : List.of(ModItems.LICH_PHYLACTERY.get(), ModItems.RELIC_SPARK.get(), ModItems.ABILITY_BOOK.get(), ModItems.ANCIENT_SOUL_STONE.get(), Items.CLAY_BALL, Items.BONE)) {
            helper.assertTrue(offers.stream().anyMatch(o -> o.getBaseCostA().is(loot)), "banker does not buy " + loot);
        }
        // Отвели (оттолкнули, увели) — сразу обратно на пост.
        banker.teleportTo(post.getX() + 6.5, post.getY(), post.getZ() + 5.5);
        MerchantPosts.keepPost(level, banker);
        helper.assertTrue(banker.position().distanceTo(Vec3.atBottomCenterOf(post)) < 0.1, "banker stayed away at " + banker.position());
        helper.assertTrue(banker.getRestrictRadius() <= MerchantPosts.POST_LEASH, "banker may wander " + banker.getRestrictRadius() + " blocks");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void armsDealerSellsTenBladesAndBowsThatChangeDaily(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long dayTime = level.getServer().overworld().getDayTime();
        try {
            Villager dealer = merchant(helper, ModVillagers.ARMS_DEALER.get(), new BlockPos(4, 2, 4));
            MerchantPosts.stock(level, dealer);
            List<MerchantOffer> today = new ArrayList<>(dealer.getOffers());
            helper.assertTrue(today.size() == MerchantPosts.ARMS_OFFERS, "arms dealer has " + today.size() + " offers");
            int bows = 0;
            for (MerchantOffer offer : today) {
                ItemStack item = offer.getResult();
                helper.assertTrue(offer.getBaseCostA().is(Items.EMERALD), "price is not in emeralds: " + offer.getBaseCostA());
                helper.assertTrue(MerchantPosts.emeralds(offer) >= 15, item + " is too cheap: " + MerchantPosts.emeralds(offer));
                helper.assertTrue(ItemRarity.get(item) != null, item + " has no rarity");
                helper.assertTrue(offer.getMaxUses() == 1, "an item can be bought " + offer.getMaxUses() + " times");
                if (BuiltInRegistries.ITEM.getKey(item.getItem()).getPath().contains("bow")) bows++;
            }
            helper.assertTrue(bows == 3, "expected 3 bows, got " + bows);

            // Купленное не возвращается до конца дня.
            dealer.getPersistentData().putInt(HeroicPaths.MODID + ".sold", 1);
            dealer.getOffers().get(0).resetUses();
            MerchantPosts.stock(level, dealer);
            helper.assertTrue(dealer.getOffers().get(0).isOutOfStock(), "a sold item came back the same day");

            level.getServer().overworld().setDayTime(dayTime + 24000L);
            MerchantPosts.stock(level, dealer);
            List<MerchantOffer> tomorrow = dealer.getOffers();
            helper.assertTrue(tomorrow.size() == MerchantPosts.ARMS_OFFERS, "next day the dealer has " + tomorrow.size() + " offers");
            helper.assertFalse(tomorrow.get(0).isOutOfStock(), "yesterday's purchase blocks today's stock");
            boolean changed = false;
            for (int i = 0; i < today.size(); i++) {
                if (!ItemStack.isSameItemSameComponents(today.get(i).getResult(), tomorrow.get(i).getResult())) changed = true;
            }
            helper.assertTrue(changed, "the assortment did not change overnight");

            // Дорогая вещь оплачивается изумрудами и изумрудными блоками.
            MerchantOffer rich = MerchantPosts.priced(new ItemStack(ModItems.DAMASCUS_GREATSWORD.get()), 216);
            helper.assertTrue(rich.getBaseCostA().getCount() <= 64 && rich.getCostB().is(Items.EMERALD_BLOCK) && MerchantPosts.emeralds(rich) == 216,
                    "216 emeralds became " + rich.getBaseCostA() + " + " + rich.getCostB());
        } finally {
            level.getServer().overworld().setDayTime(dayTime);
        }
        helper.succeed();
    }
}
