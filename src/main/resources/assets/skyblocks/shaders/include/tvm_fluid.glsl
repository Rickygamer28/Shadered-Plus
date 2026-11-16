// The Valley Mod's Colored Fluid (TVM, by D0nkey100 - used with permission: its shaders/core/colored_fluid.fsh, the
// styles as they are): coloured liquids flowing and mixing on a background - warped 3D noise round the sky. 0 ink swirl,
// 1 lava lamp blobs, 2 marble. Here each style is its own sky with its own colours (TVM lets you pick them per block).

vec3 fluidLiquid[6];
int fluidCount;

float fluidHash(vec3 p) {
    p = fract(p * 0.3183099 + 0.1);
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

float fluidNoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(fluidHash(i + vec3(0, 0, 0)), fluidHash(i + vec3(1, 0, 0)), f.x),
                   mix(fluidHash(i + vec3(0, 1, 0)), fluidHash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(fluidHash(i + vec3(0, 0, 1)), fluidHash(i + vec3(1, 0, 1)), f.x),
                   mix(fluidHash(i + vec3(0, 1, 1)), fluidHash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fluidFbm(vec3 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * fluidNoise(p);
        p = p * 2.02 + vec3(1.7, 9.2, 4.1);
        a *= 0.5;
    }
    return v;
}

// A value 0..1 spread over the liquids: neighbouring liquids blend softly where they meet.
vec3 fluidBlend(float v) {
    float x = clamp(v, 0.0, 0.9999) * float(fluidCount);
    int i = int(floor(x));
    float f = smoothstep(0.3, 0.7, fract(x));
    return mix(fluidLiquid[i], fluidLiquid[int(mod(float(i + 1), float(fluidCount)))], f);
}

vec3 coloredFluid(vec3 dir, int style, vec3 background, float t, float seedValue) {
    vec3 seed = vec3(fract(sin(seedValue * 12.9898) * 43758.5453), fract(sin(seedValue * 78.233) * 12345.678),
                     fract(sin(seedValue * 37.719) * 24634.634)) * 100.0;
    vec3 p = normalize(dir) * 2.2 + seed;
    vec3 color;
    if (style == 1) {
        // Lava lamp: round soft blobs of each liquid rising slowly, melting into each other on the background.
        float total = 0.0;
        vec3 mixed = vec3(0.0);
        for (int i = 0; i < 6; i++) {
            if (i >= fluidCount) break;
            float fi = float(i);
            float v = fluidNoise(p * 1.6 + vec3(fi * 7.31, -t * (0.18 + fi * 0.03), fi * 3.17));
            float blob = smoothstep(0.58, 0.72, v);
            total += blob;
            mixed += fluidLiquid[i] * blob;
        }
        color = mix(background, mixed / max(total, 1e-4), clamp(total, 0.0, 1.0));
    } else if (style == 2) {
        // Marble: bands of the liquids, bent and dragged along by slow turbulence.
        float warp = fluidFbm(p * 1.3 + vec3(0.0, t * 0.08, t * 0.05));
        float band = sin((p.x + p.y * 0.6 + p.z * 0.3) * 2.0 + warp * 7.0) * 0.5 + 0.5;
        float veins = smoothstep(0.02, 0.0, abs(sin((p.y + warp * 5.0) * 3.0)) - 0.02);
        color = mix(fluidBlend(fract(band + warp * 0.6)), background, veins * 0.8);
    } else {
        // Ink swirl: the liquids twisted together by layered, moving turbulence; thin spots show the background.
        vec3 q = vec3(fluidFbm(p + vec3(0.0, 0.0, t * 0.12)), fluidFbm(p + vec3(5.2, 1.3, -t * 0.10)), fluidFbm(p + vec3(1.7, 9.2, t * 0.08)));
        float r = fluidFbm(p + 3.0 * q + vec3(t * 0.05));
        float density = smoothstep(0.32, 0.5, fluidFbm(p * 1.4 + 2.0 * q - vec3(t * 0.07)));
        color = mix(background, fluidBlend(fract(r * 3.0 + q.y * 2.0)), density);
    }
    return color;
}
