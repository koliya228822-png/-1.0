package com.heroicpaths.village;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.item.Grades;
import com.heroicpaths.item.GreatswordItem;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Банкир и мастер-оружейник стоят за своими прилавками и не покидают их: «дом» каждого — клетка, где он появился
 * в своём доме, отойти от неё он может на пару шагов, а сдвинутого (толпой, мобом, игроком, лодкой) сразу ставит
 * обратно. Товары обоих задаются здесь, а не обычными уровнями жителя:
 * банкир всегда скупает добычу из данжей за изумруды, оружейник каждый игровой день выставляет десять новых
 * мечей и луков (с шансом редких, с бонусами и чарами) за большие деньги; купленное до конца дня не возвращается.
 */
public final class MerchantPosts {
    private static final String STOCK = HeroicPaths.MODID + ".stock";
    private static final String SOLD = HeroicPaths.MODID + ".sold";
    /** Версия списка банкира: при изменении цен старые банкиры получают новый список. */
    private static final long BANK_STOCK = 2;
    /** Насколько далеко от своей клетки торговец может отойти. */
    public static final int POST_LEASH = 2;
    private static final double MAX_AWAY = 2.5;
    public static final int CHECK_TICKS = 5;
    public static final int ARMS_OFFERS = 10;

    private MerchantPosts() {}

    public static boolean isPost(Villager villager) {
        var p = villager.getVillagerData().getProfession();
        return p == ModVillagers.BANKER.get() || p == ModVillagers.ARMS_DEALER.get();
    }

    // ---------------------------------------------------------------- пост

    /** Торговец, появившийся в своём доме, запоминает клетку появления как пост. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Villager villager) || !isPost(villager)) return;
        if (MentorHomes.home(villager) == null) setPost(villager, villager.blockPosition());
    }

    public static void setPost(Villager villager, BlockPos post) {
        villager.getPersistentData().putLong(MentorHomes.HOME, post.asLong());
        villager.restrictTo(post, POST_LEASH);
    }

    /** Одна проверка поста: не ходить на сборы и в чужие постели, ушедшего — сразу обратно. */
    public static void keepPost(ServerLevel level, Villager villager) {
        BlockPos post = MentorHomes.home(villager);
        if (post == null) {
            post = villager.blockPosition();
            setPost(villager, post);
        } else if (!villager.hasRestriction() || villager.getRestrictRadius() > POST_LEASH) {
            villager.restrictTo(post, POST_LEASH);
        }
        MentorHomes.releaseIf(villager, MemoryModuleType.MEETING_POINT, level, post, -1);
        MentorHomes.releaseIf(villager, MemoryModuleType.HOME, level, post, 4);
        MentorHomes.releaseIf(villager, MemoryModuleType.JOB_SITE, level, post, 4);
        MentorHomes.releaseIf(villager, MemoryModuleType.POTENTIAL_JOB_SITE, level, post, 4);
        Brain<Villager> brain = villager.getBrain();
        BlockPos home = post;
        brain.getMemory(MemoryModuleType.WALK_TARGET).ifPresent(target -> {
            if (!target.getTarget().currentBlockPosition().closerThan(home, POST_LEASH + 1)) {
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                brain.eraseMemory(MemoryModuleType.PATH);
            }
        });
        if (villager.isSleeping()) return;
        double dx = villager.getX() - (post.getX() + 0.5), dz = villager.getZ() - (post.getZ() + 0.5), dy = villager.getY() - post.getY();
        if (dx * dx + dz * dz <= MAX_AWAY * MAX_AWAY && Math.abs(dy) < 1.5) return;
        villager.stopRiding();
        villager.teleportTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5);
        villager.setDeltaMovement(0, 0, 0);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
    }

    // ---------------------------------------------------------------- товары

    /** До того как житель решит, есть ли ему что предложить, — разложить товар. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getTarget() instanceof Villager villager && villager.level() instanceof ServerLevel level && isPost(villager)) {
            stock(level, villager);
        }
    }

    /** Купленная у оружейника вещь до конца дня не появляется снова (даже если он пополнит запасы). */
    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getAbstractVillager() instanceof Villager villager) || villager.getVillagerData().getProfession() != ModVillagers.ARMS_DEALER.get()) return;
        int index = villager.getOffers().indexOf(event.getMerchantOffer());
        if (index < 0) return;
        CompoundTag data = villager.getPersistentData();
        data.putInt(SOLD, data.getInt(SOLD) | (1 << index));
    }

    /** Раскладывает товар: банкиру — постоянный список, оружейнику — свой на каждый день. */
    public static void stock(ServerLevel level, Villager villager) {
        if (villager.isBaby()) return;
        CompoundTag data = villager.getPersistentData();
        MerchantOffers offers = villager.getOffers();
        var profession = villager.getVillagerData().getProfession();
        if (profession == ModVillagers.BANKER.get()) {
            if (data.getLong(STOCK) == BANK_STOCK && !offers.isEmpty()) return;
            if (villager.getTradingPlayer() != null) return;
            offers.clear();
            offers.addAll(bankOffers());
            data.putLong(STOCK, BANK_STOCK);
        } else if (profession == ModVillagers.ARMS_DEALER.get()) {
            long day = day(level);
            if (data.getLong(STOCK) != day + 1 || offers.size() != ARMS_OFFERS) {
                if (villager.getTradingPlayer() != null) return;
                offers.clear();
                offers.addAll(armsOffers(level, villager.getUUID().getLeastSignificantBits() ^ villager.getUUID().getMostSignificantBits(), day));
                data.putLong(STOCK, day + 1);
                data.putInt(SOLD, 0);
            }
            int sold = data.getInt(SOLD);
            for (int i = 0; i < offers.size(); i++) {
                if ((sold & (1 << i)) != 0 && !offers.get(i).isOutOfStock()) offers.get(i).setToOutOfStock();
            }
        }
    }

    /** Номер игрового дня (по времени суток верхнего мира). */
    public static long day(ServerLevel level) {
        return level.getServer().overworld().getDayTime() / 24000L;
    }

    /** Банкир скупает то, что выносят из данжей. */
    public static List<MerchantOffer> bankOffers() {
        List<MerchantOffer> list = new ArrayList<>();
        // Трофеи боссов — дороже всего, по порядку данжей.
        buy(list, ModItems.CRYPT_CROWN_SHARD, 1, 6);
        buy(list, ModItems.BROODMOTHER_FANG, 1, 8);
        buy(list, ModItems.TYRANT_EMBER_CORE, 1, 10);
        buy(list, ModItems.TIDE_PEARL, 1, 12);
        buy(list, ModItems.FROST_HEART, 1, 14);
        buy(list, ModItems.LICH_PHYLACTERY, 1, 18);
        buy(list, ModItems.RELIC_SPARK, 1, 20);
        buy(list, ModItems.ANCIENT_SOUL_STONE, 1, 12);
        buy(list, ModItems.GREATER_SOUL_STONE, 1, 5);
        buy(list, ModItems.LESSER_SOUL_STONE, 1, 2);
        buy(list, ModItems.ABILITY_BOOK, 1, 4);
        buy(list, ModItems.OBLIVION_SCROLL, 1, 3);
        buy(list, ModItems.SPIRIT_ELIXIR, 2, 1);
        buy(list, () -> Items.TOTEM_OF_UNDYING, 1, 10);
        buy(list, () -> Items.ENCHANTED_GOLDEN_APPLE, 1, 12);
        buy(list, () -> Items.GOLDEN_APPLE, 1, 2);
        buy(list, () -> Items.HEART_OF_THE_SEA, 1, 10);
        buy(list, () -> Items.NETHERITE_INGOT, 1, 24);
        buy(list, () -> Items.NETHERITE_SCRAP, 1, 6);
        buy(list, () -> Items.ECHO_SHARD, 1, 3);
        buy(list, () -> Items.DIAMOND, 1, 2);
        buy(list, ModItems.RUBY, 1, 2);
        buy(list, ModItems.SAPPHIRE, 1, 2);
        buy(list, ModItems.TOPAZ, 1, 2);
        buy(list, () -> Items.GOLD_BLOCK, 1, 3);
        buy(list, () -> Items.GOLD_INGOT, 3, 1);
        buy(list, () -> Items.NAME_TAG, 1, 2);
        buy(list, () -> Items.WITHER_SKELETON_SKULL, 1, 8);
        buy(list, () -> Items.TRIDENT, 1, 8);
        buy(list, () -> Items.NAUTILUS_SHELL, 1, 2);
        buy(list, () -> Items.BOW, 1, 1);
        buy(list, () -> Items.DECORATED_POT, 1, 1);
        // Всякая мелочь из данжей — оптом: глина и кирпич копей, кости, паутина, слизь, лёд.
        buy(list, () -> Items.PHANTOM_MEMBRANE, 2, 1);
        buy(list, () -> Items.BLAZE_ROD, 2, 1);
        buy(list, () -> Items.MAGMA_CREAM, 4, 1);
        buy(list, () -> Items.FERMENTED_SPIDER_EYE, 4, 1);
        buy(list, () -> Items.COBWEB, 4, 1);
        buy(list, () -> Items.BOOK, 4, 1);
        buy(list, () -> Items.IRON_INGOT, 4, 1);
        buy(list, () -> Items.PRISMARINE_CRYSTALS, 6, 1);
        buy(list, () -> Items.PRISMARINE_SHARD, 8, 1);
        buy(list, () -> Items.SPIDER_EYE, 8, 1);
        buy(list, () -> Items.PACKED_ICE, 8, 1);
        buy(list, () -> Items.BRICK, 12, 1);
        buy(list, () -> Items.BONE, 12, 1);
        buy(list, () -> Items.STRING, 12, 1);
        buy(list, () -> Items.CLAY_BALL, 16, 1);
        buy(list, () -> Items.SNOWBALL, 16, 1);
        buy(list, () -> Items.ARROW, 16, 1);
        buy(list, () -> Items.SOUL_SAND, 16, 1);
        buy(list, () -> Items.ROTTEN_FLESH, 24, 1);
        return list;
    }

    private static void buy(List<MerchantOffer> list, Supplier<? extends ItemLike> item, int count, int emeralds) {
        list.add(new MerchantOffer(new ItemCost(item.get(), count), new ItemStack(Items.EMERALD, emeralds), 16, 0, 0.05f));
    }

    /** Мечи и луки на прилавке оружейника. */
    static final List<Supplier<? extends Item>> BLADES = List.of(
            ModItems.IRON_DAGGER, ModItems.STEEL_DAGGER, ModItems.DIAMOND_DAGGER, ModItems.DAMASCUS_DAGGER,
            ModItems.STEEL_SWORD, ModItems.DAMASCUS_SWORD,
            ModItems.IRON_RAPIER, ModItems.STEEL_RAPIER, ModItems.DAMASCUS_RAPIER,
            ModItems.IRON_GREATSWORD, ModItems.STEEL_GREATSWORD, ModItems.DIAMOND_GREATSWORD, ModItems.DAMASCUS_GREATSWORD);
    static final List<Supplier<? extends Item>> BOWS = List.of(() -> Items.BOW, ModItems.COMPOSITE_BOW, ModItems.RANGER_LONGBOW);

    /** Шансы редкости у оружейника: обычных больше всего, легендарные — изредка. */
    private static final ItemRarity.Tier[] TIERS = {ItemRarity.Tier.COMMON, ItemRarity.Tier.FINE, ItemRarity.Tier.RARE,
            ItemRarity.Tier.UNIQUE, ItemRarity.Tier.EPIC, ItemRarity.Tier.LEGENDARY};
    private static final float[] TIER_WEIGHTS = {40f, 28f, 17f, 9f, 4.5f, 1.5f};
    /** Множитель цены по редкости (по порядку {@link ItemRarity.Tier}). */
    private static final float[] TIER_PRICE = {0.6f, 1f, 1.3f, 1.8f, 2.4f, 3.2f, 4.5f, 6f};
    private static final int[] GRADE_PRICE = {26, 16, 26, 40};

    /** Десять вещей на этот день: семь клинков и три лука. Один и тот же день у одного торговца — один и тот же набор. */
    public static List<MerchantOffer> armsOffers(ServerLevel level, long seed, long day) {
        RandomSource random = RandomSource.create(seed * 31 + day * 0x9E3779B97F4A7C15L);
        List<MerchantOffer> list = new ArrayList<>();
        for (int i = 0; i < ARMS_OFFERS; i++) {
            List<Supplier<? extends Item>> pool = i < 7 ? BLADES : BOWS;
            ItemStack stack = new ItemStack(pool.get(random.nextInt(pool.size())).get());
            ItemRarity.Tier tier = rollTier(random);
            ItemRarity.apply(stack, tier, random, level.registryAccess());
            list.add(priced(stack, price(stack, tier)));
        }
        return list;
    }

    static ItemRarity.Tier rollTier(RandomSource random) {
        float total = 0;
        for (float w : TIER_WEIGHTS) total += w;
        float roll = random.nextFloat() * total;
        for (int i = 0; i < TIERS.length; i++) {
            roll -= TIER_WEIGHTS[i];
            if (roll < 0) return TIERS[i];
        }
        return TIERS[0];
    }

    /** Цена в изумрудах: по ступени материала, двуручные дороже, редкость умножает. */
    public static int price(ItemStack stack, ItemRarity.Tier tier) {
        int base = GRADE_PRICE[Math.min(3, Grades.of(stack))];
        if (stack.getItem() instanceof GreatswordItem) base += 8;
        return Math.max(1, Math.round(base * TIER_PRICE[tier.ordinal()]));
    }

    /** Больше 64 изумрудов — остаток изумрудными блоками. */
    public static MerchantOffer priced(ItemStack stack, int price) {
        Optional<ItemCost> blocks = Optional.empty();
        int emeralds = price;
        if (price > 64) {
            int count = (price - 64 + 8) / 9;
            blocks = Optional.of(new ItemCost(Items.EMERALD_BLOCK, count));
            emeralds = price - count * 9;
        }
        return new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), blocks, stack, 1, 0, 0f);
    }

    /** Сколько изумрудов стоит предложение (блок = 9). */
    public static int emeralds(MerchantOffer offer) {
        int total = offer.getBaseCostA().getCount();
        ItemStack b = offer.getCostB();
        if (b.is(Items.EMERALD_BLOCK)) total += b.getCount() * 9;
        return total;
    }
}
