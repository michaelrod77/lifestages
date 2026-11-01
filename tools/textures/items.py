"""HD (32x32) item, block, particle and emote icon textures."""
import math

import baby_skins
import holidays
import nursery
import presents

from pixel import (Canvas, HEART, alpha, arc, darken, hnoise, hx, lighten, ramp, seg,
                   sphere, stack, tube)

WOOD = ramp(hx('#E0BA86'))
CHEST_WOOD = ramp(hx('#CFA470'))
GOLD = [hx('#7E5D12'), hx('#A9801E'), hx('#E2B83F'), hx('#F2CF63'), hx('#FBE7A1')]
STINK = hx('#9BC53D', 235)


# ---------- shared shapes ----------

def diaper_half_width(v, sag):
    """Waistband, then concave leg cut-outs, then a rounded seat."""
    narrow = 6 + sag
    if 7 <= v < 11:
        return 12.5
    if 11 <= v < 22:
        t = (v - 10.5) / 11.5
        return 12.5 - (12.5 - narrow) * math.sqrt(max(0.0, 1 - (1 - t) ** 2))
    end = 4.5 + sag
    if 22 <= v < 21.5 + end:
        t = (v - 21.5) / end
        return narrow * math.sqrt(max(0.0, 1 - t * t))
    return -1


def draw_diaper(c, ox=0.0, oy=0.0, s=1.0, dirty=False):
    """Front view of a diaper. (ox, oy) offsets and s scales the 32x32 design."""
    sag = 1.6 if dirty else 0.0
    base = hx('#EEE6C8') if dirty else hx('#F5F7FB')
    band = hx('#B9B09A') if dirty else hx('#C7B5EE')
    elastic = hx('#C4B98E') if dirty else hx('#DCD0F6')
    stars = [(9, 13), (14, 17), (20, 12), (23, 16), (16, 22), (12, 19)]
    for y in range(c.h):
        for x in range(c.w):
            u, v = (x + 0.5 - ox) / s, (y + 0.5 - oy) / s
            if 8 <= v < 11 and (1 <= u < 4 or 28 <= u < 31):
                tab = hx('#F6A8C6')
                c.set(x, y, lighten(tab, 0.3) if u < 2 or v < 9 else (darken(tab, 0.18) if u >= 30 else tab))
                continue
            hw = diaper_half_width(v, sag)
            if hw < 0 or abs(u - 16) > hw:
                continue
            du = (u - 16) / 12.5
            col = darken(base, 0.13) if du > 0.55 else darken(base, 0.06) if du > 0.2 else lighten(base, 0.6) if du < -0.5 else base
            if v >= 23:
                col = darken(col, 0.05)
            if v < 10:
                col = lighten(band, 0.35) if v < 8 else band
                if 8 <= v < 9 and int(u) % 2 == 0:
                    col = darken(band, 0.12)
            elif 11 <= v < 23 and abs(u - 16) > hw - 1.0 / s:
                col = elastic
                if dirty and 14 <= v and hnoise(int(u), int(v), 4) > 0.45:
                    col = hx('#8A5A2B')
            elif not dirty and any(abs(u - pu) + abs(v - pv) <= 1.0 for pu, pv in stars):
                star = next(i for i, (pu, pv) in enumerate(stars) if abs(u - pu) + abs(v - pv) <= 1.0)
                col = hx('#F2A9C8') if star % 2 else hx('#BBA8EC')
            if dirty and v >= 10:
                e = ((u - 16) / 5.8) ** 2 + ((v - 20.8) / 4.8) ** 2
                if e <= 1:
                    n = hnoise(int(u), int(v), 5)
                    col = hx('#5C3A18') if n < 0.25 else hx('#9C6A35') if n > 0.8 else hx('#7A4E22')
                    if e > 0.72 and n < 0.5:
                        col = hx('#A88545')
            c.set(x, y, col)


def draw_fly(c, x, y, wings_up=True):
    c.set(x, y, hx('#1C1C1C'))
    c.set(x + 1, y, hx('#1C1C1C'))
    c.set(x + 2, y, hx('#7A1010'))
    wy = y - 1 if wings_up else y + 1
    c.setif(x, wy, hx('#DCE8F2', 190))
    c.setif(x + 1, wy, hx('#DCE8F2', 190))


def draw_stink(c, xs, y0, y1, phase):
    for sx in xs:
        for y in range(y0, y1 + 1):
            x = sx + round(math.sin(y * 1.1 + phase + sx))
            c.setif(x, y, STINK)


def heart(c, x, y, color):
    c.pattern(x, y, HEART, color, lighten(color, 0.45))


# ---------- items ----------

def milk_bottle():
    c = Canvas(32)
    glass, glass_edge = (206, 229, 246, 150), (168, 204, 230, 225)
    for y in range(13, 30):
        for x in range(10, 22):
            if y == 29:
                if x not in (10, 21):
                    c.set(x, y, glass_edge)
                continue
            if x in (10, 21):
                c.set(x, y, glass_edge if y < 17 else (hx('#E9F3FB') if x == 10 else hx('#C5D0DA')))
                continue
            if y < 17:
                c.set(x, y, glass)
                continue
            if y == 17:
                col = hx('#FFFFFF') if x < 19 else hx('#F1ECDF')
            elif x <= 12:
                col = hx('#FFFFFF')
            elif x <= 17:
                col = hx('#FBF8EF')
            elif x <= 19:
                col = hx('#EFE9DA')
            else:
                col = hx('#DDD5C3')
            c.set(x, y, darken(col, 0.07) if y == 28 else col)
    for y in range(13, 17):
        c.set(12, y, (255, 255, 255, 230))
    for x0, x1, y in ((19, 20, 20), (20, 20, 23), (19, 20, 26)):
        for x in range(x0, x1 + 1):
            c.set(x, y, hx('#B5C0CB'))
    heart(c, 13, 20, hx('#F28DB2'))
    blue = hx('#B7A3E6')
    for y in range(9, 13):
        for x in range(9, 23):
            col = lighten(blue, 0.35) if y == 9 else darken(blue, 0.22) if y == 12 else blue
            if y in (10, 11) and x in (11, 14, 17, 20):
                col = darken(blue, 0.15)
            if x == 9:
                col = lighten(col, 0.15)
            if x == 22:
                col = darken(col, 0.18)
            c.set(x, y, col)
    rubber = hx('#F0BE8C')
    for y in (7, 8):
        for x in range(14, 18):
            c.set(x, y, lighten(rubber, 0.25) if x == 14 else darken(rubber, 0.2) if x == 17 else rubber)
    c.ellipse(16, 4.6, 2.6, 3.1, sphere(hx('#F3C495'), 16, 4.6, 2.6, 3.1))
    c.outline()
    return c


def clean_diaper():
    c = Canvas(32)
    draw_diaper(c)
    c.outline(0.5)
    return c


def dirty_diaper_frames():
    base = Canvas(32)
    draw_diaper(base, dirty=True)
    base.outline(0.5)
    flies = [(25, 3), (27, 5), (25, 4), (23, 3)]
    frames = []
    for f in range(4):
        c = base.copy()
        draw_stink(c, (8, 15, 22), 0, 5, f * math.pi / 2)
        draw_fly(c, flies[f][0], flies[f][1], f % 2 == 0)
        frames.append(c)
    return frames


def walking_cane():
    c = Canvas(32)
    path = seg((8.5, 27.5), (20.5, 10.5)) + arc(23.5, 10.5, 3.0, math.pi, 0) + seg((26.5, 10.5), (26.5, 13.0), 6)
    c.stroke(path, 1.3, tube(ramp(hx('#8B5A2B')), 1.3))
    c.stroke(seg((6.8, 29.3), (8.6, 27.4), 8), 1.45, tube(ramp(hx('#C46A3A')), 1.45))
    for x, y in ((13, 21), (17, 16)):
        c.set(x, y, hx('#6E4520'))
    c.outline(0.6)
    return c


def frying_pan():
    c = Canvas(32)
    cx, cy, R = 19.5, 12.0, 9.5
    for y in range(32):
        for x in range(32):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > R:
                continue
            if d > 8.1:
                v = (-dx - dy) / (d * 1.414)
                c.set(x, y, hx('#A6ACB4') if v > 0.35 else hx('#565B62') if v < -0.35 else hx('#80868E'))
            else:
                col = hx('#383C42')
                if 5.0 < d < 7.3 and (dx + dy) / d < -0.95:
                    col = hx('#4E545C')
                if d > 7.3:
                    col = hx('#2C2F34')
                c.set(x, y, col)
    for x, y in ((15, 8), (16, 8), (15, 9)):
        c.set(x, y, hx('#9AA2AC'))
    c.stroke(seg((3.6, 28.4), (12.2, 19.8)), 1.45, tube([None, hx('#231B16'), hx('#3B2F28'), hx('#5A4A40')], 1.45))
    c.stroke(seg((12.0, 20.0), (13.8, 18.2), 6), 1.15, tube([None, hx('#5E636A'), hx('#7C828A'), hx('#A0A6AE')], 1.15))
    c.clear(4, 27)
    c.set(9, 23, hx('#B9BEC5'))
    c.outline(0.55)
    return c


def memory_orb():
    c = Canvas(32)
    tones = [hx('#B57A1C'), hx('#E2A43A'), hx('#F7C850'), hx('#FFE48A'), hx('#FFF7D2')]
    c.ellipse(15.5, 15.5, 10, 10, sphere(None, 15.5, 15.5, 10, 10, tones))
    pts = [(15.5 + (1 + t * 7) * math.cos(t * 4 * math.pi), 15.5 + (1 + t * 7) * math.sin(t * 4 * math.pi)) for t in [i / 120 for i in range(121)]]
    for px, py in pts:
        if c.get(int(px), int(py)) is not None:
            c.set(int(px), int(py), hx('#FFFFFF', 210))
    c.outline(0.5)
    for sx, sy in ((4, 5), (27, 25), (26, 4)):
        c.set(sx, sy, hx('#FFFFFF'))
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c.setif(sx + dx, sy + dy, hx('#FFF1A8'))
    return c


def mom_spawn_egg():
    c = Canvas(32)
    egg = (15.5, 16.5, 9.5, 12.5)
    c.ellipse(*egg, sphere(hx('#F0A2B8'), *egg))
    spots = [(12, 10, 2.3, '#7A4A2A'), (20, 15, 2.6, '#FFFFFF'), (13, 21, 2.1, '#FFFFFF'),
             (19, 24, 1.9, '#7A4A2A'), (10, 15.5, 1.5, '#7A4A2A'), (18.5, 8, 1.4, '#FFFFFF')]
    for sx, sy, r, col in spots:
        tones = ramp(hx(col))
        c.fill(lambda x, y, sx=sx, sy=sy, r=r: (x - sx) ** 2 + (y - sy) ** 2 <= r * r and c.get(int(x), int(y)) is not None,
               lambda x, y, tones=tones: tones[2 if (x + 0.5 - 15.5) / 9.5 + (y + 0.5 - 16.5) / 12.5 > -0.6 else 3])
    c.outline(0.55)
    return c


def crib_icon():
    c = Canvas(32)
    c.rect(6, 19, 25, 23, lambda x, y: hx('#D3E6F8') if y == 19 else hx('#BCD8F3'))
    c.rect(7, 17, 24, 18, lambda x, y: hx('#F4A7C0') if y == 17 else hx('#E58EAA'))
    for bx in (8, 11, 14, 17, 20, 23):
        c.rect(bx, 11, bx, 23, WOOD[2])
    for y0 in (9, 24):
        c.rect(5, y0, 26, y0 + 1, lambda x, y: WOOD[3] if y == y0 else WOOD[1])
    for px in (3, 26):
        c.rect(px, 6, px + 2, 29, lambda x, y, px=px: WOOD[3] if x == px else WOOD[1] if x == px + 2 else WOOD[2])
        c.ellipse(px + 1.5, 5.5, 2, 2, sphere(None, px + 1.5, 5.5, 2, 2, WOOD))
    c.outline(0.55)
    return c


# ---------- blocks ----------

def wood_px(x, y, tones, plank=8, seed=1):
    py, k = y % plank, y // plank
    if py == 0:
        return tones[0]
    if py == 1:
        return tones[3]
    n = hnoise(x // 7 + k * 7, py, seed)
    if n < 0.12:
        return tones[1]
    if n > 0.9:
        return tones[3]
    return tones[2]


def stone_px(x, y, seed=2):
    n = hnoise(x // 4, y // 3, seed)
    m = hnoise(x // 2, y // 2, seed + 1)
    tones = ramp(hx('#9A9AA2'))
    i = 1 if n < 0.18 else 3 if n > 0.82 else 2
    if m > 0.95:
        i = 1
    return tones[i]


def crib_wood():
    """Smooth painted-wood posts: flat color, a highlight edge and a shadow edge."""
    c = Canvas(32)
    c.rect(0, 0, 31, 31, lambda x, y: WOOD[3] if x <= 1 else WOOD[1] if x >= 30 else WOOD[2])
    return c


def crib_rails():
    c = Canvas(32)
    for y0 in (2, 27):
        c.rect(0, y0, 31, y0 + 2, lambda x, y, y0=y0: WOOD[3] if y == y0 else WOOD[1] if y == y0 + 2 else WOOD[2])
    for bx in (2, 7, 12, 17, 22, 27):
        c.rect(bx, 5, bx + 2, 26, lambda x, y, bx=bx: WOOD[3] if x == bx else WOOD[1] if x == bx + 2 else WOOD[2])
    return c


def crib_headboard():
    """Smooth arched panel with a bevelled edge, an inset groove and a heart cut-out."""
    c = Canvas(32)

    def heart_at(x, y):
        u, v = (x - 15.5) / 5.0, -(y - 15.5) / 5.0
        return (u * u + v * v - 1) ** 3 - u * u * v ** 3 <= 0

    def top_edge(x):
        return 2 + 6 * ((x - 16) / 16) ** 2

    c.fill(lambda x, y: y >= top_edge(x) and not heart_at(x, y), WOOD[2])
    for y in range(32):
        for x in range(32):
            if c.get(x, y) is None:
                continue
            if c.get(x, y - 1) is None and y > 0 or c.get(x - 1, y) is None and x > 0 or y == 0 or x == 0:
                c.set(x, y, WOOD[3])
            elif c.get(x, y + 1) is None and y < 31 or c.get(x + 1, y) is None and x < 31 or y == 31 or x == 31:
                c.set(x, y, WOOD[1])
    for y in range(32):
        for x in range(32):
            p = c.get(x, y)
            if p != WOOD[2]:
                continue
            inset = (abs(y - (top_edge(x) + 4)) < 0.6 and 4 <= x <= 27) or (x in (4, 27) and top_edge(x) + 4 <= y <= 27)                 or (y == 27 and 4 <= x <= 27)
            if inset:
                c.set(x, y, WOOD[1])
                if c.get(x + 1, y + 1) == WOOD[2]:
                    c.set(x + 1, y + 1, WOOD[3])
    return c


def crib_mattress():
    c = Canvas(32)
    base = hx('#BCD8F3')
    c.rect(0, 0, 31, 31, lambda x, y: darken(base, 0.08) if x % 8 == 0 or y % 8 == 0 else base)
    for gx in range(4, 32, 8):
        for gy in range(4, 32, 8):
            c.set(gx, gy, hx('#FFFFFF'))
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                c.set(gx + dx, gy + dy, hx('#E3F1FC'))
    return c


def chest_side(c, seed=4):
    c.rect(0, 0, 31, 31, lambda x, y: wood_px(x, y, CHEST_WOOD, seed=seed))
    for x0 in (0, 29):
        c.rect(x0, 0, x0 + 2, 31, lambda x, y, x0=x0: GOLD[3] if x == x0 else GOLD[1] if x == x0 + 2 else GOLD[2])
    for y0 in (0, 30):
        c.rect(0, y0, 31, y0 + 1, lambda x, y, y0=y0: GOLD[3] if y == y0 else GOLD[1])


def will_chest_side():
    c = Canvas(32)
    chest_side(c)
    return c


def will_chest_front():
    c = Canvas(32)
    chest_side(c, seed=5)
    c.rect(0, 11, 31, 11, CHEST_WOOD[0])
    c.rect(0, 12, 31, 13, lambda x, y: GOLD[3] if y == 12 else GOLD[1])
    c.rect(12, 8, 19, 18, lambda x, y: GOLD[1] if x in (12, 19) or y in (8, 18) else GOLD[3] if x == 13 or y == 9 else GOLD[2])
    for x, y in ((15, 11), (16, 11), (15, 12), (16, 12), (15, 13), (16, 13), (15, 14), (16, 14), (15, 15), (16, 15)):
        c.set(x, y, hx('#1A1208'))
    c.set(14, 12, hx('#1A1208'))
    c.set(17, 12, hx('#1A1208'))
    c.ellipse(16, 23, 3.4, 3.2, sphere(hx('#B8322A'), 16, 23, 3.4, 3.2))
    c.set(16, 23, hx('#7E1F16'))
    for x, y in ((14, 26), (14, 27), (13, 28), (17, 26), (17, 27), (18, 28)):
        c.set(x, y, hx('#8E2419'))
    return c


def will_chest_top():
    c = Canvas(32)
    chest_side(c, seed=6)
    for x0 in (0, 29):
        c.rect(0, x0, 31, x0 + 2, lambda x, y, x0=x0: GOLD[3] if y == x0 else GOLD[1] if y == x0 + 2 else GOLD[2])
    heart(c, 14, 13, GOLD[1])
    return c


FONT = {
    'R': ["XX.", "X.X", "XX.", "X.X", "X.X"],
    'I': ["XXX", ".X.", ".X.", ".X.", "XXX"],
    'P': ["XX.", "X.X", "XX.", "X..", "X.."],
}


def gravestone_front():
    c = Canvas(32)

    def inside(x, y):
        if y < 14:
            return (x - 16) ** 2 + (y - 14) ** 2 <= 13.5 ** 2
        return 2.5 <= x <= 29.5

    c.fill(inside, lambda x, y: stone_px(x, y))
    for y in range(32):
        for x in range(32):
            if c.get(x, y) is None:
                continue
            if c.get(x, y - 1) is None or c.get(x - 1, y) is None:
                c.set(x, y, hx('#B0B0B0'))
            elif c.get(x + 1, y) is None:
                c.set(x, y, hx('#5E5E5E'))
    deep, lip = hx('#4E4E4E'), hx('#B4B4B4')
    carved = set()
    for i, ch in enumerate("RIP"):
        for j, row in enumerate(FONT[ch]):
            for k, on in enumerate(row):
                if on == 'X':
                    for sy in range(2):
                        for sx in range(2):
                            carved.add((7 + i * 7 + k * 2 + sx, 8 + j * 2 + sy))
    for x, y in carved:
        c.set(x, y, deep)
        if (x + 1, y + 1) not in carved:
            c.set(x + 1, y + 1, lip)
    c.rect(6, 21, 25, 21, deep)
    c.rect(6, 22, 25, 22, lip)
    for x, y in ((18, 4), (19, 5), (19, 6), (20, 7)):
        c.set(x, y, hx('#5A5A5A'))
    for y in range(26, 32):
        for x in range(3, 30):
            if (x < 9 or x > 23) and hnoise(x, y, 8) > 0.45 - (y - 26) * 0.08:
                c.set(x, y, hx('#5E8C31') if hnoise(x, y, 9) > 0.5 else hx('#7FAF45'))
    return c


def gravestone_side():
    c = Canvas(32)
    c.rect(0, 0, 31, 31, lambda x, y: stone_px(x, y, seed=12))
    return c


def mess():
    c = Canvas(32)
    blobs = [(16, 17, 10, 7), (9, 13, 4.5, 4), (23.5, 21.5, 5, 4), (20, 10.5, 4, 3)]
    tones = [hx('#3E2410'), hx('#553117'), hx('#6B4423'), hx('#8A5A2F'), hx('#B07A45')]
    for bx, by, rx, ry in blobs:
        c.ellipse(bx, by, rx, ry, sphere(None, bx, by, rx, ry, tones))
    for x, y in ((14, 18), (19, 15), (11, 20)):
        c.set(x, y, hx('#D9C24A'))
        c.set(x + 1, y, hx('#B8A035'))
    c.outline(0.6)
    for x, y in ((4, 25), (27, 8), (3, 9)):
        c.set(x, y, tones[1])
    draw_fly(c, 24, 4)
    return c


# ---------- particles (16x16) ----------

def tear():
    c = Canvas(16)

    def inside(x, y):
        if y >= 10:
            return (x - 8) ** 2 + (y - 10) ** 2 <= 3.6 ** 2
        return y >= 3 and abs(x - 8) <= (y - 3) / 7 * 3.6

    c.fill(inside, sphere(hx('#5DB8F5'), 8, 9.5, 3.6, 6))
    c.set(6, 9, hx('#FFFFFF'))
    c.set(6, 10, hx('#E4F4FF'))
    c.outline(0.5)
    return c


def fart_frames():
    frames = []
    for i in range(4):
        c = Canvas(16)
        k = 1 - i * 0.16
        a = 235 - i * 45
        tones = [alpha(t, a) for t in ramp(hx('#9DAE5B'))]
        for bx, by, r in ((8, 8.5, 5.0), (4.8, 9.5, 3.2), (11.2, 9.5, 3.4), (8, 5.2, 3.2)):
            r *= k
            c.ellipse(8 + (bx - 8) * k, 8 + (by - 8) * k, r, r, sphere(None, 8 + (bx - 8) * k, 8 + (by - 8) * k, r, r, tones))
        frames.append(c)
    return frames


def fly_frames():
    frames = []
    for up in (True, False):
        c = Canvas(16)
        c.rect(5, 7, 9, 9, hx('#1C1C1C'))
        c.rect(10, 7, 11, 9, hx('#2A2A2A'))
        c.set(11, 7, hx('#9A1A1A'))
        c.set(11, 9, hx('#9A1A1A'))
        wy = 4.8 if up else 11.2
        for wx in (5.8, 9.2):
            c.ellipse(wx, wy, 1.5, 2.2, hx('#E6F0F8', 210))
        c.outline(0.6, hx('#7C8A96'))
        c.rect(5, 7, 9, 9, hx('#1C1C1C'))
        c.rect(10, 7, 11, 9, hx('#2A2A2A'))
        c.set(11, 7, hx('#9A1A1A'))
        c.set(11, 9, hx('#9A1A1A'))
        c.rect(3, 8, 4, 8, hx('#1C1C1C'))
        frames.append(c)
    return frames


def stink_frames():
    frames = []
    for f in range(2):
        c = Canvas(16)
        for sx in (5, 10):
            for y in range(1, 15):
                x = sx + round(1.3 * math.sin(y * 0.8 + f * math.pi + sx))
                c.set(x, y, STINK)
                c.set(x + 1, y, darken(STINK, 0.2))
        frames.append(c)
    return frames


def splat():
    c = Canvas(16)
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            ang = math.atan2(dy, dx)
            r = 4.2 + 1.8 * math.sin(ang * 5) + 0.8 * math.sin(ang * 3 + 1)
            if math.hypot(dx, dy) <= r:
                c.set(x, y, hx('#6B4423') if hnoise(x, y, 3) > 0.25 else hx('#8A5A2F'))
    for x, y in ((1, 2), (14, 3), (13, 14), (2, 13)):
        c.set(x, y, hx('#6B4423'))
    c.set(6, 6, hx('#B07A45'))
    return c


def sparkle():
    c = Canvas(16)
    for i in range(1, 7):
        for x, y in ((8 - i, 8), (8 + i, 8), (8, 8 - i), (8, 8 + i)):
            c.set(x, y, hx('#FFF3A0') if i > 2 else hx('#FFFFFF'))
    for x, y in ((7, 7), (9, 7), (7, 9), (9, 9)):
        c.set(x, y, hx('#FFF9D0'))
    c.set(8, 8, hx('#FFFFFF'))
    return c


# ---------- emote wheel icons (32x32) ----------

SKIN_BABY = hx('#F5BC9E')


def baby_face(c):
    for ex in (5.0, 26.0):
        c.ellipse(ex, 17.5, 2.2, 2.6, sphere(SKIN_BABY, ex, 17.5, 2.2, 2.6))
    c.ellipse(15.5, 17, 11, 10.5, sphere(SKIN_BABY, 13.5, 14, 14, 14))
    c.stroke(arc(15.5, 7.2, 2.2, math.pi * 1.15, -math.pi * 0.35), 0.75, hx('#8A5A33'))


def emote_cry():
    c = Canvas(32)
    baby_face(c)
    eye = hx('#4A2E22')
    for x, y in ((9, 14), (10, 15), (11, 16), (10, 17), (9, 18)):
        c.set(x, y, eye)
        c.set(31 - x, y, eye)
    c.ellipse(15.5, 23.5, 3.6, 3.0, hx('#6E1A26'))
    c.fill(lambda x, y: ((x - 15.5) / 3.6) ** 2 + ((y - 23.5) / 3.0) ** 2 <= 1 and y > 24.5, hx('#E9798F'))
    blue = hx('#5DB8F5')
    for x, y in ((8, 19), (7, 20), (6, 21), (6, 22), (5, 23), (5, 24), (4, 26)):
        c.set(x, y, blue)
        c.set(31 - x, y, blue)
    c.outline(0.55)
    return c


def emote_cute():
    c = Canvas(32)
    baby_face(c)
    for ex in (9, 19):
        for x in range(ex, ex + 4):
            c.set(x, 13, hx('#312757'))
        c.set(ex - 1, 13, hx('#312757'))
        for y in (14, 15, 16):
            c.set(ex, y, hx('#FDE5CB') if y == 16 else hx('#1A4A81'))
            c.set(ex + 1, y, hx('#1A4A81') if y == 14 else hx('#266EC0'))
            c.set(ex + 2, y, hx('#1A4A81') if y == 14 else hx('#266EC0'))
            c.set(ex + 3, y, hx('#B4829E') if y < 16 else hx('#FDE5CB'))
        c.set(ex + 1, 15, hx('#FFFFFF'))
    for x, y in ((13, 21), (14, 22), (15, 22), (16, 22), (17, 22), (18, 21)):
        c.set(x, y, hx('#8E3A3A'))
    for bx in (8.0, 23.0):
        c.ellipse(bx, 19.5, 1.9, 1.1, hx('#F59AA5'))
    c.outline(0.55)
    heart(c, 1, 2, hx('#FF6F91'))
    heart(c, 26, 3, hx('#FF6F91'))
    return c


def emote_fart():
    c = Canvas(32)
    draw_diaper(c, ox=-2, oy=9, s=0.62)
    tones = ramp(hx('#9DB35A'))
    for bx, by, r in ((24, 13, 4.5), (27.5, 18, 3.4), (21, 18, 3.4), (25.5, 8, 3.0)):
        c.ellipse(bx, by, r, r, sphere(None, bx, by, r, r, tones))
    c.outline(0.55)
    for y, x0, x1 in ((14, 15, 17), (19, 15, 18)):
        for x in range(x0, x1 + 1):
            c.setif(x, y, hx('#7FA03A'))
    return c


def emote_poop():
    c = Canvas(32)
    draw_diaper(c, ox=2.9, oy=5, s=0.82, dirty=True)
    c.outline(0.5)
    draw_stink(c, (10, 16, 22), 0, 9, 0.6)
    draw_fly(c, 25, 4)
    return c


# ---------- birthdays ----------

PRESENT_COLORS = {
    'common': ('#F2A2BE', '#FFFFFF'),
    'uncommon': ('#9BD8A8', '#F5D35B'),
    'rare': ('#7FB2E8', '#E8ECF2'),
    'epic': ('#B38AE0', '#F2CF63'),
    'legendary': ('#F2C14E', '#D84A4A'),
}


def present(tier):
    paper, ribbon = PRESENT_COLORS[tier]
    pr, rr = ramp(hx(paper)), ramp(hx(ribbon))
    c = Canvas(32)
    for bx, by in ((11.5, 7.5), (20.5, 7.5)):
        c.ellipse(bx, by, 4.2, 3.2, sphere(None, bx, by, 4.2, 3.2, rr))
        c.ellipse(bx, by, 1.6, 1.1, rr[1])
    c.rect(5, 13, 26, 28, lambda x, y: pr[3] if x == 5 else pr[1] if x >= 25 or y == 28 else pr[2])
    c.rect(4, 10, 27, 13, lambda x, y: pr[4] if y == 10 else pr[1] if y == 13 else pr[3])
    c.rect(14, 10, 17, 28, lambda x, y: rr[3] if x == 14 else rr[1] if x == 17 else rr[2])
    c.rect(4, 11, 27, 12, lambda x, y: rr[3] if y == 11 else rr[2])
    c.ellipse(15.5, 9.5, 2.0, 1.8, sphere(None, 15.5, 9.5, 2.0, 1.8, rr))
    for x, y in ((8, 17), (22, 21), (9, 24), (21, 16)):
        c.set(x, y, pr[4])
    c.outline(0.55)
    if tier == 'legendary':
        for sx, sy in ((2, 3), (29, 5), (28, 27)):
            c.set(sx, sy, hx('#FFFFFF'))
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                c.setif(sx + dx, sy + dy, hx('#FFF1A8'))
    return c


FUR = ramp(hx('#A0673A'))
FUR_LIGHT = ramp(hx('#E2B98A'))
EYE = hx('#2A1A12')


def teddy_icon():
    c = Canvas(32)
    for ex, ey in ((9.0, 5.5), (22.0, 5.5)):
        c.ellipse(ex, ey, 3.2, 3.2, sphere(None, ex, ey, 3.2, 3.2, FUR))
        c.ellipse(ex, ey, 1.6, 1.6, FUR_LIGHT[2])
    for ax in (6.5, 24.5):
        c.ellipse(ax, 21, 2.8, 4.2, sphere(None, ax, 21, 2.8, 4.2, FUR))
    c.ellipse(15.5, 23, 7.6, 7.2, sphere(None, 15.5, 23, 7.6, 7.2, FUR))
    c.ellipse(15.5, 24.5, 4.6, 4.4, sphere(None, 15.5, 24.5, 4.6, 4.4, FUR_LIGHT))
    for fx in (10.5, 20.5):
        c.ellipse(fx, 29, 3.4, 2.4, sphere(None, fx, 29, 3.4, 2.4, FUR))
        c.ellipse(fx, 29.2, 1.8, 1.2, FUR_LIGHT[2])
    c.ellipse(15.5, 11, 7.2, 6.8, sphere(None, 15.5, 11, 7.2, 6.8, FUR))
    c.ellipse(15.5, 14, 3.3, 2.5, sphere(None, 15.5, 14, 3.3, 2.5, FUR_LIGHT))
    c.rect(14, 12, 17, 13, EYE)
    c.set(15, 15, EYE)
    c.set(16, 15, EYE)
    for x0 in (11, 19):
        c.rect(x0, 9, x0 + 1, 10, EYE)
        c.set(x0, 9, hx('#FFFFFF'))
    ribbon = ramp(hx('#7FB2E8'))
    c.ellipse(12.8, 17.6, 2.2, 1.4, ribbon[2])
    c.ellipse(18.2, 17.6, 2.2, 1.4, ribbon[2])
    c.ellipse(15.5, 17.6, 1.1, 1.1, ribbon[1])
    c.outline(0.55)
    return c


def teddy_fur():
    c = Canvas(32)
    c.rect(0, 0, 31, 31, lambda x, y: FUR[1] if hnoise(x // 2, y // 2, 51) < 0.05 else FUR[3] if hnoise(x // 2, y // 2, 51) > 0.96 else FUR[2])
    return c


def teddy_face():
    """Head front (7 x 6 pixels on the block): two shiny black eyes above where the muzzle sits."""
    c = teddy_fur()
    for ex in (7, 21):
        c.ellipse(ex + 2.0, 13.0, 2.6, 3.4, EYE)
        c.set(ex + 1, 11, hx('#FFFFFF'))
        c.set(ex + 2, 11, hx('#FFFFFF'))
        c.set(ex + 1, 12, hx('#FFFFFF'))
    # rosy cheeks
    for cx in (5, 26):
        c.ellipse(cx + 0.5, 20.5, 2.2, 1.4, hx('#D98A6A'))
    return c


def teddy_light():
    c = Canvas(32)
    c.rect(0, 0, 31, 31, FUR_LIGHT[2])
    return c


def teddy_muzzle():
    """Light muzzle with a dark rounded nose and a little smile."""
    c = teddy_light()
    c.ellipse(15.5, 9.5, 6.5, 4.5, sphere(None, 15.5, 9.5, 6.5, 4.5, [EYE, EYE, hx('#3A2418'), hx('#5A3A28'), hx('#7A5440')]))
    c.rect(15, 14, 16, 20, EYE)
    for x, y in ((9, 21), (10, 22), (11, 23), (12, 23), (13, 22), (14, 21), (17, 21), (18, 22), (19, 23), (20, 23), (21, 22), (22, 21)):
        c.rect(x, y, x, y + 1, EYE)
    return c


def teddy_belly():
    c = teddy_fur()
    c.ellipse(15.5, 18, 11, 12.5, sphere(None, 15.5, 18, 11, 12.5, FUR_LIGHT))
    return c


def teddy_ear():
    c = teddy_fur()
    c.ellipse(15.5, 17.5, 9, 9.5, sphere(None, 15.5, 17.5, 9, 9.5, FUR_LIGHT))
    return c


def teddy_pad():
    c = teddy_fur()
    c.ellipse(15.5, 18, 10, 10, sphere(None, 15.5, 18, 10, 10, FUR_LIGHT))
    for tx in (9, 15, 21):
        c.ellipse(tx + 0.5, 9.5, 2.2, 2.2, FUR_LIGHT[3])
    return c


def teddy_ribbon():
    rr = ramp(hx('#7FB2E8'))
    c = Canvas(32)
    c.rect(0, 0, 31, 31, lambda x, y: rr[3] if y < 8 else rr[1] if y > 24 else rr[2])
    return c


def stinky_effect():
    """18x18 status-effect icon: a green stink cloud with two flies."""
    c = Canvas(18)
    cloud = ramp(hx('#9BC53D'))
    for cx, cy, r in ((6.5, 10.5, 4.0), (11.0, 9.0, 4.5), (9.0, 13.0, 3.5)):
        c.ellipse(cx, cy, r, r * 0.85, sphere(None, cx, cy, r, r * 0.85, cloud))
    draw_stink(c, (5, 9, 13), 1, 4, 0.0)
    draw_fly(c, 1, 4)
    draw_fly(c, 13, 2, False)
    c.outline(0.55)
    return c


def all_textures():
    """Returns {relative path: Canvas or [frames]}."""
    return {
        'item/milk_bottle.png': milk_bottle(),
        'item/clean_diaper.png': clean_diaper(),
        'item/dirty_diaper.png': dirty_diaper_frames(),
        'item/walking_cane.png': walking_cane(),
        'item/frying_pan.png': frying_pan(),
        'item/memory_orb.png': memory_orb(),
        'item/mom_spawn_egg.png': mom_spawn_egg(),
        'item/crib.png': crib_icon(),
        'block/crib_wood.png': crib_wood(),
        'block/crib_rails.png': crib_rails(),
        'block/crib_headboard.png': crib_headboard(),
        'block/crib_mattress.png': crib_mattress(),
        'block/will_chest_front.png': will_chest_front(),
        'block/will_chest_side.png': will_chest_side(),
        'block/will_chest_top.png': will_chest_top(),
        'block/gravestone_front.png': gravestone_front(),
        'block/gravestone_side.png': gravestone_side(),
        'block/mess.png': mess(),
        'particle/tear.png': tear(),
        'particle/fart.png': fart_frames(),
        'particle/fly.png': fly_frames(),
        'particle/stink.png': stink_frames(),
        'particle/splat.png': splat(),
        'particle/sparkle.png': sparkle(),
        'gui/emote/cry.png': emote_cry(),
        'gui/emote/cute.png': emote_cute(),
        'gui/emote/fart.png': emote_fart(),
        'gui/emote/poop.png': emote_poop(),
        'item/teddy_bear.png': teddy_icon(),
        'block/teddy_fur.png': teddy_fur(),
        'block/teddy_face.png': teddy_face(),
        'block/teddy_belly.png': teddy_belly(),
        'block/teddy_light.png': teddy_light(),
        'block/teddy_muzzle.png': teddy_muzzle(),
        'block/teddy_ear.png': teddy_ear(),
        'block/teddy_pad.png': teddy_pad(),
        'block/teddy_ribbon.png': teddy_ribbon(),
        **{f'item/present_{t}.png': present(t) for t in PRESENT_COLORS},
        **nursery.textures(),
        'mob_effect/stinky.png': stinky_effect(),
        **presents.textures(),
        **baby_skins.textures(),
        **holidays.textures(),
    }


ANIMATED = {'item/dirty_diaper.png': 3}
