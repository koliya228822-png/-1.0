package com.heroicpaths.travel;

import com.heroicpaths.dungeon.DungeonManager;
import com.heroicpaths.network.WaystoneListPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Камень перемещения: точка быстрого перемещения. Правый клик — точка становится известной и открывается список
 * известных точек, к любой из которых отсюда можно переместиться бесплатно.
 */
public class WaystoneBlock extends Block {
    public WaystoneBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server)) return;
        if (DungeonManager.isDungeonLevel(level)) return;
        Waystones.Entry entry = Waystones.get(server.getServer()).add(server, pos, placer instanceof Player player ? player : null, stack);
        if (placer instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.waystone_placed", entry.name()).withStyle(ChatFormatting.GREEN), true);
            Waystones.send(player, WaystoneListPayload.SYNC, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            Waystones.get(server.getServer()).remove(level.dimension(), pos);
            for (ServerPlayer player : server.getServer().getPlayerList().getPlayers()) Waystones.send(player, WaystoneListPayload.SYNC, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (DungeonManager.isDungeonLevel(level)) {
            serverPlayer.displayClientMessage(Component.translatable("message.heroicpaths.waystone_dungeon").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        Waystones.activate(serverPlayer, pos);
        return InteractionResult.CONSUME;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) != 0) return;
        level.addParticle(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.05, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.02, 0);
    }
}
