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
        // Гильдия искателей приключений: доски заказов и распорядитель гильдии.
        Guild adventurers = new Guild("adventurers_guild", "guildmaster", MODID + ":order_board", "orange");
        for (Biome biome : BIOMES) {
            write(structures.resolve(biome.name() + "/adventurers_guild.nbt"), buildAdventurersGuild(biome).toNbt());
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
            Map.entry("minecraft:zombie", MODID + ":ice_golem"),
            Map.entry("minecraft:skeleton", MODID + ":frozen_archer"),
            Map.entry("minecraft:cave_spider", MODID + ":ice_golem"),
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

    /**
     * Центр деревни с вратами в данжи: площадь 15×15, колокол, доска заданий,
     * рамка портала с алтарём, фонари. Улицы и жители подключаются как к ванильному центру.
     */
    static Template townPlaza(Biome b) {
        Template t = new Template(15, 8, 15);
        String ground = switch (b.name()) {
            case "desert" -> "minecraft:cut_sandstone";
            case "snowy" -> "minecraft:snow_block";
            default -> "minecraft:dirt_path";
        };
        String fence = switch (b.name()) {
            case "desert" -> "minecraft:jungle_fence";
            case "savanna" -> "minecraft:acacia_fence";
            case "snowy", "taiga" -> "minecraft:spruce_fence";
            default -> "minecraft:oak_fence";
        } + "[east=false,north=false,south=false,waterlogged=false,west=false]";

        for (int x = 0; x <= 14; x++) {
            for (int z = 0; z <= 14; z++) {
                boolean edge = x == 0 || x == 14 || z == 0 || z == 14;
                t.set(x, 0, z, edge ? b.foundation() : ground);
                for (int y = 1; y <= 7; y++) t.set(x, y, z, "minecraft:air");
            }
        }
        // Рамка врат: проём 3×4 в 2 блоках перед алтарём (алтарь смотрит на юг, на рамку).
        for (int x = 5; x <= 9; x++) {
            t.set(x, 0, 11, b.foundation());
            for (int y = 1; y <= 5; y++) {
                boolean frame = x == 5 || x == 9 || y == 5;
                if (frame) t.set(x, y, 11, (y == 5 && (x == 5 || x == 9)) ? "minecraft:crying_obsidian" : "minecraft:obsidian");
            }
        }
        t.set(7, 1, 9, MODID + ":gate_altar[facing=south]");
        t.set(7, 1, 4, "minecraft:bell[attachment=floor,facing=north,powered=false]");
        t.set(3, 1, 4, MODID + ":quest_board[facing=south]");
        t.set(11, 1, 4, MODID + ":quest_board[facing=south]");
        for (int[] p : new int[][]{{1, 1}, {13, 1}, {1, 13}, {13, 13}}) {
            t.set(p[0], 1, p[1], fence);
            t.set(p[0], 2, p[1], fence);
            t.set(p[0], 3, p[1], "minecraft:lantern[hanging=false,waterlogged=false]");
        }

        Map<String, Object> street = map("id", "minecraft:jigsaw", "name", "minecraft:street", "target", "minecraft:street",
                "pool", "minecraft:village/" + b.name() + "/streets", "final_state", "minecraft:structure_void", "joint", "aligned");
        t.set(7, 1, 0, "minecraft:jigsaw[orientation=north_up]", street);
        t.set(7, 1, 14, "minecraft:jigsaw[orientation=south_up]", street);
        t.set(0, 1, 7, "minecraft:jigsaw[orientation=west_up]", street);
        t.set(14, 1, 7, "minecraft:jigsaw[orientation=east_up]", street);

        t.set(11, 0, 7, "minecraft:jigsaw[orientation=up_north]", jigsaw("minecraft:bottom", "minecraft:village/common/iron_golem", ground, "rollable"));
        t.set(3, 0, 9, "minecraft:jigsaw[orientation=up_north]", jigsaw("minecraft:bottom", "minecraft:village/common/cats", ground, "rollable"));
        t.set(4, 0, 6, "minecraft:jigsaw[orientation=up_north]", jigsaw("minecraft:bottom", "minecraft:village/" + b.name() + "/villagers", ground, "rollable"));
        t.set(10, 0, 9, "minecraft:jigsaw[orientation=up_north]", jigsaw("minecraft:bottom", "minecraft:village/" + b.name() + "/villagers", ground, "rollable"));
        return t;
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

    // ---------------------------------------------------------------- здания

    /** Зал 7x9 (x = 1..7, z = 0..8); x = 0 — крыльцо с jigsaw-входом. Глубина 8 помещается на участки пустынных улиц. */
    static final int HALL_MAX_X = 7;

    static Template buildGuild(Biome b, Guild g) {
        int height = switch (g.name()) {
            case "swordmaster_guild" -> 7;
            case "archer_guild" -> 8;
            default -> 15;
        };
        Template t = new Template(HALL_MAX_X + 1, height, 9);

        // Фундамент по периметру, пол внутри.
        for (int x = 1; x <= HALL_MAX_X; x++) {
            for (int z = 0; z <= 8; z++) {
                boolean edge = x == 1 || x == HALL_MAX_X || z == 0 || z == 8;
                t.set(x, 0, z, edge ? b.foundation() : b.floor());
            }
        }
        // Стены 4 блока высотой, углы из брёвен, внутри воздух (вычищает рельеф).
        for (int y = 1; y <= 4; y++) {
            for (int x = 1; x <= HALL_MAX_X; x++) {
                for (int z = 0; z <= 8; z++) {
                    boolean edgeX = x == 1 || x == HALL_MAX_X;
                    boolean edgeZ = z == 0 || z == 8;
                    if (edgeX && edgeZ) t.set(x, y, z, b.corner());
                    else if (edgeX || edgeZ) t.set(x, y, z, b.wall());
                    else t.set(x, y, z, "minecraft:air");
                }
            }
        }
        // Окна.
        for (int y = 2; y <= 3; y++) {
            for (int z : new int[]{2, 6}) t.set(1, y, z, pane("glass_pane", false));
            for (int z : new int[]{2, 4, 6}) t.set(HALL_MAX_X, y, z, pane("glass_pane", false));
            for (int x : new int[]{3, 5}) {
                t.set(x, y, 0, pane("glass_pane", true));
                t.set(x, y, 8, pane("glass_pane", true));
            }
        }
        // Дверь на западной стене и jigsaw-вход перед ней. Если пол выше улицы (вход на уровне пола),
        // jigsaw превращается в ступеньку крыльца, как у ванильных домов равнин.
        t.set(1, 1, 4, b.door() + "[facing=east,half=lower,hinge=right,open=false,powered=false]");
        t.set(1, 2, 4, b.door() + "[facing=east,half=upper,hinge=right,open=false,powered=false]");
        String doorstep = b.entranceY() == 0 ? stairs(b, "east") : "minecraft:structure_void";
        t.set(0, b.entranceY(), 4, "minecraft:jigsaw[orientation=west_up]",
                jigsaw("minecraft:building_entrance", "minecraft:empty", doorstep, "aligned"));

        // Общее убранство: рабочее место наставника, сундук, кровать, дорожка, фонари, знамёна.
        t.set(6, 1, 4, g.workstation());
        t.set(2, 1, 1, "minecraft:chest[facing=south,type=single,waterlogged=false]",
                map("id", "minecraft:chest", "LootTable", MODID + ":chests/" + g.name()));
        t.set(2, 1, 7, "minecraft:" + g.color() + "_bed[facing=east,occupied=false,part=foot]");
        t.set(3, 1, 7, "minecraft:" + g.color() + "_bed[facing=east,occupied=false,part=head]");
        for (int x = 2; x <= 5; x++) t.set(x, 1, 4, "minecraft:" + g.color() + "_carpet");
        for (int[] p : new int[][]{{3, 2}, {3, 6}, {5, 2}, {5, 6}}) {
            t.set(p[0], 4, p[1], "minecraft:lantern[hanging=true,waterlogged=false]");
        }
        t.set(6, 3, 3, "minecraft:" + g.color() + "_wall_banner[facing=west]");
        t.set(6, 3, 5, "minecraft:" + g.color() + "_wall_banner[facing=west]");
        // Доска заданий гильдии у южной стены.
        t.set(5, 1, 7, MODID + ":quest_board[facing=north]");

        // Jigsaw в полу: к нему крепится шаблон с наставником (житель с нужной профессией).
        t.set(4, 0, 6, "minecraft:jigsaw[orientation=up_north]",
                jigsaw("minecraft:bottom", MODID + ":village/" + b.name() + "/" + g.profession() + "_mentor", b.floor(), "rollable"));

        switch (g.name()) {
            case "swordmaster_guild" -> decorateBarracks(t, b);
            case "archer_guild" -> decorateArcheryHall(t, b);
            default -> decorateMageTower(t, b);
        }
        return t;
    }

    /**
     * Гильдия искателей приключений: зал 7x9 с высокими стенами и двускатной крышей. Внутри две доски заказов,
     * доска заданий (контракты на данжи), стойка распорядителя, сундук с припасами и трофеи.
     */
    static Template buildAdventurersGuild(Biome b) {
        int wallTop = 5;
        Template t = new Template(HALL_MAX_X + 1, 10, 9);
        for (int x = 1; x <= HALL_MAX_X; x++) {
            for (int z = 0; z <= 8; z++) {
                boolean edge = x == 1 || x == HALL_MAX_X || z == 0 || z == 8;
                t.set(x, 0, z, edge ? b.foundation() : b.floor());
            }
        }
        for (int y = 1; y <= wallTop; y++) {
            for (int x = 1; x <= HALL_MAX_X; x++) {
                for (int z = 0; z <= 8; z++) {
                    boolean edgeX = x == 1 || x == HALL_MAX_X;
                    boolean edgeZ = z == 0 || z == 8;
                    if (edgeX && edgeZ) t.set(x, y, z, b.corner());
                    else if (edgeX || edgeZ) t.set(x, y, z, b.wall());
                    else t.set(x, y, z, "minecraft:air");
                }
            }
        }
        for (int y = 2; y <= 3; y++) {
            for (int z : new int[]{2, 6}) t.set(1, y, z, pane("glass_pane", false));
            for (int x : new int[]{3, 5}) {
                t.set(x, y, 0, pane("glass_pane", true));
                t.set(x, y, 8, pane("glass_pane", true));
            }
        }
        // Двускатная крыша на высоте стен + 1.
        for (int z = 0; z <= 8; z++) {
            t.set(1, wallTop + 1, z, stairs(b, "east"));
            t.set(HALL_MAX_X, wallTop + 1, z, stairs(b, "west"));
            for (int x = 2; x < HALL_MAX_X; x++) t.set(x, wallTop + 1, z, b.roofBlock());
            t.set(2, wallTop + 2, z, stairs(b, "east"));
            t.set(6, wallTop + 2, z, stairs(b, "west"));
            for (int x = 3; x <= 5; x++) t.set(x, wallTop + 2, z, (z == 0 || z == 8) ? b.wall() : "minecraft:air");
            t.set(3, wallTop + 3, z, stairs(b, "east"));
            t.set(5, wallTop + 3, z, stairs(b, "west"));
            t.set(4, wallTop + 3, z, b.roofSlab() + "[type=bottom,waterlogged=false]");
        }
        t.set(4, wallTop + 4, 4, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]");

        // Вход с запада, над дверью — знамя гильдии.
        t.set(1, 1, 4, b.door() + "[facing=east,half=lower,hinge=right,open=false,powered=false]");
        t.set(1, 2, 4, b.door() + "[facing=east,half=upper,hinge=right,open=false,powered=false]");
        String doorstep = b.entranceY() == 0 ? stairs(b, "east") : "minecraft:structure_void";
        t.set(0, b.entranceY(), 4, "minecraft:jigsaw[orientation=west_up]",
                jigsaw("minecraft:building_entrance", "minecraft:empty", doorstep, "aligned"));
        t.set(0, wallTop, 4, "minecraft:orange_wall_banner[facing=west]");

        // Доски заказов на восточной стене, над ними знамёна, между ними фонарь.
        t.set(6, 1, 3, MODID + ":order_board[facing=west]");
        t.set(6, 1, 5, MODID + ":order_board[facing=west]");
        t.set(6, 3, 3, "minecraft:orange_wall_banner[facing=west]");
        t.set(6, 3, 5, "minecraft:orange_wall_banner[facing=west]");
        t.set(6, 2, 4, "minecraft:lantern[hanging=false,waterlogged=false]");
        t.set(6, 1, 4, "minecraft:barrel[facing=up,open=false]");
        // Контракты на данжи и трофеи у северной стены.
        t.set(4, 1, 1, MODID + ":quest_board[facing=south]");
        t.set(2, 1, 1, "minecraft:barrel[facing=up,open=false]");
        t.set(2, 2, 1, "minecraft:skeleton_skull[rotation=8]");
        t.set(6, 1, 1, "minecraft:chest[facing=south,type=single,waterlogged=false]",
                map("id", "minecraft:chest", "LootTable", MODID + ":chests/adventurers_guild"));
        // Стойка распорядителя у южной стены.
        t.set(3, 1, 7, "minecraft:lectern[facing=north,has_book=false,powered=false]");
        t.set(2, 1, 7, "minecraft:barrel[facing=up,open=false]");
        t.set(5, 1, 7, "minecraft:cartography_table");
        for (int x = 2; x <= 5; x++) t.set(x, 1, 4, "minecraft:orange_carpet");
        for (int[] p : new int[][]{{3, 2}, {3, 6}, {5, 2}, {5, 6}}) {
            t.set(p[0], wallTop, p[1], "minecraft:lantern[hanging=true,waterlogged=false]");
        }
        // Распорядитель гильдии (житель с профессией) встаёт у стойки.
        t.set(4, 0, 6, "minecraft:jigsaw[orientation=up_north]",
                jigsaw("minecraft:bottom", MODID + ":village/" + b.name() + "/guildmaster_mentor", b.floor(), "rollable"));
        return t;
    }

    /** Мечники: плоская крыша с зубцами, тренировочные манекены, наковальня. */
    static void decorateBarracks(Template t, Biome b) {
        t.fill(1, 5, 0, HALL_MAX_X, 5, 8, b.roofBlock());
        for (int x = 1; x <= HALL_MAX_X; x++) {
            for (int z = 0; z <= 8; z++) {
                boolean edge = x == 1 || x == HALL_MAX_X || z == 0 || z == 8;
                if (edge && (x + z) % 2 == 0) t.set(x, 6, z, b.foundation());
            }
        }
        for (int x : new int[]{4, 6}) {
            t.set(x, 1, 1, "minecraft:hay_block[axis=y]");
            t.set(x, 2, 1, "minecraft:carved_pumpkin[facing=south]");
        }
        t.set(6, 1, 7, "minecraft:anvil[facing=north]");
    }

    /** Лучники: двускатная крыша, мишени в стене, тюки сена. */
    static void decorateArcheryHall(Template t, Biome b) {
        for (int z = 0; z <= 8; z++) {
            t.set(1, 5, z, stairs(b, "east"));
            t.set(HALL_MAX_X, 5, z, stairs(b, "west"));
            for (int x = 2; x < HALL_MAX_X; x++) t.set(x, 5, z, b.roofBlock());
        }
        for (int z = 0; z <= 8; z++) {
            t.set(2, 6, z, stairs(b, "east"));
            t.set(6, 6, z, stairs(b, "west"));
            for (int x = 3; x <= 5; x++) t.set(x, 6, z, (z == 0 || z == 8) ? b.wall() : "minecraft:air");
            t.set(3, 7, z, stairs(b, "east"));
            t.set(5, 7, z, stairs(b, "west"));
            t.set(4, 7, z, b.roofSlab() + "[type=bottom,waterlogged=false]");
        }
        t.set(4, 2, 0, "minecraft:target[power=0]");
        t.set(4, 1, 1, "minecraft:hay_block[axis=y]");
        t.set(5, 1, 1, "minecraft:hay_block[axis=y]");
        t.set(4, 2, 1, "minecraft:hay_block[axis=y]");
        t.set(5, 2, 1, "minecraft:target[power=0]");
        t.set(6, 1, 7, "minecraft:hay_block[axis=x]");
    }

    /** Маги: башня с комнатой наверху (лестница из зала), книжные полки, свечи и аметисты. */
    static void decorateMageTower(Template t, Biome b) {
        t.fill(1, 5, 0, HALL_MAX_X, 5, 8, b.roofBlock());

        // Книжные полки вдоль северной стены и колонна для лестницы.
        t.fill(3, 1, 1, 6, 2, 1, "minecraft:bookshelf");
        t.set(3, 3, 1, "minecraft:purple_candle[candles=3,lit=true,waterlogged=false]");
        t.set(5, 3, 1, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
        t.set(6, 3, 1, "minecraft:purple_candle[candles=3,lit=true,waterlogged=false]");
        t.fill(4, 1, 2, 4, 4, 2, "minecraft:bookshelf");
        for (int y = 1; y <= 5; y++) t.set(4, y, 3, "minecraft:ladder[facing=south,waterlogged=false]");

        // Башня 5x5 над центром зала.
        for (int y = 6; y <= 10; y++) {
            for (int x = 2; x <= 6; x++) {
                for (int z = 2; z <= 6; z++) {
                    boolean edge = x == 2 || x == 6 || z == 2 || z == 6;
                    t.set(x, y, z, edge ? b.foundation() : "minecraft:air");
                }
            }
        }
        t.set(4, 8, 2, pane("purple_stained_glass_pane", true));
        t.set(4, 8, 6, pane("purple_stained_glass_pane", true));
        t.set(2, 8, 4, pane("purple_stained_glass_pane", false));
        t.set(6, 8, 4, pane("purple_stained_glass_pane", false));

        // Шатровая крыша башни со шпилем.
        pyramidRing(t, b, 11, 2, 6, 2, 6);
        pyramidRing(t, b, 12, 3, 5, 3, 5);
        t.set(4, 13, 4, "minecraft:amethyst_block");
        t.set(4, 14, 4, "minecraft:end_rod[facing=up]");

        // Комната в башне.
        t.set(4, 10, 4, "minecraft:lantern[hanging=true,waterlogged=false]");
        t.set(3, 6, 5, "minecraft:chest[facing=north,type=single,waterlogged=false]",
                map("id", "minecraft:chest", "LootTable", MODID + ":chests/mage_guild"));
        t.set(5, 6, 5, "minecraft:bookshelf");
        t.set(5, 7, 5, "minecraft:bookshelf");
        t.set(5, 6, 3, "minecraft:amethyst_cluster[facing=up,waterlogged=false]");
    }

    /** Кольцо ступенек, поднимающихся к центру, со сплошными углами и серединой. */
    static void pyramidRing(Template t, Biome b, int y, int x1, int x2, int z1, int z2) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean cornerX = x == x1 || x == x2;
                boolean cornerZ = z == z1 || z == z2;
                if (cornerX && cornerZ) t.set(x, y, z, b.roofBlock());
                else if (z == z1) t.set(x, y, z, stairs(b, "south"));
                else if (z == z2) t.set(x, y, z, stairs(b, "north"));
                else if (x == x1) t.set(x, y, z, stairs(b, "east"));
                else if (x == x2) t.set(x, y, z, stairs(b, "west"));
                else t.set(x, y, z, b.roofBlock());
            }
        }
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
