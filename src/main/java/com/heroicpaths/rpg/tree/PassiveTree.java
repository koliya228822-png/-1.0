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
 * из 6 ветвей (8 малых узлов → значимый узел); у большинства ветвей есть продолжение — путь к ключевому камню
 * класса или второй значимый узел. От первой ветви отходит отросток со вторым ключевым камнем класса.
 * Дерево разбито на кольца, которые открываются на 5, 10, 15, 20, 25 и 30 уровнях; значимые узлы и ключевые
 * камни требуют ещё и вложенных в дерево очков, чтобы к вершинам нельзя было пройти кратчайшим путём.
 */
public final class PassiveTree {
    /** Версия раскладки. При смене раскладки сохранённые узлы сбрасываются (см. RpgData). */
    public static final int LAYOUT_VERSION = 3;

    /** Уровни, на которых открываются кольца дерева, и внешние радиусы колец. */
    public static final int[] SEGMENT_LEVELS = {1, 5, 10, 15, 20, 25, 30};
    public static final float[] SEGMENT_RADII = {265, 325, 385, 465, 555, 640, Float.MAX_VALUE};
    /** Сколько очков нужно вложить в дерево, чтобы взять значимый узел, второй значимый узел и ключевой камень. */
    public static final int NOTABLE_POINTS = 8, DEEP_NOTABLE_POINTS = 15, KEYSTONE_POINTS = 25;
    /** Ключевые камни открываются только с этого уровня. */
    public static final int KEYSTONE_LEVEL = 30;

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

    /** Узлы основы. Узлов здоровья в дереве нет: здоровье растёт с уровнем персонажа. */
    private static final CenterNode[] CENTER = {
            new CenterNode("center_vitality", StatBlock.of(DAMAGE_REDUCTION, 1)),
            new CenterNode("center_power", StatBlock.of(ABILITY_DAMAGE, 4)),
            new CenterNode("center_spirit", StatBlock.of(MAX_RESOURCE, 6)),
            new CenterNode("center_swiftness", StatBlock.of(MOVE_SPEED, 2)),
            new CenterNode("center_focus", StatBlock.of(RESOURCE_REGEN, 0.5f)),
    };

    private static final StatBlock BRIDGE = StatBlock.of(ABILITY_DAMAGE, 3, DAMAGE_REDUCTION, 1);

    private static Branch branch(String id, StatBlock small, String notableId, StatBlock notable) {
        return new Branch(id, small, notableId, notable, null);
    }

    private static Branch branch(String id, StatBlock small, String notableId, StatBlock notable, Extension extension) {
        return new Branch(id, small, notableId, notable, extension);
    }

    /**
     * Ветви идут по часовой стрелке. В дереве нет здоровья, вампиризма и брони: вместо брони — процент снижения урона,
     * сила которого зависит от класса (PlayerClass.reductionFactor).
     */
    private static final Sector[] SECTORS = {
            new Sector(PlayerClass.WARRIOR, -90, StatBlock.of(MELEE_DAMAGE, 4, DAMAGE_REDUCTION, 2),
                    branch("warrior_armor", StatBlock.of(DAMAGE_REDUCTION, 1), "steel_skin", StatBlock.of(DAMAGE_REDUCTION, 4, THORNS, 1)),
                    branch("warrior_life", StatBlock.of(HEALTH_REGEN, 0.2f), "unbreakable", StatBlock.of(HEALTH_REGEN, 0.6f, DAMAGE_REDUCTION, 2),
                            new SecondNotable(StatBlock.of(HEALTH_REGEN, 0.2f), "colossus", StatBlock.of(DAMAGE_REDUCTION, 4, THORNS, 2))),
                    branch("warrior_sword", StatBlock.of(SWORD_DAMAGE, 4), "blade_master", StatBlock.of(SWORD_DAMAGE, 12, BLEED_CHANCE, 8),
                            new KeystonePath(StatBlock.of(SWORD_DAMAGE, 4), StatBlock.of(MELEE_DAMAGE, 4), Keystone.BLOOD_RAGE)),
                    branch("warrior_crush", StatBlock.of(STUN_CHANCE, 2), "skullcrusher", StatBlock.of(STUN_CHANCE, 6, MELEE_DAMAGE, 8)),
                    branch("warrior_leech", StatBlock.of(MAX_RESOURCE, 4), "bloodthirst", StatBlock.of(RESOURCE_REGEN, 1, MELEE_DAMAGE, 5),
                            new SecondNotable(StatBlock.of(RESOURCE_REGEN, 0.3f), "insatiable", StatBlock.of(RESOURCE_REGEN, 1, ATTACK_SPEED, 5))),
                    branch("warrior_axe", StatBlock.of(AXE_DAMAGE, 4), "headsman", StatBlock.of(AXE_DAMAGE, 12, CRIT_DAMAGE, 15),
                            new KeystonePath(StatBlock.of(AXE_DAMAGE, 4), StatBlock.of(DAMAGE_REDUCTION, 1), Keystone.SECOND_WIND))),
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
                    branch("rogue_poison", StatBlock.of(POISON_DAMAGE, 5), "venomancer", StatBlock.of(POISON_DAMAGE, 15, ABILITY_DAMAGE, 5),
                            new KeystonePath(StatBlock.of(POISON_DAMAGE, 5), StatBlock.of(CRIT_CHANCE, 1.5f), Keystone.OPPORTUNIST))),
            new Sector(PlayerClass.ARCHER, 54, StatBlock.of(MOVE_SPEED, 3),
                    branch("archer_energy", StatBlock.of(MAX_RESOURCE, 6), "tireless", StatBlock.of(RESOURCE_REGEN, 2, MAX_RESOURCE, 10)),
                    branch("archer_agility", StatBlock.of(MOVE_SPEED, 2), "tailwind", StatBlock.of(MOVE_SPEED, 5, BOW_DAMAGE, 5),
                            new SecondNotable(StatBlock.of(MOVE_SPEED, 2), "phantom_step", StatBlock.of(MOVE_SPEED, 6, DODGE_CHANCE, 4))),
                    branch("archer_precision", StatBlock.of(CRIT_CHANCE, 2), "assassin", StatBlock.of(CRIT_CHANCE, 5, CRIT_DAMAGE, 20),
                            new KeystonePath(StatBlock.of(CRIT_CHANCE, 2), StatBlock.of(BOW_DAMAGE, 4), Keystone.COLD_PRECISION)),
                    branch("archer_aim", StatBlock.of(BOW_DAMAGE, 4), "sniper_eye", StatBlock.of(BOW_DAMAGE, 12, ARMOR_PIERCE, 8)),
                    branch("archer_survival", StatBlock.of(HEALTH_REGEN, 0.2f), "pathfinder", StatBlock.of(HEALTH_REGEN, 0.6f, DAMAGE_REDUCTION, 2, MOVE_SPEED, 3),
                            new SecondNotable(StatBlock.of(HEALTH_REGEN, 0.2f), "monster_hunter", StatBlock.of(BOW_DAMAGE, 8, CRIT_CHANCE, 3))),
                    branch("archer_tactics", StatBlock.of(ABILITY_DAMAGE, 4), "tactician", StatBlock.of(ABILITY_DAMAGE, 10, COOLDOWN_REDUCTION, 5),
                            new KeystonePath(StatBlock.of(BOW_DAMAGE, 4), StatBlock.of(ABILITY_DAMAGE, 4), Keystone.STEADY_HANDS))),
            new Sector(PlayerClass.HUNTER, 126, StatBlock.of(MINION_DAMAGE, 5, MINION_HEALTH, 5),
                    branch("hunter_trapper", StatBlock.of(POISON_DAMAGE, 5), "trapper", StatBlock.of(POISON_DAMAGE, 12, ABILITY_DAMAGE, 8)),
                    branch("hunter_hide", StatBlock.of(MINION_HEALTH, 5), "thick_hide", StatBlock.of(MINION_HEALTH, 15, DAMAGE_REDUCTION, 2),
                            new SecondNotable(StatBlock.of(MINION_HEALTH, 5), "iron_hide", StatBlock.of(MINION_HEALTH, 20, DAMAGE_REDUCTION, 4))),
                    branch("hunter_beasts", StatBlock.of(MINION_DAMAGE, 5), "alpha", StatBlock.of(MINION_DAMAGE, 15, MINION_HEALTH, 15),
                            new KeystonePath(StatBlock.of(MINION_DAMAGE, 5), StatBlock.of(MINION_HEALTH, 5), Keystone.PACK_BOND)),
                    branch("hunter_bow", StatBlock.of(BOW_DAMAGE, 3), "bowyer", StatBlock.of(BOW_DAMAGE, 10, CRIT_CHANCE, 4),
                            new SecondNotable(StatBlock.of(BOW_DAMAGE, 3), "beast_slayer", StatBlock.of(BOW_DAMAGE, 8, MINION_DAMAGE, 8))),
                    branch("hunter_tracking", StatBlock.of(MOVE_SPEED, 2), "pack_leader", StatBlock.of(MINION_HEALTH, 15, HEALTH_REGEN, 0.4f, MOVE_SPEED, 3)),
                    branch("hunter_survival", StatBlock.of(HEALTH_REGEN, 0.2f), "survivalist", StatBlock.of(HEALTH_REGEN, 0.8f, DAMAGE_REDUCTION, 2),
                            new KeystonePath(StatBlock.of(MINION_DAMAGE, 5), StatBlock.of(HEALTH_REGEN, 0.2f), Keystone.APEX_PREDATOR))),
            new Sector(PlayerClass.MAGE, 198, StatBlock.of(MAX_RESOURCE, 10),
                    branch("mage_earth", StatBlock.of(EARTH_DAMAGE, 5), "stone_heart", StatBlock.of(EARTH_DAMAGE, 15, DAMAGE_REDUCTION, 2)),
                    branch("mage_fire", StatBlock.of(FIRE_DAMAGE, 5), "pyromaniac", StatBlock.of(FIRE_DAMAGE, 15, SPELL_DAMAGE, 4),
                            new SecondNotable(StatBlock.of(FIRE_DAMAGE, 5), "heart_of_flame", StatBlock.of(FIRE_DAMAGE, 20, CRIT_CHANCE, 3))),
                    branch("mage_power", StatBlock.of(SPELL_DAMAGE, 4), "archmage", StatBlock.of(SPELL_DAMAGE, 12, COOLDOWN_REDUCTION, 5),
                            new KeystonePath(StatBlock.of(SPELL_DAMAGE, 4), StatBlock.of(SPELL_DAMAGE, 4), Keystone.ELEMENTAL_HARMONY)),
                    branch("mage_water", StatBlock.of(WATER_DAMAGE, 5), "tidecaller", StatBlock.of(WATER_DAMAGE, 15, HEALING, 10)),
                    branch("mage_meditation", StatBlock.of(RESOURCE_REGEN, 0.5f), "enlightenment", StatBlock.of(RESOURCE_REGEN, 2, COST_REDUCTION, 5),
                            new SecondNotable(StatBlock.of(RESOURCE_REGEN, 0.5f), "mana_flow", StatBlock.of(COST_REDUCTION, 10, MAX_RESOURCE, 15))),
                    branch("mage_air", StatBlock.of(AIR_DAMAGE, 5), "storm_mind", StatBlock.of(AIR_DAMAGE, 15, COOLDOWN_REDUCTION, 5),
                            new KeystonePath(StatBlock.of(AIR_DAMAGE, 5), StatBlock.of(SPELL_DAMAGE, 4), Keystone.OVERCHANNEL))),
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
     * величина которой зависит от глубины узла: восемь малых узлов ветви 0.15–0.5, значимый узел и всё дальше — 1,
     * старт 0.5, узлы основы 0.3. Исключение — снижение урона: оно остаётся процентом и умножается на силу класса.
     * Числовые бонусы (восстановление, ресурс, шипы) у малых узлов вдвое слабее записанных.
     */
    private static final Map<com.heroicpaths.rpg.stat.Stat, com.heroicpaths.rpg.stat.Stat> NUMERIC = Map.ofEntries(
            Map.entry(MELEE_DAMAGE, FLAT_MELEE_DAMAGE), Map.entry(SWORD_DAMAGE, FLAT_SWORD_DAMAGE), Map.entry(AXE_DAMAGE, FLAT_AXE_DAMAGE),
            Map.entry(BOW_DAMAGE, FLAT_BOW_DAMAGE), Map.entry(SPELL_DAMAGE, FLAT_SPELL_DAMAGE), Map.entry(ABILITY_DAMAGE, FLAT_ABILITY_DAMAGE),
            Map.entry(FIRE_DAMAGE, FLAT_FIRE_DAMAGE), Map.entry(WATER_DAMAGE, FLAT_WATER_DAMAGE), Map.entry(EARTH_DAMAGE, FLAT_EARTH_DAMAGE),
            Map.entry(AIR_DAMAGE, FLAT_AIR_DAMAGE), Map.entry(POISON_DAMAGE, FLAT_POISON_DAMAGE), Map.entry(MINION_DAMAGE, FLAT_MINION_DAMAGE),
            Map.entry(MINION_HEALTH, FLAT_MINION_HEALTH), Map.entry(DUAL_WIELD_DAMAGE, FLAT_DUAL_WIELD_DAMAGE), Map.entry(CRIT_DAMAGE, FLAT_CRIT_DAMAGE),
            Map.entry(HEALING, FLAT_HEALING),
            Map.entry(COST_REDUCTION, FLAT_COST_REDUCTION), Map.entry(ARMOR_PIERCE, FLAT_ARMOR_PIERCE),
            Map.entry(CRIT_CHANCE, FLAT_CRIT_CHANCE), Map.entry(DODGE_CHANCE, FLAT_DODGE_CHANCE), Map.entry(BLEED_CHANCE, FLAT_BLEED_CHANCE),
            Map.entry(STUN_CHANCE, FLAT_STUN_CHANCE), Map.entry(ATTACK_SPEED, FLAT_ATTACK_SPEED), Map.entry(MOVE_SPEED, FLAT_MOVE_SPEED),
            Map.entry(COOLDOWN_REDUCTION, FLAT_COOLDOWN_REDUCTION));
    /** Малых узлов в каждой ветви до значимого. */
    public static final int SMALL_PER_BRANCH = 8;
    /** Сила малых узлов ветви по порядку от старта. */
    private static final float[] SMALL_DEPTH = {0.15f, 0.2f, 0.25f, 0.3f, 0.35f, 0.4f, 0.45f, 0.5f};
    /** Множитель числовых бонусов и снижения урона: у малых узлов и узлов основы вдвое меньше. */
    private static final float SMALL_SCALE = 0.5f;

    /** Процентные бонусы блока — числом depth, снижение урона — процентом с силой класса, числовые — как есть × scale. */
    static StatBlock numeric(StatBlock block, float depth, float scale, PlayerClass owner) {
        StatBlock result = new StatBlock();
        for (Map.Entry<com.heroicpaths.rpg.stat.Stat, Float> entry : block.asMap().entrySet()) {
            com.heroicpaths.rpg.stat.Stat stat = entry.getKey();
            com.heroicpaths.rpg.stat.Stat flat = stat.isPercent() ? NUMERIC.get(stat) : null;
            if (flat != null) result.add(flat, depth);
            else if (stat == DAMAGE_REDUCTION) result.add(stat, Math.round(entry.getValue() * scale * owner.reductionFactor() * 100f) / 100f);
            else result.add(stat, Math.round(entry.getValue() * scale * 100f) / 100f);
        }
        return result;
    }

    /** Уровень, с которого открывается кольцо дерева на таком расстоянии от центра. */
    public static int levelForRadius(double radius) {
        for (int i = 0; i < SEGMENT_RADII.length; i++) if (radius <= SEGMENT_RADII[i]) return SEGMENT_LEVELS[i];
        return SEGMENT_LEVELS[SEGMENT_LEVELS.length - 1];
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

    /** Узел из дерева своего класса соседствует с уже изученным (или со стартом) — без учёта уровня и вложенных очков. */
    public static boolean canAllocate(IntSet allocated, PlayerClass playerClass, int id) {
        PassiveNode node = node(id);
        if (playerClass == PlayerClass.NONE || node == null || node.region() != playerClass || isAllocated(allocated, playerClass, id)) return false;
        for (int neighbour : neighbours(id)) {
            if (isAllocated(allocated, playerClass, neighbour)) return true;
        }
        return false;
    }

    /** Узел можно изучить: он соседний, его кольцо уже открыто уровнем и в дерево вложено достаточно очков. */
    public static boolean canAllocate(IntSet allocated, PlayerClass playerClass, int level, int id) {
        return canAllocate(allocated, playerClass, id) && lockReason(allocated, level, id) == null;
    }

    /** Почему узел закрыт уровнем или вложенными очками: ключ перевода с числом, или null. */
    @Nullable
    public static Lock lockReason(IntSet allocated, int level, int id) {
        PassiveNode node = node(id);
        if (node == null) return null;
        if (level < node.requiredLevel()) return new Lock("screen.heroicpaths.node_needs_level", node.requiredLevel());
        if (allocated.size() < node.requiredPoints()) return new Lock("screen.heroicpaths.node_needs_points", node.requiredPoints());
        return null;
    }

    public record Lock(String key, int value) {}

    /**
     * Узел можно забыть, если остальные изученные узлы останутся связаны со стартом и каждому из них
     * по-прежнему хватит вложенных очков.
     */
    public static boolean canRefund(IntSet allocated, PlayerClass playerClass, int id) {
        if (!allocated.contains(id)) return false;
        IntSet remaining = new IntOpenHashSet(allocated);
        remaining.remove(id);
        for (int other : remaining) {
            PassiveNode node = node(other);
            if (node != null && remaining.size() - 1 < node.requiredPoints()) return false;
        }
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
            ring[i] = add(NodeType.SMALL, owner, "tree.heroicpaths." + center.id, numeric(center.stats, 0.3f, SMALL_SCALE, owner), null, 85, -90 + i * ringStep);
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
            int start = add(NodeType.START, playerClass, "tree.heroicpaths.start." + playerClass.id(), numeric(sector.startStats, 0.5f, 1f, playerClass), null, 150, sector.angle);
            STARTS.put(playerClass, start);
            int ringIndex = Math.floorMod((int) Math.round((sector.angle + 90) / ringStep), CENTER_NODES);
            link(start, ring[ringIndex]);
            link(start, ring[(ringIndex + 1) % CENTER_NODES]);
            link(start, ring[(ringIndex + CENTER_NODES - 1) % CENTER_NODES]);

            int count = sector.branches.length;
            int[][] chains = new int[count][SMALL_PER_BRANCH];
            int[] notables = new int[count];
            for (int b = 0; b < count; b++) {
                Branch branch = sector.branches[b];
                double angle = sector.angle + (b - (count - 1) / 2.0) * BRANCH_SPACING;
                String smallName = "tree.heroicpaths." + branch.id;
                int previous = start;
                for (int i = 0; i < SMALL_PER_BRANCH; i++) {
                    int id = add(NodeType.SMALL, playerClass, smallName, numeric(branch.small, SMALL_DEPTH[i], SMALL_SCALE, playerClass), null, 200 + 30 * i, angle);
                    chains[b][i] = id;
                    link(previous, id);
                    previous = id;
                }
                int notable = add(NodeType.NOTABLE, playerClass, "tree.heroicpaths.notable." + branch.notableId, numeric(branch.notable, 1f, 1f, playerClass), null, 455, angle);
                notables[b] = notable;
                link(previous, notable);

                if (branch.extension instanceof KeystonePath path) {
                    // Путь к ключевому камню: четыре малых узла (два первых и два вторых бонуса) и камень.
                    int previousStep = notable;
                    for (int i = 0; i < 4; i++) {
                        StatBlock stats = numeric(i < 2 ? path.first : path.second, 0.5f, SMALL_SCALE, playerClass);
                        int step = add(NodeType.SMALL, playerClass, smallName, stats, null, 505 + 40 * i, angle);
                        link(previousStep, step);
                        previousStep = step;
                    }
                    int keystone = add(NodeType.KEYSTONE, playerClass, "", StatBlock.EMPTY, path.keystone, 690, angle);
                    link(previousStep, keystone);
                } else if (branch.extension instanceof SecondNotable second) {
                    int previousStep = notable;
                    for (int i = 0; i < 3; i++) {
                        int step = add(NodeType.SMALL, playerClass, smallName, numeric(second.small, 0.5f, SMALL_SCALE, playerClass), null, 505 + 40 * i, angle);
                        link(previousStep, step);
                        previousStep = step;
                    }
                    int notable2 = add(NodeType.NOTABLE, playerClass, "tree.heroicpaths.notable." + second.notableId,
                            numeric(second.stats, 1f, 1f, playerClass), null, 635, angle);
                    link(previousStep, notable2);
                }
            }
            // Поперечные связи превращают веер в паутину.
            for (int b = 0; b < count - 1; b++) link(chains[b][2], chains[b + 1][2]);
            for (int b = 0; b < count - 1; b += 2) link(chains[b][5], chains[b + 1][5]);
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
        StatBlock stats = numeric(BRIDGE, 1f, 1f, owner);
        int first = add(NodeType.SMALL, owner, "tree.heroicpaths.bridge", stats, null, 500, middleAngle - 6);
        int middle = add(NodeType.SMALL, owner, "tree.heroicpaths.bridge", stats, null, 510, middleAngle);
        int last = add(NodeType.SMALL, owner, "tree.heroicpaths.bridge", stats, null, 500, middleAngle + 6);
        int key = add(NodeType.KEYSTONE, owner, "", StatBlock.EMPTY, keystone, 590, middleAngle);
        link(to, last);
        link(last, middle);
        link(middle, first);
        link(middle, key);
    }

    private static int add(NodeType type, PlayerClass region, String nameKey, StatBlock stats, @Nullable Keystone keystone,
                           double radius, double angleDegrees) {
        double angle = Math.toRadians(angleDegrees);
        int id = NODES.size();
        // Кольцо дерева открывается уровнем; значимые узлы и ключевые камни ещё и вложенными очками.
        int level = type == NodeType.KEYSTONE ? KEYSTONE_LEVEL : levelForRadius(radius);
        int points = switch (type) {
            case KEYSTONE -> KEYSTONE_POINTS;
            case NOTABLE -> radius > 600 ? DEEP_NOTABLE_POINTS : NOTABLE_POINTS;
            default -> 0;
        };
        NODES.add(new PassiveNode(id, type, region, nameKey, stats, keystone,
                (float) (radius * Math.cos(angle)), (float) (radius * Math.sin(angle)), level, points));
        return id;
    }

    private static void link(int a, int b) {
        EDGES.add(new int[]{a, b});
        NEIGHBOURS.computeIfAbsent(a, k -> new IntArrayList()).add(b);
        NEIGHBOURS.computeIfAbsent(b, k -> new IntArrayList()).add(a);
    }
}
