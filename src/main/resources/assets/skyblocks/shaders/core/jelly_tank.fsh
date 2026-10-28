#version 150

// Shadered+'s jellyfish tank (our own code): the view ray goes through the glass into blue water lit from above,
// past rising bubbles, and finds the jellyfish - pulsing bells with frilly arms and long trailing tentacles, drifting
// slowly up and down, glowing. Everything in px, the tank's box from (2, 3.3, 2) to (14, 29, 14).

in vec3 wray;
in vec3 lpos;
in float lit;
in vec4 vColor;

uniform float JellyTime;

out vec4 fragColor;

const vec3 LO = vec3(2.0, 3.3, 2.0);
const vec3 HI = vec3(14.0, 29.0, 14.0);

float hash1(float n) {
    return fract(sin(n * 12.9898) * 43758.5453);
}

float sdEllipsoid(vec3 p, vec3 r) {
    float k0 = length(p / r);
    float k1 = length(p / (r * r));
    return k0 * (k0 - 1.0) / max(k1, 1e-6);
}

vec2 boxHit(vec3 ro, vec3 rd, vec3 lo, vec3 hi) {
    vec3 inv = 1.0 / rd;
    vec3 a = (lo - ro) * inv, b = (hi - ro) * inv;
    vec3 n = min(a, b), f = max(a, b);
    return vec2(max(max(n.x, n.y), n.z), min(min(f.x, f.y), f.z));
}

// where jellyfish k is, how big, and how far through its pulse
vec4 jelly(int k, float t) {
    float fk = float(k);
    float ph = hash1(fk * 7.3) * 6.2831;
    float y = 16.5 + 8.0 * sin(t * 0.11 + ph) + 1.2 * sin(t * 0.37 + ph * 2.0);
    float x = 8.0 + 2.6 * sin(t * 0.17 + ph * 1.7);
    float z = 8.0 + 2.6 * cos(t * 0.13 + ph * 2.3);
    float size = 0.8 + 0.35 * hash1(fk * 3.1);
    return vec4(x, y, z, size);
}

// the scene: x = distance, y = what (0 bell, 1 arms / tentacles, 2 bubble)
vec2 scene(vec3 p, float t) {
    vec2 res = vec2(1e9, 0.0);
    for (int k = 0; k < 3; k++) {
        vec4 j = jelly(k, t);
        float s = j.w;
        vec3 q = (p - j.xyz) / s;
        float pulse = sin(t * 2.1 + float(k) * 2.0);
        vec3 bq = vec3(q.x, q.y - clamp(q.y, -10.0, 2.0), q.z); // (far from this one: skip it)
        float bound = length(bq) - 3.7;
        if (bound > 0.6) {
            res.x = min(res.x, bound * s);
            continue;
        }
        vec3 r = vec3(2.3 * (1.0 + 0.1 * pulse), 1.7 * (1.0 - 0.14 * pulse), 2.3 * (1.0 + 0.1 * pulse));
        float bell = max(sdEllipsoid(q, r), -sdEllipsoid(q + vec3(0.0, 0.55, 0.0), r * vec3(0.84, 0.82, 0.84)));
        bell = max(bell, -q.y - 0.25) * s;
        if (bell < res.x) res = vec2(bell, 0.0);
        // long thin tentacles from the rim, trailing and swaying
        float tent = 1e9;
        for (int i = 0; i < 6; i++) {
            float a = float(i) * 1.047 + 0.3;
            vec3 A = vec3(cos(a) * r.x * 0.86, -0.15, sin(a) * r.z * 0.86);
            float L = 9.0;
            float u = clamp((A.y - q.y) / L, 0.0, 1.0);
            vec3 C = A + vec3(sin(u * 4.0 - t * 1.6 + a) * 0.9 * u, -u * L, cos(u * 3.5 - t * 1.3 + a) * 0.9 * u);
            C.xz *= 1.0 - 0.25 * u;
            tent = min(tent, length(q - C) - 0.11 * (1.0 - 0.5 * u));
        }
        // a few frilly arms under the middle
        for (int i = 0; i < 4; i++) {
            float a = float(i) * 1.571 + t * 0.2;
            float L = 5.0;
            float u = clamp((-0.3 - q.y) / L, 0.0, 1.0);
            vec3 C = vec3(cos(a) * 0.45, -0.3 - u * L, sin(a) * 0.45) + vec3(sin(u * 5.0 - t * 1.2 + a), 0.0, cos(u * 4.0 - t + a)) * 0.45 * u;
            tent = min(tent, length(q - C) - 0.3 * (1.0 - 0.6 * u) - 0.06 * sin(u * 30.0 + a * 3.0));
        }
        tent *= s * 0.8;
        if (tent < res.x) res = vec2(tent, 1.0);
    }
    // bubbles rising in four streams
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        vec2 c = vec2(3.6 + 8.8 * step(1.5, fi), 3.6 + 8.8 * mod(fi, 2.0));
        float sp = 4.0 + fi;
        float py = mod(p.y - t * sp + fi * 1.7, 2.4) - 1.2;
        vec3 q = vec3(p.x - c.x - 0.25 * sin(p.y * 1.3 + fi), py, p.z - c.y - 0.25 * cos(p.y * 1.1 + fi));
        float d = length(q) - 0.2 - 0.05 * fi;
        d = max(d, max(4.0 - p.y, p.y - 28.5));
        if (d < res.x) res = vec2(d, 2.0);
    }
    return res;
}

void main() {
    float t = JellyTime;
    float g = clamp(lit, 0.0, 1.0);
    g = g * g * (3.0 - 2.0 * g);
    vec3 jc = vColor.rgb;
    vec3 rd = normalize(wray);
    vec3 ro = lpos;
    float tEnd = max(boxHit(ro, rd, LO, HI).y, 0.0);

    // march
    float tt = 0.02;
    vec2 hit = vec2(-1.0);
    float halo = 0.0;
    for (int i = 0; i < 64; i++) {
        vec3 p = ro + rd * tt;
        vec2 s = scene(p, t);
        if (s.y < 1.5) {
            halo += exp(-max(s.x, 0.0) * 0.9) * 0.012;
        }
        if (s.x < 0.03) {
            hit = vec2(tt, s.y);
            break;
        }
        tt += max(s.x * 0.8, 0.04);
        if (tt > tEnd) {
            break;
        }
    }
    float tw = hit.x > 0.0 ? hit.x : tEnd;
    vec3 pe = ro + rd * tw;

    // the water behind: deep blue, lighter up towards the lamp at the top, soft shafts of light; sand at the bottom
    vec3 deep = vec3(0.01, 0.03, 0.08), shallow = vec3(0.06, 0.32, 0.5);
    vec3 water = mix(deep, shallow, smoothstep(3.0, 29.0, pe.y) * (0.25 + 0.75 * g));
    float shafts = 0.5 + 0.5 * sin(pe.x * 0.9 + pe.z * 0.6 + t * 0.25) * sin(pe.x * 0.37 - pe.z * 0.8 - t * 0.17);
    water += vec3(0.05, 0.12, 0.14) * shafts * smoothstep(10.0, 29.0, pe.y) * g;
    vec3 col = water;
    if (hit.x < 0.0 && pe.y < LO.y + 0.05) { // (the sandy floor, with light rippling on it)
        float cs = 0.5 + 0.5 * sin(pe.x * 2.1 + t * 1.3) * sin(pe.z * 1.9 - t * 1.1);
        col = vec3(0.55, 0.5, 0.38) * (0.25 + 0.6 * g) + vec3(0.25, 0.35, 0.35) * cs * g * 0.6;
    }
    if (hit.x > 0.0) {
        vec2 e = vec2(0.04, 0.0);
        vec3 n = normalize(vec3(scene(pe + e.xyy, t).x - scene(pe - e.xyy, t).x, scene(pe + e.yxy, t).x - scene(pe - e.yxy, t).x,
                                scene(pe + e.yyx, t).x - scene(pe - e.yyx, t).x));
        float fres = pow(1.0 - abs(dot(n, rd)), 2.0);
        if (hit.y > 1.5) { // a bubble: a bright silvery rim
            col = mix(water, vec3(0.8, 0.95, 1.0), 0.25 + 0.6 * fres);
        } else {
            float glow = 0.45 + 0.55 * g;
            vec3 jelly = jc * (hit.y > 0.5 ? 0.55 : 0.35) + jc * fres * 1.3 + vec3(0.25) * fres * fres;
            jelly *= glow;
            col = mix(water, jelly + water * 0.4, hit.y > 0.5 ? 0.8 : 0.7);
        }
    }
    col += jc * halo * (0.6 + 0.6 * g);
    // a little murk with distance
    col = mix(col, water, 1.0 - exp(-tw * 0.025));

    // the glass on the sides: clear, soft reflections at grazing angles, a faint sheen
    vec3 n = abs(ro.x - LO.x) < 0.05 ? vec3(-1, 0, 0) : abs(ro.x - HI.x) < 0.05 ? vec3(1, 0, 0)
           : abs(ro.z - LO.z) < 0.05 ? vec3(0, 0, -1) : abs(ro.z - HI.z) < 0.05 ? vec3(0, 0, 1) : vec3(0.0);
    if (dot(n, n) > 0.5) {
        float c = abs(dot(rd, n));
        float fres = 0.04 + 0.5 * pow(1.0 - c, 4.0);
        vec3 rf = reflect(rd, n);
        vec3 sky = mix(vec3(0.3, 0.33, 0.32), vec3(0.85, 0.92, 1.0), smoothstep(-0.2, 0.5, rf.y));
        float u = n.x != 0.0 ? ro.z : ro.x;
        float sheen = smoothstep(2.5, 0.0, abs(u * 0.9 + ro.y * 0.45 - 14.0)) * 0.06;
        col = mix(col, sky, clamp(fres + sheen, 0.0, 0.6));
    }
    fragColor = vec4(col, 1.0);
}
