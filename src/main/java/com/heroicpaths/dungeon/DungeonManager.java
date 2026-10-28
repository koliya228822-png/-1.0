package com.heroicpaths.dungeon;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.DungeonBoss;
import com.heroicpaths.party.Party;
import com.heroicpaths.party.PartyManager;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Измерение данжей: создание проходов, проверки и вход, масштабирование врагов под группу,
 * награды за босса и выход.
 */
public final class DungeonManager {
    public static final ResourceKey<Level> DUNGEON_LEVEL = ResourceKey.create(Registries.DIMENSION, HeroicPaths.id("dungeons"));
    /** Проходы стоят в 1024 блоках друг от друга, чтобы не пересекаться. */
    public static final int SPACING = 1024;
    private static final String PORTAL_COOLDOWN = "heroicpaths.portal_cooldown";
    private static final ResourceLocation HEALTH_SCALE = HeroicPaths.id("dungeon_health");
    private static final ResourceLocation DAMAGE_SCALE = HeroicPaths.id("dungeon_damage");

    /**
     * Только для игровых тестов: тестовый сервер не создаёт измерения из датапаков, поэтому тест подставляет
     * свой мир на время одной проверки и сразу возвращает null.
     */
    @Nullable
    public static ServerLevel testLevel;

    private DungeonManager() {}

    @Nullable
    public static ServerLevel dungeonLevel(MinecraftServer server) {
        return testLevel != null ? testLevel : server.getLevel(DUNGEON_LEVEL);
    }

    public static boolean isDungeonLevel(Level level) {
        return level.dimension() == DUNGEON_LEVEL || (testLevel != null && level == testLevel);
    }

    @Nullable
    public static DungeonInstance instanceAt(MinecraftServer server, BlockPos pos) {
        return DungeonData.get(server).get(indexAt(pos));
    }

    private static int indexAt(BlockPos pos) {
        return Math.floorDiv(pos.getX() + SPACING / 2, SPACING);
    }

    /** Данж, который строится прямо сейчас: мобы из шаблонов комнат появляются раньше, чем он зарегистрирован. */
    private record Pending(int index, DungeonType type, int partySize, List<java.util.Map.Entry<UUID, Integer>> monsters) {}

    @Nullable
    private static Pending generating;

    /** Тип данжа в этой точке измерения данжей, в том числе того, что строится прямо сейчас; null — данжа тут нет. */
    @Nullable
    public static DungeonType typeAt(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        DungeonInstance instance = instanceAt(level.getServer(), pos);
        if (instance != null) return instance.type;
        return generating != null && generating.index() == indexAt(pos) ? generating.type() : null;
    }

    // ================================================================ проверки доступа

    /** Результат проверки: можно ли войти, а если нельзя — почему. */
    public record Access(boolean allowed, @Nullable Component reason) {
        static final Access OK = new Access(true, null);

        static Access deny(Component reason) {
            return new Access(false, reason);
        }
    }

    /**
     * Проверка уровня: средний уровень текущей группы игрока (сумма уровней всех участников, делённая на их число)
     * не ниже минимального уровня данжа. Считается заново при каждой попытке, поэтому смена состава её не обходит.
     */
    public static Access checkLevel(ServerPlayer player, DungeonType type) {
        double average = PartyManager.averageLevel(player);
        if (average + 1e-6 < type.minLevel()) {
            return Access.deny(Component.translatable("message.heroicpaths.access.level", type.displayName(),
                    type.minLevel(), PartyManager.format(average)));
        }
        return Access.OK;
    }

    /** Ищет в инвентаре свиток нужного данжа. Возвращает номер слота или -1. */
    public static int findScroll(ServerPlayer player, DungeonType type) {
        if (type.scroll() == null) return -1;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(type.scroll())) return slot;
        }
        return -1;
    }

    // ================================================================ создание

    /** Высота пола первого этажа; каждый следующий этаж на FLOOR_STEP ниже, арена — на ARENA_DROP ниже пятого. */
    public static final int TOP_Y = 200, FLOOR_STEP = 16, ARENA_DROP = 40;
    /** Имя jigsaw-якоря в центре площадки спуска и зала у арены (низ шахты с этажа выше). */
    public static final ResourceLocation FLOOR_ANCHOR = HeroicPaths.id("floor_anchor");

    /** Создаёт новый проход для группы и строит данж. Возвращает null, если измерение недоступно или постройка не удалась. */
    @Nullable
    public static DungeonInstance create(MinecraftServer server, DungeonType type, Collection<UUID> roster, @Nullable UUID partyId,
                                         ResourceKey<Level> returnLevel, BlockPos returnPos) {
        ServerLevel level = dungeonLevel(server);
        return level == null ? null : create(level, type, roster, partyId, returnLevel, returnPos);
    }

    @Nullable
    public static DungeonInstance create(ServerLevel level, DungeonType type, Collection<UUID> roster, @Nullable UUID partyId,
                                         ResourceKey<Level> returnLevel, BlockPos returnPos) {
        if (roster.isEmpty()) return null;
        DungeonData data = DungeonData.get(level.getServer());
        int index = data.allocateIndex();
        int party = Math.max(1, Math.min(DungeonType.MAX_PARTY, roster.size()));
        BlockPos origin = new BlockPos(index * SPACING, TOP_Y, 0);
        Built built;
        Pending pending = new Pending(index, type, party, new ArrayList<>());
        generating = pending;
        try {
            built = build(level, type, origin, party);
        } catch (RuntimeException e) {
            HeroicPaths.LOGGER.error("Failed to build dungeon {} #{}", type.id(), index, e);
            return null;
        } finally {
            generating = null;
        }
        if (built == null) return null;
        DungeonInstance instance = new DungeonInstance(index, type, roster, partyId, origin, built.arrival(), built.bossArrival(),
                returnLevel, returnPos);
        data.put(instance);
        trackExitGuards(instance, pending.monsters());
        // Сохраняем сразу: после сбоя игры номер прохода не должен выдаться повторно поверх уже построенного данжа.
        level.getServer().overworld().getDataStorage().save();
        HeroicPaths.LOGGER.info("Dungeon {} #{} built: {} floors, {} pieces, {} monsters at {} for {} players", type.id(), index,
                built.floors().size(), built.pieces().size(), built.monsters(), origin, party);
        return instance;
    }

    /**
     * Запоминает монстров предпоследнего этажа: когда все они погибнут, у входа откроются врата домой.
     * У данжа из одного этажа раннего выхода нет.
     */
    static void trackExitGuards(DungeonInstance instance, List<java.util.Map.Entry<UUID, Integer>> monsters) {
        int exitFloor = exitFloor(instance);
        if (exitFloor < 1 || instance.bossArrival.equals(instance.arrival)) {
            instance.setExit(DungeonInstance.ExitState.CLOSED);
            return;
        }
        for (var monster : monsters) {
            if (floorAt(instance, monster.getValue()) == exitFloor) instance.exitGuards().add(monster.getKey());
        }
    }

    /** Этаж, после зачистки которого можно вернуться через врата у входа: предпоследний (4-й из 5). */
    public static int exitFloor(DungeonInstance instance) {
        return instance.type.floors() - 1;
    }

    /**
     * Построенный данж: комнаты по этажам (последний — арена), точка прибытия на первом этаже, низ шахты на арене
     * и сколько монстров вышло из спавнеров.
     */
    public record Built(List<List<PoolElementStructurePiece>> floors, BlockPos arrival, BlockPos bossArrival, int monsters) {
        public List<PoolElementStructurePiece> pieces() {
            List<PoolElementStructurePiece> all = new ArrayList<>();
            floors.forEach(all::addAll);
            return all;
        }
    }

    /**
     * Строит все этажи данжа сверху вниз. Этаж 1 начинается залом прибытия в origin, этажи 2–5 — площадкой спуска,
     * шестой — ареной босса; каждый ставится якорем точно под залом спуска этажа выше, и между ними прорубается шахта
     * с лестницей. Спавнеры каждого этажа сразу выпускают свою волну. Возвращает null, если этаж не построился.
     */
    @Nullable
    public static Built build(ServerLevel level, DungeonType type, BlockPos origin, int partySize) {
        List<List<PoolElementStructurePiece>> floors = new ArrayList<>();
        BlockPos arrival = null, bossArrival = null, shaftTop = null;
        int monsters = 0;
        for (int floor = 1; floor <= type.floors() + 1; floor++) {
            boolean arena = floor > type.floors();
            List<PoolElementStructurePiece> pieces;
            BlockPos anchor = null;
            if (floor == 1) {
                pieces = generate(level, type.startPool(), origin, null);
            } else {
                anchor = shaftTop.below((arena ? ARENA_DROP : FLOOR_STEP) - 1);
                // Игра ставит стартовую комнату так, что именованный jigsaw оказывается на блок ниже переданной точки
                // (точка — это клетка перед jigsaw, а он смотрит вверх), поэтому передаём точку на блок выше якоря.
                pieces = generate(level, arena ? type.arenaPool() : type.landingPool(), anchor.above(), FLOOR_ANCHOR);
            }
            if (pieces.isEmpty()) {
                HeroicPaths.LOGGER.error("Dungeon {} floor {} was not generated", type.id(), floor);
                return null;
            }
            int sealed = sealOpenings(level, pieces);
            if (sealed > 0) HeroicPaths.LOGGER.info("Dungeon {} floor {}: sealed {} doorways open to the void", type.id(), floor, sealed);
            floors.add(pieces);
            if (floor == 1) {
                BoundingBox start = pieces.get(0).getBoundingBox();
                arrival = new BlockPos(start.getCenter().getX(), start.minY() + 1, start.getCenter().getZ());
            } else {
                carveShaft(level, shaftTop, anchor);
            }
            if (arena) {
                bossArrival = anchor;
                break;
            }
            monsters += convertSpawners(level, pieces, type, partySize, floor);
            shaftTop = descentHole(pieces);
        }
        return new Built(floors, arrival, bossArrival, monsters);
    }

    /**
     * Заделывает проёмы в пустоту. Jigsaw-генератор оставляет проход открытым, если за ним ничего не поместилось
     * (кончилась глубина, дальность или место) — тогда в стене зияет дыра 3×3. Проём, перед которым нет ни одной
     * комнаты этажа, закладывается той же кладкой, что стена рядом. Возвращает число заделанных проёмов.
     */
    public static int sealOpenings(ServerLevel level, List<PoolElementStructurePiece> pieces) {
        int sealed = 0;
        for (PoolElementStructurePiece piece : pieces) {
            var jigsaws = piece.getElement().getShuffledJigsawBlocks(level.getStructureManager(), piece.getPosition(),
                    piece.getRotation(), level.getRandom());
            for (var jigsaw : jigsaws) {
                Direction front = net.minecraft.world.level.block.JigsawBlock.getFrontFacing(jigsaw.state());
                // Вертикальные якоря этажей и уже заложенные проёмы пропускаем.
                if (front.getAxis().isVertical() || !level.getBlockState(jigsaw.pos()).isAir()) continue;
                BlockPos outside = jigsaw.pos().relative(front);
                boolean connected = false;
                for (PoolElementStructurePiece other : pieces) {
                    if (other.getBoundingBox().isInside(outside)) {
                        connected = true;
                        break;
                    }
                }
                if (connected) continue;
                Direction side = front.getClockWise();
                BlockState wall = level.getBlockState(jigsaw.pos().relative(side, 2));
                if (wall.isAir()) wall = level.getBlockState(jigsaw.pos().relative(side, 2).above());
                if (wall.isAir()) wall = Blocks.STONE_BRICKS.defaultBlockState();
                for (int d = -1; d <= 1; d++) {
                    for (int y = 0; y <= 2; y++) level.setBlock(jigsaw.pos().relative(side, d).above(y), wall, Block.UPDATE_CLIENTS);
                }
                sealed++;
            }
        }
        return sealed;
    }

    /** Проём в полу зала спуска (центр пола); если зал не поместился — центр первой комнаты этажа. */
    private static BlockPos descentHole(List<PoolElementStructurePiece> pieces) {
        PoolElementStructurePiece hole = pieces.get(0);
        for (PoolElementStructurePiece piece : pieces) {
            if (piece.getElement().toString().contains("/descent]")) hole = piece;
        }
        BoundingBox box = hole.getBoundingBox();
        return new BlockPos(box.getCenter().getX(), box.minY(), box.getCenter().getZ());
    }

    /**
     * Шахта между этажами: лестница от проёма в полу зала спуска (top) до якоря нижнего этажа (bottom) на опоре с севера.
     * Выше потолка нижнего зала шахта обложена кладкой со всех сторон, чтобы в пустоте не было дыр.
     */
    public static void carveShaft(ServerLevel level, BlockPos top, BlockPos bottom) {
        BlockState wall = level.getBlockState(top.east());
        if (wall.isAir()) wall = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH);
        for (int y = bottom.getY(); y <= top.getY(); y++) {
            BlockPos at = new BlockPos(bottom.getX(), y, bottom.getZ());
            level.setBlock(at.north(), wall, Block.UPDATE_CLIENTS);
            level.setBlock(at, ladder, Block.UPDATE_CLIENTS);
            if (y < bottom.getY() + 6) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos side = at.offset(dx, 0, dz);
                    if ((dx != 0 || dz != 0) && level.getBlockState(side).isAir()) level.setBlock(side, wall, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Строит первый этаж данжа (как генератор jigsaw-структур) и возвращает его комнаты. */
    public static List<PoolElementStructurePiece> generate(ServerLevel level, DungeonType type, BlockPos origin) {
        return generate(level, type.startPool(), origin, null);
    }

    /**
     * Строит одну jigsaw-структуру из пула startPool. Если задан anchor — стартовая комната ставится так,
     * чтобы jigsaw с этим именем оказался ровно в origin (при любом повороте).
     */
    public static List<PoolElementStructurePiece> generate(ServerLevel level, ResourceLocation startPool, BlockPos origin,
                                                           @Nullable ResourceLocation anchor) {
        Holder<StructureTemplatePool> start = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL)
                .getHolderOrThrow(ResourceKey.create(Registries.TEMPLATE_POOL, startPool));
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator,
                generator.getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
                level.getSeed() + origin.getY(), new ChunkPos(origin), level, biome -> true);
        Optional<Structure.GenerationStub> stub = JigsawPlacement.addPieces(context, start, Optional.ofNullable(anchor), 7, origin,
                false, Optional.empty(), 80, PoolAliasLookup.EMPTY,
                JigsawStructure.DEFAULT_DIMENSION_PADDING, JigsawStructure.DEFAULT_LIQUID_SETTINGS);
        List<PoolElementStructurePiece> pieces = new ArrayList<>();
        if (stub.isEmpty()) return pieces;
        for (StructurePiece piece : stub.get().getPiecesBuilder().build().pieces()) {
            if (piece instanceof PoolElementStructurePiece poolPiece) {
                poolPiece.place(level, level.structureManager(), generator, level.getRandom(), BoundingBox.infinite(), origin, false);
                pieces.add(poolPiece);
            }
        }
        return pieces;
    }

    public static int convertSpawners(ServerLevel level, List<PoolElementStructurePiece> pieces, DungeonType type, int partySize) {
        return convertSpawners(level, pieces, type, partySize, 1);
    }

    /**
     * Монстры в данже не возрождаются: каждый спавнер сразу выпускает одну волну и исчезает. Волна растёт с этажом
     * ({@link DungeonType#mobsPerSpawner(int, int)}), а состав этажа задаёт {@link DungeonType#floorMob}.
     * Возвращает число появившихся мобов.
     */
    public static int convertSpawners(ServerLevel level, List<PoolElementStructurePiece> pieces, DungeonType type, int partySize, int floor) {
        int spawned = 0;
        int number = 0;
        for (PoolElementStructurePiece piece : pieces) {
            BoundingBox box = piece.getBoundingBox();
            for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
                if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) continue;
                BlockPos at = pos.immutable();
                CompoundTag spawnData = spawner.getSpawner().save(new CompoundTag()).getCompound("SpawnData").getCompound("entity");
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                Optional<EntityType<?>> mobType = EntityType.byString(spawnData.getString("id"));
                if (mobType.isEmpty()) continue;
                int count = type.mobsPerSpawner(partySize, floor);
                for (int i = 0; i < count; i++) {
                    if (!(type.floorMob(floor, number++, mobType.get()).create(level) instanceof Mob mob)) break;
                    double angle = Math.PI * 2 * i / count;
                    double spread = count > 4 ? 0.45 : 0.3;
                    mob.moveTo(at.getX() + 0.5 + Math.cos(angle) * spread, at.getY(), at.getZ() + 0.5 + Math.sin(angle) * spread,
                            level.getRandom().nextFloat() * 360f, 0f);
                    mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.STRUCTURE, null);
                    mob.setPersistenceRequired();
                    if (level.addFreshEntity(mob)) spawned++;
                }
            }
        }
        return spawned;
    }

    // ================================================================ вход и выход

    /** Вызывается, когда игрок стоит в портале данжа. */
    public static void onPortalContact(ServerPlayer player, BlockPos portalPos) {
        long now = player.level().getGameTime();
        if (player.getPersistentData().getLong(PORTAL_COOLDOWN) > now) return;
        player.getPersistentData().putLong(PORTAL_COOLDOWN, now + 60);
        MinecraftServer server = player.getServer();
        if (server == null) return;

        if (isDungeonLevel(player.level())) {
            // Внутри данжа врата домой открываются после победы над боссом, а у входа — ещё и после зачистки предпоследнего этажа.
            DungeonInstance instance = instanceAt(server, portalPos);
            if (instance != null && !player.isSpectator()
                    && (instance.isCompleted() || instance.exit() == DungeonInstance.ExitState.OPEN)) leave(player, instance);
            return;
        }
        GateAltarBlockEntity altar = GateAltarBlockEntity.findNear(player.serverLevel(), portalPos);
        DungeonInstance instance = altar == null ? null : DungeonData.get(server).get(altar.instanceIndex());
        if (instance != null) enter(player, instance, altar);
    }

    /**
     * Вход через открытые врата. Порядок проверок:
     * 1) проход не пройден; 2) игрок из группы, для которой открыты врата, и всё ещё в ней;
     * 3) средний уровень группы; 4) свиток (для данжей с 20 уровня); 5) игрок жив и может переместиться.
     * Потом вход, и только после успешного входа сгорают свиток и камень. При любой неудаче ничего не тратится.
     */
    public static boolean enter(ServerPlayer player, DungeonInstance instance, @Nullable GateAltarBlockEntity altar) {
        MinecraftServer server = player.getServer();
        if (server == null) return false;
        if (instance.isCompleted()) {
            deny(player, Component.translatable("message.heroicpaths.dungeon_completed"));
            return false;
        }
        Party party = PartyManager.partyOf(player);
        UUID currentParty = party == null ? null : party.id();
        // Одиночка, открывший врата, может войти и после вступления в группу; группа — только пока он в ней.
        boolean sameParty = instance.partyId == null || instance.partyId.equals(currentParty);
        if (!instance.isInRoster(player.getUUID()) || !sameParty) {
            deny(player, Component.translatable("message.heroicpaths.access.not_party"));
            return false;
        }
        Access level = checkLevel(player, instance.type);
        if (!level.allowed()) {
            deny(player, level.reason());
            return false;
        }
        if (instance.type.requiresScroll() && !instance.isScrollPaid() && (altar == null || !altar.hasEscrowedScroll(instance.type))) {
            deny(player, Component.translatable("message.heroicpaths.access.scroll", new ItemStack(instance.type.scroll()).getHoverName()));
            return false;
        }
        if (!player.isAlive() || player.isSpectator() || player.isPassenger() || player.isVehicle()) return false;
        ServerLevel dungeon = dungeonLevel(server);
        if (dungeon == null) {
            deny(player, Component.translatable("message.heroicpaths.gate_failed"));
            return false;
        }

        player.getPersistentData().putLong(PORTAL_COOLDOWN, dungeon.getGameTime() + 60);
        player.teleportTo(dungeon, instance.arrival.getX() + 0.5, instance.arrival.getY(), instance.arrival.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        if (player.level() != dungeon || !player.isAlive()) return false;

        // Вход состоялся: свиток и камень сгорают ровно один раз за проход.
        if (!instance.isScrollPaid()) {
            instance.markScrollPaid();
            if (altar != null) altar.consumeEscrow();
        }
        instance.entered().add(player.getUUID());
        DungeonData.get(server).setDirty();
        player.sendSystemMessage(Component.translatable("message.heroicpaths.dungeon_entered", instance.type.displayName(),
                Math.round((instance.type.healthMultiplier(instance.partySize) - 1) * 100)).withStyle(ChatFormatting.DARK_PURPLE));
        player.sendSystemMessage(Component.translatable("message.heroicpaths.dungeon_rules").withStyle(ChatFormatting.GRAY));
        if (altar != null && instance.everyoneEntered()) altar.closeGate();
        return true;
    }

    private static void deny(ServerPlayer player, @Nullable Component reason) {
        if (reason != null) player.displayClientMessage(reason.copy().withStyle(ChatFormatting.RED), true);
    }

    public static void leave(ServerPlayer player, DungeonInstance instance) {
        MinecraftServer server = player.getServer();
        ServerLevel target = server == null ? null : server.getLevel(instance.returnLevel);
        if (target == null && server != null) target = server.overworld();
        if (target == null) return;
        player.getPersistentData().putLong(PORTAL_COOLDOWN, target.getGameTime() + 60);
        player.teleportTo(target, instance.returnPos.getX() + 0.5, instance.returnPos.getY(), instance.returnPos.getZ() + 0.5,
                player.getYRot(), player.getXRot());
    }

    // ================================================================ события

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (dungeonLevel(event.getServer()) != null) {
            HeroicPaths.LOGGER.info("Dungeon dimension {} is ready", DUNGEON_LEVEL.location());
        } else {
            HeroicPaths.LOGGER.warn("Dungeon dimension {} is missing: dungeon gates will not open", DUNGEON_LEVEL.location());
        }
    }

    /** Камни и свитки, которые алтарь вернул, пока хозяин был не в сети. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        List<ItemStack> returned = DungeonData.get(player.server).takePendingReturns(player.getUUID());
        for (ItemStack stack : returned) give(player, stack);
        if (!returned.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.heroicpaths.gate_items_returned").withStyle(ChatFormatting.GRAY));
        }
    }

    private static final String LAST_FLOOR = "heroicpaths.dungeon_floor";

    /** Когда игрок спускается на новый этаж, над панелью предметов пишется «Этаж N из 6». */
    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 10 != 0) return;
        if (!isDungeonLevel(player.level())) {
            player.getPersistentData().remove(LAST_FLOOR);
            return;
        }
        DungeonInstance instance = instanceAt(player.server, player.blockPosition());
        if (instance == null || instance.bossArrival.equals(instance.arrival)) return;
        int floor = floorAt(instance, player.getBlockY());
        if (!player.isSpectator() && !instance.isCompleted()) updateExit(player.serverLevel(), instance, floor);
        if (player.getPersistentData().getInt(LAST_FLOOR) == floor) return;
        player.getPersistentData().putInt(LAST_FLOOR, floor);
        player.displayClientMessage(Component.translatable("message.heroicpaths.dungeon_floor", floor, instance.type.floors() + 1)
                .withStyle(floor > instance.type.floors() ? ChatFormatting.RED : ChatFormatting.GOLD), true);
    }

    /**
     * Врата у входа: открываются, когда на предпоследнем этаже не осталось монстров и группа там (на случай этажа без
     * монстров), и закладываются насовсем, как только кто-то живой спустится на последний этаж.
     */
    static void updateExit(ServerLevel level, DungeonInstance instance, int floor) {
        if (instance.exit() == DungeonInstance.ExitState.WAITING && instance.exitGuards().isEmpty() && floor == exitFloor(instance)) {
            openExit(level, instance);
        } else if (instance.exit() == DungeonInstance.ExitState.OPEN && floor > exitFloor(instance)) {
            closeExit(level, instance);
        }
    }

    /** Предпоследний этаж зачищен: врата у входа ведут домой. */
    public static void openExit(ServerLevel level, DungeonInstance instance) {
        Block seal = openGate(level, instance.arrival);
        instance.setExit(seal == null ? DungeonInstance.ExitState.CLOSED : DungeonInstance.ExitState.OPEN);
        if (seal != null) instance.setExitSeal(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(seal).toString());
        DungeonData.get(level.getServer()).setDirty();
        if (seal == null) return;
        for (ServerPlayer player : membersInside(level, instance)) {
            player.sendSystemMessage(Component.translatable("message.heroicpaths.dungeon_exit_opened", exitFloor(instance),
                    instance.type.floors()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }

    /** Группа спустилась на последний этаж: врата у входа снова заложены, домой — только через босса. */
    public static void closeExit(ServerLevel level, DungeonInstance instance) {
        Block seal = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(instance.exitSeal()));
        if (seal == Blocks.AIR) seal = Blocks.STONE_BRICKS;
        closeGate(level, instance.arrival, seal);
        instance.setExit(DungeonInstance.ExitState.CLOSED);
        DungeonData.get(level.getServer()).setDirty();
        for (ServerPlayer player : membersInside(level, instance)) {
            player.sendSystemMessage(Component.translatable("message.heroicpaths.dungeon_exit_closed").withStyle(ChatFormatting.DARK_RED));
        }
    }

    /** Участники прохода, которые сейчас в нём (живые и павшие). */
    public static List<ServerPlayer> membersInside(ServerLevel level, DungeonInstance instance) {
        List<ServerPlayer> inside = new ArrayList<>();
        for (UUID uuid : instance.roster()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player != null && player.level() == level && indexAt(player.blockPosition()) == instance.index) inside.add(player);
        }
        return inside;
    }

    /** Номер этажа по высоте: этажи 1–5 через FLOOR_STEP от верхнего, ниже пятого — арена. */
    public static int floorAt(DungeonInstance instance, int y) {
        if (y < instance.bossArrival.getY() + 30) return instance.type.floors() + 1;
        int floor = 1 + Math.floorDiv(instance.arrival.getY() + 8 - y, FLOOR_STEP);
        return Math.max(1, Math.min(instance.type.floors(), floor));
    }

    /** Враги в данже крепче: сложность данжа, +30% здоровья и +10% урона за каждого игрока группы сверх первого. */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !isDungeonLevel(level)) return;
        if (!(event.getEntity() instanceof Mob mob) || !(mob instanceof Enemy) || mob instanceof com.heroicpaths.minion.Minion) return;
        DungeonInstance instance = instanceAt(level.getServer(), mob.blockPosition());
        if (instance != null) {
            applyScaling(mob, instance.type, instance.partySize);
        } else if (generating != null && generating.index() == indexAt(mob.blockPosition())) {
            applyScaling(mob, generating.type(), generating.partySize());
            generating.monsters().add(java.util.Map.entry(mob.getUUID(), mob.getBlockY()));
        } else {
            return;
        }
        // Монстры данжа не исчезают, пока группа далеко.
        mob.setPersistenceRequired();
    }

    public static void applyScaling(Mob mob, DungeonInstance instance) {
        applyScaling(mob, instance.type, instance.partySize);
    }

    public static void applyScaling(Mob mob, DungeonType type, int partySize) {
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health == null || health.hasModifier(HEALTH_SCALE)) return;
        health.addPermanentModifier(new AttributeModifier(HEALTH_SCALE,
                type.healthMultiplier(partySize) - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        float damageMultiplier = type.damageMultiplier(partySize);
        if (damage != null && !damage.hasModifier(DAMAGE_SCALE) && damageMultiplier != 1f) {
            damage.addPermanentModifier(new AttributeModifier(DAMAGE_SCALE,
                    damageMultiplier - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        mob.setHealth(mob.getMaxHealth());
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof net.minecraft.world.entity.player.Player || !(entity.level() instanceof ServerLevel level) || !isDungeonLevel(level)) return;
        DungeonInstance instance = instanceAt(level.getServer(), entity.blockPosition());
        if (instance == null || instance.isCompleted()) return;
        if (entity instanceof DungeonBoss) {
            completeInstance(level, instance, entity.blockPosition());
        } else if (instance.exitGuards().remove(entity.getUUID())) {
            DungeonData.get(level.getServer()).setDirty();
            if (instance.exitGuards().isEmpty() && instance.exit() == DungeonInstance.ExitState.WAITING) openExit(level, instance);
        }
    }

    /** Босс повержен: награды и трофей всем вошедшим, выполнение контрактов и открытие врат у входа. */
    public static void completeInstance(ServerLevel level, DungeonInstance instance, BlockPos bossPos) {
        instance.complete();
        DungeonData.get(level.getServer()).setDirty();
        // Павшие соратники встают у врат домой и получают награду вместе со всеми.
        DungeonRevival.reviveAll(level, instance);
        java.util.List<ServerPlayer> cleared = new java.util.ArrayList<>();
        for (UUID uuid : instance.entered()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            cleared.add(player);
            RpgData data = RpgManager.get(player);
            boolean contract = instance.type.id().equals(data.getContractDungeon());
            if (contract) data.clearContract();
            RpgManager.addXp(player, instance.type.rewardXp());
            give(player, new ItemStack(Items.EMERALD, instance.type.rewardEmeralds()));
            give(player, new ItemStack(instance.type.trophy()));
            if (player.getRandom().nextFloat() < instance.type.relicSparkChance()) {
                give(player, new ItemStack(ModItems.RELIC_SPARK.get()));
                player.sendSystemMessage(Component.translatable("message.heroicpaths.relic_spark_found").withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            RpgManager.sync(player);
            player.sendSystemMessage(Component.translatable(contract ? "message.heroicpaths.contract_done" : "message.heroicpaths.dungeon_cleared",
                    instance.type.displayName(), instance.type.rewardXp()).withStyle(ChatFormatting.GOLD));
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        }
        // Заказы гильдии на зачистку этого данжа — для вошедших и их групп.
        com.heroicpaths.guild.GuildOrders.onDungeonCleared(level.getServer(), cleared, instance.type);
        // Домой — через врата в зале у арены, куда спустилась группа (у старых одноэтажных проходов — у входа).
        boolean gate = openEntranceGate(level, instance.bossArrival)
                || (!instance.bossArrival.equals(instance.arrival) && openEntranceGate(level, instance.arrival));
        for (ServerPlayer player : cleared)
            player.sendSystemMessage(Component.translatable(gate ? "message.heroicpaths.dungeon_gate_opened" : "message.heroicpaths.dungeon_exit_here")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (gate) return;
        // Врата не нашлись (повреждённый зал) — чтобы никто не застрял, портал на месте гибели босса, как раньше.
        for (int y = 0; y < 3; y++) {
            BlockPos pos = bossPos.above(y);
            if (level.getBlockState(pos).isAir()) {
                level.setBlockAndUpdate(pos, ModBlocks.DUNGEON_PORTAL.get().defaultBlockState()
                        .setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X));
            }
        }
    }

    /**
     * Открывает врата в зале прибытия: заливка проёма 3×4 внутри обсидиановой рамки становится порталом домой.
     * В шаблоне зала врата стоят в 4 блоках от его центра (точки прибытия); как повёрнут зал, заранее не известно,
     * поэтому проверяются все четыре стороны: подходит та, где однородная заливка обрамлена обсидианом.
     */
    public static boolean openEntranceGate(ServerLevel level, BlockPos arrival) {
        return openGate(level, arrival) != null;
    }

    /** Открывает врата у точки arrival (см. {@link #openEntranceGate}) и возвращает блок, которым они были заложены; null — врат нет. */
    @Nullable
    public static Block openGate(ServerLevel level, BlockPos arrival) {
        return gate(level, arrival, null);
    }

    /** Закладывает открытые врата у точки arrival блоком seal. */
    public static boolean closeGate(ServerLevel level, BlockPos arrival, Block seal) {
        return gate(level, arrival, seal) != null;
    }

    /**
     * Ищет врата в 4 блоках от arrival. Без closeWith открывает заложенные (заливка → портал), с closeWith —
     * закладывает открытые (портал → closeWith). Возвращает блок заливки, которая была в проёме; null — врата не нашлись.
     */
    @Nullable
    private static Block gate(ServerLevel level, BlockPos arrival, @Nullable Block closeWith) {
        int[][] sides = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] side : sides) {
            // Проём тянется поперёк направления на врата.
            int ax = side[1] != 0 ? 1 : 0, az = side[0] != 0 ? 1 : 0;
            BlockPos center = arrival.offset(side[0] * 4, 0, side[1] * 4);
            List<BlockPos> inside = new java.util.ArrayList<>();
            net.minecraft.world.level.block.Block seal = null;
            boolean ok = true;
            for (int i = -1; i <= 1 && ok; i++)
                for (int y = 0; y <= 3 && ok; y++) {
                    BlockPos pos = center.offset(ax * i, y, az * i);
                    BlockState state = level.getBlockState(pos);
                    // Открываем только заложенные врата, закладываем только открытые.
                    if (state.isAir() || state.is(Blocks.OBSIDIAN) || (seal != null && !state.is(seal))
                            || state.is(ModBlocks.DUNGEON_PORTAL.get()) != (closeWith != null)) ok = false;
                    else {
                        seal = state.getBlock();
                        inside.add(pos.immutable());
                    }
                }
            for (int y = 0; y <= 4 && ok; y++)
                ok = level.getBlockState(center.offset(-ax * 2, y, -az * 2)).is(Blocks.OBSIDIAN)
                        && level.getBlockState(center.offset(ax * 2, y, az * 2)).is(Blocks.OBSIDIAN);
            for (int i = -1; i <= 1 && ok; i++) ok = level.getBlockState(center.offset(ax * i, 4, az * i)).is(Blocks.OBSIDIAN);
            if (!ok) continue;
            BlockPos middle = center.above(2);
            if (closeWith != null) {
                for (BlockPos pos : inside) level.setBlock(pos, closeWith.defaultBlockState(), Block.UPDATE_ALL);
                level.playSound(null, middle, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1f, 0.6f);
                return seal;
            }
            DungeonPortalBlock.fill(level, inside, ax == 1 ? Direction.Axis.X : Direction.Axis.Z);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, middle.getX() + 0.5, middle.getY(), middle.getZ() + 0.5, 80, 1.2, 1.2, 1.2, 0.1);
            level.playSound(null, middle, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 1f, 1.2f);
            return seal;
        }
        return null;
    }

    static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) {
            ItemEntity drop = player.drop(stack, false);
            if (drop != null) drop.setNoPickUpDelay();
        }
    }
}
