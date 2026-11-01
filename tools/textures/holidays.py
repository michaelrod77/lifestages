"""Holiday textures (Easter eggs, Chocolate Bunny, Christmas tree, Halloween costumes) and the pet cat's emote icons."""
import skins
from pixel import Canvas, hnoise, hx, lighten, ramp, sphere

# ---------- Easter ----------

# base shell colour, pattern colour, pattern kind
EGG_DESIGNS = [
    ('#F7B8CF', '#FFFFFF', 'dots'),
    ('#A9D2F5', '#FBE38A', 'zigzag'),
    ('#FBE99A', '#9EDFC0', 'stripes'),
    ('#A8E6CB', '#C9B2F0', 'dots'),
    ('#CDB8F2', '#FFFFFF', 'stars'),
    ('#FFCCAA', '#8EC5F0', 'bands'),
]


def egg_shell(i):
    """A 32x32 shell texture: pastel with soft speckle and its pattern (the egg model maps parts of it)."""
    base, pat, kind = EGG_DESIGNS[i]
    tones, pt = ramp(hx(base)), ramp(hx(pat))
    c = Canvas(32)
    for y in range(32):
        for x in range(32):
            n = hnoise(x, y, 70 + i)
            col = tones[3] if n > 0.9 else tones[1] if n < 0.06 else tones[2]
            if kind == 'dots' and ((x % 8 - 3) ** 2 + (y % 8 - 3) ** 2 <= 3 if (y // 8) % 2 == 0 else ((x + 4) % 8 - 3) ** 2 + (y % 8 - 3) ** 2 <= 3):
                col = pt[3]
            elif kind == 'zigzag' and abs((y % 16) - 8 - (abs((x % 8) - 4) - 2)) <= 1 and 4 <= y % 16 <= 12:
                col = pt[2] if (y % 16) != 8 else pt[3]
            elif kind == 'stripes' and (y % 8) in (2, 3):
                col = pt[2] if (y % 8) == 2 else pt[1]
            elif kind == 'stars' and _star(x % 10, y % 10):
                col = pt[3]
            elif kind == 'bands' and (y % 12) in (3, 4, 8):
                col = pt[2] if (y % 12) != 8 else hx('#FFFFFF')
            c.set(x, y, col)
    return c


def _star(x, y):
    return (x == 4 and 2 <= y <= 6) or (y == 4 and 2 <= x <= 6) or (x, y) in ((3, 3), (5, 5), (3, 5), (5, 3))


def egg_icon():
    """Inventory icon: a speckled pink egg with white polka dots."""
    base, pat, _ = EGG_DESIGNS[0]
    tones = ramp(hx(base))
    c = Canvas(32)
    egg = lambda x, y: ((x + 0.5 - 16) / 9.5) ** 2 + ((y + 0.5 - 17.5) / (11.5 if y < 17 else 10.5)) ** 2 <= 1
    c.fill(egg, sphere(None, 14, 13, 10, 12, tones))
    for dx, dy in ((12, 10), (19, 13), (14, 18), (20, 21), (11, 24), (17, 26), (9, 16)):
        if egg(dx, dy):
            for ox, oy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                c.set(dx + ox, dy + oy, hx(pat))
    c.outline(0.55)
    return c


def chocolate_bunny():
    """A sitting chocolate bunny with long ears, a shine on its head and a ribbon bow."""
    choc = ramp(hx('#7A4A2A'))
    c = Canvas(32)
    c.ellipse(16, 23.5, 8.5, 7.0, sphere(None, 13, 20, 9, 8, choc))      # body
    c.ellipse(16, 13, 5.5, 5.0, sphere(None, 14, 11, 6, 6, choc))        # head
    for ex in (12.5, 19.0):                                               # ears
        c.ellipse(ex, 5.5, 1.8, 5.0, sphere(None, ex - 0.5, 4, 2, 5, choc))
        for y in range(3, 8):
            c.set(int(ex), y, lighten(choc[2], 0.15))
    c.ellipse(23.5, 27, 2.2, 2.0, hx('#F3E7DA'))                          # tail
    for x, y in ((14, 12), (18, 12)):
        c.set(x, y, hx('#2A160B'))
    c.set(16, 14, hx('#E9798F'))
    for x in range(12, 21):                                               # ribbon
        c.set(x, 18, hx('#E94A6A'))
    for x, y in ((14, 17), (15, 17), (17, 17), (18, 17), (14, 19), (18, 19)):
        c.set(x, y, hx('#FF7A93'))
    for x, y in ((13, 10), (14, 9), (12, 21), (11, 23)):
        c.set(x, y, lighten(choc[3], 0.4))                                # shine
    c.outline(0.55)
    return c


# ---------- Christmas tree ----------

def tree_leaves():
    """Fir needles with ornaments (red, gold, blue) and little warm lights."""
    green = ramp(hx('#2F7A3E'))
    c = Canvas(32)
    for y in range(32):
        for x in range(32):
            n = hnoise(x, y, 81)
            c.set(x, y, green[3] if n > 0.82 else green[1] if n < 0.22 else green[2] if (x + y) % 3 else green[1])
    ornaments = [(4, 5, '#D8393F'), (20, 3, '#F2C14E'), (12, 12, '#4A8FE0'), (27, 11, '#D8393F'), (6, 20, '#F2C14E'),
                 (18, 22, '#D8393F'), (28, 26, '#4A8FE0'), (11, 28, '#F2C14E')]
    for ox, oy, col in ornaments:
        tones = ramp(hx(col))
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            c.set(ox + dx, oy + dy, tones[2])
        c.set(ox, oy, tones[4])
        c.set(ox + 1, oy + 1, tones[1])
    for lx, ly in ((9, 2), (25, 6), (2, 13), (17, 16), (30, 18), (23, 30), (3, 27), (14, 7)):
        c.set(lx, ly, hx('#FFF2A8'))
    return c


def tree_trunk():
    bark = ramp(hx('#6B4527'))
    c = Canvas(32)
    for y in range(32):
        for x in range(32):
            c.set(x, y, bark[1] if x % 6 == 0 or hnoise(x, y, 82) < 0.15 else bark[3] if hnoise(x, y, 83) > 0.88 else bark[2])
    return c


def tree_star():
    gold = ramp(hx('#F7CF4A'))
    c = Canvas(32)
    for y in range(32):
        for x in range(32):
            c.set(x, y, gold[4] if (x + y) % 7 == 0 else gold[3] if hnoise(x, y, 84) > 0.6 else gold[2])
    return c


def tree_icon():
    green = ramp(hx('#2F7A3E'))
    c = Canvas(32)
    for i, (y0, y1, half) in enumerate(((6, 12, 5), (11, 19, 8), (18, 27, 11))):
        for y in range(y0, y1 + 1):
            w = int(half * (y - y0 + 2) / (y1 - y0 + 2))
            for x in range(16 - w, 16 + w):
                c.set(x, y, green[3] if x < 16 - w + 2 else green[1] if x > 16 + w - 3 else green[2])
    c.rect(14, 28, 17, 31, ramp(hx('#6B4527'))[2])
    for ox, oy, col in ((13, 10, '#D8393F'), (18, 15, '#F2C14E'), (12, 17, '#4A8FE0'), (20, 23, '#D8393F'), (10, 24, '#F2C14E'), (16, 21, '#4A8FE0')):
        c.set(ox, oy, hx(col))
        c.set(ox + 1, oy, hx(col))
    star = hx('#F7CF4A')
    for x, y in ((15, 2), (16, 2), (14, 3), (15, 3), (16, 3), (17, 3), (15, 4), (16, 4), (15, 1), (16, 1), (13, 3), (18, 3), (15, 5), (16, 5)):
        c.set(x, y, star)
    c.outline(0.55)
    return c


# ---------- pet cat emotes ----------

CAT = ramp(hx('#E59A4A'))


def cat_face(c):
    """An orange tabby's face filling most of a 32x32 icon."""
    for ex, tip in ((7, 2), (24, 2)):                                     # ears
        for y in range(tip, 13):
            w = (y - tip) * 0.55
            for x in range(int(ex - w), int(ex + w) + 1):
                c.set(x, y, CAT[2] if abs(x - ex) < w - 1.2 else CAT[1])
        for y in range(tip + 4, 12):
            c.set(ex, y, hx('#F2A2BE'))
    c.ellipse(15.5, 18, 12.5, 10.0, sphere(None, 12, 14, 14, 12, CAT))
    for x in range(12, 20):                                               # tabby stripes
        if x % 3 == 0:
            for y in range(9, 12):
                c.set(x, y, CAT[0])
    c.ellipse(15.5, 23, 4.5, 3.0, lighten(CAT[2], 0.5))                   # muzzle
    for x, y in ((15, 21), (16, 21), (15, 22), (16, 22)):
        c.set(x, y, hx('#E9798F'))                                        # nose
    whisk = hx('#FFF6EA')
    for y in (22, 24):
        for x in (3, 4, 5, 6):
            c.set(x, y, whisk)
            c.set(31 - x, y, whisk)


def emote_meow():
    c = Canvas(32)
    cat_face(c)
    eye = hx('#3A7A2A')
    for ex in (10, 19):
        for x in range(ex, ex + 3):
            for y in (15, 16, 17):
                c.set(x, y, eye if x != ex + 1 else hx('#14240F'))
        c.set(ex, 15, hx('#FFFFFF'))
    c.ellipse(15.5, 26, 2.2, 1.8, hx('#6E1A26'))                         # open mouth
    c.outline(0.55)
    note = hx('#FFFFFF')
    for x, y in ((28, 3), (28, 4), (28, 5), (28, 6), (29, 3), (30, 4), (27, 6), (26, 6), (27, 7)):
        c.set(x, y, note)
    return c


def emote_hiss():
    c = Canvas(32)
    cat_face(c)
    eye = hx('#C8C832')
    for ex, slant in ((10, 1), (19, -1)):
        for i in range(4):
            c.set(ex + i, 15 + (i if slant > 0 else 3 - i) // 2, hx('#2A1A0A'))
        for x in range(ex, ex + 3):
            c.set(x, 17, eye)
        c.set(ex + 1, 17, hx('#14240F'))
    c.rect(12, 25, 19, 27, hx('#6E1A26'))
    for x in (13, 18):
        c.set(x, 25, hx('#FFFFFF'))
        c.set(x, 26, hx('#FFFFFF'))
    c.outline(0.55)
    red = hx('#E04040')
    for x, y in ((28, 2), (28, 3), (28, 4), (28, 5), (28, 7)):
        c.set(x, y, red)
        c.set(29, y, red)
    return c


def emote_purr():
    c = Canvas(32)
    cat_face(c)
    lid = hx('#5A3218')
    for ex in (10, 19):
        for x, y in ((ex, 17), (ex + 1, 16), (ex + 2, 16), (ex + 3, 17)):
            c.set(x, y, lid)
    for x, y in ((13, 26), (14, 27), (15, 26), (16, 26), (17, 27), (18, 26)):
        c.set(x, y, hx('#5A3218'))
    c.outline(0.55)
    pink = hx('#FF6F91')
    for hx0, hy0 in ((1, 3), (26, 1)):
        for x, y in ((0, 0), (1, 0), (3, 0), (4, 0), (0, 1), (1, 1), (2, 1), (3, 1), (4, 1), (1, 2), (2, 2), (3, 2), (2, 3)):
            c.set(hx0 + x, hy0 + y, pink)
    return c


# ---------- Halloween costumes (HD player layers, worn over the player's own skin) ----------

ORANGE = ramp(hx('#E9822A'))
STEM = ramp(hx('#4F8A2E'))
CARVE = hx('#3A1A0A')
GLOW = hx('#FFC94A')
BLACK = ramp(hx('#26232B'))
BONE = ramp(hx('#ECE6D4'))
SHEET = ramp(hx('#F4F4F8'))
CAPE_RED = ramp(hx('#A3212E'))
HAIR = ramp(hx('#1C1A22'))


def _ridges(face, x, y, W, H):
    if face == 'top':
        return STEM[2] if 6 <= x <= 9 and 6 <= y <= 9 else ORANGE[3] if (x // 4) % 2 else ORANGE[2]
    if face == 'bottom':
        return ORANGE[1]
    return ORANGE[1] if x % 5 == 0 else ORANGE[3] if x % 5 == 2 and y < H // 2 else ORANGE[2]


def pumpkin_head(face, x, y, W, H):
    """A jack-o'-lantern over the head: ridged orange with a carved, glowing face."""
    if face == 'front':
        tri = any(4 <= y <= 7 and abs(x - ex) <= y - 4 for ex in (4, 11))
        mouth = 10 <= y <= 12 and 3 <= x <= 12 and not ((y == 10 and x in (5, 8, 11)) or (y == 12 and x in (4, 7, 10)))
        if tri or mouth:
            return GLOW if (y in (7, 11)) else CARVE
        return _ridges(face, x, y, W, H)
    return _ridges(face, x, y, W, H)


def pumpkin_body(face, x, y, W, H):
    if face == 'front' and y <= 1:
        return STEM[2]
    return flat(ORANGE, face, x, y, W, H) if not (face == 'front' and x % 6 == 0) else ORANGE[1]


def dark_limb(face, x, y, W, H):
    if y >= H - 2 and face != 'bottom':
        return ORANGE[2]
    return flat(BLACK, face, x, y, W, H)


def skull(face, x, y, W, H):
    if face == 'front':
        if 5 <= y <= 8 and (2 <= x <= 5 or 10 <= x <= 13):
            return BLACK[1]
        if 9 <= y <= 10 and 7 <= x <= 8:
            return BLACK[1]
        if y == 13 and 3 <= x <= 12:
            return BLACK[2] if x % 2 else BONE[1]
        return BONE[2] if hnoise(x, y, 91) > 0.15 else BONE[1]
    return flat(BONE, face, x, y, W, H)


def ribcage(face, x, y, W, H):
    if face in ('front', 'back'):
        spine = x in (7, 8) and y <= 18
        ribs = y in (3, 4, 7, 8, 11, 12) and 2 <= x <= 13
        pelvis = 19 <= y <= 21 and 3 <= x <= 12
        if spine or ribs or pelvis:
            return BONE[2] if y % 4 != 0 else BONE[3]
    return flat(BLACK, face, x, y, W, H)


def bone_limb(face, x, y, W, H):
    if face in ('front', 'back') and W // 2 - 1 <= x <= W // 2 and y < H - 1:
        return BONE[2]
    if face in ('front', 'back') and y in (11, 12) and 1 <= x <= W - 2:
        return BONE[3]
    return flat(BLACK, face, x, y, W, H)


def sheet_head(face, x, y, W, H):
    if face == 'front':
        if 5 <= y <= 8 and (3 <= x <= 5 or 10 <= x <= 12):
            return hx('#141218')
        if 11 <= y <= 13 and 6 <= x <= 9 and not (y == 12 and 7 <= x <= 8):
            return hx('#141218')
    return SHEET[2] if hnoise(x, y, 92) > 0.12 else SHEET[1]


def sheet(face, x, y, W, H):
    return SHEET[1] if face in ('right', 'left') and x in (0, W - 1) else SHEET[2] if hnoise(x, y, 93) > 0.1 else SHEET[3]


def sheet_legs(face, x, y, W, H):
    """The sheet hangs to the shins with a ragged hem, so the feet show."""
    hem = 17 + (x % 4 == 1) * 2 + (x % 4 == 2) * 1
    if face == 'bottom' or y > hem:
        return None
    return sheet(face, x, y, W, H)


def vampire_hair(face, x, y, W, H):
    if face == 'top':
        return HAIR[2] if (x + y) % 5 else HAIR[3]
    if face == 'back':
        return HAIR[2] if y <= 13 else None
    if face in ('right', 'left'):
        return HAIR[2] if y <= 4 or (skins.front_dist(face, x, W) >= 6 and y <= 11) else None
    if face == 'front':
        peak = 3 - abs(x - 7.5) // 2.5
        return HAIR[3] if y <= max(0, peak) else None
    return None


def vampire_suit(face, x, y, W, H):
    if face == 'front':
        if 5 <= x <= 10 and y <= 10 - abs(x - 7.5) // 1:
            return hx('#F2F0F4') if not (y == 1 and 6 <= x <= 9) else CAPE_RED[2]
        if x <= 1 or x >= 14:
            return CAPE_RED[2]
    if face in ('right', 'left'):
        return CAPE_RED[1] if skins.front_dist(face, x, W) <= 1 else BLACK[1]
    if face == 'back':
        return BLACK[1] if y % 6 else BLACK[0]
    return flat(BLACK, face, x, y, W, H)


def vampire_legs(face, x, y, W, H):
    return flat(BLACK, face, x, y, W, H)


def flat(tones, face, x, y, W, H):
    return skins.flat(tones, face, x, y, W, H)


COSTUMES = {
    'pumpkin': (pumpkin_head, pumpkin_body, dark_limb, dark_limb),
    'skeleton': (skull, ribcage, bone_limb, bone_limb),
    'ghost': (sheet_head, sheet, sheet, sheet_legs),
    'vampire': (vampire_hair, vampire_suit, vampire_legs, vampire_legs),
}


def costume_layers(slim=False):
    out = {}
    for name, (head, body, arm, leg) in COSTUMES.items():
        c = Canvas(128)
        skins.paint(c, 'hat', head)
        skins.paint(c, 'jacket', body)
        skins.paint(c, 'rsleeve', arm, slim)
        skins.paint(c, 'lsleeve', arm, slim)
        skins.paint(c, 'rpants', leg)
        skins.paint(c, 'lpants', leg)
        out[f'costume_{name}' + ('_slim' if slim else '')] = c
    return out


def textures():
    out = {f'block/easter_egg_{i}.png': egg_shell(i) for i in range(len(EGG_DESIGNS))}
    out.update({
        'item/easter_egg.png': egg_icon(),
        'item/chocolate_bunny.png': chocolate_bunny(),
        'block/christmas_tree_leaves.png': tree_leaves(),
        'block/christmas_tree_trunk.png': tree_trunk(),
        'block/christmas_tree_star.png': tree_star(),
        'item/christmas_tree.png': tree_icon(),
        'gui/emote/meow.png': emote_meow(),
        'gui/emote/hiss.png': emote_hiss(),
        'gui/emote/purr.png': emote_purr(),
    })
    for slim in (False, True):
        for name, c in costume_layers(slim).items():
            out[f'entity/player_layer/{name}.png'] = c
    return out
