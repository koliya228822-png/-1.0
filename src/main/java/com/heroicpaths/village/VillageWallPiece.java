package com.heroicpaths.village;

import com.heroicpaths.entity.VillageGuard;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Городская стена вокруг деревни. Кольцо обходит все дома с запасом и повторяет очертания деревни
 * (радиус задан по секторам и сглажен), через каждые ~30 блоков — круглые башни с коническими крышами,
 * а там, где кольцо пересекает улицу, — надвратная башня с открытым арочным проходом (ворота не закрываются)
 * и двумя башнями по сторонам; от ворот наружу уходит дорога. Стена идёт по рельефу: высота каждого
 * участка считается от земли на осевой линии кольца, под стеной — фундамент до грунта.
 * У ворот при генерации встают стражники: два мечника в проходе и два лучника-часовых наверху.
 */
public class VillageWallPiece extends StructurePiece {
    static final int SECTORS = 90;
    /** Высота стены над землёй на осевой линии, толщина (4: внутренний парапет, два ряда хода, зубцы). */
    static final int HEIGHT = 9;
    static final float TOWER_R = 4.2f, ROOF_R = 4.8f;
    /** Надвратная башня: полудлина вдоль улицы и полуширина поперёк (проход — |c| ≤ 2, по бокам башни). */
    static final int GATE_A = 3, GATE_C = 9, PASSAGE = 3, ROAD = 11, ARCH = 8, TOWER_MID = 6;
    static final int MARGIN = 7, MIN_RADIUS = 40, LIMIT = 116;

    public record Gate(int x, int z, boolean alongX, int inward) {}

    private final int cx, cz, baseY;
    private final float[] radius;
    private final List<Gate> gates;
    private final List<int[]> towers;
    /** Дороги от концов улиц к воротам, которые поставлены не на пересечении улицы с кольцом: x1, z1, x2, z2. */
    private final List<int[]> roads = new ArrayList<>();
    private final String biome;
    private final Map<Long, Integer> heights = new HashMap<>();

    VillageWallPiece(int cx, int cz, int baseY, float[] radius, List<Gate> gates, List<int[]> towers, String biome, BoundingBox box) {
        super(ModWorldgen.VILLAGE_WALL.get(), 0, box);
        this.cx = cx;
        this.cz = cz;
        this.baseY = baseY;
        this.radius = radius;
        this.gates = gates;
        this.towers = towers;
        this.biome = biome;
    }

    public VillageWallPiece(CompoundTag tag) {
        super(ModWorldgen.VILLAGE_WALL.get(), tag);
        cx = tag.getInt("cx");
        cz = tag.getInt("cz");
        baseY = tag.getInt("y");
        int[] r = tag.getIntArray("radius");
        radius = new float[SECTORS];
        for (int i = 0; i < SECTORS && i < r.length; i++) radius[i] = r[i] / 16f;
        gates = new ArrayList<>();
        for (Tag t : tag.getList("gates", Tag.TAG_INT_ARRAY)) {
            int[] g = ((IntArrayTag) t).getAsIntArray();
            gates.add(new Gate(g[0], g[1], g[2] != 0, g[3]));
        }
        towers = new ArrayList<>();
        for (Tag t : tag.getList("towers", Tag.TAG_INT_ARRAY)) towers.add(((IntArrayTag) t).getAsIntArray());
        for (Tag t : tag.getList("roads", Tag.TAG_INT_ARRAY)) roads.add(((IntArrayTag) t).getAsIntArray());
        biome = tag.getString("biome");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("cx", cx);
        tag.putInt("cz", cz);
        tag.putInt("y", baseY);
        int[] r = new int[SECTORS];
        for (int i = 0; i < SECTORS; i++) r[i] = Math.round(radius[i] * 16);
        tag.putIntArray("radius", r);
        ListTag g = new ListTag();
        for (Gate gate : gates) g.add(new IntArrayTag(new int[]{gate.x(), gate.z(), gate.alongX() ? 1 : 0, gate.inward()}));
        tag.put("gates", g);
        ListTag t = new ListTag();
        for (int[] tower : towers) t.add(new IntArrayTag(tower));
        tag.put("towers", t);
        ListTag rd = new ListTag();
        for (int[] road : roads) rd.add(new IntArrayTag(road));
        tag.put("roads", rd);
        tag.putString("biome", biome);
    }

    public int centerX() {
        return cx;
    }

    public int centerZ() {
        return cz;
    }

    public List<Gate> gates() {
        return gates;
    }

    /** Радиус кольца в направлении угла (линейно между секторами). */
    public float radiusAt(double angle) {
        double s = (angle / (2 * Math.PI) * SECTORS) % SECTORS;
        if (s < 0) s += SECTORS;
        int i = (int) s;
        double f = s - i;
        return (float) (radius[i] * (1 - f) + radius[(i + 1) % SECTORS] * f);
    }

    /** Насколько точка за кольцом: < 0 — внутри деревни. */
    public double outside(double x, double z) {
        double dx = x - cx, dz = z - cz;
        return Math.sqrt(dx * dx + dz * dz) - radiusAt(Math.atan2(dz, dx));
    }

    // ---------------------------------------------------------------- раскладка

    /** Стена вокруг уже разложенной деревни. Первый кусок списка — центр деревни (зал гильдии). */
    public static VillageWallPiece around(List<StructurePiece> pieces, ChunkPos chunk, String biome, RandomSource random,
                                          net.minecraft.world.level.levelgen.structure.Structure.GenerationContext context) {
        StructureTemplateManager manager = context.structureTemplateManager();
        BoundingBox start = pieces.get(0).getBoundingBox();
        int cx = start.getCenter().getX(), cz = start.getCenter().getZ(), baseY = start.minY();
        float[] raw = new float[SECTORS];
        for (StructurePiece piece : pieces) {
            if (isStreet(piece)) continue;
            BoundingBox b = piece.getBoundingBox();
            for (int x = b.minX(); x <= b.maxX(); x += 2) {
                mark(raw, cx, cz, x, b.minZ());
                mark(raw, cx, cz, x, b.maxZ());
            }
            for (int z = b.minZ(); z <= b.maxZ(); z += 2) {
                mark(raw, cx, cz, b.minX(), z);
                mark(raw, cx, cz, b.maxX(), z);
            }
            mark(raw, cx, cz, b.maxX(), b.maxZ());
        }
        // Запас вокруг домов, расширение на соседние сектора (дом шире сектора), затем сглаживание.
        float[] need = new float[SECTORS];
        for (int i = 0; i < SECTORS; i++) {
            float m = 0;
            for (int j = -3; j <= 3; j++) m = Math.max(m, raw[Math.floorMod(i + j, SECTORS)]);
            need[i] = Math.max(MIN_RADIUS, m + MARGIN);
        }
        float[] r = need.clone();
        for (int pass = 0; pass < 6; pass++) {
            float[] next = new float[SECTORS];
            for (int i = 0; i < SECTORS; i++) {
                float sum = 0;
                for (int j = -3; j <= 3; j++) sum += r[Math.floorMod(i + j, SECTORS)];
                next[i] = Math.max(need[i], sum / 7);
            }
            r = next;
        }
        for (int i = 0; i < SECTORS; i++) {
            double a = (i + 0.5) * 2 * Math.PI / SECTORS;
            double limit = LIMIT / Math.max(Math.abs(Math.cos(a)), Math.abs(Math.sin(a)));
            r[i] = (float) Math.min(r[i], limit);
        }
        VillageWallPiece probe = new VillageWallPiece(cx, cz, baseY, r, List.of(), List.of(), biome, start);

        // Ворота — там, где осевая линия улицы пересекает кольцо.
        List<Gate> gates = new ArrayList<>();
        List<double[]> allAxes = new ArrayList<>();
        // Ось улицы — по её jigsaw-стыкам «minecraft:street»: у прямой улицы два стыка на концах, у перекрёстка
        // три-четыре, ось идёт от каждого к середине. Контур куска улицы для этого не годится: он шире дороги.
        for (StructurePiece piece : pieces) {
            if (!isStreet(piece) || !(piece instanceof PoolElementStructurePiece street)) continue;
            List<BlockPos> ends = new ArrayList<>();
            for (StructureTemplate.StructureBlockInfo info : street.getElement().getShuffledJigsawBlocks(manager, street.getPosition(),
                    street.getRotation(), random)) {
                if (info.nbt() != null && "minecraft:street".equals(info.nbt().getString("name"))) ends.add(info.pos());
            }
            if (ends.size() < 2) continue;
            List<double[]> segments = new ArrayList<>();
            if (ends.size() == 2) {
                segments.add(new double[]{ends.get(0).getX(), ends.get(0).getZ(), ends.get(1).getX(), ends.get(1).getZ()});
            } else {
                double mx = 0, mz = 0;
                for (BlockPos e : ends) {
                    mx += e.getX();
                    mz += e.getZ();
                }
                mx /= ends.size();
                mz /= ends.size();
                for (BlockPos e : ends) segments.add(new double[]{e.getX(), e.getZ(), mx, mz});
            }
            allAxes.addAll(segments);
            for (double[] s : segments) {
                double len = Math.hypot(s[2] - s[0], s[3] - s[1]);
                if (len < 1) continue;
                boolean alongX = Math.abs(s[2] - s[0]) >= Math.abs(s[3] - s[1]);
                double previous = Double.NaN;
                for (double t = 0; t <= len; t += 0.5) {
                    double x = s[0] + (s[2] - s[0]) * t / len + 0.5, z = s[1] + (s[3] - s[1]) * t / len + 0.5;
                    double v = probe.outside(x, z);
                    if (!Double.isNaN(previous) && (previous < 0) != (v < 0)) {
                        int gx = (int) Math.floor(x), gz = (int) Math.floor(z);
                        int along = alongX ? gx - cx : gz - cz;
                        int out = along > 0 ? 1 : -1;
                        if (!wet(context, alongX ? gx + out * 10 : gx, alongX ? gz : gz + out * 10)) addGate(gates, new Gate(gx, gz, alongX, -out));
                    }
                    previous = v;
                }
            }
        }
        // На каждой стороне света — хотя бы одни ворота. Ищем на этом участке кольца место, от которого прямо
        // внутрь, не задевая домов, ближе всего до какой-нибудь улицы, и ведём туда дорогу от ворот.
        List<int[]> roads = new ArrayList<>();
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        for (int[] dir : dirs) {
            boolean covered = false;
            for (Gate g : gates) {
                double gx = g.x() - cx, gz = g.z() - cz;
                if (gx * dir[0] + gz * dir[1] > Math.abs(gx * dir[1] - gz * dir[0])) covered = true;
            }
            if (covered) continue;
            double base = Math.atan2(dir[1], dir[0]);
            int[] best = null;
            for (int k = -20; k <= 20; k++) {
                double a = base + Math.toRadians(k * 2);
                double rr = probe.radiusAt(a);
                int px = (int) Math.round(cx + Math.cos(a) * rr), pz = (int) Math.round(cz + Math.sin(a) * rr);
                if (wet(context, px + dir[0] * 10, pz + dir[1] * 10)) continue;
                for (int t = 4; t <= 48; t++) {
                    int qx = px - dir[0] * t, qz = pz - dir[1] * t;
                    if (!clearRoad(pieces, px, pz, qx, qz)) break;
                    if (nearStreet(allAxes, qx, qz)) {
                        if (best == null || t < best[4]) best = new int[]{px, pz, qx, qz, t};
                        break;
                    }
                }
            }
            if (best != null && addGate(gates, new Gate(best[0], best[1], dir[0] != 0, -(dir[0] + dir[1])))) {
                roads.add(new int[]{best[2], best[3], best[0], best[1]});
            }
        }
        if (gates.isEmpty()) {
            addGate(gates, new Gate(cx + Math.round(probe.radiusAt(0)), cz, true, -1));
            addGate(gates, new Gate(cx - Math.round(probe.radiusAt(Math.PI)), cz, true, 1));
        }

        // Башни через каждые ~30 блоков кольца, не ближе 16 к воротам.
        List<int[]> towers = new ArrayList<>();
        double arc = 15;
        int steps = 1440;
        for (int k = 0; k < steps; k++) {
            double a = k * 2 * Math.PI / steps;
            double rr = probe.radiusAt(a);
            arc += rr * 2 * Math.PI / steps;
            if (arc < 30) continue;
            int x = (int) Math.round(cx + Math.cos(a) * rr), z = (int) Math.round(cz + Math.sin(a) * rr);
            boolean nearGate = false;
            for (Gate g : gates) nearGate |= Math.hypot(g.x() - x, g.z() - z) < 16;
            if (nearGate) continue;
            towers.add(new int[]{x, z});
            arc = 0;
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int k = 0; k < 360; k++) {
            double a = k * Math.PI / 180;
            double rr = probe.radiusAt(a);
            int x = (int) Math.round(cx + Math.cos(a) * rr), z = (int) Math.round(cz + Math.sin(a) * rr);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        int pad = ROAD + 2;
        BoundingBox box = new BoundingBox(minX - pad, baseY - 24, minZ - pad, maxX + pad, baseY + 40, maxZ + pad);
        VillageWallPiece wall = new VillageWallPiece(cx, cz, baseY, r, gates, towers, biome, box);
        wall.roads.addAll(roads);
        return wall;
    }

    /** Снаружи у ворот вода — ворота в озеро не ставим. */
    private static boolean wet(net.minecraft.world.level.levelgen.structure.Structure.GenerationContext context, int x, int z) {
        int surface = context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, context.heightAccessor(), context.randomState());
        int floor = context.chunkGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, context.heightAccessor(), context.randomState());
        return surface > floor;
    }

    /** Точка в двух блоках от оси какой-нибудь улицы. */
    private static boolean nearStreet(List<double[]> axes, double x, double z) {
        for (double[] s : axes) {
            double dx = s[2] - s[0], dz = s[3] - s[1], len2 = dx * dx + dz * dz;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - s[0]) * dx + (z - s[1]) * dz) / len2));
            if (Math.hypot(s[0] + dx * t - x, s[1] + dz * t - z) <= 2) return true;
        }
        return false;
    }

    /** Прямая дорога шириной 5 не задевает ни одного дома (улицы не в счёт). */
    private static boolean clearRoad(List<StructurePiece> pieces, int x1, int z1, int x2, int z2) {
        BoundingBox road = new BoundingBox(Math.min(x1, x2) - 2, -10000, Math.min(z1, z2) - 2, Math.max(x1, x2) + 2, 10000, Math.max(z1, z2) + 2);
        for (StructurePiece piece : pieces) {
            if (isStreet(piece)) continue;
            BoundingBox b = piece.getBoundingBox();
            if (b.maxX() >= road.minX() && b.minX() <= road.maxX() && b.maxZ() >= road.minZ() && b.minZ() <= road.maxZ()) return false;
        }
        return true;
    }

    private static boolean addGate(List<Gate> gates, Gate gate) {
        if (gates.size() >= 6) return false;
        for (Gate g : gates) if (Math.hypot(g.x() - gate.x(), g.z() - gate.z()) < 28) return false;
        gates.add(gate);
        return true;
    }

    private static void mark(float[] raw, int cx, int cz, int x, int z) {
        double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
        double a = Math.atan2(dz, dx);
        int i = Math.floorMod((int) Math.floor(a / (2 * Math.PI) * SECTORS), SECTORS);
        raw[i] = (float) Math.max(raw[i], Math.sqrt(dx * dx + dz * dz));
    }

    public static boolean isStreet(StructurePiece piece) {
        if (!(piece instanceof PoolElementStructurePiece p)) return false;
        String id = p.getElement().toString();
        return id.contains("/streets/") || id.contains("/terminators/");
    }

    // ---------------------------------------------------------------- постройка

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        VillageStyle style = VillageStyle.of(biome);
        RandomState randomState = level.getLevel().getChunkSource().randomState();
        BoundingBox area = new BoundingBox(Math.max(box.minX(), boundingBox.minX()), box.minY(), Math.max(box.minZ(), boundingBox.minZ()),
                Math.min(box.maxX(), boundingBox.maxX()), box.maxY(), Math.min(box.maxZ(), boundingBox.maxZ()));
        if (area.minX() > area.maxX() || area.minZ() > area.maxZ()) return;
        for (int x = area.minX(); x <= area.maxX(); x++) {
            for (int z = area.minZ(); z <= area.maxZ(); z++) {
                column(level, generator, randomState, style, box, x, z);
            }
        }
    }

    private void column(WorldGenLevel level, ChunkGenerator generator, RandomState rs, VillageStyle st, BoundingBox box, int x, int z) {
        int ground = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
        // Ворота и дорога через них.
        for (Gate g : gates) {
            int a = g.alongX() ? x - g.x() : z - g.z(), c = g.alongX() ? z - g.z() : x - g.x();
            if (Math.abs(a) <= GATE_A && Math.abs(c) <= GATE_C) {
                gateColumn(level, generator, rs, st, box, g, a, c, x, z, ground);
                return;
            }
            if (Math.abs(c) <= PASSAGE - 1 && Math.abs(a) <= ROAD) {
                road(level, st, box, x, z, ground);
                return;
            }
            if (Math.abs(a) == GATE_A + 1 && Math.abs(c) == TOWER_MID) banner(level, generator, rs, st, box, g, a, x, z);
        }
        for (int[] r : roads) {
            if (x >= Math.min(r[0], r[2]) - 2 && x <= Math.max(r[0], r[2]) + 2 && z >= Math.min(r[1], r[3]) - 2 && z <= Math.max(r[1], r[3]) + 2) {
                road(level, st, box, x, z, ground);
                break;
            }
        }
        double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
        double d = Math.sqrt(dx * dx + dz * dz), angle = Math.atan2(dz, dx);
        for (int[] t : towers) {
            double td = Math.hypot(x - t[0], z - t[1]);
            if (td <= ROOF_R) {
                towerColumn(level, generator, rs, st, box, t, td, x, z, ground);
                return;
            }
        }
        float r = radiusAt(angle);
        double v = d - r;
        int arcPos = (int) Math.round(angle * r);
        if (v >= -2 && v < 2) {
            int centerY = groundAt(generator, rs, level, (int) Math.round(cx + Math.cos(angle) * r), (int) Math.round(cz + Math.sin(angle) * r));
            wallColumn(level, st, box, x, z, (int) Math.floor(v + 2), centerY, ground, arcPos);
        } else if (v >= 2 && v < 3 && Math.floorMod(arcPos, 12) < 2) {
            int centerY = groundAt(generator, rs, level, (int) Math.round(cx + Math.cos(angle) * r), (int) Math.round(cz + Math.sin(angle) * r));
            int bottom = Math.min(ground, centerY) - 2;
            for (int y = bottom; y <= centerY + HEIGHT - 3; y++) set(level, box, x, y, z, y <= centerY + 1 ? st.plinth() : st.stone());
            set(level, box, x, centerY + HEIGHT - 2, z, st.stoneSlab().defaultBlockState());
            clear(level, box, x, z, centerY + HEIGHT - 1, centerY + HEIGHT + 4);
        }
    }

    /** Земля (верхний твёрдый блок) на осевой линии: по шуму генератора, одинаково для всех чанков. */
    private int groundAt(ChunkGenerator generator, RandomState rs, WorldGenLevel level, int x, int z) {
        return heights.computeIfAbsent(BlockPos.asLong(x, 0, z),
                k -> generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, rs) - 1);
    }

    private void wallColumn(WorldGenLevel level, VillageStyle st, BoundingBox box, int x, int z, int layer, int centerY, int ground, int arcPos) {
        int top = centerY + HEIGHT;
        int bottom = Math.min(ground, centerY) - 2;
        for (int y = bottom; y <= top; y++) {
            BlockState state;
            if (y <= centerY + 1) state = st.plinth().defaultBlockState();
            else if (layer == 3 && y == top - 1) state = st.accent().defaultBlockState();
            else if (layer == 3 && Math.floorMod(arcPos, 8) == 0 && (y == centerY + 5 || y == centerY + 6)) state = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            else state = st.stone().defaultBlockState();
            set(level, box, x, y, z, state);
        }
        clear(level, box, x, z, top + 1, top + 5);
        if (layer == 3) {
            set(level, box, x, top + 1, z, st.stone().defaultBlockState());
            if (Math.floorMod(arcPos, 4) < 2) set(level, box, x, top + 2, z, st.stone().defaultBlockState());
        } else if (layer == 0) {
            set(level, box, x, top + 1, z, st.stoneWall().defaultBlockState());
            if (Math.floorMod(arcPos, 10) == 0) set(level, box, x, top + 2, z, Blocks.LANTERN.defaultBlockState());
        }
    }

    private void towerColumn(WorldGenLevel level, ChunkGenerator generator, RandomState rs, VillageStyle st, BoundingBox box,
                             int[] t, double td, int x, int z, int ground) {
        int base = groundAt(generator, rs, level, t[0], t[1]);
        int top = base + HEIGHT + 5;
        if (td <= TOWER_R) {
            int bottom = Math.min(ground, base) - 2;
            for (int y = bottom; y <= top; y++) {
                BlockState state;
                if (y <= base + 1 || y == top) state = st.plinth().defaultBlockState();
                else if (td > TOWER_R - 1.2 && (y == base + HEIGHT || y == top - 1)) state = st.accent().defaultBlockState();
                else if (td > TOWER_R - 1.2 && (y == base + 6 || y == base + 12) && (x == t[0] || z == t[1])) state = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                else state = st.stone().defaultBlockState();
                set(level, box, x, y, z, state);
            }
            clear(level, box, x, z, top + 1, top + 8);
        }
        // Конический шатёр с выносом над стеной башни.
        int dx = x - t[0], dz = z - t[1];
        for (int lvl = 0; lvl < 7; lvl++) {
            double rr = ROOF_R - lvl * 0.75;
            if (rr <= 0.4) break;
            if (td > rr || td <= rr - 1.25) continue;
            Direction facing = Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? Direction.EAST : Direction.WEST) : (dz < 0 ? Direction.SOUTH : Direction.NORTH);
            set(level, box, x, top + 1 + lvl, z, td < 0.6 ? st.roofBlock().defaultBlockState()
                    : st.roofStairs().defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, Half.BOTTOM));
        }
        if (dx == 0 && dz == 0) {
            for (int y = top + 1; y <= top + 6; y++) set(level, box, x, y, z, st.roofBlock().defaultBlockState());
            set(level, box, x, top + 7, z, st.fence().defaultBlockState());
            set(level, box, x, top + 8, z, st.banner().defaultBlockState().setValue(BannerBlock.ROTATION, 0));
        }
    }

    private void gateColumn(WorldGenLevel level, ChunkGenerator generator, RandomState rs, VillageStyle st, BoundingBox box,
                            Gate g, int a, int c, int x, int z, int ground) {
        int gy = groundAt(generator, rs, level, g.x(), g.z());
        int absA = Math.abs(a), absC = Math.abs(c);
        int bottom = Math.min(ground, gy) - 2;
        if (absC <= PASSAGE) {
            // Проход: дорога, арка высотой 6 со скошенными углами, поднятая решётка, над ним — надвратная палата.
            for (int y = bottom; y < gy; y++) set(level, box, x, y, z, st.plinth().defaultBlockState());
            set(level, box, x, gy, z, absC == PASSAGE ? st.plinth().defaultBlockState() : st.path().defaultBlockState());
            int roof = gy + HEIGHT + 4;
            for (int y = gy + 1; y <= roof; y++) {
                BlockState state;
                if (y < gy + ARCH) state = Blocks.AIR.defaultBlockState();
                else if (y == gy + ARCH) state = absC == PASSAGE ? st.stone().defaultBlockState()
                        : (a == 0 ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                else if (y == gy + ARCH + 1 && absA == GATE_A) state = (c == 0 ? st.accent() : st.plinth()).defaultBlockState();
                else if (y == roof) state = st.plinth().defaultBlockState();
                else state = st.stone().defaultBlockState();
                set(level, box, x, y, z, state);
            }
            if (absA == 2 && c == 0) set(level, box, x, gy + ARCH, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            clear(level, box, x, z, roof + 1, roof + 6);
            if (absA == GATE_A) {
                set(level, box, x, roof + 1, z, st.stone().defaultBlockState());
                if (Math.floorMod(c, 2) == 0) set(level, box, x, roof + 2, z, st.stone().defaultBlockState());
            }
            if (a == 0 && c == 0) spawnGateGuards(level, g, x, z, gy, roof);
            return;
        }
        // Боковые башни ворот: выше стены, с зубцами и флагштоком.
        int top = gy + HEIGHT + 7;
        for (int y = bottom; y <= top; y++) {
            BlockState state;
            boolean face = absA == GATE_A || absC == GATE_C || absC == PASSAGE + 1;
            if (y <= gy + 1 || y == top) state = st.plinth().defaultBlockState();
            else if (face && (y == gy + HEIGHT + 4 || y == top - 1)) state = st.accent().defaultBlockState();
            else if (absA == GATE_A && absC == TOWER_MID && (y == gy + 4 || y == gy + 5 || y == gy + 11 || y == gy + 12)) state = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            else state = st.stone().defaultBlockState();
            set(level, box, x, y, z, state);
        }
        clear(level, box, x, z, top + 1, top + 6);
        boolean edge = absA == GATE_A || absC == GATE_C || absC == PASSAGE + 1;
        if (edge) {
            set(level, box, x, top + 1, z, st.stone().defaultBlockState());
            if (Math.floorMod(a + c, 2) == 0) set(level, box, x, top + 2, z, st.stone().defaultBlockState());
        }
        if (a == 0 && absC == TOWER_MID) {
            set(level, box, x, top + 1, z, st.fence().defaultBlockState());
            set(level, box, x, top + 2, z, st.fence().defaultBlockState());
            set(level, box, x, top + 3, z, st.banner().defaultBlockState().setValue(BannerBlock.ROTATION, g.alongX() ? 4 : 0));
        }
        if (absA == 1 && absC == TOWER_MID) set(level, box, x, top + 1, z, Blocks.LANTERN.defaultBlockState());
    }

    /** Знамёна на внешних и внутренних гранях башен ворот. */
    private void banner(WorldGenLevel level, ChunkGenerator generator, RandomState rs, VillageStyle st, BoundingBox box, Gate g, int a, int x, int z) {
        int gy = groundAt(generator, rs, level, g.x(), g.z());
        Direction facing = g.alongX() ? (a > 0 ? Direction.EAST : Direction.WEST) : (a > 0 ? Direction.SOUTH : Direction.NORTH);
        BlockPos pos = new BlockPos(x, gy + HEIGHT + 1, z);
        if (box.isInside(pos) && level.getBlockState(pos).canBeReplaced()) {
            level.setBlock(pos, st.wallBanner().defaultBlockState().setValue(WallBannerBlock.FACING, facing), 2);
        }
    }

    private void road(WorldGenLevel level, VillageStyle st, BoundingBox box, int x, int z, int ground) {
        BlockPos pos = new BlockPos(x, ground, z);
        if (!box.isInside(pos)) return;
        BlockState top = level.getBlockState(pos);
        if (top.is(BlockTags.DIRT) || top.is(BlockTags.SAND) || top.is(Blocks.GRAVEL) || top.is(Blocks.SNOW_BLOCK) || top.is(Blocks.DIRT_PATH)) {
            level.setBlock(pos, st.path().defaultBlockState(), 2);
            BlockPos above = pos.above();
            if (level.getBlockState(above).canBeReplaced() && level.getFluidState(above).isEmpty()) level.setBlock(above, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private void spawnGateGuards(WorldGenLevel level, Gate g, int x, int z, int gy, int roof) {
        int in = g.inward() * 2;
        int[][] spots = {{in, -1, gy + 1, 0}, {in, 1, gy + 1, 0}, {0, -1, roof + 1, 1}, {0, 1, roof + 1, 1}};
        for (int[] s : spots) {
            int sx = g.alongX() ? x + s[0] : x + s[1], sz = g.alongX() ? z + s[1] : z + s[0];
            EntityType<? extends VillageGuard> type = s[3] == 1 ? ModEntities.GUARD_ARCHER.get() : ModEntities.GUARD_SWORDSMAN.get();
            VillageGuard guard = type.create(level.getLevel());
            if (guard == null) continue;
            guard.moveTo(sx + 0.5, s[2], sz + 0.5, level.getRandom().nextFloat() * 360f, 0f);
            guard.finalizeSpawn(level, level.getCurrentDifficultyAt(guard.blockPosition()), MobSpawnType.STRUCTURE, null);
            guard.setPersistenceRequired();
            if (s[3] == 1) guard.setPost(new BlockPos(sx, s[2], sz));
            level.addFreshEntityWithPassengers(guard);
        }
    }

    private static void set(WorldGenLevel level, BoundingBox box, int x, int y, int z, net.minecraft.world.level.block.Block block) {
        set(level, box, x, y, z, block.defaultBlockState());
    }

    private static void set(WorldGenLevel level, BoundingBox box, int x, int y, int z, BlockState state) {
        BlockPos pos = new BlockPos(x, y, z);
        if (!box.isInside(pos)) return;
        level.setBlock(pos, state, 2);
    }

    /** Вычищает воздух над стеной (деревья, траву, снег). */
    private static void clear(WorldGenLevel level, BoundingBox box, int x, int z, int from, int to) {
        for (int y = from; y <= to; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!box.isInside(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && (state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || !state.getFluidState().isEmpty()
                    || state.is(Blocks.SNOW))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }
}
