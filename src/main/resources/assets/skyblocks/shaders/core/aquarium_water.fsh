#version 150

#moj_import <fog.glsl>

// Aquarium Water, coloured as the aquariums colour their water (aquarium.fsh): the more water between the eye and
// what's behind it, the more of the water's colour (lighter near the top, deeper down), the moving light-cells of the
// rippling surface on top and on the floor under it, a soft shimmer. Premultiplied: rgb already times coverage.

uniform sampler2D Sampler0; // the world's depth (solid blocks and creatures)
uniform vec2 ScreenSize;
uniform mat4 InvViewProj;   // screen -> camera-relative world
uniform vec3 CamPos;        // (wrapped every 1100 blocks)
uniform float Time;
uniform float Day;          // 1 by day, 0 at night
uniform float InWater;      // the camera in this water
uniform float HasDepth;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in vec3 vPos;
in vec2 vInfo;
in float vTop;
in vec3 vNormal;

out vec4 fragColor;

vec2 hash22(vec2 p) {
    p = vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)));
    return fract(sin(p) * 43758.5453);
}

// the light-cells of a rippling surface (as the tanks')
float caustics(vec2 p, float t) {
    p *= 2.2;
    vec2 i = floor(p), f = fract(p);
    float f1 = 8.0, f2 = 8.0;
    for (int y = -1; y <= 1; y++) {
        for (int x = -1; x <= 1; x++) {
            vec2 g = vec2(x, y);
            vec2 o = hash22(i + g);
            o = 0.5 + 0.4 * sin(t * 0.9 + 6.2831 * o);
            float d = length(g + o - f);
            if (d < f1) { f2 = f1; f1 = d; } else if (d < f2) { f2 = d; }
        }
    }
    return exp(-(f2 - f1) * 9.0);
}

void main() {
    bool facing = dot(vNormal, vPos) < 0.0; // (seen from outside the water)
    // from outside, only the outside of the water; from in it, only the inside of its surface
    if (InWater > 0.5 ? facing : !facing) {
        discard;
    }
    float t = Time;
    vec3 world = vPos + CamPos;

    // the tank's water colours: teal by day, deep blue by night (as the tank's moonlight)
    vec3 deepC = mix(vec3(0.0, 0.02, 0.1), vec3(0.02, 0.2, 0.32), Day);
    vec3 lightC = mix(vec3(0.04, 0.13, 0.33), vec3(0.15, 0.55, 0.62), Day);
    vec3 waterCol = mix(deepC, lightC, exp(-max(vInfo.x, 0.0) * 0.16));

    if (!facing) {
        // from under the surface: the surface's light-cells overhead, faint
        float c = vTop > 0.5 ? caustics(world.xz * 1.6 + vec2(t * 0.1, 0.0), t * 1.6) : 0.0;
        float a = 0.22;
        vec3 col = mix(lightC, vec3(0.8, 0.95, 1.0), 0.3) * a + vec3(0.8, 0.95, 1.0) * c * 0.25;
        fragColor = vec4(col, a);
        return;
    }

    // how much water the eye looks through here: to the world behind it (or, without its depth, the water's thickness)
    float travel = max(vInfo.y, 0.5) * 1.2;
    vec3 behind = vec3(0.0);
    bool floorSeen = false;
    if (HasDepth > 0.5) {
        vec2 uv = gl_FragCoord.xy / ScreenSize;
        float d = texture(Sampler0, uv).r;
        if (d < 1.0) {
            vec4 p = InvViewProj * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
            behind = p.xyz / p.w;
            travel = min(max(length(behind) - length(vPos), 0.0), vInfo.y * 1.75 + 0.5);
            floorSeen = true;
        }
    }
    float a = 1.0 - exp(-travel * 0.3);
    a = max(a, 0.14);
    vec3 col = waterCol * a;
    col += vec3(0.25, 0.5, 0.55) * 0.08 * (0.5 + 0.5 * sin(world.x * 4.0 + world.z * 3.0 + world.y * 2.0 + t * 1.5)) * a;

    // the surface's light-cells, on the floor under the water (dimmer the deeper it is) ...
    if (floorSeen) {
        vec3 b = behind + CamPos;
        float sink = clamp(world.y - b.y, 0.0, 32.0);
        float fl = caustics(b.xz * 1.6 + vec2(t * 0.1, 0.0), t * 1.6);
        col += vec3(0.75, 0.92, 1.0) * fl * 0.2 * exp(-sink * 0.12) * (0.35 + 0.65 * Day) * (1.0 - a * 0.6);
    }
    // ... and on the surface itself, catching the light
    if (vTop > 0.5) {
        col += vec3(0.8, 0.95, 1.0) * caustics(world.xz * 1.6 + vec2(t * 0.1, 0.0), t * 1.6) * 0.18 * (0.4 + 0.6 * Day);
    }

    // the world's fog over it, as over everything (premultiplied: the fog's colour times coverage)
    float fog = linear_fog_fade(length(vPos), FogStart, FogEnd);
    col = mix(FogColor.rgb * a, col, fog);
    fragColor = vec4(col, a);
}
