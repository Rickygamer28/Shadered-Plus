#version 150

// Shadered+'s sky clock face. UV0: across the face (0 .. 1, from its top left). Color: r - where the sun is in its day
// (Minecraft's celestial angle: 0 noon, 0.25 sunset, 0.5 midnight, 0.75 sunrise), g - the moon's phase / 8, b - how
// hard it's raining, a - thunder.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 sky;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
    sky = Color;
}
