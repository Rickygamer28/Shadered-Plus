#version 150

// Adapted from Draconic Evolution by brandon3055 (https://github.com/Draconic-Inc/Draconic-Evolution), used under its
// "Don't Be a Jerk" licence (portions of the code may be copied into other projects; source kept public).
// Shadered+ credits brandon3055 / Draconic Evolution in the blocks' tooltips. All original rights his.
// (reactor.fsh: the reactor core's plasma; the block's colour picks how hot it runs.)

#moj_import <skyblocks:brandon_math.glsl>

uniform float Time;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float intensity = vertexColor.a * 1.3;
    vec3 coord = vec3(texCoord0, 0);
    float iCap = max(0, min(1, intensity));
    float yModifier = max(0, max(1 - (coord.y * 5), (coord.y - 0.8) * 5));
    float brightness = (1.4 + (yModifier * 1.2 * iCap)) * max(-0.4, intensity);
    float offline = min(0, intensity) * -1;

    float density = 10;
    for (int i = 1; i <= 7; i++) {
        float power = pow(2, float(i));
        brightness += (1.5 / power) * max(snoise(coord + vec3(0, 0, Time * 0.01), power * density), -1 + (offline * 1.5));
    }

    fragColor = vec4(clamp(vec3(brightness, pow(brightness, 2.0) * 0.4 * iCap, pow(brightness, 3.0) * 0.15 * iCap), 0.0, 1.0), 1.0);
}
