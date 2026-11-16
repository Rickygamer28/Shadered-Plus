#version 150

// Shadered+'s spin pad aura (client/SpinPadRenderer): round the spinning cat. UV0: on the floor disc, where on it
// (0 .. 1 each way, its middle 0.5, 0.5); on the ring round the cat, round it (0 .. 1) and up it (0 .. 1). Color: red how
// fast the pad's spinning (0 .. 1), green how far round it's turned (0 .. 1, a whole turn), blue which part (0 the disc,
// 1 the ring), alpha how strongly it shows.

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 at;
out float speed;
out float turn;
out float part;
out float strength;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    at = UV0;
    speed = Color.r;
    turn = Color.g;
    part = Color.b;
    strength = Color.a;
}
