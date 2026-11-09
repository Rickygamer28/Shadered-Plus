#version 150

// Shadered+'s effect block: one of its effects across the side, in the block's colours (Col0.., the first Count of
// them). uv: blocks from the group's anchor, so the blocks of a group show one picture. Every effect is Shadered+'s own.

in vec2 uv;
in float shade;
flat in float side;

uniform float Time;   // seconds
uniform float Effect; // which (client/EffectBlockRenderer, EffectBlockEntity.EFFECTS)
uniform float Count;  // colours used, 1-8
uniform float Speed;  // 1 = normal
uniform float Size;   // 1 = normal
uniform float Bright; // 0.1-1
uniform float Pixel;  // 1: pixelated, 16 to a block
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

vec3 colour(int i) {
    return C[int(mod(float(i), float(N)))];
}

// along the colours, round and back to the first: soft (edge 0) to hard (edge near 0.5) changes between them
vec3 pal(float x, float edge) {
    float f = fract(x) * float(N);
    int i = int(floor(f));
    float k = smoothstep(edge, 1.0 - edge, fract(f));
    return mix(colour(i), colour(i + 1), k);
}

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

// ---- the effects: p in pattern units (blocks / 3 / Size), t in seconds (times Speed) ----

// paint stirred round and round: a slow whirl in the middle, the colours folded into each other in bands
vec3 swirl(vec2 p, float t) {
    float r = length(p);
    float a = atan(p.y, p.x) + t * 0.18 - r * 0.55 + 0.35 * sin(t * 0.13);
    vec2 w = vec2(cos(a), sin(a)) * r * 2.4;
    vec2 z = w;
    for (int k = 0; k < 5; k++) {
        float fk = float(k);
        z += 0.62 * vec2(sin(z.y * 0.85 + t * 0.31 + fk * 1.31), cos(z.x * 0.85 - t * 0.27 + fk * 0.73));
    }
    float v = 0.5 + 0.5 * sin(z.x * 0.55 + z.y * 0.45 + t * 0.12);
    float bands = v * 0.999 + 0.07 * sin(length(z) * 0.8 - t * 0.2);
    vec3 c = pal(bands, 0.38);
    float fold = 0.5 + 0.5 * sin((z.x - z.y) * 1.3);
    return c * (0.82 + 0.18 * fold);
}

vec3 wave(vec2 p, float t) {
    float x = dot(p, normalize(vec2(1.0, 0.55))) * 0.5 - t * 0.12 + 0.05 * sin(p.y * 2.0 + t);
    return pal(x, 0.1);
}

vec3 cycle(vec2 p, float t) {
    return pal(t * 0.08 + length(p) * 0.015, 0.0);
}

vec3 breathe(vec2 p, float t) {
    float k = t * 0.25;
    float b = sin(fract(k) * 3.14159);
    return colour(int(floor(k))) * (0.12 + 0.88 * b * b);
}

vec3 strobe(vec2 p, float t) {
    float k = t * 3.0;
    return colour(int(floor(k))) * step(fract(k), 0.45);
}

vec3 sparkle(vec2 cell, float t) {
    float h = hash(cell + side * 17.31);
    float h2 = hash(cell * 1.7 + 3.1 + side);
    float tw = pow(max(sin(t * (1.2 + 2.2 * h) + h * 40.0), 0.0), 14.0);
    vec3 base = colour(0) * 0.16;
    return base + colour(int(h2 * float(N)) + (N > 1 ? 1 : 0)) * tw * 1.4;
}

vec3 plasma(vec2 p, float t) {
    float v = sin(p.x * 3.0 + t) + sin(p.y * 3.0 + t * 1.3) + sin((p.x + p.y) * 2.2 + t * 0.7)
            + sin(length(p * 3.0 + vec2(sin(t * 0.3), cos(t * 0.4)) * 2.0) - t);
    return pal(v * 0.125 + 0.5 + t * 0.02, 0.0);
}

vec3 scanner(vec2 p, float t) {
    float x = p.x * 1.2 - t * 0.35;
    float d = fract(x) - 0.5;
    float line = exp(-d * d * 120.0) + 0.5 * exp(-d * d * 12.0);
    vec3 c = colour(int(floor(x)));
    float y = p.y * 1.2 + t * 0.21;
    float e = fract(y) - 0.5;
    float line2 = exp(-e * e * 160.0);
    return c * (0.08 + 1.1 * line) + colour(int(floor(y)) + 1) * line2 * 0.6;
}

vec3 ripples(vec2 p, float t) {
    float x = length(p) * 1.4 - t * 0.35;
    return pal(x, 0.15) * (0.55 + 0.45 * cos(fract(x) * TAU));
}

vec3 gradient(vec2 p, float t) {
    return pal(p.x * 0.18 + p.y * 0.11 + t * 0.015, 0.0);
}

vec3 aurora(vec2 p, float t) {
    vec3 sky = colour(N - 1) * 0.07;
    vec3 c = sky;
    for (int k = 0; k < 3; k++) {
        float fk = float(k);
        float curtain = sin(p.x * (1.1 + fk * 0.4) + t * (0.25 + fk * 0.1) + fk * 2.0) * 0.6
                + noise(vec2(p.x * 1.5 + fk * 7.0, t * 0.2)) * 0.8;
        float y = p.y * 0.8 - curtain - fk * 0.7;
        float glow = exp(-pow(fract(y * 0.5) - 0.5, 2.0) * 30.0);
        float streak = 0.6 + 0.4 * sin(p.x * 23.0 + noise(vec2(p.x * 4.0, t)) * 6.0);
        c += pal(p.x * 0.07 + fk * 0.3 + t * 0.03, 0.0) * glow * streak * 0.75;
    }
    return c;
}

// the music: bars rising with the bass in every block, half a block wide
vec3 music(vec2 b, float t) {
    float cell = floor(b.x * 2.0);
    float h = hash(vec2(cell, 7.0));
    float level = clamp(Boost * (0.55 + 0.45 * sin(t * 6.0 + h * TAU)) + 0.08 * sin(t * 2.0 + cell), 0.04, 1.0);
    float y = fract(b.y);
    float lit = step(y, level);
    vec3 c = pal(cell * 0.125 + t * 0.03, 0.0);
    float top = smoothstep(level - 0.06, level, y) * lit;
    float gap = step(0.08, fract(b.x * 2.0)) * step(0.08, fract(y * 8.0));
    return c * (0.1 + lit * gap * (0.85 + 0.6 * top)) * (0.7 + 0.5 * Boost);
}

void main() {
    C[0] = Col0; C[1] = Col1; C[2] = Col2; C[3] = Col3; C[4] = Col4; C[5] = Col5; C[6] = Col6; C[7] = Col7;
    N = int(clamp(Count, 1.0, 8.0) + 0.5);
    vec2 b = uv;
    if (Pixel > 0.5) {
        b = (floor(b * 16.0) + 0.5) / 16.0;
    }
    vec2 p = b / (3.0 * max(Size, 0.05));
    float t = Time * Speed;
    int e = int(Effect + 0.5);
    vec3 c;
    if (e == 0) c = swirl(p, t);
    else if (e == 1) c = wave(p, t);
    else if (e == 2) c = cycle(p, t);
    else if (e == 3) c = breathe(p, t);
    else if (e == 4) c = strobe(p, t);
    else if (e == 5) c = sparkle(floor(b * 16.0 / max(Size, 0.25)), t);
    else if (e == 6) c = plasma(p, t);
    else if (e == 7) c = scanner(p, t);
    else if (e == 8) c = ripples(p, t);
    else if (e == 9) c = gradient(p, t);
    else if (e == 10) c = aurora(p, t);
    else c = music(b, t);
    fragColor = vec4(clamp(c, 0.0, 1.0) * Bright * shade, 1.0);
}
