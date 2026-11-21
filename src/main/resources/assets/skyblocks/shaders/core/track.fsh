#version 150

// The track: rough concrete sleepers, bright steel rails (a shine along their tops), a cyan strip glowing between them -
// a pulse running along it. Lit by its block light and the sky's (SkyLight: how bright the sky is now).

uniform float GameTime;
uniform float SkyLight;

in vec3 wp;
in vec3 n;
in vec2 uv;
flat in int mat;
in float blockL;
in float skyL;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    float T = GameTime * 1200.0;
    vec3 N = normalize(n);
    float light = max(blockL, skyL * (SkyLight - 0.2) / 0.8);
    light = clamp(light, 0.08, 1.0);
    float sun = 0.6 + 0.4 * max(dot(N, normalize(vec3(0.35, 1.0, 0.25))), 0.0);
    vec3 c;
    if (mat == 0) {
        float g = hash(floor(wp.xz * 16.0)) * 0.08;
        c = vec3(0.5, 0.51, 0.53) + g;
        c *= sun * light;
    } else if (mat == 1) {
        float top = smoothstep(0.6, 0.95, N.y);
        c = mix(vec3(0.45, 0.47, 0.5), vec3(0.85, 0.88, 0.92), top);
        c *= sun * light;
        c += vec3(0.25) * top * light * pow(0.5 + 0.5 * sin(uv.y * 0.8), 6.0);
    } else {
        float pulse = 0.7 + 0.3 * sin(uv.y * 1.5 - T * 4.0);
        c = vec3(0.3, 0.92, 1.0) * pulse * (mat == 3 ? 0.25 : 1.1);
    }
    fragColor = vec4(c, 1.0);
}
