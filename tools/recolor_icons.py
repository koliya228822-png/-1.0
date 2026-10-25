"""
Плоские иконки (textures/item, 128×128) оружия новых грейдов перекрашиваются из готовых иконок того же оружия:
серый металл — в металл грейда, золото и латунь — в его отделку, красный камень — в его камень.
Запуск: python tools/recolor_icons.py src/main/resources
"""
import colorsys, os, sys
from PIL import Image

PAL = {
    "iron": ["24262E", "5A5F6C", "969CA8", "D0D5DE", "FFFFFF"],
    "steel": ["161E2C", "3C4A64", "6E86A8", "B2C4DE", "F2F8FF"],
    "diamond": ["08383A", "168884", "3CC8C0", "9AF2EC", "F2FFFF"],
    "faceted": ["0A2448", "1E5A9A", "4AA0E0", "B4E4FF", "FFFFFF"],
    "brilliant": ["3A3A52", "8A8EB0", "D0D6F0", "F4F6FF", "FFFFFF"],
}
TRIM = {
    "iron": ["3A2A0E", "6E5222", "A8823E", "D8B46A", "F8E6B0"],
    "steel": ["101218", "262A36", "444A5C", "6E7690", "A8B2CC"],
    "diamond": ["3A2606", "7A5414", "C08A2C", "ECC462", "FFF2B8"],
    "faceted": ["2A2E36", "6A707C", "AEB4C0", "E2E6EE", "FFFFFF"],
    "brilliant": ["3A2606", "7A5414", "C08A2C", "ECC462", "FFF2B8"],
}
GEM = {
    "iron": ["5A2A04", "C87A1A", "FFD27A"],
    "steel": ["0C1C5A", "2A5AD8", "A8C4FF"],
    "diamond": ["0A4A56", "1AA8C0", "B4FFFF"],
    "faceted": ["0C1C5A", "2A5AD8", "A8C4FF"],
    "brilliant": ["3A2A6A", "B8A8FF", "FFFFFF"],
}


def rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def ramp(stops, t):
    t = min(1.0, max(0.0, t))
    cols = [rgb(c) for c in stops]
    x = t * (len(cols) - 1)
    i = min(int(x), len(cols) - 2)
    f = x - i
    return tuple(round(cols[i][k] + (cols[i + 1][k] - cols[i][k]) * f) for k in range(3))


def recolor(src, mat, gems=True):
    img = src.convert("RGBA")
    px = img.load()
    w, h = img.size
    metal, trim = [], []
    kinds = {}
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            hh, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            deg = hh * 360
            if s < 0.22 or (l > 0.82 and s < 0.5):
                kinds[(x, y)] = "metal"; metal.append(l)
            elif 30 <= deg <= 62 and s > 0.35 and l > 0.25:
                kinds[(x, y)] = "trim"; trim.append(l)
            elif gems and (deg < 14 or deg > 335) and s > 0.4:
                kinds[(x, y)] = "gem"
    def norm(vals):
        if not vals:
            return 0, 1
        vals = sorted(vals)
        lo, hi = vals[int(len(vals) * 0.02)], vals[int(len(vals) * 0.98) - 1]
        return lo, max(hi, lo + 0.05)
    mlo, mhi = norm(metal)
    tlo, thi = norm(trim)
    for (x, y), k in kinds.items():
        r, g, b, a = px[x, y]
        l = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)[1]
        if k == "metal":
            c = ramp(PAL[mat], 0.05 + 0.95 * (l - mlo) / (mhi - mlo))
        elif k == "trim":
            c = ramp(TRIM[mat], 0.1 + 0.9 * (l - tlo) / (thi - tlo))
        else:
            c = ramp(GEM[mat], l * 1.4)
        px[x, y] = (*c, a)
    return img


def main(res):
    tex = os.path.join(res, "assets/heroicpaths/textures/item")
    jobs = [("steel_sword", "iron_sword", "iron", True), ("musket", "steel_musket", "steel", True),
            ("musket", "damascus_musket", None, True)]
    melee = ["sword", "axe", "dagger", "greatsword", "rapier", "spear", "flanged_mace", "warhammer"]
    for mat in ("diamond", "faceted", "brilliant"):
        for wpn in melee:
            jobs.append(("damascus_" + wpn, mat + "_" + wpn, mat, True))
        for sfx in ("", "_arrow", "_firework", "_pulling_0", "_pulling_1", "_pulling_2"):
            jobs.append(("damascus_hand_crossbow" + sfx, mat + "_hand_crossbow" + sfx, mat, True))
        for sfx in ("", "_pulling_0", "_pulling_1", "_pulling_2"):
            jobs.append(("composite_bow" + sfx, mat + "_longbow" + sfx, mat, True))
        jobs.append(("musket", mat + "_musket", mat, True))
        for el in ("fire", "frost", "earth", "storm"):
            for kind in ("wand", "staff"):
                jobs.append((f"{el}_archmage_{kind}", f"{el}_{mat}_{kind}", mat, False))
    # Алмазные кинжал и двуручный меч уже были в моде со своими иконками.
    jobs = [j for j in jobs if j[1] not in ("diamond_dagger", "diamond_greatsword")]
    for src, dst, mat, gems in jobs:
        out = os.path.join(tex, dst + ".png")
        img = Image.open(os.path.join(tex, src + ".png"))
        if mat is None:
            # Дамасский мушкет: тёмный узорчатый металл и золото.
            img = recolor(img, "steel", gems)
            pal = img.load()
            for y in range(img.size[1]):
                for x in range(img.size[0]):
                    r, g, b, a = pal[x, y]
                    if a and abs(r - g) < 40 and abs(g - b) < 50:
                        pal[x, y] = (int(r * 0.6), int(g * 0.6), int(b * 0.68), a)
        else:
            img = recolor(img, mat, gems)
        img.save(out)
        print(dst)


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/main/resources")
