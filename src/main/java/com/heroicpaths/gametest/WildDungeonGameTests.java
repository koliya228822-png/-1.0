package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.dungeon.DungeonData;
import com.heroicpaths.dungeon.DungeonGateBlock;
import com.heroicpaths.dungeon.DungeonInstance;
import com.heroicpaths.dungeon.DungeonManager;
import com.heroicpaths.dungeon.DungeonType;
import com.heroicpaths.dungeon.GateAltarBlockEntity;
import com.heroicpaths.entity.DrownedProphet;
import com.heroicpaths.entity.DungeonBoss;
import com.heroicpaths.entity.Lich;
import com.heroicpaths.entity.SpellProjectile;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Игровые тесты новых данжей, их боссов и диких входов в подземелья. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class WildDungeonGameTests {
    private static final String ARENA = "gametest_arena";
    private static final List<DungeonType> WILD = List.of(DungeonType.SPIDER_LAIR, DungeonType.FORGE, DungeonType.SUNKEN_TEMPLE,
            DungeonType.FROST, DungeonType.NECROPOLIS);

    private static ServerPlayer adventurer(GameTestHelper helper, int level) {
        ServerPlayer player = RpgGameTests.playerInArena(helper);
        RpgManager.chooseClass(player, PlayerClass.WARRIOR);
        RpgManager.setLevel(player, level);
        return player;
    }

    @GameTest(template = ARENA)
    public static void newDungeonsFollowTheLevelLadder(GameTestHelper helper) {
        DungeonType[] types = DungeonType.values();
        for (int i = 1; i < types.length; i++) {
            helper.assertTrue(types[i].minLevel() > types[i - 1].minLevel(), "dungeons must be ordered by level");
        }
        helper.assertTrue(!DungeonType.SPIDER_LAIR.requiresScroll() && DungeonType.SUNKEN_TEMPLE.requiresScroll()
                && DungeonType.NECROPOLIS.requiresScroll(), "scrolls are needed from level 20");
        helper.assertTrue(DungeonType.SUNKEN_TEMPLE.scroll() == ModItems.TEMPLE_SCROLL.get()
                && DungeonType.NECROPOLIS.scroll() == ModItems.NECROPOLIS_SCROLL.get(), "wrong scrolls");
        var recipes = helper.getLevel().getRecipeManager();
        for (String scroll : List.of("temple_scroll", "necropolis_scroll", "forge_scroll", "frost_scroll")) {
            helper.assertTrue(recipes.byKey(HeroicPaths.id(scroll)).isPresent(), "missing recipe " + scroll);
        }
        helper.assertTrue(DungeonType.byId("sunken_temple") == DungeonType.SUNKEN_TEMPLE
                && DungeonType.SUNKEN_TEMPLE.getSerializedName().equals("sunken_temple"), "dungeon ids must round-trip");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void newBossesFightWithTheirAbilities(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player target = RpgGameTests.playerInArena(helper);
        int bolts = 0;
        for (var type : List.of(ModEntities.DROWNED_PROPHET.get(), ModEntities.LICH.get())) {
            Mob boss = (Mob) helper.spawn(type, new BlockPos(3, 2, 3));
            helper.assertTrue(boss instanceof DungeonBoss, type + " is not a dungeon boss");
            helper.assertTrue(boss.getMaxHealth() >= 250, type + " is too weak: " + boss.getMaxHealth());
            boss.setTarget(target);
            var controller = switch (boss) {
                case DrownedProphet prophet -> prophet.controller();
                case Lich lich -> lich.controller();
                default -> throw new IllegalStateException();
            };
            var abilities = (com.heroicpaths.entity.BossController.Abilities) boss;
            int before = level.getEntitiesOfClass(Mob.class, boss.getBoundingBox().inflate(20),
                    mob -> mob.getTags().contains(com.heroicpaths.entity.BossController.MINION_TAG)).size();
            abilities.summon();
            int after = level.getEntitiesOfClass(Mob.class, boss.getBoundingBox().inflate(20),
                    mob -> mob.getTags().contains(com.heroicpaths.entity.BossController.MINION_TAG)).size();
            helper.assertTrue(after > before, type + " summoned nobody");
            int projectiles = level.getEntitiesOfClass(SpellProjectile.class, boss.getBoundingBox().inflate(10)).size();
            abilities.bolt(target);
            bolts += level.getEntitiesOfClass(SpellProjectile.class, boss.getBoundingBox().inflate(10)).size() - projectiles;
            helper.assertTrue(controller.bossEvent() != null, "no boss bar");
            boss.discard();
            level.getEntitiesOfClass(Mob.class, target.getBoundingBox().inflate(30),
                    mob -> mob.getTags().contains(com.heroicpaths.entity.BossController.MINION_TAG)).forEach(Mob::discard);
        }
        helper.assertTrue(bolts == 6, "each boss must fire a fan of 3 bolts, got " + bolts);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void wildEntrancesAreRegistered(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var sets = level.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
        for (DungeonType type : WILD) {
            helper.assertTrue(structures.containsKey(HeroicPaths.id(type.id() + "_entrance")), "missing structure " + type.id() + "_entrance");
            helper.assertTrue(sets.containsKey(HeroicPaths.id(type.id() + "_entrances")), "missing structure set " + type.id());
            StructureTemplate template = level.getStructureManager().get(HeroicPaths.id(type.id() + "/wild_entrance")).orElse(null);
            helper.assertTrue(template != null, "missing wild entrance template of " + type.id());
            CompoundTag nbt = template.save(new CompoundTag());
            boolean gate = nbt.getList("palette", Tag.TAG_COMPOUND).stream().map(CompoundTag.class::cast)
                    .anyMatch(state -> state.getString("Name").equals(HeroicPaths.MODID + ":dungeon_gate")
                            && state.getCompound("Properties").getString("dungeon").equals(type.id()));
            helper.assertTrue(gate, type.id() + " entrance has no gate to its dungeon");
            ListTag size = nbt.getList("size", Tag.TAG_INT);
            helper.assertTrue(size.getInt(1) >= 15, type.id() + " entrance must reach the surface from below");
        }
        helper.succeed();
    }

    /** Дикие врата: без камня, по уровню группы и со свитком, после прохода запечатываются. */
    @GameTest(template = ARENA, batch = "wild_gate", timeoutTicks = 600)
    public static void wildGatesOpenWithoutStonesAndSealAfterARun(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos gatePos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(gatePos, ModBlocks.DUNGEON_GATE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH).setValue(DungeonGateBlock.DUNGEON, DungeonType.SPIDER_LAIR));
        GateAltarBlockEntity gate = (GateAltarBlockEntity) level.getBlockEntity(gatePos);
        BlockPos necroPos = helper.absolutePos(new BlockPos(6, 2, 1));
        level.setBlockAndUpdate(necroPos, ModBlocks.DUNGEON_GATE.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH).setValue(DungeonGateBlock.DUNGEON, DungeonType.NECROPOLIS));
        GateAltarBlockEntity necroGate = (GateAltarBlockEntity) level.getBlockEntity(necroPos);
        ServerPlayer player = adventurer(helper, 5);
        DungeonManager.testLevel = level;
        try {
            gate.activateWild(player, DungeonType.SPIDER_LAIR);
            helper.assertTrue(!gate.isOpen(), "a level 5 player opened a level 10 dungeon");
            RpgManager.setLevel(player, 12);
            gate.activateWild(player, DungeonType.SPIDER_LAIR);
            helper.assertTrue(gate.isOpen(), "the wild gate did not open without a stone");
            DungeonInstance instance = DungeonData.get(level.getServer()).get(gate.instanceIndex());
            helper.assertTrue(instance != null && instance.type == DungeonType.SPIDER_LAIR, "the gate built the wrong dungeon");
            helper.assertTrue(DungeonManager.enter(player, instance, gate), "the player could not enter through the wild gate");
            helper.assertTrue(!gate.isOpen() && gate.sealedUntil() > level.getGameTime(), "the wild gate must seal after a run");
            gate.activateWild(player, DungeonType.SPIDER_LAIR);
            helper.assertTrue(!gate.isOpen(), "a sealed gate opened again");

            RpgManager.setLevel(player, 55);
            necroGate.activateWild(player, DungeonType.NECROPOLIS);
            helper.assertTrue(!necroGate.isOpen(), "the necropolis opened without a scroll");
            player.getInventory().add(new ItemStack(ModItems.NECROPOLIS_SCROLL.get()));
            necroGate.activateWild(player, DungeonType.NECROPOLIS);
            helper.assertTrue(necroGate.isOpen() && necroGate.hasEscrowedScroll(DungeonType.NECROPOLIS), "the necropolis gate must take the scroll");
            necroGate.closeGate();
            helper.assertTrue(player.getInventory().countItem(ModItems.NECROPOLIS_SCROLL.get()) == 1
                    && necroGate.sealedUntil() <= level.getGameTime(), "an unused wild gate must return the scroll and stay unsealed");
        } finally {
            DungeonManager.testLevel = null;
        }
        helper.runAfterDelay(100, helper::succeed);
    }
}
