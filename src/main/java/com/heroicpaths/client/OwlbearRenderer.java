package com.heroicpaths.client;

import com.heroicpaths.HeroicPaths;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PolarBearRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.PolarBear;

import java.io.InputStream;

/**
 * Совомедведь: модель медведя с бурым пёстрым оперением. Текстура строится при первой отрисовке
 * из текстуры белого медведя, загруженной из ресурсов игры: светлые места становятся охристыми перьями,
 * тёмные — бурыми.
 */
public class OwlbearRenderer extends PolarBearRenderer {
    private static final ResourceLocation SOURCE = ResourceLocation.withDefaultNamespace("textures/entity/bear/polarbear.png");
    private static final ResourceLocation TEXTURE = HeroicPaths.id("dynamic/owlbear");
    private static boolean attempted;
    private static boolean ready;

    public OwlbearRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(PolarBear bear) {
        if (!attempted) build();
        return ready ? TEXTURE : super.getTextureLocation(bear);
    }

    private static void build() {
        attempted = true;
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(SOURCE)) {
            NativeImage image = NativeImage.read(in);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int alpha = (abgr >>> 24) & 0xFF;
                    if (alpha == 0) continue;
                    int r = abgr & 0xFF;
                    int g = (abgr >> 8) & 0xFF;
                    int b = (abgr >> 16) & 0xFF;
                    float light = (r + g + b) / (3f * 255f);
                    // Пёстрые перья: полосы потемнее через каждые два пикселя.
                    float stripe = ((x + y * 2) % 5 == 0) ? 0.75f : 1f;
                    int nr = clamp((70 + 120 * light) * stripe);
                    int ng = clamp((45 + 80 * light) * stripe);
                    int nb = clamp((25 + 35 * light) * stripe);
                    image.setPixelRGBA(x, y, (alpha << 24) | (nb << 16) | (ng << 8) | nr);
                }
            }
            Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(image));
            ready = true;
        } catch (Exception e) {
            HeroicPaths.LOGGER.warn("Could not build the owlbear texture, using the polar bear one", e);
        }
    }

    private static int clamp(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }
}
