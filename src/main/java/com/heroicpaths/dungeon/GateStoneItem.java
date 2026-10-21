package com.heroicpaths.dungeon;

import com.heroicpaths.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Камень врат: выдаётся по контракту гильдии, открывает портал в данж на алтаре деревни. */
public class GateStoneItem extends Item {
    public GateStoneItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Item item, DungeonType type, int partySize) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModComponents.GATE_KEY.get(), new GateKey(type.id(), partySize));
        return stack;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GateKey key = stack.get(ModComponents.GATE_KEY.get());
        DungeonType type = key == null ? null : DungeonType.byId(key.dungeon());
        if (type == null) {
            tooltip.add(Component.translatable("item.heroicpaths.gate_stone.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.heroicpaths.gate_stone.dungeon", type.displayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.heroicpaths.gate_stone.level", type.minLevel()).withStyle(ChatFormatting.GRAY));
        if (type.requiresScroll()) {
            tooltip.add(Component.translatable("item.heroicpaths.gate_stone.scroll", new ItemStack(type.scroll()).getHoverName())
                    .withStyle(ChatFormatting.YELLOW));
        }
        tooltip.add(Component.translatable("item.heroicpaths.gate_stone.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
