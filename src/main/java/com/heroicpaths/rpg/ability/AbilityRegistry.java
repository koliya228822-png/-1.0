package com.heroicpaths.rpg.ability;

import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.Subclass;
import com.heroicpaths.rpg.ability.AbilityDef.DamageKind;
import com.heroicpaths.rpg.stat.Element;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.heroicpaths.rpg.ability.AbilityDef.DamageKind.MELEE;
import static com.heroicpaths.rpg.ability.AbilityDef.DamageKind.MINION;
import static com.heroicpaths.rpg.ability.AbilityDef.DamageKind.RANGED;
import static com.heroicpaths.rpg.ability.AbilityDef.DamageKind.SPELL;
import static com.heroicpaths.rpg.stat.Element.AIR;
import static com.heroicpaths.rpg.stat.Element.EARTH;
import static com.heroicpaths.rpg.stat.Element.FIRE;
import static com.heroicpaths.rpg.stat.Element.HOLY;
import static com.heroicpaths.rpg.stat.Element.PHYSICAL;
import static com.heroicpaths.rpg.stat.Element.POISON;
import static com.heroicpaths.rpg.stat.Element.WATER;

/**
 * Все активные умения. Кому какое доступно — с начала игры или по книге — задают классы и подклассы;
 * уровень открытия здесь означает, с какого уровня умение можно выучить по книге.
 */
public final class AbilityRegistry {
    private static final Map<String, AbilityDef> BY_ID = new LinkedHashMap<>();

    static {
        // Воин
        add("charge", 1, 25, 160, PHYSICAL, MELEE, WeaponReq.NONE);
        add("war_cry", 10, 40, 600, PHYSICAL, MELEE, WeaponReq.NONE);
        add("cleave", 1, 25, 100, PHYSICAL, MELEE, WeaponReq.TWO_HANDED);
        add("whirlwind", 10, 35, 200, PHYSICAL, MELEE, WeaponReq.MELEE);
        add("leap_slam", 20, 40, 240, PHYSICAL, MELEE, WeaponReq.TWO_HANDED);
        add("execute", 15, 35, 200, PHYSICAL, MELEE, WeaponReq.MELEE);
        add("shield_slam", 1, 30, 160, PHYSICAL, MELEE, WeaponReq.SHIELD);
        add("taunt", 1, 25, 240, PHYSICAL, MELEE, WeaponReq.NONE);
        add("shield_wall", 15, 40, 600, PHYSICAL, MELEE, WeaponReq.SHIELD);
        add("blood_frenzy", 1, 0, 500, PHYSICAL, MELEE, WeaponReq.NONE);
        add("axe_throw", 1, 25, 80, PHYSICAL, MELEE, WeaponReq.AXE);
        add("smite", 1, 30, 160, HOLY, MELEE, WeaponReq.MELEE);
        add("holy_shield", 10, 40, 400, HOLY, MELEE, WeaponReq.NONE);
        add("lay_on_hands", 10, 50, 600, HOLY, SPELL, WeaponReq.NONE);
        add("consecration", 20, 45, 400, HOLY, MELEE, WeaponReq.NONE);
        // Маг
        add("blink", 1, 25, 120, AIR, SPELL, WeaponReq.NONE);
        add("fireball", 1, 15, 20, FIRE, SPELL, WeaponReq.FOCUS);
        add("frost_nova", 10, 30, 160, WATER, SPELL, WeaponReq.FOCUS);
        add("ice_lance", 5, 20, 60, WATER, SPELL, WeaponReq.FOCUS);
        add("meteor", 10, 45, 240, FIRE, SPELL, WeaponReq.FOCUS);
        add("stone_skin", 10, 30, 400, EARTH, SPELL, WeaponReq.NONE);
        add("flame_wave", 15, 30, 140, FIRE, SPELL, WeaponReq.FOCUS);
        add("chain_lightning", 15, 30, 100, AIR, SPELL, WeaponReq.FOCUS);
        add("earthquake", 20, 40, 220, EARTH, SPELL, WeaponReq.FOCUS);
        add("tornado", 25, 45, 300, AIR, SPELL, WeaponReq.FOCUS);
        add("ward", 1, 30, 300, HOLY, SPELL, WeaponReq.NONE);
        add("healing_rain", 1, 45, 400, WATER, SPELL, WeaponReq.NONE);
        add("haste_hymn", 10, 40, 600, AIR, SPELL, WeaponReq.NONE);
        add("entangle", 15, 35, 300, EARTH, SPELL, WeaponReq.FOCUS);
        add("purify", 20, 30, 400, WATER, SPELL, WeaponReq.NONE);
        add("raise_skeletons", 1, 40, 600, PHYSICAL, MINION, WeaponReq.FOCUS);
        add("spirit_wolves", 1, 35, 500, PHYSICAL, MINION, WeaponReq.FOCUS);
        add("empower_minions", 10, 30, 400, PHYSICAL, MINION, WeaponReq.NONE);
        add("summon_golem", 20, 60, 1200, PHYSICAL, MINION, WeaponReq.FOCUS);
        add("blood_bolt", 1, 16, 30, PHYSICAL, SPELL, WeaponReq.FOCUS);
        add("blood_nova", 1, 30, 200, PHYSICAL, SPELL, WeaponReq.FOCUS);
        add("hemorrhage", 10, 25, 240, PHYSICAL, SPELL, WeaponReq.FOCUS);
        add("sanguine_shield", 15, 0, 400, PHYSICAL, SPELL, WeaponReq.NONE);
        add("blood_pact", 25, 0, 900, PHYSICAL, SPELL, WeaponReq.NONE);
        // Лучник
        add("disengage", 1, 20, 160, PHYSICAL, RANGED, WeaponReq.NONE);
        add("hunters_mark", 10, 20, 300, PHYSICAL, RANGED, WeaponReq.NONE);
        add("volley", 1, 25, 120, PHYSICAL, RANGED, WeaponReq.BOW);
        add("aimed_shot", 1, 30, 120, PHYSICAL, RANGED, WeaponReq.BOW);
        add("poison_arrow", 5, 25, 160, POISON, RANGED, WeaponReq.NONE);
        add("arrow_rain", 10, 40, 300, PHYSICAL, RANGED, WeaponReq.BOW);
        add("rapid_fire", 15, 35, 200, PHYSICAL, RANGED, WeaponReq.BOW);
        add("thunder_arrow", 20, 30, 180, AIR, RANGED, WeaponReq.BOW);
        add("piercing_shot", 1, 30, 100, PHYSICAL, RANGED, WeaponReq.MUSKET);
        add("grapeshot", 1, 30, 140, PHYSICAL, RANGED, WeaponReq.MUSKET);
        add("steady_aim", 10, 20, 400, PHYSICAL, RANGED, WeaponReq.MUSKET);
        add("powder_keg", 15, 40, 300, FIRE, RANGED, WeaponReq.NONE);
        // Разбойник
        add("shadow_step", 1, 25, 200, PHYSICAL, MELEE, WeaponReq.NONE);
        add("smoke_bomb", 10, 30, 400, PHYSICAL, MELEE, WeaponReq.NONE);
        add("heavy_bolt", 1, 25, 120, PHYSICAL, RANGED, WeaponReq.CROSSBOW);
        add("caltrops", 1, 30, 300, PHYSICAL, RANGED, WeaponReq.NONE);
        add("bear_trap", 10, 30, 300, PHYSICAL, RANGED, WeaponReq.NONE);
        add("explosive_bolt", 15, 35, 240, FIRE, RANGED, WeaponReq.CROSSBOW);
        add("backstab", 1, 25, 100, PHYSICAL, MELEE, WeaponReq.DAGGER);
        add("flurry", 1, 30, 160, PHYSICAL, MELEE, WeaponReq.DAGGER);
        add("poison_blades", 10, 25, 500, POISON, MELEE, WeaponReq.DAGGER);
        add("fan_of_knives", 20, 35, 200, PHYSICAL, MELEE, WeaponReq.DAGGER);
        // Охотник
        add("call_companion", 1, 20, 200, PHYSICAL, MINION, WeaponReq.NONE);
        add("sic_em", 1, 20, 160, PHYSICAL, MINION, WeaponReq.NONE);
        add("mend_pet", 1, 30, 300, WATER, MINION, WeaponReq.NONE);
        add("pack_howl", 15, 35, 500, PHYSICAL, MINION, WeaponReq.NONE);

        // Этап D: новые умения классов (книги, общие для всех подклассов класса)
        add("sunder", 5, 30, 180, PHYSICAL, MELEE, WeaponReq.MELEE);
        add("battle_banner", 15, 40, 900, PHYSICAL, MELEE, WeaponReq.NONE);
        add("last_stand", 25, 40, 1200, PHYSICAL, MELEE, WeaponReq.NONE);
        add("concussive_shot", 5, 25, 160, PHYSICAL, RANGED, WeaponReq.BOW);
        add("net_shot", 10, 25, 300, PHYSICAL, RANGED, WeaponReq.NONE);
        add("eagle_eye", 20, 30, 900, PHYSICAL, RANGED, WeaponReq.NONE);
        add("mana_shield", 10, 20, 600, AIR, SPELL, WeaponReq.NONE);
        add("blizzard", 20, 50, 400, WATER, SPELL, WeaponReq.FOCUS);
        add("eviscerate", 5, 30, 160, PHYSICAL, MELEE, WeaponReq.MELEE);
        add("vanish", 10, 30, 600, PHYSICAL, MELEE, WeaponReq.NONE);
        add("feral_rage", 5, 30, 600, PHYSICAL, MINION, WeaponReq.NONE);
        add("tracking", 10, 20, 600, PHYSICAL, RANGED, WeaponReq.NONE);
        add("pounce", 15, 30, 300, PHYSICAL, MINION, WeaponReq.NONE);

        // Время каста сильных умений (тики): эффект срабатывает в конце произнесения.
        cast("meteor", 30);
        cast("summon_golem", 30);
        cast("blizzard", 25);
        cast("earthquake", 20);
        cast("tornado", 20);
        cast("healing_rain", 20);
        cast("raise_skeletons", 15);
        cast("consecration", 15);
        cast("lay_on_hands", 15);
        cast("arrow_rain", 15);
        cast("aimed_shot", 15);
        cast("chain_lightning", 12);
        cast("spirit_wolves", 12);
        cast("blood_nova", 10);
        cast("haste_hymn", 10);
        cast("purify", 10);
        cast("entangle", 10);
        cast("battle_banner", 10);
        cast("powder_keg", 10);
        cast("piercing_shot", 10);
        cast("mend_pet", 10);
        cast("pack_howl", 10);
        cast("flame_wave", 8);

        // Уникальные умения: книги делают на столе зачарований 2-го уровня (UniqueAbilities).
        unique("meteor_shower", 0, 600, FIRE, SPELL, WeaponReq.FOCUS, 0, false);
        unique("mana_surge", 0, 0, AIR, SPELL, WeaponReq.NONE, 0, true);
        unique("hunting_frenzy", 30, 800, PHYSICAL, RANGED, WeaponReq.BOW, 0, false);
        unique("critical_error", 0, 0, PHYSICAL, RANGED, WeaponReq.NONE, 0, true);
        unique("boarding_hook", 40, 400, PHYSICAL, RANGED, WeaponReq.MUSKET, 0, false);
        unique("healing_downpour", 60, 900, WATER, SPELL, WeaponReq.NONE, 20, false);
        unique("summon_dracolich", 80, 2400, PHYSICAL, MINION, WeaponReq.FOCUS, 30, false);
        unique("wake_the_beast", 50, 2400, PHYSICAL, MINION, WeaponReq.NONE, 10, false);
        unique("slaughter", 40, 1800, PHYSICAL, MELEE, WeaponReq.NONE, 0, false);
        unique("hand_of_god", 50, 1800, HOLY, MELEE, WeaponReq.NONE, 15, false);
        unique("lone_warrior", 40, 900, PHYSICAL, MELEE, WeaponReq.TWO_HANDED, 0, false);
        unique("full_steam", 30, 1200, PHYSICAL, MELEE, WeaponReq.SHIELD, 0, false);
        unique("just_getting_started", 0, 0, PHYSICAL, MELEE, WeaponReq.NONE, 0, true);
        unique("true_stealth", 40, 1200, PHYSICAL, MELEE, WeaponReq.NONE, 0, false);
        unique("cutthroat", 0, 0, PHYSICAL, MELEE, WeaponReq.NONE, 0, true);
    }

    private AbilityRegistry() {}

    private static void add(String id, int unlockLevel, int cost, int cooldownTicks, Element element, DamageKind kind, WeaponReq weapon) {
        BY_ID.put(id, new AbilityDef(id, unlockLevel, cost, cooldownTicks, element, kind, weapon, 0, false, false));
    }

    private static void cast(String id, int ticks) {
        AbilityDef d = BY_ID.get(id);
        BY_ID.put(id, new AbilityDef(d.id(), d.unlockLevel(), d.cost(), d.cooldownTicks(), d.element(), d.kind(), d.weapon(), ticks, d.unique(), d.passive()));
    }

    /** Уникальное умение (книга со стола зачарований 2-го уровня); passive — действует без слота. */
    private static void unique(String id, int cost, int cooldownTicks, Element element, DamageKind kind, WeaponReq weapon, int castTicks, boolean passive) {
        BY_ID.put(id, new AbilityDef(id, UniqueAbilities.UNLOCK_LEVEL, cost, cooldownTicks, element, kind, weapon, castTicks, true, passive));
    }

    @Nullable
    public static AbilityDef get(@Nullable String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static Collection<AbilityDef> all() {
        return Collections.unmodifiableCollection(BY_ID.values());
    }

    /** Умение известно подклассу с начала игры. */
    public static boolean isStarting(@Nullable Subclass subclass, AbilityDef def) {
        return subclass != null && subclass.startingAbilities().contains(def.id());
    }

    /** Умение подкласс может выучить по книге. */
    public static boolean isLearnable(@Nullable Subclass subclass, AbilityDef def) {
        return subclass != null && (subclass.learnableAbilities().contains(def.id()) || UniqueAbilities.learners(def).contains(subclass));
    }

    /** Уникальные умения, которые может выучить подкласс. */
    public static List<AbilityDef> uniqueFor(@Nullable Subclass subclass) {
        List<AbilityDef> result = new ArrayList<>();
        for (AbilityDef def : BY_ID.values()) if (def.unique() && UniqueAbilities.learners(def).contains(subclass)) result.add(def);
        return result;
    }

    public static List<AbilityDef> uniques() {
        return BY_ID.values().stream().filter(AbilityDef::unique).toList();
    }

    /** Все умения подкласса: сначала известные с начала, потом из книг. */
    public static List<AbilityDef> available(@Nullable Subclass subclass) {
        List<AbilityDef> result = new ArrayList<>();
        if (subclass == null) return result;
        for (String id : subclass.startingAbilities()) add(result, id);
        for (String id : subclass.learnableAbilities()) add(result, id);
        for (AbilityDef def : uniqueFor(subclass)) add(result, def.id());
        return result;
    }

    private static void add(List<AbilityDef> into, String id) {
        AbilityDef def = get(id);
        if (def != null && !into.contains(def)) into.add(def);
    }

    /** Умения, которые можно выучить только по книгам: из них состоит добыча книг. */
    public static List<AbilityDef> bookAbilities(int maxLevel) {
        List<AbilityDef> result = new ArrayList<>();
        for (AbilityDef def : BY_ID.values()) {
            if (def.unlockLevel() > maxLevel) continue;
            boolean learnableSomewhere = false;
            for (Subclass subclass : Subclass.values()) {
                if (subclass.learnableAbilities().contains(def.id())) learnableSomewhere = true;
            }
            if (learnableSomewhere) result.add(def);
        }
        return result;
    }

    /** Классы, которые могут выучить умение по книге (для подсказки книги). */
    public static List<Subclass> learners(AbilityDef def) {
        if (def.unique()) return new ArrayList<>(UniqueAbilities.learners(def));
        List<Subclass> result = new ArrayList<>();
        for (Subclass subclass : Subclass.values()) {
            if (subclass.learnableAbilities().contains(def.id())) result.add(subclass);
        }
        return result;
    }

    public static boolean isLearned(RpgData data, AbilityDef def) {
        return data.getPlayerClass() != PlayerClass.NONE && data.knows(def.id());
    }

    public static List<AbilityDef> learned(RpgData data) {
        List<AbilityDef> result = new ArrayList<>();
        for (AbilityDef def : available(data.getSubclass())) {
            if (isLearned(data, def)) result.add(def);
        }
        return result;
    }
}
