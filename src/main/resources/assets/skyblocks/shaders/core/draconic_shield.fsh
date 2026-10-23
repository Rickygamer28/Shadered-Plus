#version 150

// Adapted from Draconic Evolution by brandon3055 (https://github.com/Draconic-Inc/Draconic-Evolution), used under its
// "Don't Be a Jerk" licence (portions of the code may be copied into other projects; source kept public).
// Shadered+ credits brandon3055 / Draconic Evolution in the blocks' tooltips. All original rights his.
// (shield.fsh: rings rippling out from the middle of each face, laid over a dark base instead of blended.)

#moj_import <skyblocks:brandon_math.glsl>

uniform float Time;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec3 coord = vec3(texCoord0, 0);
    float rad = distance(coord.xy, vec2(0.5, 0.5)) * 5;

    float density = 8;
    float noise = 0;
    for (int i = 1; i <= 5; i++) {
        float power = pow(2, float(i));
        noise += (2 / power) * max(snoise((coord * 0.5) + vec3(0.0, 0.0, Time * -0.01), power * density), -1);
    }
    noise *= 0.5;

    float t1 = -1 + mod(Time, 6.0) + noise;
    float t2 = -1 + mod(Time + 2, 6.0) + noise;
    float t3 = -1 + mod(Time + 4, 6.0) + noise;

    float colour = min(min(abs(rad - t1), abs(rad - t2)), abs(rad - t3));
    float a = clamp(max(0.1, 1. - pow(colour, .3)), 0.0, 1.0);
    fragColor = vec4(vertexColor.rgb * a + vec3(0.01, 0.015, 0.03) * (1.0 - a), 1.0);
}
