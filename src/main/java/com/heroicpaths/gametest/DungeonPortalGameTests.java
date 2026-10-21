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
            for (String pool : List.of("arrival", "hub", "corridors", "rooms", "dead_ends", "boss")) {
                ResourceLocation id = HeroicPaths.id(type.id() + "/" + pool);
                helper.assertTrue(pools.containsKey(id), "missing pool " + id);
            }
            helper.assertTrue(pools.containsKey(type.startPool()), "missing start pool " + type.startPool());
            // Босс стоит в шаблоне логова: у каждого данжа свой.
            StructureTemplate boss = level.getStructureManager().get(HeroicPaths.id(type.id() + "/boss_room")).orElse(null);
            helper.assertTrue(boss != null, "missing boss room of " + type.id());
            String expected = HeroicPaths.MODID + ":" + type.bossId();
            ListTag entities = boss.save(new CompoundTag()).getList("entities", Tag.TAG_COMPOUND);
            boolean hasBoss = entities.stream().anyMatch(e -> expected.equals(((CompoundTag) e).getCompound("nbt").getString("id")));
            helper.assertTrue(hasBoss, type.id() + " boss room has no " + expected);
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
     * Каждый данж строится целиком: зал прибытия с запечатанными вратами (выхода назад нет), центральный зал и логово босса;
     * после победы врата у входа открываются порталом 3×4 — в любой теме и при любом повороте зала.
     */
    @GameTest(template = ARENA, batch = "portal_dungeons", timeoutTicks = 600)
    public static void portalDungeonsGenerateWithBossLair(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 40, 0)).offset(0, 0, -900);
        int index = 0;
        for (DungeonType type : DungeonType.values()) {
            for (int attempt = 0; attempt < 2; attempt++) {
                List<PoolElementStructurePiece> pieces = DungeonManager.generate(level, type, origin.offset(index++ * 220, 0, 0));
                List<String> names = pieces.stream().map(p -> p.getElement().toString()).toList();
                helper.assertTrue(!pieces.isEmpty(), type.id() + " was not generated");
                helper.assertTrue(count(names, type.id() + "/arrival") == 1, type.id() + " must have one arrival hall: " + names);
                helper.assertTrue(count(names, type.id() + "/hub") == 1, type.id() + " must have one hub: " + names);
                helper.assertTrue(count(names, type.id() + "/boss_room") == 1, type.id() + " must have one boss lair: " + names);
                helper.assertTrue(names.stream().noneMatch(n -> !n.contains(HeroicPaths.MODID + ":" + type.id() + "/")),
                        type.id() + " mixes pieces of other dungeons: " + names);
                int portals = countBlocks(level, pieces.get(0).getBoundingBox(), ModBlocks.DUNGEON_PORTAL.get());
                helper.assertTrue(portals == 0, type.id() + " arrival hall must not have a way back, got " + portals + " portal blocks");
                BoundingBox hall = pieces.get(0).getBoundingBox();
                BlockPos arrival = new BlockPos(hall.getCenter().getX(), hall.minY() + 1, hall.getCenter().getZ());
                helper.assertTrue(DungeonManager.openEntranceGate(level, arrival), type.id() + " entrance gate was not found");
                int opened = countBlocks(level, hall, ModBlocks.DUNGEON_PORTAL.get());
                helper.assertTrue(opened == 12, type.id() + " entrance gate must open as a 3x4 portal, got " + opened);
            }
        }
        helper.runAfterDelay(100, helper::succeed);
    }

    @GameTest(template = ARENA)
    public static void partySizeAddsThirtyPercentEnemyHealth(GameTestHelper helper) {
        helper.assertTrue(Math.abs(DungeonType.CRYPT.healthMultiplier(1) - 1.0f) < 1e-4, "solo crypt must not scale health");
        helper.assertTrue(Math.abs(DungeonType.CRYPT.healthMultiplier(3) - 1.6f) < 1e-4, "party of 3 must give +60% health");
        helper.assertTrue(Math.abs(DungeonType.FORGE.healthMultiplier(2) - 1.8f * 1.3f) < 1e-4, "forge for 2 must be 1.8 x 1.3");

        helper.assertTrue(DungeonType.CRYPT.mobsPerSpawner(1) == 3 && DungeonType.CRYPT.mobsPerSpawner(4) == 6,
                "each extra player must add one monster per spawner");

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
                BlockPos.ZERO, BlockPos.ZERO, helper.getLevel().dimension(), BlockPos.ZERO);
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
        int gate = countBlocks(level, BoundingBox.fromCorners(instance.arrival.offset(-6, -1, -6), instance.arrival.offset(6, 6, 6)), ModBlocks.DUNGEON_PORTAL.get());
        helper.assertTrue(gate == 12, "the entrance gate must open as the way home, got " + gate + " portal blocks");
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
