package com.heroicpaths.entity;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.fx.Fx;
import com.heroicpaths.fx.Palette;
import com.heroicpaths.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Матерь выводка — босс Паучьего логова: огромная мохнатая паучиха с коконом и гроздьями яиц на спине.
 * <ul>
 *   <li>Фаза 1 «Охотница» (здоровье выше 2/3): ядовитый укус, веер из трёх ядовитых плевков, ядовитая паутина
 *       вокруг себя (круг-предупреждение), зовёт пауков выводка.</li>
 *   <li>Фаза 2 «Кладка»: вылупляется половина яиц на спине. Быстрее и сильнее (+15%), стреляет сковывающей паутиной,
 *       откладывает на пол кладки — их надо разбить, пока не вылупились пауки; зовёт плевальщиков.</li>
 *   <li>Фаза 3 «Ярость» (ниже 1/3): кокон раскалывается, вылупляются последние яйца, светится нутро. +40% скорости и урона,
 *       веер из пяти плевков, прыжки на дальних врагов с ядовитым облаком на месте приземления, зовёт толстобрюха.</li>
 * </ul>
 * На смене фазы матерь 2 секунды неуязвима: встаёт на дыбы, визжит и отталкивает всех вокруг.
 */
public class SpiderQueen extends Monster implements DungeonBoss {
    public static final int NONE = 0, NOVA = 1, SPIT = 2, WEB = 3, LAY = 4, LEAP = 5, SHED = 6;
    public static final int NOVA_WINDUP = 22, SPIT_WINDUP = 12, WEB_WINDUP = 14, LAY_WINDUP = 24, LEAP_WINDUP = 12, SHED_TICKS = 40;
    /** Во сколько раз модель больше своей развёртки (рендер). */
    public static final float MODEL_SCALE = 1.4f;
    private static final double[] SPEED_BONUS = {0, 0.15, 0.4};
    private static final double[] DAMAGE_BONUS = {0, 0.15, 0.4};
    private static final ResourceLocation PHASE_SPEED = HeroicPaths.id("broodmother_phase_speed");
    private static final ResourceLocation PHASE_DAMAGE = HeroicPaths.id("broodmother_phase_damage");
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(SpiderQueen.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(SpiderQueen.class, EntityDataSerializers.INT);

    private final BossController controller = new BossController(this, "spider_queen", BossEvent.BossBarColor.PURPLE);
    private int actionTicks;
    private int novaCooldown = 100, spitCooldown = 50, webCooldown = 80, layCooldown = 120, leapCooldown = 80, summonCooldown = 160;
    private boolean airborne;
    /** Только на клиенте: тики с начала текущего действия и анимация укуса. */
    public int clientActionTicks;
    public int biteTicks;

    public SpiderQueen(EntityType<? extends SpiderQueen> type, Level level) {
        super(type, level);
        this.xpReward = 120;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 320.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
                .add(Attributes.STEP_HEIGHT, 1.2)
                .add(Attributes.FOLLOW_RANGE, 40.0);
    }

    public BossController controller() {
        return controller;
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
                return adjustedTickDelay(phase() == 3 ? 12 : phase() == 2 ? 16 : 20);
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, BroodSpider.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    public int phase() {
        return entityData.get(PHASE);
    }

    public int action() {
        return entityData.get(ACTION);
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
            if (biteTicks > 0) biteTicks--;
            if (phase() == 3 && random.nextInt(3) == 0) {
                // Из расколотого кокона сочится ядовитое свечение.
                level().addParticle(ParticleTypes.HAPPY_VILLAGER, getX() + (random.nextDouble() - 0.5) * 1.6, getY() + 2.4 + random.nextDouble() * 0.4,
                        getZ() + (random.nextDouble() - 0.5) * 1.6, 0, 0.02, 0);
            }
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 4) biteTicks = 10;
        else super.handleEntityEvent(id);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        level().broadcastEntityEvent(this, (byte) 4);
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, 60 + 20 * phase(), phase() >= 3 ? 1 : 0), this);
        }
        return hit;
    }

    // ================================================================ мозг

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        controller.tickBar();
        if (nextPhase() > phase()) startPhase(nextPhase());
        if (action() != NONE) {
            tickAction();
            return;
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        double distance = Math.sqrt(distanceToSqr(target));
        int phase = phase();
        novaCooldown--;
        spitCooldown--;
        webCooldown--;
        layCooldown--;
        leapCooldown--;
        summonCooldown--;
        if (novaCooldown <= 0 && distance < 5.5) {
            setAction(NOVA);
            novaCooldown = phase == 3 ? 100 : phase == 2 ? 130 : 160;
        } else if (phase == 3 && leapCooldown <= 0 && distance > 6 && distance < 20 && onGround()) {
            setAction(LEAP);
            leapCooldown = 120;
        } else if (phase >= 2 && layCooldown <= 0) {
            setAction(LAY);
            layCooldown = phase == 3 ? 260 : 220;
        } else if (phase >= 2 && webCooldown <= 0 && distance > 4 && hasLineOfSight(target)) {
            setAction(WEB);
            webCooldown = phase == 3 ? 90 : 110;
        } else if (spitCooldown <= 0 && distance > 4.5 && hasLineOfSight(target)) {
            setAction(SPIT);
            spitCooldown = phase == 3 ? 50 : phase == 2 ? 60 : 70;
        } else if (summonCooldown <= 0) {
            summonBrood();
            summonCooldown = phase == 3 ? 360 : phase == 2 ? 300 : 260;
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
                    Fx.burst(level, Palette.POISON, position().add(0, 2, 0), 1.2);
                    level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 2.2, getZ(), 20, 1, 0.6, 1, 0.15);
                }
                if (actionTicks >= SHED_TICKS) setAction(NONE);
            }
            case NOVA -> {
                if (actionTicks % 4 == 0) telegraph(level, 6);
                if (actionTicks >= NOVA_WINDUP) {
                    webNova(level, 6);
                    setAction(NONE);
                }
            }
            case SPIT -> {
                if (actionTicks >= SPIT_WINDUP) {
                    if (target != null) spitFan(target, phase() == 3 ? 5 : 3);
                    setAction(NONE);
                }
            }
            case WEB -> {
                if (actionTicks >= WEB_WINDUP) {
                    if (target != null) webVolley(target);
                    setAction(NONE);
                }
            }
            case LAY -> {
                if (actionTicks % 6 == 0) Fx.embers(level, Palette.POISON, position().add(0, 1, 0), 6, 0.8, 0.03);
                if (actionTicks >= LAY_WINDUP) {
                    layEggs(level, target, phase() == 3 ? 3 : 2);
                    setAction(NONE);
                }
            }
            case LEAP -> {
                if (actionTicks == LEAP_WINDUP && target != null) {
                    Vec3 to = target.position().subtract(position());
                    double time = 20;
                    setDeltaMovement(to.x / time, 0.85, to.z / time);
                    hasImpulse = true;
                    airborne = true;
                    playSound(SoundEvents.SPIDER_AMBIENT, 2f, 0.4f);
                } else if (actionTicks > LEAP_WINDUP + 3 && airborne && onGround()) {
                    airborne = false;
                    land(level);
                    setAction(NONE);
                } else if (actionTicks > LEAP_WINDUP + 60) {
                    airborne = false;
                    setAction(NONE);
                }
            }
            default -> setAction(NONE);
        }
    }

    /** Предупреждение о паутине вокруг: белый круг и растущее ядовитое кольцо. */
    private void telegraph(ServerLevel level, double radius) {
        Vec3 center = position().add(0, 0.2, 0);
        Fx.ring(level, ParticleTypes.WHITE_ASH, center, radius, (int) (radius * 10), 0, 0.02);
        Fx.ring(level, ParticleTypes.SNEEZE, center, radius * actionTicks / NOVA_WINDUP, 24, 0, 0.01);
    }

    /** Ядовитая паутина: урон, яд и сильное замедление всем рядом. */
    private void webNova(ServerLevel level, double radius) {
        for (Player player : controller.playersInRadius(radius)) {
            player.hurt(damageSources().indirectMagic(this, this), 6f + 2 * (phase() - 1));
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), this);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), this);
        }
        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2 * i / 32;
            level.sendParticles(ParticleTypes.SNEEZE, getX() + Math.cos(angle) * radius * 0.7, getY() + 0.3, getZ() + Math.sin(angle) * radius * 0.7,
                    3, 0.4, 0.1, 0.4, 0.01);
        }
        Fx.shockwave(level, Palette.POISON, position().add(0, 0.2, 0), radius, 8);
        playSound(SoundEvents.SPIDER_AMBIENT, 2f, 0.5f);
        playSound(SoundEvents.SLIME_SQUISH, 2f, 0.6f);
    }

    /** Веер ядовитых плевков из пасти. */
    private void spitFan(LivingEntity target, int count) {
        for (int i = 0; i < count; i++) {
            float angle = (i - (count - 1) / 2f) * 11f;
            SpellProjectile spit = new SpellProjectile(level(), this, 5f, SpellProjectile.Kind.POISON);
            spit.setPos(mouth());
            Vec3 dir = new Vec3(target.getX() - spit.getX(), target.getY(0.5) - spit.getY(), target.getZ() - spit.getZ())
                    .yRot((float) Math.toRadians(angle));
            spit.shoot(dir.x, dir.y, dir.z, 1.2f, 1f);
            level().addFreshEntity(spit);
        }
        level().broadcastEntityEvent(this, (byte) 4);
        playSound(SoundEvents.LLAMA_SPIT, 1.5f, 0.6f);
    }

    /** Залп сковывающей паутины: три сгустка, попавший почти не может двигаться. */
    private void webVolley(LivingEntity target) {
        for (int i = -1; i <= 1; i++) {
            SpellProjectile web = new SpellProjectile(level(), this, 2f, SpellProjectile.Kind.WEB);
            web.setPos(mouth());
            Vec3 dir = new Vec3(target.getX() - web.getX(), target.getY(0.5) - web.getY(), target.getZ() - web.getZ())
                    .yRot((float) Math.toRadians(i * 16));
            web.shoot(dir.x, dir.y + dir.horizontalDistance() * 0.05, dir.z, 1.0f, 2f);
            level().addFreshEntity(web);
        }
        playSound(SoundEvents.SPIDER_HURT, 1.5f, 0.5f);
    }

    /** Точка у жвал, откуда летят плевки. */
    private Vec3 mouth() {
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(getX() - Mth.sin(yaw) * 1.5, getY() + 0.9, getZ() + Mth.cos(yaw) * 1.5);
    }

    /** Откладывает кладки рядом с собой и с целью (не больше четырёх разом). */
    private void layEggs(ServerLevel level, @Nullable LivingEntity target, int count) {
        int existing = level.getEntitiesOfClass(SpiderEggSac.class, getBoundingBox().inflate(24)).size();
        for (int i = 0; i < Math.min(count, 4 - existing); i++) {
            SpiderEggSac sac = ModEntities.SPIDER_EGG_SAC.get().create(level);
            if (sac == null) continue;
            Vec3 center = target != null && i % 2 == 1 ? target.position() : position();
            for (int attempt = 0; attempt < 8; attempt++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = 2.5 + random.nextDouble() * 2.5;
                sac.moveTo(center.x + Math.cos(angle) * distance, getY(), center.z + Math.sin(angle) * distance, random.nextFloat() * 360f, 0);
                if (level.noCollision(sac) && !level.getBlockState(sac.blockPosition().below()).isAir()) break;
            }
            sac.addTag(BossController.MINION_TAG);
            level.addFreshEntity(sac);
            level.sendParticles(ParticleTypes.ITEM_SLIME, sac.getX(), sac.getY() + 0.3, sac.getZ(), 15, 0.3, 0.2, 0.3, 0.05);
        }
        playSound(SoundEvents.SLIME_SQUISH, 2f, 0.5f);
    }

    /** Приземление после прыжка: удар по всем рядом и ядовитое облако на месте. */
    private void land(ServerLevel level) {
        for (Player player : controller.playersInRadius(4.5)) {
            if (Math.abs(player.getY() - getY()) > 2.5) continue;
            player.hurt(damageSources().mobAttack(this), (float) getAttributeValue(Attributes.ATTACK_DAMAGE));
            Vec3 push = player.position().subtract(position()).multiply(1, 0, 1).normalize().scale(0.7);
            player.setDeltaMovement(player.getDeltaMovement().add(push.x, 0.4, push.z));
            player.hurtMarked = true;
        }
        AreaEffectCloud cloud = new AreaEffectCloud(level, getX(), getY(), getZ());
        cloud.setOwner(this);
        cloud.setRadius(3.5f);
        cloud.setRadiusOnUse(-0.2f);
        cloud.setWaitTime(10);
        cloud.setDuration(120);
        cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
        cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
        level.addFreshEntity(cloud);
        Fx.shockwave(level, Palette.POISON, position().add(0, 0.2, 0), 4.5, 8);
        playSound(SoundEvents.RAVAGER_STEP, 2f, 0.6f);
        playSound(SoundEvents.SLIME_SQUISH, 2f, 0.4f);
    }

    private void summonBrood() {
        switch (phase()) {
            case 1 -> controller.summon(ModEntities.BROOD_SPIDER.get(), 2, 4, mob -> {});
            case 2 -> {
                controller.summon(ModEntities.VENOM_SPITTER.get(), 1, 5, mob -> {});
                controller.summon(ModEntities.BROOD_SPIDER.get(), 1, 5, mob -> {});
            }
            default -> controller.summon(ModEntities.BROOD_TANK.get(), 1, 5, mob -> {});
        }
    }

    // ================================================================ фазы

    /** Смена фазы: яйца на спине вылупляются, матерь неуязвима, встаёт на дыбы и отталкивает всех вокруг. */
    private void startPhase(int phase) {
        entityData.set(PHASE, phase);
        applyPhaseBonuses();
        setAction(SHED);
        if (!(level() instanceof ServerLevel level)) return;
        if (phase == 2) controller.summon(ModEntities.BROOD_SPIDER.get(), 3, 6, mob -> {});
        else controller.summon(ModEntities.VENOM_SPITTER.get(), 2, 6, mob -> {});
        for (Player player : controller.playersInRadius(8)) {
            Vec3 push = player.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.1);
            player.setDeltaMovement(push.x, 0.45, push.z);
            player.hurtMarked = true;
        }
        playSound(SoundEvents.SPIDER_DEATH, 3f, 0.4f);
        playSound(SoundEvents.TURTLE_EGG_HATCH, 3f, 0.6f);
        Fx.shockwave(level, Palette.POISON, position().add(0, 0.2, 0), 8, 10);
        controller.bossEvent().setColor(phase == 3 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.GREEN);
        controller.message("message.heroicpaths.spider_queen_phase" + phase, phase == 3 ? ChatFormatting.DARK_RED : ChatFormatting.DARK_GREEN);
    }

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
        return super.hurt(source, amount);
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motion) {
        if (!state.is(Blocks.COBWEB)) super.makeStuckInBlock(state, motion);
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
            Fx.burst(level, Palette.POISON, position().add(0, 1.5, 0), 2);
            level.sendParticles(ParticleTypes.ITEM_SLIME, getX(), getY() + 1.5, getZ(), 80, 1.2, 0.8, 1.2, 0.2);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SPIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SPIDER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SPIDER_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SPIDER_STEP, 0.6f, 0.5f);
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
        if (phase() >= 2) controller.bossEvent().setColor(phase() == 3 ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.GREEN);
    }
}
