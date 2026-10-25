package com.heroicpaths.item;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Редкость снаряжения: Мусор → Обычная → Качественная → Редкая → Уникальная → Эпическая → Легендарная → Реликвия.
 * Скрафченное оружие, броня, инструменты, луки, щиты, жезлы и посохи получают случайную редкость.
 * Редкость меняет урон, броню и прочность (у мусора они хуже обычного); с Редкой появляются уникальные бонусы,
 * с Легендарной — два зачарования. Реликвию нельзя скрафтить.
 */
public final class ItemRarity {
    public enum Tier {
        JUNK(ChatFormatting.DARK_GRAY, -20, 10f, 0, 0f),
        COMMON(ChatFormatting.WHITE, 0, 45f, 0, 0f),
        FINE(ChatFormatting.GREEN, 10, 25f, 0, 0f),
        RARE(ChatFormatting.BLUE, 20, 12f, 1, 1.0f),
        UNIQUE(ChatFormatting.AQUA, 30, 5f, 1, 1.35f),
        EPIC(ChatFormatting.LIGHT_PURPLE, 42, 2.5f, 2, 1.6f),
        LEGENDARY(ChatFormatting.GOLD, 55, 0.5f, 2, 2.0f),
        RELIC(ChatFormatting.RED, 75, 0f, 3, 2.6f);

        private final ChatFormatting color;
        private final int bonusPercent;
        private final float chancePercent;
        private final int traits;
        private final float traitPower;

        Tier(ChatFormatting color, int bonusPercent, float chancePercent, int traits, float traitPower) {
            this.color = color;
            this.bonusPercent = bonusPercent;
            this.chancePercent = chancePercent;
            this.traits = traits;
            this.traitPower = traitPower;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Прибавка к урону, броне и прочности, %. У мусора отрицательная. */
        public int bonusPercent() {
            return bonusPercent;
        }

        public float chancePercent() {
            return chancePercent;
        }

        /** Сколько уникальных бонусов получает предмет. */
        public int traits() {
            return traits;
        }

        public float traitPower() {
            return traitPower;
        }

        /** Сколько зачарований получает предмет: два у Легендарной и Реликвии. */
        public int enchantments() {
            return ordinal() >= LEGENDARY.ordinal() ? 2 : 0;
        }

        public ChatFormatting color() {
            return color;
        }

        public int rgb() {
            Integer value = color.getColor();
            return value == null ? 0xFFFFFF : value;
        }

        public MutableComponent displayName() {
            return Component.translatable("rarity.heroicpaths." + id()).withStyle(color);
        }

        public static Tier byOrdinal(int ordinal) {
            return values()[Math.max(0, Math.min(values().length - 1, ordinal))];
        }
    }

    private static final ResourceLocation MODIFIER_ID = HeroicPaths.id("rarity");
    private static final ResourceLocation LEGACY_MODIFIER_ID = HeroicPaths.id("quality");
    private static final ResourceLocation MINING_TRAIT_ID = HeroicPaths.id("trait_mining");
    private static final List<Holder<Attribute>> SCALED = List.of(Attributes.ATTACK_DAMAGE, Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS);
    /** Старое качество (обычное, хорошее, отличное, легендарное) → новая редкость. */
    private static final Tier[] LEGACY = {Tier.COMMON, Tier.FINE, Tier.RARE, Tier.LEGENDARY};

    private ItemRarity() {}

    /** Редкость есть у нескладываемых предметов с прочностью (оружие, инструменты, броня, луки, щиты) и у жезлов и посохов. */
    public static boolean canHaveRarity(ItemStack stack) {
        return !stack.isEmpty() && stack.getMaxStackSize() == 1
                && (stack.has(DataComponents.MAX_DAMAGE) || stack.getItem() instanceof FocusItem
                || stack.getItem() instanceof com.heroicpaths.accessory.AccessoryItem);
    }

    @Nullable
    public static Tier get(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Integer value = stack.get(ModComponents.RARITY);
        if (value != null) return Tier.byOrdinal(value);
        Integer legacy = stack.get(ModComponents.QUALITY);
        return legacy == null ? null : LEGACY[Math.max(0, Math.min(3, legacy))];
    }

    public static Tier roll(RandomSource random) {
        return roll(random, com.heroicpaths.craft.Professions.MIN_RANK);
    }

    /** Редкость вещи, которую создаёт мастер этой ступени ремесла. */
    public static Tier roll(RandomSource random, int craftRank) {
        float[] chances = chances(craftRank);
        float roll = random.nextFloat() * 100f;
        for (Tier tier : Tier.values()) {
            if (roll < chances[tier.ordinal()]) return tier;
            roll -= chances[tier.ordinal()];
        }
        return Tier.COMMON;
    }

    /**
     * Шансы редкостей (%) для ступени ремесла: новичок — базовая таблица; к мастеру мусор исчезает,
     * а шансы редких вещей растут (легендарная — с 0.5% до 2%). Остаток приходится на обычную.
     */
    public static float[] chances(int craftRank) {
        float luck = Math.max(0, Math.min(4, craftRank - 1));
        float[] chances = new float[Tier.values().length];
        chances[Tier.JUNK.ordinal()] = Tier.JUNK.chancePercent * (1f - luck / 4f);
        chances[Tier.FINE.ordinal()] = Tier.FINE.chancePercent + 1.25f * luck;
        chances[Tier.RARE.ordinal()] = Tier.RARE.chancePercent * (1f + 0.125f * luck);
        chances[Tier.UNIQUE.ordinal()] = Tier.UNIQUE.chancePercent * (1f + 0.25f * luck);
        chances[Tier.EPIC.ordinal()] = Tier.EPIC.chancePercent * (1f + 0.25f * luck);
        chances[Tier.LEGENDARY.ordinal()] = Tier.LEGENDARY.chancePercent * (1f + 0.75f * luck);
        float rest = 100f;
        for (float chance : chances) rest -= chance;
        chances[Tier.COMMON.ordinal()] = rest;
        return chances;
    }

    /** Прибавка к урону от редкости оружия, % (для стрел — от редкости лука). У мусора отрицательная. */
    public static float bonusPercent(@Nullable ItemStack stack) {
        Tier tier = get(stack);
        return tier == null ? 0f : tier.bonusPercent;
    }

    public static List<ItemTrait.Roll> traits(ItemStack stack) {
        List<ItemTrait.Roll> traits = stack.get(ModComponents.TRAITS);
        return traits == null ? List.of() : traits;
    }

    /** Сила бонуса этого вида у предмета (0 — бонуса нет). */
    public static float traitValue(ItemStack stack, ItemTrait trait) {
        float total = 0;
        for (ItemTrait.Roll roll : traits(stack)) if (roll.type() == trait) total += roll.value();
        return total;
    }

    /**
     * Ставит редкость: урон, броня и прочность считаются заново от исходных значений предмета,
     * бросаются уникальные бонусы и (с Легендарной) зачарования, название окрашивается в цвет редкости.
     */
    public static void apply(ItemStack stack, Tier tier, RandomSource random, @Nullable RegistryAccess registries) {
        List<ItemTrait.Roll> traits = rollTraits(stack, tier, random);
        boolean enchanted = false;
        if (registries != null && tier.enchantments() > 0) enchanted = addEnchantments(stack, tier, random, registries) > 0;
        // Жезлы и посохи не зачаровываются: вместо чар — ещё один бонус.
        if (tier.enchantments() > 0 && !enchanted) {
            List<ItemTrait.Roll> more = new ArrayList<>(traits);
            more.addAll(rollTraits(stack, tier, random, 1, traits));
            traits = more;
        }
        applyStats(stack, tier, traits);
    }

    /**
     * Реликвия из легендарной вещи (алтарь реликвий): уникальные бонусы усиливаются до силы реликвии и к ним
     * добавляется ещё один, каждое зачарование поднимается на уровень (до максимума + 1), урон, броня
     * и прочность пересчитываются. Возвращает false, если вещь не легендарная.
     */
    public static boolean upgradeToRelic(ItemStack stack, RandomSource random) {
        if (get(stack) != Tier.LEGENDARY) return false;
        float scale = Tier.RELIC.traitPower / Tier.LEGENDARY.traitPower;
        List<ItemTrait.Roll> traits = new ArrayList<>();
        for (ItemTrait.Roll roll : traits(stack)) traits.add(new ItemTrait.Roll(roll.trait(), Math.round(roll.value() * scale * 10f) / 10f));
        traits.addAll(rollTraits(stack, Tier.RELIC, random, 1, traits));
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            stack.enchant(enchantment, Math.min(entry.getIntValue() + 1, enchantment.value().getMaxLevel() + 1));
        }
        applyStats(stack, Tier.RELIC, traits);
        return true;
    }

    /**
     * Вещь сменила предмет (заготовка на наковальне подняла её грейд): урон, броня, прочность и название
     * считаются заново от нового прототипа, редкость, бонусы и чары остаются.
     */
    public static void refresh(ItemStack stack) {
        resetToPrototype(stack, DataComponents.ATTRIBUTE_MODIFIERS);
        resetToPrototype(stack, DataComponents.MAX_DAMAGE);
        resetToPrototype(stack, DataComponents.ITEM_NAME);
        Tier tier = get(stack);
        if (tier != null) applyStats(stack, tier, traits(stack));
    }

    private static <T> void resetToPrototype(ItemStack stack, net.minecraft.core.component.DataComponentType<T> type) {
        T value = stack.getPrototype().get(type);
        if (value == null) stack.remove(type);
        else stack.set(type, value);
    }

    /** Ставит редкость с готовыми бонусами: прочность, урон, броня и цвет названия считаются от прототипа. */
    private static void applyStats(ItemStack stack, Tier tier, List<ItemTrait.Roll> traits) {
        stack.remove(ModComponents.QUALITY);
        stack.set(ModComponents.RARITY, tier.ordinal());
        float factor = tier.bonusPercent / 100f;

        Integer baseDurability = stack.getPrototype().get(DataComponents.MAX_DAMAGE);
        if (baseDurability != null) {
            float durabilityFactor = factor < 0 ? 1f + factor * 2f : 1f + factor;
            stack.set(DataComponents.MAX_DAMAGE, Math.max(1, Math.round(baseDurability * durabilityFactor)));
        }
        if (traits.isEmpty()) stack.remove(ModComponents.TRAITS);
        else stack.set(ModComponents.TRAITS, List.copyOf(traits));

        // Бонус редкости вливается в исходные урон и броню предмета, чтобы подсказка показывала итог
        // («Урон при атаке: 8.75»), а не отдельную строку. Считается от прототипа, поэтому повторный вызов ничего не копит.
        // У брони модификаторов в компоненте нет — они берутся из getDefaultAttributeModifiers предмета.
        ItemAttributeModifiers base = stack.getPrototype().getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (base.modifiers().isEmpty()) base = stack.getItem().getDefaultAttributeModifiers(stack);
        ItemAttributeModifiers current = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        if (current.modifiers().isEmpty()) current = base;
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry entry : current.modifiers()) {
            ResourceLocation id = entry.modifier().id();
            if (id.equals(MODIFIER_ID) || id.equals(LEGACY_MODIFIER_ID) || id.equals(MINING_TRAIT_ID)) continue;
            AttributeModifier original = scalableBase(base, entry);
            AttributeModifier modifier = original == null ? entry.modifier()
                    : new AttributeModifier(original.id(), original.amount() * (1f + factor), original.operation());
            builder.add(entry.attribute(), modifier, entry.slot());
        }
        double mining = stack.getItem() instanceof DiggerItem ? tier.bonusPercent / 10.0 : 0;
        for (ItemTrait.Roll roll : traits) if (roll.type() == ItemTrait.MINING_SPEED) mining += roll.value();
        if (mining != 0) {
            builder.add(Attributes.MINING_EFFICIENCY, new AttributeModifier(MODIFIER_ID, mining, AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build().withTooltip(current.showInTooltip()));

        // Название в цвет редкости: так оно видно и в подсказке, и над панелью при выборе предмета.
        stack.set(DataComponents.ITEM_NAME, stack.getItem().getName(stack).copy().withStyle(tier.color));
    }

    /** Исходный модификатор урона, брони или прочности брони, который редкость масштабирует; null — модификатор не из прототипа. */
    @Nullable
    private static AttributeModifier scalableBase(ItemAttributeModifiers base, ItemAttributeModifiers.Entry entry) {
        if (!SCALED.contains(entry.attribute())) return null;
        for (ItemAttributeModifiers.Entry original : base.modifiers()) {
            AttributeModifier modifier = original.modifier();
            if (original.attribute().equals(entry.attribute()) && original.slot() == entry.slot()
                    && modifier.id().equals(entry.modifier().id()) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE
                    && modifier.amount() > 0) {
                return modifier;
            }
        }
        return null;
    }

    private static List<ItemTrait.Roll> rollTraits(ItemStack stack, Tier tier, RandomSource random) {
        return rollTraits(stack, tier, random, tier.traits, List.of());
    }

    private static List<ItemTrait.Roll> rollTraits(ItemStack stack, Tier tier, RandomSource random, int count, List<ItemTrait.Roll> existing) {
        if (count <= 0) return List.of();
        Set<ItemTrait.Kind> kinds = ItemTrait.kindsOf(stack);
        List<ItemTrait> pool = new ArrayList<>();
        for (ItemTrait trait : ItemTrait.values()) {
            boolean taken = existing.stream().anyMatch(roll -> roll.type() == trait);
            if (!taken && trait.fits(kinds)) pool.add(trait);
        }
        Collections.shuffle(pool, new java.util.Random(random.nextLong()));
        List<ItemTrait.Roll> result = new ArrayList<>();
        float power = Math.max(1f, tier.traitPower);
        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            ItemTrait trait = pool.get(i);
            float value = (trait.min() + random.nextFloat() * (trait.max() - trait.min())) * power;
            result.add(new ItemTrait.Roll(trait.id(), Math.round(value * 10f) / 10f));
        }
        return result;
    }

    /** Два случайных совместимых зачарования: у Легендарной — максимального уровня, у Реликвии — на уровень выше. */
    /** Шанс, что вещь Уникальной редкости и выше выйдет из крафта с одним ванильным зачарованием. */
    public static final float CRAFT_ENCHANT_CHANCE = 0.1f;
    /** Эти чары крафт не даёт: прочность, починка, скорость души (и все проклятия). */
    private static final Set<ResourceKey<Enchantment>> CRAFT_EXCLUDED = Set.of(Enchantments.UNBREAKING, Enchantments.MENDING, Enchantments.SOUL_SPEED);

    /** Одно случайное полезное зачарование случайного уровня, совместимое с уже наложенными. */
    public static boolean addCraftEnchantment(ItemStack stack, RandomSource random, RegistryAccess registries) {
        Registry<Enchantment> registry = registries.registryOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments existing = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        List<Holder<Enchantment>> candidates = new ArrayList<>();
        registry.holders().forEach(holder -> {
            if (holder.is(EnchantmentTags.CURSE) || CRAFT_EXCLUDED.stream().anyMatch(holder::is) || !holder.value().isSupportedItem(stack)) return;
            if (existing.keySet().stream().allMatch(other -> Enchantment.areCompatible(other, holder))) candidates.add(holder);
        });
        if (candidates.isEmpty()) return false;
        Holder<Enchantment> chosen = candidates.get(random.nextInt(candidates.size()));
        int max = chosen.value().getMaxLevel();
        stack.enchant(chosen, Math.max(existing.getLevel(chosen), 1 + random.nextInt(max)));
        return true;
    }

    private static int addEnchantments(ItemStack stack, Tier tier, RandomSource random, RegistryAccess registries) {
        Registry<Enchantment> registry = registries.registryOrThrow(Registries.ENCHANTMENT);
        List<Holder<Enchantment>> candidates = new ArrayList<>();
        registry.holders().forEach(holder -> {
            if (!holder.is(EnchantmentTags.CURSE) && holder.value().isSupportedItem(stack)) candidates.add(holder);
        });
        Collections.shuffle(candidates, new java.util.Random(random.nextLong()));
        int added = 0;
        for (Holder<Enchantment> candidate : candidates) {
            if (added >= tier.enchantments()) break;
            ItemEnchantments existing = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            boolean compatible = existing.keySet().stream().allMatch(other -> Enchantment.areCompatible(other, candidate));
            if (!compatible) continue;
            int level = candidate.value().getMaxLevel() + (tier == Tier.RELIC ? 1 : 0);
            stack.enchant(candidate, level);
            added++;
        }
        return added;
    }

    /**
     * Бросает редкость для только что созданного предмета с учётом ступени ремесла игрока;
     * о редкой удаче сообщает игроку.
     */
    public static Tier rollFor(Player player, ItemStack target, com.heroicpaths.craft.Profession profession) {
        int rank = com.heroicpaths.rpg.RpgManager.get(player).professions().rank(profession);
        // Редкая вещь и выше требует душ из камня души; без них она выходит на ступень ниже.
        com.heroicpaths.craft.SoulStones.Payment payment = com.heroicpaths.craft.SoulStones.settle(player, roll(player.getRandom(), rank));
        Tier tier = payment.tier();
        apply(target, tier, player.getRandom(), player.level().registryAccess());
        if (tier.ordinal() >= Tier.UNIQUE.ordinal() && player.getRandom().nextFloat() < CRAFT_ENCHANT_CHANCE
                && addCraftEnchantment(target, player.getRandom(), player.level().registryAccess()) && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable("message.heroicpaths.crafted_enchanted").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        com.heroicpaths.gear.GearSet set = com.heroicpaths.gear.GearSets.roll(target, player.getRandom());
        if (set != null && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatable("message.heroicpaths.crafted_set", set.displayName()).withStyle(ChatFormatting.GOLD));
        }
        if (payment.wanted() != null && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(com.heroicpaths.craft.SoulStones.shortMessage(payment));
        }
        if (tier.ordinal() >= Tier.RARE.ordinal() && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(payment.souls() > 0
                    ? Component.translatable("message.heroicpaths.crafted_rarity_souls", tier.displayName(), payment.souls())
                    : Component.translatable("message.heroicpaths.crafted_rarity", tier.displayName()), true);
            serverPlayer.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP,
                    SoundSource.PLAYERS, 0.6f, 0.9f + 0.15f * (tier.ordinal() - Tier.RARE.ordinal()));
        }
        return tier;
    }

    /** Старая вещь с качеством получает редкость (со всеми бонусами своей ступени). */
    public static boolean migrate(ItemStack stack, RandomSource random, @Nullable RegistryAccess registries) {
        if (stack.isEmpty() || stack.has(ModComponents.RARITY) || !stack.has(ModComponents.QUALITY)) return false;
        Tier tier = get(stack);
        apply(stack, tier == null ? Tier.COMMON : tier, random, registries);
        return true;
    }

    // ---------------------------------------------------------------- события

    /**
     * При обычном взятии результата крафта событие получает тот самый предмет, что у игрока на курсоре.
     * При Shift-клике предмет уже переложен в инвентарь, а событию досталась копия, поэтому ищем его там.
     */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        ItemStack crafted = event.getCrafting();
        if (player.level().isClientSide || !canHaveRarity(crafted) || crafted.has(ModComponents.RARITY)) return;
        ItemStack target = player.containerMenu.getCarried() == crafted ? crafted : findUnrolled(player, crafted);
        if (target == null) return;
        com.heroicpaths.craft.Profession profession = com.heroicpaths.craft.Profession.forItem(target);
        rollFor(player, target, profession);
        if (player instanceof ServerPlayer serverPlayer) com.heroicpaths.craft.ProfessionProgress.award(serverPlayer, profession, WORKBENCH_CRAFT_XP);
    }

    /** Опыт ремесла за оружие или броню, сделанные в обычном верстаке. */
    public static final int WORKBENCH_CRAFT_XP = 6;

    @Nullable
    private static ItemStack findUnrolled(Player player, ItemStack crafted) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.has(ModComponents.RARITY) && ItemStack.isSameItemSameComponents(stack, crafted)) return stack;
        }
        return null;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            migrateInventory(player);
            for (int i = 0; i < player.getEnderChestInventory().getContainerSize(); i++) {
                migrate(player.getEnderChestInventory().getItem(i), player.getRandom(), player.registryAccess());
            }
        }
    }

    /** Раз в 5 секунд переводит подобранные старые вещи. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 100 == 0) migrateInventory(player);
    }

    private static void migrateInventory(ServerPlayer player) {
        boolean changed = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            changed |= migrate(player.getInventory().getItem(i), player.getRandom(), player.registryAccess());
        }
        if (changed) player.inventoryMenu.broadcastChanges();
    }

    /** Бонус «быстрое натяжение»: лук и арбалет заряжаются быстрее. */
    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        float draw = traitValue(event.getItem(), ItemTrait.DRAW_SPEED);
        if (draw > 0 && event.getEntity().getRandom().nextFloat() * 100f < draw) event.setDuration(event.getDuration() - 1);
    }

    // ---------------------------------------------------------------- подсказка

    /** Строки «Редкость: …» и уникальные бонусы под названием. Возвращает, сколько строк добавлено. */
    public static int addTooltip(ItemStack stack, List<Component> tooltip) {
        Tier tier = get(stack);
        if (tier == null) return 0;
        int index = Math.min(1, tooltip.size());
        MutableComponent line = Component.translatable("tooltip.heroicpaths.rarity", tier.displayName());
        if (tier.bonusPercent != 0) {
            line.append(Component.literal(" (" + (tier.bonusPercent > 0 ? "+" : "") + tier.bonusPercent + "%)").withStyle(tier.color));
        }
        tooltip.add(index, line.withStyle(ChatFormatting.GRAY));
        int added = 1;
        for (ItemTrait.Roll roll : traits(stack)) {
            ItemTrait trait = roll.type();
            if (trait == null) continue;
            tooltip.add(index + added, trait.describe(roll.value()).withStyle(ChatFormatting.BLUE));
            added++;
        }
        return added;
    }
}
