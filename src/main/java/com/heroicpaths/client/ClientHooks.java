package com.heroicpaths.client;

import com.heroicpaths.network.PartySyncPayload;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Действия, которые сеть вызывает только на клиенте. */
public final class ClientHooks {
    /** Состав группы игрока, присланный сервером. Пустой — игрок не в группе. */
    private static List<PartySyncPayload.Member> party = List.of();

    private ClientHooks() {}

    public static void openQuestBoard() {
        Minecraft.getInstance().setScreen(new QuestBoardScreen());
    }

    public static void openStation(int station, net.minecraft.core.BlockPos pos) {
        com.heroicpaths.craft.Station[] stations = com.heroicpaths.craft.Station.values();
        if (station < 0 || station >= stations.length) return;
        Minecraft.getInstance().setScreen(new StationScreen(stations[station], pos));
    }

    public static void openOrderBoard(net.minecraft.core.BlockPos pos, net.minecraft.nbt.CompoundTag offersTag) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        List<com.heroicpaths.guild.Order> offers = new java.util.ArrayList<>();
        for (net.minecraft.nbt.Tag tag : offersTag.getList("offers", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            com.heroicpaths.guild.Order order = com.heroicpaths.guild.Order.load(minecraft.level.registryAccess(), (net.minecraft.nbt.CompoundTag) tag);
            if (order != null) offers.add(order);
        }
        minecraft.setScreen(new OrderBoardScreen(pos, offers));
    }

    public static void setParty(List<PartySyncPayload.Member> members) {
        party = List.copyOf(members);
    }

    public static List<PartySyncPayload.Member> party() {
        return party;
    }

    /** Средний уровень группы так же, как его считает сервер; без группы — собственный уровень. */
    public static double partyAverageLevel(int ownLevel) {
        if (party.isEmpty()) return Math.max(1, ownLevel);
        return party.stream().mapToInt(member -> Math.max(1, member.level())).average().orElse(1);
    }

    public static int partySize() {
        return Math.max(1, party.size());
    }
}
