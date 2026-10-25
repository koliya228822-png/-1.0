package com.heroicpaths.item;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;

import org.jetbrains.annotations.Nullable;
import java.util.function.Consumer;

/** Боевой топор мода: рубит как обычный топор, но, как и всё снаряжение, не изнашивается в бою. */
public class BattleAxeItem extends AxeItem {
    public BattleAxeItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    /** Снаряжение не изнашивается в бою: прочность уходит только при смерти. */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<Item> onBroken) {
        return 0;
    }
}
