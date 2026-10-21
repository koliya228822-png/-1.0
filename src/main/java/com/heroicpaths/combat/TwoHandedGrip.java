package com.heroicpaths.combat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Двуручное оружие держат обеими руками: пока оно в основной руке, слот второй руки закрыт.
 * Предмет, оказавшийся во второй руке, сразу убирается в инвентарь (или падает, если места нет),
 * а само двуручное оружие нельзя переложить во вторую руку.
 */
public final class TwoHandedGrip {
    private TwoHandedGrip() {}

    /** Освобождает вторую руку, если она занята при двуручном оружии. Возвращает true, если что-то убрано. */
    public static boolean enforce(Player player) {
        ItemStack off = player.getOffhandItem();
        if (off.isEmpty() || !(WeaponType.isTwoHanded(player.getMainHandItem()) || WeaponType.isTwoHanded(off))) return false;
        ItemStack moved = off.copy();
        player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        if (!player.getInventory().add(moved)) player.drop(moved, false);
        player.displayClientMessage(Component.translatable("message.heroicpaths.two_handed_offhand").withStyle(ChatFormatting.YELLOW), true);
        return true;
    }

    /** Можно ли поменять руки местами: двуручное оружие во вторую руку не берут. */
    public static boolean canSwap(ItemStack toOffHand) {
        return !WeaponType.isTwoHanded(toOffHand);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) enforce(player);
    }

    @SubscribeEvent
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (!(event.getEntity() instanceof Player player) || canSwap(event.getItemSwappedToOffHand())) return;
        event.setCanceled(true);
        player.displayClientMessage(Component.translatable("message.heroicpaths.two_handed_swap").withStyle(ChatFormatting.YELLOW), true);
    }
}
