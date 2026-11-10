"""Every plushie (run from the repo root): soft, rounded, chibi - big heads, big shiny eyes, rosy cheeks, little smiles,
stubby limbs. Each a block model (front to the north, sitting on the floor) on its own 32x32 texture, painted here
(textures/block/plushie_<name>.png).

Texture layout: side (0,0)-(16,8): along the body, front to back; top (16,0)-(24,16); belly (24,0)-(32,16);
front (0,8)-(8,16); back (8,8)-(16,16); fin/accent (0,16)-(8,24); accent 2 (16,16)-(24,24); accent 3 (24,16)-(32,24);
white (8,16)-(10,18); black (10,16)-(12,18); mouth (12,16)-(14,17); blush (12,17)-(14,18); pale (14,16)-(16,18)."""
import json, math, random
from PIL import Image

A = 'src/main/resources/assets/skyblocks/'
R = {'side': [0, 0, 16, 8], 'top': [16, 0, 24, 16], 'belly': [24, 0, 32, 16], 'front': [0, 8, 8, 16], 'back': [8, 8, 16, 16],
     'fin': [0, 16, 8, 24], 'fin2': [16, 16, 24, 24], 'fin3': [24, 16, 32, 24], 'white': [8, 16, 10, 18], 'black': [10, 16, 12, 18],
     'mouth': [12, 16, 14, 17], 'blush': [12, 17, 14, 18], 'pale': [14, 16, 16, 18]}
N = {'n': 'north', 's': 'south', 'e': 'east', 'w': 'west', 'u': 'up', 'd': 'down'}


def uv(name, flip=False):
    r = [q / 2 for q in R[name]]
    return [r[2], r[1], r[0], r[3]] if flip else r


def el(a, b, faces, frame=None):
    """frame: the whole part this piece belongs to - each face then takes just its own share of its texture region, so a
    pattern (a clownfish's bands) carries on across the pieces of a rounded body."""
    f = {}
    for k, reg in faces.items():
        if frame is None:
            f[N[k]] = {'uv': uv(reg, flip=(k == 'e' and reg == 'side')), 'texture': '#t'}
            continue
        fx = lambda q: (q - frame[0]) / max(frame[3] - frame[0], 1e-6)
        fy = lambda q: (frame[4] - q) / max(frame[4] - frame[1], 1e-6)
        fz = lambda q: (q - frame[2]) / max(frame[5] - frame[2], 1e-6)
        if k in 'ew':
            u0, u1, v0, v1 = fz(a[2]), fz(b[2]), fy(b[1]), fy(a[1])
            if k == 'e' and reg == 'side': u0, u1 = u1, u0
        elif k in 'ns':
            u0, u1, v0, v1 = fx(a[0]), fx(b[0]), fy(b[1]), fy(a[1])
        else:
            u0, u1, v0, v1 = fx(a[0]), fx(b[0]), fz(a[2]), fz(b[2])
        r = [q / 2 for q in R[reg]]
        w, h = r[2] - r[0], r[3] - r[1]
        f[N[k]] = {'uv': [round(r[0] + w * u0, 4), round(r[1] + h * v0, 4), round(r[0] + w * u1, 4), round(r[1] + h * v1, 4)], 'texture': '#t'}
    return {'from': [round(q, 3) for q in a], 'to': [round(q, 3) for q in b], 'faces': f}


def F(reg):
    return {k: reg for k in 'nsewud'}


def body_faces(front='front', back='back', side='side', top='top', belly='belly'):
    return {'n': front, 's': back, 'e': side, 'w': side, 'u': top, 'd': belly}


def rbox(a, b, faces, r=0.6):
    """A box with its edges rounded off: three boxes, each pulled in along two of the axes."""
    r = min(r, (b[0] - a[0]) / 2.5, (b[1] - a[1]) / 2.5, (b[2] - a[2]) / 2.5)
    frame = list(a) + list(b)
    return [el([a[0], a[1] + r, a[2] + r], [b[0], b[1] - r, b[2] - r], faces, frame),
            el([a[0] + r, a[1], a[2] + r], [b[0] - r, b[1], b[2] - r], faces, frame),
            el([a[0] + r, a[1] + r, a[2]], [b[0] - r, b[1] - r, b[2]], faces, frame)]


def decal(face, u0, v0, u1, v1, plane, reg, depth=0.1):
    """A thin piece on a face: n (front, u across x, v up), e/w (sides, u along z), u (top, u across x, v along z)."""
    if face == 'n': return el([u0, v0, plane - depth], [u1, v1, plane], F(reg))
    if face == 's': return el([u0, v0, plane], [u1, v1, plane + depth], F(reg))
    if face == 'e': return el([plane, v0, u0], [plane + depth, v1, u1], F(reg))
    if face == 'w': return el([plane - depth, v0, u0], [plane, v1, u1], F(reg))
    return el([u0, plane, v0], [u1, plane + depth, v1], F(reg))


def eye(face, u, v, plane, size=1.6, mirror=False):
    """A big shiny eye: black, a big sparkle up at the top, a little one below."""
    out = [decal(face, u, v, u + size, v + size * 1.15, plane, 'black', 0.12)]
    s = size
    big = (u + s * 0.12, v + s * 0.62, u + s * 0.5, v + s * 1.0) if not mirror else (u + s * 0.5, v + s * 0.62, u + s * 0.88, v + s * 1.0)
    small = (u + s * 0.62, v + s * 0.18, u + s * 0.82, v + s * 0.38) if not mirror else (u + s * 0.18, v + s * 0.18, u + s * 0.38, v + s * 0.38)
    for b in (big, small):
        out.append(decal(face, b[0], b[1], b[2], b[3], plane - 0.12 if face in 'nw' else plane + 0.12, 'white', 0.06))
    return out


def face(plane, cx, eye_y, gap=1.6, size=1.7, mouth_y=None, blush=True, cat=False):
    """Eyes either side of cx, rosy cheeks below them, a little smile (a cat's: an ω) - all on the front at z=plane."""
    out = []
    out += eye('n', cx - gap / 2 - size, eye_y, plane, size)
    out += eye('n', cx + gap / 2, eye_y, plane, size, mirror=True)
    if blush:
        out.append(decal('n', cx - gap / 2 - size - 0.5, eye_y - 0.9, cx - gap / 2 - 0.4, eye_y - 0.35, plane, 'blush', 0.05))
        out.append(decal('n', cx + gap / 2 + 0.4, eye_y - 0.9, cx + gap / 2 + size + 0.5, eye_y - 0.35, plane, 'blush', 0.05))
    my = eye_y - 0.75 if mouth_y is None else mouth_y
    if cat:
        out.append(decal('n', cx - 0.25, my + 0.35, cx + 0.25, my + 0.65, plane, 'blush', 0.08))   # (its nose)
        out.append(decal('n', cx - 0.75, my, cx - 0.05, my + 0.25, plane, 'mouth', 0.06))
        out.append(decal('n', cx + 0.05, my, cx + 0.75, my + 0.25, plane, 'mouth', 0.06))
        out.append(decal('n', cx - 0.95, my + 0.2, cx - 0.7, my + 0.4, plane, 'mouth', 0.06))
        out.append(decal('n', cx + 0.7, my + 0.2, cx + 0.95, my + 0.4, plane, 'mouth', 0.06))
    else:
        out.append(decal('n', cx - 0.5, my, cx + 0.5, my + 0.25, plane, 'mouth', 0.06))
        out.append(decal('n', cx - 0.75, my + 0.2, cx - 0.5, my + 0.45, plane, 'mouth', 0.06))
        out.append(decal('n', cx + 0.5, my + 0.2, cx + 0.75, my + 0.45, plane, 'mouth', 0.06))
    return out


class Paint:
    def __init__(self, seed=1):
        self.im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
        self.r = random.Random(seed)

    def fill(self, name, fn, fuzz=6):
        x0, y0, x1, y1 = R[name]
        for y in range(y0, y1):
            for x in range(x0, x1):
                c = fn((x - x0 + 0.5) / (x1 - x0), (y - y0 + 0.5) / (y1 - y0))
                n = self.r.randint(-fuzz, fuzz)  # (a soft, fabric-y speckle)
                self.im.putpixel((x, y), tuple(max(0, min(255, int(v) + n)) for v in c) + (255,))

    def solid(self, regs, c, fuzz=6):
        for g in regs: self.fill(g, lambda u, v: c, fuzz)

    def save(self, name, blush=(255, 140, 170)):
        self.fill('white', lambda u, v: (255, 255, 255), 0)
        self.fill('black', lambda u, v: (24, 20, 30), 0)
        self.fill('mouth', lambda u, v: (70, 30, 40), 0)
        self.fill('blush', lambda u, v: blush, 0)
        self.fill('pale', lambda u, v: (255, 240, 230), 0)
        self.im.save(A + 'textures/block/plushie_' + name + '.png')


def mix(a, b, t):
    return tuple(a[i] * (1 - t) + b[i] * t for i in range(3))


PLUSH = {}

# ---------------------------------------------------------------- land plushies

# cat: a sitting loaf with a big round head, pink-lined ears, a curly tail, white tummy and paws
p = Paint(2); G, Gd, Cr, Pk = (168, 170, 182), (128, 130, 144), (242, 236, 228), (250, 168, 190)
p.solid(['side', 'top', 'front', 'back'], G); p.solid(['belly'], Cr); p.solid(['fin'], Pk); p.solid(['fin2'], Gd); p.solid(['fin3'], Cr)
els = rbox([4.6, 0, 6.2], [11.4, 5.2, 12.4], body_faces(front='fin3'), 1.0)                     # body
els += rbox([3.4, 4.4, 3.4], [12.6, 11.4, 10.6], body_faces(), 1.3)                            # head
for x0 in (3.9, 9.6):                                                                          # ears, pink inside
    els += rbox([x0, 11.0, 5.2], [x0 + 2.5, 13.4, 7.4], F('side'), 0.5)
    els.append(decal('n', x0 + 0.55, 11.3, x0 + 1.95, 12.8, 5.2, 'fin', 0.06))
for x0 in (8.0 - 2.4, 8.0 + 0.2):                                                              # stripes on its head
    pass
els.append(decal('u', 7.3, 4.0, 8.7, 6.2, 11.4, 'fin2', 0.04))
els.append(decal('u', 6.0, 4.4, 6.8, 5.8, 11.4, 'fin2', 0.04)); els.append(decal('u', 9.2, 4.4, 10.0, 5.8, 11.4, 'fin2', 0.04))
for x0 in (5.2, 8.9):                                                                          # paws
    els += rbox([x0, 0, 4.6], [x0 + 1.9, 1.6, 6.8], F('fin3'), 0.4)
els += rbox([10.4, 0, 11.2], [13.2, 1.6, 12.8], F('side'), 0.4)                                # tail, curling round
els += rbox([11.8, 0, 8.2], [13.2, 1.6, 11.4], F('side'), 0.4)
els += rbox([11.8, 1.0, 7.6], [13.0, 2.6, 8.8], F('fin2'), 0.3)
els += face(3.4, 8.0, 7.3, gap=2.0, size=1.6, mouth_y=5.9, cat=True)
PLUSH['cat'] = (p, els, None)


def slime(name, base, light, accent, topper, seed):
    p = Paint(seed); p.solid(['side', 'top', 'front', 'back'], base); p.solid(['belly'], mix(base, (0, 0, 0), 0.2))
    p.solid(['fin'], accent); p.solid(['fin2'], light); p.solid(['fin3'], (255, 225, 80) if name == 'ember' else mix(accent, (255, 255, 255), 0.3))
    els = rbox([3.0, 0, 3.6], [13.0, 8.4, 12.4], body_faces(), 1.6)                             # a squishy round cube
    els += rbox([3.6, 0, 4.2], [12.4, 0.8, 11.8], F('belly'), 0.3)
    els.append(decal('n', 4.2, 6.2, 5.6, 7.4, 3.6, 'fin2', 0.05))                               # a shine on it
    els.append(decal('u', 4.4, 4.4, 6.0, 5.4, 8.4, 'fin2', 0.04))
    els += face(3.6, 8.0, 3.6, gap=2.2, size=1.8, mouth_y=2.7)
    els += topper()
    PLUSH[name] = (p, els, None)


def flame():  # ember: a little flame on top
    return (rbox([6.8, 8.4, 6.6], [9.2, 10.4, 9.0], F('fin'), 0.5) + rbox([7.3, 10.2, 7.1], [8.7, 11.8, 8.5], F('fin3'), 0.3)
            + [el([7.7, 11.6, 7.5], [8.3, 12.6, 8.1], F('fin3'))])


def crystal():  # frost: an ice crystal
    return [el([7.4, 8.4, 7.4], [8.6, 11.6, 8.6], F('fin')), el([6.6, 9.2, 7.6], [9.4, 10.0, 8.4], F('fin')),
            el([7.6, 9.2, 6.6], [8.4, 10.0, 9.4], F('fin'))]


def sprout():  # moss: a sprout with two leaves
    return [el([7.7, 8.4, 7.7], [8.3, 10.4, 8.3], F('fin')), el([5.8, 10.0, 7.5], [7.8, 10.5, 8.6], F('fin3')),
            el([8.2, 10.4, 7.4], [10.4, 10.9, 8.6], F('fin3'))]


def flower():  # bloom: a flower
    out = [el([7.7, 8.4, 7.7], [8.3, 9.6, 8.3], F('fin2'))]
    for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        out.append(el([7.4 + dx * 0.9, 9.6, 7.4 + dz * 0.9], [8.6 + dx * 0.9, 10.2, 8.6 + dz * 0.9], F('fin')))
    out.append(el([7.5, 9.7, 7.5], [8.5, 10.4, 8.5], F('fin3')))
    return out


slime('ember', (244, 104, 64), (255, 190, 150), (255, 120, 30), flame, 3)
slime('frost', (110, 196, 245), (225, 245, 255), (200, 240, 255), crystal, 4)
slime('moss', (112, 196, 92), (205, 245, 180), (70, 150, 60), sprout, 5)
slime('bloom', (250, 150, 200), (255, 225, 240), (255, 230, 90), flower, 6)

# frog: round and wide, eyes up on bumps, a pale tummy, little feet
p = Paint(7); Fg, Fl, Fd = (110, 200, 90), (232, 240, 180), (80, 160, 70)
p.solid(['side', 'top', 'front', 'back'], Fg); p.solid(['belly'], Fl); p.solid(['fin'], Fd); p.solid(['fin3'], Fl)
els = rbox([2.8, 0, 4.0], [13.2, 6.2, 12.4], body_faces(), 1.8)
for x0 in (3.2, 8.8):
    els += rbox([x0, 5.0, 4.6], [x0 + 4.0, 8.6, 8.4], F('side'), 1.1)
els.append(decal('n', 5.4, 0.6, 10.6, 3.2, 4.0, 'fin3', 0.06))                                # tummy
els += eye('n', 4.3, 6.0, 4.6, 1.8); els += eye('n', 9.9, 6.0, 4.6, 1.8, mirror=True)
els.append(decal('n', 3.4, 3.8, 4.8, 4.4, 4.0, 'blush', 0.06)); els.append(decal('n', 11.2, 3.8, 12.6, 4.4, 4.0, 'blush', 0.06))
els.append(decal('n', 6.2, 3.6, 9.8, 3.85, 4.0, 'mouth', 0.07))                              # a wide smile
els.append(decal('n', 5.9, 3.85, 6.2, 4.15, 4.0, 'mouth', 0.07)); els.append(decal('n', 9.8, 3.85, 10.1, 4.15, 4.0, 'mouth', 0.07))
for x0 in (3.6, 10.4):                                                                         # feet
    els += rbox([x0, 0, 2.8], [x0 + 2.0, 0.9, 4.6], F('fin'), 0.3)
for x0 in (1.8, 12.2):
    els += rbox([x0, 0, 8.4], [x0 + 2.0, 2.4, 12.0], F('fin'), 0.6)
PLUSH['frog'] = (p, els, None)

# axolotl: a big round pink head, frilly gills, a little body and tail
p = Paint(8); Pk, Pl, Gl = (250, 170, 196), (255, 220, 230), (220, 70, 140)
p.solid(['side', 'top', 'front', 'back'], Pk); p.solid(['belly'], Pl); p.solid(['fin'], Gl); p.solid(['fin2'], Pl); p.solid(['fin3'], mix(Gl, Pk, 0.5))
els = rbox([5.0, 0, 8.0], [11.0, 4.0, 13.4], body_faces(), 1.0)
els += rbox([3.6, 0.2, 2.6], [12.4, 7.4, 8.6], body_faces(), 1.6)
els += rbox([7.2, 0.6, 13.0], [8.8, 3.6, 15.8], F('fin2'), 0.5)                               # tail fin
els.append(el([7.6, 3.6, 9.0], [8.4, 4.4, 13.0], F('fin2')))
for s in (-1, 1):
    def X(a, b):
        return (a, b) if s < 0 else ([16 - b[0], a[1], a[2]], [16 - a[0], b[1], b[2]])
    for a, b in (([2.6, 6.0, 4.6], [3.8, 8.8, 5.8]), ([1.6, 4.4, 4.6], [3.8, 5.4, 5.8]), ([2.0, 2.6, 4.6], [3.8, 3.6, 5.8])):
        a, b = X(a, b); els += rbox(a, b, F('fin'), 0.3)
    for a, b in (([4.4, 0, 8.4], [5.8, 1.2, 9.8]), ([4.4, 0, 11.4], [5.8, 1.2, 12.8])):
        a, b = X(a, b); els += rbox(a, b, F('fin3'), 0.3)
els += face(2.6, 8.0, 3.8, gap=3.0, size=1.5, mouth_y=2.8)
PLUSH['axolotl'] = (p, els, None)

# ---------------------------------------------------------------- sea creatures


def fish(L, W, H, lift=0.0, dorsal=(2, 0.35, 0.45), anal=None, tail=(3, 0.8), side_fin=1.3, eye_size=1.9):
    els = []
    z0, z1 = 8 - L / 2, 8 + L / 2
    x0, x1 = 8 - W / 2, 8 + W / 2
    y0, y1 = lift + 0.4, lift + 0.4 + H
    els += rbox([x0, y0, z0], [x1, y1, z1], body_faces(), min(W, H) * 0.3)
    els += rbox([x0 + W * 0.2, lift, z0 + L * 0.25], [x1 - W * 0.2, y0 + 0.6, z1 - L * 0.25], F('belly'), 0.2)
    ey = y0 + H * 0.45
    els += eye('w', z0 + 1.0, ey, x0, eye_size)                         # big eyes on the sides, near the front
    els += eye('e', z0 + 1.0, ey, x1, eye_size, mirror=True)
    for xe, fc, a, b in ((x0, 'w', z0 + 0.6, z0 + 1.0 + eye_size + 0.2), (x1, 'e', z0 + 0.6, z0 + 1.0 + eye_size + 0.2)):
        els.append(decal(fc, a, ey - 0.8, b, ey - 0.3, xe, 'blush', 0.05))
    my = y0 + H * 0.3
    els.append(decal('n', 7.4, my, 8.6, my + 0.3, z0, 'mouth', 0.06))
    els.append(decal('n', 7.15, my + 0.25, 7.4, my + 0.5, z0, 'mouth', 0.06)); els.append(decal('n', 8.6, my + 0.25, 8.85, my + 0.5, z0, 'mouth', 0.06))
    dh, ds, de = dorsal
    if dh > 0:
        els += rbox([7.3, y1 - 0.4, z0 + L * ds], [8.7, y1 + dh, z0 + L * de], F('fin'), 0.4)
    if anal:
        ah, as_, ae = anal
        els += rbox([7.3, y0 - ah, z0 + L * as_], [8.7, y0 + 0.4, z0 + L * ae], F('fin'), 0.4)
    for s in (-1, 1):
        a, b = sorted([8 + s * (W / 2 - 0.3), 8 + s * (W / 2 + side_fin)])
        els += rbox([a, y0 + H * 0.2, z0 + L * 0.42], [b, y0 + H * 0.2 + 0.9, z0 + L * 0.42 + 2.2], F('fin'), 0.3)
    tl, th = tail
    if tl > 0:
        cy = (y0 + y1) / 2
        els += rbox([7.4, cy - 0.9, z1 - 0.4], [8.6, cy + 0.9, z1 + 0.8], F('fin2'), 0.3)
        els += rbox([7.2, cy - H * th / 2, z1 + 0.6], [8.8, cy + H * th / 2, z1 + 0.6 + tl], F('fin2'), 0.6)
    return els


def bands(base, band, edge, at, width=0.1):
    def f(u, v):
        for c in at:
            d = abs(u - c)
            if d < width: return band
            if d < width + 0.04: return edge
        return base
    return f


K = (30, 24, 24)
p = Paint(10); O, Wt = (250, 130, 40), (252, 252, 248)
side = bands(O, Wt, K, [0.24, 0.55, 0.86], 0.08)
p.fill('side', side); p.fill('top', lambda u, v: side(v, 0)); p.fill('belly', lambda u, v: mix(O, Wt, 0.4))
p.fill('front', lambda u, v: O); p.fill('back', lambda u, v: O)
p.fill('fin', lambda u, v: K if u < 0.12 or u > 0.88 or v < 0.12 else O); p.fill('fin2', lambda u, v: K if u > 0.85 or v > 0.85 or v < 0.15 else O)
PLUSH['clownfish'] = (p, fish(10, 6.0, 6.4, dorsal=(1.6, 0.3, 0.7), tail=(2.2, 0.8)), None)

p = Paint(11); B, Y = (60, 110, 235), (255, 215, 50)
def tang(u, v):
    if 0.35 < u < 0.9 and abs(v - (0.3 + 0.25 * (u - 0.35))) < 0.12: return (25, 30, 70)
    return B
p.fill('side', tang); p.fill('top', lambda u, v: B); p.fill('belly', lambda u, v: mix(B, (255, 255, 255), 0.35))
p.fill('front', lambda u, v: B); p.fill('back', lambda u, v: Y)
p.fill('fin', lambda u, v: (25, 30, 70) if v < 0.2 else B); p.fill('fin2', lambda u, v: Y)
PLUSH['blue_tang'] = (p, fish(10, 5.4, 7.2, dorsal=(1.4, 0.25, 0.8), tail=(2.2, 0.75)), None)

p = Paint(12); Gd, Gp = (255, 160, 40), (255, 215, 130)
p.fill('side', lambda u, v: mix(Gd, Gp, max(0, v - 0.5) * 1.6)); p.fill('top', lambda u, v: Gd); p.fill('belly', lambda u, v: Gp)
p.fill('front', lambda u, v: Gd); p.fill('back', lambda u, v: Gd)
p.fill('fin', lambda u, v: mix(Gd, (255, 240, 220), v * 0.6)); p.fill('fin2', lambda u, v: mix(Gd, (255, 240, 220), u * 0.7))
PLUSH['goldfish'] = (p, fish(9, 6.4, 6.8, dorsal=(1.8, 0.3, 0.6), tail=(4.0, 1.1)), None)

p = Paint(13); S = (220, 225, 232)
side = bands(S, (40, 40, 50), S, [0.35, 0.62], 0.07)
p.fill('side', side); p.fill('top', lambda u, v: side(v, 0)); p.fill('belly', lambda u, v: S)
p.fill('front', lambda u, v: S); p.fill('back', lambda u, v: S)
p.fill('fin', lambda u, v: (40, 40, 50) if abs(u - 0.5) < 0.14 else mix(S, (255, 255, 255), 0.3)); p.fill('fin2', lambda u, v: mix(S, (255, 255, 255), 0.4))
PLUSH['angelfish'] = (p, fish(7.5, 4.8, 6.8, lift=2.4, dorsal=(3.0, 0.25, 0.65), anal=(2.4, 0.3, 0.7), tail=(1.8, 0.8)), None)

p = Paint(14); Gy, Wh = (130, 145, 165), (245, 246, 250)
p.fill('side', lambda u, v: Gy if v < 0.55 else Wh); p.fill('top', lambda u, v: Gy); p.fill('belly', lambda u, v: Wh)
p.fill('front', lambda u, v: Gy if v < 0.55 else Wh); p.fill('back', lambda u, v: Gy)
p.fill('fin', lambda u, v: Gy); p.fill('fin2', lambda u, v: Gy)
els = fish(13, 6.0, 5.4, dorsal=(2.6, 0.38, 0.56), tail=(2.0, 1.3), side_fin=2.2, eye_size=1.6)
PLUSH['shark'] = (p, els, None)

p = Paint(15); Sf = (170, 182, 198)
p.fill('side', lambda u, v: mix(Sf, (235, 238, 242), max(0, v - 0.4))); p.fill('top', lambda u, v: Sf); p.fill('belly', lambda u, v: (235, 238, 242))
p.fill('front', lambda u, v: Sf); p.fill('back', lambda u, v: Sf)
p.fill('fin', lambda u, v: mix(Sf, (110, 120, 135), v * 0.5)); p.fill('fin2', lambda u, v: mix(Sf, (130, 140, 155), (u * 7 % 1) * 0.6))
PLUSH['ocean_sunfish'] = (p, fish(7.2, 4.6, 7.8, lift=2.2, dorsal=(3.0, 0.5, 0.8), anal=(2.2, 0.5, 0.8), tail=(1.0, 0.9), side_fin=0.9), None)

# octopus: a big soft head, a face low on it, eight curly arms
p = Paint(16); Oc, Oc2 = (240, 110, 110), (255, 185, 170)
for reg in ('side', 'top', 'front', 'back'): p.fill(reg, lambda u, v: Oc2 if (int(u * 7) * 5 + int(v * 7) * 3) % 11 == 0 else Oc)
p.fill('belly', lambda u, v: Oc2); p.fill('fin', lambda u, v: Oc); p.fill('fin2', lambda u, v: Oc2)
els = rbox([4.0, 1.6, 4.0], [12.0, 10.6, 12.0], body_faces(), 1.5)
els += face(4.0, 8.0, 4.4, gap=1.8, size=1.6, mouth_y=3.5)
for k in range(8):
    a = k * math.pi / 4 + math.pi / 8
    cx, cz = 8 + math.sin(a) * 3.6, 8 + math.cos(a) * 3.6
    tx, tz = 8 + math.sin(a) * 6.0, 8 + math.cos(a) * 6.0
    els += rbox([min(cx, tx) - 0.8, 0, min(cz, tz) - 0.8], [max(cx, tx) + 0.8, 1.7, max(cz, tz) + 0.8], F('fin'), 0.5)
    els += rbox([tx - 0.65, 1.2, tz - 0.65], [tx + 0.65, 2.4, tz + 0.65], F('fin2'), 0.3)
PLUSH['octopus'] = (p, els, None)

# crab: round and wide, big soft claws, little legs, eyes up on stalks
p = Paint(17); Cr, Cr2 = (235, 90, 70), (255, 200, 170)
for reg in ('side', 'top', 'front', 'back'): p.fill(reg, lambda u, v: Cr)
p.fill('belly', lambda u, v: Cr2); p.fill('fin', lambda u, v: Cr); p.fill('fin2', lambda u, v: Cr2)
els = rbox([3.4, 0.6, 5.0], [12.6, 5.4, 11.4], body_faces(), 1.6)
for s in (-1, 1):
    def X(a, b):
        return (a, b) if s < 0 else ([16 - b[0], a[1], a[2]], [16 - a[0], b[1], b[2]])
    for a, b, reg in (([2.4, 1.6, 3.6], [4.2, 2.8, 6.2], 'fin'), ([0.8, 1.0, 1.0], [4.2, 4.4, 4.2], 'fin')):
        a, b = X(a, b); els += rbox(a, b, F(reg), 0.8)
    a, b = X([1.2, 3.4, 0.6], [2.6, 4.2, 1.6]); els += rbox(a, b, F('fin2'), 0.3)
    a, b = X([5.6, 5.0, 6.0], [6.4, 6.8, 6.8]); els.append(el(a, b, F('fin')))       # eye stalks
    a, b = X([4.9, 6.6, 5.2], [7.1, 8.8, 7.4]); els += rbox(a, b, F('pale'), 0.6)
    for z in (6.4, 8.0, 9.6):
        a, b = X([1.6, 0, z], [3.6, 1.6, z + 1.0]); els += rbox(a, b, F('fin'), 0.3)
for x0, m in ((5.25, False), (9.15, True)):
    els += eye('n', x0, 7.0, 5.2, 1.6, mirror=m)
els.append(decal('n', 4.4, 2.6, 5.8, 3.1, 5.0, 'blush', 0.06)); els.append(decal('n', 10.2, 2.6, 11.6, 3.1, 5.0, 'blush', 0.06))
els.append(decal('n', 7.4, 2.4, 8.6, 2.65, 5.0, 'mouth', 0.06))
els.append(decal('n', 7.15, 2.6, 7.4, 2.85, 5.0, 'mouth', 0.06)); els.append(decal('n', 8.6, 2.6, 8.85, 2.85, 5.0, 'mouth', 0.06))
PLUSH['crab'] = (p, els, None)

cat = json.load(open(A + 'models/block/plushie_frog.json'))
for name, (paint, els, _) in PLUSH.items():
    paint.save(name)
    for e in els:
        e['from'] = [max(-16, min(32, q)) for q in e['from']]; e['to'] = [max(-16, min(32, q)) for q in e['to']]
    model = {'parent': 'block/block', 'textures': {'t': 'skyblocks:block/plushie_' + name, 'particle': 'skyblocks:block/plushie_' + name},
             'elements': els, 'display': cat.get('display', {})}
    json.dump(model, open(A + 'models/block/plushie_' + name + '.json', 'w'), indent=1)
    json.dump({'parent': 'skyblocks:block/plushie_' + name}, open(A + 'models/item/plushie_' + name + '.json', 'w'))
    bs = '{"variants":{' + ','.join('"facing=%s":{"model":"skyblocks:block/plushie_%s"%s}' % (f, name, (',"y":%d' % y) if y else '')
                                    for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270))) + '}}'
    open(A + 'blockstates/plushie_' + name + '.json', 'w').write(json.dumps(json.loads(bs), indent=2))
    print(name, len(els))
