"""NPC Moms built from the team's reference skin (Mom 1).

Mom 1 is the reference itself, moved to a clean slim-arm layout. Moms 2-6 are recolors of it
(hair, skin, dress, eyes, flowers) plus a few shape edits, so all six share its soft, painterly look.
"""
import colorsys

from PIL import Image

from pixel import Canvas, darken, hx, mix

# ---------- slim layout ----------

SLIM_PARTS = {
    'head': (0, 0, 8, 8, 8), 'hat': (32, 0, 8, 8, 8),
    'body': (16, 16, 8, 12, 4), 'jacket': (16, 32, 8, 12, 4),
    'rarm': (40, 16, 3, 12, 4), 'rsleeve': (40, 32, 3, 12, 4),
    'larm': (32, 48, 3, 12, 4), 'lsleeve': (48, 48, 3, 12, 4),
    'rleg': (0, 16, 4, 12, 4), 'rpants': (0, 32, 4, 12, 4),
    'lleg': (16, 48, 4, 12, 4), 'lpants': (0, 48, 4, 12, 4),
}


def slim_faces(part):
    u, v, w, h, d = SLIM_PARTS[part]
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + d + w + d, v + d, w, h),
    }


def to_slim(src):
    """The reference draws 3-wide arms in the 4-wide layout with a grey filler column. Repack them."""
    out = src.copy()
    moves = []
    for u, v in ((40, 16), (40, 32)):  # right arm and sleeve: filler on the front's first column
        moves += [((u + 5, v, 3, 4), (u + 4, v)), ((u + 9, v, 3, 4), (u + 7, v)),
                  ((u, v + 4, 4, 12), (u, v + 4)), ((u + 5, v + 4, 3, 12), (u + 4, v + 4)),
                  ((u + 8, v + 4, 4, 12), (u + 7, v + 4)), ((u + 12, v + 4, 3, 12), (u + 11, v + 4))]
    for u, v in ((32, 48), (48, 48)):  # left arm and sleeve: filler on the front's last column
        moves += [((u + 4, v, 3, 4), (u + 4, v)), ((u + 8, v, 3, 4), (u + 7, v)),
                  ((u, v + 4, 4, 12), (u, v + 4)), ((u + 4, v + 4, 3, 12), (u + 4, v + 4)),
                  ((u + 8, v + 4, 4, 12), (u + 7, v + 4)), ((u + 13, v + 4, 3, 12), (u + 11, v + 4))]
    for u, v in ((40, 16), (40, 32), (32, 48), (48, 48)):
        for y in range(v, v + 16):
            for x in range(u, u + 16):
                out.putpixel((x, y), (0, 0, 0, 0))
    for (sx, sy, w, h), (dx, dy) in moves:
        out.paste(src.crop((sx, sy, sx + w, sy + h)), (dx, dy))
    return out


# ---------- materials ----------

def hexset(s):
    return {tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) for c in s.split()}


LINE = hexset('312757 211941 605866 383334 332d33 332d34 2a2431 201a2e 383335 393334 383234 242424 504043 704a52 cac9c9')
BUCKLE = hexset('a38259 ca9f68')
EYE = hexset('1a4a81 266ec0')
EYE_WHITE = hexset('fde5cb b4829e')
LEAF = hexset('69674f 68674f')
FLOWER = hexset('c07c96 d48893 d58893 eb9b9c eb9b9d af7199 af7299 e89294 b07299 c17c96')
PETAL = hexset('f5dcce fce9d8')
SKIN = hexset('f3b198 f8c9a6 dca09a db9f9a db9f9b dba09a f3b199 f2b198 f3b098 fcdab4 e19161 c68f9c c6909c '
              'c8919c c8909c f3a498 f3ac98 f4b2a6 a576a0')


def hsv(c):
    return colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)


def region_map():
    regions = {}
    for part in SLIM_PARTS:
        for face, (fx, fy, fw, fh) in slim_faces(part).items():
            for y in range(fy, fy + fh):
                for x in range(fx, fx + fw):
                    regions[(x, y)] = (part, face, x - fx, y - fy)
    return regions


REGIONS = region_map()


def classify(img):
    mats = {}
    for (x, y), (part, face, fx, fy) in REGIONS.items():
        p = img.getpixel((x, y))
        if p[3] == 0:
            continue
        c = p[:3]
        h, s, v = hsv(c)
        head = part in ('head', 'hat')
        if c in LINE:
            m = 'line'
        elif c in BUCKLE:
            m = 'buckle'
        elif c in EYE:
            m = 'eye'
        elif head and c in EYE_WHITE:
            m = 'eyewhite'
        elif c in LEAF:
            m = 'leaf'
        elif head and c in FLOWER:
            m = 'flower'
        elif head and c in PETAL:
            m = 'petal'
        elif s >= 0.38 and (v <= 0.75 or (s >= 0.55 and v <= 0.86)):
            m = 'hair'
        elif c in SKIN and not (part not in ('head', 'hat') and c == (0xa5, 0x76, 0xa0)):
            m = 'skin'
        elif head:
            m = 'skin'
        else:
            m = 'dress'
        mats[(x, y)] = m
    return mats


def stats(img, mats, m):
    vals = [hsv(img.getpixel(k)[:3]) for k, mm in mats.items() if mm == m]
    if not vals:
        return (0.0, 0.0, 0.5)
    hs = [h for h, s, v in vals]
    return (circ_mean(hs), sum(s for _, s, _ in vals) / len(vals), sum(v for _, _, v in vals) / len(vals))


def circ_mean(hs):
    import math
    x = sum(math.cos(h * 2 * math.pi) for h in hs)
    y = sum(math.sin(h * 2 * math.pi) for h in hs)
    return (math.atan2(y, x) / (2 * math.pi)) % 1.0


def hue_diff(a, b):
    d = (a - b) % 1.0
    return d - 1.0 if d > 0.5 else d


def clamp(x, lo=0.0, hi=1.0):
    return max(lo, min(hi, x))


def remap(c, src, tgt, hk=0.5, sk=1.0, shadow_shift=0.0):
    """Moves a color from the source material's center to the target's, keeping its offsets."""
    h, s, v = hsv(c)
    mh, ms, mv = src
    th, ts, tv = tgt
    vk = 0.5 + 0.5 * min(1.0, tv / mv)
    nh = (th + hue_diff(h, mh) * hk + (shadow_shift if v < mv - 0.04 else 0.0)) % 1.0
    ns = clamp(ts + (s - ms) * sk)
    nv = clamp(tv + (v - mv) * vk, 0.06, 1.0)
    r, g, b = colorsys.hsv_to_rgb(nh, ns, nv)
    return (round(r * 255), round(g * 255), round(b * 255))


def T(h, s, v):
    return (h / 360.0, s, v)


# ---------- the family ----------

MOMS = {
    'mom_2': dict(note='Black hair in a high bun with a daisy, light tan skin, powder-blue cardigan over a cream blouse, long pleated skirt.',
                  hair=T(250, 0.22, 0.2), skin=T(24, 0.36, 0.86), dress=T(210, 0.22, 0.92),
                  flower=T(48, 0.62, 0.96), eye=T(25, 0.6, 0.38), outfit='cardigan', hairstyle='bun'),
    'mom_3': dict(note='Dark hair in two braids with coral ribbons and flowers, warm tan skin, butter-yellow pinafore over a puff-sleeve tee, knee socks.',
                  hair=T(15, 0.45, 0.19), skin=T(24, 0.42, 0.8), dress=T(46, 0.3, 0.96),
                  flower=T(12, 0.55, 0.92), eye=T(24, 0.7, 0.3), outfit='pinafore', hairstyle='braids', tie='#E8A88F'),
    'mom_4': dict(note='Long blonde hair with blue forget-me-nots, fair skin, sage-green dress.',
                  hair=T(40, 0.5, 0.86), skin=T(18, 0.28, 0.95), dress=T(100, 0.2, 0.86),
                  flower=T(215, 0.48, 0.92), eye=T(140, 0.45, 0.5)),
    'mom_5': dict(note='Black hair in a high ponytail with a ribbon and white blossoms, light olive skin, lilac cable-knit sweater, light jeans, white sneakers.',
                  hair=T(300, 0.14, 0.15), skin=T(28, 0.36, 0.84), dress=T(275, 0.24, 0.9),
                  flower=T(330, 0.06, 0.98), eye=T(25, 0.65, 0.28), outfit='sweater', hairstyle='ponytail', tie='#F4EEFB'),
    'mom_6': dict(note='Shoulder-length ginger hair with red roses, freckled fair skin, blush-pink dress.',
                  hair=T(18, 0.68, 0.72), skin=T(20, 0.24, 0.97), dress=T(345, 0.2, 0.96),
                  flower=T(356, 0.62, 0.82), eye=T(150, 0.3, 0.52), short=True, freckles=True),
}


def recolor(img, mats, spec):
    src = {m: stats(img, mats, m) for m in ('hair', 'skin', 'dress', 'eye', 'flower')}
    out = img.copy()
    for (x, y), m in mats.items():
        p = img.getpixel((x, y))
        c = p[:3]
        if m == 'hair' and spec.get('hair'):
            c = remap(c, src['hair'], spec['hair'], hk=0.4)
        elif m == 'skin' and spec.get('skin'):
            c = remap(c, src['skin'], spec['skin'], hk=0.35)
        elif m == 'dress' and spec.get('dress'):
            c = remap(c, src['dress'], spec['dress'], hk=0.15, sk=0.9, shadow_shift=0.04)
        elif m == 'eye' and spec.get('eye'):
            c = remap(c, src['eye'], spec['eye'], hk=0.3)
        elif m == 'flower' and spec.get('flower'):
            c = remap(c, src['flower'], spec['flower'], hk=0.3)
        out.putpixel((x, y), c + (p[3],))
    return out


def shorten_hair(img, mats):
    """Cuts the hair at the shoulders: hair on the torso becomes dress, hair on the jacket layer goes."""
    out = img.copy()
    fx0, fy0, _, _ = slim_faces('body')['front']
    for (x, y), m in mats.items():
        if m != 'hair':
            continue
        part, face, fx, fy = REGIONS[(x, y)]
        if part == 'jacket' and (fy >= 2 or face in ('top', 'bottom')):
            out.putpixel((x, y), (0, 0, 0, 0))
        elif part == 'body' and (fy >= 2 or face in ('top', 'bottom')):
            row = 6 if fy == 6 else 7 + (fy + fx) % 5
            col = fx % 8 if face != 'back' else 7 - fx % 8
            out.putpixel((x, y), img.getpixel((fx0 + col, fy0 + row)))
    return out


# ---------- HD finishing ----------

def upscale(img):
    return Canvas.from_image(img.resize((128, 128), Image.NEAREST))


def head_front_origin():
    fx, fy, _, _ = slim_faces('head')['front']
    return fx * 2, fy * 2


def add_glasses(c):
    ox, oy = head_front_origin()
    frame = hx('#7A5A44')
    for x0 in (1, 9):
        for x in range(x0, x0 + 6):
            for y in (9, 14):
                c.set(ox + x, oy + y, frame)
        for y in range(9, 15):
            c.set(ox + x0, oy + y, frame)
            c.set(ox + x0 + 5, oy + y, frame)
    c.set(ox + 7, oy + 10, frame)
    c.set(ox + 8, oy + 10, frame)
    for y in (11, 12):
        c.set(ox + 2, oy + y, mix(c.get(ox + 2, oy + y), (255, 255, 255, 255), 0.25))
        c.set(ox + 10, oy + y, mix(c.get(ox + 10, oy + y), (255, 255, 255, 255), 0.25))


def add_smile_lines(c):
    ox, oy = head_front_origin()
    for x, y in ((0, 12), (15, 12), (3, 15), (12, 15)):
        p = c.get(ox + x, oy + y)
        if p:
            c.set(ox + x, oy + y, darken(p, 0.12))


def add_freckles(c):
    ox, oy = head_front_origin()
    for x, y in ((3, 13), (5, 13), (4, 14), (10, 13), (12, 13), (11, 14)):
        p = c.get(ox + x, oy + y)
        if p:
            c.set(ox + x, oy + y, darken(p, 0.18))


def age(img, mats):
    """Elderly look: silver hair and faded, paler eyes."""
    eh, es, ev = stats(img, mats, 'eye')
    return recolor(img, mats, {'hair': T(255, 0.05, 0.82), 'eye': (eh, es * 0.4, min(0.85, ev + 0.22))})


def build_family(reference_path):
    """Returns {name: (Canvas young, Canvas elderly, note)}."""
    ref = to_slim(Image.open(reference_path).convert('RGBA'))
    mats = classify(ref)
    family = {}
    old = upscale(age(ref, mats))
    add_smile_lines(old)
    family['mom_1'] = (upscale(ref), old, 'Your reference skin: long wavy brown hair, rose crown, white and lavender dress.')
    for name, spec in MOMS.items():
        img = recolor(ref, mats, spec)
        if spec.get('short'):
            img = shorten_hair(img, mats)
        m2 = classify_like(mats, img)
        if spec.get('outfit'):
            import outfits
            sh, ss, sv = stats(img, m2, 'skin')
            img, m2 = outfits.restyle(img, m2, spec, outfits.rgb_of((sh, ss, sv)),
                                      outfits.rgb_of(spec['hair']), outfits.rgb_of(spec['flower']))
        young = upscale(img)
        aged = upscale(age(img, m2))
        if spec.get('freckles'):
            add_freckles(young)
            add_freckles(aged)
        add_smile_lines(aged)
        family[name] = (young, aged, spec['note'])
    return family


def remove_flowers(img, mats):
    """Flowers on the head become hair; flowers on the hat layer are removed."""
    out, m2 = img.copy(), dict(mats)
    for k, m in mats.items():
        if m in ('flower', 'petal', 'leaf'):
            if REGIONS[k][0] == 'hat':
                out.putpixel(k, (0, 0, 0, 0))
                del m2[k]
            else:
                out.putpixel(k, (0x65, 0x39, 0x33, 255))
                m2[k] = 'hair'
    return out, m2


def build_sample(reference_path):
    """A neutral sample player for previewing the baby and elderly layers."""
    ref = to_slim(Image.open(reference_path).convert('RGBA'))
    mats = classify(ref)
    img, m2 = remove_flowers(ref, mats)
    img = recolor(img, m2, {'hair': T(28, 0.45, 0.42), 'dress': T(165, 0.22, 0.88)})
    img = shorten_hair(img, m2)
    return upscale(img)


def classify_like(mats, img):
    """Materials for a recolored Mom: same as the reference, minus pixels removed or turned to dress."""
    out = {}
    for k, m in mats.items():
        p = img.getpixel(k)
        if p[3] == 0:
            continue
        part = REGIONS[k][0]
        out[k] = 'dress' if (m == 'hair' and part in ('body',) and REGIONS[k][3] >= 2 and hsv(p[:3])[1] < 0.38) else m
    return out
