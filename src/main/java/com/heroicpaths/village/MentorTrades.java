package com.heroicpaths.village;

import com.heroicpaths.craft.Profession;
import com.heroicpaths.craft.Professions;
import com.heroicpaths.craft.RuneEnchanting;
import com.heroicpaths.item.AbilityBookItem;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.item.ProfessionManualItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.registry.ModVillagers;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.Subclass;
import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerTrades.ItemListing;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.ArrayList;
import java.util.List;

/** Товары наставников по уровням (1 — новичок … 5 — мастер). */
public final class MentorTrades {
    private MentorTrades() {}

    public static void onVillagerTrades(VillagerTradesEvent event) {
        Int2ObjectMap<List<ItemListing>> trades = event.getTrades();
        VillagerProfession profession = event.getType();

        if (profession == ModVillagers.SWORDMASTER.get()) {
            add(trades, 1, buy(Items.COAL, 15, 2), sell(Items.IRON_SWORD, 3, 1, 1), sell(ModItems.SPIRIT_ELIXIR.get(), 2, 1, 1));
            add(trades, 2, buy(Items.IRON_INGOT, 4, 10), sell(Items.SHIELD, 5, 1, 5), enchanted(Items.IRON_SWORD, 4, 5),
                    sell(ModItems.STEEL_PLATE.get(), 2, 2, 5));
            add(trades, 3, buy(Items.FLINT, 24, 20), enchanted(Items.IRON_AXE, 5, 10), sell(ModItems.EXPERIENCE_TOME.get(), 12, 1, 10));
            add(trades, 4, buy(Items.DIAMOND, 1, 30), enchanted(Items.DIAMOND_SWORD, 12, 15), sell(ModItems.OBLIVION_SCROLL.get(), 10, 1, 15));
            add(trades, 5, enchanted(Items.DIAMOND_AXE, 17, 30), sell(ModItems.EXPERIENCE_TOME.get(), 9, 1, 30));
        } else if (profession == ModVillagers.MASTER_ARCHER.get()) {
            add(trades, 1, sell(ModItems.MUSKET_BALL.get(), 1, 8, 1));
            add(trades, 3, sell(ModItems.MUSKET.get(), 10, 1, 10));
            add(trades, 1, buy(Items.STICK, 32, 2), sell(Items.ARROW, 1, 16, 1), sell(ModItems.SPIRIT_ELIXIR.get(), 2, 1, 1));
            add(trades, 2, buy(Items.STRING, 14, 10), sell(Items.BOW, 2, 1, 5), sell(Items.CROSSBOW, 3, 1, 5),
                    sell(ModItems.HARDENED_LEATHER.get(), 2, 2, 5));
            add(trades, 3, buy(Items.FEATHER, 24, 20), enchanted(Items.BOW, 3, 10), sell(ModItems.EXPERIENCE_TOME.get(), 12, 1, 10));
            add(trades, 4, buy(Items.TRIPWIRE_HOOK, 8, 30), enchanted(Items.CROSSBOW, 4, 15), sell(Items.SPECTRAL_ARROW, 2, 8, 15));
            add(trades, 5, enchanted(Items.BOW, 8, 30), sell(ModItems.OBLIVION_SCROLL.get(), 9, 1, 30));
        } else if (profession == ModVillagers.ARCHMAGE.get()) {
            add(trades, 1, buy(Items.PAPER, 24, 2), sell(ModItems.SPIRIT_ELIXIR.get(), 1, 1, 1), sell(Items.LAPIS_LAZULI, 1, 4, 1));
            add(trades, 2, buy(Items.AMETHYST_SHARD, 8, 10), sell(Items.EXPERIENCE_BOTTLE, 3, 1, 5), sell(Items.GLOWSTONE_DUST, 1, 4, 5),
                    sell(ModItems.ARCANE_CLOTH.get(), 2, 3, 5));
            add(trades, 3, buy(Items.BOOK, 4, 20), sell(ModItems.EXPERIENCE_TOME.get(), 10, 1, 10), sell(Items.ENDER_PEARL, 5, 1, 10));
            add(trades, 4, buy(Items.INK_SAC, 5, 30), sell(ModItems.OBLIVION_SCROLL.get(), 8, 1, 15), sell(ModItems.SPIRIT_ELIXIR.get(), 3, 4, 15));
            add(trades, 5, sell(ModItems.EXPERIENCE_TOME.get(), 8, 1, 30), sell(Items.ENDER_EYE, 6, 1, 30));
        }
        if (profession == ModVillagers.SWORDMASTER.get()) {
            add(trades, 2, sell(ModItems.IRON_DAGGER.get(), 4, 1, 5), sell(ModItems.IRON_GREATSWORD.get(), 7, 1, 5));
            add(trades, 2, sell(ModItems.IRON_RAPIER.get(), 5, 1, 5), sell(ModItems.IRON_FLANGED_MACE.get(), 6, 1, 5));
            add(trades, 3, sell(ModItems.IRON_SPEAR.get(), 7, 1, 10), sell(ModItems.IRON_WARHAMMER.get(), 9, 1, 10));
        }
        if (profession == ModVillagers.ARCHMAGE.get()) {
            add(trades, 1, sell(ModItems.APPRENTICE_WAND.get(), 3, 1, 1));
            add(trades, 2, sell(ModItems.APPRENTICE_STAFF.get(), 6, 1, 5));
        }

        // Распорядитель гильдии искателей приключений: скупает добычу с охоты и продаёт припасы и книги любых классов.
        if (profession == ModVillagers.GUILDMASTER.get()) {
            add(trades, 1, buy(Items.ROTTEN_FLESH, 24, 2), buy(Items.BONE, 16, 2), sell(ModItems.SPIRIT_ELIXIR.get(), 2, 1, 1));
            add(trades, 2, buy(Items.STRING, 16, 10), buy(Items.GUNPOWDER, 8, 10), sell(Items.MAP, 3, 1, 5));
            add(trades, 3, buy(Items.ENDER_PEARL, 3, 20), sell(ModItems.EXPERIENCE_TOME.get(), 11, 1, 10),
                    book(PlayerClass.playable(), 20, 10));
            add(trades, 4, buy(Items.BLAZE_ROD, 4, 30), sell(ModItems.OBLIVION_SCROLL.get(), 9, 1, 15), book(PlayerClass.playable(), 40, 15));
            add(trades, 5, book(PlayerClass.playable(), 100, 30), sell(ModItems.ORDER_BOARD.get(), 4, 1, 30));
        }

        // Наставники продают книги умений своих классов: чем опытнее наставник, тем сильнее умения.
        List<PlayerClass> classes = ClassMentors.classesOf(profession);
        if (!classes.isEmpty()) {
            add(trades, 2, book(classes, 10, 5));
            add(trades, 3, book(classes, 15, 10));
            add(trades, 4, book(classes, 20, 15));
            add(trades, 5, book(classes, 100, 30), book(classes, 100, 30));
        }
        if (profession == ModVillagers.MASTER_ARCHER.get()) add(trades, 2, sell(ModItems.HAND_CROSSBOW.get(), 5, 1, 5));

        // Жители-мастера продают трактаты своего ремесла: ступень трактата равна уровню жителя.
        Profession craft = Profession.taughtBy(profession);
        if (craft != null) {
            for (int rank = Professions.MIN_RANK + 1; rank <= Professions.MAX_RANK; rank++) add(trades, rank, manual(craft, rank));
        }
        // Оружие и броню зачаровывает только рунный мастер: жители продают их без чар, зато с редкостью.
        for (List<ItemListing> listings : trades.values()) listings.replaceAll(MentorTrades::withoutGearEnchantments);
    }

    private static final int[] MANUAL_PRICE = {0, 0, 6, 14, 26, 40};

    /** Трактат ремесла за изумруды и книгу. */
    private static ItemListing manual(Profession craft, int rank) {
        return (trader, random) -> new MerchantOffer(new ItemCost(Items.EMERALD, MANUAL_PRICE[rank]),
                java.util.Optional.of(new ItemCost(Items.BOOK, 1)), ProfessionManualItem.create(craft.manual(), rank), 3, 5 * rank, 0.05f);
    }

    private static ItemListing withoutGearEnchantments(ItemListing listing) {
        return listing instanceof UnenchantedGear ? listing : new UnenchantedGear(listing);
    }

    /** Обёртка над товаром жителя: зачарованное оружие или броня теряют чары и получают качественную или редкую редкость. */
    private record UnenchantedGear(ItemListing inner) implements ItemListing {
        @Override
        public MerchantOffer getOffer(net.minecraft.world.entity.Entity trader, net.minecraft.util.RandomSource random) {
            MerchantOffer offer = inner.getOffer(trader, random);
            if (offer == null) return null;
            ItemStack result = offer.getResult();
            if (RuneEnchanting.isRuneOnly(result) && result.isEnchanted()) {
                result.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
                ItemRarity.apply(result, random.nextBoolean() ? ItemRarity.Tier.FINE : ItemRarity.Tier.RARE, random, null);
            }
            return offer;
        }
    }

    /** Книга случайного умения этих классов, которое можно выучить до maxLevel. Цена зависит от уровня умения. */
    private static ItemListing book(List<PlayerClass> classes, int maxLevel, int villagerXp) {
        return (trader, random) -> {
            List<AbilityDef> pool = new ArrayList<>();
            for (AbilityDef def : AbilityRegistry.bookAbilities(maxLevel)) {
                for (Subclass subclass : AbilityRegistry.learners(def)) {
                    if (classes.contains(subclass.playerClass()) && !pool.contains(def)) pool.add(def);
                }
            }
            if (pool.isEmpty()) return null;
            AbilityDef def = pool.get(random.nextInt(pool.size()));
            int price = Math.min(64, 6 + def.unlockLevel() / 2);
            return new MerchantOffer(new ItemCost(Items.EMERALD, price),
                    AbilityBookItem.create(ModItems.ABILITY_BOOK.get(), def), 3, villagerXp, 0.05f);
        };
    }

    private static void add(Int2ObjectMap<List<ItemListing>> trades, int level, ItemListing... listings) {
        trades.computeIfAbsent(level, l -> new ArrayList<>()).addAll(List.of(listings));
    }

    /** Наставник покупает предметы за 1 изумруд. */
    private static ItemListing buy(Item item, int count, int villagerXp) {
        return new VillagerTrades.EmeraldForItems(item, count, 16, villagerXp);
    }

    /** Наставник продаёт предметы за изумруды. */
    private static ItemListing sell(Item item, int emeralds, int count, int villagerXp) {
        return new VillagerTrades.ItemsForEmeralds(item, emeralds, count, 12, villagerXp);
    }

    /** Наставник продаёт зачарованное снаряжение; цена растёт вместе с силой чар. */
    private static ItemListing enchanted(Item item, int baseEmeralds, int villagerXp) {
        return new VillagerTrades.EnchantedItemForEmeralds(item, baseEmeralds, 3, villagerXp);
    }
}
