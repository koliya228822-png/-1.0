package com.heroicpaths.gear;

import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.stat.Stat;
import com.heroicpaths.rpg.stat.StatBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Комплекты (сеты): по четыре на тип брони. Бонус половины — с 4 вещей сета, полный — со всех 7
 * (шлем, нагрудник, поножи, сапоги, перчатки, пояс, наручи). Украшения в сет не входят.
 * Кроме прибавок к характеристикам у сета может быть особое свойство (см. {@link Perk}).
 */
public enum GearSet {
    // Латы
    GLADIATOR(PlayerClass.WARRIOR, StatBlock.EMPTY, Perk.RANGED_GUARD, StatBlock.of(Stat.MELEE_DAMAGE, 10), Perk.NO_SLOWNESS),
    BASTION(PlayerClass.WARRIOR, StatBlock.of(Stat.DAMAGE_REDUCTION, 5, Stat.MAX_HEALTH, 4), null,
            StatBlock.of(Stat.DAMAGE_REDUCTION, 5, Stat.THORNS, 3), Perk.NO_KNOCKBACK),
    BERSERKER(PlayerClass.WARRIOR, StatBlock.of(Stat.ATTACK_SPEED, 8), null, StatBlock.of(Stat.LIFE_STEAL, 3), Perk.BLOODLUST),
    CRUSADER(PlayerClass.WARRIOR, StatBlock.of(Stat.HEALING, 10, Stat.MAX_HEALTH, 4), null,
            StatBlock.of(Stat.HOLY_DAMAGE, 15), Perk.PURITY),
    // Кожа
    PATHFINDER(PlayerClass.ARCHER, StatBlock.of(Stat.BOW_DAMAGE, 10), null, StatBlock.of(Stat.CRIT_CHANCE, 8, Stat.MOVE_SPEED, 8), null),
    SHADOW(PlayerClass.ARCHER, StatBlock.of(Stat.DODGE_CHANCE, 6), null, StatBlock.of(Stat.CRIT_DAMAGE, 25), Perk.NIGHT_EYES),
    WIND(PlayerClass.ARCHER, StatBlock.of(Stat.MOVE_SPEED, 8), null, StatBlock.of(Stat.DODGE_CHANCE, 6), Perk.FEATHER_FALL),
    BEASTMASTER(PlayerClass.ARCHER, StatBlock.of(Stat.MINION_DAMAGE, 15), null, StatBlock.of(Stat.MINION_HEALTH, 25, Stat.MINION_DAMAGE, 10), null),
    // Ткань
    ARCHMAGE(PlayerClass.MAGE, StatBlock.of(Stat.SPELL_DAMAGE, 10), null, StatBlock.of(Stat.COOLDOWN_REDUCTION, 15), null),
    WARLOCK(PlayerClass.MAGE, StatBlock.of(Stat.LIFE_STEAL, 4), null, StatBlock.of(Stat.SPELL_DAMAGE, 15), Perk.NO_WITHER),
    ELEMENTALIST(PlayerClass.MAGE, StatBlock.of(Stat.FIRE_DAMAGE, 8, Stat.WATER_DAMAGE, 8, Stat.EARTH_DAMAGE, 8, Stat.AIR_DAMAGE, 8), null,
            StatBlock.EMPTY, Perk.ELEMENTAL_WARD),
    SAGE(PlayerClass.MAGE, StatBlock.of(Stat.MAX_RESOURCE, 20), null, StatBlock.of(Stat.RESOURCE_REGEN, 2, Stat.XP_GAIN, 10), null);

    /** Вещей для бонуса половины и полного бонуса. */
    public static final int HALF = 4, FULL = 7;

    /** Особые свойства сетов: их обрабатывает {@link GearSets}. */
    public enum Perk {
        /** Урон от снарядов (стрелы, болты, пули, заряды) −15%. */
        RANGED_GUARD,
        /** Невосприимчивость к замедлению. */
        NO_SLOWNESS,
        /** Не отбрасывает. */
        NO_KNOCKBACK,
        /** При здоровье ниже половины удары ближнего боя наносят +25% урона. */
        BLOODLUST,
        /** Невосприимчивость к отравлению и иссушению. */
        PURITY,
        /** Невосприимчивость к слепоте и тьме. */
        NIGHT_EYES,
        /** Нет урона от падения. */
        FEATHER_FALL,
        /** Невосприимчивость к иссушению. */
        NO_WITHER,
        /** Нет урона от огня, лавы и мороза. */
        ELEMENTAL_WARD;

        public MutableComponent describe() {
            return Component.translatable("set_perk.heroicpaths." + name().toLowerCase(Locale.ROOT));
        }
    }

    private final PlayerClass armorClass;
    private final StatBlock half, full;
    @Nullable
    private final Perk halfPerk, fullPerk;

    GearSet(PlayerClass armorClass, StatBlock half, @Nullable Perk halfPerk, StatBlock full, @Nullable Perk fullPerk) {
        this.armorClass = armorClass;
        this.half = half;
        this.halfPerk = halfPerk;
        this.full = full;
        this.fullPerk = fullPerk;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Тип брони сета: воин — латы, лучник — кожа, маг — ткань. */
    public PlayerClass armorClass() {
        return armorClass;
    }

    public StatBlock half() {
        return half;
    }

    public StatBlock full() {
        return full;
    }

    @Nullable
    public Perk halfPerk() {
        return halfPerk;
    }

    @Nullable
    public Perk fullPerk() {
        return fullPerk;
    }

    public MutableComponent displayName() {
        return Component.translatable("gear_set.heroicpaths." + id());
    }

    /** Строки бонуса: прибавки и свойство. */
    public List<Component> describe(boolean fullBonus) {
        List<Component> lines = new java.util.ArrayList<>((fullBonus ? full : half).describe());
        Perk perk = fullBonus ? fullPerk : halfPerk;
        if (perk != null) lines.add(perk.describe());
        return lines;
    }

    @Nullable
    public static GearSet byId(@Nullable String id) {
        if (id == null) return null;
        for (GearSet set : values()) if (set.id().equals(id)) return set;
        return null;
    }

    /** Сеты этого типа брони. */
    public static List<GearSet> forArmor(PlayerClass armorClass) {
        return java.util.Arrays.stream(values()).filter(set -> set.armorClass == armorClass).toList();
    }
}
