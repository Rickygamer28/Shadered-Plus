#version 150

// Shadered+'s magic circle (client/MagicCircleRenderer). UV0: on the circle, -1 .. 1 across; on its wall of light, round
// it (blocks) and up it (0 .. 1). Color: its rune's colour; alpha: rune x 2 + 1 for the wall (0 for the circle).

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 at;
flat out vec3 tint;
flat out int rune;
flat out int wall;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    at = UV0;
    tint = Color.rgb;
    int a = int(Color.a * 255.0 + 0.5);
    rune = a / 2;
    wall = a - rune * 2;
}
