#!/usr/bin/env python3
"""The Sky Studio's settings, in one place: writes
  src/main/java/com/example/skyblocks/StudioSpec.java        (the layers, their settings, where each is, the presets)
  src/main/resources/assets/skyblocks/shaders/include/studio_params.glsl  (uniforms P0.. and a #define per setting)
  src/main/resources/assets/skyblocks/shaders/core/studio_sky.json, studio_preview.json  (their uniforms)
Every setting is 0..1 (a colour: r g b). Run it after changing the list."""
import json, os

ROOT = os.path.join(os.path.dirname(__file__), '..')

# (layer id, has an on switch, [(setting id, 'c' colour | 'n' number, default)])
LAYERS = [
    ('global', False, [('brightness', 'n', 0.5), ('contrast', 'n', 0.5), ('saturation', 'n', 0.5), ('hue_shift', 'n', 0.0),
                       ('speed', 'n', 0.5), ('spin', 'n', 0.0), ('vignette', 'n', 0.0)]),
    ('gradient', True, [('top', 'c', '#120d2e'), ('middle', 'c', '#3b1f5c'), ('horizon', 'c', '#f08a5d'), ('ground', 'c', '#1a1030'),
                        ('horizon_height', 'n', 0.55), ('glow', 'n', 0.7), ('glow_width', 'n', 0.4), ('curve', 'n', 0.5)]),
    ('nebula', True, [('colour1', 'c', '#7c4dff'), ('colour2', 'c', '#ff6fae'), ('colour3', 'c', '#5ef2c2'), ('amount', 'n', 0.6),
                      ('scale', 'n', 0.5), ('drift', 'n', 0.25), ('softness', 'n', 0.8), ('detail', 'n', 0.5), ('brightness', 'n', 0.6),
                      ('swirl', 'n', 0.2)]),
    ('stars', True, [('colour', 'c', '#fff7e6'), ('variety', 'n', 0.3), ('density', 'n', 0.45), ('size', 'n', 0.3), ('twinkle', 'n', 0.65),
                     ('brightness', 'n', 0.8), ('milky_way', 'n', 0.3), ('band_tilt', 'n', 0.35), ('big_stars', 'n', 0.3)]),
    ('aurora', True, [('colour1', 'c', '#5ef2c2'), ('colour2', 'c', '#ff6fae'), ('brightness', 'n', 0.75), ('height', 'n', 0.35),
                      ('thickness', 'n', 0.4), ('wave_speed', 'n', 0.4), ('waviness', 'n', 0.5), ('rays', 'n', 0.4), ('bands', 'n', 0.6)]),
    ('moon', True, [('colour', 'c', '#f4efe1'), ('size', 'n', 0.4), ('height', 'n', 0.35), ('direction', 'n', 0.12), ('glow', 'n', 0.3),
                    ('round', 'n', 0.0), ('craters', 'n', 0.5), ('rings', 'n', 0.0), ('ring_colour', 'c', '#d8c7ff'), ('orbit', 'n', 0.0)]),
    ('planet', True, [('colour1', 'c', '#e0a56a'), ('colour2', 'c', '#8a4f3a'), ('size', 'n', 0.55), ('height', 'n', 0.45),
                      ('direction', 'n', 0.62), ('bands', 'n', 0.5), ('rings', 'n', 0.6), ('ring_colour', 'c', '#cdb99a'), ('glow', 'n', 0.3),
                      ('turn', 'n', 0.2)]),
    ('clouds', True, [('colour', 'c', '#f6d2e6'), ('shadow', 'c', '#5a3d78'), ('amount', 'n', 0.45), ('height', 'n', 0.3), ('scale', 'n', 0.5),
                      ('speed', 'n', 0.3), ('softness', 'n', 0.6)]),
    ('rays', True, [('colour', 'c', '#ffd9a8'), ('strength', 'n', 0.4), ('count', 'n', 0.5), ('spin', 'n', 0.2), ('width', 'n', 0.5)]),
    ('shooting', True, [('colour', 'c', '#ffffff'), ('how_often', 'n', 0.6), ('trail', 'n', 0.55), ('speed', 'n', 0.5), ('size', 'n', 0.4)]),
    ('mountains', True, [('colour', 'c', '#20143a'), ('far_colour', 'c', '#4a2f6e'), ('height', 'n', 0.4), ('roughness', 'n', 0.5),
                         ('layers', 'n', 0.6), ('haze', 'n', 0.5)]),
    ('grid', True, [('colour', 'c', '#ff5fd2'), ('spacing', 'n', 0.5), ('line_width', 'n', 0.3), ('scroll', 'n', 0.6), ('glow', 'n', 0.5),
                    ('sun', 'n', 0.0), ('sun_colour', 'c', '#ffb347')]),
    ('lake', True, [('tint', 'c', '#3b6fa8'), ('clarity', 'n', 0.6), ('ripples', 'n', 0.4), ('ripple_speed', 'n', 0.4)]),
    ('sparkles', True, [('colour', 'c', '#fff1b8'), ('amount', 'n', 0.4), ('size', 'n', 0.4), ('speed', 'n', 0.3), ('twinkle', 'n', 0.6)]),
]

# which layers are on in each preset, and its colours: (name, on, {layer.setting: value})
PRESETS = [
    ('dusk', ['gradient', 'nebula', 'stars', 'aurora', 'moon', 'shooting', 'grid'], {}),
    ('space', ['gradient', 'nebula', 'stars', 'moon', 'planet', 'shooting'],
     {'gradient.top': '#02030a', 'gradient.middle': '#0b1640', 'gradient.horizon': '#1b3a6b', 'gradient.ground': '#03050c',
      'nebula.colour1': '#3aa0ff', 'nebula.colour2': '#9b5cff', 'nebula.colour3': '#2bd6ff', 'stars.milky_way': 0.7,
      'gradient.glow': 0.2, 'moon.height': 0.55, 'moon.direction': 0.85}),
    ('neon', ['gradient', 'stars', 'aurora', 'shooting', 'grid', 'mountains'],
     {'gradient.top': '#0a0018', 'gradient.middle': '#2a0b4d', 'gradient.horizon': '#ff3d81', 'aurora.colour1': '#c6ff3d',
      'aurora.colour2': '#2bf0ff', 'grid.colour': '#2bf0ff', 'grid.sun': 0.7, 'mountains.colour': '#12002a', 'mountains.far_colour': '#3d0a5e'}),
    ('dawn', ['gradient', 'nebula', 'moon', 'clouds', 'rays', 'lake'],
     {'gradient.top': '#1d2b4f', 'gradient.middle': '#5b6fa8', 'gradient.horizon': '#ffc59e', 'nebula.colour1': '#ffd6e8',
      'nebula.colour2': '#a5d8ff', 'nebula.amount': 0.3, 'moon.colour': '#fff2c4', 'moon.round': 1.0, 'moon.height': 0.12,
      'moon.glow': 0.8, 'moon.craters': 0.0}),
    ('alien', ['gradient', 'nebula', 'stars', 'planet', 'mountains', 'sparkles'],
     {'gradient.top': '#06180f', 'gradient.middle': '#145236', 'gradient.horizon': '#b4ff6a', 'nebula.colour1': '#3dffb0',
      'nebula.colour2': '#d6ff3d', 'planet.colour1': '#7ad6ff', 'planet.colour2': '#2a5a9e', 'mountains.colour': '#04140c',
      'mountains.far_colour': '#0f3d2a'}),
    ('storm', ['gradient', 'clouds', 'rays', 'lake'],
     {'gradient.top': '#1a1f2b', 'gradient.middle': '#3b4558', 'gradient.horizon': '#8a95a8', 'clouds.colour': '#9aa5b8',
      'clouds.shadow': '#2a2f3a', 'clouds.amount': 0.8, 'rays.colour': '#dfe8ff', 'rays.strength': 0.3, 'lake.tint': '#2a3442'}),
    ('arctic', ['gradient', 'stars', 'aurora', 'mountains', 'lake'],
     {'gradient.top': '#020814', 'gradient.middle': '#0a2440', 'gradient.horizon': '#2a6a8a', 'aurora.colour1': '#3dffa0',
      'aurora.colour2': '#a06bff', 'aurora.brightness': 0.9, 'mountains.colour': '#0c1a2a', 'mountains.far_colour': '#2a4a66'}),
    ('inferno', ['gradient', 'nebula', 'sparkles', 'mountains', 'rays', 'moon'],
     {'gradient.top': '#1a0204', 'gradient.middle': '#5a0a08', 'gradient.horizon': '#ff7a1a', 'nebula.colour1': '#ff3d1a',
      'nebula.colour2': '#ffb01a', 'nebula.colour3': '#6a0a2a', 'sparkles.colour': '#ffb347', 'mountains.colour': '#140202',
      'moon.colour': '#ff5a1a', 'moon.round': 1.0, 'moon.craters': 0.2}),
]

def rgb(h):
    v = int(h[1:], 16)
    return [((v >> 16) & 255) / 255, ((v >> 8) & 255) / 255, (v & 255) / 255]

# where each setting is
fields = []  # (layer, setting, kind, offset)
offset = 0
on_index = {}
for layer, has_on, settings in LAYERS:
    if has_on:
        on_index[layer] = offset
        fields.append((layer, 'on', 'o', offset))
        offset += 1
    for sid, kind, default in settings:
        fields.append((layer, sid, kind, offset))
        offset += 3 if kind == 'c' else 1
SIZE = offset
VECS = (SIZE + 3) // 4

def defaults():
    out = [0.0] * SIZE
    for layer, has_on, settings in LAYERS:
        for sid, kind, default in settings:
            o = next(f[3] for f in fields if f[0] == layer and f[1] == sid)
            if kind == 'c':
                out[o:o + 3] = rgb(default)
            else:
                out[o] = default
    return out

def preset(on, values):
    out = defaults()
    for layer, o in on_index.items():
        out[o] = 1.0 if layer in on else 0.0
    for key, v in values.items():
        layer, sid = key.split('.')
        f = next(f for f in fields if f[0] == layer and f[1] == sid)
        if f[2] == 'c':
            out[f[3]:f[3] + 3] = rgb(v)
        else:
            out[f[3]] = v
    return out

# ---- GLSL ----
comp = 'xyzw'
def ref(i):
    return 'P%d.%s' % (i // 4, comp[i % 4])
glsl = ['// Written by tools/studio_spec.py: the Sky Studio\'s settings (StudioSpec.java has the same list).', '']
for v in range(VECS):
    glsl.append('uniform vec4 P%d;' % v)
glsl.append('')
for layer, sid, kind, o in fields:
    name = 'S_%s_%s' % (layer, sid)
    if kind == 'c':
        glsl.append('#define %s vec3(%s, %s, %s)' % (name, ref(o), ref(o + 1), ref(o + 2)))
    else:
        glsl.append('#define %s %s' % (name, ref(o)))
open(os.path.join(ROOT, 'src/main/resources/assets/skyblocks/shaders/include/studio_params.glsl'), 'w').write('\n'.join(glsl) + '\n')

# ---- shader JSONs ----
ident = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]
P = [{'name': 'P%d' % i, 'type': 'float', 'count': 4, 'values': [0.0] * 4} for i in range(VECS)]
base = [{'name': 'ModelViewMat', 'type': 'matrix4x4', 'count': 16, 'values': ident},
        {'name': 'ProjMat', 'type': 'matrix4x4', 'count': 16, 'values': ident},
        {'name': 'Time', 'type': 'float', 'count': 1, 'values': [0.0]}]
core = os.path.join(ROOT, 'src/main/resources/assets/skyblocks/shaders/core/')
json.dump({'vertex': 'skyblocks:shader_sky', 'fragment': 'skyblocks:studio_sky', 'uniforms': base + P}, open(core + 'studio_sky.json', 'w'), indent=2)
json.dump({'vertex': 'skyblocks:studio_preview', 'fragment': 'skyblocks:studio_preview',
           'uniforms': base + [{'name': 'View', 'type': 'float', 'count': 2, 'values': [0.0, 0.2]}] + P},
          open(core + 'studio_preview.json', 'w'), indent=2)

# ---- Java ----
def flt(x):
    return ('%.4f' % x).rstrip('0').rstrip('.') + 'F' if x != int(x) else '%d.0F' % x
j = ['package com.example.skyblocks;', '', '/**',
     ' * Written by tools/studio_spec.py: the Sky Studio\'s settings - its layers, each one\'s settings and where they are in',
     ' * a studio sky\'s numbers (StudioSkies), and the looks to start from. The shader has the same list',
     ' * (shaders/include/studio_params.glsl).', ' */', 'public final class StudioSpec {',
     '    /** How many numbers a studio sky has (each 0 .. 1), and in how many vec4s the shader gets them. */',
     '    public static final int SIZE = %d, VECS = %d;' % (SIZE, VECS), '',
     '    /** A setting: its name, kind (\'o\' the layer\'s switch, \'c\' a colour: 3 numbers, \'n\' a number), where it is. */',
     '    public record Field(String id, char kind, int at) {}', '',
     '    /** A layer: its name, where its switch is (-1: always on), its settings. */',
     '    public record Layer(String id, int on, Field[] fields) {}', '',
     '    public static final Layer[] LAYERS = {']
for layer, has_on, settings in LAYERS:
    fs = ', '.join('new Field("%s", \'%s\', %d)' % (f[1], f[2], f[3]) for f in fields if f[0] == layer and f[2] != 'o')
    j.append('            new Layer("%s", %d, new Field[] {%s}),' % (layer, on_index.get(layer, -1), fs))
j.append('    };')
j.append('')
j.append('    public static final String[] PRESET_NAMES = {%s};' % ', '.join('"%s"' % p[0] for p in PRESETS))
j.append('')
j.append('    /** The looks to start from (a slot starts as one of them). */')
j.append('    public static final float[][] PRESETS = {')
for name, on, values in PRESETS:
    vals = preset(on, values)
    lines = []
    for i in range(0, SIZE, 10):
        lines.append(', '.join(flt(round(x, 4)) for x in vals[i:i + 10]))
    j.append('            { // ' + name)
    for k, l in enumerate(lines):
        j.append('                ' + l + (',' if k < len(lines) - 1 else ''))
    j.append('            },')
j.append('    };')
j.append('')
j.append('    private StudioSpec() {}')
j.append('}')
open(os.path.join(ROOT, 'src/main/java/com/example/skyblocks/StudioSpec.java'), 'w').write('\n'.join(j) + '\n')

# ---- names (en_us), for the screen ----
L = os.path.join(ROOT, 'src/main/resources/assets/skyblocks/lang/en_us.json')
lang = json.load(open(L))
LAYER_NAMES = {'global': 'Whole sky', 'gradient': 'Sky colour', 'nebula': 'Nebula', 'stars': 'Stars', 'aurora': 'Aurora',
               'moon': 'Sun or moon', 'planet': 'Planet', 'clouds': 'Clouds', 'rays': 'Light rays', 'shooting': 'Shooting stars',
               'mountains': 'Mountains', 'grid': 'Horizon grid', 'lake': 'Lake', 'sparkles': 'Sparkles'}
for k, v in LAYER_NAMES.items():
    lang['screen.skyblocks.studio.layer.' + k] = v
for layer, sid, kind, o in fields:
    if kind != 'o':
        words = sid.replace('_', ' ')
        words = {'colour1': 'Colour 1', 'colour2': 'Colour 2', 'colour3': 'Colour 3'}.get(sid, words[0].upper() + words[1:])
        lang['screen.skyblocks.studio.field.' + sid] = words
for name, _, _ in PRESETS:
    lang['screen.skyblocks.studio.look.' + name] = name[0].upper() + name[1:]
json.dump(lang, open(L, 'w'), indent=2, ensure_ascii=False)

print('settings', SIZE, 'vec4s', VECS)
