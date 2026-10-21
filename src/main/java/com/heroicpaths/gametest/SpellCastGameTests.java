package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.Subclass;
import com.heroicpaths.rpg.ability.AbilityLogic;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import com.heroicpaths.rpg.ability.Casting;
import com.heroicpaths.rpg.ability.Zones;
import com.heroicpaths.fx.Palette;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Игровые тесты сильных умений: время каста, метка с замедлением на 80% и метеор по рангу. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class SpellCastGameTests {
    private static final String ARENA = "gametest_arena";

    /** Маг-стихийник 10 уровня с выученным метеором и жезлом в руке; смотрит в пол в трёх блоках перед собой. */
    private static ServerPlayer meteorMage(GameTestHelper helper) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        RpgManager.chooseClass(player, PlayerClass.MAGE, Subclass.ELEMENTALIST);
        RpgManager.setLevel(player, 10);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.APPRENTICE_WAND.get()));
        helper.assertTrue(RpgManager.learnAbility(player, AbilityRegistry.get("meteor")), "meteor was not learned");
        RpgManager.get(player).setResource(PlayerStats.maxResource(player));
        // Взгляд берётся по повороту головы: повернуть её туда же, куда смотрит тело (+X).
        player.setYHeadRot(player.getYRot());
        player.setXRot(28f);
        return player;
    }

    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void meteorCastsThenStrikesThroughSlowingMark(GameTestHelper helper) {
        ServerPlayer player = meteorMage(helper);
        RpgData data = RpgManager.get(player);
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 4));
        husk.setNoAi(true);
        float mana = data.getResource();
        net.minecraft.world.phys.Vec3 aim = AbilityLogic.pickGround(player, 32);

        RpgManager.useAbility(player, RpgGameTests.slotOf(data, "meteor"));
        helper.assertTrue(Casting.isCasting(player), "meteor must be cast, not instant");
        helper.assertTrue(data.getCooldownEnd("meteor") == 0 && data.getResource() == mana, "cost and cooldown must wait for the cast");
        Casting.finishNow(player);
        helper.assertTrue(!Casting.isCasting(player), "the cast did not finish");
        helper.assertTrue(data.getCooldownEnd("meteor") > 0 && data.getResource() < mana, "finished cast did not spend mana or start the cooldown");
        helper.assertTrue(!player.serverLevel().getEntities(ModEntities.METEOR.get(), player.getBoundingBox().inflate(48), e -> true).isEmpty(),
                "no meteor in the sky");

        float full = husk.getHealth();
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(Zones.isSlowed(husk), "the mark did not slow the husk: aim " + aim + ", husk " + husk.position());
            double speed = husk.getAttributeValue(Attributes.MOVEMENT_SPEED), base = husk.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
            helper.assertTrue(Math.abs(speed - base * (1 - Zones.SLOW)) < 1.0E-3, "the mark must slow by 80%: " + speed + " of " + base);
        });
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(!Zones.isSlowed(husk), "the mark must fade half a second before the impact");
            helper.assertTrue(husk.getHealth() == full, "the meteor hit too early");
        });
        helper.runAfterDelay(34, () -> {
            helper.assertTrue(husk.getHealth() < full, "the meteor did not hit the husk");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void meteorGrowsWithRank(GameTestHelper helper) {
        ServerPlayer player = meteorMage(helper);
        RpgData data = RpgManager.get(player);
        var meteor = AbilityRegistry.get("meteor");
        helper.assertTrue(AbilityLogic.areaRadius(player, meteor) == 3, "rank 1 meteor must hit 3 blocks around");
        data.setRank("meteor", 3);
        helper.assertTrue(AbilityLogic.rank(player, meteor) == 3 && AbilityLogic.areaRadius(player, meteor) == 5, "rank 3 meteor must hit 5 blocks around");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void castIsInterruptedWithoutFocus(GameTestHelper helper) {
        ServerPlayer player = meteorMage(helper);
        RpgData data = RpgManager.get(player);
        float mana = data.getResource();
        RpgManager.useAbility(player, RpgGameTests.slotOf(data, "meteor"));
        helper.assertTrue(Casting.isCasting(player), "meteor must be cast");
        RpgManager.useAbility(player, RpgGameTests.slotOf(data, "fireball"));
        helper.assertTrue(data.getCooldownEnd("fireball") == 0, "another ability was used during a cast");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(!Casting.isCasting(player), "the cast went on without a focus");
            helper.assertTrue(data.getCooldownEnd("meteor") == 0 && data.getResource() == mana, "an interrupted cast must cost nothing");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void markSlowsOnlyEnemies(GameTestHelper helper) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 4));
        husk.setNoAi(true);
        Zones.mark(player, husk.position(), 2.5, Palette.FROST, 10);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(Zones.isSlowed(husk), "the mark did not slow an enemy");
            helper.assertTrue(!Zones.isSlowed(player), "the mark slowed its owner");
        });
        helper.runAfterDelay(16, () -> {
            helper.assertTrue(!Zones.isSlowed(husk), "the slow outlived the mark");
            helper.succeed();
        });
    }
}
