package com.heroicpaths.registry;

import com.google.common.collect.ImmutableSet;
import com.heroicpaths.HeroicPaths;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Профессии наставников классов и их рабочие места (POI). */
public final class ModVillagers {
    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, HeroicPaths.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, HeroicPaths.MODID);

    public static final DeferredHolder<PoiType, PoiType> SWORDMASTER_POI =
            POI_TYPES.register("swordmaster", () -> workstation(ModBlocks.WEAPON_RACK.get()));
    public static final DeferredHolder<PoiType, PoiType> MASTER_ARCHER_POI =
            POI_TYPES.register("master_archer", () -> workstation(ModBlocks.ARCHERY_TARGET.get()));
    public static final DeferredHolder<PoiType, PoiType> ARCHMAGE_POI =
            POI_TYPES.register("archmage", () -> workstation(ModBlocks.ARCANE_LECTERN.get()));

    public static final DeferredHolder<PoiType, PoiType> GUILDMASTER_POI =
            POI_TYPES.register("guildmaster", () -> workstation(ModBlocks.ORDER_BOARD.get()));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> SWORDMASTER =
            PROFESSIONS.register("swordmaster", () -> profession("swordmaster", SWORDMASTER_POI.getKey(), SoundEvents.VILLAGER_WORK_WEAPONSMITH));
    public static final DeferredHolder<VillagerProfession, VillagerProfession> MASTER_ARCHER =
            PROFESSIONS.register("master_archer", () -> profession("master_archer", MASTER_ARCHER_POI.getKey(), SoundEvents.VILLAGER_WORK_FLETCHER));
    public static final DeferredHolder<VillagerProfession, VillagerProfession> ARCHMAGE =
            PROFESSIONS.register("archmage", () -> profession("archmage", ARCHMAGE_POI.getKey(), SoundEvents.VILLAGER_WORK_LIBRARIAN));
    /** Распорядитель гильдии искателей приключений. */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> GUILDMASTER =
            PROFESSIONS.register("guildmaster", () -> profession("guildmaster", GUILDMASTER_POI.getKey(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER));

    private ModVillagers() {}

    private static PoiType workstation(Block block) {
        return new PoiType(ImmutableSet.copyOf(block.getStateDefinition().getPossibleStates()), 1, 1);
    }

    private static VillagerProfession profession(String name, ResourceKey<PoiType> jobSite, SoundEvent workSound) {
        return new VillagerProfession(name, poi -> poi.is(jobSite), poi -> poi.is(jobSite),
                ImmutableSet.of(), ImmutableSet.of(), workSound);
    }
}
