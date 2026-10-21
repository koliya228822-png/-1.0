package com.heroicpaths.craft;

import com.heroicpaths.gear.GearRules;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Рунные зачарования: оружие и броню зачаровывает только рунный мастер у рунного стола (стол зачарований
 * и книги на наковальне для них не работают). Каждая чара стоит руну своей стихии за уровень и опыт.
 * Ступень рунного мастера ограничивает уровень чар, а редкость вещи — число чар на ней.
 */
public final class RuneEnchanting {
    /** Сколько чар выдерживает вещь каждой редкости (мусор — ни одной). */
    private static final int[] SLOTS_BY_RARITY = {0, 1, 1, 2, 2, 3, 3, 4};
    /** Опыт рунного мастера за каждый уровень наложенных чар. */
    public static final int XP_PER_LEVEL = 12;

    /** Руна для чар: огонь — пламя и кара, лёд — острота и вода, гроза — дальний бой и удача, земля — защита и прочность. */
    private static final Map<String, Supplier<Item>> RUNES = Map.ofEntries(
            Map.entry("fire_aspect", ModItems.FIRE_RUNE), Map.entry("flame", ModItems.FIRE_RUNE),
            Map.entry("fire_protection", ModItems.FIRE_RUNE), Map.entry("smite", ModItems.FIRE_RUNE),
            Map.entry("power", ModItems.FIRE_RUNE),
            Map.entry("sharpness", ModItems.FROST_RUNE), Map.entry("respiration", ModItems.FROST_RUNE),
            Map.entry("aqua_affinity", ModItems.FROST_RUNE), Map.entry("depth_strider", ModItems.FROST_RUNE),
            Map.entry("impaling", ModItems.FROST_RUNE), Map.entry("riptide", ModItems.FROST_RUNE),
            Map.entry("loyalty", ModItems.FROST_RUNE), Map.entry("piercing", ModItems.FROST_RUNE),
            Map.entry("projectile_protection", ModItems.STORM_RUNE), Map.entry("sweeping_edge", ModItems.STORM_RUNE),
            Map.entry("looting", ModItems.STORM_RUNE), Map.entry("quick_charge", ModItems.STORM_RUNE),
            Map.entry("multishot", ModItems.STORM_RUNE), Map.entry("channeling", ModItems.STORM_RUNE),
            Map.entry("wind_burst", ModItems.STORM_RUNE), Map.entry("punch", ModItems.STORM_RUNE));

    private RuneEnchanting() {}

    /** Вариант чар для вещи: следующий уровень, цена и причина, по которой он закрыт (null — открыт). */
    public record Option(Holder<Enchantment> enchantment, int level, Item rune, int runes, int xpLevels, @Nullable Component lock) {
        public ResourceLocation id() {
            return enchantment.unwrapKey().map(key -> key.location()).orElseThrow();
        }
    }

    /** Оружие и броня зачаровываются только рунами. Инструменты — как обычно. */
    public static boolean isRuneOnly(ItemStack stack) {
        return GearRules.isGear(stack);
    }

    public static boolean canEnchant(ItemStack stack) {
        return !stack.isEmpty() && stack.getMaxStackSize() == 1 && !stack.is(Items.BOOK) && !stack.is(Items.ENCHANTED_BOOK);
    }

    /** Сколько чар выдерживает вещь. Вещь без редкости — как обычная. */
    public static int slots(ItemStack stack) {
        ItemRarity.Tier tier = ItemRarity.get(stack);
        return SLOTS_BY_RARITY[tier == null ? ItemRarity.Tier.COMMON.ordinal() : tier.ordinal()];
    }

    public static Item runeFor(Holder<Enchantment> enchantment) {
        String path = enchantment.unwrapKey().map(key -> key.location().getPath()).orElse("");
        Supplier<Item> rune = RUNES.get(path);
        return rune == null ? ModItems.EARTH_RUNE.get() : rune.get();
    }

    /** Все чары, которые можно наложить (или усилить) на вещь, по порядку; закрытые — с причиной. */
    public static List<Option> options(Player player, ItemStack stack) {
        List<Option> options = new ArrayList<>();
        if (!canEnchant(stack)) return options;
        Registry<Enchantment> registry = player.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        int rank = RpgManager.get(player).professions().rank(Profession.RUNECRAFTER);
        ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int slots = slots(stack);
        List<Holder<Enchantment>> pool = new ArrayList<>();
        registry.getTagOrEmpty(EnchantmentTags.IN_ENCHANTING_TABLE).forEach(pool::add);
        pool.sort(Comparator.comparing(holder -> holder.unwrapKey().map(key -> key.location().toString()).orElse("")));
        for (Holder<Enchantment> holder : pool) {
            Enchantment enchantment = holder.value();
            if (!enchantment.isSupportedItem(stack)) continue;
            int have = current.getLevel(holder);
            if (have >= enchantment.getMaxLevel()) continue;
            if (have == 0 && !current.keySet().stream().allMatch(other -> Enchantment.areCompatible(other, holder))) continue;
            int level = have + 1;
            Component lock = null;
            if (have == 0 && current.size() >= slots) {
                lock = slots == 0 ? Component.translatable("screen.heroicpaths.rune.lock_junk")
                        : Component.translatable("screen.heroicpaths.rune.lock_slots", slots);
            } else if (level > rank) {
                lock = Component.translatable("screen.heroicpaths.rune.lock_rank", Professions.rankName(level));
            }
            options.add(new Option(holder, level, runeFor(holder), level, level + 1, lock));
        }
        return options;
    }

    public static int countRunes(Player player, Item rune) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) if (stack.is(rune)) total += stack.getCount();
        for (ItemStack stack : player.getInventory().offhand) if (stack.is(rune)) total += stack.getCount();
        return total;
    }

    public static boolean affordable(Player player, Option option) {
        return countRunes(player, option.rune()) >= option.runes()
                && (player.getAbilities().instabuild || player.experienceLevel >= option.xpLevels());
    }

    /** Накладывает чары на вещь в основной руке у рунного стола. Возвращает true, если получилось. */
    public static boolean enchant(ServerPlayer player, ResourceLocation enchantmentId, BlockPos pos) {
        if (!(player.level().getBlockState(pos).getBlock() instanceof StationBlock block) || block.station() != Station.RUNE_TABLE) return false;
        if (player.distanceToSqr(pos.getCenter()) > StationCrafting.MAX_DISTANCE_SQR) return false;
        ItemStack stack = player.getMainHandItem();
        Option option = null;
        for (Option candidate : options(player, stack)) if (candidate.id().equals(enchantmentId)) option = candidate;
        if (option == null) return false;
        if (option.lock() != null) {
            player.displayClientMessage(option.lock().copy().withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!affordable(player, option)) return false;

        int left = option.runes();
        for (List<ItemStack> list : List.of(player.getInventory().items, player.getInventory().offhand)) {
            for (ItemStack slot : list) {
                if (left <= 0) break;
                if (!slot.is(option.rune())) continue;
                int take = Math.min(left, slot.getCount());
                slot.shrink(take);
                left -= take;
            }
        }
        if (!player.getAbilities().instabuild) player.giveExperienceLevels(-option.xpLevels());
        stack.enchant(option.enchantment(), option.level());
        player.serverLevel().sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 30, 0.4, 0.4, 0.4, 0.6);
        player.serverLevel().playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        player.displayClientMessage(Component.translatable("message.heroicpaths.rune_enchanted",
                Enchantment.getFullname(option.enchantment(), option.level())).withStyle(ChatFormatting.AQUA), true);
        ProfessionProgress.award(player, Profession.RUNECRAFTER, XP_PER_LEVEL * option.level());
        return true;
    }
}
