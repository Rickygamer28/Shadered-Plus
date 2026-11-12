#version 150

// The sky clock's face: the sky as it is now. The sun and moon go round on an arc about the middle (noon at the top,
// midnight at the bottom - hidden by the hills); the sky behind them blue by day, gold and pink round sunrise and
// sunset, deep blue with twinkling stars by night, grey in the rain; rolling hills across the bottom; a brass rim, the
// hours marked, and a hand pointing at the sun.

in vec2 uv;
in vec4 sky;

uniform float GameTime;

out vec4 fragColor;

const float PI = 3.14159265;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float segment(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}

void main() {
    vec2 p = vec2(uv.x * 2.0 - 1.0, 1.0 - uv.y * 2.0);
    float r = length(p);
    if (r > 1.0) {
        discard;
    }
    float angle = sky.r * 2.0 * PI;
    int phase = int(sky.g * 8.0 + 0.5) % 8;
    float rain = sky.b, thunder = sky.a;
    vec2 sunDir = vec2(-sin(angle), cos(angle));
    vec2 sun = sunDir * 0.6, moon = -sun;
    float elev = cos(angle);
    float day = smoothstep(-0.12, 0.3, elev);
    float dusk = exp(-pow(elev / 0.2, 2.0));

    // the sky: dark overhead to pale at the horizon; sunrise and sunset colours low down, on the sun's side most
    float horizon = -0.08;
    float h = clamp((p.y - horizon) / (1.0 - horizon), 0.0, 1.0);
    vec3 top = mix(vec3(0.01, 0.02, 0.07), vec3(0.24, 0.5, 0.92), day);
    vec3 low = mix(vec3(0.05, 0.08, 0.18), vec3(0.66, 0.82, 0.98), day);
    float sunSide = 0.5 + 0.5 * dot(normalize(vec2(p.x, 0.3)), normalize(vec2(sunDir.x, 0.3)));
    low = mix(low, mix(vec3(0.95, 0.42, 0.28), vec3(1.0, 0.72, 0.35), sunSide), dusk * (0.55 + 0.45 * sunSide));
    top = mix(top, vec3(0.32, 0.26, 0.52), dusk * 0.5);
    vec3 c = mix(low, top, pow(h, 0.65));
    // stars, by night, twinkling
    vec2 cell = floor(p * 26.0);
    float star = step(0.975, hash(cell));
    float twinkle = 0.55 + 0.45 * sin(GameTime * 2400.0 * (0.5 + hash(cell + 3.0)) + hash(cell) * 40.0);
    vec2 inCell = fract(p * 26.0) - 0.5;
    c += vec3(0.95, 0.95, 1.0) * star * twinkle * smoothstep(0.22, 0.0, length(inCell)) * (1.0 - day) * (1.0 - rain);
    // the sun: a glow round it, its disc
    float ds = length(p - sun);
    c += vec3(1.0, 0.78, 0.45) * exp(-ds * 7.0) * 0.55 * max(day, dusk) * (1.0 - rain * 0.7);
    c = mix(c, vec3(1.0, 0.96, 0.82), smoothstep(0.1, 0.085, ds) * (1.0 - rain * 0.6));
    // the moon, in its phase (a shadow disc slid across it: 0 full .. 4 new)
    float dm = length(p - moon);
    float waning = phase < 4 ? 1.0 : -1.0;
    float dark = float(4 - abs(phase - 4));
    vec2 shadow = moon + vec2(waning * 0.17 * (1.0 - dark / 4.0), 0.0);
    float lit = smoothstep(0.08, 0.07, dm) * smoothstep(0.07, 0.08, length(p - shadow));
    c = mix(c, vec3(0.92, 0.94, 0.86), lit * (1.0 - day * 0.6) * (1.0 - rain * 0.5));
    c += vec3(0.5, 0.55, 0.7) * exp(-dm * 9.0) * 0.18 * (1.0 - day);
    // rain: greyed over and dimmer; thunder flickers it
    vec3 grey = vec3(dot(c, vec3(0.3, 0.55, 0.15))) * vec3(0.85, 0.9, 1.0);
    c = mix(c, grey * 0.75, rain * 0.75);
    c += vec3(0.6, 0.65, 0.8) * thunder * step(0.985, hash(vec2(floor(GameTime * 24000.0 / 3.0), 1.0))) * 0.6;
    // the hills
    float hill = horizon + 0.07 * sin(p.x * 5.0 + 1.3) + 0.045 * sin(p.x * 11.0 + 0.4) - 0.05;
    if (p.y < hill) {
        vec3 ground = mix(vec3(0.02, 0.05, 0.04), vec3(0.2, 0.44, 0.17), day);
        ground = mix(ground, vec3(0.32, 0.22, 0.16), dusk * 0.35);
        ground *= 1.0 - rain * 0.35;
        float far = smoothstep(hill - 0.25, hill, p.y);
        c = mix(ground * 0.75, ground, far);
    }
    // the hand, on the sun (and a pin in the middle)
    float hand = segment(p, vec2(0.0), sunDir * 0.7);
    c = mix(c, vec3(0.86, 0.68, 0.3), smoothstep(0.03, 0.018, hand));
    c = mix(c, vec3(0.62, 0.46, 0.2), smoothstep(0.07, 0.055, r));
    // the hours (noon at the top), and the brass rim
    float a = atan(p.x, p.y);
    float hour = abs(fract(a / (2.0 * PI) * 12.0 + 0.5) - 0.5) * 2.0 * PI / 12.0 * r;
    float tick = smoothstep(0.022, 0.012, hour) * step(0.8, r) * step(r, 0.9);
    c = mix(c, vec3(0.9, 0.74, 0.38), tick);
    float rim = smoothstep(0.9, 0.92, r);
    vec3 brass = mix(vec3(0.62, 0.45, 0.2), vec3(0.95, 0.8, 0.45), 0.5 + 0.5 * (p.y * 0.7 - p.x * 0.3));
    c = mix(c, brass, rim);
    fragColor = vec4(c, 1.0);
}
