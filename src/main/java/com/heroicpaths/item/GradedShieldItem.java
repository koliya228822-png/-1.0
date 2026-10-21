package com.heroicpaths.item;

import com.heroicpaths.HeroicPaths;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.List;

/**
 * Щит с грейдом, как у оружия: железо (I), сталь (II), дамаск (III). Блокирует как обычный щит,
 * а во второй руке добавляет броню; вид щита задаёт характер: баклер лёгкий и быстрый,
 * треугольный — надёжный, каплевидный — самый крепкий, но тяжёлый.
 */
public class GradedShieldItem extends ShieldItem {
    public enum Type {
        BUCKLER, HEATER, KITE
    }

    private final Type type;
    private final int grade;

    public GradedShieldItem(Type type, int grade, Item.Properties properties) {
        super(properties.durability(durability(type, grade)).attributes(attributes(type, grade)));
        this.type = type;
        this.grade = grade;
    }

    public Type type() {
        return type;
    }

    public int grade() {
        return grade;
    }

    private static double gradeFactor(int grade) {
        return grade == 1 ? 1 : grade == 2 ? 1.5 : 2;
    }

    public static int durability(Type type, int grade) {
        int base = switch (type) {
            case BUCKLER -> 220;
            case HEATER -> 336;
            case KITE -> 450;
        };
        return (int) Math.round(base * (grade == 1 ? 1 : grade == 2 ? 1.8 : 3));
    }

    public static ItemAttributeModifiers attributes(Type type, int grade) {
        double k = gradeFactor(grade);
        ItemAttributeModifiers.Builder b = ItemAttributeModifiers.builder();
        double armor = switch (type) {
            case BUCKLER -> 1;
            case HEATER -> 2;
            case KITE -> 3;
        } * k;
        b.add(Attributes.ARMOR, new AttributeModifier(HeroicPaths.id("shield_armor"), armor, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.OFFHAND);
        switch (type) {
            case BUCKLER -> b.add(Attributes.ATTACK_SPEED, new AttributeModifier(HeroicPaths.id("shield_agility"), 0.1 * k,
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND);
            case HEATER -> b.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(HeroicPaths.id("shield_footing"), 0.05 * k,
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND);
            case KITE -> {
                b.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(HeroicPaths.id("shield_footing"), 0.1 * k,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND);
                b.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(HeroicPaths.id("shield_weight"), -0.05,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.OFFHAND);
            }
        }
        return b.build();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.heroicpaths.shield." + type.name().toLowerCase()).withStyle(ChatFormatting.GRAY));
    }
}
