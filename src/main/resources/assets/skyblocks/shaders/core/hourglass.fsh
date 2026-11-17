#version 150

// An hourglass's glass and sand (blocks, from its middle; turned over about x as it's turned). Two round glass bulbs
// meeting at a narrow neck; the sand in the top one - its surface sinking into a funnel as it runs - and a heap growing
// in the bottom one, a thin stream between them while it runs. The sand glows warm gold, grains glinting. The glass is
// seen front and back, brighter at its edges, a highlight on it. Writes its depth.

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

in vec3 lp;
flat in vec3 eye;
flat in vec3 centre;
flat in vec3 half_;
flat in float turned;
flat in float fallen;
flat in float running;

out vec4 fragColor;

const float H = 0.36, PI = 3.14159265;

float hash3(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

// the bulbs' radius at a height (the neck at 0)
float radiusAt(float y) {
    float f = clamp(abs(y) / H, 0.0, 1.0);
    return 0.03 + 0.245 * sin(f * PI * 0.75);
}

float bulb(vec3 p) {
    return max((length(p.xz) - radiusAt(p.y)) * 0.75, abs(p.y) - H);
}

// the sand: how far, and which (1 top, 2 heap, 3 stream)
float topLevel, heapH;

float sand(vec3 p, out int which) {
    float inner = bulb(p) + 0.012;
    float r = length(p.xz);
    // the top: up from the neck to its level, a funnel in the middle as it drains
    float funnel = running > 0.5 ? 0.09 * (1.0 - smoothstep(0.0, 0.2, r)) : 0.0;
    float top = max(max(inner, p.y - (topLevel - funnel)), -p.y + 0.002);
    top = topLevel <= 0.004 ? 1e3 : top;
    // the heap: a cone up from the bottom
    float heap = max(inner, (p.y - (-H + heapH - r * 0.55)) * 0.85);
    heap = heapH <= 0.002 ? 1e3 : heap;
    float d = top;
    which = 1;
    if (heap < d) { d = heap; which = 2; }
    if (running > 0.5 && topLevel > 0.004) {
        float bottom = -H + heapH;
        float stream = max(length(p.xz) - 0.007, max(p.y - 0.01, bottom - p.y));
        if (stream < d) { d = stream; which = 3; }
    }
    return d;
}

vec3 sandNormal(vec3 p) {
    vec2 e = vec2(0.002, 0.0);
    int w;
    return normalize(vec3(sand(p + e.xyy, w) - sand(p - e.xyy, w), sand(p + e.yxy, w) - sand(p - e.yxy, w), sand(p + e.yyx, w) - sand(p - e.yyx, w)));
}

vec3 bulbNormal(vec3 p) {
    vec2 e = vec2(0.002, 0.0);
    return normalize(vec3(bulb(p + e.xyy) - bulb(p - e.xyy), bulb(p + e.yxy) - bulb(p - e.yxy), bulb(p + e.yyx) - bulb(p - e.yyx)));
}

void main() {
    float t = GameTime * 1200.0;
    // how much sand where: what's left above fills the top bulb up from the neck; what's fallen heaps below
    float left = 1.0 - fallen;
    topLevel = H * 0.82 * pow(left, 0.75);
    heapH = H * 0.78 * pow(fallen, 0.7);
    // turned over: the ray turned the other way (about x)
    float a = turned * PI;
    mat3 rx = mat3(1.0, 0.0, 0.0, 0.0, cos(a), sin(a), 0.0, -sin(a), cos(a));
    vec3 ro0 = eye, rd0 = normalize(lp - eye);
    vec3 inv = 1.0 / (sign(rd0) * max(abs(rd0), vec3(1e-6)));
    vec3 ta = (-half_ - ro0) * inv, tb = (half_ - ro0) * inv;
    float t0 = max(max(max(min(ta.x, tb.x), min(ta.y, tb.y)), min(ta.z, tb.z)), 0.0);
    float t1 = min(min(max(ta.x, tb.x), max(ta.y, tb.y)), max(ta.z, tb.z));
    if (t1 <= t0) {
        discard;
    }
    vec3 ro = rx * ro0, rd = rx * rd0;
    vec3 col = vec3(0.0);
    float alpha = 0.0, depthT = -1.0;
    float tt = t0;
    vec3 light = rx * normalize(vec3(0.4, 0.9, 0.3));
    for (int i = 0; i < 96 && tt < t1 && alpha < 0.97; i++) {
        vec3 p = ro + rd * tt;
        int which;
        float s = sand(p, which);
        float g = abs(bulb(p)) - 0.004;
        if (s < 0.0015) {
            vec3 n = sandNormal(p);
            float diff = 0.55 + 0.45 * max(dot(n, light), 0.0);
            vec3 c = mix(vec3(0.85, 0.55, 0.18), vec3(1.0, 0.82, 0.42), diff);
            // grains, and glints that come and go
            vec3 cell = floor(p * 160.0);
            float grain = hash3(cell);
            c *= 0.85 + 0.25 * grain;
            float glint = step(0.985, hash3(cell + floor(t * 0.15 + grain * 7.0)));
            c += vec3(1.0, 0.95, 0.75) * glint * 1.2;
            c += vec3(1.0, 0.65, 0.2) * 0.25; // (its own glow)
            if (which == 3) c = vec3(1.0, 0.8, 0.4) * 1.1;
            col += (1.0 - alpha) * c;
            alpha = 1.0;
            if (depthT < 0.0) depthT = tt;
            break;
        }
        if (g < 0.0015) {
            vec3 n = bulbNormal(p);
            float fres = pow(1.0 - abs(dot(n, rd)), 3.0);
            float ga = 0.05 + 0.55 * fres;
            vec3 gc = vec3(0.8, 0.9, 1.0) * ga + vec3(1.0) * pow(max(dot(reflect(rd, n), light), 0.0), 40.0) * 0.6;
            col += (1.0 - alpha) * gc;
            alpha += (1.0 - alpha) * ga;
            if (depthT < 0.0) depthT = tt;
            tt += 0.012;
            continue;
        }
        tt += max(min(s, g), 0.0015);
    }
    if (alpha < 0.01 || depthT < 0.0) {
        discard;
    }
    vec4 clip = ProjMat * ModelViewMat * vec4(centre + ro0 + rd0 * depthT, 1.0);
    gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0);
    fragColor = vec4(col / max(alpha, 0.001), alpha);
}
