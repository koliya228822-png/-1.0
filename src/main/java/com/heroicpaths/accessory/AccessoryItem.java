package com.heroicpaths.accessory;

import com.heroicpaths.item.ClassArmorItem;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.registry.ModComponents;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.stat.Stat;
import com.heroicpaths.rpg.stat.StatBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Аксессуар: перчатки, пояс, наручи (под тип брони — латы, кожа или ткань) или украшение ювелира.
 * Грейд I–VI хранится в компоненте gear_grade, прибавки растут с грейдом и редкостью, как у брони.
 * Перчатки, пояс и наручи дают прибавки только классу своего типа брони; украшения — всем.
 * ПКМ надевает аксессуар в свободный слот.
 */
public class AccessoryItem extends Item {
    private final AccessoryKind kind;
    /** Тип брони (класс, чья это броня): воин — латы, лучник — кожа, маг — ткань; null — украшение. */
    @Nullable
    private final PlayerClass armorClass;

    public AccessoryItem(AccessoryKind kind, @Nullable PlayerClass armorClass, Properties properties) {
        super(properties.stacksTo(1));
        this.kind = kind;
        this.armorClass = armorClass;
    }

    public AccessoryKind kind() {
        return kind;
    }

    @Nullable
    public PlayerClass armorClass() {
        return armorClass;
    }

    public static int grade(ItemStack stack) {
        return stack.getOrDefault(ModComponents.GEAR_GRADE, 1);
    }

    public static ItemStack withGrade(Item item, int grade) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModComponents.GEAR_GRADE, grade);
        return stack;
    }

    /** Прибавки вещи грейда II обычной редкости. */
    public StatBlock baseStats() {
        if (armorClass == null) {
            return switch (kind) {
                case NECKLACE -> StatBlock.of(Stat.MAX_HEALTH, 4, Stat.HEALING, 4);
                case EARRING -> StatBlock.of(Stat.CRIT_CHANCE, 2, Stat.CRIT_DAMAGE, 5);
                case RING -> StatBlock.of(Stat.ABILITY_DAMAGE, 3, Stat.COST_REDUCTION, 2);
                default -> StatBlock.EMPTY;
            };
        }
        return switch (armorClass) {
            case WARRIOR -> switch (kind) {
                case GLOVES -> StatBlock.of(Stat.MELEE_DAMAGE, 3, Stat.ATTACK_SPEED, 2);
                case BELT -> StatBlock.of(Stat.MAX_HEALTH, 4, Stat.DAMAGE_REDUCTION, 2);
                case BRACERS -> StatBlock.of(Stat.DAMAGE_REDUCTION, 2, Stat.THORNS, 1);
                default -> StatBlock.EMPTY;
            };
            case ARCHER -> switch (kind) {
                case GLOVES -> StatBlock.of(Stat.BOW_DAMAGE, 3, Stat.CRIT_CHANCE, 2);
                case BELT -> StatBlock.of(Stat.MAX_HEALTH, 2, Stat.MOVE_SPEED, 2);
                case BRACERS -> StatBlock.of(Stat.DODGE_CHANCE, 2, Stat.ATTACK_SPEED, 2);
                default -> StatBlock.EMPTY;
            };
            case MAGE -> switch (kind) {
                case GLOVES -> StatBlock.of(Stat.SPELL_DAMAGE, 3, Stat.COOLDOWN_REDUCTION, 2);
                case BELT -> StatBlock.of(Stat.MAX_RESOURCE, 8, Stat.MAX_HEALTH, 2);
                case BRACERS -> StatBlock.of(Stat.HEALING, 4, Stat.SPELL_DAMAGE, 2);
                default -> StatBlock.EMPTY;
            };
            default -> StatBlock.EMPTY;
        };
    }

    /** Прибавки этой вещи с учётом грейда и редкости. */
    public StatBlock stats(ItemStack stack) {
        float factor = (1f + ItemRarity.bonusPercent(stack) / 100f) * ClassArmorItem.gradeFactor(grade(stack));
        return baseStats().scaled(factor);
    }

    /** Даёт ли вещь прибавки игроку этого класса. */
    public boolean worksFor(PlayerClass playerClass) {
        return armorClass == null || ClassArmorItem.armorClass(playerClass) == armorClass;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Accessories accessories = RpgManager.get(player).accessories();
        int slot = accessories.freeSlotFor(stack);
        if (slot < 0) return InteractionResultHolder.fail(stack);
        if (player instanceof ServerPlayer serverPlayer) {
            accessories.set(slot, stack.copyWithCount(1));
            stack.shrink(1);
            AccessoryEvents.changed(serverPlayer);
        }
        player.playSound(SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.8f, 1.2f);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (armorClass == null) {
            tooltip.add(Component.translatable("tooltip.heroicpaths.jewelry", kind.displayName()).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.heroicpaths.accessory." + armorClass.name().toLowerCase(java.util.Locale.ROOT),
                    kind.displayName()).withStyle(ChatFormatting.GRAY));
        }
        for (Component line : stats(stack).describe()) tooltip.add(line.copy().withStyle(ChatFormatting.BLUE));
        tooltip.add(Component.translatable("tooltip.heroicpaths.accessory.equip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
