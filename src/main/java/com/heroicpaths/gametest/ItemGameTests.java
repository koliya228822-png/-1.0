package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.item.ItemRarity.Tier;
import com.heroicpaths.item.ItemTrait;
import com.heroicpaths.registry.ModComponents;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.stat.Stat;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

/** Игровые тесты редкости предметов и классовой брони. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class ItemGameTests {
    private static final String ARENA = "gametest_arena";

    @GameTest(template = ARENA)
    public static void rarityRollsFollowChances(GameTestHelper helper) {
        RandomSource random = RandomSource.create(42);
        int[] counts = new int[Tier.values().length];
        for (int i = 0; i < 40000; i++) counts[ItemRarity.roll(random).ordinal()]++;
        helper.assertTrue(counts[Tier.JUNK.ordinal()] > 3500 && counts[Tier.JUNK.ordinal()] < 4500, "junk should be ~10%: " + counts[0]);
        helper.assertTrue(counts[Tier.COMMON.ordinal()] > 17000 && counts[Tier.COMMON.ordinal()] < 19000, "common should be ~45%: " + counts[1]);
        helper.assertTrue(counts[Tier.RARE.ordinal()] > 4300 && counts[Tier.RARE.ordinal()] < 5300, "rare should be ~12%: " + counts[3]);
        int legendary = counts[Tier.LEGENDARY.ordinal()];
        helper.assertTrue(legendary > 120 && legendary < 300, "legendary should be ~0.5%: " + legendary);
        helper.assertTrue(counts[Tier.RELIC.ordinal()] == 0, "relics must never be crafted");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void rarityScalesWeapons(GameTestHelper helper) {
        RandomSource random = RandomSource.create(7);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        int baseDurability = sword.getMaxDamage();
        ItemRarity.apply(sword, Tier.LEGENDARY, random, null);
        // Повторное применение не должно копить бонусы.
        ItemRarity.apply(sword, Tier.LEGENDARY, random, null);
        helper.assertTrue(ItemRarity.get(sword) == Tier.LEGENDARY, "rarity was not stored");
        helper.assertTrue(Math.abs(sword.getMaxDamage() - baseDurability * 1.55) <= 1, "legendary durability must be +55%: " + sword.getMaxDamage());
        double bonus = rarityDamage(sword);
        helper.assertTrue(Math.abs(bonus - 5 * 0.55) < 0.01, "legendary iron sword must get +55% of its 5 damage, got +" + bonus);

        ItemStack junk = new ItemStack(Items.IRON_SWORD);
        ItemRarity.apply(junk, Tier.JUNK, random, null);
        helper.assertTrue(rarityDamage(junk) < -0.9, "junk must lower damage, got " + rarityDamage(junk));
        helper.assertTrue(junk.getMaxDamage() < baseDurability * 0.7f, "junk must be fragile: " + junk.getMaxDamage());
        helper.assertTrue(ItemRarity.bonusPercent(junk) == -20f, "junk bonus must be -20%");

        ItemStack bow = new ItemStack(Items.BOW);
        ItemRarity.apply(bow, Tier.RARE, random, null);
        helper.assertTrue(ItemRarity.bonusPercent(bow) == 20f, "rare bow must give +20% arrow damage");
        helper.assertTrue(ItemRarity.traits(bow).size() == 1, "rare items must get one unique bonus");
        helper.assertTrue(sword.has(DataComponents.ITEM_NAME), "name must be colored by rarity");
        helper.succeed();
    }

    /** Насколько урон предмета отличается от исходного (бонус редкости влит в базовый модификатор). */
    private static double rarityDamage(ItemStack stack) {
        return sum(stack.getAttributeModifiers(), Attributes.ATTACK_DAMAGE) - sum(new ItemStack(stack.getItem()).getAttributeModifiers(), Attributes.ATTACK_DAMAGE);
    }

    private static double sum(ItemAttributeModifiers modifiers, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
        return modifiers.modifiers().stream().filter(e -> e.attribute().equals(attribute)).mapToDouble(e -> e.modifier().amount()).sum();
    }

    @GameTest(template = ARENA)
    public static void rarityScalesArmor(GameTestHelper helper) {
        RandomSource random = RandomSource.create(9);
        for (ItemStack armor : List.of(new ItemStack(Items.IRON_CHESTPLATE), new ItemStack(ModItems.WARRIOR_DAMASCUS_CHESTPLATE.get()))) {
            double base = sum(armor.getAttributeModifiers(), Attributes.ARMOR);
            ItemRarity.apply(armor, Tier.EPIC, random, null);
            ItemRarity.apply(armor, Tier.EPIC, random, null);
            double armorValue = sum(armor.getAttributeModifiers(), Attributes.ARMOR);
            helper.assertTrue(Math.abs(armorValue - base * 1.42) < 0.01, armor.getItem() + " epic armor must be +42%: " + base + " -> " + armorValue);
            long lines = armor.getAttributeModifiers().modifiers().stream().filter(e -> e.attribute().equals(Attributes.ARMOR)).count();
            helper.assertTrue(lines == 1, "rarity bonus must merge into the base armor modifier");
        }
        ItemStack junk = new ItemStack(Items.IRON_HELMET);
        double base = sum(junk.getAttributeModifiers(), Attributes.ARMOR);
        ItemRarity.apply(junk, Tier.JUNK, random, null);
        helper.assertTrue(sum(junk.getAttributeModifiers(), Attributes.ARMOR) < base, "junk armor must be weaker");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void legendaryGearIsEnchanted(GameTestHelper helper) {
        RandomSource random = RandomSource.create(11);
        var registries = helper.getLevel().registryAccess();
        List<ItemStack> gear = List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(ModItems.STEEL_SWORD.get()),
                new ItemStack(ModItems.STEEL_WARHAMMER.get()), new ItemStack(ModItems.WARRIOR_DAMASCUS_CHESTPLATE.get()),
                new ItemStack(ModItems.RANGER_LONGBOW.get()), new ItemStack(ModItems.DAMASCUS_AXE.get()));
        for (ItemStack stack : gear) {
            ItemRarity.apply(stack, Tier.LEGENDARY, random, registries);
            int enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).size();
            helper.assertTrue(enchantments == 2, stack.getItem() + " must get 2 enchantments, got " + enchantments);
            helper.assertTrue(ItemRarity.traits(stack).size() == 2, stack.getItem() + " must get 2 unique bonuses");
        }
        ItemStack relic = new ItemStack(Items.IRON_SWORD);
        ItemRarity.apply(relic, Tier.RELIC, random, registries);
        ItemEnchantments enchantments = relic.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        boolean aboveMax = enchantments.entrySet().stream().allMatch(e -> e.getIntValue() == e.getKey().value().getMaxLevel() + 1);
        helper.assertTrue(enchantments.size() == 2 && aboveMax, "relic enchantments must be one level above max");
        helper.assertTrue(ItemRarity.traits(relic).size() == 3, "relic must get 3 unique bonuses");

        // Жезл не зачаровывается — вместо чар ещё один бонус.
        ItemStack wand = new ItemStack(ModItems.FIRE_ARCHMAGE_WAND.get());
        ItemRarity.apply(wand, Tier.LEGENDARY, random, registries);
        helper.assertTrue(!wand.has(DataComponents.ENCHANTMENTS) || wand.get(DataComponents.ENCHANTMENTS).isEmpty(), "foci must not be enchanted");
        helper.assertTrue(ItemRarity.traits(wand).size() == 3, "legendary focus must get 3 unique bonuses");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void traitsMatchItemKind(GameTestHelper helper) {
        RandomSource random = RandomSource.create(3);
        List<ItemStack> items = List.of(new ItemStack(Items.BOW), new ItemStack(ModItems.STORM_RUNE_STAFF.get()),
                new ItemStack(ModItems.ARCHER_IMPREGNATED_BOOTS.get()), new ItemStack(Items.IRON_PICKAXE),
                new ItemStack(ModItems.IRON_FLANGED_MACE.get()), new ItemStack(ModItems.STEEL_RAPIER.get()), new ItemStack(Items.SHIELD));
        for (ItemStack stack : items) {
            Set<ItemTrait.Kind> kinds = ItemTrait.kindsOf(stack);
            helper.assertTrue(!kinds.isEmpty(), stack.getItem() + " has no trait kinds");
            for (int i = 0; i < 30; i++) {
                ItemRarity.apply(stack, Tier.EPIC, random, null);
                for (ItemTrait.Roll roll : ItemRarity.traits(stack)) {
                    ItemTrait trait = roll.type();
                    helper.assertTrue(trait != null && trait.fits(kinds), roll.trait() + " does not fit " + stack.getItem());
                    float power = Tier.EPIC.traitPower();
                    helper.assertTrue(roll.value() >= trait.min() * power - 0.06f && roll.value() <= trait.max() * power + 0.06f,
                            roll.trait() + " value out of range: " + roll.value());
                }
            }
        }
        // Колющее оружие может получить пробивание брони, дробящее — оглушение, но не наоборот.
        helper.assertTrue(!ItemTrait.ARMOR_PIERCE.fits(ItemTrait.kindsOf(new ItemStack(ModItems.IRON_FLANGED_MACE.get()))), "mace must not pierce");
        helper.assertTrue(ItemTrait.STUN_CHANCE.fits(ItemTrait.kindsOf(new ItemStack(ModItems.IRON_FLANGED_MACE.get()))), "mace must stun");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void legacyQualityMigrates(GameTestHelper helper) {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.set(ModComponents.QUALITY, 2); // старое «отличное»
        helper.assertTrue(ItemRarity.get(sword) == Tier.RARE, "excellent must read as rare");
        helper.assertTrue(ItemRarity.migrate(sword, RandomSource.create(5), helper.getLevel().registryAccess()), "sword was not migrated");
        helper.assertTrue(!sword.has(ModComponents.QUALITY) && sword.get(ModComponents.RARITY) == Tier.RARE.ordinal(), "quality must turn into rarity");
        helper.assertTrue(ItemRarity.traits(sword).size() == 1, "migrated rare sword must get a unique bonus");
        helper.assertTrue(!ItemRarity.migrate(sword, RandomSource.create(5), null), "migration must run only once");

        ItemStack good = new ItemStack(Items.IRON_CHESTPLATE);
        good.set(ModComponents.QUALITY, 1);
        ItemRarity.migrate(good, RandomSource.create(5), null);
        helper.assertTrue(ItemRarity.get(good) == Tier.FINE, "good must become fine");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void traitStatsApplyToWearer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RpgManager.chooseClass(player, PlayerClass.WARRIOR);
        float baseSpeed = PlayerStats.get(player).get(Stat.ATTACK_SPEED);
        float baseHealth = PlayerStats.get(player).get(Stat.MAX_HEALTH);

        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        ItemRarity.apply(sword, Tier.RARE, RandomSource.create(1), null);
        sword.set(ModComponents.TRAITS, List.of(new ItemTrait.Roll("attack_speed", 6f)));
        player.setItemSlot(EquipmentSlot.MAINHAND, sword);
        ItemStack helmet = new ItemStack(Items.IRON_HELMET);
        helmet.set(ModComponents.TRAITS, List.of(new ItemTrait.Roll("max_health", 3f)));
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        RpgManager.applyPassives(player);
        helper.assertTrue(Math.abs(PlayerStats.get(player).get(Stat.ATTACK_SPEED) - baseSpeed - 6f) < 0.001f, "sword trait must add attack speed");
        helper.assertTrue(Math.abs(PlayerStats.get(player).get(Stat.MAX_HEALTH) - baseHealth - 3f) < 0.001f, "helmet trait must add health");

        // Броня в руке своих бонусов не даёт.
        player.setItemSlot(EquipmentSlot.MAINHAND, helmet.copy());
        RpgManager.applyPassives(player);
        helper.assertTrue(Math.abs(PlayerStats.get(player).get(Stat.MAX_HEALTH) - baseHealth - 3f) < 0.001f, "armor held in hand must not count twice");
        helper.assertTrue(Math.abs(PlayerStats.get(player).get(Stat.ATTACK_SPEED) - baseSpeed) < 0.001f, "sword trait must stop after switching items");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void craftingAssignsRarity(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        // Обычное взятие: результат у игрока на курсоре.
        ItemStack taken = new ItemStack(Items.DIAMOND_SWORD);
        player.containerMenu.setCarried(taken);
        ItemRarity.onItemCrafted(new PlayerEvent.ItemCraftedEvent(player, taken, new SimpleContainer(9)));
        helper.assertTrue(taken.has(ModComponents.RARITY), "clicked craft result got no rarity");

        // Shift-клик: событие получает копию, сам предмет уже в инвентаре.
        player.containerMenu.setCarried(ItemStack.EMPTY);
        ItemStack moved = new ItemStack(Items.BOW);
        ItemStack copy = moved.copy(); // Inventory.add опустошает исходный стек, поэтому копируем заранее
        player.getInventory().add(moved);
        ItemRarity.onItemCrafted(new PlayerEvent.ItemCraftedEvent(player, copy, new SimpleContainer(9)));
        boolean inventoryHasRarity = player.getInventory().items.stream()
                .anyMatch(stack -> stack.is(Items.BOW) && stack.has(ModComponents.RARITY));
        helper.assertTrue(inventoryHasRarity, "shift-clicked craft result got no rarity");

        ItemStack stackable = new ItemStack(Items.ARROW, 16);
        player.containerMenu.setCarried(stackable);
        ItemRarity.onItemCrafted(new PlayerEvent.ItemCraftedEvent(player, stackable, new SimpleContainer(9)));
        helper.assertTrue(!stackable.has(ModComponents.RARITY), "stackable items must not get rarity");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void classArmorSetBonus(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RpgManager.chooseClass(player, PlayerClass.MAGE);
        // Без брони урон заклинаний уже есть от интеллекта и подкласса: сравниваем прирост.
        float baseSpell = PlayerStats.get(player).get(Stat.SPELL_DAMAGE);
        float baseRegen = PlayerStats.get(player).get(Stat.RESOURCE_REGEN);
        player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.MAGE_HELMET.get()));
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.MAGE_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.MAGE_LEGGINGS.get()));
        RpgManager.applyPassives(player);
        float threePieces = PlayerStats.get(player).get(Stat.SPELL_DAMAGE) - baseSpell;
        helper.assertTrue(threePieces == 12f, "3 mage pieces must give 12% spell damage, got " + threePieces);
        helper.assertTrue(PlayerStats.get(player).get(Stat.RESOURCE_REGEN) == baseRegen, "set bonus must need 4 pieces");

        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.MAGE_BOOTS.get()));
        RpgManager.applyPassives(player);
        helper.assertTrue(PlayerStats.get(player).get(Stat.SPELL_DAMAGE) - baseSpell == 26f, "full mage set must give 16+10% spell damage");
        helper.assertTrue(Math.abs(PlayerStats.get(player).get(Stat.RESOURCE_REGEN) - baseRegen - 2f) < 0.001f, "full mage set must give +2 regen");

        RpgManager.resetClass(player);
        RpgManager.chooseClass(player, PlayerClass.WARRIOR);
        helper.assertTrue(PlayerStats.get(player).get(Stat.SPELL_DAMAGE) == 4f, "mage armor must not help a warrior (only intelligence 2 = 4%)");
        helper.succeed();
    }
}
