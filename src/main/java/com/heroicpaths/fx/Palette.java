package com.heroicpaths.fx;

import com.heroicpaths.rpg.stat.Element;

import java.util.Locale;

/**
 * Цветовые гаммы эффектов умений. У каждой гаммы три частицы — облачко, искра и вспышка (ModParticles):
 * частица рождается цветом start и остывает к end. Светящиеся гаммы видны и в темноте; пыль и камень (glow = false)
 * освещаются как мир.
 */
public enum Palette {
    FIRE(1f, 0.82f, 0.3f, 0.75f, 0.16f, 0.02f, true),
    FROST(0.88f, 0.97f, 1f, 0.3f, 0.6f, 1f, true),
    WATER(0.75f, 0.92f, 1f, 0.15f, 0.45f, 0.95f, true),
    HOLY(1f, 1f, 0.85f, 1f, 0.72f, 0.2f, true),
    ARCANE(0.95f, 0.8f, 1f, 0.5f, 0.2f, 1f, true),
    STORM(1f, 1f, 0.82f, 0.45f, 0.62f, 1f, true),
    NATURE(0.8f, 1f, 0.55f, 0.2f, 0.55f, 0.1f, true),
    POISON(0.75f, 1f, 0.35f, 0.25f, 0.45f, 0.05f, true),
    BLOOD(1f, 0.38f, 0.35f, 0.42f, 0f, 0.03f, true),
    SHADOW(0.62f, 0.5f, 0.8f, 0.12f, 0.04f, 0.2f, true),
    STEEL(1f, 0.98f, 0.9f, 0.62f, 0.58f, 0.52f, true),
    EARTH(0.82f, 0.68f, 0.46f, 0.42f, 0.3f, 0.18f, false),
    /** Светящаяся магия земли (стрелы земляных фокусов): янтарь. */
    AMBER(1f, 0.86f, 0.5f, 0.6f, 0.34f, 0.08f, true);

    public final float r0, g0, b0, r1, g1, b1;
    public final boolean glow;

    Palette(float r0, float g0, float b0, float r1, float g1, float b1, boolean glow) {
        this.r0 = r0;
        this.g0 = g0;
        this.b0 = b0;
        this.r1 = r1;
        this.g1 = g1;
        this.b1 = b1;
        this.glow = glow;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Гамма стихии по умолчанию (для каста и общих эффектов). */
    public static Palette of(Element element) {
        return switch (element) {
            case FIRE -> FIRE;
            case WATER -> FROST;
            case EARTH -> EARTH;
            case AIR -> STORM;
            case HOLY -> HOLY;
            case POISON -> POISON;
            default -> STEEL;
        };
    }
}
