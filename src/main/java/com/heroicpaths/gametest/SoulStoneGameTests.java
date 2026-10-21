package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.craft.RelicForging;
import com.heroicpaths.craft.SoulStones;
import com.heroicpaths.dungeon.DungeonType;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.item.ItemRarity.Tier;
import com.heroicpaths.item.ItemTrait;
import com.heroicpaths.item.SoulStoneItem;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Игровые тесты камней души и реликвий. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class SoulStoneGameTests {
    private static final String ARENA = "gametest_arena";

    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    @GameTest(template = ARENA)
    public static void soulStonesFillFromKills(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ItemStack lesser = new ItemStack(ModItems.LESSER_SOUL_STONE.get());
        ItemStack greater = new ItemStack(ModItems.GREATER_SOUL_STONE.get());
        player.getInventory().setItem(5, lesser);
        player.getInventory().setItem(6, greater);

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        helper.assertTrue(SoulStones.soulsFor(zombie) == 4, "a zombie (20 hp, monster) must give 4 souls");
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 2, 3));
        helper.assertTrue(SoulStones.soulsFor(cow) == 1, "a cow must give 1 soul");
        helper.assertTrue(SoulStones.soulsFor(player) == 0, "players give no souls");

        zombie.hurt(player.damageSources().playerAttack(player), 1000f);
        helper.assertTrue(!zombie.isAlive() && SoulStoneItem.souls(lesser) == 4, "killing a zombie must fill the stone, got " + SoulStoneItem.souls(lesser));

        // Полный камень передаёт остаток следующему.
        helper.assertTrue(SoulStones.absorb(player, 50) == 50, "50 souls must fit into the two stones");
        helper.assertTrue(SoulStoneItem.souls(lesser) == 40 && SoulStoneItem.souls(greater) == 14,
                "overflow must go to the next stone: " + SoulStoneItem.souls(lesser) + "/" + SoulStoneItem.souls(greater));
        helper.assertTrue(lesser.getItem().isFoil(lesser) && !greater.getItem().isFoil(greater), "only a full stone shines");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void rareCraftsSpendSouls(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        SoulStones.Payment none = SoulStones.settle(player, Tier.RARE);
        helper.assertTrue(none.tier() == Tier.FINE && none.wanted() == Tier.RARE, "without a stone a rare roll must become fine");
        helper.assertTrue(SoulStones.settle(player, Tier.FINE).tier() == Tier.FINE, "fine items need no souls");

        ItemStack lesser = SoulStoneItem.full(ModItems.LESSER_SOUL_STONE.get());
        player.getInventory().setItem(5, lesser);
        SoulStones.Payment epic = SoulStones.settle(player, Tier.EPIC);
        helper.assertTrue(epic.tier() == Tier.UNIQUE && epic.wanted() == Tier.EPIC && SoulStoneItem.souls(lesser) == 24,
                "a lesser stone must turn an epic roll into a unique one for 16 souls");

        ItemStack ancient = SoulStoneItem.full(ModItems.ANCIENT_SOUL_STONE.get());
        player.getInventory().setItem(6, ancient);
        SoulStones.Payment legendary = SoulStones.settle(player, Tier.LEGENDARY);
        helper.assertTrue(legendary.tier() == Tier.LEGENDARY && legendary.wanted() == null && SoulStoneItem.souls(ancient) == 200,
                "an ancient stone must pay 100 souls for a legendary item");
        SoulStones.Payment rare = SoulStones.settle(player, Tier.RARE);
        helper.assertTrue(rare.tier() == Tier.RARE && SoulStoneItem.souls(lesser) == 16 && SoulStoneItem.souls(ancient) == 200,
                "rare items must take souls from the weakest fitting stone");

        player.setGameMode(GameType.CREATIVE);
        helper.assertTrue(SoulStones.settle(player, Tier.LEGENDARY).souls() == 0, "creative crafting needs no souls");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void relicAltarUpgradesLegendaryGear(GameTestHelper helper) {
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos altar = player.blockPosition().east(2);
        level.setBlockAndUpdate(altar, ModBlocks.RELIC_ALTAR.get().defaultBlockState());

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemRarity.apply(sword, Tier.LEGENDARY, RandomSource.create(4), level.registryAccess());
        int legendaryDurability = sword.getMaxDamage();
        List<ItemTrait.Roll> traitsBefore = ItemRarity.traits(sword);
        Map<String, Integer> levelsBefore = new HashMap<>();
        sword.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()
                .forEach(e -> levelsBefore.put(e.getKey().getRegisteredName(), e.getIntValue()));

        helper.assertTrue(!RelicForging.upgrade(player, sword, altar), "a relic must need a spark");
        player.getInventory().setItem(9, new ItemStack(ModItems.RELIC_SPARK.get(), 2));
        helper.assertTrue(!RelicForging.upgrade(player, sword, altar), "a relic must need experience levels");
        player.giveExperienceLevels(15);
        helper.assertTrue(RelicForging.upgrade(player, sword, altar), "the altar failed to forge a relic");

        helper.assertTrue(ItemRarity.get(sword) == Tier.RELIC, "the sword is not a relic");
        helper.assertTrue(sword.getMaxDamage() > legendaryDurability, "relic durability must grow");
        helper.assertTrue(player.experienceLevel == 5 && player.getInventory().countItem(ModItems.RELIC_SPARK.get()) == 1,
                "the ritual must take one spark and 10 levels");
        List<ItemTrait.Roll> traitsAfter = ItemRarity.traits(sword);
        helper.assertTrue(traitsAfter.size() == traitsBefore.size() + 1, "a relic must gain one more unique bonus");
        float scale = Tier.RELIC.traitPower() / Tier.LEGENDARY.traitPower();
        for (int i = 0; i < traitsBefore.size(); i++) {
            helper.assertTrue(traitsAfter.get(i).trait().equals(traitsBefore.get(i).trait())
                    && Math.abs(traitsAfter.get(i).value() - traitsBefore.get(i).value() * scale) < 0.06f, "legendary bonuses must grow, not reroll");
        }
        sword.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet().forEach(e -> {
            Integer before = levelsBefore.get(e.getKey().getRegisteredName());
            helper.assertTrue(before != null && e.getIntValue() == Math.min(before + 1, e.getKey().value().getMaxLevel() + 1),
                    "enchantments must rise by one level: " + e.getKey().getRegisteredName());
        });

        helper.assertTrue(!RelicForging.upgrade(player, sword, altar), "a relic must not be upgraded twice");
        ItemStack epic = new ItemStack(Items.IRON_SWORD);
        ItemRarity.apply(epic, Tier.EPIC, RandomSource.create(5), null);
        helper.assertTrue(!RelicForging.upgrade(player, epic, altar), "only legendary items become relics");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void bossesDropRelicSparks(GameTestHelper helper) {
        float last = 0;
        for (DungeonType type : DungeonType.values()) {
            helper.assertTrue(type.relicSparkChance() > last && type.relicSparkChance() < 0.5f, "spark chance must be small and grow with the dungeon");
            last = type.relicSparkChance();
        }
        helper.assertTrue(ItemRarity.chances(5)[Tier.RELIC.ordinal()] == 0f, "relics are never crafted");
        helper.succeed();
    }
}
