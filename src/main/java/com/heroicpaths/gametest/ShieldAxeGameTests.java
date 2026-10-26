package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.combat.ShieldBlock;
import com.heroicpaths.combat.Stamina;
import com.heroicpaths.combat.WeaponType;
import com.heroicpaths.item.Grades;
import com.heroicpaths.item.ToolAxes;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.registry.ModTiers;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.Subclass;
import com.heroicpaths.rpg.ability.WeaponReq;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Щит гасит лишь часть урона и тратит выносливость; ванильный топор — инструмент с уроном кирки. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class ShieldAxeGameTests {
    private static final String ARENA = "gametest_arena";
    private static final float DAMAGE = 10f;

    private static ServerPlayer hero(GameTestHelper helper, PlayerClass playerClass, Subclass subclass) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        RpgManager.chooseClass(player, playerClass, subclass);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.IRON_HEATER_SHIELD.get()));
        return player;
    }

    /** Сколько урона щит погасит из удара в 10 (событие проходит через шину, как в настоящем бою). */
    private static float blocked(ServerPlayer player) {
        LivingShieldBlockEvent event = new LivingShieldBlockEvent(player,
                new DamageContainer(player.damageSources().generic(), DAMAGE), true);
        NeoForge.EVENT_BUS.post(event);
        return event.getBlocked() ? event.getBlockedDamage() : 0f;
    }

    @GameTest(template = ARENA)
    public static void shieldPassesFortyPercent(GameTestHelper helper) {
        ServerPlayer warrior = hero(helper, PlayerClass.WARRIOR, Subclass.GREATSWORD);
        RpgData data = RpgManager.get(warrior);
        data.setResource(Stamina.max(warrior));
        float before = data.getResource();
        float blocked = blocked(warrior);
        helper.assertTrue(Math.abs(blocked - DAMAGE * ShieldBlock.BLOCK_FACTOR) < 0.01f,
                "a shield must stop 60% and let 40% through, stopped " + blocked);
        float cost = Math.min(ShieldBlock.MAX_STAMINA_COST, blocked * ShieldBlock.STAMINA_PER_DAMAGE);
        helper.assertTrue(Math.abs(before - data.getResource() - cost) < 0.01f,
                "a blocked hit must cost " + cost + " endurance, spent " + (before - data.getResource()));

        ServerPlayer nobody = RpgGameTests.playerInArena(helper);
        nobody.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(Math.abs(blocked(nobody) - DAMAGE * ShieldBlock.BLOCK_FACTOR) < 0.01f, "a classless player blocks 60% too");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void tiredShieldPassesSeventyPercent(GameTestHelper helper) {
        ServerPlayer warrior = hero(helper, PlayerClass.WARRIOR, Subclass.GREATSWORD);
        RpgData data = RpgManager.get(warrior);
        data.setResource(1f);
        float blocked = blocked(warrior);
        helper.assertTrue(Math.abs(blocked - DAMAGE * ShieldBlock.TIRED_BLOCK_FACTOR) < 0.01f,
                "a tired shield must stop only 30%, stopped " + blocked);
        helper.assertTrue(data.getResource() == 0f, "a tired block empties endurance");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void mageShieldStillBlocksFully(GameTestHelper helper) {
        ServerPlayer mage = hero(helper, PlayerClass.MAGE, Subclass.ELEMENTALIST);
        float before = RpgManager.get(mage).getResource();
        float blocked = blocked(mage);
        helper.assertTrue(Math.abs(blocked - DAMAGE) < 0.01f, "a mage's shield must block everything, stopped " + blocked);
        helper.assertTrue(RpgManager.get(mage).getResource() == before, "a mage's block must not cost mana");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void vanillaAxeHitsLikePickaxe(GameTestHelper helper) {
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        double axeDamage = ToolAxes.attackDamage(axe), pickaxeDamage = ToolAxes.attackDamage(new ItemStack(Items.IRON_PICKAXE));
        helper.assertTrue(pickaxeDamage > 0 && Math.abs(axeDamage - pickaxeDamage) < 0.001,
                "an iron axe must hit like an iron pickaxe: " + axeDamage + " vs " + pickaxeDamage);
        helper.assertTrue(Math.abs(ToolAxes.attackDamage(new ItemStack(Items.DIAMOND_AXE)) - ToolAxes.attackDamage(new ItemStack(Items.DIAMOND_PICKAXE))) < 0.001,
                "a diamond axe must hit like a diamond pickaxe");
        double[] speed = {0};
        axe.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_SPEED)) speed[0] += modifier.amount();
        });
        helper.assertTrue(speed[0] < -2.9, "the axe keeps its slow swing, speed modifier " + speed[0]);
        helper.assertTrue(WeaponType.of(axe) == null && !WeaponReq.isMeleeWeapon(axe.getItem()) && Grades.of(axe) == 0,
                "a vanilla axe is a tool, not a weapon");

        ServerPlayer warrior = hero(helper, PlayerClass.WARRIOR, Subclass.GREATSWORD);
        warrior.setItemInHand(InteractionHand.MAIN_HAND, axe);
        helper.assertTrue(!WeaponReq.AXE.isMet(warrior) && !WeaponReq.MELEE.isMet(warrior), "a vanilla axe must not satisfy weapon requirements");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void battleAxesStayWeapons(GameTestHelper helper) {
        ItemStack steel = new ItemStack(ModItems.STEEL_AXE.get());
        double expected = AxeItem.createAttributes(ModTiers.STEEL, 6f, -3.05f).modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE))
                .mapToDouble(entry -> entry.modifier().amount()).sum();
        helper.assertTrue(Math.abs(ToolAxes.attackDamage(steel) - expected) < 0.001,
                "a steel battle axe keeps its damage " + expected + ", got " + ToolAxes.attackDamage(steel));
        helper.assertTrue(WeaponType.of(steel) == WeaponType.SLASHING && WeaponReq.isMeleeWeapon(steel.getItem()) && Grades.of(steel) == 2,
                "a battle axe is still a slashing weapon of grade II");
        ServerPlayer warrior = hero(helper, PlayerClass.WARRIOR, Subclass.GREATSWORD);
        warrior.setItemInHand(InteractionHand.MAIN_HAND, steel);
        helper.assertTrue(WeaponReq.AXE.isMet(warrior), "a battle axe satisfies the axe requirement");
        helper.succeed();
    }
}
