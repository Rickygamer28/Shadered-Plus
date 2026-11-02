#version 150

// Shadered+'s small fish tanks (our own code), traced in the tank's own space:
//   0 a fishbowl - a round glass bowl, open at the top, coloured gravel, a sprig of weed, a goldfish or two (or a
//     betta, or guppies);
//   1 a tall cylinder tank - a glass column (stacked blocks make one), gravel, tall plants swaying, an air stone's
//     bubbles rising up the middle, fish circling at every height;
//   2 a slim wall tank - a shallow tank hung on a wall (side by side and on top of each other they make one), a
//     painted backdrop, plants along the back, a school of fish swimming end to end and turning.
// The fish are the aquariums' own (include/aqua_creatures). The water is see-through: premultiplied colour.

in vec3 lp;
in vec3 lray;
flat in vec4 info;  // shape, width, height (blocks), variant
flat in vec2 extra; // seed, light (0..1)

uniform float TankTime;
uniform float ItemMode;

out vec4 fragColor;

vec2 hash22(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

const float Deep = 0.0;

#moj_import <skyblocks:aqua_creatures.glsl>

float hash11(float n) {
    return fract(sin(n * 12.9898 + 4.1414) * 43758.5453);
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

const float SLIM_D = 0.3125; // (the slim tank's depth; client/SmallTankRenderer's box)
const vec3 BOWL_C = vec3(0.5, 0.4, 0.5);
const float BOWL_R = 0.38;

int gShape;
float gW, gH;

// (the tall tank: as wide as its footprint, gW blocks; its middle and radius)
vec2 cylC() { return vec2(gW * 0.5); }
float cylR() { return gW * 0.5 - 0.08; }

float waterTop() {
    return gShape == 0 ? 0.56 : gShape == 1 ? gH - 0.3 : gH - 0.2;
}

float floorY() {
    return gShape == 0 ? 0.1 : gShape == 1 ? 0.24 : 0.2;
}

vec2 slab(float o, float d, float lo, float hi) {
    if (abs(d) < 1e-6) {
        return o >= lo && o <= hi ? vec2(-1e9, 1e9) : vec2(1e9, -1e9);
    }
    float a = (lo - o) / d, b = (hi - o) / d;
    return vec2(min(a, b), max(a, b));
}

// where the ray is inside the glass (the container's inside): t from .. to
vec2 container(vec3 ro, vec3 rd) {
    if (gShape == 0) {
        vec3 oc = ro - BOWL_C;
        float b = dot(oc, rd), c = dot(oc, oc) - BOWL_R * BOWL_R, h = b * b - c;
        if (h < 0.0) return vec2(1e9, -1e9);
        h = sqrt(h);
        vec2 s = vec2(-b - h, -b + h);
        vec2 y = slab(ro.y, rd.y, 0.035, 0.7); // (a flat foot, an open top)
        return vec2(max(s.x, y.x), min(s.y, y.y));
    }
    if (gShape == 1) {
        vec2 o = ro.xz - cylC(), d = rd.xz;
        float a = dot(d, d), b = dot(o, d), c = dot(o, o) - cylR() * cylR();
        vec2 s;
        if (a < 1e-8) {
            s = c < 0.0 ? vec2(-1e9, 1e9) : vec2(1e9, -1e9);
        } else {
            float h = b * b - a * c;
            if (h < 0.0) return vec2(1e9, -1e9);
            h = sqrt(h);
            s = vec2(-b - h, -b + h) / a;
        }
        vec2 y = slab(ro.y, rd.y, 0.125, gH - 0.125);
        return vec2(max(s.x, y.x), min(s.y, y.y));
    }
    vec2 x = slab(ro.x, rd.x, 0.0625, gW - 0.0625), y = slab(ro.y, rd.y, 0.0625, gH - 0.0625), z = slab(ro.z, rd.z, 0.02, SLIM_D);
    return vec2(max(x.x, max(y.x, z.x)), min(x.y, min(y.y, z.y)));
}

vec3 glassNormal(vec3 p) {
    if (gShape == 0) return normalize(p - BOWL_C);
    if (gShape == 1) return normalize(vec3(p.x - cylC().x, 0.0, p.z - cylC().y));
    return vec3(0.0, 0.0, 1.0);
}

// ---- the fish ----

int fishCount() {
    int v = int(info.w + 0.5);
    if (gShape == 0) return v == 0 ? 2 : v == 1 ? 1 : 3;
    if (gShape == 1) {
        int S = int(gW + 0.5);
        if (S <= 1) return min(2 + 2 * int(gH + 0.5), v == 1 ? 4 : 7);
        if (S == 2) return min(4 + 2 * int(gH + 0.5), 10);
        return min(5 + 2 * int(gH + 0.5), 12);
    }
    return min(int(gW * gH * 2.5 + 2.0), 9);
}

int fishKind(int i) {
    int v = int(info.w + 0.5);
    if (gShape == 0) return v == 0 ? 5 : v == 1 ? 6 : 8;                                 // goldfish / a betta / guppies
    if (gShape == 1) { // (what lives in it goes by how wide it is)
        int S = int(gW + 0.5);
        if (S <= 1) {
            if (v == 0) return i < 4 ? 3 : 8;                                               // neon tetras and guppies
            if (v == 1) return i == 0 ? 6 : 8;                                              // a betta and guppies
            return 5;                                                                       // goldfish
        }
        if (S == 2) {
            if (v == 0) return i == 0 ? 4 : i == 1 ? 9 : i == 2 ? 4 : i < 7 ? 3 : 8;        // angelfish, discus, tetras, guppies
            if (v == 1) return i == 0 ? 1 : i == 1 ? 2 : i == 2 ? 7 : i < 6 ? 0 : 28;       // reef: tangs, a pufferfish, clownfish
            return i == 0 ? 6 : 5;                                                          // goldfish and a betta
        }
        if (S == 3) {
            if (v == 0) return i == 0 ? 13 : i == 1 ? 14 : 17;                              // a sea turtle, a grouper, sardines
            if (v == 1) return 15;                                                          // jellyfish
            return i == 0 ? 7 : i < 3 ? 1 : i < 5 ? 2 : 0;                                  // a big reef
        }
        if (v == 0) return i == 0 ? 10 : 17;                                                // a little shark and a school of sardines
        if (v == 1) return 15;                                                              // jellyfish
        return i == 0 ? 12 : i == 1 ? 13 : 17;                                              // a manta ray, a turtle, sardines
    }
    if (v == 0) return 3;                                                                   // a school of neon tetras
    if (v == 1) return i == 0 ? 6 : 8;                                                      // guppies and a betta
    return i == 0 ? 1 : i < 3 ? 0 : 3;                                                      // clownfish, a tang, tetras
}

float fishSize(int kind) {
    if (kind == 10) return 0.55;              // (the sea's big ones, scaled down to the tank)
    if (kind == 12) return 0.5;
    if (kind == 13) return 0.6;
    if (kind == 14) return 0.5;
    if (kind == 15) return 0.75;
    if (kind == 17) return 0.45;
    return kind == 3 || kind == 8 ? 0.5 : kind == 4 || kind == 9 ? 0.62 : kind == 5 ? 0.62 : 0.58;
}

float gSqueeze = 0.0; // (a jellyfish's squeeze, for the fish just placed)

void tankFish(int i, float t, out int kind, out vec3 c, out vec3 fwd, out vec3 up, out vec3 side, out float size) {
    float fi = float(i), s = extra.x * 37.0;
    kind = fishKind(i);
    size = fishSize(kind) * (0.9 + 0.2 * hash11(fi * 3.3 + s));
    if (gShape == 1) size *= 1.0 + 0.3 * (gW - 1.0); // (the wider the tank, the bigger its fish)
    float dir = hash11(fi * 7.1 + s) > 0.5 ? 1.0 : -1.0;
    vec3 vel;
    if (gShape == 2) {
        // end to end along the tank, turning at the ends (a school keeps together)
        bool school = int(info.w + 0.5) == 0;
        float w = school ? 0.32 : 0.22 + 0.12 * hash11(fi * 5.5 + s);
        float ph = (school ? 0.0 : fi * 2.3) + s + t * w / max(gW, 1.0) * 1.6 + (school ? fi * 0.09 : 0.0);
        float span = max(gW * 0.5 - 0.2, 0.05);
        float x = gW * 0.5 + span * sin(ph);
        float yl = floorY() + 0.14, yh = waterTop() - 0.1;
        float y = mix(yl, yh, school ? 0.45 + 0.25 * hash11(fi * 2.9 + s) * (gH > 1.5 ? 2.0 : 1.0) * 0.5 : hash11(fi * 4.7 + s)) + 0.04 * sin(t * 0.7 + fi * 1.3);
        y = clamp(y, yl, yh);
        float z = SLIM_D * 0.55 + (school ? 0.05 * sin(fi * 2.0) : 0.0);
        c = vec3(x, y, z);
        float vx = cos(ph);
        vel = vec3(vx, 0.04 * cos(t * 0.7 + fi * 1.3), 0.3 * sin(ph) * (abs(vx) < 0.4 ? 1.0 : 0.3));
    } else {
        // round and round, rising and sinking a little
        float w = (0.35 + 0.2 * hash11(fi * 5.5 + s)) * dir;
        float a = fi * 2.399 + s + t * w;
        float r, y;
        if (gShape == 0) {
            r = 0.15 + 0.04 * sin(t * 0.31 + fi * 2.0);
            y = 0.3 + 0.08 * sin(t * 0.23 + fi * 1.7);
        } else {
            float R = cylR();
            bool sardine = kind == 17;
            if (sardine) { // (a school: round together, close)
                w = 0.45 * (hash11(s) > 0.5 ? 1.0 : -1.0);
                dir = w > 0.0 ? 1.0 : -1.0;
                a = s + t * w + fi * 0.13;
            }
            r = (sardine ? 0.55 + 0.08 * sin(fi * 2.1) : 0.25 + 0.4 * hash11(fi * 8.3 + s)) * R + 0.03 * sin(t * 0.3 + fi);
            float yl = floorY() + 0.25, yh = waterTop() - 0.15;
            y = mix(yl, yh, sardine ? 0.5 + 0.08 * sin(fi * 1.7) : hash11(fi * 4.1 + s)) + 0.12 * sin(t * 0.17 + (sardine ? 0.0 : fi * 1.9));
            y = clamp(y, yl, yh);
        }
        c = vec3(cylC().x + r * cos(a), y, cylC().y + r * sin(a));
        vel = vec3(-sin(a), 0.05 * cos(t * 0.23 + fi * 1.7), cos(a)) * dir;
    }
    fwd = normalize(vel);
    gSqueeze = 0.0;
    if (kind == 15) { // (a jellyfish upright, drifting, squeezing and relaxing)
        float jph = fract(t * 0.4 + fi * 0.37);
        gSqueeze = smoothstep(0.0, 0.12, jph) * (1.0 - smoothstep(0.12, 0.9, jph));
        c.xz = cylC() + (c.xz - cylC()) * 0.8 + 0.05 * vec2(sin(t * 0.13 + fi), cos(t * 0.11 + fi * 2.0));
        c.y += 0.05 * gSqueeze;
        fwd = vec3(1.0, 0.0, 0.0);
    }
    up = vec3(0.0, 1.0, 0.0);
    side = normalize(cross(fwd, up));
    up = cross(side, fwd);
}

void tankSwim(int i, int kind, Species sp, float t, out float wave, out float flap) {
    float fi = float(i);
    wave = t * (9.0 - 5.0 * clamp(sp.body.x, 0.0, 1.0)) + fi * 2.0;
    flap = sin(t * 3.0 + fi * 1.3);
    gBeat = 1.0; gBend = 0.0; gFinSway = sin(t * 1.6 + fi); gMouth = 0.5 + 0.5 * sin(t * 2.3 + fi * 1.7);
    gTime = t; gWalk = 0.0; gPulse = gSqueeze;
}

// ---- the plants: blades of weed swaying ----

int plantCount() {
    return gShape == 0 ? 3 : gShape == 1 ? min(7 * int(gW + 0.5), 16) : min(int(gW * 4.0), 16);
}

// blade j: its foot (x, z), height, and lean
vec4 blade(int j) {
    float fj = float(j), s = extra.x * 11.0;
    if (gShape == 0) return vec4(0.62 + 0.03 * fj, 0.42 + 0.04 * fj, 0.16 + 0.05 * hash11(fj + s), fj - 1.0);
    if (gShape == 1) {
        float a = fj * 2.1 + s;
        float r = cylR() * (0.62 + 0.18 * hash11(fj * 3.0 + s));
        return vec4(cylC().x + r * cos(a), cylC().y + r * sin(a), min(0.5 + (gH - 1.0) * (0.35 + 0.4 * hash11(fj * 5.0 + s)), waterTop() - floorY() - 0.1), hash11(fj * 7.0) - 0.5);
    }
    float x = (fj + 0.5 + 0.6 * (hash11(fj * 3.0 + s) - 0.5)) * gW / float(plantCount());
    return vec4(x, 0.05 + 0.08 * hash11(fj * 9.0 + s), min(0.3 + (gH - 0.4) * 0.6 * hash11(fj * 5.0 + s), waterTop() - floorY() - 0.08), hash11(fj * 7.0) - 0.5);
}

float sdSeg(vec3 p, vec3 a, vec3 b) {
    vec3 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

float plantSDF(vec3 p, float t, out float along) {
    float best = 1e9;
    along = 0.0;
    float fl = floorY();
    int n = plantCount();
    for (int j = 0; j < 16; j++) {
        if (j >= n) break;
        vec4 b = blade(j);
        vec3 base = vec3(b.x, fl - 0.02, b.y);
        if (abs(p.x - base.x) > 0.2 * (gShape == 1 ? gW : 1.0) || abs(p.z - base.z) > 0.2 * (gShape == 1 ? gW : 1.0) || p.y > fl + b.z + 0.05) continue;
        vec3 prev = base;
        for (int k = 1; k <= 3; k++) {
            float u = float(k) / 3.0;
            float sw = (0.03 + 0.04 * b.z) * u * u * sin(t * 0.8 + float(j) * 1.7 + u * 2.0);
            vec3 q = base + vec3(b.w * 0.08 * u + sw, b.z * u, 0.5 * sw * (gShape == 2 ? 0.3 : 1.0));
            float r = mix(0.014, 0.004, u) * (gShape == 1 ? sqrt(gW) : 1.0);
            float d = sdSeg(p, prev, q) - r;
            if (d < best) {
                best = d;
                along = (float(k) - 1.0 + clamp(dot(p - prev, q - prev) / dot(q - prev, q - prev), 0.0, 1.0)) / 3.0;
            }
            prev = q;
        }
    }
    return best;
}

float caustic(vec2 p, float t) {
    vec2 q = p * 6.0;
    float c = 0.0;
    for (int k = 0; k < 2; k++) {
        q += vec2(sin(q.y * 1.3 + t * 0.9), cos(q.x * 1.1 - t * 0.8)) * 0.6;
        c += 0.5 / (1.0 + 8.0 * abs(sin(q.x) * sin(q.y)));
    }
    return clamp(c - 0.3, 0.0, 1.0);
}

vec3 gravelColour(vec2 p) {
    vec2 g = p * 16.0;
    vec2 i = floor(g), f = fract(g);
    float best = 9.0, edge = 9.0;
    vec2 id = i;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 o = vec2(x, y);
            vec2 r = o + hash22(i + o) * 0.8 + 0.1 - f;
            float d = dot(r, r);
            if (d < best) { edge = best; best = d; id = i + o; }
            else if (d < edge) edge = d;
        }
    }
    float h = hash12(id * 1.7);
    vec3 c;
    if (gShape == 0) { // (bright aquarium gravel)
        c = h < 0.25 ? vec3(0.95, 0.45, 0.6) : h < 0.5 ? vec3(0.35, 0.6, 0.95) : h < 0.75 ? vec3(0.95, 0.9, 0.85) : vec3(0.95, 0.8, 0.3);
    } else {
        c = mix(vec3(0.42, 0.36, 0.28), vec3(0.78, 0.72, 0.6), h);
    }
    float rim = smoothstep(0.0, 0.25, sqrt(edge) - sqrt(best));
    return c * (0.55 + 0.45 * rim);
}

// the slim tank's backdrop: a painted underwater scene
vec3 backdrop(vec3 p) {
    float yy = p.y / gH;
    vec3 c = mix(vec3(0.05, 0.22, 0.32), vec3(0.3, 0.62, 0.72), yy);
    float hills = 0.25 + 0.1 * sin(p.x * 3.0 + extra.x * 9.0) + 0.06 * sin(p.x * 7.3);
    c = mix(c, vec3(0.08, 0.2, 0.16), smoothstep(hills + 0.01, hills - 0.01, yy) * 0.7);
    float weed = smoothstep(0.55, 0.5, abs(fract(p.x * 5.0 + 0.3 * sin(p.y * 4.0)) - 0.5) * 2.0) * step(yy, 0.45 + 0.3 * hash11(floor(p.x * 5.0)));
    c = mix(c, vec3(0.1, 0.3, 0.15), weed * 0.5);
    return c;
}

void main() {
    gShape = int(info.x + 0.5);
    gW = info.y;
    gH = info.z;
    float t = TankTime + extra.x * 60.0;
    float light = clamp(extra.y, 0.0, 1.0);
    float lit = gShape == 0 ? max(light, 0.22) : max(light, 0.62); // (the tanks have a lamp; the bowl doesn't)

    vec3 ro = lp;
    vec3 rd = normalize(lray);

    // the slim tank's frame: where the box's face isn't the glass front
    if (gShape == 2 && (lp.x < 0.0625 || lp.x > gW - 0.0625 || lp.y < 0.0625 || lp.y > gH - 0.0625 || lp.z < SLIM_D - 0.002)) {
        vec3 n = lp.z < SLIM_D - 0.002 ? (lp.x < 0.01 ? vec3(-1, 0, 0) : lp.x > gW - 0.01 ? vec3(1, 0, 0) : lp.y > gH - 0.01 ? vec3(0, 1, 0) : vec3(0, -1, 0)) : vec3(0, 0, 1);
        float sh = 0.55 + 0.45 * max(dot(n, normalize(vec3(0.3, 1.0, 0.6))), 0.0);
        vec3 metal = vec3(0.13, 0.13, 0.14) * sh * max(light, 0.25) + vec3(0.25) * pow(max(dot(reflect(rd, n), normalize(vec3(0.3, 1.0, 0.6))), 0.0), 30.0);
        fragColor = vec4(metal, 1.0);
        return;
    }

    vec2 tc = container(ro, rd);
    float t0 = max(tc.x, 0.0), t1 = tc.y;
    if (t1 <= t0) {
        discard;
    }
    float wt = waterTop(), fy = floorY();

    // ---- what the ray meets first: the gravel, the back, a plant, a fish ----
    float hitT = 1e9;
    int what = 0; // 1 gravel, 2 back, 3 plant, 4 fish
    if (rd.y < 0.0) {
        float tf = (fy - ro.y) / rd.y;
        if (tf > t0 && tf < t1) { hitT = tf; what = 1; }
    }
    if (gShape == 2 && t1 < hitT) { hitT = t1; what = 2; }
    if (gShape != 2 && (ro + rd * t0).y < fy) { hitT = t0; what = 1; } // (seen through the glass, into the gravel)
    // (the bowl's bottom inside the gravel: gravel too)
    if (gShape != 2 && what == 0) {
        vec3 pe = ro + rd * t1;
        if (pe.y < fy + 0.005) { hitT = t1; what = 1; }
    }
    // plants: traced through the water
    float plantAlong = 0.0;
    {
        float tm = t0, tEnd = min(hitT, t1);
        for (int k = 0; k < 48; k++) {
            if (tm >= tEnd) break;
            vec3 p = ro + rd * tm;
            if (p.y > wt + 0.05) { // (above the plants, rising: done; else skip down)
                if (rd.y >= 0.0) break;
                tm += max((p.y - wt) / -rd.y, 0.01);
                continue;
            }
            float along;
            float d = plantSDF(p, t, along);
            if (d < 0.0015) { hitT = tm; what = 3; plantAlong = along; break; }
            tm += max(d * 0.85, 0.002);
        }
    }
    // fish: each traced only as far as the nearest found so far
    int fishI = -1;
    {
        int n = fishCount();
        for (int i = 0; i < 12; i++) {
            if (i >= n) break;
            int kind;
            vec3 c, fwd, up, side;
            float size;
            tankFish(i, t, kind, c, fwd, up, side, size);
            Species sp = species(kind);
            float reach = size * (length(sp.body) + sp.tail.x * 2.5 + sp.tail.y * (kind == 15 ? 2.5 : 0.6) + sp.fin.z + sp.fin.y + 0.03);
            vec3 oc = ro - c;
            float bq = dot(oc, rd), cq = dot(oc, oc) - reach * reach, disc = bq * bq - cq;
            if (disc < 0.0) continue;
            float f0 = max(-bq - sqrt(disc), t0), f1 = min(-bq + sqrt(disc), min(hitT, t1));
            if (f0 >= f1) continue;
            vec3 lo = vec3(dot(oc, fwd), dot(oc, up), dot(oc, side)) / size;
            vec3 ld = vec3(dot(rd, fwd), dot(rd, up), dot(rd, side));
            float wave, flap;
            tankSwim(i, kind, sp, t, wave, flap);
            float tm = f0;
            int part;
            vec3 q;
            for (int k = 0; k < 64; k++) {
                float dd = fishSDF(kind, sp, lo + ld * (tm / size), wave, flap, gSqueeze, part, q) * size;
                if (dd < 0.0006) { hitT = tm; what = 4; fishI = i; break; }
                tm += max(dd * 0.75, 0.0006);
                if (tm > f1) break;
            }
        }
    }

    vec3 sunDir = normalize(vec3(0.25, 1.0, 0.35));
    vec3 col = vec3(0.0);
    float alpha = 0.0;
    vec3 hitCol = vec3(0.0);
    bool opaque = what != 0;
    float tEnd = opaque ? hitT : t1;
    vec3 hp = ro + rd * tEnd;
    if (what == 1) {
        hitCol = gravelColour(hp.xz) * (0.7 + 0.6 * caustic(hp.xz, t) * smoothstep(0.0, 0.6, wt - fy)) * lit;
    } else if (what == 2) {
        vec3 b = backdrop(hp);
        if (hp.z > 0.03) b *= 0.55; // (the tank's inner sides and bottom)
        hitCol = b * lit;
    } else if (what == 3) {
        vec3 g = mix(vec3(0.08, 0.3, 0.07), vec3(0.35, 0.72, 0.22), plantAlong);
        hitCol = g * (0.65 + 0.35 * plantAlong + 0.3 * caustic(hp.xz, t)) * lit;
    } else if (what == 4) {
        int kind;
        vec3 c, fwd, up, side;
        float size;
        tankFish(fishI, t, kind, c, fwd, up, side, size);
        Species sp = species(kind);
        float wave, flap;
        tankSwim(fishI, kind, sp, t, wave, flap);
        float fi = float(fishI);
        vec3 oc = ro - c;
        vec3 lo = vec3(dot(oc, fwd), dot(oc, up), dot(oc, side)) / size;
        vec3 ld = vec3(dot(rd, fwd), dot(rd, up), dot(rd, side));
        vec3 pl = lo + ld * (hitT / size);
        int part;
        vec3 q;
        fishSDF(kind, sp, pl, wave, flap, gSqueeze, part, q);
        float ee = max(0.0007, 0.008 * min(sp.body.x, sp.body.y));
        vec2 e = vec2(ee, -ee);
        int dt2;
        vec3 dq;
        vec3 g = e.xyy * fishSDF(kind, sp, pl + e.xyy, wave, flap, gSqueeze, dt2, dq) + e.yyx * fishSDF(kind, sp, pl + e.yyx, wave, flap, gSqueeze, dt2, dq)
               + e.yxy * fishSDF(kind, sp, pl + e.yxy, wave, flap, gSqueeze, dt2, dq) + e.xxx * fishSDF(kind, sp, pl + e.xxx, wave, flap, gSqueeze, dt2, dq);
        vec3 n = normalize(fwd * g.x + up * g.y + side * g.z);
        vec3 base = fishColour(kind, q, part, hash11(fi * 13.3 + extra.x * 5.0));
        bool isEye = false;
        if (isFish(kind) && part == 0 && abs(q.z) > 0.55) {
            float er = sp.body.y * eyeScale(kind);
            vec2 ev = vec2((q.x - eyeAt(kind).x) * sp.body.x, (q.y - eyeAt(kind).y) * sp.body.y) / er;
            float eyeD = length(ev);
            if (eyeD < 0.115) { isEye = true; base = eyeD < 0.07 ? vec3(0.015) : eyeRing(kind); }
            else if (eyeD < 0.13) base *= 0.6;
        }
        float diff = clamp(dot(n, sunDir) * 0.5 + 0.5, 0.0, 1.0);
        vec3 amb = mix(vec3(0.22, 0.32, 0.4), vec3(0.6, 0.75, 0.85), n.y * 0.5 + 0.5);
        vec3 shade = base * (amb * 0.85 + vec3(1.0, 0.97, 0.9) * diff * 0.8);
        shade += vec3(1.0) * pow(max(dot(n, normalize(sunDir - rd)), 0.0), isEye ? 120.0 : 40.0) * (isEye ? 1.2 : 0.4);
        shade += vec3(0.5, 0.8, 1.0) * pow(1.0 - max(dot(n, -rd), 0.0), 3.0) * 0.2;
        shade += vec3(0.6, 0.9, 1.0) * caustic(hp.xz, t) * max(n.y, 0.0) * 0.3;
        if (kind == 3 && part == 0 && abs(q.y - 0.12) < 0.13) shade += vec3(0.1, 0.6, 0.8) * 0.6; // (the neon stripe)
        hitCol = shade * lit;
    }

    // ---- the water between: how much of the way is under the surface ----
    float under = 0.0;
    {
        float a = t0, b = tEnd;
        float tw = abs(rd.y) > 1e-5 ? (wt - ro.y) / rd.y : 1e9;
        if (ro.y + rd.y * a > wt) a = rd.y < 0.0 ? max(a, tw) : b;
        if (ro.y + rd.y * b > wt) b = rd.y > 0.0 ? min(b, tw) : a;
        under = max(b - a, 0.0);
    }
    vec3 tint = gShape == 2 && int(info.w + 0.5) == 2 ? vec3(0.25, 0.6, 0.7) : vec3(0.42, 0.66, 0.6);
    float absorb = gShape == 2 ? 2.2 : gShape == 1 ? 1.4 / pow(gW, 0.8) : 1.4; // (a wide tank no murkier for being wide)
    float T = exp(-under * absorb);
    vec3 waterCol = tint * lit * 0.55;
    if (opaque) {
        col = hitCol * T + waterCol * (1.0 - T);
        alpha = 1.0;
    } else {
        col = waterCol * (1.0 - T);
        alpha = (1.0 - T) * 0.85;
    }

    // ---- the surface, crossed on the way (from above: sky in it, ripples; from below: silvery) ----
    if (abs(rd.y) > 1e-4) {
        float ts = (wt - ro.y) / rd.y;
        if (ts > t0 && ts < tEnd) {
            vec3 sp = ro + rd * ts;
            vec2 rip = vec2(sin(sp.x * 40.0 + t * 2.0) + sin(sp.z * 33.0 - t * 1.7), cos(sp.z * 37.0 + t * 1.9) + cos(sp.x * 29.0 - t * 2.3)) * 0.04;
            vec3 n = normalize(vec3(rip.x, 1.0, rip.y));
            float f = 0.03 + 0.97 * pow(1.0 - abs(dot(n, rd)), 5.0);
            if (rd.y < 0.0) {
                vec3 sky = mix(vec3(0.55, 0.65, 0.75), vec3(0.85, 0.92, 1.0), max(reflect(rd, n).y, 0.0)) * lit;
                col = mix(col, sky, f * 0.6) + vec3(1.0) * pow(max(dot(reflect(rd, n), sunDir), 0.0), 80.0) * 0.5 * lit;
                alpha = max(alpha, f * 0.6 + 0.1);
            } else {
                col = mix(col, vec3(0.6, 0.8, 0.8) * lit, 0.35 + 0.4 * f);
                alpha = max(alpha, 0.5);
            }
        }
    }

    // ---- an air stone's bubbles, rising up the middle (the tall and slim tanks) ----
    if (gShape != 0) {
        vec3 src = gShape == 1 ? vec3(cylC().x, fy, cylC().y) : vec3(gW * 0.15 + 0.1, fy, SLIM_D * 0.4);
        float height = wt - fy;
        for (int k = 0; k < 10; k++) {
            float fk = float(k);
            float rise = fract(t * (0.18 + 0.05 * hash11(fk)) / max(height, 0.3) + hash11(fk * 7.3));
            vec3 bc = src + vec3(0.02 * sin(t * 3.0 + fk * 2.0), rise * height, 0.02 * cos(t * 2.5 + fk));
            float br = 0.008 + 0.006 * hash11(fk * 3.1);
            vec3 oc = ro - bc;
            float b = dot(oc, rd), c = dot(oc, oc) - br * br, h = b * b - c;
            if (h > 0.0) {
                float tb = -b - sqrt(h);
                if (tb > t0 && tb < tEnd) {
                    vec3 bn = normalize(oc + rd * tb);
                    float rim = pow(1.0 - abs(dot(bn, rd)), 2.0);
                    col += vec3(0.8, 0.95, 1.0) * (0.15 + 0.6 * rim) * lit;
                    alpha = max(alpha, 0.25 + 0.5 * rim);
                }
            }
        }
    }

    // ---- the glass: a sheen, a highlight ----
    {
        vec3 pe = ro + rd * t0;
        bool onGlass = gShape == 2 || (gShape == 0 ? pe.y < 0.69 : (pe.y > 0.13 && pe.y < gH - 0.13));
        if (onGlass) {
            vec3 n = glassNormal(pe);
            float cosv = abs(dot(n, rd));
            float fres = 0.04 + 0.5 * pow(1.0 - cosv, 4.0);
            vec3 rf = reflect(rd, n);
            vec3 sky = mix(vec3(0.3, 0.32, 0.35), vec3(0.8, 0.88, 1.0), smoothstep(-0.2, 0.6, rf.y)) * max(light, 0.3);
            float spec = pow(max(dot(rf, normalize(vec3(0.4, 1.0, 0.3))), 0.0), 60.0);
            col = col * (1.0 - fres) + sky * fres + vec3(spec) * 0.7 * max(light, 0.3);
            alpha = clamp(alpha + fres * (1.0 - alpha) + spec * 0.5, 0.0, 1.0);
            alpha = max(alpha, 0.06);
            if (gShape == 0) { // (the bowl's rim: a thicker lip of glass)
                float lip = smoothstep(0.66, 0.69, pe.y);
                col += vec3(0.6, 0.7, 0.75) * lip * 0.4 * max(light, 0.3);
                alpha = max(alpha, lip * 0.5);
            }
        }
    }
    fragColor = vec4(clamp(col, 0.0, 1.0), clamp(alpha, 0.0, 1.0));
}
