#version 150

// Shadered+'s lava lamp pillar: one block's part of a glass column (as tall as the pillar), coloured liquid inside and
// wax blobs rising and sinking the whole height, merging and parting, a pool of wax at the foot. Each block draws the
// pixels whose glass it holds; the inside is traced through the whole column. Heights here: blocks from the pillar's
// bottom (the block's own y + SegY).

in vec3 lp;
in vec3 eye;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 PoseMat;
uniform float Time;
uniform float SegY;   // this block: how many above the pillar's bottom
uniform float Height; // the pillar, in blocks
uniform float Seed;
uniform vec3 Wax;
uniform vec3 Liquid;

out vec4 fragColor;

const float R = 0.34;
const float FOOT = 0.1875, CAP = 0.1875;

float hash(float n) { return fract(sin(n * 127.1 + Seed * 311.7) * 43758.5453); }

vec4 B[24]; // (the blobs where they are now: worked out once a pixel)
int NB;

void placeBlobs() {
    float top = Height - CAP;
    NB = int(min(3.0 * Height + 1.0, 24.0));
    for (int k = 0; k < 24; k++) {
        if (k >= NB) break;
        float fk = float(k);
        float speed = 0.05 + 0.06 * hash(fk + 1.0);
        float phase = hash(fk + 2.0) * 6.2831;
        // up and down the whole column, slow at the ends (cooling at the top, warming at the foot)
        float y = FOOT + 0.12 + (top - FOOT - 0.24) * (0.5 - 0.5 * cos(Time * speed * 3.0 / max(Height, 1.0) + phase));
        vec2 xz = vec2(0.5) + (vec2(hash(fk + 3.0), hash(fk + 4.0)) - 0.5) * 0.22
                + 0.03 * vec2(sin(Time * 0.3 + fk), cos(Time * 0.27 + fk * 1.3));
        float r = (0.075 + 0.07 * hash(fk + 5.0)) * (1.0 + 0.15 * sin(Time * 0.5 + fk * 2.0)); // (wobbling)
        B[k] = vec4(xz.x, y, xz.y, r);
    }
}

float field(vec3 p) {
    float f = 0.0;
    for (int k = 0; k < 24; k++) {
        if (k >= NB) break;
        vec3 d = p - B[k].xyz;
        if (abs(d.y) > 0.9) continue; // (too far to matter)
        d.y *= 0.8; // (a little taller than wide)
        f += B[k].w * B[k].w / (dot(d, d) + 1e-4);
    }
    float pool = p.y - FOOT; // (the wax pooled at the foot)
    f += 0.0035 / (pool * pool + 0.0004);
    return f;
}

float depthOf(vec3 p) {
    vec4 c = ProjMat * ModelViewMat * PoseMat * vec4(p, 1.0);
    return clamp(c.z / c.w * 0.5 + 0.5, 0.0, 1.0);
}

void main() {
    vec3 rd = normalize(lp - eye);
    vec3 ro = eye + vec3(0.0, SegY, 0.0); // (in the pillar's heights)
    vec2 oc = ro.xz - 0.5;
    float a = dot(rd.xz, rd.xz), b = dot(oc, rd.xz), c = dot(oc, oc) - R * R;
    float h = b * b - a * c;
    if (h < 0.0 || a < 1e-6) discard;
    h = sqrt(h);
    float t0 = (-b - h) / a, t1 = (-b + h) / a;
    // between the foot and the cap
    float y0 = FOOT, y1 = Height - CAP;
    float ta = (y0 - ro.y) / rd.y, tb = (y1 - ro.y) / rd.y;
    float sn = min(ta, tb), sf = max(ta, tb);
    if (abs(rd.y) < 1e-5) { sn = -1e9; sf = 1e9; if (ro.y < y0 || ro.y > y1) discard; }
    float tn = max(max(t0, sn), 0.0), tf = min(t1, sf);
    if (tn >= tf) discard;
    vec3 e = ro + rd * tn;
    if (e.y - SegY < -0.001 || e.y - SegY >= 1.0) discard; // (another block of the pillar draws this one)
    vec3 n = tn == sn ? vec3(0.0, -sign(rd.y), 0.0) : normalize(vec3(e.x - 0.5, 0.0, e.z - 0.5));

    // through the liquid until the wax
    placeBlobs();
    float steps = 32.0;
    float dt = (tf - tn) / steps;
    vec3 col = vec3(0.0);
    float glow = 0.0;
    bool hit = false;
    vec3 hp = e;
    for (int i = 0; i < 32; i++) {
        vec3 p = ro + rd * (tn + dt * (float(i) + 0.5));
        float f = field(p);
        glow += clamp(f, 0.0, 1.0) * dt;
        if (f > 1.0) { hit = true; hp = p; break; }
    }
    vec3 liquid = Liquid * 0.55 + Wax * clamp(glow * 1.2, 0.0, 0.6) * 0.5;
    if (hit) {
        float e2 = 0.01;
        vec3 g = vec3(field(hp + vec3(e2, 0.0, 0.0)) - field(hp - vec3(e2, 0.0, 0.0)),
                field(hp + vec3(0.0, e2, 0.0)) - field(hp - vec3(0.0, e2, 0.0)),
                field(hp + vec3(0.0, 0.0, e2)) - field(hp - vec3(0.0, 0.0, e2)));
        vec3 wn = normalize(-g);
        float lit = 0.55 + 0.45 * max(dot(wn, normalize(vec3(0.3, 0.8, 0.4))), 0.0);
        float rim = pow(1.0 - max(dot(wn, -rd), 0.0), 2.0);
        vec3 wax = Wax * lit * 1.1 + Wax * rim * 0.4;
        float through = clamp(length(hp - e) * 1.8, 0.0, 0.6); // (seen through some liquid)
        col = mix(wax, liquid, through);
    } else {
        col = liquid;
    }
    // the glass: a shine and the edges catching the light
    float fres = pow(1.0 - abs(dot(-rd, n)), 3.0);
    float spec = pow(max(dot(reflect(rd, n), normalize(vec3(-0.4, 0.7, 0.55))), 0.0), 50.0);
    col = mix(col, vec3(0.85, 0.9, 1.0), fres * 0.35) + vec3(1.0) * spec * 0.8;
    fragColor = vec4(col, 1.0);
    gl_FragDepth = depthOf(e - vec3(0.0, SegY, 0.0));
}
