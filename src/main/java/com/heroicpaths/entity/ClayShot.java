package com.heroicpaths.entity;

import com.heroicpaths.registry.ModEntities;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Ком глины метателя ({@code clay_shot}) или валун колосса ({@code clay_boulder}): валун летит тяжелее
 * и при падении бьёт всех вокруг.
 */
public class ClayShot extends ThrowableItemProjectile {
    private float damage = 3f;

    public ClayShot(EntityType<? extends ClayShot> type, Level level) {
        super(type, level);
    }

    public ClayShot(EntityType<? extends ClayShot> type, LivingEntity owner, Level level, float damage) {
        super(type, owner, level);
        this.damage = damage;
    }

    public boolean isBoulder() {
        return getType() == ModEntities.CLAY_BOULDER.get();
    }

    @Override
    protected Item getDefaultItem() {
        return isBoulder() ? Items.CLAY : Items.CLAY_BALL;
    }

    @Override
    protected double getDefaultGravity() {
        return isBoulder() ? 0.05 : 0.03;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return super.canHitEntity(target) && !(target instanceof ClayGolem) && !(target instanceof ClayColossus);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (!isBoulder()) result.getEntity().hurt(damageSources().thrown(this, getOwner()), damage);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(level() instanceof ServerLevel level)) return;
        if (isBoulder()) {
            // Валун раскалывается: урон всем игрокам в радиусе 2.5 блока.
            for (Player player : level.getEntitiesOfClass(Player.class, getBoundingBox().inflate(2.5),
                    p -> !p.isSpectator() && !p.isCreative())) {
                player.hurt(damageSources().thrown(this, getOwner()), damage);
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CLAY.defaultBlockState()),
                    getX(), getY(), getZ(), 60, 1.0, 0.4, 1.0, 0.2);
            level.sendParticles(ParticleTypes.POOF, getX(), getY(), getZ(), 12, 0.8, 0.3, 0.8, 0.02);
            playSound(SoundEvents.MUD_BRICKS_BREAK, 2f, 0.5f);
        } else {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.CLAY_BALL)),
                    getX(), getY(), getZ(), 10, 0.1, 0.1, 0.1, 0.08);
            playSound(SoundEvents.MUD_HIT, 1f, 0.9f);
        }
        discard();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", damage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("Damage");
    }
}
