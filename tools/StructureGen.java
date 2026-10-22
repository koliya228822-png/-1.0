import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.GZIPOutputStream;

/**
 * Генератор NBT-шаблонов зданий гильдий для деревень пяти биомов и шаблонов с наставниками.
 *
 * Запуск из корня проекта (нужна JDK 21):
 *   java tools/StructureGen.java src/main/resources
 *
 * Здания стыкуются с улицами ванильных деревень через jigsaw "minecraft:building_entrance" так же,
 * как ванильные дома: вход с запада (x = 0), высота входа повторяет ванильные дома этого биома.
 */
public class StructureGen {
    static final String MODID = "heroicpaths";
    static final int DATA_VERSION = 3955; // Minecraft 1.21.1

    /** @param entranceY высота jigsaw-входа над полом: 0 в равнинах и тайге, 1 в пустыне, саванне и снегах. */
    record Biome(String name, String villagerType, int entranceY, String floor, String foundation, String wall,
                 String corner, String roofStairs, String roofSlab, String roofBlock, String door) {}

    record Guild(String name, String profession, String workstation, String color) {}

    static final List<Biome> BIOMES = List.of(
            new Biome("plains", "minecraft:plains", 0, "minecraft:oak_planks", "minecraft:cobblestone",
                    "minecraft:oak_planks", "minecraft:stripped_oak_log[axis=y]", "minecraft:oak_stairs",
                    "minecraft:oak_slab", "minecraft:oak_planks", "minecraft:oak_door"),
            new Biome("desert", "minecraft:desert", 1, "minecraft:smooth_sandstone", "minecraft:cut_sandstone",
                    "minecraft:sandstone", "minecraft:chiseled_sandstone", "minecraft:smooth_sandstone_stairs",
                    "minecraft:smooth_sandstone_slab", "minecraft:smooth_sandstone", "minecraft:jungle_door"),
            new Biome("savanna", "minecraft:savanna", 1, "minecraft:acacia_planks", "minecraft:terracotta",
                    "minecraft:acacia_planks", "minecraft:acacia_log[axis=y]", "minecraft:acacia_stairs",
                    "minecraft:acacia_slab", "minecraft:acacia_planks", "minecraft:acacia_door"),
            new Biome("snowy", "minecraft:snow", 1, "minecraft:spruce_planks", "minecraft:polished_diorite",
                    "minecraft:snow_block", "minecraft:stripped_spruce_log[axis=y]", "minecraft:spruce_stairs",
                    "minecraft:spruce_slab", "minecraft:spruce_planks", "minecraft:spruce_door"),
            new Biome("taiga", "minecraft:taiga", 0, "minecraft:spruce_planks", "minecraft:mossy_cobblestone",
                    "minecraft:spruce_planks", "minecraft:spruce_log[axis=y]", "minecraft:dark_oak_stairs",
                    "minecraft:dark_oak_slab", "minecraft:dark_oak_planks", "minecraft:spruce_door"));

    static final List<Guild> GUILDS = List.of(
            new Guild("swordmaster_guild", "swordmaster", MODID + ":weapon_rack", "red"),
            new Guild("archer_guild", "master_archer", MODID + ":archery_target", "green"),
            new Guild("mage_guild", "archmage", MODID + ":arcane_lectern", "purple"));

    public static void main(String[] args) throws IOException {
        Path resources = Path.of(args.length > 0 ? args[0] : "src/main/resources");
        Path structures = resources.resolve("data/" + MODID + "/structure/village");
        Path pools = resources.resolve("data/" + MODID + "/worldgen/template_pool/village");
        int count = 0;
        for (Biome biome : BIOMES) {
            for (Guild guild : GUILDS) {
                write(structures.resolve(biome.name() + "/" + guild.name() + ".nbt"), buildGuild(biome, guild).toNbt());
                write(structures.resolve(biome.name() + "/" + guild.profession() + "_mentor.nbt"), buildMentor(biome, guild).toNbt());
                writeText(pools.resolve(biome.name() + "/" + guild.profession() + "_mentor.json"), mentorPool(biome, guild));
                count += 3;
            }
        }
        // Распорядитель гильдии искателей приключений (сама гильдия теперь — зал в центре деревни, см. townPlaza).
        Guild adventurers = new Guild("adventurers_guild", "guildmaster", MODID + ":order_board", "orange");
        for (Biome biome : BIOMES) {
            write(structures.resolve(biome.name() + "/guildmaster_mentor.nbt"), buildMentor(biome, adventurers).toNbt());
            writeText(pools.resolve(biome.name() + "/guildmaster_mentor.json"), mentorPool(biome, adventurers));
            count += 3;
        }
        count += generateCrypt(resources);
        count += generatePortalDungeons(resources);
        count += generateWildEntrances(resources);
        count += generateTownPlazas(resources);
        System.out.println("Generated " + count + " files under " + resources.toAbsolutePath());
    }

    // ================================================================ данжи через врата

    /** Тема данжа: те же планировки склепа, но другие материалы, мобы, сундуки и босс. */
    record Theme(String name, Map<String, String> substitutions) {}

    static final Theme FORGE = new Theme("forge", Map.ofEntries(
            Map.entry("minecraft:stone_bricks", "minecraft:nether_bricks"),
            Map.entry("minecraft:mossy_stone_bricks", "minecraft:red_nether_bricks"),
            Map.entry("minecraft:cracked_stone_bricks", "minecraft:cracked_nether_bricks"),
            Map.entry("minecraft:chiseled_stone_bricks", "minecraft:chiseled_nether_bricks"),
            Map.entry("minecraft:stone_brick_stairs", "minecraft:nether_brick_stairs"),
            Map.entry("minecraft:soul_lantern", "minecraft:lantern"),
            Map.entry("minecraft:soul_campfire", "minecraft:campfire"),
            Map.entry("minecraft:cobweb", "minecraft:air"),
            Map.entry("minecraft:polished_deepslate", "minecraft:magma_block"),
            Map.entry("minecraft:crying_obsidian", "minecraft:magma_block"),
            Map.entry("minecraft:bookshelf", "minecraft:blackstone"),
            Map.entry("minecraft:skeleton_skull", "minecraft:wither_skeleton_skull"),
            Map.entry("minecraft:zombie", "minecraft:wither_skeleton"),
            Map.entry("minecraft:skeleton", "minecraft:blaze"),
            Map.entry("minecraft:cave_spider", "minecraft:magma_cube"),
            Map.entry(MODID + ":crypt_king", MODID + ":flame_tyrant"),
            Map.entry(MODID + ":chests/crypt", MODID + ":chests/forge"),
            Map.entry(MODID + ":chests/crypt_treasure", MODID + ":chests/forge_treasure")));

    static final Theme FROST = new Theme("frost", Map.ofEntries(
            Map.entry("minecraft:stone_bricks", "minecraft:packed_ice"),
            Map.entry("minecraft:mossy_stone_bricks", "minecraft:blue_ice"),
            Map.entry("minecraft:cracked_stone_bricks", "minecraft:snow_block"),
            Map.entry("minecraft:chiseled_stone_bricks", "minecraft:chiseled_quartz_block"),
            Map.entry("minecraft:stone_brick_stairs", "minecraft:quartz_stairs"),
            Map.entry("minecraft:cobweb", "minecraft:powder_snow"),
            Map.entry("minecraft:polished_deepslate", "minecraft:blue_ice"),
            Map.entry("minecraft:polished_blackstone_bricks", "minecraft:packed_ice"),
            Map.entry("minecraft:cracked_polished_blackstone_bricks", "minecraft:blue_ice"),
            Map.entry("minecraft:polished_blackstone", "minecraft:packed_ice"),
            Map.entry("minecraft:chiseled_polished_blackstone", "minecraft:chiseled_quartz_block"),
            Map.entry("minecraft:polished_blackstone_stairs", "minecraft:quartz_stairs"),
            Map.entry("minecraft:polished_blackstone_slab", "minecraft:quartz_slab"),
            Map.entry("minecraft:crying_obsidian", "minecraft:blue_ice"),
            Map.entry("minecraft:gilded_blackstone", "minecraft:diamond_block"),
            Map.entry("minecraft:zombie", MODID + ":undead_zombie"),
            Map.entry("minecraft:skeleton", MODID + ":ghoul"),
            Map.entry("minecraft:cave_spider", MODID + ":undead_zombie"),
            Map.entry(MODID + ":crypt_king", MODID + ":frost_lich"),
            Map.entry(MODID + ":chests/crypt", MODID + ":chests/frost"),
            Map.entry(MODID + ":chests/crypt_treasure", MODID + ":chests/frost_treasure")));

    /** Паучье логово: замшелый булыжник, мох и глубинный сланец, пауки и Королева пауков. */
    static final Theme SPIDER_LAIR = new Theme("spider_lair", Map.ofEntries(
            Map.entry("minecraft:stone_bricks", "minecraft:mossy_cobblestone"),
            Map.entry("minecraft:mossy_stone_bricks", "minecraft:moss_block"),
            Map.entry("minecraft:cracked_stone_bricks", "minecraft:cobblestone"),
            Map.entry("minecraft:chiseled_stone_bricks", "minecraft:mossy_stone_bricks"),
            Map.entry("minecraft:stone_brick_stairs", "minecraft:mossy_cobblestone_stairs"),
            Map.entry("minecraft:polished_blackstone_bricks", "minecraft:deepslate_bricks"),
            Map.entry("minecraft:cracked_polished_blackstone_bricks", "minecraft:cracked_deepslate_bricks"),
            Map.entry("minecraft:polished_blackstone", "minecraft:deepslate_tiles"),
            Map.entry("minecraft:chiseled_polished_blackstone", "minecraft:chiseled_deepslate"),
            Map.entry("minecraft:polished_blackstone_stairs", "minecraft:deepslate_tile_stairs"),
            Map.entry("minecraft:polished_blackstone_slab", "minecraft:deepslate_tile_slab"),
            Map.entry("minecraft:gilded_blackstone", "minecraft:tuff"),
            Map.entry("minecraft:crying_obsidian", "minecraft:moss_block"),
            Map.entry("minecraft:polished_deepslate", "minecraft:rooted_dirt"),
            Map.entry("minecraft:bookshelf", "minecraft:cobweb"),
            Map.entry("minecraft:zombie", "minecraft:spider"),
            Map.entry(MODID + ":crypt_king", MODID + ":spider_queen"),
            Map.entry(MODID + ":chests/crypt", MODID + ":chests/spider_lair"),
            Map.entry(MODID + ":chests/crypt_treasure", MODID + ":chests/spider_lair_treasure")));

    /** Затопленный храм: призмарин и морские фонари, утопленники и Утопший пророк. */
    static final Theme SUNKEN_TEMPLE = new Theme("sunken_temple", Map.ofEntries(
            Map.entry("minecraft:stone_bricks", "minecraft:prismarine_bricks"),
            Map.entry("minecraft:mossy_stone_bricks", "minecraft:prismarine"),
            Map.entry("minecraft:cracked_stone_bricks", "minecraft:dark_prismarine"),
            Map.entry("minecraft:chiseled_stone_bricks", "minecraft:sea_lantern"),
            Map.entry("minecraft:stone_brick_stairs", "minecraft:prismarine_brick_stairs"),
            Map.entry("minecraft:polished_blackstone_bricks", "minecraft:dark_prismarine"),
            Map.entry("minecraft:cracked_polished_blackstone_bricks", "minecraft:prismarine"),
            Map.entry("minecraft:polished_blackstone", "minecraft:prismarine_bricks"),
            Map.entry("minecraft:chiseled_polished_blackstone", "minecraft:sea_lantern"),
            Map.entry("minecraft:polished_blackstone_stairs", "minecraft:dark_prismarine_stairs"),
            Map.entry("minecraft:polished_blackstone_slab", "minecraft:dark_prismarine_slab"),
            Map.entry("minecraft:gilded_blackstone", "minecraft:gold_block"),
            Map.entry("minecraft:crying_obsidian", "minecraft:dark_prismarine"),
            Map.entry("minecraft:polished_deepslate", "minecraft:dark_prismarine"),
            Map.entry("minecraft:bookshelf", "minecraft:dark_prismarine"),
            Map.entry("minecraft:cobweb", "minecraft:air"),
            Map.entry("minecraft:soul_lantern", "minecraft:lantern"),
            Map.entry("minecraft:soul_campfire", "minecraft:campfire"),
            Map.entry("minecraft:zombie", "minecraft:drowned"),
            Map.entry("minecraft:cave_spider", "minecraft:drowned"),
            Map.entry(MODID + ":crypt_king", MODID + ":drowned_prophet"),
            Map.entry(MODID + ":chests/crypt", MODID + ":chests/sunken_temple"),
            Map.entry(MODID + ":chests/crypt_treasure", MODID + ":chests/sunken_temple_treasure")));

    /** Некрополь: глубинносланцевый кирпич и чернокамень, скелеты-иссушители и Лич. */
    static final Theme NECROPOLIS = new Theme("necropolis", Map.ofEntries(
            Map.entry("minecraft:stone_bricks", "minecraft:deepslate_bricks"),
            Map.entry("minecraft:mossy_stone_bricks", "minecraft:deepslate_tiles"),
            Map.entry("minecraft:cracked_stone_bricks", "minecraft:cracked_deepslate_bricks"),
            Map.entry("minecraft:chiseled_stone_bricks", "minecraft:chiseled_deepslate"),
            Map.entry("minecraft:stone_brick_stairs", "minecraft:deepslate_brick_stairs"),
            Map.entry("minecraft:skeleton_skull", "minecraft:wither_skeleton_skull"),
            Map.entry("minecraft:zombie", "minecraft:wither_skeleton"),
            Map.entry("minecraft:cave_spider", "minecraft:husk"),
            Map.entry(MODID + ":crypt_king", MODID + ":lich"),
            Map.entry(MODID + ":chests/crypt", MODID + ":chests/necropolis"),
            Map.entry(MODID + ":chests/crypt_treasure", MODID + ":chests/necropolis_treasure")));

    /** Глиняные копи — первый данж за вратами: глина и терракота, глиняные големы и Глиняный колосс. */
    static final Theme CLAY = new Theme("clay", Map.ofEntries(
            Map.entry("minecraft:stone_bricks", "minecraft:mud_bricks"),
            Map.entry("minecraft:mossy_stone_bricks", "minecraft:packed_mud"),
            Map.entry("minecraft:cracked_stone_bricks", "minecraft:brown_terracotta"),
            Map.entry("minecraft:chiseled_stone_bricks", "minecraft:red_terracotta"),
            Map.entry("minecraft:stone_brick_stairs", "minecraft:mud_brick_stairs"),
            Map.entry("minecraft:polished_blackstone_bricks", "minecraft:bricks"),
            Map.entry("minecraft:cracked_polished_blackstone_bricks", "minecraft:terracotta"),
            Map.entry("minecraft:polished_blackstone", "minecraft:packed_mud"),
            Map.entry("minecraft:chiseled_polished_blackstone", "minecraft:red_terracotta"),
            Map.entry("minecraft:polished_blackstone_stairs", "minecraft:brick_stairs"),
            Map.entry("minecraft:polished_blackstone_slab", "minecraft:brick_slab"),
            Map.entry("minecraft:gilded_blackstone", "minecraft:raw_copper_block"),
            Map.entry("minecraft:crying_obsidian", "minecraft:orange_terracotta"),
            Map.entry("minecraft:polished_deepslate", "minecraft:clay"),
            Map.entry("minecraft:bookshelf", "minecraft:clay"),
            Map.entry("minecraft:cobweb", "minecraft:air"),
            Map.entry("minecraft:soul_lantern", "minecraft:lantern"),
            Map.entry("minecraft:soul_campfire", "minecraft:campfire"),
            Map.entry("minecraft:zombie", MODID + ":clay_golem"),
            Map.entry("minecraft:skeleton", MODID + ":clay_golem"),
            Map.entry("minecraft:cave_spider", MODID + ":clay_golem"),
            Map.entry(MODID + ":crypt_king", MODID + ":clay_colossus"),
            Map.entry(MODID + ":chests/crypt", MODID + ":chests/clay"),
            Map.entry(MODID + ":chests/crypt_treasure", MODID + ":chests/clay_treasure")));

    /** Имя jigsaw-якоря в центре площадки спуска и зала у арены: по нему этаж ставится точно под шахтой с этажа выше. */
    static final String FLOOR_ANCHOR = MODID + ":floor_anchor";

    /**
     * Данжи за вратами многоэтажные: этажи 1–5 (зал прибытия или площадка спуска → центральный зал → комнаты,
     * а из центрального зала проход в зал со спуском вниз) и шестой — огромная арена босса.
     * Шахту с лестницей между этажами прорубает игра, когда строит проход.
     */
    static int generatePortalDungeons(Path resources) throws IOException {
        Path structures = resources.resolve("data/" + MODID + "/structure");
        Path pools = resources.resolve("data/" + MODID + "/worldgen/template_pool");
        Map<String, Template> pieces = new LinkedHashMap<>();
        pieces.put("arrival", cryptArrival());
        pieces.put("landing", floorLanding());
        pieces.put("floor_hub", cryptHub(false));
        pieces.put("descent", descentRoom());
        pieces.put("arena", bossArena());
        pieces.put("corridor_straight", cryptCorridor());
        pieces.put("corridor_turn", cryptTurn());
        pieces.put("tomb_room", tombRoom());
        pieces.put("library", libraryRoom());
        pieces.put("spider_den", spiderDen());
        pieces.put("armory", armoryRoom());
        pieces.put("dead_end", deadEnd());

        int count = 0;
        for (Theme theme : List.of(CLAY, FORGE, FROST, SPIDER_LAIR, SUNKEN_TEMPLE, NECROPOLIS)) {
            String prefix = MODID + ":" + theme.name() + "/";
            for (Map.Entry<String, Template> piece : pieces.entrySet()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nbt = (Map<String, Object>) substitute(piece.getValue().toNbt(), theme.substitutions(), CRYPT, prefix);
                write(structures.resolve(theme.name() + "/" + piece.getKey() + ".nbt"), nbt);
                count++;
            }
            for (String single : List.of("arrival", "landing", "floor_hub", "descent", "arena")) {
                writeText(pools.resolve(theme.name() + "/" + single + ".json"), poolJson(prefix, "minecraft:empty", single, 1));
            }
            writeText(pools.resolve(theme.name() + "/corridors.json"), poolJson(prefix, prefix + "dead_ends", "corridor_straight", 3, "corridor_turn", 2));
            writeText(pools.resolve(theme.name() + "/rooms.json"), poolJson(prefix, prefix + "dead_ends",
                    "tomb_room", 2, "library", 1, "spider_den", 1, "armory", 1));
            writeText(pools.resolve(theme.name() + "/dead_ends.json"), poolJson(prefix, "minecraft:empty", "dead_end", 1));
            count += 8;
        }
        return count;
    }

    // ================================================================ дикие входы в данжи

    /** Где в мире встречаются входы в данж: тема, биомы и соль размещения. */
    record WildEntrance(Theme theme, List<String> biomes, int salt) {}

    static final List<WildEntrance> WILD_ENTRANCES = List.of(
            new WildEntrance(SPIDER_LAIR, List.of("#minecraft:is_forest", "#minecraft:is_jungle", "#minecraft:is_taiga"), 734120981),
            new WildEntrance(FORGE, List.of("#minecraft:is_badlands", "minecraft:savanna_plateau", "minecraft:windswept_savanna",
                    "minecraft:stony_peaks", "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills"), 734120982),
            new WildEntrance(SUNKEN_TEMPLE, List.of("#minecraft:is_beach", "#minecraft:is_river", "minecraft:swamp",
                    "minecraft:mangrove_swamp"), 734120983),
            new WildEntrance(FROST, List.of("minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:snowy_taiga",
                    "minecraft:snowy_slopes", "minecraft:grove", "minecraft:frozen_peaks", "minecraft:jagged_peaks"), 734120984),
            new WildEntrance(NECROPOLIS, List.of("minecraft:desert", "minecraft:deep_dark", "minecraft:dripstone_caves",
                    "minecraft:savanna"), 734120985));

    /**
     * Дикие входы: на поверхности — небольшое святилище с колодцем-лестницей, под землёй (14 блоков вниз) —
     * зал с Вратами подземелья и рамкой портала. Сам данж строится в измерении данжей, как за вратами деревни.
     */
    static int generateWildEntrances(Path resources) throws IOException {
        Path data = resources.resolve("data/" + MODID);
        int count = 0;
        for (WildEntrance wild : WILD_ENTRANCES) {
            String id = wild.theme().name();
            @SuppressWarnings("unchecked")
            Map<String, Object> nbt = (Map<String, Object>) substitute(wildEntrance(id).toNbt(), wild.theme().substitutions(), CRYPT,
                    MODID + ":" + id + "/");
            write(data.resolve("structure/" + id + "/wild_entrance.nbt"), nbt);
            writeText(data.resolve("worldgen/template_pool/" + id + "/wild_entrance.json"),
                    poolJson(MODID + ":" + id + "/", "minecraft:empty", "wild_entrance", 1));
            writeText(data.resolve("worldgen/structure/" + id + "_entrance.json"), """
                    {
                      "type": "minecraft:jigsaw",
                      "biomes": "#%s:has_structure/%s_entrance",
                      "step": "underground_structures",
                      "terrain_adaptation": "none",
                      "spawn_overrides": {},
                      "start_pool": "%s:%s/wild_entrance",
                      "size": 1,
                      "start_height": { "absolute": -14 },
                      "project_start_to_heightmap": "WORLD_SURFACE_WG",
                      "max_distance_from_center": 80,
                      "use_expansion_hack": false
                    }
                    """.formatted(MODID, id, MODID, id));
            writeText(data.resolve("worldgen/structure_set/" + id + "_entrances.json"), """
                    {
                      "structures": [
                        { "structure": "%s:%s_entrance", "weight": 1 }
                      ],
                      "placement": {
                        "type": "minecraft:random_spread",
                        "spacing": 40,
                        "separation": 14,
                        "salt": %d
                      }
                    }
                    """.formatted(MODID, id, wild.salt()));
            StringBuilder biomes = new StringBuilder();
            for (int i = 0; i < wild.biomes().size(); i++) {
                if (i > 0) biomes.append(",\n");
                biomes.append("    \"").append(wild.biomes().get(i)).append('"');
            }
            writeText(data.resolve("tags/worldgen/biome/has_structure/" + id + "_entrance.json"),
                    "{\n  \"values\": [\n" + biomes + "\n  ]\n}\n");
            count += 5;
        }
        return count;
    }

    /**
     * Шаблон дикого входа 11×19×11 в блоках склепа (тема данжа заменяет их потом). Пол зала на y = 0,
     * поверхность на y = 14: святилище над колодцем с лестницей у (8, 2).
     */
    static Template wildEntrance(String dungeon) {
        Random r = new Random(dungeon.hashCode());
        Template t = new Template(11, 19, 11);
        room(t, 0, 0, 10, 10, 7, r);
        // Рамка портала в 2 блоках к югу от врат; проём 3×4 откроют сами врата.
        for (int x = 3; x <= 7; x++) {
            for (int y = 1; y <= 5; y++) {
                boolean frame = x == 3 || x == 7 || y == 5;
                if (frame) t.set(x, y, 6, (y == 5 && (x == 3 || x == 7)) ? "minecraft:crying_obsidian" : "minecraft:obsidian");
            }
        }
        t.set(5, 1, 4, MODID + ":dungeon_gate[dungeon=" + dungeon + ",facing=south]");
        for (int[] p : new int[][]{{2, 2}, {2, 8}, {8, 8}}) t.set(p[0], 6, p[1], SOUL_LANTERN);
        t.set(1, 6, 9, COBWEB);
        t.set(9, 6, 9, COBWEB);
        t.set(1, 1, 1, "minecraft:bone_block[axis=y]");
        t.set(9, 1, 9, "minecraft:bone_block[axis=y]");
        t.set(1, 2, 1, "minecraft:skeleton_skull[powered=false,rotation=6]");
        t.set(2, 1, 9, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        t.set(8, 1, 9, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");

        // Колодец: столб-опора у (8, 1) и лестница у (8, 2) от пола зала до поверхности.
        String ladder = "minecraft:ladder[facing=south,waterlogged=false]";
        for (int y = 1; y <= 6; y++) t.set(8, y, 1, "minecraft:chiseled_stone_bricks");
        for (int y = 1; y <= 14; y++) t.set(8, y, 2, ladder);
        for (int y = 8; y <= 13; y++) {
            t.set(7, y, 2, brick(r));
            t.set(9, y, 2, brick(r));
            t.set(8, y, 1, brick(r));
            t.set(8, y, 3, brick(r));
        }

        // Святилище на поверхности: площадка 5×5, четыре колонны, кровля и фонарь над колодцем.
        for (int x = 6; x <= 10; x++) {
            for (int z = 0; z <= 4; z++) {
                if (x != 8 || z != 2) t.set(x, 14, z, brick(r));
                for (int y = 15; y <= 17; y++) {
                    boolean pillar = (x == 6 || x == 10) && (z == 0 || z == 4);
                    t.set(x, y, z, pillar ? "minecraft:chiseled_stone_bricks" : AIR);
                }
                t.set(x, 18, z, (x == 6 || x == 10 || z == 0 || z == 4) ? "minecraft:stone_brick_stairs[facing=" +
                        (z == 0 ? "south" : z == 4 ? "north" : x == 6 ? "east" : "west") + ",half=top,shape=straight,waterlogged=false]"
                        : "minecraft:stone_bricks");
            }
        }
        t.set(8, 17, 2, SOUL_LANTERN);
        return t;
    }

    /** Зал прибытия: портал обратно к вратам деревни у северной стены и выход в главный зал на юг. */
    static Template cryptArrival() {
        Random r = new Random(11);
        Template t = new Template(9, 7, 11);
        room(t, 0, 0, 8, 10, 6, r);
        for (int x = 2; x <= 6; x++) {
            for (int y = 1; y <= 5; y++) {
                // Врата за спиной запечатаны: выйти из данжа можно, только убив босса или погибнув.
                boolean frame = x == 2 || x == 6 || y == 5;
                t.set(x, y, 1, frame ? "minecraft:obsidian" : "minecraft:crying_obsidian");
            }
        }
        t.set(2, 5, 6, SOUL_LANTERN);
        t.set(6, 5, 6, SOUL_LANTERN);
        t.set(1, 5, 9, COBWEB);
        t.set(7, 5, 9, COBWEB);
        doorway(t, 4, 10, "south", cryptJigsaw(MODID + ":crypt_entrance", MODID + ":crypt_hub", CRYPT + "floor_hub", 0));
        return t;
    }

    /**
     * Площадка спуска (этажи 2–5): сюда спускается лестница с этажа выше. Якорь в центре — низ шахты,
     * колонна к северу от него держит лестницу. Выход в центральный зал этажа на юг.
     */
    static Template floorLanding() {
        Random r = new Random(12);
        Template t = new Template(9, 7, 9);
        room(t, 0, 0, 8, 8, 6, r);
        t.set(4, 1, 4, "minecraft:jigsaw[orientation=up_north]", cryptJigsaw(FLOOR_ANCHOR, FLOOR_ANCHOR, "minecraft:empty", 0));
        for (int y = 1; y <= 5; y++) t.set(4, y, 3, "minecraft:chiseled_stone_bricks");
        for (int[] p : new int[][]{{1, 1}, {7, 1}, {1, 7}, {7, 7}}) t.fill(p[0], 1, p[1], p[0], 5, p[1], "minecraft:chiseled_stone_bricks");
        t.set(2, 5, 6, SOUL_LANTERN);
        t.set(6, 5, 6, SOUL_LANTERN);
        t.set(2, 1, 1, "minecraft:soul_campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
        t.set(6, 1, 1, "minecraft:soul_campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
        t.set(1, 5, 4, COBWEB);
        doorway(t, 4, 8, "south", cryptJigsaw(MODID + ":crypt_entrance", MODID + ":crypt_hub", CRYPT + "floor_hub", 0));
        return t;
    }

    /**
     * Зал спуска: проём в полу посередине (лестницу вниз прорубает игра), вокруг — плиты резного камня,
     * четыре столба с фонарями и страж. Вход только с севера, из центрального зала.
     */
    static Template descentRoom() {
        Random r = new Random(13);
        Template t = new Template(9, 7, 9);
        room(t, 0, 0, 8, 8, 6, r);
        for (int x = 3; x <= 5; x++) for (int z = 3; z <= 5; z++) t.set(x, 0, z, "minecraft:chiseled_stone_bricks");
        for (int[] p : new int[][]{{2, 2}, {6, 2}, {2, 6}, {6, 6}}) {
            t.set(p[0], 1, p[1], "minecraft:chiseled_stone_bricks");
            t.set(p[0], 2, p[1], "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
            t.set(p[0], 5, p[1], SOUL_LANTERN);
        }
        t.set(1, 5, 1, COBWEB);
        t.set(7, 5, 7, COBWEB);
        t.set(1, 1, 7, "minecraft:bone_block[axis=y]");
        t.set(7, 1, 7, "minecraft:skeleton_skull[powered=false,rotation=8]");
        guard(t, 4, 6, "minecraft:zombie");
        doorway(t, 4, 0, "north", cryptJigsaw(MODID + ":descent_room", MODID + ":descent_door", "minecraft:empty", 0));
        return t;
    }

    /** Радиус круглого зала арены, его центр в шаблоне и высота свода. */
    static final int ARENA_R = 24, ARENA_C = 26, ARENA_H = 27;

    /**
     * Арена босса — шестой этаж. Круглый зал радиусом 24 и высотой 27 с кольцом колонн, узором на полу,
     * светящимся сводом и боссом посередине. С юга к нему примыкает зал спуска: сюда приходит лестница,
     * здесь же запечатанные врата домой (в 4 блоках к югу от якоря), которые открываются после победы.
     */
    static Template bossArena() {
        Random r = new Random(14);
        int c = ARENA_C;
        int size = c * 2 + 1;
        int ante = size; // первый ряд зала спуска по z
        Template t = new Template(size, ARENA_H + 1, size + 11);
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                double d = Math.sqrt((x - c) * (x - c) + (z - c) * (z - c));
                if (d >= ARENA_R + 2) continue;
                // Пол: чернокаменный кирпич, два кольца плачущего обсидиана, резной круг под боссом и лучи от центра.
                String floor = r.nextDouble() < 0.85 ? "minecraft:polished_blackstone_bricks" : "minecraft:cracked_polished_blackstone_bricks";
                if ((d >= 5.5 && d < 6.5) || (d >= 15.5 && d < 16.5)) floor = "minecraft:crying_obsidian";
                else if (d < 3.5) floor = "minecraft:chiseled_polished_blackstone";
                else if (d < 15.5 && (x == c || z == c)) floor = "minecraft:polished_blackstone";
                t.set(x, 0, z, floor);
                if (d >= ARENA_R) {
                    // Стена с рёбрами через каждые 15° и поясами через 7 блоков.
                    double angle = Math.toDegrees(Math.atan2(z - c, x - c)) + 360;
                    boolean rib = (angle % 15) < 4;
                    for (int y = 1; y < ARENA_H; y++) {
                        t.set(x, y, z, rib ? "minecraft:chiseled_polished_blackstone" : y % 7 == 0 ? "minecraft:polished_blackstone" : blackBrick(r));
                    }
                }
                // Свод: светящиеся блоки решёткой через 5.
                boolean light = d < ARENA_R - 1 && (x - c) % 5 == 0 && (z - c) % 5 == 0;
                t.set(x, ARENA_H, z, light ? "minecraft:glowstone" : "minecraft:polished_blackstone");
            }
        }
        // Восемь колонн 3×3 по кольцу радиусом 17, у каждой фонарь и костёр.
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2 * i / 8 + Math.PI / 8;
            int px = c + (int) Math.round(Math.cos(a) * 17), pz = c + (int) Math.round(Math.sin(a) * 17);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = 1; y < ARENA_H; y++) {
                        boolean cap = y <= 2 || y >= ARENA_H - 2;
                        t.set(px + dx, y, pz + dz, cap ? "minecraft:chiseled_polished_blackstone" : "minecraft:polished_blackstone");
                    }
                }
            }
            t.set(px, 1, pz + (pz > c ? -2 : 2), "minecraft:soul_lantern[hanging=false,waterlogged=false]");
            t.set(px + (px > c ? -2 : 2), 1, pz, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        }
        // Висячие фонари двумя кольцами.
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            t.set(c + (int) Math.round(Math.cos(a) * 10), ARENA_H - 1, c + (int) Math.round(Math.sin(a) * 10), SOUL_LANTERN);
            t.set(c + (int) Math.round(Math.cos(a + 0.26) * 21), ARENA_H - 1, c + (int) Math.round(Math.sin(a + 0.26) * 21), SOUL_LANTERN);
        }
        // Трон босса у северной стены и сундуки с сокровищами по бокам.
        t.fill(c - 2, 1, 3, c + 2, 1, 4, "minecraft:gilded_blackstone");
        t.fill(c - 1, 2, 3, c + 1, 5, 3, "minecraft:gilded_blackstone");
        t.set(c, 2, 4, "minecraft:polished_blackstone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]");
        chest(t, c - 4, 1, 4, "south", "crypt_treasure");
        chest(t, c + 4, 1, 4, "south", "crypt_treasure");
        for (int[] p : new int[][]{{c - 6, 5}, {c + 6, 5}, {c - 5, 3}, {c + 5, 3}}) t.set(p[0], 1, p[1], "minecraft:bone_block[axis=y]");
        t.entities.add(map("blockPos", List.of(c, 1, c), "pos", List.of(c + 0.5, 1.0, c + 0.5),
                "nbt", map("id", MODID + ":crypt_king", "PersistenceRequired", (byte) 1)));

        // Проход из зала спуска в арену: 5 в ширину, 5 в высоту, сквозь стену зала.
        for (int z = c + ARENA_R; z < ante; z++) {
            for (int x = c - 3; x <= c + 3; x++) {
                boolean wall = x == c - 3 || x == c + 3;
                t.set(x, 0, z, "minecraft:polished_blackstone");
                t.set(x, 6, z, blackBrick(r));
                for (int y = 1; y <= 5; y++) t.set(x, y, z, wall ? blackBrick(r) : AIR);
            }
        }
        // Зал спуска 11×11 (z = ante..ante+10): якорь шахты в центре, колонна-опора лестницы к северу от него,
        // запечатанные врата домой в 4 блоках к югу.
        int az = ante + 5;
        for (int x = c - 5; x <= c + 5; x++) {
            for (int z = ante; z <= ante + 10; z++) {
                boolean edge = x == c - 5 || x == c + 5 || z == ante || z == ante + 10;
                t.set(x, 0, z, r.nextDouble() < 0.8 ? "minecraft:stone_bricks" : "minecraft:cracked_stone_bricks");
                t.set(x, 7, z, brick(r));
                for (int y = 1; y <= 6; y++) t.set(x, y, z, edge ? brick(r) : AIR);
            }
        }
        for (int x = c - 2; x <= c + 2; x++) for (int y = 1; y <= 5; y++) t.set(x, y, ante, AIR);
        t.set(c, 1, az, "minecraft:jigsaw[orientation=up_north]", cryptJigsaw(FLOOR_ANCHOR, FLOOR_ANCHOR, "minecraft:empty", 0));
        for (int y = 1; y <= 6; y++) t.set(c, y, az - 1, "minecraft:chiseled_stone_bricks");
        for (int x = c - 2; x <= c + 2; x++) {
            for (int y = 1; y <= 5; y++) {
                boolean frame = x == c - 2 || x == c + 2 || y == 5;
                t.set(x, y, az + 4, frame ? "minecraft:obsidian" : "minecraft:crying_obsidian");
            }
        }
        for (int[] p : new int[][]{{c - 3, az - 3}, {c + 3, az - 3}, {c - 3, az + 2}, {c + 3, az + 2}}) t.set(p[0], 6, p[1], SOUL_LANTERN);
        t.set(c - 4, 1, az + 4, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        t.set(c + 4, 1, az + 4, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        return t;
    }

    /** Заменяет блоки, мобов, сундуки и пулы склепа на тему данжа во всём NBT шаблона. */
    static Object substitute(Object value, Map<String, String> substitutions, String fromPrefix, String toPrefix) {
        if (value instanceof String text) {
            if (substitutions.containsKey(text)) return substitutions.get(text);
            return text.startsWith(fromPrefix) ? toPrefix + text.substring(fromPrefix.length()) : text;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put((String) entry.getKey(), substitute(entry.getValue(), substitutions, fromPrefix, toPrefix));
            }
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object element : list) copy.add(substitute(element, substitutions, fromPrefix, toPrefix));
            return copy;
        }
        return value;
    }

    // ================================================================ площади с вратами

    static int generateTownPlazas(Path resources) throws IOException {
        Path structures = resources.resolve("data/" + MODID + "/structure/village");
        for (Biome biome : BIOMES) {
            write(structures.resolve(biome.name() + "/gate_plaza.nbt"), townPlaza(biome).toNbt());
        }
        return BIOMES.size();
    }

    /** Материалы зала гильдии по биому деревни. */
    record HallStyle(String stone, String stoneStairs, String stoneSlab, String stoneWall, String accent, String plinth,
                     String timber, String plaster, String roofStairs, String roofSlab, String roofBlock,
                     String wood, String door, String ground, String path) {}

    static HallStyle hallStyle(Biome b) {
        return switch (b.name()) {
            case "desert" -> new HallStyle("minecraft:cut_sandstone", "minecraft:smooth_sandstone_stairs", "minecraft:smooth_sandstone_slab",
                    "minecraft:sandstone_wall", "minecraft:chiseled_sandstone", "minecraft:smooth_sandstone",
                    "minecraft:stripped_jungle_log", "minecraft:smooth_sandstone", "minecraft:smooth_red_sandstone_stairs",
                    "minecraft:smooth_red_sandstone_slab", "minecraft:smooth_red_sandstone", "jungle", "minecraft:jungle_door",
                    "minecraft:cut_sandstone", "minecraft:smooth_sandstone");
            case "savanna" -> new HallStyle("minecraft:mud_bricks", "minecraft:mud_brick_stairs", "minecraft:mud_brick_slab",
                    "minecraft:mud_brick_wall", "minecraft:chiseled_stone_bricks", "minecraft:terracotta",
                    "minecraft:acacia_log", "minecraft:white_terracotta", "minecraft:brick_stairs", "minecraft:brick_slab",
                    "minecraft:bricks", "acacia", "minecraft:acacia_door", "minecraft:dirt_path", "minecraft:coarse_dirt");
            case "snowy" -> new HallStyle("minecraft:stone_bricks", "minecraft:stone_brick_stairs", "minecraft:stone_brick_slab",
                    "minecraft:stone_brick_wall", "minecraft:chiseled_stone_bricks", "minecraft:polished_diorite",
                    "minecraft:spruce_log", "minecraft:calcite", "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_slab",
                    "minecraft:deepslate_tiles", "spruce", "minecraft:spruce_door", "minecraft:snow_block", "minecraft:packed_ice");
            case "taiga" -> new HallStyle("minecraft:stone_bricks", "minecraft:stone_brick_stairs", "minecraft:stone_brick_slab",
                    "minecraft:mossy_stone_brick_wall", "minecraft:chiseled_stone_bricks", "minecraft:mossy_cobblestone",
                    "minecraft:spruce_log", "minecraft:calcite", "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_slab",
                    "minecraft:deepslate_tiles", "spruce", "minecraft:spruce_door", "minecraft:dirt_path", "minecraft:mossy_cobblestone");
            default -> new HallStyle("minecraft:stone_bricks", "minecraft:stone_brick_stairs", "minecraft:stone_brick_slab",
                    "minecraft:stone_brick_wall", "minecraft:chiseled_stone_bricks", "minecraft:polished_andesite",
                    "minecraft:dark_oak_log", "minecraft:calcite", "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_slab",
                    "minecraft:deepslate_tiles", "dark_oak", "minecraft:dark_oak_door", "minecraft:dirt_path", "minecraft:cobblestone");
        };
    }

    // Зал гильдии: размеры шаблона и опорные линии (x — поперёк зала, z — от задней стены к фасаду).
    static final int GH = 35, GH_TOP = 46, GX = 17;
    static final int HX1 = 7, HX2 = 27, HZ1 = 5, HZ2 = 26;
    /** Стены первого, каменного этажа — до этой высоты; выше — фахверк, вынесенный на блок наружу. */
    static final int STONE_TOP = 8, TIMBER_TOP = 14, EAVES = 15;
    static final int TOWER_X1 = 14, TOWER_X2 = 20, TOWER_Z2 = 32, TOWER_TOP = 33;
    /** Проём врат: плоскость z, низ проёма y; алтарь в двух блоках перед ним. */
    static final int GATE_Z = 8, GATE_Y = 2, ALTAR_Z = GATE_Z + 2;

    static String st(String stairs, String facing, boolean top) {
        return stairs + "[facing=" + facing + ",half=" + (top ? "top" : "bottom") + ",shape=straight,waterlogged=false]";
    }

    static String slab(String slab, boolean top) {
        return slab + "[type=" + (top ? "top" : "bottom") + ",waterlogged=false]";
    }

    static String log(String log, char axis) {
        return log + "[axis=" + axis + "]";
    }

    static String wall(String wall, boolean alongX, boolean alongZ) {
        String x = alongX ? "low" : "none", z = alongZ ? "low" : "none";
        return wall + "[east=" + x + ",north=" + z + ",south=" + z + ",up=true,waterlogged=false,west=" + x + "]";
    }

    static void door(Template t, int x, int y, int z, String door, String facing, String hinge) {
        t.set(x, y, z, door + "[facing=" + facing + ",half=lower,hinge=" + hinge + ",open=false,powered=false]");
        t.set(x, y + 1, z, door + "[facing=" + facing + ",half=upper,hinge=" + hinge + ",open=false,powered=false]");
    }

    static void chandelier(Template t, int x, int z, int top, int bottom) {
        for (int y = bottom + 1; y <= top; y++) t.set(x, y, z, "minecraft:chain[axis=y,waterlogged=false]");
        t.set(x, bottom, z, "minecraft:lantern[hanging=true,waterlogged=false]");
    }

    /**
     * Центр деревни — зал гильдии искателей приключений, собранный по мотивам средневековых гильдейских залов:
     * колокольня над входом как у Белфорта в Брюгге, ступенчатые щипцы как у гильдейских домов на Граслее в Генте,
     * выступающий фахверковый второй этаж и угловые башенки как у ратуши Михельштадта, стропила с подбалками
     * как в Вестминстер-холле. Внутри — помост с вратами в данжи, доски заданий, стойка распорядителя
     * с досками заказов и угол глашатая с доской наказов на боссов. Улицы подходят с четырёх сторон.
     */
    static Template townPlaza(Biome b) {
        HallStyle s = hallStyle(b);
        Template t = new Template(GH, GH_TOP, GH);
        Random random = new Random(b.name().hashCode());
        String fence = "minecraft:" + s.wood() + "_fence";

        // Двор: мостовая вокруг зала, дорожки к четырём улицам, над ним чистый воздух.
        for (int x = 0; x < GH; x++) {
            for (int z = 0; z < GH; z++) {
                boolean road = Math.abs(x - GX) <= 1 || Math.abs(z - 17) <= 1;
                t.set(x, 0, z, road ? s.path() : (random.nextInt(4) == 0 ? s.plinth() : s.ground()));
                for (int y = 1; y <= 8; y++) t.set(x, y, z, AIR);
            }
        }

        // ---------------------------------------------------------------- первый этаж: камень
        for (int x = HX1; x <= HX2; x++) {
            for (int z = HZ1; z <= HZ2; z++) {
                boolean edge = x == HX1 || x == HX2 || z == HZ1 || z == HZ2;
                t.set(x, 0, z, edge ? s.plinth() : floorTile(x, z));
                for (int y = 1; y <= STONE_TOP; y++) {
                    if (!edge) t.set(x, y, z, AIR);
                    else t.set(x, y, z, (y == 1 || y == STONE_TOP) ? s.plinth() : s.stone());
                }
            }
        }
        // Высокие стрельчатые окна в боковых стенах и контрфорсы между ними.
        for (int z : new int[]{9, 13, 21, 24}) {
            for (int x : new int[]{HX1, HX2}) {
                for (int y = 2; y <= 6; y++) t.set(x, y, z, pane("glass_pane", false));
                t.set(x, 7, z, s.accent());
            }
        }
        for (int z : new int[]{7, 11, 15, 19, 23}) {
            buttress(t, s, HX1 - 1, z, "west");
            buttress(t, s, HX2 + 1, z, "east");
        }
        // Боковые двери к восточной и западной улицам, факелы по сторонам.
        door(t, HX1, 1, 17, s.door(), "east", "left");
        door(t, HX2, 1, 17, s.door(), "west", "right");
        for (int z : new int[]{16, 18}) {
            t.set(HX1 - 1, 3, z, "minecraft:wall_torch[facing=west]");
            t.set(HX2 + 1, 3, z, "minecraft:wall_torch[facing=east]");
        }
        t.set(HX1, 3, 17, s.accent());
        t.set(HX2, 3, 17, s.accent());
        // Окна задней стены по сторонам помоста и фасада по сторонам колокольни.
        for (int x : new int[]{10, 24}) {
            for (int y = 2; y <= 6; y++) {
                t.set(x, y, HZ1, pane("glass_pane", true));
                t.set(x, y, HZ2, pane("glass_pane", true));
            }
            t.set(x, 7, HZ1, s.accent());
            t.set(x, 7, HZ2, s.accent());
        }

        // ---------------------------------------------------------------- второй этаж: фахверк с выносом
        for (int y = STONE_TOP + 1; y <= TIMBER_TOP; y++) {
            for (int z = HZ1; z <= HZ2; z++) {
                for (int x : new int[]{HX1 - 1, HX2 + 1}) t.set(x, y, z, timberWall(s, y, z));
                // Под выносом — вход в зал: старые стены второго этажа убраны.
                t.set(HX1, y, z, z == HZ1 || z == HZ2 ? s.stone() : AIR);
                t.set(HX2, y, z, z == HZ1 || z == HZ2 ? s.stone() : AIR);
            }
            for (int x = HX1; x <= HX2; x++) {
                t.set(x, y, HZ1, s.stone());
                t.set(x, y, HZ2, s.stone());
            }
        }
        // Окна второго этажа с распахнутыми ставнями.
        for (int z : new int[]{9, 13, 17, 21}) {
            for (int[] side : new int[][]{{HX1 - 1, -1}, {HX2 + 1, 1}}) {
                int x = side[0];
                String out = side[1] < 0 ? "west" : "east";
                for (int y = 11; y <= 12; y++) {
                    t.set(x, y, z, pane("glass_pane", false));
                    t.set(x + side[1], y, z - 1, "minecraft:" + s.wood() + "_trapdoor[facing=north,half=bottom,open=true,powered=false,waterlogged=false]");
                    t.set(x + side[1], y, z + 1, "minecraft:" + s.wood() + "_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]");
                }
                t.set(x + side[1], 10, z, st(s.stoneStairs(), out.equals("west") ? "east" : "west", true));
            }
        }
        // Внутренняя сторона выноса: пол-балка над первым этажом.
        for (int z = HZ1 + 1; z < HZ2; z++) {
            t.set(HX1, STONE_TOP + 1, z, log(s.timber(), 'z'));
            t.set(HX2, STONE_TOP + 1, z, log(s.timber(), 'z'));
        }

        roof(t, s);
        gable(t, s, HZ1, false);
        gable(t, s, HZ2, true);
        roseWindow(t, s);
        trusses(t, s);
        for (int[] c : new int[][]{{HX1 - 1, HZ1}, {HX2 + 1, HZ1}, {HX1 - 1, HZ2}, {HX2 + 1, HZ2}}) turret(t, s, c[0], c[1]);
        belfry(t, s);
        gateDais(t, s);
        hallInterior(t, b, s, fence);

        // Двор: фонари, колокол сбора деревни, бочки и сено у входа.
        for (int[] p : new int[][]{{2, 2}, {32, 2}, {2, 32}, {32, 32}, {12, 33}, {22, 33}}) lampPost(t, fence, p[0], p[1]);
        t.set(10, 1, 32, "minecraft:bell[attachment=floor,facing=east,powered=false]");
        t.set(10, 0, 32, s.plinth());
        t.set(25, 1, 31, "minecraft:barrel[facing=up,open=false]");
        t.set(26, 1, 31, "minecraft:hay_block[axis=y]");
        t.set(26, 2, 31, "minecraft:barrel[facing=up,open=false]");
        t.set(26, 1, 32, "minecraft:barrel[facing=north,open=false]");

        Map<String, Object> street = map("id", "minecraft:jigsaw", "name", "minecraft:street", "target", "minecraft:street",
                "pool", "minecraft:village/" + b.name() + "/streets", "final_state", "minecraft:structure_void", "joint", "aligned");
        t.set(GX, 1, 0, "minecraft:jigsaw[orientation=north_up]", street);
        t.set(GX, 1, GH - 1, "minecraft:jigsaw[orientation=south_up]", street);
        t.set(0, 1, 17, "minecraft:jigsaw[orientation=west_up]", street);
        t.set(GH - 1, 1, 17, "minecraft:jigsaw[orientation=east_up]", street);

        t.set(30, 0, 25, "minecraft:jigsaw[orientation=up_north]", jigsaw("minecraft:bottom", "minecraft:village/common/iron_golem", s.ground(), "rollable"));
        t.set(4, 0, 10, "minecraft:jigsaw[orientation=up_north]", jigsaw("minecraft:bottom", "minecraft:village/common/cats", s.ground(), "rollable"));
        t.set(4, 0, 25, "minecraft:jigsaw[orientation=up_north]", jigsaw("minecraft:bottom", "minecraft:village/" + b.name() + "/villagers", s.ground(), "rollable"));
        t.set(30, 0, 10, "minecraft:jigsaw[orientation=up_north]", jigsaw("minecraft:bottom", "minecraft:village/" + b.name() + "/villagers", s.ground(), "rollable"));
        return t;
    }

    /** Пол зала: доски с каменной окантовкой, тёмная дорожка от дверей к помосту. */
    static String floorTile(int x, int z) {
        if (Math.abs(x - GX) <= 1) return (z % 2 == 0) == (x == GX) ? "minecraft:polished_deepslate" : "minecraft:polished_andesite";
        if (x == HX1 + 1 || x == HX2 - 1 || z == HZ1 + 1 || z == HZ2 - 1) return "minecraft:polished_andesite";
        return "minecraft:spruce_planks";
    }

    /** Фахверк: стойки через блок, лежни по низу и верху, косые подкосы, белёные филёнки. */
    static String timberWall(HallStyle s, int y, int z) {
        if (y == STONE_TOP + 1 || y == TIMBER_TOP) return log(s.timber(), 'z');
        if (z % 4 == 1 || z == HZ1 || z == HZ2) return log(s.timber(), 'y');
        int k = Math.floorMod(z, 4);
        // Подкосы «косым крестом» в каждой второй филёнке.
        if (y == 10 && (k == 2 || k == 0)) return st(s.wood().equals("dark_oak") ? "minecraft:dark_oak_stairs" : "minecraft:" + s.wood() + "_stairs",
                k == 2 ? "north" : "south", true);
        if (y == 13 && (k == 2 || k == 0)) return st(s.wood().equals("dark_oak") ? "minecraft:dark_oak_stairs" : "minecraft:" + s.wood() + "_stairs",
                k == 2 ? "south" : "north", false);
        return s.plaster();
    }

    static void buttress(Template t, HallStyle s, int x, int z, String out) {
        for (int y = 1; y <= 6; y++) t.set(x, y, z, y == 1 ? s.plinth() : s.stone());
        t.set(x, 7, z, st(s.stoneStairs(), out.equals("west") ? "east" : "west", false));
        // Подкос-кронштейн под выносом второго этажа.
        t.set(x, STONE_TOP, z, st(s.stoneStairs(), out.equals("west") ? "east" : "west", true));
    }

    /** Крутая двускатная крыша вдоль зала: черепица, подшивка изнутри перевёрнутыми ступенями, конёк. */
    static void roof(Template t, HallStyle s) {
        int left = HX1 - 2, right = HX2 + 2;
        for (int z = HZ1 + 1; z < HZ2; z++) {
            for (int k = 0; left + k <= right - k; k++) {
                int y = EAVES + k, xl = left + k, xr = right - k;
                if (xl == xr) {
                    t.set(xl, y, z, s.roofBlock());
                    t.set(xl, y + 1, z, slab(s.roofSlab(), false));
                    continue;
                }
                t.set(xl, y, z, st(s.roofStairs(), "east", false));
                t.set(xr, y, z, st(s.roofStairs(), "west", false));
                if (xl + 1 < xr - 1) {
                    t.set(xl + 1, y, z, st(s.roofStairs(), "west", true));
                    t.set(xr - 1, y, z, st(s.roofStairs(), "east", true));
                } else if (xl + 1 == xr - 1) {
                    t.set(xl + 1, y, z, s.roofBlock());
                }
                for (int x = xl + 2; x <= xr - 2; x++) t.set(x, y, z, AIR);
            }
            // Под карнизом: воздух над стенами второго этажа внутри.
            for (int x = HX1; x <= HX2; x++) for (int y = TIMBER_TOP + 1; y < EAVES; y++) t.set(x, y, z, AIR);
        }
        // Слуховые окна на обоих скатах.
        for (int z : new int[]{10, 15, 20}) {
            dormer(t, s, HX1 + 1, z, "west");
            dormer(t, s, HX2 - 1, z, "east");
        }
    }

    /** Слуховое окно: щипчик из бревна и штукатурки с окошком, свой маленький скат. */
    static void dormer(Template t, HallStyle s, int x, int z, String out) {
        int k = out.equals("west") ? x - (HX1 - 2) : (HX2 + 2) - x;
        int y = EAVES + k;
        int dx = out.equals("west") ? -1 : 1;
        for (int dz = -1; dz <= 1; dz++) {
            t.set(x, y, z + dz, dz == 0 ? pane("glass_pane", false) : log(s.timber(), 'y'));
            t.set(x, y + 1, z + dz, dz == 0 ? s.plaster() : log(s.timber(), 'y'));
        }
        t.set(x - dx, y, z, AIR);
        t.set(x - dx, y + 1, z, AIR);
        t.set(x, y + 2, z - 1, st(s.roofStairs(), "south", false));
        t.set(x, y + 2, z + 1, st(s.roofStairs(), "north", false));
        t.set(x, y + 2, z, s.roofBlock());
        t.set(x, y + 3, z, slab(s.roofSlab(), false));
        t.set(x - dx, y + 2, z, s.roofBlock());
        t.set(x - dx, y + 3, z, slab(s.roofSlab(), false));
        t.set(x + dx, y, z, st(s.stoneStairs(), out.equals("west") ? "east" : "west", false));
    }

    /** Каменный щипец со ступенчатым («вороньим») верхом, как у гильдейских домов Гента; на ступенях — каменные шишки. */
    static void gable(Template t, HallStyle s, int z, boolean front) {
        int left = HX1 - 2, right = HX2 + 2;
        for (int x = HX1; x <= HX2; x++) {
            int fromEdge = Math.min(x - left, right - x);
            int top = EAVES + 2 * (fromEdge / 2) + 1;
            for (int y = TIMBER_TOP + 1; y <= top; y++) t.set(x, y, z, s.stone());
            boolean stepEdge = fromEdge % 2 == 1 || x == GX;
            t.set(x, top + 1, z, stepEdge ? wall(s.stoneWall(), false, false) : slab(s.stoneSlab(), false));
            // Пояс-карниз по щипцу.
            t.set(x, TIMBER_TOP + 1, z + (front ? 1 : -1), st(s.stoneStairs(), front ? "north" : "south", true));
        }
        int peak = EAVES + 2 * ((GX - left) / 2) + 2;
        t.set(GX, peak, z, s.accent());
        t.set(GX, peak + 1, z, wall(s.stoneWall(), false, false));
        t.set(GX, peak + 2, z, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
    }

    /** Круглое окно-роза в заднем щипце, над вратами: фиолетовое стекло в каменном кольце. */
    static void roseWindow(Template t, HallStyle s) {
        int cy = 20;
        for (int x = GX - 4; x <= GX + 4; x++) {
            for (int y = cy - 4; y <= cy + 4; y++) {
                double d = Math.hypot(x - GX, y - cy);
                if (d <= 2.6) {
                    boolean spoke = x == GX || y == cy;
                    t.set(x, y, HZ1, spoke && d > 0.5 ? "minecraft:magenta_stained_glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]"
                            : "minecraft:purple_stained_glass_pane[east=true,north=false,south=false,waterlogged=false,west=true]");
                } else if (d <= 3.6) {
                    t.set(x, y, HZ1, s.accent());
                }
            }
        }
        t.set(GX, cy, HZ1, "minecraft:crying_obsidian");
    }

    /** Стропила с подбалками: выносные балки на стенах, подкосы, затяжка и бабка до конька; с затяжек свисают люстры. */
    static void trusses(Template t, HallStyle s) {
        String beam = s.timber().replace("stripped_", "");
        String stairs = "minecraft:" + s.wood() + "_stairs";
        for (int z : new int[]{8, 12, 16, 20, 24}) {
            for (int[] side : new int[][]{{HX1, 1}, {HX2, -1}}) {
                int x = side[0], d = side[1];
                String inward = d > 0 ? "east" : "west", outward = d > 0 ? "west" : "east";
                for (int y = STONE_TOP + 1; y <= EAVES; y++) t.set(x, y, z, log(beam, 'y'));
                t.set(x + d, EAVES - 1, z, log(beam, 'x'));
                t.set(x + 2 * d, EAVES - 1, z, st(stairs, outward, true));
                t.set(x + d, EAVES - 2, z, st(stairs, outward, true));
                for (int k = 1; k <= 6; k++) t.set(x + d * (1 + k), EAVES - 1 + k, z, st(stairs, inward, true));
                t.set(x + d, EAVES - 3, z, "minecraft:lantern[hanging=true,waterlogged=false]");
            }
            int collar = EAVES + 6;
            for (int x = HX1 - 2 + 7; x <= HX2 + 2 - 7; x++) t.set(x, collar, z, log(beam, 'x'));
            for (int y = collar + 1; y <= EAVES + 10; y++) t.set(GX, y, z, log(beam, 'y'));
            if (z != 8) chandelier(t, GX, z, collar - 1, 11);
            if (z == 24) continue;
            chandelier(t, GX - 5, z + 2, collar - 1, 13);
            chandelier(t, GX + 5, z + 2, collar - 1, 13);
        }
    }

    /** Угловая башенка на кронштейнах: круглая (по возможности) стена со шпилем. */
    static void turret(Template t, HallStyle s, int cx, int cz) {
        int base = STONE_TOP + 1, top = TIMBER_TOP + 3;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = cx + dx, z = cz + dz;
                if (z < 0 || z >= GH) continue;
                boolean hallWall = x >= HX1 && x <= HX2 && z >= HZ1 && z <= HZ2;
                if (!hallWall) {
                    t.set(x, base - 1, z, dx == 0 && dz == 0 ? s.stone()
                            : st(s.stoneStairs(), dx != 0 ? (dx < 0 ? "east" : "west") : (dz < 0 ? "south" : "north"), true));
                }
                for (int y = base; y <= top; y++) {
                    boolean window = (y == base + 3 || y == base + 4) && (dx == 0 || dz == 0) && !(dx == 0 && dz == 0);
                    // Сердцевина сплошная: в тёмной пустоте заводились бы мобы.
                    t.set(x, y, z, window ? "minecraft:glass" : (y == base || y == top ? s.accent() : s.stone()));
                }
            }
        }
        // Конический шпиль.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int x = cx + dx, z = cz + dz;
                if (z < 0 || z >= GH || (Math.abs(dx) == 2 && Math.abs(dz) == 2)) continue;
                boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                String facing = Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? "east" : "west") : (dz < 0 ? "south" : "north");
                t.set(x, top + 1, z, ring ? st(s.roofStairs(), facing, false) : s.roofBlock());
                if (!ring) {
                    boolean inner = dx == 0 && dz == 0;
                    t.set(x, top + 2, z, inner ? s.roofBlock() : st(s.roofStairs(), facing, false));
                }
            }
        }
        for (int y = top + 3; y <= top + 4; y++) t.set(cx, y, cz, s.roofBlock());
        t.set(cx, top + 5, cz, wall(s.stoneWall(), false, false));
        t.set(cx, top + 6, cz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
    }

    /** Колокольня над входом (по мотивам Белфорта в Брюгге): арка-проход, щели окон, звонница с колоколом, зубцы и шпиль. */
    static void belfry(Template t, HallStyle s) {
        int z1 = HZ2, z2 = TOWER_Z2;
        for (int x = TOWER_X1; x <= TOWER_X2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == TOWER_X1 || x == TOWER_X2 || z == z1 || z == z2;
                boolean corner = (x == TOWER_X1 || x == TOWER_X2) && (z == z1 || z == z2);
                t.set(x, 0, z, s.plinth());
                for (int y = 1; y <= TOWER_TOP; y++) {
                    String block;
                    if (y == 1) block = s.plinth();
                    else if (corner) block = y % 6 == 0 ? s.accent() : s.plinth();
                    else if (y % 9 == 0) block = s.plinth();
                    else block = s.stone();
                    // Внутри — сплошная кладка (никаких тёмных пустот), кроме прохода и звонницы.
                    t.set(x, y, z, edge || y <= 25 ? block : AIR);
                }
            }
        }
        // Проход под башней в зал: стрельчатая арка, по бокам каменные скамьи.
        for (int z = z1; z <= z2; z++) {
            for (int x = GX - 1; x <= GX + 1; x++) {
                for (int y = 1; y <= 4; y++) t.set(x, y, z, AIR);
                t.set(x, 0, z, (x + z) % 2 == 0 ? "minecraft:polished_deepslate" : "minecraft:polished_andesite");
            }
            t.set(GX, 5, z, AIR);
            t.set(GX - 1, 5, z, st(s.stoneStairs(), "east", true));
            t.set(GX + 1, 5, z, st(s.stoneStairs(), "west", true));
            if (z > z1 && z < z2 && z % 2 == 0) {
                t.set(GX - 2, 3, z, "minecraft:wall_torch[facing=east]");
                t.set(GX + 2, 3, z, "minecraft:wall_torch[facing=west]");
            }
        }
        t.set(GX, 6, z2, s.accent());
        t.set(GX - 2, 5, z2, s.accent());
        t.set(GX + 2, 5, z2, s.accent());
        // Решётка-герса, поднятая наполовину, и фонари у входа.
        for (int x = GX - 1; x <= GX + 1; x++) t.set(x, 4, z2 - 1, "minecraft:iron_bars[east=" + (x < GX + 1) + ",north=false,south=false,waterlogged=false,west=" + (x > GX - 1) + "]");
        t.set(GX - 3, 3, z2 + 1, "minecraft:lantern[hanging=false,waterlogged=false]");
        t.set(GX + 3, 3, z2 + 1, "minecraft:lantern[hanging=false,waterlogged=false]");
        t.set(GX - 3, 2, z2 + 1, wall(s.stoneWall(), false, false));
        t.set(GX + 3, 2, z2 + 1, wall(s.stoneWall(), false, false));
        t.set(GX - 3, 1, z2 + 1, s.accent());
        t.set(GX + 3, 1, z2 + 1, s.accent());
        // Дверь из прохода в зал: две створки и каменный столб-«трюмо» между ними, тимпан над ними.
        door(t, GX - 1, 1, HZ2, s.door(), "north", "left");
        door(t, GX + 1, 1, HZ2, s.door(), "north", "right");
        t.set(GX, 1, HZ2, s.plinth());
        t.set(GX, 2, HZ2, wall(s.stoneWall(), false, false));
        t.set(GX, 3, HZ2, s.accent());
        for (int x = GX - 1; x <= GX + 1; x++) t.set(x, 4, HZ2, s.stone());
        t.set(GX, 5, HZ2, s.stone());
        // Гербовые знамёна гильдии на фасаде башни и узкие окна-щели.
        t.set(GX - 2, 12, z2 + 1, "minecraft:orange_wall_banner[facing=south]");
        t.set(GX + 2, 12, z2 + 1, "minecraft:orange_wall_banner[facing=south]");
        for (int y : new int[]{10, 11, 16, 17, 21, 22}) {
            t.set(GX, y, z2, "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]");
            t.set(TOWER_X1, y, 29, "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]");
            t.set(TOWER_X2, y, 29, "minecraft:iron_bars[east=false,north=false,south=false,waterlogged=false,west=false]");
        }
        // Звонница: по стрельчатой арке на каждой стороне, колокол на балке, фонари.
        for (int y = 26; y <= 31; y++) {
            for (int d = -1; d <= 1; d++) {
                boolean open = y <= 30 || d == 0;
                if (!open) continue;
                t.set(GX + d, y, z2, AIR);
                t.set(GX + d, y, z1, AIR);
                t.set(TOWER_X1, y, 29 + d, AIR);
                t.set(TOWER_X2, y, 29 + d, AIR);
            }
        }
        for (int d : new int[]{-1, 1}) {
            String toCenter = d < 0 ? "east" : "west";
            String toCenterZ = d < 0 ? "south" : "north";
            t.set(GX + d, 31, z2, st(s.stoneStairs(), toCenter, true));
            t.set(GX + d, 31, z1, st(s.stoneStairs(), toCenter, true));
            t.set(TOWER_X1, 31, 29 + d, st(s.stoneStairs(), toCenterZ, true));
            t.set(TOWER_X2, 31, 29 + d, st(s.stoneStairs(), toCenterZ, true));
        }
        for (int x = TOWER_X1 + 1; x < TOWER_X2; x++) for (int z = z1 + 1; z < z2; z++) t.set(x, 25, z, s.plinth());
        for (int x = TOWER_X1 + 1; x < TOWER_X2; x++) t.set(x, 32, 29, log(s.timber().replace("stripped_", ""), 'x'));
        t.set(GX, 31, 29, "minecraft:bell[attachment=ceiling,facing=north,powered=false]");
        t.set(TOWER_X1 + 1, 26, z1 + 1, "minecraft:lantern[hanging=false,waterlogged=false]");
        t.set(TOWER_X2 - 1, 26, z2 - 1, "minecraft:lantern[hanging=false,waterlogged=false]");
        // Карниз, зубцы с угловыми башенками.
        for (int x = TOWER_X1 - 1; x <= TOWER_X2 + 1; x++) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean ring = x == TOWER_X1 - 1 || x == TOWER_X2 + 1 || z == z1 - 1 || z == z2 + 1;
                if (!ring) continue;
                String facing = x == TOWER_X1 - 1 ? "east" : x == TOWER_X2 + 1 ? "west" : z == z1 - 1 ? "south" : "north";
                t.set(x, TOWER_TOP, z, st(s.stoneStairs(), facing, true));
                boolean corner = (x == TOWER_X1 - 1 || x == TOWER_X2 + 1) && (z == z1 - 1 || z == z2 + 1);
                boolean merlon = corner || (x + z) % 2 == 0;
                t.set(x, TOWER_TOP + 1, z, merlon ? s.stone() : slab(s.stoneSlab(), false));
                if (corner) {
                    t.set(x, TOWER_TOP + 2, z, s.accent());
                    t.set(x, TOWER_TOP + 3, z, wall(s.stoneWall(), false, false));
                    t.set(x, TOWER_TOP + 4, z, "minecraft:lantern[hanging=false,waterlogged=false]");
                }
            }
        }
        for (int x = TOWER_X1; x <= TOWER_X2; x++) for (int z = z1; z <= z2; z++) t.set(x, TOWER_TOP, z, s.stone());
        // Шпиль: уступами 7 → 5 → 3 → 1, золотое навершие.
        int y = TOWER_TOP + 1;
        int[][] tiers = {{3, 1}, {2, 2}, {1, 2}};
        for (int[] tier : tiers) {
            int r = tier[0];
            for (int layer = 0; layer < tier[1]; layer++, y++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        int x = GX + dx, z = 29 + dz;
                        boolean ring = Math.abs(dx) == r || Math.abs(dz) == r;
                        if (!ring) {
                            t.set(x, y, z, s.roofBlock());
                            continue;
                        }
                        if (layer == 0 && tier[1] > 1) {
                            t.set(x, y, z, s.roofBlock());
                            continue;
                        }
                        String facing = Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? "east" : "west") : (dz < 0 ? "south" : "north");
                        t.set(x, y, z, st(s.roofStairs(), facing, false));
                    }
                }
            }
        }
        for (int k = 0; k < 3; k++) t.set(GX, y + k, 29, s.roofBlock());
        t.set(GX, y + 3, 29, "minecraft:gold_block");
        t.set(GX, y + 4, 29, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");
    }

    /**
     * Помост у задней стены с вратами в данжи: стрельчатая арка из полированного чернокамня с позолотой,
     * плачущий обсидиан в замке арки, аметисты и свечи на пилонах, синие жаровни, алтарь врат перед проёмом
     * и доски заданий по краям помоста. Проём 3×4 остаётся пустым — его заливает порталом алтарь.
     */
    static void gateDais(Template t, HallStyle s) {
        int x1 = GX - 6, x2 = GX + 6, z1 = HZ1 + 1, z2 = ALTAR_Z + 1;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z2;
                t.set(x, 1, z, edge ? "minecraft:polished_deepslate" : ((x + z) % 2 == 0 ? "minecraft:polished_blackstone" : "minecraft:polished_deepslate"));
            }
            if (x > x1 && x < x2) t.set(x, 1, z2 + 1, st("minecraft:polished_deepslate_stairs", "north", false));
        }
        t.set(x1, 1, z2, "minecraft:chiseled_deepslate");
        t.set(x2, 1, z2, "minecraft:chiseled_deepslate");
        t.set(x1, 1, z2 + 1, AIR);
        t.set(x2, 1, z2 + 1, AIR);
        for (int x = GX - 1; x <= GX + 1; x++) t.set(x, 1, ALTAR_Z, "minecraft:polished_blackstone_bricks");

        String brick = "minecraft:polished_blackstone_bricks", chiseled = "minecraft:chiseled_polished_blackstone";
        String bStairs = "minecraft:polished_blackstone_brick_stairs";
        int z = GATE_Z, y0 = GATE_Y;
        // Внутренняя рама: колонны с капителями.
        for (int x : new int[]{GX - 2, GX + 2}) {
            for (int y = y0; y <= y0 + 4; y++) t.set(x, y, z, y == y0 || y == y0 + 3 ? chiseled : brick);
        }
        // Стрельчатая арка над проёмом.
        t.set(GX - 1, y0 + 4, z, st(bStairs, "east", true));
        t.set(GX + 1, y0 + 4, z, st(bStairs, "west", true));
        t.set(GX, y0 + 4, z, "minecraft:gilded_blackstone");
        t.set(GX - 2, y0 + 5, z, st(bStairs, "east", false));
        t.set(GX + 2, y0 + 5, z, st(bStairs, "west", false));
        t.set(GX - 1, y0 + 5, z, brick);
        t.set(GX + 1, y0 + 5, z, brick);
        t.set(GX, y0 + 5, z, "minecraft:crying_obsidian");
        t.set(GX - 1, y0 + 6, z, st(bStairs, "east", false));
        t.set(GX + 1, y0 + 6, z, st(bStairs, "west", false));
        t.set(GX, y0 + 6, z, "minecraft:gilded_blackstone");
        t.set(GX, y0 + 7, z, wall("minecraft:polished_blackstone_brick_wall", false, false));
        t.set(GX, y0 + 8, z, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        // Внешние пилоны: золото, свечи, аметист.
        for (int x : new int[]{GX - 3, GX + 3}) {
            for (int y = y0; y <= y0 + 4; y++) {
                t.set(x, y, z, y == y0 ? "minecraft:gilded_blackstone" : wall("minecraft:polished_blackstone_brick_wall", false, false));
            }
            t.set(x, y0 + 5, z, chiseled);
            t.set(x, y0 + 6, z, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
            t.set(x, y0 + 3, z + 1, "minecraft:soul_wall_torch[facing=south]");
            t.set(x, y0 + 3, z - 1, "minecraft:soul_wall_torch[facing=north]");
        }
        // Ступенчатые «крылья» по сторонам арки.
        t.set(GX - 4, y0, z, st(bStairs, "east", false));
        t.set(GX + 4, y0, z, st(bStairs, "west", false));
        // Синие жаровни перед вратами и свечи.
        for (int x : new int[]{GX - 4, GX + 4}) {
            t.set(x, y0, z + 2, "minecraft:soul_campfire[facing=south,lit=true,signal_fire=false,waterlogged=false]");
        }
        for (int x : new int[]{GX - 5, GX + 5}) {
            t.set(x, y0, z - 1, "minecraft:candle[candles=4,lit=true,waterlogged=false]");
            t.set(x, y0, z + 1, "minecraft:candle[candles=3,lit=true,waterlogged=false]");
        }
        // Алтарь врат смотрит на проём, доски заданий — на зал.
        t.set(GX, y0, ALTAR_Z, MODID + ":gate_altar[facing=north]");
        t.set(x1 + 1, y0, z2, MODID + ":quest_board[facing=south]");
        t.set(x2 - 1, y0, z2, MODID + ":quest_board[facing=south]");
        // Знамёна гильдии на задней стене за помостом.
        for (int x : new int[]{GX - 5, GX + 5}) {
            t.set(x, 5, HZ1 + 1, "minecraft:orange_wall_banner[facing=south]");
            t.set(x, 6, HZ1 + 1, AIR);
        }
    }

    /** Убранство зала: столы с лавками, стойка распорядителя с досками заказов, угол глашатая с доской наказов, трофеи. */
    static void hallInterior(Template t, Biome b, HallStyle s, String fence) {
        String woodSlab = "minecraft:" + s.wood() + "_slab", woodStairs = "minecraft:" + s.wood() + "_stairs";
        // Ковровая дорожка от дверей к помосту.
        for (int z = ALTAR_Z + 3; z < HZ2; z++) t.set(GX, 1, z, "minecraft:red_carpet");
        // Два длинных стола с лавками, свечами и снедью.
        for (int tx : new int[]{GX - 6, GX + 6}) {
            for (int z = 14; z <= 19; z++) {
                t.set(tx, 1, z, slab(woodSlab, true));
                t.set(tx - 1, 1, z, st(woodStairs, "west", false));
                t.set(tx + 1, 1, z, st(woodStairs, "east", false));
            }
            t.set(tx, 2, 14, "minecraft:candle[candles=3,lit=true,waterlogged=false]");
            t.set(tx, 2, 17, "minecraft:cake[bites=0]");
            t.set(tx, 2, 19, "minecraft:candle[candles=2,lit=true,waterlogged=false]");
        }
        // Стойка распорядителя (восток): прилавок, доски заказов на стене, бочки, знамёна.
        int ex = HX2 - 1;
        for (int z = 20; z <= 24; z++) t.set(ex - 2, 1, z, z == 22 ? "minecraft:" + s.wood() + "_fence_gate[facing=west,in_wall=false,open=false,powered=false]" : slab(woodSlab, true));
        t.set(ex - 2, 2, 20, "minecraft:lantern[hanging=false,waterlogged=false]");
        t.set(ex - 2, 2, 24, "minecraft:candle[candles=2,lit=true,waterlogged=false]");
        t.set(ex, 1, 21, MODID + ":order_board[facing=west]");
        t.set(ex, 1, 23, MODID + ":order_board[facing=west]");
        t.set(ex, 1, 22, "minecraft:barrel[facing=up,open=false]");
        t.set(ex, 2, 22, "minecraft:lantern[hanging=false,waterlogged=false]");
        t.set(ex, 1, 20, "minecraft:chest[facing=west,type=single,waterlogged=false]",
                map("id", "minecraft:chest", "LootTable", MODID + ":chests/adventurers_guild"));
        t.set(ex, 1, 24, "minecraft:cartography_table");
        t.set(ex, 4, 21, "minecraft:orange_wall_banner[facing=west]");
        t.set(ex, 4, 23, "minecraft:orange_wall_banner[facing=west]");
        // Угол глашатая (запад): доска наказов в окружении черепов-трофеев, конторка, красное знамя.
        int wx = HX1 + 1;
        t.set(wx, 1, 22, MODID + ":bounty_board[facing=east]");
        t.set(wx, 2, 22, "minecraft:wither_skeleton_wall_skull[facing=east,powered=false]");
        t.set(wx, 3, 21, "minecraft:skeleton_wall_skull[facing=east,powered=false]");
        t.set(wx, 3, 23, "minecraft:skeleton_wall_skull[facing=east,powered=false]");
        t.set(wx - 1, 2, 22, s.accent());
        t.set(wx, 4, 22, "minecraft:red_wall_banner[facing=east]");
        t.set(wx, 1, 20, "minecraft:lectern[facing=east,has_book=false,powered=false]");
        t.set(wx, 1, 24, "minecraft:barrel[facing=up,open=false]");
        t.set(wx, 2, 24, "minecraft:skeleton_skull[powered=false,rotation=4]");
        for (int z = 20; z <= 24; z++) t.set(wx + 2, 1, z, z == 22 ? "minecraft:" + s.wood() + "_fence_gate[facing=east,in_wall=false,open=false,powered=false]" : slab(woodSlab, true));
        t.set(wx + 2, 2, 20, "minecraft:candle[candles=3,lit=true,waterlogged=false]");
        t.set(wx + 2, 2, 24, "minecraft:lantern[hanging=false,waterlogged=false]");
        // Знамёна гильдии между окнами второго этажа.
        for (int z : new int[]{11, 15, 19, 23}) {
            t.set(HX1, 12, z, "minecraft:orange_wall_banner[facing=east]");
            t.set(HX2, 12, z, "minecraft:orange_wall_banner[facing=west]");
        }
        // Фонари на стенах первого этажа.
        for (int z : new int[]{7, 11, 15, 19}) {
            t.set(HX1 + 1, 4, z, "minecraft:wall_torch[facing=east]");
            t.set(HX2 - 1, 4, z, "minecraft:wall_torch[facing=west]");
        }

        // Распорядитель и глашатай стоят у своих досок.
        t.entities.add(villager(b, "guildmaster", ex - 1, 22));
        t.entities.add(villager(b, "bounty_herald", wx + 1, 22));
    }

    static Map<String, Object> villager(Biome b, String profession, int x, int z) {
        Map<String, Object> nbt = map(
                "id", "minecraft:villager",
                "VillagerData", map("profession", MODID + ":" + profession, "level", 1, "type", b.villagerType()),
                // Опыт больше нуля: житель не теряет профессию, даже если рабочее место ещё не занято.
                "Xp", 1,
                "PersistenceRequired", (byte) 1);
        return map("blockPos", List.of(x, 1, z), "pos", List.of(x + 0.5, 1.0, z + 0.5), "nbt", nbt);
    }

    static void lampPost(Template t, String fence, int x, int z) {
        t.set(x, 1, z, fence + "[east=false,north=false,south=false,waterlogged=false,west=false]");
        t.set(x, 2, z, fence + "[east=false,north=false,south=false,waterlogged=false,west=false]");
        t.set(x, 3, z, "minecraft:lantern[hanging=false,waterlogged=false]");
    }

    // ================================================================ склеп (данж)

    static final String CRYPT = MODID + ":crypt/";
    static final String PASSAGE = MODID + ":crypt_passage";
    static final String AIR = "minecraft:air";
    static final String COBWEB = "minecraft:cobweb";
    static final String SOUL_LANTERN = "minecraft:soul_lantern[hanging=true,waterlogged=false]";

    static int generateCrypt(Path resources) throws IOException {
        Path structures = resources.resolve("data/" + MODID + "/structure/crypt");
        Path pools = resources.resolve("data/" + MODID + "/worldgen/template_pool/crypt");
        Map<String, Template> pieces = new LinkedHashMap<>();
        pieces.put("entrance", cryptEntrance());
        pieces.put("hub", cryptHub());
        pieces.put("boss_room", cryptBossRoom());
        pieces.put("corridor_straight", cryptCorridor());
        pieces.put("corridor_turn", cryptTurn());
        pieces.put("tomb_room", tombRoom());
        pieces.put("library", libraryRoom());
        pieces.put("spider_den", spiderDen());
        pieces.put("armory", armoryRoom());
        pieces.put("dead_end", deadEnd());
        for (Map.Entry<String, Template> piece : pieces.entrySet()) {
            write(structures.resolve(piece.getKey() + ".nbt"), piece.getValue().toNbt());
        }
        writeText(pools.resolve("entrance.json"), poolJson(CRYPT, "minecraft:empty", "entrance", 1));
        writeText(pools.resolve("hub.json"), poolJson(CRYPT, "minecraft:empty", "hub", 1));
        writeText(pools.resolve("boss.json"), poolJson(CRYPT, "minecraft:empty", "boss_room", 1));
        writeText(pools.resolve("corridors.json"), poolJson(CRYPT, CRYPT + "dead_ends", "corridor_straight", 3, "corridor_turn", 2));
        writeText(pools.resolve("rooms.json"), poolJson(CRYPT, CRYPT + "dead_ends",
                "tomb_room", 2, "library", 1, "spider_den", 1, "armory", 1));
        writeText(pools.resolve("dead_ends.json"), poolJson(CRYPT, "minecraft:empty", "dead_end", 1));
        return pieces.size() + 6;
    }

    /** Вход: мавзолей на поверхности (пол на y = 13) и лестница вниз к площадке с выходом на юг. */
    static Template cryptEntrance() {
        Random r = new Random(1);
        Template t = new Template(9, 19, 19);
        int floorY = 13;
        for (int x = 0; x <= 8; x++) {
            for (int z = 0; z <= 8; z++) {
                t.set(x, floorY, z, brick(r));
                t.set(x, 18, z, brick(r));
                for (int y = 14; y <= 17; y++) {
                    boolean edgeX = x == 0 || x == 8;
                    boolean edgeZ = z == 0 || z == 8;
                    if (edgeX && edgeZ) t.set(x, y, z, "minecraft:chiseled_stone_bricks");
                    else if (edgeX || edgeZ) t.set(x, y, z, brick(r));
                    else t.set(x, y, z, AIR);
                }
            }
        }
        t.fill(3, 14, 0, 5, 16, 0, AIR);
        for (int z = 3; z <= 5; z++) {
            for (int y = 15; y <= 16; y++) {
                t.set(0, y, z, bars(false));
                t.set(8, y, z, bars(false));
            }
        }
        for (int[] p : new int[][]{{2, 2}, {6, 2}, {2, 6}, {6, 6}}) t.set(p[0], 17, p[1], SOUL_LANTERN);
        t.set(1, 17, 1, COBWEB);
        t.set(7, 17, 7, COBWEB);
        t.set(4, 14, 7, "minecraft:chiseled_stone_bricks");
        t.set(4, 15, 7, "minecraft:wither_skeleton_skull[rotation=8]");

        // Лестница: 13 ступеней вниз на юг, под ней сплошная кладка.
        for (int i = 0; i <= 12; i++) {
            int z = 2 + i;
            int y = 12 - i;
            for (int x = 2; x <= 6; x++) for (int yy = 0; yy < y; yy++) t.set(x, yy, z, brick(r));
            for (int yy = y; yy <= Math.min(12, y + 4); yy++) {
                t.set(2, yy, z, brick(r));
                t.set(6, yy, z, brick(r));
            }
            for (int x = 3; x <= 5; x++) {
                t.set(x, y, z, "minecraft:stone_brick_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]");
                for (int yy = y + 1; yy <= y + 4; yy++) t.set(x, yy, z, AIR);
            }
            if (y + 5 <= 12) for (int x = 2; x <= 6; x++) t.set(x, y + 5, z, brick(r));
        }
        // Ограждение вокруг проёма лестницы в полу мавзолея.
        for (int z = 2; z <= 5; z++) {
            t.set(2, 14, z, bars(false));
            t.set(6, 14, z, bars(false));
        }
        for (int x = 3; x <= 5; x++) t.set(x, 14, 6, bars(true));

        // Площадка внизу и выход в главный зал.
        for (int z = 15; z <= 17; z++) {
            for (int x = 2; x <= 6; x++) {
                t.set(x, 0, z, brick(r));
                t.set(x, 5, z, brick(r));
                for (int y = 1; y <= 4; y++) t.set(x, y, z, (x == 2 || x == 6) ? brick(r) : AIR);
            }
        }
        for (int x = 2; x <= 6; x++) for (int y = 0; y <= 5; y++) t.set(x, y, 18, brick(r));
        t.set(4, 4, 16, SOUL_LANTERN);
        doorway(t, 4, 18, "south", cryptJigsaw(MODID + ":crypt_entrance", MODID + ":crypt_hub", CRYPT + "hub", 0));
        return t;
    }

    static Template cryptHub() {
        return cryptHub(true);
    }

    /**
     * Главный зал 13x13: колонны, спавнеры, сундук, стражи. Проход на юг ведёт к боссу (склеп в мире)
     * или к спуску на следующий этаж (данжи за вратами).
     */
    static Template cryptHub(boolean bossDoor) {
        Random r = new Random(2);
        Template t = new Template(13, 7, 13);
        room(t, 0, 0, 12, 12, 6, r);
        for (int[] p : new int[][]{{3, 3}, {9, 3}, {3, 9}, {9, 9}}) t.fill(p[0], 1, p[1], p[0], 5, p[1], "minecraft:chiseled_stone_bricks");
        t.set(6, 1, 6, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        spawner(t, 2, 1, 10, "minecraft:zombie");
        spawner(t, 10, 1, 2, "minecraft:skeleton");
        chest(t, 10, 1, 10, "west", "crypt");
        for (int[] p : new int[][]{{6, 3}, {6, 9}, {3, 6}, {9, 6}}) t.set(p[0], 5, p[1], SOUL_LANTERN);
        for (int[] p : new int[][]{{1, 1}, {11, 11}, {1, 11}, {11, 1}}) t.set(p[0], 5, p[1], COBWEB);
        guard(t, 4, 8, "minecraft:zombie");
        guard(t, 8, 4, "minecraft:zombie");
        doorway(t, 6, 0, "north", cryptJigsaw(MODID + ":crypt_hub", MODID + ":crypt_entrance", "minecraft:empty", 0));
        // Высокий приоритет: проход к боссу достраивается первым, пока рядом ещё свободно.
        doorway(t, 6, 12, "south", bossDoor
                ? cryptJigsaw(MODID + ":crypt_boss_door", MODID + ":crypt_boss_room", CRYPT + "boss", 10)
                : cryptJigsaw(MODID + ":descent_door", MODID + ":descent_room", CRYPT + "descent", 10));
        doorway(t, 0, 6, "west", passage(CRYPT + "corridors"));
        doorway(t, 12, 6, "east", passage(CRYPT + "corridors"));
        return t;
    }

    /** Логово Короля Склепа: зал из чернокамня, круг из плачущего обсидиана, трон и сундуки с сокровищами. */
    static Template cryptBossRoom() {
        Random r = new Random(3);
        Template t = new Template(17, 10, 17);
        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 16; z++) {
                t.set(x, 0, z, r.nextDouble() < 0.85 ? "minecraft:polished_blackstone_bricks" : "minecraft:cracked_polished_blackstone_bricks");
                t.set(x, 9, z, "minecraft:polished_blackstone");
                for (int y = 1; y <= 8; y++) {
                    boolean edge = x == 0 || x == 16 || z == 0 || z == 16;
                    t.set(x, y, z, edge ? blackBrick(r) : AIR);
                }
                double d = Math.sqrt((x - 8) * (x - 8) + (z - 8) * (z - 8));
                if (d >= 3.5 && d < 4.5) t.set(x, 0, z, "minecraft:crying_obsidian");
            }
        }
        for (int[] p : new int[][]{{3, 3}, {12, 3}, {3, 12}, {12, 12}}) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    for (int y = 1; y <= 8; y++) {
                        boolean cap = y == 1 || y == 8;
                        t.set(p[0] + dx, y, p[1] + dz, cap ? "minecraft:chiseled_polished_blackstone" : "minecraft:polished_blackstone");
                    }
                }
            }
        }
        for (int[] p : new int[][]{{8, 4}, {4, 8}, {12, 8}, {8, 12}}) t.set(p[0], 8, p[1], SOUL_LANTERN);
        // Трон у южной стены.
        t.fill(7, 1, 15, 9, 1, 15, "minecraft:gilded_blackstone");
        t.set(8, 2, 15, "minecraft:polished_blackstone_stairs[facing=south,half=bottom,shape=straight,waterlogged=false]");
        t.set(7, 2, 15, "minecraft:polished_blackstone_slab[type=bottom,waterlogged=false]");
        t.set(9, 2, 15, "minecraft:polished_blackstone_slab[type=bottom,waterlogged=false]");
        t.fill(8, 3, 16, 8, 5, 16, "minecraft:gilded_blackstone");
        chest(t, 5, 1, 15, "north", "crypt_treasure");
        chest(t, 11, 1, 15, "north", "crypt_treasure");
        t.set(2, 1, 14, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        t.set(14, 1, 14, "minecraft:soul_campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        for (int[] p : new int[][]{{1, 1}, {15, 1}, {1, 2}, {15, 2}, {2, 1}, {14, 1}}) t.set(p[0], 1, p[1], "minecraft:bone_block[axis=y]");
        t.entities.add(map("blockPos", List.of(8, 1, 9), "pos", List.of(8.5, 1.0, 9.5),
                "nbt", map("id", MODID + ":crypt_king", "PersistenceRequired", (byte) 1)));
        doorway(t, 8, 0, "north", cryptJigsaw(MODID + ":crypt_boss_room", MODID + ":crypt_boss_door", "minecraft:empty", 0));
        return t;
    }

    static Template cryptCorridor() {
        Random r = new Random(4);
        Template t = new Template(5, 6, 9);
        room(t, 0, 0, 4, 8, 5, r);
        t.set(2, 4, 4, SOUL_LANTERN);
        t.set(1, 4, 2, COBWEB);
        t.set(3, 4, 6, COBWEB);
        doorway(t, 2, 0, "north", passage(CRYPT + "rooms"));
        doorway(t, 2, 8, "south", passage(CRYPT + "rooms"));
        return t;
    }

    /**
     * Поворот. Выход сдвинут на 5 блоков от входа: комната шириной 9 у выхода не задевает
     * зал или комнату, к которой поворот пристроен.
     */
    static Template cryptTurn() {
        Random r = new Random(5);
        Template t = new Template(5, 6, 8);
        room(t, 0, 0, 4, 7, 5, r);
        t.set(1, 4, 2, COBWEB);
        t.set(2, 4, 3, SOUL_LANTERN);
        doorway(t, 2, 0, "north", passage(CRYPT + "rooms"));
        doorway(t, 4, 5, "east", passage(CRYPT + "rooms"));
        return t;
    }

    /** Гробница: саркофаги с черепами, спавнер скелетов, сундук. */
    static Template tombRoom() {
        Random r = new Random(6);
        Template t = baseRoom(r);
        for (int x : new int[]{2, 6}) {
            t.fill(x, 1, 1, x, 1, 2, "minecraft:polished_deepslate");
            t.set(x, 2, 1, "minecraft:skeleton_skull[rotation=0]");
        }
        spawner(t, 2, 1, 6, "minecraft:skeleton");
        chest(t, 6, 1, 6, "west", "crypt");
        guard(t, 4, 6, "minecraft:zombie");
        doorway(t, 4, 0, "north", passage(CRYPT + "corridors"));
        doorway(t, 8, 4, "east", passage(CRYPT + "corridors"));
        return t;
    }

    /** Библиотека: стеллажи вдоль стен, спавнер зомби, сундук. */
    static Template libraryRoom() {
        Random r = new Random(7);
        Template t = baseRoom(r);
        t.fill(1, 1, 1, 7, 3, 1, "minecraft:bookshelf");
        t.fill(7, 1, 1, 7, 2, 7, "minecraft:bookshelf");
        spawner(t, 6, 1, 6, "minecraft:zombie");
        chest(t, 1, 1, 7, "east", "crypt");
        guard(t, 4, 4, "minecraft:skeleton");
        doorway(t, 0, 4, "west", passage(CRYPT + "corridors"));
        return t;
    }

    /** Паучье логово: паутина повсюду и спавнер пещерных пауков. */
    static Template spiderDen() {
        Random r = new Random(8);
        Template t = baseRoom(r);
        for (int x = 1; x <= 7; x++) {
            for (int z = 1; z <= 7; z++) {
                boolean nearDoor = (x >= 3 && x <= 5 && z == 7) || (z >= 3 && z <= 5 && (x == 1 || x == 7));
                for (int y = 1; y <= 5; y++) if (!nearDoor && r.nextDouble() < 0.25) t.set(x, y, z, COBWEB);
            }
        }
        spawner(t, 4, 1, 4, "minecraft:cave_spider");
        chest(t, 1, 1, 1, "south", "crypt");
        doorway(t, 8, 4, "east", passage(CRYPT + "corridors"));
        doorway(t, 0, 4, "west", passage(CRYPT + "corridors"));
        return t;
    }

    /** Оружейная: оружейные стойки, наковальня, скелеты-стражи. */
    static Template armoryRoom() {
        Random r = new Random(9);
        Template t = baseRoom(r);
        for (int z : new int[]{2, 3, 5, 6}) {
            t.set(1, 1, z, MODID + ":weapon_rack");
            t.set(7, 1, z, MODID + ":weapon_rack");
        }
        t.set(1, 1, 7, "minecraft:anvil[facing=north]");
        spawner(t, 7, 1, 1, "minecraft:skeleton");
        chest(t, 7, 1, 7, "west", "crypt");
        guard(t, 3, 3, "minecraft:skeleton");
        guard(t, 5, 5, "minecraft:skeleton");
        doorway(t, 4, 0, "north", passage(CRYPT + "corridors"));
        return t;
    }

    /** Комната 9x9 с входом с юга, общий каркас для всех комнат. */
    static Template baseRoom(Random r) {
        Template t = new Template(9, 7, 9);
        room(t, 0, 0, 8, 8, 6, r);
        t.set(4, 5, 4, SOUL_LANTERN);
        t.set(1, 5, 7, COBWEB);
        t.set(7, 5, 1, COBWEB);
        doorway(t, 4, 8, "south", passage(CRYPT + "corridors"));
        return t;
    }

    /** Тупик: короткая ниша, закрывающая проход, если дальше ничего не поместилось. */
    static Template deadEnd() {
        Random r = new Random(10);
        Template t = new Template(5, 6, 2);
        for (int x = 0; x <= 4; x++) {
            t.set(x, 0, 0, brick(r));
            t.set(x, 5, 0, brick(r));
            for (int y = 1; y <= 4; y++) t.set(x, y, 0, (x == 0 || x == 4) ? brick(r) : AIR);
            for (int y = 0; y <= 5; y++) t.set(x, y, 1, brick(r));
        }
        t.set(1, 4, 0, COBWEB);
        t.set(2, 1, 0, "minecraft:jigsaw[orientation=north_up]", passage("minecraft:empty"));
        return t;
    }

    /** Пол на y = 0, стены по периметру, потолок на y = ceil, внутри воздух. */
    static void room(Template t, int x1, int z1, int x2, int z2, int ceil, Random r) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                t.set(x, 0, z, r.nextDouble() < 0.8 ? "minecraft:stone_bricks" : "minecraft:cracked_stone_bricks");
                t.set(x, ceil, z, brick(r));
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                for (int y = 1; y < ceil; y++) t.set(x, y, z, edge ? brick(r) : AIR);
            }
        }
    }

    /** Проём 3x3 в стене; jigsaw в нижнем центре смотрит наружу (facing). */
    static void doorway(Template t, int x, int z, String facing, Map<String, Object> jigsaw) {
        boolean wallAlongX = facing.equals("north") || facing.equals("south");
        for (int d = -1; d <= 1; d++) {
            for (int y = 1; y <= 3; y++) t.set(wallAlongX ? x + d : x, y, wallAlongX ? z : z + d, AIR);
        }
        t.set(x, 1, z, "minecraft:jigsaw[orientation=" + facing + "_up]", jigsaw);
    }

    static Map<String, Object> cryptJigsaw(String name, String target, String pool, int selectionPriority) {
        Map<String, Object> nbt = map("id", "minecraft:jigsaw", "name", name, "target", target, "pool", pool,
                "final_state", AIR, "joint", "aligned");
        if (selectionPriority != 0) nbt.put("selection_priority", selectionPriority);
        return nbt;
    }

    static Map<String, Object> passage(String pool) {
        return cryptJigsaw(PASSAGE, PASSAGE, pool, 0);
    }

    static void spawner(Template t, int x, int y, int z, String mob) {
        t.set(x, y, z, "minecraft:spawner", map("id", "minecraft:mob_spawner",
                "SpawnData", map("entity", map("id", mob)),
                "Delay", (short) 20, "MinSpawnDelay", (short) 200, "MaxSpawnDelay", (short) 600,
                "SpawnCount", (short) 3, "MaxNearbyEntities", (short) 6,
                "RequiredPlayerRange", (short) 16, "SpawnRange", (short) 4));
    }

    static void chest(Template t, int x, int y, int z, String facing, String lootTable) {
        t.set(x, y, z, "minecraft:chest[facing=" + facing + ",type=single,waterlogged=false]",
                map("id", "minecraft:chest", "LootTable", MODID + ":chests/" + lootTable));
    }

    /** Моб-страж, который стоит в комнате с момента генерации и не исчезает. */
    static void guard(Template t, int x, int z, String mob) {
        t.entities.add(map("blockPos", List.of(x, 1, z), "pos", List.of(x + 0.5, 1.0, z + 0.5),
                "nbt", map("id", mob, "PersistenceRequired", (byte) 1)));
    }

    static String bars(boolean alongX) {
        return "minecraft:iron_bars" + (alongX
                ? "[east=true,north=false,south=false,waterlogged=false,west=true]"
                : "[east=false,north=true,south=true,waterlogged=false,west=false]");
    }

    static String brick(Random r) {
        double v = r.nextDouble();
        return v < 0.6 ? "minecraft:stone_bricks" : v < 0.85 ? "minecraft:mossy_stone_bricks" : "minecraft:cracked_stone_bricks";
    }

    static String blackBrick(Random r) {
        double v = r.nextDouble();
        return v < 0.7 ? "minecraft:polished_blackstone_bricks" : "minecraft:cracked_polished_blackstone_bricks";
    }

    static String poolJson(String prefix, String fallback, Object... pieceWeights) {
        StringBuilder elements = new StringBuilder();
        for (int i = 0; i < pieceWeights.length; i += 2) {
            if (i > 0) elements.append(",\n");
            elements.append("""
                        {
                          "weight": %d,
                          "element": {
                            "element_type": "minecraft:single_pool_element",
                            "location": "%s%s",
                            "processors": "minecraft:empty",
                            "projection": "rigid"
                          }
                        }""".formatted((Integer) pieceWeights[i + 1], prefix, pieceWeights[i]));
        }
        return "{\n  \"fallback\": \"" + fallback + "\",\n  \"elements\": [\n" + elements + "\n  ]\n}\n";
    }

    // ---------------------------------------------------------------- дома наставников

    /*
     * Дом наставника — уменьшенная родня зала гильдии: каменный первый этаж, фахверковый второй с выносом
     * на улицу и во двор, крутая крыша между двумя ступенчатыми щипцами. Дом стоит длинной стороной к улице
     * (x = 0 — jigsaw-вход, дверь под выносом в x = 2), за северным щипцом — пристройка своего класса:
     * двор мечника со сторожевой башней, стрельбище лучника, круглая башня мага со шпилем.
     * Участок 12x13 и высота 18 — как у крупных ванильных домов: такие ещё влезают на участки улиц
     * всех пяти типов деревень (шире или выше — уже нет: улицы растягивают место под дома до высоты самого
     * высокого ванильного дома).
     */
    /** Каменные стены: x = HB_X1..HB_X2, южный щипец z = HB_Z1 (северный — у каждого дома свой). */
    static final int HB_X1 = 2, HB_X2 = 9, HB_Z1 = 1;
    /** Пол второго этажа, верх фахверка, карниз; размеры участка с карнизами на x = 0 и 11. */
    static final int HB_FLOOR2 = 5, HB_TIMBER_TOP = 8, HB_EAVES = 9, HB_SX = 12, HB_SY = 18, HB_SZ = 13;

    /** Корпус дома: северный щипец {@code z2} и дверь в {@code door}. */
    record HouseBody(int z2, int door) {}

    static Template buildGuild(Biome b, Guild g) {
        HallStyle s = hallStyle(b);
        // У мага корпус короче: за ним целиком встаёт круглая башня.
        HouseBody h = g.name().equals("mage_guild") ? new HouseBody(7, 4) : new HouseBody(9, 5);
        int z2 = h.z2(), door = h.door();
        Template t = new Template(HB_SX, HB_SY, HB_SZ);
        String woodStairs = "minecraft:" + s.wood() + "_stairs", planks = "minecraft:" + s.wood() + "_planks";
        String beam = s.timber().replace("stripped_", "");
        int[] windows = {HB_Z1 + 2, z2 - 2};

        // Над участком — чистый воздух (вычищает рельеф), на улице (x = 0) — только выше крыльца.
        for (int x = 0; x < HB_SX; x++) {
            for (int z = 0; z < HB_SZ; z++) {
                for (int y = x == 0 ? HB_FLOOR2 : 1; y < HB_SY; y++) t.set(x, y, z, AIR);
            }
        }
        // Пол: доски с каменной окантовкой, крыльцо под выносом.
        for (int x = HB_X1; x <= HB_X2; x++) {
            for (int z = HB_Z1; z <= z2; z++) {
                boolean edge = x == HB_X1 || x == HB_X2 || z == HB_Z1 || z == z2;
                t.set(x, 0, z, edge ? s.plinth() : (x == HB_X1 + 1 || z == HB_Z1 + 1 || z == z2 - 1 ? "minecraft:polished_andesite" : planks));
            }
        }
        for (int z = HB_Z1; z <= z2; z++) t.set(1, 0, z, s.path());

        // ---------------------------------------------------------------- первый этаж: камень
        for (int y = 1; y < HB_FLOOR2; y++) {
            for (int x = HB_X1; x <= HB_X2; x++) {
                for (int z = HB_Z1; z <= z2; z++) {
                    boolean edgeX = x == HB_X1 || x == HB_X2, edgeZ = z == HB_Z1 || z == z2;
                    if (!edgeX && !edgeZ) continue;
                    t.set(x, y, z, (edgeX && edgeZ) || y == 1 ? s.plinth() : s.stone());
                }
            }
        }
        // Окна в длинных стенах и в южном щипце, дверь под выносом, факелы у входа.
        for (int z : windows) {
            for (int y = 2; y <= 3; y++) {
                t.set(HB_X1, y, z, pane("glass_pane", false));
                t.set(HB_X2, y, z, pane("glass_pane", false));
            }
            t.set(HB_X1, 4, z, s.accent());
            t.set(HB_X2, 4, z, s.accent());
        }
        for (int y = 2; y <= 3; y++) t.set(6, y, HB_Z1, pane("glass_pane", true));
        t.set(6, 4, HB_Z1, s.accent());
        door(t, HB_X1, 1, door, s.door(), "east", "right");
        t.set(HB_X1, 3, door, s.accent());
        t.set(HB_X1 - 1, 3, door - 1, "minecraft:wall_torch[facing=west]");
        t.set(HB_X1 - 1, 3, door + 1, "minecraft:wall_torch[facing=west]");
        String doorstep = b.entranceY() == 0 ? st(woodStairs, "east", false) : "minecraft:structure_void";
        t.set(0, b.entranceY(), door, "minecraft:jigsaw[orientation=west_up]",
                jigsaw("minecraft:building_entrance", "minecraft:empty", doorstep, "aligned"));

        // ---------------------------------------------------------------- второй этаж: фахверк с выносом
        for (int x = HB_X1; x <= HB_X2; x++) {
            for (int z = HB_Z1 + 1; z < z2; z++) t.set(x, HB_FLOOR2, z, x == HB_X1 || x == HB_X2 ? log(beam, 'z') : planks);
        }
        for (int[] side : new int[][]{{HB_X1 - 1, -1}, {HB_X2 + 1, 1}}) {
            int x = side[0], d = side[1];
            String out = d < 0 ? "west" : "east", in = d < 0 ? "east" : "west";
            for (int z = HB_Z1; z <= z2; z++) {
                for (int y = HB_FLOOR2; y <= HB_TIMBER_TOP; y++) t.set(x, y, z, houseTimber(s, woodStairs, h, y, z));
                // Кронштейны-подкосы под выносом.
                if (z == HB_Z1 || z == door || z == z2) t.set(x, HB_FLOOR2 - 1, z, st(s.stoneStairs(), in, true));
            }
            // Окна с цветочными ящиками под ними.
            for (int z : windows) {
                for (int y = 6; y <= 7; y++) t.set(x, y, z, pane("glass_pane", false));
                t.set(x + d, 5, z, "minecraft:" + s.wood() + "_trapdoor[facing=" + out + ",half=top,open=false,powered=false,waterlogged=false]");
            }
            // Внутри: стены второго этажа заменены выносом.
            for (int z = HB_Z1 + 1; z < z2; z++) for (int y = HB_FLOOR2 + 1; y <= HB_TIMBER_TOP; y++) t.set(x - d, y, z, AIR);
        }
        for (int z = HB_Z1 + 1; z < z2; z++) {
            t.set(HB_X1 - 1, HB_FLOOR2, z, log(beam, 'z'));
            t.set(HB_X2 + 1, HB_FLOOR2, z, log(beam, 'z'));
        }

        houseRoof(t, s, z2);
        houseGable(t, s, HB_Z1, false);
        houseGable(t, s, z2, true);
        // Затяжки под крышей и люстры на них.
        for (int z : windows) {
            for (int x = HB_X1; x <= HB_X2; x++) t.set(x, HB_EAVES + 1, z, log(beam, 'x'));
            chandelier(t, 4, z, HB_EAVES, HB_EAVES - 1);
            chandelier(t, 7, z, HB_EAVES, HB_EAVES - 1);
        }

        // ---------------------------------------------------------------- убранство
        String color = g.color();
        // Знамя гильдии над входом, на стойке выноса.
        t.set(0, 7, door, "minecraft:" + color + "_wall_banner[facing=west]");
        // Лестница на второй этаж у южного щипца.
        for (int y = 1; y <= HB_FLOOR2; y++) t.set(8, y, HB_Z1 + 1, "minecraft:ladder[facing=south,waterlogged=false]");
        // Рабочее место наставника у задней стены напротив двери, к нему — ковровая дорожка.
        t.set(8, 1, door, g.workstation());
        for (int x = HB_X1 + 1; x <= 7; x++) t.set(x, 1, door, "minecraft:" + color + "_carpet");
        t.set(8, 3, door - 1, "minecraft:" + color + "_wall_banner[facing=west]");
        t.set(8, 3, door + 1, "minecraft:" + color + "_wall_banner[facing=west]");
        t.set(3, 1, HB_Z1 + 1, "minecraft:chest[facing=south,type=single,waterlogged=false]",
                map("id", "minecraft:chest", "LootTable", MODID + ":chests/" + g.name()));
        t.set(3, 1, z2 - 1, MODID + ":quest_board[facing=north]");
        for (int z : windows) for (int x : new int[]{4, 7}) t.set(x, 4, z, "minecraft:lantern[hanging=true,waterlogged=false]");
        // Второй этаж: кровать наставника, бочка, полки, столик со свечой.
        t.set(3, 6, z2 - 1, "minecraft:" + color + "_bed[facing=west,occupied=false,part=head]");
        t.set(4, 6, z2 - 1, "minecraft:" + color + "_bed[facing=west,occupied=false,part=foot]");
        t.set(7, 6, z2 - 1, "minecraft:barrel[facing=up,open=false]");
        t.set(8, 6, z2 - 1, "minecraft:bookshelf");
        t.set(3, 6, HB_Z1 + 1, slab("minecraft:" + s.wood() + "_slab", true));
        t.set(3, 7, HB_Z1 + 1, "minecraft:candle[candles=2,lit=true,waterlogged=false]");
        t.set(4, 6, HB_Z1 + 1, st(woodStairs, "west", false));
        for (int x = 4; x <= 7; x++) t.set(x, 6, door, "minecraft:" + color + "_carpet");

        // Jigsaw в полу: к нему крепится шаблон с наставником (житель с нужной профессией).
        t.set(6, 0, door + 1, "minecraft:jigsaw[orientation=up_north]",
                jigsaw("minecraft:bottom", MODID + ":village/" + b.name() + "/" + g.profession() + "_mentor", planks, "rollable"));

        switch (g.name()) {
            case "swordmaster_guild" -> swordYard(t, s, z2);
            case "archer_guild" -> archeryRange(t, s, z2);
            default -> mageTower(t, s, z2);
        }
        return t;
    }

    /** Фахверк длинной стены: лежни по низу и верху, стойки у углов и двери, подкосы у углов, белёные филёнки. */
    static String houseTimber(HallStyle s, String woodStairs, HouseBody h, int y, int z) {
        if (y == HB_FLOOR2 || y == HB_TIMBER_TOP) return log(s.timber(), 'z');
        if (z == HB_Z1 || z == h.z2() || z == h.door()) return log(s.timber(), 'y');
        if ((z == HB_Z1 + 1 || z == h.z2() - 1) && y == 6) return st(woodStairs, z == HB_Z1 + 1 ? "north" : "south", true);
        if ((z == h.door() - 1 || z == h.door() + 1) && y == 7) return st(woodStairs, z < h.door() ? "south" : "north", false);
        return s.plaster();
    }

    /** Крутая двускатная крыша от карнизов над выносом (x = 0 и 11) до двойного конька, изнутри — подшивка. */
    static void houseRoof(Template t, HallStyle s, int z2) {
        for (int z = HB_Z1 + 1; z < z2; z++) {
            for (int k = 0; k <= 5; k++) {
                int y = HB_EAVES + k, xl = k, xr = HB_SX - 1 - k;
                if (k == 5) {
                    t.set(xl, y, z, s.roofBlock());
                    t.set(xr, y, z, s.roofBlock());
                    t.set(xl, y + 1, z, slab(s.roofSlab(), false));
                    t.set(xr, y + 1, z, slab(s.roofSlab(), false));
                    continue;
                }
                t.set(xl, y, z, st(s.roofStairs(), "east", false));
                t.set(xr, y, z, st(s.roofStairs(), "west", false));
                if (k >= 1) {
                    t.set(xl + 1, y, z, st(s.roofStairs(), "west", true));
                    t.set(xr - 1, y, z, st(s.roofStairs(), "east", true));
                }
            }
        }
    }

    /** Каменный щипец со ступенчатым верхом; в нём окно второго этажа и чердачное окошко. */
    static void houseGable(Template t, HallStyle s, int z, boolean north) {
        for (int x = 1; x <= HB_SX - 2; x++) {
            int k = Math.min(x, HB_SX - 1 - x);
            int top = HB_EAVES + 2 * (k / 2) + 1;
            for (int y = HB_FLOOR2; y <= top; y++) t.set(x, y, z, x == 1 || x == HB_SX - 2 ? s.plinth() : s.stone());
            boolean stepEdge = k % 2 == 1 || k == 5;
            t.set(x, top + 1, z, stepEdge ? wall(s.stoneWall(), false, false) : slab(s.stoneSlab(), false));
            // Пояс-карниз между этажами.
            t.set(x, HB_FLOOR2, z + (north ? 1 : -1), st(s.stoneStairs(), north ? "north" : "south", true));
        }
        for (int x = 5; x <= 6; x++) {
            for (int y = 6; y <= 7; y++) t.set(x, y, z, pane("glass_pane", true));
            t.set(x, 8, z, s.accent());
            t.set(x, 11, z, pane("glass_pane", true));
        }
    }

    /** Мечник: мощёный учебный двор с манекенами, точилом и наковальней, сторожевая башня с зубцами и знаменем. */
    static void swordYard(Template t, HallStyle s, int z2) {
        String fence = "minecraft:" + s.wood() + "_fence";
        yardGround(t, s, z2);
        yardFence(t, s, fence, "red", z2);
        // Труба горна на северном щипце.
        for (int y = 12; y <= 15; y++) t.set(8, y, z2, "minecraft:bricks");
        t.set(8, 16, z2, "minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]");
        // Манекены: столб, тюк сена, тыква-голова, знамя на груди.
        for (int x : new int[]{3, 5}) {
            t.set(x, 1, z2 + 2, fence + "[east=false,north=false,south=false,waterlogged=false,west=false]");
            t.set(x, 2, z2 + 2, "minecraft:hay_block[axis=y]");
            t.set(x, 3, z2 + 2, "minecraft:carved_pumpkin[facing=north]");
            t.set(x, 2, z2 + 1, "minecraft:red_wall_banner[facing=north]");
        }
        t.set(2, 1, z2 + 1, "minecraft:anvil[facing=east]");
        t.set(4, 1, z2 + 1, "minecraft:grindstone[face=floor,facing=east]");
        t.set(6, 1, z2 + 1, "minecraft:barrel[facing=up,open=false]");
        t.set(6, 2, z2 + 1, "minecraft:lantern[hanging=false,waterlogged=false]");
        // Сторожевая башня 3x3 в углу двора, сплошная (без тёмных пустот), с зубцами.
        int cz = z2 + 2;
        for (int x = 7; x <= 9; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                boolean corner = (x == 7 || x == 9) && z != cz;
                t.set(x, 0, z, s.plinth());
                for (int y = 1; y <= 12; y++) t.set(x, y, z, y == 1 || y == 12 || (corner && y % 3 == 0) ? s.plinth() : s.stone());
                if ((x + z) % 2 == 0) t.set(x, 13, z, s.stone());
            }
        }
        t.set(8, 13, cz, "minecraft:lantern[hanging=false,waterlogged=false]");
        t.set(6, 9, cz, "minecraft:red_wall_banner[facing=west]");
        t.set(6, 10, cz, AIR);
        for (int y = 5; y <= 6; y++) t.set(8, y, cz - 1, "minecraft:iron_bars[east=true,north=false,south=false,waterlogged=false,west=true]");
        t.set(8, 14, cz, wall(s.stoneWall(), false, false));
        t.set(8, 15, cz, "minecraft:red_banner[rotation=8]");
    }

    /** Лучник: стрельбище с мишенями в изгороди, навес у линии стрельбы, деревянная дозорная вышка. */
    static void archeryRange(Template t, HallStyle s, int z2) {
        String fence = "minecraft:" + s.wood() + "_fence", planks = "minecraft:" + s.wood() + "_planks";
        String beam = s.timber().replace("stripped_", "");
        yardGround(t, s, z2);
        yardFence(t, s, fence, "green", z2);
        int back = HB_SZ - 1;
        // Мишени на тюках сена прямо в дальней изгороди.
        for (int x : new int[]{5, 7, 9}) {
            t.set(x, 1, back, "minecraft:hay_block[axis=y]");
            t.set(x, 2, back, "minecraft:target[power=0]");
            t.set(x, 3, back, "minecraft:hay_block[axis=x]");
        }
        // Навес над линией стрельбы.
        for (int x = 4; x <= 9; x++) t.set(x, 4, z2 + 1, st(s.roofStairs(), "north", false));
        for (int x : new int[]{4, 9}) for (int y = 1; y <= 3; y++) t.set(x, y, z2 + 1, fence + "[east=false,north=false,south=false,waterlogged=false,west=false]");
        t.set(5, 1, z2 + 1, "minecraft:fletching_table");
        t.set(6, 1, z2 + 1, "minecraft:barrel[facing=up,open=false]");
        t.set(7, 1, z2 + 1, "minecraft:hay_block[axis=x]");
        t.set(8, 1, z2 + 1, "minecraft:barrel[facing=up,open=false]");
        t.set(8, 2, z2 + 1, "minecraft:lantern[hanging=false,waterlogged=false]");
        // Дозорная вышка на четырёх столбах с площадкой и шатровой крышей.
        int cz = z2 + 2;
        for (int x : new int[]{1, 3}) {
            for (int z : new int[]{cz - 1, cz + 1}) for (int y = 1; y <= 10; y++) t.set(x, y, z, log(beam, 'y'));
        }
        for (int x = 1; x <= 3; x++) for (int z = cz - 1; z <= cz + 1; z++) if (x == 2 || z == cz) t.set(x, 7, z, planks);
        for (int y = 1; y <= 6; y++) t.set(2, y, cz + 1, log(beam, 'y'));
        for (int y = 1; y <= 7; y++) t.set(2, y, cz, "minecraft:ladder[facing=north,waterlogged=false]");
        for (int z : new int[]{cz - 1, cz + 1}) t.set(2, 8, z, fence + "[east=true,north=false,south=false,waterlogged=false,west=true]");
        for (int x : new int[]{1, 3}) t.set(x, 8, cz, fence + "[east=false,north=true,south=true,waterlogged=false,west=false]");
        for (int x = 1; x <= 3; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                int dx = x - 2, dz = z - cz;
                String facing = Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? "east" : "west") : (dz < 0 ? "south" : "north");
                t.set(x, 11, z, dx != 0 && dz != 0 ? s.roofBlock() : st(s.roofStairs(), facing, false));
            }
        }
        // Свес крыши вышки над улицей и двором.
        t.set(0, 11, cz, st(s.roofStairs(), "east", false));
        t.set(4, 11, cz, st(s.roofStairs(), "west", false));
        t.set(2, 11, cz, s.roofBlock());
        t.set(2, 12, cz, s.roofBlock());
        t.set(2, 13, cz, slab(s.roofSlab(), false));
        t.set(2, 10, cz, "minecraft:lantern[hanging=true,waterlogged=false]");
        t.set(0, 8, cz - 1, "minecraft:green_wall_banner[facing=west]");
    }

    /** Маг: круглая башня за северным щипцом — кабинет наверху, фиолетовые окна, шпиль с аметистом. */
    static void mageTower(Template t, HallStyle s, int z2) {
        int cx = 6, cz = z2 + 3, top = 10;
        double r = 2.6;
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = z2 + 1; z < HB_SZ; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > r) continue;
                boolean ring = d > r - 1.1;
                t.set(x, 0, z, ring ? s.plinth() : ((x + z) % 2 == 0 ? "minecraft:polished_deepslate" : "minecraft:polished_blackstone"));
                for (int y = 1; y <= top; y++) {
                    if (!ring) {
                        t.set(x, y, z, y == 7 ? "minecraft:" + s.wood() + "_planks" : AIR);
                        continue;
                    }
                    boolean window = (x == cx || z == cz) && (y == 3 || y == 4 || y == 8 || y == 9);
                    String block = y == 1 || y == 6 || y == top ? s.plinth() : s.stone();
                    t.set(x, y, z, window ? pane("purple_stained_glass_pane", z == cz) : block);
                }
            }
        }
        // Проход из дома в башню через северный щипец.
        for (int y = 1; y <= 2; y++) {
            t.set(cx, y, z2, AIR);
            t.set(cx, y, z2 + 1, AIR);
        }
        t.set(cx, 3, z2 + 1, s.accent());
        // Лестница в кабинет.
        for (int y = 1; y <= 7; y++) t.set(cx + 1, y, cz, "minecraft:ladder[facing=west,waterlogged=false]");
        // Шатёр-конус: кольца ступеней к оси, сверху аметист и шпиль.
        for (int level = 0; level < 6; level++) {
            double rr = 3.4 - level * 0.55;
            for (int x = cx - 4; x <= cx + 4; x++) {
                for (int z = z2 + 1; z < HB_SZ; z++) {
                    double d = Math.hypot(x - cx, z - cz);
                    if (d > rr || d <= rr - 1.2) continue;
                    int dx = x - cx, dz = z - cz;
                    String facing = Math.abs(dx) >= Math.abs(dz) ? (dx < 0 ? "east" : "west") : (dz < 0 ? "south" : "north");
                    t.set(x, top + 1 + level, z, d < 0.5 ? s.roofBlock() : st(s.roofStairs(), facing, false));
                }
            }
        }
        for (int y = top + 1; y <= top + 5; y++) t.set(cx, y, cz, s.roofBlock());
        t.set(cx, top + 6, cz, "minecraft:amethyst_block");
        t.set(cx, top + 7, cz, "minecraft:end_rod[facing=up]");
        // Нижний ярус: аметисты и свечи. Кабинет наверху: полки, стойка зелий, сундук.
        t.set(cx - 1, 1, cz, "minecraft:amethyst_block");
        t.set(cx - 1, 2, cz, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        t.set(cx, 1, cz + 1, "minecraft:purple_candle[candles=4,lit=true,waterlogged=false]");
        t.set(cx, 6, cz, "minecraft:lantern[hanging=true,waterlogged=false]");
        t.set(cx - 1, 8, cz, "minecraft:bookshelf");
        t.set(cx - 1, 9, cz, "minecraft:purple_candle[candles=3,lit=true,waterlogged=false]");
        t.set(cx, 8, cz + 1, "minecraft:brewing_stand[has_bottle_0=true,has_bottle_1=false,has_bottle_2=true]");
        t.set(cx, 8, cz - 1, "minecraft:chest[facing=south,type=single,waterlogged=false]",
                map("id", "minecraft:chest", "LootTable", MODID + ":chests/mage_guild"));
        t.set(cx, 8, cz, "minecraft:purple_carpet");
        t.set(cx, top, cz, "minecraft:lantern[hanging=true,waterlogged=false]");
        // Дом мага: полки и аметисты у южной стены, фиолетовые витражи.
        t.set(4, 1, HB_Z1 + 1, "minecraft:bookshelf");
        t.set(5, 1, HB_Z1 + 1, "minecraft:bookshelf");
        t.set(4, 2, HB_Z1 + 1, "minecraft:purple_candle[candles=3,lit=true,waterlogged=false]");
        t.set(5, 2, HB_Z1 + 1, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        for (int y = 2; y <= 3; y++) t.set(6, y, HB_Z1, pane("purple_stained_glass_pane", true));
        // Площадка вокруг башни.
        for (int x = 1; x < HB_SX - 1; x++) {
            for (int z = z2 + 1; z < HB_SZ; z++) {
                if (Math.hypot(x - cx, z - cz) > r) t.set(x, 0, z, s.path());
            }
        }
        t.set(2, 1, HB_SZ - 1, "minecraft:lantern[hanging=false,waterlogged=false]");
        t.set(10, 1, HB_SZ - 1, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        t.set(10, 0, HB_SZ - 1, "minecraft:amethyst_block");
    }

    static void yardGround(Template t, HallStyle s, int z2) {
        Random random = new Random(s.stone().hashCode() + 7);
        for (int x = 1; x < HB_SX - 1; x++) {
            for (int z = z2 + 1; z < HB_SZ; z++) t.set(x, 0, z, random.nextInt(3) == 0 ? s.plinth() : s.path());
        }
    }

    /** Изгородь двора от углов дома, калитка со стороны улицы и знамёна на угловых столбах. */
    static void yardFence(Template t, HallStyle s, String fence, String color, int z2) {
        int x1 = 1, x2 = HB_SX - 2, back = HB_SZ - 1;
        String post = log(s.timber().replace("stripped_", ""), 'y');
        for (int z = z2 + 1; z <= back; z++) {
            t.set(x1, 1, z, fence + "[east=false,north=true,south=" + (z < back) + ",waterlogged=false,west=false]");
            t.set(x2, 1, z, fence + "[east=false,north=true,south=" + (z < back) + ",waterlogged=false,west=false]");
        }
        for (int x = x1 + 1; x < x2; x++) t.set(x, 1, back, fence + "[east=true,north=false,south=false,waterlogged=false,west=true]");
        // Угловые столбы — брёвна: знамя держится только на сплошном блоке.
        t.set(x1, 1, back, post);
        t.set(x2, 1, back, post);
        t.set(x1, 1, z2 + 1, fence.replace("_fence", "_fence_gate") + "[facing=west,in_wall=false,open=false,powered=false]");
        t.set(x1, 2, back, "minecraft:" + color + "_banner[rotation=0]");
        t.set(x2, 2, back, "minecraft:" + color + "_banner[rotation=0]");
    }

    /** Шаблон 1x3x1 с наставником, как ванильный village/plains/villagers/unemployed. */
    static Template buildMentor(Biome b, Guild g) {
        Template t = new Template(1, 3, 1);
        t.set(0, 0, 0, "minecraft:jigsaw[orientation=down_south]",
                jigsaw("minecraft:bottom", "minecraft:empty", "minecraft:structure_void", "rollable"));
        t.set(0, 1, 0, "minecraft:air");
        t.set(0, 2, 0, "minecraft:air");
        Map<String, Object> villager = map(
                "id", "minecraft:villager",
                "VillagerData", map("profession", MODID + ":" + g.profession(), "level", 1, "type", b.villagerType()),
                // Опыт больше нуля: такой житель не теряет профессию, даже если рабочее место ещё не занято.
                "Xp", 1);
        t.entities.add(map("blockPos", List.of(0, 1, 0), "pos", List.of(0.5, 1.0, 0.5), "nbt", villager));
        return t;
    }

    static String mentorPool(Biome b, Guild g) {
        return """
                {
                  "fallback": "minecraft:empty",
                  "elements": [
                    {
                      "weight": 1,
                      "element": {
                        "element_type": "minecraft:single_pool_element",
                        "location": "%s:village/%s/%s_mentor",
                        "processors": "minecraft:empty",
                        "projection": "rigid"
                      }
                    }
                  ]
                }
                """.formatted(MODID, b.name(), g.profession());
    }

    // ---------------------------------------------------------------- блоки

    static String stairs(Biome b, String facing) {
        return b.roofStairs() + "[facing=" + facing + ",half=bottom,shape=straight,waterlogged=false]";
    }

    /** Стекло в стене: alongX — стена тянется вдоль оси X (северная/южная), иначе вдоль Z. */
    static String pane(String block, boolean alongX) {
        return "minecraft:" + block + (alongX
                ? "[east=true,north=false,south=false,waterlogged=false,west=true]"
                : "[east=false,north=true,south=true,waterlogged=false,west=false]");
    }

    static Map<String, Object> jigsaw(String name, String pool, String finalState, String joint) {
        return map("id", "minecraft:jigsaw", "name", name, "target", name, "pool", pool,
                "final_state", finalState, "joint", joint);
    }

    static Map<String, Object> map(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) m.put((String) keyValues[i], keyValues[i + 1]);
        return m;
    }

    static Map<String, Object> parseState(String state) {
        Map<String, Object> result = new LinkedHashMap<>();
        int bracket = state.indexOf('[');
        if (bracket < 0) {
            result.put("Name", state);
            return result;
        }
        result.put("Name", state.substring(0, bracket));
        Map<String, Object> properties = new LinkedHashMap<>();
        for (String pair : state.substring(bracket + 1, state.length() - 1).split(",")) {
            String[] kv = pair.split("=");
            properties.put(kv[0], kv[1]);
        }
        result.put("Properties", properties);
        return result;
    }

    static final class Template {
        final int sx, sy, sz;
        final Map<String, Object[]> blocks = new LinkedHashMap<>();
        final List<Map<String, Object>> entities = new ArrayList<>();

        Template(int sx, int sy, int sz) {
            this.sx = sx;
            this.sy = sy;
            this.sz = sz;
        }

        void set(int x, int y, int z, String state) {
            set(x, y, z, state, null);
        }

        void set(int x, int y, int z, String state, Map<String, Object> nbt) {
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
                throw new IllegalArgumentException("Block outside template: " + x + "," + y + "," + z + " " + state);
            }
            blocks.put(x + "," + y + "," + z, new Object[]{x, y, z, state, nbt});
        }

        void fill(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
            for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) set(x, y, z, state);
        }

        Map<String, Object> toNbt() {
            List<String> paletteStates = new ArrayList<>();
            Map<String, Integer> index = new HashMap<>();
            List<Object> blockList = new ArrayList<>();
            for (Object[] b : blocks.values()) {
                int state = index.computeIfAbsent((String) b[3], s -> {
                    paletteStates.add(s);
                    return paletteStates.size() - 1;
                });
                Map<String, Object> entry = map("pos", List.of(b[0], b[1], b[2]), "state", state);
                if (b[4] != null) entry.put("nbt", b[4]);
                blockList.add(entry);
            }
            List<Object> palette = new ArrayList<>();
            for (String s : paletteStates) palette.add(parseState(s));
            return map("size", List.of(sx, sy, sz), "palette", palette, "blocks", blockList,
                    "entities", new ArrayList<>(entities), "DataVersion", DATA_VERSION);
        }
    }

    // ---------------------------------------------------------------- NBT

    static void write(Path path, Map<String, Object> root) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStream file = Files.newOutputStream(path);
             DataOutputStream out = new DataOutputStream(new GZIPOutputStream(file))) {
            out.writeByte(10);
            out.writeUTF("");
            writePayload(out, root);
        }
    }

    static void writeText(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    static byte typeOf(Object value) {
        if (value instanceof Byte) return 1;
        if (value instanceof Short) return 2;
        if (value instanceof Integer) return 3;
        if (value instanceof Long) return 4;
        if (value instanceof Float) return 5;
        if (value instanceof Double) return 6;
        if (value instanceof String) return 8;
        if (value instanceof List) return 9;
        if (value instanceof Map) return 10;
        throw new IllegalArgumentException("Unsupported NBT value: " + value);
    }

    @SuppressWarnings("unchecked")
    static void writePayload(DataOutputStream out, Object value) throws IOException {
        switch (typeOf(value)) {
            case 1 -> out.writeByte((Byte) value);
            case 2 -> out.writeShort((Short) value);
            case 3 -> out.writeInt((Integer) value);
            case 4 -> out.writeLong((Long) value);
            case 5 -> out.writeFloat((Float) value);
            case 6 -> out.writeDouble((Double) value);
            case 8 -> out.writeUTF((String) value);
            case 9 -> {
                List<Object> list = (List<Object>) value;
                out.writeByte(list.isEmpty() ? 0 : typeOf(list.get(0)));
                out.writeInt(list.size());
                for (Object element : list) writePayload(out, element);
            }
            case 10 -> {
                for (Map.Entry<String, Object> entry : ((Map<String, Object>) value).entrySet()) {
                    out.writeByte(typeOf(entry.getValue()));
                    out.writeUTF(entry.getKey());
                    writePayload(out, entry.getValue());
                }
                out.writeByte(0);
            }
            default -> throw new IllegalStateException();
        }
    }
}
