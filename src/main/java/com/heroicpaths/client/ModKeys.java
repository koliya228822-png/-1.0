package com.heroicpaths.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Клавиши мода. Их можно переназначить в Настройки → Управление → «Пути героев». */
public final class ModKeys {
    private static final String CATEGORY = "key.categories.heroicpaths";

    public static final KeyMapping OPEN_SKILLS = new KeyMapping("key.heroicpaths.skills",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    public static final KeyMapping[] ABILITIES = {
            new KeyMapping("key.heroicpaths.ability_1", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY),
            new KeyMapping("key.heroicpaths.ability_2", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY),
            new KeyMapping("key.heroicpaths.ability_3", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY),
            new KeyMapping("key.heroicpaths.ability_4", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY),
    };

    private ModKeys() {}

    public static KeyMapping ability(int slot) {
        return ABILITIES[slot];
    }
}
