package com.heroicpaths.item;

import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.stat.Stat;
import com.heroicpaths.rpg.stat.StatBlock;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/**
 * Характеристики от надетой классовой брони: по вещам (с учётом редкости и грейда) и за полный комплект.
 * Бонус комплекта считается по самому низкому грейду среди четырёх вещей.
 */
public final class EquipmentStats {
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private EquipmentStats() {}

    public static void collect(Player player, PlayerClass playerClass, StatBlock into) {
        if (playerClass == PlayerClass.NONE) return;
        playerClass = ClassArmorItem.armorClass(playerClass);
        int pieces = 0;
        int lowestGrade = Integer.MAX_VALUE;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ClassArmorItem armor) || armor.playerClass() != playerClass) continue;
            pieces++;
            lowestGrade = Math.min(lowestGrade, armor.grade());
            float factor = (1f + ItemRarity.bonusPercent(stack) / 100f) * ClassArmorItem.gradeFactor(armor.grade());
            for (Map.Entry<Stat, Float> entry : ClassArmorItem.pieceStats(playerClass).asMap().entrySet()) {
                into.add(entry.getKey(), entry.getValue() * factor);
            }
        }
        if (pieces == ARMOR_SLOTS.length) into.addAll(ClassArmorItem.setBonus(playerClass).scaled(ClassArmorItem.gradeFactor(lowestGrade)));
        // Аксессуары: перчатки, пояс и наручи своего типа брони и украшения.
        for (ItemStack stack : com.heroicpaths.rpg.RpgManager.get(player).accessories().worn()) {
            if (stack.getItem() instanceof com.heroicpaths.accessory.AccessoryItem accessory && accessory.worksFor(playerClass)) {
                into.addAll(accessory.stats(stack));
            }
        }
        com.heroicpaths.gear.GearSets.collect(player, into);
    }

    /**
     * Уникальные бонусы редких вещей: от надетой брони, предмета в главной руке и подходящего предмета во второй
     * (жезл, щит, лук или второе одноручное оружие).
     */
    public static void collectTraits(Player player, StatBlock into) {
        for (EquipmentSlot slot : ARMOR_SLOTS) addTraits(player.getItemBySlot(slot), into);
        for (ItemStack stack : com.heroicpaths.rpg.RpgManager.get(player).accessories().worn()) addTraits(stack, into);
        ItemStack main = player.getMainHandItem();
        if (!(main.getItem() instanceof net.minecraft.world.item.ArmorItem)) addTraits(main, into);
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof FocusItem || off.getItem() instanceof net.minecraft.world.item.ShieldItem
                || off.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem || com.heroicpaths.combat.WeaponType.isOneHanded(off)) {
            addTraits(off, into);
        }
    }

    private static void addTraits(ItemStack stack, StatBlock into) {
        for (ItemTrait.Roll roll : ItemRarity.traits(stack)) {
            ItemTrait trait = roll.type();
            if (trait != null && trait.stat() != null) into.add(trait.stat(), roll.value());
        }
    }
}
