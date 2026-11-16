#version 150

// The power core's skins, each showing how full it is:
//  0 star:   an ember when empty, swelling orange, yellow, then white-hot, boiling, flares licking off it;
//  1 storm:  a thundercloud, darker as it fills, lightning crackling down out of it more and more often, rain;
//  2 plasma: a glass globe, tendrils of light reaching from its middle to the glass - more, brighter and bluer as it fills;
//  3 atom:   a nucleus with electrons circling on rings - a new ring of them for each quarter it fills.

uniform float GameTime;

in vec2 q;
flat in int skin;
flat in float full;
flat in float big;
flat in float strength;

out vec4 fragColor;

const float TAU = 6.2831853;

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float hash1(float n) {
    return fract(sin(n * 91.345) * 47453.5453);
}

float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
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

vec3 hue(float h) {
    return clamp(abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

// ---- 0: the pocket star ----
vec4 star(float t) {
    float r = length(q);
    float R = 0.16 + 0.32 * sqrt(full);
    vec3 hot = mix(vec3(1.0, 0.42, 0.12), vec3(1.0, 0.97, 0.88), full);
    vec3 rim = mix(vec3(0.55, 0.05, 0.02), vec3(1.0, 0.38, 0.06), full);
    if (r >= R) {
        float around = atan(q.y, q.x);
        float out_ = (r - R) / (1.0 - R);
        vec2 ring = vec2(cos(around), sin(around)) * 2.0;
        float tongues = fbm(vec3(ring * 1.5, t * 0.35 - out_ * 2.0));
        float flare = smoothstep(0.38, 0.85, tongues) * exp(-out_ * (3.5 + 5.0 * (1.0 - tongues))) * (0.5 + full);
        float glow = exp(-out_ * (6.0 - 2.5 * full)) * (0.35 + 0.45 * full);
        float a = clamp(glow + flare, 0.0, 1.0) * smoothstep(1.0, 0.85, r);
        return vec4(mix(rim, hot, flare * 0.6) , a);
    }
    vec3 n = vec3(q / R, sqrt(max(0.0, 1.0 - dot(q, q) / (R * R))));
    float s = t * 0.05;
    vec3 p = vec3(n.x * cos(s) + n.z * sin(s), n.y, -n.x * sin(s) + n.z * cos(s));
    float granules = fbm(p * 7.0 + vec3(0.0, t * 0.3, 0.0));
    float spots = smoothstep(0.72, 0.8, noise(p * 2.5 + vec3(t * 0.02))) * (1.0 - full * 0.6);
    float heat = 0.75 + 0.35 * granules - 0.5 * spots;
    float limb = pow(n.z, 0.45);
    vec3 col = mix(rim, hot, limb) * heat * (0.75 + 0.45 * limb);
    return vec4(min(col, vec3(1.0)), 1.0);
}

// ---- 1: the storm ----
float boltX(float y, float seed) {
    return (noise(vec3(y * 5.0, seed, 0.0)) - 0.5) * 0.28 + (noise(vec3(y * 19.0, seed, 3.0)) - 0.5) * 0.07;
}

vec4 storm(float t) {
    vec2 p = q;
    // the cloud: lumps of it across the top, darker the fuller it is (lit by its lightning)
    float shape = 0.0;
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        vec2 c = vec2(-0.6 + fi * 0.2, 0.28 + 0.08 * sin(fi * 2.3)) + vec2(0.02 * sin(t * 0.3 + fi), 0.0);
        float s = 0.2 + 0.08 * hash1(fi);
        shape = max(shape, exp(-dot((p - c) / vec2(s, s * 0.75), (p - c) / vec2(s, s * 0.75))));
    }
    float churn = fbm(vec3(p * 4.0, t * 0.15));
    float dens = smoothstep(0.35, 0.6, shape * (0.75 + 0.5 * churn));
    // lightning: bolts down out of it, more of them and more often the fuller it is
    float light = 0.0, flashAll = 0.0;
    int bolts = 1 + int(full * 4.0 + 0.5);
    float every = mix(3.0, 0.5, full);
    for (int i = 0; i < 5; i++) {
        if (i >= bolts) break;
        float fi = float(i);
        float phase = t / every + hash1(fi * 7.1);
        float strike = floor(phase);
        float k = fract(phase);
        float flash = k < 0.12 ? (1.0 - k / 0.12) * (0.6 + 0.4 * step(0.5, fract(k * 40.0))) : 0.0;
        if (flash <= 0.0) continue;
        float seed = strike * 13.7 + fi * 3.1;
        float x0 = (hash1(seed) - 0.5) * 1.0;
        float bottom = -0.95 + 0.25 * hash1(seed + 1.0);
        if (p.y < 0.2 && p.y > bottom) {
            float d = abs(p.x - x0 - boltX(p.y, seed));
            light += (exp(-d * 160.0) + exp(-d * 22.0) * 0.35) * flash;
            // a fork
            float fy = 0.05 - 0.3 * hash1(seed + 2.0);
            if (p.y < fy && p.y > fy - 0.35) {
                float fd = abs(p.x - x0 - boltX(fy, seed) - (fy - p.y) * (hash1(seed + 3.0) - 0.5) * 1.6 - boltX(p.y, seed + 9.0) * 0.5);
                light += (exp(-fd * 200.0) + exp(-fd * 30.0) * 0.25) * flash * 0.8;
            }
        }
        flashAll = max(flashAll, flash);
    }
    float shade = 0.62 - 0.38 * full + 0.22 * clamp((p.y - 0.15) * 3.0, 0.0, 1.0) + 0.1 * churn;
    vec3 cloud = vec3(shade * 0.78, shade * 0.8, shade * 0.95) + vec3(0.55, 0.5, 0.9) * flashAll * 0.45;
    // rain falling under it
    float rain = step(0.86, fract(p.x * 23.0 + floor(p.y * 3.0 - t * 4.0) * 0.37)) * step(0.5, fract(p.y * 6.0 - t * 5.0 + p.x * 9.0))
            * step(p.y, 0.15) * smoothstep(-1.0, -0.4, p.y) * smoothstep(0.75, 0.3, abs(p.x)) * (0.15 + 0.3 * full);
    vec3 boltCol = vec3(0.92, 0.9, 1.0);
    vec3 col = mix(vec3(0.6, 0.65, 0.8) * rain, cloud, dens) + boltCol * light;
    float a = clamp(max(max(dens * 0.96, light), rain), 0.0, 1.0);
    return vec4(col, a);
}

// ---- 2: the plasma globe ----
vec4 plasma(float t) {
    float r = length(q);
    const float R = 0.86;
    float glassRim = exp(-pow((r - R) / 0.025, 2.0)) * 0.55 + exp(-pow((r - R) / 0.09, 2.0)) * 0.12;
    float inside = step(r, R);
    float a0 = atan(q.y, q.x);
    float s = r / R;
    float tendrils = 0.0, tips = 0.0;
    int count = 3 + int(full * 9.0 + 0.5);
    for (int i = 0; i < 12; i++) {
        if (i >= count) break;
        float fi = float(i);
        float base = hash1(fi * 3.7) * TAU + 0.5 * sin(t * (0.4 + 0.3 * hash1(fi)) + fi);
        float bend = (0.6 + 0.4 * sin(t * 1.1 + fi * 1.7)) * sin(s * 5.0 + t * 1.6 + fi * 2.3);
        float ang = base + bend * s * 0.7;
        float d = abs(mod(a0 - ang + TAU * 0.5, TAU) - TAU * 0.5) * r;
        float flicker = 0.75 + 0.25 * sin(t * 13.0 + fi * 5.0);
        tendrils += (exp(-d * 140.0) + exp(-d * 28.0) * 0.3) * inside * flicker * smoothstep(0.02, 0.15, s);
        float endAng = base + (0.6 + 0.4 * sin(t * 1.1 + fi * 1.7)) * sin(5.0 + t * 1.6 + fi * 2.3) * 0.7;
        tips += exp(-length(q - R * 0.985 * vec2(cos(endAng), sin(endAng))) * 30.0) * flicker;
    }
    vec3 c = mix(vec3(0.85, 0.35, 1.0), vec3(0.35, 0.6, 1.0), full);
    float core = exp(-r * 16.0) * 1.5 + exp(-r * 5.0) * 0.25;
    vec3 col = c * tendrils * 1.4 + vec3(1.0, 0.9, 1.0) * (core + tips * 0.9) + vec3(0.7, 0.8, 1.0) * glassRim
            + c * 0.06 * inside;
    float a = clamp(max(max(tendrils, core + tips), glassRim * 1.5) + inside * 0.18, 0.0, 1.0);
    return vec4(col, a);
}

// ---- 3: the atom ----
vec4 atom(float t) {
    float glow = 0.0, lines = 0.0, electrons = 0.0;
    float shells = 1.0 + floor(full * 3.999);
    float fade = fract(full * 3.999); // (the next ring of it fading in)
    for (int k = 0; k < 4; k++) {
        float fk = float(k);
        if (fk >= shells + 1.0) break;
        float show = fk < shells ? 1.0 : fade * 0.0;
        if (show <= 0.0) continue;
        float ang = fk * 3.14159 / shells + t * 0.08 * (mod(fk, 2.0) < 0.5 ? 1.0 : -1.0);
        vec2 rx = vec2(cos(ang), sin(ang));
        vec2 p = vec2(dot(q, rx), dot(q, vec2(-rx.y, rx.x)));
        vec2 radii = vec2(0.42 + 0.13 * fk, 0.13 + 0.04 * fk);
        float e = length(p / radii);
        float d = abs(e - 1.0) * radii.y;
        lines += exp(-pow(d / 0.006, 2.0)) * 0.55 + exp(-pow(d / 0.03, 2.0)) * 0.12;
        for (int j = 0; j < 2; j++) {
            float ph = t * (1.6 + 0.5 * fk) + float(j) * 3.14159 + fk;
            vec2 at = vec2(cos(ph) * radii.x, sin(ph) * radii.y);
            vec2 world = at.x * rx + at.y * vec2(-rx.y, rx.x);
            float de = length(q - world);
            electrons += exp(-pow(de / 0.028, 2.0)) * 1.6 + exp(-de / 0.07) * 0.35;
        }
    }
    // the nucleus: a cluster of little red and blue balls, jiggling
    vec3 nuc = vec3(0.0);
    float nucA = 0.0;
    for (int i = 0; i < 7; i++) {
        float fi = float(i);
        vec2 c = (vec2(hash1(fi), hash1(fi + 9.0)) - 0.5) * 0.13 + 0.006 * vec2(sin(t * 7.0 + fi), cos(t * 6.0 + fi * 2.0));
        float d = length(q - c) / 0.05;
        if (d < 1.0) {
            float lit = sqrt(1.0 - d * d);
            vec3 base = mod(fi, 2.0) < 0.5 ? vec3(1.0, 0.35, 0.3) : vec3(0.4, 0.55, 1.0);
            nuc = base * (0.45 + 0.65 * lit);
            nucA = 1.0;
        }
    }
    float halo = exp(-length(q) / 0.13) * (0.35 + 0.4 * full);
    vec3 c = vec3(0.45, 0.8, 1.0);
    vec3 col = c * lines + vec3(0.9, 0.97, 1.0) * electrons + vec3(0.8, 0.85, 1.0) * halo;
    col = mix(col, nuc, nucA);
    float a = clamp(max(max(lines, electrons), max(halo, nucA)), 0.0, 1.0);
    return vec4(col, a);
}

void main() {
    float t = GameTime * 1200.0;
    vec4 c;
    if (skin == 1) {
        c = storm(t);
    } else if (skin == 2) {
        c = plasma(t);
    } else if (skin == 3) {
        c = atom(t);
    } else {
        c = star(t);
    }
    c.a *= strength;
    if (c.a < 0.004) {
        discard;
    }
    fragColor = vec4(c.rgb, c.a);
}
