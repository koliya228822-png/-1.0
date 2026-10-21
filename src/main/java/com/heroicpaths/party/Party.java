package com.heroicpaths.party;

import com.heroicpaths.dungeon.DungeonType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Группа игроков. Хранит состав, лидера, а также имена и последние известные уровни участников:
 * по ним считается средний уровень, пока участник не в сети.
 */
public final class Party {
    public static final int MAX_SIZE = DungeonType.MAX_PARTY;

    private final UUID id;
    private UUID leader;
    private final List<UUID> members = new ArrayList<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, Integer> levels = new HashMap<>();

    public Party(UUID id, UUID leader) {
        this.id = id;
        this.leader = leader;
    }

    public UUID id() {
        return id;
    }

    public UUID leader() {
        return leader;
    }

    public boolean isLeader(UUID player) {
        return leader.equals(player);
    }

    public List<UUID> members() {
        return Collections.unmodifiableList(members);
    }

    public int size() {
        return members.size();
    }

    public boolean isFull() {
        return members.size() >= MAX_SIZE;
    }

    public boolean contains(UUID player) {
        return members.contains(player);
    }

    public String name(UUID player) {
        return names.getOrDefault(player, "?");
    }

    /** Последний известный уровень участника; 1, если он неизвестен. */
    public int cachedLevel(UUID player) {
        return Math.max(1, levels.getOrDefault(player, 1));
    }

    void add(UUID player, String name, int level) {
        if (!members.contains(player)) members.add(player);
        remember(player, name, level);
    }

    void remember(UUID player, String name, int level) {
        names.put(player, name);
        levels.put(player, Math.max(1, level));
    }

    /** Убирает участника. Если ушёл лидер, лидером становится следующий по списку. */
    void remove(UUID player) {
        members.remove(player);
        names.remove(player);
        levels.remove(player);
        if (leader.equals(player) && !members.isEmpty()) leader = members.get(0);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putUUID("leader", leader);
        ListTag list = new ListTag();
        for (UUID member : members) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", member);
            entry.putString("name", name(member));
            entry.putInt("level", cachedLevel(member));
            list.add(entry);
        }
        tag.put("members", list);
        return tag;
    }

    static Party load(CompoundTag tag) {
        Party party = new Party(tag.getUUID("id"), tag.getUUID("leader"));
        for (Tag element : tag.getList("members", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            party.add(entry.getUUID("uuid"), entry.getString("name"), entry.getInt("level"));
        }
        if (!party.members.isEmpty() && !party.members.contains(party.leader)) party.leader = party.members.get(0);
        return party;
    }
}
