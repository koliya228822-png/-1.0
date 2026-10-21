package com.heroicpaths.dungeon;

import com.heroicpaths.registry.ModBlockEntities;
import com.heroicpaths.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Алтарь врат. Смотрит в сторону рамки портала: рамка внутри 3×4 стоит в 2 блоках перед алтарём.
 * Камень врат, приложенный к алтарю, открывает портал в данж на 5 минут.
 */
public class GateAltarBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<GateAltarBlock> CODEC = simpleCodec(GateAltarBlock::new);

    public GateAltarBlock(Properties properties) {
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
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GateAltarBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.GATE_ALTAR.get()) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<GateAltarBlockEntity>) (l, p, s, altar) -> altar.serverTick();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.GATE_STONE.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof GateAltarBlockEntity altar && player instanceof ServerPlayer serverPlayer) {
            altar.activate(serverPlayer, stack);
        }
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof GateAltarBlockEntity altar && altar.isOpen()) {
            long secondsLeft = (altar.closesAt() - level.getGameTime()) / 20;
            player.displayClientMessage(Component.translatable("message.heroicpaths.gate_open_for", secondsLeft), true);
        } else {
            player.displayClientMessage(Component.translatable("message.heroicpaths.gate_need_stone"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Алтарь сломан: портал гаснет, а камень и свиток, если группа ещё не вошла, возвращаются хозяину.
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GateAltarBlockEntity altar) {
            altar.closeGate();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    public static Direction facing(BlockState state) {
        return state.hasProperty(FACING) ? state.getValue(FACING) : null;
    }
}
