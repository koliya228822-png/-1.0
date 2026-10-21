package com.heroicpaths.item;

import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Сбрасывает класс и все навыки, позволяя выбрать новый путь. */
public class OblivionScrollItem extends Item {
    public OblivionScrollItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // Shift: забыть мастерство ремесла (класс остаётся).
        if (player.isShiftKeyDown()) {
            if (RpgManager.get(player).professions().mastery() == null) {
                if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.heroicpaths.no_mastery"), true);
                return InteractionResultHolder.fail(stack);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                var mastery = RpgManager.get(player).professions().mastery();
                RpgManager.get(player).professions().resetMastery();
                RpgManager.sync(serverPlayer);
                serverPlayer.sendSystemMessage(Component.translatable("message.heroicpaths.mastery_reset", mastery.displayName())
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.6f, 1.4f);
            }
            stack.consume(1, player);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (RpgManager.get(player).getPlayerClass() == PlayerClass.NONE) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.heroicpaths.no_class"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            RpgManager.resetClass(serverPlayer);
            serverPlayer.sendSystemMessage(Component.translatable("message.heroicpaths.class_reset")
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            serverPlayer.serverLevel().sendParticles(ParticleTypes.ENCHANT,
                    player.getX(), player.getY() + 1, player.getZ(), 60, 0.5, 0.8, 0.5, 0.5);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.6f, 1.2f);
        }
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.heroicpaths.oblivion_scroll.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.heroicpaths.oblivion_scroll.mastery").withStyle(ChatFormatting.DARK_GRAY));
    }
}
