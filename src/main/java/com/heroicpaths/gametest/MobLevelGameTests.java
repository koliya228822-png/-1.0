package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.combat.MobLevels;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgEvents;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class MobLevelGameTests {
    private static final String ARENA = "gametest_arena";

    @GameTest(template = ARENA)
    public static void hostileMobsGetTheirBaseLevel(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
        Pig pig = helper.spawn(EntityType.PIG, 4, 2, 2);
        helper.assertTrue(MobLevels.level(zombie) == 5, "a zombie must be level 5, got " + MobLevels.level(zombie));
        helper.assertTrue(zombie.getMaxHealth() == 20f, "a normal zombie keeps 20 health, got " + zombie.getMaxHealth());
        helper.assertTrue(MobLevels.level(pig) == 0, "passive mobs have no level");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void higherLevelMobIsStrongerAndResistant(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
        MobLevels.assign(zombie, 5, 10, true);
        helper.assertTrue(MobLevels.level(zombie) == 10 && MobLevels.power(zombie) == 2f, "level 10 zombie must be twice as strong");
        helper.assertTrue(Math.abs(zombie.getMaxHealth() - 40f) < 0.01f, "level 10 zombie needs 40 health, got " + zombie.getMaxHealth());
        helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(), "a leveled mob spawns at full health");
        helper.assertTrue(MobLevels.resist(zombie) != null, "a mob above its level gets a resistance");
        helper.assertTrue(Math.abs(MobLevels.resistAmount(2f) - 0.25f) < 1e-4f, "double power gives 25% resistance");

        Pig pig = helper.spawn(EntityType.PIG, 4, 2, 2);
        pig.hurt(helper.getLevel().damageSources().mobAttack(zombie), 4f);
        helper.assertTrue(Math.abs(pig.getHealth() - 2f) < 0.01f, "4 damage from a level 10 zombie must deal 8, pig has " + pig.getHealth());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void higherLevelsAreRarer(GameTestHelper helper) {
        RandomSource random = RandomSource.create(42);
        int[] counts = new int[MobLevels.MAX_LEVEL + 1];
        int rolls = 20000;
        for (int i = 0; i < rolls; i++) counts[MobLevels.rollLevel(5, random)]++;
        helper.assertTrue(counts[4] == 0, "never below the base level");
        helper.assertTrue(Math.abs(counts[5] / (float) rolls - 0.75f) < 0.02f, "about 75% spawn at the base level, got " + counts[5]);
        for (int level = 6; level < 15; level++) {
            helper.assertTrue(counts[level] > counts[level + 1], "level " + (level + 1) + " must be rarer than " + level);
        }
        int doubled = 0;
        for (int level = 10; level <= MobLevels.MAX_LEVEL; level++) doubled += counts[level];
        helper.assertTrue(doubled > 0 && doubled < rolls * 0.15f, "level 10+ zombies must be rare, got " + doubled);
        helper.assertTrue(MobLevels.rollLevel(MobLevels.MAX_LEVEL, random) == MobLevels.MAX_LEVEL, "level is capped at 80");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void killXpDependsOnLevelGap(GameTestHelper helper) {
        helper.assertTrue(MobLevels.xpFactor(10, 15) == 1.2f, "5 levels above the player: +20%");
        helper.assertTrue(MobLevels.xpFactor(10, 5) == 0.1f, "5 levels below the player: 10%");
        helper.assertTrue(MobLevels.xpFactor(10, 13) == 1f && MobLevels.xpFactor(10, 7) == 1f, "a smaller gap changes nothing");

        ServerPlayer player = RpgGameTests.playerInArena(helper);
        RpgData data = RpgManager.get(player);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 2, 2, 2);
        data.setLevel(20);
        data.setXp(0);
        RpgEvents.awardKillXp(player, zombie); // 20 опыта × 10%
        helper.assertTrue(data.getXp() == 2, "a level 20 player gets 10% for a level 5 zombie, got " + data.getXp());
        helper.succeed();
    }
}
