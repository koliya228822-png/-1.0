package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.rpg.CoreStat;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.Subclass;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import com.heroicpaths.rpg.stat.Keystone;
import com.heroicpaths.rpg.stat.Stat;
import com.heroicpaths.rpg.tree.PassiveNode;
import com.heroicpaths.rpg.tree.PassiveTree;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Игровые тесты прокачки: очки, дерево, классы и подклассы, характеристики, книги и ранги, умения.
 * Запуск: gradlew runGameTestServer.
 */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class RpgGameTests {
    private static final String ARENA = "gametest_arena";

    static ServerPlayer playerInArena(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // В GameTest 1.21 относительный y = 1 — это слой пола арены, стоять нужно на y = 2.
        Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2, 4.5));
        player.moveTo(pos.x, pos.y, pos.z, -90f, 0f); // смотрит в сторону +X
        return player;
    }

    static int slotOf(RpgData data, String ability) {
        return RpgManager.slotOf(data, ability);
    }

    /** Первый узел ветки, которая начинается у старта класса. */
    private static int branchStart(PlayerClass playerClass, String branchId) {
        for (int id : PassiveTree.neighbours(PassiveTree.startNode(playerClass))) {
            if (PassiveTree.node(id).nameKey().equals("tree.heroicpaths." + branchId)) return id;
        }
        throw new IllegalStateException("No branch " + branchId);
    }

    private static int keystoneNode(Keystone keystone) {
        for (PassiveNode node : PassiveTree.nodes()) {
            if (node.keystone() == keystone) return node.id();
        }
        throw new IllegalStateException("No keystone " + keystone);
    }

    /** У каждого класса своё дерево: связи не переходят в чужие деревья, всё своё дерево связано со стартом, чужое не взять. */
    @GameTest(template = ARENA)
    public static void classTreesAreSeparate(GameTestHelper helper) {
        for (int[] edge : PassiveTree.edges()) {
            PassiveNode a = PassiveTree.node(edge[0]), b = PassiveTree.node(edge[1]);
            helper.assertTrue(a.region() == b.region() && a.region() != PlayerClass.NONE,
                    "an edge links different class trees: " + a.id() + " " + a.region() + " - " + b.id() + " " + b.region());
        }
        for (PlayerClass playerClass : PlayerClass.values()) {
            if (playerClass == PlayerClass.NONE) continue;
            IntSet own = new IntOpenHashSet();
            for (PassiveNode node : PassiveTree.nodes()) if (node.region() == playerClass && node.type() != com.heroicpaths.rpg.tree.NodeType.START) own.add(node.id());
            helper.assertTrue(PassiveTree.validAllocation(own, playerClass).size() == own.size(), playerClass + " tree is not connected to its start");
            for (PassiveNode node : PassiveTree.nodes())
                if (node.region() != playerClass) helper.assertTrue(!PassiveTree.canAllocate(own, playerClass, node.id()), playerClass + " can take node " + node.id());
            long keystones = own.intStream().filter(id -> PassiveTree.node(id).keystone() != null).count();
            helper.assertTrue(keystones == 2, playerClass + " must have two keystones, got " + keystones);
        }
        helper.succeed();
    }

    /** В дереве нет процентов: только числа, у бывших процентных бонусов — от 0.3 до 1 по глубине ветви. */
    @GameTest(template = ARENA)
    public static void treeGivesNumbersFromPointThreeToOne(GameTestHelper helper) {
        for (PassiveNode node : PassiveTree.nodes()) {
            for (var entry : node.stats().asMap().entrySet()) {
                helper.assertTrue(!entry.getKey().isPercent(), "node " + node.id() + " still gives a percent: " + entry.getKey());
                if (entry.getKey().name().startsWith("FLAT_"))
                    helper.assertTrue(entry.getValue() >= 0.3f - 1e-4 && entry.getValue() <= 1f + 1e-4,
                            "node " + node.id() + " gives " + entry.getValue() + " " + entry.getKey());
            }
        }
        // Ветвь «Мощь заклинаний» мага: 0.3, 0.5, 0.7, 0.85, значимый узел 1.
        int id = branchStart(PlayerClass.MAGE, "mage_power");
        float[] expected = {0.3f, 0.5f, 0.7f, 0.85f};
        for (float value : expected) {
            helper.assertTrue(Math.abs(PassiveTree.node(id).stats().get(Stat.FLAT_SPELL_DAMAGE) - value) < 1e-4,
                    "small node " + id + " must give " + value + " spell damage");
            int current = id;
            id = PassiveTree.neighbours(current).intStream().filter(next -> next > current && PassiveTree.node(next).region() == PlayerClass.MAGE
                    && PassiveTree.node(next).nameKey().equals(PassiveTree.node(current).nameKey())).findFirst()
                    .orElse(PassiveTree.neighbours(current).intStream().filter(next -> next > current
                            && PassiveTree.node(next).type() == com.heroicpaths.rpg.tree.NodeType.NOTABLE).findFirst().orElse(-1));
        }
        helper.assertTrue(id >= 0 && Math.abs(PassiveTree.node(id).stats().get(Stat.FLAT_SPELL_DAMAGE) - 1f) < 1e-4, "the notable must give 1 spell damage");
        // Числовая прибавка доходит до удара умения.
        ServerPlayer player = playerInArena(helper);
        RpgManager.chooseClass(player, PlayerClass.MAGE, Subclass.ELEMENTALIST);
        RpgManager.setLevel(player, 10);
        RpgManager.allocateNode(player, branchStart(PlayerClass.MAGE, "mage_power"));
        float flat = PlayerStats.flatAbilityDamage(player, AbilityRegistry.get("fireball"));
        helper.assertTrue(Math.abs(flat - 0.3f) < 1e-4, "the first spell node must add 0.3 to every spell hit, got " + flat);
        helper.succeed();
    }

    /** Узлы чужого дерева из старого сохранения снимаются, очки возвращаются, свои узлы остаются. */
    @GameTest(template = ARENA)
    public static void foreignTreeNodesAreRefunded(GameTestHelper helper) {
        ServerPlayer player = playerInArena(helper);
        RpgManager.chooseClass(player, PlayerClass.WARRIOR, Subclass.GUARDIAN);
        RpgManager.setLevel(player, 10);
        RpgData data = RpgManager.get(player);
        int own = branchStart(PlayerClass.WARRIOR, "warrior_life");
        helper.assertTrue(RpgManager.allocateNode(player, own), "own node was not allocated");
        int foreign = branchStart(PlayerClass.MAGE, "mage_fire");
        data.allocateNode(foreign);
        int removed = data.retainNodes(PassiveTree.validAllocation(data.getAllocatedNodes(), PlayerClass.WARRIOR));
        helper.assertTrue(removed == 1 && data.getAllocatedNodes().contains(own) && !data.getAllocatedNodes().contains(foreign),
                "the foreign node must be removed and the own one kept");
        helper.assertTrue(data.treePoints() == 9, "the point of the removed node must come back, got " + data.treePoints());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void levelsGrantTreeStatAndAbilityPoints(GameTestHelper helper) {
        ServerPlayer player = playerInArena(helper);
        RpgData data = RpgManager.get(player);
        helper.assertTrue(data.getLevel() == 1 && data.treePoints() == 1, "new player must be level 1 with 1 tree point");
        helper.assertTrue(data.statPoints() == 0 && data.abilityPoints() == 2, "start: 0 free stat points (2 in each stat) and 2 ability points");
        for (CoreStat stat : CoreStat.values()) helper.assertTrue(data.getStat(stat) == CoreStat.BASE, stat + " must start at 2");
        RpgManager.addXp(player, RpgData.xpToNextLevel(1) + RpgData.xpToNextLevel(2) + 5);
        helper.assertTrue(data.getLevel() == 3, "expected level 3, got " + data.getLevel());
        helper.assertTrue(data.treePoints() == 3 && data.statPoints() == 2, "each level must give a tree point and a stat point");
        helper.assertTrue(data.abilityPoints() == 3, "level 3 must give the third ability point, got " + data.abilityPoints());
        helper.assertTrue(data.getXp() == 5, "expected 5 leftover xp, got " + data.getXp());
        RpgManager.addXp(player, 10_000_000);
        helper.assertTrue(data.getLevel() == RpgData.MAX_LEVEL, "level must cap at " + RpgData.MAX_LEVEL);
        helper.assertTrue(data.abilityPoints() == 2 + RpgData.MAX_LEVEL / 3, "ability points at max level: " + data.abilityPoints());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void passiveTreeIsLargeAndConnected(GameTestHelper helper) {
        List<PassiveNode> nodes = PassiveTree.nodes();
        helper.assertTrue(nodes.size() >= 150, "tree must have at least 150 nodes, has " + nodes.size());
        for (PlayerClass playerClass : PlayerClass.playable()) {
            helper.assertTrue(PassiveTree.startNode(playerClass) >= 0, playerClass + " has no start on the tree");
        }
        // Деревья классов раздельные: от старта класса достижимо ровно его дерево, а вместе они покрывают всю карту.
        int total = 0;
        for (PlayerClass playerClass : PlayerClass.playable()) {
            IntSet reached = new IntOpenHashSet();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            int start = PassiveTree.startNode(playerClass);
            queue.add(start);
            reached.add(start);
            while (!queue.isEmpty()) {
                for (int next : PassiveTree.neighbours(queue.poll())) {
                    if (reached.add(next)) queue.add(next);
                }
            }
            long own = nodes.stream().filter(node -> node.region() == playerClass).count();
            helper.assertTrue(reached.size() == own, playerClass + " reaches " + reached.size() + " nodes, its tree has " + own);
            total += reached.size();
        }
        helper.assertTrue(total == nodes.size(), "class trees cover " + total + " of " + nodes.size() + " nodes");
        long keystones = nodes.stream().filter(node -> node.keystone() != null).count();
        helper.assertTrue(keystones == Keystone.values().length, "expected one node per keystone, got " + keystones);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void treeAllocationFollowsPaths(GameTestHelper helper) {
        ServerPlayer player = playerInArena(helper);
        RpgData data = RpgManager.get(player);
        RpgManager.chooseClass(player, PlayerClass.WARRIOR);
        RpgManager.setLevel(player, 5);

        int first = branchStart(PlayerClass.WARRIOR, "warrior_life");
        int second = PassiveTree.neighbours(first).intStream()
                .filter(id -> id != PassiveTree.startNode(PlayerClass.WARRIOR) && PassiveTree.node(id).nameKey().equals("tree.heroicpaths.warrior_life"))
                .findFirst().orElseThrow();
        int farAway = branchStart(PlayerClass.MAGE, "mage_fire");

        helper.assertTrue(!RpgManager.allocateNode(player, farAway), "a node far from the warrior start was allocated");
        helper.assertTrue(!RpgManager.allocateNode(player, second), "a node was allocated without its neighbour");
        helper.assertTrue(RpgManager.allocateNode(player, first), "first branch node was not allocated");
        helper.assertTrue(RpgManager.allocateNode(player, second), "second branch node was not allocated");
        helper.assertTrue(data.treePoints() == 3, "expected 3 points left, got " + data.treePoints());
        helper.assertTrue(!RpgManager.refundNode(player, first), "refunding a node in the middle of a path must fail");
        helper.assertTrue(RpgManager.refundNode(player, second), "refunding the end of a path must work");
        helper.assertTrue(data.treePoints() == 4, "refund must return the point");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void newClassesHaveTheirOwnBranches(GameTestHelper helper) {
        ServerPlayer player = playerInArena(helper);
        RpgManager.chooseClass(player, PlayerClass.HUNTER);
        RpgManager.setLevel(player, 3);
        helper.assertTrue(RpgManager.allocateNode(player, branchStart(PlayerClass.HUNTER, "hunter_beasts")), "hunter branch was not allocated");
        helper.assertTrue(!RpgManager.allocateNode(player, branchStart(PlayerClass.ROGUE, "rogue_blades")), "a hunter took a rogue start branch");
        helper.assertTrue(PlayerStats.get(player).get(Stat.MINION_DAMAGE) > 0, "hunter tree must strengthen the companion");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void statsAndTreeBecomeAttributes(GameTestHelper helper) {
        ServerPlayer player = playerInArena(helper);
        RpgManager.chooseClass(player, PlayerClass.WARRIOR);
        // 20 + 10 (старт воина) + 2 (телосложение 2)
        helper.assertTrue(player.getMaxHealth() == 32f, "warrior must have 32 health, got " + player.getMaxHealth());
        RpgManager.allocateNode(player, branchStart(PlayerClass.WARRIOR, "warrior_life"));
        helper.assertTrue(player.getMaxHealth() == 40f, "life node must give +8 health, got " + player.getMaxHealth());
        RpgManager.setLevel(player, 3);
        helper.assertTrue(RpgManager.addStat(player, CoreStat.CONSTITUTION), "constitution point was refused");
        helper.assertTrue(player.getMaxHealth() == 41f, "constitution must give +1 health, got " + player.getMaxHealth());
        RpgManager.addStat(player, CoreStat.CONSTITUTION);
        helper.assertTrue(!RpgManager.addStat(player, CoreStat.STRENGTH), "a stat point was spent without points");
        RpgManager.resetClass(player);
        helper.assertTrue(player.getMaxHealth() == 20f, "reset must remove all bonuses, got " + player.getMaxHealth());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void keystonesChangeTheRules(GameTestHelper helper) {
        ServerPlayer player = playerInArena(helper);
        RpgData data = RpgManager.get(player);
        RpgManager.chooseClass(player, PlayerClass.WARRIOR);
        float melee = PlayerStats.get(player).get(Stat.MELEE_DAMAGE);
        data.allocateNode(keystoneNode(Keystone.GLASS_CANNON));
        RpgManager.applyPassives(player);
        helper.assertTrue(PlayerStats.get(player).get(Stat.MAX_HEALTH_PCT) == -25f, "glass cannon must cut max health by 25%");
        helper.assertTrue(PlayerStats.get(player).get(Stat.MELEE_DAMAGE) == melee + 25f, "glass cannon must add 25% melee damage");

        data.allocateNode(keystoneNode(Keystone.BLOOD_RAGE));
        RpgManager.applyPassives(player);
        float resource = data.getResource();
        float health = player.getHealth();
        RpgManager.useAbility(player, slotOf(data, "charge")); // Рывок, 25 выносливости
        helper.assertTrue(data.getResource() == resource, "blood rage must not spend stamina");
        helper.assertTrue(Math.abs(player.getHealth() - (health - 6.25f)) < 0.01f,
                "blood rage must spend 6.25 health, health " + health + " -> " + player.getHealth());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void classAndSubclassAreChosenTogether(GameTestHelper helper) {
        ServerPlayer player = playerInArena(helper);
        RpgData data = RpgManager.get(player);
        helper.assertTrue(!RpgManager.chooseClass(player, PlayerClass.WARRIOR, Subclass.ELEMENTALIST), "a warrior chose a mage subclass");
        helper.assertTrue(RpgManager.chooseClass(player, PlayerClass.WARRIOR, Subclass.PALADIN), "paladin was not chosen");
        helper.assertTrue(!RpgManager.chooseClass(player, PlayerClass.MAGE, Subclass.SUMMONER), "class changed without a reset");
        helper.assertTrue(data.getSubclass() == Subclass.PALADIN, "subclass not saved");
        for (String id : List.of("smite", "holy_shield", "charge")) {
            helper.assertTrue(AbilityRegistry.isLearned(data, AbilityRegistry.get(id)), id + " must be known from the start");
        }
        helper.assertTrue(!AbilityRegistry.isLearned(data, AbilityRegistry.get("lay_on_hands")), "book abilities must not be known");
        helper.assertTrue(PlayerStats.get(player).get(Stat.HOLY_DAMAGE) >= 15f, "paladin must give holy damage");
        helper.assertTrue(slotOf(data, "smite") >= 0 && slotOf(data, "charge") >= 0, "starting abilities must fill the slots");
        helper.assertTrue(Subclass.forClass(PlayerClass.WARRIOR).size() == 4 && Subclass.forClass(PlayerClass.MAGE).size() == 4
                && Subclass.forClass(PlayerClass.ARCHER).size() == 2 && Subclass.forClass(PlayerClass.ROGUE).size() == 2
                && Subclass.forClass(PlayerClass.HUNTER).size() == 1, "wrong subclass counts");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void booksAndRanksSpendAbilityPoints(GameTestHelper helper) {
        ServerPlayer player = playerInArena(helper);
        RpgData data = RpgManager.get(player);
        RpgManager.chooseClass(player, PlayerClass.WARRIOR, Subclass.PALADIN);
        RpgManager.setLevel(player, 10);
        int points = data.abilityPoints();
        helper.assertTrue(points == 5, "level 10 must have 5 ability points, got " + points);
        helper.assertTrue(!RpgManager.learnAbility(player, AbilityRegistry.get("meteor")), "a paladin learned a mage book");
        helper.assertTrue(!RpgManager.learnAbility(player, AbilityRegistry.get("consecration")), "consecration needs level 20");
        helper.assertTrue(RpgManager.learnAbility(player, AbilityRegistry.get("lay_on_hands")), "lay on hands was not learned");
        helper.assertTrue(!RpgManager.learnAbility(player, AbilityRegistry.get("lay_on_hands")), "a book was learned twice");
        helper.assertTrue(data.knows("lay_on_hands") && data.abilityPoints() == points - 1, "learning must cost one point");
        helper.assertTrue(RpgManager.rankUp(player, "smite") && data.getRank("smite") == 2, "smite rank 2 failed");
        helper.assertTrue(RpgManager.rankUp(player, "smite") && data.getRank("smite") == 3, "smite rank 3 failed");
        helper.assertTrue(!RpgManager.rankUp(player, "smite"), "rank above 3");
        helper.assertTrue(data.abilityPoints() == points - 3, "ranks must cost points, left " + data.abilityPoints());
        helper.assertTrue(RpgManager.rankPower(player, AbilityRegistry.get("smite")) == 1.5f, "rank 3 must give +50% power");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void fireballSpendsManaAndCoolsDown(GameTestHelper helper) {
        ServerPlayer player = playerInArena(helper);
        RpgData data = RpgManager.get(player);
        RpgManager.chooseClass(player, PlayerClass.MAGE, Subclass.ELEMENTALIST);
        helper.assertTrue("fireball".equals(data.getSlot(0)), "fireball must be in slot 1");

        float manaBefore = data.getResource();
        RpgManager.useAbility(player, 0);
        helper.assertTrue(data.getResource() == manaBefore - 15, "mana was not spent: " + data.getResource());
        helper.assertTrue(data.getCooldownEnd("fireball") > player.level().getGameTime(), "cooldown was not set");
        helper.assertTrue(!player.serverLevel().getEntities(ModEntities.SPELL_PROJECTILE.get(),
                player.getBoundingBox().inflate(4), e -> true).isEmpty(), "fireball was not spawned");
        float manaAfter = data.getResource();
        RpgManager.useAbility(player, 0);
        helper.assertTrue(data.getResource() == manaAfter, "ability was cast during cooldown");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void frostNovaDamagesAndSlowsMonsters(GameTestHelper helper) {
        ServerPlayer player = playerInArena(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 4));
        RpgManager.chooseClass(player, PlayerClass.MAGE, Subclass.ELEMENTALIST);
        RpgManager.useAbility(player, slotOf(RpgManager.get(player), "frost_nova"));
        helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "zombie was not damaged");
        helper.assertTrue(zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "zombie was not slowed");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void chainLightningJumpsBetweenEnemies(GameTestHelper helper) {
        ServerPlayer player = playerInArena(helper);
        RpgManager.chooseClass(player, PlayerClass.MAGE, Subclass.ELEMENTALIST);
        RpgManager.setLevel(player, 15);
        RpgManager.learnAbility(player, AbilityRegistry.get("chain_lightning"));
        RpgData data = RpgManager.get(player);
        int slot = slotOf(data, "chain_lightning");
        helper.assertTrue(slot >= 0, "chain lightning is not in a slot");

        List<Zombie> zombies = List.of(
                helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4)),
                helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 3)),
                helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 6)));
        RpgManager.useAbility(player, slot);
        com.heroicpaths.rpg.ability.Casting.finishNow(player);
        long hurt = zombies.stream().filter(z -> z.getHealth() < z.getMaxHealth()).count();
        helper.assertTrue(hurt >= 2, "chain lightning hit only " + hurt + " zombies");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void killingMonstersGivesXp(GameTestHelper helper) {
        ServerPlayer player = playerInArena(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 4));
        zombie.hurt(player.damageSources().playerAttack(player), 1000f);
        RpgData data = RpgManager.get(player);
        helper.assertTrue(data.getXp() == 20, "zombie kill must give 20 xp, got " + data.getXp());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void manaRegenerates(GameTestHelper helper) {
        ServerPlayer player = playerInArena(helper);
        RpgData data = RpgManager.get(player);
        RpgManager.chooseClass(player, PlayerClass.MAGE);
        data.setResource(10f);
        RpgManager.tickRegen(player);
        // 3 у мага + 0.1 за каждое очко мудрости (2 на старте)
        helper.assertTrue(Math.abs(data.getResource() - 13.2f) < 0.01f, "mana must regen by 3.2, got " + data.getResource());
        helper.succeed();
    }

    /** Сохранение прошлой версии: специализация становится подклассом, открытые умения — выученными книгами. */
    @GameTest(template = ARENA)
    public static void oldSavesMoveToSubclasses(GameTestHelper helper) {
        ServerPlayer player = playerInArena(helper);
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", 2);
        tag.putString("class", "mage");
        tag.putInt("level", 25);
        tag.putString("spec", "pyromancer");
        tag.put("nodes", new IntArrayTag(new int[0]));
        ListTag slots = new ListTag();
        for (String id : List.of("fireball", "meteor", "flame_wave", "blink")) slots.add(StringTag.valueOf(id));
        tag.put("slots", slots);
        RpgData data = new RpgData();
        data.deserializeNBT(player.registryAccess(), tag);
        helper.assertTrue(data.getSubclass() == Subclass.ELEMENTALIST, "pyromancer must become elementalist, got " + data.getSubclass());
        helper.assertTrue(data.knows("meteor") && data.knows("flame_wave"), "old spec abilities must stay learned");
        helper.assertTrue("meteor".equals(data.getSlot(1)), "known abilities must keep their slots");
        helper.assertTrue(data.abilityPoints() == 2 + 25 / 3 - 2, "migrated abilities must use ability points, left " + data.abilityPoints());
        helper.assertTrue(data.statPoints() == 24, "all stat points must be free after migration");
        helper.succeed();
    }
}
