package com.heroicpaths.accessory;

import com.heroicpaths.rpg.RpgManager;
import net.minecraft.server.level.ServerPlayer;

/** Аксессуары сменились: характеристики считаются заново, клиент получает новые данные. */
public final class AccessoryEvents {
    private AccessoryEvents() {}

    public static void changed(ServerPlayer player) {
        RpgManager.applyPassives(player);
        RpgManager.sync(player);
    }
}
