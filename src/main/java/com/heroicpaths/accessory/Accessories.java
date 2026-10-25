package com.heroicpaths.accessory;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Слоты аксессуаров игрока: перчатки, пояс, наручи, ожерелье, две серьги и два кольца. На модели игрока не видны.
 * Хранятся в RPG-данных игрока, поэтому переживают смерть и приходят на клиент вместе с ними.
 */
public final class Accessories {
    public static final AccessoryKind[] SLOTS = {AccessoryKind.GLOVES, AccessoryKind.BELT, AccessoryKind.BRACERS, AccessoryKind.NECKLACE,
            AccessoryKind.EARRING, AccessoryKind.EARRING, AccessoryKind.RING, AccessoryKind.RING};
    public static final int SIZE = SLOTS.length;

    private final ItemStackHandler items = new ItemStackHandler(SIZE) {
        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return fits(slot, stack);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    public static boolean fits(int slot, ItemStack stack) {
        return slot >= 0 && slot < SIZE && stack.getItem() instanceof AccessoryItem accessory && accessory.kind() == SLOTS[slot];
    }

    public ItemStackHandler handler() {
        return items;
    }

    public ItemStack get(int slot) {
        return items.getStackInSlot(slot);
    }

    public void set(int slot, ItemStack stack) {
        items.setStackInSlot(slot, stack);
    }

    /** Все надетые аксессуары (без пустых слотов). */
    public List<ItemStack> worn() {
        List<ItemStack> worn = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) if (!get(i).isEmpty()) worn.add(get(i));
        return worn;
    }

    /** Первый свободный подходящий слот или −1. */
    public int freeSlotFor(ItemStack stack) {
        for (int i = 0; i < SIZE; i++) if (get(i).isEmpty() && fits(i, stack)) return i;
        return -1;
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        return items.serializeNBT(provider);
    }

    public void load(HolderLookup.Provider provider, CompoundTag tag) {
        for (int i = 0; i < SIZE; i++) items.setStackInSlot(i, ItemStack.EMPTY);
        if (tag.isEmpty()) return;
        ItemStackHandler loaded = new ItemStackHandler(SIZE);
        loaded.deserializeNBT(provider, tag);
        for (int i = 0; i < Math.min(SIZE, loaded.getSlots()); i++) items.setStackInSlot(i, loaded.getStackInSlot(i));
    }
}
