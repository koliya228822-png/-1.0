package com.heroicpaths.village;

import com.heroicpaths.craft.Profession;
import com.heroicpaths.craft.Professions;
import com.heroicpaths.item.ProfessionManualItem;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Книжник в книжной лавке деревни: продаёт трактаты всех ремёсел и всех ступеней за изумруды. Чем выше ступень
 * трактата, тем он дороже. Товар не кончается: распроданный трактат снова появляется, когда к книжнику подходят.
 */
public final class Bookseller {
    /** Цена трактата по ступени (индекс — ступень): подмастерье 8 … легендарный мастер 64 изумруда. */
    private static final int[] PRICE = {0, 0, 8, 18, 36, 64};
    public static final int USES = 16;

    private Bookseller() {}

    public static int price(int rank) {
        return PRICE[Math.max(Professions.MIN_RANK + 1, Math.min(Professions.MAX_RANK, rank))];
    }

    /** Все трактаты: по ремёслам, от младшей ступени к старшей. */
    public static List<MerchantOffer> offers() {
        List<MerchantOffer> list = new ArrayList<>();
        for (Profession craft : Profession.values()) {
            for (int rank = Professions.MIN_RANK + 1; rank <= Professions.MAX_RANK; rank++) {
                MerchantOffer offer = MerchantPosts.priced(ProfessionManualItem.create(craft.manual(), rank), price(rank));
                list.add(new MerchantOffer(offer.getItemCostA(), offer.getItemCostB(), offer.getResult(), 0, USES, 0, 0f));
            }
        }
        return list;
    }
}
