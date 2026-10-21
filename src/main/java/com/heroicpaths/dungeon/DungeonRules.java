package com.heroicpaths.dungeon;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Правила измерения данжей: блоки нельзя ломать, ставить и двигать (кроме творческого режима),
 * взрывы и мобы их не разрушают, а монстры не появляются заново — только те, что были при постройке.
 */
public final class DungeonRules {
    private DungeonRules() {}

    private static boolean protectedLevel(@Nullable LevelAccessor level) {
        return level instanceof Level real && DungeonManager.isDungeonLevel(real);
    }

    /** Творческий режим проверяем по режиму игры сервера: так правило работает и для тестовых игроков. */
    private static boolean exempt(@Nullable Entity entity) {
        return entity instanceof ServerPlayer player && player.gameMode.getGameModeForPlayer().isCreative();
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (protectedLevel(event.getLevel()) && !exempt(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (protectedLevel(event.getLevel()) && !exempt(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onToolModify(BlockEvent.BlockToolModificationEvent event) {
        if (protectedLevel(event.getLevel()) && !exempt(event.getPlayer())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (protectedLevel(event.getLevel())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (protectedLevel(event.getLevel())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (protectedLevel(event.getLevel())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (protectedLevel(event.getLevel())) event.getAffectedBlocks().clear();
    }

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        if (protectedLevel(event.getEntity().level())) event.setCanGrief(false);
    }

    /** Вёдра, огниво и огненные заряды внутри данжа не действуют на блоки. */
    @SubscribeEvent
    public static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!protectedLevel(player.level()) || exempt(player)) return;
        ItemStack stack = event.getItemStack();
        if (stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE) || stack.getItem() instanceof net.minecraft.world.item.BucketItem) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (protectedLevel(player.level()) && !exempt(player) && event.getItemStack().getItem() instanceof net.minecraft.world.item.BucketItem) {
            event.setCanceled(true);
        }
    }

    /** Монстры не возрождаются: в данже не работают ни спавнеры, ни естественное появление. */
    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!protectedLevel(event.getLevel())) return;
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.NATURAL || type == MobSpawnType.SPAWNER || type == MobSpawnType.CHUNK_GENERATION
                || type == MobSpawnType.PATROL || type == MobSpawnType.REINFORCEMENT) {
            event.setSpawnCancelled(true);
        }
    }
}
