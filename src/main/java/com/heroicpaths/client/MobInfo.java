package com.heroicpaths.client;

import com.heroicpaths.combat.MobLevels;

/** Цвета и значки плашки уровня моба. */
public final class MobInfo {
    private MobInfo() {}

    /** Красный — моб на 5+ уровней выше, оранжевый — на 3–4, жёлтый — ровня, зелёный — слабее, серый — на 5+ ниже. */
    public static int levelColor(int diff) {
        if (diff >= MobLevels.LEVEL_GAP) return 0xFF5A50;
        if (diff >= 3) return 0xFFA040;
        if (diff > -3) return 0xFFE070;
        if (diff > -MobLevels.LEVEL_GAP) return 0x70E070;
        return 0x9A9A9A;
    }

    public static String resistIcon(MobLevels.Resist resist) {
        return switch (resist) {
            case PHYSICAL -> "■";
            case PROJECTILE -> "➶";
            case MAGIC -> "✦";
            case FIRE -> "▲";
        };
    }

    public static int resistColor(MobLevels.Resist resist) {
        return switch (resist) {
            case PHYSICAL -> 0xB8C0C8;
            case PROJECTILE -> 0xD8B880;
            case MAGIC -> 0xC890FF;
            case FIRE -> 0xFF9040;
        };
    }
}
