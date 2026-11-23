#version 150

// The Constellation Board: a deep night (blue to violet, a faint nebula, tiny stars twinkling) and on it the stars
// you've placed - bright, with little spikes, twinkling, each its own tint - joined by soft glowing lines, a light
// running along each. A picked star (the next one placed is joined to it) has a ring pulsing round it.

uniform float GameTime;

in vec2 uv;
in vec3 viewPos;
in vec4 data;

out vec4 fragColor;

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec3(3.1, 1.7, 5.3);
        a *= 0.5;
    }
    return v;
}

void main() {
    float t = GameTime * 1200.0;
    int what = int(data.r * 255.0 + 0.5);
    float seed = data.b * 255.0;
    if (what == 0) {
        vec2 p = uv;
        vec3 c = mix(vec3(0.01, 0.015, 0.05), vec3(0.06, 0.03, 0.12), clamp(fbm(vec3(p * 0.6, 1.0)), 0.0, 1.0));
        float neb = fbm(vec3(p * 1.4, 4.0));
        c += mix(vec3(0.15, 0.05, 0.3), vec3(0.05, 0.2, 0.35), fbm(vec3(p * 0.8, 9.0))) * smoothstep(0.5, 0.8, neb) * 0.5;
        for (int i = 0; i < 2; i++) {
            float k = 24.0 + float(i) * 22.0;
            vec2 cell = floor(p * k);
            float h = hash2(cell + float(i) * 17.0);
            if (h > 0.9) {
                vec2 f = fract(p * k) - 0.5 - (vec2(hash2(cell + 3.0), hash2(cell + 5.0)) - 0.5) * 0.6;
                float tw = 0.6 + 0.4 * sin(t * (1.5 + h * 3.0) + h * 40.0);
                c += vec3(0.8, 0.85, 1.0) * smoothstep(0.12, 0.0, length(f)) * tw * (h - 0.9) * 8.0;
            }
        }
        fragColor = vec4(c, 1.0);
        return;
    }
    if (what == 1) {
        float r = length(uv);
        float tw = 0.8 + 0.2 * sin(t * (2.0 + fract(seed * 0.37) * 3.0) + seed);
        vec3 tint = mix(vec3(0.75, 0.85, 1.0), vec3(1.0, 0.9, 0.7), fract(seed * 0.618));
        float core = smoothstep(0.26, 0.0, r);
        float glow = exp(-r * 3.5) * 0.9;
        float spikes = (smoothstep(0.08, 0.0, abs(uv.x)) + smoothstep(0.08, 0.0, abs(uv.y))) * smoothstep(1.0, 0.1, r) * 0.6;
        float a = clamp(core + glow * tw + spikes * tw, 0.0, 1.0);
        vec3 c = mix(tint, vec3(1.0), core) * (0.7 + 0.5 * data.g);
        if (data.g > 0.9) {
            float ring = smoothstep(0.08, 0.0, abs(r - 0.75 - 0.08 * sin(t * 4.0)));
            c = mix(c, vec3(0.5, 1.0, 0.95), ring);
            a = max(a, ring);
        }
        fragColor = vec4(c, a);
        return;
    }
    float across = abs(uv.y);
    float pulse = pow(fract(uv.x * 0.8 - t * 0.35 + seed * 0.1), 6.0);
    float line = smoothstep(1.0, 0.0, across);
    float a = (line * line * 0.55 + smoothstep(0.35, 0.0, across) * 0.5) * (0.7 + 0.6 * pulse);
    fragColor = vec4(mix(vec3(0.55, 0.7, 1.0), vec3(1.0), pulse), clamp(a, 0.0, 1.0));
}
