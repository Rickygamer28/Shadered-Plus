#version 150

// The power core's skins, in 3D (traced through its box, in units of the box's half-size: the box is -1 .. 1), each
// showing how full it is:
//  0 star:   a ball of fire - an ember when empty, swelling orange, yellow, then white-hot, boiling, turning slowly,
//            with a corona and flares round it;
//  1 storm:  a thundercloud, lumpy and churning, darker as it fills, lightning bolts forking down out of it (in 3D),
//            more and more often, sheets of rain falling under it;
//  2 plasma: a glass ball, tendrils of light (3D curves) reaching from its middle to the glass - more, brighter and
//            bluer as it fills;
//  3 atom:   a nucleus of little balls, with electrons racing round it on tilted rings - a new ring for each quarter.

uniform float GameTime;

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

// how close the ray (from t0 to t1) comes to a point, and where along it
vec2 rayPoint(vec3 ro, vec3 rd, float t0, float t1, vec3 p) {
    float t = clamp(dot(p - ro, rd), t0, t1);
    return vec2(length(ro + rd * t - p), t);
}

// ---- 0: the pocket star ----
vec3 hash33(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)), dot(p, vec3(113.5, 271.9, 124.6)));
    return fract(sin(p) * 43758.5453);
}

// cells (granules): the distances to the nearest two cell middles
vec2 cells(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    float d1 = 8.0, d2 = 8.0;
    for (int z = -1; z <= 1; z++) for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        vec3 o = vec3(x, y, z);
        vec3 r = o + hash33(i + o) - f;
        float d = dot(r, r);
        if (d < d1) { d2 = d1; d1 = d; } else if (d < d2) { d2 = d; }
    }
    return sqrt(vec2(d1, d2));
}

// its colour by how hot (0 dull red .. 1 white): ember, orange, yellow, white
vec3 fire(float k) {
    k = clamp(k, 0.0, 1.2);
    vec3 c = mix(vec3(0.45, 0.04, 0.01), vec3(1.0, 0.32, 0.05), smoothstep(0.0, 0.35, k));
    c = mix(c, vec3(1.0, 0.72, 0.25), smoothstep(0.3, 0.65, k));
    c = mix(c, vec3(1.0, 0.97, 0.9), smoothstep(0.6, 1.0, k));
    return c * (0.6 + 0.6 * k);
}

// a loop of glowing gas rising off the star and back down (a prominence): how close the ray comes, and where along it
vec2 loopDist(vec3 ro, vec3 rd, float t0, float t1, float fi, float R, float t) {
    vec3 b = normalize(hash33(vec3(fi * 7.3, 1.0, 3.0)) - 0.5);
    b = rotY(b, t * 0.05);
    vec3 side = normalize(cross(b, vec3(0.2, 1.0, 0.1)));
    float w = 0.28 + 0.12 * hash1(fi * 3.0);
    float life = fract(t * 0.06 + hash1(fi * 11.0));
    float H = (0.12 + 0.35 * full) * sin(3.14159 * life) * (0.7 + 0.6 * hash1(fi));
    vec3 A = normalize(b - side * w), B = normalize(b + side * w);
    vec2 best = vec2(1e9, 0.0);
    vec3 prev = A * R;
    for (int k = 1; k <= 7; k++) {
        float s = float(k) / 7.0;
        float wob = 0.03 * sin(s * 9.0 + t * 1.3 + fi);
        vec3 next = normalize(mix(A, B, s)) * R * (1.0 + H * sin(3.14159 * s) + wob * H);
        vec2 d = raySeg(ro, rd, t0, t1, prev, next);
        if (d.x < best.x) best = d;
        prev = next;
    }
    return best;
}

vec4 star(vec3 ro, vec3 rd, float t0, float t1, float t) {
    float R = 0.3 + 0.34 * sqrt(full);
    float heatAll = 0.35 + 0.65 * full; // (an ember empty, white-hot full)
    vec2 hit = ball(ro, rd, vec3(0.0), R);
    bool onStar = !(hit.y < hit.x || hit.x > t1 || hit.y < t0);
    float front = onStar ? max(hit.x, t0) : 1e9;

    // the corona round it: streamers reaching out, by the way the ray passes it
    vec2 near = rayPoint(ro, rd, t0, min(t1, front), vec3(0.0));
    vec3 dirN = normalize(ro + rd * near.y + vec3(1e-4));
    float out_ = max(near.x - R, 0.0) / R;
    vec3 qd = rotY(dirN, t * 0.03);
    float streak = fbm(qd * 3.0 + vec3(0.0, t * 0.05, 0.0) - out_ * 0.6);
    float streamers = pow(smoothstep(0.35, 0.8, streak), 1.5);
    float glow = exp(-out_ * 3.2) * (0.35 + 0.35 * full) + streamers * exp(-out_ * 1.6) * (0.35 + 0.5 * full);
    float chromo = onStar ? 0.0 : exp(-pow((near.x - R) / (0.018 + 0.01 * full), 2.0));
    glow *= smoothstep(0.98, 0.72, near.x); // (none at the box's edge)
    vec3 col = fire(heatAll * 0.75) * glow + vec3(1.0, 0.45, 0.35) * chromo * (0.7 + 0.5 * full);
    float a = clamp(glow * 0.9 + chromo, 0.0, 1.0);

    // the prominences: loops of fire standing off its edge (in front of its face, dark threads against it)
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        if (fi >= 2.0 + full * 2.0) break;
        vec2 d = loopDist(ro, rd, t0, t1, fi, R, t);
        float thick = 0.008 + 0.008 * full;
        float g = exp(-pow(d.x / thick, 2.0)) * 0.8 + exp(-d.x / (thick * 4.0)) * 0.3;
        if (onStar && d.y > front) continue; // (behind it)
        vec3 pc = mix(vec3(1.0, 0.22, 0.12), vec3(1.0, 0.55, 0.4), full);
        col += pc * g * 1.1;
        a = max(a, clamp(g, 0.0, 1.0));
    }
    if (!onStar) {
        return vec4(min(col, vec3(1.2)), a);
    }

    // its face: boiling granules (bright cells with dark lanes between), big slow swirls, dark spots with paler
    // edges, bright patches by its edge, and darker, redder towards its edge
    vec3 p = ro + rd * front;
    vec3 n = normalize(p);
    vec3 q = rotY(n, t * 0.05);
    vec2 v = cells(q * (16.0 + 4.0 * full) + vec3(0.0, t * 0.08, t * 0.05));
    float gran = smoothstep(0.0, 0.25, v.y - v.x) * (0.6 + 0.4 * noise(q * 30.0));
    float swirl = fbm(q * 2.5 + vec3(t * 0.04, 0.0, -t * 0.03));
    float sp = noise(q * 3.2 + vec3(5.0, t * 0.015, 1.0));
    float pen = smoothstep(0.68, 0.76, sp) * (1.0 - full * 0.6);
    float umbra = smoothstep(0.78, 0.84, sp) * (1.0 - full * 0.6);
    float mu = max(dot(n, -rd), 0.0);
    float fac = smoothstep(0.55, 0.75, fbm(q * 7.0 + 11.0)) * pow(1.0 - mu, 1.5);
    float k = heatAll * (0.78 + 0.18 * gran + 0.3 * (swirl - 0.5)) - 0.25 * pen - 0.4 * umbra + 0.25 * fac;
    k *= 0.55 + 0.45 * pow(mu, 0.4);
    vec3 face = fire(k * 0.92 + 0.04);
    face += fire(heatAll) * pow(1.0 - mu, 4.0) * 0.35; // (the glow of its edge)
    // a prominence in front of its face: a darker, redder thread
    return vec4(min(face + col * 0.25, vec3(1.2)), 1.0);
}

// ---- 1: the storm ----
float fbm5(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.07 + vec3(3.1, 1.7, 5.3);
        a *= 0.5;
    }
    return v;
}

// the cloud's shape: billowing lobes heaped over a flat base (0 outside them)
float cloudShape(vec3 p) {
    float s = 0.0;
    s = max(s, 1.0 - length((p - vec3(0.0, 0.40, 0.0)) / vec3(0.66, 0.30, 0.62)));
    s = max(s, 1.0 - length(p - vec3(0.40, 0.42, 0.16)) / 0.34);
    s = max(s, 1.0 - length(p - vec3(-0.38, 0.44, -0.20)) / 0.36);
    s = max(s, 1.0 - length(p - vec3(0.10, 0.64, -0.26)) / 0.32);
    s = max(s, 1.0 - length(p - vec3(-0.20, 0.66, 0.28)) / 0.30);
    s = max(s, 1.0 - length(p - vec3(0.36, 0.56, -0.38)) / 0.26);
    s = max(s, 1.0 - length(p - vec3(-0.42, 0.52, 0.38)) / 0.26);
    s = max(s, 1.0 - length(p - vec3(0.05, 0.78, 0.05)) / 0.22);
    return s * smoothstep(0.1, 0.22, p.y);
}

// the thundercloud: its shape, its edges eaten into curls by churning noise (none outside it: no haze in the box)
float cloudDensity(vec3 p, float t) {
    float s = cloudShape(p);
    if (s <= 0.0) return 0.0;
    vec3 q = rotY(p, t * 0.04) * 3.6 + vec3(0.0, -t * 0.06, t * 0.04);
    float churn = fbm5(q);
    float billow = 1.0 - abs(noise(q * 2.6 + 7.0) * 2.0 - 1.0); // (puffs, cauliflower-like, on its edges)
    return clamp(s * 2.4 - (1.0 - churn) * 0.95 - (1.0 - billow) * 0.45, 0.0, 1.0);
}

// a bolt's bend points: wandering down from the cloud's base to the floor
vec3 boltPoint(float seed, int k) {
    vec3 at = vec3((hash1(seed) - 0.5) * 0.8, 0.2, (hash1(seed + 5.0) - 0.5) * 0.8);
    for (int j = 1; j <= 8; j++) {
        if (j > k) break;
        float fj = float(j);
        at += vec3((hash1(seed + fj * 3.3) - 0.5) * 0.2, -0.15, (hash1(seed + fj * 7.7) - 0.5) * 0.2);
    }
    return at;
}

vec4 storm(vec3 ro, vec3 rd, float t0, float t1, float t) {
    // lightning: bolts forking down out of the cloud, each flashing (flickering) for a moment - more of them, more often,
    // the fuller it is - and flickers inside the cloud between them
    float light = 0.0, halo = 0.0;
    float boltT = 1e9;
    vec3 flashAt[5];
    float flashOf[5];
    float flashAll = 0.0;
    int bolts = 1 + int(full * 4.0 + 0.5);
    float every = mix(3.2, 0.55, full);
    for (int i = 0; i < 5; i++) {
        flashOf[i] = 0.0;
        flashAt[i] = vec3(0.0, 0.45, 0.0);
        if (i >= bolts) continue;
        float fi = float(i);
        float phase = t / every + hash1(fi * 7.1);
        float strike = floor(phase);
        float k = fract(phase);
        float seed = strike * 13.7 + fi * 3.1;
        float flash = k < 0.14 ? (1.0 - k / 0.14) * (0.55 + 0.45 * step(0.45, fract(k * 37.0))) : 0.0;
        flashAt[i] = boltPoint(seed, 0) + vec3(0.0, 0.2, 0.0);
        if (hash1(seed + 4.0) < 0.35) {
            flashOf[i] = flash * 1.3; // (this one stays in the cloud: a flicker lighting it from inside)
            flashAll += flash * 0.5;
            continue;
        }
        flashOf[i] = flash;
        flashAll += flash;
        if (flash <= 0.0) continue;
        for (int sgi = 0; sgi < 8; sgi++) {
            vec2 d = raySeg(ro, rd, t0, t1, boltPoint(seed, sgi), boltPoint(seed, sgi + 1));
            light += exp(-d.x * 140.0) * 1.6 * flash;
            halo += exp(-d.x * 12.0) * 0.12 * flash;
            if (d.x < 0.012) boltT = min(boltT, d.y);
        }
        // two branches off it, thinner
        for (int b = 0; b < 2; b++) {
            float fb = float(b);
            vec3 f0 = boltPoint(seed, 2 + b * 2);
            vec3 f1 = f0 + vec3((hash1(seed + 8.0 + fb) - 0.5) * 0.4, -0.2, (hash1(seed + 9.0 + fb) - 0.5) * 0.4);
            vec3 f2 = f1 + vec3((hash1(seed + 12.0 + fb) - 0.5) * 0.25, -0.16, (hash1(seed + 13.0 + fb) - 0.5) * 0.25);
            vec2 d1 = raySeg(ro, rd, t0, t1, f0, f1), d2 = raySeg(ro, rd, t0, t1, f1, f2);
            light += (exp(-d1.x * 200.0) + exp(-d2.x * 240.0) * 0.7) * flash * 0.9;
        }
    }
    // the cloud, marched front to back, lit from above (a few steps towards the light: darker where more cloud lies
    // over it, its edges catching the light), its base dark and heavy; the lightning lights it from inside; under it,
    // rain falling, lit by the flashes
    vec3 L = normalize(vec3(0.35, 1.0, 0.2));
    vec3 col = vec3(0.0);
    float a = 0.0;
    const int STEPS = 48;
    float dt = (t1 - t0) / float(STEPS);
    float jitter = hash3(vec3(gl_FragCoord.xy, 0.0));
    float gloom = 1.0 - 0.55 * full;
    for (int i = 0; i < STEPS; i++) {
        float tt = t0 + (float(i) + jitter) * dt;
        if (tt > boltT) break;
        vec3 p = ro + rd * tt;
        vec3 c = vec3(0.0);
        float dens = 0.0;
        if (p.y > 0.08) {
            dens = cloudDensity(p, t);
            if (dens > 0.002) {
                float over = cloudDensity(p + L * 0.06, t) + cloudDensity(p + L * 0.16, t) * 0.8;
                float sun = exp(-over * 0.8);
                float powder = 0.55 + 0.45 * (1.0 - exp(-dens * 5.0));
                vec3 base = vec3(0.13, 0.14, 0.2), top = vec3(1.0, 1.0, 1.04);
                float h = smoothstep(0.15, 0.8, p.y);
                c = mix(base, top, sun * powder) * gloom * (0.8 + 0.2 * sun) * (0.55 + 0.45 * h) + vec3(0.06, 0.07, 0.1) * (1.0 - h);
                for (int k = 0; k < 5; k++) {
                    float f = flashOf[k];
                    if (f > 0.0) {
                        c += vec3(0.7, 0.72, 1.0) * f * exp(-length(p - flashAt[k]) * 3.5) * 1.8;
                    }
                }
            }
        } else if (p.y > -1.0) {
            // the rain: streaks falling (thin across, long down), under the cloud, slanting a little, fading near the floor
            float under = smoothstep(0.8, 0.3, length(p.xz + vec2(0.0, 0.0))) * smoothstep(-1.0, -0.6, p.y);
            if (under <= 0.0) continue; // (none falls outside its footprint)
            vec3 rp = p + vec3(0.06 * p.y, 0.0, 0.0);
            float n = noise(vec3(rp.x * 40.0, rp.y * 2.0 + t * 8.0, rp.z * 40.0));
            float sheet = 0.6 + 0.6 * noise(vec3(p.x * 3.0 - t * 0.3, p.y, p.z * 3.0));
            dens = smoothstep(0.76, 0.92, n) * under * sheet * (0.35 + 0.5 * full) * 0.9;
            c = vec3(0.55, 0.62, 0.78) * (0.6 + 0.4 * gloom) + vec3(0.6, 0.6, 0.9) * min(flashAll, 1.0);
        }
        if (dens <= 0.002) continue;
        float da = 1.0 - exp(-dens * dt * 12.0);
        col += (1.0 - a) * da * c;
        a += (1.0 - a) * da;
        if (a > 0.985) break;
    }
    vec3 bolt = vec3(0.92, 0.9, 1.0) * light + vec3(0.55, 0.55, 1.0) * halo;
    return vec4(col + bolt * (1.0 - a * 0.5), clamp(max(a, min(light + halo, 1.0)), 0.0, 1.0));
}

// ---- 2: the plasma globe ----
// a tendril's bend points: from the little glass ball in the middle out to the big one, wandering, crackling
vec3 tendrilPoint(float fi, float s, float t, float R) {
    vec3 base = normalize(vec3(hash1(fi * 3.7) - 0.5, hash1(fi * 5.3) - 0.5, hash1(fi * 9.1) - 0.5) + vec3(0.0, 0.001, 0.0));
    base = normalize(base + 0.55 * vec3(sin(t * 0.5 + fi), sin(t * 0.43 + fi * 2.0), cos(t * 0.47 + fi * 3.0)));
    vec3 side = normalize(cross(base, vec3(0.3, 1.0, 0.2)));
    vec3 up = cross(base, side);
    float bend = sin(s * 5.0 + t * 1.7 + fi * 2.3) * 0.22 * s;
    float bend2 = cos(s * 4.0 + t * 1.3 + fi) * 0.16 * s;
    float tick = floor(t * 14.0);
    float crackle = s * (1.0 - s) * 0.16;
    vec3 jit = (vec3(hash1(fi + s * 17.0 + tick), hash1(fi * 2.0 + s * 23.0 + tick), hash1(fi * 3.0 + s * 29.0 + tick)) - 0.5) * crackle;
    return normalize(base + side * bend + up * bend2 + jit) * mix(0.13, R * 0.985, s);
}

vec4 plasma(vec3 ro, vec3 rd, float t0, float t1, float t) {
    const float R = 0.88;
    vec2 hit = ball(ro, rd, vec3(0.0), R);
    if (hit.y < hit.x || hit.y < t0 || hit.x > t1) {
        return vec4(0.0);
    }
    float a0 = max(hit.x, t0), a1 = min(hit.y, t1);
    // the little ball in the middle (they reach out from it): hides what's behind it
    vec2 eh = ball(ro, rd, vec3(0.0), 0.13);
    bool onCore = eh.y >= eh.x && eh.x > a0;
    if (onCore) a1 = min(a1, eh.x);
    vec3 c = mix(vec3(0.85, 0.3, 1.0), vec3(0.3, 0.55, 1.0), full);
    vec3 tip = mix(vec3(1.0, 0.45, 0.85), vec3(0.55, 0.9, 1.0), full);
    vec3 col = vec3(0.0);
    float lit = 0.0;
    int count = 3 + int(full * 9.0 + 0.5);
    for (int i = 0; i < 12; i++) {
        if (i >= count) break;
        float fi = float(i);
        float flicker = 0.7 + 0.3 * sin(t * 13.0 + fi * 5.0);
        vec3 prev = tendrilPoint(fi, 0.0, t, R);
        for (int k = 1; k <= 7; k++) {
            float s = float(k) / 7.0;
            vec3 next = tendrilPoint(fi, s, t, R);
            vec2 d = raySeg(ro, rd, a0, a1, prev, next);
            float w = (0.006 + 0.004 * (1.0 - s));
            float core = exp(-pow(d.x / w, 2.0));
            float glow = exp(-d.x / (w * 6.0)) * 0.35 + exp(-d.x * 9.0) * 0.04;
            col += (vec3(1.0, 0.95, 1.0) * core + mix(c, tip, s) * glow) * flicker;
            lit += (core + glow) * flicker;
            prev = next;
        }
        // where it touches the glass: a bright spot spreading on it
        vec2 dt = rayPoint(ro, rd, t0, t1, prev);
        float spot = exp(-dt.x / 0.035) * 0.7 + exp(-pow(dt.x / 0.012, 2.0)) * 0.8;
        col += tip * spot * flicker;
        lit += spot * flicker;
    }
    // the glowing gas filling it, brighter towards the middle
    float mid = rayPoint(ro, rd, a0, a1, vec3(0.0)).x;
    float gas = (a1 - a0) * 0.06 + exp(-mid * 5.0) * 0.35;
    col += c * gas;
    // the little ball: lit from inside, a white heart
    if (onCore) {
        vec3 n = normalize(ro + rd * eh.x);
        float f = pow(1.0 - max(dot(n, -rd), 0.0), 2.0);
        col += mix(vec3(1.0, 0.95, 1.0), c, f) * (1.2 - f * 0.6);
        lit += 1.0;
    }
    // the glass: its edge, a window's shine on it, faintly tinted
    vec3 n = normalize(ro + rd * hit.x);
    float fres = pow(1.0 - abs(dot(n, rd)), 3.0);
    vec3 refl = reflect(rd, n);
    float shine = pow(max(dot(refl, normalize(vec3(-0.5, 0.75, 0.4))), 0.0), 90.0) * 1.2
                + pow(max(dot(refl, normalize(vec3(0.6, 0.35, -0.7))), 0.0), 30.0) * 0.25;
    col += vec3(0.75, 0.82, 1.0) * (fres * 0.7 + shine);
    float a = clamp(max(lit, fres * 0.9 + shine) + 0.12 + gas * 0.5, 0.0, 1.0);
    return vec4(col, a);
}

// ---- 3: the atom ----
vec4 atom(vec3 ro, vec3 rd, float t0, float t1, float t) {
    // the nucleus: red and blue balls packed together, shaded, shining, glowing round
    float nucT = 1e9;
    vec3 nucCol = vec3(0.0);
    int balls = 6 + int(full * 8.0);
    for (int i = 0; i < 14; i++) {
        if (i >= balls) break;
        float fi = float(i);
        vec3 dir = normalize(hash33(vec3(fi, 3.0, 7.0)) - 0.5);
        float rr = 0.055 + 0.04 * sqrt(fi / 14.0);
        vec3 c = dir * (i == 0 ? 0.0 : rr) + 0.006 * vec3(sin(t * 7.0 + fi), cos(t * 6.0 + fi * 2.0), sin(t * 5.0 + fi * 3.0));
        vec2 h = ball(ro, rd, c, 0.062);
        if (h.y >= h.x && h.x > t0 && h.x < nucT) {
            nucT = h.x;
            vec3 n = normalize(ro + rd * h.x - c);
            vec3 Ld = normalize(vec3(0.4, 0.8, 0.3));
            float dif = max(dot(n, Ld), 0.0);
            float spec = pow(max(dot(reflect(rd, n), Ld), 0.0), 24.0);
            float rim = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);
            vec3 base = mod(fi, 2.0) < 0.5 ? vec3(1.0, 0.3, 0.25) : vec3(0.35, 0.5, 1.0);
            nucCol = base * (0.25 + 0.8 * dif) + vec3(1.0) * spec * 0.7 + vec3(0.6, 0.85, 1.0) * rim * 0.5;
        }
    }
    float glow = 0.0, electrons = 0.0, haze = 0.0;
    float shells = 1.0 + floor(full * 3.999);
    for (int k = 0; k < 4; k++) {
        float fk = float(k);
        if (fk >= shells) break;
        // its ring: a circle, tilted its own way, the tilt turning slowly
        float a = fk * 1.9 + t * 0.15 * (mod(fk, 2.0) < 0.5 ? 1.0 : -1.0);
        float b = 0.6 + fk * 0.7;
        vec3 nrm = normalize(vec3(sin(b) * cos(a), cos(b), sin(b) * sin(a)));
        vec3 u = normalize(cross(nrm, vec3(0.0, 0.0, 1.0) + vec3(0.001)));
        vec3 v = cross(nrm, u);
        float r = 0.42 + 0.16 * fk;
        float speed = 1.6 + 0.5 * fk;
        // where the ray crosses its plane: how far from the ring - and how far behind an electron (its trail)
        float denom = dot(rd, nrm);
        if (abs(denom) > 1e-4) {
            float tp = -dot(ro, nrm) / denom;
            if (tp > t0 && tp < t1 && tp < nucT) {
                vec3 p = ro + rd * tp;
                float d = abs(length(p) - r);
                float ang = atan(dot(p, v), dot(p, u));
                float trail = 0.0;
                for (int j = 0; j < 2; j++) {
                    float ph = t * speed + float(j) * 3.14159 + fk;
                    float behind = mod(ph - ang, TAU);
                    trail += exp(-behind * 2.2);
                }
                float line = exp(-pow(d / 0.008, 2.0));
                glow += line * (0.35 + 1.4 * trail) + exp(-pow(d / 0.04, 2.0)) * (0.08 + 0.35 * trail);
                haze += exp(-pow(d / 0.14, 2.0)) * 0.05;
            }
        }
        for (int j = 0; j < 2; j++) {
            float ph = t * speed + float(j) * 3.14159 + fk;
            vec3 at = (u * cos(ph) + v * sin(ph)) * r;
            vec2 d = rayPoint(ro, rd, t0, min(t1, nucT), at);
            electrons += exp(-pow(d.x / 0.026, 2.0)) * 2.0 + exp(-d.x / 0.05) * 0.35;
        }
    }
    float pulse = 0.85 + 0.15 * sin(t * 3.0);
    float halo = exp(-rayPoint(ro, rd, t0, min(t1, nucT), vec3(0.0)).x / 0.17) * (0.3 + 0.45 * full) * pulse;
    vec3 col = vec3(0.4, 0.78, 1.0) * glow + vec3(0.92, 0.98, 1.0) * electrons + vec3(1.0, 0.75, 0.9) * halo + vec3(0.3, 0.6, 1.0) * haze;
    float alpha = clamp(max(max(glow, electrons), halo + haze), 0.0, 1.0);
    if (nucT < 1e8) {
        col = col * 0.6 + nucCol;
        alpha = 1.0;
    }
    return vec4(col, alpha);
}

// the star forge's star: the pocket star in another colour - 0 red, 1 yellow (as it is), 2 blue, 3 white - its
// brightness kept
vec3 starColour(vec3 c, int k) {
    float l = max(max(c.r, c.g), c.b);
    float m = clamp(l, 0.0, 1.0);
    if (k == 0) return mix(vec3(0.55, 0.05, 0.02), vec3(1.0, 0.42, 0.22), m) * l * 1.05;
    if (k == 2) return mix(vec3(0.08, 0.2, 0.85), vec3(0.78, 0.9, 1.0), m) * l * 1.1;
    if (k == 3) return mix(vec3(0.62, 0.62, 0.8), vec3(1.0, 1.0, 1.04), m) * l * 1.12;
    return c;
}

void main() {
    float t = GameTime * 1200.0;
    // the ray, in the box's own units: from the eye (or, outside it, from where it enters the box) to where it leaves
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
        c = storm(ro, rd, t0, t1, t);
    } else if (skin == 2) {
        c = plasma(ro, rd, t0, t1, t);
    } else if (skin == 3) {
        c = atom(ro, rd, t0, t1, t);
    } else {
        c = star(ro, rd, t0, t1, t);
        if (skin >= 4) {
            c.rgb = starColour(c.rgb, skin - 4);
        }
    }
    c.a *= strength;
    if (c.a < 0.004) {
        discard;
    }
    fragColor = vec4(c.rgb, c.a);
}
