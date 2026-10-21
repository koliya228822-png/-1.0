package com.heroicpaths.rpg.tree;

import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.stat.Keystone;
import com.heroicpaths.rpg.stat.StatBlock;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.heroicpaths.rpg.stat.Stat.*;

/**
 * Пассивные деревья пяти классов в духе Path of Exile. У каждого класса своё дерево: изучать можно только его узлы.
 * Деревья лежат на одной карте пятью секторами по 72° (воин → разбойник → лучник → охотник → маг), но не связаны:
 * в центре у каждого класса три своих узла основы (кольцо разрезано между классами). В секторе старт и веер
 * из 6 ветвей (4 малых узла → значимый узел); у трёх внутренних ветвей есть продолжение — путь к ключевому камню
 * класса или второй значимый узел. От первой ветви отходит отросток со вторым ключевым камнем класса
 * (раньше это были мосты между классами; номера узлов сохранены, связи с чужими деревьями убраны).
 */
public final class PassiveTree {
    /** Версия раскладки. При смене раскладки сохранённые узлы сбрасываются (см. RpgData). */
    public static final int LAYOUT_VERSION = 2;

    private static final List<PassiveNode> NODES = new ArrayList<>();
    private static final List<int[]> EDGES = new ArrayList<>();
    private static final Int2ObjectMap<IntList> NEIGHBOURS = new Int2ObjectOpenHashMap<>();
    private static final Map<PlayerClass, Integer> STARTS = new EnumMap<>(PlayerClass.class);

    private sealed interface Extension permits KeystonePath, SecondNotable {}

    private record KeystonePath(StatBlock first, StatBlock second, Keystone keystone) implements Extension {}

    private record SecondNotable(StatBlock small, String notableId, StatBlock stats) implements Extension {}

    private record Branch(String id, StatBlock small, String notableId, StatBlock notable, @Nullable Extension extension) {}

    private record Sector(PlayerClass playerClass, double angle, StatBlock startStats, Branch... branches) {}

    private record CenterNode(String id, StatBlock stats) {}

    private static final CenterNode[] CENTER = {
            new CenterNode("center_vitality", StatBlock.of(MAX_HEALTH, 6)),
            new CenterNode("center_power", StatBlock.of(ABILITY_DAMAGE, 4)),
            new CenterNode("center_spirit", StatBlock.of(MAX_RESOURCE, 6)),
            new CenterNode("center_swiftness", StatBlock.of(MOVE_SPEED, 2)),
            new CenterNode("center_focus", StatBlock.of(RESOURCE_REGEN, 0.5f)),
    };

    private static final StatBlock BRIDGE = StatBlock.of(MAX_HEALTH, 6, ABILITY_DAMAGE, 3);

    private static Branch branch(String id, StatBlock small, String notableId, StatBlock notable) {
        return new Branch(id, small, notableId, notable, null);
    }

    private static Branch branch(String id, StatBlock small, String notableId, StatBlock notable, Extension extension) {
        return new Branch(id, small, notableId, notable, extension);
    }

    /** Ветви идут по часовой стрелке; у крайних (первой и последней) продолжений нет — рядом проходят мосты. */
    private static final Sector[] SECTORS = {
            new Sector(PlayerClass.WARRIOR, -90, StatBlock.of(MAX_HEALTH, 10),
                    branch("warrior_armor", StatBlock.of(ARMOR, 2), "steel_skin", StatBlock.of(ARMOR, 6, DAMAGE_REDUCTION, 4)),
                    branch("warrior_life", StatBlock.of(MAX_HEALTH, 8), "unbreakable", StatBlock.of(MAX_HEALTH_PCT, 10, ARMOR, 4),
                            new SecondNotable(StatBlock.of(MAX_HEALTH, 8), "colossus", StatBlock.of(MAX_HEALTH_PCT, 15, DAMAGE_REDUCTION, 5))),
                    branch("warrior_sword", StatBlock.of(SWORD_DAMAGE, 4), "blade_master", StatBlock.of(SWORD_DAMAGE, 12, BLEED_CHANCE, 8),
                            new KeystonePath(StatBlock.of(SWORD_DAMAGE, 4), StatBlock.of(MELEE_DAMAGE, 4), Keystone.BLOOD_RAGE)),
                    branch("warrior_crush", StatBlock.of(STUN_CHANCE, 2), "skullcrusher", StatBlock.of(STUN_CHANCE, 6, MELEE_DAMAGE, 8)),
                    branch("warrior_leech", StatBlock.of(LIFE_STEAL, 1), "bloodthirst", StatBlock.of(LIFE_STEAL, 3, MELEE_DAMAGE, 5),
                            new SecondNotable(StatBlock.of(LIFE_STEAL, 1), "insatiable", StatBlock.of(LIFE_STEAL, 4, MAX_HEALTH, 10))),
                    branch("warrior_axe", StatBlock.of(AXE_DAMAGE, 4), "headsman", StatBlock.of(AXE_DAMAGE, 12, CRIT_DAMAGE, 15))),
            new Sector(PlayerClass.ROGUE, -18, StatBlock.of(CRIT_CHANCE, 2, MOVE_SPEED, 2),
                    branch("rogue_pierce", StatBlock.of(ARMOR_PIERCE, 3), "needle", StatBlock.of(ARMOR_PIERCE, 10, CRIT_DAMAGE, 15)),
                    branch("rogue_blades", StatBlock.of(CRIT_CHANCE, 1.5f, MELEE_DAMAGE, 2), "shadow_dancer",
                            StatBlock.of(CRIT_CHANCE, 5, ATTACK_SPEED, 6, MOVE_SPEED, 3),
                            new KeystonePath(StatBlock.of(CRIT_CHANCE, 1.5f), StatBlock.of(MELEE_DAMAGE, 3), Keystone.AMBUSH)),
                    branch("rogue_dual", StatBlock.of(DUAL_WIELD_DAMAGE, 5), "twin_fangs", StatBlock.of(DUAL_WIELD_DAMAGE, 15, ATTACK_SPEED, 5)),
                    branch("rogue_evasion", StatBlock.of(DODGE_CHANCE, 1), "phantom", StatBlock.of(DODGE_CHANCE, 4, MOVE_SPEED, 5),
                            new SecondNotable(StatBlock.of(DODGE_CHANCE, 1), "untouchable", StatBlock.of(DODGE_CHANCE, 5, DAMAGE_REDUCTION, 4))),
                    branch("rogue_bolts", StatBlock.of(BOW_DAMAGE, 3), "deadeye", StatBlock.of(BOW_DAMAGE, 10, CRIT_DAMAGE, 15),
                            new SecondNotable(StatBlock.of(BOW_DAMAGE, 3), "bolt_thrower", StatBlock.of(BOW_DAMAGE, 12, ARMOR_PIERCE, 8))),
                    branch("rogue_poison", StatBlock.of(POISON_DAMAGE, 5), "venomancer", StatBlock.of(POISON_DAMAGE, 15, ABILITY_DAMAGE, 5))),
            new Sector(PlayerClass.ARCHER, 54, StatBlock.of(MOVE_SPEED, 3),
                    branch("archer_energy", StatBlock.of(MAX_RESOURCE, 6), "tireless", StatBlock.of(RESOURCE_REGEN, 2, MAX_RESOURCE, 10)),
                    branch("archer_agility", StatBlock.of(MOVE_SPEED, 2), "tailwind", StatBlock.of(MOVE_SPEED, 5, BOW_DAMAGE, 5),
                            new SecondNotable(StatBlock.of(MOVE_SPEED, 2), "phantom_step", StatBlock.of(MOVE_SPEED, 6, DODGE_CHANCE, 4))),
                    branch("archer_precision", StatBlock.of(CRIT_CHANCE, 2), "assassin", StatBlock.of(CRIT_CHANCE, 5, CRIT_DAMAGE, 20),
                            new KeystonePath(StatBlock.of(CRIT_CHANCE, 2), StatBlock.of(BOW_DAMAGE, 4), Keystone.COLD_PRECISION)),
                    branch("archer_aim", StatBlock.of(BOW_DAMAGE, 4), "sniper_eye", StatBlock.of(BOW_DAMAGE, 12, ARMOR_PIERCE, 8)),
                    branch("archer_survival", StatBlock.of(MAX_HEALTH, 8), "pathfinder", StatBlock.of(MAX_HEALTH, 10, ARMOR, 2, MOVE_SPEED, 3),
                            new SecondNotable(StatBlock.of(MAX_HEALTH, 8), "monster_hunter", StatBlock.of(BOW_DAMAGE, 8, LIFE_STEAL, 2))),
                    branch("archer_tactics", StatBlock.of(ABILITY_DAMAGE, 4), "tactician", StatBlock.of(ABILITY_DAMAGE, 10, COOLDOWN_REDUCTION, 5))),
            new Sector(PlayerClass.HUNTER, 126, StatBlock.of(MINION_DAMAGE, 5, MINION_HEALTH, 5),
                    branch("hunter_trapper", StatBlock.of(POISON_DAMAGE, 5), "trapper", StatBlock.of(POISON_DAMAGE, 12, ABILITY_DAMAGE, 8)),
                    branch("hunter_hide", StatBlock.of(MINION_HEALTH, 5), "thick_hide", StatBlock.of(MINION_HEALTH, 15, ARMOR, 3),
                            new SecondNotable(StatBlock.of(MINION_HEALTH, 5), "iron_hide", StatBlock.of(MINION_HEALTH, 20, DAMAGE_REDUCTION, 4))),
                    branch("hunter_beasts", StatBlock.of(MINION_DAMAGE, 5), "alpha", StatBlock.of(MINION_DAMAGE, 15, MINION_HEALTH, 15),
                            new KeystonePath(StatBlock.of(MINION_DAMAGE, 5), StatBlock.of(MINION_HEALTH, 5), Keystone.PACK_BOND)),
                    branch("hunter_bow", StatBlock.of(BOW_DAMAGE, 3), "bowyer", StatBlock.of(BOW_DAMAGE, 10, CRIT_CHANCE, 4),
                            new SecondNotable(StatBlock.of(BOW_DAMAGE, 3), "beast_slayer", StatBlock.of(BOW_DAMAGE, 8, MINION_DAMAGE, 8))),
                    branch("hunter_tracking", StatBlock.of(MOVE_SPEED, 2), "pack_leader", StatBlock.of(MINION_HEALTH, 15, MAX_HEALTH, 10, MOVE_SPEED, 3)),
                    branch("hunter_survival", StatBlock.of(HEALTH_REGEN, 0.2f), "survivalist", StatBlock.of(HEALTH_REGEN, 0.8f, MAX_HEALTH, 12))),
            new Sector(PlayerClass.MAGE, 198, StatBlock.of(MAX_RESOURCE, 10),
                    branch("mage_earth", StatBlock.of(EARTH_DAMAGE, 5), "stone_heart", StatBlock.of(EARTH_DAMAGE, 15, ARMOR, 4)),
                    branch("mage_fire", StatBlock.of(FIRE_DAMAGE, 5), "pyromaniac", StatBlock.of(FIRE_DAMAGE, 15, SPELL_DAMAGE, 4),
                            new SecondNotable(StatBlock.of(FIRE_DAMAGE, 5), "heart_of_flame", StatBlock.of(FIRE_DAMAGE, 20, CRIT_CHANCE, 3))),
                    branch("mage_power", StatBlock.of(SPELL_DAMAGE, 4), "archmage", StatBlock.of(SPELL_DAMAGE, 12, COOLDOWN_REDUCTION, 5),
                            new KeystonePath(StatBlock.of(SPELL_DAMAGE, 4), StatBlock.of(SPELL_DAMAGE, 4), Keystone.ELEMENTAL_HARMONY)),
                    branch("mage_water", StatBlock.of(WATER_DAMAGE, 5), "tidecaller", StatBlock.of(WATER_DAMAGE, 15, HEALING, 10)),
                    branch("mage_meditation", StatBlock.of(RESOURCE_REGEN, 0.5f), "enlightenment", StatBlock.of(RESOURCE_REGEN, 2, COST_REDUCTION, 5),
                            new SecondNotable(StatBlock.of(RESOURCE_REGEN, 0.5f), "mana_flow", StatBlock.of(COST_REDUCTION, 10, MAX_RESOURCE, 15))),
                    branch("mage_air", StatBlock.of(AIR_DAMAGE, 5), "storm_mind", StatBlock.of(AIR_DAMAGE, 15, COOLDOWN_REDUCTION, 5))),
    };

    /** Ключевые камни бывших мостов: отросток i принадлежит классу сектора i + 1. */
    private static final Keystone[] BRIDGE_KEYSTONES = {
            Keystone.BATTLE_TRANCE,  // воин — разбойник
            Keystone.GLASS_CANNON,   // разбойник — лучник
            Keystone.VITAL_SURGE,    // лучник — охотник
            Keystone.ARCANE_SHIELD,  // охотник — маг
            Keystone.IRON_WILL,      // маг — воин
    };

    /**
     * Числа вместо процентов: каждый процентный бонус узла превращается в числовую прибавку (FLAT_*),
     * величина которой зависит от глубины узла: малые узлы ветви 0.3, 0.5, 0.7, 0.85, значимый узел и всё дальше — 1,
     * старт 0.5, узлы основы у старта 0.3. Числовые бонусы (здоровье, броня, ресурс, восстановление) не меняются.
     */
    private static final Map<com.heroicpaths.rpg.stat.Stat, com.heroicpaths.rpg.stat.Stat> NUMERIC = Map.ofEntries(
            Map.entry(MELEE_DAMAGE, FLAT_MELEE_DAMAGE), Map.entry(SWORD_DAMAGE, FLAT_SWORD_DAMAGE), Map.entry(AXE_DAMAGE, FLAT_AXE_DAMAGE),
            Map.entry(BOW_DAMAGE, FLAT_BOW_DAMAGE), Map.entry(SPELL_DAMAGE, FLAT_SPELL_DAMAGE), Map.entry(ABILITY_DAMAGE, FLAT_ABILITY_DAMAGE),
            Map.entry(FIRE_DAMAGE, FLAT_FIRE_DAMAGE), Map.entry(WATER_DAMAGE, FLAT_WATER_DAMAGE), Map.entry(EARTH_DAMAGE, FLAT_EARTH_DAMAGE),
            Map.entry(AIR_DAMAGE, FLAT_AIR_DAMAGE), Map.entry(POISON_DAMAGE, FLAT_POISON_DAMAGE), Map.entry(MINION_DAMAGE, FLAT_MINION_DAMAGE),
            Map.entry(MINION_HEALTH, FLAT_MINION_HEALTH), Map.entry(DUAL_WIELD_DAMAGE, FLAT_DUAL_WIELD_DAMAGE), Map.entry(CRIT_DAMAGE, FLAT_CRIT_DAMAGE),
            Map.entry(LIFE_STEAL, FLAT_LIFE_STEAL), Map.entry(DAMAGE_REDUCTION, FLAT_DAMAGE_REDUCTION), Map.entry(HEALING, FLAT_HEALING),
            Map.entry(COST_REDUCTION, FLAT_COST_REDUCTION), Map.entry(ARMOR_PIERCE, FLAT_ARMOR_PIERCE), Map.entry(MAX_HEALTH_PCT, MAX_HEALTH),
            Map.entry(CRIT_CHANCE, FLAT_CRIT_CHANCE), Map.entry(DODGE_CHANCE, FLAT_DODGE_CHANCE), Map.entry(BLEED_CHANCE, FLAT_BLEED_CHANCE),
            Map.entry(STUN_CHANCE, FLAT_STUN_CHANCE), Map.entry(ATTACK_SPEED, FLAT_ATTACK_SPEED), Map.entry(MOVE_SPEED, FLAT_MOVE_SPEED),
            Map.entry(COOLDOWN_REDUCTION, FLAT_COOLDOWN_REDUCTION));
    /** Сила малых узлов ветви по порядку от старта. */
    private static final float[] SMALL_DEPTH = {0.3f, 0.5f, 0.7f, 0.85f};

    /** Процентные бонусы блока — числом depth (0.3–1), числовые — как есть. */
    static StatBlock numeric(StatBlock block, float depth) {
        StatBlock result = new StatBlock();
        for (Map.Entry<com.heroicpaths.rpg.stat.Stat, Float> entry : block.asMap().entrySet()) {
            com.heroicpaths.rpg.stat.Stat flat = entry.getKey().isPercent() ? NUMERIC.get(entry.getKey()) : null;
            if (flat != null) result.add(flat, depth);
            else result.add(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static final double SECTOR_WIDTH = 72;
    private static final double BRANCH_SPACING = 11;
    private static final int CENTER_NODES = 15;

    static {
        build();
    }

    private PassiveTree() {}

    public static List<PassiveNode> nodes() {
        return Collections.unmodifiableList(NODES);
    }

    public static List<int[]> edges() {
        return Collections.unmodifiableList(EDGES);
    }

    @Nullable
    public static PassiveNode node(int id) {
        return id >= 0 && id < NODES.size() ? NODES.get(id) : null;
    }

    public static IntList neighbours(int id) {
        return NEIGHBOURS.getOrDefault(id, IntList.of());
    }

    public static int startNode(PlayerClass playerClass) {
        return STARTS.getOrDefault(playerClass, -1);
    }

    public static boolean isAllocated(IntSet allocated, PlayerClass playerClass, int id) {
        return id == startNode(playerClass) || allocated.contains(id);
    }

    /** Узел можно изучить, если он из дерева своего класса и соседствует с уже изученным (или со стартом). */
    public static boolean canAllocate(IntSet allocated, PlayerClass playerClass, int id) {
        PassiveNode node = node(id);
        if (playerClass == PlayerClass.NONE || node == null || node.region() != playerClass || isAllocated(allocated, playerClass, id)) return false;
        for (int neighbour : neighbours(id)) {
            if (isAllocated(allocated, playerClass, neighbour)) return true;
        }
        return false;
    }

    /** Узел можно забыть, если остальные изученные узлы останутся связаны со стартом. */
    public static boolean canRefund(IntSet allocated, PlayerClass playerClass, int id) {
        if (!allocated.contains(id)) return false;
        IntSet remaining = new IntOpenHashSet(allocated);
        remaining.remove(id);
        IntSet reached = new IntOpenHashSet();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        int start = startNode(playerClass);
        queue.add(start);
        reached.add(start);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int next : neighbours(current)) {
                if (remaining.contains(next) && reached.add(next)) queue.add(next);
            }
        }
        return reached.containsAll(remaining);
    }

    /** Изученные узлы, которые классу можно держать: из его дерева и связанные со стартом. */
    public static IntSet validAllocation(IntSet allocated, PlayerClass playerClass) {
        IntSet reached = new IntOpenHashSet();
        int start = startNode(playerClass);
        if (start < 0) return reached;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        IntSet seen = new IntOpenHashSet();
        seen.add(start);
        while (!queue.isEmpty()) {
            int current = queue.poll();
            for (int next : neighbours(current)) {
                PassiveNode node = node(next);
                if (allocated.contains(next) && node != null && node.region() == playerClass && seen.add(next)) {
                    reached.add(next);
                    queue.add(next);
                }
            }
        }
        return reached;
    }

    /** Складывает характеристики старта класса и всех изученных узлов. */
    public static void collectStats(IntSet allocated, PlayerClass playerClass, StatBlock into) {
        PassiveNode start = node(startNode(playerClass));
        if (start != null) into.addAll(start.stats());
        for (int id : allocated) {
            PassiveNode node = node(id);
            if (node != null) into.addAll(node.stats());
        }
    }

    public static Set<Keystone> keystones(IntSet allocated) {
        Set<Keystone> result = EnumSet.noneOf(Keystone.class);
        for (int id : allocated) {
            PassiveNode node = node(id);
            if (node != null && node.keystone() != null) result.add(node.keystone());
        }
        return result;
    }

    // ---------------------------------------------------------------- построение

    private static void build() {
        int[] ring = new int[CENTER_NODES];
        double ringStep = 360.0 / CENTER_NODES;
        // Узлы основы: три узла кольца у старта класса принадлежат этому классу.
        PlayerClass[] ringOwner = new PlayerClass[CENTER_NODES];
        for (Sector sector : SECTORS) {
            int index = Math.floorMod((int) Math.round((sector.angle + 90) / ringStep), CENTER_NODES);
            for (int d = -1; d <= 1; d++) ringOwner[Math.floorMod(index + d, CENTER_NODES)] = sector.playerClass;
        }
        for (int i = 0; i < CENTER_NODES; i++) {
            CenterNode center = CENTER[i % CENTER.length];
            PlayerClass owner = ringOwner[i] == null ? PlayerClass.NONE : ringOwner[i];
            ring[i] = add(NodeType.SMALL, owner, "tree.heroicpaths." + center.id, numeric(center.stats, 0.3f), null, 85, -90 + i * ringStep);
        }
        // Кольцо разрезано между классами: связаны только соседние узлы основы одного класса.
        for (int i = 0; i < CENTER_NODES; i++) {
            int next = (i + 1) % CENTER_NODES;
            if (ringOwner[i] == ringOwner[next]) link(ring[i], ring[next]);
        }

        int[][] edgeNotables = new int[SECTORS.length][];
        for (int s = 0; s < SECTORS.length; s++) {
            Sector sector = SECTORS[s];
            PlayerClass playerClass = sector.playerClass;
            int start = add(NodeType.START, playerClass, "tree.heroicpaths.start." + playerClass.id(), numeric(sector.startStats, 0.5f), null, 150, sector.angle);
            STARTS.put(playerClass, start);
            int ringIndex = Math.floorMod((int) Math.round((sector.angle + 90) / ringStep), CENTER_NODES);
            link(start, ring[ringIndex]);
            link(start, ring[(ringIndex + 1) % CENTER_NODES]);
            link(start, ring[(ringIndex + CENTER_NODES - 1) % CENTER_NODES]);

            int count = sector.branches.length;
            int[][] chains = new int[count][4];
            int[] notables = new int[count];
            for (int b = 0; b < count; b++) {
                Branch branch = sector.branches[b];
                double angle = sector.angle + (b - (count - 1) / 2.0) * BRANCH_SPACING;
                String smallName = "tree.heroicpaths." + branch.id;
                int previous = start;
                for (int i = 0; i < 4; i++) {
                    int id = add(NodeType.SMALL, playerClass, smallName, numeric(branch.small, SMALL_DEPTH[i]), null, 205 + 45 * i, angle);
                    chains[b][i] = id;
                    link(previous, id);
                    previous = id;
                }
                int notable = add(NodeType.NOTABLE, playerClass, "tree.heroicpaths.notable." + branch.notableId, numeric(branch.notable, 1f), null, 395, angle);
                notables[b] = notable;
                link(previous, notable);

                if (branch.extension instanceof KeystonePath path) {
                    int first = add(NodeType.SMALL, playerClass, smallName, numeric(path.first, 1f), null, 450, angle);
                    int second = add(NodeType.SMALL, playerClass, smallName, numeric(path.second, 1f), null, 495, angle);
                    int keystone = add(NodeType.KEYSTONE, playerClass, "", StatBlock.EMPTY, path.keystone, 555, angle);
                    link(notable, first);
                    link(first, second);
                    link(second, keystone);
                } else if (branch.extension instanceof SecondNotable second) {
                    int small = add(NodeType.SMALL, playerClass, smallName, numeric(second.small, 1f), null, 450, angle);
                    int notable2 = add(NodeType.NOTABLE, playerClass, "tree.heroicpaths.notable." + second.notableId, numeric(second.stats, 1f), null, 505, angle);
                    link(notable, small);
                    link(small, notable2);
                }
            }
            // Поперечные связи превращают веер в паутину.
            for (int b = 0; b < count - 1; b++) link(chains[b][1], chains[b + 1][1]);
            for (int b = 0; b < count - 1; b += 2) link(chains[b][3], chains[b + 1][3]);
            edgeNotables[s] = new int[]{notables[0], notables[count - 1]};
        }

        for (int s = 0; s < SECTORS.length; s++) {
            int next = (s + 1) % SECTORS.length;
            bridge(SECTORS[next].playerClass, edgeNotables[next][0], SECTORS[s].angle + SECTOR_WIDTH / 2, BRIDGE_KEYSTONES[s]);
        }
    }

    /**
     * Бывший мост между секторами — теперь отросток дерева следующего класса от его первой ветви:
     * три узла и ключевой камень снаружи. С деревом предыдущего класса он не связан.
     */
    private static void bridge(PlayerClass owner, int to, double middleAngle, Keystone keystone) {
        StatBlock stats = numeric(BRIDGE, 1f);
        int first = add(NodeType.SMALL, owner, "tree.heroicpaths.bridge", stats, null, 440, middleAngle - 7);
        int middle = add(NodeType.SMALL, owner, "tree.heroicpaths.bridge", stats, null, 445, middleAngle);
        int last = add(NodeType.SMALL, owner, "tree.heroicpaths.bridge", stats, null, 440, middleAngle + 7);
        int key = add(NodeType.KEYSTONE, owner, "", StatBlock.EMPTY, keystone, 515, middleAngle);
        link(to, last);
        link(last, middle);
        link(middle, first);
        link(middle, key);
    }

    private static int add(NodeType type, PlayerClass region, String nameKey, StatBlock stats, @Nullable Keystone keystone,
                           double radius, double angleDegrees) {
        double angle = Math.toRadians(angleDegrees);
        int id = NODES.size();
        NODES.add(new PassiveNode(id, type, region, nameKey, stats, keystone,
                (float) (radius * Math.cos(angle)), (float) (radius * Math.sin(angle))));
        return id;
    }

    private static void link(int a, int b) {
        EDGES.add(new int[]{a, b});
        NEIGHBOURS.computeIfAbsent(a, k -> new IntArrayList()).add(b);
        NEIGHBOURS.computeIfAbsent(b, k -> new IntArrayList()).add(a);
    }
}
