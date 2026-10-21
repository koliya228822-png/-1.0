package com.heroicpaths.rpg;

import com.heroicpaths.rpg.stat.StatBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.heroicpaths.rpg.stat.Stat.*;

/**
 * Подклассы: выбираются вместе с классом и не меняются (кроме полного сброса Свитком забвения).
 * У каждого подкласса два умения с самого начала, свои книги умений и постоянные бонусы.
 */
public enum Subclass {
    // Воин
    GREATSWORD(PlayerClass.WARRIOR, List.of("cleave", "whirlwind"), List.of("leap_slam", "execute"),
            StatBlock.of(MELEE_DAMAGE, 10, CRIT_DAMAGE, 15)),
    GUARDIAN(PlayerClass.WARRIOR, List.of("shield_slam", "taunt"), List.of("shield_wall", "holy_shield"),
            StatBlock.of(ARMOR, 4, DAMAGE_REDUCTION, 8, THORNS, 1), "guardian"),
    BARBARIAN(PlayerClass.WARRIOR, List.of("blood_frenzy", "axe_throw"), List.of("whirlwind", "execute"),
            StatBlock.of(AXE_DAMAGE, 15, LIFE_STEAL, 2, ATTACK_SPEED, 5), "berserker"),
    PALADIN(PlayerClass.WARRIOR, List.of("smite", "holy_shield"), List.of("lay_on_hands", "consecration"),
            StatBlock.of(HOLY_DAMAGE, 15, HEALING, 15, MAX_HEALTH, 10), "paladin"),
    // Маг
    ELEMENTALIST(PlayerClass.MAGE, List.of("fireball", "frost_nova"),
            List.of("ice_lance", "meteor", "stone_skin", "flame_wave", "chain_lightning", "earthquake", "tornado", "mana_shield"),
            StatBlock.of(SPELL_DAMAGE, 10, FIRE_DAMAGE, 5, WATER_DAMAGE, 5, EARTH_DAMAGE, 5, AIR_DAMAGE, 5),
            "pyromancer", "hydromancer", "geomancer", "aeromancer"),
    SUPPORT(PlayerClass.MAGE, List.of("ward", "healing_rain"), List.of("haste_hymn", "entangle", "purify", "frost_nova", "mana_shield"),
            StatBlock.of(HEALING, 25, COOLDOWN_REDUCTION, 5, MAX_RESOURCE, 20)),
    SUMMONER(PlayerClass.MAGE, List.of("raise_skeletons", "spirit_wolves"), List.of("empower_minions", "summon_golem", "frost_nova", "mana_shield"),
            StatBlock.of(MINION_DAMAGE, 20, MINION_HEALTH, 20, MAX_RESOURCE, 10)),
    BLOOD_MAGE(PlayerClass.MAGE, List.of("blood_bolt", "blood_nova"), List.of("hemorrhage", "sanguine_shield", "blood_pact"),
            StatBlock.of(SPELL_DAMAGE, 10, LIFE_STEAL, 3, MAX_HEALTH, 10)),
    // Лучник
    RANGER(PlayerClass.ARCHER, List.of("volley", "aimed_shot"), List.of("poison_arrow", "arrow_rain", "rapid_fire", "thunder_arrow"),
            StatBlock.of(BOW_DAMAGE, 10, MOVE_SPEED, 4), "sniper", "ranger", "stormcaller"),
    MUSKETEER(PlayerClass.ARCHER, List.of("piercing_shot", "grapeshot"), List.of("steady_aim", "powder_keg"),
            StatBlock.of(BOW_DAMAGE, 15, CRIT_DAMAGE, 25)),
    // Разбойник
    CROSSBOWMAN(PlayerClass.ROGUE, List.of("heavy_bolt", "caltrops"), List.of("bear_trap", "explosive_bolt"),
            StatBlock.of(BOW_DAMAGE, 10, ARMOR, 2, CRIT_CHANCE, 3)),
    DUELIST(PlayerClass.ROGUE, List.of("backstab", "flurry"), List.of("poison_blades", "fan_of_knives"),
            StatBlock.of(MELEE_DAMAGE, 8, CRIT_CHANCE, 6, ATTACK_SPEED, 8)),
    // Охотник
    BEASTMASTER(PlayerClass.HUNTER, List.of("sic_em", "mend_pet"), List.of("poison_arrow", "bear_trap", "pack_howl"),
            StatBlock.of(MINION_DAMAGE, 25, MINION_HEALTH, 25, MOVE_SPEED, 3));

    private final PlayerClass playerClass;
    private final List<String> baseAbilities;
    private final List<String> bookAbilities;
    private final StatBlock passive;
    /** Специализации прошлой версии мода, которые переходят в этот подкласс. */
    private final List<String> legacySpecs;

    Subclass(PlayerClass playerClass, List<String> baseAbilities, List<String> bookAbilities, StatBlock passive, String... legacySpecs) {
        this.playerClass = playerClass;
        this.baseAbilities = baseAbilities;
        this.bookAbilities = bookAbilities;
        this.passive = passive;
        this.legacySpecs = List.of(legacySpecs);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public PlayerClass playerClass() {
        return playerClass;
    }

    /** Умения подкласса, известные с начала игры (без умения класса). */
    public List<String> baseAbilities() {
        return baseAbilities;
    }

    /** Умения подкласса из книг (без общих книг класса). */
    public List<String> bookAbilities() {
        return bookAbilities;
    }

    /** Все умения, известные с начала: сначала свои, потом умение класса. */
    public List<String> startingAbilities() {
        List<String> result = new ArrayList<>(baseAbilities);
        result.addAll(playerClass.baseAbilities());
        return result;
    }

    /** Все умения, которые подкласс может выучить по книгам. */
    public List<String> learnableAbilities() {
        List<String> result = new ArrayList<>(bookAbilities);
        for (String id : playerClass.bookAbilities()) if (!result.contains(id)) result.add(id);
        return result;
    }

    public StatBlock passive() {
        return passive;
    }

    public MutableComponent displayName() {
        return Component.translatable("subclass.heroicpaths." + id());
    }

    public MutableComponent description() {
        return Component.translatable("subclass.heroicpaths." + id() + ".desc");
    }

    /** Кровь вместо маны: умения стоят здоровья. */
    public boolean usesHealth() {
        return this == BLOOD_MAGE;
    }

    public static List<Subclass> forClass(PlayerClass playerClass) {
        List<Subclass> result = new ArrayList<>();
        for (Subclass subclass : values()) {
            if (subclass.playerClass == playerClass) result.add(subclass);
        }
        return result;
    }

    @Nullable
    public static Subclass byId(String id) {
        for (Subclass subclass : values()) {
            if (subclass.id().equals(id)) return subclass;
        }
        return null;
    }

    /** Подкласс по специализации старой версии мода. */
    @Nullable
    public static Subclass fromLegacySpec(String spec) {
        for (Subclass subclass : values()) {
            if (subclass.legacySpecs.contains(spec)) return subclass;
        }
        return null;
    }
}
