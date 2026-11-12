"""Splits the OIIA cat (models/complex/oiia_cat.glb) into pieces that move on their own, for the companion's walk:
the body, the head, each leg (turning at its hip) and the tail (turning where it leaves the body).
Writes oiia_cat_<part>.glb next to it and prints each part's pivot (model units) for client/OiiaCatModel.
Run from the repository root: python3 tools/models/oiia_cat/split_walk.py [--preview out.png]"""
import json, struct, sys
import numpy as np

SRC = "src/main/resources/assets/skyblocks/models/complex/oiia_cat.glb"
OUT = "src/main/resources/assets/skyblocks/models/complex/oiia_cat_%s.glb"

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
I = acc(prim["indices"]).astype(np.uint32).reshape(-1, 3)
img_bv = gj["bufferViews"][gj["images"][0]["bufferView"]]
PNG = BIN[img_bv.get("byteOffset", 0):img_bv.get("byteOffset", 0) + img_bv["byteLength"]]

# the legs: short stumps under a round body; each a column round its paw, up to where it meets the belly
LEGS = {
    "leg_fl": (8.1, 16.9, 6.2), "leg_fr": (-5.6, 17.4, 6.2),   # front: x, z of the paw, top (y)
    "leg_bl": (6.0, -13.6, 7.6), "leg_br": (-7.2, -15.3, 7.6),  # back
}
RADIUS = 3.9

# the head: in front of the neck - a plane leaning forward from the back of the neck (z 5, y 28.6) down to under the
# chin (z 21, y 16.3), where the collar goes (client/OiiaCatCollar), so the collar covers the join
NECK_C = np.array([13.0, 22.45])      # (z, y)
NECK_N = np.array([0.609, 0.793])     # (z, y): forward and up

def part_of(p):
    x, y, z = p
    for name, (cx, cz, top) in LEGS.items():
        if y < top and (x - cx) ** 2 + (z - cz) ** 2 < RADIUS ** 2:
            return name
    if z < -22.5 and y > 8.0:
        return "tail"
    if (np.array([z, y]) - NECK_C) @ NECK_N > 0:
        return "head"
    return "body"

vpart = np.array([part_of(p) for p in P])
# each triangle goes with the part most of its corners are in (ties: the body, so legs don't pull bits off it)
tri_part = []
for t in I:
    ps = [vpart[k] for k in t]
    best = max(set(ps), key=ps.count)
    if ps.count(best) == 1:
        best = "body"
    tri_part.append(best)
tri_part = np.array(tri_part)

def write(name, tris, extra=None):
    used = np.unique(tris)
    remap = {int(k): i for i, k in enumerate(used)}
    idx = np.array([[remap[int(k)] for k in t] for t in tris], dtype=np.uint32)
    pos, nor, uv = P[used], N[used], T[used]
    if extra is not None:
        # (more triangles with vertices of their own: positions, normals, uvs, and their triangles indexing them)
        ep, en, eu, et = extra
        idx = np.concatenate([idx, (et + len(pos)).astype(np.uint32)])
        pos, nor, uv = np.concatenate([pos, ep]).astype(np.float32), np.concatenate([nor, en]).astype(np.float32), np.concatenate([uv, eu]).astype(np.float32)
    blobs = [pos.tobytes(), nor.tobytes(), uv.tobytes(), idx.tobytes(), PNG]
    views, data, off = [], b"", 0
    for i, blob in enumerate(blobs):
        pad = (-len(data)) % 4
        data += b"\0" * pad
        v = {"buffer": 0, "byteOffset": len(data), "byteLength": len(blob)}
        if i < 3:
            v["target"] = 34962
        elif i == 3:
            v["target"] = 34963
        views.append(v)
        data += blob
    data += b"\0" * ((-len(data)) % 4)
    j = {
        "asset": {"version": "2.0", "generator": "Shadered+ tools (oiia_cat split for walking)"},
        "scene": 0, "scenes": [{"nodes": [0]}], "nodes": [{"name": "OiiaCat_" + name, "mesh": 0}],
        "meshes": [{"name": "OiiaCat_" + name, "primitives": [{"attributes": {"POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2}, "indices": 3,
                                                               "material": 0, "mode": 4}]}],
        "accessors": [
            {"bufferView": 0, "componentType": 5126, "count": len(pos), "type": "VEC3", "min": pos.min(0).tolist(), "max": pos.max(0).tolist()},
            {"bufferView": 1, "componentType": 5126, "count": len(nor), "type": "VEC3"},
            {"bufferView": 2, "componentType": 5126, "count": len(uv), "type": "VEC2"},
            {"bufferView": 3, "componentType": 5125, "count": idx.size, "type": "SCALAR"},
        ],
        "bufferViews": views,
        "buffers": [{"byteLength": len(data)}],
        "materials": gj["materials"], "textures": gj["textures"], "samplers": gj["samplers"],
        "images": [{"bufferView": 4, "mimeType": "image/png", "name": gj["images"][0].get("name", "tex")}],
    }
    js = json.dumps(j, separators=(",", ":")).encode()
    js += b" " * ((-len(js)) % 4)
    out = struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(data))
    out += struct.pack("<II", len(js), 0x4E4F534A) + js + struct.pack("<II", len(data), 0x004E4942) + data
    open(OUT % name, "wb").write(out)
    return len(used), len(tris)

def sleeve(inside, centre, shrink=0.965):
    """A piece that turns takes a sleeve with it: the body's fur just past its joint (where `inside` says), a copy
    drawn a little smaller than the body's own so it stays hidden inside while the piece is at rest, and fills the
    join when it turns."""
    pick = [k for k, t in enumerate(I) if tri_part[k] == "body" and all(inside(P[v]) for v in t)]
    tris = I[pick]
    used = np.unique(tris)
    remap = {int(k): i for i, k in enumerate(used)}
    et = np.array([[remap[int(k)] for k in t] for t in tris], dtype=np.int64).reshape(-1, 3)
    c = np.array(centre)
    f = np.array([shrink(q) for q in P[used]])[:, None] if callable(shrink) else shrink
    ep = c + (P[used] - c) * f
    return ep, N[used], T[used], et

def neck_depth(p):
    return -((np.array([p[2], p[1]]) - NECK_C) @ NECK_N)

def neck_plug():
    # (the body's fur up to 3 units behind the neck line, narrowing the further back it goes, so as the head turns the
    # far end of it stays inside the body rather than poking out of the back of the neck)
    return sleeve(lambda p: -0.5 <= neck_depth(p) < 3.0, (1.6, 22.45, 13.0), lambda p: 0.96 - 0.07 * max(0.0, neck_depth(p)))

def hip(name):
    # (the body's fur round the top of the leg, a little wider than it and up to 4 units above it)
    cx, cz, top = LEGS[name]
    return sleeve(lambda p: top - 0.5 <= p[1] < top + 4.0 and (p[0] - cx) ** 2 + (p[2] - cz) ** 2 < (RADIUS * 1.25) ** 2, (cx, top, cz), 0.95)

def tail_root():
    # (the body's fur round where the tail leaves it)
    return sleeve(lambda p: -22.5 <= p[2] < -18.5 and p[1] > 13.0, (0.0, 22.12, -22.5), 0.95)

SLEEVES = {"head": neck_plug, "tail": tail_root, "leg_fl": lambda: hip("leg_fl"), "leg_fr": lambda: hip("leg_fr"),
           "leg_bl": lambda: hip("leg_bl"), "leg_br": lambda: hip("leg_br")}

for name in ["body", "tail", "head"] + list(LEGS):
    tris = I[tri_part == name]
    extra = SLEEVES[name]() if name in SLEEVES else None
    if extra is not None and len(extra[3]) == 0:
        extra = None
    nv, nt = write(name, tris, extra)
    sel = P[np.unique(tris)]
    print("%-7s %5d verts %5d tris  y %.1f..%.1f" % (name, nv, nt, sel[:, 1].min(), sel[:, 1].max()))
for name, (cx, cz, top) in LEGS.items():
    print("pivot %s: %.2f %.2f %.2f" % (name, cx, top, cz))
tail = P[vpart == "tail"]
print("pivot tail: 0 %.2f -22.5" % tail[tail[:, 2] > -24][:, 1].mean())
print("pivot head (the neck's middle): 1.6 22.45 13.0")

if "--preview" in sys.argv:
    from PIL import Image, ImageDraw
    col = {"head": (240, 200, 60), "body": (160, 160, 160), "tail": (230, 120, 40), "leg_fl": (220, 40, 40), "leg_fr": (40, 160, 40), "leg_bl": (40, 80, 230), "leg_br": (200, 40, 200)}
    S, mn, mx = 8, P.min(0), P.max(0)
    im = Image.new("RGB", (int((mx[2] - mn[2]) * S) * 2 + 60, int((mx[1] - mn[1]) * S) + 20), "white")
    d = ImageDraw.Draw(im)
    H = im.height
    for side in (0, 1):
        order = np.argsort(P[I].mean(1)[:, 0] * (1 if side else -1))
        for k in order:
            t = I[k]
            pts = [((P[v][2] - mn[2]) * S + 10 + side * ((mx[2] - mn[2]) * S + 40), H - ((P[v][1] - mn[1]) * S + 10)) for v in t]
            d.polygon(pts, fill=col[tri_part[k]])
    im.save(sys.argv[sys.argv.index("--preview") + 1])
