"""Models and textures for the ferrofluid display (a brass-rimmed dish on a dark stand: its fluid is drawn in code),
the sky clock (a round brass frame on the wall: its face is drawn in code) and the prism (a little wooden stand: its
glass is drawn in code). Run from the repository root: python3 tools/models/gadgets/build.py"""
import json, math, os
import numpy as np
from PIL import Image

ROOT = "src/main/resources/assets/skyblocks"
rnd = np.random.RandomState(9)
Y, X = np.mgrid[0:16, 0:16].astype(np.float32)


def save(name, rgb, alpha=None):
    rgb = np.clip(rgb, 0, 255).astype(np.uint8)
    a = np.full(rgb.shape[:2] + (1,), 255, np.uint8) if alpha is None else np.clip(alpha, 0, 255).astype(np.uint8)[..., None]
    Image.fromarray(np.concatenate([rgb, a], 2), "RGBA").save(os.path.join(ROOT, "textures/block", name + ".png"))


def tex(base, shade):
    return np.array(base, np.float32)[None, None, :] * shade[..., None]


save("gadget_brass", tex((214, 170, 86), 0.9 + 0.18 * np.exp(-((X - 4) ** 2 + (Y - 4) ** 2) / 30) + rnd.uniform(-0.04, 0.04, (16, 16))))
save("gadget_dark_metal", tex((52, 54, 62), 0.92 + 0.12 * (Y < 2) + rnd.uniform(-0.05, 0.05, (16, 16))))
save("gadget_walnut", tex((112, 72, 44), 0.92 + 0.07 * np.sin(Y * 1.9 + np.sin(X * 0.5) * 2.0) + rnd.uniform(-0.03, 0.03, (16, 16))))
# the prism's glass: clear with a faint sheen, its edges catching the light
edge = (X < 1) | (X > 14) | (Y < 1) | (Y > 14)
save("prism_glass", tex((236, 244, 255), 1.0 - 0.04 * (Y / 16) + 0 * X), alpha=np.where(edge, 190, 70) + 20 * np.exp(-((X - Y) ** 2) / 6))

ALL = ("north", "south", "east", "west", "up", "down")


def face(t, cull=None):
    f = {"texture": "#" + t}
    if cull:
        f["cullface"] = cull
    return f


def cube(a, b, t, over=None, skip=()):
    over = over or {}
    return {"from": [round(v, 4) for v in a], "to": [round(v, 4) for v in b], "faces": {f: face(over.get(f, t)) for f in ALL if f not in skip}}


def write(name, textures, elements, extra=None):
    m = {"parent": "block/block", "textures": textures, "elements": elements}
    m.update(extra or {})
    json.dump(m, open(os.path.join(ROOT, "models/block", name + ".json"), "w"), indent=2)
    json.dump({"parent": "skyblocks:block/" + name}, open(os.path.join(ROOT, "models/item", name + ".json"), "w"), indent=2)


# -- the ferrofluid display: a foot, a stem, the dish (its floor below the fluid, brass walls round it, a rim on top)
ferro = [cube((3, 0, 3), (13, 1, 13), "dark"), cube((4, 1, 4), (12, 1.5, 12), "dark"), cube((6, 1.5, 6), (10, 2.5, 10), "dark"),
         cube((2, 2.5, 2), (14, 3.5, 14), "dark")]
for (a, b) in (((1.5, 2.5, 1.5), (14.5, 5, 2.5)), ((1.5, 2.5, 13.5), (14.5, 5, 14.5)), ((1.5, 2.5, 2.5), (2.5, 5, 13.5)), ((13.5, 2.5, 2.5), (14.5, 5, 13.5))):
    ferro.append(cube(a, b, "brass"))
write("ferrofluid", {"brass": "skyblocks:block/gadget_brass", "dark": "skyblocks:block/gadget_dark_metal", "particle": "skyblocks:block/gadget_dark_metal"}, ferro)
json.dump({"variants": {"": {"model": "skyblocks:block/ferrofluid"}}}, open(os.path.join(ROOT, "blockstates/ferrofluid.json"), "w"), indent=2)

# -- the sky clock (made facing south: on a wall at z 0): a walnut disc behind, a brass ring round the face, a little
# brass crown on top
clock = []
for row in range(1, 15):
    dy = row + 0.5 - 8
    w7 = math.sqrt(max(0.0, 49 - dy * dy))
    w6 = math.sqrt(max(0.0, 36 - dy * dy))
    w7 = round(w7 * 2) / 2
    w6 = round(w6 * 2) / 2
    if w7 <= 0:
        continue
    clock.append(cube((8 - w7, row, 0), (8 + w7, row + 1, 1), "walnut"))
    if w6 <= 0:
        clock.append(cube((8 - w7, row, 1), (8 + w7, row + 1, 2), "brass"))
    else:
        clock.append(cube((8 - w7, row, 1), (8 - w6, row + 1, 2), "brass"))
        clock.append(cube((8 + w6, row, 1), (8 + w7, row + 1, 2), "brass"))
clock.append(cube((7, 14.5, 0.5), (9, 15.5, 1.5), "brass"))
write("sky_clock", {"brass": "skyblocks:block/gadget_brass", "walnut": "skyblocks:block/gadget_walnut", "particle": "skyblocks:block/gadget_brass"}, clock)
json.dump({"variants": {"facing=south": {"model": "skyblocks:block/sky_clock"}, "facing=west": {"model": "skyblocks:block/sky_clock", "y": 90},
                        "facing=north": {"model": "skyblocks:block/sky_clock", "y": 180}, "facing=east": {"model": "skyblocks:block/sky_clock", "y": 270}}},
          open(os.path.join(ROOT, "blockstates/sky_clock.json"), "w"), indent=2)

# -- the prism's stand (made with the prism along x): a walnut base, two brass cradles holding its ends
prism = [cube((3, 0, 4), (13, 1.5, 12), "walnut"), cube((2.5, 1.5, 6.5), (3.5, 4, 9.5), "brass"), cube((12.5, 1.5, 6.5), (13.5, 4, 9.5), "brass"),
         cube((3.5, 1.5, 5), (12.5, 2.5, 11), "walnut")]
write("prism", {"brass": "skyblocks:block/gadget_brass", "walnut": "skyblocks:block/gadget_walnut", "particle": "skyblocks:block/prism_glass"}, prism)
json.dump({"variants": {"axis=x": {"model": "skyblocks:block/prism"}, "axis=z": {"model": "skyblocks:block/prism", "y": 90}}},
          open(os.path.join(ROOT, "blockstates/prism.json"), "w"), indent=2)
print("gadgets built")
