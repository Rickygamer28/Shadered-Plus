#version 150

// Adapted from Draconic Evolution by brandon3055 (https://github.com/Draconic-Inc/Draconic-Evolution), used under its
// "Don't Be a Jerk" licence (portions of the code may be copied into other projects; source kept public).
// Shadered+ credits brandon3055 / Draconic Evolution in the blocks' tooltips. All original rights his.
// (reactor_shield.fsh: the reactor's containment field, laid over a dark core instead of blended.)

#moj_import <skyblocks:brandon_math.glsl>

uniform float Time;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float power = vertexColor.a;

    vec3 coord = vec3(texCoord0, 0);
    float yModifier = max(0, max(1 - (coord.y * 10), (coord.y - 0.9) * 10));
    float brightness = 1.2 + (yModifier * -2) + 1 - min(1, power * 3);

    coord.x += Time / 10;
    coord.y += Time / 5;
    coord.x -= coord.y / 6;
    coord.y *= 0.2;

    float density = 31;
    for (int i = 1; i <= 7; i++) {
        float p = pow(2, float(i));
        brightness += (1.5 / p) * max(snoise(coord + vec3(0, 0, Time * 0.006), p * density), -1);
    }

    float po = max(brightness, 0.8);
    float r = (0.5 - (power * 2));
    float g = power + yModifier * 0.5;
    float b = (power * 2) + yModifier * 0.5;

    vec4 colour = vec4(po * r, pow(po, 2.0) * g, pow(po, 3.0) * b, max(0.1, 2 - brightness));
    float a = clamp(colour.a, 0.0, 1.0);
    fragColor = vec4(clamp(colour.rgb, 0.0, 1.0) * a + vec3(0.015, 0.02, 0.05) * (1.0 - a), 1.0);
}
