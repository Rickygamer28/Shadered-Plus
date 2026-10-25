#version 150

// Shadered+'s wall fire (our own code): behind glass, a row of white stones and a long ribbon of flame dancing over
// them - nearly white at the base, the flame's colour above, dark tips; taller with the music's beat.

in vec2 uv;
in vec4 vColor;

uniform float FireTime;
uniform float Boost;

out vec4 fragColor;

vec2 hash22(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash22(i).x, hash22(i + vec2(1.0, 0.0)).x, f.x), mix(hash22(i + vec2(0.0, 1.0)).x, hash22(i + vec2(1.0, 1.0)).x, f.x), f.y);
}

float fbm(vec2 p) {
    return 0.5 * vnoise(p) + 0.3 * vnoise(p * 2.03 + 7.1) + 0.2 * vnoise(p * 4.1 + 3.3);
}

void main() {
    float s = uv.x;
    float h = (uv.y - 4.5 / 16.0) / (7.5 / 16.0); // 0 at the bottom of the opening .. 1 at the top
    float on = vColor.a > 0.5 ? 1.0 : 0.0;
    vec3 fc = vColor.rgb;
    float t = FireTime;
    vec3 col = vec3(0.02, 0.018, 0.022);

    float stoneTop = 0.16;
    float fh = h - stoneTop * 0.6;
    // the warm glow on the back wall
    col += fc * 0.3 * on * exp(-max(fh, 0.0) * 2.2) * (0.85 + 0.15 * sin(t * 7.0 + s * 3.0));

    // the stones: a row of rounded white pebbles
    if (h < stoneTop + 0.02) {
        float row = s * 7.0;
        vec2 id = vec2(floor(row), 0.0);
        float x = fract(row) - 0.5 + (hash22(id).x - 0.5) * 0.25;
        float r = 0.32 + 0.12 * hash22(id + 3.0).x;
        float y = (h - stoneTop * 0.45) / (stoneTop * 0.9);
        float d = length(vec2(x, y * 0.9)) / r;
        if (d < 1.0) {
            float lit = 0.5 + 0.5 * clamp(y + 0.6, 0.0, 1.0);
            vec3 stone = mix(vec3(0.78, 0.77, 0.75), vec3(0.95, 0.94, 0.92), hash22(id + 7.0).y) * (0.55 + 0.45 * sqrt(1.0 - d * d));
            col = stone * lit * (0.35 + 0.65 * (0.3 + 0.7 * on)) + fc * 0.35 * on * clamp(y + 0.3, 0.0, 1.0);
        } else if (h < stoneTop * 0.4) {
            col = vec3(0.12, 0.11, 0.1); // (between the stones, at the back)
        }
    }

    // the flame
    if (on > 0.5 && fh > -0.05) {
        float n = fbm(vec2(s * 4.0, fh * 2.6 - t * 2.0));
        float n2 = fbm(vec2(s * 10.0 + 3.0, fh * 5.5 - t * 3.4));
        float tongues = 0.6 + 0.22 * sin(s * 7.0 + t * 1.3 + n * 3.0) + 0.18 * sin(s * 19.0 - t * 2.3 + n2 * 2.0);
        float height = (0.55 + 0.4 * Boost) * tongues;
        float f = clamp((height - fh) / height, 0.0, 1.0);
        float fire = smoothstep(0.18, 0.55, f + (n - 0.5) * 0.9 + (n2 - 0.5) * 0.45);
        float core = smoothstep(0.62, 0.95, f + (n - 0.5) * 0.35);
        vec3 hot = mix(fc + 0.35, vec3(1.0, 0.97, 0.86), 0.65);
        vec3 fireCol = mix(fc * mix(0.55, 1.25, f), hot, core);
        col = mix(col, fireCol * 1.35, fire * (h > stoneTop * 0.35 ? 1.0 : 0.6));
    }

    // the glass: a faint slanted reflection
    col += vec3(0.07) * smoothstep(0.06, 0.0, abs(fract(s * 0.35 + uv.y * 0.8) - 0.5) - 0.2);
    fragColor = vec4(col, 1.0);
}
