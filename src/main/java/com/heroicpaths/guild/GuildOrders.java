package com.heroicpaths.guild;

import com.heroicpaths.dungeon.DungeonType;
import com.heroicpaths.entity.DungeonBoss;
import com.heroicpaths.minion.Minion;
import com.heroicpaths.party.PartyManager;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Заказы гильдии искателей приключений: взять на доске заказов, выполнить, сдать за награду.
 * Убийства, добыча и зачистки данжей любого участника группы засчитываются всем участникам, взявшим такой заказ.
 */
public final class GuildOrders {
    public static final int MAX_ACTIVE = 3;
    /** Как далеко от доски можно брать и сдавать заказы (квадрат расстояния). */
    public static final double BOARD_RANGE_SQR = 8 * 8;
    public static final double BOUNTY_RANGE_SQR = 24 * 24;
    public static final long DAY_TICKS = 24000L;

    private GuildOrders() {}

    public static long day(Level level) {
        return level.getDayTime() / DAY_TICKS;
    }

    public static String dayPrefix(long day) {
        return "d" + day + ":";
    }

    /** На доске наказов глашатая — наказы на боссов, на обычной доске заказов — заказы дня. */
    public static List<Order> offers(ServerLevel level, BlockPos board) {
        if (level.getBlockState(board).getBlock() instanceof BountyBoardBlock) return OrderCatalog.bounties(day(level), board);
        return OrderCatalog.offers(level.getSeed(), day(level), board);
    }

    private static boolean nearBoard(ServerPlayer player, BlockPos board) {
        net.minecraft.world.level.block.Block block = player.level().getBlockState(board).getBlock();
        // Наказы берут у глашатая, а он ходит по залу гильдии, поэтому до его доски можно быть дальше.
        double range = block instanceof BountyBoardBlock ? BOUNTY_RANGE_SQR : BOARD_RANGE_SQR;
        return block instanceof OrderBoardBlock
                && player.distanceToSqr(board.getCenter()) <= range;
    }

    private static void tell(ServerPlayer player, Component message, ChatFormatting color) {
        player.displayClientMessage(message.copy().withStyle(color), true);
    }

    // ---------------------------------------------------------------- действия игрока

    public static boolean take(ServerPlayer player, BlockPos board, int index) {
        if (!nearBoard(player, board)) return false;
        List<Order> offers = offers(player.serverLevel(), board);
        if (index < 0 || index >= offers.size()) return false;
        Order offer = offers.get(index);
        RpgData data = RpgManager.get(player);
        if (data.hasTakenOrder(offer.id())) {
            tell(player, Component.translatable("message.heroicpaths.order_already"), ChatFormatting.RED);
            return false;
        }
        if (data.getOrders().size() >= MAX_ACTIVE) {
            tell(player, Component.translatable("message.heroicpaths.order_full", MAX_ACTIVE), ChatFormatting.RED);
            return false;
        }
        if (data.getLevel() < offer.minLevel()) {
            tell(player, Component.translatable("message.heroicpaths.order_level", offer.minLevel()), ChatFormatting.RED);
            return false;
        }
        data.addOrder(offer);
        data.markOrderTaken(offer.id(), dayPrefix(day(player.level())));
        RpgManager.sync(player);
        tell(player, Component.translatable("message.heroicpaths.order_taken", offer.title()), ChatFormatting.GOLD);
        player.serverLevel().playSound(null, board, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1f, 1f);
        return true;
    }

    /** Сдать заказ на любой доске заказов. Для доставки предметы забираются из инвентаря. */
    public static boolean turnIn(ServerPlayer player, BlockPos board, int slot) {
        if (!nearBoard(player, board)) return false;
        RpgData data = RpgManager.get(player);
        if (slot < 0 || slot >= data.getOrders().size()) return false;
        Order order = data.getOrders().get(slot);
        if (order.type() == OrderType.DELIVERY) {
            int have = countItems(player, order);
            if (have < order.amount()) {
                tell(player, Component.translatable("message.heroicpaths.order_missing", order.amount() - have,
                        Component.translatable(order.targetKey())), ChatFormatting.RED);
                return false;
            }
            removeItems(player, order, order.amount());
        } else if (!order.isDone()) {
            tell(player, Component.translatable("message.heroicpaths.order_not_done", order.progress(), order.amount()), ChatFormatting.RED);
            return false;
        }
        data.removeOrder(slot);
        RpgManager.addXp(player, order.rewardXp());
        give(player, new ItemStack(Items.EMERALD, order.rewardEmeralds()));
        if (!order.bonus().isEmpty()) give(player, order.bonus().copy());
        RpgManager.sync(player);
        player.sendSystemMessage(Component.translatable("message.heroicpaths.order_done", order.title(), order.rewardXp(), order.rewardEmeralds())
                .withStyle(ChatFormatting.GOLD));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.3f);
        return true;
    }

    public static boolean abandon(ServerPlayer player, int slot) {
        RpgData data = RpgManager.get(player);
        if (slot < 0 || slot >= data.getOrders().size()) return false;
        Order order = data.getOrders().get(slot);
        data.removeOrder(slot);
        RpgManager.sync(player);
        tell(player, Component.translatable("message.heroicpaths.order_abandoned", order.title()), ChatFormatting.GRAY);
        return true;
    }

    /** Сколько подходящих предметов у игрока (для доставки). */
    public static int countItems(Player player, Order order) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) if (order.matchesItem(stack)) count += stack.getCount();
        for (ItemStack stack : player.getInventory().offhand) if (order.matchesItem(stack)) count += stack.getCount();
        return count;
    }

    private static void removeItems(Player player, Order order, int amount) {
        List<ItemStack> slots = new ArrayList<>(player.getInventory().items);
        slots.addAll(player.getInventory().offhand);
        for (ItemStack stack : slots) {
            if (amount <= 0) return;
            if (!order.matchesItem(stack)) continue;
            int take = Math.min(amount, stack.getCount());
            stack.shrink(take);
            amount -= take;
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        while (!stack.isEmpty()) {
            ItemStack part = stack.split(Math.min(stack.getCount(), stack.getMaxStackSize()));
            if (!player.getInventory().add(part)) player.drop(part, false);
        }
    }

    // ---------------------------------------------------------------- прогресс

    /** Участники групп этих игроков, которые сейчас в сети (каждый один раз). */
    private static Set<ServerPlayer> partyMembers(MinecraftServer server, Collection<ServerPlayer> sources) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (ServerPlayer source : sources) ids.addAll(PartyManager.roster(source));
        Set<ServerPlayer> result = new LinkedHashSet<>();
        for (UUID id : ids) {
            ServerPlayer member = server.getPlayerList().getPlayer(id);
            if (member != null) result.add(member);
        }
        return result;
    }

    /** Продвигает подходящие заказы всех участников группы. */
    public static void progress(MinecraftServer server, Collection<ServerPlayer> sources, OrderType type, Predicate<Order> matches) {
        for (ServerPlayer member : partyMembers(server, sources)) {
            RpgData data = RpgManager.get(member);
            boolean changed = false;
            for (int i = 0; i < data.getOrders().size(); i++) {
                Order order = data.getOrders().get(i);
                if (order.type() != type || order.isDone() || !matches.test(order)) continue;
                Order next = order.withProgress(order.progress() + 1);
                data.setOrder(i, next);
                changed = true;
                if (next.isDone()) {
                    member.sendSystemMessage(Component.translatable("message.heroicpaths.order_ready", next.title()).withStyle(ChatFormatting.GREEN));
                    member.serverLevel().playSound(null, member.getX(), member.getY(), member.getZ(),
                            SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.8f, 1.5f);
                }
            }
            if (changed) RpgManager.sync(member);
        }
    }

    /** Игрок, которому засчитывается убийство: сам убийца или хозяин его зверя или слуги. */
    @Nullable
    public static ServerPlayer creditedPlayer(@Nullable Entity killer) {
        if (killer instanceof ServerPlayer player) return player;
        if (killer instanceof Minion minion && killer.level() instanceof ServerLevel level && minion.minionData().owner() != null) {
            return level.getServer().getPlayerList().getPlayer(minion.minionData().owner());
        }
        return null;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player || !(victim.level() instanceof ServerLevel level)) return;
        if (victim instanceof DungeonBoss) onBossKilled(level, victim, creditedPlayer(event.getSource().getEntity()));
        ServerPlayer player = creditedPlayer(event.getSource().getEntity());
        if (player == null) return;
        progress(level.getServer(), List.of(player), OrderType.HUNT, order -> order.matchesEntity(victim.getType()));
    }

    /** Как далеко от убитого босса игроки получают засчитанный наказ. */
    public static final double BOSS_CREDIT_RANGE = 48;

    /** Босс убит: наказ засчитывается убийце и всем игрокам в его логове, а через них — их группам. */
    public static void onBossKilled(ServerLevel level, LivingEntity boss, @Nullable ServerPlayer killer) {
        List<ServerPlayer> witnesses = new ArrayList<>(level.getEntitiesOfClass(ServerPlayer.class,
                boss.getBoundingBox().inflate(BOSS_CREDIT_RANGE), player -> !player.isSpectator()));
        if (killer != null && !witnesses.contains(killer)) witnesses.add(killer);
        if (witnesses.isEmpty()) return;
        progress(level.getServer(), witnesses, OrderType.BOSS, order -> order.matchesEntity(boss.getType()));
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || event.isCanceled()) return;
        progress(player.server, List.of(player), OrderType.GATHER, order -> order.matchesBlock(event.getState()));
    }

    /** Данж зачищен: все вошедшие и их группы продвигают заказы на этот данж (каждый один раз). */
    public static void onDungeonCleared(MinecraftServer server, Collection<ServerPlayer> entered, DungeonType type) {
        progress(server, entered, OrderType.DUNGEON, order -> order.target().equals(type.id()));
    }
}
