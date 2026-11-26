#version 150

// Shadered+'s storage skins (client/StorageSkins), traced through their box in 3D (in units of the box's half-size: the
// box is -1 .. 1), each showing how full it is:
//  0 Dyson sphere: a blazing star in a swarm of turning rings of solar panels - more rings, fuller ones, as it fills;
//  1 tesseract:    a 4D cube turning itself inside out round a bright core, another smaller one folded inside for
//                  each third;
//  2 item galaxy:  spiral arms made of the items held - their own pictures (U0 .., on the block atlas) - a core - more
//                  stars, and a third arm, as it fills;
//  3 water planet: a ball of the fluids held, in bands by how much of each, waves rolling across, a droplet moon -
//                  swelling as it fills;
//  4 bubbles:      bubbles of each fluid, sized by how much, more as it fills, the fluid sloshing inside, bobbing;
//  5 fluid helix:  a strand for each fluid twisting round the others, joined by rungs, bubbles rising in them,
//                  four to a helix (more helices for more fluids), from a swirling pool - climbing as it fills.
// C0 .. C9: colours (rgb) and shares (a) - the dye's colour first for the singularity's own; Info.x how many, Info.y
// which glow (a bit each).

uniform float GameTime;
uniform vec4 C0;
uniform vec4 C1;
uniform vec4 C2;
uniform vec4 C3;
uniform vec4 C4;
uniform vec4 C5;
uniform vec4 C6;
uniform vec4 C7;
uniform vec4 C8;
uniform vec4 C9;
uniform vec4 Info;
uniform sampler2D Sampler0;
uniform vec4 U0;
uniform vec4 U1;
uniform vec4 U2;
uniform vec4 U3;
uniform vec4 U4;
uniform vec4 U5;
uniform vec4 U6;
uniform vec4 U7;
uniform vec4 U8;
uniform vec4 U9;
uniform vec4 V0;
uniform vec4 V1;
uniform vec4 V2;
uniform vec4 V3;
uniform vec4 V4;
uniform vec4 V5;
uniform vec4 V6;
uniform vec4 V7;
uniform vec4 V8;
uniform vec4 V9;

in vec3 lp;
flat in vec3 eye;
flat in float half_;
flat in int skin;
flat in float full;
flat in float big;
flat in float strength;

out vec4 fragColor;

const float TAU = 6.2831853;

float hash1(float n) {
    return fract(sin(n * 91.345) * 47453.5453);
}

float hash3(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash3(i), hash3(i + vec3(1, 0, 0)), f.x), mix(hash3(i + vec3(0, 1, 0)), hash3(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash3(i + vec3(0, 0, 1)), hash3(i + vec3(1, 0, 1)), f.x), mix(hash3(i + vec3(0, 1, 1)), hash3(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec3(3.1, 1.7, 5.3);
        a *= 0.5;
    }
    return v;
}

vec3 rotY(vec3 p, float a) {
    float c = cos(a), s = sin(a);
    return vec3(c * p.x + s * p.z, p.y, -s * p.x + c * p.z);
}

// the ray's entry and exit of a ball (radius r round the middle): (t0, t1), or t1 < t0 if it misses
vec2 ball(vec3 ro, vec3 rd, vec3 c, float r) {
    vec3 oc = ro - c;
    float b = dot(oc, rd);
    float h = b * b - (dot(oc, oc) - r * r);
    if (h < 0.0) return vec2(1.0, -1.0);
    h = sqrt(h);
    return vec2(-b - h, -b + h);
}

// the ray (from t0 to t1) and a segment: how close they come, and how far along the ray that is
vec2 raySeg(vec3 ro, vec3 rd, float t0, float t1, vec3 a, vec3 b) {
    vec3 ab = b - a;
    vec3 w = ro - a;
    float aa = dot(ab, ab), bb = dot(ab, rd), cc = dot(ab, w), dd = dot(rd, w);
    float den = aa - bb * bb;
    float s = den > 1e-6 ? clamp((cc - bb * dd) / den, 0.0, 1.0) : 0.0;
    float t = clamp(bb * s - dd, t0, t1);
    s = clamp((dot(ro + rd * t - a, ab)) / max(aa, 1e-6), 0.0, 1.0);
    return vec2(length(ro + rd * t - (a + ab * s)), t);
}

vec4 pal(int i) {
    if (i <= 0) return C0;
    if (i == 1) return C1;
    if (i == 2) return C2;
    if (i == 3) return C3;
    if (i == 4) return C4;
    if (i == 5) return C5;
    if (i == 6) return C6;
    if (i == 7) return C7;
    if (i == 8) return C8;
    return C9;
}

// the item galaxy's items: where each one's picture is on the block atlas (Sampler0)
vec4 spriteOf(int i) {
    if (i <= 0) return U0;
    if (i == 1) return U1;
    if (i == 2) return U2;
    if (i == 3) return U3;
    if (i == 4) return U4;
    if (i == 5) return U5;
    if (i == 6) return U6;
    if (i == 7) return U7;
    if (i == 8) return U8;
    return U9;
}

int count() {
    return clamp(int(Info.x + 0.5), 1, 10);
}

bool glows(int i) {
    return mod(floor(Info.y / pow(2.0, float(i))), 2.0) > 0.5;
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// over: the nearer colour first, then what's behind
vec4 over(vec4 front, vec4 back) {
    float a = front.a + back.a * (1.0 - front.a);
    return vec4((front.rgb * front.a + back.rgb * back.a * (1.0 - front.a)) / max(a, 1e-4), a);
}

// ---- 0: Dyson sphere (a swarm of rings round a star) ----

// a ring's frame: its axis turned by (ax, az)
mat3 ringFrame(float ax, float az) {
    float cx = cos(ax), sx = sin(ax), cz = cos(az), sz = sin(az);
    mat3 rx = mat3(1.0, 0.0, 0.0, 0.0, cx, sx, 0.0, -sx, cx);
    mat3 rz = mat3(cz, sz, 0.0, -sz, cz, 0.0, 0.0, 0.0, 1.0);
    return rz * rx;
}

// one ring of panels (a band round the star, its inside facing it): where the ray meets a panel (t), or -1; the panel's
// look comes back in col
float ring(vec3 ro, vec3 rd, mat3 fr, float R, float h, float spin, float fill, float k, vec3 starCol, out vec4 col) {
    vec3 o = ro * fr, d = rd * fr; // (into the ring's own frame: its axis y)
    float a = dot(d.xz, d.xz), b = dot(o.xz, d.xz), c = dot(o.xz, o.xz) - R * R;
    float disc = b * b - a * c;
    col = vec4(0.0);
    if (a < 1e-6 || disc < 0.0) return -1.0;
    disc = sqrt(disc);
    for (int side = 0; side < 2; side++) {
        float t = (-b + (side == 0 ? -disc : disc)) / a;
        if (t <= 0.0) continue;
        vec3 p = o + d * t;
        if (abs(p.y) > h) continue;
        float ang = atan(p.z, p.x) / 6.2832 + 0.5 + spin;
        float cells = 22.0 + 6.0 * k;
        float u = ang * cells, v = (p.y + h) / (2.0 * h);
        vec2 cell = vec2(floor(u), k);
        if (hash2(cell + 0.37) > fill) continue;
        float fu = fract(u);
        bool frame = fu < 0.07 || fu > 0.93 || v < 0.08 || v > 0.92;
        vec3 n = normalize(vec3(p.x, 0.0, p.z));
        bool inner = dot(d, n) > 0.0; // (looking at the side facing the star)
        vec3 c3;
        if (frame) {
            c3 = vec3(0.95, 0.72, 0.3) * (inner ? 1.2 : 0.55);
        } else {
            // (solar cells: deep blue, ruled in a fine grid, the inside bright with the star's light)
            float grid = step(0.88, fract(fu * 4.0)) + step(0.85, fract(v * 3.0));
            vec3 cellCol = mix(vec3(0.06, 0.1, 0.32), vec3(0.16, 0.26, 0.62), hash2(cell + 4.1));
            c3 = inner ? mix(cellCol * 1.6, starCol, 0.35) : cellCol * 0.75 + vec3(0.25, 0.3, 0.45) * pow(1.0 - abs(dot(normalize(d), n)), 4.0);
            c3 = mix(c3, vec3(0.75, 0.6, 0.35), clamp(grid, 0.0, 1.0) * 0.35);
        }
        col = vec4(c3, 1.0);
        return t;
    }
    return -1.0;
}

vec4 dyson(vec3 ro, vec3 rd, float t) {
    vec3 starCol = mix(vec3(1.0, 0.8, 0.45), C0.rgb, 0.3);
    float rs = 0.2 + 0.07 * full;
    int rings = 1 + int(full * 2.99);
    float fill = 0.35 + 0.65 * full;
    float best = 1e9;
    vec4 hit = vec4(0.0);
    for (int k = 0; k < 3; k++) {
        if (k >= rings) break;
        float fk = float(k);
        mat3 fr = ringFrame(k == 0 ? 0.55 : k == 1 ? 1.35 : 2.2, k == 0 ? 0.45 : k == 1 ? -0.6 : 0.9);
        vec4 col;
        float th = ring(ro, rd, fr, 0.46 + 0.16 * fk, 0.12 - 0.015 * fk, t * (0.03 + 0.02 * fk) * (k == 1 ? -1.0 : 1.0), fill, fk, starCol, col);
        if (th > 0.0 && th < best) {
            best = th;
            hit = col;
        }
    }
    vec2 st = ball(ro, rd, vec3(0.0), rs);
    float bb = max(-dot(ro, rd), 0.0);
    float d = length(ro + rd * bb);
    // (the star: boiling, bright; its corona and flares)
    vec4 star = vec4(0.0);
    if (st.y > st.x && st.x > 0.0 && st.x < best) {
        vec3 n = normalize(ro + rd * st.x);
        float boil = fbm(n * 6.0 + vec3(0.0, t * 0.25, t * 0.1));
        float spots = smoothstep(0.62, 0.7, fbm(n * 3.0 - t * 0.05));
        vec3 sc = mix(starCol * 1.1, vec3(1.0, 0.98, 0.9), boil) * (1.0 - spots * 0.35);
        sc *= 1.0 + 0.4 * pow(1.0 - max(dot(n, -rd), 0.0), 2.0);
        return vec4(sc * 1.25, 1.0);
    }
    float ang = atan(dot(ro + rd * bb, vec3(0.0, 1.0, 0.0)), dot(ro + rd * bb, vec3(1.0, 0.0, 0.0)));
    float flare = pow(max(sin(ang * 5.0 + t * 0.6) * 0.5 + 0.5, 0.0), 6.0);
    float corona = exp(-max(d - rs, 0.0) * 9.0) * (0.75 + 0.5 * flare) + exp(-d * d * 3.0) * 0.25;
    vec4 glow = vec4(starCol * 1.2, clamp(corona, 0.0, 1.0));
    // (nearly full: a fine golden lattice closes round the whole swarm)
    vec4 lattice = vec4(0.0);
    float lat = smoothstep(0.7, 0.95, full);
    vec2 sh = ball(ro, rd, vec3(0.0), 0.92);
    if (lat > 0.0 && sh.y > sh.x) {
        for (int side = 0; side < 2; side++) {
            float ts = side == 0 ? sh.x : sh.y;
            if (ts <= 0.0 || ts > best) continue;
            vec3 n = rotY(normalize(ro + rd * ts), t * 0.05);
            float lo = atan(n.z, n.x) / 6.2832 * 16.0, la = asin(clamp(n.y, -1.0, 1.0)) / 3.14159 * 8.0;
            float line = max(smoothstep(0.465, 0.49, abs(fract(lo) - 0.5)), smoothstep(0.465, 0.49, abs(fract(la) - 0.5)));
            vec4 lc = vec4(vec3(1.0, 0.78, 0.35) * (side == 0 ? 1.0 : 0.6), line * lat * (side == 0 ? 0.8 : 0.45));
            lattice = over(lattice, lc);
        }
    }
    if (best < 1e8) {
        // (the corona in front of a panel adds its light; behind it, hidden)
        float front = bb < best ? 1.0 : 0.25;
        return over(lattice, over(vec4(glow.rgb, glow.a * front * 0.6), hit));
    }
    return over(lattice, glow);
}

// ---- 1: tesseract ----

vec4 tess4(int i, float a, float b) {
    vec4 v = vec4((i & 1) != 0 ? 1.0 : -1.0, (i & 2) != 0 ? 1.0 : -1.0, (i & 4) != 0 ? 1.0 : -1.0, (i & 8) != 0 ? 1.0 : -1.0);
    float x = v.x * cos(a) - v.w * sin(a), w = v.x * sin(a) + v.w * cos(a);
    float y = v.y * cos(b) - v.z * sin(b), z = v.y * sin(b) + v.z * cos(b);
    return vec4(x, y, z, w);
}

// a 4D corner seen in 3D (xyz), and how near it is in the fourth way round (w: -1 far .. 1 near)
vec4 tess(int i, float a, float b, float sc) {
    vec4 v = tess4(i, a, b);
    float k = 1.0 / (3.2 - v.w);
    return vec4(v.xyz * k * sc * 1.3, v.w);
}

// a ray against a capsule (a rod with round ends) from pa to pb, radius r: how far along, or -1
float capsule(vec3 ro, vec3 rd, vec3 pa, vec3 pb, float r) {
    vec3 ba = pb - pa, oa = ro - pa;
    float baba = dot(ba, ba), bard = dot(ba, rd), baoa = dot(ba, oa), rdoa = dot(rd, oa), oaoa = dot(oa, oa);
    float a = baba - bard * bard, b = baba * rdoa - baoa * bard, c = baba * oaoa - baoa * baoa - r * r * baba;
    float h = b * b - a * c;
    if (h >= 0.0 && a > 1e-8) {
        float t = (-b - sqrt(h)) / a;
        float y = baoa + t * bard;
        if (y > 0.0 && y < baba) return t;
        vec3 oc = y <= 0.0 ? oa : ro - pb;
        b = dot(rd, oc);
        c = dot(oc, oc) - r * r;
        h = b * b - c;
        if (h > 0.0) return -b - sqrt(h);
    }
    return -1.0;
}

vec4 tesseract(vec3 ro, vec3 rd, float t0, float t1, float t) {
    vec3 hue = mix(vec3(0.62, 0.5, 1.0), C0.rgb, 0.35);
    vec3 far_ = vec3(0.25, 0.85, 1.0); // (its far side, in the fourth way round: cyan)
    vec3 L = normalize(vec3(0.4, 0.8, 0.3));
    float best = 1e9;
    vec3 bestN = vec3(0.0), bestC = vec3(0.0);
    float halo = 0.0;
    vec3 haloC = hue;
    float tMin = max(t0, 0.18); // (rods right by the eye - standing in a chamber - aren't drawn: no bar across the view)
    int layers = full > 0.55 ? 2 : 1;
    for (int Ly = 0; Ly < 2; Ly++) {
        if (Ly >= layers) break;
        float sc = Ly == 0 ? 1.0 : 0.46;
        float a = t * (Ly == 0 ? 0.45 : -0.7), b = t * (Ly == 0 ? 0.3 : 0.5) + float(Ly) * 1.7;
        vec4 P[16];
        for (int i = 0; i < 16; i++) P[i] = tess(i, a, b, sc);
        float rod = (Ly == 0 ? 0.021 : 0.014);
        for (int i = 0; i < 16; i++) {
            // its corners: little glowing balls
            vec3 pc = P[i].xyz;
            float rb = rod * 1.9 * (0.75 + 0.35 * (P[i].w * 0.5 + 0.5));
            vec2 h = ball(ro, rd, pc, rb);
            if (h.y > h.x && h.x > tMin && h.x < best) {
                best = h.x;
                bestN = normalize(ro + rd * h.x - pc);
                bestC = mix(far_, vec3(1.0, 0.95, 1.0), 0.35 + 0.35 * (P[i].w * 0.5 + 0.5));
            }
            for (int d = 0; d < 4; d++) {
                int j = i ^ (1 << d);
                if (j < i) continue;
                float w = (P[i].w + P[j].w) * 0.25 + 0.5;
                float r = rod * (0.7 + 0.45 * w);
                float th = capsule(ro, rd, P[i].xyz, P[j].xyz, r);
                if (th > tMin && th < best) {
                    best = th;
                    vec3 p = ro + rd * th, ab = P[j].xyz - P[i].xyz;
                    vec3 q = P[i].xyz + ab * clamp(dot(p - P[i].xyz, ab) / dot(ab, ab), 0.0, 1.0);
                    bestN = normalize(p - q);
                    bestC = mix(far_, hue, w);
                }
                // (a faint glow round each rod - the strongest, not added up: no white fog where they cross)
                vec2 g = raySeg(ro, rd, tMin, t1, P[i].xyz, P[j].xyz);
                float gl = exp(-g.x / (rod * 1.6)) * 0.35;
                if (gl > halo) {
                    halo = gl;
                    haloC = mix(far_, hue, w);
                }
            }
        }
    }
    // its heart: a ball of light, swirling
    float rc = 0.07 + 0.06 * full;
    vec2 hc = ball(ro, rd, vec3(0.0), rc);
    vec4 heart = vec4(0.0);
    if (hc.y > hc.x && hc.x > t0) {
        vec3 n = normalize(ro + rd * hc.x);
        float sw = fbm(rotY(n, t * 0.8) * 4.0 + vec3(0.0, t * 0.4, 0.0));
        float f = max(dot(n, -rd), 0.0);
        heart = vec4(mix(hue * 1.1, vec3(1.0, 0.97, 1.0), 0.55 + 0.45 * f) * (0.85 + 0.35 * sw), 1.0);
    }
    float bb = max(-dot(ro, rd), 0.0);
    float dc = length(ro + rd * bb);
    float aura = exp(-max(dc - rc, 0.0) * 22.0) * 0.55 * (0.85 + 0.15 * sin(t * 3.0));
    vec4 back = over(vec4(hue * 1.2 + 0.2, aura), vec4(haloC, halo));
    if (best < 1e8) {
        float dif = max(dot(bestN, L), 0.0);
        float facing = max(dot(bestN, -rd), 0.0);
        float spec = pow(max(dot(reflect(rd, bestN), L), 0.0), 40.0);
        // (a glowing glass rod: bright through its middle, its colour deepening to its edges, a shine on it)
        vec3 c = mix(bestC * 0.45, mix(bestC, vec3(1.0), 0.45) * 1.25, pow(facing, 1.4)) * (0.8 + 0.3 * dif) + vec3(spec * 0.7);
        if (heart.a > 0.0 && hc.x < best) return heart;
        return vec4(c, 1.0);
    }
    if (heart.a > 0.0) return heart;
    return back;
}

// ---- 2: item galaxy ----

// a block item's top picture (for one drawn as a cube; none for a flat one)
vec4 topOf(int i) {
    if (i <= 0) return V0;
    if (i == 1) return V1;
    if (i == 2) return V2;
    if (i == 3) return V3;
    if (i == 4) return V4;
    if (i == 5) return V5;
    if (i == 6) return V6;
    if (i == 7) return V7;
    if (i == 8) return V8;
    return V9;
}

// a ray against a box of half-size hs round the origin: (in, out), out < in for a miss; the face it goes in by
vec2 boxHit(vec3 ro, vec3 rd, vec3 hs, out vec3 nIn) {
    vec3 inv = 1.0 / (sign(rd) * max(abs(rd), vec3(1e-6)));
    vec3 n = -ro * inv, k = abs(inv) * hs;
    vec3 tA = n - k, tB = n + k;
    float tN = max(max(tA.x, tA.y), tA.z), tF = min(min(tB.x, tB.y), tB.z);
    nIn = -sign(rd) * step(tA.yzx, tA.xyz) * step(tA.zxy, tA.xyz);
    return vec2(tN, tF);
}

vec3 picture(vec4 sp, vec2 f) {
    return textureLod(Sampler0, mix(sp.xy, sp.zw, vec2(f.x, 1.0 - f.y)), 0.0).rgb;
}

// one item, in its own frame (centred, turned): a little cube for a block, otherwise its picture made solid - a
// pixel thick, like a dropped item; (how far along, or -1; its colour unlit; its normal)
float itemHit(vec3 ro, vec3 rd, int which, float s, out vec3 col, out vec3 nrm) {
    vec4 sp = spriteOf(which), tp = topOf(which);
    col = vec3(0.0);
    nrm = vec3(0.0, 0.0, 1.0);
    vec3 nIn;
    if (sp.z <= sp.x) {
        // (no picture: a little cube of its colour)
        vec2 h = boxHit(ro, rd, vec3(s * 0.5), nIn);
        if (h.y < h.x || h.y < 0.0) return -1.0;
        col = pal(which).rgb;
        nrm = nIn;
        return h.x;
    }
    if (tp.z > tp.x) {
        float cs = s * 0.62;
        vec2 h = boxHit(ro, rd, vec3(cs), nIn);
        if (h.y < h.x || h.y < 0.0) return -1.0;
        vec3 p = (ro + rd * h.x) / cs * 0.5 + 0.5;
        nrm = nIn;
        if (abs(nIn.y) > 0.5) col = picture(tp, p.xz);
        else if (abs(nIn.x) > 0.5) col = picture(sp, p.zy);
        else col = picture(sp, p.xy);
        return h.x;
    }
    float th = s / 16.0;
    vec2 h = boxHit(ro, rd, vec3(s, s, th), nIn);
    if (h.y < h.x || h.y < 0.0) return -1.0;
    // through the pixels it crosses (a pixel grid on its face, sixteen across), to the first one there
    vec3 pe = ro + rd * h.x;
    vec2 g = (pe.xy / (2.0 * s) + 0.5) * 16.0;
    vec2 dir = rd.xy / (2.0 * s) * 16.0;
    vec2 cell = clamp(floor(g), 0.0, 15.0);
    vec2 stp = sign(dir);
    vec2 adir = max(abs(dir), vec2(1e-5));
    vec2 tDelta = 1.0 / adir;
    vec2 tMax = h.x + (stp.x >= 0.0 ? cell + 1.0 - g : g - cell) / adir;
    float tc = h.x;
    vec3 n = nIn;
    for (int i = 0; i < 20; i++) {
        vec4 tex = textureLod(Sampler0, mix(sp.xy, sp.zw, vec2((cell.x + 0.5) / 16.0, 1.0 - (cell.y + 0.5) / 16.0)), 0.0);
        if (tex.a > 0.5) {
            col = tex.rgb;
            nrm = n;
            return tc;
        }
        if (tMax.x < tMax.y) {
            tc = tMax.x;
            tMax.x += tDelta.x;
            cell.x += stp.x;
            n = vec3(-stp.x, 0.0, 0.0);
        } else {
            tc = tMax.y;
            tMax.y += tDelta.y;
            cell.y += stp.y;
            n = vec3(0.0, -stp.y, 0.0);
        }
        if (tc > h.y || cell.x < 0.0 || cell.y < 0.0 || cell.x > 15.0 || cell.y > 15.0) break;
    }
    return -1.0;
}

mat3 tumble(float a, float b) {
    float ca = cos(a), sa = sin(a), cb = cos(b), sb = sin(b);
    return mat3(ca, 0.0, -sa, 0.0, 1.0, 0.0, sa, 0.0, ca) * mat3(1.0, 0.0, 0.0, 0.0, cb, sb, 0.0, -sb, cb);
}

vec4 galaxy(vec3 ro, vec3 rd, float t) {
    // its disk: tipped a little, turning slowly - the items it holds strewn along its arms, each turning over and over
    // as it goes round, lit by the bright heart
    vec3 N = normalize(vec3(0.22, 1.0, 0.14));
    vec3 e1 = normalize(cross(N, vec3(0.0, 0.0, 1.0))), e2 = cross(N, e1);
    float arms = full < 0.5 ? 2.0 : 3.0;
    float spin = t * 0.08;
    int n = count();
    float bestT = 1e9;
    vec3 bestC = vec3(0.0);
    int items = V0.x < -0.5 ? 0 : 10 + int(full * 22.0); // (V0 -1: its items drawn as themselves, outside the shader)
    for (int k = 0; k < 32; k++) {
        if (k >= items) break;
        float fk = float(k);
        float h1 = hash1(fk * 1.7 + 0.3), h2 = hash1(fk * 3.1 + 1.1), h3 = hash1(fk * 5.7 + 2.3), h4 = hash1(fk * 7.9 + 4.1);
        float r = 0.2 + 0.68 * sqrt(h1);
        float ang = log(r + 0.06) * 2.6 + mod(fk, arms) * TAU / arms + (h2 - 0.5) * 0.5 + spin;
        vec3 pos = (e1 * cos(ang) + e2 * sin(ang)) * r + N * (h3 - 0.5) * 0.16 * (1.1 - r);
        float s = 0.07 + 0.035 * h4;
        // (cheaply first: does the ray come near it at all)
        vec3 oc = ro - pos;
        float bq = dot(oc, rd), cq = dot(oc, oc) - s * s * 2.2;
        if (bq * bq - cq < 0.0) continue;
        mat3 R = tumble(t * (0.5 + h1) + fk * 2.0, t * (0.35 + 0.6 * h2) + fk);
        vec3 col, nrm;
        int which = int(mod(fk + floor(h3 * 3.0), float(n)));
        float th = itemHit(oc * R, rd * R, which, s, col, nrm);
        if (th > 0.12 && th < bestT) {
            bestT = th;
            vec3 nw = R * nrm;
            vec3 Ld = normalize(-pos);
            float lit = 0.4 + 0.6 * max(dot(nw, Ld), 0.0) + 0.25 * max(dot(nw, normalize(vec3(0.3, 1.0, 0.2))), 0.0);
            float rim = pow(1.0 - abs(dot(nw, rd)), 3.0);
            bestC = col * lit * 1.1 + vec3(1.0, 0.9, 0.75) * rim * 0.25;
        }
    }
    // its dust: arms of faint glowing dust, sprinkled with tiny stars
    vec4 dust = vec4(0.0);
    float tp = 1e9;
    float dn = dot(rd, N);
    if (abs(dn) > 1e-4) {
        tp = -dot(ro, N) / dn;
        if (tp > 0.12) {
            vec3 p = ro + rd * tp;
            vec2 q = vec2(dot(p, e1), dot(p, e2));
            float r = length(q);
            if (r < 0.98) {
                float th = atan(q.y, q.x) - spin;
                float dens = 0.0;
                for (int k = 0; k < 3; k++) {
                    if (float(k) >= arms) break;
                    float ph = th - log(r + 0.06) * 2.6 - float(k) * TAU / arms;
                    float dd = mod(ph + 3.14159, TAU) - 3.14159;
                    dens = max(dens, exp(-dd * dd * 4.0));
                }
                dens *= smoothstep(0.98, 0.55, r) * smoothstep(0.0, 0.15, r);
                float mottle = 0.6 + 0.8 * noise(vec3(q * 9.0, 1.0));
                vec2 sq = vec2(cos(-spin) * q.x - sin(-spin) * q.y, sin(-spin) * q.x + cos(-spin) * q.y) * 34.0;
                vec2 sc = floor(sq);
                float st = step(0.93 - 0.25 * dens, hash2(sc)) * smoothstep(0.32, 0.0, length(fract(sq) - 0.5 - (vec2(hash2(sc + 1.7), hash2(sc + 3.1)) - 0.5) * 0.4));
                vec3 dc = mix(vec3(0.45, 0.35, 0.95), C0.rgb, 0.35);
                dust = vec4(dc * mottle + vec3(1.0) * st * 1.5, clamp(dens * 0.3 * mottle * (0.5 + 0.5 * full) + st * 0.9 * smoothstep(0.98, 0.7, r), 0.0, 1.0));
            } else {
                tp = 1e9;
            }
        }
    }
    // its heart: a bright bulge of light
    float bb = max(-dot(ro, rd), 0.0);
    float dcen = length(ro + rd * bb);
    vec4 heart = vec4(vec3(1.0, 0.92, 0.75), clamp(exp(-dcen * dcen * 90.0) * 1.2 + exp(-dcen * dcen * 14.0) * 0.4, 0.0, 1.0));
    // (nearest first: an item, the dust, the heart - by where each is along the ray)
    vec4 c = bestT < 1e8 ? vec4(bestC, 1.0) : vec4(0.0);
    vec4 A = tp < bestT ? dust : vec4(0.0), B = bb < bestT ? heart : vec4(0.0);
    return tp > bb ? over(B, over(A, c)) : over(A, over(B, c));
}

// ---- 3: water planet ----

int bandAt(float v) {
    float acc = 0.0;
    int n = count();
    for (int i = 0; i < 10; i++) {
        if (i >= n) break;
        acc += max(pal(i).a, 0.0);
        if (v < acc) return i;
    }
    return n - 1;
}

vec4 planet(vec3 ro, vec3 rd, float t) {
    float R = 0.3 + 0.42 * full;
    vec3 L = normalize(vec3(0.6, 0.7, 0.4));
    float bb = max(-dot(ro, rd), 0.0);
    float dc = length(ro + rd * bb);
    // the droplet moon
    float ma = t * 0.7;
    vec3 mc = vec3(cos(ma) * (R + 0.2), sin(ma) * 0.12, sin(ma) * (R + 0.2));
    vec2 m = ball(ro, rd, mc, 0.06);
    vec2 s = ball(ro, rd, vec3(0.0), R);
    vec4 c = vec4(0.0);
    if (s.y > s.x && s.x > 0.0 && (m.y <= m.x || s.x < m.x)) {
        vec3 n = normalize(ro + rd * s.x);
        vec3 nr = rotY(n, t * 0.15);
        float lon = atan(nr.z, nr.x);
        float v = 1.0 - (nr.y + 1.0) * 0.5;
        v += 0.03 * sin(lon * 3.0 + t * 1.4) + 0.015 * sin(lon * 7.0 - t * 2.1);
        int b = bandAt(clamp(v, 0.0, 0.999));
        vec3 base = pal(b).rgb;
        // (the waves: the surface's light and dark)
        float w = noise(nr * 9.0 + vec3(t * 0.4, 0.0, 0.0));
        vec3 np = normalize(n + (vec3(noise(nr * 7.0 + t * 0.3), noise(nr * 7.0 + 4.0), noise(nr * 7.0 - t * 0.3)) - 0.5) * 0.25);
        float dif = max(dot(np, L), 0.0), spec = pow(max(dot(reflect(rd, np), L), 0.0), 40.0);
        vec3 col = glows(b) ? base * (1.0 + 0.4 * w) : base * (0.25 + 0.75 * dif) * (0.9 + 0.2 * w) + vec3(spec * 0.6);
        float rim = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);
        col += pal(0).rgb * rim * 0.5;
        c = vec4(col, 1.0);
    } else if (m.y > m.x && m.x > 0.0) {
        vec3 n = normalize(ro + rd * m.x - mc);
        c = vec4(mix(pal(0).rgb, vec3(1.0), 0.35) * (0.4 + 0.6 * max(dot(n, L), 0.0)) + vec3(pow(max(dot(reflect(rd, n), L), 0.0), 30.0)), 1.0);
    } else if (dc > R && dc < R + 0.09) {
        c = vec4(mix(pal(0).rgb, vec3(0.8, 0.9, 1.0), 0.5), (1.0 - (dc - R) / 0.09) * 0.45);
    }
    return c;
}

// ---- 4: bubbles ----

vec4 bubbles(vec3 ro, vec3 rd, float t) {
    int n = count();
    // (a bubble of each fluid - and more of them as it fills: up to ten in all)
    int total = min(10, n * (1 + int(full * 1.99)));
    float best = 1e9;
    vec4 c = vec4(0.0);
    vec3 L = normalize(vec3(0.5, 0.8, 0.3));
    for (int k = 0; k < 10; k++) {
        if (k >= total) break;
        int i = k - (k / n) * n;
        float share = max(pal(i).a, 0.0) / float(1 + (total - 1 - i) / n);
        float r = (0.16 + 0.26 * sqrt(share)) * (0.55 + 0.45 * full) * (total > 4 ? 0.8 : 1.0);
        float fi = float(k);
        vec3 ctr = vec3((hash1(fi * 3.1 + 1.0) - 0.5) * 1.1, (hash1(fi * 5.7 + 2.0) - 0.5) * 0.9, (hash1(fi * 7.3 + 3.0) - 0.5) * 1.1);
        if (k == 0) ctr *= 0.25;
        ctr = clamp(ctr, vec3(-1.0 + r), vec3(1.0 - r));
        ctr.y += 0.06 * sin(t * 1.3 + fi * 1.7);
        ctr.x += 0.03 * sin(t * 0.7 + fi * 2.3);
        vec2 s = ball(ro, rd, ctr, r);
        if (s.y <= s.x || s.x <= 0.0 || s.x >= best) continue;
        best = s.x;
        vec3 p = ro + rd * s.x, nrm = normalize(p - ctr);
        float level = ctr.y + r * 0.15 + 0.03 * r * sin(t * 2.0 + p.x * 12.0);
        vec3 fc = pal(i).rgb;
        bool glow = glows(i);
        vec4 inside;
        if (p.y < level) {
            inside = vec4(glow ? fc * 1.2 : fc * (0.45 + 0.55 * max(dot(nrm, L), 0.0)), 0.92);
        } else {
            float tp = rd.y < 0.0 ? (level - ro.y) / rd.y : -1.0;
            if (tp > s.x && tp < s.y) {
                inside = vec4(glow ? fc * 1.35 : mix(fc, vec3(1.0), 0.18), 0.88);
            } else if ((ro + rd * s.y).y < level) {
                inside = vec4(fc * (glow ? 1.1 : 0.8), 0.8);
            } else {
                inside = vec4(fc, 0.1);
            }
        }
        float rim = pow(1.0 - abs(dot(nrm, rd)), 3.0);
        float spec = pow(max(dot(reflect(rd, nrm), L), 0.0), 60.0);
        c = over(vec4(vec3(1.0), clamp(rim * 0.55 + spec, 0.0, 1.0)), inside);
    }
    return c;
}

// ---- 5: fluid helix ----

// the helix's layout: how many groups (four strands to a group), where a strand's group stands, how wide it turns
void helixLayout(int i, int n, out vec2 gc, out float rr, out float tr, out int j, out int inG, out int g) {
    int groups = (n + 3) / 4;
    g = i / 4;
    j = i - g * 4;
    inG = min(4, n - g * 4);
    float ga = float(g) * 6.2832 / float(groups) + 0.4;
    gc = groups == 1 ? vec2(0.0) : vec2(cos(ga), sin(ga)) * (groups == 2 ? 0.46 : 0.52);
    rr = groups == 1 ? 0.3 : 0.2;
    tr = groups == 1 ? 0.082 : 0.058;
}

vec2 strandAt(int i, int n, float y, float t) {
    vec2 gc;
    float rr, tr;
    int j, inG, g;
    helixLayout(i, n, gc, rr, tr, j, inG, g);
    float a = y * 6.5 + t * 1.6 + float(j) * 6.2832 / float(inG) + float(g) * 1.3;
    return gc + vec2(sin(a), cos(a)) * rr;
}

float segDist(vec3 p, vec3 a, vec3 b) {
    vec3 ab = b - a;
    float h = clamp(dot(p - a, ab) / dot(ab, ab), 0.0, 1.0);
    return length(p - a - ab * h);
}

// how wide its base and top rings are: round all its strands
float ringR(int n) {
    int groups = (n + 3) / 4;
    return groups == 1 ? 0.47 : (groups == 2 ? 0.46 : 0.52) + 0.31;
}

// how far from the nearest strand, rung, ring or bubble (x), and whose (y: the strand; 10 + a rung between strand y-10
// and the next; 30 a ring; 40 a bubble)
vec2 helixAt(vec3 p, float t, float bottom, float top) {
    int n = count();
    vec2 best = vec2(1e9, 0.0);
    float cap = max(bottom - p.y, p.y - top);
    for (int i = 0; i < 10; i++) {
        if (i >= n) break;
        vec2 gc;
        float rr, tr;
        int j, inG, g;
        helixLayout(i, n, gc, rr, tr, j, inG, g);
        vec2 cc = strandAt(i, n, p.y, t);
        // (a strand: thicker and thinner as the fluid pulses up it)
        float pulse = 1.0 + 0.12 * sin(p.y * 14.0 - t * 3.0 + float(i) * 2.0);
        float d = (length(p.xz - cc) - tr * pulse) * 0.6;
        d = max(d, cap);
        if (d < best.x) best = vec2(d, float(i));
        // (the rungs: thin bridges of fluid to the next strand round, every so often up the helix)
        if (inG > 1 && (inG > 2 || j == 0)) {
            int nx = g * 4 + (j + 1) % inG;
            float step_ = 0.16;
            float yk = (floor(p.y / step_) + 0.5) * step_;
            if (yk > bottom + 0.06 && yk < top - 0.06) {
                vec2 ca = strandAt(i, n, yk, t), cb = strandAt(nx, n, yk, t);
                float dr = segDist(p, vec3(ca.x, yk, ca.y), vec3(cb.x, yk, cb.y)) - tr * 0.32;
                if (dr < best.x) best = vec2(dr, 10.0 + float(i));
            }
        }
        // (bubbles rising up the middle of each helix)
        if (j == 0) {
            for (int k = 0; k < 3; k++) {
                float ph = fract(t * 0.12 + float(k) / 3.0 + float(g) * 0.37);
                float by = mix(bottom + 0.05, top - 0.05, ph);
                vec3 bc = vec3(gc.x + 0.03 * sin(t * 2.0 + float(k) * 2.0), by, gc.y + 0.03 * cos(t * 1.7 + float(k)));
                float rb = (0.022 + 0.012 * float(k)) * smoothstep(0.0, 0.12, ph) * smoothstep(1.0, 0.85, ph) + 0.001;
                float db = length(p - bc) - rb;
                if (db < best.x) best = vec2(db, 40.0);
            }
        }
    }
    // (its base and the ring round its top: rings of metal)
    float R = ringR(n);
    float dRing = min(length(vec2(length(p.xz) - R, p.y - bottom)) - 0.04, length(vec2(length(p.xz) - R, p.y - top)) - 0.028);
    if (dRing < best.x) best = vec2(dRing, 30.0);
    return best;
}

// a fluid's own picture flowing over it: how much lighter or darker than the picture is on the whole, at uv
float fluidDetail(int i, vec2 uv) {
    vec4 sp = spriteOf(i);
    if (sp.z <= sp.x) return 1.0;
    vec3 k = vec3(0.3, 0.59, 0.11);
    float here = dot(textureLod(Sampler0, mix(sp.xy, sp.zw, fract(uv)), 0.0).rgb, k);
    float all_ = 0.0;
    for (int a = 0; a < 4; a++) {
        vec2 f = vec2(float(a & 1), float(a >> 1)) * 0.5 + 0.25;
        all_ += dot(textureLod(Sampler0, mix(sp.xy, sp.zw, f), 0.0).rgb, k);
    }
    return clamp(here / max(all_ * 0.25, 0.04), 0.55, 1.6);
}

vec4 helix(vec3 ro, vec3 rd, float t0, float t1, float t) {
    int n = count();
    float bottom = -0.86, top = -0.86 + 1.72 * max(full, 0.1);
    float R = ringR(n);
    float tt = t0;
    vec3 L = normalize(vec3(0.5, 0.8, 0.4));
    // (all its fluids together: their colour, for the lights on its rings)
    vec3 mixC = vec3(0.0);
    for (int i = 0; i < 10; i++) {
        if (i >= n) break;
        mixC += pal(i).rgb;
    }
    mixC /= float(n);
    // its base: a pool of its fluids at its foot, held in the base ring, each strand rising from it, rippling round it
    float tPool = 1e9;
    vec4 pool = vec4(0.0);
    if (rd.y < 0.0) {
        float tp = (bottom + 0.005 - ro.y) / rd.y;
        vec3 p = ro + rd * tp;
        float r = length(p.xz);
        if (tp > t0 && tp < t1 && r < R) {
            tPool = tp;
            vec3 pc = vec3(0.0);
            float wsum = 0.0, rip = 0.0;
            for (int i = 0; i < 10; i++) {
                if (i >= n) break;
                vec2 foot = strandAt(i, n, bottom, t);
                float d = length(p.xz - foot);
                float w = 1.0 / (d * d + 0.012);
                pc += pal(i).rgb * w * fluidDetail(i, p.xz * 1.5 + vec2(t * 0.05, 0.0));
                wsum += w;
                rip += sin(d * 75.0 - t * 7.0) * exp(-d * 9.0);
            }
            pc /= wsum;
            vec3 nrm = normalize(vec3(rip * 0.08 * p.x / max(r, 0.01), 1.0, rip * 0.08 * p.z / max(r, 0.01)));
            float spec = pow(max(dot(reflect(rd, nrm), L), 0.0), 40.0);
            float fres = pow(1.0 - max(dot(nrm, -rd), 0.0), 4.0);
            pool = vec4(pc * (0.6 + 0.3 * max(dot(nrm, L), 0.0)) + vec3(spec * 0.7) + vec3(0.6, 0.7, 0.9) * fres * 0.3 + pc * 0.12 * rip, 1.0);
        }
    }
    for (int s = 0; s < 120; s++) {
        vec3 p = ro + rd * tt;
        if (tt > tPool) break;
        vec2 h = helixAt(p, t, bottom, top);
        if (h.x < 0.0015) {
            float e = 0.003;
            vec3 nrm = normalize(vec3(helixAt(p + vec3(e, 0, 0), t, bottom, top).x - helixAt(p - vec3(e, 0, 0), t, bottom, top).x,
                                      helixAt(p + vec3(0, e, 0), t, bottom, top).x - helixAt(p - vec3(0, e, 0), t, bottom, top).x,
                                      helixAt(p + vec3(0, 0, e), t, bottom, top).x - helixAt(p - vec3(0, 0, e), t, bottom, top).x));
            float dif = max(dot(nrm, L), 0.0);
            float spec = pow(max(dot(reflect(rd, nrm), L), 0.0), 48.0);
            float fres = pow(1.0 - max(dot(nrm, -rd), 0.0), 3.0);
            if (h.y > 39.5) {
                // a bubble: clear, its edge and a shine
                return vec4(mixC * 0.4 + vec3(0.9) * fres + vec3(spec), clamp(0.25 + fres + spec, 0.0, 1.0));
            }
            if (h.y > 29.5) {
                // a ring: dark polished metal, a band of little lights round its top glowing with its fluids' colours
                float ang = atan(p.z, p.x);
                float stud = smoothstep(0.2, 0.08, abs(fract(ang / TAU * 16.0) - 0.5)) * smoothstep(0.3, 0.75, nrm.y);
                float env = 0.5 + 0.5 * nrm.y;
                vec3 c = vec3(0.13, 0.14, 0.17) * (0.5 + 0.9 * dif) + vec3(0.55, 0.6, 0.7) * env * 0.18 + mixC * fres * 0.35 + vec3(spec * 0.9);
                c = mix(c, mixC * 1.3 + 0.15, stud * (0.75 + 0.25 * sin(t * 3.0 + ang * 4.0)));
                return vec4(c, 1.0);
            }
            bool rung = h.y >= 9.5;
            int i = int((rung ? h.y - 10.0 : h.y) + 0.5);
            vec3 fc = pal(i).rgb;
            float around = atan(nrm.z, nrm.x) / TAU;
            float det = fluidDetail(i, vec2(around * 2.0 + p.y * 0.8, p.y * 2.2 - t * 0.5));
            if (rung) {
                int g = i / 4, j = i - g * 4, inG = min(4, n - g * 4);
                fc = mix(fc, pal(g * 4 + (j + 1) % inG).rgb, 0.5);
            }
            fc *= det;
            // (the fluid flowing up: bubbles rising inside it)
            float bub = smoothstep(0.78, 0.86, noise(vec3(p.y * 30.0 - t * 4.0, float(i) * 7.0 + p.x * 9.0, p.z * 9.0)));
            vec3 col;
            if (glows(i)) {
                col = fc * 1.15 + vec3(1.0, 0.85, 0.5) * bub * 0.5 + fc * fres * 0.4;
            } else {
                col = fc * (0.25 + 0.75 * dif);
                col += mix(fc, vec3(1.0), 0.6) * fres * 0.5 + vec3(spec * 0.8) + vec3(bub * 0.3);
            }
            return vec4(col, 1.0);
        }
        tt += max(h.x, 0.003);
        if (tt > t1) break;
    }
    return pool;
}

// ---- 6: layers (a still tank: its fluids settled, the heaviest at the bottom, gases hanging from the top) ----

bool isGas(int i) {
    return mod(floor(Info.z / pow(2.0, float(i))), 2.0) > 0.5;
}

// the line between two layers, moving gently (a little more on the liquids' top)
float layerWave(vec2 q, float t, float i, float k) {
    return k * (sin(q.x * 3.7 + t * 1.2 + i * 1.7) * cos(q.y * 3.1 - t * 0.9 + i) * 0.7 + 0.3 * sin((q.x - q.y) * 7.0 - t * 2.1 + i * 3.0));
}

// which layer a point is in (-1: none), how far to the nearest line between two layers, and the index of the liquids'
// top layer (-1: no liquids)
int layerAt(vec3 p, float t, out float edge, out int topL) {
    int n = count();
    edge = 1e9;
    topL = -1;
    int found = -1;
    float base = -1.0, below = -1.0;
    int lastLiquid = -1;
    for (int i = 0; i < 10; i++) {
        if (i >= n) break;
        if (!isGas(i)) lastLiquid = i;
    }
    topL = lastLiquid;
    for (int i = 0; i < 10; i++) {
        if (i >= n) break;
        if (isGas(i)) continue;
        float h = 2.0 * full * pal(i).a;
        base += h;
        bool last = i == lastLiquid;
        float top = base + layerWave(p.xz, t, float(i), last ? 0.03 : 0.018 * min(1.0, h * 8.0));
        if (found < 0 && p.y >= below && p.y < top) found = i;
        if (!last) edge = min(edge, abs(p.y - top));
        below = top;
    }
    float ceil_ = 1.0;
    for (int i = 0; i < 10; i++) {
        if (i >= n) break;
        if (!isGas(i)) continue;
        float h = 2.0 * full * pal(i).a;
        float bot = ceil_ - h + layerWave(p.xz, t * 0.6, float(i) + 5.0, 0.025);
        if (found < 0 && p.y <= ceil_ && p.y > bot) found = i;
        ceil_ = bot;
    }
    return found;
}

vec4 layers(vec3 ro, vec3 rd, float t0, float t1, float t) {
    const int STEPS = 56;
    float dt = (t1 - t0) / float(STEPS);
    float jitter = hash3(vec3(gl_FragCoord.xy, 3.0));
    vec3 col = vec3(0.0);
    float a = 0.0;
    vec3 L = normalize(vec3(0.4, 0.8, 0.3));
    // where the ray comes in, on the glass: the lines between the layers
    float e0;
    int topL;
    int l0 = layerAt(ro + rd * (t0 + 0.002), t, e0, topL);
    int prev = l0;
    int detL = -1;
    float det = 1.0;
    for (int i = 0; i < STEPS; i++) {
        float tt = t0 + (float(i) + jitter) * dt;
        vec3 p = ro + rd * tt;
        float e;
        int tl;
        int Ly = layerAt(p, t, e, tl);
        if (Ly < 0) {
            prev = -1;
            continue;
        }
        bool gas = isGas(Ly), gl = glows(Ly);
        // its own picture, about once a block, drifting slowly - taken where the ray comes into this layer (on the
        // glass, its top, or the line from the layer before), not all through it: no streaks
        if (Ly != detL) {
            vec3 q = p * half_;
            vec2 uv = abs(rd.y) > 0.7 && prev == -1 ? q.xz : vec2(q.x + q.z, q.y);
            det = fluidDetail(Ly, uv + vec2(t * 0.01, -t * 0.015));
            detL = Ly;
        }
        vec3 c = pal(Ly).rgb * det;
        // fizz rising through it (slowly in gases), glowing ones flickering
        float fizz = smoothstep(0.86, 0.97, noise(p * vec3(16.0, 8.0, 16.0) * max(half_, 0.5) + vec3(0.0, -t * (gas ? 0.5 : 1.6), 0.0)));
        if (gl) {
            c *= 1.2 + 0.25 * noise(p * 5.0 + vec3(0.0, t * 0.4, 0.0));
        } else {
            c *= 0.75 + 0.25 * clamp(p.y * 0.5 + 0.5, 0.0, 1.0); // (darker deeper down)
        }
        c += vec3(0.9) * fizz * (gl ? 0.5 : 0.4);
        // coming down onto the liquids' top from above: its shine
        if (prev == -1 && !gas && Ly == topL && rd.y < 0.0) {
            float ex = 0.01;
            float hx = layerWave(p.xz + vec2(ex, 0.0), t, float(Ly), 0.03) - layerWave(p.xz - vec2(ex, 0.0), t, float(Ly), 0.03);
            float hz = layerWave(p.xz + vec2(0.0, ex), t, float(Ly), 0.03) - layerWave(p.xz - vec2(0.0, ex), t, float(Ly), 0.03);
            vec3 nrm = normalize(vec3(-hx / (2.0 * ex), 1.0, -hz / (2.0 * ex)));
            float spec = pow(max(dot(reflect(rd, nrm), L), 0.0), 60.0);
            float fres = pow(1.0 - max(dot(nrm, -rd), 0.0), 4.0);
            float sheen = clamp(spec * 0.9 + fres * 0.35, 0.0, 1.0);
            col += (1.0 - a) * sheen * mix(vec3(1.0), c, 0.3);
            a += (1.0 - a) * sheen * 0.6;
        }
        float dens = gas ? 0.9 : (gl ? 7.0 : 2.8);
        dens += fizz * 5.0;
        float da = 1.0 - exp(-dens * dt);
        col += (1.0 - a) * da * c;
        a += (1.0 - a) * da;
        prev = Ly;
        if (a > 0.985) break;
    }
    // (the lines between the layers on the glass: a bright thin edge)
    if (l0 >= 0) {
        float line = smoothstep(0.022, 0.0, e0);
        col = mix(col, vec3(1.0) * max(a, 0.5), line * 0.45);
        a = max(a, line * 0.7);
    }
    return vec4(col / max(a, 1e-4), a);
}

void main() {
    float t = GameTime * 1200.0;
    vec3 ro = eye / half_;
    vec3 rd = normalize(lp - eye);
    vec3 inv = 1.0 / (sign(rd) * max(abs(rd), vec3(1e-6)));
    vec3 ta = (vec3(-1.0) - ro) * inv, tb = (vec3(1.0) - ro) * inv;
    float t0 = max(max(max(min(ta.x, tb.x), min(ta.y, tb.y)), min(ta.z, tb.z)), 0.0);
    float t1 = min(min(max(ta.x, tb.x), max(ta.y, tb.y)), max(ta.z, tb.z));
    if (t1 <= t0) {
        discard;
    }
    // (in a chamber or tank - which you stand inside to see - the singularity's skins keep to its middle, away from
    // its walls and you)
    float zoom = big > 0.5 && skin <= 2 ? 1.45 : 1.0;
    ro *= zoom;
    t0 *= zoom;
    t1 *= zoom;
    vec4 c;
    if (skin == 1) {
        c = tesseract(ro, rd, t0, t1, t);
    } else if (skin == 2) {
        c = galaxy(ro, rd, t);
    } else if (skin == 3) {
        c = planet(ro, rd, t);
    } else if (skin == 4) {
        c = bubbles(ro, rd, t);
    } else if (skin == 5) {
        c = helix(ro, rd, t0, t1, t);
    } else if (skin == 6) {
        c = layers(ro, rd, t0, t1, t);
    } else {
        c = dyson(ro, rd, t);
    }
    c.a *= strength;
    if (c.a < 0.004) {
        discard;
    }
    fragColor = vec4(c.rgb, c.a);
}
