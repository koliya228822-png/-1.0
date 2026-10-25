package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.combat.TwoHandedGrip;
import com.heroicpaths.combat.WeaponCombat;
import com.heroicpaths.combat.WeaponType;
import com.heroicpaths.item.Grades;
import com.heroicpaths.registry.ModDamageTypes;
import com.heroicpaths.registry.ModEffects;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.ability.WeaponReq;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Игровые тесты этапа C: типы урона оружия, кровотечение, оглушение, парное и двуручное оружие. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class CombatGameTests {
    private static final String ARENA = "gametest_arena";

    private static ServerPlayer fighter(GameTestHelper helper) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        return player;
    }

    private static <T extends Mob> T dummy(GameTestHelper helper, EntityType<T> type, int x, int z) {
        T mob = helper.spawn(type, new BlockPos(x, 2, z));
        mob.setNoAi(true);
        return mob;
    }

    /** Сколько здоровья цель потеряла от удара игрока с оружием в руках. */
    private static float hit(ServerPlayer player, LivingEntity target, float amount) {
        float before = target.getHealth();
        target.hurt(player.damageSources().playerAttack(player), amount);
        return before - target.getHealth();
    }

    @GameTest(template = ARENA)
    public static void weaponTypesAndGrips(GameTestHelper helper) {
        helper.assertTrue(WeaponType.of(new ItemStack(Items.IRON_SWORD)) == WeaponType.SLASHING
                && WeaponType.of(new ItemStack(Items.IRON_AXE)) == WeaponType.SLASHING
                && WeaponType.of(new ItemStack(ModItems.IRON_GREATSWORD.get())) == WeaponType.SLASHING, "swords and axes slash");
        helper.assertTrue(WeaponType.of(new ItemStack(ModItems.IRON_DAGGER.get())) == WeaponType.PIERCING
                && WeaponType.of(new ItemStack(ModItems.STEEL_RAPIER.get())) == WeaponType.PIERCING
                && WeaponType.of(new ItemStack(ModItems.IRON_SPEAR.get())) == WeaponType.PIERCING
                && WeaponType.of(new ItemStack(Items.TRIDENT)) == WeaponType.PIERCING, "daggers, rapiers, spears and tridents pierce");
        helper.assertTrue(WeaponType.of(new ItemStack(Items.MACE)) == WeaponType.CRUSHING
                && WeaponType.of(new ItemStack(ModItems.DAMASCUS_FLANGED_MACE.get())) == WeaponType.CRUSHING
                && WeaponType.of(new ItemStack(ModItems.IRON_WARHAMMER.get())) == WeaponType.CRUSHING, "maces and hammers crush");
        helper.assertTrue(WeaponType.of(new ItemStack(Items.STICK)) == null && WeaponType.of(new ItemStack(Items.SHIELD)) == null,
                "sticks and shields are not weapons");

        helper.assertTrue(WeaponType.isTwoHanded(new ItemStack(ModItems.IRON_GREATSWORD.get())) && WeaponType.isTwoHanded(new ItemStack(ModItems.IRON_SPEAR.get()))
                && WeaponType.isTwoHanded(new ItemStack(ModItems.IRON_WARHAMMER.get())), "greatswords, spears and warhammers are two-handed");
        helper.assertTrue(WeaponType.isOneHanded(new ItemStack(Items.IRON_SWORD)) && WeaponType.isOneHanded(new ItemStack(ModItems.IRON_FLANGED_MACE.get()))
                && WeaponType.isOneHanded(new ItemStack(ModItems.IRON_RAPIER.get())), "swords, flanged maces and rapiers are one-handed");

        ItemStack rapier = new ItemStack(ModItems.IRON_RAPIER.get());
        helper.assertTrue(!rapier.canPerformAction(ItemAbilities.SWORD_SWEEP) && new ItemStack(Items.IRON_SWORD).canPerformAction(ItemAbilities.SWORD_SWEEP),
                "only slashing weapons sweep");
        double[] reach = {0};
        new ItemStack(ModItems.IRON_SPEAR.get()).forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ENTITY_INTERACTION_RANGE)) reach[0] += modifier.amount();
        });
        helper.assertTrue(reach[0] == 1.0, "a spear must reach one block further");

        ServerPlayer player = fighter(helper);
        Zombie zombie = dummy(helper, EntityType.ZOMBIE, 3, 3);
        helper.assertTrue(new ItemStack(ModItems.IRON_WARHAMMER.get()).canDisableShield(new ItemStack(Items.SHIELD), player, zombie),
                "crushing weapons knock shields aside");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.IRON_WARHAMMER.get()));
        helper.assertTrue(WeaponReq.TWO_HANDED.isMet(player), "a warhammer counts as a two-handed weapon for abilities");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
        helper.assertTrue(!WeaponReq.TWO_HANDED.isMet(player), "two-handed abilities need a free off hand");
        helper.succeed();
    }

    /** Двуручное оружие закрывает вторую руку: предмет оттуда уходит в инвентарь, само оружие туда не кладётся. */
    @GameTest(template = ARENA)
    public static void twoHandedLocksOffHand(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STEEL_GREATSWORD.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH, 5));
        helper.assertTrue(TwoHandedGrip.enforce(player), "the off hand was not freed");
        helper.assertTrue(player.getOffhandItem().isEmpty() && player.getInventory().countItem(Items.TORCH) == 5,
                "the off-hand torches must go to the inventory");
        helper.assertTrue(!TwoHandedGrip.enforce(player), "an empty off hand needs nothing");
        helper.assertTrue(!TwoHandedGrip.canSwap(player.getMainHandItem()) && TwoHandedGrip.canSwap(new ItemStack(ModItems.STEEL_SWORD.get())),
                "two-handed weapons cannot be swapped into the off hand");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.IRON_WARHAMMER.get()));
        helper.assertTrue(TwoHandedGrip.enforce(player) && player.getOffhandItem().isEmpty(), "a two-handed weapon cannot stay in the off hand");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STEEL_SWORD.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        helper.assertTrue(!TwoHandedGrip.enforce(player) && player.getOffhandItem().is(Items.SHIELD), "one-handed weapons keep the off hand free to use");
        helper.succeed();
    }

    /** Щиты с грейдом: грейд по материалу, прочность и броня растут с грейдом, все блокируют и годятся для умений щита. */
    @GameTest(template = ARENA)
    public static void gradedShields(GameTestHelper helper) {
        ItemStack iron = new ItemStack(ModItems.IRON_HEATER_SHIELD.get()), steel = new ItemStack(ModItems.STEEL_KITE_SHIELD.get());
        ItemStack damascus = new ItemStack(ModItems.DAMASCUS_HEATER_SHIELD.get());
        helper.assertTrue(Grades.of(iron) == 1 && Grades.of(steel) == 2 && Grades.of(damascus) == 3, "shield grades follow the material");
        helper.assertTrue(damascus.getMaxDamage() > iron.getMaxDamage() && iron.getMaxDamage() > new ItemStack(ModItems.IRON_BUCKLER.get()).getMaxDamage(),
                "durability grows with grade and size");
        helper.assertTrue(iron.canPerformAction(ItemAbilities.SHIELD_BLOCK) && new ItemStack(ModItems.DAMASCUS_BUCKLER.get()).canPerformAction(ItemAbilities.SHIELD_BLOCK),
                "graded shields block");
        double[] armor = {0, 0}, speed = {0};
        iron.forEachModifier(EquipmentSlot.OFFHAND, (attribute, modifier) -> { if (attribute.is(Attributes.ARMOR)) armor[0] += modifier.amount(); });
        damascus.forEachModifier(EquipmentSlot.OFFHAND, (attribute, modifier) -> { if (attribute.is(Attributes.ARMOR)) armor[1] += modifier.amount(); });
        steel.forEachModifier(EquipmentSlot.OFFHAND, (attribute, modifier) -> { if (attribute.is(Attributes.MOVEMENT_SPEED)) speed[0] += modifier.amount(); });
        helper.assertTrue(armor[0] > 0 && armor[1] > armor[0], "higher grades give more armor in the off hand");
        helper.assertTrue(speed[0] < 0, "kite shields slow their bearer");
        ServerPlayer player = fighter(helper);
        player.setItemInHand(InteractionHand.OFF_HAND, iron);
        helper.assertTrue(WeaponReq.SHIELD.isMet(player), "shield abilities accept graded shields");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void piercingIgnoresArmor(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        Mob first = dummy(helper, EntityType.PIG, 3, 1);
        Mob second = dummy(helper, EntityType.PIG, 3, 4);
        first.getAttribute(Attributes.ARMOR).setBaseValue(20);
        second.getAttribute(Attributes.ARMOR).setBaseValue(20);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        float sword = hit(player, first, 8);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.IRON_RAPIER.get()));
        float rapier = hit(player, second, 8);
        // Броня 20 против удара 8 снимает 64%: меч наносит ~2.9, рапира (35% брони пробито) ~4.7.
        helper.assertTrue(Math.abs(sword - 2.88f) < 0.05f, "a sword against 20 armor must deal 2.88, got " + sword);
        helper.assertTrue(Math.abs(rapier - 4.67f) < 0.05f, "a rapier must ignore 35% of armor and deal 4.67, got " + rapier);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void crushingBreaksBonesAndStuns(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        Mob bones = dummy(helper, EntityType.SKELETON, 3, 1);
        Mob moreBones = dummy(helper, EntityType.SKELETON, 3, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        float sword = hit(player, bones, 6);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.IRON_FLANGED_MACE.get()));
        float mace = hit(player, moreBones, 6);
        helper.assertTrue(Math.abs(sword - 6f) < 0.01f && Math.abs(mace - 7.5f) < 0.01f,
                "crushing must deal +25% to skeletons: sword " + sword + ", mace " + mace);

        Zombie zombie = dummy(helper, EntityType.ZOMBIE, 1, 1);
        Zombie awake = dummy(helper, EntityType.ZOMBIE, 1, 2);
        Mob pig = dummy(helper, EntityType.PIG, 5, 1);
        Mob otherPig = dummy(helper, EntityType.PIG, 5, 4);
        helper.assertTrue(WeaponCombat.applyStun(player, zombie) && zombie.hasEffect(ModEffects.STUN), "the zombie was not stunned");
        helper.assertTrue(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED) == 0, "a stunned zombie must not move");
        pig.hurt(pig.damageSources().mobAttack(zombie), 4);
        otherPig.hurt(otherPig.damageSources().mobAttack(awake), 4);
        helper.assertTrue(pig.getHealth() == pig.getMaxHealth(), "a stunned zombie must not deal damage");
        helper.assertTrue(otherPig.getHealth() < otherPig.getMaxHealth(), "an awake zombie must still deal damage");

        LivingEntity king = helper.spawn(ModEntities.CRYPT_KING.get(), new BlockPos(1, 2, 4));
        helper.assertTrue(!WeaponCombat.applyStun(player, king) && !king.hasEffect(ModEffects.STUN), "dungeon bosses must not be stunned");
        king.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void slashingCausesBleeding(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        Mob pig = dummy(helper, EntityType.PIG, 3, 1);
        Mob skeleton = dummy(helper, EntityType.SKELETON, 3, 4);
        int[] levels = new int[4];
        for (int i = 0; i < 4; i++) {
            WeaponCombat.applyBleeding(player, pig);
            MobEffectInstance bleeding = pig.getEffect(ModEffects.BLEEDING);
            levels[i] = bleeding == null ? -1 : bleeding.getAmplifier();
        }
        helper.assertTrue(levels[0] == 0 && levels[1] == 1 && levels[2] == 2 && levels[3] == 2, "bleeding must stack up to level III");
        helper.assertTrue(!WeaponCombat.applyBleeding(player, skeleton) && !skeleton.hasEffect(ModEffects.BLEEDING), "skeletons have no blood");
        helper.assertTrue(ModDamageTypes.holder(helper.getLevel(), ModDamageTypes.BLEEDING).is(DamageTypeTags.BYPASSES_ARMOR), "bleeding must ignore armor");
        float start = pig.getHealth();
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(pig.getHealth() < start || !pig.isAlive(), "bleeding did not hurt the pig");
            helper.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void dualWieldingAddsTheOffhand(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        Mob single = dummy(helper, EntityType.PIG, 3, 1);
        Mob dual = dummy(helper, EntityType.PIG, 3, 3);
        Mob heavy = dummy(helper, EntityType.PIG, 3, 5);

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        helper.assertTrue(!WeaponCombat.isDualWielding(player), "a shield is not a second weapon");
        float alone = hit(player, single, 6);

        ItemStack dagger = new ItemStack(ModItems.IRON_DAGGER.get());
        player.setItemInHand(InteractionHand.OFF_HAND, dagger);
        helper.assertTrue(WeaponCombat.isDualWielding(player), "sword + dagger must be dual wielding");
        float paired = hit(player, dual, 6);
        // Железный кинжал бьёт на 4 (1 + 1 + 2): вторая рука добавляет половину — 2.
        helper.assertTrue(Math.abs(alone - 6f) < 0.01f && Math.abs(paired - 8f) < 0.01f, "dual: alone " + alone + ", paired " + paired);
        helper.assertTrue(player.getOffhandItem().getDamageValue() == 0, "gear must not wear in combat");

        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.IRON_WARHAMMER.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
        helper.assertTrue(!WeaponCombat.isDualWielding(player), "a warhammer cannot be paired");
        float busy = hit(player, heavy, 8);
        helper.assertTrue(Math.abs(busy - 4f) < 0.01f, "a two-handed weapon with a busy off hand must deal half, got " + busy);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void daggersArePaired(GameTestHelper helper) {
        ServerPlayer player = fighter(helper);
        Mob pig = dummy(helper, EntityType.PIG, 3, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.IRON_DAGGER.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.IRON_BUCKLER.get()));
        helper.assertTrue(WeaponType.locksOffhand(player.getMainHandItem()) && TwoHandedGrip.enforce(player) && player.getOffhandItem().isEmpty(),
                "a dagger in the main hand must free the off hand");
        helper.assertTrue(player.getInventory().contains(new ItemStack(ModItems.IRON_BUCKLER.get())), "the buckler must go to the inventory");
        helper.assertTrue(WeaponCombat.isDualWielding(player) && WeaponCombat.secondWeapon(player) == player.getMainHandItem(),
                "a dagger is held as a pair");
        // Железный кинжал бьёт на 4, второй такой же добавляет половину — 2.
        float paired = hit(player, pig, 4);
        helper.assertTrue(Math.abs(paired - 6f) < 0.01f, "paired daggers: " + paired);
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 0, "daggers must not wear in combat");

        // Кинжал во второй руке к мечу — по-прежнему обычная пара разного оружия, вторая рука не закрыта.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STEEL_SWORD.get()));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.IRON_DAGGER.get()));
        helper.assertTrue(!TwoHandedGrip.enforce(player) && WeaponCombat.isDualWielding(player), "sword + off-hand dagger stays allowed");
        helper.succeed();
    }
}
