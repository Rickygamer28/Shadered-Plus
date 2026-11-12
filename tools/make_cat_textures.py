"""Textures for the cat companion's things: the cat bed (knit fabric and cushion, greyscale - tinted by its dye), the
scratching post's rope (new, worn, frayed) and carpet, the food bowl (greyscale glaze, tinted) and its food, the
cardboard box, the cat flap, and the yarn ball / feather wand / laser pointer items, the yarn ball's ball texture and
the laser's dot. Run from the repository root: python3 tools/make_cat_textures.py"""
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


# ---------- cat bed: knit rim (rows of little v-stitches), soft cushion ----------
def knit(x, y):
    row = y // 2
    col = (x + (row % 2)) % 4
    v = 205 + (22 if col in (1, 2) else -18) + (12 if y % 2 == 0 else -6) + rnd.uniform(-6, 6)
    return grey(v)


save(tex(knit), "block/cat_bed_fabric.png")
save(tex(lambda x, y: grey(232 + 10 * math.cos((x - 7.5) / 8 * math.pi) * math.cos((y - 7.5) / 8 * math.pi) + rnd.uniform(-5, 5))),
     "block/cat_bed_cushion.png")


# ---------- scratching post: wound sisal rope, wearing to fraying; carpet ----------
def rope(wear):
    def fn(x, y):
        strand = (y + x * 0.25) % 2.0
        v = 1.0 - 0.22 * abs(strand - 1.0)
        base = (196, 164, 116)
        n = rnd.uniform(-1, 1)
        c = [base[i] * (v + n * 0.05) for i in range(3)]
        if wear > 0 and rnd.random() < 0.10 * wear:
            c = [ci * 0.72 for ci in c]  # (scratched-in gouges)
        if wear > 1 and rnd.random() < 0.10:
            c = [min(255, ci * 1.25) for ci in c]  # (loose fibres)
        return c
    return fn


for wear, name in ((0, ""), (1, "_worn"), (2, "_frayed")):
    save(tex(rope(wear)), "block/scratching_post_rope%s.png" % name)
save(tex(lambda x, y: [b * (1 + rnd.uniform(-0.07, 0.07)) for b in (176, 150, 132)]), "block/scratching_post_carpet.png")

# ---------- food bowl: glaze (greyscale, tinted), its plain inside, fish kibble ----------
save(tex(lambda x, y: grey(214 + 30 * math.exp(-((x - 5) ** 2) / 5.0) * (1 if 4 < y < 13 else 0.3) + rnd.uniform(-4, 4))),
     "block/cat_bowl.png")
save(tex(lambda x, y: grey(240 + rnd.uniform(-5, 5))), "block/cat_bowl_inside.png")


def kibble(x, y):
    r = rnd.random()
    if r < 0.25:
        return (232, 150, 120)  # (salmon pink bits)
    if r < 0.5:
        return (150, 98, 60)
    return [b * (1 + rnd.uniform(-0.12, 0.12)) for b in (180, 122, 74)]


save(tex(kibble), "block/cat_bowl_food.png")


# ---------- cardboard box: brown board with corrugation, a strip of tape; the inside a shade darker ----------
def board(dark):
    def fn(x, y):
        f = 0.94 + 0.04 * (x % 3 == 0) + rnd.uniform(-0.03, 0.03)
        c = [b * f * (0.85 if dark else 1.0) for b in (190, 146, 96)]
        if not dark and y in (1, 2):
            c = [b * (1 + rnd.uniform(-0.03, 0.03)) for b in (214, 190, 150)]  # (the tape)
        return c
    return fn


save(tex(board(False)), "block/cardboard_box.png")
save(tex(board(True)), "block/cardboard_box_inside.png")


# ---------- cat flap: its little door (the flap itself is built of whatever block it was given)
save(tex(lambda x, y: [b * (1 + rnd.uniform(-0.03, 0.03)) for b in (196, 196, 200)] if y != 0 else (150, 150, 156)), "block/cat_flap_door.png")

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
