package com.heroicpaths.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.heroicpaths.HeroicPaths;
import com.heroicpaths.dungeon.ContractService;
import com.heroicpaths.dungeon.DungeonData;
import com.heroicpaths.dungeon.DungeonInstance;
import com.heroicpaths.dungeon.DungeonManager;
import com.heroicpaths.dungeon.DungeonType;
import com.heroicpaths.dungeon.GateAltarBlockEntity;
import com.heroicpaths.dungeon.GateKey;
import com.heroicpaths.entity.FlameTyrant;
import com.heroicpaths.entity.FrostLord;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModComponents;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.village.VillageInjector;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Игровые тесты данжей за вратами: измерение, генерация, контракты, алтарь и награда за босса. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class DungeonPortalGameTests {
    private static final String ARENA = "gametest_arena";

    @GameTest(template = ARENA)
    public static void portalDungeonsAreRegistered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        for (DungeonType type : DungeonType.values()) {
            for (String pool : List.of("arrival", "landing", "floor_hub", "corridors", "rooms", "dead_ends", "descent", "arena")) {
                ResourceLocation id = HeroicPaths.id(type.theme() + "/" + pool);
                helper.assertTrue(pools.containsKey(id), "missing pool " + id);
            }
            helper.assertTrue(pools.containsKey(type.startPool()), "missing start pool " + type.startPool());
            // Босс стоит в шаблоне арены: у каждого данжа свой.
            StructureTemplate boss = level.getStructureManager().get(HeroicPaths.id(type.theme() + "/arena")).orElse(null);
            helper.assertTrue(boss != null, "missing boss arena of " + type.id());
            String expected = HeroicPaths.MODID + ":" + type.bossId();
            ListTag entities = boss.save(new CompoundTag()).getList("entities", Tag.TAG_COMPOUND);
            boolean hasBoss = entities.stream().anyMatch(e -> expected.equals(((CompoundTag) e).getCompound("nbt").getString("id")));
            helper.assertTrue(hasBoss, type.id() + " boss arena has no " + expected);
        }
        helper.assertTrue(level.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).containsKey(HeroicPaths.id("dungeon")),
                "dimension type heroicpaths:dungeon is not registered");
        helper.succeed();
    }

    /** Тестовый сервер не создаёт измерения из датапаков, поэтому проверяем, что описание измерения читается кодеком. */
    @GameTest(template = ARENA)
    public static void dungeonDimensionDecodes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation file = HeroicPaths.id("dimension/dungeons.json");
        Optional<Resource> resource = level.getServer().getResourceManager().getResource(file);
        helper.assertTrue(resource.isPresent(), "missing " + file);
        JsonElement json;
        try (Reader reader = resource.get().openAsReader()) {
            json = JsonParser.parseReader(reader);
        } catch (Exception e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
        var result = LevelStem.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), json);
        helper.assertTrue(result.isSuccess(), "dungeon dimension does not decode: " + result.error().map(Object::toString).orElse(""));
        helper.assertTrue(result.getOrThrow().type().is(HeroicPaths.id("dungeon")), "dimension must use the dungeon type");
        helper.succeed();
    }

    /**
     * Каждый данж строится целиком: пять этажей (зал прибытия или площадка спуска, центральный зал, зал со спуском),
     * между ними шахты с лестницей, внизу арена с боссом. Врата домой у арены открываются порталом 3×4 — в любой теме
     * и при любом повороте; врата в зале прибытия остаются запечатанными.
     */
    @GameTest(template = ARENA, batch = "portal_dungeons", timeoutTicks = 600)
    public static void portalDungeonsBuildFiveFloorsAndArena(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos(base.getX(), 220, base.getZ() - 900);
        int index = 0;
        for (DungeonType type : DungeonType.values()) {
            String theme = type.theme();
            DungeonManager.Built built = DungeonManager.build(level, type, origin.offset(index++ * 260, 0, 0), 1);
            helper.assertTrue(built != null, type.id() + " was not built");
            helper.assertTrue(built.floors().size() == DungeonType.FLOORS + 1, type.id() + " must have 5 floors and an arena");
            for (int floor = 1; floor <= DungeonType.FLOORS + 1; floor++) {
                List<String> names = built.floors().get(floor - 1).stream().map(p -> p.getElement().toString()).toList();
                helper.assertTrue(names.stream().allMatch(n -> n.contains(HeroicPaths.MODID + ":" + theme + "/")),
                        type.id() + " mixes pieces of other dungeons: " + names);
                if (floor > DungeonType.FLOORS) {
                    helper.assertTrue(count(names, theme + "/arena") == 1, type.id() + " arena missing: " + names);
                    continue;
                }
                helper.assertTrue(count(names, theme + (floor == 1 ? "/arrival" : "/landing")) == 1, type.id() + " floor " + floor + " start: " + names);
                helper.assertTrue(count(names, theme + "/floor_hub") == 1, type.id() + " floor " + floor + " must have one hub: " + names);
                helper.assertTrue(count(names, theme + "/descent") == 1, type.id() + " floor " + floor + " must have one way down: " + names);
            }
            // Шахта: лестница доходит до низа каждой площадки спуска и до зала у арены.
            helper.assertTrue(level.getBlockState(built.bossArrival()).is(net.minecraft.world.level.block.Blocks.LADDER),
                    type.id() + " shaft does not reach the arena");
            helper.assertTrue(level.getBlockState(built.bossArrival().above(20)).is(net.minecraft.world.level.block.Blocks.LADDER),
                    type.id() + " shaft to the arena is broken");
            BoundingBox hall = built.floors().get(0).get(0).getBoundingBox();
            helper.assertTrue(countBlocks(level, hall, ModBlocks.DUNGEON_PORTAL.get()) == 0, type.id() + " arrival hall must not have a way back");
            // Босс посреди арены.
            BoundingBox arena = built.floors().get(DungeonType.FLOORS).get(0).getBoundingBox();
            String bossId = HeroicPaths.MODID + ":" + type.bossId();
            boolean boss = !level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, net.minecraft.world.phys.AABB.of(arena),
                    m -> EntityType.getKey(m.getType()).toString().equals(bossId)).isEmpty();
            helper.assertTrue(boss, type.id() + " arena has no " + bossId);
            if (!DungeonManager.openEntranceGate(level, built.bossArrival())) {
                StringBuilder dump = new StringBuilder();
                for (int[] d : new int[][]{{0, -4}, {4, 0}, {0, 4}, {-4, 0}}) {
                    dump.append(" [").append(d[0]).append(',').append(d[1]).append("]:");
                    for (int y = 0; y <= 4; y++) dump.append(level.getBlockState(built.bossArrival().offset(d[0], y, d[1])).getBlock().getName().getString()).append('/');
                }
                for (int y = -2; y <= 2; y++) dump.append(" y").append(y).append('=').append(level.getBlockState(built.bossArrival().above(y)).getBlock().getName().getString());
                helper.fail(type.id() + " arena gate home was not found at " + built.bossArrival() + ":" + dump);
            }
            int opened = countBlocks(level, BoundingBox.fromCorners(built.bossArrival().offset(-6, -1, -6), built.bossArrival().offset(6, 6, 6)),
                    ModBlocks.DUNGEON_PORTAL.get());
            helper.assertTrue(opened == 12, type.id() + " arena gate must open as a 3x4 portal, got " + opened);
        }
        helper.runAfterDelay(100, helper::succeed);
    }

    /** В Глиняных копях состав этажей: 1–2 только ближний бой, 3 — пополам с метателями, 4–5 — каждый пятый танк. */
    @GameTest(template = ARENA)
    public static void clayFloorsMixGolems(GameTestHelper helper) {
        DungeonType clay = DungeonType.CRYPT;
        EntityType<?> any = EntityType.ZOMBIE;
        for (int floor = 1; floor <= 2; floor++)
            for (int i = 0; i < 10; i++)
                helper.assertTrue(clay.floorMob(floor, i, any) == ModEntities.CLAY_GOLEM.get(), "floor " + floor + " must be melee only");
        int ranged = 0, tanks = 0;
        for (int i = 0; i < 10; i++) if (clay.floorMob(3, i, any) == ModEntities.CLAY_THROWER.get()) ranged++;
        helper.assertTrue(ranged == 5, "floor 3 must be half throwers, got " + ranged);
        ranged = 0;
        for (int i = 0; i < 20; i++) {
            EntityType<?> mob = clay.floorMob(4, i, any);
            if (mob == ModEntities.CLAY_BRUTE.get()) tanks++;
            if (mob == ModEntities.CLAY_THROWER.get()) ranged++;
        }
        helper.assertTrue(tanks == 4, "floor 4 must be 20% tanks, got " + tanks + " of 20");
        helper.assertTrue(ranged == 8, "floor 4 must split the rest between melee and ranged, got " + ranged + " throwers");
        helper.assertTrue(DungeonType.FORGE.floorMob(4, 0, any) == any, "other dungeons keep their own monsters");
        helper.succeed();
    }

    /** Колосс: латы гасят урон, на 2/3 и 1/3 здоровья он сбрасывает части и становится быстрее и сильнее. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void clayColossusHasThreePhases(GameTestHelper helper) {
        com.heroicpaths.entity.ClayColossus boss = helper.spawn(ModEntities.CLAY_COLOSSUS.get(), new BlockPos(4, 2, 4));
        helper.assertTrue(boss.getBbHeight() >= 5.3f, "the colossus must be three times the player's height");
        helper.assertTrue(boss.phase() == 1 && boss.armorReduction() > 0.3f, "phase 1 must be armored");
        double speed = boss.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        double damage = boss.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        boss.setHealth(boss.getMaxHealth() * 0.6f);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(boss.phase() == 2, "below 2/3 health the colossus must enter phase 2, got " + boss.phase());
            helper.assertTrue(boss.action() == com.heroicpaths.entity.ClayColossus.SHED, "phase change must shed armor");
            helper.assertTrue(boss.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) > speed, "phase 2 must be faster");
            helper.assertTrue(!boss.hurt(boss.damageSources().generic(), 5f), "the colossus must be invulnerable while shedding");
            int debris = helper.getLevel().getEntitiesOfClass(com.heroicpaths.entity.ClayDebris.class, boss.getBoundingBox().inflate(10)).size();
            helper.assertTrue(debris == 5, "phase 2 must drop 2 arms and 3 armor parts, got " + debris);
            boss.setHealth(boss.getMaxHealth() * 0.2f);
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(boss.phase() == 3 && boss.armorReduction() == 0f, "below 1/3 health the colossus must be bare in phase 3");
                helper.assertTrue(boss.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) > damage * 1.5,
                        "phase 3 must hit much harder");
                boss.discard();
                helper.succeed();
            });
        });
    }

    @GameTest(template = ARENA)
    public static void partySizeAddsThirtyPercentEnemyHealth(GameTestHelper helper) {
        helper.assertTrue(Math.abs(DungeonType.CRYPT.healthMultiplier(1) - 1.0f) < 1e-4, "solo crypt must not scale health");
        helper.assertTrue(Math.abs(DungeonType.CRYPT.healthMultiplier(3) - 1.6f) < 1e-4, "party of 3 must give +60% health");
        helper.assertTrue(Math.abs(DungeonType.FORGE.healthMultiplier(2) - 1.8f * 1.3f) < 1e-4, "forge for 2 must be 1.8 x 1.3");

        helper.assertTrue(DungeonType.CRYPT.mobsPerSpawner(1) == 2 && DungeonType.CRYPT.mobsPerSpawner(4) == 5,
                "each extra player must add one monster per spawner");
        // Этажи людней с глубиной: второй вдвое людней первого, дальше ещё больше.
        int previous = 0;
        for (int floor = 1; floor <= DungeonType.FLOORS; floor++) {
            int crowd = DungeonType.CRYPT.mobsPerSpawner(1, floor);
            helper.assertTrue(crowd > previous, "floor " + floor + " must have more monsters than the one above, got " + crowd);
            previous = crowd;
        }
        helper.assertTrue(DungeonType.CRYPT.mobsPerSpawner(1, 2) == 2 * DungeonType.CRYPT.mobsPerSpawner(1, 1), "floor 2 must be twice floor 1");

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        float baseDamage = (float) zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        DungeonManager.applyScaling(zombie, DungeonType.FORGE, 3);
        DungeonManager.applyScaling(zombie, DungeonType.FORGE, 3); // повторный вход в мир не должен умножать ещё раз
        float expected = 20f * 1.8f * 1.6f;
        helper.assertTrue(Math.abs(zombie.getMaxHealth() - expected) < 0.01f, "zombie health must be " + expected + ", got " + zombie.getMaxHealth());
        helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(), "scaled zombie must be at full health");
        float damage = (float) zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        helper.assertTrue(Math.abs(damage - baseDamage * 1.4f * 1.2f) < 0.01f, "forge damage for 3 must be x1.4 x1.2, got " + damage);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void contractGivesGateStoneAndChecksLevel(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RpgManager.chooseClass(player, PlayerClass.ARCHER);
        player.getInventory().clearContent();

        helper.assertTrue(!ContractService.take(player, DungeonType.FORGE), "level 1 player took a level 20 contract");
        helper.assertTrue(findStone(player).isEmpty(), "a stone was given for a refused contract");

        helper.assertTrue(ContractService.take(player, DungeonType.CRYPT), "crypt contract was refused");
        ItemStack stone = findStone(player);
        GateKey key = stone.get(ModComponents.GATE_KEY.get());
        helper.assertTrue(key != null && key.dungeon().equals("crypt"), "stone is not keyed to the crypt: " + key);
        helper.assertTrue(key.partySize() == 1, "a solo player must register a party of 1, got " + key.partySize());
        RpgData data = RpgManager.get(player);
        helper.assertTrue("crypt".equals(data.getContractDungeon()) && data.getContractParty() == 1,
                "contract was not saved: " + data.getContractDungeon() + " x" + data.getContractParty());

        RpgManager.setLevel(player, 45);
        helper.assertTrue(ContractService.take(player, DungeonType.FROST), "level 45 player could not take the frost contract");
        helper.assertTrue("frost".equals(data.getContractDungeon()), "new contract must replace the old one");
        helper.succeed();
    }

    private static ItemStack findStone(ServerPlayer player) {
        for (int slot = player.getInventory().getContainerSize() - 1; slot >= 0; slot--) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.GATE_STONE.get())) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Проход помнит, для кого открыты врата: посторонний не войдёт, а состав переживает сохранение. */
    @GameTest(template = ARENA)
    public static void dungeonRemembersItsRoster(GameTestHelper helper) {
        ServerPlayer first = helper.makeMockServerPlayerInLevel();
        ServerPlayer second = helper.makeMockServerPlayerInLevel();
        ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
        UUID partyId = UUID.randomUUID();
        DungeonInstance instance = new DungeonInstance(998, DungeonType.CRYPT, List.of(first.getUUID(), second.getUUID()), partyId,
                BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO, helper.getLevel().dimension(), BlockPos.ZERO);
        helper.assertTrue(instance.partySize == 2, "party size must come from the roster");
        helper.assertTrue(instance.isInRoster(first.getUUID()) && instance.isInRoster(second.getUUID()), "party members must be in the roster");
        helper.assertTrue(!instance.isInRoster(stranger.getUUID()), "a stranger is in the roster");
        helper.assertTrue(!DungeonManager.enter(stranger, instance, null), "enter() let a stranger in");
        instance.entered().add(first.getUUID());
        helper.assertTrue(!instance.everyoneEntered(), "only one of two has entered");
        instance.entered().add(second.getUUID());
        helper.assertTrue(instance.everyoneEntered(), "both have entered");
        instance.markScrollPaid();

        DungeonInstance loaded = DungeonInstance.load(instance.save());
        helper.assertTrue(loaded.partySize == 2 && loaded.entered().size() == 2 && loaded.type == DungeonType.CRYPT
                        && partyId.equals(loaded.partyId) && loaded.isScrollPaid() && loaded.isInRoster(second.getUUID()),
                "instance did not survive saving");
        helper.succeed();
    }

    /** Данж создаётся и регистрируется, а смерть босса выполняет контракт, выдаёт награду и открывает портал домой. */
    @GameTest(template = ARENA, batch = "portal_reward", timeoutTicks = 400)
    public static void bossDeathCompletesContract(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        RpgManager.chooseClass(player, PlayerClass.MAGE);
        player.getInventory().clearContent();
        RpgManager.get(player).setContract("crypt", 1);

        DungeonInstance instance = DungeonManager.create(level, DungeonType.CRYPT, List.of(player.getUUID()), null, level.dimension(),
                helper.absolutePos(new BlockPos(1, 2, 1)));
        helper.assertTrue(instance != null, "instance was not created");
        helper.assertTrue(DungeonData.get(level.getServer()).get(instance.index) == instance, "instance is not registered");
        helper.assertTrue(DungeonManager.instanceAt(level.getServer(), instance.arrival) == instance, "instanceAt does not find the instance");
        helper.assertTrue(level.getBlockState(instance.arrival).isAir(), "arrival point is blocked");
        instance.entered().add(player.getUUID());

        int levelBefore = RpgManager.get(player).getLevel();
        BlockPos bossPos = instance.arrival;
        DungeonManager.completeInstance(level, instance, bossPos);
        RpgData data = RpgManager.get(player);
        helper.assertTrue(instance.isCompleted(), "instance must be completed");
        helper.assertTrue(data.getContractDungeon().isEmpty(), "contract was not cleared: " + data.getContractDungeon());
        helper.assertTrue(data.getLevel() > levelBefore, "boss reward gave no levels");
        helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == DungeonType.CRYPT.rewardEmeralds(), "wrong emerald reward");
        helper.assertTrue(player.getInventory().countItem(ModItems.EXPERIENCE_TOME.get()) == DungeonType.CRYPT.rewardTomes(), "wrong tome reward");
        helper.assertTrue(player.getInventory().countItem(ModItems.CRYPT_CROWN_SHARD.get()) == 1, "every party member must get a boss trophy");
        int gate = countBlocks(level, BoundingBox.fromCorners(instance.bossArrival.offset(-6, -1, -6), instance.bossArrival.offset(6, 6, 6)), ModBlocks.DUNGEON_PORTAL.get());
        helper.assertTrue(gate == 12, "the arena gate must open as the way home, got " + gate + " portal blocks");
        int entrance = countBlocks(level, BoundingBox.fromCorners(instance.arrival.offset(-6, -1, -6), instance.arrival.offset(6, 6, 6)), ModBlocks.DUNGEON_PORTAL.get());
        helper.assertTrue(entrance == 0, "the arrival hall must stay sealed, got " + entrance + " portal blocks");
        helper.assertTrue(!DungeonManager.enter(player, instance, null), "a completed dungeon must not be enterable");
        helper.runAfterDelay(60, helper::succeed);
    }

    @GameTest(template = ARENA)
    public static void newBossesAreTanky(GameTestHelper helper) {
        FlameTyrant tyrant = helper.spawn(ModEntities.FLAME_TYRANT.get(), new BlockPos(2, 2, 2));
        helper.assertTrue(tyrant.getMaxHealth() == 400f, "flame tyrant must have 400 HP, got " + tyrant.getMaxHealth());
        helper.assertTrue(tyrant.fireImmune(), "flame tyrant must be fire immune");
        helper.assertTrue(tyrant.controller().summon(EntityType.BLAZE, 2, 5, mob -> {}) >= 1, "flame tyrant summoned no blazes");

        FrostLord lord = helper.spawn(ModEntities.FROST_LORD.get(), new BlockPos(5, 2, 5));
        lord.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(lord.blockPosition()), MobSpawnType.SPAWN_EGG, null);
        helper.assertTrue(lord.getMaxHealth() == 500f, "frost lord must have 500 HP, got " + lord.getMaxHealth());
        helper.assertTrue(lord.getMainHandItem().is(Items.BOW), "frost lord must hold a bow");
        helper.assertTrue(!lord.controller().isEnraged(), "frost lord must start calm");
        lord.setHealth(lord.getMaxHealth() * 0.4f);
        lord.controller().updatePhase();
        helper.assertTrue(lord.controller().isEnraged(), "frost lord must enrage below half health");
        helper.succeed();
    }

    /** Во всех пяти типах деревень центром становится площадь с алтарём, рамкой врат и досками заданий. */
    @GameTest(template = ARENA, batch = "gate_plazas", timeoutTicks = 300)
    public static void gatePlazasHaveWorkingAltar(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        BlockPos origin = helper.absolutePos(new BlockPos(0, 1, 0)).offset(0, 0, 700);
        int index = 0;
        for (String biome : VillageInjector.BIOMES) {
            StructureTemplatePool centers = pools.get(VillageInjector.townCentersPool(biome));
            helper.assertTrue(centers != null, "missing " + VillageInjector.townCentersPool(biome));
            String plazaId = VillageInjector.plazaTemplate(biome).toString();
            long plazas = centers.templates.stream().filter(e -> e.toString().contains(plazaId)).count();
            helper.assertTrue(plazas * 2 > centers.templates.size(), biome + " town centers are not mostly gate plazas");

            StructureTemplate plaza = level.getStructureManager().get(VillageInjector.plazaTemplate(biome)).orElse(null);
            helper.assertTrue(plaza != null, "missing template " + plazaId);
            BlockPos pos = origin.offset(index++ * 24, 0, 0);
            plaza.placeInWorld(level, pos, pos, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_CLIENTS);
            BlockPos altarPos = pos.offset(7, 1, 9);
            helper.assertTrue(level.getBlockState(altarPos).is(ModBlocks.GATE_ALTAR.get()), biome + " plaza has no altar at " + altarPos);
            helper.assertTrue(level.getBlockState(altarPos).getValue(HorizontalDirectionalBlock.FACING) == Direction.SOUTH,
                    biome + " altar must face the gate");
            helper.assertTrue(countBlocks(level, BoundingBox.fromCorners(pos, pos.offset(14, 7, 14)), ModBlocks.QUEST_BOARD.get()) >= 2,
                    biome + " plaza must have quest boards");
            GateAltarBlockEntity altar = (GateAltarBlockEntity) level.getBlockEntity(altarPos);
            helper.assertTrue(altar != null, biome + " altar has no block entity");
            altar.setPortal(true);
            int portals = countBlocks(level, BoundingBox.fromCorners(pos, pos.offset(14, 7, 14)), ModBlocks.DUNGEON_PORTAL.get());
            helper.assertTrue(portals == 12, biome + " gate frame must hold a 3x4 portal, got " + portals);
            helper.assertTrue(GateAltarBlockEntity.findNear(level, pos.offset(7, 2, 11)) == null, "a closed altar must not be found");
            altar.setPortal(false);
            helper.assertTrue(countBlocks(level, BoundingBox.fromCorners(pos, pos.offset(14, 7, 14)), ModBlocks.DUNGEON_PORTAL.get()) == 0,
                    biome + " portal did not close");
        }
        helper.runAfterDelay(40, helper::succeed);
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
}
