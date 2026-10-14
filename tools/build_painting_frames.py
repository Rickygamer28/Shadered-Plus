"""Builds the Island Painting's frame styles: recoloured frame textures, the part models for each style, the multipart
blockstate (with a "frame" condition on every part) and a name-plaque texture per style.

Run from the project root: python3 tools/build_painting_frames.py
The oak frame is the original texture and models; the others are made from them.
"""
import copy
import json
import os

from PIL import Image

ROOT = os.path.join("src", "main", "resources", "assets", "skyblocks")
TEX = os.path.join(ROOT, "textures", "block")
MODELS = os.path.join(ROOT, "models", "block")
STATE = os.path.join(ROOT, "blockstates", "island_painting.json")
BASE_STATE = os.path.join("tools", "models", "island_painting_oak_blockstate.json")

PARTS = ["top", "bottom", "cw", "ccw", "corner_top_cw", "corner_top_ccw", "corner_bottom_cw",
         "corner_bottom_ccw", "particle"]

# style: (wood colour the brown parts become, trim colour the gold trim becomes)
STYLES = {
    "gold": ((232, 186, 58), (255, 244, 170)),
    "dark_oak": ((40, 26, 13), (118, 86, 52)),
    "prismarine": ((86, 156, 140), (160, 230, 214)),
    "quartz": ((232, 226, 218), (186, 176, 166)),
    "blackstone": ((48, 42, 50), (214, 170, 64)),
}
TRIM = {(0xD6, 0xAA, 0x40), (0xF0, 0xCD, 0x64)}
WOOD_MEAN = 0x56 * 0.299 + 0x38 * 0.587 + 0x22 * 0.114  # brightness of the average brown


def lum(c):
    return c[0] * 0.299 + c[1] * 0.587 + c[2] * 0.114


def tint(base, factor):
    return tuple(max(0, min(255, round(v * factor))) for v in base)


def build_texture(src, wood, trim):
    out = Image.new("RGBA", src.size)
    for y in range(src.size[1]):
        for x in range(src.size[0]):
            r, g, b, a = src.getpixel((x, y))
            if (r, g, b) in TRIM:
                factor = 1.08 if (r, g, b) == (0xF0, 0xCD, 0x64) else 0.94
                out.putpixel((x, y), tint(trim, factor) + (a,))
            else:
                factor = lum((r, g, b)) / WOOD_MEAN
                factor = 1 + (factor - 1) * 0.8  # keep the grain, a bit softer
                out.putpixel((x, y), tint(wood, factor) + (a,))
    return out


def clamp(c):
    return tuple(max(0, min(255, int(round(v)))) for v in c)


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def build_plaque(style):
    """One plate per frame style, 32x16 (stretched sideways in game, so the designs are mostly horizontal bands)."""
    import random
    rnd = random.Random("plaque-" + style)
    w, h = 32, 16
    img = Image.new("RGBA", (w, h))
    for y in range(h):
        t = y / (h - 1)
        for x in range(w):
            n = rnd.uniform(-1, 1)
            if style == "oak":  # brushed brass
                c = mix((222, 180, 90), (170, 130, 58), t)
                c = mix(c, c, 0)
                c = tuple(v + ((x * 7 + y * 13) % 5 - 2) * 2 for v in c)
                if y == 0: c = (248, 218, 132)
                if y == h - 1: c = (116, 84, 38)
            elif style == "gold":  # polished gold with engraved border lines
                c = mix((255, 226, 92), (214, 160, 30), t)
                c = tuple(v + n * 4 for v in c)
                if y <= 1: c = (255, 248, 196)
                if y in (3, h - 4): c = (176, 122, 18)
                if y == h - 1: c = (140, 94, 10)
            elif style == "dark_oak":  # dark wood with grain streaks
                c = mix((74, 50, 28), (52, 34, 18), t)
                grain = (y * 5 + (x // 6) * 3) % 7
                if grain == 0: c = tuple(v * 0.78 for v in c)
                if grain == 3: c = tuple(v * 1.12 for v in c)
                c = tuple(v + n * 3 for v in c)
                if y == 0: c = (104, 74, 44)
                if y == h - 1: c = (30, 19, 9)
            elif style == "prismarine":  # mottled sea-green stone
                c = mix((112, 186, 168), (70, 138, 122), t)
                if rnd.random() < 0.18: c = mix(c, (176, 236, 220), 0.5)
                if rnd.random() < 0.10: c = tuple(v * 0.85 for v in c)
                if y == 0: c = (186, 240, 226)
                if y == h - 1: c = (40, 96, 84)
            elif style == "quartz":  # white marble with faint grey veins
                c = mix((246, 242, 236), (220, 212, 204), t)
                vein = abs(((x * 0.35 + y * 1.7) % 6) - 3) < 0.35 or abs(((x * 0.22 - y * 2.3) % 9) - 4.5) < 0.3
                if vein: c = mix(c, (170, 164, 160), 0.45)
                c = tuple(v + n * 2 for v in c)
                if y == 0: c = (255, 255, 252)
                if y == h - 1: c = (178, 170, 162)
            else:  # blackstone: dark speckled stone with gold inlay lines
                c = mix((54, 48, 58), (34, 30, 38), t)
                if rnd.random() < 0.15: c = tuple(v * 1.35 for v in c)
                if rnd.random() < 0.08: c = tuple(v * 0.6 for v in c)
                if y in (2, h - 3): c = (214, 170, 64)
                if y == 0: c = (84, 76, 88)
                if y == h - 1: c = (18, 15, 20)
            img.putpixel((x, y), clamp(c) + (255,))
    return img


def build_rim():
    """White, tinted per style in game (the plaque's rim and its rivets)."""
    img = Image.new("RGBA", (4, 4))
    for y in range(4):
        for x in range(4):
            v = 255 - ((x + y) % 2) * 14
            img.putpixel((x, y), (v, v, v, 255))
    return img


def add_frame(cond, style):
    """Adds frame=style to a multipart condition (a plain one or an OR of plain ones)."""
    cond = copy.deepcopy(cond)
    if "OR" in cond:
        for term in cond["OR"]:
            term["frame"] = style
    else:
        cond["frame"] = style
    return cond


def model_name(model, style):
    return model if style == "oak" else model + "_" + style


def main():
    src = Image.open(os.path.join(TEX, "island_painting_frame.png")).convert("RGBA")
    for style, (wood, trim) in STYLES.items():
        build_texture(src, wood, trim).save(os.path.join(TEX, "island_painting_frame_" + style + ".png"))
    for style in ["oak"] + list(STYLES):
        build_plaque(style).save(os.path.join(TEX, "island_painting_plaque_" + style + ".png"))
    build_rim().save(os.path.join(TEX, "island_painting_plaque_rim.png"))
    old_plaque = os.path.join(TEX, "island_painting_plaque.png")
    if os.path.exists(old_plaque):
        os.remove(old_plaque)

    for style in STYLES:
        texture = "skyblocks:block/island_painting_frame_" + style
        for part in PARTS:
            with open(os.path.join(MODELS, "island_painting_" + part + ".json")) as f:
                model = json.load(f)
            for key in model.get("textures", {}):
                model["textures"][key] = texture
            with open(os.path.join(MODELS, "island_painting_" + part + "_" + style + ".json"), "w") as f:
                json.dump(model, f, indent=2)

    # The oak-only blockstate is kept as the source, so running this again doesn't add frames twice.
    if not os.path.exists(BASE_STATE):
        os.makedirs(os.path.dirname(BASE_STATE), exist_ok=True)
        with open(STATE) as f:
            base = json.load(f)
        with open(BASE_STATE, "w") as f:
            json.dump(base, f, indent=2)
    with open(BASE_STATE) as f:
        base = json.load(f)
    parts = []
    for style in ["oak"] + list(STYLES):
        for entry in base["multipart"]:
            new = copy.deepcopy(entry)
            new["when"] = add_frame(entry["when"], style) if "when" in entry else {"frame": style}
            new["apply"]["model"] = model_name(entry["apply"]["model"], style)
            parts.append(new)
    with open(STATE, "w") as f:
        json.dump({"multipart": parts}, f, indent=2)
    print("frames:", ", ".join(STYLES), "| parts:", len(parts))


if __name__ == "__main__":
    main()
