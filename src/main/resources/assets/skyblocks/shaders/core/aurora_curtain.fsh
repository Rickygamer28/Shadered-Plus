#version 150

// An aurora curtain: light hanging in folds - brightest along its lower edge, fading away upwards - made of fine
// rays that drift and flicker, ripples of brightness running along it. Its colours: the top's fading into the
// bottom's. Drawn adding light (it glows; you see through it).

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
    float len = data.a * 16.0;
    float x = uv.x, y = uv.y;
    float fromBottom = len - y;
    float rays = pow(fbm(vec3(x * 2.2, t * 0.15, 0.0)) * 1.6, 2.2);
    rays *= 0.35 + 0.65 * pow(noise(vec3(x * 16.0, y * 0.25 - t * 0.5, t * 0.2)), 1.5) * 1.6;
    float edge = smoothstep(0.0, 0.12, fromBottom) * (0.18 + 0.82 * exp(-fromBottom * 1.3)) + 0.6 * exp(-fromBottom * 6.0);
    float top = smoothstep(0.0, min(1.2, len * 0.4), y);
    float ripple = 0.75 + 0.25 * sin(x * 1.7 - t * 1.2 + noise(vec3(x * 0.5, t * 0.1, 3.0)) * 4.0);
    float a = clamp(rays * edge * top * ripple * 1.4, 0.0, 1.0);
    vec3 c = data.rgb * (0.8 + 0.6 * exp(-fromBottom * 2.5));
    fragColor = vec4(c, a);
}
