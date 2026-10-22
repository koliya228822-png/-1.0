package com.heroicpaths.entity;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.level.Level;

/** Паук-плевальщик: держится на расстоянии и плюётся ядом из светящегося мешка на брюшке. */
public class VenomSpitter extends BroodSpider implements RangedAttackMob {
    public VenomSpitter(EntityType<? extends VenomSpitter> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 14.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerAttackGoals() {
        goalSelector.addGoal(3, new RangedAttackGoal(this, 1.0, 30, 50, 14f));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        SpellProjectile spit = new SpellProjectile(level(), this, 3.5f, SpellProjectile.Kind.POISON);
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double dy = target.getY(0.4) - spit.getY() + Math.sqrt(dx * dx + dz * dz) * 0.1;
        spit.shoot(dx, dy, dz, 1.1f, 5f);
        swing(InteractionHand.MAIN_HAND);
        playSound(SoundEvents.LLAMA_SPIT, 1f, 1.3f);
        level().addFreshEntity(spit);
    }
}
