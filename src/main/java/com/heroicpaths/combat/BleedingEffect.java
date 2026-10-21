package com.heroicpaths.combat;

import com.heroicpaths.registry.ModDamageTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * Кровотечение от режущего оружия: каждую секунду 1 урон за уровень эффекта, мимо брони.
 * Урон засчитывается нанёсшему рану: если жертва истечёт кровью, опыт и добыча достанутся ему.
 */
public class BleedingEffect extends MobEffect {
    public static final String SOURCE_KEY = "heroicpaths.bleed_by";

    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8A0A0A);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level() instanceof ServerLevel level) {
            Entity attacker = null;
            if (entity.getPersistentData().hasUUID(SOURCE_KEY)) {
                UUID id = entity.getPersistentData().getUUID(SOURCE_KEY);
                attacker = level.getEntity(id);
            }
            entity.hurt(new DamageSource(ModDamageTypes.holder(level, ModDamageTypes.BLEEDING), null, attacker), 1f + amplifier);
        }
        return true;
    }
}
