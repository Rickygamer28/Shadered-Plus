#version 150

// A phase block's face: its panel, lit, its lights softly pulsing; burning away as it opens - holes that grow from
// all over it (a noise pattern, its own on each face) with glowing orange-to-white edges, embers just inside them;
// all of it gone at the end. Shutting, the same backwards: it forms from the edges in.

uniform sampler2D Sampler0;
uniform float GameTime;

in vec2 uv;
in vec4 info;

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

void main() {
    float gone = info.a;
    vec2 seed = vec2(info.g * 255.0 * 3.7, info.b * 255.0 * 1.3);
    // (0 .. 1: where on the face burns first - low first)
    float n = clamp((fbm(uv * 3.0 + seed) - 0.2) / 0.6, 0.0, 1.0);
    float edgeAt = gone * 1.12 - 0.06;
    if (gone > 0.0 && n < edgeAt) {
        discard;
    }
    vec4 tex = texture(Sampler0, uv);
    float t = GameTime * 1200.0;
    vec3 col = tex.rgb * info.r;
    // its light strips (the texture's brightest cyan) glow, pulsing gently
    float strip = smoothstep(0.75, 0.95, tex.g) * smoothstep(0.6, 0.9, tex.b);
    col += vec3(0.25, 0.9, 1.0) * strip * (0.55 + 0.25 * sin(t * 2.0 + seed.x));
    if (gone > 0.0) {
        float e = n - edgeAt;
        float burn = 1.0 - smoothstep(0.0, 0.07, e);
        float ember = (1.0 - smoothstep(0.0, 0.2, e)) * 0.5;
        vec3 fire = mix(vec3(1.0, 0.35, 0.05), vec3(1.0, 0.95, 0.7), burn * burn);
        col = mix(col, col * vec3(0.5, 0.3, 0.25), ember);
        col = mix(col, fire * 1.6, burn);
    }
    fragColor = vec4(col, 1.0);
}
