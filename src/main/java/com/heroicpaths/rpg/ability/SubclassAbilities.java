package com.heroicpaths.rpg.ability;

import com.heroicpaths.fx.Fx;
import com.heroicpaths.fx.Palette;
import com.heroicpaths.item.MusketItem;
import com.heroicpaths.minion.CompanionManager;
import com.heroicpaths.minion.Minion;
import com.heroicpaths.minion.Minions;
import com.heroicpaths.party.PartyManager;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModParticles;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.stat.Stat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static com.heroicpaths.rpg.ability.AbilityLogic.*;

/** Умения подклассов: воин, маг, мушкетёр, разбойник и охотник. */
public final class SubclassAbilities {
    public static final String BLOOD_PACT_UNTIL = "heroicpaths.blood_pact_until";
    public static final String POISON_BLADES_UNTIL = "heroicpaths.poison_blades_until";
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.65f, 0.02f, 0.05f), 1.3f);

    private SubclassAbilities() {}

    static boolean cast(ServerPlayer player, AbilityDef def) {
        return switch (def.id()) {
            // Воин
            case "cleave" -> cleave(player, def);
            case "leap_slam" -> leapSlam(player, def);
            case "shield_wall" -> shieldWall(player, def);
            case "axe_throw" -> axeThrow(player, def);
            case "lay_on_hands" -> layOnHands(player, def);
            case "consecration" -> consecration(player, def);
            // Маг
            case "ward" -> ward(player, def);
            case "haste_hymn" -> hasteHymn(player, def);
            case "entangle" -> entangle(player, def);
            case "purify" -> purify(player, def);
            case "raise_skeletons" -> raiseSkeletons(player, def);
            case "spirit_wolves" -> spiritWolves(player, def);
            case "summon_golem" -> summonGolem(player, def);
            case "empower_minions" -> empowerMinions(player, def);
            case "blood_bolt" -> bloodBolt(player, def);
            case "blood_nova" -> bloodNova(player, def);
            case "hemorrhage" -> hemorrhage(player, def);
            case "sanguine_shield" -> sanguineShield(player, def);
            case "blood_pact" -> bloodPact(player, def);
            // Мушкетёр
            case "piercing_shot" -> piercingShot(player, def);
            case "grapeshot" -> grapeshot(player, def);
            case "steady_aim" -> steadyAim(player, def);
            case "powder_keg" -> powderKeg(player, def);
            // Разбойник
            case "shadow_step" -> shadowStep(player, def);
            case "smoke_bomb" -> smokeBomb(player, def);
            case "heavy_bolt" -> effectBolt(player, def, "heavy", 4.0f, 1.5f);
            case "caltrops" -> caltrops(player, def);
            case "explosive_bolt" -> effectBolt(player, def, "explosive", 2.5f, power(player, def, 9f));
            case "backstab" -> backstab(player, def);
            case "flurry" -> flurry(player, def);
            case "poison_blades" -> poisonBlades(player, def);
            case "fan_of_knives" -> fanOfKnives(player, def);
            // Охотник
            case "call_companion" -> CompanionManager.call(player);
            case "sic_em" -> sicEm(player, def);
            case "mend_pet" -> mendPet(player, def);
            case "pack_howl" -> packHowl(player, def);
            default -> ClassAbilities.cast(player, def);
        };
    }

    // ================================================================ общее

    /** Игрок и участники его группы рядом. */
    static List<Player> allies(ServerPlayer player, double radius) {
        return player.serverLevel().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(radius),
                p -> p.isAlive() && !p.isSpectator() && (p == player || PartyManager.sameParty(player, p)));
    }

    /** Сила выстрела без снаряда: к силе умения добавляются бонусы стрелкового оружия. */
    private static float rangedPower(ServerPlayer player, AbilityDef def, float base) {
        return power(player, def, base) * (1f + PlayerStats.get(player).get(Stat.BOW_DAMAGE) / 100f);
    }

    private static boolean poisonBladesActive(ServerPlayer player) {
        return player.getPersistentData().getLong(POISON_BLADES_UNTIL) > player.level().getGameTime();
    }

    // ================================================================ воин

    private static boolean cleave(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        float damage = power(player, def, 9f);
        double reach = 4.5 * rankScale(player, def);
        Vec3 dir = horizontalLook(player);
        for (LivingEntity target : inCone(player, reach, 0.3)) {
            hit(player, target, damage, def);
            target.knockback(0.5, -dir.x, -dir.z);
            Fx.embers(level, Palette.BLOOD, target.position().add(0, target.getBbHeight() / 2, 0), 8, 0.2, 0.15);
        }
        // Дуга рассечения: широкий светлый след клинка перед воином.
        for (double a = -1.1; a <= 1.1; a += 0.12) {
            Vec3 d = dir.yRot((float) a);
            for (double r = 1.5; r <= reach; r += 1.0) {
                Vec3 p = player.position().add(d.scale(r)).add(0, 1 + a * 0.25, 0);
                Fx.one(level, ModParticles.mark(Palette.STEEL), p, d.scale(0.08));
            }
        }
        for (int i = -2; i <= 2; i++) {
            Vec3 at = player.position().add(dir.yRot((float) Math.toRadians(i * 25)).scale(2.5)).add(0, 1, 0);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
        sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
        return true;
    }

    private static boolean leapSlam(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 dir = horizontalLook(player);
        player.setDeltaMovement(dir.x * 1.4, 0.75, dir.z * 1.4);
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 30, 1));
        float damage = power(player, def, 10f);
        double radius = areaRadius(player, def);
        DelayedTasks.repeat(0, 1, 12, () -> Fx.wisps(level, Palette.STEEL, player.position().add(0, 0.5, 0), 1, 0.2, 0.01));
        DelayedTasks.schedule(14, () -> {
            if (!player.isAlive()) return;
            player.resetFallDistance();
            for (LivingEntity target : targetsInRadius(player, player.position(), radius)) {
                hit(player, target, damage, def);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                target.setDeltaMovement(target.getDeltaMovement().add(0, 0.3, 0));
                target.hurtMarked = true;
            }
            // Приземление: вспышка, волна, земля разлетается.
            Fx.flash(level, Palette.STEEL, player.position().add(0, 0.4, 0), 2);
            Fx.shockwave(level, Palette.EARTH, player.position(), radius, 6);
            Fx.debris(level, player.position(), 50, radius * 0.4, 0.3);
            level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0, 0, 0, 0);
            sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.7f, 1.3f);
        });
        sound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
        return true;
    }

    private static boolean shieldWall(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 2));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 0));
        absorption(player, power(player, def, 6f), 120);
        // Стальной купол: кольца искр вокруг воина всё время защиты.
        DelayedTasks.repeat(0, 5, 24, () -> {
            if (!player.isAlive()) return;
            double spin = level.getGameTime() * 0.2;
            for (int ring = 0; ring < 3; ring++) {
                double y = 0.3 + ring * 0.7, r = 1.2 - ring * 0.25;
                for (int i = 0; i < 8; i++) {
                    double angle = spin * (ring % 2 == 0 ? 1 : -1) + i * Math.PI / 4;
                    Fx.one(level, ModParticles.mark(Palette.STEEL), player.position().add(Math.cos(angle) * r, y, Math.sin(angle) * r), Vec3.ZERO);
                }
            }
        });
        Fx.flash(level, Palette.STEEL, player.position().add(0, 1, 0), 1);
        sound(player, SoundEvents.SHIELD_BLOCK, 1.2f, 0.8f);
        sound(player, SoundEvents.ANVIL_PLACE, 0.4f, 1.4f);
        return true;
    }

    private static boolean axeThrow(ServerPlayer player, AbilityDef def) {
        LivingEntity target = targetInFront(player, 16);
        if (target == null) return noTarget(player);
        ServerLevel level = player.serverLevel();
        hit(player, target, power(player, def, 8f), def);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
        Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0);
        line(level, new ItemParticleOption(ParticleTypes.ITEM, player.getMainHandItem().copyWithCount(1)), player.getEyePosition(), to);
        Fx.line(level, ModParticles.mark(Palette.STEEL), player.getEyePosition(), to, 2);
        Fx.burst(level, Palette.BLOOD, to, 0.6);
        sound(player, SoundEvents.TRIDENT_THROW.value(), 1f, 0.8f);
        return true;
    }

    private static boolean layOnHands(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Player target = player;
        Vec3 look = player.getLookAngle();
        double best = 0.9;
        for (Player ally : allies(player, 6)) {
            if (ally == player) continue;
            Vec3 to = ally.position().add(0, 1, 0).subtract(player.getEyePosition()).normalize();
            if (to.dot(look) > best) {
                best = to.dot(look);
                target = ally;
            }
        }
        heal(player, target, target.getMaxHealth() * 0.4f * RpgManager.rankPower(player, def));
        target.removeEffect(MobEffects.POISON);
        target.removeEffect(MobEffects.WITHER);
        // Свет нисходит на исцелённого.
        Fx.skyBeam(level, Palette.HOLY, target.position(), 6);
        Fx.spiral(level, ModParticles.ember(Palette.HOLY), target.position(), 0.7, 2.2, 3, 36, 0);
        level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY() + 2, target.getZ(), 4, 0.4, 0.2, 0.4, 0);
        sound(player, SoundEvents.BEACON_ACTIVATE, 1f, 1.6f);
        return true;
    }

    private static boolean consecration(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        double radius = areaRadius(player, def);
        float damage = power(player, def, 3f);
        float healing = levelScale(player) * RpgManager.rankPower(player, def);
        Fx.flash(level, Palette.HOLY, center.add(0, 0.5, 0), 2);
        Fx.shockwave(level, Palette.HOLY, center, radius, 8);
        DelayedTasks.repeat(0, 20, 6, () -> {
            for (LivingEntity target : targetsInRadius(player, center, radius)) {
                hit(player, target, target.getType().is(EntityTypeTags.UNDEAD) ? damage * 2 : damage, def);
                Fx.embers(level, Palette.HOLY, target.position().add(0, 0.5, 0), 6, 0.3, 0.1);
            }
            for (Player ally : level.getEntitiesOfClass(Player.class, new AABB(center, center).inflate(radius),
                    p -> p.isAlive() && (p == player || PartyManager.sameParty(player, p)))) {
                heal(player, ally, healing);
            }
            // Освящённая земля: светлый круг, искры поднимаются из земли.
            Fx.ring(level, ModParticles.mark(Palette.HOLY), center.add(0, 0.1, 0), radius, (int) (radius * 8), 0, 0);
            for (int i = 0; i < 14; i++) {
                double angle = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * radius;
                Fx.one(level, ModParticles.ember(Palette.HOLY), center.add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r), new Vec3(0, 0.12, 0));
            }
        });
        sound(player, SoundEvents.BEACON_POWER_SELECT, 1f, 1.2f);
        return true;
    }

    // ================================================================ маг: поддержка

    private static boolean ward(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        float amount = power(player, def, 6f) * PlayerStats.healingFactor(player);
        for (Player ally : allies(player, 8)) {
            absorption(ally, amount, 300);
            Fx.spiral(level, ModParticles.mark(Palette.ARCANE), ally.position(), 0.9, 2.2, 2, 30, 0);
            Fx.flash(level, Palette.ARCANE, ally.position().add(0, 1, 0), 1);
            Fx.aura(ally, Palette.ARCANE, 300, 6);
        }
        sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.2f);
        return true;
    }

    private static boolean hasteHymn(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        int ticks = 300 + 60 * (RpgManager.get(player).getRank(def.id()) - 1);
        for (Player ally : allies(player, 10)) {
            ally.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1));
            ally.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, ticks, 1));
            Fx.burst(level, Palette.STORM, ally.position().add(0, 1, 0), 0.5);
            Fx.aura(ally, Palette.STORM, Math.min(ticks, 200), 5);
            particles(player, ParticleTypes.NOTE, ally.position().add(0, 2, 0), 6, 0.4, 0.5);
        }
        Fx.shockwave(level, Palette.STORM, player.position(), 10, 8);
        sound(player, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f, 1.4f);
        return true;
    }

    private static boolean entangle(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        float damage = power(player, def, 3f);
        double radius = areaRadius(player, def);
        int ticks = 80 + 20 * (RpgManager.get(player).getRank(def.id()) - 1);
        BlockParticleOption leaves = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OAK_LEAVES.defaultBlockState());
        for (LivingEntity target : targetsInRadius(player, player.position(), radius)) {
            hit(player, target, damage, def);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6));
            // Лозы обвивают пойманного всё время, пока он связан.
            DelayedTasks.repeat(0, 5, ticks / 5, () -> {
                if (!target.isAlive()) return;
                Fx.spiral(level, ModParticles.mark(Palette.NATURE), target.position(), target.getBbWidth() * 0.7 + 0.2,
                        Math.min(2, target.getBbHeight()), 1.5, 10, level.getGameTime() * 0.3);
            });
            particles(player, leaves, target.position().add(0, 0.5, 0), 20, 0.4, 0.1);
        }
        Fx.shockwave(level, Palette.NATURE, player.position(), radius, 7);
        sound(player, SoundEvents.AZALEA_LEAVES_PLACE, 1.4f, 0.6f);
        return true;
    }

    private static boolean purify(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        float amount = power(player, def, 4f);
        for (Player ally : allies(player, 8)) {
            for (MobEffectInstance effect : new ArrayList<>(ally.getActiveEffects())) {
                if (!effect.getEffect().value().isBeneficial()) ally.removeEffect(effect.getEffect());
            }
            ally.clearFire();
            heal(player, ally, amount);
            Fx.column(level, ModParticles.ember(Palette.WATER), ally.position(), 2.4, 14, 0.3, 0.1);
            Fx.flash(level, Palette.WATER, ally.position().add(0, 1, 0), 1);
        }
        sound(player, SoundEvents.BREWING_STAND_BREW, 1f, 1.5f);
        return true;
    }

    // ================================================================ маг: призыватель

    private static <T extends Mob> List<T> summon(ServerPlayer player, EntityType<T> type, int count, int lifetime,
                                                 float health, float damage, Consumer<T> setup) {
        ServerLevel level = player.serverLevel();
        List<T> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            T mob = type.create(level);
            if (!(mob instanceof Minion minion)) break;
            minion.minionData().setOwner(player.getUUID());
            minion.minionData().setLifetime(lifetime);
            double angle = Math.PI * 2 * i / count + player.getRandom().nextDouble();
            Vec3 at = player.position().add(Math.cos(angle) * 1.5, 0, Math.sin(angle) * 1.5);
            mob.moveTo(at.x, at.y, at.z, player.getYRot(), 0f);
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
            setup.accept(mob);
            // Слуги призывателя бьют как его заклинания: от силы жезла или посоха.
            Minions.applyStats(mob, player, health, damage * com.heroicpaths.item.FocusItem.damageFactor(player));
            mob.setHealth(mob.getMaxHealth());
            level.addFreshEntity(mob);
            // Призыв: круг на земле, столб душ, вспышка.
            Fx.ring(level, ModParticles.mark(Palette.SHADOW), at.add(0, 0.1, 0), mob.getBbWidth() + 0.4, 16, 0, 0);
            Fx.column(level, ParticleTypes.SOUL, at, mob.getBbHeight() + 0.5, 10, 0.25, 0.05);
            Fx.burst(level, Palette.SHADOW, at.add(0, mob.getBbHeight() / 2, 0), 0.7);
            Fx.debris(level, at, 12, 0.4, 0.1);
            result.add(mob);
        }
        return result;
    }

    /** Не больше max существ одного вида: лишние, самые старые, рассыпаются. */
    private static void limit(ServerPlayer player, EntityType<?> type, int max) {
        List<Mob> owned = new ArrayList<>(Minions.ownedNear(player, 64).stream().filter(mob -> mob.getType() == type).toList());
        owned.sort(Comparator.comparingInt(mob -> -mob.tickCount));
        for (int i = 0; i < owned.size() - max; i++) Minions.vanish(owned.get(i));
    }

    private static boolean raiseSkeletons(ServerPlayer player, AbilityDef def) {
        int count = RpgManager.get(player).getRank(def.id()) >= 3 ? 3 : 2;
        summon(player, ModEntities.SUMMONED_SKELETON.get(), count, 800, 20f, 4f, skeleton -> {
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            for (EquipmentSlot slot : EquipmentSlot.values()) skeleton.setDropChance(slot, 0f);
        });
        limit(player, ModEntities.SUMMONED_SKELETON.get(), 4);
        sound(player, SoundEvents.SKELETON_AMBIENT, 1.2f, 0.7f);
        return true;
    }

    private static boolean spiritWolves(ServerPlayer player, AbilityDef def) {
        int count = RpgManager.get(player).getRank(def.id()) >= 3 ? 3 : 2;
        summon(player, ModEntities.SPIRIT_WOLF.get(), count, 500, 16f, 4f,
                wolf -> wolf.addEffect(new MobEffectInstance(MobEffects.GLOWING, 500, 0, false, false)));
        limit(player, ModEntities.SPIRIT_WOLF.get(), 3);
        sound(player, SoundEvents.WOLF_HOWL, 1f, 1.4f);
        return true;
    }

    private static boolean summonGolem(ServerPlayer player, AbilityDef def) {
        limit(player, ModEntities.SUMMONED_GOLEM.get(), 0);
        List<Mob> golem = new ArrayList<>(summon(player, ModEntities.SUMMONED_GOLEM.get(), 1, 900, 60f, 10f, mob -> {}));
        if (!golem.isEmpty()) {
            Vec3 at = golem.get(0).position();
            Fx.shockwave(player.serverLevel(), Palette.EARTH, at, 4, 7);
            Fx.debris(player.serverLevel(), at, 50, 1.2, 0.3);
        }
        sound(player, SoundEvents.IRON_GOLEM_REPAIR, 1.2f, 0.8f);
        return true;
    }

    private static boolean empowerMinions(ServerPlayer player, AbilityDef def) {
        List<Mob> minions = Minions.ownedNear(player, 24);
        if (minions.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.no_minions"), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        for (Mob minion : minions) {
            minion.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300, 1));
            minion.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 300, 0));
            minion.heal(minion.getMaxHealth() * 0.3f);
            // Тёмная сила тянется от хозяина к каждому слуге.
            Fx.line(level, ModParticles.mark(Palette.SHADOW), player.position().add(0, 1.2, 0), minion.position().add(0, minion.getBbHeight() / 2, 0), 2);
            Fx.burst(level, Palette.SHADOW, minion.position().add(0, minion.getBbHeight() / 2, 0), 0.5);
            Fx.aura(minion, Palette.SHADOW, 300, 6);
        }
        sound(player, SoundEvents.EVOKER_CAST_SPELL, 1f, 1.2f);
        return true;
    }

    // ================================================================ маг крови

    private static boolean bloodBolt(ServerPlayer player, AbilityDef def) {
        LivingEntity target = targetInFront(player, 20);
        if (target == null) return noTarget(player);
        ServerLevel level = player.serverLevel();
        Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0);
        line(level, BLOOD, player.getEyePosition(), to);
        Fx.line(level, ModParticles.mark(Palette.BLOOD), player.getEyePosition(), to, 3);
        Fx.burst(level, Palette.BLOOD, to, 0.6);
        hit(player, target, power(player, def, 7f), def);
        sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.5f, 1.6f);
        return true;
    }

    private static boolean bloodNova(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        float damage = power(player, def, 6f);
        double radius = areaRadius(player, def);
        for (LivingEntity target : targetsInRadius(player, player.position(), radius)) {
            hit(player, target, damage, def);
            Fx.embers(level, Palette.BLOOD, target.position().add(0, target.getBbHeight() / 2, 0), 10, 0.3, 0.1);
        }
        Fx.flash(level, Palette.BLOOD, player.position().add(0, 1, 0), 2);
        Fx.shockwave(level, Palette.BLOOD, player.position(), radius, 6);
        Fx.nova(level, ModParticles.ember(Palette.BLOOD), player.position().add(0, 1, 0), 40, 0.4, player.getRandom());
        sound(player, SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1.8f);
        return true;
    }

    private static boolean hemorrhage(ServerPlayer player, AbilityDef def) {
        LivingEntity target = targetInFront(player, 12);
        if (target == null) return noTarget(player);
        ServerLevel level = player.serverLevel();
        float damage = power(player, def, 2.5f);
        Fx.line(level, ModParticles.mark(Palette.BLOOD), player.getEyePosition(), target.position().add(0, target.getBbHeight() / 2, 0), 3);
        DelayedTasks.repeat(0, 20, 6, () -> {
            if (!target.isAlive() || !player.isAlive()) return;
            hit(player, target, damage, def);
            level.sendParticles(BLOOD, target.getX(), target.getY(0.6), target.getZ(), 8, 0.2, 0.3, 0.2, 0);
            Fx.embers(level, Palette.BLOOD, target.position().add(0, target.getBbHeight() * 0.6, 0), 6, 0.2, 0.05);
        });
        sound(player, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.6f);
        return true;
    }

    private static boolean sanguineShield(ServerPlayer player, AbilityDef def) {
        float price = player.getHealth() * 0.2f;
        if (player.getHealth() <= 6f) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.too_weak"), true);
            return false;
        }
        player.setHealth(player.getHealth() - price);
        absorption(player, price * 2f * RpgManager.rankPower(player, def), 200);
        ServerLevel level = player.serverLevel();
        Fx.spiral(level, ModParticles.mark(Palette.BLOOD), player.position(), 0.9, 2.2, 2, 30, 0);
        Fx.aura(player, Palette.BLOOD, 200, 5);
        particles(player, BLOOD, player.position().add(0, 1, 0), 30, 0.5, 0);
        sound(player, SoundEvents.WARDEN_HEARTBEAT, 1.5f, 1f);
        return true;
    }

    private static boolean bloodPact(ServerPlayer player, AbilityDef def) {
        if (player.getHealth() <= 6f) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.too_weak"), true);
            return false;
        }
        player.setHealth(player.getHealth() * 0.7f);
        int ticks = 240 + 60 * (RpgManager.get(player).getRank(def.id()) - 1);
        player.getPersistentData().putLong(BLOOD_PACT_UNTIL, player.level().getGameTime() + ticks);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0));
        ServerLevel level = player.serverLevel();
        Fx.column(level, ModParticles.wisp(Palette.BLOOD), player.position(), 2.5, 10, 0.3, 0.1);
        Fx.flash(level, Palette.BLOOD, player.position().add(0, 1, 0), 2);
        Fx.aura(player, Palette.BLOOD, ticks, 4);
        sound(player, SoundEvents.WITHER_AMBIENT, 0.6f, 1.6f);
        return true;
    }

    // ================================================================ мушкетёр

    /** Дым и вспышка у ствола мушкета. */
    private static void muzzle(ServerPlayer player, double scale) {
        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(1.2)).add(0, -0.15, 0);
        ServerLevel level = player.serverLevel();
        Fx.flash(level, Palette.FIRE, at, 1);
        Fx.embers(level, Palette.FIRE, at, (int) (10 * scale), 0.1, 0.2);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, (int) (8 * scale), 0.15, 0.15, 0.15, 0.03);
    }

    /** Выстрел без снаряда: все враги на линии до первой стены. */
    private static boolean piercingShot(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(32));
        BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) end = wall.getLocation();
        float damage = rangedPower(player, def, 10f);
        Vec3 finalEnd = end;
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1),
                entity -> canHarm(player, entity) && entity.getBoundingBox().inflate(0.3).clip(eye, finalEnd).isPresent());
        for (LivingEntity victim : victims) {
            hit(player, victim, damage, def);
            Fx.burst(level, Palette.BLOOD, victim.position().add(0, victim.getBbHeight() / 2, 0), 0.5);
        }
        Fx.line(level, ModParticles.mark(Palette.FIRE), eye.add(player.getLookAngle()), end, 3);
        line(level, ParticleTypes.CRIT, eye.add(player.getLookAngle()), end);
        Fx.burst(level, Palette.STEEL, end, 0.5);
        Fx.debris(level, end, 10, 0.2, 0.15);
        muzzle(player, 1.5);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8f, 1.6f);
        return true;
    }

    private static boolean grapeshot(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        float damage = rangedPower(player, def, 7f);
        Vec3 dir = horizontalLook(player);
        for (LivingEntity target : inCone(player, 7, 0.75)) {
            hit(player, target, damage, def);
            target.knockback(0.8, -dir.x, -dir.z);
            Fx.embers(level, Palette.FIRE, target.position().add(0, target.getBbHeight() / 2, 0), 6, 0.2, 0.1);
        }
        // Картечь веером: раскалённые дробины и дым.
        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(1.2)).add(0, -0.15, 0);
        for (int i = 0; i < 30; i++) {
            Vec3 v = player.getLookAngle().yRot((float) ((player.getRandom().nextDouble() - 0.5) * 1.1))
                    .add(0, (player.getRandom().nextDouble() - 0.5) * 0.3, 0).scale(0.8);
            Fx.one(level, ModParticles.ember(Palette.FIRE), from, v);
        }
        for (int d = 1; d <= 6; d++) {
            particles(player, ParticleTypes.SMOKE, player.position().add(dir.scale(d)).add(0, 1.4, 0), 4 + d * 2, d * 0.2, 0.02);
        }
        muzzle(player, 2);
        sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 1.4f);
        return true;
    }

    private static boolean steadyAim(ServerPlayer player, AbilityDef def) {
        player.getPersistentData().putLong(MusketItem.STEADY_AIM, player.level().getGameTime() + 200);
        player.getCooldowns().removeCooldown(player.getMainHandItem().getItem());
        Fx.aura(player, Palette.STEEL, 200, 6);
        Fx.ring(player.serverLevel(), ModParticles.mark(Palette.STEEL), player.getEyePosition().add(player.getLookAngle().scale(1.5)), 0.4, 12, 0, 0);
        sound(player, SoundEvents.CROSSBOW_LOADING_END.value(), 1f, 0.8f);
        return true;
    }

    /** Пороховая бочка по схеме меток: место отмечено и замедлено, взрыв через 1.5 с. */
    private static boolean powderKeg(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 at = groundTarget(player, def);
        double radius = areaRadius(player, def);
        float damage = power(player, def, 12f);
        // Тлеющий фитиль, пока горит метка.
        DelayedTasks.repeat(0, 3, 9, () -> {
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.4, at.z, 3, 0.05, 0.05, 0.05, 0.02);
            Fx.embers(level, Palette.FIRE, at.add(0, 0.4, 0), 2, 0.05, 0.08);
        });
        Zones.strike(player, at, radius, Palette.FIRE, () -> {
            for (LivingEntity target : targetsInRadius(player, at, radius)) {
                hit(player, target, damage, def);
                Vec3 push = target.position().subtract(at).normalize();
                target.knockback(1.0, -push.x, -push.z);
                target.igniteForSeconds(3);
            }
            Vec3 up = at.add(0, 0.5, 0);
            Fx.flash(level, Palette.FIRE, up, 2);
            Fx.burst(level, Palette.FIRE, up, 1.5);
            Fx.shockwave(level, Palette.FIRE, at, radius, 5);
            Fx.debris(level, at, 35, radius * 0.4, 0.3);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, up.x, up.y, up.z, 12, radius * 0.4, 0.4, radius * 0.4, 0.04);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.4f, 0.9f);
        });
        sound(player, SoundEvents.TNT_PRIMED, 1f, 1f);
        return true;
    }

    // ================================================================ разбойник

    private static boolean shadowStep(ServerPlayer player, AbilityDef def) {
        LivingEntity target = targetInFront(player, 16);
        if (target == null) return noTarget(player);
        ServerLevel level = player.serverLevel();
        Vec3 behind = Vec3.directionFromRotation(0, target.getYRot()).scale(-1.5);
        Vec3 dest = target.position().add(behind);
        if (!level.noCollision(player, player.getBoundingBox().move(dest.subtract(player.position())))) dest = target.position();
        Vec3 start = player.position();
        Fx.burst(level, Palette.SHADOW, start.add(0, 1, 0), 0.8);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, start.x, start.y + 1, start.z, 15, 0.3, 0.6, 0.3, 0.02);
        Fx.line(level, ModParticles.mark(Palette.SHADOW), start.add(0, 1, 0), dest.add(0, 1, 0), 2);
        Vec3 face = target.position().subtract(dest);
        float yaw = (float) (Math.toDegrees(Math.atan2(face.z, face.x)) - 90);
        player.teleportTo(level, dest.x, dest.y, dest.z, yaw, player.getXRot());
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1));
        Fx.burst(level, Palette.SHADOW, dest.add(0, 1, 0), 0.8);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, dest.x, dest.y + 1, dest.z, 15, 0.3, 0.6, 0.3, 0.02);
        level.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.6f);
        return true;
    }

    private static boolean smokeBomb(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        double radius = areaRadius(player, def);
        for (LivingEntity target : targetsInRadius(player, player.position(), radius)) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            if (target instanceof Mob mob && mob.getTarget() == player) mob.setTarget(null);
        }
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 80, 0));
        // Хлопок и густое облако дыма, которое держится несколько секунд.
        Vec3 center = player.position();
        Fx.flash(level, Palette.SHADOW, center.add(0, 1, 0), 1);
        Fx.shockwave(level, Palette.SHADOW, center, radius, 5);
        DelayedTasks.repeat(0, 10, 8, () -> {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, center.x, center.y + 0.5, center.z, 10, radius * 0.4, 0.4, radius * 0.4, 0.01);
            level.sendParticles(ModParticles.wisp(Palette.SHADOW), center.x, center.y + 0.8, center.z, 12, radius * 0.4, 0.5, radius * 0.4, 0.01);
        });
        sound(player, SoundEvents.FIRE_EXTINGUISH, 1f, 0.6f);
        return true;
    }

    /** Арбалетный болт с эффектом при попадании (см. onArrowEffect). */
    private static boolean effectBolt(ServerPlayer player, AbilityDef def, String effect, float arrowBase, float effectPower) {
        Arrow arrow = abilityArrow(player, def, arrowBase);
        CompoundTag data = arrow.getPersistentData();
        data.putString(ARROW_EFFECT, effect);
        data.putFloat(ARROW_POWER, effectPower);
        arrow.setCritArrow(true);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 3.5f, 0.3f);
        player.serverLevel().addFreshEntity(arrow);
        AbilityLogic.bowFlash(player, effect.equals("explosive") ? Palette.FIRE : Palette.STEEL);
        sound(player, SoundEvents.CROSSBOW_SHOOT, 1.2f, effect.equals("heavy") ? 0.6f : 1.1f);
        return true;
    }

    /** Эффекты болтов разбойника при попадании. */
    static void onArrowEffect(String effect, ServerPlayer player, ServerLevel level, ProjectileImpactEvent event, float power) {
        Vec3 at = event.getRayTraceResult().getLocation();
        if (effect.equals("heavy")) {
            if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity target) {
                Vec3 push = target.position().subtract(player.position()).normalize();
                target.knockback(power, -push.x, -push.z);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
            }
            Fx.burst(level, Palette.STEEL, at, 0.7);
        } else if (effect.equals("explosive")) {
            AbilityDef def = AbilityRegistry.get("explosive_bolt");
            for (LivingEntity target : targetsInRadius(player, at, 3)) hit(player, target, power, def);
            Fx.flash(level, Palette.FIRE, at, 2);
            Fx.burst(level, Palette.FIRE, at, 1.2);
            Fx.shockwave(level, Palette.FIRE, at, 3, 5);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 8, 0.5, 0.5, 0.5, 0.03);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1f, 1.2f);
        } else {
            ClassAbilities.onArrowEffect(effect, player, level, event, power);
        }
    }

    private static boolean caltrops(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position().add(horizontalLook(player).scale(3));
        double radius = areaRadius(player, def);
        float damage = power(player, def, 1.5f);
        Fx.embers(level, Palette.STEEL, center.add(0, 0.5, 0), 30, radius * 0.4, 0.1);
        DelayedTasks.repeat(0, 10, 16, () -> {
            boolean damageTick = level.getGameTime() % 20 < 10;
            for (LivingEntity target : targetsInRadius(player, center, radius)) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1));
                if (damageTick) {
                    hit(player, target, damage, def);
                    Fx.embers(level, Palette.BLOOD, target.position().add(0, 0.2, 0), 4, 0.2, 0.05);
                }
            }
            // Шипы поблёскивают на земле.
            for (int i = 0; i < 10; i++) {
                double angle = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * radius;
                Fx.one(level, ModParticles.mark(Palette.STEEL), center.add(Math.cos(angle) * r, 0.1, Math.sin(angle) * r), Vec3.ZERO);
            }
        });
        sound(player, SoundEvents.CHAIN_PLACE, 1f, 1.4f);
        return true;
    }

    private static boolean backstab(ServerPlayer player, AbilityDef def) {
        LivingEntity target = targetInFront(player, 4);
        if (target == null) return noTarget(player);
        ServerLevel level = player.serverLevel();
        Vec3 targetLook = Vec3.directionFromRotation(0, target.getYRot());
        Vec3 toPlayer = player.position().subtract(target.position());
        boolean behind = new Vec3(toPlayer.x, 0, toPlayer.z).normalize().dot(targetLook) < -0.3;
        float damage = power(player, def, 8f) * (behind ? 2.5f : 1.2f);
        hit(player, target, damage, def);
        if (poisonBladesActive(player)) target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
        Vec3 c = target.position().add(0, target.getBbHeight() / 2, 0);
        Fx.burst(level, behind ? Palette.BLOOD : Palette.SHADOW, c, behind ? 1.1 : 0.5);
        particles(player, behind ? ParticleTypes.DAMAGE_INDICATOR : ParticleTypes.CRIT, c, behind ? 15 : 8, 0.3, 0.2);
        sound(player, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, behind ? 0.8f : 1.2f);
        return true;
    }

    private static boolean flurry(ServerPlayer player, AbilityDef def) {
        LivingEntity target = targetInFront(player, 4);
        if (target == null) return noTarget(player);
        ServerLevel level = player.serverLevel();
        float damage = power(player, def, 3.5f);
        int[] strike = {0};
        DelayedTasks.repeat(0, 4, 4, () -> {
            if (!target.isAlive() || !player.isAlive() || target.distanceToSqr(player) > 25) return;
            hit(player, target, damage, def);
            if (poisonBladesActive(player)) target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
            // Каждый удар — косой росчерк в другую сторону.
            Vec3 c = target.position().add(0, target.getBbHeight() * 0.6, 0);
            Vec3 side = new Vec3(-horizontalLook(player).z, 0, horizontalLook(player).x).scale(strike[0]++ % 2 == 0 ? 0.6 : -0.6);
            Fx.line(level, ModParticles.mark(Palette.STEEL), c.add(side).add(0, 0.4, 0), c.subtract(side).add(0, -0.4, 0), 8);
            Fx.embers(level, Palette.BLOOD, c, 4, 0.15, 0.08);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.6), target.getZ(), 1, 0.2, 0.2, 0.2, 0);
            sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.6f);
        });
        return true;
    }

    private static boolean poisonBlades(ServerPlayer player, AbilityDef def) {
        int ticks = 200 + 60 * (RpgManager.get(player).getRank(def.id()) - 1);
        player.getPersistentData().putLong(POISON_BLADES_UNTIL, player.level().getGameTime() + ticks);
        Fx.burst(player.serverLevel(), Palette.POISON, player.position().add(0, 1, 0), 0.6);
        Fx.aura(player, Palette.POISON, ticks, 5);
        particles(player, ParticleTypes.ITEM_SLIME, player.position().add(0, 1, 0), 20, 0.4, 0.05);
        sound(player, SoundEvents.BREWING_STAND_BREW, 1f, 0.7f);
        return true;
    }

    private static boolean fanOfKnives(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        float damage = power(player, def, 6f);
        double radius = areaRadius(player, def);
        boolean poison = poisonBladesActive(player);
        for (LivingEntity target : targetsInRadius(player, player.position(), radius)) {
            hit(player, target, damage, def);
            if (poison) target.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
            Fx.embers(level, poison ? Palette.POISON : Palette.BLOOD, target.position().add(0, target.getBbHeight() / 2, 0), 6, 0.2, 0.1);
        }
        // Веер ножей: искры разлетаются кольцом во все стороны.
        Fx.ring(level, ModParticles.ember(poison ? Palette.POISON : Palette.STEEL), player.position().add(0, 1, 0), 0.5, 32, radius * 0.12, 0.02);
        Fx.ring(level, ModParticles.mark(Palette.STEEL), player.position().add(0, 1.1, 0), 0.6, 24, radius * 0.09, 0);
        sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 1.4f);
        return true;
    }

    // ================================================================ охотник

    private static Mob companionOrComplain(ServerPlayer player) {
        Mob companion = CompanionManager.find(player);
        if (companion == null) player.displayClientMessage(Component.translatable("message.heroicpaths.no_companion"), true);
        return companion;
    }

    private static boolean sicEm(ServerPlayer player, AbilityDef def) {
        Mob companion = companionOrComplain(player);
        if (companion == null) return false;
        LivingEntity target = targetInFront(player, 24);
        if (target == null) return noTarget(player);
        ServerLevel level = player.serverLevel();
        companion.setTarget(target);
        companion.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 120, 1));
        companion.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 120, 1));
        Fx.line(level, ModParticles.mark(Palette.BLOOD), companion.position().add(0, companion.getBbHeight() / 2, 0),
                target.position().add(0, target.getBbHeight() / 2, 0), 2);
        Fx.aura(companion, Palette.BLOOD, 120, 5);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, companion.getX(), companion.getY() + companion.getBbHeight(), companion.getZ(), 6, 0.3, 0.2, 0.3, 0);
        sound(player, SoundEvents.WOLF_GROWL, 1f, 0.8f);
        return true;
    }

    private static boolean mendPet(ServerPlayer player, AbilityDef def) {
        Mob companion = companionOrComplain(player);
        if (companion == null) return false;
        ServerLevel level = player.serverLevel();
        companion.heal(companion.getMaxHealth() * 0.4f * PlayerStats.healingFactor(player) * RpgManager.rankPower(player, def));
        companion.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 1));
        Fx.spiral(level, ModParticles.ember(Palette.NATURE), companion.position(), companion.getBbWidth() * 0.7 + 0.3, companion.getBbHeight() + 0.4, 2, 30, 0);
        Fx.line(level, ModParticles.mark(Palette.NATURE), player.position().add(0, 1.2, 0), companion.position().add(0, companion.getBbHeight() / 2, 0), 2);
        level.sendParticles(ParticleTypes.HEART, companion.getX(), companion.getY() + companion.getBbHeight(), companion.getZ(), 8, 0.4, 0.3, 0.4, 0);
        sound(player, SoundEvents.WOLF_WHINE, 1f, 1.2f);
        return true;
    }

    private static boolean packHowl(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Mob companion = CompanionManager.find(player);
        for (LivingEntity member : companion == null ? List.<LivingEntity>of(player) : List.<LivingEntity>of(player, companion)) {
            member.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 240, 0));
            member.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 240, 0));
            Fx.aura(member, Palette.NATURE, 240, 6);
        }
        for (LivingEntity target : targetsInRadius(player, player.position(), 8)) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0));
            Fx.embers(level, Palette.SHADOW, target.position().add(0, target.getBbHeight(), 0), 5, 0.2, 0.03);
        }
        // Вой расходится тремя волнами.
        DelayedTasks.repeat(0, 5, 3, () -> Fx.shockwave(level, Palette.NATURE, player.position().add(0, 1, 0), 8, 6));
        particles(player, ParticleTypes.NOTE, player.position().add(0, 2, 0), 8, 0.6, 0.5);
        sound(player, SoundEvents.WOLF_HOWL, 1.4f, 0.9f);
        return true;
    }
}
