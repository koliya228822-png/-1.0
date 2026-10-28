package com.heroicpaths.travel;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.dungeon.DungeonManager;
import com.heroicpaths.network.WaystoneListPayload;
import com.heroicpaths.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Точки быстрого перемещения: все поставленные в мире камни перемещения. Игрок знает точку, которую сам поставил или
 * хоть раз активировал правым кликом; между известными точками можно перемещаться — от камня бесплатно, свитком
 * перемещения — откуда угодно за один свиток. В данжах ни то ни другое не работает.
 */
public final class Waystones extends SavedData {
    private static final String NAME = HeroicPaths.MODID + "_waystones";
    /** Длина названия точки. */
    public static final int MAX_NAME = 32;
    /** Насколько близко надо стоять к камню, чтобы переместиться от него. */
    private static final double USE_DISTANCE = 8;

    /** Одна точка: где стоит, как называется, кто поставил и кто её знает. */
    public static final class Entry {
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        String name;
        final UUID owner;
        final Set<UUID> known = new HashSet<>();

        Entry(ResourceKey<Level> dimension, BlockPos pos, String name, UUID owner) {
            this.dimension = dimension;
            this.pos = pos.immutable();
            this.name = name;
            this.owner = owner;
        }

        public String name() {
            return name;
        }

        public BlockPos pos() {
            return pos;
        }

        public ResourceKey<Level> dimension() {
            return dimension;
        }
    }

    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public static Waystones get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Waystones::new, Waystones::load), NAME);
    }

    private static String key(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.location() + "|" + pos.asLong();
    }

    public Entry at(ResourceKey<Level> dimension, BlockPos pos) {
        return entries.get(key(dimension, pos));
    }

    /** Новый камень: название — с переименованного предмета или по координатам. Поставивший сразу его знает. */
    public Entry add(ServerLevel level, BlockPos pos, Player placer, ItemStack stack) {
        String name = stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)
                ? clean(stack.getHoverName().getString())
                : Component.translatable("block.heroicpaths.waystone.default_name", pos.getX(), pos.getZ()).getString();
        Entry entry = new Entry(level.dimension(), pos, name, placer == null ? null : placer.getUUID());
        if (placer != null) entry.known.add(placer.getUUID());
        entries.put(key(level.dimension(), pos), entry);
        setDirty();
        return entry;
    }

    public void remove(ResourceKey<Level> dimension, BlockPos pos) {
        if (entries.remove(key(dimension, pos)) != null) setDirty();
    }

    public static String clean(String name) {
        String trimmed = name.strip();
        if (trimmed.length() > MAX_NAME) trimmed = trimmed.substring(0, MAX_NAME);
        return trimmed;
    }

    /** Известные игроку точки, ближние к нему — первыми. */
    public List<Entry> known(ServerPlayer player) {
        List<Entry> list = new ArrayList<>();
        for (Entry entry : entries.values()) if (entry.known.contains(player.getUUID())) list.add(entry);
        list.sort(Comparator.comparing((Entry e) -> e.dimension != player.level().dimension())
                .thenComparingDouble(e -> e.pos.distToCenterSqr(player.position())));
        return list;
    }

    // ---------------------------------------------------------------- действия

    /** Правый клик по камню: точка становится известной, открывается список точек. */
    public static void activate(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        Waystones data = get(level.getServer());
        Entry entry = data.at(level.dimension(), pos);
        if (entry == null) {
            // Камень из постройки (зал гильдии): никто его не ставил, название — по деревне.
            entry = data.add(level, pos, null, ItemStack.EMPTY);
            entry.name = Component.translatable("block.heroicpaths.waystone.guild_name", pos.getX(), pos.getZ()).getString();
        }
        if (entry.known.add(player.getUUID())) {
            data.setDirty();
            player.displayClientMessage(Component.translatable("message.heroicpaths.waystone_discovered", entry.name).withStyle(ChatFormatting.GREEN), true);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 1.2f);
        }
        send(player, WaystoneListPayload.FROM_BLOCK, pos);
    }

    /** Свиток: открыть список точек, если перемещаться отсюда можно. */
    public static boolean openScroll(ServerPlayer player) {
        Component problem = blocked(player);
        if (problem == null && get(player.server).known(player).isEmpty()) problem = Component.translatable("message.heroicpaths.waystone_none");
        if (problem != null) {
            player.displayClientMessage(problem.copy().withStyle(ChatFormatting.RED), true);
            return false;
        }
        send(player, WaystoneListPayload.FROM_SCROLL, BlockPos.ZERO);
        return true;
    }

    /** Список известных точек на клиент: для миникарты и (при {@code mode} ≠ SYNC) для окна выбора. */
    public static void send(ServerPlayer player, int mode, BlockPos source) {
        if (player.connection == null || !player.connection.hasChannel(WaystoneListPayload.TYPE)) return;
        List<WaystoneListPayload.Point> points = new ArrayList<>();
        Waystones data = get(player.server);
        for (Entry entry : data.known(player)) {
            points.add(new WaystoneListPayload.Point(entry.dimension.location(), entry.pos, entry.name,
                    entry.owner != null && entry.owner.equals(player.getUUID())));
        }
        if (player.connection != null && player.connection.hasChannel(WaystoneListPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new WaystoneListPayload(mode, source, points));
        }
    }

    /** Почему отсюда нельзя переместиться (или null). */
    private static Component blocked(ServerPlayer player) {
        if (DungeonManager.isDungeonLevel(player.level())) return Component.translatable("message.heroicpaths.waystone_dungeon");
        return null;
    }

    /** Переименовать свою точку (стоя рядом с ней). */
    public static void rename(ServerPlayer player, BlockPos pos, String name) {
        Waystones data = get(player.server);
        Entry entry = data.at(player.level().dimension(), pos);
        String cleaned = clean(name);
        if (entry == null || cleaned.isEmpty() || pos.distToCenterSqr(player.position()) > USE_DISTANCE * USE_DISTANCE) return;
        if (entry.owner != null && !entry.owner.equals(player.getUUID()) && !player.hasPermissions(2)) return;
        entry.name = cleaned;
        data.setDirty();
        send(player, WaystoneListPayload.FROM_BLOCK, pos);
    }

    /**
     * Перемещение к точке {@code target}: от камня {@code source} (игрок рядом с ним) бесплатно или свитком из руки.
     * Возвращает текст ошибки или null.
     */
    public static Component travel(ServerPlayer player, ResourceLocation dimension, BlockPos target, boolean scroll, BlockPos source) {
        Component problem = blocked(player);
        if (problem != null) return problem;
        MinecraftServer server = player.server;
        Waystones data = get(server);
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimension);
        Entry entry = data.at(key, target);
        if (entry == null || !entry.known.contains(player.getUUID())) return Component.translatable("message.heroicpaths.waystone_unknown");
        ServerLevel level = server.getLevel(key);
        if (level == null || DungeonManager.isDungeonLevel(level)) return Component.translatable("message.heroicpaths.waystone_unknown");
        if (!level.getBlockState(target).is(ModBlocks.WAYSTONE.get())) {
            data.remove(key, target);
            return Component.translatable("message.heroicpaths.waystone_gone", entry.name);
        }
        ItemStack paid = ItemStack.EMPTY;
        if (scroll) {
            paid = scrollInHand(player);
            if (paid.isEmpty()) return Component.translatable("message.heroicpaths.waystone_no_scroll");
        } else {
            Entry from = data.at(player.level().dimension(), source);
            if (from == null || source.distToCenterSqr(player.position()) > USE_DISTANCE * USE_DISTANCE) {
                return Component.translatable("message.heroicpaths.waystone_too_far");
            }
            if (from == entry) return Component.translatable("message.heroicpaths.waystone_here");
        }
        BlockPos landing = landing(level, target);
        effects(player.serverLevel(), player.blockPosition());
        player.stopRiding();
        player.teleportTo(level, landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5, player.getYRot(), player.getXRot());
        player.resetFallDistance();
        effects(level, landing);
        if (!paid.isEmpty()) paid.consume(1, player);
        player.displayClientMessage(Component.translatable("message.heroicpaths.waystone_arrived", entry.name).withStyle(ChatFormatting.GREEN), true);
        return null;
    }

    private static ItemStack scrollInHand(ServerPlayer player) {
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof TeleportScrollItem) return stack;
        }
        return player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.CREATIVE ? new ItemStack(com.heroicpaths.registry.ModItems.TELEPORT_SCROLL.get()) : ItemStack.EMPTY;
    }

    /** Свободное место рядом с камнем: сверху или с любой стороны. */
    static BlockPos landing(ServerLevel level, BlockPos stone) {
        BlockPos[] spots = {stone.above(), stone.north(), stone.south(), stone.east(), stone.west(),
                stone.north().east(), stone.north().west(), stone.south().east(), stone.south().west()};
        for (BlockPos spot : spots) {
            if (free(level, spot) && free(level, spot.above())) return spot;
        }
        return stone.above();
    }

    private static boolean free(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }

    private static void effects(ServerLevel level, BlockPos pos) {
        level.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 40, 0.4, 0.8, 0.4, 0.3);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 12, 0.5, 0.8, 0.5, 0);
        level.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
    }

    /** При входе игрок получает свои точки: они видны на миникарте. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player, WaystoneListPayload.SYNC, BlockPos.ZERO);
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) send(player, WaystoneListPayload.SYNC, BlockPos.ZERO);
    }

    // ---------------------------------------------------------------- сохранение

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry entry : entries.values()) {
            CompoundTag e = new CompoundTag();
            e.putString("dim", entry.dimension.location().toString());
            e.putLong("pos", entry.pos.asLong());
            e.putString("name", entry.name);
            if (entry.owner != null) e.putUUID("owner", entry.owner);
            ListTag known = new ListTag();
            for (UUID id : entry.known) {
                CompoundTag k = new CompoundTag();
                k.putUUID("id", id);
                known.add(k);
            }
            e.put("known", known);
            list.add(e);
        }
        tag.put("waystones", list);
        return tag;
    }

    private static Waystones load(CompoundTag tag, HolderLookup.Provider registries) {
        Waystones data = new Waystones();
        for (Tag t : tag.getList("waystones", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            ResourceLocation dim = ResourceLocation.tryParse(e.getString("dim"));
            if (dim == null) continue;
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dim);
            Entry entry = new Entry(key, BlockPos.of(e.getLong("pos")), e.getString("name"), e.hasUUID("owner") ? e.getUUID("owner") : null);
            for (Tag k : e.getList("known", Tag.TAG_COMPOUND)) entry.known.add(((CompoundTag) k).getUUID("id"));
            data.entries.put(key(key, entry.pos), entry);
        }
        return data;
    }
}
