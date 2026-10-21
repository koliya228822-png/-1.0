package com.heroicpaths.rpg.ability;

import com.heroicpaths.item.DaggerItem;
import com.heroicpaths.item.MusketItem;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;

import java.util.Locale;

/** Оружие, которое нужно держать для умения. */
public enum WeaponReq {
    NONE,
    /** Любое оружие ближнего боя в основной руке. */
    MELEE,
    /** Двуручный меч в основной руке и пустая вторая рука. */
    TWO_HANDED,
    /** Щит в любой руке. */
    SHIELD,
    /** Топор в основной руке. */
    AXE,
    /** Кинжал в основной руке. */
    DAGGER,
    /** Лук в любой руке. */
    BOW,
    /** Арбалет в любой руке. */
    CROSSBOW,
    /** Мушкет в основной руке. */
    MUSKET,
    /** Жезл или посох мага: от его силы зависит урон заклинания. */
    FOCUS;

    public boolean isMet(Player player) {
        Item main = player.getMainHandItem().getItem();
        Item off = player.getOffhandItem().getItem();
        return switch (this) {
            case NONE -> true;
            case MELEE -> isMeleeWeapon(main);
            case TWO_HANDED -> com.heroicpaths.combat.WeaponType.isTwoHanded(player.getMainHandItem()) && player.getOffhandItem().isEmpty();
            case SHIELD -> main instanceof ShieldItem || off instanceof ShieldItem;
            case AXE -> main instanceof AxeItem;
            case DAGGER -> main instanceof DaggerItem;
            case BOW -> main instanceof BowItem || off instanceof BowItem;
            case CROSSBOW -> main instanceof CrossbowItem || off instanceof CrossbowItem;
            case MUSKET -> main instanceof MusketItem;
            case FOCUS -> com.heroicpaths.item.FocusItem.power(player) > 0;
        };
    }

    public static boolean isMeleeWeapon(Item item) {
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof MaceItem || item instanceof TridentItem;
    }

    public MutableComponent displayName() {
        return Component.translatable("weapon_req.heroicpaths." + name().toLowerCase(Locale.ROOT));
    }
}
