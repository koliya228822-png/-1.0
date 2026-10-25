package com.heroicpaths.craft;

import com.heroicpaths.item.GearBlankItem;
import com.heroicpaths.item.Grades;
import com.heroicpaths.item.ItemRarity;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Ремесло гроссмейстера: на наковальне вещь грейда III–V и заготовка следующего грейда дают ту же вещь
 * на грейд выше (IV–VI). Редкость, уникальные бонусы, чары, сет и имя сохраняются, урон, броня и прочность
 * считаются по новой вещи. Нужна ступень «гроссмейстер» в ремесле этой вещи.
 */
public final class GrandmasterForging {
    /** Ступень ремесла для алмазного снаряжения. */
    public static final int RANK = 4;
    /** Сколько уровней опыта стоит переплавка: за IV, V и VI. */
    private static final int[] LEVEL_COST = {0, 0, 0, 0, 10, 15, 20};
    /** Как называется вещь следующего грейда: подстроки имени вещи III грейда и чем их заменить. */
    private static final String[][] FROM_GRADE_3 = {
            {"warrior_damascus_", "warrior_diamond_"}, {"archer_impregnated_", "archer_diamond_"},
            {"mage_astral_", "mage_diamond_"}, {"ranger_longbow", "diamond_longbow"}, {"_archmage_", "_diamond_"},
            {"damascus_", "diamond_"}};

    private GrandmasterForging() {}

    /** Та же вещь на грейд выше или null. */
    @Nullable
    public static Item next(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String path = id.getPath();
        int grade = Grades.of(new ItemStack(item));
        String next = null;
        if (grade == 3) {
            for (String[] rule : FROM_GRADE_3) {
                if (path.contains(rule[0])) {
                    next = path.replace(rule[0], rule[1]);
                    break;
                }
            }
        } else if (grade == 4) {
            next = replaceToken(path, "diamond", "faceted");
        } else if (grade == 5) {
            next = replaceToken(path, "faceted", "brilliant");
        }
        if (next == null || next.equals(path)) return null;
        Item result = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(id.getNamespace(), next));
        return result == Items.AIR ? null : result;
    }

    private static String replaceToken(String path, String from, String to) {
        String[] parts = path.split("_");
        for (int i = 0; i < parts.length; i++) if (parts[i].equals(from)) parts[i] = to;
        return String.join("_", parts);
    }

    /** Что выйдет из вещи и заготовки; пустой стек — не подходят. */
    public static ItemStack result(ItemStack gear, ItemStack blank) {
        if (gear.isEmpty() || !(blank.getItem() instanceof GearBlankItem blankItem) || !blankItem.fits(gear)) return ItemStack.EMPTY;
        Item next = next(gear.getItem());
        if (next == null) return ItemStack.EMPTY;
        ItemStack result = gear.transmuteCopy(next, 1);
        float worn = gear.getMaxDamage() > 0 ? gear.getDamageValue() / (float) gear.getMaxDamage() : 0f;
        ItemRarity.refresh(result);
        if (result.isDamageableItem()) result.setDamageValue(Math.round(worn * result.getMaxDamage()));
        return result;
    }

    public static int levelCost(ItemStack result) {
        int grade = Grades.of(result);
        return grade >= 0 && grade < LEVEL_COST.length ? LEVEL_COST[grade] : 0;
    }

    /** Хватает ли игроку ступени ремесла для этой вещи. */
    public static boolean canForge(Player player, ItemStack result) {
        return player.getAbilities().instabuild
                || RpgManager.get(player).professions().rank(Profession.forItem(result)) >= RANK;
    }
}
