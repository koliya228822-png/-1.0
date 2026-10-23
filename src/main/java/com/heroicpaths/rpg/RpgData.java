package com.heroicpaths.rpg;

import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import com.heroicpaths.rpg.stat.Keystone;
import com.heroicpaths.rpg.stat.StatBlock;
import com.heroicpaths.rpg.tree.PassiveTree;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * RPG-прогресс одного игрока: класс и подкласс, уровень, характеристики, пассивное дерево, выученные
 * по книгам умения и их ранги. Живёт как attachment на игроке и синхронизируется с клиентом.
 */
public class RpgData implements INBTSerializable<CompoundTag> {
    public static final int MAX_LEVEL = 100;
    /** Опыт с 1-го на 2-й уровень; каждый следующий уровень дороже предыдущего в XP_GROWTH раз. */
    public static final int XP_FIRST_LEVEL = 40;
    public static final double XP_GROWTH = 1.5;
    public static final int ABILITY_SLOTS = 4;
    /** Очки умений на старте. */
    public static final int STARTING_ABILITY_POINTS = 2;
    /** Очко умений даётся каждые 3 уровня. */
    public static final int LEVELS_PER_ABILITY_POINT = 3;
    /**
     * 4 — пассивное дерево перестроено под пять классов: узлы сохранений версий 2–3 не переносятся,
     * очки дерева возвращаются игроку.
     */
    private static final int DATA_VERSION = 4;
    /** Первая версия данных с нынешней раскладкой дерева. */
    private static final int TREE_LAYOUT_SINCE = 4;
    /** С версии 3 есть подклассы, характеристики, книги умений и ранги. */
    private static final int SUBCLASSES_SINCE = 3;

    private PlayerClass playerClass = PlayerClass.NONE;
    @Nullable
    private Subclass subclass;
    private int level = 1;
    private int xp;
    private float resource = 100f;
    private final int[] coreStats = new int[CoreStat.values().length];
    private final IntSet allocatedNodes = new IntOpenHashSet();
    /** Умения, выученные по книгам. Умения подкласса и класса известны и без этого. */
    private final Set<String> learned = new LinkedHashSet<>();
    /** Ранги умений выше первого. */
    private final Map<String, Integer> ranks = new HashMap<>();
    private final String[] slots = new String[ABILITY_SLOTS];
    private final Map<String, Long> cooldowns = new HashMap<>();
    /** Активный контракт гильдии: данж и размер группы. Пустая строка — контракта нет. */
    private String contractDungeon = "";
    private int contractParty;
    /** Взятые заказы гильдии искателей приключений (не больше трёх). */
    private final List<com.heroicpaths.guild.Order> orders = new java.util.ArrayList<>();
    /** Заказы досок, уже взятые сегодня: один заказ можно взять только раз. */
    private final Set<String> takenOrders = new LinkedHashSet<>();
    /** Ремёсла: кузнец, плотник, рунный мастер. Сброс класса их не трогает. */
    private final com.heroicpaths.craft.Professions professions = new com.heroicpaths.craft.Professions();
    /** Зверь охотника, если он призван. */
    @Nullable
    private UUID companion;
    /** До какого игрового времени погибший зверь не может вернуться. */
    private long companionDownUntil;
    /** Когда выносливость тратилась в последний раз: восстановление начинается через секунду (см. Stamina). */
    private long staminaUsedAt;
    /** Тик последнего удара или выстрела, на который не хватило выносливости. */
    private long tiredAt = -1;
    /** Растёт при каждой синхронизации, чтобы открытое меню знало, что пора обновиться. */
    private int syncVersion;
    /** Дерево сброшено при загрузке старого сохранения: игроку нужно об этом сказать (не сохраняется). */
    private boolean treeWasReset;

    @Nullable
    private StatBlock cachedStats;
    @Nullable
    private Set<Keystone> cachedKeystones;

    public RpgData() {
        Arrays.fill(coreStats, CoreStat.BASE);
    }

    /** Опыт до следующего уровня: 40 на первом, каждый следующий уровень требует в 1,5 раза больше предыдущего. */
    public static int xpToNextLevel(int level) {
        double xp = XP_FIRST_LEVEL * Math.pow(XP_GROWTH, Math.max(0, level - 1));
        return xp >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.round(xp);
    }

    public PlayerClass getPlayerClass() {
        return playerClass;
    }

    public void setPlayerClass(PlayerClass playerClass) {
        this.playerClass = playerClass;
        invalidateStats();
    }

    @Nullable
    public Subclass getSubclass() {
        return subclass;
    }

    public void setSubclass(@Nullable Subclass subclass) {
        this.subclass = subclass;
        invalidateStats();
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }

    public float getResource() {
        return resource;
    }

    public void setResource(float resource) {
        this.resource = resource;
    }

    public long getStaminaUsedAt() {
        return staminaUsedAt;
    }

    public void setStaminaUsedAt(long gameTime) {
        this.staminaUsedAt = gameTime;
    }

    public long getTiredAt() {
        return tiredAt;
    }

    public void setTiredAt(long gameTime) {
        this.tiredAt = gameTime;
    }

    public int getSyncVersion() {
        return syncVersion;
    }

    // ---------------------------------------------------------------- характеристики

    public int getStat(CoreStat stat) {
        return coreStats[stat.ordinal()];
    }

    public void addStat(CoreStat stat) {
        coreStats[stat.ordinal()]++;
        invalidateStats();
    }

    /** Свободные очки характеристик: по одному за каждый уровень после первого. */
    public int statPoints() {
        int spent = 0;
        for (int value : coreStats) spent += value - CoreStat.BASE;
        return level - 1 - spent;
    }

    // ---------------------------------------------------------------- дерево

    public IntSet getAllocatedNodes() {
        return IntSets.unmodifiable(allocatedNodes);
    }

    public void allocateNode(int id) {
        allocatedNodes.add(id);
        invalidateStats();
    }

    public void refundNode(int id) {
        allocatedNodes.remove(id);
        invalidateStats();
    }

    /** Оставить из изученных узлов только keep; возвращает, сколько узлов снято (их очки вернулись). */
    public int retainNodes(IntSet keep) {
        int before = allocatedNodes.size();
        allocatedNodes.retainAll(keep);
        if (allocatedNodes.size() != before) invalidateStats();
        return before - allocatedNodes.size();
    }

    /** Свободные очки дерева: одно за каждый уровень. */
    /** true один раз после загрузки сохранения со старым деревом. */
    public boolean consumeTreeReset() {
        boolean reset = treeWasReset;
        treeWasReset = false;
        return reset;
    }

    public int treePoints() {
        return level - allocatedNodes.size();
    }

    // ---------------------------------------------------------------- умения

    /** Умение известно: оно стартовое для подкласса или выучено по книге. */
    public boolean knows(String abilityId) {
        return (subclass != null && subclass.startingAbilities().contains(abilityId)) || learned.contains(abilityId);
    }

    public Set<String> getLearned() {
        return Collections.unmodifiableSet(learned);
    }

    public void learn(String abilityId) {
        learned.add(abilityId);
    }

    public int getRank(String abilityId) {
        return ranks.getOrDefault(abilityId, 1);
    }

    public void setRank(String abilityId, int rank) {
        if (rank <= 1) ranks.remove(abilityId);
        else ranks.put(abilityId, Math.min(AbilityDef.MAX_RANK, rank));
    }

    /** Очки умений: 2 на старте и по одному каждые 3 уровня. Тратятся на книги и на ранги. */
    public int abilityPointsTotal() {
        return STARTING_ABILITY_POINTS + level / LEVELS_PER_ABILITY_POINT;
    }

    public int abilityPoints() {
        int spent = learned.size();
        for (int rank : ranks.values()) spent += rank - 1;
        return abilityPointsTotal() - spent;
    }

    @Nullable
    public String getSlot(int slot) {
        return slot >= 0 && slot < ABILITY_SLOTS ? slots[slot] : null;
    }

    public void setSlot(int slot, @Nullable String abilityId) {
        if (slot >= 0 && slot < ABILITY_SLOTS) slots[slot] = abilityId;
    }

    public long getCooldownEnd(String abilityId) {
        return cooldowns.getOrDefault(abilityId, 0L);
    }

    public void setCooldownEnd(String abilityId, long gameTime) {
        cooldowns.put(abilityId, gameTime);
    }

    // ---------------------------------------------------------------- ремёсла

    public com.heroicpaths.craft.Professions professions() {
        return professions;
    }

    // ---------------------------------------------------------------- зверь охотника

    @Nullable
    public UUID getCompanion() {
        return companion;
    }

    public void setCompanion(@Nullable UUID companion) {
        this.companion = companion;
    }

    public long getCompanionDownUntil() {
        return companionDownUntil;
    }

    public void setCompanionDownUntil(long gameTime) {
        this.companionDownUntil = gameTime;
    }

    // ---------------------------------------------------------------- контракт

    public String getContractDungeon() {
        return contractDungeon;
    }

    public int getContractParty() {
        return contractParty;
    }

    public void setContract(String dungeon, int party) {
        contractDungeon = dungeon;
        contractParty = party;
    }

    public void clearContract() {
        setContract("", 0);
    }

    // ---------------------------------------------------------------- заказы гильдии

    public List<com.heroicpaths.guild.Order> getOrders() {
        return java.util.Collections.unmodifiableList(orders);
    }

    public void addOrder(com.heroicpaths.guild.Order order) {
        orders.add(order);
        syncVersion++;
    }

    public void setOrder(int index, com.heroicpaths.guild.Order order) {
        orders.set(index, order);
        syncVersion++;
    }

    public void removeOrder(int index) {
        orders.remove(index);
        syncVersion++;
    }

    public boolean hasTakenOrder(String id) {
        return takenOrders.contains(id);
    }

    /** Отмечает заказ взятым; отметки прошлых дней забываются. */
    public void markOrderTaken(String id, String todayPrefix) {
        takenOrders.removeIf(taken -> !taken.startsWith(todayPrefix));
        takenOrders.add(id);
    }

    // ---------------------------------------------------------------- кэш характеристик

    /** Кэш характеристик сбрасывается при изменении дерева, характеристик, подкласса или снаряжения. */
    public void invalidateStats() {
        cachedStats = null;
        cachedKeystones = null;
    }

    @Nullable
    StatBlock cachedStats() {
        return cachedStats;
    }

    void cacheStats(StatBlock stats, Set<Keystone> keystones) {
        cachedStats = stats;
        cachedKeystones = keystones;
    }

    public Set<Keystone> keystones() {
        if (cachedKeystones == null) cachedKeystones = PassiveTree.keystones(allocatedNodes);
        return cachedKeystones;
    }

    // ---------------------------------------------------------------- сброс

    /** Забывает дерево, характеристики, книги, ранги и слоты. Класс, подкласс и уровень сохраняются. */
    public void resetProgress() {
        allocatedNodes.clear();
        Arrays.fill(coreStats, CoreStat.BASE);
        learned.clear();
        ranks.clear();
        cooldowns.clear();
        Arrays.fill(slots, null);
        companion = null;
        companionDownUntil = 0;
        invalidateStats();
    }

    public void resetClass() {
        resetProgress();
        playerClass = PlayerClass.NONE;
        subclass = null;
        resource = 100f;
    }

    // ---------------------------------------------------------------- NBT

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", DATA_VERSION);
        tag.putString("class", playerClass.id());
        if (subclass != null) tag.putString("subclass", subclass.id());
        tag.putInt("level", level);
        tag.putInt("xp", xp);
        tag.putFloat("resource", resource);
        tag.putIntArray("stats", coreStats.clone());
        tag.put("nodes", new IntArrayTag(allocatedNodes.toIntArray()));
        ListTag learnedTag = new ListTag();
        for (String id : learned) learnedTag.add(StringTag.valueOf(id));
        tag.put("learned", learnedTag);
        CompoundTag ranksTag = new CompoundTag();
        ranks.forEach(ranksTag::putInt);
        tag.put("ranks", ranksTag);
        ListTag slotsTag = new ListTag();
        for (String slot : slots) slotsTag.add(StringTag.valueOf(slot == null ? "" : slot));
        tag.put("slots", slotsTag);
        CompoundTag cooldownsTag = new CompoundTag();
        cooldowns.forEach(cooldownsTag::putLong);
        tag.put("cooldowns", cooldownsTag);
        tag.putString("contract", contractDungeon);
        tag.putInt("contractParty", contractParty);
        if (companion != null) tag.putUUID("companion", companion);
        tag.putLong("companionDown", companionDownUntil);
        ListTag ordersTag = new ListTag();
        for (com.heroicpaths.guild.Order order : orders) ordersTag.add(order.save(provider));
        tag.put("orders", ordersTag);
        ListTag takenTag = new ListTag();
        for (String id : takenOrders) takenTag.add(StringTag.valueOf(id));
        tag.put("takenOrders", takenTag);
        tag.put("crafts", professions.save());
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        int version = tag.getInt("v");
        playerClass = PlayerClass.byId(tag.getString("class"));
        level = Math.min(MAX_LEVEL, Math.max(1, tag.getInt("level")));
        xp = tag.getInt("xp");
        resource = tag.getFloat("resource");
        contractDungeon = tag.getString("contract");
        contractParty = tag.getInt("contractParty");
        allocatedNodes.clear();
        learned.clear();
        ranks.clear();
        Arrays.fill(slots, null);
        Arrays.fill(coreStats, CoreStat.BASE);
        cooldowns.clear();
        subclass = null;
        companion = tag.hasUUID("companion") ? tag.getUUID("companion") : null;
        companionDownUntil = tag.getLong("companionDown");
        orders.clear();
        for (Tag order : tag.getList("orders", Tag.TAG_COMPOUND)) {
            com.heroicpaths.guild.Order loaded = com.heroicpaths.guild.Order.load(provider, (CompoundTag) order);
            if (loaded != null) orders.add(loaded);
        }
        takenOrders.clear();
        for (Tag id : tag.getList("takenOrders", Tag.TAG_STRING)) takenOrders.add(id.getAsString());
        professions.load(tag.getCompound("crafts"));

        // Сохранения версии 1 (ранги навыков) и старые раскладки дерева (до версии 4) переносятся без узлов:
        // очки дерева возвращаются.
        if (version >= TREE_LAYOUT_SINCE) {
            for (int id : tag.getIntArray("nodes")) {
                if (PassiveTree.node(id) != null) allocatedNodes.add(id);
            }
        } else if (tag.getIntArray("nodes").length > 0) {
            treeWasReset = true;
        }
        if (version >= 2) {
            ListTag slotsTag = tag.getList("slots", Tag.TAG_STRING);
            for (int i = 0; i < Math.min(ABILITY_SLOTS, slotsTag.size()); i++) {
                String id = slotsTag.getString(i);
                slots[i] = id.isEmpty() ? null : id;
            }
        }
        if (version >= SUBCLASSES_SINCE) {
            subclass = Subclass.byId(tag.getString("subclass"));
            int[] stats = tag.getIntArray("stats");
            for (int i = 0; i < Math.min(stats.length, coreStats.length); i++) coreStats[i] = Math.max(CoreStat.BASE, stats[i]);
            for (Tag id : tag.getList("learned", Tag.TAG_STRING)) {
                if (AbilityRegistry.get(id.getAsString()) != null) learned.add(id.getAsString());
            }
            CompoundTag ranksTag = tag.getCompound("ranks");
            for (String key : ranksTag.getAllKeys()) setRank(key, ranksTag.getInt(key));
            CompoundTag cooldownsTag = tag.getCompound("cooldowns");
            for (String key : cooldownsTag.getAllKeys()) cooldowns.put(key, cooldownsTag.getLong(key));
        } else if (version == 2) {
            migrateFromSpecializations(tag.getString("spec"));
        }
        if (subclass != null && subclass.playerClass() != playerClass) subclass = null;
        for (int i = 0; i < ABILITY_SLOTS; i++) {
            if (slots[i] != null && !knows(slots[i])) slots[i] = null;
        }
        invalidateStats();
        syncVersion++;
    }

    /**
     * Сохранения версии 2: специализация становится подклассом, а умения, которые игрок уже открыл,
     * переходят в выученные книги (пока хватает очков умений).
     */
    private void migrateFromSpecializations(String spec) {
        subclass = Subclass.fromLegacySpec(spec);
        if (subclass == null) return;
        for (String id : legacyUnlocked(spec)) {
            AbilityDef def = AbilityRegistry.get(id);
            if (def == null || knows(id) || !subclass.learnableAbilities().contains(id) || abilityPoints() <= 0) continue;
            learned.add(id);
        }
    }

    private List<String> legacyUnlocked(String spec) {
        List<String> base = switch (playerClass) {
            case WARRIOR -> List.of("charge", "whirlwind", "war_cry");
            case ARCHER -> List.of("volley", "disengage", "arrow_rain");
            case MAGE -> List.of("fireball", "frost_nova", "blink");
            default -> List.of();
        };
        List<String> specAbilities = switch (spec) {
            case "paladin" -> List.of("holy_shield", "smite");
            case "guardian" -> List.of("taunt", "shield_slam");
            case "berserker" -> List.of("blood_frenzy", "execute");
            case "sniper" -> List.of("aimed_shot", "hunters_mark");
            case "ranger" -> List.of("poison_arrow", "bear_trap");
            case "stormcaller" -> List.of("rapid_fire", "thunder_arrow");
            case "pyromancer" -> List.of("meteor", "flame_wave");
            case "hydromancer" -> List.of("ice_lance", "healing_rain");
            case "geomancer" -> List.of("stone_skin", "earthquake");
            case "aeromancer" -> List.of("chain_lightning", "tornado");
            default -> List.of();
        };
        int[] baseLevels = {1, 5, 10};
        java.util.List<String> result = new java.util.ArrayList<>();
        for (int i = 0; i < base.size(); i++) if (level >= baseLevels[i]) result.add(base.get(i));
        if (specAbilities.size() == 2) {
            if (level >= 10) result.add(specAbilities.get(0));
            if (level >= 20) result.add(specAbilities.get(1));
        }
        return result;
    }
}
