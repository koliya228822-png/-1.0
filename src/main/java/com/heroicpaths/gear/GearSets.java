package com.heroicpaths.gear;

import com.heroicpaths.accessory.Accessories;
import com.heroicpaths.accessory.AccessoryItem;
import com.heroicpaths.item.ClassArmorItem;
import com.heroicpaths.item.Grades;
import com.heroicpaths.registry.ModComponents;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.stat.StatBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Сеты снаряжения. Созданная броня, перчатки, пояс или наручи грейда III и выше могут получить случайный сет
 * своего типа брони (шанс растёт с грейдом). Сет считается по надетой броне и слотам перчаток, пояса и наручей.
 */
public final class GearSets {
    /** Шанс сета по грейду вещи (индекс — грейд). */
    private static final float[] CHANCE = {0, 0, 0, 0.2f, 0.3f, 0.4f, 0.5f};
    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    /** Слоты аксессуаров, которые входят в сет: перчатки, пояс, наручи. */
    private static final int SET_ACCESSORY_SLOTS = 3;

    private GearSets() {}

    @Nullable
    public static GearSet of(ItemStack stack) {
        return stack.isEmpty() ? null : GearSet.byId(stack.get(ModComponents.GEAR_SET));
    }

    /** Тип брони вещи-части сета или null, если вещь в сет не входит. */
    @Nullable
    public static PlayerClass armorType(ItemStack stack) {
        if (stack.getItem() instanceof ClassArmorItem armor) return ClassArmorItem.armorClass(armor.playerClass());
        if (stack.getItem() instanceof AccessoryItem accessory && accessory.kind().isSetPiece()) return accessory.armorClass();
        return null;
    }

    public static boolean canHaveSet(ItemStack stack) {
        return armorType(stack) != null && Grades.of(stack) >= 3;
    }

    public static float chance(int grade) {
        return CHANCE[Math.max(0, Math.min(CHANCE.length - 1, grade))];
    }

    /** Бросок сета для только что созданной вещи. Возвращает выпавший сет или null. */
    @Nullable
    public static GearSet roll(ItemStack stack, RandomSource random) {
        if (!canHaveSet(stack) || stack.has(ModComponents.GEAR_SET)) return null;
        if (random.nextFloat() >= chance(Grades.of(stack))) return null;
        List<GearSet> sets = GearSet.forArmor(armorType(stack));
        GearSet set = sets.get(random.nextInt(sets.size()));
        stack.set(ModComponents.GEAR_SET, set.id());
        return set;
    }

    /** Сколько вещей каждого сета надето. */
    public static Map<GearSet, Integer> count(Player player) {
        Map<GearSet, Integer> counts = new EnumMap<>(GearSet.class);
        for (EquipmentSlot slot : ARMOR_SLOTS) add(counts, player.getItemBySlot(slot));
        Accessories accessories = RpgManager.get(player).accessories();
        for (int i = 0; i < SET_ACCESSORY_SLOTS; i++) add(counts, accessories.get(i));
        return counts;
    }

    private static void add(Map<GearSet, Integer> counts, ItemStack stack) {
        GearSet set = of(stack);
        if (set != null) counts.merge(set, 1, Integer::sum);
    }

    /** Прибавки активных сетов. */
    public static void collect(Player player, StatBlock into) {
        count(player).forEach((set, pieces) -> {
            if (pieces >= GearSet.HALF) into.addAll(set.half());
            if (pieces >= GearSet.FULL) into.addAll(set.full());
        });
    }

    public static boolean hasPerk(Player player, GearSet.Perk perk) {
        for (Map.Entry<GearSet, Integer> entry : count(player).entrySet()) {
            GearSet set = entry.getKey();
            if (entry.getValue() >= GearSet.HALF && set.halfPerk() == perk) return true;
            if (entry.getValue() >= GearSet.FULL && set.fullPerk() == perk) return true;
        }
        return false;
    }

    /** Строки «Гладиатор 5/7» для окна аксессуаров. */
    public static List<Component> summary(Player player) {
        List<Component> lines = new ArrayList<>();
        count(player).forEach((set, pieces) -> lines.add(set.displayName().append(" " + pieces + "/" + GearSet.FULL)
                .withStyle(pieces >= GearSet.FULL ? ChatFormatting.GOLD : pieces >= GearSet.HALF ? ChatFormatting.YELLOW : ChatFormatting.GRAY)));
        return lines;
    }

    // ================================================================ подсказка

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        GearSet set = of(event.getItemStack());
        if (set == null) return;
        int pieces = event.getEntity() == null ? 0 : count(event.getEntity()).getOrDefault(set, 0);
        List<Component> tooltip = event.getToolTip();
        tooltip.add(Component.translatable("tooltip.heroicpaths.gear_set", set.displayName(), pieces, GearSet.FULL).withStyle(ChatFormatting.GOLD));
        for (boolean full : new boolean[]{false, true}) {
            boolean active = pieces >= (full ? GearSet.FULL : GearSet.HALF);
            tooltip.add(Component.translatable(full ? "tooltip.heroicpaths.gear_set.full" : "tooltip.heroicpaths.gear_set.half",
                    full ? GearSet.FULL : GearSet.HALF).withStyle(active ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
            for (Component line : set.describe(full)) {
                tooltip.add(Component.literal("  ").append(line).withStyle(active ? ChatFormatting.DARK_AQUA : ChatFormatting.DARK_GRAY));
            }
        }
    }

    // ================================================================ особые свойства

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (event.getSource().is(DamageTypeTags.IS_FALL) && hasPerk(player, GearSet.Perk.FEATHER_FALL)) {
                event.setCanceled(true);
                return;
            }
            if ((event.getSource().is(DamageTypeTags.IS_FIRE) || event.getSource().is(DamageTypeTags.IS_FREEZING))
                    && hasPerk(player, GearSet.Perk.ELEMENTAL_WARD)) {
                event.setCanceled(true);
                return;
            }
            if (event.getSource().getDirectEntity() instanceof Projectile && hasPerk(player, GearSet.Perk.RANGED_GUARD)) {
                event.setAmount(event.getAmount() * 0.85f);
            }
        }
        if (event.getSource().getEntity() instanceof Player attacker && event.getSource().getDirectEntity() == attacker
                && attacker.getHealth() < attacker.getMaxHealth() * 0.5f && hasPerk(attacker, GearSet.Perk.BLOODLUST)) {
            event.setAmount(event.getAmount() * 1.25f);
        }
    }

    @SubscribeEvent
    public static void onKnockBack(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof Player player && hasPerk(player, GearSet.Perk.NO_KNOCKBACK)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEffect(MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof Player player)) return;
        var effect = event.getEffectInstance().getEffect();
        boolean immune = effect.is(MobEffects.MOVEMENT_SLOWDOWN) && hasPerk(player, GearSet.Perk.NO_SLOWNESS)
                || (effect.is(MobEffects.POISON) || effect.is(MobEffects.WITHER)) && hasPerk(player, GearSet.Perk.PURITY)
                || effect.is(MobEffects.WITHER) && hasPerk(player, GearSet.Perk.NO_WITHER)
                || (effect.is(MobEffects.BLINDNESS) || effect.is(MobEffects.DARKNESS)) && hasPerk(player, GearSet.Perk.NIGHT_EYES);
        if (immune) event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }
}
