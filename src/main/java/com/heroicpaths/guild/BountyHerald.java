package com.heroicpaths.guild;

import com.heroicpaths.network.OpenOrderBoardPayload;
import com.heroicpaths.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Глашатай гильдии: вместо торговли открывает свою доску наказов на боссов. Доска — его рабочее место;
 * если он его ещё не занял, берётся ближайшая доска наказов рядом с ним.
 */
public final class BountyHerald {
    /** Где искать доску вокруг глашатая, если рабочее место не запомнено. */
    private static final int SEARCH = 12;

    private BountyHerald() {}

    public static boolean isHerald(Villager villager) {
        return villager.getVillagerData().getProfession() == ModVillagers.BOUNTY_HERALD.get();
    }

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager villager) || !isHerald(villager) || event.getHand() != InteractionHand.MAIN_HAND) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer player) || !(villager.level() instanceof ServerLevel level)) return;
        BlockPos board = boardOf(level, villager);
        if (board == null) {
            villager.setUnhappyCounter(40);
            villager.playSound(SoundEvents.VILLAGER_NO, 1f, villager.getVoicePitch());
            return;
        }
        villager.playSound(SoundEvents.VILLAGER_YES, 1f, villager.getVoicePitch());
        villager.getLookControl().setLookAt(player);
        if (player.connection != null && player.connection.hasChannel(OpenOrderBoardPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new OpenOrderBoardPayload(board, OrderBoardBlock.offersTag(player, board)));
        }
    }

    /** Доска наказов глашатая: его рабочее место или ближайшая к нему. */
    @Nullable
    public static BlockPos boardOf(ServerLevel level, Villager villager) {
        Optional<GlobalPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (site.isPresent() && site.get().dimension() == level.dimension()
                && level.getBlockState(site.get().pos()).getBlock() instanceof BountyBoardBlock) {
            return site.get().pos();
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(villager.blockPosition().offset(-SEARCH, -4, -SEARCH),
                villager.blockPosition().offset(SEARCH, 4, SEARCH))) {
            if (!(level.getBlockState(pos).getBlock() instanceof BountyBoardBlock)) continue;
            double distance = pos.distSqr(villager.blockPosition());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }
}
