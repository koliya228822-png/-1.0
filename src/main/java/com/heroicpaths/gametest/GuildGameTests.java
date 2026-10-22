package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.combat.Finishers;
import com.heroicpaths.dungeon.DungeonType;
import com.heroicpaths.guild.GuildOrders;
import com.heroicpaths.guild.Order;
import com.heroicpaths.guild.OrderCatalog;
import com.heroicpaths.guild.OrderType;
import com.heroicpaths.party.PartyManager;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.registry.ModVillagers;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.stat.Element;
import com.heroicpaths.village.VillageInjector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Игровые тесты этапа E: гильдия искателей приключений, заказы и добивания. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class GuildGameTests {
    private static final String ARENA = "gametest_arena";

    private static ServerPlayer adventurer(GameTestHelper helper) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    private static BlockPos board(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = player.blockPosition().east(2);
        helper.getLevel().setBlockAndUpdate(pos, ModBlocks.ORDER_BOARD.get().defaultBlockState());
        return pos;
    }

    private static Order order(String id, OrderType type, String target, int amount) {
        return new Order(id, type, target, "order.heroicpaths.target.zombies", "minecraft:zombie_spawn_egg", amount, 0, 100, 5, ItemStack.EMPTY, 0);
    }

    @GameTest(template = ARENA)
    public static void offersAreDailyAndValid(GameTestHelper helper) {
        BlockPos board = new BlockPos(10, 64, -20);
        List<Order> today = OrderCatalog.offers(42L, 7, board);
        List<Order> again = OrderCatalog.offers(42L, 7, board);
        helper.assertTrue(today.size() == OrderCatalog.OFFERS, "a board must offer " + OrderCatalog.OFFERS + " orders");
        for (int i = 0; i < today.size(); i++) {
            helper.assertTrue(today.get(i).id().equals(again.get(i).id()) && today.get(i).target().equals(again.get(i).target()),
                    "offers of the same board and day must be the same");
            helper.assertTrue(today.get(i).tier() == i / 2, "offers must go two per tier");
        }
        // Каталог: значки существуют, виды существ и предметы без тегов — тоже.
        for (long day = 0; day < 60; day++) {
            for (Order offer : OrderCatalog.offers(day * 31, day, board.offset((int) day, 0, 0))) {
                helper.assertTrue(!offer.iconStack().isEmpty() && !offer.iconStack().is(Items.AIR), "missing icon " + offer.icon());
                helper.assertTrue(offer.rewardXp() > 0 && offer.rewardEmeralds() > 0 && offer.amount() > 0, "bad reward in " + offer.id());
                if (offer.target().startsWith("#") || offer.type() == OrderType.DUNGEON) continue;
                ResourceLocation id = ResourceLocation.parse(offer.target());
                boolean known = offer.type() == OrderType.HUNT ? BuiltInRegistries.ENTITY_TYPE.containsKey(id) : BuiltInRegistries.ITEM.containsKey(id);
                helper.assertTrue(known, "unknown order target " + offer.target());
            }
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void partyHuntsTogether(GameTestHelper helper) {
        ServerPlayer leader = adventurer(helper);
        ServerPlayer member = adventurer(helper);
        BlockPos board = board(helper, leader);
        helper.assertTrue(PartyManager.create(leader) && PartyManager.invite(leader, member) && PartyManager.accept(member), "party was not formed");
        try {
            RpgData leaderData = RpgManager.get(leader);
            helper.assertTrue(GuildOrders.take(leader, board, 0), "the first offer could not be taken");
            helper.assertTrue(!GuildOrders.take(leader, board, 0), "the same order must not be taken twice a day");
            helper.assertTrue(leaderData.getOrders().size() == 1, "the taken order is missing");

            RpgData memberData = RpgManager.get(member);
            memberData.addOrder(order("test:hunt", OrderType.HUNT, "#minecraft:zombies", 2));
            for (int i = 0; i < 2; i++) {
                Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 1 + i * 2));
                zombie.hurt(leader.damageSources().playerAttack(leader), 1000);
            }
            Order hunt = memberData.getOrders().get(0);
            helper.assertTrue(hunt.progress() == 2 && hunt.isDone(), "the leader's kills must count for the member: " + hunt.progress());

            int before = member.getInventory().countItem(Items.EMERALD);
            helper.assertTrue(GuildOrders.turnIn(member, board, 0), "the finished order was not accepted");
            helper.assertTrue(memberData.getOrders().isEmpty() && member.getInventory().countItem(Items.EMERALD) == before + 5, "no reward");

            helper.assertTrue(GuildOrders.abandon(leader, 0) && leaderData.getOrders().isEmpty(), "abandon failed");
        } finally {
            PartyManager.leave(member);
            PartyManager.leave(leader);
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void deliveryAndGathering(GameTestHelper helper) {
        ServerPlayer player = adventurer(helper);
        BlockPos board = board(helper, player);
        RpgData data = RpgManager.get(player);
        data.addOrder(order("test:delivery", OrderType.DELIVERY, "minecraft:iron_ingot", 4));
        helper.assertTrue(!GuildOrders.turnIn(player, board, 0), "a delivery without items must fail");
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 5));
        helper.assertTrue(GuildOrders.turnIn(player, board, 0), "the delivery was not accepted");
        helper.assertTrue(player.getInventory().countItem(Items.IRON_INGOT) == 1, "the delivery must take exactly 4 ingots");

        data.addOrder(order("test:gather", OrderType.GATHER, "#minecraft:iron_ores", 1));
        BlockPos ore = player.blockPosition().west(1).north(1);
        helper.getLevel().setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
        player.gameMode.destroyBlock(ore);
        helper.assertTrue(data.getOrders().get(0).isDone(), "mining iron ore must advance the gathering order");

        player.moveTo(player.getX() + 30, player.getY(), player.getZ());
        helper.assertTrue(!GuildOrders.turnIn(player, board, 0), "orders are turned in only next to a board");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void dungeonClearCountsOncePerMember(GameTestHelper helper) {
        ServerPlayer leader = adventurer(helper);
        ServerPlayer member = adventurer(helper);
        helper.assertTrue(PartyManager.create(leader) && PartyManager.invite(leader, member) && PartyManager.accept(member), "party was not formed");
        try {
            RpgData memberData = RpgManager.get(member);
            memberData.addOrder(order("test:dungeon", OrderType.DUNGEON, "crypt", 2));
            GuildOrders.onDungeonCleared(helper.getLevel().getServer(), List.of(leader, member), DungeonType.CRYPT);
            helper.assertTrue(memberData.getOrders().get(0).progress() == 1, "one clear must count once even when both entered");
            GuildOrders.onDungeonCleared(helper.getLevel().getServer(), List.of(leader), DungeonType.FORGE);
            helper.assertTrue(memberData.getOrders().get(0).progress() == 1, "another dungeon must not count");
            GuildOrders.onDungeonCleared(helper.getLevel().getServer(), List.of(leader), DungeonType.CRYPT);
            helper.assertTrue(memberData.getOrders().get(0).isDone(), "a party member's clear must count");
        } finally {
            PartyManager.leave(member);
            PartyManager.leave(leader);
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void guildBuildingAndGuildmasterLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (String biome : VillageInjector.BIOMES) {
            ResourceLocation mentor = HeroicPaths.id("village/" + biome + "/guildmaster_mentor");
            helper.assertTrue(level.getStructureManager().get(mentor).isPresent(), "missing " + mentor);
            StructureTemplatePool pool = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).get(mentor);
            helper.assertTrue(pool != null && !pool.templates.isEmpty(), "missing pool " + mentor);
        }
        helper.assertTrue(ModVillagers.GUILDMASTER_POI.get().is(ModBlocks.ORDER_BOARD.get().defaultBlockState()), "the order board must be the guildmaster's workstation");
        helper.assertTrue(ModVillagers.BOUNTY_HERALD_POI.get().is(ModBlocks.BOUNTY_BOARD.get().defaultBlockState()), "the bounty board must be the herald's workstation");
        helper.succeed();
    }

    /** Глашатай: на доске наказов по наказу на босса каждого данжа, уровень — как у данжа, засчитывается всем у босса. */
    @GameTest(template = ARENA)
    public static void bossBountiesFromTheHerald(GameTestHelper helper) {
        ServerPlayer player = adventurer(helper);
        ServerLevel level = helper.getLevel();
        BlockPos board = player.blockPosition().east(2);
        level.setBlockAndUpdate(board, ModBlocks.BOUNTY_BOARD.get().defaultBlockState());
        List<Order> bounties = GuildOrders.offers(level, board);
        helper.assertTrue(bounties.size() == DungeonType.values().length, "the herald must offer one bounty per dungeon boss");
        for (int i = 0; i < bounties.size(); i++) {
            Order bounty = bounties.get(i);
            DungeonType type = DungeonType.values()[i];
            helper.assertTrue(bounty.type() == OrderType.BOSS && bounty.minLevel() == type.minLevel(), "bad bounty " + bounty.id());
            helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(bounty.target())), "unknown boss " + bounty.target());
            helper.assertTrue(!bounty.iconStack().isEmpty() && !bounty.bonus().isEmpty(), "bounty " + bounty.id() + " needs an icon and a bonus");
        }
        RpgData data = RpgManager.get(player);
        helper.assertTrue(GuildOrders.take(player, board, 0), "a level 1 hero must be able to take the clay colossus bounty");
        helper.assertTrue(!GuildOrders.take(player, board, DungeonType.values().length - 1), "the necropolis bounty needs level 50");

        LivingEntity colossus = (LivingEntity) helper.spawn(ModEntities.CLAY_COLOSSUS.get(), new BlockPos(6, 2, 6));
        LivingEntity spider = (LivingEntity) helper.spawn(ModEntities.SPIDER_QUEEN.get(), new BlockPos(2, 2, 6));
        GuildOrders.onBossKilled(level, spider, null);
        helper.assertTrue(!data.getOrders().get(0).isDone(), "another boss must not count");
        GuildOrders.onBossKilled(level, colossus, null);
        helper.assertTrue(data.getOrders().get(0).isDone(), "a player next to the slain boss must get the bounty");
        player.getInventory().clearContent();
        helper.assertTrue(GuildOrders.turnIn(player, board, 0), "the bounty was not turned in");
        helper.assertTrue(player.getInventory().countItem(ModItems.LESSER_SOUL_STONE.get()) == 1, "the colossus bounty pays a lesser soul stone");
        colossus.discard();
        spider.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void finishersMatchTheKillingBlow(GameTestHelper helper) {
        ServerPlayer player = adventurer(helper);
        helper.assertTrue(Finishers.styleFor(player.damageSources().onFire(), player) == Finishers.Style.FIRE, "fire");
        helper.assertTrue(Finishers.styleFor(player.damageSources().freeze(), player) == Finishers.Style.FROST, "freeze");
        helper.assertTrue(Finishers.styleFor(player.damageSources().lightningBolt(), player) == Finishers.Style.STORM, "lightning");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.IRON_FLANGED_MACE.get()));
        helper.assertTrue(Finishers.styleFor(player.damageSources().playerAttack(player), player) == Finishers.Style.SHATTER, "crushing");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.IRON_DAGGER.get()));
        helper.assertTrue(Finishers.styleFor(player.damageSources().playerAttack(player), player) == Finishers.Style.PIERCE, "piercing");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(Finishers.styleFor(player.damageSources().playerAttack(player), player) == Finishers.Style.BLOOD, "slashing");
        helper.assertTrue(Finishers.byElement(Element.EARTH) == Finishers.Style.EARTH && Finishers.byElement(Element.AIR) == Finishers.Style.STORM
                && Finishers.byElement(Element.HOLY) == Finishers.Style.HOLY, "element mapping");

        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        LivingEntity king = helper.spawn(ModEntities.CRYPT_KING.get(), new BlockPos(1, 2, 1));
        helper.assertTrue(Finishers.canFinish(zombie) && !Finishers.canFinish(king), "bosses must not get finishers");
        king.discard();
        Finishers.play(helper.getLevel(), zombie, Finishers.Style.FROST, player);
        helper.assertTrue(zombie.isInvisible(), "the frozen body must be hidden by the statue");
        List<Display.BlockDisplay> statues = helper.getLevel().getEntitiesOfClass(Display.BlockDisplay.class, zombie.getBoundingBox().inflate(2),
                display -> display.getTags().contains(Finishers.STATUE_TAG));
        helper.assertTrue(statues.size() == 1, "an ice statue must appear, found " + statues.size());
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(statues.get(0).isRemoved(), "the ice statue must shatter");
            helper.succeed();
        });
    }
}
