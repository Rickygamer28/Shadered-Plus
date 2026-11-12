"""The sky lantern's frame (its sky, inside the glass, is drawn in code: client/SkyLanternRenderer): a base plate,
corner posts, a cap and roof with a knob, and a handle on top standing; hanging, a chain instead of the handle.
Dark iron with brass rivets. Run from the repository root: python3 tools/models/sky_lantern/build.py"""
import json, os
import numpy as np
from PIL import Image

ROOT = "src/main/resources/assets/skyblocks"
rnd = np.random.RandomState(4)
Y, X = np.mgrid[0:16, 0:16].astype(np.float32)


def save(name, rgb):
    rgb = np.clip(rgb, 0, 255).astype(np.uint8)
    a = np.full(rgb.shape[:2] + (1,), 255, np.uint8)
    Image.fromarray(np.concatenate([rgb, a], 2), "RGBA").save(os.path.join(ROOT, "textures/block", name + ".png"))


iron = np.array([84, 86, 98], np.float32)
shade = 1.0 + 0.10 * (Y < 1) - 0.10 * (Y > 14) + rnd.uniform(-0.05, 0.05, (16, 16))
metal = iron[None, None, :] * shade[..., None]
metal[(X % 8 == 1) & (Y % 8 == 1)] = (196, 160, 88)  # (brass rivets)
save("sky_lantern_metal", metal)
cap = iron[None, None, :] * (0.9 + 0.25 * np.exp(-((X - 5) ** 2 + (Y - 5) ** 2) / 18) + rnd.uniform(-0.04, 0.04, (16, 16)))[..., None]
for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
    cap[y, x] = (196, 160, 88)
save("sky_lantern_cap", cap)


def face(tex, uv=None):
    f = {"texture": "#" + tex}
    if uv:
        f["uv"] = uv
    return f


ALL = ("north", "south", "east", "west", "up", "down")


def cube(a, b, tex="metal", over=None):
    over = over or {}
    return {"from": a, "to": b, "faces": {f: face(over.get(f, tex)) for f in ALL}}


def lantern(dy, hanging):
    up = lambda v: v + dy
    e = [cube([4, up(0), 4], [12, up(1), 12], over={"up": "cap", "down": "cap"}),
         cube([4.5, up(1), 4.5], [5.5, up(9), 5.5]), cube([10.5, up(1), 4.5], [11.5, up(9), 5.5]),
         cube([4.5, up(1), 10.5], [5.5, up(9), 11.5]), cube([10.5, up(1), 10.5], [11.5, up(9), 11.5]),
         # (a thin band round the glass halfway up)
         {"from": [4.75, up(4.75), 4.75], "to": [11.25, up(5.25), 11.25],
          "faces": {f: face("metal") for f in ("north", "south", "east", "west")}},
         cube([4, up(9), 4], [12, up(10), 12], over={"up": "cap", "down": "cap"}),
         cube([5, up(10), 5], [11, up(11), 11], over={"up": "cap"}),
         cube([6.5, up(11), 6.5], [9.5, up(12), 9.5], over={"up": "cap"})]
    if hanging:
        # the chain up from the knob (two crossed planes, as the game's hanging lantern has)
        e.append({"from": [6.5, up(12), 8], "to": [9.5, 16, 8], "rotation": {"angle": 45, "axis": "y", "origin": [8, 8, 8]}, "shade": False,
                  "faces": {"north": face("chain", [0, 0, 3, 16 - up(12)]), "south": face("chain", [0, 0, 3, 16 - up(12)])}})
        e.append({"from": [8, up(12), 6.5], "to": [8, 16, 9.5], "rotation": {"angle": 45, "axis": "y", "origin": [8, 8, 8]}, "shade": False,
                  "faces": {"east": face("chain", [3, 0, 6, 16 - up(12)]), "west": face("chain", [3, 0, 6, 16 - up(12)])}})
    else:
        # the handle
        e += [cube([6, 12, 7.75], [6.5, 14, 8.25]), cube([9.5, 12, 7.75], [10, 14, 8.25]), cube([6, 14, 7.75], [10, 14.5, 8.25])]
    return e


for name, dy, hanging in (("sky_lantern", 0, False), ("sky_lantern_hanging", 1, True)):
    m = {"parent": "block/block", "render_type": "minecraft:cutout",
         "textures": {"metal": "skyblocks:block/sky_lantern_metal", "cap": "skyblocks:block/sky_lantern_cap", "chain": "minecraft:block/chain",
                      "particle": "skyblocks:block/sky_lantern_metal"},
         "elements": lantern(dy, hanging)}
    json.dump(m, open(os.path.join(ROOT, "models/block", name + ".json"), "w"), indent=2)
json.dump({"variants": {"hanging=false": {"model": "skyblocks:block/sky_lantern"}, "hanging=true": {"model": "skyblocks:block/sky_lantern_hanging"}}},
          open(os.path.join(ROOT, "blockstates/sky_lantern.json"), "w"), indent=2)
json.dump({"parent": "skyblocks:block/sky_lantern"}, open(os.path.join(ROOT, "models/item/sky_lantern.json"), "w"), indent=2)
print("sky lantern built")
