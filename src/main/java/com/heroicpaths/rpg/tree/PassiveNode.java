package com.heroicpaths.rpg.tree;

import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.stat.Keystone;
import com.heroicpaths.rpg.stat.StatBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Узел пассивного дерева. Координаты — в единицах экрана дерева при масштабе 1. */
public record PassiveNode(int id, NodeType type, PlayerClass region, String nameKey, StatBlock stats,
                          @Nullable Keystone keystone, float x, float y) {

    public MutableComponent name() {
        return keystone != null ? keystone.displayName() : Component.translatable(nameKey);
    }

    public List<Component> tooltip() {
        List<Component> lines = new ArrayList<>();
        ChatFormatting color = switch (type) {
            case KEYSTONE -> ChatFormatting.GOLD;
            case NOTABLE -> ChatFormatting.YELLOW;
            case START -> ChatFormatting.AQUA;
            case SMALL -> ChatFormatting.WHITE;
        };
        lines.add(name().withStyle(color));
        if (keystone != null) lines.add(keystone.description().withStyle(ChatFormatting.GRAY));
        for (Component line : stats.describe()) lines.add(line.copy().withStyle(ChatFormatting.BLUE));
        return lines;
    }
}
