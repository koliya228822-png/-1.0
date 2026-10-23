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

    public static final DeferredHolder<PoiType, PoiType> BOUNTY_HERALD_POI =
            POI_TYPES.register("bounty_herald", () -> workstation(ModBlocks.BOUNTY_BOARD.get()));
    public static final DeferredHolder<PoiType, PoiType> BANKER_POI =
            POI_TYPES.register("banker", () -> workstation(ModBlocks.BANK_COUNTER.get()));
    public static final DeferredHolder<PoiType, PoiType> ARMS_DEALER_POI =
            POI_TYPES.register("arms_dealer", () -> workstation(ModBlocks.ARMS_DISPLAY.get()));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> SWORDMASTER =
            PROFESSIONS.register("swordmaster", () -> profession("swordmaster", SWORDMASTER_POI.getKey(), SoundEvents.VILLAGER_WORK_WEAPONSMITH));
    public static final DeferredHolder<VillagerProfession, VillagerProfession> MASTER_ARCHER =
            PROFESSIONS.register("master_archer", () -> profession("master_archer", MASTER_ARCHER_POI.getKey(), SoundEvents.VILLAGER_WORK_FLETCHER));
    public static final DeferredHolder<VillagerProfession, VillagerProfession> ARCHMAGE =
            PROFESSIONS.register("archmage", () -> profession("archmage", ARCHMAGE_POI.getKey(), SoundEvents.VILLAGER_WORK_LIBRARIAN));
    /** Распорядитель гильдии искателей приключений. */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> GUILDMASTER =
            PROFESSIONS.register("guildmaster", () -> profession("guildmaster", GUILDMASTER_POI.getKey(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER));
    /** Глашатай гильдии: выдаёт наказы на боссов данжей. */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> BOUNTY_HERALD =
            PROFESSIONS.register("bounty_herald", () -> profession("bounty_herald", BOUNTY_HERALD_POI.getKey(), SoundEvents.VILLAGER_WORK_ARMORER));
    /** Банкир: скупает добычу из данжей за изумруды и меняет изумруды на блоки. */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> BANKER =
            PROFESSIONS.register("banker", () -> profession("banker", BANKER_POI.getKey(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER));
    /** Мастер-оружейник: каждый день выставляет десять мечей и луков, бывают и редкие. */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> ARMS_DEALER =
            PROFESSIONS.register("arms_dealer", () -> profession("arms_dealer", ARMS_DEALER_POI.getKey(), SoundEvents.VILLAGER_WORK_WEAPONSMITH));

    private ModVillagers() {}

    private static PoiType workstation(Block block) {
        return new PoiType(ImmutableSet.copyOf(block.getStateDefinition().getPossibleStates()), 1, 1);
    }

    private static VillagerProfession profession(String name, ResourceKey<PoiType> jobSite, SoundEvent workSound) {
        return new VillagerProfession(name, poi -> poi.is(jobSite), poi -> poi.is(jobSite),
                ImmutableSet.of(), ImmutableSet.of(), workSound);
    }
}
