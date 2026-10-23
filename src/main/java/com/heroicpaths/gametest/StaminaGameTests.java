package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.combat.Stamina;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.Subclass;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Выносливость бойцов: расход на удары, восстановление, слабые удары без выносливости, у мага её нет. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class StaminaGameTests {
    private static final String ARENA = "gametest_arena";

    private static ServerPlayer hero(GameTestHelper helper, PlayerClass playerClass, Subclass subclass) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        RpgManager.chooseClass(player, playerClass, subclass);
        return player;
    }

    private static Mob dummy(GameTestHelper helper, int x, int z) {
        Mob mob = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(x, 2, z));
        mob.setNoAi(true);
        return mob;
    }

    @GameTest(template = ARENA)
    public static void meleeSpendsStaminaAndItComesBack(GameTestHelper helper) {
        ServerPlayer warrior = hero(helper, PlayerClass.WARRIOR, Subclass.GREATSWORD);
        warrior.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
        RpgData data = RpgManager.get(warrior);
        float max = Stamina.max(warrior);
        data.setResource(max);
        float cost = Stamina.meleeCost(warrior, warrior.getAttackStrengthScale(0.5f));
        warrior.attack(dummy(helper, 3, 4));
        // Черта воина «Боевая ярость» возвращает часть выносливости за каждый удар.
        float expected = max - cost + com.heroicpaths.rpg.ClassTraits.WARRIOR_STAMINA_PER_HIT;
        helper.assertTrue(Math.abs(data.getResource() - expected) < 0.01f,
                "an axe swing must cost " + cost + " endurance (minus the warrior's refund): expected " + expected + ", got " + data.getResource());

        float axe = Stamina.meleeCost(warrior, 1f);
        warrior.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        float sword = Stamina.meleeCost(warrior, 1f);
        helper.assertTrue(axe > sword && sword >= Stamina.MIN_MELEE_COST, "slow weapons cost more: axe " + axe + ", sword " + sword);

        float before = data.getResource();
        Stamina.tick(warrior);
        RpgManager.tickRegen(warrior);
        helper.assertTrue(data.getResource() == before, "stamina must not regenerate right after a swing");
        data.setStaminaUsedAt(warrior.level().getGameTime() - Stamina.REGEN_DELAY);
        Stamina.tick(warrior);
        helper.assertTrue(Math.abs(data.getResource() - (before + Stamina.regenPerTick(warrior))) < 0.01f
                && Stamina.regenPerTick(warrior) >= Stamina.REGEN_PER_TICK, "endurance must regenerate after a second");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void tiredHitsAreWeak(GameTestHelper helper) {
        ServerPlayer rogue = hero(helper, PlayerClass.ROGUE, Subclass.DUELIST);
        rogue.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        LivingEntity first = dummy(helper, 3, 4);
        float before = first.getHealth();
        first.hurt(rogue.damageSources().playerAttack(rogue), 10);
        float fresh = before - first.getHealth();

        RpgData data = RpgManager.get(rogue);
        data.setResource(1f);
        helper.assertTrue(!Stamina.spend(rogue, 10f) && data.getResource() == 0f, "not enough endurance empties it");
        LivingEntity second = dummy(helper, 5, 4);
        before = second.getHealth();
        second.hurt(rogue.damageSources().playerAttack(rogue), 10);
        float tired = before - second.getHealth();
        float crit = PlayerStats.applyCrit(rogue, 1f); // любой из двух ударов мог оказаться критом
        float ratio = tired / fresh;
        helper.assertTrue(ratio > Stamina.TIRED_FACTOR / crit - 0.01f && ratio < Stamina.TIRED_FACTOR * crit + 0.01f,
                "a tired hit must deal 40%: " + fresh + " -> " + tired);
        helper.succeed();
    }

    /** Умение тратит ту же выносливость, что и удары, и после него она копится тоже через секунду. */
    @GameTest(template = ARENA)
    public static void abilitiesAndAttacksShareEndurance(GameTestHelper helper) {
        ServerPlayer archer = hero(helper, PlayerClass.ARCHER, archerSubclass());
        RpgData data = RpgManager.get(archer);
        data.setResource(Stamina.max(archer));
        int slot = RpgManager.slotOf(data, "disengage");
        helper.assertTrue(slot >= 0, "the archer must have disengage bound");
        RpgManager.useAbility(archer, slot);
        helper.assertTrue(data.getResource() < Stamina.max(archer), "an ability must spend endurance");
        helper.assertTrue(data.getStaminaUsedAt() == archer.level().getGameTime(), "endurance waits a second after an ability too");
        helper.succeed();
    }

    private static Subclass archerSubclass() {
        for (Subclass subclass : Subclass.values()) if (subclass.playerClass() == PlayerClass.ARCHER) return subclass;
        throw new IllegalStateException("no archer subclass");
    }

    @GameTest(template = ARENA)
    public static void mageHasNoStamina(GameTestHelper helper) {
        ServerPlayer mage = hero(helper, PlayerClass.MAGE, Subclass.ELEMENTALIST);
        mage.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(!Stamina.uses(mage), "mages have no stamina");
        RpgManager.get(mage).setResource(50f);
        mage.attack(dummy(helper, 3, 4));
        helper.assertTrue(RpgManager.get(mage).getResource() == 50f, "a mage's swing must not spend mana");
        helper.assertTrue(Stamina.uses(PlayerClass.ARCHER) && Stamina.uses(PlayerClass.HUNTER) && Stamina.uses(PlayerClass.WARRIOR)
                && Stamina.uses(PlayerClass.ROGUE) && !Stamina.uses(PlayerClass.NONE), "every fighter has stamina");
        helper.succeed();
    }
}
