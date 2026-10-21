package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.function.Supplier;

/**
 * Материалы классовой брони трёх грейдов: латы мечника (железо → сталь → дамаск),
 * лёгкая броня (кожа → прочная → пропитанная), мантия мага (шерсть → чародейская ткань → астральный шёлк).
 */
public final class ModArmorMaterials {
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, HeroicPaths.MODID);

    // Грейд II (исходная классовая броня)
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> WARRIOR = register("warrior",
            3, 6, 8, 3, 12, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(ModItems.STEEL_PLATE.get()), 1.0f, 0.05f);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ARCHER = register("archer",
            2, 5, 6, 2, 15, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(ModItems.HARDENED_LEATHER.get()), 0.5f, 0f);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MAGE = register("mage",
            2, 4, 5, 2, 25, SoundEvents.ARMOR_EQUIP_GENERIC, () -> Ingredient.of(ModItems.ARCANE_CLOTH.get()), 0f, 0f);

    // Грейд I
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> WARRIOR_IRON = register("warrior_iron",
            2, 5, 6, 2, 9, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.of(Items.IRON_INGOT), 0f, 0f);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ARCHER_LEATHER = register("archer_leather",
            1, 3, 4, 1, 15, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(Items.LEATHER), 0f, 0f);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MAGE_WOOL = register("mage_wool",
            1, 2, 3, 1, 20, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(ItemTags.WOOL), 0f, 0f);

    // Грейд III
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> WARRIOR_DAMASCUS = register("warrior_damascus",
            3, 7, 9, 3, 15, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(ModItems.DAMASCUS_INGOT.get()), 2.5f, 0.1f);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> ARCHER_IMPREGNATED = register("archer_impregnated",
            3, 6, 7, 2, 18, SoundEvents.ARMOR_EQUIP_LEATHER, () -> Ingredient.of(ModItems.IMPREGNATED_LEATHER.get()), 1.5f, 0f);
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> MAGE_ASTRAL = register("mage_astral",
            2, 5, 6, 2, 30, SoundEvents.ARMOR_EQUIP_GENERIC, () -> Ingredient.of(ModItems.ASTRAL_SILK.get()), 1.0f, 0f);

    private ModArmorMaterials() {}

    private static DeferredHolder<ArmorMaterial, ArmorMaterial> register(String name, int boots, int leggings, int chestplate,
                                                                          int helmet, int enchantability, Holder<SoundEvent> sound,
                                                                          Supplier<Ingredient> repair, float toughness, float knockbackResistance) {
        return ARMOR_MATERIALS.register(name, () -> {
            EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
            defense.put(ArmorItem.Type.BOOTS, boots);
            defense.put(ArmorItem.Type.LEGGINGS, leggings);
            defense.put(ArmorItem.Type.CHESTPLATE, chestplate);
            defense.put(ArmorItem.Type.HELMET, helmet);
            defense.put(ArmorItem.Type.BODY, chestplate);
            return new ArmorMaterial(defense, enchantability, sound, repair,
                    List.of(new ArmorMaterial.Layer(HeroicPaths.id(name))), toughness, knockbackResistance);
        });
    }
}
