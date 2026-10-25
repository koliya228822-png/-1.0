package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HeroicPaths.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.heroicpaths"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ModItems.EXPERIENCE_TOME.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        ModItems.ITEMS.getEntries().forEach(item -> {
                            if (item.get() != ModItems.ABILITY_BOOK.get() && item.get() != ModItems.UNIQUE_ABILITY_BOOK.get() && !(item.get() instanceof com.heroicpaths.item.ProfessionManualItem)) {
                                output.accept(item.get());
                            }
                        });
                        // Полные камни души — для творческого режима.
                        for (var stone : java.util.List.of(ModItems.LESSER_SOUL_STONE, ModItems.GREATER_SOUL_STONE, ModItems.ANCIENT_SOUL_STONE)) {
                            output.accept(com.heroicpaths.item.SoulStoneItem.full(stone.get()));
                        }
                        // Трактаты ремёсел всех ступеней.
                        for (var craft : com.heroicpaths.craft.Profession.values()) {
                            for (int rank = 2; rank <= com.heroicpaths.craft.Professions.MAX_RANK; rank++) {
                                output.accept(com.heroicpaths.item.ProfessionManualItem.create(craft.manual(), rank));
                            }
                        }
                        // По книге на каждое умение, которое учат по книгам.
                        for (var def : com.heroicpaths.rpg.ability.AbilityRegistry.bookAbilities(100)) {
                            output.accept(com.heroicpaths.item.AbilityBookItem.create(ModItems.ABILITY_BOOK.get(), def));
                        }
                        for (var def : com.heroicpaths.rpg.ability.AbilityRegistry.uniques()) {
                            output.accept(com.heroicpaths.item.AbilityBookItem.create(ModItems.UNIQUE_ABILITY_BOOK.get(), def));
                        }
                    })
                    .build());

    private ModCreativeTabs() {}
}
