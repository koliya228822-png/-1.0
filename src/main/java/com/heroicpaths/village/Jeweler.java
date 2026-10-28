package com.heroicpaths.village;

import com.heroicpaths.accessory.AccessoryItem;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Ювелир в ювелирной лавке деревни: каждый игровой день выставляет восемь украшений — кольца, серьги и ожерелья
 * с редкостью, как у оружейника. Грейд растёт с уровнем торговца: новичок продаёт медь с аметистом (I), ученик —
 * ещё и грейд II, эксперт — до III. Алмазные украшения (IV–VI) только делают у стола ювелира.
 * Купленное до конца дня не возвращается.
 */
public final class Jeweler {
    public static final int OFFERS = 8;
    private static final List<Supplier<? extends Item>> PIECES = List.of(ModItems.RING, ModItems.EARRING, ModItems.NECKLACE);
    /** Цена по грейду (индекс — грейд). */
    private static final int[] GRADE_PRICE = {0, 12, 20, 32};
    /** Ожерелье дороже кольца и серьги. */
    private static final int NECKLACE_EXTRA = 4;

    private Jeweler() {}

    /** Наибольший грейд украшений у торговца этого уровня. */
    static int maxGrade(int merchantLevel) {
        return merchantLevel >= 4 ? 3 : merchantLevel >= 2 ? 2 : 1;
    }

    /** Восемь украшений на этот день; один и тот же день (и реролл) у одного ювелира — один и тот же набор. */
    public static List<MerchantOffer> offers(ServerLevel level, long seed, long day, int merchantLevel) {
        RandomSource random = RandomSource.create(seed * 37 + day * 0x9E3779B97F4A7C15L);
        List<MerchantOffer> list = new ArrayList<>();
        int top = maxGrade(merchantLevel);
        for (int i = 0; i < OFFERS; i++) {
            Item piece = PIECES.get(random.nextInt(PIECES.size())).get();
            int grade = 1 + random.nextInt(top);
            ItemStack stack = AccessoryItem.withGrade(piece, grade);
            ItemRarity.Tier tier = MerchantPosts.rollTier(random, merchantLevel);
            ItemRarity.apply(stack, tier, random, level.registryAccess());
            int price = price(stack, grade, tier);
            list.add(MerchantPosts.priced(stack, price, Math.min(30, 5 + price / 4)));
        }
        return list;
    }

    public static int price(ItemStack stack, int grade, ItemRarity.Tier tier) {
        int base = GRADE_PRICE[Math.max(1, Math.min(3, grade))];
        if (stack.is(ModItems.NECKLACE.get())) base += NECKLACE_EXTRA;
        return MerchantPosts.byRarity(base, tier);
    }
}
