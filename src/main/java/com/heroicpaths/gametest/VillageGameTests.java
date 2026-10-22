package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModVillagers;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.village.MentorEvents;
import com.heroicpaths.village.MentorHomes;
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
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
     * временно оставив в пуле домов одну гильдию; улицы — все ванильные улицы биома.
     * Проверяет, что гильдия пристраивается к улицам: хотя бы в одной из нескольких раскладок она встала,
     * а после постройки этой раскладки её рабочее место стоит в мире. Дома наставников размером с крупные
     * ванильные дома, поэтому, как и те, встают не на каждый участок.
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
            // Пул кэширует максимальную высоту домов при первом обращении. В игре это происходит уже после
            // добавления гильдий, поэтому прогреваем кэш до подмены, иначе он запомнит высоту одной гильдии.
            houses.getMaxSize(level.getStructureManager());
            List<StructurePoolElement> housesBackup = new ArrayList<>(houses.templates);
            Holder<StructureTemplatePool> startStreet = singleElementPool(level, TEST_STREETS.get(biome));
            for (String guild : VillageInjector.GUILDS) {
                String template = VillageInjector.guildTemplate(biome, guild).toString();
                BlockPos pos = origin.offset(index++ * 128, 0, 0);
                // Раскладка строится заново при каждом обращении к заготовке, поэтому куски берём один раз.
                List<StructurePiece> layout = null;
                replace(houses, singleElementPool(level, template).value().templates);
                try {
                    for (int attempt = 0; attempt < 30 && layout == null; attempt++) {
                        List<StructurePiece> pieces = villageLayout(level, startStreet, pos, level.getSeed() + attempt, 3)
                                .map(stub -> stub.getPiecesBuilder().build().pieces()).orElse(List.of());
                        if (pieces.stream().anyMatch(p -> p.toString().contains(template))) layout = pieces;
                    }
                } finally {
                    replace(houses, housesBackup);
                }
                helper.assertTrue(layout != null, guild + " did not attach to any " + biome + " street layout");
                place(level, layout, pos);
                BoundingBox house = layout.stream().filter(p -> p.toString().contains(template)).findFirst().orElseThrow().getBoundingBox();
                Block workstation = WORKSTATIONS.get(guild).get();
                helper.assertTrue(BlockPos.betweenClosedStream(house).anyMatch(p -> level.getBlockState(p).is(workstation)),
                        guild + " was laid out but its workstation is missing in " + biome);
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

    /** Раскладка как у JigsawPlacement.generateJigsaw, но с expansion hack, который включён у ванильных деревень. */
    private static Optional<Structure.GenerationStub> villageLayout(ServerLevel level, Holder<StructureTemplatePool> start, BlockPos pos,
                                                                  long seed, int maxDepth) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        Structure.GenerationContext context = new Structure.GenerationContext(level.registryAccess(), generator,
                generator.getBiomeSource(), level.getChunkSource().randomState(), level.getStructureManager(),
                seed, new ChunkPos(pos), level, biome -> true);
        return JigsawPlacement.addPieces(context, start,
                Optional.of(ResourceLocation.withDefaultNamespace("street")), maxDepth, pos, true, Optional.empty(), 128,
                PoolAliasLookup.EMPTY, JigsawStructure.DEFAULT_DIMENSION_PADDING, JigsawStructure.DEFAULT_LIQUID_SETTINGS);
    }

    private static void place(ServerLevel level, List<StructurePiece> layout, BlockPos pos) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (StructurePiece piece : layout) {
            if (piece instanceof PoolElementStructurePiece poolPiece) {
                poolPiece.place(level, level.structureManager(), generator, level.getRandom(), BoundingBox.infinite(), pos, false);
            }
        }
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

    /** Наставник помнит дом у рабочего места, ушедшего тянет назад, погибшего через пять дней сменяет новый. */
    @GameTest(template = ARENA, batch = "village_mentor_home")
    public static void fallenMentorIsReplacedAfterFiveDays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lectern = new BlockPos(2, 2, 2);
        helper.setBlock(lectern, ModBlocks.ARCANE_LECTERN.get());
        BlockPos home = helper.absolutePos(lectern);
        Villager mentor = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
        mentor.setVillagerData(mentor.getVillagerData().setProfession(ModVillagers.ARCHMAGE.get()));
        mentor.setVillagerXp(1);

        MentorHomes.keepHome(level, mentor);
        helper.assertTrue(home.equals(MentorHomes.home(mentor)), "mentor home is " + MentorHomes.home(mentor) + ", not the lectern " + home);
        helper.assertTrue(mentor.hasRestriction() && mentor.getRestrictCenter().equals(home), "mentor is not leashed to the lectern");
        helper.assertTrue(mentor.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                        .map(target -> target.getTarget().currentBlockPosition().equals(home)).orElse(false),
                "a mentor far from home is not walking back");

        MentorHomes.Graves graves = MentorHomes.Graves.get(level.getServer());
        int before = graves.all().size();
        mentor.kill();
        helper.assertTrue(graves.all().size() == before + 1, "the fallen mentor was not remembered");
        helper.assertTrue(MentorHomes.replaceFallen(level.getServer(), 0) == 0, "a new mentor came before five days passed");
        helper.assertTrue(MentorHomes.replaceFallen(level.getServer(), MentorHomes.RESPAWN_TICKS) == 1, "no new mentor after five days");
        List<Villager> successors = level.getEntitiesOfClass(Villager.class, new AABB(home).inflate(4),
                v -> v.isAlive() && v.getVillagerData().getProfession() == ModVillagers.ARCHMAGE.get());
        helper.assertTrue(successors.size() == 1, "expected one new archmage at the lectern, found " + successors.size());
        helper.assertTrue(home.equals(MentorHomes.home(successors.get(0))), "the new mentor does not live at the old home");
        successors.forEach(Villager::discard);
        helper.succeed();
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
