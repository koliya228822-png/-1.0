package com.heroicpaths.client;

import com.heroicpaths.item.SkillTable;
import com.heroicpaths.network.SkillTablePayload;
import com.heroicpaths.rpg.ability.AbilityDef;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Окно стола зачарований: книга умения за книгу, лазурит и уровни, а на 2-м уровне стола
 * (6 полок умений вокруг) — книги уникальных умений подкласса.
 */
public class SkillTableScreen extends Screen {
    private final BlockPos pos;
    private final int shelves;

    public SkillTableScreen(BlockPos pos, int shelves) {
        super(Component.translatable("screen.heroicpaths.skill_table"));
        this.pos = pos;
        this.shelves = shelves;
    }

    private boolean tier2() {
        return shelves >= SkillTable.SHELVES_FOR_TIER_2;
    }

    @Override
    protected void init() {
        int x = width / 2 - 110, y = 60;
        addRenderableWidget(Button.builder(Component.translatable("screen.heroicpaths.skill_table_book"), b -> send(""))
                .bounds(x, y, 220, 20)
                .tooltip(Tooltip.create(Component.translatable("screen.heroicpaths.skill_table_book_cost", SkillTable.BOOK_LAPIS, SkillTable.BOOK_LEVELS)))
                .build());
        if (tier2()) {
            List<AbilityDef> uniques = SkillTable.uniquePool(minecraft.player);
            int row = 0;
            for (AbilityDef def : uniques) {
                int col = row % 2;
                Button button = Button.builder(def.name(), b -> send(def.id()))
                        .bounds(x + col * 112, y + 48 + (row / 2) * 22, 108, 20)
                        .tooltip(Tooltip.create(def.description().copy().append("\n\n").append(Component.translatable(
                                "screen.heroicpaths.skill_table_unique_cost", SkillTable.UNIQUE_LAPIS, SkillTable.UNIQUE_DIAMONDS, SkillTable.UNIQUE_LEVELS)
                                .withStyle(ChatFormatting.GOLD))))
                        .build();
                addRenderableWidget(button);
                row++;
            }
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    private void send(String ability) {
        PacketDistributor.sendToServer(new SkillTablePayload(pos, ability, 0));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        graphics.drawCenteredString(font, title, center, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.heroicpaths.skill_table_shelves", shelves, SkillTable.SHELVES_FOR_TIER_2,
                tier2() ? 2 : 1), center, 28, tier2() ? 0xD0A0FF : 0xA0A0A0);
        var player = minecraft.player;
        graphics.drawCenteredString(font, Component.translatable("screen.heroicpaths.skill_table_have",
                SkillTable.count(player, Items.BOOK), SkillTable.count(player, Items.LAPIS_LAZULI), SkillTable.count(player, Items.DIAMOND),
                player.experienceLevel), center, 42, 0xC0C0C0);
        if (tier2()) {
            graphics.drawCenteredString(font, Component.translatable("screen.heroicpaths.skill_table_uniques"), center, 96, 0xFFD070);
        } else {
            graphics.drawCenteredString(font, Component.translatable("screen.heroicpaths.skill_table_need_shelves", SkillTable.SHELVES_FOR_TIER_2),
                    center, 96, 0x909090);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
