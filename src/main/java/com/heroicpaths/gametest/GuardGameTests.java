package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.VillageGuard;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.village.GuardPosts;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Стража деревни: бьёт нечисть, не трогает жителей, погибшего сменяет новый. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class GuardGameTests {
    private static final String ARENA = "gametest_arena";

    private static VillageGuard guard(GameTestHelper helper, EntityType<VillageGuard> type, BlockPos pos) {
        VillageGuard guard = helper.spawn(type, pos);
        guard.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(guard.blockPosition()), MobSpawnType.STRUCTURE, null);
        return guard;
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void swordsmanKillsZombie(GameTestHelper helper) {
        VillageGuard guard = guard(helper, ModEntities.GUARD_SWORDSMAN.get(), new BlockPos(2, 2, 2));
        helper.assertTrue(guard.getOffhandItem().getItem() != net.minecraft.world.item.Items.AIR, "swordsman has no shield");
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 7));
        helper.succeedWhen(() -> {
            helper.assertFalse(zombie.isAlive(), "zombie is still alive");
            helper.assertTrue(guard.isAlive(), "the swordsman died");
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void archerShootsZombie(GameTestHelper helper) {
        VillageGuard guard = guard(helper, ModEntities.GUARD_ARCHER.get(), new BlockPos(1, 2, 1));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(8, 2, 8));
        helper.succeedWhen(() -> {
            helper.assertFalse(zombie.isAlive(), "zombie is still alive");
            helper.assertTrue(guard.isAlive(), "archer died");
        });
    }

    @GameTest(template = ARENA, batch = "guard_respawn")
    public static void fallenGuardIsReplacedNextDay(GameTestHelper helper) {
        VillageGuard guard = guard(helper, ModEntities.GUARD_SWORDSMAN.get(), new BlockPos(3, 2, 3));
        guard.kill();
        AABB area = new AABB(helper.absolutePos(new BlockPos(3, 2, 3))).inflate(8);
        helper.assertTrue(GuardPosts.Vacancies.get(helper.getLevel().getServer()).all().size() >= 1, "no vacancy recorded");
        helper.assertTrue(GuardPosts.fillVacancies(helper.getLevel().getServer(), 0) == 0, "a guard came back at once");
        helper.assertTrue(GuardPosts.fillVacancies(helper.getLevel().getServer(), GuardPosts.RESPAWN_TICKS) >= 1, "no guard came back after a day");
        helper.assertFalse(helper.getLevel().getEntitiesOfClass(VillageGuard.class, area, VillageGuard::isAlive).isEmpty(), "new guard is not at the post");
        helper.succeed();
    }
}
