package com.heroicpaths.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Искра реликвии: редкая добыча с боссов данжей. На алтаре реликвий поднимает легендарную вещь до реликвии. */
public class RelicSparkItem extends Item {
    public RelicSparkItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.heroicpaths.relic_spark.desc").withStyle(ChatFormatting.GRAY));
    }
}
