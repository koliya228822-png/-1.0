package com.heroicpaths.client;

import com.heroicpaths.accessory.Accessories;
import com.heroicpaths.accessory.AccessoryMenu;
import com.heroicpaths.gear.GearSets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

/** Окно аксессуаров: слоты рисуются рамками с подписью вида вещи, посередине — активные сеты. */
public class AccessoryScreen extends AbstractContainerScreen<AccessoryMenu> {
    public AccessoryScreen(AccessoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 184;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF1E1E24);
        graphics.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, 0xFF34343E);
        for (int i = 0; i < Accessories.SIZE; i++) {
            int sx = x + AccessoryMenu.SLOT_POS[i][0], sy = y + AccessoryMenu.SLOT_POS[i][1];
            frame(graphics, sx, sy);
            if (menu.getSlot(i).hasItem()) continue;
            Component label = Accessories.SLOTS[i].displayName();
            // Подпись вида — рядом со слотом: слева у левой колонки, справа у правой.
            boolean left = AccessoryMenu.SLOT_POS[i][0] < imageWidth / 2;
            int lx = left ? sx + 20 : sx - 3 - font.width(label);
            graphics.drawString(font, label, lx, sy + 5, 0xFF8A8AA0, false);
        }
        for (int i = Accessories.SIZE; i < menu.slots.size(); i++) {
            frame(graphics, x + menu.slots.get(i).x, y + menu.slots.get(i).y);
        }
    }

    private static void frame(GuiGraphics graphics, int sx, int sy) {
        graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF18181C);
        graphics.fill(sx, sy, sx + 17, sy + 17, 0xFF5A5A66);
        graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF26262E);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 6, 0xFFE0C080, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFB0B0B0, false);
        List<Component> sets = GearSets.summary(minecraft.player);
        int y = 92 - sets.size() * 9;
        for (Component line : sets) {
            for (FormattedCharSequence part : font.split(line, 64)) {
                graphics.drawString(font, part, 54, Math.max(18, y), 0xFFFFFFFF, false);
                y += 9;
            }
        }
    }
}
