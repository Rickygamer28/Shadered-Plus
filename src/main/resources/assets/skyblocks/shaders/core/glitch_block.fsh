#version 150

// A glitch block: each face a grid of patches, each flicking (every few frames, at its own rate) between - the world
// behind it torn sideways, its colours split; a wrong block texture (a random one off the block atlas); the
// missing-texture checks (magenta and black); static; black with scanlines. Now and then the whole face jolts. Sampler0:
// the picture behind; Sampler2: the block atlas.

uniform sampler2D Sampler0;
uniform sampler2D Sampler2;
uniform float GameTime;

in vec2 at;
flat in float face;
flat in float seed;

out vec4 fragColor;

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

void main() {
    float t = GameTime * 1200.0; // (ticks)
    vec2 screen = vec2(textureSize(Sampler0, 0));
    vec2 su = gl_FragCoord.xy / screen;
    // its time, in uneven steps (a glitch doesn't keep time)
    float step_ = floor(t * 0.7 + hash(vec3(seed, face, 3.0)) * 10.0);
    float jolt = step(0.9, hash(vec3(floor(t * 0.35), seed, 9.0)));
    vec2 uv = at;
    if (jolt > 0.5) {
        uv.x = fract(uv.x + (hash(vec3(floor(uv.y * 8.0), step_, seed)) - 0.5) * 0.5);
    }
    float n = 4.0;
    vec2 cell = floor(uv * n);
    vec2 inCell = fract(uv * n);
    float rate = 1.0 + floor(hash(vec3(cell, seed + face)) * 4.0);
    float when = floor(step_ / rate);
    float h = hash(vec3(cell + face * 7.0, when + seed * 0.37));
    vec3 col;
    if (h < 0.3) {
        // the world behind, torn sideways in slices, its colours split
        float row = floor(su.y * screen.y / 6.0);
        float shift = (hash(vec3(row, when, seed)) - 0.5) * 0.08;
        float split = 0.006 + 0.01 * hash(vec3(cell, when));
        col.r = texture(Sampler0, su + vec2(shift + split, 0.0)).r;
        col.g = texture(Sampler0, su + vec2(shift, 0.0)).g;
        col.b = texture(Sampler0, su + vec2(shift - split, 0.0)).b;
    } else if (h < 0.62) {
        // a wrong texture: some block's off the atlas (blank places on it: the checks instead)
        vec2 atlas = vec2(textureSize(Sampler2, 0));
        vec2 tiles = floor(atlas / 16.0);
        vec2 tile = floor(vec2(hash(vec3(cell, when + 1.0)), hash(vec3(cell, when + 2.0))) * tiles);
        vec4 tex = texture(Sampler2, (tile * 16.0 + floor(inCell * 16.0) + 0.5) / atlas);
        vec2 ch = floor(inCell * 2.0);
        vec3 checks = mod(ch.x + ch.y, 2.0) < 0.5 ? vec3(0.97, 0.0, 0.86) : vec3(0.0);
        col = tex.a > 0.5 ? tex.rgb : checks;
        // (posterised, its colours off)
        col = floor(col * 5.0) / 5.0;
        if (hash(vec3(cell, when + 5.0)) > 0.7) col = col.gbr;
    } else if (h < 0.78) {
        // the missing-texture checks
        vec2 ch = floor(inCell * 2.0);
        col = mod(ch.x + ch.y, 2.0) < 0.5 ? vec3(0.97, 0.0, 0.86) : vec3(0.0);
    } else if (h < 0.9) {
        // static
        float s = hash(vec3(floor(inCell * 16.0), floor(t * 2.0) + cell.x * 3.1 + cell.y));
        col = vec3(s);
    } else {
        // black, scanlines
        col = vec3(0.02) + vec3(0.25, 0.0, 0.3) * step(0.5, fract(inCell.y * 8.0 + t * 0.1));
    }
    // a bright line across now and then
    col += vec3(0.6, 1.0, 0.9) * step(0.985, hash(vec3(floor(uv.y * 40.0), step_, seed))) * 0.7;
    fragColor = vec4(col, 1.0);
}
