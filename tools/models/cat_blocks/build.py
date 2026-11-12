"""The cat companion's blocks, modelled and textured: the cat bed (plush, quilted cushion, rolled rim - tinted by its
dye), the scratching post (in three states of wear) and the cat tree (wooden boards under carpet, sisal rope posts,
dangling pom-poms), the food bowl (flared glazed bowl, five food levels), the cardboard box (printed, taped, doodled
on) and the cat flap (a framed door that swings in). The footprints and heights stay the blocks' own (their shapes in
code, where the cat sits or perches).
Run from the repository root: python3 tools/models/cat_blocks/build.py"""
import json, math, os, random
import numpy as np
from PIL import Image

ROOT = "src/main/resources/assets/skyblocks"
rnd = random.Random(7)
nrnd = np.random.RandomState(7)


# ---------------------------------------------------------------- textures

def save_tex(name, arr):
    arr = np.clip(arr, 0, 255).astype(np.uint8)
    if arr.shape[2] == 3:
        arr = np.concatenate([arr, np.full(arr.shape[:2] + (1,), 255, np.uint8)], 2)
    Image.fromarray(arr, "RGBA").save(os.path.join(ROOT, "textures/block", name + ".png"))


def noise(amount, size=16):
    return nrnd.uniform(-amount, amount, (size, size))


def grey(v):
    return np.stack([v, v, v], 2)


def colour(base, shade):
    """base RGB scaled per pixel by shade (16 x 16)."""
    return np.stack([shade * base[0], shade * base[1], shade * base[2]], 2)


Y, X = np.mgrid[0:16, 0:16].astype(np.float32)

# -- the bed: plush fabric (soft ribs), its canvas underside, the quilted cushion with a button, the rolled rim
rib = 0.5 + 0.5 * np.cos((X + 0.5) / 4 * 2 * math.pi)
fabric = 214 + 22 * rib - 10 * (Y > 13) + 8 * (Y < 2) + noise(5)
for _ in range(14):  # (a few lighter fluffs)
    fabric[rnd.randrange(16), rnd.randrange(16)] += 12
save_tex("cat_bed_fabric", grey(fabric))
canvas = 150 + 10 * ((X + Y) % 2) + noise(6)
save_tex("cat_bed_base", grey(canvas))
# quilting: stitch lines on the diagonals every 8, each diamond puffed up, a button in the middle
d1 = (X + Y) % 8
d2 = (X - Y) % 8
puff = np.minimum(np.minimum(d1, 8 - d1), np.minimum(d2, 8 - d2)) / 4.0
cushion = 206 + 40 * puff + noise(4)
cushion[(d1 == 0) | (d2 == 0)] -= 28
for (bx, by) in ((7, 7), (8, 8), (7, 8), (8, 7)):
    cushion[by, bx] = 150
cushion[7, 7] = 180
save_tex("cat_bed_cushion", grey(cushion))
# the rolled rim (a soft tube: lit along its middle rows)
roll = 1.0 - ((Y - 7.5) / 8.0) ** 2
piping = 172 + 70 * roll + 6 * ((X // 2) % 2) + noise(3)
save_tex("cat_bed_piping", grey(piping))

# -- sisal rope, wound round (strands slanting, each strand lit on top), worn (scratches), frayed (loose fibres)
def rope_tex(wear):
    # each turn of rope two pixels tall, slanting a pixel every four across: its top row lit, its bottom in shadow
    row = (Y + X // 4) % 2
    turn = ((Y + X // 4) // 2) % 3
    strand = np.where(row == 0, 1.06, 0.80) + 0.04 * (turn == 1) - 0.03 * (turn == 2)
    fibre = 0.05 * ((X + 2 * Y) % 3 == 0)
    base = np.array([204, 172, 120])
    shade = strand + fibre + noise(0.035)
    img = colour(base, shade)
    r = random.Random(3 + wear)
    if wear >= 1:
        for _ in range(10 + 8 * wear):  # (claw marks: short dark slashes down the rope)
            x, y = r.randrange(16), r.randrange(13)
            for k in range(r.randrange(2, 4)):
                img[min(15, y + k), x] *= 0.68
    if wear >= 2:
        for _ in range(26):  # (loose, light fibres)
            x, y = r.randrange(16), r.randrange(16)
            img[y, x] = np.minimum(255, img[y, x] * 1.28)
    return img


for wear, suffix in ((0, ""), (1, "_worn"), (2, "_frayed")):
    save_tex("scratching_post_rope" + suffix, rope_tex(wear))
# carpet (short pile: speckled, a faint weave) and its edge (pile over the board's edge)
carpet_base = np.array([182, 150, 136])
carpet = colour(carpet_base, 1.0 + noise(0.07) + 0.03 * ((X + Y * 3) % 4 == 0))
save_tex("scratching_post_carpet", carpet)
edge = colour(carpet_base, 0.92 + noise(0.07))
edge[0] = colour(carpet_base, np.full((16, 16), 1.08))[0]  # (the pile catching the light over the top edge)
save_tex("scratching_post_carpet_edge", edge)
# the boards: pale birch-ish plywood, grain along x, layered edge
wood = colour(np.array([214, 186, 140]), 0.96 + 0.05 * np.sin(Y * 1.7 + np.sin(X * 0.4) * 1.5) + noise(0.025))
save_tex("cat_wood", wood)
# yarn pom-poms (fluffy pink) and their string
pom = colour(np.array([236, 120, 160]), 0.86 + 0.16 * nrnd.rand(16, 16))
save_tex("cat_pom", pom)
save_tex("cat_string", grey(np.full((16, 16), 236.0) + noise(6)))

# -- the bowl: glaze (greyscale, tinted) with a paw print, its rim, the plain inside, kibble (fish and stars)
glaze = 222 + 24 * np.exp(-((X - 4.5) ** 2) / 6.0) - 18 * (Y > 12) + noise(3)
for (px, py) in ((7, 10), (8, 10), (7, 11), (8, 11), (5, 8), (7, 7), (8, 7), (10, 8)):  # (a little paw print)
    glaze[py, px] = 168
save_tex("cat_bowl", grey(glaze))
rim = 246 - 10 * np.abs(Y - 7.5) / 7.5 + noise(2)
save_tex("cat_bowl_rim", grey(rim))
save_tex("cat_bowl_inside", grey(238 - 14 * np.hypot(X - 7.5, Y - 7.5) / 10 + noise(3)))
food = colour(np.array([150, 96, 56]), 1.0 + noise(0.08))
r = random.Random(5)
for _ in range(22):  # (kibble: brown bits, pink fish bits, lighter stars)
    x, y = r.randrange(15), r.randrange(15)
    c = r.choice([(184, 122, 72), (232, 150, 128), (204, 160, 96), (120, 74, 44)])
    food[y, x] = c
    if r.random() < 0.6:
        food[y, x + 1] = [v * 0.85 for v in c]
save_tex("cat_bowl_food", food)

# -- the cardboard box: corrugated board (faint flutes down it), tape, print, a doodle; its cut edge; the inside
def board(dark=1.0):
    flute = 0.03 * ((X % 3) == 0)
    return colour(np.array([194, 150, 100]), (0.95 + flute + noise(0.025)) * dark)


plain = board()
plain[:, 7:9] = colour(np.array([214, 190, 150]), 1.0 + noise(0.03))[:, 7:9]  # (tape down the middle)
plain[:, 7] *= 1.04
save_tex("cardboard_box", plain)
# (a wall shows the texture's rows 9 .. 15, columns 2 .. 13: what's printed or drawn on it goes there)
def draw(img, rows, x0, y0, inks):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in inks:
                img[y0 + y, x0 + x] = inks[ch]


printed = board()
ink = np.array([92, 64, 44])
draw(printed, ["..x......x..",  # (this way up, and a line of print)
               ".xxx....xxx.",
               "xxxxx..xxxxx",
               "..x......x..",
               "..x......x..",
               "............",
               "x.xx.xxx.xx."], 2, 9, {"x": ink})
save_tex("cardboard_box_print", printed)
doodle = board()
pen = np.array([40, 36, 44])
draw(doodle, [".x......x.",  # (a cat, drawn on in marker)
              ".xx....xx.",
              ".x.xxxx.x.",
              "x..o..o..x",
              "x...vv...x",
              ".x.w..w.x.",
              "..xxxxxx.."], 3, 9, {"x": pen, "o": pen, "w": pen, "v": (210, 110, 120)})
save_tex("cardboard_box_doodle", doodle)
cut = board(0.9)
cut[(X.astype(int) % 2 == 0) & (Y.astype(int) % 4 < 2)] *= 0.82  # (the flutes, seen end on)
save_tex("cardboard_box_edge", cut)
save_tex("cardboard_box_inside", board(0.8))

# -- the cat flap: a white plastic frame (screws), its smoky door (a paw print, a magnet strip), the dark hole
frame = grey(232 + noise(3))
for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
    frame[y, x] = (150, 150, 156)
save_tex("cat_flap_frame", frame)
door = colour(np.array([150, 160, 172]), 0.9 + 0.12 * (1 - Y / 16) + noise(0.02))
door[14:16] = (70, 70, 78)  # (the magnet strip along its bottom)
door[7:8] = (120, 124, 134)  # (the hinge, along its top: the door shows rows 8 .. 14)
for (x, y) in ((6, 11), (7, 11), (8, 11), (9, 11), (6, 12), (7, 12), (8, 12), (9, 12), (7, 13), (8, 13),  # (a paw: its pad, four toes)
               (4, 9), (6, 8), (9, 8), (11, 9)):
    door[y, x] = (214, 222, 232)
save_tex("cat_flap_door", door)


# ---------------------------------------------------------------- models

def face(tex, uv=None, tint=None, rot=None, cull=None):
    f = {"texture": "#" + tex}
    if uv is not None:
        f["uv"] = uv
    if tint is not None:
        f["tintindex"] = tint
    if rot:
        f["rotation"] = rot
    if cull:
        f["cullface"] = cull
    return f


ALL = ("north", "south", "east", "west", "up", "down")


def el(frm, to, faces, rotation=None, shade=True):
    e = {"from": [round(v, 4) for v in frm], "to": [round(v, 4) for v in to], "faces": faces}
    if rotation:
        e["rotation"] = rotation
    if not shade:
        e["shade"] = False
    return e


def cube(frm, to, tex, tint=None, skip=(), over=None):
    """All six faces one texture (default uv: where the element is), but those in skip; over: {face: texture}."""
    over = over or {}
    return el(frm, to, {f: face(over.get(f, tex), tint=tint) for f in ALL if f not in skip})


def model(name, textures, elements, extra=None):
    m = {"parent": "block/block", "textures": textures, "elements": elements}
    if extra:
        m.update(extra)
    with open(os.path.join(ROOT, "models/block", name + ".json"), "w") as f:
        json.dump(m, f, indent=2)


# -- the cat bed: canvas pad, plush body, quilted cushion, a rim (high at the back, lower round the sides, dipped at
# the front to step in), rolled piping along its top; the corners rounded off
bed = [
    cube((1.5, 0, 1.5), (14.5, 0.5, 14.5), "base", 0, skip=("up",)),
    cube((1, 0.5, 1), (15, 3, 15), "fabric", 0, skip=("up", "down")),
    cube((3, 3, 3), (13, 4.5, 13), "cushion", 1, skip=("down",)),
    # back
    cube((2.5, 3, 13), (13.5, 6.25, 15), "fabric", 0, skip=("down",), over={"up": "piping"}),
    el((2.5, 6.25, 13.25), (13.5, 7, 14.75), {f: face("piping", tint=1) for f in ("north", "south", "up", "east", "west")}),
    # sides, stepping down towards the front
    cube((1, 3, 3), (3, 5.75, 13.5), "fabric", 0, skip=("down",), over={"up": "piping"}),
    cube((13, 3, 3), (15, 5.75, 13.5), "fabric", 0, skip=("down",), over={"up": "piping"}),
    el((1.25, 5.75, 3.5), (2.75, 6.5, 13.5), {f: face("piping", tint=1) for f in ("east", "west", "up", "north")}),
    el((13.25, 5.75, 3.5), (14.75, 6.5, 13.5), {f: face("piping", tint=1) for f in ("east", "west", "up", "north")}),
    # back corners (rounded: in a little)
    cube((1.5, 3, 13), (2.5, 6, 14.5), "fabric", 0, skip=("down",)),
    cube((13.5, 3, 13), (14.5, 6, 14.5), "fabric", 0, skip=("down",)),
    # the front, dipped in the middle
    cube((1.5, 3, 1), (5.5, 5, 3), "fabric", 0, skip=("down",), over={"up": "piping"}),
    cube((10.5, 3, 1), (14.5, 5, 3), "fabric", 0, skip=("down",), over={"up": "piping"}),
    el((5.5, 3, 1.25), (10.5, 4.25, 2.75), {f: face("piping", tint=1) for f in ("north", "south", "up")}),
]
model("cat_bed", {"fabric": "skyblocks:block/cat_bed_fabric", "cushion": "skyblocks:block/cat_bed_cushion", "base": "skyblocks:block/cat_bed_base",
                  "piping": "skyblocks:block/cat_bed_piping", "particle": "skyblocks:block/cat_bed_fabric"}, bed)


# -- board + carpet slabs, rope posts with collars, a pom-pom on a string
def slab(x0, y0, z0, x1, y1, z1):
    """A board (0.5 thick) with carpet on it, up to y1."""
    return [cube((x0, y0, z0), (x1, y0 + 0.5, z1), "wood", skip=()),
            el((x0, y0 + 0.5, z0), (x1, y1, z1), {"up": face("carpet"), "down": face("carpet"), **{f: face("edge") for f in ("north", "south", "east", "west")}})]


def post(x0, y0, x1, y1):
    return [el((x0, y0, x0), (x1, y1, x1), {f: face("rope") for f in ("north", "south", "east", "west")}),
            cube((x0 - 0.25, y0, x0 - 0.25), (x1 + 0.25, y0 + 0.5, x1 + 0.25), "wood"),
            cube((x0 - 0.25, y1 - 0.5, x0 - 0.25), (x1 + 0.25, y1, x1 + 0.25), "wood")]


def toy(x, z, top, length):
    """A pom-pom hanging on a string from (x, top, z)."""
    return [cube((x - 0.1, top - length, z - 0.1), (x + 0.1, top, z + 0.1), "string"),
            cube((x - 0.75, top - length - 1.5, z - 0.75), (x + 0.75, top - length, z + 0.75), "pom"),
            cube((x - 0.5, top - length - 1.75, z - 0.5), (x + 0.5, top - length + 0.25, z + 0.5), "pom")]


POST_TEX = {"carpet": "skyblocks:block/scratching_post_carpet", "edge": "skyblocks:block/scratching_post_carpet_edge", "wood": "skyblocks:block/cat_wood",
            "string": "skyblocks:block/cat_string", "pom": "skyblocks:block/cat_pom", "particle": "skyblocks:block/scratching_post_carpet"}
for suffix in ("", "_worn", "_frayed"):
    model("scratching_post" + suffix, {"rope": "skyblocks:block/scratching_post_rope" + suffix, **POST_TEX},
          slab(3, 0, 3, 13, 2, 13) + post(5.5, 2, 10.5, 14) + slab(3, 14, 3, 13, 16, 13) + toy(11.75, 8, 14, 4.5))

# the cat tree: the lower half (base, post, the ledge on its west with a bracket under it, a pom-pom from the ledge);
# the upper (post, the top platform with a padded rim round its back and sides, a pom-pom from its front)
lower = (slab(1, 0, 1, 15, 2, 15) + post(6, 2, 10, 16) + slab(0, 8, 3, 6, 10, 13)
         + [cube((4, 6.5, 7), (6, 8, 9), "wood"), cube((5, 5, 7.25), (6, 6.5, 8.75), "wood")]
         + toy(1.25, 8, 8, 2.5))
model("cat_tree_lower", {"rope": "skyblocks:block/scratching_post_rope", **POST_TEX}, lower)
upper = ([el((6, 0, 6), (10, 11.5, 10), {f: face("rope") for f in ("north", "south", "east", "west")}),
          cube((5.75, 11, 5.75), (10.25, 11.5, 10.25), "wood")]
         + slab(1, 11.5, 1, 15, 14, 15)
         + [cube((1.5, 14, 12.5), (14.5, 16, 14.75), "carpet", over={"north": "edge", "south": "edge"}),
            cube((1.25, 14, 3), (2.75, 15, 12.5), "carpet"),
            cube((13.25, 14, 3), (14.75, 15, 12.5), "carpet")]
         + toy(8, 1.75, 11.5, 3))
model("cat_tree_upper", {"rope": "skyblocks:block/scratching_post_rope", **POST_TEX}, upper)


# -- the bowl: a foot, a floor, a narrow lower wall flaring to a wider upper one, a light rim; its food (0 .. 4)
def ring(x0, x1, y0, y1, t, tex, top=None):
    out = []
    for (a, b) in (((x0, y0, x0), (x1, y1, x0 + t)), ((x0, y0, x1 - t), (x1, y1, x1))):
        out.append(el(a, b, {"north": face(tex, tint=0), "south": face(tex, tint=0), "east": face(tex, tint=0), "west": face(tex, tint=0),
                             "up": face(top or tex, tint=0)}))
    for (a, b) in (((x0, y0, x0 + t), (x0 + t, y1, x1 - t)), ((x1 - t, y0, x0 + t), (x1, y1, x1 - t))):
        out.append(el(a, b, {"east": face(tex, tint=0), "west": face(tex, tint=0), "up": face(top or tex, tint=0)}))
    return out


for level in range(5):
    bowl = [cube((5, 0, 5), (11, 0.5, 11), "bowl", 0, over={"up": "inside"}),
            el((4.5, 0.5, 4.5), (11.5, 1, 11.5), {"up": face("inside"), "down": face("bowl", tint=0)})]
    bowl += ring(4.5, 11.5, 0.5, 2, 0.75, "bowl")
    bowl += ring(4, 12, 2, 3, 1, "bowl", top="rim")
    if level > 0:
        h = 1 + 0.5 * level
        bowl.append(el((5.25, 1, 5.25), (10.75, h, 10.75), {f: face("food") for f in ("north", "south", "east", "west", "up")}))
        if level == 4:
            bowl.append(el((6.5, h, 6.5), (9.5, h + 0.75, 9.5), {f: face("food") for f in ("north", "south", "east", "west", "up")}))
    model("cat_bowl_%d" % level, {"bowl": "skyblocks:block/cat_bowl", "rim": "skyblocks:block/cat_bowl_rim", "inside": "skyblocks:block/cat_bowl_inside",
                                  "food": "skyblocks:block/cat_bowl_food", "particle": "skyblocks:block/cat_bowl"}, bowl)

# -- the box: walls (each side its own print), their cut top edges, the floor; four flaps open at different angles
wall = lambda frm, to, out_face, out_tex, in_face: el(frm, to, {
    out_face: face(out_tex), in_face: face("inside"), "up": face("edge"),
    **{f: face("edge") for f in ("north", "south", "east", "west") if f not in (out_face, in_face)}})
box = [el((2, 0, 2), (14, 1, 14), {"up": face("inside"), "down": face("box"), **{f: face("box") for f in ("north", "south", "east", "west")}}),
       wall((2, 0, 2), (14, 7, 3), "north", "print", "south"),
       wall((2, 0, 13), (14, 7, 14), "south", "doodle", "north"),
       wall((2, 0, 3), (3, 7, 13), "west", "box", "east"),
       wall((13, 0, 3), (14, 7, 13), "east", "box", "west"),
       el((2, 7, 2), (14, 12, 2.5), {"north": face("inside"), "south": face("box"), "up": face("edge"), "east": face("edge"), "west": face("edge")},
          rotation={"origin": [8, 7, 2], "axis": "x", "angle": -45}),
       el((2, 7, 13.5), (14, 12, 14), {"south": face("inside"), "north": face("box"), "up": face("edge"), "east": face("edge"), "west": face("edge")},
          rotation={"origin": [8, 7, 14], "axis": "x", "angle": 22.5}),
       el((2, 7, 3), (2.5, 11, 13), {"west": face("inside"), "east": face("box"), "up": face("edge"), "north": face("edge"), "south": face("edge")},
          rotation={"origin": [2, 7, 8], "axis": "z", "angle": 22.5}),
       el((13.5, 7, 3), (14, 11, 13), {"east": face("inside"), "west": face("box"), "up": face("edge"), "north": face("edge"), "south": face("edge")},
          rotation={"origin": [14, 7, 8], "axis": "z", "angle": -45})]
model("cardboard_box", {"box": "skyblocks:block/cardboard_box", "print": "skyblocks:block/cardboard_box_print", "doodle": "skyblocks:block/cardboard_box_doodle",
                        "edge": "skyblocks:block/cardboard_box_edge", "inside": "skyblocks:block/cardboard_box_inside", "particle": "skyblocks:block/cardboard_box"}, box)


# -- the flap: the block itself (its material: #side, swapped in code), on both faces a frame (arched top, sill, a
# hood over the hinge) round the door; shut, or swung in (and the dark way through showing)
def flap_side(z_out, sign):
    """The frame on the face at z_out (0: north, 16: south), sticking out by 0.4 (sign: which way out is)."""
    za, zb = (z_out - 0.4, z_out) if sign < 0 else (z_out, z_out + 0.4)
    out = "north" if sign < 0 else "south"
    fr = lambda a, b: el((a[0], a[1], za), (b[0], b[1], zb), {out: face("frame"), "up": face("frame"), "down": face("frame"), "east": face("frame"), "west": face("frame")})
    return [fr((3.25, 0.75), (12.75, 1.25)),  # (the sill)
            fr((3.25, 1.25), (4, 8.25)), fr((12, 1.25), (12.75, 8.25)),  # (the sides)
            fr((3.5, 8.25), (12.5, 9)), fr((4, 9), (12, 9.75)),  # (the arched top)
            el((4.5, 9.75, za), (11.5, 10.25, zb),
               {out: face("frame"), "up": face("frame"), "down": face("frame"), "east": face("frame"), "west": face("frame")})]


def door_face(z, out):
    return el((4, 1.25, min(z, z + (-0.1 if out == "north" else 0.1))), (12, 8.25, max(z, z + (-0.1 if out == "north" else 0.1))), {out: face("door")})


FLAP_TEX = {"side": "minecraft:block/oak_planks", "frame": "skyblocks:block/cat_flap_frame", "door": "skyblocks:block/cat_flap_door",
            "hole": "minecraft:block/black_concrete", "particle": "minecraft:block/oak_planks"}
solid = el((0, 0, 0), (16, 16, 16), {f: face("side", cull=f) for f in ALL})
model("cat_flap", FLAP_TEX, [solid] + flap_side(0, -1) + flap_side(16, 1) + [door_face(0, "north"), door_face(16, "south")])
hole = lambda z, out: el((4, 1.25, min(z, z + (-0.05 if out == "north" else 0.05))), (12, 8.25, max(z, z + (-0.05 if out == "north" else 0.05))), {out: face("hole")})
swung = el((4, 1.75, -0.45), (12, 8.25, -0.05), {f: face("door") for f in ("north", "south", "down", "east", "west")},
           rotation={"origin": [8, 8.25, -0.25], "axis": "x", "angle": 45})
model("cat_flap_open", FLAP_TEX, [solid] + flap_side(0, -1) + flap_side(16, 1) + [hole(0, "north"), hole(16, "south"), swung])
print("cat blocks built")


# -- the window perch: a fleece hammock slung between two rods, held out from the window by suction cups (made facing
# south: the window at z 0, the hammock out to z 10; turned by its blockstate)
fleece = colour(np.array([226, 214, 206]), 0.95 + 0.07 * nrnd.rand(16, 16))
for (x, y) in ((3, 4), (4, 3), (5, 4), (4, 5), (4, 4), (11, 10), (12, 9), (13, 10), (12, 11), (12, 10)):  # (two little paw prints)
    fleece[y, x] = (196, 168, 172)
save_tex("cat_window_perch_fleece", fleece)
rod = colour(np.array([186, 190, 198]), 0.9 + 0.2 * (1 - np.abs(Y - 7.5) / 8) + noise(0.02))
save_tex("cat_window_perch_frame", rod)
cup = np.zeros((16, 16, 4))
cup[..., :3] = (214, 226, 236)
cup[..., 3] = 200
cup[(X + Y) % 5 == 0] = (240, 246, 252, 220)
Image.fromarray(cup.astype(np.uint8), "RGBA").save(os.path.join(ROOT, "textures/block/cat_window_perch_cup.png"))
perch = [
    # the suction cups on the glass, and their stems out to the back rod
    cube((3, 6.5, 0), (5.5, 9, 0.6), "cup"), cube((10.5, 6.5, 0), (13, 9, 0.6), "cup"),
    cube((3.75, 7.25, 0.6), (4.75, 8.25, 1.6), "frame"), cube((11.25, 7.25, 0.6), (12.25, 8.25, 1.6), "frame"),
    cube((3.85, 5.5, 1.1), (4.65, 7.25, 1.6), "frame"), cube((11.35, 5.5, 1.1), (12.15, 7.25, 1.6), "frame"),
    # the frame: two side rods out from the window, a back rod and a front one
    cube((2, 5, 1), (3, 6, 10), "frame"), cube((13, 5, 1), (14, 6, 10), "frame"),
    cube((3, 5, 1), (13, 6, 2), "frame"), cube((3, 5, 9), (13, 6, 10), "frame"),
    # the fleece, sagging between them (lowest in the middle)
    el((3, 4.5, 2), (13, 5.25, 3), {f: face("fleece") for f in ALL}),
    el((3, 4.5, 8), (13, 5.25, 9), {f: face("fleece") for f in ALL}),
    el((3, 3.75, 3), (13, 4.5, 8), {f: face("fleece") for f in ALL}),
]
model("cat_window_perch", {"fleece": "skyblocks:block/cat_window_perch_fleece", "frame": "skyblocks:block/cat_window_perch_frame",
                           "cup": "skyblocks:block/cat_window_perch_cup", "particle": "skyblocks:block/cat_window_perch_fleece"},
      perch, {"render_type": "minecraft:translucent"})
json.dump({"variants": {"facing=south": {"model": "skyblocks:block/cat_window_perch"}, "facing=west": {"model": "skyblocks:block/cat_window_perch", "y": 90},
                        "facing=north": {"model": "skyblocks:block/cat_window_perch", "y": 180}, "facing=east": {"model": "skyblocks:block/cat_window_perch", "y": 270}}},
          open(os.path.join(ROOT, "blockstates/cat_window_perch.json"), "w"), indent=2)
json.dump({"parent": "skyblocks:block/cat_window_perch"}, open(os.path.join(ROOT, "models/item/cat_window_perch.json"), "w"), indent=2)

# -- what the cat watches out of the window: butterflies' wings (each 8 x 8: a monarch, a blue, a pale yellow) and a
# bird's wing (a dark swallow), all on one texture; the body's along x 0 of each, the wing out to the right
wings = np.zeros((8, 32, 4), np.uint8)
shape = ["........",
         "xxxx....",
         "xxxxxxx.",
         "xxxxxxxx",
         "xxxxxxx.",
         "xxxxx...",
         "xxxx....",
         "xx......"]
palettes = [((236, 128, 32), (40, 28, 24), (250, 240, 220)), ((70, 140, 236), (24, 30, 60), (220, 236, 255)), ((244, 232, 140), (120, 100, 50), (255, 255, 240))]
for k, (main, edge, dot) in enumerate(palettes):
    for y, row in enumerate(shape):
        for x, ch in enumerate(row):
            if ch == "x":
                outer = x == len(row.rstrip(".")) - 1 or y in (1, 7) or (y > 0 and shape[y - 1][x] != "x")
                c = edge if outer else main
                if outer and (x + y) % 3 == 0:
                    c = dot
                if x == 0:
                    c = edge
                wings[y, k * 8 + x] = c + (255,)
bird = ["........",
        "........",
        "xxx.....",
        "xxxxxx..",
        "xxxxxxxx",
        ".xxxx...",
        "........",
        "........"]
for y, row in enumerate(bird):
    for x, ch in enumerate(row):
        if ch == "x":
            wings[y, 24 + x] = (44, 46, 58, 255)
Image.fromarray(wings, "RGBA").save(os.path.join(ROOT, "textures/entity/window_wings.png"))
print("window perch built")
