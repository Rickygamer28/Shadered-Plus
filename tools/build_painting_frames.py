"""Builds the Island Painting's frame styles: recoloured frame textures, the part models for each style, the multipart
blockstate (with a "frame" condition on every part) and the name-plaque texture.

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


def build_plaque():
    """A brass plate: soft vertical gradient, a light top edge and a dark bottom edge (stretched sideways in game)."""
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        t = y / 15
        base = (int(214 - 40 * t), int(172 - 38 * t), int(84 - 24 * t))
        if y == 0:
            base = (246, 214, 128)
        elif y == 15:
            base = (120, 88, 40)
        for x in range(16):
            n = ((x * 7 + y * 13) % 5 - 2) * 2  # faint brushed look
            img.putpixel((x, y), tuple(max(0, min(255, c + n)) for c in base) + (255,))
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
    build_plaque().save(os.path.join(TEX, "island_painting_plaque.png"))

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
