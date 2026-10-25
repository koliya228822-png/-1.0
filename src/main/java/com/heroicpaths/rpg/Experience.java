package com.heroicpaths.rpg;

import com.heroicpaths.craft.Profession;
import com.heroicpaths.craft.ProfessionProgress;
import com.heroicpaths.entity.DungeonBoss;
import com.heroicpaths.party.Party;
import com.heroicpaths.party.PartyManager;
import com.heroicpaths.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Опыт сверх убийств:
 * <ul>
 *     <li>в группе опыт за моба получают все участники рядом, кто бы ни нанёс последний удар: каждый — 80% своего
 *     опыта за этого моба, плюс 7% за каждого участника, получившего опыт;</li>
 *     <li>с боссов с шансом 20% выпадает камень предков (1500 опыта);</li>
 *     <li>ванильный опыт можно перевести в опыт персонажа (1:1) или ремесла (5:1).</li>
 * </ul>
 */
public final class Experience {
    public static final float PARTY_SHARE = 0.8f;
    public static final float PARTY_BONUS_PER_MEMBER = 0.07f;
    /** Участники дальше этого от добычи опыта не получают. */
    public static final double PARTY_RANGE = 64;
    public static final float ANCESTOR_STONE_CHANCE = 0.2f;
    public static final int ANCESTOR_STONE_XP = 1500;
    /** Сколько очков ванильного опыта стоит единица опыта ремесла. */
    public static final int VANILLA_PER_CRAFT_XP = 5;

    private Experience() {}

    /** Опыт за убийство: одному игроку — как раньше, группе — всем участникам рядом. */
    public static void awardKill(ServerPlayer killer, LivingEntity victim) {
        List<ServerPlayer> members = partyNear(killer, victim);
        if (members.size() <= 1) {
            RpgEvents.awardKillXp(killer, victim);
            return;
        }
        float factor = partyFactor(members.size());
        for (ServerPlayer member : members) {
            float xp = RpgEvents.killXp(member, victim);
            if (xp > 0) RpgManager.addXp(member, Math.max(1, Math.round(xp * factor)));
        }
    }

    /** Доля опыта каждого участника группы: 80% × (1 + 7% × участников). */
    public static float partyFactor(int members) {
        return PARTY_SHARE * (1f + PARTY_BONUS_PER_MEMBER * members);
    }

    /** Участники группы убийцы в том же мире и не дальше PARTY_RANGE от жертвы (сам убийца — всегда). */
    public static List<ServerPlayer> partyNear(ServerPlayer killer, LivingEntity victim) {
        List<ServerPlayer> result = new ArrayList<>();
        result.add(killer);
        Party party = PartyManager.partyOf(killer);
        if (party == null) return result;
        for (UUID id : party.members()) {
            if (id.equals(killer.getUUID())) continue;
            ServerPlayer member = killer.server.getPlayerList().getPlayer(id);
            if (member != null && member.isAlive() && member.level() == victim.level() && member.distanceTo(victim) <= PARTY_RANGE) {
                result.add(member);
            }
        }
        return result;
    }

    /** Камень предков с боссов. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof DungeonBoss) || entity.level().isClientSide) return;
        if (entity.getRandom().nextFloat() >= ANCESTOR_STONE_CHANCE) return;
        event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY() + 0.5, entity.getZ(),
                new ItemStack(ModItems.ANCESTOR_STONE.get())));
    }

    /**
     * Перевод всего ванильного опыта: в опыт персонажа (profession == null) или ремесла.
     * Возвращает false, если переводить нечего.
     */
    public static boolean convertVanilla(ServerPlayer player, @Nullable Profession profession) {
        int points = totalPoints(player);
        RpgData data = RpgManager.get(player);
        if (points <= 0 || (profession == null && (data.getPlayerClass() == PlayerClass.NONE || data.getLevel() >= RpgData.MAX_LEVEL))) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.convert_nothing").withStyle(ChatFormatting.RED), true);
            return false;
        }
        int gained = profession == null ? points : points / VANILLA_PER_CRAFT_XP;
        if (gained <= 0) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.convert_nothing").withStyle(ChatFormatting.RED), true);
            return false;
        }
        int spent = profession == null ? points : gained * VANILLA_PER_CRAFT_XP;
        player.giveExperiencePoints(-spent);
        if (profession == null) RpgManager.addXp(player, gained);
        else ProfessionProgress.award(player, profession, gained);
        player.sendSystemMessage(Component.translatable("message.heroicpaths.converted", spent, gained,
                profession == null ? Component.translatable("screen.heroicpaths.convert.character") : profession.displayName())
                .withStyle(ChatFormatting.GREEN));
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS, 0.8f, 0.8f);
        return true;
    }

    /** Весь ванильный опыт игрока в очках (уровни и полоска текущего). */
    public static int totalPoints(net.minecraft.world.entity.player.Player player) {
        int points = 0;
        for (int level = 0; level < player.experienceLevel; level++) points += xpForLevel(level);
        return points + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    /** Сколько очков нужно, чтобы с уровня level перейти на следующий (как в ванилле). */
    static int xpForLevel(int level) {
        if (level >= 30) return 112 + (level - 30) * 9;
        return level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2;
    }
}
