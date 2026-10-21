package com.heroicpaths.rpg;

import com.heroicpaths.item.ArmorWeight;
import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.stat.Stat;
import com.heroicpaths.rpg.stat.StatBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Владение бронёй. Каждый класс без штрафов носит броню до своего веса: маг — ткань, разбойник — лёгкую,
 * лучник и охотник — до средней, воин — любую. Каждая ступень веса сверх этого на каждой вещи — очко перегруза,
 * а перегруз даёт штрафы и запрещает часть умений:
 * <ul>
 *     <li>маг: −2.5% урона заклинаний за очко (в полных латах −30%); от 6 очков сильные заклинания (с 15 уровня) недоступны;</li>
 *     <li>разбойник: −2% уклонения и −1.5% скорости за очко; в любой тяжёлой вещи нет «Исчезновения» и удара в спину;</li>
 *     <li>лучник: −5% урона стрел за очко (в латах −20%);</li>
 *     <li>охотник: −6% силы зверя и существ и −1% скорости за очко.</li>
 * </ul>
 * Страж (воин со щитом) за каждую тяжёлую вещь получает +2% снижения урона.
 */
public final class ArmorProficiency {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    public static final float MAGE_SPELL_PENALTY = 2.5f;
    public static final int MAGE_BLOCK_OVERLOAD = 6;
    public static final int STRONG_SPELL_LEVEL = 15;
    public static final float ROGUE_DODGE_PENALTY = 2f;
    public static final float ROGUE_SPEED_PENALTY = 1.5f;
    public static final float ARCHER_BOW_PENALTY = 5f;
    public static final float HUNTER_MINION_PENALTY = 6f;
    public static final float HUNTER_SPEED_PENALTY = 1f;
    public static final float GUARDIAN_REDUCTION_PER_HEAVY = 2f;

    private ArmorProficiency() {}

    /** Очки перегруза вещи для класса: на сколько ступеней она тяжелее, чем класс носит без штрафов. */
    public static int overload(ItemStack stack, PlayerClass playerClass) {
        ArmorWeight weight = ArmorWeight.of(stack);
        if (weight == null) return 0;
        return Math.max(0, weight.ordinal() - ArmorWeight.proficiency(playerClass).ordinal());
    }

    /** Перегруз всей надетой брони. */
    public static int overload(Player player, PlayerClass playerClass) {
        int total = 0;
        for (EquipmentSlot slot : SLOTS) total += overload(player.getItemBySlot(slot), playerClass);
        return total;
    }

    public static int heavyPieces(Player player) {
        int count = 0;
        for (EquipmentSlot slot : SLOTS) if (ArmorWeight.of(player.getItemBySlot(slot)) == ArmorWeight.HEAVY) count++;
        return count;
    }

    /** Штрафы класса за указанный перегруз. */
    public static StatBlock penalties(PlayerClass playerClass, int overload) {
        StatBlock stats = new StatBlock();
        if (overload <= 0) return stats;
        switch (playerClass) {
            case MAGE -> stats.add(Stat.SPELL_DAMAGE, -MAGE_SPELL_PENALTY * overload);
            case ROGUE -> stats.add(Stat.DODGE_CHANCE, -ROGUE_DODGE_PENALTY * overload).add(Stat.MOVE_SPEED, -ROGUE_SPEED_PENALTY * overload);
            case ARCHER -> stats.add(Stat.BOW_DAMAGE, -ARCHER_BOW_PENALTY * overload);
            case HUNTER -> stats.add(Stat.MINION_DAMAGE, -HUNTER_MINION_PENALTY * overload).add(Stat.MOVE_SPEED, -HUNTER_SPEED_PENALTY * overload);
            default -> { }
        }
        return stats;
    }

    /** Добавляет штрафы за чужую броню и бонус стража за латы. */
    public static void collect(Player player, RpgData data, StatBlock into) {
        PlayerClass playerClass = data.getPlayerClass();
        into.addAll(penalties(playerClass, overload(player, playerClass)));
        if (data.getSubclass() == Subclass.GUARDIAN) into.add(Stat.DAMAGE_REDUCTION, GUARDIAN_REDUCTION_PER_HEAVY * heavyPieces(player));
    }

    /** Почему умение недоступно в надетой броне; null — доступно. */
    @Nullable
    public static Component blockReason(Player player, AbilityDef def) {
        PlayerClass playerClass = RpgManager.get(player).getPlayerClass();
        if (playerClass == PlayerClass.MAGE && isStrongSpell(def) && overload(player, playerClass) >= MAGE_BLOCK_OVERLOAD) {
            return Component.translatable("message.heroicpaths.armor_blocks_spell", def.name());
        }
        if (playerClass == PlayerClass.ROGUE && def.id().equals("vanish") && heavyPieces(player) > 0) {
            return Component.translatable("message.heroicpaths.armor_blocks_vanish");
        }
        return null;
    }

    /** Сильное заклинание: заклинание или призыв с 15 уровня. */
    public static boolean isStrongSpell(AbilityDef def) {
        return (def.kind() == AbilityDef.DamageKind.SPELL || def.kind() == AbilityDef.DamageKind.MINION)
                && def.unlockLevel() >= STRONG_SPELL_LEVEL;
    }

    /** Удар в спину разбойника работает только без тяжёлой брони. */
    public static boolean backstabAllowed(Player player) {
        return heavyPieces(player) == 0;
    }

    /** Строки для вкладки «Характеристики»: вес надетой брони, перегруз, штрафы и запреты. */
    public static List<Component> describe(Player player, RpgData data) {
        List<Component> lines = new ArrayList<>();
        PlayerClass playerClass = data.getPlayerClass();
        int[] counts = new int[ArmorWeight.values().length];
        boolean any = false;
        for (EquipmentSlot slot : SLOTS) {
            ArmorWeight weight = ArmorWeight.of(player.getItemBySlot(slot));
            if (weight != null) {
                counts[weight.ordinal()]++;
                any = true;
            }
        }
        var worn = Component.empty();
        if (!any) worn.append(Component.translatable("screen.heroicpaths.armor.none"));
        boolean first = true;
        for (ArmorWeight weight : ArmorWeight.values()) {
            if (counts[weight.ordinal()] == 0) continue;
            if (!first) worn.append(", ");
            first = false;
            worn.append(weight.displayName()).append(" ×" + counts[weight.ordinal()]);
        }
        lines.add(Component.translatable("screen.heroicpaths.armor.worn", worn, ArmorWeight.proficiency(playerClass).displayName()));
        int overload = overload(player, playerClass);
        if (overload > 0) {
            lines.add(Component.translatable("screen.heroicpaths.armor.overload", overload).withStyle(ChatFormatting.RED));
            for (Component line : penalties(playerClass, overload).describe()) {
                lines.add(Component.literal(" ").append(line).withStyle(ChatFormatting.RED));
            }
            if (playerClass == PlayerClass.MAGE && overload >= MAGE_BLOCK_OVERLOAD) {
                lines.add(Component.translatable("screen.heroicpaths.armor.no_strong_spells", STRONG_SPELL_LEVEL).withStyle(ChatFormatting.RED));
            }
        }
        if (playerClass == PlayerClass.ROGUE && heavyPieces(player) > 0) {
            lines.add(Component.translatable("screen.heroicpaths.armor.no_stealth").withStyle(ChatFormatting.RED));
        }
        if (data.getSubclass() == Subclass.GUARDIAN && heavyPieces(player) > 0) {
            lines.add(Component.translatable("screen.heroicpaths.armor.guardian", Math.round(GUARDIAN_REDUCTION_PER_HEAVY * heavyPieces(player)))
                    .withStyle(ChatFormatting.GREEN));
        }
        return lines;
    }

    /** Строки подсказки брони: вес и подходит ли она классу игрока. Возвращает, сколько строк добавлено. */
    public static int addTooltip(ItemStack stack, List<Component> tooltip, int index, @Nullable Player player) {
        ArmorWeight weight = ArmorWeight.of(stack);
        if (weight == null) return 0;
        index = Math.min(index, tooltip.size());
        tooltip.add(index, Component.translatable("tooltip.heroicpaths.armor_weight", weight.displayName()).withStyle(ChatFormatting.GRAY));
        if (player == null) return 1;
        RpgData data = RpgManager.get(player);
        PlayerClass playerClass = data.getPlayerClass();
        if (playerClass == PlayerClass.NONE) return 1;
        int added = 1;
        int overload = overload(stack, playerClass);
        if (overload == 0) {
            tooltip.add(index + added++, Component.translatable("tooltip.heroicpaths.armor_fits", playerClass.displayName()).withStyle(ChatFormatting.DARK_GREEN));
        } else {
            tooltip.add(index + added++, Component.translatable("tooltip.heroicpaths.armor_foreign", playerClass.displayName()).withStyle(ChatFormatting.RED));
            for (Component line : penalties(playerClass, overload).describe()) {
                tooltip.add(index + added++, Component.literal(" ").append(line).withStyle(ChatFormatting.RED));
            }
            if (playerClass == PlayerClass.ROGUE && weight == ArmorWeight.HEAVY) {
                tooltip.add(index + added++, Component.literal(" ").append(Component.translatable("screen.heroicpaths.armor.no_stealth"))
                        .withStyle(ChatFormatting.RED));
            }
        }
        if (data.getSubclass() == Subclass.GUARDIAN && weight == ArmorWeight.HEAVY) {
            tooltip.add(index + added++, Component.translatable("tooltip.heroicpaths.armor_guardian", Math.round(GUARDIAN_REDUCTION_PER_HEAVY))
                    .withStyle(ChatFormatting.DARK_GREEN));
        }
        return added;
    }
}
