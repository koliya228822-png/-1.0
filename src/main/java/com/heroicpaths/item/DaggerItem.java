package com.heroicpaths.item;

import com.heroicpaths.combat.WeaponType;
import net.minecraft.world.item.Tier;

/** Кинжал: колющий, слабый, но быстрый удар. Хорош в паре с другим одноручным оружием. */
public class DaggerItem extends MeleeWeaponItem {
    public DaggerItem(Tier tier, Properties properties) {
        super(tier, WeaponType.PIERCING, false, properties.attributes(createAttributes(tier, 1, -1.6f)));
    }
}
