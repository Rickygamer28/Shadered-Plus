#version 150

// A train car, traced in its own space (x along it - its nose +x - y up from the rails' middle, z across), 12 blocks
// long, matching its solid walls (TrainEntity.boxes):
//  - a body: its floor's top at FL, walls round a corridor 3 wide, a ceiling at CE with a strip of light along it;
//    sliding doors in its sides (two leaves each, open as 'door'), windows along it, a gangway at each end it joins
//    another car by; seats in rows either side of the corridor.
//  - maglev (0): white, rounded, a cyan line along it, a skirt over the rails glowing underneath; its cabs (the
//    locomotive, and a cab car at its other end) a sloped nose, a windscreen, a desk and the driver's seat.
//  - steam (1): maroon and cream carriages on bogies, a raised roof; the locomotive a black boiler, chimney and dome,
//    red driving wheels turning as it goes, and its cab behind, open at the sides.
// Glass is seen through both ways, streaked as it goes faster. It writes its depth: what's inside shows through.

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

in vec3 lp;
flat in vec3 eye;
flat in vec3 centre;
flat in vec3 F;
flat in vec3 U;
flat in vec3 S;
flat in int style;
flat in int kind;
flat in int flags;
flat in float door;
flat in float speed;
flat in float travel;
flat in float lit;

out vec4 fragColor;

const vec3 LO = vec3(-7.0, -0.15, -1.85), HI = vec3(7.3, 5.2, 1.85);
const float HL = 6.0, FL = 0.9, IW = 1.5, OW = 1.65, CE = 4.1, RT = 4.35;
const float DX = 3.6, DH = 0.7, DHT = 2.4, GH = 0.6, GHT = 2.3, GOUT = 0.33;

float T;

float hash1(float n) {
    return fract(sin(n * 91.345) * 47453.5453);
}

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float sdRoundBox(vec3 p, vec3 b, float r) {
    vec3 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r;
}

// a box between two corners
float box2(vec3 p, vec3 a, vec3 b) {
    return sdBox(p - (a + b) * 0.5, abs(b - a) * 0.5);
}

float cylX(vec3 p, vec2 c, float r, float x0, float h) {
    vec2 d = vec2(length(p.yz - c) - r, abs(p.x - x0) - h);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}

float cylY(vec3 p, vec2 c, float r, float y0, float h) {
    vec2 d = vec2(length(p.xz - c) - r, abs(p.y - y0) - h);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}

float cylZ(vec3 p, vec2 c, float r, float z0, float h) {
    vec2 d = vec2(length(p.xy - c) - r, abs(p.z - z0) - h);
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0));
}

bool steamLoco() {
    return style == 1 && kind == 0;
}

bool cab() {
    return kind != 1 && style == 0;
}

bool gangP() {
    return (flags & 1) != 0;
}

bool gangM() {
    return (flags & 2) != 0;
}

// ---- the body's outside, and the room inside it ----
float outer(vec3 p) {
    if (steamLoco()) {
        return box2(p, vec3(-HL, 0.55, -OW), vec3(-1.2, CE + 0.2, OW));
    }
    if (style == 0) {
        float x1 = cab() ? HL + 1.1 : HL;
        float d = sdRoundBox(p - vec3((x1 - HL) * 0.5, (0.2 + RT) * 0.5, 0.0), vec3((x1 + HL) * 0.5, (RT - 0.2) * 0.5, OW), 0.45);
        if (cab()) {
            d = max(d, dot(p - vec3(HL - 1.3, RT, 0.0), normalize(vec3(1.05, 1.0, 0.0))));
            d = max(d, dot(p - vec3(HL + 1.1, 0.6, 0.0), normalize(vec3(1.0, -0.8, 0.0))));
        }
        return d;
    }
    float b = sdRoundBox(p - vec3(0.0, (0.55 + CE + 0.1) * 0.5, 0.0), vec3(HL, (CE + 0.1 - 0.55) * 0.5, OW), 0.1);
    float clere = box2(p, vec3(-HL + 0.6, CE, -0.85), vec3(HL - 0.6, CE + 0.45, 0.85));
    return min(b, clere);
}

float inner(vec3 p) {
    if (steamLoco()) {
        return box2(p, vec3(-HL + 0.15, FL, -IW), vec3(-1.35, CE, IW));
    }
    return box2(p, vec3(-HL + 0.15, FL, -IW), vec3(HL - 0.15, CE, IW));
}

// openings through the skin: doors (where they've slid back), gangways, the steam cab's open sides
float openings(vec3 p) {
    float d = 1e3;
    if (steamLoco()) {
        d = box2(p, vec3(-4.6, FL, -OW - 0.5), vec3(-2.2, FL + DHT, OW + 0.5));
        if (gangM()) d = min(d, box2(p, vec3(-HL - 0.5, FL, -GH), vec3(-HL + 0.5, FL + GHT, GH)));
        return d;
    }
    d = box2(p, vec3(-DX - DH, FL, -OW - 0.5), vec3(-DX + DH, FL + DHT, OW + 0.5));
    if (kind == 1) d = min(d, box2(p, vec3(DX - DH, FL, -OW - 0.5), vec3(DX + DH, FL + DHT, OW + 0.5)));
    if (gangM()) d = min(d, box2(p, vec3(-HL - 0.5, FL, -GH), vec3(-HL + 0.5, FL + GHT, GH)));
    if (gangP()) d = min(d, box2(p, vec3(HL - 0.5, FL, -GH), vec3(HL + 0.5, FL + GHT, GH)));
    return d;
}

// where its windows are (on its skin)
bool window(vec3 p) {
    float az = abs(p.z);
    if (steamLoco()) {
        if (p.y < FL + 1.3 || p.y > FL + 2.4) return false;
        if (az > IW - 0.05) return p.x < -4.75 && p.x > -5.7;
        return p.x > -1.5 && az > 0.35 && az < 1.3;
    }
    if (cab() && p.x > HL - 1.35 && p.y > FL + 1.3) {
        return az < OW - 0.25; // (its windscreen)
    }
    if (az < IW - 0.05 || p.y < FL + 1.05 || p.y > FL + 2.25) return false;
    float ax = abs(p.x);
    if (abs(p.x + DX) < DH + 0.2 || (kind == 1 && abs(p.x - DX) < DH + 0.2) || ax > HL - 0.45) return false;
    if (cab() && p.x > 2.4) return p.x < HL - 1.6;
    float pitch = style == 0 ? 1.2 : 0.9;
    float c = fract(p.x / pitch + 0.5) - 0.5;
    return abs(c) < (style == 0 ? 0.44 : 0.36);
}

// seats: their x along it (a cab: fewer, and the driver's)
float seats(vec3 p) {
    if (p.y > FL + 1.35 || steamLoco()) {
        if (steamLoco()) {
            return min(box2(p, vec3(-3.35, FL, 0.25), vec3(-2.65, FL + 0.45, 0.95)), box2(p, vec3(-3.4, FL + 0.45, 0.25), vec3(-3.3, FL + 1.2, 0.95)));
        }
        return 1e3;
    }
    float d = 1e3;
    vec3 q = vec3(p.x, p.y, abs(p.z));
    for (int i = 0; i < 6; i++) {
        float sx = i == 0 ? -5.1 : i == 1 ? -1.8 : i == 2 ? -0.6 : i == 3 ? 0.6 : i == 4 ? 1.8 : 5.1;
        if (i == 5 && kind != 1) break;
        float back = (i == 0 || i == 2 || i == 4) ? -1.0 : 1.0;
        if (i == 5) back = 1.0;
        if (i == 0) back = -1.0;
        float base = box2(q, vec3(sx - 0.4, FL, 0.55), vec3(sx + 0.4, FL + 0.45, 1.45));
        float bk = box2(q, vec3(sx + back * 0.36 - 0.06, FL + 0.45, 0.55), vec3(sx + back * 0.36 + 0.06, FL + 1.3, 1.45));
        d = min(d, min(base, bk));
    }
    if (kind != 1) {
        d = min(d, box2(p, vec3(3.6, FL, -0.35), vec3(4.4, FL + 0.45, 0.35)));
        d = min(d, box2(p, vec3(3.55, FL + 0.45, -0.35), vec3(3.67, FL + 1.3, 0.35)));
    }
    return d;
}

// what's solid, and which: 1 skin, 2 seats, 3 under, 4 wheels, 5 boiler, 6 black metal, 7 brass, 8 lamp, 9 door leaves, 10 desk / bulkhead
float solid(vec3 p, out int mat) {
    float o = outer(p);
    float sh = max(o, -inner(p));
    sh = max(sh, -openings(p));
    if (sh < 0.05 && window(p)) sh = max(sh, 0.06);
    float d = sh;
    mat = 1;
    // door leaves: two each side, slid apart
    if (!steamLoco()) {
        float az = abs(p.z);
        for (int i = 0; i < 2; i++) {
            float dc = i == 0 ? -DX : DX;
            if (i == 1 && kind != 1) break;
            float shift = door * (DH - 0.05);
            float l1 = box2(vec3(p.x, p.y, az), vec3(dc - DH - shift, FL, IW + 0.02), vec3(dc - shift, FL + DHT, IW + 0.1));
            float l2 = box2(vec3(p.x, p.y, az), vec3(dc + shift, FL, IW + 0.02), vec3(dc + DH + shift, FL + DHT, IW + 0.1));
            float leaves = max(min(l1, l2), box2(vec3(p.x, p.y, az), vec3(dc - DH, FL - 0.1, IW - 0.1), vec3(dc + DH, FL + DHT + 0.1, OW + 0.1)));
            // (a window in each leaf)
            vec3 lq = vec3(p.x, p.y, az);
            if (leaves < d) { d = leaves; mat = 9; }
        }
    }
    float st = seats(p);
    if (st < d) { d = st; mat = 2; }
    if (cab()) {
        float bulk = max(box2(p, vec3(2.6, FL, -IW), vec3(2.75, CE, IW)), -box2(p, vec3(2.5, FL - 0.1, -0.5), vec3(2.85, FL + 2.2, 0.5)));
        float desk = box2(p, vec3(4.9, FL, -1.2), vec3(5.6, FL + 1.0, 1.2));
        float b = min(bulk, desk);
        if (b < d) { d = b; mat = 10; }
    }
    // gangways: the bellows out to the next car
    if (gangP() || gangM()) {
        for (int i = 0; i < 2; i++) {
            bool on = i == 0 ? gangP() : gangM();
            if (!on) continue;
            float x0 = i == 0 ? HL - 0.05 : -HL - GOUT, x1 = i == 0 ? HL + GOUT : -HL + 0.05;
            float g = max(box2(p, vec3(x0, FL - 0.2, -GH - 0.15), vec3(x1, FL + GHT + 0.15, GH + 0.15)),
                          -box2(p, vec3(x0 - 0.1, FL, -GH), vec3(x1 + 0.1, FL + GHT, GH)));
            if (g < d) { d = g; mat = 6; }
        }
    }
    if (style == 0) {
        float sk = box2(p, vec3(-HL + 0.4, 0.12, -1.1), vec3((cab() ? HL + 0.5 : HL - 0.4), 0.35, 1.1));
        if (sk < d) { d = sk; mat = 3; }
    } else if (!steamLoco()) {
        float bg = 1e3, wh = 1e3;
        for (int i = -1; i <= 1; i += 2) {
            bg = min(bg, box2(p, vec3(4.2 * float(i) - 1.0, 0.3, -0.75), vec3(4.2 * float(i) + 1.0, 0.6, 0.75)));
            for (int j = -1; j <= 1; j += 2) {
                vec3 q = p - vec3(4.2 * float(i) + 0.65 * float(j), 0.0, 0.0);
                q.z = abs(q.z);
                wh = min(wh, cylZ(q, vec2(0.0, 0.42), 0.36, 0.82, 0.06));
            }
        }
        if (bg < d) { d = bg; mat = 3; }
        if (wh < d) { d = wh; mat = 4; }
    } else {
        // the steam locomotive's front: boiler, smokebox, chimney, domes, running board, frame, wheels, cowcatcher, lamp
        float boiler = cylX(p, vec2(2.95, 0.0), 1.1, 2.35, 3.55);
        if (boiler < d) { d = boiler; mat = 5; }
        float blk = cylY(p, vec2(HL - 0.6, 0.0), 0.38, 4.4, 0.6);
        blk = min(blk, cylY(p, vec2(HL - 0.6, 0.0), 0.5, 4.95, 0.08));
        blk = min(blk, length(p - vec3(1.6, 4.0, 0.0)) - 0.5);
        blk = min(blk, box2(p, vec3(-1.2, 1.78, -OW), vec3(HL + 0.3, 1.88, OW)));
        blk = min(blk, box2(p, vec3(-HL, 0.35, -0.75), vec3(HL + 0.5, 0.9, 0.75)));
        blk = min(blk, max(box2(p, vec3(HL + 0.3, 0.1, -1.1), vec3(HL + 1.1, 0.9, 1.1)), dot(p - vec3(HL + 0.3, 0.9, 0.0), normalize(vec3(0.8, 0.6, 0.0)))));
        if (blk < d) { d = blk; mat = 6; }
        float brass = min(cylX(p, vec2(2.95, 0.0), 1.13, 0.4, 0.05), cylX(p, vec2(2.95, 0.0), 1.13, 3.4, 0.05));
        brass = min(brass, length(p - vec3(-0.2, 3.95, 0.0)) - 0.36);
        if (brass < d) { d = brass; mat = 7; }
        float wh = 1e3;
        vec3 q = vec3(p.x, p.y, abs(p.z));
        for (int i = 0; i < 3; i++) {
            wh = min(wh, cylZ(q, vec2(0.4 + float(i) * 1.8, 0.95), 0.85, 1.15, 0.08));
        }
        wh = min(wh, cylZ(q, vec2(5.3, 0.5), 0.42, 0.95, 0.07));
        if (wh < d) { d = wh; mat = 4; }
        float lamp = length(p - vec3(HL + 1.2, 2.95, 0.0)) - 0.2;
        if (lamp < d) { d = lamp; mat = 8; }
    }
    return d;
}

float solidOnly(vec3 p) {
    int m;
    return solid(p, m);
}

vec3 solidNormal(vec3 p) {
    vec2 e = vec2(0.003, 0.0);
    return normalize(vec3(solidOnly(p + e.xyy) - solidOnly(p - e.xyy), solidOnly(p + e.yxy) - solidOnly(p - e.yxy), solidOnly(p + e.yyx) - solidOnly(p - e.yyx)));
}

// the glass: a thin sheet in its windows, mid-way through its skin
float glass(vec3 p) {
    float sk = max(outer(p), -inner(p));
    float mid = abs(outer(p) + 0.075) - 0.008;
    if (sk > 0.06 || !window(p)) return max(sk, 0.05);
    return mid;
}

vec3 glassNormal(vec3 p) {
    vec2 e = vec2(0.004, 0.0);
    return normalize(vec3(outer(p + e.xyy) - outer(p - e.xyy), outer(p + e.yxy) - outer(p - e.yxy), outer(p + e.yyx) - outer(p - e.yyx)));
}

vec3 sunDir() {
    vec3 L = normalize(vec3(0.35, 1.0, 0.25));
    return normalize(vec3(dot(L, F), dot(L, U), dot(L, S)));
}

bool isInside(vec3 p) {
    return inner(p) < 0.03 && !(steamLoco() && p.x > -1.2);
}

vec3 shade(vec3 c, vec3 n, vec3 rd, float gloss, bool in_) {
    vec3 L = sunDir();
    float dif = max(dot(n, L), 0.0);
    float amb = 0.45 + 0.25 * n.y;
    vec3 h = normalize(L - rd);
    float sp = pow(max(dot(n, h), 0.0), 40.0) * gloss;
    if (in_) {
        // (inside: its own lights from the ceiling, and some daylight through the windows)
        float lamp = 0.55 + 0.25 * max(-n.y, 0.0) + 0.2 * max(n.y, 0.0);
        return c * (vec3(1.0, 0.93, 0.8) * lamp + vec3(0.25) * lit);
    }
    return (c * (amb + dif * 0.8) + vec3(sp)) * lit;
}

vec3 solidColour(vec3 p, vec3 n, vec3 rd, int mat) {
    bool in_ = isInside(p + n * 0.02);
    if (mat == 2) {
        vec3 c = style == 0 ? vec3(0.13, 0.42, 0.62) : vec3(0.5, 0.1, 0.12);
        return shade(c, n, rd, 0.1, true);
    }
    if (mat == 3) {
        vec3 o = shade(style == 0 ? vec3(0.22, 0.24, 0.28) : vec3(0.08), n, rd, 0.3, false);
        if (style == 0) {
            float band = smoothstep(0.05, 0.0, abs(p.y - 0.2));
            o += vec3(0.2, 0.9, 1.0) * band * (0.8 + 0.2 * sin(T * 4.0 + p.x * 3.0));
        }
        return o;
    }
    if (mat == 4) {
        vec2 c;
        float r;
        if (steamLoco()) {
            float wx = p.x > 4.8 ? 5.3 : 0.4 + floor((p.x - 0.4) / 1.8 + 0.5) * 1.8;
            r = p.x > 4.8 ? 0.42 : 0.85;
            c = vec2(p.x - wx, p.y - (p.x > 4.8 ? 0.5 : 0.95));
        } else {
            float wx = (p.x > 0.0 ? 4.2 : -4.2);
            wx += (p.x > wx ? 0.65 : -0.65);
            r = 0.36;
            c = vec2(p.x - wx, p.y - 0.42);
        }
        float a = atan(c.y, c.x) + travel / r;
        float spoke = smoothstep(0.75, 0.95, cos(a * 10.0));
        float rim = smoothstep(r * 0.8, r * 0.86, length(c));
        vec3 base = steamLoco() ? vec3(0.6, 0.08, 0.06) : vec3(0.14);
        return shade(mix(base * 0.45, base, max(spoke, rim)), n, rd, 0.5, false);
    }
    if (mat == 5) {
        return shade(p.x > HL - 1.0 ? vec3(0.05) : vec3(0.07, 0.08, 0.1), n, rd, 0.9, false);
    }
    if (mat == 6) {
        return shade(vec3(0.07), n, rd, 0.5, in_);
    }
    if (mat == 7) {
        return shade(vec3(0.85, 0.65, 0.25), n, rd, 1.2, false);
    }
    if (mat == 8) {
        return vec3(1.0, 0.93, 0.7) * 1.5;
    }
    if (mat == 9) {
        vec3 c = style == 0 ? vec3(0.75, 0.78, 0.82) : vec3(0.3, 0.06, 0.07);
        float edge = smoothstep(0.03, 0.0, abs(fract((p.x + 0.7) / 0.7) - 0.5) - 0.47);
        // (a door glows round its edge as it opens and shuts)
        vec3 o = shade(c, n, rd, 0.6, in_);
        float moving = door * (1.0 - door) * 4.0;
        return o + (style == 0 ? vec3(0.2, 0.9, 1.0) : vec3(1.0, 0.7, 0.3)) * edge * (0.3 + moving);
    }
    if (mat == 10) {
        vec3 o = shade(p.x < 2.8 ? vec3(0.78, 0.8, 0.83) : vec3(0.3, 0.32, 0.35), n, rd, 0.4, true);
        if (p.x > 4.85 && n.y > 0.5) {
            // (the desk's screens)
            float scr = step(0.0, sin(p.z * 9.0)) * step(abs(p.z), 1.0);
            o += vec3(0.2, 0.8, 1.0) * scr * 0.6;
        }
        return o;
    }
    // its skin
    if (in_) {
        if (n.y > 0.8 && p.y < FL + 0.05) {
            float tile = step(0.5, fract(p.x * 1.0)) * 0.04;
            return shade(style == 0 ? vec3(0.3, 0.32, 0.36) + tile : vec3(0.36, 0.22, 0.12) + tile, n, rd, 0.1, true);
        }
        if (n.y < -0.8) {
            // its ceiling: a strip of light down its middle
            float strip = smoothstep(0.2, 0.12, abs(p.z));
            return mix(shade(vec3(0.85), n, rd, 0.0, true), vec3(1.0, 0.96, 0.86) * 1.3, strip);
        }
        return shade(style == 0 ? vec3(0.86, 0.88, 0.9) : vec3(0.55, 0.38, 0.24), n, rd, 0.1, true);
    }
    vec3 c;
    float gloss = 0.6;
    if (style == 0) {
        c = vec3(0.93, 0.95, 0.97);
        float strip = smoothstep(0.04, 0.0, abs(p.y - (FL + 0.75)));
        if (strip > 0.0 && abs(p.z) > 1.0) {
            return mix(shade(c, n, rd, gloss, false), vec3(0.25, 0.95, 1.0) * 1.3, strip);
        }
        if (cab() && p.x > HL + 0.3 && p.y > 1.0 && p.y < 1.35 && abs(p.z) > 0.45 && abs(p.z) < 1.2) {
            return vec3(1.0, 0.98, 0.9) * 1.6;
        }
        if (p.y < FL - 0.1) c = vec3(0.3, 0.33, 0.38);
        gloss = 1.0;
    } else {
        c = steamLoco() ? vec3(0.32, 0.06, 0.07) : vec3(0.42, 0.07, 0.09);
        if (!steamLoco() && p.y > FL + 0.95 && p.y < FL + 2.35) c = vec3(0.88, 0.82, 0.66);
        if (p.y > CE - 0.05) c = vec3(0.16, 0.16, 0.18);
        float line = smoothstep(0.025, 0.0, abs(p.y - (FL + 0.9))) + smoothstep(0.025, 0.0, abs(p.y - (FL + 2.4)));
        c = mix(c, vec3(0.85, 0.65, 0.25), clamp(line, 0.0, 1.0));
        gloss = 0.35;
    }
    return shade(c, n, rd, gloss, false);
}

vec4 glassColour(vec3 p, vec3 n, vec3 rd) {
    float fres = pow(1.0 - abs(dot(n, rd)), 3.0);
    vec3 tint = style == 0 ? vec3(0.55, 0.78, 0.95) : vec3(0.75, 0.8, 0.7);
    float row = floor(p.y * 26.0);
    float dash = fract(p.x * 0.35 + travel * 0.9 * (0.6 + hash1(row)) + hash1(row + 7.0));
    float streak = step(0.6, hash1(row * 1.7)) * smoothstep(0.0, 0.2, dash) * smoothstep(0.7, 0.2, dash);
    float fast = clamp((speed - 0.1) * 2.0, 0.0, 1.0);
    float night = clamp(0.75 - lit, 0.0, 1.0);
    vec3 c = tint * (0.25 + 0.5 * fres) * max(lit, 0.3) + vec3(0.85, 0.95, 1.0) * streak * fast * 0.7;
    c += vec3(1.0, 0.8, 0.5) * night * 0.5 * (1.0 - fres);
    return vec4(c, clamp(0.14 + fres * 0.45 + streak * fast * 0.5 + night * 0.3, 0.0, 0.9));
}

void main() {
    T = GameTime * 1200.0;
    vec3 ro = eye;
    vec3 rd = normalize(lp - eye);
    vec3 inv = 1.0 / (sign(rd) * max(abs(rd), vec3(1e-6)));
    vec3 ta = (LO - ro) * inv, tb = (HI - ro) * inv;
    float t0 = max(max(max(min(ta.x, tb.x), min(ta.y, tb.y)), min(ta.z, tb.z)), 0.0);
    float t1 = min(min(max(ta.x, tb.x), max(ta.y, tb.y)), max(ta.z, tb.z));
    if (t1 <= t0) {
        discard;
    }
    vec3 col = vec3(0.0);
    float a = 0.0, depthT = -1.0;
    float t = t0;
    for (int i = 0; i < 180 && t < t1 && a < 0.97; i++) {
        vec3 p = ro + rd * t;
        int mat;
        float s = solid(p, mat);
        float g = glass(p);
        if (s < 0.002) {
            vec3 n = solidNormal(p);
            col += (1.0 - a) * solidColour(p, n, rd, mat);
            a = 1.0;
            if (depthT < 0.0) depthT = t;
            break;
        }
        if (g < 0.002) {
            vec3 n = glassNormal(p);
            vec4 gc = glassColour(p, n, rd);
            col += (1.0 - a) * gc.rgb * gc.a;
            a += (1.0 - a) * gc.a;
            if (depthT < 0.0) depthT = t;
            t += 0.03;
            continue;
        }
        float dt = max(min(s, g), 0.004);
        if (style == 0 && p.y < 0.15) {
            float gl = smoothstep(1.0, 0.5, abs(p.z)) * smoothstep(HL, HL - 0.6, abs(p.x)) * smoothstep(-0.15, 0.1, p.y);
            dt = min(dt, 0.05);
            col += (1.0 - a) * vec3(0.15, 0.75, 1.0) * gl * dt * (1.0 + speed * 2.0);
        }
        t += dt;
    }
    float glowA = clamp(max(col.r, max(col.g, col.b)), 0.0, 1.0);
    if (a < 0.01) {
        if (glowA < 0.01) discard;
        a = glowA * 0.5;
        depthT = t0;
    }
    vec3 hit = centre + F * (ro.x + rd.x * depthT) + U * (ro.y + rd.y * depthT) + S * (ro.z + rd.z * depthT);
    vec4 clip = ProjMat * ModelViewMat * vec4(hit, 1.0);
    gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0);
    fragColor = vec4(col / max(a, 0.001), a);
}
