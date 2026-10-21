package com.heroicpaths.item;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.rpg.PlayerClass;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Вес брони: ткань (мантии мага) → лёгкая (кожа) → средняя (кольчуга, золото, черепаший панцирь) → тяжёлая (латы,
 * железо, алмаз, незерит). Вес можно задать тегами {@code heroicpaths:cloth_armor} и т. п. (для брони других модов),
 * иначе он считается по защите нагрудника материала.
 */
public enum ArmorWeight {
    CLOTH(ChatFormatting.LIGHT_PURPLE),
    LIGHT(ChatFormatting.GREEN),
    MEDIUM(ChatFormatting.YELLOW),
    HEAVY(ChatFormatting.RED);

    private final ChatFormatting color;
    private final TagKey<Item> tag;

    ArmorWeight(ChatFormatting color) {
        this.color = color;
        this.tag = TagKey.create(Registries.ITEM, HeroicPaths.id(name().toLowerCase(Locale.ROOT) + "_armor"));
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MutableComponent displayName() {
        return Component.translatable("armor_weight.heroicpaths." + id()).withStyle(color);
    }

    /** Вес вещи; null — это не броня. */
    @Nullable
    public static ArmorWeight of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (ArmorWeight weight : values()) if (stack.is(weight.tag)) return weight;
        if (stack.getItem() instanceof ClassArmorItem armor) {
            return switch (armor.playerClass()) {
                case WARRIOR -> HEAVY;
                case MAGE -> CLOTH;
                default -> LIGHT;
            };
        }
        if (!(stack.getItem() instanceof ArmorItem armor)) return null;
        ArmorMaterial material = armor.getMaterial().value();
        if (material == ArmorMaterials.LEATHER.value()) return LIGHT;
        if (material == ArmorMaterials.CHAIN.value() || material == ArmorMaterials.GOLD.value() || material == ArmorMaterials.TURTLE.value()) {
            return MEDIUM;
        }
        int chest = material.getDefense(ArmorItem.Type.CHESTPLATE);
        return chest >= 6 ? HEAVY : chest >= 4 ? MEDIUM : LIGHT;
    }

    /** Самая тяжёлая броня, которую класс носит без штрафов. */
    public static ArmorWeight proficiency(PlayerClass playerClass) {
        return switch (playerClass) {
            case MAGE -> CLOTH;
            case ROGUE -> LIGHT;
            case ARCHER, HUNTER -> MEDIUM;
            default -> HEAVY;
        };
    }
}
