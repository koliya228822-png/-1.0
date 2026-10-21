package com.heroicpaths.item;

import com.heroicpaths.craft.SoulStones;
import com.heroicpaths.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Камень души: пока лежит в инвентаре, впитывает души убитых существ. Без заряженного камня крафт не даёт
 * вещей редкой редкости и выше — души платят за удачу. Малый камень питает редкие и уникальные вещи,
 * великий — ещё и эпические, древний — вплоть до легендарных.
 */
public class SoulStoneItem extends Item {
    private final int capacity;
    private final ItemRarity.Tier maxTier;

    public SoulStoneItem(int capacity, ItemRarity.Tier maxTier, Properties properties) {
        super(properties.stacksTo(1));
        this.capacity = capacity;
        this.maxTier = maxTier;
    }

    public int capacity() {
        return capacity;
    }

    /** Самая высокая редкость, за которую может заплатить камень. */
    public ItemRarity.Tier maxTier() {
        return maxTier;
    }

    public static int souls(ItemStack stack) {
        return stack.getOrDefault(ModComponents.SOULS, 0);
    }

    public static void setSouls(ItemStack stack, int souls) {
        int capacity = stack.getItem() instanceof SoulStoneItem stone ? stone.capacity : 0;
        stack.set(ModComponents.SOULS, Mth.clamp(souls, 0, capacity));
    }

    public static ItemStack full(Item item) {
        ItemStack stack = new ItemStack(item);
        if (item instanceof SoulStoneItem stone) setSouls(stack, stone.capacity);
        return stack;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * souls(stack) / capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x9C6CFF;
    }

    /** Полный камень сияет. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return souls(stack) >= capacity;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.heroicpaths.soul_stone.souls", souls(stack), capacity).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.heroicpaths.soul_stone.max", maxTier.displayName()).withStyle(ChatFormatting.GRAY));
        MutableCosts costs = new MutableCosts();
        for (ItemRarity.Tier tier : ItemRarity.Tier.values()) {
            if (tier.ordinal() < ItemRarity.Tier.RARE.ordinal() || tier.ordinal() > maxTier.ordinal()) continue;
            costs.add(tier);
        }
        tooltip.add(Component.translatable("item.heroicpaths.soul_stone.costs", costs.text).withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.heroicpaths.soul_stone.desc").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** «редкая — 8, уникальная — 16». */
    private static final class MutableCosts {
        private final net.minecraft.network.chat.MutableComponent text = Component.empty();
        private boolean first = true;

        void add(ItemRarity.Tier tier) {
            if (!first) text.append(", ");
            first = false;
            text.append(tier.displayName()).append(" — " + SoulStones.cost(tier));
        }
    }
}
