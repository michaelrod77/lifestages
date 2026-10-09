"""Nursery textures: changing table, music box and rattle (HD 32x32, same soft modern style as the crib)."""
import math

from pixel import Canvas, darken, hnoise, hx, lighten, ramp, seg, sphere, tube

PAINT = ramp(hx('#F3EEE6'))      # warm white painted wood
PAD = ramp(hx('#BCD8F3'))        # the crib mattress blue
PINK = ramp(hx('#F2A9C2'))
GOLD = [hx('#7E5D12'), hx('#A9801E'), hx('#E2B83F'), hx('#F2CF63'), hx('#FBE7A1')]
WOOD = ramp(hx('#E0BA86'))


def bevel(c, x0, y0, x1, y1, tones):
    """A flat panel with a light top-left edge and a dark bottom-right edge."""
    c.rect(x0, y0, x1, y1, lambda x, y: tones[3] if x == x0 or y == y0 else tones[1] if x == x1 or y == y1 else tones[2])


def changing_table_front():
    c = Canvas(32)
    bevel(c, 0, 0, 31, 31, PAINT)
    for y0 in (3, 17):
        bevel(c, 3, y0, 28, y0 + 11, [None, darken(PAINT[2], 0.06), PAINT[2], PAINT[4], None])
        c.rect(4, y0 + 1, 27, y0 + 10, PAINT[2])
        c.ellipse(15.5, y0 + 5.5, 2.0, 2.0, sphere(None, 15.5, y0 + 5.5, 2.0, 2.0, GOLD))
    return c


def changing_table_side():
    c = Canvas(32)
    bevel(c, 0, 0, 31, 31, PAINT)
    bevel(c, 4, 4, 27, 27, [None, PAINT[3], PAINT[2], PAINT[1], None])
    c.rect(5, 5, 26, 26, PAINT[2])
    # a little duck decal
    duck = ramp(hx('#F7D56B'))
    c.ellipse(15, 18, 5.0, 3.6, sphere(None, 15, 18, 5.0, 3.6, duck))
    c.ellipse(19, 13.5, 2.8, 2.8, sphere(None, 19, 13.5, 2.8, 2.8, duck))
    c.rect(21, 14, 23, 14, hx('#F08A3C'))
    c.set(19, 12, hx('#3A2A20'))
    return c


def changing_table_top():
    c = Canvas(32)
    c.rect(0, 0, 31, 31, lambda x, y: WOOD[3] if hnoise(x // 6, y, 9) > 0.9 else WOOD[2] if y % 8 else WOOD[1])
    return c


def changing_table_pad():
    c = Canvas(32)
    c.rect(0, 0, 31, 31, lambda x, y: PAD[1] if x in (0, 31) or y in (0, 31) else PAD[2])
    for y in range(2, 30):
        for x in range(2, 30):
            if (x + y) % 8 == 0 or (x - y) % 8 == 0:
                c.set(x, y, darken(PAD[2], 0.07))
    for sx, sy in ((8, 8), (24, 8), (16, 16), (8, 24), (24, 24)):
        c.set(sx, sy, hx('#FFFFFF'))
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c.set(sx + dx, sy + dy, lighten(PAD[2], 0.6))
    return c


def music_box_side():
    c = Canvas(32)
    c.rect(0, 0, 31, 31, lambda x, y: PINK[3] if y < 2 else PINK[1] if y > 29 else PINK[2])
    c.rect(0, 5, 31, 6, lambda x, y: GOLD[3] if y == 5 else GOLD[1])
    for x in range(2, 31, 6):
        c.ellipse(x + 0.5, 18.5, 1.6, 1.6, sphere(None, x + 0.5, 18.5, 1.6, 1.6, ramp(hx('#FFFFFF'))))
    return c


def music_box_front():
    c = music_box_side()
    # a gold keyhole plate and a heart
    c.ellipse(15.5, 18.5, 4.5, 4.5, sphere(None, 15.5, 18.5, 4.5, 4.5, GOLD))
    for y in range(32):
        for x in range(32):
            u, v = (x + 0.5 - 15.5) / 2.6, -(y + 0.5 - 18.2) / 2.6
            if (u * u + v * v - 1) ** 3 - u * u * v ** 3 <= 0:
                c.set(x, y, hx('#E8547C'))
    return c


def music_box_top():
    c = Canvas(32)
    c.rect(0, 0, 31, 31, lambda x, y: GOLD[2] if x < 2 or y < 2 or x > 29 or y > 29 else PINK[3] if x + y < 20 else PINK[2])
    # a gold star in the middle
    pts = []
    for i in range(11):
        a = math.pi / 2 + i * math.pi / 5
        r = 7.5 if i % 2 == 0 else 3.2
        pts.append((15.5 + r * math.cos(a), 15.5 - r * math.sin(a)))

    def inside(px, py):
        n, j, hit = len(pts), len(pts) - 1, False
        for i in range(n):
            xi, yi = pts[i]
            xj, yj = pts[j]
            if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
                hit = not hit
            j = i
        return hit

    c.fill(inside, lambda x, y: GOLD[4] if x + y < 30 else GOLD[2])
    return c


def music_box_inside():
    c = Canvas(32)
    velvet = ramp(hx('#7C3A68'))
    c.rect(0, 0, 31, 31, lambda x, y: velvet[1] if hnoise(x, y, 77) < 0.2 else velvet[2])
    c.ellipse(15.5, 15.5, 6, 6, sphere(None, 15.5, 15.5, 6, 6, ramp(hx('#D9D9E6'))))
    return c


def rattle():
    c = Canvas(32)
    handle = ramp(hx('#F7D56B'))
    c.stroke(seg((7.5, 26.5), (17.0, 15.0)), 1.6, tube(handle, 1.6))
    c.ellipse(6.5, 27.5, 2.6, 2.6, sphere(None, 6.5, 27.5, 2.6, 2.6, ramp(hx('#8FD3B6'))))
    c.ellipse(21.0, 10.5, 8.0, 8.0, sphere(None, 21.0, 10.5, 8.0, 8.0, PINK))
    # stripes and a window of beads
    for y in range(32):
        for x in range(32):
            px, py = x + 0.5 - 21.0, y + 0.5 - 10.5
            if px * px + py * py <= 7.0 ** 2 and abs(px - py) < 1.2:
                c.set(x, y, lighten(PINK[2], 0.55))
    for bx, by, col in ((18, 8, '#7FB2E8'), (23, 12, '#F7D56B'), (21, 14, '#8FD3B6')):
        c.ellipse(bx + 0.5, by + 0.5, 1.4, 1.4, sphere(None, bx + 0.5, by + 0.5, 1.4, 1.4, ramp(hx(col))))
    c.outline(0.55)
    return c


MINT = ramp(hx('#9ED9C0'))


def trash_can_side():
    """Pastel mint metal with vertical ridges, a darker foot band and a little diaper sticker."""
    c = Canvas(32)
    c.rect(0, 0, 31, 31, lambda x, y: MINT[3] if x % 6 == 1 else MINT[1] if x % 6 == 4 else MINT[2])
    c.rect(0, 26, 31, 31, lambda x, y: MINT[1] if y > 26 else MINT[0])
    c.rect(0, 8, 31, 8, MINT[3])
    # sticker: a white diaper shape
    white = ramp(hx('#FFFFFF'))
    c.rect(11, 13, 20, 15, white[3])
    c.fill(lambda x, y: 11 <= x <= 21 and 15 <= y <= 21 and abs(x - 16) <= 5 - max(0, y - 18), white[2])
    c.rect(12, 13, 13, 14, hx('#F2A9C2'))
    c.rect(18, 13, 19, 14, hx('#F2A9C2'))
    return c


def trash_can_lid():
    c = Canvas(32)
    c.rect(0, 0, 31, 31, lambda x, y: MINT[3] if (x - 15.5) ** 2 + (y - 15.5) ** 2 < 49 else MINT[2])
    c.fill(lambda x, y: 9 <= ((x - 16) ** 2 + (y - 16) ** 2) ** 0.5 <= 10, MINT[1])
    c.rect(0, 0, 31, 0, MINT[4])
    c.rect(0, 31, 31, 31, MINT[1])
    return c


def trash_can_rim():
    c = Canvas(32)
    c.rect(0, 0, 31, 31, lambda x, y: MINT[1] if y % 8 == 7 else MINT[2] if y % 8 > 1 else MINT[3])
    return c


def trash_can_inside():
    c = Canvas(32)
    dark = ramp(hx('#3E5A50'))
    c.rect(0, 0, 31, 31, lambda x, y: dark[1] if hnoise(x, y, 88) < 0.25 else dark[2])
    return c


def textures():
    return {
        'block/changing_table_front.png': changing_table_front(),
        'block/changing_table_side.png': changing_table_side(),
        'block/changing_table_top.png': changing_table_top(),
        'block/changing_table_pad.png': changing_table_pad(),
        'block/music_box_side.png': music_box_side(),
        'block/music_box_front.png': music_box_front(),
        'block/music_box_top.png': music_box_top(),
        'block/music_box_inside.png': music_box_inside(),
        'item/rattle.png': rattle(),
        'block/trash_can_side.png': trash_can_side(),
        'block/trash_can_lid.png': trash_can_lid(),
        'block/trash_can_rim.png': trash_can_rim(),
        'block/trash_can_inside.png': trash_can_inside(),
    }
