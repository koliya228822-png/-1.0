package com.heroicpaths.party;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Все группы мира. Хранится в данных верхнего мира, переживает перезапуск сервера. */
public final class PartyData extends SavedData {
    private static final String NAME = "heroicpaths_parties";

    private final Map<UUID, Party> parties = new LinkedHashMap<>();
    private final Map<UUID, UUID> partyByPlayer = new HashMap<>();

    public static PartyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(PartyData::new, PartyData::load), NAME);
    }

    @Nullable
    public Party partyOf(UUID player) {
        UUID id = partyByPlayer.get(player);
        return id == null ? null : parties.get(id);
    }

    @Nullable
    public Party byId(UUID id) {
        return parties.get(id);
    }

    public Collection<Party> all() {
        return Collections.unmodifiableCollection(new ArrayList<>(parties.values()));
    }

    public Party create(UUID leader, String name, int level) {
        Party party = new Party(UUID.randomUUID(), leader);
        party.add(leader, name, level);
        parties.put(party.id(), party);
        partyByPlayer.put(leader, party.id());
        setDirty();
        return party;
    }

    public void join(Party party, UUID player, String name, int level) {
        party.add(player, name, level);
        partyByPlayer.put(player, party.id());
        setDirty();
    }

    /** Убирает игрока из его группы; пустая группа распускается. */
    public void leave(UUID player) {
        Party party = partyOf(player);
        partyByPlayer.remove(player);
        if (party == null) return;
        party.remove(player);
        if (party.size() == 0) parties.remove(party.id());
        setDirty();
    }

    public void disband(Party party) {
        for (UUID member : party.members()) partyByPlayer.remove(member);
        parties.remove(party.id());
        setDirty();
    }

    public void remember(Party party, UUID player, String name, int level) {
        party.remember(player, name, level);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Party party : parties.values()) list.add(party.save());
        tag.put("parties", list);
        return tag;
    }

    private static PartyData load(CompoundTag tag, HolderLookup.Provider registries) {
        PartyData data = new PartyData();
        for (Tag element : tag.getList("parties", Tag.TAG_COMPOUND)) {
            Party party = Party.load((CompoundTag) element);
            if (party.size() == 0) continue;
            data.parties.put(party.id(), party);
            for (UUID member : party.members()) data.partyByPlayer.put(member, party.id());
        }
        return data;
    }
}
