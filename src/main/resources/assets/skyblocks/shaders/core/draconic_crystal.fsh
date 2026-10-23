#version 150

// Adapted from Draconic Evolution by brandon3055 (https://github.com/Draconic-Inc/Draconic-Evolution), used under its
// "Don't Be a Jerk" licence (portions of the code may be copied into other projects; source kept public).
// Shadered+ credits brandon3055 / Draconic Evolution in the blocks' tooltips. All original rights his.
// (energy_crystal.fsh; the pattern follows the face, not the view, so it doesn't slide as you walk.)

#moj_import <skyblocks:brandon_math.glsl>

uniform float Time;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec3 Colour = vertexColor.rgb;
    vec3 coord = vec3(texCoord0, 0);
    coord.x += Time / 15.0;

    float density = 15;
    float brightness = 1;
    for (int i = 1; i <= 7; i++) {
        float power = pow(2, float(i));
        brightness += (1.5 / power) * max(snoise(coord + vec3(0, 0, Time * 0.01), power * density), -1);
    }

    fragColor = vec4(clamp(vec3(brightness * Colour.r, pow(brightness, 2.0) * Colour.g, pow(brightness, 3.0) * Colour.b), 0.0, 1.0), 1.0);
}
