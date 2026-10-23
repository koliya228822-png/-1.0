package com.heroicpaths.village;

import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.HashMap;
import java.util.Map;

/**
 * Высоты рельефа для раскладки деревни: поверхность (с водой) и дно по шуму генератора. Каждый запрос
 * генератора стоит ~2 мс, а раскладка спрашивает тысячи точек, поэтому высоты берутся по сетке 4x4 и кэшируются
 * на время одной раскладки.
 */
public final class TerrainSampler {
    private static final ThreadLocal<TerrainSampler> CURRENT = new ThreadLocal<>();
    private final Structure.GenerationContext context;
    private final Map<Long, int[]> cache = new HashMap<>();

    private TerrainSampler(Structure.GenerationContext context) {
        this.context = context;
    }

    /** Включает кэш на время {@code task} для этого контекста. */
    static <T> T with(Structure.GenerationContext context, java.util.function.Supplier<T> task) {
        TerrainSampler previous = CURRENT.get();
        CURRENT.set(new TerrainSampler(context));
        try {
            return task.get();
        } finally {
            CURRENT.set(previous);
        }
    }

    static TerrainSampler of(Structure.GenerationContext context) {
        TerrainSampler s = CURRENT.get();
        return s != null && s.context == context ? s : new TerrainSampler(context);
    }

    private int[] sample(int x, int z) {
        int qx = x >> 2, qz = z >> 2;
        return cache.computeIfAbsent(((long) qx << 32) ^ (qz & 0xFFFFFFFFL), k -> {
            int sx = (qx << 2) + 2, sz = (qz << 2) + 2;
            int surface = context.chunkGenerator().getBaseHeight(sx, sz, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
            int floor = context.chunkGenerator().getBaseHeight(sx, sz, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
            return new int[]{surface, floor};
        });
    }

    /** Первый свободный блок над поверхностью (вода считается поверхностью). */
    public int surface(int x, int z) {
        return sample(x, z)[0];
    }

    /** Первый свободный блок над дном (без воды). */
    public int floor(int x, int z) {
        return sample(x, z)[1];
    }

    public boolean wet(int x, int z) {
        int[] s = sample(x, z);
        return s[0] > s[1];
    }
}
