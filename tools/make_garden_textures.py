"""Textures for the garden and keepsakes: zen sand (plain and every raked pattern), the hot spring's animated water,
the bonsai pot, the rake / camera / photo items, and a plain white texture (the map table's land is vertex-coloured).
Run from the repository root: python3 tools/make_garden_textures.py"""
import math, os, random
from PIL import Image

OUT = "src/main/resources/assets/skyblocks/textures"
rnd = random.Random(7)

def save(img, path):
    full = os.path.join(OUT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)

def clamp(v):
    return max(0, min(255, int(round(v))))

# ---------- zen sand ----------
SAND = (226, 214, 186)
N = 32
grain = [[rnd.uniform(-1, 1) for _ in range(N)] for _ in range(N)]

def sand_pixel(x, y, groove):
    """groove: -1 deep in a furrow .. +1 on a ridge's crest, 0 flat."""
    g = grain[y % N][x % N]
    b = 1.0 + g * 0.045 + groove * 0.15
    warm = groove * 3
    return (clamp(SAND[0] * b + warm), clamp(SAND[1] * b + warm * 0.6), clamp(SAND[2] * b - warm * 0.4), 255)

def pattern_img(fn, size=N):
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            img.putpixel((x, y), sand_pixel(x, y, fn(x + 0.5, y + 0.5)))
    return img

PERIOD = 6.4  # furrows across a block: 5
def furrow(d):
    # a raked line: a soft dark furrow, a bright ridge on the side the light comes from
    p = (d / PERIOD) % 1.0
    return math.sin(p * 2 * math.pi) * 0.9

save(pattern_img(lambda x, y: 0.0), "block/zen_sand_smooth.png")
save(pattern_img(lambda x, y: furrow(y)), "block/zen_sand_lines_x.png")   # lines running east-west
save(pattern_img(lambda x, y: furrow(x)), "block/zen_sand_lines_z.png")   # lines running north-south
save(pattern_img(lambda x, y: furrow(y + 2.2 * math.sin(x / N * 2 * math.pi))), "block/zen_sand_waves_x.png")
save(pattern_img(lambda x, y: furrow(x + 2.2 * math.sin(y / N * 2 * math.pi))), "block/zen_sand_waves_z.png")
# rings: one 3x3 picture round the middle block, cut into nine
big = 3 * N
def ring(x, y):
    d = math.hypot(x - big / 2, y - big / 2)
    if d < N * 0.42:
        return 0.0  # (the stone sits here)
    return furrow(d)
ring_img = pattern_img(ring, big)
names = {(0, 0): "nw", (1, 0): "n", (2, 0): "ne", (0, 1): "w", (1, 1): "", (2, 1): "e", (0, 2): "sw", (1, 2): "s", (2, 2): "se"}
for (cx, cy), nm in names.items():
    tile = ring_img.crop((cx * N, cy * N, cx * N + N, cy * N + N))
    save(tile, "block/zen_sand_ring%s.png" % ("_" + nm if nm else ""))
# the side: sand in layers
side = Image.new("RGBA", (16, 16))
for y in range(16):
    for x in range(16):
        band = 0.04 * math.sin(y * 1.3 + math.sin(x * 0.7))
        side.putpixel((x, y), sand_pixel(x, y, -0.25 + band * 5 - (0.15 if y > 12 else 0)))
save(side, "block/zen_sand_side.png")

# ---------- the hot spring's water (animated) ----------
W, FRAMES = 32, 32
frames = Image.new("RGBA", (W, W * FRAMES))
bubbles = [(rnd.uniform(0, W), rnd.uniform(0, W), rnd.uniform(0, 1), rnd.uniform(1.6, 3.2)) for _ in range(5)]
for f in range(FRAMES):
    t = f / FRAMES
    for y in range(W):
        for x in range(W):
            u, v = x / W * 2 * math.pi, y / W * 2 * math.pi
            # (all of it periodic in x, y and time, so it tiles and loops)
            w = (math.sin(u + 2 * math.pi * t) + math.sin(v * 2 - 2 * math.pi * t + u)
                 + 0.6 * math.sin(2 * u - v + 4 * math.pi * t) + 0.5 * math.cos(u * 3 + v + 2 * math.pi * t)) / 3.1
            hl = max(0.0, w - 0.35) * 1.6  # (light catching the ripples)
            r = 120 + 34 * w + 110 * hl
            g = 196 + 18 * w + 50 * hl
            b = 206 + 12 * w + 40 * hl
            for (bx, by, ph, rad) in bubbles:
                age = (t + ph) % 1.0
                if age < 0.45:
                    rr = rad * (0.4 + age * 2.2)
                    d = math.hypot(((x - bx + W / 2) % W) - W / 2, ((y - by + W / 2) % W) - W / 2)
                    ring_ = max(0.0, 1.0 - abs(d - rr) / 0.9) * (1 - age / 0.45)
                    r += 90 * ring_
                    g += 50 * ring_
                    b += 40 * ring_
            frames.putpixel((x, f * W + y), (clamp(r), clamp(g), clamp(b), 255))
save(frames, "block/hot_spring_water.png")
with open(os.path.join(OUT, "block/hot_spring_water.png.mcmeta"), "w") as fh:
    fh.write('{\n  "animation": {\n    "frametime": 3,\n    "interpolate": true\n  }\n}\n')

# ---------- the bonsai pot: dark blue-grey glaze ----------
pot = Image.new("RGBA", (16, 16))
for y in range(16):
    for x in range(16):
        n = rnd.uniform(-1, 1)
        sheen = 0.18 * math.exp(-((x - 5) ** 2) / 6.0) * (1 if 3 < y < 13 else 0.4)
        base = (58, 70, 88)
        if y < 2:
            base = (72, 84, 102)  # (the lip)
        c = [base[i] * (1 + n * 0.05) + 255 * sheen * 0.35 for i in range(3)]
        pot.putpixel((x, y), (clamp(c[0]), clamp(c[1]), clamp(c[2]), 255))
save(pot, "block/bonsai_pot.png")

# ---------- items ----------
def item(pixels, palette, path):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(pixels):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), palette[ch])
    save(img, path)

item([
    "................",
    "...a.a.a.a.a....",
    "...a.a.a.a.a....",
    "..bbbbbbbbbbb...",
    "..cccccccccccc..",
    ".......dd.......",
    ".......dd.......",
    ".......de.......",
    ".......de.......",
    ".......de.......",
    ".......de.......",
    ".......de.......",
    ".......de.......",
    ".......de.......",
    ".......ee.......",
    "................"],
    {"a": (150, 112, 70, 255), "b": (176, 134, 84, 255), "c": (120, 88, 52, 255), "d": (168, 128, 80, 255), "e": (118, 86, 50, 255)},
    "item/zen_rake.png")

item([
    "................",
    "................",
    "...ggg.....rr...",
    "..kkkkkkkkkkkk..",
    ".kssssssssssssk.",
    ".ksshhooooohhsk.",
    ".kshhollllohhsk.",
    ".kshollLlllohsk.",
    ".kshollllllohsk.",
    ".kshhollllohhsk.",
    ".ksshhooooohhsk.",
    ".kssssssssssssk.",
    "..kkkkkkkkkkkk..",
    "................",
    "................",
    "................"],
    {"g": (90, 90, 96, 255), "r": (200, 40, 40, 255), "k": (28, 28, 32, 255), "s": (62, 62, 70, 255), "h": (44, 44, 50, 255),
     "o": (120, 120, 130, 255), "l": (40, 70, 110, 255), "L": (190, 220, 255, 255)},
    "item/camera.png")

item([
    "................",
    "..wwwwwwwwwwww..",
    "..wbbbbbbbbbbw..",
    "..wbbbbbbbbbbw..",
    "..wbbbbbbyybbw..",
    "..wbbbbbbyybbw..",
    "..wbbbbbbbbbbw..",
    "..wbbgbbbbbbbw..",
    "..wbgggbbbgbbw..",
    "..wggggggggggw..",
    "..wddddddddddw..",
    "..wwwwwwwwwwww..",
    "..wwwwwwwwwwww..",
    "..wwwwwwwwwwww..",
    "..ssssssssssss..",
    "................"],
    {"w": (244, 240, 230, 255), "b": (120, 170, 230, 255), "y": (255, 220, 90, 255), "g": (90, 160, 70, 255), "d": (110, 90, 60, 255),
     "s": (180, 176, 166, 255)},
    "item/photo.png")

save(Image.new("RGBA", (16, 16), (255, 255, 255, 255)), "misc/white.png")
print("ok")

# ---------- the camera's 3D model texture (32x32; the model's UVs count in 16ths) ----------
cam = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
def fill(x0, y0, x1, y1, fn):
    for y in range(y0, y1):
        for x in range(x0, x1):
            cam.putpixel((x, y), fn(x - x0, y - y0, x1 - x0, y1 - y0))
crng = random.Random(3)
# leather body (0..16, 0..16): dark, pebbled
fill(0, 0, 16, 16, lambda x, y, w, h: tuple([clamp(34 + crng.uniform(-6, 6) + (6 if (x * 7 + y * 3) % 5 == 0 else 0))] * 2
                                            + [clamp(38 + crng.uniform(-6, 6))]) + (255,))
# brushed metal (16..32, 0..8)
fill(16, 0, 32, 8, lambda x, y, w, h: (clamp(178 + 30 * math.sin(y * 1.7) * 0.3 + crng.uniform(-8, 8)),) * 3 + (255,))
# lens front (16..24, 8..16): ring, dark glass, a highlight
def lens(x, y, w, h):
    d = math.hypot(x - 3.5, y - 3.5)
    if d > 3.6:
        return (70, 70, 76, 255)
    if d > 2.7:
        return (120, 120, 128, 255)
    if math.hypot(x - 2.3, y - 2.3) < 0.9:
        return (200, 230, 255, 255)
    return (clamp(20 + d * 8), clamp(36 + d * 10), clamp(70 + d * 14), 255)
fill(16, 8, 24, 16, lens)
# lens barrel sides (24..32, 8..16): ridged metal
fill(24, 8, 32, 16, lambda x, y, w, h: (clamp(70 + (40 if x % 2 == 0 else 0)),) * 3 + (255,))
# back screen (0..12, 16..24): a little picture of the view
def screen(x, y, w, h):
    if x == 0 or y == 0 or x == w - 1 or y == h - 1:
        return (20, 20, 24, 255)
    if y < 4:
        return (110, 170, 230, 255)
    if y < 6:
        return (90, 160, 80, 255)
    return (70, 120, 60, 255)
fill(0, 16, 12, 24, screen)
# red button (16..20, 16..20), flash (20..28, 16..20)
fill(16, 16, 20, 20, lambda x, y, w, h: (200, 40, 40, 255) if 0 < x < 3 and 0 < y < 3 else (150, 30, 30, 255))
fill(20, 16, 28, 20, lambda x, y, w, h: (240, 240, 230, 255) if 0 < x < 7 and 0 < y < 3 else (160, 160, 165, 255))
save(cam, "item/camera_model.png")
