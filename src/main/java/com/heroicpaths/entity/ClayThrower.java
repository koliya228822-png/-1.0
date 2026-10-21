package com.heroicpaths.entity;

import com.heroicpaths.registry.ModEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.level.Level;

/** Голем-метатель: держится на расстоянии и швыряет комья глины. */
public class ClayThrower extends ClayGolem implements RangedAttackMob {
    public ClayThrower(EntityType<? extends ClayThrower> type, Level level) {
        super(type, level);
        this.xpReward = 6;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 12.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerAttackGoals() {
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 30, 50, 14f));
    }

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ClayShot shot = new ClayShot(ModEntities.CLAY_SHOT.get(), this, level(), 3f);
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double dy = target.getY(0.4) - shot.getY() + Math.sqrt(dx * dx + dz * dz) * 0.12;
        shot.shoot(dx, dy, dz, 1.1f, 6f);
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        playSound(SoundEvents.SNOWBALL_THROW, 1f, 0.6f);
        level().addFreshEntity(shot);
    }
}
