package com.heroicpaths.craft;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.enchanting.EnchantmentLevelSetEvent;

/** Ванильный стол зачарований не предлагает чар для оружия и брони: их зачаровывает только рунный мастер. */
public final class CraftEvents {
    private CraftEvents() {}

    @SubscribeEvent
    public static void onEnchantmentLevel(EnchantmentLevelSetEvent event) {
        if (RuneEnchanting.isRuneOnly(event.getItem())) event.setEnchantLevel(0);
    }
}
