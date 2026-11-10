"""Plushies of the aquariums' sea creatures (run from the repo root): chubby, rounded, stubby-finned soft toys with big
eyes - each a block model on its own 32x32 texture (textures/block/plushie_<name>.png), painted here.

Texture layout: side (0,0)-(16,8): along the body front to back, top to bottom; top (16,0)-(24,16): across, front to back;
belly (24,0)-(32,16); front (0,8)-(8,16); back (8,8)-(16,16); fin (0,16)-(8,24); fin 2 (16,16)-(24,24);
eye white (8,16)-(10,18); pupil (10,16)-(12,18); mouth (12,16)-(14,17); shine (14,16)-(15,17)."""
import json
from PIL import Image

A = 'src/main/resources/assets/skyblocks/'
R = {'side': [0, 0, 16, 8], 'top': [16, 0, 24, 16], 'belly': [24, 0, 32, 16], 'front': [0, 8, 8, 16], 'back': [8, 8, 16, 16],
     'fin': [0, 16, 8, 24], 'fin2': [16, 16, 24, 24], 'white': [8, 16, 10, 18], 'pupil': [10, 16, 12, 18], 'mouth': [12, 16, 14, 17]}
N = {'n': 'north', 's': 'south', 'e': 'east', 'w': 'west', 'u': 'up', 'd': 'down'}


def uv(name, flip=False):
    r = [q / 2 for q in R[name]]  # (32 px texture: model uv in 16ths)
    return [r[2], r[1], r[0], r[3]] if flip else r


def el(a, b, faces):
    """faces: {face letter: region name} - a side region on east/west runs front (north) to back."""
    f = {}
    for k, reg in faces.items():
        f[N[k]] = {'uv': uv(reg, flip=(k == 'e' and reg == 'side')), 'texture': '#t'}
    return {'from': [round(q, 3) for q in a], 'to': [round(q, 3) for q in b], 'faces': f}


def body_faces(front='front', back='back'):
    return {'n': front, 's': back, 'e': 'side', 'w': 'side', 'u': 'top', 'd': 'belly'}


def all_faces(reg):
    return {k: reg for k in 'nsewud'}


class Paint:
    def __init__(self):
        self.im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))

    def fill(self, name, fn):
        x0, y0, x1, y1 = R[name]
        for y in range(y0, y1):
            for x in range(x0, x1):
                c = fn((x - x0 + 0.5) / (x1 - x0), (y - y0 + 0.5) / (y1 - y0))
                self.im.putpixel((x, y), tuple(int(v) for v in c) + (255,))

    def save(self, name):
        self.fill('white', lambda u, v: (250, 250, 250))
        self.fill('pupil', lambda u, v: (20, 20, 28))
        self.im.putpixel((10, 16), (255, 255, 255, 255))  # (a shine in the pupil)
        self.fill('mouth', lambda u, v: (90, 30, 40))
        self.im.save(A + 'textures/block/plushie_' + name + '.png')


def fish(L, W, H, lift=0.0, dorsal=(2, 0.35, 0.45), anal=None, tail=(3, 0.8), side_fin=1.3, eye=1.7):
    """A fish plush: length, width, height (pixels); dorsal (height, start, end along it), tail (length, height share)."""
    els = []
    z0, z1 = 8 - L / 2, 8 + L / 2
    x0, x1 = 8 - W / 2, 8 + W / 2
    y0, y1 = lift + 0.6, lift + 0.6 + H
    els.append(el([x0, y0, z0 + 1.2], [x1, y1, z1 - 1.2], body_faces()))                  # middle
    els.append(el([x0 + 0.5, y0 + 0.6, z0], [x1 - 0.5, y1 - 0.6, z0 + 1.2], body_faces()))  # rounded front
    els.append(el([x0 + 0.5, y0 + 0.6, z1 - 1.2], [x1 - 0.5, y1 - 0.6, z1], body_faces()))  # rounded back
    els.append(el([x0 + 0.6, y1, z0 + 2.0], [x1 - 0.6, y1 + 0.5, z1 - 2.0], {k: 'top' if k == 'u' else 'side' for k in 'nsewu'}))
    els.append(el([x0 + 0.6, y0 - 0.6, z0 + 2.0], [x1 - 0.6, y0, z1 - 2.0], {k: 'belly' if k == 'd' else 'side' for k in 'nsewd'}))
    # big eyes on the sides of its head, a little mouth
    ey = y0 + H * 0.58
    for s in (-1, 1):
        xe = x1 if s > 0 else x0
        a, b = sorted([xe, xe + 0.25 * s])
        els.append(el([a, ey - eye / 2, z0 + 1.4], [b, ey + eye / 2, z0 + 1.4 + eye], all_faces('white')))
        a, b = sorted([xe + 0.25 * s, xe + 0.4 * s])
        els.append(el([a, ey - eye * 0.3, z0 + 1.5], [b, ey + eye * 0.3, z0 + 1.5 + eye * 0.6], all_faces('pupil')))
    els.append(el([7.3, y0 + H * 0.32, z0 - 0.1], [8.7, y0 + H * 0.32 + 0.45, z0], {'n': 'mouth'}))
    # fins: stubby, soft
    dh, ds, de = dorsal
    if dh > 0:
        els.append(el([7.4, y1 + 0.5, z0 + L * ds], [8.6, y1 + 0.5 + dh, z0 + L * de], all_faces('fin')))
    if anal:
        ah, as_, ae = anal
        els.append(el([7.4, y0 - 0.6 - ah, z0 + L * as_], [8.6, y0 - 0.6, z0 + L * ae], all_faces('fin')))
    for s in (-1, 1):
        a, b = sorted([8 + s * W / 2, 8 + s * (W / 2 + side_fin)])
        els.append(el([a, y0 + H * 0.25, z0 + L * 0.38], [b, y0 + H * 0.25 + 0.7, z0 + L * 0.38 + 2.0], all_faces('fin')))
    tl, th = tail
    if tl > 0:
        cy = (y0 + y1) / 2
        els.append(el([7.5, cy - 0.8, z1], [8.5, cy + 0.8, z1 + 0.8], all_faces('fin2')))           # its root
        els.append(el([7.3, cy - H * th / 2, z1 + 0.8], [8.7, cy + H * th / 2, z1 + 0.8 + tl], all_faces('fin2')))
    return els


def bands(base, band, edge, at, width=0.1):
    """A side painter: bands across the body at the given places (0 front .. 1 back)."""
    def f(u, v):
        for c in at:
            d = abs(u - c)
            if d < width: return band
            if d < width + 0.04: return edge
        return base
    return f


def mix(a, b, t):
    return tuple(a[i] * (1 - t) + b[i] * t for i in range(3))


PLUSH = {}

# clownfish: orange, three white bands edged in black
p = Paint(); O, Wt, K = (245, 120, 30), (250, 250, 245), (30, 24, 24)
side = bands(O, Wt, K, [0.2, 0.52, 0.86], 0.08)
p.fill('side', side); p.fill('top', lambda u, v: side(v, 0)); p.fill('belly', lambda u, v: mix(side(v, 0), Wt, 0.3))
p.fill('front', lambda u, v: O); p.fill('back', lambda u, v: O)
p.fill('fin', lambda u, v: K if u < 0.15 or u > 0.85 or v > 0.85 else O); p.fill('fin2', lambda u, v: K if v > 0.8 or u > 0.85 else O)
PLUSH['clownfish'] = (p, fish(11, 5.2, 6, dorsal=(1.8, 0.3, 0.7), tail=(2.6, 0.75)))

# blue tang: blue, a black swoosh, yellow tail
p = Paint(); B, Y = (40, 90, 220), (250, 210, 40)
def tang(u, v):
    if 0.3 < u < 0.9 and abs(v - (0.3 + 0.25 * (u - 0.3))) < 0.12: return K
    return B
p.fill('side', tang); p.fill('top', lambda u, v: B); p.fill('belly', lambda u, v: mix(B, (255, 255, 255), 0.25))
p.fill('front', lambda u, v: B); p.fill('back', lambda u, v: Y)
p.fill('fin', lambda u, v: K if v > 0.8 else B); p.fill('fin2', lambda u, v: Y if v < 0.8 else K)
PLUSH['blue_tang'] = (p, fish(11, 4.6, 7, dorsal=(1.6, 0.2, 0.85), anal=None, tail=(2.4, 0.7)))

# goldfish: gold, paler belly, a big soft tail
p = Paint(); G, Gp = (255, 150, 30), (255, 210, 120)
p.fill('side', lambda u, v: mix(G, Gp, max(0, v - 0.5) * 1.6)); p.fill('top', lambda u, v: G); p.fill('belly', lambda u, v: Gp)
p.fill('front', lambda u, v: G); p.fill('back', lambda u, v: G)
p.fill('fin', lambda u, v: mix(G, (255, 240, 220), v * 0.6)); p.fill('fin2', lambda u, v: mix(G, (255, 240, 220), v * 0.7))
PLUSH['goldfish'] = (p, fish(10, 5.6, 6.4, dorsal=(2.0, 0.3, 0.6), tail=(4.2, 1.0)))

# angelfish: silver, black upright stripes, tall fins
p = Paint(); S = (215, 220, 225)
side = bands(S, K, S, [0.3, 0.6], 0.06)
p.fill('side', side); p.fill('top', lambda u, v: side(v, 0)); p.fill('belly', lambda u, v: S)
p.fill('front', lambda u, v: S); p.fill('back', lambda u, v: S)
p.fill('fin', lambda u, v: K if abs(u - 0.5) < 0.12 else mix(S, (255, 255, 255), 0.3)); p.fill('fin2', lambda u, v: mix(S, (255, 255, 255), 0.4))
PLUSH['angelfish'] = (p, fish(8, 4.2, 6.6, lift=2.6, dorsal=(3.2, 0.25, 0.65), anal=(2.6, 0.3, 0.7), tail=(2.0, 0.8)))

# shark: grey back, white belly, a tall fin and a crescent tail
p = Paint(); Gy, Wh = (120, 132, 145), (240, 242, 245)
p.fill('side', lambda u, v: Gy if v < 0.55 else Wh); p.fill('top', lambda u, v: Gy); p.fill('belly', lambda u, v: Wh)
p.fill('front', lambda u, v: Gy if v < 0.55 else Wh); p.fill('back', lambda u, v: Gy)
p.fill('fin', lambda u, v: Gy); p.fill('fin2', lambda u, v: Gy)
els = fish(14, 5.2, 4.6, dorsal=(3.0, 0.35, 0.55), tail=(2.4, 1.4), side_fin=2.4, eye=1.3)
els.append(el([7.5, 0.6 + 4.6 + 0.5 + 3.0, 8 - 7 + 14 * 0.38], [8.5, 0.6 + 4.6 + 0.5 + 3.6, 8 - 7 + 14 * 0.48], all_faces('fin')))  # (its fin's tip)
# a toothy grin
els.append(el([6.6, 0.6 + 4.6 * 0.3, 0.9], [9.4, 0.6 + 4.6 * 0.3 + 0.35, 1.0], {'n': 'white'}))
PLUSH['shark'] = (p, els)

# ocean sunfish: tall and round, tall fins top and bottom, a frilly back end
p = Paint(); Sf = (160, 172, 188)
p.fill('side', lambda u, v: mix(Sf, (230, 232, 236), max(0, v - 0.4))); p.fill('top', lambda u, v: Sf); p.fill('belly', lambda u, v: (230, 232, 236))
p.fill('front', lambda u, v: Sf); p.fill('back', lambda u, v: Sf)
p.fill('fin', lambda u, v: mix(Sf, (100, 110, 125), v * 0.5)); p.fill('fin2', lambda u, v: mix(Sf, (120, 130, 145), (u * 7 % 1) * 0.6))
PLUSH['ocean_sunfish'] = (p, fish(7.5, 3.8, 8.0, lift=2.4, dorsal=(3.4, 0.5, 0.8), anal=(2.4, 0.5, 0.8), tail=(1.2, 0.9), side_fin=1.0, eye=1.6))

# octopus: a soft dome with eight curly stubby arms
p = Paint(); Oc, Oc2 = (225, 90, 80), (245, 170, 150)
spot = lambda u, v: Oc2 if (int(u * 8) + int(v * 8) * 3) % 7 == 0 else Oc
for reg in ('side', 'top', 'front', 'back'): p.fill(reg, spot)
p.fill('belly', lambda u, v: Oc2); p.fill('fin', lambda u, v: Oc if v < 0.6 else Oc2); p.fill('fin2', lambda u, v: Oc2)
els = [el([4.5, 3.0, 4.5], [11.5, 9.0, 11.5], body_faces()), el([5.2, 9.0, 5.2], [10.8, 10.2, 10.8], body_faces()),
       el([5, 1.6, 5], [11, 3.0, 11], body_faces())]
for s in (-1, 1):
    x = 4.5 if s < 0 else 11.5
    els.append(el([min(x, x + 0.2 * s), 5.8, 4.4], [max(x, x + 0.2 * s) if False else max(x, x + 0.2 * s), 7.6, 4.5], {}))
els = [e for e in els if e['faces']]
for xe in (5.3, 9.0):  # (big eyes on the front)
    els.append(el([xe, 5.4, 4.3], [xe + 1.8, 7.4, 4.5], all_faces('white')))
    els.append(el([xe + 0.5, 5.7, 4.15], [xe + 1.4, 6.8, 4.3], all_faces('pupil')))
els.append(el([7.2, 4.4, 4.4], [8.8, 4.8, 4.5], {'n': 'mouth'}))
import math
for k in range(8):
    a = k * math.pi / 4 + math.pi / 8
    cx, cz = 8 + math.sin(a) * 4.2, 8 + math.cos(a) * 4.2
    tx, tz = 8 + math.sin(a) * 6.3, 8 + math.cos(a) * 6.3
    els.append(el([min(cx, tx) - 0.7, 0, min(cz, tz) - 0.7], [max(cx, tx) + 0.7, 1.6, max(cz, tz) + 0.7], {k2: 'fin' for k2 in 'nsewud'}))
    els.append(el([tx - 0.6, 1.6, tz - 0.6], [tx + 0.6, 2.4, tz + 0.6], all_faces('fin2')))  # (curled up at the end)
PLUSH['octopus'] = (p, els)

# crab: wide and round, big claws, little legs, eyes on stalks
p = Paint(); Cr, Cr2 = (220, 70, 50), (250, 190, 150)
for reg in ('side', 'top', 'front', 'back'): p.fill(reg, lambda u, v: Cr)
p.fill('belly', lambda u, v: Cr2); p.fill('fin', lambda u, v: Cr); p.fill('fin2', lambda u, v: Cr2 if v > 0.5 else Cr)
els = [el([3.5, 1.2, 5], [12.5, 4.6, 11], body_faces()), el([4.3, 4.6, 5.8], [11.7, 5.2, 10.2], {k: 'top' if k == 'u' else 'side' for k in 'nsewu'}),
       el([4.2, 0.6, 5.6], [11.8, 1.2, 10.4], {k: 'belly' if k == 'd' else 'side' for k in 'nsewd'})]
for s in (-1, 1):
    def X(a, b): return ([a[0], a[1], a[2]], [b[0], b[1], b[2]]) if s < 0 else ([16 - b[0], a[1], a[2]], [16 - a[0], b[1], b[2]])
    for a, b, reg in (([2.4, 2.0, 3.2], [4.2, 3.0, 6.0], 'fin'),        # arm
                      ([1.0, 1.4, 0.8], [4.0, 4.4, 3.6], 'fin'),        # claw
                      ([1.0, 1.4, 0.2], [2.2, 3.0, 0.8], 'fin2'),       # its pincers
                      ([2.8, 1.4, 0.2], [4.0, 3.0, 0.8], 'fin2'),
                      ([6.0, 4.6, 5.0], [6.6, 6.6, 5.6], 'fin'),        # eye stalk
                      ([5.5, 6.6, 4.6], [7.1, 8.2, 6.2], 'white'),
                      ([5.9, 6.9, 4.45], [6.7, 7.8, 4.6], 'pupil')):
        a, b = X(a, b); els.append(el(a, b, all_faces(reg)))
    for z in (6.4, 8.0, 9.6):
        a, b = X([1.6, 0, z], [3.6, 1.6, z + 0.9]); els.append(el(a, b, all_faces('fin')))
els.append(el([7.0, 2.4, 4.9], [9.0, 2.8, 5.0], {'n': 'mouth'}))
PLUSH['crab'] = (p, els)

cat = json.load(open(A + 'models/block/plushie_cat.json'))
for name, (paint, els) in PLUSH.items():
    paint.save(name)
    for e in els:  # (keep inside what a model allows)
        e['from'] = [max(-16, min(32, q)) for q in e['from']]; e['to'] = [max(-16, min(32, q)) for q in e['to']]
    model = {'parent': 'block/block', 'textures': {'t': 'skyblocks:block/plushie_' + name, 'particle': 'skyblocks:block/plushie_' + name},
             'elements': els, 'display': cat.get('display', {})}
    json.dump(model, open(A + 'models/block/plushie_' + name + '.json', 'w'), indent=1)
    json.dump({'parent': 'skyblocks:block/plushie_' + name}, open(A + 'models/item/plushie_' + name + '.json', 'w'))
    bs = open(A + 'blockstates/plushie_cat.json').read().replace('plushie_cat', 'plushie_' + name)
    open(A + 'blockstates/plushie_' + name + '.json', 'w').write(bs)
    print(name, len(els))
