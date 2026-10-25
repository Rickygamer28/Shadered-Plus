#version 150

// Shadered+'s wall fire (our own code): behind glass, a row of white stones and a long ribbon of flame dancing over
// them - nearly white at the base, the flame's colour above, dark tips; taller with the music's beat.

in vec2 uvIn;
vec2 uv;     // (uvIn, without the free-flames flag)
float cut;   // free flames (the hearth): no back, no bed, see-through round the flames
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

// one flame layer: 0..1 how much fire, and how hot (the core)
vec2 flame(float s, float fh, float t, float seed) {
    float n = fbm(vec2(s * 4.0 + seed, fh * 2.6 - t * 2.0));
    float n2 = fbm(vec2(s * 10.0 + 3.0 + seed, fh * 5.5 - t * 3.4));
    float tongues = 0.6 + 0.22 * sin(s * 7.0 + t * 1.3 + n * 3.0 + seed) + 0.18 * sin(s * 19.0 - t * 2.3 + n2 * 2.0);
    float height = (0.62 + 0.38 * Boost) * tongues;
    float f = clamp((height - fh) / height, 0.0, 1.0);
    float fire = smoothstep(0.18, 0.55, f + (n - 0.5) * 0.9 + (n2 - 0.5) * 0.45);
    float core = smoothstep(0.62, 0.95, f + (n - 0.5) * 0.35);
    return vec2(fire, core * fire) * step(-0.05, fh);
}

void main() {
    cut = uvIn.y > 5.0 ? 1.0 : 0.0;
    uv = vec2(uvIn.x, uvIn.y - 10.0 * cut);
    float s = uv.x;
    float h = (uv.y - 4.3 / 16.0) / (7.4 / 16.0); // 0 at the bottom of the opening .. 1 at the top
    float on = vColor.a > 0.5 ? 1.0 : 0.0;
    vec3 fc = vColor.rgb;
    float t = FireTime;
    float flick = 0.85 + 0.15 * sin(t * 7.0 + s * 3.0);

    // the back: black glass, a soft mirror of the flames further back, and the warm glow
    vec3 col = mix(vec3(0.012, 0.012, 0.016), vec3(0.03, 0.03, 0.036), h);
    float bedTop = 0.2;
    float fh = h - bedTop * 0.7;
    col += fc * 0.28 * on * exp(-max(fh, 0.0) * 2.4) * flick;
    if (on > 0.5) {
        vec2 back = flame(s * 0.93 + 0.37, fh * 1.08 + 0.04, t * 0.9 + 4.0, 11.0);
        col += mix(fc * 0.7, vec3(1.0, 0.85, 0.6), back.y) * back.x * 0.22;
    }

    // the flames, in front of the back
    if (on > 0.5) {
        vec2 fr = flame(s, fh, t, 0.0);
        vec3 hot = mix(fc + 0.35, vec3(1.0, 0.97, 0.86), 0.65);
        vec3 fireCol = mix(fc * mix(0.6, 1.25, clamp(1.0 - fh, 0.0, 1.0)), hot, fr.y / max(fr.x, 0.001));
        col = mix(col, fireCol * 1.35, fr.x);
    }

    // the bed: chunks of fire glass in front of the flames' base - facets catching the light
    if (cut < 0.5 && h < bedTop + 0.04) {
        vec2 q = vec2(s * 15.0, h * 15.0 * 7.4 / 16.0 * 1.5);
        vec2 id = floor(q), fq = fract(q);
        float d1 = 9.0, d2 = 9.0;
        vec2 cell = vec2(0.0);
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                vec2 o = vec2(i, j);
                vec2 pt = o + hash22(id + o) * 0.8 + 0.1;
                float d = length(fq - pt);
                if (d < d1) { d2 = d1; d1 = d; cell = id + o; }
                else if (d < d2) { d2 = d; }
            }
        }
        float top = bedTop * (0.75 + 0.3 * hash22(vec2(floor(s * 4.5), 3.0)).x);
        if (h < top) {
            vec2 rh = hash22(cell + 5.0);
            float edge = smoothstep(0.08, 0.0, d2 - d1);            // (the facet edges)
            float facet = 0.4 + 0.6 * rh.x;                           // (each facet turned its own way)
            float glow = on * (0.35 + 0.65 * clamp(h / top, 0.0, 1.0)) * flick;
            vec3 glassCol = mix(vec3(0.05, 0.055, 0.07), mix(fc, vec3(1.0), 0.15), 0.1 + 0.35 * rh.y * rh.y);
            vec3 bed = glassCol * facet * (0.35 + 0.65 * glow) + fc * glow * 0.22 * rh.x * rh.x;
            bed += (mix(fc, vec3(1.0), 0.5) * on + 0.1) * edge * (0.15 + 0.5 * rh.y) * flick;
            float spark = step(0.985, hash22(cell + floor(t * 3.0 + rh.x * 7.0)).x) * on;
            bed += mix(fc, vec3(1.0), 0.7) * spark * 0.7;
            col = bed;
        }
    }

    // the glass: a faint slanted reflection, a little brighter towards the top
    if (cut > 0.5) {
        float a = clamp(max(col.r, max(col.g, col.b)) * 2.4 - 0.75, 0.0, 1.0);
        if (a < 0.02) {
            discard;
        }
        fragColor = vec4(col, a);
        return;
    }
    col += vec3(0.06 + 0.03 * h) * smoothstep(0.06, 0.0, abs(fract(s * 0.35 + uv.y * 0.8) - 0.5) - 0.2);
    fragColor = vec4(col, 1.0);
}
