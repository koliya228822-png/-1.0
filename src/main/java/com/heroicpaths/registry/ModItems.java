package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.dungeon.DungeonScrollItem;
import com.heroicpaths.dungeon.GateStoneItem;
import com.heroicpaths.dungeon.TrophyItem;
import com.heroicpaths.item.AbilityBookItem;
import com.heroicpaths.item.ClassArmorItem;
import com.heroicpaths.item.DaggerItem;
import com.heroicpaths.item.FocusItem;
import com.heroicpaths.item.GradedBowItem;
import com.heroicpaths.combat.WeaponType;
import com.heroicpaths.item.GradedShieldItem;
import com.heroicpaths.item.GreatswordItem;
import com.heroicpaths.item.MeleeWeaponItem;
import com.heroicpaths.item.MusketItem;
import net.minecraft.world.item.Tier;
import com.heroicpaths.rpg.stat.Element;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;
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
            () -> new DeferredSpawnEggItem(ModEntities.SPIDER_QUEEN, 0x2A1A28, 0xC02030, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> DROWNED_PROPHET_SPAWN_EGG = ITEMS.register("drowned_prophet_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.DROWNED_PROPHET, 0x2A6A6A, 0x8AF0E0, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> LICH_SPAWN_EGG = ITEMS.register("lich_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.LICH, 0x1A1A22, 0x60E0FF, new Item.Properties()));
    // Ледяная цитадель: Лич и его слуги
    public static final DeferredItem<DeferredSpawnEggItem> FROST_LICH_SPAWN_EGG = ITEMS.register("frost_lich_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.FROST_LICH, 0x2A3A6A, 0x8AE8FF, new Item.Properties()));
    public static final DeferredItem<DeferredSpawnEggItem> ICE_GOLEM_SPAWN_EGG = ITEMS.register("ice_golem_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.ICE_GOLEM, 0x92CCEC, 0x3E76AC, new Item.Properties()));
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
            () -> new DaggerItem(Tiers.DIAMOND, new Item.Properties()));
    public static final DeferredItem<GreatswordItem> IRON_GREATSWORD = ITEMS.register("iron_greatsword",
            () -> new GreatswordItem(Tiers.IRON, new Item.Properties()));
    public static final DeferredItem<GreatswordItem> DIAMOND_GREATSWORD = ITEMS.register("diamond_greatsword",
            () -> new GreatswordItem(Tiers.DIAMOND, new Item.Properties()));
    public static final DeferredItem<MusketItem> MUSKET = ITEMS.register("musket",
            () -> new MusketItem(new Item.Properties().durability(350)));
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
    public static final DeferredItem<SwordItem> STEEL_SWORD = ITEMS.register("steel_sword",
            () -> new SwordItem(ModTiers.STEEL, new Item.Properties().attributes(SwordItem.createAttributes(ModTiers.STEEL, 3, -2.4f))));
    public static final DeferredItem<AxeItem> STEEL_AXE = ITEMS.register("steel_axe",
            () -> new AxeItem(ModTiers.STEEL, new Item.Properties().attributes(AxeItem.createAttributes(ModTiers.STEEL, 6f, -3.05f))));
    public static final DeferredItem<DaggerItem> STEEL_DAGGER = ITEMS.register("steel_dagger",
            () -> new DaggerItem(ModTiers.STEEL, new Item.Properties()));
    public static final DeferredItem<GreatswordItem> STEEL_GREATSWORD = ITEMS.register("steel_greatsword",
            () -> new GreatswordItem(ModTiers.STEEL, new Item.Properties()));
    public static final DeferredItem<SwordItem> DAMASCUS_SWORD = ITEMS.register("damascus_sword",
            () -> new SwordItem(ModTiers.DAMASCUS, new Item.Properties().rarity(Rarity.UNCOMMON)
                    .attributes(SwordItem.createAttributes(ModTiers.DAMASCUS, 3, -2.4f))));
    public static final DeferredItem<AxeItem> DAMASCUS_AXE = ITEMS.register("damascus_axe",
            () -> new AxeItem(ModTiers.DAMASCUS, new Item.Properties().rarity(Rarity.UNCOMMON)
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

    /** Бонус фокуса к заклинаниям своей стихии, %: рунный — 15, архимага — 30. */
    public static final int RUNE_FOCUS_ELEMENT_BONUS = 15;
    public static final int ARCHMAGE_FOCUS_ELEMENT_BONUS = 30;

    private ModItems() {}

    private static DeferredItem<ClassArmorItem> armor(String name, Holder<ArmorMaterial> material, ArmorItem.Type type,
                                                      PlayerClass playerClass, int durabilityFactor) {
        return armor(name, material, type, playerClass, 2, durabilityFactor);
    }

    private static DeferredItem<ClassArmorItem> armor(String name, Holder<ArmorMaterial> material, ArmorItem.Type type,
                                                      PlayerClass playerClass, int grade, int durabilityFactor) {
        Rarity rarity = grade >= 3 ? Rarity.UNCOMMON : Rarity.COMMON;
        return ITEMS.register(name, () -> new ClassArmorItem(material, type, playerClass, grade,
                new Item.Properties().durability(type.getDurability(durabilityFactor)).rarity(rarity)));
    }

    private static DeferredItem<MeleeWeaponItem> weapon(String name, Tier tier, WeaponType type, boolean twoHanded,
                                                        int damage, float speed, double reach) {
        Rarity rarity = tier == ModTiers.DAMASCUS ? Rarity.UNCOMMON : Rarity.COMMON;
        return ITEMS.register(name, () -> new MeleeWeaponItem(tier, type, twoHanded, new Item.Properties().rarity(rarity)
                .attributes(MeleeWeaponItem.attributes(tier, damage, speed, reach))));
    }

    private static DeferredItem<GradedShieldItem> shield(String name, GradedShieldItem.Type type, int grade) {
        Rarity rarity = grade >= 3 ? Rarity.UNCOMMON : Rarity.COMMON;
        return ITEMS.register(name, () -> new GradedShieldItem(type, grade, new Item.Properties().rarity(rarity)));
    }

    private static DeferredItem<FocusItem> focus(String name, float power, boolean staff, int grade, Element element) {
        int bonus = grade >= 3 ? ARCHMAGE_FOCUS_ELEMENT_BONUS : RUNE_FOCUS_ELEMENT_BONUS;
        Rarity rarity = grade >= 3 ? Rarity.RARE : Rarity.UNCOMMON;
        return ITEMS.register(name, () -> new FocusItem(power, staff, grade, element, bonus,
                new Item.Properties().stacksTo(1).rarity(rarity)));
    }
}
