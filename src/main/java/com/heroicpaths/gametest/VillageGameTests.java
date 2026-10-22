package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModVillagers;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.village.MentorEvents;
import com.heroicpaths.village.VillageInjector;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Игровые тесты деревень: шаблоны гильдий, встраивание в пулы, стыковка с улицами, наставники, скидки. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class VillageGameTests {
    private static final String ARENA = "gametest_arena";

    /** Отрезки ванильных улиц, у которых есть входы для домов. */
    private static final Map<String, String> TEST_STREETS = Map.of(
            "plains", "minecraft:village/plains/streets/straight_03",
            "desert", "minecraft:village/desert/streets/straight_01",
            "savanna", "minecraft:village/savanna/streets/straight_02",
            "snowy", "minecraft:village/snowy/streets/straight_03",
            "taiga", "minecraft:village/taiga/streets/straight_03");

    private static final Map<String, DeferredBlock<? extends Block>> WORKSTATIONS = Map.of(
            "swordmaster_guild", ModBlocks.WEAPON_RACK,
            "archer_guild", ModBlocks.ARCHERY_TARGET,
            "mage_guild", ModBlocks.ARCANE_LECTERN);

    private static Registry<StructureTemplatePool> pools(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
    }

    private static Holder<StructureTemplatePool> singleElementPool(ServerLevel level, String location) {
        Holder<StructureProcessorList> noProcessors =
                level.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST).getHolderOrThrow(VillageInjector.NO_PROCESSORS);
        StructurePoolElement element = StructurePoolElement.single(location, noProcessors)
                .apply(StructureTemplatePool.Projection.RIGID);
        return Holder.direct(new StructureTemplatePool(pools(level).getHolderOrThrow(Pools.EMPTY), List.of(Pair.of(element, 1))));
    }

    @GameTest(template = ARENA)
    public static void guildTemplatesAndMentorPoolsLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (String biome : VillageInjector.BIOMES) {
            for (String guild : VillageInjector.GUILDS) {
                ResourceLocation template = VillageInjector.guildTemplate(biome, guild);
                helper.assertTrue(level.getStructureManager().get(template).isPresent(), "missing template " + template);
            }
            for (String profession : List.of("swordmaster", "master_archer", "archmage")) {
                ResourceLocation id = HeroicPaths.id("village/" + biome + "/" + profession + "_mentor");
                helper.assertTrue(level.getStructureManager().get(id).isPresent(), "missing mentor template " + id);
                helper.assertTrue(pools(level).containsKey(id), "missing mentor pool " + id);
            }
        }
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void villagePoolsContainGuilds(GameTestHelper helper) {
        for (String biome : VillageInjector.BIOMES) {
            StructureTemplatePool houses = pools(helper.getLevel()).get(VillageInjector.housesPool(biome));
            helper.assertTrue(houses != null, "missing pool " + VillageInjector.housesPool(biome));
            for (String guild : VillageInjector.GUILDS) {
                String location = VillageInjector.guildTemplate(biome, guild).toString();
                boolean present = houses.templates.stream().anyMatch(e -> e.toString().contains(location));
                helper.assertTrue(present, location + " is not in " + VillageInjector.housesPool(biome));
            }
        }
        helper.succeed();
    }

    /**
     * Строит кусок деревни как настоящий генератор (улица → дочерние улицы → дома, с expansion hack),
     * временно оставив в пуле улиц один отрезок, а в пуле домов — одну гильдию.
     * Проверяет, что гильдия пристроилась: её рабочее место стоит рядом.
     * Начинаем с улицы, а не с центра деревни: центр спавнит жителей и кошек в незагруженные чанки,
     * и тестовый сервер потом зависает при выгрузке.
     */
    @GameTest(template = ARENA, batch = "village_streets", timeoutTicks = 400)
    public static void guildsAttachToVillageStreets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0)).offset(0, 0, 400);
        int index = 0;
        for (String biome : VillageInjector.BIOMES) {
            StructureTemplatePool houses = pools(level).get(VillageInjector.housesPool(biome));
            StructureTemplatePool streets = pools(level).get(ResourceLocation.withDefaultNamespace("village/" + biome + "/streets"));
            // Пул кэширует максимальную высоту домов при первом обращении. В игре это происходит уже после
            // добавления гильдий, поэтому прогреваем кэш до подмены, иначе он запомнит высоту одной гильдии.
            houses.getMaxSize(level.getStructureManager());
            List<StructurePoolElement> housesBackup = new ArrayList<>(houses.templates);
            List<StructurePoolElement> streetsBackup = new ArrayList<>(streets.templates);
            Holder<StructureTemplatePool> startStreet = singleElementPool(level, TEST_STREETS.get(biome));
            for (String guild : VillageInjector.GUILDS) {
                BlockPos pos = origin.offset(index++ * 128, 0, 0);
                replace(houses, singleElementPool(level, VillageInjector.guildTemplate(biome, guild).toString()).value().templates);
                replace(streets, singleElementPool(level, TEST_STREETS.get(biome)).value().templates);
                try {
                    helper.assertTrue(generateLikeVillage(level, startStreet, pos, 2), "village piece was not generated for " + biome);
                } finally {
                    replace(houses, housesBackup);
                    replace(streets, streetsBackup);
                }
                helper.assertTrue(containsBlock(level, pos, WORKSTATIONS.get(guild).get()),
                        guild + " did not attach to a " + biome + " street");
            }
        }
        // Даём серверу доделать загрузку чанков, затронутых генерацией, иначе он зависает при остановке.
        helper.runAfterDelay(100, helper::succeed);
    }

    private static void replace(StructureTemplatePool pool, List<StructurePoolElement> elements) {
        List<StructurePoolElement> copy = new ArrayList<>(elements);
        pool.templates.clear();
        pool.templates.addAll(copy);
    }

    /** Как JigsawPlacement.generateJigsaw, но с expansion hack, который включён у ванильных деревень. */
    private static boolean generateLikeVillage(ServerLevel level, Holder<StructureTemplatePool> start, BlockPos pos, int maxDepth) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator,
                generator.getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
                level.getSeed(), new ChunkPos(pos), level, biome -> true);
        Optional<Structure.GenerationStub> stub = JigsawPlacement.addPieces(context, start,
                Optional.of(ResourceLocation.withDefaultNamespace("street")), maxDepth, pos, true, Optional.empty(), 128,
                PoolAliasLookup.EMPTY, JigsawStructure.DEFAULT_DIMENSION_PADDING, JigsawStructure.DEFAULT_LIQUID_SETTINGS);
        if (stub.isEmpty()) return false;
        for (StructurePiece piece : stub.get().getPiecesBuilder().build().pieces()) {
            if (piece instanceof PoolElementStructurePiece poolPiece) {
                poolPiece.place(level, level.structureManager(), generator, level.getRandom(), BoundingBox.infinite(), pos, false);
            }
        }
        return true;
    }

    private static boolean containsBlock(ServerLevel level, BlockPos center, Block block) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -48; x <= 48; x++) {
            for (int z = -48; z <= 48; z++) {
                for (int y = -4; y <= 8; y++) {
                    if (level.getBlockState(cursor.setWithOffset(center, x, y, z)).is(block)) return true;
                }
            }
        }
        return false;
    }

    /** Гильдия, поставленная через jigsaw, приводит с собой жителя-наставника нужной профессии. */
    @GameTest(template = ARENA, batch = "village_mentor", timeoutTicks = 200)
    public static void guildSpawnsItsMentor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0)).offset(48, 0, 0);
        ChunkPos chunk = new ChunkPos(pos);
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) level.setChunkForced(chunk.x + dx, chunk.z + dz, true);
        }
        Holder<StructureTemplatePool> guild = singleElementPool(level, VillageInjector.guildTemplate("taiga", "mage_guild").toString());
        helper.assertTrue(JigsawPlacement.generateJigsaw(level, guild, ResourceLocation.withDefaultNamespace("building_entrance"), 2, pos, false),
                "guild was not generated");
        helper.assertTrue(containsBlock(level, pos, ModBlocks.ARCANE_LECTERN.get()), "arcane lectern missing");

        AABB area = new AABB(pos).inflate(16);
        VillagerProfession archmage = ModVillagers.ARCHMAGE.get();
        helper.succeedWhen(() -> {
            helper.assertTrue(
                    !level.getEntitiesOfClass(Villager.class, area, v -> v.getVillagerData().getProfession() == archmage).isEmpty(),
                    "archmage did not appear in the guild");
            for (int dx = -1; dx <= 2; dx++) {
                for (int dz = -1; dz <= 1; dz++) level.setChunkForced(chunk.x + dx, chunk.z + dz, false);
            }
        });
    }

    @GameTest(template = ARENA)
    public static void studentsGetDiscountFromTheirMentor(GameTestHelper helper) {
        Villager mentor = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
        mentor.setVillagerData(mentor.getVillagerData().setProfession(ModVillagers.ARCHMAGE.get()));
        helper.assertTrue(!mentor.getOffers().isEmpty(), "archmage has no trades");

        ServerPlayer warrior = helper.makeMockServerPlayerInLevel();
        RpgManager.chooseClass(warrior, PlayerClass.WARRIOR);
        helper.assertTrue(!MentorEvents.applyStudentDiscount(warrior, mentor), "warrior got a discount from the archmage");

        ServerPlayer mage = helper.makeMockServerPlayerInLevel();
        RpgManager.chooseClass(mage, PlayerClass.MAGE);
        helper.assertTrue(MentorEvents.applyStudentDiscount(mage, mentor), "mage got no discount from the archmage");
        for (MerchantOffer offer : mentor.getOffers()) {
            helper.assertTrue(offer.getSpecialPriceDiff() < 0, "offer " + offer.getResult() + " is not discounted");
        }
        helper.succeed();
    }
}
