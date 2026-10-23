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
uniform float FishCount;

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

// a fish's colours at a point on it (q: its own coordinates, x along it nose-first, -1..1)
vec3 fishColour(int kind, vec3 q, bool tail) {
    if (kind == 0) { // clownfish: orange, white bands edged black
        float b = min(abs(q.x - 0.45), min(abs(q.x + 0.05), abs(q.x + 0.55)));
        vec3 c = b < 0.09 ? vec3(0.95) : b < 0.13 ? vec3(0.05) : vec3(1.0, 0.42, 0.05);
        return tail ? vec3(1.0, 0.42, 0.05) : c;
    }
    if (kind == 1) { // blue tang: blue, dark mark, yellow tail
        if (tail) return vec3(1.0, 0.85, 0.1);
        return q.y > 0.2 && abs(q.x) < 0.6 ? vec3(0.03, 0.06, 0.2) : vec3(0.1, 0.35, 0.95);
    }
    if (kind == 2) { // yellow tang
        return vec3(1.0, 0.86, 0.1) * (tail ? 0.9 : 1.0);
    }
    // neon tetra: a glowing blue line, red below behind, silver
    if (abs(q.y - 0.15) < 0.18 && !tail) return vec3(0.1, 0.9, 1.0) * 1.3;
    if (q.y < -0.05 && q.x < 0.2 && !tail) return vec3(0.9, 0.1, 0.15);
    return vec3(0.75, 0.78, 0.85);
}

vec4 fishData(int k) {
    return k == 0 ? Fish0 : k == 1 ? Fish1 : k == 2 ? Fish2 : k == 3 ? Fish3 : k == 4 ? Fish4 : k == 5 ? Fish5 : k == 6 ? Fish6 : k == 7 ? Fish7 : k == 8 ? Fish8 : k == 9 ? Fish9 : k == 10 ? Fish10 : k == 11 ? Fish11 : k == 12 ? Fish12 : k == 13 ? Fish13 : k == 14 ? Fish14 : k == 15 ? Fish15 : k == 16 ? Fish16 : k == 17 ? Fish17 : k == 18 ? Fish18 : k == 19 ? Fish19 : k == 20 ? Fish20 : k == 21 ? Fish21 : k == 22 ? Fish22 : k == 23 ? Fish23 : k == 24 ? Fish24 : k == 25 ? Fish25 : k == 26 ? Fish26 : Fish27;
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
    for (int i = 0; i < 14; i++) {
        if (i >= count) {
            break;
        }
        float fi = float(i);
        // its own stretch of water (worked out on the CPU from the tank's shape)
        vec3 middle = fishData(i * 2).xyz, reach = fishData(i * 2 + 1).xyz;
        float size = 0.65 + 0.6 * hash11(fi * 3.1);
        if (min(reach.x, reach.z) < 0.75) size *= 0.75;
        float speed = 0.25 + 0.35 * hash11(fi * 5.7);
        vec3 range = max(reach - vec3(0.22 * size + 0.06), vec3(0.02));
        float a1 = t * speed + hash11(fi) * 6.2831, a2 = t * speed * 0.73 + hash11(fi + 9.0) * 6.2831;
        float level = middle.y + (hash11(fi * 2.3) * 2.0 - 1.0) * max(reach.y - 0.3, 0.0);
        vec3 c = vec3(middle.x + range.x * sin(a1), clamp(level + 0.08 * sin(t * 0.7 + fi), middle.y - reach.y + 0.2, middle.y + reach.y - 0.2),
                      middle.z + range.z * sin(a2));
        vec3 vel = vec3(range.x * cos(a1) * speed, 0.0, range.z * cos(a2) * speed * 0.73);
        vec3 fwd = normalize(vel + vec3(1.0e-4, 0.0, 0.0));
        vec3 up = vec3(0.0, 1.0, 0.0);
        vec3 side = normalize(cross(fwd, up));
        float beat = sin(t * (7.0 + 4.0 * speed) + fi * 2.0);
        // a little body sway with the tail
        vec3 bodyFwd = normalize(fwd + side * beat * 0.08);
        vec3 n, q;
        float tf = ellipsoid(ro - BoxMin, rd, c, bodyFwd, up, normalize(cross(bodyFwd, up)), vec3(0.16, 0.075, 0.035) * size, n, q);
        bool tail = false;
        vec3 tailDir = normalize(-fwd + side * beat * 0.5);
        vec3 tc = c - fwd * 0.17 * size + side * beat * 0.03 * size;
        vec3 n2, q2;
        float tt = ellipsoid(ro - BoxMin, rd, tc, tailDir, up, normalize(cross(tailDir, up)), vec3(0.06, 0.065, 0.01) * size, n2, q2);
        if (tt > 0.0 && (tf < 0.0 || tt < tf)) {
            tf = tt; n = n2; q = q2; tail = true;
        }
        if (tf > 0.0 && tf > tEnter - 0.01 && tf < hitT) {
            hitT = tf;
            fish = true;
            int kind = int(hash11(fi * 7.7) * 4.0);
            vec3 base = fishColour(kind, q, tail);
            // the eye
            if (!tail && q.x > 0.55 && abs(q.y - 0.15) < 0.18 && abs(q.z) > 0.6) {
                base = vec3(0.02);
            }
            vec3 p = ro - BoxMin + rd * tf;
            float light = 0.45 + 0.55 * max(n.y, 0.0) + 0.25 * max(dot(n, -rd), 0.0);
            float caust = caustics(p.xz, t) * max(n.y, 0.0) * (p.y / BoxSize.y);
            fishCol = base * light + vec3(0.6, 0.9, 1.0) * caust * 0.5;
            fishCol += vec3(1.0) * pow(max(dot(reflect(rd, n), vec3(0.0, 1.0, 0.0)), 0.0), 24.0) * 0.4; // (shiny)
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
    if (fish) {
        col = fishCol;
    } else {
        vec3 p = hitPos;
        bool onSand = false;
        if (rd.y < 0.0) {
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
            col = vec3(0.85, 0.75, 0.52) * pebble * (0.75 + 0.1 * hash22(floor(p.xz * 40.0)).x);
            col += vec3(0.6, 0.85, 1.0) * caustics(p.xz, t) * 0.55;
        } else if (up) {
            // the underside of the surface: bright, rippling
            col = vec3(0.35, 0.75, 0.85) + vec3(0.5, 0.8, 0.9) * caustics(p.xz * 1.3, t * 1.4) * 0.6;
        } else {
            // the back glass: deep water, seaweed swaying on it, darker lower down
            vec2 wall = exitAxis == 0 ? vec2(p.z, p.y) : vec2(p.x, p.y);
            col = mix(vec3(0.02, 0.18, 0.3), vec3(0.1, 0.45, 0.55), p.y / BoxSize.y);
            for (int k = 0; k < 3; k++) {
                float fk = float(k);
                float spacing = 0.55 + 0.2 * fk;
                float xi = floor(wall.x / spacing);
                float hgt = (0.35 + 0.45 * hash11(xi * 1.3 + fk * 17.0)) * min(BoxSize.y, 2.5);
                float sway = 0.08 * sin(t * (0.8 + 0.3 * fk) + wall.y * 3.0 + xi) * (wall.y / max(hgt, 0.1));
                float dx = abs(fract(wall.x / spacing) * spacing - spacing * 0.5 - sway);
                float w = 0.035 * (1.0 - wall.y / max(hgt, 0.1)) + 0.008;
                if (wall.y < hgt && dx < w && hash11(xi * 3.7 + fk) > 0.35) {
                    col = mix(vec3(0.05, 0.35, 0.12), vec3(0.2, 0.6, 0.2), wall.y / hgt) * (0.6 + 0.2 * fk);
                }
            }
        }
    }
    if (bubbleT < hitT) {
        col = mix(col, bubbleCol, 0.7);
        hitT = bubbleT;
    }

    // ---- the water between: deeper colour with distance, lighter near the top, a soft shimmer ----
    float travel = max(hitT - tEnter, 0.0);
    vec3 waterCol = mix(vec3(0.02, 0.2, 0.32), vec3(0.15, 0.55, 0.62), clamp(entry.y / BoxSize.y, 0.0, 1.0));
    col = mix(col, waterCol, 1.0 - exp(-travel * 0.38));
    col += vec3(0.25, 0.5, 0.55) * 0.08 * (0.5 + 0.5 * sin(entry.x * 4.0 + entry.z * 3.0 + entry.y * 2.0 + t * 1.5));

    // looking down through the top: the surface ripples catch the light
    if (abs(entry.y - floor(entry.y + 0.5)) < 0.01 && rd.y < 0.0 && !water(ivec3(floor(entry + vec3(0.0, 0.01, 0.0))))) {
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
