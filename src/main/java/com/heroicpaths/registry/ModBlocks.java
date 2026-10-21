package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.craft.Station;
import com.heroicpaths.craft.StationBlock;
import com.heroicpaths.dungeon.DungeonPortalBlock;
import com.heroicpaths.dungeon.GateAltarBlock;
import com.heroicpaths.dungeon.QuestBoardBlock;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Рабочие места наставников, блоки данжей, руды кристаллов и самоцветов, станки ремесла. */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HeroicPaths.MODID);

    public static final DeferredBlock<Block> WEAPON_RACK = BLOCKS.registerSimpleBlock("weapon_rack",
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(2.5f));
    public static final DeferredBlock<Block> ARCHERY_TARGET = BLOCKS.registerSimpleBlock("archery_target",
            BlockBehaviour.Properties.ofFullCopy(Blocks.HAY_BLOCK));
    public static final DeferredBlock<Block> ARCANE_LECTERN = BLOCKS.registerSimpleBlock("arcane_lectern",
            BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_DEEPSLATE).lightLevel(state -> 7));

    /** Алтарь врат на площади деревни: открывает портал в данж Камнем врат. */
    public static final DeferredBlock<GateAltarBlock> GATE_ALTAR = BLOCKS.register("gate_altar",
            () -> new GateAltarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CRYING_OBSIDIAN).strength(50f, 1200f).lightLevel(state -> 10)));
    /** Врата подземелья под дикими входами в данжи: открываются без камня, не ломаются. */
    public static final DeferredBlock<com.heroicpaths.dungeon.DungeonGateBlock> DUNGEON_GATE = BLOCKS.register("dungeon_gate",
            () -> new com.heroicpaths.dungeon.DungeonGateBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CRYING_OBSIDIAN)
                    .strength(-1f, 3600000f).noLootTable().lightLevel(state -> 10)));
    /** Портал данжа: не ломается, сквозь него можно пройти. */
    public static final DeferredBlock<DungeonPortalBlock> DUNGEON_PORTAL = BLOCKS.register("dungeon_portal",
            () -> new DungeonPortalBlock(BlockBehaviour.Properties.of().noCollission().strength(-1f, 3600000f).noLootTable()
                    .lightLevel(state -> 11).sound(SoundType.GLASS).pushReaction(PushReaction.BLOCK)));
    /** Доска заданий гильдии. */
    public static final DeferredBlock<QuestBoardBlock> QUEST_BOARD = BLOCKS.register("quest_board",
            () -> new QuestBoardBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(2.5f)));

    // Руды стихийных кристаллов (для рун) — слегка светятся; добываются каменной киркой.
    public static final DeferredBlock<DropExperienceBlock> FIRE_CRYSTAL_ORE = crystalOre("fire_crystal_ore", false);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_FIRE_CRYSTAL_ORE = crystalOre("deepslate_fire_crystal_ore", true);
    public static final DeferredBlock<DropExperienceBlock> FROST_CRYSTAL_ORE = crystalOre("frost_crystal_ore", false);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_FROST_CRYSTAL_ORE = crystalOre("deepslate_frost_crystal_ore", true);
    public static final DeferredBlock<DropExperienceBlock> EARTH_CRYSTAL_ORE = crystalOre("earth_crystal_ore", false);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_EARTH_CRYSTAL_ORE = crystalOre("deepslate_earth_crystal_ore", true);
    public static final DeferredBlock<DropExperienceBlock> STORM_CRYSTAL_ORE = crystalOre("storm_crystal_ore", false);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_STORM_CRYSTAL_ORE = crystalOre("deepslate_storm_crystal_ore", true);
    // Руды самоцветов (для жезлов, посохов и астрального шёлка) — нужна железная кирка.
    public static final DeferredBlock<DropExperienceBlock> TOPAZ_ORE = gemOre("topaz_ore", false);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_TOPAZ_ORE = gemOre("deepslate_topaz_ore", true);
    public static final DeferredBlock<DropExperienceBlock> SAPPHIRE_ORE = gemOre("sapphire_ore", false);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_SAPPHIRE_ORE = gemOre("deepslate_sapphire_ore", true);
    public static final DeferredBlock<DropExperienceBlock> RUBY_ORE = gemOre("ruby_ore", false);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_RUBY_ORE = gemOre("deepslate_ruby_ore", true);

    // Станки ремесла
    public static final DeferredBlock<StationBlock> SMITHING_FORGE = BLOCKS.register("smithing_forge",
            () -> new StationBlock(Station.FORGE, BlockBehaviour.Properties.ofFullCopy(Blocks.BLAST_FURNACE).lightLevel(state -> 9)));
    public static final DeferredBlock<StationBlock> TANNING_VAT = BLOCKS.register("tanning_vat",
            () -> new StationBlock(Station.TANNERY, BlockBehaviour.Properties.ofFullCopy(Blocks.BARREL)));
    public static final DeferredBlock<StationBlock> WEAVING_LOOM = BLOCKS.register("weaving_loom",
            () -> new StationBlock(Station.LOOM, BlockBehaviour.Properties.ofFullCopy(Blocks.LOOM)));
    public static final DeferredBlock<StationBlock> RUNE_TABLE = BLOCKS.register("rune_table",
            () -> new StationBlock(Station.RUNE_TABLE, BlockBehaviour.Properties.ofFullCopy(Blocks.POLISHED_DEEPSLATE)
                    .strength(3f, 6f).lightLevel(state -> 6)));

    /** Доска заказов гильдии искателей приключений (рабочее место распорядителя гильдии). */
    public static final DeferredBlock<com.heroicpaths.guild.OrderBoardBlock> ORDER_BOARD = BLOCKS.register("order_board",
            () -> new com.heroicpaths.guild.OrderBoardBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).strength(2.5f)));

    /** Алтарь реликвий: поднимает легендарную вещь до реликвии за Искру реликвии. */
    public static final DeferredBlock<com.heroicpaths.craft.RelicAltarBlock> RELIC_ALTAR = BLOCKS.register("relic_altar",
            () -> new com.heroicpaths.craft.RelicAltarBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ENCHANTING_TABLE)
                    .lightLevel(state -> 8)));

    private ModBlocks() {}

    private static DeferredBlock<DropExperienceBlock> crystalOre(String name, boolean deepslate) {
        return BLOCKS.register(name, () -> new DropExperienceBlock(UniformInt.of(1, 4),
                BlockBehaviour.Properties.ofFullCopy(deepslate ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE).lightLevel(state -> 4)));
    }

    private static DeferredBlock<DropExperienceBlock> gemOre(String name, boolean deepslate) {
        return BLOCKS.register(name, () -> new DropExperienceBlock(UniformInt.of(3, 7),
                BlockBehaviour.Properties.ofFullCopy(deepslate ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE)));
    }
}
