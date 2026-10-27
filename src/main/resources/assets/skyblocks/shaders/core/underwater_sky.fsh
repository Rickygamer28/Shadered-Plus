#version 150

// Shadered+'s underwater sky (our own code): down in clear blue sea. Overhead the bright rippling surface, the sun a
// blur of light through it; shafts of light slanting down and swaying; all round, blue water fading into the
// distance, kelp swaying far off, the aquariums' own fish swimming round you (a school of sardines wheeling past,
// clownfish, tangs and an angelfish close by, a grouper and a tuna further off, a sea turtle, a manta ray high above,
// a shark far out, a jellyfish drifting), bubbles rising; below, a sandy floor with light rippling on it, rocks and seagrass. Walls of these blocks: the sea
// right outside your windows.

uniform float Time; // ticks

in vec3 dir;

out vec4 fragColor;

const float PI = 3.14159265;
const float TAU = 6.2831853;
const vec3 SUN = vec3(0.28, 0.93, 0.24);

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 hash22(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

const float Deep = 0.0; // (not the deep sea: the creatures' everyday colours)

// the aquariums' fish and sea creatures: their shapes, colours and how they swim
#moj_import <skyblocks:aqua_creatures.glsl>

float hash11(float n) {
    return hash12(vec2(n, n * 0.37 + 1.3));
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1.0, 0.0)), f.x), mix(hash12(i + vec2(0.0, 1.0)), hash12(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm4(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

// rippling light: a moving web of bright lines (as the surface's waves focus the sunlight)
float caustic(vec2 p, float t) {
    float c = 0.0;
    vec2 q = p;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        q = vec2(q.x + 0.6 * sin(q.y * 1.3 + t * (0.6 + 0.2 * fi)), q.y + 0.6 * sin(q.x * 1.1 - t * (0.5 + 0.15 * fi)));
        c += 1.0 / (1.0 + 30.0 * abs(sin(q.x * 1.7) * sin(q.y * 1.9)));
    }
    return c / 3.0;
}

// the water's own colour looking this way: bright above, deep blue all round, dark far below
vec3 water(vec3 d) {
    vec3 c = mix(vec3(0.02, 0.2, 0.34), vec3(0.18, 0.6, 0.74), smoothstep(-0.1, 0.9, d.y));
    c = mix(c, vec3(0.0, 0.06, 0.15), smoothstep(0.0, -0.9, d.y));
    return c + vec3(0.3, 0.45, 0.4) * pow(max(dot(d, normalize(SUN)), 0.0), 6.0) * 0.35;
}

float wrapA(float a) {
    return mod(a + PI, TAU) - PI;
}

void main() {
    vec3 d = normalize(dir);
    float t = Time / 20.0;
    float az = atan(d.z, d.x);
    float el = asin(clamp(d.y, -1.0, 1.0));
    vec3 sun = normalize(SUN);
    vec3 col = water(d);

    if (d.y > 0.05) { // the surface overhead: bright through its window straight up, mirror-dark beyond it, rippling
        vec2 p = d.xz * (6.0 / d.y);
        float far = length(p);
        float win = smoothstep(0.6, 0.72, d.y); // (the window of sky seen straight up)
        float rip = caustic(p * 0.35, t * 1.1);
        vec3 s = mix(vec3(0.15, 0.45, 0.6), vec3(0.75, 0.95, 1.0), win) * (0.75 + 0.5 * rip);
        s += vec3(1.0, 0.95, 0.8) * pow(max(dot(d, sun), 0.0), 60.0) * 2.0 * (0.8 + 0.4 * rip); // (the sun through it)
        col = mix(col, s, exp(-far * 0.045) * smoothstep(0.05, 0.25, d.y));
    }
    if (d.y < -0.04) { // the sea floor: sand in soft ripples, light dancing on it, rocks and tufts of seagrass
        vec2 p = d.xz * (4.0 / -d.y);
        float far = length(p);
        float rip = 0.5 + 0.5 * sin(p.x * 3.0 + 1.5 * fbm4(p * 0.6));
        vec3 sand = vec3(0.78, 0.72, 0.52) * (0.8 + 0.2 * rip) * (0.85 + 0.25 * fbm4(p * 4.0));
        sand += vec3(0.5, 0.75, 0.8) * caustic(p * 0.9, t * 1.3) * 0.5;
        float rf = fbm4(p * 0.35 + 3.0);
        float rock = smoothstep(0.64, 0.67, rf);
        float lit = clamp((fbm4(p * 0.35 + 3.0 + vec2(0.15, 0.15)) - rf) * -14.0 + 0.6, 0.2, 1.2); // (lit on the sun's side)
        vec3 stone = vec3(0.42, 0.44, 0.4) * (0.75 + 0.4 * fbm4(p * 3.0)) * lit + vec3(0.4, 0.6, 0.6) * caustic(p * 0.9, t * 1.3) * 0.3;
        stone = mix(stone, vec3(0.25, 0.4, 0.2), smoothstep(0.5, 0.8, fbm4(p * 2.0 + 5.0)) * 0.5); // (weedy)
        sand = mix(sand, stone, rock);
        float grass = smoothstep(0.6, 0.7, fbm4(p * 0.5 + 9.0)) * step(0.45, noise(p * 14.0 + vec2(sin(t + p.y), 0.0) * 0.3));
        sand = mix(sand, vec3(0.15, 0.45, 0.2), grass * (1.0 - rock));
        col = mix(col, sand * vec3(0.7, 0.9, 1.0), exp(-far * 0.07) * smoothstep(-0.04, -0.2, d.y));
    }

    // kelp far off all round: tall swaying strands rising from the floor into the haze
    {
        float cells = 46.0;
        float x = az / TAU * cells;
        for (int k = -1; k <= 1; k++) {
            float ci = floor(x) + float(k);
            float h = hash11(ci * 1.37);
            if (h < 0.45) continue;
            float top = 0.05 + 0.45 * hash11(ci * 2.11);
            float k01 = clamp((el + 0.12) / (top + 0.12), 0.0, 1.0);
            float sway = 0.18 * sin(t * 0.5 + ci * 1.7 + el * 6.0) * k01;
            float cx = ci + 0.5 + (hash11(ci * 3.3) - 0.5) * 0.5 + sway;
            float w = (0.08 + 0.09 * smoothstep(0.3, 0.7, abs(fract(el * 16.0 + ci) - 0.5) * 2.0)) * (1.0 - 0.5 * k01); // (leaves along it)
            if (abs(x - cx) < w && el > -0.12 && el < top) {
                float depth = 0.45 + 0.4 * hash11(ci * 5.1); // (how far off: hazier)
                vec3 kc = mix(vec3(0.12, 0.25, 0.08), vec3(0.3, 0.42, 0.12), k01);
                col = mix(col, kc, 1.0 - depth);
            }
        }
    }

    // light shafts slanting down through the water, swaying
    {
        vec3 u = normalize(cross(sun, vec3(0.0, 0.0, 1.0))), v = cross(sun, u);
        float a = atan(dot(d, v), dot(d, u));
        float shafts = pow(noise(vec2(a * 9.0 + 0.4 * sin(t * 0.3), t * 0.15)), 3.0) + 0.6 * pow(noise(vec2(a * 23.0 - t * 0.1, 3.0)), 4.0);
        float toward = smoothstep(-0.3, 0.9, dot(d, sun));
        col += vec3(0.4, 0.75, 0.8) * shafts * toward * 0.35 * smoothstep(-0.5, 0.2, d.y);
    }

    // the fish swimming round you (the aquariums' own, in 3D): the nearest one in front wins
    {
        float hitT = 1.0e9;
        vec3 fishCol = vec3(0.0);
        float fishA = 0.0;
        for (int i = 0; i < 20; i++) {
            float fi = float(i);
            int kind;
            float R, h, w, size;
            if (i < 10) { // a school of sardines, wheeling round together
                kind = 17; R = 3.2 + (hash11(fi * 3.1) - 0.5) * 0.7; h = 0.4 + (hash11(fi * 5.3) - 0.5) * 0.6; w = 0.22; size = 1.6;
            } else if (i == 10) { kind = 0; R = 2.2; h = -0.3; w = -0.18; size = 1.6; }   // clownfish
            else if (i == 11) { kind = 1; R = 2.6; h = 0.6; w = 0.15; size = 1.6; }       // blue tang
            else if (i == 12) { kind = 2; R = 2.0; h = 0.15; w = 0.2; size = 1.5; }       // yellow tang
            else if (i == 13) { kind = 4; R = 2.8; h = -0.6; w = -0.12; size = 1.6; }     // angelfish
            else if (i == 14) { kind = 13; R = 6.0; h = 1.3; w = 0.05; size = 1.4; }      // sea turtle
            else if (i == 15) { kind = 10; R = 9.0; h = 0.2; w = -0.04; size = 1.3; }     // shark
            else if (i == 16) { kind = 12; R = 7.0; h = 2.6; w = 0.035; size = 1.6; }     // manta ray
            else if (i == 17) { kind = 15; R = 3.6; h = 1.3; w = 0.02; size = 1.2; }      // jellyfish
            else if (i == 18) { kind = 14; R = 4.6; h = -1.0; w = 0.07; size = 1.3; }     // grouper
            else { kind = 11; R = 5.2; h = 0.9; w = -0.1; size = 1.3; }                    // tuna
            Species sp = species(kind);
            float a0 = (i < 10 ? 0.0 : fi * 2.399) + (i < 10 ? (hash11(fi * 7.7) - 0.5) * 0.5 : 0.0);
            float ang = a0 + w * t;
            float bob = 0.08 * sin(t * 0.4 + fi * 1.7);
            if (i < 10) { // (each weaving a little in the school)
                R += 0.08 * sin(t * 1.1 + fi * 2.0);
                bob += 0.06 * sin(t * 1.3 + fi);
            }
            vec3 c = vec3(R * cos(ang), h + bob, R * sin(ang));
            float sgn = w < 0.0 ? -1.0 : 1.0;
            vec3 fwd = normalize(vec3(-sin(ang), 0.05 * cos(t * 0.4 + fi * 1.7), cos(ang)) * sgn);
            vec3 up = vec3(0.0, 1.0, 0.0);
            float squeeze = 0.0;
            if (kind == 15) { // (a jellyfish upright, squeezing and relaxing)
                fwd = vec3(1.0, 0.0, 0.0);
                float jph = fract(t * 0.4 + fi * 0.37);
                squeeze = smoothstep(0.0, 0.12, jph) * (1.0 - smoothstep(0.12, 0.9, jph));
                c.y += 0.05 * squeeze;
            }
            vec3 side = normalize(cross(fwd, up));
            up = cross(side, fwd);
            float reach = size * (length(sp.body) + sp.tail.x * 2.5 + sp.tail.y * (kind == 15 ? 2.5 : 0.6) + sp.fin.z + sp.fin.y + 0.05);
            float bq = dot(-c, d), cq = dot(c, c) - reach * reach;
            float disc = bq * bq - cq;
            if (disc < 0.0) continue;
            float t0 = max(-bq - sqrt(disc), 0.05), t1 = min(-bq + sqrt(disc), hitT);
            if (t0 >= t1) continue;
            vec3 lo = vec3(dot(-c, fwd), dot(-c, up), dot(-c, side)) / size;
            vec3 ld = vec3(dot(d, fwd), dot(d, up), dot(d, side));
            float wave = t * (9.0 - 5.0 * clamp(sp.body.x, 0.0, 1.0)) + fi * 2.0;
            float flap = kind == 13 ? sin(t * 1.4 + fi * 1.3) : sin(t * 3.0 + fi * 1.3);
            gBeat = 1.0; gBend = 0.0; gFinSway = sin(t * 1.6 + fi); gMouth = 0.5 + 0.5 * sin(t * 2.3 + fi * 1.7);
            gTime = t; gPulse = squeeze; gWalk = 0.0;
            float tm = t0, tf = -1.0;
            int part = 0;
            vec3 q = vec3(0.0);
            for (int k = 0; k < 80; k++) {
                float dd = fishSDF(kind, sp, lo + ld * (tm / size), wave, flap, squeeze, part, q) * size;
                if (dd < 0.0015 * tm) { tf = tm; break; }
                tm += max(dd * 0.7, 0.0008 * tm);
                if (tm > t1) break;
            }
            if (tf < 0.0) continue;
            vec3 pl = lo + ld * (tf / size);
            float ee = max(0.0007, 0.008 * min(sp.body.x, sp.body.y));
            vec2 e = vec2(ee, -ee);
            int dt2;
            vec3 dq;
            vec3 g = e.xyy * fishSDF(kind, sp, pl + e.xyy, wave, flap, squeeze, dt2, dq) + e.yyx * fishSDF(kind, sp, pl + e.yyx, wave, flap, squeeze, dt2, dq)
                   + e.yxy * fishSDF(kind, sp, pl + e.yxy, wave, flap, squeeze, dt2, dq) + e.xxx * fishSDF(kind, sp, pl + e.xxx, wave, flap, squeeze, dt2, dq);
            vec3 n = normalize(fwd * g.x + up * g.y + side * g.z);
            vec3 base = fishColour(kind, q, part, hash11(fi * 13.3));
            bool isEye = false;
            if (isFish(kind) && part == 0 && abs(q.z) > 0.55) { // (its eye)
                float er = sp.body.y * eyeScale(kind);
                vec2 ev = vec2((q.x - eyeAt(kind).x) * sp.body.x, (q.y - eyeAt(kind).y) * sp.body.y) / er;
                float eyeD = length(ev);
                if (eyeD < 0.115) { isEye = true; base = eyeD < 0.07 ? vec3(0.015) : eyeRing(kind); }
                else if (eyeD < 0.13) base *= 0.6;
            }
            if (kind == 13 && part == 1 && abs(q.z) > 0.55) {
                float eyeD = length(vec2((q.x - 0.45) * 1.2, q.y - 0.2));
                if (eyeD < 0.13) { base = vec3(0.02); isEye = true; }
            }
            float diff = clamp(dot(n, sun) * 0.5 + 0.5, 0.0, 1.0);
            vec3 amb = mix(vec3(0.2, 0.32, 0.45), vec3(0.55, 0.75, 0.88), n.y * 0.5 + 0.5);
            vec3 shade = base * (amb * 0.85 + vec3(1.0, 0.97, 0.9) * diff * 0.75);
            shade += vec3(1.0) * pow(max(dot(n, normalize(sun - d)), 0.0), isEye ? 120.0 : 40.0) * (isEye ? 1.2 : 0.35);
            shade += vec3(0.5, 0.8, 1.0) * pow(1.0 - max(dot(n, -d), 0.0), 3.0) * 0.25; // (light round its edges, from the water)
            shade += vec3(0.6, 0.9, 1.0) * caustic(vec2(dot(c + d * tf, vec3(1.0, 0.0, 0.0)), (c + d * tf).z) * 1.5, t * 1.2) * max(n.y, 0.0) * 0.25;
            hitT = tf;
            fishCol = mix(shade, water(d), 1.0 - exp(-tf * 0.16)); // (fading into the blue far off)
            fishA = kind == 15 ? 0.6 : 1.0;
        }
        col = mix(col, fishCol, fishA);
    }

    // bubbles rising past, and bits drifting in the water
    for (int b = 0; b < 14; b++) {
        float fb = float(b);
        float bAz = hash11(fb * 7.7) * TAU;
        float rise = mod(t * (0.05 + 0.04 * hash11(fb * 2.3)) + hash11(fb * 4.1), 1.4) - 0.6;
        float bEl = rise;
        vec2 bp = vec2(wrapA(az - bAz - 0.02 * sin(t * 2.0 + fb)) * cos(el), el - bEl);
        float r = 0.004 + 0.004 * hash11(fb * 9.3);
        float dd = length(bp);
        if (dd < r) col += vec3(0.6, 0.85, 0.9) * (0.25 + 0.75 * smoothstep(r * 0.4, r, dd)) * 0.5;
    }
    vec2 sp = vec2(az * 30.0, el * 30.0 + t * 0.15);
    vec2 sc = floor(sp);
    if (hash12(sc) > 0.93) {
        vec2 so = sc + 0.5 + (vec2(hash12(sc + 3.1), hash12(sc + 7.7)) - 0.5) * 0.6;
        col += vec3(0.5, 0.7, 0.7) * smoothstep(0.08, 0.0, length(sp - so)) * 0.35;
    }
    fragColor = vec4(col, 1.0);
}
