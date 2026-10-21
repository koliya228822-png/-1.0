package com.heroicpaths.combat;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.DungeonBoss;
import com.heroicpaths.registry.ModDamageTypes;
import com.heroicpaths.registry.ModEffects;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.ability.AbilityLogic;
import com.heroicpaths.rpg.stat.Stat;
import net.minecraft.world.entity.projectile.Projectile;
import com.heroicpaths.rpg.ability.DelayedTasks;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Бой оружием ближнего боя:
 * <ul>
 *     <li>колющее оружие пробивает 35% брони цели;</li>
 *     <li>режущее с шансом 25% вызывает кровотечение (до 3 уровней), но не у бескровных — скелетов, големов, ифритов;</li>
 *     <li>дробящее с шансом 15% оглушает (кроме боссов) и бьёт бескровных на 25% сильнее;</li>
 *     <li>парное оружие: с одноручным оружием в обеих руках каждый удар добавляет половину урона второго оружия,
 *     а эффект его типа тоже срабатывает;</li>
 *     <li>двуручное оружие с занятой второй рукой бьёт вдвое слабее.</li>
 * </ul>
 * Эффекты срабатывают на сильных ударах (полностью заряженных) и на ударах умений ближнего боя.
 */
public final class WeaponCombat {
    public static final float PIERCING_ARMOR_IGNORED = 0.35f;
    public static final float BLEED_CHANCE = 0.25f;
    public static final int BLEED_TICKS = 80;
    public static final int MAX_BLEED_LEVEL = 3;
    public static final float STUN_CHANCE = 0.15f;
    public static final int STUN_TICKS = 30;
    public static final float CRUSHING_BONELESS_BONUS = 0.25f;
    public static final float DUAL_FACTOR = 0.5f;
    public static final float TWO_HANDED_BUSY_FACTOR = 0.5f;
    /** Минимальная зарядка удара, при которой срабатывают эффекты оружия. */
    public static final float STRONG_HIT = 0.9f;
    public static final TagKey<EntityType<?>> BLOODLESS = TagKey.create(Registries.ENTITY_TYPE, HeroicPaths.id("bloodless"));

    private static final Map<Player, Float> ATTACK_STRENGTH = new WeakHashMap<>();

    private WeaponCombat() {}

    /** Зарядку удара запоминаем до того, как игра её сбросит. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide) ATTACK_STRENGTH.put(player, player.getAttackStrengthScale(0.5f));
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        LivingEntity target = event.getEntity();
        // Кровотечение не даёт неуязвимости после удара, иначе оно мешало бы бить цель.
        if (source.is(ModDamageTypes.BLEEDING)) {
            event.setInvulnerabilityTicks(0);
            return;
        }
        // Оглушённый не может ранить.
        if (source.getEntity() instanceof LivingEntity attacker && attacker != target && attacker.hasEffect(ModEffects.STUN)) {
            event.setCanceled(true);
            return;
        }
        // Стрелы и болты: пробивание брони из дерева.
        if (source.getEntity() instanceof ServerPlayer shooter && source.getDirectEntity() instanceof Projectile && shooter != target) {
            pierceArmor(event, PlayerStats.get(shooter).get(Stat.ARMOR_PIERCE) / 100f, PlayerStats.get(shooter).get(Stat.FLAT_ARMOR_PIERCE));
            return;
        }
        ServerPlayer player = meleeAttacker(source, target);
        if (player == null) return;

        boolean ability = AbilityLogic.isAbilityDamage();
        ItemStack main = player.getMainHandItem();
        ItemStack off = secondWeapon(player);
        boolean dual = !ability && isDualWielding(player);
        float amount = event.getAmount();
        if (!ability) {
            if (WeaponType.isTwoHanded(main) && !player.getOffhandItem().isEmpty()) amount *= TWO_HANDED_BUSY_FACTOR;
            if (dual) {
                amount += (float) offhandDamage(off) * dualFactor(player) * Math.max(0.2f, strength(player))
                        + PlayerStats.get(player).get(Stat.FLAT_DUAL_WIELD_DAMAGE);
                // Парный кинжал — тот же предмет, что в основной руке: прочность тратится один раз, взмах рисует анимация пары.
                if (!WeaponType.isPaired(main)) strikeWithOffhand(player, off);
            }
        }
        WeaponType mainType = WeaponType.of(main);
        WeaponType offType = dual ? WeaponType.of(off) : null;
        if ((mainType == WeaponType.CRUSHING || offType == WeaponType.CRUSHING) && isBloodless(target)) {
            amount *= 1f + CRUSHING_BONELESS_BONUS;
        }
        event.setAmount(amount);
        boolean piercing = mainType == WeaponType.PIERCING || offType == WeaponType.PIERCING;
        pierceArmor(event, (piercing ? PIERCING_ARMOR_IGNORED : 0f) + PlayerStats.get(player).get(Stat.ARMOR_PIERCE) / 100f,
                PlayerStats.get(player).get(Stat.FLAT_ARMOR_PIERCE));
    }

    /** Доля брони цели, которую удар не учитывает (не больше 80%). */
    private static void pierceArmor(LivingIncomingDamageEvent event, float share, float flatPoints) {
        float ignored = Math.min(MAX_ARMOR_IGNORED, share);
        // Числовое пробивание дерева: столько единиц брони цели удар не учитывает.
        float armor = event.getEntity().getArmorValue();
        float flatKeep = flatPoints > 0 && armor > 0 ? Math.max(0, armor - flatPoints) / armor : 1f;
        float keep = (1f - ignored) * flatKeep;
        if (keep < 1f) event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> reduction * keep);
    }

    public static final float MAX_ARMOR_IGNORED = 0.8f;

    /** Доля урона второго оружия: 50% плюс прибавка из дерева. */
    public static float dualFactor(Player player) {
        return DUAL_FACTOR + PlayerStats.get(player).get(Stat.DUAL_WIELD_DAMAGE) / 100f;
    }

    public static float bleedChance(Player player) {
        return BLEED_CHANCE + PlayerStats.get(player).get(Stat.BLEED_CHANCE) / 100f;
    }

    public static float stunChance(Player player) {
        return STUN_CHANCE + PlayerStats.get(player).get(Stat.STUN_CHANCE) / 100f;
    }

    /** Кровотечение и оглушение — только если удар действительно ранил цель. */
    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0) return;
        LivingEntity target = event.getEntity();
        ServerPlayer player = meleeAttacker(event.getSource(), target);
        if (player == null) return;
        boolean ability = AbilityLogic.isAbilityDamage();
        if (!ability && strength(player) < STRONG_HIT) return;
        rollEffect(player, target, WeaponType.of(player.getMainHandItem()));
        if (!ability && isDualWielding(player)) rollEffect(player, target, WeaponType.of(secondWeapon(player)));
    }

    private static void rollEffect(ServerPlayer player, LivingEntity target, @Nullable WeaponType type) {
        if (type == WeaponType.SLASHING && player.getRandom().nextFloat() < bleedChance(player)) applyBleeding(player, target);
        if (type == WeaponType.CRUSHING && player.getRandom().nextFloat() < stunChance(player)) applyStun(player, target);
    }

    /** Удар оружием из руки игрока: обычный удар или удар умения ближнего боя. */
    @Nullable
    private static ServerPlayer meleeAttacker(DamageSource source, LivingEntity target) {
        if (!source.is(DamageTypes.PLAYER_ATTACK) || !(source.getDirectEntity() instanceof ServerPlayer player)) return null;
        return player == target ? null : player;
    }

    private static float strength(Player player) {
        return ATTACK_STRENGTH.getOrDefault(player, 1f);
    }

    /** В обеих руках одноручное оружие ближнего боя — или парные кинжалы. */
    public static boolean isDualWielding(Player player) {
        return WeaponType.isPaired(player.getMainHandItem())
                || WeaponType.isOneHanded(player.getMainHandItem()) && WeaponType.isOneHanded(player.getOffhandItem());
    }

    /** Оружие второй руки: у парных кинжалов это второй такой же кинжал, иначе — предмет во второй руке. */
    public static ItemStack secondWeapon(Player player) {
        return WeaponType.isPaired(player.getMainHandItem()) ? player.getMainHandItem() : player.getOffhandItem();
    }

    public static boolean isBloodless(LivingEntity entity) {
        return entity.getType().is(BLOODLESS);
    }

    /** Урон оружия во второй руке: базовый урон атаки + бонус этого оружия (с качеством). */
    public static double offhandDamage(ItemStack offhand) {
        double[] damage = {1.0};
        offhand.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                damage[0] += modifier.amount();
            }
        });
        return damage[0];
    }

    /** Второе оружие тоже бьёт: тратит прочность и делает взмах чуть позже первого. */
    private static void strikeWithOffhand(ServerPlayer player, ItemStack offhand) {
        offhand.hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
        DelayedTasks.schedule(4, () -> {
            if (player.isAlive() && !player.hasDisconnected()) player.swing(InteractionHand.OFF_HAND, true);
        });
    }

    /** Кровотечение: каждое новое ранение повышает уровень (до 3) и обновляет длительность. */
    public static boolean applyBleeding(@Nullable Entity attacker, LivingEntity target) {
        if (!target.isAlive() || isBloodless(target)) return false;
        MobEffectInstance current = target.getEffect(ModEffects.BLEEDING);
        int amplifier = current == null ? 0 : Math.min(MAX_BLEED_LEVEL - 1, current.getAmplifier() + 1);
        target.addEffect(new MobEffectInstance(ModEffects.BLEEDING, BLEED_TICKS, amplifier), attacker);
        if (attacker != null) target.getPersistentData().putUUID(BleedingEffect.SOURCE_KEY, attacker.getUUID());
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(new DustParticleOptions(new Vector3f(0.6f, 0.02f, 0.02f), 1.2f),
                    target.getX(), target.getY(0.6), target.getZ(), 12, 0.25, 0.3, 0.25, 0.0);
        }
        return true;
    }

    /** Оглушение: боссы данжей к нему невосприимчивы. */
    public static boolean applyStun(@Nullable Entity attacker, LivingEntity target) {
        if (!target.isAlive() || target instanceof DungeonBoss) return false;
        target.addEffect(new MobEffectInstance(ModEffects.STUN, STUN_TICKS, 0), attacker);
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(1.0) + 0.3, target.getZ(), 10, 0.3, 0.1, 0.3, 0.05);
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.PLAYERS, 0.4f, 1.4f);
        }
        return true;
    }
}
