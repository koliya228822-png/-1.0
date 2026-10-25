"""
Плоские иконки 16×16 алмазного тира: гранёный алмаз, бриллиант, заготовки брони и оружия трёх грейдов,
трактат ювелира, гранильный стол и стол ювелира.
Запуск: python tools/diamond_icons.py src/main/resources
"""
import colorsys, os, sys
from PIL import Image

RAMPS = {
    "diamond": ["0E3E3C", "1E8C86", "3CC8C0", "9AF2EC", "F2FFFF"],
    "faceted": ["0C2650", "1E5A9A", "4AA0E0", "B4E4FF", "FFFFFF"],
    "brilliant": ["4A4A64", "8A8EB0", "C8CEEC", "EEF0FF", "FFFFFF"],
}


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def draw(rows, colors):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colors:
                img.putpixel((x, y), colors[ch])
    return img


# Силуэты заготовок (как ванильные алмазные вещи, но грубые: без деталей, со следами ковки).
# o — контур, d — тень, m — основной тон, l — блик, h — яркий блик.
HELMET = [
    "................",
    "................",
    "................",
    "....oooooooo....",
    "...ohhllllmmo...",
    "..ohlllmmmmmdo..",
    "..olmmmmmmmmdo..",
    "..olmmmmmmmmdo..",
    "..olmmooooomdo..",
    "..olmo....omdo..",
    "..olmo....omdo..",
    "..oddo....oddo..",
    "...oo......oo...",
    "................",
    "................",
    "................",
]
CHESTPLATE = [
    "................",
    "..ooo......ooo..",
    ".ohlmo....omldo.",
    ".olmmmooooommdo.",
    ".olmmmmmmmmmmdo.",
    "..ooolmmmmmdoo..",
    "....olmmmmmdo...",
    "....olmmmmmdo...",
    "....olmmmmmdo...",
    "....olmmmmmdo...",
    "....olmmmmmdo...",
    "....olmmmmmdo...",
    "....oddddddoo...",
    ".....oooooo.....",
    "................",
    "................",
]
LEGGINGS = [
    "................",
    "................",
    "....oooooooo....",
    "....ohllmmmdo...",
    "....olmmmmmdo...",
    "....olmmoommdo..",
    "....olmo..omdo..",
    "....olmo..omdo..",
    "....olmo..omdo..",
    "....olmo..omdo..",
    "....olmo..omdo..",
    "....oddo..oddo..",
    "....oooo..oooo..",
    "................",
    "................",
    "................",
]
BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "...oooo..oooo...",
    "...olmo..olmo...",
    "...olmo..olmo...",
    "...olmo..olmo...",
    "..oolmo..olmoo..",
    ".ohlmmo..olmmdo.",
    ".oddddo..oddddo.",
    ".oooooo..oooooo.",
    "................",
    "................",
    "................",
]
WEAPON = [
    "................",
    "............ooo.",
    "...........ohlo.",
    "..........ohlmo.",
    ".........ohlmo..",
    "........ohlmo...",
    ".......ohlmo....",
    "......ohlmo.....",
    "..oo.ohlmo......",
    "..odoolmo.......",
    "...oddmo........",
    "...owddo........",
    "..owwoodo.......",
    ".owwo..oo.......",
    ".ooo............",
    "................",
]
FACETED = [
    "................",
    "................",
    "................",
    "....oooooooo....",
    "...ohhlhlmlmo...",
    "..ohlhlmlmmmdo..",
    ".ommmmmmmmmmmdo.",
    ".odlmlmmmmmlmdo.",
    "..odlmmmmmmmdo..",
    "...odlmmmmmdo...",
    "....odlmmmdo....",
    ".....odmmdo.....",
    "......oddo......",
    ".......oo.......",
    "................",
    "................",
]
BRILLIANT = [
    "................",
    "................",
    ".....oooooo.....",
    "....ohhlhllo....",
    "...ohlhlmlmlo...",
    "..ohlhhlmlmmdo..",
    "..olllmlmmmmdo..",
    "..ommhmmlmmldo..",
    "..olmmlmmmhmdo..",
    "..odmmmlmmmddo..",
    "...odmlmmmddo...",
    "....oddmmddo....",
    ".....oooooo.....",
    "................",
    "................",
    "................",
]


def palette(name):
    r = RAMPS[name]
    return {"o": rgb(r[0]), "d": rgb(r[1]), "m": rgb(r[2]), "l": rgb(r[3]), "h": rgb(r[4]),
            "w": rgb("5A3A1E")}


def tint(img, hue_deg, sat=None, light=0.0):
    img = img.convert("RGBA")
    px = img.load()
    for y in range(img.size[1]):
        for x in range(img.size[0]):
            r, g, b, a = px[x, y]
            if not a:
                continue
            h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            if s < 0.12:
                continue
            nr, ng, nb = colorsys.hls_to_rgb(hue_deg / 360, min(1, max(0, l + light)), s if sat is None else sat)
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
    return img


def block_textures(tex):
    stone = [rgb("4A4E58"), rgb("5E636E"), rgb("72777F"), rgb("3A3D45")]
    wood = [rgb("2E1C10"), rgb("4A2E1A"), rgb("5E3C22"), rgb("24160C")]
    gold, gold_l = rgb("C8962C"), rgb("F0D070")
    gem, gem_l = rgb("3CC8C0"), rgb("D8FFFC")

    def base(cols, seed):
        img = Image.new("RGBA", (16, 16))
        for y in range(16):
            for x in range(16):
                v = (x * 7 + y * 13 + seed * 5 + (x * y) % 5) % 9
                img.putpixel((x, y), cols[0] if v < 3 else cols[1] if v < 7 else cols[2])
        return img

    def frame(img, col):
        for i in range(16):
            for p in ((i, 0), (i, 15), (0, i), (15, i)):
                img.putpixel(p, col)

    # Гранильный стол: каменный, сверху точильный круг с алмазом, спереди — круг и рукоять.
    top = base(stone, 1); frame(top, stone[3])
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 6: top.putpixel((x, y), rgb("9A9EA6") if d < 5 else rgb("6A6E76"))
    for p in ((7, 6), (8, 6), (6, 7), (9, 7), (7, 8), (8, 8), (7, 7)):
        top.putpixel(p, gem)
    top.putpixel((8, 7), gem_l)
    side = base(stone, 2); frame(side, stone[3])
    for x in range(1, 15): side.putpixel((x, 4), stone[3])
    front = side.copy()
    for y in range(6, 15):
        for x in range(3, 13):
            d = ((x - 7.5) ** 2 + (y - 10) ** 2) ** 0.5
            if d < 4.6: front.putpixel((x, y), rgb("9A9EA6") if d < 3.6 else rgb("5A5E66"))
    front.putpixel((7, 10), rgb("2A2C30")); front.putpixel((8, 10), rgb("2A2C30"))
    for x in range(9, 14): front.putpixel((x, 7), wood[1])
    front.putpixel((13, 8), wood[2]); front.putpixel((13, 9), wood[2])
    for name, img in (("gem_table_top", top), ("gem_table_side", side), ("gem_table_front", front)):
        img.save(os.path.join(tex, "block", name + ".png"))

    # Стол ювелира: тёмное дерево, сверху бархатная подушка с кольцом, спереди ящички с золотыми ручками.
    top = base(wood, 3); frame(top, wood[3])
    for y in range(3, 13):
        for x in range(3, 13):
            top.putpixel((x, y), rgb("5A1E6A") if (x + y) % 3 else rgb("6E2A80"))
    for p in ((6, 6), (7, 5), (8, 5), (9, 6), (9, 7), (8, 8), (7, 8), (6, 7)):
        top.putpixel(p, gold)
    top.putpixel((7, 5), gold_l); top.putpixel((8, 4), gem); top.putpixel((7, 4), gem_l)
    side = base(wood, 4); frame(side, wood[3])
    for x in range(1, 15): side.putpixel((x, 3), wood[3])
    front = side.copy()
    for row in (5, 10):
        for y in range(row, row + 4):
            for x in range(2, 14):
                front.putpixel((x, y), wood[3] if y in (row, row + 3) or x in (2, 13) else wood[2])
        front.putpixel((7, row + 2), gold); front.putpixel((8, row + 2), gold_l)
    for name, img in (("jeweler_table_top", top), ("jeweler_table_side", side), ("jeweler_table_front", front)):
        img.save(os.path.join(tex, "block", name + ".png"))


# Аксессуары: o — контур, d/m/l — тени и тона материала, g/G — отделка (золото, латунь), s — камень.
GLOVES = [
    "................",
    "................",
    "...o.o.o........",
    "..olololo.......",
    "..olmlmlmo......",
    "..olmmmmmo.oo...",
    "..olmmmmmoolmo..",
    "..olmmmmmmlmdo..",
    "..olmmmmmmmdo...",
    "..odmmmmmmdo....",
    "...odddddddo....",
    "...oggggggo.....",
    "...oGGGGGGo.....",
    "....oooooo......",
    "................",
    "................",
]
BELT = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "oooooooooooooooo",
    "lmmmmmogggommmml",
    "mmmmmmog.goddddm",
    "ddddddogggoddddd",
    "oooooooooooooooo",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]
BRACERS = [
    "................",
    "................",
    "................",
    "..oooooo........",
    ".ogglllmo.......",
    ".olmmmmmo.......",
    ".olmmmmmo.......",
    ".olmmmmmo.oooooo",
    ".olmmmmmooggllmo",
    ".oggmmddoolmmmmo",
    "..oooooo.olmmmmo",
    ".........olmmmmo",
    ".........oggmmdo",
    "..........oooooo",
    "................",
    "................",
]
NECKLACE = [
    "................",
    "...oo......oo...",
    "..og.o....o.go..",
    "..og..o..o..go..",
    "...og.o..o.go...",
    "...og..oo..go...",
    "....og....go....",
    "....og....go....",
    ".....og..go.....",
    "......oGGo......",
    ".....oslsso.....",
    ".....ossmso.....",
    "......osso......",
    ".......oo.......",
    "................",
    "................",
]
EARRING = [
    "................",
    "................",
    "......ooo.......",
    ".....og.go......",
    ".....og.go......",
    "......ogo.......",
    ".......o........",
    ".......G........",
    "......oGo.......",
    ".....oslso......",
    ".....osmso......",
    "......oso.......",
    ".......o........",
    "................",
    "................",
    "................",
]
RING = [
    "................",
    "................",
    "................",
    "......oooo......",
    ".....oslsso.....",
    "......osso......",
    ".....oGooGo.....",
    "....oGo..oGo....",
    "....og....go....",
    "....og....go....",
    "....oGo..oGo....",
    ".....oGooGo.....",
    "......oooo......",
    "................",
    "................",
    "................",
]


def accessory_palette(dark, mid, light, outline="1A1410"):
    return {"o": rgb(outline), "d": rgb(dark), "m": rgb(mid), "l": rgb(light),
            "g": rgb("C8962C"), "G": rgb("8A6420"), "s": rgb("3CC8C0")}


def main(res):
    tex = os.path.join(res, "assets/heroicpaths/textures")
    item = os.path.join(tex, "item")
    draw(FACETED, palette("faceted")).save(os.path.join(item, "faceted_diamond.png"))
    draw(BRILLIANT, palette("brilliant")).save(os.path.join(item, "brilliant.png"))
    for mat in RAMPS:
        for name, rows in (("helmet", HELMET), ("chestplate", CHESTPLATE), ("leggings", LEGGINGS), ("boots", BOOTS), ("weapon", WEAPON)):
            draw(rows, palette(mat)).save(os.path.join(item, f"{mat}_{name}_blank.png"))
    tint(Image.open(os.path.join(item, "blacksmith_manual.png")), 285, 0.45).save(os.path.join(item, "jeweler_manual.png"))
    block_textures(tex)
    kinds = {"gloves": GLOVES, "belt": BELT, "bracers": BRACERS}
    for name, pal in (("plate", accessory_palette("4A505C", "8A92A0", "D0D6E0")),
                      ("leather", accessory_palette("4A2C14", "7C4E24", "A87444")),
                      ("cloth", accessory_palette("2C1848", "5E3C8C", "8060B0"))):
        for kind, rows in kinds.items():
            draw(rows, pal).save(os.path.join(item, f"{name}_{kind}.png"))
    jewel = accessory_palette("8A6420", "C8962C", "F0D070", "3A2606")
    jewel["s"] = rgb("C01A2C")
    for kind, rows in (("necklace", NECKLACE), ("earring", EARRING), ("ring", RING)):
        draw(rows, jewel).save(os.path.join(item, f"{kind}.png"))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources")
