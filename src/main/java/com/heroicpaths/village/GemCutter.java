package com.heroicpaths.village;

import com.heroicpaths.registry.ModItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Огранщик в лавке самоцветов: торгует драгоценными камнями. Каждый игровой день выкладывает до восьми разных
 * камней; чем выше уровень торговца, тем дороже камни на прилавке: новичок — аметист, лазурит, кварц и топаз,
 * ученик — рубины и сапфиры, подмастерье — алмазы, эксперт — гранёные алмазы, мастер — бриллианты.
 */
public final class GemCutter {
    public static final int OFFERS = 8;
    /** Сколько раз можно купить каждый камень, пока огранщик не пополнит запас. */
    public static final int USES = 4;

    /** Камень на прилавке: сколько штук за раз, цена в изумрудах и уровень торговца, с которого он есть. */
    record Gem(Supplier<? extends ItemLike> item, int count, int price, int level) {}

    static final List<Gem> GEMS = List.of(
            new Gem(() -> Items.AMETHYST_SHARD, 4, 2, 1),
            new Gem(() -> Items.LAPIS_LAZULI, 8, 2, 1),
            new Gem(() -> Items.QUARTZ, 8, 3, 1),
            new Gem(ModItems.TOPAZ, 1, 3, 1),
            new Gem(ModItems.RUBY, 1, 5, 2),
            new Gem(ModItems.SAPPHIRE, 1, 5, 2),
            new Gem(() -> Items.AMETHYST_BLOCK, 1, 6, 2),
            new Gem(() -> Items.DIAMOND, 1, 9, 3),
            new Gem(() -> Items.ECHO_SHARD, 1, 8, 3),
            new Gem(ModItems.FACETED_DIAMOND, 1, 18, 4),
            new Gem(ModItems.BRILLIANT, 1, 34, 5));

    private GemCutter() {}

    /** Камни на этот день: разные, из доступных торговцу этого уровня; один день (и реролл) — один набор. */
    public static List<MerchantOffer> offers(long seed, long day, int merchantLevel) {
        RandomSource random = RandomSource.create(seed * 41 + day * 0x9E3779B97F4A7C15L);
        List<Gem> pool = new ArrayList<>(GEMS.stream().filter(g -> g.level() <= merchantLevel).toList());
        List<MerchantOffer> list = new ArrayList<>();
        // Самый дорогой камень уровня всегда на прилавке, из остальных одного-двух нет: что именно — решает день (и реролл).
        Gem best = pool.remove(pool.size() - 1);
        List<Gem> picked = new ArrayList<>();
        int others = Math.min(OFFERS - 1, pool.size() - 1);
        while (picked.size() < others) picked.add(pool.remove(random.nextInt(pool.size())));
        picked.add(best);
        picked.sort((a, b) -> Integer.compare(GEMS.indexOf(a), GEMS.indexOf(b)));
        for (Gem gem : picked) {
            // Цена дня гуляет на ±20%.
            int price = Math.max(1, Math.round(gem.price() * (0.8f + random.nextFloat() * 0.4f)));
            list.add(new MerchantOffer(new ItemCost(Items.EMERALD, price), new ItemStack(gem.item().get(), gem.count()),
                    USES, Math.min(20, 2 + price / 2), 0.05f));
        }
        return list;
    }
}
