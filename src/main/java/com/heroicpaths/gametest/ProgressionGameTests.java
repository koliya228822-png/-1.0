package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.combat.PowerAttack;
import com.heroicpaths.item.SkillTable;
import com.heroicpaths.item.VanillaGear;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.Experience;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.Subclass;
import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import com.heroicpaths.rpg.ability.Casting;
import com.heroicpaths.rpg.ability.UniqueAbilities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Игровые тесты обновления прогрессии: опыт группы, силовая атака, отмена каста, уникальные умения, стол умений. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class ProgressionGameTests {
    private static final String ARENA = "gametest_arena";
    private static final List<String> UNIQUES = List.of("meteor_shower", "mana_surge", "hunting_frenzy", "critical_error", "boarding_hook",
            "healing_downpour", "summon_dracolich", "wake_the_beast", "slaughter", "hand_of_god", "lone_warrior", "full_steam",
            "just_getting_started", "true_stealth", "cutthroat");

    private static ServerPlayer hero(GameTestHelper helper, PlayerClass playerClass, Subclass subclass, int level) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        RpgManager.chooseClass(player, playerClass, subclass);
        RpgManager.setLevel(player, level);
        return player;
    }

    private static int learnAndBind(ServerPlayer player, String id) {
        RpgData data = RpgManager.get(player);
        data.learn(id);
        RpgManager.bindAbility(player, id, 0);
        return 0;
    }

    @GameTest(template = ARENA)
    public static void fifteenUniqueAbilities(GameTestHelper helper) {
        helper.assertTrue(AbilityRegistry.uniques().size() == UNIQUES.size(), "Уникальных умений должно быть 15, а их " + AbilityRegistry.uniques().size());
        for (String id : UNIQUES) {
            AbilityDef def = AbilityRegistry.get(id);
            helper.assertTrue(def != null && def.unique(), "Нет уникального умения " + id);
            helper.assertTrue(def.unlockLevel() == UniqueAbilities.UNLOCK_LEVEL, id + " должно открываться с 30-го уровня");
            helper.assertTrue(!UniqueAbilities.learners(def).isEmpty(), "Умение " + id + " никто не может выучить");
            for (Subclass subclass : UniqueAbilities.learners(def)) {
                helper.assertTrue(AbilityRegistry.isLearnable(subclass, def), subclass + " должен уметь учить " + id);
            }
        }
        helper.assertFalse(AbilityRegistry.isLearnable(Subclass.GUARDIAN, AbilityRegistry.get("meteor_shower")), "Страж не учит метеоритный дождь");
        helper.assertTrue(AbilityRegistry.bookAbilities(100).stream().noneMatch(AbilityDef::unique), "Уникальные книги не выпадают как обычные");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void partyXpFormula(GameTestHelper helper) {
        helper.assertTrue(Math.abs(Experience.partyFactor(2) - 0.8f * 1.14f) < 1e-4f, "Группа из двух: 80% + 7% за каждого");
        helper.assertTrue(Math.abs(Experience.partyFactor(5) - 0.8f * 1.35f) < 1e-4f, "Группа из пяти: 80% × 1.35");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void ancestorStoneGivesXp(GameTestHelper helper) {
        ServerPlayer player = hero(helper, PlayerClass.WARRIOR, Subclass.GUARDIAN, 1);
        int before = RpgManager.get(player).getLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ANCESTOR_STONE.get()));
        player.getMainHandItem().use(player.level(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(RpgManager.get(player).getLevel() > before, "Камень предков должен поднять уровень новичка (1500 опыта)");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void powerAttackHitsAndCoolsDown(GameTestHelper helper) {
        ServerPlayer player = hero(helper, PlayerClass.ROGUE, Subclass.DUELIST, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.IRON_DAGGER.get()));
        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 4));
        zombie.setNoAi(true);
        helper.runAfterDelay(2, () -> {
            float before = zombie.getHealth();
            helper.assertTrue(PowerAttack.perform(player, PowerAttack.CHARGE_TICKS), "Силовая атака должна сработать");
            helper.assertTrue(zombie.getHealth() < before, "Силовая атака должна ранить цель впереди");
            long end = RpgManager.get(player).getCooldownEnd(PowerAttack.COOLDOWN_KEY);
            helper.assertTrue(end - player.level().getGameTime() >= 40, "Перезарядка силовой атаки — не меньше 2 секунд");
            helper.assertFalse(PowerAttack.perform(player, PowerAttack.CHARGE_TICKS), "Во время перезарядки силовая атака недоступна");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void cancelledCastHasNoCooldown(GameTestHelper helper) {
        ServerPlayer player = hero(helper, PlayerClass.MAGE, Subclass.ELEMENTALIST, 10);
        RpgData data = RpgManager.get(player);
        data.setResource(1000);
        RpgManager.useAbility(player, RpgGameTests.slotOf(data, "meteor"));
        helper.assertTrue(Casting.isCasting(player), "Метеор произносится");
        Casting.cancel(player);
        helper.assertFalse(Casting.isCasting(player), "Каст отменён");
        helper.assertTrue(data.getCooldownEnd("meteor") <= player.level().getGameTime(), "Отменённый каст не уходит на перезарядку");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void meteorShowerAlwaysCoolsDown(GameTestHelper helper) {
        ServerPlayer player = hero(helper, PlayerClass.MAGE, Subclass.ELEMENTALIST, 30);
        RpgData data = RpgManager.get(player);
        data.setResource(1000);
        learnAndBind(player, "meteor_shower");
        RpgManager.useAbility(player, 0);
        helper.assertTrue(UniqueAbilities.channeling(player), "Метеоритный дождь поддерживается");
        Casting.cancel(player);
        helper.assertFalse(UniqueAbilities.channeling(player), "Метеоритный дождь прекращён");
        helper.assertTrue(data.getCooldownEnd("meteor_shower") > player.level().getGameTime(), "Метеоритный дождь всегда уходит на перезарядку");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void slaughterKeepsOneHealth(GameTestHelper helper) {
        ServerPlayer player = hero(helper, PlayerClass.WARRIOR, Subclass.BARBARIAN, 30);
        learnAndBind(player, "slaughter");
        RpgManager.useAbility(player, 0);
        helper.assertTrue(UniqueAbilities.slaughterActive(player), "Бойня включилась");
        Mob zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 4));
        zombie.setNoAi(true);
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().mobAttack(zombie), 1000f);
        helper.assertTrue(player.isAlive() && player.getHealth() >= 1f, "Под Бойней здоровье не падает ниже 1");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void skillTableMakesUniqueBooks(GameTestHelper helper) {
        ServerPlayer player = hero(helper, PlayerClass.MAGE, Subclass.ELEMENTALIST, 30);
        BlockPos table = new BlockPos(4, 2, 4);
        helper.setBlock(table, Blocks.ENCHANTING_TABLE);
        BlockPos absolute = helper.absolutePos(table);
        helper.assertTrue(SkillTable.tier(helper.getLevel(), absolute) == 1, "Без полок стол 1-го уровня");
        for (int dz = -1; dz <= 1; dz++) {
            helper.setBlock(table.offset(-2, 0, dz), ModBlocks.SKILL_BOOKSHELF.get());
            helper.setBlock(table.offset(2, 0, dz), ModBlocks.SKILL_BOOKSHELF.get());
        }
        helper.assertTrue(SkillTable.shelves(helper.getLevel(), absolute) == 6, "Вокруг стола 6 полок умений");
        helper.assertTrue(SkillTable.tier(helper.getLevel(), absolute) == 2, "С 6 полками стол 2-го уровня");
        player.getInventory().add(new ItemStack(Items.BOOK, 2));
        player.getInventory().add(new ItemStack(Items.LAPIS_LAZULI, 40));
        player.getInventory().add(new ItemStack(Items.DIAMOND));
        player.setExperienceLevels(40);
        helper.assertTrue(SkillTable.craft(player, absolute, "meteor_shower"), "Книга уникального умения создаётся");
        helper.assertTrue(SkillTable.count(player, ModItems.UNIQUE_ABILITY_BOOK.get()) == 1, "Книга в инвентаре");
        helper.assertTrue(SkillTable.count(player, Items.LAPIS_LAZULI) == 8 && player.experienceLevel == 10, "Списаны 32 лазурита и 30 уровней");
        helper.assertTrue(SkillTable.craft(player, absolute, ""), "Обычная книга умения создаётся");
        helper.assertTrue(SkillTable.count(player, ModItems.ABILITY_BOOK.get()) == 1 && SkillTable.count(player, Items.LAPIS_LAZULI) == 0,
                "Обычная книга стоит 8 лазурита");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void enchantedBooksAreGone(GameTestHelper helper) {
        helper.assertTrue(VanillaGear.isRemovedFromLoot(new ItemStack(Items.ENCHANTED_BOOK)), "Зачарованные книги убраны из добычи и торговли");
        helper.assertFalse(VanillaGear.isRemovedFromLoot(new ItemStack(Items.BOOK)), "Обычные книги остаются");
        helper.succeed();
    }
}
