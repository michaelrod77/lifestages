"""Small pixel-art toolkit used by generate_textures.py."""
import colorsys
import math

from PIL import Image


def hx(s, a=255):
    s = s.lstrip('#')
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(int(round(c1[i] * (1 - t) + c2[i] * t)) for i in range(4))


COOL, WARM = 0.70, 0.13  # shadows lean blue-violet, highlights lean warm yellow


def _toward(h, target, amount):
    d = (target - h) % 1.0
    if d > 0.5:
        d -= 1.0
    return (h + d * amount) % 1.0


def _hsv(c):
    return colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)


def _rgb(h, s, v, a):
    r, g, b = colorsys.hsv_to_rgb(h, max(0.0, min(1.0, s)), max(0.0, min(1.0, v)))
    return (round(r * 255), round(g * 255), round(b * 255), a)


def lighten(c, t):
    """Lighter and warmer, with less saturation (modern hue-shifted shading)."""
    h, s, v = _hsv(c)
    return _rgb(_toward(h, WARM, 0.18 * t), s * (1 - 0.55 * t), v + (1 - v) * t, c[3])


def darken(c, t):
    """Darker and cooler, with more saturation (modern hue-shifted shading)."""
    h, s, v = _hsv(c)
    grey = s < 0.06
    if grey:
        h = COOL
    ns = s + (0.12 if grey else 0.3) * t
    return _rgb(_toward(h, COOL, 0.22 * t), ns, v * (1 - t), c[3])


def alpha(c, a):
    return (c[0], c[1], c[2], a)


def ramp(base):
    """Five hue-shifted tones, darkest first."""
    return [darken(base, 0.4), darken(base, 0.2), base, lighten(base, 0.28), lighten(base, 0.55)]


def hnoise(x, y, seed=0):
    """Deterministic 0..1 noise for a pixel."""
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def shade_idx(nx, ny):
    """Ramp index for a point on a lit dome, light from the top left."""
    r2 = nx * nx + ny * ny
    v = (-0.55 * nx - 0.75 * ny) - max(0.0, r2 - 0.55) * 0.9
    if v > 0.55:
        return 4
    if v > 0.18:
        return 3
    if v > -0.3:
        return 2
    if v > -0.7:
        return 1
    return 0


def sphere(base, cx, cy, rx, ry, tones=None):
    rp = tones or ramp(base)
    return lambda x, y: rp[shade_idx((x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry)]


def tube(tones, r):
    """Shading for a stroked line: lighter on the top-left edge."""
    def f(x, y, ox, oy, d):
        v = (-ox - oy) / (r * 1.414)
        if v > 0.3:
            return tones[3]
        if v < -0.3:
            return tones[1]
        return tones[2]
    return f


def seg(p0, p1, n=40):
    return [(p0[0] + (p1[0] - p0[0]) * i / n, p0[1] + (p1[1] - p0[1]) * i / n) for i in range(n + 1)]


def arc(cx, cy, r, a0, a1, n=40):
    return [(cx + r * math.cos(a0 + (a1 - a0) * i / n), cy - r * math.sin(a0 + (a1 - a0) * i / n)) for i in range(n + 1)]


HEART = [".X.X.", "XXXXX", "XXXXX", ".XXX.", "..X.."]


class Canvas:
    def __init__(self, w, h=None):
        self.w, self.h = w, h or w
        self.p = [[None] * self.w for _ in range(self.h)]

    def copy(self):
        c = Canvas(self.w, self.h)
        c.p = [row[:] for row in self.p]
        return c

    def set(self, x, y, c):
        x, y = int(x), int(y)
        if c is not None and 0 <= x < self.w and 0 <= y < self.h:
            self.p[y][x] = c

    def setif(self, x, y, c):
        """Set only on an empty pixel."""
        if self.get(x, y) is None:
            self.set(x, y, c)

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.p[y][x]
        return None

    def clear(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.p[y][x] = None

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, c(x, y) if callable(c) else c)

    def fill(self, inside, color):
        for y in range(self.h):
            for x in range(self.w):
                if inside(x + 0.5, y + 0.5):
                    self.set(x, y, color(x, y) if callable(color) else color)

    def ellipse(self, cx, cy, rx, ry, color):
        self.fill(lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1, color)

    def stroke(self, pts, r, color):
        for y in range(self.h):
            for x in range(self.w):
                px, py = x + 0.5, y + 0.5
                best = min(pts, key=lambda q: (px - q[0]) ** 2 + (py - q[1]) ** 2)
                d = math.hypot(px - best[0], py - best[1])
                if d <= r:
                    self.set(x, y, color(x, y, px - best[0], py - best[1], d) if callable(color) else color)

    def pattern(self, x0, y0, rows, color, light=None):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == 'X':
                    self.set(x0 + i, y0 + j, color)
        if light:
            self.set(x0 + 1, y0 + 1, light)

    def outline(self, amount=0.55, color=None):
        adds = []
        for y in range(self.h):
            for x in range(self.w):
                if self.p[y][x] is not None:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    n = self.get(x + dx, y + dy)
                    if n is not None:
                        adds.append((x, y, color or darken((n[0], n[1], n[2], 255), amount)))
                        break
        for x, y, c in adds:
            self.p[y][x] = c

    def paste(self, other, ox, oy):
        for y in range(other.h):
            for x in range(other.w):
                c = other.p[y][x]
                if c is not None:
                    self.set(ox + x, oy + y, c)

    def image(self):
        img = Image.new('RGBA', (self.w, self.h), (0, 0, 0, 0))
        img.putdata([c or (0, 0, 0, 0) for row in self.p for c in row])
        return img

    @staticmethod
    def from_image(img):
        img = img.convert('RGBA')
        c = Canvas(img.width, img.height)
        for y in range(img.height):
            for x in range(img.width):
                px = img.getpixel((x, y))
                if px[3] > 0:
                    c.p[y][x] = px
        return c


def stack(frames):
    """Stack equal-size frames vertically, as Minecraft animations expect."""
    out = Canvas(frames[0].w, frames[0].h * len(frames))
    for i, f in enumerate(frames):
        out.paste(f, 0, i * f.h)
    return out
