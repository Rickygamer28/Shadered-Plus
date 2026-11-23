#version 150

// Shadered+'s storage skins (client/StorageSkins), traced through their box in 3D (in units of the box's half-size: the
// box is -1 .. 1), each showing how full it is:
//  0 Dyson sphere: a small star in a turning shell of panels - the fuller, the more panels close, till it's sealed,
//                  glowing at the seams;
//  1 tesseract:    a 4D cube turning itself inside out round a bright core, another smaller one folded inside for
//                  each third;
//  2 item galaxy:  spiral arms of little squares in the colours of the items held (C0 ..), a glowing core - more
//                  stars, and a third arm, as it fills;
//  3 water planet: a ball of the fluids held, in bands by how much of each, waves rolling across, a droplet moon -
//                  swelling as it fills;
//  4 bubbles:      bubbles of each fluid, sized by how much, more as it fills, the fluid sloshing inside, bobbing;
//  5 fluid helix:  a strand for each fluid twisting round the others, four to a helix (more helices for more
//                  fluids), climbing as it fills.
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

// ---- 0: Dyson sphere ----

// a point of the shell (its direction from the middle): x panel there, y its frame
vec2 shell(vec3 n, float t) {
    n = rotY(n, t * 0.12);
    float lat = asin(clamp(n.y, -1.0, 1.0)), lon = atan(n.z, n.x);
    float bl = 3.14159 / 8.0;
    vec2 g = vec2((lat + 1.5708) / bl, (lon + 3.14159) / bl);
    vec2 cell = floor(g), f = fract(g);
    float edge = min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y) * cos(lat));
    float panel = hash2(cell + 3.7) < full * 1.04 ? 1.0 : 0.0;
    return vec2(panel, smoothstep(0.07, 0.03, edge));
}

vec4 dyson(vec3 ro, vec3 rd, float t) {
    vec3 starCol = mix(vec3(1.0, 0.82, 0.5), C0.rgb, 0.35);
    float R = 0.8, rs = 0.26 + 0.06 * full;
    vec2 s = ball(ro, rd, vec3(0.0), R), st = ball(ro, rd, vec3(0.0), rs);
    vec4 c = vec4(0.0);
    // (the star's glow, all along the ray)
    float b = max(-dot(ro, rd), 0.0);
    float d = length(ro + rd * b);
    vec4 glow = vec4(starCol, clamp(exp(-d * d * 9.0) * 0.9, 0.0, 1.0));
    bool sealed = full > 0.97;
    if (s.y > s.x) {
        // the shell's near side
        if (s.x > 0.0) {
            vec3 n = normalize(ro + rd * s.x);
            vec2 p = shell(n, t);
            float seam = sealed ? (0.5 + 0.5 * sin(t * 2.0)) * p.y : 0.0;
            if (p.x > 0.5) {
                vec3 metal = mix(vec3(0.24, 0.25, 0.3), vec3(0.62, 0.64, 0.72), 0.5 + 0.5 * dot(n, normalize(vec3(0.4, 0.8, 0.3))));
                metal = mix(metal, vec3(0.12), p.y * 0.6) + starCol * seam * 1.2;
                return vec4(metal, 1.0);
            }
            if (p.y > 0.5) {
                c = over(c, vec4(vec3(0.35, 0.36, 0.42), 0.95));
            }
        }
        if (st.y > st.x && st.x > 0.0) {
            vec3 n = normalize(ro + rd * st.x);
            float boil = fbm(n * 5.0 + vec3(0.0, t * 0.3, 0.0));
            vec3 sc = mix(starCol, vec3(1.0, 0.98, 0.9), boil * 0.8);
            return over(c, vec4(sc * (1.1 + 0.3 * boil), 1.0));
        }
        c = over(c, glow);
        // the far side, from inside: lit by the star
        vec3 n = normalize(ro + rd * s.y);
        vec2 p = shell(n, t);
        if (p.x > 0.5) {
            c = over(c, vec4(mix(vec3(0.2, 0.2, 0.24), starCol, 0.45) * (1.0 - 0.5 * p.y), 1.0));
        } else if (p.y > 0.5) {
            c = over(c, vec4(vec3(0.3), 0.9));
        }
        return c;
    }
    return glow * vec4(1.0, 1.0, 1.0, 0.6);
}

// ---- 1: tesseract ----

vec3 tess(int i, float a, float b, float sc) {
    vec4 v = vec4((i & 1) != 0 ? 1.0 : -1.0, (i & 2) != 0 ? 1.0 : -1.0, (i & 4) != 0 ? 1.0 : -1.0, (i & 8) != 0 ? 1.0 : -1.0);
    float x = v.x * cos(a) - v.w * sin(a), w = v.x * sin(a) + v.w * cos(a);
    float y = v.y * cos(b) - v.z * sin(b), z = v.y * sin(b) + v.z * cos(b);
    float k = 1.0 / (2.6 - w);
    return vec3(x, y, z) * k * sc;
}

vec4 tesseract(vec3 ro, vec3 rd, float t0, float t1, float t) {
    vec3 col = mix(vec3(0.62, 0.55, 1.0), C0.rgb, 0.4);
    float glow = 0.0, core = 0.0;
    int layers = 1 + int(full * 2.99);
    for (int L = 0; L < 3; L++) {
        if (L >= layers) break;
        float sc = 1.05 * pow(0.55, float(L)), a = t * (0.5 + 0.25 * float(L)), b = t * 0.33 + float(L);
        vec3 P[16];
        for (int i = 0; i < 16; i++) P[i] = tess(i, a, b, sc);
        for (int i = 0; i < 16; i++) {
            for (int d = 0; d < 4; d++) {
                int j = i ^ (1 << d);
                if (j < i) continue;
                vec2 h = raySeg(ro, rd, t0, t1, P[i], P[j]);
                float w = (L == 0 ? 0.022 : 0.016);
                glow += exp(-h.x * h.x / (w * w)) * (d == 3 ? 0.7 : 1.0);
            }
        }
    }
    float bb = max(-dot(ro, rd), 0.0);
    float dc = length(ro + rd * bb);
    core = exp(-dc * dc * (60.0 - 30.0 * full)) * (0.7 + 0.3 * sin(t * 3.0));
    vec3 c = col * glow + vec3(1.0, 0.97, 1.0) * core + col * exp(-dc * dc * 8.0) * 0.25;
    return vec4(c, clamp(glow * 0.9 + core + exp(-dc * dc * 8.0) * 0.2, 0.0, 1.0));
}

// ---- 2: item galaxy ----

vec4 galaxy(vec3 ro, vec3 rd, float t) {
    // (its disk tipped towards the eye, wherever you stand - seen nearly face on, never edge on)
    vec3 toEye = normalize(ro);
    vec3 N = normalize(toEye + vec3(0.0, 0.9, 0.0) + 0.15 * vec3(1.0, 0.0, 0.6));
    vec3 e1 = normalize(cross(N, vec3(0.0, 0.0, 1.0))), e2 = cross(N, e1);
    float bb = max(-dot(ro, rd), 0.0);
    float dc = length(ro + rd * bb);
    vec4 c = vec4(0.0);
    float dn = dot(rd, N);
    if (abs(dn) > 1e-4) {
        float tp = -dot(ro, N) / dn;
        if (tp > 0.0) {
            vec3 p = ro + rd * tp;
            vec2 q = vec2(dot(p, e1), dot(p, e2));
            float r = length(q);
            if (r < 0.96) {
                float th = atan(q.y, q.x) - t * 0.08;
                float arms = full < 0.5 ? 2.0 : 3.0, dens = 0.0;
                for (int k = 0; k < 3; k++) {
                    if (float(k) >= arms) break;
                    float ph = th - log(r + 0.06) * 2.6 - float(k) * 6.2832 / arms;
                    float dd = mod(ph + 3.14159, 6.2832) - 3.14159;
                    dens = max(dens, exp(-dd * dd * 5.0));
                }
                dens *= smoothstep(0.96, 0.6, r) * smoothstep(0.02, 0.12, r);
                // (dust in the arms)
                c = vec4(mix(vec3(0.35, 0.3, 0.7), C0.rgb, 0.4), dens * 0.25 * (0.4 + 0.6 * full));
                // the stars: little squares, each the colour of something held
                float cells = 11.0 + 6.0 * full;
                vec2 rq = vec2(cos(-t * 0.08) * q.x - sin(-t * 0.08) * q.y, sin(-t * 0.08) * q.x + cos(-t * 0.08) * q.y) * cells;
                vec2 cell = floor(rq), f = fract(rq) - 0.5;
                float h = hash2(cell);
                if (h < dens * (0.55 + 0.45 * full) + 0.05 * full) {
                    vec2 off = (vec2(hash2(cell + 1.3), hash2(cell + 2.9)) - 0.5) * 0.4;
                    float size = 0.2 + 0.12 * hash2(cell + 5.1);
                    vec2 a = abs(f - off);
                    if (max(a.x, a.y) < size) {
                        int which = int(hash2(cell + 7.7) * float(count()));
                        vec3 sc = pal(which).rgb;
                        float edge = max(a.x, a.y) > size * 0.7 ? 0.65 : 1.0;
                        c = vec4(sc * edge * 1.15, 1.0);
                    }
                }
            }
        }
    }
    vec4 core = vec4(vec3(1.0, 0.92, 0.75), clamp(exp(-dc * dc * 70.0) + exp(-dc * dc * 12.0) * 0.35, 0.0, 1.0));
    return over(core, c);
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

// how far from the nearest strand (x), and which (y)
vec2 helixAt(vec3 p, float t, float bottom, float top) {
    int n = count();
    int groups = (n + 3) / 4;
    float rr = groups == 1 ? 0.3 : 0.19, tr = groups == 1 ? 0.085 : 0.062;
    vec2 best = vec2(1e9, 0.0);
    for (int i = 0; i < 10; i++) {
        if (i >= n) break;
        int g = i / 4, j = i - g * 4;
        int inG = min(4, n - g * 4);
        float ga = float(g) * 6.2832 / float(groups);
        vec2 gc = groups == 1 ? vec2(0.0) : vec2(cos(ga), sin(ga)) * (groups == 2 ? 0.45 : 0.5);
        float a = p.y * 6.5 + t * 1.6 + float(j) * 6.2832 / float(inG) + float(g) * 1.3;
        vec2 cc = gc + vec2(sin(a), cos(a)) * rr;
        float d = (length(p.xz - cc) - tr) * 0.6;
        d = max(d, max(bottom - p.y, p.y - top));
        if (d < best.x) best = vec2(d, float(i));
    }
    return best;
}

vec4 helix(vec3 ro, vec3 rd, float t0, float t1, float t) {
    float bottom = -0.92, top = -0.92 + 1.84 * max(full, 0.08);
    float tt = t0;
    vec3 L = normalize(vec3(0.5, 0.8, 0.4));
    for (int s = 0; s < 90; s++) {
        vec3 p = ro + rd * tt;
        vec2 h = helixAt(p, t, bottom, top);
        if (h.x < 0.002) {
            int i = int(h.y + 0.5);
            float e = 0.004;
            vec3 n = normalize(vec3(helixAt(p + vec3(e, 0, 0), t, bottom, top).x - helixAt(p - vec3(e, 0, 0), t, bottom, top).x,
                                    helixAt(p + vec3(0, e, 0), t, bottom, top).x - helixAt(p - vec3(0, e, 0), t, bottom, top).x,
                                    helixAt(p + vec3(0, 0, e), t, bottom, top).x - helixAt(p - vec3(0, 0, e), t, bottom, top).x));
            vec3 fc = pal(i).rgb;
            float dif = max(dot(n, L), 0.0), spec = pow(max(dot(reflect(rd, n), L), 0.0), 30.0);
            float flow = noise(vec3(p.y * 14.0 - t * 2.0, float(i), 0.0));
            vec3 col = glows(i) ? fc * (1.0 + 0.35 * flow) : fc * (0.3 + 0.7 * dif) * (0.9 + 0.2 * flow) + vec3(spec * 0.5);
            return vec4(col, 1.0);
        }
        tt += max(h.x, 0.004);
        if (tt > t1) break;
    }
    return vec4(0.0);
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
    } else {
        c = dyson(ro, rd, t);
    }
    c.a *= strength;
    if (c.a < 0.004) {
        discard;
    }
    fragColor = vec4(c.rgb, c.a);
}
