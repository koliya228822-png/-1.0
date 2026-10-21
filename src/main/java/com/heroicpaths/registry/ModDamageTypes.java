package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/** Типы урона мода (сами типы описаны в data/heroicpaths/damage_type). */
public final class ModDamageTypes {
    /** Кровотечение: мимо брони, без отбрасывания. */
    public static final ResourceKey<DamageType> BLEEDING = ResourceKey.create(Registries.DAMAGE_TYPE, HeroicPaths.id("bleeding"));

    private ModDamageTypes() {}

    public static Holder<DamageType> holder(Level level, ResourceKey<DamageType> key) {
        return level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key);
    }
}
