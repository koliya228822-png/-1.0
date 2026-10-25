package com.heroicpaths.rpg.ability;

import com.heroicpaths.entity.MeteorEntity;
import com.heroicpaths.entity.SpellProjectile;
import com.heroicpaths.fx.Fx;
import com.heroicpaths.fx.Palette;
import com.heroicpaths.registry.ModParticles;
import com.heroicpaths.minion.Minion;
import com.heroicpaths.minion.Minions;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.Subclass;
import com.heroicpaths.rpg.ability.AbilityDef.DamageKind;
import com.heroicpaths.rpg.stat.Stat;
import com.heroicpaths.rpg.stat.StatBlock;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Реализация всех активных способностей. Метод возвращает false, если способность не сработала. */
public final class AbilityLogic {
    public static final String ARROW_EFFECT = "heroicpaths.arrow_effect";
    public static final String ARROW_POWER = "heroicpaths.arrow_power";
    public static final String MARKED_UNTIL = "heroicpaths.marked_until";
    /** Метка стрел умений: такие стрелы не задевают своих — группу, своих слуг и питомцев (см. onProjectileImpact). */
    public static final String ABILITY_PROJECTILE = "heroicpaths.ability_projectile";

    private static final ThreadLocal<Boolean> IN_ABILITY_DAMAGE = ThreadLocal.withInitial(() -> false);

    private AbilityLogic() {}

    /** true, пока наносится урон способности: обработчик урона не должен применять бонусы второй раз. */
    public static boolean isAbilityDamage() {
        return IN_ABILITY_DAMAGE.get();
    }

    private static final ThreadLocal<com.heroicpaths.rpg.stat.Element> ABILITY_ELEMENT = new ThreadLocal<>();

    /** Стихия умения, урон которого наносится прямо сейчас (для добиваний); null — урон не от умения. */
    @Nullable
    public static com.heroicpaths.rpg.stat.Element abilityElement() {
        return IN_ABILITY_DAMAGE.get() ? ABILITY_ELEMENT.get() : null;
    }

    public static boolean cast(ServerPlayer player, AbilityDef def) {
        return switch (def.id()) {
            case "charge" -> charge(player, def);
            case "whirlwind" -> whirlwind(player, def);
            case "war_cry" -> warCry(player, def);
            case "holy_shield" -> holyShield(player, def);
            case "smite" -> smite(player, def);
            case "taunt" -> taunt(player, def);
            case "shield_slam" -> shieldSlam(player, def);
            case "blood_frenzy" -> bloodFrenzy(player, def);
            case "execute" -> execute(player, def);
            case "volley" -> volley(player, def);
            case "disengage" -> disengage(player, def);
            case "arrow_rain" -> arrowRain(player, def);
            case "aimed_shot" -> aimedShot(player, def);
            case "hunters_mark" -> huntersMark(player, def);
            case "poison_arrow" -> effectArrow(player, def, "poison", 2.5f, 2f);
            case "bear_trap" -> bearTrap(player, def);
            case "rapid_fire" -> rapidFire(player, def);
            case "thunder_arrow" -> effectArrow(player, def, "thunder", 2.5f, 8f);
            case "fireball" -> bolt(player, def, SpellProjectile.Kind.FIRE, 6f, 1.8f);
            case "frost_nova" -> frostNova(player, def);
            case "blink" -> blink(player, def);
            case "meteor" -> meteor(player, def);
            case "flame_wave" -> flameWave(player, def);
            case "ice_lance" -> bolt(player, def, SpellProjectile.Kind.ICE, 7f, 2.6f);
            case "healing_rain" -> healingRain(player, def);
            case "stone_skin" -> stoneSkin(player, def);
            case "earthquake" -> earthquake(player, def);
            case "chain_lightning" -> chainLightning(player, def);
            case "tornado" -> tornado(player, def);
            default -> SubclassAbilities.cast(player, def);
        };
    }

    // ================================================================ сила и урон

    public static float levelScale(Player player) {
        return 1f + 0.03f * (RpgManager.get(player).getLevel() - 1);
    }

    /** Сила способности: база × множитель уровня × бонусы характеристик × ранг умения. */
    public static float power(ServerPlayer player, AbilityDef def, float base) {
        StatBlock stats = PlayerStats.get(player);
        float bonus = stats.get(Stat.ABILITY_DAMAGE) + PlayerStats.elementBonus(player, def.element());
        bonus += switch (def.kind()) {
            case SPELL -> stats.get(Stat.SPELL_DAMAGE);
            case MELEE -> stats.get(Stat.MELEE_DAMAGE);
            case RANGED -> 0; // бонус стрелкового оружия добавится при попадании стрелы
            case MINION -> stats.get(Stat.MINION_DAMAGE);
        };
        // «Кровавый договор» мага крови: +30% к силе заклинаний на время действия.
        if (player.getPersistentData().getLong(SubclassAbilities.BLOOD_PACT_UNTIL) > player.level().getGameTime()) bonus += 30;
        return base * levelScale(player) * Math.max(0.1f, 1f + bonus / 100f) * RpgManager.rankPower(player, def);
    }

    /** Общий множитель урона умений всех классов (балансная правка: урон вдвое меньше). */
    public static final float CLASS_DAMAGE_FACTOR = 0.5f;

    /**
     * Множитель урона умений игрока: общий множитель классов, а у магов — ещё и сила жезла или посоха
     * (без фокуса маг не наносит урона).
     */
    public static float damageFactor(Player player) {
        float factor = CLASS_DAMAGE_FACTOR;
        if (RpgManager.get(player).getPlayerClass() == com.heroicpaths.rpg.PlayerClass.MAGE) {
            factor *= com.heroicpaths.item.FocusItem.damageFactor(player);
        }
        return factor;
    }

    /** То же, плюс бонус рунного фокуса к заклинаниям его стихии. */
    public static float damageFactor(Player player, AbilityDef def) {
        float factor = damageFactor(player);
        if (def.kind() == DamageKind.SPELL && RpgManager.get(player).getPlayerClass() == com.heroicpaths.rpg.PlayerClass.MAGE) {
            factor *= com.heroicpaths.item.FocusItem.elementFactor(player, def.element());
            factor *= com.heroicpaths.rpg.ClassTraits.spellFactor(player, def);
            if (PlayerStats.has(player, com.heroicpaths.rpg.stat.Keystone.OVERCHANNEL)) factor *= RpgManager.OVERCHANNEL_DAMAGE;
        }
        return factor;
    }

    /** Наносит урон способности с шансом крита. Заклинания мага крови лечат его (вампиризм). */
    public static void hit(ServerPlayer player, LivingEntity target, float amount, AbilityDef def) {
        float scaled = amount * damageFactor(player, def);
        // Числовые прибавки дерева — к каждому удару (если умение вообще наносит урон: маг без фокуса — нет).
        if (scaled > 0) scaled += PlayerStats.flatAbilityDamage(player, def);
        amount = rollCrit(player, target, scaled);
        if (amount <= 0) return;
        DamageSource source = def.kind() == DamageKind.SPELL
                ? player.damageSources().indirectMagic(player, player)
                : player.damageSources().playerAttack(player);
        IN_ABILITY_DAMAGE.set(true);
        ABILITY_ELEMENT.set(def.element());
        boolean dealt;
        float before = target.getHealth();
        try {
            dealt = target.hurt(source, amount);
        } finally {
            IN_ABILITY_DAMAGE.set(false);
            ABILITY_ELEMENT.remove();
        }
        if (dealt && def.kind() == DamageKind.SPELL && RpgManager.get(player).getSubclass() == Subclass.BLOOD_MAGE) {
            float taken = Math.max(0, before - target.getHealth());
            player.heal(taken * (BLOOD_MAGE_LEECH + PlayerStats.get(player).get(Stat.LIFE_STEAL) / 100f));
        }
    }

    /** Доля урона заклинаний, которую маг крови возвращает себе здоровьем. */
    public static final float BLOOD_MAGE_LEECH = 0.15f;

    static float rollCrit(ServerPlayer player, LivingEntity target, float amount) {
        if (player.getRandom().nextFloat() * 100f >= PlayerStats.critChance(player)) return amount;
        player.serverLevel().sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
        return PlayerStats.applyCrit(player, amount);
    }

    static void heal(ServerPlayer caster, LivingEntity target, float amount) {
        target.heal(amount * PlayerStats.healingFactor(caster) + PlayerStats.get(caster).get(Stat.FLAT_HEALING));
    }

    /** Эффект поглощения: уровень эффекта подбирается под нужное число единиц здоровья. */
    static void absorption(LivingEntity target, float amount, int ticks) {
        int amplifier = Math.max(0, Math.min(9, Math.round(amount / 4f) - 1));
        target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks, amplifier));
    }

    // ================================================================ мечник

    private static boolean charge(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 dir = horizontalLook(player);
        player.setDeltaMovement(dir.x * 1.9, 0.25, dir.z * 1.9);
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1));
        float damage = power(player, def, 6f);
        for (LivingEntity target : targetsIn(player, player.getBoundingBox().expandTowards(dir.scale(5)).inflate(1))) {
            hit(player, target, damage, def);
            target.knockback(0.8, -dir.x, -dir.z);
            Fx.burst(level, Palette.STEEL, target.position().add(0, target.getBbHeight() / 2, 0), 0.6);
        }
        // Рывок оставляет след: пыль из-под ног и искры.
        DelayedTasks.repeat(0, 1, 8, () -> {
            Fx.wisps(level, Palette.EARTH, player.position().add(0, 0.2, 0), 2, 0.2, 0.01);
            Fx.embers(level, Palette.STEEL, player.position().add(0, 1, 0), 2, 0.3, 0.05);
        });
        Fx.debris(level, player.position(), 15, 0.4, 0.1);
        sound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
        return true;
    }

    private static boolean whirlwind(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        double radius = areaRadius(player, def);
        float damage = power(player, def, 7f);
        for (LivingEntity target : targetsInRadius(player, player.position(), radius)) {
            hit(player, target, damage, def);
            Vec3 away = target.position().subtract(player.position()).normalize();
            target.knockback(0.6, -away.x, -away.z);
            Fx.embers(level, Palette.STEEL, target.position().add(0, target.getBbHeight() / 2, 0), 8, 0.2, 0.2);
        }
        // Два клинка-вихря обходят круг, от ног расходится волна.
        int[] step = {0};
        DelayedTasks.repeat(0, 1, 8, () -> {
            double base = step[0]++ * 0.9;
            for (int blade = 0; blade < 2; blade++) {
                double angle = base + blade * Math.PI;
                for (double r = 1; r <= radius; r += 0.6) {
                    Vec3 p = player.position().add(Math.cos(angle) * r, 1, Math.sin(angle) * r);
                    Fx.one(level, ModParticles.mark(Palette.STEEL), p, new Vec3(-Math.sin(angle) * 0.2, 0, Math.cos(angle) * 0.2));
                }
            }
        });
        Fx.shockwave(level, Palette.STEEL, player.position(), radius, 6);
        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2 * i / 12;
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + Math.cos(angle) * radius * 0.65,
                    player.getY() + 1, player.getZ() + Math.sin(angle) * radius * 0.65, 1, 0, 0, 0, 0);
        }
        sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2f, 0.8f);
        return true;
    }

    private static boolean warCry(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        int levelBonus = RpgManager.get(player).getLevel();
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 320, Math.min(2, levelBonus / 30)));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 320, 0));
        // Рёв: огненная волна от воина и ярость на всё время усиления.
        Fx.shockwave(level, Palette.FIRE, player.position(), 6, 8);
        Fx.nova(level, ModParticles.ember(Palette.FIRE), player.position().add(0, 1.2, 0), 40, 0.35, player.getRandom());
        Fx.aura(player, Palette.FIRE, 320, 4);
        particles(player, ParticleTypes.ANGRY_VILLAGER, player.position().add(0, 1.2, 0), 10, 0.6, 0);
        sound(player, SoundEvents.RAVAGER_ROAR, 1f, 1.2f);
        return true;
    }

    private static boolean holyShield(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        float amount = power(player, def, 6f) * PlayerStats.healingFactor(player);
        for (Player ally : player.serverLevel().getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(6),
                p -> p.isAlive() && !p.isSpectator())) {
            absorption(ally, amount, 200);
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
            Fx.spiral(level, ModParticles.mark(Palette.HOLY), ally.position(), 0.9, 2.2, 2, 30, 0);
            Fx.flash(level, Palette.HOLY, ally.position().add(0, 1, 0), 1);
            Fx.aura(ally, Palette.HOLY, 200, 5);
        }
        Fx.shockwave(level, Palette.HOLY, player.position(), 6, 6);
        sound(player, SoundEvents.BEACON_POWER_SELECT, 1f, 1.4f);
        return true;
    }

    private static boolean smite(ServerPlayer player, AbilityDef def) {
        LivingEntity target = targetInFront(player, 6);
        if (target == null) return noTarget(player);
        ServerLevel level = player.serverLevel();
        float damage = power(player, def, 9f);
        if (target.getType().is(EntityTypeTags.UNDEAD)) damage *= 1.5f;
        hit(player, target, damage, def);
        heal(player, player, damage * 0.3f);
        // Столб света с неба в цель.
        Fx.skyBeam(level, Palette.HOLY, target.position(), 10);
        Fx.burst(level, Palette.HOLY, target.position().add(0, target.getBbHeight() / 2, 0), 1);
        Fx.shockwave(level, Palette.HOLY, target.position(), 2.5, 5);
        sound(player, SoundEvents.TRIDENT_THUNDER.value(), 0.6f, 1.6f);
        return true;
    }

    private static boolean taunt(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(10),
                mob -> mob instanceof Enemy && mob.isAlive())) {
            mob.setTarget(player);
            Fx.embers(level, Palette.BLOOD, mob.position().add(0, mob.getBbHeight() + 0.3, 0), 6, 0.2, 0.05);
        }
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 1));
        // Три волны вызова.
        DelayedTasks.repeat(0, 6, 3, () -> Fx.shockwave(level, Palette.BLOOD, player.position(), 10, 8));
        particles(player, ParticleTypes.ANGRY_VILLAGER, player.position().add(0, 2, 0), 12, 0.8, 0);
        sound(player, SoundEvents.RAVAGER_ROAR, 1.2f, 0.7f);
        return true;
    }

    private static boolean shieldSlam(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        float damage = power(player, def, 7f);
        Vec3 dir = horizontalLook(player);
        for (LivingEntity target : inCone(player, 4.5, 0.5)) {
            hit(player, target, damage, def);
            target.knockback(1.5, -dir.x, -dir.z);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
            Fx.burst(level, Palette.STEEL, target.position().add(0, target.getBbHeight() / 2, 0), 0.6);
        }
        // Удар щитом — вспышка и веер искр вперёд.
        Vec3 front = player.position().add(dir.scale(1.5)).add(0, 1, 0);
        Fx.flash(level, Palette.STEEL, front, 1);
        for (int i = 0; i < 24; i++) {
            Vec3 v = dir.yRot((float) ((player.getRandom().nextDouble() - 0.5) * 1.6)).scale(0.4 + player.getRandom().nextDouble() * 0.3);
            Fx.one(level, ModParticles.ember(Palette.STEEL), front, v.add(0, player.getRandom().nextDouble() * 0.15, 0));
        }
        sound(player, SoundEvents.SHIELD_BLOCK, 1.2f, 0.6f);
        return true;
    }

    private static boolean bloodFrenzy(ServerPlayer player, AbilityDef def) {
        float cost = player.getHealth() * 0.2f;
        if (player.getHealth() <= 4f) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.too_weak"), true);
            return false;
        }
        player.setHealth(player.getHealth() - cost);
        int level = RpgManager.get(player).getLevel();
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, level >= 50 ? 1 : 0));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 1));
        Fx.burst(player.serverLevel(), Palette.BLOOD, player.position().add(0, 1, 0), 0.8);
        Fx.aura(player, Palette.BLOOD, 200, 3);
        sound(player, SoundEvents.PLAYER_HURT, 1f, 0.6f);
        return true;
    }

    private static boolean execute(ServerPlayer player, AbilityDef def) {
        LivingEntity target = targetInFront(player, 4.5);
        if (target == null) return noTarget(player);
        ServerLevel level = player.serverLevel();
        float damage = power(player, def, 12f);
        boolean finishing = target.getHealth() / target.getMaxHealth() < 0.35f;
        if (finishing) damage *= 2f;
        hit(player, target, damage, def);
        // Косой рубящий след через цель и кровь; добивание — сильнее.
        Vec3 c = target.position().add(0, target.getBbHeight() / 2, 0);
        Vec3 side = new Vec3(-horizontalLook(player).z, 0, horizontalLook(player).x);
        Fx.line(level, ModParticles.mark(Palette.STEEL), c.add(side.scale(0.8)).add(0, 0.8, 0), c.subtract(side.scale(0.8)).add(0, -0.8, 0), 8);
        Fx.burst(level, Palette.BLOOD, c, finishing ? 1.3 : 0.6);
        sound(player, SoundEvents.PLAYER_ATTACK_CRIT, 1.2f, 0.6f);
        return true;
    }

    // ================================================================ лучник

    /** Вспышка у лука: искры и ветер вперёд. */
    static void bowFlash(ServerPlayer player, Palette palette) {
        Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(1.0)).add(0, -0.2, 0);
        Fx.embers(player.serverLevel(), palette, at, 8, 0.1, 0.12);
        Fx.wisps(player.serverLevel(), palette, player.getEyePosition().add(player.getLookAngle().scale(2.5)), 2, 0.05, 0.02);
    }

    private static boolean volley(ServerPlayer player, AbilityDef def) {
        int count = 5;
        float spread = 30f;
        for (int i = 0; i < count; i++) {
            Arrow arrow = abilityArrow(player, def, 2.2f);
            arrow.shootFromRotation(player, player.getXRot(), player.getYRot() - spread / 2 + spread * i / (count - 1), 0f, 3.0f, 1.0f);
            player.serverLevel().addFreshEntity(arrow);
        }
        bowFlash(player, Palette.STEEL);
        sound(player, SoundEvents.ARROW_SHOOT, 1f, 1.2f);
        return true;
    }

    private static boolean disengage(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 back = horizontalLook(player).scale(-1.6);
        player.setDeltaMovement(back.x, 0.55, back.z);
        player.hurtMarked = true;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
        Fx.burst(level, Palette.STORM, player.position().add(0, 0.5, 0), 0.6);
        DelayedTasks.repeat(0, 1, 8, () -> Fx.wisps(level, Palette.STORM, player.position().add(0, 0.8, 0), 1, 0.2, 0.01));
        sound(player, SoundEvents.ENDER_DRAGON_FLAP, 0.6f, 1.5f);
        return true;
    }

    /** Дождь стрел по схеме меток: место отмечено и замедлено, стрелы падают через 1.5 с. */
    private static boolean arrowRain(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 center = groundTarget(player, def);
        double radius = areaRadius(player, def);
        int arrows = 12 + 4 * rank(player, def);
        Zones.mark(player, center, radius, Palette.STEEL, Zones.MARK_TICKS);
        DelayedTasks.schedule(Zones.IMPACT_DELAY - 4, () -> {
            BlockHitResult ceiling = level.clip(new ClipContext(center.add(0, 0.5, 0), center.add(0, 12, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            double spawnY = ceiling.getType() == HitResult.Type.MISS ? center.y + 9 : ceiling.getLocation().y - 1.0;
            for (int i = 0; i < arrows; i++) {
                double angle = player.getRandom().nextDouble() * Math.PI * 2, r = Math.sqrt(player.getRandom().nextDouble()) * radius;
                Arrow arrow = new Arrow(level, center.x + Math.cos(angle) * r, spawnY + player.getRandom().nextDouble() * 2,
                        center.z + Math.sin(angle) * r, new ItemStack(Items.ARROW), heldBow(player));
                arrow.setOwner(player);
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                arrow.getPersistentData().putBoolean(ABILITY_PROJECTILE, true);
                arrow.setCritArrow(true);
                arrow.setBaseDamage(power(player, def, 2.0f) * CLASS_DAMAGE_FACTOR * com.heroicpaths.item.GradedBowItem.damageFactor(heldBow(player)));
                arrow.shoot(0, -1, 0, 2.4f, 2f);
                level.addFreshEntity(arrow);
            }
            level.sendParticles(ParticleTypes.CRIT, center.x, spawnY, center.z, 40, radius * 0.5, 0.5, radius * 0.5, 0.1);
            Fx.sound(level, center, SoundEvents.ARROW_SHOOT, 1.4f, 0.6f);
        });
        DelayedTasks.schedule(Zones.IMPACT_DELAY, () -> {
            Fx.debris(level, center, 30, radius * 0.5, 0.15);
            Fx.embers(level, Palette.STEEL, center.add(0, 0.3, 0), 30, radius * 0.5, 0.15);
        });
        bowFlash(player, Palette.STEEL);
        sound(player, SoundEvents.ARROW_SHOOT, 1.2f, 0.7f);
        return true;
    }

    private static boolean aimedShot(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        ItemStack piercingWeapon = new ItemStack(Items.CROSSBOW);
        Holder<Enchantment> piercing = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.PIERCING);
        piercingWeapon.enchant(piercing, 4);
        Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), piercingWeapon);
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        arrow.getPersistentData().putBoolean(ABILITY_PROJECTILE, true);
        arrow.setBaseDamage(power(player, def, 3.0f) * CLASS_DAMAGE_FACTOR);
        arrow.setCritArrow(true);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 4.5f, 0f);
        level.addFreshEntity(arrow);
        // След прицельного выстрела: прямая линия света по траектории.
        Vec3 eye = player.getEyePosition();
        Fx.line(level, ModParticles.mark(Palette.STORM), eye.add(player.getLookAngle()), eye.add(player.getLookAngle().scale(18)), 3);
        bowFlash(player, Palette.STORM);
        sound(player, SoundEvents.CROSSBOW_SHOOT, 1.2f, 0.7f);
        return true;
    }

    private static boolean huntersMark(ServerPlayer player, AbilityDef def) {
        LivingEntity target = targetInFront(player, 32);
        if (target == null) return noTarget(player);
        ServerLevel level = player.serverLevel();
        target.getPersistentData().putLong(MARKED_UNTIL, player.level().getGameTime() + 200);
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
        // Над целью кружит знак охотника.
        DelayedTasks.repeat(0, 4, 50, () -> {
            if (!target.isAlive()) return;
            double spin = level.getGameTime() * 0.25;
            Vec3 top = target.position().add(0, target.getBbHeight() + 0.5, 0);
            for (int i = 0; i < 6; i++) {
                double angle = spin + i * Math.PI / 3;
                Fx.one(level, ModParticles.mark(Palette.BLOOD), top.add(Math.cos(angle) * 0.45, 0, Math.sin(angle) * 0.45), Vec3.ZERO);
            }
        });
        Fx.line(level, ModParticles.mark(Palette.BLOOD), player.getEyePosition(), target.position().add(0, target.getBbHeight() / 2, 0), 2);
        sound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
        return true;
    }

    /** Стрела с эффектом при попадании (яд или молния), см. onProjectileImpact. */
    private static boolean effectArrow(ServerPlayer player, AbilityDef def, String effect, float arrowBase, float effectBase) {
        Arrow arrow = abilityArrow(player, def, arrowBase);
        CompoundTag data = arrow.getPersistentData();
        data.putString(ARROW_EFFECT, effect);
        data.putFloat(ARROW_POWER, power(player, def, effectBase));
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 3.0f, 0.5f);
        player.serverLevel().addFreshEntity(arrow);
        bowFlash(player, effect.equals("poison") ? Palette.POISON : Palette.STORM);
        sound(player, SoundEvents.ARROW_SHOOT, 1f, effect.equals("poison") ? 0.8f : 1.5f);
        return true;
    }

    private static boolean bearTrap(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        float damage = power(player, def, 1.5f);
        // Капкан живёт 30 секунд: враги в радиусе 2.2 блока обездвижены и получают урон.
        DelayedTasks.repeat(0, 10, 60, () -> {
            for (LivingEntity target : targetsInRadius(player, center, 2.2)) {
                if (!target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                    Fx.burst(level, Palette.STEEL, target.position().add(0, 0.3, 0), 0.5);
                    Fx.sound(level, target.position(), SoundEvents.CHAIN_BREAK, 1f, 0.7f);
                }
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 6));
                if (target.tickCount % 20 < 10) hit(player, target, damage, def);
            }
            Fx.ring(level, ModParticles.mark(Palette.STEEL), center.add(0, 0.1, 0), 2.2, 16, 0, 0);
        });
        Fx.debris(level, center, 12, 0.5, 0.05);
        sound(player, SoundEvents.CHAIN_PLACE, 1.2f, 0.7f);
        return true;
    }

    private static boolean rapidFire(ServerPlayer player, AbilityDef def) {
        DelayedTasks.repeat(0, 6, 3, () -> {
            if (!player.isAlive()) return;
            for (int i = -1; i <= 1; i++) {
                Arrow arrow = abilityArrow(player, def, 1.8f);
                arrow.shootFromRotation(player, player.getXRot(), player.getYRot() + i * 5f, 0f, 3.2f, 1.0f);
                player.serverLevel().addFreshEntity(arrow);
            }
            bowFlash(player, Palette.STEEL);
            sound(player, SoundEvents.ARROW_SHOOT, 1f, 1.4f);
        });
        return true;
    }

    /** Эффекты стрел «Ядовитая стрела» и «Грозовая стрела» при попадании; стрелы умений пролетают сквозь своих. */
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().getPersistentData().getBoolean(ABILITY_PROJECTILE) && event.getRayTraceResult() instanceof EntityHitResult entityHit
                && entityHit.getEntity() instanceof LivingEntity living && event.getProjectile().getOwner() instanceof ServerPlayer owner
                && !canHarm(owner, living)) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || !(arrow.level() instanceof ServerLevel level)) return;
        CompoundTag data = arrow.getPersistentData();
        if (!data.contains(ARROW_EFFECT) || !(arrow.getOwner() instanceof ServerPlayer player)) return;
        String effect = data.getString(ARROW_EFFECT);
        float power = data.getFloat(ARROW_POWER);
        data.remove(ARROW_EFFECT);
        Vec3 at = event.getRayTraceResult().getLocation();
        if (effect.equals("poison")) {
            AbilityDef def = AbilityRegistry.get("poison_arrow");
            Fx.burst(level, Palette.POISON, at, 0.8);
            DelayedTasks.repeat(0, 20, 5, () -> {
                for (LivingEntity target : targetsInRadius(player, at, 3)) {
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
                    hit(player, target, power, def);
                }
                // Ядовитое облако: клубы и капли яда.
                level.sendParticles(ModParticles.wisp(Palette.POISON), at.x, at.y + 0.6, at.z, 18, 1.4, 0.4, 1.4, 0.01);
                level.sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y + 0.3, at.z, 12, 1.5, 0.3, 1.5, 0.02);
                Fx.ring(level, ModParticles.mark(Palette.POISON), at.add(0, 0.1, 0), 3, 20, 0, 0);
            });
        } else if (effect.equals("thunder")) {
            AbilityDef def = AbilityRegistry.get("thunder_arrow");
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(at);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            Fx.zigzag(level, Palette.STORM, at.add(0, 10, 0), at, level.random);
            Fx.burst(level, Palette.STORM, at.add(0, 0.5, 0), 1);
            Fx.shockwave(level, Palette.STORM, at, 3, 5);
            for (LivingEntity target : targetsInRadius(player, at, 3)) hit(player, target, power, def);
        } else {
            SubclassAbilities.onArrowEffect(effect, player, level, event, power);
        }
    }

    // ================================================================ маг

    private static boolean bolt(ServerPlayer player, AbilityDef def, SpellProjectile.Kind kind, float base, float speed) {
        float damage = power(player, def, base) * damageFactor(player, def);
        if (damage > 0) damage += PlayerStats.flatAbilityDamage(player, def);
        if (player.getRandom().nextFloat() * 100f < PlayerStats.critChance(player)) damage = PlayerStats.applyCrit(player, damage);
        SpellProjectile projectile = new SpellProjectile(player.serverLevel(), player, damage, kind);
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, speed, 0f);
        player.serverLevel().addFreshEntity(projectile);
        // Вспышка у руки мага при броске.
        Palette palette = kind == SpellProjectile.Kind.FIRE ? Palette.FIRE : Palette.FROST;
        Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.9)).add(0, -0.25, 0);
        Fx.wisps(player.serverLevel(), palette, player.getEyePosition().add(player.getLookAngle().scale(2)), 5, 0.08, 0.04);
        Fx.embers(player.serverLevel(), palette, hand, 12, 0.05, 0.12);
        if (kind == SpellProjectile.Kind.FIRE) player.serverLevel().sendParticles(ParticleTypes.SMOKE, hand.x, hand.y, hand.z, 4, 0.1, 0.1, 0.1, 0.01);
        else player.serverLevel().sendParticles(ParticleTypes.SNOWFLAKE, hand.x, hand.y, hand.z, 6, 0.1, 0.1, 0.1, 0.03);
        sound(player, kind == SpellProjectile.Kind.FIRE ? SoundEvents.BLAZE_SHOOT : SoundEvents.PLAYER_HURT_FREEZE, 0.8f, 1.2f);
        return true;
    }

    private static boolean frostNova(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        double radius = areaRadius(player, def);
        float damage = power(player, def, 5f);
        for (LivingEntity target : targetsInRadius(player, player.position(), radius)) {
            hit(player, target, damage, def);
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
            target.setTicksFrozen(Math.max(target.getTicksFrozen(), target.getTicksRequiredToFreeze()));
            Fx.embers(level, Palette.FROST, target.position().add(0, target.getBbHeight() / 2, 0), 12, 0.3, 0.08);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    target.getX(), target.getY() + 0.3, target.getZ(), 15, 0.3, 0.3, 0.3, 0.1);
        }
        // Ледяная волна: вспышка, кольцо холода расходится, снег.
        Fx.flash(level, Palette.FROST, player.position().add(0, 1, 0), 2);
        Fx.shockwave(level, Palette.FROST, player.position(), radius, 6);
        Fx.ring(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), player.position().add(0, 0.2, 0),
                radius * 0.8, (int) (radius * 6), 0.15, 0.2);
        particles(player, ParticleTypes.SNOWFLAKE, player.position().add(0, 1, 0), 120, radius / 2, 0.05);
        sound(player, SoundEvents.GLASS_BREAK, 1f, 0.6f);
        sound(player, SoundEvents.PLAYER_HURT_FREEZE, 1f, 0.8f);
        return true;
    }

    private static boolean blink(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        double distance = Math.min(20, 12 + RpgManager.get(player).getLevel() / 10.0);
        Vec3 look = player.getLookAngle();
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(distance)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 target = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(distance)) : hit.getLocation().subtract(look.scale(0.5));
        Vec3 feet = target.subtract(0, player.getEyeHeight(), 0);
        Vec3 start = player.position();
        for (double back = 0; back <= distance; back += 0.5) {
            Vec3 base = feet.subtract(look.scale(back));
            for (double up = 0; up <= 2.0; up += 0.5) {
                Vec3 pos = base.add(0, up, 0);
                if (!level.noCollision(player, player.getBoundingBox().move(pos.subtract(start)))) continue;
                // Мерцание: вспышка на месте, след волшебства, вспышка там, где появился маг.
                Fx.burst(level, Palette.ARCANE, start.add(0, 1, 0), 0.8);
                level.sendParticles(ParticleTypes.PORTAL, start.x, start.y + 1, start.z, 30, 0.3, 0.6, 0.3, 0.2);
                Fx.line(level, ModParticles.mark(Palette.ARCANE), start.add(0, 1, 0), pos.add(0, 1, 0), 2);
                player.teleportTo(pos.x, pos.y, pos.z);
                player.resetFallDistance();
                Fx.burst(level, Palette.ARCANE, pos.add(0, 1, 0), 0.8);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.x, pos.y + 1, pos.z, 30, 0.3, 0.6, 0.3, 0.05);
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1.2f);
                return true;
            }
        }
        player.displayClientMessage(Component.translatable("message.heroicpaths.blink_failed"), true);
        return false;
    }

    /**
     * Метеор по схеме меток: раскалённая глыба (1, 2 или 3 блока по рангу) падает с неба из-за цели — маг видит, как она летит;
     * место падения 1 секунду отмечено и замедляет врагов на 80%, за полсекунды до удара метка гаснет.
     */
    private static boolean meteor(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 target = groundTarget(player, def);
        int size = rank(player, def);
        double radius = areaRadius(player, def);
        float damage = power(player, def, 14f);
        Vec3 ahead = horizontalLook(player), side = new Vec3(-ahead.z, 0, ahead.x);
        Vec3 from = target.add(ahead.scale(16)).add(side.scale(5)).add(0, 14 + size * 2, 0);
        level.addFreshEntity(new MeteorEntity(level, from, target, size, Zones.IMPACT_DELAY));
        Fx.sound(level, target, SoundEvents.FIRECHARGE_USE, 2f, 0.5f);
        Zones.strike(player, target, radius, Palette.FIRE, () -> {
            for (LivingEntity victim : targetsInRadius(player, target, radius)) {
                hit(player, victim, damage, def);
                victim.igniteForSeconds(5);
                Vec3 away = victim.position().subtract(target);
                Vec3 flat = new Vec3(away.x, 0, away.z);
                if (flat.lengthSqr() > 1.0E-4) victim.knockback(0.6 + 0.2 * size, -flat.x, -flat.z);
                victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.35, 0));
                victim.hurtMarked = true;
            }
            Vec3 up = target.add(0, 0.5, 0);
            Fx.flash(level, Palette.FIRE, up, 2 + size);
            Fx.burst(level, Palette.FIRE, up, 1.2 + size * 0.6);
            Fx.shockwave(level, Palette.FIRE, target, radius + 1, 7);
            Fx.debris(level, target, 30 + 20 * size, radius * 0.4, 0.35);
            level.sendParticles(ParticleTypes.LAVA, up.x, up.y, up.z, 6 * size, radius * 0.3, 0.3, radius * 0.3, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, up.x, up.y + 0.5, up.z, 8 * size, radius * 0.4, 0.5, radius * 0.4, 0.04);
            level.playSound(null, target.x, target.y, target.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.6f + size * 0.3f, 0.75f - size * 0.08f);
            level.playSound(null, target.x, target.y, target.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5f, 0.6f);
        });
        sound(player, SoundEvents.BLAZE_SHOOT, 1f, 0.5f);
        return true;
    }

    /** Огненная волна катится от мага конусом и поджигает всех, через кого проходит. */
    private static boolean flameWave(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        float damage = power(player, def, 8f);
        double reach = 7 * rankScale(player, def);
        Vec3 look = horizontalLook(player);
        Vec3 origin = player.position();
        List<LivingEntity> victims = inCone(player, reach, 0.7);
        Set<LivingEntity> burned = new HashSet<>();
        int steps = 7;
        int[] step = {0};
        DelayedTasks.repeat(0, 1, steps, () -> {
            double d = reach * (++step[0]) / steps;
            for (double a = -0.75; a <= 0.75; a += 0.25) {
                Vec3 dir = look.yRot((float) a);
                Vec3 p = origin.add(dir.scale(d)).add(0, 0.6, 0);
                level.sendParticles(ModParticles.wisp(Palette.FIRE), p.x, p.y, p.z, 1, 0.15, 0.25, 0.15, 0.01);
                Fx.one(level, ModParticles.ember(Palette.FIRE), p, dir.scale(0.15).add(0, 0.1, 0));
            }
            for (LivingEntity target : victims) {
                if (burned.contains(target) || !target.isAlive() || target.position().distanceTo(origin) > d + 0.5) continue;
                burned.add(target);
                hit(player, target, damage, def);
                target.igniteForSeconds(5);
                Fx.burst(level, Palette.FIRE, target.position().add(0, target.getBbHeight() / 2, 0), 0.5);
            }
        });
        sound(player, SoundEvents.FIRECHARGE_USE, 1.2f, 0.7f);
        sound(player, SoundEvents.BLAZE_SHOOT, 1f, 0.6f);
        return true;
    }

    private static boolean healingRain(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        double radius = areaRadius(player, def);
        float amount = 1.5f * levelScale(player) * (float) rankScale(player, def);
        DelayedTasks.repeat(0, 20, 8, () -> {
            for (Player ally : level.getEntitiesOfClass(Player.class, new AABB(center, center).inflate(radius, 3, radius),
                    p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(center) < radius * radius)) {
                heal(player, ally, amount);
                Fx.embers(level, Palette.WATER, ally.position().add(0, 1, 0), 6, 0.3, 0.05);
            }
            // Облако над кругом и капли целебного дождя.
            level.sendParticles(ModParticles.wisp(Palette.WATER), center.x, center.y + 4.5, center.z, 20, radius * 0.5, 0.3, radius * 0.5, 0.005);
            for (int i = 0; i < 25; i++) {
                double angle = level.random.nextDouble() * Math.PI * 2, r = Math.sqrt(level.random.nextDouble()) * radius;
                Fx.one(level, ModParticles.ember(Palette.WATER), center.add(Math.cos(angle) * r, 4, Math.sin(angle) * r), new Vec3(0, -0.45, 0));
            }
            level.sendParticles(ParticleTypes.FALLING_WATER, center.x, center.y + 4, center.z, 40, radius * 0.5, 0.5, radius * 0.5, 0);
            Fx.ring(level, ModParticles.mark(Palette.WATER), center.add(0, 0.1, 0), radius, (int) (radius * 8), 0, 0);
        });
        sound(player, SoundEvents.WEATHER_RAIN, 1f, 1.2f);
        return true;
    }

    private static boolean stoneSkin(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        absorption(player, power(player, def, 8f), 200);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        // Камень поднимается из-под ног и обрастает мага.
        BlockParticleOption stone = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState());
        Fx.spiral(level, stone, player.position(), 0.8, 2.2, 2, 40, 0);
        Fx.debris(level, player.position(), 25, 0.6, 0.15);
        Fx.ring(level, ModParticles.wisp(Palette.EARTH), player.position().add(0, 0.2, 0), 1.2, 12, 0.05, 0.03);
        Fx.aura(player, Palette.EARTH, 200, 5);
        sound(player, SoundEvents.STONE_PLACE, 1.2f, 0.6f);
        sound(player, SoundEvents.ANVIL_LAND, 0.4f, 0.6f);
        return true;
    }

    /** Землетрясение по схеме меток: круг вокруг мага замедляет врагов, через 1.5 с — три удара по земле. */
    private static boolean earthquake(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        double radius = areaRadius(player, def);
        float damage = power(player, def, 9f);
        Zones.strike(player, center, radius, Palette.EARTH, () -> {
            for (LivingEntity target : targetsInRadius(player, center, radius)) {
                hit(player, target, damage, def);
                target.setDeltaMovement(target.getDeltaMovement().x, 0.6, target.getDeltaMovement().z);
                target.hurtMarked = true;
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                Fx.debris(level, target.position(), 15, 0.4, 0.2);
            }
            // Три толчка: волны земли и трещины лучами от центра.
            DelayedTasks.repeat(0, 4, 3, () -> {
                Fx.shockwave(level, Palette.EARTH, center, radius, 6);
                for (int i = 0; i < 16; i++) {
                    double angle = Math.PI * 2 * i / 16;
                    Fx.debris(level, center.add(Math.cos(angle) * radius * 0.6, 0, Math.sin(angle) * radius * 0.6), 6, 0.4, 0.15);
                }
                Fx.sound(level, center, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 0.5f);
            });
            for (int ray = 0; ray < 8; ray++) {
                double angle = Math.PI * 2 * ray / 8 + level.random.nextDouble() * 0.3;
                for (double r = 0.5; r <= radius; r += 0.7)
                    Fx.debris(level, center.add(Math.cos(angle) * r, 0, Math.sin(angle) * r), 3, 0.15, 0.1);
            }
        });
        sound(player, SoundEvents.ANVIL_LAND, 0.6f, 0.5f);
        return true;
    }

    private static boolean chainLightning(ServerPlayer player, AbilityDef def) {
        LivingEntity target = targetInFront(player, 20);
        if (target == null) return noTarget(player);
        ServerLevel level = player.serverLevel();
        float damage = power(player, def, 8f);
        Set<LivingEntity> struck = new HashSet<>();
        Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.6));
        int jumps = 3 + rank(player, def);
        for (int jump = 0; jump < jumps && target != null; jump++) {
            Vec3 to = target.position().add(0, target.getBbHeight() / 2, 0);
            Fx.zigzag(level, Palette.STORM, from, to, player.getRandom());
            Fx.flash(level, Palette.STORM, to, 1);
            hit(player, target, damage, def);
            struck.add(target);
            damage *= 0.85f;
            from = to;
            LivingEntity next = null;
            double best = Double.MAX_VALUE;
            for (LivingEntity candidate : enemiesInRadius(player, target.position(), 6)) {
                double dist = candidate.distanceToSqr(target);
                if (!struck.contains(candidate) && dist < best) {
                    best = dist;
                    next = candidate;
                }
            }
            target = next;
        }
        sound(player, SoundEvents.TRIDENT_THUNDER.value(), 0.8f, 1.4f);
        return true;
    }

    /** Смерч по схеме меток: место отмечено, через 1.5 с поднимается воронка, тянет и ранит врагов 3 секунды. */
    private static boolean tornado(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 center = groundTarget(player, def);
        double radius = areaRadius(player, def);
        float damage = power(player, def, 3f);
        Zones.strike(player, center, radius * 0.6, Palette.STORM, () -> DelayedTasks.repeat(0, 2, 30, () -> {
            boolean damageTick = level.getGameTime() % 20 < 2;
            for (LivingEntity target : targetsInRadius(player, center, radius)) {
                Vec3 pull = center.subtract(target.position()).normalize().scale(0.35);
                target.setDeltaMovement(pull.x, 0.12, pull.z);
                target.hurtMarked = true;
                if (damageTick) hit(player, target, damage, def);
            }
            // Воронка: вихрь шире кверху, пыль у основания.
            double spin = level.getGameTime() * 0.7;
            for (int i = 0; i < 14; i++) {
                double h = i * 0.4, r = 0.4 + h * 0.35, angle = spin + i * 0.7;
                Vec3 p = center.add(Math.cos(angle) * r, h, Math.sin(angle) * r);
                Fx.one(level, ModParticles.wisp(Palette.STORM), p, new Vec3(-Math.sin(angle) * 0.2, 0.05, Math.cos(angle) * 0.2));
                if (i % 3 == 0) level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            if (level.getGameTime() % 4 == 0) Fx.debris(level, center, 6, 0.6, 0.2);
        }));
        sound(player, SoundEvents.ELYTRA_FLYING, 1f, 1.5f);
        return true;
    }

    // ================================================================ общее

    /** Ранг умения у игрока: 1–3. */
    public static int rank(Player player, AbilityDef def) {
        return Math.max(1, RpgManager.get(player).getRank(def.id()));
    }

    /** Размер эффекта по рангу: 1, 1.15, 1.3. */
    public static double rankScale(Player player, AbilityDef def) {
        return 1 + 0.15 * (rank(player, def) - 1);
    }

    /** Как далеко можно указать точку удара у умений по площади (0 — умение бьёт не в точку). */
    public static double groundRange(AbilityDef def) {
        return switch (def.id()) {
            case "meteor" -> 32;
            case "arrow_rain" -> 40;
            case "blizzard" -> 24;
            case "tornado" -> 20;
            case "powder_keg" -> 16;
            default -> 0;
        };
    }

    /** Точка на земле, куда смотрит игрок. */
    public static Vec3 pickGround(ServerPlayer player, double range) {
        return Fx.ground(player.serverLevel(), player.pick(range, 1f, false).getLocation());
    }

    /** Точка удара: выбранная в начале каста, а без каста — куда игрок смотрит сейчас. */
    static Vec3 groundTarget(ServerPlayer player, AbilityDef def) {
        Vec3 chosen = Casting.finishingTarget(player);
        return chosen != null ? chosen : pickGround(player, Math.max(8, groundRange(def)));
    }

    /** Радиус умения по площади с учётом ранга (у метеора 3, 4 и 5 блоков — как и сама глыба растёт с рангом). */
    public static double areaRadius(Player player, AbilityDef def) {
        double s = rankScale(player, def);
        return switch (def.id()) {
            case "meteor" -> 2 + rank(player, def);
            case "blizzard" -> 4 * s;
            case "arrow_rain", "powder_keg", "leap_slam" -> 3.5 * s;
            case "tornado", "earthquake", "healing_rain", "battle_banner", "entangle" -> 6 * s;
            case "consecration", "frost_nova", "blood_nova", "fan_of_knives", "smoke_bomb" -> 5 * s;
            case "whirlwind" -> 4.5 * s;
            case "caltrops" -> 2.5 * s;
            default -> 4 * s;
        };
    }

    static Arrow abilityArrow(ServerPlayer player, AbilityDef def, float base) {
        Arrow arrow = new Arrow(player.serverLevel(), player, new ItemStack(Items.ARROW), heldBow(player));
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        arrow.getPersistentData().putBoolean(ABILITY_PROJECTILE, true);
        arrow.setBaseDamage(power(player, def, base) * CLASS_DAMAGE_FACTOR * com.heroicpaths.item.GradedBowItem.damageFactor(heldBow(player)));
        return arrow;
    }

    /** Лук или арбалет в руке: от него стрела унаследует качество. */
    @Nullable
    static ItemStack heldBow(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        return main.getItem() instanceof BowItem || main.getItem() instanceof CrossbowItem ? main : null;
    }

    static boolean noTarget(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.heroicpaths.no_target"), true);
        return false;
    }

    static Vec3 horizontalLook(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        return flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, player.getYRot()) : flat.normalize();
    }

    @Nullable
    public static LivingEntity targetInFront(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, eye.add(look.scale(range)),
                player.getBoundingBox().expandTowards(look.scale(range)).inflate(1),
                entity -> entity instanceof LivingEntity living && canHarm(player, living), range * range);
        if (hit != null) return (LivingEntity) hit.getEntity();
        // Мягкое наведение: ближайший враг в узком конусе перед игроком (мирных — только если смотреть прямо на них).
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : enemiesInRadius(player, player.position(), range)) {
            Vec3 to = candidate.position().add(0, candidate.getBbHeight() / 2, 0).subtract(eye);
            double distance = to.length();
            if (distance > 0 && to.normalize().dot(look) > 0.85 && distance < bestDistance && player.hasLineOfSight(candidate)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    static List<LivingEntity> inCone(ServerPlayer player, double range, double minDot) {
        Vec3 look = horizontalLook(player);
        List<LivingEntity> result = new ArrayList<>();
        for (LivingEntity target : targetsInRadius(player, player.position(), range)) {
            Vec3 to = target.position().subtract(player.position());
            Vec3 flat = new Vec3(to.x, 0, to.z);
            if (flat.lengthSqr() < 0.01 || flat.normalize().dot(look) >= minDot) result.add(target);
        }
        return result;
    }

    public static List<LivingEntity> targetsInRadius(ServerPlayer player, Vec3 center, double radius) {
        double radiusSqr = radius * radius;
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                entity -> canHarm(player, entity) && entity.distanceToSqr(center) <= radiusSqr);
    }

    static List<LivingEntity> targetsIn(ServerPlayer player, AABB area) {
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, area, entity -> canHarm(player, entity));
    }

    /** Враги в радиусе — для наведения, подсветки и перескоков (урон по площади задевает и мирных, см. targetsInRadius). */
    public static List<LivingEntity> enemiesInRadius(ServerPlayer player, Vec3 center, double radius) {
        double radiusSqr = radius * radius;
        return player.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                entity -> isEnemy(player, entity) && entity.distanceToSqr(center) <= radiusSqr);
    }

    /**
     * Кого умения игрока могут ранить: всех существ — монстров, мирных животных, жителей, големов, —
     * кроме самого игрока, его группы (и игроков без PvP), своих и союзных слуг, спутников и питомцев,
     * а также стоек для брони. Чужие слуги и питомцы — только если сами напали.
     */
    public static boolean canHarm(ServerPlayer player, LivingEntity entity) {
        if (entity == player || !entity.isAlive() || entity.isSpectator() || entity instanceof ArmorStand) return false;
        if (entity instanceof Player other) return player.canHarmPlayer(other) && !com.heroicpaths.party.PartyManager.sameParty(player, other);
        if (entity instanceof Minion minion && minion.minionData().owner() != null) {
            return !Minions.isFriendly(entity, player) && entity instanceof Mob mob && mob.getTarget() == player;
        }
        if (entity instanceof OwnableEntity pet && pet.getOwnerUUID() != null) {
            if (pet.getOwnerUUID().equals(player.getUUID())) return false;
            Player owner = player.serverLevel().getPlayerByUUID(pet.getOwnerUUID());
            if (owner != null && com.heroicpaths.party.PartyManager.sameParty(player, owner)) return false;
            return entity instanceof Mob mob && mob.getTarget() == player;
        }
        return true;
    }

    /** Враг: монстр, существо, напавшее на игрока, или игрок, которого можно ранить. По нему наводятся умения и стрелы. */
    public static boolean isEnemy(ServerPlayer player, LivingEntity entity) {
        if (!canHarm(player, entity)) return false;
        if (entity instanceof Player || entity instanceof Enemy) return true;
        return entity instanceof Mob mob && mob.getTarget() == player;
    }

    static void particles(ServerPlayer player, ParticleOptions particle, Vec3 at, int count, double spread, double speed) {
        player.serverLevel().sendParticles(particle, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    static void line(ServerLevel level, ParticleOptions particle, Vec3 from, Vec3 to) {
        Vec3 step = to.subtract(from);
        int points = (int) Math.max(2, step.length() * 3);
        for (int i = 0; i <= points; i++) {
            Vec3 at = from.add(step.scale((double) i / points));
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0);
        }
    }

    static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }
}
