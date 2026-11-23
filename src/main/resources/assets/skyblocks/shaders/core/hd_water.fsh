#version 150

// HD water: on the surface, small waves running over each other (their normals from the heights round each point),
// clear blue-green deep down, the sky reflected in it more the flatter you look (Fresnel), a glint of the sun, bright
// caustic ripples, and foam where the streams land. On a stream: a falling rope of water, rounded, streaked white
// as it rushes down, glinting along its edge.

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
    for (int i = 0; i < 5; i++) {
        v += a * noise(p);
        p = p * 2.03 + vec3(3.1, 1.7, 5.3);
        a *= 0.5;
    }
    return v;
}

float waves(vec2 p, float t) {
    float h = 0.0;
    h += sin(dot(p, vec2(0.8, 0.6)) * 6.0 + t * 2.1) * 0.020;
    h += sin(dot(p, vec2(-0.5, 0.9)) * 9.0 + t * 2.7) * 0.012;
    h += sin(dot(p, vec2(0.95, -0.3)) * 14.0 + t * 3.6) * 0.006;
    h += (noise(vec3(p * 5.0, t * 0.6)) - 0.5) * 0.03;
    // (rings spreading out from the middle, where the streams fall)
    vec2 c = fract(p) - 0.5;
    float r = length(c);
    h += sin(r * 40.0 - t * 6.0) * 0.006 * exp(-r * 4.0);
    return h;
}

vec3 skyCol(vec3 r, float day) {
    vec3 zenith = mix(vec3(0.02, 0.03, 0.08), vec3(0.25, 0.5, 0.95), day);
    vec3 horizon = mix(vec3(0.05, 0.07, 0.14), vec3(0.75, 0.86, 1.0), day);
    vec3 c = mix(horizon, zenith, clamp(r.y * 1.4, 0.0, 1.0));
    vec3 sun = normalize(vec3(0.45, 0.75, 0.35));
    c += vec3(1.0, 0.92, 0.75) * pow(max(dot(r, sun), 0.0), 350.0) * 6.0 * day;
    c += vec3(1.0, 0.85, 0.6) * pow(max(dot(r, sun), 0.0), 12.0) * 0.25 * day;
    return c;
}

void main() {
    float t = GameTime * 1200.0;
    float kind = data.r, foamHint = data.g, day = data.b;
    mat3 toView = mat3(ModelViewMat);
    mat3 toWorld = transpose(toView);
    vec3 v = normalize(-viewPos);
    vec3 col;
    float alpha;
    if (kind < 0.5) {
        vec2 p = uv;
        float e = 0.02;
        float h = waves(p, t);
        vec3 n = normalize(vec3(-(waves(p + vec2(e, 0.0), t) - h) / e, 1.0, -(waves(p + vec2(0.0, e), t) - h) / e));
        vec3 nv = toView * n;
        float ndv = max(dot(nv, v), 0.0);
        float fres = 0.03 + 0.97 * pow(1.0 - ndv, 5.0);
        vec3 r = toWorld * reflect(-v, nv);
        vec3 refl = skyCol(r, day);
        vec3 deep = vec3(0.015, 0.11, 0.16), shallow = vec3(0.07, 0.42, 0.48);
        vec3 body = mix(deep, shallow, 0.35 + 0.65 * ndv) * (0.3 + 0.7 * day);
        // (caustics: bright wandering lines of light under the surface)
        float ca = abs(noise(vec3(p * 7.0, t * 0.5)) - noise(vec3(p * 7.0 + 3.7, t * 0.43)));
        body += vec3(0.25, 0.55, 0.55) * pow(1.0 - clamp(ca * 4.0, 0.0, 1.0), 6.0) * 0.35 * (0.3 + 0.7 * day);
        col = mix(body, refl, fres);
        // (foam where the streams land, and round the edge)
        float f = foamHint * smoothstep(0.6, 0.78, fbm(vec3(p * 9.0, t * 0.8)) + foamHint * 0.12);
        col = mix(col, vec3(0.92, 0.97, 1.0) * (0.35 + 0.65 * day), clamp(f, 0.0, 1.0));
        alpha = mix(0.72, 0.97, fres) * data.a;
        alpha = max(alpha, f);
    } else {
        float along = uv.x, across = uv.y;
        float a = clamp(abs(across), 0.0, 1.0);
        vec3 nv = normalize(vec3(across * 0.9, 0.0, sqrt(1.0 - a * a * 0.81)));
        float fres = 0.04 + 0.96 * pow(1.0 - max(nv.z, 0.0), 4.0);
        vec3 r = toWorld * reflect(-v, normalize(nv * 0.6 + v * 0.4));
        float streak = fbm(vec3(along * 3.0 - t * 4.0, across * 2.5, t * 0.3));
        vec3 body = mix(vec3(0.08, 0.38, 0.46), vec3(0.85, 0.95, 1.0), smoothstep(0.45, 0.8, streak)) * (0.3 + 0.7 * day);
        col = mix(body, skyCol(r, day), fres * 0.8);
        col += vec3(1.0) * pow(1.0 - abs(across + 0.35), 12.0) * 0.5 * (0.4 + 0.6 * day);
        alpha = (0.62 + 0.3 * smoothstep(0.5, 0.8, streak)) * smoothstep(1.0, 0.7, a) * data.a;
    }
    fragColor = vec4(col, alpha);
}
