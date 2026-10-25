package com.heroicpaths.craft;

import com.heroicpaths.item.ClassArmorItem;
import com.heroicpaths.item.FocusItem;
import com.heroicpaths.item.MusketItem;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.PlayerClass;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Ремесло игрока. Кузнец работает у горна (металлическое оружие и латы), плотник — у дубильного чана
 * и ткацкого станка (луки, арбалеты, кожаная броня и мантии), рунный мастер — у рунного стола
 * (руны, жезлы, посохи и рунные зачарования), ювелир — у гранильного стола и стола ювелира (огранка алмазов и украшения).
 */
public enum Profession {
    BLACKSMITH(ChatFormatting.GOLD, () -> ModItems.BLACKSMITH_MANUAL.get()),
    CARPENTER(ChatFormatting.GREEN, () -> ModItems.CARPENTRY_MANUAL.get()),
    RUNECRAFTER(ChatFormatting.AQUA, () -> ModItems.RUNECRAFT_MANUAL.get()),
    JEWELER(ChatFormatting.LIGHT_PURPLE, () -> ModItems.JEWELER_MANUAL.get());

    private final ChatFormatting color;
    private final Supplier<Item> manual;

    Profession(ChatFormatting color, Supplier<Item> manual) {
        this.color = color;
        this.manual = manual;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public ChatFormatting color() {
        return color;
    }

    public MutableComponent displayName() {
        return Component.translatable("profession.heroicpaths." + id()).withStyle(color);
    }

    /** Трактат, по которому растёт это ремесло. */
    public Item manual() {
        return manual.get();
    }

    @Nullable
    public static Profession byId(String id) {
        for (Profession profession : values()) if (profession.id().equals(id)) return profession;
        return null;
    }

    /** Чьё ремесло этот станок. */
    public static Profession of(Station station) {
        return switch (station) {
            case FORGE -> BLACKSMITH;
            case TANNERY, LOOM -> CARPENTER;
            case RUNE_TABLE -> RUNECRAFTER;
            case GEM_TABLE, JEWELER_TABLE -> JEWELER;
        };
    }

    /** Чьё ремесло вещь, сделанная в верстаке: жезлы — рунного мастера, луки, щиты и кожа — плотника, остальное — кузнеца. */
    public static Profession forItem(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof FocusItem) return RUNECRAFTER;
        if (item instanceof com.heroicpaths.accessory.AccessoryItem accessory) {
            if (accessory.armorClass() == null) return JEWELER;
            return accessory.armorClass() == PlayerClass.WARRIOR ? BLACKSMITH : CARPENTER;
        }
        if (item instanceof ProjectileWeaponItem || item instanceof MusketItem || item instanceof ShieldItem) return CARPENTER;
        if (item instanceof ClassArmorItem armor) {
            return ClassArmorItem.armorClass(armor.playerClass()) == PlayerClass.WARRIOR ? BLACKSMITH : CARPENTER;
        }
        if (item instanceof ArmorItem armor && armor.getMaterial().value() == ArmorMaterials.LEATHER.value()) return CARPENTER;
        return BLACKSMITH;
    }

    /** Жители, у которых можно купить трактаты этого ремесла. */
    @Nullable
    public static Profession taughtBy(VillagerProfession villager) {
        if (villager == VillagerProfession.WEAPONSMITH || villager == VillagerProfession.ARMORER
                || villager == VillagerProfession.TOOLSMITH) {
            return BLACKSMITH;
        }
        if (villager == VillagerProfession.FLETCHER || villager == VillagerProfession.LEATHERWORKER
                || villager == VillagerProfession.SHEPHERD) {
            return CARPENTER;
        }
        if (villager == VillagerProfession.LIBRARIAN || villager == VillagerProfession.CLERIC) return RUNECRAFTER;
        if (villager == VillagerProfession.MASON) return JEWELER;
        return null;
    }
}
