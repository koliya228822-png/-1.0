package com.heroicpaths.client.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.heroicpaths.HeroicPaths;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Метки игрока на карте. Хранятся на его компьютере, отдельно для каждого мира и сервера:
 * {@code .minecraft/heroicpaths/waypoints/<мир>.json}.
 */
public final class ClientWaypoints {
    /** Цвета меток по очереди. */
    public static final int[] COLORS = {0xFFE04040, 0xFF40A0FF, 0xFFFFD040, 0xFF60E060, 0xFFE070E0, 0xFFFF9030, 0xFF40E0D0, 0xFFFFFFFF};
    public static final int MAX_NAME = 32;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Метка: название, измерение, координаты, цвет. */
    public static final class Waypoint {
        public String name;
        public String dimension;
        public int x, y, z;
        public int color;

        public Waypoint(String name, String dimension, int x, int y, int z, int color) {
            this.name = name;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.color = color;
        }
    }

    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();
    private static Path file;

    private ClientWaypoints() {}

    public static List<Waypoint> all() {
        return WAYPOINTS;
    }

    /** Метки текущего измерения. */
    public static List<Waypoint> here() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return List.of();
        String dimension = minecraft.level.dimension().location().toString();
        return WAYPOINTS.stream().filter(w -> dimension.equals(w.dimension)).toList();
    }

    public static Waypoint add(String name, int x, int y, int z) {
        Minecraft minecraft = Minecraft.getInstance();
        ensureLoaded();
        String cleaned = clean(name);
        if (cleaned.isEmpty()) cleaned = net.minecraft.network.chat.Component.translatable("gui.heroicpaths.map.default_name", WAYPOINTS.size() + 1).getString();
        Waypoint waypoint = new Waypoint(cleaned, minecraft.level.dimension().location().toString(), x, y, z,
                COLORS[WAYPOINTS.size() % COLORS.length]);
        WAYPOINTS.add(waypoint);
        save();
        return waypoint;
    }

    public static void rename(Waypoint waypoint, String name) {
        String cleaned = clean(name);
        if (cleaned.isEmpty()) return;
        waypoint.name = cleaned;
        save();
    }

    public static void recolor(Waypoint waypoint) {
        int index = 0;
        for (int i = 0; i < COLORS.length; i++) if (COLORS[i] == waypoint.color) index = i;
        waypoint.color = COLORS[(index + 1) % COLORS.length];
        save();
    }

    public static void remove(Waypoint waypoint) {
        WAYPOINTS.remove(waypoint);
        save();
    }

    public static String clean(String name) {
        String trimmed = name.strip();
        return trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed;
    }

    // ---------------------------------------------------------------- файл

    /** Файл меток этого мира: для одиночной игры — по папке сохранения, для сервера — по адресу. */
    private static Path fileFor(Minecraft minecraft) {
        String key;
        if (minecraft.getSingleplayerServer() != null) {
            Path root = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            key = "local_" + root.getFileName();
        } else if (minecraft.getCurrentServer() != null) {
            key = "server_" + minecraft.getCurrentServer().ip;
        } else {
            key = "unknown";
        }
        key = key.replaceAll("[^\\p{L}\\p{N}._-]", "_");
        return minecraft.gameDirectory.toPath().resolve(HeroicPaths.MODID).resolve("waypoints").resolve(key + ".json");
    }

    public static void ensureLoaded() {
        Minecraft minecraft = Minecraft.getInstance();
        Path wanted = fileFor(minecraft);
        if (wanted.equals(file)) return;
        file = wanted;
        WAYPOINTS.clear();
        if (!Files.exists(file)) return;
        try {
            List<Waypoint> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Waypoint>>() {}.getType());
            if (loaded != null) for (Waypoint w : loaded) if (w != null && w.name != null && w.dimension != null) WAYPOINTS.add(w);
        } catch (IOException | RuntimeException e) {
            HeroicPaths.LOGGER.warn("Could not read waypoints {}", file, e);
        }
    }

    private static void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(WAYPOINTS), StandardCharsets.UTF_8);
        } catch (IOException e) {
            HeroicPaths.LOGGER.warn("Could not save waypoints {}", file, e);
        }
    }

    /** Выход из мира: следующий мир загрузит свои метки. */
    public static void reset() {
        file = null;
        WAYPOINTS.clear();
    }
}
