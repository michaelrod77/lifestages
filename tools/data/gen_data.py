"""Writes the mod's JSON data and asset definitions (advancements, recipes, tags, models, blockstates, particles).

Run:  python tools/data/gen_data.py
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
NS = 'lifestages'


def write(rel, obj):
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, indent=2)


# ---------------- advancements ----------------

def adv(name, title, desc, icon, parent='root', frame='task', hidden=False, toast=True, chat=True, background=None):
    display = {
        'icon': {'id': icon},
        'title': {'text': title},
        'description': {'text': desc},
        'frame': frame,
        'show_toast': toast,
        'announce_to_chat': chat,
        'hidden': hidden,
    }
    if background:
        display['background'] = background
    obj = {'display': display, 'criteria': {'done': {'trigger': 'minecraft:impossible'}}, 'requirements': [['done']]}
    if parent:
        obj['parent'] = f'{NS}:{parent}'
    write(f'data/{NS}/advancement/{name}.json', obj)


adv('root', 'Life Stages', 'Begin a life.', f'{NS}:milk_bottle', parent=None, toast=False, chat=False,
    background='minecraft:gui/advancements/backgrounds/stone')
chain = [
    ('hello_world', 'Hello World', 'Grow from Newborn to Infant.', f'{NS}:milk_bottle'),
    ('first_steps', 'First Steps', 'Grow into a Toddler.', f'{NS}:clean_diaper'),
    ('big_kid_pants', 'Big Kid Pants', 'Grow into a Preschooler. No more diapers!', 'minecraft:leather_leggings'),
    ('first_day_of_school', 'First Day of School', 'Grow into a Child.', 'minecraft:book'),
    ('teenage_angst', 'Teenage Angst', 'Grow into a Teenager.', 'minecraft:music_disc_11'),
    ('graduation', 'Graduation', 'Grow into a Young Adult.', 'minecraft:written_book'),
    ('bills_to_pay', 'Bills to Pay', 'Grow into an Adult.', 'minecraft:emerald'),
    ('midlife_crisis', 'Midlife Crisis', 'Reach Middle Age.', 'minecraft:saddle'),
    ('senior_discount', 'Senior Discount', 'Become a Senior Citizen.', 'minecraft:gold_nugget'),
    ('golden_years', 'Golden Years', 'Become Elderly.', f'{NS}:walking_cane'),
    ('rest_in_peace', 'Rest in Peace', 'Live a whole life and pass away peacefully of old age.', f'{NS}:gravestone'),
]
parent = 'root'
for i, (name, title, desc, icon) in enumerate(chain):
    frame = 'challenge' if name == 'rest_in_peace' else 'goal' if name in ('first_day_of_school', 'graduation', 'golden_years') else 'task'
    adv(name, title, desc, icon, parent=parent, frame=frame)
    parent = name
extras = [
    ('milk_drunk', 'Milk Drunk', 'Drink 3 milk bottles in a row.', f'{NS}:milk_bottle'),
    ('blowout', 'Blowout', 'Poop while someone is carrying you.', f'{NS}:dirty_diaper'),
    ('silent_but_deadly', 'Silent but Deadly', 'Fart for the first time.', 'minecraft:green_dye'),
    ('fart_flame', 'Flame On', 'Fart near a fire.', 'minecraft:blaze_powder'),
    ('turbo_fart', 'Turbo Fart', 'Launch yourself into the air with a fart.', 'minecraft:firework_rocket'),
    ('it_takes_a_village', 'It Takes a Village', 'Be looked after by an NPC Mom.', f'{NS}:mom_spawn_egg'),
    ('proud_parent', 'Proud Parent', 'Become a parent.', 'minecraft:cake'),
    ('welcome_home', 'Welcome Home', 'Get a parent.', 'minecraft:red_bed'),
    ('generations', 'Generations', 'Your child becomes a parent.', 'minecraft:totem_of_undying'),
    ('gotcha', 'Gotcha!', 'Catch a running child and scoop them up.', 'minecraft:lead'),
    ('sugar_rush', 'Sugar Rush!', 'Eat something sweet as a child.', 'minecraft:cookie'),
    ('boo', 'Boo!', 'Scare someone as a ghost.', 'minecraft:phantom_membrane'),
    ('thanks_mom', 'Thanks, Mom', 'Say goodbye to your NPC Mom.', f'{NS}:frying_pan'),
]
for name, title, desc, icon in extras:
    adv(name, title, desc, icon, parent='root', hidden=name in ('fart_flame', 'turbo_fart', 'blowout'))

# ---------------- damage type ----------------
write(f'data/{NS}/damage_type/old_age.json', {'exhaustion': 0.0, 'message_id': f'{NS}.old_age', 'scaling': 'never'})
for tag in ('bypasses_armor', 'bypasses_invulnerability', 'bypasses_resistance', 'bypasses_effects', 'bypasses_enchantments',
            'bypasses_cooldown', 'no_knockback', 'no_impact'):
    write(f'data/minecraft/tags/damage_type/{tag}.json', {'replace': False, 'values': [f'{NS}:old_age']})

# ---------------- item tags ----------------
write(f'data/{NS}/tags/item/soft_foods.json', {'values': [
    'minecraft:bread', 'minecraft:apple', 'minecraft:carrot', 'minecraft:baked_potato', 'minecraft:sweet_berries',
    'minecraft:melon_slice', 'minecraft:cookie', 'minecraft:pumpkin_pie', f'{NS}:milk_bottle', 'minecraft:honey_bottle']})
write(f'data/{NS}/tags/item/sugary.json', {'values': [
    'minecraft:cookie', 'minecraft:cake', 'minecraft:pumpkin_pie', 'minecraft:honey_bottle', 'minecraft:sugar']})

# ---------------- recipes ----------------
for n in (1, 2, 3):
    write(f'data/{NS}/recipe/milk_bottle{"" if n == 1 else "_" + str(n)}.json', {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': ['minecraft:milk_bucket'] + ['minecraft:glass_bottle'] * n,
        'result': {'id': f'{NS}:milk_bottle', 'count': n}})
write(f'data/{NS}/recipe/clean_diaper.json', {
    'type': 'minecraft:crafting_shapeless', 'category': 'misc',
    'ingredients': ['minecraft:paper', 'minecraft:paper', 'minecraft:white_wool'],
    'result': {'id': f'{NS}:clean_diaper', 'count': 2}})
write(f'data/{NS}/recipe/crib.json', {
    'type': 'minecraft:crafting_shaped', 'category': 'misc',
    'pattern': ['PPP', 'SSS', 'PPP'], 'key': {'P': '#minecraft:planks', 'S': 'minecraft:stick'},
    'result': {'id': f'{NS}:crib'}})
write(f'data/{NS}/recipe/walking_cane.json', {
    'type': 'minecraft:crafting_shaped', 'category': 'equipment',
    'pattern': ['  S', ' S ', 'C  '], 'key': {'S': 'minecraft:stick', 'C': 'minecraft:copper_ingot'},
    'result': {'id': f'{NS}:walking_cane'}})

# ---------------- item models ----------------
def item(name, parent='minecraft:item/generated', texture=None):
    write(f'assets/{NS}/items/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{name}'}})
    write(f'assets/{NS}/models/item/{name}.json', {'parent': parent, 'textures': {'layer0': texture or f'{NS}:item/{name}'}})


for name in ('milk_bottle', 'clean_diaper', 'dirty_diaper', 'memory_orb', 'mom_spawn_egg'):
    item(name)
item('walking_cane', 'minecraft:item/handheld')
item('frying_pan', 'minecraft:item/handheld')
item('crib', texture=f'{NS}:item/crib')


def block_item(name):
    write(f'assets/{NS}/items/{name}.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:block/{name}'}})


block_item('will_chest')
block_item('gravestone')

# ---------------- blockstates and block models ----------------
def facing_states(name):
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    write(f'assets/{NS}/blockstates/{name}.json', {'variants': {
        f'facing={f}': ({'model': f'{NS}:block/{name}', 'y': y} if y else {'model': f'{NS}:block/{name}'}) for f, y in rot.items()}})


def faces(tex, uv=None, which=('north', 'south', 'east', 'west', 'up', 'down')):
    out = {}
    for f in which:
        face = {'texture': tex}
        if uv:
            face['uv'] = uv
        out[f] = face
    return out


facing_states('crib')
crib_elements = []
for x0, z0 in ((0, 0), (14, 0), (0, 14), (14, 14)):
    crib_elements.append({'from': [x0, 0, z0], 'to': [x0 + 2, 15, z0 + 2], 'faces': faces('#wood')})
crib_elements += [
    {'from': [1, 1, 1], 'to': [15, 2, 15], 'faces': faces('#wood')},
    {'from': [1, 2, 1], 'to': [15, 4, 15], 'faces': faces('#mattress')},
    {'from': [2, 1, 0.5], 'to': [14, 15, 1.5], 'faces': faces('#head', [0, 0, 16, 16], ('north', 'south'))},
    {'from': [2, 1, 14.5], 'to': [14, 15, 15.5], 'faces': faces('#head', [0, 0, 16, 16], ('north', 'south'))},
    {'from': [0.5, 3, 2], 'to': [1.5, 14, 14], 'faces': faces('#rails', [0, 0, 16, 16], ('east', 'west'))},
    {'from': [14.5, 3, 2], 'to': [15.5, 14, 14], 'faces': faces('#rails', [0, 0, 16, 16], ('east', 'west'))},
]
write(f'assets/{NS}/models/block/crib.json', {
    'textures': {'wood': f'{NS}:block/crib_wood', 'mattress': f'{NS}:block/crib_mattress', 'head': f'{NS}:block/crib_headboard',
                 'rails': f'{NS}:block/crib_rails', 'particle': f'{NS}:block/crib_wood'},
    'elements': crib_elements})

facing_states('will_chest')
write(f'assets/{NS}/models/block/will_chest.json', {'parent': 'minecraft:block/orientable', 'textures': {
    'top': f'{NS}:block/will_chest_top', 'front': f'{NS}:block/will_chest_front', 'side': f'{NS}:block/will_chest_side'}})

facing_states('gravestone')
write(f'assets/{NS}/models/block/gravestone.json', {
    'textures': {'front': f'{NS}:block/gravestone_front', 'stone': f'{NS}:block/gravestone_side', 'particle': f'{NS}:block/gravestone_side'},
    'elements': [
        {'from': [2, 0, 6], 'to': [14, 15, 10], 'faces': {
            'north': {'texture': '#front', 'uv': [2, 1, 14, 16]}, 'south': {'texture': '#front', 'uv': [2, 1, 14, 16]},
            'east': {'texture': '#stone', 'uv': [6, 1, 10, 16]}, 'west': {'texture': '#stone', 'uv': [6, 1, 10, 16]},
            'up': {'texture': '#stone', 'uv': [2, 6, 14, 10]}, 'down': {'texture': '#stone', 'uv': [2, 6, 14, 10]}}}]})

write(f'assets/{NS}/blockstates/mess.json', {'variants': {'': {'model': f'{NS}:block/mess'}}})
write(f'assets/{NS}/models/block/mess.json', {
    'textures': {'mess': f'{NS}:block/mess', 'particle': f'{NS}:block/mess'},
    'elements': [{'from': [0, 0, 0], 'to': [16, 0.5, 16], 'faces': {
        'up': {'texture': '#mess'}, 'down': {'texture': '#mess'}}}]})

# ---------------- particles ----------------
for name, frames in (('tear', 1), ('fart', 4), ('fly', 2), ('stink', 2), ('splat', 1), ('sparkle', 1)):
    tex = [f'{NS}:{name}'] if frames == 1 else [f'{NS}:{name}_{i}' for i in range(frames)]
    write(f'assets/{NS}/particles/{name}.json', {'textures': tex})

# ---------- birthday presents ----------
for tier in ('common', 'uncommon', 'rare', 'epic', 'legendary'):
    item(f'present_{tier}')
item('teddy_bear', texture=f'{NS}:item/teddy_bear')


def entry(name, weight=1, count=None, enchant=False):
    """A loot entry in the 26.3 format: at most one "modifier" per entry."""
    e = {'type': 'minecraft:item', 'name': name, 'weight': weight}
    assert not (count and enchant)
    if count:
        e['modifier'] = {'type': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': count[0], 'max': count[1]}}
    if enchant:
        e['modifier'] = {'type': 'minecraft:enchant_randomly', 'options': '#minecraft:on_random_loot'}
    return e


PRESENTS = {
    'common': (2, [entry('minecraft:cookie', 5, (3, 6)), entry('minecraft:cake', 3), entry('minecraft:sweet_berries', 3, (4, 8)),
                   entry('minecraft:pink_wool', 2, (2, 4)), entry('minecraft:light_blue_wool', 2, (2, 4)), entry('minecraft:poppy', 2, (1, 3)),
                   entry('minecraft:cornflower', 2, (1, 3)), entry('minecraft:feather', 2, (2, 5)), entry('minecraft:slime_ball', 1, (1, 3)),
                   entry('minecraft:bell', 1), entry(f'{NS}:milk_bottle', 3, (2, 4)), entry(f'{NS}:rattle', 3)]),
    'uncommon': (2, [entry('minecraft:iron_ingot', 4, (2, 5)), entry('minecraft:book', 3, (2, 4)), entry('minecraft:bow', 2),
                     entry('minecraft:fishing_rod', 2), entry('minecraft:name_tag', 2), entry('minecraft:lead', 2, (1, 2)),
                     entry('minecraft:golden_carrot', 3, (2, 4)), entry('minecraft:saddle', 1), entry('minecraft:spyglass', 1),
                     entry('minecraft:cake', 2)]),
    'rare': (3, [entry('minecraft:diamond', 3, (1, 3)), entry('minecraft:enchanted_book', 3, enchant=True), entry('minecraft:golden_apple', 2),
                 entry('minecraft:emerald', 3, (3, 8)), entry('minecraft:music_disc_cat', 1), entry('minecraft:music_disc_pigstep', 1),
                 entry('minecraft:iron_sword', 2, enchant=True), entry('minecraft:experience_bottle', 2, (4, 8)), entry('minecraft:cake', 1)]),
    'epic': (3, [entry('minecraft:diamond', 4, (3, 6)), entry('minecraft:netherite_scrap', 2, (1, 2)), entry('minecraft:totem_of_undying', 2),
                 entry('minecraft:diamond_pickaxe', 2, enchant=True), entry('minecraft:heart_of_the_sea', 1), entry('minecraft:golden_apple', 3, (1, 3)),
                 entry('minecraft:experience_bottle', 2, (8, 16)), entry('minecraft:enchanted_book', 2, enchant=True)]),
    'legendary': (3, [entry('minecraft:enchanted_golden_apple', 3), entry('minecraft:netherite_ingot', 3), entry('minecraft:elytra', 1),
                      entry('minecraft:nether_star', 1), entry('minecraft:totem_of_undying', 2), entry('minecraft:diamond', 3, (5, 10)),
                      entry('minecraft:diamond_sword', 2, enchant=True), entry('minecraft:experience_bottle', 2, (16, 32))]),
}
# Presents are containers now: the loot rolls into their four slots when first opened (chest context).
for tier, (rolls, entries) in PRESENTS.items():
    write(f'data/{NS}/loot_table/present/{tier}.json', {'type': 'minecraft:chest', 'pools': [{'rolls': rolls, 'entries': entries}]})
write(f'data/{NS}/loot_table/present/teddy.json', {'type': 'minecraft:chest', 'pools': [{'rolls': 1, 'entries': [entry(f'{NS}:teddy_bear')]}]})

# ---------- placed presents ----------
for tier in PRESENTS:
    name = f'present_{tier}'
    write(f'assets/{NS}/blockstates/{name}.json', {'variants': {'': {'model': f'{NS}:block/{name}'}}})
    write(f'assets/{NS}/models/block/{name}.json', {
        'textures': {'side': f'{NS}:block/{name}_side', 'top': f'{NS}:block/{name}_top', 'ribbon': f'{NS}:block/{name}_ribbon',
                     'particle': f'{NS}:block/{name}_side'},
        'elements': [
            {'from': [4, 0, 4], 'to': [12, 6.5, 12], 'faces': {
                **{d: {'texture': '#side', 'uv': [4, 3, 12, 9.5]} for d in ('north', 'south', 'east', 'west')},
                'down': {'texture': '#top'}}},
            {'from': [3.5, 6, 3.5], 'to': [12.5, 8, 12.5], 'faces': {
                **{d: {'texture': '#side', 'uv': [3.5, 0, 12.5, 2]} for d in ('north', 'south', 'east', 'west')},
                'up': {'texture': '#top'}, 'down': {'texture': '#top'}}},
            {'from': [5.25, 8, 7.25], 'to': [7.75, 10, 8.75], 'rotation': {'origin': [7.75, 8, 8], 'axis': 'z', 'angle': 22.5},
             'faces': faces('#ribbon')},
            {'from': [8.25, 8, 7.25], 'to': [10.75, 10, 8.75], 'rotation': {'origin': [8.25, 8, 8], 'axis': 'z', 'angle': -22.5},
             'faces': faces('#ribbon')},
            {'from': [7.25, 8, 7.25], 'to': [8.75, 9.25, 8.75], 'faces': faces('#ribbon')},
        ]})
    # Broken presents keep what's inside (or stay wrapped), like a shulker box.
    write(f'data/{NS}/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}', 'modifier': {
            'type': 'minecraft:copy_components', 'source': 'block_entity',
            'include': ['minecraft:custom_name', 'minecraft:container', 'minecraft:container_loot']}}]}]})

# ---------- teddy bear block ----------
facing_states('teddy_bear')
FULL = [0, 0, 16, 16]


def box(a, b, tex, front=None, front_uv=FULL):
    f = {d: {'texture': tex} for d in ('north', 'south', 'east', 'west', 'up', 'down')}
    if front:
        f['north'] = {'texture': front, 'uv': front_uv}
    return {'from': a, 'to': b, 'faces': f}


write(f'assets/{NS}/models/block/teddy_bear.json', {
    'textures': {'fur': f'{NS}:block/teddy_fur', 'light': f'{NS}:block/teddy_light', 'face': f'{NS}:block/teddy_face',
                 'muzzle': f'{NS}:block/teddy_muzzle', 'belly': f'{NS}:block/teddy_belly', 'ear': f'{NS}:block/teddy_ear',
                 'pad': f'{NS}:block/teddy_pad', 'ribbon': f'{NS}:block/teddy_ribbon', 'particle': f'{NS}:block/teddy_fur'},
    'elements': [
        box([5, 0, 3.5], [7.5, 2.5, 8], '#fur', '#pad'), box([8.5, 0, 3.5], [11, 2.5, 8], '#fur', '#pad'),
        box([5, 2, 5.5], [11, 8.5, 10.5], '#fur', '#belly'),
        box([3.5, 3.5, 6.5], [5, 8, 9], '#fur'), box([11, 3.5, 6.5], [12.5, 8, 9], '#fur'),
        box([4.5, 8.5, 5], [11.5, 14.5, 10.5], '#fur', '#face'),
        box([6.5, 9, 4], [9.5, 11.5, 5], '#light', '#muzzle'),
        box([4, 13.5, 7], [6, 15.5, 8.5], '#fur', '#ear'), box([10, 13.5, 7], [12, 15.5, 8.5], '#fur', '#ear'),
        box([5.5, 7.5, 4.75], [7.75, 9, 5.5], '#ribbon'), box([8.25, 7.5, 4.75], [10.5, 9, 5.5], '#ribbon'),
        box([7.5, 7.75, 4.5], [8.5, 8.75, 5.5], '#ribbon'),
    ]})

# ---------- block drops ----------
for name in ('crib', 'gravestone', 'teddy_bear'):
    write(f'data/{NS}/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}],
        'condition': {'type': 'minecraft:survives_explosion'}}]})

# ---------- nursery: changing table, music box, rattle ----------
item('rattle')
block_item('changing_table')
block_item('music_box')

facing_states('changing_table')
write(f'assets/{NS}/models/block/changing_table.json', {
    'textures': {'front': f'{NS}:block/changing_table_front', 'side': f'{NS}:block/changing_table_side',
                 'top': f'{NS}:block/changing_table_top', 'pad': f'{NS}:block/changing_table_pad', 'particle': f'{NS}:block/changing_table_side'},
    'elements': [
        {'from': [0, 0, 0], 'to': [16, 11, 16], 'faces': {
            'north': {'texture': '#front', 'uv': [0, 0, 16, 11]}, 'south': {'texture': '#side', 'uv': [0, 5, 16, 16]},
            'east': {'texture': '#side', 'uv': [0, 5, 16, 16]}, 'west': {'texture': '#side', 'uv': [0, 5, 16, 16]},
            'up': {'texture': '#top'}, 'down': {'texture': '#top'}}},
        {'from': [1, 11, 1], 'to': [15, 12.5, 15], 'faces': faces('#pad')},
        {'from': [0, 11, 15], 'to': [16, 14, 16], 'faces': faces('#top')},
        {'from': [0, 11, 0], 'to': [1, 14, 15], 'faces': faces('#top')},
        {'from': [15, 11, 0], 'to': [16, 14, 15], 'faces': faces('#top')},
    ]})


def music_box(open_lid):
    els = [
        {'from': [3, 0, 4], 'to': [13, 6, 12], 'faces': {
            'north': {'texture': '#front', 'uv': [3, 8, 13, 14]}, 'south': {'texture': '#side', 'uv': [3, 8, 13, 14]},
            'east': {'texture': '#side', 'uv': [4, 8, 12, 14]}, 'west': {'texture': '#side', 'uv': [4, 8, 12, 14]},
            'up': {'texture': '#inside'}, 'down': {'texture': '#side'}}},
        {'from': [13, 2.5, 7.5], 'to': [14.5, 3.5, 8.5], 'faces': faces('#gold')},
        {'from': [14.5, 2.5, 7.5], 'to': [15.5, 5.5, 8.5], 'faces': faces('#gold')},
    ]
    lid = {'from': [3, 6, 4], 'to': [13, 7.5, 12], 'faces': {
        'north': {'texture': '#side', 'uv': [3, 0, 13, 2]}, 'south': {'texture': '#side', 'uv': [3, 0, 13, 2]},
        'east': {'texture': '#side', 'uv': [4, 0, 12, 2]}, 'west': {'texture': '#side', 'uv': [4, 0, 12, 2]},
        'up': {'texture': '#top'}, 'down': {'texture': '#inside'}}}
    if open_lid:
        # the lid stands open against the back
        lid = {'from': [3, 6, 12], 'to': [13, 14, 13.5], 'faces': {
            'north': {'texture': '#inside'}, 'south': {'texture': '#top'},
            'east': {'texture': '#side', 'uv': [0, 0, 2, 8]}, 'west': {'texture': '#side', 'uv': [0, 0, 2, 8]},
            'up': {'texture': '#side', 'uv': [3, 0, 13, 2]}, 'down': {'texture': '#side', 'uv': [3, 0, 13, 2]}}}
        # a tiny dancer turning on the cylinder
        els.append({'from': [7.5, 6, 7.5], 'to': [8.5, 9, 8.5], 'faces': faces('#dancer')})
        els.append({'from': [6.5, 7, 7.5], 'to': [9.5, 7.5, 8.5], 'faces': faces('#dancer')})
    els.append(lid)
    return {'textures': {'side': f'{NS}:block/music_box_side', 'front': f'{NS}:block/music_box_front', 'top': f'{NS}:block/music_box_top',
                         'inside': f'{NS}:block/music_box_inside', 'gold': 'minecraft:block/gold_block', 'dancer': 'minecraft:block/white_wool',
                         'particle': f'{NS}:block/music_box_side'},
            'elements': els}


write(f'assets/{NS}/models/block/music_box.json', music_box(False))
write(f'assets/{NS}/models/block/music_box_playing.json', music_box(True))
rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
variants = {}
for f, y in rot.items():
    for powered in ('false', 'true'):
        v = {'model': f'{NS}:block/music_box' + ('_playing' if powered == 'true' else '')}
        if y:
            v['y'] = y
        variants[f'facing={f},powered={powered}'] = v
write(f'assets/{NS}/blockstates/music_box.json', {'variants': variants})

for name in ('changing_table', 'music_box'):
    write(f'data/{NS}/loot_table/blocks/{name}.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{name}'}],
        'condition': {'type': 'minecraft:survives_explosion'}}]})

write(f'data/{NS}/recipe/changing_table.json', {
    'type': 'minecraft:crafting_shaped', 'category': 'misc',
    'pattern': ['WWW', 'PCP', 'PPP'], 'key': {'W': 'minecraft:white_wool', 'P': '#minecraft:planks', 'C': 'minecraft:chest'},
    'result': {'id': f'{NS}:changing_table'}})
write(f'data/{NS}/recipe/music_box.json', {
    'type': 'minecraft:crafting_shaped', 'category': 'misc',
    'pattern': [' G ', 'PNP'], 'key': {'G': 'minecraft:gold_nugget', 'P': '#minecraft:planks', 'N': 'minecraft:note_block'},
    'result': {'id': f'{NS}:music_box'}})
write(f'data/{NS}/recipe/rattle.json', {
    'type': 'minecraft:crafting_shaped', 'category': 'misc',
    'pattern': [' D', 'S '], 'key': {'D': 'minecraft:pink_dye', 'S': 'minecraft:stick'},
    'result': {'id': f'{NS}:rattle'}})

# ---------- trash can ----------
block_item('trash_can')


def trash_can(open_lid):
    side = {d: {'texture': '#side', 'uv': [4, 4, 12, 16]} for d in ('north', 'south', 'east', 'west')}
    els = [
        {'from': [4, 0, 4], 'to': [12, 12, 12], 'faces': {**side, 'up': {'texture': '#inside'}, 'down': {'texture': '#lid'}}},
        {'from': [3.5, 11, 3.5], 'to': [12.5, 12, 12.5], 'faces': {
            **{d: {'texture': '#rim'} for d in ('north', 'south', 'east', 'west', 'down')}, 'up': {'texture': '#inside'}}},
        {'from': [4.5, 0, 3.75], 'to': [11.5, 0.75, 4], 'faces': faces('#rim')},  # foot pedal
    ]
    if open_lid:
        # lid tipped up against the back
        els.append({'from': [3.5, 12, 12.5], 'to': [12.5, 21, 13.5], 'faces': {
            'north': {'texture': '#lid_under'}, 'south': {'texture': '#lid'},
            'east': {'texture': '#rim'}, 'west': {'texture': '#rim'}, 'up': {'texture': '#rim'}, 'down': {'texture': '#rim'}}})
    else:
        els.append({'from': [3.5, 12, 3.5], 'to': [12.5, 13.25, 12.5], 'faces': {
            **{d: {'texture': '#rim'} for d in ('north', 'south', 'east', 'west')}, 'up': {'texture': '#lid'}, 'down': {'texture': '#lid_under'}}})
        els.append({'from': [7, 13.25, 7.25], 'to': [9, 14, 8.75], 'faces': faces('#rim')})  # handle
    return {'textures': {'side': f'{NS}:block/trash_can_side', 'lid': f'{NS}:block/trash_can_lid', 'lid_under': f'{NS}:block/trash_can_inside',
                         'inside': f'{NS}:block/trash_can_inside', 'rim': f'{NS}:block/trash_can_rim', 'particle': f'{NS}:block/trash_can_side'},
            'elements': els}


write(f'assets/{NS}/models/block/trash_can.json', trash_can(False))
write(f'assets/{NS}/models/block/trash_can_open.json', trash_can(True))
write(f'assets/{NS}/blockstates/trash_can.json', {'variants': {
    'open=false': {'model': f'{NS}:block/trash_can'}, 'open=true': {'model': f'{NS}:block/trash_can_open'}}})
write(f'data/{NS}/loot_table/blocks/trash_can.json', {'type': 'minecraft:block', 'pools': [{
    'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{NS}:trash_can'}],
    'condition': {'type': 'minecraft:survives_explosion'}}]})
write(f'data/{NS}/recipe/trash_can.json', {
    'type': 'minecraft:crafting_shapeless', 'category': 'misc',
    'ingredients': ['minecraft:cauldron', 'minecraft:iron_trapdoor', 'minecraft:lime_dye'],
    'result': {'id': f'{NS}:trash_can'}})
adv('nothing_but_net', 'Nothing but Net', 'Throw a dirty diaper into a trash can.', f'{NS}:dirty_diaper')

# ---------- NPC babies ----------
item('baby_spawn_egg')
adv('parenthood', 'Parenthood', 'Have a baby with a Baby Spawn Egg.', f'{NS}:baby_spawn_egg')
adv('raised_right', 'Raised Right', 'Raise an NPC baby all the way to a Young Adult.', 'minecraft:cake', frame='challenge')

adv('home_sweet_home', 'Home Sweet Home', 'Your NPC Mom finished building your house.', 'minecraft:oak_door')

print('data written')
