package com.heroicpaths.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Один проход данжа: место в измерении данжей, группа, для которой открыты врата, кто уже вошёл
 * и куда возвращаться.
 */
public final class DungeonInstance {
    public final int index;
    public final DungeonType type;
    /** Размер группы на момент открытия врат: по нему усилены враги. */
    public final int partySize;
    public final BlockPos origin;
    public final BlockPos arrival;
    /** Где появляется группа на арене босса (низ шахты с пятого этажа): рядом врата домой. */
    public final BlockPos bossArrival;
    public final ResourceKey<Level> returnLevel;
    public final BlockPos returnPos;
    /** Группа, открывшая врата; null — игрок был один. */
    @Nullable
    public final UUID partyId;
    /** Кто может войти: состав группы на момент открытия врат. */
    private final Set<UUID> roster = new LinkedHashSet<>();
    private final Set<UUID> entered = new HashSet<>();
    private boolean completed;
    /** Свиток доступа потрачен: группа уже вошла. */
    private boolean scrollPaid;

    public DungeonInstance(int index, DungeonType type, Collection<UUID> roster, @Nullable UUID partyId, BlockPos origin,
                           BlockPos arrival, BlockPos bossArrival, ResourceKey<Level> returnLevel, BlockPos returnPos) {
        this.index = index;
        this.type = type;
        this.roster.addAll(roster);
        this.partySize = Math.max(1, Math.min(DungeonType.MAX_PARTY, roster.size()));
        this.partyId = partyId;
        this.origin = origin;
        this.arrival = arrival;
        this.bossArrival = bossArrival;
        this.returnLevel = returnLevel;
        this.returnPos = returnPos;
    }

    public Set<UUID> entered() {
        return entered;
    }

    public Set<UUID> roster() {
        return roster;
    }

    /** Войти может только участник группы, для которой открыты врата. */
    public boolean isInRoster(UUID player) {
        return roster.contains(player);
    }

    /** Вошли все, для кого открыты врата. */
    public boolean everyoneEntered() {
        return entered.containsAll(roster);
    }

    public boolean isCompleted() {
        return completed;
    }

    public void complete() {
        completed = true;
    }

    public boolean isScrollPaid() {
        return scrollPaid;
    }

    public void markScrollPaid() {
        scrollPaid = true;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("index", index);
        tag.putString("type", type.id());
        tag.put("origin", NbtUtils.writeBlockPos(origin));
        tag.put("arrival", NbtUtils.writeBlockPos(arrival));
        tag.put("bossArrival", NbtUtils.writeBlockPos(bossArrival));
        tag.putString("returnLevel", returnLevel.location().toString());
        tag.put("returnPos", NbtUtils.writeBlockPos(returnPos));
        if (partyId != null) tag.putUUID("partyId", partyId);
        tag.put("roster", uuids(roster));
        tag.put("entered", uuids(entered));
        tag.putBoolean("completed", completed);
        tag.putBoolean("scrollPaid", scrollPaid);
        return tag;
    }

    public static DungeonInstance load(CompoundTag tag) {
        DungeonType type = DungeonType.byId(tag.getString("type"));
        Set<UUID> roster = readUuids(tag.getList("roster", Tag.TAG_INT_ARRAY));
        Set<UUID> entered = readUuids(tag.getList("entered", Tag.TAG_INT_ARRAY));
        // Проходы старой версии не знали состав группы: пускаем тех, кто уже вошёл.
        if (roster.isEmpty()) roster.addAll(entered);
        BlockPos arrival = NbtUtils.readBlockPos(tag, "arrival").orElse(BlockPos.ZERO);
        DungeonInstance instance = new DungeonInstance(tag.getInt("index"), type == null ? DungeonType.CRYPT : type,
                roster, tag.hasUUID("partyId") ? tag.getUUID("partyId") : null,
                NbtUtils.readBlockPos(tag, "origin").orElse(BlockPos.ZERO),
                arrival,
                // Одноэтажные проходы старой версии: арены нет, врата домой — у входа.
                NbtUtils.readBlockPos(tag, "bossArrival").orElse(arrival),
                ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("returnLevel"))),
                NbtUtils.readBlockPos(tag, "returnPos").orElse(BlockPos.ZERO));
        instance.entered.addAll(entered);
        instance.completed = tag.getBoolean("completed");
        instance.scrollPaid = tag.getBoolean("scrollPaid");
        return instance;
    }

    private static ListTag uuids(Set<UUID> set) {
        ListTag list = new ListTag();
        for (UUID uuid : set) list.add(NbtUtils.createUUID(uuid));
        return list;
    }

    private static Set<UUID> readUuids(ListTag list) {
        Set<UUID> set = new LinkedHashSet<>();
        for (Tag uuid : list) set.add(NbtUtils.loadUUID(uuid));
        return set;
    }
}
