package com.heroicpaths.minion;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Кабан охотника. Обычный хоглин живёт «мозгом» и нападает на игроков; здесь мозг отключён,
 * а вместо него работают цели существа игрока. В верхнем мире он не превращается в зоглина.
 */
public class CompanionBoar extends Hoglin implements Minion {
    private final MinionData data = new MinionData();

    public CompanionBoar(EntityType<? extends Hoglin> type, Level level) {
        super(type, level);
        setImmuneToZombification(true);
    }

    @Override
    public MinionData minionData() {
        return data;
    }

    @Override
    protected void registerGoals() {
        MinionGoals.install(this, 1.0, true);
    }

    /** Без мозга хоглина: ни охоты на игроков, ни превращения, ни бегства от искажённых грибов. */
    @Override
    protected void customServerAiStep() {
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) Minions.tick(this);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    @Override
    public boolean canMate(net.minecraft.world.entity.animal.Animal other) {
        return false;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof LivingEntity attacker && Minions.isFriendly(this, attacker)) return false;
        return super.hurt(source, amount);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        data.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        data.load(tag);
    }
}
