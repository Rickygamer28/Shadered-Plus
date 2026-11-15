#version 150

// The moon in the jar: a ball (its surface grey and pale, darker seas, a few craters), the sunlit part of it as in its
// phase (full at 0, new at 4), slowly turning; round it a soft halo of moonlight.

uniform float GameTime;

in vec2 q;
flat in float phase;
flat in float bright;

out vec4 fragColor;

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

void main() {
    const float R = 0.55;
    float r = length(q);
    float halo = exp(-max(r - R, 0.0) * 7.0) * 0.55 * smoothstep(1.0, 0.75, r);
    if (r >= R) {
        fragColor = vec4(vec3(0.75, 0.82, 1.0) * halo * bright, halo * bright);
        return;
    }
    vec3 n = vec3(q / R, sqrt(max(0.0, 1.0 - dot(q, q) / (R * R))));
    // (turning slowly)
    float ticks = GameTime * 24000.0;
    float a = ticks * 0.004;
    vec3 s = vec3(n.x * cos(a) + n.z * sin(a), n.y, -n.x * sin(a) + n.z * cos(a));
    float seas = smoothstep(0.45, 0.62, noise(s * 2.3 + 3.0));
    float grain = noise(s * 9.0) * 0.12;
    float craters = 0.0;
    for (int i = 0; i < 7; i++) {
        vec3 c = normalize(vec3(hash(vec3(i, 1, 2)) - 0.5, hash(vec3(i, 3, 4)) - 0.5, hash(vec3(i, 5, 6)) - 0.5));
        float d = length(s - c);
        float size = 0.08 + 0.12 * hash(vec3(i, 7, 8));
        craters += smoothstep(size, size * 0.6, d) * 0.18 - smoothstep(size * 1.15, size, d) * smoothstep(size * 0.9, size * 1.15, d) * 0.12;
    }
    vec3 surface = vec3(0.86, 0.87, 0.9) * (1.0 - 0.32 * seas - craters + grain);
    // the phase: the sun's side of it lit (0 full, 2 half, 4 new, 6 half the other side)
    float p = phase / 8.0 * 6.2831853;
    vec3 sunDir = normalize(vec3(sin(p), 0.0, cos(p)));
    float day = smoothstep(-0.08, 0.12, dot(n, sunDir));
    vec3 col = surface * (0.06 + 0.94 * day) * (0.9 + 0.1 * n.z);
    // (earthshine on the dark part, and the halo over it all)
    col += vec3(0.05, 0.06, 0.09) * (1.0 - day);
    col += vec3(0.75, 0.82, 1.0) * halo * 0.4;
    fragColor = vec4(col * bright, 1.0);
}
