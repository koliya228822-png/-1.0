package com.heroicpaths.item;

import com.heroicpaths.minion.Minion;
import com.heroicpaths.registry.ModItems;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

import java.util.List;
import java.util.Set;

/**
 * Ванильное оружие и броня убраны из игры: мечи, вся броня (кожаная, кольчужная, железная, золотая, алмазная,
 * незеритовая, черепаший шлем), щит, арбалет, булава и трезубец. Их не скрафтить (рецепты отключены в data/minecraft/recipe),
 * нет во вкладках творческого режима, в сундуках и добыче, у торговцев, а монстры появляются без них.
 * Ванильный лук остаётся — это лук I грейда, а ванильные инструменты — инструменты, не оружие.
 */
public final class VanillaGear {
    public static final Set<Item> REMOVED = Set.of(
            Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD,
            Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
            Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
            Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS,
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
            Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
            Items.TURTLE_HELMET, Items.SHIELD, Items.CROSSBOW, Items.MACE, Items.TRIDENT);

    private VanillaGear() {}

    public static boolean isRemoved(ItemStack stack) {
        return !stack.isEmpty() && REMOVED.contains(stack.getItem());
    }

    /** Убирает их из всех вкладок творческого режима и из поиска (шина мода). */
    public static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        for (ItemStack stack : List.copyOf(event.getParentEntries())) {
            if (isRemoved(stack)) event.remove(stack, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
        }
        for (ItemStack stack : List.copyOf(event.getSearchEntries())) {
            if (isRemoved(stack)) event.remove(stack, CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY);
        }
    }

    /**
     * Монстры (зомби с мечами и в броне, утопленники с трезубцами, пиглины с золотыми мечами…) появляются без них.
     * Слуги игрока — скелеты призывателя — вместо меча получают железный кинжал мода.
     */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) return;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!isRemoved(mob.getItemBySlot(slot))) continue;
            boolean weapon = slot == EquipmentSlot.MAINHAND;
            mob.setItemSlot(slot, mob instanceof Minion && weapon ? new ItemStack(ModItems.IRON_DAGGER.get()) : ItemStack.EMPTY);
        }
    }

    /** Торговцы больше не продают ванильное оружие и броню: такие сделки пропадают. */
    @SubscribeEvent
    public static void onVillagerTrades(VillagerTradesEvent event) {
        event.getTrades().values().forEach(VanillaGear::filter);
    }

    @SubscribeEvent
    public static void onWandererTrades(WandererTradesEvent event) {
        filter(event.getGenericTrades());
        filter(event.getRareTrades());
    }

    private static void filter(List<VillagerTrades.ItemListing> listings) {
        listings.replaceAll(listing -> (trader, random) -> {
            MerchantOffer offer = listing.getOffer(trader, random);
            return offer == null || isRemoved(offer.getResult()) ? null : offer;
        });
    }

    /** Убирает их из любой добычи: сундуки, рыбалка, добыча с мобов, подарки жителей. */
    public static class LootFilter extends LootModifier {
        public static final MapCodec<LootFilter> CODEC = RecordCodecBuilder.mapCodec(inst -> codecStart(inst).apply(inst, LootFilter::new));

        public LootFilter(LootItemCondition[] conditions) {
            super(conditions);
        }

        @Override
        protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
            loot.removeIf(VanillaGear::isRemoved);
            return loot;
        }

        @Override
        public MapCodec<? extends IGlobalLootModifier> codec() {
            return CODEC;
        }
    }
}
