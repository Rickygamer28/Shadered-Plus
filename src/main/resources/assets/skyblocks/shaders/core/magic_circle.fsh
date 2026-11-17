#version 150

// A lit magic circle. On the ground: an outer double ring with a band of runes between, turning slowly; inside, a ring,
// a six-pointed star turning the other way, and its rune's sign in the middle (feather, heart, shield, arrows, sprout,
// moon); all pulsing softly in its colour. Its wall: faint light rising off the ring, motes drifting up it. Light,
// added (alpha next to nothing, as shaders/core/spin_aura's).

uniform float GameTime;

in vec2 at;
flat in vec3 tint;
flat in int rune;
flat in int wall;

out vec4 fragColor;

const float TAU = 6.2831853;

float hash1(float n) {
    return fract(sin(n * 91.345) * 47453.5453);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float line(float d, float w) {
    float px = fwidth(d) + 1e-4;
    return 1.0 - smoothstep(w * 0.5, w * 0.5 + px, abs(d));
}

float seg(vec2 p, vec2 a, vec2 b) {
    vec2 ab = b - a;
    float s = clamp(dot(p - a, ab) / dot(ab, ab), 0.0, 1.0);
    return length(p - a - ab * s);
}

// a rune glyph: strokes on a 3 x 3 grid (seed), in a cell -0.5 .. 0.5
float glyph(vec2 q, float seed) {
    float v = 0.0;
    for (int i = 0; i < 5; i++) {
        if (hash1(seed * 13.1 + float(i) * 7.7) < 0.3) continue;
        vec2 a = (vec2(floor(hash1(seed + float(i)) * 3.0), floor(hash1(seed * 2.0 + float(i)) * 3.0)) - 1.0) * 0.35;
        vec2 b = (vec2(floor(hash1(seed + float(i) * 3.0) * 3.0), floor(hash1(seed * 5.0 + float(i)) * 3.0)) - 1.0) * 0.35;
        v = max(v, line(seg(q, a, b), 0.12));
    }
    return v;
}

// its rune's sign, in the middle (p: -1 .. 1 across it)
float sign_(vec2 p, int r) {
    float w = 0.09;
    if (r == 1) { // feather: a quill and its barbs
        float v = line(seg(p, vec2(-0.5, -0.6), vec2(0.45, 0.6)), w);
        for (int i = 0; i < 4; i++) {
            float k = -0.3 + float(i) * 0.22;
            vec2 c = vec2(-0.5, -0.6) + (vec2(0.95, 1.2)) * (k + 0.5);
            v = max(v, line(seg(p, c, c + vec2(-0.3, 0.12)), w * 0.8));
            v = max(v, line(seg(p, c, c + vec2(0.25, -0.18)), w * 0.8));
        }
        return v;
    } else if (r == 2) { // heart
        vec2 q = vec2(abs(p.x), p.y + 0.15);
        float d = length(q - vec2(0.27, 0.22)) - 0.3;
        float tip = seg(q, vec2(0.0, -0.62), vec2(0.5, 0.05));
        return max(line(d, w), line(tip, w));
    } else if (r == 3) { // ward: a shield
        vec2 q = vec2(abs(p.x), p.y);
        float v = line(seg(q, vec2(0.0, 0.6), vec2(0.5, 0.45)), w);
        v = max(v, line(seg(q, vec2(0.5, 0.45), vec2(0.45, -0.1)), w));
        v = max(v, line(seg(q, vec2(0.45, -0.1), vec2(0.0, -0.65)), w));
        return max(v, line(seg(p, vec2(0.0, 0.4), vec2(0.0, -0.4)), w * 0.8));
    } else if (r == 4) { // swift: arrows
        vec2 q = vec2(p.x, abs(p.y));
        float v = line(seg(q, vec2(-0.55, 0.45), vec2(-0.05, 0.0)), w);
        return max(v, line(seg(q, vec2(0.0, 0.45), vec2(0.5, 0.0)), w));
    } else if (r == 5) { // growth: a sprout
        float v = line(seg(p, vec2(0.0, -0.6), vec2(0.0, 0.3)), w);
        v = max(v, line(length(p - vec2(-0.25, 0.3)) - 0.25, w));
        return max(v, line(length(p - vec2(0.25, 0.4)) - 0.22, w));
    }
    // moon: a crescent
    return max(line(length(p) - 0.55, w), 0.0) * step(0.0, length(p - vec2(0.25, 0.15)) - 0.45)
         + line(length(p - vec2(0.25, 0.15)) - 0.45, w) * step(length(p) - 0.55, 0.0);
}

void main() {
    float t = GameTime * 1200.0;
    float pulse = 0.8 + 0.2 * sin(t * 0.12);
    vec3 col;
    if (wall == 1) {
        // the wall of light: brightest at the ground, fading up; motes drifting up it
        float up = at.y;
        float glow = exp(-up * 4.0) * 0.35;
        vec2 g = vec2(at.x, up * 6.0 - t * 0.03);
        vec2 id = floor(g), f = fract(g) - 0.5;
        float h = hash2(id);
        float mote = h > 0.75 ? smoothstep(0.12, 0.0, length(f - (vec2(hash2(id + 1.3), hash2(id + 2.7)) - 0.5) * 0.6)) : 0.0;
        col = tint * (glow + mote * (1.0 - up) * 0.9) * pulse;
    } else {
        vec2 p = at;
        float r = length(p);
        if (r > 1.0) discard;
        float a = atan(p.y, p.x);
        float v = 0.0;
        v += line(r - 0.97, 0.025) + line(r - 0.83, 0.02);
        // the rune band, turning
        float turn = t * 0.004;
        float n = 28.0;
        float u = fract((a / TAU) + turn) * n;
        vec2 q = vec2(fract(u) - 0.5, (r - 0.9) / 0.11);
        if (abs(q.y) < 0.5) {
            v = max(v, glyph(q * vec2(1.0, 1.1), floor(u) + float(rune) * 31.0) * 0.9);
        }
        v = max(v, line(r - 0.62, 0.018));
        // the star, turning the other way
        float sa = -t * 0.006;
        for (int k = 0; k < 6; k++) {
            vec2 c0 = vec2(cos(sa + float(k) * TAU / 3.0), sin(sa + float(k) * TAU / 3.0)) * 0.6;
            vec2 c1 = vec2(cos(sa + float(k + 1) * TAU / 3.0), sin(sa + float(k + 1) * TAU / 3.0)) * 0.6;
            if (k >= 3) {
                c0 = vec2(cos(sa + TAU / 6.0 + float(k) * TAU / 3.0), sin(sa + TAU / 6.0 + float(k) * TAU / 3.0)) * 0.6;
                c1 = vec2(cos(sa + TAU / 6.0 + float(k + 1) * TAU / 3.0), sin(sa + TAU / 6.0 + float(k + 1) * TAU / 3.0)) * 0.6;
            }
            v = max(v, line(seg(p, c0, c1), 0.014) * 0.85);
        }
        v = max(v, line(r - 0.3, 0.014) * 0.8);
        // its rune's sign in the middle
        v = max(v, sign_(p / 0.24, rune) * 1.1);
        // a soft glow over all of it
        v += exp(-r * 3.0) * 0.12;
        col = tint * v * pulse * 1.3;
    }
    if (max(col.r, max(col.g, col.b)) < 0.003) {
        discard;
    }
    fragColor = vec4(col, 1.0 / 255.0);
}
