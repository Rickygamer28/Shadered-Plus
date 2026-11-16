#version 150

// The power core's skins, in 3D (traced through its box, in units of the box's half-size: the box is -1 .. 1), each
// showing how full it is:
//  0 star:   a ball of fire - an ember when empty, swelling orange, yellow, then white-hot, boiling, turning slowly,
//            with a corona and flares round it;
//  1 storm:  a thundercloud, lumpy and churning, darker as it fills, lightning bolts forking down out of it (in 3D)
//            more and more often (the rain: client/PowerCoreRenderer's drops);
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
vec4 star(vec3 ro, vec3 rd, float t0, float t1, float t) {
    float R = 0.32 + 0.4 * sqrt(full);
    vec3 hot = mix(vec3(1.0, 0.42, 0.12), vec3(1.0, 0.97, 0.88), full);
    vec3 rim = mix(vec3(0.55, 0.05, 0.02), vec3(1.0, 0.38, 0.06), full);
    vec2 hit = ball(ro, rd, vec3(0.0), R);
    // the corona round it: how close the ray comes, the flares from where that is
    vec2 near = rayPoint(ro, rd, t0, t1, vec3(0.0));
    vec3 dirN = normalize(ro + rd * near.y + vec3(1e-4));
    float out_ = max(near.x - R, 0.0) / max(1.0 - R, 0.05);
    float tongues = fbm(rotY(dirN, t * 0.05) * 2.5 + vec3(0.0, t * 0.3, 0.0) - out_ * 1.5);
    float flare = smoothstep(0.45, 0.85, tongues) * exp(-out_ * (4.0 + 5.0 * (1.0 - tongues))) * (0.5 + full);
    float glow = exp(-out_ * (6.0 - 2.5 * full)) * (0.4 + 0.5 * full);
    vec4 corona = vec4(mix(rim, hot, flare * 0.5), clamp(glow + flare, 0.0, 1.0));
    if (hit.y < hit.x || hit.x > t1 || hit.y < t0) {
        return corona;
    }
    vec3 p = ro + rd * max(hit.x, t0);
    vec3 n = normalize(p);
    vec3 q = rotY(n, t * 0.05);
    float granules = fbm(q * 7.0 + vec3(0.0, t * 0.3, 0.0));
    float spots = smoothstep(0.8, 0.88, noise(q * 4.5 + vec3(t * 0.02))) * (1.0 - full * 0.7);
    float heat = 0.78 + 0.35 * granules - 0.3 * spots;
    float limb = pow(max(dot(n, -rd), 0.0), 0.45);
    vec3 col = mix(rim, hot, limb) * heat * (0.75 + 0.45 * limb);
    return vec4(min(col, vec3(1.0)), 1.0);
}

// ---- 1: the storm ----
float cloudDensity(vec3 p, float t) {
    vec3 c = p - vec3(0.0, 0.42, 0.0);
    float shape = 1.0 - length(c / vec3(0.88, 0.36, 0.88));
    float churn = fbm(rotY(p, t * 0.03) * 3.0 + vec3(0.0, -t * 0.08, t * 0.05));
    return clamp((shape + (churn - 0.5) * 0.9) * 2.4, 0.0, 1.0);
}

vec3 boltPoint(float seed, int k) {
    float y = 0.2 - float(k) * 0.2;
    vec3 start = vec3((hash1(seed) - 0.5) * 1.1, 0.2, (hash1(seed + 5.0) - 0.5) * 1.1);
    if (k == 0) return start;
    return vec3(start.x + (hash1(seed + float(k) * 3.3) - 0.5) * 0.35 * float(k) * 0.5, y,
                start.z + (hash1(seed + float(k) * 7.7) - 0.5) * 0.35 * float(k) * 0.5);
}

vec4 storm(vec3 ro, vec3 rd, float t0, float t1, float t) {
    // lightning first: bolts of a few segments down out of the cloud, each flashing for a moment
    float light = 0.0, flashAll = 0.0;
    int bolts = 1 + int(full * 4.0 + 0.5);
    float every = mix(3.0, 0.5, full);
    float boltT = 1e9;
    for (int i = 0; i < 5; i++) {
        if (i >= bolts) break;
        float fi = float(i);
        float phase = t / every + hash1(fi * 7.1);
        float strike = floor(phase);
        float k = fract(phase);
        float flash = k < 0.12 ? (1.0 - k / 0.12) * (0.6 + 0.4 * step(0.5, fract(k * 40.0))) : 0.0;
        if (flash <= 0.0) continue;
        flashAll = max(flashAll, flash);
        float seed = strike * 13.7 + fi * 3.1;
        int segs = 4 + int(hash1(seed + 2.0) * 2.0);
        for (int s = 0; s < 6; s++) {
            if (s >= segs) break;
            vec2 d = raySeg(ro, rd, t0, t1, boltPoint(seed, s), boltPoint(seed, s + 1));
            light += (exp(-d.x * 140.0) + exp(-d.x * 20.0) * 0.3) * flash;
            if (d.x < 0.02) boltT = min(boltT, d.y);
        }
    }
    // the cloud: marched through, front to back
    vec3 col = vec3(0.0);
    float a = 0.0;
    const int STEPS = 40;
    float dt = (t1 - t0) / float(STEPS);
    for (int i = 0; i < STEPS; i++) {
        float tt = t0 + (float(i) + hash3(vec3(gl_FragCoord.xy, float(i))) * 0.9) * dt;
        if (tt > boltT) break;
        vec3 p = ro + rd * tt;
        float dens = cloudDensity(p, t);
        if (dens <= 0.001) continue;
        float shade = 0.62 - 0.38 * full + 0.45 * clamp((p.y - 0.3) * 2.0, 0.0, 1.0);
        vec3 c = vec3(shade * 0.8, shade * 0.82, shade * 0.95) + vec3(0.55, 0.5, 0.9) * flashAll * 0.5;
        float da = 1.0 - exp(-dens * dt * 9.0);
        col += (1.0 - a) * da * c;
        a += (1.0 - a) * da;
        if (a > 0.98) break;
    }
    vec3 bolt = vec3(0.92, 0.9, 1.0) * light;
    return vec4(col + bolt * (1.0 - a * 0.7), clamp(max(a, light), 0.0, 1.0));
}

// ---- 2: the plasma globe ----
vec3 tendrilPoint(float fi, float s, float t) {
    vec3 base = normalize(vec3(hash1(fi * 3.7) - 0.5, hash1(fi * 5.3) - 0.5, hash1(fi * 9.1) - 0.5) + vec3(0.0, 0.001, 0.0));
    base = normalize(base + 0.45 * vec3(sin(t * 0.5 + fi), sin(t * 0.43 + fi * 2.0), cos(t * 0.47 + fi * 3.0)));
    vec3 side = normalize(cross(base, vec3(0.3, 1.0, 0.2)));
    vec3 up = cross(base, side);
    float bend = sin(s * 5.0 + t * 1.7 + fi * 2.3) * 0.25 * s;
    float bend2 = cos(s * 4.0 + t * 1.3 + fi) * 0.18 * s;
    return normalize(base + side * bend + up * bend2) * s * 0.86;
}

vec4 plasma(vec3 ro, vec3 rd, float t0, float t1, float t) {
    const float R = 0.88;
    vec2 hit = ball(ro, rd, vec3(0.0), R);
    if (hit.y < hit.x || hit.y < t0 || hit.x > t1) {
        return vec4(0.0);
    }
    float a0 = max(hit.x, t0), a1 = min(hit.y, t1);
    float tendrils = 0.0, tips = 0.0;
    int count = 3 + int(full * 9.0 + 0.5);
    for (int i = 0; i < 12; i++) {
        if (i >= count) break;
        float fi = float(i);
        float flicker = 0.75 + 0.25 * sin(t * 13.0 + fi * 5.0);
        vec3 prev = vec3(0.0);
        for (int k = 1; k <= 5; k++) {
            vec3 next = tendrilPoint(fi, float(k) / 5.0, t);
            vec2 d = raySeg(ro, rd, a0, a1, prev, next);
            tendrils += (exp(-d.x * 160.0) + exp(-d.x * 30.0) * 0.25) * flicker;
            prev = next;
        }
        tips += exp(-rayPoint(ro, rd, a0, a1, prev).x * 45.0) * flicker;
    }
    vec3 c = mix(vec3(0.85, 0.35, 1.0), vec3(0.35, 0.6, 1.0), full);
    float core = exp(-rayPoint(ro, rd, a0, a1, vec3(0.0)).x * 18.0) * 1.6;
    vec3 n = normalize(ro + rd * hit.x);
    float fres = pow(1.0 - abs(dot(n, rd)), 3.0);
    vec3 col = c * tendrils * 1.3 + vec3(1.0, 0.9, 1.0) * (core + tips * 0.9) + vec3(0.7, 0.8, 1.0) * fres * 0.8 + c * 0.05;
    float a = clamp(max(max(tendrils, core + tips), fres * 0.9) + 0.14, 0.0, 1.0);
    return vec4(col, a);
}

// ---- 3: the atom ----
vec4 atom(vec3 ro, vec3 rd, float t0, float t1, float t) {
    // the nucleus: little red and blue balls, the nearest one hit
    float nucT = 1e9;
    vec3 nucCol = vec3(0.0);
    for (int i = 0; i < 9; i++) {
        float fi = float(i);
        vec3 c = (vec3(hash1(fi), hash1(fi + 9.0), hash1(fi + 17.0)) - 0.5) * 0.2
               + 0.008 * vec3(sin(t * 7.0 + fi), cos(t * 6.0 + fi * 2.0), sin(t * 5.0 + fi * 3.0));
        vec2 h = ball(ro, rd, c, 0.075);
        if (h.y >= h.x && h.x > t0 && h.x < nucT) {
            nucT = h.x;
            vec3 n = normalize(ro + rd * h.x - c);
            float lit = 0.35 + 0.75 * max(dot(n, normalize(vec3(0.4, 0.8, 0.3))), 0.0);
            nucCol = (mod(fi, 2.0) < 0.5 ? vec3(1.0, 0.35, 0.3) : vec3(0.4, 0.55, 1.0)) * lit;
        }
    }
    float glow = 0.0, electrons = 0.0;
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
        float r = 0.45 + 0.15 * fk;
        // where the ray crosses its plane: how far from the ring
        float denom = dot(rd, nrm);
        if (abs(denom) > 1e-4) {
            float tp = -dot(ro, nrm) / denom;
            if (tp > t0 && tp < t1 && tp < nucT) {
                vec3 p = ro + rd * tp;
                float d = abs(length(p) - r);
                glow += exp(-pow(d / 0.012, 2.0)) * 0.6 + exp(-pow(d / 0.05, 2.0)) * 0.12;
            }
        }
        for (int j = 0; j < 2; j++) {
            float ph = t * (1.6 + 0.5 * fk) + float(j) * 3.14159 + fk;
            vec3 at = (u * cos(ph) + v * sin(ph)) * r;
            vec2 d = rayPoint(ro, rd, t0, min(t1, nucT), at);
            electrons += exp(-pow(d.x / 0.035, 2.0)) * 1.8 + exp(-d.x / 0.08) * 0.3;
        }
    }
    float halo = exp(-rayPoint(ro, rd, t0, min(t1, nucT), vec3(0.0)).x / 0.16) * (0.3 + 0.4 * full);
    vec3 col = vec3(0.45, 0.8, 1.0) * glow + vec3(0.9, 0.97, 1.0) * electrons + vec3(0.8, 0.85, 1.0) * halo;
    float alpha = clamp(max(max(glow, electrons), halo), 0.0, 1.0);
    if (nucT < 1e8) {
        col = col + nucCol * (1.0 - alpha * 0.5);
        alpha = 1.0;
    }
    return vec4(col, alpha);
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
    }
    c.a *= strength;
    if (c.a < 0.004) {
        discard;
    }
    fragColor = vec4(c.rgb, c.a);
}
