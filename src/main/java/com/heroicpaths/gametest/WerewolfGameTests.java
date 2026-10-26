package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.Werewolf;
import com.heroicpaths.registry.ModEffects;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Оборотень: появляется только из яйца призыва и полосует когтями до кровотечения. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class WerewolfGameTests {
    private static final String ARENA = "gametest_arena";

    @GameTest(template = ARENA)
    public static void werewolfSpawnsOnlyFromEgg(GameTestHelper helper) {
        helper.assertTrue(ModItems.WEREWOLF_SPAWN_EGG.get().getType(ModItems.WEREWOLF_SPAWN_EGG.get().getDefaultInstance()) == ModEntities.WEREWOLF.get(),
                "the spawn egg must summon the werewolf");
        for (Biome biome : helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME)) {
            for (MobCategory category : MobCategory.values()) {
                for (MobSpawnSettings.SpawnerData data : biome.getMobSettings().getMobs(category).unwrap()) {
                    helper.assertTrue(data.type != ModEntities.WEREWOLF.get(), "werewolves must not spawn naturally");
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void werewolfClawsCauseBleeding(GameTestHelper helper) {
        Werewolf wolf = helper.spawn(ModEntities.WEREWOLF.get(), new BlockPos(2, 2, 2));
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(3, 2, 2));
        helper.assertTrue(wolf.getMaxHealth() >= 60, "werewolf must be tough");
        helper.assertTrue(wolf.doHurtTarget(golem), "werewolf must hit");
        helper.assertTrue(golem.hasEffect(ModEffects.BLEEDING), "claws must cause bleeding");
        helper.succeed();
    }
}
