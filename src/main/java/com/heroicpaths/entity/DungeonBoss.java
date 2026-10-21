package com.heroicpaths.entity;

import com.heroicpaths.minion.Minion;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;

/** Метка босса данжа: его смерть в измерении данжей завершает проход и контракт. */
public interface DungeonBoss {
    /**
     * Урон, который босс игнорирует: от его собственных слуг и других монстров.
     * Существа игроков (звери, скелеты, волки, голем) ранят босса как обычно.
     */
    static boolean ignoresDamage(DamageSource source) {
        return source.getEntity() instanceof Mob && !(source.getEntity() instanceof Minion);
    }
}
