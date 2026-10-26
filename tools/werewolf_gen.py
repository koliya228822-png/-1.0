"""Оборотень: модель из кубоидов, раскладка развёрток и текстура (тёмная шерсть, светящиеся глаза, пасть).

Пишет textures/entity/werewolf/werewolf.png и werewolf_glow.png и client/werewolf/WerewolfModels.java
(LayerDefinition). Координаты — как у моделей Minecraft: y вниз, земля на y = 24, морда смотрит в −z.
Текстура в R раз подробнее развёртки. С ключом --preview ещё рисует werewolf_preview.png (вид с трёх сторон).
Запуск из корня проекта: python tools/werewolf_gen.py .
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

R = 2
TEX_W = 128

# Палитры: тень, основа, светлый ворс.
PAL = {
    "dark": [(18, 17, 19), (40, 38, 40), (66, 62, 62)],
    "mane": [(14, 13, 15), (34, 32, 35), (58, 55, 56)],
    "grey": [(56, 50, 46), (96, 88, 80), (138, 128, 116)],
    "mid": [(36, 33, 32), (66, 61, 58), (100, 93, 86)],
    "chest": [(62, 56, 50), (108, 99, 89), (150, 140, 126)],
    "hand": [(16, 15, 16), (32, 30, 31), (52, 49, 49)],
    "nose": [(10, 10, 11), (22, 21, 23), (44, 42, 44)],
    "bone": [(150, 136, 112), (210, 196, 170), (240, 232, 214)],
    "mouth": [(60, 8, 12), (112, 22, 28), (150, 44, 46)],
}
EYE = (255, 214, 96)
SCAR = (120, 22, 24)


class Box:
    def __init__(self, x, y, z, w, h, d, mat, feature="", inflate=0.0):
        self.x, self.y, self.z, self.w, self.h, self.d = x, y, z, w, h, d
        self.mat, self.feature, self.inflate = mat, feature, inflate
        self.u = self.v = 0
        self.shift = 0.0


class Part:
    def __init__(self, name, parent, px, py, pz, rx=0.0, ry=0.0, rz=0.0):
        self.name, self.parent = name, parent
        self.px, self.py, self.pz, self.rx, self.ry, self.rz = px, py, pz, rx, ry, rz
        self.boxes = []

    def box(self, *args, **kw):
        self.boxes.append(Box(*args, **kw))
        return self

    def fur(self, rnd, region, face, n, mat="mane", length=(1, 3), size=(1, 2), hang=(1, 3), where=None):
        """Клочья шерсти: n кубиков-прядей торчат из грани face кубоида region (x, y, z, w, h, d) —
        воксельный лохматый силуэт, как на эталоне. where(u, v) в долях грани отбирает места."""
        x, y, z, w, h, d = region
        placed = 0
        tries = 0
        while placed < n and tries < n * 20:
            tries += 1
            L = rnd.randint(*length)
            s1 = rnd.randint(*size)
            s2 = rnd.randint(*hang) if face not in ("top", "bottom") else rnd.randint(*size)
            fu, fv = rnd.random(), rnd.random()
            if where is not None and not where(fu, fv):
                continue
            if face in ("back", "front"):
                bx, by = x + fu * max(0, w - s1), y + fv * max(0, h - s2)
                bz = z + d - 0.5 if face == "back" else z - L + 0.5
                b = Box(bx, by, bz, s1, s2, L, mat, "tuft")
            elif face in ("left", "right"):
                bz, by = z + fu * max(0, d - s1), y + fv * max(0, h - s2)
                bx = x + w - 0.5 if face == "left" else x - L + 0.5
                b = Box(bx, by, bz, L, s2, s1, mat, "tuft")
            else:
                bx, bz = x + fu * max(0, w - s1), z + fv * max(0, d - s2)
                by = y - L + 0.5 if face == "top" else y + h - 0.5
                b = Box(bx, by, bz, s1, L, s2, mat, "tuft")
            b.shift = rnd.uniform(-0.22, 0.22)
            self.boxes.append(b)
            placed += 1
        return self


def model():
    parts = []

    def part(*args, **kw):
        p = Part(*args, **kw)
        parts.append(p)
        return p

    # Таз и сгорбленное туловище: грудь светлее, загривок — лохматая грива.
    part("hips", "root", 0, 9, 2).box(-5, -3, -3.5, 10, 5, 7, "dark", "shag") \
        .box(-2, 1.5, -3.9, 4, 5, 1, "dark", "tatter")
    part("torso", "hips", 0, -2, 0, 0.5) \
        .box(-6, -13, -4, 12, 13, 8, "dark", "chest") \
        .box(-7, -15, -1.5, 14, 6, 8, "mane", "shag", 0.25) \
        .box(-4, -17, 1, 3, 4, 4, "mane", "shag") \
        .box(1, -16.5, 2, 3, 4, 4, "mane", "shag") \
        .box(-1.5, -13, 5.5, 3, 5, 2, "mane", "shag") \
        .box(-2, -7, 3.6, 4, 5, 2, "mane", "shag")
    # Голова вытянута вперёд; пасть оскалена, нижняя челюсть — отдельная деталь.
    part("head", "torso", 0, -14, -3, -0.5) \
        .box(-5, -3, -2.5, 10, 6, 5, "mane", "shag", 0.2) \
        .box(-4, -7, -7, 8, 8, 7, "dark", "face") \
        .box(-4.5, -10, -3.5, 3, 3, 2, "dark", "ear") \
        .box(-4.5, -12, -3, 2, 2, 1, "mane") \
        .box(1.5, -10, -3.5, 3, 3, 2, "dark", "ear") \
        .box(2.5, -12, -3, 2, 2, 1, "mane") \
        .box(-2.5, -3.5, -13, 5, 4, 6, "mid", "snout") \
        .box(-1.5, -4, -13.5, 3, 2, 2, "nose") \
        .box(-2.3, -0.2, -12.6, 1, 2, 1, "bone", "fang") \
        .box(1.3, -0.2, -12.6, 1, 2, 1, "bone", "fang")
    part("jaw", "head", 0, 0, -7, 0.4) \
        .box(-2, 0, -5.5, 4, 2, 6, "mid", "jaw") \
        .box(-1.8, -1.6, -5.3, 1, 2, 1, "bone", "fang") \
        .box(0.8, -1.6, -5.3, 1, 2, 1, "bone", "fang")
    # Длинные руки с когтями до колен; на предплечьях шрамы.
    for side, sx in (("right", -1), ("left", 1)):
        part("arm_" + side, "torso", 7.5 * sx, -11, -0.5, -0.55, 0, 0.12 * sx) \
            .box(-3.5, -2.5, -3.5, 7, 5, 7, "mane", "shag", 0.2) \
            .box(-2.5, 0, -2.5, 5, 8, 5, "grey", "scars")
        part("forearm_" + side, "arm_" + side, 0, 7.5, 0, -0.3) \
            .box(-2.5, 0, -2.5, 5, 7, 5, "grey", "scars") \
            .box(-3, -0.5, 1, 6, 4, 2, "mid", "shag")
        # Кисть: тёмная ладонь, четыре согнутых пальца из двух фаланг и большой палец; когти короткие, светлые.
        part("hand_" + side, "forearm_" + side, 0, 6.5, 0, -0.15).box(-3, 0, -2.5, 6, 3, 5, "hand")
        for i in range(4):
            name = f"finger_{side}_{i}"
            part(name, "hand_" + side, -2.5 + i * 1.67, 2.5, -1.5, 0.05 - 0.08 * (i % 2), 0, 0).box(-0.5, 0, -0.5, 1, 4, 1, "hand")
            part(name + "_tip", name, 0, 3.8, 0, -0.35).box(-0.5, 0, -0.5, 1, 2, 1, "hand") \
                .box(-0.5, 1.7, -0.5, 1, 2, 1, "bone", "claw")
        inner = -sx
        part(f"thumb_{side}", "hand_" + side, 2.7 * inner, 1.2, -1.8, -0.2, 0, 0.3 * inner).box(-0.5, 0, -0.5, 1, 3, 1, "hand") \
            .box(-0.5, 2.7, -0.5, 1, 2, 1, "bone", "claw")
    # Ноги: бедро вперёд, голень назад, широкая лапа с когтями.
    for side, sx in (("right", -1), ("left", 1)):
        part("leg_" + side, "root", 3.2 * sx, 9, 2, -0.35).box(-2.5, -1, -3, 5, 9, 6, "mid", "shag")
        part("shin_" + side, "leg_" + side, 0, 7.5, -0.5, 0.75).box(-2.5, -0.5, -2.5, 5, 7, 5, "grey")
        # Широкая волчья лапа: подушка, четыре пальца и светлые когти, загнутые вниз.
        foot = part("foot_" + side, "shin_" + side, 0, 6.5, 0, -0.4).box(-3, 0, -3, 6, 2, 5, "hand") \
            .box(-2.5, -1.5, -1.5, 5, 2, 4, "grey")
        for i in range(4):
            x = -3 + i * 1.67
            foot.box(x, 0, -5, 1, 2, 2, "hand").box(x, 1, -6.5, 1, 1, 2, "bone", "clawh")
        foot.box(-0.5, 0.5, 1.6, 1, 1, 1, "bone", "clawh")
    # Пушистый хвост из трёх звеньев, висит назад и вниз.
    part("tail", "hips", 0, -1, 3.5, -0.7).box(-2, -2, 0, 4, 4, 5, "mane", "shag", 0.2)
    part("tail2", "tail", 0, 0, 4.5, -0.25).box(-2.5, -2.5, 0, 5, 5, 5, "mane", "shag", 0.3)
    part("tail3", "tail2", 0, 0, 4.5, -0.2).box(-2, -2, 0, 4, 4, 4, "mane", "shag", 0.2) \
        .box(-1, -1, 3.5, 2, 2, 3, "dark", "shag")

    # Воксельная шерсть: клочья-кубики по гриве, спине, бокам, рукам, ногам и хвосту.
    rnd = random.Random(4242)
    by = {p.name: p for p in parts}
    mane, chest = (-7, -15, -1.5, 14, 6, 8), (-6, -13, -4, 12, 13, 8)
    t = by["torso"]
    t.fur(rnd, mane, "top", 34, "mane", (1, 4)).fur(rnd, mane, "back", 22, "mane", (1, 3))
    t.fur(rnd, mane, "left", 9, "mane").fur(rnd, mane, "right", 9, "mane")
    t.fur(rnd, chest, "back", 32, "dark", (1, 3)).fur(rnd, chest, "left", 12, "dark", (1, 2)).fur(rnd, chest, "right", 12, "dark", (1, 2))
    t.fur(rnd, chest, "front", 22, "chest", (1, 2), where=lambda u, v: abs(u - 0.5) < 0.35 and v > 0.15)
    t.fur(rnd, chest, "front", 10, "dark", (1, 2), where=lambda u, v: abs(u - 0.5) >= 0.35)
    hips = (-5, -3, -3.5, 10, 5, 7)
    h = by["hips"]
    h.fur(rnd, hips, "back", 10, "dark").fur(rnd, hips, "left", 5, "dark").fur(rnd, hips, "right", 5, "dark")
    h.fur(rnd, hips, "bottom", 10, "dark", (1, 3))
    hd = by["head"]
    ruff, skull = (-5, -3, -2.5, 10, 6, 5), (-4, -7, -7, 8, 8, 7)
    hd.fur(rnd, ruff, "left", 9, "mane", (1, 3)).fur(rnd, ruff, "right", 9, "mane", (1, 3))
    hd.fur(rnd, ruff, "bottom", 8, "mane", (1, 3)).fur(rnd, ruff, "back", 8, "mane")
    hd.fur(rnd, skull, "top", 14, "mane", (1, 3), where=lambda u, v: v < 0.6)
    hd.fur(rnd, skull, "back", 6, "mane").fur(rnd, skull, "left", 4, "dark", (1, 1), where=lambda u, v: u < 0.5 and v > 0.4)
    hd.fur(rnd, skull, "right", 4, "dark", (1, 1), where=lambda u, v: u < 0.5 and v > 0.4)
    for side in ("right", "left"):
        outer, inner = side, ("left" if side == "right" else "right")
        a, f = by["arm_" + side], by["forearm_" + side]
        shoulder, upper, fore = (-3.5, -2.5, -3.5, 7, 5, 7), (-2.5, 0, -2.5, 5, 8, 5), (-2.5, 0, -2.5, 5, 7, 5)
        a.fur(rnd, shoulder, "top", 10, "mane", (1, 3)).fur(rnd, shoulder, outer, 8, "mane").fur(rnd, shoulder, "back", 6, "mane")
        a.fur(rnd, shoulder, "bottom", 6, "mane", (1, 2))
        a.fur(rnd, upper, outer, 9, "grey", (1, 2)).fur(rnd, upper, "back", 7, "mid", (1, 2)).fur(rnd, upper, "front", 4, "grey", (1, 1))
        f.fur(rnd, fore, outer, 7, "grey", (1, 2)).fur(rnd, fore, "back", 7, "mid", (1, 2)).fur(rnd, fore, inner, 3, "grey", (1, 1))
        l, sh = by["leg_" + side], by["shin_" + side]
        thigh, shin = (-2.5, -1, -3, 5, 9, 6), (-2.5, -0.5, -2.5, 5, 7, 5)
        l.fur(rnd, thigh, outer, 7, "mid", (1, 2)).fur(rnd, thigh, "back", 9, "dark", (1, 3)).fur(rnd, thigh, "front", 5, "mid", (1, 2))
        l.fur(rnd, thigh, inner, 3, "mid", (1, 1))
        sh.fur(rnd, shin, "bottom", 6, "grey", (1, 2), where=lambda u, v: v < 0.5)
        by["hand_" + side].fur(rnd, (-3, 0, -2.5, 6, 3, 5), "back", 5, "hand", (1, 2), hang=(1, 2))
        by["hand_" + side].fur(rnd, (-3, 0, -2.5, 6, 3, 5), outer, 3, "hand", (1, 1), hang=(1, 2))
        f.fur(rnd, fore, "bottom", 7, "mid", (1, 2))
        sh.fur(rnd, shin, "back", 6, "grey", (1, 2)).fur(rnd, shin, outer, 4, "grey", (1, 1)).fur(rnd, shin, "front", 3, "grey", (1, 1))
    for name, region in (("tail", (-2, -2, 0, 4, 4, 5)), ("tail2", (-2.5, -2.5, 0, 5, 5, 5)), ("tail3", (-2, -2, 0, 4, 4, 4))):
        tp = by[name]
        for face in ("top", "bottom", "left", "right"):
            tp.fur(rnd, region, face, 7, "mane", (1, 3), size=(1, 2))
    by["tail3"].fur(rnd, (-2, -2, 0, 4, 4, 4), "back", 6, "mane", (1, 3))
    return parts


def pack(parts):
    boxes = [b for p in parts for b in p.boxes]
    order = sorted(boxes, key=lambda b: -(b.d + b.h))
    x = y = shelf = 0
    for b in order:
        w, h = 2 * (b.w + b.d), b.d + b.h
        if x + w > TEX_W:
            x, y, shelf = 0, y + shelf, 0
        b.u, b.v = x, y
        x += w
        shelf = max(shelf, h)
    tex_h = 32
    while tex_h < y + shelf:
        tex_h *= 2
    return tex_h


def faces(b):
    """Грани кубоида: (вид, u, v, ширина, высота) в единицах развёртки — как в ModelPart.Cube."""
    u, v, w, h, d = b.u, b.v, b.w, b.h, b.d
    return [("top", u + d, v, w, d), ("bottom", u + d + w, v, w, d), ("right", u, v + d, d, h),
            ("front", u + d, v + d, w, h), ("left", u + d + w, v + d, d, h), ("back", u + 2 * d + w, v + d, w, h)]


def mix(pal, t):
    """t: 0 — тень, 0.5 — основа, 1 — светлый ворс."""
    t = max(0.0, min(1.0, t))
    a, b2 = (pal[0], pal[1]) if t < 0.5 else (pal[1], pal[2])
    k = t * 2 if t < 0.5 else t * 2 - 1
    return tuple(int(a[i] + (b2[i] - a[i]) * k) for i in range(3))


def paint(parts, tex_h):
    w, h = TEX_W * R, tex_h * R
    base = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bp, gp = base.load(), glow.load()
    rnd = random.Random(1717)
    for p in parts:
        for b in p.boxes:
            for kind, u, v, fw, fh in faces(b):
                face(bp, gp, b, kind, u * R, v * R, fw * R, fh * R, rnd)
    return base, glow


def face(bp, gp, b, kind, x0, y0, pw, ph, rnd):
    if pw <= 0 or ph <= 0:
        return
    pal = PAL[b.mat]
    side = kind in ("right", "left", "front", "back")
    # Шерсть: пятна по 2×2 пикселя и вертикальные пряди разной длины.
    cells = {}
    tone = [[0.0] * ph for _ in range(pw)]
    for x in range(pw):
        streak, left = 0.0, 0
        for y in range(ph):
            if left <= 0:
                streak, left = rnd.uniform(-0.18, 0.18), rnd.randint(2, 5)
            left -= 1
            c = (x // 2, y // 2)
            if c not in cells:
                cells[c] = rnd.uniform(-0.2, 0.2)
            shade = {"top": 0.08, "bottom": -0.18}.get(kind, 0.06 - 0.16 * y / ph)
            tone[x][y] = 0.5 + b.shift + cells[c] + (streak if side and b.mat not in ("bone", "nose", "mouth") else streak * 0.3) + shade
    # Края граней темнее — кубики читаются, как на эталоне.
    for x in range(pw):
        for y in range(ph):
            if min(pw, ph) > 4 and min(x, pw - 1 - x, y, ph - 1 - y) == 0:
                tone[x][y] -= 0.12
    f = b.feature
    if f == "chest" and kind == "front":
        # Светлая грудь и живот: светлее к середине, сверху темнее (под гривой).
        for x in range(pw):
            for y in range(ph):
                cx = 1 - abs(x - pw / 2) / (pw / 2)
                k = max(0.0, cx * 1.4 - 0.25) * min(1.0, y / (ph * 0.25))
                col = mix(PAL["chest"], tone[x][y] + 0.05)
                dark = mix(pal, tone[x][y])
                k = min(1.0, k + rnd.uniform(-0.25, 0.25))
                bp[x0 + x, y0 + y] = (*lerp(dark, col, max(0.0, k)), 255)
    elif f == "chest" and kind in ("right", "left"):
        for x in range(pw):
            for y in range(ph):
                bp[x0 + x, y0 + y] = (*lerp(mix(pal, tone[x][y]), mix(PAL["mid"], tone[x][y]), 0.5 if rnd.random() < 0.4 else 0), 255)
    else:
        for x in range(pw):
            for y in range(ph):
                bp[x0 + x, y0 + y] = (*mix(pal, tone[x][y]), 255)

    if f in ("shag", "tatter") and side:
        # Лохматый низ: клочья шерсти, между ними насквозь пусто.
        for x in range(pw):
            cut = rnd.randint(0, 3 if f == "tatter" else 2) if (x // 2) % 2 == rnd.randint(0, 1) else rnd.randint(0, 1)
            for y in range(max(0, ph - cut), ph):
                bp[x0 + x, y0 + y] = (0, 0, 0, 0)
    if f == "scars" and kind in ("front", "right", "left"):
        # Кровавые шрамы: две косые полосы.
        for n in range(2):
            sx, sy = rnd.randint(1, max(1, pw - 6)), rnd.randint(2, max(2, ph - 6))
            for i in range(rnd.randint(4, 7)):
                px, py = sx + i, sy + i // 2 + (n if kind == "front" else 0)
                if 0 <= px < pw and 0 <= py < ph:
                    bp[x0 + px, y0 + py] = (*lerp(SCAR, (70, 10, 14), rnd.random() * 0.5), 255)
    if f == "face":
        if kind == "front":
            # Глаза: жёлтые, светятся; над ними тёмная бровь, под ними светлые щёки.
            ey = int(ph * 0.22)
            for ex in (int(pw * 0.18), int(pw * 0.82) - 3):
                for x in range(ex - 1, ex + 4):
                    bp[x0 + x, y0 + ey - 1] = (*PAL["nose"][1], 255)
                for x in range(ex, ex + 3):
                    for y in (ey, ey + 1):
                        col = EYE if (x - ex) != 2 or y != ey + 1 else (200, 140, 40)
                        bp[x0 + x, y0 + y] = (*col, 255)
                        gp[x0 + x, y0 + y] = (*col, 255)
                for x in range(ex - 1, ex + 4):
                    for y in range(ey + 2, ey + 5):
                        bp[x0 + x, y0 + y] = (*mix(PAL["grey"], 0.45 + rnd.uniform(-0.15, 0.15)), 255)
            for y in range(ph):
                for x in range(int(pw * 0.38), int(pw * 0.62)):
                    if y > ey + 1:
                        bp[x0 + x, y0 + y] = (*mix(PAL["mid"], 0.5 + rnd.uniform(-0.2, 0.2)), 255)
        if kind == "bottom":
            for x in range(pw):
                for y in range(ph):
                    bp[x0 + x, y0 + y] = (*mix(PAL["mouth"], 0.3 + rnd.uniform(-0.2, 0.2)), 255)
    if f == "ear" and kind == "front":
        for x in range(1, pw - 1):
            for y in range(1, ph - 1):
                bp[x0 + x, y0 + y] = (*mix(PAL["mid"], 0.35 + rnd.uniform(-0.1, 0.1)), 255)
    if f == "snout":
        if kind == "bottom":
            # Нёбо: красное с рядом светлых зубов по краю.
            for x in range(pw):
                for y in range(ph):
                    edge = x < 2 or x >= pw - 2 or y >= ph - 2
                    bp[x0 + x, y0 + y] = (*(mix(PAL["bone"], 0.5 + rnd.uniform(-0.2, 0.1)) if edge and (x + y) % 3 else mix(PAL["mouth"], 0.4 + rnd.uniform(-0.2, 0.2))), 255)
        if kind in ("front", "right", "left"):
            # Верхние зубы по нижнему краю морды.
            for x in range(pw):
                for y in range(ph - 3, ph):
                    if (x % 3 != 2) and (y >= ph - 2 or x % 3 == 0):
                        bp[x0 + x, y0 + y] = (*mix(PAL["bone"], 0.55 + rnd.uniform(-0.15, 0.1)), 255)
        if kind == "top":
            for x in range(pw):
                for y in range(ph):
                    if abs(x - pw / 2 + 0.5) < 1.5:
                        bp[x0 + x, y0 + y] = (*mix(PAL["dark"], 0.5 + rnd.uniform(-0.2, 0.2)), 255)
    if f == "jaw":
        if kind == "top":
            # Язык и нижние зубы по краю.
            for x in range(pw):
                for y in range(ph):
                    edge = x < 2 or x >= pw - 2 or y >= ph - 2
                    col = mix(PAL["bone"], 0.5 + rnd.uniform(-0.2, 0.1)) if edge and (x + y) % 3 else mix(PAL["mouth"], 0.6 + rnd.uniform(-0.2, 0.2))
                    bp[x0 + x, y0 + y] = (*col, 255)
        if kind in ("front", "right", "left"):
            for x in range(pw):
                for y in range(0, 2):
                    if x % 3 != 2:
                        bp[x0 + x, y0 + y] = (*mix(PAL["bone"], 0.55 + rnd.uniform(-0.15, 0.1)), 255)
    if f in ("claw", "clawh"):
        # Коготь темнеет к основанию и светлеет к кончику (claw — смотрит вниз, clawh — вперёд).
        for x in range(pw):
            for y in range(ph):
                if f == "claw":
                    t = 1.0 if kind == "bottom" else 0.0 if kind == "top" else y / max(1, ph - 1)
                elif kind == "left":
                    t = 1 - x / max(1, pw - 1)
                elif kind == "right":
                    t = x / max(1, pw - 1)
                elif kind in ("top", "bottom"):
                    t = y / max(1, ph - 1)
                else:
                    t = 1.0 if kind == "front" else 0.0
                bp[x0 + x, y0 + y] = (*lerp(PAL["hand"][1], PAL["bone"][2], min(1.0, t * 1.2)), 255)


def lerp(a, b, k):
    return tuple(int(a[i] + (b[i] - a[i]) * k) for i in range(3))


def fmt(v):
    return f"{v:.2f}f"


def java(parts, tex_h):
    out = ["package com.heroicpaths.client.werewolf;", "",
           "import net.minecraft.client.model.geom.PartPose;",
           "import net.minecraft.client.model.geom.builders.CubeDeformation;",
           "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
           "import net.minecraft.client.model.geom.builders.LayerDefinition;",
           "import net.minecraft.client.model.geom.builders.MeshDefinition;",
           "import net.minecraft.client.model.geom.builders.PartDefinition;", "",
           "import java.util.HashMap;", "import java.util.Map;", "",
           "// Сгенерировано tools/werewolf_gen.py — не править вручную.",
           "public final class WerewolfModels {", "    private WerewolfModels() {}", "",
           "    public static LayerDefinition werewolf() {",
           "        MeshDefinition mesh = new MeshDefinition();",
           "        Map<String, PartDefinition> parts = new HashMap<>();",
           "        parts.put(\"root\", mesh.getRoot());"]
    for p in parts:
        cubes = "CubeListBuilder.create()" + "".join(
            f".texOffs({b.u}, {b.v}).addBox({fmt(b.x)}, {fmt(b.y)}, {fmt(b.z)}, {b.w}, {b.h}, {b.d}, new CubeDeformation({fmt(b.inflate)}))"
            for b in p.boxes)
        out.append(f"        parts.put(\"{p.name}\", parts.get(\"{p.parent}\").addOrReplaceChild(\"{p.name}\", {cubes},")
        out.append(f"                PartPose.offsetAndRotation({fmt(p.px)}, {fmt(p.py)}, {fmt(p.pz)}, {fmt(p.rx)}, {fmt(p.ry)}, {fmt(p.rz)})));")
    out += [f"        return LayerDefinition.create(mesh, {TEX_W}, {tex_h});", "    }", "}", ""]
    return "\n".join(out)


# --- Предпросмотр: программная отрисовка кубоидов с текстурой (как ModelPart, без анимации). ---

def rot(rx, ry, rz):
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    mx = [[1, 0, 0], [0, cx, -sx], [0, sx, cx]]
    my = [[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]]
    mz = [[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]]
    return mul(mul(mz, my), mx)


def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def apply(m, t, v):
    return [t[i] + sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def transforms(parts):
    by = {p.name: p for p in parts}
    cache = {"root": ([[1, 0, 0], [0, 1, 0], [0, 0, 1]], [0, 0, 0])}

    def get(name):
        if name in cache:
            return cache[name]
        p = by[name]
        pm, pt = get(p.parent)
        m = mul(pm, rot(p.rx, p.ry, p.rz))
        t = apply(pm, pt, [p.px, p.py, p.pz])
        cache[name] = (m, t)
        return cache[name]

    return {p.name: get(p.name) for p in parts}


def face_point(b, kind, s, t):
    i = b.inflate
    x0, y0, z0 = b.x - i, b.y - i, b.z - i
    x1, y1, z1 = b.x + b.w + i, b.y + b.h + i, b.z + b.d + i
    W, H, D = x1 - x0, y1 - y0, z1 - z0
    return {
        "top": (x0 + s * W, y0, z1 - t * D),
        "bottom": (x0 + s * W, y1, z1 - t * D),
        "right": (x0, y0 + t * H, z1 - s * D),
        "front": (x0 + s * W, y0 + t * H, z0),
        "left": (x1, y0 + t * H, z0 + s * D),
        "back": (x1 - s * W, y0 + t * H, z1),
    }[kind]


def preview(parts, base, path):
    tr = transforms(parts)
    px = base.load()
    views = [("перед", 0), ("три четверти", math.pi * 0.3), ("бок", math.pi / 2), ("сзади", math.pi)]
    S, cw = 18, 520
    img = Image.new("RGB", (cw * len(views), 720), (235, 235, 232))
    draw = ImageDraw.Draw(img)
    for vi, (_, yaw) in enumerate(views):
        cy, sy = math.cos(yaw), math.sin(yaw)
        quads = []
        for p in parts:
            m, t = tr[p.name]
            for b in p.boxes:
                for kind, u, v, fw, fh in faces(b):
                    pw, ph = fw * R, fh * R
                    if pw <= 0 or ph <= 0:
                        continue
                    for ix in range(pw):
                        for iy in range(ph):
                            col = px[u * R + ix, v * R + iy]
                            if col[3] == 0:
                                continue
                            pts = []
                            for ds, dt in ((0, 0), (1, 0), (1, 1), (0, 1)):
                                lx, ly, lz = face_point(b, kind, (ix + ds) / pw, (iy + dt) / ph)
                                wx, wy, wz = apply(m, t, [lx, ly, lz])
                                # Поворот вокруг вертикали; зритель смотрит вдоль +z.
                                rx2 = wx * cy - wz * sy
                                rz2 = wx * sy + wz * cy
                                pts.append((rx2, wy, rz2))
                            depth = sum(q[2] for q in pts) / 4
                            # Нормаль для освещения.
                            ax = [pts[1][k] - pts[0][k] for k in range(3)]
                            bx = [pts[3][k] - pts[0][k] for k in range(3)]
                            n = [ax[1] * bx[2] - ax[2] * bx[1], ax[2] * bx[0] - ax[0] * bx[2], ax[0] * bx[1] - ax[1] * bx[0]]
                            ln = math.sqrt(sum(c * c for c in n)) or 1
                            light = 0.55 + 0.45 * abs((-n[1] * 0.6 - n[2] * 0.8) / ln)
                            quads.append((depth, pts, tuple(int(c * light) for c in col[:3])))
        quads.sort(key=lambda q: -q[0])
        ox, oy = cw * vi + cw / 2, 700 - 24 * S / 1.6
        for _, pts, col in quads:
            draw.polygon([(ox + q[0] * S / 1.6, oy + q[1] * S / 1.6) for q in pts], fill=col)
    img.save(path)


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else ".")
    parts = model()
    tex_h = pack(parts)
    base, glow = paint(parts, tex_h)
    tex = root / "src/main/resources/assets/heroicpaths/textures/entity/werewolf"
    tex.mkdir(parents=True, exist_ok=True)
    base.save(tex / "werewolf.png")
    glow.save(tex / "werewolf_glow.png")
    src = root / "src/main/java/com/heroicpaths/client/werewolf"
    src.mkdir(parents=True, exist_ok=True)
    (src / "WerewolfModels.java").write_text(java(parts, tex_h), encoding="utf-8")
    print(f"werewolf: {len(parts)} parts, texture {TEX_W}x{tex_h} (x{R})")
    if "--preview" in sys.argv:
        preview(parts, base, "werewolf_preview.png")


if __name__ == "__main__":
    main()
