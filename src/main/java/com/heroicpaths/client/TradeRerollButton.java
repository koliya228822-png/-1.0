package com.heroicpaths.client;

import com.heroicpaths.network.TradeRerollPayload;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Кнопка «Обновить товары» в окне торговли с жителем. Цену следующего реролла присылает сервер, когда окно открылось
 * (и после каждого реролла); пока цены нет или у жителя реролла не бывает (банкир, книжник, скупщик…), кнопка скрыта.
 */
public final class TradeRerollButton {
    private static int price = -1;
    @Nullable
    private static Button button;

    private TradeRerollButton() {}

    public static void setPrice(int value) {
        price = value;
    }

    public static void onOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof MerchantScreen) price = -1;
    }

    public static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof MerchantScreen screen)) return;
        int width = 120;
        int x = screen.getGuiLeft() + screen.getXSize() - width;
        int y = screen.getGuiTop() >= 22 ? screen.getGuiTop() - 22 : screen.getGuiTop() + screen.getYSize() + 2;
        button = Button.builder(label(), b -> PacketDistributor.sendToServer(TradeRerollPayload.REQUEST))
                .bounds(x, y, width, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.heroicpaths.reroll.tooltip")))
                .build();
        button.visible = price >= 0;
        event.addListener(button);
    }

    public static void onRender(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof MerchantScreen screen)) return;
        // После реролла товаров может стать меньше: выбранный товар не должен оказаться за концом списка.
        try {
            if (SHOP_ITEM.getInt(screen) >= screen.getMenu().getOffers().size()) SHOP_ITEM.setInt(screen, 0);
        } catch (IllegalAccessException ignored) {
        }
        if (button == null) return;
        button.visible = price >= 0;
        button.setMessage(label());
    }

    private static final java.lang.reflect.Field SHOP_ITEM =
            net.neoforged.fml.util.ObfuscationReflectionHelper.findField(MerchantScreen.class, "shopItem");

    private static Component label() {
        return Component.translatable("screen.heroicpaths.reroll", Math.max(0, price));
    }
}
