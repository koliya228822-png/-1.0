package com.heroicpaths.village;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.network.TradeRerollPayload;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Реролл товаров жителя-торговца: кнопка в окне торговли заново раскладывает его товары — для каждого уровня,
 * до которого житель дорос, как при обычном повышении (по два товара с уровня). Стоит 3 изумруда, каждый следующий
 * реролл у этого жителя — на изумруд дороже; утром (с началом нового игрового дня) цена снова 3.
 * Реролл немного растит торговца — как маленькая сделка.
 * Оружейник, ювелир, огранщик и книжник раскладывают новый дневной набор ({@link MerchantPosts#reroll}).
 * У банкира и скупщика реролла нет: они только покупают.
 */
public final class TradeReroll {
    private static final String DAY = HeroicPaths.MODID + ".reroll_day";
    private static final String COUNT = HeroicPaths.MODID + ".reroll_count";
    public static final int BASE_PRICE = 3;
    /** Опыт торговца за один реролл (сделки дают 1–30). */
    public static final int VILLAGER_XP = 1;
    /** Товаров с каждого уровня, как у ванильного жителя. */
    private static final int OFFERS_PER_LEVEL = 2;

    private TradeReroll() {}

    /** Окно торговли открылось: клиент узнаёт цену реролла (или что его нет). */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getContainer() instanceof MerchantMenu) || !(event.getEntity() instanceof ServerPlayer player)) return;
        Villager villager = tradingVillager(player);
        send(player, villager != null && canReroll(villager) ? price(villager) : -1);
    }

    private static void send(ServerPlayer player, int price) {
        // У клиента без мода (и у тестовых игроков) канала нет — отправка бросила бы исключение.
        if (player.connection == null || !player.connection.hasChannel(TradeRerollPayload.TYPE)) return;
        PacketDistributor.sendToPlayer(player, new TradeRerollPayload(price));
    }

    /** Можно ли рероллить товары этого жителя. */
    public static boolean canReroll(Villager villager) {
        if (villager.isBaby()) return false;
        VillagerProfession profession = villager.getVillagerData().getProfession();
        // Из торговцев за прилавком товар меняют все, кроме банкира и скупщика: те только покупают.
        if (MerchantPosts.isPost(profession)) return MerchantPosts.isDaily(profession);
        if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) return false;
        Int2ObjectMap<VillagerTrades.ItemListing[]> trades = VillagerTrades.TRADES.get(profession);
        return trades != null && !trades.isEmpty();
    }

    /** Цена следующего реролла у этого жителя сегодня. */
    public static int price(Villager villager) {
        return BASE_PRICE + rollsToday(villager);
    }

    private static int rollsToday(Villager villager) {
        var data = villager.getPersistentData();
        long today = MerchantPosts.day((ServerLevel) villager.level());
        return data.getLong(DAY) == today + 1 ? data.getInt(COUNT) : 0;
    }

    /** Житель, с которым сейчас торгует игрок. */
    @Nullable
    static Villager tradingVillager(ServerPlayer player) {
        List<Villager> list = player.serverLevel().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(16),
                v -> v.getTradingPlayer() == player);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Нажата кнопка реролла. */
    public static void reroll(ServerPlayer player) {
        if (!(player.containerMenu instanceof MerchantMenu menu)) return;
        Villager villager = tradingVillager(player);
        if (villager == null || !canReroll(villager)) return;
        int price = price(villager);
        if (!player.getAbilities().instabuild && !pay(player, price)) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.reroll_cost", price).withStyle(ChatFormatting.RED), true);
            return;
        }
        reroll(villager, player.getRandom());
        var data = villager.getPersistentData();
        data.putLong(DAY, MerchantPosts.day(player.serverLevel()) + 1);
        data.putInt(COUNT, rollsToday(villager) + 1);
        // Скидки ученика и харизмы снова ложатся на новые товары.
        MentorEvents.applyStudentDiscount(player, villager);
        MentorEvents.applyCharismaDiscount(player, villager);
        // Немного опыта торговцу — как маленькая сделка (повышение, если хватило, — когда закроют окно).
        villager.notifyTrade(new MerchantOffer(new ItemCost(Items.EMERALD, price), ItemStack.EMPTY, 1, VILLAGER_XP, 0f));
        menu.setSelectionHint(0);
        player.sendMerchantOffers(menu.containerId, villager.getOffers(), villager.getVillagerData().getLevel(), villager.getVillagerXp(),
                villager.showProgressBar(), villager.canRestock());
        send(player, price(villager));
        player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.8, villager.getZ(), 8, 0.3, 0.3, 0.3, 0);
        player.serverLevel().playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
    }

    /** Заново раскладывает товары: по два с каждого уровня от новичка до текущего (у торговцев за прилавком — новый дневной набор). */
    public static void reroll(Villager villager, RandomSource random) {
        if (MerchantPosts.isPost(villager)) {
            if (villager.level() instanceof ServerLevel level) MerchantPosts.reroll(level, villager);
            return;
        }
        Int2ObjectMap<VillagerTrades.ItemListing[]> trades = VillagerTrades.TRADES.get(villager.getVillagerData().getProfession());
        MerchantOffers offers = villager.getOffers();
        offers.clear();
        if (trades == null) return;
        int level = villager.getVillagerData().getLevel();
        for (int l = 1; l <= level; l++) {
            VillagerTrades.ItemListing[] listings = trades.get(l);
            if (listings == null) continue;
            List<VillagerTrades.ItemListing> pool = new ArrayList<>(Arrays.asList(listings));
            int added = 0;
            while (added < OFFERS_PER_LEVEL && !pool.isEmpty()) {
                MerchantOffer offer = pool.remove(random.nextInt(pool.size())).getOffer(villager, random);
                if (offer != null) {
                    offers.add(offer);
                    added++;
                }
            }
        }
    }

    /** Забирает изумруды из инвентаря; false — не хватает. */
    private static boolean pay(ServerPlayer player, int price) {
        var inventory = player.getInventory();
        if (inventory.countItem(Items.EMERALD) < price) return false;
        int left = price;
        for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(Items.EMERALD)) continue;
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
        }
        inventory.setChanged();
        return true;
    }
}
