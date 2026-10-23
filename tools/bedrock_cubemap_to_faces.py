"""Bedrock overworld_cubemap (cubemap_0..5) -> our GL-convention faces (px nx py ny pz nz), 512 px.
Bedrock layout (checked from the seams): sides 0..3 go round clockwise seen from above (0 north, 1 east,
2 south, 3 west, each seen from inside with up at the top); 4 = top with its bottom edge on side 0's top edge;
5 = bottom with its top edge on side 0's bottom edge."""
import sys, os, json
import numpy as np
from PIL import Image

SRC, DST, NAME = sys.argv[1], sys.argv[2], sys.argv[3]
WORK = 1024
OUT = 512

faces = [np.asarray(Image.open(os.path.join(SRC, f"cubemap_{i}.png")).convert("RGB"), dtype=np.uint8) for i in range(6)]
N = faces[0].shape[0]

def cube_lookup(d):
    x, y, z = d[:, 0], d[:, 1], d[:, 2]
    a = np.abs(d)
    face = np.zeros(len(d), int); u = np.zeros(len(d)); v = np.zeros(len(d))
    xm = (a[:, 0] >= a[:, 1]) & (a[:, 0] >= a[:, 2]); ym = ~xm & (a[:, 1] >= a[:, 2]); zm = ~xm & ~ym
    def put(sel, f, uu, vv):
        face[sel] = f; u[sel] = uu[sel]; v[sel] = vv[sel]
    with np.errstate(divide='ignore', invalid='ignore'):
        put(zm & (z < 0), 0, 0.5 + 0.5 * x / a[:, 2], 0.5 - 0.5 * y / a[:, 2])   # north
        put(xm & (x > 0), 1, 0.5 + 0.5 * z / a[:, 0], 0.5 - 0.5 * y / a[:, 0])   # east
        put(zm & (z > 0), 2, 0.5 - 0.5 * x / a[:, 2], 0.5 - 0.5 * y / a[:, 2])   # south
        put(xm & (x < 0), 3, 0.5 - 0.5 * z / a[:, 0], 0.5 - 0.5 * y / a[:, 0])   # west
        put(ym & (y > 0), 4, 0.5 + 0.5 * x / a[:, 1], 0.5 - 0.5 * z / a[:, 1])   # top
        put(ym & (y < 0), 5, 0.5 + 0.5 * x / a[:, 1], 0.5 + 0.5 * z / a[:, 1])   # bottom
    return face, u, v


# With a flat-coloured bottom, the pack's maker painted the haze above the ground straight across each side face
# (the same row everywhere), which on a cube bends at every corner and gives the cube away. Below the horizon the
# sides are read with rows as equal elevation instead (columns as before, so it joins the sky above seamlessly at
# the horizon, where both readings agree).
FLAT_BOTTOM = np.asarray(faces[5]).reshape(-1, 3).std(0).max() < 1.0


def bedrock(d):
    face, u, v = cube_lookup(d)
    if FLAT_BOTTOM:
        x, y, z = d[:, 0], d[:, 1], d[:, 2]
        horiz = np.sqrt(x * x + z * z)
        elev = np.degrees(np.arctan2(y, horiz))
        az = np.degrees(np.arctan2(x, -z)) % 360.0
        side = (np.floor((az + 45.0) / 90.0).astype(int)) % 4
        off = np.radians(((az + 45.0) % 90.0) - 45.0)
        low = (elev < 0.0) & (elev >= -45.0)
        face[low] = side[low]
        u[low] = 0.5 + 0.5 * np.tan(off[low])
        v[low] = 0.5 - elev[low] / 90.0
        under = elev < -45.0
        face[under] = 5
        u[under] = 0.5
        v[under] = 0.5
    col = np.clip((u * N).astype(int), 0, N - 1); row = np.clip((v * N).astype(int), 0, N - 1)
    out = np.zeros((len(d), 3), np.uint8)
    for k in range(6):
        sel = face == k
        out[sel] = faces[k][row[sel], col[sel]]
    return out

# GL convention (tools/build_shadered_faces.py cube_lookup): face -> direction for (sc, tc) in [-1, 1]
def gl_dirs(k, sc, tc):
    one = np.ones_like(sc)
    return {
        0: np.stack([one, -tc, -sc], 1),    # px: sc=-z, tc=-y
        1: np.stack([-one, -tc, sc], 1),    # nx: sc=z, tc=-y
        2: np.stack([sc, one, tc], 1),      # py: sc=x, tc=z
        3: np.stack([sc, -one, -tc], 1),    # ny: sc=x, tc=-z
        4: np.stack([sc, -tc, one], 1),     # pz: sc=x, tc=-y
        5: np.stack([-sc, -tc, -one], 1),   # nz: sc=-x, tc=-y
    }[k]

ii, jj = np.meshgrid(np.arange(WORK), np.arange(WORK))
sc = ((ii.ravel() + 0.5) / WORK) * 2 - 1
tc = ((jj.ravel() + 0.5) / WORK) * 2 - 1
os.makedirs(DST, exist_ok=True)
names = ["px", "nx", "py", "ny", "pz", "nz"]
colours = []
for k in range(6):
    img = bedrock(gl_dirs(k, sc, tc)).reshape(WORK, WORK, 3)
    im = Image.fromarray(img).resize((OUT, OUT), Image.LANCZOS)
    im.save(os.path.join(DST, names[k] + ".png"), optimize=True)
    if k in (0, 1, 2, 4, 5):
        colours.append(np.asarray(im, float).reshape(-1, 3))
# block icon: the north side's middle
north = Image.fromarray(faces[0]).crop((N // 4, N // 4, 3 * N // 4, 3 * N // 4)).resize((16, 16), Image.LANCZOS)
north.save(os.path.join(DST, "_icon.png"))
# particle colour: the brighter, more colourful pixels' average
c = np.concatenate(colours)
sat = c.max(1) - c.min(1)
pick = c[(sat >= np.percentile(sat, 80))]
col = pick.mean(0) / 255.0
col = col / max(col.max(), 1e-3)
print(json.dumps({"name": NAME, "color": [round(float(x), 2) for x in col]}))
