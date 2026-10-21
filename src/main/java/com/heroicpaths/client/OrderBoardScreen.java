package com.heroicpaths.client;

import com.heroicpaths.guild.GuildOrders;
import com.heroicpaths.guild.Order;
import com.heroicpaths.guild.OrderType;
import com.heroicpaths.network.OrderActionPayload;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Доска заказов гильдии искателей приключений: слева заказы на сегодня, справа свои взятые заказы с прогрессом.
 * Взять, сдать и бросить заказ можно кнопками; окно обновляется, когда сервер присылает новые данные игрока.
 */
public class OrderBoardScreen extends Screen {
    /** Высота строки: подбирается под окно, чтобы все заказы поместились над кнопкой «Готово». */
    private int row = 30;
    private final BlockPos pos;
    private final List<Order> offers;
    private int seenVersion = -1;

    public OrderBoardScreen(BlockPos pos, List<Order> offers) {
        super(Component.translatable("screen.heroicpaths.order_board"));
        this.pos = pos;
        this.offers = List.copyOf(offers);
    }

    private RpgData data() {
        return RpgManager.get(minecraft.player);
    }

    private int columnWidth() {
        return Math.min(240, (width - 24) / 2);
    }

    private int leftX() {
        return width / 2 - 4 - columnWidth();
    }

    private int rightX() {
        return width / 2 + 4;
    }

    private int listTop() {
        return 48;
    }

    @Override
    protected void init() {
        RpgData data = data();
        seenVersion = data.getSyncVersion();
        row = net.minecraft.util.Mth.clamp((height - listTop() - 30) / Math.max(1, offers.size()), 26, 30);
        int column = columnWidth();
        for (int i = 0; i < offers.size(); i++) {
            Order offer = offers.get(i);
            int index = i;
            int y = listTop() + i * row;
            Button take = Button.builder(Component.translatable("screen.heroicpaths.order_take"),
                            b -> PacketDistributor.sendToServer(new OrderActionPayload(OrderActionPayload.TAKE, pos, index)))
                    .bounds(leftX() + column - 50, buttonY(y), 48, 14).build();
            take.active = !data.hasTakenOrder(offer.id()) && data.getOrders().size() < GuildOrders.MAX_ACTIVE
                    && data.getLevel() >= offer.minLevel();
            if (data.hasTakenOrder(offer.id())) take.setMessage(Component.translatable("screen.heroicpaths.order_taken_short"));
            else if (data.getLevel() < offer.minLevel()) take.setTooltip(Tooltip.create(Component.translatable("message.heroicpaths.order_level", offer.minLevel())));
            addRenderableWidget(take);
        }
        List<Order> mine = data.getOrders();
        for (int i = 0; i < mine.size(); i++) {
            Order order = mine.get(i);
            int slot = i;
            int y = listTop() + i * row;
            Button turnIn = Button.builder(Component.translatable("screen.heroicpaths.order_turn_in"),
                            b -> PacketDistributor.sendToServer(new OrderActionPayload(OrderActionPayload.TURN_IN, pos, slot)))
                    .bounds(rightX() + column - 72, buttonY(y), 52, 14).build();
            turnIn.active = ready(order);
            addRenderableWidget(turnIn);
            addRenderableWidget(Button.builder(Component.literal("✕"),
                            b -> PacketDistributor.sendToServer(new OrderActionPayload(OrderActionPayload.ABANDON, pos, slot)))
                    .bounds(rightX() + column - 18, buttonY(y), 16, 14)
                    .tooltip(Tooltip.create(Component.translatable("screen.heroicpaths.order_abandon"))).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 50, height - 26, 100, 20).build());
    }

    /** Кнопки стоят во второй строке заказа, под названием. */
    private int buttonY(int rowY) {
        return rowY + row - 16;
    }

    /** Заказ можно сдать: цель достигнута, а для доставки — нужные предметы в инвентаре. */
    private boolean ready(Order order) {
        return order.type() == OrderType.DELIVERY ? GuildOrders.countItems(minecraft.player, order) >= order.amount() : order.isDone();
    }

    private int progressOf(Order order) {
        return order.type() == OrderType.DELIVERY ? Math.min(order.amount(), GuildOrders.countItems(minecraft.player, order)) : order.progress();
    }

    @Override
    public void tick() {
        if (data().getSyncVersion() != seenVersion) rebuildWidgets();
        if (minecraft.player.distanceToSqr(pos.getCenter()) > GuildOrders.BOARD_RANGE_SQR) onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFD27F);
        List<FormattedCharSequence> hint = font.split(Component.translatable("screen.heroicpaths.order_hint"), Math.max(120, width - 30));
        for (int i = 0; i < Math.min(2, hint.size()); i++) graphics.drawCenteredString(font, hint.get(i), width / 2, 19 + i * 10, 0x9A9A9A);

        int column = columnWidth();
        RpgData data = data();
        graphics.drawString(font, Component.translatable("screen.heroicpaths.order_offers"), leftX(), listTop() - 10, 0xFFD27F, true);
        graphics.drawString(font, Component.translatable("screen.heroicpaths.order_mine", data.getOrders().size(), GuildOrders.MAX_ACTIVE),
                rightX(), listTop() - 10, 0xFFD27F, true);

        List<Component> tooltip = null;
        for (int i = 0; i < offers.size(); i++) {
            Order offer = offers.get(i);
            int y = listTop() + i * row;
            boolean available = !data.hasTakenOrder(offer.id()) && data.getLevel() >= offer.minLevel();
            drawRow(graphics, offer, leftX(), y, column, available ? 0xFFFFFF : 0x8A8A8A, rewardLine(offer), 50);
            if (isOver(mouseX, mouseY, leftX(), y, column - 52)) tooltip = details(offer, false);
        }
        if (data.getOrders().isEmpty()) {
            graphics.drawString(font, Component.translatable("screen.heroicpaths.order_none"), rightX() + 4, listTop() + 4, 0x8A8A8A, true);
        }
        for (int i = 0; i < data.getOrders().size(); i++) {
            Order order = data.getOrders().get(i);
            int y = listTop() + i * row;
            int progress = progressOf(order);
            MutableComponent line = Component.translatable(order.type() == OrderType.DELIVERY ? "screen.heroicpaths.order_have" : "screen.heroicpaths.order_progress",
                    progress, order.amount()).withStyle(ready(order) ? ChatFormatting.GREEN : ChatFormatting.GRAY);
            drawRow(graphics, order, rightX(), y, column, ready(order) ? 0x7FFF7F : 0xFFFFFF, line, 74);
            int barLeft = rightX() + 22;
            int barRight = rightX() + column - 78;
            if (barRight > barLeft + 10) {
                graphics.fill(barLeft, y + row - 5, barRight, y + row - 3, 0xFF1A1A1A);
                graphics.fill(barLeft, y + row - 5, barLeft + (barRight - barLeft) * progress / Math.max(1, order.amount()), y + row - 3, 0xFF7FD23C);
            }
            if (isOver(mouseX, mouseY, rightX(), y, column - 76)) tooltip = details(order, true);
        }
        if (tooltip != null) graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    private boolean isOver(int mouseX, int mouseY, int x, int y, int w) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + row - 2;
    }

    private void drawRow(GuiGraphics graphics, Order order, int x, int y, int column, int titleColor, Component second, int buttonsWidth) {
        graphics.fill(x, y, x + column, y + row - 2, 0x70000000);
        graphics.renderItem(order.iconStack(), x + 3, y + (row - 18) / 2);
        int textWidth = column - 24;
        String title = order.title().getString();
        if (font.width(title) > textWidth) title = font.plainSubstrByWidth(title, textWidth - font.width("…")) + "…";
        graphics.drawString(font, title, x + 22, y + 2, titleColor, true);
        String secondText = second.getString();
        int secondWidth = column - 24 - buttonsWidth;
        if (font.width(secondText) > secondWidth) secondText = font.plainSubstrByWidth(secondText, secondWidth - font.width("…")) + "…";
        graphics.drawString(font, Component.literal(secondText).withStyle(second.getStyle()), x + 22, y + row - 15, 0xBBBBBB, true);
    }

    private static Component rewardLine(Order order) {
        return Component.translatable("screen.heroicpaths.order_reward_short", order.minLevel(), order.rewardXp(), order.rewardEmeralds())
                .withStyle(ChatFormatting.GRAY);
    }

    private static List<Component> details(Order order, boolean mine) {
        List<Component> lines = new ArrayList<>();
        lines.add(order.title().withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("screen.heroicpaths.order_tier", order.tierName(), order.minLevel()));
        lines.add(Component.translatable("order_type.heroicpaths." + order.type().id() + ".desc").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("screen.heroicpaths.order_reward", order.rewardXp(), order.rewardEmeralds()).withStyle(ChatFormatting.GREEN));
        ItemStack bonus = order.bonus();
        if (!bonus.isEmpty()) {
            lines.add(Component.translatable("screen.heroicpaths.order_bonus", bonus.getHoverName(), bonus.getCount()).withStyle(ChatFormatting.AQUA));
        }
        if (mine && order.type() != OrderType.DELIVERY) {
            lines.add(Component.translatable("screen.heroicpaths.order_party_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
