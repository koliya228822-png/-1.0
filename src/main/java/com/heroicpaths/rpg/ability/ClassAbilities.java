package com.heroicpaths.rpg.ability;

import com.heroicpaths.combat.WeaponCombat;
import com.heroicpaths.fx.Fx;
import com.heroicpaths.fx.Palette;
import com.heroicpaths.registry.ModParticles;
import com.heroicpaths.minion.CompanionManager;
import com.heroicpaths.registry.ModEffects;
import com.heroicpaths.rpg.ClassTraits;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

import java.util.Comparator;
import java.util.List;

import static com.heroicpaths.rpg.ability.AbilityLogic.*;

/**
 * Умения классов из книг, общие для всех подклассов класса:
 * воин — «Раскалывающий удар», «Боевое знамя», «Последний рубеж»;
 * лучник — «Оглушающая стрела», «Ловчая сеть», «Орлиный глаз»;
 * маг — «Щит маны», «Метель»; разбойник — «Потрошение», «Исчезновение»;
 * охотник — «Звериная ярость», «Выслеживание», «Бросок зверя».
 */
public final class ClassAbilities {
    private ClassAbilities() {}

    static boolean cast(ServerPlayer player, AbilityDef def) {
        return switch (def.id()) {
            case "sunder" -> sunder(player, def);
            case "battle_banner" -> battleBanner(player, def);
            case "last_stand" -> lastStand(player, def);
            case "concussive_shot" -> arrowWithEffect(player, def, "concussive", 3.0f);
            case "net_shot" -> arrowWithEffect(player, def, "net", 1.0f);
            case "eagle_eye" -> eagleEye(player, def);
            case "mana_shield" -> manaShield(player, def);
            case "blizzard" -> blizzard(player, def);
            case "eviscerate" -> eviscerate(player, def);
            case "vanish" -> vanish(player, def);
            case "feral_rage" -> feralRage(player, def);
            case "tracking" -> tracking(player, def);
            case "pounce" -> pounce(player, def);
            default -> false;
        };
    }

    private static int rankTicks(ServerPlayer player, AbilityDef def, int base, int perRank) {
        return base + perRank * (RpgManager.get(player).getRank(def.id()) - 1);
    }

    // ================================================================ воин

    /** Тяжёлый удар: урон, оглушение и замедление цели. */
    private static boolean sunder(ServerPlayer player, AbilityDef def) {
        LivingEntity target = targetInFront(player, 4.5);
        if (target == null) return noTarget(player);
        ServerLevel level = player.serverLevel();
        hit(player, target, power(player, def, 9f), def);
        WeaponCombat.applyStun(player, target);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
        // Удар раскалывает землю под целью.
        Fx.flash(level, Palette.STEEL, target.position().add(0, target.getBbHeight() / 2, 0), 1);
        Fx.embers(level, Palette.STEEL, target.position().add(0, target.getBbHeight() / 2, 0), 16, 0.2, 0.25);
        Fx.shockwave(level, Palette.EARTH, target.position(), 2, 4);
        Fx.debris(level, target.position(), 25, 0.5, 0.25);
        sound(player, SoundEvents.ANVIL_LAND, 0.6f, 1.3f);
        return true;
    }

    /** Знамя на 10+ секунд: союзники рядом с ним получают силу и сопротивление. */
    private static boolean battleBanner(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 at = player.position();
        double radius = areaRadius(player, def);
        int seconds = rankTicks(player, def, 10, 2);
        Fx.flash(level, Palette.FIRE, at.add(0, 1.5, 0), 1);
        Fx.debris(level, at, 20, 0.3, 0.2);
        DelayedTasks.repeat(0, 20, seconds, () -> {
            for (Player ally : SubclassAbilities.allies(player, 32)) {
                if (ally.position().distanceTo(at) > radius) continue;
                ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 0));
                ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0));
                Fx.embers(level, Palette.FIRE, ally.position().add(0, 1, 0), 3, 0.3, 0.03);
            }
            // Знамя: огненное древко, полотнище из искр, золотой круг силы.
            Fx.column(level, ModParticles.mark(Palette.FIRE), at, 3.2, 12, 0.02, 0);
            for (int i = 0; i < 6; i++) Fx.one(level, ModParticles.ember(Palette.FIRE), at.add(0.25 + i * 0.12, 2.6 + (i % 2) * 0.2, 0), new Vec3(0.02, 0, 0));
            Fx.ring(level, ModParticles.mark(Palette.HOLY), at.add(0, 0.1, 0), radius, (int) (radius * 8), 0, 0);
        });
        sound(player, SoundEvents.BELL_BLOCK, 1.2f, 0.8f);
        return true;
    }

    /** 6+ секунд здоровье не падает ниже 1, в конце — лечение на 20% здоровья. */
    private static boolean lastStand(ServerPlayer player, AbilityDef def) {
        int ticks = rankTicks(player, def, 120, 40);
        player.getPersistentData().putLong(ClassTraits.LAST_STAND_UNTIL, player.level().getGameTime() + ticks);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0));
        DelayedTasks.schedule(ticks, () -> {
            if (!player.isAlive()) return;
            heal(player, player, player.getMaxHealth() * 0.2f);
            Fx.burst(player.serverLevel(), Palette.HOLY, player.position().add(0, 1, 0), 0.8);
            particles(player, ParticleTypes.HEART, player.position().add(0, 1.5, 0), 6, 0.4, 0);
        });
        Fx.flash(player.serverLevel(), Palette.HOLY, player.position().add(0, 1, 0), 2);
        Fx.aura(player, Palette.HOLY, ticks, 4);
        particles(player, ParticleTypes.TOTEM_OF_UNDYING, player.position().add(0, 1, 0), 30, 0.5, 0.3);
        sound(player, SoundEvents.TOTEM_USE, 0.5f, 1.2f);
        return true;
    }

    // ================================================================ лучник

    /** Стрела с эффектом при попадании (см. onArrowEffect). */
    private static boolean arrowWithEffect(ServerPlayer player, AbilityDef def, String effect, float arrowBase) {
        Arrow arrow = abilityArrow(player, def, arrowBase);
        CompoundTag data = arrow.getPersistentData();
        data.putString(ARROW_EFFECT, effect);
        data.putFloat(ARROW_POWER, RpgManager.rankPower(player, def));
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 3.0f, 0.5f);
        player.serverLevel().addFreshEntity(arrow);
        AbilityLogic.bowFlash(player, effect.equals("net") ? Palette.NATURE : Palette.STORM);
        sound(player, SoundEvents.ARROW_SHOOT, 1f, effect.equals("net") ? 0.7f : 1.3f);
        return true;
    }

    /** 10+ секунд: +25% к шансу крита, ночное зрение и подсветка врагов вокруг. */
    private static boolean eagleEye(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        int ticks = rankTicks(player, def, 200, 40);
        player.getPersistentData().putLong(ClassTraits.EAGLE_EYE_UNTIL, player.level().getGameTime() + ticks);
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks + 40, 0));
        for (LivingEntity target : enemiesInRadius(player, player.position(), 32)) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
            Fx.embers(level, Palette.STORM, target.position().add(0, target.getBbHeight() + 0.3, 0), 4, 0.15, 0.02);
        }
        Fx.shockwave(level, Palette.STORM, player.position().add(0, 1.5, 0), 12, 10);
        Fx.aura(player, Palette.STORM, Math.min(ticks, 200), 6);
        sound(player, SoundEvents.PHANTOM_FLAP, 1f, 1.5f);
        return true;
    }

    /** Эффекты стрел классов при попадании. */
    static void onArrowEffect(String effect, ServerPlayer player, ServerLevel level, ProjectileImpactEvent event, float power) {
        Vec3 at = event.getRayTraceResult().getLocation();
        if (effect.equals("concussive")) {
            if (event.getRayTraceResult() instanceof EntityHitResult result && result.getEntity() instanceof LivingEntity target
                    && canHarm(player, target)) {
                Vec3 push = target.position().subtract(player.position()).normalize();
                target.knockback(1.2 * power, -push.x, -push.z);
                WeaponCombat.applyStun(player, target);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            }
            Fx.flash(level, Palette.STORM, at, 1);
            Fx.shockwave(level, Palette.STORM, at, 2.5, 4);
            Fx.embers(level, Palette.STORM, at, 16, 0.1, 0.25);
        } else if (effect.equals("net")) {
            int ticks = Math.round(80 * power);
            for (LivingEntity target : targetsInRadius(player, at, 2.5)) {
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6));
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0));
                if (target instanceof Mob mob) mob.getNavigation().stop();
            }
            // Сеть раскрывается: нити крест-накрест и паутина.
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI * i / 6;
                Vec3 d = new Vec3(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
                Fx.line(level, ModParticles.mark(Palette.NATURE), at.add(d).add(0, 0.3, 0), at.subtract(d).add(0, 0.3, 0), 3);
            }
            Fx.ring(level, ModParticles.mark(Palette.NATURE), at.add(0, 0.3, 0), 2.5, 20, 0, 0);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)), at.x, at.y + 0.5, at.z, 30, 1.2, 0.5, 1.2, 0.02);
        }
    }

    // ================================================================ маг

    /** 10+ секунд: 60% получаемого урона снимается с маны (2 маны за единицу урона). */
    private static boolean manaShield(ServerPlayer player, AbilityDef def) {
        if (PlayerStats.paysWithHealth(player)) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.mana_shield_blood"), true);
            return false;
        }
        ServerLevel level = player.serverLevel();
        int ticks = rankTicks(player, def, 200, 60);
        player.getPersistentData().putLong(ClassTraits.MANA_SHIELD_UNTIL, player.level().getGameTime() + ticks);
        // Сфера маны: кольца вокруг мага, пока щит держится.
        DelayedTasks.repeat(0, 5, ticks / 5, () -> {
            if (!player.isAlive()) return;
            double spin = level.getGameTime() * 0.15;
            for (int i = 0; i < 10; i++) {
                double angle = spin + i * Math.PI / 5;
                Fx.one(level, ModParticles.mark(Palette.ARCANE), player.position().add(Math.cos(angle) * 1.1, 1 + Math.sin(angle * 2 + spin) * 0.6, Math.sin(angle) * 1.1), Vec3.ZERO);
            }
        });
        Fx.flash(level, Palette.ARCANE, player.position().add(0, 1, 0), 1);
        particles(player, ParticleTypes.ENCHANT, player.position().add(0, 1, 0), 40, 0.6, 0.5);
        sound(player, SoundEvents.BEACON_ACTIVATE, 1f, 1.6f);
        return true;
    }

    /** Метель по схеме меток: место отмечено и замедлено, через 1.5 с — 8 волн холода с неба. */
    private static boolean blizzard(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 at = groundTarget(player, def);
        double radius = areaRadius(player, def);
        float damage = power(player, def, 2.5f);
        Zones.strike(player, at, radius, Palette.FROST, () -> DelayedTasks.repeat(0, 15, 8, () -> {
            for (LivingEntity target : targetsInRadius(player, at, radius)) {
                hit(player, target, damage, def);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                target.setTicksFrozen(Math.min(target.getTicksRequiredToFreeze() + 20, target.getTicksFrozen() + 30));
                Fx.embers(level, Palette.FROST, target.position().add(0, target.getBbHeight() / 2, 0), 5, 0.3, 0.05);
            }
            // Туча над кругом, снег и льдинки летят косо вниз.
            level.sendParticles(ModParticles.wisp(Palette.FROST), at.x, at.y + 5, at.z, 18, radius * 0.5, 0.3, radius * 0.5, 0.005);
            for (int i = 0; i < 30; i++) {
                double angle = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * radius;
                Fx.one(level, ModParticles.ember(Palette.FROST), at.add(Math.cos(angle) * r - 1, 4.5, Math.sin(angle) * r), new Vec3(0.12, -0.5, 0));
            }
            level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 3, at.z, 50, radius * 0.6, 1.5, radius * 0.6, 0.02);
            Fx.ring(level, ModParticles.mark(Palette.FROST), at.add(0, 0.1, 0), radius, (int) (radius * 8), 0, 0);
            Fx.sound(level, at, SoundEvents.POWDER_SNOW_STEP, 1f, 0.6f);
        }));
        sound(player, SoundEvents.PLAYER_HURT_FREEZE, 1.2f, 0.6f);
        return true;
    }

    // ================================================================ разбойник

    /** Удар, который вскрывает раны: чем сильнее кровотечение, тем больнее; без кровотечения — наносит его. */
    private static boolean eviscerate(ServerPlayer player, AbilityDef def) {
        LivingEntity target = targetInFront(player, 4);
        if (target == null) return noTarget(player);
        MobEffectInstance bleeding = target.getEffect(ModEffects.BLEEDING);
        float damage = power(player, def, 6f);
        if (bleeding != null) damage *= 1f + 0.6f * (bleeding.getAmplifier() + 1);
        hit(player, target, damage, def);
        // Кровотечение снимается после удара: режущее оружие в руке не должно тут же открыть рану снова.
        if (bleeding != null) target.removeEffect(ModEffects.BLEEDING);
        else WeaponCombat.applyBleeding(player, target);
        Fx.burst(player.serverLevel(), Palette.BLOOD, target.position().add(0, target.getBbHeight() / 2, 0), bleeding != null ? 1.2 : 0.5);
        particles(player, ParticleTypes.DAMAGE_INDICATOR, target.position().add(0, target.getBbHeight() / 2, 0), bleeding != null ? 15 : 6, 0.3, 0.2);
        sound(player, SoundEvents.PLAYER_ATTACK_CRIT, 1.1f, 0.7f);
        return true;
    }

    /** Невидимость, враги теряют цель, следующий удар в ближнем бою — на 60% сильнее. */
    private static boolean vanish(ServerPlayer player, AbilityDef def) {
        int ticks = rankTicks(player, def, 120, 40);
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0));
        player.getPersistentData().putLong(ClassTraits.VANISH_UNTIL, player.level().getGameTime() + ticks);
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16), mob -> mob.getTarget() == player)) {
            mob.setTarget(null);
        }
        Fx.burst(player.serverLevel(), Palette.SHADOW, player.position().add(0, 1, 0), 1);
        particles(player, ParticleTypes.LARGE_SMOKE, player.position().add(0, 1, 0), 30, 0.5, 0.02);
        sound(player, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
        return true;
    }

    // ================================================================ охотник

    private static Mob companion(ServerPlayer player) {
        Mob companion = CompanionManager.find(player);
        if (companion == null) player.displayClientMessage(Component.translatable("message.heroicpaths.no_companion"), true);
        return companion;
    }

    /** Зверь впадает в ярость: сила, скорость и сопротивление, лечение на 25%. */
    private static boolean feralRage(ServerPlayer player, AbilityDef def) {
        Mob beast = companion(player);
        if (beast == null) return false;
        int ticks = rankTicks(player, def, 200, 40);
        beast.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 1));
        beast.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1));
        beast.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0));
        beast.heal(beast.getMaxHealth() * 0.25f * PlayerStats.healingFactor(player));
        Fx.burst(player.serverLevel(), Palette.FIRE, beast.position().add(0, beast.getBbHeight() / 2, 0), 0.8);
        Fx.aura(beast, Palette.FIRE, ticks, 4);
        player.serverLevel().sendParticles(ParticleTypes.ANGRY_VILLAGER, beast.getX(), beast.getY() + beast.getBbHeight(), beast.getZ(), 10, 0.4, 0.3, 0.4, 0);
        sound(player, SoundEvents.RAVAGER_ROAR, 0.8f, 1.4f);
        return true;
    }

    /** Подсвечивает всех врагов в 40 блоках на 15 секунд и метит ближайшего (+30% урона по нему). */
    private static boolean tracking(ServerPlayer player, AbilityDef def) {
        List<LivingEntity> prey = enemiesInRadius(player, player.position(), 40);
        if (prey.isEmpty()) return noTarget(player);
        ServerLevel level = player.serverLevel();
        int ticks = rankTicks(player, def, 300, 60);
        for (LivingEntity target : prey) target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0));
        prey.stream().min(Comparator.comparingDouble(target -> target.distanceToSqr(player))).ifPresent(nearest -> {
            nearest.getPersistentData().putLong(MARKED_UNTIL, player.level().getGameTime() + 200);
            Fx.line(level, ModParticles.mark(Palette.BLOOD), player.position().add(0, 1.2, 0), nearest.position().add(0, nearest.getBbHeight() / 2, 0), 1.5);
            Fx.burst(level, Palette.BLOOD, nearest.position().add(0, nearest.getBbHeight() + 0.3, 0), 0.5);
        });
        Fx.shockwave(level, Palette.NATURE, player.position().add(0, 0.5, 0), 14, 12);
        sound(player, SoundEvents.FOX_SNIFF, 1.2f, 0.8f);
        return true;
    }

    /** Зверь прыгает к цели, бьёт и оглушает её. */
    private static boolean pounce(ServerPlayer player, AbilityDef def) {
        Mob beast = companion(player);
        if (beast == null) return false;
        LivingEntity target = targetInFront(player, 24);
        if (target == null) return noTarget(player);
        ServerLevel level = player.serverLevel();
        Vec3 from = beast.position();
        Vec3 toward = from.subtract(target.position());
        Vec3 offset = toward.lengthSqr() > 1e-4 ? toward.normalize().scale(1.2) : Vec3.ZERO;
        Vec3 dest = target.position().add(offset.x, 0, offset.z);
        Fx.burst(level, Palette.NATURE, from.add(0, 0.5, 0), 0.6);
        Fx.line(level, ModParticles.mark(Palette.NATURE), from.add(0, 0.8, 0), dest.add(0, 0.8, 0), 2);
        beast.teleportTo(dest.x, target.getY(), dest.z);
        beast.setTarget(target);
        hit(player, target, power(player, def, 7f), def);
        WeaponCombat.applyStun(player, target);
        Fx.burst(level, Palette.BLOOD, target.position().add(0, target.getBbHeight() * 0.6, 0), 0.8);
        Fx.debris(level, dest, 15, 0.4, 0.15);
        sound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.2f, 0.7f);
        return true;
    }
}
