"""Rigs the OIIA cat (models/complex/oiia_cat.glb) with a skeleton, for the companion's animations: one mesh, every
vertex tied to its bones by weights that blend smoothly across each joint (so the fur bends there instead of the
pieces parting). Bones and joints: BONES below (client/OiiaCatRig has the same).
Writes models/complex/oiia_cat_rigged.glb. Run from the repository root: python3 tools/models/oiia_cat/rig.py [--preview out.png]"""
import json, struct, sys
import numpy as np

SRC = "src/main/resources/assets/skyblocks/models/complex/oiia_cat.glb"
OUT = "src/main/resources/assets/skyblocks/models/complex/oiia_cat_rigged.glb"

b = open(SRC, "rb").read()
jl = struct.unpack("<I", b[12:16])[0]
gj = json.loads(b[20:20 + jl])
bin0 = 20 + jl + 8
binlen = struct.unpack("<I", b[20 + jl:24 + jl])[0]
BIN = b[bin0:bin0 + binlen]


def acc(i):
    a = gj["accessors"][i]
    bv = gj["bufferViews"][a["bufferView"]]
    n = {"SCALAR": 1, "VEC2": 2, "VEC3": 3}[a["type"]]
    dt = {5126: np.float32, 5125: np.uint32, 5123: np.uint16}[a["componentType"]]
    arr = np.frombuffer(BIN, dtype=dt, count=a["count"] * n, offset=bv.get("byteOffset", 0) + a.get("byteOffset", 0))
    return arr.reshape(-1, n) if n > 1 else arr


prim = gj["meshes"][0]["primitives"][0]
P = acc(prim["attributes"]["POSITION"]).astype(np.float32)
N = acc(prim["attributes"]["NORMAL"]).astype(np.float32)
T = acc(prim["attributes"]["TEXCOORD_0"]).astype(np.float32)
I = acc(prim["indices"]).astype(np.uint32)
img_bv = gj["bufferViews"][gj["images"][0]["bufferView"]]
PNG = BIN[img_bv.get("byteOffset", 0):img_bv.get("byteOffset", 0) + img_bv["byteLength"]]

# the bones: name, parent, joint (model units). The body is the chest (the root); the hips carry the back legs and
# the tail, so the back can arch and the rump sit down; each leg has its paw below the knee; the tail is three; the
# ears turn on their own.
BONES = [
    ("body", None, (0.0, 0.0, 0.0)),
    ("hips", "body", (0.0, 15.0, -2.0)),
    ("head", "body", (1.6, 22.45, 13.0)),
    ("ear_l", "head", (4.8, 33.0, 19.5)),
    ("ear_r", "head", (-3.4, 33.0, 19.5)),
    ("leg_fl", "body", (8.10, 6.20, 16.90)),
    ("leg_fr", "body", (-5.60, 6.20, 17.40)),
    ("leg_bl", "hips", (6.00, 7.60, -13.60)),
    ("leg_br", "hips", (-7.20, 7.60, -15.30)),
    ("paw_fl", "leg_fl", (8.10, 2.6, 16.90)),
    ("paw_fr", "leg_fr", (-5.60, 2.6, 17.40)),
    ("paw_bl", "leg_bl", (6.00, 3.0, -13.60)),
    ("paw_br", "leg_br", (-7.20, 3.0, -15.30)),
    ("tail", "hips", (0.0, 22.12, -22.5)),
    ("tail_mid", "tail", (0.0, 20.6, -29.5)),
    ("tail_tip", "tail_mid", (0.0, 18.0, -36.5)),
]
NAMES = [b[0] for b in BONES]
LEGS = {"leg_fl": (8.1, 16.9, 6.2), "leg_fr": (-5.6, 17.4, 6.2), "leg_bl": (6.0, -13.6, 7.6), "leg_br": (-7.2, -15.3, 7.6)}
KNEES = {"leg_fl": 2.6, "leg_fr": 2.6, "leg_bl": 3.0, "leg_br": 3.0}
RADIUS = 3.9
NECK_C = np.array([13.0, 22.45])   # (z, y)
NECK_N = np.array([0.609, 0.793])  # (z, y): forward and up


def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


x, y, z = P[:, 0], P[:, 1], P[:, 2]
W = np.zeros((len(P), len(BONES)), dtype=np.float32)
col = lambda n: NAMES.index(n)
# the head: in front of the neck line, blending over 5 units across it; its ears, up top
head = smooth(-2.5, 2.5, (np.stack([z, y], 1) - NECK_C) @ NECK_N)
ears = smooth(32.6, 34.6, y) * smooth(13.0, 15.5, z) * head
W[:, col("ear_l")] = ears * smooth(0.6, 1.8, x)
W[:, col("ear_r")] = ears * (1.0 - smooth(0.6, 1.8, x))
W[:, col("head")] = head - ears
# each leg: below its hip, blending over the hip, close round the leg; its paw below the knee
legs_all = np.zeros(len(P), dtype=np.float32)
for name, (cx, cz, top) in LEGS.items():
    r = np.sqrt((x - cx) ** 2 + (z - cz) ** 2)
    leg = (1.0 - smooth(top - 2.5, top + 2.5, y)) * (1.0 - smooth(RADIUS * 0.9, RADIUS * 1.35, r))
    paw = leg * (1.0 - smooth(KNEES[name] - 1.2, KNEES[name] + 1.2, y))
    W[:, col("paw_" + name[4:])] = paw
    W[:, col(name)] = leg - paw
    legs_all += leg
# the tail: back from where it leaves the body, in three
tailish = smooth(9.0, 12.0, y)
t_all = smooth(-19.5, -24.0, z) * tailish
t_mid = smooth(-27.5, -31.5, z) * tailish
t_tip = smooth(-34.5, -38.5, z) * tailish
W[:, col("tail_tip")] = t_tip
W[:, col("tail_mid")] = t_mid - t_tip
W[:, col("tail")] = t_all - t_mid
# the hips: the back half of the body (not its legs or tail, which are their own), blending across the middle
rest = np.clip(1.0 - W.sum(1), 0.0, 1.0)
hips = smooth(3.0, -7.0, z) * (1.0 - head)
W[:, col("hips")] = rest * hips
# the body (the chest): the rest
W[:, col("body")] = np.clip(1.0 - W.sum(1), 0.0, 1.0)
# four at most a vertex, adding up to one
J4 = np.zeros((len(P), 4), dtype=np.uint16)
W4 = np.zeros((len(P), 4), dtype=np.float32)
order = np.argsort(-W, axis=1)[:, :4]
for k in range(4):
    J4[:, k] = order[:, k]
    W4[:, k] = W[np.arange(len(P)), order[:, k]]
W4 /= np.maximum(W4.sum(1, keepdims=True), 1e-6)
J4[W4 == 0] = 0

# the joints as nodes (each placed from its parent), and their inverse bind matrices (just the joint, undone)
nodes = [{"name": "OiiaCat", "mesh": 0, "skin": 0}]
index = {}
for i, (name, parent, j) in enumerate(BONES):
    index[name] = i + 1
for i, (name, parent, j) in enumerate(BONES):
    pj = np.array(BONES[NAMES.index(parent)][2]) if parent else np.zeros(3)
    node = {"name": name, "translation": (np.array(j) - pj).tolist()}
    kids = [index[c] for c, p, _ in BONES if p == name]
    if kids:
        node["children"] = kids
    nodes.append(node)
ibm = np.zeros((len(BONES), 16), dtype=np.float32)
for i, (name, parent, j) in enumerate(BONES):
    m = np.eye(4, dtype=np.float32)
    m[:3, 3] = -np.array(j)
    ibm[i] = m.T.reshape(-1)  # (column major)

blobs = [P.tobytes(), N.tobytes(), T.tobytes(), J4.tobytes(), W4.tobytes(), I.astype(np.uint32).tobytes(), ibm.tobytes(), PNG]
views, data = [], b""
for i, blob in enumerate(blobs):
    data += b"\0" * ((-len(data)) % 4)
    v = {"buffer": 0, "byteOffset": len(data), "byteLength": len(blob)}
    if i < 5:
        v["target"] = 34962
    elif i == 5:
        v["target"] = 34963
    views.append(v)
    data += blob
data += b"\0" * ((-len(data)) % 4)
j = {
    "asset": {"version": "2.0", "generator": "Shadered+ tools (oiia_cat rigged)"},
    "scene": 0, "scenes": [{"nodes": [0, 1]}], "nodes": nodes,
    "skins": [{"joints": list(range(1, len(BONES) + 1)), "inverseBindMatrices": 6, "skeleton": 1}],
    "meshes": [{"name": "OiiaCat", "primitives": [{"attributes": {"POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2, "JOINTS_0": 3, "WEIGHTS_0": 4},
                                                   "indices": 5, "material": 0, "mode": 4}]}],
    "accessors": [
        {"bufferView": 0, "componentType": 5126, "count": len(P), "type": "VEC3", "min": P.min(0).tolist(), "max": P.max(0).tolist()},
        {"bufferView": 1, "componentType": 5126, "count": len(N), "type": "VEC3"},
        {"bufferView": 2, "componentType": 5126, "count": len(T), "type": "VEC2"},
        {"bufferView": 3, "componentType": 5123, "count": len(J4), "type": "VEC4"},
        {"bufferView": 4, "componentType": 5126, "count": len(W4), "type": "VEC4"},
        {"bufferView": 5, "componentType": 5125, "count": int(I.size), "type": "SCALAR"},
        {"bufferView": 6, "componentType": 5126, "count": len(BONES), "type": "MAT4"},
    ],
    "bufferViews": views,
    "buffers": [{"byteLength": len(data)}],
    "materials": gj["materials"], "textures": gj["textures"], "samplers": gj["samplers"],
    "images": [{"bufferView": 7, "mimeType": "image/png", "name": gj["images"][0].get("name", "tex")}],
}
js = json.dumps(j, separators=(",", ":")).encode()
js += b" " * ((-len(js)) % 4)
out = struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(data))
out += struct.pack("<II", len(js), 0x4E4F534A) + js + struct.pack("<II", len(data), 0x004E4942) + data
open(OUT, "wb").write(out)
for k, name in enumerate(NAMES):
    print("%-9s %5d vertices mostly its own, %5d touched" % (name, int((W[:, k] > 0.5).sum()), int((W[:, k] > 0.01).sum())))

if "--preview" in sys.argv:
    # side view, each vertex coloured by its bones (a joint's blend shows as a fade), and the joints
    from PIL import Image, ImageDraw
    rnd = np.random.RandomState(3)
    cols = rnd.randint(40, 240, size=(len(BONES), 3)).astype(float)
    cols[0] = (150, 150, 150)
    C = W @ cols
    S, mn, mx = 8, P.min(0), P.max(0)
    im = Image.new("RGB", (int((mx[2] - mn[2]) * S) + 20, int((mx[1] - mn[1]) * S) + 20), "white")
    d = ImageDraw.Draw(im)
    H = im.height
    for k in np.argsort(P[:, 0]):
        px, py = (P[k, 2] - mn[2]) * S + 10, H - ((P[k, 1] - mn[1]) * S + 10)
        d.point((px, py), fill=tuple(int(c) for c in C[k]))
    for name, parent, jp in BONES:
        px, py = (jp[2] - mn[2]) * S + 10, H - ((jp[1] - mn[1]) * S + 10)
        d.ellipse((px - 4, py - 4, px + 4, py + 4), outline=(0, 0, 0))
    im.save(sys.argv[sys.argv.index("--preview") + 1])
