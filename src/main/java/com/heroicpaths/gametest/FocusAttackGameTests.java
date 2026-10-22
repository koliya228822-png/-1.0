package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.MagicBoltEntity;
import com.heroicpaths.fx.Palette;
import com.heroicpaths.item.FocusItem;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.Subclass;
import com.heroicpaths.rpg.stat.Stat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Игровые тесты стрельбы из жезлов и посохов и того, кого задевают заклинания: мирных — да, своих — нет. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class FocusAttackGameTests {
    private static final String ARENA = "gametest_arena";

    /** Маг-стихийник с фокусом в руке, смотрит вдоль +X. */
    private static ServerPlayer mage(GameTestHelper helper, Item focus) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        RpgManager.chooseClass(player, PlayerClass.MAGE, Subclass.ELEMENTALIST);
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(focus));
        player.setYHeadRot(player.getYRot());
        RpgManager.get(player).setResource(PlayerStats.maxResource(player));
        return player;
    }

    /** Отпустить ПКМ после ticks тиков зарядки. */
    private static void shoot(ServerPlayer player, int ticks) {
        ItemStack stack = player.getMainHandItem();
        stack.getItem().releaseUsing(stack, player.serverLevel(), player, stack.getUseDuration(player) - ticks);
    }

    private static List<MagicBoltEntity> bolts(ServerPlayer player) {
        return player.serverLevel().getEntities(ModEntities.MAGIC_BOLT.get(), player.getBoundingBox().inflate(32), e -> true);
    }

    @GameTest(template = ARENA)
    public static void boltDamageIsFocusPowerPlusQuarterOfSpellPower(GameTestHelper helper) {
        ServerPlayer player = mage(helper, ModItems.APPRENTICE_WAND.get());
        float spell = PlayerStats.get(player).get(Stat.SPELL_DAMAGE);
        float expected = FocusItem.power(player) * (1f + 0.25f * spell / 100f);
        float actual = FocusItem.boltDamage(player, player.getMainHandItem());
        helper.assertTrue(spell > 0, "the mage has no spell power to test with");
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-4, "bolt damage " + actual + " != " + expected);
        helper.assertTrue(FocusItem.CHARGE_TICKS == 24, "the focus must charge 20% longer than a bow (24 ticks)");
        helper.assertTrue(FocusItem.chargeTicks(new ItemStack(ModItems.APPRENTICE_STAFF.get())) == 20
                && FocusItem.chargeTicks(new ItemStack(ModItems.APPRENTICE_WAND.get())) == 24, "a staff charges 20% faster than a wand");
        helper.assertTrue(Math.abs(MagicBoltEntity.SPEED * 20 - 5f) < 1.0E-4, "the bolt must fly 5 blocks per second");
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void focusShootsHomingElementalBoltForManaPercent(GameTestHelper helper) {
        ServerPlayer player = mage(helper, ModItems.FROST_RUNE_STAFF.get());
        RpgData data = RpgManager.get(player);
        // Хаск сбоку от линии выстрела: стрела должна довернуть к нему.
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(5, 2, 7));
        husk.setNoAi(true);
        float max = PlayerStats.maxResource(player), mana = data.getResource();
        shoot(player, 30);
        helper.assertTrue(Math.abs(data.getResource() - (mana - max * 0.05f)) < 1.0E-3, "a shot must cost 5% mana: " + data.getResource());
        List<MagicBoltEntity> shot = bolts(player);
        helper.assertTrue(shot.size() == 1 && shot.get(0).palette() == Palette.FROST, "a frost staff must shoot one frost bolt");
        float full = husk.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < full, "the bolt did not home in on the husk");
            helper.assertTrue(husk.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the frost bolt did not slow the husk");
        });
    }

    @GameTest(template = ARENA)
    public static void weakChargeShootsNothing(GameTestHelper helper) {
        ServerPlayer player = mage(helper, ModItems.APPRENTICE_STAFF.get());
        RpgData data = RpgManager.get(player);
        float mana = data.getResource();
        shoot(player, 1);
        helper.assertTrue(bolts(player).isEmpty() && data.getResource() == mana, "a barely charged focus must not shoot");
        RpgManager.resetClass(player);
        RpgManager.chooseClass(player, PlayerClass.WARRIOR, Subclass.GUARDIAN);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.APPRENTICE_STAFF.get()));
        shoot(player, 30);
        helper.assertTrue(bolts(player).isEmpty(), "only a mage may shoot from a staff");
        helper.succeed();
    }

    /** Огненный шар пролетает сквозь своего питомца и попадает в мирную корову за ним. */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void spellsHitPassiveMobsButSpareOwnPets(GameTestHelper helper) {
        ServerPlayer player = mage(helper, ModItems.APPRENTICE_WAND.get());
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(3, 2, 4));
        wolf.setNoAi(true);
        wolf.tame(player);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(7, 2, 4));
        cow.setNoAi(true);
        RpgManager.useAbility(player, RpgGameTests.slotOf(RpgManager.get(player), "fireball"));
        float wolfHealth = wolf.getHealth(), cowHealth = cow.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(cow.getHealth() < cowHealth, "the fireball did not hurt the cow (passive mobs must take spell damage)");
            helper.assertTrue(wolf.getHealth() == wolfHealth && !wolf.isOnFire(), "the fireball hurt the caster's own wolf");
        });
    }

    /** Ледяная нова ранит мирных животных и жителей вокруг, но не своего питомца. */
    @GameTest(template = ARENA)
    public static void novaHurtsPassiveMobs(GameTestHelper helper) {
        ServerPlayer player = mage(helper, ModItems.APPRENTICE_WAND.get());
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 2, 4));
        cow.setNoAi(true);
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 6));
        villager.setNoAi(true);
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(1, 2, 2));
        wolf.setNoAi(true);
        wolf.tame(player);
        RpgManager.useAbility(player, RpgGameTests.slotOf(RpgManager.get(player), "frost_nova"));
        helper.assertTrue(cow.getHealth() < cow.getMaxHealth(), "frost nova did not hurt the cow");
        helper.assertTrue(villager.getHealth() < villager.getMaxHealth(), "frost nova did not hurt the villager");
        helper.assertTrue(wolf.getHealth() == wolf.getMaxHealth(), "frost nova hurt the caster's own wolf");
        helper.succeed();
    }

    /** Магическая стрела бьёт мирное существо на своём пути (наводится она только на врагов). */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void boltHitsPassiveMobInItsPath(GameTestHelper helper) {
        ServerPlayer player = mage(helper, ModItems.FIRE_RUNE_WAND.get());
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(4, 2, 4));
        cow.setNoAi(true);
        float health = cow.getHealth();
        player.setXRot(15f);
        shoot(player, 30);
        helper.succeedWhen(() -> helper.assertTrue(cow.getHealth() < health, "the bolt flew through a cow"));
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void boltAimsAtTheVisibleEnemyInSight(GameTestHelper helper) {
        // Маг смотрит вдоль +X. Ближний враг прямо по прицелу, но за стеной; дальний — левее прицела, на виду.
        ServerPlayer player = mage(helper, ModItems.FIRE_RUNE_STAFF.get());
        Husk hidden = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 4));
        Husk seen = helper.spawn(EntityType.HUSK, new BlockPos(7, 2, 1));
        hidden.setNoAi(true);
        seen.setNoAi(true);
        helper.assertTrue(MagicBoltEntity.aimedTarget(player) == hidden && player.hasLineOfSight(seen), "without a wall the enemy at the crosshair is the target");
        for (int y = 2; y <= 4; y++)
            for (int z = 4; z <= 5; z++) helper.setBlock(new BlockPos(3, y, z), net.minecraft.world.level.block.Blocks.STONE);
        helper.assertTrue(MagicBoltEntity.aimedTarget(player) == seen, "behind a wall the visible enemy must be the target, got " + MagicBoltEntity.aimedTarget(player));
        shoot(player, 30);
        helper.assertTrue(bolts(player).stream().allMatch(b -> b.target() == seen), "the bolt must lock on the visible enemy");
        helper.succeed();
    }
}
