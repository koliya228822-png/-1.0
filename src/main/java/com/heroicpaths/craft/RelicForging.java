package com.heroicpaths.craft;

import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Реликвии не крафтятся: легендарную вещь поднимают до реликвии на алтаре реликвий
 * за Искру реликвии (редкая добыча с боссов данжей) и уровни опыта.
 */
public final class RelicForging {
    /** Сколько уровней опыта стоит обряд. */
    public static final int XP_LEVELS = 10;

    private RelicForging() {}

    /** Поднимает вещь в руке до реликвии. Возвращает true, если получилось. */
    public static boolean upgrade(ServerPlayer player, ItemStack stack, BlockPos altar) {
        ItemRarity.Tier tier = ItemRarity.get(stack);
        Component problem = null;
        if (tier == ItemRarity.Tier.RELIC) problem = Component.translatable("message.heroicpaths.relic_already");
        else if (tier != ItemRarity.Tier.LEGENDARY) problem = Component.translatable("message.heroicpaths.relic_need_legendary");
        else if (player.getInventory().countItem(ModItems.RELIC_SPARK.get()) < 1) problem = Component.translatable("message.heroicpaths.relic_need_spark");
        else if (!player.getAbilities().instabuild && player.experienceLevel < XP_LEVELS) {
            problem = Component.translatable("message.heroicpaths.relic_need_xp", XP_LEVELS);
        }
        if (problem != null) {
            player.displayClientMessage(problem.copy().withStyle(ChatFormatting.RED), true);
            return false;
        }
        takeSpark(player);
        if (!player.getAbilities().instabuild) player.giveExperienceLevels(-XP_LEVELS);
        ItemRarity.upgradeToRelic(stack, player.getRandom());

        player.sendSystemMessage(Component.translatable("message.heroicpaths.relic_forged", stack.getHoverName()).withStyle(ChatFormatting.GOLD));
        var level = player.serverLevel();
        double x = altar.getX() + 0.5, y = altar.getY() + 1.0, z = altar.getZ() + 0.5;
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 40, 0.4, 0.5, 0.4, 0.05);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.5, z, 30, 0.3, 0.6, 0.3, 0.08);
        level.playSound(null, altar, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1f, 0.8f);
        level.playSound(null, altar, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.1f);
        return true;
    }

    private static void takeSpark(ServerPlayer player) {
        for (var list : java.util.List.of(player.getInventory().items, player.getInventory().offhand)) {
            for (ItemStack stack : list) {
                if (stack.is(ModItems.RELIC_SPARK.get())) {
                    stack.shrink(1);
                    return;
                }
            }
        }
    }
}
