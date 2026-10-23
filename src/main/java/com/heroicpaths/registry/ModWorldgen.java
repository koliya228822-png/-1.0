package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.village.FoundationProcessor;
import com.heroicpaths.village.GuildVillageStructure;
import com.heroicpaths.village.VillageWallPiece;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Генерация деревень: свой тип структуры, процессор фундаментов и кусок городской стены. */
public final class ModWorldgen {
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, HeroicPaths.MODID);
    public static final DeferredRegister<StructureProcessorType<?>> PROCESSORS =
            DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, HeroicPaths.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, HeroicPaths.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<GuildVillageStructure>> GUILD_VILLAGE =
            STRUCTURE_TYPES.register("guild_village", () -> () -> GuildVillageStructure.CODEC);
    public static final DeferredHolder<StructureProcessorType<?>, StructureProcessorType<FoundationProcessor>> FOUNDATION =
            PROCESSORS.register("foundation", () -> () -> FoundationProcessor.CODEC);
    public static final DeferredHolder<StructurePieceType, StructurePieceType> VILLAGE_WALL =
            PIECES.register("village_wall", () -> (StructurePieceType.ContextlessType) VillageWallPiece::new);

    private ModWorldgen() {}
}
