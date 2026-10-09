"""NPC baby skins (128x128, standard slim layout) in four looks, each with a clean and a dirty diaper, plus the
Baby Spawn Egg icon."""
import skins
from pixel import Canvas, hx, lighten, ramp, sphere
from skins import faces, flat, front_dist, paint

# skin, hair, onesie
LOOKS = {
    1: ('#F6D7C3', '#F0CC78', '#F7C3D4'),   # fair, blond, pink
    2: ('#EBC3A0', '#7A4A2A', '#BDE8D4'),   # light, brown, mint
    3: ('#C99572', '#3B2A20', '#E3D6F7'),   # tan, dark brown, lavender
    4: ('#8D5E42', '#221812', '#FAE7A8'),   # deep, black, butter yellow
}
EYE = hx('#2A1E18')
WHITE = hx('#FFFFFF')
BLUSH = hx('#F09AA6')
MOUTH = hx('#D06A7A')


def head(skin, hair, age=0):
    """age 0 = baby (short fringe, rosy cheeks), 1 = kid (a little longer), 2 = teen (long hair, no blush)."""
    sk, hr = ramp(hx(skin)), ramp(hx(hair))
    back_to = (9, 11, 14)[age]
    side_to = (7, 9, 12)[age]

    def fn(face, x, y, W, H):
        if face == 'top':
            return hr[3] if (x + y) % 5 == 0 else hr[2]
        if face == 'bottom':
            return sk[1]
        if face == 'back':
            return hr[2] if y <= back_to else (hr[1] if y == back_to + 1 else sk[2])
        if face in ('right', 'left'):
            fd = front_dist(face, x, W)
            if y <= 3 or (y <= side_to and fd >= 5) or (age == 2 and y <= side_to and fd >= 3):
                return hr[2]
            return sk[1] if y == H - 1 else sk[2]
        # front: a little fringe, big eyes, rosy cheeks and a small smile
        if y <= 2 or (y == 3 and x not in (5, 6, 9, 10)) or (y == 4 and x in (7, 8)):
            return hr[3] if y == 0 else hr[2]
        if 8 <= y <= 10 and x in (4, 5, 10, 11):
            return WHITE if (x, y) in ((4, 8), (10, 8)) else EYE
        if age < 2 and y in (11, 12) and (x in (1, 2, 3) or x in (12, 13, 14)):
            return BLUSH
        if age == 2 and (x <= 1 or x >= 14) and y <= side_to:
            return hr[2]
        if y == 13 and x in (7, 8):
            return MOUTH
        if y == 12 and x in (6, 9):
            return MOUTH
        return sk[1] if y == H - 1 else sk[2]
    return fn


def limb(skin):
    sk = ramp(hx(skin))
    return lambda face, x, y, W, H: flat(sk, face, x, y, W, H)


def baby_skin(look, dirty):
    skin, hair, onesie_col = LOOKS[look]
    c = Canvas(128)
    paint(c, 'head', head(skin, hair))
    for part in ('body', 'rarm', 'larm', 'rleg', 'lleg'):
        paint(c, part, limb(skin), slim=True)
    # outer layers: the onesie in this look's colour, then the diaper
    saved = skins.ONESIE
    skins.ONESIE = ramp(hx(onesie_col))
    paint(c, 'jacket', skins.onesie)
    paint(c, 'rsleeve', skins.onesie_sleeve, slim=True)
    paint(c, 'lsleeve', skins.onesie_sleeve, slim=True)
    paint(c, 'rpants', skins.onesie_leg)
    paint(c, 'lpants', skins.onesie_leg)
    skins.ONESIE = saved
    body, leg = skins.diaper_layer(dirty)
    paint(c, 'jacket', body)
    paint(c, 'rpants', leg)
    paint(c, 'lpants', leg)
    return c


# shorts for kids, hoodies for teens
SHORTS = {1: '#7FB2E8', 2: '#F2A2BE', 3: '#8A6FC8', 4: '#5E8F6E'}
HOODIE = {1: '#E8789C', 2: '#5FB592', 3: '#9C82D8', 4: '#E8B84A'}
JEANS = ramp(hx('#4A6FA5'))
SHOE = ramp(hx('#F4F4F8'))
SOLE = ramp(hx('#B8B8C8'))


def kid_skin(look):
    """Preschooler and child: t-shirt, shorts, white socks and sneakers."""
    skin, hair, shirt_col = LOOKS[look]
    shirt, shorts = ramp(hx(shirt_col)), ramp(hx(SHORTS[look]))
    c = Canvas(128)
    paint(c, 'head', head(skin, hair, 1))
    for part in ('body', 'rarm', 'larm', 'rleg', 'lleg'):
        paint(c, part, limb(skin), slim=True)

    def tee(face, x, y, W, H):
        if face == 'top':
            return shirt[3]
        if face == 'bottom' or y >= 15:
            return shorts[2] if face != 'bottom' else shorts[1]
        if face == 'front' and y == 0 and 5 <= x <= 10:
            return shirt[1]  # collar
        if face == 'front' and 5 <= y <= 9 and 6 <= x <= 9 and (x + y) % 2 == 0:
            return lighten(shirt[2], 0.5)  # a little print
        return flat(shirt, face, x, y, W, H)

    def sleeve(face, x, y, W, H):
        if face == 'bottom' or y > 7:
            return None
        return flat(shirt, face, x, y, W, 8)

    def leg(face, x, y, W, H):
        if face == 'top':
            return shorts[3]
        if face == 'bottom':
            return SOLE[2]
        if y <= 6:
            return flat(shorts, face, x, y, W, 7)
        if y in (18, 19):
            return SHOE[3] if y == 18 else SHOE[2]  # socks
        if y >= 20:
            return SOLE[2] if y == 23 else SHOE[2] if not (face == 'front' and y == 21 and x in (2, 5)) else SHORTS_ACCENT(look)
        return None

    paint(c, 'jacket', tee)
    paint(c, 'rsleeve', sleeve, slim=True)
    paint(c, 'lsleeve', sleeve, slim=True)
    paint(c, 'rpants', leg)
    paint(c, 'lpants', leg)
    return c


def SHORTS_ACCENT(look):
    return ramp(hx(SHORTS[look]))[1]


def teen_skin(look):
    """Teenager and young adult: a hoodie with drawstrings and a pocket, jeans and sneakers."""
    skin, hair, _ = LOOKS[look]
    hood = ramp(hx(HOODIE[look]))
    c = Canvas(128)
    paint(c, 'head', head(skin, hair, 2))
    for part in ('body', 'rarm', 'larm', 'rleg', 'lleg'):
        paint(c, part, limb(skin), slim=True)

    def hoodie(face, x, y, W, H):
        if face == 'front':
            if y <= 1 and 4 <= x <= 11:
                return hood[1]  # neckline
            if 2 <= y <= 7 and x in (6, 9):
                return WHITE if y < 7 else hx('#DDDDE6')  # drawstrings
            if 14 <= y <= 19 and 3 <= x <= 12:
                return hood[1] if y == 14 or x in (3, 12) else hood[2]  # pocket
            if y >= 22:
                return hood[1]  # waistband
        return flat(hood, face, x, y, W, H)

    def sleeve(face, x, y, W, H):
        if face == 'bottom' or y > 21:
            return None
        return hood[1] if y >= 20 else flat(hood, face, x, y, W, 22)

    def jeans(face, x, y, W, H):
        if face == 'top':
            return JEANS[3]
        if face == 'bottom':
            return SOLE[2]
        if y >= 20:
            return SOLE[2] if y == 23 else SHOE[2] if y != 20 else SHOE[3]
        if face == 'front' and x == W // 2 and y % 3 == 0:
            return JEANS[3]  # a little fading
        return flat(JEANS, face, x, y, W, 20)

    paint(c, 'jacket', hoodie)
    paint(c, 'rsleeve', sleeve, slim=True)
    paint(c, 'lsleeve', sleeve, slim=True)
    paint(c, 'rpants', jeans)
    paint(c, 'lpants', jeans)
    return c


def baby_egg():
    """A pastel spawn egg with speckles and a little pink heart."""
    c = Canvas(32)
    shell = ramp(hx('#FBEFD9'))
    c.fill(lambda x, y: ((x - 15.5) / 10.5) ** 2 + ((y - 17.5) / 13.0) ** 2 * (1.18 if y < 17.5 else 1.0) <= 1,
           sphere(None, 15.5, 16.0, 10.5, 13.0, shell))
    for sx, sy, col in ((10, 10, '#F2A2BE'), (20, 8, '#8EC3F2'), (22, 20, '#9ED9C0'), (9, 22, '#F7D56B'), (14, 26, '#8EC3F2'),
                        (24, 14, '#F2A2BE'), (12, 15, '#9ED9C0')):
        tones = ramp(hx(col))
        c.set(sx, sy, tones[2])
        c.set(sx + 1, sy, tones[3])
        c.set(sx, sy + 1, tones[1])
    heart = ramp(hx('#E8547C'))
    for y in range(32):
        for x in range(32):
            u, v = (x + 0.5 - 16) / 3.4, -(y + 0.5 - 18.5) / 3.4
            if (u * u + v * v - 1) ** 3 - u * u * v ** 3 <= 0:
                c.set(x, y, heart[3] if u < -0.2 and v > 0.2 else heart[2])
    c.outline(0.55)
    return c


def textures():
    out = {'item/baby_spawn_egg.png': baby_egg()}
    for look in LOOKS:
        out[f'entity/baby/baby_{look}.png'] = baby_skin(look, False)
        out[f'entity/baby/baby_{look}_dirty.png'] = baby_skin(look, True)
        out[f'entity/baby/kid_{look}.png'] = kid_skin(look)
        out[f'entity/baby/teen_{look}.png'] = teen_skin(look)
    return out
