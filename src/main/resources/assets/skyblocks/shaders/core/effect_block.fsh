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
uniform vec3 Extent;  // the size of the block's group, in blocks (x, y, z)
uniform vec3 Bg;      // the background, behind the effects that light only part of the side
uniform vec4 BandsA;  // the music's 16 bands, low to high, 0-1
uniform vec4 BandsB;
uniform vec4 BandsC;
uniform vec4 BandsD;
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
float SZ; // (the size: 1 = normal)
int OCT;  // (more fine detail the bigger the size, so a big pattern stays sharp)

vec3 colour(int i) {
    return C[int(mod(float(i), float(N)))];
}

// along the colours, round and back to the first, each change as soft as the blur says (never sharper than a pixel)
vec3 pal(float x, float edge) {
    float f = fract(x) * float(N);
    int i = int(floor(f));
    float w = min(max(mix(0.012, 0.5, Blur), fwidth(x) * float(N) * 0.75), 0.5); // (never sharper than a pixel)
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
    for (int i = 0; i < 8; i++) {
        if (i >= OCT + 2) break;
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

// ---- the Balatro-like vortex: glossy paint pulled round into the middle ----

// three octaves only: broad, smooth shapes (liquid, not grain)
float fbm3(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 6; i++) {
        if (i >= OCT) break;
        s += a * noise(p);
        p = ROT * p * 2.03 + 17.1;
        a *= 0.5;
    }
    return s;
}

// how far out a point is in the paint, after the twist and the stirring (0 the middle, about 1 the outside)
float vortexField(vec2 p, float t) {
    float r = length(p);
    float a = atan(p.y, p.x) + 2.2 / (0.5 + r * 0.5) + t * 0.3; // (twisted harder towards the middle, turning)
    vec2 w = vec2(cos(a), sin(a)) * r;
    vec2 d = w * 0.7;
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        d += 0.38 * vec2(sin(d.y * 1.3 + t * 0.45 + fk * 1.7), cos(d.x * 1.3 - t * 0.38 + fk * 2.3));
    }
    float n = fbm3(d * 0.75 + vec2(t * 0.06, -t * 0.05));
    float streaks = 0.035 * sin(d.x * 3.1 + d.y * 2.3 + n * 4.0) + 0.02 * sin(d.y * 5.7 - d.x * 1.9); // (the long glossy ridges)
    return r * 0.26 + n * 0.45 + streaks;
}

vec3 swirl(vec2 p, float t) {
    float v = vortexField(p, t);
    float e = 0.04 / max(SZ, 1.0);
    float vx = vortexField(p + vec2(e, 0.0), t), vy = vortexField(p + vec2(0.0, e), t);
    // the colours from the middle out: the second; any from the third on, thin bands (the third the dark edge round
    // the middle); the first all round the outside
    float edge = max(mix(0.015, 0.25, Blur), AA * 2.0);
    vec3 c = colour(1);
    float x = 0.42;
    for (int k = 2; k < 8; k++) {
        if (k >= N) break;
        c = mix(c, colour(k), smoothstep(x - edge, x + edge, v));
        x += k == 2 ? 0.14 : 0.07;
    }
    c = mix(c, colour(0), smoothstep(x - edge, x + edge, v));
    if (N == 1) c = colour(0);
    // glossy: lit by the slope of the paint, with bright streaks where it catches the light
    vec3 n = normalize(vec3((v - vx) / e, (v - vy) / e, 0.6));
    vec3 L = normalize(vec3(-0.45, 0.55, 0.7));
    float diff = 0.55 + 0.55 * max(dot(n, L), 0.0);
    float spec = pow(max(dot(reflect(-L, n), vec3(0.0, 0.0, 1.0)), 0.0), 14.0);
    c *= diff;
    return c + mix(c, vec3(1.0), 0.5) * spec * 0.85;
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
    float k = t * 0.22 - length(p) * 0.08; // (the breath spreads out from the middle: slower across a bigger size)
    float b = 0.5 - 0.5 * cos(fract(k) * TAU); // in and out
    // (the colour changes while it's dark, eased from one to the next)
    vec3 c = mix(colour(int(floor(k))), colour(int(floor(k)) + 1), smoothstep(0.85, 1.0, fract(k)));
    float soft = 0.92 + 0.08 * fbm(p * 0.6 + t * 0.05);
    return c * (0.08 + 0.92 * b * b) * soft;
}

vec3 strobe(vec2 p, float t) {
    vec2 tile = floor(p * 0.5);
    float k = t * 2.5 + (SZ < 7.5 ? hash(tile + side * 3.0) * 7.0 : 0.0);
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
    float x = length(p) * 0.3 - t * 0.12 + h * 0.04;
    vec3 c = pal(x, 0.0);
    return c * relief(h * 0.3, 1.0) * (0.85 + 0.12 * h);
}

vec3 gradient(vec2 p, float t) {
    vec2 q = p * 0.12 + 0.25 * vec2(fbm(p * 0.15 + t * 0.03), fbm(p * 0.15 - t * 0.025 + 5.0));
    return pal(q.x + q.y * 0.6 + t * 0.012, 0.0);
}

vec3 aurora(vec2 p, float t) {
    vec3 c = vec3(0.0);
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
    vec2 q = p * 0.3;
    vec2 a = vec2(fbm3(q + vec2(0.0, t * 0.06)), fbm3(q + vec2(5.2, 1.3) - t * 0.05));
    vec2 b = vec2(fbm3(q + 2.2 * a + vec2(1.7, 9.2) + t * 0.04), fbm3(q + 2.2 * a + vec2(8.3, 2.8) - t * 0.045));
    float v = fbm3(q + 2.4 * b);
    vec3 c = pal(v * 1.4 + length(b) * 0.6 + 0.3, 0.0);
    float dark = smoothstep(-0.6, -0.1, v); // (shadows in the deepest folds)
    return c * mix(0.45, 1.0, dark) * relief(v, 0.5);
}

// the music: bars rising with the bass from the foot of the group to its top, half a block wide, glowing at their
// tops; the rest breathing with it. b: blocks from the group's middle along the side's axes
vec3 music(vec2 b, float t) {
    vec2 ext = side < 1.5 ? Extent.xz : side < 3.5 ? Extent.xy : Extent.zy;
    float y = clamp(b.y / max(ext.y, 1.0) + 0.5, 0.0, 1.0); // (0 at the group's foot, 1 at its top)
    float cell = floor(b.x * 2.0 / SZ);
    float h = hash(vec2(cell, 7.0));
    float h2 = hash(vec2(cell, 3.0));
    float level = clamp(Boost * (0.45 + 0.4 * sin(t * (4.0 + 3.0 * h2) + h * TAU) + 0.25 * h2)
            + 0.05 + 0.03 * sin(t * 1.7 + cell), 0.03, 1.0);
    float edge = max(fwidth(y) * 1.5, 0.002);
    float lit = 1.0 - smoothstep(level - edge, level + edge, y);
    vec3 c = pal(y * 0.9 + cell * 0.04 + t * 0.02, 0.0);
    float topGlow = exp(-abs(y - level) * 8.0 * max(ext.y, 1.0));
    float gx = fract(b.x * 2.0 / SZ);
    float gap = smoothstep(0.04, 0.1, gx) * smoothstep(0.04, 0.1, 1.0 - gx);
    float rows = smoothstep(0.03, 0.08, fract(y * ext.y * 4.0 / SZ)); // (lit in segments, like a meter)
    vec3 bg = palSmooth(t * 0.05) * (0.04 + 0.12 * Boost);
    return bg + c * gap * (lit * rows * (0.7 + 0.5 * Boost) + topGlow * 0.9);
}

// ---- (more) ----

// where on the side's group a point is: 0..1 across, 0..1 from its foot up; and the group's size along the side
vec2 groupPlace(vec2 b, out vec2 ext) {
    ext = side < 1.5 ? Extent.xz : side < 3.5 ? Extent.xy : Extent.zy;
    ext = max(ext, vec2(1.0));
    return clamp(b / ext + 0.5, 0.0, 1.0);
}

// flames rising from the foot of the group, hottest at the bottom: the colours from the coolest (the first) up
vec3 fire(vec2 b, vec2 p, float t) {
    vec2 ext;
    vec2 g = groupPlace(b, ext);
    float y = g.y * ext.y; // (blocks up from the foot)
    vec2 q = vec2(p.x * 1.3, (y / SZ - t * 1.4) * 0.9);
    float n = fbm(q + vec2(0.0, fbm(q * 0.7 + t * 0.2) * 0.8));
    float heat = 1.25 - y / max(ext.y * 0.85 * clamp(SZ, 0.4, 1.0), 1.0) + n * 0.75;
    heat -= 0.25 * fbm(vec2(p.x * 0.6, t * 0.3)); // (some tongues taller than others)
    float hot = clamp(heat, 0.0, 1.35);
    vec3 c = vec3(0.0);
    if (hot > 0.05) {
        // (each colour a crisp band of flame, the edge between them a pixel wide, or as soft as the blur)
        float x = clamp((hot - 0.05) / 1.0, 0.0, 0.999) * float(N);
        int i = int(floor(x));
        vec3 a = i == 0 ? colour(0) * 0.25 : colour(i - 1);
        float w = min(max(mix(0.04, 0.5, Blur), fwidth(x) * 0.75), 0.5);
        c = mix(a, colour(min(i, N - 1)), smoothstep(0.5 - w, 0.5 + w, fract(x)));
        c += vec3(1.0, 0.9, 0.7) * smoothstep(1.0, 1.35, hot) * 0.6; // (white-hot at the heart)
        c *= smoothstep(0.05, 0.3, hot);
    }
    return c;
}

// digital rain: columns of changing characters falling, a bright head and a fading tail
vec3 rain(vec2 b, float t) {
    float cols = 6.0 / SZ;
    vec2 cell = vec2(floor(b.x * cols), floor(b.y * cols));
    vec2 f = fract(b * cols);
    float col = cell.x + side * 31.0;
    float speed = 1.5 + 2.5 * hash(vec2(col, 1.0));
    float len = 6.0 + 14.0 * hash(vec2(col, 2.0));
    float head = -t * speed * 3.0 + hash(vec2(col, 3.0)) * 50.0;
    float d = mod(cell.y - head, len + 12.0);  // (how far above the head this cell is)
    float trail = d < len ? 1.0 - d / len : 0.0;
    // a 5 x 7 character, changing now and then
    vec2 px = floor(f * vec2(5.0, 7.0));
    float glyph = step(0.45, hash(px + cell * 7.3 + floor(t * (2.0 + 6.0 * hash(cell)))));
    glyph *= step(0.5, f.x * 5.0 - floor(f.x * 5.0) + 0.3) * step(0.1, f.y) * step(f.x, 0.95);
    vec3 ink = colour(int(hash(vec2(col, 5.0)) * float(N))); // (each column in one of the colours)
    vec3 c = ink * glyph * trail * 0.9;
    c += mix(ink, vec3(1.0), 0.75) * glyph * step(d, 0.99) * 1.2; // (the head, almost white)
    return c;
}

// a lava lamp: big soft blobs rising and sinking, joining and coming apart
vec3 lava(vec2 b, float t) {
    vec2 ext;
    vec2 g = groupPlace(b, ext);
    vec2 q = (g - 0.5) * ext; // (blocks from the middle)
    float field = 0.0;
    vec3 tint = vec3(0.0); // (each blob in one of the colours, blended where they join)
    for (int k = 0; k < 7; k++) {
        float fk = float(k);
        float x = (hash(vec2(fk, 1.0)) - 0.5) * ext.x * 0.8 + sin(t * 0.21 + fk * 2.0) * 0.4;
        float y = sin(t * (0.12 + 0.05 * hash(vec2(fk, 2.0))) + fk * 1.7) * ext.y * 0.42;
        float r = (0.3 + 0.3 * hash(vec2(fk, 3.0))) * min(max(ext.x, ext.y), 4.0) * 0.32 * SZ;
        vec2 d = q - vec2(x, y);
        float f = r * r / (dot(d, d) + 0.02);
        field += f;
        tint += colour(k) * f;
    }
    vec3 wax = tint / max(field, 1e-4);
    float edge = max(fwidth(field) * 1.5, 0.02);
    float blob = smoothstep(1.0 - edge, 1.0 + edge, field);
    vec3 glow = wax * 0.12 * clamp(field, 0.0, 1.0); // (the liquid lit by the wax near it)
    float rim = smoothstep(1.0, 1.6, field) * (1.0 - smoothstep(1.6, 3.0, field));
    float core = smoothstep(1.5, 4.0, field); // (brighter inside)
    return mix(glow, wax * (0.8 + 0.2 * rim + 0.2 * core), blob);
}

// stars streaking out from the middle, as if flying through them
vec3 warp(vec2 p, float t) {
    vec3 c = vec3(0.0);
    float r = length(p);
    float a = atan(p.y, p.x);
    for (int layer = 0; layer < 3; layer++) {
        float fl = float(layer);
        float slices = (70.0 + fl * 40.0) / clamp(sqrt(SZ), 0.5, 2.0);
        float sa = floor(a / TAU * slices);
        float h = hash(vec2(sa, fl));
        float speed = 0.6 + h * 0.8;
        float z = fract(h * 13.0 + t * speed * 0.25); // (how near: 0 far, 1 here)
        float sr = z * z * 9.0;                         // (stars speed up as they come)
        float len = 0.08 + z * z * 1.6;
        float along = (r - sr) / len;
        float streak = smoothstep(-1.0, 0.0, along) * (1.0 - smoothstep(0.0, 0.08, along));
        float across = abs(fract(a / TAU * slices) - 0.5);
        float thin = 1.0 - smoothstep(0.02, 0.12 + z * 0.1, across);
        c += colour(int(h * 31.0)) * streak * thin * (0.4 + 1.4 * z);
    }
    return c;
}

// a real equaliser: the low notes on the left, the high on the right, each bar as loud as its part of the music
vec3 spectrum(vec2 b, float t) {
    vec2 ext;
    vec2 g = groupPlace(b, ext);
    float bars = clamp(floor(ext.x * 2.0 / SZ), 2.0, 32.0);
    float i = floor(g.x * bars);
    float bi = floor(i / bars * 16.0);
    vec4 sel = bi < 4.0 ? BandsA : bi < 8.0 ? BandsB : bi < 12.0 ? BandsC : BandsD;
    float m = mod(bi, 4.0);
    float level = clamp(m < 0.5 ? sel.x : m < 1.5 ? sel.y : m < 2.5 ? sel.z : sel.w, 0.02, 1.0);
    float y = g.y;
    float edge = max(fwidth(y) * 1.5, 0.002);
    float lit = 1.0 - smoothstep(level - edge, level + edge, y);
    float gx = fract(g.x * bars);
    float gap = smoothstep(0.06, 0.14, gx) * smoothstep(0.06, 0.14, 1.0 - gx);
    float rows = smoothstep(0.04, 0.1, fract(y * ext.y * 4.0 / SZ));
    vec3 c = pal(y * 0.9 + 0.001, 0.0);
    float cap = exp(-abs(y - level) * 10.0 * ext.y);
    return c * gap * (lit * rows + cap * 0.8);
}

// one point of the side: its effect, over the background where it lights only part of it
vec3 effectAt(vec2 b, int e, float t) {
    if (Pixel > 0.5) {
        b = (floor(b * 32.0) + 0.5) / 32.0;
    }
    vec2 p = b / SZ;
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
    else if (e == 12) c = ink(p, t);
    else if (e == 13) c = fire(b, p, t);
    else if (e == 14) c = rain(b, t);
    else if (e == 15) c = lava(b, t);
    else if (e == 16) c = warp(p, t);
    else c = spectrum(b, t);
    c = max(c, 0.0);
    if (e == 3 || e == 4 || e == 5 || e == 7 || e == 10 || e == 11 || (e >= 13 && e <= 17)) {
        c += Bg * (1.0 - clamp(max(max(c.r, c.g), c.b) * 1.5, 0.0, 1.0));
    }
    return c;
}

void main() {
    C[0] = Col0; C[1] = Col1; C[2] = Col2; C[3] = Col3; C[4] = Col4; C[5] = Col5; C[6] = Col6; C[7] = Col7;
    N = int(clamp(Count, 1.0, 8.0) + 0.5);
    SZ = max(Size, 0.05);
    OCT = 3 + int(clamp(log2(max(SZ, 1.0)) + 0.5, 0.0, 3.0));
    vec2 b = uv;
    AA = length(fwidth(b / SZ)) * 0.5;
    float t = Time * Speed;
    int e = int(Effect + 0.5);
    vec3 c = effectAt(b, e, t);
    if (Blur > 0.01) {
        // blurred: the effect around this point too, softly weighted (up to a fifth of a block each way)
        float r = Blur * 0.2;
        c = c * 0.28 + (effectAt(b + vec2(r, r * 0.4), e, t) + effectAt(b + vec2(-r * 0.4, r), e, t)
                + effectAt(b + vec2(-r, -r * 0.4), e, t) + effectAt(b + vec2(r * 0.4, -r), e, t)) * 0.18;
    }
    // (a gentle curve: highlights roll off instead of clipping flat)
    c = c / (1.0 + max(max(c.r, c.g), c.b) * 0.15) * 1.15;
    fragColor = vec4(clamp(c, 0.0, 1.0) * Bright * shade, 1.0);
}
