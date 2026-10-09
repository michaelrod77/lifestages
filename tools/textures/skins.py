"""HD (128x128) player-layer textures on the standard skin layout, plus flat previews."""
from PIL import Image

from pixel import Canvas, hnoise, hx, ramp

S = 2  # HD scale: every 64x64-layout unit is 2x2 pixels

PARTS = {
    'head': (0, 0, 8, 8, 8), 'hat': (32, 0, 8, 8, 8),
    'body': (16, 16, 8, 12, 4), 'jacket': (16, 32, 8, 12, 4),
    'rarm': (40, 16, 4, 12, 4), 'rsleeve': (40, 32, 4, 12, 4),
    'larm': (32, 48, 4, 12, 4), 'lsleeve': (48, 48, 4, 12, 4),
    'rleg': (0, 16, 4, 12, 4), 'rpants': (0, 32, 4, 12, 4),
    'lleg': (16, 48, 4, 12, 4), 'lpants': (0, 48, 4, 12, 4),
}
ARMS = ('rarm', 'rsleeve', 'larm', 'lsleeve')


def faces(part, slim=False):
    u, v, w, h, d = PARTS[part]
    if slim and part in ARMS:
        w = 3
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h),
    }


def paint(c, part, fn, slim=False):
    for face, (fx, fy, fw, fh) in faces(part, slim).items():
        W, H = fw * S, fh * S
        for y in range(H):
            for x in range(W):
                col = fn(face, x, y, W, H)
                if col is not None:
                    c.set(fx * S + x, fy * S + y, col)


def front_dist(face, x, W):
    """Pixels from the front edge on a side face."""
    return (W - 1 - x) if face == 'right' else x


def soft(tones, x, y, seed, lo=0.16, hi=0.86):
    """Painterly dither: mostly the base tone, with scattered lighter and darker pixels."""
    n = hnoise(x, y, seed)
    return tones[1] if n < lo else tones[3] if n > hi else tones[2]


# ---------- layers worn on the player's own skin ----------

BLANKET = ramp(hx('#B4D3F2'))
BONNET = ramp(hx('#F4F0FB'))
RIBBON = ramp(hx('#F2A2BE'))
ONESIE = ramp(hx('#EFE7FA'))
DIAPER = ramp(hx('#FAF8FD'))
DIRTY = ramp(hx('#EEE3C4'))
BAND = ramp(hx('#C7B5EE'))
BROWN = ramp(hx('#8A5A2B'))
BOOTIE = ramp(hx('#FBF8FD'))


def flat(tones, face, x, y, W, H):
    """Clean flat fill: one highlight row on top, one shadow row at the bottom, sides a step darker."""
    if face == 'top':
        return tones[3]
    if face == 'bottom':
        return tones[1]
    if y == 0:
        return tones[3]
    if y == H - 1:
        return tones[1]
    if face in ('right', 'left', 'back'):
        return tones[2] if (face == 'back' or x not in (0, W - 1)) else tones[1]
    return tones[2]


def blanket(face, x, y, W, H):
    """Swaddle: flat blue with two wrap folds and a few white stars on the front."""
    if face == 'front' and y > 0 and (x + y) in (14, 26) and y < H - 1:
        return BLANKET[1]
    if face == 'front' and (x, y) in ((4, 6), (11, 15), (5, 19)):
        return BLANKET[4]
    return flat(BLANKET, face, x, y, W, H)


def bonnet(face, x, y, W, H):
    """Bonnet: flat with a lace edge framing the face and a pink ribbon under the chin."""
    lace = BONNET[4]
    if face in ('top', 'back'):
        return BONNET[2]
    if face in ('right', 'left'):
        fd = front_dist(face, x, W)
        if fd == 0:
            return lace
        return BONNET[2] if y <= 12 else (RIBBON[2] if fd <= 2 else None)
    if face == 'front':
        if y <= 2:
            return BONNET[2]
        if y == 3 or (y == 4 and x % 3 == 1):
            return lace
        if x <= 1 or x >= 14:
            return BONNET[2] if y <= 12 else RIBBON[2]
        if (x == 2 or x == 13) and 4 <= y <= 12:
            return lace
        if y >= 14 and (x <= 3 or x >= 12):
            return RIBBON[2]
    return None


def onesie(face, x, y, W, H):
    """Infant and toddler top: soft lilac onesie with a round collar and pink snaps (above the diaper)."""
    if face == 'bottom' or y >= 18:
        return None
    if face == 'front':
        if y == 0 and 5 <= x <= 10:
            return RIBBON[3]
        if x in (7, 8) and y in (6, 10, 14):
            return RIBBON[2]
    return flat(ONESIE, face, x, y, W, H)


def onesie_sleeve(face, x, y, W, H):
    """Long sleeves with a pink cuff; the hands stay bare."""
    if face == 'bottom' or y > 19:
        return None
    if y == 19:
        return RIBBON[3]
    return flat(ONESIE, face, x, y, W, 20)


def onesie_leg(face, x, y, W, H):
    """Leggings down to the booties; the diaper layer covers the top."""
    if face == 'bottom' or y > 17:
        return None
    return flat(ONESIE, face, x, y, W, 18)


def diaper_layer(dirty):
    tones = DIRTY if dirty else DIAPER

    def stain(face, x, y, cx, cy, rx, ry):
        if not dirty or face not in ('back', 'bottom'):
            return None
        e = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
        if e <= 1:
            return BROWN[1] if e > 0.6 else BROWN[2]
        return None

    def body(face, x, y, W, H):
        if face == 'top':
            return None
        if face == 'bottom':
            return stain(face, x, y, W / 2, H / 2, W / 2.5, H / 2.2) or tones[1]
        if y < 18:
            return None
        if y == 18:
            return BAND[3]
        if y == 19:
            return BAND[2]
        if face == 'front' and y in (20, 21) and x in (0, 1, W - 2, W - 1):
            return RIBBON[2]
        return stain(face, x, y, W / 2, 23, 5, 3.5) or (tones[1] if y == H - 1 else tones[2])

    def leg(face, x, y, W, H):
        cuff_row = 7
        if face == 'top':
            return tones[3]
        if face == 'bottom':
            return BOOTIE[1]
        if y >= 18:
            if y == 18:
                return RIBBON[3]
            if face == 'front' and y == 20 and x in (2, 5):
                return hx('#3A3048')
            if face == 'front' and y == 21 and x in (1, 6):
                return RIBBON[3]
            return BOOTIE[1] if y == 23 else BOOTIE[2]
        if y > cuff_row:
            return None
        if y == cuff_row:
            return BROWN[2] if dirty and x % 3 == 0 else BAND[3]
        return stain(face, x, y, W / 2, 1.5, 3.5, 3) or tones[2]

    return body, leg


SILVER = ramp(hx('#CFCFDA'))


def grey_hair(face, x, y, W, H):
    col = SILVER[1] if (x + y // 2) % 5 == 0 else soft(SILVER, x, y, 31, 0.1, 0.8)
    if face == 'top':
        return col
    if face == 'back':
        return col if y <= 12 else None
    if face in ('right', 'left'):
        fd = front_dist(face, x, W)
        return col if y <= 4 or (fd >= 8 and y <= 11) else None
    if face == 'front':
        return col if y <= 2 or (y == 3 and x % 3 != 0) else None
    return None


def overlays(slim=False):
    swaddle = Canvas(128)
    for part in ('jacket', 'rsleeve', 'lsleeve', 'rpants', 'lpants'):
        paint(swaddle, part, blanket, slim)
    paint(swaddle, 'hat', bonnet)
    out = {'newborn_swaddle': swaddle}
    top = Canvas(128)
    paint(top, 'jacket', onesie)
    paint(top, 'rsleeve', onesie_sleeve, slim)
    paint(top, 'lsleeve', onesie_sleeve, slim)
    paint(top, 'rpants', onesie_leg)
    paint(top, 'lpants', onesie_leg)
    out['onesie'] = top
    for name, dirty in (('diaper', False), ('diaper_dirty', True)):
        c = Canvas(128)
        body, leg = diaper_layer(dirty)
        paint(c, 'jacket', body)
        paint(c, 'rpants', leg)
        paint(c, 'lpants', leg)
        out[name] = c
    hair = Canvas(128)
    paint(hair, 'hat', grey_hair)
    out['elderly_hair'] = hair
    harness = Canvas(128)
    paint(harness, 'jacket', carrier)
    out['baby_carrier'] = harness
    return out


# ---------- baby carrier (worn by a grown-up who is carrying a baby) ----------

CARRIER = ramp(hx('#4A4F57'))
CARRIER_PAD = ramp(hx('#9AA0A8'))
BUCKLE = hx('#1E2024')


def carrier(face, x, y, W, H):
    """Dark grey soft-structured carrier: front panel with a padded headrest, shoulder straps, waist belt, X on the back."""
    strap = lambda t: CARRIER[3] if t == 0 else CARRIER[1] if t == 2 else CARRIER[2]
    belt = 18 <= y <= 20
    if face == 'top':
        return CARRIER[2] if x in (2, 3, 12, 13) else None
    if face == 'bottom':
        return None
    if face == 'front':
        if belt:
            return BUCKLE if x in (7, 8) and y == 19 else CARRIER[1] if y == 20 else CARRIER[2]
        if 7 <= y <= 17 and 3 <= x <= 12:
            if y in (7, 8):
                return CARRIER_PAD[3] if y == 7 else CARRIER_PAD[2]  # padded headrest
            return CARRIER[1] if x in (3, 12) else CARRIER[2]
        if x in (2, 3, 12, 13) and y < 7:
            return strap(x % 2)
        return None
    if face == 'back':
        if belt:
            return CARRIER[1] if y == 20 else CARRIER[2]
        d1, d2 = abs(x - (2 + y * 0.66)), abs(x - (13 - y * 0.66))
        if y < 18 and (d1 < 1.2 or d2 < 1.2):
            return CARRIER[2]
        return None
    # sides: just the belt
    return (CARRIER[1] if y == 20 else CARRIER[2]) if belt else None


# ---------- previews ----------

def crop_face(img, part, face, slim=False):
    fx, fy, fw, fh = faces(part, slim)[face]
    return img.crop((fx * S, fy * S, (fx + fw) * S, (fy + fh) * S))


def render(layers, view='front', baby=False, slim=True, ss=1):
    """Flat front or back view. layers = 128x128 images, base skin first."""
    aw = 6 if slim else 8
    face = 'front' if view == 'front' else 'back'
    near, far = ('r', 'l') if view == 'front' else ('l', 'r')
    if baby:
        # Toddler proportions: head about as wide as the shoulders, slim body, short legs.
        W, H = 20, 41
        place = {'head': (1, 0, 18, 18), 'body': (4, 18, 12, 14),
                 near + 'arm': (0, 18, 4, 12), far + 'arm': (16, 18, 4, 12),
                 near + 'leg': (4, 32, 6, 9), far + 'leg': (10, 32, 6, 9)}
    else:
        W, H = 16 + 2 * aw, 64
        place = {'head': (aw, 0, 16, 16), 'body': (aw, 16, 16, 24),
                 near + 'arm': (0, 16, aw, 24), far + 'arm': (aw + 16, 16, aw, 24),
                 near + 'leg': (aw, 40, 8, 24), far + 'leg': (aw + 8, 40, 8, 24)}
    over = {'head': 'hat', 'body': 'jacket', 'rarm': 'rsleeve', 'larm': 'lsleeve', 'rleg': 'rpants', 'lleg': 'lpants'}
    out = Image.new('RGBA', (W * ss, H * ss), (0, 0, 0, 0))
    for base, (x, y, w, h) in place.items():
        x, y, w, h = x * ss, y * ss, w * ss, h * ss
        for i, layer in enumerate(layers):
            for part in ((base, over[base]) if i == 0 else (over[base],)):
                tile = crop_face(layer, part, face, slim)
                if tile.size != (w, h):
                    tile = tile.resize((w, h), Image.NEAREST)
                out.alpha_composite(tile, (x, y))
    return out
