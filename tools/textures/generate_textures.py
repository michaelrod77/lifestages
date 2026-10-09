"""Builds every Life Stages texture into the mod's assets folder.

Run:  python tools/textures/generate_textures.py
All Moms are built from art/reference/mom_reference.png (the team's Mom 1 skin).
"""
import json
import os

from PIL import Image, ImageDraw

import items
import mom_family
import skins
from pixel import stack

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'lifestages', 'textures')
PREVIEW = os.path.join(ROOT, 'art', 'preview')
REFERENCE = os.path.join(ROOT, 'art', 'reference', 'mom_reference.png')


def save(img, rel):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    return path


def save_preview(img, name, scale):
    os.makedirs(PREVIEW, exist_ok=True)
    big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
    big.save(os.path.join(PREVIEW, name))


def main():
    written = []
    for rel, tex in items.all_textures().items():
        if isinstance(tex, list):
            if rel in items.ANIMATED:
                written.append(save(stack(tex).image(), rel))
                with open(os.path.join(TEX, rel + '.mcmeta'), 'w') as f:
                    json.dump({'animation': {'frametime': items.ANIMATED[rel]}}, f, indent=2)
            else:
                stem = rel[:-4]
                for i, frame in enumerate(tex):
                    written.append(save(frame.image(), f'{stem}_{i}.png'))
        else:
            written.append(save(tex.image(), rel))

    family = mom_family.build_family(REFERENCE)
    for name, (young, old, note) in family.items():
        written.append(save(young.image(), f'entity/mom/{name}.png'))
        written.append(save(old.image(), f'entity/mom/{name}_elderly.png'))

    layers = skins.overlays()
    slim_layers = skins.overlays(slim=True)
    layers['newborn_swaddle_slim'] = slim_layers['newborn_swaddle']
    layers['onesie_slim'] = slim_layers['onesie']
    for name, canvas in layers.items():
        written.append(save(canvas.image(), f'entity/player_layer/{name}.png'))

    sample = mom_family.build_sample(REFERENCE).image()
    os.makedirs(PREVIEW, exist_ok=True)
    sample.save(os.path.join(PREVIEW, 'sample_player_skin.png'))
    L = {k: v.image() for k, v in layers.items()}
    figures = {
        'player_adult': skins.render([sample]),
        'player_newborn': skins.render([sample, L['newborn_swaddle_slim']], baby=True, ss=4),
        'player_infant': skins.render([sample, L['onesie_slim'], L['diaper']], baby=True, ss=4),
        'player_infant_dirty_back': skins.render([sample, L['onesie_slim'], L['diaper_dirty']], 'back', baby=True, ss=4),
        'player_elderly': skins.render([sample, L['elderly_hair']]),
    }
    for name, (young, old, note) in family.items():
        figures[f'{name}_front'] = skins.render([young.image()])
        figures[f'{name}_back'] = skins.render([young.image()], 'back')
        figures[f'{name}_elderly'] = skins.render([old.image()])
    for name, fig in figures.items():
        save_preview(fig, f'{name}.png', 6)

    contact_sheet(written, figures)
    print(f'{len(written)} textures written to {TEX}')


def contact_sheet(paths, figures):
    tiles = []
    for p in paths:
        if '/entity/' in p.replace('\\', '/') or '/gui/container/' in p.replace('\\', '/'):
            continue
        img = Image.open(p).convert('RGBA')
        tiles.append((os.path.basename(p), img.crop((0, 0, img.width, min(img.height, img.width)))))
    cell = 136
    cols = 8
    rows = (len(tiles) + cols - 1) // cols
    fig_list = list(figures.items())
    fig_h = 64 * 4 + 30
    fig_rows = (len(fig_list) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * cell, rows * (cell + 16) + fig_rows * fig_h), (60, 60, 64, 255))
    d = ImageDraw.Draw(sheet)
    for i, (name, img) in enumerate(tiles):
        x, y = (i % cols) * cell, (i // cols) * (cell + 16)
        scale = 128 // img.width
        big = img.resize((img.width * scale, img.height * scale), Image.NEAREST)
        sheet.alpha_composite(big, (x + 4, y + 4))
        d.text((x + 4, y + cell - 2), name[:20], fill=(230, 230, 230, 255))
    oy = rows * (cell + 16)
    for i, (name, fig) in enumerate(fig_list):
        x, y = (i % cols) * cell, oy + (i // cols) * fig_h
        big = fig.resize((fig.width * 4, fig.height * 4), Image.NEAREST)
        sheet.alpha_composite(big, (x + 4, y + 4))
        d.text((x + 4, y + fig_h - 22), name[:20], fill=(230, 230, 230, 255))
    sheet.save(os.path.join(PREVIEW, 'contact_sheet.png'))


if __name__ == '__main__':
    main()
