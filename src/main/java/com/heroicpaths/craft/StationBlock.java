package com.heroicpaths.craft;

import com.heroicpaths.network.OpenStationPayload;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

/** Станок ремесла: ПКМ открывает список его рецептов. */
public class StationBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<StationBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Station.CODEC.fieldOf("station").forGetter(StationBlock::station),
            propertiesCodec()
    ).apply(instance, StationBlock::new));

    private final Station station;

    public StationBlock(Station station, Properties properties) {
        super(properties);
        this.station = station;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public Station station() {
        return station;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null
                && serverPlayer.connection.hasChannel(OpenStationPayload.TYPE)) {
            PacketDistributor.sendToPlayer(serverPlayer, new OpenStationPayload(station.ordinal(), pos));
        }
        return InteractionResult.CONSUME;
    }
}
