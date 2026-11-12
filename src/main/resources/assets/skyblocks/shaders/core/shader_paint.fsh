#version 150

// Each style the look of one of Shadered+'s shader blocks, drawn across the item's texture (p: 0 .. 1 across it, in
// steps a quarter of its pixels, so it keeps the blocky look), moving with time.

in vec2 uv;
flat in int style;
flat in float texels;

uniform float GameTime;

out vec4 fragColor;

const float PI = 3.14159265;

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

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

vec3 hsv(float h, float s, float v) {
    vec3 k = clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
    return v * mix(vec3(1.0), k, s);
}

// distance to the nearest cell edge of a moving voronoi pattern (cracks, crystals)
vec2 voronoi(vec2 p, float t) {
    vec2 i = floor(p), f = fract(p);
    float d1 = 8.0, d2 = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(float(x), float(y));
            vec2 o = vec2(hash(i + g), hash(i + g + 17.0));
            o = 0.5 + 0.4 * sin(t + 6.2831 * o);
            float d = length(g + o - f);
            if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) { d2 = d; }
        }
    }
    return vec2(d1, d2 - d1);
}

vec3 paint(int s, vec2 p, float t) {
    vec2 c = p - 0.5;
    float r = length(c), a = atan(c.y, c.x);
    if (s == 1) { // galaxy
        vec2 q = p * 3.0 + vec2(t * 0.03, t * 0.02);
        float n = fbm(q + fbm(q * 1.7));
        vec3 col = mix(vec3(0.02, 0.01, 0.08), vec3(0.35, 0.1, 0.55), n);
        col += vec3(0.1, 0.35, 0.6) * pow(fbm(q * 2.3 - 3.0), 3.0) * 2.0;
        vec2 sp = floor(p * texels * 2.0);
        float st = hash(sp);
        col += vec3(1.0) * step(0.93, st) * (0.5 + 0.5 * sin(t * 3.0 + st * 60.0));
        return col;
    }
    if (s == 2) { // lava lamp
        float f = 0.0;
        for (int i = 0; i < 4; i++) {
            float fi = float(i);
            vec2 b = vec2(0.25 + 0.5 * hash(vec2(fi, 3.0)), 0.5 + 0.45 * sin(t * (0.25 + 0.1 * fi) + fi * 2.0));
            f += 0.018 / max(dot(p - b, p - b), 0.0005);
        }
        vec3 bg = mix(vec3(0.35, 0.05, 0.3), vec3(0.6, 0.15, 0.25), p.y);
        return mix(bg, mix(vec3(1.0, 0.45, 0.05), vec3(1.0, 0.85, 0.3), clamp(f - 1.0, 0.0, 1.0)), smoothstep(0.9, 1.1, f));
    }
    if (s == 3) { // plasma ball
        float arcs = 0.0;
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float w = sin(a * 3.0 + fi * 2.1 + t * (1.0 + fi * 0.3) + fbm(vec2(r * 6.0, t + fi)) * 4.0);
            arcs += pow(1.0 - abs(w), 18.0);
        }
        vec3 col = vec3(0.05, 0.0, 0.12) + vec3(0.8, 0.3, 1.0) * arcs * 0.8;
        col += vec3(1.0, 0.8, 1.0) * smoothstep(0.18, 0.0, r);
        return col;
    }
    if (s == 4) { // black hole
        float tw = a + 3.0 / (r + 0.15) - t * 0.8;
        float disk = pow(0.5 + 0.5 * sin(tw * 3.0 + fbm(vec2(tw, r * 8.0)) * 3.0), 3.0);
        vec3 col = mix(vec3(1.0, 0.55, 0.15), vec3(1.0, 0.9, 0.6), disk) * disk * smoothstep(0.55, 0.2, r);
        col *= smoothstep(0.08, 0.16, r);
        return col + vec3(0.02, 0.0, 0.04);
    }
    if (s == 5) { // aurora
        vec3 col = vec3(0.01, 0.02, 0.06);
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float x = p.x * 3.0 + fi * 1.3;
            float band = sin(x + t * (0.4 + 0.15 * fi) + fbm(vec2(x * 0.7, t * 0.2)) * 3.0) * 0.18 + 0.35 + fi * 0.12;
            float d = abs(p.y - band);
            vec3 hue = mix(vec3(0.1, 1.0, 0.5), vec3(0.6, 0.2, 1.0), fi * 0.5);
            col += hue * exp(-d * 14.0) * (0.6 + 0.4 * fbm(vec2(x * 4.0, t)));
        }
        return col;
    }
    if (s == 6) { // hologram
        float lines = 0.55 + 0.45 * sin(p.y * texels * 6.0 - t * 8.0);
        float flick = 0.85 + 0.15 * sin(t * 37.0) * step(0.8, hash(vec2(floor(t * 4.0), 1.0)));
        return vec3(0.25, 0.9, 1.0) * lines * flick + vec3(0.6, 1.0, 1.0) * pow(noise(vec2(p.y * 40.0, t * 10.0)), 8.0);
    }
    if (s == 7) { // kaleidoscope
        float seg = PI / 3.0;
        float aa = abs(mod(a + t * 0.2, seg * 2.0) - seg);
        vec2 q = vec2(cos(aa), sin(aa)) * r * 4.0;
        float n = fbm(q + t * 0.3);
        return hsv(fract(n * 1.5 + t * 0.05 + r), 0.75, 0.6 + 0.4 * sin(n * 12.0));
    }
    if (s == 8) { // neon
        float w = 0.5 + 0.5 * sin((p.x + p.y) * 10.0 - t * 3.0);
        vec3 col = mix(vec3(1.0, 0.15, 0.7), vec3(0.1, 0.9, 1.0), w);
        return col * (0.7 + 0.5 * pow(0.5 + 0.5 * sin(t * 4.0), 4.0));
    }
    if (s == 9) { // magma
        vec2 v = voronoi(p * 5.0 + vec2(0.0, t * 0.05), t * 0.3);
        float crack = smoothstep(0.12, 0.0, v.y);
        float heat = 0.6 + 0.4 * fbm(p * 6.0 + t * 0.4);
        vec3 rock = vec3(0.12, 0.05, 0.04) * (0.6 + 0.6 * fbm(p * 9.0));
        return mix(rock, mix(vec3(1.0, 0.25, 0.0), vec3(1.0, 0.85, 0.2), heat), crack * heat);
    }
    if (s == 10) { // koi pond
        vec2 q = p * 4.0;
        float caust = 0.0;
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            caust += pow(abs(sin(q.x * (1.0 + fi * 0.4) + sin(q.y * 1.3 + t * (0.8 + fi * 0.2)) * 1.6 + t * 0.5)), 12.0);
        }
        return mix(vec3(0.02, 0.18, 0.35), vec3(0.1, 0.45, 0.6), p.y) + vec3(0.6, 0.9, 1.0) * caust * 0.4;
    }
    if (s == 11) { // infinity mirror
        float rings = fract(log(r + 0.02) * 2.5 + t * 0.5);
        return hsv(fract(floor(log(r + 0.02) * 2.5 + t * 0.5) * 0.13 + t * 0.03), 0.8, 1.0) * smoothstep(0.35, 0.0, abs(rings - 0.5) - 0.12) * (0.4 + r * 1.6);
    }
    if (s == 12) { // disco floor
        vec2 cell = floor(p * 4.0);
        float h = hash(cell + floor(t * 2.0));
        vec2 f = fract(p * 4.0);
        float edge = step(0.1, f.x) * step(0.1, f.y);
        return hsv(h, 0.9, 1.0) * edge * (0.6 + 0.4 * sin(t * 6.0 + h * 20.0));
    }
    if (s == 13) { // synthwave
        vec3 sky = mix(vec3(1.0, 0.3, 0.6), vec3(0.25, 0.05, 0.45), p.y * 1.6);
        if (p.y > 0.55) {
            float gy = 1.0 / (p.y - 0.5);
            float gx = (p.x - 0.5) * gy;
            float grid = max(step(0.92, fract(gx * 2.0)), step(0.9, fract(gy * 0.6 + t * 0.6)));
            return mix(vec3(0.08, 0.0, 0.15), vec3(1.0, 0.2, 0.9), grid);
        }
        float sun = smoothstep(0.24, 0.22, length(p - vec2(0.5, 0.45))) * step(0.5, fract(p.y * 18.0 + 0.2) + smoothstep(0.35, 0.5, -p.y + 0.8));
        return mix(sky, vec3(1.0, 0.85, 0.3), sun);
    }
    if (s == 14) { // whirlpool
        float sp = sin(a * 3.0 + r * 18.0 - t * 2.5 + fbm(vec2(a, r * 5.0)) * 2.0);
        vec3 col = mix(vec3(0.0, 0.2, 0.3), vec3(0.3, 0.9, 0.9), 0.5 + 0.5 * sp);
        return col * smoothstep(0.0, 0.25, r) + vec3(1.0) * pow(0.5 + 0.5 * sp, 12.0) * 0.5;
    }
    if (s == 15) { // fire
        vec2 q = vec2(p.x * 3.0, (1.0 - p.y) * 2.0 - t * 1.2);
        float f = fbm(q) * (1.2 - (1.0 - p.y) * 0.5);
        f = clamp(f * 1.6 - 0.25, 0.0, 1.0);
        return mix(vec3(0.3, 0.0, 0.0), mix(vec3(1.0, 0.35, 0.0), vec3(1.0, 0.95, 0.5), f), f);
    }
    if (s == 16) { // holo foil
        float band = (p.x + p.y) * 1.5 + t * 0.25 + fbm(p * 5.0) * 0.6;
        vec3 col = hsv(fract(band), 0.55, 1.0);
        float glint = pow(max(0.0, sin((p.x - p.y) * 9.0 + t * 2.0)), 20.0);
        return col * 0.85 + vec3(glint);
    }
    if (s == 17) { // frost
        vec2 v = voronoi(p * 6.0, 0.0);
        float edge = smoothstep(0.08, 0.0, v.y);
        vec3 col = mix(vec3(0.55, 0.75, 0.95), vec3(0.9, 0.97, 1.0), edge + v.x * 0.3);
        float sparkle = step(0.97, hash(floor(p * texels * 2.0) + floor(t * 3.0)));
        return col + vec3(sparkle);
    }
    if (s == 18) { // light bridge
        vec2 q = p * 6.0;
        q.x *= 1.1547;
        q.y += mod(floor(q.x), 2.0) * 0.5;
        vec2 f = abs(fract(q) - 0.5);
        float hex = smoothstep(0.42, 0.48, max(f.x * 1.5 + f.y, f.y * 2.0) * 0.5 + 0.25);
        float pulse = 0.5 + 0.5 * sin(p.y * 8.0 - t * 4.0);
        return vec3(0.25, 0.6, 1.0) * (0.45 + 0.3 * pulse) + vec3(0.7, 0.9, 1.0) * hex;
    }
    if (s == 19) { // geode
        vec2 v = voronoi(p * 4.0, 0.0);
        float facet = hash(floor(p * 4.0 + v.x));
        vec3 col = hsv(0.78 + 0.08 * sin(t * 0.7 + facet * 6.0), 0.6, 0.55 + 0.45 * facet);
        col += vec3(1.0, 0.8, 1.0) * smoothstep(0.06, 0.0, v.y) * 0.6;
        return col;
    }
    // 20: waterfall
    float streak = noise(vec2(p.x * 14.0, p.y * 2.0 - t * 3.0));
    float foam = pow(noise(vec2(p.x * 30.0, p.y * 6.0 - t * 5.0)), 4.0);
    return mix(vec3(0.1, 0.35, 0.7), vec3(0.6, 0.85, 1.0), streak) + vec3(foam);
}

void main() {
    float t = GameTime * 1200.0;
    float steps = texels * 4.0;
    vec2 p = (floor(uv * steps) + 0.5) / steps;
    vec3 col = paint(style, p, t);
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
