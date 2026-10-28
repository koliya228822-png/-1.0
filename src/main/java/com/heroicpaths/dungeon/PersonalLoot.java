package com.heroicpaths.dungeon;

import com.heroicpaths.HeroicPaths;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Личная добыча в сундуках данжей (и в склепе скелетов в обычном мире): каждый игрок, открывший сундук,
 * видит своё содержимое, сгенерированное из той же таблицы добычи только для него. Общий сундук остаётся пустым.
 * При первом открытии таблица снимается с сундука и запоминается здесь, а для каждого игрока хранится его доля.
 */
public final class PersonalLoot extends SavedData {
    private static final String NAME = "heroicpaths_personal_loot";
    public static final int SIZE = 27;

    private final Map<String, Chest> chests = new HashMap<>();

    /** Сундук с личной добычей: откуда брать добычу и что осталось у каждого игрока. */
    private static final class Chest {
        final ResourceKey<LootTable> table;
        final long seed;
        final Map<UUID, Share> shares = new HashMap<>();
        /** Сколько игроков сейчас смотрят в сундук: крышка закрывается за последним. */
        int viewers;

        Chest(ResourceKey<LootTable> table, long seed) {
            this.table = table;
            this.seed = seed;
        }
    }

    /** Доля одного игрока. Любое изменение сохраняется вместе с миром. */
    private final class Share extends SimpleContainer {
        private final Chest chest;
        /** Где стоит сундук: задаётся при каждом открытии (при загрузке мира он ещё не известен). */
        private Level level;
        private BlockPos pos = BlockPos.ZERO;

        Share(Chest chest) {
            super(SIZE);
            this.chest = chest;
        }

        Share bind(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos.immutable();
            return this;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            PersonalLoot.this.setDirty();
        }

        @Override
        public boolean stillValid(Player player) {
            return level != null && level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity
                    && player.distanceToSqr(Vec3.atCenterOf(pos)) <= 64;
        }

        @Override
        public void startOpen(Player player) {
            if (chest.viewers++ == 0) level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.5f, 0.95f);
            level.blockEvent(pos, level.getBlockState(pos).getBlock(), 1, chest.viewers);
        }

        @Override
        public void stopOpen(Player player) {
            chest.viewers = Math.max(0, chest.viewers - 1);
            if (chest.viewers == 0) level.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.5f, 0.95f);
            level.blockEvent(pos, level.getBlockState(pos).getBlock(), 1, chest.viewers);
        }
    }

    public static PersonalLoot get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(PersonalLoot::new, PersonalLoot::load), NAME);
    }

    private static String key(Level level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    /** Сундуки данжей: таблицы chests/<тема> и chests/<тема>_treasure всех данжей и склепа. */
    public static boolean isDungeonTable(ResourceKey<LootTable> table) {
        ResourceLocation id = table.location();
        if (!id.getNamespace().equals(HeroicPaths.MODID) || !id.getPath().startsWith("chests/")) return false;
        String name = id.getPath().substring("chests/".length());
        if (name.endsWith("_treasure")) name = name.substring(0, name.length() - "_treasure".length());
        Set<String> themes = new HashSet<>();
        themes.add("crypt");
        for (DungeonType type : DungeonType.values()) themes.add(type.theme());
        return themes.contains(name);
    }

    /**
     * Доля игрока в сундуке на pos; при первом обращении к сундуку данжа он становится личным, при первом обращении
     * игрока его доля генерируется. null — это не сундук с личной добычей.
     */
    @Nullable
    public static Container containerFor(ServerPlayer player, ServerLevel level, BlockPos pos) {
        PersonalLoot data = get(level.getServer());
        String key = key(level, pos);
        Chest chest = data.chests.get(key);
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof RandomizableContainerBlockEntity container)) return null;
        if (chest == null) {
            ResourceKey<LootTable> table = container.getLootTable();
            if (table == null || !isDungeonTable(table)) return null;
            long seed = container.getLootTableSeed();
            chest = new Chest(table, seed != 0 ? seed : level.getSeed() ^ pos.asLong() * 31);
            data.chests.put(key, chest);
            // Общий сундук больше не наполняется: добыча теперь у каждого своя.
            container.setLootTable(null);
            container.setChanged();
            data.setDirty();
        }
        Share share = chest.shares.get(player.getUUID());
        if (share == null) {
            share = data.new Share(chest);
            LootTable table = level.getServer().reloadableRegistries().getLootTable(chest.table);
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .withLuck(player.getLuck())
                    .create(LootContextParamSets.CHEST);
            UUID uuid = player.getUUID();
            table.fill(share, params, chest.seed ^ uuid.getMostSignificantBits() ^ Long.rotateLeft(uuid.getLeastSignificantBits(), 17));
            chest.shares.put(uuid, share);
            data.setDirty();
        }
        return share.bind(level, pos);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level) || player.isSpectator()) return;
        // Присевший игрок с предметом в руке ставит блок, а не открывает сундук.
        if (player.isSecondaryUseActive() && !(player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty())) return;
        BlockPos pos = event.getPos();
        Container share = containerFor(player, level, pos);
        if (share == null) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        Component name = level.getBlockEntity(pos) instanceof Nameable nameable ? nameable.getDisplayName()
                : Component.translatable("container.chest");
        Component title = Component.translatable("container.heroicpaths.personal_chest", name);
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> ChestMenu.threeRows(id, inventory, share), title));
    }

    // ================================================================ сохранение

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        chests.forEach((key, chest) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", key);
            entry.putString("table", chest.table.location().toString());
            entry.putLong("seed", chest.seed);
            ListTag shares = new ListTag();
            chest.shares.forEach((player, share) -> {
                CompoundTag s = new CompoundTag();
                s.putUUID("player", player);
                ContainerHelper.saveAllItems(s, share.getItems(), registries);
                shares.add(s);
            });
            entry.put("shares", shares);
            list.add(entry);
        });
        tag.put("chests", list);
        return tag;
    }

    private static PersonalLoot load(CompoundTag tag, HolderLookup.Provider registries) {
        PersonalLoot data = new PersonalLoot();
        for (Tag t : tag.getList("chests", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) t;
            String key = entry.getString("key");
            ResourceLocation table = ResourceLocation.tryParse(entry.getString("table"));
            if (table == null) continue;
            Chest chest = new Chest(ResourceKey.create(Registries.LOOT_TABLE, table), entry.getLong("seed"));
            data.chests.put(key, chest);
            for (Tag s : entry.getList("shares", Tag.TAG_COMPOUND)) {
                CompoundTag shareTag = (CompoundTag) s;
                Share share = data.new Share(chest);
                ContainerHelper.loadAllItems(shareTag, share.getItems(), registries);
                chest.shares.put(shareTag.getUUID("player"), share);
            }
        }
        return data;
    }
}
