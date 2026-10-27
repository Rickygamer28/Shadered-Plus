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
uniform float Light;   // the tank's light: 0 daylight, 1 moonlight, 2 sunset, 3 reef
uniform float Deep;    // 1: the deep sea aquarium (always dark; no plants, no sunlight; its creatures glow)
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
// the tank's light (chosen in its menu): what it does to everything seen in the tank, and the water's own colour
vec3 lightGrade(vec3 c) {
    int l = int(Light + 0.5);
    float lum = dot(c, vec3(0.3, 0.55, 0.15));
    if (l == 1) return mix(vec3(lum), c, 0.55) * vec3(0.42, 0.56, 0.98);                 // moonlight: cool, dim, silvery
    if (l == 4) return mix(vec3(lum), c, 0.5) * vec3(0.3, 0.42, 0.85);                   // jellies: dark blue, the jellyfish lighting it
    if (l == 2) return mix(c * vec3(1.2, 0.84, 0.6), lum * vec3(1.4, 0.82, 0.58), 0.4) + vec3(0.03, 0.0, 0.0); // sunset: warm orange-pink
    if (l == 3) { // reef: richer colours, a violet-blue light
        c = max(mix(vec3(lum), c, 1.45), 0.0);
        return mix(c * vec3(0.92, 0.8, 1.15), lum * vec3(0.85, 0.55, 1.35), 0.3);
    }
    return c;
}

vec3 lightWater(float y, vec3 usual) {
    int l = int(Light + 0.5);
    if (l == 1) return mix(vec3(0.0, 0.02, 0.1), vec3(0.04, 0.13, 0.33), y);
    if (l == 4) return mix(vec3(0.0, 0.01, 0.07), vec3(0.02, 0.08, 0.24), y);
    if (l == 2) return mix(vec3(0.13, 0.06, 0.11), vec3(0.58, 0.34, 0.3), y);
    if (l == 3) return mix(vec3(0.05, 0.02, 0.18), vec3(0.24, 0.2, 0.58), y);
    return usual;
}

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

#moj_import <skyblocks:aqua_creatures.glsl>

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

// ---- the treasure chest (5): now and then its lid pops open, a burst of bubbles rising out of it, gold coins heaped
// inside glinting, then it slowly falls shut again. Each chest keeps its own time (from where it stands: the same on
// the CPU, for the sound) ----
const float CHEST_CYCLE = 14.0;
float gDecoPhase = 0.0; // (this chest's own time offset)

float chestPhase(vec3 base) {
    return mod(base.x * 3.7 + base.z * 5.3, CHEST_CYCLE);
}

// how far open the lid is (radians): a rattle, a quick pop open with a little bounce, a while open, slowly shut
float chestOpen(float t) {
    float c = mod(t + gDecoPhase, CHEST_CYCLE);
    if (c > CHEST_CYCLE - 0.7) return 0.05 * abs(sin(c * 38.0)) * smoothstep(CHEST_CYCLE - 0.7, CHEST_CYCLE - 0.3, c); // (the rattle)
    float a = c < 0.3 ? sin(c / 0.3 * 1.5708) * 1.12
            : c < 3.2 ? 1.0 + 0.12 * exp(-(c - 0.3) * 4.0) * cos((c - 0.3) * 16.0)
            : 1.0 - smoothstep(3.2, 5.0, c);
    return a * 1.05;
}

// a point in the closed lid's frame (the lid turned up round its hinge at the back)
vec3 chestLid(vec3 p, float a) {
    vec2 h = p.yz - vec2(0.14, -0.09);
    h = vec2(h.x * cos(a) - h.y * sin(a), h.x * sin(a) + h.y * cos(a));
    return vec3(p.x, h + vec2(0.14, -0.09));
}

// the chest's parts: (body, lid, gold heap)
vec3 chestParts(vec3 p, float t) {
    float body = sdBox(p - vec3(0.0, 0.07, 0.0), vec3(0.14, 0.07, 0.09)) - 0.004;
    body = max(body, -sdBox(p - vec3(0.0, 0.13, 0.0), vec3(0.124, 0.035, 0.074))); // (hollow: open at the top)
    float heap = 0.104 + 0.018 * (1.0 - length(p.xz / vec2(0.124, 0.074))) + 0.003 * sin(p.x * 160.0) * sin(p.z * 170.0);
    float gold = max(sdBox(p - vec3(0.0, 0.08, 0.0), vec3(0.124, 0.06, 0.074)), p.y - heap);
    vec3 l = chestLid(p, chestOpen(t));
    float lid = max(max(length(l.yz - vec2(0.14, 0.0)) - 0.09, abs(l.x) - 0.14), 0.14 - l.y);
    lid = max(lid, -max(max(length(l.yz - vec2(0.14, 0.0)) - 0.078, abs(l.x) - 0.128), 0.13 - l.y)); // (hollow underneath)
    return vec3(body, lid, gold);
}

float decoSDF(int type, vec3 p, float t) {
    if (type == 14) { // an air stone: a little round porous stone, its bubbles rising from it
        vec2 d = vec2(length(p.xz) - 0.06, abs(p.y - 0.024) - 0.022);
        return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - 0.008;
    }
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
        vec3 c = chestParts(p, t);
        return min(min(c.x, c.y), c.z);
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
    if (type == 14) { // the air stone: blue-grey, fine pores all over it, a ring of tiny bubbles just leaving its top
        vec3 c = vec3(0.56, 0.62, 0.68) * (0.85 + 0.25 * hash22(floor(p.xz * 140.0 + p.y * 90.0)).x);
        c *= 1.0 - 0.45 * step(0.82, hash22(floor(vec2(atan(p.z, p.x) * 18.0, p.y * 120.0 + length(p.xz) * 140.0))).y);
        return c;
    }
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
        vec3 c = chestParts(p, gTime);
        if (c.z <= min(c.x, c.y) + 0.0005) { // the gold inside: a heap of coins, glinting
            vec2 cp = p.xz * 70.0;
            cp.y += 0.5 * floor(cp.x);
            float coin = smoothstep(0.42, 0.36, length(fract(cp) - 0.5));
            vec3 g = mix(vec3(0.75, 0.52, 0.12), vec3(1.0, 0.85, 0.35), coin) * 1.25;
            float glint = step(0.93, hash22(floor(cp) + floor(gTime * 3.0)).x);
            return g + vec3(1.0, 0.95, 0.7) * glint * 1.5;
        }
        vec3 q = c.y < c.x ? chestLid(p, chestOpen(gTime)) : p; // (the lid's bands turn up with it)
        bool inside = c.y < c.x ? length(q.yz - vec2(0.14, 0.0)) < 0.084 && abs(q.x) < 0.134 : max(abs(q.x) - 0.12, abs(q.z) - 0.07) < 0.0 && q.y > 0.09;
        if (inside) return vec3(0.22, 0.12, 0.06); // (the dark wood inside)
        bool gold = abs(abs(q.x) - 0.09) < 0.015 || abs(q.y - 0.14) < 0.012 || (c.y < c.x && abs(q.x) < 0.02 && q.z > 0.07 && q.y < 0.19);
        return gold ? vec3(1.0, 0.8, 0.25) * 1.2 : vec3(0.42, 0.25, 0.12) * (0.8 + 0.2 * step(0.5, fract(q.y * 25.0)));
    }
    if (type == 6) return mix(vec3(0.95, 0.5, 0.2), mix(vec3(0.75, 0.2, 0.9), vec3(1.0, 0.5, 0.9), rnd), smoothstep(0.05, 0.16, p.y)) * 1.1;
    return abs(p.y - 0.16) < 0.015 ? vec3(0.12, 0.08, 0.06) : vec3(0.75, 0.38, 0.2) * (0.85 + 0.15 * speck);
}



// rings spreading out on the surface above the air stones (where their bubbles burst)
float stoneRipple(vec2 p, vec3 s0, vec3 s1, float t) {
    float r = 0.0;
    for (int i = 0; i < 2; i++) {
        vec3 s = i == 0 ? s0 : s1;
        if (s.y < -1.0) continue;
        float d = length(p - s.xz);
        r += max(sin(d * 45.0 - t * 7.0), 0.0) * exp(-d * 9.0) + exp(-d * d * 900.0) * (0.5 + 0.5 * sin(t * 13.0));
    }
    return r;
}

vec4 decoData(int k) {
    return k == 0 ? Deco0 : k == 1 ? Deco1 : k == 2 ? Deco2 : k == 3 ? Deco3 : k == 4 ? Deco4 : k == 5 ? Deco5 : k == 6 ? Deco6 : k == 7 ? Deco7 : k == 8 ? Deco8 : k == 9 ? Deco9 : k == 10 ? Deco10 : k == 11 ? Deco11 : k == 12 ? Deco12 : k == 13 ? Deco13 : k == 14 ? Deco14 : k == 15 ? Deco15 : k == 16 ? Deco16 : k == 17 ? Deco17 : k == 18 ? Deco18 : k == 19 ? Deco19 : k == 20 ? Deco20 : k == 21 ? Deco21 : k == 22 ? Deco22 : Deco23;
}

bool water(ivec3 c);

// ---- the "jellies" light: the tank full of small moon jellyfish drifting, each glowing softly - a pale see-through
// bell with four lilac rings in it, pulsing, its short frilly arms trailing. One maybe in each little cell of water
// along the ray (front to back); returns their colour over what's behind (premultiplied) and how much they cover ----
vec4 jellies(vec3 org, vec3 rd, float tMin, float tMax, float t) {
    const float JC = 1.2; // (big cells, few of them with a jellyfish: here and there, not a crowd)
    vec3 rdn = mix(rd, vec3(1.0e-5), lessThan(abs(rd), vec3(1.0e-5)));
    vec3 p0 = org + rd * tMin;
    vec3 cell = floor(p0 / JC);
    vec3 stp = sign(rdn);
    vec3 tD = abs(JC / rdn);
    vec3 tM = ((cell + max(stp, 0.0)) * JC - p0) / rdn + tMin;
    vec4 acc = vec4(0.0);
    for (int i = 0; i < 24; i++) {
        vec2 h1 = hash22(cell.xy + cell.z * 7.31 + 0.37), h2 = hash22(cell.zx * 1.7 + cell.y * 3.1 + 5.3);
        if (h1.x < 0.09) {
            float r = 0.065 + 0.035 * h2.x;
            float ph = h1.y * 6.28 + t * (0.55 + 0.25 * h2.y);
            float pulse = pow(max(sin(ph), 0.0), 2.0);
            vec3 c = (cell + 0.5) * JC + vec3((h2.y - 0.5) * 0.6, (fract(h1.y * 7.3) - 0.5) * 0.45 + 0.04 * sin(t * 0.35 + h2.x * 6.0) + 0.025 * pulse, (fract(h2.x * 5.1) - 0.5) * 0.6);
            if (c.y > 0.3 && c.y < BoxSize.y - 0.12 && water(ivec3(floor(c)))) {
                vec3 rad = vec3(r * (1.0 - 0.12 * pulse), r * 0.72 * (1.0 + 0.1 * pulse), r * (1.0 - 0.12 * pulse));
                vec3 oc = (org - c) / rad, dd = rd / rad;
                float a = dot(dd, dd), b = dot(oc, dd), k = dot(oc, oc) - 1.0, disc = b * b - a * k;
                float along = dot(c - org, rd);
                float fog = exp(-max(along - tMin, 0.0) * 0.18);
                vec3 glowC = vec3(0.0);
                float glowA = 0.0;
                if (along > tMin && along < tMax) { // (a soft glow round it, and its arms hanging under it)
                    float dc = length(org + rd * along - c);
                    glowC += vec3(0.35, 0.6, 1.0) * exp(-dc * dc / (r * r * 2.2)) * 0.22;
                    vec3 a0 = c - vec3(0.0, 0.05 * r, 0.0), a1 = c - vec3(0.0, 1.5 * r, 0.0);
                    vec3 w = a1 - a0;
                    vec3 cr = cross(rd, w);
                    float lc = length(cr);
                    if (lc > 1.0e-5) {
                        float dl = abs(dot(org - a0, cr)) / lc;
                        float s = dot(cross(a0 - org, rd), cr) / (lc * lc); // (how far down the arms)
                        float wid = r * (0.2 + 0.1 * sin(s * 18.0 + t * 2.0 + h2.x * 9.0));
                        float arm = exp(-dl * dl / (wid * wid)) * smoothstep(0.0, 0.15, s) * (1.0 - smoothstep(0.5, 1.0, s));
                        glowC += vec3(0.7, 0.6, 1.0) * arm * 0.5;
                        glowA += arm * 0.25;
                    }
                }
                vec3 jc = glowC;
                float ja = glowA;
                if (disc > 0.0) {
                    float sq = sqrt(disc);
                    for (int side = 0; side < 2; side++) {
                        float th = (-b + (side == 0 ? -sq : sq)) / a;
                        vec3 lp = oc + dd * th; // (on the unit sphere)
                        if (th < tMin || th > tMax || lp.y < -0.05) continue;
                        vec3 n = normalize(lp / rad);
                        float fres = pow(1.0 - abs(dot(n, rd)), 2.0);
                        vec2 xz = lp.xz;
                        float rings = 0.0;
                        for (int g = 0; g < 4; g++) { // (the four lilac horseshoe rings, seen through the top)
                            float ang = float(g) * 1.5708 + 0.785;
                            float dr = length(xz - vec2(cos(ang), sin(ang)) * 0.36);
                            rings = max(rings, smoothstep(0.06, 0.0, abs(dr - 0.17)));
                        }
                        float rim = smoothstep(0.15, 0.0, lp.y) * (0.6 + 0.4 * sin(atan(xz.y, xz.x) * 16.0)); // (the frilly rim)
                        vec3 bc = mix(vec3(0.6, 0.78, 1.0), vec3(0.85, 0.55, 1.0), rings) * (0.45 + 0.9 * fres + 0.3 * rim + 0.25 * pulse);
                        float ba = 0.12 + 0.5 * fres + 0.35 * rings + 0.2 * rim;
                        jc += (1.0 - ja) * bc * ba;
                        ja += (1.0 - ja) * ba;
                    }
                }
                acc.rgb += (1.0 - acc.a) * jc * fog;
                acc.a += (1.0 - acc.a) * clamp(ja, 0.0, 1.0) * fog;
                if (acc.a > 0.97) break;
            }
        }
        float tn = min(tM.x, min(tM.y, tM.z));
        if (tn > tMax) break;
        if (tM.x < tM.y && tM.x < tM.z) { cell.x += stp.x; tM.x += tD.x; }
        else if (tM.y < tM.z) { cell.y += stp.y; tM.y += tD.y; }
        else { cell.z += stp.z; tM.z += tD.z; }
    }
    return acc;
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
        // (only a roofed space - a tunnel or room built into the tank, the water over it - is seen across; open air
        // beside or under some part of the tank (an arch, an L) is the world's, not the tank's: the view stops there)
        bool roofed = water(cell + ivec3(0, 1, 0)) || water(cell + ivec3(0, 2, 0)) || water(cell + ivec3(0, 3, 0));
        if (!roofed || (inAir && leave - airStart > 6.0)) {
            if (!inAir) {
                inAir = true;
                airStart = leave;
                airCell = cell;
                airAxis = exitAxis;
            }
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
    vec3 lureHalo = vec3(0.0); // (the glow round anglerfish's lures, in the water)
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
        if (kind == 33) { // an anglerfish's lure: a soft glow in the water round its bulb (seen even where the fish isn't)
            vec3 lure = anglerLure(sp.body.x, sp.body.y);
            vec3 lw = c + (fwd * lure.x + up * lure.y + side * lure.z) * size - rot;
            float along = dot(lw, rd);
            if (along > tEnter && along < hitT) {
                float dd = length(lw - rd * along);
                lureHalo += vec3(0.35, 0.85, 1.0) * exp(-dd * dd / (0.0036 * size * size)) * 0.55;
            }
        }
        // (a quick test first: does the ray pass near this fish at all?)
        float tallest = sp.body.y + max(sp.fin.y * 1.3, sp.body.y * 0.45 * fishShape(kind).w) + (kind == 4 ? sp.body.y : 0.0)
                + (kind == 33 ? sp.body.y * 0.75 : 0.0); // (the anglerfish's lure)
        float reachAll = isCrawler(kind) ? size * (length(sp.body) + 0.02) : isSquid(kind) ? size * 0.62 : size * max(sp.body.x * 1.3 + sp.tail.x * 2.2 + max(sp.body.z, sp.fin.z) + sp.tail.y * (kind == 15 ? 2.2 : 0.6),
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
        if (isFish(kind)) { // a much tighter box round it, in its own frame: far less tracing
            float L = sp.body.x;
            float xb = -(0.92 * L + 2.3 * sp.tail.x) - 0.01, xf = (kind == 33 ? 1.45 : 1.2) * L;
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
            bool eyed = isFish(kind);
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
            if (part >= 1 && part <= 2 && isFish(kind) && kind != 10 && kind != 16 && kind != 19 && kind != 29) {
                float rc = part == 1 ? q.y * 8.0 : q.x * 10.0; // (rays run along a tail, up a fin)
                float ray = 1.0 - smoothstep(0.0, 0.25, abs(fract(rc) - 0.5) - 0.2);
                // (faded out where the rays would be finer than a few pixels: far off they'd shimmer)
                float per = (part == 1 ? sp.tail.y * 0.25 : sp.body.x * 0.1) * size;
                base *= 1.0 - 0.16 * ray * smoothstep(2.0, 5.0, per / (pixA * tf));
                float edge = part == 1 ? 0.5 - 0.5 * q.x : abs(q.y);
                base = mix(base, base * 1.15 + vec3(0.05, 0.08, 0.1), smoothstep(0.5, 1.0, edge) * 0.5);
            }
            if (part == 0 && !isEye && isFish(kind) && kind != 10 && kind != 16 && kind != 19 && kind != 18 && kind != 29 && kind != 33) {
                float ss = sp.body.y * (kind == 5 || kind == 17 ? 0.13 : 0.1);
                vec2 sc = vec2(-q.x * sp.body.x, q.y * sp.body.y) / ss;
                sc.y += 0.5 * floor(sc.x);
                vec2 sf = fract(sc) - 0.5;
                float rim = smoothstep(0.32, 0.5, length(sf - vec2(0.45, 0.0)));
                float sv = hash22(floor(sc)).x;
                float strength = kind == 5 ? 0.2 : kind == 17 ? 0.16 : kind == 7 ? 0.0 : kind == 11 ? 0.05 : kind == 26 ? 0.12 : 0.09;
                strength *= smoothstep(1.5, 4.0, ss * size / (pixA * tf)); // (fading where too fine to see: no shimmer)
                base *= 1.0 - strength * (rim * 0.7 + (sv - 0.5) * 0.4); // (overlapping scales)
                float gx = q.x - (0.44 - 0.1 * q.y * q.y);
                if (kind != 7 && gx > 0.0 && gx < 0.03 && abs(q.y) < 0.75) base *= 0.78;              // (the gill cover's edge)
                if (kind != 7 && q.x < 0.42 && abs(q.y - 0.18 + 0.12 * q.x * q.x) < 0.012) base *= 0.9; // (lateral line)
            }
            vec3 p = rot + rd * tf;
            vec3 sun = normalize(vec3(0.3, 1.0, 0.2));
            float diff = clamp(dot(n, sun) * 0.5 + 0.5, 0.0, 1.0);
            vec3 amb = mix(vec3(0.24, 0.34, 0.45), vec3(0.6, 0.76, 0.88), n.y * 0.5 + 0.5);
            float through = part >= 1 && kind != 13 && !isCrawler(kind) ? max(-dot(n, sun), 0.0) * 0.35 + 0.2 * max(dot(rd, sun), 0.0) : 0.0; // (thin fins let light through)
            float irid = part != 0 ? 0.0 : kind == 3 || kind == 17 ? 0.28 : kind == 6 || kind == 8 ? 0.25 : kind == 11 ? 0.2 : kind == 4 || kind == 27 || isSquid(kind) ? 0.12 : kind == 1 ? 0.1 : 0.0;
            if (irid > 0.0) {
                float fa = 1.0 - max(dot(n, -rd), 0.0);
                base = mix(base, base + hue(fa * 0.9 + q.x * 0.25 + 0.45) * 0.5, irid * (0.4 + 0.6 * fa));
            }
            vec3 shade = base * (amb * 0.85 + vec3(1.0, 0.97, 0.9) * diff * 0.8 + 0.18 * max(dot(n, -rd), 0.0) + through) * ao;
            vec3 hv = normalize(sun - rd);
            bool silver = kind == 3 || kind == 11 || kind == 17 || kind == 4 || kind == 27;
            float shin = silver ? 60.0 : kind == 13 ? 12.0 : 40.0;
            shade += vec3(1.0) * pow(max(dot(n, hv), 0.0), shin) * (silver ? 0.85 : kind == 13 ? 0.12 : 0.4) * ao; // (wet, shiny skin)
            if (isEye) {
                shade += vec3(1.0) * pow(max(dot(n, hv), 0.0), 150.0) * 1.5; // (a glossy eye)
                shade += vec3(1.0) * pow(max(dot(reflect(rd, n), vec3(0.0, 1.0, 0.0)), 0.0), 80.0) * 0.8;
            }
            float caust = caustics(p.xz, t) * max(n.y, 0.0) * (p.y / BoxSize.y);
            shade += vec3(0.6, 0.9, 1.0) * caust * 0.45 * ao;
            shade += vec3(0.5, 0.8, 1.0) * pow(1.0 - max(dot(n, -rd), 0.0), 3.0) * 0.22; // (light round the edges, from the water)
            if (kind == 33) { // (lit blue by its own lure)
                vec3 lure = anglerLure(sp.body.x, sp.body.y);
                shade += base * vec3(0.3, 0.75, 0.9) * 1.6 * exp(-length(pl - lure) / 0.07);
                if (part == 3) shade = vec3(0.8, 1.0, 1.0);
            }
            float sGlow = kind == 31 ? squidGlow(q, part) * (0.8 + 0.4 * pulse) : 0.0; // (a glow squid's lights, by day too)
            shade += vec3(0.2, 0.95, 0.8) * sGlow * 0.45;
            if (kind == 15 && Deep > 0.5) shade += base * (0.5 + 0.4 * pulse); // (the deep sea's jellyfish lit from within)
            float alpha = (kind == 15 ? 0.55 : 1.0) * cover; // (the jellyfish is see-through)
            if (cover > 0.99) {
                hitT = tf;
                fish = true;
                fishCol = shade;
                fishAlpha = alpha;
                glow = kind == 15 ? (Deep > 0.5 ? (base * (1.4 + 1.0 * pulse) + vec3(0.04, 0.1, 0.25) * pulse) * (part == 0 ? 1.0 : 0.6) * alpha : base * (1.3 + 0.5 * pulse) * alpha)
                     : kind == 33 && part == 3 ? vec3(0.6, 1.0, 1.0) * 2.0
                     : kind == 34 && part == 0 ? vec3(0.3, 0.65, 1.0) * photophores(q) * 1.6
                     : kind == 31 ? vec3(0.25, 1.0, 0.85) * sGlow * 1.4 : kind == 3 && part == 0 && abs(q.y - 0.12) < 0.13 ? vec3(0.1, 0.6, 0.8) : vec3(0.0);
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
        gDecoPhase = chestPhase(base);
        float rad = (type == 8 ? 0.7 : type == 13 ? 0.56 : type == 14 ? 0.25 : 0.5) * sz;
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
        if (type == 5) {
            decoCol += vec3(1.0, 0.8, 0.3) * 0.15 * pow(max(dot(reflect(rd, n), vec3(0.0, 1.0, 0.0)), 0.0), 8.0);
            vec3 cpp = chestParts(pl, t);
            if (cpp.z <= min(cpp.x, cpp.y) + 0.0005) decoCol += vec3(1.0, 0.75, 0.3) * 0.35 * min(chestOpen(t), 1.0); // (the gold shining out)
        }
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

    // ---- bubbles: a stream rising from each air stone, wobbling, growing a little as they rise ----
    vec3 stone0 = vec3(-9.0), stone1 = vec3(-9.0), chest = vec3(-9.0);
    float chestSz = 1.0;
    for (int k = 0; k < 24; k++) {
        if (k >= int(DecoCount + 0.5)) break;
        vec4 dd = decoData(k);
        int pk = int(dd.w + 0.5);
        if (pk - (pk / 16) * 16 == 14) {
            if (stone0.y < -1.0) stone0 = dd.xyz; else if (stone1.y < -1.0) stone1 = dd.xyz;
        }
        if (pk - (pk / 16) * 16 == 5 && chest.y < -1.0) {
            chest = dd.xyz;
            chestSz = float(pk / 256) / 10.0;
        }
    }
    vec3 bubbleCol = vec3(0.0);
    float bubbleT = 1.0e9;
    for (int k = 0; k < 46; k++) {
        float fk = float(k);
        vec3 src = k < 16 ? stone0 : k < 32 ? stone1 : chest;
        if (src.y < -1.0) continue;
        float top = BoxSize.y - 0.02 - src.y;
        float sp = 0.5 + 0.18 * hash11(fk + 20.0);
        float rise = mod(t * sp + hash11(fk * 3.7) * top + fk * 0.11, top);
        float wob = 0.012 + 0.035 * rise / max(top, 0.1);
        vec3 bc = src + vec3(wob * sin(t * 4.0 + fk * 1.7 + rise * 6.0), 0.05 + rise, wob * cos(t * 3.4 + fk + rise * 5.0));
        float br = (0.009 + 0.008 * hash11(fk * 9.1)) * (1.0 + 0.5 * rise / max(top, 0.1));
        if (k >= 32) { // a burst of bubbles out of the treasure chest as its lid pops open
            if (chest.y < -1.0) continue;
            float age = mod(t + chestPhase(chest), CHEST_CYCLE) - 0.12 - 0.5 * hash11(fk * 4.3);
            float top2 = BoxSize.y - 0.02 - chest.y;
            float up2 = age * (0.45 + 0.25 * hash11(fk * 1.9)) + 0.35 * age * age;
            if (age < 0.0 || up2 > top2) continue;
            vec2 off = (hash22(vec2(fk, 7.7)) - 0.5) * vec2(0.18, 0.1) * chestSz;
            float w2 = 0.01 + 0.03 * up2 / max(top2, 0.1);
            bc = chest + vec3(off.x + w2 * sin(t * 5.0 + fk * 2.3), 0.15 * chestSz + up2, off.y + w2 * cos(t * 4.3 + fk));
            br = (0.012 + 0.012 * hash11(fk * 6.1)) * (1.0 + 0.4 * up2 / max(top2, 0.1));
        }
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
        // (out through a side into air inside the box - beside an arch, an L: the same deep water and back glass as
        // the tank's outer sides, so every side of it looks alike; only up or down into such air shows plain glass)
        if (inner && !onSand && exitAxis == 1) {
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
            if (Deep > 0.5) { // (the deep sea floor: grey ooze, no sunlight playing on it)
                col = mix(vec3(0.3, 0.32, 0.36), vec3(0.42, 0.43, 0.46), fbm(p.xz * 9.0)) * pebble;
            }
        } else if (up) {
            // the underside of the surface: bright, rippling (rings spreading where the bubbles reach it)
            col = vec3(0.35, 0.75, 0.85) + vec3(0.5, 0.8, 0.9) * caustics(p.xz * 1.3, t * 1.4) * 0.6;
            col += vec3(0.6, 0.9, 1.0) * stoneRipple(p.xz, stone0, stone1, t) * 0.35;
            if (Deep > 0.5) col = vec3(0.02, 0.05, 0.1); // (no light from above down here)
        } else {
            // the back glass: deep water, seaweed swaying on it, darker lower down
            vec2 wall = exitAxis == 0 ? vec2(p.z, p.y) : vec2(p.x, p.y);
            if (Deep > 0.5) { // the deep sea: dark water on and on, faint glowing specks far off
                col = mix(vec3(0.0, 0.01, 0.03), vec3(0.01, 0.03, 0.07), p.y / BoxSize.y);
                vec2 sc = floor(wall * 9.0);
                vec2 hh = hash22(sc + 3.3);
                if (hh.x > 0.93) col += vec3(0.2, 0.6, 0.9) * smoothstep(0.12, 0.0, length(fract(wall * 9.0) - 0.5 - (hh - 0.5) * 0.6))
                                       * (0.5 + 0.5 * sin(t * 1.3 + hh.y * 40.0));
            } else {
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
    }
    if (fish && fishAlpha <= 0.99) {
        col = mix(col, fishCol, fishAlpha);
        hitT = mix(hitT, fishT, fishAlpha);
    }
    if (bubbleT < hitT) {
        col = mix(col, bubbleCol, 0.7);
        hitT = bubbleT;
    }

    col = lightGrade(col);

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
    waterCol = lightWater(clamp(entry.y / BoxSize.y, 0.0, 1.0), waterCol);
    if (Deep > 0.5) waterCol = mix(vec3(0.0, 0.008, 0.03), vec3(0.0, 0.03, 0.08), clamp(entry.y / BoxSize.y, 0.0, 1.0));
    col = mix(col, waterCol, 1.0 - exp(-travel * (Ocean > 0.5 ? 0.22 : 0.3)));
    col += vec3(0.25, 0.5, 0.55) * 0.08 * (0.5 + 0.5 * sin(entry.x * 4.0 + entry.z * 3.0 + entry.y * 2.0 + t * 1.5));

    // looking down through the top: the surface ripples catch the light
    if (faceN.y < -0.5) { // (a top face: faceN points into the tank, down)
        col += vec3(0.8, 0.95, 1.0) * caustics(entry.xz * 1.6 + vec2(t * 0.1, 0.0), t * 1.6) * 0.18;
        col += vec3(0.85, 0.95, 1.0) * stoneRipple(entry.xz, stone0, stone1, t) * 0.22;
    }

    // ---- night: the tank's lights dimmed to a deep blue; what glows still glows ----
    // (in moonlight too: what glows shines out, the plankton twinkles)
    float glowing = max(Night, abs(Light - 1.0) < 0.5 || abs(Light - 4.0) < 0.5 ? 0.8 : 0.0);
    if (glowing > 0.0) {
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
            if (h2.y > (Deep > 0.5 ? 0.86 : 0.95)) { // (the deep sea thick with it)
                vec3 cp = (cell + 0.2 + 0.6 * vec3(h1, h2.x)) / 5.0 + vec3(0.0, t * 0.04, 0.0);
                vec3 w = cp - entry;
                float along = dot(w, rd);
                float dist = length(w - rd * along);
                if (along > 0.0 && along < seen) {
                    spark += smoothstep(0.008, 0.0, dist) * (0.4 + 0.4 * sin(t * 2.5 + h1.x * 40.0));
                }
            }
        }
        col += vec3(0.25, 0.85, 1.0) * min(spark, 1.0) * 0.7 * glowing;
        if (!deco) col += glow * glowing * exp(-seen * 0.25);
        col += lureHalo * glowing;
    }

    if (abs(Light - 4.0) < 0.5 && Deep < 0.5) { // the jellies (glowing: not dimmed by the night)
        vec4 jl = jellies(ro - BoxMin, rd, tEnter, hitT, t);
        col = col * (1.0 - jl.a) + jl.rgb;
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
