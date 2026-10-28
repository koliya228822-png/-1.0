package com.heroicpaths.client.map;

import com.heroicpaths.network.WaystoneActionPayload;
import com.heroicpaths.network.WaystoneListPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Выбор точки перемещения: у камня (бесплатно) или по свитку (свиток тратится). У своего камня можно сменить название.
 */
public class WaystoneScreen extends Screen {
    private static final int ROWS = 7, ROW_H = 22, WIDTH = 240;
    private final boolean scroll;
    private final BlockPos source;
    private int offset;
    private EditBox nameBox;

    public WaystoneScreen(boolean scroll, BlockPos source) {
        super(Component.translatable(scroll ? "gui.heroicpaths.waystone.scroll_title" : "gui.heroicpaths.waystone.title"));
        this.scroll = scroll;
        this.source = source.immutable();
    }

    boolean matches(WaystoneListPayload payload) {
        return (payload.mode() == WaystoneListPayload.FROM_SCROLL) == scroll && payload.source().equals(source);
    }

    void refresh() {
        String typed = nameBox == null ? null : nameBox.getValue();
        rebuildWidgets();
        if (typed != null && nameBox != null && !typed.equals(nameBox.getValue()) && nameBox.isFocused()) nameBox.setValue(typed);
    }

    /** Точки, куда можно попасть отсюда: все известные, кроме этого камня. */
    private List<WaystoneListPayload.Point> targets() {
        List<WaystoneListPayload.Point> list = new ArrayList<>();
        String here = minecraft.level.dimension().location().toString();
        for (WaystoneListPayload.Point point : ClientWaystones.points()) {
            if (!scroll && point.pos().equals(source) && point.dimension().toString().equals(here)) continue;
            list.add(point);
        }
        return list;
    }

    private WaystoneListPayload.Point current() {
        if (scroll) return null;
        for (WaystoneListPayload.Point point : ClientWaystones.points()) {
            if (point.pos().equals(source) && point.dimension().equals(minecraft.level.dimension().location())) return point;
        }
        return null;
    }

    @Override
    protected void init() {
        int left = (width - WIDTH) / 2, top = height / 2 - (ROWS * ROW_H) / 2 - 10;
        WaystoneListPayload.Point here = current();
        if (here != null && here.own()) {
            nameBox = new EditBox(font, left, top - 26, WIDTH - 84, 18, Component.translatable("gui.heroicpaths.waystone.name"));
            nameBox.setMaxLength(32);
            nameBox.setValue(here.name());
            addRenderableWidget(nameBox);
            addRenderableWidget(Button.builder(Component.translatable("gui.heroicpaths.waystone.rename"), b -> {
                String name = nameBox.getValue().strip();
                if (!name.isEmpty()) {
                    PacketDistributor.sendToServer(new WaystoneActionPayload(WaystoneActionPayload.RENAME, here.dimension(), source, source, false, name));
                }
            }).bounds(left + WIDTH - 80, top - 27, 80, 20).build());
        } else {
            nameBox = null;
        }
        List<WaystoneListPayload.Point> targets = targets();
        offset = Math.max(0, Math.min(offset, targets.size() - ROWS));
        for (int i = 0; i < ROWS && offset + i < targets.size(); i++) {
            WaystoneListPayload.Point point = targets.get(offset + i);
            addRenderableWidget(Button.builder(label(point), b -> travel(point))
                    .bounds(left, top + i * ROW_H, WIDTH, 20).build());
        }
        if (targets.size() > ROWS) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> { offset = Math.max(0, offset - ROWS); rebuildWidgets(); })
                    .bounds(left + WIDTH + 4, top, 20, 20).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> { offset += ROWS; rebuildWidgets(); })
                    .bounds(left + WIDTH + 4, top + (ROWS - 1) * ROW_H, 20, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 50, top + ROWS * ROW_H + 6, 100, 20).build());
    }

    private Component label(WaystoneListPayload.Point point) {
        String here = minecraft.level.dimension().location().toString();
        if (!point.dimension().toString().equals(here)) {
            return Component.translatable("gui.heroicpaths.waystone.other_world", point.name(), dimensionName(point));
        }
        int distance = (int) Math.sqrt(point.pos().distToCenterSqr(minecraft.player.position()));
        return Component.translatable("gui.heroicpaths.waystone.entry", point.name(), distance);
    }

    private static Component dimensionName(WaystoneListPayload.Point point) {
        String path = point.dimension().getPath();
        return Component.translatable("gui.heroicpaths.dimension." + path);
    }

    private void travel(WaystoneListPayload.Point point) {
        PacketDistributor.sendToServer(new WaystoneActionPayload(WaystoneActionPayload.TRAVEL, point.dimension(), point.pos(), source, scroll, ""));
        onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int size = targets().size();
        if (size > ROWS) {
            offset = Math.max(0, Math.min(size - ROWS, offset - (int) Math.signum(scrollY)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int top = height / 2 - (ROWS * ROW_H) / 2 - 10;
        int titleY = nameBox != null ? top - 42 : top - 16;
        graphics.drawCenteredString(font, title, width / 2, titleY, 0xFFFFFF);
        if (targets().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("gui.heroicpaths.waystone.empty"), width / 2, top + 20, 0xAAAAAA);
        }
        if (scroll) {
            graphics.drawCenteredString(font, Component.translatable("gui.heroicpaths.waystone.scroll_hint"), width / 2, top + ROWS * ROW_H + 32, 0xAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
