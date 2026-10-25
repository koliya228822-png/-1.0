package com.heroicpaths.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import java.util.function.Consumer;

/** Лук высокого грейда из прочной или пропитанной кожи: стрелы из него бьют сильнее. */
public class GradedBowItem extends BowItem {
    private final int grade;
    private final int damageBonusPercent;

    public GradedBowItem(int grade, int damageBonusPercent, Properties properties) {
        super(properties);
        this.grade = grade;
        this.damageBonusPercent = damageBonusPercent;
    }

    public int grade() {
        return grade;
    }

    /** Множитель урона стрел из этого оружия: 1 для обычного лука. */
    public static float damageFactor(@Nullable ItemStack weapon) {
        return weapon != null && weapon.getItem() instanceof GradedBowItem bow ? 1f + bow.damageBonusPercent / 100f : 1f;
    }

    @Override
    public AbstractArrow customArrow(AbstractArrow arrow, ItemStack projectileStack, ItemStack weaponStack) {
        arrow.setBaseDamage(arrow.getBaseDamage() * damageFactor(weaponStack));
        return arrow;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.heroicpaths.graded_bow.hint", damageBonusPercent).withStyle(ChatFormatting.BLUE));
    }

    /** Снаряжение не изнашивается в бою: прочность уходит только при смерти. */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<Item> onBroken) {
        return 0;
    }
}
