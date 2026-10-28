package com.heroicpaths.client;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.craft.Profession;
import com.heroicpaths.craft.Professions;
import com.heroicpaths.craft.Station;
import com.heroicpaths.craft.StationCrafting;
import com.heroicpaths.craft.StationRecipe;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Вкладка «Книга крафтов» меню K: все рецепты мода по станкам (и верстаку) — что получится, из чего
 * и какая ступень ремесла нужна; рецепты верстака — сеткой 3×3, как их выкладывать. Только для чтения: создавать вещи надо у самого станка.
 */
final class CraftBookView {
    static final int ROW = 22;
    /** Строка рецепта верстака: сетка 3×3 из уменьшенных ячеек. */
    static final int GRID_ROW = 44;
    private static final int ICON = 18;
    private static final int CELL = 13;
    private static final float CELL_SCALE = 0.75f;

    /**
     * Рецепт в книге. У станков — ингредиенты с количеством; у верстака ({@code gridWidth} > 0) — ячейки сетки
     * построчно, ширина {@code gridWidth}, пустая ячейка — пустой массив.
     */
    record Entry(ItemStack result, List<ItemStack[]> ingredients, List<Integer> counts, int rank, Profession profession, int gridWidth) {
        Entry(ItemStack result, List<ItemStack[]> ingredients, List<Integer> counts, int rank, Profession profession) {
            this(result, ingredients, counts, rank, profession, 0);
        }
    }

    private static int category;
    private int scroll;
    private List<Entry> entries = List.of();

    static int categories() {
        return Station.values().length + 1;
    }

    static Component categoryName(int index) {
        return index < Station.values().length ? Component.translatable("block.heroicpaths.station_short." + Station.values()[index].id())
                : Component.translatable("screen.heroicpaths.book.workbench");
    }

    static int category() {
        return category;
    }

    private static boolean workbench() {
        return category == categories() - 1;
    }

    /** Высота строки в текущем разделе. */
    static int rowHeight() {
        return workbench() ? GRID_ROW : ROW;
    }

    void select(int index) {
        category = Mth.clamp(index, 0, categories() - 1);
        scroll = 0;
        entries = List.of();
    }

    private List<Entry> entries(Minecraft minecraft) {
        if (!entries.isEmpty() || minecraft.level == null) return entries;
        List<Entry> list = new ArrayList<>();
        if (category < Station.values().length) {
            Station station = Station.values()[category];
            for (RecipeHolder<StationRecipe> holder : StationCrafting.recipes(minecraft.level.getRecipeManager(), station)) {
                StationRecipe recipe = holder.value();
                List<ItemStack[]> items = new ArrayList<>();
                List<Integer> counts = new ArrayList<>();
                recipe.ingredients().forEach(ingredient -> {
                    items.add(ingredient.getItems());
                    counts.add(ingredient.count());
                });
                list.add(new Entry(recipe.result(), items, counts, StationCrafting.requiredRank(recipe), Profession.of(station)));
            }
        } else {
            // Верстак: рецепты мода в обычной сетке (заготовки, порох, пули, станки…) — как их выложить в сетку 3×3.
            for (var holder : minecraft.level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                if (!holder.id().getNamespace().equals(HeroicPaths.MODID)) continue;
                ItemStack result = holder.value().getResultItem(minecraft.level.registryAccess());
                if (result.isEmpty()) continue;
                List<ItemStack[]> cells = new ArrayList<>();
                int width;
                if (holder.value() instanceof ShapedRecipe shaped) {
                    width = shaped.getWidth();
                    for (Ingredient ingredient : shaped.getIngredients()) cells.add(ingredient.isEmpty() ? new ItemStack[0] : ingredient.getItems());
                } else {
                    // Без формы: ингредиенты подряд, клади как удобно.
                    for (Ingredient ingredient : holder.value().getIngredients()) if (!ingredient.isEmpty()) cells.add(ingredient.getItems());
                    width = cells.size() <= 1 ? 1 : cells.size() <= 4 ? 2 : 3;
                }
                if (cells.stream().allMatch(c -> c.length == 0) || width < 1 || width > 3 || cells.size() > 9) continue;
                list.add(new Entry(result, cells, List.of(), 0, null, width));
            }
        }
        entries = list;
        return list;
    }

    boolean scroll(double amount, int visibleRows) {
        int max = Math.max(0, entries.size() - visibleRows);
        scroll = Mth.clamp(scroll - (int) Math.signum(amount) * 3, 0, max);
        return true;
    }

    /** Рисует список; возвращает вещь под курсором (для подсказки) или пустой стек. */
    ItemStack render(GuiGraphics graphics, Font font, Minecraft minecraft, int left, int top, int right, int bottom, int mouseX, int mouseY) {
        List<Entry> list = entries(minecraft);
        Professions professions = RpgManager.get(minecraft.player).professions();
        ItemStack hovered = ItemStack.EMPTY;
        long cycle = minecraft.level.getGameTime() / 20;
        int rowHeight = rowHeight();
        int rows = Math.max(1, (bottom - top) / rowHeight);
        scroll = Mth.clamp(scroll, 0, Math.max(0, list.size() - rows));
        if (category == categories() - 1) {
            // Подсказка о переплавке алмазных вещей — первой строкой раздела верстака.
            for (FormattedCharSequence line : font.split(Component.translatable("screen.heroicpaths.book.anvil"), right - left)) {
                graphics.drawString(font, line, left, top, 0x9AD8E0, false);
                top += 10;
            }
            top += 2;
            rows = Math.max(1, (bottom - top) / rowHeight);
        }
        for (int row = 0; row < rows && scroll + row < list.size(); row++) {
            Entry entry = list.get(scroll + row);
            int y = top + row * rowHeight;
            boolean unlocked = entry.profession() == null || professions.rank(entry.profession()) >= entry.rank();
            graphics.fill(left, y, right, y + rowHeight - 2, unlocked ? 0x70000000 : 0x90200808);
            if (entry.gridWidth() > 0) {
                ItemStack under = renderGrid(graphics, font, entry, left, y, right, cycle, mouseX, mouseY);
                if (!under.isEmpty()) hovered = under;
                continue;
            }
            graphics.renderItem(entry.result(), left + 3, y + 2);
            graphics.renderItemDecorations(font, entry.result(), left + 3, y + 2);
            if (mouseX >= left + 2 && mouseX < left + 20 && mouseY >= y + 1 && mouseY < y + 19) hovered = entry.result();

            int ingredientsLeft = right - 4 - entry.ingredients().size() * (ICON + 2);
            for (int i = 0; i < entry.ingredients().size(); i++) {
                ItemStack[] choices = entry.ingredients().get(i);
                if (choices.length == 0) continue;
                ItemStack shown = choices[(int) (cycle % choices.length)].copyWithCount(1);
                int x = ingredientsLeft + i * (ICON + 2);
                graphics.renderItem(shown, x, y + 2);
                String count = String.valueOf(entry.counts().get(i));
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                graphics.drawString(font, count, x + 17 - font.width(count), y + 11, 0xFFFFFF, true);
                graphics.pose().popPose();
                if (mouseX >= x && mouseX < x + 16 && mouseY >= y + 2 && mouseY < y + 18) hovered = shown;
            }
            int textWidth = Math.max(20, ingredientsLeft - (left + 24) - 4);
            Component name = entry.result().getHoverName();
            graphics.drawString(font, font.plainSubstrByWidth(name.getString(), textWidth), left + 24, y + 2, unlocked ? 0xFFFFFF : 0x9A9A9A, true);
            if (entry.profession() != null) {
                Component need = entry.profession().displayName().append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Professions.rankName(entry.rank()).withStyle(unlocked ? ChatFormatting.GREEN : ChatFormatting.RED));
                graphics.drawString(font, font.plainSubstrByWidth(need.getString(), textWidth), left + 24, y + 11,
                        unlocked ? 0x70B070 : 0xD06060, false);
            }
        }
        if (list.isEmpty()) graphics.drawString(font, Component.translatable("screen.heroicpaths.station.empty"), left, top + 4, 0xFF8080, false);
        // Полоса прокрутки.
        int max = Math.max(0, list.size() - rows);
        if (max > 0) {
            int trackHeight = rows * rowHeight - 2;
            int thumb = Math.max(10, trackHeight * rows / list.size());
            int thumbY = top + (trackHeight - thumb) * scroll / max;
            graphics.fill(right + 2, top, right + 5, top + trackHeight, 0x50000000);
            graphics.fill(right + 2, thumbY, right + 5, thumbY + thumb, 0xFFAAAAAA);
        }
        return hovered;
    }

    /** Строка рецепта верстака: результат слева, справа сетка 3×3 с раскладкой; возвращает вещь под курсором. */
    private static ItemStack renderGrid(GuiGraphics graphics, Font font, Entry entry, int left, int y, int right, long cycle, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        int gridLeft = right - 4 - 3 * CELL, gridTop = y + 2;
        int resultY = y + (GRID_ROW - 2 - 16) / 2;
        // Стрелка от сетки к результату — как в окне верстака.
        int arrowX = gridLeft - 12;
        graphics.drawString(font, "←", arrowX, resultY + 4, 0xC0C0C0, false);
        for (int cy = 0; cy < 3; cy++) {
            for (int cx = 0; cx < 3; cx++) {
                int x = gridLeft + cx * CELL, top = gridTop + cy * CELL;
                graphics.fill(x, top, x + CELL - 1, top + CELL - 1, 0x60FFFFFF);
                graphics.fill(x + 1, top + 1, x + CELL - 1, top + CELL - 1, 0xA0202020);
                if (cx >= entry.gridWidth()) continue;
                int index = cy * entry.gridWidth() + cx;
                if (index >= entry.ingredients().size()) continue;
                ItemStack[] choices = entry.ingredients().get(index);
                if (choices.length == 0) continue;
                ItemStack shown = choices[(int) (cycle % choices.length)].copyWithCount(1);
                graphics.pose().pushPose();
                graphics.pose().translate(x, top, 0);
                graphics.pose().scale(CELL_SCALE, CELL_SCALE, 1f);
                graphics.renderItem(shown, 0, 0);
                graphics.pose().popPose();
                if (mouseX >= x && mouseX < x + CELL - 1 && mouseY >= top && mouseY < top + CELL - 1) hovered = shown;
            }
        }
        graphics.renderItem(entry.result(), left + 3, resultY);
        graphics.renderItemDecorations(font, entry.result(), left + 3, resultY);
        if (mouseX >= left + 2 && mouseX < left + 20 && mouseY >= resultY - 1 && mouseY < resultY + 17) hovered = entry.result();
        int textWidth = Math.max(20, arrowX - (left + 24) - 4);
        List<FormattedCharSequence> lines = font.split(entry.result().getHoverName(), textWidth);
        int shown = Math.min(3, lines.size());
        int textY = resultY + 4 - (shown - 1) * 5;
        for (int i = 0; i < shown; i++) graphics.drawString(font, lines.get(i), left + 24, textY + i * 10, 0xFFFFFF, true);
        return hovered;
    }
}
