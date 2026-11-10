"""Fish plushies from the aquariums' own fish models (src/.../client/AquaModels.java): each one's boxes, turned into a
block model on its own texture (textures/entity/aqua/, stitched onto the blocks atlas by atlases/blocks.json), grown to
plushie size and puffed up a little. Run from the repo root."""
import json, math, re, sys

SRC = 'src/main/java/com/example/skyblocks/client/AquaModels.java'
ASSETS = 'src/main/resources/assets/skyblocks/'
FISH = sys.argv[1:] or ['clownfish', 'blue_tang', 'goldfish', 'angelfish', 'shark', 'octopus', 'crab', 'ocean_sunfish']

src = open(SRC).read()

def camel(n):
    a = n.split('_')
    return a[0] + ''.join(x.title() for x in a[1:])

F = r'(-?[\d.]+)F'
def parse(name):
    m = re.search(r'static ModelPart ' + camel(name) + r'\(\) \{(.*?)\n    \}', src, re.S)
    body = m.group(1)
    tw, th = map(int, re.search(r'LayerDefinition\.create\(mesh, (\d+), (\d+)\)', body).groups())
    parts = {'': ((0, 0, 0), (0, 0, 0), None)}
    boxes = []
    for pm in re.finditer(r'part\(parts, "(\w+)", "(\w*)", CubeListBuilder\.create\(\)(.*?), PartPose\.offsetAndRotation\(' + ', '.join([F] * 6) + r'\)\);', body):
        pname, parent, cubes = pm.group(1), pm.group(2), pm.group(3)
        off = tuple(float(pm.group(i)) for i in range(4, 7))
        rot = tuple(float(pm.group(i)) for i in range(7, 10))
        po, pr, _ = parts[parent]
        # (the parents here aren't turned, only moved: add their offsets up)
        origin = (po[0] + off[0], po[1] + off[1], po[2] + off[2])
        parts[pname] = (origin, rot, parent)
        for cm in re.finditer(r'texOffs\((\d+), (\d+)\)\.addBox\(' + ', '.join([F] * 6) + r'(?:, new CubeDeformation\(' + ', '.join([F] * 3) + r'\))?\)', cubes):
            u, v = int(cm.group(1)), int(cm.group(2))
            x, y, z, w, h, d = (float(cm.group(i)) for i in range(3, 9))
            dx = float(cm.group(9) or 0); dy = float(cm.group(10) or 0); dz = float(cm.group(11) or 0)
            boxes.append(dict(u=u, v=v, w=w, h=h, d=d, origin=origin, rot=rot,
                              lo=(x - dx, y - dy, z - dz), hi=(x + w + dx, y + h + dy, z + d + dz)))
    return tw, th, boxes

def snap(deg):
    k = max(-2, min(2, round(deg / 22.5)))
    return k * 22.5

def build(name):
    tw, th, boxes = parse(name)
    els = []
    # into world-like space (y up, x mirrored, as the renderer shows them)
    for b in boxes:
        o = b['origin']
        x0, x1 = -(o[0] + b['hi'][0]), -(o[0] + b['lo'][0])
        y0, y1 = -(o[1] + b['hi'][1]), -(o[1] + b['lo'][1])
        z0, z1 = o[2] + b['lo'][2], o[2] + b['hi'][2]
        b['box'] = [x0, y0, z0, x1, y1, z1]
        b['pivot'] = (-o[0], -o[1], o[2])
    lo = [min(b['box'][i] for b in boxes) for i in range(3)]
    hi = [max(b['box'][i + 3] for b in boxes) for i in range(3)]
    size = [hi[i] - lo[i] for i in range(3)]
    s = min(13.0 / size[2], 14.0 / max(size[0], 1e-3), 12.0 / size[1])
    s = min(s, 2.2)
    cx, cz = (lo[0] + hi[0]) / 2, (lo[2] + hi[2]) / 2
    xs = min(2.6, max(1.0, 4.5 / (size[0] * s)))  # (plushies are chubbier than fish)
    xs = min(xs, 15.0 / (size[0] * s))
    def P(p):
        return [round(8 + (p[0] - cx) * s * xs, 4), round((p[1] - lo[1]) * s, 4), round(8 + (p[2] - cz) * s, 4)]
    su, sv = 16.0 / tw, 16.0 / th
    for b in boxes:
        a, c = P(b['box'][:3]), P(b['box'][3:])
        # puffed up a little; thin fins at least half a pixel thick
        for i in range(3):
            mid = (a[i] + c[i]) / 2
            half = max((c[i] - a[i]) / 2 + 0.12, 0.3)
            a[i], c[i] = round(mid - half, 4), round(mid + half, 4)
        a = [max(-16, min(32, q)) for q in a]; c = [max(-16, min(32, q)) for q in c]
        u, v, w, h, d = b['u'], b['v'], b['w'], b['h'], b['d']
        def uv(u0, v0, u1, v1):
            return [round(u0 * su, 4), round(v0 * sv, 4), round(u1 * su, 4), round(v1 * sv, 4)]
        faces = {
            'east': uv(u, v + d, u + d, v + d + h),
            'north': uv(u + d, v + d, u + d + w, v + d + h),
            'west': uv(u + d + w, v + d, u + 2 * d + w, v + d + h),
            'south': uv(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
            'up': uv(u + d + w, v + d, u + d, v),
            'down': uv(u + d + 2 * w, v, u + d + w, v + d),
        }
        e = {'from': a, 'to': c, 'faces': {k: {'uv': q, 'texture': '#fish'} for k, q in faces.items()}}
        rx, ry, rz = b['rot']
        angles = {'x': -math.degrees(rx), 'y': -math.degrees(ry), 'z': math.degrees(rz)}
        axis = max(angles, key=lambda k: abs(angles[k]))
        ang = snap(angles[axis])
        if ang != 0:
            e['rotation'] = {'angle': ang, 'axis': axis, 'origin': P(b['pivot'])}
        els.append(e)
    model = {
        'parent': 'block/block',
        'textures': {'fish': 'skyblocks:entity/aqua/' + name, 'particle': 'skyblocks:entity/aqua/' + name},
        'elements': els,
        'display': {
            'gui': {'rotation': [30, 225, 0], 'translation': [0, 1, 0], 'scale': [0.75, 0.75, 0.75]},
            'ground': {'rotation': [0, 0, 0], 'translation': [0, 3, 0], 'scale': [0.5, 0.5, 0.5]},
            'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [0.9, 0.9, 0.9]},
            'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 0], 'scale': [0.9, 0.9, 0.9]},
            'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.5, 0.5, 0.5]},
            'firstperson_righthand': {'rotation': [0, 45, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
            'firstperson_lefthand': {'rotation': [0, 225, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
        },
    }
    n = 'plushie_' + name
    json.dump(model, open(ASSETS + 'models/block/' + n + '.json', 'w'), indent=1)
    json.dump({'parent': 'skyblocks:block/' + n}, open(ASSETS + 'models/item/' + n + '.json', 'w'), indent=2)
    variants = {}
    for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        variants['facing=' + f] = {'model': 'skyblocks:block/' + n, **({'y': y} if y else {})}
    json.dump({'variants': variants}, open(ASSETS + 'blockstates/' + n + '.json', 'w'), indent=2)
    print(name, len(els), 'boxes, size', [round(size[0] * s * xs, 1), round(size[1] * s, 1), round(size[2] * s, 1)])

for f in FISH:
    build(f)
