#version 150

// A planet on a globe stand: traced as a ball (and its rings, a flat disc round it) in the world's directions, lit
// from over your shoulder, so you see its day side and the dusk line round its edge. It turns on its tilted axis; each look has its own ground:
// 0 a blue world with green and brown land, ice caps, clouds and city lights at night; 1 a gas giant, banded, with a
// great storm; 2 ice; 3 lava, cracked and glowing; 4 a red desert; 5 an alien world, glowing teal and violet;
// 6 a grey cratered moon; 7 a world under thick yellow clouds. A thin bright atmosphere at its edge.

uniform mat4 ModelViewMat;
uniform float GameTime;

in vec2 uv;
in vec3 viewPos;
in vec4 data;

out vec4 fragColor;

float hash(vec3 p) {
    return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453);
}

float hash2(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x), mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x), mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

float fbm(vec3 p) {
    float v = 0.0, a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec3(3.1, 1.7, 5.3);
        a *= 0.5;
    }
    return v;
}

mat3 rotY(float a) {
    float c = cos(a), s = sin(a);
    return mat3(c, 0.0, -s, 0.0, 1.0, 0.0, s, 0.0, c);
}

mat3 rotX(float a) {
    float c = cos(a), s = sin(a);
    return mat3(1.0, 0.0, 0.0, 0.0, c, s, 0.0, -s, c);
}

const float R = 0.48;

vec3 ground(int look, vec3 p, vec3 pc, float lat, float t, float seed, out float clouds, out float lights) {
    clouds = 0.0;
    lights = 0.0;
    vec3 s = p + vec3(seed * 7.3);
    if (look == 0) {
        float land = fbm(s * 2.2);
        vec3 c = land > 0.52 ? mix(vec3(0.16, 0.42, 0.14), vec3(0.5, 0.42, 0.25), smoothstep(0.55, 0.7, land))
                             : mix(vec3(0.02, 0.08, 0.3), vec3(0.05, 0.25, 0.5), smoothstep(0.35, 0.52, land));
        c = mix(c, vec3(0.92, 0.95, 1.0), smoothstep(0.72, 0.8, abs(lat) + (fbm(s * 5.0) - 0.5) * 0.15));
        clouds = smoothstep(0.5, 0.75, fbm(pc * 3.0 + vec3(seed)));
        lights = land > 0.52 ? smoothstep(0.62, 0.78, noise(s * 30.0)) : 0.0;
        return c;
    }
    if (look == 1) {
        float b = lat * 7.0 + (fbm(s * vec3(2.0, 0.6, 2.0)) - 0.5) * 2.5;
        vec3 c = mix(vec3(0.78, 0.6, 0.42), vec3(0.95, 0.88, 0.75), 0.5 + 0.5 * sin(b * 3.0));
        c = mix(c, vec3(0.55, 0.32, 0.2), smoothstep(0.6, 0.9, sin(b * 1.3 + 1.0)) * 0.6);
        vec3 spot = normalize(vec3(0.6, -0.35, 0.72));
        float sp = smoothstep(0.32, 0.2, length(p - spot * dot(p, spot)) + (1.0 - dot(p, spot)) * 2.0);
        return mix(c, vec3(0.75, 0.3, 0.18), sp);
    }
    if (look == 2) {
        float f = fbm(s * 3.0);
        vec3 c = mix(vec3(0.62, 0.78, 0.9), vec3(0.95, 0.98, 1.0), f);
        c = mix(c, vec3(0.3, 0.55, 0.75), smoothstep(0.02, 0.0, abs(noise(s * 6.0) - 0.5)) * 0.7);
        return c;
    }
    if (look == 3) {
        float f = fbm(s * 3.0);
        float crack = smoothstep(0.06, 0.0, abs(noise(s * 4.0) - 0.5)) + smoothstep(0.04, 0.0, abs(noise(s * 9.0 + 2.0) - 0.5)) * 0.6;
        lights = crack * (0.7 + 0.3 * sin(t * 2.0 + f * 10.0));
        return mix(vec3(0.08, 0.05, 0.05), vec3(0.22, 0.12, 0.1), f);
    }
    if (look == 4) {
        float f = fbm(s * 2.5);
        vec3 c = mix(vec3(0.55, 0.2, 0.08), vec3(0.85, 0.5, 0.3), f);
        c = mix(c, vec3(0.35, 0.12, 0.06), smoothstep(0.6, 0.7, fbm(s * 5.0)));
        return mix(c, vec3(0.95, 0.9, 0.88), smoothstep(0.82, 0.88, abs(lat)));
    }
    if (look == 5) {
        float f = fbm(s * 2.8);
        vec3 c = mix(vec3(0.12, 0.04, 0.22), vec3(0.3, 0.1, 0.45), f);
        float veins = smoothstep(0.05, 0.0, abs(noise(s * 5.0) - 0.5));
        lights = veins + smoothstep(0.7, 0.8, f) * 0.5;
        clouds = smoothstep(0.62, 0.8, fbm(pc * 3.5 + vec3(seed))) * 0.5;
        return c;
    }
    if (look == 6) {
        float f = fbm(s * 3.0);
        vec3 c = vec3(0.45 + 0.2 * f);
        for (int i = 0; i < 3; i++) {
            float k = float(i);
            vec3 q = s * (4.0 + k * 3.0);
            vec3 cell = floor(q);
            vec3 o = vec3(hash(cell), hash(cell + 1.3), hash(cell + 2.7));
            float d = length(fract(q) - o);
            float r = 0.25 + 0.2 * hash(cell + 5.0);
            c *= 1.0 - 0.25 * smoothstep(r, r * 0.7, d) + 0.15 * smoothstep(r * 0.9, r, d) * step(d, r * 1.1);
        }
        return c;
    }
    float f = fbm(s * 2.0 + vec3(t * 0.05, 0.0, 0.0));
    clouds = 0.85;
    return mix(vec3(0.5, 0.45, 0.1), vec3(0.9, 0.85, 0.35), f);
}

void main() {
    float t = GameTime * 1200.0;
    int look = int(data.r * 255.0 + 0.5);
    bool rings = data.g > 0.5;
    float seed = data.b * 255.0;
    mat3 toWorld = transpose(mat3(ModelViewMat));
    vec2 q = uv;
    // (nothing out past the halo, or the rings: let go at once)
    float reach = rings ? R * 2.05 : R * 1.25;
    if (dot(q, q) > reach * reach) discard;
    vec3 o = toWorld * vec3(q, 3.0);
    vec3 d = toWorld * vec3(0.0, 0.0, -1.0);
    vec3 L = toWorld * normalize(vec3(-0.5, 0.45, 0.75)); // (the light: from over your shoulder, up to the left)
    float tilt = 0.4;
    mat3 axis = rotX(tilt);
    vec3 N = axis * vec3(0.0, 1.0, 0.0);
    float q2 = dot(q, q);
    vec4 planet = vec4(0.0);
    float tPlanet = 1e9;
    if (q2 < R * R) {
        float z = sqrt(R * R - q2);
        tPlanet = 3.0 - z;
        vec3 n = normalize(toWorld * vec3(q, z));
        // (the ground: turned with the planet)
        vec3 local = transpose(axis) * n;
        vec3 p = rotY(t * 0.08) * local;
        vec3 pc = rotY(t * 0.11) * local;
        float lat = local.y;
        float clouds, lights;
        vec3 c = ground(look, p, pc, lat, t, seed, clouds, lights);
        float lit = max(dot(n, L), 0.0);
        float dark = smoothstep(0.15, -0.1, dot(n, L));
        vec3 shade = c * (0.06 + 0.94 * lit);
        if (look == 0) shade += vec3(1.0, 0.8, 0.45) * lights * dark * 0.9;
        if (look == 3) shade += vec3(1.0, 0.35, 0.05) * lights * 1.4;
        if (look == 5) shade += mix(vec3(0.2, 0.9, 0.8), vec3(0.8, 0.3, 1.0), clouds) * lights * 0.8;
        // (clouds over it, their shadow faint)
        vec3 cloudCol = look == 7 ? vec3(0.95, 0.9, 0.55) : vec3(1.0);
        shade = mix(shade, cloudCol * (0.06 + 0.94 * lit), clouds * 0.9);
        // (the atmosphere: bright at the lit edge)
        float rim = pow(1.0 - z / R, 3.0);
        vec3 air = look == 3 ? vec3(1.0, 0.4, 0.1) : look == 4 ? vec3(1.0, 0.6, 0.4) : look == 5 ? vec3(0.6, 0.3, 1.0)
                : look == 6 ? vec3(0.0) : look == 7 ? vec3(0.9, 0.85, 0.4) : vec3(0.4, 0.7, 1.0);
        shade += air * rim * (0.15 + 0.85 * smoothstep(-0.2, 0.5, dot(n, L))) * 0.9;
        planet = vec4(shade, 1.0);
    }
    vec4 ring = vec4(0.0);
    float tRing = 1e9;
    if (rings) {
        float dn = dot(d, N);
        if (abs(dn) > 1e-4) {
            float tr = -dot(o, N) / dn;
            vec3 pr = o + tr * d;
            float r = length(pr);
            if (r > R * 1.3 && r < R * 2.05) {
                float k = (r - R * 1.3) / (R * 0.75);
                float bands = 0.55 + 0.45 * sin(k * 40.0 + seed) * sin(k * 13.0 + 1.0);
                float a = bands * smoothstep(0.0, 0.08, k) * smoothstep(1.0, 0.9, k) * (1.0 - 0.6 * smoothstep(0.55, 0.6, k) * smoothstep(0.66, 0.6, k));
                vec3 rc = mix(vec3(0.8, 0.72, 0.6), vec3(0.95, 0.92, 0.85), hash2(vec2(floor(k * 60.0), seed)));
                if (look == 2) rc = vec3(0.75, 0.88, 1.0);
                if (look == 5) rc = vec3(0.6, 0.45, 0.95);
                // (the planet's shadow across it)
                float along = dot(pr, L);
                float sh = along < 0.0 && length(pr - along * L) < R ? 0.15 : 1.0;
                ring = vec4(rc * (0.25 + 0.75 * sh) * (0.5 + 0.5 * abs(dot(N, L))), a * 0.85);
                tRing = tr;
            }
        }
    }
    vec4 col;
    if (tRing < tPlanet) {
        col = vec4(mix(planet.rgb, ring.rgb, ring.a), max(planet.a, ring.a));
    } else if (planet.a > 0.0) {
        col = planet;
    } else {
        col = ring;
    }
    // (a faint halo round it)
    if (col.a < 0.99 && look != 6) {
        float r = sqrt(q2);
        float halo = smoothstep(R * 1.25, R, r) * step(R, r) * 0.35;
        vec3 air = look == 3 ? vec3(1.0, 0.4, 0.1) : look == 5 ? vec3(0.6, 0.3, 1.0) : vec3(0.4, 0.7, 1.0);
        col = vec4(mix(air, col.rgb, col.a), max(col.a, halo));
    }
    if (col.a < 0.01) discard;
    fragColor = col;
}
