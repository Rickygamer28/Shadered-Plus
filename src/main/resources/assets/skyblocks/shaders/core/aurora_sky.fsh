#version 150

// Shadered+'s aurora sky (our own code): a clear arctic night. Curtains of northern lights ripple and fold across the
// sky - green at their foot, shading up into violet and pink at their tops, fine rays flickering in them - drifting
// and slowly changing colour; stars twinkling behind them, the Milky Way faint across the sky; all round, dark snowy
// hills; below, a still lake mirroring it all. A ceiling of these blocks: the northern lights over your room.

uniform float Time; // ticks

in vec3 dir;

out vec4 fragColor;

const float PI = 3.14159265;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x), mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

mat2 rot(float a) {
    return mat2(cos(a), -sin(a), sin(a), cos(a));
}

// the northern lights: curtains hanging down through the sky, each a sheet along a slowly wandering line (the same
// line at every height, so they hang straight down), seen through layer after layer from their foot up
vec3 aurora(vec3 d, float t) {
    vec3 col = vec3(0.0);
    if (d.y < 0.015) return col;
    float shift = 0.5 + 0.5 * sin(t * 0.021); // (the colours slowly changing: more violet, then more green)
    float jit = hash13(floor(d * 1400.0)); // (each layer a little higher or lower for each bit of sky: no stripes)
    // (each curtain's own frame, for this direction: worked out once, not for every layer)
    vec2 qd0 = rot(0.4) * d.xz / d.y, qd1 = rot(1.7) * d.xz / d.y, qd2 = rot(3.0) * d.xz / d.y;
    vec3 green = mix(vec3(0.15, 1.0, 0.45), vec3(0.2, 1.0, 0.8), shift * 0.5);
    vec3 top = mix(vec3(0.6, 0.25, 1.0), vec3(1.0, 0.3, 0.55), shift);
    for (int i = 0; i < 40; i++) {
        float fi = float(i) + jit;
        float k = fi / 40.0;
        float h = 1.0 + k * 1.4;            // (each layer's height: the curtains' foot at 1)
        float far = length(d.xz) * (h / d.y); // (how far off it is, where the view meets it)
        float band = 0.0;
        for (int c = 0; c < 3; c++) {
            float fc = float(c);
            vec2 q = (c == 0 ? qd0 : c == 1 ? qd1 : qd2) * h;
            // the line it hangs along: wandering and drifting, folded in small pleats
            float line = q.y - (fc * 2.4 - 1.9) - 1.1 * sin(q.x * 0.28 + t * (0.04 + 0.015 * fc) + fc * 2.3);
            // (the rest moves it at most 0.84 more: far enough from it, the curtain gives nothing you could see - skip it)
            if (abs(line) > 1.95) continue;
            line = line - 1.2 * (fbm(vec2(q.x * 0.35 + t * 0.03, fc * 5.1)) - 0.5)
                       - 0.18 * sin(q.x * 2.3 + t * 0.4 + fc) - 0.12 * (noise(vec2(q.x * 3.0 - t * 0.3, fc * 9.0)) - 0.5);
            float w = 0.05 + 0.04 * (0.5 + 0.5 * sin(q.x * 0.6 + fc * 1.9 + t * 0.07));
            float sheet = exp(-line * line / (w * w)) + 0.22 * exp(-line * line / (w * w * 16.0));
            // rays: fine streaks up and down it, flickering, running along it
            float r = noise(vec2(q.x * 26.0 + t * (1.2 + 0.3 * fc), fc * 3.7 + t * 0.5));
            float rays = 0.25 + 2.2 * r * r * r;
            float pulse = 0.55 + 0.45 * sin(t * 0.23 + fc * 2.1 + q.x * 0.08);
            band += sheet * rays * pulse;
        }
        if (band <= 0.0) continue;
        // a bright sharp foot, a long fading top
        float fall = smoothstep(0.0, 0.03, k) * (exp(-k * 7.0) + 0.5 * exp(-k * 1.4));
        vec3 c = mix(green, top, smoothstep(0.15, 0.55, k));
        col += c * band * fall * 0.14 / (1.0 + far * 0.05);
    }
    return col;
}

// the night sky (no hills or lake): its colour, the stars, the Milky Way, the northern lights
vec3 sky(vec3 d, float t) {
    float up = max(d.y, 0.0);
    vec3 col = mix(vec3(0.015, 0.04, 0.09), vec3(0.0, 0.006, 0.025), pow(up, 0.5));
    // the Milky Way: a faint dusty band across the sky
    vec3 mwN = normalize(vec3(0.35, 0.45, 0.82));
    float mw = exp(-pow(dot(d, mwN), 2.0) * 18.0);
    vec2 mp = vec2(atan(d.z, d.x) * 3.0, d.y * 6.0);
    col += vec3(0.35, 0.38, 0.5) * mw * (0.12 + 0.18 * fbm(mp * 2.5)) * smoothstep(0.0, 0.2, d.y);
    // stars, twinkling
    vec3 sp = d * 140.0;
    vec3 cell = floor(sp);
    float hs = hash13(cell);
    if (hs > 0.965 - mw * 0.02) {
        vec3 at = cell + 0.5 + (vec3(hash13(cell + 7.1), hash13(cell + 3.3), hash13(cell + 5.9)) - 0.5) * 0.6;
        float r = length(sp - at);
        float tw = 0.6 + 0.4 * sin(t * (1.5 + 3.0 * hash13(cell + 1.7)) + hs * 60.0);
        vec3 sc = mix(vec3(1.0, 0.85, 0.7), vec3(0.75, 0.85, 1.0), hash13(cell + 9.9));
        col += sc * smoothstep(0.14, 0.0, r) * tw * (0.4 + 1.6 * (hs - 0.965) / 0.035) * smoothstep(0.0, 0.08, d.y);
    }
    vec3 a = aurora(d, t);
    col += a;
    // (the lights' glow in the air low down)
    col += vec3(0.02, 0.12, 0.07) * smoothstep(0.35, 0.0, d.y) * (0.6 + 0.4 * sin(t * 0.1)) * step(0.0, d.y);
    return col;
}

// the hills round the lake: how high they rise at this bearing (a dark snowy ridge, nearer lower hills before it)
float hills(float az) {
    return 0.035 + 0.07 * fbm(vec2(az * 1.6, 3.0)) + 0.03 * fbm(vec2(az * 6.0, 7.0));
}

vec3 world(vec3 d, float t, out bool hill) {
    float az = atan(d.z, d.x);
    float hh = hills(az);
    hill = d.y >= 0.0 && d.y < hh;
    if (hill) { // snow on dark slopes, faintly lit green by the lights above, pines along the foot
        float k = d.y / hh;
        vec3 snow = vec3(0.07, 0.11, 0.14) + vec3(0.03, 0.1, 0.07) * (0.6 + 0.4 * sin(t * 0.1));
        vec3 c = mix(vec3(0.01, 0.015, 0.02), snow, smoothstep(0.3, 1.0, k) * fbm(vec2(az * 30.0, d.y * 60.0)));
        float pines = step(0.5, noise(vec2(az * 140.0, 1.0))) * step(k, 0.25 + 0.2 * noise(vec2(az * 260.0, 2.0)));
        return mix(c, vec3(0.0, 0.006, 0.01), pines);
    }
    return sky(d, t);
}

void main() {
    vec3 d = normalize(dir);
    float t = Time / 20.0;
    vec3 col;
    bool hill;
    if (d.y >= 0.0) {
        col = world(d, t, hill);
    } else { // the lake: the sky and hills mirrored, a little darker, the faintest ripples
        vec3 r = vec3(d.x, -d.y, d.z);
        vec2 rp = d.xz / max(-d.y, 0.02);
        r.xz += (vec2(noise(rp * 1.5 + t * 0.15), noise(rp * 1.5 - t * 0.12 + 4.0)) - 0.5) * 0.015 * smoothstep(0.0, 0.3, -d.y);
        col = world(normalize(r), t, hill) * 0.55;
        // (ice along the near shore)
        float shore = smoothstep(-0.55, -0.8, d.y);
        col = mix(col, vec3(0.05, 0.08, 0.11) * (0.8 + 0.4 * fbm(rp * 3.0)), shore * 0.8);
    }
    fragColor = vec4(col, 1.0);
}
