package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.CryptKing;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Игровые тесты данжа: регистрация склепа, генерация целиком, босс и добыча. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class DungeonGameTests {
    private static final String ARENA = "gametest_arena";
    private static final List<String> PIECES = List.of("entrance", "hub", "boss_room", "corridor_straight", "corridor_turn",
            "tomb_room", "library", "spider_den", "armory", "dead_end");
    private static final List<String> ROOMS = List.of("tomb_room", "library", "spider_den", "armory");

    @GameTest(template = ARENA)
    public static void cryptIsRegistered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (String piece : PIECES) {
            ResourceLocation id = HeroicPaths.id("crypt/" + piece);
            helper.assertTrue(level.getStructureManager().get(id).isPresent(), "missing template " + id);
        }
        for (String pool : List.of("entrance", "hub", "boss", "corridors", "rooms", "dead_ends")) {
            ResourceLocation id = HeroicPaths.id("crypt/" + pool);
            helper.assertTrue(level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).containsKey(id), "missing pool " + id);
        }
        helper.assertTrue(level.registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(HeroicPaths.id("crypt")),
                "structure heroicpaths:crypt is not registered");
        helper.assertTrue(level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET).containsKey(HeroicPaths.id("crypts")),
                "structure set heroicpaths:crypts is not registered");
        helper.succeed();
    }

    /** Генерирует три склепа целиком: в каждом должны быть главный зал, логово босса с троном, комнаты и спавнеры. */
    @GameTest(template = ARENA, batch = "crypt_generation", timeoutTicks = 400)
    public static void cryptGeneratesWithBossLair(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Registry<StructureTemplatePool> pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        Holder<StructureTemplatePool> start = pools.getHolderOrThrow(
                ResourceKey.create(Registries.TEMPLATE_POOL, HeroicPaths.id("crypt/entrance")));
        BlockPos origin = helper.absolutePos(new BlockPos(0, 10, 0)).offset(0, 0, -400);

        int totalRooms = 0;
        List<String> layouts = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<PoolElementStructurePiece> pieces = generate(level, start, origin.offset(i * 200, 0, 0));
            helper.assertTrue(!pieces.isEmpty(), "crypt " + i + " was not generated");
            List<String> names = pieces.stream().map(p -> p.getElement().toString()).toList();
            layouts.add(names.toString());
            helper.assertTrue(count(names, "crypt/hub") == 1, "crypt " + i + " must have one hub: " + names);
            helper.assertTrue(count(names, "crypt/boss_room") == 1, "crypt " + i + " must have one boss lair: " + names);
            totalRooms += ROOMS.stream().mapToInt(room -> count(names, "crypt/" + room)).sum();

            PoolElementStructurePiece boss = pieces.stream()
                    .filter(p -> p.getElement().toString().contains("crypt/boss_room")).findFirst().orElseThrow();
            helper.assertTrue(countBlocks(level, boss.getBoundingBox(), Blocks.GILDED_BLACKSTONE) > 0, "boss throne was not placed");
            int spawners = pieces.stream().mapToInt(p -> countBlocks(level, p.getBoundingBox(), Blocks.SPAWNER)).sum();
            helper.assertTrue(spawners >= 2, "crypt " + i + " has only " + spawners + " spawners");
        }
        // Комнаты случайны, поэтому требуем их в среднем хотя бы по одной на склеп.
        helper.assertTrue(totalRooms >= 3, "only " + totalRooms + " rooms in 3 crypts: " + layouts);
        HeroicPaths.LOGGER.info("Crypt layouts in test: {}", layouts);
        // Даём серверу доделать загрузку затронутых чанков, иначе он зависает при остановке.
        helper.runAfterDelay(100, helper::succeed);
    }

    private static int count(List<String> names, String piece) {
        return (int) names.stream().filter(n -> n.contains(HeroicPaths.MODID + ":" + piece + "]")).count();
    }

    private static int countBlocks(ServerLevel level, BoundingBox box, Block block) {
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockState(pos).is(block)) found++;
        }
        return found;
    }

    /** Как генерация настоящего склепа, только без привязки к поверхности. */
    private static List<PoolElementStructurePiece> generate(ServerLevel level, Holder<StructureTemplatePool> start, BlockPos pos) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator,
                generator.getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
                level.getSeed(), new net.minecraft.world.level.ChunkPos(pos), level, biome -> true);
        Optional<Structure.GenerationStub> stub = JigsawPlacement.addPieces(context, start, Optional.empty(), 7, pos,
                false, Optional.empty(), 80, PoolAliasLookup.EMPTY,
                JigsawStructure.DEFAULT_DIMENSION_PADDING, JigsawStructure.DEFAULT_LIQUID_SETTINGS);
        List<PoolElementStructurePiece> pieces = new ArrayList<>();
        if (stub.isEmpty()) return pieces;
        for (StructurePiece piece : stub.get().getPiecesBuilder().build().pieces()) {
            if (piece instanceof PoolElementStructurePiece poolPiece) {
                poolPiece.place(level, level.structureManager(), generator, level.getRandom(), BoundingBox.infinite(), pos, false);
                pieces.add(poolPiece);
            }
        }
        return pieces;
    }

    @GameTest(template = ARENA)
    public static void cryptKingIsATankyBoss(GameTestHelper helper) {
        CryptKing king = helper.spawn(ModEntities.CRYPT_KING.get(), new BlockPos(3, 2, 3));
        king.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(king.blockPosition()), MobSpawnType.SPAWN_EGG, null);
        helper.assertTrue(king.getMaxHealth() == 300f, "max health must be 300, got " + king.getMaxHealth());
        helper.assertTrue(king.getAttributeValue(Attributes.ATTACK_DAMAGE) == 12.0,
                "attack damage must be 12, got " + king.getAttributeValue(Attributes.ATTACK_DAMAGE));
        helper.assertTrue(king.getMainHandItem().is(Items.NETHERITE_SWORD), "king must hold a netherite sword");
        helper.assertTrue(king.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET), "king must wear a crown");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void cryptKingSummonsGuardsAndEnrages(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CryptKing king = helper.spawn(ModEntities.CRYPT_KING.get(), new BlockPos(4, 2, 4));

        int summoned = king.summonMinions();
        helper.assertTrue(summoned >= 1, "king summoned no guards");
        List<Zombie> guards = level.getEntitiesOfClass(Zombie.class, king.getBoundingBox().inflate(8),
                zombie -> zombie.getTags().contains(CryptKing.MINION_TAG));
        helper.assertTrue(guards.size() == summoned, "expected " + summoned + " guards, found " + guards.size());

        float before = king.getHealth();
        helper.assertTrue(!king.hurt(king.damageSources().mobAttack(guards.get(0)), 10f) && king.getHealth() == before,
                "guards must not be able to hurt the king");

        helper.assertTrue(!king.isEnraged(), "king must start calm");
        king.setHealth(king.getMaxHealth() * 0.4f);
        king.updatePhase();
        helper.assertTrue(king.isEnraged(), "king must enrage below half health");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void cryptLootContainsExperienceTomes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (String table : List.of("chests/crypt", "chests/crypt_treasure", "entities/crypt_king")) {
            LootTable loot = lootTable(level, table);
            helper.assertTrue(loot != LootTable.EMPTY, "missing loot table " + table);
        }
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, helper.absoluteVec(Vec3.ZERO))
                .create(LootContextParamSets.CHEST);
        List<ItemStack> treasure = lootTable(level, "chests/crypt_treasure").getRandomItems(params);
        int tomes = treasure.stream().filter(stack -> stack.is(ModItems.EXPERIENCE_TOME.get())).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(tomes >= 2, "treasure chest must contain at least 2 experience tomes, got " + tomes);
        helper.succeed();
    }

    private static LootTable lootTable(ServerLevel level, String path) {
        return level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, HeroicPaths.id(path)));
    }
}
