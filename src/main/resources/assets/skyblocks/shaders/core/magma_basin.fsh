#version 150

// Shadered+'s magma basin (our own code): slowly churning lava. Plates of dark cooling crust drift on the molten
// lava, pulling apart and pushing together; between them cracks glow yellow-white in the middle, orange to red at the
// edges - seen a little below the crust (they move with the view). Bubbles swell in the cracks and pop. The colour
// of the lava can be dyed (Color); the plates are always basalt-dark.

in vec2 wpos;
in vec3 wray;
in vec4 vColor;

uniform float MagmaTime;

out vec4 fragColor;

vec2 hash22(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(41.3, 289.1))) * 45758.5453);
}

float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash12(i), hash12(i + vec2(1, 0)), f.x), mix(hash12(i + vec2(0, 1)), hash12(i + vec2(1, 1)), f.x), f.y);
}

float fbm(vec2 p) {
    return 0.55 * vnoise(p) + 0.3 * vnoise(p * 2.1 + 3.7) + 0.15 * vnoise(p * 4.3 + 9.1);
}

// the crust plates: (distance to the crack, the plate's id, its drift)
vec3 plates(vec2 p, float t) {
    vec2 i = floor(p), f = fract(p);
    float d1 = 9.0, d2 = 9.0;
    vec2 id1 = vec2(0.0);
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(x, y);
            vec2 h = hash22(i + g);
            vec2 c = g + 0.5 + 0.38 * sin(t * (0.07 + 0.05 * h) + 6.2831 * h); // (each plate wanders)
            float d = length(c - f);
            if (d < d1) { d2 = d1; d1 = d; id1 = i + g; }
            else if (d < d2) { d2 = d; }
        }
    }
    return vec3(d2 - d1, hash12(id1), hash12(id1 + 17.0));
}

vec3 molten(float heat, vec3 tint) {
    // dark red -> the lava's colour -> yellow-white, by heat
    vec3 deep = tint * vec3(0.45, 0.12, 0.08);
    vec3 hot = mix(tint, vec3(1.0, 0.92, 0.55), 0.55);
    return heat < 0.5 ? mix(deep, tint * 1.1, heat * 2.0) : mix(tint * 1.1, hot, heat * 2.0 - 1.0);
}

void main() {
    float t = MagmaTime;
    vec3 rd = normalize(wray);
    vec3 tint = vColor.rgb;
    // the whole surface creeps slowly, with a gentle swirl
    vec2 flow = vec2(t * 0.018, t * 0.011) + vec2(fbm(wpos * 0.6 + t * 0.03), fbm(wpos * 0.6 - t * 0.025 + 5.0)) * 0.35;
    vec2 p = wpos * 2.3 + flow;
    vec3 pl = plates(p, t);
    // the cracks: wider where the plates pull apart (slowly, plate by plate), with ragged edges
    float open = 0.10 + 0.22 * smoothstep(0.15, 0.85, vnoise(p * 0.7 + t * 0.05)) + 0.06 * (pl.z - 0.5);
    float rag = (fbm(p * 5.0) - 0.5) * 0.07;
    float edge = pl.x + rag;
    float crack = 1.0 - smoothstep(open * 0.35, open, edge);

    // seen a little below: the molten lava under the crust shifts with the view (depth)
    vec2 under = wpos + rd.xz / max(-rd.y, 0.25) * 0.035;
    vec2 pu = under * 2.3 + flow;
    float swirl = fbm(pu * 2.3 + vec2(t * 0.12, -t * 0.09));
    float heat = clamp(0.35 + 0.65 * (1.0 - smoothstep(0.0, open, plates(pu, t).x + rag)) + (swirl - 0.5) * 0.5, 0.0, 1.0);
    float pulse = 0.85 + 0.15 * sin(t * 1.7 + swirl * 6.0); // (it breathes)
    vec3 lava = molten(heat * pulse, tint) * (1.0 + 0.6 * heat);

    // the crust: dark basalt, a faint warm glow near the cracks and where it is thin, fine grain
    float grain = fbm(p * 9.0 + pl.y * 10.0);
    float thin = smoothstep(0.55, 0.15, pl.y + 0.3 * grain - 0.2) * 0.45; // (some plates are thinner: glowing through)
    vec3 crust = vec3(0.075, 0.065, 0.065) * (0.7 + 0.6 * grain) + tint * (0.25 * exp(-edge * 18.0) + thin * 0.35);

    vec3 col = mix(crust, lava, crack);

    // bubbles: some cracks swell into a bright dome and pop
    vec2 bc = floor(p * 0.8);
    vec2 bh = hash22(bc);
    float life = fract(t * (0.12 + 0.1 * bh.x) + bh.y);
    vec2 bp = (bc + 0.25 + 0.5 * bh) / 0.8;
    float br = 0.13 * smoothstep(0.0, 0.85, life) * step(life, 0.92);
    float bd = length(p - bp);
    if (bh.x > 0.55 && bd < br) {
        float dome = sqrt(1.0 - bd * bd / (br * br));
        col = mix(col, molten(0.75 + 0.25 * dome, tint) * (1.2 + 0.5 * dome), 0.9);
    } else if (bh.x > 0.55 && life > 0.92) { // (just popped: a bright ring spreading out)
        float ring = exp(-pow((bd - (life - 0.92) * 3.0) / 0.02, 2.0)) * (1.0 - (life - 0.92) / 0.08);
        col += molten(0.9, tint) * ring;
    }
    fragColor = vec4(col, 1.0);
}
