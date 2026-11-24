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
                // (an edge right by the eye - standing in a chamber - fades, not a streak across the view)
                glow += exp(-h.x * h.x / (w * w)) * (d == 3 ? 0.7 : 1.0) * smoothstep(0.2, 0.9, h.y);
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
        if (tp > 0.15) {
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
                // the stars: the items held, their own pictures (or, for want of one, a square of its colour)
                float cells = (7.0 + 4.0 * full) * (big > 0.5 ? 0.62 : 1.0);
                vec2 rq = vec2(cos(-t * 0.08) * q.x - sin(-t * 0.08) * q.y, sin(-t * 0.08) * q.x + cos(-t * 0.08) * q.y) * cells;
                vec2 cell = floor(rq), f = fract(rq) - 0.5;
                float h = hash2(cell);
                if (h < dens * (0.6 + 0.4 * full) + 0.06 * full) {
                    vec2 off = (vec2(hash2(cell + 1.3), hash2(cell + 2.9)) - 0.5) * 0.25;
                    float size = 0.26 + 0.12 * hash2(cell + 5.1);
                    vec2 a = (f - off) / size;
                    if (max(abs(a.x), abs(a.y)) < 1.0) {
                        int which = int(hash2(cell + 7.7) * float(count()));
                        vec4 sp = spriteOf(which);
                        if (sp.z > sp.x) {
                            vec2 uv = mix(sp.xy, sp.zw, vec2(a.x * 0.5 + 0.5, 0.5 - a.y * 0.5));
                            vec4 tex = textureLod(Sampler0, uv, 0.0);
                            if (tex.a > 0.3) {
                                c = vec4(tex.rgb * 1.15, 1.0);
                            }
                        } else if (max(abs(a.x), abs(a.y)) < 0.6) {
                            c = vec4(pal(which).rgb * 1.15, 1.0);
                        }
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

// how far from the nearest strand or rung (x), whose (y: the strand, or 10 + a rung between strand y-10 and the next)
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
            if (yk > bottom + 0.04 && yk < top - 0.04) {
                vec2 ca = strandAt(i, n, yk, t), cb = strandAt(nx, n, yk, t);
                float dr = segDist(p, vec3(ca.x, yk, ca.y), vec3(cb.x, yk, cb.y)) - tr * 0.32;
                if (dr < best.x) best = vec2(dr, 10.0 + float(i));
            }
        }
    }
    return best;
}

vec4 helix(vec3 ro, vec3 rd, float t0, float t1, float t) {
    int n = count();
    float bottom = -0.9, top = -0.9 + 1.8 * max(full, 0.08);
    float tt = t0;
    vec3 L = normalize(vec3(0.5, 0.8, 0.4));
    vec4 c = vec4(0.0);
    for (int s = 0; s < 110; s++) {
        vec3 p = ro + rd * tt;
        vec2 h = helixAt(p, t, bottom, top);
        if (h.x < 0.0015) {
            bool rung = h.y >= 9.5;
            int i = int((rung ? h.y - 10.0 : h.y) + 0.5);
            float e = 0.003;
            vec3 nrm = normalize(vec3(helixAt(p + vec3(e, 0, 0), t, bottom, top).x - helixAt(p - vec3(e, 0, 0), t, bottom, top).x,
                                      helixAt(p + vec3(0, e, 0), t, bottom, top).x - helixAt(p - vec3(0, e, 0), t, bottom, top).x,
                                      helixAt(p + vec3(0, 0, e), t, bottom, top).x - helixAt(p - vec3(0, 0, e), t, bottom, top).x));
            vec3 fc = pal(i).rgb;
            if (rung) {
                int g = i / 4, j = i - g * 4, inG = min(4, n - g * 4);
                fc = mix(fc, pal(g * 4 + (j + 1) % inG).rgb, 0.5);
            }
            float dif = max(dot(nrm, L), 0.0);
            float spec = pow(max(dot(reflect(rd, nrm), L), 0.0), 48.0);
            float fres = pow(1.0 - max(dot(nrm, -rd), 0.0), 3.0);
            // (the fluid flowing up: streaks, and bubbles rising inside it)
            float flow = noise(vec3(p.y * 16.0 - t * 2.4, float(i) * 3.0, atan(nrm.z, nrm.x) * 2.0));
            float bub = smoothstep(0.78, 0.86, noise(vec3(p.y * 30.0 - t * 4.0, float(i) * 7.0 + p.x * 9.0, p.z * 9.0)));
            vec3 col;
            if (glows(i)) {
                col = fc * (1.05 + 0.45 * flow) + vec3(1.0, 0.85, 0.5) * bub * 0.6;
            } else {
                col = fc * (0.22 + 0.78 * dif) * (0.85 + 0.3 * flow);
                col += mix(fc, vec3(1.0), 0.6) * fres * 0.55 + vec3(spec * 0.8) + vec3(bub * 0.35);
            }
            return over(c, vec4(col, 1.0));
        }
        tt += max(h.x, 0.003);
        if (tt > t1) break;
    }
    // (the pool at its foot: the fluids swirling round, glowing faintly)
    if (rd.y < 0.0) {
        float tp = (bottom - 0.02 - ro.y) / rd.y;
        if (tp > t0 && tp < t1) {
            vec3 p = ro + rd * tp;
            float r = length(p.xz);
            if (r < 0.78) {
                float sw = atan(p.z, p.x) + r * 6.0 - t * 0.9;
                int i = int(mod(floor((sw / 6.2832 + 1.0) * float(n) * 2.0), float(n)));
                vec3 pc = pal(i).rgb * (0.55 + 0.3 * noise(vec3(p.xz * 9.0, t * 0.6)));
                float edge = smoothstep(0.78, 0.6, r);
                return vec4(pc + vec3(0.2) * pow(max(dot(reflect(rd, vec3(0.0, 1.0, 0.0)), L), 0.0), 30.0), 0.85 * edge);
            }
        }
    }
    return c;
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
    } else {
        c = dyson(ro, rd, t);
    }
    c.a *= strength;
    if (c.a < 0.004) {
        discard;
    }
    fragColor = vec4(c.rgb, c.a);
}
