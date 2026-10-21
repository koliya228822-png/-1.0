package com.heroicpaths.village;

import com.heroicpaths.HeroicPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jetbrains.annotations.Nullable;

/**
 * В новом мире игрок появляется в деревне: точка появления мира ставится в центр ближайшей к началу координат деревни
 * (там площадь с вратами в данжи и гильдии). Если деревень поблизости нет или постройки отключены — как в обычной игре.
 * Уже созданные миры не меняются.
 */
public final class VillageSpawn {
    /** Как далеко искать деревню, в чанках. */
    private static final int SEARCH_CHUNKS = 64;

    private VillageSpawn() {}

    @SubscribeEvent
    public static void onCreateSpawnPosition(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        if (!level.getServer().getWorldData().worldGenOptions().generateStructures()) return;
        BlockPos spawn = findVillageSpawn(level, BlockPos.ZERO);
        if (spawn == null) {
            HeroicPaths.LOGGER.info("No village within {} chunks of the origin: using the vanilla world spawn", SEARCH_CHUNKS);
            return;
        }
        event.getSettings().setSpawn(spawn, 0f);
        event.setCanceled(true);
        HeroicPaths.LOGGER.info("World spawn set to the village at {}", spawn);
    }

    /** Точка на поверхности в центре ближайшей деревни или null, если деревни нет. */
    @Nullable
    public static BlockPos findVillageSpawn(ServerLevel level, BlockPos origin) {
        BlockPos village = level.findNearestMapStructure(StructureTags.VILLAGE, origin, SEARCH_CHUNKS, false);
        if (village == null) return null;
        level.getChunk(village);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, village.getX(), village.getZ());
        return new BlockPos(village.getX(), y, village.getZ());
    }
}
