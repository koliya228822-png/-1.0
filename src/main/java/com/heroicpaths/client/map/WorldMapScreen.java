package com.heroicpaths.client.map;

import com.heroicpaths.network.WaystoneListPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * Большая карта (клавиша M, нужна карта в инвентаре). Левый клик по карте ставит метку с названием из поля,
 * правый клик по метке убирает её; колесо — масштаб, перетаскивание — сдвиг. Справа — список меток:
 * выбранную можно переименовать, перекрасить или удалить.
 */
public class WorldMapScreen extends Screen {
    private static final int TEXTURE = 256, PANEL = 140, ROW_H = 14;
    private static final int[] STEPS = {1, 2, 4};
    private static MapRenderer renderer;
    private int zoom = 1;
    private double centerX, centerZ;
    private boolean dirty = true, dragged;
    private int mapX, mapY, mapSize;
    private int listOffset;
    private ClientWaypoints.Waypoint selected;
    private EditBox nameBox;
    private Button renameButton, colorButton, deleteButton;

    public WorldMapScreen() {
        super(Component.translatable("gui.heroicpaths.map.title"));
    }

    @Override
    protected void init() {
        ClientWaypoints.ensureLoaded();
        if (renderer == null) renderer = new MapRenderer("worldmap", TEXTURE);
        if (minecraft.player != null && centerX == 0 && centerZ == 0) {
            centerX = minecraft.player.getBlockX();
            centerZ = minecraft.player.getBlockZ();
        }
        mapSize = Math.max(64, Math.min(width - PANEL - 24, height - 40));
        mapX = (width - PANEL - 8 - mapSize) / 2;
        mapY = (height - mapSize) / 2 + 6;
        int px = mapX + mapSize + 8;
        nameBox = new EditBox(font, px, mapY, PANEL, 16, Component.translatable("gui.heroicpaths.map.name"));
        nameBox.setMaxLength(ClientWaypoints.MAX_NAME);
        nameBox.setHint(Component.translatable("gui.heroicpaths.map.name_hint"));
        if (selected != null) nameBox.setValue(selected.name);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("gui.heroicpaths.map.add_here"), b -> {
            var player = minecraft.player;
            select(ClientWaypoints.add(nameBox.getValue(), player.getBlockX(), player.getBlockY(), player.getBlockZ()));
        }).bounds(px, mapY + 20, PANEL, 18).build());
        renameButton = addRenderableWidget(Button.builder(Component.translatable("gui.heroicpaths.map.rename"), b -> {
            if (selected != null) ClientWaypoints.rename(selected, nameBox.getValue());
        }).bounds(px, mapY + 40, PANEL / 2 - 1, 18).build());
        colorButton = addRenderableWidget(Button.builder(Component.translatable("gui.heroicpaths.map.color"), b -> {
            if (selected != null) ClientWaypoints.recolor(selected);
        }).bounds(px + PANEL / 2 + 1, mapY + 40, PANEL / 2 - 1, 18).build());
        deleteButton = addRenderableWidget(Button.builder(Component.translatable("gui.heroicpaths.map.delete"), b -> {
            if (selected != null) {
                ClientWaypoints.remove(selected);
                select(null);
            }
        }).bounds(px, mapY + 60, PANEL, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.heroicpaths.map.center"), b -> {
            centerX = minecraft.player.getBlockX();
            centerZ = minecraft.player.getBlockZ();
            dirty = true;
        }).bounds(px, mapY + mapSize - 18, PANEL, 18).build());
        updateButtons();
        dirty = true;
    }

    private void select(ClientWaypoints.Waypoint waypoint) {
        selected = waypoint;
        nameBox.setValue(waypoint == null ? "" : waypoint.name);
        updateButtons();
    }

    private void updateButtons() {
        boolean any = selected != null;
        renameButton.active = any;
        colorButton.active = any;
        deleteButton.active = any;
    }

    private int step() {
        return STEPS[zoom];
    }

    /** Блоков на экранный пиксель карты. */
    private double scale() {
        return TEXTURE * step() / (double) mapSize;
    }

    private boolean overMap(double mx, double my) {
        return mx >= mapX && mx < mapX + mapSize && my >= mapY && my < mapY + mapSize;
    }

    private double toScreenX(double blockX) {
        return mapX + mapSize / 2.0 + (blockX - renderer.centerX()) / scale();
    }

    private double toScreenZ(double blockZ) {
        return mapY + mapSize / 2.0 + (blockZ - renderer.centerZ()) / scale();
    }

    @Override
    public void tick() {
        if (dirty && renderer != null && minecraft.level != null) {
            renderer.render(minecraft.level, (int) Math.floor(centerX), (int) Math.floor(centerZ), step(), MinimapLayer.caveLevel(minecraft.player));
            dirty = false;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (renderer == null || renderer.centerX() == Integer.MIN_VALUE) tick();
        graphics.drawCenteredString(font, title, mapX + mapSize / 2, mapY - 14, 0xFFFFFF);
        graphics.fill(mapX - 2, mapY - 2, mapX + mapSize + 2, mapY + mapSize + 2, 0xFF2B1D10);
        graphics.fill(mapX - 1, mapY - 1, mapX + mapSize + 1, mapY + mapSize + 1, 0xFFB08A50);
        graphics.blit(renderer.location(), mapX, mapY, mapSize, mapSize, 0, 0, TEXTURE, TEXTURE, TEXTURE, TEXTURE);

        Component tooltip = null;
        graphics.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);
        String dimension = minecraft.level.dimension().location().toString();
        for (WaystoneListPayload.Point point : ClientWaystones.points()) {
            if (!point.dimension().toString().equals(dimension)) continue;
            float sx = (float) toScreenX(point.pos().getX() + 0.5), sz = (float) toScreenZ(point.pos().getZ() + 0.5);
            graphics.pose().pushPose();
            graphics.pose().translate(sx, sz, 0);
            graphics.pose().scale(1.5f, 1.5f, 1);
            MinimapLayer.drawWaystone(graphics, 0, 0);
            graphics.pose().popPose();
            if (Math.abs(mouseX - sx) <= 4 && Math.abs(mouseY - sz) <= 4) tooltip = Component.translatable("gui.heroicpaths.map.waystone", point.name());
        }
        for (ClientWaypoints.Waypoint waypoint : ClientWaypoints.here()) {
            int sx = (int) Math.round(toScreenX(waypoint.x + 0.5)), sz = (int) Math.round(toScreenZ(waypoint.z + 0.5));
            int r = waypoint == selected ? 4 : 3;
            graphics.fill(sx - r, sz - r, sx + r, sz + r, waypoint == selected ? 0xFFFFFFFF : 0xFF000000);
            graphics.fill(sx - r + 1, sz - r + 1, sx + r - 1, sz + r - 1, waypoint.color);
            graphics.drawCenteredString(font, waypoint.name, sx, sz - r - 10, waypoint.color);
            if (Math.abs(mouseX - sx) <= r && Math.abs(mouseY - sz) <= r) {
                tooltip = Component.translatable("gui.heroicpaths.map.waypoint", waypoint.name, waypoint.x, waypoint.y, waypoint.z);
            }
        }
        var player = minecraft.player;
        MinimapLayer.drawArrow(graphics, (float) toScreenX(player.getX()), (float) toScreenZ(player.getZ()), player.getYRot());
        graphics.disableScissor();
        graphics.drawString(font, "N", mapX + mapSize / 2 - font.width("N") / 2, mapY + 2, 0xFFFFFFFF, true);

        // Координаты под курсором и подсказка.
        if (overMap(mouseX, mouseY)) {
            int bx = (int) Math.floor(renderer.centerX() + (mouseX - mapX - mapSize / 2.0) * scale());
            int bz = (int) Math.floor(renderer.centerZ() + (mouseY - mapY - mapSize / 2.0) * scale());
            graphics.drawString(font, bx + ", " + bz, mapX + 3, mapY + mapSize - 11, 0xFFFFFFFF, true);
        }
        graphics.drawString(font, Component.translatable("gui.heroicpaths.map.hint"), mapX, mapY + mapSize + 5, 0xAAAAAA, false);

        // Список меток этого измерения.
        int px = mapX + mapSize + 8, listTop = mapY + 84, listBottom = mapY + mapSize - 22;
        List<ClientWaypoints.Waypoint> here = ClientWaypoints.here();
        int rows = Math.max(1, (listBottom - listTop) / ROW_H);
        listOffset = Math.max(0, Math.min(listOffset, here.size() - rows));
        graphics.drawString(font, Component.translatable("gui.heroicpaths.map.list", here.size()), px, listTop - 2, 0xFFFFFF, false);
        for (int i = 0; i < rows && listOffset + i < here.size(); i++) {
            ClientWaypoints.Waypoint waypoint = here.get(listOffset + i);
            int y = listTop + 10 + i * ROW_H;
            if (waypoint == selected) graphics.fill(px - 1, y - 2, px + PANEL, y + ROW_H - 3, 0x60FFFFFF);
            graphics.fill(px, y, px + 6, y + 6, waypoint.color);
            int distance = (int) Math.sqrt(player.distanceToSqr(waypoint.x + 0.5, player.getY(), waypoint.z + 0.5));
            String line = font.plainSubstrByWidth(waypoint.name, PANEL - 50);
            graphics.drawString(font, line, px + 9, y - 1, 0xFFFFFF, false);
            String dist = distance + "м";
            graphics.drawString(font, dist, px + PANEL - font.width(dist), y - 1, 0xAAAAAA, false);
        }
        if (tooltip != null) graphics.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    /** Метка под курсором (в пределах нескольких пикселей). */
    private ClientWaypoints.Waypoint waypointAt(double mx, double my) {
        for (ClientWaypoints.Waypoint waypoint : ClientWaypoints.here()) {
            if (Math.abs(mx - toScreenX(waypoint.x + 0.5)) <= 5 && Math.abs(my - toScreenZ(waypoint.z + 0.5)) <= 5) return waypoint;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        dragged = false;
        // Клик по строке списка выбирает метку.
        int px = mapX + mapSize + 8, listTop = mapY + 84 + 10;
        List<ClientWaypoints.Waypoint> here = ClientWaypoints.here();
        if (mx >= px && mx < px + PANEL && my >= listTop - 2 && my < mapY + mapSize - 22) {
            int index = listOffset + (int) ((my - listTop + 2) / ROW_H);
            if (index >= 0 && index < here.size()) {
                select(here.get(index));
                centerX = selected.x;
                centerZ = selected.z;
                dirty = true;
                return true;
            }
        }
        if (overMap(mx, my) && button == 1) {
            ClientWaypoints.Waypoint hit = waypointAt(mx, my);
            if (hit != null) {
                ClientWaypoints.remove(hit);
                if (hit == selected) select(null);
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && overMap(mx, my)) {
            centerX -= dx * scale();
            centerZ -= dy * scale();
            if (Math.abs(dx) + Math.abs(dy) > 0) dragged = true;
            dirty = true;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && overMap(mx, my) && !dragged) {
            ClientWaypoints.Waypoint hit = waypointAt(mx, my);
            if (hit != null) {
                select(hit);
            } else {
                int bx = (int) Math.floor(renderer.centerX() + (mx - mapX - mapSize / 2.0) * scale());
                int bz = (int) Math.floor(renderer.centerZ() + (my - mapY - mapSize / 2.0) * scale());
                int by = minecraft.level.getChunkSource().hasChunk(bx >> 4, bz >> 4)
                        ? minecraft.level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz) : minecraft.player.getBlockY();
                select(ClientWaypoints.add(nameBox.getValue(), bx, by, bz));
            }
            dragged = false;
            return true;
        }
        dragged = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (overMap(mx, my)) {
            int next = Math.max(0, Math.min(STEPS.length - 1, zoom - (int) Math.signum(scrollY)));
            if (next != zoom) {
                zoom = next;
                dirty = true;
            }
            return true;
        }
        if (mx > mapX + mapSize) {
            listOffset = Math.max(0, listOffset - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (!nameBox.isFocused() && com.heroicpaths.client.ModKeys.OPEN_MAP.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
