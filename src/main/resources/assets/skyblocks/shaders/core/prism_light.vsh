#version 150

// Shadered+'s prism (client/PrismRenderer): the light out of the crystal - the little rainbows it throws onto what's
// round it, and the faint rays to them. UV0: where in the shape (a rainbow: x -1 red .. 1 violet, y across it; a ray:
// x 2 .. 3 along it, y -1 .. 1 across it, red to violet). Color: the light's colour (white: a plain rainbow), alpha
// how bright.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec4 tint;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
    tint = Color;
}
