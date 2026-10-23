package com.heroicpaths.village;

import com.heroicpaths.HeroicPaths;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

import java.util.List;

/**
 * Перестраивает пулы ванильных деревень всех пяти типов: дома — в стиле зала гильдии, в центре — зал с вратами.
 * Поле {@code StructureTemplatePool.templates} открыто через META-INF/accesstransformer.cfg.
 */
public final class VillageInjector {
    public static final List<String> BIOMES = List.of("plains", "desert", "savanna", "snowy", "taiga");
    public static final List<String> GUILDS = List.of("swordmaster_guild", "archer_guild", "mage_guild");

    private VillageInjector() {}

    public static ResourceLocation guildTemplate(String biome, String guild) {
        return HeroicPaths.id("village/" + biome + "/" + guild);
    }

    public static ResourceLocation housesPool(String biome) {
        return ResourceLocation.withDefaultNamespace("village/" + biome + "/houses");
    }

    public static ResourceLocation plazaTemplate(String biome) {
        return HeroicPaths.id("village/" + biome + "/gate_plaza");
    }

    public static ResourceLocation townCentersPool(String biome) {
        return ResourceLocation.withDefaultNamespace("village/" + biome + "/town_centers");
    }

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        injectHouses(event.getServer().registryAccess());
        injectTownCenters(event.getServer().registryAccess());
    }

    /**
     * Каждая обычная деревня строится вокруг площади с вратами в данжи.
     * Заброшенные (зомби-) деревни остаются ванильными.
     */
    public static void injectTownCenters(RegistryAccess registryAccess) {
        Registry<StructureTemplatePool> pools = registryAccess.registryOrThrow(Registries.TEMPLATE_POOL);
        Holder<StructureProcessorList> noProcessors =
                registryAccess.registryOrThrow(Registries.PROCESSOR_LIST).getHolderOrThrow(NO_PROCESSORS);
        for (String biome : BIOMES) {
            StructureTemplatePool pool = pools.get(townCentersPool(biome));
            if (pool == null) continue;
            List<StructurePoolElement> zombie = pool.templates.stream()
                    .filter(element -> element.toString().contains("zombie")).toList();
            Holder<StructureProcessorList> foundation =
                    registryAccess.registryOrThrow(Registries.PROCESSOR_LIST).getHolderOrThrow(foundation(biome));
            StructurePoolElement plaza = StructurePoolElement.single(plazaTemplate(biome).toString(), foundation)
                    .apply(StructureTemplatePool.Projection.RIGID);
            pool.templates.clear();
            pool.templates.addAll(zombie);
            for (int i = 0; i < 100; i++) pool.templates.add(plaza);
            HeroicPaths.LOGGER.info("Village centers of {} now use the gate plaza ({} abandoned variants kept)", biome, zombie.size());
        }
    }

    public static final ResourceKey<StructureProcessorList> NO_PROCESSORS =
            ResourceKey.create(Registries.PROCESSOR_LIST, ResourceLocation.withDefaultNamespace("empty"));

    /** Процессоры фундамента для домов деревни этого биома (data/heroicpaths/worldgen/processor_list). */
    public static ResourceKey<StructureProcessorList> foundation(String biome) {
        return ResourceKey.create(Registries.PROCESSOR_LIST, HeroicPaths.id("foundation_" + biome));
    }

    public static ResourceLocation ourHousesPool(String biome) {
        return HeroicPaths.id("village/" + biome + "/houses");
    }

    /**
     * Ванильные дома уступают место домам в стиле зала гильдии (пул heroicpaths:village/<биом>/houses).
     * Поля, загоны, колодцы и пустые места остаются. Дома наставников и казармы в пул не входят: их ставит
     * {@link VillageLayout} на места больших домов.
     */
    public static void injectHouses(RegistryAccess registryAccess) {
        Registry<StructureTemplatePool> pools = registryAccess.registryOrThrow(Registries.TEMPLATE_POOL);
        for (String biome : BIOMES) {
            StructureTemplatePool pool = pools.get(housesPool(biome));
            StructureTemplatePool ours = pools.get(ourHousesPool(biome));
            if (pool == null || ours == null) {
                HeroicPaths.LOGGER.warn("Village pool {} or {} not found, houses not replaced", housesPool(biome), ourHousesPool(biome));
                continue;
            }
            int before = pool.templates.size();
            pool.templates.removeIf(element -> !keepsVanilla(element));
            int kept = pool.templates.size();
            pool.templates.addAll(ours.templates);
            HeroicPaths.LOGGER.info("Village houses of {}: {} vanilla entries -> {} kept + {} guild-style", biome, before, kept, ours.templates.size());
        }
    }

    /** Остаются из ванили: пустой элемент, поля, загоны, колодцы-места встреч и прочие не-дома. */
    static boolean keepsVanilla(StructurePoolElement element) {
        String id = element.toString();
        if (!id.contains("village/")) return true;
        return id.contains("farm") || id.contains("animal_pen") || id.contains("accessory") || id.contains("meeting_point")
                || id.contains("pen_") || id.contains("_pen");
    }
}
