package com.heroicpaths.village;

import com.heroicpaths.registry.ModWorldgen;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Фундамент под домом деревни: под каждым блоком пола (нижний слой шаблона) кладка уходит вниз до земли,
 * так что дом на склоне стоит на цоколе, а не висит в воздухе. Глубже {@link #MAX_DEPTH} не копает.
 */
public class FoundationProcessor extends StructureProcessor {
    public static final MapCodec<FoundationProcessor> CODEC =
            BlockState.CODEC.fieldOf("block").xmap(FoundationProcessor::new, p -> p.block);
    private static final int MAX_DEPTH = 24;

    private final BlockState block;

    public FoundationProcessor(BlockState block) {
        this.block = block;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModWorldgen.FOUNDATION.get();
    }

    @Override
    public List<StructureTemplate.StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos offset, BlockPos pos,
            List<StructureTemplate.StructureBlockInfo> original, List<StructureTemplate.StructureBlockInfo> processed,
            StructurePlaceSettings settings) {
        BoundingBox box = settings.getBoundingBox();
        int floor = offset.getY();
        List<StructureTemplate.StructureBlockInfo> result = new ArrayList<>(processed);
        for (StructureTemplate.StructureBlockInfo info : processed) {
            if (info.pos().getY() != floor || !supportsFoundation(info.state())) continue;
            if (box != null && !box.isInside(info.pos())) continue;
            BlockPos.MutableBlockPos below = info.pos().mutable();
            for (int i = 0; i < MAX_DEPTH; i++) {
                below.move(0, -1, 0);
                if (below.getY() <= level.getMinBuildHeight()) break;
                if (!canReplace(level.getBlockState(below))) break;
                result.add(new StructureTemplate.StructureBlockInfo(below.immutable(), block, null));
            }
        }
        return result;
    }

    private static boolean supportsFoundation(BlockState state) {
        return !state.isAir() && !state.is(Blocks.STRUCTURE_VOID) && !state.is(Blocks.JIGSAW);
    }

    /** Пустота, вода, трава, листва, снег — всё, что не держит дом. */
    private static boolean canReplace(BlockState state) {
        return state.isAir() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                || !state.getFluidState().isEmpty() || state.is(BlockTags.FLOWERS);
    }
}
