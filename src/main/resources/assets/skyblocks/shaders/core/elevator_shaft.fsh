#version 150

// The elevator's shaft. Its walls, for:
//  the bubble pod - glowing water, light rippling through it, bubbles rising (faster as the pod rises);
//  the warp cabin - deep space, faint nebulae, stars that streak into lines the faster the cabin goes;
//  the cloud      - open sky, clouds drifting by (and sliding past as the cloud rises or sinks).
// The glass capsule's rings: bands of light round the shaft every two blocks, dashed, turning, brighter near the
// capsule and as it speeds. The crystal cabin's rail: a strip of crystal diamonds up the shaft, glinting.
// Rings and rail are light, added (alpha next to nothing, as shaders/core/spin_aura's).

uniform float GameTime;

in vec2 at;
flat in int skin;
flat in int mode;
flat in float vel;
flat in float near;

out vec4 fragColor;

const float TAU = 6.2831853;

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1, 0)), f.x), mix(hash2(i + vec2(0, 1)), hash2(i + vec2(1, 1)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec2(3.1, 1.7);
        a *= 0.5;
    }
    return v;
}

vec3 water(vec2 p, float t) {
    vec3 col = mix(vec3(0.02, 0.12, 0.25), vec3(0.05, 0.3, 0.45), 0.5 + 0.5 * sin(p.y * 0.3));
    // light rippling through it
    float c = 0.0;
    vec2 q = p * 1.6;
    for (int i = 0; i < 3; i++) {
        q += vec2(sin(q.y * 1.3 + t * 0.03 + float(i)), cos(q.x * 1.1 - t * 0.025));
        c += 1.0 / (1.0 + 18.0 * abs(sin(q.x + q.y)));
    }
    col += vec3(0.25, 0.65, 0.85) * c * 0.35;
    // bubbles rising
    for (int k = 0; k < 2; k++) {
        float sc = 3.0 + float(k) * 2.5;
        vec2 b = vec2(p.x * sc, p.y * sc - t * (0.05 + 0.03 * float(k)) * sc - vel * 0.0);
        vec2 id = floor(b), f = fract(b) - 0.5;
        float h = hash2(id + float(k) * 13.0);
        if (h > 0.55) {
            vec2 o = vec2(hash2(id + 3.1) - 0.5, hash2(id + 7.7) - 0.5) * 0.5;
            float r = 0.12 + 0.12 * hash2(id + 1.3);
            float d = length(f - o);
            col += vec3(0.6, 0.85, 1.0) * smoothstep(0.03, 0.0, abs(d - r)) * 0.8;
            col += vec3(1.0) * smoothstep(0.05, 0.0, length(f - o + vec2(r * 0.4, -r * 0.4))) * 0.5;
        }
    }
    return col;
}

vec3 space(vec2 p, float t) {
    vec3 col = vec3(0.012, 0.008, 0.035);
    float neb = fbm(p * 0.25 + vec2(4.0, 0.0));
    col += vec3(0.25, 0.08, 0.35) * smoothstep(0.45, 0.85, neb) * 0.6 + vec3(0.05, 0.12, 0.3) * smoothstep(0.5, 0.9, fbm(p * 0.18 + 9.0)) * 0.5;
    // stars, streaking along the shaft the faster it goes
    float streak = 0.02 + abs(vel) * 0.9;
    for (int k = 0; k < 3; k++) {
        float sc = 5.0 + float(k) * 4.0;
        vec2 g = p * sc;
        vec2 id = floor(g), f = fract(g);
        float h = hash2(id + float(k) * 31.0);
        if (h > 0.8) {
            vec2 c = vec2(hash2(id + 2.3), hash2(id + 5.1));
            float dx = abs(f.x - c.x) * sc;
            float dy = (f.y - c.y) * sc / max(streak * sc, 0.06);
            float s = smoothstep(0.08, 0.0, dx) * smoothstep(1.0, 0.0, abs(dy));
            vec3 tint = mix(vec3(0.8, 0.85, 1.0), mix(vec3(1.0, 0.75, 0.9), vec3(0.6, 1.0, 1.0), h * 5.0 - 4.0), 0.5);
            col += tint * s * (0.6 + 0.4 * sin(t * 0.1 + h * 50.0));
        }
    }
    return col;
}

vec3 sky(vec2 p, float t) {
    vec3 col = mix(vec3(0.62, 0.78, 0.98), vec3(0.32, 0.56, 0.92), clamp(0.5 + 0.02 * p.y, 0.0, 1.0));
    float c = fbm(vec2(p.x * 0.35 + t * 0.004, p.y * 0.5));
    float cl = smoothstep(0.48, 0.7, c);
    vec3 cloudCol = mix(vec3(0.82, 0.86, 0.94), vec3(1.0), smoothstep(0.5, 0.85, fbm(vec2(p.x * 0.7 + t * 0.004, p.y * 0.9 + 0.3))));
    return mix(col, cloudCol, cl);
}

void main() {
    float t = GameTime * 1200.0;
    vec3 col;
    if (mode == 0) {
        col = skin == 2 ? water(at, t) : skin == 3 ? space(at, t) : sky(at, t);
        fragColor = vec4(col, 1.0);
        return;
    }
    if (mode == 1) {
        // a ring round the shaft
        float r = max(abs(at.x), abs(at.y));
        float round_ = length(at);
        float d = abs(round_ - 0.93);
        float ang = atan(at.y, at.x);
        float dash = step(0.35, fract(ang / TAU * 24.0 + t * (0.004 + abs(vel) * 0.05)));
        float strength = (0.25 + 0.75 * near) * (0.6 + 1.6 * abs(vel));
        col = vec3(0.35, 0.75, 1.0) * (smoothstep(0.03, 0.0, d) * dash + exp(-d * 30.0) * 0.25) * strength;
        if (r > 1.0) col = vec3(0.0);
    } else {
        // the rail: crystal diamonds up it
        float y = at.y * 2.0;
        vec2 q = vec2(abs(at.x), abs(fract(y) - 0.5) * 2.0);
        float diamond = q.x + q.y;
        float glint = 0.5 + 0.5 * sin(at.y * 1.7 - t * (0.05 + abs(vel) * 0.6));
        col = vec3(0.7, 0.4, 1.0) * smoothstep(1.0, 0.85, diamond) * (0.35 + 0.65 * glint);
        col += vec3(1.0, 0.85, 1.0) * smoothstep(0.98, 0.9, diamond) * smoothstep(0.75, 0.9, diamond) * 0.6;
    }
    if (max(col.r, max(col.g, col.b)) < 0.003) {
        discard;
    }
    fragColor = vec4(col, 1.0 / 255.0);
}
