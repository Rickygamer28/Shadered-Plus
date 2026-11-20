#version 150

// A fractal in a glass box, traced by its distance function: 0 a bulb (its power slowly changing), 1 a sponge (its
// holes sliding), 2 a pyramid of pyramids (twisting), 3 a folded crystal (unfolding and folding). Turning slowly;
// coloured by how its points wander as they're worked out (drifting colours); lit, its hollows darker; a glow round
// it, brighter to music; faint glass on the box. Writes its depth.

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

in vec3 lp;
flat in vec3 eye;
flat in vec3 centre;
flat in float kind;
flat in float pulse;
flat in float bright;

out vec4 fragColor;

const float HALF = 0.36;
float T;
float trap;

mat2 rot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float bulb(vec3 p) {
    vec3 z = p;
    float dr = 1.0, r = 0.0;
    float power = 7.0 + 1.5 * sin(T * 0.13);
    trap = 1e9;
    for (int i = 0; i < 6; i++) {
        r = length(z);
        if (r > 2.0) break;
        float th = acos(clamp(z.y / r, -1.0, 1.0)) * power;
        float ph = atan(z.z, z.x) * power;
        dr = pow(r, power - 1.0) * power * dr + 1.0;
        z = pow(r, power) * vec3(sin(th) * cos(ph), cos(th), sin(th) * sin(ph)) + p;
        trap = min(trap, dot(z, z));
    }
    return 0.5 * log(max(r, 1e-4)) * r / dr;
}

float sponge(vec3 p) {
    float d = sdBox(p, vec3(1.0));
    float s = 1.0;
    trap = 1e9;
    for (int m = 0; m < 4; m++) {
        vec3 a = mod(p * s + vec3(0.0, 0.25 * sin(T * 0.25 + float(m)), 0.0), 2.0) - 1.0;
        s *= 3.0;
        vec3 r = abs(1.0 - 3.0 * abs(a));
        float c = (min(max(r.x, r.y), min(max(r.y, r.z), max(r.z, r.x))) - 1.0) / s;
        d = max(d, c);
        trap = min(trap, dot(a, a));
    }
    return d;
}

float pyramid(vec3 z) {
    trap = 1e9;
    for (int i = 0; i < 9; i++) {
        if (z.x + z.y < 0.0) z.xy = -z.yx;
        if (z.x + z.z < 0.0) z.xz = -z.zx;
        if (z.y + z.z < 0.0) z.zy = -z.yz;
        z.xy *= rot(0.12 * sin(T * 0.2));
        z = z * 2.0 - vec3(1.0);
        trap = min(trap, dot(z, z) * 0.05);
    }
    return (length(z) - 1.5) * pow(2.0, -9.0);
}

float crystal(vec3 p) {
    float s = 1.0;
    trap = 1e9;
    for (int i = 0; i < 7; i++) {
        p = abs(p);
        if (p.x < p.y) p.xy = p.yx;
        if (p.x < p.z) p.xz = p.zx;
        if (p.y < p.z) p.yz = p.zy;
        p.xz *= rot(0.25 + 0.2 * sin(T * 0.09));
        p.xy *= rot(0.4 + 0.15 * sin(T * 0.07));
        p = p * 1.8 - vec3(0.9, 0.5, 0.35) * 0.8;
        s *= 1.8;
        trap = min(trap, dot(p, p) * 0.2);
    }
    return sdBox(p, vec3(0.9)) / s;
}

// distance (in the box's own size, -1 .. 1 across it) - each fractal scaled to fill it
float de(vec3 q) {
    q.xz *= rot(T * 0.12);
    if (kind < 0.5) return bulb(q * 1.3) / 1.3;
    if (kind < 1.5) return sponge(q * 1.15) / 1.15;
    if (kind < 2.5) return pyramid(q * 1.25) / 1.25;
    return crystal(q * 1.1) / 1.1;
}

vec3 palette(float t) {
    vec3 shift = kind < 0.5 ? vec3(0.0, 0.33, 0.67) : kind < 1.5 ? vec3(0.1, 0.2, 0.3) : kind < 2.5 ? vec3(0.5, 0.2, 0.25) : vec3(0.8, 0.9, 0.3);
    return 0.5 + 0.5 * cos(6.2831 * (t + shift));
}

void main() {
    T = GameTime * 1200.0;
    vec3 rd = normalize(lp - eye);
    vec3 ro = eye;
    // the box: where the ray's in it
    vec3 inv = 1.0 / rd;
    vec3 t0 = (-vec3(HALF) - ro) * inv, t1 = (vec3(HALF) - ro) * inv;
    vec3 tmin = min(t0, t1), tmax = max(t0, t1);
    float tin = max(max(tmin.x, tmin.y), max(tmin.z, 0.0)), tout = min(tmax.x, min(tmax.y, tmax.z));
    if (tin >= tout) discard;
    vec3 boxN = -sign(rd) * step(tmin.yzx, tmin.xyz) * step(tmin.zxy, tmin.xyz);

    float swell = 1.0 + pulse * 0.08;
    float t = tin, minD = 1e9;
    bool hit = false;
    int steps = 0;
    for (int i = 0; i < 96; i++) {
        vec3 q = (ro + rd * t) / (HALF * swell);
        float d = de(q) * HALF * swell;
        minD = min(minD, d);
        if (d < 0.0006 * (1.0 + t)) { hit = true; break; }
        t += d * 0.9;
        steps = i;
        if (t > tout) break;
    }
    float hue = T * 0.015;
    vec3 glass = vec3(0.75, 0.85, 1.0) * pow(1.0 - abs(dot(rd, boxN)), 4.0) * 0.25;
    vec3 col;
    float alpha;
    float depthT = tin;
    if (hit) {
        vec3 p = (ro + rd * t) / (HALF * swell);
        float tr = trap;
        // its normal: four samples round the point
        vec2 k = vec2(1.0, -1.0) * 0.0015;
        vec3 n = normalize(k.xyy * de(p + k.xyy) + k.yyx * de(p + k.yyx) + k.yxy * de(p + k.yxy) + k.xxx * de(p + k.xxx));
        vec3 base = palette(sqrt(tr) * 0.7 + hue);
        vec3 L = normalize(vec3(0.5, 0.85, 0.35));
        float diff = max(dot(n, L), 0.0);
        float occ = 1.0 - float(steps) / 96.0;
        float rim = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);
        float lit = 0.35 + 0.65 * bright;
        col = base * (0.18 + 0.82 * diff) * occ * lit + base * rim * (0.4 + pulse * 0.6);
        col += base * 0.12; // (its own faint glow, in the dark too)
        col += glass;
        alpha = 1.0;
        depthT = t;
    } else {
        // the glow round it, brighter the nearer the ray passed (and to music), and the glass
        float g = exp(-max(minD, 0.0) * 55.0) * (0.45 + pulse * 0.8);
        col = palette(hue + 0.3) * g + glass;
        alpha = clamp(max(g, 0.0) * 0.8 + length(glass), 0.0, 0.85);
        if (alpha < 0.01) discard;
        col /= max(alpha, 1e-3);
    }
    vec4 clip = ProjMat * ModelViewMat * vec4(centre + ro + rd * depthT, 1.0);
    gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0);
    fragColor = vec4(col, alpha);
}
