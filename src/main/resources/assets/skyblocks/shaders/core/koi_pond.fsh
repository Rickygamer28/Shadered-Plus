#version 150

// Shadered+'s koi pond (our own code). The view meets the rippling surface (lily pads float on it; the sky shines
// off it at low angles), bends into the water and goes down past koi swimming at three depths to the pebbles or the
// stone sides, light rippling on them, the fish and pads casting soft shadows. Each block has its own koi (maybe)
// and lily pads, swimming in loops in it; joined ponds are one water. x, z: world blocks; y: up in the block.

in vec3 wray;
in vec2 uv;
in vec4 vColor;

uniform float PondTime;
uniform float Daylight;

out vec4 fragColor;

const float SURF = 14.5 / 16.0;
const float FLOOR = 1.0 / 16.0;
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
    return SURF - (L == 0 ? 3.0 : L == 1 ? 5.5 : 8.0) / 16.0;
}

// a cell's koi: (is there one: 0/1, its depth layer, its kind 0..4, its size)
vec4 koiOf(vec2 cell) {
    vec2 h = hash22(cell + 3.7), k = hash22(cell * 1.7 + 11.1);
    return vec4(step(0.3, h.x), floor(h.y * 3.0), floor(k.x * 5.0), 0.8 + 0.45 * k.y);
}

// where a cell's koi is and which way it faces
void koiPath(vec2 cell, float t, out vec2 pos, out vec2 dir) {
    vec2 h = hash22(cell + 21.3);
    float sp = (0.22 + 0.18 * h.x) * (h.y < 0.5 ? -1.0 : 1.0);
    float th = t * sp + h.y * 31.0;
    vec2 c = cell + 0.5;
    pos = c + vec2(cos(th) * 0.2, sin(th) * 0.15 + 0.04 * sin(2.0 * th));
    vec2 v = vec2(-sin(th) * 0.2, cos(th) * 0.15 + 0.08 * cos(2.0 * th)) * sp;
    dir = normalize(v + vec2(1e-5));
}

// a koi at q (world x, z): (colour, how much it covers); body = only its body (for shadows)
vec4 koiAt(vec2 q, vec2 cell, float t, bool body) {
    vec4 info = koiOf(cell);
    float size = info.w;
    vec2 pos, dir;
    koiPath(cell, t, pos, dir);
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
    vec2 h = hash22(cell + 5.1);
    vec2 lp = vec2(s, side);
    if (abs(side) < bw && s > -0.46 && s < 0.56 && bw > 0.0) {
        if (body) return vec4(0.0, 0.0, 0.0, 1.0);
        float kind = info.z;
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

// the koi at q in a depth layer (from the nine cells round it)
vec4 koiLayer(vec2 q, int L, float t, bool body) {
    vec2 c0 = floor(q);
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 cell = c0 + vec2(i, j);
            vec4 info = koiOf(cell);
            if (info.x < 0.5 || int(info.y) != L) continue;
            vec4 k = koiAt(q, cell, t, body);
            if (k.a > 0.0) return k;
        }
    }
    return vec4(0.0);
}

// lily pad k of a cell: is there one, and where / how big / turned
bool padParams(vec2 cell, int k, float t, out vec2 c, out float r, out vec2 h, out vec2 h2) {
    h = hash22(cell * 2.3 + float(k) * 9.1 + 0.7);
    h2 = hash22(cell * 3.1 + float(k) * 4.7);
    c = cell + 0.15 + 0.7 * h2 + 0.02 * vec2(sin(t * 0.07 + h.y * 9.0), cos(t * 0.05 + h.x * 7.0));
    r = 0.08 + 0.07 * h.y;
    return h.x >= 0.45;
}

// is a frog sitting on this pad now (now and then one hops on for a while): its kind, size and which way it faces
bool frogOn(vec2 cell, int k, float t, out vec3 info) {
    vec2 hf = hash22(cell * 5.3 + float(k) * 2.9 + 1.3);
    float epoch = floor(t / 75.0 + hf.x * 13.0);
    vec2 he = hash22(vec2(epoch, hf.y * 97.0 + cell.x * 0.13 + cell.y * 0.71));
    info = vec3(floor(he.y * 3.0), 0.62 + 0.16 * hf.x, he.x * 6.2832 + 0.4 * sin(t * 0.3 + hf.y * 9.0));
    return hf.y < 0.16 && he.x < 0.5;
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
    float up = 0.19;
    vec3 pTop = p0 - rd * (up / -rd.y);
    vec2 c0 = floor(p0.xz);
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 cell = c0 + vec2(i, j);
            for (int k = 0; k < 2; k++) {
                vec2 c, h, h2;
                float r;
                vec3 info;
                if (!padParams(cell, k, t, c, r, h, h2) || r < 0.11 || !frogOn(cell, k, t, info)) continue;
                // close to the ray's way down?
                vec2 ab = p0.xz - pTop.xz;
                float u = clamp(dot(c - pTop.xz, ab) / max(dot(ab, ab), 1e-6), 0.0, 1.0);
                if (length(pTop.xz + ab * u - c) > 0.22) continue;
                float ca = cos(info.z), sa = sin(info.z);
                float sc = 16.0 / info.y;
                vec3 ro = vec3(pTop.x - c.x, pTop.y - SURF, pTop.z - c.y) * sc;
                vec3 rr = rd;
                ro.xz = mat2(ca, -sa, sa, ca) * ro.xz;
                rr.xz = mat2(ca, -sa, sa, ca) * rr.xz;
                float tm = 0.0, tEnd = length(p0 - pTop) * sc;
                for (int n = 0; n < 40; n++) {
                    vec3 q = ro + rr * tm;
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
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            vec2 cell = c0 + vec2(i, j);
            for (int k = 0; k < 2; k++) {
                vec2 c, h, h2;
                float r;
                if (!padParams(cell, k, t, c, r, h, h2)) continue;
                vec2 d = q - c;
                float dist = length(d);
                if (dist > r) continue;
                float a = atan(d.y, d.x) - h2.x * 6.28 - t * 0.02;
                float notch = abs(mod(a + 3.1416, 6.2832) - 3.1416);
                if (notch < 0.32 * (1.0 - 0.3 * dist / r) && dist > r * 0.08) continue;
                vec3 col = mix(vec3(0.14, 0.4, 0.12), vec3(0.3, 0.58, 0.2), noise2(d * 60.0 + h * 9.0) * 0.6 + 0.2);
                col *= 0.85 + 0.15 * sin(a * 12.0); // (veins)
                col *= 0.8 + 0.2 * smoothstep(r, r * 0.85, dist);
                vec3 info;
                if (r >= 0.11 && frogOn(cell, k, t, info)) {
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

float caustics(vec2 p, float t) {
    float a = sin(p.x * 31.0 + t * 0.9 + 2.0 * sin(p.y * 17.0 + t * 1.3));
    float b = sin(p.y * 29.0 - t * 0.8 + 2.0 * sin(p.x * 13.0 - t * 1.1));
    return pow(0.5 + 0.25 * (a + b), 4.0) * 1.6;
}

void main() {
    float t = PondTime;
    float day = clamp(Daylight, 0.0, 1.0);
    int ia = int(vColor.a * 255.0 + 0.5) - 16;
    bool openN = (ia & 1) != 0, openS = (ia & 2) != 0, openE = (ia & 4) != 0, openW = (ia & 8) != 0;
    vec3 rd = normalize(wray);
    vec3 p0 = vec3(uv.x, SURF, uv.y);
    vec2 cell0 = floor(uv);
    float light = 0.18 + 0.82 * day;

    // a frog sitting on a lily pad, in front of this point
    vec4 frog = frogHit(p0, rd, t, light);
    if (frog.a > 0.0) {
        fragColor = vec4(frog.rgb, 1.0);
        return;
    }
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
    vec3 n = normalize(vec3(-g.x * 23.0, 1.0, -g.y * 23.0));
    vec3 rr = refract(rd, n, 1.0 / 1.33);
    if (rr.y > -0.05) rr = normalize(vec3(rr.x, -0.05, rr.z));

    // what the bent ray meets: the stone sides (where no pond carries on), else the bottom
    float tHit = (FLOOR - SURF) / rr.y;
    int what = 0; // 0 bottom, 1 side
    float wx0 = cell0.x + 2.0 / 16.0, wx1 = cell0.x + 14.0 / 16.0, wz0 = cell0.y + 2.0 / 16.0, wz1 = cell0.y + 14.0 / 16.0;
    if (!openW && rr.x < 0.0) { float tw = (wx0 - p0.x) / rr.x; if (tw < tHit) { tHit = tw; what = 1; } }
    if (!openE && rr.x > 0.0) { float tw = (wx1 - p0.x) / rr.x; if (tw < tHit) { tHit = tw; what = 1; } }
    if (!openN && rr.z < 0.0) { float tw = (wz0 - p0.z) / rr.z; if (tw < tHit) { tHit = tw; what = 1; } }
    if (!openS && rr.z > 0.0) { float tw = (wz1 - p0.z) / rr.z; if (tw < tHit) { tHit = tw; what = 1; } }
    vec3 ph = p0 + rr * tHit;

    vec3 water = vec3(0.05, 0.17, 0.15) * light;
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
        vec4 k = koiLayer(q, L, t, false);
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
    col += vec3(1.0, 0.95, 0.85) * pow(max(dot(rf, normalize(SUN)), 0.0), 220.0) * day * 1.2;
    fragColor = vec4(col, 1.0);
}
