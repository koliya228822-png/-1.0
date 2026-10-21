package com.heroicpaths.entity;

import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Пуля мушкета: летит прямо и очень быстро, но недалеко (около 30 блоков). Это стрела для всех бонусов
 * стрелкового оружия, но её нельзя подобрать, и она исчезает при ударе о блок.
 */
public class MusketBall extends AbstractArrow implements ItemSupplier {
    /** Сколько тиков живёт пуля: при скорости 5 блоков за тик это около 30 блоков. */
    private static final int LIFETIME = 6;

    public MusketBall(EntityType<? extends MusketBall> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }

    public MusketBall(Level level, LivingEntity shooter, ItemStack musket) {
        super(ModEntities.MUSKET_BALL.get(), shooter, level, new ItemStack(ModItems.MUSKET_BALL.get()), musket);
        setNoGravity(true);
        pickup = Pickup.DISALLOWED;
    }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            if (tickCount > LIFETIME) discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, result.getLocation().x, result.getLocation().y, result.getLocation().z, 6, 0.1, 0.1, 0.1, 0.1);
        }
        discard();
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(ModItems.MUSKET_BALL.get());
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.MUSKET_BALL.get());
    }

    @Nullable
    public ItemStack weapon() {
        return getWeaponItem();
    }
}
