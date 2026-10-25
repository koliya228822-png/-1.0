package com.heroicpaths.item;

import com.heroicpaths.registry.ModItems;
import com.heroicpaths.registry.ModTiers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;

import java.util.List;

/**
 * Грейд снаряжения I–VI по ступени материала: железо → сталь → дамаск, кожа → прочная → пропитанная,
 * шерсть → чародейская ткань → астральный шёлк; у жезлов и посохов — ученический → рунный → архимага.
 * Грейды IV–VI у всего снаряжения алмазные: алмаз → гранёный алмаз → бриллиант.
 */
public final class Grades {
    public static final int MAX = 6;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI"};
    private static final ChatFormatting[] COLORS = {ChatFormatting.GRAY, ChatFormatting.GRAY, ChatFormatting.YELLOW, ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.AQUA, ChatFormatting.BLUE, ChatFormatting.WHITE};

    private Grades() {}

    /** Грейд предмета; 0 — у предмета нет грейда. */
    public static int of(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof ClassArmorItem armor) return armor.grade();
        if (item instanceof FocusItem focus) return focus.grade();
        if (item instanceof GradedBowItem bow) return bow.grade();
        if (item instanceof HandCrossbowItem crossbow) return crossbow.grade();
        if (item instanceof GradedShieldItem shield) return shield.grade();
        if (item instanceof MusketItem musket) return musket.grade();
        if (item instanceof com.heroicpaths.accessory.AccessoryItem) return com.heroicpaths.accessory.AccessoryItem.grade(stack);
        if (item == Items.BOW) return 1;
        if (item instanceof SwordItem sword) return ofTier(sword.getTier());
        if (item instanceof AxeItem axe) return ofTier(axe.getTier());
        return 0;
    }

    /** Ступень материала: 2 — сталь, прочная кожа, чародейская ткань; 3 — дамаск, пропитанная кожа, астральный шёлк; иначе 0. */
    public static int materialGrade(ItemStack stack) {
        Item item = stack.getItem();
        if (item == ModItems.STEEL_PLATE.get() || item == ModItems.HARDENED_LEATHER.get() || item == ModItems.ARCANE_CLOTH.get()) return 2;
        if (item == ModItems.DAMASCUS_INGOT.get() || item == ModItems.IMPREGNATED_LEATHER.get() || item == ModItems.ASTRAL_SILK.get()) return 3;
        // Камни души: великий — подмастерье рунного дела, древний — мастер-ремесленник.
        if (item == ModItems.GREATER_SOUL_STONE.get()) return 2;
        if (item == ModItems.ANCIENT_SOUL_STONE.get()) return 3;
        // Огранка: гранёный алмаз — подмастерье ювелира, бриллиант — мастер-ремесленник.
        if (item == ModItems.FACETED_DIAMOND.get()) return 2;
        if (item == ModItems.BRILLIANT.get()) return 3;
        return 0;
    }

    private static int ofTier(Tier tier) {
        if (tier == Tiers.IRON) return 1;
        if (tier == ModTiers.STEEL) return 2;
        if (tier == ModTiers.DAMASCUS) return 3;
        if (tier == ModTiers.DIAMOND) return 4;
        if (tier == ModTiers.FACETED) return 5;
        if (tier == ModTiers.BRILLIANT) return 6;
        return 0;
    }

    public static Component label(int grade) {
        return Component.literal(ROMAN[grade]).withStyle(COLORS[grade]);
    }

    /** Строка «Грейд: II» на позиции index (под названием, редкостью и её бонусами). Возвращает, сколько строк добавлено. */
    public static int addTooltip(ItemStack stack, List<Component> tooltip, int index) {
        int grade = of(stack);
        if (grade < 1 || grade > MAX) return 0;
        tooltip.add(Math.min(index, tooltip.size()), Component.translatable("tooltip.heroicpaths.grade", label(grade)).withStyle(ChatFormatting.GRAY));
        return 1;
    }
}
