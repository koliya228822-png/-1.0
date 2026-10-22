package com.heroicpaths.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Клиентские настройки мода: хранятся в config/heroicpaths-client.toml. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;
    public static final ModConfigSpec.BooleanValue SHOW_PARTY;
    public static final ModConfigSpec.BooleanValue SHOW_COMPANION;
    public static final ModConfigSpec.BooleanValue SHOW_ORDERS;
    public static final ModConfigSpec.IntValue PLATE_DISTANCE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        HUD_SCALE = builder.comment("Размер панели мода на экране (0.5 — 2.0)")
                .translation("config.heroicpaths.hud_scale")
                .defineInRange("hudScale", 1.0, 0.5, 2.0);
        SHOW_PARTY = builder.comment("Показывать состав группы").translation("config.heroicpaths.show_party").define("showParty", true);
        SHOW_COMPANION = builder.comment("Показывать здоровье зверя охотника").translation("config.heroicpaths.show_companion")
                .define("showCompanion", true);
        SHOW_ORDERS = builder.comment("Показывать взятые заказы гильдии").translation("config.heroicpaths.show_orders")
                .define("showOrders", true);
        PLATE_DISTANCE = builder.comment("С какого расстояния (в блоках) видна плашка с уровнем над мобом; 0 — не показывать")
                .translation("config.heroicpaths.plate_distance").defineInRange("plateDistance", 9, 0, 32);
        SPEC = builder.build();
    }

    private ClientConfig() {}

    public static float hudScale() {
        return SPEC.isLoaded() ? HUD_SCALE.get().floatValue() : 1f;
    }

    public static boolean showParty() {
        return !SPEC.isLoaded() || SHOW_PARTY.get();
    }

    public static boolean showCompanion() {
        return !SPEC.isLoaded() || SHOW_COMPANION.get();
    }

    public static boolean showOrders() {
        return !SPEC.isLoaded() || SHOW_ORDERS.get();
    }

    public static int plateDistance() {
        return SPEC.isLoaded() ? PLATE_DISTANCE.get() : 9;
    }
}
