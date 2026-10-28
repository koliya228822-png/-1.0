package com.heroicpaths.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Смерть в данже. Пока в проходе есть живой соратник, погибший не умирает, а падает: становится духом
 * (режим наблюдателя) и сохраняет все вещи. Соратник может поднять его свитком воскрешения, а победа над боссом
 * поднимает всех павших. Если пали все — смерть окончательная: павшие умирают по-настоящему там, где упали
 * (вещи выпадают в данже, как при обычной смерти), и возрождаются на своей точке спавна в обычном мире.
 */
public final class DungeonRevival {
    /** Метка павшего: номер прохода, прежний режим игры и место падения. */
    private static final String FALLEN = "heroicpaths.fallen";
    /** Эта смерть окончательная: обработчик её пропускает. */
    private static final String FINAL = "heroicpaths.final_death";

    private DungeonRevival() {}

    public static boolean isFallen(ServerPlayer player) {
        return player.getPersistentData().contains(FALLEN);
    }

    // ================================================================ падение

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.getPersistentData().getBoolean(FINAL)) {
            player.getPersistentData().remove(FINAL);
            return;
        }
        if (!DungeonManager.isDungeonLevel(player.level()) || isFallen(player)) return;
        DungeonInstance instance = DungeonManager.instanceAt(player.server, player.blockPosition());
        if (instance == null || instance.isCompleted() || !instance.isInRoster(player.getUUID())) return;
        if (living(player.serverLevel(), instance, player).isEmpty()) {
            // Пал последний: его смерть идёт своим чередом, павшие до него умирают вместе с ним.
            wipe(player.serverLevel(), instance);
            return;
        }
        event.setCanceled(true);
        fall(player, instance);
    }

    /** Игрок падает: полное здоровье, режим наблюдателя, вещи остаются при нём. */
    static void fall(ServerPlayer player, DungeonInstance instance) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("instance", instance.index);
        tag.putString("mode", player.gameMode.getGameModeForPlayer().getName());
        BlockPos at = player.blockPosition();
        if (at.getY() <= player.level().getMinBuildHeight()) at = instance.arrival;
        tag.putInt("x", at.getX());
        tag.putInt("y", at.getY());
        tag.putInt("z", at.getZ());
        player.getPersistentData().put(FALLEN, tag);
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
        player.clearFire();
        player.setAirSupply(player.getMaxAirSupply());
        player.fallDistance = 0;
        player.setGameMode(GameType.SPECTATOR);
        instance.fallen().add(player.getUUID());
        DungeonData.get(player.server).setDirty();
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.8, 0.4, 0.03);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SKELETON_DEATH, SoundSource.PLAYERS, 0.8f, 1.2f);
        player.sendSystemMessage(Component.translatable("message.heroicpaths.fallen.you").withStyle(ChatFormatting.RED));
        for (ServerPlayer ally : DungeonManager.membersInside(level, instance)) {
            if (ally != player) ally.sendSystemMessage(Component.translatable("message.heroicpaths.fallen.ally", player.getDisplayName())
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** Живые участники прохода внутри него, кроме except. */
    static List<ServerPlayer> living(ServerLevel level, DungeonInstance instance, @Nullable ServerPlayer except) {
        List<ServerPlayer> alive = new ArrayList<>();
        for (ServerPlayer player : DungeonManager.membersInside(level, instance)) {
            if (player != except && player.isAlive() && !player.isSpectator() && !instance.fallen().contains(player.getUUID())) alive.add(player);
        }
        return alive;
    }

    // ================================================================ воскрешение

    /** Читает свиток: поднимает ближайшего павшего соратника рядом с читающим. Возвращает причину отказа или null. */
    @Nullable
    public static Component useScroll(ServerPlayer reader) {
        if (!DungeonManager.isDungeonLevel(reader.level())) return Component.translatable("message.heroicpaths.resurrect.not_dungeon");
        DungeonInstance instance = DungeonManager.instanceAt(reader.server, reader.blockPosition());
        if (instance == null || isFallen(reader) || reader.isSpectator()) return Component.translatable("message.heroicpaths.resurrect.not_dungeon");
        ServerPlayer target = null;
        for (UUID uuid : instance.fallen()) {
            ServerPlayer fallen = reader.server.getPlayerList().getPlayer(uuid);
            if (fallen == null || fallen == reader) continue;
            if (target == null || fallen.distanceToSqr(reader) < target.distanceToSqr(reader)) target = fallen;
        }
        if (target == null) return Component.translatable("message.heroicpaths.resurrect.nobody");
        revive(target, instance, reader.serverLevel(), reader.position());
        for (ServerPlayer ally : DungeonManager.membersInside(reader.serverLevel(), instance)) {
            ally.sendSystemMessage(Component.translatable("message.heroicpaths.resurrect.done", reader.getDisplayName(), target.getDisplayName())
                    .withStyle(ChatFormatting.GREEN));
        }
        return null;
    }

    /** Босс повержен: все павшие в сети встают у врат домой (кто не в сети — встанет при входе). */
    public static void reviveAll(ServerLevel level, DungeonInstance instance) {
        for (UUID uuid : List.copyOf(instance.fallen())) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) revive(player, instance, level, Vec3.atBottomCenterOf(instance.bossArrival));
        }
    }

    /** Поднимает павшего в точке at с половиной здоровья и прежним режимом игры. */
    static void revive(ServerPlayer player, DungeonInstance instance, ServerLevel level, Vec3 at) {
        instance.fallen().remove(player.getUUID());
        DungeonData.get(level.getServer()).setDirty();
        restoreMode(player);
        player.teleportTo(level, at.x, at.y, at.z, player.getYRot(), player.getXRot());
        player.setHealth(Math.max(1f, player.getMaxHealth() / 2f));
        player.fallDistance = 0;
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y + 1, at.z, 60, 0.5, 1, 0.5, 0.3);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.1f);
        player.sendSystemMessage(Component.translatable("message.heroicpaths.resurrect.you").withStyle(ChatFormatting.GREEN));
    }

    /** Возвращает режим игры, что был до падения, и снимает метку павшего. */
    private static void restoreMode(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(FALLEN);
        player.getPersistentData().remove(FALLEN);
        GameType mode = GameType.byName(tag.getString("mode"), GameType.SURVIVAL);
        player.setGameMode(mode == GameType.SPECTATOR ? GameType.SURVIVAL : mode);
    }

    // ================================================================ окончательная смерть

    /** Пала вся группа: павшие в сети умирают окончательно, остальные — когда войдут. */
    static void wipe(ServerLevel level, DungeonInstance instance) {
        for (UUID uuid : List.copyOf(instance.fallen())) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) finalDeath(player, instance);
            else instance.lost().add(uuid);
        }
        instance.fallen().clear();
        DungeonData.get(level.getServer()).setDirty();
    }

    /** Окончательная смерть павшего: вернуться туда, где упал, и умереть — вещи выпадают в данже, возрождение дома. */
    static void finalDeath(ServerPlayer player, DungeonInstance instance) {
        instance.fallen().remove(player.getUUID());
        instance.lost().remove(player.getUUID());
        DungeonData.get(player.server).setDirty();
        CompoundTag tag = player.getPersistentData().getCompound(FALLEN);
        restoreMode(player);
        ServerLevel dungeon = DungeonManager.dungeonLevel(player.server);
        if (dungeon != null) {
            BlockPos at = tag.contains("x") ? new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")) : instance.arrival;
            player.teleportTo(dungeon, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot(), player.getXRot());
        }
        player.sendSystemMessage(Component.translatable("message.heroicpaths.fallen.final").withStyle(ChatFormatting.DARK_RED));
        player.getPersistentData().putBoolean(FINAL, true);
        player.setHealth(0f);
        player.die(player.damageSources().genericKill());
        player.getPersistentData().remove(FINAL);
    }

    // ================================================================ присмотр

    /** Раз в секунду: если в проходе не осталось живых (вышли, отключились), павшие умирают окончательно. */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        ServerLevel level = DungeonManager.dungeonLevel(server);
        if (level == null) return;
        for (DungeonInstance instance : DungeonData.get(server).instances()) {
            if (instance.fallen().isEmpty() || instance.isCompleted()) continue;
            if (living(level, instance, null).isEmpty()) wipe(level, instance);
        }
    }

    /** Дух не покидает свой проход: телепорт наблюдателя к игроку в другом мире или уход далеко возвращают его на место. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0 || !isFallen(player)) return;
        CompoundTag tag = player.getPersistentData().getCompound(FALLEN);
        ServerLevel dungeon = DungeonManager.dungeonLevel(player.server);
        if (dungeon == null) return;
        DungeonInstance here = player.level() == dungeon ? DungeonManager.instanceAt(player.server, player.blockPosition()) : null;
        if (here != null && here.index == tag.getInt("instance")) return;
        player.teleportTo(dungeon, tag.getInt("x") + 0.5, tag.getInt("y"), tag.getInt("z") + 0.5, player.getYRot(), player.getXRot());
    }

    /** Вход павшего: босс уже повержен — встаёт, группа пала без него — умирает, иначе остаётся духом. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isFallen(player)) return;
        DungeonInstance instance = DungeonData.get(player.server).get(player.getPersistentData().getCompound(FALLEN).getInt("instance"));
        ServerLevel dungeon = DungeonManager.dungeonLevel(player.server);
        if (instance == null || dungeon == null) {
            restoreMode(player);
        } else if (instance.lost().contains(player.getUUID())) {
            finalDeath(player, instance);
        } else if (instance.isCompleted() || !instance.fallen().contains(player.getUUID())) {
            revive(player, instance, dungeon, Vec3.atBottomCenterOf(instance.isCompleted() ? instance.bossArrival : instance.arrival));
        } else if (living(dungeon, instance, player).isEmpty()) {
            wipe(dungeon, instance);
        } else {
            player.setGameMode(GameType.SPECTATOR);
        }
    }
}
