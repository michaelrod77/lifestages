"""New outfits and hairstyles painted in the reference skin's soft, painterly style (64x64 slim layout)."""
import colorsys

from mom_family import REGIONS, slim_faces
from pixel import darken, hnoise, hx, lighten

# ---------- painterly helpers ----------


def tone(tones, x, y, seed, face, key_shade=0):
    """Mostly the base tone with scattered lighter and darker pixels; sides and backs sit a little darker."""
    n = hnoise(x // 2 * 2 + y % 2, y, seed)
    dark_bias = 0.06 if face in ('right', 'left', 'back', 'bottom') else 0.0
    i = 2 + key_shade
    if n < 0.14 + dark_bias:
        i -= 1
    elif n > 0.88 + dark_bias:
        i += 1
    return tones[max(0, min(4, i))]


def soft_ramp(base):
    """Low-contrast tones like the reference skin's shading."""
    return [darken(base, 0.2), darken(base, 0.09), base, lighten(base, 0.14), lighten(base, 0.32)]


def rgb_of(t):
    r, g, b = colorsys.hsv_to_rgb(*t)
    return (round(r * 255), round(g * 255), round(b * 255), 255)


class Painter:
    def __init__(self, img, mats, palette, skin_rgb):
        self.img, self.mats = img, mats
        self.tones = {k: soft_ramp(hx(v) if isinstance(v, str) else v) for k, v in palette.items()}
        self.tones['skin'] = soft_ramp(skin_rgb)

    def paint(self, part, fn, seed=1):
        for face, (fx, fy, fw, fh) in slim_faces(part).items():
            for y in range(fh):
                for x in range(fw):
                    key = fn(face, x, y, fw, fh)
                    pos = (fx + x, fy + y)
                    if key is None:
                        continue
                    if key == 'clear':
                        self.img.putpixel(pos, (0, 0, 0, 0))
                        self.mats.pop(pos, None)
                        continue
                    shade = 0
                    if key.endswith('-d'):
                        key, shade = key[:-2], -1
                    elif key.endswith('-l'):
                        key, shade = key[:-2], 1
                    if key == 'skin' and self.mats.get(pos) == 'skin':
                        continue  # keep the reference's own skin shading where it already has skin
                    self.img.putpixel(pos, tone(self.tones[key], pos[0], pos[1], seed, face, shade))
                    self.mats[pos] = {'skin': 'skin', 'hair': 'hair'}.get(key, 'line' if key in ('shoe', 'button') else 'dress')

    def clear_part(self, part):
        self.paint(part, lambda *a: 'clear')

    def clear_hair_off_head(self):
        for pos, m in list(self.mats.items()):
            if m == 'hair' and REGIONS[pos][0] in ('jacket', 'body', 'rsleeve', 'lsleeve'):
                self.img.putpixel(pos, (0, 0, 0, 0))
                del self.mats[pos]


# ---------- outfits ----------

def cardigan(p):
    """Powder-blue cardigan over a cream blouse, long pleated skirt, flats."""
    def body(face, x, y, w, h):
        if face == 'top':
            return 'c1'
        if face == 'bottom':
            return 'c3'
        if face == 'front' and y <= 6:
            if y == 0 and 3 <= x <= 4:
                return 'skin'
            if 3 <= x <= 4:
                return 'c2'
            if x in (2, 5) and y in (2, 4, 6):
                return 'button'
            return 'c1-l' if x in (2, 5) else 'c1'
        if y <= 6:
            return 'c1'
        if y == 7:
            return 'c3-d'
        return 'c3-d' if x % 2 == 0 else 'c3'

    def arm(face, x, y, w, h):
        if face == 'top':
            return 'c1'
        if face == 'bottom':
            return 'skin'
        return 'c1' if y <= 9 else 'c1-l' if y == 10 else 'skin'

    def leg(face, x, y, w, h):
        if face == 'top':
            return 'c3'
        if face == 'bottom':
            return 'shoe'
        if y <= 9:
            return 'c3-d' if x % 2 == 0 else 'c3'
        return 'skin' if y == 10 else 'shoe'

    return body, arm, leg, None


def pinafore(p):
    """Butter-yellow pinafore over a puff-sleeve tee, knee socks, Mary Janes."""
    def body(face, x, y, w, h):
        if face == 'top':
            return 'c2'
        if face == 'bottom':
            return 'c1'
        if y >= 7:
            return 'c1-d' if y == 11 else 'c1'
        if face == 'front':
            if y == 0 and 3 <= x <= 4:
                return 'skin'
            if x in (2, 5) and y == 1:
                return 'button'
            if x in (2, 5) and y == 0:
                return 'c1'
            if 2 <= x <= 5 and 2 <= y <= 6:
                return 'c1-d' if (y == 4 and 3 <= x <= 4) else 'c1'
            return 'c2'
        if face == 'back':
            if (x in (2, 5) and y <= 2) or (x in (3, 4) and 3 <= y <= 4):
                return 'c1'
            return 'c2'
        return 'c2'

    def arm(face, x, y, w, h):
        if face == 'top':
            return 'c2'
        if face == 'bottom':
            return 'skin'
        return 'c2' if y <= 1 else 'trim' if y == 2 else 'skin'

    def sleeve(face, x, y, w, h):
        if face == 'top':
            return 'c2-l'
        if face == 'bottom':
            return 'clear'
        return 'c2-l' if y <= 1 else 'clear'

    def leg(face, x, y, w, h):
        if face == 'top':
            return 'c1'
        if face == 'bottom':
            return 'shoe'
        if y <= 2:
            return 'c1-d' if y == 2 else 'c1'
        if y <= 5:
            return 'skin'
        if y == 6:
            return 'trim'
        return 'sock' if y <= 10 else 'shoe'

    return body, arm, leg, sleeve


def sweater(p):
    """Lilac cable-knit sweater, light-wash jeans, white sneakers."""
    def body(face, x, y, w, h):
        if face == 'top':
            return 'c1'
        if face == 'bottom':
            return 'c3'
        if y >= 10:
            return 'c3-d' if y == 10 else 'c3'
        if y == 9:
            return 'c1-d' if x % 2 == 0 else 'c1-l'
        if face == 'front':
            if y == 0 and 3 <= x <= 4:
                return 'skin'
            if y <= 1 and 2 <= x <= 5:
                return 'c1-l'
            if x in (2, 5) and 2 <= y <= 8:
                return 'c1-l' if y % 2 == 0 else 'c1-d'
        return 'c1'

    def arm(face, x, y, w, h):
        if face == 'top':
            return 'c1'
        if face == 'bottom':
            return 'skin'
        return 'c1' if y <= 9 else ('c1-d' if x % 2 == 0 else 'c1-l') if y == 10 else 'skin'

    def leg(face, x, y, w, h):
        if face == 'top':
            return 'c3'
        if face == 'bottom':
            return 'sole'
        if y <= 9:
            return 'c3-l' if face == 'front' and x in (1, 2) and y in (2, 3, 6) else 'c3'
        return 'c3-l' if y == 10 else 'sneaker'

    return body, arm, leg, None


OUTFITS = {
    'cardigan': (cardigan, {'c1': '#A9C6E8', 'c2': '#FBF4EC', 'c3': '#8FA3C7', 'button': '#F4E3B5', 'shoe': '#3D4466'}),
    'pinafore': (pinafore, {'c1': '#F3D98A', 'c2': '#FBF6F2', 'trim': '#E8A88F', 'sock': '#FBF6F2', 'button': '#FFFFFF',
                            'shoe': '#8A5A3C'}),
    'sweater': (sweater, {'c1': '#C8B4E6', 'c3': '#A9C2E2', 'sneaker': '#FAFAFA', 'sole': '#C8B4E6'}),
}


# ---------- hairstyles ----------

def hair_key(x, y):
    return 'hair-l' if (x + y) % 4 == 0 else 'hair'


def bun(p, flower):
    """High bun at the back of the head with a daisy tucked in."""
    def hat(face, x, y, w, h):
        if face == 'back' and (x - 3.5) ** 2 + (y - 2.2) ** 2 <= 5.5:
            return 'hair-l' if (x - 2.5) ** 2 + (y - 1.5) ** 2 <= 1 else 'hair'
        if face == 'top' and y <= 2 and 2 <= x <= 5:
            return 'hair'
        return None
    p.paint('hat', hat, seed=7)
    for pos, col in (((60, 9), flower), ((59, 9), (255, 255, 255, 255)), ((61, 9), (255, 255, 255, 255)),
                     ((60, 8), (255, 255, 255, 255)), ((60, 10), (255, 255, 255, 255))):
        p.img.putpixel(pos, col)
        p.mats[pos] = 'flower' if col == flower else 'petal'


def braids(p, tie):
    """Two plaits over the shoulders, tied with ribbons."""
    def braid(face, x, y, w, h):
        if face != 'front' or y > 8:
            return None
        if x not in (0, 1, 6, 7):
            return None
        if y == 7:
            return 'tie'
        if y == 8:
            return 'hair-d' if x in (1, 6) else None
        return 'hair-l' if (x + y) % 2 == 0 else 'hair-d'
    p.paint('body', braid, seed=8)
    p.paint('jacket', lambda f, x, y, w, h: braid(f, x, y, w, h) if y <= 6 else None, seed=9)


def ponytail(p):
    """High ponytail with a ribbon and a blossom clip."""
    def hat(face, x, y, w, h):
        if face == 'back' and 2 <= x <= 5 and 2 <= y <= 7:
            if y == 3 and 3 <= x <= 4:
                return 'tie'
            if y >= 4 and x in (3, 4):
                return hair_key(x, y)
            if y == 2:
                return 'hair'
        return None
    p.paint('hat', hat, seed=10)

    def tail(face, x, y, w, h):
        if face == 'back' and x in (3, 4) and y <= 7:
            return 'hair-l' if (y + x) % 3 == 0 else 'hair'
        return None
    p.paint('body', tail, seed=11)
    p.paint('jacket', lambda f, x, y, w, h: ('hair' if f == 'back' and x in (2, 5) and y <= 4 else None), seed=12)


def restyle(img, mats, spec, skin_rgb, hair_rgb, flower_rgb):
    """Repaints clothes and hair for one Mom. Returns the new image and materials."""
    img, mats = img.copy(), dict(mats)
    builder, palette = OUTFITS[spec['outfit']]
    palette = dict(palette, hair=hair_rgb, tie=spec.get('tie', '#FFFFFF'))
    p = Painter(img, mats, palette, skin_rgb)
    p.clear_hair_off_head()
    for part in ('jacket', 'rsleeve', 'lsleeve', 'rpants', 'lpants'):
        p.clear_part(part)
    body, arm, leg, sleeve = builder(p)
    p.paint('body', body, seed=21)
    p.paint('rarm', arm, seed=22)
    p.paint('larm', arm, seed=23)
    p.paint('rleg', leg, seed=24)
    p.paint('lleg', leg, seed=25)
    if sleeve:
        p.paint('rsleeve', sleeve, seed=26)
        p.paint('lsleeve', sleeve, seed=27)
    style = spec['hairstyle']
    if style == 'bun':
        bun(p, flower_rgb)
    elif style == 'braids':
        braids(p, spec.get('tie'))
    elif style == 'ponytail':
        ponytail(p)
    return p.img, p.mats
