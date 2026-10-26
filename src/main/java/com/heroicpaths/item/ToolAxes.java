package com.heroicpaths.item;

import com.heroicpaths.combat.WeaponType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Ванильный топор — инструмент, а не оружие: бьёт как кирка того же материала (железный топор — как железная кирка).
 * Его урон при атаке заменяется уроном кирки прямо в компонентах предмета по умолчанию, поэтому подсказка, редкость
 * и сам удар видят одно и то же число. Скорость атаки, рубка деревьев и выбивание щита остаются как у топора.
 * Боевые топоры мода не трогаются.
 */
public final class ToolAxes {
    private ToolAxes() {}

    /** Меняет урон ванильных топоров на урон кирок того же материала (шина мода). */
    public static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof AxeItem axe) || !WeaponType.isToolAxe(axe)) continue;
            Item pickaxe = pickaxeOf(axe.getTier());
            if (pickaxe == null) continue;
            ItemAttributeModifiers modifiers = withDamageOf(
                    item.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY),
                    pickaxe.components().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY));
            event.modify(item, builder -> builder.set(DataComponents.ATTRIBUTE_MODIFIERS, modifiers));
        }
    }

    /** Ванильная кирка того же материала; null — такой нет. */
    @Nullable
    private static Item pickaxeOf(Tier tier) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item instanceof PickaxeItem pickaxe && pickaxe.getTier() == tier
                    && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("minecraft")) {
                return item;
            }
        }
        return null;
    }

    /** Модификаторы топора, у которых урон при атаке взят у кирки. */
    private static ItemAttributeModifiers withDamageOf(ItemAttributeModifiers axe, ItemAttributeModifiers pickaxe) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry entry : axe.modifiers()) {
            if (!entry.attribute().equals(Attributes.ATTACK_DAMAGE)) builder.add(entry.attribute(), entry.modifier(), entry.slot());
        }
        for (ItemAttributeModifiers.Entry entry : pickaxe.modifiers()) {
            if (entry.attribute().equals(Attributes.ATTACK_DAMAGE)) builder.add(entry.attribute(), entry.modifier(), entry.slot());
        }
        return builder.build().withTooltip(axe.showInTooltip());
    }

    /** Прибавка урона при атаке, которую даёт предмет в основной руке (без базового урона игрока). */
    public static double attackDamage(ItemStack stack) {
        double[] damage = {0};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_DAMAGE) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                damage[0] += modifier.amount();
            }
        });
        return damage[0];
    }
}
