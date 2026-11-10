"""The terrarium's frame pieces (run from the repo root): for each side, a glass wall with rails top and bottom when no
tank is joined on that side, or a strip of floor, ground and lid carrying on into the next tank when one is - each in
four lengths, reaching right to the corner where the tank on that end is joined too, so joined tanks make one smooth tank;
and the corner post, only on outside corners. The scenes (terrarium_desert / _jungle) are the middle."""
import json, os

B = 'src/main/resources/assets/skyblocks/'
M = B + 'models/block/'
TEX = {'frame': 'minecraft:block/dark_oak_planks', 'glass': 'minecraft:block/white_stained_glass',
       'mesh': 'minecraft:block/iron_bars', 'particle': 'minecraft:block/dark_oak_planks'}
GROUND = {'desert': 'minecraft:block/sand', 'jungle': 'minecraft:block/moss_block'}
N = {'n': 'north', 's': 'south', 'e': 'east', 'w': 'west', 'u': 'up', 'd': 'down'}

def el(a, b, tex, faces='nsewud'):
    return {'from': a, 'to': b, 'faces': {N[k]: {'texture': tex} for k in faces}}

def save(name, els, tex):
    json.dump({'parent': 'block/block', 'render_type': 'minecraft:translucent', 'textures': tex, 'elements': els},
              open(M + name + '.json', 'w'), indent=1)

for f in os.listdir(M):
    if f.startswith(('terrarium_wall_', 'terrarium_join_', 'terrarium_post_')):
        os.remove(M + f)

# (made as the north side; a: joined at its x=0 end, b: at its x=16 end)
for a in (0, 1):
    for b in (0, 1):
        k = '%d%d' % (a, b)
        x0, x1 = (0 if a else 1.8), (16 if b else 14.2)
        save('terrarium_wall_' + k, [
            el([x0, 0, 0.8], [x1, 1.5, 1.8], '#frame'),
            el([x0, 13.2, 0.8], [x1, 14.2, 1.8], '#frame'),
            el([x0, 1.5, 1.1], [x1, 13.2, 1.5], '#glass', 'ns'),
        ], TEX)
        x0, x1 = (0 if a else 1), (16 if b else 15)
        for scene, ground in GROUND.items():
            save('terrarium_join_%s_%s' % (scene, k), [
                el([x0, 0, 0], [x1, 1, 1], '#frame', 'd'),
                el([x0, 1, 0], [x1, 3, 1], '#ground', 'u'),
                el([x0, 13.5, 0], [x1, 14, 1], '#mesh', 'ud'),
            ], dict(TEX, ground=ground))
save('terrarium_post', [el([0.8, 0, 0.8], [1.8, 14.2, 1.8], '#frame'),
                        el([0.6, 14.2, 0.6], [2.0, 14.6, 2.0], '#frame')], TEX)

# the blockstate: which end is which, turned to each side
SIDES = [('north', 0, 'west', 'east'), ('east', 90, 'north', 'south'), ('south', 180, 'east', 'west'), ('west', 270, 'south', 'north')]
parts = [{'when': {'scene': '0'}, 'apply': {'model': 'skyblocks:block/terrarium_desert'}},
         {'when': {'scene': '1'}, 'apply': {'model': 'skyblocks:block/terrarium_jungle'}}]
for side, y, ea, eb in SIDES:
    for a in (0, 1):
        for b in (0, 1):
            k = '%d%d' % (a, b)
            when = {side: 'false', ea: str(bool(a)).lower(), eb: str(bool(b)).lower()}
            parts.append({'when': when, 'apply': dict({'model': 'skyblocks:block/terrarium_wall_' + k}, **({'y': y} if y else {}))})
            for si, scene in enumerate(GROUND):
                when = {side: 'true', 'scene': str(si), ea: str(bool(a)).lower(), eb: str(bool(b)).lower()}
                parts.append({'when': when, 'apply': dict({'model': 'skyblocks:block/terrarium_join_%s_%s' % (scene, k)}, **({'y': y} if y else {}))})
# outside corners only (north-west as made, turned clockwise round)
for (s1, s2), y in ((('north', 'west'), 0), (('north', 'east'), 90), (('south', 'east'), 180), (('south', 'west'), 270)):
    parts.append({'when': {s1: 'false', s2: 'false'}, 'apply': dict({'model': 'skyblocks:block/terrarium_post'}, **({'y': y} if y else {}))})
json.dump({'multipart': parts}, open(B + 'blockstates/terrarium.json', 'w'), indent=1)
print(len(parts), 'parts')
