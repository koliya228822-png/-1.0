package com.heroicpaths.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Свиток доступа в данж. Нужен, чтобы открыть врата в данж с 20 уровня. Алтарь забирает его при открытии врат
 * и сжигает, когда группа действительно вошла; если никто не вошёл, свиток возвращается.
 */
public class DungeonScrollItem extends Item {
    private final String dungeon;

    public DungeonScrollItem(String dungeon, Properties properties) {
        super(properties);
        this.dungeon = dungeon;
    }

    public DungeonType dungeon() {
        DungeonType type = DungeonType.byId(dungeon);
        return type == null ? DungeonType.CRYPT : type;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.heroicpaths.dungeon_scroll.dungeon", dungeon().displayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.heroicpaths.dungeon_scroll.hint").withStyle(ChatFormatting.GRAY));
    }
}
