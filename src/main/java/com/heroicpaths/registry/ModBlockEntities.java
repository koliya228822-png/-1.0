package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.dungeon.GateAltarBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HeroicPaths.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GateAltarBlockEntity>> GATE_ALTAR =
            BLOCK_ENTITIES.register("gate_altar", () -> BlockEntityType.Builder
                    .of(GateAltarBlockEntity::new, ModBlocks.GATE_ALTAR.get(), ModBlocks.DUNGEON_GATE.get()).build(null));

    private ModBlockEntities() {}
}
