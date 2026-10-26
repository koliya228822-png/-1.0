package com.heroicpaths.village;

import com.heroicpaths.HeroicPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Раскладка особых зданий деревни. Оружейная, банк, книжная лавка, лавка скупщика, дома наставников и казармы не выпадают из пула случайно: после того как
 * jigsaw разложил улицы и дома, часть больших домов (12x13, тот же вход, что у этих зданий) заменяется ими.
 * В каждой деревне все три наставника — мечник, лучник и маг,
 * и две-три казармы стражи. Четыре лавки (оружейная, банк, книги, скупка) есть в каждой деревне: без участка для них
 * деревня не ставится.
 */
public final class VillageLayout {

    /** Гильдия, профессия наставника и положение двери её дома (z), под которое подбирается большой дом. */
    record Mentor(String guild, String profession, int door) {}

    static final List<Mentor> MENTORS = List.of(
            new Mentor("swordmaster_guild", "swordmaster", 5),
            new Mentor("archer_guild", "master_archer", 5),
            new Mentor("mage_guild", "archmage", 4));

    /** Jigsaw наставника в полу дома гильдии (см. StructureGen.buildGuild). */
    private static final int MENTOR_X = 6;

    private VillageLayout() {}

    /** @return false, если для какой-то из лавок не нашлось участка — тогда деревня здесь не ставится. */
    public static boolean arrange(List<StructurePiece> pieces, Structure.GenerationContext context, String biome, RandomSource random) {
        // Участки — все дома деревни в нашем стиле; большие (той же формы, что дома наставников) — в первую очередь.
        List<PoolElementStructurePiece> big = new ArrayList<>(), other = new ArrayList<>();
        for (StructurePiece piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece p)) continue;
            String id = p.getElement().toString();
            if (!id.contains(HeroicPaths.MODID + ":village/" + biome + "/houses/")) continue;
            (id.contains("_d5") || id.contains("_d4") || (id.contains("/field_") && !id.contains("/field_small")) ? big : other).add(p);
        }
        shuffle(big, random);
        shuffle(other, random);
        List<PoolElementStructurePiece> lots = new ArrayList<>(big);
        lots.addAll(other);

        // Лавки есть в каждой деревне: они встают первыми, на лучшие участки.
        String prefix = HeroicPaths.MODID + ":village/" + biome + "/";
        if (build(pieces, lots, context, prefix + "armory", BARRACKS_DOOR) == null) return false;
        if (build(pieces, lots, context, prefix + "bank", BARRACKS_DOOR) == null) return false;
        if (build(pieces, lots, context, prefix + "bookshop", BARRACKS_DOOR) == null) return false;
        if (build(pieces, lots, context, prefix + "pawnshop", BARRACKS_DOOR) == null) return false;

        List<Mentor> order = new ArrayList<>(MENTORS);
        shuffle(order, random);
        int wanted = MENTORS.size();
        int placed = 0;
        for (Mentor mentor : order) {
            if (placed >= wanted) break;
            PoolElementStructurePiece house = build(pieces, lots, context, HeroicPaths.MODID + ":village/" + biome + "/" + mentor.guild(), mentor.door());
            if (house == null) continue;
            BlockPos jigsaw = worldPos(house, new BlockPos(MENTOR_X, 0, mentor.door() + 1));
            pieces.add(piece(context, HeroicPaths.MODID + ":village/" + biome + "/" + mentor.profession() + "_mentor",
                    jigsaw.above(), Rotation.NONE, 0, VillageInjector.NO_PROCESSORS));
            placed++;
        }
        // Все три наставника обязательны: если кому-то не нашлось участка, деревня здесь не ставится.
        if (placed < wanted) return false;
        // Сначала по одной казарме и мельнице, потом — сколько поместится: казарм до трёх, мельниц до двух.
        String barracks = HeroicPaths.MODID + ":village/" + biome + "/barracks", mill = HeroicPaths.MODID + ":village/" + biome + "/windmill";
        int lotCount = lots.size();
        build(pieces, lots, context, barracks, BARRACKS_DOOR);
        build(pieces, lots, context, mill, BARRACKS_DOOR);
        build(pieces, lots, context, barracks, BARRACKS_DOOR);
        if (lotCount >= 24) build(pieces, lots, context, barracks, BARRACKS_DOOR);
        if (lotCount >= 28) build(pieces, lots, context, mill, BARRACKS_DOOR);
        return true;
    }

    static final int BARRACKS_DOOR = 5;

    /**
     * Дома над водой или высоко над дном (озеро, река, обрыв) выкидываются вместе с их жителями; если таких
     * больше четверти или над водой оказался сам зал гильдии — деревня здесь не встаёт вовсе.
     */
    public static boolean dropHousesOverWater(List<StructurePiece> pieces, Structure.GenerationContext context) {
        List<StructurePiece> bad = new ArrayList<>();
        int buildings = 0;
        for (int i = 0; i < pieces.size(); i++) {
            StructurePiece piece = pieces.get(i);
            if (!(piece instanceof PoolElementStructurePiece p) || VillageWallPiece.isStreet(piece)) continue;
            if (p.getElement().toString().contains("/villagers/")) continue;
            BoundingBox b = piece.getBoundingBox();
            if (b.getXSpan() < 4 && b.getZSpan() < 4) continue;
            buildings++;
            boolean over = false;
            for (int[] c : new int[][]{{b.minX() + 1, b.minZ() + 1}, {b.maxX() - 1, b.maxZ() - 1}, {b.minX() + 1, b.maxZ() - 1},
                    {b.maxX() - 1, b.minZ() + 1}, {(b.minX() + b.maxX()) / 2, (b.minZ() + b.maxZ()) / 2}}) {
                int surface = TerrainSampler.of(context).surface(c[0], c[1]);
                int floor = TerrainSampler.of(context).floor(c[0], c[1]);
                if (surface > floor || b.minY() - floor > 8) over = true;
            }
            if (!over) continue;
            if (i == 0) return false;
            bad.add(piece);
        }
        if (bad.size() * 4 > buildings) return false;
        for (StructurePiece piece : bad) {
            BoundingBox box = piece.getBoundingBox();
            pieces.removeIf(p -> p == piece || isVillagerIn(p, box));
        }
        return true;
    }

    /** Дверь дома деревни (z входа в шаблоне): у домиков 3, у домов 4, у больших — по имени. */
    static int doorOf(String id) {
        if (id.contains("_d5")) return 5;
        if (id.contains("_d4")) return 4;
        if (id.contains("/field_small")) return 3;
        if (id.contains("/field_")) return 5;
        return id.contains("cottage") ? 3 : 4;
    }

    /**
     * Ставит здание на первый подходящий участок: вход здания совмещается со входом дома, а новый контур
     * не должен задевать соседние дома и улицы. Занятый участок убирается из списка.
     */
    @org.jetbrains.annotations.Nullable
    private static PoolElementStructurePiece build(List<StructurePiece> pieces, List<PoolElementStructurePiece> lots,
                                                   Structure.GenerationContext context, String location, int door) {
        for (int i = 0; i < lots.size(); i++) {
            PoolElementStructurePiece lot = lots.get(i);
            int shift = doorOf(lot.getElement().toString()) - door;
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(lot.getRotation());
            BlockPos pos = lot.getPosition().offset(StructureTemplate.calculateRelativePosition(settings, new BlockPos(0, 0, shift)));
            PoolElementStructurePiece house = piece(context, location, pos, lot.getRotation(), lot.getGroundLevelDelta(),
                    VillageInjector.foundation(VillageStyle.biomeOf(location)));
            BoundingBox box = house.getBoundingBox();
            boolean free = true;
            for (StructurePiece other : pieces) {
                // Улицы не считаем: их контуры (с expansion hack) накрывают и участки домов вдоль них.
                if (other == lot || isVillagerIn(other, lot.getBoundingBox()) || VillageWallPiece.isStreet(other)) continue;
                if (intersects2d(other.getBoundingBox(), box)) {
                    free = false;
                    break;
                }
            }
            if (!free) continue;
            lots.remove(i);
            house.getJunctions().addAll(lot.getJunctions());
            BoundingBox old = lot.getBoundingBox();
            pieces.removeIf(p -> isVillagerIn(p, old));
            pieces.set(pieces.indexOf(lot), house);
            return house;
        }
        return null;
    }

    private static boolean intersects2d(BoundingBox a, BoundingBox b) {
        return a.maxX() >= b.minX() && a.minX() <= b.maxX() && a.maxZ() >= b.minZ() && a.minZ() <= b.maxZ();
    }

    private static boolean isVillagerIn(StructurePiece piece, BoundingBox box) {
        return piece instanceof PoolElementStructurePiece e && e.getElement().toString().contains("/villagers/")
                && box.intersects(e.getBoundingBox());
    }

    private static PoolElementStructurePiece piece(Structure.GenerationContext context, String location, BlockPos pos, Rotation rotation,
                                                   int groundLevelDelta, ResourceKey<StructureProcessorList> processors) {
        Holder<StructureProcessorList> list = context.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).getHolderOrThrow(processors);
        StructurePoolElement element = StructurePoolElement.single(location, list).apply(StructureTemplatePool.Projection.RIGID);
        StructureTemplateManager manager = context.structureTemplateManager();
        return new PoolElementStructurePiece(manager, element, pos, groundLevelDelta, rotation,
                element.getBoundingBox(manager, pos, rotation), JigsawStructure.DEFAULT_LIQUID_SETTINGS);
    }

    static BlockPos worldPos(PoolElementStructurePiece piece, BlockPos local) {
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(piece.getRotation());
        return piece.getPosition().offset(StructureTemplate.calculateRelativePosition(settings, local));
    }

    private static <T> void shuffle(List<T> list, RandomSource random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T t = list.get(i);
            list.set(i, list.get(j));
            list.set(j, t);
        }
    }
}
