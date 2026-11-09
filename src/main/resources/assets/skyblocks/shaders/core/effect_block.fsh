#version 150

// Shadered+'s effect block: one of its effects across the side, in the block's colours (Col0.., the first Count of
// them). uv: blocks from the group's anchor, so the blocks of a group show one picture. Every effect is Shadered+'s own:
// smooth noise, warped and folded, its colour changes softened by how big a pixel is (no shimmering far away).

in vec2 uv;
in float shade;
flat in float side;

uniform float Time;   // seconds
uniform float Effect; // which (EffectBlockEntity.EFFECTS)
uniform float Count;  // colours used, 1-8
uniform float Speed;  // 1 = normal
uniform float Size;   // 1 = normal
uniform float Bright; // 0.1-1
uniform float Pixel;  // 1: pixelated, 32 to a block
uniform float Blur;   // 0: crisp changes from colour to colour, 1: fully blended
uniform float Boost;  // the music's bass, 0-1
uniform vec3 Col0;
uniform vec3 Col1;
uniform vec3 Col2;
uniform vec3 Col3;
uniform vec3 Col4;
uniform vec3 Col5;
uniform vec3 Col6;
uniform vec3 Col7;

out vec4 fragColor;

const float TAU = 6.28318530718;
vec3 C[8];
int N;
float AA; // (how much one screen pixel covers, in pattern units: for soft edges)

vec3 colour(int i) {
    return C[int(mod(float(i), float(N)))];
}

// along the colours, round and back to the first, each change as soft as the blur says (never sharper than a pixel)
vec3 pal(float x, float edge) {
    float f = fract(x) * float(N);
    int i = int(floor(f));
    float w = max(mix(0.012, 0.5, Blur), AA * float(N) * 1.2);
    float k = smoothstep(0.5 - w, 0.5 + w, fract(f));
    return mix(colour(i), colour(i + 1), k);
}

// smooth all the way, each colour eased into the next (for changes over time)
vec3 palSmooth(float x) {
    float f = fract(x) * float(N);
    int i = int(floor(f));
    float k = fract(f);
    k = k * k * k * (k * (k * 6.0 - 15.0) + 10.0);
    return mix(colour(i), colour(i + 1), k);
}

vec2 hash2(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return -1.0 + 2.0 * fract(sin(p) * 43758.5453123);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// gradient noise, about -1..1
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float a = dot(hash2(i), f), b = dot(hash2(i + vec2(1.0, 0.0)), f - vec2(1.0, 0.0));
    float c = dot(hash2(i + vec2(0.0, 1.0)), f - vec2(0.0, 1.0)), d = dot(hash2(i + vec2(1.0, 1.0)), f - vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y) * 1.4;
}

const mat2 ROT = mat2(0.8, 0.6, -0.6, 0.8);

float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        s += a * noise(p);
        p = ROT * p * 2.03 + 17.1;
        a *= 0.5;
    }
    return s;
}

// a soft light over a surface from its slope: makes flat colours look like paint or liquid
float relief(float h, float strength) {
    vec2 g = vec2(dFdx(h), dFdy(h));
    return clamp(1.0 + dot(g, vec2(-0.7, 0.7)) * strength * 40.0, 0.75, 1.2);
}

// ---- the effects: p in pattern units (blocks / Size), t in seconds (times Speed) ----

// paint stirred round and round, a whirl in the middle, the colours folded into each other in bands (Balatro-like)
vec3 swirl(vec2 p, float t) {
    float r = length(p);
    float a = atan(p.y, p.x) + t * 0.16 - r * 0.42;
    vec2 w = vec2(cos(a), sin(a)) * r * 1.6;
    for (int k = 0; k < 4; k++) {
        float fk = float(k);
        w += 0.55 * vec2(sin(w.y * 1.1 + t * 0.29 + fk * 1.31), cos(w.x * 1.1 - t * 0.23 + fk * 0.73));
    }
    float v = 0.5 + 0.5 * sin(w.x * 0.9 + w.y * 0.7 + t * 0.1 + 0.4 * fbm(w * 0.6));
    vec3 c = pal(v, 0.07);
    float fold = 0.5 + 0.5 * sin((w.x - w.y) * 1.6);
    return c * (0.84 + 0.16 * fold) * relief(v, 0.35);
}

vec3 wave(vec2 p, float t) {
    float warp = 0.35 * fbm(p * 0.35 + vec2(t * 0.05, 0.0));
    float x = dot(p, normalize(vec2(1.0, 0.55))) * 0.22 - t * 0.09 + warp;
    vec3 c = pal(x, 0.0);
    float sheen = 0.9 + 0.1 * sin((p.x + p.y) * 2.0 - t * 1.5);
    return c * sheen;
}

vec3 cycle(vec2 p, float t) {
    vec3 c = palSmooth(t * 0.06);
    float glow = 0.88 + 0.12 * fbm(p * 0.5 + vec2(t * 0.1, -t * 0.07));
    return c * glow;
}

vec3 breathe(vec2 p, float t) {
    float k = t * 0.22;
    float b = 0.5 - 0.5 * cos(fract(k) * TAU); // in and out
    // (the colour changes while it's dark, eased from one to the next)
    vec3 c = mix(colour(int(floor(k))), colour(int(floor(k)) + 1), smoothstep(0.85, 1.0, fract(k)));
    float soft = 0.92 + 0.08 * fbm(p * 0.6 + t * 0.05);
    return c * (0.08 + 0.92 * b * b) * soft;
}

vec3 strobe(vec2 p, float t) {
    float k = t * 2.5;
    float f = fract(k);
    float flash = exp(-f * 9.0); // (a flash, fading fast)
    return colour(int(floor(k))) * (0.04 + 1.1 * flash);
}

// stars: twinkling points with a soft glow and a cross of light, over a dim drifting haze
vec3 sparkle(vec2 p, float t) {
    vec3 c = colour(0) * (0.06 + 0.07 * (0.5 + 0.5 * fbm(p * 0.7 + t * 0.03)));
    vec2 q = p * 2.5;
    for (int layer = 0; layer < 2; layer++) {
        vec2 cell = floor(q), f = fract(q);
        for (int y = -1; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                vec2 o = vec2(float(x), float(y));
                vec2 id = cell + o + side * 13.0 + float(layer) * 71.0;
                float h = hash(id);
                vec2 star = o + 0.5 + 0.35 * hash2(id + 4.0);
                vec2 d = f - star;
                float tw = pow(0.5 + 0.5 * sin(t * (1.0 + 2.0 * h) + h * 40.0), 6.0);
                float glow = 0.012 / (dot(d, d) + 0.002) * tw;
                float cross = max(0.0, 1.0 - abs(d.x) * 40.0) * max(0.0, 1.0 - abs(d.y) * 4.0)
                        + max(0.0, 1.0 - abs(d.y) * 40.0) * max(0.0, 1.0 - abs(d.x) * 4.0);
                c += colour(int(h * 97.0) + (N > 1 ? 1 : 0)) * (glow * 0.18 + cross * tw * 0.6);
            }
        }
        q = q * 1.7 + 3.3;
    }
    return c;
}

vec3 plasma(vec2 p, float t) {
    vec2 q = p * 0.9;
    float v = sin(q.x * 2.0 + t) + sin(q.y * 2.3 + t * 1.3) + sin((q.x + q.y) * 1.6 + t * 0.7)
            + sin(length(q * 2.0 + vec2(sin(t * 0.3), cos(t * 0.4)) * 3.0) - t);
    v = v * 0.125 + 0.5 + 0.12 * fbm(q + t * 0.1);
    return pal(v + t * 0.02, 0.0) * (0.85 + 0.25 * smoothstep(0.3, 1.0, abs(sin(v * TAU * 0.5))));
}

vec3 scanner(vec2 p, float t) {
    float x = p.x * 0.7 - t * 0.3;
    float d = fract(x) - 0.5;
    float line = exp(-d * d * 220.0) * 1.3 + exp(-d * d * 18.0) * 0.35;
    float trail = exp(-fract(-x + 0.5) * 3.0) * 0.25; // (a fading trail behind each line)
    vec3 c = colour(int(floor(x + 0.5))) * (line + trail);
    float y = p.y * 0.55 + t * 0.17;
    float e = fract(y) - 0.5;
    c += colour(int(floor(y + 0.5)) + 1) * (exp(-e * e * 260.0) * 0.8 + exp(-e * e * 20.0) * 0.15);
    vec2 g = abs(fract(p * 2.0) - 0.5);
    c += colour(0) * 0.05 * smoothstep(0.47, 0.5, max(g.x, g.y));
    return c + colour(0) * 0.03;
}

// water rings from three places, their waves crossing; lit by their slope
vec3 ripples(vec2 p, float t) {
    float h = 0.0;
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        vec2 c = vec2(sin(fk * 2.1 + t * 0.05), cos(fk * 1.7 - t * 0.04)) * 3.0 * fk;
        float r = length(p - c);
        h += sin(r * 4.0 - t * 1.6 + fk) / (1.0 + r * 0.25);
    }
    float x = length(p) * 0.12 - t * 0.04 + h * 0.03;
    vec3 c = pal(x, 0.0);
    return c * relief(h * 0.05, 1.0) * (0.85 + 0.15 * h);
}

vec3 gradient(vec2 p, float t) {
    vec2 q = p * 0.12 + 0.25 * vec2(fbm(p * 0.15 + t * 0.03), fbm(p * 0.15 - t * 0.025 + 5.0));
    return pal(q.x + q.y * 0.6 + t * 0.012, 0.0);
}

vec3 aurora(vec2 p, float t) {
    vec3 c = colour(N - 1) * 0.05;
    // (faint stars behind)
    vec2 sc = floor(p * 8.0);
    float s = hash(sc + side);
    c += vec3(0.6) * step(0.985, s) * (0.5 + 0.5 * sin(t * 2.0 + s * 50.0));
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float curtain = fbm(vec2(p.x * 0.35 + fk * 7.0, t * 0.06)) * 2.2 + sin(p.x * 0.4 + t * 0.15 + fk * 2.0) * 0.8;
        float y = p.y * 0.45 - curtain - fk * 0.9;
        float band = exp(-pow(fract(y * 0.5) - 0.5, 2.0) * 24.0);
        float rays = 0.55 + 0.45 * fbm(vec2(p.x * 6.0 + fk * 3.0, t * 0.4));
        float top = smoothstep(0.0, 0.5, fract(y * 0.5)); // (bright at its foot, fading upwards)
        c += pal(p.x * 0.05 + fk * 0.33 + t * 0.02, 0.0) * band * rays * mix(1.0, 0.45, top) * 0.85;
    }
    return c;
}

// liquid ink: colours flowing round each other, folding and spreading
vec3 ink(vec2 p, float t) {
    vec2 q = p * 0.55;
    vec2 a = vec2(fbm(q + vec2(0.0, t * 0.08)), fbm(q + vec2(5.2, 1.3) - t * 0.07));
    vec2 b = vec2(fbm(q + 3.0 * a + vec2(1.7, 9.2) + t * 0.05), fbm(q + 3.0 * a + vec2(8.3, 2.8) - t * 0.06));
    float v = fbm(q + 3.2 * b);
    vec3 c = pal(v * 0.9 + 0.5 + length(b) * 0.25, 0.18);
    float dark = smoothstep(-0.55, 0.1, v); // (deep shadows in the folds)
    return c * mix(0.25, 1.0, dark) * relief(v, 0.6);
}

// the music: bars rising with the bass, half a block wide, glowing at their tops; the rest breathing with it
vec3 music(vec2 b, float t) {
    float cell = floor(b.x * 2.0);
    float h = hash(vec2(cell, 7.0));
    float level = clamp(Boost * (0.55 + 0.45 * sin(t * 5.0 + h * TAU)) + 0.06 + 0.04 * sin(t * 1.7 + cell), 0.03, 1.0);
    float y = fract(b.y);
    float edge = max(fwidth(y) * 1.5, 0.004);
    float lit = 1.0 - smoothstep(level - edge, level + edge, y);
    vec3 c = pal(cell * 0.11 + y * 0.25 + t * 0.03, 0.0);
    float topGlow = exp(-abs(y - level) * 30.0);
    float gx = fract(b.x * 2.0);
    float gap = smoothstep(0.04, 0.1, gx) * smoothstep(0.04, 0.1, 1.0 - gx);
    vec3 bg = palSmooth(t * 0.05) * (0.05 + 0.15 * Boost);
    return bg + c * gap * (lit * (0.7 + 0.5 * Boost) + topGlow * 0.9);
}

void main() {
    C[0] = Col0; C[1] = Col1; C[2] = Col2; C[3] = Col3; C[4] = Col4; C[5] = Col5; C[6] = Col6; C[7] = Col7;
    N = int(clamp(Count, 1.0, 8.0) + 0.5);
    vec2 b = uv;
    if (Pixel > 0.5) {
        b = (floor(b * 32.0) + 0.5) / 32.0;
    }
    float size = max(Size, 0.05);
    vec2 p = b / size;
    AA = length(fwidth(p)) * 0.5;
    float t = Time * Speed;
    int e = int(Effect + 0.5);
    vec3 c;
    if (e == 0) c = swirl(p, t);
    else if (e == 1) c = wave(p, t);
    else if (e == 2) c = cycle(p, t);
    else if (e == 3) c = breathe(p, t);
    else if (e == 4) c = strobe(p, t);
    else if (e == 5) c = sparkle(p, t);
    else if (e == 6) c = plasma(p, t);
    else if (e == 7) c = scanner(p, t);
    else if (e == 8) c = ripples(p, t);
    else if (e == 9) c = gradient(p, t);
    else if (e == 10) c = aurora(p, t);
    else if (e == 11) c = music(b, t);
    else c = ink(p, t);
    // (a gentle curve: highlights roll off instead of clipping flat)
    c = max(c, 0.0);
    c = c / (1.0 + max(max(c.r, c.g), c.b) * 0.15) * 1.15;
    fragColor = vec4(clamp(c, 0.0, 1.0) * Bright * shade, 1.0);
}
