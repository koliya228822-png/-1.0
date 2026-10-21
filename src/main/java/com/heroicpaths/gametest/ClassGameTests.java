package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.combat.WeaponCombat;
import com.heroicpaths.minion.CompanionManager;
import com.heroicpaths.registry.ModEffects;
import com.heroicpaths.rpg.ClassTraits;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.Subclass;
import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.ability.AbilityLogic;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import com.heroicpaths.rpg.stat.Keystone;
import com.heroicpaths.rpg.tree.NodeType;
import com.heroicpaths.rpg.tree.PassiveNode;
import com.heroicpaths.rpg.tree.PassiveTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumMap;
import java.util.Map;

/** Игровые тесты этапа D: дерево на пять классов, особенности классов и новые умения. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class ClassGameTests {
    private static final String ARENA = "gametest_arena";

    private static ServerPlayer hero(GameTestHelper helper, PlayerClass playerClass, Subclass subclass) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        RpgManager.chooseClass(player, playerClass, subclass);
        return player;
    }

    private static Mob dummy(GameTestHelper helper, EntityType<? extends Mob> type, int x, int z, float yaw) {
        Mob mob = helper.spawn(type, new BlockPos(x, 2, z));
        mob.setNoAi(true);
        mob.setYRot(yaw);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
        return mob;
    }

    /** Урон по игроку, который не снижается бронёй и проходит сквозь неуязвимость после появления. */
    private static float takeHit(ServerPlayer player, LivingEntity attacker, float amount) {
        DamageSource source = new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.GENERIC_KILL), attacker);
        float before = player.getHealth();
        player.invulnerableTime = 0;
        player.hurt(source, amount);
        return before - player.getHealth();
    }

    @GameTest(template = ARENA)
    public static void treeHasFiveEqualSectors(GameTestHelper helper) {
        Map<PlayerClass, Integer> regionSizes = new EnumMap<>(PlayerClass.class);
        Map<PlayerClass, Integer> classKeystones = new EnumMap<>(PlayerClass.class);
        for (PassiveNode node : PassiveTree.nodes()) {
            if (node.region() == PlayerClass.NONE) continue;
            regionSizes.merge(node.region(), 1, Integer::sum);
            if (node.type() == NodeType.KEYSTONE) classKeystones.merge(node.region(), 1, Integer::sum);
        }
        int expected = regionSizes.get(PlayerClass.WARRIOR);
        for (PlayerClass playerClass : PlayerClass.playable()) {
            helper.assertTrue(regionSizes.getOrDefault(playerClass, 0) == expected,
                    playerClass + " sector has " + regionSizes.get(playerClass) + " nodes, warrior has " + expected);
            helper.assertTrue(classKeystones.getOrDefault(playerClass, 0) == 2, playerClass + " must have two keystones of its own");
            int start = PassiveTree.startNode(playerClass);
            long branches = PassiveTree.neighbours(start).intStream().filter(id -> PassiveTree.node(id).region() == playerClass).count();
            helper.assertTrue(branches == 9, playerClass + " start must open 6 branches and 3 base nodes, opens " + branches);
        }
        helper.assertTrue(Keystone.values().length == 10, "expected 10 keystones");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void oldTreeIsRefundedOnLoad(GameTestHelper helper) {
        CompoundTag old = new CompoundTag();
        old.putInt("v", 3);
        old.putString("class", "warrior");
        old.putString("subclass", "guardian");
        old.putInt("level", 10);
        old.put("nodes", new IntArrayTag(new int[]{20, 21, 22}));
        RpgData data = new RpgData();
        data.deserializeNBT(helper.getLevel().registryAccess(), old);
        helper.assertTrue(data.getAllocatedNodes().isEmpty() && data.treePoints() == 10, "old tree nodes must be refunded");
        helper.assertTrue(data.getSubclass() == Subclass.GUARDIAN && data.getLevel() == 10, "class, subclass and level must survive");
        helper.assertTrue(data.consumeTreeReset() && !data.consumeTreeReset(), "the player must be told once about the rebuilt tree");

        RpgData fresh = new RpgData();
        fresh.deserializeNBT(helper.getLevel().registryAccess(), data.serializeNBT(helper.getLevel().registryAccess()));
        helper.assertTrue(!fresh.consumeTreeReset(), "a current save must not report a tree reset");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void rogueStabsInTheBack(GameTestHelper helper) {
        ServerPlayer rogue = hero(helper, PlayerClass.ROGUE, Subclass.DUELIST);
        rogue.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        // Игрок смотрит в сторону +X. Одна свинья тоже смотрит в +X (спиной к нему), другая — на него.
        Mob away = dummy(helper, EntityType.PIG, 3, 4, -90f);
        Mob facing = dummy(helper, EntityType.PIG, 3, 6, 90f);
        helper.assertTrue(ClassTraits.isBehind(rogue, away) && !ClassTraits.isBehind(rogue, facing), "behind check is wrong");
        rogue.getRandom().setSeed(7);
        float front = hitFrom(rogue, facing, 4);
        rogue.getRandom().setSeed(7);
        float back = hitFrom(rogue, away, 4);
        helper.assertTrue(Math.abs(back / front - ClassTraits.ROGUE_BACKSTAB_FACTOR) < 0.01f,
                "a hit in the back must deal 35% more: front " + front + ", back " + back);
        helper.succeed();
    }

    private static float hitFrom(ServerPlayer player, LivingEntity target, float amount) {
        float before = target.getHealth();
        target.hurt(player.damageSources().playerAttack(player), amount);
        return before - target.getHealth();
    }

    @GameTest(template = ARENA)
    public static void warriorRagesAndHoldsTheLine(GameTestHelper helper) {
        ServerPlayer warrior = hero(helper, PlayerClass.WARRIOR, Subclass.GREATSWORD);
        Mob zombie = dummy(helper, EntityType.ZOMBIE, 5, 1, 0f);
        float full = takeHit(warrior, zombie, 4);
        warrior.setHealth(warrior.getMaxHealth() * 0.3f);
        float low = takeHit(warrior, zombie, 4);
        helper.assertTrue(Math.abs(low - full * ClassTraits.WARRIOR_LOW_HEALTH_FACTOR) < 0.01f,
                "a wounded warrior must take 20% less: " + full + " -> " + low);

        RpgData data = RpgManager.get(warrior);
        data.setResource(10);
        Mob pig = dummy(helper, EntityType.PIG, 3, 4, 90f);
        pig.hurt(warrior.damageSources().playerAttack(warrior), 1);
        helper.assertTrue(data.getResource() == 10 + ClassTraits.WARRIOR_STAMINA_PER_HIT, "a melee hit must restore stamina, got " + data.getResource());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void mageResonatesAndArcherShootsFar(GameTestHelper helper) {
        ServerPlayer mage = hero(helper, PlayerClass.MAGE, Subclass.ELEMENTALIST);
        AbilityDef fireball = AbilityRegistry.get("fireball");
        AbilityDef iceLance = AbilityRegistry.get("ice_lance");
        float plain = AbilityLogic.damageFactor(mage, iceLance);
        ClassTraits.beforeCast(mage, fireball);
        helper.assertTrue(!ClassTraits.resonating(mage, fireball.element()), "the first spell has nothing to resonate with");
        ClassTraits.beforeCast(mage, iceLance);
        float resonating = AbilityLogic.damageFactor(mage, iceLance);
        helper.assertTrue(Math.abs(resonating - plain * ClassTraits.MAGE_RESONANCE_FACTOR) < 1e-4, "switching element must add 20%: " + plain + " -> " + resonating);
        ClassTraits.beforeCast(mage, iceLance);
        helper.assertTrue(Math.abs(AbilityLogic.damageFactor(mage, iceLance) - plain) < 1e-4, "repeating the element must not resonate");

        helper.assertTrue(ClassTraits.archerRangeBonus(6) == 0 && Math.abs(ClassTraits.archerRangeBonus(18) - 0.2f) < 1e-4
                && ClassTraits.archerRangeBonus(60) == ClassTraits.ARCHER_MAX_BONUS, "long shot bonus is wrong");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void hunterIsProtectedByTheBeast(GameTestHelper helper) {
        ServerPlayer hunter = hero(helper, PlayerClass.HUNTER, Subclass.BEASTMASTER);
        Mob zombie = dummy(helper, EntityType.ZOMBIE, 5, 1, 0f);
        float alone = takeHit(hunter, zombie, 4);
        hunter.setHealth(hunter.getMaxHealth());
        helper.assertTrue(CompanionManager.call(hunter) && CompanionManager.find(hunter) != null, "the companion did not come");
        float together = takeHit(hunter, zombie, 4);
        helper.assertTrue(Math.abs(together - alone * ClassTraits.HUNTER_BOND_FACTOR) < 0.01f, "the beast bond must cut damage by 15%: " + alone + " -> " + together);

        Mob beast = CompanionManager.find(hunter);
        helper.assertTrue(AbilityLogic.cast(hunter, AbilityRegistry.get("feral_rage")) && beast.hasEffect(MobEffects.DAMAGE_BOOST), "feral rage failed");
        CompanionManager.dismiss(hunter);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void newAbilitiesWork(GameTestHelper helper) {
        for (PlayerClass playerClass : PlayerClass.playable()) {
            int books = playerClass.bookAbilities().size();
            helper.assertTrue(books >= (playerClass == PlayerClass.MAGE ? 1 : 3), playerClass + " has only " + books + " class books");
            for (String id : playerClass.bookAbilities()) helper.assertTrue(AbilityRegistry.get(id) != null, "unknown ability " + id);
        }
        for (Subclass subclass : Subclass.values()) {
            if (subclass.playerClass() == PlayerClass.MAGE && subclass != Subclass.BLOOD_MAGE) {
                helper.assertTrue(subclass.learnableAbilities().contains("mana_shield"), subclass + " must learn mana shield");
            }
        }

        // Разбойник первым: пока на арене нет других игроков, «Потрошение» точно выберет зомби перед ним.
        ServerPlayer rogue = hero(helper, PlayerClass.ROGUE, Subclass.DUELIST);
        rogue.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        Mob victim = dummy(helper, EntityType.ZOMBIE, 3, 4, 90f);
        WeaponCombat.applyBleeding(rogue, victim);
        WeaponCombat.applyBleeding(rogue, victim);
        float before = victim.getHealth();
        helper.assertTrue(AbilityLogic.cast(rogue, AbilityRegistry.get("eviscerate")), "eviscerate found no target");
        helper.assertTrue(!victim.hasEffect(ModEffects.BLEEDING) && victim.getHealth() < before, "eviscerate must consume bleeding and wound");
        helper.assertTrue(AbilityLogic.cast(rogue, AbilityRegistry.get("vanish")) && rogue.hasEffect(MobEffects.INVISIBILITY), "vanish failed");
        float critBefore = PlayerStats.critChance(rogue);

        ServerPlayer warrior = hero(helper, PlayerClass.WARRIOR, Subclass.GREATSWORD);
        Mob zombie = dummy(helper, EntityType.ZOMBIE, 5, 1, 0f);
        helper.assertTrue(AbilityLogic.cast(warrior, AbilityRegistry.get("last_stand")), "last stand failed");
        takeHit(warrior, zombie, 1000);
        helper.assertTrue(warrior.isAlive() && warrior.getHealth() >= 1f, "last stand must keep the warrior alive");

        ServerPlayer mage = hero(helper, PlayerClass.MAGE, Subclass.SUPPORT);
        RpgData mageData = RpgManager.get(mage);
        mageData.setResource(100);
        helper.assertTrue(AbilityLogic.cast(mage, AbilityRegistry.get("mana_shield")), "mana shield failed");
        float lost = takeHit(mage, zombie, 10);
        // Характеристики мага немного снижают урон, поэтому сверяем доли: 60% урона ушло в ману по 2 маны за единицу.
        float absorbed = (100f - mageData.getResource()) / ClassTraits.RESOURCE_PER_DAMAGE;
        helper.assertTrue(absorbed > 5f && Math.abs(absorbed / (absorbed + lost) - ClassTraits.MANA_SHIELD_SHARE) < 0.01f,
                "mana shield must take 60% from mana: lost " + lost + ", mana " + mageData.getResource());

        ServerPlayer archer = hero(helper, PlayerClass.ARCHER, Subclass.RANGER);
        float archerCrit = PlayerStats.critChance(archer);
        helper.assertTrue(AbilityLogic.cast(archer, AbilityRegistry.get("eagle_eye"))
                && Math.abs(PlayerStats.critChance(archer) - archerCrit - ClassTraits.EAGLE_EYE_CRIT) < 0.01f, "eagle eye must add 25% crit");
        helper.assertTrue(critBefore > 0, "crit chance must be positive");
        helper.succeed();
    }
}
