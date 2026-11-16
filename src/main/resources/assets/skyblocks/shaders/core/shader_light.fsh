#version 150

// The Shader Light (from The Valley Mod by D0nkey100, used with permission: its filters.fsh "glows", as it is): coloured
// light painted onto the picture. Everything within a light's reach takes its colour, most near the light and none at
// the edge (the block itself gives ordinary white light; this colours it). Several lights mix. It goes by the spot under
// each pixel (the picture's depth), so it does not stop at walls.

uniform sampler2D Sampler0;  // the picture so far
uniform sampler2D Sampler1;  // its depth
uniform mat4 InvViewProj;    // from the screen back to the world (from the camera, the world's way round)
uniform mat4 LightsA;        // lights 0-3: columns x, y, z (from the camera), reach
uniform mat4 LightsB;        // lights 4-7
uniform mat4 ColoursA;       // their colours: columns r, g, b
uniform mat4 ColoursB;
uniform float LightCount;

in vec2 screenUv;

out vec4 fragColor;

void glow(vec3 point, vec4 light, vec3 tint, inout vec3 sum, inout float total) {
    vec3 to = point - light.xyz;
    float near = clamp(1.0 - dot(to, to) / (light.w * light.w), 0.0, 1.0);
    near *= near;
    sum += tint * near;
    total += near;
}

void main() {
    vec3 colour = texture(Sampler0, screenUv).rgb;
    float depth = texture(Sampler1, screenUv).r;
    if (depth >= 1.0) {
        fragColor = vec4(colour, 1.0); // (the sky)
        return;
    }
    vec4 p = InvViewProj * vec4(screenUv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec3 point = p.xyz / p.w;
    vec3 sum = vec3(0.0);
    float total = 0.0;
    int count = int(LightCount + 0.5);
    for (int i = 0; i < 8; i++) {
        if (i >= count) break;
        vec4 light = i < 4 ? LightsA[i] : LightsB[i - 4];
        vec3 tint = (i < 4 ? ColoursA[i] : ColoursB[i - 4]).rgb;
        glow(point, light, tint, sum, total);
    }
    if (total <= 0.0) {
        fragColor = vec4(colour, 1.0);
        return;
    }
    vec3 tint = sum / total;
    float amount = min(total, 1.0);
    // (the tint colours what is lit, a little brighter so it does not only darken, and a little of it is added on top)
    fragColor = vec4(colour * mix(vec3(1.0), tint * 1.3, amount) + tint * 0.10 * amount, 1.0);
}
