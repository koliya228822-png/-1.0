package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.dungeon.DungeonType;
import com.heroicpaths.entity.BossController;
import com.heroicpaths.entity.BroodSpider;
import com.heroicpaths.entity.BroodTank;
import com.heroicpaths.entity.SpiderEggSac;
import com.heroicpaths.entity.SpiderQueen;
import com.heroicpaths.entity.VenomSpitter;
import com.heroicpaths.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Игровые тесты Паучьего логова: Матерь выводка с тремя фазами, её пауки и кладки. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class SpiderLairGameTests {
    private static final String ARENA = "gametest_arena";

    /** Этажи логова: 1–2 только пауки выводка, 3 — пополам с плевальщиками, с 4-го каждый пятый — толстобрюх. */
    @GameTest(template = ARENA)
    public static void spiderFloorsMixTheBrood(GameTestHelper helper) {
        DungeonType lair = DungeonType.SPIDER_LAIR;
        EntityType<?> any = EntityType.CAVE_SPIDER;
        for (int floor = 1; floor <= 2; floor++)
            for (int i = 0; i < 10; i++)
                helper.assertTrue(lair.floorMob(floor, i, any) == ModEntities.BROOD_SPIDER.get(), "floor " + floor + " must be brood spiders only");
        int spitters = 0;
        for (int i = 0; i < 10; i++) if (lair.floorMob(3, i, any) == ModEntities.VENOM_SPITTER.get()) spitters++;
        helper.assertTrue(spitters == 5, "floor 3 must be half spitters, got " + spitters);
        int tanks = 0;
        for (int i = 0; i < 20; i++) if (lair.floorMob(5, i, any) == ModEntities.BROOD_TANK.get()) tanks++;
        helper.assertTrue(tanks == 4, "floor 5 must be 20% bloaters, got " + tanks);
        helper.succeed();
    }

    /** Пауки: плевальщик хлипче паука выводка, толстобрюх втрое крепче и гасит четверть урона; яд им не страшен. */
    @GameTest(template = ARENA)
    public static void broodSpidersHaveRoles(GameTestHelper helper) {
        BroodSpider spider = helper.spawn(ModEntities.BROOD_SPIDER.get(), new BlockPos(2, 2, 2));
        VenomSpitter spitter = helper.spawn(ModEntities.VENOM_SPITTER.get(), new BlockPos(4, 2, 2));
        BroodTank tank = helper.spawn(ModEntities.BROOD_TANK.get(), new BlockPos(6, 2, 4));
        helper.assertTrue(spitter.getMaxHealth() < spider.getMaxHealth(), "the spitter must be frailer than the brood spider");
        helper.assertTrue(tank.getMaxHealth() >= spider.getMaxHealth() * 3, "the bloater must be at least three times tougher");
        helper.assertTrue(tank.getAttributeValue(Attributes.MOVEMENT_SPEED) < spider.getAttributeValue(Attributes.MOVEMENT_SPEED), "the bloater must be slow");
        helper.assertTrue(!spider.canBeAffected(new MobEffectInstance(MobEffects.POISON, 40)), "spiders must ignore poison");
        float before = tank.getHealth();
        tank.hurt(tank.damageSources().magic(), 8f);
        helper.assertTrue(Math.abs(before - tank.getHealth() - 6f) < 0.5f, "the bloater must absorb a quarter of the damage, took " + (before - tank.getHealth()));
        spider.discard();
        spitter.discard();
        tank.discard();
        helper.succeed();
    }

    /** Кладка вылупляется пауком, если её не разбить. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void eggSacsHatch(GameTestHelper helper) {
        SpiderEggSac sac = helper.spawn(ModEntities.SPIDER_EGG_SAC.get(), new BlockPos(4, 2, 4));
        helper.runAfterDelay(SpiderEggSac.HATCH_TICKS + 5, () -> {
            helper.assertTrue(!sac.isAlive(), "the egg sac must be gone after hatching");
            var hatched = helper.getLevel().getEntitiesOfClass(BroodSpider.class, sac.getBoundingBox().inflate(4));
            helper.assertTrue(!hatched.isEmpty(), "a spider must hatch from the egg sac");
            helper.assertTrue(hatched.get(0).getTags().contains(BossController.MINION_TAG), "hatchlings belong to the Broodmother");
            hatched.forEach(Mob::discard);
            helper.succeed();
        });
    }

    /** Матерь выводка: на 2/3 здоровья вылупляются яйца со спины, на 1/3 раскалывается кокон; каждая фаза быстрее и сильнее. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void broodmotherHasThreePhases(GameTestHelper helper) {
        SpiderQueen boss = helper.spawn(ModEntities.SPIDER_QUEEN.get(), new BlockPos(4, 2, 4));
        helper.assertTrue(boss.getMaxHealth() >= 300, "the Broodmother is too weak: " + boss.getMaxHealth());
        helper.assertTrue(boss.phase() == 1, "the Broodmother must start in phase 1");
        double speed = boss.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double damage = boss.getAttributeValue(Attributes.ATTACK_DAMAGE);
        boss.setHealth(boss.getMaxHealth() * 0.6f);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(boss.phase() == 2, "below 2/3 health the Broodmother must enter phase 2, got " + boss.phase());
            helper.assertTrue(boss.action() == SpiderQueen.SHED, "phase change must hatch the eggs on her back");
            helper.assertTrue(!boss.hurt(boss.damageSources().generic(), 5f), "the Broodmother must be invulnerable while her eggs hatch");
            helper.assertTrue(boss.getAttributeValue(Attributes.MOVEMENT_SPEED) > speed, "phase 2 must be faster");
            int brood = helper.getLevel().getEntitiesOfClass(BroodSpider.class, boss.getBoundingBox().inflate(10)).size();
            helper.assertTrue(brood >= 1, "hatching eggs must release spiders");
            boss.setHealth(boss.getMaxHealth() * 0.2f);
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(boss.phase() == 3, "below 1/3 health the Broodmother must enter phase 3");
                helper.assertTrue(boss.getAttributeValue(Attributes.ATTACK_DAMAGE) > damage * 1.3, "phase 3 must hit much harder");
                helper.getLevel().getEntitiesOfClass(Mob.class, boss.getBoundingBox().inflate(20),
                        mob -> mob.getTags().contains(BossController.MINION_TAG)).forEach(Mob::discard);
                boss.discard();
                helper.succeed();
            });
        });
    }
}
