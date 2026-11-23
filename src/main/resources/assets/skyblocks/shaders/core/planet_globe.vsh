#version 150

// Shadered+'s Planet Globe: a little planet, drawn on a square turned to face you (UV0: -1 .. 1 across it).
// Color: red its look (0 .. 7), green 1 with rings, blue its seed.

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
