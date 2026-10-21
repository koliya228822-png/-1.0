package com.heroicpaths.client;

import com.heroicpaths.craft.Profession;
import com.heroicpaths.craft.Professions;
import com.heroicpaths.craft.RuneEnchanting;
import com.heroicpaths.craft.Station;
import com.heroicpaths.craft.StationBlock;
import com.heroicpaths.craft.StationCrafting;
import com.heroicpaths.craft.StationRecipe;
import com.heroicpaths.network.RuneEnchantPayload;
import com.heroicpaths.network.StationCraftPayload;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Окно станка ремесла: ступень ремесла и список рецептов станка с материалами. Нажатие на строку создаёт предмет,
 * Shift+нажатие — столько, на сколько хватит материалов. Рецепты выше ступени ремесла закрыты.
 * У рунного стола есть второй режим — рунные чары для вещи в основной руке.
 */
public class StationScreen extends Screen {
    private static final int ROW = 24;
    private static final int ICON = 18;
    private static boolean lastEnchantMode;
    private final Station station;
    private final Profession profession;
    private final BlockPos pos;
    private List<RecipeHolder<StationRecipe>> recipes = List.of();
    private List<RuneEnchanting.Option> options = List.of();
    private boolean enchantMode;
    private int scroll;

    public StationScreen(Station station, BlockPos pos) {
        super(station.displayName());
        this.station = station;
        this.profession = Profession.of(station);
        this.pos = pos;
        this.enchantMode = station == Station.RUNE_TABLE && lastEnchantMode;
    }

    @Override
    protected void init() {
        recipes = StationCrafting.recipes(minecraft.level.getRecipeManager(), station);
        refreshOptions();
        scroll = Mth.clamp(scroll, 0, maxScroll());
        if (station == Station.RUNE_TABLE) {
            Button recipesButton = Button.builder(Component.translatable("screen.heroicpaths.rune.recipes"), b -> setMode(false))
                    .bounds(width / 2 - 154, height - 26, 100, 20).build();
            Button enchantButton = Button.builder(Component.translatable("screen.heroicpaths.rune.enchant"), b -> setMode(true))
                    .bounds(width / 2 - 50, height - 26, 100, 20).build();
            recipesButton.active = enchantMode;
            enchantButton.active = !enchantMode;
            addRenderableWidget(recipesButton);
            addRenderableWidget(enchantButton);
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 + 54, height - 26, 100, 20).build());
        } else {
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose())
                    .bounds(width / 2 - 50, height - 26, 100, 20).build());
        }
    }

    private void setMode(boolean enchant) {
        enchantMode = enchant;
        lastEnchantMode = enchant;
        scroll = 0;
        rebuildWidgets();
    }

    private void refreshOptions() {
        options = enchantMode ? RuneEnchanting.options(minecraft.player, minecraft.player.getMainHandItem()) : List.of();
    }

    private int panelWidth() {
        return Math.min(400, width - 20);
    }

    private int left() {
        return (width - panelWidth()) / 2;
    }

    /** Подсказка переносится по ширине окна, список начинается под ней. */
    private List<FormattedCharSequence> hintLines() {
        String key = enchantMode ? "screen.heroicpaths.rune.hint" : "screen.heroicpaths.station.hint";
        return font.split(Component.translatable(key), Math.max(100, panelWidth()));
    }

    /** Под заголовком — ступень ремесла и полоска опыта, затем подсказка; в режиме чар ещё строка с вещью в руке. */
    private int top() {
        return 40 + hintLines().size() * 10 + 2 + (enchantMode ? ROW : 0);
    }

    private int visibleRows() {
        return Math.max(1, (height - 46 - top()) / ROW);
    }

    private int rowCount() {
        return enchantMode ? options.size() : recipes.size();
    }

    private int maxScroll() {
        return Math.max(0, rowCount() - visibleRows());
    }

    @Override
    public void tick() {
        // Отошёл от станка или его сломали — окно закрывается.
        if (minecraft.player.distanceToSqr(pos.getCenter()) > StationCrafting.MAX_DISTANCE_SQR
                || !(minecraft.level.getBlockState(pos).getBlock() instanceof StationBlock block) || block.station() != station) {
            onClose();
            return;
        }
        refreshOptions();
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFD27F);
        renderProfession(graphics);
        List<FormattedCharSequence> hint = hintLines();
        for (int i = 0; i < hint.size(); i++) graphics.drawCenteredString(font, hint.get(i), width / 2, 40 + i * 10, 0xAAAAAA);

        ItemStack hovered = enchantMode ? renderEnchanting(graphics, mouseX, mouseY) : renderRecipes(graphics, mouseX, mouseY);

        int rows = visibleRows();
        if (maxScroll() > 0) {
            int right = left() + panelWidth();
            int trackTop = top();
            int trackHeight = rows * ROW - 2;
            int thumb = Math.max(10, trackHeight * rows / rowCount());
            int thumbY = trackTop + (trackHeight - thumb) * scroll / maxScroll();
            graphics.fill(right + 2, trackTop, right + 5, trackTop + trackHeight, 0x50000000);
            graphics.fill(right + 2, thumbY, right + 5, thumbY + thumb, 0xFFAAAAAA);
        }
        if (!hovered.isEmpty()) graphics.renderTooltip(font, hovered, mouseX, mouseY);
    }

    /** «Кузнец: подмастерье — опыт 140 / 400» и полоска до следующей ступени. */
    private void renderProfession(GuiGraphics graphics) {
        Professions professions = RpgManager.get(minecraft.player).professions();
        int rank = professions.rank(profession);
        int xp = professions.xp(profession);
        boolean capped = rank >= professions.maxRank(profession);
        Component progress = capped
                ? Component.translatable(rank >= Professions.MAX_RANK ? "screen.heroicpaths.craft.max" : "screen.heroicpaths.craft.capped")
                : Component.translatable("screen.heroicpaths.craft.xp", xp, Professions.xpForRank(rank + 1));
        Component line = professions.describe(profession).append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                .append(progress.copy().withStyle(ChatFormatting.GRAY));
        graphics.drawCenteredString(font, line, width / 2, 22, 0xFFFFFF);
        int barWidth = Math.min(200, panelWidth());
        int barLeft = width / 2 - barWidth / 2;
        graphics.fill(barLeft, 33, barLeft + barWidth, 36, 0xFF202020);
        if (!capped) {
            int from = Professions.xpForRank(rank);
            int to = Professions.xpForRank(rank + 1);
            float progressPart = Mth.clamp((xp - from) / (float) Math.max(1, to - from), 0f, 1f);
            int color = xp >= to ? 0xFFE0B040 : 0xFF50A0E0;
            graphics.fill(barLeft, 33, barLeft + (int) (barWidth * progressPart), 36, color);
        } else {
            graphics.fill(barLeft, 33, barLeft + barWidth, 36, 0xFF806020);
        }
    }

    private ItemStack renderRecipes(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = left();
        int right = left + panelWidth();
        ItemStack hovered = ItemStack.EMPTY;
        long cycle = minecraft.level.getGameTime() / 20;
        if (recipes.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.heroicpaths.station.empty"), width / 2, top() + 10, 0xFF8080);
        }
        for (int row = 0; row < visibleRows() && scroll + row < recipes.size(); row++) {
            StationRecipe recipe = recipes.get(scroll + row).value();
            int y = top() + row * ROW;
            boolean unlocked = StationCrafting.isUnlocked(minecraft.player, recipe);
            boolean craftable = unlocked && StationCrafting.canCraft(minecraft.player.getInventory(), recipe);
            boolean over = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW - 2;
            graphics.fill(left, y, right, y + ROW - 2, over && craftable ? 0x90405060 : unlocked ? 0x70000000 : 0x90200808);
            if (craftable) graphics.fill(left, y, left + 2, y + ROW - 2, 0xFF60C060);

            ItemStack result = recipe.result();
            graphics.renderItem(result, left + 5, y + 3);
            graphics.renderItemDecorations(font, result, left + 5, y + 3);
            if (mouseX >= left + 4 && mouseX < left + 22 && mouseY >= y + 2 && mouseY < y + 20) hovered = result;

            // Материалы справа: число красное, если не хватает.
            List<SizedIngredient> ingredients = recipe.ingredients();
            int ingredientsLeft = right - 6 - ingredients.size() * (ICON + 4);
            for (int i = 0; i < ingredients.size(); i++) {
                SizedIngredient ingredient = ingredients.get(i);
                ItemStack[] choices = ingredient.getItems();
                if (choices.length == 0) continue;
                ItemStack shown = choices[(int) (cycle % choices.length)].copyWithCount(1);
                int x = ingredientsLeft + i * (ICON + 4);
                graphics.renderItem(shown, x, y + 3);
                boolean enough = StationCrafting.available(minecraft.player.getInventory(), ingredient) >= ingredient.count();
                drawCount(graphics, String.valueOf(ingredient.count()), x, y, enough);
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y + 3 && mouseY < y + 19) hovered = shown;
            }

            int nameWidth = Math.max(20, ingredientsLeft - (left + 26) - 6);
            String name = clip(result.getHoverName().getString(), nameWidth);
            if (unlocked) {
                graphics.drawString(font, name, left + 26, y + 8, craftable ? 0xFFFFFF : 0x8A8A8A, true);
            } else {
                // Закрытый рецепт: какая ступень ремесла нужна.
                graphics.drawString(font, name, left + 26, y + 3, 0x7A7A7A, true);
                Component lock = Component.translatable("screen.heroicpaths.craft.locked", Professions.rankName(StationCrafting.requiredRank(recipe)));
                graphics.drawString(font, clip(lock.getString(), nameWidth), left + 26, y + 13, 0xFF6060, true);
            }
        }
        return hovered;
    }

    private ItemStack renderEnchanting(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = left();
        int right = left + panelWidth();
        ItemStack hovered = ItemStack.EMPTY;
        ItemStack held = minecraft.player.getMainHandItem();
        int headerY = top() - ROW;
        graphics.fill(left, headerY, right, headerY + ROW - 2, 0x70203040);
        if (!RuneEnchanting.canEnchant(held)) {
            graphics.drawCenteredString(font, Component.translatable("screen.heroicpaths.rune.no_item"), width / 2, headerY + 8, 0xFF8080);
            return hovered;
        }
        graphics.renderItem(held, left + 5, headerY + 3);
        graphics.renderItemDecorations(font, held, left + 5, headerY + 3);
        if (mouseX >= left + 4 && mouseX < left + 22 && mouseY >= headerY + 2 && mouseY < headerY + 20) hovered = held;
        int used = held.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).size();
        Component slots = Component.translatable("screen.heroicpaths.rune.slots", used, RuneEnchanting.slots(held));
        graphics.drawString(font, slots, right - 6 - font.width(slots), headerY + 8, 0xC0C0FF, true);
        graphics.drawString(font, clip(held.getHoverName().getString(), right - left - 40 - font.width(slots)), left + 26, headerY + 8, 0xFFFFFF, true);

        if (options.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("screen.heroicpaths.rune.none"), width / 2, top() + 10, 0xFF8080);
        }
        for (int row = 0; row < visibleRows() && scroll + row < options.size(); row++) {
            RuneEnchanting.Option option = options.get(scroll + row);
            int y = top() + row * ROW;
            boolean affordable = RuneEnchanting.affordable(minecraft.player, option);
            boolean available = option.lock() == null && affordable;
            boolean over = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW - 2;
            graphics.fill(left, y, right, y + ROW - 2, over && available ? 0x90405060 : option.lock() == null ? 0x70000000 : 0x90200808);
            if (available) graphics.fill(left, y, left + 2, y + ROW - 2, 0xFF60C0E0);

            // Цена справа: руны своей стихии и уровни опыта.
            ItemStack rune = new ItemStack(option.rune());
            int costLeft = right - 6 - 2 * (ICON + 4) - 8;
            graphics.renderItem(rune, costLeft, y + 3);
            drawCount(graphics, String.valueOf(option.runes()), costLeft, y,
                    RuneEnchanting.countRunes(minecraft.player, option.rune()) >= option.runes());
            if (mouseX >= costLeft && mouseX < costLeft + 16 && mouseY >= y + 3 && mouseY < y + 19) hovered = rune;
            Component xp = Component.translatable("screen.heroicpaths.rune.levels", option.xpLevels());
            boolean enoughXp = minecraft.player.getAbilities().instabuild || minecraft.player.experienceLevel >= option.xpLevels();
            graphics.drawString(font, xp, right - 6 - font.width(xp), y + 8, enoughXp ? 0x80FF20 : 0xFF5555, true);

            int textWidth = Math.max(20, costLeft - (left + 8) - 6);
            String name = clip(Enchantment.getFullname(option.enchantment(), option.level()).getString(), textWidth);
            if (option.lock() == null) {
                graphics.drawString(font, name, left + 8, y + 8, available ? 0xFFFFFF : 0x8A8A8A, true);
            } else {
                graphics.drawString(font, name, left + 8, y + 3, 0x7A7A7A, true);
                graphics.drawString(font, clip(option.lock().getString(), textWidth), left + 8, y + 13, 0xFF6060, true);
            }
        }
        return hovered;
    }

    private void drawCount(GuiGraphics graphics, String count, int x, int y, boolean enough) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.drawString(font, count, x + 17 - font.width(count), y + 12, enough ? 0xFFFFFF : 0xFF5555, true);
        graphics.pose().popPose();
    }

    private String clip(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0 || mouseX < left() || mouseX >= left() + panelWidth() || mouseY < top()) return false;
        int row = (int) ((mouseY - top()) / ROW);
        int index = scroll + row;
        if (row >= visibleRows() || index >= rowCount()) return false;
        if (enchantMode) {
            RuneEnchanting.Option option = options.get(index);
            if (option.lock() != null || !RuneEnchanting.affordable(minecraft.player, option)) return false;
            PacketDistributor.sendToServer(new RuneEnchantPayload(option.id(), pos));
        } else {
            RecipeHolder<StationRecipe> holder = recipes.get(index);
            if (!StationCrafting.isUnlocked(minecraft.player, holder.value())
                    || !StationCrafting.canCraft(minecraft.player.getInventory(), holder.value())) {
                return false;
            }
            PacketDistributor.sendToServer(new StationCraftPayload(holder.id(), pos, hasShiftDown()));
        }
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll = Mth.clamp(scroll - (int) Math.signum(scrollY), 0, maxScroll());
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
