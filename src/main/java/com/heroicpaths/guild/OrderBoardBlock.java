package com.heroicpaths.guild;

import com.heroicpaths.network.OpenOrderBoardPayload;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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

/**
 * Доска заказов гильдии искателей приключений: на ней каждый день новые заказы,
 * здесь же их берут и сдают. Рабочее место распорядителя гильдии.
 */
public class OrderBoardBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<OrderBoardBlock> CODEC = simpleCodec(OrderBoardBlock::new);

    public OrderBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
                && serverPlayer.connection.hasChannel(OpenOrderBoardPayload.TYPE)) {
            PacketDistributor.sendToPlayer(serverPlayer, new OpenOrderBoardPayload(pos, offersTag(serverPlayer, pos)));
        }
        return InteractionResult.CONSUME;
    }

    /** Заказы доски на сегодня для отправки клиенту. */
    public static CompoundTag offersTag(ServerPlayer player, BlockPos pos) {
        ListTag list = new ListTag();
        for (Order order : GuildOrders.offers(player.serverLevel(), pos)) list.add(order.save(player.registryAccess()));
        CompoundTag tag = new CompoundTag();
        tag.put("offers", list);
        return tag;
    }
}
