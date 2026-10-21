package com.heroicpaths.client;

import com.heroicpaths.fx.Palette;
import com.heroicpaths.registry.ModParticles.Kind;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Частицы умений в цвет стихии (Palette): рождаются ярким цветом гаммы и остывают к тёмному, затем гаснут.
 * Светящиеся гаммы видны в темноте, пыль и камень освещаются как мир.
 * <ul>
 *   <li>Облачко (WISP) — мягкий клуб: шлейфы, волны, облака.</li>
 *   <li>Искра (EMBER) — мелкая, летит по своей скорости и чуть падает.</li>
 *   <li>Вспышка (FLASH) — большой короткий всполох в месте удара.</li>
 *   <li>Метка (MARK) — неподвижная точка на несколько тиков: контуры зон и знаков.</li>
 * </ul>
 */
public class GlowParticle extends TextureSheetParticle {
    private final Kind kind;
    private final Palette palette;
    private final float startSize;

    GlowParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, Palette palette, Kind kind) {
        super(level, x, y, z);
        this.kind = kind;
        this.palette = palette;
        xd = vx;
        yd = vy;
        zd = vz;
        hasPhysics = false;
        switch (kind) {
            case WISP -> {
                friction = 0.86F;
                gravity = palette == Palette.EARTH ? 0.01F : -0.03F;
                lifetime = 7 + random.nextInt(6);
                startSize = 0.3F + random.nextFloat() * 0.15F;
            }
            case EMBER -> {
                friction = 0.94F;
                gravity = 0.05F;
                lifetime = 10 + random.nextInt(12);
                startSize = 0.05F + random.nextFloat() * 0.04F;
            }
            case MARK -> {
                friction = 0.6F;
                gravity = 0;
                lifetime = 5 + random.nextInt(2);
                startSize = 0.11F + random.nextFloat() * 0.03F;
            }
            default -> {
                friction = 0.5F;
                gravity = 0;
                lifetime = 4 + random.nextInt(2);
                startSize = 1.1F + random.nextFloat() * 0.3F;
            }
        }
        quadSize = startSize;
        roll = oRoll = random.nextFloat() * Mth.TWO_PI;
        pickSprite(sprites);
        tint(0);
    }

    /** Цвет по ходу жизни: от яркого цвета гаммы к тёмному. */
    private void tint(float k) {
        float c = kind == Kind.WISP ? k : kind == Kind.MARK ? k * 0.4F : Math.min(1, k * 1.2F);
        rCol = Mth.lerp(c, palette.r0, palette.r1);
        gCol = Mth.lerp(c, palette.g0, palette.g1);
        bCol = Mth.lerp(c, palette.b0, palette.b1);
        alpha = switch (kind) {
            case WISP -> 0.95F * (1 - k * k);
            case EMBER -> 1 - k * k * k;
            case MARK -> 1 - k * k;
            default -> 0.9F * (1 - k);
        };
    }

    @Override
    public void tick() {
        super.tick();
        tint(age / (float) lifetime);
        if (kind == Kind.WISP) {
            oRoll = roll;
            roll += 0.08F;
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float k = Mth.clamp((age + partialTick) / lifetime, 0, 1);
        return startSize * switch (kind) {
            case WISP -> 1 - 0.55F * k;
            case EMBER -> 1 - k;
            case MARK -> 1;
            default -> 0.6F + 0.4F * Mth.sin(k * Mth.PI);
        };
    }

    @Override
    protected int getLightColor(float partialTick) {
        return palette.glow ? LightTexture.FULL_BRIGHT : super.getLightColor(partialTick);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record Provider(SpriteSet sprites, Palette palette, Kind kind) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new GlowParticle(level, x, y, z, vx, vy, vz, sprites, palette, kind);
        }
    }
}
