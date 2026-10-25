package com.heroicpaths.combat;

import com.heroicpaths.fx.Fx;
import com.heroicpaths.fx.Palette;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.ability.AbilityLogic;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Силовая атака: зажать правую кнопку с оружием ближнего боя и отпустить, когда удар заряжен.
 * Бьёт цель перед собой сильнее обычного и задевает врагов вокруг неё; особый эффект оружия (кровотечение,
 * оглушение, пробивание брони) срабатывает с удвоенным шансом. Стоит на 20% больше выносливости, чем полный удар.
 * Чем тяжелее (медленнее) оружие, тем сильнее удар, шире область и дольше перезарядка: от 2 до 5 секунд.
 */
public final class PowerAttack {
    /** Ключ перезарядки в RpgData (синхронизируется с клиентом вместе с перезарядками умений). */
    public static final String COOLDOWN_KEY = "power_attack";
    /** Сколько тиков держать кнопку, чтобы удар зарядился. */
    public static final int CHARGE_TICKS = 12;
    public static final float STAMINA_FACTOR = 1.2f;
    public static final float MIN_DAMAGE_FACTOR = 1.5f, MAX_DAMAGE_FACTOR = 2.5f;
    public static final int MIN_COOLDOWN = 40, MAX_COOLDOWN = 100;
    /** Скорость атаки самого лёгкого и самого тяжёлого оружия (кинжал ~2.4, меч 1.6, топор ~1, двуручник ~0.8). */
    private static final float LIGHT_SPEED = 2.2f, HEAVY_SPEED = 0.8f;
    /** Урон по врагам вокруг цели — доля от удара по цели. */
    public static final float AREA_SHARE = 0.6f;

    private static final ThreadLocal<Boolean> POWER = ThreadLocal.withInitial(() -> false);

    private PowerAttack() {}

    /** Сейчас наносится урон силовой атаки: эффекты оружия срабатывают с двойным шансом. */
    public static boolean isPowerHit() {
        return POWER.get();
    }

    /** Оружие, которым можно провести силовую атаку: ближнего боя и без своего действия на правую кнопку. */
    public static boolean canPowerAttack(ItemStack stack) {
        return WeaponType.of(stack) != null && stack.getUseAnimation() == UseAnim.NONE;
    }

    /** Тяжесть оружия от 0 (кинжал) до 1 (двуручник, боевой молот). */
    public static float heaviness(ItemStack stack) {
        return Mth.clamp((LIGHT_SPEED - Stamina.weaponSpeed(stack)) / (LIGHT_SPEED - HEAVY_SPEED), 0f, 1f);
    }

    public static float damageFactor(ItemStack stack) {
        return Mth.lerp(heaviness(stack), MIN_DAMAGE_FACTOR, MAX_DAMAGE_FACTOR);
    }

    /** Перезарядка в тиках: 2 секунды у лёгкого оружия, 5 — у тяжёлого; «Бойня» сокращает её вдвое. */
    public static int cooldown(Player player, ItemStack stack) {
        int ticks = Math.round(Mth.lerp(heaviness(stack), MIN_COOLDOWN, MAX_COOLDOWN));
        return com.heroicpaths.rpg.ability.UniqueAbilities.slaughterActive(player) ? ticks / 2 : ticks;
    }

    public static double areaRadius(ItemStack stack) {
        return 2.0 + heaviness(stack);
    }

    public static float staminaCost(Player player) {
        return Stamina.meleeCost(player, 1f) * STAMINA_FACTOR;
    }

    /** Сколько тиков осталось до готовности (0 — готова). */
    public static long remaining(Player player) {
        return Math.max(0, RpgManager.get(player).getCooldownEnd(COOLDOWN_KEY) - player.level().getGameTime());
    }

    /** Провести силовую атаку. Возвращает true, если удар состоялся. */
    public static boolean perform(ServerPlayer player, int heldTicks) {
        if (!player.isAlive() || player.isSpectator() || heldTicks < CHARGE_TICKS) return false;
        ItemStack weapon = player.getMainHandItem();
        boolean werewolf = com.heroicpaths.rpg.ability.UniqueAbilities.werewolf(player);
        if (!werewolf && !canPowerAttack(weapon)) return false;
        RpgData data = RpgManager.get(player);
        long now = player.level().getGameTime();
        if (now < data.getCooldownEnd(COOLDOWN_KEY)) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.power_attack_cooldown",
                    (data.getCooldownEnd(COOLDOWN_KEY) - now + 19) / 20).withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (!com.heroicpaths.rpg.ability.UniqueAbilities.freeStamina(player)) Stamina.spend(player, staminaCost(player));
        data.setCooldownEnd(COOLDOWN_KEY, now + cooldown(player, weapon));

        ServerLevel level = player.serverLevel();
        float factor = werewolf ? MAX_DAMAGE_FACTOR : damageFactor(weapon);
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * factor;
        double radius = werewolf ? 3.0 : areaRadius(weapon);
        LivingEntity target = AbilityLogic.targetInFront(player, player.entityInteractionRange() + 0.5);
        Vec3 center = target != null ? target.position() : player.position().add(AbilityLogic.horizontalLook(player).scale(2));
        player.swing(InteractionHand.MAIN_HAND, true);
        POWER.set(true);
        try {
            if (target != null) strike(player, target, damage, werewolf);
            for (LivingEntity other : AbilityLogic.enemiesInRadius(player, center, radius)) {
                if (other != target) strike(player, other, damage * AREA_SHARE, werewolf);
            }
        } finally {
            POWER.set(false);
        }
        player.resetAttackStrengthTicker();
        Fx.shockwave(level, Palette.STEEL, center, radius, 6);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y + 1, center.z, 3, radius * 0.4, 0.2, radius * 0.4, 0);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2f, 0.7f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1f, 0.6f);
        RpgManager.sync(player);
        return true;
    }

    private static void strike(ServerPlayer player, LivingEntity target, float damage, boolean werewolf) {
        target.invulnerableTime = 0;
        if (!target.hurt(player.damageSources().playerAttack(player), damage)) return;
        Vec3 away = target.position().subtract(player.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1e-4) return;
        away = away.normalize();
        // Заряженная атака волколака отбрасывает врагов на ~10 блоков.
        double strength = werewolf ? 3.0 : 0.6;
        target.knockback(strength, -away.x, -away.z);
    }

    @Nullable
    public static Component describe(ItemStack stack) {
        if (!canPowerAttack(stack)) return null;
        return Component.translatable("tooltip.heroicpaths.power_attack", Math.round(damageFactor(stack) * 100),
                String.format(java.util.Locale.ROOT, "%.1f", Mth.lerp(heaviness(stack), MIN_COOLDOWN, MAX_COOLDOWN) / 20f));
    }
}
