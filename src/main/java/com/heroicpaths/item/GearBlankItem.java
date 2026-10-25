package com.heroicpaths.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Заготовка алмазного снаряжения. На наковальне вещь грейда на единицу ниже и заготовка дают ту же вещь
 * грейда заготовки: дамасская броня + алмазная заготовка шлема → алмазный шлем того же класса, той же редкости
 * и с тем же сетом. Заготовки брони — по слоту, заготовка оружия — для любого оружия, щита или фокуса.
 */
public class GearBlankItem extends Item {
    private final int grade;
    @Nullable
    private final ArmorItem.Type slot;

    public GearBlankItem(int grade, @Nullable ArmorItem.Type slot, Properties properties) {
        super(properties);
        this.grade = grade;
        this.slot = slot;
    }

    public int grade() {
        return grade;
    }

    /** Слот брони; null — заготовка оружия. */
    @Nullable
    public ArmorItem.Type slot() {
        return slot;
    }

    /** Подходит ли вещь: грейд на единицу ниже и тот же слот брони (или не броня — для заготовки оружия). */
    public boolean fits(ItemStack gear) {
        if (Grades.of(gear) != grade - 1) return false;
        if (gear.getItem() instanceof ArmorItem armor) return armor.getType() == slot;
        return slot == null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.heroicpaths.gear_blank", Grades.label(grade - 1), Grades.label(grade))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.heroicpaths.gear_blank.rank").withStyle(ChatFormatting.DARK_GRAY));
    }
}
