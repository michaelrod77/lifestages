"""Placed birthday presents (block faces per tier) and the opened-present GUI."""
from pixel import Canvas, darken, hnoise, hx, lighten, ramp

PRESENT_COLORS = {
    'common': ('#F2A2BE', '#FFFFFF'),
    'uncommon': ('#9BD8A8', '#F5D35B'),
    'rare': ('#7FB2E8', '#E8ECF2'),
    'epic': ('#B38AE0', '#F2CF63'),
    'legendary': ('#F2C14E', '#D84A4A'),
}


def _paper(c, pr, x0, y0, x1, y1, seed):
    """Wrapping paper with soft polka dots."""
    c.rect(x0, y0, x1, y1, pr[2])
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if (x % 8, y % 8) in ((2, 2), (6, 6)):
                c.set(x, y, pr[4])
                c.set(x + 1, y, pr[3])
            elif hnoise(x, y, seed) > 0.97:
                c.set(x, y, pr[3])


def present_side(tier):
    """Lid band (rows 0-5) over the box (rows 6-23), with the ribbon running down the middle."""
    paper, ribbon = PRESENT_COLORS[tier]
    pr, rr = ramp(hx(paper)), ramp(hx(ribbon))
    c = Canvas(32)
    _paper(c, pr, 0, 6, 31, 31, 61)
    c.rect(0, 0, 31, 5, lambda x, y: pr[4] if y == 0 else pr[1] if y == 5 else pr[3])
    c.rect(0, 6, 31, 6, darken(pr[2], 0.3))  # shadow under the lid
    c.rect(13, 0, 18, 31, lambda x, y: rr[3] if x == 13 else rr[1] if x == 18 else rr[2])
    c.rect(13, 6, 18, 6, rr[1])
    return c


def present_top(tier):
    paper, ribbon = PRESENT_COLORS[tier]
    pr, rr = ramp(hx(paper)), ramp(hx(ribbon))
    c = Canvas(32)
    _paper(c, pr, 0, 0, 31, 31, 62)
    c.rect(0, 0, 31, 0, pr[4])
    c.rect(0, 31, 31, 31, pr[1])
    c.rect(13, 0, 18, 31, lambda x, y: rr[3] if x == 13 else rr[1] if x == 18 else rr[2])
    c.rect(0, 13, 31, 18, lambda x, y: rr[3] if y == 13 else rr[1] if y == 18 else rr[2])
    return c


def present_ribbon(tier):
    rr = ramp(hx(PRESENT_COLORS[tier][1]))
    c = Canvas(32)
    c.rect(0, 0, 31, 31, lambda x, y: rr[3] if (x + y) % 12 < 2 else rr[2] if y > 3 else rr[3])
    c.rect(0, 28, 31, 31, rr[1])
    return c


# ---------- GUI: 176 x 133 on a 256 x 256 sheet, same layout as a hopper ----------

PANEL = hx('#C6C6C6')
LIGHT = hx('#FFFFFF')
DARK = hx('#555555')
SLOT_FILL = hx('#8B8B8B')
SLOT_DARK = hx('#373737')


def _panel(c, w, h):
    c.rect(0, 0, w - 1, h - 1, PANEL)
    edge = hx('#000000')
    for x in range(2, w - 2):
        c.set(x, 0, edge)
        c.set(x, h - 1, edge)
        c.set(x, 1, LIGHT)
        c.set(x, 2, LIGHT)
        c.set(x, h - 2, DARK)
        c.set(x, h - 3, DARK)
    for y in range(2, h - 2):
        c.set(0, y, edge)
        c.set(w - 1, y, edge)
        c.set(1, y, LIGHT)
        c.set(2, y, LIGHT)
        c.set(w - 2, y, DARK)
        c.set(w - 3, y, DARK)
    for x, y in ((1, 1), (w - 2, h - 2)):
        c.set(x, y, edge)
    for x, y in ((0, 0), (1, 0), (0, 1), (w - 1, 0), (w - 2, 0), (w - 1, 1), (0, h - 1), (1, h - 1), (0, h - 2),
                 (w - 1, h - 1), (w - 2, h - 1), (w - 1, h - 2)):
        c.clear(x, y)
    c.set(w - 2, 1, PANEL)
    c.set(1, h - 2, PANEL)
    c.set(w - 3, 2, PANEL)
    c.set(2, h - 3, PANEL)


def _slot(c, x, y):
    """An 18 x 18 slot frame whose item area starts at (x + 1, y + 1)."""
    c.rect(x, y, x + 17, y + 17, SLOT_FILL)
    c.rect(x, y, x + 16, y, SLOT_DARK)
    c.rect(x, y, x, y + 16, SLOT_DARK)
    c.rect(x + 1, y + 17, x + 17, y + 17, LIGHT)
    c.rect(x + 17, y + 1, x + 17, y + 17, LIGHT)


def present_gui():
    c = Canvas(256)
    w, h = 176, 133
    _panel(c, w, h)
    # wrapping paper behind the four slots, with a ribbon and a bow
    pr, rr = ramp(hx('#F7C3D4')), ramp(hx('#E8547C'))
    x0, y0, x1, y1 = 48, 14, 127, 38
    _paper(c, pr, x0, y0, x1, y1, 63)
    c.rect(x0, y0, x1, y0, darken(pr[2], 0.35))
    c.rect(x0, y0, x0, y1, darken(pr[2], 0.35))
    c.rect(x0, y1, x1, y1, lighten(pr[2], 0.5))
    c.rect(x1, y0, x1, y1, lighten(pr[2], 0.5))
    c.rect(x0 + 1, 27, x1 - 1, 30, lambda x, y: rr[3] if y == 27 else rr[1] if y == 30 else rr[2])
    for i in range(4):
        _slot(c, 52 + i * 18, 19)
    for bx in (129, 139):
        c.ellipse(bx + 0.5, 28.5, 4.5, 3.4, lambda x, y: rr[3] if y < 28 else rr[1])
    c.ellipse(134.5, 28.5, 2.3, 2.3, rr[2])
    # player inventory and hotbar
    for row in range(3):
        for col in range(9):
            _slot(c, 7 + col * 18, 50 + row * 18)
    for col in range(9):
        _slot(c, 7 + col * 18, 108)
    return c


def textures():
    out = {'gui/container/present.png': present_gui()}
    for t in PRESENT_COLORS:
        out[f'block/present_{t}_side.png'] = present_side(t)
        out[f'block/present_{t}_top.png'] = present_top(t)
        out[f'block/present_{t}_ribbon.png'] = present_ribbon(t)
    return out
