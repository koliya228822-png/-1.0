package com.heroicpaths.party;

import com.heroicpaths.network.PartySyncPayload;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Группы игроков: создание, приглашения, выход и исключение, средний уровень, синхронизация состава
 * с клиентом и отключённый урон по своим.
 */
public final class PartyManager {
    /** Приглашение действует минуту. */
    public static final int INVITE_TICKS = 1200;
    private static final Map<UUID, Invite> INVITES = new HashMap<>();

    private record Invite(UUID partyId, UUID inviter, long expiresAt) {}

    private PartyManager() {}

    // ================================================================ запросы

    @Nullable
    public static Party partyOf(ServerPlayer player) {
        return PartyData.get(player.server).partyOf(player.getUUID());
    }

    /** Оба игрока в одной группе. Только на сервере. */
    public static boolean sameParty(Player a, Player b) {
        if (a == b || !(a instanceof ServerPlayer first) || !(b instanceof ServerPlayer second)) return false;
        Party party = partyOf(first);
        return party != null && party.contains(second.getUUID());
    }

    /** Состав группы игрока; одиночка — группа из него самого. */
    public static List<UUID> roster(ServerPlayer player) {
        Party party = partyOf(player);
        return party == null ? List.of(player.getUUID()) : List.copyOf(party.members());
    }

    /** Уровень участника: у игроков в сети — текущий, у остальных — последний известный. Минимум 1. */
    public static int memberLevel(MinecraftServer server, Party party, UUID member) {
        ServerPlayer online = server.getPlayerList().getPlayer(member);
        return online != null ? level(online) : party.cachedLevel(member);
    }

    public static int level(ServerPlayer player) {
        return Math.max(1, RpgManager.get(player).getLevel());
    }

    /** Средний уровень группы: сумма уровней всех участников, делённая на их число. */
    public static double averageLevel(MinecraftServer server, Party party) {
        if (party.size() == 0) return 1;
        int sum = 0;
        for (UUID member : party.members()) sum += memberLevel(server, party, member);
        return (double) sum / party.size();
    }

    /** Средний уровень группы игрока; для одиночки — его собственный уровень. */
    public static double averageLevel(ServerPlayer player) {
        Party party = partyOf(player);
        return party == null ? level(player) : averageLevel(player.server, party);
    }

    public static String format(double averageLevel) {
        return String.format(Locale.ROOT, "%.1f", averageLevel);
    }

    // ================================================================ действия

    public static boolean create(ServerPlayer player) {
        PartyData data = PartyData.get(player.server);
        if (data.partyOf(player.getUUID()) != null) {
            fail(player, "message.heroicpaths.party.already_in_party");
            return false;
        }
        data.create(player.getUUID(), player.getGameProfile().getName(), level(player));
        player.sendSystemMessage(Component.translatable("message.heroicpaths.party.created").withStyle(ChatFormatting.GREEN));
        syncAll(player.server);
        return true;
    }

    /** Лидер приглашает игрока. Если группы ещё нет, она создаётся. */
    public static boolean invite(ServerPlayer leader, ServerPlayer target) {
        PartyData data = PartyData.get(leader.server);
        if (target == leader) {
            fail(leader, "message.heroicpaths.party.self_invite");
            return false;
        }
        Party party = data.partyOf(leader.getUUID());
        if (party == null) party = data.create(leader.getUUID(), leader.getGameProfile().getName(), level(leader));
        if (!party.isLeader(leader.getUUID())) {
            fail(leader, "message.heroicpaths.party.not_leader");
            return false;
        }
        if (party.isFull()) {
            leader.sendSystemMessage(Component.translatable("message.heroicpaths.party.full", Party.MAX_SIZE).withStyle(ChatFormatting.RED));
            return false;
        }
        if (data.partyOf(target.getUUID()) != null) {
            leader.sendSystemMessage(Component.translatable("message.heroicpaths.party.target_in_party", target.getDisplayName())
                    .withStyle(ChatFormatting.RED));
            return false;
        }
        INVITES.put(target.getUUID(), new Invite(party.id(), leader.getUUID(), leader.server.overworld().getGameTime() + INVITE_TICKS));
        leader.sendSystemMessage(Component.translatable("message.heroicpaths.party.invite_sent", target.getDisplayName())
                .withStyle(ChatFormatting.GRAY));
        MutableComponent accept = Component.translatable("message.heroicpaths.party.accept_button")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party accept")));
        MutableComponent decline = Component.translatable("message.heroicpaths.party.decline_button")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.RED).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party decline")));
        target.sendSystemMessage(Component.translatable("message.heroicpaths.party.invited", leader.getDisplayName())
                .withStyle(ChatFormatting.GOLD).append(" ").append(accept).append(" ").append(decline));
        syncAll(leader.server);
        return true;
    }

    public static boolean accept(ServerPlayer player) {
        PartyData data = PartyData.get(player.server);
        Invite invite = INVITES.remove(player.getUUID());
        Party party = invite == null ? null : data.byId(invite.partyId());
        if (invite == null || invite.expiresAt() < player.server.overworld().getGameTime() || party == null) {
            fail(player, "message.heroicpaths.party.no_invite");
            return false;
        }
        if (data.partyOf(player.getUUID()) != null) {
            fail(player, "message.heroicpaths.party.already_in_party");
            return false;
        }
        if (party.isFull()) {
            player.sendSystemMessage(Component.translatable("message.heroicpaths.party.full", Party.MAX_SIZE).withStyle(ChatFormatting.RED));
            return false;
        }
        data.join(party, player.getUUID(), player.getGameProfile().getName(), level(player));
        notifyParty(player.server, party, Component.translatable("message.heroicpaths.party.joined", player.getDisplayName())
                .withStyle(ChatFormatting.GREEN));
        syncAll(player.server);
        return true;
    }

    public static boolean decline(ServerPlayer player) {
        Invite invite = INVITES.remove(player.getUUID());
        if (invite == null) {
            fail(player, "message.heroicpaths.party.no_invite");
            return false;
        }
        ServerPlayer inviter = player.server.getPlayerList().getPlayer(invite.inviter());
        if (inviter != null) {
            inviter.sendSystemMessage(Component.translatable("message.heroicpaths.party.declined", player.getDisplayName())
                    .withStyle(ChatFormatting.GRAY));
        }
        player.sendSystemMessage(Component.translatable("message.heroicpaths.party.you_declined").withStyle(ChatFormatting.GRAY));
        return true;
    }

    public static boolean leave(ServerPlayer player) {
        PartyData data = PartyData.get(player.server);
        Party party = data.partyOf(player.getUUID());
        if (party == null) {
            fail(player, "message.heroicpaths.party.not_in_party");
            return false;
        }
        UUID oldLeader = party.leader();
        data.leave(player.getUUID());
        player.sendSystemMessage(Component.translatable("message.heroicpaths.party.you_left").withStyle(ChatFormatting.GRAY));
        notifyParty(player.server, party, Component.translatable("message.heroicpaths.party.left", player.getDisplayName())
                .withStyle(ChatFormatting.YELLOW));
        announceLeader(player.server, party, oldLeader);
        sendEmpty(player);
        syncAll(player.server);
        return true;
    }

    public static boolean kick(ServerPlayer leader, UUID target) {
        PartyData data = PartyData.get(leader.server);
        Party party = data.partyOf(leader.getUUID());
        if (party == null) {
            fail(leader, "message.heroicpaths.party.not_in_party");
            return false;
        }
        if (!party.isLeader(leader.getUUID())) {
            fail(leader, "message.heroicpaths.party.not_leader");
            return false;
        }
        if (target.equals(leader.getUUID()) || !party.contains(target)) {
            fail(leader, "message.heroicpaths.party.not_member");
            return false;
        }
        String name = party.name(target);
        data.leave(target);
        notifyParty(leader.server, party, Component.translatable("message.heroicpaths.party.kicked", name).withStyle(ChatFormatting.YELLOW));
        ServerPlayer kicked = leader.server.getPlayerList().getPlayer(target);
        if (kicked != null) {
            kicked.sendSystemMessage(Component.translatable("message.heroicpaths.party.you_were_kicked").withStyle(ChatFormatting.RED));
            sendEmpty(kicked);
        }
        syncAll(leader.server);
        return true;
    }

    public static boolean disband(ServerPlayer leader) {
        PartyData data = PartyData.get(leader.server);
        Party party = data.partyOf(leader.getUUID());
        if (party == null) {
            fail(leader, "message.heroicpaths.party.not_in_party");
            return false;
        }
        if (!party.isLeader(leader.getUUID())) {
            fail(leader, "message.heroicpaths.party.not_leader");
            return false;
        }
        List<UUID> members = List.copyOf(party.members());
        data.disband(party);
        for (UUID member : members) {
            ServerPlayer online = leader.server.getPlayerList().getPlayer(member);
            if (online == null) continue;
            online.sendSystemMessage(Component.translatable("message.heroicpaths.party.disbanded").withStyle(ChatFormatting.YELLOW));
            sendEmpty(online);
        }
        return true;
    }

    /** Состав группы в чат: уровни, лидер, кто не в сети, средний уровень. */
    public static boolean list(ServerPlayer player) {
        Party party = partyOf(player);
        if (party == null) {
            player.sendSystemMessage(Component.translatable("message.heroicpaths.party.solo", level(player)).withStyle(ChatFormatting.GRAY));
            return false;
        }
        player.sendSystemMessage(Component.translatable("message.heroicpaths.party.list_header", party.size(), Party.MAX_SIZE,
                format(averageLevel(player.server, party))).withStyle(ChatFormatting.GOLD));
        for (UUID member : party.members()) {
            ServerPlayer online = player.server.getPlayerList().getPlayer(member);
            MutableComponent line = Component.literal(" • " + party.name(member) + " ")
                    .append(Component.translatable("hud.heroicpaths.level", memberLevel(player.server, party, member)));
            if (party.isLeader(member)) line.append(" ").append(Component.translatable("message.heroicpaths.party.leader_tag"));
            if (online == null) line.append(" ").append(Component.translatable("message.heroicpaths.party.offline_tag"));
            player.sendSystemMessage(line.withStyle(online == null ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE));
        }
        return true;
    }

    private static void announceLeader(MinecraftServer server, Party party, UUID oldLeader) {
        if (party.size() > 0 && !party.leader().equals(oldLeader)) {
            notifyParty(server, party, Component.translatable("message.heroicpaths.party.new_leader", party.name(party.leader()))
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    private static void notifyParty(MinecraftServer server, Party party, Component message) {
        for (UUID member : party.members()) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) online.sendSystemMessage(message);
        }
    }

    private static void fail(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.RED));
    }

    // ================================================================ синхронизация

    /** Отправляет каждому участнику в сети состав его группы. */
    public static void syncAll(MinecraftServer server) {
        PartyData data = PartyData.get(server);
        for (Party party : data.all()) {
            List<PartySyncPayload.Member> entries = new ArrayList<>();
            for (UUID member : party.members()) {
                ServerPlayer online = server.getPlayerList().getPlayer(member);
                if (online != null) {
                    data.remember(party, member, online.getGameProfile().getName(), level(online));
                    RpgData rpg = RpgManager.get(online);
                    PlayerClass playerClass = rpg.getPlayerClass();
                    entries.add(new PartySyncPayload.Member(party.name(member), level(online), online.getHealth(), online.getMaxHealth(),
                            true, party.isLeader(member), playerClass == PlayerClass.NONE ? 0xAAAAAA : playerClass.color()));
                } else {
                    entries.add(new PartySyncPayload.Member(party.name(member), party.cachedLevel(member), 0, 20,
                            false, party.isLeader(member), 0x777777));
                }
            }
            PartySyncPayload payload = new PartySyncPayload(entries);
            for (UUID member : party.members()) {
                ServerPlayer online = server.getPlayerList().getPlayer(member);
                if (canReceive(online)) PacketDistributor.sendToPlayer(online, payload);
            }
        }
    }

    private static void sendEmpty(ServerPlayer player) {
        if (canReceive(player)) PacketDistributor.sendToPlayer(player, new PartySyncPayload(List.of()));
    }

    private static boolean canReceive(@Nullable ServerPlayer player) {
        return player != null && player.connection != null && player.connection.hasChannel(PartySyncPayload.TYPE);
    }

    // ================================================================ события

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        long now = server.overworld().getGameTime();
        INVITES.values().removeIf(invite -> invite.expiresAt() < now);
        syncAll(server);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PartyData data = PartyData.get(player.server);
        Party party = data.partyOf(player.getUUID());
        if (party != null) data.remember(party, player.getUUID(), player.getGameProfile().getName(), level(player));
        INVITES.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        INVITES.clear();
    }

    /** Огонь по своим отключён: участники одной группы не ранят друг друга ни оружием, ни снарядами, ни способностями. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && event.getSource().getEntity() instanceof ServerPlayer attacker
                && attacker != victim && sameParty(attacker, victim)) {
            event.setCanceled(true);
        }
    }
}
