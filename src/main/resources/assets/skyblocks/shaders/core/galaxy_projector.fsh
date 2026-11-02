#version 150

// Shadered+'s galaxy projector (our own code): the light it throws on the room. At each pixel, where the world already
// drawn there is (from the depth: Sampler0), the direction from the projector to it, and the sky that way, slowly
// turning - a galaxy (glowing nebulae, a band of stars, a bright core), a starry night (with now and then a shooting
// star), or the light of the sea (rippling caustics). Only on surfaces turned towards it, not where a block stands in
// between (Sampler1: the solid blocks round it), fading out at the edge of its reach. Added on top.

uniform sampler2D Sampler0; // depth
uniform sampler2D Sampler1; // the solid blocks round it: x + 17 z across, y up
uniform mat4 InvViewProj;
uniform vec3 Center;   // the projector's lens, relative to the camera
uniform vec3 GridMin;  // the block map's corner, relative to the camera
uniform float Range;
uniform float Mode;
uniform float Strength;
uniform float GalTime;

out vec4 fragColor;

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float vnoise3(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x), mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x), mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm3(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) {
        v += a * vnoise3(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

// stars on the sky: points in cells round the sphere of directions (cells scale per unit), how bright
vec3 stars(vec3 d, float scale, float density, float t) {
    vec3 g = d * scale;
    vec3 base = floor(g - 0.5);
    vec3 col = vec3(0.0);
    for (int z = 0; z <= 1; z++) {
        for (int y = 0; y <= 1; y++) {
            for (int x = 0; x <= 1; x++) {
                vec3 c = base + vec3(x, y, z);
                float h = hash13(c);
                if (h > density) continue;
                vec3 sp = c + vec3(hash13(c + 7.1), hash13(c + 3.3), hash13(c + 5.9));
                vec3 dir = normalize(sp);
                float ang = length(cross(d, dir)) * scale; // (how far, in cells)
                float b = hash13(c + 11.0);
                float tw = 0.7 + 0.3 * sin(t * (1.5 + 3.0 * b) + h * 40.0);
                float r = 0.05 + 0.07 * b;
                float s = smoothstep(r, 0.0, ang) * tw * (0.5 + 1.2 * b * b);
                vec3 tint = b > 0.85 ? vec3(1.0, 0.75, 0.6) : b > 0.7 ? vec3(0.7, 0.8, 1.0) : vec3(1.0);
                col += tint * s;
            }
        }
    }
    return col;
}

float caustic(vec2 p, float t) {
    vec2 q = p;
    float c = 0.0;
    for (int k = 0; k < 2; k++) {
        q += vec2(sin(q.y * 1.3 + t * 0.7), cos(q.x * 1.1 - t * 0.6)) * 0.6;
        c += 0.5 / (1.0 + 8.0 * abs(sin(q.x) * sin(q.y)));
    }
    return clamp(c - 0.3, 0.0, 1.0);
}

vec3 rotY(vec3 d, float a) {
    float c = cos(a), s = sin(a);
    return vec3(c * d.x - s * d.z, d.y, s * d.x + c * d.z);
}

vec3 sky(vec3 d, float t) {
    int mode = int(Mode + 0.5);
    if (mode == 2) { // the sea's light: the bright web of caustics, drifting, blue
        vec3 a = abs(d);
        a /= a.x + a.y + a.z;
        float c = caustic(d.xz * 5.0, t) * a.y + caustic(d.xy * 5.0 + 3.0, t) * a.z + caustic(d.zy * 5.0 + 7.0, t) * a.x;
        return vec3(0.02, 0.1, 0.25) + vec3(0.3, 0.8, 1.0) * c * c * 1.3;
    }
    vec3 r = rotY(d, t * 0.035);
    if (mode == 1) { // a starry night: deep blue, many stars, now and then a shooting star
        vec3 col = vec3(0.02, 0.035, 0.11) * (0.7 + 0.6 * fbm3(r * 2.0));
        col += stars(r, 22.0, 0.45, t) * 1.3 + stars(r, 50.0, 0.35, t) * 0.6;
        float slot = floor(t / 7.0);
        float ph = fract(t / 7.0);
        if (hash13(vec3(slot, 1.0, 2.0)) > 0.4 && ph < 0.12) {
            vec3 a = normalize(vec3(hash13(vec3(slot, 3, 1)) - 0.5, 0.25 + 0.4 * hash13(vec3(slot, 4, 1)), hash13(vec3(slot, 5, 1)) - 0.5));
            vec3 b = normalize(cross(a, vec3(0.0, 1.0, 0.0)));
            vec3 head = normalize(a + b * (ph / 0.12 - 0.5) * 0.6);
            vec3 tail = normalize(head - b * 0.15);
            vec3 pa = d - tail, ba = head - tail;
            float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
            float dist = length(pa - ba * h);
            col += vec3(0.9, 0.95, 1.0) * smoothstep(0.012, 0.0, dist) * h * (1.0 - ph / 0.12) * 2.0;
        }
        return col;
    }
    // a galaxy: nebulae in a band, a bright core, stars
    vec3 bandN = normalize(vec3(0.25, 1.0, 0.35));
    float lat = dot(r, bandN);
    float band = exp(-lat * lat / 0.08);
    float n = fbm3(r * 2.4 + vec3(0.0, t * 0.01, 0.0));
    float n2 = fbm3(r * 5.0 + 4.0);
    float neb = smoothstep(0.38, 0.85, n) * (0.35 + 0.9 * band);
    vec3 col = mix(vec3(0.35, 0.08, 0.6), vec3(0.08, 0.25, 0.75), smoothstep(0.3, 0.7, n2));
    col = mix(col, vec3(0.85, 0.2, 0.55), smoothstep(0.62, 0.8, n2) * 0.7);
    col *= neb * 0.9;
    col -= vec3(0.15) * smoothstep(0.55, 0.75, fbm3(r * 7.0 + 9.0)) * band; // (dark dust lanes)
    col = max(col, vec3(0.0));
    vec3 coreDir = normalize(cross(bandN, vec3(1.0, 0.0, 0.0)));
    col += vec3(1.0, 0.85, 0.6) * pow(max(dot(r, coreDir), 0.0), 18.0) * band * 0.8; // (the core)
    col += stars(r, 24.0, 0.32 + 0.25 * band, t) * 0.9 + stars(r, 50.0, 0.25, t) * 0.45;
    return col;
}

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    vec2 size = vec2(textureSize(Sampler0, 0));
    float dep = texelFetch(Sampler0, px, 0).r;
    if (dep >= 1.0) {
        discard;
    }
    vec4 w = InvViewProj * vec4(gl_FragCoord.xy / size * 2.0 - 1.0, dep * 2.0 - 1.0, 1.0);
    vec3 P = w.xyz / w.w;
    vec3 rel = P - Center;
    float dist = length(rel);
    if (dist > Range || dist < 0.3) {
        discard;
    }
    vec3 dir = rel / dist;
    // turned towards it? (the surface's normal from the neighbouring pixels, towards the camera)
    vec3 n = cross(dFdx(P), dFdy(P));
    float nl = length(n);
    if (nl < 1e-9) {
        discard;
    }
    n /= nl;
    if (dot(n, P) > 0.0) n = -n;
    float facing = dot(n, -dir);
    if (facing <= 0.02) {
        discard;
    }
    // a block in between: no light
    float lit = 1.0;
    vec3 from = P + n * 0.04;
    vec3 to = Center;
    float len = length(to - from);
    int steps = int(min(len / 0.2, 48.0));
    for (int i = 1; i < 48; i++) {
        if (i >= steps) break;
        vec3 q = from + (to - from) * (float(i) / float(steps));
        if (length(q - Center) < 0.45) break;
        ivec3 c = ivec3(floor(q - GridMin));
        if (any(lessThan(c, ivec3(0))) || any(greaterThanEqual(c, ivec3(17)))) continue;
        if (texelFetch(Sampler1, ivec2(c.x + 17 * c.z, c.y), 0).r > 0.5) {
            lit = 0.0;
            break;
        }
    }
    if (lit <= 0.0) {
        discard;
    }
    float fade = 1.0 - smoothstep(Range * 0.6, Range, dist);
    float near = clamp(2.4 / (dist + 0.8), 0.35, 1.6);
    vec3 col = sky(dir, GalTime) * (0.35 + 0.65 * facing) * fade * near * Strength * 2.2;
    fragColor = vec4(col, 0.0);
}
