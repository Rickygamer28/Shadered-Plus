#version 150

// The track: a bed of grey gravel (stones of their own, shaded between), concrete sleepers (pitted, their edges worn),
// rails rusty brown down their sides with their heads polished bright by wheels (the sky and sun shining in them),
// dark steel plates and clips, and a cyan strip glowing between the rails - a pulse running along it. Lit by its block
// light and the sky's (SkyLight: how bright the sky is now).

uniform float GameTime;
uniform float SkyLight;

in vec3 wp;
in vec3 n;
in vec3 vdir;
in vec2 uv;
flat in int mat;
in float blockL;
in float skyL;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec2 hash22(vec2 p) {
    return fract(sin(vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}

// stones: how far to the nearest stone's edge, and which stone
vec2 stones(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    float d1 = 8.0, d2 = 8.0, id = 0.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(x, y);
            vec2 o = hash22(i + g);
            float d = length(g + o - f);
            if (d < d1) {
                d2 = d1;
                d1 = d;
                id = hash(i + g);
            } else if (d < d2) {
                d2 = d;
            }
        }
    }
    return vec2(d2 - d1, id);
}

void main() {
    float T = GameTime * 1200.0;
    vec3 N = normalize(n);
    vec3 V = normalize(vdir);
    vec3 L = normalize(vec3(0.35, 1.0, 0.25));
    float light = clamp(max(blockL, skyL * (SkyLight - 0.2) / 0.8), 0.06, 1.0);
    float dif = max(dot(N, L), 0.0);
    float amb = 0.45 + 0.25 * N.y;
    float lightK = (amb + dif * 0.7) * light;
    vec3 c;
    if (mat == 5) {
        vec2 st = stones(wp.xz * 16.0 + wp.y * 5.0);
        float edge = smoothstep(0.0, 0.18, st.x);
        vec3 stone = mix(vec3(0.33, 0.32, 0.31), vec3(0.55, 0.53, 0.5), st.y);
        stone = mix(stone, vec3(0.45, 0.38, 0.3), step(0.85, st.y) * 0.6);
        c = stone * (0.35 + 0.65 * edge) * lightK;
    } else if (mat == 0) {
        float pit = noise(wp.xz * 40.0 + wp.y * 17.0);
        c = vec3(0.6, 0.6, 0.58) * (0.85 + 0.15 * pit) * (0.92 + 0.08 * noise(wp.xz * 4.0));
        c *= lightK;
    } else if (mat == 1 || mat == 4) {
        vec3 h = normalize(L - V);
        if (mat == 1) {
            // a polished head: bright steel, the sky in it
            float wear = 0.8 + 0.2 * noise(vec2(uv.y * 3.0, uv.x * 4.0));
            vec3 base = vec3(0.72, 0.74, 0.78) * wear;
            vec3 r = reflect(V, N);
            vec3 sky = mix(vec3(0.5, 0.55, 0.5), vec3(0.6, 0.72, 0.9), clamp(r.y * 2.0 + 0.5, 0.0, 1.0));
            float fres = 0.3 + 0.7 * pow(1.0 - max(dot(N, -V), 0.0), 4.0);
            c = base * lightK * 0.6 + sky * fres * 0.6 * light + vec3(1.0, 0.95, 0.85) * pow(max(dot(N, h), 0.0), 60.0) * dif * light;
        } else {
            // its sides: rust, darker in streaks
            float rust = noise(vec2(uv.y * 6.0, wp.y * 60.0));
            c = mix(vec3(0.32, 0.2, 0.13), vec3(0.42, 0.26, 0.15), rust) * lightK;
            c += vec3(0.08) * pow(max(dot(N, h), 0.0), 20.0) * dif * light;
        }
    } else if (mat == 6) {
        c = vec3(0.16, 0.16, 0.17) * lightK;
        vec3 h = normalize(L - V);
        c += vec3(0.15) * pow(max(dot(N, h), 0.0), 30.0) * dif * light;
    } else {
        float pulse = 0.7 + 0.3 * sin(uv.y * 1.5 - T * 4.0);
        float edge = smoothstep(0.0, 0.3, min(uv.x, 1.0 - uv.x));
        c = vec3(0.25, 0.9, 1.0) * pulse * (mat == 3 ? 0.22 : 1.15) * (0.6 + 0.4 * edge);
    }
    fragColor = vec4(c, 1.0);
}
