#version 150

// Shadered+'s waterfall: streaks of water racing down, white where they break, foam where it lands.

in vec2 uv;
in vec4 col;

uniform float Time;
uniform float Length; // blocks it falls

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p *= 2.03;
        a *= 0.5;
    }
    return v;
}

void main() {
    if (col.a > 0.9) { // the foam: a churning ring where it lands
        vec2 p = uv * 2.0 - 1.0;
        float r = length(p);
        if (r > 1.0) discard;
        float n = fbm(vec2(atan(p.y, p.x) * 2.0, r * 5.0 - Time * 1.6));
        vec3 c = mix(vec3(0.62, 0.84, 0.95), vec3(1.0), smoothstep(0.35, 0.75, n));
        fragColor = vec4(c * col.rgb, (1.0 - r * r) * (0.45 + 0.5 * n));
        return;
    }
    float x = uv.x, y = uv.y;
    float streak = fbm(vec2(x * 6.0, y * 0.8 - Time * 2.4));
    float fine = noise(vec2(x * 28.0, y * 2.5 - Time * 5.5));
    vec3 deep = vec3(0.10, 0.34, 0.58), light = vec3(0.50, 0.80, 0.95);
    vec3 c = mix(deep, light, streak);
    float white = smoothstep(0.58, 0.82, streak * 0.75 + fine * 0.35);
    c = mix(c, vec3(0.96), white);
    // breaking up at its sides, white with spray where it lands, smooth over the lip at the top
    float edge = smoothstep(0.0, 0.14 + 0.06 * noise(vec2(y * 3.0 - Time * 3.0, 1.0)), x) * smoothstep(1.0, 0.86, x);
    float foam = smoothstep(Length - 0.7, Length, y);
    c = mix(c, vec3(1.0), foam * 0.75);
    float a = (0.5 + 0.35 * streak + white * 0.25 + foam * 0.3) * edge;
    fragColor = vec4(c * col.rgb, clamp(a, 0.0, 0.95));
}
