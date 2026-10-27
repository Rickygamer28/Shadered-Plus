#version 150

// Shadered+'s koi pond (our own code). Drawn on a box over the water (up above the rim, so the ducks and frogs on it
// are never cut off by the stone; the depth of what is seen is written, so the rim still hides what it should).
// The view meets the rippling surface (lily pads float on it; the sky shines
// off it at low angles), bends into the water and goes down past koi swimming at three depths to the pebbles or the
// stone sides, light rippling on them, the fish and pads casting soft shadows. Each block has its own koi (maybe)
// and lily pads, swimming in loops in it; joined ponds are one water. x, z: world blocks; y: up in the block.

in vec3 wray;
in vec2 uv;
in vec4 vColor;
in vec3 vpos;
flat in ivec2 vShape;

uniform sampler2D Sampler0; // (the ponds' shapes: KoiPondData)

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

uniform float PondTime;
uniform float Daylight;
uniform float Rain;    // how hard it's raining on the pond (0 none): rings on the water, the koi swimming lower
uniform vec3 MoonDir;  // where the moon is (its reflection on the water at night)

out vec4 fragColor;

const float SURF = 14.5 / 16.0;
const float FLOOR = 1.0 / 16.0;
const float TOP = 2.5; // (the top of the box drawn over the water, well above the rim: ducks flying in and off fit in it)
vec3 gHit; // (where a duck or frog was hit)
vec3 gStart; // (where the ray starts: on the box, or at the eye inside it)
const vec3 SUN = vec3(0.35, 1.0, 0.2);

vec2 hash22(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

float noise2(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash22(i).x, hash22(i + vec2(1, 0)).x, f.x), mix(hash22(i + vec2(0, 1)).x, hash22(i + vec2(1, 1)).x, f.x), f.y);
}

float layerY(int L) {
    return SURF - (L == 0 ? 3.0 : L == 1 ? 5.5 : 8.0) / 16.0 - 0.06 * Rain; // (lower in the rain)
}

// rain on the water: rings spreading from where drops land (each little patch of water its own drops, coming and
// going); returns the slope the rings give the surface (xy) and how bright their crests are (z)
vec3 rainRings(vec2 x, float t) {
    vec3 acc = vec3(0.0);
    if (Rain <= 0.0) return acc; // (no rain: no rings - and nothing to work out)
    vec2 cell = floor(x * 5.0);
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 c = cell + vec2(i, j);
            vec2 h = hash22(c * 1.7 + 3.1);
            float period = 0.9 + 0.6 * h.x;
            float ph = t / period + h.y;
            float n = floor(ph), age = ph - n;
            if (hash22(c + n * 0.37).x > 0.35 + 0.6 * Rain) continue; // (fewer drops in light rain)
            vec2 centre = (c + 0.15 + 0.7 * hash22(c * 3.3 + n * 1.13)) / 5.0;
            vec2 d = x - centre;
            float r = length(d);
            float rad = 0.015 + 0.11 * sqrt(age);
            float w = 0.012 + 0.01 * age;
            float ring = exp(-pow((r - rad) / w, 2.0)) * (1.0 - age) * (1.0 - age);
            float slope = -2.0 * (r - rad) / (w * w) * ring;
            acc.xy += (r > 1.0e-4 ? d / r : vec2(0.0)) * slope * 0.0025;
            acc.z += ring;
        }
    }
    return acc * Rain;
}

// the pond this block is part of (the rectangle round it, in world blocks), the rectangles it is made of (any shape
// of pond: the koi each keep to one of them; the first is the biggest), its size in blocks, which of the eight blocks
// round this one are pond too, and how many koi live in it: set in main()
vec2 pondLo, pondHi;
vec4 pondRect[8];
int pondRects;
float pondBlocks;
int pondRound;
vec2 cell0;
int pondFish;
int pondStones; // (stepping stones: on this block (bit 0), on the eight round it (bits 1-8, as pondRound))

// is this block (x, z) part of the pond?
bool isPond(vec2 cell) {
    vec2 d = cell - cell0;
    if (d == vec2(0.0)) return true;
    if (max(abs(d.x), abs(d.y)) < 1.5) {
        int bit = d.y == 0.0 ? (d.x < 0.0 ? 0 : 1) : d.x == 0.0 ? (d.y < 0.0 ? 2 : 3) : d.y < 0.0 ? (d.x < 0.0 ? 4 : 5) : (d.x < 0.0 ? 6 : 7);
        return (pondRound & (1 << bit)) != 0;
    }
    for (int r = 0; r < 8; r++) {
        if (r >= pondRects) break;
        if (all(greaterThanEqual(cell, pondRect[r].xy)) && all(lessThan(cell, pondRect[r].zw))) return true;
    }
    return false;
}

// is there a stepping stone on this block (this one or one next to it)? Frogs sit on it.
bool stoneAt(vec2 cell) {
    vec2 d = cell - cell0;
    if (d == vec2(0.0)) return (pondStones & 1) != 0;
    if (max(abs(d.x), abs(d.y)) < 1.5) {
        int bit = d.y == 0.0 ? (d.x < 0.0 ? 0 : 1) : d.x == 0.0 ? (d.y < 0.0 ? 2 : 3) : d.y < 0.0 ? (d.x < 0.0 ? 4 : 5) : (d.x < 0.0 ? 6 : 7);
        return (pondStones & (2 << bit)) != 0;
    }
    return false;
}

// koi i of the pond: (its depth layer, its kind 0..4, its size, a seed)
vec4 koiOf(int i) {
    vec2 h = hash22(pondLo * 1.31 + pondHi * 0.73 + float(i) * 7.17), k = hash22(pondLo * 0.37 + pondHi * 1.9 + float(i) * 3.91 + 11.1);
    return vec4(floor(h.x * 3.0), floor(k.x * 5.0), 0.72 + 0.4 * k.y, h.y);
}

// where koi i is and which way it faces: wandering all over the pond in long, lazy curves (never up into the rims)
void koiPath(int i, float size, float t, out vec2 pos, out vec2 dir) {
    vec2 h = hash22(pondLo * 2.1 + pondHi * 0.3 + float(i) * 5.37 + 21.3), h2 = hash22(pondLo * 0.9 + float(i) * 9.11 + 4.4);
    float reach = 0.42 * size;                       // (head to tail fin, from where it is)
    // which part of the pond it keeps to (the bigger the part, the more koi in it)
    float pick = hash22(pondLo * 0.53 + float(i) * 2.71 + 8.8).x * pondBlocks;
    vec4 part = pondRect[0];
    for (int r = 0; r < 8; r++) {
        if (r >= pondRects) break;
        vec2 rs = pondRect[r].zw - pondRect[r].xy;
        part = pondRect[r];
        pick -= rs.x * rs.y;
        if (pick < 0.0) break;
    }
    vec2 lo = part.xy + 0.125 + reach, hi = part.zw - 0.125 - reach;
    vec2 mid = (part.xy + part.zw) * 0.5;
    lo = min(lo, mid);
    hi = max(hi, mid);
    vec2 hs = (hi - lo) * 0.5;
    vec2 f = (0.12 + 0.05 * h) / max(hs, vec2(0.6)); // (about the same speed in a big pond as a small one)
    vec2 ph = h2 * 6.2832;
    vec2 u = vec2(0.62 * sin(t * f.x + ph.x) + 0.38 * sin(t * f.x * 1.73 + ph.y * 1.3),
                  0.62 * sin(t * f.y * 1.11 + ph.y) + 0.38 * sin(t * f.y * 1.91 + ph.x * 0.7));
    vec2 v = vec2(0.62 * f.x * cos(t * f.x + ph.x) + 0.38 * f.x * 1.73 * cos(t * f.x * 1.73 + ph.y * 1.3),
                  0.62 * f.y * 1.11 * cos(t * f.y * 1.11 + ph.y) + 0.38 * f.y * 1.91 * cos(t * f.y * 1.91 + ph.x * 0.7));
    pos = lo + hs + hs * u;
    v *= hs;
    dir = length(v) > 1e-5 ? normalize(v) : vec2(1.0, 0.0);
}

// every koi of the pond this moment (its layer, kind, size, seed; where it is and which way it faces): worked out once
// for this pixel, then looked up by the shadows and the koi themselves
vec4 gKoiInfo[24];
vec4 gKoiAt[24];

void placeKoi(float t) {
    for (int i = 0; i < 24; i++) {
        if (i >= pondFish) break;
        vec4 info = koiOf(i);
        vec2 pos, dir;
        koiPath(i, info.z, t, pos, dir);
        gKoiInfo[i] = info;
        gKoiAt[i] = vec4(pos, dir);
    }
}

// a koi at q (world x, z): (colour, how much it covers); body = only its body (for shadows)
vec4 koiAt(vec2 q, int i, float t, bool body) {
    vec4 info = gKoiInfo[i];
    float size = info.z;
    vec2 pos = gKoiAt[i].xy, dir = gKoiAt[i].zw;
    vec2 d = q - pos;
    if (dot(d, d) > 0.09 * size * size) {
        return vec4(0.0);
    }
    float L = 0.34 * size;
    float s = dot(d, dir) / L;               // (head +0.5 .. tail -0.5)
    float side = dot(d, vec2(-dir.y, dir.x)) / L;
    side += 0.075 * sin(s * 7.0 - t * 6.0) * (0.55 - s); // (the tail sweeps more than the head)
    // the body: a rounded head, widest a third of the way back, tapering to a slim tail stalk
    float bw = s > 0.12 ? 0.17 * sqrt(max(0.0, 1.0 - pow((s - 0.12) / 0.44, 2.0)))
                        : mix(0.17, 0.035, pow(clamp((0.12 - s) / 0.58, 0.0, 1.0), 1.3));
    vec2 h = vec2(info.w, fract(info.w * 13.7));
    vec2 lp = vec2(s, side);
    if (abs(side) < bw && s > -0.46 && s < 0.56 && bw > 0.0) {
        if (body) return vec4(0.0, 0.0, 0.0, 1.0);
        float kind = info.y;
        float n = noise2(lp * 9.0 + h * 40.0), n2 = noise2(lp * 17.0 + h * 77.0);
        vec3 white = vec3(0.96, 0.94, 0.9), red = vec3(0.86, 0.2, 0.08), black = vec3(0.06, 0.06, 0.07);
        vec3 col;
        if (kind < 0.5) col = n > 0.5 ? red : white;                                        // kohaku
        else if (kind < 1.5) col = mix(vec3(1.0, 0.72, 0.22), vec3(1.0, 0.9, 0.55), n2 * 0.6); // ogon (gold)
        else if (kind < 2.5) col = n2 > 0.74 ? black : (n > 0.52 ? red : white);              // sanke
        else if (kind < 3.5) col = n > 0.45 ? (n2 > 0.55 ? white : red) : black;              // showa
        else col = s > 0.3 ? white : vec3(1.0, 0.46, 0.1);                                    // orange, white head
        float r = 1.0 - pow(side / bw, 2.0);
        col *= 0.62 + 0.38 * sqrt(max(r, 0.0));
        col += vec3(0.18) * smoothstep(0.25, 0.0, abs(side) / bw) * smoothstep(-0.3, 0.3, s); // (a shine along the back)
        if (length(vec2(s - 0.42, abs(side) - 0.09)) < 0.025) col = vec3(0.03);               // (eyes)
        return vec4(col, 1.0);
    }
    if (body) return vec4(0.0);
    // the tail fin: long and flowing
    if (s < -0.38 && s > -0.85) {
        float u = (-0.38 - s) / 0.47;
        float tside = side + 0.08 * sin(t * 6.0 - s * 6.0) * u;
        float fw = 0.05 + 0.2 * u * (1.0 - 0.35 * smoothstep(0.75, 1.0, u) * (1.0 - abs(tside) * 4.0));
        if (abs(tside) < fw) {
            return vec4(mix(vec3(1.0, 0.75, 0.6), vec3(1.0), 0.5), 0.55 * (1.0 - 0.6 * u));
        }
    }
    // the side fins
    vec2 f = vec2(s - 0.18, abs(side) - bw - 0.05);
    f = mat2(0.8, 0.6, -0.6, 0.8) * f;
    if (length(f / vec2(0.06, 0.035)) < 1.0) {
        return vec4(vec3(1.0, 0.9, 0.85), 0.5);
    }
    return vec4(0.0);
}

// the koi at q in a depth layer
vec4 koiLayer(vec2 q, int L, float t, bool body) {
    for (int i = 0; i < 24; i++) {
        if (i >= pondFish) break;
        if (int(gKoiInfo[i].x) != L) continue;
        vec4 k = koiAt(q, i, t, body);
        if (k.a > 0.0) return k;
    }
    return vec4(0.0);
}

// a whole-number hash (the same as KoiPondBlock's, so the game knows where the frogs are, for their croaks)
uint hashu(uvec3 v) {
    v = v * 1664525u + 1013904223u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    v ^= v >> 16u;
    v.x += v.y * v.z; v.y += v.z * v.x; v.z += v.x * v.y;
    return v.x ^ v.y ^ v.z;
}

float u01(uint x) {
    return float(x & 65535u) / 65535.0;
}

uvec3 padKey(vec2 cell, int k) {
    return uvec3(uint(int(cell.x)), uint(int(cell.y)), uint(k));
}

// is a frog on this pad now: its kind, size and which way it faces, and its hop (1 sitting; 0..1 hopping on from the
// water; 0..-1 hopping off). At most one frog to a block (on its first pad); each stays a minute or so, then hops off,
// and a while later another may come - never two at once.
float gFrogTe; // (how far into its visit the last frog asked about is: seconds)
bool frogOn(vec2 cell, int k, float t, bool stone, out vec3 info, out float hop) {
    info = vec3(0.0);
    hop = 0.0;
    if (k != 0) {
        return false;
    }
    uvec3 key = padKey(cell, k);
    vec2 hf = vec2(u01(hashu(key + uvec3(0u, 0u, 200u))), u01(hashu(key + uvec3(0u, 0u, 300u))));
    float phase = t / 75.0 + hf.x * 13.0;
    uint epoch = uint(int(floor(phase)));
    float te = fract(phase) * 75.0;
    gFrogTe = te;
    vec2 he = vec2(u01(hashu(key + uvec3(0u, 0u, 200000u + epoch))), u01(hashu(key + uvec3(0u, 0u, 500000u + epoch))));
    info = vec3(floor(he.y * 3.0), 0.62 + 0.16 * hf.x, he.x * 6.2832); // (z: which way it faces, on its pad)
    hop = te < 6.0 ? te - 5.0 : te > 70.0 ? -(te - 70.0) : 1.0;
    return (stone || hf.y < 0.55) && he.x < 0.6 && te > 5.0 && te < 71.0; // (a stepping stone: frogs like it more)
}

// lily pad k of a cell: is there one, where, how big, and which way it is turned (a frog sitting on it turns with it)
bool padParams(vec2 cell, int k, float t, out vec2 c, out float r, out vec2 h, out vec2 h2, out float ang) {
    uvec3 key = padKey(cell, k);
    h = vec2(u01(hashu(key)), u01(hashu(key + uvec3(0u, 0u, 100u))));
    h2 = hash22(cell * 3.1 + float(k) * 4.7);
    r = 0.08 + 0.07 * h.y;
    float m = 0.165 + r; // (clear of the stone rims round the block, whichever sides they're on)
    c = cell + m + (1.0 - 2.0 * m) * h2 + 0.02 * vec2(sin(t * 0.07 + h.y * 9.0), cos(t * 0.05 + h.x * 7.0));
    ang = h2.x * 6.28 + t * 0.02;
    vec3 info;
    float hop;
    if (r >= 0.09 && !stoneAt(cell) && frogOn(cell, k, t, false, info, hop)) {
        float sit = clamp(hop >= 0.0 ? hop : 1.0 + hop, 0.0, 1.0); // (eases in as it lands, out as it leaves)
        ang += 0.5 * sin(t * 0.3 + h2.y * 9.0) * sit * sit; // (the frog shifting round, the pad turning under it)
    }
    return h.x >= 0.45;
}

float smin(float a, float b, float k) {
    float h = max(k - abs(a - b), 0.0) / k;
    return min(a, b) - h * h * k * 0.25;
}

float sdEllipsoid(vec3 p, vec3 r) {
    float k0 = length(p / r);
    float k1 = length(p / (r * r));
    return k0 * (k0 - 1.0) / max(k1, 1e-6);
}

// a frog (px, from its feet on the pad; z forward): x = distance, y = what (0 skin, 1 eye)
vec2 frogSDF(vec3 p, float t) {
    vec3 q = vec3(abs(p.x), p.y, p.z);
    float breathe = 1.0 + 0.06 * sin(t * 3.1);
    float d = sdEllipsoid(p - vec3(0.0, 1.25, -0.3), vec3(1.5, 1.0 * breathe, 2.0));
    d = smin(d, sdEllipsoid(p - vec3(0.0, 1.45, 1.45), vec3(1.35, 0.78, 1.1)), 0.6);
    d = smin(d, sdEllipsoid(q - vec3(1.45, 0.6, -0.9), vec3(0.62, 0.55, 1.45)), 0.4);  // (back legs folded)
    d = smin(d, sdEllipsoid(q - vec3(0.95, 0.35, 1.35), vec3(0.36, 0.38, 0.62)), 0.3); // (front legs)
    float eye = length(q - vec3(0.72, 2.1, 1.7)) - 0.46;
    return eye < d ? vec2(eye, 1.0) : vec2(d, 0.0);
}

// a frog in front of the surface point (the ray passes over it first): (colour, 0/1)
vec4 frogHit(vec3 p0, vec3 rd, float t, float light) {
    if (rd.y > -0.08) {
        return vec4(0.0);
    }
    float up = 0.42;
    vec3 pTop = p0 - rd * (up / -rd.y);
    if (pTop.y > gStart.y) pTop = gStart; // (nothing behind the eye)
    vec2 c0 = floor(p0.xz);
    vec2 sLo = min(pTop.xz, p0.xz) - 0.33, sHi = max(pTop.xz, p0.xz) + 0.33; // (where a frog could be, for this ray)
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 cell = c0 + vec2(i, j);
            if (any(greaterThan(cell, sHi)) || any(lessThan(cell + 1.0, sLo))) continue; // (too far from the ray)
            if (!isPond(cell)) continue; // (only the pond's own pads)
            for (int k = 0; k < 1; k++) { // (a frog only ever sits on a block's first pad)
                vec2 c, h, h2;
                float r;
                vec3 info;
                float hop;
                float pang;
                float seat = SURF;
                if (stoneAt(cell)) { // (on the stepping stone there, in the middle of the block, not a lily pad)
                    if (k != 0 || !frogOn(cell, 0, t, true, info, hop)) continue;
                    c = cell + 0.5;
                    h2 = hash22(cell * 3.1);
                    pang = h2.x * 6.28 + t * 0.02;
                    seat = 1.0 + 2.0 / 16.0;
                } else if (!frogOn(cell, k, t, false, info, hop) || !padParams(cell, k, t, c, r, h, h2, pang) || r < 0.09) continue;
                // hopping on: a leap up out of the water onto the pad; hopping off: a leap forward into the water
                float u = hop > 0.0 ? 1.0 - hop : -hop;
                vec3 off = hop >= 1.0 ? vec3(0.0) : hop > 0.0 ? vec3(0.0, 8.0 * u * (1.0 - u) - 3.0 * u * u, -2.5 * u)
                                                                 : vec3(0.0, 8.0 * u * (1.0 - u) - 3.0 * u * u, 2.5 * u);
                // close to the ray's way down?
                vec2 ab = p0.xz - pTop.xz;
                float w = clamp(dot(c - pTop.xz, ab) / max(dot(ab, ab), 1e-6), 0.0, 1.0);
                if (length(pTop.xz + ab * w - c) > 0.32) continue;
                // which way it faces: out to the open water (the middle of its block - never the stone round it), so
                // it hops off into the water and comes from it: leaping on facing the pad, it turns round once there
                float te = gFrogTe;
                vec2 toMid = cell + 0.5 - c;
                float aIn = dot(toMid, toMid) > 1e-4 ? -atan(toMid.x, toMid.y) : info.z;
                float sway = pang - (h2.x * 6.28 + t * 0.02); // (the frog shifting round, its pad turning with it)
                float face = te < 6.0 ? aIn + 3.1416 : aIn + 3.1416 * (1.0 - smoothstep(6.0, 7.2, te)) + sway;
                float ca = cos(face), sa = sin(face);
                float sc = 16.0 / info.y;
                vec3 ro = vec3(pTop.x - c.x, pTop.y - seat, pTop.z - c.y) * sc;
                vec3 rr = rd;
                ro.xz = mat2(ca, -sa, sa, ca) * ro.xz;
                rr.xz = mat2(ca, -sa, sa, ca) * rr.xz;
                float tm = 0.0, tEnd = length(p0 - pTop) * sc;
                for (int n = 0; n < 40; n++) {
                    vec3 q = ro + rr * tm - off;
                    vec2 f = frogSDF(q, t);
                    if (f.x < 0.02) {
                        vec2 e = vec2(0.03, 0.0);
                        vec3 nn = normalize(vec3(frogSDF(q + e.xyy, t).x - frogSDF(q - e.xyy, t).x, frogSDF(q + e.yxy, t).x - frogSDF(q - e.yxy, t).x,
                                                 frogSDF(q + e.yyx, t).x - frogSDF(q - e.yyx, t).x));
                        vec3 sun = normalize(SUN);
                        sun.xz = mat2(ca, -sa, sa, ca) * sun.xz;
                        float lam = 0.45 + 0.55 * max(dot(nn, sun), 0.0);
                        vec3 col;
                        if (f.y > 0.5) {
                            col = vec3(0.05, 0.05, 0.06) + vec3(0.9) * pow(max(dot(reflect(rr, nn), sun), 0.0), 30.0);
                        } else {
                            vec3 back = info.x < 0.5 ? vec3(0.78, 0.47, 0.24) : info.x < 1.5 ? vec3(0.4, 0.62, 0.3) : vec3(0.86, 0.83, 0.75);
                            vec3 belly = mix(back, vec3(0.95, 0.9, 0.78), 0.55);
                            col = mix(belly, back, smoothstep(0.6, 1.4, q.y));
                            if (info.x > 1.5 && noise2(q.xz * 1.6) > 0.68) col *= 0.7; // (spots)
                            col *= lam;
                        }
                        gHit = pTop + rd * (tm / sc);
                        return vec4(col * light, 1.0);
                    }
                    tm += max(f.x * 0.9, 0.05);
                    if (tm > tEnd) break;
                }
            }
        }
    }
    return vec4(0.0);
}

// a lily pad at q on the surface: (colour, 0/1)
vec4 padAt(vec2 q, float t) {
    vec2 c0 = floor(q);
    // (a pad lies wholly inside its own block - its middle is kept further from the block's edges than it is wide - so
    // only this block's pads can be here)
    for (int j = 0; j <= 0; j++) {
        for (int i = 0; i <= 0; i++) {
            vec2 cell = c0 + vec2(i, j);
            for (int k = 0; k < 2; k++) {
                vec2 c, h, h2;
                float r, pang;
                if (!padParams(cell, k, t, c, r, h, h2, pang)) continue;
                vec2 d = q - c;
                float dist = length(d);
                if (dist > r) continue;
                float a = atan(d.y, d.x) - pang;
                float notch = abs(mod(a + 3.1416, 6.2832) - 3.1416);
                if (notch < 0.32 * (1.0 - 0.3 * dist / r) && dist > r * 0.08) continue;
                vec3 col = mix(vec3(0.14, 0.4, 0.12), vec3(0.3, 0.58, 0.2), noise2(d * 60.0 + h * 9.0) * 0.6 + 0.2);
                col *= 0.85 + 0.15 * sin(a * 12.0); // (veins)
                col *= 0.8 + 0.2 * smoothstep(r, r * 0.85, dist);
                vec3 info;
                float hop;
                if (r >= 0.09 && !stoneAt(cell) && frogOn(cell, k, t, false, info, hop) && hop >= 1.0) {
                    col *= 0.55 + 0.45 * smoothstep(0.06 * info.y, 0.16 * info.y, dist); // (the frog's shadow)
                } else if (h.y > 0.72 && dist < r * 0.32) { // a water-lily flower
                    float petals = 0.5 + 0.5 * cos(atan(d.y, d.x) * 8.0);
                    col = mix(vec3(1.0, 0.55, 0.75), vec3(1.0, 0.92, 0.95), petals * (1.0 - dist / (r * 0.32)));
                    if (dist < r * 0.07) col = vec3(1.0, 0.85, 0.3);
                }
                return vec4(col, 1.0);
            }
        }
    }
    return vec4(0.0);
}

// ---- visitors: now and then a duck or two drops in for a while, or a turtle swims through ----

// a wandering path over the pond (like the koi's), its own speed
void wander(float seed, float reach, float speed, float t, out vec2 pos, out vec2 dir) {
    vec2 h = hash22(pondLo * 1.7 + pondHi * 0.9 + seed), h2 = hash22(pondLo * 0.3 + seed * 3.1 + 2.2);
    vec2 lo = pondRect[0].xy + 0.125 + reach, hi = pondRect[0].zw - 0.125 - reach; // (the pond's biggest part)
    vec2 mid = (pondRect[0].xy + pondRect[0].zw) * 0.5;
    lo = min(lo, mid);
    hi = max(hi, mid);
    vec2 hs = (hi - lo) * 0.5;
    vec2 f = (speed + 0.03 * h) / max(hs, vec2(0.6));
    vec2 ph = h2 * 6.2832;
    vec2 u = vec2(0.62 * sin(t * f.x + ph.x) + 0.38 * sin(t * f.x * 1.61 + ph.y), 0.62 * sin(t * f.y * 1.13 + ph.y) + 0.38 * sin(t * f.y * 1.87 + ph.x));
    vec2 v = vec2(0.62 * f.x * cos(t * f.x + ph.x) + 0.38 * f.x * 1.61 * cos(t * f.x * 1.61 + ph.y),
                  0.62 * f.y * 1.13 * cos(t * f.y * 1.13 + ph.y) + 0.38 * f.y * 1.87 * cos(t * f.y * 1.87 + ph.x)) * hs;
    pos = lo + hs + hs * u;
    dir = length(v) > 1e-5 ? normalize(v) : vec2(1.0, 0.0);
}

// duck d (0 or 1): is it here now; how high above the water and how far ahead of its swimming path (flying in or
// off); its wings (spread 0 folded .. 1 open, how hard they flap); how big a splash there is; and its kind
bool duckHere(int d, float t, out float height, out float ahead, out float spread, out float flap, out float splash, out float kind) {
    float ph = t / 140.0 + hash22(pondLo + pondHi * 1.3).x * 7.0;
    float e = floor(ph), te = fract(ph) * 140.0;
    vec2 h = hash22(pondLo * 0.71 + pondHi * 0.37 + e * 3.3 + float(d) * 17.0);
    vec2 size = pondRect[0].zw - pondRect[0].xy;
    bool visit = hash22(pondLo * 0.71 + pondHi * 0.37 + e * 3.3).x < 0.55 && (d == 0 || size.x * size.y >= 8.0);
    kind = step(0.5, h.y);
    float tin = 8.0 + float(d) * 1.5, tout = 108.0 - float(d) * 2.0;
    height = 0.0; ahead = 0.0; spread = 0.0; flap = 0.0; splash = 0.0;
    if (te < tin + 3.0) { // flying in: down out of the sky, flapping, then a glide, wings folding as it touches down
        float u = clamp((te - tin) / 3.0, 0.0, 1.0);
        height = (1.0 - u) * (1.0 - u) * 1.7;
        ahead = -(1.0 - u) * 0.9;
        spread = 1.0 - smoothstep(0.82, 1.0, u);
        flap = u < 0.55 ? 1.0 : 0.15;
    } else if (te > tout - 3.0) { // flying off: a run across the water flapping hard, then up and away
        float u = clamp((te - tout + 3.0) / 3.0, 0.0, 1.0);
        float rise = max(u - 0.4, 0.0) / 0.6;
        height = 0.02 * step(0.05, u) + rise * rise * 1.9;
        ahead = u * u * 1.4;
        spread = smoothstep(0.0, 0.12, u);
        flap = 1.0;
    }
    float since = te - (tin + 2.7); // (the splash as it lands)
    splash = since > 0.0 ? exp(-since * 1.2) : 0.0;
    return visit && te > tin && te < tout;
}

// tuck: asleep, its head turned back and laid on its back, its bill tucked in its feathers
float duckSDF(vec3 p, float spread, float flapAngle, float tuck, out int mat) {
    vec3 q = vec3(abs(p.x), p.y, p.z);
    float d = sdEllipsoid(p - vec3(0.0, 0.7, 0.0), vec3(1.7, 1.25, 2.7));
    d = smin(d, sdEllipsoid(p - vec3(0.0, 1.5, -2.3), vec3(0.9, 0.6, 0.9)), 0.6);
    d = smin(d, sdEllipsoid(p - mix(vec3(0.0, 2.0, 1.6), vec3(0.25, 1.7, 0.7), tuck), mix(vec3(0.75, 1.3, 0.75), vec3(0.8, 0.8, 0.8), tuck)), 0.5);
    d = smin(d, length(p - mix(vec3(0.0, 3.1, 2.0), vec3(0.6, 2.15, -0.1), tuck)) - 1.0, 0.4);
    mat = 0;
    // the wings: folded along its sides, or spread wide and beating
    vec3 w = q - mix(vec3(1.35, 1.35, -0.3), vec3(1.1, 1.65, 0.2), spread);
    float a = flapAngle * spread;
    w.xy = mat2(cos(a), -sin(a), sin(a), cos(a)) * w.xy;
    float wing = sdEllipsoid(w - vec3(mix(0.1, 2.3, spread), 0.0, mix(0.0, -0.2, spread)),
                             vec3(mix(0.32, 2.5, spread), mix(0.62, 0.17, spread), mix(1.9, 1.15, spread)));
    if (wing < d) { d = min(d, wing); mat = 3; }
    float bill = sdEllipsoid(p - mix(vec3(0.0, 2.85, 3.15), vec3(0.95, 1.95, -1.05), tuck), vec3(0.42, 0.2, 0.8));
    if (bill < d) { d = bill; mat = 1; }
    if (tuck < 0.5) {
        float eye = length(q - vec3(0.72, 3.3, 2.45)) - 0.17;
        if (eye < d) { d = eye; mat = 2; }
    }
    return d;
}

// a duckling (in the same units as a duck, drawn smaller): a round fluffy body, a big head, a little bill
float ducklingSDF(vec3 p, float tuck, out int mat) {
    vec3 q = vec3(abs(p.x), p.y, p.z);
    float d = sdEllipsoid(p - vec3(0.0, 0.9, -0.1), vec3(1.5, 1.3, 1.9));
    d = smin(d, sdEllipsoid(p - vec3(0.0, 1.2, -1.9), vec3(0.5, 0.45, 0.55)), 0.4); // (a little tail tuft)
    vec3 hc = mix(vec3(0.0, 2.35, 1.45), vec3(0.45, 1.75, 0.35), tuck);
    d = smin(d, length(p - hc) - 1.15, 0.6);
    mat = 0;
    float bill = sdEllipsoid(p - (hc + mix(vec3(0.0, -0.2, 1.15), vec3(0.6, -0.15, -0.75), tuck)), vec3(0.42, 0.2, 0.5));
    if (bill < d) { d = bill; mat = 1; }
    if (tuck < 0.5) {
        float eye = length(q - vec3(0.8, 2.55, 1.95)) - 0.2;
        if (eye < d) { d = eye; mat = 2; }
    }
    return d;
}

// ---- the duck family: in a pond big enough, a mother duck with her ducklings, swimming round in a line after her by
// day, at night asleep together at the edge of the pond, heads tucked ----

int familyKids() {
    vec2 size = pondRect[0].zw - pondRect[0].xy;
    return size.x * size.y >= 12.0 ? 4 : size.x * size.y >= 6.0 ? 3 : 0;
}

// the family's own clock: she swims about 18 s, slows to a stop and rests about 10 s, then sets off again
const float FAM_CYCLE = 32.0, FAM_RUN = 21.55;
float famClock(float t) {
    float cyc = floor(t / FAM_CYCLE), c = t - cyc * FAM_CYCLE;
    float s = min(c, 18.0);
    if (c > 18.0) { float u = min(c, 21.0) - 18.0; s += u - 0.95 * u * u / 6.0; }
    if (c > 21.0) s += (min(c, 29.0) - 21.0) * 0.05;
    if (c > 29.0) { float u = c - 29.0; s += 0.05 * u + 0.95 * u * u / 6.0; }
    return cyc * FAM_RUN + s;
}

// how much she's resting now (0 swimming .. 1 stopped): the ducklings scatter round her and play
float famRest(float t) {
    float c = mod(t, FAM_CYCLE);
    return smoothstep(19.5, 22.0, c) * (1.0 - smoothstep(28.5, 31.0, c));
}

// every duck on the pond now (the two visitors, then the family): where (xy), facing (zw); dab: tipped up head
// down, dabbling (0..1); the ducks keeping out of each other's way
vec4 gDuck[7];
float gDab[7];
bool gHere[7];

void placeDucks(float t, bool family) {
    int kids = family ? familyKids() : 0;
    float awake = smoothstep(0.1, 0.35, Daylight);
    float rest = famRest(t) * awake;
    float seed = hash22(pondLo * 0.77 + pondHi * 0.21).x * 50.0;
    // where the family is on the way she has swum: the ducklings a set distance apart behind her along it (however
    // slowly she goes: never bunching up), found by going back along her way
    float tau = famClock(t);
    vec2 follow[5];
    vec2 followDir[5];
    vec2 mp, md;
    wander(70.0, 0.3, 0.05, tau, mp, md);
    follow[0] = mp; followDir[0] = md;
    for (int f = 1; f < 5; f++) { follow[f] = mp; followDir[f] = md; }
    {
        vec2 prev = mp;
        float walked = 0.0;
        int next = 1;
        for (int s = 1; s <= 48; s++) {
            if (next > kids) break;
            vec2 p, pd;
            wander(70.0, 0.3, 0.05, tau - float(s) * 0.8, p, pd);
            float seg = length(p - prev);
            for (int k = 0; k < 4; k++) {
                if (next > kids) break;
                float want = 0.19 + float(next) * 0.17; // (the first a little further back from her)
                if (walked + seg < want) break;
                float u = seg > 1.0e-5 ? (want - walked) / seg : 0.0;
                follow[next] = mix(prev, p, u);
                followDir[next] = normalize(prev - p + pd * 1.0e-3);
                next++;
            }
            walked += seg;
            prev = p;
        }
        vec2 back = -normalize(md + vec2(1.0e-4, 0.0)); // (not far enough found: carried on straight behind)
        for (int f = 1; f < 5; f++) if (f >= next) { follow[f] = prev + back * max(0.19 + float(f) * 0.17 - walked, 0.0); followDir[f] = md; }
    }
    for (int dk = 0; dk < 7; dk++) {
        gDab[dk] = 0.0;
        gHere[dk] = false;
        vec2 pos, dir;
        if (dk < 2) {
            float height, ahead, spread, flap, splash, kind;
            gHere[dk] = duckHere(dk, t, height, ahead, spread, flap, splash, kind);
            wander(40.0 + float(dk) * 11.0, 0.3, 0.07, t, pos, dir);
            pos += dir * ahead;
        } else {
            int f = dk - 2;
            gHere[dk] = family && f <= kids;
            if (!gHere[dk]) { gDuck[dk] = vec4(0.0, 0.0, 1.0, 0.0); continue; }
            vec2 wp = follow[f], wd = followDir[f];
            if (f > 0) { // playing round her while she rests: paddling round her, evenly spaced, now and then tipping up to dabble
                float fk = float(f);
                float ang = fk / float(max(kids, 1)) * 6.2832 + seed + t * 0.3;
                float rad = 0.28 + 0.025 * sin(t * 0.7 + fk * 1.9);
                vec2 pp = mp + vec2(cos(ang), sin(ang)) * rad;
                vec2 pd = normalize(vec2(-sin(ang), cos(ang)) + 0.25 * vec2(cos(t * 1.3 + fk), sin(t * 1.1 + fk)));
                wp = mix(wp, pp, rest);
                wd = normalize(mix(wd, pd, rest) + vec2(1.0e-4, 0.0));
                gDab[dk] = rest * pow(max(sin(t * 0.8 + fk * 2.1 + seed), 0.0), 10.0);
            } else {
                gDab[dk] = rest * pow(max(sin(t * 0.45 + seed + 1.0), 0.0), 14.0) * 0.8;
            }
            // asleep at night: huddled in a corner of the pond, heads tucked
            vec2 corner = pondRect[0].xy + 0.125;
            vec2 off = f == 0 ? vec2(0.24, 0.24) : f == 1 ? vec2(0.12, 0.4) : f == 2 ? vec2(0.42, 0.12) : f == 3 ? vec2(0.4, 0.38) : vec2(0.13, 0.14);
            float sa = float(f) * 2.1 + 0.6;
            pos = mix(corner + off, wp, awake);
            dir = normalize(mix(vec2(cos(sa), sin(sa)), wd, awake) + vec2(1.0e-4, 0.0));
        }
        gDuck[dk] = vec4(pos, dir);
    }
    // a visiting duck and the family keep out of each other's way: gently (the family moving aside for it)
    for (int i = 0; i < 2; i++) {
        if (!gHere[i]) continue;
        for (int j = 2; j < 7; j++) {
            if (!gHere[j]) continue;
            vec2 dd = gDuck[j].xy - gDuck[i].xy;
            float dl = length(dd);
            float m = j == 2 ? 0.32 : 0.25;
            if (dl >= m || dl < 1.0e-4) continue;
            float push = (m - dl) * smoothstep(m, m * 0.4, dl);
            gDuck[j].xy += dd / dl * push * 0.8;
            gDuck[i].xy -= dd / dl * push * 0.2;
        }
    }
}

// a duck in front of the surface point: (colour, 0/1); ripple: rings on the water round it (and its landing splash)
vec4 duckHit(vec3 p0, vec3 rd, float t, float light, out float ripple) {
    ripple = 0.0;
    vec4 res = vec4(0.0);
    int kids = familyKids();
    // (the family: only near where it is - her, the ducklings behind her or round her, or their corner at night - is
    // its whereabouts worked out: the ray over the water passing near them, or near enough for their rings)
    bool family = false;
    if (kids > 0) {
        vec2 mp, md;
        wander(70.0, 0.3, 0.05, famClock(t), mp, md);
        float reach = 0.19 + float(kids) * 0.17 + 0.6;
        vec3 pHigh = rd.y < -0.002 ? p0 - rd * (0.45 / -rd.y) : p0;
        if (pHigh.y > gStart.y) pHigh = gStart;
        vec2 ab = p0.xz - pHigh.xz;
        float w = clamp(dot(mp - pHigh.xz, ab) / max(dot(ab, ab), 1e-6), 0.0, 1.0);
        family = length(pHigh.xz + ab * w - mp) < reach || (Daylight > 0.1 && Daylight < 0.35); // (going to bed: anywhere between)
        if (Daylight < 0.35) { // (asleep in the pond's corner)
            vec2 cn = pondRect[0].xy + 0.4;
            float wc = clamp(dot(cn - pHigh.xz, ab) / max(dot(ab, ab), 1e-6), 0.0, 1.0);
            family = family || length(pHigh.xz + ab * wc - cn) < 0.9;
        }
    }
    placeDucks(t, family);
    float tuckAll = 1.0 - smoothstep(0.1, 0.35, Daylight);
    for (int dk = 0; dk < 7; dk++) {
        float height = 0.0, ahead = 0.0, spread = 0.0, flap = 0.0, splash = 0.0, kind = 1.0, tuck = 0.0, scl = 1.0;
        vec2 pos, dir, sitPos;
        if (!gHere[dk]) continue;
        pos = gDuck[dk].xy;
        dir = gDuck[dk].zw;
        float dab = gDab[dk];
        if (dk < 2) { // a visitor, flying in for a while
            duckHere(dk, t, height, ahead, spread, flap, splash, kind);
            sitPos = pos - dir * ahead;
        } else { // the family
            int f = dk - 2;
            sitPos = pos;
            tuck = tuckAll;
            kind = f == 0 ? 1.0 : 2.0;
            scl = f == 0 ? 1.0 : 0.55;
        }
        float dist = length(p0.xz - pos);
        if (height < 0.05) ripple += sin(dist * 110.0 - t * 5.0) * exp(-dist * 9.0 / scl) * 0.06 * (1.0 - 0.7 * tuck);
        float sd = length(p0.xz - sitPos);
        ripple += splash * sin(sd * 70.0 - t * 9.0) * exp(-sd * 5.0) * 0.25; // (the landing splash, spreading)
        ripple += dab * sin(sd * 120.0 / scl - t * 8.0) * exp(-sd * 14.0 / scl) * 0.15; // (rings as it dabbles)
        if (rd.y > -0.002 || res.a > 0.0) continue;
        float bob = height > 0.0 ? 0.0 : (kind > 1.5 ? 0.008 : 0.012) * sin(t * (kind > 1.5 ? 2.3 : 1.7) + float(dk)) * (1.0 - 0.6 * tuck);
        float base = SURF + height + bob;
        float up = (0.4 + height) * scl;
        vec3 pTop = p0 - rd * (up / -rd.y);
        if (pTop.y > gStart.y) pTop = gStart; // (nothing behind the eye)
        vec2 ab = p0.xz - pTop.xz;
        float w = clamp(dot(pos - pTop.xz, ab) / max(dot(ab, ab), 1e-6), 0.0, 1.0);
        if (length(pTop.xz + ab * w - pos) > 0.36 * scl) continue;
        float sc = 16.0 / 0.85 / scl;
        vec2 right = vec2(dir.y, -dir.x);
        vec3 ro = vec3(dot(pTop.xz - pos, right), pTop.y - base, dot(pTop.xz - pos, dir)) * sc;
        vec3 rr = vec3(dot(rd.xz, right), rd.y, dot(rd.xz, dir));
        if (dab > 0.001) { // (tipped up: head down under the water, tail in the air)
            float pa = -dab * 1.25;
            mat2 pr = mat2(cos(pa), -sin(pa), sin(pa), cos(pa));
            vec2 pv = ro.yz - vec2(0.8, 0.0);
            ro.yz = pr * pv + vec2(0.8, 0.0);
            rr.yz = pr * rr.yz;
        }
        float flapAngle = flap > 0.5 ? 0.15 + 0.85 * sin(t * 15.0 + float(dk)) : 0.25; // (beating, or held up in a glide)
        float tm = 0.0, tEnd = length(p0 - pTop) * sc;
        for (int n = 0; n < 56; n++) {
            vec3 q = ro + rr * tm;
            int mat;
            float f = kind > 1.5 ? ducklingSDF(q, tuck, mat) : duckSDF(q, spread, flapAngle, tuck, mat);
            if (f < 0.02) {
                vec2 e = vec2(0.03, 0.0);
                int m2;
                vec3 nn = kind > 1.5
                        ? normalize(vec3(ducklingSDF(q + e.xyy, tuck, m2) - ducklingSDF(q - e.xyy, tuck, m2),
                                         ducklingSDF(q + e.yxy, tuck, m2) - ducklingSDF(q - e.yxy, tuck, m2),
                                         ducklingSDF(q + e.yyx, tuck, m2) - ducklingSDF(q - e.yyx, tuck, m2)))
                        : normalize(vec3(duckSDF(q + e.xyy, spread, flapAngle, tuck, m2) - duckSDF(q - e.xyy, spread, flapAngle, tuck, m2),
                                         duckSDF(q + e.yxy, spread, flapAngle, tuck, m2) - duckSDF(q - e.yxy, spread, flapAngle, tuck, m2),
                                         duckSDF(q + e.yyx, spread, flapAngle, tuck, m2) - duckSDF(q - e.yyx, spread, flapAngle, tuck, m2)));
                vec3 sun = normalize(vec3(dot(SUN.xz, right), SUN.y, dot(SUN.xz, dir)));
                float lam = 0.45 + 0.55 * max(dot(nn, sun), 0.0);
                vec3 col;
                if (mat == 2) {
                    col = vec3(0.03) + vec3(0.8) * pow(max(dot(reflect(rr, nn), sun), 0.0), 30.0);
                } else if (kind > 1.5) { // a duckling: fluffy yellow, a brown cap and back, a dark stripe through its eye, a dark bill
                    if (mat == 1) {
                        col = vec3(0.3, 0.26, 0.22) * lam;
                    } else {
                        float fluff = noise2(q.xz * 6.0 + q.y * 4.0);
                        col = vec3(0.98, 0.84, 0.36) * (0.88 + 0.2 * fluff);
                        vec3 hc = mix(vec3(0.0, 2.35, 1.45), vec3(0.45, 1.75, 0.35), tuck);
                        bool onHead = length(q - hc) < 1.3;
                        if (onHead ? q.y > hc.y + 0.55 : q.y > 1.55 && q.z < 0.9) col = vec3(0.45, 0.33, 0.16) * (0.85 + 0.25 * fluff); // (cap, back)
                        if (!onHead && q.y > 1.55 && q.z < 0.9 && noise2(q.xz * 3.0) > 0.72) col = vec3(0.95, 0.85, 0.45); // (pale patches)
                        if (onHead && tuck < 0.5 && abs(q.y - 2.55 + 0.25 * (q.z - 1.95)) < 0.12 && q.z > 1.4 && abs(q.x) > 0.5) col = vec3(0.3, 0.22, 0.12);
                        col *= lam;
                    }
                } else if (mat == 1) {
                    col = (kind < 0.5 ? vec3(0.92, 0.78, 0.22) : vec3(0.85, 0.5, 0.15)) * lam;
                } else if (mat == 3) { // the wings: grey-brown, a blue patch with white edges near the back
                    col = kind < 0.5 ? vec3(0.5, 0.48, 0.44) : vec3(0.42, 0.31, 0.19);
                    float along = q.z;
                    if (abs(along + 0.5) < 0.35 && abs(q.x) > 1.0) col = vec3(0.15, 0.25, 0.75);
                    col *= lam;
                } else if (kind < 0.5) { // a drake: green head, white collar, chestnut breast, grey sides
                    if (q.y > 2.35 && q.z > 1.0) col = vec3(0.05, 0.36, 0.16) + vec3(0.25) * pow(max(dot(reflect(rr, nn), sun), 0.0), 16.0);
                    else if (abs(q.y - 2.2) < 0.13 && q.z > 1.0) col = vec3(0.95);
                    else if (q.z > 0.9 && q.y < 2.3) col = vec3(0.45, 0.22, 0.12);
                    else if (q.z < -1.8) col = q.y > 1.6 ? vec3(0.05) : vec3(0.9);
                    else col = vec3(0.62, 0.62, 0.58);
                    col *= lam;
                } else { // a hen: mottled browns
                    col = mix(vec3(0.5, 0.37, 0.22), vec3(0.3, 0.2, 0.12), step(0.62, noise2(q.xz * 2.3 + q.y * 1.7)));
                    if (q.y > 2.4 && q.z > 1.0 && tuck < 0.5) col = vec3(0.55, 0.42, 0.28);
                    col *= lam;
                }
                gHit = pTop + rd * (tm / sc);
                res = vec4(col * light, 1.0);
                break;
            }
            tm += max(f * 0.85, 0.05);
            if (tm > tEnd) break;
        }
    }
    return res;
}

// the turtle: is it here now (how much: it fades in from the murk and away again)
float turtleHere(float t) {
    float ph = t / 190.0 + hash22(pondLo * 2.3 + pondHi).y * 5.0;
    float e = floor(ph), te = fract(ph) * 190.0;
    vec2 size = pondRect[0].zw - pondRect[0].xy;
    if (hash22(pondLo * 1.9 + pondHi * 0.4 + e * 5.7).x > 0.5 || size.x * size.y < 4.0) return 0.0;
    return smoothstep(10.0, 15.0, te) * (1.0 - smoothstep(155.0, 160.0, te));
}

// a turtle at q (world x, z), swimming in the middle layer: (colour, how much it covers)
vec4 turtleAt(vec2 q, float t, bool body) {
    float here = turtleHere(t);
    if (here <= 0.0) return vec4(0.0);
    vec2 pos, dir;
    wander(77.0, 0.28, 0.05, t, pos, dir);
    vec2 d = q - pos;
    if (dot(d, d) > 0.09) return vec4(0.0);
    float s = dot(d, dir), side = dot(d, vec2(-dir.y, dir.x));
    vec3 skin = vec3(0.42, 0.48, 0.3);
    // the shell
    vec2 e = vec2(s / 0.15, side / 0.115);
    float r = dot(e, e);
    if (r < 1.0) {
        if (body) return vec4(0.0, 0.0, 0.0, here);
        vec2 g = vec2(s, side) * 32.0;
        vec2 id = floor(g), fq = fract(g);
        float d1 = 9.0, d2 = 9.0;
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                vec2 o = vec2(i, j);
                float dd = length(fq - (o + 0.5 + 0.3 * (hash22(id + o) - 0.5)));
                if (dd < d1) { d2 = d1; d1 = dd; } else if (dd < d2) { d2 = dd; }
            }
        }
        vec3 col = mix(vec3(0.24, 0.28, 0.13), vec3(0.38, 0.4, 0.2), 0.5 + 0.5 * noise2(g * 0.4));
        col *= 0.65 + 0.35 * smoothstep(0.0, 0.15, d2 - d1);          // (the scutes' seams)
        col = mix(col, vec3(0.55, 0.5, 0.3), smoothstep(0.75, 1.0, r)); // (a paler rim)
        col *= 0.75 + 0.25 * (1.0 - r);
        return vec4(col, here);
    }
    // the head
    if (length(vec2((s - 0.19) / 0.05, side / 0.034)) < 1.0) return body ? vec4(0.0, 0.0, 0.0, here) : vec4(skin * (side * side < 0.0002 ? 1.1 : 1.0), here);
    if (body) return vec4(0.0);
    // flippers, paddling: the front pair long and sweeping, the back pair small
    float stroke = sin(t * 2.4);
    for (int k = 0; k < 2; k++) {
        float sgn = k == 0 ? 1.0 : -1.0;
        vec2 f = vec2(s - 0.07, side * sgn - 0.11);
        float a = 0.5 + 0.55 * stroke;
        f = mat2(cos(a), sin(a), -sin(a), cos(a)) * f;
        if (length(vec2((f.x + 0.0) / 0.035, (f.y - 0.05) / 0.08)) < 1.0) return vec4(skin * 0.9, here);
        vec2 b = vec2(s + 0.12, side * sgn - 0.085);
        if (length(vec2(b.x / 0.035, b.y / 0.045)) < 1.0) return vec4(skin * 0.85, here);
    }
    if (length(vec2((s + 0.17) / 0.04, side / 0.015)) < 1.0) return vec4(skin * 0.85, here);
    return vec4(0.0);
}

float caustics(vec2 p, float t) {
    float a = sin(p.x * 31.0 + t * 0.9 + 2.0 * sin(p.y * 17.0 + t * 1.3));
    float b = sin(p.y * 29.0 - t * 0.8 + 2.0 * sin(p.x * 13.0 - t * 1.1));
    return pow(0.5 + 0.25 * (a + b), 4.0) * 1.6;
}

// the depth of a point (world blocks), from where this fragment is on the box (vpos: the same point, as drawn)
float depthOf(vec3 hit, vec3 entry) {
    vec4 c = ProjMat * ModelViewMat * vec4(vpos + (hit - entry), 1.0);
    return clamp(c.z / c.w * 0.5 + 0.5, 0.0, 1.0);
}

void main() {
    float t = PondTime;
    float day = clamp(Daylight, 0.0, 1.0);
    vec3 rd = normalize(wray);
    // where the view enters the box over the water (its top, or a side above the rim)
    vec3 entry = vec3(uv.x, mix(SURF, TOP, vColor.a), uv.y);
    if (rd.y > -0.002) {
        discard;
    }
    cell0 = floor(entry.xz); // (the block this is drawn for)
    // the whole pond's rectangle (how far it reaches from this block each way, packed into the colour) and its koi
    int rectBits = int(vColor.r * 255.0 + 0.5) | (int(vColor.g * 255.0 + 0.5) << 8) | (int(vColor.b * 255.0 + 0.5) << 16);
    pondLo = cell0 - vec2(float(rectBits & 63), float((rectBits >> 12) & 63));
    pondHi = cell0 + vec2(float((rectBits >> 6) & 63), float((rectBits >> 18) & 63)) + 1.0;
    // its shape (KoiPondData's row for it) and the blocks round this one
    int word = (vShape.x & 65535) | ((vShape.y & 65535) << 16);
    int row = word & 255;
    pondRound = (word >> 8) & 255;
    pondStones = (word >> 16) & 511;
    vec4 head = texelFetch(Sampler0, ivec2(0, row), 0) * 255.0 + 0.5;
    pondRects = clamp(int(head.r), 1, 8);
    pondBlocks = max(floor(head.g) + 256.0 * floor(head.b), 1.0);
    for (int r = 0; r < 8; r++) {
        vec4 q = floor(texelFetch(Sampler0, ivec2(1 + r, row), 0) * 255.0 + 0.5);
        pondRect[r] = vec4(pondLo + q.xz, pondLo + q.yw + 1.0); // (its west-north and east-south corners)
    }
    if (head.r < 0.75) { // (not known yet: the rectangle round it)
        pondRects = 1;
        pondRect[0] = vec4(pondLo, pondHi);
        pondBlocks = (pondHi.x - pondLo.x) * (pondHi.y - pondLo.y);
    }
    pondFish = clamp(int(pondBlocks * 0.45 + 0.75), 1, 24);
    // the box over the water (from the water up, over each pond block, its sides only where the pond ends - any
    // shape of pond). Every pixel is drawn once, where the view LEAVES it, worked out from which face this is (the
    // face's own slope on screen) and which way that face looks out - not from its winding; the ray starts at the eye
    vec3 fn = cross(dFdx(entry), dFdy(entry));
    vec2 local = entry.xz - cell0;
    vec3 outward = abs(fn.y) >= max(abs(fn.x), abs(fn.z)) ? vec3(0.0, entry.y > 0.5 * (SURF + TOP) ? 1.0 : -1.0, 0.0)
            : abs(fn.x) > abs(fn.z) ? vec3(local.x > 0.5 ? 1.0 : -1.0, 0.0, 0.0)
            : vec3(0.0, 0.0, local.y > 0.5 ? 1.0 : -1.0);
    bool leaving = dot(rd, outward) > 0.0;
    vec3 start;
    if (ProjMat[2][3] < -0.5) {
        if (!leaving) {
            discard;
        }
        start = entry - wray; // (the eye: nothing between it and the box)
    } else {
        if (leaving) {
            discard; // (a flat view, as in a picture: drawn where the view comes in)
        }
        start = entry;
    }
    if (start.y < SURF - 0.001) {
        discard;
    }
    vec3 p0 = start + rd * ((SURF - start.y) / rd.y); // (where it meets the water)
    gStart = start;
    float light = 0.18 + 0.82 * day;

    // a frog sitting on a lily pad, in front of this point
    vec4 frog = frogHit(p0, rd, t, light);
    if (frog.a > 0.0) {
        fragColor = vec4(frog.rgb, 1.0);
        gl_FragDepth = depthOf(gHit, entry);
        return;
    }
    // a duck (now and then one or two drop in for a while)
    float ripple;
    vec4 duck = duckHit(p0, rd, t, light, ripple);
    if (duck.a > 0.0) {
        fragColor = vec4(duck.rgb, 1.0);
        gl_FragDepth = depthOf(gHit, entry);
        return;
    }
    // no water there (the view leaves by a side, over the rim to beyond the pond): nothing to draw. Water is seen
    // where the view leaves by the bottom (on the water itself)
    if (ProjMat[2][3] < -0.5 ? outward.y > -0.5 : !isPond(floor(p0.xz))) {
        discard;
    }
    // the stone sides round this block (where the pond ends; where it carries on, at the far side of the pond)
    vec2 wlo = vec2((pondRound & 1) != 0 ? pondLo.x + 0.125 : cell0.x + 0.125, (pondRound & 4) != 0 ? pondLo.y + 0.125 : cell0.y + 0.125);
    vec2 whi = vec2((pondRound & 2) != 0 ? pondHi.x - 0.125 : cell0.x + 0.875, (pondRound & 8) != 0 ? pondHi.y - 0.125 : cell0.y + 0.875);
    gl_FragDepth = depthOf(p0, entry);
    // a lily pad here: it floats on top
    vec4 pad = padAt(p0.xz, t);
    if (pad.a > 0.0) {
        fragColor = vec4(pad.rgb * light, 1.0);
        return;
    }

    // the surface: gentle ripples
    vec2 x = p0.xz;
    vec2 g = vec2(0.0);
    g += vec2(1.0, 0.3) * cos(dot(x, vec2(1.0, 0.3)) * 23.0 - t * 1.4) * 0.0035;
    g += vec2(-0.4, 1.0) * cos(dot(x, vec2(-0.4, 1.0)) * 31.0 - t * 1.9) * 0.0025;
    g += vec2(0.7, -0.7) * cos(dot(x, vec2(0.7, -0.7)) * 47.0 - t * 2.6) * 0.0015;
    vec3 rings = rainRings(x, t);
    g += rings.xy;
    vec3 n = normalize(vec3(-g.x * 23.0, 1.0, -g.y * 23.0));
    vec3 rr = refract(rd, n, 1.0 / 1.33);
    if (rr.y > -0.05) rr = normalize(vec3(rr.x, -0.05, rr.z));

    // what the bent ray meets: the stone sides (where no pond carries on), else the bottom
    float tHit = (FLOOR - SURF) / rr.y;
    int what = 0; // 0 bottom, 1 side
    // (the pond's stone sides: round its whole rectangle)
    if (rr.x < 0.0) { float tw = (wlo.x - p0.x) / rr.x; if (tw < tHit) { tHit = tw; what = 1; } }
    if (rr.x > 0.0) { float tw = (whi.x - p0.x) / rr.x; if (tw < tHit) { tHit = tw; what = 1; } }
    if (rr.z < 0.0) { float tw = (wlo.y - p0.z) / rr.z; if (tw < tHit) { tHit = tw; what = 1; } }
    if (rr.z > 0.0) { float tw = (whi.y - p0.z) / rr.z; if (tw < tHit) { tHit = tw; what = 1; } }
    vec3 ph = p0 + rr * tHit;

    vec3 water = vec3(0.05, 0.17, 0.15) * light;
    placeKoi(t);
    // the bottom or the side
    vec3 bg;
    if (what == 0) {
        vec2 q = ph.xz * 22.0;
        vec2 id = floor(q), fq = fract(q);
        float d1 = 9.0, d2 = 9.0;
        vec2 best = vec2(0.0);
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                vec2 o = vec2(i, j);
                vec2 pt = o + hash22(id + o) * 0.8 + 0.1;
                float dd = length(fq - pt);
                if (dd < d1) { d2 = d1; d1 = dd; best = id + o; } else if (dd < d2) { d2 = dd; }
            }
        }
        vec2 hb = hash22(best);
        vec3 stone = mix(mix(vec3(0.45, 0.4, 0.33), vec3(0.6, 0.55, 0.46), hb.x), vec3(0.35, 0.36, 0.34), step(0.7, hb.y));
        stone *= 0.75 + 0.25 * smoothstep(0.0, 0.12, d2 - d1);
        bg = stone;
        // shadows: koi in each layer, and the lily pads on top
        float sh = 1.0;
        for (int L = 0; L < 3; L++) {
            vec2 sp = ph.xz + SUN.xz * (layerY(L) - ph.y);
            if (koiLayer(sp, L, t, true).a > 0.0) sh *= 0.62;
            if (L == 1) sh *= 1.0 - 0.4 * turtleAt(sp, t, true).a;
        }
        if (padAt(ph.xz + SUN.xz * (SURF - ph.y), t).a > 0.0) sh *= 0.5;
        bg *= sh * light;
        bg += vec3(0.5, 0.6, 0.5) * caustics(ph.xz, t) * sh * day * 0.5;
    } else {
        float s = noise2(vec2(ph.x + ph.z, ph.y) * 40.0) * 0.6 + noise2(vec2(ph.x + ph.z, ph.y) * 11.0) * 0.4;
        bg = mix(vec3(0.32, 0.31, 0.29), vec3(0.5, 0.48, 0.44), s);
        bg = mix(bg, vec3(0.2, 0.32, 0.14), smoothstep(SURF - 0.12, SURF, ph.y) * 0.7); // (moss at the waterline)
        bg *= light;
        bg += vec3(0.3, 0.4, 0.35) * caustics(ph.xz + ph.y, t) * day * 0.25;
    }
    bg = mix(bg, water, 1.0 - exp(-tHit * 2.6));

    // the koi above it, front to back (the fins are see-through)
    vec3 acc = vec3(0.0);
    float accA = 0.0;
    for (int L = 0; L < 3; L++) {
        float tl = (layerY(L) - SURF) / rr.y;
        if (tl > tHit) break;
        vec2 q = p0.xz + rr.xz * tl;
        vec4 k = L == 1 ? turtleAt(q, t, false) : vec4(0.0);
        if (k.a <= 0.0) k = koiLayer(q, L, t, false);
        if (k.a > 0.0) {
            vec3 kc = mix(k.rgb * light, water, 1.0 - exp(-tl * 2.6));
            acc += (1.0 - accA) * k.a * kc;
            accA += (1.0 - accA) * k.a;
            if (accA > 0.98) break;
        }
    }
    vec3 under = acc + (1.0 - accA) * bg;

    // the sky off the surface, and the sun's glint
    float fres = 0.02 + 0.98 * pow(1.0 - max(dot(-rd, n), 0.0), 5.0);
    vec3 rf = reflect(rd, n);
    vec3 sky = mix(mix(vec3(0.02, 0.03, 0.07), vec3(0.45, 0.62, 0.85), day), mix(vec3(0.05, 0.06, 0.12), vec3(0.75, 0.86, 1.0), day),
                   smoothstep(0.0, 0.8, rf.y));
    vec3 col = mix(under, sky, fres);
    col += vec3(1.0, 0.95, 0.85) * pow(max(dot(rf, normalize(SUN)), 0.0), 160.0) * day * 0.7;
    col += vec3(0.8, 0.9, 0.9) * ripple * light; // (rings round a swimming duck)
    col += vec3(0.7, 0.78, 0.85) * min(rings.z, 1.0) * 0.18 * light;
    // the moon at night: its reflection, broken up a little by the ripples, a soft glow round it
    if (MoonDir.y > 0.02) {
        float m = max(dot(rf, normalize(MoonDir)), 0.0);
        float night = (1.0 - day) * smoothstep(0.02, 0.2, MoonDir.y);
        col += vec3(0.85, 0.9, 1.0) * (smoothstep(0.9988, 0.9994, m) * 0.9 + pow(m, 300.0) * 0.35) * night * (0.4 + 0.6 * fres * 8.0);
    }
    fragColor = vec4(col, 1.0);
}
