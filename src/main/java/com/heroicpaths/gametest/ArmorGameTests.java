package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.item.ArmorWeight;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.ArmorProficiency;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.Subclass;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import com.heroicpaths.rpg.stat.Stat;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Игровые тесты веса брони и штрафов за чужую броню. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class ArmorGameTests {
    private static final String ARENA = "gametest_arena";

    private static void wear(ServerPlayer player, Item head, Item chest, Item legs, Item feet) {
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(head));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(chest));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(legs));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(feet));
        RpgManager.applyPassives(player);
    }

    private static void wearIron(ServerPlayer player) {
        wear(player, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
    }

    private static void strip(ServerPlayer player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        RpgManager.applyPassives(player);
    }

    @GameTest(template = ARENA)
    public static void armorWeightsByMaterial(GameTestHelper helper) {
        helper.assertTrue(ArmorWeight.of(new ItemStack(Items.LEATHER_CHESTPLATE)) == ArmorWeight.LIGHT, "leather must be light");
        helper.assertTrue(ArmorWeight.of(new ItemStack(Items.CHAINMAIL_CHESTPLATE)) == ArmorWeight.MEDIUM, "chainmail must be medium");
        helper.assertTrue(ArmorWeight.of(new ItemStack(Items.GOLDEN_HELMET)) == ArmorWeight.MEDIUM, "gold must be medium");
        helper.assertTrue(ArmorWeight.of(new ItemStack(Items.TURTLE_HELMET)) == ArmorWeight.MEDIUM, "turtle shell must be medium");
        helper.assertTrue(ArmorWeight.of(new ItemStack(Items.IRON_BOOTS)) == ArmorWeight.HEAVY
                && ArmorWeight.of(new ItemStack(Items.DIAMOND_HELMET)) == ArmorWeight.HEAVY
                && ArmorWeight.of(new ItemStack(Items.NETHERITE_LEGGINGS)) == ArmorWeight.HEAVY, "iron, diamond and netherite must be heavy");
        helper.assertTrue(ArmorWeight.of(new ItemStack(ModItems.WARRIOR_DAMASCUS_CHESTPLATE.get())) == ArmorWeight.HEAVY, "warrior plate must be heavy");
        helper.assertTrue(ArmorWeight.of(new ItemStack(ModItems.ARCHER_IMPREGNATED_BOOTS.get())) == ArmorWeight.LIGHT, "archer leather must be light");
        helper.assertTrue(ArmorWeight.of(new ItemStack(ModItems.MAGE_WOOL_HELMET.get())) == ArmorWeight.CLOTH, "mage robes must be cloth");
        helper.assertTrue(ArmorWeight.of(new ItemStack(Items.IRON_SWORD)) == null && ArmorWeight.of(new ItemStack(Items.ELYTRA)) == null,
                "non-armor has no weight");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void magesSufferInPlate(GameTestHelper helper) {
        ServerPlayer mage = RpgGameTests.playerInArena(helper);
        RpgManager.chooseClass(mage, PlayerClass.MAGE);
        strip(mage);
        float base = PlayerStats.get(mage).get(Stat.SPELL_DAMAGE);
        var chainLightning = AbilityRegistry.get("chain_lightning");
        var fireball = AbilityRegistry.get("fireball");

        wear(mage, ModItems.MAGE_HELMET.get(), ModItems.MAGE_CHESTPLATE.get(), ModItems.MAGE_LEGGINGS.get(), ModItems.MAGE_BOOTS.get());
        helper.assertTrue(ArmorProficiency.overload(mage, PlayerClass.MAGE) == 0, "robes must not overload a mage");

        wear(mage, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
        helper.assertTrue(Math.abs(PlayerStats.get(mage).get(Stat.SPELL_DAMAGE) - base + 10f) < 0.01f, "full leather must cost a mage 10% spell damage");
        helper.assertTrue(ArmorProficiency.blockReason(mage, chainLightning) == null, "leather must not block strong spells");

        wearIron(mage);
        helper.assertTrue(Math.abs(PlayerStats.get(mage).get(Stat.SPELL_DAMAGE) - base + 30f) < 0.01f, "full plate must cost a mage 30% spell damage");
        helper.assertTrue(ArmorProficiency.blockReason(mage, chainLightning) != null, "plate must block strong spells");
        helper.assertTrue(ArmorProficiency.blockReason(mage, fireball) == null, "plate must not block basic spells");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void roguesLoseStealthInHeavyArmor(GameTestHelper helper) {
        ServerPlayer rogue = RpgGameTests.playerInArena(helper);
        RpgManager.chooseClass(rogue, PlayerClass.ROGUE);
        strip(rogue);
        float dodge = PlayerStats.get(rogue).get(Stat.DODGE_CHANCE);
        float speed = PlayerStats.get(rogue).get(Stat.MOVE_SPEED);
        var vanish = AbilityRegistry.get("vanish");

        wear(rogue, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
        helper.assertTrue(ArmorProficiency.backstabAllowed(rogue) && ArmorProficiency.blockReason(rogue, vanish) == null, "leather must keep stealth");
        helper.assertTrue(PlayerStats.get(rogue).get(Stat.DODGE_CHANCE) == dodge, "leather must not cost dodge");

        rogue.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        RpgManager.applyPassives(rogue);
        helper.assertTrue(!ArmorProficiency.backstabAllowed(rogue) && ArmorProficiency.blockReason(rogue, vanish) != null,
                "one heavy piece must block vanish and backstab");
        helper.assertTrue(Math.abs(PlayerStats.get(rogue).get(Stat.DODGE_CHANCE) - dodge + 4f) < 0.01f
                && Math.abs(PlayerStats.get(rogue).get(Stat.MOVE_SPEED) - speed + 3f) < 0.01f, "an iron chestplate must cost 4% dodge and 3% speed");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void archersHuntersAndWarriorsInPlate(GameTestHelper helper) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        RpgManager.chooseClass(player, PlayerClass.ARCHER);
        strip(player);
        float bow = PlayerStats.get(player).get(Stat.BOW_DAMAGE);
        wear(player, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
        helper.assertTrue(PlayerStats.get(player).get(Stat.BOW_DAMAGE) == bow, "chainmail must fit an archer");
        wearIron(player);
        helper.assertTrue(Math.abs(PlayerStats.get(player).get(Stat.BOW_DAMAGE) - bow + 20f) < 0.01f, "plate must cost an archer 20% arrow damage");

        RpgManager.resetClass(player);
        RpgManager.chooseClass(player, PlayerClass.HUNTER);
        strip(player);
        float minions = PlayerStats.get(player).get(Stat.MINION_DAMAGE);
        wearIron(player);
        helper.assertTrue(Math.abs(PlayerStats.get(player).get(Stat.MINION_DAMAGE) - minions + 24f) < 0.01f, "plate must weaken a hunter's beast by 24%");

        RpgManager.resetClass(player);
        RpgManager.chooseClass(player, PlayerClass.WARRIOR, Subclass.GUARDIAN);
        strip(player);
        float reduction = PlayerStats.get(player).get(Stat.DAMAGE_REDUCTION);
        float speed = PlayerStats.get(player).get(Stat.MOVE_SPEED);
        wearIron(player);
        helper.assertTrue(ArmorProficiency.overload(player, PlayerClass.WARRIOR) == 0 && PlayerStats.get(player).get(Stat.MOVE_SPEED) == speed,
                "warriors wear plate freely");
        helper.assertTrue(Math.abs(PlayerStats.get(player).get(Stat.DAMAGE_REDUCTION) - reduction - 8f) < 0.01f, "a guardian in full plate must get +8% reduction");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void armorTooltipsShowFit(GameTestHelper helper) {
        ServerPlayer mage = RpgGameTests.playerInArena(helper);
        RpgManager.chooseClass(mage, PlayerClass.MAGE);
        List<Component> plate = new ArrayList<>(List.of(Component.literal("name")));
        int added = ArmorProficiency.addTooltip(new ItemStack(Items.IRON_CHESTPLATE), plate, 1, mage);
        helper.assertTrue(added >= 3 && plate.get(1).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents weight
                        && weight.getKey().equals("tooltip.heroicpaths.armor_weight")
                        && plate.get(2).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents foreign
                        && foreign.getKey().equals("tooltip.heroicpaths.armor_foreign"),
                "plate tooltip must show weight, foreign status and penalty: " + plate);
        List<Component> robe = new ArrayList<>(List.of(Component.literal("name")));
        helper.assertTrue(ArmorProficiency.addTooltip(new ItemStack(ModItems.MAGE_CHESTPLATE.get()), robe, 1, mage) == 2,
                "robe tooltip must show weight and fit: " + robe);
        helper.assertTrue(ArmorProficiency.addTooltip(new ItemStack(Items.IRON_SWORD), new ArrayList<>(), 0, mage) == 0, "swords have no weight line");
        helper.succeed();
    }
}
