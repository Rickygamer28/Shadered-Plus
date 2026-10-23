#version 150

// Shadered+'s synthwave sky (our own code). All around you:
//  west   the big striped sun sinking into the horizon, striped clouds drifting over it
//  east   a neon city skyline, windows lighting up and going dark, blinking antenna lights
//  along the highway  palm trees passing by as the road moves; high in the north a ringed planet
//  everywhere  wireframe mountains (two layers), stars, shooting stars, the neon grid floor rushing towards you, a
//         highway along it from the city to the sun
// With music playing (client/MusicReactor) the grid rises into peaks either side of the highway (bass near it,
// treble far out, flowing away from you), the mountains pulse, the sun swells with the bass and equaliser bars glow
// along the horizon.

uniform sampler2D Sampler0; // the last seconds of music: x = how long ago (0 now .. 1 oldest), y = band (bass .. treble)
uniform float Time;         // ticks
uniform float Music;        // how loud the music is here (0 = none)
uniform float Bass;
uniform float Mid;
uniform float High;
uniform vec4 Bars0;
uniform vec4 Bars1;
uniform vec4 Bars2;
uniform vec4 Bars3;
uniform float HistoryPhase; // 0..1: how far into the next history step

in vec3 dir;

out vec4 fragColor;

const float PI = 3.14159265;
const float TAU = 6.2831853;
const vec3 SUN_DIR = vec3(-1.0, 0.0, 0.0); // west
const vec3 SIDE = vec3(0.0, 0.0, 1.0);
const vec3 PLANET_DIR = vec3(0.30, 0.52, -0.80);

const vec3 TOP = vec3(0.05, 0.01, 0.13);
const vec3 MID = vec3(0.42, 0.05, 0.45);
const vec3 MID_COOL = vec3(0.24, 0.04, 0.40);
const vec3 HORIZON = vec3(1.0, 0.40, 0.42);
const vec3 HORIZON_COOL = vec3(0.80, 0.18, 0.62);
const vec3 PINK = vec3(1.0, 0.16, 0.62);
const vec3 ORANGE = vec3(1.0, 0.52, 0.12);
const vec3 YELLOW = vec3(1.0, 0.86, 0.24);
const vec3 WIRE = vec3(0.95, 0.35, 1.0);
const vec3 FLOOR = vec3(0.05, 0.0, 0.1);
const vec3 ROCK = vec3(0.07, 0.01, 0.13);
const vec3 SILHOUETTE = vec3(0.035, 0.0, 0.07);

float hash11(float n) {
    return fract(sin(n * 12.9898) * 43758.5453);
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float bar(int i) {
    vec4 v = i < 4 ? Bars0 : i < 8 ? Bars1 : i < 12 ? Bars2 : Bars3;
    return v[i - (i / 4) * 4];
}

// the angle round the horizon from a horizontal direction c (0 towards it)
float azFrom(vec3 d, vec3 c) {
    return atan(dot(d, vec3(-c.z, 0.0, c.x)), dot(d, c));
}

// straight lines between random peaks, n of them all the way round
float ridge(float x, float n, float seed) {
    float i = floor(x);
    return mix(hash11(mod(i, n) + seed), hash11(mod(i + 1.0, n) + seed), fract(x));
}

// a thin line through every whole number of v (smooth, fades out before it would shimmer)
float lines(float v, float width) {
    float aa = fwidth(v);
    float d = 0.5 - abs(fract(v) - 0.5);
    return (1.0 - smoothstep(width, width + aa * 1.5, d)) * (1.0 - smoothstep(0.3, 0.8, aa));
}

// ---- sky things ----

float noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x), mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x), mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise3(p);
        p = p * 2.03 + vec3(1.7, 9.2, 4.1);
        a *= 0.5;
    }
    return v;
}

// soft streaks of cloud high up, drifting; lit orange-pink towards the sun, purple elsewhere
vec3 wisps(vec3 d, float elev, float t, float facing, vec3 col) {
    if (elev < 0.05 || elev > 1.0) {
        return col;
    }
    float c = fbm(vec3(d.x * 2.2 + t * 0.012, d.y * 9.0, d.z * 2.2 - t * 0.008));
    float mask = smoothstep(0.52, 0.78, c) * smoothstep(0.05, 0.2, elev) * (1.0 - smoothstep(0.55, 1.0, elev));
    vec3 lit = mix(vec3(0.42, 0.1, 0.55), vec3(1.0, 0.5, 0.55), facing * 0.8);
    return mix(col, lit, mask * 0.38);
}

// two searchlight beams from the city, sweeping slowly
vec3 searchlights(vec3 d, float elev, float t, vec3 col) {
    float a = azFrom(d, vec3(1.0, 0.0, 0.0));
    if (abs(a) > 1.4 || elev < 0.0) {
        return col;
    }
    for (int k = 0; k < 2; k++) {
        float base = k == 0 ? -0.35 : 0.42;
        float swing = sin(t * (0.23 + 0.07 * float(k)) + float(k) * 2.1) * 0.55;
        vec2 dir2 = vec2(sin(swing), cos(swing));
        vec2 rel = vec2(a - base, elev);
        float along = dot(rel, dir2);
        if (along <= 0.0) {
            continue;
        }
        float across = abs(rel.x * dir2.y - rel.y * dir2.x);
        float width = 0.006 + along * 0.06;
        col += vec3(1.0, 0.6, 0.85) * 0.22 * exp(-across * across / (width * width)) * exp(-along * 1.6);
    }
    return col;
}

// the farthest hills: pale and soft, all the way round
vec3 farHills(float az, float elev, vec3 col, float facing) {
    float h = 0.09 + 0.045 * sin(3.0 * az + 1.3) + 0.03 * sin(7.0 * az - 0.4) + 0.018 * sin(17.0 * az + 2.0) + 0.01 * sin(41.0 * az);
    if (elev < h) {
        vec3 hazy = mix(mix(HORIZON_COOL, HORIZON, facing), MID, 0.45);
        col = mix(col, hazy, 0.6 * (0.6 + 0.4 * elev / h));
    }
    return col;
}


vec3 stars(vec3 d, float h, float away, float t, vec3 col) {
    vec3 cell = floor(d * 170.0);
    float rnd = hash13(cell);
    if (rnd > mix(0.994, 0.988, away)) {
        vec3 centre = cell + 0.5 + (vec3(hash13(cell + 1.7), hash13(cell + 3.1), hash13(cell + 5.3)) - 0.5) * 0.5;
        float twinkle = 0.5 + 0.5 * sin(t * (1.5 + 3.0 * hash13(cell + 9.0)) + rnd * 300.0);
        twinkle = min(1.3, twinkle + High * 0.6);
        float star = smoothstep(0.32, 0.0, length(d * 170.0 - centre));
        col += vec3(1.0, 0.85, 1.0) * star * twinkle * smoothstep(0.1, 0.35, h);
    }
    return col;
}

vec3 shootingStars(float az, float elev, float t, vec3 col) {
    for (int k = 0; k < 2; k++) {
        float period = 6.0 + 3.0 * float(k);
        float shifted = t + float(k) * 2.7;
        float slot = floor(shifted / period);
        float age = shifted - slot * period;
        if (age > 1.1) {
            continue;
        }
        float r1 = hash11(slot * 1.7 + float(k) * 13.0), r2 = hash11(slot * 3.1 + float(k) * 7.0), r3 = hash11(slot * 5.3 + 3.0);
        vec2 start = vec2((r1 * 2.0 - 1.0) * PI, 0.5 + r2 * 0.55);
        float a = -0.3 - r3 * 0.5;
        vec2 dv = vec2(cos(a) * (r1 < 0.5 ? -1.0 : 1.0), sin(a));
        vec2 head = start + dv * (age / 1.1) * 0.8;
        vec2 rel = vec2(az, elev) - head;
        rel.x = (mod(rel.x + PI, TAU) - PI) * cos(elev);
        vec2 tail = -dv * 0.2;
        float along = clamp(dot(rel, tail) / dot(tail, tail), 0.0, 1.0);
        float dist = length(rel - tail * along);
        float fade = (1.0 - along) * sin(age / 1.1 * PI);
        col += (vec3(1.0, 0.85, 0.95) * smoothstep(0.004, 0.0, dist) + PINK * 0.35 * smoothstep(0.02, 0.0, dist)) * fade;
    }
    return col;
}

vec3 planet(vec3 d, float t, vec3 col) {
    vec3 c = normalize(PLANET_DIR);
    float z = dot(d, c);
    if (z <= 0.5) {
        return col;
    }
    vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), c));
    vec3 up = cross(c, right);
    vec2 uv = vec2(dot(d, right), dot(d, up)) / z;
    float r = 0.12;
    float pd = length(uv);
    // the ring, in a tilted plane; its far half goes behind the planet
    float tilt = 0.38;
    vec2 ru = mat2(cos(tilt), sin(tilt), -sin(tilt), cos(tilt)) * uv;
    float rr = length(vec2(ru.x, ru.y / 0.27));
    float ring = smoothstep(0.165, 0.17, rr) * (1.0 - smoothstep(0.245, 0.25, rr)) * (0.55 + 0.45 * sin(rr * 170.0));
    vec3 ringCol = mix(PINK, ORANGE, 0.45);
    col += PINK * 0.18 * exp(-max(pd - r, 0.0) * 22.0);
    if (ru.y > 0.0) {
        col = mix(col, ringCol, ring * 0.8);
    }
    if (pd < r + 0.003) {
        vec2 q = uv / r;
        float band = 0.5 + 0.5 * sin(q.y * 13.0 + sin(q.x * 3.0 + t * 0.15) * 0.7);
        vec3 surface = mix(vec3(0.32, 0.05, 0.45), vec3(0.98, 0.38, 0.62), band * 0.65 + 0.15);
        vec2 sunFlat = normalize(vec2(dot(SUN_DIR, right), dot(SUN_DIR, up)) + vec2(0.0, 0.001));
        vec3 n = vec3(q, sqrt(max(0.0, 1.0 - dot(q, q))));
        float light = clamp(dot(n, normalize(vec3(sunFlat * 0.85, 0.45))), 0.0, 1.0);
        surface = surface * (0.18 + 0.95 * light) + PINK * 0.5 * pow(1.0 - n.z, 3.0);
        col = mix(col, surface, 1.0 - smoothstep(r - 0.002, r + 0.002, pd));
    }
    if (ru.y <= 0.0) {
        col = mix(col, ringCol, ring * 0.8);
    }
    return col;
}

vec3 sun(float az, float elev, float t, vec3 col) {
    float radius = 0.3 * (1.0 + 0.025 * sin(t * 1.3) + 0.12 * Bass);
    vec2 sp = vec2(az, elev - 0.14);
    float sd = length(sp);
    col += PINK * (0.5 + 0.4 * Bass) * exp(-max(sd - radius, 0.0) * 6.0);
    if (sd < radius + 0.01) {
        float up = sp.y / radius * 0.5 + 0.5;
        vec3 disc = mix(PINK, ORANGE, smoothstep(0.0, 0.55, up));
        disc = mix(disc, YELLOW, smoothstep(0.5, 1.0, up));
        float keep = 1.0;
        if (up < 0.6) {
            float band = fract(up * 9.0 + t * 0.35);
            float gap = mix(0.5, 0.06, up / 0.6);
            keep = smoothstep(gap - 0.03, gap + 0.03, band);
        }
        float edge = 1.0 - smoothstep(radius - 0.004, radius + 0.004, sd);
        col = mix(col, disc * 1.15, keep * edge);
    }
    return col;
}

// thin bands of cloud near the horizon, drifting; dark against the sun, glowing pink elsewhere
vec3 clouds(float az, float elev, float t, float facing, vec3 col) {
    if (elev < 0.015 || elev > 0.3) {
        return col;
    }
    float s = 0.5 + 0.22 * sin(3.0 * az + 1.0 + t * 0.03) + 0.16 * sin(7.0 * az - 2.0 - t * 0.05) + 0.12 * sin(13.0 * az + 0.5 + t * 0.02);
    float mask = smoothstep(0.52, 0.72, s + 0.22 * sin(az * 5.0 + elev * 25.0)) * smoothstep(0.015, 0.05, elev) * (1.0 - smoothstep(0.18, 0.3, elev));
    float stripe = smoothstep(0.25, 0.6, sin(elev * 120.0 + s * 7.0));
    vec3 cloud = mix(vec3(1.0, 0.45, 0.72) * 0.75, vec3(0.22, 0.02, 0.28), smoothstep(0.55, 0.95, facing));
    return mix(col, cloud, mask * stripe * 0.7);
}

// equaliser bars round the horizon (with music only), mirrored, faded in front of the sun
vec3 equaliser(float az, float elev, vec3 col) {
    if (Music < 0.001 || elev > 0.3) {
        return col;
    }
    float x = (az / TAU + 0.5) * 64.0;
    float i = floor(x);
    float f = fract(x);
    int b = int(mod(i, 32.0));
    b = b < 16 ? b : 31 - b;
    float height = bar(b) * 0.24 * Music;
    float inBar = smoothstep(0.12, 0.18, f) * (1.0 - smoothstep(0.82, 0.88, f));
    float show = inBar * (1.0 - smoothstep(height - 0.004, height, elev)) * smoothstep(0.2, 0.6, abs(az));
    vec3 c = mix(PINK, ORANGE, clamp(elev / 0.24, 0.0, 1.0) * 1.3);
    float lit = 0.35 + 0.65 * lines(elev * 90.0, 0.25); // (segmented, like an old equaliser)
    return mix(col, c * (0.6 + 0.6 * lit), show * 0.75);
}

vec3 mountains(float az, float elev, vec3 col) {
    float x = (az / TAU + 0.5) * 36.0;
    float pulse = 1.0 + 0.6 * Bass;
    // far, hazy layer
    float far = (ridge(x * 1.5, 54.0, 77.0) * 0.16 + 0.03) * pulse;
    if (elev < far) {
        float up = elev / max(far, 0.001);
        vec3 rock = mix(vec3(0.16, 0.02, 0.22), mix(vec3(0.5, 0.1, 0.45), HORIZON_COOL * 0.85, 0.35), up);
        col = mix(col, rock, 0.88);
        col += PINK * 0.18 * lines(elev * 45.0, 0.03) * up; // (faint contour lines)
        col += PINK * 0.6 * (1.0 - smoothstep(0.0, fwidth(elev) * 2.5, far - elev));
    }
    // near, wireframe layer (lower in front of the sun so it shows through the valley)
    float peak = max(ridge(x, 36.0, 3.0) * 0.12, ridge(x * 2.0, 72.0, 41.0) * 0.07);
    peak *= mix(0.3, 1.0, smoothstep(0.08, 0.55, abs(az))) * pulse;
    if (elev < peak) {
        float wire = max(lines(elev * 70.0, 0.04), lines(x * 2.0, 0.04)) * 0.55 * (1.0 + Mid);
        float rim = 1.0 - smoothstep(0.0, fwidth(elev) * 2.5, peak - elev);
        col = ROCK + WIRE * max(wire * (0.4 + 0.6 * elev / max(peak, 0.001)), rim);
    }
    // radio masts with blinking red lights on some of the peaks
    float vx = floor(x + 0.5);
    float mast = hash11(mod(vx, 36.0) + 91.0);
    if (mast > 0.72) {
        float top = max(hash11(mod(vx, 36.0) + 3.0) * 0.12, ridge(vx * 2.0, 72.0, 41.0) * 0.07)
                * mix(0.3, 1.0, smoothstep(0.08, 0.55, abs(az))) * pulse + 0.012;
        vec2 rel = vec2((x - vx) / 36.0 * TAU * cos(elev), elev - top);
        float blink = step(0.5, fract(Time / 20.0 * 0.6 + mast * 7.0));
        col += vec3(1.0, 0.12, 0.25) * (smoothstep(0.003, 0.0, length(rel)) * 2.0 + 0.25 * exp(-length(rel) * 120.0)) * blink;
        if (abs(rel.x) < 0.0008 && rel.y < 0.0 && rel.y > -0.012) {
            col = ROCK; // the mast
        }
    }
    return col;
}

vec3 city(vec3 d, float elev, float t, vec3 col) {
    float a = azFrom(d, vec3(1.0, 0.0, 0.0)); // 0 = east
    const float SPAN = 1.05;
    if (abs(a) > SPAN || elev > 0.25 || elev < -0.01) {
        return col;
    }
    float edge = 1.0 - smoothstep(0.55, 1.0, abs(a) / SPAN);
    // back row: taller, hazy
    float xb = (a + SPAN) * 15.0 + 0.37;
    float ib = floor(xb);
    float hb = (0.05 + 0.13 * pow(hash11(ib + 51.0), 1.4)) * edge;
    float fb = fract(xb);
    if (elev < hb && fb > 0.06 && fb < 0.94) {
        col = mix(col, vec3(0.2, 0.03, 0.27), 0.9);
        vec3 wcell = vec3(ib, floor(fb * 5.0), floor(elev * 200.0));
        if (hash13(wcell + 3.0) > 0.7 && fract(fb * 5.0) > 0.3 && fract(elev * 200.0) > 0.4) {
            col += ORANGE * 0.25;
        }
    }
    // front row: dark, lit windows, neon tops, antennas
    float x = (a + SPAN) * 24.0;
    float i = floor(x);
    float f = fract(x);
    float r = hash11(i + 101.0);
    float h = (0.022 + 0.1 * pow(r, 1.7)) * edge;
    float gap = 0.05 + 0.15 * hash11(i + 7.0);
    bool inside = f > gap && f < 1.0 - gap;
    if (inside && elev < h) {
        col = SILHOUETTE;
        float u = (f - gap) / (1.0 - 2.0 * gap) * 4.0;
        float v = elev * 260.0;
        vec3 cell = vec3(i, floor(u), floor(v));
        float w = hash13(cell);
        float flicker = hash13(cell + floor(t / (4.0 + 9.0 * hash13(cell + 2.0))));
        if (w > 0.45 && flicker > 0.25 && fract(u) > 0.22 && fract(u) < 0.78 && fract(v) > 0.3 && fract(v) < 0.75 && elev < h - 0.004) {
            vec3 lamp = hash13(cell + 5.0) > 0.9 ? vec3(0.4, 0.9, 1.0) : mix(ORANGE, PINK, hash13(cell + 8.0));
            col = lamp * (0.7 + 0.3 * w);
        }
        col += PINK * 0.8 * (1.0 - smoothstep(0.0, fwidth(elev) * 2.0, h - elev));
    }
    if (r > 0.82 && abs(f - 0.5) < 0.035 && elev >= h && elev < h + 0.035 * edge) {
        col = SILHOUETTE; // antenna
    }
    if (r > 0.82) {
        vec2 tip = vec2((f - 0.5) / 24.0, elev - (h + 0.035 * edge));
        float blink = step(0.55, fract(t * 0.7 + r * 5.0));
        col += vec3(1.0, 0.15, 0.3) * blink * smoothstep(0.004, 0.0, length(tip)) * 2.0;
    }
    return col;
}

// distance from p to the segment a-b, and how far along it (0..1) the nearest point is
vec2 segment(vec2 p, vec2 a, vec2 b) {
    vec2 ab = b - a;
    float h = clamp(dot(p - a, ab) / dot(ab, ab), 0.0, 1.0);
    return vec2(length(p - a - ab * h), h);
}

// a palm tree silhouette: q = position from its foot (blocks), size = its height, aa = one pixel in palm heights
float palm(vec2 q, float size, float lean, float aa, float seed) {
    vec2 p = q / size;
    if (p.x < -1.05 || p.x > 1.05 || p.y < -0.05 || p.y > 1.45) {
        return 0.0;
    }
    float s = 0.0;
    // trunk: curved, thinner towards the top, slightly ringed
    if (p.y < 1.0) {
        float width = (0.036 - 0.016 * p.y) * (0.9 + 0.1 * abs(sin(p.y * 70.0)));
        s = 1.0 - smoothstep(width, width + aa, abs(p.x - lean * p.y * p.y));
    }
    vec2 top = vec2(lean, 1.0);
    if (length(p - top) > 0.75) {
        return s;
    }
    // coconuts
    s = max(s, 1.0 - smoothstep(0.03, 0.03 + aa, length(p - top - vec2(0.02, -0.04))));
    s = max(s, 1.0 - smoothstep(0.026, 0.026 + aa, length(p - top - vec2(-0.03, -0.05))));
    // fronds: arching out and drooping, tapering, with leaflets along them
    for (int k = 0; k < 8; k++) {
        float ang = -0.35 + (float(k) + 0.5 * hash11(seed + float(k))) / 7.5 * (PI + 0.7);
        vec2 spread = vec2(cos(ang), sin(ang) * 0.75 + 0.25);
        float len = 0.5 + 0.18 * hash11(seed * 1.7 + float(k));
        float droop = 0.35 + 0.25 * hash11(seed * 2.3 + float(k));
        vec2 prev = top;
        for (int j = 1; j <= 6; j++) {
            float along = float(j) / 6.0;
            vec2 next = top + spread * along * len - vec2(0.0, droop * along * along * len);
            vec2 seg = segment(p, prev, next);
            float f = (float(j) - 1.0 + seg.y) / 6.0; // 0 at the top .. 1 at the tip
            float leaflets = 0.55 + 0.9 * abs(fract(f * 11.0) - 0.5); // (a jagged edge, like a palm leaf)
            float w = (0.045 * (1.0 - f) + 0.006) * leaflets;
            s = max(s, 1.0 - smoothstep(w, w + aa, seg.x));
            prev = next;
        }
    }
    return s;
}

const float PALM_SPACING = 7.0; // blocks between palms along the highway
const float PALM_SIDE = 1.4;     // how far from the middle of the highway they stand

// the palms along one side of the highway, moving with it: colour (premultiplied) and coverage at this pixel, and how
// far away the nearest one is. Each palm is a flat cut-out turned to face you, standing on the floor.
vec4 roadPalms(vec3 d, float side, float t, float facing, out float depth) {
    depth = 1.0e9;
    vec4 acc = vec4(0.0);
    float pixel = length(fwidth(d)); // (taken before any branching, so it's reliable)
    float da = dot(d, SUN_DIR);
    float db = dot(d, SIDE);
    float scroll = t * 2.5; // (as the grid)
    // how far along the ray it is within reach of this side's row of palms...
    float reach = 6.0;
    float t1 = 0.0, t2 = 130.0;
    if (abs(db) > 1.0e-4) {
        float ta = (side - reach) / db, tb = (side + reach) / db;
        t1 = max(t1, min(ta, tb));
        t2 = min(t2, max(ta, tb));
    } else if (abs(side) > reach) {
        return acc;
    }
    if (t1 > t2) {
        return acc;
    }
    // ...and so which stretch of the highway (plus a palm's reach either way), walked from the near end
    float near = da * t1, far = da * t2;
    float dir = far >= near ? 1.0 : -1.0;
    near -= dir * reach;
    far += dir * reach;
    float first = dir > 0.0 ? ceil((near + scroll) / PALM_SPACING) : floor((near + scroll) / PALM_SPACING);
    for (int i = 0; i < 18; i++) {
        float k = first + dir * float(i);
        float along = k * PALM_SPACING - scroll;
        if ((along - far) * dir > 0.0) {
            break;
        }
        float seed = k * 3.7 + side * 11.0;
        if (hash11(seed) < 0.15) {
            continue; // (a gap now and then)
        }
        vec2 base = SUN_DIR.xz * along + SIDE.xz * (side + sign(side) * hash11(seed + 5.0) * 0.6);
        float dist = length(base);
        vec2 n = base / dist;
        float toward = dot(d.xz, n);
        if (toward <= 1.0e-3) {
            continue;
        }
        float th = dist / toward; // where the ray meets the palm's cut-out
        vec3 h = d * th;
        vec2 q = vec2(dot(h.xz - base, vec2(-n.y, n.x)), h.y + 1.0);
        float size = 3.4 + 1.8 * hash11(seed + 1.3);
        float lean = (hash11(seed + 2.9) - 0.5) * 0.55;
        float cover = palm(q, size, lean, th * pixel / size * 1.5, seed);
        if (cover > 0.0) {
            float haze = exp(-14.0 / th) * 0.85;
            vec3 c = mix(SILHOUETTE, mix(HORIZON_COOL, HORIZON, facing) * 0.9, haze);
            acc.rgb += (1.0 - acc.a) * cover * c;
            acc.a += (1.0 - acc.a) * cover;
            depth = min(depth, th);
            if (acc.a > 0.98) {
                break;
            }
        }
    }
    return acc;
}

// ---- the floor ----

const float FLOW_STEP = 0.7; // blocks the music moves away per history step (24 steps a second)
const float SPREAD = 60.0;   // blocks across for the 16 bands, from the highway out

// how tall the peaks may get at distance r (taller far away, so they still show near the horizon)
float amp(float r) {
    return 0.7 + 0.045 * r;
}

// how high the music raises the floor at (along the highway, across it), r blocks away
float terrain(vec2 g, float r) {
    float lateral = abs(g.y) - 2.2; // (beyond the palms)
    if (lateral <= 0.0) {
        return 0.0;
    }
    float band = lateral / SPREAD;
    float steps = abs(g.x) / FLOW_STEP;
    if (band >= 1.0 || steps >= 127.0) {
        return 0.0;
    }
    // (HistoryPhase: part of the next step already gone by, so the peaks glide instead of jumping)
    float x = (max(steps - HistoryPhase, 0.0) + 0.5) / 128.0;
    float v = texture(Sampler0, vec2(x, band * (15.0 / 16.0) + 0.5 / 16.0)).r;
    return amp(r) * v * Music * smoothstep(0.0, 0.8, lateral) * (1.0 - smoothstep(100.0, 127.0, steps))
            * (1.0 - smoothstep(0.85, 1.0, band));
}

vec2 gridAt(vec3 p) {
    return vec2(dot(p.xz, SUN_DIR.xz), dot(p.xz, SIDE.xz));
}

// where the view ray meets the music's peaks (or the floor under them), -1 if it doesn't
float marchTerrain(vec3 d) {
    float t = 0.3;
    float before = t;
    for (int i = 0; i < 100; i++) {
        vec3 p = d * t;
        float r = length(p.xz);
        if (r > 130.0 || (d.y >= 0.0 && p.y > -1.0 + amp(r))) {
            break; // out of the music's floor, or above every peak and rising
        }
        if (p.y < -1.0 + terrain(gridAt(p), r)) {
            float lo = before, hi = t;
            for (int k = 0; k < 7; k++) {
                float m = 0.5 * (lo + hi);
                vec3 pm = d * m;
                if (pm.y < -1.0 + terrain(gridAt(pm), length(pm.xz))) {
                    hi = m;
                } else {
                    lo = m;
                }
            }
            return hi;
        }
        before = t;
        t = t * 1.05 + 0.05; // (longer steps further away)
    }
    return -1.0;
}

// lines through every whole number of v, aa = how much of v one pixel covers
float linesW(float v, float width, float aa) {
    float d = 0.5 - abs(fract(v) - 0.5);
    return (1.0 - smoothstep(width, width + aa * 1.5, d)) * (1.0 - smoothstep(0.3, 0.8, aa));
}

vec3 floorColour(vec3 d, float az, float t, float facing, float hit) {
    float down = -d.y;
    vec3 p = d * hit;
    float height = clamp((p.y + 1.0) / amp(length(p.xz)), 0.0, 1.0);
    // how many blocks of floor one pixel covers (from the view direction, smooth everywhere, unlike fwidth of the hit)
    float aa = hit * length(fwidth(d)) / max(abs(d.y) + 0.35 * height, 0.06);
    vec2 g = gridAt(p);
    vec2 moving = vec2(g.x + t * 2.5, g.y);
    float road = abs(g.y);
    vec3 col;
    if (road < 0.75) {
        // the highway: dark, orange edges, a dashed middle line
        col = vec3(0.03, 0.0, 0.06);
        float edgeLine = 1.0 - smoothstep(0.03, 0.03 + aa * 1.5, abs(road - 0.7));
        float dash = (1.0 - smoothstep(0.025, 0.025 + aa * 1.5, road)) * step(0.5, fract(moving.x * 0.5));
        col += ORANGE * 1.2 * edgeLine + YELLOW * 0.9 * dash * (1.0 - smoothstep(0.3, 0.9, aa));
    } else {
        float grid = max(linesW(moving.x, 0.03, aa), linesW(moving.y, 0.03, aa));
        float near = 0.5 - max(abs(fract(moving.x) - 0.5), abs(fract(moving.y) - 0.5));
        vec3 lineCol = mix(PINK, mix(ORANGE, YELLOW, height), smoothstep(0.05, 0.6, height));
        col = FLOOR + PINK * 0.3 * height + lineCol * 1.3 * grid * (1.0 + 0.6 * Mid)
                + PINK * 0.12 * (1.0 - smoothstep(0.0, 0.2, near)) * (1.0 - smoothstep(0.3, 0.9, aa));
    }
    // the sun's reflection, the haze far away
    float haze = exp(-14.0 / hit) * (1.0 - 0.6 * height); // (peaks stand out of the haze)
    col += ORANGE * 0.3 * exp(-abs(az) * 5.0) * haze;
    col = mix(col, mix(HORIZON_COOL, HORIZON, facing) * 0.9, haze * 0.8);
    col += PINK * 0.45 * exp(-max(down, 0.0) * 40.0) * (1.0 - height);
    return col;
}

void main() {
    vec3 d = normalize(dir);
    float t = Time / 20.0; // seconds
    float elev = asin(clamp(d.y, -1.0, 1.0));
    float az = azFrom(d, SUN_DIR); // 0 towards the sun
    float facing = pow(clamp(dot(normalize(d.xz + vec2(1.0e-5)), SUN_DIR.xz) * 0.5 + 0.5, 0.0, 1.0), 1.5); // 1 sunwards
    vec3 col;

    // with music the floor rises into peaks, all the way out (far ones above the horizon, in front of the mountains)
    float peaks = Music > 0.001 && elev < 0.12 ? marchTerrain(d) : -1.0;
    bool raised = peaks > 0.0 && (d * peaks).y > -0.995;
    if (peaks > 0.0) {
        col = floorColour(d, az, t, facing, peaks);
    } else if (elev >= 0.0) {
        float h = elev / (PI * 0.5);
        col = mix(mix(HORIZON_COOL, HORIZON, facing), mix(MID_COOL, MID, facing), smoothstep(0.0, 0.2, h));
        col = mix(col, TOP, smoothstep(0.15, 0.65, h));
        col = stars(d, h, 1.0 - facing, t, col);
        col = shootingStars(az, elev, t, col);
        col = planet(d, t, col);
        col = sun(az, elev, t, col);
        col = wisps(d, elev, t, facing, col);
        col = searchlights(d, elev, t, col);
        col = equaliser(az, elev, col);
        col = clouds(az, elev, t, facing, col);
        col += mix(PINK, ORANGE, facing * 0.4) * 0.45 * exp(-elev * 40.0); // glow along the horizon
        col = farHills(az, elev, col, facing);
        col = mountains(az, elev, col);
    } else {
        col = floorColour(d, az, t, facing, 1.0 / max(-d.y, 1.0e-4));
    }
    if (!raised) { // (the city is behind the peaks)
        col = city(d, elev, t, col);
    }
    // the palms along the highway, in front of whatever is behind them (far side first)
    float depthA, depthB;
    vec4 palmsA = roadPalms(d, PALM_SIDE, t, facing, depthA);
    vec4 palmsB = roadPalms(d, -PALM_SIDE, t, facing, depthB);
    float scene = peaks > 0.0 ? peaks : elev < 0.0 ? 1.0 / max(-d.y, 1.0e-4) : 1.0e9;
    if (depthA < depthB) {
        vec4 swap = palmsA; palmsA = palmsB; palmsB = swap;
        float swapDepth = depthA; depthA = depthB; depthB = swapDepth;
    }
    if (depthA < scene) {
        col = col * (1.0 - palmsA.a) + palmsA.rgb;
    }
    if (depthB < scene) {
        col = col * (1.0 - palmsB.a) + palmsB.rgb;
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
