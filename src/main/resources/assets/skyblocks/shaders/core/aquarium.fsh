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

in vec3 fragPos;

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

void main() {
    vec3 ro = Eye;
    vec3 rd = normalize(fragPos - Eye);
    vec3 bmax = BoxMin + BoxSize;
    vec3 inv = 1.0 / rd;
    vec3 ta = (BoxMin - ro) * inv, tb = (bmax - ro) * inv;
    vec3 tsmall = min(ta, tb), tbig = max(ta, tb);
    float tEnter = max(max(max(tsmall.x, tsmall.y), tsmall.z), 0.0);
    float tExit = min(min(tbig.x, tbig.y), tbig.z);
    tEnter = max(tEnter, length(fragPos - Eye) - 0.002);
    vec3 entry = ro + rd * tEnter - BoxMin; // (tank coordinates, 0..BoxSize)

    float surface = BoxSize.y - 0.06;
    float t = Time;
    vec3 col;
    float hitT = tExit;
    vec3 hitPos = ro + rd * tExit - BoxMin;

    // ---- the fish ----
    float volume = BoxSize.x * BoxSize.y * BoxSize.z;
    int count = int(clamp(volume * 1.6, 3.0, 14.0));
    vec3 fishCol = vec3(0.0);
    bool fish = false;
    for (int i = 0; i < 14; i++) {
        if (i >= count) {
            break;
        }
        float fi = float(i);
        float size = 0.65 + 0.6 * hash11(fi * 3.1);
        if (min(BoxSize.x, BoxSize.z) < 1.5) size *= 0.75;
        float speed = 0.25 + 0.35 * hash11(fi * 5.7);
        vec3 half_ = BoxSize * 0.5;
        vec3 range = max(half_ - vec3(0.22 * size + 0.06), vec3(0.02));
        float a1 = t * speed + hash11(fi) * 6.2831, a2 = t * speed * 0.73 + hash11(fi + 9.0) * 6.2831;
        float level = mix(0.25, BoxSize.y - 0.3, hash11(fi * 2.3));
        vec3 c = vec3(half_.x + range.x * sin(a1), clamp(level + 0.08 * sin(t * 0.7 + fi), 0.2, BoxSize.y - 0.2),
                      half_.z + range.z * sin(a2));
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
        float sand = 0.12 + 0.03 * sin(p.x * 3.0) * sin(p.z * 2.7);
        if (rd.y < 0.0) {
            float ts = (BoxMin.y + sand - ro.y) / rd.y;
            if (ts > tEnter && ts < hitT) {
                hitT = ts;
                p = ro - BoxMin + rd * ts;
            }
        }
        if (p.y <= sand + 0.02) {
            // sand with pebbles, lit by the moving light patterns
            vec2 cell = floor(p.xz * 9.0);
            float pebble = hash22(cell).x > 0.93 ? 0.7 : 1.0;
            col = vec3(0.85, 0.75, 0.52) * pebble * (0.75 + 0.1 * hash22(floor(p.xz * 40.0)).x);
            col += vec3(0.6, 0.85, 1.0) * caustics(p.xz, t) * 0.55;
        } else if (p.y >= surface - 0.02) {
            // the underside of the surface: bright, rippling
            col = vec3(0.35, 0.75, 0.85) + vec3(0.5, 0.8, 0.9) * caustics(p.xz * 1.3, t * 1.4) * 0.6;
        } else {
            // the back glass: deep water, seaweed swaying on it, darker lower down
            vec2 wall = abs(p.x) < 0.01 || abs(p.x - BoxSize.x) < 0.01 ? vec2(p.z, p.y) : vec2(p.x, p.y);
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
    vec3 water = mix(vec3(0.02, 0.2, 0.32), vec3(0.15, 0.55, 0.62), clamp(entry.y / BoxSize.y, 0.0, 1.0));
    col = mix(col, water, 1.0 - exp(-travel * 0.38));
    col += vec3(0.25, 0.5, 0.55) * 0.08 * (0.5 + 0.5 * sin(entry.x * 4.0 + entry.z * 3.0 + entry.y * 2.0 + t * 1.5));

    // looking down through the top: the surface ripples catch the light
    if (abs(entry.y - BoxSize.y) < 0.01) {
        col += vec3(0.8, 0.95, 1.0) * caustics(entry.xz * 1.6 + vec2(t * 0.1, 0.0), t * 1.6) * 0.18;
    }

    // ---- the frame round the tank's edges ----
    vec3 e = min(entry, BoxSize - entry);
    float edgeA = min(e.x, min(e.y, e.z));
    float edgeB = e.x + e.y + e.z - edgeA - max(e.x, max(e.y, e.z)); // the second smallest
    if (edgeB < 0.05) {
        col = vec3(0.08, 0.08, 0.09) + 0.05 * (1.0 - edgeB / 0.05);
    }
    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
