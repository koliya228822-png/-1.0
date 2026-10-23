package com.heroicpaths.village;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Пашни вокруг деревни за городской стеной: широкий пояс полей по рельефу, поделённый на наделы 12x12
 * с полевыми дорожками между ними. Больше всего пшеницы, есть наделы моркови, картофеля, свёклы и залежь;
 * у дорожек — стога, на краю пояса — низкая ограда из замшелого булыжника, от ворот через поля идёт дорога.
 * Среди полей стоят ветряные мельницы. Поля не заходят на воду и крутые склоны.
 */
public class VillageFarmPiece extends StructurePiece {
    /** Ширина пояса полей за стеной и отступ от её внешнего края. */
    static final int WIDTH = 30, GAP = 6, PLOT = 12;
    /** Пояс не выходит дальше этого от центра стартового чанка (структура достаёт не дальше 8 чанков). */
    static final int REACH = 120;

    private final int cx, cz, startX, startZ;
    private final float[] radius;
    private final List<VillageWallPiece.Gate> gates;
    private final List<int[]> mills;
    private final String biome;

    VillageFarmPiece(VillageWallPiece wall, ChunkPos start, String biome, List<int[]> mills, BoundingBox box) {
        super(ModWorldgen.VILLAGE_FARMS.get(), 0, box);
        this.cx = wall.centerX();
        this.cz = wall.centerZ();
        this.startX = start.getMiddleBlockX();
        this.startZ = start.getMiddleBlockZ();
        this.radius = wall.radii();
        this.gates = wall.gates();
        this.mills = mills;
        this.biome = biome;
    }

    public VillageFarmPiece(CompoundTag tag) {
        super(ModWorldgen.VILLAGE_FARMS.get(), tag);
        cx = tag.getInt("cx");
        cz = tag.getInt("cz");
        startX = tag.getInt("sx");
        startZ = tag.getInt("sz");
        int[] r = tag.getIntArray("radius");
        radius = new float[r.length];
        for (int i = 0; i < r.length; i++) radius[i] = r[i] / 16f;
        gates = new ArrayList<>();
        for (Tag t : tag.getList("gates", Tag.TAG_INT_ARRAY)) {
            int[] g = ((IntArrayTag) t).getAsIntArray();
            gates.add(new VillageWallPiece.Gate(g[0], g[1], g[2] != 0, g[3]));
        }
        mills = new ArrayList<>();
        for (Tag t : tag.getList("mills", Tag.TAG_INT_ARRAY)) mills.add(((IntArrayTag) t).getAsIntArray());
        biome = tag.getString("biome");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("cx", cx);
        tag.putInt("cz", cz);
        tag.putInt("sx", startX);
        tag.putInt("sz", startZ);
        int[] r = new int[radius.length];
        for (int i = 0; i < r.length; i++) r[i] = Math.round(radius[i] * 16);
        tag.putIntArray("radius", r);
        ListTag g = new ListTag();
        for (VillageWallPiece.Gate gate : gates) g.add(new IntArrayTag(new int[]{gate.x(), gate.z(), gate.alongX() ? 1 : 0, gate.inward()}));
        tag.put("gates", g);
        ListTag m = new ListTag();
        for (int[] mill : mills) m.add(new IntArrayTag(mill));
        tag.put("mills", m);
        tag.putString("biome", biome);
    }

    public List<int[]> mills() {
        return mills;
    }

    float radiusAt(double angle) {
        int n = radius.length;
        double s = (angle / (2 * Math.PI) * n) % n;
        if (s < 0) s += n;
        int i = (int) s;
        double f = s - i;
        return (float) (radius[i] * (1 - f) + radius[(i + 1) % n] * f);
    }

    /** Сколько за внешним краем стены (отрицательно — внутри). */
    double beyondWall(double x, double z) {
        double dx = x - cx, dz = z - cz;
        return Math.sqrt(dx * dx + dz * dz) - radiusAt(Math.atan2(dz, dx)) - 2;
    }

    boolean inReach(int x, int z) {
        return Math.abs(x - startX) <= REACH && Math.abs(z - startZ) <= REACH;
    }

    // ---------------------------------------------------------------- раскладка

    public static VillageFarmPiece around(VillageWallPiece wall, ChunkPos start, String biome, Structure.GenerationContext context, RandomSource random) {
        VillageFarmPiece probe = new VillageFarmPiece(wall, start, biome, List.of(), wall.getBoundingBox());
        // Мельницы: у дороги от ворот, в глубине полей, на ровной суше.
        List<int[]> mills = new ArrayList<>();
        List<VillageWallPiece.Gate> order = new ArrayList<>(wall.gates());
        for (int i = order.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            VillageWallPiece.Gate t = order.get(i);
            order.set(i, order.get(j));
            order.set(j, t);
        }
        for (VillageWallPiece.Gate g : order) {
            if (mills.size() >= 2) break;
            int ox = g.alongX() ? -g.inward() : 0, oz = g.alongX() ? 0 : -g.inward();
            int side = random.nextBoolean() ? 1 : -1;
            search:
            for (int depth = 12; depth <= 28; depth += 4) for (int off : new int[]{11 * side, -11 * side, 15 * side, -15 * side}) {
                int mx = g.x() + ox * depth + oz * off, mz = g.z() + oz * depth + ox * off;
                if (!probe.inReach(mx - 7, mz - 7) || !probe.inReach(mx + 7, mz + 7)) continue;
                if (probe.beyondWall(mx, mz) < GAP + 8) continue;
                Integer y = flatDry(context, mx, mz);
                if (y == null) continue;
                mills.add(new int[]{mx, y - 1, mz, random.nextInt(4)});
                break search;
            }
        }
        // У ворот не нашлось места — ищем вдоль всего пояса полей.
        for (int deg = random.nextInt(360), n = 0; n < 36 && mills.isEmpty(); n++, deg += 10) {
            double a = Math.toRadians(deg);
            double rr = probe.radiusAt(a) + 2 + GAP + 12;
            int mx = (int) Math.round(wall.centerX() + Math.cos(a) * rr), mz = (int) Math.round(wall.centerZ() + Math.sin(a) * rr);
            if (!probe.inReach(mx - 7, mz - 7) || !probe.inReach(mx + 7, mz + 7)) continue;
            Integer y = flatDry(context, mx, mz);
            if (y != null) mills.add(new int[]{mx, y - 1, mz, random.nextInt(4)});
        }
        BoundingBox b = wall.getBoundingBox();
        int pad = WIDTH + GAP + 4;
        BoundingBox box = new BoundingBox(Math.max(b.minX() - pad, start.getMiddleBlockX() - REACH), b.minY() - 8,
                Math.max(b.minZ() - pad, start.getMiddleBlockZ() - REACH), Math.min(b.maxX() + pad, start.getMiddleBlockX() + REACH),
                b.maxY() + 8, Math.min(b.maxZ() + pad, start.getMiddleBlockZ() + REACH));
        return new VillageFarmPiece(wall, start, biome, mills, box);
    }

    /** Высота ровной сухой площадки 13x13 вокруг точки или null. */
    static Integer flatDry(Structure.GenerationContext context, int x, int z) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx = -6; dx <= 6; dx += 6) {
            for (int dz = -6; dz <= 6; dz += 6) {
                int surface = TerrainSampler.of(context).surface(x + dx, z + dz);
                int floor = TerrainSampler.of(context).floor(x + dx, z + dz);
                if (surface > floor) return null;
                min = Math.min(min, floor);
                max = Math.max(max, floor);
            }
        }
        return max - min <= 4 ? min : null;
    }

    // ---------------------------------------------------------------- постройка

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        for (int x = Math.max(box.minX(), boundingBox.minX()); x <= Math.min(box.maxX(), boundingBox.maxX()); x++) {
            for (int z = Math.max(box.minZ(), boundingBox.minZ()); z <= Math.min(box.maxZ(), boundingBox.maxZ()); z++) {
                column(level, box, x, z);
            }
        }
        for (int[] mill : mills) placeMill(level, box, mill, random);
    }

    private void column(WorldGenLevel level, BoundingBox box, int x, int z) {
        if (!inReach(x, z)) return;
        double v = beyondWall(x + 0.5, z + 0.5);
        if (v < GAP || v > GAP + WIDTH) return;
        for (int[] m : mills) if (Math.abs(x - m[0]) <= 7 && Math.abs(z - m[2]) <= 7) return;
        int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
        BlockPos pos = new BlockPos(x, ground, z);
        if (!level.getFluidState(pos.above()).isEmpty()) return;
        BlockState top = level.getBlockState(pos);
        boolean soil = top.is(BlockTags.DIRT) || top.is(BlockTags.SAND) || top.is(Blocks.SNOW_BLOCK) || top.is(Blocks.DIRT_PATH)
                || top.is(Blocks.GRAVEL);
        if (!soil) return;
        // Дорога от ворот через поля.
        for (VillageWallPiece.Gate g : gates) {
            int a = g.alongX() ? x - g.x() : z - g.z(), c = g.alongX() ? z - g.z() : x - g.x();
            if (Math.abs(c) <= 2 && a * -g.inward() > 0) {
                track(level, box, pos, Math.abs(c) <= 1 ? Blocks.DIRT_PATH : Blocks.COARSE_DIRT);
                return;
            }
        }
        // Ограда по внешнему краю пояса.
        if (v > GAP + WIDTH - 1) {
            set(level, box, pos.above(), Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState());
            if (Math.floorMod(x * 31 + z * 17, 23) == 0) set(level, box, pos.above(2), Blocks.LANTERN.defaultBlockState());
            return;
        }
        // Крутой склон — оставляем луг.
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int nx = x + d[0], nz = z + d[1];
            if (!box.isInside(nx, ground, nz)) continue;
            if (Math.abs(level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, nx, nz) - 1 - ground) > 1) return;
        }
        int px = Math.floorDiv(x, PLOT), pz = Math.floorDiv(z, PLOT), lx = Math.floorMod(x, PLOT), lz = Math.floorMod(z, PLOT);
        long h = hash(px, pz);
        if (lx == 0 || lz == 0) {
            track(level, box, pos, Math.floorMod(x + z, 3) == 0 ? Blocks.COARSE_DIRT : Blocks.DIRT_PATH);
            if (hash(x, z) % 97 == 0) set(level, box, pos.above(), Blocks.HAY_BLOCK.defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, (hash(z, x) & 1) == 0 ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z));
            return;
        }
        int kind = (int) (h % 12);
        if (kind == 11) return; // залежь — луг
        Block crop = kind <= 6 ? Blocks.WHEAT : kind == 7 ? Blocks.CARROTS : kind == 8 ? Blocks.POTATOES : kind == 9 ? Blocks.BEETROOTS
                : Blocks.WHEAT;
        // Стога посреди скошенного надела.
        if (kind == 10) {
            if (hash(x, z) % 19 == 0) {
                set(level, box, pos, Blocks.DIRT.defaultBlockState());
                set(level, box, pos.above(), Blocks.HAY_BLOCK.defaultBlockState());
                if (hash(x, z) % 2 == 0) set(level, box, pos.above(2), Blocks.HAY_BLOCK.defaultBlockState());
            } else {
                set(level, box, pos, Blocks.COARSE_DIRT.defaultBlockState());
                clearAbove(level, box, pos);
            }
            return;
        }
        set(level, box, pos, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
        CropBlock cropBlock = (CropBlock) crop;
        int maxAge = cropBlock.getMaxAge();
        // Почти всё спелое, кое-где колос ещё зеленеет.
        int age = Math.max(1, maxAge - (hash(x * 7, z * 3) % 6 == 0 ? 1 + (int) (hash(z, x * 5) % 2) : 0));
        BlockState state = crop.defaultBlockState();
        state = crop == Blocks.BEETROOTS ? state.setValue(BlockStateProperties.AGE_3, Math.min(3, age))
                : state.setValue(BlockStateProperties.AGE_7, age);
        set(level, box, pos.above(), state);
        clearAbove(level, box, pos.above());
    }

    private void track(WorldGenLevel level, BoundingBox box, BlockPos pos, Block block) {
        set(level, box, pos, block.defaultBlockState());
        clearAbove(level, box, pos);
    }

    private void clearAbove(WorldGenLevel level, BoundingBox box, BlockPos pos) {
        for (int i = 1; i <= 2; i++) {
            BlockPos p = pos.above(i);
            BlockState s = level.getBlockState(p);
            if (!s.isAir() && (s.canBeReplaced() || s.is(Blocks.SNOW)) && s.getFluidState().isEmpty()) set(level, box, p, Blocks.AIR.defaultBlockState());
        }
    }

    private void placeMill(WorldGenLevel level, BoundingBox box, int[] mill, RandomSource random) {
        BoundingBox area = new BoundingBox(mill[0] - 7, mill[1] - 30, mill[2] - 7, mill[0] + 7, mill[1] + 20, mill[2] + 7);
        if (!area.intersects(box)) return;
        StructureTemplate template = level.getLevel().getStructureManager().getOrCreate(HeroicPaths.id("village/" + biome + "/windmill"));
        Rotation rotation = Rotation.values()[mill[3] & 3];
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setBoundingBox(box)
                .addProcessor(JigsawReplacementProcessor.INSTANCE)
                .addProcessor(new FoundationProcessor(VillageStyle.of(biome).plinth().defaultBlockState()));
        // Шаблон ставится центром в точку мельницы.
        BlockPos center = StructureTemplate.calculateRelativePosition(settings, new BlockPos(6, 0, 6));
        BlockPos origin = new BlockPos(mill[0], mill[1], mill[2]).subtract(center);
        template.placeInWorld(level, origin, origin, settings, random, 2);
    }

    private static void set(WorldGenLevel level, BoundingBox box, BlockPos pos, BlockState state) {
        if (box.isInside(pos)) level.setBlock(pos, state, 2);
    }

    private static long hash(int a, int b) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h & Long.MAX_VALUE;
    }
}
