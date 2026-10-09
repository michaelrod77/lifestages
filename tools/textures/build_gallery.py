"""Builds art/gallery.html, a review page for every texture. Run after generate_textures.py."""
import base64
import html
import io
import json
import os

from PIL import Image

import mom_family
import skins

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'lifestages', 'textures')
OUT = os.path.join(ROOT, 'art', 'gallery.html')
REFERENCE = os.path.join(ROOT, 'art', 'reference', 'mom_reference.png')


def uri(img):
    buf = io.BytesIO()
    img.save(buf, 'PNG')
    return 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode()


def load(rel):
    return Image.open(os.path.join(TEX, rel)).convert('RGBA')


def frames_of(rel):
    img = load(rel)
    n = img.height // img.width
    return [img.crop((0, i * img.width, img.width, (i + 1) * img.width)) for i in range(n)]


def particle_frames(stem):
    out, i = [], 0
    while os.path.exists(os.path.join(TEX, f'particle/{stem}_{i}.png')):
        out.append(load(f'particle/{stem}_{i}.png'))
        i += 1
    return out


def sprite(frames, px, ms=160, alt=''):
    if len(frames) == 1:
        return f'<img class="px" src="{uri(frames[0])}" width="{px}" height="{px}" alt="{html.escape(alt)}">'
    data = html.escape(json.dumps([uri(f) for f in frames]))
    return (f'<img class="px anim" src="{uri(frames[0])}" data-frames="{data}" data-ms="{ms}" '
            f'width="{px}" height="{px}" alt="{html.escape(alt)}">')


def card(frames, name, path, note, px=128, ms=160):
    return f'''<article class="card">
  <div class="tile">{sprite(frames, px, ms, name)}</div>
  <div class="meta"><h3>{html.escape(name)}</h3><code>{html.escape(path)}</code><p>{html.escape(note)}</p></div>
</article>'''


ITEMS = [
    ('item/milk_bottle.png', 'Milk Bottle', 'Rubber teat, blue collar, measuring marks and a pink heart. Babies drink it to grow.'),
    ('item/clean_diaper.png', 'Clean Diaper', 'Blue waistband, yellow tabs, star print and leg cut-outs.'),
    ('item/dirty_diaper.png', 'Dirty Diaper', 'Yellowed, stained and sagging, with stink lines and a fly. 4-frame animation.'),
    ('item/walking_cane.png', 'Walking Cane', 'Wooden crook with a copper tip. Elderly respawns start with one.'),
    ('item/frying_pan.png', 'Frying Pan', "Mom's weapon, and her heirloom after she dies."),
    ('item/memory_orb.png', 'Memory Orb', 'Holds the XP left in a Will Chest.'),
    ('item/mom_spawn_egg.png', 'NPC Mom Spawn Egg', 'Pink with hair-brown and apron-white spots. Creative and admin only.'),
    ('item/crib.png', 'Crib', 'Inventory icon for the Crib block.'),
]

BLOCKS = [
    ('block/crib_rails.png', 'Crib rails', 'Side panels. The gaps between bars are see-through.'),
    ('block/crib_headboard.png', 'Crib headboard', 'Smooth arched end panels with an inset groove and a heart cut-out.'),
    ('block/crib_mattress.png', 'Crib mattress', 'Quilted blue with white stars.'),
    ('block/crib_wood.png', 'Crib posts', 'Smooth light wood for the four corner posts.'),
    ('block/will_chest_front.png', 'Will Chest front', 'Light oak, gold trim, lock plate and a red wax seal.'),
    ('block/will_chest_side.png', 'Will Chest side', 'Light oak planks with gold corner bands.'),
    ('block/will_chest_top.png', 'Will Chest lid', 'Gold border with a small gold heart.'),
    ('block/gravestone_front.png', 'Gravestone front', 'Carved RIP and a line. The name and age are drawn as text below it in game.'),
    ('block/gravestone_side.png', 'Gravestone stone', 'Plain stone for the sides and top.'),
    ('block/mess.png', 'Mess', 'Flat layer left by an Elderly accident with no diaper on. Comes with its own fly.'),
]

PARTICLES = [
    ('tear', 'Tear', 'Sprays out in two arcs when a baby cries.'),
    ('fart', 'Fart cloud', 'Puffs up and fades over 4 frames.'),
    ('fly', 'Fly', 'Buzzes around a dirty diaper or a Mess.'),
    ('stink', 'Stink lines', 'Rise from dirty diapers and Stinky players.'),
    ('splat', 'Splat', 'Flies out during a poop or a thrown Dirty Diaper.'),
    ('sparkle', 'Sparkle', 'Plays with hearts when a baby is cute.'),
]

def figure(img, label, scale=3):
    return (f'<figure><img class="px" src="{uri(img)}" width="{img.width * scale}" '
            f'height="{img.height * scale}" alt="{html.escape(label)}"><figcaption>{html.escape(label)}</figcaption></figure>')


def mom_cards():
    out = []
    notes = {'mom_1': 'Your reference skin: long wavy brown hair, rose crown, white and lavender dress.'}
    notes.update({k: v['note'] for k, v in mom_family.MOMS.items()})
    for i in range(1, 7):
        name = f'mom_{i}'
        young = load(f'entity/mom/{name}.png')
        old = load(f'entity/mom/{name}_elderly.png')
        figs = (figure(skins.render([young]), 'Front') + figure(skins.render([young], 'back'), 'Back')
                + figure(skins.render([old]), 'Elderly'))
        out.append(f'<article class="card wide"><div class="tile figs">{figs}</div><div class="meta"><h3>Mom {i}</h3>'
                   f'<code>entity/mom/{name}.png · {name}_elderly.png</code><p>{html.escape(notes[name])}</p></div></article>')
    return chr(10).join(out)


def layer_cards():
    sample = mom_family.build_sample(REFERENCE).image()
    L = {k: load(f'entity/player_layer/{k}.png') for k in ('newborn_swaddle_slim', 'onesie_slim', 'diaper', 'diaper_dirty', 'elderly_hair')}
    sw, top = L['newborn_swaddle_slim'], L['onesie_slim']

    def baby(layers, view, label):
        return figure(skins.render([sample] + layers, view, baby=True, ss=4), label, scale=1)

    rows = [
        ('Adult', 'No layer', 'The sample player as an adult, for comparison.', [figure(skins.render([sample]), 'Front')]),
        ('Newborn', 'newborn_swaddle.png · newborn_swaddle_slim.png', 'A plain blue swaddle with two wrap folds and a few stars, and a bonnet with a lace edge and a pink ribbon. Shown at baby proportions: a slim body, short legs and a head about as wide as the shoulders. In game a newborn lies down.',
         [baby([sw], 'front', 'Front'), baby([sw], 'back', 'Back')]),
        ('Infant and toddler', 'onesie.png · onesie_slim.png · diaper.png', 'A soft lilac onesie with a pink collar and snaps, a diaper with a lavender waistband and pink tabs, and white booties with little faces.',
         [baby([top, L['diaper']], 'front', 'Front'), baby([top, L['diaper']], 'back', 'Back')]),
        ('Dirty diaper', 'diaper_dirty.png', 'Swaps in after a poop: a yellowed diaper with a stain on the back.',
         [baby([top, L['diaper_dirty']], 'front', 'Front'), baby([top, L['diaper_dirty']], 'back', 'Back')]),
        ('Elderly', 'elderly_hair.png', 'Silver hair laid over the player’s own hair. Elderly players also wear the diaper layer.',
         [figure(skins.render([sample, L['elderly_hair']]), 'Front'), figure(skins.render([sample, L['elderly_hair']], 'back'), 'Back')]),
    ]
    out = []
    for title, path, note, figs in rows:
        out.append(f'<article class="card wide"><div class="tile figs">{"".join(figs)}</div><div class="meta"><h3>{html.escape(title)}</h3>'
                   f'<code>entity/player_layer/{html.escape(path)}</code><p>{html.escape(note)}</p></div></article>')
    return chr(10).join(out)


def hotbar():
    slots = []
    for rel, name, _ in ITEMS:
        frames = frames_of(rel)
        slots.append(f'<div class="slot" title="{html.escape(name)}">{sprite(frames, 64, 180, name)}</div>')
    slots.append('<div class="slot"></div>')
    return ''.join(slots)


def emote_wheel():
    spots = [('cry', 'Cry', 'n'), ('fart', 'Fart', 'e'), ('poop', 'Poop', 's'), ('cute', 'Be cute', 'w')]
    out = []
    for f, label, pos in spots:
        out.append(f'<div class="spoke {pos}">{sprite([load(f"gui/emote/{f}.png")], 96, alt=label)}<span>{label}</span></div>')
    return ''.join(out)


CSS = r'''
/* Layout: one reading column of sections; each section is a wrapping grid of texture cards. */
:root {
  --bg: #eceee9; --surface: #ffffff; --ink: #1d221e; --muted: #59625b; --line: #d2d7d0;
  --accent: #3d7a33; --check-a: #dfe3dc; --check-b: #f4f6f2;
  --grass-a: #6b9f46; --grass-b: #5f923c; --night-a: #1d2536; --night-b: #222b3e;
  --slot: #8b8b8b; --slot-hi: #ffffff; --slot-lo: #373737; --slot-face: #c6c6c6;
  --display: "Pixelify Sans", "Courier New", monospace;
  --body: "Public Sans", "Segoe UI", system-ui, sans-serif;
  --mono: "IBM Plex Mono", ui-monospace, Consolas, monospace;
}
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) {
  --bg: #141815; --surface: #1d221e; --ink: #e5e9e3; --muted: #9aa49b; --line: #323a33;
  --accent: #86c46f; --check-a: #252b26; --check-b: #2d342e; color-scheme: dark } }
:root[data-theme="dark"] {
  --bg: #141815; --surface: #1d221e; --ink: #e5e9e3; --muted: #9aa49b; --line: #323a33;
  --accent: #86c46f; --check-a: #252b26; --check-b: #2d342e; color-scheme: dark }
body { background: var(--bg); color: var(--ink); font: 15px/1.55 var(--body); }
.wrap { max-width: 1120px; margin: 0 auto; padding-inline: 20px; padding-block: 36px 64px; display: grid; gap: 48px; }
header { display: grid; gap: 14px; max-width: 70ch; }
.eyebrow { font: 600 12px/1 var(--body); letter-spacing: .12em; text-transform: uppercase; color: var(--accent); }
h1 { font: 700 clamp(34px, 6vw, 52px)/1.05 var(--display); margin: 0; text-wrap: balance; }
h2 { font: 700 26px/1.1 var(--display); margin: 0; }
h3 { font: 600 15px/1.3 var(--body); margin: 0; }
p { margin: 0; }
header p { color: var(--muted); }
.controls { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.controls span { font-size: 13px; color: var(--muted); margin-right: 4px; }
.controls button { font: 600 13px var(--body); color: var(--ink); background: var(--surface); border: 1px solid var(--line);
  border-radius: 999px; padding: 6px 14px; cursor: pointer; }
.controls button[aria-pressed="true"] { background: var(--accent); border-color: var(--accent); color: var(--bg); }
.controls button:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
section { display: grid; gap: 18px; }
.sechead { display: grid; gap: 4px; max-width: 70ch; }
.sechead p { color: var(--muted); }
.grid { display: grid; gap: 16px; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); }
.grid.wide { grid-template-columns: repeat(auto-fill, minmax(min(100%, 340px), 1fr)); }
.card { background: var(--surface); border: 1px solid var(--line); border-radius: 10px; overflow: hidden; display: grid; grid-template-rows: auto 1fr; min-width: 0; }
.tile { display: grid; place-items: center; padding: 22px; min-height: 172px;
  background: repeating-conic-gradient(var(--tile-a) 0 25%, var(--tile-b) 0 50%) 0 0 / 16px 16px; }
.tile.figs { display: flex; flex-wrap: wrap; justify-content: center; align-items: end; gap: 18px; }
.meta { display: grid; gap: 6px; padding: 14px 16px 16px; align-content: start; min-width: 0; }
.meta p { font-size: 14px; color: var(--muted); }
code { font: 12px/1.4 var(--mono); color: var(--muted); overflow-wrap: anywhere; }
figure { margin: 0; display: grid; gap: 6px; justify-items: center; }
figcaption { font: 600 11px var(--body); letter-spacing: .08em; text-transform: uppercase; color: var(--tile-ink); }
img.px { image-rendering: pixelated; image-rendering: crisp-edges; max-width: 100%; height: auto; }
.pending .tile { min-height: 230px; }
.wait { font: 500 18px var(--display); color: var(--tile-ink); text-align: center; }
main[data-bg="checker"] { --tile-a: var(--check-a); --tile-b: var(--check-b); --tile-ink: var(--muted); }
main[data-bg="grass"] { --tile-a: var(--grass-a); --tile-b: var(--grass-b); --tile-ink: #f1f6ea; }
main[data-bg="night"] { --tile-a: var(--night-a); --tile-b: var(--night-b); --tile-ink: #c9d3e6; }
.hotbar { display: flex; flex-wrap: wrap; gap: 0; width: fit-content; max-width: 100%; padding: 6px; background: var(--slot-face);
  border: 3px solid; border-color: var(--slot-hi) var(--slot-lo) var(--slot-lo) var(--slot-hi); }
.slot { width: 76px; height: 76px; display: grid; place-items: center; background: var(--slot);
  border: 3px solid; border-color: var(--slot-lo) var(--slot-hi) var(--slot-hi) var(--slot-lo); }
.wheelbox { display: grid; grid-template-columns: minmax(0, 360px) minmax(0, 1fr); gap: 28px; align-items: center; }
.wheel { position: relative; width: min(100%, 340px); aspect-ratio: 1; border-radius: 50%;
  background: radial-gradient(circle, transparent 0 22%, rgba(20, 24, 21, .72) 22.5% 70%, transparent 70.5%); }
.wheel::after { content: "G"; position: absolute; inset: 0; display: grid; place-items: center; font: 700 28px var(--display); color: var(--ink); }
.spoke { position: absolute; display: grid; justify-items: center; gap: 2px; transform: translate(-50%, -50%); }
.spoke img { width: 25%; min-width: 64px; }
.spoke span { font: 600 12px var(--body); color: #f2f4f1; }
.spoke.n { left: 50%; top: 23%; } .spoke.s { left: 50%; top: 77%; } .spoke.e { left: 77%; top: 50%; } .spoke.w { left: 23%; top: 50%; }
.wheelbox ul { margin: 0; padding-left: 18px; display: grid; gap: 8px; color: var(--muted); max-width: 60ch; }
@media (max-width: 640px) { .wheelbox { grid-template-columns: 1fr; } .slot { width: 60px; height: 60px; } .slot img { width: 48px; } }
'''

JS = r'''
const main = document.querySelector('main');
document.querySelectorAll('[data-setbg]').forEach(b => b.addEventListener('click', () => {
  main.dataset.bg = b.dataset.setbg;
  document.querySelectorAll('[data-setbg]').forEach(o => o.setAttribute('aria-pressed', String(o === b)));
}));
if (!matchMedia('(prefers-reduced-motion: reduce)').matches) {
  document.querySelectorAll('img.anim').forEach(img => {
    const frames = JSON.parse(img.dataset.frames); let i = 0;
    setInterval(() => { i = (i + 1) % frames.length; img.src = frames[i]; }, Number(img.dataset.ms) || 160);
  });
}
'''


def build():
    items = '\n'.join(card(frames_of(rel), name, rel, note, 128, 180) for rel, name, note in ITEMS)
    blocks = '\n'.join(card([load(rel)], name, rel, note) for rel, name, note in BLOCKS)
    parts = '\n'.join(card(particle_frames(stem) or [load(f'particle/{stem}.png')], name,
                           f'particle/{stem}' + ('_0…' if particle_frames(stem) else '.png'), note, 96, 200)
                      for stem, name, note in PARTICLES)
    page = f'''<title>Life Stages Textures</title>
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Pixelify+Sans:wght@500;700&family=Public+Sans:wght@400;600&family=IBM+Plex+Mono&display=swap">
<style>{CSS}</style>
<main class="wrap" data-bg="checker">
<header>
  <span class="eyebrow">Life Stages mod · texture review</span>
  <h1>Life Stages Textures</h1>
  <p>Every texture is drawn at 32×32 (HD) with modern hue-shifted shading: shadows lean cool, highlights lean warm, and outlines are a darker shade of the object’s own color. Characters use the standard skin layout at 128×128. Comment on any texture you want changed. A script builds all of them, so changes are quick.</p>
  <div class="controls" role="group" aria-label="Preview background"><span>Background</span>
    <button type="button" data-setbg="checker" aria-pressed="true">Checker</button>
    <button type="button" data-setbg="grass" aria-pressed="false">Grass</button>
    <button type="button" data-setbg="night" aria-pressed="false">Night</button>
  </div>
</header>

<section id="hotbar"><div class="sechead"><h2>In the hotbar</h2><p>All new items in slots, at the size players see them with GUI scale 2.</p></div>
  <div class="hotbar">{hotbar()}</div></section>

<section id="items"><div class="sechead"><h2>Items</h2><p>Shown at 4x.</p></div><div class="grid">{items}</div></section>

<section id="moms"><div class="sechead"><h2>NPC Moms</h2><p>All six Moms come from your Mom 1 skin, so they share its face, soft painterly shading and slim arms. Moms 2, 3 and 5 have their own outfits and hairstyles. Each has an Elderly version with silver hair, faded eyes and smile lines for when she ages. Shown as flat front and back views.</p></div>
  <div class="grid wide">{mom_cards()}</div></section>

<section id="layers"><div class="sechead"><h2>Player layers</h2><p>These layers sit on top of each player’s own skin, so everyone still looks like themselves. Shown on a sample player.</p></div>
  <div class="grid wide">{layer_cards()}</div></section>

<section id="blocks"><div class="sechead"><h2>Blocks</h2><p>Face textures for the Crib, Will Chest, Gravestone and Mess.</p></div><div class="grid">{blocks}</div></section>

<section id="emotes"><div class="sechead"><h2>Emote wheel</h2><p>Hold G and release over an emote.</p></div>
  <div class="wheelbox"><div class="wheel">{emote_wheel()}</div>
  <ul><li><strong>Cry</strong>: eyes squeezed shut, mouth wide open, tears streaming.</li>
  <li><strong>Fart</strong>: a diaper and a green cloud.</li>
  <li><strong>Poop</strong>: a dirty diaper with stink lines and a fly.</li>
  <li><strong>Be cute</strong>: sparkly eyes, rosy cheeks and hearts.</li></ul></div></section>

<section id="particles"><div class="sechead"><h2>Particles</h2><p>16×16 sprites. Animated ones play their frames here.</p></div><div class="grid">{parts}</div></section>
</main>
<script>{JS}</script>
'''
    with open(OUT, 'w', encoding='utf-8') as f:
        f.write(page)
    print(f'wrote {OUT} ({os.path.getsize(OUT) // 1024} KB)')


if __name__ == '__main__':
    build()
