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
 * Добавляет здания гильдий в пулы домов ванильных деревень всех пяти типов.
 * Поле {@code StructureTemplatePool.templates} открыто через META-INF/accesstransformer.cfg.
 */
public final class VillageInjector {
    public static final List<String> BIOMES = List.of("plains", "desert", "savanna", "snowy", "taiga");
    public static final List<String> GUILDS = List.of("swordmaster_guild", "archer_guild", "mage_guild");
    /**
     * Вес каждой гильдии в пуле домов. Для сравнения: у обычных домов равнин вес 2–3, весь пул — 70–90.
     * Дома наставников крупные и встают не на каждый участок, поэтому вес заметно выше: на дом гильдии
     * приходится около трети выборов из пула.
     */
    private static final int GUILD_WEIGHT = 12;

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
        injectGuilds(event.getServer().registryAccess());
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
            StructurePoolElement plaza = StructurePoolElement.single(plazaTemplate(biome).toString(), noProcessors)
                    .apply(StructureTemplatePool.Projection.RIGID);
            pool.templates.clear();
            pool.templates.addAll(zombie);
            for (int i = 0; i < 100; i++) pool.templates.add(plaza);
            HeroicPaths.LOGGER.info("Village centers of {} now use the gate plaza ({} abandoned variants kept)", biome, zombie.size());
        }
    }

    public static final ResourceKey<StructureProcessorList> NO_PROCESSORS =
            ResourceKey.create(Registries.PROCESSOR_LIST, ResourceLocation.withDefaultNamespace("empty"));

    public static void injectGuilds(RegistryAccess registryAccess) {
        Registry<StructureTemplatePool> pools = registryAccess.registryOrThrow(Registries.TEMPLATE_POOL);
        Holder<StructureProcessorList> noProcessors =
                registryAccess.registryOrThrow(Registries.PROCESSOR_LIST).getHolderOrThrow(NO_PROCESSORS);

        for (String biome : BIOMES) {
            StructureTemplatePool pool = pools.get(housesPool(biome));
            if (pool == null) {
                HeroicPaths.LOGGER.warn("Village pool {} not found, guilds not added", housesPool(biome));
                continue;
            }
            int before = pool.templates.size();
            for (String guild : GUILDS) {
                StructurePoolElement element = StructurePoolElement
                        .single(guildTemplate(biome, guild).toString(), noProcessors)
                        .apply(StructureTemplatePool.Projection.RIGID);

                for (int i = 0; i < GUILD_WEIGHT; i++) pool.templates.add(element);
            }
            HeroicPaths.LOGGER.info("Added class guilds to {}: {} -> {} entries", housesPool(biome), before, pool.templates.size());
        }
    }
}
