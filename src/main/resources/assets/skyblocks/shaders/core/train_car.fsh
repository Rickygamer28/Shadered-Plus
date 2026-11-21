#version 150

// A train car, traced (its own space: x along it - its nose +x - y up from the rails, z across):
//  maglev (0): a white rounded body hovering over the rails, a band of windows, a cyan strip along it, a glow under it;
//              the locomotive's nose sloped down to a point, its windscreen across the slope, headlights in it.
//  steam (1):  maroon carriages with cream window bands and a rounded roof on bogies; the locomotive black - its boiler,
//              chimney and dome, its cab behind with windows, red driving wheels turning as it goes, a lamp on its front.
// Inside: a floor, seats, warm lights. Its windows are glass, seen through both ways, streaked as it goes faster.
// Writes its depth: riders inside show through its windows.

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
flat in float speed;
flat in float travel;
flat in float lit;

out vec4 fragColor;

const vec3 LO = vec3(-2.8, -0.15, -1.15), HI = vec3(2.8, 2.95, 1.15);
const float WALL = 0.07;

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

// a cylinder along x (radius r, half length h), round (cy, cz)
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

bool loco() {
    return kind == 0;
}

// ---- the body's outside (a closed shape; the shell is its skin) ----
float body(vec3 p) {
    if (style == 0) {
        float d = sdRoundBox(p - vec3(0.0, 1.45, 0.0), vec3(loco() ? 2.7 : 2.45, 0.95, 0.95), 0.42);
        if (loco()) {
            // (its nose: the top sloping down to the front)
            d = max(d, dot(p - vec3(1.1, 2.4, 0.0), normalize(vec3(0.62, 1.0, 0.0))));
            d = max(d, dot(p - vec3(2.7, 0.5, 0.0), normalize(vec3(1.0, -0.7, 0.0))));
        }
        return d;
    }
    if (loco()) {
        // (the cab: the rest's solid - boiler and all)
        return sdBox(p - vec3(-1.45, 1.55, 0.0), vec3(1.0, 0.85, 0.95));
    }
    float b = sdRoundBox(p - vec3(0.0, 1.3, 0.0), vec3(2.45, 0.8, 0.95), 0.08);
    float roof = max(length(vec2((p.y - 1.55) * 1.7, p.z)) - 1.0, abs(p.x) - 2.5);
    return min(b, max(roof, 1.55 - p.y));
}

// where its windows are (on its skin)
bool window(vec3 p) {
    if (style == 0) {
        if (loco() && p.x > 1.25 && p.y > 1.35) {
            return abs(p.z) < 0.78 || p.y > 1.7; // (its windscreen)
        }
        if (p.y < 1.35 || p.y > 2.05 || abs(p.x) > (loco() ? 0.9 : 2.0)) {
            return false;
        }
        float c = fract((p.x + 2.0) / 1.0) - 0.5;
        return abs(p.z) > 0.5 && abs(c) < 0.38;
    }
    if (loco()) {
        // (the cab's: its sides and its front, either side of the boiler)
        if (p.y < 1.45 || p.y > 2.15) return false;
        if (abs(p.z) > 0.6) return abs(p.x + 1.4) < 0.5;
        return p.x > -0.6 && abs(p.z) > 0.25;
    }
    if (p.y < 1.2 || p.y > 1.85 || abs(p.x) > 2.05) return false;
    float c = fract((p.x + 2.0) / 0.82) - 0.5;
    return abs(p.z) > 0.5 && abs(c) < 0.33;
}

// what's solid, and which: 1 skin, 2 floor, 3 seats, 4 under (bogies, skirt), 5 wheels, 6 boiler, 7 chimney / dome, 8 brass, 9 lamp
float solid(vec3 p, out int mat) {
    float b = body(p);
    float sh = abs(b + WALL * 0.5) - WALL * 0.5;
    if (sh < 0.03 && window(p)) {
        sh = 1.0;
    }
    float d = sh;
    mat = 1;
    // (the ends of a car: a door's gap between cars - shut)
    float fl = max(sdBox(p - vec3(0.0, 0.5, 0.0), vec3(2.7, 0.06, 0.92)), b);
    if (fl < d) { d = fl; mat = 2; }
    if (!(style == 1 && loco())) {
        float seats = 1e3;
        if (loco()) {
            float sx = style == 0 ? 0.9 : -1.5;
            seats = min(sdBox(p - vec3(sx, 0.72, 0.0), vec3(0.24, 0.16, 0.26)), sdBox(p - vec3(sx - 0.24, 1.0, 0.0), vec3(0.05, 0.42, 0.26)));
            // (its desk under the windscreen)
            if (style == 0) seats = min(seats, max(sdBox(p - vec3(1.65, 1.05, 0.0), vec3(0.4, 0.06, 0.6)), b));
        } else {
            for (int i = 0; i < 4; i++) {
                float sx = (i < 2 ? -1.1 : 1.1), sz = ((i & 1) == 0 ? -0.45 : 0.45);
                float back = sx < 0.0 ? -0.26 : 0.26;
                seats = min(seats, sdBox(p - vec3(sx, 0.72, sz), vec3(0.24, 0.16, 0.24)));
                seats = min(seats, sdBox(p - vec3(sx + back, 1.02, sz), vec3(0.05, 0.42, 0.24)));
            }
        }
        if (seats < d) { d = seats; mat = 3; }
    }
    if (style == 0) {
        // (its skirt over the rails)
        float sk = sdRoundBox(p - vec3(0.0, 0.32, 0.0), vec3(loco() ? 2.55 : 2.35, 0.14, 0.7), 0.1);
        if (sk < d) { d = sk; mat = 4; }
    } else {
        // bogies and wheels
        float bg = 1e3, wh = 1e3;
        if (!loco()) {
            for (int i = -1; i <= 1; i += 2) {
                bg = min(bg, sdBox(p - vec3(1.75 * float(i), 0.38, 0.0), vec3(0.7, 0.1, 0.62)));
                for (int j = -1; j <= 1; j += 2) {
                    vec3 q = p - vec3(1.75 * float(i) + 0.42 * float(j), 0.0, 0.0);
                    q.z = abs(q.z);
                    wh = min(wh, cylZ(q, vec2(0.0, 0.3), 0.29, 0.66, 0.05));
                }
            }
        } else {
            for (int i = 0; i < 3; i++) {
                vec3 q = p - vec3(1.6 - float(i) * 1.05, 0.0, 0.0);
                q.z = abs(q.z);
                wh = min(wh, cylZ(q, vec2(0.0, 0.47), 0.46, 0.72, 0.06));
            }
            bg = min(bg, sdBox(p - vec3(0.2, 0.68, 0.0), vec3(2.5, 0.06, 1.0))); // (its running board)
            bg = min(bg, sdBox(p - vec3(0.5, 0.4, 0.0), vec3(1.9, 0.2, 0.45)));  // (its frame)
            // (its cowcatcher)
            bg = min(bg, max(sdBox(p - vec3(2.55, 0.35, 0.0), vec3(0.25, 0.3, 0.75)), dot(p - vec3(2.4, 0.65, 0.0), normalize(vec3(0.8, 0.6, 0.0)))));
            float boiler = cylX(p, vec2(1.42, 0.0), 0.66, 1.05, 1.5);
            if (boiler < d) { d = boiler; mat = 6; }
            float ch = min(cylY(p, vec2(2.05, 0.0), 0.19, 2.25, 0.4), length(p - vec3(0.85, 2.02, 0.0)) - 0.3);
            ch = min(ch, cylY(p, vec2(2.05, 0.0), 0.26, 2.62, 0.06));
            if (ch < d) { d = ch; mat = 7; }
            float brass = min(cylX(p, vec2(1.42, 0.0), 0.68, 0.2, 0.04), cylX(p, vec2(1.42, 0.0), 0.68, 1.6, 0.04));
            if (brass < d) { d = brass; mat = 8; }
            float cabRoof = sdBox(p - vec3(-1.45, 2.47, 0.0), vec3(1.12, 0.05, 1.05));
            if (cabRoof < d) { d = cabRoof; mat = 7; }
            float lamp = length(p - vec3(2.58, 1.42, 0.0)) - 0.16;
            if (lamp < d) { d = lamp; mat = 9; }
        }
        if (bg < d) { d = bg; mat = 4; }
        if (wh < d) { d = wh; mat = 5; }
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

// the glass: a thin sheet in its windows
float glass(vec3 p) {
    float b = body(p);
    float g = abs(b + WALL * 0.5) - 0.008;
    return (g < 0.05 && window(p)) ? g : max(g, 0.05);
}

vec3 bodyNormal(vec3 p) {
    vec2 e = vec2(0.004, 0.0);
    return normalize(vec3(body(p + e.xyy) - body(p - e.xyy), body(p + e.yxy) - body(p - e.yxy), body(p + e.yyx) - body(p - e.yyx)));
}

vec3 sunDir() {
    vec3 L = normalize(vec3(0.35, 1.0, 0.25));
    return normalize(vec3(dot(L, F), dot(L, U), dot(L, S)));
}

// a colour lit: daylight on it (its lit) and its own light inside
vec3 shade(vec3 c, vec3 n, vec3 rd, float gloss, bool inside) {
    vec3 L = sunDir();
    float dif = max(dot(n, L), 0.0);
    float amb = 0.45 + 0.25 * n.y;
    vec3 h = normalize(L - rd);
    float sp = pow(max(dot(n, h), 0.0), 40.0) * gloss;
    vec3 day = c * (amb + dif * 0.8) + vec3(sp);
    vec3 lamp = inside ? c * vec3(1.0, 0.86, 0.62) * 0.55 : vec3(0.0);
    return day * lit + lamp;
}

vec3 solidColour(vec3 p, vec3 n, vec3 rd, int mat) {
    bool inside = body(p + n * 0.02) < -WALL * 0.6 || (mat == 2 || mat == 3);
    if (mat == 2) {
        float plank = step(0.5, fract(p.z * 3.0));
        return shade(style == 0 ? vec3(0.32, 0.34, 0.38) : mix(vec3(0.36, 0.22, 0.12), vec3(0.3, 0.18, 0.1), plank), n, rd, 0.1, true);
    }
    if (mat == 3) {
        return shade(style == 0 ? vec3(0.12, 0.45, 0.62) : vec3(0.5, 0.1, 0.12), n, rd, 0.1, true);
    }
    if (mat == 4) {
        vec3 c = style == 0 ? vec3(0.2, 0.22, 0.26) : vec3(0.07);
        vec3 o = shade(c, n, rd, 0.3, false);
        if (style == 0) {
            // (the glow under it: a cyan band round its skirt)
            float band = smoothstep(0.06, 0.0, abs(p.y - 0.24));
            o += vec3(0.2, 0.9, 1.0) * band * (0.8 + 0.2 * sin(T * 4.0 + p.x * 3.0));
        }
        return o;
    }
    if (mat == 5) {
        // (red wheels, their spokes turning as it goes)
        vec3 q = p;
        float r = loco() ? 0.46 : 0.29;
        vec2 c = vec2(q.x - (loco() ? (1.6 - floor((1.6 - q.x) / 1.05 + 0.5) * 1.05) : 0.0), q.y - (loco() ? 0.47 : 0.3));
        if (!loco()) c.x = q.x - (floor(q.x / 0.84 + 0.5) * 0.84);
        float a = atan(c.y, c.x) + travel / r;
        float spoke = smoothstep(0.75, 0.95, cos(a * 8.0));
        float rim = smoothstep(r * 0.78, r * 0.84, length(c));
        vec3 base = loco() ? vec3(0.55, 0.08, 0.06) : vec3(0.12);
        return shade(mix(base * 0.5, base, max(spoke, rim)), n, rd, 0.5, false) + (abs(n.z) > 0.9 ? vec3(0.0) : vec3(0.05));
    }
    if (mat == 6) {
        return shade(p.x > 2.3 ? vec3(0.04) : vec3(0.08, 0.09, 0.1), n, rd, 0.9, false);
    }
    if (mat == 7) {
        return shade(vec3(0.06), n, rd, 0.6, false);
    }
    if (mat == 8) {
        return shade(vec3(0.85, 0.65, 0.25), n, rd, 1.2, false);
    }
    if (mat == 9) {
        return vec3(1.0, 0.93, 0.7) * 1.4;
    }
    // its skin
    if (inside) {
        return shade(style == 0 ? vec3(0.86, 0.88, 0.9) : vec3(0.55, 0.38, 0.24), n, rd, 0.1, true);
    }
    vec3 c;
    float gloss = 0.6;
    if (style == 0) {
        c = vec3(0.93, 0.95, 0.97);
        float strip = smoothstep(0.035, 0.0, abs(p.y - 1.2)) * step(0.4, abs(p.z) + max(p.x - 2.0, 0.0));
        if (strip > 0.0) {
            return mix(shade(c, n, rd, gloss, false), vec3(0.25, 0.95, 1.0) * 1.3, strip);
        }
        if (loco() && p.x > 2.35 && p.y < 1.1 && p.y > 0.85 && abs(p.z) > 0.3 && abs(p.z) < 0.75) {
            return vec3(1.0, 0.98, 0.9) * 1.5; // (its headlights)
        }
        if (p.y < 0.75) c = vec3(0.3, 0.33, 0.38);
        gloss = 1.0;
    } else {
        c = loco() ? vec3(0.32, 0.06, 0.07) : vec3(0.42, 0.07, 0.09);
        if (!loco() && p.y > 1.15 && p.y < 1.9) c = vec3(0.88, 0.82, 0.66);
        if (p.y > 1.97 && !loco()) c = vec3(0.16, 0.16, 0.18);
        float line = smoothstep(0.02, 0.0, abs(p.y - 1.1)) + smoothstep(0.02, 0.0, abs(p.y - 1.95));
        c = mix(c, vec3(0.85, 0.65, 0.25), clamp(line, 0.0, 1.0));
        gloss = 0.35;
    }
    return shade(c, n, rd, gloss, false);
}

// the glass: tinted, a sheen, and streaks as it goes faster
vec4 glassColour(vec3 p, vec3 n, vec3 rd) {
    float fres = pow(1.0 - abs(dot(n, rd)), 3.0);
    vec3 tint = style == 0 ? vec3(0.55, 0.78, 0.95) : vec3(0.75, 0.8, 0.7);
    float row = floor(p.y * 26.0);
    float dash = fract(p.x * 0.35 + travel * 0.9 * (0.6 + hash1(row)) + hash1(row + 7.0));
    float streak = step(0.6, hash1(row * 1.7)) * smoothstep(0.0, 0.2, dash) * smoothstep(0.7, 0.2, dash);
    float fast = clamp((speed - 0.08) * 2.2, 0.0, 1.0);
    vec3 c = tint * (0.25 + 0.5 * fres) * max(lit, 0.3) + vec3(0.85, 0.95, 1.0) * streak * fast * 0.7;
    // (at night: its cars' lights glowing warm through it)
    c += vec3(1.0, 0.78, 0.45) * clamp(0.75 - lit, 0.0, 1.0) * 0.6 * (1.0 - fres);
    return vec4(c, clamp(0.16 + fres * 0.45 + streak * fast * 0.5 + clamp(0.75 - lit, 0.0, 1.0) * 0.4, 0.0, 0.9));
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
    for (int i = 0; i < 150 && t < t1 && a < 0.97; i++) {
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
            vec3 n = bodyNormal(p);
            vec4 gc = glassColour(p, n, rd);
            col += (1.0 - a) * gc.rgb * gc.a;
            a += (1.0 - a) * gc.a;
            if (depthT < 0.0) depthT = t;
            t += 0.03;
            continue;
        }
        float dt = max(min(s, g), 0.004);
        if (style == 0 && p.y < 0.3) {
            // (the glow under a maglev: between it and the rails)
            float gl = smoothstep(0.7, 0.3, abs(p.z)) * smoothstep(2.6, 2.1, abs(p.x)) * smoothstep(-0.15, 0.2, p.y);
            dt = min(dt, 0.05);
            col += (1.0 - a) * vec3(0.15, 0.75, 1.0) * gl * dt * (1.2 + speed * 2.0);
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
