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

/** Стамина бойцов: расход на удары, восстановление, слабые удары без стамины, у мага стамины нет. */
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
        helper.assertTrue(data.getStamina() == Stamina.MAX, "stamina starts full");
        float cost = Stamina.meleeCost(warrior, warrior.getAttackStrengthScale(0.5f));
        warrior.attack(dummy(helper, 3, 4));
        helper.assertTrue(Math.abs(data.getStamina() - (Stamina.MAX - cost)) < 0.01f,
                "an axe swing must cost " + cost + ", stamina is " + data.getStamina());

        float axe = Stamina.meleeCost(warrior, 1f);
        warrior.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        float sword = Stamina.meleeCost(warrior, 1f);
        helper.assertTrue(axe > sword && sword >= Stamina.MIN_MELEE_COST, "slow weapons cost more: axe " + axe + ", sword " + sword);

        float before = data.getStamina();
        Stamina.tick(warrior);
        helper.assertTrue(data.getStamina() == before, "stamina must not regenerate right after a swing");
        data.setStaminaUsedAt(warrior.level().getGameTime() - Stamina.REGEN_DELAY);
        Stamina.tick(warrior);
        helper.assertTrue(Math.abs(data.getStamina() - (before + Stamina.REGEN_PER_TICK)) < 0.01f, "stamina must regenerate after a second");
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
        data.setStamina(1f);
        helper.assertTrue(!Stamina.spend(rogue, 10f) && data.getStamina() == 0f, "not enough stamina empties it");
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

    @GameTest(template = ARENA)
    public static void mageHasNoStamina(GameTestHelper helper) {
        ServerPlayer mage = hero(helper, PlayerClass.MAGE, Subclass.ELEMENTALIST);
        mage.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        helper.assertTrue(!Stamina.uses(mage), "mages have no stamina");
        mage.attack(dummy(helper, 3, 4));
        helper.assertTrue(RpgManager.get(mage).getStamina() == Stamina.MAX, "a mage's swing must not spend stamina");
        helper.assertTrue(Stamina.uses(PlayerClass.ARCHER) && Stamina.uses(PlayerClass.HUNTER) && Stamina.uses(PlayerClass.WARRIOR)
                && Stamina.uses(PlayerClass.ROGUE) && !Stamina.uses(PlayerClass.NONE), "every fighter has stamina");
        helper.succeed();
    }
}
