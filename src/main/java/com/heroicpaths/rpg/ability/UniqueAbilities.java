package com.heroicpaths.rpg.ability;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.combat.WeaponCombat;
import com.heroicpaths.combat.WeaponType;
import com.heroicpaths.entity.Dracolich;
import com.heroicpaths.entity.MeteorEntity;
import com.heroicpaths.fx.Fx;
import com.heroicpaths.fx.Palette;
import com.heroicpaths.minion.CompanionManager;
import com.heroicpaths.minion.Minions;
import com.heroicpaths.network.CastBarPayload;
import com.heroicpaths.network.PlayerFormPayload;
import com.heroicpaths.registry.ModEffects;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModParticles;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.Subclass;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Пятнадцать уникальных умений. Их книги делают на столе зачарований 2-го уровня (обставленном полками умений);
 * учить их можно с 30-го уровня за одно очко умений. Четыре умения пассивные: действуют, как только выучены.
 * Длительные усиления хранятся как «перезарядки» с ключом buff.* в RpgData — так клиент тоже знает о них.
 */
public final class UniqueAbilities {
    public static final int UNLOCK_LEVEL = 30;

    private static final Set<Subclass> MAGES = EnumSet.of(Subclass.ELEMENTALIST, Subclass.SUPPORT, Subclass.SUMMONER, Subclass.BLOOD_MAGE);
    private static final Set<Subclass> WARRIORS = EnumSet.of(Subclass.GREATSWORD, Subclass.GUARDIAN, Subclass.BARBARIAN, Subclass.PALADIN);
    private static final Set<Subclass> ROGUES = EnumSet.of(Subclass.CROSSBOWMAN, Subclass.DUELIST);
    /** Кто может выучить уникальное умение. */
    private static final Map<String, Set<Subclass>> LEARNERS = Map.ofEntries(
            Map.entry("meteor_shower", EnumSet.of(Subclass.ELEMENTALIST)),
            Map.entry("mana_surge", MAGES),
            Map.entry("hunting_frenzy", EnumSet.of(Subclass.RANGER, Subclass.BEASTMASTER)),
            Map.entry("critical_error", EnumSet.of(Subclass.RANGER, Subclass.MUSKETEER, Subclass.CROSSBOWMAN, Subclass.BEASTMASTER)),
            Map.entry("boarding_hook", EnumSet.of(Subclass.MUSKETEER)),
            Map.entry("healing_downpour", EnumSet.of(Subclass.SUPPORT)),
            Map.entry("summon_dracolich", EnumSet.of(Subclass.SUMMONER)),
            Map.entry("wake_the_beast", EnumSet.of(Subclass.BEASTMASTER)),
            Map.entry("slaughter", EnumSet.of(Subclass.BARBARIAN)),
            Map.entry("hand_of_god", EnumSet.of(Subclass.PALADIN)),
            Map.entry("lone_warrior", EnumSet.of(Subclass.GREATSWORD)),
            Map.entry("full_steam", EnumSet.of(Subclass.GUARDIAN)),
            Map.entry("just_getting_started", WARRIORS),
            Map.entry("true_stealth", ROGUES),
            Map.entry("cutthroat", ROGUES));

    // Числа умений
    public static final float METEOR_MANA_PER_SECOND = 80f;
    public static final int METEOR_INTERVAL = 5, METEOR_MAX_TICKS = 600;
    public static final double METEOR_AREA = 7;
    public static final float MANA_SURGE_CHANCE = 0.3f;
    public static final int FRENZY_TICKS = 100;
    public static final float FRENZY_DAMAGE = 0.35f;
    public static final float CRIT_ERROR_SPEED = 1.7f, CRIT_ERROR_RICOCHET = 0.1f, CRIT_ERROR_STUN = 0.1f;
    public static final double HOOK_RANGE = 25;
    public static final float HOOK_DAMAGE = 3f;
    public static final int DOWNPOUR_TICKS = 160;
    public static final double DOWNPOUR_RADIUS = 8;
    public static final float DOWNPOUR_HEAL = 0.1f;
    public static final int DRACOLICH_TICKS = 1200;
    public static final int WEREWOLF_TICKS = 1200, BERSERK_TICKS = 400;
    public static final float WEREWOLF_REGEN = 5f, WEREWOLF_SPEED = 1.5f, BERSERK_BONUS = 0.7f;
    public static final int SLAUGHTER_TICKS = 500;
    public static final float SLAUGHTER_PER_ENEMY = 0.2f, SLAUGHTER_MAX = 1.0f;
    public static final double SLAUGHTER_RANGE = 20;
    public static final int HAND_OF_GOD_TICKS = 600;
    public static final float HAND_OF_GOD_SCALE = 0.5f, RADIANCE_SHARE = 0.2f;
    public static final double RADIANCE_RADIUS = 5;
    public static final int LONE_WARRIOR_TICKS = 140;
    public static final float SPIN_DAMAGE_PER_SECOND = 1.25f, LONE_WARRIOR_TAKEN = 0.3f, LONE_WARRIOR_SPEED = 0.2f;
    public static final double SPIN_RADIUS = 4;
    public static final int FULL_STEAM_TICKS = 300, FULL_STEAM_CDR = 4;
    public static final float FULL_STEAM_REGEN = 2f;
    public static final double FULL_STEAM_RANGE = 8;
    public static final float JUST_STARTED_MAX = 0.99f;
    public static final int STEALTH_TICKS = 200, FEAR_TICKS = 200;
    public static final double FEAR_RADIUS = 5;
    public static final float FEAR_FACTOR = 3f;
    public static final float CUTTHROAT_CRIT = 15f, TERRIFY_CHANCE = 0.2f;
    public static final double TERRIFY_RADIUS = 8;
    public static final int TERRIFY_TICKS = 40;

    private static final String CRIT_ERROR_TAG = "heroicpaths.critical_error";
    private static final String RICOCHET_TAG = "heroicpaths.ricochet";
    private static final String SLAUGHTER_BONUS = "heroicpaths.slaughter_bonus";
    private static final String LAST_QUICK_SHOT = "heroicpaths.quick_shot";

    private static final ResourceLocation WEREWOLF_SPEED_ID = HeroicPaths.id("werewolf_speed");
    private static final ResourceLocation WEREWOLF_ARMOR_ID = HeroicPaths.id("werewolf_armor");
    private static final ResourceLocation WEREWOLF_TOUGHNESS_ID = HeroicPaths.id("werewolf_toughness");
    private static final ResourceLocation SLAUGHTER_SPEED_ID = HeroicPaths.id("slaughter_speed");
    private static final ResourceLocation SLAUGHTER_ATTACK_ID = HeroicPaths.id("slaughter_attack");
    private static final ResourceLocation GIANT_ID = HeroicPaths.id("hand_of_god_scale");
    private static final ResourceLocation STEADFAST_ID = HeroicPaths.id("unique_knockback");
    private static final ResourceLocation SPIN_SPEED_ID = HeroicPaths.id("lone_warrior_speed");
    private static final ResourceLocation CHANNEL_SLOW_ID = HeroicPaths.id("channel_slow");
    private static final ResourceLocation BERSERK_SPEED_ID = HeroicPaths.id("berserk_speed");
    private static final ResourceLocation BERSERK_ATTACK_ID = HeroicPaths.id("berserk_attack");

    private static final ThreadLocal<Boolean> SPLASHING = ThreadLocal.withInitial(() -> false);

    private UniqueAbilities() {}

    public static Set<Subclass> learners(AbilityDef def) {
        return LEARNERS.getOrDefault(def.id(), Set.of());
    }

    static boolean cast(ServerPlayer player, AbilityDef def) {
        return switch (def.id()) {
            case "meteor_shower" -> startChannel(player, def);
            case "hunting_frenzy" -> buff(player, def, FRENZY_TICKS, () -> {
                Fx.aura(player, Palette.NATURE, FRENZY_TICKS, 4);
                sound(player, SoundEvents.ARROW_SHOOT, 1f, 0.6f);
            });
            case "boarding_hook" -> boardingHook(player, def);
            case "healing_downpour" -> healingDownpour(player, def);
            case "summon_dracolich" -> summonDracolich(player, def);
            case "wake_the_beast" -> wakeTheBeast(player, def);
            case "slaughter" -> slaughter(player, def);
            case "hand_of_god" -> handOfGod(player, def);
            case "lone_warrior" -> loneWarrior(player, def);
            case "full_steam" -> buff(player, def, FULL_STEAM_TICKS, () -> {
                Fx.aura(player, Palette.STEEL, FULL_STEAM_TICKS, 4);
                sound(player, SoundEvents.IRON_GOLEM_REPAIR, 1f, 0.8f);
            });
            case "true_stealth" -> trueStealth(player, def);
            default -> false;
        };
    }

    // ================================================================ усиления

    static String buffKey(String id) {
        return "buff." + id;
    }

    /** Действует ли усиление умения id (на сервере и на клиенте). */
    public static boolean active(Player player, String id) {
        return RpgManager.get(player).getCooldownEnd(buffKey(id)) > player.level().getGameTime();
    }

    private static void setBuff(ServerPlayer player, String id, int ticks) {
        RpgManager.get(player).setCooldownEnd(buffKey(id), player.level().getGameTime() + ticks);
        RpgManager.sync(player);
    }

    private static boolean buff(ServerPlayer player, AbilityDef def, int ticks, Runnable effects) {
        setBuff(player, def.id(), ticks);
        effects.run();
        return true;
    }

    public static boolean knows(Player player, String id) {
        RpgData data = RpgManager.get(player);
        AbilityDef def = AbilityRegistry.get(id);
        return def != null && data.knows(id) && learners(def).contains(data.getSubclass());
    }

    /** Волколак: бьёт лапами, броня не действует. */
    public static boolean werewolf(Player player) {
        return active(player, "wake_the_beast");
    }

    public static boolean slaughterActive(Player player) {
        return active(player, "slaughter");
    }

    /** Выносливость не тратится: «Бойня» и «Полный вперёд». */
    public static boolean freeStamina(Player player) {
        return active(player, "slaughter") || active(player, "full_steam");
    }

    /** «Ловкость головореза»: +15% к шансу крита. */
    public static float critBonus(Player player) {
        return knows(player, "cutthroat") ? CUTTHROAT_CRIT : 0f;
    }

    /** «Ловкость головореза»: колющее оружие полностью игнорирует броню. */
    public static boolean ignoresArmor(Player player) {
        return knows(player, "cutthroat");
    }

    /** «Да я только начал»: чем меньше здоровья у воина, тем сильнее удары и быстрее выносливость (до +99%). */
    public static float justStartedBonus(Player player) {
        if (!knows(player, "just_getting_started") || player.getMaxHealth() <= 0) return 0f;
        return JUST_STARTED_MAX * Math.max(0f, 1f - player.getHealth() / player.getMaxHealth());
    }

    /**
     * «Всплеск маны»: с шансом 30% заклинание мага не тратит ману и сразу снова готово.
     * Возвращает true, если всплеск случился (расход и перезарядку тогда пропускают).
     */
    public static boolean manaSurge(ServerPlayer player, AbilityDef def) {
        if (def.passive() || channeling(player) || !knows(player, "mana_surge") || player.getRandom().nextFloat() >= MANA_SURGE_CHANCE) return false;
        RpgManager.get(player).setCooldownEnd(def.id(), 0);
        Fx.wisps(player.serverLevel(), Palette.ARCANE, player.position().add(0, 1.2, 0), 12, 0.5, 0.05);
        player.displayClientMessage(Component.translatable("message.heroicpaths.mana_surge").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1f, 1.4f);
        return true;
    }

    // ================================================================ метеоритный дождь (поддерживаемый каст)

    private record Channel(ServerPlayer player, ServerLevel level, AbilityDef def, long start) {}

    private static final Map<UUID, Channel> CHANNELS = new HashMap<>();

    public static boolean channeling(Player player) {
        return CHANNELS.containsKey(player.getUUID());
    }

    private static boolean startChannel(ServerPlayer player, AbilityDef def) {
        if (channeling(player) || RpgManager.get(player).getResource() < manaPerSecond(player)) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.not_enough",
                    RpgManager.get(player).getPlayerClass().resourceName()).withStyle(ChatFormatting.RED), true);
            return false;
        }
        CHANNELS.put(player.getUUID(), new Channel(player, player.serverLevel(), def, player.level().getGameTime()));
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(CHANNEL_SLOW_ID))
            speed.addTransientModifier(new AttributeModifier(CHANNEL_SLOW_ID, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        int estimate = (int) Math.min(METEOR_MAX_TICKS, RpgManager.get(player).getResource() / manaPerSecond(player) * 20f);
        bar(player, def.id(), Math.max(20, estimate));
        player.displayClientMessage(Component.translatable("message.heroicpaths.channel_hint").withStyle(ChatFormatting.GRAY), true);
        sound(player, SoundEvents.BLAZE_AMBIENT, 1.2f, 0.5f);
        return true;
    }

    private static float manaPerSecond(ServerPlayer player) {
        return METEOR_MANA_PER_SECOND * PlayerStats.costFactor(player);
    }

    /** Прекратить метеоритный дождь (игроком, игрой или когда кончилась мана): он всегда уходит на перезарядку. */
    public static void stopChannel(ServerPlayer player) {
        Channel channel = CHANNELS.remove(player.getUUID());
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(CHANNEL_SLOW_ID);
        if (channel == null) return;
        bar(player, channel.def.id(), 0);
        RpgManager.get(player).setCooldownEnd(channel.def.id(), player.level().getGameTime() + RpgManager.cooldown(player, channel.def));
        RpgManager.sync(player);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        for (Channel channel : new ArrayList<>(CHANNELS.values())) {
            ServerPlayer player = channel.player;
            long now = channel.level.getGameTime();
            long elapsed = now - channel.start;
            if (player.isRemoved() || !player.isAlive() || player.level() != channel.level || !channel.def.weapon().isMet(player)
                    || elapsed > METEOR_MAX_TICKS) {
                stopChannel(player);
                continue;
            }
            RpgData data = RpgManager.get(player);
            if (elapsed % 20 == 0) {
                float cost = manaPerSecond(player);
                if (data.getResource() < cost) {
                    stopChannel(player);
                    continue;
                }
                data.setResource(data.getResource() - cost);
                RpgManager.sync(player);
            }
            if (elapsed % METEOR_INTERVAL == 0) dropMeteor(player, channel.def);
        }
    }

    /** Метеор 1-го уровня в случайную точку большой области вокруг прицела. */
    private static void dropMeteor(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 aim = AbilityLogic.pickGround(player, 32);
        double angle = player.getRandom().nextDouble() * Math.PI * 2, r = Math.sqrt(player.getRandom().nextDouble()) * METEOR_AREA;
        Vec3 target = Fx.ground(level, aim.add(Math.cos(angle) * r, 3, Math.sin(angle) * r));
        Vec3 ahead = AbilityLogic.horizontalLook(player);
        Vec3 from = target.add(ahead.scale(12)).add(0, 16, 0);
        float damage = AbilityLogic.power(player, def, 9f);
        level.addFreshEntity(new MeteorEntity(level, from, target, 1, 20));
        DelayedTasks.schedule(20, () -> {
            for (LivingEntity victim : AbilityLogic.targetsInRadius(player, target, 2.5)) {
                AbilityLogic.hit(player, victim, damage, def);
                victim.igniteForSeconds(3);
            }
            Fx.burst(level, Palette.FIRE, target.add(0, 0.5, 0), 1.2);
            Fx.debris(level, target, 15, 0.8, 0.3);
            level.playSound(null, target.x, target.y, target.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9f, 1.1f);
        });
    }

    private static void bar(ServerPlayer player, String ability, int ticks) {
        if (player.connection != null && player.connection.hasChannel(CastBarPayload.TYPE))
            PacketDistributor.sendToPlayer(player, new CastBarPayload(ability, ticks));
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        CHANNELS.clear();
    }

    // ================================================================ охотничий азарт

    /** Щелчок ПКМ с луком под «Охотничьим азартом»: мгновенный выстрел с полным натяжением, без стрел и выносливости. */
    public static boolean quickShot(ServerPlayer player) {
        if (!active(player, "hunting_frenzy")) return false;
        ItemStack bow = player.getMainHandItem().getItem() instanceof BowItem ? player.getMainHandItem()
                : player.getOffhandItem().getItem() instanceof BowItem ? player.getOffhandItem() : ItemStack.EMPTY;
        if (bow.isEmpty()) return false;
        long now = player.level().getGameTime();
        CompoundTag tag = player.getPersistentData();
        if (now - tag.getLong(LAST_QUICK_SHOT) < 4) return false;
        tag.putLong(LAST_QUICK_SHOT, now);
        Arrow arrow = new Arrow(player.serverLevel(), player, new ItemStack(Items.ARROW), bow);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 3f, 1f);
        arrow.setCritArrow(true);
        arrow.setBaseDamage(arrow.getBaseDamage() * FRENZY_DAMAGE * com.heroicpaths.item.GradedBowItem.damageFactor(bow));
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        player.serverLevel().addFreshEntity(arrow);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1f, 1.3f);
        return true;
    }

    // ================================================================ на абордаж!

    private static boolean boardingHook(ServerPlayer player, AbilityDef def) {
        LivingEntity target = AbilityLogic.targetInFront(player, HOOK_RANGE);
        if (target == null) return AbilityLogic.noTarget(player);
        ServerLevel level = player.serverLevel();
        Vec3 meet = player.position().add(target.position()).scale(0.5);
        int pullTicks = 8;
        int[] step = {0};
        // Гарпун летит, цепь натягивается: оба несутся навстречу друг другу, каждый на половину расстояния.
        DelayedTasks.repeat(0, 1, pullTicks, () -> {
            int left = pullTicks - step[0]++;
            if (left <= 0 || !player.isAlive()) return;
            Fx.line(level, ParticleTypes.CRIT, player.position().add(0, 1.2, 0), target.position().add(0, target.getBbHeight() / 2, 0), 2);
            pull(player, meet, left);
            if (target.isAlive() && !(target instanceof com.heroicpaths.entity.DungeonBoss)) pull(target, meet, left);
        });
        float damage = SubclassAbilities.rangedPower(player, def, 7f) * HOOK_DAMAGE;
        DelayedTasks.schedule(pullTicks + 1, () -> {
            if (!player.isAlive()) return;
            Vec3 dir = AbilityLogic.horizontalLook(player);
            for (LivingEntity victim : AbilityLogic.inCone(player, 7, 0.6)) {
                AbilityLogic.hit(player, victim, damage, def);
                victim.knockback(0.8, -dir.x, -dir.z);
            }
            SubclassAbilities.muzzle(player, 2);
            // Отдача отбрасывает мушкетёра на 5 блоков назад.
            player.setDeltaMovement(-dir.x * 1.6, 0.35, -dir.z * 1.6);
            player.hurtMarked = true;
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1f, 1.3f);
        });
        sound(player, SoundEvents.TRIDENT_THROW.value(), 1.2f, 0.7f);
        return true;
    }

    private static void pull(LivingEntity entity, Vec3 to, int ticksLeft) {
        Vec3 delta = to.subtract(entity.position());
        Vec3 velocity = new Vec3(delta.x / ticksLeft, Math.max(0.05, delta.y / ticksLeft + 0.08), delta.z / ticksLeft);
        entity.setDeltaMovement(velocity);
        entity.hurtMarked = true;
        entity.fallDistance = 0;
    }

    // ================================================================ исцеляющий дождь

    private static boolean healingDownpour(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        Vec3 center = player.position();
        DelayedTasks.repeat(0, 20, DOWNPOUR_TICKS / 20, () -> {
            for (Player ally : level.getEntitiesOfClass(Player.class, new AABB(center, center).inflate(DOWNPOUR_RADIUS, 4, DOWNPOUR_RADIUS),
                    p -> p.isAlive() && !p.isSpectator() && p.distanceToSqr(center) < DOWNPOUR_RADIUS * DOWNPOUR_RADIUS
                            && (p == player || com.heroicpaths.party.PartyManager.sameParty(player, p)))) {
                ally.heal(ally.getMaxHealth() * DOWNPOUR_HEAL * PlayerStats.healingFactor(player));
                ally.getFoodData().eat(2, 0.6f);
                Fx.embers(level, Palette.WATER, ally.position().add(0, 1, 0), 6, 0.3, 0.05);
            }
            for (Mob minion : Minions.ownedNear(player, DOWNPOUR_RADIUS)) minion.heal(minion.getMaxHealth() * DOWNPOUR_HEAL);
            level.sendParticles(ModParticles.wisp(Palette.WATER), center.x, center.y + 5, center.z, 30, DOWNPOUR_RADIUS * 0.5, 0.3, DOWNPOUR_RADIUS * 0.5, 0.005);
            level.sendParticles(ParticleTypes.FALLING_WATER, center.x, center.y + 4.5, center.z, 80, DOWNPOUR_RADIUS * 0.5, 0.5, DOWNPOUR_RADIUS * 0.5, 0);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y + 1, center.z, 12, DOWNPOUR_RADIUS * 0.4, 0.5, DOWNPOUR_RADIUS * 0.4, 0);
            Fx.ring(level, ModParticles.mark(Palette.WATER), center.add(0, 0.1, 0), DOWNPOUR_RADIUS, 64, 0, 0);
        });
        sound(player, SoundEvents.WEATHER_RAIN_ABOVE, 1.5f, 1.1f);
        return true;
    }

    // ================================================================ драколич

    private static boolean summonDracolich(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        SubclassAbilities.limit(player, ModEntities.DRACOLICH.get(), 0);
        Dracolich dragon = ModEntities.DRACOLICH.get().create(level);
        if (dragon == null) return false;
        dragon.minionData().setOwner(player.getUUID());
        dragon.minionData().setLifetime(DRACOLICH_TICKS);
        Vec3 at = player.position().add(AbilityLogic.horizontalLook(player).scale(3)).add(0, 3, 0);
        dragon.moveTo(at.x, at.y, at.z, player.getYRot(), 0f);
        dragon.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.MOB_SUMMONED, null);
        dragon.setPhantomSize(Dracolich.SIZE);
        Minions.applyStats(dragon, player, 120f, 9f * com.heroicpaths.item.FocusItem.damageFactor(player));
        dragon.setHealth(dragon.getMaxHealth());
        level.addFreshEntity(dragon);
        Fx.column(level, ParticleTypes.SOUL, at, 4, 40, 1.2, 0.08);
        Fx.burst(level, Palette.SHADOW, at, 2);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.2f, 1.4f);
        return true;
    }

    /** Убитый драколичем враг встаёт слабым скелетом-воином хозяина. */
    private static void raiseFromKill(ServerPlayer owner, LivingEntity victim) {
        ServerLevel level = owner.serverLevel();
        var skeleton = ModEntities.SUMMONED_SKELETON.get().create(level);
        if (skeleton == null) return;
        skeleton.minionData().setOwner(owner.getUUID());
        skeleton.minionData().setLifetime(600);
        skeleton.moveTo(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), 0f);
        skeleton.finalizeSpawn(level, level.getCurrentDifficultyAt(victim.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
        skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(com.heroicpaths.registry.ModItems.IRON_DAGGER.get()));
        for (EquipmentSlot slot : EquipmentSlot.values()) skeleton.setDropChance(slot, 0f);
        Minions.applyStats(skeleton, owner, 10f, 2f);
        skeleton.setHealth(skeleton.getMaxHealth());
        level.addFreshEntity(skeleton);
        Fx.column(level, ParticleTypes.SOUL, victim.position(), 2, 12, 0.3, 0.05);
        SubclassAbilities.limit(owner, ModEntities.SUMMONED_SKELETON.get(), 8);
    }

    // ================================================================ не буди зверя

    private static boolean wakeTheBeast(ServerPlayer player, AbilityDef def) {
        setBuff(player, def.id(), WEREWOLF_TICKS);
        broadcastForm(player, "werewolf", WEREWOLF_TICKS);
        Mob companion = CompanionManager.find(player);
        if (companion != null) {
            addTimed(companion, Attributes.MOVEMENT_SPEED, BERSERK_SPEED_ID, BERSERK_BONUS, BERSERK_TICKS);
            addTimed(companion, Attributes.ATTACK_SPEED, BERSERK_ATTACK_ID, BERSERK_BONUS, BERSERK_TICKS);
            companion.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, BERSERK_TICKS, 0));
            Fx.aura(companion, Palette.BLOOD, BERSERK_TICKS, 4);
        }
        ServerLevel level = player.serverLevel();
        Fx.shockwave(level, Palette.BLOOD, player.position(), 6, 8);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOLF_HOWL, SoundSource.PLAYERS, 2f, 0.6f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1f, 1.3f);
        return true;
    }

    private static void addTimed(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, double amount, int ticks) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        DelayedTasks.schedule(ticks, () -> instance.removeModifier(id));
    }

    /** Урон лапы волколака: растёт с уровнем. */
    public static float clawDamage(Player player) {
        return 4f + 0.25f * RpgManager.get(player).getLevel();
    }

    // ================================================================ бойня

    private static boolean slaughter(ServerPlayer player, AbilityDef def) {
        int enemies = AbilityLogic.enemiesInRadius(player, player.position(), SLAUGHTER_RANGE).size();
        float bonus = Math.min(SLAUGHTER_MAX, SLAUGHTER_PER_ENEMY * enemies);
        player.getPersistentData().putFloat(SLAUGHTER_BONUS, bonus);
        setBuff(player, def.id(), SLAUGHTER_TICKS);
        RpgManager.restoreResource(player, PlayerStats.maxResource(player));
        Fx.aura(player, Palette.BLOOD, SLAUGHTER_TICKS, 3);
        Fx.shockwave(player.serverLevel(), Palette.BLOOD, player.position(), 8, 8);
        player.displayClientMessage(Component.translatable("message.heroicpaths.slaughter", enemies, Math.round(bonus * 100))
                .withStyle(ChatFormatting.DARK_RED), true);
        sound(player, SoundEvents.RAVAGER_ROAR, 1.2f, 0.8f);
        return true;
    }

    // ================================================================ длань господня

    private static boolean handOfGod(ServerPlayer player, AbilityDef def) {
        setBuff(player, def.id(), HAND_OF_GOD_TICKS);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        Fx.skyBeam(player.serverLevel(), Palette.HOLY, player.position(), 20);
        Fx.aura(player, Palette.HOLY, HAND_OF_GOD_TICKS, 3);
        sound(player, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.2f);
        return true;
    }

    // ================================================================ и один в поле воин

    private static boolean loneWarrior(ServerPlayer player, AbilityDef def) {
        setBuff(player, def.id(), LONE_WARRIOR_TICKS);
        broadcastForm(player, "spin", LONE_WARRIOR_TICKS);
        sendForm(player, player, "camera", LONE_WARRIOR_TICKS);
        sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.5f, 0.6f);
        return true;
    }

    // ================================================================ истинная скрытность

    private static boolean trueStealth(ServerPlayer player, AbilityDef def) {
        ServerLevel level = player.serverLevel();
        setBuff(player, def.id(), STEALTH_TICKS);
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, STEALTH_TICKS, 0, false, false, true));
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32), mob -> mob.getTarget() == player)) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        for (LivingEntity enemy : AbilityLogic.enemiesInRadius(player, player.position(), FEAR_RADIUS)) {
            enemy.addEffect(new MobEffectInstance(ModEffects.FEAR, FEAR_TICKS, 0), player);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 40, 0.8, 0.8, 0.8, 0.02);
        Fx.shockwave(level, Palette.SHADOW, player.position(), FEAR_RADIUS, 6);
        sound(player, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1f, 0.8f);
        return true;
    }

    /** «Внушает страх»: враги вокруг пытаются убежать и сильно замедлены. */
    private static void terrify(ServerPlayer player) {
        for (LivingEntity enemy : AbilityLogic.enemiesInRadius(player, player.position(), TERRIFY_RADIUS)) {
            enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, TERRIFY_TICKS, 3), player);
            if (enemy instanceof PathfinderMob mob && !(mob instanceof com.heroicpaths.entity.DungeonBoss)) {
                mob.setTarget(null);
                Vec3 away = DefaultRandomPos.getPosAway(mob, 12, 6, player.position());
                if (away != null) mob.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
            }
        }
        player.serverLevel().sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1, player.getZ(), 20, 1.5, 0.5, 1.5, 0.02);
        player.displayClientMessage(Component.translatable("message.heroicpaths.terrify").withStyle(ChatFormatting.DARK_PURPLE), true);
        sound(player, SoundEvents.WARDEN_ROAR, 0.6f, 1.6f);
    }

    // ================================================================ формы (видно другим игрокам)

    private static void broadcastForm(ServerPlayer player, String form, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new PlayerFormPayload(player.getId(), form, ticks));
    }

    private static void sendForm(ServerPlayer to, ServerPlayer player, String form, int ticks) {
        if (to.connection != null && to.connection.hasChannel(PlayerFormPayload.TYPE))
            PacketDistributor.sendToPlayer(to, new PlayerFormPayload(player.getId(), form, ticks));
    }

    private static int remaining(Player player, String id) {
        return (int) Math.max(0, RpgManager.get(player).getCooldownEnd(buffKey(id)) - player.level().getGameTime());
    }

    // ================================================================ события

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean werewolf = werewolf(player), slaughter = slaughterActive(player), giant = active(player, "hand_of_god"),
                spin = active(player, "lone_warrior"), steam = active(player, "full_steam");
        float slaughterBonus = player.getPersistentData().getFloat(SLAUGHTER_BONUS);
        toggle(player, Attributes.MOVEMENT_SPEED, WEREWOLF_SPEED_ID, werewolf, WEREWOLF_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        toggle(player, Attributes.ARMOR, WEREWOLF_ARMOR_ID, werewolf, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        toggle(player, Attributes.ARMOR_TOUGHNESS, WEREWOLF_TOUGHNESS_ID, werewolf, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        toggle(player, Attributes.MOVEMENT_SPEED, SLAUGHTER_SPEED_ID, slaughter && slaughterBonus > 0, slaughterBonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        toggle(player, Attributes.ATTACK_SPEED, SLAUGHTER_ATTACK_ID, slaughter && slaughterBonus > 0, slaughterBonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        toggle(player, Attributes.SCALE, GIANT_ID, giant, HAND_OF_GOD_SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        toggle(player, Attributes.KNOCKBACK_RESISTANCE, STEADFAST_ID, giant || spin, 1, AttributeModifier.Operation.ADD_VALUE);
        toggle(player, Attributes.MOVEMENT_SPEED, SPIN_SPEED_ID, spin, LONE_WARRIOR_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

        ServerLevel level = player.serverLevel();
        int tick = player.tickCount;
        if (werewolf) {
            if (tick % 20 == 0 && player.getHealth() < player.getMaxHealth()) player.heal(WEREWOLF_REGEN);
            if (tick % 40 == 0) broadcastForm(player, "werewolf", remaining(player, "wake_the_beast"));
        }
        if (giant) {
            if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) speed.removeModifier(HeroicPaths.id("zone_slow"));
            player.fallDistance = 0;
            if (tick % 20 == 0) {
                float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * RADIANCE_SHARE;
                for (LivingEntity enemy : AbilityLogic.enemiesInRadius(player, player.position(), RADIANCE_RADIUS)) {
                    enemy.invulnerableTime = 0;
                    enemy.hurt(player.damageSources().indirectMagic(player, player), damage);
                }
                Fx.ring(level, ModParticles.mark(Palette.HOLY), player.position().add(0, 0.2, 0), RADIANCE_RADIUS, 40, 0, 0);
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.5, player.getZ(), 10, 1.5, 1, 1.5, 0.02);
            }
        }
        if (spin) {
            if (tick % 10 == 0) {
                float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * SPIN_DAMAGE_PER_SECOND / 2f;
                for (LivingEntity enemy : AbilityLogic.targetsInRadius(player, player.position(), SPIN_RADIUS)) {
                    enemy.invulnerableTime = 0;
                    enemy.hurt(player.damageSources().playerAttack(player), damage);
                }
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1, player.getZ(), 4, SPIN_RADIUS * 0.4, 0.1, SPIN_RADIUS * 0.4, 0);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 1.2f);
            }
            if (tick % 40 == 0) broadcastForm(player, "spin", remaining(player, "lone_warrior"));
        }
        if (steam && tick % 20 == 0) {
            int enemies = AbilityLogic.enemiesInRadius(player, player.position(), FULL_STEAM_RANGE).size();
            if (enemies > 0) player.heal(FULL_STEAM_REGEN * enemies);
        }
    }

    private static void toggle(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id, boolean on, double amount,
                               AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier current = instance.getModifier(id);
        if (!on) {
            if (current != null) instance.removeModifier(id);
            return;
        }
        if (current != null && current.amount() == amount) return;
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, operation));
    }

    /** Лапы волколака, «Да я только начал», «Страх». */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();
        // Защита: падение и урон под усилениями.
        if (target instanceof ServerPlayer defender) {
            if (source.is(DamageTypeTags.IS_FALL) && active(defender, "hand_of_god")) {
                event.setCanceled(true);
                return;
            }
            if (active(defender, "lone_warrior")) event.setAmount(event.getAmount() * LONE_WARRIOR_TAKEN);
        }
        if (!(source.getEntity() instanceof ServerPlayer player) || player == target) return;
        boolean melee = source.getDirectEntity() == player;
        if (melee && werewolf(player) && !AbilityLogic.isAbilityDamage() && !com.heroicpaths.combat.PowerAttack.isPowerHit()) {
            event.setAmount(clawDamage(player) * Math.max(0.2f, player.getAttackStrengthScale(0.5f)));
        }
        float bonus = justStartedBonus(player);
        if (bonus > 0 && (melee || AbilityLogic.isAbilityDamage())) event.setAmount(event.getAmount() * (1f + bonus));
        if (target.hasEffect(ModEffects.FEAR) && piercing(player, source)) event.setAmount(event.getAmount() * FEAR_FACTOR);
    }

    /** Колющий удар: колющее оружие в руке или стрела, болт, пуля. */
    public static boolean piercing(Player player, DamageSource source) {
        if (source.getDirectEntity() instanceof Projectile) return true;
        return source.getDirectEntity() == player && WeaponType.of(player.getMainHandItem()) == WeaponType.PIERCING;
    }

    /** Атаки волколака задевают всех вокруг цели. */
    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        LivingEntity target = event.getEntity();
        if (SPLASHING.get() || event.getNewDamage() <= 0 || !(source.getEntity() instanceof ServerPlayer player) || player == target) return;
        if (source.getDirectEntity() == player && werewolf(player)) {
            SPLASHING.set(true);
            try {
                for (LivingEntity other : AbilityLogic.enemiesInRadius(player, target.position(), 2.5)) {
                    if (other == target) continue;
                    other.invulnerableTime = 0;
                    other.hurt(player.damageSources().playerAttack(player), event.getNewDamage() * 0.5f);
                }
            } finally {
                SPLASHING.set(false);
            }
        }
        // «Критическая ошибка»: оглушение и рикошет снарядов.
        if (source.getDirectEntity() instanceof Projectile projectile && projectile.getPersistentData().getBoolean(CRIT_ERROR_TAG)) {
            if (player.getRandom().nextFloat() < CRIT_ERROR_STUN) WeaponCombat.applyStun(player, target);
            if (!projectile.getPersistentData().getBoolean(RICOCHET_TAG) && player.getRandom().nextFloat() < CRIT_ERROR_RICOCHET) {
                ricochet(player, target, event.getNewDamage());
            }
        }
    }

    private static void ricochet(ServerPlayer player, LivingEntity from, float damage) {
        LivingEntity next = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : AbilityLogic.enemiesInRadius(player, from.position(), 12)) {
            if (candidate == from) continue;
            double distance = candidate.distanceToSqr(from);
            if (distance < best) {
                best = distance;
                next = candidate;
            }
        }
        if (next == null) return;
        Vec3 start = from.position().add(0, from.getBbHeight() * 0.7, 0);
        Vec3 aim = next.position().add(0, next.getBbHeight() * 0.5, 0).subtract(start).normalize();
        Arrow arrow = new Arrow(player.serverLevel(), player, new ItemStack(Items.ARROW), null);
        arrow.setPos(start.add(aim.scale(from.getBbWidth() * 0.6 + 0.3)));
        arrow.shoot(aim.x, aim.y, aim.z, 2.5f, 0f);
        arrow.setBaseDamage(Math.max(1f, damage / 2.5f));
        arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        arrow.getPersistentData().putBoolean(RICOCHET_TAG, true);
        arrow.getPersistentData().putBoolean(CRIT_ERROR_TAG, true);
        arrow.getPersistentData().putBoolean(AbilityLogic.ABILITY_PROJECTILE, true);
        player.serverLevel().addFreshEntity(arrow);
        player.serverLevel().playSound(null, from.getX(), from.getY(), from.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3f, 2f);
    }

    /** «Критическая ошибка»: снаряды стрелков летят на 70% быстрее. */
    @SubscribeEvent
    public static void onProjectileSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk() || !(event.getEntity() instanceof Projectile projectile)) return;
        if (!(projectile.getOwner() instanceof ServerPlayer player) || projectile.getPersistentData().getBoolean(CRIT_ERROR_TAG)
                || !knows(player, "critical_error")) return;
        projectile.getPersistentData().putBoolean(CRIT_ERROR_TAG, true);
        if (!projectile.getPersistentData().getBoolean(RICOCHET_TAG)) {
            projectile.setDeltaMovement(projectile.getDeltaMovement().scale(CRIT_ERROR_SPEED));
        }
    }

    /** Итоговый урон: «Бойня» не даёт здоровью упасть ниже 1, «Полный вперёд» сокращает перезарядки. */
    @SubscribeEvent
    public static void onDamageTaken(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getNewDamage() <= 0) return;
        if (slaughterActive(player) && event.getNewDamage() >= player.getHealth()) {
            event.setNewDamage(Math.max(0, player.getHealth() - 1f));
        }
        if (active(player, "full_steam")) {
            RpgData data = RpgManager.get(player);
            long now = player.level().getGameTime();
            for (AbilityDef def : AbilityRegistry.learned(data)) {
                if (def.id().equals("full_steam")) continue;
                long end = data.getCooldownEnd(def.id());
                if (end > now) data.setCooldownEnd(def.id(), Math.max(now, end - FULL_STEAM_CDR));
            }
            RpgManager.sync(player);
        }
    }

    /** Убийства: драколич поднимает скелетов, «Ловкость головореза» внушает страх. */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || victim instanceof com.heroicpaths.minion.Minion) return;
        if (event.getSource().getEntity() instanceof Dracolich dragon) {
            ServerPlayer owner = Minions.owner(dragon);
            if (owner != null) raiseFromKill(owner, victim);
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer player && knows(player, "cutthroat")
                && player.getRandom().nextFloat() < TERRIFY_CHANCE) {
            terrify(player);
        }
    }

    /** Незыблемость под «Дланью Господней» и в вихре «И один в поле воин». */
    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof Player player && (active(player, "hand_of_god") || active(player, "lone_warrior"))) {
            event.setCanceled(true);
        }
    }

    /** Под «Дланью Господней» замедление не действует. */
    @SubscribeEvent
    public static void onEffectApplicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() instanceof Player player && event.getEffectInstance().is(MobEffects.MOVEMENT_SLOWDOWN)
                && active(player, "hand_of_god")) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /** Под «Истинной скрытностью» мобы не замечают разбойника. */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        if (event.getNewAboutToBeSetTarget() instanceof Player player && active(player, "true_stealth")) event.setCanceled(true);
    }

    private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    @Nullable
    public static PlayerClass classOf(Player player) {
        return RpgManager.get(player).getPlayerClass();
    }
}
