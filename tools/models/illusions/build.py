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

# the spin pad: a low base (its plate turns on top, drawn in code)
model("spin_pad", {"side": "skyblocks:block/spin_pad_side", "top": "skyblocks:block/spin_pad_top", "particle": "skyblocks:block/spin_pad_side"},
      [{"from": [0, 0, 0], "to": [16, 4, 16], "faces": {f: dict(face("top" if f in ("up", "down") else "side"), **({"uv": [0, 6, 16, 10]} if f not in ("up", "down") else {}))
                                                          for f in ALL}}])
item("spin_pad", {"parent": "skyblocks:block/spin_pad"})
state("spin_pad", {"variants": {"": {"model": "skyblocks:block/spin_pad"}}})

# the dark fountain: nothing but its particles' texture (it's all drawn in code)
json.dump({"textures": {"particle": "minecraft:block/black_concrete"}}, open(os.path.join(ROOT, "models/block/dark_fountain.json"), "w"), indent=2)
state("dark_fountain", {"variants": {"": {"model": "skyblocks:block/dark_fountain"}}})

# the items drawn flat
item("sky_wand", {"parent": "minecraft:item/handheld", "textures": {"layer0": "skyblocks:item/sky_wand"}})
item("rift_knife", {"parent": "minecraft:item/handheld", "textures": {"layer0": "skyblocks:item/rift_knife"}})
