package com.heroicpaths.item;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.rpg.PlayerClass;
import com.heroicpaths.rpg.stat.Stat;
import com.heroicpaths.rpg.stat.StatBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Предмет классовой брони: прибавки к характеристикам, если класс игрока совпадает.
 * Грейд (I–III) зависит от материала: чем выше грейд, тем сильнее прибавки и бонус комплекта.
 */
public class ClassArmorItem extends ArmorItem {
    private final PlayerClass playerClass;
    private final int grade;

    public ClassArmorItem(Holder<ArmorMaterial> material, Type type, PlayerClass playerClass, int grade, Properties properties) {
        super(material, type, properties);
        this.playerClass = playerClass;
        this.grade = grade;
    }

    public PlayerClass playerClass() {
        return playerClass;
    }

    public int grade() {
        return grade;
    }

    /** Комплект — по материалу: warrior_iron, mage_astral и т. д. */
    public String setName() {
        return getMaterial().unwrapKey().map(key -> key.location().getPath()).orElse("warrior");
    }

    /** Текстура объёмной модели комплекта (одна на все четыре вещи). */
    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return HeroicPaths.id("textures/models/armor3d/" + setName() + ".png");
    }

    /** Множитель прибавок брони от грейда: I — 0.6, II — 1, III — 1.5. */
    public static float gradeFactor(int grade) {
        return switch (grade) {
            case 1 -> 0.6f;
            case 3 -> 1.5f;
            default -> 1f;
        };
    }

    /** Лёгкая броня лучника — классовая и для разбойника, и для охотника. */
    public static PlayerClass armorClass(PlayerClass playerClass) {
        return playerClass == PlayerClass.ROGUE || playerClass == PlayerClass.HUNTER ? PlayerClass.ARCHER : playerClass;
    }

    /** Прибавка от одной вещи грейда II. */
    public static StatBlock pieceStats(PlayerClass playerClass) {
        return switch (playerClass) {
            case WARRIOR -> StatBlock.of(Stat.MAX_HEALTH, 4, Stat.MELEE_DAMAGE, 2);
            case ARCHER -> StatBlock.of(Stat.BOW_DAMAGE, 4, Stat.MOVE_SPEED, 1);
            case MAGE -> StatBlock.of(Stat.SPELL_DAMAGE, 4, Stat.MAX_RESOURCE, 5);
            default -> StatBlock.EMPTY;
        };
    }

    /** Бонус полного комплекта (4 вещи своего класса) грейда II. */
    public static StatBlock setBonus(PlayerClass playerClass) {
        return switch (playerClass) {
            case WARRIOR -> StatBlock.of(Stat.MAX_HEALTH_PCT, 10, Stat.DAMAGE_REDUCTION, 8, Stat.THORNS, 1);
            case ARCHER -> StatBlock.of(Stat.MOVE_SPEED, 8, Stat.CRIT_CHANCE, 5, Stat.BOW_DAMAGE, 10);
            case MAGE -> StatBlock.of(Stat.MAX_RESOURCE, 30, Stat.RESOURCE_REGEN, 2, Stat.SPELL_DAMAGE, 10);
            default -> StatBlock.EMPTY;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(playerClass == PlayerClass.ARCHER ? "tooltip.heroicpaths.class_armor_light" : "tooltip.heroicpaths.class_armor", playerClass.displayName().withColor(playerClass.color()))
                .withStyle(ChatFormatting.GRAY));
        for (Component line : pieceStats(playerClass).scaled(gradeFactor(grade)).describe()) {
            tooltip.add(line.copy().withStyle(ChatFormatting.BLUE));
        }
        tooltip.add(Component.translatable("tooltip.heroicpaths.set_bonus").withStyle(ChatFormatting.GOLD));
        for (Component line : setBonus(playerClass).scaled(gradeFactor(grade)).describe()) {
            tooltip.add(Component.literal("  ").append(line).withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("tooltip.heroicpaths.set_grade_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
