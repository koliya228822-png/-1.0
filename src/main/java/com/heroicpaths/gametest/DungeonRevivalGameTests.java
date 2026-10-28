package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.dungeon.DungeonInstance;
import com.heroicpaths.dungeon.DungeonManager;
import com.heroicpaths.dungeon.DungeonRevival;
import com.heroicpaths.dungeon.DungeonType;
import com.heroicpaths.dungeon.PersonalLoot;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/** Игровые тесты смерти в данже, раннего выхода и личной добычи в сундуках. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class DungeonRevivalGameTests {
    private static final String ARENA = "gametest_arena";

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    /** Тестовые игроки всегда отвечают isSpectator() = false, поэтому режим смотрим напрямую. */
    private static boolean spectator(ServerPlayer player) {
        return player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
    }

    private static int portals(ServerLevel level, BlockPos around) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(around.offset(-6, -1, -6), around.offset(6, 6, 6))) {
            if (level.getBlockState(pos).is(ModBlocks.DUNGEON_PORTAL.get())) count++;
        }
        return count;
    }

    /**
     * Полный проход вдвоём: зачистка предпоследнего этажа открывает врата у входа, спуск ниже закрывает их;
     * павший становится духом с вещами, свиток поднимает его, а когда падает вся группа — смерть окончательная
     * и вещи выпадают в данже.
     */
    @GameTest(template = ARENA, batch = "dungeon_revival", timeoutTicks = 400)
    public static void partyFallsRisesAndWipes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer first = player(helper);
        ServerPlayer second = player(helper);
        DungeonManager.testLevel = level;
        try {
            DungeonInstance instance = DungeonManager.create(level, DungeonType.CRYPT, List.of(first.getUUID(), second.getUUID()), null,
                    level.dimension(), helper.absolutePos(new BlockPos(1, 2, 1)));
            helper.assertTrue(instance != null, "instance was not created");
            helper.assertTrue(!instance.exitGuards().isEmpty(), "the 4th floor monsters were not tracked");
            helper.assertTrue(instance.exit() == DungeonInstance.ExitState.WAITING, "the entrance gate must wait for the 4th floor");

            // Предпоследний этаж зачищен — врата у входа ведут домой.
            for (UUID uuid : List.copyOf(instance.exitGuards())) {
                Entity guard = level.getEntity(uuid);
                if (guard != null) guard.kill();
                else instance.exitGuards().remove(uuid);
            }
            if (instance.exit() == DungeonInstance.ExitState.WAITING) DungeonManager.openExit(level, instance);
            helper.assertTrue(instance.exitGuards().isEmpty(), "killed guards are still tracked");
            helper.assertTrue(instance.exit() == DungeonInstance.ExitState.OPEN, "the entrance gate did not open: " + instance.exit());
            int open = portals(level, instance.arrival);
            helper.assertTrue(open == 12, "the entrance gate must be a 3x4 portal, got " + open);
            DungeonManager.closeExit(level, instance);
            helper.assertTrue(instance.exit() == DungeonInstance.ExitState.CLOSED && portals(level, instance.arrival) == 0,
                    "going down to the last floor must seal the entrance gate again");

            for (ServerPlayer player : List.of(first, second)) {
                instance.entered().add(player.getUUID());
                player.teleportTo(level, instance.arrival.getX() + 0.5, instance.arrival.getY(), instance.arrival.getZ() + 0.5, 0, 0);
            }
            first.getInventory().add(new ItemStack(Items.DIAMOND, 3));

            // Первый падает: становится духом и ничего не теряет.
            first.hurt(first.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(first.isAlive() && spectator(first) && DungeonRevival.isFallen(first), "the first player must fall, not die");
            helper.assertTrue(instance.fallen().contains(first.getUUID()), "the fallen player is not recorded");
            helper.assertTrue(first.getInventory().countItem(Items.DIAMOND) == 3, "a fallen player must keep the items");

            // Свиток воскрешения поднимает его рядом со вторым.
            helper.assertTrue(DungeonRevival.useScroll(second) == null, "the scroll did not work");
            helper.assertTrue(!spectator(first) && !DungeonRevival.isFallen(first) && first.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
                    "the scroll did not raise the fallen ally");
            helper.assertTrue(instance.fallen().isEmpty(), "the raised player is still among the fallen");
            helper.assertTrue(DungeonRevival.useScroll(second) != null, "the scroll must refuse when nobody has fallen");

            // Пали оба — смерть окончательная, вещи павшего выпадают в данже.
            first.invulnerableTime = 0; // после прошлого удара игрок ещё полсекунды неуязвим
            first.hurt(first.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(DungeonRevival.isFallen(first), "the first player must fall again");
            BlockPos fell = first.blockPosition();
            second.hurt(second.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(!second.isAlive(), "the last player of the party must die for real");
            helper.assertTrue(!first.isAlive() && !DungeonRevival.isFallen(first), "a party wipe must make the fallen die for real");
            helper.assertTrue(instance.fallen().isEmpty(), "nobody may stay fallen after a wipe");
            int dropped = 0;
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(fell).inflate(4))) {
                if (item.getItem().is(Items.DIAMOND)) dropped += item.getItem().getCount();
            }
            helper.assertTrue(dropped == 3, "the items must drop in the dungeon on the final death, found " + dropped);
        } finally {
            DungeonManager.testLevel = null;
        }
        helper.runAfterDelay(60, helper::succeed);
    }

    /** Победа над боссом поднимает павших. */
    @GameTest(template = ARENA, batch = "dungeon_revival_boss", timeoutTicks = 400)
    public static void bossKillRaisesTheFallen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer first = player(helper);
        ServerPlayer second = player(helper);
        DungeonManager.testLevel = level;
        try {
            DungeonInstance instance = DungeonManager.create(level, DungeonType.CRYPT, List.of(first.getUUID(), second.getUUID()), null,
                    level.dimension(), helper.absolutePos(new BlockPos(1, 2, 1)));
            helper.assertTrue(instance != null, "instance was not created");
            for (ServerPlayer player : List.of(first, second)) {
                instance.entered().add(player.getUUID());
                player.teleportTo(level, instance.arrival.getX() + 0.5, instance.arrival.getY(), instance.arrival.getZ() + 0.5, 0, 0);
            }
            first.hurt(first.damageSources().genericKill(), Float.MAX_VALUE);
            helper.assertTrue(DungeonRevival.isFallen(first), "the first player must fall");
            DungeonManager.completeInstance(level, instance, instance.bossArrival);
            helper.assertTrue(!DungeonRevival.isFallen(first) && !spectator(first) && first.isAlive(), "the boss kill must raise the fallen");
            helper.assertTrue(first.blockPosition().closerThan(instance.bossArrival, 3), "the raised player must stand by the way home");
            helper.assertTrue(first.getInventory().countItem(DungeonType.CRYPT.trophy()) == 1, "the raised player must get the boss reward too");
        } finally {
            DungeonManager.testLevel = null;
        }
        helper.runAfterDelay(60, helper::succeed);
    }

    /** Сундук данжа: у каждого своя добыча, общий сундук не наполняется, а сундуки гильдий остаются общими. */
    @GameTest(template = ARENA)
    public static void dungeonChestsArePersonal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer first = player(helper);
        ServerPlayer second = player(helper);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
        ChestBlockEntity chest = (ChestBlockEntity) level.getBlockEntity(pos);
        chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, HeroicPaths.id("chests/crypt_treasure")), 42L);

        Container mine = PersonalLoot.containerFor(first, level, pos);
        Container theirs = PersonalLoot.containerFor(second, level, pos);
        helper.assertTrue(mine != null && theirs != null && mine != theirs, "each player must get a share of their own");
        helper.assertTrue(chest.getLootTable() == null && chest.isEmpty(), "the shared chest must stay empty");
        helper.assertTrue(!mine.isEmpty() && !theirs.isEmpty(), "personal shares must be filled from the loot table");
        int before = 0;
        for (int i = 0; i < theirs.getContainerSize(); i++) before += theirs.getItem(i).getCount();
        mine.clearContent();
        int after = 0;
        for (int i = 0; i < theirs.getContainerSize(); i++) after += theirs.getItem(i).getCount();
        helper.assertTrue(before == after, "emptying one share must not touch the other");
        helper.assertTrue(PersonalLoot.containerFor(first, level, pos) == mine && mine.isEmpty(), "a looted share must not refill");

        helper.assertTrue(PersonalLoot.isDungeonTable(ResourceKey.create(Registries.LOOT_TABLE, HeroicPaths.id("chests/spider_lair"))),
                "spider lair chests must be personal");
        helper.assertTrue(PersonalLoot.isDungeonTable(ResourceKey.create(Registries.LOOT_TABLE, HeroicPaths.id("chests/crypt"))),
                "the overworld crypt chests must be personal");
        helper.assertTrue(!PersonalLoot.isDungeonTable(ResourceKey.create(Registries.LOOT_TABLE, HeroicPaths.id("chests/mage_guild"))),
                "guild chests must stay shared");
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    /** Свиток воскрешения крафтится из трёх цветов, четырёх костей, тотема и бумаги. */
    @GameTest(template = ARENA)
    public static void resurrectionScrollIsCraftable(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager().byKey(HeroicPaths.id("resurrection_scroll"));
        helper.assertTrue(recipe.isPresent(), "the resurrection scroll recipe is missing");
        helper.assertTrue(recipe.get().value().getResultItem(helper.getLevel().registryAccess()).is(ModItems.RESURRECTION_SCROLL.get()),
                "the recipe must make a resurrection scroll");
        helper.assertTrue(recipe.get().value().getIngredients().size() == 9, "the recipe must take 9 ingredients");
        helper.succeed();
    }

    /**
     * Монстры данжа привязаны к этажу: при постройке все получают метку, нераненый монстр не уходит с высоты своего
     * этажа (его возвращают), а после урона ходит где угодно.
     */
    @GameTest(template = ARENA, batch = "dungeon_floor_leash", timeoutTicks = 400)
    public static void unhurtMonstersStayOnTheirFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper);
        DungeonManager.testLevel = level;
        DungeonInstance instance = DungeonManager.create(level, DungeonType.CRYPT, List.of(player.getUUID()), null,
                level.dimension(), helper.absolutePos(new BlockPos(1, 2, 1)));
        helper.assertTrue(instance != null, "instance was not created");
        for (UUID guard : instance.exitGuards()) {
            helper.assertTrue(level.getEntity(guard) instanceof net.minecraft.world.entity.Mob mob && com.heroicpaths.dungeon.FloorLeash.isBound(mob),
                    "a dungeon monster is not bound to its floor");
        }
        net.minecraft.world.entity.monster.Zombie zombie = helper.spawn(net.minecraft.world.entity.EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        zombie.setNoAi(true);
        com.heroicpaths.dungeon.FloorLeash.bind(zombie);
        double home = zombie.getY();
        helper.runAfterDelay(5, () -> zombie.setPos(zombie.getX(), home + 3, zombie.getZ()));
        helper.runAfterDelay(7, () -> {
            try {
                helper.assertTrue(zombie.getY() < home + 1.3, "an unhurt monster left its floor: y " + zombie.getY() + " home " + home);
                zombie.hurt(level.damageSources().generic(), 1);
                helper.assertTrue(!com.heroicpaths.dungeon.FloorLeash.isBound(zombie), "a hurt monster stays bound");
                zombie.setPos(zombie.getX(), home + 3, zombie.getZ());
            } catch (RuntimeException e) {
                DungeonManager.testLevel = null;
                throw e;
            }
        });
        helper.runAfterDelay(8, () -> {
            DungeonManager.testLevel = null;
            helper.assertTrue(zombie.getY() > home + 1.5, "a hurt monster must be free to leave its floor");
            helper.succeed();
        });
    }
}
