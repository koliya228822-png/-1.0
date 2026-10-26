package com.heroicpaths.village;

import com.heroicpaths.item.AbilityBookItem;
import com.heroicpaths.item.Grades;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.item.MeleeWeaponItem;
import com.heroicpaths.item.ProfessionManualItem;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.ability.AbilityDef;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Скупщик в своей лавке покупает у игрока его же вещи: когда к нему подходят, он оценивает инвентарь и предлагает
 * за каждую вещь изумруды — в пять раз меньше её рыночной цены, но не меньше одного изумруда.
 * Рыночная цена: у снаряжения — по грейду и редкости (как у мастера-оружейника, только для всех грейдов),
 * у трактатов — цена книжника, у книг умений — цена наставника, у добычи — вдвое больше того, что платит банкир,
 * у руды, слитков и прочих ценностей — по таблице ниже, у остального — по ванильной редкости предмета.
 * Обычный хлам (земля, камень) скупщик не берёт.
 */
public final class Pawnbroker {
    /** Во сколько раз скупщик платит меньше рыночной цены. */
    public static final int DISCOUNT = 5;
    /** Больше предложений скупщик за раз не делает. */
    public static final int MAX_OFFERS = 40;
    /** Рыночная цена снаряжения по грейду (0 — вещь без грейда). */
    private static final int[] GRADE_VALUE = {8, 16, 26, 40, 60, 85, 120};
    /** Множитель цены по редкости (по порядку {@link ItemRarity.Tier}). */
    private static final float[] TIER_VALUE = {0.6f, 1f, 1.3f, 1.8f, 2.4f, 3.2f, 4.5f, 6f};

    private static Map<Item, Double> table;

    private Pawnbroker() {}

    /** Сколько изумрудов стоит одна такая вещь на рынке; 0 — скупщику она не нужна. */
    public static double marketValue(ItemStack stack) {
        if (stack.isEmpty() || stack.is(Items.EMERALD) || stack.is(Items.EMERALD_BLOCK)) return 0;
        Item item = stack.getItem();
        if (item instanceof ProfessionManualItem) return Bookseller.price(ProfessionManualItem.rank(stack));
        if (item instanceof AbilityBookItem) {
            AbilityDef def = AbilityBookItem.ability(stack);
            return def == null ? 4 : Math.min(64, 6 + def.unlockLevel() / 2);
        }
        int grade = Grades.of(stack);
        ItemRarity.Tier tier = ItemRarity.get(stack);
        if (grade > 0 || tier != null) {
            double value = GRADE_VALUE[Math.min(Grades.MAX, grade)];
            if (item instanceof MeleeWeaponItem weapon && weapon.isTwoHanded()) value += 8;
            value *= TIER_VALUE[(tier == null ? ItemRarity.Tier.COMMON : tier).ordinal()];
            return value * durability(stack);
        }
        Double listed = table().get(item);
        if (listed != null) return listed;
        if (item instanceof TieredItem tiered) return toolValue(tiered) * durability(stack);
        if (item instanceof ArmorItem armor) return armorValue(armor) * durability(stack);
        Rarity rarity = stack.getRarity();
        return switch (rarity) {
            case EPIC -> 24;
            case RARE -> 10;
            case UNCOMMON -> 3;
            default -> 0;
        };
    }

    /** Сколько скупщик заплатит за count таких вещей. */
    public static int payment(ItemStack stack, int count) {
        double value = marketValue(stack);
        if (value <= 0) return 0;
        return Math.max(1, (int) Math.floor(value * count / DISCOUNT));
    }

    /** Сколько штук скупщик берёт за раз: столько, чтобы набрался хотя бы один изумруд (не больше стопки). */
    public static int lot(ItemStack stack) {
        double value = marketValue(stack);
        if (value <= 0) return 0;
        return Math.max(1, Math.min(stack.getMaxStackSize(), (int) Math.ceil(DISCOUNT / value - 1e-9)));
    }

    /** Предложения скупщика для этого игрока: по одному на каждую разную вещь в его инвентаре. */
    public static List<MerchantOffer> offersFor(Player player) {
        List<ItemStack> kinds = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.isEmpty() || marketValue(stack) <= 0) continue;
            int index = -1;
            for (int i = 0; i < kinds.size(); i++) if (ItemStack.isSameItemSameComponents(kinds.get(i), stack)) index = i;
            if (index < 0) {
                kinds.add(stack.copyWithCount(1));
                counts.add(stack.getCount());
            } else {
                counts.set(index, counts.get(index) + stack.getCount());
            }
        }
        List<MerchantOffer> offers = new ArrayList<>();
        for (int i = 0; i < kinds.size() && offers.size() < MAX_OFFERS; i++) {
            ItemStack kind = kinds.get(i);
            int lot = lot(kind);
            if (counts.get(i) < lot) continue;
            offers.add(offer(kind, lot, payment(kind, lot), counts.get(i) / lot));
        }
        // Самое дорогое — сверху.
        offers.sort((a, b) -> Integer.compare(paid(b), paid(a)));
        return offers;
    }

    /** Сколько изумрудов в оплате скупщика (блок = 9). */
    public static int paid(MerchantOffer offer) {
        ItemStack result = offer.getResult();
        return result.is(Items.EMERALD_BLOCK) ? result.getCount() * 9 : result.getCount();
    }

    private static MerchantOffer offer(ItemStack kind, int count, int emeralds, int uses) {
        ItemCost cost = new ItemCost(kind.getItemHolder(), count, exact(kind));
        // Больше стопки изумрудов скупщик платит изумрудными блоками.
        ItemStack pay = emeralds > 64 ? new ItemStack(Items.EMERALD_BLOCK, Math.min(64, emeralds / 9)) : new ItemStack(Items.EMERALD, emeralds);
        return new MerchantOffer(cost, java.util.Optional.empty(), pay, 0, Math.max(1, uses), 0, 0f);
    }

    /** Предложение принимает только ровно такую вещь: с той же редкостью, бонусами, чарами и износом. */
    static DataComponentPredicate exact(ItemStack stack) {
        DataComponentPredicate.Builder builder = DataComponentPredicate.builder();
        for (var entry : stack.getComponentsPatch().entrySet()) {
            entry.getValue().ifPresent(value -> expect(builder, entry.getKey(), value));
        }
        return builder.build();
    }

    @SuppressWarnings("unchecked")
    private static <T> void expect(DataComponentPredicate.Builder builder, DataComponentType<T> type, Object value) {
        builder.expect(type, (T) value);
    }

    private static double durability(ItemStack stack) {
        if (!stack.isDamageableItem() || stack.getMaxDamage() <= 0) return 1;
        return Math.max(0.3, 1 - stack.getDamageValue() / (double) stack.getMaxDamage());
    }

    private static double toolValue(TieredItem item) {
        var tier = item.getTier();
        if (tier == Tiers.WOOD) return 0.5;
        if (tier == Tiers.STONE) return 1;
        if (tier == Tiers.GOLD) return 4;
        if (tier == Tiers.IRON) return 6;
        if (tier == Tiers.DIAMOND) return 20;
        if (tier == Tiers.NETHERITE) return 60;
        return 6;
    }

    private static double armorValue(ArmorItem armor) {
        var material = armor.getMaterial().value();
        if (material == ArmorMaterials.LEATHER.value()) return 3;
        if (material == ArmorMaterials.CHAIN.value() || material == ArmorMaterials.GOLD.value()) return 6;
        if (material == ArmorMaterials.IRON.value()) return 8;
        if (material == ArmorMaterials.DIAMOND.value()) return 24;
        if (material == ArmorMaterials.NETHERITE.value()) return 70;
        return 6;
    }

    /** Рыночные цены ценностей: добыча из данжей — по цене банкира, остальное — здесь. */
    private static Map<Item, Double> table() {
        if (table != null) return table;
        Map<Item, Double> map = new HashMap<>();
        Object[] values = {
                Items.DIAMOND, 8.0, Items.DIAMOND_BLOCK, 72.0, Items.NETHERITE_INGOT, 60.0, Items.NETHERITE_SCRAP, 15.0,
                Items.ANCIENT_DEBRIS, 15.0, Items.NETHERITE_BLOCK, 540.0,
                Items.GOLD_INGOT, 2.0, Items.GOLD_BLOCK, 18.0, Items.RAW_GOLD, 1.5, Items.GOLD_NUGGET, 0.2,
                Items.IRON_INGOT, 1.0, Items.IRON_BLOCK, 9.0, Items.RAW_IRON, 0.8, Items.IRON_NUGGET, 0.1,
                Items.COPPER_INGOT, 0.3, Items.LAPIS_LAZULI, 0.25, Items.REDSTONE, 0.2, Items.QUARTZ, 0.25,
                Items.AMETHYST_SHARD, 0.5, Items.COAL, 0.15, Items.ENDER_PEARL, 3.0, Items.ENDER_EYE, 5.0,
                Items.BLAZE_ROD, 2.0, Items.GHAST_TEAR, 4.0, Items.SHULKER_SHELL, 8.0, Items.ELYTRA, 120.0,
                Items.SADDLE, 6.0, Items.EXPERIENCE_BOTTLE, 3.0, Items.ENCHANTED_GOLDEN_APPLE, 60.0, Items.GOLDEN_APPLE, 10.0,
                Items.TOTEM_OF_UNDYING, 50.0, Items.HEART_OF_THE_SEA, 50.0, Items.NETHER_STAR, 120.0, Items.DRAGON_EGG, 200.0,
                Items.BOOK, 1.0, Items.LEATHER, 0.5, Items.WHITE_WOOL, 0.3, Items.STRING, 0.1, Items.FEATHER, 0.2,
                Items.WHEAT, 0.1, Items.BREAD, 0.3, Items.COOKED_BEEF, 0.4, Items.COOKED_PORKCHOP, 0.4,
                ModItems.STEEL_PLATE.get(), 3.0, ModItems.DAMASCUS_INGOT.get(), 8.0, ModItems.HARDENED_LEATHER.get(), 3.0,
                ModItems.IMPREGNATED_LEATHER.get(), 8.0, ModItems.ARCANE_CLOTH.get(), 3.0, ModItems.ASTRAL_SILK.get(), 8.0,
                ModItems.FACETED_DIAMOND.get(), 16.0, ModItems.BRILLIANT.get(), 32.0,
                ModItems.MUSKET_BALL.get(), 0.15, ModItems.SPIRIT_ELIXIR.get(), 2.0, ModItems.OBLIVION_SCROLL.get(), 9.0,
        };
        for (int i = 0; i < values.length; i += 2) map.put((Item) values[i], (Double) values[i + 1]);
        // Банкир платит за добычу вполовину рынка: рыночная цена — вдвое больше его цены.
        for (MerchantOffer offer : MerchantPosts.bankOffers()) {
            Item item = offer.getItemCostA().itemStack().getItem();
            double bank = 2.0 * offer.getResult().getCount() / offer.getItemCostA().count();
            map.merge(item, bank, Math::max);
        }
        table = map;
        return map;
    }
}
