package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.combat.BleedingEffect;
import com.heroicpaths.combat.StunEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Эффекты оружия: кровотечение (режущее) и оглушение (дробящее). */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, HeroicPaths.MODID);

    public static final DeferredHolder<MobEffect, BleedingEffect> BLEEDING = EFFECTS.register("bleeding", BleedingEffect::new);
    public static final DeferredHolder<MobEffect, StunEffect> STUN = EFFECTS.register("stun", StunEffect::new);

    private ModEffects() {}
}
