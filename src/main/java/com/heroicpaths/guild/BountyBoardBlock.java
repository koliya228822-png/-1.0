package com.heroicpaths.guild;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/**
 * Доска наказов глашатая гильдии: каждый день наказы на боссов всех данжей.
 * Берутся и сдаются так же, как заказы; рабочее место глашатая.
 */
public class BountyBoardBlock extends OrderBoardBlock {
    public static final MapCodec<BountyBoardBlock> CODEC = simpleCodec(BountyBoardBlock::new);

    public BountyBoardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
