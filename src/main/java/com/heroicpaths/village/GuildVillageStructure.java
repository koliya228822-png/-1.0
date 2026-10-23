package com.heroicpaths.village;

import com.heroicpaths.registry.ModWorldgen;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Деревня Героических путей: обычная jigsaw-деревня (зал гильдии в центре, улицы, дома), но
 * <ul>
 *     <li>встаёт только на ровной суше — перепад высот на площадке и вода отсекают место;</li>
 *     <li>крупнее ванильной (глубже сеть улиц и шире разрешённая площадь);</li>
 *     <li>после раскладки на места больших домов встают дома наставников (ровно два разных, изредка три)
 *     и казармы стражи — см. {@link VillageLayout};</li>
 *     <li>обнесена стеной с воротами там, где из неё выходят улицы — {@link VillageWallPiece}.</li>
 * </ul>
 * Подключается переопределением ванильных data/minecraft/worldgen/structure/village_*.json.
 */
public class GuildVillageStructure extends Structure {
    public static final MapCodec<GuildVillageStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            StructureTemplatePool.CODEC.fieldOf("start_pool").forGetter(s -> s.startPool),
            Codec.intRange(0, 20).fieldOf("size").forGetter(s -> s.size),
            HeightProvider.CODEC.fieldOf("start_height").forGetter(s -> s.startHeight),
            Heightmap.Types.CODEC.optionalFieldOf("project_start_to_heightmap").forGetter(s -> s.projectStartToHeightmap),
            Codec.intRange(1, 128).fieldOf("max_distance_from_center").forGetter(s -> s.maxDistance),
            Codec.intRange(0, 64).optionalFieldOf("max_height_difference", 10).forGetter(s -> s.maxHeightDifference),
            Codec.intRange(8, 112).optionalFieldOf("flat_radius", 48).forGetter(s -> s.flatRadius)
    ).apply(i, GuildVillageStructure::new));

    private final Holder<StructureTemplatePool> startPool;
    private final int size;
    private final HeightProvider startHeight;
    private final Optional<Heightmap.Types> projectStartToHeightmap;
    private final int maxDistance;
    private final int maxHeightDifference;
    private final int flatRadius;
    private final JigsawStructure jigsaw;

    public GuildVillageStructure(StructureSettings settings, Holder<StructureTemplatePool> startPool, int size, HeightProvider startHeight,
                                 Optional<Heightmap.Types> projectStartToHeightmap, int maxDistance, int maxHeightDifference, int flatRadius) {
        super(settings);
        this.startPool = startPool;
        this.size = size;
        this.startHeight = startHeight;
        this.projectStartToHeightmap = projectStartToHeightmap;
        this.maxDistance = maxDistance;
        this.maxHeightDifference = maxHeightDifference;
        this.flatRadius = flatRadius;
        this.jigsaw = new JigsawStructure(settings, startPool, Optional.empty(), size, startHeight, true, projectStartToHeightmap,
                maxDistance, List.of(), JigsawStructure.DEFAULT_DIMENSION_PADDING, JigsawStructure.DEFAULT_LIQUID_SETTINGS);
    }

    public String biome() {
        return VillageStyle.biomeOf(startPool.unwrapKey().map(k -> k.location().toString()).orElse(""));
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!biomeFits(context)) return Optional.empty();
        return TerrainSampler.with(context, () -> {
            if (!isFlatLand(context)) return Optional.<GenerationStub>empty();
            Optional<GenerationStub> stub = jigsaw.findGenerationPoint(context);
            if (stub.isEmpty()) return stub;
            Optional<List<StructurePiece>> layout = layout(context, stub.get());
            if (layout.isEmpty()) return Optional.<GenerationStub>empty();
            List<StructurePiece> pieces = layout.get();
            return Optional.of(new GenerationStub(stub.get().position(), builder -> pieces.forEach(builder::addPiece)));
        });
    }

    /** Биом в центре подходит этой деревне (проверяем до дорогой раскладки; ваниль смотрит биом уже после). */
    private static boolean biomeFits(GenerationContext context) {
        ChunkPos c = context.chunkPos();
        int x = c.getMiddleBlockX(), z = c.getMiddleBlockZ();
        int y = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        return context.validBiome().test(context.biomeSource().getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(x),
                net.minecraft.core.QuartPos.fromBlock(y), net.minecraft.core.QuartPos.fromBlock(z), context.randomState().sampler()));
    }

    /** Раскладка деревни без проверки рельефа (для тестов) — улицы и дома jigsaw, особые здания, стена. */
    public Optional<List<StructurePiece>> layout(GenerationContext context) {
        return TerrainSampler.with(context, () -> jigsaw.findGenerationPoint(context).flatMap(stub -> layout(context, stub)));
    }

    private Optional<List<StructurePiece>> layout(GenerationContext context, GenerationStub stub) {
        List<StructurePiece> pieces = new ArrayList<>(stub.getPiecesBuilder().build().pieces());
        if (pieces.isEmpty()) return Optional.empty();
        // Заброшенная (зомби-) деревня остаётся как есть: без наставников, стражи и стен.
        if (isAbandoned(pieces)) return Optional.of(pieces);
        // Деревня должна быть большой: если улицы вышли короткими и домов мало, здесь её не будет.
        if (buildings(pieces) < MIN_BUILDINGS) return Optional.empty();
        ChunkPos chunk = context.chunkPos();
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(context.seed() ^ chunk.toLong() * 0x9E3779B97F4A7C15L));
        String biome = biome();
        if (!VillageLayout.dropHousesOverWater(pieces, context)) return Optional.empty();
        VillageLayout.arrange(pieces, context, biome, random);
        VillageWallPiece wall = VillageWallPiece.around(pieces, chunk, biome, random, context);
        pieces.add(wall);
        pieces.add(VillageFarmPiece.around(wall, chunk, biome, context, random));
        return Optional.of(pieces);
    }

    /** Меньше стольких домов и полей — деревня не ставится: на ней не уместятся наставники, казармы и мельница. */
    public static final int MIN_BUILDINGS = 14;

    static long buildings(List<StructurePiece> pieces) {
        return pieces.stream().filter(p -> p instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece e
                && e.getElement().toString().contains(com.heroicpaths.HeroicPaths.MODID + ":village/") && e.getElement().toString().contains("/houses/")).count();
    }

    public static boolean isAbandoned(List<StructurePiece> pieces) {
        return pieces.get(0) instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece start
                && start.getElement().toString().contains("/zombie/");
    }

    /**
     * Ровная суша: по сетке точек вокруг центра перепад высот поверхности не больше {@code maxHeightDifference},
     * и почти нигде нет воды (дно ниже поверхности).
     */
    boolean isFlatLand(GenerationContext context) {
        ChunkPos chunk = context.chunkPos();
        int cx = chunk.getMiddleBlockX(), cz = chunk.getMiddleBlockZ();
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE, wet = 0, samples = 0;
        int step = Math.max(8, flatRadius / 3);
        for (int dx = -flatRadius; dx <= flatRadius; dx += step) {
            for (int dz = -flatRadius; dz <= flatRadius; dz += step) {
                if (dx * dx + dz * dz > flatRadius * flatRadius) continue;
                int x = cx + dx, z = cz + dz;
                int surface = TerrainSampler.of(context).surface(x, z);
                int floor = TerrainSampler.of(context).floor(x, z);
                if (surface - floor > 0) wet++;
                min = Math.min(min, floor);
                max = Math.max(max, floor);
                samples++;
                if (max - min > maxHeightDifference) return false;
            }
        }
        return wet * 8 <= samples;
    }

    @Override
    public StructureType<?> type() {
        return ModWorldgen.GUILD_VILLAGE.get();
    }
}
