#version 150

// The shaft: from where you look through the top, straight on down to where the ray meets one of the hole's walls -
// the ground round it, block after block down, darker and darker - with dust drifting slowly down through it, caught in
// the light from above, until it's all dark.

uniform sampler2D Sampler0;
uniform float GameTime;

in vec3 eyeDir;
in vec2 local;
flat in vec4 rect;
flat in ivec2 spriteAt;
flat in int spriteSize;
flat in float bright;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec3 wallAt(float u, float depth, float lod) {
    int level = int(clamp(lod, 0.0, 4.0));
    int size = max(spriteSize >> level, 1);
    ivec2 texel = ivec2(int(floor(fract(u) * float(size))), int(floor(fract(depth) * float(size))));
    return texelFetch(Sampler0, (spriteAt >> level) + texel, level).rgb;
}

void main() {
    vec3 d = normalize(eyeDir);
    if (d.y > -1.0e-4) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec3 o = vec3(local.x, 0.0, local.y);
    // where the ray leaves the hole: through an x wall or a z wall
    float tx = d.x > 1.0e-5 ? (rect.z - o.x) / d.x : d.x < -1.0e-5 ? (rect.x - o.x) / d.x : 1.0e9;
    float tz = d.z > 1.0e-5 ? (rect.w - o.z) / d.z : d.z < -1.0e-5 ? (rect.y - o.z) / d.z : 1.0e9;
    float t = min(tx, tz);
    vec3 h = o + d * t;
    float depth = -h.y;
    bool xWall = tx < tz;
    float u = xWall ? h.z : h.x;
    // (the texture blurred as it gets small with distance, so it doesn't shimmer)
    vec2 wuv = vec2(u, depth) * float(spriteSize);
    float lod = 0.5 * log2(max(max(dot(dFdx(wuv), dFdx(wuv)), dot(dFdy(wuv), dFdy(wuv))), 1.0e-6));
    vec3 wall = wallAt(u, depth, lod);
    float side = xWall ? 0.78 : 0.62;
    // the light from above dying away down the shaft; a shadow right under the rim, and in the wall's corners
    // (quickly: a few blocks down it's nearly black, and it never ends)
    float fall = exp(-depth * 1.05) * (0.75 + 0.25 * smoothstep(0.0, 0.5, depth));
    vec2 inRect = vec2(min(h.x - rect.x, rect.z - h.x), min(h.z - rect.y, rect.w - h.z));
    float corner = smoothstep(0.0, 0.35, xWall ? inRect.y : inRect.x);
    vec3 c = wall * side * fall * mix(0.55, 1.0, corner) * max(bright, 0.04);

    // dust drifting down through the dark: a few layers of motes, slowly falling, lit less the deeper they are
    float ticks = GameTime * 24000.0;
    for (int k = 0; k < 6; k++) {
        float layer = mod(float(k) * 4.7 + ticks * 0.012 * (1.0 + float(k) * 0.13), 28.0) + 0.8;
        float tl = layer / -d.y;
        if (tl >= t) {
            continue;
        }
        vec2 q = (o + d * tl).xz * 3.0 + float(k) * 17.3;
        vec2 cell = floor(q);
        float pick = hash(cell);
        if (pick > 0.88) {
            vec2 spot = vec2(hash(cell + 3.1), hash(cell + 7.7)) * 0.6 + 0.2;
            float r = length(fract(q) - spot);
            float twinkle = 0.6 + 0.4 * sin(ticks * 0.05 + pick * 40.0);
            c += vec3(0.85, 0.8, 0.7) * smoothstep(0.09, 0.0, r) * exp(-layer * 0.12) * 0.9 * twinkle * (0.3 + 0.7 * bright);
        }
    }
    fragColor = vec4(c, 1.0);
}
