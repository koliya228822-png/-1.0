package com.heroicpaths.village;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Материалы городской стены по типу деревни — те же, что у зала гильдии и домов (tools/StructureGen, hallStyle).
 */
public record VillageStyle(Block stone, Block stoneStairs, Block stoneSlab, Block stoneWall, Block accent, Block plinth,
                           Block roofStairs, Block roofBlock, Block fence, Block path, Block banner, Block wallBanner) {

    public static VillageStyle of(String biome) {
        return switch (biome) {
            case "desert" -> new VillageStyle(Blocks.CUT_SANDSTONE, Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.SMOOTH_SANDSTONE_SLAB,
                    Blocks.SANDSTONE_WALL, Blocks.CHISELED_SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE_STAIRS,
                    Blocks.SMOOTH_RED_SANDSTONE, Blocks.JUNGLE_FENCE, Blocks.SMOOTH_SANDSTONE, Blocks.ORANGE_BANNER, Blocks.ORANGE_WALL_BANNER);
            case "savanna" -> new VillageStyle(Blocks.MUD_BRICKS, Blocks.MUD_BRICK_STAIRS, Blocks.MUD_BRICK_SLAB, Blocks.MUD_BRICK_WALL,
                    Blocks.CHISELED_STONE_BRICKS, Blocks.TERRACOTTA, Blocks.BRICK_STAIRS, Blocks.BRICKS, Blocks.ACACIA_FENCE,
                    Blocks.DIRT_PATH, Blocks.RED_BANNER, Blocks.RED_WALL_BANNER);
            case "snowy" -> new VillageStyle(Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL,
                    Blocks.CHISELED_STONE_BRICKS, Blocks.POLISHED_DIORITE, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES,
                    Blocks.SPRUCE_FENCE, Blocks.PACKED_ICE, Blocks.LIGHT_BLUE_BANNER, Blocks.LIGHT_BLUE_WALL_BANNER);
            case "taiga" -> new VillageStyle(Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB,
                    Blocks.MOSSY_STONE_BRICK_WALL, Blocks.CHISELED_STONE_BRICKS, Blocks.MOSSY_COBBLESTONE, Blocks.DEEPSLATE_TILE_STAIRS,
                    Blocks.DEEPSLATE_TILES, Blocks.SPRUCE_FENCE, Blocks.DIRT_PATH, Blocks.GREEN_BANNER, Blocks.GREEN_WALL_BANNER);
            default -> new VillageStyle(Blocks.STONE_BRICKS, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, Blocks.STONE_BRICK_WALL,
                    Blocks.CHISELED_STONE_BRICKS, Blocks.POLISHED_ANDESITE, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILES,
                    Blocks.DARK_OAK_FENCE, Blocks.DIRT_PATH, Blocks.BLUE_BANNER, Blocks.BLUE_WALL_BANNER);
        };
    }

    /** Биом деревни по пути стартового пула: minecraft:village/plains/town_centers → plains. */
    public static String biomeOf(String poolId) {
        for (String biome : VillageInjector.BIOMES) if (poolId.contains("/" + biome + "/")) return biome;
        return "plains";
    }
}
