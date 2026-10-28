package com.heroicpaths.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * Рисует местность вокруг точки в текстуру, как ванильная карта: цвет блока сверху, тени по перепаду высот,
 * глубина воды. В мирах с потолком (Незер) и под землёй — пол пещеры на уровне игрока. Незагруженные чанки тёмные.
 */
public final class MapRenderer {
    private static final int UNKNOWN = 0xFF1A1410;
    private static final int CAVE_WALL = 0xFF2A2420;

    private final int size;
    private final DynamicTexture texture;
    private final ResourceLocation location;
    /** Центр (в блоках) и масштаб (блоков на пиксель) последней отрисовки. */
    private int centerX = Integer.MIN_VALUE, centerZ, step = 1;

    public MapRenderer(String name, int size) {
        this.size = size;
        this.texture = new DynamicTexture(size, size, true);
        this.location = Minecraft.getInstance().getTextureManager().register("heroicpaths_" + name, texture);
    }

    public ResourceLocation location() {
        return location;
    }

    public int size() {
        return size;
    }

    public int centerX() {
        return centerX;
    }

    public int centerZ() {
        return centerZ;
    }

    public int step() {
        return step;
    }

    /** Перерисовать карту с центром (cx, cz), {@code step} блоков на пиксель; {@code caveY} — уровень пещер или MIN_VALUE. */
    public void render(Level level, int cx, int cz, int step, int caveY) {
        this.centerX = cx;
        this.centerZ = cz;
        this.step = step;
        NativeImage image = texture.getPixels();
        if (image == null) return;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int half = size / 2;
        int[] previous = new int[size];
        // Строка над картой — только ради теней первой строки.
        for (int px = 0; px < size; px++) previous[px] = height(level, cx + (px - half) * step, cz - (half + 1) * step, caveY, pos);
        for (int pz = 0; pz < size; pz++) {
            int wz = cz + (pz - half) * step;
            for (int px = 0; px < size; px++) {
                int wx = cx + (px - half) * step;
                int color;
                int h = Integer.MIN_VALUE;
                if (!level.getChunkSource().hasChunk(wx >> 4, wz >> 4)) {
                    color = UNKNOWN;
                } else {
                    h = height(level, wx, wz, caveY, pos);
                    color = h == Integer.MIN_VALUE ? CAVE_WALL : color(level, wx, wz, h, previous[px], px + pz, pos);
                }
                previous[px] = h;
                image.setPixelRGBA(px, pz, color);
            }
        }
        texture.upload();
    }

    /** Высота видимого блока в столбце; MIN_VALUE — в пещерном режиме пола не нашлось. */
    private static int height(Level level, int x, int z, int caveY, BlockPos.MutableBlockPos pos) {
        if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) return Integer.MIN_VALUE;
        if (caveY == Integer.MIN_VALUE) {
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            int bottom = level.getMinBuildHeight();
            // Пропустить то, что на карте не видно (стекло, свет и т. п.).
            while (y > bottom && level.getBlockState(pos.set(x, y, z)).getMapColor(level, pos) == MapColor.NONE) y--;
            return y;
        }
        // Пещера: от уровня глаз вверх до воздуха, затем вниз до пола.
        int y = caveY;
        int top = caveY + 4;
        while (y < top && !level.getBlockState(pos.set(x, y, z)).isAir()) y++;
        if (y >= top) return Integer.MIN_VALUE;
        int floor = caveY - 24;
        while (y > floor && level.getBlockState(pos.set(x, y - 1, z)).getMapColor(level, pos) == MapColor.NONE) y--;
        return y <= floor ? Integer.MIN_VALUE : y - 1;
    }

    private static int color(Level level, int x, int z, int y, int northHeight, int parity, BlockPos.MutableBlockPos pos) {
        BlockState state = level.getBlockState(pos.set(x, y, z));
        if (!state.getFluidState().isEmpty()) {
            MapColor water = state.getMapColor(level, pos);
            int depth = 0;
            while (depth < 12 && !level.getBlockState(pos.set(x, y - depth - 1, z)).getFluidState().isEmpty()) depth++;
            double shade = depth * 0.1 + (parity & 1) * 0.2;
            MapColor.Brightness brightness = shade < 0.5 ? MapColor.Brightness.HIGH : shade > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
            return water.calculateRGBColor(brightness);
        }
        MapColor mapColor = state.getMapColor(level, pos);
        if (mapColor == MapColor.NONE) return UNKNOWN;
        MapColor.Brightness brightness;
        if (northHeight == Integer.MIN_VALUE || y == northHeight) brightness = MapColor.Brightness.NORMAL;
        else brightness = y > northHeight ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
        return mapColor.calculateRGBColor(brightness);
    }

    public void close() {
        texture.close();
    }
}
