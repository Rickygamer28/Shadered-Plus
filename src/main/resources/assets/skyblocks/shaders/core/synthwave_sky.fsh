#version 150

// Shadered+'s synthwave sky: a sunset in pink / purple / orange, a big striped sun sinking into the horizon (its
// stripes drift down, it pulses slowly), low-poly wireframe mountains in front of it, twinkling stars, and a neon grid
// floor rushing towards you from the sun.

uniform float Time; // ticks

in vec3 dir;

out vec4 fragColor;

const float PI = 3.14159265;
const vec3 SUN_DIR = vec3(-1.0, 0.0, 0.0); // the sun sets in the west
const vec3 SIDE = vec3(0.0, 0.0, 1.0);

const vec3 TOP = vec3(0.06, 0.01, 0.15);
const vec3 MID = vec3(0.42, 0.05, 0.45);
const vec3 HORIZON = vec3(1.0, 0.36, 0.45);
const vec3 PINK = vec3(1.0, 0.16, 0.62);
const vec3 ORANGE = vec3(1.0, 0.52, 0.12);
const vec3 YELLOW = vec3(1.0, 0.86, 0.24);
const vec3 WIRE = vec3(0.95, 0.35, 1.0);
const vec3 FLOOR = vec3(0.05, 0.0, 0.1);
const vec3 ROCK = vec3(0.07, 0.01, 0.13);

float hash11(float n) {
    return fract(sin(n * 12.9898) * 43758.5453);
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

// a low-poly ridge around the horizon: straight lines between random peaks, N segments all the way round
float ridge(float x, float n, float seed) {
    float i = floor(x);
    return mix(hash11(mod(i, n) + seed), hash11(mod(i + 1.0, n) + seed), fract(x));
}

// how much of a thin line through every whole number of v there is at this pixel (smooth, no shimmer)
float lines(float v, float width) {
    float aa = fwidth(v);
    float d = 0.5 - abs(fract(v) - 0.5);
    return (1.0 - smoothstep(width, width + aa * 1.5, d)) * (1.0 - smoothstep(0.3, 0.8, aa));
}

void main() {
    vec3 d = normalize(dir);
    float t = Time / 20.0; // seconds
    float elev = asin(clamp(d.y, -1.0, 1.0));
    float az = atan(dot(d, SIDE), dot(d, SUN_DIR)); // 0 towards the sun
    vec3 col;

    if (elev >= 0.0) {
        // ---- sky ----
        float h = elev / (PI * 0.5);
        col = mix(HORIZON, MID, smoothstep(0.0, 0.2, h));
        col = mix(col, TOP, smoothstep(0.15, 0.65, h));

        // stars, twinkling
        vec3 cell = floor(d * 170.0);
        float rnd = hash13(cell);
        if (rnd > 0.994) {
            vec3 centre = cell + 0.5 + (vec3(hash13(cell + 1.7), hash13(cell + 3.1), hash13(cell + 5.3)) - 0.5) * 0.5;
            float twinkle = 0.5 + 0.5 * sin(t * (1.5 + 3.0 * hash13(cell + 9.0)) + rnd * 300.0);
            float star = smoothstep(0.32, 0.0, length(d * 170.0 - centre));
            col += vec3(1.0, 0.85, 1.0) * star * twinkle * smoothstep(0.12, 0.4, h);
        }

        // the sun: glow, then the disc with stripes in its lower half drifting down
        float radius = 0.3 * (1.0 + 0.025 * sin(t * 1.3));
        vec2 sp = vec2(az, elev - 0.14);
        float sd = length(sp);
        col += PINK * 0.5 * exp(-max(sd - radius, 0.0) * 6.0);
        if (sd < radius + 0.01) {
            float up = sp.y / radius * 0.5 + 0.5; // 0 at the bottom of the disc, 1 at the top
            vec3 sun = mix(PINK, ORANGE, smoothstep(0.0, 0.55, up));
            sun = mix(sun, YELLOW, smoothstep(0.5, 1.0, up));
            float keep = 1.0;
            if (up < 0.6) {
                float band = fract(up * 9.0 + t * 0.35);
                float gap = mix(0.5, 0.06, up / 0.6); // wider gaps lower down
                keep = smoothstep(gap - 0.03, gap + 0.03, band);
            }
            float edge = 1.0 - smoothstep(radius - 0.004, radius + 0.004, sd);
            col = mix(col, sun * 1.15, keep * edge);
        }

        // a glow along the horizon
        col += PINK * 0.45 * exp(-elev * 40.0);

        // wireframe mountains (lower right in front of the sun, so it shows through the valley)
        float x = (az / (2.0 * PI) + 0.5) * 36.0;
        float peak = max(ridge(x, 36.0, 3.0) * 0.12, ridge(x * 2.0, 72.0, 41.0) * 0.07);
        peak *= mix(0.3, 1.0, smoothstep(0.08, 0.55, abs(az)));
        if (elev < peak) {
            float wire = max(lines(elev * 70.0, 0.04), lines(x * 2.0, 0.04)) * 0.55;
            float rim = 1.0 - smoothstep(0.0, fwidth(elev) * 2.5, peak - elev);
            col = ROCK + WIRE * max(wire * (0.4 + 0.6 * elev / max(peak, 0.001)), rim);
        }
    } else {
        // ---- the floor: a grid on a plane below you, moving towards you ----
        float down = -d.y;
        vec2 p = d.xz / down;
        vec2 g = vec2(dot(p, SUN_DIR.xz) + t * 2.5, dot(p, SIDE.xz));
        float grid = max(lines(g.x, 0.03), lines(g.y, 0.03));
        float near = 0.5 - max(abs(fract(g.x) - 0.5), abs(fract(g.y) - 0.5));
        col = FLOOR + PINK * 1.3 * grid + PINK * 0.12 * (1.0 - smoothstep(0.0, 0.2, near)) * (1.0 - smoothstep(0.3, 0.9, fwidth(g.x)));
        // the sun's reflection and the haze far away
        float haze = exp(-down * 14.0);
        col += ORANGE * 0.3 * exp(-abs(az) * 5.0) * haze;
        col = mix(col, HORIZON * 0.9, haze * 0.8);
        col += PINK * 0.45 * exp(-down * 40.0);
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
