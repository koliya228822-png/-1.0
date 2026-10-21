package com.heroicpaths.craft;

import com.heroicpaths.item.ItemRarity.Tier;
import com.heroicpaths.item.SoulStoneItem;
import com.heroicpaths.minion.Minion;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Души и камни души. Убитые игроком (или его существами) мобы отдают души первому неполному камню в инвентаре:
 * чем сильнее моб, тем больше душ, монстры — вдвое. Крафт вещи редкой редкости и выше тратит души камня
 * подходящего уровня; если душ не хватает, вещь выходит на ту ступень, на которую хватило, а без камня — качественной.
 */
public final class SoulStones {
    private SoulStones() {}

    /** Цена редкости в душах. */
    public static int cost(Tier tier) {
        return switch (tier) {
            case RARE -> 8;
            case UNIQUE -> 16;
            case EPIC -> 40;
            case LEGENDARY -> 100;
            default -> 0;
        };
    }

    /** Чем заплатили за редкость. wanted — какая редкость выпала, если пришлось опуститься ниже (иначе null). */
    public record Payment(Tier tier, int souls, @Nullable Tier wanted) {}

    /**
     * Платит душами за выпавшую редкость. Берётся самый слабый подходящий камень, чтобы древний сберечь
     * для легендарных вещей. В творческом режиме души не нужны.
     */
    public static Payment settle(Player player, Tier rolled) {
        if (rolled.ordinal() < Tier.RARE.ordinal() || player.getAbilities().instabuild) return new Payment(rolled, 0, null);
        for (int ordinal = rolled.ordinal(); ordinal >= Tier.RARE.ordinal(); ordinal--) {
            Tier tier = Tier.byOrdinal(ordinal);
            ItemStack stone = cheapestStone(player, tier);
            if (stone == null) continue;
            SoulStoneItem.setSouls(stone, SoulStoneItem.souls(stone) - cost(tier));
            return new Payment(tier, cost(tier), tier == rolled ? null : rolled);
        }
        return new Payment(Tier.FINE, 0, rolled);
    }

    @Nullable
    private static ItemStack cheapestStone(Player player, Tier tier) {
        ItemStack best = null;
        for (ItemStack stack : stones(player)) {
            SoulStoneItem stone = (SoulStoneItem) stack.getItem();
            if (stone.maxTier().ordinal() < tier.ordinal() || SoulStoneItem.souls(stack) < cost(tier)) continue;
            if (best == null || stone.maxTier().ordinal() < ((SoulStoneItem) best.getItem()).maxTier().ordinal()) best = stack;
        }
        return best;
    }

    private static List<ItemStack> stones(Player player) {
        List<ItemStack> stones = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) if (stack.getItem() instanceof SoulStoneItem) stones.add(stack);
        for (ItemStack stack : player.getInventory().offhand) if (stack.getItem() instanceof SoulStoneItem) stones.add(stack);
        return stones;
    }

    /** Сколько душ отдаёт существо: десятая часть его здоровья (не меньше одной), у монстров — вдвое больше. */
    public static int soulsFor(LivingEntity victim) {
        if (victim instanceof Player || victim instanceof Minion) return 0;
        int souls = Math.max(1, Math.round(victim.getMaxHealth() / 10f));
        return victim instanceof Enemy ? souls * 2 : souls;
    }

    /** Наполняет камни игрока по порядку инвентаря. Возвращает, сколько душ впитано. */
    public static int absorb(ServerPlayer player, int souls) {
        int left = souls;
        for (ItemStack stack : stones(player)) {
            if (left <= 0) break;
            SoulStoneItem stone = (SoulStoneItem) stack.getItem();
            int before = SoulStoneItem.souls(stack);
            int taken = Math.min(left, stone.capacity() - before);
            if (taken <= 0) continue;
            SoulStoneItem.setSouls(stack, before + taken);
            left -= taken;
            if (before + taken >= stone.capacity()) {
                player.displayClientMessage(Component.translatable("message.heroicpaths.soul_stone_full", stack.getHoverName())
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        }
        int absorbed = souls - left;
        if (absorbed > 0) {
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(),
                    SoundSource.PLAYERS, 0.35f, 1.2f + player.getRandom().nextFloat() * 0.3f);
        }
        return absorbed;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDeath(LivingDeathEvent event) {
        ServerPlayer killer = killer(event.getEntity(), event.getSource().getEntity());
        if (killer == null) return;
        int souls = soulsFor(event.getEntity());
        if (souls > 0) absorb(killer, souls);
    }

    /** Игрок, которому достаются души: сам убийца или хозяин его существа. */
    @Nullable
    private static ServerPlayer killer(LivingEntity victim, @Nullable net.minecraft.world.entity.Entity source) {
        if (source instanceof ServerPlayer player) return player;
        if (source instanceof Minion minion && victim.level().getServer() != null) {
            UUID owner = minion.minionData().owner();
            return owner == null ? null : victim.level().getServer().getPlayerList().getPlayer(owner);
        }
        return null;
    }

    /** Подсказка о нехватке душ: какая редкость выпала и какой вышла вещь. */
    public static Component shortMessage(Payment payment) {
        return Component.translatable(payment.tier() == Tier.FINE ? "message.heroicpaths.soul_stone_none" : "message.heroicpaths.soul_stone_short",
                payment.wanted().displayName(), payment.tier().displayName()).withStyle(ChatFormatting.GRAY);
    }
}
