package com.heroicpaths.item;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.combat.WeaponType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * Оружие ближнего боя с типом урона и хватом. Рубящим ударом по площади бьёт только режущее оружие;
 * дробящее, как топор, выбивает щит из рук.
 */
public class MeleeWeaponItem extends SwordItem {
    private final WeaponType weaponType;
    private final boolean twoHanded;

    public MeleeWeaponItem(Tier tier, WeaponType weaponType, boolean twoHanded, Properties properties) {
        super(tier, properties);
        this.weaponType = weaponType;
        this.twoHanded = twoHanded;
    }

    public WeaponType weaponType() {
        return weaponType;
    }

    public boolean isTwoHanded() {
        return twoHanded;
    }

    /** Урон и скорость атаки как у меча; reach > 0 добавляет дальность удара (копьё). */
    public static ItemAttributeModifiers attributes(Tier tier, int damage, float speed, double reach) {
        ItemAttributeModifiers modifiers = createAttributes(tier, damage, speed);
        if (reach > 0) {
            modifiers = modifiers.withModifierAdded(Attributes.ENTITY_INTERACTION_RANGE,
                    new AttributeModifier(HeroicPaths.id("weapon_reach"), reach, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        return modifiers;
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
        if (itemAbility == ItemAbilities.SWORD_SWEEP && weaponType != WeaponType.SLASHING) return false;
        return super.canPerformAction(stack, itemAbility);
    }

    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker) {
        return weaponType == WeaponType.CRUSHING || super.canDisableShield(stack, shield, entity, attacker);
    }
}
