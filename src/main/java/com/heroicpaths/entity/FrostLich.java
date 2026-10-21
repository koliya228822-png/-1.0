package com.heroicpaths.entity;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.fx.Fx;
import com.heroicpaths.fx.Palette;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.rpg.ability.DelayedTasks;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ледяной Лич — босс Ледяной цитадели, внешне в стиле Аинза Оал Гоуна из «Повелителя»: парящий скелет в чёрной
 * мантии с золотом, с посохом и красной сферой в рёбрах.
 * <ul>
 *   <li>Фаза 1 (здоровье выше 2/3): только дальние атаки — ледяные стрелы, каждая третья — веером из трёх.
 *       Ледяные доспехи гасят 30% урона.</li>
 *   <li>Фаза 2: спадают наплечники, золотой воротник и нагрудная накидка. Добавляется магия: Ледяная вспышка — круг
 *       под врагом взрывается льдом, Цепной мороз — скачет между игроками, призыв банши и замороженного лучника.
 *       Стрелы веером из трёх, быстрее (+15%).</li>
 *   <li>Фаза 3 (ниже 1/3): рвётся пояс, Лич окутан вьюгой. Ещё больше магии: Буран — град ледяных глыб по меткам
 *       вокруг каждого игрока, Цепной мороз скачет 7 раз, Морозная аура замедляет и ранит рядом, Скачок прочь от
 *       подошедших вплотную, призыв ледяных слуг — голема и снеговика. Стрелы веером из пяти, урон +40%.</li>
 * </ul>
 * На смене фазы Лич 1.5 секунды неуязвим: доспехи разлетаются осколками, всех вокруг отбрасывает.
 */
public class FrostLich extends Monster implements DungeonBoss, RangedAttackMob {
    public static final int NONE = 0, CAST = 1, SHATTER = 2;
    public static final int CAST_TICKS = 16, SHATTER_TICKS = 30;
    private static final int NOVA = 1, CHAIN = 2, BLIZZARD = 3;
    private static final float[] ARMOR = {0.3f, 0.15f, 0f};
    private static final double[] SPEED_BONUS = {0, 0.15, 0.3};
    private static final double[] DAMAGE_BONUS = {0, 0.15, 0.4};
    private static final ResourceLocation PHASE_SPEED = HeroicPaths.id("lich_phase_speed");
    private static final ResourceLocation PHASE_DAMAGE = HeroicPaths.id("lich_phase_damage");
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(FrostLich.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(FrostLich.class, EntityDataSerializers.INT);
    /** Во сколько раз модель Лича больше своей развёртки (рендер и разлетающиеся доспехи). */
    public static final float MODEL_SCALE = 1.2f;

    private final BossController controller = new BossController(this, "frost_lich", BossEvent.BossBarColor.BLUE);
    private int actionTicks;
    private int spell;
    private int shots;
    private int novaCooldown = 60, chainCooldown = 120, blizzardCooldown = 80, summonCooldown = 200, blinkCooldown = 60;
    /** Только на клиенте: тики с начала текущего действия. */
    public int clientActionTicks;

    public FrostLich(EntityType<? extends FrostLich> type, Level level) {
        super(type, level);
        this.xpReward = 220;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 650.0)
                .add(Attributes.ATTACK_DAMAGE, 9.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
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
        goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 30, 50, 18f) {
            @Override
            public boolean canUse() {
                return action() == NONE && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return action() == NONE && super.canContinueToUse();
            }
        });
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 20f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
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
            // Вокруг Лича кружит снег, в третьей фазе — вьюга.
            int flakes = phase() == 3 ? 3 : 1;
            for (int i = 0; i < flakes; i++) {
                level().addParticle(ParticleTypes.SNOWFLAKE, getX() + (random.nextDouble() - 0.5) * 3, getY() + random.nextDouble() * 3,
                        getZ() + (random.nextDouble() - 0.5) * 3, 0, -0.02, 0);
            }
        }
    }

    // ================================================================ дальние атаки

    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        int phase = phase();
        shots++;
        int count = phase == 3 ? 5 : phase == 2 || shots % 3 == 0 ? 3 : 1;
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.8f;
        for (int i = 0; i < count; i++) {
            SpellProjectile bolt = new SpellProjectile(level(), this, damage, SpellProjectile.Kind.ICE);
            bolt.setPos(getX(), getY() + getBbHeight() * 0.7, getZ());
            Vec3 dir = new Vec3(target.getX() - getX(), target.getY(0.5) - bolt.getY(), target.getZ() - getZ())
                    .yRot((float) Math.toRadians((i - (count - 1) / 2.0) * 9));
            bolt.shoot(dir.x, dir.y, dir.z, 1.4f, 1f);
            level().addFreshEntity(bolt);
        }
        playSound(SoundEvents.PLAYER_HURT_FREEZE, 1.5f, 1.4f);
        playSound(SoundEvents.EVOKER_CAST_SPELL, 1f, 1.6f);
    }

    // ================================================================ мозг

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        controller.tickBar();
        if (nextPhase() > phase()) startShatter(nextPhase());
        if (action() != NONE) {
            tickAction();
            return;
        }
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) return;
        int phase = phase();
        novaCooldown--;
        chainCooldown--;
        blizzardCooldown--;
        summonCooldown--;
        blinkCooldown--;
        if (phase == 3) frostAura();
        if (phase == 3 && blinkCooldown <= 0 && distanceToSqr(target) < 16) {
            blinkAway(target);
            blinkCooldown = 100;
        } else if (phase == 3 && blizzardCooldown <= 0) {
            beginCast(BLIZZARD);
            blizzardCooldown = 220;
        } else if (phase >= 2 && chainCooldown <= 0 && hasLineOfSight(target)) {
            beginCast(CHAIN);
            chainCooldown = phase == 3 ? 130 : 190;
        } else if (phase >= 2 && novaCooldown <= 0) {
            beginCast(NOVA);
            novaCooldown = phase == 3 ? 100 : 150;
        } else if (phase >= 2 && summonCooldown <= 0) {
            summonMinions();
            summonCooldown = phase == 3 ? 320 : 380;
        }
    }

    private int nextPhase() {
        float part = getHealth() / getMaxHealth();
        return part > 2f / 3f ? 1 : part > 1f / 3f ? 2 : 3;
    }

    private void beginCast(int spell) {
        this.spell = spell;
        setAction(CAST);
        playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 2f, 0.7f);
    }

    private void tickAction() {
        actionTicks++;
        getNavigation().stop();
        setDeltaMovement(0, getDeltaMovement().y, 0);
        LivingEntity target = getTarget();
        if (target != null) getLookControl().setLookAt(target, 30f, 30f);
        ServerLevel level = (ServerLevel) level();
        if (action() == SHATTER) {
            if (actionTicks % 4 == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                        getX(), getY() + 2, getZ(), 20, 0.8, 1.2, 0.8, 0.2);
            }
            if (actionTicks >= SHATTER_TICKS) setAction(NONE);
            return;
        }
        if (actionTicks % 3 == 0) Fx.wisps(level, Palette.FROST, position().add(0, getBbHeight() + 0.3, 0), 4, 0.4, 0.02);
        if (actionTicks < CAST_TICKS) return;
        setAction(NONE);
        if (target == null) return;
        switch (spell) {
            case NOVA -> frostNova(level, target.position());
            case CHAIN -> chainFrost(level, target, phase() == 3 ? 7 : 4);
            case BLIZZARD -> blizzard(level);
            default -> {}
        }
    }

    /** Ледяная вспышка: круг под врагом, через секунду взрыв льда — урон и сильное замедление. */
    private void frostNova(ServerLevel level, Vec3 center) {
        strikeAt(level, center, 3.5, 20, (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 1.1f);
    }

    /** Метка-предупреждение на ticks тиков, затем ледяной взрыв радиусом radius. */
    private void strikeAt(ServerLevel level, Vec3 center, double radius, int ticks, float damage) {
        Vec3 ground = Fx.ground(level, center).add(0, 0.1, 0);
        DelayedTasks.repeat(0, 4, ticks / 4, () -> {
            if (!isAlive()) return;
            Fx.ring(level, ParticleTypes.SNOWFLAKE, ground, radius, (int) (radius * 10), 0, 0.02);
            Fx.ring(level, net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, ground, radius * 0.5, 12, 0, 0.01);
        });
        DelayedTasks.schedule(ticks, () -> {
            if (!isAlive()) return;
            for (Player player : level.getEntitiesOfClass(Player.class, new AABB(ground, ground).inflate(radius, 3, radius),
                    p -> !p.isSpectator() && !p.isCreative() && p.position().distanceToSqr(ground) < radius * radius + 4)) {
                player.hurt(damageSources().indirectMagic(this, this), damage);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2), this);
                player.setTicksFrozen(Math.max(player.getTicksFrozen(), 120));
            }
            Fx.shockwave(level, Palette.FROST, ground, radius, 6);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    ground.x, ground.y + 0.3, ground.z, 40, radius / 2, 0.4, radius / 2, 0.2);
            level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.GLASS_BREAK, getSoundSource(), 2f, 0.6f);
        });
    }

    /** Цепной мороз: ледяная молния бьёт цель и скачет к ближайшему другому игроку, jumps раз. */
    private void chainFrost(ServerLevel level, LivingEntity first, int jumps) {
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.7f;
        Set<LivingEntity> hit = new HashSet<>();
        Vec3[] from = {position().add(0, getBbHeight() * 0.7, 0)};
        LivingEntity[] current = {first};
        for (int jump = 0; jump <= jumps; jump++) {
            DelayedTasks.schedule(jump * 6, () -> {
                LivingEntity target = current[0];
                if (!isAlive() || target == null || !target.isAlive()) return;
                Vec3 to = target.position().add(0, target.getBbHeight() * 0.6, 0);
                Fx.zigzag(level, Palette.FROST, from[0], to, getRandom());
                target.hurt(damageSources().indirectMagic(this, this), damage);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), this);
                level.playSound(null, to.x, to.y, to.z, SoundEvents.AMETHYST_BLOCK_BREAK, getSoundSource(), 1.5f, 1.2f);
                hit.add(target);
                from[0] = to;
                // Следующая цель — ближайший другой игрок в 10 блоках; если все уже задеты, круг начинается заново.
                List<Player> near = level.getEntitiesOfClass(Player.class, target.getBoundingBox().inflate(10),
                        p -> p != target && p.isAlive() && !p.isSpectator() && !p.isCreative());
                near.sort((a, b) -> Double.compare(a.distanceToSqr(target), b.distanceToSqr(target)));
                LivingEntity next = near.stream().filter(p -> !hit.contains(p)).findFirst().orElse(near.isEmpty() ? null : near.get(0));
                if (next != null && hit.containsAll(near)) hit.clear();
                current[0] = next;
            });
        }
    }

    /** Буран: по две метки вокруг каждого игрока поблизости, через секунду на каждую обрушивается ледяная глыба. */
    private void blizzard(ServerLevel level) {
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.9f;
        List<Vec3> spots = new ArrayList<>();
        for (Player player : controller.playersInRadius(30)) {
            spots.add(player.position());
            spots.add(player.position().add((random.nextDouble() - 0.5) * 6, 0, (random.nextDouble() - 0.5) * 6));
        }
        for (int i = 0; i < spots.size(); i++) {
            Vec3 spot = spots.get(i);
            DelayedTasks.schedule(i * 3, () -> {
                if (!isAlive()) return;
                strikeAt(level, spot, 2.5, 24, damage);
                Fx.column(level, ParticleTypes.SNOWFLAKE, spot.add(0, 6, 0), 0.1, 20, 0.6, -0.3);
            });
        }
        playSound(SoundEvents.ELYTRA_FLYING, 1.5f, 0.6f);
    }

    /** Морозная аура третьей фазы: игроки ближе 5 блоков замедлены, раз в секунду их обжигает холодом. */
    private void frostAura() {
        if (tickCount % 20 != 0) return;
        for (Player player : controller.playersInRadius(5)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0), this);
            player.hurt(damageSources().freeze(), 2f);
        }
        if (level() instanceof ServerLevel level) Fx.ring(level, ParticleTypes.SNOWFLAKE, position().add(0, 0.3, 0), 5, 40, 0.02, 0.02);
    }

    /** Скачок: Лич рассыпается снегом и появляется в 8–12 блоках от подошедшего врага. */
    private void blinkAway(LivingEntity threat) {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 away = position().subtract(threat.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
        away = away.normalize();
        Vec3 start = position();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = (random.nextDouble() - 0.5) * 1.6;
            Vec3 dir = away.yRot((float) angle).scale(8 + random.nextDouble() * 4);
            if (randomTeleport(getX() + dir.x, getY(), getZ() + dir.z, false)) {
                level.sendParticles(ParticleTypes.SNOWFLAKE, start.x, start.y + 1.5, start.z, 60, 0.6, 1.2, 0.6, 0.05);
                Fx.burst(level, Palette.FROST, position().add(0, 1.5, 0), 1.2);
                playSound(SoundEvents.ENDERMAN_TELEPORT, 1.5f, 0.6f);
                return;
            }
        }
    }

    private void summonMinions() {
        if (phase() >= 3) {
            controller.summon(ModEntities.ICE_GOLEM.get(), 1, 5, mob -> {});
            controller.summon(ModEntities.SNOW_BRUTE.get(), 1, 5, mob -> {});
        } else {
            controller.summon(ModEntities.BANSHEE.get(), 1, 5, mob -> {});
            controller.summon(ModEntities.FROZEN_ARCHER.get(), 1, 5, mob -> {});
        }
    }

    // ================================================================ фазы

    /** Смена фазы: ледяные доспехи раскалываются и разлетаются, Лич неуязвим и отбрасывает всех вокруг. */
    private void startShatter(int phase) {
        entityData.set(PHASE, phase);
        applyPhaseBonuses();
        setAction(SHATTER);
        if (!(level() instanceof ServerLevel level)) return;
        String[] parts = phase == 2 ? new String[]{"pauldron_left", "pauldron_right", "crown", "chest_armor"} : new String[]{"belt"};
        for (String part : parts) dropPart(level, part);
        for (Player player : controller.playersInRadius(7)) {
            Vec3 push = player.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.1);
            player.setDeltaMovement(push.x, 0.45, push.z);
            player.hurtMarked = true;
            player.setTicksFrozen(Math.max(player.getTicksFrozen(), 100));
        }
        playSound(SoundEvents.GLASS_BREAK, 3f, 0.5f);
        playSound(SoundEvents.WITHER_AMBIENT, 2f, 1.6f);
        Fx.shockwave(level, Palette.FROST, position().add(0, 0.2, 0), 7, 10);
        controller.bossEvent().setColor(phase == 3 ? BossEvent.BossBarColor.PURPLE : BossEvent.BossBarColor.BLUE);
        controller.message("message.heroicpaths.frost_lich_phase" + phase, phase == 3 ? ChatFormatting.DARK_PURPLE : ChatFormatting.AQUA);
    }

    /** Где у модели часть (в пикселях модели: x вправо от Лича, высота от земли, z вперёд). */
    private static double[] partOffset(String part) {
        return switch (part) {
            case "pauldron_left" -> new double[]{-7, 35, 0};
            case "pauldron_right" -> new double[]{7, 35, 0};
            case "crown" -> new double[]{0, 47, 1};
            case "chest_armor" -> new double[]{0, 30, 3};
            default -> new double[]{0, 22, 3}; // belt
        };
    }

    private void dropPart(ServerLevel level, String part) {
        ClayDebris debris = ModEntities.CLAY_DEBRIS.get().create(level);
        if (debris == null) return;
        double[] o = partOffset(part);
        double scale = MODEL_SCALE / 16.0;
        float yaw = yBodyRot * Mth.DEG_TO_RAD;
        double sideX = -Math.cos(yaw), sideZ = -Math.sin(yaw);
        double fwdX = -Math.sin(yaw), fwdZ = Math.cos(yaw);
        double x = getX() + (o[0] * sideX + o[2] * fwdX) * scale;
        double z = getZ() + (o[0] * sideZ + o[2] * fwdZ) * scale;
        Vec3 out = new Vec3(x - getX(), 0, z - getZ());
        out = (out.lengthSqr() < 0.01 ? new Vec3(fwdX, 0, fwdZ) : out.normalize()).scale(0.25 + random.nextDouble() * 0.15);
        debris.moveTo(x, getY() + o[1] * scale, z, yBodyRot, 0);
        debris.setup(ClayDebris.LICH, part, yBodyRot, out.add(0, 0.3, 0));
        level.addFreshEntity(debris);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BLUE_ICE.defaultBlockState()),
                x, getY() + o[1] * scale, z, 20, 0.3, 0.3, 0.3, 0.15);
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
        if (DungeonBoss.ignoresDamage(source) || source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING)) return false;
        if (action() == SHATTER && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount * (1 - armorReduction()));
    }

    @Override
    public boolean canFreeze() {
        return false;
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
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    getX(), getY() + 2, getZ(), 150, 1, 1.5, 1, 0.3);
            Fx.burst(level, Palette.FROST, position().add(0, 2, 0), 3);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.GLASS_HIT;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.6f;
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
        if (phase() == 3) controller.bossEvent().setColor(BossEvent.BossBarColor.PURPLE);
    }
}
