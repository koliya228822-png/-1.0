package com.heroicpaths.craft;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Ремёсла игрока: опыт и ступень каждого. Ступени: новичок → подмастерье → мастер-ремесленник → эксперт → мастер.
 * Опыт даёт крафт, а следующую ступень открывает трактат от жителей-мастеров, когда опыта хватает.
 * Выше мастера-ремесленника можно подняться только в одном ремесле — оно становится мастерством игрока.
 */
public final class Professions {
    public static final int MIN_RANK = 1;
    public static final int MAX_RANK = 5;
    /** Потолок для всех ремёсел, кроме выбранного мастерства. */
    public static final int MAX_SECONDARY_RANK = 3;
    /** Сколько всего опыта ремесла нужно для ступени (индекс — ступень). */
    private static final int[] XP_FOR_RANK = {0, 0, 100, 400, 1000, 2000};

    private final int[] xp = new int[Profession.values().length];
    private final int[] ranks = new int[Profession.values().length];
    @Nullable
    private Profession mastery;

    public Professions() {
        java.util.Arrays.fill(ranks, MIN_RANK);
    }

    public static int xpForRank(int rank) {
        return XP_FOR_RANK[Math.max(MIN_RANK, Math.min(MAX_RANK, rank))];
    }

    public static MutableComponent rankName(int rank) {
        return Component.translatable("profession_rank.heroicpaths." + Math.max(MIN_RANK, Math.min(MAX_RANK, rank)));
    }

    public int rank(Profession profession) {
        return ranks[profession.ordinal()];
    }

    public int xp(Profession profession) {
        return xp[profession.ordinal()];
    }

    @Nullable
    public Profession mastery() {
        return mastery;
    }

    /** Самая высокая ступень, доступная в ремесле: мастер для выбранного мастерства (или пока оно не выбрано). */
    public int maxRank(Profession profession) {
        return mastery == null || mastery == profession ? MAX_RANK : MAX_SECONDARY_RANK;
    }

    /** Добавляет опыт. Возвращает true, если опыта впервые хватило на следующую ступень. */
    public boolean addXp(Profession profession, int amount) {
        int before = xp[profession.ordinal()];
        xp[profession.ordinal()] = (int) Math.min(Integer.MAX_VALUE, (long) before + Math.max(0, amount));
        int next = rank(profession) + 1;
        return next <= maxRank(profession) && before < xpForRank(next) && xp[profession.ordinal()] >= xpForRank(next);
    }

    public enum Check { OK, ALREADY_KNOWN, NEED_PREVIOUS, NEED_XP, MASTERY_TAKEN }

    /** Можно ли подняться в ремесле до ступени target по трактату. */
    public Check check(Profession profession, int target) {
        int rank = rank(profession);
        if (rank >= target) return Check.ALREADY_KNOWN;
        if (rank + 1 < target) return Check.NEED_PREVIOUS;
        if (target > maxRank(profession)) return Check.MASTERY_TAKEN;
        if (xp(profession) < xpForRank(target)) return Check.NEED_XP;
        return Check.OK;
    }

    /** Следующая ступень. Ступень выше мастера-ремесленника закрепляет мастерство за этим ремеслом. */
    public void rankUp(Profession profession) {
        int rank = Math.min(MAX_RANK, rank(profession) + 1);
        ranks[profession.ordinal()] = rank;
        if (rank > MAX_SECONDARY_RANK) mastery = profession;
    }

    /** Для тестов и команд: ставит ступень напрямую (опыт подтягивается до её порога). */
    public void setRank(Profession profession, int rank) {
        rank = Math.max(MIN_RANK, Math.min(MAX_RANK, rank));
        ranks[profession.ordinal()] = rank;
        xp[profession.ordinal()] = Math.max(xp[profession.ordinal()], xpForRank(rank));
        if (rank > MAX_SECONDARY_RANK) mastery = profession;
        else if (mastery == profession) mastery = null;
    }

    /** Свиток забвения: мастерство снимается, ремесло опускается до мастера-ремесленника. */
    public boolean resetMastery() {
        if (mastery == null) return false;
        ranks[mastery.ordinal()] = Math.min(ranks[mastery.ordinal()], MAX_SECONDARY_RANK);
        mastery = null;
        return true;
    }

    /** «Кузнец: подмастерье» в цвете ремесла. */
    public MutableComponent describe(Profession profession) {
        return profession.displayName().append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(rankName(rank(profession)).withStyle(ChatFormatting.WHITE));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("xp", xp.clone());
        tag.putIntArray("ranks", ranks.clone());
        if (mastery != null) tag.putString("mastery", mastery.id());
        return tag;
    }

    public void load(CompoundTag tag) {
        java.util.Arrays.fill(xp, 0);
        java.util.Arrays.fill(ranks, MIN_RANK);
        int[] savedXp = tag.getIntArray("xp");
        int[] savedRanks = tag.getIntArray("ranks");
        for (int i = 0; i < xp.length; i++) {
            if (i < savedXp.length) xp[i] = Math.max(0, savedXp[i]);
            if (i < savedRanks.length) ranks[i] = Math.max(MIN_RANK, Math.min(MAX_RANK, savedRanks[i]));
        }
        mastery = Profession.byId(tag.getString("mastery"));
    }
}
