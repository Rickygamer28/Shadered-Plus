#version 150

// A phase block's face: the block it looks like (cut out where its picture's see-through), lit; its own panel's light
// strips glowing. As it opens it goes away, as it's set to; shutting, it comes back the same way, backwards:
// 0 burning - holes eaten through it, glowing orange-to-white edges, embers inside them
// 1 pixels  - square by square, each flaring cyan before it goes
// 2 scan    - a green scan line wiping down it, a grid glowing just behind the line
// 3 hologram- turning to a flickering blue hologram, scan lines rolling up it, fading out
// 4 ripple  - a hole opening from its middle in rings, a magenta wave at its edge

uniform sampler2D Sampler0;
uniform float GameTime;

in vec2 uv;
in vec2 face;
in vec4 info;
flat in int mode;
flat in int own;
flat in float seed;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += noise(p) * a;
        p = p * 2.03 + 17.1;
        a *= 0.5;
    }
    return v;
}

float bayer(vec2 p) {
    ivec2 q = ivec2(mod(p, 4.0));
    int m[16] = int[16](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);
    return (float(m[q.y * 4 + q.x]) + 0.5) / 16.0;
}

void main() {
    vec4 tex = texture(Sampler0, uv);
    if (tex.a < 0.1) discard;
    float gone = info.a;
    float t = GameTime * 1200.0;
    vec2 s = vec2(seed * 3.7, seed * 1.3);
    vec3 col = tex.rgb * info.rgb;
    if (own == 1) {
        // (its own panel's light strips: the brightest cyan of its picture)
        float strip = smoothstep(0.75, 0.95, tex.g) * smoothstep(0.6, 0.9, tex.b);
        col += vec3(0.25, 0.9, 1.0) * strip * (0.55 + 0.25 * sin(t * 2.0 + s.x));
    }
    if (gone > 0.0) {
        if (mode == 1) {
            vec2 cell = floor(face * 8.0);
            float r = hash(cell + s) * 0.9 + 0.05;
            float e = r - gone;
            if (e < 0.0) discard;
            float flare = 1.0 - smoothstep(0.0, 0.08, e);
            vec2 inCell = fract(face * 8.0);
            float rim = step(0.85, max(abs(inCell.x - 0.5), abs(inCell.y - 0.5)) * 2.0);
            col = mix(col, vec3(0.3, 0.95, 1.0) * 1.8, flare * (0.6 + 0.4 * rim));
        } else if (mode == 2) {
            // (down its face: the line where it's wiped to)
            float at = gone * 1.1 - 0.05;
            float e = face.y - at;
            if (e < 0.0) discard;
            float line = 1.0 - smoothstep(0.0, 0.035, e);
            vec2 g = abs(fract(face * 8.0) - 0.5);
            float grid = step(0.44, max(g.x, g.y)) * (1.0 - smoothstep(0.0, 0.25, e));
            col = mix(col, vec3(0.3, 1.0, 0.5) * 0.8, grid * 0.7);
            col = mix(col, vec3(0.7, 1.0, 0.8) * 1.8, line);
        } else if (mode == 3) {
            float lines = 0.5 + 0.5 * sin(face.y * 60.0 - t * 8.0);
            float flicker = 0.85 + 0.15 * sin(t * 37.0 + s.x) * sin(t * 23.0);
            vec3 holo = vec3(0.25, 0.65, 1.0) * (0.6 + 0.4 * dot(tex.rgb, vec3(0.33))) * (0.7 + 0.5 * lines) * flicker;
            col = mix(col, holo * 1.4, smoothstep(0.0, 0.35, gone));
            // (fading: fewer and fewer of its pixels on screen)
            if (bayer(gl_FragCoord.xy) < smoothstep(0.3, 1.0, gone) * (0.85 + 0.15 * lines)) discard;
        } else if (mode == 4) {
            float r = length(face - 0.5) * 1.42;
            float wob = 0.04 * sin(atan(face.y - 0.5, face.x - 0.5) * 6.0 + t * 3.0);
            float at = gone * 1.15 - 0.08;
            float e = r + wob - at;
            if (e < 0.0) discard;
            float wave = 1.0 - smoothstep(0.0, 0.07, e);
            float rings = (0.5 + 0.5 * sin((r - at) * 60.0)) * (1.0 - smoothstep(0.0, 0.25, e));
            col = mix(col, vec3(1.0, 0.3, 0.9) * 0.8, rings * 0.5);
            col = mix(col, vec3(1.0, 0.75, 1.0) * 1.7, wave);
        } else {
            float n = clamp((fbm(face * 3.0 + s) - 0.2) / 0.6, 0.0, 1.0);
            float edgeAt = gone * 1.12 - 0.06;
            if (n < edgeAt) discard;
            float e = n - edgeAt;
            float burn = 1.0 - smoothstep(0.0, 0.07, e);
            float ember = (1.0 - smoothstep(0.0, 0.2, e)) * 0.5;
            vec3 fire = mix(vec3(1.0, 0.35, 0.05), vec3(1.0, 0.95, 0.7), burn * burn);
            col = mix(col, col * vec3(0.5, 0.3, 0.25), ember);
            col = mix(col, fire * 1.6, burn);
        }
    }
    fragColor = vec4(col, 1.0);
}
