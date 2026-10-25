package com.heroicpaths.combat;

import com.heroicpaths.HeroicPaths;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Страх от «Истинной скрытности»: враг теряется, колющие удары по нему наносят втрое больше урона. */
public class FearEffect extends MobEffect {
    public FearEffect() {
        super(MobEffectCategory.HARMFUL, 0x5B2A86);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, HeroicPaths.id("fear_speed"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
