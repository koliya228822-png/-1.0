package com.heroicpaths.rpg.ability;

import com.heroicpaths.rpg.stat.Element;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Описание активного умения. Каким классам и подклассам оно доступно, решают {@link com.heroicpaths.rpg.PlayerClass}
 * и {@link com.heroicpaths.rpg.Subclass}. Сила растёт с уровнем, характеристиками и рангом умения (1–3).
 *
 * @param unlockLevel   с какого уровня умение можно выучить по книге
 * @param cost          затраты ресурса до скидок
 * @param cooldownTicks перезарядка до сокращений
 * @param weapon        какое оружие нужно держать, чтобы применить умение
 * @param castTicks     время произнесения (0 — мгновенно); сильные умения срабатывают в конце каста (Casting)
 * @param unique        уникальное умение: его книгу делают только на столе зачарований 2-го уровня (UniqueAbilities)
 * @param passive       пассивное умение: действует, как только выучено, слот не занимает
 */
public record AbilityDef(String id, int unlockLevel, int cost, int cooldownTicks, Element element, DamageKind kind, WeaponReq weapon, int castTicks,
                         boolean unique, boolean passive) {
    public static final int MAX_RANK = 3;

    public enum DamageKind {
        /** Урон усиливается бонусами ближнего боя. */
        MELEE,
        /** Урон усиливается бонусами стрелкового оружия. */
        RANGED,
        /** Урон усиливается бонусами заклинаний. */
        SPELL,
        /** Урон наносят спутники и призванные существа. */
        MINION
    }

    public MutableComponent name() {
        return Component.translatable("ability.heroicpaths." + id);
    }

    public MutableComponent description() {
        return Component.translatable("ability.heroicpaths." + id + ".desc");
    }
}
