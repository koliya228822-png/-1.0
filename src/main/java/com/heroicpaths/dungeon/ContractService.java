package com.heroicpaths.dungeon;

import com.heroicpaths.party.PartyManager;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Контракты гильдий: игрок выбирает данж и получает Камень врат. Группа регистрируется по составу
 * текущей группы игрока (/party); враги усиливаются за каждого её участника сверх первого.
 */
public final class ContractService {
    private static final double MAX_BOARD_DISTANCE = 8;

    private ContractService() {}

    /** Взять контракт у доски заданий, рядом с которой игрок стоит. */
    public static boolean takeFromBoard(ServerPlayer player, DungeonType type) {
        Optional<BlockPos> board = NbtUtils.readBlockPos(player.getPersistentData(), QuestBoardBlock.LAST_BOARD);
        if (board.isEmpty() || board.get().distToCenterSqr(player.position()) > MAX_BOARD_DISTANCE * MAX_BOARD_DISTANCE) {
            return false;
        }
        return take(player, type);
    }

    public static boolean take(ServerPlayer player, DungeonType type) {
        DungeonManager.Access access = DungeonManager.checkLevel(player, type);
        if (!access.allowed()) {
            player.displayClientMessage(access.reason().copy().withStyle(ChatFormatting.RED), true);
            return false;
        }
        int party = PartyManager.roster(player).size();
        ItemStack stone = GateStoneItem.create(ModItems.GATE_STONE.get(), type, party);
        if (!player.getInventory().add(stone)) player.drop(stone, false);
        RpgData data = RpgManager.get(player);
        data.setContract(type.id(), party);
        RpgManager.sync(player);
        player.sendSystemMessage(Component.translatable("message.heroicpaths.contract_taken", type.displayName(), party)
                .withStyle(ChatFormatting.GOLD));
        if (type.requiresScroll()) {
            player.sendSystemMessage(Component.translatable("message.heroicpaths.contract_needs_scroll",
                    new ItemStack(type.scroll()).getHoverName()).withStyle(ChatFormatting.YELLOW));
        }
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.8f);
        return true;
    }
}
