package com.heroicpaths.rpg;

import com.heroicpaths.item.DaggerItem;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.rpg.ability.AbilityLogic;
import com.heroicpaths.rpg.ability.SubclassAbilities;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import com.heroicpaths.rpg.stat.Keystone;
import com.heroicpaths.rpg.stat.Stat;
import com.heroicpaths.rpg.stat.StatBlock;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Игровые события: регенерация, опыт за убийства, бонусы урона и защиты, синхронизация. */
public final class RpgEvents {
    private RpgEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            RpgManager.tickRegen(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) Experience.awardKill(player, event.getEntity());
    }

    /**
     * Опыт класса за убийство: половина здоровья жертвы, за монстров вдвое больше. Существа игроков опыта не дают.
     * Разница уровней: моб на 5+ уровней выше игрока даёт +20%, на 5+ ниже — только 10%.
     */
    public static void awardKillXp(ServerPlayer player, LivingEntity victim) {
        float xp = killXp(player, victim);
        if (xp > 0) RpgManager.addXp(player, Math.max(1, Math.round(xp)));
    }

    /** Опыт этого игрока за эту жертву (0 — опыта не будет). */
    public static float killXp(ServerPlayer player, LivingEntity victim) {
        if (victim instanceof Player || victim instanceof com.heroicpaths.minion.Minion) return 0;
        float xp = Math.max(1, victim.getMaxHealth() / 2f);
        if (victim instanceof Enemy) xp *= 2;
        xp *= 1f + PlayerStats.get(player).get(Stat.XP_GAIN) / 100f;
        xp *= com.heroicpaths.combat.MobLevels.xpFactor(RpgManager.get(player).getLevel(), com.heroicpaths.combat.MobLevels.level(victim));
        return xp;
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        DamageSource source = event.getSource();

        // Метка охотника: +30% ко всему урону по цели.
        if (target.getPersistentData().getLong(AbilityLogic.MARKED_UNTIL) > target.level().getGameTime()) {
            event.setAmount(event.getAmount() * 1.3f);
        }

        if (target instanceof ServerPlayer defender) defend(event, defender, source);

        if (!(source.getEntity() instanceof ServerPlayer player) || target == player) return;
        if (AbilityLogic.isAbilityDamage()) return; // урон способностей уже посчитан
        RpgData data = RpgManager.get(player);
        if (data.getPlayerClass() == PlayerClass.NONE) return;
        StatBlock stats = PlayerStats.get(player);

        // Охотник полагается на зверя: его собственное оружие бьёт на 25% слабее.
        float classFactor = data.getPlayerClass() == PlayerClass.HUNTER ? HUNTER_WEAPON_FACTOR : 1f;
        if (source.getDirectEntity() == player) {
            ItemStack weapon = player.getMainHandItem();
            // Парное и двуручное оружие уже учтено в WeaponCombat (он срабатывает раньше).
            float amount = event.getAmount();
            float bonus = stats.get(Stat.MELEE_DAMAGE), flat = stats.get(Stat.FLAT_MELEE_DAMAGE);
            if (weapon.is(ItemTags.SWORDS)) {
                bonus += stats.get(Stat.SWORD_DAMAGE);
                flat += stats.get(Stat.FLAT_SWORD_DAMAGE);
            }
            if (weapon.is(ItemTags.AXES)) {
                bonus += stats.get(Stat.AXE_DAMAGE);
                flat += stats.get(Stat.FLAT_AXE_DAMAGE);
            }
            if (PlayerStats.has(player, Keystone.IRON_WILL)) {
                bonus += stats.get(Stat.SPELL_DAMAGE);
                flat += stats.get(Stat.FLAT_SPELL_DAMAGE);
            }
            // Проценты умножают удар, числовые прибавки дерева добавляются к каждому удару.
            amount = crit(player, target, amount * classFactor * Math.max(0.1f, 1f + bonus / 100f) + flat);
            event.setAmount(amount);
            float lifeSteal = stats.get(Stat.LIFE_STEAL);
            if (lifeSteal > 0) player.heal(amount * lifeSteal / 100f);
            if (stats.get(Stat.FLAT_LIFE_STEAL) > 0) player.heal(stats.get(Stat.FLAT_LIFE_STEAL));
            if (weapon.getItem() instanceof DaggerItem
                    && player.getPersistentData().getLong(SubclassAbilities.POISON_BLADES_UNTIL) > player.level().getGameTime()) {
                target.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
            }
        } else if (source.getDirectEntity() instanceof AbstractArrow arrow) {
            float bonus = stats.get(Stat.BOW_DAMAGE) + ItemRarity.bonusPercent(arrow.getWeaponItem());
            event.setAmount(crit(player, target, event.getAmount() * classFactor * Math.max(0.1f, 1f + bonus / 100f) + stats.get(Stat.FLAT_BOW_DAMAGE)));
        }
    }

    public static final float HUNTER_WEAPON_FACTOR = 0.75f;

    private static float crit(ServerPlayer player, LivingEntity target, float amount) {
        if (player.getRandom().nextFloat() * 100f >= PlayerStats.critChance(player)) return amount;
        player.serverLevel().sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
        return PlayerStats.applyCrit(player, amount);
    }

    /** Защита игрока: снижение урона и шипы. */
    private static void defend(LivingIncomingDamageEvent event, ServerPlayer defender, DamageSource source) {
        if (RpgManager.get(defender).getPlayerClass() == PlayerClass.NONE) return;
        // Проценты снижают урон, числовая прибавка дерева вычитается из каждого удара (не меньше пятой части удара).
        float reduced = event.getAmount() * PlayerStats.damageTakenFactor(defender);
        event.setAmount(Math.max(reduced * 0.2f, reduced - PlayerStats.get(defender).get(Stat.FLAT_DAMAGE_REDUCTION)));
        float thorns = PlayerStats.get(defender).get(Stat.THORNS);
        if (thorns > 0 && source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
                && attacker != defender) {
            attacker.hurt(defender.damageSources().thorns(defender), thorns * AbilityLogic.levelScale(defender));
        }
    }

    @SubscribeEvent
    public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && (event.getSlot().isArmor() || traitsChanged(event.getFrom(), event.getTo()))) {
            RpgManager.applyPassives(player);
            RpgManager.sync(player);
        }
    }

    /** Вещь в руке сменилась на другую с иными уникальными бонусами (износ прочности не считается). */
    private static boolean traitsChanged(ItemStack from, ItemStack to) {
        var before = ItemRarity.traits(from);
        var after = ItemRarity.traits(to);
        if (before.isEmpty() && after.isEmpty()) return false;
        return from.getItem() != to.getItem() || !before.equals(after);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        refresh(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        refresh(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        refresh(event.getEntity());
    }

    private static void refresh(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (RpgManager.get(serverPlayer).consumeTreeReset()) {
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.heroicpaths.tree_rebuilt")
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
            }
            // Деревья классов разделены: узлы чужих деревьев и оторванные от старта снимаются, очки возвращаются.
            RpgData data = RpgManager.get(serverPlayer);
            int removed = data.retainNodes(com.heroicpaths.rpg.tree.PassiveTree.validAllocation(data.getAllocatedNodes(), data.getPlayerClass()));
            if (removed > 0) {
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.heroicpaths.tree_split", removed)
                        .withStyle(net.minecraft.ChatFormatting.GOLD));
            }
            RpgManager.autoFillSlots(data);
            RpgManager.applyPassives(serverPlayer);
            RpgManager.sync(serverPlayer);
        }
    }
}
