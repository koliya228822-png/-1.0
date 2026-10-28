package com.heroicpaths.gametest;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.travel.Waystones;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Камни перемещения: от камня к камню бесплатно, свиток тратится, к незнакомой точке не перенести. */
@GameTestHolder(HeroicPaths.MODID)
@PrefixGameTestTemplate(false)
public class WaystoneGameTests {
    private static final String ARENA = "gametest_arena";

    @SuppressWarnings("removal")
    @GameTest(template = ARENA)
    public static void waystonesTeleportBetweenKnownPoints(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos a = helper.absolutePos(new BlockPos(1, 2, 1)), b = helper.absolutePos(new BlockPos(6, 2, 6));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Waystones data = Waystones.get(level.getServer());
        try {
            for (BlockPos pos : new BlockPos[]{a, b}) {
                level.setBlockAndUpdate(pos, ModBlocks.WAYSTONE.get().defaultBlockState());
                data.add(level, pos, null, ItemStack.EMPTY);
            }
            player.teleportTo(a.getX() + 0.5, a.getY() + 1, a.getZ() + 1.5);
            // Незнакомая точка.
            helper.assertTrue(Waystones.travel(player, level.dimension().location(), b, false, a) != null, "travelled to an unknown waystone");
            Waystones.activate(player, a);
            Waystones.activate(player, b);
            player.teleportTo(a.getX() + 0.5, a.getY() + 1, a.getZ() + 1.5);
            helper.assertTrue(Waystones.travel(player, level.dimension().location(), b, false, a) == null, "could not travel from a to b");
            helper.assertTrue(player.blockPosition().closerThan(b, 2.5), "player is at " + player.blockPosition() + ", not near " + b);
            // Свиток: без него нельзя, с ним — переносит и тратится.
            helper.assertTrue(Waystones.travel(player, level.dimension().location(), a, true, BlockPos.ZERO) != null, "travelled by scroll without a scroll");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.TELEPORT_SCROLL.get(), 2));
            helper.assertTrue(Waystones.travel(player, level.dimension().location(), a, true, BlockPos.ZERO) == null, "scroll travel failed");
            helper.assertTrue(player.blockPosition().closerThan(a, 2.5), "scroll did not bring the player to " + a);
            helper.assertTrue(player.getMainHandItem().getCount() == 1, "scroll was not used up");
            // Сломанный камень исчезает из списка.
            level.removeBlock(b, false);
            helper.assertTrue(data.at(level.dimension(), b) == null, "a broken waystone is still listed");
        } finally {
            data.remove(level.dimension(), a);
            data.remove(level.dimension(), b);
        }
        helper.succeed();
    }
}
