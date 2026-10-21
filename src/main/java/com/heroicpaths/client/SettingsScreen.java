package com.heroicpaths.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Настройки «Путей героев»: размер панели мода, что на ней показывать, и переход к назначению клавиш.
 * Открывается из меню K и из списка модов.
 */
public class SettingsScreen extends Screen {
    private static final double[] SCALES = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0};
    @Nullable
    private final Screen parent;

    public SettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.heroicpaths.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 110;
        int y = buttonsTop();
        addRenderableWidget(Button.builder(scaleLabel(), b -> {
            ClientConfig.HUD_SCALE.set(nextScale(ClientConfig.hudScale()));
            ClientConfig.SPEC.save();
            b.setMessage(scaleLabel());
        }).bounds(x, y, 220, 20).build());
        addRenderableWidget(Button.builder(toggleLabel("screen.heroicpaths.settings.party", ClientConfig.showParty()), b -> {
            ClientConfig.SHOW_PARTY.set(!ClientConfig.showParty());
            ClientConfig.SPEC.save();
            b.setMessage(toggleLabel("screen.heroicpaths.settings.party", ClientConfig.showParty()));
        }).bounds(x, y + 24, 220, 20).build());
        addRenderableWidget(Button.builder(toggleLabel("screen.heroicpaths.settings.companion", ClientConfig.showCompanion()), b -> {
            ClientConfig.SHOW_COMPANION.set(!ClientConfig.showCompanion());
            ClientConfig.SPEC.save();
            b.setMessage(toggleLabel("screen.heroicpaths.settings.companion", ClientConfig.showCompanion()));
        }).bounds(x, y + 48, 220, 20).build());
        addRenderableWidget(Button.builder(toggleLabel("screen.heroicpaths.settings.orders", ClientConfig.showOrders()), b -> {
            ClientConfig.SHOW_ORDERS.set(!ClientConfig.showOrders());
            ClientConfig.SPEC.save();
            b.setMessage(toggleLabel("screen.heroicpaths.settings.orders", ClientConfig.showOrders()));
        }).bounds(x, y + 72, 220, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.heroicpaths.settings.controls"),
                b -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options))).bounds(x, y + 96, 220, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    private int buttonsTop() {
        return Math.max(36, height / 2 - 82);
    }

    private static double nextScale(float current) {
        for (double scale : SCALES) if (scale > current + 0.01) return scale;
        return SCALES[0];
    }

    private static Component scaleLabel() {
        return Component.translatable("screen.heroicpaths.settings.scale", Math.round(ClientConfig.hudScale() * 100));
    }

    private static Component toggleLabel(String key, boolean value) {
        return Component.translatable(key, value ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFD27F);
        List<FormattedCharSequence> hint = font.split(Component.translatable("screen.heroicpaths.settings.hint"), Math.min(320, width - 30));
        int top = buttonsTop() + 5 * 24 + 6;
        for (int i = 0; i < hint.size(); i++) graphics.drawCenteredString(font, hint.get(i), width / 2, top + i * 10, 0x888888);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
