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
// per fish: (middle of its stretch of water x, y, z, -), (how far it reaches x, y, z, -)
uniform vec4 Fish0;
uniform vec4 Fish1;
uniform vec4 Fish2;
uniform vec4 Fish3;
uniform vec4 Fish4;
uniform vec4 Fish5;
uniform vec4 Fish6;
uniform vec4 Fish7;
uniform vec4 Fish8;
uniform vec4 Fish9;
uniform vec4 Fish10;
uniform vec4 Fish11;
uniform vec4 Fish12;
uniform vec4 Fish13;
uniform vec4 Fish14;
uniform vec4 Fish15;
uniform vec4 Fish16;
uniform vec4 Fish17;
uniform vec4 Fish18;
uniform vec4 Fish19;
uniform vec4 Fish20;
uniform vec4 Fish21;
uniform vec4 Fish22;
uniform vec4 Fish23;
uniform vec4 Fish24;
uniform vec4 Fish25;
uniform vec4 Fish26;
uniform vec4 Fish27;
uniform vec4 Fish28;
uniform vec4 Fish29;
uniform vec4 Fish30;
uniform vec4 Fish31;
uniform vec4 Fish32;
uniform vec4 Fish33;
uniform vec4 Fish34;
uniform vec4 Fish35;
uniform vec4 Fish36;
uniform vec4 Fish37;
uniform vec4 Fish38;
uniform vec4 Fish39;
uniform vec4 Fish40;
uniform vec4 Fish41;
uniform vec4 Fish42;
uniform vec4 Fish43;
uniform vec4 Fish44;
uniform vec4 Fish45;
uniform vec4 Fish46;
uniform vec4 Fish47;
uniform vec4 Fish48;
uniform vec4 Fish49;
uniform vec4 Fish50;
uniform vec4 Fish51;
uniform vec4 Fish52;
uniform vec4 Fish53;
uniform vec4 Fish54;
uniform vec4 Fish55;
uniform vec4 Fish56;
uniform vec4 Fish57;
uniform vec4 Fish58;
uniform vec4 Fish59;
uniform vec4 Fish60;
uniform vec4 Fish61;
uniform vec4 Fish62;
uniform vec4 Fish63;
uniform float FishCount;
uniform float Ocean;
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
    if (k == 7) return Species(vec3(0.12, 0.1, 0.1), vec3(0.04, 0.04, 0.01), vec3(0.0), 0.5);
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
    if (k == 19) return Species(vec3(1.25, 0.27, 0.25), vec3(0.32, 0.42, 0.04), vec3(0.22, 0.3, 0.04), 0.45); // basking shark
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

// 1 inside a round spot (one per cell, placed at random), 0 elsewhere
float spot(vec2 p, float r) {
    vec2 o = hash22(floor(p)) * 0.5 + 0.25;
    return 1.0 - smoothstep(r * 0.8, r, length(fract(p) - o));
}

// a fish's colour at a point on it (q: that part's own coordinates, x along it nose-first, -1..1; part 0 body,
// 1 tail (the turtle's head and flippers; the jellyfish's arms), 2 fin (the jellyfish's tentacles); on the fins
// q.y is the height out from the body and q.z says which: 0 the back's, -1 underneath, 2 a side fin, 3 a filament);
// rnd: this fish's own random number (colour variants). Each matched to a photo of the real fish.
vec3 fishColour(int kind, vec3 q, int part, float rnd) {
    bool tail = part == 1, fin = part == 2;
    bool anal = fin && q.z < -0.5, pect = fin && q.z > 1.5 && q.z < 2.5, fil = fin && q.z > 2.5;
    bool dorsal = fin && !anal && !pect && !fil;
    float tk = 0.5 - 0.5 * q.x; // along the tail: 0 at its root, 1 at its tip
    if (kind == 0) { // clownfish: orange, three white bands edged black, black-rimmed fins
        vec3 o = vec3(1.0, 0.42, 0.03);
        if (part == 0) {
            float b1 = abs(q.x - 0.4 + 0.1 * q.y * q.y) - 0.08;  // behind the eye, curving
            float b2 = abs(q.x + 0.1 - 0.12 * q.y) - 0.11;        // the middle, leaning forward at the top
            float b3 = abs(q.x + 0.86) - 0.06;                     // at the tail root
            float b = min(b1, min(b2, b3));
            return b < 0.0 ? vec3(0.97) : b < 0.035 ? vec3(0.03) : o;
        }
        float edge = tail ? smoothstep(0.8, 0.88, tk) : pect ? smoothstep(0.62, 0.78, abs(q.x)) : smoothstep(0.72, 0.84, abs(q.y));
        return mix(o, vec3(0.03), edge);
    }
    if (kind == 1) { // blue tang: royal blue, the black "palette" mark with a blue window, yellow tail
        vec3 blue = vec3(0.08, 0.3, 0.95), black = vec3(0.02, 0.03, 0.08), yellow = vec3(1.0, 0.85, 0.08);
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
        return mix(y, vec3(0.78, 0.9, 0.15), smoothstep(-0.1, -0.6, q.y) * 0.6);
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
        return mix(c, vec3(1.0, 0.38, 0.08), smoothstep(0.55, 0.9, q.x) * 0.6);
    }
    if (kind == 6) { // betta: blue body, reddish head, huge fins blue at the base going red, pale tips
        vec3 blue = vec3(0.2, 0.25, 0.95), red = vec3(0.9, 0.12, 0.08);
        if (rnd > 0.6) { blue = rnd > 0.8 ? vec3(0.1, 0.15, 0.85) : vec3(0.55, 0.1, 0.75); red = blue * 1.3 + 0.05; }
        if (part == 0) return mix(blue * 0.95, vec3(0.55, 0.15, 0.25), smoothstep(0.45, 0.7, q.x));
        float away = tail ? tk : pect ? 0.3 : abs(q.y);
        vec3 c = mix(blue, red, smoothstep(0.1, 0.45, away));
        c *= 0.85 + 0.15 * sin(tail ? q.y * 25.0 : q.x * 25.0); // (streaks)
        return mix(c, vec3(0.95, 0.85, 0.85), smoothstep(0.85, 1.0, away) * 0.7);
    }
    if (kind == 7) { // pufferfish: cream with small black spots, white belly, pink lips, spotted yellowish fins
        if (part > 0) return vec3(0.8, 0.8, 0.55) * (1.0 - 0.6 * spot(q.xy * 9.0, 0.15));
        if (q.y < -0.38) return vec3(0.96, 0.95, 0.9);
        if (q.x > 0.9 && abs(q.y + 0.12) < 0.1) return vec3(0.9, 0.55, 0.55);
        vec3 c = mix(vec3(0.55, 0.52, 0.42), vec3(0.9, 0.88, 0.78), smoothstep(-0.38, -0.1, q.y));
        return c * (1.0 - 0.9 * spot(vec2(q.x * 9.0, (q.y + abs(q.z)) * 7.0), 0.14));
    }
    if (kind == 8) { // guppy: silver-green front, deep blue back half, a big orange tail with black blotches
        if (part > 0) {
            vec3 c = rnd < 0.5 ? vec3(1.0, 0.45, 0.1) : hue(rnd) * 1.05;
            if (sin(q.x * 13.0 + sin(q.y * 8.0) * 2.0) * sin(q.y * 10.0 + q.x * 5.0) > 0.35) c = vec3(0.05);
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
        vec3 c = cellEdge(q.xy * vec2(4.0, 5.0) + rnd * 7.0) < 0.12 ? red : cream;
        return q.x > 0.62 ? mix(c, vec3(1.0, 0.7, 0.45), 0.6) : c;
    }
    if (kind == 10) { // mako shark: metallic blue-grey above, a sharp white belly, gill slits
        vec3 top = vec3(0.3, 0.4, 0.56), belly = vec3(0.93, 0.94, 0.96);
        if (part > 0) return top * (dorsal ? 0.8 : 0.9);
        float line = -0.15 + 0.1 * q.x;
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
        return c;
    }
    if (kind == 12) { // manta ray: speckled dark brown on top, white underneath, pale-tipped head fins
        vec3 top = vec3(0.17, 0.14, 0.11);
        if (part == 2) return mix(top, vec3(0.88, 0.85, 0.72), smoothstep(0.0, 0.6, q.x));
        if (part == 0 && q.y < 0.0) return vec3(0.9, 0.9, 0.92);
        return mix(top, vec3(0.6), step(0.9, hash22(floor(q.xz * 40.0)).x) * 0.6);
    }
    if (kind == 13) { // green sea turtle: red-brown shell with streaked plates, cream-netted scaly skin
        if (part == 0) {
            if (q.y < -0.25) return vec3(0.9, 0.84, 0.66);
            float e = cellEdge(q.xz * vec2(2.0, 2.4) + rnd * 3.0);
            float streak = 0.5 + 0.5 * sin(q.x * 31.0 + sin(q.z * 9.0) * 3.0);
            vec3 c = mix(vec3(0.48, 0.21, 0.09), vec3(0.7, 0.42, 0.2), streak * 0.5 + smoothstep(0.0, 0.5, e) * 0.3);
            return mix(vec3(0.82, 0.68, 0.48), c, smoothstep(0.03, 0.08, e));
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
        return mix(base, vec3(0.86, 0.88, 0.9), spot(q.xy * vec2(9.0, 7.0) + rnd * 3.0, 0.13) * smoothstep(-0.3, 0.3, q.x) * 0.85);
    }
    if (kind == 15) { // jellyfish: mostly sea nettles (orange, dark stripes), some pink-purple ones
        bool nettle = rnd < 0.6;
        if (part == 1) return nettle ? vec3(0.98, 0.9, 0.86) : vec3(0.9, 0.75, 1.0);
        if (part == 2) return nettle ? vec3(1.0, 0.55, 0.45) : vec3(0.95, 0.6, 0.95);
        vec3 bell = nettle ? vec3(1.0, 0.55, 0.22) : mix(vec3(1.0, 0.45, 0.85), vec3(0.6, 0.4, 1.0), rnd);
        if (nettle && q.y > -0.3 && abs(fract(atan(q.z, q.x) * 2.546) - 0.5) < 0.1) bell = vec3(0.5, 0.1, 0.07);
        return mix(bell, vec3(1.0, 0.9, 0.85), smoothstep(-0.2, -0.7, q.y)) * 1.15;
    }
    if (kind == 16) { // dolphin: dark grey back, lighter sides, pale belly, a smile
        vec3 dark = vec3(0.36, 0.39, 0.44);
        if (part > 0) return dark;
        vec3 c = mix(vec3(0.86, 0.86, 0.88), vec3(0.55, 0.58, 0.63), smoothstep(-0.55, -0.25, q.y));
        c = mix(c, dark, smoothstep(0.1, 0.5, q.y));
        if (q.x > 0.78 && abs(q.y + 0.12 + (q.x - 0.78) * 0.3) < 0.025) c *= 0.55;
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
    if (kind == 19) { // basking shark: grey-brown, faintly blotched; inside its gaping mouth, white gill rakers
        vec3 c = mix(vec3(0.6, 0.58, 0.55), vec3(0.4, 0.38, 0.36), smoothstep(-0.4, 0.3, q.y));
        if (part > 0) return c * 0.88;
        float m = length((q - vec3(0.78, -0.3, 0.0)) / vec3(0.3, 0.4, 0.45));
        if (m < 1.03 && q.x > 0.45) { // (the inside of the mouth)
            if (q.x < 0.7) return fract(q.z * 7.0 + 0.5) < 0.45 ? vec3(0.88, 0.86, 0.82) : vec3(0.14, 0.07, 0.07);
            return mix(vec3(0.62, 0.42, 0.42), vec3(0.3, 0.15, 0.15), smoothstep(0.95, 0.75, q.x));
        }
        c = mix(c, c * 1.12, spot(q.xy * vec2(7.0, 9.0) + rnd * 4.0, 0.3) * 0.7);
        float gx = q.x - 0.12 + 0.06 * q.y * q.y; // five long, curving gill slits round the head
        if (gx > 0.0 && gx < 0.4 && fract(gx * 12.5) < 0.07 && abs(q.y) < 0.8) c *= 0.62;
        return c;
    }
    // sardine: dark blue-green back, bright silver sides, golden tail and back fin
    if (tail || dorsal) return vec3(0.85, 0.78, 0.5);
    if (fin) return vec3(0.85, 0.85, 0.82);
    vec3 c = mix(vec3(0.86, 0.88, 0.92), vec3(0.16, 0.27, 0.38), smoothstep(0.1, 0.4, q.y));
    return q.x > 0.45 && q.x < 0.62 && abs(q.y) < 0.5 ? mix(c, vec3(0.8, 0.7, 0.45), 0.35) : c;
}

// how big a fish's eye is, against the usual
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

const int PARTS = 9;

// part j of a fish of this kind (radii scaled by the species, not yet by the fish's size); swing: tail beat (-1..1)
Part fishPart(int kind, Species sp, int j, float swing, float flap) {
    vec3 b = sp.body, tl = sp.tail, fn = sp.fin;
    bool forked = kind == 0 || kind == 1 || kind == 2 || kind == 3 || kind == 4 || kind == 9 || kind == 10 || kind == 11 || kind == 14 || kind == 17;
    if (kind == 13) { // sea turtle: shell, head, four flippers
        if (j == 0) return Part(vec3(0.0), b, 0.0, 0.0, 0);
        if (j == 1) return Part(vec3(b.x + tl.x * 0.4, 0.0, 0.0), tl, 0.0, 0.0, 1);
        if (j == 2 || j == 3) { float s = j == 2 ? 1.0 : -1.0;
            return Part(vec3(b.x * 0.35, -b.y * 0.15, s * b.z * 0.95), vec3(fn.x, fn.y, fn.z * 0.55), s * 0.5, 0.0, 1); }
        if (j == 4 || j == 5) { float s = j == 4 ? 1.0 : -1.0;
            return Part(vec3(-b.x * 0.7, -b.y * 0.2, s * b.z * 0.7), vec3(fn.x * 0.6, fn.y, fn.z * 0.25), -s * 0.7, 0.0, 1); }
        return Part(vec3(0.0), vec3(0.0), 0.0, 0.0, 0);
    }
    if (kind == 15) { // jellyfish: dome and four wavy tentacles
        if (j == 0) return Part(vec3(0.0), b, 0.0, 0.0, 0);
        if (j <= 4) { float a = float(j) * 1.5708;
            return Part(vec3(b.x * 0.45 * cos(a) + 0.03 * swing, -b.y - tl.y * 0.7, b.z * 0.45 * sin(a)), tl, 0.0, 0.08 * swing * (float(j) - 2.5), 1); }
        { float a = float(j) * 1.5708 + 0.785; // long thin tentacles from the rim
            return Part(vec3(b.x * 0.85 * cos(a) + 0.05 * swing, -b.y - tl.y * 0.95, b.z * 0.85 * sin(a)), vec3(0.018, tl.y * 1.15, 0.018), 0.0, 0.1 * swing * (float(j) - 6.5), 2); }
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
    if (kind == 7) return vec4(0.5, 0.5, 0.0, 0.0);
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
float fishBody(int kind, Species sp, vec3 p, float wave, float flap, out int ptype, out vec3 q) {
    float L = sp.body.x, H = sp.body.y, W = sp.body.z;
    vec4 sh = fishShape(kind);
    p.z += 0.2 * L * sin(wave - p.x / L * 2.2) * smoothstep(0.4 * L, -1.9 * L, p.x);
    // the body's outline at this point along it
    float s = clamp((p.x + L) / (2.0 * L), 0.0, 1.0);
    float u = (s - sh.x) / (s < sh.x ? sh.x : 1.0 - sh.x);
    float prof = pow(max(1.0 - u * u, 0.0), sh.y);
    float root = smoothstep(0.3, 0.0, s) * (kind == 18 ? 0.75 : 0.22); // (the sunfish ends as if cut off)
    float h = H * max(prof, root) + 0.002, w = W * max(prof, root * 0.8) + 0.002;
    float yy = clamp(p.y / h, -1.0, 1.0);
    float wl = kind == 7 ? w : w * (1.0 - 0.28 * yy * yy);
    float body = max((length(vec2(p.y / h, p.z / wl)) - 1.0) * min(h, wl), abs(p.x) - L);
    bool bony = kind != 10 && kind != 16 && kind != 19 && kind != 18;
    // eyes, bulging a little, and a small mouth
    float se = 0.81;
    float ue = (se - sh.x) / (1.0 - sh.x);
    float we = W * pow(max(1.0 - ue * ue, 0.0), sh.y);
    float eyeR = H * 0.13 * eyeScale(kind);
    float eye = length(vec3(p.x - L * 0.62, p.y - H * 0.22, abs(p.z) - we * 0.8)) - eyeR;
    if (kind != 10 && kind != 16 && kind != 19) {
        body = smin(body, eye, eyeR * 0.7);
    }
    vec3 mc = vec3(L * 1.0, -H * 0.12, 0.0), mr = vec3(L * 0.08, H * 0.05, W * 0.7);
    if (kind == 14) mr = vec3(L * 0.12, H * 0.1, W * 0.8); // (the grouper's big mouth)
    if (kind == 19) { mc = vec3(L * 0.78, -H * 0.3, 0.0); mr = vec3(L * 0.3, H * 0.4, W * 0.45); } // (gaping wide)
    body = max(body, -sdEllipsoid(p - mc, mr));
    float res = body, best = body;
    ptype = 0;
    q = p / sp.body;
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
            outer = p.y > 0.0 ? span * (0.2 + 1.0 * kt) : span * (0.2 + 0.55 * kt);
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
    // the fin along the back (swept back), and one underneath
    if (sp.fin.x > 0.0) {
        float fx1 = L * 0.3, fx0 = fx1 - sp.fin.x * 2.2;
        float kx = clamp((p.x - fx0) / (fx1 - fx0), 0.0, 1.0);
        float dh = sp.fin.y * pow(max(sin(kx * 3.1416), 0.0), 0.7) * (1.25 - 0.5 * kx) * (bony ? 1.0 - 0.07 * abs(sin(kx * 22.0)) : 1.0);
        float up = clamp((p.y - h * 0.92) / max(dh, 0.001), 0.0, 1.0);
        float dorsal = max(max(max(p.y - (h * 0.92 + dh), h * 0.55 - p.y), max(fx0 - p.x, p.x - fx1)) * 0.7, abs(p.z) - sp.fin.z * (1.0 - 0.6 * up));
        res = smin(res, dorsal, 0.04 * L);
        if (dorsal < best) {
            best = dorsal; ptype = 2;
            q = vec3(kx * 2.0 - 1.0, clamp((p.y - h) / max(dh, 0.001), 0.0, 1.0), 0.0);
        }
    }
    if (sh.w > 0.0) {
        float ax1 = -L * 0.05, ax0 = -L * 0.65;
        float kx = clamp((p.x - ax0) / (ax1 - ax0), 0.0, 1.0);
        float dh = H * 0.35 * sh.w * pow(max(sin(kx * 3.1416), 0.0), 0.7) * (1.2 - 0.4 * kx) * (bony ? 1.0 - 0.07 * abs(sin(kx * 18.0)) : 1.0);
        float down = clamp((-p.y - h * 0.92) / max(dh, 0.001), 0.0, 1.0);
        float anal = max(max(max(-p.y - (h * 0.92 + dh), h * 0.55 + p.y), max(ax0 - p.x, p.x - ax1)) * 0.7, abs(p.z) - max(sp.fin.z, 0.006) * (1.0 - 0.6 * down));
        res = smin(res, anal, 0.04 * L);
        if (anal < best) {
            best = anal; ptype = 2;
            q = vec3(kx * 2.0 - 1.0, -clamp((-p.y - h) / max(dh, 0.001), 0.0, 1.0), -1.0);
        }
    }
    // side fins, paddling
    if (kind != 8) {
        float big = kind == 10 || kind == 16 || kind == 19 ? 1.7 : 1.0;
        vec3 pp = vec3(p.x - L * 0.3, p.y + H * 0.35, abs(p.z) - W * 0.85);
        float a = 0.7 + 0.25 * flap;
        vec3 lq = vec3(pp.x * cos(a) - pp.z * sin(a), pp.y, pp.x * sin(a) + pp.z * cos(a));
        float pect = sdEllipsoid(lq, vec3(L * 0.2 * big, H * 0.05, W * 0.5 * big));
        res = smin(res, pect, 0.02 * L);
        if (pect < best) {
            best = pect; ptype = 2;
            q = vec3(lq.x / (L * 0.2 * big), 0.0, 2.0);
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

float fishSDF(int kind, Species sp, vec3 p, float wave, float flap, float pulse, out int ptype, out vec3 q) {
    if (kind != 12 && kind != 13 && kind != 15) {
        return fishBody(kind, sp, p, wave, flap, ptype, q);
    }
    float L = sp.body.x;
    bool bends = kind != 12 && kind != 13 && kind != 15;
    if (bends) {
        p.z += 0.2 * L * sin(wave - p.x / L * 2.2) * smoothstep(0.4 * L, -1.9 * L, p.x);
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
        if (kind == 15 && j == 0) {
            r.y *= pulse;
        }
        vec3 d0 = p - pt.at;
        vec3 fx = vec3(cos(pt.yaw), 0.0, sin(pt.yaw));
        vec3 fz = vec3(-sin(pt.yaw), 0.0, cos(pt.yaw));
        vec3 ax = fx * cos(pt.pitch) + vec3(0.0, sin(pt.pitch), 0.0);
        vec3 ay = vec3(0.0, cos(pt.pitch), 0.0) - fx * sin(pt.pitch);
        vec3 lq = vec3(dot(d0, ax), dot(d0, ay), dot(d0, fz));
        float d = sdEllipsoid(lq, r);
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
float decoSDF(int type, vec3 p, float t) {
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

int pickKind(float r) {
    if (Ocean < 0.5) {
        return int(r * 9.999);
    }
    // the ocean: more sardines and tuna than sharks
    if (r < 0.09) return 10;
    if (r < 0.21) return 11;
    if (r < 0.28) return 12;
    if (r < 0.38) return 13;
    if (r < 0.47) return 14;
    if (r < 0.56) return 15;
    if (r < 0.63) return 16;
    if (r < 0.70) return 18;
    if (r < 0.76) return 19;
    return 17;
}

vec4 fishData(int k) {
    return k == 0 ? Fish0 : k == 1 ? Fish1 : k == 2 ? Fish2 : k == 3 ? Fish3 : k == 4 ? Fish4 : k == 5 ? Fish5 : k == 6 ? Fish6 : k == 7 ? Fish7 : k == 8 ? Fish8 : k == 9 ? Fish9 : k == 10 ? Fish10 : k == 11 ? Fish11 : k == 12 ? Fish12 : k == 13 ? Fish13 : k == 14 ? Fish14 : k == 15 ? Fish15 : k == 16 ? Fish16 : k == 17 ? Fish17 : k == 18 ? Fish18 : k == 19 ? Fish19 : k == 20 ? Fish20 : k == 21 ? Fish21 : k == 22 ? Fish22 : k == 23 ? Fish23 : k == 24 ? Fish24 : k == 25 ? Fish25 : k == 26 ? Fish26 : k == 27 ? Fish27 : k == 28 ? Fish28 : k == 29 ? Fish29 : k == 30 ? Fish30 : k == 31 ? Fish31 : k == 32 ? Fish32 : k == 33 ? Fish33 : k == 34 ? Fish34 : k == 35 ? Fish35 : k == 36 ? Fish36 : k == 37 ? Fish37 : k == 38 ? Fish38 : k == 39 ? Fish39 : k == 40 ? Fish40 : k == 41 ? Fish41 : k == 42 ? Fish42 : k == 43 ? Fish43 : k == 44 ? Fish44 : k == 45 ? Fish45 : k == 46 ? Fish46 : k == 47 ? Fish47 : k == 48 ? Fish48 : k == 49 ? Fish49 : k == 50 ? Fish50 : k == 51 ? Fish51 : k == 52 ? Fish52 : k == 53 ? Fish53 : k == 54 ? Fish54 : k == 55 ? Fish55 : k == 56 ? Fish56 : k == 57 ? Fish57 : k == 58 ? Fish58 : k == 59 ? Fish59 : k == 60 ? Fish60 : k == 61 ? Fish61 : k == 62 ? Fish62 : Fish63;
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
    for (int i = 0; i < 64; i++) {
        if (tMax.x < tMax.y && tMax.x < tMax.z) {
            leave = tMax.x; cell.x += int(stepDir.x); tMax.x += tDelta.x; exitAxis = 0;
        } else if (tMax.y < tMax.z) {
            leave = tMax.y; cell.y += int(stepDir.y); tMax.y += tDelta.y; exitAxis = 1;
        } else {
            leave = tMax.z; cell.z += int(stepDir.z); tMax.z += tDelta.z; exitAxis = 2;
        }
        if (!water(cell)) {
            break;
        }
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
    bool fish = false;
    float fishAlpha = 1.0;
    vec3 edgeCol = vec3(0.0);
    float edgeA = 0.0, edgeT = 1.0e9;
    float pixA = max(length(fwidth(rd)), 1.0e-5); // (how wide a pixel is, as an angle)
    vec3 rot = ro - BoxMin;
    for (int i = 0; i < 32; i++) {
        if (i >= count) {
            break;
        }
        float fi = float(i);
        int kind = pickKind(hash11(fi * 7.7));
        Species sp = species(kind);
        // its own stretch of water (worked out on the CPU from the tank's shape), made to fit in it
        vec3 middle = fishData(i * 2).xyz, reach = fishData(i * 2 + 1).xyz;
        float extent = max(sp.body.x + sp.tail.x * 1.6, max(sp.body.z, sp.fin.z));
        float size = (Ocean > 0.5 ? 0.75 : 0.65) + 0.5 * hash11(fi * 3.1);
        if (kind == 18) size *= 1.9; // the sunfish is a giant (still made to fit the water it has)
        size = min(size, max(min(reach.x, reach.z) - 0.08, 0.1) / extent);
        float tallH = sp.body.y + (kind == 15 ? sp.tail.y * 2.0 : 0.0) + (kind == 18 ? 0.8 : 0.7) * max(sp.fin.y, sp.body.y * 0.42 * fishShape(kind).w) + (kind == 4 ? sp.body.y * 0.8 : 0.0);
        size = min(size, max(reach.y - 0.05, 0.1) / max(tallH, 0.05));
        float speed = (0.25 + 0.35 * hash11(fi * 5.7)) * sp.speed;
        vec3 range = max(reach - vec3(extent * size + 0.06, tallH * size + 0.04, extent * size + 0.06), vec3(0.02));
        float a1 = t * speed + hash11(fi) * 6.2831, a2 = t * speed * 0.73 + hash11(fi + 9.0) * 6.2831;
        float level = middle.y + (hash11(fi * 2.3) * 2.0 - 1.0) * range.y;
        float bob = kind == 15 ? 0.25 * sin(t * 0.6 + fi) : 0.08 * sin(t * 0.7 + fi);
        vec3 c = vec3(middle.x + range.x * sin(a1), clamp(level + bob, middle.y - range.y, middle.y + range.y), middle.z + range.z * sin(a2));
        vec3 vel = vec3(range.x * cos(a1) * speed, 0.0, range.z * cos(a2) * speed * 0.73);
        vec3 fwd = normalize(vel + vec3(1.0e-4, 0.0, 0.0));
        vec3 up = vec3(0.0, 1.0, 0.0);
        if (kind == 12) { // the manta banks as it turns, its wings rising and falling
            up = normalize(up + normalize(cross(fwd, up)) * 0.25 * sin(t * 1.5 + fi));
        }
        if (kind == 15) { // the jellyfish just drifts, upright
            fwd = vec3(1.0, 0.0, 0.0);
        }
        vec3 side = normalize(cross(fwd, up));
        float wave = t * (7.0 + 4.0 * speed) / max(size * extent * 4.0, 1.0) + fi * 2.0;
        // (a quick test first: does the ray pass near this fish at all?)
        float tallest = sp.body.y + max(sp.fin.y * 1.3, sp.body.y * 0.45 * fishShape(kind).w) + (kind == 4 ? sp.body.y : 0.0);
        float reachAll = size * max(sp.body.x * 1.3 + sp.tail.x * 2.2 + max(sp.body.z, sp.fin.z) + sp.tail.y * (kind == 15 ? 2.2 : 0.6),
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
        float flap = sin(t * 3.0 + fi * 1.3);
        float pulse = 0.85 + 0.15 * sin(t * 2.0 + fi);
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
            bool eyed = kind != 12 && kind != 15 && kind != 13;
            if (kind == 13 && part == 1 && abs(q.z) > 0.55) {
                float eyeD = length(vec2((q.x - 0.45) * 1.2, q.y - 0.2));
                if (eyeD < 0.13) { base = vec3(0.02); isEye = true; }
                else if (eyeD < 0.19) base = vec3(0.6, 0.45, 0.2);
            }
            if (eyed && part == 0 && abs(q.z) > 0.55) { // (where the eye bulges out)
                float er = sp.body.y * eyeScale(kind);
                vec2 ev = vec2((q.x - 0.62) * sp.body.x, (q.y - 0.22) * sp.body.y) / er;
                float eyeD = length(ev);
                if (eyeD < 0.115) {
                    isEye = true;
                    base = eyeD < 0.07 ? vec3(0.015) : mix(eyeRing(kind), eyeRing(kind) * 0.6, smoothstep(0.09, 0.115, eyeD));
                } else if (eyeD < 0.13) {
                    base *= 0.6; // (a dark rim round the eye)
                }
            }
            if (part >= 1 && kind != 13 && kind != 15 && kind != 10 && kind != 16 && kind != 19 && kind != 12) {
                base *= 0.88 + 0.12 * smoothstep(0.2, 0.5, abs(fract(q.x * 7.0 + q.y * 2.0) - 0.5) * 2.0); // (fin rays)
                base = mix(base, vec3(1.0), 0.1);
            }
            if (part == 0 && !isEye && kind != 12 && kind != 13 && kind != 15 && kind != 10 && kind != 16 && kind != 19 && kind != 18) {
                vec2 sc = vec2(q.x * 16.0, q.y * 10.0 + 0.5 * floor(q.x * 16.0));
                vec2 sf = fract(sc) - 0.5;
                base *= 0.92 + 0.08 * smoothstep(0.25, 0.5, length(sf + vec2(0.35, 0.0))); // (overlapping scales)
                float gx = q.x - (0.44 - 0.1 * q.y * q.y);
                if (gx > 0.0 && gx < 0.03 && abs(q.y) < 0.75) base *= 0.78;              // (the gill cover's edge)
                if (kind != 7 && q.x < 0.42 && abs(q.y - 0.18 + 0.12 * q.x * q.x) < 0.012) base *= 0.9; // (lateral line)
            }
            vec3 p = rot + rd * tf;
            vec3 sun = normalize(vec3(0.3, 1.0, 0.2));
            float diff = clamp(dot(n, sun) * 0.5 + 0.5, 0.0, 1.0);
            vec3 amb = mix(vec3(0.24, 0.34, 0.45), vec3(0.6, 0.76, 0.88), n.y * 0.5 + 0.5);
            float through = part >= 1 && kind != 13 ? max(-dot(n, sun), 0.0) * 0.35 + 0.2 * max(dot(rd, sun), 0.0) : 0.0; // (thin fins let light through)
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
            } else { // (a soft edge: laid over whatever is behind, once that is known)
                edgeT = tf;
                edgeCol = shade;
                edgeA = alpha;
            }
        }
    }

    // ---- decorations on the bottom (placed on the CPU, from the tank's floor) ----
    bool deco = false;
    vec3 decoCol = vec3(0.0);
    int decoCount = int(DecoCount + 0.5);
    for (int k = 0; k < 24; k++) {
        if (k >= decoCount) {
            break;
        }
        vec4 dd = decoData(k);
        int pk = int(dd.w + 0.5);
        int type = pk - (pk / 8) * 8;
        float ang = float((pk / 8) - (pk / 128) * 16) * 0.3927;
        float sz = float(pk / 128) / 10.0;
        vec3 base = dd.xyz;
        float rad = 0.5 * sz;
        vec3 oc = rot - (base + vec3(0.0, 0.2 * sz, 0.0));
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
        for (int s = 0; s < 40; s++) {
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
            if (tb2 > tEnter && tb2 < bubbleT) {
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
        if (down || onSand) {
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
    vec3 waterCol = Ocean > 0.5 ? mix(vec3(0.0, 0.1, 0.24), vec3(0.05, 0.38, 0.58), clamp(entry.y / BoxSize.y, 0.0, 1.0))
                                : mix(vec3(0.02, 0.2, 0.32), vec3(0.15, 0.55, 0.62), clamp(entry.y / BoxSize.y, 0.0, 1.0));
    col = mix(col, waterCol, 1.0 - exp(-travel * (Ocean > 0.5 ? 0.22 : 0.3)));
    col += vec3(0.25, 0.5, 0.55) * 0.08 * (0.5 + 0.5 * sin(entry.x * 4.0 + entry.z * 3.0 + entry.y * 2.0 + t * 1.5));

    // looking down through the top: the surface ripples catch the light
    if (faceN.y < -0.5) { // (a top face: faceN points into the tank, down)
        col += vec3(0.8, 0.95, 1.0) * caustics(entry.xz * 1.6 + vec2(t * 0.1, 0.0), t * 1.6) * 0.18;
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
