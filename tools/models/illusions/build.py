"""Models and textures for the rifts and illusions: the bottomless pit (its top is drawn in code), the room window (its
glass is drawn in code), the moon jar, the spin pad (its plate turns in code), the dark fountain (all drawn in code),
and the sky wand's and rift knife's item textures. Run from the repository root: python3 tools/models/illusions/build.py"""
import json, math, os
import numpy as np
from PIL import Image

ROOT = "src/main/resources/assets/skyblocks"
rnd = np.random.RandomState(31)
Y, X = np.mgrid[0:16, 0:16].astype(np.float32)


def save(kind, name, rgb, alpha=None):
    rgb = np.clip(rgb, 0, 255).astype(np.uint8)
    a = np.full(rgb.shape[:2] + (1,), 255, np.uint8) if alpha is None else np.clip(alpha, 0, 255).astype(np.uint8)[..., None]
    Image.fromarray(np.concatenate([rgb, a], 2), "RGBA").save(os.path.join(ROOT, "textures", kind, name + ".png"))


def tex(base, shade):
    return np.array(base, np.float32)[None, None, :] * shade[..., None]


# ---- textures ----
# the pit's sides: dark, rough stone; its top as an item: a hole, black in the middle, a few motes
save("block", "bottomless_pit_side", tex((58, 56, 64), 0.85 + 0.25 * rnd.uniform(0, 1, (16, 16)) ** 2 + 0.06 * ((X + Y) % 5 == 0)))
d = np.sqrt((X - 7.5) ** 2 + (Y - 7.5) ** 2)
top = tex((70, 66, 74), np.clip((d - 3.0) / 5.0, 0, 1) ** 1.6 * (0.85 + 0.2 * rnd.uniform(0, 1, (16, 16))))
for (x, y) in ((6, 7), (9, 9), (8, 5)):
    top[y, x] = (110, 100, 130)
save("block", "bottomless_pit_top", top)

# the room window's wall (plaster, a frame edge round it) and, as an item, its glass: a lit room through a window
plaster = tex((214, 206, 192), 0.93 + 0.08 * rnd.uniform(0, 1, (16, 16)))
save("block", "room_window_wall", plaster)
win = np.zeros((16, 16, 3), np.float32)
win[:] = (236, 234, 228)
inner = (X >= 1) & (X <= 14) & (Y >= 1) & (Y <= 14)
glow = np.clip(1.0 - np.sqrt((X - 7.5) ** 2 + (Y - 4) ** 2) / 12.0, 0, 1)
room = tex((255, 196, 120), 0.45 + 0.55 * glow)
room[Y >= 10] = tex((150, 96, 60), 0.8 + 0.2 * glow)[Y >= 10]
win[inner] = room[inner]
win[(X == 7) | (X == 8) | (Y == 9)] = (236, 234, 228)
win[(X == 11) & (Y >= 6) & (Y <= 9)] = (40, 34, 40)  # (a cat on the sill)
win[(X == 12) & (Y >= 7) & (Y <= 9)] = (40, 34, 40)
win[(Y == 5) & ((X == 11) | (X == 12))] = (40, 34, 40)
save("block", "room_window_item", win)

# the moon jar: clear glass (edges catching the light), a cork, the moon glowing in it (the full jar's item)
edge = (X < 1) | (X > 14) | (Y < 1) | (Y > 14)
save("block", "moon_jar_glass", tex((228, 238, 255), 1.0 + 0 * X), alpha=np.where(edge, 170, 55) + 30 * np.exp(-((X - Y - 3) ** 2) / 4))
save("block", "moon_jar_cork", tex((176, 128, 82), 0.85 + 0.25 * rnd.uniform(0, 1, (16, 16))))
moon = tex((236, 238, 246), 0.9 + 0.1 * rnd.uniform(0, 1, (16, 16)))
moon[(X - 5) ** 2 + (Y - 6) ** 2 < 5] *= 0.8
moon[(X - 10) ** 2 + (Y - 10) ** 2 < 3] *= 0.82
save("block", "moon_jar_moon", moon)
# the sun jar: a gold lid; the sun in it (the full jar's item): white-hot middle, orange rim (its own random numbers:
# the textures after it stay as they were)
srnd = np.random.RandomState(77)
save("block", "sun_jar_lid", tex((236, 184, 64), 0.8 + 0.25 * srnd.uniform(0, 1, (16, 16)) + 0.15 * (Y % 4 == 0)))
dsun = np.sqrt((X - 7.5) ** 2 + (Y - 7.5) ** 2)
sun = np.stack([np.full_like(X, 255.0), 250 - dsun * 14 + 10 * srnd.uniform(0, 1, (16, 16)), 200 - dsun * 30], -1)
save("block", "sun_jar_sun", sun)

# the spin pad: a dark base with a glowing band, its top a ring; the plate that turns (round, a swirl on it)
base = tex((70, 74, 86), 0.9 + 0.12 * rnd.uniform(0, 1, (16, 16)))
base[(Y >= 9) & (Y <= 10)] = tex((120, 210, 255), 0.85 + 0.15 * np.sin(X * 0.8) ** 2)[(Y >= 9) & (Y <= 10)]
save("block", "spin_pad_side", base)
save("block", "spin_pad_top", tex((60, 64, 76), 0.9 + 0.12 * rnd.uniform(0, 1, (16, 16))))
r = np.sqrt((X - 7.5) ** 2 + (Y - 7.5) ** 2)
ang = np.arctan2(Y - 7.5, X - 7.5)
swirl = 0.5 + 0.5 * np.sin(ang * 3 + r * 0.9)
plate = tex((236, 226, 246), 0.75 + 0.25 * swirl)
plate[r < 2.2] = (255, 200, 120)
plate[(r > 6.6) & (r < 8.0)] = (90, 96, 120)
save("block", "spin_pad_plate", plate, alpha=np.where(r < 8.0, 255, 0))

# the sky wand: a dark rod, a star of sky at its tip
wand = np.zeros((16, 16, 3), np.float32)
wa = np.zeros((16, 16), np.float32)
for i in range(3, 13):  # the rod, corner to corner
    x, y = i - 1, 16 - i
    wand[y, x] = (52, 40, 70)
    wand[y, x + 1] = (84, 66, 110)
    wa[y, x] = wa[y, x + 1] = 255
cx, cy = 12, 3
for (dx, dy, c) in ((0, 0, (255, 255, 255)), (1, 0, (170, 140, 255)), (-1, 0, (170, 140, 255)), (0, 1, (120, 200, 255)), (0, -1, (255, 160, 230)),
                    (2, 0, (110, 80, 200)), (-2, 0, (110, 80, 200)), (0, 2, (80, 150, 230)), (0, -2, (230, 110, 200)), (1, 1, (90, 70, 170)),
                    (-1, -1, (90, 70, 170)), (1, -1, (140, 90, 210)), (-1, 1, (140, 90, 210))):
    wand[cy + dy, cx + dx] = c
    wa[cy + dy, cx + dx] = 255
save("item", "sky_wand", wand, alpha=wa)

# the rift knife: a dark blade with a pale cutting edge, a crossguard, a wrapped grip
knife = np.zeros((16, 16, 3), np.float32)
ka = np.zeros((16, 16), np.float32)
for i in range(6, 15):  # the blade
    x, y = i, 15 - i
    knife[y, x] = (28, 22, 40)
    knife[y + 1, x] = (60, 48, 84)
    knife[y, x - 1] = (210, 200, 240)  # (the edge, catching light)
    ka[y, x] = ka[y + 1, x] = ka[y, x - 1] = 255
for (x, y) in ((4, 9), (5, 10), (6, 11), (7, 12)):  # the guard
    knife[y, x] = (150, 120, 200)
    ka[y, x] = 255
for i in range(1, 5):  # the grip
    x, y = i, 15 - i
    knife[y, x] = (90, 52, 40) if i % 2 else (60, 34, 28)
    ka[y, x] = 255
knife[14, 0] = (150, 120, 200)
ka[14, 0] = 255
save("item", "rift_knife", knife, alpha=ka)

# ---- models ----
ALL = ("north", "south", "east", "west", "up", "down")


def face(t):
    return {"texture": "#" + t}


def cube(a, b, t, over=None, skip=()):
    over = over or {}
    return {"from": list(a), "to": list(b), "faces": {f: face(over.get(f, t)) for f in ALL if f not in skip}}


def model(name, textures, elements, extra=None, parent="block/block"):
    m = {"parent": parent, "textures": textures, "elements": elements}
    m.update(extra or {})
    json.dump(m, open(os.path.join(ROOT, "models/block", name + ".json"), "w"), indent=2)


def item(name, m):
    json.dump(m, open(os.path.join(ROOT, "models/item", name + ".json"), "w"), indent=2)


def state(name, s):
    json.dump(s, open(os.path.join(ROOT, "blockstates", name + ".json"), "w"), indent=2)


# the bottomless pit: no top (the shaft's drawn there); as an item, a black hole on top
model("bottomless_pit", {"side": "skyblocks:block/bottomless_pit_side", "particle": "skyblocks:block/bottomless_pit_side"},
      [cube((0, 0, 0), (16, 16, 16), "side", skip=("up",))])
model("bottomless_pit_item", {"side": "skyblocks:block/bottomless_pit_side", "top": "skyblocks:block/bottomless_pit_top",
                              "particle": "skyblocks:block/bottomless_pit_side"}, [cube((0, 0, 0), (16, 16, 16), "side", over={"up": "top"})])
item("bottomless_pit", {"parent": "skyblocks:block/bottomless_pit_item"})
state("bottomless_pit", {"variants": {"": {"model": "skyblocks:block/bottomless_pit"}}})

# the room window (made looking out south): plaster all round, no south face (the glass is drawn there)
model("room_window", {"wall": "skyblocks:block/room_window_wall", "particle": "skyblocks:block/room_window_wall"},
      [cube((0, 0, 0), (16, 16, 16), "wall", skip=("south",))])
model("room_window_item", {"wall": "skyblocks:block/room_window_wall", "glass": "skyblocks:block/room_window_item",
                           "particle": "skyblocks:block/room_window_wall"}, [cube((0, 0, 0), (16, 16, 16), "wall", over={"south": "glass", "north": "glass"})])
item("room_window", {"parent": "skyblocks:block/room_window_item"})
ys = {"south": 0, "west": 90, "north": 180, "east": 270}
state("room_window", {"multipart": [{"when": {"facing": f}, "apply": {"model": "skyblocks:block/room_window", "y": y}} for f, y in ys.items()]})

# the moon jar: glass, its neck, a cork; full (as an item): the moon inside
jar = [cube((4.5, 0, 4.5), (11.5, 10, 11.5), "glass"), cube((5.5, 10, 5.5), (10.5, 11, 10.5), "glass"), cube((5.5, 11, 5.5), (10.5, 13, 10.5), "cork")]
model("moon_jar", {"glass": "skyblocks:block/moon_jar_glass", "cork": "skyblocks:block/moon_jar_cork", "particle": "skyblocks:block/moon_jar_glass"},
      jar, {"render_type": "minecraft:translucent"})
model("moon_jar_full", {"glass": "skyblocks:block/moon_jar_glass", "cork": "skyblocks:block/moon_jar_cork", "moon": "skyblocks:block/moon_jar_moon",
                        "particle": "skyblocks:block/moon_jar_glass"},
      [cube((6, 2.5, 6), (10, 6.5, 10), "moon")] + jar, {"render_type": "minecraft:translucent"})
item("moon_jar", {"parent": "skyblocks:block/moon_jar", "overrides": [{"predicate": {"skyblocks:moon": 1}, "model": "skyblocks:item/moon_jar_full"}]})
item("moon_jar_full", {"parent": "skyblocks:block/moon_jar_full"})
state("moon_jar", {"variants": {"moon=false": {"model": "skyblocks:block/moon_jar"}, "moon=true": {"model": "skyblocks:block/moon_jar"}}})

# the sun jar: the same glass, a gold lid; full (as an item): the sun inside
sjar = [cube((4.5, 0, 4.5), (11.5, 10, 11.5), "glass"), cube((5.5, 10, 5.5), (10.5, 11, 10.5), "glass"), cube((5, 11, 5), (11, 12.5, 11), "lid")]
model("sun_jar", {"glass": "skyblocks:block/moon_jar_glass", "lid": "skyblocks:block/sun_jar_lid", "particle": "skyblocks:block/moon_jar_glass"},
      sjar, {"render_type": "minecraft:translucent"})
model("sun_jar_full", {"glass": "skyblocks:block/moon_jar_glass", "lid": "skyblocks:block/sun_jar_lid", "sun": "skyblocks:block/sun_jar_sun",
                       "particle": "skyblocks:block/moon_jar_glass"},
      [cube((5.5, 2, 5.5), (10.5, 7, 10.5), "sun")] + sjar, {"render_type": "minecraft:translucent"})
item("sun_jar", {"parent": "skyblocks:block/sun_jar", "overrides": [{"predicate": {"skyblocks:sun": 1}, "model": "skyblocks:item/sun_jar_full"}]})
item("sun_jar_full", {"parent": "skyblocks:block/sun_jar_full"})
state("sun_jar", {"variants": {"sun=false": {"model": "skyblocks:block/sun_jar"}, "sun=true": {"model": "skyblocks:block/sun_jar"}}})

# the spin pad: a low base (its plate turns on top, drawn in code)
model("spin_pad", {"side": "skyblocks:block/spin_pad_side", "top": "skyblocks:block/spin_pad_top", "particle": "skyblocks:block/spin_pad_side"},
      [{"from": [0, 0, 0], "to": [16, 4, 16], "faces": {f: dict(face("top" if f in ("up", "down") else "side"), **({"uv": [0, 6, 16, 10]} if f not in ("up", "down") else {}))
                                                          for f in ALL}}])
item("spin_pad", {"parent": "skyblocks:block/spin_pad"})
state("spin_pad", {"variants": {"": {"model": "skyblocks:block/spin_pad"}}})

# the dark fountain: nothing but its particles' texture (it's all drawn in code)
json.dump({"textures": {"particle": "minecraft:block/black_concrete"}}, open(os.path.join(ROOT, "models/block/dark_fountain.json"), "w"), indent=2)
state("dark_fountain", {"variants": {"": {"model": "skyblocks:block/dark_fountain"}}})

# ---- the sky wand and the rift knife in 3D (their flat textures stay for anything that wants a sprite) ----
# Each model's texture is a palette of 4 x 4 patches (P(col, row)); the parts pick theirs. Both are made standing up
# along y; their display turns them diagonal, as a flat handheld item is.
prnd = np.random.RandomState(53)


def palette(name, patches):
    img = np.zeros((16, 16, 3), np.float32)
    for (c, r), (top, bottom) in patches.items():
        for y in range(4):
            img[r * 4 + y, c * 4:c * 4 + 4] = np.array(top, np.float32) * (1 - y / 3) + np.array(bottom, np.float32) * (y / 3)
        img[r * 4:r * 4 + 4, c * 4:c * 4 + 4] *= (0.92 + 0.12 * prnd.uniform(0, 1, (4, 4)))[..., None]
    save("item", name, img)


def P(c, r):
    return [c * 4, r * 4, c * 4 + 4, r * 4 + 4]


def part(a, b, uv, rot=None, glow=False, ends=None):
    faces = {f: {"texture": "#all", "uv": (ends if ends and f in ("up", "down") else uv)} for f in ALL}
    e = {"from": list(a), "to": list(b), "faces": faces}
    if rot:
        e["rotation"] = rot
    if glow:  # (NeoForge's per-face light: lit as if full bright, whatever the light where it's held)
        for f in faces.values():
            f["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    return e


def held(gui_tilt=-45, scale=0.68):
    # (as minecraft:item/handheld turns a flat item, less the 45 degrees the flat item's drawn at)
    return {
        "thirdperson_righthand": {"rotation": [0, -90, 10], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [0, 90, -10], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 3.2, 1.13], "scale": [scale, scale, scale]},
        "firstperson_lefthand": {"rotation": [0, 90, 20], "translation": [1.13, 3.2, 1.13], "scale": [scale, scale, scale]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "gui": {"rotation": [10, 25, gui_tilt], "translation": [0, 0, 0], "scale": [1.1, 1.1, 1.1]},
        "fixed": {"rotation": [0, 180, -45], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 8, 0], "scale": [1, 1, 1]},
    }


palette("sky_wand_3d", {
    (0, 0): ((70, 54, 96), (40, 30, 58)),      # the rod: dark violet wood
    (1, 0): ((255, 222, 120), (196, 140, 50)),  # gold bands
    (2, 0): ((96, 58, 44), (58, 34, 28)),       # the grip
    (3, 0): ((140, 110, 220), (84, 60, 160)),   # the pommel's gem
    (0, 1): ((255, 255, 255), (190, 225, 255)), # the star's heart
    (1, 1): ((200, 240, 255), (110, 180, 255)), # its upright points
    (2, 1): ((255, 190, 240), (230, 110, 200)), # its side points
    (3, 1): ((200, 170, 255), (130, 100, 230)), # its slanted points
})
st = {"origin": [8, 14, 8]}
wand = [
    part((6.75, 0, 6.75), (9.25, 1.25, 9.25), P(3, 0)),                     # pommel
    part((7.25, 1.25, 7.25), (8.75, 12, 8.75), P(0, 0)),                    # rod
    part((7, 1.75, 7), (9, 4.75, 9), P(2, 0)),                              # grip
    part((6.9, 4.75, 6.9), (9.1, 5.35, 9.1), P(1, 0)),                      # band
    part((6.9, 9.5, 6.9), (9.1, 10.1, 9.1), P(1, 0)),                       # band
    part((6.6, 11.4, 6.6), (9.4, 12.4, 9.4), P(1, 0)),                      # the star's collar
    part((7.5, 10.5, 7.5), (8.5, 17.5, 8.5), P(1, 1), glow=True),           # upright points
    part((4.5, 13.5, 7.5), (11.5, 14.5, 8.5), P(2, 1), glow=True),          # side points
    part((7.5, 13.5, 4.5), (8.5, 14.5, 11.5), P(2, 1), glow=True),          # front / back points
    part((5.5, 13.6, 7.6), (10.5, 14.4, 8.4), P(3, 1), dict(st, axis="z", angle=45), glow=True),
    part((5.5, 13.6, 7.6), (10.5, 14.4, 8.4), P(3, 1), dict(st, axis="z", angle=-45), glow=True),
    part((7, 13, 7), (9, 15, 9), P(0, 1), glow=True),                       # the heart
]
json.dump({"textures": {"all": "skyblocks:item/sky_wand_3d", "particle": "skyblocks:item/sky_wand"},
           "elements": wand, "display": held()}, open(os.path.join(ROOT, "models/item", "sky_wand.json"), "w"), indent=2)

palette("rift_knife_3d", {
    (0, 0): ((58, 52, 78), (40, 36, 56)),       # the blade: dark steel, a violet sheen
    (1, 0): ((236, 230, 255), (200, 192, 236)), # its cutting edge
    (2, 0): ((235, 120, 255), (200, 70, 255)),  # the rift line glowing down it
    (3, 0): ((196, 190, 220), (150, 142, 184)), # the guard: pale silver
    (0, 1): ((74, 40, 46), (54, 28, 34)),       # the grip
    (1, 1): ((176, 150, 220), (140, 112, 196)), # its silver wraps
    (2, 1): ((255, 140, 255), (210, 80, 255)),  # the pommel's gem
    (3, 1): ((30, 26, 42), (22, 18, 32)),       # the blade's spine
})
# A slim double-edged dagger: a dark blade with pale bevelled edges running straight up, then in to a sharp point (two
# bevels turned 22.5 degrees, a diamond at the tip); a thin glowing rift line down its middle; a narrow silver guard with
# its tips swept up; a slender wrapped grip; a little gem for a pommel. Each part one flat colour (a texel of its patch).


def T(c, r):
    return [c * 4 + 1.5, r * 4 + 1.5, c * 4 + 2.5, r * 4 + 2.5]


knife = [
    part((7.5, -0.25, 7.5), (8.5, 0.75, 8.5), T(2, 1), {"origin": [8, 0.25, 8], "axis": "y", "angle": 45}, glow=True),  # gem
    part((7.65, 0.6, 7.65), (8.35, 4.0, 8.35), T(0, 1)),                    # grip
    part((7.55, 1.2, 7.55), (8.45, 1.55, 8.45), T(1, 1)),                   # wraps
    part((7.55, 2.15, 7.55), (8.45, 2.5, 8.45), T(1, 1)),
    part((7.55, 3.1, 7.55), (8.45, 3.45, 8.45), T(1, 1)),
    part((7.6, 4.0, 6.6), (8.4, 4.5, 9.4), T(3, 0)),                        # guard
    part((7.65, 4.1, 5.9), (8.35, 4.45, 6.8), T(3, 0), {"origin": [8, 4.3, 6.6], "axis": "x", "angle": 22.5}),
    part((7.65, 4.1, 9.2), (8.35, 4.45, 10.1), T(3, 0), {"origin": [8, 4.3, 9.4], "axis": "x", "angle": -22.5}),
    part((7.75, 4.5, 7.45), (8.25, 12.5, 8.55), T(0, 0)),                   # the blade
    part((7.8, 4.5, 7.25), (8.2, 12.5, 7.45), T(1, 0)),                     # its edges
    part((7.8, 4.5, 8.55), (8.2, 12.5, 8.75), T(1, 0)),
    # the point: each edge bevelled in 22.5 degrees, the blade narrowing between them, a diamond at the tip
    part((7.74, 12.4, 7.25), (8.26, 13.6, 7.6), T(1, 0), {"origin": [8, 12.4, 7.25], "axis": "x", "angle": 22.5}),
    part((7.74, 12.4, 8.4), (8.26, 13.6, 8.75), T(1, 0), {"origin": [8, 12.4, 8.75], "axis": "x", "angle": -22.5}),
    part((7.76, 12.4, 7.55), (8.24, 12.9, 8.45), T(0, 0)),
    part((7.76, 12.9, 7.75), (8.24, 13.3, 8.25), T(0, 0)),
    part((7.78, 13.2, 7.76), (8.22, 13.7, 8.24), T(1, 0), {"origin": [8, 13.45, 8.0], "axis": "x", "angle": 45}),
    part((7.7, 5.1, 7.88), (8.3, 12.0, 8.12), T(2, 0), glow=True),          # the rift line
]
json.dump({"textures": {"all": "skyblocks:item/rift_knife_3d", "particle": "skyblocks:item/rift_knife"},
           "elements": knife, "display": held(scale=0.6)}, open(os.path.join(ROOT, "models/item", "rift_knife.json"), "w"), indent=2)
