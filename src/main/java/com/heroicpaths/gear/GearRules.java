package com.heroicpaths.gear;

import com.heroicpaths.HeroicPaths;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

/**
 * Что считается оружием и бронёй. Оно не выпадает при смерти, а теряет прочность, и его нельзя чинить.
 * Мечи, топоры, луки, арбалеты, трезубцы, булавы, щиты, жезлы и посохи и любая броня, плюс всё из тега {@code heroicpaths:gear}
 * (туда модпак может добавить оружие других модов).
 */
public final class GearRules {
    public static final TagKey<Item> GEAR = TagKey.create(Registries.ITEM, HeroicPaths.id("gear"));
    /** Сколько прочности снаряжение теряет при смерти: 20% от максимума. */
    public static final float DEATH_DURABILITY_LOSS = 0.2f;

    private GearRules() {}

    public static boolean isGear(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof ArmorItem || item instanceof SwordItem || item instanceof AxeItem
                || item instanceof ProjectileWeaponItem || item instanceof TridentItem || item instanceof MaceItem
                || item instanceof ShieldItem || item instanceof com.heroicpaths.item.MusketItem
                || item instanceof com.heroicpaths.item.FocusItem || stack.is(GEAR);
    }

    /** Потеря прочности за одну смерть. */
    public static int deathLoss(ItemStack stack) {
        return Math.max(1, (int) Math.ceil(stack.getMaxDamage() * DEATH_DURABILITY_LOSS));
    }
}
