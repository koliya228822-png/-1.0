package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.fx.Palette;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/**
 * Частицы умений: для каждой цветовой гаммы (Palette) — облачко (&lt;гамма&gt;_wisp), искра (&lt;гамма&gt;_ember),
 * вспышка (&lt;гамма&gt;_flash) и метка (&lt;гамма&gt;_mark) — неподвижная точка для контуров зон.
 */
public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, HeroicPaths.MODID);

    public enum Kind {
        WISP, EMBER, FLASH, MARK;

        public String suffix() {
            return "_" + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final Map<Palette, Map<Kind, DeferredHolder<ParticleType<?>, SimpleParticleType>>> TYPES = new EnumMap<>(Palette.class);

    static {
        for (Palette palette : Palette.values()) {
            Map<Kind, DeferredHolder<ParticleType<?>, SimpleParticleType>> kinds = new EnumMap<>(Kind.class);
            for (Kind kind : Kind.values())
                kinds.put(kind, PARTICLES.register(palette.id() + kind.suffix(), () -> new SimpleParticleType(false)));
            TYPES.put(palette, kinds);
        }
    }

    public static SimpleParticleType get(Palette palette, Kind kind) {
        return TYPES.get(palette).get(kind).get();
    }

    public static SimpleParticleType wisp(Palette palette) {
        return get(palette, Kind.WISP);
    }

    public static SimpleParticleType ember(Palette palette) {
        return get(palette, Kind.EMBER);
    }

    public static SimpleParticleType mark(Palette palette) {
        return get(palette, Kind.MARK);
    }

    public static SimpleParticleType flash(Palette palette) {
        return get(palette, Kind.FLASH);
    }

    private ModParticles() {}
}
