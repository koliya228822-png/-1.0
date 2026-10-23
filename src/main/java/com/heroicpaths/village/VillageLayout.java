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
 * Раскладка особых зданий деревни. Дома наставников и казармы не выпадают из пула случайно: после того как
 * jigsaw разложил улицы и дома, часть больших домов (12x13, тот же вход, что у этих зданий) заменяется ими.
 * В каждой деревне ровно два наставника разных классов, в {@link #THIRD_MENTOR_CHANCE} деревень — все три,
 * и две-три казармы стражи.
 */
public final class VillageLayout {
    public static final float THIRD_MENTOR_CHANCE = 0.15f;

    /** Гильдия, профессия наставника и положение двери её дома (z), под которое подбирается большой дом. */
    record Mentor(String guild, String profession, int door) {}

    static final List<Mentor> MENTORS = List.of(
            new Mentor("swordmaster_guild", "swordmaster", 5),
            new Mentor("archer_guild", "master_archer", 5),
            new Mentor("mage_guild", "archmage", 4));

    /** Jigsaw наставника в полу дома гильдии (см. StructureGen.buildGuild). */
    private static final int MENTOR_X = 6;

    private VillageLayout() {}

    public static void arrange(List<StructurePiece> pieces, Structure.GenerationContext context, String biome, RandomSource random) {
        // Участки — все дома деревни в нашем стиле; большие (той же формы, что дома наставников) — в первую очередь.
        List<PoolElementStructurePiece> big = new ArrayList<>(), other = new ArrayList<>();
        for (StructurePiece piece : pieces) {
            if (!(piece instanceof PoolElementStructurePiece p)) continue;
            String id = p.getElement().toString();
            if (!id.contains(HeroicPaths.MODID + ":village/" + biome + "/houses/")) continue;
            (id.contains("_d5") || id.contains("_d4") ? big : other).add(p);
        }
        shuffle(big, random);
        shuffle(other, random);
        List<PoolElementStructurePiece> lots = new ArrayList<>(big);
        lots.addAll(other);

        List<Mentor> order = new ArrayList<>(MENTORS);
        shuffle(order, random);
        int wanted = random.nextFloat() < THIRD_MENTOR_CHANCE ? 3 : 2;
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
        int barracks = lots.size() >= 24 ? 3 : 2;
        for (int i = 0; i < barracks; i++) {
            if (build(pieces, lots, context, HeroicPaths.MODID + ":village/" + biome + "/barracks", BARRACKS_DOOR) == null) break;
        }
    }

    static final int BARRACKS_DOOR = 5;

    /** Дверь дома деревни (z входа в шаблоне): у домиков 3, у домов 4, у больших — по имени. */
    static int doorOf(String id) {
        if (id.contains("_d5")) return 5;
        if (id.contains("_d4")) return 4;
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
