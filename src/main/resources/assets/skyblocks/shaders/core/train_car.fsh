#version 150

// A train car, traced in its own space (x along it - its nose +x - y up from the rails' middle, z across), 12 blocks
// long, matching its solid walls (TrainEntity.boxes): its floor's top at FL, a corridor 3 wide, a ceiling at CE, sliding
// doors (two leaves each, open as 'door'), gangways at the ends it joins another car by.
//
//  maglev (0): a glossy white body, its sides leaning in towards the roof, a black glass band along its windows, a
//    cyan line sweeping along it and up its nose, navy skirts, air-conditioning pods on its roof, magnets glowing
//    underneath; its cabs a long rounded nose, a wrap-round windscreen, LED headlights (red tail lights at its other end).
//    Inside: slate carpet, blue seats with white headrests, glass luggage racks, a strip of light down the ceiling.
//  steam (1): maroon and cream carriages, panelled and lined in gold, arched windows, a curved roof with a clerestory
//    and lamp tops, footboards, bogies with leaf springs, buffers; inside, wood panelling, red seats, brass lamps.
//    Its locomotive a maroon boiler with brass bands, a black smokebox and its round door, a copper-capped chimney, a
//    brass dome, cylinders, red driving wheels with their coupling and connecting rods turning as it goes, a red buffer
//    beam and buffers, and its cab behind - an arched roof, round front windows, the firebox glowing.
// Lit by the sky and the sun (its reflections in the paint and glass), shadowed in its corners. Glass is seen through
// both ways, streaked as it goes faster. It writes its depth: what's inside shows through.

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
const float PI = 3.14159265;

float T;
vec3 upL, sunL;

// ---- little tools ----

float hash1(float n) {
    return fract(sin(n * 91.345) * 47453.5453);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise2(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1, 0)), f.x), mix(hash2(i + vec2(0, 1)), hash2(i + vec2(1, 1)), f.x), f.y);
}

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float sdRoundBox(vec3 p, vec3 b, float r) {
    vec3 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r;
}

float box2(vec3 p, vec3 a, vec3 b) {
    return sdBox(p - (a + b) * 0.5, abs(b - a) * 0.5);
}

float rbox2(vec3 p, vec3 a, vec3 b, float r) {
    return sdRoundBox(p - (a + b) * 0.5, abs(b - a) * 0.5, r);
}

float sdRect(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
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

float capsule(vec3 p, vec3 a, vec3 b, float r) {
    vec3 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h) - r;
}

float smin(float a, float b, float k) {
    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
    return mix(b, a, h) - k * h * (1.0 - h);
}

float smax(float a, float b, float k) {
    return -smin(-a, -b, k);
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

// how far into a maglev cab's nose (0 .. 1)
float noseS(float x) {
    return cab() ? clamp((x - (HL - 2.3)) / 3.4, 0.0, 1.0) : 0.0;
}

// ---- the body ----

float outer(vec3 p) {
    if (style == 0) {
        float s = noseS(p.x);
        float yb = 0.3 + 0.4 * s * s;
        float yt = RT - (RT - 1.25) * pow(s, 1.45);
        float hw = OW * (1.0 - 0.42 * s * s);
        float zz = abs(p.z) + 0.11 * smoothstep(FL + 2.2, RT, p.y);
        float r = min(0.62, min((yt - yb) * 0.45, hw * 0.75));
        float d = sdRect(vec2(zz, p.y - (yb + yt) * 0.5), vec2(hw, (yt - yb) * 0.5), r);
        float x1 = cab() ? HL + 1.1 : HL;
        d = smax(d, p.x - x1, cab() ? 0.45 : 0.12);
        d = smax(d, -p.x - HL, 0.12);
        return d;
    }
    if (steamLoco()) {
        float b = rbox2(p, vec3(-HL, 0.55, -OW), vec3(-1.2, CE - 0.3, OW), 0.06);
        float roof = max(length(vec2(p.y - (CE - 2.45), p.z)) - 2.9, max(abs(p.x + 3.6) - 2.45, CE - 0.45 - p.y));
        return min(b, roof);
    }
    float b = rbox2(p, vec3(-HL, 0.55, -OW), vec3(HL, CE - 0.3, OW), 0.1);
    float roof = max(length(vec2(p.y - (CE - 2.45), p.z)) - 2.9, max(abs(p.x) - HL - 0.03, CE - 0.45 - p.y));
    float clere = rbox2(p, vec3(-HL + 0.9, CE, -0.72), vec3(HL - 0.9, CE + 0.5, 0.72), 0.05);
    return min(min(b, roof), clere);
}

float inner(vec3 p) {
    float b = steamLoco() ? box2(p, vec3(-HL + 0.15, FL, -IW), vec3(-1.35, CE, IW)) : box2(p, vec3(-HL + 0.15, FL, -IW), vec3(HL - 0.15, CE, IW));
    return max(b, outer(p) + 0.13);
}

// openings through the skin: doors, gangways, the steam cab's open sides
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

// its windows (on its skin): how far inside a window's edge a point is (> 0 in one)
float windowIn(vec3 p) {
    float az = abs(p.z);
    if (style == 0) {
        float s = noseS(p.x);
        if (cab() && s > 0.08) {
            // the wrap-round windscreen: over the nose's waist, back from its tip
            float top = RT - (RT - 1.25) * pow(s, 1.45);
            return min(min(p.y - (FL + 1.15 + 0.25 * s), top - 0.25 - p.y), min(HL + 0.75 - p.x, 1.2 - az * 0.55)) * 4.0;
        }
        if (az < IW - 0.05 || p.y < FL + 1.0 || p.y > FL + 2.3) return -1.0;
        if (abs(p.x + DX) < DH + 0.25 || (kind == 1 && abs(p.x - DX) < DH + 0.25) || abs(p.x) > HL - 0.5) return -1.0;
        if (cab() && p.x > 2.4) return -1.0;
        float c = fract(p.x / 1.2 + 0.5) - 0.5;
        return -sdRect(vec2(c * 1.2, p.y - (FL + 1.65)), vec2(0.5, 0.6), 0.12);
    }
    if (steamLoco()) {
        if (az > IW - 0.05) {
            return -sdRect(vec2(p.x + 5.2, p.y - (FL + 1.85)), vec2(0.42, 0.45), 0.18);
        }
        // round spectacle windows either side of the boiler
        return 0.3 - length(vec2(az - 0.95, p.y - (FL + 2.35)));
    }
    if (az > IW - 0.05) {
        if (abs(p.x + DX) < DH + 0.15 || abs(p.x - DX) < DH + 0.15 || abs(p.x) > HL - 0.4) return -1.0;
        float c = fract(p.x / 0.9 + 0.5) - 0.5;
        vec2 q = vec2(c * 0.9, p.y - (FL + 1.62));
        // arched tops
        float rect = sdRect(q, vec2(0.33, 0.55), 0.06);
        float arch = length(vec2(q.x, max(q.y - 0.22, 0.0))) - 0.33;
        return -max(rect, q.y > 0.22 ? arch : rect);
    }
    // the clerestory's little windows
    if (p.y > CE + 0.12 && p.y < CE + 0.38 && az > 0.6 && abs(p.x) < HL - 1.1) {
        float c = fract(p.x / 0.6 + 0.5) - 0.5;
        return 0.18 - abs(c * 0.6);
    }
    return -1.0;
}

bool window(vec3 p) {
    return windowIn(p) > 0.0;
}

// seats (and the driver's), with headrests
float seats(vec3 p) {
    if (steamLoco()) {
        if (p.y > FL + 1.4) return 1e3;
        return min(rbox2(p, vec3(-3.35, FL + 0.3, 0.25), vec3(-2.65, FL + 0.48, 0.95), 0.05), rbox2(p, vec3(-3.42, FL + 0.48, 0.25), vec3(-3.3, FL + 1.25, 0.95), 0.04));
    }
    if (p.y > FL + 1.45 || abs(p.z) < 0.5) {
        if (kind != 1 && abs(p.z) < 0.5 && p.y < FL + 1.45) {
            return min(rbox2(p, vec3(3.6, FL + 0.28, -0.35), vec3(4.4, FL + 0.5, 0.35), 0.08), rbox2(p, vec3(3.5, FL + 0.45, -0.35), vec3(3.66, FL + 1.35, 0.35), 0.06));
        }
        return 1e3;
    }
    float d = 1e3;
    vec3 q = vec3(p.x, p.y, abs(p.z));
    for (int i = 0; i < 6; i++) {
        float sx = i == 0 ? -5.1 : i == 1 ? -1.8 : i == 2 ? -0.6 : i == 3 ? 0.6 : i == 4 ? 1.8 : 5.1;
        if (i == 5 && kind != 1) break;
        if (abs(p.x - sx) > 0.7) continue;
        float back = (i == 0 || i == 1 || i == 3) ? -1.0 : 1.0;
        float cushion = rbox2(q, vec3(sx - 0.38, FL + 0.28, 0.58), vec3(sx + 0.38, FL + 0.48, 1.42), 0.07);
        float leg = box2(q, vec3(sx - 0.3, FL, 0.7), vec3(sx + 0.3, FL + 0.28, 1.3));
        float bx = sx + back * 0.34;
        float backrest = rbox2(q, vec3(bx - 0.08, FL + 0.45, 0.58), vec3(bx + 0.08, FL + 1.25, 1.42), 0.06);
        float head = rbox2(q, vec3(bx - 0.1, FL + 1.18, 0.75), vec3(bx + 0.1, FL + 1.38, 1.25), 0.07);
        float arm = rbox2(q, vec3(sx - 0.35, FL + 0.48, 0.58), vec3(sx + 0.3, FL + 0.66, 0.66), 0.03);
        d = min(d, min(min(cushion, leg), min(min(backrest, head), arm)));
    }
    return d;
}

// what's solid, and which:
// 1 skin, 2 seats, 3 under-gear, 4 wheels, 5 boiler, 6 black metal, 7 brass, 8 lamp, 9 door leaves, 10 desk / bulkhead,
// 11 copper, 12 red (buffer beam, rods' ends), 13 steel (rods, buffers), 14 roof pods, 15 racks / bellows
float solid(vec3 p, out int mat) {
    float o = outer(p);
    float sh = max(o, -inner(p));
    sh = max(sh, -openings(p));
    if (sh < 0.05 && window(p)) sh = max(sh, 0.06);
    float d = sh;
    mat = 1;
    float az = abs(p.z);
    // door leaves: two each side, slid apart
    if (!steamLoco() && az > IW - 0.2 && p.y < FL + DHT + 0.1) {
        for (int i = 0; i < 2; i++) {
            float dc = i == 0 ? -DX : DX;
            if (i == 1 && kind != 1) break;
            float shift = door * (DH - 0.05);
            vec3 q = vec3(p.x, p.y, az);
            float l1 = box2(q, vec3(dc - DH - shift, FL, IW + 0.02), vec3(dc - shift, FL + DHT, IW + 0.1));
            float l2 = box2(q, vec3(dc + shift, FL, IW + 0.02), vec3(dc + DH + shift, FL + DHT, IW + 0.1));
            float leaves = max(min(l1, l2), box2(q, vec3(dc - DH, FL - 0.1, IW - 0.1), vec3(dc + DH, FL + DHT + 0.1, OW + 0.1)));
            if (leaves < d) { d = leaves; mat = 9; }
        }
    }
    float st = seats(p);
    if (st < d) { d = st; mat = 2; }
    if (cab() && p.x > 2.4) {
        float bulk = max(box2(p, vec3(2.6, FL, -IW), vec3(2.75, CE, IW)), -box2(p, vec3(2.5, FL - 0.1, -0.5), vec3(2.85, FL + 2.2, 0.5)));
        float desk = max(rbox2(p, vec3(4.8, FL, -1.25), vec3(5.9, FL + 1.05, 1.25), 0.12), dot(p - vec3(4.8, FL + 1.05, 0.0), normalize(vec3(0.5, 1.0, 0.0))));
        float b = min(bulk, desk);
        if (b < d) { d = b; mat = 10; }
    }
    // luggage racks along both sides, over the seats
    if (!steamLoco() && p.y > CE - 0.7 && az > 0.9) {
        float rack = box2(vec3(p.x, p.y, az), vec3(-HL + 0.3, CE - 0.62, 0.95), vec3(HL - (cab() ? 3.5 : 0.3), CE - 0.58, IW));
        rack = max(rack, -box2(vec3(p.x, p.y, az), vec3(-DX - 1.0, CE - 1.0, 0.0), vec3(-DX + 1.0, CE, 2.0)));
        if (kind == 1) rack = max(rack, -box2(vec3(p.x, p.y, az), vec3(DX - 1.0, CE - 1.0, 0.0), vec3(DX + 1.0, CE, 2.0)));
        if (rack < d) { d = rack; mat = 15; }
    }
    // gangways: rubber bellows out to the next car
    if ((gangP() || gangM()) && abs(p.x) > HL - 0.2) {
        for (int i = 0; i < 2; i++) {
            bool on = i == 0 ? gangP() : gangM();
            if (!on) continue;
            float x0 = i == 0 ? HL - 0.1 : -HL - GOUT, x1 = i == 0 ? HL + GOUT : -HL + 0.1;
            float g = max(rbox2(p, vec3(x0, FL - 0.2, -GH - 0.15), vec3(x1, FL + GHT + 0.15, GH + 0.15), 0.08),
                          -box2(p, vec3(x0 - 0.1, FL, -GH), vec3(x1 + 0.1, FL + GHT, GH)));
            if (g < d) { d = g; mat = 15; }
        }
    }
    if (style == 0) {
        // under-gear and its magnets; air-conditioning pods on the roof
        if (p.y < 0.35) {
            float eq = min(rbox2(p, vec3(-4.6, 0.1, -1.15), vec3(-1.4, 0.32, 1.15), 0.06), rbox2(p, vec3(1.4, 0.1, -1.15), vec3(cab() ? 3.6 : 4.6, 0.32, 1.15), 0.06));
            eq = min(eq, box2(vec3(p.x, p.y, az), vec3(-HL + 0.5, 0.02, 0.55), vec3(HL - 0.5, 0.12, 0.95)));
            if (eq < d) { d = eq; mat = 3; }
        }
        if (p.y > RT - 0.3) {
            float pod = rbox2(p, vec3(-4.0, RT - 0.2, -0.75), vec3(-2.0, RT + 0.2, 0.75), 0.15);
            if (kind == 1) pod = min(pod, rbox2(p, vec3(2.0, RT - 0.2, -0.75), vec3(4.0, RT + 0.2, 0.75), 0.15));
            if (pod < d) { d = pod; mat = 14; }
        }
        return d;
    }
    if (!steamLoco()) {
        // footboards, bogies (frames, leaf springs, axle boxes, wheels), buffers
        if (az > OW - 0.05 && p.y < 0.6) {
            float fb = box2(vec3(p.x, p.y, az), vec3(-HL + 0.4, 0.42, OW), vec3(HL - 0.4, 0.48, OW + 0.18));
            if (fb < d) { d = fb; mat = 6; }
        }
        if (p.y < 0.62) {
            float bx = p.x > 0.0 ? 4.2 : -4.2;
            vec3 q = vec3(p.x - bx, p.y, az);
            float frame = box2(q, vec3(-1.1, 0.42, 0.62), vec3(1.1, 0.58, 0.9));
            float spring = rbox2(q, vec3(-0.45, 0.5, 0.8), vec3(0.45, 0.56 - abs(q.x) * 0.0, 0.9), 0.02);
            float axle = box2(q, vec3(-0.75, 0.28, 0.82), vec3(-0.55, 0.5, 0.95));
            axle = min(axle, box2(q, vec3(0.55, 0.28, 0.82), vec3(0.75, 0.5, 0.95)));
            float b = min(min(frame, spring), axle);
            if (b < d) { d = b; mat = 3; }
            float wh = min(cylZ(q, vec2(-0.65, 0.4), 0.38, 0.72, 0.05), cylZ(q, vec2(0.65, 0.4), 0.38, 0.72, 0.05));
            if (wh < d) { d = wh; mat = 4; }
        }
        if (abs(p.x) > HL - 0.1 && p.y < 1.1) {
            vec3 q = vec3(abs(p.x), p.y, az);
            float buf = min(cylX(q, vec2(0.78, 1.0), 0.1, HL + 0.12, 0.13), cylX(q, vec2(0.78, 1.0), 0.2, HL + 0.27, 0.03));
            if (buf < d) { d = buf; mat = 13; }
        }
        if (p.y > CE + 0.4) {
            float lamp = cylY(vec3(fract(p.x / 2.4 + 0.5) * 2.4 - 1.2, p.y, p.z), vec2(0.0, 0.0), 0.13, CE + 0.6, 0.1);
            lamp = max(lamp, abs(p.x) - HL + 1.0);
            if (lamp < d) { d = lamp; mat = 6; }
        }
        return d;
    }
    // ---- the steam locomotive's front ----
    vec3 q = vec3(p.x, p.y, az);
    float boiler = cylX(p, vec2(2.95, 0.0), 1.05, (HL - 1.1 - 1.2) * 0.5, (HL - 1.1 + 1.2) * 0.5);
    if (boiler < d) { d = boiler; mat = 5; }
    float blk = cylX(p, vec2(2.95, 0.0), 1.12, HL - 0.42, 0.68);
    // its round smokebox door
    blk = min(blk, max(length(p - vec3(HL - 0.6, 2.95, 0.0)) - 1.05, HL + 0.22 - p.x) - 0.0);
    blk = min(blk, cylY(p, vec2(HL - 0.65, 0.0), 0.34, 4.35, 0.55));
    blk = min(blk, box2(p, vec3(-1.2, 0.55, -0.62), vec3(HL + 0.35, 1.75, 0.62)));
    blk = min(blk, box2(p, vec3(-HL, 0.4, -0.62), vec3(-1.2, 0.86, 0.62)));
    blk = min(blk, box2(p, vec3(-1.2, 1.82, -OW), vec3(HL + 0.32, 1.9, OW)));
    if (blk < d) { d = blk; mat = 6; }
    float cu = min(cylY(p, vec2(HL - 0.65, 0.0), 0.44, 4.92, 0.07), cylY(p, vec2(HL - 0.65, 0.0), 0.38, 4.78, 0.08));
    if (cu < d) { d = cu; mat = 11; }
    float brass = min(cylX(p, vec2(2.95, 0.0), 1.08, 0.0, 0.04), min(cylX(p, vec2(2.95, 0.0), 1.08, 1.6, 0.04), cylX(p, vec2(2.95, 0.0), 1.08, 3.2, 0.04)));
    brass = min(brass, min(length(p - vec3(1.8, 4.05, 0.0)) - 0.5, cylY(p, vec2(1.8, 0.0), 0.47, 3.85, 0.22)));
    brass = min(brass, cylY(p, vec2(-0.7, 0.0), 0.09, 4.2, 0.25));
    brass = min(brass, cylX(q, vec2(3.35, 1.02), 0.03, 2.0, 2.9));
    brass = min(brass, box2(p, vec3(HL + 0.2, 3.55, -0.3), vec3(HL + 0.27, 3.75, 0.3)));
    if (brass < d) { d = brass; mat = 7; }
    // cylinders, slide bars
    float cyl = cylX(q, vec2(1.05, 1.15), 0.4, HL - 0.85, 0.55);
    if (cyl < d) { d = cyl; mat = 5; }
    // wheels: three drivers each side, a small leading pair, a trailing pair under the cab
    float wh = 1e3;
    for (int i = 0; i < 3; i++) {
        wh = min(wh, cylZ(q, vec2(float(i) * 1.8, 0.95), 0.85, 0.72, 0.07));
    }
    wh = min(wh, cylZ(q, vec2(5.2, 0.5), 0.42, 0.72, 0.06));
    wh = min(wh, cylZ(q, vec2(-3.5, 0.44), 0.42, 0.72, 0.06));
    if (wh < d) { d = wh; mat = 4; }
    // rods: coupling rod between the drivers' crank pins, connecting rod from the crosshead (each side a quarter turn apart)
    float side = p.z > 0.0 ? 0.0 : PI * 0.5;
    float a = travel / 0.85 + side;
    vec2 pin = vec2(0.42 * cos(a), 0.42 * sin(a));
    float rods = box2(q, vec3(0.0 + pin.x, 0.95 + pin.y - 0.06, 0.83), vec3(3.6 + pin.x, 0.95 + pin.y + 0.06, 0.9));
    vec3 cross = vec3(4.0 + 0.42 * cos(a) * 0.9, 1.05, 0.97);
    rods = min(rods, capsule(q, vec3(1.8 + pin.x, 0.95 + pin.y, 0.97), cross, 0.055));
    rods = min(rods, box2(q, vec3(3.7, 1.0, 0.92), vec3(HL - 1.3, 1.1, 1.04)));
    if (rods < d) { d = rods; mat = 13; }
    // buffer beam, buffers, coupling; its lamp
    float red = box2(p, vec3(HL + 0.32, 0.55, -1.4), vec3(HL + 0.45, 1.25, 1.4));
    if (red < d) { d = red; mat = 12; }
    float buf = min(cylX(q, vec2(0.9, 1.0), 0.1, HL + 0.6, 0.15), cylX(q, vec2(0.9, 1.0), 0.21, HL + 0.76, 0.03));
    if (buf < d) { d = buf; mat = 13; }
    float lamp = rbox2(p, vec3(HL + 0.18, 2.0, -0.16), vec3(HL + 0.4, 2.35, 0.16), 0.04);
    if (lamp < d) { d = lamp; mat = 8; }
    return d;
}

float solidOnly(vec3 p) {
    int m;
    return solid(p, m);
}

vec3 solidNormal(vec3 p) {
    vec2 e = vec2(0.0025, 0.0);
    return normalize(vec3(solidOnly(p + e.xyy) - solidOnly(p - e.xyy), solidOnly(p + e.yxy) - solidOnly(p - e.yxy), solidOnly(p + e.yyx) - solidOnly(p - e.yyx)));
}

float glass(vec3 p) {
    float sk = max(outer(p), -inner(p));
    if (sk > 0.06 || !window(p)) return max(sk, 0.05);
    return abs(outer(p) + 0.065) - 0.008;
}

vec3 glassNormal(vec3 p) {
    vec2 e = vec2(0.004, 0.0);
    return normalize(vec3(outer(p + e.xyy) - outer(p - e.xyy), outer(p + e.yxy) - outer(p - e.yxy), outer(p + e.yyx) - outer(p - e.yyx)));
}

bool isInside(vec3 p) {
    if (steamLoco() && p.x > -1.25) return false;
    return inner(p) < 0.03;
}

// ---- light ----

// the sky (or the ground) seen in a direction
vec3 env(vec3 r) {
    float e = dot(r, upL);
    vec3 sky = mix(vec3(0.78, 0.86, 0.95), vec3(0.36, 0.56, 0.9), clamp(e * 1.6, 0.0, 1.0));
    vec3 ground = vec3(0.24, 0.27, 0.21);
    vec3 c = mix(ground, sky, smoothstep(-0.08, 0.04, e));
    c += vec3(1.0, 0.92, 0.75) * pow(max(dot(r, sunL), 0.0), 220.0) * 6.0;
    return c * max(lit, 0.08);
}

float ao(vec3 p, vec3 n) {
    float occ = 0.0;
    for (int i = 1; i <= 3; i++) {
        float h = 0.07 * float(i);
        occ += (h - solidOnly(p + n * h)) / float(i);
    }
    return clamp(1.0 - 2.2 * occ, 0.35, 1.0);
}

// a surface lit: its colour, how glossy (0 matt .. 1 mirror-like), outside or in
vec3 shade(vec3 c, vec3 n, vec3 rd, float gloss, bool in_, float occ) {
    float fres = 0.04 + 0.96 * pow(1.0 - max(dot(n, -rd), 0.0), 5.0);
    if (in_) {
        // lit from its ceiling, warm; some daylight through the windows
        float lamp = 0.5 + 0.35 * max(n.y, 0.0) + 0.25 * max(-n.y, 0.0);
        vec3 l = vec3(1.0, 0.92, 0.78) * lamp + vec3(0.55, 0.62, 0.7) * 0.35 * lit;
        vec3 o = c * l * occ;
        o += vec3(1.0, 0.95, 0.85) * pow(max(n.y, 0.0), 8.0) * gloss * 0.15;
        return o;
    }
    float dif = max(dot(n, sunL), 0.0);
    float hem = 0.5 + 0.5 * dot(n, upL);
    vec3 amb = mix(vec3(0.3, 0.32, 0.28), vec3(0.62, 0.7, 0.82), hem);
    vec3 o = c * (amb * occ * 0.85 + vec3(1.0, 0.95, 0.85) * dif * 0.85) * max(lit, 0.08);
    vec3 h = normalize(sunL - rd);
    o += vec3(1.0, 0.96, 0.88) * pow(max(dot(n, h), 0.0), mix(16.0, 120.0, gloss)) * gloss * dif * max(lit, 0.08) * 1.2;
    o = mix(o, env(reflect(rd, n)) * occ, fres * gloss * 0.85);
    return o;
}

// ---- colours ----

vec3 maglevSkin(vec3 p, vec3 n, out float gloss, out float glow) {
    gloss = 0.9;
    glow = 0.0;
    float s = noseS(p.x);
    float az = abs(p.z);
    vec3 c = vec3(0.93, 0.945, 0.96);
    // a black glass band along the windows, swept up into the windscreen
    float bandLo = FL + 0.92 + 0.35 * s, bandHi = FL + 2.38;
    if (p.y > bandLo && p.y < bandHi && az > 1.0 && !(abs(p.x + DX) < DH + 0.05 || (kind == 1 && abs(p.x - DX) < DH + 0.05))) {
        c = vec3(0.035, 0.04, 0.05);
        gloss = 1.0;
    }
    // the navy skirt
    if (p.y < 0.62 + 0.3 * s * s) {
        c = vec3(0.07, 0.11, 0.2);
        gloss = 0.6;
    }
    // the cyan line, sweeping up the nose
    float lineY = FL + 0.62 + 1.6 * pow(s, 1.8);
    float line = smoothstep(0.045, 0.02, abs(p.y - lineY));
    if (line > 0.0 && az > 0.4) {
        glow = line;
    }
    // panel seams
    float seam = smoothstep(0.012, 0.0, abs(fract(p.x / 3.0 + 0.5) - 0.5) * 3.0) * step(p.y, bandLo) * step(abs(p.x), HL - 0.5) * (1.0 - step(0.01, s));
    c *= 1.0 - seam * 0.45;
    // head and tail lights: LED strips low on the nose
    if (cab() && s > 0.7 && p.y < 0.7 + 0.4 * s * s + 0.6 && p.y > 0.7 + 0.4 * s * s + 0.38 && az > 0.25 && az < 0.95) {
        glow = kind == 0 ? 2.0 : 3.0;
    }
    // vents along the skirt
    if (p.y < 0.6 && p.y > 0.4 && az > 1.3 && fract(p.x * 4.0) < 0.5 && abs(p.x) < 2.0) {
        c *= 0.6;
    }
    return c;
}

vec3 steamSkin(vec3 p, vec3 n, out float gloss) {
    gloss = 0.45;
    float az = abs(p.z);
    vec3 maroon = vec3(0.4, 0.055, 0.075), cream = vec3(0.9, 0.84, 0.68), gold = vec3(0.86, 0.66, 0.26);
    if (p.y > CE - 0.32 && steamLoco()) {
        return vec3(0.05, 0.05, 0.055); // (the cab's roof: black)
    }
    if (p.y > CE - 0.32) {
        // its roof: pale grey canvas
        float wv = noise2(p.xz * vec2(8.0, 30.0)) * 0.06;
        return vec3(0.68, 0.67, 0.64) - wv;
    }
    if (az < OW - 0.05 && !steamLoco()) {
        return maroon * 0.8; // (its ends)
    }
    vec3 c = maroon;
    if (!steamLoco() && p.y > FL + 0.95 && p.y < FL + 2.42) c = cream;
    // panel beading and gold lining: round the windows' row, along its waist, up between panels
    float lineY = min(abs(p.y - (FL + 0.95)), abs(p.y - (FL + 2.42)));
    float panel = abs(fract(p.x / 0.9 + 0.5) - 0.5) * 0.9;
    float vline = steamLoco() ? 1.0 : abs(panel - 0.4);
    float bead = min(lineY, p.y < FL + 0.95 ? min(vline, abs(p.y - 0.75)) : 1.0);
    if (steamLoco()) bead = min(abs(p.y - (FL + 0.3)), min(abs(p.y - (CE - 0.5)), min(abs(p.x + 1.4), abs(p.x + HL - 0.15))));
    c = mix(c, gold, smoothstep(0.022, 0.008, bead));
    c *= 1.0 - smoothstep(0.035, 0.022, bead) * smoothstep(0.008, 0.02, bead) * 0.35;
    // the windows' frames (varnished wood)
    float w = windowIn(p);
    if (w > -0.06 && w <= 0.0) c = vec3(0.32, 0.18, 0.09);
    if (p.y < 0.68) c = vec3(0.06);
    return c;
}

vec3 interior(vec3 p, vec3 n, out float gloss, out float glow) {
    gloss = 0.1;
    glow = 0.0;
    float az = abs(p.z);
    if (n.y > 0.8 && p.y < FL + 0.05) {
        if (style == 0) {
            float fleck = hash2(floor(p.xz * 40.0)) * 0.05;
            vec3 c = vec3(0.2, 0.22, 0.26) + fleck;
            // yellow lines at the doors
            if ((abs(p.x + DX) < DH && az > IW - 0.35 && az < IW - 0.25) || (kind == 1 && abs(p.x - DX) < DH && az > IW - 0.35 && az < IW - 0.25)) c = vec3(0.9, 0.75, 0.1);
            return c;
        }
        float plank = floor(p.z * 6.0);
        return vec3(0.42, 0.26, 0.13) * (0.85 + 0.25 * hash1(plank)) * (0.9 + 0.1 * noise2(vec2(p.x * 2.0, plank)));
    }
    if (n.y < -0.8 && p.y > CE - 0.05) {
        if (style == 0) {
            float strip = smoothstep(0.22, 0.16, az);
            glow = strip * 1.0;
            return vec3(0.9, 0.91, 0.92);
        }
        // brass lamps down the ceiling
        vec2 l = vec2(fract(p.x / 2.4 + 0.5) * 2.4 - 1.2, p.z);
        float lamp = smoothstep(0.2, 0.14, length(l));
        glow = lamp * 1.2;
        return vec3(0.92, 0.88, 0.78);
    }
    if (steamLoco() && n.x < -0.8 && p.x > -1.6) {
        // the backhead: the firebox door glowing, brass gauges, pipes
        vec3 c = vec3(0.06);
        vec2 q = vec2(p.z, p.y);
        if (abs(q.x) < 0.32 && q.y > FL + 0.55 && q.y < FL + 1.15) {
            float flick = 0.75 + 0.25 * sin(T * 9.0 + sin(T * 23.0));
            glow = (step(abs(q.x), 0.26) * step(abs(q.y - (FL + 0.85)), 0.06)) * 2.0 * flick;
            return mix(vec3(0.1), vec3(1.0, 0.45, 0.1), step(0.5, glow));
        }
        for (int i = -1; i <= 1; i += 2) {
            float g = length(q - vec2(0.42 * float(i), FL + 2.0));
            if (g < 0.13) {
                float needle = step(abs(dot(normalize(q - vec2(0.42 * float(i), FL + 2.0)), vec2(0.7, 0.7))), 0.12) * step(g, 0.1);
                return g > 0.105 ? vec3(0.85, 0.64, 0.24) : mix(vec3(0.95, 0.93, 0.88), vec3(0.05), needle);
            }
        }
        if (abs(q.y - (FL + 1.55)) < 0.03 || abs(q.x - 0.7) < 0.03) c = vec3(0.7, 0.5, 0.2);
        return c;
    }
    if (style == 0) {
        // white panels, darker lower down; screens over the doors
        vec3 c = mix(vec3(0.55, 0.6, 0.66), vec3(0.93, 0.94, 0.95), smoothstep(FL + 0.3, FL + 0.9, p.y));
        if (az > IW - 0.1 && p.y > FL + DHT + 0.1 && p.y < FL + DHT + 0.45 && (abs(p.x + DX) < 0.5 || (kind == 1 && abs(p.x - DX) < 0.5))) {
            glow = 0.6;
            float txt = step(0.5, hash2(floor(vec2(p.x * 22.0, p.y * 20.0 + floor(T * 0.5)))));
            return mix(vec3(0.02, 0.05, 0.1), vec3(0.3, 0.8, 1.0), txt * step(abs(p.y - (FL + DHT + 0.27)), 0.07));
        }
        return c;
    }
    // wood panelling: teak boards
    float board = floor(p.x * 3.0);
    vec3 c = vec3(0.45, 0.25, 0.12) * (0.85 + 0.2 * hash1(board)) * (0.9 + 0.12 * noise2(vec2(board, p.y * 6.0)));
    c *= 1.0 - smoothstep(0.03, 0.0, fract(p.x * 3.0)) * 0.35;
    if (p.y > FL + 2.5) c = vec3(0.9, 0.86, 0.74);
    return c;
}

vec3 solidColour(vec3 p, vec3 n, vec3 rd, int mat) {
    bool in_ = isInside(p + n * 0.03);
    float occ = ao(p, n);
    float az = abs(p.z);
    float gloss = 0.3, glow = 0.0;
    vec3 c;
    if (mat == 2) {
        if (style == 0) {
            // blue fabric, white headrest covers
            float weave = 0.9 + 0.1 * step(0.5, fract((p.x + p.y + p.z) * 30.0));
            c = p.y > FL + 1.17 ? vec3(0.9, 0.92, 0.95) : vec3(0.08, 0.3, 0.55) * weave;
            if (p.y > FL + 0.46 && p.y < FL + 0.68 && az < 0.7) c = vec3(0.2);
        } else {
            float moq = 0.85 + 0.15 * step(0.5, fract(p.x * 12.0) + fract(p.y * 12.0) * 0.5);
            c = vec3(0.55, 0.08, 0.1) * moq;
            if (abs(n.y) < 0.3 && p.y < FL + 0.3) c = vec3(0.3, 0.17, 0.08);
        }
        return shade(c, n, rd, 0.1, true, occ);
    }
    if (mat == 3) {
        c = style == 0 ? vec3(0.12, 0.13, 0.15) : vec3(0.07, 0.065, 0.06);
        vec3 o = shade(c, n, rd, 0.3, false, occ);
        if (style == 0 && p.y < 0.13 && az > 0.5) {
            float pulse = 0.75 + 0.25 * sin(T * 5.0 - p.x * 2.0);
            o += vec3(0.25, 0.9, 1.0) * pulse * (0.6 + speed);
        }
        return o;
    }
    if (mat == 4) {
        vec2 cc;
        float r;
        if (steamLoco()) {
            vec2 c0;
            if (p.x > 4.6) { c0 = vec2(5.2, 0.5); r = 0.42; }
            else if (p.x < -2.5) { c0 = vec2(-3.5, 0.44); r = 0.42; }
            else { c0 = vec2(clamp(floor(p.x / 1.8 + 0.5), 0.0, 2.0) * 1.8, 0.95); r = 0.85; }
            cc = p.xy - c0;
        } else {
            float bx = p.x > 0.0 ? 4.2 : -4.2;
            float wx = bx + (p.x > bx ? 0.65 : -0.65);
            r = 0.38;
            cc = p.xy - vec2(wx, 0.4);
        }
        float rr = length(cc);
        float a = atan(cc.y, cc.x) + travel / r;
        bool driver = steamLoco() && r > 0.8;
        float spokes = smoothstep(0.82, 0.96, cos(a * (driver ? 16.0 : 10.0)));
        float hub = smoothstep(r * 0.24, r * 0.2, rr);
        float tyre = smoothstep(r * 0.84, r * 0.88, rr);
        // a counterweight on the drivers
        float cw = driver ? step(0.0, cos(a + PI)) * smoothstep(r * 0.4, r * 0.45, rr) * (1.0 - tyre) * step(0.75, cos(a + PI)) : 0.0;
        vec3 body = steamLoco() ? vec3(0.45, 0.05, 0.06) : vec3(0.1);
        vec3 col = mix(body * 0.25, body, max(max(spokes, hub), cw));
        col = mix(col, vec3(0.55, 0.56, 0.58), tyre * (abs(n.z) > 0.7 ? 1.0 : 0.6));
        if (abs(n.z) < 0.7) col = vec3(0.5, 0.5, 0.52);
        return shade(col, n, rd, tyre > 0.5 ? 0.7 : 0.3, false, occ);
    }
    if (mat == 5) {
        // the boiler and cylinders: maroon, rivets in rows
        c = vec3(0.4, 0.055, 0.075);
        float rv = smoothstep(0.03, 0.015, length(vec2(fract(p.x * 4.0) - 0.5, (fract(atan(p.z, p.y - 2.95) * 6.0) - 0.5) * 0.4)));
        c += vec3(0.08) * rv;
        return shade(c, n, rd, 0.7, false, occ);
    }
    if (mat == 6) {
        c = vec3(0.045, 0.045, 0.05);
        if (steamLoco() && p.x > HL + 0.2 && n.x > 0.8) {
            // the smokebox door: its hinges, its dart
            float hinge = step(abs(p.y - 2.55), 0.04) + step(abs(p.y - 3.35), 0.04);
            float dart = smoothstep(0.08, 0.05, length(vec2(p.y - 2.95, p.z)));
            c = mix(c, vec3(0.5, 0.5, 0.5), clamp(hinge * step(p.z, 0.0) + dart, 0.0, 1.0));
        }
        return shade(c, n, rd, 0.55, in_, occ);
    }
    if (mat == 7) {
        return shade(vec3(0.85, 0.64, 0.24), n, rd, 0.95, false, occ);
    }
    if (mat == 11) {
        return shade(vec3(0.75, 0.38, 0.2), n, rd, 0.9, false, occ);
    }
    if (mat == 12) {
        return shade(vec3(0.62, 0.06, 0.04), n, rd, 0.5, false, occ);
    }
    if (mat == 13) {
        return shade(vec3(0.62, 0.64, 0.68), n, rd, 0.85, false, occ);
    }
    if (mat == 8) {
        vec3 o = shade(vec3(0.15), n, rd, 0.6, false, occ);
        if (n.x > 0.6) o = vec3(1.0, 0.92, 0.7) * 2.0;
        return o;
    }
    if (mat == 9) {
        // door leaves: the body's colour outside, a window in each, lit edges as they move
        float gl2;
        float gw;
        vec3 base = in_ ? (style == 0 ? vec3(0.85) : vec3(0.42, 0.25, 0.12)) : (style == 0 ? maglevSkin(p, n, gl2, gw) : steamSkin(p, n, gl2));
        float leafX = p.x - (p.x > 0.0 ? DX : -DX);
        float win = step(abs(p.y - (FL + 1.65)), 0.45) * step(abs(abs(leafX) - 0.35 * (1.0 + door)), 0.22);
        base = mix(base, in_ ? vec3(0.55, 0.66, 0.78) * max(lit, 0.3) : vec3(0.05, 0.07, 0.09), win * 0.85);
        vec3 o = shade(base, n, rd, 0.7, in_, occ);
        float edge = smoothstep(0.04, 0.0, abs(abs(leafX) - door * (DH - 0.05)));
        float moving = clamp(door * (1.0 - door) * 4.0, 0.0, 1.0);
        o += (style == 0 ? vec3(1.0, 0.55, 0.15) : vec3(1.0, 0.75, 0.35)) * edge * (0.25 + moving) * step(p.y, FL + DHT);
        return o;
    }
    if (mat == 10) {
        vec3 o = shade(p.x < 2.8 ? vec3(0.86, 0.87, 0.88) : vec3(0.18, 0.19, 0.21), n, rd, 0.4, true, occ);
        if (p.x > 4.8 && n.y > 0.4) {
            float scr = step(abs(p.z), 1.0) * step(0.15, fract(p.z * 1.6));
            float graph = step(0.5, hash2(floor(vec2(p.z * 30.0, p.x * 20.0 + floor(T)))));
            o += mix(vec3(0.05, 0.25, 0.4), vec3(0.3, 0.9, 1.0), graph) * scr * 0.6;
        }
        return o;
    }
    if (mat == 14) {
        c = vec3(0.8, 0.82, 0.84);
        float grille = step(0.5, fract(p.x * 10.0)) * step(abs(n.y), 0.5);
        return shade(c * (1.0 - grille * 0.3), n, rd, 0.5, false, occ);
    }
    if (mat == 15) {
        if (p.y > CE - 0.7 && abs(p.x) < HL - 0.1) {
            // racks: glass on chrome
            return shade(style == 0 ? vec3(0.55, 0.62, 0.68) : vec3(0.8, 0.62, 0.25), n, rd, 0.9, true, occ);
        }
        float fold = 0.75 + 0.25 * sin(p.x * 60.0);
        return shade(vec3(0.08) * fold, n, rd, 0.2, false, occ);
    }
    // its skin
    if (in_) {
        vec3 ic = interior(p, n, gloss, glow);
        return shade(ic, n, rd, gloss, true, occ) + ic * glow;
    }
    if (style == 0) {
        c = maglevSkin(p, n, gloss, glow);
        vec3 o = shade(c, n, rd, gloss, false, occ);
        vec3 gc = glow > 2.5 ? vec3(1.0, 0.1, 0.08) : glow > 1.5 ? vec3(1.0, 0.98, 0.92) : vec3(0.25, 0.95, 1.0);
        return mix(o, gc * 1.3, clamp(glow, 0.0, 1.0));
    }
    c = steamSkin(p, n, gloss);
    return shade(c, n, rd, gloss, false, occ);
}

vec4 glassColour(vec3 p, vec3 n, vec3 rd) {
    float fres = 0.05 + 0.95 * pow(1.0 - abs(dot(n, rd)), 5.0);
    vec3 tint = style == 0 ? vec3(0.32, 0.4, 0.46) : vec3(0.7, 0.75, 0.68);
    float row = floor(p.y * 26.0);
    float dash = fract(p.x * 0.35 + travel * 0.9 * (0.6 + hash1(row)) + hash1(row + 7.0));
    float streak = step(0.6, hash1(row * 1.7)) * smoothstep(0.0, 0.2, dash) * smoothstep(0.7, 0.2, dash);
    float fast = clamp((speed - 0.1) * 2.0, 0.0, 1.0);
    float night = clamp(0.75 - lit, 0.0, 1.0);
    vec3 refl = env(reflect(rd, n));
    vec3 c = tint * 0.15 * max(lit, 0.2) + refl * fres + vec3(0.85, 0.95, 1.0) * streak * fast * 0.7;
    c += vec3(1.0, 0.8, 0.5) * night * 0.45 * (1.0 - fres);
    float a = clamp((style == 0 ? 0.28 : 0.16) + fres * 0.6 + streak * fast * 0.5 + night * 0.25, 0.0, 0.92);
    return vec4(c / max(a, 0.01), a);
}

void main() {
    T = GameTime * 1200.0;
    upL = normalize(vec3(dot(vec3(0, 1, 0), F), dot(vec3(0, 1, 0), U), dot(vec3(0, 1, 0), S)));
    vec3 sunW = normalize(vec3(0.35, 1.0, 0.25));
    sunL = normalize(vec3(dot(sunW, F), dot(sunW, U), dot(sunW, S)));
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
    for (int i = 0; i < 220 && t < t1 && a < 0.97; i++) {
        vec3 p = ro + rd * t;
        int mat;
        float s = solid(p, mat);
        float g = glass(p);
        if (s < 0.0015) {
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
        float dt = max(min(s, g) * 0.8, 0.003);
        if (style == 0 && p.y < 0.1) {
            float gl = smoothstep(1.1, 0.6, abs(p.z)) * smoothstep(HL, HL - 0.6, abs(p.x)) * smoothstep(-0.15, 0.05, p.y);
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
