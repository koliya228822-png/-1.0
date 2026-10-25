package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.dungeon.DungeonScrollItem;
import com.heroicpaths.dungeon.GateStoneItem;
import com.heroicpaths.dungeon.TrophyItem;
import com.heroicpaths.accessory.AccessoryItem;
import com.heroicpaths.accessory.AccessoryKind;
import com.heroicpaths.item.AbilityBookItem;
import com.heroicpaths.item.BattleAxeItem;
import com.heroicpaths.item.ClassArmorItem;
import com.heroicpaths.item.DaggerItem;
import com.heroicpaths.item.FocusItem;
import com.heroicpaths.item.GearBlankItem;
import com.heroicpaths.item.GradedBowItem;
import com.heroicpaths.combat.WeaponType;
import com.heroicpaths.item.GradedShieldItem;
import com.heroicpaths.item.GreatswordItem;
import com.heroicpaths.item.MeleeWeaponItem;
import com.heroicpaths.item.MusketItem;
import net.minecraft.world.item.Tier;
import com.heroicpaths.rpg.stat.Element;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Tiers;
import com.heroicpaths.item.ExperienceTomeItem;
import com.heroicpaths.item.OblivionScrollItem;
import com.heroicpaths.item.SpiritElixirItem;
import com.heroicpaths.rpg.PlayerClass;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HeroicPaths.MODID);

    public static final DeferredItem<SpiritElixirItem> SPIRIT_ELIXIR = ITEMS.register("spirit_elixir",
            () -> new SpiritElixirItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<OblivionScrollItem> OBLIVION_SCROLL = ITEMS.register("oblivion_scroll",
            () -> new OblivionScrollItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<ExperienceTomeItem> EXPERIENCE_TOME = ITEMS.register("experience_tome",
            () -> new ExperienceTomeItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    public static final DeferredItem<BlockItem> WEAPON_RACK = ITEMS.registerSimpleBlockItem("weapon_rack", ModBlocks.WEAPON_RACK);
    public static final DeferredItem<BlockItem> ARCHERY_TARGET = ITEMS.registerSimpleBlockItem("archery_target", ModBlocks.ARCHERY_TARGET);
    public static final DeferredItem<BlockItem> ARCANE_LECTERN = ITEMS.registerSimpleBlockItem("arcane_lectern", ModBlocks.ARCANE_LECTERN);
    public static final DeferredItem<BlockItem> BANK_COUNTER = ITEMS.registerSimpleBlockItem("bank_counter", ModBlocks.BANK_COUNTER);
    public static final DeferredItem<BlockItem> ARMS_DISPLAY = ITEMS.registerSimpleBlockItem("arms_display", ModBlocks.ARMS_DISPLAY);

    public static final DeferredItem<DeferredSpawnEggItem> CRYPT_KING_SPAWN_EGG = ITEMS.register("crypt_king_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.CRYPT_KING, 0x2A2530, 0x9A40FF, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> FLAME_TYRANT_SPAWN_EGG = ITEMS.register("flame_tyrant_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FLAME_TYRANT, 0x8A2A0A, 0xFFB020, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> FROST_LORD_SPAWN_EGG = ITEMS.register("frost_lord_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FROST_LORD, 0x5A7A8A, 0xBFEFFF, new Item.Properties()));

    // Данжи через врата
    public static final DeferredItem<GateStoneItem> GATE_STONE = ITEMS.register("gate_stone",
            () -> new GateStoneItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<BlockItem> GATE_ALTAR = ITEMS.registerSimpleBlockItem("gate_altar", ModBlocks.GATE_ALTAR);
    public static final DeferredItem<BlockItem> QUEST_BOARD = ITEMS.registerSimpleBlockItem("quest_board", ModBlocks.QUEST_BOARD);
    public static final DeferredItem<BlockItem> DUNGEON_GATE = ITEMS.registerSimpleBlockItem("dungeon_gate", ModBlocks.DUNGEON_GATE,
            new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredItem<BlockItem> ORDER_BOARD = ITEMS.registerSimpleBlockItem("order_board", ModBlocks.ORDER_BOARD);
    public static final DeferredItem<BlockItem> BOUNTY_BOARD = ITEMS.registerSimpleBlockItem("bounty_board", ModBlocks.BOUNTY_BOARD);

    // Трофеи боссов: из них крафтятся свитки доступа в следующий данж
    public static final DeferredItem<TrophyItem> CRYPT_CROWN_SHARD = ITEMS.register("crypt_crown_shard",
            () -> new TrophyItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<TrophyItem> TYRANT_EMBER_CORE = ITEMS.register("tyrant_ember_core",
            () -> new TrophyItem(new Item.Properties().stacksTo(64).rarity(Rarity.RARE).fireResistant()));
    public static final DeferredItem<TrophyItem> FROST_HEART = ITEMS.register("frost_heart",
            () -> new TrophyItem(new Item.Properties().stacksTo(64).rarity(Rarity.EPIC)));
    // Одноразовые свитки доступа в данжи с 20 уровня
    public static final DeferredItem<DungeonScrollItem> FORGE_SCROLL = ITEMS.register("forge_scroll",
            () -> new DungeonScrollItem("forge", new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final DeferredItem<DungeonScrollItem> FROST_SCROLL = ITEMS.register("frost_scroll",
            () -> new DungeonScrollItem("frost", new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));

    // Этап J: Паучье логово, Затопленный храм, Некрополь — их боссы, трофеи и свитки
    public static final DeferredItem<DeferredSpawnEggItem> SPIDER_QUEEN_SPAWN_EGG = ITEMS.register("spider_queen_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SPIDER_QUEEN, 0x3E1E58, 0x78FF6E, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> DROWNED_PROPHET_SPAWN_EGG = ITEMS.register("drowned_prophet_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.DROWNED_PROPHET, 0x2A6A6A, 0x8AF0E0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> LICH_SPAWN_EGG = ITEMS.register("lich_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.LICH, 0x1A1A22, 0x60E0FF, new Item.Properties()));
    // Ледяная цитадель: Лич и его слуги
    public static final DeferredItem<DeferredSpawnEggItem> FROST_LICH_SPAWN_EGG = ITEMS.register("frost_lich_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FROST_LICH, 0x2A3A6A, 0x8AE8FF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ICE_GOLEM_SPAWN_EGG = ITEMS.register("ice_golem_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.ICE_GOLEM, 0x92CCEC, 0x3E76AC, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> GUARD_SWORDSMAN_SPAWN_EGG = ITEMS.register("guard_swordsman_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.GUARD_SWORDSMAN, 0x2F4F9A, 0xD8B23A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> GUARD_ARCHER_SPAWN_EGG = ITEMS.register("guard_archer_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.GUARD_ARCHER, 0x3F6B2E, 0x8A5A32, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> FROZEN_ARCHER_SPAWN_EGG = ITEMS.register("frozen_archer_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FROZEN_ARCHER, 0xC4CCD4, 0x6EDCFF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> SNOW_BRUTE_SPAWN_EGG = ITEMS.register("snow_brute_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.SNOW_BRUTE, 0xE8EEF4, 0x2C2C32, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> UNDEAD_ZOMBIE_SPAWN_EGG = ITEMS.register("undead_zombie_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.UNDEAD_ZOMBIE, 0x708A60, 0x60503C, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> GHOUL_SPAWN_EGG = ITEMS.register("ghoul_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.GHOUL, 0x80768C, 0xD6CCB2, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> BANSHEE_SPAWN_EGG = ITEMS.register("banshee_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BANSHEE, 0xB0E4F0, 0xFFFFFF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ABOMINATION_SPAWN_EGG = ITEMS.register("abomination_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.ABOMINATION, 0xBC7A74, 0x962C36, new Item.Properties()));
    // Глиняные копи: колосс и его големы
    public static final DeferredItem<DeferredSpawnEggItem> CLAY_COLOSSUS_SPAWN_EGG = ITEMS.register("clay_colossus_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.CLAY_COLOSSUS, 0x8A4A2A, 0xFFB040, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> CLAY_GOLEM_SPAWN_EGG = ITEMS.register("clay_golem_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.CLAY_GOLEM, 0xB06C46, 0x704028, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> CLAY_THROWER_SPAWN_EGG = ITEMS.register("clay_thrower_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.CLAY_THROWER, 0xAA9C8C, 0x78E6FF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> CLAY_BRUTE_SPAWN_EGG = ITEMS.register("clay_brute_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.CLAY_BRUTE, 0x6E4634, 0xFF5032, new Item.Properties()));
    // Паучье логово: пауки выводка
    public static final DeferredItem<DeferredSpawnEggItem> BROOD_SPIDER_SPAWN_EGG = ITEMS.register("brood_spider_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BROOD_SPIDER, 0x181B2E, 0xC8A46A, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> VENOM_SPITTER_SPAWN_EGG = ITEMS.register("venom_spitter_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.VENOM_SPITTER, 0x181B2E, 0x78FF6E, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> BROOD_TANK_SPAWN_EGG = ITEMS.register("brood_tank_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.BROOD_TANK, 0x281E3A, 0xC8A46A, new Item.Properties()));
    public static final DeferredItem<TrophyItem> BROODMOTHER_FANG = ITEMS.register("broodmother_fang",
            () -> new TrophyItem(new Item.Properties().stacksTo(64).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<TrophyItem> TIDE_PEARL = ITEMS.register("tide_pearl",
            () -> new TrophyItem(new Item.Properties().stacksTo(64).rarity(Rarity.RARE)));
    public static final DeferredItem<TrophyItem> LICH_PHYLACTERY = ITEMS.register("lich_phylactery",
            () -> new TrophyItem(new Item.Properties().stacksTo(64).rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<DungeonScrollItem> TEMPLE_SCROLL = ITEMS.register("temple_scroll",
            () -> new DungeonScrollItem("sunken_temple", new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final DeferredItem<DungeonScrollItem> NECROPOLIS_SCROLL = ITEMS.register("necropolis_scroll",
            () -> new DungeonScrollItem("necropolis", new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));

    // Книги умений и оружие подклассов
    public static final DeferredItem<AbilityBookItem> ABILITY_BOOK = ITEMS.register("ability_book",
            () -> new AbilityBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<DaggerItem> IRON_DAGGER = ITEMS.register("iron_dagger",
            () -> new DaggerItem(Tiers.IRON, new Item.Properties()));
    public static final DeferredItem<DaggerItem> DIAMOND_DAGGER = ITEMS.register("diamond_dagger",
            () -> new DaggerItem(ModTiers.DIAMOND, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<GreatswordItem> IRON_GREATSWORD = ITEMS.register("iron_greatsword",
            () -> new GreatswordItem(Tiers.IRON, new Item.Properties()));
    public static final DeferredItem<GreatswordItem> DIAMOND_GREATSWORD = ITEMS.register("diamond_greatsword",
            () -> new GreatswordItem(ModTiers.DIAMOND, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<MusketItem> MUSKET = musket("musket", 1);
    public static final DeferredItem<MusketItem> STEEL_MUSKET = musket("steel_musket", 2);
    public static final DeferredItem<MusketItem> DAMASCUS_MUSKET = musket("damascus_musket", 3);
    public static final DeferredItem<Item> MUSKET_BALL = ITEMS.registerSimpleItem("musket_ball");
    // Жезлы и посохи: от них зависит урон заклинаний мага
    public static final DeferredItem<FocusItem> APPRENTICE_WAND = ITEMS.register("apprentice_wand",
            () -> new FocusItem(4f, false, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<FocusItem> APPRENTICE_STAFF = ITEMS.register("apprentice_staff",
            () -> new FocusItem(6f, true, new Item.Properties().stacksTo(1)));

    // Материалы классовой брони
    public static final DeferredItem<Item> STEEL_PLATE = ITEMS.registerSimpleItem("steel_plate");
    public static final DeferredItem<Item> HARDENED_LEATHER = ITEMS.registerSimpleItem("hardened_leather");
    public static final DeferredItem<Item> ARCANE_CLOTH = ITEMS.registerSimpleItem("arcane_cloth");

    // Классовая броня: мечник (латы), лучник (кожа), маг (мантия)
    public static final DeferredItem<ClassArmorItem> WARRIOR_HELMET = armor("warrior_helmet", ModArmorMaterials.WARRIOR, ArmorItem.Type.HELMET, PlayerClass.WARRIOR, 20);
    public static final DeferredItem<ClassArmorItem> WARRIOR_CHESTPLATE = armor("warrior_chestplate", ModArmorMaterials.WARRIOR, ArmorItem.Type.CHESTPLATE, PlayerClass.WARRIOR, 20);
    public static final DeferredItem<ClassArmorItem> WARRIOR_LEGGINGS = armor("warrior_leggings", ModArmorMaterials.WARRIOR, ArmorItem.Type.LEGGINGS, PlayerClass.WARRIOR, 20);
    public static final DeferredItem<ClassArmorItem> WARRIOR_BOOTS = armor("warrior_boots", ModArmorMaterials.WARRIOR, ArmorItem.Type.BOOTS, PlayerClass.WARRIOR, 20);
    public static final DeferredItem<ClassArmorItem> ARCHER_HELMET = armor("archer_helmet", ModArmorMaterials.ARCHER, ArmorItem.Type.HELMET, PlayerClass.ARCHER, 16);
    public static final DeferredItem<ClassArmorItem> ARCHER_CHESTPLATE = armor("archer_chestplate", ModArmorMaterials.ARCHER, ArmorItem.Type.CHESTPLATE, PlayerClass.ARCHER, 16);
    public static final DeferredItem<ClassArmorItem> ARCHER_LEGGINGS = armor("archer_leggings", ModArmorMaterials.ARCHER, ArmorItem.Type.LEGGINGS, PlayerClass.ARCHER, 16);
    public static final DeferredItem<ClassArmorItem> ARCHER_BOOTS = armor("archer_boots", ModArmorMaterials.ARCHER, ArmorItem.Type.BOOTS, PlayerClass.ARCHER, 16);
    public static final DeferredItem<ClassArmorItem> MAGE_HELMET = armor("mage_helmet", ModArmorMaterials.MAGE, ArmorItem.Type.HELMET, PlayerClass.MAGE, 14);
    public static final DeferredItem<ClassArmorItem> MAGE_CHESTPLATE = armor("mage_chestplate", ModArmorMaterials.MAGE, ArmorItem.Type.CHESTPLATE, PlayerClass.MAGE, 14);
    public static final DeferredItem<ClassArmorItem> MAGE_LEGGINGS = armor("mage_leggings", ModArmorMaterials.MAGE, ArmorItem.Type.LEGGINGS, PlayerClass.MAGE, 14);
    public static final DeferredItem<ClassArmorItem> MAGE_BOOTS = armor("mage_boots", ModArmorMaterials.MAGE, ArmorItem.Type.BOOTS, PlayerClass.MAGE, 14);

    // ===== Этап B: руды, ступени материалов, грейды, руны =====

    // Стихийные кристаллы (руны) и самоцветы (жезлы, посохи, астральный шёлк)
    public static final DeferredItem<Item> FIRE_CRYSTAL = ITEMS.registerSimpleItem("fire_crystal");
    public static final DeferredItem<Item> FROST_CRYSTAL = ITEMS.registerSimpleItem("frost_crystal");
    public static final DeferredItem<Item> EARTH_CRYSTAL = ITEMS.registerSimpleItem("earth_crystal");
    public static final DeferredItem<Item> STORM_CRYSTAL = ITEMS.registerSimpleItem("storm_crystal");
    public static final DeferredItem<Item> TOPAZ = ITEMS.registerSimpleItem("topaz");
    public static final DeferredItem<Item> SAPPHIRE = ITEMS.registerSimpleItem("sapphire", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> RUBY = ITEMS.registerSimpleItem("ruby", new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<BlockItem> FIRE_CRYSTAL_ORE = ITEMS.registerSimpleBlockItem("fire_crystal_ore", ModBlocks.FIRE_CRYSTAL_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_FIRE_CRYSTAL_ORE = ITEMS.registerSimpleBlockItem("deepslate_fire_crystal_ore", ModBlocks.DEEPSLATE_FIRE_CRYSTAL_ORE);
    public static final DeferredItem<BlockItem> FROST_CRYSTAL_ORE = ITEMS.registerSimpleBlockItem("frost_crystal_ore", ModBlocks.FROST_CRYSTAL_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_FROST_CRYSTAL_ORE = ITEMS.registerSimpleBlockItem("deepslate_frost_crystal_ore", ModBlocks.DEEPSLATE_FROST_CRYSTAL_ORE);
    public static final DeferredItem<BlockItem> EARTH_CRYSTAL_ORE = ITEMS.registerSimpleBlockItem("earth_crystal_ore", ModBlocks.EARTH_CRYSTAL_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_EARTH_CRYSTAL_ORE = ITEMS.registerSimpleBlockItem("deepslate_earth_crystal_ore", ModBlocks.DEEPSLATE_EARTH_CRYSTAL_ORE);
    public static final DeferredItem<BlockItem> STORM_CRYSTAL_ORE = ITEMS.registerSimpleBlockItem("storm_crystal_ore", ModBlocks.STORM_CRYSTAL_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_STORM_CRYSTAL_ORE = ITEMS.registerSimpleBlockItem("deepslate_storm_crystal_ore", ModBlocks.DEEPSLATE_STORM_CRYSTAL_ORE);
    public static final DeferredItem<BlockItem> TOPAZ_ORE = ITEMS.registerSimpleBlockItem("topaz_ore", ModBlocks.TOPAZ_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_TOPAZ_ORE = ITEMS.registerSimpleBlockItem("deepslate_topaz_ore", ModBlocks.DEEPSLATE_TOPAZ_ORE);
    public static final DeferredItem<BlockItem> SAPPHIRE_ORE = ITEMS.registerSimpleBlockItem("sapphire_ore", ModBlocks.SAPPHIRE_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_SAPPHIRE_ORE = ITEMS.registerSimpleBlockItem("deepslate_sapphire_ore", ModBlocks.DEEPSLATE_SAPPHIRE_ORE);
    public static final DeferredItem<BlockItem> RUBY_ORE = ITEMS.registerSimpleBlockItem("ruby_ore", ModBlocks.RUBY_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_RUBY_ORE = ITEMS.registerSimpleBlockItem("deepslate_ruby_ore", ModBlocks.DEEPSLATE_RUBY_ORE);

    // Станки ремесла
    public static final DeferredItem<BlockItem> SMITHING_FORGE = ITEMS.registerSimpleBlockItem("smithing_forge", ModBlocks.SMITHING_FORGE);
    public static final DeferredItem<BlockItem> TANNING_VAT = ITEMS.registerSimpleBlockItem("tanning_vat", ModBlocks.TANNING_VAT);
    public static final DeferredItem<BlockItem> WEAVING_LOOM = ITEMS.registerSimpleBlockItem("weaving_loom", ModBlocks.WEAVING_LOOM);
    public static final DeferredItem<BlockItem> RUNE_TABLE = ITEMS.registerSimpleBlockItem("rune_table", ModBlocks.RUNE_TABLE);

    // Третья ступень материалов (вторая — сталь, прочная кожа, чародейская ткань — выше)
    public static final DeferredItem<Item> DAMASCUS_INGOT = ITEMS.registerSimpleItem("damascus_ingot", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> IMPREGNATED_LEATHER = ITEMS.registerSimpleItem("impregnated_leather", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> ASTRAL_SILK = ITEMS.registerSimpleItem("astral_silk", new Item.Properties().rarity(Rarity.UNCOMMON));

    // Руны стихий
    public static final DeferredItem<Item> FIRE_RUNE = ITEMS.registerSimpleItem("fire_rune", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> FROST_RUNE = ITEMS.registerSimpleItem("frost_rune", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> EARTH_RUNE = ITEMS.registerSimpleItem("earth_rune", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> STORM_RUNE = ITEMS.registerSimpleItem("storm_rune", new Item.Properties().stacksTo(16));

    // Классовая броня грейда I: железные латы, кожаная броня, шерстяная мантия мага
    public static final DeferredItem<ClassArmorItem> WARRIOR_IRON_HELMET = armor("warrior_iron_helmet", ModArmorMaterials.WARRIOR_IRON, ArmorItem.Type.HELMET, PlayerClass.WARRIOR, 1, 15);
    public static final DeferredItem<ClassArmorItem> WARRIOR_IRON_CHESTPLATE = armor("warrior_iron_chestplate", ModArmorMaterials.WARRIOR_IRON, ArmorItem.Type.CHESTPLATE, PlayerClass.WARRIOR, 1, 15);
    public static final DeferredItem<ClassArmorItem> WARRIOR_IRON_LEGGINGS = armor("warrior_iron_leggings", ModArmorMaterials.WARRIOR_IRON, ArmorItem.Type.LEGGINGS, PlayerClass.WARRIOR, 1, 15);
    public static final DeferredItem<ClassArmorItem> WARRIOR_IRON_BOOTS = armor("warrior_iron_boots", ModArmorMaterials.WARRIOR_IRON, ArmorItem.Type.BOOTS, PlayerClass.WARRIOR, 1, 15);
    public static final DeferredItem<ClassArmorItem> ARCHER_LEATHER_HELMET = armor("archer_leather_helmet", ModArmorMaterials.ARCHER_LEATHER, ArmorItem.Type.HELMET, PlayerClass.ARCHER, 1, 9);
    public static final DeferredItem<ClassArmorItem> ARCHER_LEATHER_CHESTPLATE = armor("archer_leather_chestplate", ModArmorMaterials.ARCHER_LEATHER, ArmorItem.Type.CHESTPLATE, PlayerClass.ARCHER, 1, 9);
    public static final DeferredItem<ClassArmorItem> ARCHER_LEATHER_LEGGINGS = armor("archer_leather_leggings", ModArmorMaterials.ARCHER_LEATHER, ArmorItem.Type.LEGGINGS, PlayerClass.ARCHER, 1, 9);
    public static final DeferredItem<ClassArmorItem> ARCHER_LEATHER_BOOTS = armor("archer_leather_boots", ModArmorMaterials.ARCHER_LEATHER, ArmorItem.Type.BOOTS, PlayerClass.ARCHER, 1, 9);
    public static final DeferredItem<ClassArmorItem> MAGE_WOOL_HELMET = armor("mage_wool_helmet", ModArmorMaterials.MAGE_WOOL, ArmorItem.Type.HELMET, PlayerClass.MAGE, 1, 8);
    public static final DeferredItem<ClassArmorItem> MAGE_WOOL_CHESTPLATE = armor("mage_wool_chestplate", ModArmorMaterials.MAGE_WOOL, ArmorItem.Type.CHESTPLATE, PlayerClass.MAGE, 1, 8);
    public static final DeferredItem<ClassArmorItem> MAGE_WOOL_LEGGINGS = armor("mage_wool_leggings", ModArmorMaterials.MAGE_WOOL, ArmorItem.Type.LEGGINGS, PlayerClass.MAGE, 1, 8);
    public static final DeferredItem<ClassArmorItem> MAGE_WOOL_BOOTS = armor("mage_wool_boots", ModArmorMaterials.MAGE_WOOL, ArmorItem.Type.BOOTS, PlayerClass.MAGE, 1, 8);

    // Классовая броня грейда III: дамасские латы, пропитанная кожа, астральная мантия
    public static final DeferredItem<ClassArmorItem> WARRIOR_DAMASCUS_HELMET = armor("warrior_damascus_helmet", ModArmorMaterials.WARRIOR_DAMASCUS, ArmorItem.Type.HELMET, PlayerClass.WARRIOR, 3, 33);
    public static final DeferredItem<ClassArmorItem> WARRIOR_DAMASCUS_CHESTPLATE = armor("warrior_damascus_chestplate", ModArmorMaterials.WARRIOR_DAMASCUS, ArmorItem.Type.CHESTPLATE, PlayerClass.WARRIOR, 3, 33);
    public static final DeferredItem<ClassArmorItem> WARRIOR_DAMASCUS_LEGGINGS = armor("warrior_damascus_leggings", ModArmorMaterials.WARRIOR_DAMASCUS, ArmorItem.Type.LEGGINGS, PlayerClass.WARRIOR, 3, 33);
    public static final DeferredItem<ClassArmorItem> WARRIOR_DAMASCUS_BOOTS = armor("warrior_damascus_boots", ModArmorMaterials.WARRIOR_DAMASCUS, ArmorItem.Type.BOOTS, PlayerClass.WARRIOR, 3, 33);
    public static final DeferredItem<ClassArmorItem> ARCHER_IMPREGNATED_HELMET = armor("archer_impregnated_helmet", ModArmorMaterials.ARCHER_IMPREGNATED, ArmorItem.Type.HELMET, PlayerClass.ARCHER, 3, 25);
    public static final DeferredItem<ClassArmorItem> ARCHER_IMPREGNATED_CHESTPLATE = armor("archer_impregnated_chestplate", ModArmorMaterials.ARCHER_IMPREGNATED, ArmorItem.Type.CHESTPLATE, PlayerClass.ARCHER, 3, 25);
    public static final DeferredItem<ClassArmorItem> ARCHER_IMPREGNATED_LEGGINGS = armor("archer_impregnated_leggings", ModArmorMaterials.ARCHER_IMPREGNATED, ArmorItem.Type.LEGGINGS, PlayerClass.ARCHER, 3, 25);
    public static final DeferredItem<ClassArmorItem> ARCHER_IMPREGNATED_BOOTS = armor("archer_impregnated_boots", ModArmorMaterials.ARCHER_IMPREGNATED, ArmorItem.Type.BOOTS, PlayerClass.ARCHER, 3, 25);
    public static final DeferredItem<ClassArmorItem> MAGE_ASTRAL_HELMET = armor("mage_astral_helmet", ModArmorMaterials.MAGE_ASTRAL, ArmorItem.Type.HELMET, PlayerClass.MAGE, 3, 22);
    public static final DeferredItem<ClassArmorItem> MAGE_ASTRAL_CHESTPLATE = armor("mage_astral_chestplate", ModArmorMaterials.MAGE_ASTRAL, ArmorItem.Type.CHESTPLATE, PlayerClass.MAGE, 3, 22);
    public static final DeferredItem<ClassArmorItem> MAGE_ASTRAL_LEGGINGS = armor("mage_astral_leggings", ModArmorMaterials.MAGE_ASTRAL, ArmorItem.Type.LEGGINGS, PlayerClass.MAGE, 3, 22);
    public static final DeferredItem<ClassArmorItem> MAGE_ASTRAL_BOOTS = armor("mage_astral_boots", ModArmorMaterials.MAGE_ASTRAL, ArmorItem.Type.BOOTS, PlayerClass.MAGE, 3, 22);

    // Оружие грейдов II (сталь) и III (дамаск)
    public static final DeferredItem<MeleeWeaponItem> IRON_SWORD = weapon("iron_sword", Tiers.IRON, WeaponType.SLASHING, false, 3, -2.4f, 0);
    public static final DeferredItem<MeleeWeaponItem> STEEL_SWORD = weapon("steel_sword", ModTiers.STEEL, WeaponType.SLASHING, false, 3, -2.4f, 0);
    public static final DeferredItem<BattleAxeItem> STEEL_AXE = ITEMS.register("steel_axe",
            () -> new BattleAxeItem(ModTiers.STEEL, new Item.Properties().attributes(AxeItem.createAttributes(ModTiers.STEEL, 6f, -3.05f))));
    public static final DeferredItem<DaggerItem> STEEL_DAGGER = ITEMS.register("steel_dagger",
            () -> new DaggerItem(ModTiers.STEEL, new Item.Properties()));
    public static final DeferredItem<GreatswordItem> STEEL_GREATSWORD = ITEMS.register("steel_greatsword",
            () -> new GreatswordItem(ModTiers.STEEL, new Item.Properties()));
    public static final DeferredItem<MeleeWeaponItem> DAMASCUS_SWORD = weapon("damascus_sword", ModTiers.DAMASCUS, WeaponType.SLASHING, false, 3, -2.4f, 0);
    public static final DeferredItem<BattleAxeItem> DAMASCUS_AXE = ITEMS.register("damascus_axe",
            () -> new BattleAxeItem(ModTiers.DAMASCUS, new Item.Properties().rarity(Rarity.UNCOMMON)
                    .attributes(AxeItem.createAttributes(ModTiers.DAMASCUS, 5.5f, -3.0f))));
    public static final DeferredItem<DaggerItem> DAMASCUS_DAGGER = ITEMS.register("damascus_dagger",
            () -> new DaggerItem(ModTiers.DAMASCUS, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<GreatswordItem> DAMASCUS_GREATSWORD = ITEMS.register("damascus_greatsword",
            () -> new GreatswordItem(ModTiers.DAMASCUS, new Item.Properties().rarity(Rarity.UNCOMMON)));
    // Луки грейдов II и III
    public static final DeferredItem<GradedBowItem> COMPOSITE_BOW = ITEMS.register("composite_bow",
            () -> new GradedBowItem(2, 25, new Item.Properties().durability(550)));
    public static final DeferredItem<GradedBowItem> RANGER_LONGBOW = ITEMS.register("ranger_longbow",
            () -> new GradedBowItem(3, 50, new Item.Properties().durability(900).rarity(Rarity.UNCOMMON)));

    // Рунные жезлы и посохи (грейд II) и жезлы и посохи архимага (грейд III) четырёх стихий
    public static final DeferredItem<FocusItem> FIRE_RUNE_WAND = focus("fire_rune_wand", 5.5f, false, 2, Element.FIRE);
    public static final DeferredItem<FocusItem> FIRE_RUNE_STAFF = focus("fire_rune_staff", 8f, true, 2, Element.FIRE);
    public static final DeferredItem<FocusItem> FROST_RUNE_WAND = focus("frost_rune_wand", 5.5f, false, 2, Element.WATER);
    public static final DeferredItem<FocusItem> FROST_RUNE_STAFF = focus("frost_rune_staff", 8f, true, 2, Element.WATER);
    public static final DeferredItem<FocusItem> EARTH_RUNE_WAND = focus("earth_rune_wand", 5.5f, false, 2, Element.EARTH);
    public static final DeferredItem<FocusItem> EARTH_RUNE_STAFF = focus("earth_rune_staff", 8f, true, 2, Element.EARTH);
    public static final DeferredItem<FocusItem> STORM_RUNE_WAND = focus("storm_rune_wand", 5.5f, false, 2, Element.AIR);
    public static final DeferredItem<FocusItem> STORM_RUNE_STAFF = focus("storm_rune_staff", 8f, true, 2, Element.AIR);
    public static final DeferredItem<FocusItem> FIRE_ARCHMAGE_WAND = focus("fire_archmage_wand", 7f, false, 3, Element.FIRE);
    public static final DeferredItem<FocusItem> FIRE_ARCHMAGE_STAFF = focus("fire_archmage_staff", 10f, true, 3, Element.FIRE);
    public static final DeferredItem<FocusItem> FROST_ARCHMAGE_WAND = focus("frost_archmage_wand", 7f, false, 3, Element.WATER);
    public static final DeferredItem<FocusItem> FROST_ARCHMAGE_STAFF = focus("frost_archmage_staff", 10f, true, 3, Element.WATER);
    public static final DeferredItem<FocusItem> EARTH_ARCHMAGE_WAND = focus("earth_archmage_wand", 7f, false, 3, Element.EARTH);
    public static final DeferredItem<FocusItem> EARTH_ARCHMAGE_STAFF = focus("earth_archmage_staff", 10f, true, 3, Element.EARTH);
    public static final DeferredItem<FocusItem> STORM_ARCHMAGE_WAND = focus("storm_archmage_wand", 7f, false, 3, Element.AIR);
    public static final DeferredItem<FocusItem> STORM_ARCHMAGE_STAFF = focus("storm_archmage_staff", 10f, true, 3, Element.AIR);

    // ===== Этап C: колющее, режущее и дробящее оружие трёх грейдов =====
    // Рапира (колющая, одноручная), копьё (колющее, двуручное, бьёт дальше),
    // шестопёр (дробящий, одноручный), боевой молот (дробящий, двуручный).
    public static final DeferredItem<MeleeWeaponItem> IRON_RAPIER = weapon("iron_rapier", Tiers.IRON, WeaponType.PIERCING, false, 2, -2.0f, 0);
    public static final DeferredItem<MeleeWeaponItem> STEEL_RAPIER = weapon("steel_rapier", ModTiers.STEEL, WeaponType.PIERCING, false, 2, -2.0f, 0);
    public static final DeferredItem<MeleeWeaponItem> DAMASCUS_RAPIER = weapon("damascus_rapier", ModTiers.DAMASCUS, WeaponType.PIERCING, false, 2, -2.0f, 0);
    public static final DeferredItem<MeleeWeaponItem> IRON_SPEAR = weapon("iron_spear", Tiers.IRON, WeaponType.PIERCING, true, 4, -2.8f, 1.0);
    public static final DeferredItem<MeleeWeaponItem> STEEL_SPEAR = weapon("steel_spear", ModTiers.STEEL, WeaponType.PIERCING, true, 4, -2.8f, 1.0);
    public static final DeferredItem<MeleeWeaponItem> DAMASCUS_SPEAR = weapon("damascus_spear", ModTiers.DAMASCUS, WeaponType.PIERCING, true, 4, -2.8f, 1.0);
    public static final DeferredItem<MeleeWeaponItem> IRON_FLANGED_MACE = weapon("iron_flanged_mace", Tiers.IRON, WeaponType.CRUSHING, false, 4, -2.8f, 0);
    public static final DeferredItem<MeleeWeaponItem> STEEL_FLANGED_MACE = weapon("steel_flanged_mace", ModTiers.STEEL, WeaponType.CRUSHING, false, 4, -2.8f, 0);
    public static final DeferredItem<MeleeWeaponItem> DAMASCUS_FLANGED_MACE = weapon("damascus_flanged_mace", ModTiers.DAMASCUS, WeaponType.CRUSHING, false, 4, -2.8f, 0);
    public static final DeferredItem<MeleeWeaponItem> IRON_WARHAMMER = weapon("iron_warhammer", Tiers.IRON, WeaponType.CRUSHING, true, 8, -3.3f, 0);
    public static final DeferredItem<MeleeWeaponItem> STEEL_WARHAMMER = weapon("steel_warhammer", ModTiers.STEEL, WeaponType.CRUSHING, true, 8, -3.3f, 0);
    public static final DeferredItem<MeleeWeaponItem> DAMASCUS_WARHAMMER = weapon("damascus_warhammer", ModTiers.DAMASCUS, WeaponType.CRUSHING, true, 8, -3.3f, 0);

    // ===== Этап G: ремёсла и ручные арбалеты =====
    // Трактаты ремёсел: ступень (подмастерье … мастер) хранится в компоненте manual_rank.
    public static final DeferredItem<com.heroicpaths.item.ProfessionManualItem> BLACKSMITH_MANUAL = ITEMS.register("blacksmith_manual",
            () -> new com.heroicpaths.item.ProfessionManualItem(com.heroicpaths.craft.Profession.BLACKSMITH, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<com.heroicpaths.item.ProfessionManualItem> CARPENTRY_MANUAL = ITEMS.register("carpentry_manual",
            () -> new com.heroicpaths.item.ProfessionManualItem(com.heroicpaths.craft.Profession.CARPENTER, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<com.heroicpaths.item.ProfessionManualItem> RUNECRAFT_MANUAL = ITEMS.register("runecraft_manual",
            () -> new com.heroicpaths.item.ProfessionManualItem(com.heroicpaths.craft.Profession.RUNECRAFTER, new Item.Properties().stacksTo(16)));
    // Ручные арбалеты (одноручные, быстро заряжаются, парой стреляют залпом) грейдов I–III.
    public static final DeferredItem<com.heroicpaths.item.HandCrossbowItem> HAND_CROSSBOW = ITEMS.register("hand_crossbow",
            () -> new com.heroicpaths.item.HandCrossbowItem(1, 16, 2.4f, new Item.Properties().durability(350)));
    public static final DeferredItem<com.heroicpaths.item.HandCrossbowItem> STEEL_HAND_CROSSBOW = ITEMS.register("steel_hand_crossbow",
            () -> new com.heroicpaths.item.HandCrossbowItem(2, 13, 2.7f, new Item.Properties().durability(600)));
    public static final DeferredItem<com.heroicpaths.item.HandCrossbowItem> DAMASCUS_HAND_CROSSBOW = ITEMS.register("damascus_hand_crossbow",
            () -> new com.heroicpaths.item.HandCrossbowItem(3, 10, 3.0f, new Item.Properties().durability(900).rarity(Rarity.UNCOMMON)));

    // ===== Этап H: камни души и реликвии =====
    // Малый камень питает редкие и уникальные вещи, великий — до эпических, древний — до легендарных.
    public static final DeferredItem<com.heroicpaths.item.SoulStoneItem> LESSER_SOUL_STONE = ITEMS.register("lesser_soul_stone",
            () -> new com.heroicpaths.item.SoulStoneItem(40, com.heroicpaths.item.ItemRarity.Tier.UNIQUE, new Item.Properties()));
    public static final DeferredItem<com.heroicpaths.item.SoulStoneItem> GREATER_SOUL_STONE = ITEMS.register("greater_soul_stone",
            () -> new com.heroicpaths.item.SoulStoneItem(120, com.heroicpaths.item.ItemRarity.Tier.EPIC, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<com.heroicpaths.item.SoulStoneItem> ANCIENT_SOUL_STONE = ITEMS.register("ancient_soul_stone",
            () -> new com.heroicpaths.item.SoulStoneItem(300, com.heroicpaths.item.ItemRarity.Tier.LEGENDARY, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<com.heroicpaths.item.RelicSparkItem> RELIC_SPARK = ITEMS.register("relic_spark",
            () -> new com.heroicpaths.item.RelicSparkItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC).fireResistant()));
    public static final DeferredItem<BlockItem> RELIC_ALTAR = ITEMS.registerSimpleBlockItem("relic_altar", ModBlocks.RELIC_ALTAR,
            new Item.Properties().rarity(Rarity.EPIC));

    // ===== Щиты трёх видов и грейдов I–III (железо, сталь, дамаск) =====
    public static final DeferredItem<GradedShieldItem> IRON_BUCKLER = shield("iron_buckler", GradedShieldItem.Type.BUCKLER, 1);
    public static final DeferredItem<GradedShieldItem> STEEL_BUCKLER = shield("steel_buckler", GradedShieldItem.Type.BUCKLER, 2);
    public static final DeferredItem<GradedShieldItem> DAMASCUS_BUCKLER = shield("damascus_buckler", GradedShieldItem.Type.BUCKLER, 3);
    public static final DeferredItem<GradedShieldItem> IRON_HEATER_SHIELD = shield("iron_heater_shield", GradedShieldItem.Type.HEATER, 1);
    public static final DeferredItem<GradedShieldItem> STEEL_HEATER_SHIELD = shield("steel_heater_shield", GradedShieldItem.Type.HEATER, 2);
    public static final DeferredItem<GradedShieldItem> DAMASCUS_HEATER_SHIELD = shield("damascus_heater_shield", GradedShieldItem.Type.HEATER, 3);
    public static final DeferredItem<GradedShieldItem> IRON_KITE_SHIELD = shield("iron_kite_shield", GradedShieldItem.Type.KITE, 1);
    public static final DeferredItem<GradedShieldItem> STEEL_KITE_SHIELD = shield("steel_kite_shield", GradedShieldItem.Type.KITE, 2);
    public static final DeferredItem<GradedShieldItem> DAMASCUS_KITE_SHIELD = shield("damascus_kite_shield", GradedShieldItem.Type.KITE, 3);

    // ===== Алмазный тир: грейды IV–VI (алмаз → гранёный алмаз → бриллиант), ремесло гроссмейстера =====
    // Огранка на гранильном столе: алмаз → гранёный алмаз → бриллиант, каждый раз камень может расколоться.
    public static final DeferredItem<Item> FACETED_DIAMOND = ITEMS.registerSimpleItem("faceted_diamond", new Item.Properties().rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> BRILLIANT = ITEMS.registerSimpleItem("brilliant", new Item.Properties().rarity(Rarity.RARE));
    public static final DeferredItem<BlockItem> GEM_TABLE = ITEMS.registerSimpleBlockItem("gem_table", ModBlocks.GEM_TABLE);
    public static final DeferredItem<BlockItem> JEWELER_TABLE = ITEMS.registerSimpleBlockItem("jeweler_table", ModBlocks.JEWELER_TABLE);
    public static final DeferredItem<com.heroicpaths.item.ProfessionManualItem> JEWELER_MANUAL = ITEMS.register("jeweler_manual",
            () -> new com.heroicpaths.item.ProfessionManualItem(com.heroicpaths.craft.Profession.JEWELER, new Item.Properties().stacksTo(16)));
    // Заготовки: на наковальне вещь грейда III + заготовка IV даёт ту же вещь грейда IV, IV + V — грейд V, V + VI — грейд VI.
    public static final DeferredItem<GearBlankItem> DIAMOND_HELMET_BLANK = blank("diamond_helmet_blank", 4, ArmorItem.Type.HELMET);
    public static final DeferredItem<GearBlankItem> DIAMOND_CHESTPLATE_BLANK = blank("diamond_chestplate_blank", 4, ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<GearBlankItem> DIAMOND_LEGGINGS_BLANK = blank("diamond_leggings_blank", 4, ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<GearBlankItem> DIAMOND_BOOTS_BLANK = blank("diamond_boots_blank", 4, ArmorItem.Type.BOOTS);
    public static final DeferredItem<GearBlankItem> DIAMOND_WEAPON_BLANK = blank("diamond_weapon_blank", 4, null);
    public static final DeferredItem<GearBlankItem> FACETED_HELMET_BLANK = blank("faceted_helmet_blank", 5, ArmorItem.Type.HELMET);
    public static final DeferredItem<GearBlankItem> FACETED_CHESTPLATE_BLANK = blank("faceted_chestplate_blank", 5, ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<GearBlankItem> FACETED_LEGGINGS_BLANK = blank("faceted_leggings_blank", 5, ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<GearBlankItem> FACETED_BOOTS_BLANK = blank("faceted_boots_blank", 5, ArmorItem.Type.BOOTS);
    public static final DeferredItem<GearBlankItem> FACETED_WEAPON_BLANK = blank("faceted_weapon_blank", 5, null);
    public static final DeferredItem<GearBlankItem> BRILLIANT_HELMET_BLANK = blank("brilliant_helmet_blank", 6, ArmorItem.Type.HELMET);
    public static final DeferredItem<GearBlankItem> BRILLIANT_CHESTPLATE_BLANK = blank("brilliant_chestplate_blank", 6, ArmorItem.Type.CHESTPLATE);
    public static final DeferredItem<GearBlankItem> BRILLIANT_LEGGINGS_BLANK = blank("brilliant_leggings_blank", 6, ArmorItem.Type.LEGGINGS);
    public static final DeferredItem<GearBlankItem> BRILLIANT_BOOTS_BLANK = blank("brilliant_boots_blank", 6, ArmorItem.Type.BOOTS);
    public static final DeferredItem<GearBlankItem> BRILLIANT_WEAPON_BLANK = blank("brilliant_weapon_blank", 6, null);

    // Оружие ближнего боя IV–VI
    public static final DeferredItem<MeleeWeaponItem> DIAMOND_SWORD = weapon("diamond_sword", ModTiers.DIAMOND, WeaponType.SLASHING, false, 3, -2.4f, 0);
    public static final DeferredItem<BattleAxeItem> DIAMOND_AXE = ITEMS.register("diamond_axe",
            () -> new BattleAxeItem(ModTiers.DIAMOND, new Item.Properties().rarity(Rarity.RARE).attributes(AxeItem.createAttributes(ModTiers.DIAMOND, 5.5f, -3.0f))));
    public static final DeferredItem<MeleeWeaponItem> DIAMOND_RAPIER = weapon("diamond_rapier", ModTiers.DIAMOND, WeaponType.PIERCING, false, 2, -2.0f, 0);
    public static final DeferredItem<MeleeWeaponItem> DIAMOND_SPEAR = weapon("diamond_spear", ModTiers.DIAMOND, WeaponType.PIERCING, true, 4, -2.8f, 1.0);
    public static final DeferredItem<MeleeWeaponItem> DIAMOND_FLANGED_MACE = weapon("diamond_flanged_mace", ModTiers.DIAMOND, WeaponType.CRUSHING, false, 4, -2.8f, 0);
    public static final DeferredItem<MeleeWeaponItem> DIAMOND_WARHAMMER = weapon("diamond_warhammer", ModTiers.DIAMOND, WeaponType.CRUSHING, true, 8, -3.3f, 0);
    public static final DeferredItem<MeleeWeaponItem> FACETED_SWORD = weapon("faceted_sword", ModTiers.FACETED, WeaponType.SLASHING, false, 3, -2.4f, 0);
    public static final DeferredItem<BattleAxeItem> FACETED_AXE = ITEMS.register("faceted_axe",
            () -> new BattleAxeItem(ModTiers.FACETED, new Item.Properties().rarity(Rarity.RARE).attributes(AxeItem.createAttributes(ModTiers.FACETED, 5.5f, -3.0f))));
    public static final DeferredItem<DaggerItem> FACETED_DAGGER = ITEMS.register("faceted_dagger", () -> new DaggerItem(ModTiers.FACETED, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<GreatswordItem> FACETED_GREATSWORD = ITEMS.register("faceted_greatsword", () -> new GreatswordItem(ModTiers.FACETED, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<MeleeWeaponItem> FACETED_RAPIER = weapon("faceted_rapier", ModTiers.FACETED, WeaponType.PIERCING, false, 2, -2.0f, 0);
    public static final DeferredItem<MeleeWeaponItem> FACETED_SPEAR = weapon("faceted_spear", ModTiers.FACETED, WeaponType.PIERCING, true, 4, -2.8f, 1.0);
    public static final DeferredItem<MeleeWeaponItem> FACETED_FLANGED_MACE = weapon("faceted_flanged_mace", ModTiers.FACETED, WeaponType.CRUSHING, false, 4, -2.8f, 0);
    public static final DeferredItem<MeleeWeaponItem> FACETED_WARHAMMER = weapon("faceted_warhammer", ModTiers.FACETED, WeaponType.CRUSHING, true, 8, -3.3f, 0);
    public static final DeferredItem<MeleeWeaponItem> BRILLIANT_SWORD = weapon("brilliant_sword", ModTiers.BRILLIANT, WeaponType.SLASHING, false, 3, -2.4f, 0);
    public static final DeferredItem<BattleAxeItem> BRILLIANT_AXE = ITEMS.register("brilliant_axe",
            () -> new BattleAxeItem(ModTiers.BRILLIANT, new Item.Properties().rarity(Rarity.RARE).attributes(AxeItem.createAttributes(ModTiers.BRILLIANT, 5.5f, -3.0f))));
    public static final DeferredItem<DaggerItem> BRILLIANT_DAGGER = ITEMS.register("brilliant_dagger", () -> new DaggerItem(ModTiers.BRILLIANT, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<GreatswordItem> BRILLIANT_GREATSWORD = ITEMS.register("brilliant_greatsword", () -> new GreatswordItem(ModTiers.BRILLIANT, new Item.Properties().rarity(Rarity.RARE)));
    public static final DeferredItem<MeleeWeaponItem> BRILLIANT_RAPIER = weapon("brilliant_rapier", ModTiers.BRILLIANT, WeaponType.PIERCING, false, 2, -2.0f, 0);
    public static final DeferredItem<MeleeWeaponItem> BRILLIANT_SPEAR = weapon("brilliant_spear", ModTiers.BRILLIANT, WeaponType.PIERCING, true, 4, -2.8f, 1.0);
    public static final DeferredItem<MeleeWeaponItem> BRILLIANT_FLANGED_MACE = weapon("brilliant_flanged_mace", ModTiers.BRILLIANT, WeaponType.CRUSHING, false, 4, -2.8f, 0);
    public static final DeferredItem<MeleeWeaponItem> BRILLIANT_WARHAMMER = weapon("brilliant_warhammer", ModTiers.BRILLIANT, WeaponType.CRUSHING, true, 8, -3.3f, 0);
    // Щиты, луки, ручные арбалеты и мушкеты IV–VI
    public static final DeferredItem<GradedShieldItem> DIAMOND_BUCKLER = shield("diamond_buckler", GradedShieldItem.Type.BUCKLER, 4);
    public static final DeferredItem<GradedShieldItem> DIAMOND_HEATER_SHIELD = shield("diamond_heater_shield", GradedShieldItem.Type.HEATER, 4);
    public static final DeferredItem<GradedShieldItem> DIAMOND_KITE_SHIELD = shield("diamond_kite_shield", GradedShieldItem.Type.KITE, 4);
    public static final DeferredItem<GradedShieldItem> FACETED_BUCKLER = shield("faceted_buckler", GradedShieldItem.Type.BUCKLER, 5);
    public static final DeferredItem<GradedShieldItem> FACETED_HEATER_SHIELD = shield("faceted_heater_shield", GradedShieldItem.Type.HEATER, 5);
    public static final DeferredItem<GradedShieldItem> FACETED_KITE_SHIELD = shield("faceted_kite_shield", GradedShieldItem.Type.KITE, 5);
    public static final DeferredItem<GradedShieldItem> BRILLIANT_BUCKLER = shield("brilliant_buckler", GradedShieldItem.Type.BUCKLER, 6);
    public static final DeferredItem<GradedShieldItem> BRILLIANT_HEATER_SHIELD = shield("brilliant_heater_shield", GradedShieldItem.Type.HEATER, 6);
    public static final DeferredItem<GradedShieldItem> BRILLIANT_KITE_SHIELD = shield("brilliant_kite_shield", GradedShieldItem.Type.KITE, 6);
    public static final DeferredItem<GradedBowItem> DIAMOND_LONGBOW = ITEMS.register("diamond_longbow",
            () -> new GradedBowItem(4, 75, new Item.Properties().durability(1300).rarity(Rarity.RARE)));
    public static final DeferredItem<com.heroicpaths.item.HandCrossbowItem> DIAMOND_HAND_CROSSBOW = ITEMS.register("diamond_hand_crossbow",
            () -> new com.heroicpaths.item.HandCrossbowItem(4, 9, 3.3f, new Item.Properties().durability(1300).rarity(Rarity.RARE)));
    public static final DeferredItem<MusketItem> DIAMOND_MUSKET = musket("diamond_musket", 4);
    public static final DeferredItem<GradedBowItem> FACETED_LONGBOW = ITEMS.register("faceted_longbow",
            () -> new GradedBowItem(5, 100, new Item.Properties().durability(1700).rarity(Rarity.RARE)));
    public static final DeferredItem<com.heroicpaths.item.HandCrossbowItem> FACETED_HAND_CROSSBOW = ITEMS.register("faceted_hand_crossbow",
            () -> new com.heroicpaths.item.HandCrossbowItem(5, 8, 3.6f, new Item.Properties().durability(1700).rarity(Rarity.RARE)));
    public static final DeferredItem<MusketItem> FACETED_MUSKET = musket("faceted_musket", 5);
    public static final DeferredItem<GradedBowItem> BRILLIANT_LONGBOW = ITEMS.register("brilliant_longbow",
            () -> new GradedBowItem(6, 130, new Item.Properties().durability(2200).rarity(Rarity.RARE)));
    public static final DeferredItem<com.heroicpaths.item.HandCrossbowItem> BRILLIANT_HAND_CROSSBOW = ITEMS.register("brilliant_hand_crossbow",
            () -> new com.heroicpaths.item.HandCrossbowItem(6, 7, 4.0f, new Item.Properties().durability(2200).rarity(Rarity.RARE)));
    public static final DeferredItem<MusketItem> BRILLIANT_MUSKET = musket("brilliant_musket", 6);
    // Жезлы и посохи IV–VI четырёх стихий
    public static final DeferredItem<FocusItem> FIRE_DIAMOND_WAND = focus("fire_diamond_wand", 8.5f, false, 4, Element.FIRE);
    public static final DeferredItem<FocusItem> FIRE_DIAMOND_STAFF = focus("fire_diamond_staff", 12f, true, 4, Element.FIRE);
    public static final DeferredItem<FocusItem> FROST_DIAMOND_WAND = focus("frost_diamond_wand", 8.5f, false, 4, Element.WATER);
    public static final DeferredItem<FocusItem> FROST_DIAMOND_STAFF = focus("frost_diamond_staff", 12f, true, 4, Element.WATER);
    public static final DeferredItem<FocusItem> EARTH_DIAMOND_WAND = focus("earth_diamond_wand", 8.5f, false, 4, Element.EARTH);
    public static final DeferredItem<FocusItem> EARTH_DIAMOND_STAFF = focus("earth_diamond_staff", 12f, true, 4, Element.EARTH);
    public static final DeferredItem<FocusItem> STORM_DIAMOND_WAND = focus("storm_diamond_wand", 8.5f, false, 4, Element.AIR);
    public static final DeferredItem<FocusItem> STORM_DIAMOND_STAFF = focus("storm_diamond_staff", 12f, true, 4, Element.AIR);
    public static final DeferredItem<FocusItem> FIRE_FACETED_WAND = focus("fire_faceted_wand", 10f, false, 5, Element.FIRE);
    public static final DeferredItem<FocusItem> FIRE_FACETED_STAFF = focus("fire_faceted_staff", 14f, true, 5, Element.FIRE);
    public static final DeferredItem<FocusItem> FROST_FACETED_WAND = focus("frost_faceted_wand", 10f, false, 5, Element.WATER);
    public static final DeferredItem<FocusItem> FROST_FACETED_STAFF = focus("frost_faceted_staff", 14f, true, 5, Element.WATER);
    public static final DeferredItem<FocusItem> EARTH_FACETED_WAND = focus("earth_faceted_wand", 10f, false, 5, Element.EARTH);
    public static final DeferredItem<FocusItem> EARTH_FACETED_STAFF = focus("earth_faceted_staff", 14f, true, 5, Element.EARTH);
    public static final DeferredItem<FocusItem> STORM_FACETED_WAND = focus("storm_faceted_wand", 10f, false, 5, Element.AIR);
    public static final DeferredItem<FocusItem> STORM_FACETED_STAFF = focus("storm_faceted_staff", 14f, true, 5, Element.AIR);
    public static final DeferredItem<FocusItem> FIRE_BRILLIANT_WAND = focus("fire_brilliant_wand", 11.5f, false, 6, Element.FIRE);
    public static final DeferredItem<FocusItem> FIRE_BRILLIANT_STAFF = focus("fire_brilliant_staff", 16f, true, 6, Element.FIRE);
    public static final DeferredItem<FocusItem> FROST_BRILLIANT_WAND = focus("frost_brilliant_wand", 11.5f, false, 6, Element.WATER);
    public static final DeferredItem<FocusItem> FROST_BRILLIANT_STAFF = focus("frost_brilliant_staff", 16f, true, 6, Element.WATER);
    public static final DeferredItem<FocusItem> EARTH_BRILLIANT_WAND = focus("earth_brilliant_wand", 11.5f, false, 6, Element.EARTH);
    public static final DeferredItem<FocusItem> EARTH_BRILLIANT_STAFF = focus("earth_brilliant_staff", 16f, true, 6, Element.EARTH);
    public static final DeferredItem<FocusItem> STORM_BRILLIANT_WAND = focus("storm_brilliant_wand", 11.5f, false, 6, Element.AIR);
    public static final DeferredItem<FocusItem> STORM_BRILLIANT_STAFF = focus("storm_brilliant_staff", 16f, true, 6, Element.AIR);
    // Классовая броня IV–VI
    public static final DeferredItem<ClassArmorItem> WARRIOR_DIAMOND_HELMET = armor("warrior_diamond_helmet", ModArmorMaterials.WARRIOR_DIAMOND, ArmorItem.Type.HELMET, PlayerClass.WARRIOR, 4, 40);
    public static final DeferredItem<ClassArmorItem> WARRIOR_DIAMOND_CHESTPLATE = armor("warrior_diamond_chestplate", ModArmorMaterials.WARRIOR_DIAMOND, ArmorItem.Type.CHESTPLATE, PlayerClass.WARRIOR, 4, 40);
    public static final DeferredItem<ClassArmorItem> WARRIOR_DIAMOND_LEGGINGS = armor("warrior_diamond_leggings", ModArmorMaterials.WARRIOR_DIAMOND, ArmorItem.Type.LEGGINGS, PlayerClass.WARRIOR, 4, 40);
    public static final DeferredItem<ClassArmorItem> WARRIOR_DIAMOND_BOOTS = armor("warrior_diamond_boots", ModArmorMaterials.WARRIOR_DIAMOND, ArmorItem.Type.BOOTS, PlayerClass.WARRIOR, 4, 40);
    public static final DeferredItem<ClassArmorItem> WARRIOR_FACETED_HELMET = armor("warrior_faceted_helmet", ModArmorMaterials.WARRIOR_FACETED, ArmorItem.Type.HELMET, PlayerClass.WARRIOR, 5, 48);
    public static final DeferredItem<ClassArmorItem> WARRIOR_FACETED_CHESTPLATE = armor("warrior_faceted_chestplate", ModArmorMaterials.WARRIOR_FACETED, ArmorItem.Type.CHESTPLATE, PlayerClass.WARRIOR, 5, 48);
    public static final DeferredItem<ClassArmorItem> WARRIOR_FACETED_LEGGINGS = armor("warrior_faceted_leggings", ModArmorMaterials.WARRIOR_FACETED, ArmorItem.Type.LEGGINGS, PlayerClass.WARRIOR, 5, 48);
    public static final DeferredItem<ClassArmorItem> WARRIOR_FACETED_BOOTS = armor("warrior_faceted_boots", ModArmorMaterials.WARRIOR_FACETED, ArmorItem.Type.BOOTS, PlayerClass.WARRIOR, 5, 48);
    public static final DeferredItem<ClassArmorItem> WARRIOR_BRILLIANT_HELMET = armor("warrior_brilliant_helmet", ModArmorMaterials.WARRIOR_BRILLIANT, ArmorItem.Type.HELMET, PlayerClass.WARRIOR, 6, 56);
    public static final DeferredItem<ClassArmorItem> WARRIOR_BRILLIANT_CHESTPLATE = armor("warrior_brilliant_chestplate", ModArmorMaterials.WARRIOR_BRILLIANT, ArmorItem.Type.CHESTPLATE, PlayerClass.WARRIOR, 6, 56);
    public static final DeferredItem<ClassArmorItem> WARRIOR_BRILLIANT_LEGGINGS = armor("warrior_brilliant_leggings", ModArmorMaterials.WARRIOR_BRILLIANT, ArmorItem.Type.LEGGINGS, PlayerClass.WARRIOR, 6, 56);
    public static final DeferredItem<ClassArmorItem> WARRIOR_BRILLIANT_BOOTS = armor("warrior_brilliant_boots", ModArmorMaterials.WARRIOR_BRILLIANT, ArmorItem.Type.BOOTS, PlayerClass.WARRIOR, 6, 56);
    public static final DeferredItem<ClassArmorItem> ARCHER_DIAMOND_HELMET = armor("archer_diamond_helmet", ModArmorMaterials.ARCHER_DIAMOND, ArmorItem.Type.HELMET, PlayerClass.ARCHER, 4, 31);
    public static final DeferredItem<ClassArmorItem> ARCHER_DIAMOND_CHESTPLATE = armor("archer_diamond_chestplate", ModArmorMaterials.ARCHER_DIAMOND, ArmorItem.Type.CHESTPLATE, PlayerClass.ARCHER, 4, 31);
    public static final DeferredItem<ClassArmorItem> ARCHER_DIAMOND_LEGGINGS = armor("archer_diamond_leggings", ModArmorMaterials.ARCHER_DIAMOND, ArmorItem.Type.LEGGINGS, PlayerClass.ARCHER, 4, 31);
    public static final DeferredItem<ClassArmorItem> ARCHER_DIAMOND_BOOTS = armor("archer_diamond_boots", ModArmorMaterials.ARCHER_DIAMOND, ArmorItem.Type.BOOTS, PlayerClass.ARCHER, 4, 31);
    public static final DeferredItem<ClassArmorItem> ARCHER_FACETED_HELMET = armor("archer_faceted_helmet", ModArmorMaterials.ARCHER_FACETED, ArmorItem.Type.HELMET, PlayerClass.ARCHER, 5, 37);
    public static final DeferredItem<ClassArmorItem> ARCHER_FACETED_CHESTPLATE = armor("archer_faceted_chestplate", ModArmorMaterials.ARCHER_FACETED, ArmorItem.Type.CHESTPLATE, PlayerClass.ARCHER, 5, 37);
    public static final DeferredItem<ClassArmorItem> ARCHER_FACETED_LEGGINGS = armor("archer_faceted_leggings", ModArmorMaterials.ARCHER_FACETED, ArmorItem.Type.LEGGINGS, PlayerClass.ARCHER, 5, 37);
    public static final DeferredItem<ClassArmorItem> ARCHER_FACETED_BOOTS = armor("archer_faceted_boots", ModArmorMaterials.ARCHER_FACETED, ArmorItem.Type.BOOTS, PlayerClass.ARCHER, 5, 37);
    public static final DeferredItem<ClassArmorItem> ARCHER_BRILLIANT_HELMET = armor("archer_brilliant_helmet", ModArmorMaterials.ARCHER_BRILLIANT, ArmorItem.Type.HELMET, PlayerClass.ARCHER, 6, 43);
    public static final DeferredItem<ClassArmorItem> ARCHER_BRILLIANT_CHESTPLATE = armor("archer_brilliant_chestplate", ModArmorMaterials.ARCHER_BRILLIANT, ArmorItem.Type.CHESTPLATE, PlayerClass.ARCHER, 6, 43);
    public static final DeferredItem<ClassArmorItem> ARCHER_BRILLIANT_LEGGINGS = armor("archer_brilliant_leggings", ModArmorMaterials.ARCHER_BRILLIANT, ArmorItem.Type.LEGGINGS, PlayerClass.ARCHER, 6, 43);
    public static final DeferredItem<ClassArmorItem> ARCHER_BRILLIANT_BOOTS = armor("archer_brilliant_boots", ModArmorMaterials.ARCHER_BRILLIANT, ArmorItem.Type.BOOTS, PlayerClass.ARCHER, 6, 43);
    public static final DeferredItem<ClassArmorItem> MAGE_DIAMOND_HELMET = armor("mage_diamond_helmet", ModArmorMaterials.MAGE_DIAMOND, ArmorItem.Type.HELMET, PlayerClass.MAGE, 4, 27);
    public static final DeferredItem<ClassArmorItem> MAGE_DIAMOND_CHESTPLATE = armor("mage_diamond_chestplate", ModArmorMaterials.MAGE_DIAMOND, ArmorItem.Type.CHESTPLATE, PlayerClass.MAGE, 4, 27);
    public static final DeferredItem<ClassArmorItem> MAGE_DIAMOND_LEGGINGS = armor("mage_diamond_leggings", ModArmorMaterials.MAGE_DIAMOND, ArmorItem.Type.LEGGINGS, PlayerClass.MAGE, 4, 27);
    public static final DeferredItem<ClassArmorItem> MAGE_DIAMOND_BOOTS = armor("mage_diamond_boots", ModArmorMaterials.MAGE_DIAMOND, ArmorItem.Type.BOOTS, PlayerClass.MAGE, 4, 27);
    public static final DeferredItem<ClassArmorItem> MAGE_FACETED_HELMET = armor("mage_faceted_helmet", ModArmorMaterials.MAGE_FACETED, ArmorItem.Type.HELMET, PlayerClass.MAGE, 5, 32);
    public static final DeferredItem<ClassArmorItem> MAGE_FACETED_CHESTPLATE = armor("mage_faceted_chestplate", ModArmorMaterials.MAGE_FACETED, ArmorItem.Type.CHESTPLATE, PlayerClass.MAGE, 5, 32);
    public static final DeferredItem<ClassArmorItem> MAGE_FACETED_LEGGINGS = armor("mage_faceted_leggings", ModArmorMaterials.MAGE_FACETED, ArmorItem.Type.LEGGINGS, PlayerClass.MAGE, 5, 32);
    public static final DeferredItem<ClassArmorItem> MAGE_FACETED_BOOTS = armor("mage_faceted_boots", ModArmorMaterials.MAGE_FACETED, ArmorItem.Type.BOOTS, PlayerClass.MAGE, 5, 32);
    public static final DeferredItem<ClassArmorItem> MAGE_BRILLIANT_HELMET = armor("mage_brilliant_helmet", ModArmorMaterials.MAGE_BRILLIANT, ArmorItem.Type.HELMET, PlayerClass.MAGE, 6, 37);
    public static final DeferredItem<ClassArmorItem> MAGE_BRILLIANT_CHESTPLATE = armor("mage_brilliant_chestplate", ModArmorMaterials.MAGE_BRILLIANT, ArmorItem.Type.CHESTPLATE, PlayerClass.MAGE, 6, 37);
    public static final DeferredItem<ClassArmorItem> MAGE_BRILLIANT_LEGGINGS = armor("mage_brilliant_leggings", ModArmorMaterials.MAGE_BRILLIANT, ArmorItem.Type.LEGGINGS, PlayerClass.MAGE, 6, 37);
    public static final DeferredItem<ClassArmorItem> MAGE_BRILLIANT_BOOTS = armor("mage_brilliant_boots", ModArmorMaterials.MAGE_BRILLIANT, ArmorItem.Type.BOOTS, PlayerClass.MAGE, 6, 37);

    // ===== Аксессуары: перчатки, пояс и наручи под тип брони (части сетов) и украшения ювелира. Грейд — в компоненте gear_grade. =====
    public static final DeferredItem<AccessoryItem> PLATE_GLOVES = accessory("plate_gloves", AccessoryKind.GLOVES, PlayerClass.WARRIOR);
    public static final DeferredItem<AccessoryItem> PLATE_BELT = accessory("plate_belt", AccessoryKind.BELT, PlayerClass.WARRIOR);
    public static final DeferredItem<AccessoryItem> PLATE_BRACERS = accessory("plate_bracers", AccessoryKind.BRACERS, PlayerClass.WARRIOR);
    public static final DeferredItem<AccessoryItem> LEATHER_GLOVES = accessory("leather_gloves", AccessoryKind.GLOVES, PlayerClass.ARCHER);
    public static final DeferredItem<AccessoryItem> LEATHER_BELT = accessory("leather_belt", AccessoryKind.BELT, PlayerClass.ARCHER);
    public static final DeferredItem<AccessoryItem> LEATHER_BRACERS = accessory("leather_bracers", AccessoryKind.BRACERS, PlayerClass.ARCHER);
    public static final DeferredItem<AccessoryItem> CLOTH_GLOVES = accessory("cloth_gloves", AccessoryKind.GLOVES, PlayerClass.MAGE);
    public static final DeferredItem<AccessoryItem> CLOTH_BELT = accessory("cloth_belt", AccessoryKind.BELT, PlayerClass.MAGE);
    public static final DeferredItem<AccessoryItem> CLOTH_BRACERS = accessory("cloth_bracers", AccessoryKind.BRACERS, PlayerClass.MAGE);
    public static final DeferredItem<AccessoryItem> NECKLACE = accessory("necklace", AccessoryKind.NECKLACE, null);
    public static final DeferredItem<AccessoryItem> EARRING = accessory("earring", AccessoryKind.EARRING, null);
    public static final DeferredItem<AccessoryItem> RING = accessory("ring", AccessoryKind.RING, null);

    /** Бонус фокуса к заклинаниям своей стихии, %: рунный — 15, архимага — 30, дальше +10 за грейд. */
    public static final int RUNE_FOCUS_ELEMENT_BONUS = 15;
    public static final int ARCHMAGE_FOCUS_ELEMENT_BONUS = 30;

    private ModItems() {}

    private static DeferredItem<ClassArmorItem> armor(String name, Holder<ArmorMaterial> material, ArmorItem.Type type,
                                                      PlayerClass playerClass, int durabilityFactor) {
        return armor(name, material, type, playerClass, 2, durabilityFactor);
    }

    private static DeferredItem<ClassArmorItem> armor(String name, Holder<ArmorMaterial> material, ArmorItem.Type type,
                                                      PlayerClass playerClass, int grade, int durabilityFactor) {
        Rarity rarity = gradeRarity(grade);
        return ITEMS.register(name, () -> new ClassArmorItem(material, type, playerClass, grade,
                new Item.Properties().durability(type.getDurability(durabilityFactor)).rarity(rarity)));
    }

    private static DeferredItem<MeleeWeaponItem> weapon(String name, Tier tier, WeaponType type, boolean twoHanded,
                                                        int damage, float speed, double reach) {
        Rarity rarity = tier == ModTiers.DAMASCUS ? Rarity.UNCOMMON
                : tier == ModTiers.DIAMOND || tier == ModTiers.FACETED || tier == ModTiers.BRILLIANT ? Rarity.RARE : Rarity.COMMON;
        return ITEMS.register(name, () -> new MeleeWeaponItem(tier, type, twoHanded, new Item.Properties().rarity(rarity)
                .attributes(MeleeWeaponItem.attributes(tier, damage, speed, reach))));
    }

    private static DeferredItem<MusketItem> musket(String name, int grade) {
        Rarity rarity = gradeRarity(grade);
        return ITEMS.register(name, () -> new MusketItem(grade, new Item.Properties().durability(350 + 250 * (grade - 1)).rarity(rarity)));
    }

    private static DeferredItem<AccessoryItem> accessory(String name, AccessoryKind kind, @Nullable PlayerClass armorClass) {
        return ITEMS.register(name, () -> new AccessoryItem(kind, armorClass, new Item.Properties()));
    }

    /** Заготовка грейда grade для брони слота type или, если type == null, для оружия. */
    private static DeferredItem<GearBlankItem> blank(String name, int grade, @Nullable ArmorItem.Type type) {
        return ITEMS.register(name, () -> new GearBlankItem(grade, type, new Item.Properties().stacksTo(16).rarity(gradeRarity(grade))));
    }

    /** Цвет названия по грейду: I–II — обычный, III — необычный, IV–VI — редкий. */
    private static Rarity gradeRarity(int grade) {
        return grade >= 4 ? Rarity.RARE : grade >= 3 ? Rarity.UNCOMMON : Rarity.COMMON;
    }

    private static DeferredItem<GradedShieldItem> shield(String name, GradedShieldItem.Type type, int grade) {
        Rarity rarity = gradeRarity(grade);
        return ITEMS.register(name, () -> new GradedShieldItem(type, grade, new Item.Properties().rarity(rarity)));
    }

    private static DeferredItem<FocusItem> focus(String name, float power, boolean staff, int grade, Element element) {
        int bonus = grade >= 3 ? ARCHMAGE_FOCUS_ELEMENT_BONUS + 10 * (grade - 3) : RUNE_FOCUS_ELEMENT_BONUS;
        Rarity rarity = grade >= 4 ? Rarity.EPIC : grade >= 3 ? Rarity.RARE : Rarity.UNCOMMON;
        return ITEMS.register(name, () -> new FocusItem(power, staff, grade, element, bonus,
                new Item.Properties().stacksTo(1).rarity(rarity)));
    }
}
