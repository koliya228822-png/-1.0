package com.heroicpaths.village;

import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

/**
 * Ученики (игроки, которых наставник учит) торгуются с ним дешевле и получают опыт за сделки.
 * Харизма даёт скидку у всех жителей.
 */
public final class MentorEvents {
    private static final float STUDENT_DISCOUNT = 0.2f;
    private static final int XP_PER_TRADE = 8;

    private MentorEvents() {}

    /**
     * Окно торговли открывается после того, как житель пересчитал цены, но до отправки предложений клиенту.
     * Скидки сбрасываются сами, когда торговля заканчивается.
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getContainer() instanceof MerchantMenu) || !(event.getEntity() instanceof ServerPlayer player)) return;
        for (Villager villager : player.serverLevel().getEntitiesOfClass(Villager.class,
                player.getBoundingBox().inflate(16), v -> v.getTradingPlayer() == player)) {
            applyStudentDiscount(player, villager);
            applyCharismaDiscount(player, villager);
        }
    }

    /** @return true, если скидка применена */
    public static boolean applyStudentDiscount(ServerPlayer player, Villager villager) {
        PlayerClass playerClass = RpgManager.get(player).getPlayerClass();
        if (!ClassMentors.teaches(villager, playerClass)) return false;
        discount(villager, STUDENT_DISCOUNT);
        return true;
    }

    /** Скидка от харизмы: 1% за каждое очко сверх начальных, до 30%. */
    public static boolean applyCharismaDiscount(ServerPlayer player, Villager villager) {
        float percent = PlayerStats.tradeDiscount(player);
        if (percent <= 0) return false;
        discount(villager, percent / 100f);
        return true;
    }

    private static void discount(Villager villager, float fraction) {
        for (MerchantOffer offer : villager.getOffers()) {
            int cost = offer.getBaseCostA().getCount();
            offer.addToSpecialPriceDiff(-Math.max(1, Math.round(cost * fraction)));
        }
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (ClassMentors.teaches(event.getAbstractVillager(), RpgManager.get(player).getPlayerClass())) {
            RpgManager.addXp(player, XP_PER_TRADE);
        }
    }
}
