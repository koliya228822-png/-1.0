package com.heroicpaths.item;

import com.heroicpaths.combat.WeaponType;
import net.minecraft.world.item.Tier;

/** Двуручный меч: режущий, тяжёлый и медленный. Если во второй руке что-то есть, урон вдвое меньше. */
public class GreatswordItem extends MeleeWeaponItem {
    public GreatswordItem(Tier tier, Properties properties) {
        super(tier, WeaponType.SLASHING, true, properties.attributes(createAttributes(tier, 6, -3.0f)));
    }
}
