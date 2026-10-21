package com.heroicpaths.dungeon;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Все проходы данжей мира. Хранится в данных верхнего мира.
 * Здесь же ждут хозяев камни и свитки, которые алтарь вернул, пока хозяин был не в сети.
 */
public final class DungeonData extends SavedData {
    private static final String NAME = "heroicpaths_dungeons";

    private int nextIndex = 1;
    private final Map<Integer, DungeonInstance> instances = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingReturns = new HashMap<>();

    public static DungeonData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(DungeonData::new, DungeonData::load), NAME);
    }

    public int allocateIndex() {
        setDirty();
        return nextIndex++;
    }

    public void put(DungeonInstance instance) {
        instances.put(instance.index, instance);
        setDirty();
    }

    @Nullable
    public DungeonInstance get(int index) {
        return instances.get(index);
    }

    public void addPendingReturn(UUID player, ItemStack stack) {
        if (stack.isEmpty()) return;
        pendingReturns.computeIfAbsent(player, uuid -> new ArrayList<>()).add(stack.copy());
        setDirty();
    }

    public List<ItemStack> takePendingReturns(UUID player) {
        List<ItemStack> stacks = pendingReturns.remove(player);
        if (stacks == null) return List.of();
        setDirty();
        return stacks;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("next", nextIndex);
        ListTag list = new ListTag();
        for (DungeonInstance instance : instances.values()) list.add(instance.save());
        tag.put("instances", list);
        ListTag returns = new ListTag();
        pendingReturns.forEach((player, stacks) -> {
            for (ItemStack stack : stacks) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("player", player);
                entry.put("item", stack.save(registries));
                returns.add(entry);
            }
        });
        tag.put("pendingReturns", returns);
        return tag;
    }

    private static DungeonData load(CompoundTag tag, HolderLookup.Provider registries) {
        DungeonData data = new DungeonData();
        data.nextIndex = Math.max(1, tag.getInt("next"));
        for (Tag entry : tag.getList("instances", Tag.TAG_COMPOUND)) {
            DungeonInstance instance = DungeonInstance.load((CompoundTag) entry);
            data.instances.put(instance.index, instance);
        }
        for (Tag element : tag.getList("pendingReturns", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("item"));
            if (!stack.isEmpty()) data.pendingReturns.computeIfAbsent(entry.getUUID("player"), uuid -> new ArrayList<>()).add(stack);
        }
        return data;
    }
}
