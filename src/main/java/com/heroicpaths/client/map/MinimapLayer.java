package com.heroicpaths.client.map;

import com.heroicpaths.network.WaystoneListPayload;
import com.mojang.math.Axis;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Миникарта в правом верхнем углу: видна, пока у игрока в инвентаре есть карта (пустая или заполненная).
 * Север сверху, стрелка — игрок, цветные точки — его метки, зелёные ромбы — известные камни перемещения.
 */
public final class MinimapLayer implements LayeredDraw.Layer {
    /** Размер миникарты на экране и сколько блоков она охватывает. */
    public static final int SIZE = 64, BLOCKS = 128, MARGIN = 4;
    private static MapRenderer renderer;
    private static boolean visible;
    private static long lastRender;
    private static int lastX = Integer.MIN_VALUE, lastZ, lastCave;

    public static boolean visible() {
        return visible;
    }

    /** Есть ли у игрока карта в инвентаре. */
    public static boolean hasMap(Player player) {
        return player.getInventory().contains(stack -> stack.is(Items.MAP) || stack.is(Items.FILLED_MAP));
    }

    /** Под землёй или в мире с потолком карта показывает пол пещеры на уровне игрока. */
    public static int caveLevel(Player player) {
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        if (level.dimensionType().hasCeiling()) return pos.getY();
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        return pos.getY() < surface - 4 && !level.canSeeSky(pos.above()) ? pos.getY() : Integer.MIN_VALUE;
    }

    /** Каждый тик: показывать ли карту и пора ли её перерисовать. */
    public static void tick(Minecraft minecraft) {
        Player player = minecraft.player;
        visible = player != null && minecraft.level != null && hasMap(player);
        if (!visible) return;
        ClientWaypoints.ensureLoaded();
        if (renderer == null) renderer = new MapRenderer("minimap", BLOCKS);
        long time = minecraft.level.getGameTime();
        int x = player.getBlockX(), z = player.getBlockZ(), cave = caveLevel(player);
        boolean moved = x != lastX || z != lastZ || cave != lastCave;
        if ((moved && time - lastRender >= 2) || time - lastRender >= 40 || time < lastRender) {
            renderer.render(minecraft.level, x, z, 1, cave);
            lastRender = time;
            lastX = x;
            lastZ = z;
            lastCave = cave;
        }
    }

    public static void reset() {
        lastX = Integer.MIN_VALUE;
        lastRender = 0;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!visible || renderer == null || player == null || minecraft.options.hideGui || minecraft.getDebugOverlay().showDebugScreen()) return;
        int x = graphics.guiWidth() - SIZE - MARGIN, y = MARGIN;
        graphics.fill(x - 2, y - 2, x + SIZE + 2, y + SIZE + 2, 0xFF2B1D10);
        graphics.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, 0xFFB08A50);
        graphics.blit(renderer.location(), x, y, SIZE, SIZE, 0, 0, BLOCKS, BLOCKS, BLOCKS, BLOCKS);
        float scale = SIZE / (float) BLOCKS;
        double centerX = renderer.centerX() + 0.5, centerZ = renderer.centerZ() + 0.5;
        Font font = minecraft.font;
        graphics.enableScissor(x, y, x + SIZE, y + SIZE);
        for (WaystoneListPayload.Point point : ClientWaystones.points()) {
            if (!point.dimension().equals(player.level().dimension().location())) continue;
            float px = x + SIZE / 2f + (float) (point.pos().getX() + 0.5 - centerX) * scale;
            float pz = y + SIZE / 2f + (float) (point.pos().getZ() + 0.5 - centerZ) * scale;
            drawWaystone(graphics, px, pz);
        }
        graphics.disableScissor();
        for (ClientWaypoints.Waypoint waypoint : ClientWaypoints.here()) {
            float px = (float) (waypoint.x + 0.5 - centerX) * scale, pz = (float) (waypoint.z + 0.5 - centerZ) * scale;
            boolean inside = Math.abs(px) <= SIZE / 2f - 2 && Math.abs(pz) <= SIZE / 2f - 2;
            if (!inside) {
                // Дальняя метка — на краю карты в её сторону.
                float k = (SIZE / 2f - 2) / Math.max(Math.abs(px), Math.abs(pz));
                px *= k;
                pz *= k;
            }
            int dx = Math.round(x + SIZE / 2f + px), dz = Math.round(y + SIZE / 2f + pz);
            graphics.fill(dx - 2, dz - 2, dx + 2, dz + 2, 0xFF000000);
            graphics.fill(dx - 1, dz - 1, dx + 1, dz + 1, waypoint.color);
            if (inside) {
                graphics.pose().pushPose();
                graphics.pose().translate(dx, dz - 7, 0);
                graphics.pose().scale(0.5f, 0.5f, 1);
                String name = waypoint.name;
                graphics.drawString(font, name, -font.width(name) / 2, 0, waypoint.color, true);
                graphics.pose().popPose();
            }
        }
        drawArrow(graphics, x + SIZE / 2f + (float) (player.getX() - centerX) * scale,
                y + SIZE / 2f + (float) (player.getZ() - centerZ) * scale, player.getViewYRot(delta.getGameTimeDeltaPartialTick(false)));
        // Север и координаты.
        graphics.drawString(font, "N", x + SIZE / 2 - font.width("N") / 2, y + 1, 0xFFFFFFFF, true);
        String coords = player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ();
        graphics.pose().pushPose();
        graphics.pose().translate(x + SIZE / 2f, y + SIZE + 4, 0);
        graphics.pose().scale(0.75f, 0.75f, 1);
        graphics.drawString(font, coords, -font.width(coords) / 2, 0, 0xFFFFFFFF, true);
        graphics.pose().popPose();
    }

    /** Зелёный ромб камня перемещения. */
    static void drawWaystone(GuiGraphics graphics, float x, float y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(45));
        graphics.fill(-2, -2, 2, 2, 0xFF0B3D1B);
        graphics.fill(-1, -1, 1, 1, 0xFF3CF07A);
        graphics.pose().popPose();
    }

    /** Стрелка игрока, повёрнутая по взгляду (0° — на юг, вниз по карте). */
    static void drawArrow(GuiGraphics graphics, float x, float y, float yaw) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(Mth.wrapDegrees(yaw) + 180));
        graphics.pose().scale(0.5f, 0.5f, 1);
        // Остриё вверх (до поворота): треугольник с чёрной обводкой и выемкой снизу.
        for (int pass = 0; pass < 2; pass++) {
            int grow = pass == 0 ? 1 : 0, color = pass == 0 ? 0xFF000000 : 0xFFFFFFFF;
            for (int row = 0; row < 10; row++) {
                int ry = -6 + row, half = Math.min(row / 2 + 1, 5) + grow;
                if (row >= 8) {
                    // Выемка: середина нижних строк пустая.
                    graphics.fill(-half, ry, -1 - (row - 7) + grow, ry + 1 + grow, color);
                    graphics.fill(1 + (row - 7) - grow, ry, half, ry + 1 + grow, color);
                } else {
                    graphics.fill(-half, ry - grow, half, ry + 1, color);
                }
            }
        }
        graphics.pose().popPose();
    }
}
