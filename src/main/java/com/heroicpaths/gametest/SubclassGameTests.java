package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.MusketBall;
import com.heroicpaths.item.AbilityBookItem;
import com.heroicpaths.minion.CompanionManager;
import com.heroicpaths.minion.Minion;
import com.heroicpaths.minion.Minions;
import com.heroicpaths.minion.SummonedSkeleton;
import com.heroicpaths.registry.ModComponents;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.Subclass;
import com.heroicpaths.rpg.ability.AbilityLogic;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import com.heroicpaths.rpg.ability.WeaponReq;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Игровые тесты подклассов: оружие умений, маг крови, зверь охотника, призыватель, мушкет и книги умений. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class SubclassGameTests {
    private static final String ARENA = "gametest_arena";

    private static ServerPlayer player(GameTestHelper helper, PlayerClass playerClass, Subclass subclass) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.getInventory().clearContent();
        RpgManager.chooseClass(player, playerClass, subclass);
        return player;
    }

    @GameTest(template = ARENA)
    public static void abilitiesNeedTheirWeapon(GameTestHelper helper) {
        ServerPlayer player = player(helper, PlayerClass.WARRIOR, Subclass.GUARDIAN);
        RpgData data = RpgManager.get(player);
        int slot = RpgGameTests.slotOf(data, "shield_slam");
        RpgManager.useAbility(player, slot);
        helper.assertTrue(data.getCooldownEnd("shield_slam") == 0, "shield slam was used without a shield");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        RpgManager.useAbility(player, slot);
        helper.assertTrue(data.getCooldownEnd("shield_slam") > 0, "shield slam was not used with a shield");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.IRON_GREATSWORD.get()));
        helper.assertTrue(!WeaponReq.TWO_HANDED.isMet(player), "a greatsword with a shield in the off hand counts as two-handed");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        helper.assertTrue(WeaponReq.TWO_HANDED.isMet(player) && WeaponReq.MELEE.isMet(player), "greatsword requirements failed");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.IRON_DAGGER.get()));
        helper.assertTrue(WeaponReq.DAGGER.isMet(player) && !WeaponReq.AXE.isMet(player), "dagger requirements failed");
        helper.succeed();
    }

    /** Урон заклинаний мага берётся от жезла или посоха; без них заклинание урона не применить. */
    @GameTest(template = ARENA)
    public static void mageDamageComesFromTheFocus(GameTestHelper helper) {
        ServerPlayer mage = player(helper, PlayerClass.MAGE, Subclass.ELEMENTALIST);
        RpgData data = RpgManager.get(mage);
        helper.assertTrue(mage.getMainHandItem().is(ModItems.APPRENTICE_WAND.get()), "a new mage must receive an apprentice wand");
        float wand = AbilityLogic.damageFactor(mage);
        mage.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.APPRENTICE_STAFF.get()));
        float staff = AbilityLogic.damageFactor(mage);
        mage.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
        float staffWithOffhand = AbilityLogic.damageFactor(mage);
        mage.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        helper.assertTrue(Math.abs(wand - 0.4f) < 1e-4 && Math.abs(staff - 0.6f) < 1e-4, "wand/staff factors: " + wand + " / " + staff);
        helper.assertTrue(Math.abs(staffWithOffhand - 0.3f) < 1e-4, "a staff with a busy off hand must lose half its power");

        mage.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(AbilityLogic.damageFactor(mage) == 0f, "a mage without a focus must deal no damage");
        int fireball = RpgGameTests.slotOf(data, "fireball");
        RpgManager.useAbility(mage, fireball);
        helper.assertTrue(data.getCooldownEnd("fireball") == 0, "fireball was cast without a wand or staff");
        int blink = RpgGameTests.slotOf(data, "blink");
        RpgManager.useAbility(mage, blink);

        ServerPlayer warrior = player(helper, PlayerClass.WARRIOR, Subclass.GREATSWORD);
        helper.assertTrue(AbilityLogic.damageFactor(warrior) == 0.5f, "all classes must deal half ability damage");
        int mageCooldown = RpgManager.cooldown(mage, AbilityRegistry.get("frost_nova"));
        int expected = Math.round(160 * com.heroicpaths.rpg.PlayerStats.cooldownFactor(mage) * 1.5f);
        helper.assertTrue(mageCooldown == expected, "mage cooldowns must be 1.5x, got " + mageCooldown + " expected " + expected);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void bloodMagePaysWithHealth(GameTestHelper helper) {
        ServerPlayer player = player(helper, PlayerClass.MAGE, Subclass.BLOOD_MAGE);
        RpgData data = RpgManager.get(player);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 4));
        zombie.setNoAi(true);
        float resource = data.getResource();
        player.setHealth(player.getMaxHealth() - 6f);
        float health = player.getHealth();
        RpgManager.useAbility(player, RpgGameTests.slotOf(data, "blood_nova"));
        com.heroicpaths.rpg.ability.Casting.finishNow(player);
        helper.assertTrue(data.getResource() == resource, "a blood mage spent mana");
        helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "blood nova did not hit the zombie");
        float cost = PlayerStats.healthCost(RpgManager.cost(player, AbilityRegistry.get("blood_nova")));
        float dealt = zombie.getMaxHealth() - zombie.getHealth();
        float expected = health - cost + dealt * AbilityLogic.BLOOD_MAGE_LEECH;
        helper.assertTrue(player.getHealth() > health - cost, "blood mage did not leech any health back");
        helper.assertTrue(player.getHealth() <= expected + dealt * 0.1f + 0.01f, "too much health: " + player.getHealth() + " > " + expected);
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "hunter_companion", timeoutTicks = 200)
    public static void hunterCompanionGrowsWithItsMaster(GameTestHelper helper) {
        ServerPlayer player = player(helper, PlayerClass.HUNTER, Subclass.BEASTMASTER);
        RpgData data = RpgManager.get(player);
        RpgManager.useAbility(player, RpgGameTests.slotOf(data, "call_companion"));
        Mob companion = CompanionManager.find(player);
        helper.assertTrue(companion != null && companion.getType() == ModEntities.COMPANION_DOG.get(), "a level 1 hunter must get a dog");
        helper.assertTrue(Minions.isFriendly(companion, player) && Minions.isOwnedBy(companion, player), "the dog must belong to the hunter");
        float dogHealth = companion.getMaxHealth();

        RpgManager.setLevel(player, 30);
        Mob boar = CompanionManager.find(player);
        helper.assertTrue(boar != null && boar.getType() == ModEntities.COMPANION_BOAR.get(), "a level 30 hunter must have a boar, got " + boar);
        helper.assertTrue(!companion.isAlive(), "the old dog must be gone");
        helper.assertTrue(boar.getMaxHealth() > dogHealth, "the boar must be stronger than the dog");
        RpgManager.setLevel(player, 80);
        helper.assertTrue(CompanionManager.find(player).getType() == ModEntities.COMPANION_OWLBEAR.get(), "a level 80 hunter must have an owlbear");

        Mob owlbear = CompanionManager.find(player);
        owlbear.kill();
        helper.assertTrue(data.getCompanion() == null && data.getCompanionDownUntil() > player.level().getGameTime(),
                "a fallen companion must leave a cooldown");
        helper.assertTrue(!CompanionManager.call(player), "a fallen companion came back at once");
        helper.runAfterDelay(20, helper::succeed);
    }

    /** Кабан охотника сам находит и атакует монстра рядом. */
    @GameTest(template = ARENA, batch = "boar_fight", timeoutTicks = 300)
    public static void companionBoarAttacksMonsters(GameTestHelper helper) {
        ServerPlayer player = player(helper, PlayerClass.HUNTER, Subclass.BEASTMASTER);
        RpgManager.setLevel(player, 35);
        CompanionManager.call(player);
        Mob boar = CompanionManager.find(player);
        helper.assertTrue(boar != null && boar.getType() == ModEntities.COMPANION_BOAR.get(), "a level 35 hunter must have a boar");
        // Зомби далеко: кабан должен сам дойти до него.
        Vec3 at = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        boar.moveTo(at.x, at.y, at.z, 0, 0);
        Vec3 playerAt = helper.absoluteVec(new Vec3(1.5, 2, 2.5));
        player.moveTo(playerAt.x, playerAt.y, playerAt.z);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 7));
        zombie.setNoAi(true);
        helper.succeedWhen(() -> helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "the boar did not attack the zombie"));
    }

    /** Существа игроков ранят боссов данжа, а слуги босса — нет. */
    @GameTest(template = ARENA)
    public static void minionsCanHurtDungeonBosses(GameTestHelper helper) {
        ServerPlayer player = player(helper, PlayerClass.MAGE, Subclass.SUMMONER);
        com.heroicpaths.entity.FlameTyrant boss = helper.spawn(ModEntities.FLAME_TYRANT.get(), new BlockPos(5, 2, 5));
        boss.setNoAi(true);
        SummonedSkeleton skeleton = helper.spawn(ModEntities.SUMMONED_SKELETON.get(), new BlockPos(3, 2, 3));
        skeleton.minionData().setOwner(player.getUUID());
        helper.assertTrue(Minions.isValidTarget(skeleton, boss), "a minion must be allowed to target a boss");
        helper.assertTrue(boss.hurt(boss.damageSources().mobAttack(skeleton), 5f), "a player's minion could not hurt the boss");
        net.minecraft.world.entity.monster.Blaze blaze = helper.spawn(EntityType.BLAZE, new BlockPos(2, 2, 6));
        float health = boss.getHealth();
        helper.assertTrue(!boss.hurt(boss.damageSources().mobAttack(blaze), 5f) && boss.getHealth() == health, "the boss's own blazes hurt it");
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "summoner", timeoutTicks = 200)
    public static void summonerRaisesLoyalSkeletons(GameTestHelper helper) {
        ServerPlayer player = player(helper, PlayerClass.MAGE, Subclass.SUMMONER);
        RpgData data = RpgManager.get(player);
        RpgManager.useAbility(player, RpgGameTests.slotOf(data, "raise_skeletons"));
        com.heroicpaths.rpg.ability.Casting.finishNow(player);
        List<Mob> minions = Minions.ownedNear(player, 8);
        helper.assertTrue(minions.size() == 2, "expected 2 skeletons, got " + minions.size());
        for (Mob minion : minions) {
            helper.assertTrue(minion instanceof SummonedSkeleton && ((Minion) minion).minionData().lifetime() > 0, "skeleton must be temporary");
            helper.assertTrue(!Minions.isValidTarget(minion, player), "a skeleton may attack its master");
            helper.assertTrue(!AbilityLogic.canHarm(player, minion), "the master's abilities may hit the skeleton");
            helper.assertTrue(!minion.hurt(player.damageSources().playerAttack(player), 5f), "the master hurt a skeleton");
        }
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 4));
        helper.assertTrue(Minions.isValidTarget(minions.get(0), zombie), "skeletons must fight monsters");
        helper.runAfterDelay(20, helper::succeed);
    }

    @GameTest(template = ARENA)
    public static void musketFiresBallsAndReloads(GameTestHelper helper) {
        ServerPlayer player = player(helper, PlayerClass.ARCHER, Subclass.MUSKETEER);
        ServerLevel level = helper.getLevel();
        ItemStack musket = new ItemStack(ModItems.MUSKET.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, musket);
        musket.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(level.getEntitiesOfClass(MusketBall.class, player.getBoundingBox().inflate(8)).isEmpty(), "a musket fired without balls");
        player.getInventory().add(new ItemStack(ModItems.MUSKET_BALL.get(), 3));
        musket.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!level.getEntitiesOfClass(MusketBall.class, player.getBoundingBox().inflate(8)).isEmpty(), "no musket ball was fired");
        helper.assertTrue(player.getInventory().countItem(ModItems.MUSKET_BALL.get()) == 2, "a ball must be spent");
        helper.assertTrue(player.getCooldowns().isOnCooldown(ModItems.MUSKET.get()), "the musket must reload");
        helper.assertTrue(WeaponReq.MUSKET.isMet(player), "musket requirement failed");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void abilityBooksTeachTheRightSubclass(GameTestHelper helper) {
        ServerPlayer player = player(helper, PlayerClass.ROGUE, Subclass.DUELIST);
        RpgData data = RpgManager.get(player);
        RpgManager.setLevel(player, 10);
        ItemStack wrong = AbilityBookItem.create(ModItems.ABILITY_BOOK.get(), AbilityRegistry.get("meteor"));
        player.setItemInHand(InteractionHand.MAIN_HAND, wrong);
        wrong.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(!wrong.isEmpty() && !data.knows("meteor"), "a duelist learned a mage book");

        ItemStack book = AbilityBookItem.create(ModItems.ABILITY_BOOK.get(), AbilityRegistry.get("poison_blades"));
        player.setItemInHand(InteractionHand.MAIN_HAND, book);
        book.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(data.knows("poison_blades") && book.isEmpty(), "the poison blades book was not learned and spent");
        helper.assertTrue(AbilityRegistry.learners(AbilityRegistry.get("smoke_bomb")).containsAll(List.of(Subclass.DUELIST, Subclass.CROSSBOWMAN)),
                "smoke bomb is a rogue class book");
        helper.assertTrue(AbilityRegistry.bookAbilities(100).stream().noneMatch(def -> def.id().equals("charge")),
                "starting-only abilities must not drop as books");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void dungeonChestsHoldAbilityBooks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
                .create(LootContextParamSets.CHEST);
        List<ItemStack> loot = level.getServer().reloadableRegistries()
                .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, HeroicPaths.id("chests/frost_treasure"))).getRandomItems(params);
        List<ItemStack> books = loot.stream().filter(stack -> stack.is(ModItems.ABILITY_BOOK.get())).toList();
        helper.assertTrue(!books.isEmpty(), "the frost treasure has no ability books: " + loot);
        for (ItemStack book : books) {
            helper.assertTrue(AbilityBookItem.ability(book) != null && book.has(ModComponents.ABILITY.get()), "a dropped book teaches nothing");
        }
        helper.succeed();
    }
}
