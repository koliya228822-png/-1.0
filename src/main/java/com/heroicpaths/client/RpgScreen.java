package com.heroicpaths.client;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.network.ChooseClassPayload;
import com.heroicpaths.network.TreeActionPayload;
import com.heroicpaths.rpg.CoreStat;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.PlayerStats;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.Subclass;
import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import com.heroicpaths.rpg.ability.WeaponReq;
import com.heroicpaths.rpg.stat.Keystone;
import com.heroicpaths.rpg.stat.Stat;
import com.heroicpaths.rpg.stat.StatBlock;
import com.heroicpaths.rpg.tree.NodeType;
import com.heroicpaths.rpg.tree.PassiveNode;
import com.heroicpaths.rpg.tree.PassiveTree;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Меню «Пути героев»: выбор класса и подкласса, пассивное дерево, умения (ранги и книги)
 * и характеристики (вложение очков).
 */
public class RpgScreen extends Screen {
    private enum Tab { TREE, ABILITIES, STATS, CRAFTS, BOOK }

    private static final ResourceLocation NODE_SMALL = HeroicPaths.id("textures/gui/node_small.png");
    private static final ResourceLocation NODE_NOTABLE = HeroicPaths.id("textures/gui/node_notable.png");
    private static final ResourceLocation NODE_KEYSTONE = HeroicPaths.id("textures/gui/node_keystone.png");
    private static final ResourceLocation NODE_START = HeroicPaths.id("textures/gui/node_start.png");
    private static final int CONTENT_TOP = 44;

    private static Tab lastTab = Tab.TREE;
    private static float panX = Float.NaN;
    private static float panY;
    private static float zoom = 0.6f;

    private Tab tab = lastTab;
    private boolean dragging;
    @Nullable
    private String selectedAbility;
    /** Класс, выбранный на первом шаге выбора: на втором шаге выбирается его подкласс. */
    @Nullable
    private PlayerClass pendingClass;
    private RpgData shownData;
    private int shownVersion = -1;

    public RpgScreen() {
        super(Component.translatable("screen.heroicpaths.skills"));
    }

    private RpgData data() {
        return RpgManager.get(minecraft.player);
    }

    // ================================================================ виджеты

    @Override
    protected void init() {
        RpgData data = data();
        shownData = data;
        shownVersion = data.getSyncVersion();
        int centerX = width / 2;

        if (data.getPlayerClass() == PlayerClass.NONE) {
            initClassChoice();
            return;
        }
        if (data.getSubclass() == null) {
            initSubclassChoice(data.getPlayerClass(), true);
            return;
        }
        if (Float.isNaN(panX)) centerTreeOnClass(data.getPlayerClass());

        Tab[] tabs = Tab.values();
        int tabWidth = Math.max(60, Math.min(110, (width - 16) / tabs.length - 4));
        int tabsX = centerX - (tabs.length * (tabWidth + 4) - 4) / 2;
        for (int i = 0; i < tabs.length; i++) {
            Tab target = tabs[i];
            Button button = Button.builder(Component.translatable("screen.heroicpaths.tab." + target.name().toLowerCase(Locale.ROOT)),
                    b -> switchTab(target)).bounds(tabsX + i * (tabWidth + 4), 4, tabWidth, 20).build();
            button.active = target != tab;
            addRenderableWidget(button);
        }

        switch (tab) {
            case TREE -> addRenderableWidget(Button.builder(Component.translatable("screen.heroicpaths.center_view"),
                    b -> centerTreeOnClass(data.getPlayerClass())).bounds(6, height - 26, 100, 20).build());
            case ABILITIES -> initAbilitiesTab(data);
            case STATS -> initStatsTab(data);
            case CRAFTS -> { }
            case BOOK -> initBookTab();
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width - 106, height - 26, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.heroicpaths.settings_button"),
                b -> minecraft.setScreen(new SettingsScreen(this))).bounds(width - 212, height - 26, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.heroicpaths.accessories_button"),
                b -> PacketDistributor.sendToServer(com.heroicpaths.network.OpenAccessoriesPayload.INSTANCE))
                .bounds(width - 318, height - 26, 100, 20).build());
    }

    private void switchTab(Tab target) {
        tab = target;
        lastTab = target;
        rebuildWidgets();
    }

    /** Шаг 1: класс. */
    private void initClassChoice() {
        if (pendingClass != null) {
            initSubclassChoice(pendingClass, false);
            return;
        }
        List<PlayerClass> classes = PlayerClass.playable();
        int buttonWidth = rowButtonWidth(classes.size(), 100);
        int startX = rowStart(classes.size(), buttonWidth);
        for (int i = 0; i < classes.size(); i++) {
            PlayerClass playerClass = classes.get(i);
            addRenderableWidget(Button.builder(playerClass.displayName().withColor(playerClass.color()), b -> {
                        pendingClass = playerClass;
                        rebuildWidgets();
                    })
                    .bounds(startX + i * (buttonWidth + ROW_GAP), height / 2 - 30, buttonWidth, 20)
                    .tooltip(Tooltip.create(playerClass.description()))
                    .build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 - 50, height - 28, 100, 20).build());
    }

    /** Шаг 2: подкласс. Для персонажа, перенесённого со старой версии, класс уже выбран. */
    private void initSubclassChoice(PlayerClass playerClass, boolean classFixed) {
        List<Subclass> subclasses = Subclass.forClass(playerClass);
        int buttonWidth = rowButtonWidth(subclasses.size(), 120);
        int startX = rowStart(subclasses.size(), buttonWidth);
        for (int i = 0; i < subclasses.size(); i++) {
            Subclass subclass = subclasses.get(i);
            addRenderableWidget(Button.builder(subclass.displayName(), b -> {
                        if (classFixed) PacketDistributor.sendToServer(TreeActionPayload.chooseSubclass(subclass.id()));
                        else PacketDistributor.sendToServer(new ChooseClassPayload(playerClass.ordinal(), subclass.id()));
                    })
                    .bounds(startX + i * (buttonWidth + ROW_GAP), height / 2 - 10, buttonWidth, 20)
                    .tooltip(Tooltip.create(subclassTooltip(subclass)))
                    .build());
        }
        if (!classFixed) {
            addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> {
                pendingClass = null;
                rebuildWidgets();
            }).bounds(width / 2 - 104, height - 28, 100, 20).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                .bounds(width / 2 + (classFixed ? -50 : 4), height - 28, 100, 20).build());
    }

    private static final int ROW_GAP = 6;

    /** Ширина кнопки в ряду из count кнопок, чтобы ряд поместился в окно. */
    private int rowButtonWidth(int count, int preferred) {
        return Math.max(40, Math.min(preferred, (width - 16) / count - ROW_GAP));
    }

    private int rowStart(int count, int buttonWidth) {
        return width / 2 - (count * (buttonWidth + ROW_GAP) - ROW_GAP) / 2;
    }

    /** Левая колонка вкладок «Умения» и «Характеристики». */
    private int leftColumn() {
        return Math.max(6, width / 2 - 214);
    }

    /** Многострочный текст по центру; возвращает число строк. */
    private int drawWrapped(GuiGraphics graphics, Component text, int y, int color) {
        List<FormattedCharSequence> lines = font.split(text, Math.max(100, width - 30));
        for (int i = 0; i < lines.size(); i++) graphics.drawCenteredString(font, lines.get(i), width / 2, y + i * 10, color);
        return lines.size();
    }

    private static Component subclassTooltip(Subclass subclass) {
        MutableComponent text = subclass.displayName().withStyle(ChatFormatting.GOLD);
        text.append("\n").append(subclass.description().withStyle(ChatFormatting.GRAY));
        text.append("\n").append(Component.translatable("screen.heroicpaths.starting_abilities").withStyle(ChatFormatting.AQUA));
        for (String id : subclass.startingAbilities()) {
            AbilityDef def = AbilityRegistry.get(id);
            if (def != null) text.append("\n  ").append(def.name().withStyle(ChatFormatting.WHITE));
        }
        text.append("\n").append(Component.translatable("screen.heroicpaths.book_abilities").withStyle(ChatFormatting.DARK_AQUA));
        for (String id : subclass.learnableAbilities()) {
            AbilityDef def = AbilityRegistry.get(id);
            if (def != null) text.append("\n  ").append(def.name().withStyle(ChatFormatting.GRAY));
        }
        text.append("\n").append(Component.translatable("screen.heroicpaths.subclass_bonus").withStyle(ChatFormatting.BLUE));
        for (Component line : subclass.passive().describe()) text.append("\n  ").append(line);
        text.append("\n").append(Component.translatable("screen.heroicpaths.subclass_warning").withStyle(ChatFormatting.RED));
        return text;
    }

    private void initAbilitiesTab(RpgData data) {
        int centerX = width / 2;
        List<AbilityDef> abilities = AbilityRegistry.available(data.getSubclass());
        int points = data.abilityPoints();
        int left = leftColumn();
        int listWidth = Math.min(176, centerX - left - 30);
        int top = CONTENT_TOP + 14;
        // Список подстраивается под высоту окна, чтобы не наезжать на подсказку внизу.
        int rowHeight = Mth.clamp((height - 56 - top) / Math.max(1, abilities.size()), 14, 22);
        for (int i = 0; i < abilities.size(); i++) {
            AbilityDef def = abilities.get(i);
            boolean known = AbilityRegistry.isLearned(data, def);
            int y = top + i * rowHeight;
            MutableComponent label;
            if (!known) {
                label = Component.translatable("screen.heroicpaths.book_ability", def.name(), def.unlockLevel()).withStyle(ChatFormatting.DARK_GRAY);
            } else {
                label = def.name().copy().append(" ").append(Component.translatable("screen.heroicpaths.rank", data.getRank(def.id())));
                if (def.id().equals(selectedAbility)) label = Component.literal("» ").append(label).append(" «").withStyle(ChatFormatting.YELLOW);
            }
            Button button = Button.builder(label, b -> {
                        selectedAbility = def.id();
                        rebuildWidgets();
                    })
                    .bounds(left, y, listWidth, rowHeight - 2)
                    .tooltip(Tooltip.create(abilityTooltip(data, def, known))).build();
            button.active = known;
            addRenderableWidget(button);
            if (known && data.getRank(def.id()) < AbilityDef.MAX_RANK) {
                Button rank = Button.builder(Component.literal("+"), b -> PacketDistributor.sendToServer(TreeActionPayload.rankUp(def.id())))
                        .bounds(left + listWidth + 2, y, 20, rowHeight - 2)
                        .tooltip(Tooltip.create(Component.translatable("screen.heroicpaths.rank_up_hint", data.getRank(def.id()) + 1))).build();
                rank.active = points > 0;
                addRenderableWidget(rank);
            }
        }
        for (int slot = 0; slot < RpgData.ABILITY_SLOTS; slot++) {
            int target = slot;
            AbilityDef bound = AbilityRegistry.get(data.getSlot(slot));
            MutableComponent label = Component.literal("[")
                    .append(ModKeys.ability(slot).getTranslatedKeyMessage()).append("] ")
                    .append(bound != null ? bound.name() : Component.translatable("hud.heroicpaths.empty_slot"));
            Button button = Button.builder(label, b -> {
                        if (selectedAbility != null) PacketDistributor.sendToServer(TreeActionPayload.bind(selectedAbility, target));
                    })
                    .bounds(centerX + 6, top + slot * 26, Math.min(180, width - centerX - 12), 20)
                    .tooltip(Tooltip.create(Component.translatable("screen.heroicpaths.slot_hint"))).build();
            addRenderableWidget(button);
        }
    }

    private Component abilityTooltip(RpgData data, AbilityDef def, boolean known) {
        MutableComponent text = def.name().copy().withStyle(ChatFormatting.GOLD);
        text.append("\n").append(def.description().withStyle(ChatFormatting.GRAY));
        boolean blood = data.getSubclass() != null && data.getSubclass().usesHealth();
        if (blood && def.cost() > 0) {
            text.append("\n").append(Component.translatable("screen.heroicpaths.cost_health",
                    String.format(Locale.ROOT, "%.1f", PlayerStats.healthCost(RpgManager.cost(minecraft.player, def)))).withStyle(ChatFormatting.RED));
        } else {
            text.append("\n").append(Component.translatable("screen.heroicpaths.cost",
                    RpgManager.cost(minecraft.player, def), data.getPlayerClass().resourceName()).withStyle(ChatFormatting.AQUA));
        }
        text.append("\n").append(Component.translatable("screen.heroicpaths.cooldown",
                String.format(Locale.ROOT, "%.1f", RpgManager.cooldown(minecraft.player, def) / 20f)).withStyle(ChatFormatting.AQUA));
        if (def.castTicks() > 0) {
            text.append("\n").append(Component.translatable("screen.heroicpaths.cast_time",
                    String.format(Locale.ROOT, "%.1f", def.castTicks() / 20f)).withStyle(ChatFormatting.GOLD));
        }
        if (def.weapon() != WeaponReq.NONE) {
            text.append("\n").append(Component.translatable("screen.heroicpaths.weapon", def.weapon().displayName())
                    .withStyle(def.weapon().isMet(minecraft.player) ? ChatFormatting.DARK_AQUA : ChatFormatting.RED));
        }
        Component armorBlock = com.heroicpaths.rpg.ArmorProficiency.blockReason(minecraft.player, def);
        if (armorBlock != null) text.append("\n").append(armorBlock.copy().withStyle(ChatFormatting.RED));
        text.append("\n").append(Component.translatable("screen.heroicpaths.element",
                Component.translatable("element.heroicpaths." + def.element().name().toLowerCase(Locale.ROOT))).withStyle(ChatFormatting.DARK_AQUA));
        if (!known) {
            text.append("\n").append(Component.translatable("screen.heroicpaths.needs_book", def.unlockLevel()).withStyle(ChatFormatting.YELLOW));
        }
        return text;
    }

    private void initStatsTab(RpgData data) {
        int x = leftColumn();
        int points = data.statPoints();
        for (CoreStat stat : CoreStat.values()) {
            int y = CONTENT_TOP + 20 + stat.ordinal() * 22;
            Button plus = Button.builder(Component.literal("+"), b -> PacketDistributor.sendToServer(TreeActionPayload.addStat(stat.ordinal())))
                    .bounds(x + 120, y, 20, 20)
                    .tooltip(Tooltip.create(stat.description())).build();
            plus.active = points > 0 && data.getStat(stat) < CoreStat.MAX;
            addRenderableWidget(plus);
        }
        // Перевод ванильного опыта: в опыт персонажа или любого ремесла.
        int y = convertTop();
        boolean hasXp = minecraft.player != null && com.heroicpaths.rpg.Experience.totalPoints(minecraft.player) > 0;
        Button character = Button.builder(Component.translatable("screen.heroicpaths.convert.character"),
                        b -> PacketDistributor.sendToServer(TreeActionPayload.convertXp("")))
                .bounds(x, y + 12, 140, 18).tooltip(Tooltip.create(Component.translatable("screen.heroicpaths.convert.character_hint"))).build();
        character.active = hasXp;
        addRenderableWidget(character);
        int i = 0;
        for (com.heroicpaths.craft.Profession profession : com.heroicpaths.craft.Profession.values()) {
            Button craft = Button.builder(profession.displayName(), b -> PacketDistributor.sendToServer(TreeActionPayload.convertXp(profession.id())))
                    .bounds(x + (i % 2) * 71, y + 32 + (i / 2) * 20, 69, 18)
                    .tooltip(Tooltip.create(Component.translatable("screen.heroicpaths.convert.craft_hint",
                            com.heroicpaths.rpg.Experience.VANILLA_PER_CRAFT_XP))).build();
            craft.active = hasXp;
            addRenderableWidget(craft);
            i++;
        }
    }

    private int convertTop() {
        return CONTENT_TOP + 20 + CoreStat.values().length * 22 + 16;
    }

    private static Component joinLines(List<Component> lines) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) result.append("\n");
            result.append(lines.get(i));
        }
        return result;
    }

    @Override
    public void tick() {
        if (minecraft.player == null) {
            onClose();
            return;
        }
        RpgData data = data();
        if (data != shownData || data.getSyncVersion() != shownVersion) rebuildWidgets();
    }

    // ================================================================ отрисовка

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        RpgData data = data();
        if (data.getPlayerClass() == PlayerClass.NONE) {
            if (pendingClass == null) renderClassChoice(graphics);
            else renderSubclassChoice(graphics, pendingClass);
            return;
        }
        if (data.getSubclass() == null) {
            renderSubclassChoice(graphics, data.getPlayerClass());
            return;
        }
        renderHeader(graphics, data);
        switch (tab) {
            case TREE -> renderTree(graphics, data, mouseX, mouseY);
            case ABILITIES -> renderAbilitiesHeader(graphics, data);
            case STATS -> renderStats(graphics, data);
            case CRAFTS -> renderCrafts(graphics, data, mouseX, mouseY);
            case BOOK -> renderBook(graphics, mouseX, mouseY);
        }
    }

    // ---------------------------------------------------------------- книга крафтов

    private final CraftBookView book = new CraftBookView();

    /** Кнопки разделов книги: станки и верстак — в ряд под вкладками. */
    private void initBookTab() {
        int count = CraftBookView.categories();
        int buttonWidth = Math.max(40, Math.min(90, (width - 16) / count - 2));
        int startX = width / 2 - (count * (buttonWidth + 2) - 2) / 2;
        for (int i = 0; i < count; i++) {
            int index = i;
            Button button = Button.builder(CraftBookView.categoryName(i), b -> {
                book.select(index);
                rebuildWidgets();
            }).bounds(startX + i * (buttonWidth + 2), CONTENT_TOP, buttonWidth, 16).build();
            button.active = i != CraftBookView.category();
            addRenderableWidget(button);
        }
    }

    private int bookLeft() {
        return Math.max(8, width / 2 - 230);
    }

    private int bookTop() {
        return CONTENT_TOP + 20;
    }

    private void renderBook(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = bookLeft();
        int right = width - left - 8;
        ItemStack hovered = book.render(graphics, font, minecraft, left, bookTop(), right, height - 32, mouseX, mouseY);
        if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
    }

    // ---------------------------------------------------------------- ремёсла

    private static final String[] GRADE_RANGE = {"", "I", "I–II", "I–III", "I–VI", "I–VI"};

    /**
     * Три ремесла: ступень, опыт до следующей, какой трактат нужен, открытые грейды и шансы редкости.
     * Наведение на название ремесла показывает его станки.
     */
    private void renderCrafts(GuiGraphics graphics, RpgData data, int mouseX, int mouseY) {
        com.heroicpaths.craft.Professions professions = data.professions();
        int x = leftColumn();
        int columnWidth = Math.min(428, width - 2 * x);
        int y = CONTENT_TOP + 6;
        Component hoveredStations = null;
        com.heroicpaths.craft.Profession mastery = professions.mastery();
        Component masteryLine = mastery == null
                ? Component.translatable("screen.heroicpaths.craft.no_mastery")
                : Component.translatable("screen.heroicpaths.craft.mastery", mastery.displayName());
        for (FormattedCharSequence line : font.split(masteryLine, columnWidth)) {
            graphics.drawString(font, line, x, y, 0xD0D0D0, true);
            y += 10;
        }
        y += 6;
        for (com.heroicpaths.craft.Profession profession : com.heroicpaths.craft.Profession.values()) {
            int rank = professions.rank(profession);
            int xp = professions.xp(profession);
            MutableComponent title = professions.describe(profession);
            if (profession == mastery) title.append(Component.literal(" ★").withStyle(ChatFormatting.GOLD));
            graphics.drawString(font, title, x, y, 0xFFFFFF, true);
            if (mouseX >= x && mouseX < x + font.width(title) && mouseY >= y - 1 && mouseY < y + 9) {
                hoveredStations = Component.translatable("screen.heroicpaths.craft.stations." + profession.id());
            }
            y += 11;

            boolean capped = rank >= professions.maxRank(profession);
            int barWidth = Math.min(200, columnWidth);
            graphics.fill(x, y, x + barWidth, y + 3, 0xFF202020);
            if (capped) {
                graphics.fill(x, y, x + barWidth, y + 3, 0xFF806020);
            } else {
                int from = com.heroicpaths.craft.Professions.xpForRank(rank);
                int to = com.heroicpaths.craft.Professions.xpForRank(rank + 1);
                float part = Mth.clamp((xp - from) / (float) Math.max(1, to - from), 0f, 1f);
                graphics.fill(x, y, x + (int) (barWidth * part), y + 3, xp >= to ? 0xFFE0B040 : 0xFF50A0E0);
            }
            y += 6;

            Component next;
            if (rank >= com.heroicpaths.craft.Professions.MAX_RANK) {
                next = Component.translatable("screen.heroicpaths.craft.max");
            } else if (capped) {
                next = Component.translatable("screen.heroicpaths.craft.capped");
            } else {
                ItemStack manual = com.heroicpaths.item.ProfessionManualItem.create(profession.manual(), rank + 1);
                next = Component.translatable("screen.heroicpaths.craft.next", xp, com.heroicpaths.craft.Professions.xpForRank(rank + 1),
                        manual.getHoverName());
            }
            float[] chances = com.heroicpaths.item.ItemRarity.chances(rank);
            float rarePlus = 0;
            for (int i = com.heroicpaths.item.ItemRarity.Tier.RARE.ordinal(); i < chances.length; i++) rarePlus += chances[i];
            MutableComponent perks = Component.translatable("screen.heroicpaths.craft.perks", GRADE_RANGE[rank],
                    String.format(Locale.ROOT, "%.1f", rarePlus), String.format(Locale.ROOT, "%.1f", chances[0]));
            if (profession == com.heroicpaths.craft.Profession.RUNECRAFTER) {
                perks.append(" ").append(Component.translatable("screen.heroicpaths.craft.rune_perk", rank));
            }
            for (Component text : List.of(next, perks)) {
                for (FormattedCharSequence line : font.split(text, columnWidth - 6)) {
                    graphics.drawString(font, line, x + 6, y, 0xA0A0A0, false);
                    y += 10;
                }
            }
            y += 4;
        }
        // Подсказка — только если не налезает на кнопки внизу.
        if (y + 9 <= height - 30) graphics.drawString(font, Component.translatable("screen.heroicpaths.craft.hover"), x, y, 0x707070, false);
        if (hoveredStations != null) {
            graphics.renderTooltip(font, font.split(hoveredStations, Math.min(260, width / 2)), mouseX, mouseY);
        }
    }

    private void renderClassChoice(GuiGraphics graphics) {
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, 12, 0xFFD27F);
        graphics.drawCenteredString(font, Component.translatable("screen.heroicpaths.choose_class"), centerX, height / 2 - 60, 0xFFFFFF);
        List<PlayerClass> classes = PlayerClass.playable();
        int buttonWidth = rowButtonWidth(classes.size(), 100);
        int startX = rowStart(classes.size(), buttonWidth);
        for (int i = 0; i < classes.size(); i++) {
            List<FormattedCharSequence> role = font.split(classes.get(i).role(), buttonWidth + ROW_GAP);
            for (int line = 0; line < Math.min(2, role.size()); line++) {
                graphics.drawCenteredString(font, role.get(line), startX + i * (buttonWidth + ROW_GAP) + buttonWidth / 2,
                        height / 2 - 4 + line * 10, 0xAAAAAA);
            }
        }
        drawWrapped(graphics, Component.translatable("screen.heroicpaths.choose_class_hint"), height / 2 + 30, 0x888888);
    }

    private void renderSubclassChoice(GuiGraphics graphics, PlayerClass playerClass) {
        int centerX = width / 2;
        graphics.drawCenteredString(font, title, centerX, 12, 0xFFD27F);
        graphics.drawCenteredString(font, playerClass.displayName().withColor(playerClass.color()), centerX, height / 2 - 70, 0xFFFFFF);
        List<FormattedCharSequence> description = font.split(playerClass.description(), Math.min(420, width - 40));
        for (int i = 0; i < description.size(); i++) {
            graphics.drawCenteredString(font, description.get(i), centerX, height / 2 - 56 + i * 10, 0xAAAAAA);
        }
        graphics.drawCenteredString(font, Component.translatable("screen.heroicpaths.choose_subclass"), centerX, height / 2 - 24, 0xFFD27F);
        int hintLines = drawWrapped(graphics, Component.translatable("screen.heroicpaths.choose_subclass_hint"), height / 2 + 20, 0x888888);
        drawWrapped(graphics, traitLine(playerClass), height / 2 + 26 + hintLines * 10, 0xFFFFFF);
    }

    /** «Особенность класса — Боевая ярость: …» */
    private static Component traitLine(PlayerClass playerClass) {
        return Component.translatable("screen.heroicpaths.class_trait", playerClass.traitName().withStyle(ChatFormatting.GOLD),
                playerClass.traitDescription().withStyle(ChatFormatting.GRAY));
    }

    private void renderHeader(GuiGraphics graphics, RpgData data) {
        PlayerClass playerClass = data.getPlayerClass();
        MutableComponent header = Component.empty().append(playerClass.displayName().withColor(playerClass.color()));
        if (data.getSubclass() != null) header.append(" · ").append(data.getSubclass().displayName().withStyle(ChatFormatting.LIGHT_PURPLE));
        header.append("  ").append(Component.translatable("screen.heroicpaths.level", data.getLevel()));
        if (data.getLevel() < RpgData.MAX_LEVEL) {
            header.append("  ").append(Component.translatable("screen.heroicpaths.xp", data.getXp(), RpgData.xpToNextLevel(data.getLevel()))
                    .withStyle(ChatFormatting.GRAY));
        }
        graphics.drawCenteredString(font, header, width / 2, 30, 0xFFFFFF);
    }

    // ---------------------------------------------------------------- дерево

    /** У каждого класса своё дерево: на экране только его узлы, вид — по центру этого дерева. */
    private static boolean shown(PassiveNode node, PlayerClass playerClass) {
        return playerClass == PlayerClass.NONE || node.region() == playerClass;
    }

    /** Направление сектора класса на карте дерева (радианы): там подписаны кольца. */
    private static double classAngle(PlayerClass playerClass) {
        int start = PassiveTree.startNode(playerClass);
        PassiveNode node = PassiveTree.node(start);
        return node == null ? -Math.PI / 2 : Math.atan2(node.y(), node.x()) + Math.toRadians(40);
    }

    private void centerTreeOnClass(PlayerClass playerClass) {
        float sx = 0, sy = 0;
        int count = 0;
        for (PassiveNode node : PassiveTree.nodes()) {
            if (playerClass == PlayerClass.NONE || node.region() != playerClass) continue;
            sx += node.x();
            sy += node.y();
            count++;
        }
        panX = count == 0 ? 0 : -sx / count * zoom;
        panY = count == 0 ? 0 : -sy / count * zoom;
    }

    private float treeCenterX() {
        return width / 2f + panX;
    }

    private float treeCenterY() {
        return (CONTENT_TOP + height - 30) / 2f + panY;
    }

    private boolean inTreeArea(double x, double y) {
        return y >= CONTENT_TOP && y <= height - 30;
    }

    private void renderTree(GuiGraphics graphics, RpgData data, int mouseX, int mouseY) {
        IntSet allocated = data.getAllocatedNodes();
        PlayerClass playerClass = data.getPlayerClass();
        int points = data.treePoints();

        graphics.enableScissor(0, CONTENT_TOP, width, height - 30);
        graphics.fill(0, CONTENT_TOP, width, height - 30, 0xA0101018);
        graphics.pose().pushPose();
        graphics.pose().translate(treeCenterX(), treeCenterY(), 0);
        graphics.pose().scale(zoom, zoom, 1);

        VertexConsumer lines = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f matrix = graphics.pose().last().pose();
        // Кольца дерева: граница каждого кольца и уровень, с которого оно открывается.
        for (int i = 0; i < PassiveTree.SEGMENT_RADII.length - 1; i++) {
            float radius = PassiveTree.SEGMENT_RADII[i];
            boolean open = data.getLevel() >= PassiveTree.SEGMENT_LEVELS[i + 1];
            int color = open ? 0x5040A060 : 0x50A04040;
            int steps = 180;
            for (int k = 0; k < steps; k++) {
                double a1 = Math.PI * 2 * k / steps, a2 = Math.PI * 2 * (k + 1) / steps;
                line(lines, matrix, (float) (Math.cos(a1) * radius), (float) (Math.sin(a1) * radius),
                        (float) (Math.cos(a2) * radius), (float) (Math.sin(a2) * radius), 1.5f, color);
            }
        }
        for (int[] edge : PassiveTree.edges()) {
            PassiveNode a = PassiveTree.node(edge[0]);
            PassiveNode b = PassiveTree.node(edge[1]);
            if (!shown(a, playerClass) || !shown(b, playerClass)) continue;
            boolean aOwned = PassiveTree.isAllocated(allocated, playerClass, a.id());
            boolean bOwned = PassiveTree.isAllocated(allocated, playerClass, b.id());
            int color = aOwned && bOwned ? 0xFFE0B040 : aOwned || bOwned ? 0xFF8A8A9A : 0xFF3A3A46;
            line(lines, matrix, a.x(), a.y(), b.x(), b.y(), aOwned && bOwned ? 4f : 3f, color);
        }
        graphics.flush();

        for (PassiveNode node : PassiveTree.nodes()) {
            if (!shown(node, playerClass)) continue;
            boolean owned = PassiveTree.isAllocated(allocated, playerClass, node.id());
            boolean available = !owned && points > 0 && PassiveTree.canAllocate(allocated, playerClass, data.getLevel(), node.id());
            boolean gated = !owned && PassiveTree.lockReason(allocated, data.getLevel(), node.id()) != null;
            int size = nodeSize(node.type());
            int color;
            if (owned) color = node.type() == NodeType.KEYSTONE ? 0xFF8C1A : 0xFFD24A;
            else if (available) color = 0xF0F0FF;
            else if (gated) color = 0x3A3036;
            else color = node.region() == PlayerClass.NONE ? 0x5A5A66 : dim(node.region().color());
            RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, 1f);
            graphics.blit(nodeTexture(node.type()), Math.round(node.x() - size / 2f), Math.round(node.y() - size / 2f),
                    0, 0, size, size, size, size);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        // Подписи колец: уровень открытия у границы, со стороны дерева своего класса.
        double labelAngle = classAngle(playerClass);
        for (int i = 0; i < PassiveTree.SEGMENT_RADII.length - 1; i++) {
            float radius = PassiveTree.SEGMENT_RADII[i] + 8;
            int level = PassiveTree.SEGMENT_LEVELS[i + 1];
            Component label = Component.translatable("screen.heroicpaths.ring_level", level);
            graphics.drawCenteredString(font, label, (int) Math.round(Math.cos(labelAngle) * radius), (int) Math.round(Math.sin(labelAngle) * radius) - 4,
                    data.getLevel() >= level ? 0x80E080 : 0xE08080);
        }
        graphics.pose().popPose();
        graphics.disableScissor();

        Component pointsText = Component.translatable("screen.heroicpaths.tree_points", points);
        graphics.drawString(font, pointsText, 8, CONTENT_TOP + 4, points > 0 ? 0x7FFF7F : 0xAAAAAA, true);
        graphics.drawString(font, Component.translatable("screen.heroicpaths.tree_hint"), 8, CONTENT_TOP + 14, 0x888888, true);

        PassiveNode hovered = inTreeArea(mouseX, mouseY) ? nodeAt(mouseX, mouseY) : null;
        if (hovered != null) {
            List<Component> tooltip = new ArrayList<>(hovered.tooltip());
            if (PassiveTree.isAllocated(allocated, playerClass, hovered.id())) {
                if (PassiveTree.canRefund(allocated, playerClass, hovered.id())) {
                    tooltip.add(Component.translatable("screen.heroicpaths.node_refund").withStyle(ChatFormatting.DARK_GRAY));
                }
            } else if (PassiveTree.lockReason(allocated, data.getLevel(), hovered.id()) instanceof PassiveTree.Lock lock) {
                tooltip.add(Component.translatable(lock.key(), lock.value()).withStyle(ChatFormatting.RED));
            } else if (PassiveTree.canAllocate(allocated, playerClass, hovered.id())) {
                tooltip.add(Component.translatable(points > 0 ? "screen.heroicpaths.node_learn" : "screen.heroicpaths.no_points")
                        .withStyle(points > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
            } else {
                tooltip.add(Component.translatable("screen.heroicpaths.node_locked").withStyle(ChatFormatting.DARK_GRAY));
            }
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private static int dim(int rgb) {
        int r = ((rgb >> 16) & 0xFF) / 3 + 30;
        int g = ((rgb >> 8) & 0xFF) / 3 + 30;
        int b = (rgb & 0xFF) / 3 + 30;
        return (r << 16) | (g << 8) | b;
    }

    private static int nodeSize(NodeType type) {
        return switch (type) {
            case SMALL -> 16;
            case NOTABLE -> 24;
            case KEYSTONE -> 32;
            case START -> 28;
        };
    }

    private static ResourceLocation nodeTexture(NodeType type) {
        return switch (type) {
            case SMALL -> NODE_SMALL;
            case NOTABLE -> NODE_NOTABLE;
            case KEYSTONE -> NODE_KEYSTONE;
            case START -> NODE_START;
        };
    }

    /** Толстая линия как четырёхугольник (с обеих сторон, чтобы не зависеть от отсечения граней). */
    private static void line(VertexConsumer consumer, Matrix4f matrix, float x1, float y1, float x2, float y2, float thickness, int argb) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length < 0.001f) return;
        float nx = -dy / length * thickness / 2;
        float ny = dx / length * thickness / 2;
        consumer.addVertex(matrix, x1 + nx, y1 + ny, 0).setColor(argb);
        consumer.addVertex(matrix, x1 - nx, y1 - ny, 0).setColor(argb);
        consumer.addVertex(matrix, x2 - nx, y2 - ny, 0).setColor(argb);
        consumer.addVertex(matrix, x2 + nx, y2 + ny, 0).setColor(argb);
        consumer.addVertex(matrix, x2 + nx, y2 + ny, 0).setColor(argb);
        consumer.addVertex(matrix, x2 - nx, y2 - ny, 0).setColor(argb);
        consumer.addVertex(matrix, x1 - nx, y1 - ny, 0).setColor(argb);
        consumer.addVertex(matrix, x1 + nx, y1 + ny, 0).setColor(argb);
    }

    @Nullable
    private PassiveNode nodeAt(double mouseX, double mouseY) {
        double tx = (mouseX - treeCenterX()) / zoom;
        double ty = (mouseY - treeCenterY()) / zoom;
        PassiveNode best = null;
        double bestDistance = Double.MAX_VALUE;
        PlayerClass playerClass = minecraft == null || minecraft.player == null ? PlayerClass.NONE : RpgManager.get(minecraft.player).getPlayerClass();
        for (PassiveNode node : PassiveTree.nodes()) {
            if (!shown(node, playerClass)) continue;
            double radius = nodeSize(node.type()) / 2.0 + 2;
            double distance = (node.x() - tx) * (node.x() - tx) + (node.y() - ty) * (node.y() - ty);
            if (distance <= radius * radius && distance < bestDistance) {
                best = node;
                bestDistance = distance;
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- прочие вкладки

    private void renderAbilitiesHeader(GuiGraphics graphics, RpgData data) {
        int centerX = width / 2;
        int points = data.abilityPoints();
        graphics.drawString(font, Component.translatable("screen.heroicpaths.ability_points", points, data.abilityPointsTotal()),
                leftColumn(), CONTENT_TOP + 2, points > 0 ? 0x7FFF7F : 0xAAAAAA, true);
        graphics.drawCenteredString(font, Component.translatable("screen.heroicpaths.slots"),
                centerX + 6 + Math.min(180, width - centerX - 12) / 2, CONTENT_TOP + 2, 0xFFD27F);
        // Подсказки — справа под слотами, где есть место при любом размере окна.
        int hintX = centerX + 6;
        int hintWidth = Math.min(180, width - centerX - 12);
        int y = CONTENT_TOP + 14 + RpgData.ABILITY_SLOTS * 26 + 4;
        Component bind = Component.translatable(selectedAbility == null ? "screen.heroicpaths.bind_hint" : "screen.heroicpaths.bind_selected");
        for (FormattedCharSequence line : font.split(bind, hintWidth)) {
            graphics.drawString(font, line, hintX, y, 0x999999, true);
            y += 10;
        }
        y += 4;
        for (FormattedCharSequence line : font.split(Component.translatable("screen.heroicpaths.books_hint"), hintWidth)) {
            if (y > height - 34) break;
            graphics.drawString(font, line, hintX, y, 0x777777, true);
            y += 10;
        }
    }

    private void renderStats(GuiGraphics graphics, RpgData data) {
        int x = leftColumn();
        int points = data.statPoints();
        graphics.drawString(font, Component.translatable("screen.heroicpaths.stat_points", points), x, CONTENT_TOP + 6,
                points > 0 ? 0x7FFF7F : 0xAAAAAA, true);
        for (CoreStat stat : CoreStat.values()) {
            int y = CONTENT_TOP + 20 + stat.ordinal() * 22;
            graphics.drawString(font, stat.displayName(), x, y + 6, 0xFFD27F, true);
            String value = String.valueOf(data.getStat(stat));
            graphics.drawString(font, value, x + 112 - font.width(value), y + 6, 0xFFFFFF, true);
        }
        graphics.drawString(font, Component.translatable("screen.heroicpaths.stat_hint"), x,
                CONTENT_TOP + 20 + CoreStat.values().length * 22 + 4, 0x777777, true);
        graphics.drawString(font, Component.translatable("screen.heroicpaths.convert.title",
                com.heroicpaths.rpg.Experience.totalPoints(minecraft.player)), x, convertTop(), 0x80FF80, true);

        StatBlock stats = PlayerStats.get(minecraft.player);
        List<Component> lines = new ArrayList<>();
        lines.add(traitLine(data.getPlayerClass()));
        lines.addAll(com.heroicpaths.rpg.ArmorProficiency.describe(minecraft.player, data));
        if (data.getSubclass() != null) {
            lines.add(Component.translatable("screen.heroicpaths.subclass_bonus").withStyle(ChatFormatting.LIGHT_PURPLE));
            for (Component line : data.getSubclass().passive().describe()) lines.add(Component.literal(" ").append(line).withStyle(ChatFormatting.DARK_PURPLE));
        }
        lines.add(Component.translatable("screen.heroicpaths.stat_health", Math.round(minecraft.player.getMaxHealth())));
        lines.add(Component.translatable("screen.heroicpaths.stat_resource", data.getPlayerClass().resourceName(),
                Math.round(PlayerStats.maxResource(minecraft.player)),
                String.format(Locale.ROOT, "%.1f", PlayerStats.resourceRegen(minecraft.player))));
        lines.add(Component.translatable("screen.heroicpaths.stat_crit",
                String.format(Locale.ROOT, "%.0f", PlayerStats.critChance(minecraft.player)),
                String.format(Locale.ROOT, "%.0f", PlayerStats.critMultiplier(minecraft.player) * 100)));
        for (Map.Entry<Stat, Float> entry : stats.asMap().entrySet()) {
            if (entry.getValue() != 0) lines.add(entry.getKey().describe(entry.getValue()).withStyle(ChatFormatting.BLUE));
        }
        Set<Keystone> keystones = data.keystones();
        for (Keystone keystone : keystones) {
            lines.add(keystone.displayName().withStyle(ChatFormatting.GOLD).append(": ").append(keystone.description().withStyle(ChatFormatting.GRAY)));
        }
        int columnX = Math.max(x + 150, width / 2 - 30);
        int columnWidth = Math.min(260, width - columnX - 6);
        // Длинные строки (особенность класса, ключевые камни) переносятся, пока хватает места.
        int y = CONTENT_TOP + 6;
        int bottom = height - 34;
        for (Component line : lines) {
            for (FormattedCharSequence part : font.split(line, columnWidth)) {
                if (y + 9 > bottom) return;
                graphics.drawString(font, part, columnX, y, 0xFFFFFF, true);
                y += 10;
            }
            y += 1;
        }
    }

    // ================================================================ мышь

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        RpgData data = data();
        if (tab != Tab.TREE || data.getPlayerClass() == PlayerClass.NONE || data.getSubclass() == null || !inTreeArea(mouseX, mouseY)) return false;
        PassiveNode node = nodeAt(mouseX, mouseY);
        if (node != null) {
            if (button == 0) PacketDistributor.sendToServer(TreeActionPayload.allocate(node.id()));
            else if (button == 1) PacketDistributor.sendToServer(TreeActionPayload.refund(node.id()));
            return true;
        }
        dragging = button == 0;
        return dragging;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging && tab == Tab.TREE) {
            panX += (float) dragX;
            panY += (float) dragY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.BOOK && scrollY != 0) return book.scroll(scrollY, Math.max(1, (height - 32 - bookTop()) / CraftBookView.ROW));
        if (tab != Tab.TREE || scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        float old = zoom;
        zoom = Mth.clamp(zoom * (scrollY > 0 ? 1.15f : 1 / 1.15f), 0.25f, 2.0f);
        // Масштабируем вокруг курсора: точка дерева под мышью остаётся на месте.
        double treeX = (mouseX - treeCenterX()) / old;
        double treeY = (mouseY - treeCenterY()) / old;
        panX += (float) (treeX * old - treeX * zoom);
        panY += (float) (treeY * old - treeY * zoom);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
