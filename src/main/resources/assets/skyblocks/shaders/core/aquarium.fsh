#version 150

// Shadered+'s aquarium (our own code): every face of a tank (touching aquarium blocks) looks into the same water.
// The view ray is traced through the tank: fish (3D, swimming round, tails beating), rising bubbles, a sandy floor
// with moving light patterns from the surface, seaweed swaying on the back glass, the water's colour deepening with
// distance, and a dark frame round the tank's edges.

uniform vec3 Eye;      // the eye, relative to the camera (as the positions)
uniform vec3 BoxMin;   // the tank's corner, relative to the camera
uniform vec3 BoxSize;
uniform float Time;    // seconds
uniform float Seed;
// which spots of the box are water: 1 bit each, 24 to a number, 4 to a vec4 (spot = x + sizeX * (z + sizeZ * y))
uniform vec4 Occ0;
uniform vec4 Occ1;
uniform vec4 Occ2;
uniform vec4 Occ3;
uniform vec4 Occ4;
uniform vec4 Occ5;
uniform vec4 Occ6;
uniform vec4 Occ7;
uniform vec4 Occ8;
uniform vec4 Occ9;
uniform vec4 Occ10;
uniform vec4 Occ11;
uniform vec4 Occ12;
uniform vec4 Occ13;
uniform vec4 Occ14;
uniform vec4 Occ15;
uniform float Full;    // 1: the whole box is water (a box-shaped tank, or too big to say spot by spot)
uniform sampler2D FishTex; // per fish, 4 texels (worked out on the CPU each frame): (middle, size), (forward, species), (up, swim wave), (tail beat, turn bend, jellyfish squeeze, -)
uniform float FishCount;
uniform float Ocean;
uniform float Night;   // 0 day .. 1 night: the tank lights dimmed, the jellyfish glowing
// decorations: (x, y, z of its base, type + 8 * (turn + 16 * size * 10))
uniform vec4 Deco0;
uniform vec4 Deco1;
uniform vec4 Deco2;
uniform vec4 Deco3;
uniform vec4 Deco4;
uniform vec4 Deco5;
uniform vec4 Deco6;
uniform vec4 Deco7;
uniform vec4 Deco8;
uniform vec4 Deco9;
uniform vec4 Deco10;
uniform vec4 Deco11;
uniform vec4 Deco12;
uniform vec4 Deco13;
uniform vec4 Deco14;
uniform vec4 Deco15;
uniform vec4 Deco16;
uniform vec4 Deco17;
uniform vec4 Deco18;
uniform vec4 Deco19;
uniform vec4 Deco20;
uniform vec4 Deco21;
uniform vec4 Deco22;
uniform vec4 Deco23;
uniform float DecoCount;   // 1: the ocean tank (big sea animals, deeper water, coral)

in vec3 fragPos;
in vec2 faceUV;
in vec4 edges;

out vec4 fragColor;

const float PI = 3.14159265;

float hash11(float n) {
    return fract(sin(n * 12.9898 + Seed * 7.13) * 43758.5453);
}

vec2 hash22(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

// light patterns from the rippling surface, on a horizontal plane
float caustics(vec2 p, float t) {
    p *= 2.2;
    vec2 i = floor(p), f = fract(p);
    float f1 = 8.0, f2 = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(x, y);
            vec2 o = hash22(i + g);
            o = 0.5 + 0.4 * sin(t * 0.9 + 6.2831 * o);
            float d = length(g + o - f);
            if (d < f1) { f2 = f1; f1 = d; } else if (d < f2) { f2 = d; }
        }
    }
    return exp(-(f2 - f1) * 9.0);
}

// ray against an ellipsoid (centre c, axes ax/ay/az, radii r): distance, or -1
float ellipsoid(vec3 ro, vec3 rd, vec3 c, vec3 ax, vec3 ay, vec3 az, vec3 r, out vec3 n, out vec3 local) {
    vec3 o = ro - c;
    vec3 lo = vec3(dot(o, ax), dot(o, ay), dot(o, az)) / r;
    vec3 ld = vec3(dot(rd, ax), dot(rd, ay), dot(rd, az)) / r;
    float a = dot(ld, ld), b = dot(lo, ld), cc = dot(lo, lo) - 1.0;
    float h = b * b - a * cc;
    if (h < 0.0) {
        return -1.0;
    }
    float t = (-b - sqrt(h)) / a;
    if (t < 0.0) {
        return -1.0;
    }
    local = lo + ld * t;
    vec3 gn = local / r;
    n = normalize(ax * gn.x + ay * gn.y + az * gn.z);
    return t;
}

// A species: body radii (along, up, sideways), tail radii, fin radii (a dorsal fin; the turtle's flippers), how fast.
// 0-9 the normal tank (clownfish, blue tang, yellow tang, neon tetra, angelfish, goldfish, betta, pufferfish, guppy,
// discus); 10-19 the ocean tank (shark, tuna, manta ray, sea turtle, grouper, jellyfish, dolphin, sardine, ocean
// sunfish, basking shark).
struct Species {
    vec3 body;
    vec3 tail;
    vec3 fin;
    float speed;
};

Species species(int k) {
    if (k == 0) return Species(vec3(0.16, 0.075, 0.035), vec3(0.06, 0.065, 0.01), vec3(0.07, 0.03, 0.008), 1.0);
    if (k == 1) return Species(vec3(0.16, 0.09, 0.03), vec3(0.06, 0.07, 0.01), vec3(0.12, 0.03, 0.008), 1.0);
    if (k == 2) return Species(vec3(0.13, 0.11, 0.03), vec3(0.05, 0.06, 0.01), vec3(0.11, 0.08, 0.008), 0.9);
    if (k == 3) return Species(vec3(0.11, 0.04, 0.025), vec3(0.04, 0.04, 0.008), vec3(0.0), 1.4);
    if (k == 4) return Species(vec3(0.11, 0.13, 0.025), vec3(0.05, 0.08, 0.008), vec3(0.06, 0.15, 0.008), 0.6);
    if (k == 5) return Species(vec3(0.13, 0.09, 0.065), vec3(0.09, 0.09, 0.012), vec3(0.06, 0.04, 0.008), 0.7);
    if (k == 6) return Species(vec3(0.12, 0.05, 0.03), vec3(0.13, 0.17, 0.008), vec3(0.11, 0.11, 0.008), 0.6);
    if (k == 7) return Species(vec3(0.14, 0.085, 0.08), vec3(0.045, 0.05, 0.006), vec3(0.0), 0.5);
    if (k == 8) return Species(vec3(0.08, 0.03, 0.02), vec3(0.085, 0.075, 0.006), vec3(0.04, 0.035, 0.005), 1.3);
    if (k == 9) return Species(vec3(0.12, 0.13, 0.025), vec3(0.04, 0.05, 0.008), vec3(0.11, 0.035, 0.008), 0.6);
    if (k == 10) return Species(vec3(0.9, 0.18, 0.16), vec3(0.25, 0.3, 0.03), vec3(0.18, 0.22, 0.03), 0.8);
    if (k == 11) return Species(vec3(0.45, 0.13, 0.11), vec3(0.12, 0.18, 0.02), vec3(0.08, 0.1, 0.02), 1.3);
    if (k == 12) return Species(vec3(0.35, 0.05, 0.75), vec3(0.35, 0.015, 0.015), vec3(0.0), 0.6);
    if (k == 13) return Species(vec3(0.4, 0.16, 0.33), vec3(0.12, 0.08, 0.09), vec3(0.18, 0.03, 0.55), 0.5);
    if (k == 14) return Species(vec3(0.55, 0.25, 0.2), vec3(0.15, 0.2, 0.03), vec3(0.3, 0.08, 0.02), 0.5);
    if (k == 15) return Species(vec3(0.25, 0.18, 0.25), vec3(0.06, 0.4, 0.06), vec3(0.0), 0.25);
    if (k == 16) return Species(vec3(0.8, 0.17, 0.17), vec3(0.12, 0.04, 0.28), vec3(0.15, 0.18, 0.03), 1.1);
    if (k == 18) return Species(vec3(0.5, 0.42, 0.12), vec3(0.05, 0.4, 0.02), vec3(0.14, 0.6, 0.03), 0.35); // ocean sunfish
    if (k == 19) return Species(vec3(1.25, 0.3, 0.27), vec3(0.22, 0.4, 0.04), vec3(0.2, 0.38, 0.045), 0.45); // basking shark
    // the bottom creatures: how far they reach (forward/back, up, sideways), for the quick tests
    if (k == 20) return Species(vec3(0.15, 0.13, 0.11), vec3(0.0), vec3(0.0), 0.5);  // hermit crab
    if (k == 21) return Species(vec3(0.16, 0.09, 0.17), vec3(0.0), vec3(0.0), 1.0);  // crab
    if (k == 22) return Species(vec3(0.63, 0.08, 0.31), vec3(0.0), vec3(0.0), 0.8);  // stingray
    if (k == 23) return Species(vec3(0.17, 0.13, 0.17), vec3(0.0), vec3(0.0), 0.5);  // octopus
    if (k == 24) return Species(vec3(0.38, 0.1, 0.17), vec3(0.0), vec3(0.0), 0.4);   // lobster
    if (k == 25) return Species(vec3(0.13, 0.08, 0.06), vec3(0.0), vec3(0.0), 0.8);  // shrimp
    return Species(vec3(0.12, 0.035, 0.025), vec3(0.04, 0.035, 0.006), vec3(0.0), 1.5);
}

vec3 hue(float h) {
    return clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

// how close to the edge of a cell of a random net (small on the lines): the discus's and the turtle's patterns
float cellEdge(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    float f1 = 8.0, f2 = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(float(x), float(y));
            float d = length(g + hash22(i + g) - f);
            if (d < f1) { f2 = f1; f1 = d; } else if (d < f2) { f2 = d; }
        }
    }
    return f2 - f1;
}

// smooth random variation (0..1), and a few layers of it (finer and finer): blotches, mottling, uneven colour
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash22(i).x, hash22(i + vec2(1.0, 0.0)).x, f.x), mix(hash22(i + vec2(0.0, 1.0)).x, hash22(i + vec2(1.0, 1.0)).x, f.x), f.y);
}

float fbm(vec2 p) {
    return 0.5 * vnoise(p) + 0.3 * vnoise(p * 2.03 + 7.1) + 0.2 * vnoise(p * 4.1 + 3.3);
}

// 1 inside a round spot (one per cell, placed at random), 0 elsewhere
float spot(vec2 p, float r) {
    vec2 o = hash22(floor(p)) * 0.5 + 0.25;
    return 1.0 - smoothstep(r * 0.8, r, length(fract(p) - o));
}

// a fish's colour at a point on it (q: that part's own coordinates, x along it nose-first, -1..1; part 0 body,
// 1 tail (the turtle's head and flippers; the jellyfish's arms), 2 fin (the jellyfish's tentacles); on the fins
// q.y is the height out from the body and q.z says which: 0 the back's, -1 underneath, 2 a side fin, 3 a filament);
// rnd: this fish's own random number (colour variants). Each matched to a photo of the real fish.
vec3 critterColour(int kind, vec3 p, int part, float rnd);

vec3 fishColour(int kind, vec3 q, int part, float rnd) {
    if (kind >= 20) {
        return critterColour(kind, q, part, rnd);
    }
    bool tail = part == 1, fin = part == 2;
    bool anal = fin && q.z < -0.5, pect = fin && q.z > 1.5 && q.z < 2.5, fil = fin && q.z > 2.5;
    bool dorsal = fin && !anal && !pect && !fil;
    float tk = 0.5 - 0.5 * q.x; // along the tail: 0 at its root, 1 at its tip
    if (kind == 0) { // clownfish: orange, three white bands edged black, black-rimmed fins
        vec3 o = mix(vec3(1.0, 0.56, 0.12), vec3(0.95, 0.35, 0.02), smoothstep(-0.5, 0.6, q.y)) * (0.93 + 0.12 * fbm(q.xy * 7.0));
        if (part == 0) {
            float wob = (fbm(q.xy * vec2(5.0, 7.0) + rnd * 3.0) - 0.5) * 0.05;
            float b1 = abs(q.x - 0.4 + 0.1 * q.y * q.y) - 0.08;  // behind the eye, curving
            float b2 = abs(q.x + 0.1 - 0.12 * q.y) - 0.11;        // the middle, leaning forward at the top
            float b3 = abs(q.x + 0.86) - 0.06;                     // at the tail root
            float b = min(b1, min(b2, b3)) + wob;
            return b < 0.0 ? vec3(0.97) * (0.95 + 0.05 * fbm(q.xy * 12.0)) : b < 0.035 ? vec3(0.03) : o;
        }
        float edge = tail ? smoothstep(0.8, 0.88, tk) : pect ? smoothstep(0.62, 0.78, abs(q.x)) : smoothstep(0.72, 0.84, abs(q.y));
        return mix(o, vec3(0.03), edge);
    }
    if (kind == 1) { // blue tang: royal blue, the black "palette" mark with a blue window, yellow tail
        vec3 blue = vec3(0.08, 0.3, 0.95) * (0.85 + 0.25 * fbm(q.xy * 8.0 + rnd * 5.0)), black = vec3(0.02, 0.03, 0.08), yellow = vec3(1.0, 0.85, 0.08);
        if (tail) return abs(q.y) > 0.78 ? black : yellow;
        if (pect) return yellow;
        if (dorsal) return q.y > 0.72 ? blue : black;
        if (anal) return abs(q.y) > 0.72 ? black : blue;
        float lower = mix(0.18, -0.22, smoothstep(0.6, -0.1, q.x)) + 0.24 * smoothstep(-0.4, -0.95, q.x);
        bool mark = q.x < 0.58 && q.y > lower && q.y < 0.8;
        bool window = length((q.xy - vec2(-0.12, 0.42)) / vec2(0.36, 0.15)) < 1.0;
        return mark && !window ? black : blue;
    }
    if (kind == 2) { // yellow tang: all yellow, a little green on the belly, a white spine at the tail root
        vec3 y = vec3(1.0, 0.86, 0.05);
        if (part > 0) return y * 0.95;
        if (q.x < -0.84 && abs(q.y) < 0.08) return vec3(0.95);
        y *= 0.95 + 0.05 * sin(q.y * 45.0 + sin(q.x * 8.0) * 2.0);
        return mix(y, vec3(0.78, 0.9, 0.15), smoothstep(-0.1, -0.6, q.y) * 0.6) * (0.94 + 0.1 * fbm(q.xy * 6.0));
    }
    if (kind == 3) { // neon tetra: glowing blue line, red rear belly, silvery, see-through fins
        if (part > 0) return tail && tk < 0.2 && abs(q.y) < 0.3 ? vec3(0.9, 0.2, 0.2) : vec3(0.55, 0.65, 0.85);
        if (abs(q.y - 0.12) < 0.13 && q.x < 0.6 && q.x > -0.6) return vec3(0.15, 0.85, 1.0) * 1.35;
        if (q.y < -0.01 && q.y > -0.8 && q.x < 0.15) return vec3(1.0, 0.15, 0.12) * 1.1;
        return q.y > 0.0 ? vec3(0.55, 0.58, 0.62) : vec3(0.88, 0.88, 0.92);
    }
    if (kind == 4) { // angelfish: silver, black bands (one through the eye), orange shoulders, long fins and filaments
        vec3 silver = vec3(0.86, 0.86, 0.9), black = vec3(0.06), orange = vec3(0.95, 0.42, 0.12);
        if (fil) return vec3(0.7, 0.82, 1.0);
        if (tail) return mix(silver, vec3(1.0), 0.3);
        if (pect) return silver;
        if (fin) return q.x > 0.6 ? black : abs(fract(q.x * 1.6 + 0.3) - 0.5) < 0.1 ? black * 2.0 : silver * 0.95;
        float b = min(abs(q.x - 0.62), min(abs(q.x - 0.18), abs(q.x + 0.3)));
        if (b < 0.07) return black;
        if (q.x > 0.7) return vec3(0.78, 0.62, 0.45);
        if (q.y > 0.1 && q.x > -0.3) return mix(silver, orange, smoothstep(0.1, 0.45, q.y));
        return silver;
    }
    if (kind == 5) { // goldfish: deep orange back, golden sides, pale belly
        if (part > 0) return vec3(1.0, 0.52, 0.1);
        vec3 c = mix(vec3(1.0, 0.68, 0.15), vec3(0.95, 0.42, 0.04), smoothstep(0.1, 0.7, q.y));
        c = mix(c, vec3(1.0, 0.82, 0.45), smoothstep(-0.1, -0.7, q.y));
        return mix(c, vec3(1.0, 0.38, 0.08), smoothstep(0.55, 0.9, q.x) * 0.6) * (0.88 + 0.2 * fbm(q.xy * 5.0 + rnd * 4.0));
    }
    if (kind == 6) { // betta: blue body, reddish head, huge fins blue at the base going red, pale tips
        vec3 blue = vec3(0.2, 0.25, 0.95), red = vec3(0.9, 0.12, 0.08);
        if (rnd > 0.6) { blue = rnd > 0.8 ? vec3(0.1, 0.15, 0.85) : vec3(0.55, 0.1, 0.75); red = blue * 1.3 + 0.05; }
        if (part == 0) return mix(blue * (0.8 + 0.35 * fbm(q.xy * 9.0)), vec3(0.55, 0.15, 0.25), smoothstep(0.45, 0.7, q.x));
        float away = tail ? tk : pect ? 0.3 : abs(q.y);
        vec3 c = mix(blue, red, smoothstep(0.1, 0.45, away));
        c *= 0.8 + 0.2 * sin((tail ? q.y : q.x) * 25.0 + fbm(q.xy * 4.0) * 3.0); // (streaks)
        c = mix(c, c * 1.3, smoothstep(0.55, 0.8, fbm(q.xy * 6.0 + rnd * 7.0)) * 0.5);
        return mix(c, vec3(0.95, 0.85, 0.85), smoothstep(0.85, 1.0, away) * 0.7);
    }
    if (kind == 7) { // porcupine pufferfish: pale cream, dense small black spots, a greyer band low on its side,
                     // a white belly, pink lips, see-through yellowish fins with black spots
        if (part > 0) {
            vec3 f = vec3(0.95, 0.82, 0.35) * (1.0 - 0.75 * spot(q.xy * vec2(6.0, 5.0) + rnd * 3.0, 0.16));
            return mix(vec3(1.0, 0.88, 0.45), f, smoothstep(0.0, 0.35, abs(q.y) + (1.0 - abs(q.x)) * 0.1)); // (paler at the base)
        }
        if (q.x > 1.1) return vec3(0.92, 0.58, 0.56) * (0.9 + 0.1 * fbm(vec2(q.z * 9.0, 1.0))); // (lips)
        float ang = atan(q.y, q.z);
        vec2 uv = vec2(q.x * 0.14, ang * 0.08) / 0.0105;
        float dots = spot(uv + rnd * 7.0, 0.13 + 0.12 * vnoise(uv * 0.3)) + spot(uv * 1.6 + 3.3, 0.1) * 0.8;
        vec3 c = mix(vec3(0.82, 0.66, 0.2), vec3(1.0, 0.84, 0.28), smoothstep(0.75, 0.2, q.y)); // (deeper yellow on top)
        c = mix(c, vec3(0.66, 0.52, 0.18), smoothstep(0.12, 0.0, abs(q.y + 0.38)) * 0.7);        // (the band low on its side)
        float belly = smoothstep(-0.42, -0.55, q.y);
        c *= 1.0 - 0.88 * min(dots, 1.0) * (1.0 - belly) * smoothstep(1.02, 0.92, q.x);
        c = mix(c, vec3(1.0, 0.93, 0.62), belly);
        return c * (0.95 + 0.08 * fbm(q.xy * 5.0));
    }
    if (kind == 8) { // guppy: silver-green front, deep blue back half, a big orange tail with black blotches
        if (part > 0) {
            vec3 c = rnd < 0.5 ? vec3(1.0, 0.45, 0.1) : hue(rnd) * 1.05;
            float bl = fbm(q.xy * vec2(5.0, 7.0) + rnd * 9.0);
            c = mix(c, vec3(0.04), smoothstep(0.55, 0.6, bl));
            c = mix(c, c * 1.25 + 0.05, smoothstep(0.3, 0.2, bl) * 0.6);
            return tail ? mix(c, vec3(0.8, 0.78, 0.75), smoothstep(0.85, 1.0, tk)) : c;
        }
        vec3 c = mix(vec3(0.12, 0.2, 0.55), vec3(0.62, 0.75, 0.78), smoothstep(-0.2, 0.4, q.x));
        if (q.x < -0.7) c = mix(c, vec3(0.3, 0.75, 0.85), 0.5);
        return q.y < -0.4 ? mix(c, vec3(0.85, 0.85, 0.88), 0.5) : c;
    }
    if (kind == 9) { // discus: cream with a red net pattern, orange face, red-striped fins, clear tail
        vec3 cream = vec3(0.98, 0.92, 0.85), red = vec3(0.9, 0.22, 0.08);
        if (tail) return vec3(0.85, 0.86, 0.82);
        if (fin) return fract(q.x * 9.0) < 0.35 || abs(q.y) > 0.8 ? red : cream;
        float ce = cellEdge(q.xy * vec2(4.0, 5.0) + rnd * 7.0 + (vec2(fbm(q.xy * 6.0), fbm(q.yx * 6.0 + 3.0)) - 0.5) * 0.5);
        vec3 c = mix(red * (0.85 + 0.2 * fbm(q.xy * 9.0)), cream, smoothstep(0.09, 0.14, ce));
        return q.x > 0.62 ? mix(c, vec3(1.0, 0.7, 0.45), 0.6) : c;
    }
    if (kind == 10) { // mako shark: metallic blue-grey above, a sharp white belly, gill slits
        vec3 top = vec3(0.3, 0.4, 0.56), belly = vec3(0.93, 0.94, 0.96);
        if (part > 0) return top * (dorsal ? 0.8 : 0.9);
        top *= 0.9 + 0.14 * fbm(q.xy * vec2(10.0, 6.0) + rnd * 4.0);
        float line = -0.15 + 0.1 * q.x + (fbm(q.xy * vec2(6.0, 3.0)) - 0.5) * 0.1;
        vec3 c = mix(belly, top, smoothstep(line - 0.06, line + 0.06, q.y));
        if (q.x > 0.25 && q.x < 0.5 && fract((q.x - 0.25) * 20.0) < 0.2 && abs(q.y + 0.05) < 0.35) c *= 0.6;
        if (q.x > 0.72 && abs(q.y + 0.38 - (q.x - 0.72) * 0.4) < 0.035) c = vec3(0.15, 0.1, 0.1); // (mouth)
        return c;
    }
    if (kind == 11) { // bluefin tuna: navy back, steel-blue band, silver belly, golden first fin, yellow finlets
        if (tail) return vec3(0.14, 0.15, 0.2);
        if (pect) return vec3(0.12, 0.13, 0.18);
        if (dorsal) return q.x > -0.2 ? vec3(0.7, 0.55, 0.25) : vec3(0.15, 0.16, 0.22);
        if (anal) return vec3(0.6, 0.6, 0.62);
        vec3 c = mix(vec3(0.86, 0.89, 0.92), vec3(0.35, 0.45, 0.62), smoothstep(-0.05, 0.15, q.y));
        c = mix(c, vec3(0.04, 0.07, 0.16), smoothstep(0.3, 0.5, q.y));
        if (q.y < 0.0) c *= 0.94 + 0.06 * step(0.5, fract(q.x * 16.0 + q.y * 3.0 + fbm(q.xy * 5.0)));
        return c;
    }
    if (kind == 12) { // manta ray: speckled dark brown on top, white underneath, pale-tipped head fins
        vec3 top = vec3(0.17, 0.14, 0.11);
        if (part == 2) return mix(top, vec3(0.88, 0.85, 0.72), smoothstep(0.0, 0.6, q.x));
        if (part == 0 && q.y < 0.0) return vec3(0.9, 0.9, 0.92) * (1.0 - 0.75 * spot(q.xz * 6.0 + rnd * 5.0, 0.1 + 0.08 * vnoise(q.xz * 3.0)));
        vec3 c = mix(top, vec3(0.6), step(0.9, hash22(floor(q.xz * 40.0)).x) * 0.6);
        if (part == 0) c = mix(c, vec3(0.78, 0.78, 0.76), smoothstep(0.12, 0.04, abs(q.x - 0.35 + abs(q.z) * 0.7) - 0.05 * fbm(q.xz * 8.0)) * step(0.12, abs(q.z)) * step(abs(q.z), 0.6)); // (shoulder marks)
        return c * (0.9 + 0.15 * fbm(q.xz * 7.0));
    }
    if (kind == 13) { // green sea turtle: red-brown shell with streaked plates, cream-netted scaly skin
        if (part == 0) {
            if (q.y < -0.25) return vec3(0.9, 0.84, 0.66);
            float e = cellEdge(q.xz * vec2(2.0, 2.4) + rnd * 3.0);
            float streak = 0.5 + 0.5 * sin(q.x * 31.0 + sin(q.z * 9.0) * 3.0);
            vec3 c = mix(vec3(0.48, 0.21, 0.09), vec3(0.7, 0.42, 0.2), streak * 0.5 + smoothstep(0.0, 0.5, e) * 0.3);
            return mix(vec3(0.82, 0.68, 0.48), c * (0.85 + 0.25 * fbm(q.xz * 9.0 + rnd * 3.0)), smoothstep(0.03, 0.08, e));
        }
        if (q.y < -0.4) return vec3(0.9, 0.85, 0.7);
        return cellEdge(vec2(q.x * 4.0, q.z * 4.0 + q.y * 3.0)) < 0.15 ? vec3(0.9, 0.84, 0.68) : vec3(0.45, 0.22, 0.12);
    }
    if (kind == 14) { // giant grouper: blue-grey with pale spots on the front, dark fins with yellowish spotted edges
        if (part > 0) {
            float edge = tail ? smoothstep(0.6, 0.9, tk) : pect ? smoothstep(0.3, 0.8, abs(q.x)) : smoothstep(0.5, 0.9, abs(q.y));
            return mix(vec3(0.24, 0.26, 0.3), vec3(0.7, 0.62, 0.3), spot(q.xy * 10.0, 0.18) * edge);
        }
        vec3 base = mix(vec3(0.42, 0.47, 0.54), vec3(0.32, 0.36, 0.42), smoothstep(-0.2, 0.6, q.y));
        base = mix(base, base * 0.7, smoothstep(0.5, 0.65, fbm(q.xy * vec2(5.0, 4.0) + rnd * 5.0)));
        return mix(base, vec3(0.86, 0.88, 0.9), spot(q.xy * vec2(9.0, 7.0) + rnd * 3.0, 0.13) * smoothstep(-0.3, 0.3, q.x) * 0.85);
    }
    if (kind == 15) { // jellyfish: mostly sea nettles (orange, dark stripes), some pink-purple ones
        bool nettle = rnd < 0.6;
        if (part == 1) return nettle ? vec3(0.98, 0.9, 0.86) : vec3(0.9, 0.75, 1.0);
        if (part == 2) return nettle ? vec3(1.0, 0.55, 0.45) : vec3(0.95, 0.6, 0.95);
        vec3 bell = nettle ? vec3(1.0, 0.55, 0.22) : mix(vec3(1.0, 0.45, 0.85), vec3(0.6, 0.4, 1.0), rnd);
        if (nettle && q.y > -0.3 && abs(fract(atan(q.z, q.x) * 2.546) - 0.5) < 0.1) bell = vec3(0.5, 0.1, 0.07);
        bell *= 0.9 + 0.2 * fbm(vec2(atan(q.z, q.x) * 3.0, q.y * 4.0));
        return mix(bell, vec3(1.0, 0.9, 0.85), smoothstep(-0.2, -0.7, q.y)) * 1.15;
    }
    if (kind == 16) { // dolphin: dark grey back, lighter sides, pale belly, a smile
        vec3 dark = vec3(0.36, 0.39, 0.44);
        if (part > 0) return dark;
        vec3 c = mix(vec3(0.86, 0.86, 0.88), vec3(0.55, 0.58, 0.63), smoothstep(-0.55, -0.25, q.y));
        c = mix(c, dark, smoothstep(0.1, 0.5, q.y));
        if (q.x > 0.78 && abs(q.y + 0.12 + (q.x - 0.78) * 0.3) < 0.025) c *= 0.55;
        c *= 0.95 + 0.08 * fbm(q.xy * vec2(3.0, 9.0) + rnd * 6.0);
        if (abs(fract(q.x * 6.0 + q.y * 9.0 + rnd * 3.0) - 0.5) < 0.012 && vnoise(q.xy * 4.0 + rnd) > 0.7) c *= 1.12; // (old scratches)
        return c;
    }
    if (kind == 18) { // ocean sunfish: brownish grey, brown mottling round the head, white specks behind, pale below
        if (pect) return vec3(0.6, 0.45, 0.25);
        if (part > 0) return mix(vec3(0.33, 0.31, 0.3), vec3(0.85), spot(q.xy * 12.0, 0.1));
        if (q.x > 0.9 && abs(q.y + 0.12) < 0.1) return vec3(0.85, 0.65, 0.62);
        float m = sin(q.x * 17.0 + sin(q.y * 13.0) * 2.0) * sin(q.y * 15.0 + q.x * 7.0);
        vec3 upper = mix(vec3(0.5, 0.47, 0.44), vec3(0.34, 0.24, 0.16), step(0.2, m) * smoothstep(-0.2, 0.3, q.x));
        upper = mix(upper, vec3(0.88, 0.87, 0.84), spot(q.xy * vec2(11.0, 13.0), 0.12) * smoothstep(0.2, -0.3, q.x));
        return mix(vec3(0.9, 0.88, 0.83), upper, smoothstep(-0.2, 0.15, q.y));
    }
    if (kind == 19) { // basking shark: dark grey-brown, faintly blotched, paler underneath; inside its mouth, white gill rakers
        if (q.z > 6.5) { // inside the mouth: rows of white gill rakers deep in the throat, a dark pink lining, pale lips
            float z = (q.z - 7.0) / 0.4;
            if (q.x < 0.62) return fract(z * 5.0 + 0.5) < 0.55 ? mix(vec3(0.92, 0.9, 0.86), vec3(0.6, 0.55, 0.52), smoothstep(0.62, 0.4, q.x)) : vec3(0.08, 0.03, 0.03);
            if (q.x > 1.02) return vec3(0.82, 0.68, 0.66);
            return mix(vec3(0.3, 0.15, 0.15), vec3(0.55, 0.38, 0.38), smoothstep(0.62, 1.0, q.x)) * (0.85 + 0.15 * step(0.5, fract(z * 5.0 + 0.5)));
        }
        vec3 c = mix(vec3(0.55, 0.52, 0.49), vec3(0.32, 0.3, 0.28), smoothstep(-0.5, 0.3, q.y));
        if (part > 0) return c * 0.92;
        c *= 0.94 + 0.08 * sin(q.x * 9.0 + sin(q.y * 7.0 + rnd * 6.0) * 2.0) * sin(q.y * 6.0 + q.x * 4.0); // (soft mottling)
        float gx = q.x + 0.1 * q.y * q.y;
        if (gx > -0.01 && gx < 0.27 && fract(gx / 0.065 + 0.08) < 0.16 && abs(q.y) < 0.95) c *= 0.6; // (gill slits)
        return mix(c, vec3(0.62, 0.6, 0.57), smoothstep(0.85, 1.0, q.x) * 0.5); // (a paler snout)
    }
    // sardine: dark blue-green back, bright silver sides, golden tail and back fin
    if (tail || dorsal) return vec3(0.85, 0.78, 0.5);
    if (fin) return vec3(0.85, 0.85, 0.82);
    vec3 c = mix(vec3(0.86, 0.88, 0.92), vec3(0.16, 0.27, 0.38), smoothstep(0.1, 0.4, q.y));
    if (q.x > -0.5 && q.x < 0.4) c *= 1.0 - 0.55 * spot(vec2(q.x * 6.0 + rnd * 3.0, (q.y - 0.12) * 5.0 + 0.5), 0.2);
    return q.x > 0.45 && q.x < 0.62 && abs(q.y) < 0.5 ? mix(c, vec3(0.8, 0.7, 0.45), 0.35) : c;
}

// how big a fish's eye is, against the usual
// where a fish's eye is (along it, nose = 1; up it, in body heights)
vec2 eyeAt(int kind) {
    return kind == 7 ? vec2(0.5, 0.32) : vec2(0.62, 0.22);
}

float eyeScale(int kind) {
    return kind == 7 ? 1.3 : kind == 18 ? 1.25 : kind == 3 || kind == 17 ? 1.2 : kind == 19 ? 0.6 : kind == 10 ? 0.8 : 1.0;
}

// the ring round a fish's pupil
vec3 eyeRing(int kind) {
    if (kind == 10 || kind == 16 || kind == 19 || kind == 1) return vec3(0.05);
    if (kind == 9 || kind == 4) return vec3(0.75, 0.2, 0.08);
    if (kind == 3) return vec3(0.3, 0.8, 0.7);
    if (kind == 18 || kind == 17 || kind == 8) return vec3(0.9, 0.9, 0.88);
    if (kind == 7) return vec3(0.35, 0.45, 0.5);
    if (kind == 0) return vec3(0.9, 0.4, 0.05);
    return vec3(0.95, 0.9, 0.6);
}

// One part of a fish, in the fish's own frame (x forward, y up, z sideways): where, its radii, how it's turned
// (yaw about up, then pitch about sideways), and what it is (0 body, 1 tail, 2 fin).
struct Part {
    vec3 at;
    vec3 r;
    float yaw;
    float pitch;
    int type;
};

float gPulse = 0.0; // (a jellyfish's squeeze, 0..1)

const int PARTS = 13;

// part j of a fish of this kind (radii scaled by the species, not yet by the fish's size); swing: tail beat (-1..1)
Part fishPart(int kind, Species sp, int j, float swing, float flap) {
    vec3 b = sp.body, tl = sp.tail, fn = sp.fin;
    bool forked = kind == 0 || kind == 1 || kind == 2 || kind == 3 || kind == 4 || kind == 9 || kind == 10 || kind == 11 || kind == 14 || kind == 17;
    if (kind == 13) { // sea turtle: shell, head, four flippers
        if (j == 0) return Part(vec3(0.0), b, 0.0, 0.0, 0);
        if (j == 1) return Part(vec3(b.x + tl.x * 0.4, 0.0, 0.0), tl, 0.0, 0.0, 1);
        if (j == 2 || j == 3) { float s = j == 2 ? 1.0 : -1.0;
            return Part(vec3(b.x * 0.35, -b.y * 0.15 + b.y * 0.5 * swing, s * b.z * 0.95), vec3(fn.x * 0.7, fn.y, fn.z * 0.62), s * (0.5 + 0.3 * swing), -0.25 * swing, 1); }
        if (j == 4 || j == 5) { float s = j == 4 ? 1.0 : -1.0;
            return Part(vec3(-b.x * 0.7, -b.y * 0.2, s * b.z * 0.7), vec3(fn.x * 0.6, fn.y, fn.z * 0.25), -s * (0.7 + 0.15 * swing), 0.0, 1); }
        return Part(vec3(0.0), vec3(0.0), 0.0, 0.0, 0);
    }
    if (kind == 15) { // jellyfish: a hollow dome, four frilly arms from its middle, eight thin tentacles from its rim
        float sq = 1.0 - 0.15 * gPulse, st = 1.0 + 0.12 * gPulse; // (following the bell as it squeezes)
        if (j == 0) return Part(vec3(0.0), b, 0.0, 0.0, 0);
        if (j <= 4) { float a = float(j) * 1.5708 + 0.4;
            return Part(vec3(b.x * 0.16 * cos(a) * sq, 0.05 * b.y * st - tl.y, b.z * 0.16 * sin(a) * sq), tl, 0.0, 0.0, 1); }
        { float a = float(j - 5) * 0.7854 + 0.39;
            vec3 r = vec3(0.02, tl.y * 1.15, 0.02);
            return Part(vec3(b.x * 0.84 * cos(a) * sq, -0.5 * b.y * st - r.y, b.z * 0.84 * sin(a) * sq), r, 0.0, 0.0, 2); }
    }
    if (kind == 12) { // manta ray: wide body, long thin tail, two head fins
        if (j == 0) return Part(vec3(0.0), b, 0.0, 0.0, 0);
        if (j == 1) return Part(vec3(-b.x - tl.x * 0.8, 0.0, 0.0), tl, swing * 0.15, 0.0, 1);
        if (j == 2 || j == 3) { float s = j == 2 ? 1.0 : -1.0;
            return Part(vec3(b.x * 0.95, -b.y * 0.2, s * b.z * 0.18), vec3(b.x * 0.25, b.y * 0.6, b.z * 0.06), 0.0, 0.0, 2); }
        return Part(vec3(0.0), vec3(0.0), 0.0, 0.0, 0);
    }
    if (j == 0) return Part(vec3(0.0), b, 0.0, 0.0, 0);
    if (j == 1) { // the narrow part before the tail
        if (kind == 7) return Part(vec3(0.0), vec3(0.0), 0.0, 0.0, 0);
        return Part(vec3(-b.x * 0.82, 0.0, 0.0), vec3(b.x * 0.38, b.y * 0.42, b.z * 0.62), swing * 0.25, 0.0, 0);
    }
    vec3 tailRoot = vec3(-b.x * 1.12, 0.0, 0.0);
    if (kind == 16) { // dolphin: flat flukes, sideways
        if (j == 2 || j == 3) { float s = j == 2 ? 1.0 : -1.0;
            return Part(tailRoot + vec3(-tl.x * 0.6, 0.0, s * tl.z * 0.45), vec3(tl.x, tl.y, tl.z * 0.55), swing * 0.4 - s * 0.45, swing * 0.3, 1); }
    } else if (forked) { // forked tail: two lobes (the shark's top lobe bigger)
        if (j == 2 || j == 3) { float s = j == 2 ? 1.0 : -1.0;
            float big = kind == 10 ? (s > 0.0 ? 1.25 : 0.75) : 1.0;
            return Part(tailRoot + vec3(-tl.x * 0.55, s * tl.y * 0.42 * big, 0.0), vec3(tl.x * big, tl.y * 0.5 * big, tl.z), swing * 0.5, -s * 0.55, 1); }
    } else if (j == 2) { // a round, flowing tail
        return Part(tailRoot + vec3(-tl.x * 0.6, 0.0, 0.0), tl, swing * 0.5, 0.0, 1);
    }
    if (j == 4 && fn.x > 0.0) { // dorsal fin, swept back
        return Part(vec3(-b.x * 0.12, b.y * 0.86, 0.0), fn, 0.0, 0.35, 2);
    }
    if (j == 5 || j == 6) { // side fins, gently paddling
        if (kind == 7 || kind == 8) return Part(vec3(0.0), vec3(0.0), 0.0, 0.0, 0);
        float s = j == 5 ? 1.0 : -1.0;
        float big = kind == 10 || kind == 16 ? 1.6 : 1.0;
        return Part(vec3(b.x * 0.25, -b.y * 0.35, s * b.z * 0.95), vec3(b.x * 0.22 * big, b.y * 0.07, b.z * 0.55 * big), s * (0.7 + 0.25 * flap), -0.35, 2);
    }
    if (j == 7) { // a fin underneath (long on the angelfish and betta)
        if (kind == 4 || kind == 6) return Part(vec3(-b.x * 0.2, -b.y * 0.9, 0.0), vec3(fn.x * 0.9, fn.y * 0.9, fn.z), 0.0, -0.3, 2);
        if (kind == 0 || kind == 1 || kind == 2 || kind == 9 || kind == 14) return Part(vec3(-b.x * 0.4, -b.y * 0.8, 0.0), vec3(b.x * 0.25, b.y * 0.25, b.z * 0.2), 0.0, -0.4, 2);
    }
    if (j == 8 && kind != 7) { // (the back half of the body, narrower: a teardrop)
        return Part(vec3(-b.x * 0.42, -b.y * 0.04, 0.0), vec3(b.x * 0.68, b.y * 0.8, b.z * 0.78), 0.0, 0.0, 0);
    }
    if (j == 3 && kind == 16) return Part(vec3(0.0), vec3(0.0), 0.0, 0.0, 0);
    if (j == 7 && kind == 16) return Part(vec3(0.0), vec3(0.0), 0.0, 0.0, 0);
    return Part(vec3(0.0), vec3(0.0), 0.0, 0.0, 0);
}

// ---- smooth shapes (signed distances) ----

float sdEllipsoid(vec3 p, vec3 r) {
    float k0 = length(p / r);
    float k1 = length(p / (r * r));
    return k0 * (k0 - 1.0) / max(k1, 1.0e-6);
}

float smin(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float sdCapsule(vec3 p, vec3 a, vec3 b, float r) {
    vec3 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - r;
}

float sdStar5(vec2 p, float r, float rf) {
    const vec2 k1 = vec2(0.809016994375, -0.587785252292);
    const vec2 k2 = vec2(-0.809016994375, -0.587785252292);
    p.x = abs(p.x);
    p -= 2.0 * max(dot(k1, p), 0.0) * k1;
    p -= 2.0 * max(dot(k2, p), 0.0) * k2;
    p.x = abs(p.x);
    p.y -= r;
    vec2 ba = rf * vec2(-k1.y, k1.x) - vec2(0.0, 1.0);
    float h = clamp(dot(p, ba) / dot(ba, ba), 0.0, r);
    return length(p - ba * h) * sign(p.y * ba.x - p.x * ba.y);
}

/**
 * A fish as one smooth shape: its parts blended together, the back half swinging in a travelling wave as it swims.
 * p: in the fish's own frame (x forward, y up, z sideways), in species units. ptype / q: the nearest part and the
 * point in that part's coordinates (for its colours).
 */
// how each fish is shaped: where it is deepest (0 tail root .. 1 nose), how blunt, its tail (0 round / fan,
// 1 forked, 2 the shark's (forked, top lobe longer), 3 flat flukes sideways (dolphin)), how big its belly fin is
vec4 fishShape(int kind) {
    if (kind == 0) return vec4(0.55, 0.5, 0.0, 0.6);
    if (kind == 1) return vec4(0.5, 0.45, 1.0, 0.8);
    if (kind == 2) return vec4(0.5, 0.4, 0.0, 1.8);
    if (kind == 3) return vec4(0.55, 0.5, 1.0, 0.7);
    if (kind == 4) return vec4(0.5, 0.55, 1.0, 2.6);
    if (kind == 5) return vec4(0.55, 0.55, 1.0, 0.6);
    if (kind == 6) return vec4(0.62, 0.5, 0.0, 3.5);
    if (kind == 7) return vec4(0.58, 0.52, 0.0, 0.0);
    if (kind == 8) return vec4(0.62, 0.5, 0.0, 0.4);
    if (kind == 9) return vec4(0.5, 0.6, 0.0, 0.8);
    if (kind == 10) return vec4(0.6, 0.6, 2.0, 0.35);
    if (kind == 11) return vec4(0.55, 0.35, 1.0, 0.5);
    if (kind == 14) return vec4(0.55, 0.55, 0.0, 0.7);
    if (kind == 16) return vec4(0.6, 0.4, 3.0, 0.0);
    if (kind == 18) return vec4(0.45, 0.7, 0.0, 1.7);
    if (kind == 19) return vec4(0.58, 0.7, 2.0, 0.35);
    return vec4(0.55, 0.4, 1.0, 0.5);
}

/**
 * A fish's real shape: a body whose height and width follow a fish outline along its length (blunt head, deepest
 * part, narrowing to the tail root), bulging eyes and a mouth, a thin tail fin (forked, fan, or a shark's), a thin
 * fin along the back and one underneath, paddling side fins; the back half swinging in a travelling wave.
 */
// (set for each fish before its shape is traced)
float gBend = 0.0;    // how sharply it's turning: its body curves round into the turn
float gBeat = 1.0;    // how hard it's swimming: bigger tail beats in a burst, small ones gliding
float gFinSway = 0.0; // the sunfish sweeping its tall fins side to side (it swims with them)
float gMouth = 0.5;   // breathing: the mouth opening and closing
float gTime = 0.0;

// the body alone (no fins), as in fishBody: for the basking shark's lower jaw, worked out a second time turned down
float trunkAt(int kind, Species sp, vec4 sh, vec3 p) {
    float L = sp.body.x, H = sp.body.y, W = sp.body.z;
    float s = clamp((p.x + L) / (2.0 * L), 0.0, 1.0);
    float u = (s - sh.x) / (s < sh.x ? sh.x : 1.0 - sh.x);
    float sb = sh.x - 0.06;
    float ub = (s - sb) / (s < sb ? sb : 1.0 - sb);
    float profT = pow(max(1.0 - u * u, 0.0), sh.y);
    float profB = pow(max(1.0 - ub * ub, 0.0), sh.y * 1.15);
    float root = smoothstep(0.3, 0.0, s) * 0.22;
    float hT = H * max(profT, root) + 0.002, hB = H * max(profB, root) + 0.002;
    float w = W * max(profT, root * 0.55) + 0.002;
    float py = p.y + 0.12 * H * smoothstep(0.55, 1.0, s);
    float h = py > 0.0 ? hT : hB;
    float yy = clamp(py / h, -1.0, 1.0);
    float wl = w * (1.0 - 0.28 * yy * yy);
    return max((length(vec2(py / h, p.z / wl)) - 1.0) * min(h, wl), abs(p.x) - L);
}

// a fin's height along it (kx: 0 its back end .. 1 its front), as a share of its full height
float finShape(float kx, float lean, float rag, float rk) {
    return pow(max(sin(kx * 3.1416), 0.0), 0.8) * (1.0 + lean * (0.5 - kx)) * (1.0 - rag * abs(sin(kx * rk)));
}

float fishBody(int kind, Species sp, vec3 p, float wave, float flap, out int ptype, out vec3 q) {
    float L = sp.body.x, H = sp.body.y, W = sp.body.z;
    vec4 sh = fishShape(kind);
    bool bony = kind != 10 && kind != 16 && kind != 19 && kind != 18;
    // swimming: a wave running back along the body, growing towards the tail (tuna and sharks: only the back end
    // swings; the dolphin beats up and down; the sunfish hardly bends at all), the tail fin flexing behind it
    bool stiff = kind == 10 || kind == 11 || kind == 19;
    float env = stiff ? smoothstep(-0.05 * L, -1.9 * L, p.x) * 1.15 : smoothstep(0.4 * L, -1.9 * L, p.x);
    float amp = 0.2 * L * gBeat * (kind == 18 ? 0.12 : kind == 7 ? 0.6 : 1.0);
    float ph = wave - p.x / L * 2.2;
    float sw = amp * sin(ph) * env;
    float flex = clamp((-p.x - L * 0.9) / max(sp.tail.x * 2.0, 0.001), 0.0, 1.0);
    sw += 0.35 * amp * flex * flex * sin(ph - 1.3);
    if (kind == 16) p.y += sw * 0.9; else p.z += sw;
    p.z -= 0.5 * gBend * p.x * p.x; // (turning)
    // the body's outline at this point along it: back and belly curved differently, the head sloping down to the mouth
    float s = clamp((p.x + L) / (2.0 * L), 0.0, 1.0);
    float u = (s - sh.x) / (s < sh.x ? sh.x : 1.0 - sh.x);
    float sb = sh.x - 0.06;
    float ub = (s - sb) / (s < sb ? sb : 1.0 - sb);
    float profT = pow(max(1.0 - u * u, 0.0), sh.y);
    float profB = pow(max(1.0 - ub * ub, 0.0), sh.y * 1.15);
    float root = smoothstep(0.3, 0.0, s) * (kind == 18 ? 0.75 : 0.22); // (the sunfish ends as if cut off)
    float hT = H * max(profT, root) + 0.002, hB = H * max(profB, root) + 0.002;
    float w = W * max(profT, root * (kind == 18 ? 0.8 : 0.55)) + 0.002; // (a thin tail stalk)
    float yc = kind == 7 || kind == 18 ? 0.0 : -0.12 * H * smoothstep(0.55, 1.0, s);
    float py = p.y - yc;
    float h = py > 0.0 ? hT : hB;
    float yy = clamp(py / h, -1.0, 1.0);
    float wl = kind == 7 ? w : w * (1.0 - 0.28 * yy * yy); // (narrowing to a ridge along the back and belly)
    float body = max((length(vec2(py / h, p.z / wl)) - 1.0) * min(h, wl), abs(p.x) - L);
    float mouthFace = 0.0; // (the basking shark: 1 inside its open mouth, 2 its gill rakers at the back)
    if (kind == 19) { // feeding: the pointed snout on top, under it the huge round mouth wide open, the throat ballooning
        float snout = sdEllipsoid(p - vec3(L * 0.86, -0.02 * H, 0.0), vec3(L * 0.2, H * 0.36, W * 0.4));
        float bag = sdEllipsoid(p - vec3(L * 0.7, -0.35 * H, 0.0), vec3(L * 0.45, H * 0.62, W * 0.95));
        body = smin(smin(body, snout, 0.06 * H), bag, 0.12 * H);
        // the inside: a deep cave, its round opening at the front
        float cave = sdEllipsoid(p - vec3(L * 0.8, -0.36 * H, 0.0), vec3(L * 0.47, H * 0.5, W * 0.8));
        if (-cave > body) mouthFace = 1.0;
        body = max(body, -cave);
    }
    if (kind == 19 || kind == 10) { // gill slits, cut into the skin
        for (int k = 0; k < 5; k++) {
            float xk = kind == 19 ? L * (0.0 + 0.065 * float(k)) : L * (0.27 + 0.05 * float(k));
            float slab = max(abs(p.x - xk + (kind == 19 ? 0.1 : 0.06) * L * (p.y / H) * (p.y / H)) - 0.005 * L, abs(p.y) - (kind == 19 ? 0.95 : 0.35) * H);
            body = max(body, min(-slab, body + 0.025 * H));
        }
    }
    // eyes, bulging a little, and a mouth (breathing)
    vec2 ea = eyeAt(kind);
    float se = (ea.x + 1.0) * 0.5;
    float ue = (se - sh.x) / (1.0 - sh.x);
    float we = W * pow(max(1.0 - ue * ue, 0.0), sh.y);
    float eyeR = H * 0.13 * eyeScale(kind);
    float eye = length(vec3(p.x - L * ea.x, p.y - H * ea.y, abs(p.z) - we * (kind == 7 ? 0.72 : 0.8))) - eyeR;
    if (kind != 10 && kind != 16 && kind != 19) {
        body = smin(body, eye, eyeR * 0.7);
    }
    vec3 mc = vec3(L * 1.0, -H * 0.12, 0.0), mr = vec3(L * 0.08, H * 0.05 * (0.5 + gMouth), W * 0.7);
    if (kind == 14) mr = vec3(L * 0.12, H * 0.1 * (0.6 + 0.8 * gMouth), W * 0.8); // (the grouper's big mouth)
    if (kind == 10 || kind == 16) mr.y = H * 0.04;
    if (kind == 7) { mc = vec3(L * 1.05, -H * 0.16, 0.0); mr = vec3(L * 0.05, H * 0.035 * (0.5 + gMouth), W * 0.14); } // (a small round mouth)
    if (kind != 19) body = max(body, -sdEllipsoid(p - mc, mr));
    float res = body, best = body;
    ptype = 0;
    q = p / sp.body;
    if (mouthFace > 0.5) q.z = (mouthFace > 1.5 ? 9.0 : 7.0) + 0.4 * clamp(p.z / W, -1.0, 1.0);
    // tail fin, behind the tail root
    float tl = sp.tail.x * 2.0, th = sp.tail.y, thick = max(sp.tail.z, 0.004);
    float xt = -L * 0.92 - p.x;
    float kt = clamp(xt / tl, 0.0, 1.0);
    float across = sh.z > 2.5 ? abs(p.z) : abs(p.y), sideways = sh.z > 2.5 ? abs(p.y) : abs(p.z);
    float span = sh.z > 2.5 ? sp.tail.z : th;
    if (sh.z > 2.5) thick = max(sp.tail.y * 0.5, 0.004);
    float outer, inner = -1.0;
    if (sh.z > 0.5) {
        outer = span * (0.2 + 0.8 * kt);
        inner = span * 0.85 * kt * kt;
        if (sh.z > 1.5 && sh.z < 2.5) { // the shark's: the top lobe longer than the bottom one
            outer = p.y > 0.0 ? span * (0.2 + 1.0 * kt) : span * (0.2 + (kind == 19 ? 0.85 : 0.55) * kt);
        }
    } else {
        outer = span * (0.25 + 0.75 * sqrt(max(sin(kt * 1.5708), 0.0))); // a rounded fan
    }
    float tlr = tl * (bony ? 1.0 - 0.05 * abs(sin(across / max(span, 0.001) * 14.0)) : 1.0);
    float tail = max(max(max(across - outer, inner - across), max(-xt, xt - tlr)) * 0.7, sideways - thick * (1.0 - 0.6 * kt));
    res = smin(res, tail, 0.05 * L);
    if (tail < best) {
        best = tail; ptype = 1;
        q = vec3(1.0 - 2.0 * kt, p.y / span, p.z / thick);
    }
    // the fin along the back and one underneath: swept back (sharks' and the dolphin's strongly), rippling as it
    // swims (the sunfish's sweeping side to side)
    float sweep = bony ? 0.35 : kind == 18 ? 0.1 : kind == 19 ? 0.45 : 0.9;
    if (sp.fin.x > 0.0) {
        float fx1 = L * 0.3, fx0 = fx1 - sp.fin.x * 2.2;
        float lift = max(py - hT * 0.92, 0.0);
        float xs = p.x + lift * sweep;
        float kx = clamp((xs - fx0) / (fx1 - fx0), 0.0, 1.0);
        float rag = bony ? 0.07 : 0.0;
        float dh = sp.fin.y * 1.0 * finShape(kx, 0.5, rag, 22.0);
        float slope = sp.fin.y * (finShape(min(kx + 0.02, 1.0), 0.5, rag, 22.0) - finShape(max(kx - 0.02, 0.0), 0.5, rag, 22.0)) / (0.04 * (fx1 - fx0));
        float up = clamp(lift / max(dh, 0.001), 0.0, 1.0);
        float upS = clamp(lift / sp.fin.y, 0.0, 1.0); // (smooth: over the fin's full height)
        float zf = p.z - (kind == 18 ? gFinSway * sp.fin.y * 0.3 : sp.fin.y * 0.06 * gBeat * sin(wave * 1.3 - kx * 6.0)) * upS * upS;
        float dorsal = max(max(max((py - (hT * 0.92 + dh)) / sqrt(1.0 + slope * slope), hT * 0.55 - py), max(fx0 - xs, xs - fx1)) * 0.55, (abs(zf) - sp.fin.z * (1.0 - 0.6 * upS)) * 0.8);
        res = smin(res, dorsal, 0.04 * L);
        if (dorsal < best) {
            best = dorsal; ptype = 2;
            q = vec3(kx * 2.0 - 1.0, up, 0.0);
        }
    }
    if (sh.w > 0.0) {
        float ax1 = -L * 0.05, ax0 = -L * 0.65;
        float lift = max(-py - hB * 0.92, 0.0);
        float xs = p.x + lift * sweep;
        float kx = clamp((xs - ax0) / (ax1 - ax0), 0.0, 1.0);
        float rag = bony ? 0.07 : 0.0;
        float dh = H * 0.35 * sh.w * finShape(kx, 0.4, rag, 18.0);
        float slope = H * 0.35 * sh.w * (finShape(min(kx + 0.02, 1.0), 0.4, rag, 18.0) - finShape(max(kx - 0.02, 0.0), 0.4, rag, 18.0)) / (0.04 * (ax1 - ax0));
        float down = clamp(lift / max(dh, 0.001), 0.0, 1.0);
        float downS = clamp(lift / (H * 0.35 * sh.w), 0.0, 1.0);
        float zf = p.z - (kind == 18 ? -gFinSway * H * 0.35 * sh.w * 0.3 : H * 0.02 * gBeat * sin(wave * 1.3 - kx * 6.0 + 1.0)) * downS * downS;
        float anal = max(max(max((-py - (hB * 0.92 + dh)) / sqrt(1.0 + slope * slope), hB * 0.55 + py), max(ax0 - xs, xs - ax1)) * 0.55, (abs(zf) - max(sp.fin.z, 0.006) * (1.0 - 0.6 * downS)) * 0.8);
        res = smin(res, anal, 0.04 * L);
        if (anal < best) {
            best = anal; ptype = 2;
            q = vec3(kx * 2.0 - 1.0, -down, -1.0);
        }
    }
    if (kind == 10 || kind == 11 || kind == 19) { // a small second fin on the back and underneath, near the tail
        float x1 = -L * 0.45, x0 = -L * 0.66;
        float kx = clamp((p.x - x0) / (x1 - x0), 0.0, 1.0);
        float dh = sp.fin.y * 0.32 * sin(kx * 3.1416);
        float d2 = max(max(max(py - (hT * 0.92 + dh), hT * 0.55 - py), max(x0 - p.x, p.x - x1)) * 0.7, abs(p.z) - sp.fin.z * 0.6);
        float d3 = max(max(max(-py - (hB * 0.92 + dh), hB * 0.55 + py), max(x0 - p.x, p.x - x1)) * 0.7, abs(p.z) - sp.fin.z * 0.6);
        float d23 = min(d2, d3);
        res = smin(res, d23, 0.03 * L);
        if (d23 < best) {
            best = d23; ptype = 2;
            q = vec3(kx * 2.0 - 1.0, d2 < d3 ? 0.5 : -0.5, d2 < d3 ? 0.0 : -1.0);
        }
    }
    // side fins, paddling (sharks' stiff and swept back)
    if (kind == 7) { // pufferfish
        // thick pouting lips round the mouth
        float lips = sdEllipsoid(p - vec3(L * 0.97, -H * 0.16, 0.0), vec3(L * 0.07, H * 0.11, W * 0.24));
        res = smin(res, max(lips, -sdEllipsoid(p - mc, mr)), 0.04 * H);
        if (lips < best) { best = lips; ptype = 0; q = vec3(1.2, -0.16, p.z / W); } // (q.x 1.2: the lips)
        // short spines lying flat all over it (the porcupine pufferfish)
        float ang = atan(py, p.z);
        vec2 sg = vec2(p.x / L * 7.0, ang * 2.6);
        sg.y += 0.5 * floor(sg.x);
        float spike = max(1.0 - length(fract(sg) - 0.5) * 3.2, 0.0);
        res -= 0.035 * H * spike * spike * smoothstep(0.98, 0.7, abs(p.x / L));
        // its big side fins: upright fans just behind the eyes, whirring
        vec3 pp = vec3(p.x - L * 0.18, py + H * 0.05, abs(p.z) - W * 0.8);
        float a = 1.0 + 0.3 * sin(gTime * 9.0 + wave * 0.3); // (standing well out from its side, never flat against it)
        vec3 lq = vec3(pp.x * cos(a) - pp.z * sin(a), pp.y, pp.x * sin(a) + pp.z * cos(a));
        lq.x += L * 0.12;
        float pect = sdEllipsoid(lq, vec3(L * 0.15, H * 0.34, W * 0.06));
        res = smin(res, pect, 0.02 * L);
        if (pect < best) { best = pect; ptype = 2; q = vec3(-lq.x / (L * 0.15), lq.y / (H * 0.34), 2.0); }
        // small rounded fins on its back and underneath, far back near the tail
        for (int k = 0; k < 2; k++) {
            float sg2 = k == 0 ? 1.0 : -1.0;
            vec3 fp = vec3(p.x + L * 0.62, py * sg2 - H * 0.5, p.z);
            float tilt = 0.6 + 0.12 * sin(wave * 1.3 + float(k));
            vec3 fq = vec3(fp.x * cos(tilt) - fp.y * sin(tilt), fp.x * sin(tilt) + fp.y * cos(tilt), fp.z);
            fq.y -= H * 0.18;
            float fin = sdEllipsoid(fq, vec3(L * 0.13, H * 0.3, W * 0.05));
            res = smin(res, fin, 0.02 * L);
            if (fin < best) { best = fin; ptype = 2; q = vec3(fq.x / (L * 0.13), sg2 * clamp(fq.y / (H * 0.3) * 0.5 + 0.5, 0.0, 1.0), k == 0 ? 0.0 : -1.0); }
        }
    }
    if (kind != 8 && kind != 7) {
        float big = kind == 10 || kind == 16 || kind == 19 ? 1.7 : 1.0;
        vec3 pp = vec3(p.x - L * 0.3, py + H * 0.35, abs(p.z) - W * 0.85);
        float a = big > 1.5 ? 1.0 + 0.06 * flap : 0.7 + 0.3 * flap;
        vec3 lq = vec3(pp.x * cos(a) - pp.z * sin(a), pp.y, pp.x * sin(a) + pp.z * cos(a));
        lq.y -= lq.z * (big > 1.5 ? 0.35 : 0.15 * flap); // (angled down, or rowing)
        float pect = sdEllipsoid(lq, vec3(L * 0.2 * big, H * 0.05, W * 0.5 * big));
        res = smin(res, pect, 0.02 * L);
        if (pect < best) {
            best = pect; ptype = 2;
            q = vec3(lq.x / (L * 0.2 * big), 0.0, 2.0);
        }
    }
    // the pair of small fins under the belly
    if (bony && kind != 7 && kind != 8) {
        vec3 pp = vec3(p.x - L * 0.02, py + hB * 0.82, abs(p.z) - W * 0.3);
        float a = 0.65 + 0.1 * flap;
        vec3 lq = vec3(pp.x * cos(a) + pp.y * sin(a), -pp.x * sin(a) + pp.y * cos(a), pp.z);
        float pelv = sdEllipsoid(lq, vec3(L * 0.13, H * 0.12, W * 0.07));
        res = smin(res, pelv, 0.02 * L);
        if (pelv < best) {
            best = pelv; ptype = 2;
            q = vec3(lq.x / (L * 0.13), 0.0, 2.0);
        }
    }
    if (kind == 4) { // the angelfish's two long filaments, hanging down from its chest
        vec3 fp = vec3(p.x, p.y, abs(p.z));
        float fil = sdCapsule(fp, vec3(L * 0.3, -H * 0.55, W * 0.25), vec3(-L * 0.1 + 0.05 * L * sin(wave * 0.5), -H * 2.0, W * 0.4), 0.006);
        res = min(res, fil);
        if (fil < best) {
            best = fil; ptype = 2;
            q = vec3(0.0, 0.0, 3.0);
        }
    }
    return res;
}

// ---- the creatures on the bottom: 20 hermit crab, 21 crab, 22 stingray, 23 (blue-ringed) octopus, 24 lobster,
// 25 (fire) shrimp. In their own frame: origin on the sand under them, x forward, y up, z sideways. Parts (for their
// colours): 0 body, 1 legs / claws / arms / tail, 2 eyes, 3 pale bits (eye stalks, a shrimp's legs and feelers),
// 4 the hermit crab's shell, 5 feelers ----

float gWalk = 0.0; // (how far it has walked: its legs step with it)

float sdTaper(vec3 p, vec3 a, vec3 b, float ra, float rb) {
    vec3 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - mix(ra, rb, h);
}

void addPart(inout float res, inout float best, inout int ptype, float d, int id, float k) {
    res = k > 0.0 ? smin(res, d, k) : min(res, d);
    if (d < best) {
        best = d;
        ptype = id;
    }
}

// a jointed walking leg (hip, knee, foot), stepping: lifted and swung forward in turn
void addLeg(inout float res, inout float best, inout int ptype, vec3 p, vec3 hip, vec3 knee, vec3 foot, float r, float phase,
            vec3 swingDir, float stride, int id) {
    float lift = max(sin(phase), 0.0);
    vec3 f = foot + vec3(0.0, lift * stride * 0.9, 0.0) + swingDir * cos(phase) * stride;
    vec3 k = knee + vec3(0.0, lift * stride * 0.6, 0.0) + swingDir * cos(phase) * stride * 0.5;
    addPart(res, best, ptype, sdTaper(p, hip, k, r, r * 0.85), id, 0.0);
    addPart(res, best, ptype, sdTaper(p, k, f, r * 0.85, r * 0.4), id, 0.0);
}

float critterSDF(int kind, vec3 p, float wave, out int ptype, out vec3 q) {
    q = p;
    ptype = 0;
    float res = 1.0e9, best = 1.0e9;
    float stepPh = gWalk * 6.2831;
    vec3 sp = vec3(p.x, p.y, abs(p.z));
    float sgn = p.z < 0.0 ? 1.0 : -1.0; // (the two sides step in turn)
    if (kind == 21) { // crab: wide dark shell, red legs, two red claws, eyes on short stalks; walks sideways
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.0, 0.05, 0.0), vec3(0.058, 0.026, 0.084)), 0, 0.0);
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.0, 0.04, 0.0), vec3(0.05, 0.022, 0.07)), 1, 0.01); // (underside)
        addPart(res, best, ptype, sdTaper(sp, vec3(0.045, 0.055, 0.02), vec3(0.056, 0.07, 0.023), 0.004, 0.0035), 1, 0.003);
        addPart(res, best, ptype, length(sp - vec3(0.057, 0.074, 0.023)) - 0.0075, 2, 0.0);
        float open = 0.5 + 0.5 * sin(wave * 0.5 + (p.z > 0.0 ? 0.0 : 2.0));
        addPart(res, best, ptype, sdTaper(sp, vec3(0.035, 0.042, 0.06), vec3(0.075, 0.038, 0.085), 0.011, 0.009), 1, 0.004);
        addPart(res, best, ptype, sdEllipsoid(sp - vec3(0.098, 0.034, 0.078), vec3(0.03, 0.017, 0.017)), 1, 0.006);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.12, 0.04, 0.074), vec3(0.152, 0.038, 0.066), 0.007, 0.0015), 1, 0.002);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.12, 0.027, 0.074), vec3(0.15, 0.027 - 0.018 * open, 0.067), 0.006, 0.0015), 1, 0.002);
        for (int k = 0; k < 4; k++) {
            float x = 0.028 - float(k) * 0.022;
            addLeg(res, best, ptype, sp, vec3(x, 0.04, 0.066), vec3(x - 0.006 * float(k), 0.072, 0.112),
                   vec3(x - 0.014 * float(k), 0.0, 0.148), 0.0065, stepPh + float(k) * 1.57 + sgn * 1.57, vec3(0.0, 0.0, 1.0), 0.014, 1);
        }
    } else if (kind == 20) { // hermit crab: a ridged cream shell, orange body, big black eyes on white stalks, a big claw
        vec3 sc = p - vec3(-0.035, 0.068, 0.0);
        float rock = 0.06 * sin(stepPh * 0.5);
        sc.xy = mat2(cos(rock), -sin(rock), sin(rock), cos(rock)) * sc.xy;
        float shell = sdEllipsoid(sc, vec3(0.066, 0.06, 0.055));
        shell -= 0.0035 * sin(atan(sc.y, sc.x) * 4.0 + sc.z * 60.0); // (spiral ridges)
        addPart(res, best, ptype, shell, 4, 0.0);
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.028, 0.045, 0.0), vec3(0.036, 0.03, 0.034)), 0, 0.012);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.05, 0.058, 0.01), vec3(0.068, 0.09, 0.015), 0.0055, 0.005), 3, 0.004);
        addPart(res, best, ptype, sdEllipsoid(sp - vec3(0.071, 0.1, 0.016), vec3(0.009, 0.013, 0.008)), 2, 0.002);
        float aw = 0.15 * sin(wave * 0.8 + p.z * 40.0);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.062, 0.06, 0.006), vec3(0.1, 0.07 + aw * 0.1, 0.02), 0.0028, 0.002), 5, 0.0);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.1, 0.07 + aw * 0.1, 0.02), vec3(0.14, 0.02, 0.045 + aw * 0.05), 0.002, 0.0012), 5, 0.0);
        // the big claw (left) and the small one
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.078, 0.034, 0.03), vec3(0.034, 0.03, 0.017)), 1, 0.008);
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.074, 0.03, -0.03), vec3(0.024, 0.02, 0.012)), 1, 0.008);
        for (int k = 0; k < 2; k++) {
            float x = 0.04 - float(k) * 0.022;
            addLeg(res, best, ptype, sp, vec3(x, 0.04, 0.03), vec3(x + 0.025, 0.062, 0.075),
                   vec3(x + 0.03, 0.0, 0.1), 0.0085, stepPh + float(k) * 3.14 + sgn * 1.57, vec3(1.0, 0.0, 0.0), 0.012, 1);
        }
    } else if (kind == 22) { // stingray: a flat rounded disc, its edges rippling, eyes on top, a long thin tail
        vec3 d = p - vec3(0.0, 0.03, 0.0);
        float zr = clamp(abs(d.z) / 0.27, 0.0, 1.2);
        d.y -= 0.028 * zr * zr * sin(wave - d.x * 14.0);
        d.x += 0.07 * zr * zr;
        addPart(res, best, ptype, sdEllipsoid(d, vec3(0.25, 0.032, 0.27)), 0, 0.0);
        addPart(res, best, ptype, length(sp - vec3(0.085, 0.056, 0.045)) - 0.013, 2, 0.01);
        float sway = 0.04 * sin(wave * 0.4);
        vec3 t1 = vec3(-0.43, 0.034, sway * 0.5), t2 = vec3(-0.62, 0.03, sway);
        addPart(res, best, ptype, sdTaper(p, vec3(-0.22, 0.035, 0.0), t1, 0.016, 0.008), 1, 0.02);
        addPart(res, best, ptype, sdTaper(p, t1, t2, 0.008, 0.002), 1, 0.0);
        addPart(res, best, ptype, sdEllipsoid(p - vec3(-0.31, 0.05, sway * 0.2), vec3(0.03, 0.014, 0.004)), 1, 0.006);
    } else if (kind == 23) { // blue-ringed octopus: a round head and body, eight arms crawling, curling at the tips
        vec3 m = p - vec3(-0.03, 0.08, 0.0);
        m.xy = mat2(0.82, -0.57, 0.57, 0.82) * m.xy;
        addPart(res, best, ptype, sdEllipsoid(m, vec3(0.042, 0.05, 0.036)), 0, 0.0);
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.006, 0.045, 0.0), vec3(0.03, 0.028, 0.032)), 0, 0.02);
        addPart(res, best, ptype, length(sp - vec3(0.02, 0.062, 0.025)) - 0.0095, 2, 0.006);
        for (int k = 0; k < 8; k++) {
            float th = (float(k) + 0.5) * 0.7854;
            vec3 dir = vec3(cos(th), 0.0, sin(th)), perp = vec3(-sin(th), 0.0, cos(th));
            vec3 prev = vec3(0.005, 0.035, 0.0) + dir * 0.015;
            float rPrev = 0.012;
            for (int j = 1; j <= 4; j++) {
                float s = float(j) / 4.0;
                float curl = 0.03 * s * s * sin(wave * 0.8 + float(k) * 1.3 + s * 4.0 + stepPh * 0.5);
                vec3 pt = vec3(0.005, 0.0, 0.0) + dir * (0.015 + s * 0.13) + perp * curl;
                pt.y = 0.035 * (1.0 - s) + 0.004 + 0.02 * s * s * max(sin(wave * 0.6 + float(k) * 2.1), 0.0);
                float r = mix(0.012, 0.0022, s);
                addPart(res, best, ptype, sdTaper(p, prev, pt, rPrev, r), 1, j == 1 ? 0.012 : 0.0);
                prev = pt;
                rPrev = r;
            }
        }
    } else if (kind == 24) { // lobster: a long shell, a segmented tail ending in a fan, two big claws, long feelers
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.04, 0.055, 0.0), vec3(0.085, 0.034, 0.037)), 0, 0.0);
        addPart(res, best, ptype, sdTaper(p, vec3(0.115, 0.06, 0.0), vec3(0.15, 0.064, 0.0), 0.008, 0.0015), 0, 0.004);
        for (int k = 0; k < 6; k++) {
            float fk = float(k);
            addPart(res, best, ptype, sdEllipsoid(p - vec3(-0.055 - fk * 0.033, 0.05 - fk * 0.003, 0.0),
                                                  vec3(0.021, 0.031 - fk * 0.0025, 0.034 - fk * 0.003)), 0, 0.003);
        }
        for (int j = 0; j < 5; j++) { // the tail fan
            float a = (float(j) - 2.0) * 0.38 + 0.08 * sin(wave * 0.6);
            vec3 c = vec3(-0.25 - 0.03 * cos(a), 0.034, 0.03 * sin(a));
            vec3 l = p - c;
            l.xz = mat2(cos(a), sin(a), -sin(a), cos(a)) * l.xz;
            addPart(res, best, ptype, sdEllipsoid(l, vec3(0.034, 0.005, 0.015)), 0, 0.004);
        }
        float big = p.z > 0.0 ? 1.12 : 0.9;
        float open = 0.5 + 0.5 * sin(wave * 0.4 + (p.z > 0.0 ? 0.0 : 1.7));
        addPart(res, best, ptype, sdTaper(sp, vec3(0.1, 0.045, 0.03), vec3(0.15, 0.045, 0.075), 0.012, 0.011), 1, 0.005);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.15, 0.045, 0.075), vec3(0.19, 0.04, 0.077), 0.011, 0.012), 1, 0.005);
        addPart(res, best, ptype, sdEllipsoid(sp - vec3(0.235, 0.04, 0.077), vec3(0.055, 0.02, 0.03) * big), 1, 0.008);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.28, 0.046, 0.072), vec3(0.335, 0.046, 0.062), 0.011 * big, 0.003), 1, 0.003);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.28, 0.034, 0.072), vec3(0.33, 0.034 - 0.022 * open, 0.064), 0.009 * big, 0.003), 1, 0.003);
        for (int k = 0; k < 4; k++) {
            float x = 0.07 - float(k) * 0.024;
            addLeg(res, best, ptype, sp, vec3(x, 0.035, 0.03), vec3(x - 0.01, 0.062, 0.078),
                   vec3(x - 0.02 - 0.01 * float(k), 0.0, 0.104), 0.0055, stepPh + float(k) * 1.57 + sgn * 1.57, vec3(1.0, 0.0, 0.0), 0.012, 1);
        }
        addPart(res, best, ptype, length(sp - vec3(0.11, 0.07, 0.016)) - 0.006, 2, 0.0);
        float aw = 0.02 * sin(wave * 0.7 + p.z * 30.0);
        vec3 a1 = vec3(0.25, 0.09, 0.07 + aw), a2 = vec3(0.37, 0.06 + aw, 0.13 + aw);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.13, 0.06, 0.012), a1, 0.0035, 0.0025), 5, 0.0);
        addPart(res, best, ptype, sdTaper(sp, a1, a2, 0.0025, 0.0012), 5, 0.0);
    } else { // fire shrimp: bright red, white spots, a curved tail, long white legs and feelers
        addPart(res, best, ptype, sdEllipsoid(p - vec3(0.015, 0.042, 0.0), vec3(0.028, 0.018, 0.014)), 0, 0.0);
        addPart(res, best, ptype, sdTaper(p, vec3(0.04, 0.047, 0.0), vec3(0.056, 0.05, 0.0), 0.003, 0.001), 0, 0.002);
        for (int k = 0; k < 5; k++) {
            float fk = float(k);
            addPart(res, best, ptype, sdEllipsoid(p - vec3(-0.012 - fk * 0.011, 0.042 - fk * 0.004 - fk * fk * 0.0009, 0.0),
                                                  vec3(0.009, 0.016 - fk * 0.0018, 0.012 - fk * 0.0015)), 0, 0.003);
        }
        vec3 tf = p - vec3(-0.072, 0.02, 0.0);
        tf.xy = mat2(0.8, 0.6, -0.6, 0.8) * tf.xy;
        addPart(res, best, ptype, sdEllipsoid(tf, vec3(0.012, 0.0035, 0.011)), 0, 0.003);
        addPart(res, best, ptype, length(sp - vec3(0.038, 0.052, 0.007)) - 0.0042, 2, 0.0);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.03, 0.032, 0.008), vec3(0.058, 0.026, 0.013), 0.0032, 0.0018), 0, 0.0);
        for (int k = 0; k < 5; k++) {
            float x = 0.024 - float(k) * 0.009;
            addLeg(res, best, ptype, sp, vec3(x, 0.03, 0.007), vec3(x + 0.002, 0.037, 0.02),
                   vec3(x - 0.004, 0.0, 0.026), 0.0018, stepPh * 1.5 + float(k) * 1.26 + sgn * 1.57, vec3(1.0, 0.0, 0.0), 0.005, 3);
        }
        float aw = 0.01 * sin(wave * 1.1 + p.z * 50.0);
        vec3 a1 = vec3(0.08, 0.07, 0.02 + aw), a2 = vec3(0.12, 0.052 + aw, 0.036);
        addPart(res, best, ptype, sdTaper(sp, vec3(0.042, 0.05, 0.004), a1, 0.0013, 0.001), 3, 0.0);
        addPart(res, best, ptype, sdTaper(sp, a1, a2, 0.001, 0.0008), 3, 0.0);
    }
    return res;
}

vec3 critterColour(int kind, vec3 p, int part, float rnd) {
    if (part == 2) return vec3(0.02); // (eyes)
    if (kind == 21) { // crab
        if (part == 0) {
            float rim = length(vec2(p.x / 0.058, p.z / 0.084));
            vec3 c = mix(vec3(0.13, 0.09, 0.07), vec3(0.75, 0.15, 0.08), smoothstep(0.88, 0.97, rim));
            return mix(c, vec3(0.7, 0.62, 0.5), spot(p.xz * 140.0 + rnd * 9.0, 0.08) * 0.6);
        }
        float tip = smoothstep(0.13, 0.15, p.x);
        return mix(vec3(0.88, 0.16, 0.07), vec3(1.0, 0.55, 0.15), tip * 0.8 + 0.25 * step(0.5, fract(length(p.xz) * 60.0)) * 0.3);
    }
    if (kind == 20) { // hermit crab
        if (part == 4) {
            float ridge = 0.5 + 0.5 * sin(atan(p.y - 0.068, p.x + 0.035) * 4.0 + p.z * 60.0);
            vec3 c = mix(vec3(0.82, 0.8, 0.7), vec3(0.95, 0.93, 0.86), ridge);
            c = mix(c, vec3(0.6, 0.66, 0.45), smoothstep(0.55, 0.75, fbm(p.xz * 40.0)) * 0.5);
            return c * (1.0 - 0.6 * spot(p.xy * 260.0 + p.z * 90.0, 0.07));
        }
        if (part == 3) return vec3(0.95, 0.92, 0.85);
        if (part == 5) return vec3(1.0, 0.42, 0.12);
        vec3 c = mix(vec3(1.0, 0.85, 0.65), vec3(1.0, 0.42, 0.15), smoothstep(0.02, 0.05, p.y));
        return mix(c, vec3(1.0, 0.95, 0.88), spot(vec2(p.x, p.y + p.z) * 220.0, 0.12) * 0.8);
    }
    if (kind == 22) { // stingray
        if (p.y < 0.026 && part == 0) return vec3(0.9, 0.88, 0.85);
        vec3 c = mix(vec3(0.58, 0.56, 0.52), vec3(0.68, 0.66, 0.62), fbm(p.xz * 12.0 + rnd * 5.0));
        return part == 1 ? c * 0.85 : mix(c, c * 1.1, smoothstep(0.8, 1.0, length(vec2(p.x / 0.25, p.z / 0.27))));
    }
    if (kind == 23) { // blue-ringed octopus: cream-yellow, ochre patches each ringed in electric blue edged black
        vec2 uv = part == 1 ? vec2(length(p.xz) * 45.0, atan(p.z, p.x) * 3.0) : vec2(p.x * 70.0 + p.y * 30.0, p.z * 70.0 + p.y * 50.0);
        vec2 cell = floor(uv);
        vec2 o = hash22(cell) * 0.4 + 0.3;
        float d = length(fract(uv) - o);
        vec3 c = mix(vec3(0.85, 0.75, 0.5), vec3(0.95, 0.9, 0.75), fbm(uv * 0.3));
        if (d < 0.22) c = vec3(0.6, 0.45, 0.25);                       // (the patch)
        else if (d < 0.27) c = vec3(0.05, 0.04, 0.06);                 // (black edge)
        else if (d < 0.35) c = vec3(0.15, 0.45, 1.0) * 1.5;            // (blue ring)
        else if (d < 0.39) c = vec3(0.05, 0.04, 0.06);
        return c;
    }
    if (kind == 24) { // lobster: red, speckled darker, claw tips dark
        vec3 c = vec3(0.78, 0.2, 0.1) * (0.85 + 0.25 * fbm(p.xz * 60.0));
        c *= 1.0 - 0.35 * spot(p.xz * 180.0 + p.y * 40.0, 0.12);
        if (part == 1) c = mix(c, vec3(0.2, 0.06, 0.05), smoothstep(0.22, 0.3, p.x) * 0.7);
        if (part == 5) c = vec3(0.6, 0.15, 0.1);
        return mix(c, c * 1.2 + 0.05, smoothstep(0.03, 0.0, p.y)); // (paler underneath)
    }
    // fire shrimp
    if (part == 3) return vec3(0.95, 0.94, 0.92);
    vec3 c = vec3(1.0, 0.3, 0.08);
    if (part == 0 && p.x > -0.012) c = mix(c, vec3(1.0), spot(vec2(p.x, p.y + p.z) * 160.0 + rnd * 4.0, 0.12));
    return c;
}

float fishSDF(int kind, Species sp, vec3 p, float wave, float flap, float pulse, out int ptype, out vec3 q) {
    if (kind >= 20) {
        return critterSDF(kind, p, wave, ptype, q);
    }
    if (kind != 12 && kind != 13 && kind != 15) {
        return fishBody(kind, sp, p, wave, flap, ptype, q);
    }
    float L = sp.body.x;
    if (kind == 12) { // the manta: wings swept back, beating slowly up and down in a wave out to the tips
        float zr = clamp(abs(p.z) / sp.body.z, 0.0, 1.2);
        p.y -= sp.body.z * 0.2 * zr * zr * sin(wave * 0.6 - zr * 1.2);
        p.x += 0.4 * sp.body.x * zr * zr;
    }
    if (kind == 13) {
        p.z -= 0.5 * gBend * p.x * p.x;
    }
    if (kind == 15) { // the jellyfish's arms and tentacles trailing, swaying in slow waves running down them
        float below = max(-p.y - sp.body.y * 0.5, 0.0);
        float bend = below * below * 0.6 + below * 0.04;
        p.x += bend * sin(gTime * 1.3 + p.y * 4.5 + wave * 0.2);
        p.z += bend * 0.8 * cos(gTime * 1.05 + p.y * 3.8 + wave * 0.15);
    }
    float swing = kind == 12 || kind == 15 ? sin(wave) : kind == 13 ? flap : 0.0;
    float res = 1.0e9, best = 1.0e9;
    ptype = 0;
    q = p / sp.body;
    for (int j = 0; j < PARTS; j++) {
        Part pt = fishPart(kind, sp, j, swing, flap);
        if (pt.r.x <= 0.0) {
            continue;
        }
        vec3 r = pt.r;
        vec3 d0 = p - pt.at;
        vec3 fx = vec3(cos(pt.yaw), 0.0, sin(pt.yaw));
        vec3 fz = vec3(-sin(pt.yaw), 0.0, cos(pt.yaw));
        vec3 ax = fx * cos(pt.pitch) + vec3(0.0, sin(pt.pitch), 0.0);
        vec3 ay = vec3(0.0, cos(pt.pitch), 0.0) - fx * sin(pt.pitch);
        vec3 lq = vec3(dot(d0, ax), dot(d0, ay), dot(d0, fz));
        float d;
        if (kind == 15 && j == 0) { // the bell: a hollow dome, squeezing in quickly then slowly relaxing
            vec3 rb = r * vec3(1.0 - 0.15 * pulse, 1.0 + 0.12 * pulse, 1.0 - 0.15 * pulse);
            d = max(sdEllipsoid(lq, rb), -sdEllipsoid(lq + vec3(0.0, rb.y * 0.6, 0.0), rb * vec3(0.85, 0.75, 0.85)));
        } else if (kind == 15) { // an arm or tentacle: a strand from its top, thinning to its end
            float hy = clamp(lq.y, -r.y, r.y);
            float rad = r.x * (0.35 + 0.65 * (hy + r.y) / (2.0 * r.y));
            d = length(vec2(length(vec2(lq.x, lq.z * (j <= 4 ? 2.0 : 1.0))), lq.y - hy)) - rad;
        } else if (kind == 13 && j >= 2) { // a flipper: long, narrowing to a point at its tip (not a round paddle)
            float along = clamp(abs(lq.z) / r.z, 0.0, 1.0);
            d = sdEllipsoid(vec3(lq.x / (1.0 - 0.6 * along), lq.y, lq.z), r) * 0.55;
        } else if (kind == 13 && j == 0) { // the shell: domed on top, flatter underneath
            d = sdEllipsoid(lq, vec3(r.x, lq.y > 0.0 ? r.y : r.y * 0.55, r.z));
        } else {
            d = sdEllipsoid(lq, r);
        }
        float k = pt.type == 0 ? 0.35 * min(sp.body.y, sp.body.z) : 0.2 * min(r.y, r.z) + 0.012 * L;
        res = smin(res, d, k);
        if (d < best) {
            best = d;
            ptype = pt.type;
            q = pt.type == 0 ? p / sp.body : lq / r;
        }
    }
    return res;
}

/**
 * A decoration on the bottom (p: from its base, y up, in its own units). 0 rock, 1 shell, 2 starfish, 3 castle,
 * 4 coral, 5 treasure chest, 6 sea anemone, 7 old vase.
 */
// the clam: two ribbed fan-shaped shells hinged at the back (-x), the top one opened up and back so you see its pearly
// inside, the soft frilled flesh in the bottom one and a pearl on it
const vec3 CLAM_HINGE = vec3(-0.1, 0.036, 0.0);

// a point in the top shell's frame (the shell turned up round the hinge; its front lifts as it opens)
vec3 clamTop(vec3 p, float t) {
    float open = 0.68 + 0.15 * sin(t * 0.45);
    vec3 h = p - CLAM_HINGE;
    h.xy = mat2(cos(open), -sin(open), sin(open), cos(open)) * h.xy; // (turned back by -open: into the shell's own frame)
    return h + CLAM_HINGE;
}

// one shell (bowl downwards when up < 0, dome upwards when up > 0): its distance, and how far in from its middle
vec2 clamShell(vec3 p, float up) {
    vec3 c = p - vec3(0.0, CLAM_HINGE.y, 0.0);
    float ang = atan(c.z, p.x - CLAM_HINGE.x);                  // (round the hinge: the ribs fan out from it)
    float rib = abs(sin(ang * 7.0));
    vec3 r = vec3(0.12, up > 0.0 ? 0.042 : 0.05, 0.1);
    float e = sdEllipsoid(c - vec3(0.012, 0.0, 0.0), r);
    float shell = abs(e + 0.0035) - 0.0035 - 0.0022 * rib;      // (thin, ridged)
    float edge = 0.006 * rib * smoothstep(0.03, 0.1, length(c.xz)); // (a scalloped rim)
    shell = max(shell, up > 0.0 ? -(c.y - edge * 0.5) : c.y + edge * 0.5 - edge);
    return vec2(shell, e);
}

vec4 clamParts(vec3 p, float t) {
    p.y -= 0.012; // (sitting on the sand, not sunk into it)
    float low = clamShell(p, -1.0).x;
    float up = clamShell(clamTop(p, t), 1.0).x;
    vec3 f = p - vec3(0.01, 0.028, 0.0);
    float frill = 0.0025 * sin(atan(f.z, f.x) * 18.0 + 1.3) * smoothstep(0.04, 0.08, length(f.xz));
    float flesh = sdEllipsoid(f, vec3(0.088, 0.016, 0.072)) - frill;
    float pearl = length(p - vec3(0.022, 0.058, 0.0)) - 0.016;
    return vec4(low, up, flesh, pearl);
}

// ---- seaweed: a tuft of seagrass (12), a tall kelp (13) swaying in the water ----
float gDecoSz = 1.0; // (the decoration's size: for the kelp, its height in blocks)

// one blade of seagrass: a thin ribbon from the sand (h tall, turned to a), leaning over as it rises, swaying
float grassBlade(vec3 p, float h, float lean, float a, float t, float ph) {
    vec2 r = mat2(cos(a), -sin(a), sin(a), cos(a)) * p.xz;
    float k = clamp(p.y / h, 0.0, 1.0);
    float bend = lean * k * k + 0.05 * sin(t * 1.3 + ph + k * 2.5) * k;
    float w = 0.026 * (1.0 - 0.75 * k) + 0.004;                    // (wide at the root, to a point)
    vec2 q = vec2(abs(r.x - bend) - 0.005, abs(r.y) - w);
    float d2 = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0);
    return max(d2, max(p.y - h, -p.y)) * 0.8;
}

float seagrassSDF(vec3 p, float t) {
    float d = 1e9;
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        float a = fi * 0.9 + 0.5 * sin(fi * 2.3);                  // (each blade leaning its own way, out from the tuft)
        vec3 o = vec3(cos(a), 0.0, sin(a)) * 0.05 * (0.4 + 0.6 * fract(fi * 0.618));
        vec2 out2 = vec2(cos(a), sin(a));
        vec3 q = p - o;
        q.xz = vec2(dot(q.xz, out2), dot(q.xz, vec2(-out2.y, out2.x))); // (into the blade's frame: x outwards)
        d = min(d, grassBlade(q, 0.26 + 0.3 * fract(fi * 0.371 + 0.2), 0.1 + 0.14 * fract(fi * 0.53), 1.5708, t, fi * 1.9));
    }
    return d;
}

// the kelp's stalk: how far it has swayed at height y (blocks), sideways (blocks)
vec2 kelpSway(float yb, float t) {
    float k = yb / max(gDecoSz, 0.5);
    return vec2(0.07 * sin(t * 0.6 + yb * 1.4), 0.05 * sin(t * 0.47 + yb * 1.1 + 1.3)) * k;
}

// p in its own units (its height 1): s = how much of that a block is
float kelpSDF(vec3 p, float t, out int part) {
    float s = 1.0 / max(gDecoSz, 0.5);
    float yb = p.y / s;
    vec2 c = kelpSway(yb, t) * s;
    float stalk = max(length(p.xz - c) - 0.012 * s, max(p.y - 1.0, -p.y));
    float d = stalk;
    part = 0;
    // its leaves: long blades every so often up the stalk, first one side then the other, reaching up and out, a
    // little air bladder where each joins the stalk (the nearest two: enough for the distance)
    float leafGap = 0.15;
    float fi = floor((yb - 0.12) / leafGap + 0.5);
    for (int j = -1; j <= 1; j++) {
        float i = fi + float(j);
        if (i < 0.0) continue;
        float ly = 0.12 + i * leafGap;
        if (ly > gDecoSz - 0.12) continue;
        float a = i * 2.4 + 0.6;
        vec2 dir = vec2(cos(a), sin(a));
        vec2 cc = kelpSway(ly, t);
        vec3 q = vec3(p.x / s - cc.x, yb - ly, p.z / s - cc.y);
        vec2 r = vec2(dot(q.xz, dir), dot(q.xz, vec2(-dir.y, dir.x)));
        // (a long wavy blade, rising off the stalk then arching over, rippling along its edge)
        float along = r.x - 0.03;
        float lift = 0.32 * along - 0.9 * along * along + 0.015 * sin(t * 0.9 + i + along * 30.0);
        vec3 lq = vec3(along - 0.1, q.y - 0.03 - lift, r.y + 0.01 * sin(along * 45.0 + t + i));
        float leaf = sdEllipsoid(lq, vec3(0.12, 0.008, 0.042)) * 0.7;
        float bladder = length(vec3(r.x - 0.022, q.y - 0.012, r.y)) - 0.016;
        float lb = min(leaf, bladder) * s;
        if (lb < d) {
            d = lb;
            part = bladder < leaf ? 2 : 1;
        }
    }
    return d;
}

float decoSDF(int type, vec3 p, float t) {
    if (type == 12) {
        return seagrassSDF(p, t);
    }
    if (type == 13) {
        int part;
        return kelpSDF(p, t, part);
    }
    if (type == 8) { // a sunken ship: a broken, tilted wooden hull half in the sand, its mast snapped
        vec3 s = p;
        s.xy = mat2(0.985, -0.174, 0.174, 0.985) * s.xy;
        s.yz = mat2(0.94, -0.34, 0.34, 0.94) * s.yz;
        float hull = max(sdEllipsoid(s - vec3(0.0, 0.06, 0.0), vec3(0.45, 0.16, 0.16)), s.y - 0.12);
        hull = max(hull, -sdEllipsoid(s - vec3(0.0, 0.11, 0.0), vec3(0.41, 0.14, 0.13)));
        hull = max(hull, -sdEllipsoid(s - vec3(0.1, 0.05, 0.16), vec3(0.08, 0.06, 0.05))); // (a hole in its side)
        hull = min(hull, sdBox(s - vec3(-0.33, 0.13, 0.0), vec3(0.08, 0.07, 0.11)));          // (the cabin at the back)
        float mast = sdCapsule(s, vec3(0.05, 0.05, 0.0), vec3(-0.02, 0.42, 0.04), 0.018);
        mast = min(mast, sdCapsule(s, vec3(-0.012, 0.31, -0.13), vec3(0.0, 0.29, 0.15), 0.01));
        return max(min(hull, mast), -p.y);
    }
    if (type == 9) { // an old anchor, leaning: shank, ring, crossbar, curved arms with flukes
        vec3 a = p;
        a.xy = mat2(0.94, 0.34, -0.34, 0.94) * a.xy;
        float d = sdCapsule(a, vec3(0.0, 0.03, 0.0), vec3(0.0, 0.34, 0.0), 0.016);
        d = min(d, length(vec2(length(a.xy - vec2(0.0, 0.385)) - 0.035, a.z)) - 0.01);
        d = min(d, sdCapsule(a, vec3(0.0, 0.3, -0.09), vec3(0.0, 0.3, 0.09), 0.012));
        vec2 q2 = a.xy - vec2(0.0, 0.14);
        d = min(d, max(length(vec2(length(q2) - 0.11, a.z)) - 0.014, q2.y));
        vec3 f = vec3(abs(a.x) - 0.105, a.y - 0.165, a.z);
        d = min(d, sdEllipsoid(f, vec3(0.03, 0.04, 0.008)));
        return max(d, -p.y);
    }
    if (type == 10) { // a diver's helmet: a brass dome on its collar, round windows
        float d = sdEllipsoid(p - vec3(0.0, 0.15, 0.0), vec3(0.13, 0.14, 0.13));
        d = min(d, max(length(p.xz) - 0.15, abs(p.y - 0.035) - 0.035) - 0.006);
        d = min(d, length(vec2(length(p.xy - vec2(0.0, 0.15)) - 0.055, p.z - 0.122)) - 0.012);
        d = min(d, length(vec2(length(p.zy - vec2(0.0, 0.16)) - 0.035, abs(p.x) - 0.118)) - 0.009);
        return max(d, -p.y);
    }
    if (type == 11) { // a clam opening and closing: two thin wavy shells hinged at the back, pearly inside, soft flesh, a pearl
        vec4 c = clamParts(p, t);
        return max(min(min(c.x, c.y), min(c.z, c.w)), -p.y);
    }
    if (type == 0) {
        float d = sdEllipsoid(p - vec3(0.0, 0.07, 0.0), vec3(0.2, 0.13, 0.16));
        d = smin(d, sdEllipsoid(p - vec3(0.13, 0.04, 0.07), vec3(0.12, 0.08, 0.1)), 0.05);
        return smin(d, sdEllipsoid(p - vec3(-0.11, 0.03, -0.08), vec3(0.11, 0.07, 0.09)), 0.05);
    }
    if (type == 1) {
        float d = sdEllipsoid(p - vec3(0.0, 0.02, 0.0), vec3(0.07, 0.035, 0.06));
        return d - 0.003 * sin(atan(p.z, p.x - 0.07) * 18.0); // (ridges)
    }
    if (type == 2) {
        return max(sdStar5(p.xz, 0.1, 0.42), abs(p.y - 0.012) - 0.012) - 0.006;
    }
    if (type == 3) {
        float tower = sdBox(p - vec3(0.0, 0.2, 0.0), vec3(0.1, 0.2, 0.1));
        vec3 c = p - vec3(0.0, 0.39, 0.0);
        float notch = min(sdBox(vec3(abs(c.x) - 0.05, c.y, c.z), vec3(0.022, 0.025, 0.12)), sdBox(vec3(c.x, c.y, abs(c.z) - 0.05), vec3(0.12, 0.025, 0.022)));
        tower = max(tower, -notch);
        tower = max(tower, -sdBox(p - vec3(0.0, 0.06, 0.1), vec3(0.035, 0.065, 0.03))); // (door)
        tower = max(tower, -sdBox(p - vec3(0.0, 0.25, 0.1), vec3(0.018, 0.03, 0.03))); // (window)
        float small = sdBox(p - vec3(0.17, 0.12, 0.05), vec3(0.06, 0.12, 0.06));
        return min(tower, small);
    }
    if (type == 4) {
        float d = sdCapsule(p, vec3(0.0), vec3(0.0, 0.16, 0.0), 0.03);
        d = smin(d, sdCapsule(p, vec3(0.0, 0.11, 0.0), vec3(0.09, 0.26, 0.03), 0.022), 0.03);
        d = smin(d, sdCapsule(p, vec3(0.0, 0.09, 0.0), vec3(-0.08, 0.24, -0.04), 0.022), 0.03);
        d = smin(d, sdCapsule(p, vec3(0.0, 0.15, 0.0), vec3(0.02, 0.32, -0.06), 0.02), 0.03);
        return smin(d, sdCapsule(p, vec3(0.05, 0.19, 0.015), vec3(0.12, 0.3, 0.08), 0.017), 0.025);
    }
    if (type == 5) {
        float body = sdBox(p - vec3(0.0, 0.07, 0.0), vec3(0.14, 0.07, 0.09)) - 0.004;
        float lid = max(max(length(p.yz - vec2(0.14, 0.0)) - 0.09, abs(p.x) - 0.14), 0.14 - p.y);
        return min(body, lid);
    }
    if (type == 6) {
        float d = sdEllipsoid(p - vec3(0.0, 0.03, 0.0), vec3(0.08, 0.05, 0.08));
        for (int k = 0; k < 7; k++) {
            float a = float(k) * 0.8976;
            vec3 tip = vec3(cos(a) * 0.11 + 0.025 * sin(t * 1.3 + float(k)), 0.18, sin(a) * 0.11 + 0.025 * cos(t * 1.1 + float(k)));
            d = smin(d, sdCapsule(p, vec3(cos(a) * 0.04, 0.05, sin(a) * 0.04), tip, 0.012), 0.02);
        }
        return d;
    }
    float d = sdEllipsoid(p - vec3(0.0, 0.12, 0.0), vec3(0.09, 0.12, 0.09));
    d = smin(d, max(length(p.xz) - 0.035, abs(p.y - 0.27) - 0.05), 0.03);
    return smin(d, max(length(p.xz) - 0.05, abs(p.y - 0.32) - 0.012), 0.01);
}

vec3 decoColour(int type, vec3 p, vec3 n, float rnd) {
    if (type == 12) { // seagrass: deep green at the root, lighter to the tips
        return mix(vec3(0.1, 0.32, 0.14), vec3(0.36, 0.66, 0.3), smoothstep(0.0, 0.5, p.y)) * (0.9 + 0.2 * rnd);
    }
    if (type == 13) { // kelp: an olive-brown stalk and leaves, yellowish bladders
        int part;
        kelpSDF(p, gTime, part);
        vec3 leaf = mix(vec3(0.3, 0.34, 0.1), vec3(0.48, 0.5, 0.16), 0.5 + 0.5 * sin(p.y * 40.0));
        return part == 2 ? vec3(0.62, 0.55, 0.2) : part == 1 ? leaf : vec3(0.26, 0.27, 0.09);
    }
    if (type == 8) { // old dark wood, planked, mossy on top
        vec3 c = vec3(0.32, 0.22, 0.14) * (0.8 + 0.3 * step(0.15, fract(p.y * 28.0 + p.x * 2.0))) * (0.85 + 0.3 * fbm(p.xz * 20.0));
        if (p.y > 0.2) c = vec3(0.45, 0.35, 0.22) * (0.85 + 0.2 * fbm(p.xy * 30.0)); // (the mast)
        return mix(c, vec3(0.22, 0.38, 0.16), smoothstep(0.55, 0.9, n.y) * 0.6 * fbm(p.xz * 12.0 + 3.0));
    }
    if (type == 9) { // rusty iron
        float r = fbm(p.xy * 25.0 + p.z * 10.0);
        return mix(vec3(0.2, 0.17, 0.16), vec3(0.6, 0.3, 0.12), smoothstep(0.4, 0.7, r));
    }
    if (type == 10) { // brass with green patches; dark glass windows
        if ((p.z > 0.1 && length(p.xy - vec2(0.0, 0.15)) < 0.046) || (abs(p.x) > 0.1 && length(p.zy - vec2(0.0, 0.16)) < 0.028)) {
            return vec3(0.05, 0.08, 0.1) + vec3(0.4) * pow(max(n.y, 0.0), 8.0);
        }
        return mix(vec3(0.78, 0.56, 0.22), vec3(0.3, 0.55, 0.42), smoothstep(0.55, 0.75, fbm(p.xz * 18.0 + p.y * 9.0)) * 0.7);
    }
    if (type == 11) { // the clam: ribbed cream-pink shells outside, pearly (nacre) inside, soft flesh, a white pearl
        vec4 c = clamParts(p, gTime);
        float m = min(min(c.x, c.y), min(c.z, c.w));
        vec3 q = p - vec3(0.0, 0.012, 0.0);
        if (m == c.w) return vec3(0.97, 0.95, 0.96) + vec3(0.3) * pow(max(n.y, 0.0), 14.0);
        if (m == c.z) { // the flesh: soft cream-pink, a darker frilled edge
            float r = length(q.xz - vec2(0.01, 0.0));
            return mix(vec3(0.98, 0.8, 0.74), vec3(0.85, 0.5, 0.5), smoothstep(0.055, 0.085, r)) * (0.92 + 0.08 * fbm(q.xz * 50.0));
        }
        bool upper = m == c.y;
        vec3 sp = upper ? clamTop(q, gTime) : q;
        vec2 sh = clamShell(sp, upper ? 1.0 : -1.0);
        if (sh.y < -0.0035) { // the inside: nacre, pearly white with a soft rainbow sheen
            float k = 0.5 + 0.5 * sin(dot(n, vec3(5.0, 7.0, 3.0)) + length(sp.xz) * 50.0);
            return mix(vec3(0.95, 0.93, 0.96), mix(vec3(1.0, 0.86, 0.92), vec3(0.82, 0.92, 1.0), k), 0.4);
        }
        float ang = atan(sp.z, sp.x - CLAM_HINGE.x);
        float rib = abs(sin(ang * 7.0));
        float band = 0.5 + 0.5 * sin(length(sp.xz - CLAM_HINGE.xz) * 140.0); // (growth rings)
        return mix(vec3(0.72, 0.6, 0.52), vec3(0.97, 0.84, 0.78), rib) * (0.88 + 0.12 * band);
    }
    float speck = hash22(floor(p.xz * 60.0) + floor(p.y * 60.0)).x;
    if (type == 0) {
        vec3 c = mix(vec3(0.45, 0.43, 0.39), vec3(0.3, 0.28, 0.25), speck * 0.7 + rnd * 0.3);
        return mix(c, vec3(0.22, 0.4, 0.15), smoothstep(0.55, 0.9, n.y) * 0.6); // (moss on top)
    }
    if (type == 1) {
        float ridge = 0.5 + 0.5 * sin(atan(p.z, p.x - 0.07) * 18.0);
        return mix(vec3(0.95, 0.85, 0.75), vec3(0.95, 0.6, 0.6), ridge * 0.5 + rnd * 0.3);
    }
    if (type == 2) return mix(vec3(0.95, 0.38, 0.12), vec3(0.85, 0.15, 0.2), rnd) * (0.85 + 0.25 * step(0.7, speck));
    if (type == 3) {
        float mortar = step(0.9, fract(p.y * 22.0)) + step(0.92, fract(p.x * 11.0 + p.z * 11.0 + floor(p.y * 22.0) * 0.5));
        return mix(vec3(0.62, 0.6, 0.58), vec3(0.35, 0.34, 0.33), min(mortar, 1.0)) * (0.9 + 0.1 * speck);
    }
    if (type == 4) return mix(hue(0.9 + rnd * 0.2), vec3(1.0, 0.9, 0.85), smoothstep(0.2, 0.33, p.y) * 0.5);
    if (type == 5) {
        bool gold = abs(abs(p.x) - 0.09) < 0.015 || abs(p.y - 0.14) < 0.012;
        return gold ? vec3(1.0, 0.8, 0.25) * 1.2 : vec3(0.42, 0.25, 0.12) * (0.8 + 0.2 * step(0.5, fract(p.y * 25.0)));
    }
    if (type == 6) return mix(vec3(0.95, 0.5, 0.2), mix(vec3(0.75, 0.2, 0.9), vec3(1.0, 0.5, 0.9), rnd), smoothstep(0.05, 0.16, p.y)) * 1.1;
    return abs(p.y - 0.16) < 0.015 ? vec3(0.12, 0.08, 0.06) : vec3(0.75, 0.38, 0.2) * (0.85 + 0.15 * speck);
}



vec4 decoData(int k) {
    return k == 0 ? Deco0 : k == 1 ? Deco1 : k == 2 ? Deco2 : k == 3 ? Deco3 : k == 4 ? Deco4 : k == 5 ? Deco5 : k == 6 ? Deco6 : k == 7 ? Deco7 : k == 8 ? Deco8 : k == 9 ? Deco9 : k == 10 ? Deco10 : k == 11 ? Deco11 : k == 12 ? Deco12 : k == 13 ? Deco13 : k == 14 ? Deco14 : k == 15 ? Deco15 : k == 16 ? Deco16 : k == 17 ? Deco17 : k == 18 ? Deco18 : k == 19 ? Deco19 : k == 20 ? Deco20 : k == 21 ? Deco21 : k == 22 ? Deco22 : Deco23;
}

bool water(ivec3 c) {
    ivec3 size = ivec3(BoxSize + 0.5);
    if (any(lessThan(c, ivec3(0))) || any(greaterThanEqual(c, size))) {
        return false;
    }
    if (Full > 0.5) {
        return true;
    }
    int index = c.x + size.x * (c.z + size.z * c.y);
    int word = index / 24;
    int v = word / 4;
    vec4 o = v == 0 ? Occ0 : v == 1 ? Occ1 : v == 2 ? Occ2 : v == 3 ? Occ3 : v == 4 ? Occ4 : v == 5 ? Occ5 : v == 6 ? Occ6 : v == 7 ? Occ7 : v == 8 ? Occ8 : v == 9 ? Occ9 : v == 10 ? Occ10 : v == 11 ? Occ11 : v == 12 ? Occ12 : v == 13 ? Occ13 : v == 14 ? Occ14 : Occ15;
    int bits = int(o[word - v * 4]); // (exact whole numbers: no + 0.5, which rounds a full 24 bits up to 2^24)
    return ((bits >> (index - word * 24)) & 1) == 1;
}

void main() {
    vec3 ro = Eye;
    vec3 rd = normalize(fragPos - Eye);
    float tEnter = length(fragPos - Eye);
    vec3 entry = ro + rd * tEnter - BoxMin; // (tank coordinates, 0..BoxSize)
    // walk through the tank's blocks to where the ray leaves the water (a tank of any shape)
    // (start just inside the face, straight in from it: right on the line between two blocks the ray could start a
    // hair outside the tank and "leave the water" at once, drawing a broken line along the seam)
    vec3 faceN = normalize(cross(dFdx(fragPos), dFdy(fragPos)));
    faceN = dot(faceN, rd) < 0.0 ? -faceN : faceN;
    vec3 pos = clamp(entry + faceN * 0.01, vec3(0.001), BoxSize - 0.001);
    ivec3 cell = ivec3(floor(pos));
    vec3 stepDir = sign(rd);
    vec3 tDelta = abs(1.0 / rd);
    vec3 tMax = (vec3(cell) + max(stepDir, 0.0) - pos) / rd;
    float leave = 0.0;
    int exitAxis = 1;
    // (air inside the tank's box - a glass tunnel or room built through it - is seen through: the ray crosses it and
    // carries on into the water beyond; the gaps are remembered, for no fog in them)
    bool inAir = false;
    float airStart = 0.0, gap0 = -1.0, gap1 = -1.0, gap2 = -1.0, gap3 = -1.0, airTotal = 0.0;
    ivec3 airCell = cell;
    int airAxis = 1;
    for (int i = 0; i < 96; i++) {
        if (tMax.x < tMax.y && tMax.x < tMax.z) {
            leave = tMax.x; cell.x += int(stepDir.x); tMax.x += tDelta.x; exitAxis = 0;
        } else if (tMax.y < tMax.z) {
            leave = tMax.y; cell.y += int(stepDir.y); tMax.y += tDelta.y; exitAxis = 1;
        } else {
            leave = tMax.z; cell.z += int(stepDir.z); tMax.z += tDelta.z; exitAxis = 2;
        }
        if (water(cell)) {
            if (inAir) { // (back into water across the tunnel)
                inAir = false;
                if (gap0 < 0.0) { gap0 = tEnter + airStart; gap1 = tEnter + leave; }
                else if (gap2 < 0.0) { gap2 = tEnter + airStart; gap3 = tEnter + leave; }
                airTotal += leave - airStart;
            }
            continue;
        }
        bool insideBox = all(greaterThanEqual(cell, ivec3(0))) && all(lessThan(cell, ivec3(BoxSize + 0.5)));
        if (!insideBox) {
            break;
        }
        if (!inAir) {
            inAir = true;
            airStart = leave;
            airCell = cell;
            airAxis = exitAxis;
        }
    }
    if (inAir) { // (the air opens out of the tank: stop where the water ended - the tunnel's glass)
        leave = airStart;
        cell = airCell;
        exitAxis = airAxis;
    }
    float tExit = tEnter + leave;

    float surface = BoxSize.y - 0.06;
    float t = Time;
    vec3 col;
    float hitT = tExit;
    vec3 hitPos = ro + rd * tExit - BoxMin;

    // ---- the fish ----
    int count = int(FishCount + 0.5);
    vec3 fishCol = vec3(0.0);
    vec3 glow = vec3(0.0); // (light a fish gives off itself: the jellyfish at night)
    bool fish = false;
    float fishAlpha = 1.0;
    vec3 edgeCol = vec3(0.0);
    float edgeA = 0.0, edgeT = 1.0e9;
    float pixA = max(length(fwidth(rd)), 1.0e-5); // (how wide a pixel is, as an angle)
    vec3 rot = ro - BoxMin;
    for (int i = 0; i < 48; i++) {
        if (i >= count) {
            break;
        }
        float fi = float(i);
        // where it is and how it's swimming (worked out on the CPU, once a frame)
        vec4 f0 = texelFetch(FishTex, ivec2(0, i), 0), f1 = texelFetch(FishTex, ivec2(1, i), 0);
        vec4 f2 = texelFetch(FishTex, ivec2(2, i), 0), f3 = texelFetch(FishTex, ivec2(3, i), 0);
        int kind = int(f1.w + 0.5);
        Species sp = species(kind);
        vec3 c = f0.xyz;
        float size = f0.w;
        vec3 fwd = f1.xyz, up = f2.xyz;
        vec3 side = normalize(cross(fwd, up));
        float wave = f2.w;
        float squeeze = f3.z;
        gBeat = f3.x;
        gBend = f3.y;
        gFinSway = sin(t * 1.6 + fi);
        gMouth = 0.5 + 0.5 * sin(t * (2.0 + hash11(fi * 2.2)) + fi * 1.7);
        gTime = t;
        gPulse = squeeze;
        gWalk = f3.w;
        // (a quick test first: does the ray pass near this fish at all?)
        float tallest = sp.body.y + max(sp.fin.y * 1.3, sp.body.y * 0.45 * fishShape(kind).w) + (kind == 4 ? sp.body.y : 0.0);
        float reachAll = kind >= 20 ? size * (length(sp.body) + 0.02) : size * max(sp.body.x * 1.3 + sp.tail.x * 2.2 + max(sp.body.z, sp.fin.z) + sp.tail.y * (kind == 15 ? 2.2 : 0.6),
                length(vec2(sp.body.x + sp.tail.x * 2.0, tallest)) + 0.02);
        vec3 oc = rot - c;
        float bq = dot(oc, rd), cq = dot(oc, oc) - reachAll * reachAll;
        float disc = bq * bq - cq;
        if (disc < 0.0) {
            continue;
        }
        float t0 = max(-bq - sqrt(disc), tEnter), t1 = min(-bq + sqrt(disc), hitT);
        if (t0 >= t1) {
            continue;
        }
        vec3 F = fwd, U = up, S = side;
        vec3 lo = vec3(dot(oc, F), dot(oc, U), dot(oc, S)) / size;
        vec3 ld = vec3(dot(rd, F), dot(rd, U), dot(rd, S));
        if (kind != 12 && kind != 13 && kind != 15 && kind < 20) { // a much tighter box round it, in its own frame: far less tracing
            float L = sp.body.x;
            float xb = -(0.92 * L + 2.3 * sp.tail.x) - 0.01, xf = 1.2 * L;
            float bigP = kind == 10 || kind == 16 || kind == 19 ? 1.7 : 1.0;
            float zb = max(sp.body.z, 0.85 * sp.body.z + 0.4 * L * bigP) + 0.35 * L + 0.5 * abs(f3.y) * xb * xb + 0.01 + (kind == 18 ? 0.3 * sp.fin.y : 0.0);
            float yb = max(max(tallest, 1.25 * sp.tail.y), kind == 7 ? 1.2 * sp.body.y : 0.0) + (kind == 16 ? 0.3 * L : 0.0) + 0.01;
            vec3 lds = mix(ld, vec3(1.0e-6), lessThan(abs(ld), vec3(1.0e-6)));
            vec3 ta = (vec3(xb, -yb, -zb) - lo) / lds, tb = (vec3(xf, yb, zb) - lo) / lds;
            vec3 tn = min(ta, tb), tx = max(ta, tb);
            t0 = max(t0, max(max(tn.x, tn.y), tn.z) * size);
            t1 = min(t1, min(min(tx.x, tx.y), tx.z) * size);
            if (t0 >= t1) {
                continue;
            }
        }
        float flap = kind == 13 ? sin(t * 1.4 + fi * 1.3) : sin(t * 3.0 + fi * 1.3);
        float pulse = squeeze;
        float tf = -1.0;
        int part_ = 0;
        vec3 q = vec3(0.0);
        float tm = t0;
        float bestR = 1.0e9, bestT = -1.0; // (how close the ray passes, in pixels: for smooth edges)
        for (int s = 0; s < 96; s++) {
            float d = fishSDF(kind, sp, lo + ld * (tm / size), wave, flap, pulse, part_, q) * size;
            float px = pixA * tm;
            if (d < max(0.0004, 0.12 * px)) {
                tf = tm;
                break;
            }
            if (d / px < bestR) {
                bestR = d / px;
                bestT = tm;
            }
            tm += max(d * 0.6, 0.0003 + 0.08 * px);
            if (tm > t1) {
                break;
            }
        }
        float cover = 1.0;
        if (tf < 0.0) { // missed: but if it passed within a pixel, part of this pixel is fish (a soft edge)
            if (bestT < 0.0 || bestR > 1.0 || bestT >= edgeT) {
                continue;
            }
            cover = 1.0 - smoothstep(0.12, 1.0, bestR);
            if (cover < 0.03) {
                continue;
            }
            tf = bestT;
            float unused = fishSDF(kind, sp, lo + ld * (tf / size), wave, flap, pulse, part_, q);
        }
        vec3 pl = lo + ld * (tf / size);
        float ee = max(0.0007, 0.008 * min(sp.body.x, sp.body.y));
        vec2 e = vec2(ee, -ee);
        int dummyT;
        vec3 dummyQ;
        vec3 g = e.xyy * fishSDF(kind, sp, pl + e.xyy, wave, flap, pulse, dummyT, dummyQ)
               + e.yyx * fishSDF(kind, sp, pl + e.yyx, wave, flap, pulse, dummyT, dummyQ)
               + e.yxy * fishSDF(kind, sp, pl + e.yxy, wave, flap, pulse, dummyT, dummyQ)
               + e.xxx * fishSDF(kind, sp, pl + e.xxx, wave, flap, pulse, dummyT, dummyQ);
        vec3 nl = normalize(g);
        vec3 n = normalize(F * g.x + U * g.y + S * g.z);
        int part = part_;
        if (tf > tEnter - 0.01 && tf < hitT) {
            // shade in the creases (where fins meet the body, under the gill cover): ambient occlusion
            float occ = 0.0;
            for (int k = 1; k <= 3; k++) {
                float hk = 0.12 * sp.body.y * float(k);
                occ += max(hk - fishSDF(kind, sp, pl + nl * hk, wave, flap, pulse, dummyT, dummyQ), 0.0) / hk / float(k);
            }
            float ao = clamp(1.0 - 0.55 * occ, 0.35, 1.0);
            float rnd = hash11(fi * 13.3);
            vec3 base = fishColour(kind, q, part, rnd);
            bool isEye = false;
            // the eye
            bool eyed = kind != 12 && kind != 15 && kind != 13 && kind < 20;
            if (kind == 13 && part == 1 && abs(q.z) > 0.55) {
                float eyeD = length(vec2((q.x - 0.45) * 1.2, q.y - 0.2));
                if (eyeD < 0.13) { base = vec3(0.02); isEye = true; }
                else if (eyeD < 0.19) base = vec3(0.6, 0.45, 0.2);
            }
            if (eyed && part == 0 && abs(q.z) > 0.55) { // (where the eye bulges out)
                float er = sp.body.y * eyeScale(kind);
                vec2 ev = vec2((q.x - eyeAt(kind).x) * sp.body.x, (q.y - eyeAt(kind).y) * sp.body.y) / er;
                float eyeD = length(ev);
                if (eyeD < 0.115) {
                    isEye = true;
                    base = eyeD < 0.07 ? vec3(0.015) : mix(eyeRing(kind), eyeRing(kind) * 0.6, smoothstep(0.09, 0.115, eyeD)) * (0.85 + 0.15 * sin(atan(ev.y, ev.x) * 14.0));
                } else if (eyeD < 0.13) {
                    base *= 0.6; // (a dark rim round the eye)
                }
            }
            if (part >= 1 && kind < 20 && kind != 13 && kind != 15 && kind != 10 && kind != 16 && kind != 19 && kind != 12) {
                float rc = part == 1 ? q.y * 8.0 : q.x * 10.0; // (rays run along a tail, up a fin)
                float ray = 1.0 - smoothstep(0.0, 0.25, abs(fract(rc) - 0.5) - 0.2);
                base *= 0.84 + 0.16 * (1.0 - ray);
                float edge = part == 1 ? 0.5 - 0.5 * q.x : abs(q.y);
                base = mix(base, base * 1.15 + vec3(0.05, 0.08, 0.1), smoothstep(0.5, 1.0, edge) * 0.5);
            }
            if (part == 0 && !isEye && kind < 20 && kind != 12 && kind != 13 && kind != 15 && kind != 10 && kind != 16 && kind != 19 && kind != 18) {
                float ss = sp.body.y * (kind == 5 || kind == 17 ? 0.13 : 0.1);
                vec2 sc = vec2(-q.x * sp.body.x, q.y * sp.body.y) / ss;
                sc.y += 0.5 * floor(sc.x);
                vec2 sf = fract(sc) - 0.5;
                float rim = smoothstep(0.32, 0.5, length(sf - vec2(0.45, 0.0)));
                float sv = hash22(floor(sc)).x;
                float strength = kind == 5 ? 0.2 : kind == 17 ? 0.16 : kind == 7 ? 0.0 : kind == 11 ? 0.05 : 0.09;
                base *= 1.0 - strength * (rim * 0.7 + (sv - 0.5) * 0.4); // (overlapping scales)
                float gx = q.x - (0.44 - 0.1 * q.y * q.y);
                if (kind != 7 && gx > 0.0 && gx < 0.03 && abs(q.y) < 0.75) base *= 0.78;              // (the gill cover's edge)
                if (kind != 7 && q.x < 0.42 && abs(q.y - 0.18 + 0.12 * q.x * q.x) < 0.012) base *= 0.9; // (lateral line)
            }
            vec3 p = rot + rd * tf;
            vec3 sun = normalize(vec3(0.3, 1.0, 0.2));
            float diff = clamp(dot(n, sun) * 0.5 + 0.5, 0.0, 1.0);
            vec3 amb = mix(vec3(0.24, 0.34, 0.45), vec3(0.6, 0.76, 0.88), n.y * 0.5 + 0.5);
            float through = part >= 1 && kind != 13 && kind < 20 ? max(-dot(n, sun), 0.0) * 0.35 + 0.2 * max(dot(rd, sun), 0.0) : 0.0; // (thin fins let light through)
            float irid = part != 0 ? 0.0 : kind == 3 || kind == 17 ? 0.28 : kind == 6 || kind == 8 ? 0.25 : kind == 11 ? 0.2 : kind == 4 ? 0.12 : kind == 1 ? 0.1 : 0.0;
            if (irid > 0.0) {
                float fa = 1.0 - max(dot(n, -rd), 0.0);
                base = mix(base, base + hue(fa * 0.9 + q.x * 0.25 + 0.45) * 0.5, irid * (0.4 + 0.6 * fa));
            }
            vec3 shade = base * (amb * 0.85 + vec3(1.0, 0.97, 0.9) * diff * 0.8 + 0.18 * max(dot(n, -rd), 0.0) + through) * ao;
            vec3 hv = normalize(sun - rd);
            bool silver = kind == 3 || kind == 11 || kind == 17 || kind == 4;
            float shin = silver ? 60.0 : kind == 13 ? 12.0 : 40.0;
            shade += vec3(1.0) * pow(max(dot(n, hv), 0.0), shin) * (silver ? 0.85 : kind == 13 ? 0.12 : 0.4) * ao; // (wet, shiny skin)
            if (isEye) {
                shade += vec3(1.0) * pow(max(dot(n, hv), 0.0), 150.0) * 1.5; // (a glossy eye)
                shade += vec3(1.0) * pow(max(dot(reflect(rd, n), vec3(0.0, 1.0, 0.0)), 0.0), 80.0) * 0.8;
            }
            float caust = caustics(p.xz, t) * max(n.y, 0.0) * (p.y / BoxSize.y);
            shade += vec3(0.6, 0.9, 1.0) * caust * 0.45 * ao;
            shade += vec3(0.5, 0.8, 1.0) * pow(1.0 - max(dot(n, -rd), 0.0), 3.0) * 0.22; // (light round the edges, from the water)
            float alpha = (kind == 15 ? 0.55 : 1.0) * cover; // (the jellyfish is see-through)
            if (cover > 0.99) {
                hitT = tf;
                fish = true;
                fishCol = shade;
                fishAlpha = alpha;
                glow = kind == 15 ? base * (1.3 + 0.5 * pulse) * alpha : kind == 3 && part == 0 && abs(q.y - 0.12) < 0.13 ? vec3(0.1, 0.6, 0.8) : vec3(0.0);
            } else { // (a soft edge: laid over whatever is behind, once that is known)
                edgeT = tf;
                edgeCol = shade;
                edgeA = alpha;
            }
        }
    }

    // ---- decorations on the bottom (placed on the CPU, from the tank's floor) ----
    gTime = t;
    bool deco = false;
    vec3 decoCol = vec3(0.0);
    int decoCount = int(DecoCount + 0.5);
    for (int k = 0; k < 24; k++) {
        if (k >= decoCount) {
            break;
        }
        vec4 dd = decoData(k);
        int pk = int(dd.w + 0.5);
        int type = pk - (pk / 16) * 16;
        float ang = float((pk / 16) - (pk / 256) * 16) * 0.3927;
        float sz = float(pk / 256) / 10.0;
        vec3 base = dd.xyz;
        float rad = (type == 8 ? 0.7 : type == 13 ? 0.56 : 0.5) * sz;
        vec3 oc = rot - (base + vec3(0.0, (type == 13 ? 0.5 : 0.2) * sz, 0.0));
        gDecoSz = sz;
        float bq = dot(oc, rd), cq = dot(oc, oc) - rad * rad;
        float disc = bq * bq - cq;
        if (disc < 0.0) {
            continue;
        }
        float t0 = max(-bq - sqrt(disc), tEnter), t1 = min(-bq + sqrt(disc), hitT);
        if (t0 >= t1) {
            continue;
        }
        float ca = cos(ang), sa = sin(ang);
        vec3 lo = rot - base;
        lo = vec3(ca * lo.x + sa * lo.z, lo.y, -sa * lo.x + ca * lo.z) / sz;
        vec3 ld = vec3(ca * rd.x + sa * rd.z, rd.y, -sa * rd.x + ca * rd.z);
        float tm = t0, td = -1.0;
        int steps = type == 13 ? 72 : type == 12 ? 56 : 40; // (the thin seaweed takes more little steps)
        for (int s = 0; s < 72; s++) {
            if (s >= steps) break;
            float d = decoSDF(type, lo + ld * (tm / sz), t) * sz;
            if (d < 0.0012) {
                td = tm;
                break;
            }
            tm += max(d * 0.8, 0.0015);
            if (tm > t1) {
                break;
            }
        }
        if (td < 0.0) {
            continue;
        }
        vec3 pl = lo + ld * (td / sz);
        vec2 e = vec2(0.002, -0.002);
        vec3 g = e.xyy * decoSDF(type, pl + e.xyy, t) + e.yyx * decoSDF(type, pl + e.yyx, t)
               + e.yxy * decoSDF(type, pl + e.yxy, t) + e.xxx * decoSDF(type, pl + e.xxx, t);
        g = normalize(g);
        vec3 n = vec3(ca * g.x - sa * g.z, g.y, sa * g.x + ca * g.z);
        vec3 p = rot + rd * td;
        float light = 0.4 + 0.6 * max(n.y, 0.0) + 0.2 * max(dot(n, -rd), 0.0);
        decoCol = decoColour(type, pl, g, hash11(float(k) * 17.1)) * light
                + vec3(0.6, 0.85, 1.0) * caustics(p.xz, t) * max(n.y, 0.0) * 0.45;
        if (type == 5) decoCol += vec3(1.0, 0.8, 0.3) * 0.15 * pow(max(dot(reflect(rd, n), vec3(0.0, 1.0, 0.0)), 0.0), 8.0);
        deco = true;
        fish = false;
        hitT = td;
    }

    // the soft edge of a fish, over whatever is behind it
    if (edgeA > 0.0 && edgeT < hitT) {
        if (deco) {
            decoCol = mix(decoCol, edgeCol, edgeA);
        } else if (fish) {
            fishCol = mix(fishCol, edgeCol, edgeA);
        } else {
            fish = true;
            fishCol = edgeCol;
            fishAlpha = edgeA;
            hitT = edgeT;
        }
    }

    // ---- bubbles from a bubbler in one corner ----
    vec2 bubbler = vec2(0.3, 0.3) + vec2(hash11(41.0), hash11(43.0)) * max(BoxSize.xz - 0.6, vec2(0.0));
    vec3 bubbleCol = vec3(0.0);
    float bubbleT = 1.0e9;
    for (int k = 0; k < 9; k++) {
        float fk = float(k);
        float rise = mod(t * (0.55 + 0.1 * hash11(fk + 20.0)) + fk / 9.0 * BoxSize.y, BoxSize.y);
        vec3 bc = vec3(bubbler.x + 0.05 * sin(t * 3.0 + fk * 1.7), rise, bubbler.y + 0.05 * cos(t * 2.6 + fk));
        float br = 0.018 + 0.012 * hash11(fk * 9.1);
        vec3 o = ro - BoxMin - bc;
        float b = dot(o, rd), cc = dot(o, o) - br * br, h = b * b - cc;
        if (h > 0.0) {
            float tb2 = -b - sqrt(h);
            bool inGap = (tb2 > gap0 && tb2 < gap1) || (tb2 > gap2 && tb2 < gap3);
            if (tb2 > tEnter && tb2 < bubbleT && !inGap) {
                bubbleT = tb2;
                vec3 bn = normalize(o + rd * tb2);
                bubbleCol = vec3(0.8, 0.95, 1.0) * (0.25 + 0.75 * pow(1.0 - abs(dot(bn, rd)), 2.0));
            }
        }
    }

    // ---- what the ray reaches: a fish, or the sand / back glass / surface ----
    float fishT = hitT;
    if (deco) {
        col = decoCol;
    } else if (fish && fishAlpha > 0.99) {
        col = fishCol;
    } else {
        hitT = tExit;
        vec3 p = hitPos;
        bool onSand = entry.y < 0.12 && entry.y > 0.0;
        if (onSand) {
            p = entry; // (the side of the sand layer, right at the glass)
            hitT = tEnter;
        } else if (rd.y < 0.0) {
            float ts = (BoxMin.y + 0.12 - ro.y) / rd.y;
            if (ts > tEnter && ts < hitT) {
                hitT = ts;
                p = ro - BoxMin + rd * ts;
                onSand = true;
            }
        }
        bool down = exitAxis == 1 && rd.y < 0.0, up = exitAxis == 1 && rd.y > 0.0;
        // the water ends inside the tank's box (a glass corridor or room built into the tank): glass, looking out into
        // the air - no sand, no seaweed (only the real bottom and the outer walls have those)
        bool inner = all(greaterThanEqual(cell, ivec3(0))) && all(lessThan(cell, ivec3(BoxSize + 0.5)));
        if (inner && !onSand) {
            vec2 gp = exitAxis == 0 ? p.zy : exitAxis == 1 ? p.xz : p.xy;
            col = mix(vec3(0.32, 0.5, 0.58), vec3(0.5, 0.68, 0.74), clamp(p.y / BoxSize.y, 0.0, 1.0));
            col += vec3(0.5, 0.75, 0.85) * caustics(gp * 1.2, t) * 0.18;                       // (light rippling on it)
            col += vec3(0.6, 0.8, 0.9) * 0.12 * smoothstep(0.6, 1.0, sin(gp.x * 2.0 + gp.y * 3.0 + 1.3)); // (a sheen on the glass)
        } else if (down || onSand) {
            // sand with pebbles, lit by the moving light patterns
            // a fine, lighter grain (no dark patches: they looked like shadows)
            float pebble = 0.94 + 0.1 * hash22(floor(p.xz * 30.0)).x;
            col = vec3(0.85, 0.75, 0.52) * pebble * 0.82;
            col += vec3(0.6, 0.85, 1.0) * caustics(p.xz, t) * 0.55;
        } else if (up) {
            // the underside of the surface: bright, rippling
            col = vec3(0.35, 0.75, 0.85) + vec3(0.5, 0.8, 0.9) * caustics(p.xz * 1.3, t * 1.4) * 0.6;
        } else {
            // the back glass: deep water, seaweed swaying on it, darker lower down
            vec2 wall = exitAxis == 0 ? vec2(p.z, p.y) : vec2(p.x, p.y);
            col = Ocean > 0.5 ? mix(vec3(0.0, 0.08, 0.2), vec3(0.04, 0.3, 0.5), p.y / BoxSize.y)
                              : mix(vec3(0.02, 0.18, 0.3), vec3(0.1, 0.45, 0.55), p.y / BoxSize.y);
            for (int k = 0; k < 3; k++) {
                float fk = float(k);
                float spacing = 0.55 + 0.2 * fk;
                float xi = floor(wall.x / spacing);
                float hgt = (0.35 + 0.45 * hash11(xi * 1.3 + fk * 17.0)) * (Ocean > 0.5 ? BoxSize.y * 0.9 : min(BoxSize.y, 2.5));
                float sway = 0.08 * sin(t * (0.8 + 0.3 * fk) + wall.y * 3.0 + xi) * (wall.y / max(hgt, 0.1));
                float dx = abs(fract(wall.x / spacing) * spacing - spacing * 0.5 - sway);
                float w = 0.035 * (1.0 - wall.y / max(hgt, 0.1)) + 0.008;
                if (wall.y < hgt && dx < w && hash11(xi * 3.7 + fk) > 0.35) {
                    col = mix(vec3(0.05, 0.35, 0.12), vec3(0.2, 0.6, 0.2), wall.y / hgt) * (0.6 + 0.2 * fk);
                    if (Ocean > 0.5) col = mix(vec3(0.25, 0.22, 0.05), vec3(0.45, 0.42, 0.1), wall.y / hgt) * (0.7 + 0.2 * fk); // kelp
                }
            }
            if (Ocean > 0.5) { // coral along the bottom of the glass
                float ci = floor(wall.x / 0.7);
                vec2 cc = vec2((ci + 0.5) * 0.7, 0.12 + 0.12 * hash11(ci * 5.1));
                float r = 0.12 + 0.12 * hash11(ci * 2.9);
                vec2 dd = wall - cc;
                float blob = length(dd * vec2(1.0, 0.8)) + 0.04 * sin(atan(dd.y, dd.x) * 7.0 + ci);
                if (blob < r && hash11(ci * 7.3) > 0.25) {
                    col = hue(hash11(ci * 3.3) * 0.25 + 0.85) * (0.6 + 0.4 * (1.0 - blob / r));
                }
            }
        }
    }
    if (fish && fishAlpha <= 0.99) {
        col = mix(col, fishCol, fishAlpha);
        hitT = mix(hitT, fishT, fishAlpha);
    }
    if (bubbleT < hitT) {
        col = mix(col, bubbleCol, 0.7);
        hitT = bubbleT;
    }

    // ---- the water between: deeper colour with distance, lighter near the top, a soft shimmer ----
    float travel = max(hitT - tEnter, 0.0);
    // (no fog across the tunnels' air; a faint glassy tint for each tunnel seen through)
    float crossed = 0.0;
    if (gap0 >= 0.0 && hitT > gap0) { travel -= min(hitT, gap1) - gap0; crossed += 1.0; }
    if (gap2 >= 0.0 && hitT > gap2) { travel -= min(hitT, gap3) - gap2; crossed += 1.0; }
    travel = max(travel, 0.0);
    col = mix(col, vec3(0.6, 0.8, 0.88), 0.07 * crossed);
    vec3 waterCol = Ocean > 0.5 ? mix(vec3(0.0, 0.1, 0.24), vec3(0.05, 0.38, 0.58), clamp(entry.y / BoxSize.y, 0.0, 1.0))
                                : mix(vec3(0.02, 0.2, 0.32), vec3(0.15, 0.55, 0.62), clamp(entry.y / BoxSize.y, 0.0, 1.0));
    col = mix(col, waterCol, 1.0 - exp(-travel * (Ocean > 0.5 ? 0.22 : 0.3)));
    col += vec3(0.25, 0.5, 0.55) * 0.08 * (0.5 + 0.5 * sin(entry.x * 4.0 + entry.z * 3.0 + entry.y * 2.0 + t * 1.5));

    // looking down through the top: the surface ripples catch the light
    if (faceN.y < -0.5) { // (a top face: faceN points into the tank, down)
        col += vec3(0.8, 0.95, 1.0) * caustics(entry.xz * 1.6 + vec2(t * 0.1, 0.0), t * 1.6) * 0.18;
    }

    // ---- night: the tank's lights dimmed to a deep blue; what glows still glows ----
    if (Night > 0.0) {
        float seen = max(hitT - tEnter, 0.0);
        col *= mix(vec3(1.0), vec3(0.24, 0.3, 0.5), Night); // (dim, but you can still see in)
        // glowing plankton drifting in the water, twinkling
        float spark = 0.0;
        for (int k = 0; k < 8; k++) {
            float dd = 0.2 + float(k) * 0.45;
            if (dd > seen) {
                break;
            }
            vec3 sp = entry + rd * dd + vec3(0.0, -t * 0.04, 0.0);
            vec3 cell = floor(sp * 5.0);
            vec2 h1 = hash22(cell.xy + cell.z * 7.13);
            vec2 h2 = hash22(cell.zx + 3.71);
            if (h2.y > 0.95) {
                vec3 cp = (cell + 0.2 + 0.6 * vec3(h1, h2.x)) / 5.0 + vec3(0.0, t * 0.04, 0.0);
                vec3 w = cp - entry;
                float along = dot(w, rd);
                float dist = length(w - rd * along);
                if (along > 0.0 && along < seen) {
                    spark += smoothstep(0.008, 0.0, dist) * (0.4 + 0.4 * sin(t * 2.5 + h1.x * 40.0));
                }
            }
        }
        col += vec3(0.25, 0.85, 1.0) * min(spark, 1.0) * 0.7 * Night;
        if (!deco) col += glow * Night * exp(-seen * 0.25);
    }

    // ---- the frame round the tank's edges ----
    const float W = 0.05;
    float frame = max(max(edges.r * step(faceUV.x, W), edges.g * step(1.0 - W, faceUV.x)),
                      max(edges.b * step(faceUV.y, W), edges.a * step(1.0 - W, faceUV.y)));
    if (frame > 0.5) {
        col = vec3(0.09, 0.09, 0.1);
    }
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
