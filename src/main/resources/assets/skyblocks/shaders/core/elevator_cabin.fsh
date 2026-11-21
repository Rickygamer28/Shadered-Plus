#version 150

// The elevator's cabin, in its look (blocks, from the box's middle; its floor's top at FY):
//  0 glass capsule:  a rounded glass box, a metal floor and roof, bright bands round it;
//  1 hard-light:     a floor of glowing hexagons, walls of faint hexagon light fading upward, a trail of light under it;
//  2 bubble pod:     a soap bubble round you, its film shifting through the colours, a pearly disc to stand on;
//  3 warp cabin:     a dark frame - its edges, violet light along them - a floor and a roof, its sides open;
//  4 crystal cabin:  a six-sided crystal room with a pointed roof, purple, light splitting into colours at its edges;
//  5 cloud:          a cloud to stand on, soft and lumpy, drifting.
// Glass (and the bubble's film) is seen front and back; what's solid stops the ray. Writes its depth (the nearest thing
// it shows): what's in it - its riders - hides what's behind them, and is seen through what's in front.

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

in vec3 lp;
flat in vec3 eye;
flat in vec3 centre;
flat in int skin;
flat in float vel;

out vec4 fragColor;

const vec3 HALF = vec3(1.5, 2.0, 1.5);
const float FY = -0.8;
const float TAU = 6.2831853;

float T;

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
        p = p * 2.02 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

float sdBox(vec3 p, vec3 b) {
    vec3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float sdRoundBox(vec3 p, vec3 b, float r) {
    vec3 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0) - r;
}

float sdBoxFrame(vec3 p, vec3 b, float e) {
    p = abs(p) - b;
    vec3 q = abs(p + e) - e;
    return min(min(length(max(vec3(p.x, q.y, q.z), 0.0)) + min(max(p.x, max(q.y, q.z)), 0.0),
                   length(max(vec3(q.x, p.y, q.z), 0.0)) + min(max(q.x, max(p.y, q.z)), 0.0)),
               length(max(vec3(q.x, q.y, p.z), 0.0)) + min(max(q.x, max(q.y, p.z)), 0.0));
}

// a hexagon's distance (flat sides along x), size r (its apothem)
float sdHex(vec2 p, float r) {
    const vec3 k = vec3(-0.866025404, 0.5, 0.577350269);
    p = abs(p);
    p -= 2.0 * min(dot(k.xy, p), 0.0) * k.xy;
    p -= vec2(clamp(p.x, -k.z * r, k.z * r), r);
    return length(p) * sign(p.y);
}

// how near the edge of a hexagon tiling (0 on an edge, 0.5 in a cell's middle)
float hexEdge(vec2 p) {
    const vec2 s = vec2(1.0, 1.7320508);
    vec4 hc = floor(vec4(p, p - vec2(0.5, 1.0)) / s.xyxy) + 0.5;
    vec4 h = vec4(p - hc.xy * s, p - (hc.zw + 0.5) * s);
    vec2 c = dot(h.xy, h.xy) < dot(h.zw, h.zw) ? h.xy : h.zw;
    c = abs(c);
    return 0.5 - max(dot(c, normalize(s)), c.x);
}

// ---- what's solid (stops the ray): its distance, and which (1 metal, 2 hexagon light, 3 pearl, 4 dark frame, 5 crystal)
float solid(vec3 p, out int mat) {
    mat = 0;
    float d = 1e3;
    if (skin == 0) {
        d = min(sdBox(p - vec3(0.0, FY - 0.09, 0.0), vec3(1.45, 0.09, 1.45)), sdBox(p - vec3(0.0, FY + 2.66, 0.0), vec3(1.45, 0.07, 1.45)));
        mat = 1;
    } else if (skin == 1) {
        d = max(length(p.xz) - 1.44, abs(p.y - (FY - 0.06)) - 0.06);
        mat = 2;
    } else if (skin == 2) {
        d = max(length(p.xz) - 1.05, abs(p.y - (FY - 0.04)) - 0.04);
        mat = 3;
    } else if (skin == 3) {
        float f = sdBoxFrame(p - vec3(0.0, FY + 1.3, 0.0), vec3(1.42, 1.3, 1.42), 0.06);
        float floor_ = sdBox(p - vec3(0.0, FY - 0.08, 0.0), vec3(1.45, 0.08, 1.45));
        float roof = sdBox(p - vec3(0.0, FY + 2.66, 0.0), vec3(1.45, 0.06, 1.45));
        d = min(f, min(floor_, roof));
        mat = 4;
    } else if (skin == 4) {
        d = max(sdHex(p.xz, 1.3), abs(p.y - (FY - 0.07)) - 0.07);
        mat = 5;
    }
    return d;
}

// ---- what's glass (seen through, front and back): its distance
float glassBody(vec3 p) {
    if (skin == 0) {
        return sdRoundBox(p - vec3(0.0, FY + 1.3, 0.0), vec3(1.42, 1.3, 1.42), 0.4);
    } else if (skin == 1) {
        return max(length(p.xz) - 1.42, abs(p.y - (FY + 1.05)) - 1.05);
    } else if (skin == 2) {
        return length(p - vec3(0.0, FY + 1.15, 0.0)) - 1.42;
    } else if (skin == 4) {
        float h = sdHex(p.xz, 1.38);
        // (a pointed roof: highest in the middle)
        float roof = (p.y - (FY + 2.95 - (1.38 + h) * 0.45)) / 1.0966; // (over its slope: a true distance)
        return max(max(h, roof), FY - p.y);
    }
    return 1e3;
}

float glass(vec3 p) {
    float b = glassBody(p);
    if (skin == 1) {
        // (open at the top: only the round wall)
        return max(abs(length(p.xz) - 1.42) - 0.004, abs(p.y - (FY + 1.05)) - 1.05);
    }
    return abs(b) - 0.004;
}

vec3 glassNormal(vec3 p) {
    vec2 e = vec2(0.003, 0.0);
    return normalize(vec3(glassBody(p + e.xyy) - glassBody(p - e.xyy), glassBody(p + e.yxy) - glassBody(p - e.yxy),
                          glassBody(p + e.yyx) - glassBody(p - e.yyx)));
}

vec3 solidNormal(vec3 p) {
    vec2 e = vec2(0.003, 0.0);
    int m;
    return normalize(vec3(solid(p + e.xyy, m) - solid(p - e.xyy, m), solid(p + e.yxy, m) - solid(p - e.yxy, m),
                          solid(p + e.yyx, m) - solid(p - e.yyx, m)));
}

// glass's colour (premultiplied rgb, alpha) where the ray crosses it
vec4 glassColour(vec3 p, vec3 n, vec3 rd) {
    float fres = pow(1.0 - abs(dot(n, rd)), 3.0);
    if (skin == 0) {
        float band = smoothstep(0.035, 0.0, abs(p.y - (FY + 0.32))) + smoothstep(0.035, 0.0, abs(p.y - (FY + 2.28)));
        vec3 c = vec3(0.65, 0.88, 1.0);
        float a = 0.06 + 0.5 * fres;
        vec3 col = c * a + vec3(0.6, 0.9, 1.0) * band * 0.9;
        return vec4(col, clamp(a + band * 0.6, 0.0, 1.0));
    } else if (skin == 1) {
        float up = clamp((p.y - FY) / 2.1, 0.0, 1.0);
        float ang = atan(p.z, p.x);
        float e = hexEdge(vec2(ang * 1.42 * 3.0, p.y * 3.0));
        float line = smoothstep(0.06, 0.0, e);
        float shimmer = 0.6 + 0.4 * sin(p.y * 6.0 - T * 0.15 + ang * 3.0);
        float a = (0.03 + line * 0.55 * shimmer + fres * 0.25) * (1.0 - up) * (1.0 - up);
        return vec4(vec3(0.45, 1.0, 0.82) * a, a * 0.8);
    } else if (skin == 2) {
        float film = fres * 2.2 + fbm(p * 1.6 + vec3(0.0, T * 0.02, 0.0)) * 1.4 + T * 0.004;
        vec3 rainbow = 0.5 + 0.5 * cos(TAU * (film + vec3(0.0, 0.33, 0.67)));
        float a = 0.05 + 0.6 * fres * fres;
        vec3 col = rainbow * a * 1.3;
        // a highlight, up and to the side
        vec3 l = normalize(vec3(-0.5, 0.8, 0.3));
        col += vec3(1.0) * pow(max(dot(reflect(rd, n), l), 0.0), 40.0) * 0.8;
        return vec4(col, clamp(a + 0.1 * fres, 0.0, 1.0));
    } else if (skin == 4) {
        float h = sdHex(p.xz, 1.38);
        // near the prism's edges (corners of the hexagon): the colours split
        float ang = atan(p.z, p.x) + TAU / 12.0;
        float corner = abs(fract(ang / (TAU / 6.0)) - 0.5) * 2.0;
        float edge = smoothstep(0.85, 1.0, corner);
        vec3 split = 0.5 + 0.5 * cos(TAU * (p.y * 0.8 + vec3(0.0, 0.33, 0.67)));
        float a = 0.1 + 0.45 * fres + edge * 0.25;
        vec3 col = vec3(0.62, 0.38, 0.95) * a + split * edge * 0.45;
        return vec4(col, clamp(a, 0.0, 1.0));
    }
    return vec4(0.0);
}

// what's solid's colour (lit, glowing)
vec3 solidColour(vec3 p, vec3 n, vec3 rd, int mat) {
    vec3 l = normalize(vec3(0.3, 0.9, 0.4));
    float diff = 0.35 + 0.65 * max(dot(n, l), 0.0);
    if (mat == 1) {
        vec3 c = vec3(0.62, 0.66, 0.72) * diff;
        // a strip of light round its floor's edge
        float edge = max(abs(p.x), abs(p.z));
        c += vec3(0.4, 0.8, 1.0) * smoothstep(1.38, 1.44, edge) * step(p.y, FY + 0.01) * 1.2;
        return c;
    } else if (mat == 2) {
        float e = hexEdge(p.xz * 3.2);
        float line = smoothstep(0.07, 0.0, e);
        float pulse = 0.6 + 0.4 * sin(length(p.xz) * 5.0 - T * 0.2);
        return vec3(0.05, 0.25, 0.2) + vec3(0.45, 1.0, 0.8) * line * pulse * 1.3 + vec3(0.2, 0.6, 0.5) * smoothstep(1.3, 1.44, length(p.xz));
    } else if (mat == 3) {
        float r = length(p.xz);
        vec3 c = mix(vec3(0.95, 0.92, 1.0), vec3(0.85, 0.75, 1.0), r / 1.05) * diff;
        return c + 0.5 + 0.5 * cos(TAU * (r * 0.8 + vec3(0.0, 0.33, 0.67))) * 0.08;
    } else if (mat == 4) {
        vec3 c = vec3(0.13, 0.12, 0.17) * diff;
        // violet light along its edges (the frame) and round its floor
        float g = smoothstep(0.012, 0.0, abs(abs(p.x) - 1.42) * abs(abs(p.z) - 1.42)) ;
        float edgeFloor = smoothstep(1.36, 1.44, max(abs(p.x), abs(p.z))) * step(abs(p.y - FY), 0.02);
        c += vec3(0.7, 0.45, 1.0) * (g * 0.8 + edgeFloor * 1.2);
        // a grid on the floor
        vec2 gr = abs(fract(p.xz * 2.0) - 0.5);
        c += vec3(0.4, 0.3, 0.8) * smoothstep(0.47, 0.5, max(gr.x, gr.y)) * step(abs(p.y - FY), 0.01) * 0.5;
        return c;
    } else if (mat == 5) {
        float e = sdHex(p.xz, 1.3);
        vec3 c = vec3(0.45, 0.25, 0.75) * diff + vec3(0.8, 0.6, 1.0) * smoothstep(-0.08, 0.0, e) * 0.8;
        return c + vec3(0.6, 0.4, 1.0) * pow(1.0 - abs(dot(n, rd)), 3.0) * 0.4;
    }
    return vec3(1.0, 0.0, 1.0);
}

// glowing air (premultiplied rgb a block): the hard-light trail under its floor
vec3 glowAt(vec3 p) {
    if (skin == 1) {
        float below = (FY - 0.12) - p.y;
        if (below > 0.0 && below < 1.15) {
            float k = below / 1.15;
            float r = length(p.xz);
            float cone = 1.4 * (1.0 - k);
            float rim = exp(-abs(r - cone) * 14.0);
            float lines = 0.5 + 0.5 * sin(p.y * 22.0 + T * (0.2 + abs(vel) * 4.0));
            return vec3(0.35, 1.0, 0.8) * rim * lines * (1.0 - k) * (0.6 + 2.0 * abs(vel)) * 0.9;
        }
    }
    return vec3(0.0);
}

// the cloud: how thick it is here
float cloud(vec3 p) {
    vec3 q = (p - vec3(0.0, FY - 0.22, 0.0)) / vec3(1.55, 0.45, 1.55);
    float e = length(q);
    float n = fbm(p * 2.3 + vec3(T * 0.01, 0.0, T * 0.006));
    return clamp((1.0 - e) * 2.2 + (n - 0.5) * 1.6, 0.0, 1.0);
}

void main() {
    T = GameTime * 1200.0;
    vec3 ro = eye;
    vec3 rd = normalize(lp - eye);
    vec3 inv = 1.0 / (sign(rd) * max(abs(rd), vec3(1e-6)));
    vec3 ta = (-HALF - ro) * inv, tb = (HALF - ro) * inv;
    float t0 = max(max(max(min(ta.x, tb.x), min(ta.y, tb.y)), min(ta.z, tb.z)), 0.0);
    float t1 = min(min(max(ta.x, tb.x), max(ta.y, tb.y)), max(ta.z, tb.z));
    if (t1 <= t0) {
        discard;
    }
    vec3 col = vec3(0.0);
    float a = 0.0, depthT = -1.0;
    float t = t0;
    if (skin == 5) {
        // (the cloud: only where it can be - a slab round its floor; the rest of the box is empty)
        vec3 lo = vec3(-1.6, FY - 0.72, -1.6), hi = vec3(1.6, FY + 0.32, 1.6);
        vec3 ca = (lo - ro) * inv, cb = (hi - ro) * inv;
        float c0 = max(max(max(min(ca.x, cb.x), min(ca.y, cb.y)), min(ca.z, cb.z)), t0);
        float c1 = min(min(min(max(ca.x, cb.x), max(ca.y, cb.y)), max(ca.z, cb.z)), t1);
        if (c1 <= c0) {
            discard;
        }
        t = c0;
        t1 = c1;
    }
    for (int i = 0; i < 140 && t < t1 && a < 0.97; i++) {
        vec3 p = ro + rd * t;
        if (skin == 5) {
            // the cloud: soft, marched in steps
            float d = cloud(p);
            if (d > 0.01) {
                float lit = 0.75 + 0.35 * clamp((p.y - (FY - 0.6)) / 0.6, 0.0, 1.0);
                vec3 c = mix(vec3(0.78, 0.82, 0.92), vec3(1.0, 1.0, 1.0), lit - 0.4);
                float k = clamp(d * 0.35, 0.0, 1.0);
                col += (1.0 - a) * c * lit * k;
                a += (1.0 - a) * k;
                if (depthT < 0.0 && a > 0.3) depthT = t;
            }
            t += 0.045;
            continue;
        }
        int mat;
        float s = solid(p, mat);
        float g = glass(p);
        vec3 glow = glowAt(p);
        if (s < 0.002) {
            vec3 n = solidNormal(p);
            vec3 c = solidColour(p, n, rd, mat);
            col += (1.0 - a) * c;
            a = 1.0;
            if (depthT < 0.0) depthT = t;
            break;
        }
        if (g < 0.002) {
            vec3 n = glassNormal(p);
            vec4 gc = glassColour(p, n, rd);
            col += (1.0 - a) * gc.rgb;
            a += (1.0 - a) * gc.a;
            if (depthT < 0.0 && gc.a > 0.02) depthT = t;
            t += 0.02;
            continue;
        }
        float dt = max(min(s, g), 0.003);
        if (skin == 1) {
            dt = min(dt, 0.12); // (its trail of light under the floor: sampled, not stepped over)
        }
        if (dot(glow, glow) > 0.0) {
            dt = min(dt, 0.05);
            col += (1.0 - a) * glow * dt * 3.0;
            if (depthT < 0.0 && dot(glow, glow) > 0.02) depthT = t;
        }
        t += dt;
    }
    float glowA = clamp(max(col.r, max(col.g, col.b)), 0.0, 1.0);
    a = max(a, skin == 1 ? glowA * 0.6 : 0.0);
    if (a < 0.01 || depthT < 0.0) {
        discard;
    }
    // (a hair nearer than it is: its floor's top is level with the base's and the landing's blocks - they'd show through it in rings)
    vec4 clip = ProjMat * ModelViewMat * vec4(centre + ro + rd * max(depthT - 0.012, 0.0), 1.0);
    gl_FragDepth = clamp(clip.z / clip.w * 0.5 + 0.5, 0.0, 1.0);
    fragColor = vec4(col / max(a, 0.001), a);
}
