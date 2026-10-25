package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.accessory.AccessoryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Окна с контейнером. */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HeroicPaths.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<AccessoryMenu>> ACCESSORIES = MENUS.register("accessories",
            () -> new MenuType<>(AccessoryMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private ModMenus() {}
}
