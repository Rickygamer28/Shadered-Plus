"""Draws block models (the mod's JSON block models) as Minecraft would show them, from a few sides, to look at while
making them: elements with their rotations, faces with their textures (uv, rotation, tint), shaded by facing.
Run from the repository root: python3 tools/preview_models.py out.png model [model ...] [--tint RRGGBB] [--size N]
Vanilla textures (minecraft:...) aren't in the repository: they're drawn as flat colours."""
import json, math, os, sys
import numpy as np
from PIL import Image

ASSETS = "src/main/resources/assets"
FLAT = {"oak_planks": (162, 130, 78), "gray_concrete": (54, 57, 61), "black_concrete": (8, 10, 15), "white_concrete": (207, 213, 214)}


def load_model(name):
    ns, path = name.split(":") if ":" in name else ("skyblocks", name)
    if not path.startswith("block/") and not path.startswith("item/"):
        path = "block/" + path
    f = os.path.join(ASSETS, ns, "models", path + ".json")
    if not os.path.exists(f):
        return {}
    m = json.load(open(f))
    if "parent" in m:
        p = load_model(m["parent"])
        tex = dict(p.get("textures", {}))
        tex.update(m.get("textures", {}))
        out = dict(p)
        out.update(m)
        out["textures"] = tex
        if "elements" not in m and "elements" in p:
            out["elements"] = p["elements"]
        return out
    return m


_cache = {}


def texture(ref, textures):
    seen = 0
    while ref.startswith("#") and seen < 10:
        ref = textures.get(ref[1:], "missing")
        seen += 1
    if ref in _cache:
        return _cache[ref]
    ns, path = ref.split(":") if ":" in ref else ("minecraft", ref)
    f = os.path.join(ASSETS, ns, "textures", path + ".png")
    if os.path.exists(f):
        img = np.asarray(Image.open(f).convert("RGBA")).astype(np.float32)
        h, w = img.shape[:2]
        if h > w:  # (animated: its first frame)
            img = img[:w]
    else:
        c = FLAT.get(path.split("/")[-1], (255, 0, 255))
        img = np.zeros((16, 16, 4), np.float32)
        img[:, :] = c + (255,)
    _cache[ref] = img
    return img


def default_uv(face, f, t):
    return {
        "down": [f[0], 16 - t[2], t[0], 16 - f[2]], "up": [f[0], f[2], t[0], t[2]],
        "north": [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]], "south": [f[0], 16 - t[1], t[0], 16 - f[1]],
        "west": [f[2], 16 - t[1], t[2], 16 - f[1]], "east": [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]],
    }[face]


def corners(face, f, t):
    # top-left, top-right, bottom-right, bottom-left as the texture lies on the face, seen from outside
    x0, y0, z0 = f
    x1, y1, z1 = t
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[face]


SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}


def rotate(p, rot):
    if not rot:
        return np.array(p, np.float32)
    o = np.array(rot["origin"], np.float32)
    a = math.radians(rot["angle"])
    q = np.array(p, np.float32) - o
    c, s = math.cos(a), math.sin(a)
    ax = rot["axis"]
    if ax == "x":
        q = np.array([q[0], q[1] * c - q[2] * s, q[1] * s + q[2] * c])
    elif ax == "y":
        q = np.array([q[0] * c + q[2] * s, q[1], -q[0] * s + q[2] * c])
    else:
        q = np.array([q[0] * c - q[1] * s, q[0] * s + q[1] * c, q[2]])
    if rot.get("rescale"):
        k = 1 / math.cos(a) if abs(math.cos(a)) > 1e-3 else 1
        scale = np.array([1, 1, 1], np.float32)
        for i, n in enumerate("xyz"):
            if n != ax:
                scale[i] = k
        q = q * scale
    return q + o


def quads(model, tints):
    out = []
    textures = model.get("textures", {})
    for e in model.get("elements", []):
        f, t = e["from"], e["to"]
        for face, d in e["faces"].items():
            img = texture(d["texture"], textures)
            uv = d.get("uv", default_uv(face, f, t))
            cs = [rotate(p, e.get("rotation")) for p in corners(face, f, t)]
            uvs = [(uv[0], uv[1]), (uv[2], uv[1]), (uv[2], uv[3]), (uv[0], uv[3])]
            r = (d.get("rotation", 0) // 90) % 4
            uvs = uvs[-r:] + uvs[:-r] if r else uvs  # (the texture turned on the face)
            tint = tints.get(d.get("tintindex", -1), (255, 255, 255)) if "tintindex" in d else (255, 255, 255)
            shade = SHADE[face] if e.get("shade", True) else 1.0
            if e.get("rotation"):  # (shade by where the face points once turned)
                n = rotate(np.array(cs[0]) + np.cross(np.array(cs[1]) - cs[0], np.array(cs[3]) - cs[0]), None)
            out.append((cs, uvs, img, np.array(tint, np.float32) / 255 * shade))
    return out


def render(model, size, yaw, pitch, tints, zoom=1.0):
    img = np.zeros((size, size, 3), np.float32)
    img[:] = (46, 50, 58)
    depth = np.full((size, size), -1e9, np.float32)
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    scale = size / 30.0 * zoom

    def proj(p):
        x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return np.array([size / 2 + x * scale, size / 2 - y * scale, z])

    ys, xs = np.mgrid[0:size, 0:size].astype(np.float32) + 0.5
    for cs, uvs, tex, mul in quads(model, tints):
        P = [proj(p) for p in cs]
        th, tw = tex.shape[:2]
        for tri in ((0, 1, 2), (0, 2, 3)):
            a, b, c = (P[i] for i in tri)
            ua, ub, uc = (uvs[i] for i in tri)
            x0, x1 = int(max(0, min(a[0], b[0], c[0]))), int(min(size - 1, max(a[0], b[0], c[0])) + 1)
            y0, y1 = int(max(0, min(a[1], b[1], c[1]))), int(min(size - 1, max(a[1], b[1], c[1])) + 1)
            if x1 <= x0 or y1 <= y0:
                continue
            det = (b[1] - c[1]) * (a[0] - c[0]) + (c[0] - b[0]) * (a[1] - c[1])
            if abs(det) < 1e-9:
                continue
            X, Y = xs[y0:y1, x0:x1], ys[y0:y1, x0:x1]
            w0 = ((b[1] - c[1]) * (X - c[0]) + (c[0] - b[0]) * (Y - c[1])) / det
            w1 = ((c[1] - a[1]) * (X - c[0]) + (a[0] - c[0]) * (Y - c[1])) / det
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-4) & (w1 >= -1e-4) & (w2 >= -1e-4)
            z = w0 * a[2] + w1 * b[2] + w2 * c[2]
            u = (w0 * ua[0] + w1 * ub[0] + w2 * uc[0]) / 16 * tw
            v = (w0 * ua[1] + w1 * ub[1] + w2 * uc[1]) / 16 * th
            ui = np.clip(np.floor(u).astype(int), 0, tw - 1)
            vi = np.clip(np.floor(v).astype(int), 0, th - 1)
            col = tex[vi, ui]
            ok = inside & (col[..., 3] > 127) & (z > depth[y0:y1, x0:x1])
            sub = img[y0:y1, x0:x1]
            sub[ok] = col[..., :3][ok] * mul
            depth[y0:y1, x0:x1][ok] = z[ok]
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


if __name__ == "__main__":
    args = sys.argv[1:]
    tints = {0: (255, 255, 255), 1: (255, 255, 255)}
    size = 256
    if "--tint" in args:
        i = args.index("--tint")
        h = args[i + 1]
        c = tuple(int(h[j:j + 2], 16) for j in (0, 2, 4))
        tints = {0: c, 1: tuple(v + (255 - v) * 3 // 5 for v in c)}  # (as the cat things' tint: 1 is paler)
        del args[i:i + 2]
    if "--size" in args:
        i = args.index("--size")
        size = int(args[i + 1])
        del args[i:i + 2]
    out, names = args[0], args[1:]
    views = [(-135, 30), (45, 30), (-135, 70)]
    sheet = Image.new("RGB", (size * len(views), size * len(names)))
    for r, n in enumerate(names):
        m = load_model(n)
        for c, (yaw, pitch) in enumerate(views):
            sheet.paste(render(m, size, yaw, pitch, tints), (c * size, r * size))
    sheet.save(out)
