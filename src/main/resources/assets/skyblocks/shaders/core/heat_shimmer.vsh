#version 150

// Shadered+'s heat shimmer: a sheet of hot air over fire and lava, turned to face you. UV0: across it (-1 .. 1), up
// it (0 .. 1).

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 uv;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    uv = UV0;
}
