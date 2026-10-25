package com.heroicpaths.combat;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.item.MusketItem;
import com.heroicpaths.network.StaminaPayload;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.ability.AbilityLogic;
import com.heroicpaths.rpg.stat.Stat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Выносливость (стамина) бойцов: воина, лучника, разбойника и охотника (у мага вместо неё мана). Это их единственный
 * ресурс: на неё работают и умения, и удары оружием ближнего боя, и выстрелы из лука, арбалета и мушкета.
 * <ul>
 *     <li>запас — 100, восстанавливается 30 в секунду, если секунду ничего не тратить (ни ударами, ни умениями);</li>
 *     <li>удар стоит 14 / скорость атаки оружия (кинжал ≈6, меч ≈9, топор и двуручник ≈14–18), слабый удар — меньше;</li>
 *     <li>выстрел из лука — до 12 (по силе натяжения), арбалет — 12, мушкет — 15;</li>
 *     <li>если стамины не хватает, удар или выстрел всё равно происходит, но наносит лишь 40% урона.</li>
 * </ul>
 * Максимум и прибавки восстановления из дерева — те же, что у ресурса класса. Клиент получает выносливость маленьким
 * пакетом, не дожидаясь полной синхронизации.
 */
public final class Stamina {
    public static final float MAX = 100f;
    public static final float REGEN_PER_TICK = 1.5f;
    public static final int REGEN_DELAY = 20;
    public static final float MELEE_COST = 14f;
    public static final float MIN_MELEE_COST = 4f;
    public static final float MAX_MELEE_COST = 20f;
    public static final float BOW_COST = 12f;
    public static final float CROSSBOW_COST = 12f;
    public static final float MUSKET_COST = 15f;
    /** Урон удара или выстрела, на который не хватило стамины. */
    public static final float TIRED_FACTOR = 0.4f;
    private static final String TIRED_TAG = HeroicPaths.MODID + ".tired";

    private Stamina() {}

    /** Есть ли у класса выносливость: у всех, кроме мага (и игрока без класса). */
    public static boolean uses(PlayerClass playerClass) {
        return playerClass != PlayerClass.NONE && playerClass != PlayerClass.MAGE;
    }

    /** Сколько выносливости сейчас: это ресурс класса. */
    public static float current(Player player) {
        return RpgManager.get(player).getResource();
    }

    public static float max(Player player) {
        return PlayerStats.maxResource(player);
    }

    public static void set(Player player, float value) {
        RpgManager.get(player).setResource(value);
    }

    public static boolean uses(Player player) {
        return uses(RpgManager.get(player).getPlayerClass()) && !player.getAbilities().instabuild && !player.isSpectator();
    }

    /** Цена удара оружием в руке: чем медленнее оружие, тем дороже. Слабый (не заряженный) удар дешевле. */
    public static float meleeCost(Player player, float strength) {
        float speed = weaponSpeed(player.getMainHandItem());
        float cost = Mth.clamp(MELEE_COST / Math.max(0.1f, speed), MIN_MELEE_COST, MAX_MELEE_COST);
        return cost * Mth.clamp(strength, 0.3f, 1f);
    }

    /** Скорость атаки самого оружия (база игрока 4 плюс прибавки предмета); бонусы скорости игрока цену не меняют. */
    public static float weaponSpeed(ItemStack stack) {
        double[] speed = {4.0};
        stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.equals(Attributes.ATTACK_SPEED) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
                speed[0] += modifier.amount();
            }
        });
        return (float) speed[0];
    }

    /**
     * Тратит стамину. Возвращает false, если её не хватило: тогда запас обнуляется, а удар выйдет слабым.
     * Без стамины (маг, творческий режим) ничего не тратится и возвращается true.
     */
    public static boolean spend(ServerPlayer player, float cost) {
        if (!uses(player) || cost <= 0) return true;
        if (com.heroicpaths.rpg.ability.UniqueAbilities.freeStamina(player)) return true;
        RpgData data = RpgManager.get(player);
        float stamina = current(player);
        boolean enough = stamina >= cost;
        set(player, enough ? stamina - cost : 0f);
        data.setStaminaUsedAt(player.level().getGameTime());
        if (!enough) data.setTiredAt(player.level().getGameTime());
        send(player);
        return enough;
    }

    /** Удар или выстрел в этот тик был сделан без стамины. */
    public static boolean tiredNow(Player player) {
        return RpgManager.get(player).getTiredAt() == player.level().getGameTime();
    }

    // ================================================================ события

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) tick(player);
    }

    /**
     * Восстановление: через секунду после последней траты, 1.5 за тик (полный запас за 3–4 секунды).
     * Чем больше максимум, тем быстрее; прибавка восстановления ресурса из дерева добавляется сверху.
     */
    public static void tick(ServerPlayer player) {
        RpgData data = RpgManager.get(player);
        if (!uses(data.getPlayerClass())) return;
        float stamina = current(player), max = max(player);
        if (stamina > max) {
            set(player, max);
            send(player);
        } else if (stamina < max && player.level().getGameTime() - data.getStaminaUsedAt() >= REGEN_DELAY) {
            float regen = regenPerTick(player);
            set(player, Math.min(max, stamina + regen));
            // Пока стамина копится, клиенту хватает обновления раз в 2 тика.
            if (current(player) >= max || player.tickCount % 2 == 0) send(player);
        }
    }

    public static float regenPerTick(Player player) {
        float regen = REGEN_PER_TICK * max(player) / MAX + PlayerStats.get(player).get(Stat.RESOURCE_REGEN) / 20f;
        // «Да я только начал»: чем меньше здоровья, тем быстрее восстанавливается выносливость.
        return regen * (1f + com.heroicpaths.rpg.ability.UniqueAbilities.justStartedBonus(player));
    }

    /** Удар по существу оружием ближнего боя. Голые руки стамину не тратят. */
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getMainHandItem().isEmpty()) return;
        spend(player, meleeCost(player, player.getAttackStrengthScale(0.5f)));
    }

    /** Выстрел из лука: цена по силе натяжения. */
    @SubscribeEvent
    public static void onArrowLoose(ArrowLooseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !event.hasAmmo()) return;
        float power = BowItem.getPowerForTime(event.getCharge());
        if (power < 0.1f) return; // игра такую стрелу не выпустит
        spend(player, BOW_COST * Math.max(0.3f, power));
    }

    /** Выстрел из заряженного арбалета или мушкета. */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            spend(player, CROSSBOW_COST);
        } else if (stack.getItem() instanceof MusketItem && !player.getCooldowns().isOnCooldown(stack.getItem())
                && MusketItem.hasAmmo(player)) {
            spend(player, MUSKET_COST);
        }
    }

    /** Снаряды, выпущенные без стамины, помечаются: их урон будет слабее. */
    @SubscribeEvent
    public static void onProjectileSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || event.loadedFromDisk()) return;
        if (event.getEntity() instanceof Projectile projectile && projectile.getOwner() instanceof ServerPlayer player
                && tiredNow(player)) {
            projectile.getPersistentData().putBoolean(TIRED_TAG, true);
        }
    }

    /** Удар или выстрел без стамины наносит лишь 40% урона. Удары умений не трогаем. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (AbilityLogic.isAbilityDamage() || !(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        LivingEntity target = event.getEntity();
        if (target == player) return;
        boolean tired = event.getSource().getDirectEntity() == player
                ? tiredNow(player)
                : event.getSource().getDirectEntity() instanceof Projectile projectile && projectile.getPersistentData().getBoolean(TIRED_TAG);
        if (tired) event.setAmount(event.getAmount() * TIRED_FACTOR);
    }

    // ================================================================ синхронизация

    public static void send(ServerPlayer player) {
        if (player.connection == null || !player.connection.hasChannel(StaminaPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player, new StaminaPayload(current(player)));
    }
}
