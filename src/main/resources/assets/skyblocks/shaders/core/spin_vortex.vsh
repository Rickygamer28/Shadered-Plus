#version 150

// Shadered+'s spin pad vortex: a funnel round the spinning cat. UV0: round the funnel (0 .. 1) and up it (0 .. 1);
// Color: red how fast the pad's spinning (0 .. 1), green how far round it's turned (0 .. 1, a whole turn), alpha how
// strongly it shows.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 at;
out float speed;
out float turn;
out float strength;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    at = UV0;
    speed = Color.r;
    turn = Color.g;
    strength = Color.a;
}
