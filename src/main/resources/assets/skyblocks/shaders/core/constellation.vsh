#version 150

// Shadered+'s Constellation Board. Color: red what (0 the night behind, 1 a star, 2 a line between two), green how bright
// (a picked star: 1), blue a seed. UV0: the night - the world's position across the board; a star: -1 .. 1; a line:
// along it (blocks), across it (-1 .. 1).

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;
out vec3 viewPos;
out vec4 data;

void main() {
    vec4 v = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * v;
    viewPos = v.xyz;
    uv = UV0;
    data = Color;
}
