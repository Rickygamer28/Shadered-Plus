"""Textures for the cat companion's toys: the yarn ball / feather wand / laser pointer items, the yarn ball's ball
texture and the laser's dot, and the cat tree's item (the blocks' own: tools/models/cat_blocks/build.py). Run from the repository root: python3 tools/make_cat_textures.py"""
import math, os, random
from PIL import Image

OUT = "src/main/resources/assets/skyblocks/textures"
rnd = random.Random(11)


def save(img, path):
    full = os.path.join(OUT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    img.save(full)


def clamp(v):
    return max(0, min(255, int(round(v))))


def tex(fn, size=16):
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        for x in range(size):
            c = fn(x, y)
            img.putpixel((x, y), tuple(clamp(v) for v in c) + ((255,) if len(c) == 3 else ()))
    return img


def grey(v):
    return (v, v, v)


# (the blocks' own textures - the bed, the post's rope and carpet, the bowl, the box, the flap - are made with their
# models: tools/models/cat_blocks/build.py)

# ---------- yarn ball: wound strands on the ball itself; the laser's soft red dot ----------
yarn = Image.new("RGBA", (32, 32))
for y in range(32):
    for x in range(32):
        a = (x * 0.9 + y * 0.45) % 3.0
        b = (x * 0.4 - y * 0.95) % 3.0
        s = min(abs(a - 1.5), abs(b - 1.5))
        v = 0.78 + 0.22 * min(1.0, s)
        yarn.putpixel((x, y), (clamp(205 * v), clamp(48 * v), clamp(58 * v), 255))
save(yarn, "entity/yarn_ball.png")

dot = Image.new("RGBA", (16, 16))
for y in range(16):
    for x in range(16):
        d = math.hypot(x - 7.5, y - 7.5) / 7.5
        core = max(0.0, 1 - d * 3.2)
        glow = max(0.0, 1 - d) ** 2
        dot.putpixel((x, y), (255, clamp(40 + 200 * core), clamp(40 + 200 * core), clamp(255 * min(1.0, glow + core))))
save(dot, "misc/laser_dot.png")


def item(pixels, palette, path):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(pixels):
        for x, ch in enumerate(row):
            if ch != ".":
                img.putpixel((x, y), palette[ch])
    save(img, path)


item([
    "................",
    "................",
    "................",
    "......rrrr......",
    "....rRrrRrrr....",
    "...rrRRrrrRrr...",
    "...rRrrRRrrRr...",
    "..rrrRrrrRRrrr..",
    "..rRrrRRrrrRrr..",
    "..rrRrrrRRrrRr..",
    "...rrRRrrrRrr...",
    "...rRrrRRrrrr...",
    "....rrrrrRrr.dd.",
    "......rrrr..d...",
    "...........d....",
    "................"],
    {"r": (200, 46, 56, 255), "R": (150, 30, 40, 255), "d": (200, 46, 56, 255)},
    "item/yarn_ball.png")

item([
    "............ww..",
    "...........wWww.",
    "..........wWwww.",
    ".........sWwww..",
    ".........sww....",
    "........s.......",
    ".......s........",
    "......t.........",
    ".....tT.........",
    "....tT..........",
    "...tT...........",
    "..tT............",
    ".tT.............",
    "tT..............",
    "T...............",
    "................"],
    {"w": (240, 236, 228, 255), "W": (200, 120, 200, 255), "s": (230, 230, 230, 255), "t": (176, 134, 84, 255), "T": (118, 86, 50, 255)},
    "item/feather_wand.png")

item([
    "................",
    "................",
    "................",
    "............rR..",
    "...........kkRr.",
    "..........kkkk..",
    ".........kkgkk..",
    "........kkkkk...",
    ".......kkkkk....",
    "......kkgkk.....",
    ".....kkkkk......",
    "....kkkkk.......",
    "...kkkkk........",
    "...kkkk.........",
    "....kk..........",
    "................"],
    {"k": (52, 52, 60, 255), "g": (150, 150, 160, 255), "r": (255, 60, 60, 255), "R": (255, 150, 150, 255)},
    "item/laser_pointer.png")
item([
    "...cccccccccc...",
    "...CCCCCCCCCC...",
    ".......rr.......",
    ".......Rr.......",
    ".......rr.......",
    "..cccccRr.......",
    "..CCCCCrr.......",
    ".......Rr.......",
    ".......rr.......",
    ".......Rr.......",
    ".......rr.......",
    ".......Rr.......",
    ".......rr.......",
    ".cccccccccccccc.",
    ".CCCCCCCCCCCCCC.",
    "................"],
    {"c": (190, 162, 142, 255), "C": (150, 126, 110, 255), "r": (204, 172, 122, 255), "R": (168, 138, 92, 255)},
    "item/cat_tree.png")
print("ok")
