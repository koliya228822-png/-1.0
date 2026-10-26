package com.heroicpaths.craft;

import com.heroicpaths.item.ProfessionManualItem;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Рост ремёсел: опыт за крафт и новые ступени по трактатам. */
public final class ProfessionProgress {
    private ProfessionProgress() {}

    /** Опыт ремесла; когда его впервые хватает на следующую ступень, игрок узнаёт, какой трактат искать. */
    public static void award(ServerPlayer player, Profession profession, int amount) {
        if (amount <= 0) return;
        Professions professions = RpgManager.get(player).professions();
        if (professions.addXp(profession, amount)) {
            int next = professions.rank(profession) + 1;
            player.sendSystemMessage(Component.translatable("message.heroicpaths.craft_rank_ready", profession.displayName(),
                    Professions.rankName(next), ProfessionManualItem.create(profession.manual(), next).getHoverName())
                    .withStyle(ChatFormatting.GOLD));
        }
        RpgManager.sync(player);
    }

    /** Чтение трактата: при достаточном опыте ремесло поднимается до его ступени. Возвращает true, если трактат прочитан. */
    public static boolean readManual(ServerPlayer player, Profession profession, int rank) {
        Professions professions = RpgManager.get(player).professions();
        Component message = switch (professions.check(profession, rank)) {
            case ALREADY_KNOWN -> Component.translatable("message.heroicpaths.manual_known", profession.displayName(),
                    Professions.rankName(professions.rank(profession)));
            case NEED_PREVIOUS -> Component.translatable("message.heroicpaths.manual_previous", Professions.rankName(rank - 1));
            case NEED_XP -> Component.translatable("message.heroicpaths.manual_need_xp", professions.xp(profession),
                    Professions.xpForRank(rank), profession.displayName());
            case OK -> null;
        };
        if (message != null) {
            player.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
            return false;
        }
        professions.rankUp(profession);
        player.sendSystemMessage(Component.translatable("message.heroicpaths.craft_rank_up", profession.displayName(), Professions.rankName(rank)).withStyle(ChatFormatting.GOLD));
        player.serverLevel().sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1, player.getZ(), 40, 0.5, 0.8, 0.5, 0.4);
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.7f, 1.1f);
        RpgManager.sync(player);
        return true;
    }
}
