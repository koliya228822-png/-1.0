package com.heroicpaths.rpg;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Жёсткие классы без смешивания. У каждого класса есть умение, известное с самого начала,
 * и общие для всех его подклассов книги умений.
 */
public enum PlayerClass {
    NONE("none", 0xAAAAAA, 0x888888, 0f, 0f, 0f, List.of(), List.of()),
    WARRIOR("warrior", 0xE0503C, 0xE8A040, 4f, 2.5f, 1f, List.of("charge"), List.of("war_cry", "sunder", "battle_banner", "last_stand")),
    ARCHER("archer", 0x6BCB4F, 0xE6D84A, 5f, 1.6f, 0.7f, List.of("disengage"), List.of("hunters_mark", "concussive_shot", "net_shot", "eagle_eye")),
    MAGE("mage", 0x9C8BFF, 0x3C8CFF, 3f, 1.2f, 0.5f, List.of("blink"), List.of("blizzard")),
    ROGUE("rogue", 0x8E8E9E, 0xC8C850, 5f, 1.8f, 0.7f, List.of("shadow_step"), List.of("smoke_bomb", "eviscerate", "vanish")),
    HUNTER("hunter", 0xB07A3A, 0x9AD050, 5f, 2f, 0.8f, List.of("call_companion"), List.of("feral_rage", "tracking", "pounce"));

    /** Мана мага за каждый уровень после первого. */
    public static final float MANA_PER_LEVEL = 3f;
    /** Выносливость бойцов за каждый уровень после первого. */
    public static final float STAMINA_PER_LEVEL = 1f;

    private final String id;
    private final int color;
    private final int resourceColor;
    private final float resourceRegen;
    private final float healthPerLevel;
    private final float reductionFactor;
    private final List<String> baseAbilities;
    private final List<String> bookAbilities;

    PlayerClass(String id, int color, int resourceColor, float resourceRegen, float healthPerLevel, float reductionFactor,
                List<String> baseAbilities, List<String> bookAbilities) {
        this.id = id;
        this.healthPerLevel = healthPerLevel;
        this.reductionFactor = reductionFactor;
        this.color = color;
        this.resourceColor = resourceColor;
        this.resourceRegen = resourceRegen;
        this.baseAbilities = baseAbilities;
        this.bookAbilities = bookAbilities;
    }

    public String id() {
        return id;
    }

    public int color() {
        return color;
    }

    public int resourceColor() {
        return resourceColor;
    }

    /** Восстановление ресурса в секунду до бонусов. */
    public float resourceRegen() {
        return resourceRegen;
    }

    /** Здоровье за каждый уровень после первого: у воина больше всех, у мага меньше всех. */
    public float healthPerLevel() {
        return healthPerLevel;
    }

    /** Ресурс класса за каждый уровень после первого: мана мага растёт быстрее выносливости бойцов. */
    public float resourcePerLevel() {
        return this == MAGE ? MANA_PER_LEVEL : this == NONE ? 0f : STAMINA_PER_LEVEL;
    }

    /** Сила узлов снижения урона в дереве: у воина полная, у мага вдвое меньше. */
    public float reductionFactor() {
        return reductionFactor;
    }

    /** Умения, известные с начала игры. */
    public List<String> baseAbilities() {
        return baseAbilities;
    }

    /** Умения из книг, доступные всем подклассам класса. */
    public List<String> bookAbilities() {
        return bookAbilities;
    }

    public MutableComponent displayName() {
        return Component.translatable("class.heroicpaths." + id);
    }

    public MutableComponent role() {
        return Component.translatable("class.heroicpaths." + id + ".role");
    }

    public MutableComponent description() {
        return Component.translatable("class.heroicpaths." + id + ".desc");
    }

    /** Врождённая особенность класса (см. ClassTraits): название. */
    public MutableComponent traitName() {
        return Component.translatable("class.heroicpaths." + id + ".trait");
    }

    /** Врождённая особенность класса: что она делает. */
    public MutableComponent traitDescription() {
        return Component.translatable("class.heroicpaths." + id + ".trait.desc");
    }

    /** Выносливость воина, энергия лучника, разбойника и охотника, мана мага. */
    public MutableComponent resourceName() {
        return Component.translatable("resource.heroicpaths." + id);
    }

    /** Классы, которые можно выбрать. */
    public static List<PlayerClass> playable() {
        return List.of(WARRIOR, MAGE, ARCHER, ROGUE, HUNTER);
    }

    public static PlayerClass byId(String id) {
        for (PlayerClass c : values()) {
            if (c.id.equals(id)) return c;
        }
        return NONE;
    }

    public static PlayerClass byOrdinal(int ordinal) {
        PlayerClass[] values = values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : NONE;
    }
}
