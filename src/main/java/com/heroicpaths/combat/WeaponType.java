package com.heroicpaths.combat;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.item.MeleeWeaponItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Тип урона оружия ближнего боя и его особый эффект:
 * колющее пробивает броню, режущее вызывает кровотечение, дробящее оглушает и крушит кости и камень.
 * Тип можно переопределить тегами {@code heroicpaths:piercing_weapons} и т. п. (для оружия других модов).
 */
public enum WeaponType {
    PIERCING(ChatFormatting.AQUA),
    SLASHING(ChatFormatting.RED),
    CRUSHING(ChatFormatting.GOLD);

    public static final TagKey<Item> PIERCING_TAG = TagKey.create(Registries.ITEM, HeroicPaths.id("piercing_weapons"));
    public static final TagKey<Item> SLASHING_TAG = TagKey.create(Registries.ITEM, HeroicPaths.id("slashing_weapons"));
    public static final TagKey<Item> CRUSHING_TAG = TagKey.create(Registries.ITEM, HeroicPaths.id("crushing_weapons"));
    public static final TagKey<Item> TWO_HANDED_TAG = TagKey.create(Registries.ITEM, HeroicPaths.id("two_handed_weapons"));

    private final ChatFormatting color;

    WeaponType(ChatFormatting color) {
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MutableComponent displayName() {
        return Component.translatable("weapon_type.heroicpaths." + id()).withStyle(color);
    }

    /** Тип урона оружия; null — не оружие ближнего боя. */
    @Nullable
    public static WeaponType of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.is(PIERCING_TAG)) return PIERCING;
        if (stack.is(SLASHING_TAG)) return SLASHING;
        if (stack.is(CRUSHING_TAG)) return CRUSHING;
        Item item = stack.getItem();
        if (item instanceof MeleeWeaponItem weapon) return weapon.weaponType();
        if (item instanceof MaceItem) return CRUSHING;
        if (item instanceof TridentItem) return PIERCING;
        if (item instanceof SwordItem || item instanceof AxeItem) return SLASHING;
        return null;
    }

    /** Двуручное оружие: двуручный меч, копьё, боевой молот (и всё из тега двуручного оружия). */
    public static boolean isTwoHanded(ItemStack stack) {
        return stack.is(TWO_HANDED_TAG) || stack.getItem() instanceof MeleeWeaponItem weapon && weapon.isTwoHanded();
    }

    /** Кинжал парный: в основной руке его держат вместе со вторым таким же, поэтому вторая рука занята. */
    public static boolean isPaired(ItemStack stack) {
        return stack.getItem() instanceof com.heroicpaths.item.DaggerItem;
    }

    /** Оружие в основной руке занимает и вторую руку: двуручное или парные кинжалы. */
    public static boolean locksOffhand(ItemStack main) {
        return isTwoHanded(main) || isPaired(main);
    }

    /** Одноручное оружие ближнего боя: его можно взять парой. */
    public static boolean isOneHanded(ItemStack stack) {
        return of(stack) != null && !isTwoHanded(stack);
    }

    /** Строки подсказки: тип урона с эффектом и хват (одноручное или двуручное). */
    public static void addTooltip(ItemStack stack, List<Component> tooltip, int index) {
        WeaponType type = of(stack);
        if (type == null) return;
        index = Math.min(index, tooltip.size());
        tooltip.add(index, Component.translatable("tooltip.heroicpaths.weapon_type", type.displayName(),
                Component.translatable("weapon_type.heroicpaths." + type.id() + ".effect")).withStyle(ChatFormatting.GRAY));
        String grip = isTwoHanded(stack) ? "two_handed" : isPaired(stack) ? "paired" : "one_handed";
        tooltip.add(index + 1, Component.translatable("tooltip.heroicpaths." + grip)
                .withStyle(ChatFormatting.DARK_GRAY));
        Component power = com.heroicpaths.combat.PowerAttack.describe(stack);
        if (power != null) tooltip.add(index + 2, power.copy().withStyle(ChatFormatting.DARK_GRAY));
    }
}
