package com.heroicpaths.gear;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.INBTSerializable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;
import java.util.List;

/**
 * Снаряжение, снятое с игрока в момент смерти, чтобы оно не выпало. Переходит к новому телу игрока
 * и возвращается в те же слоты после возрождения. Хранится в данных игрока, поэтому переживает выход из игры.
 */
public final class KeptGear implements INBTSerializable<CompoundTag> {
    public record Entry(int slot, ItemStack stack) {}

    private final List<Entry> entries = new ArrayList<>();

    public void add(int slot, ItemStack stack) {
        entries.add(new Entry(slot, stack));
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void clear() {
        entries.clear();
    }

    @Override
    public @UnknownNullability CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag item = new CompoundTag();
            item.putInt("slot", entry.slot());
            item.put("item", entry.stack().save(provider));
            list.add(item);
        }
        tag.put("items", list);
        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        entries.clear();
        for (Tag element : tag.getList("items", Tag.TAG_COMPOUND)) {
            CompoundTag item = (CompoundTag) element;
            ItemStack stack = ItemStack.parseOptional(provider, item.getCompound("item"));
            if (!stack.isEmpty()) entries.add(new Entry(item.getInt("slot"), stack));
        }
    }
}
