package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.dungeon.DungeonData;
import com.heroicpaths.dungeon.DungeonInstance;
import com.heroicpaths.dungeon.DungeonManager;
import com.heroicpaths.dungeon.DungeonType;
import com.heroicpaths.dungeon.GateAltarBlockEntity;
import com.heroicpaths.dungeon.GateStoneItem;
import com.heroicpaths.entity.CryptKing;
import com.heroicpaths.gear.GearEvents;
import com.heroicpaths.gear.GearRules;
import com.heroicpaths.party.PartyManager;
import com.heroicpaths.registry.ModAttachments;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModEntities;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.ability.AbilityLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Игровые тесты групп, входа в данж (средний уровень, свитки), правил данжа, прочности и запрета ремонта. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class PartyGearGameTests {
    private static final String ARENA = "gametest_arena";

    private static ServerPlayer player(GameTestHelper helper, int level) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        RpgManager.setLevel(player, level);
        return player;
    }

    private static void party(ServerPlayer leader, ServerPlayer... members) {
        for (ServerPlayer member : members) {
            PartyManager.invite(leader, member);
            PartyManager.accept(member);
        }
    }

    // ================================================================ группы

    @GameTest(template = ARENA)
    public static void partyAverageLevelAndFriendlyFire(GameTestHelper helper) {
        ServerPlayer leader = player(helper, 10);
        ServerPlayer member = player(helper, 30);
        ServerPlayer stranger = player(helper, 50);
        helper.assertTrue(PartyManager.averageLevel(leader) == 10, "a solo player's average must be their own level");

        party(leader, member);
        helper.assertTrue(PartyManager.partyOf(member) != null && PartyManager.partyOf(member) == PartyManager.partyOf(leader),
                "invite and accept must put both players into one party");
        helper.assertTrue(PartyManager.averageLevel(leader) == 20 && PartyManager.averageLevel(member) == 20,
                "average of 10 and 30 must be 20, got " + PartyManager.averageLevel(leader));
        helper.assertTrue(PartyManager.sameParty(leader, member) && !PartyManager.sameParty(leader, stranger), "wrong party membership");
        helper.assertTrue(!PartyManager.invite(member, stranger), "only the leader can invite");

        LivingIncomingDamageEvent friendly = new LivingIncomingDamageEvent(member,
                new DamageContainer(leader.damageSources().playerAttack(leader), 5f));
        PartyManager.onIncomingDamage(friendly);
        helper.assertTrue(friendly.isCanceled(), "party members must not hurt each other");
        LivingIncomingDamageEvent hostile = new LivingIncomingDamageEvent(stranger,
                new DamageContainer(leader.damageSources().playerAttack(leader), 5f));
        PartyManager.onIncomingDamage(hostile);
        helper.assertTrue(!hostile.isCanceled(), "damage to a non-member must not be cancelled by the party");
        helper.assertTrue(!AbilityLogic.canHarm(leader, member), "abilities must not target party members");

        helper.assertTrue(PartyManager.kick(leader, member.getUUID()), "the leader must be able to kick");
        helper.assertTrue(PartyManager.partyOf(member) == null && PartyManager.averageLevel(leader) == 10, "kick did not remove the member");
        helper.assertTrue(PartyManager.disband(leader) && PartyManager.partyOf(leader) == null, "disband did not remove the party");
        helper.succeed();
    }

    // ================================================================ врата и вход

    /**
     * Полный путь врат: без свитка и при низком среднем уровне врата не открываются и ничего не тратится;
     * посторонний и вышедший из группы не входят; свиток сгорает только при первом настоящем входе, один раз на группу.
     */
    @GameTest(template = ARENA, batch = "gate_flow", timeoutTicks = 400)
    public static void gateSpendsScrollOnlyOnSuccessfulEntry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos altarPos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(altarPos, ModBlocks.GATE_ALTAR.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        GateAltarBlockEntity altar = (GateAltarBlockEntity) level.getBlockEntity(altarPos);
        ServerPlayer leader = player(helper, 30);
        ServerPlayer member = player(helper, 12);
        ServerPlayer stranger = player(helper, 60);
        party(leader, member);

        DungeonManager.testLevel = level;
        try {
            leader.setItemInHand(InteractionHand.MAIN_HAND, GateStoneItem.create(ModItems.GATE_STONE.get(), DungeonType.FORGE, 2));
            altar.activate(leader, leader.getMainHandItem());
            helper.assertTrue(!altar.isOpen(), "the forge gate opened without a scroll");
            helper.assertTrue(leader.getMainHandItem().is(ModItems.GATE_STONE.get()), "the stone was spent on a refused gate");

            leader.getInventory().add(new ItemStack(ModItems.FORGE_SCROLL.get()));
            RpgManager.setLevel(member, 5); // средний уровень (30 + 5) / 2 = 17,5 < 20
            altar.activate(leader, leader.getMainHandItem());
            helper.assertTrue(!altar.isOpen(), "the gate opened for a party with average level 17.5");
            helper.assertTrue(leader.getInventory().countItem(ModItems.FORGE_SCROLL.get()) == 1, "the scroll was spent on a refused gate");

            RpgManager.setLevel(member, 12); // (30 + 12) / 2 = 21
            altar.activate(leader, leader.getMainHandItem());
            helper.assertTrue(altar.isOpen(), "the gate did not open for a party with average level 21 and a scroll");
            helper.assertTrue(leader.getInventory().countItem(ModItems.FORGE_SCROLL.get()) == 0
                    && leader.getInventory().countItem(ModItems.GATE_STONE.get()) == 0, "the altar must hold the stone and scroll");
            helper.assertTrue(altar.hasEscrowedScroll(DungeonType.FORGE), "the scroll is not held by the altar");
            DungeonInstance instance = DungeonData.get(level.getServer()).get(altar.instanceIndex());
            helper.assertTrue(instance != null && instance.partySize == 2, "the instance must be built for the party of 2");

            helper.assertTrue(!DungeonManager.enter(stranger, instance, altar), "a stranger entered");
            PartyManager.leave(member);
            helper.assertTrue(!DungeonManager.enter(member, instance, altar), "a player who left the party entered");
            party(leader, member);
            RpgManager.setLevel(member, 5);
            helper.assertTrue(!DungeonManager.enter(member, instance, altar), "the party entered with average level 17.5");
            helper.assertTrue(altar.hasEscrowedScroll(DungeonType.FORGE) && !instance.isScrollPaid(), "a refused entry spent the scroll");

            RpgManager.setLevel(member, 12);
            helper.assertTrue(DungeonManager.enter(member, instance, altar), "a party member with a valid party could not enter");
            helper.assertTrue(member.blockPosition().closerThan(instance.arrival, 2), "the member was not moved into the dungeon");
            helper.assertTrue(instance.isScrollPaid() && !altar.hasEscrow(), "the scroll must burn on the first entry");
            helper.assertTrue(altar.isOpen(), "the gate must stay open until the whole party is in");

            helper.assertTrue(DungeonManager.enter(leader, instance, altar), "the second member must enter without another scroll");
            helper.assertTrue(!altar.isOpen(), "the gate must close once the whole party is inside");
            helper.assertTrue(leader.getInventory().countItem(ModItems.FORGE_SCROLL.get()) == 0, "the scroll came back after being used");
        } finally {
            DungeonManager.testLevel = null;
            PartyManager.disband(leader);
        }
        helper.runAfterDelay(100, helper::succeed);
    }

    /** Если в открытые врата никто не вошёл, камень и свиток возвращаются тому, кто их принёс. */
    @GameTest(template = ARENA, batch = "gate_return", timeoutTicks = 400)
    public static void gateReturnsItemsWhenNobodyEnters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos altarPos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(altarPos, ModBlocks.GATE_ALTAR.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        GateAltarBlockEntity altar = (GateAltarBlockEntity) level.getBlockEntity(altarPos);
        ServerPlayer solo = player(helper, 25);
        DungeonManager.testLevel = level;
        try {
            solo.setItemInHand(InteractionHand.MAIN_HAND, GateStoneItem.create(ModItems.GATE_STONE.get(), DungeonType.FORGE, 1));
            solo.getInventory().add(new ItemStack(ModItems.FORGE_SCROLL.get()));
            altar.activate(solo, solo.getMainHandItem());
            helper.assertTrue(altar.isOpen(), "the gate did not open");
            altar.closeGate();
            helper.assertTrue(!altar.isOpen() && !altar.hasEscrow(), "the gate did not close");
            helper.assertTrue(solo.getInventory().countItem(ModItems.FORGE_SCROLL.get()) == 1
                    && solo.getInventory().countItem(ModItems.GATE_STONE.get()) == 1, "the stone and scroll were not returned");
        } finally {
            DungeonManager.testLevel = null;
        }
        helper.runAfterDelay(100, helper::succeed);
    }

    @GameTest(template = ARENA)
    public static void scrollsAreCraftedFromBossTrophies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(!DungeonType.CRYPT.requiresScroll() && DungeonType.FORGE.requiresScroll() && DungeonType.FROST.requiresScroll(),
                "only dungeons from level 20 need a scroll");
        assertCrafts(helper, level, List.of(new ItemStack(ModItems.CRYPT_CROWN_SHARD.get()), new ItemStack(ModItems.CRYPT_CROWN_SHARD.get()),
                new ItemStack(ModItems.CRYPT_CROWN_SHARD.get()), new ItemStack(Items.PAPER)), ModItems.FORGE_SCROLL.get().getDefaultInstance());
        assertCrafts(helper, level, List.of(new ItemStack(ModItems.TYRANT_EMBER_CORE.get()), new ItemStack(ModItems.TYRANT_EMBER_CORE.get()),
                new ItemStack(ModItems.TYRANT_EMBER_CORE.get()), new ItemStack(Items.PAPER)), ModItems.FROST_SCROLL.get().getDefaultInstance());
        // Без трофеев свиток не сделать.
        CraftingInput paperOnly = CraftingInput.of(2, 2, List.of(new ItemStack(Items.PAPER), new ItemStack(Items.PAPER),
                new ItemStack(Items.PAPER), new ItemStack(Items.PAPER)));
        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, paperOnly, level).isEmpty(), "paper alone crafts something");

        CryptKing king = helper.spawn(ModEntities.CRYPT_KING.get(), new BlockPos(3, 2, 3));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, king)
                .withParameter(LootContextParams.ORIGIN, king.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(LootContextParamSets.ENTITY);
        List<ItemStack> drops = level.getServer().reloadableRegistries().getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, HeroicPaths.id("entities/crypt_king"))).getRandomItems(params);
        helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(ModItems.CRYPT_CROWN_SHARD.get())), "the crypt king dropped no crown shard: " + drops);
        king.discard();
        helper.succeed();
    }

    private static void assertCrafts(GameTestHelper helper, ServerLevel level, List<ItemStack> items, ItemStack expected) {
        CraftingInput input = CraftingInput.of(2, 2, items);
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        helper.assertTrue(recipe.isPresent(), "no recipe for " + expected);
        ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
        helper.assertTrue(result.is(expected.getItem()), "expected " + expected + ", got " + result);
    }

    // ================================================================ правила данжа

    @GameTest(template = ARENA)
    public static void dungeonBlocksAreProtected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 10);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, Blocks.STONE_BRICKS.defaultBlockState());
        DungeonManager.testLevel = level;
        try {
            helper.assertTrue(!player.gameMode.destroyBlock(pos), "a block was broken inside a dungeon");
            helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE_BRICKS), "the block disappeared");
            level.explode(null, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 2f, Level.ExplosionInteraction.TNT);
            helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE_BRICKS), "an explosion broke a dungeon block");
        } finally {
            DungeonManager.testLevel = null;
        }
        helper.assertTrue(player.gameMode.destroyBlock(pos), "outside of dungeons blocks must break normally");
        helper.succeed();
    }

    /** Спавнеры при постройке превращаются в одну волну монстров: 3 и ещё по одному за каждого игрока сверх первого. */
    @GameTest(template = ARENA, batch = "spawner_waves", timeoutTicks = 400)
    public static void spawnersBecomeOneWave(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(0, 40, 0)).offset(0, 0, 1300);
        List<PoolElementStructurePiece> pieces = DungeonManager.generate(level, DungeonType.CRYPT, origin);
        int spawners = 0;
        for (PoolElementStructurePiece piece : pieces) spawners += countSpawners(level, piece.getBoundingBox());
        helper.assertTrue(spawners >= 2, "the crypt has only " + spawners + " spawners");
        int spawned = DungeonManager.convertSpawners(level, pieces, DungeonType.CRYPT, 2);
        int left = 0;
        for (PoolElementStructurePiece piece : pieces) left += countSpawners(level, piece.getBoundingBox());
        helper.assertTrue(left == 0, left + " spawners are still there");
        helper.assertTrue(spawned == spawners * DungeonType.CRYPT.mobsPerSpawner(2),
                "expected " + spawners * 4 + " monsters, got " + spawned);
        helper.runAfterDelay(100, helper::succeed);
    }

    private static int countSpawners(ServerLevel level, BoundingBox box) {
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            if (level.getBlockState(pos).is(Blocks.SPAWNER)) found++;
        }
        return found;
    }

    // ================================================================ снаряжение

    @GameTest(template = ARENA)
    public static void gearIsWeaponsAndArmorOnly(GameTestHelper helper) {
        for (ItemStack gear : List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.IRON_AXE), new ItemStack(Items.BOW),
                new ItemStack(Items.CROSSBOW), new ItemStack(Items.SHIELD), new ItemStack(Items.TRIDENT), new ItemStack(Items.IRON_CHESTPLATE),
                new ItemStack(ModItems.MAGE_HELMET.get()))) {
            helper.assertTrue(GearRules.isGear(gear), gear + " must count as gear");
        }
        for (ItemStack other : List.of(new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.SHEARS), new ItemStack(Items.FISHING_ROD),
                new ItemStack(Items.ELYTRA), new ItemStack(Items.DIRT))) {
            helper.assertTrue(!GearRules.isGear(other), other + " must not count as gear");
        }
        helper.succeed();
    }

    /** Смерть: надетое снаряжение не выпадает, а теряет 20% прочности; почти сломанное разрушается; остальное выпадает. */
    @GameTest(template = ARENA)
    public static void deathWearsGearInsteadOfDroppingIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 10);
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2.5, 2, 2.5)));
        Inventory inventory = player.getInventory();
        inventory.selected = 0;
        inventory.setItem(0, new ItemStack(Items.DIAMOND_SWORD));
        inventory.setItem(38, new ItemStack(Items.IRON_CHESTPLATE));
        inventory.setItem(Inventory.SLOT_OFFHAND, new ItemStack(Items.SHIELD));
        ItemStack worn = new ItemStack(Items.LEATHER_BOOTS);
        worn.setDamageValue(worn.getMaxDamage() - 2);
        inventory.setItem(36, worn);
        inventory.setItem(5, new ItemStack(Items.DIRT, 16));
        inventory.setItem(6, new ItemStack(Items.IRON_SWORD)); // меч не в руке — обычный предмет, выпадает

        player.die(level.damageSources().generic());

        helper.assertTrue(inventory.getItem(5).isEmpty() && inventory.getItem(6).isEmpty(), "other items must drop as usual");
        helper.assertTrue(inventory.getItem(0).isEmpty() && inventory.getItem(38).isEmpty(), "equipped gear must be stashed, not left in place");
        helper.assertTrue(player.getData(ModAttachments.KEPT_GEAR).entries().size() == 3, "sword, chestplate and shield must be kept, got "
                + player.getData(ModAttachments.KEPT_GEAR).entries().size());

        GearEvents.restore(player);
        ItemStack sword = inventory.getItem(0);
        helper.assertTrue(sword.is(Items.DIAMOND_SWORD) && sword.getDamageValue() == GearRules.deathLoss(sword),
                "the sword must come back with 5% wear, got " + sword + " damage " + sword.getDamageValue());
        helper.assertTrue(inventory.getItem(38).is(Items.IRON_CHESTPLATE) && inventory.getItem(38).getDamageValue() > 0,
                "the chestplate must come back worn");
        helper.assertTrue(inventory.getItem(Inventory.SLOT_OFFHAND).is(Items.SHIELD), "the shield must come back to the off hand");
        helper.assertTrue(inventory.getItem(36).isEmpty(), "the nearly broken boots must be destroyed");
        helper.assertTrue(!player.hasData(ModAttachments.KEPT_GEAR), "kept gear must be cleared after restoring");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void gearCannotBeRepaired(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = player(helper, 10);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));

        // Наковальня: материал и второй такой же предмет не чинят оружие и броню, а кирку — чинят.
        AnvilMenu anvil = new AnvilMenu(1, player.getInventory(), ContainerLevelAccess.create(level, pos));
        anvil.getSlot(0).set(damaged(Items.DIAMOND_SWORD));
        anvil.getSlot(1).set(new ItemStack(Items.DIAMOND));
        anvil.createResult();
        helper.assertTrue(anvil.getSlot(2).getItem().isEmpty(), "the anvil repaired a sword with a diamond");
        anvil.getSlot(1).set(damaged(Items.DIAMOND_SWORD));
        anvil.createResult();
        helper.assertTrue(anvil.getSlot(2).getItem().isEmpty(), "the anvil combined two swords");
        anvil.getSlot(0).set(damaged(Items.DIAMOND_PICKAXE));
        anvil.getSlot(1).set(new ItemStack(Items.DIAMOND));
        anvil.createResult();
        helper.assertTrue(!anvil.getSlot(2).getItem().isEmpty(), "tools must still be repairable on the anvil");
        helper.assertTrue(GearEvents.isBlockedAnvilRepair(damaged(Items.IRON_CHESTPLATE), new ItemStack(Items.IRON_INGOT)),
                "armor repair with ingots must be blocked");
        helper.assertTrue(GearEvents.isBlockedAnvilRepair(damaged(Items.IRON_CHESTPLATE), new ItemStack(Items.ENCHANTED_BOOK)),
                "armor is enchanted only with runes, not with books");
        helper.assertTrue(!GearEvents.isBlockedAnvilRepair(damaged(Items.IRON_PICKAXE), new ItemStack(Items.ENCHANTED_BOOK)),
                "tools still take enchanted books");
        helper.assertTrue(!GearEvents.isBlockedAnvilRepair(damaged(Items.IRON_CHESTPLATE), ItemStack.EMPTY), "renaming gear stays allowed");

        // Точило: два меча не объединяются, две кирки — да.
        GrindstoneMenu grindstone = new GrindstoneMenu(2, player.getInventory(), ContainerLevelAccess.create(level, pos));
        grindstone.getSlot(0).set(damaged(Items.IRON_SWORD));
        grindstone.getSlot(1).set(damaged(Items.IRON_SWORD));
        helper.assertTrue(grindstone.getSlot(2).getItem().isEmpty(), "the grindstone combined two swords");
        grindstone.getSlot(0).set(damaged(Items.IRON_PICKAXE));
        grindstone.getSlot(1).set(damaged(Items.IRON_PICKAXE));
        helper.assertTrue(!grindstone.getSlot(2).getItem().isEmpty(), "tools must still combine on the grindstone");
        player.getInventory().clearContent();

        // Сетка крафта: два меча не чинятся, две кирки чинятся.
        CraftingInput swords = CraftingInput.of(2, 1, List.of(damaged(Items.IRON_SWORD), damaged(Items.IRON_SWORD)));
        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, swords, level).isEmpty(), "two swords were repaired by crafting");
        CraftingInput pickaxes = CraftingInput.of(2, 1, List.of(damaged(Items.IRON_PICKAXE), damaged(Items.IRON_PICKAXE)));
        helper.assertTrue(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, pickaxes, level).isPresent(), "tools must still repair by crafting");

        // Починка опытом: меч не чинится, кирка во второй руке — чинится.
        Holder<Enchantment> mending = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.MENDING);
        ItemStack sword = damaged(Items.DIAMOND_SWORD);
        sword.enchant(mending, 1);
        ItemStack pickaxe = damaged(Items.DIAMOND_PICKAXE);
        pickaxe.enchant(mending, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        player.setItemInHand(InteractionHand.OFF_HAND, pickaxe);
        int swordDamage = sword.getDamageValue();
        int pickaxeDamage = pickaxe.getDamageValue();
        for (int i = 0; i < 5; i++) {
            player.takeXpDelay = 0;
            ExperienceOrb orb = new ExperienceOrb(level, player.getX(), player.getY(), player.getZ(), 7);
            level.addFreshEntity(orb);
            orb.playerTouch(player);
        }
        helper.assertTrue(player.getMainHandItem().getDamageValue() == swordDamage, "mending repaired the sword");
        helper.assertTrue(player.getOffhandItem().getDamageValue() < pickaxeDamage, "mending must still repair tools");
        helper.succeed();
    }

    private static ItemStack damaged(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue(stack.getMaxDamage() / 2);
        return stack;
    }
}
