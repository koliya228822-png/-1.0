package com.heroicpaths.client;

import com.heroicpaths.dungeon.DungeonType;
import com.heroicpaths.network.TakeContractPayload;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Доска заданий гильдии: выбор данжа. Группа берётся из /party: доступ решает средний уровень группы,
 * враги получают +30% здоровья и +10% урона за каждого её участника сверх первого.
 * Данжи идут по уровню; если все не помещаются, список прокручивается колесом мыши.
 */
public class QuestBoardScreen extends Screen {
    private static final int ROW_HEIGHT = 47;
    private static int scroll;
    private final List<Button> takeButtons = new ArrayList<>();

    public QuestBoardScreen() {
        super(Component.translatable("screen.heroicpaths.quest_board"));
    }

    /** Подсказка о группе переносится по ширине окна. */
    private List<net.minecraft.util.FormattedCharSequence> hintLines() {
        return font.split(Component.translatable("screen.heroicpaths.party_hint"), Math.max(100, Math.min(430, width - 20)));
    }

    /** Заголовок, состав группы, подсказка и активный контракт, под ними — список данжей. */
    private int rowsTop() {
        return 30 + hintLines().size() * 10 + 14;
    }

    private int visibleRows() {
        return Math.max(1, (height - 32 - rowsTop()) / ROW_HEIGHT);
    }

    private int maxScroll() {
        return Math.max(0, DungeonType.values().length - visibleRows());
    }

    private double averageLevel() {
        return ClientHooks.partyAverageLevel(RpgManager.get(minecraft.player).getLevel());
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        scroll = Mth.clamp(scroll, 0, maxScroll());
        takeButtons.clear();
        DungeonType[] types = DungeonType.values();
        for (int row = 0; row < visibleRows() && scroll + row < types.length; row++) {
            DungeonType type = types[scroll + row];
            Button take = Button.builder(Component.translatable("screen.heroicpaths.take_contract"), b -> {
                        PacketDistributor.sendToServer(new TakeContractPayload(type.id()));
                        onClose();
                    })
                    .bounds(centerX + 100, rowsTop() + row * ROW_HEIGHT + 6, 110, 20)
                    .tooltip(Tooltip.create(type.description()))
                    .build();
            takeButtons.add(take);
            addRenderableWidget(take);
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(centerX - 50, height - 28, 100, 20).build());
    }

    @Override
    public void tick() {
        // Состав группы может измениться, пока экран открыт.
        DungeonType[] types = DungeonType.values();
        for (int row = 0; row < takeButtons.size() && scroll + row < types.length; row++) {
            takeButtons.get(row).active = averageLevel() + 1e-6 >= types[scroll + row].minLevel();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int next = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
        if (next != scroll) {
            scroll = next;
            rebuildWidgets();
        }
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int centerX = width / 2;
        int partySize = ClientHooks.partySize();
        double average = averageLevel();
        graphics.drawCenteredString(font, title, centerX, 6, 0xFFD27F);
        graphics.drawCenteredString(font, Component.translatable("screen.heroicpaths.party_info", partySize,
                String.format(Locale.ROOT, "%.1f", average)), centerX, 18, 0xFFFFFF);
        List<net.minecraft.util.FormattedCharSequence> hint = hintLines();
        for (int i = 0; i < hint.size(); i++) graphics.drawCenteredString(font, hint.get(i), centerX, 30 + i * 10, 0xAAAAAA);

        RpgData data = RpgManager.get(minecraft.player);
        Component contract = data.getContractDungeon().isEmpty()
                ? Component.translatable("screen.heroicpaths.no_contract")
                : Component.translatable("hud.heroicpaths.contract", contractName(data), data.getContractParty());
        graphics.drawCenteredString(font, contract, centerX, 30 + hint.size() * 10, 0xD0A0FF);
        DungeonType[] types = DungeonType.values();
        int left = centerX - 210;
        for (int row = 0; row < visibleRows() && scroll + row < types.length; row++) {
            DungeonType type = types[scroll + row];
            int y = rowsTop() + row * ROW_HEIGHT;
            graphics.fill(left - 4, y - 2, centerX + 214, y + ROW_HEIGHT - 3, 0x60000000);
            boolean allowed = average + 1e-6 >= type.minLevel();
            graphics.drawString(font, type.displayName().withStyle(allowed ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY), left, y + 2, 0xFFFFFF, true);
            graphics.drawString(font, Component.translatable("screen.heroicpaths.min_level", type.minLevel())
                    .withStyle(allowed ? ChatFormatting.GRAY : ChatFormatting.RED), left + 130, y + 2, 0xFFFFFF, true);
            String health = String.format(Locale.ROOT, "%.2f", type.healthMultiplier(partySize));
            String damage = String.format(Locale.ROOT, "%.2f", type.damageMultiplier(partySize));
            graphics.drawString(font, Component.translatable("screen.heroicpaths.enemy_stats", health, damage), left, y + 13, 0xFF8080, true);
            graphics.drawString(font, Component.translatable("screen.heroicpaths.rewards", type.rewardXp(), type.rewardEmeralds(), type.rewardTomes()),
                    left, y + 24, 0x80FF80, true);
            if (type.requiresScroll()) {
                boolean hasScroll = minecraft.player.getInventory().contains(new ItemStack(type.scroll()));
                graphics.drawString(font, Component.translatable("screen.heroicpaths.needs_scroll", new ItemStack(type.scroll()).getHoverName())
                        .withStyle(hasScroll ? ChatFormatting.GREEN : ChatFormatting.YELLOW), left, y + 35, 0xFFFFFF, true);
            }
        }
        if (maxScroll() > 0) {
            int trackTop = rowsTop() - 2;
            int trackHeight = visibleRows() * ROW_HEIGHT - 4;
            int thumb = Math.max(10, trackHeight * visibleRows() / types.length);
            int thumbY = trackTop + (trackHeight - thumb) * scroll / maxScroll();
            int barX = Math.min(centerX + 217, width - 4);
            graphics.fill(barX, trackTop, barX + 3, trackTop + trackHeight, 0x50000000);
            graphics.fill(barX, thumbY, barX + 3, thumbY + thumb, 0xFFAAAAAA);
        }
    }

    private static Component contractName(RpgData data) {
        DungeonType type = DungeonType.byId(data.getContractDungeon());
        return type == null ? Component.literal("?") : type.displayName();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
