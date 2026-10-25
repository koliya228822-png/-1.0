package com.heroicpaths.rpg;

import com.heroicpaths.minion.CompanionManager;
import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.ability.AbilityLogic;
import com.heroicpaths.rpg.stat.Element;
import com.heroicpaths.rpg.stat.Keystone;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Врождённые особенности классов, которые делают их непохожими друг на друга, и правила новых ключевых камней:
 * <ul>
 *     <li>воин — «Боевая ярость»: удары в ближнем бою восстанавливают выносливость, а при здоровье ниже 35% он получает на 20% меньше урона;</li>
 *     <li>лучник — «Дальний выстрел»: стрелы бьют на 2% сильнее за каждый блок дальше 8 (до +30%);</li>
 *     <li>маг — «Резонанс стихий»: заклинание другой стихии, чем предыдущее, наносит на 20% больше урона;</li>
 *     <li>разбойник — «Удар в спину»: удары ближнего боя со спины цели наносят на 35% больше урона;</li>
 *     <li>охотник — «Связь со зверем»: пока его зверь жив и рядом, охотник получает на 15% меньше урона.</li>
 * </ul>
 * Здесь же уклонение, «Последний рубеж», «Щит маны», «Исчезновение» и «Орлиный глаз».
 */
public final class ClassTraits {
    public static final float WARRIOR_STAMINA_PER_HIT = 3f;
    public static final float WARRIOR_LOW_HEALTH = 0.35f;
    public static final float WARRIOR_LOW_HEALTH_FACTOR = 0.8f;
    public static final double ARCHER_FAR_FROM = 8;
    public static final float ARCHER_BONUS_PER_BLOCK = 0.02f;
    public static final float ARCHER_MAX_BONUS = 0.3f;
    public static final float MAGE_RESONANCE_FACTOR = 1.2f;
    public static final float ROGUE_BACKSTAB_FACTOR = 1.35f;
    public static final float HUNTER_BOND_FACTOR = 0.85f;
    public static final double HUNTER_BOND_RANGE = 24;
    public static final float AMBUSH_FACTOR = 1.4f;
    public static final float VITAL_SURGE_HEAL = 0.08f;
    public static final float ARCANE_SHIELD_SHARE = 0.3f;
    public static final float MANA_SHIELD_SHARE = 0.6f;
    /** Сколько ресурса стоит единица урона, поглощённая щитом маны. */
    public static final float RESOURCE_PER_DAMAGE = 2f;
    public static final float VANISH_FACTOR = 1.6f;
    public static final float EAGLE_EYE_CRIT = 25f;

    public static final float SECOND_WIND_BELOW = 0.3f, SECOND_WIND_HEAL = 0.25f;
    public static final int SECOND_WIND_COOLDOWN = 1800;
    public static final float OPPORTUNIST_FACTOR = 1.3f, STEADY_HANDS_FACTOR = 1.25f, APEX_HEAL = 0.1f;
    public static final int STEADY_TICKS = 20;
    private static final String SECOND_WIND_READY = "heroicpaths.second_wind_ready";
    private static final String MOVED_AT = "heroicpaths.moved_at";
    private static final String STILL_AT = "heroicpaths.still_pos";

    public static final String LAST_STAND_UNTIL = "heroicpaths.last_stand_until";
    public static final String MANA_SHIELD_UNTIL = "heroicpaths.mana_shield_until";
    public static final String VANISH_UNTIL = "heroicpaths.vanish_until";
    public static final String EAGLE_EYE_UNTIL = "heroicpaths.eagle_eye_until";
    private static final String LAST_ELEMENT = "heroicpaths.last_element";
    private static final String RESONANCE_ELEMENT = "heroicpaths.resonance_element";
    private static final String RESONANCE_UNTIL = "heroicpaths.resonance_until";

    private ClassTraits() {}

    private static boolean active(LivingEntity entity, String key) {
        return entity.getPersistentData().getLong(key) > entity.level().getGameTime();
    }

    /** Цель стоит к атакующему спиной. */
    public static boolean isBehind(LivingEntity attacker, LivingEntity target) {
        Vec3 look = Vec3.directionFromRotation(0, target.getYRot());
        Vec3 toAttacker = attacker.position().subtract(target.position());
        Vec3 flat = new Vec3(toAttacker.x, 0, toAttacker.z);
        return flat.lengthSqr() > 1e-4 && flat.normalize().dot(look) < -0.3;
    }

    // ---------------------------------------------------------------- урон

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        // Защита: уклонение, особенности воина и охотника.
        if (target instanceof ServerPlayer defender && RpgManager.get(defender).getPlayerClass() != PlayerClass.NONE) {
            if (source.getEntity() instanceof LivingEntity && source.getEntity() != defender
                    && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                    && defender.getRandom().nextFloat() * 100f < PlayerStats.dodgeChance(defender)) {
                event.setCanceled(true);
                defender.serverLevel().sendParticles(ParticleTypes.CLOUD, defender.getX(), defender.getY(0.6), defender.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
                defender.serverLevel().playSound(null, defender.getX(), defender.getY(), defender.getZ(),
                        SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, 1f, 1.4f);
                return;
            }
            float factor = 1f;
            PlayerClass defenderClass = RpgManager.get(defender).getPlayerClass();
            if (defenderClass == PlayerClass.WARRIOR && defender.getHealth() <= defender.getMaxHealth() * WARRIOR_LOW_HEALTH) {
                factor *= WARRIOR_LOW_HEALTH_FACTOR;
            }
            if (defenderClass == PlayerClass.HUNTER && companionNear(defender)) factor *= HUNTER_BOND_FACTOR;
            if (factor != 1f) event.setAmount(event.getAmount() * factor);
        }

        // Нападение: особенности лучника и разбойника, «Засада», «Исчезновение».
        if (!(source.getEntity() instanceof ServerPlayer player) || player == target) return;
        PlayerClass playerClass = RpgManager.get(player).getPlayerClass();
        if (playerClass == PlayerClass.NONE) return;
        boolean melee = source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == player;
        float factor = 1f;
        // Удар в спину — только без тяжёлой брони: в латах к врагу не подкрадёшься.
        if (playerClass == PlayerClass.ROGUE && melee && isBehind(player, target) && ArmorProficiency.backstabAllowed(player)) {
            factor *= ROGUE_BACKSTAB_FACTOR;
            player.serverLevel().sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.7), target.getZ(), 4, 0.2, 0.2, 0.2, 0.05);
        }
        if (playerClass == PlayerClass.ARCHER && source.getDirectEntity() instanceof AbstractArrow) {
            factor *= 1f + archerRangeBonus(player.distanceTo(target));
        }
        if (PlayerStats.has(player, Keystone.AMBUSH) && target.getHealth() >= target.getMaxHealth() * 0.95f) factor *= AMBUSH_FACTOR;
        if (PlayerStats.has(player, Keystone.OPPORTUNIST) && weakened(target)) factor *= OPPORTUNIST_FACTOR;
        if (PlayerStats.has(player, Keystone.STEADY_HANDS) && source.getDirectEntity() instanceof AbstractArrow && standingStill(player)) {
            factor *= STEADY_HANDS_FACTOR;
        }
        if (melee && active(player, VANISH_UNTIL)) {
            factor *= VANISH_FACTOR;
            player.getPersistentData().remove(VANISH_UNTIL);
            player.removeEffect(MobEffects.INVISIBILITY);
        }
        if (factor != 1f) event.setAmount(event.getAmount() * factor);
    }

    /** Цель ослаблена: замедлена, оглушена, отравлена или кровоточит («Добыча слабых»). */
    public static boolean weakened(LivingEntity target) {
        return target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || target.hasEffect(MobEffects.POISON)
                || target.hasEffect(com.heroicpaths.registry.ModEffects.STUN) || target.hasEffect(com.heroicpaths.registry.ModEffects.BLEEDING);
    }

    /** Лучник стоит на месте не меньше секунды («Твёрдая рука»). */
    public static boolean standingStill(Player player) {
        return player.level().getGameTime() - player.getPersistentData().getLong(MOVED_AT) >= STEADY_TICKS;
    }

    /** Запоминает, когда игрок в последний раз сдвинулся с места. */
    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag data = player.getPersistentData();
        long packed = net.minecraft.core.BlockPos.containing(player.getX() * 4, player.getY() * 4, player.getZ() * 4).asLong();
        if (data.getLong(STILL_AT) != packed) {
            data.putLong(STILL_AT, packed);
            data.putLong(MOVED_AT, player.level().getGameTime());
        }
    }

    /** «Второе дыхание»: здоровье упало ниже 30% — сразу четверть здоровья и вся выносливость. */
    @SubscribeEvent
    public static void onDamageTaken(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !player.isAlive() || event.getNewDamage() <= 0
                || !PlayerStats.has(player, Keystone.SECOND_WIND) || player.getHealth() >= player.getMaxHealth() * SECOND_WIND_BELOW) return;
        long now = player.level().getGameTime();
        if (player.getPersistentData().getLong(SECOND_WIND_READY) > now) return;
        player.getPersistentData().putLong(SECOND_WIND_READY, now + SECOND_WIND_COOLDOWN);
        player.heal(player.getMaxHealth() * SECOND_WIND_HEAL);
        RpgManager.restoreResource(player, PlayerStats.maxResource(player));
        player.serverLevel().sendParticles(ParticleTypes.HEART, player.getX(), player.getY(1), player.getZ(), 8, 0.4, 0.4, 0.4, 0.1);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 0.6f);
    }

    /** Бонус «Дальнего выстрела» за расстояние до цели. */
    public static float archerRangeBonus(double distance) {
        return (float) Math.min(ARCHER_MAX_BONUS, Math.max(0, distance - ARCHER_FAR_FROM) * ARCHER_BONUS_PER_BLOCK);
    }

    private static boolean companionNear(ServerPlayer hunter) {
        Mob companion = CompanionManager.find(hunter);
        return companion != null && companion.distanceTo(hunter) <= HUNTER_BOND_RANGE;
    }

    /** Итоговый урон по игроку: щит маны и «Последний рубеж». */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0) return;
        RpgData data = RpgManager.get(player);
        float share = 0;
        if (PlayerStats.has(player, Keystone.ARCANE_SHIELD)) share = ARCANE_SHIELD_SHARE;
        if (active(player, MANA_SHIELD_UNTIL)) share = Math.max(share, MANA_SHIELD_SHARE);
        if (share > 0 && !PlayerStats.paysWithHealth(player)) {
            float absorbed = Math.min(event.getNewDamage() * share, data.getResource() / RESOURCE_PER_DAMAGE);
            if (absorbed > 0) {
                data.setResource(data.getResource() - absorbed * RESOURCE_PER_DAMAGE);
                event.setNewDamage(event.getNewDamage() - absorbed);
                RpgManager.sync(player);
            }
        }
        if (active(player, LAST_STAND_UNTIL) && event.getNewDamage() >= player.getHealth()) {
            event.setNewDamage(Math.max(0, player.getHealth() - 1f));
        }
    }

    /** Воин: удар в ближнем бою восстанавливает выносливость. */
    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        if (event.getNewDamage() <= 0 || !source.is(DamageTypes.PLAYER_ATTACK) || AbilityLogic.isAbilityDamage()
                || !(source.getDirectEntity() instanceof ServerPlayer player) || player == event.getEntity()) return;
        if (RpgManager.get(player).getPlayerClass() == PlayerClass.WARRIOR && !PlayerStats.paysWithHealth(player)) {
            RpgManager.restoreResource(player, WARRIOR_STAMINA_PER_HIT);
        }
    }

    /** «Прилив жизни»: убийство лечит. «Вершина стаи»: убийство зверя лечит и ускоряет охотника и зверя. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof Mob killer && killer != event.getEntity()) {
            ServerPlayer owner = com.heroicpaths.minion.Minions.owner(killer);
            if (owner != null && PlayerStats.has(owner, Keystone.APEX_PREDATOR) && killer == CompanionManager.find(owner)) {
                for (LivingEntity healed : new LivingEntity[]{owner, killer}) {
                    healed.heal(healed.getMaxHealth() * APEX_HEAL);
                    healed.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1));
                }
            }
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player && player != event.getEntity()
                && PlayerStats.has(player, Keystone.VITAL_SURGE)) {
            player.heal(player.getMaxHealth() * VITAL_SURGE_HEAL);
        }
    }

    // ---------------------------------------------------------------- заклинания и баффы

    /** Перед применением умения: маг запоминает стихию для «Резонанса стихий». */
    public static void beforeCast(ServerPlayer player, AbilityDef def) {
        if (RpgManager.get(player).getPlayerClass() != PlayerClass.MAGE || def.kind() != AbilityDef.DamageKind.SPELL
                || !def.element().isClassical()) return;
        CompoundTag data = player.getPersistentData();
        String element = def.element().name();
        String last = data.getString(LAST_ELEMENT);
        if (!last.isEmpty() && !last.equals(element)) {
            data.putString(RESONANCE_ELEMENT, element);
            data.putLong(RESONANCE_UNTIL, player.level().getGameTime() + 60);
        } else {
            data.remove(RESONANCE_UNTIL);
        }
        data.putString(LAST_ELEMENT, element);
    }

    /** Множитель «Резонанса стихий» для урона заклинания. */
    public static float spellFactor(Player player, AbilityDef def) {
        if (def.kind() != AbilityDef.DamageKind.SPELL || !active(player, RESONANCE_UNTIL)) return 1f;
        return def.element().name().equals(player.getPersistentData().getString(RESONANCE_ELEMENT)) ? MAGE_RESONANCE_FACTOR : 1f;
    }

    /** Временная прибавка к шансу крита («Орлиный глаз»). */
    public static float critBonus(Player player) {
        return active(player, EAGLE_EYE_UNTIL) ? EAGLE_EYE_CRIT : 0f;
    }

    /** Есть ли у заклинания резонанс прямо сейчас (для подсказок и тестов). */
    public static boolean resonating(Player player, Element element) {
        return active(player, RESONANCE_UNTIL) && element.name().equals(player.getPersistentData().getString(RESONANCE_ELEMENT));
    }
}
