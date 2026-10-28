package com.heroicpaths.client.map;

import com.heroicpaths.network.WaystoneListPayload;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Камни перемещения, которые знает игрок (присылает сервер): видны на картах, к ним ведёт окно выбора точки. */
public final class ClientWaystones {
    private static List<WaystoneListPayload.Point> points = List.of();

    private ClientWaystones() {}

    public static List<WaystoneListPayload.Point> points() {
        return points;
    }

    public static void receive(WaystoneListPayload payload) {
        points = List.copyOf(payload.points());
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.mode() == WaystoneListPayload.SYNC) {
            if (minecraft.screen instanceof WaystoneScreen screen) screen.refresh();
            return;
        }
        if (minecraft.screen instanceof WaystoneScreen screen && screen.matches(payload)) screen.refresh();
        else minecraft.setScreen(new WaystoneScreen(payload.mode() == WaystoneListPayload.FROM_SCROLL, payload.source()));
    }

    public static void clear() {
        points = List.of();
    }
}
