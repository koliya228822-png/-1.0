package com.heroicpaths.dungeon;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Врата подземелья: стоят в подземных залах под входами в данжи, разбросанными по миру. Ведут в данж своего типа
 * без камня врат (проверки уровня группы и свитка — те же). ПКМ открывает портал в рамке перед вратами.
 * После прохода врата запечатываются на 20 минут. Разрушить их нельзя.
 */
public class DungeonGateBlock extends GateAltarBlock {
    public static final MapCodec<DungeonGateBlock> CODEC = simpleCodec(DungeonGateBlock::new);
    public static final EnumProperty<DungeonType> DUNGEON = EnumProperty.create("dungeon", DungeonType.class);

    public DungeonGateBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DUNGEON);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof GateAltarBlockEntity gate) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }
        if (gate.isOpen()) {
            long secondsLeft = (gate.closesAt() - level.getGameTime()) / 20;
            player.displayClientMessage(Component.translatable("message.heroicpaths.gate_open_for", secondsLeft), true);
        } else {
            gate.activateWild(serverPlayer, state.getValue(DUNGEON));
        }
        return InteractionResult.CONSUME;
    }

    /** Над вратами тлеют души. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) != 0) return;
        level.addParticle(ParticleTypes.SOUL, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.05,
                pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.02, 0);
    }
}
