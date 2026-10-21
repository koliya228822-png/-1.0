package com.heroicpaths.entity;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.fx.Fx;
import com.heroicpaths.fx.Palette;
import com.heroicpaths.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Глиняный колосс — босс Глиняных копей, втрое выше игрока, с шестью руками и в обожжённых латах.
 * <ul>
 *   <li>Фаза 1 (здоровье выше 2/3): латы гасят 40% урона; тяжёлые удары и удар оземь по кругу.</li>
 *   <li>Фаза 2: отваливаются нагрудник, наплечники и нижняя пара рук. Латы гасят 20%; быстрее (+20%) и сильнее (+25%),
 *       швыряет глиняные валуны, лепит големов, удары замедляют.</li>
 *   <li>Фаза 3 (ниже 1/3): отваливаются шлем, пояс и средняя пара рук, открывается раскалённое ядро. Брони нет;
 *       +45% скорости, +60% урона, чаще бьёт оземь, прыгает на дальних врагов и лепит метателей и танков.</li>
 * </ul>
 * На смене фазы колосс 2 секунды неуязвим: сбрасывает части и отталкивает всех вокруг.
 */
public class ClayColossus extends Monster implements DungeonBoss {
    public static final int NONE = 0, SLAM = 1, SHED = 2, LEAP = 3, THROW = 4;
    public static final int SLAM_WINDUP = 24, SHED_TICKS = 40, LEAP_WINDUP = 10, THROW_WINDUP = 14;
    private static final float[] ARMOR = {0.4f, 0.2f, 0f};
    private static final double[] SPEED_BONUS = {0, 0.2, 0.45};
    private static final double[] DAMAGE_BONUS = {0, 0.25, 0.6};
    private static final ResourceLocation PHASE_SPEED = HeroicPaths.id("colossus_phase_speed");
    private static final ResourceLocation PHASE_DAMAGE = HeroicPaths.id("colossus_phase_damage");
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ClayColossus.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(ClayColossus.class, EntityDataSerializers.INT);

    private final BossController controller = new BossController(this, "clay_colossus", BossEvent.BossBarColor.YELLOW);
    private int actionTicks;
    private int slamCooldown = 80, throwCooldown = 60, summonCooldown = 200, leapCooldown = 100;
    private boolean airborne;
    /** Только на клиенте: тики с начала текущего действия и анимация удара руками. */
    public int clientActionTicks;
    public int attackAnimTicks;

    public ClayColossus(EntityType<? extends ClayColossus> type, Level level) {
        super(type, level);
        this.xpReward = 120;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 420.0)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.24)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_KNOCKBACK, 1.2)
                .add(Attributes.STEP_HEIGHT, 1.5)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(PHASE, 1);
        builder.define(ACTION, NONE);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true) {
            @Override
            public boolean canUse() {
                return action() == NONE && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return action() == NONE && super.canContinueToUse();
            }

            @Override
            protected int getAttackInterval() {
                return adjustedTickDelay(phase() == 3 ? 12 : phase() == 2 ? 16 : 22);
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, ClayGolem.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public int action() {
        return entityData.get(ACTION);
    }

    public BossController controller() {
        return controller;
    }

    /** Какая часть урона гасится латами в текущей фазе. */
    public float armorReduction() {
        return ARMOR[Mth.clamp(phase(), 1, 3) - 1];
    }

    private void setAction(int action) {
        entityData.set(ACTION, action);
        actionTicks = 0;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (ACTION.equals(key)) clientActionTicks = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientActionTicks++;
            if (attackAnimTicks > 0) attackAnimTicks--;
            if (phase() == 3 && random.nextInt(3) == 0) {
                // Из трещин раскалённого ядра сыплются искры.
                level().addParticle(ParticleTypes.SMALL_FLAME, getX() + (random.nextDouble() - 0.5), getY() + 3.2 + random.nextDouble(),
                        getZ() + (random.nextDouble() - 0.5), 0, 0.02, 0);
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) attackAnimTicks = 10;
        else super.handleEntityEvent(id);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        level().broadcastEntityEvent(this, (byte) 4);
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            target.setDeltaMovement(target.getDeltaMovement().add(0, 0.35, 0));
            if (phase() >= 2 && target instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, phase() - 2), this);
            }
            playSound(SoundEvents.MUD_BRICKS_BREAK, 1.5f, 0.5f);
        }
        return hit;
    }

    // ================================================================ мозг

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        controller.tickBar();
        if (nextPhase() > phase()) startShedding(nextPhase());
        if (action() != NONE) {
            tickAction();
            return;
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        double distance = Math.sqrt(distanceToSqr(target));
        slamCooldown--;
        throwCooldown--;
        summonCooldown--;
        leapCooldown--;
        int phase = phase();
        if (slamCooldown <= 0 && distance < 7 && onGround()) {
            setAction(SLAM);
            slamCooldown = phase == 3 ? 80 : phase == 2 ? 110 : 140;
        } else if (phase == 3 && leapCooldown <= 0 && distance > 7 && distance < 22 && onGround()) {
            setAction(LEAP);
            leapCooldown = 150;
        } else if (phase >= 2 && throwCooldown <= 0 && distance > 7 && hasLineOfSight(target)) {
            setAction(THROW);
            throwCooldown = phase == 3 ? 70 : 100;
        } else if (phase >= 2 && summonCooldown <= 0) {
            summonGolems();
            summonCooldown = phase == 3 ? 260 : 320;
        }
    }

    private int nextPhase() {
        float part = getHealth() / getMaxHealth();
        return part > 2f / 3f ? 1 : part > 1f / 3f ? 2 : 3;
    }

    private void tickAction() {
        actionTicks++;
        int action = action();
        if (action != LEAP || actionTicks <= LEAP_WINDUP) {
            getNavigation().stop();
            setDeltaMovement(0, getDeltaMovement().y, 0);
        }
        LivingEntity target = getTarget();
        if (target != null) getLookControl().setLookAt(target, 30f, 30f);
        ServerLevel level = (ServerLevel) level();
        switch (action) {
            case SHED -> {
                if (actionTicks % 5 == 0) {
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TERRACOTTA.defaultBlockState()),
                            getX(), getY() + 3, getZ(), 25, 1.2, 1.5, 1.2, 0.2);
                }
                if (actionTicks >= SHED_TICKS) setAction(NONE);
            }
            case SLAM -> {
                if (actionTicks % 4 == 0) telegraph(level, 6.5);
                if (actionTicks >= SLAM_WINDUP) {
                    groundImpact(level, 6.5, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.2f, 0.75);
                    setAction(NONE);
                }
            }
            case THROW -> {
                if (actionTicks >= THROW_WINDUP) {
                    if (target != null) throwBoulder(target);
                    setAction(NONE);
                }
            }
            case LEAP -> {
                if (actionTicks == LEAP_WINDUP && target != null) {
                    Vec3 to = target.position().subtract(position());
                    double flat = Math.sqrt(to.x * to.x + to.z * to.z);
                    double time = 22;
                    setDeltaMovement(to.x / time, 0.9, to.z / time);
                    hasImpulse = true;
                    airborne = true;
                    playSound(SoundEvents.RAVAGER_STEP, 2f, 0.5f);
                    if (flat < 1) setDeltaMovement(0, 0.9, 0);
                } else if (actionTicks > LEAP_WINDUP + 3 && airborne && onGround()) {
                    airborne = false;
                    groundImpact(level, 4.5, (float) getAttributeValue(Attributes.ATTACK_DAMAGE), 0.6);
                    setAction(NONE);
                } else if (actionTicks > LEAP_WINDUP + 60) {
                    airborne = false;
                    setAction(NONE);
                }
            }
            default -> setAction(NONE);
        }
    }

    /** Предупреждение об ударе оземь: круг пыли там, куда придётся удар. */
    private void telegraph(ServerLevel level, double radius) {
        Vec3 center = position().add(0, 0.2, 0);
        Fx.ring(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RED_TERRACOTTA.defaultBlockState()), center, radius,
                (int) (radius * 8), 0, 0.05);
        Fx.ring(level, net.minecraft.core.particles.ParticleTypes.CRIT, center, radius * actionTicks / SLAM_WINDUP, 24, 0, 0.02);
    }

    /** Удар оземь: урон и подброс всем игрокам на земле в радиусе. */
    private void groundImpact(ServerLevel level, double radius, float damage, double lift) {
        for (Player player : controller.playersInRadius(radius + 1)) {
            Vec3 offset = player.position().subtract(position());
            if (offset.x * offset.x + offset.z * offset.z > radius * radius || Math.abs(offset.y) > 2.5) continue;
            player.hurt(damageSources().mobAttack(this), damage);
            Vec3 push = new Vec3(offset.x, 0, offset.z).normalize().scale(0.8);
            player.setDeltaMovement(player.getDeltaMovement().add(push.x, lift, push.z));
            player.hurtMarked = true;
        }
        Fx.shockwave(level, Palette.EARTH, position().add(0, 0.2, 0), radius, 8);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TERRACOTTA.defaultBlockState()),
                getX(), getY() + 0.3, getZ(), 80, radius / 2, 0.3, radius / 2, 0.3);
        level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 3, 1.5, 0.2, 1.5, 0);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.5f, 0.6f);
        playSound(SoundEvents.ANVIL_LAND, 1.2f, 0.4f);
    }

    private void throwBoulder(LivingEntity target) {
        ClayShot boulder = new ClayShot(ModEntities.CLAY_BOULDER.get(), this, level(), (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.9f);
        boulder.setPos(getX(), getY() + getBbHeight() + 0.5, getZ());
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        double dy = target.getY() - boulder.getY() + flat * 0.25;
        boulder.shoot(dx, dy, dz, 1.2f, 2f);
        level().broadcastEntityEvent(this, (byte) 4);
        playSound(SoundEvents.WITCH_THROW, 2f, 0.4f);
        level().addFreshEntity(boulder);
    }

    private void summonGolems() {
        if (phase() >= 3) {
            controller.summon(ModEntities.CLAY_THROWER.get(), 1, 4, mob -> {});
            controller.summon(ModEntities.CLAY_BRUTE.get(), 1, 4, mob -> {});
        } else {
            controller.summon(ModEntities.CLAY_GOLEM.get(), 2, 4, mob -> {});
        }
        if (level() instanceof ServerLevel level) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CLAY.defaultBlockState()),
                    getX(), getY() + 0.5, getZ(), 40, 3, 0.3, 3, 0.1);
        }
    }

    // ================================================================ фазы

    /** Смена фазы: части лат и пара рук отваливаются, колосс неуязвим на время и отталкивает всех вокруг. */
    private void startShedding(int phase) {
        entityData.set(PHASE, phase);
        applyPhaseBonuses();
        setAction(SHED);
        if (!(level() instanceof ServerLevel level)) return;
        String[] parts = phase == 2
                ? new String[]{"arm_lower_left", "arm_lower_right", "chest_plate", "pauldron_left", "pauldron_right"}
                : new String[]{"arm_mid_left", "arm_mid_right", "helmet", "belt_plate"};
        for (String part : parts) dropPart(level, part);
        for (Player player : controller.playersInRadius(8)) {
            Vec3 push = player.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.2);
            player.setDeltaMovement(push.x, 0.5, push.z);
            player.hurtMarked = true;
        }
        playSound(SoundEvents.RAVAGER_ROAR, 3f, 0.5f);
        playSound(SoundEvents.MUD_BRICKS_BREAK, 3f, 0.4f);
        Fx.shockwave(level, Palette.EARTH, position().add(0, 0.2, 0), 8, 10);
        controller.bossEvent().setColor(phase == 3 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.YELLOW);
        controller.message("message.heroicpaths.clay_colossus_phase" + phase, phase == 3 ? ChatFormatting.DARK_RED : ChatFormatting.GOLD);
    }

    /** Где у модели висит часть (в пикселях модели: x вправо от колосса, высота от земли, z вперёд). */
    private static double[] partOffset(String part) {
        return switch (part) {
            case "arm_lower_left" -> new double[]{-7, 18, -1};
            case "arm_lower_right" -> new double[]{7, 18, -1};
            case "arm_mid_left" -> new double[]{-8, 24, -1};
            case "arm_mid_right" -> new double[]{8, 24, -1};
            case "pauldron_left" -> new double[]{-10, 36, 0};
            case "pauldron_right" -> new double[]{10, 36, 0};
            case "chest_plate" -> new double[]{0, 28, 4};
            case "helmet" -> new double[]{0, 38, 3};
            default -> new double[]{0, 15, 2}; // belt_plate
        };
    }

    private void dropPart(ServerLevel level, String part) {
        ClayDebris debris = ModEntities.CLAY_DEBRIS.get().create(level);
        if (debris == null) return;
        double[] o = partOffset(part);
        double scale = ClayColossus.MODEL_SCALE / 16.0;
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        // Модель смотрит вдоль взгляда тела; правая сторона колосса — слева от его взгляда.
        double sideX = -Math.cos(yaw), sideZ = -Math.sin(yaw);
        double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw);
        double x = getX() + (o[0] * sideX + o[2] * fwdX) * scale;
        double z = getZ() + (o[0] * sideZ + o[2] * fwdZ) * scale;
        Vec3 out = new Vec3(x - getX(), 0, z - getZ()).normalize().scale(0.25 + random.nextDouble() * 0.15);
        debris.moveTo(x, getY() + o[1] * scale, z, yBodyRot, 0);
        debris.setup(part, yBodyRot, out.add(0, 0.25, 0));
        level.addFreshEntity(debris);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TERRACOTTA.defaultBlockState()),
                x, getY() + o[1] * scale, z, 20, 0.4, 0.4, 0.4, 0.15);
    }

    /** Во сколько раз модель колосса больше своей развёртки в пикселях (рендер и отвалившиеся части). */
    public static final float MODEL_SCALE = 2.05f;

    private void applyPhaseBonuses() {
        int i = Mth.clamp(phase(), 1, 3) - 1;
        modifier(Attributes.MOVEMENT_SPEED, PHASE_SPEED, SPEED_BONUS[i]);
        modifier(Attributes.ATTACK_DAMAGE, PHASE_DAMAGE, DAMAGE_BONUS[i]);
    }

    private void modifier(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, ResourceLocation id, double amount) {
        AttributeInstance instance = getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        if (amount != 0) instance.addPermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    // ================================================================ урон и жизнь

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (DungeonBoss.ignoresDamage(source)) return false;
        if (action() == SHED && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount * (1 - armorReduction()));
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (level() instanceof ServerLevel level) {
            controller.onDeath();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.TERRACOTTA.defaultBlockState()),
                    getX(), getY() + 2.5, getZ(), 200, 1.5, 2.5, 1.5, 0.3);
            for (String part : new String[]{"arm_lower_left", "arm_mid_right"}) dropPart(level, part);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.MUD_BRICKS_STEP;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.MUD_BRICKS_HIT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        playSound(SoundEvents.RAVAGER_STEP, 1f, 0.5f);
    }

    @Override
    public float getVoicePitch() {
        return 0.5f;
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        controller.bossEvent().removePlayer(player);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        controller.bossEvent().removeAllPlayers();
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        controller.bossEvent().setName(getDisplayName());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", phase());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Phase")) entityData.set(PHASE, Mth.clamp(tag.getInt("Phase"), 1, 3));
        applyPhaseBonuses();
        controller.load(tag);
        if (phase() == 3) controller.bossEvent().setColor(BossEvent.BossBarColor.RED);
    }
}
