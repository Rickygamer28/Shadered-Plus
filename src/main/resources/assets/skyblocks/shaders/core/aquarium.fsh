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
uniform float FishCount;
uniform float Ocean;   // 1: the ocean tank (big sea animals, deeper water, coral)

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
// discus); 10-17 the ocean tank (shark, tuna, manta ray, sea turtle, grouper, jellyfish, dolphin, sardine).
struct Species {
    vec3 body;
    vec3 tail;
    vec3 fin;
    float speed;
};

Species species(int k) {
    if (k == 0) return Species(vec3(0.16, 0.075, 0.035), vec3(0.06, 0.065, 0.01), vec3(0.07, 0.03, 0.008), 1.0);
    if (k == 1) return Species(vec3(0.16, 0.09, 0.03), vec3(0.06, 0.07, 0.01), vec3(0.12, 0.03, 0.008), 1.0);
    if (k == 2) return Species(vec3(0.13, 0.11, 0.03), vec3(0.05, 0.06, 0.01), vec3(0.1, 0.04, 0.008), 0.9);
    if (k == 3) return Species(vec3(0.11, 0.04, 0.025), vec3(0.04, 0.04, 0.008), vec3(0.0), 1.4);
    if (k == 4) return Species(vec3(0.11, 0.13, 0.025), vec3(0.05, 0.08, 0.008), vec3(0.06, 0.15, 0.008), 0.6);
    if (k == 5) return Species(vec3(0.13, 0.09, 0.065), vec3(0.09, 0.09, 0.012), vec3(0.06, 0.04, 0.008), 0.7);
    if (k == 6) return Species(vec3(0.12, 0.05, 0.03), vec3(0.12, 0.12, 0.01), vec3(0.1, 0.07, 0.008), 0.6);
    if (k == 7) return Species(vec3(0.12, 0.1, 0.1), vec3(0.04, 0.04, 0.01), vec3(0.0), 0.5);
    if (k == 8) return Species(vec3(0.08, 0.03, 0.02), vec3(0.06, 0.05, 0.006), vec3(0.0), 1.3);
    if (k == 9) return Species(vec3(0.12, 0.13, 0.025), vec3(0.04, 0.05, 0.008), vec3(0.1, 0.03, 0.008), 0.6);
    if (k == 10) return Species(vec3(0.9, 0.18, 0.16), vec3(0.25, 0.3, 0.03), vec3(0.18, 0.22, 0.03), 0.8);
    if (k == 11) return Species(vec3(0.45, 0.13, 0.11), vec3(0.12, 0.18, 0.02), vec3(0.08, 0.1, 0.02), 1.3);
    if (k == 12) return Species(vec3(0.35, 0.05, 0.75), vec3(0.35, 0.015, 0.015), vec3(0.0), 0.6);
    if (k == 13) return Species(vec3(0.4, 0.16, 0.33), vec3(0.12, 0.08, 0.09), vec3(0.18, 0.03, 0.55), 0.5);
    if (k == 14) return Species(vec3(0.55, 0.25, 0.2), vec3(0.15, 0.2, 0.03), vec3(0.3, 0.08, 0.02), 0.5);
    if (k == 15) return Species(vec3(0.25, 0.18, 0.25), vec3(0.06, 0.4, 0.06), vec3(0.0), 0.25);
    if (k == 16) return Species(vec3(0.8, 0.17, 0.17), vec3(0.12, 0.04, 0.28), vec3(0.15, 0.18, 0.03), 1.1);
    return Species(vec3(0.12, 0.035, 0.025), vec3(0.04, 0.035, 0.006), vec3(0.0), 1.5);
}

vec3 hue(float h) {
    return clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

// a fish's colour at a point on it (q: that part's own coordinates, x along it nose-first, -1..1; part 0 body,
// 1 tail (the turtle's head), 2 fin); rnd: this fish's own random number (colour variants)
vec3 fishColour(int kind, vec3 q, int part, float rnd) {
    bool tail = part == 1, fin = part == 2;
    if (kind == 0) { // clownfish: orange, white bands edged black
        float b = min(abs(q.x - 0.45), min(abs(q.x + 0.05), abs(q.x + 0.55)));
        vec3 c = b < 0.09 ? vec3(0.95) : b < 0.13 ? vec3(0.05) : vec3(1.0, 0.42, 0.05);
        return part > 0 ? vec3(1.0, 0.42, 0.05) : c;
    }
    if (kind == 1) { // blue tang: blue, dark mark, yellow tail
        if (tail) return vec3(1.0, 0.85, 0.1);
        return q.y > 0.2 && abs(q.x) < 0.6 ? vec3(0.03, 0.06, 0.2) : vec3(0.1, 0.35, 0.95);
    }
    if (kind == 2) return vec3(1.0, 0.86, 0.1) * (tail ? 0.9 : 1.0); // yellow tang
    if (kind == 3) { // neon tetra
        if (abs(q.y - 0.15) < 0.18 && !tail) return vec3(0.1, 0.9, 1.0) * 1.3;
        if (q.y < -0.05 && q.x < 0.2 && !tail) return vec3(0.9, 0.1, 0.15);
        return vec3(0.75, 0.78, 0.85);
    }
    if (kind == 4) { // angelfish: silver, black bands, long fins
        if (part > 0) return mix(vec3(0.85, 0.85, 0.9), vec3(0.1), step(0.75, abs(q.y)));
        return abs(fract(q.x * 1.4 + 0.15) - 0.5) < 0.12 ? vec3(0.08) : vec3(0.82, 0.84, 0.88);
    }
    if (kind == 5) { // goldfish
        vec3 c = mix(vec3(1.0, 0.45, 0.05), vec3(1.0, 0.8, 0.35), smoothstep(0.0, -0.8, q.y));
        return part > 0 ? vec3(1.0, 0.55, 0.15) : c;
    }
    if (kind == 6) { // betta: deep body, big glowing fins in its own colour
        vec3 c = rnd < 0.33 ? vec3(0.75, 0.05, 0.12) : rnd < 0.66 ? vec3(0.1, 0.15, 0.85) : vec3(0.55, 0.1, 0.75);
        return part > 0 ? mix(c, c * 1.4 + 0.1, abs(q.y)) : c * 0.8;
    }
    if (kind == 7) { // pufferfish: spotted on top, white belly
        if (q.y < -0.2) return vec3(0.95, 0.93, 0.85);
        vec2 s = fract(q.xz * 3.5) - 0.5;
        return length(s) < 0.18 ? vec3(0.25, 0.18, 0.08) : vec3(0.85, 0.72, 0.35);
    }
    if (kind == 8) return tail ? hue(rnd) * 1.1 : vec3(0.65, 0.68, 0.72); // guppy: a colourful tail
    if (kind == 9) { // discus: red with blue wavy lines
        return sin(q.y * 18.0 + sin(q.x * 7.0) * 1.5) > 0.55 ? vec3(0.15, 0.45, 0.95) : vec3(0.85, 0.2, 0.15);
    }
    if (kind == 10) return q.y < -0.25 && part == 0 ? vec3(0.9, 0.9, 0.92) : vec3(0.42, 0.48, 0.55); // shark
    if (kind == 11) { // tuna
        if (fin) return vec3(0.95, 0.8, 0.2);
        return q.y > 0.3 ? vec3(0.05, 0.12, 0.35) : vec3(0.72, 0.78, 0.85);
    }
    if (kind == 12) return q.y > 0.0 ? vec3(0.08, 0.08, 0.1) : vec3(0.9, 0.9, 0.92); // manta ray
    if (kind == 13) { // sea turtle: patterned shell, spotted olive skin
        if (part == 0) {
            vec2 s = abs(fract(q.xz * vec2(2.2, 2.6)) - 0.5);
            return max(s.x, s.y) > 0.42 ? vec3(0.22, 0.18, 0.08) : mix(vec3(0.42, 0.32, 0.12), vec3(0.3, 0.38, 0.15), rnd);
        }
        vec2 s = fract(q.xz * 4.0) - 0.5;
        return length(s) < 0.2 ? vec3(0.25, 0.28, 0.15) : vec3(0.48, 0.55, 0.32);
    }
    if (kind == 14) { // grouper: brown with darker blotches
        vec2 s = fract(q.xy * vec2(3.0, 2.5) + rnd) - 0.5;
        return length(s) < 0.22 ? vec3(0.25, 0.17, 0.1) : vec3(0.5, 0.36, 0.22);
    }
    if (kind == 15) return mix(vec3(1.0, 0.45, 0.85), vec3(0.6, 0.4, 1.0), rnd) * (tail ? 0.8 : 1.2); // jellyfish
    if (kind == 16) return q.y < -0.3 && part == 0 ? vec3(0.82, 0.85, 0.88) : vec3(0.45, 0.5, 0.58); // dolphin
    return q.y > 0.25 ? vec3(0.15, 0.3, 0.5) : vec3(0.8, 0.84, 0.9); // sardine
}

int pickKind(float r) {
    if (Ocean < 0.5) {
        return int(r * 9.999);
    }
    // the ocean: more sardines and tuna than sharks
    if (r < 0.1) return 10;
    if (r < 0.25) return 11;
    if (r < 0.33) return 12;
    if (r < 0.45) return 13;
    if (r < 0.56) return 14;
    if (r < 0.66) return 15;
    if (r < 0.74) return 16;
    return 17;
}

vec4 fishData(int k) {
    return k == 0 ? Fish0 : k == 1 ? Fish1 : k == 2 ? Fish2 : k == 3 ? Fish3 : k == 4 ? Fish4 : k == 5 ? Fish5 : k == 6 ? Fish6 : k == 7 ? Fish7 : k == 8 ? Fish8 : k == 9 ? Fish9 : k == 10 ? Fish10 : k == 11 ? Fish11 : k == 12 ? Fish12 : k == 13 ? Fish13 : k == 14 ? Fish14 : k == 15 ? Fish15 : k == 16 ? Fish16 : k == 17 ? Fish17 : k == 18 ? Fish18 : k == 19 ? Fish19 : k == 20 ? Fish20 : k == 21 ? Fish21 : k == 22 ? Fish22 : k == 23 ? Fish23 : k == 24 ? Fish24 : k == 25 ? Fish25 : k == 26 ? Fish26 : k == 27 ? Fish27 : k == 28 ? Fish28 : k == 29 ? Fish29 : k == 30 ? Fish30 : k == 31 ? Fish31 : k == 32 ? Fish32 : k == 33 ? Fish33 : k == 34 ? Fish34 : k == 35 ? Fish35 : k == 36 ? Fish36 : k == 37 ? Fish37 : k == 38 ? Fish38 : Fish39;
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
    int bits = int(o[word - v * 4] + 0.5);
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
    vec3 rot = ro - BoxMin;
    for (int i = 0; i < 20; i++) {
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
        size = min(size, max(min(reach.x, reach.z) - 0.08, 0.1) / extent);
        size = min(size, max(reach.y - 0.05, 0.1) / max(sp.body.y + sp.tail.y * (kind == 15 ? 2.0 : 0.0), 0.05));
        float speed = (0.25 + 0.35 * hash11(fi * 5.7)) * sp.speed;
        vec3 range = max(reach - vec3(extent * size + 0.06, sp.body.y * size + 0.06, extent * size + 0.06), vec3(0.02));
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
        float beat = sin(t * (7.0 + 4.0 * speed) / max(size * extent * 4.0, 1.0) + fi * 2.0);
        vec3 bodyFwd = normalize(fwd + side * beat * 0.06);
        vec3 bodySide = normalize(cross(bodyFwd, up));
        vec3 bodyR = sp.body * size;
        if (kind == 15) {
            bodyR.y *= 0.85 + 0.15 * sin(t * 2.0 + fi); // (pulsing)
        }
        vec3 n, q;
        float tf = ellipsoid(rot, rd, c, bodyFwd, up, bodySide, bodyR, n, q);
        int part = 0;
        // tail (the turtle's head, in front; the jellyfish's tentacles, below; the dolphin's flukes, flat)
        vec3 tailDir = normalize(-fwd + side * beat * 0.5);
        vec3 tc = c - fwd * (sp.body.x + sp.tail.x * 0.6) * size + side * beat * 0.03 * size;
        vec3 tAx = tailDir, tAy = up;
        if (kind == 13) { tc = c + fwd * (sp.body.x + sp.tail.x * 0.4) * size; tAx = fwd; }
        if (kind == 15) { tc = c - up * (bodyR.y + sp.tail.y * size * 0.7); tAx = fwd; }
        vec3 n2, q2;
        float tt = ellipsoid(rot, rd, tc, tAx, tAy, normalize(cross(tAx, tAy)), sp.tail * size, n2, q2);
        if (tt > 0.0 && (tf < 0.0 || tt < tf)) {
            tf = tt; n = n2; q = q2; part = 1;
        }
        // fin: a dorsal fin on top (the turtle's flippers, flapping)
        if (sp.fin.x > 0.0) {
            vec3 fc = c + up * bodyR.y * 0.85 - fwd * sp.body.x * size * 0.1;
            vec3 fUp = up;
            if (kind == 13) {
                fc = c + fwd * sp.body.x * size * 0.25 - up * bodyR.y * 0.2;
                fUp = normalize(up + side * 0.0 + fwd * 0.3 * sin(t * 1.8 + fi));
            }
            vec3 n3, q3;
            float tfin = ellipsoid(rot, rd, fc, fwd, fUp, normalize(cross(fwd, fUp)), sp.fin * size, n3, q3);
            if (tfin > 0.0 && (tf < 0.0 || tfin < tf)) {
                tf = tfin; n = n3; q = q3; part = 2;
            }
        }
        if (tf > 0.0 && tf > tEnter - 0.01 && tf < hitT) {
            hitT = tf;
            fish = true;
            float rnd = hash11(fi * 13.3);
            vec3 base = fishColour(kind, q, part, rnd);
            // the eye
            bool eyed = kind != 12 && kind != 15 && kind != 13;
            if ((eyed && part == 0 && q.x > 0.6 && abs(q.y - 0.2) < 0.18 && abs(q.z) > 0.6)
                    || (kind == 13 && part == 1 && q.x > 0.3 && abs(q.y - 0.2) < 0.25 && abs(q.z) > 0.6)) {
                base = vec3(0.02);
            }
            vec3 p = rot + rd * tf;
            float light = 0.45 + 0.55 * max(n.y, 0.0) + 0.25 * max(dot(n, -rd), 0.0);
            float caust = caustics(p.xz, t) * max(n.y, 0.0) * (p.y / BoxSize.y);
            fishCol = base * light + vec3(0.6, 0.9, 1.0) * caust * 0.5;
            fishCol += vec3(1.0) * pow(max(dot(reflect(rd, n), vec3(0.0, 1.0, 0.0)), 0.0), 24.0) * (kind == 13 ? 0.15 : 0.4);
            fishAlpha = kind == 15 ? 0.55 : 1.0; // (the jellyfish is see-through)
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
    if (fish && fishAlpha > 0.99) {
        col = fishCol;
    } else {
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
            vec2 cell = floor(p.xz * 9.0);
            float pebble = hash22(cell).x > 0.93 ? 0.7 : 1.0;
            if (Ocean > 0.5 && hash22(floor(p.xz * 2.0)).y > 0.86) pebble = 0.45; // (rocks)
            col = vec3(0.85, 0.75, 0.52) * pebble * (0.75 + 0.1 * hash22(floor(p.xz * 40.0)).x);
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
    }
    if (bubbleT < hitT) {
        col = mix(col, bubbleCol, 0.7);
        hitT = bubbleT;
    }

    // ---- the water between: deeper colour with distance, lighter near the top, a soft shimmer ----
    float travel = max(hitT - tEnter, 0.0);
    vec3 waterCol = Ocean > 0.5 ? mix(vec3(0.0, 0.1, 0.24), vec3(0.05, 0.38, 0.58), clamp(entry.y / BoxSize.y, 0.0, 1.0))
                                : mix(vec3(0.02, 0.2, 0.32), vec3(0.15, 0.55, 0.62), clamp(entry.y / BoxSize.y, 0.0, 1.0));
    col = mix(col, waterCol, 1.0 - exp(-travel * (Ocean > 0.5 ? 0.16 : 0.38)));
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
